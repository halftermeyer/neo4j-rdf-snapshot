package io.github.halftermeyer.rdfsnapshot.procedure;

import io.github.halftermeyer.rdfsnapshot.IriMinter;
import io.github.halftermeyer.rdfsnapshot.SnapshotSerializer;
import io.github.halftermeyer.rdfsnapshot.Vocabulary;
import io.github.halftermeyer.rdfsnapshot.model.SnapshotMetadata;
import io.github.halftermeyer.rdfsnapshot.sink.NQuadsSink;
import io.github.halftermeyer.rdfsnapshot.sink.QuadSink;
import io.github.halftermeyer.rdfsnapshot.term.Iri;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        ExportConfig cfg = ExportConfig.parse(config, now);
        if (scopeQuery == null || scopeQuery.isBlank()) {
            throw new IllegalArgumentException("scopeQuery must not be empty");
        }

        Scope scope = Scope.collect(tx, scopeQuery);

        IriMinter iris = new IriMinter(cfg.base());
        StringBuilder buffer = new StringBuilder(CHUNK_SIZE * 2);
        QuadSink sink = switch (cfg.format()) {
            case NQUADS -> new NQuadsSink(buffer);
            case TRIG -> throw new IllegalArgumentException("format 'trig' is not implemented yet");
        };
        SnapshotSerializer serializer = new SnapshotSerializer(iris, sink);
        SnapshotMetadata metadata = new SnapshotMetadata(cfg.snapshotId(), database, scopeQuery, now);
        Iri graph = iris.snapshot(cfg.snapshotId());

        Stream<Runnable> steps = Stream.of(
                        Stream.<Runnable>of(
                                () -> serializer.writeMetadata(metadata),
                                () -> serializer.writeVocabulary(graph, vocabulary(tx, scope))),
                        scope.nodeIds.stream().<Runnable>map(id -> () ->
                                serializer.writeNode(graph, Neo4jAdapter.node(tx.getNodeByElementId(id)))),
                        scope.relationshipIds.stream().<Runnable>map(id -> () ->
                                serializer.writeRelationship(graph,
                                        Neo4jAdapter.relationship(tx.getRelationshipByElementId(id)))),
                        Stream.<Runnable>of(sink::close))
                .flatMap(s -> s);

        ChunkIterator chunks = new ChunkIterator(buffer, steps.iterator(), CHUNK_SIZE);
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(chunks, Spliterator.ORDERED), false)
                .map(Chunk::new);
    }

    private static Vocabulary vocabulary(Transaction tx, Scope scope) {
        Vocabulary vocabulary = new Vocabulary();
        scope.nodeIds.forEach(id -> vocabulary.addNode(Neo4jAdapter.node(tx.getNodeByElementId(id))));
        scope.relationshipIds.forEach(id ->
                vocabulary.addRelationship(Neo4jAdapter.relationship(tx.getRelationshipByElementId(id))));
        return vocabulary;
    }
}
