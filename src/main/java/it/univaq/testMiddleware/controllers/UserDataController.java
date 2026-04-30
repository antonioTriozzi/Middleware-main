package it.univaq.testMiddleware.controllers;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.univaq.testMiddleware.DTO.UserDTO;
import it.univaq.testMiddleware.services.UserDataService;

@RestController
@RequestMapping("/api/userdata")
public class UserDataController {

    private static final Logger log = LoggerFactory.getLogger(UserDataController.class);

    private final UserDataService userDataService;
    private final ObjectMapper objectMapper; // Inietta l'ObjectMapper

    public UserDataController(UserDataService userDataService, ObjectMapper objectMapper) {
        this.userDataService = userDataService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/save")
    public void saveUserData(@RequestBody UserDTO userDto) {
        try {
            // --- ECCO IL LOG ---
            String jsonRicevuto = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(userDto);
            log.info("Ricevuto JSON per il salvataggio utente:\n{}", jsonRicevuto);
            // ------------------
        } catch (Exception e) {
            log.error("Errore durante la conversione del DTO ricevuto in JSON per il log", e);
        }

        // La logica di business rimane la stessa
        userDataService.save(userDto);
    }
}