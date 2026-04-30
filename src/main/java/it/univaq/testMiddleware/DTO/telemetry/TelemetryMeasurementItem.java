package it.univaq.testMiddleware.DTO.telemetry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Misura normalizzata dopo il driver (measure / value / unit), come nel payload in uscita.
 * Retro-compatibile: accetta anche il campo legacy "name".
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelemetryMeasurementItem {

    @JsonProperty("measure")
    @JsonAlias({"name"})
    private String measure;

    private Double value;

    private String unit;
}
