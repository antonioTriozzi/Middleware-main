package it.univaq.testMiddleware.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "dati_sensori")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DatoSensore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_sensore")
    private Long idSensore;

    private String valore;

    @Column(name = "timestamp")
    private Instant timestamp;
    // Oppure LocalDateTime, a seconda di come preferisci gestire la data/ora

    // Relazione con Dispositivo
    @ManyToOne
    @JoinColumn(name = "parametro_id")
    private ParametroDispositivo parametro;
}
