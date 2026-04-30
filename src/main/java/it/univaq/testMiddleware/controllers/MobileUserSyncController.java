package it.univaq.testMiddleware.controllers;

import it.univaq.testMiddleware.DTO.UserDTO;
import it.univaq.testMiddleware.services.UserDataService;
import it.univaq.testMiddleware.services.WebAppUserSyncScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * API dedicate all'app Android: upsert utente (per email) + accodamento verso {@code user_sync_outbox}
 * per la sincronizzazione con l'app web. Dopo un utente nuovo, viene invocato subito
 * {@link WebAppUserSyncScheduler#flushOutboxNow()} (se {@code app.web-sync.enabled=true}) così la web
 * persiste il client senza attendere lo scheduler.
 * <p>
 * Autenticazione: header {@code X-Mobile-Api-Token} = {@code app.mobile-api.secret}.
 */
@RestController
@RequestMapping("/api/mobile/v1")
public class MobileUserSyncController {

    private static final Logger log = LoggerFactory.getLogger(MobileUserSyncController.class);

    private final UserDataService userDataService;
    private final WebAppUserSyncScheduler webAppUserSyncScheduler;

    @Value("${app.mobile-api.secret:}")
    private String mobileApiSecret;

    public MobileUserSyncController(UserDataService userDataService, WebAppUserSyncScheduler webAppUserSyncScheduler) {
        this.userDataService = userDataService;
        this.webAppUserSyncScheduler = webAppUserSyncScheduler;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "service", "middleware-mobile-v1");
    }

    /**
     * Body JSON: {@link UserDTO} (email obbligatoria; username/password/nome/cognome/idCondominio/ruolo opzionali).
     * Se l'utente non esiste viene creato; se esiste viene aggiornato. L'outbox verso la web
     * ({@code WEB_CLIENT_UPSERT}) solo per email nuove; subito dopo, se {@code app.web-sync.enabled=true},
     * viene eseguito un flush outbox verso la web (stesso meccanismo dello scheduler).
     */
    @PostMapping("/users/sync")
    public ResponseEntity<?> syncUser(
            @RequestHeader(value = "X-Mobile-Api-Token", required = false) String token,
            @RequestBody UserDTO body) {
        if (!StringUtils.hasText(mobileApiSecret)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "app.mobile-api.secret non configurato sul middleware"));
        }
        if (!StringUtils.hasText(token) || !mobileApiSecret.equals(token.trim())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Token mancante o non valido (header X-Mobile-Api-Token)"));
        }
        try {
            UserDataService.UserSaveResult saveResult = userDataService.saveWithResult(body);
            var saved = saveResult.user();
            if (saveResult.willSyncToWeb()) {
                webAppUserSyncScheduler.flushOutboxNow();
            }
            log.info(
                    "Mobile user sync OK: id={}, email={}, willSyncToWeb={}",
                    saved.getId(),
                    saved.getEmail(),
                    saveResult.willSyncToWeb());
            return ResponseEntity.ok(Map.of(
                    "id", saved.getId(),
                    "email", saved.getEmail() != null ? saved.getEmail() : "",
                    "username", saved.getUsername() != null ? saved.getUsername() : "",
                    "newUserWillSyncToWeb", saveResult.willSyncToWeb()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
