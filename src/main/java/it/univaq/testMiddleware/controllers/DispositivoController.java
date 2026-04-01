package it.univaq.testMiddleware.controllers;

import it.univaq.testMiddleware.models.Condominio;
import it.univaq.testMiddleware.models.DatoSensore;
import it.univaq.testMiddleware.models.Dispositivo;
import it.univaq.testMiddleware.models.ParametroDispositivo;
import it.univaq.testMiddleware.repositories.CondominioRepository;
import it.univaq.testMiddleware.repositories.DatoSensoreRepository;
import it.univaq.testMiddleware.repositories.DispositivoRepository;
import it.univaq.testMiddleware.repositories.ParametroDispositivoRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class DispositivoController {

    @Autowired
    private DispositivoRepository dispositivoRepository;

    @Autowired
    private CondominioRepository condominioRepository;

    @Autowired
    private ParametroDispositivoRepository parametroRepository;

    @Autowired
    private DatoSensoreRepository datoSensoreRepository;

    // DTO per Dispositivo che include i parametri
    public static class DispositivoDTO {
        private Long idDispositivo;
        private String nome;
        private String marca;
        private String modello;
        private String tipo;
        private String stato;
        private List<ParametroDispositivo> parametri;

        public DispositivoDTO(Dispositivo dispositivo) {
            this.idDispositivo = dispositivo.getIdDispositivo();
            this.nome = dispositivo.getNome();
            this.marca = dispositivo.getMarca();
            this.modello = dispositivo.getModello();
            this.tipo = dispositivo.getTipo();
            this.stato = dispositivo.getStato();
            // Forza l'inizializzazione della collection dei parametri
            if (dispositivo.getParametriDispositivo() != null) {
                dispositivo.getParametriDispositivo().size();
                this.parametri = dispositivo.getParametriDispositivo();
            }
        }

        // Getters & Setters
        public Long getIdDispositivo() {
            return idDispositivo;
        }

        public void setIdDispositivo(Long idDispositivo) {
            this.idDispositivo = idDispositivo;
        }

        public String getNome() {
            return nome;
        }

        public void setNome(String nome) {
            this.nome = nome;
        }

        public String getMarca() {
            return marca;
        }

        public void setMarca(String marca) {
            this.marca = marca;
        }

        public String getModello() {
            return modello;
        }

        public void setModello(String modello) {
            this.modello = modello;
        }

        public String getTipo() {
            return tipo;
        }

        public void setTipo(String tipo) {
            this.tipo = tipo;
        }

        public String getStato() {
            return stato;
        }

        public void setStato(String stato) {
            this.stato = stato;
        }

        public List<ParametroDispositivo> getParametri() {
            return parametri;
        }

        public void setParametri(List<ParametroDispositivo> parametri) {
            this.parametri = parametri;
        }
    }

    // DTO per la risposta GET: include il condominio e la lista dei dispositivi (con i relativi parametri)
    public static class CondominioDispositiviResponse {
        private Condominio condominio;
        private List<DispositivoDTO> dispositivi;

        public CondominioDispositiviResponse() {}

        public Condominio getCondominio() {
            return condominio;
        }

        public void setCondominio(Condominio condominio) {
            this.condominio = condominio;
        }

        public List<DispositivoDTO> getDispositivi() {
            return dispositivi;
        }

        public void setDispositivi(List<DispositivoDTO> dispositivi) {
            this.dispositivi = dispositivi;
        }
    }

    /**
     * GET: Ottiene i dati del condominio e tutti i dispositivi associati (con i relativi parametri).
     * URL: GET /api/condomini/{idCondominio}/dispositivi
     */
    @GetMapping("/condomini/{idCondominio}/dispositivi")
    @Transactional
    public ResponseEntity<CondominioDispositiviResponse> getDispositiviByCondominio(@PathVariable Long idCondominio) {
        Optional<Condominio> condOptional = condominioRepository.findById(idCondominio);
        if (!condOptional.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        Condominio condominio = condOptional.get();
        List<Dispositivo> dispositivi = dispositivoRepository.findByCondominio(condominio);

        // Mappiamo ogni dispositivo in un DTO che include anche i parametri
        List<DispositivoDTO> dispositiviDTO = dispositivi.stream()
                .map(DispositivoDTO::new)
                .collect(Collectors.toList());

        CondominioDispositiviResponse response = new CondominioDispositiviResponse();
        response.setCondominio(condominio);
        response.setDispositivi(dispositiviDTO);
        return ResponseEntity.ok(response);
    }

    // DTO di input per il parametro (i campi che il client invia)
    public static class ParametroInputDTO {
        private String nome;
        private String tipologia;
        private String unitaMisura;
        private Double valMin;
        private Double valMax;
        private Double maxDelta;
        // Getters & Setters
        public String getNome() { return nome; }
        public void setNome(String nome) { this.nome = nome; }
        public String getTipologia() { return tipologia; }
        public void setTipologia(String tipologia) { this.tipologia = tipologia; }
        public String getUnitaMisura() { return unitaMisura; }
        public void setUnitaMisura(String unitaMisura) { this.unitaMisura = unitaMisura; }
        public Double getValMin() { return valMin; }
        public void setValMin(Double valMin) { this.valMin = valMin; }
        public Double getValMax() { return valMax; }
        public void setValMax(Double valMax) { this.valMax = valMax; }
        public Double getMaxDelta() { return maxDelta; }
        public void setMaxDelta(Double maxDelta) { this.maxDelta = maxDelta; }
    }

    // DTO di input per il dispositivo
    public static class DispositivoInputDTO {
        private String nome;
        private String marca;
        private String modello;
        private String tipo;
        private String stato;
        private List<ParametroInputDTO> parametri;
        // Getters & Setters
        public String getNome() { return nome; }
        public void setNome(String nome) { this.nome = nome; }
        public String getMarca() { return marca; }
        public void setMarca(String marca) { this.marca = marca; }
        public String getModello() { return modello; }
        public void setModello(String modello) { this.modello = modello; }
        public String getTipo() { return tipo; }
        public void setTipo(String tipo) { this.tipo = tipo; }
        public String getStato() { return stato; }
        public void setStato(String stato) { this.stato = stato; }
        public List<ParametroInputDTO> getParametri() { return parametri; }
        public void setParametri(List<ParametroInputDTO> parametri) { this.parametri = parametri; }
    }

    /**
     * POST: Aggiunge un nuovo dispositivo ad un condominio.
     * URL: POST /api/condomini/{idCondominio}/dispositivi
     * Il corpo della richiesta deve contenere i dati del dispositivo, compresi i parametri (nella proprietà "parametri").
     */
    @PostMapping("/condomini/{idCondominio}/dispositivi")
    @Transactional
    public ResponseEntity<Dispositivo> addDispositivo(@PathVariable Long idCondominio, @RequestBody DispositivoInputDTO input) {
        Optional<Condominio> condOptional = condominioRepository.findById(idCondominio);
        if (!condOptional.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        Condominio condominio = condOptional.get();

        // Mappiamo il DTO all'entità Dispositivo
        Dispositivo dispositivo = new Dispositivo();
        dispositivo.setNome(input.getNome());
        dispositivo.setMarca(input.getMarca());
        dispositivo.setModello(input.getModello());
        dispositivo.setTipo(input.getTipo());
        dispositivo.setStato(input.getStato());
        dispositivo.setCondominio(condominio);

        // Mappiamo la lista di parametri dal DTO alla collection "parametriDispositivo" dell'entità
        if (input.getParametri() != null && !input.getParametri().isEmpty()) {
            List<ParametroDispositivo> paramList = input.getParametri().stream().map(pi -> {
                ParametroDispositivo p = new ParametroDispositivo();
                p.setNome(pi.getNome());
                p.setTipologia(pi.getTipologia());
                p.setUnitaMisura(pi.getUnitaMisura());
                p.setValMin(pi.getValMin());
                p.setValMax(pi.getValMax());
                p.setMaxDelta(pi.getMaxDelta());
                p.setDispositivo(dispositivo);
                return p;
            }).collect(Collectors.toList());
            dispositivo.setParametriDispositivo(paramList);
        }

        Dispositivo saved = dispositivoRepository.save(dispositivo);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }


    @GetMapping("/dispositivi/{idDispositivo}")
    @Transactional
    public ResponseEntity<DispositivoDTO> getDispositivo(@PathVariable Long idDispositivo) {
        Optional<Dispositivo> deviceOpt = dispositivoRepository.findById(idDispositivo);
        if (!deviceOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        Dispositivo dispositivo = deviceOpt.get();
        // Forza l'inizializzazione della collection dei parametri
        if (dispositivo.getParametriDispositivo() != null) {
            dispositivo.getParametriDispositivo().size();
        }
        // Restituisce il DTO, che include anche i parametri
        DispositivoDTO dto = new DispositivoDTO(dispositivo);
        return ResponseEntity.ok(dto);
    }


    @PutMapping("/dispositivi/{idDispositivo}")
    @Transactional
    public ResponseEntity<Dispositivo> updateDispositivo(@PathVariable Long idDispositivo, @RequestBody DispositivoInputDTO input) {
        Optional<Dispositivo> dispOpt = dispositivoRepository.findById(idDispositivo);
        if (!dispOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        Dispositivo dispositivo = dispOpt.get();

        // Aggiorna i campi base
        dispositivo.setNome(input.getNome());
        dispositivo.setMarca(input.getMarca());
        dispositivo.setModello(input.getModello());
        dispositivo.setTipo(input.getTipo());
        dispositivo.setStato(input.getStato());

        // Aggiorna i parametri: cancella quelli esistenti e aggiungi i nuovi
        if (input.getParametri() != null) {
            dispositivo.getParametriDispositivo().clear();
            for (ParametroInputDTO paramDTO : input.getParametri()) {
                ParametroDispositivo nuovoParametro = new ParametroDispositivo();
                nuovoParametro.setNome(paramDTO.getNome());
                nuovoParametro.setTipologia(paramDTO.getTipologia());
                nuovoParametro.setUnitaMisura(paramDTO.getUnitaMisura());
                nuovoParametro.setValMin(paramDTO.getValMin());
                nuovoParametro.setValMax(paramDTO.getValMax());
                nuovoParametro.setMaxDelta(paramDTO.getMaxDelta());
                nuovoParametro.setDispositivo(dispositivo);
                dispositivo.getParametriDispositivo().add(nuovoParametro);
            }
        }

        Dispositivo updatedDispositivo = dispositivoRepository.save(dispositivo);
        return ResponseEntity.ok(updatedDispositivo);
    }


    /**
     * DELETE: Elimina un dispositivo.
     * URL: DELETE /api/dispositivi/{idDispositivo}
     */
    @DeleteMapping("/dispositivi/{idDispositivo}")
    @Transactional
    public ResponseEntity<Void> deleteDispositivo(@PathVariable Long idDispositivo) {
        Optional<Dispositivo> dispOpt = dispositivoRepository.findById(idDispositivo);
        if (!dispOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        dispositivoRepository.delete(dispOpt.get());
        return ResponseEntity.noContent().build();
    }

    /**
     * DELETE: Elimina un parametro e tutti i dati sensori associati.
     * URL: DELETE /api/parametri/{idParametro}
     */
    @DeleteMapping("/parametri/{idParametro}")
    @Transactional
    public ResponseEntity<Void> deleteParametro(@PathVariable Long idParametro) {
        Optional<ParametroDispositivo> parametroOpt = parametroRepository.findById(idParametro);
        if (!parametroOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        ParametroDispositivo parametro = parametroOpt.get();

        // Recupera e cancella i dati sensori associati al parametro
        List<DatoSensore> sensori = datoSensoreRepository.findByParametro(parametro);
        if (sensori != null && !sensori.isEmpty()) {
            datoSensoreRepository.deleteAll(sensori);
        }

        // Ora elimina il parametro
        parametroRepository.delete(parametro);
        return ResponseEntity.noContent().build();
    }

}
