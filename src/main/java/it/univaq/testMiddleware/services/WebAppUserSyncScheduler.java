package it.univaq.testMiddleware.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.univaq.testMiddleware.models.UserSyncOutbox;
import it.univaq.testMiddleware.repositories.UserSyncOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Spinge i nuovi client verso il DB dell'app web (batch periodico, outbox {@link UserSyncOutboxService#EVENT_WEB_CLIENT_UPSERT}).
 */
@Component
public class WebAppUserSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(WebAppUserSyncScheduler.class);

    private final UserSyncOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.web-sync.enabled:false}")
    private boolean enabled;

    @Value("${app.web-sync.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${app.web-sync.path:/app/api/integration/middleware/client-upsert}")
    private String path;

    @Value("${app.web-sync.secret:}")
    private String secret;

    public WebAppUserSyncScheduler(UserSyncOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Intervallo default 120s (2 min): compromesso tra latenza verso la web app e carico HTTP.
     * Override: {@code app.web-sync.interval-ms}.
     */
    @Scheduled(fixedDelayString = "${app.web-sync.interval-ms:120000}")
    public void flushOutboxToWebAppScheduled() {
        syncPendingOutboxToWebApp();
    }

    /**
     * Esegue subito lo stesso flusso del job (es. dopo {@code POST /api/integration/web-sync/flush}).
     */
    public void flushOutboxNow() {
        syncPendingOutboxToWebApp();
    }

    private void syncPendingOutboxToWebApp() {
        if (!enabled) {
            return;
        }
        if (secret == null || secret.isBlank()) {
            log.warn("app.web-sync abilitato ma app.web-sync.secret vuoto: sync disabilitata");
            return;
        }

        List<UserSyncOutbox> batch = outboxRepository.findTop200ByStatusAndEventTypeOrderByIdAsc(
                "PENDING",
                UserSyncOutboxService.EVENT_WEB_CLIENT_UPSERT);
        if (batch.isEmpty()) {
            return;
        }

        Map<String, UserSyncOutbox> latestByEmail = new LinkedHashMap<>();
        for (UserSyncOutbox row : batch) {
            String email = row.getEmail() != null ? row.getEmail().trim().toLowerCase() : "";
            if (!email.isBlank()) {
                latestByEmail.put(email, row);
            }
        }

        ArrayNode clients = objectMapper.createArrayNode();
        Set<String> emailsSentInPayload = new HashSet<>();
        for (UserSyncOutbox row : batch.stream()
                .filter(r -> {
                    String em = r.getEmail() != null ? r.getEmail().trim().toLowerCase() : "";
                    UserSyncOutbox keep = latestByEmail.get(em);
                    return keep != null && keep.getId().equals(r.getId());
                })
                .sorted(Comparator.comparing(UserSyncOutbox::getId))
                .toList()) {
            try {
                JsonNode payload = objectMapper.readTree(row.getPayload());
                ObjectNode item = objectMapper.createObjectNode();
                item.set("email", payload.path("email"));
                item.set("nome", payload.path("nome"));
                item.set("cognome", payload.path("cognome"));
                item.set("idCondominio", payload.path("idCondominio"));
                clients.add(item);
                String em = row.getEmail() != null ? row.getEmail().trim().toLowerCase() : "";
                if (!em.isBlank()) {
                    emailsSentInPayload.add(em);
                }
            } catch (Exception e) {
                log.warn("Outbox id={}: payload non valido: {}", row.getId(), e.getMessage());
            }
        }

        if (clients.isEmpty()) {
            return;
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.set("clients", clients);

        try {
            String json = Objects.requireNonNull(objectMapper.writeValueAsString(body), "json");
            String syncToken = Objects.requireNonNull(secret, "secret");
            RestClient client = RestClient.builder().baseUrl(Objects.requireNonNull(baseUrl, "baseUrl")).build();
            client.post()
                    .uri(Objects.requireNonNull(path, "path"))
                    .header("X-Middleware-Sync-Token", syncToken)
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .body(json)
                    .retrieve()
                    .toBodilessEntity();

            for (UserSyncOutbox row : batch) {
                String em = row.getEmail() != null ? row.getEmail().trim().toLowerCase() : "";
                if (!em.isBlank() && emailsSentInPayload.contains(em)) {
                    row.setStatus("CONSUMED");
                    outboxRepository.save(row);
                }
            }
            log.info("Sync web app: inviati {} utenti verso {}", clients.size(), baseUrl);
        } catch (Exception e) {
            log.warn("Sync web app fallita: {}", e.getMessage());
        }
    }
}
