package it.univaq.testMiddleware.controllers;

import it.univaq.testMiddleware.models.SessioneRicarica;
import it.univaq.testMiddleware.repositories.SessioneRicaricaRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/sessioni-ricarica")
public class SessioneRicaricaController {

    private final SessioneRicaricaRepository sessioneRicaricaRepository;

    public SessioneRicaricaController(SessioneRicaricaRepository sessioneRicaricaRepository) {
        this.sessioneRicaricaRepository = sessioneRicaricaRepository;
    }

    // Endpoint per ottenere tutte le sessioni di ricarica per una data colonnina
    @GetMapping("/colonnina/{colonninaId}")
    public ResponseEntity<List<SessioneRicarica>> getSessioniByColonnina(@PathVariable Long colonninaId) {
        // Usa il metodo aggiornato con il nome corretto della proprietà dell'entità Dispositivo
        List<SessioneRicarica> sessioni = sessioneRicaricaRepository.findByColonnina_IdDispositivo(colonninaId);
        return ResponseEntity.ok(sessioni);
    }
}
