package it.univaq.testMiddleware.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "user_sync_outbox")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSyncOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private String email;

    /**
     * Esempi: OTP_VERIFY_CREATE, OTP_VERIFY_UPDATE, REGISTER_CREATE, REGISTER_UPDATE
     */
    @Column(nullable = false)
    private String eventType;

    /**
     * Payload JSON con i dati da sincronizzare (testo).
     */
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    /**
     * Stato per il consumer esterno (il collega): PENDING / CONSUMED / ERROR.
     */
    @Column(nullable = false)
    private String status = "PENDING";
}

