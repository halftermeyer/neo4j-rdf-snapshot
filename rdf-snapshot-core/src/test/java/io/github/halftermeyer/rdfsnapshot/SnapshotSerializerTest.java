package io.github.halftermeyer.rdfsnapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.halftermeyer.rdfsnapshot.model.LpgDuration;
import io.github.halftermeyer.rdfsnapshot.model.LpgNode;
import io.github.halftermeyer.rdfsnapshot.model.LpgPoint;
import io.github.halftermeyer.rdfsnapshot.model.LpgRelationship;
import io.github.halftermeyer.rdfsnapshot.model.LpgVector;
import io.github.halftermeyer.rdfsnapshot.model.SnapshotMetadata;
import io.github.halftermeyer.rdfsnapshot.sink.NQuadsSink;
import java.io.StringReader;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.apache.jena.graph.Node;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.junit.jupiter.api.Test;

class SnapshotSerializerTest {
    private static final IriMinter IRIS = new IriMinter("http://acme.org/plm");
    private static final SnapshotMetadata META = new SnapshotMetadata(
            "20261005T140311Z", "plm",
            "MATCH (p:Part {partNumber: 'P-001'})-[r:USES]->(m) RETURN p, r, m",
            Instant.parse("2026-10-05T14:03:11Z"));

    private static final LpgNode PART = new LpgNode("4:a1b2:12", List.of("Part"), Map.of(
            "partNumber", "P-001", "mass", 1.2, "tags", List.of("steel", "EU", "steel")));
    private static final LpgNode SUBSTANCE = new LpgNode("4:a1b2:57", List.of("Substance", "Material"),
            Map.of("casNumber", "335-67-1"));
    private static final LpgRelationship USES = new LpgRelationship("5:a1b2:3", "USES", "4:a1b2:12", "4:a1b2:57",
            Map.of("quantity", 4L));

    private static String nquads(List<LpgNode> nodes, List<LpgRelationship> rels) {
        StringBuilder out = new StringBuilder();
        new SnapshotSerializer(IRIS, new NQuadsSink(out)).write(META, nodes, rels);
        return out.toString();
    }

