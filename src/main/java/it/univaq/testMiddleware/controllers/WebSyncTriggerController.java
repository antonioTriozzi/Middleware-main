package it.univaq.testMiddleware.controllers;

import it.univaq.testMiddleware.services.WebAppUserSyncScheduler;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Forza un ciclo di invio outbox → app web senza attendere lo scheduler.
 * Autenticazione (filtro JWT): {@code X-Web-Sync-Trigger-Token} = {@code app.web-sync.trigger-secret}
 * oppure {@code Authorization: Bearer} con JWT middleware valido.
 */
@RestController
@RequestMapping("/api/integration/web-sync")
public class WebSyncTriggerController {

    private final WebAppUserSyncScheduler webAppUserSyncScheduler;

    public WebSyncTriggerController(WebAppUserSyncScheduler webAppUserSyncScheduler) {
        this.webAppUserSyncScheduler = webAppUserSyncScheduler;
    }

    @PostMapping("/flush")
    public ResponseEntity<Map<String, Object>> flush() {
        webAppUserSyncScheduler.flushOutboxNow();
        return ResponseEntity.ok(Map.of("ok", true, "message", "Sync outbox eseguita (se abilitata e con righe PENDING)"));
    }
}
