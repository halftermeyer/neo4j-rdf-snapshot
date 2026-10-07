package io.github.halftermeyer.rdfsnapshot.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.halftermeyer.rdfsnapshot.sink.NQuadsSink;
import io.github.halftermeyer.rdfsnapshot.sink.QuadSink;
import io.github.halftermeyer.rdfsnapshot.term.Iri;
import io.github.halftermeyer.rdfsnapshot.term.Term;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;

class SnapshotterTest {
    private static final String BASE = "http://acme.org/plm";
    private static final Instant AT = Instant.parse("2026-10-05T14:03:11.789Z");

    private final FakeGraph g = new FakeGraph();
    private final Node a = g.node("4:db:1", List.of("Step"), Map.of("n", 1L));
    private final Node b = g.node("4:db:2", List.of("Step"), Map.of("n", 2L));
    private final Node c = g.node("4:db:3", List.of("Step"), Map.of("n", 3L));
    private final Node inMap = g.node("4:db:4", List.of("Step"), Map.of("n", 4L));
    private final Node outside = g.node("4:db:5", List.of("Other"), Map.of());
    private final Relationship ab = g.rel("5:db:1", "NEXT", a, b, Map.of());
    private final Relationship bc = g.rel("5:db:2", "NEXT", b, c, Map.of("w", 1.5));

    /** A path, a nested list, a map, a relationship alone and an ignored value. */
    private Transaction tx() {
        return g.tx(List.of(Map.of(
                "p", FakeGraph.path(List.of(a, b), List.of(ab)),
                "nested", List.of(List.of(a)),
                "map", Map.of("k", inMap),
                "r", bc,
                "n", 42L)));
    }

    private String write() {
        StringBuilder out = new StringBuilder();
        Snapshotter.write(tx(), "plm", "MATCH ... RETURN ...", BASE, "s1", AT, new NQuadsSink(out));
        return out.toString();
    }

    @Test
    void scopeRules() {
        String out = write();
        for (String id : List.of("4:db:1", "4:db:2", "4:db:3", "4:db:4", "5:db:1", "5:db:2")) {
            assertTrue(out.contains("<http://acme.org/plm/e/" + id + "> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type>"), id);
        }
        // c comes in through the closure rule, inMap from a map value; outside is never returned
        assertFalse(out.contains("4:db:5"));
        assertTrue(out.contains("\"2026-10-05T14:03:11Z\"^^<http://www.w3.org/2001/XMLSchema#dateTime>"), "generatedAt truncated");
        assertTrue(out.contains("<https://example.org/lpg#database> \"plm\""));
    }

    @Test
    void writeIsTheStepsRunInOrder() {
        StringBuilder out = new StringBuilder();
        Snapshotter.steps(tx(), "plm", "MATCH ... RETURN ...", BASE, "s1", AT, new NQuadsSink(out))
                .forEach(Runnable::run);
        assertEquals(write(), out.toString());
        assertEquals(write(), write());
    }

    @Test
    void stepsEndWithFlushAndTheLastOneCloses() {
        List<String> events = new ArrayList<>();
        QuadSink sink = new QuadSink() {
            @Override
            public void quad(Term subject, Iri predicate, Term object, Iri graph) {
                events.add("q");
            }

            @Override
            public void flush() {
                events.add("flush");
            }

            @Override
            public void close() {
                events.add("close");
            }
        };
        List<Runnable> steps = Snapshotter.steps(tx(), "plm", "q", BASE, "s1", AT, sink).toList();
        // metadata, vocabulary, 4 nodes, 2 relationships, close
        assertEquals(9, steps.size());
        assertTrue(events.isEmpty(), "steps are lazy");
        for (int i = 0; i < steps.size() - 1; i++) {
            steps.get(i).run();
            assertEquals("flush", events.getLast());
        }
        steps.getLast().run();
        assertEquals("close", events.getLast());
    }

    @Test
    void validation() {
        QuadSink sink = new NQuadsSink(new StringBuilder());
        assertThrows(IllegalArgumentException.class, () -> Snapshotter.write(tx(), "plm", " ", BASE, "s1", AT, sink));
        assertThrows(IllegalArgumentException.class, () -> Snapshotter.write(tx(), "plm", "q", " ", "s1", AT, sink));
        assertThrows(IllegalArgumentException.class, () -> Snapshotter.write(tx(), "plm", "q", BASE, "", AT, sink));
        assertThrows(NullPointerException.class, () -> Snapshotter.write(tx(), "plm", "q", BASE, "s1", null, sink));
    }
}
