package it.univaq.testMiddleware.services;

import it.univaq.testMiddleware.models.User;
import it.univaq.testMiddleware.models.UserSyncOutbox;
import it.univaq.testMiddleware.repositories.UserRepository;
import it.univaq.testMiddleware.repositories.UserSyncOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Retry automatico del push verso la web usando la tabella {@code user_sync_outbox}.
 * Non altera {@link UserSyncOutbox#getStatus()} (che resta per consumer esterni), ma usa i campi {@code web*}.
 */
@Service
public class UserSyncOutboxWebRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(UserSyncOutboxWebRetryScheduler.class);

    private final UserSyncOutboxRepository outboxRepository;
    private final UserRepository userRepository;
    private final WebAppUserSyncService webAppUserSyncService;

    @Value("${app.web-sync.outbox-retry.enabled:true}")
    private boolean retryEnabled;

    @Value("${app.web-sync.outbox-retry.max-attempts:20}")
    private int maxAttempts;

    public UserSyncOutboxWebRetryScheduler(
            UserSyncOutboxRepository outboxRepository,
            UserRepository userRepository,
            WebAppUserSyncService webAppUserSyncService) {
        this.outboxRepository = outboxRepository;
        this.userRepository = userRepository;
        this.webAppUserSyncService = webAppUserSyncService;
    }

    /**
     * Nota: fixedDelay controllabile via {@code app.web-sync.outbox-retry.delay-ms}.
     */
    @Scheduled(fixedDelayString = "${app.web-sync.outbox-retry.delay-ms:15000}")
    @Transactional
    public void retryPendingWebSync() {
        if (!retryEnabled) {
            return;
        }

        List<UserSyncOutbox> batch = outboxRepository.findTop200ByWebStatusOrderByIdAsc("PENDING");
        if (batch.isEmpty()) {
            return;
        }

        for (UserSyncOutbox row : batch) {
            if (row == null) continue;

            if (row.getWebAttempts() >= Math.max(1, maxAttempts)) {
                row.setWebStatus("ERROR");
                row.setWebLastError("Max attempts reached (" + row.getWebAttempts() + ")");
                outboxRepository.save(row);
                continue;
            }

            try {
                Optional<User> userOpt = userRepository.findById(row.getUserId());
                if (userOpt.isEmpty()) {
                    // fallback: usa email se userId non è più valido
                    String email = row.getEmail() != null ? row.getEmail().trim().toLowerCase() : "";
                    userOpt = email.isBlank() ? Optional.empty() : userRepository.findByEmail(email);
                }

                if (userOpt.isEmpty()) {
                    row.setWebAttempts(row.getWebAttempts() + 1);
                    row.setWebLastAttemptAt(Instant.now());
                    row.setWebLastError("User not found (userId=" + row.getUserId() + ", email=" + row.getEmail() + ")");
                    outboxRepository.save(row);
                    continue;
                }

                row.setWebAttempts(row.getWebAttempts() + 1);
                row.setWebLastAttemptAt(Instant.now());
                outboxRepository.save(row);

                boolean ok = webAppUserSyncService.tryPushClientUpsert(userOpt.get());
                if (ok) {
                    row.setWebStatus("SENT");
                    row.setWebSentAt(Instant.now());
                    row.setWebLastError(null);
                    outboxRepository.save(row);
                }
            } catch (Exception e) {
                row.setWebAttempts(row.getWebAttempts() + 1);
                row.setWebLastAttemptAt(Instant.now());
                row.setWebLastError(e.getMessage());
                outboxRepository.save(row);
                log.debug("Web outbox retry failed for outboxId={}: {}", row.getId(), e.getMessage());
            }
        }
    }
}

