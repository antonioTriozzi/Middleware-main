package it.univaq.testMiddleware.controllers;

import it.univaq.testMiddleware.models.StoricoEvento;
import it.univaq.testMiddleware.models.Dispositivo;
import it.univaq.testMiddleware.models.User;
import it.univaq.testMiddleware.repositories.StoricoEventoRepository;
import it.univaq.testMiddleware.repositories.DispositivoRepository;
import it.univaq.testMiddleware.repositories.UserRepository;
import it.univaq.testMiddleware.services.UserService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/events")
public class EventController {

    @Autowired
    private StoricoEventoRepository storicoEventoRepository;

    @Autowired
    private DispositivoRepository dispositivoRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    /**
     * Endpoint per creare un evento per un dispositivo e inviare una notifica all'applicazione.
     * URL di esempio: POST /api/events/device/{deviceId}?expoToken=XXXX
     * Il body della richiesta dovrà contenere in formato JSON i campi:
     * - descrizione: String (descrizione dell'evento da salvare)
     * - title: String (titolo per la notifica, se non presente viene usato "Default Title")
     * - body: String (contenuto della notifica, se non presente viene usato "Default Body")
     */
    @PostMapping("/device/{deviceId}")
    @Transactional
    public ResponseEntity<?> createEvent(@PathVariable Long deviceId,
                                         @RequestParam("expoToken") String expoToken,
                                         @RequestBody EventCreationRequest request) {
        Optional<Dispositivo> dispositivoOpt = dispositivoRepository.findById(deviceId);
        if (!dispositivoOpt.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Dispositivo con ID " + deviceId + " non trovato.");
        }
        Dispositivo dispositivo = dispositivoOpt.get();

        // Crea l'evento
        StoricoEvento evento = new StoricoEvento();
        evento.setDescrizione(request.getDescrizione());
        evento.setConfermaLettura(false);
        evento.setTimestamp(Instant.now());
        evento.setDispositivo(dispositivo);
        storicoEventoRepository.save(evento);

        // Prepara il messaggio di notifica (simile all'esempio PHP)
        Map<String, Object> message = new HashMap<>();
        message.put("to", expoToken);
        message.put("sound", "default");
        message.put("title", (request.getTitle() != null && !request.getTitle().isEmpty()) ? request.getTitle() : "Default Title");
        message.put("body", (request.getBody() != null && !request.getBody().isEmpty()) ? request.getBody() : "Default Body");
        Map<String, String> dataMap = new HashMap<>();
        dataMap.put("someData", "goes here");
        message.put("data", dataMap);

        // Simula uno sleep di 5 secondi come nell'esempio PHP
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Invio della notifica tramite chiamata REST
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(message, headers);
        String expoUrl = "https://exp.host/--/api/v2/push/send";
        ResponseEntity<String> response = restTemplate.postForEntity(expoUrl, entity, String.class);

        Map<String, Object> result = new HashMap<>();
        result.put("eventoCreato", evento);
        result.put("rispostaNotifica", response.getBody());
        return ResponseEntity.ok(result);
    }

    /**
     * Endpoint per ottenere lo storico degli eventi relativi ai dispositivi dei condomini gestiti da un utente.
     * URL di esempio: GET /api/events/user/{userId}
     */
    @GetMapping("/user")
    @Transactional
    public ResponseEntity<?> getEventsForAuthenticatedUser() {
        // Recupera il nome utente dal token JWT
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        // Recupera l'utente dal database tramite il service (o repository)
        User user = userService.findByUsername(username);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Utente non trovato");
        }
        // Recupera gli eventi dei dispositivi dei condomini dove l'utente è amministratore
        List<StoricoEvento> eventi = storicoEventoRepository.findByDispositivo_Condominio_Amministratore_Id(user.getId());
        return ResponseEntity.ok(eventi);
    }

    /**
     * Endpoint per segnare un evento come letto.
     * URL di esempio: PUT /api/events/{eventId}/markAsRead
     */
    @PutMapping("/{eventId}/markAsRead")
    @Transactional
    public ResponseEntity<StoricoEvento> markEventAsRead(@PathVariable Long eventId) {
        Optional<StoricoEvento> eventoOpt = storicoEventoRepository.findById(eventId);
        if (!eventoOpt.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        StoricoEvento evento = eventoOpt.get();
        evento.setConfermaLettura(true);
        storicoEventoRepository.save(evento);
        return ResponseEntity.ok(evento);
    }

    /**
     * DTO per la richiesta di creazione evento e notifica.
     */
    public static class EventCreationRequest {
        private String descrizione;
        private String title;
        private String body;

        public String getDescrizione() {
            return descrizione;
        }
        public void setDescrizione(String descrizione) {
            this.descrizione = descrizione;
        }
        public String getTitle() {
            return title;
        }
        public void setTitle(String title) {
            this.title = title;
        }
        public String getBody() {
            return body;
        }
        public void setBody(String body) {
            this.body = body;
        }
    }
}
