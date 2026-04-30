package it.univaq.testMiddleware.controllers;

import it.univaq.testMiddleware.models.Dispositivo;
import it.univaq.testMiddleware.models.StoricoEvento;
import it.univaq.testMiddleware.models.User;
import it.univaq.testMiddleware.repositories.DispositivoRepository;
import it.univaq.testMiddleware.repositories.StoricoEventoRepository;
import it.univaq.testMiddleware.services.UserService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@RestController
@RequestMapping("/api/events")
public class EventController {

    @Autowired
    private StoricoEventoRepository storicoEventoRepository;

    @Autowired
    private DispositivoRepository dispositivoRepository;

    @Autowired
    private UserService userService;

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

    /**
     * POST /api/events/device/{deviceId}?expoToken=...
     */
    @PostMapping("/device/{deviceId}")
    @Transactional
    public ResponseEntity<?> createEvent(
            @PathVariable("deviceId") Long deviceId,
            @RequestParam("expoToken") String expoToken,
            @RequestBody EventCreationRequest creationRequest) {
        Objects.requireNonNull(deviceId, "deviceId");
        Optional<Dispositivo> dispositivoOpt = dispositivoRepository.findById(deviceId);
        if (!dispositivoOpt.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Dispositivo con ID " + deviceId + " non trovato.");
        }
        Dispositivo dispositivo = dispositivoOpt.get();

        StoricoEvento evento = new StoricoEvento();
        evento.setDescrizione(creationRequest.getDescrizione());
        evento.setConfermaLettura(false);
        evento.setTimestamp(Instant.now());
        evento.setDispositivo(dispositivo);
        storicoEventoRepository.save(evento);

        Map<String, Object> message = new HashMap<>();
        message.put("to", expoToken);
        message.put("sound", "default");
        message.put("title",
                (creationRequest.getTitle() != null && !creationRequest.getTitle().isEmpty())
                        ? creationRequest.getTitle() : "Default Title");
        message.put("body",
                (creationRequest.getBody() != null && !creationRequest.getBody().isEmpty())
                        ? creationRequest.getBody() : "Default Body");
        Map<String, String> dataMap = new HashMap<>();
        dataMap.put("someData", "goes here");
        message.put("data", dataMap);

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        List<MediaType> accept = Objects.requireNonNull(List.of(MediaType.APPLICATION_JSON));
        headers.setAccept(accept);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(message, headers);
        String expoUrl = "https://exp.host/--/api/v2/push/send";
        ResponseEntity<String> response = restTemplate.postForEntity(expoUrl, entity, String.class);

        Map<String, Object> result = new HashMap<>();
        result.put("eventoCreato", evento);
        result.put("rispostaNotifica", response.getBody());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/user")
    @Transactional
    public ResponseEntity<?> getEventsForAuthenticatedUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userService.findByUsername(username);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Utente non trovato");
        }
        List<StoricoEvento> eventi =
                storicoEventoRepository.findByDispositivo_Condominio_Amministratore_Id(user.getId());
        return ResponseEntity.ok(eventi);
    }

    @PutMapping("/{eventId}/markAsRead")
    @Transactional
    public ResponseEntity<StoricoEvento> markEventAsRead(@PathVariable("eventId") Long eventId) {
        Objects.requireNonNull(eventId, "eventId");
        Optional<StoricoEvento> eventoOpt = storicoEventoRepository.findById(eventId);
        if (!eventoOpt.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        StoricoEvento evento = eventoOpt.get();
        evento.setConfermaLettura(true);
        storicoEventoRepository.save(evento);
        return ResponseEntity.ok(evento);
    }
}
