package it.univaq.testMiddleware.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "dispositivi")
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"parametriDispositivo", "storicoEventi"})
public class Dispositivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_dispositivo")
    private Long idDispositivo;

    /**
     * Identificativo stringa del dispositivo lato gateway (es. SN-SE-12345678, MBUS-..., 00FA:...).
     * Utile per riconciliare eventi tra protocolli diversi.
     */
    @Column(name = "external_device_id")
    private String externalDeviceId;

    /**
     * Identificativo asset (numerico) lato dominio applicativo.
     * Nel nuovo JSON consumi è il campo asset_id.
     */
    @Column(name = "asset_id")
    private Long assetId;

    @Column(name = "asset_name")
    private String assetName;

    private String nome;
    private String marca;
    private String modello;
    private String tipo;
    private String stato;
    private String adapterType = "default";

    // Relazione con Condominio (FK)
    @ManyToOne
    @JoinColumn(name = "condominio_id")
    @JsonIgnoreProperties({"tokens"})
    private Condominio condominio;

    // Proprietario (persona fisica) a cui attribuire dispositivo/consumi
    @ManyToOne
    @JoinColumn(name = "owner_user_id")
    @JsonIgnoreProperties({"tokens"})
    private User owner;

    // 1-N con ParametroDispositivo
    @OneToMany(mappedBy = "dispositivo", cascade = CascadeType.ALL)
    @JsonIgnore  // Esclude questo campo dalla serializzazione
    private List<ParametroDispositivo> parametriDispositivo;

    // 1-N con StoricoEvento
    @OneToMany(mappedBy = "dispositivo", cascade = CascadeType.ALL)
    @JsonIgnore  // Esclude questo campo dalla serializzazione
    private List<StoricoEvento> storicoEventi;
}
