package it.univaq.testMiddleware.services;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpService {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final int MAX_ATTEMPTS = 8;
    private static final Duration MIN_REQUEST_INTERVAL = Duration.ofSeconds(15);

    private final SecureRandom random = new SecureRandom();
    private final Map<String, OtpRecord> byEmail = new ConcurrentHashMap<>();

    public String issue(String email) {
        String key = normalize(email);
        Instant now = Instant.now();
        OtpRecord prev = byEmail.get(key);
        if (prev != null && prev.lastIssuedAt != null && prev.lastIssuedAt.plus(MIN_REQUEST_INTERVAL).isAfter(now)) {
            // throttling: riusa lo stesso codice finché non passa l'intervallo minimo
            return prev.code;
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        byEmail.put(key, new OtpRecord(code, now, now.plus(TTL), 0));
        return code;
    }

    public VerifyResult verify(String email, String code) {
        String key = normalize(email);
        Instant now = Instant.now();
        OtpRecord rec = byEmail.get(key);
        if (rec == null) {
            return VerifyResult.INVALID;
        }
        if (rec.expiresAt.isBefore(now)) {
            byEmail.remove(key);
            return VerifyResult.EXPIRED;
        }
        if (rec.attempts >= MAX_ATTEMPTS) {
            return VerifyResult.LOCKED;
        }
        if (rec.code.equals(code)) {
            byEmail.remove(key);
            return VerifyResult.OK;
        }
        byEmail.put(key, rec.withAttempts(rec.attempts + 1));
        return VerifyResult.INVALID;
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private record OtpRecord(String code, Instant lastIssuedAt, Instant expiresAt, int attempts) {
        OtpRecord withAttempts(int n) {
            return new OtpRecord(code, lastIssuedAt, expiresAt, n);
        }
    }

    public enum VerifyResult {
        OK,
        INVALID,
        EXPIRED,
        LOCKED
    }
}

