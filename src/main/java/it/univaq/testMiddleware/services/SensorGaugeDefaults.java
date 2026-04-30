package it.univaq.testMiddleware.services;

/**
 * Range UI coerenti per gauge (min/max/delta) in base a unità e nome misura.
 * Usato da ingest consumi/telemetria e da {@code RealTimeController} quando mancano val_min/val_max sul parametro.
 */
public final class SensorGaugeDefaults {

    private SensorGaugeDefaults() {}

    public record Range(double min, double max, double maxDelta) {}

    public static Range infer(String unit, String measureName) {
        String u = unit != null ? unit.trim() : "";
        String m = measureName != null ? measureName.toLowerCase().trim() : "";
        if ("W".equalsIgnoreCase(u)) return new Range(0.0, 10_000.0, 250.0);
        if ("kW".equalsIgnoreCase(u)) return new Range(0.0, 50.0, 2.0);
        if ("Wh".equalsIgnoreCase(u)) return new Range(0.0, 5_000_000.0, 10_000.0);
        if ("kWh".equalsIgnoreCase(u)) return new Range(0.0, 50_000.0, 10.0);
        if ("C".equalsIgnoreCase(u) || "°C".equalsIgnoreCase(u)) return new Range(-10.0, 90.0, 2.0);
        if ("%".equalsIgnoreCase(u)) return new Range(0.0, 100.0, 5.0);
        if (m.contains("switch")) return new Range(0.0, 1.0, 1.0);
        return new Range(0.0, 100.0, 10.0);
    }
}
