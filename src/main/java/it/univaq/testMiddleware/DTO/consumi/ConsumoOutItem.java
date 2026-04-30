package it.univaq.testMiddleware.DTO.consumi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Formato "definitivo" consumi in uscita verso l'app mobile (lista piatta).
 * NB: non include campi extra KNX (group_address/dpt/raw).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConsumoOutItem {

    private String measure;

    private Object value;

    private String unit;

    /**
     * Categoria logica di consumo per la UI (es. Energia, Calore, Ambiente Clima, Colonnine).
     * Deriva tipicamente dal tipo dispositivo.
     */
    private String category;

    /**
     * Protocollo stimato della misura (es. KNX, M-Bus, Modbus, Telemetry).
     * Non include metadati KNX (group_address/dpt/raw) come richiesto.
     */
    private String protocol;

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
}