    @Test
    void specExampleDataGraph() {
        String g = " <http://acme.org/plm/snapshot/20261005T140311Z> .";
        String lpg = "<https://example.org/lpg#";
        String rdf = "<http://www.w3.org/1999/02/22-rdf-syntax-ns#";
        String tagsHead = BlankNodes.of("http://acme.org/plm/snapshot/20261005T140311Z",
                IRIS.element("4:a1b2:12"), "http://acme.org/plm/prop/tags", 0).toString();
        String tags1 = BlankNodes.of("http://acme.org/plm/snapshot/20261005T140311Z",
                IRIS.element("4:a1b2:12"), "http://acme.org/plm/prop/tags", 1).toString();
        String tags2 = BlankNodes.of("http://acme.org/plm/snapshot/20261005T140311Z",
                IRIS.element("4:a1b2:12"), "http://acme.org/plm/prop/tags", 2).toString();
        String expected = String.join("\n",
                "<http://acme.org/plm/snapshot/20261005T140311Z> " + rdf + "type> " + lpg + "Snapshot> .",
                "<http://acme.org/plm/snapshot/20261005T140311Z> " + lpg + "database> \"plm\" .",
                "<http://acme.org/plm/snapshot/20261005T140311Z> " + lpg + "scope> \"MATCH (p:Part {partNumber: 'P-001'})-[r:USES]->(m) RETURN p, r, m\" .",
                "<http://acme.org/plm/snapshot/20261005T140311Z> <http://www.w3.org/ns/prov#generatedAtTime> \"2026-10-05T14:03:11Z\"^^<http://www.w3.org/2001/XMLSchema#dateTime> .",
                "<http://acme.org/plm/label/Material> " + rdf + "type> " + lpg + "Label>" + g,
                "<http://acme.org/plm/label/Material> " + lpg + "name> \"Material\"" + g,
                "<http://acme.org/plm/label/Part> " + rdf + "type> " + lpg + "Label>" + g,
                "<http://acme.org/plm/label/Part> " + lpg + "name> \"Part\"" + g,
                "<http://acme.org/plm/label/Substance> " + rdf + "type> " + lpg + "Label>" + g,
                "<http://acme.org/plm/label/Substance> " + lpg + "name> \"Substance\"" + g,
                "<http://acme.org/plm/type/USES> " + rdf + "type> " + lpg + "RelationshipType>" + g,
                "<http://acme.org/plm/type/USES> " + lpg + "name> \"USES\"" + g,
                "<http://acme.org/plm/prop/casNumber> " + rdf + "type> " + lpg + "PropertyKey>" + g,
                "<http://acme.org/plm/prop/casNumber> " + lpg + "name> \"casNumber\"" + g,
                "<http://acme.org/plm/prop/mass> " + rdf + "type> " + lpg + "PropertyKey>" + g,
                "<http://acme.org/plm/prop/mass> " + lpg + "name> \"mass\"" + g,
                "<http://acme.org/plm/prop/partNumber> " + rdf + "type> " + lpg + "PropertyKey>" + g,
                "<http://acme.org/plm/prop/partNumber> " + lpg + "name> \"partNumber\"" + g,
                "<http://acme.org/plm/prop/quantity> " + rdf + "type> " + lpg + "PropertyKey>" + g,
                "<http://acme.org/plm/prop/quantity> " + lpg + "name> \"quantity\"" + g,
                "<http://acme.org/plm/prop/tags> " + rdf + "type> " + lpg + "PropertyKey>" + g,
                "<http://acme.org/plm/prop/tags> " + lpg + "name> \"tags\"" + g,
                "<http://acme.org/plm/e/4:a1b2:12> " + rdf + "type> " + lpg + "Node>" + g,
                "<http://acme.org/plm/e/4:a1b2:12> " + lpg + "label> <http://acme.org/plm/label/Part>" + g,
                "<http://acme.org/plm/e/4:a1b2:12> " + lpg + "elementId> \"4:a1b2:12\"" + g,
                "<http://acme.org/plm/e/4:a1b2:12> <http://acme.org/plm/prop/mass> \"1.2E0\"^^<http://www.w3.org/2001/XMLSchema#double>" + g,
                "<http://acme.org/plm/e/4:a1b2:12> <http://acme.org/plm/prop/partNumber> \"P-001\"" + g,
                "<http://acme.org/plm/e/4:a1b2:12> <http://acme.org/plm/prop/tags> " + tagsHead + g,
                tagsHead + " " + rdf + "first> \"steel\"" + g,
                tagsHead + " " + rdf + "rest> " + tags1 + g,
                tags1 + " " + rdf + "first> \"EU\"" + g,
                tags1 + " " + rdf + "rest> " + tags2 + g,
                tags2 + " " + rdf + "first> \"steel\"" + g,
                tags2 + " " + rdf + "rest> " + rdf + "nil>" + g,
                "<http://acme.org/plm/e/4:a1b2:57> " + rdf + "type> " + lpg + "Node>" + g,
                "<http://acme.org/plm/e/4:a1b2:57> " + lpg + "label> <http://acme.org/plm/label/Material>" + g,
                "<http://acme.org/plm/e/4:a1b2:57> " + lpg + "label> <http://acme.org/plm/label/Substance>" + g,
                "<http://acme.org/plm/e/4:a1b2:57> " + lpg + "elementId> \"4:a1b2:57\"" + g,
                "<http://acme.org/plm/e/4:a1b2:57> <http://acme.org/plm/prop/casNumber> \"335-67-1\"" + g,
                "<http://acme.org/plm/e/5:a1b2:3> " + rdf + "type> " + lpg + "Relationship>" + g,
                "<http://acme.org/plm/e/5:a1b2:3> " + lpg + "type> <http://acme.org/plm/type/USES>" + g,
                "<http://acme.org/plm/e/5:a1b2:3> " + lpg + "elementId> \"5:a1b2:3\"" + g,
                "<http://acme.org/plm/e/5:a1b2:3> " + lpg + "source> <http://acme.org/plm/e/4:a1b2:12>" + g,
                "<http://acme.org/plm/e/5:a1b2:3> " + lpg + "target> <http://acme.org/plm/e/4:a1b2:57>" + g,
                "<http://acme.org/plm/e/5:a1b2:3> <http://acme.org/plm/prop/quantity> \"4\"^^<http://www.w3.org/2001/XMLSchema#integer>" + g,
                "");
        assertEquals(expected, nquads(List.of(PART, SUBSTANCE), List.of(USES)));
    }

