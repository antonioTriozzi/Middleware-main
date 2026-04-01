package it.univaq.testMiddleware.models;

import jakarta.persistence.*;
import lombok.Data; // Importa l'annotazione di Lombok
import java.time.Instant;

@Entity
@Table(name = "storico_dati_sensori")
@Data // Genera automaticamente getters, setters, toString, equals e hashCode
public class StoricoDatoSensore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String valore;

    private Instant timestamp;

    @ManyToOne
    @JoinColumn(name = "id_parametro", referencedColumnName = "id_parametro")
    private ParametroDispositivo parametro;

}