package io.github.halftermeyer.rdfsnapshot.procedure;

import io.github.halftermeyer.rdfsnapshot.IriMinter;
import io.github.halftermeyer.rdfsnapshot.neo4j.SnapshotRules;
import io.github.halftermeyer.rdfsnapshot.neo4j.Snapshotter;
import io.github.halftermeyer.rdfsnapshot.sink.NQuadsSink;
import io.github.halftermeyer.rdfsnapshot.sink.QuadSink;
import io.github.halftermeyer.rdfsnapshot.sink.TriGSink;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.neo4j.graphdb.Transaction;

/** The work behind {@code rdfsnapshot.export}. */
public final class SnapshotExport {
    static final int CHUNK_SIZE = 16 * 1024;

    private SnapshotExport() {}

    /**
     * Collects the scope, then streams the serialized snapshot. Everything runs in {@code tx}, so
     * the snapshot is one consistent read; the stream must be consumed before the transaction ends.
     */
    public static Stream<Chunk> run(Transaction tx, String database, String scopeQuery, Map<String, Object> config) {
        Instant now = SnapshotRules.exportInstant();
        ExportConfig cfg = ExportConfig.parse(config, now);
        SnapshotRules.requireScopeQuery(scopeQuery);

        StringBuilder buffer = new StringBuilder(CHUNK_SIZE * 2);
        QuadSink sink = switch (cfg.format()) {
            case NQUADS -> new NQuadsSink(buffer);
            case TRIG -> new TriGSink(buffer, new IriMinter(cfg.base()));
        };
        Iterator<Runnable> steps = Snapshotter
                .steps(tx, database, scopeQuery, cfg.base(), cfg.snapshotId(), now, sink)
                .iterator();

        ChunkIterator chunks = new ChunkIterator(buffer, steps, CHUNK_SIZE);
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(chunks, Spliterator.ORDERED), false)
                .map(Chunk::new);
    }
}
