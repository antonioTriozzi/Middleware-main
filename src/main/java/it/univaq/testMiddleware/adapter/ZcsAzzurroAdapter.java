package it.univaq.testMiddleware.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.univaq.testMiddleware.DTO.*;
import it.univaq.testMiddleware.models.Condominio;
import it.univaq.testMiddleware.models.DatoSensore;
import it.univaq.testMiddleware.models.Dispositivo;
import it.univaq.testMiddleware.models.ParametroDispositivo;
import it.univaq.testMiddleware.models.User;
import it.univaq.testMiddleware.repositories.DatoSensoreRepository;
import it.univaq.testMiddleware.repositories.DispositivoRepository;
import it.univaq.testMiddleware.repositories.ParametroDispositivoRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ZcsAzzurroAdapter implements ExternalDataAdapter {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private DatoSensoreRepository datoSensoreRepository;

    @Autowired
    private ParametroDispositivoRepository parametroDispositivoRepository;

    @Autowired
    private DispositivoRepository dispositivoRepository;

    @Override
    @Transactional
    public ExternalDataResponse fetchAndMapData(Condominio condominio, Dispositivo dispositivo, User user) {
        // Costruisci la richiesta all’API esterna
        String externalApiUrl = "https://third.zcsazzurroportal.com:19003/";
        Map<String, Object> externalRequest = new HashMap<>();
        Map<String, Object> realtimeData = new HashMap<>();
        realtimeData.put("command", "realtimeData");
        Map<String, Object> params = new HashMap<>();
        params.put("client", "TUO_CLIENT_QUI");
        params.put("thingKey", "TUA_CHIAVE_QUI");
        params.put("requiredValues", "*");
        params.put("start", "2025-01-15T00:00:00.000Z");
        params.put("end", "2025-01-15T23:59:59.059Z");
        realtimeData.put("params", params);
        externalRequest.put("realtimeData", realtimeData);

        // Configura gli header richiesti
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "TUO_TOKEN_QUI");
        headers.set("Client", "NOME_CLIENT_QUI");
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(externalRequest, headers);

        ResponseEntity<String> externalResponse = restTemplate.postForEntity(externalApiUrl, requestEntity, String.class);
        if (!externalResponse.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Errore nella chiamata all’API esterna");
        }

        String responseBody = externalResponse.getBody();
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> responseMap;
        try {
            responseMap = mapper.readValue(responseBody, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Errore nel parsing della risposta esterna", e);
        }

        Map<String, Object> realtimeDataResp = (Map<String, Object>) responseMap.get("realtimeData");
        if (realtimeDataResp == null || !(Boolean) realtimeDataResp.get("success")) {
            throw new RuntimeException("API esterna ha restituito un errore");
        }
        Map<String, Object> paramsResp = (Map<String, Object>) realtimeDataResp.get("params");
        List<Object> valuesList = (List<Object>) paramsResp.get("value");

        // Estrai il dato per "ZA1ES111H1C029" (la chiave fissa della risposta)
        Map<String, Object> sensorData = null;
        if (valuesList != null && !valuesList.isEmpty()) {
            Object first = valuesList.get(0);
            if (first instanceof Map) {
                Map<String, Object> mapObj = (Map<String, Object>) first;
                sensorData = (Map<String, Object>) mapObj.get("ZA1ES111H1C029");
            }
        }
        if (sensorData == null) {
            throw new RuntimeException("Nessun dato sensore ricevuto dalla risposta");
        }

        // Qui, rigo per rigo, crei i parametri attesi.
        // Per ogni campo definito nella risposta, se non esiste il parametro nel dispositivo,
        // viene creato e viene salvato il relativo dato sensore usando il valore e il timestamp ("lastUpdate").
        processSensorField("energyGeneratingTotal", "energia generata", "kw", 0.0, 8000.0, sensorData, dispositivo);
        processSensorField("powerGenerating", "potenza generata", "W", 0.0, 1000.0, sensorData, dispositivo);
        processSensorField("energyGenerating", "energia corrente", "kw", 0.0, 10.0, sensorData, dispositivo);

        // Se necessario, puoi aggiungere altri processi per altri campi come "thingFind" (se intendi salvarlo come parametro)

        dispositivoRepository.save(dispositivo);

        // Costruisci il DTO di risposta
        ExternalDataResponse responseDto = new ExternalDataResponse();
        responseDto.setCondominioDTO(mapCondominio(condominio));
        responseDto.setDispositivoDTO(mapDispositivo(dispositivo));
        responseDto.setUserDTO(mapUser(user));

        // Prepara la lista dei parametri con l'ultimo dato sensore per ciascuno
        List<Map<String, Object>> paramList = new ArrayList<>();
        if (dispositivo.getParametriDispositivo() != null) {
            for (ParametroDispositivo parametro : dispositivo.getParametriDispositivo()) {
                Map<String, Object> paramData = new HashMap<>();
                ParametroDTO parametroDTO = mapParametro(parametro);
                DatoSensore lastDato = datoSensoreRepository.findFirstByParametroOrderByTimestampDesc(parametro);
                SensorValueDTO sensorValueDTO = new SensorValueDTO();
                if (lastDato != null) {
                    sensorValueDTO.setValore(lastDato.getValore());
                    sensorValueDTO.setTimestamp(lastDato.getTimestamp());
                }
                paramData.put("parametro", parametroDTO);
                paramData.put("valoriSensore", Collections.singletonList(sensorValueDTO));
                paramList.add(paramData);
            }
        }
        responseDto.setParametri(paramList);
        return responseDto;
    }

    /**
     * Processa un campo specifico della risposta esterna.
     * Se il parametro non esiste nel dispositivo, viene creato; in ogni caso, viene salvato un nuovo dato sensore
     * con il valore del campo e il timestamp preso da "lastUpdate".
     */
    private void processSensorField(String fieldName, String tipologia, String unitaMisura, Double valMin, Double valMax,
                                    Map<String, Object> sensorData, Dispositivo dispositivo) {
        Object fieldValueObj = sensorData.get(fieldName);
        if (fieldValueObj == null) return; // Salta se il campo non esiste
        String fieldValue = fieldValueObj.toString();

        // Ottieni il timestamp dal campo "lastUpdate" della risposta esterna
        Object lastUpdateObj = sensorData.get("lastUpdate");
        Instant externalTimestamp;
        try {
            externalTimestamp = lastUpdateObj != null ? Instant.parse(lastUpdateObj.toString()) : Instant.now();
        } catch (Exception e) {
            externalTimestamp = Instant.now();
        }

        // Cerca se il parametro esiste già nel dispositivo
        ParametroDispositivo parametro = null;
        if (dispositivo.getParametriDispositivo() != null) {
            parametro = dispositivo.getParametriDispositivo().stream()
                    .filter(p -> fieldName.equals(p.getNome()))
                    .findFirst().orElse(null);
        }
        if (parametro == null) {
            // Crea il nuovo parametro se non esiste
            parametro = new ParametroDispositivo();
            parametro.setNome(fieldName);
            parametro.setTipologia(tipologia);
            parametro.setUnitaMisura(unitaMisura);
            parametro.setValMin(valMin);
            parametro.setValMax(valMax);
            parametro.setMaxDelta(100.0); // valore di default, modificabile se necessario
            parametro.setDispositivo(dispositivo);
            parametro = parametroDispositivoRepository.save(parametro);
            if (dispositivo.getParametriDispositivo() == null) {
                dispositivo.setParametriDispositivo(new ArrayList<>());
            }
            dispositivo.getParametriDispositivo().add(parametro);
        }

        // Recupera l'ultimo dato sensore per questo parametro
        DatoSensore lastDato = datoSensoreRepository.findFirstByParametroOrderByTimestampDesc(parametro);
        // Se esiste già un dato e il suo timestamp è uguale o successivo a quello dell'API, non creiamo un nuovo record.
        if (lastDato != null && !lastDato.getTimestamp().isBefore(externalTimestamp)) {
            // Non fare nulla: usiamo il record già presente
            return;
        }

        // Altrimenti, crea e salva il nuovo dato sensore associato a questo parametro
        DatoSensore nuovoDato = new DatoSensore();
        nuovoDato.setValore(fieldValue);
        nuovoDato.setTimestamp(externalTimestamp);
        nuovoDato.setParametro(parametro);
        datoSensoreRepository.save(nuovoDato);
    }


    // Metodi di mapping per costruire i DTO di risposta

    private CondominioDTO mapCondominio(Condominio condominio) {
        CondominioDTO dto = new CondominioDTO();
        dto.setIdCondominio(condominio.getIdCondominio());
        dto.setNome(condominio.getNome());
        dto.setIndirizzo(condominio.getIndirizzo());
        return dto;
    }

    private DispositivoDTO mapDispositivo(Dispositivo dispositivo) {
        DispositivoDTO dto = new DispositivoDTO();
        dto.setIdDispositivo(dispositivo.getIdDispositivo());
        dto.setNome(dispositivo.getNome());
        dto.setMarca(dispositivo.getMarca());
        dto.setModello(dispositivo.getModello());
        dto.setTipo(dispositivo.getTipo());
        dto.setStato(dispositivo.getStato());
        return dto;
    }

    private UserDTO mapUser(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getEmail());
        return dto;
    }

    private ParametroDTO mapParametro(ParametroDispositivo parametro) {
        ParametroDTO dto = new ParametroDTO();
        dto.setNome(parametro.getNome());
        dto.setTipologia(parametro.getTipologia());
        dto.setUnitaMisura(parametro.getUnitaMisura());
        return dto;
    }
}
