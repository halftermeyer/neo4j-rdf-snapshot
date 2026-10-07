package io.github.halftermeyer.rdfsnapshot.conformance;

import java.nio.file.Files;
import java.nio.file.Path;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.testcontainers.neo4j.Neo4jContainer;
import org.testcontainers.utility.MountableFile;

/** One Neo4j Enterprise container per JVM, with the plugin jar mounted. */
final class Neo4jFixture {
    private static Neo4jContainer container;
    private static Driver driver;

    private Neo4jFixture() {}

    static synchronized Driver driver() {
        if (driver == null) {
            Path jar = Path.of(System.getProperty("plugin.jar"));
            if (!Files.isRegularFile(jar)) {
                throw new IllegalStateException("Plugin jar not found: " + jar + " (run `mvn verify` from the root)");
            }
            container = new Neo4jContainer(System.getProperty("neo4j.image", "neo4j:2026.09.0-enterprise"))
                    .withEnv("NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes")
                    .withAdminPassword("conformance")
                    .withPlugins(MountableFile.forHostPath(jar))
                    // case files are written in Cypher 25
                    .withNeo4jConfig("db.query.default_language", "CYPHER_25");
            container.start();
            driver = GraphDatabase.driver(container.getBoltUrl(), AuthTokens.basic("neo4j", "conformance"));
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                driver.close();
                container.stop();
            }));
        }
        return driver;
    }
}
