package it.univaq.testMiddleware.models;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "sessioni_ricarica")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessioneRicarica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_sessione")
    private Long id;

    // Relazione many-to-one con Dispositivo (che rappresenta la colonnina)
    @ManyToOne
    @JoinColumn(name = "colonnina_id", nullable = false)
    private Dispositivo colonnina;

    // Campo per memorizzare l'identificativo RFID (o il nome) dell'utente che ha effettuato la ricarica
    @Column(name = "rfid", nullable = false)
    private String rfid;

    @Column(name = "inizio_sessione", nullable = false)
    private LocalDateTime inizioSessione;

    @Column(name = "fine_sessione")
    private LocalDateTime fineSessione;

    @Column(name = "energia_erogata", precision = 10, scale = 2)
    private BigDecimal energiaErogata;

    @Column(name = "durata")
    private Integer durata; // in minuti

    @Column(name = "potenza_media", precision = 5, scale = 2)
    private BigDecimal potenzaMedia;

    @Column(name = "costo_totale", precision = 10, scale = 2)
    private BigDecimal costoTotale;
}
