package it.univaq.testMiddleware.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.Date;
import java.util.List;

@Entity
@Table(name = "user")  // Per mappare la tabella "utenti" del DB
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"tokens", "condominiGestiti"})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_utente") // Se vuoi rispettare il nome della PK "id_utente" nel DB
    private Long id;

    @Column(unique = true)
    private String username;

    // Nuovi campi per il profilo utente
    private String nome;
    private String cognome;
    @Column(unique = true)
    private String email;
    private String numeroDiTelefono;
    private String ruolo;
    private Long idCondominio; // Per collegare l'utente a un condominio specifico

    @Temporal(TemporalType.DATE)
    private Date dataNascita;

    @Column(nullable = false)
    @JsonIgnore
    private String password; // Deve essere criptata con BCrypt!

    // Lista dei token associati all'utente (già presente)
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    @JsonIgnore  // Aggiungi questa annotazione per non serializzare tokens
    private List<Token> tokens;

    // Relazione bidirezionale con Condominio (opzionale, se vuoi vedere i condomini gestiti)
    @OneToMany(mappedBy = "amministratore", cascade = CascadeType.ALL)
    @JsonIgnore  // Aggiungi questa annotazione per non serializzare tokens
    private List<Condominio> condominiGestiti;
}
