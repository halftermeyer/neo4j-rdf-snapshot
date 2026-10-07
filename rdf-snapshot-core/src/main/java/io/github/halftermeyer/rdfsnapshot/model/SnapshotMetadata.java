package io.github.halftermeyer.rdfsnapshot.model;

import java.time.Instant;
import java.util.Objects;

/** What the default graph says about the snapshot (SPEC §10). */
public record SnapshotMetadata(String snapshotId, String database, String scope, Instant generatedAt) {
    public SnapshotMetadata {
        Objects.requireNonNull(snapshotId, "snapshotId");
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(generatedAt, "generatedAt");
    }
}
