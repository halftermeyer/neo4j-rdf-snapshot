package io.github.halftermeyer.rdfsnapshot.procedure;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;

/** The {@code config} map of {@code rdfsnapshot.export}. */
public record ExportConfig(String base, Format format, String snapshotId) {

    public enum Format {
        NQUADS,
        TRIG
    }

    private static final Set<String> KEYS = Set.of("base", "format", "snapshotId");
    static final DateTimeFormatter SNAPSHOT_ID = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    /** @param now the start of the export, used for the default snapshot ID */
    public static ExportConfig parse(Map<String, Object> config, Instant now) {
        Map<String, Object> map = config == null ? Map.of() : config;
        for (String key : map.keySet()) {
            if (!KEYS.contains(key)) {
                throw new IllegalArgumentException("Unknown config key '" + key + "', expected one of " + KEYS);
            }
        }
        Object base = map.get("base");
        if (!(base instanceof String b) || b.isBlank()) {
            throw new IllegalArgumentException("config.base is required, e.g. {base: 'http://acme.org/plm'}");
        }
        Object format = map.getOrDefault("format", "trig");
        Format f = switch (format instanceof String s ? s : "") {
            case "nquads" -> Format.NQUADS;
            case "trig" -> Format.TRIG;
            default -> throw new IllegalArgumentException("config.format must be 'nquads' or 'trig', got " + format);
        };
        Object snapshotId = map.getOrDefault("snapshotId", SNAPSHOT_ID.format(now));
        if (!(snapshotId instanceof String id) || id.isEmpty()) {
            throw new IllegalArgumentException("config.snapshotId must be a non-empty string");
        }
        return new ExportConfig(b, f, id);
    }
}
