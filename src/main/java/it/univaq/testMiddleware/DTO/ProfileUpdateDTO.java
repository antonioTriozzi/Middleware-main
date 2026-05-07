package it.univaq.testMiddleware.DTO;

import lombok.Data;

/**
 * Aggiornamento anagrafica: campi opzionali. {@code null} = non modificare.
 * Per cancellare testo esplicitamente usa stringa vuota (per la data usa stringa vuota).
 */
@Data
public class ProfileUpdateDTO {
    private String nome;
    private String cognome;
    private String numeroDiTelefono;
    /** Formato ISO {@code yyyy-MM-dd}; vuoto per rimuovere la data. */
    private String dataNascita;
}
