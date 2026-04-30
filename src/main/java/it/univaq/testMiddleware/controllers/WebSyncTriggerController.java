package it.univaq.testMiddleware.controllers;

import it.univaq.testMiddleware.services.WebAppUserSyncScheduler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Forza un ciclo di invio outbox → app web senza attendere lo scheduler.
 * Header: {@code X-Web-Sync-Trigger-Token} = {@code app.web-sync.trigger-secret}.
 */
@RestController
@RequestMapping("/api/integration/web-sync")
public class WebSyncTriggerController {

    private final WebAppUserSyncScheduler webAppUserSyncScheduler;

    @Value("${app.web-sync.trigger-secret:}")
    private String triggerSecret;

    public WebSyncTriggerController(WebAppUserSyncScheduler webAppUserSyncScheduler) {
        this.webAppUserSyncScheduler = webAppUserSyncScheduler;
    }

    @PostMapping("/flush")
    public ResponseEntity<Map<String, Object>> flush(
            @RequestHeader(value = "X-Web-Sync-Trigger-Token", required = false) String token) {
        if (!StringUtils.hasText(triggerSecret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "app.web-sync.trigger-secret non configurato"));
        }
        if (!StringUtils.hasText(token) || !triggerSecret.equals(token.trim())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Token trigger non valido"));
        }
        webAppUserSyncScheduler.flushOutboxNow();
        return ResponseEntity.ok(Map.of("ok", true, "message", "Sync outbox eseguita (se abilitata e con righe PENDING)"));
    }
}
