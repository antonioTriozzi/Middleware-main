package it.univaq.testMiddleware.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.univaq.testMiddleware.models.User;
import it.univaq.testMiddleware.models.UserSyncOutbox;
import it.univaq.testMiddleware.repositories.UserSyncOutboxRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class UserSyncOutboxService {

    /** Sync verso DB app web (es. OTP, registrazione, UserDataService). Non usato per utenti creati solo da ingest gateway. */
    public static final String EVENT_WEB_CLIENT_UPSERT = "WEB_CLIENT_UPSERT";

    private final UserSyncOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public UserSyncOutboxService(UserSyncOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void enqueue(User user, String eventType) {
        if (user == null) return;
        String email = user.getEmail() != null ? user.getEmail().trim().toLowerCase() : "";
        if (email.isBlank()) return;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", user.getId());
        payload.put("username", user.getUsername());
        payload.put("nome", user.getNome());
        payload.put("cognome", user.getCognome());
        payload.put("email", email);
        payload.put("numeroDiTelefono", user.getNumeroDiTelefono());
        payload.put("ruolo", user.getRuolo());
        payload.put("idCondominio", user.getIdCondominio());

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            // fallback minimale: evita di fallire l'autenticazione
            json = "{\"id\":" + user.getId() + ",\"email\":\"" + email.replace("\"", "") + "\"}";
        }

        UserSyncOutbox row = new UserSyncOutbox();
        row.setEmail(email);
        row.setEventType(eventType != null && !eventType.isBlank() ? eventType : "UNKNOWN");
        row.setPayload(json);
        row.setStatus("PENDING");
        outboxRepository.save(row);
    }
}

