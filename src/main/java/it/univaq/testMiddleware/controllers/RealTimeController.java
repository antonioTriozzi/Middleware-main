package it.univaq.testMiddleware.controllers;

import it.univaq.testMiddleware.DTO.*;
import it.univaq.testMiddleware.models.*;
import it.univaq.testMiddleware.repositories.CondominioRepository;
import it.univaq.testMiddleware.repositories.DispositivoRepository;
import it.univaq.testMiddleware.repositories.DatoSensoreRepository;
import it.univaq.testMiddleware.services.UserService;
import jakarta.transaction.Transactional;
import it.univaq.testMiddleware.services.SensorGaugeDefaults;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    /**
     * Se true, mantiene il vecchio comportamento demo: genera variazioni casuali e scrive nuovi {@link DatoSensore}.
     * Default false: l'endpoint restituisce solo l'ultimo valore già ingested (JSON → DB) senza modificarlo.
     */
    @Value("${app.realtime.simulate-readings:false}")
    private boolean simulateReadings;

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

        List<ParametroRealtimeDTO> parametriList = simulateReadings
                ? buildSimulatedRealtime(dispositivo)
                : buildReadOnlyRealtime(dispositivo);

        // Costruisce la risposta finale
        Map<String, Object> response = new HashMap<>();
        response.put("dispositivo", dispositivoDTO);
        response.put("condominio", condominioDTO);
        response.put("parametri", parametriList);
        response.put("user", userDTO);

        return ResponseEntity.ok(response);
    }

    /**
     * Ultimo valore per parametro così com'è nel DB (nessuna scrittura). I range gauge usano val_min/val_max se presenti,
     * altrimenti default per unità; se il valore supera il massimo teorico, il massimo viene esteso leggermente.
     */
    private List<ParametroRealtimeDTO> buildReadOnlyRealtime(Dispositivo dispositivo) {
        List<ParametroRealtimeDTO> parametriList = new ArrayList<>();
        if (dispositivo.getParametriDispositivo() == null) {
            return parametriList;
        }
        for (ParametroDispositivo parametro : dispositivo.getParametriDispositivo()) {
            SensorGaugeDefaults.Range def = SensorGaugeDefaults.infer(
                    parametro.getUnitaMisura(), parametro.getNome());
            double min = parametro.getValMin() != null ? parametro.getValMin() : def.min();
            double max = parametro.getValMax() != null ? parametro.getValMax() : def.max();
            double maxDelta = parametro.getMaxDelta() != null ? parametro.getMaxDelta() : def.maxDelta();

            DatoSensore lastDato = datoSensoreRepository.findFirstByParametroOrderByTimestampDesc(parametro);
            // Se non esiste nessun campione per questo parametro, lasciamo valore/timestamp null:
            // evita di mostrare "Misurato il: adesso" quando in realtà non ci sono dati.
            String valore = lastDato != null ? lastDato.getValore() : null;
            Instant ts = lastDato != null ? lastDato.getTimestamp() : null;

            if (lastDato != null && lastDato.getValore() != null) {
                try {
                    double v = Double.parseDouble(lastDato.getValore().replace(",", ".").trim());
                    if (v > max) {
                        max = Math.max(v * 1.05, max);
                    }
                    if (v < min) {
                        min = Math.min(v, min);
                    }
                } catch (NumberFormatException ignored) {
                }
            }

            ParametroRealtimeDTO dto = new ParametroRealtimeDTO();
            dto.setNome(parametro.getNome());
            dto.setTipologia(parametro.getTipologia());
            dto.setUnitaMisura(parametro.getUnitaMisura());
            dto.setValMin(min);
            dto.setValMax(max);
            dto.setMaxDelta(maxDelta);
            dto.setValore(valore);
            dto.setTimestamp(ts);
            parametriList.add(dto);
        }
        return parametriList;
    }

    /** Comportamento legacy: genera nuovi campioni casuali e li persiste (solo se {@code app.realtime.simulate-readings=true}). */
    private List<ParametroRealtimeDTO> buildSimulatedRealtime(Dispositivo dispositivo) {
        List<ParametroRealtimeDTO> parametriList = new ArrayList<>();
        Random random = new Random();
        if (dispositivo.getParametriDispositivo() == null) {
            return parametriList;
        }
        for (ParametroDispositivo parametro : dispositivo.getParametriDispositivo()) {
            SensorGaugeDefaults.Range def = SensorGaugeDefaults.infer(
                    parametro.getUnitaMisura(), parametro.getNome());
            double min = parametro.getValMin() != null ? parametro.getValMin() : def.min();
            double max = parametro.getValMax() != null ? parametro.getValMax() : def.max();
            double maxDelta = parametro.getMaxDelta() != null ? parametro.getMaxDelta() : def.maxDelta();
            if (maxDelta <= 0 && max > min) {
                maxDelta = (max - min) * 0.1;
            }

            Instant now = Instant.now();
            DatoSensore lastDato = datoSensoreRepository.findFirstByParametroOrderByTimestampDesc(parametro);
            boolean generateNewData = true;
            double baseValue;

            if (lastDato != null) {
                Duration diff = Duration.between(lastDato.getTimestamp(), now);
                if (diff.toMinutes() < 10) {
                    generateNewData = false;
                    try {
                        baseValue = Double.parseDouble(lastDato.getValore().replace(",", ".").trim());
                    } catch (NumberFormatException e) {
                        baseValue = min + (max - min) * random.nextDouble();
                    }
                } else {
                    try {
                        baseValue = Double.parseDouble(lastDato.getValore().replace(",", ".").trim());
                    } catch (NumberFormatException e) {
                        baseValue = min + (max - min) * random.nextDouble();
                    }
                }
            } else {
                baseValue = min + (max - min) * random.nextDouble();
            }

            DatoSensore datoToUse;
            if (generateNewData) {
                double delta = -maxDelta + 2 * maxDelta * random.nextDouble();
                double newValue = baseValue + delta;
                newValue = Math.max(min, Math.min(newValue, max));

                DatoSensore newDato = new DatoSensore();
                newDato.setValore(String.valueOf(newValue));
                newDato.setTimestamp(now);
                newDato.setParametro(parametro);
                datoToUse = datoSensoreRepository.save(newDato);
            } else {
                datoToUse = lastDato;
            }

            ParametroRealtimeDTO dto = new ParametroRealtimeDTO();
            dto.setNome(parametro.getNome());
            dto.setTipologia(parametro.getTipologia());
            dto.setUnitaMisura(parametro.getUnitaMisura());
            dto.setValMin(min);
            dto.setValMax(max);
            dto.setMaxDelta(maxDelta);
            dto.setValore(datoToUse.getValore());
            dto.setTimestamp(datoToUse.getTimestamp());
            parametriList.add(dto);
        }
        return parametriList;
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

}
