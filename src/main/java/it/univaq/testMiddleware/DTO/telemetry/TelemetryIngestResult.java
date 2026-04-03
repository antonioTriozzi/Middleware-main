package it.univaq.testMiddleware.DTO.telemetry;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TelemetryIngestResult {

    private int readingsSaved;

    private List<String> warnings = new ArrayList<>();
}
