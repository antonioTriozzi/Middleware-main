package it.univaq.testMiddleware.adapter;

import it.univaq.testMiddleware.DTO.*;
import it.univaq.testMiddleware.models.Condominio;
import it.univaq.testMiddleware.models.DatoSensore;
import it.univaq.testMiddleware.models.Dispositivo;
import it.univaq.testMiddleware.models.ParametroDispositivo;
import it.univaq.testMiddleware.models.User;
import it.univaq.testMiddleware.repositories.DatoSensoreRepository;
import it.univaq.testMiddleware.repositories.DispositivoRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Component
public class RandomDataAdapter implements ExternalDataAdapter {

    @Autowired
    private DatoSensoreRepository datoSensoreRepository;

    @Autowired
    private DispositivoRepository dispositivoRepository;

    @Override
    @Transactional
    public ExternalDataResponse fetchAndMapData(Condominio condominio, Dispositivo dispositivo, User user) {
        // Per ogni parametro del dispositivo, genera (o riutilizza) un dato sensore casuale
        List<Map<String, Object>> parametriList = new ArrayList<>();
        Random random = new Random();

        if (dispositivo.getParametriDispositivo() != null) {
            for (ParametroDispositivo parametro : dispositivo.getParametriDispositivo()) {
                Map<String, Object> paramData = new HashMap<>();
                ParametroDTO parametroDTO = mapParametro(parametro);

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
                    newDato.setValore(String.valueOf((int) newValue)); // conversione a intero; modificare se necessario
                    newDato.setTimestamp(now);
                    newDato.setParametro(parametro);
                    datoToUse = datoSensoreRepository.save(newDato);
                } else {
                    datoToUse = lastDato;
                }

                if (datoToUse == null) {
                    continue;
                }

                // Costruisci il DTO del dato sensore
                SensorValueDTO sensorValueDTO = new SensorValueDTO();
                sensorValueDTO.setValore(datoToUse.getValore());
                sensorValueDTO.setTimestamp(datoToUse.getTimestamp());

                paramData.put("parametro", parametroDTO);
                paramData.put("valoriSensore", Collections.singletonList(sensorValueDTO));
                parametriList.add(paramData);
            }
        }

        // Forza il flush delle modifiche nel database
        datoSensoreRepository.flush();
        dispositivoRepository.flush();

        // Costruisci il DTO di risposta finale
        ExternalDataResponse responseDto = new ExternalDataResponse();
        responseDto.setCondominioDTO(mapCondominio(condominio));
        responseDto.setDispositivoDTO(mapDispositivo(dispositivo));
        responseDto.setUserDTO(mapUser(user));
        responseDto.setParametri(parametriList);
        return responseDto;
    }

    // Metodi di mapping per costruire i DTO

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
