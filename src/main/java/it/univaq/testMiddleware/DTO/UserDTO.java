package it.univaq.testMiddleware.DTO;

import lombok.Data;

// DTO per l'utente (amministratore)
@Data
public class UserDTO {
    private Long id;
    private String username;

    // Nuovi campi per il profilo
    private String nome;
    private String cognome;
    private String email;
    private String ruolo;
    private String password;

    /** Edificio (stesso ID usato nel gateway / tabella condomini). */
    private Long idCondominio;

    // Campo calcolato: numero di condomini gestiti
    private int numeroCondominiGestiti;
}
