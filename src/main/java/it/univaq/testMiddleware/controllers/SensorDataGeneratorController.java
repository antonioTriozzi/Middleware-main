package it.univaq.testMiddleware.controllers;

import it.univaq.testMiddleware.models.DatoSensore;
import it.univaq.testMiddleware.models.ParametroDispositivo;
import it.univaq.testMiddleware.models.ParametroDispositivo;
import it.univaq.testMiddleware.repositories.DatoSensoreRepository;
import it.univaq.testMiddleware.repositories.ParametroDispositivoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@RestController
@RequestMapping("/api/sensor-data")
public class SensorDataGeneratorController {

    @Autowired
    private DatoSensoreRepository DatoSensoreRepository;

    @Autowired
    private ParametroDispositivoRepository parametriRepository;

    private final Random random = new Random();

    /**
     * Genera un dato sensore per ogni parametro.
     */
    @PostMapping("/generate")
    public ResponseEntity<String> generateSensorData() {
        List<ParametroDispositivo> parameters = parametriRepository.findAll();
        int count = 0;
        for (ParametroDispositivo param : parameters) {
            String generatedValue = generateValueForParameter(param);
            DatoSensore data = new DatoSensore();
            data.setParametro(param);
            data.setValore(generatedValue);
            data.setTimestamp(Instant.now());
            DatoSensoreRepository.save(data);
            count++;
        }
        return ResponseEntity.ok("Generated sensor data for " + count + " parameters.");
    }

    /**
     * Genera un valore casuale per un parametro, basandosi sul range [valMin, valMax] se definito.
     * Se il range non è definito, utilizza un valore di fallback.
     */
    private String generateValueForParameter(ParametroDispositivo param) {
        if (param.getValMin() != null && param.getValMax() != null) {
            double min = param.getValMin();
            double max = param.getValMax();
            double value = min + (max - min) * random.nextDouble();
            // Se il parametro richiede interi, potresti arrotondare.
            return String.format("%.2f", value);
        } else {
            // Fallback: genera un valore intero casuale tra 0 e 100
            return String.valueOf(random.nextInt(101));
        }
    }
}
