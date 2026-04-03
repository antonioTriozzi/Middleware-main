package it.univaq.testMiddleware.controllers;

import it.univaq.testMiddleware.DTO.telemetry.GatewayTelemetryPayload;
import it.univaq.testMiddleware.DTO.telemetry.TelemetryIngestResult;
import it.univaq.testMiddleware.services.TelemetryIngestService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TelemetryController {

    private final TelemetryIngestService telemetryIngestService;

    public TelemetryController(TelemetryIngestService telemetryIngestService) {
        this.telemetryIngestService = telemetryIngestService;
    }

    /**
     * Accetta il JSON in <strong>uscita dal gateway</strong> (northbound), come nel fake di test.
     * Richiede JWT Bearer valido (come gli altri endpoint protetti).
     */
    @PostMapping("/telemetry")
    public ResponseEntity<?> ingest(@RequestBody GatewayTelemetryPayload body) {
        try {
            TelemetryIngestResult result = telemetryIngestService.ingest(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body("gateway_id o device_id non numerico.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
