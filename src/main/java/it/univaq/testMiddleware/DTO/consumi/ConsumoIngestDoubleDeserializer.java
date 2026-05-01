package it.univaq.testMiddleware.DTO.consumi;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/**
 * Accetta numeri, stringhe numeriche e boolean JSON (KNX / M-Bus spesso espongono true/false per DPT 1.x).
 */
public class ConsumoIngestDoubleDeserializer extends JsonDeserializer<Double> {

    @Override
    public Double deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken t = p.currentToken();
        if (t == null || t == JsonToken.VALUE_NULL) {
            return null;
        }
        if (t == JsonToken.VALUE_TRUE) {
            return 1.0;
        }
        if (t == JsonToken.VALUE_FALSE) {
            return 0.0;
        }
        if (t == JsonToken.VALUE_NUMBER_FLOAT || t == JsonToken.VALUE_NUMBER_INT) {
            return p.getDoubleValue();
        }
        if (t == JsonToken.VALUE_STRING) {
            String s = p.getValueAsString();
            if (s == null) {
                return null;
            }
            s = s.trim();
            if (s.isEmpty()) {
                return null;
            }
            String lower = s.toLowerCase();
            if ("true".equals(lower) || "on".equals(lower) || "yes".equals(lower)) {
                return 1.0;
            }
            if ("false".equals(lower) || "off".equals(lower) || "no".equals(lower)) {
                return 0.0;
            }
            try {
                return Double.parseDouble(s.replace(',', '.'));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
