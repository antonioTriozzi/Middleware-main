package it.univaq.testMiddleware.controllers;

import it.univaq.testMiddleware.DTO.consumi.ConsumoIngestItem;
import it.univaq.testMiddleware.DTO.consumi.ConsumoOutItem;
import it.univaq.testMiddleware.models.DatoSensore;
import it.univaq.testMiddleware.models.Dispositivo;
import it.univaq.testMiddleware.models.ParametroDispositivo;
import it.univaq.testMiddleware.models.User;
import it.univaq.testMiddleware.repositories.DatoSensoreRepository;
import it.univaq.testMiddleware.repositories.DispositivoRepository;
import it.univaq.testMiddleware.services.ConsumiIngestService;
import it.univaq.testMiddleware.services.UserService;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ConsumiController {

    private final ConsumiIngestService consumiIngestService;
    private final UserService userService;
    private final DispositivoRepository dispositivoRepository;
    private final DatoSensoreRepository datoSensoreRepository;

    public ConsumiController(ConsumiIngestService consumiIngestService,
                             UserService userService,
                             DispositivoRepository dispositivoRepository,
                             DatoSensoreRepository datoSensoreRepository) {
        this.consumiIngestService = consumiIngestService;
        this.userService = userService;
        this.dispositivoRepository = dispositivoRepository;
        this.datoSensoreRepository = datoSensoreRepository;
    }

    /**
     * Endpoint "nuovo formato consumi": accetta un array di misure (Modbus/M-Bus/KNX),
     * e salva su DB decomponendo asset/owner/parametri.
     *
     * Il gateway fa PUSH (POST) senza scaricare nulla.
     */
    @PostMapping("/consumi")
    public ResponseEntity<?> ingest(@RequestBody List<ConsumoIngestItem> body) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(consumiIngestService.ingest(body));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Endpoint per APP MOBILE: restituisce l'ultimo valore di ogni parametro
     * per tutti i dispositivi di cui l'utente autenticato è owner, nel formato JSON definitivo.
     *
     * GET /api/consumi/condomini/{idCondominio}/mine/latest
     */
    @GetMapping("/consumi/condomini/{idCondominio}/mine/latest")
    @Transactional
    public ResponseEntity<?> getMyLatestConsumi(@PathVariable Long idCondominio) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userService.findByUsername(username);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Utente non trovato");
        }

        List<Dispositivo> devices = dispositivoRepository.findByCondominio_IdCondominioAndOwner_Id(idCondominio, user.getId());
        List<ConsumoOutItem> out = new ArrayList<>();

        for (Dispositivo d : devices) {
            if (d.getParametriDispositivo() == null) continue;
            for (ParametroDispositivo p : d.getParametriDispositivo()) {
                DatoSensore last = datoSensoreRepository.findFirstByParametroOrderByTimestampDesc(p);
                if (last == null) continue;

                Object value = parseValue(last.getValore());
                out.add(new ConsumoOutItem(
                        p.getNome(),
                        value,
                        p.getUnitaMisura() != null ? p.getUnitaMisura() : "",
                        d.getTipo() != null ? d.getTipo() : "",
                        inferProtocol(d, p),
                        (d.getExternalDeviceId() != null && !d.getExternalDeviceId().isBlank()) ? d.getExternalDeviceId() : String.valueOf(d.getIdDispositivo()),
                        idCondominio,
                        d.getAssetId(),
                        d.getAssetName() != null ? d.getAssetName() : d.getNome(),
                        user.getId(),
                        user.getEmail()
                ));
            }
        }

        return ResponseEntity.ok(out);
    }

    private static String inferProtocol(Dispositivo d, ParametroDispositivo p) {
        if (p != null && ((p.getGroupAddress() != null && !p.getGroupAddress().isBlank()) || (p.getDpt() != null && !p.getDpt().isBlank()))) {
            return "KNX";
        }
        String ext = d != null && d.getExternalDeviceId() != null ? d.getExternalDeviceId().trim().toUpperCase() : "";
        if (ext.startsWith("MBUS")) return "M-Bus";
        if (ext.startsWith("MODBUS")) return "Modbus";
        if (ext.startsWith("SN-") || ext.startsWith("SN_")) return "Telemetry";
        return ext.isBlank() ? "Telemetry" : "Telemetry";
    }

    private static Object parseValue(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return "";
        if ("true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s)) {
            return Boolean.parseBoolean(s);
        }
        try {
            if (s.contains(".") || s.contains(",")) {
                String norm = s.replace(",", ".");
                return Double.parseDouble(norm);
            }
            return Long.parseLong(s);
        } catch (NumberFormatException ignored) {
        }
        return s;
    }
}

