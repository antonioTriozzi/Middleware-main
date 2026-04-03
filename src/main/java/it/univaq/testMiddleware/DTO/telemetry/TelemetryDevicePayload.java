package it.univaq.testMiddleware.DTO.telemetry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Un campione in uscita dal gateway: un dispositivo a un dato timestamp.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelemetryDevicePayload {

    @JsonProperty("device_id")
    private String deviceId;

    private String timestamp;

    @JsonDeserialize(using = FlexibleReadingsDeserializer.class)
    private List<TelemetryMeasurementItem> readings;
}
