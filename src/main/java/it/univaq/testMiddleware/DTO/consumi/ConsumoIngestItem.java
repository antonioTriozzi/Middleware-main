package it.univaq.testMiddleware.DTO.consumi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Singolo elemento del JSON consumi (lista piatta) proveniente dal gateway.
 * Supporta Modbus TCP/IP, Modbus RTU, M-Bus, KNX.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConsumoIngestItem {

    private String measure;

    @JsonDeserialize(using = ConsumoIngestDoubleDeserializer.class)
    private Double value;

    private String unit;

    @JsonProperty("device_id")
    private String deviceId;

    @JsonProperty("building_id")
    private Long buildingId;

    @JsonProperty("asset_id")
    private Long assetId;

    @JsonProperty("asset_name")
    private String assetName;

    @JsonProperty("client_id")
    private Long clientId;

    @JsonProperty("client_mail")
    private String clientMail;

    /**
     * Categoria logica per l'app mobile (es. Energia, Calore, Ambiente Clima, Colonnine).
     * Se assente, il backend proverà a dedurla da unit/measure.
     */
    private String category;

    /** Opzionale: se il gateway invia metadati edificio, aggiorniamo il condominio. */
    @JsonProperty("building_name")
    private String buildingName;

    @JsonProperty("building_address")
    private String buildingAddress;

    /** Opzionale: metadati dispositivo lato gateway (mappati su marca/modello). */
    @JsonProperty("device_brand")
    private String deviceBrand;

    @JsonProperty("device_model")
    private String deviceModel;

    // KNX-only
    @JsonProperty("group_address")
    private String groupAddress;

    private String dpt;

    private String raw;
}

