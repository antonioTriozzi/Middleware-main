package it.univaq.testMiddleware.DTO.telemetry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Batch in <strong>uscita dal gateway</strong> verso il middleware (northbound).
 * È questo il JSON da replicare con un fake per testare l’endpoint; non riguarda
 * l’ingresso verso Modbus/M-Bus.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GatewayTelemetryPayload {

    @JsonProperty("gateway_id")
    private String gatewayId;

    private List<TelemetryDevicePayload> data;
}
