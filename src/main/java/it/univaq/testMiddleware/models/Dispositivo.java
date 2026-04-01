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
@ToString(exclude = {"parametriDispositivo", "datiSensori", "storicoEventi"})
public class Dispositivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_dispositivo")
    private Long idDispositivo;

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

    // 1-N con ParametroDispositivo
    @OneToMany(mappedBy = "dispositivo", cascade = CascadeType.ALL)
    @JsonIgnore  // Esclude questo campo dalla serializzazione
    private List<ParametroDispositivo> parametriDispositivo;

    // 1-N con StoricoEvento
    @OneToMany(mappedBy = "dispositivo", cascade = CascadeType.ALL)
    @JsonIgnore  // Esclude questo campo dalla serializzazione
    private List<StoricoEvento> storicoEventi;
}
