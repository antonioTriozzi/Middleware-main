package it.univaq.testMiddleware.controllers;

import it.univaq.testMiddleware.DTO.*;
import it.univaq.testMiddleware.models.Condominio;
import it.univaq.testMiddleware.models.DatoSensore;
import it.univaq.testMiddleware.models.Dispositivo;
import it.univaq.testMiddleware.models.User;
import it.univaq.testMiddleware.repositories.DatoSensoreRepository;
import it.univaq.testMiddleware.repositories.DispositivoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/sensor-data")
public class SensorDataQueryController {

    @Autowired
    private DatoSensoreRepository datoSensoreRepository;

    @Autowired
    private DispositivoRepository dispositivoRepository;

    /**
     * Endpoint per ottenere tutti i dati sensori di un dispositivo per un dato giorno.
     *
     * Esempio di chiamata:
     * GET /api/sensor-data/device/5?date=2025-02-26
     */
    @GetMapping("/device/{deviceId}")
    public ResponseEntity<?> getSensorDataByDeviceAndDate(
            @PathVariable("deviceId") Long deviceId,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        Objects.requireNonNull(deviceId, "deviceId");
        // Verifica che il dispositivo esista
        Optional<Dispositivo> deviceOpt = dispositivoRepository.findById(deviceId);
        if (deviceOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Dispositivo non trovato");
        }

        Dispositivo dispositivo = deviceOpt.get();

        // Converti la data in intervallo di tempo
        ZoneId zone = ZoneId.systemDefault();
        Instant startOfDay = date.atStartOfDay(zone).toInstant();
        Instant endOfDay = date.plusDays(1).atStartOfDay(zone).toInstant();

        // Recupera i dati sensore per quel dispositivo in quell'intervallo
        List<DatoSensore> sensorData = datoSensoreRepository
                .findByParametro_Dispositivo_IdDispositivoAndTimestampBetween(deviceId, startOfDay, endOfDay);

        if (sensorData.isEmpty()) {
            return ResponseEntity.ok().body("Nessun dato trovato per il giorno richiesto");
        }

        // Raggruppa i dati per parametro
        Map<Long, List<DatoSensore>> datiPerParametro = sensorData.stream()
                .collect(Collectors.groupingBy(ds -> ds.getParametro().getIdParametro()));

        List<ParametroDTO> parametriDTO = new ArrayList<>();
        for (Map.Entry<Long, List<DatoSensore>> entry : datiPerParametro.entrySet()) {
            List<DatoSensore> datiParametro = entry.getValue();
            DatoSensore sample = datiParametro.get(0); // usiamo il primo per estrarre info sul parametro

            ParametroDTO parametroDTO = new ParametroDTO();
            parametroDTO.setNome(sample.getParametro().getNome());
            parametroDTO.setTipologia(sample.getParametro().getTipologia());
            parametroDTO.setUnitaMisura(sample.getParametro().getUnitaMisura());

            // Mappa ogni DatoSensore in un SensorValueDTO
            List<SensorValueDTO> valori = datiParametro.stream().map(ds -> {
                SensorValueDTO dto = new SensorValueDTO();
                dto.setValore(ds.getValore());
                dto.setTimestamp(ds.getTimestamp());
                return dto;
            }).collect(Collectors.toList());

            parametroDTO.setValori(valori);
            parametriDTO.add(parametroDTO);
        }

        // Mappa il dispositivo in DTO
        DispositivoDTO dispositivoDTO = new DispositivoDTO();
        dispositivoDTO.setIdDispositivo(dispositivo.getIdDispositivo());
        dispositivoDTO.setNome(dispositivo.getNome());
        dispositivoDTO.setMarca(dispositivo.getMarca());
        dispositivoDTO.setModello(dispositivo.getModello());
        dispositivoDTO.setTipo(dispositivo.getTipo());
        dispositivoDTO.setStato(dispositivo.getStato());
        dispositivoDTO.setParametri(parametriDTO);

        // Mappa il condominio
        Condominio condominio = dispositivo.getCondominio();
        CondominioDTO condominioDTO = new CondominioDTO();
        condominioDTO.setIdCondominio(condominio.getIdCondominio());
        condominioDTO.setNome(condominio.getNome());
        condominioDTO.setIndirizzo(condominio.getIndirizzo());

        // Mappa l'amministratore (User)
        User admin = condominio.getAmministratore();
        UserDTO adminDTO = new UserDTO();
        adminDTO.setId(admin.getId());
        adminDTO.setUsername(admin.getEmail());
        // Nota: potresti decidere di non includere la password nella risposta
        condominioDTO.setAmministratore(adminDTO);

        // Crea il DTO di risposta finale
        SensorDataHierarchicalResponse response = new SensorDataHierarchicalResponse();
        response.setCondominio(condominioDTO);
        response.setDispositivo(dispositivoDTO);

        return ResponseEntity.ok(response);
    }

}
