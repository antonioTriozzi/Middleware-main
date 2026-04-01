package it.univaq.testMiddleware.models;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "parametri_dispositivi")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParametroDispositivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_parametro")
    private Long idParametro;

    @Column(name = "nome")
    private String nome;

    @Column(name = "tipologia")
    private String tipologia;

    @Column(name = "unita_misura")
    private String unitaMisura;

    // Nuove colonne per il range di valori (ipotizziamo valori numerici come Double)
    @Column(name = "val_min")
    private Double valMin;

    @Column(name = "val_max")
    private Double valMax;

    @Column(name = "max_delta")
    private Double maxDelta;

    // Relazione con Dispositivo
    @ManyToOne
    @JoinColumn(name = "dispositivo_id")
    private Dispositivo dispositivo;
}
