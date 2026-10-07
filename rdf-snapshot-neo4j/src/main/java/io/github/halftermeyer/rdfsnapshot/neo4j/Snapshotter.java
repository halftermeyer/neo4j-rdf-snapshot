package io.github.halftermeyer.rdfsnapshot.neo4j;

import io.github.halftermeyer.rdfsnapshot.IriMinter;
import io.github.halftermeyer.rdfsnapshot.SnapshotSerializer;
import io.github.halftermeyer.rdfsnapshot.Vocabulary;
import io.github.halftermeyer.rdfsnapshot.model.SnapshotMetadata;
import io.github.halftermeyer.rdfsnapshot.sink.QuadSink;
import io.github.halftermeyer.rdfsnapshot.term.Iri;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.stream.Stream;
import org.neo4j.graphdb.Transaction;

/**
 * Writes a snapshot of the subgraph returned by a scope query to any {@link QuadSink}. Everything
 * runs in the caller's transaction, so the snapshot is one consistent read (SPEC §3).
 */
public final class Snapshotter {
    private Snapshotter() {}

    /**
     * Writes the whole snapshot: metadata, vocabulary, nodes, relationships, then closes the sink.
     *
     * @param tx          the transaction to read from; the scope query runs in it
     * @param database    the database name, for {@code lpg:database}
     * @param scopeQuery  a read-only Cypher query (SPEC §3)
     * @param base        the base IRI (SPEC §5)
     * @param snapshotId  e.g. {@link SnapshotRules#defaultSnapshotId(Instant)}
     * @param generatedAt the export instant, for {@code prov:generatedAtTime}; truncated to the
     *                    second, e.g. {@link SnapshotRules#exportInstant()}
     * @throws IllegalArgumentException if an argument breaks {@link SnapshotRules}
     */
    public static void write(Transaction tx, String database, String scopeQuery,
            String base, String snapshotId, Instant generatedAt, QuadSink sink) {
        steps(tx, database, scopeQuery, base, snapshotId, generatedAt, sink).forEach(Runnable::run);
    }

    /**
     * The same work as {@link #write}, as a lazy sequence of steps, for callers that stream. The
     * scope query runs now and only element IDs are kept; each step then loads what it writes:
     * metadata, vocabulary (one pass over the elements), one step per node and per relationship in
     * element ID order, and finally {@link QuadSink#close()}. Every step ends with a complete
     * subject block, followed by {@link QuadSink#flush()}. Run all steps, in order, before the
     * transaction ends.
     */
    public static Stream<Runnable> steps(Transaction tx, String database, String scopeQuery,
            String base, String snapshotId, Instant generatedAt, QuadSink sink) {
        Objects.requireNonNull(tx, "tx");
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(generatedAt, "generatedAt");
        Objects.requireNonNull(sink, "sink");
        SnapshotRules.requireScopeQuery(scopeQuery);
        SnapshotRules.requireBase(base);
        SnapshotRules.requireSnapshotId(snapshotId);

        Scope scope = Scope.collect(tx, scopeQuery);

        IriMinter iris = new IriMinter(base);
        SnapshotSerializer serializer = new SnapshotSerializer(iris, sink);
        SnapshotMetadata metadata = new SnapshotMetadata(
                snapshotId, database, scopeQuery, generatedAt.truncatedTo(ChronoUnit.SECONDS));
        Iri graph = iris.snapshot(snapshotId);

        return Stream.of(
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
    }

    private static Vocabulary vocabulary(Transaction tx, Scope scope) {
        Vocabulary vocabulary = new Vocabulary();
        scope.nodeIds.forEach(id -> vocabulary.addNode(Neo4jAdapter.node(tx.getNodeByElementId(id))));
        scope.relationshipIds.forEach(id ->
                vocabulary.addRelationship(Neo4jAdapter.relationship(tx.getRelationshipByElementId(id))));
        return vocabulary;
    }
}
