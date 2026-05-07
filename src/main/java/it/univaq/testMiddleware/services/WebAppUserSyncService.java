package it.univaq.testMiddleware.services;

import it.univaq.testMiddleware.models.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Propaga utenti CLIENT dal middleware verso l'app web (ProgettoTesi):
 * POST /app/api/integration/middleware/client-upsert
 */
@Service
public class WebAppUserSyncService {

    private static final Logger log = LoggerFactory.getLogger(WebAppUserSyncService.class);

    private final RestTemplate restTemplate;

    @Value("${app.web-sync.enabled:false}")
    private boolean enabled;

    /** Es. http://localhost:8081 (senza slash finale). */
    @Value("${app.web-sync.base-url:}")
    private String baseUrl;

    /** Stesso valore di {@code app.middleware-integration.secret} lato web. */
    @Value("${app.web-sync.token:}")
    private String syncToken;

    /**
     * Se l'utente middleware non ha {@link User#getIdCondominio()}, usa questo ID edificio sulla web.
     */
    @Value("${app.web-sync.default-condominio-id:}")
    private String defaultCondominioIdStr;

    public WebAppUserSyncService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void pushClientUpsert(User user) {
        tryPushClientUpsert(user);
    }

    /**
     * @return true se la chiamata HTTP verso la web è andata a buon fine (2xx), false altrimenti.
     *         Non lancia eccezioni: pensato per retry/background.
     */
    public boolean tryPushClientUpsert(User user) {
        if (!enabled) {
            return false;
        }
        String urlBase = baseUrl == null ? "" : baseUrl.trim();
        String token = syncToken == null ? "" : syncToken.trim();
        if (urlBase.isEmpty() || token.isEmpty()) {
            log.warn("app.web-sync.enabled=true ma base-url o token non configurati: skip push web.");
            return false;
        }
        if (user == null) {
            return false;
        }
        String email = user.getEmail() != null ? user.getEmail().trim().toLowerCase() : "";
        if (email.isBlank()) {
            log.warn("Web sync skip: utente senza email (id={}).", user.getId());
            return false;
        }

        Long idCondominio = user.getIdCondominio();
        if (idCondominio == null && defaultCondominioIdStr != null && !defaultCondominioIdStr.isBlank()) {
            try {
                idCondominio = Long.parseLong(defaultCondominioIdStr.trim());
            } catch (NumberFormatException e) {
                log.warn("app.web-sync.default-condominio-id non numerico: {}", defaultCondominioIdStr);
            }
        }

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("email", email);
        item.put("nome", user.getNome() != null ? user.getNome() : "");
        item.put("cognome", user.getCognome() != null ? user.getCognome() : "");
        item.put("idCondominio", idCondominio);

        Map<String, Object> body = Map.of("clients", List.of(item));

        String path = "/app/api/integration/middleware/client-upsert";
        String url = urlBase.endsWith("/") ? urlBase.substring(0, urlBase.length() - 1) + path : urlBase + path;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Middleware-Sync-Token", token);

        try {
            ResponseEntity<String> res = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
            return res.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("Push web fallito per {}: {}", email, e.getMessage());
            return false;
        }
    }
}
