package it.univaq.testMiddleware.services;

import it.univaq.testMiddleware.DTO.telemetry.GatewayTelemetryPayload;
import it.univaq.testMiddleware.DTO.telemetry.TelemetryDevicePayload;
import it.univaq.testMiddleware.DTO.telemetry.TelemetryIngestResult;
import it.univaq.testMiddleware.DTO.telemetry.TelemetryMeasurementItem;
import it.univaq.testMiddleware.models.Condominio;
import it.univaq.testMiddleware.models.DatoSensore;
import it.univaq.testMiddleware.models.Dispositivo;
import it.univaq.testMiddleware.models.ParametroDispositivo;
import it.univaq.testMiddleware.repositories.CondominioRepository;
import it.univaq.testMiddleware.repositories.DatoSensoreRepository;
import it.univaq.testMiddleware.repositories.DispositivoRepository;
import it.univaq.testMiddleware.repositories.ParametroDispositivoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
public class TelemetryIngestService {

    private static final DateTimeFormatter LOCAL_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CondominioRepository condominioRepository;
    private final DispositivoRepository dispositivoRepository;
    private final ParametroDispositivoRepository parametroDispositivoRepository;
    private final DatoSensoreRepository datoSensoreRepository;

    public TelemetryIngestService(CondominioRepository condominioRepository,
                                  DispositivoRepository dispositivoRepository,
                                  ParametroDispositivoRepository parametroDispositivoRepository,
                                  DatoSensoreRepository datoSensoreRepository) {
        this.condominioRepository = condominioRepository;
        this.dispositivoRepository = dispositivoRepository;
        this.parametroDispositivoRepository = parametroDispositivoRepository;
        this.datoSensoreRepository = datoSensoreRepository;
    }

    @Transactional
    public TelemetryIngestResult ingest(GatewayTelemetryPayload request) {
        if (request == null || request.getGatewayId() == null || request.getGatewayId().isBlank()) {
            throw new IllegalArgumentException("gateway_id mancante.");
        }
        if (request.getData() == null || request.getData().isEmpty()) {
            throw new IllegalArgumentException("data vuoto.");
        }

        long condominioId = Long.parseLong(request.getGatewayId().trim());
        Condominio condominio = condominioRepository.findById(condominioId)
                .orElseThrow(() -> new IllegalArgumentException("Condominio non trovato: " + condominioId));

        int saved = 0;
        List<String> warnings = new ArrayList<>();

        for (TelemetryDevicePayload row : request.getData()) {
            if (row.getDeviceId() == null || row.getDeviceId().isBlank()) {
                warnings.add("Riga senza device_id ignorata.");
                continue;
            }
            long deviceId = Long.parseLong(row.getDeviceId().trim());
            Dispositivo dispositivo = dispositivoRepository
                    .findByIdDispositivoAndCondominio_IdCondominio(deviceId, condominio.getIdCondominio())
                    .orElse(null);
            if (dispositivo == null) {
                warnings.add("Dispositivo " + deviceId + " non trovato per condominio " + condominioId + ".");
                continue;
            }

            Instant ts = parseTimestamp(row.getTimestamp());
            List<TelemetryMeasurementItem> readings = row.getReadings() != null ? row.getReadings() : List.of();
            for (TelemetryMeasurementItem m : readings) {
                if (m == null || m.getName() == null || m.getName().isBlank()) {
                    continue;
                }
                ParametroDispositivo param = parametroDispositivoRepository
                        .findByNomeAndDispositivo(m.getName().trim(), dispositivo)
                        .orElseGet(() -> createParametro(dispositivo, m));

                DatoSensore dato = new DatoSensore();
                dato.setParametro(param);
                dato.setTimestamp(ts);
                dato.setValore(formatValore(m));
                datoSensoreRepository.save(dato);
                saved++;
            }
        }

        return new TelemetryIngestResult(saved, warnings);
    }

    private ParametroDispositivo createParametro(Dispositivo dispositivo, TelemetryMeasurementItem m) {
        ParametroDispositivo p = new ParametroDispositivo();
        p.setNome(m.getName().trim());
        p.setTipologia("Telemetria gateway");
        p.setUnitaMisura(m.getUnit() != null ? m.getUnit() : "");
        p.setDispositivo(dispositivo);
        return parametroDispositivoRepository.save(p);
    }

    private static String formatValore(TelemetryMeasurementItem m) {
        if (m.getValue() == null) {
            return "";
        }
        return String.valueOf(m.getValue());
    }

    private static Instant parseTimestamp(String raw) {
        if (raw == null || raw.isBlank()) {
            return Instant.now();
        }
        String s = raw.trim().replace('=', '-');
        try {
            LocalDateTime ldt = LocalDateTime.parse(s, LOCAL_TS);
            return ldt.atZone(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return Instant.parse(s);
        } catch (DateTimeParseException ignored) {
        }
        throw new IllegalArgumentException("Timestamp non valido: " + raw);
    }
}