    @Test
    void deterministicRegardlessOfInputOrder() {
        List<LpgNode> nodes = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("z" + i, (long) i);
            props.put("a", List.of("x", "y"));
            props.put("m", "v" + i);
            nodes.add(new LpgNode("4:db:" + i, List.of("L" + (i % 7), "K" + (i % 3)), props));
        }
        List<LpgRelationship> rels = new ArrayList<>();
        for (int i = 0; i < 49; i++) {
            rels.add(new LpgRelationship("5:db:" + i, "R" + (i % 4), "4:db:" + i, "4:db:" + (i + 1),
                    Map.of("w", (double) i, "tags", List.of())));
        }
        String reference = nquads(nodes, rels);
        Random random = new Random(42);
        for (int run = 0; run < 5; run++) {
            List<LpgNode> n = new ArrayList<>(nodes);
            List<LpgRelationship> r = new ArrayList<>(rels);
            Collections.shuffle(n, random);
            Collections.shuffle(r, random);
            assertEquals(reference, nquads(n, r));
        }
    }

    @Test
    void duplicateElementsAreWrittenOnce() {
        assertEquals(nquads(List.of(PART, SUBSTANCE), List.of(USES)),
                nquads(List.of(PART, SUBSTANCE, PART), List.of(USES, USES)));
    }

    @Test
    void emptyListIsRdfNil() {
        String out = nquads(List.of(new LpgNode("1", List.of(), Map.of("tags", List.of()))), List.of());
        assertTrue(out.contains("<http://acme.org/plm/e/1> <http://acme.org/plm/prop/tags> "
                + "<http://www.w3.org/1999/02/22-rdf-syntax-ns#nil> "));
    }

    @Test
    void vectorPropertyAndItsKeyDoNotAppear() {
        String out = nquads(List.of(new LpgNode("1", List.of("A"), Map.of("embedding", LpgVector.INSTANCE, "n", 1L))),
                List.of());
        assertFalse(out.contains("embedding"));
        assertTrue(out.contains("/prop/n>"));
    }

    @Test
    void everyValueTypeParsesAndIsWellTyped() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("string", "line1\nline2 \"quoted\" \\ é 🔩");
        props.put("integer", Long.MAX_VALUE);
        props.put("float", -1.5e-300);
        props.put("nan", Double.NaN);
        props.put("boolean", true);
        props.put("date", LocalDate.of(-44, 3, 15));
        props.put("localTime", LocalTime.of(23, 59, 59, 999_999_999));
        props.put("zonedTime", OffsetTime.of(8, 0, 0, 0, ZoneOffset.ofHours(-5)));
        props.put("localDateTime", LocalDateTime.of(2026, 10, 5, 14, 3, 11));
        props.put("zonedDateTime", ZonedDateTime.of(2026, 10, 5, 14, 3, 11, 0, ZoneId.of("Europe/Paris")));
        props.put("duration", new LpgDuration(14, 3, 3661, 5));
        props.put("negDuration", new LpgDuration(0, 0, -1, 500_000_000));
        props.put("wgs", new LpgPoint(LpgPoint.Crs.WGS84_2D, new double[] {2.35, 48.85}));
        props.put("wgs3", new LpgPoint(LpgPoint.Crs.WGS84_3D, new double[] {2.35, 48.85, 35}));
        props.put("cart", new LpgPoint(LpgPoint.Crs.CARTESIAN_2D, new double[] {1, 2}));
        props.put("cart3", new LpgPoint(LpgPoint.Crs.CARTESIAN_3D, new double[] {1, 2, 3}));
        props.put("bytes", new byte[] {1, 2, 3});
        props.put("uuid", java.util.UUID.fromString("3f2504e0-4f89-11d3-9a0c-0305e82c3301"));
        props.put("list", List.of(1L, 2L, 1L));
        props.put("dateList", List.of(LocalDate.of(2026, 1, 1)));
        props.put("empty", List.of());
        props.put("vector", LpgVector.INSTANCE);
        LpgNode node = new LpgNode("4:db:0", List.of("All Types", "a/b#c", "Pièce"), props);

        String out = nquads(List.of(node), List.of());
        DatasetGraph dsg = DatasetGraphFactory.create();
        RDFParser.create().source(new StringReader(out)).lang(Lang.NQUADS).strict(true).parse(dsg);

        // 4 metadata + 3 labels * 2 + 21 keys * 2 + node (type, 3 labels, elementId, 21 properties) + 4 list cells * 2
        assertEquals(4 + 6 + 42 + 26 + 8, dsg.stream().count());
        dsg.stream().map(q -> q.getObject()).filter(Node::isLiteral).forEach(o -> {
            String dt = o.getLiteralDatatypeURI();
            if (!dt.startsWith("http://www.opengis.net/") && !dt.startsWith(io.github.halftermeyer.rdfsnapshot.term.Vocab.LPG)) {
                assertTrue(o.getLiteral().isWellFormed(), "ill-formed literal: " + o);
            }
        });
    }
}
