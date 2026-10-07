package io.github.halftermeyer.rdfsnapshot.neo4j;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * The input rules every caller applies, so that the procedure and embedding plugins produce the
 * same snapshots (docs/DECISIONS.md D2, D14, D17).
 */
public final class SnapshotRules {
    /** Format of the default snapshot ID: {@code yyyyMMdd'T'HHmmss'Z'}, UTC (SPEC §7.1). */
    public static final DateTimeFormatter SNAPSHOT_ID_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private SnapshotRules() {}

    /** The instant of an export starting now, truncated to the second (D14). */
    public static Instant exportInstant() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }

    /** The default snapshot ID for an export started at {@code generatedAt}, e.g. {@code 20261005T140311Z}. */
    public static String defaultSnapshotId(Instant generatedAt) {
        return SNAPSHOT_ID_FORMAT.format(generatedAt);
    }

    /**
     * @return the base IRI, unchanged (one trailing {@code /} is ignored when minting, D2)
     * @throws IllegalArgumentException unless a non-blank string
     */
    public static String requireBase(Object base) {
        if (!(base instanceof String b) || b.isBlank()) {
            throw new IllegalArgumentException("base is required, e.g. 'http://acme.org/plm'");
        }
        return b;
    }

    /** @throws IllegalArgumentException unless a non-empty string */
    public static String requireSnapshotId(Object snapshotId) {
        if (!(snapshotId instanceof String id) || id.isEmpty()) {
            throw new IllegalArgumentException("snapshotId must be a non-empty string");
        }
        return id;
    }

    /** @throws IllegalArgumentException if null or blank */
    public static String requireScopeQuery(String scopeQuery) {
        if (scopeQuery == null || scopeQuery.isBlank()) {
            throw new IllegalArgumentException("scopeQuery must not be empty");
        }
        return scopeQuery;
    }
}
