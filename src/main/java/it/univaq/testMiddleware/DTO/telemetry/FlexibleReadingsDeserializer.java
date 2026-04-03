package it.univaq.testMiddleware.DTO.telemetry;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Accetta sia un array JSON di misure sia una stringa che contiene JSON serializzato
 * (come compare in alcuni log di test della tesi).
 */
public class FlexibleReadingsDeserializer extends JsonDeserializer<List<TelemetryMeasurementItem>> {

    @Override
    public List<TelemetryMeasurementItem> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        ObjectMapper mapper = (ObjectMapper) p.getCodec();
        JsonNode node = mapper.readTree(p);
        if (node == null || node.isNull()) {
            return Collections.emptyList();
        }
        if (node.isTextual()) {
            String text = node.asText();
            if (text.isBlank()) {
                return Collections.emptyList();
            }
            return mapper.readValue(text, new TypeReference<List<TelemetryMeasurementItem>>() {});
        }
        if (node.isArray()) {
            return mapper.convertValue(node, new TypeReference<List<TelemetryMeasurementItem>>() {});
        }
        return Collections.emptyList();
    }
}
