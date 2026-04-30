package it.univaq.testMiddleware.adapter;

import it.univaq.testMiddleware.DTO.*;
import it.univaq.testMiddleware.models.*;
import it.univaq.testMiddleware.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class InverterAdapter implements ExternalDataAdapter {

    // 1. MAPPING STATICO: Include Tipologia, Unità e VALORI DI SOGLIA
    
    private static final Map<String, Map<String, Object>> PARAMETER_MAPPING = Map.of(
        // Chiave API -> Mappa dei valori standard
        "energyGeneratingTotal", Map.of(
            "tipologia", "Energia Generata Totale", 
            "unitaMisura", "kWh",
            "valMin", 0.0, 
            "valMax", 8000.0, 
            "maxDelta", 50.0  
        ),
        "powerGenerating", Map.of(
            "tipologia", "Potenza Generata", 
            "unitaMisura", "W",
            "valMin", 0.0, 
            "valMax", 1000.0, 
            "maxDelta", 100.0 
        ),          
        "energyGenerating", Map.of(
            "tipologia", "Energia Corrente", 
            "unitaMisura", "kWh",
            "valMin", 0.0, 
            "valMax", 10.0,   
            "maxDelta", 1.0   
        )
    );
    
    // Valori di default per parametri non mappati o campi mancanti
    private static final String DEFAULT_UNIT = "-";
    private static final String DEFAULT_TIPOLOGIA_PREFIX = "Parametro Sconosciuto: ";
    private static final double DEFAULT_VAL_MIN = 0.0;
    private static final double DEFAULT_VAL_MAX = 99999.0;
    private static final double DEFAULT_MAX_DELTA = 50.0;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private DispositivoRepository dispositivoRepository;

    @Autowired
    private ParametroDispositivoRepository parametroDispositivoRepository;

    @Autowired
    private DatoSensoreRepository datoSensoreRepository;

    @Autowired
    private StoricoEventoRepository storicoEventoRepository;

    @Transactional
    @Override
    @SuppressWarnings("unchecked")
    public ExternalDataResponse fetchAndMapData(Condominio condominio, Dispositivo dispositivo, User user) {

        Long idDispositivo = dispositivo.getIdDispositivo();
        if (idDispositivo == null) {
            return null;
        }
        String url = "http://localhost:8081/api/inverter/" + idDispositivo;
        Map<String, Object> datiApi = restTemplate.getForObject(url, Map.class);
        if (datiApi == null) return null;

        Dispositivo existingDispositivo = dispositivoRepository.findById(idDispositivo)
                .orElse(dispositivoRepository.save(dispositivo));

        List<Map<String, Object>> paramList = new ArrayList<>();

        for (Map.Entry<String, Object> entry : datiApi.entrySet()) {
            Map<String, Object> record = (Map<String, Object>) entry.getValue();

            String paramName = (String) record.get("nome");
            String unitaMisuraFromApi = (String) record.get("unitaMisura"); // Unità di misura grezza (Source Unit)

            // Recupera tutti i dati standardizzati dalla mappa
            Map<String, Object> mappingData = PARAMETER_MAPPING.getOrDefault(paramName, Collections.emptyMap());
            
            String standardTipologia = (String) mappingData.getOrDefault("tipologia", DEFAULT_TIPOLOGIA_PREFIX + paramName);
            String standardUnitaMisura = (String) mappingData.getOrDefault("unitaMisura", DEFAULT_UNIT);
            Double standardValMin = (Double) mappingData.getOrDefault("valMin", DEFAULT_VAL_MIN);
            Double standardValMax = (Double) mappingData.getOrDefault("valMax", DEFAULT_VAL_MAX);
            Double standardMaxDelta = (Double) mappingData.getOrDefault("maxDelta", DEFAULT_MAX_DELTA);

            // Cerca il parametro nel DB del middleware
            ParametroDispositivo parametro = parametroDispositivoRepository.findByNomeAndDispositivo(paramName, existingDispositivo)
                    .orElse(null);

            boolean needsUpdate = false;
            
            // Logica di creazione e aggiornamento (Correzione retroattiva)
            if (parametro == null) {
                parametro = new ParametroDispositivo();
                parametro.setNome(paramName);
                parametro.setDispositivo(existingDispositivo);
                needsUpdate = true;
            }

            // Forziamo tutti i valori standard (Tipologia, Unità, Limiti)
            if (!standardTipologia.equals(parametro.getTipologia())) {
                parametro.setTipologia(standardTipologia);
                needsUpdate = true;
            }
            if (!standardUnitaMisura.equals(parametro.getUnitaMisura())) {
                parametro.setUnitaMisura(standardUnitaMisura);
                needsUpdate = true;
            }
            if (!standardValMin.equals(parametro.getValMin())) {
                parametro.setValMin(standardValMin);
                needsUpdate = true;
            }
            if (!standardValMax.equals(parametro.getValMax())) {
                parametro.setValMax(standardValMax);
                needsUpdate = true;
            }
            if (!standardMaxDelta.equals(parametro.getMaxDelta())) {
                parametro.setMaxDelta(standardMaxDelta);
                needsUpdate = true;
            }
            
            if (needsUpdate) {
                parametro = parametroDispositivoRepository.save(parametro);
            }

            // Popola i dati da restituire
            Map<String, Object> paramData = new HashMap<>();
            paramData.put("nome", parametro.getNome());
            paramData.put("tipologia", parametro.getTipologia());
            paramData.put("unitaMisura", parametro.getUnitaMisura());
            paramData.put("valMin", parametro.getValMin());
            paramData.put("valMax", parametro.getValMax());
            paramData.put("maxDelta", parametro.getMaxDelta());
            
            List<Map<String, Object>> valoriSensore = (List<Map<String, Object>>) record.get("valori");

            if (valoriSensore != null && !valoriSensore.isEmpty()) {
                Map<String, Object> valoreRecord = valoriSensore.get(0);
                
                Object valoreObject = valoreRecord.get("valore");
                Instant timestamp = LocalDateTime.parse(
                    valoreRecord.get("timestamp").toString(),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
                    .atZone(ZoneId.systemDefault()).toInstant();

                String rawValore = String.valueOf(valoreObject);
                double valoreDouble = 0.0;
                try {
                    valoreDouble = Double.parseDouble(rawValore);
                } catch (NumberFormatException e) {
                    // Ignora
                }

                // Esegui la conversione del valore numerico all'unità standard
                double convertedValoreDouble = convertValueToStandardUnit(
                    valoreDouble, 
                    unitaMisuraFromApi, 
                    parametro.getUnitaMisura()
                );
                String finalValoreToSave = String.valueOf(convertedValoreDouble);
                
                // Logica di prevenzione duplicati e salvataggio (QUI LA CORREZIONE)
                // Usiamo parametro, timestamp e finalValoreToSave per la verifica
                if (datoSensoreRepository.findByParametroAndTimestampAndValore(parametro, timestamp, finalValoreToSave).isEmpty()) {
                    DatoSensore nuovoDato = new DatoSensore();
                    nuovoDato.setParametro(parametro);
                    nuovoDato.setValore(finalValoreToSave); 
                    nuovoDato.setTimestamp(timestamp);
                    datoSensoreRepository.save(nuovoDato);
                }
                
// ... [codice successivo]

                
                paramData.put("valore", finalValoreToSave); 
                paramData.put("timestamp", timestamp);

                // Check Anomalia
                if ((parametro.getValMin() != null && convertedValoreDouble < parametro.getValMin()) ||
                    (parametro.getValMax() != null && convertedValoreDouble > parametro.getValMax())) {

                    StoricoEvento eventoAnomalia = new StoricoEvento();
                    eventoAnomalia.setTimestamp(Instant.now());
                    eventoAnomalia.setDispositivo(existingDispositivo);
                    eventoAnomalia.setConfermaLettura(Boolean.FALSE);
                    eventoAnomalia.setValore(finalValoreToSave);
                    
                    String descrizione = String.format("Anomalia rilevata: il parametro '%s' ha un valore di %.2f %s, fuori dall'intervallo operativo [ %.2f, %.2f ].",
                            parametro.getTipologia(), convertedValoreDouble, parametro.getUnitaMisura(), parametro.getValMin(), parametro.getValMax());
                    eventoAnomalia.setDescrizione(descrizione);
                    
                    storicoEventoRepository.save(eventoAnomalia);
                }

            } else {
                paramData.put("valore", null);
                paramData.put("timestamp", null);
            }

            paramList.add(paramData);
        }
        
        ExternalDataResponse response = new ExternalDataResponse();
        response.setCondominioDTO(mapCondominio(condominio));
        response.setDispositivoDTO(mapDispositivo(existingDispositivo));
        response.setParametri(paramList);
        response.setUserDTO(mapUser(user));

        return response;
    }

    // 2. METODO: Conversione del Valore Numerico

    private double convertValueToStandardUnit(double sourceValue, String sourceUnit, String targetUnit) {
        if (sourceUnit == null || sourceUnit.equalsIgnoreCase(targetUnit)) {
            return sourceValue; 
        }

        if (targetUnit.equalsIgnoreCase("W") && sourceUnit.equalsIgnoreCase("kW")) {
            return sourceValue * 1000.0; // da kW a W
        } 
        
        if (targetUnit.equalsIgnoreCase("kWh")) {
             // Se la fonte invia Wattora
             if (sourceUnit.equalsIgnoreCase("Wh")) {
                return sourceValue / 1000.0; // da Wh a kWh
            }
        }
        
        // Se non viene trovata una regola specifica, restituisce il valore originale
        return sourceValue;
    }

    // 3. METODI DI MAPPING 

    private CondominioDTO mapCondominio(Condominio condominio) {
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
        dto.setLatitudine(condominio.getLatitudine());
        dto.setLongitudine(condominio.getLongitudine());
        if (condominio.getAmministratore() != null) {
            dto.setAmministratore(mapUser(condominio.getAmministratore()));
        }
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
        if (user == null) {
            return null;
        }
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setNome(user.getNome());
        dto.setCognome(user.getCognome());
        dto.setEmail(user.getEmail());
        dto.setRuolo(user.getRuolo());
        return dto;
    }
}
