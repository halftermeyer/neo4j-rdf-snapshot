package io.github.halftermeyer.rdfsnapshot.conformance;

import java.util.Map;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;

/** Loads a case into a fresh database and runs the export. */
final class CaseRunner {
    static final String DATABASE = "conformance";
    static final String BASE = "http://acme.org/plm";
    static final String SNAPSHOT_ID = "20261005T140311Z";

    private final Driver driver;

    CaseRunner(Driver driver) {
        this.driver = driver;
    }

    /** Fresh database (no data, no graph type), then graph type, then data. */
    void load(ConformanceCase c) {
        try (Session system = driver.session(SessionConfig.forDatabase("system"))) {
            system.run("CREATE OR REPLACE DATABASE " + DATABASE + " WAIT").consume();
        }
        try (Session session = session()) {
            for (String statement : c.graphType()) {
                session.run(statement).consume();
            }
            for (String statement : c.data()) {
                session.run(statement, c.params()).consume();
            }
        }
    }

    /** Runs the procedure and concatenates its chunks. */
    String export(ConformanceCase c, String format) {
        try (Session session = session()) {
            StringBuilder out = new StringBuilder();
            session.run("CALL rdfsnapshot.export($scope, $config) YIELD chunk RETURN chunk",
                            Map.of("scope", c.scope(),
                                    "config", Map.of("base", BASE, "format", format, "snapshotId", SNAPSHOT_ID)))
                    .forEachRemaining((Record r) -> out.append(r.get("chunk").asString()));
            return out.toString();
        }
    }

    private Session session() {
        return driver.session(SessionConfig.forDatabase(DATABASE));
    }
}
