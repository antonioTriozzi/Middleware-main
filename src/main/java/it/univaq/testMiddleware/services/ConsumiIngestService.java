package it.univaq.testMiddleware.services;

import it.univaq.testMiddleware.DTO.consumi.ConsumoIngestItem;
import it.univaq.testMiddleware.models.Condominio;
import it.univaq.testMiddleware.models.DatoSensore;
import it.univaq.testMiddleware.models.Dispositivo;
import it.univaq.testMiddleware.models.ParametroDispositivo;
import it.univaq.testMiddleware.models.User;
import it.univaq.testMiddleware.repositories.CondominioRepository;
import it.univaq.testMiddleware.repositories.DatoSensoreRepository;
import it.univaq.testMiddleware.repositories.DispositivoRepository;
import it.univaq.testMiddleware.repositories.ParametroDispositivoRepository;
import it.univaq.testMiddleware.repositories.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ConsumiIngestService {

    private final CondominioRepository condominioRepository;
    private final DispositivoRepository dispositivoRepository;
    private final ParametroDispositivoRepository parametroDispositivoRepository;
    private final DatoSensoreRepository datoSensoreRepository;
    private final UserRepository userRepository;

    public ConsumiIngestService(CondominioRepository condominioRepository,
                                DispositivoRepository dispositivoRepository,
                                ParametroDispositivoRepository parametroDispositivoRepository,
                                DatoSensoreRepository datoSensoreRepository,
                                UserRepository userRepository) {
        this.condominioRepository = condominioRepository;
        this.dispositivoRepository = dispositivoRepository;
        this.parametroDispositivoRepository = parametroDispositivoRepository;
        this.datoSensoreRepository = datoSensoreRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public IngestSummary ingest(List<ConsumoIngestItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Body vuoto: atteso array di misure.");
        }

        int saved = 0;
        List<String> warnings = new ArrayList<>();

        for (ConsumoIngestItem it : items) {
            if (it == null) continue;
            if (it.getBuildingId() == null) {
                warnings.add("Riga senza building_id ignorata.");
                continue;
            }
            if (it.getAssetId() == null && (it.getDeviceId() == null || it.getDeviceId().isBlank())) {
                warnings.add("Riga senza asset_id e senza device_id ignorata.");
                continue;
            }
            if (it.getMeasure() == null || it.getMeasure().isBlank()) {
                warnings.add("Riga senza measure ignorata.");
                continue;
            }

            Condominio condominio = condominioRepository.findById(it.getBuildingId())
                    .orElseGet(() -> createCondominioFromJson(it, warnings));
            enrichCondominioFromJson(condominio, it);

            User owner = resolveOwner(it, warnings).orElse(null);

            Dispositivo dispositivo = resolveDevice(condominio, it, owner);

            ParametroDispositivo parametro = parametroDispositivoRepository
                    .findByNomeAndDispositivo(it.getMeasure().trim(), dispositivo)
                    .orElseGet(() -> createParametro(dispositivo, it));

            DatoSensore dato = new DatoSensore();
            dato.setParametro(parametro);
            dato.setTimestamp(Instant.now());
            dato.setValore(it.getValue() == null ? "" : String.valueOf(it.getValue()));
            dato.setRaw(it.getRaw());
            datoSensoreRepository.save(dato);
            saved++;
        }

        return new IngestSummary(saved, warnings);
    }

    private void enrichCondominioFromJson(Condominio c, ConsumoIngestItem it) {
        if (c == null || it == null) return;
        boolean touched = false;
        if (it.getBuildingName() != null && !it.getBuildingName().isBlank()) {
            c.setNome(it.getBuildingName().trim());
            touched = true;
        }
        if (it.getBuildingAddress() != null && !it.getBuildingAddress().isBlank()) {
            c.setIndirizzo(it.getBuildingAddress().trim());
            touched = true;
        }
        if (touched) {
            condominioRepository.save(c);
        }
    }

    private Condominio createCondominioFromJson(ConsumoIngestItem it, List<String> warnings) {
        // Obiettivo: rendere il flusso "self-contained": se arriva un building_id che non esiste,
        // creiamo un condominio minimo (nome/indirizzo default) usando quello stesso id.
        Condominio c = new Condominio();
        c.setIdCondominio(it.getBuildingId());
        boolean hasName = it.getBuildingName() != null && !it.getBuildingName().isBlank();
        c.setNome(hasName ? it.getBuildingName().trim() : "Condominio " + it.getBuildingId());
        c.setIndirizzo(it.getBuildingAddress() != null ? it.getBuildingAddress().trim() : "");
        c.setClasseEnergetica("");
        c.setUnitaAbitative(0);
        c.setAnnoCostruzione(null);
        c.setNumeroPiani(null);
        c.setSuperficie(null);
        c.setRegolamenti("");
        c.setLatitudine(null);
        c.setLongitudine(null);
        // amministratore: non presente nel JSON consumi → rimane null

        Condominio saved = condominioRepository.save(c);
        warnings.add("Creato nuovo condominio da building_id: " + saved.getIdCondominio());
        return saved;
    }

    private Optional<User> resolveOwner(ConsumoIngestItem it, List<String> warnings) {
        // Regola: client_mail è la sorgente di verità (stabile). client_id può non corrispondere al DB locale.
        String email = it.getClientMail() != null ? it.getClientMail().trim().toLowerCase() : "";
        if (!email.isBlank()) {
            Optional<User> byMail = userRepository.findByEmail(email);
            if (byMail.isPresent()) {
                User existing = byMail.get();
                // Se arriva anche client_id ma punta ad un altro utente, segnala (ma NON usare quel client_id).
                if (it.getClientId() != null && !existing.getId().equals(it.getClientId())) {
                    warnings.add("client_id (" + it.getClientId() + ") non coerente con client_mail (" + email + "). Usata email come chiave.");
                }
                // Se non è valorizzato, aggancia il client al condominio del JSON
                if (existing.getIdCondominio() == null && it.getBuildingId() != null) {
                    existing.setIdCondominio(it.getBuildingId());
                    userRepository.save(existing);
                }
                return Optional.of(existing);
            }

            // Fallback importante: in alcuni flussi l'utente reale fa login con username (es. "chiara.verdi")
            // e non ha email valorizzata nel DB, oppure il JSON porta un identificativo senza dominio.
            // In questo caso proviamo ad agganciare per username derivato dall'email (parte prima di @, o stringa intera).
            String baseUsername = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
            baseUsername = baseUsername.replaceAll("[^a-zA-Z0-9._-]", "");
            if (!baseUsername.isBlank()) {
                Optional<User> byUsername = userRepository.findByUsername(baseUsername);
                if (byUsername.isPresent()) {
                    User u = byUsername.get();
                    String existingEmail = u.getEmail() != null ? u.getEmail().trim().toLowerCase() : "";
                    if (existingEmail.isBlank() || existingEmail.equals(email)) {
                        if (existingEmail.isBlank()) {
                            u.setEmail(email);
                        }
                        if (u.getRuolo() == null || u.getRuolo().isBlank()) {
                            u.setRuolo("CLIENT");
                        }
                        if (u.getIdCondominio() == null && it.getBuildingId() != null) {
                            u.setIdCondominio(it.getBuildingId());
                        }
                        userRepository.save(u);
                        warnings.add("Agganciato client_mail a utente esistente per username=" + baseUsername);
                        return Optional.of(u);
                    }
                    warnings.add("Trovato username=" + baseUsername + " ma con email diversa (" + existingEmail + "). Non aggancio per evitare mismatch.");
                }
            }

            // Se non esiste per email, prova a usare client_id SOLO se presente e compatibile:
            // - se l'utente esiste e non ha email o ha la stessa email, lo normalizziamo.
            if (it.getClientId() != null) {
                Optional<User> byId = userRepository.findById(it.getClientId());
                if (byId.isPresent()) {
                    User u = byId.get();
                    String existingEmail = u.getEmail() != null ? u.getEmail().trim().toLowerCase() : "";
                    if (existingEmail.isBlank() || existingEmail.equals(email)) {
                        if (existingEmail.isBlank()) {
                            u.setEmail(email);
                        }
                        if (u.getRuolo() == null || u.getRuolo().isBlank()) {
                            u.setRuolo("CLIENT");
                        }
                        if (u.getIdCondominio() == null && it.getBuildingId() != null) {
                            u.setIdCondominio(it.getBuildingId());
                        }
                        userRepository.save(u);
                        warnings.add("Agganciato client_mail a utente esistente per client_id=" + it.getClientId());
                        return Optional.of(u);
                    }
                    warnings.add("client_id (" + it.getClientId() + ") appartiene a email diversa (" + existingEmail + "). Ignorato client_id.");
                }
            }

            // Crea l'utente UNA volta (id univoco DB), derivando username dall'email
            User u = new User();
            u.setEmail(email);
            u.setRuolo("CLIENT");
            u.setNome("");
            u.setCognome("");
            u.setIdCondominio(it.getBuildingId());

            String baseUsername2 = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
            baseUsername2 = baseUsername2.replaceAll("[^a-zA-Z0-9._-]", "");
            if (baseUsername2.isBlank()) {
                baseUsername2 = "client";
            }
            String candidate = baseUsername2;
            if (userRepository.existsByUsername(candidate)) {
                candidate = baseUsername2 + "_" + UUID.randomUUID().toString().substring(0, 8);
            }
            u.setUsername(candidate);

            // Password non necessaria per utenti "gateway-created" (non usano login).
            // Mettiamo comunque un hash BCrypt per rispettare la logica di login esistente.
            u.setPassword(new BCryptPasswordEncoder().encode(UUID.randomUUID().toString()));

            User saved = userRepository.save(u);
            warnings.add("Creato nuovo utente da client_mail: " + email + " (id=" + saved.getId() + ")");
            return Optional.of(saved);
        }

        // Fallback: se manca email ma c'è client_id, usa quello
        if (it.getClientId() != null) {
            Optional<User> byId = userRepository.findById(it.getClientId());
            if (byId.isPresent()) {
                return byId;
            }
        }

        warnings.add("Riga senza client_id/client_mail: dispositivo non verrà attribuito a un owner.");
        return Optional.empty();
    }

    private Dispositivo resolveDevice(Condominio condominio, ConsumoIngestItem it, User owner) {
        // Priorità: asset_id (numerico) se presente
        Optional<Dispositivo> byAsset = it.getAssetId() != null
                ? dispositivoRepository.findByAssetIdAndCondominio_IdCondominio(it.getAssetId(), condominio.getIdCondominio())
                : Optional.empty();
        if (byAsset.isPresent()) {
            Dispositivo d = byAsset.get();
            updateDeviceFields(d, it, owner);
            return dispositivoRepository.save(d);
        }

        // Fallback: external device id
        if (it.getDeviceId() != null && !it.getDeviceId().isBlank()) {
            Optional<Dispositivo> byExt = dispositivoRepository.findByExternalDeviceIdAndCondominio_IdCondominio(it.getDeviceId().trim(), condominio.getIdCondominio());
            if (byExt.isPresent()) {
                Dispositivo d = byExt.get();
                updateDeviceFields(d, it, owner);
                return dispositivoRepository.save(d);
            }
        }

        // Create new device
        Dispositivo d = new Dispositivo();
        d.setCondominio(condominio);
        updateDeviceFields(d, it, owner);
        // nome "di comodo" se non presente
        if (d.getNome() == null || d.getNome().isBlank()) {
            d.setNome(it.getAssetName() != null ? it.getAssetName() : "asset_" + (it.getAssetId() != null ? it.getAssetId() : it.getDeviceId()));
        }
        if (d.getTipo() == null) d.setTipo(inferCategory(it));
        if (d.getStato() == null) d.setStato("attivo");
        return dispositivoRepository.save(d);
    }

    private void updateDeviceFields(Dispositivo d, ConsumoIngestItem it, User owner) {
        if (it.getDeviceId() != null && !it.getDeviceId().isBlank()) {
            d.setExternalDeviceId(it.getDeviceId().trim());
        }
        if (it.getAssetId() != null) d.setAssetId(it.getAssetId());
        if (it.getAssetName() != null) d.setAssetName(it.getAssetName());
        if (it.getDeviceBrand() != null && !it.getDeviceBrand().isBlank()) {
            d.setMarca(it.getDeviceBrand().trim());
        }
        if (it.getDeviceModel() != null && !it.getDeviceModel().isBlank()) {
            d.setModello(it.getDeviceModel().trim());
        }
        // Se il JSON non porta brand/model, riempi almeno con asset_name così a schermo non resta vuoto.
        // Non sovrascrive valori già presenti (o arrivati dal JSON).
        if ((d.getMarca() == null || d.getMarca().isBlank()) && it.getAssetName() != null && !it.getAssetName().isBlank()) {
            d.setMarca(it.getAssetName().trim());
        }
        if ((d.getModello() == null || d.getModello().isBlank()) && it.getAssetName() != null && !it.getAssetName().isBlank()) {
            d.setModello(it.getAssetName().trim());
        }
        // Categoria per l'app
        if (it.getCategory() != null && !it.getCategory().isBlank()) {
            d.setTipo(it.getCategory().trim());
        } else if (d.getTipo() == null || d.getTipo().isBlank() || "consumi".equalsIgnoreCase(d.getTipo())) {
            d.setTipo(inferCategory(it));
        }
        if (owner != null) d.setOwner(owner);
    }

    private ParametroDispositivo createParametro(Dispositivo dispositivo, ConsumoIngestItem it) {
        ParametroDispositivo p = new ParametroDispositivo();
        p.setDispositivo(dispositivo);
        p.setNome(it.getMeasure().trim());
        p.setTipologia("Telemetria gateway");
        p.setUnitaMisura(it.getUnit() != null ? it.getUnit() : "");
        p.setGroupAddress(it.getGroupAddress());
        p.setDpt(it.getDpt());

        SensorGaugeDefaults.Range rd = SensorGaugeDefaults.infer(it.getUnit(), it.getMeasure());
        p.setValMin(rd.min());
        p.setValMax(rd.max());
        p.setMaxDelta(rd.maxDelta());

        return parametroDispositivoRepository.save(p);
    }

    private static String inferCategory(ConsumoIngestItem it) {
        String unit = it.getUnit() != null ? it.getUnit().trim() : "";
        String m = it.getMeasure() != null ? it.getMeasure().toLowerCase().trim() : "";
        if (unit.equalsIgnoreCase("C") || unit.equalsIgnoreCase("°C") || m.contains("temperature")) {
            return "Calore";
        }
        if (unit.equalsIgnoreCase("W") || unit.equalsIgnoreCase("kW") || unit.equalsIgnoreCase("Wh") || unit.equalsIgnoreCase("kWh") || m.contains("energy") || m.contains("power")) {
            return "Energia";
        }
        if (unit.equalsIgnoreCase("%") || m.contains("percent") || m.contains("valvola")) {
            return "Ambiente Clima";
        }
        // fallback
        return "Energia";
    }

    public record IngestSummary(int readingsSaved, List<String> warnings) {}
}

