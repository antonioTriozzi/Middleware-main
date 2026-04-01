package it.univaq.testMiddleware.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "condomini")
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "dispositivi")
public class Condominio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_condominio")
    private Long idCondominio;

    private String nome;
    private String indirizzo;

    // Nuovi campi aggiunti
    private String classeEnergetica;
    private Integer unitaAbitative;     // Numero di unità abitative
    private Integer annoCostruzione;
    private Integer numeroPiani;
    private Double superficie;           // Superficie in metri quadrati

    @Column(length = 2000)
    private String regolamenti;          // Norme e regolamenti condominiali

    // Coordinate per la mappa
    private Double latitudine;
    private Double longitudine;

    // Relazione con User (l'amministratore)
    @ManyToOne
    @JoinColumn(name = "amministratore_id") // FK verso "utenti"
    @JsonIgnoreProperties("tokens")
    private User amministratore;

    // Relazione 1-N con Dispositivo: escludo la serializzazione per evitare problemi di lazy loading
    @JsonIgnore
    @OneToMany(mappedBy = "condominio", cascade = CascadeType.ALL)
    private List<Dispositivo> dispositivi;
}
