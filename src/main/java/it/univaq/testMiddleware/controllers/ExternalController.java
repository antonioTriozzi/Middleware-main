package it.univaq.testMiddleware.controllers;

import it.univaq.testMiddleware.DTO.*;
import it.univaq.testMiddleware.models.*;
import it.univaq.testMiddleware.repositories.CondominioRepository;
import it.univaq.testMiddleware.repositories.DispositivoRepository;
import it.univaq.testMiddleware.services.UserService;
import it.univaq.testMiddleware.adapter.ExternalDataAdapter;
import it.univaq.testMiddleware.adapter.ExternalDataAdapterFactory;
import it.univaq.testMiddleware.adapter.ExternalDataResponse;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/external")
public class ExternalController {

    @Autowired
    private CondominioRepository condominioRepository;

    @Autowired
    private DispositivoRepository dispositivoRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private ExternalDataAdapterFactory adapterFactory;

    /**
     * Endpoint per recuperare dati esterni con metodo POST.
     * Necessario se il tuo adapter richiede un corpo JSON per la richiesta.
     */
    @Transactional
    @PostMapping("/condominio/{id}/dispositivo/{idDispositivo}")
    public ResponseEntity<?> fetchExternalDataPost(@PathVariable("id") Long condominioId,
                                                   @PathVariable("idDispositivo") Long dispositivoId) {
        // Recupera l'utente autenticato
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userService.findByUsername(username);
        if (user == null) {
            return ResponseEntity.status(404).body("Utente non trovato");
        }
        // Recupera il condominio
        Condominio condominio = condominioRepository.findById(condominioId)
                .orElse(null);
        if (condominio == null) {
            return ResponseEntity.status(404).body("Condominio non trovato");
        }
        // Recupera il dispositivo e verifica che appartenga al condominio
        Dispositivo dispositivo = dispositivoRepository.findById(dispositivoId)
                .orElse(null);
        if (dispositivo == null || !dispositivo.getCondominio().getIdCondominio().equals(condominioId)) {
            return ResponseEntity.status(404).body("Dispositivo non trovato per il condominio specificato");
        }

        // Seleziona l'adapter in base al dispositivo (ad es. tramite un campo "adapterType")
        ExternalDataAdapter adapter = adapterFactory.getAdapter(dispositivo);
        if (adapter == null) {
            return ResponseEntity.badRequest().body("Nessun adapter trovato per questo dispositivo");
        }

        ExternalDataResponse responseDto = adapter.fetchAndMapData(condominio, dispositivo, user);
        return ResponseEntity.ok(responseDto);
    }
    
    /**
     * Endpoint per recuperare dati esterni con metodo GET.
     * Utile se la tua API esterna non richiede un corpo JSON per la richiesta.
     */
    @Transactional
    @GetMapping("/condominio/{id}/dispositivo/{idDispositivo}")
    public ResponseEntity<?> fetchExternalDataGet(@PathVariable("id") Long condominioId,
                                                  @PathVariable("idDispositivo") Long dispositivoId) {
        // Recupera l'utente autenticato
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userService.findByUsername(username);
        if (user == null) {
            return ResponseEntity.status(404).body("Utente non trovato");
        }
        // Recupera il condominio
        Condominio condominio = condominioRepository.findById(condominioId)
                .orElse(null);
        if (condominio == null) {
            return ResponseEntity.status(404).body("Condominio non trovato");
        }
        // Recupera il dispositivo e verifica che appartenga al condominio
        Dispositivo dispositivo = dispositivoRepository.findById(dispositivoId)
                .orElse(null);
        if (dispositivo == null || !dispositivo.getCondominio().getIdCondominio().equals(condominioId)) {
            return ResponseEntity.status(404).body("Dispositivo non trovato per il condominio specificato");
        }

        // Seleziona l'adapter in base al dispositivo (ad es. tramite un campo "adapterType")
        ExternalDataAdapter adapter = adapterFactory.getAdapter(dispositivo);
        if (adapter == null) {
            return ResponseEntity.badRequest().body("Nessun adapter trovato per questo dispositivo");
        }

        ExternalDataResponse responseDto = adapter.fetchAndMapData(condominio, dispositivo, user);
        return ResponseEntity.ok(responseDto);
    }
}