package it.univaq.testMiddleware.DTO;

import lombok.Data;

import java.time.Instant;

// DTO per il valore del sensore
@Data
public class SensorValueDTO {
    private String valore;
    private Instant timestamp;
}
