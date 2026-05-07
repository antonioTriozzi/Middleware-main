package it.univaq.testMiddleware;

import it.univaq.testMiddleware.models.DatoSensore;
import it.univaq.testMiddleware.models.StoricoDatoSensore;
import it.univaq.testMiddleware.repositories.DatoSensoreRepository;
import it.univaq.testMiddleware.repositories.StoricoDatoSensoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

@Component
public class DataArchivingScheduler {

    private static final Logger logger = LoggerFactory.getLogger(DataArchivingScheduler.class);

    @Autowired
    private DatoSensoreRepository datoSensoreRepository;

    @Autowired
    private StoricoDatoSensoreRepository storicoDatoSensoreRepository;

    // Esegue il processo di archiviazione ogni giorno alle 2:00 del mattino
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void archiveOldSensorData() {
        logger.info("Avvio del processo di archiviazione dei dati storici...");

        // Imposta il "taglio" a 24 ore fa
        Instant cutoffTime = Instant.now().minus(24, ChronoUnit.HOURS);

        // 1. Trova tutti i dati dei sensori più vecchi di 24 ore
        List<DatoSensore> oldData = datoSensoreRepository.findByTimestampBefore(cutoffTime);

        if (oldData.isEmpty()) {
            logger.info("Nessun dato da archiviare trovato.");
            return;
        }

        logger.info("Trovati {} record da archiviare.", oldData.size());

        // 2. Copia i dati in StoricoDatoSensore
        List<StoricoDatoSensore> archivedData = oldData.stream()
                .map(this::mapToStoricoDatoSensore)
                .toList();

        storicoDatoSensoreRepository.saveAll(Objects.requireNonNull(archivedData, "archivedData"));
        logger.info("Archiviati con successo {} record nella tabella storico_dati_sensori.", archivedData.size());

        // 3. Cancella i dati da DatoSensore
        datoSensoreRepository.deleteAll(Objects.requireNonNull(oldData, "oldData"));
        logger.info("Cancellati con successo {} record dalla tabella dati_sensori.", oldData.size());
    }

    private StoricoDatoSensore mapToStoricoDatoSensore(DatoSensore datoSensore) {
        StoricoDatoSensore storicoDato = new StoricoDatoSensore();
        storicoDato.setTimestamp(datoSensore.getTimestamp());
        storicoDato.setValore(datoSensore.getValore());
        storicoDato.setParametro(datoSensore.getParametro());
        return storicoDato;
    }
}