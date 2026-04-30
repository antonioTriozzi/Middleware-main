package it.univaq.testMiddleware.controllers;

import it.univaq.testMiddleware.DTO.*;
import it.univaq.testMiddleware.models.*;
import it.univaq.testMiddleware.repositories.CondominioRepository;
import it.univaq.testMiddleware.repositories.DatoSensoreRepository;
import it.univaq.testMiddleware.repositories.DispositivoRepository;
import it.univaq.testMiddleware.services.UserService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/general")
public class CondominioController {

    @Autowired
    private CondominioRepository condominioRepository;

    @Autowired
    private DispositivoRepository dispositivoRepository;

    @Autowired
    private DatoSensoreRepository datoSensoreRepository;

    @Autowired
    private UserService userService;

    // Endpoint già esistente: /api/general/my
    @GetMapping("/my")
    public ResponseEntity<?> getCondominiForAuthenticatedUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userService.findByUsername(username);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Utente non trovato");
        }
        List<Condominio> condomini;
        // Mobile: CLIENT deve vedere i "suoi" condomini (derivati da idCondominio o dai dispositivi owner)
        if ("CLIENT".equalsIgnoreCase(user.getRuolo())) {
            // Unione: il CLIENT può essere associato a un condominio "statico" (user.idCondominio)
            // e/o risultare owner di dispositivi (derivati dall'ingest consumi).
            Map<Long, Condominio> unique = new LinkedHashMap<>();

            if (user.getIdCondominio() != null) {
                condominioRepository.findById(user.getIdCondominio())
                        .ifPresent(c -> unique.put(c.getIdCondominio(), c));
            }

            List<Condominio> byOwner = dispositivoRepository.findDistinctCondominiByOwnerId(user.getId());
            for (Condominio c : byOwner) {
                if (c != null && c.getIdCondominio() != null) {
                    unique.putIfAbsent(c.getIdCondominio(), c);
                }
            }

            condomini = new ArrayList<>(unique.values());
        } else {
            // ADMIN: condomini gestiti (amministratore)
            condomini = condominioRepository.findByAmministratore(user);
        }
        List<CondominioDTO> condominiDTO = condomini.stream().map(this::mapToCondominioDTOWithoutAdmin).collect(Collectors.toList());

        UserDTO userDTO = mapToUserDTO(user);

        UserCondominiResponse response = new UserCondominiResponse();
        response.setUser(userDTO);
        response.setCondomini(condominiDTO);

        return ResponseEntity.ok(response);
    }

    // Endpoint: /api/general/condominio/{id}
    @GetMapping("/condominio/{id}")
    @Transactional
    public ResponseEntity<?> getCondominioDetails(@PathVariable("id") Long id) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userService.findByUsername(username);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Utente non trovato");
        }
        Optional<Condominio> condOpt = condominioRepository.findById(id);
        if (!condOpt.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Condominio non trovato");
        }
        Condominio condominio = condOpt.get();
        // Recupera i dispositivi per il condominio (si assume l'esistenza di un metodo nel repository oppure si usa condominio.getDispositivi())
        List<Dispositivo> dispositivi = dispositivoRepository.findByCondominio(condominio);

        // Mappatura DTO
        CondominioDTO condominioDTO = mapToCondominioDTO(condominio);
        List<DispositivoDTO> dispositiviDTO = dispositivi.stream()
                .map(this::mapToDispositivoDTOWithoutParametri)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("user", mapToUserDTO(user));
        response.put("condominio", condominioDTO);
        response.put("dispositivi", dispositiviDTO);

        return ResponseEntity.ok(response);
    }

    // Endpoint: /api/general/condominio/{id}/dispositivo/{idDispositivo}
    @GetMapping("/condominio/{id}/dispositivo/{idDispositivo}")
    @Transactional
    public ResponseEntity<?> getDispositivoDetails(@PathVariable("id") Long id,
                                                   @PathVariable("idDispositivo") Long idDispositivo) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userService.findByUsername(username);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Utente non trovato");
        }
        Optional<Condominio> condOpt = condominioRepository.findById(id);
        if (!condOpt.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Condominio non trovato");
        }
        Condominio condominio = condOpt.get();

        Optional<Dispositivo> dispOpt = dispositivoRepository.findById(idDispositivo);
        if (!dispOpt.isPresent() || !dispOpt.get().getCondominio().getIdCondominio().equals(condominio.getIdCondominio())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Dispositivo non trovato per questo condominio");
        }
        Dispositivo dispositivo = dispOpt.get();

        // Mappatura DTO
        CondominioDTO condominioDTO = mapToCondominioDTO(condominio);
        DispositivoDTO dispositivoDTO = mapToDispositivoDTO(dispositivo);
        // Mappa i parametri presenti nel dispositivo
        List<ParametroDTO> parametriDTO = dispositivo.getParametriDispositivo().stream()
                .map(this::mapToParametroDTOWithoutValori)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("user", mapToUserDTO(user));
        response.put("condominio", condominioDTO);
        response.put("dispositivo", dispositivoDTO);
        response.put("parametri", parametriDTO);

        return ResponseEntity.ok(response);
    }

    // Endpoint: /api/general/condominio/{id}/dispositivo/{idDispositivo}/parametro/{idParametro}?startDate=...&endDate=...
    @GetMapping("/condominio/{id}/dispositivo/{idDispositivo}/parametro/{idParametro}")
    @Transactional
    public ResponseEntity<?> getParametroSensorData(@PathVariable("id") Long id,
                                                    @PathVariable("idDispositivo") Long idDispositivo,
                                                    @PathVariable("idParametro") Long idParametro,
                                                    @RequestParam(value = "startDate", required = false)
                                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) String startDateStr,
                                                    @RequestParam(value = "endDate", required = false)
                                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) String endDateStr) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userService.findByUsername(username);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Utente non trovato");
        }
        Optional<Condominio> condOpt = condominioRepository.findById(id);
        if (!condOpt.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Condominio non trovato");
        }
        Condominio condominio = condOpt.get();

        Optional<Dispositivo> dispOpt = dispositivoRepository.findById(idDispositivo);
        if (!dispOpt.isPresent() || !dispOpt.get().getCondominio().getIdCondominio().equals(condominio.getIdCondominio())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Dispositivo non trovato per questo condominio");
        }
        Dispositivo dispositivo = dispOpt.get();

        // Cerca il parametro tra quelli del dispositivo
        Optional<ParametroDispositivo> paramOpt = dispositivo.getParametriDispositivo().stream()
                .filter(p -> p.getIdParametro().equals(idParametro))
                .findFirst();
        if (!paramOpt.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Parametro non trovato per questo dispositivo");
        }
        ParametroDispositivo parametro = paramOpt.get();

        // Gestione del range di date
        Instant start = null, end = null;
        try {
            if (startDateStr != null && !startDateStr.isEmpty()) {
                start = Instant.parse(startDateStr);
            }
            if (endDateStr != null && !endDateStr.isEmpty()) {
                end = Instant.parse(endDateStr);
            }
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body("Formato data non valido. Utilizzare ISO-8601.");
        }

        List<DatoSensore> datiSensore;
        if (start != null && end != null) {
            // Si assume l'esistenza di questo metodo nel repository
            datiSensore = datoSensoreRepository.findByParametroAndTimestampBetween(parametro, start, end);
        } else {
            datiSensore = datoSensoreRepository.findByParametro(parametro);
        }
        // Mappatura in DTO per i dati sensore
        List<SensorValueDTO> sensorValues = datiSensore.stream()
                .map(this::mapToSensorValueDTO)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("user", mapToUserDTO(user));
        response.put("condominio", mapToCondominioDTO(condominio));
        response.put("dispositivo", mapToDispositivoDTO(dispositivo));
        response.put("parametro", mapToParametroDTO(parametro));
        response.put("valoriSensore", sensorValues);

        return ResponseEntity.ok(response);
    }

    // ---------------------- METODI DI MAPPING (helper privati) ----------------------

    private UserDTO mapToUserDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setNome(user.getNome());
        dto.setCognome(user.getCognome());
        dto.setEmail(user.getEmail());
        dto.setRuolo(user.getRuolo());
        return dto;
    }

    private CondominioDTO mapToCondominioDTO(Condominio condominio) {
        CondominioDTO dto = new CondominioDTO();
        dto.setIdCondominio(condominio.getIdCondominio());
        dto.setNome(condominio.getNome());
        dto.setIndirizzo(condominio.getIndirizzo());
        dto.setAnnoCostruzione(condominio.getAnnoCostruzione());
        dto.setClasseEnergetica(condominio.getClasseEnergetica());
        dto.setNumeroPiani(condominio.getNumeroPiani());
        dto.setRegolamenti(condominio.getRegolamenti());
        dto.setSuperficie(condominio.getSuperficie());
        dto.setUnitaAbitative(condominio.getUnitaAbitative());

        // Imposta le coordinate se presenti
        dto.setLatitudine(condominio.getLatitudine());
        dto.setLongitudine(condominio.getLongitudine());

        if (condominio.getAmministratore() != null) {
            dto.setAmministratore(mapToUserDTO(condominio.getAmministratore()));
        }
        return dto;
    }


    // Versione senza amministratore (per lista di condomini)
    private CondominioDTO mapToCondominioDTOWithoutAdmin(Condominio condominio) {
        CondominioDTO dto = new CondominioDTO();
        dto.setIdCondominio(condominio.getIdCondominio());
        dto.setNome(condominio.getNome());
        dto.setIndirizzo(condominio.getIndirizzo());
        dto.setAnnoCostruzione(condominio.getAnnoCostruzione());
        dto.setClasseEnergetica(condominio.getClasseEnergetica());
        dto.setNumeroPiani(condominio.getNumeroPiani());
        dto.setRegolamenti(condominio.getRegolamenti());
        dto.setSuperficie(condominio.getSuperficie());
        dto.setUnitaAbitative(condominio.getUnitaAbitative());
        // Imposta le coordinate
        dto.setLatitudine(condominio.getLatitudine());
        dto.setLongitudine(condominio.getLongitudine());
        return dto;
    }

    private DispositivoDTO mapToDispositivoDTO(Dispositivo dispositivo) {
        DispositivoDTO dto = new DispositivoDTO();
        dto.setIdDispositivo(dispositivo.getIdDispositivo());
        dto.setAssetId(dispositivo.getAssetId());
        dto.setAssetName(dispositivo.getAssetName());
        dto.setExternalDeviceId(dispositivo.getExternalDeviceId());
        dto.setNome(dispositivo.getNome());
        dto.setMarca(dispositivo.getMarca());
        dto.setModello(dispositivo.getModello());
        dto.setTipo(dispositivo.getTipo());
        dto.setStato(dispositivo.getStato());
        if (dispositivo.getOwner() != null) {
            dto.setOwnerUserId(dispositivo.getOwner().getId());
            dto.setOwnerMail(dispositivo.getOwner().getEmail());
        }
        // Per evitare un mapping ricorsivo, si può decidere di non mappare qui i parametri
        // oppure lasciarli vuoti e popolarli nel dettaglio specifico
        return dto;
    }

    // Mapping per il metodo /condominio/{id}/ (lista dispositivi senza parametri)
    private DispositivoDTO mapToDispositivoDTOWithoutParametri(Dispositivo dispositivo) {
        return mapToDispositivoDTO(dispositivo);
    }

    private ParametroDTO mapToParametroDTO(ParametroDispositivo parametro) {
        ParametroDTO dto = new ParametroDTO();
        dto.setNome(parametro.getNome());
        dto.setTipologia(parametro.getTipologia());
        dto.setUnitaMisura(parametro.getUnitaMisura());
        // Se vuoi includere i valori sensore qui (es. nel dettaglio completo) puoi mappare anche la lista
        // dto.setValori(...);
        return dto;
    }

    // Mapping per il dispositivo con lista parametri vuota (che verrà restituita separatamente)
    private ParametroDTO mapToParametroDTOWithoutValori(ParametroDispositivo parametro) {
        return mapToParametroDTO(parametro);
    }

    private SensorValueDTO mapToSensorValueDTO(DatoSensore datoSensore) {
        SensorValueDTO dto = new SensorValueDTO();
        dto.setValore(datoSensore.getValore());
        dto.setTimestamp(datoSensore.getTimestamp());
        return dto;
    }
}