package io.github.halftermeyer.rdfsnapshot.procedure;

import java.util.Map;
import java.util.stream.Stream;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Mode;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

/**
 * {@code CALL rdfsnapshot.export(scopeQuery, config) YIELD chunk}.
 *
 * <p>Kept to a bare delegation on purpose: Neo4j loads this annotated class in its own class
 * loader and every other class of the jar through the parent loader, so this class may only use
 * public members of other classes (see docs/DECISIONS.md).
 */
public class ExportProcedure {

    @Context
    public Transaction tx;

    @Context
    public GraphDatabaseService db;

    @Procedure(name = "rdfsnapshot.export", mode = Mode.READ)
    @Description("Exports the subgraph returned by scopeQuery as an RDF snapshot. "
            + "config: base (required), format ('nquads' | 'trig', default 'trig'), "
            + "snapshotId (default: UTC timestamp yyyyMMdd'T'HHmmss'Z'), includeSchema (default true).")
    public Stream<Chunk> export(
            @Name("scopeQuery") String scopeQuery,
            @Name(value = "config", defaultValue = "{}") Map<String, Object> config) {
        return SnapshotExport.run(tx, db.databaseName(), scopeQuery, config);
    }
}
