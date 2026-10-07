package io.github.halftermeyer.rdfsnapshot.procedure;

import io.github.halftermeyer.rdfsnapshot.neo4j.SnapshotRules;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/** The {@code config} map of {@code rdfsnapshot.export}. */
public record ExportConfig(String base, Format format, String snapshotId) {

    public enum Format {
        NQUADS,
        TRIG
    }

    private static final Set<String> KEYS = Set.of("base", "format", "snapshotId");

    /** @param now the start of the export, used for the default snapshot ID */
    public static ExportConfig parse(Map<String, Object> config, Instant now) {
        Map<String, Object> map = config == null ? Map.of() : config;
        for (String key : map.keySet()) {
            if (!KEYS.contains(key)) {
                throw new IllegalArgumentException("Unknown config key '" + key + "', expected one of " + KEYS);
            }
        }
        String base = SnapshotRules.requireBase(map.get("base"));
        Object format = map.getOrDefault("format", "trig");
        Format f = switch (format instanceof String s ? s : "") {
            case "nquads" -> Format.NQUADS;
            case "trig" -> Format.TRIG;
            default -> throw new IllegalArgumentException("config.format must be 'nquads' or 'trig', got " + format);
        };
        String snapshotId = SnapshotRules.requireSnapshotId(
                map.getOrDefault("snapshotId", SnapshotRules.defaultSnapshotId(now)));
        return new ExportConfig(base, f, snapshotId);
    }
}
