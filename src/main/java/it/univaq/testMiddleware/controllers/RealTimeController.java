package it.univaq.testMiddleware.controllers;

import it.univaq.testMiddleware.DTO.*;
import it.univaq.testMiddleware.models.*;
import it.univaq.testMiddleware.repositories.CondominioRepository;
import it.univaq.testMiddleware.repositories.DispositivoRepository;
import it.univaq.testMiddleware.repositories.DatoSensoreRepository;
import it.univaq.testMiddleware.services.UserService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/realtime")
public class RealTimeController {

    @Autowired
    private CondominioRepository condominioRepository;

    @Autowired
    private DispositivoRepository dispositivoRepository;

    @Autowired
    private DatoSensoreRepository datoSensoreRepository;

    @Autowired
    private UserService userService;

    @Transactional
    @GetMapping("/condominio/{id}/dispositivo/{idDispositivo}")
    public ResponseEntity<?> getRealtimeData(@PathVariable("id") Long condominioId,
                                             @PathVariable("idDispositivo") Long dispositivoId) {
        // Recupera l'utente autenticato
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userService.findByUsername(username);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Utente non trovato");
        }

        // Recupera il condominio
        Optional<Condominio> condOpt = condominioRepository.findById(condominioId);
        if (!condOpt.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Condominio non trovato");
        }
        Condominio condominio = condOpt.get();

        // Recupera il dispositivo e verifica che appartenga al condominio
        Optional<Dispositivo> dispOpt = dispositivoRepository.findById(dispositivoId);
        if (!dispOpt.isPresent() || !dispOpt.get().getCondominio().getIdCondominio().equals(condominioId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Dispositivo non trovato per il condominio specificato");
        }
        Dispositivo dispositivo = dispOpt.get();

        // Mappatura degli oggetti in DTO
        DispositivoDTO dispositivoDTO = mapToDispositivoDTO(dispositivo);
        CondominioDTO condominioDTO = mapToCondominioDTO(condominio);
        UserDTO userDTO = mapToUserDTO(user);

        // Per ogni parametro del dispositivo, recupera l'ultimo dato e genera (o riutilizza) il nuovo valore
        List<Map<String, Object>> parametriList = new ArrayList<>();
        Random random = new Random();

        if (dispositivo.getParametriDispositivo() != null) {
            for (ParametroDispositivo parametro : dispositivo.getParametriDispositivo()) {
                Map<String, Object> paramData = new HashMap<>();
                ParametroDTO parametroDTO = mapToParametroDTO(parametro);

                double min = parametro.getValMin() != null ? parametro.getValMin() : 0.0;
                double max = parametro.getValMax() != null ? parametro.getValMax() : 100.0;
                double maxDelta = parametro.getMaxDelta() != null ? parametro.getMaxDelta() : (max - min) * 0.1; // default: 10% del range

                Instant now = Instant.now();

                // Recupera l'ultimo dato sensore per questo parametro
                DatoSensore lastDato = datoSensoreRepository.findFirstByParametroOrderByTimestampDesc(parametro);
                boolean generateNewData = true;
                double baseValue;

                if (lastDato != null) {
                    Duration diff = Duration.between(lastDato.getTimestamp(), now);
                    if (diff.toMinutes() < 10) {
                        // Se l'ultimo dato è stato registrato da meno di 10 minuti, non genera nuovi dati
                        generateNewData = false;
                        try {
                            baseValue = Double.parseDouble(lastDato.getValore());
                        } catch (NumberFormatException e) {
                            baseValue = min + (max - min) * random.nextDouble();
                        }
                    } else {
                        generateNewData = true;
                        try {
                            baseValue = Double.parseDouble(lastDato.getValore());
                        } catch (NumberFormatException e) {
                            baseValue = min + (max - min) * random.nextDouble();
                        }
                    }
                } else {
                    generateNewData = true;
                    baseValue = min + (max - min) * random.nextDouble();
                }

                DatoSensore datoToUse;
                if (generateNewData) {
                    // Calcola una variazione casuale compresa tra -maxDelta e +maxDelta
                    double delta = -maxDelta + 2 * maxDelta * random.nextDouble();
                    double newValue = baseValue + delta;
                    // Clampa il nuovo valore nell'intervallo [min, max]
                    newValue = Math.max(min, Math.min(newValue, max));

                    DatoSensore newDato = new DatoSensore();
                    newDato.setValore(String.valueOf((int)newValue)); // conversione a intero; modificare se necessario
                    newDato.setTimestamp(now);
                    newDato.setParametro(parametro);
                    // Salva il nuovo dato nel DB (l'id verrà generato automaticamente)
                    datoToUse = datoSensoreRepository.save(newDato);
                } else {
                    datoToUse = lastDato;
                }

                // Mappatura in DTO del dato sensore
                SensorValueDTO sensorValueDTO = new SensorValueDTO();
                sensorValueDTO.setValore(datoToUse.getValore());
                sensorValueDTO.setTimestamp(datoToUse.getTimestamp());

                List<SensorValueDTO> sensorValues = Collections.singletonList(sensorValueDTO);
                paramData.put("parametro", parametroDTO);
                paramData.put("valoriSensore", sensorValues);
                parametriList.add(paramData);
            }
        }

        // Costruisce la risposta finale
        Map<String, Object> response = new HashMap<>();
        response.put("dispositivo", dispositivoDTO);
        response.put("condominio", condominioDTO);
        response.put("parametri", parametriList);
        response.put("user", userDTO);

        return ResponseEntity.ok(response);
    }

    // ------------------- METODI DI MAPPING -------------------

    private UserDTO mapToUserDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        return dto;
    }

    private CondominioDTO mapToCondominioDTO(Condominio condominio) {
        CondominioDTO dto = new CondominioDTO();
        dto.setIdCondominio(condominio.getIdCondominio());
        dto.setNome(condominio.getNome());
        dto.setIndirizzo(condominio.getIndirizzo());
        if (condominio.getAmministratore() != null) {
            dto.setAmministratore(mapToUserDTO(condominio.getAmministratore()));
        }
        return dto;
    }

    private DispositivoDTO mapToDispositivoDTO(Dispositivo dispositivo) {
        DispositivoDTO dto = new DispositivoDTO();
        dto.setIdDispositivo(dispositivo.getIdDispositivo());
        dto.setNome(dispositivo.getNome());
        dto.setMarca(dispositivo.getMarca());
        dto.setModello(dispositivo.getModello());
        dto.setTipo(dispositivo.getTipo());
        dto.setStato(dispositivo.getStato());
        return dto;
    }

    private ParametroDTO mapToParametroDTO(ParametroDispositivo parametro) {
        ParametroDTO dto = new ParametroDTO();
        dto.setNome(parametro.getNome());
        dto.setTipologia(parametro.getTipologia());
        dto.setUnitaMisura(parametro.getUnitaMisura());
        return dto;
    }
}
