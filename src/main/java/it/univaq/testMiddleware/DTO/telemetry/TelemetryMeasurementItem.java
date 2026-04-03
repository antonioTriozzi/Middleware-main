package it.univaq.testMiddleware.DTO.telemetry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Misura normalizzata dopo il driver (name / value / unit), come nel payload in uscita.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelemetryMeasurementItem {

    private String name;

    private Double value;

    private String unit;
}
