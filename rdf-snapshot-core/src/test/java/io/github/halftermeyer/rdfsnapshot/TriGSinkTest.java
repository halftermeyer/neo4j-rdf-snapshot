package io.github.halftermeyer.rdfsnapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.halftermeyer.rdfsnapshot.model.LpgDuration;
import io.github.halftermeyer.rdfsnapshot.model.LpgNode;
import io.github.halftermeyer.rdfsnapshot.model.LpgPoint;
import io.github.halftermeyer.rdfsnapshot.model.LpgRelationship;
import io.github.halftermeyer.rdfsnapshot.model.LpgVector;
import io.github.halftermeyer.rdfsnapshot.model.SnapshotMetadata;
import io.github.halftermeyer.rdfsnapshot.sink.NQuadsSink;
import io.github.halftermeyer.rdfsnapshot.sink.QuadSink;
import io.github.halftermeyer.rdfsnapshot.sink.TriGSink;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.util.IsoMatcher;
import org.junit.jupiter.api.Test;

class TriGSinkTest {
    private static final IriMinter IRIS = new IriMinter("http://acme.org/plm");
    private static final SnapshotMetadata META = new SnapshotMetadata(
            "20261005T140311Z", "plm",
            "MATCH (p:Part {partNumber: 'P-001'})-[r:USES]->(m) RETURN p, r, m",
            Instant.parse("2026-10-05T14:03:11Z"));

    private static final List<LpgNode> NODES = List.of(
            new LpgNode("4:a1b2:12", List.of("Part"),
                    Map.of("partNumber", "P-001", "mass", 1.2, "tags", List.of("steel", "EU", "steel"))),
            new LpgNode("4:a1b2:57", List.of("Substance", "Material"), Map.of("casNumber", "335-67-1")));
    private static final List<LpgRelationship> RELS = List.of(
            new LpgRelationship("5:a1b2:3", "USES", "4:a1b2:12", "4:a1b2:57", Map.of("quantity", 4L)));

    private static String write(Function<StringBuilder, QuadSink> sink, List<LpgNode> nodes, List<LpgRelationship> rels) {
        StringBuilder out = new StringBuilder();
        new SnapshotSerializer(IRIS, sink.apply(out)).write(META, nodes, rels);
        return out.toString();
    }

    private static String trig(List<LpgNode> nodes, List<LpgRelationship> rels) {
        return write(out -> new TriGSink(out, IRIS), nodes, rels);
    }

    private static String nquads(List<LpgNode> nodes, List<LpgRelationship> rels) {
        return write(NQuadsSink::new, nodes, rels);
    }

    private static DatasetGraph parse(String text, Lang lang) {
        DatasetGraph dsg = DatasetGraphFactory.create();
        RDFParser.create().source(new StringReader(text)).lang(lang).strict(true).parse(dsg);
        return dsg;
    }

    @Test
    void specExampleLayout() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/spec-example.trig")) {
            assertEquals(new String(in.readAllBytes(), StandardCharsets.UTF_8), trig(NODES, RELS));
        }
    }

    @Test
    void trigAndNQuadsAreIsomorphic() {
        assertTrue(IsoMatcher.isomorphic(parse(trig(NODES, RELS), Lang.TRIG), parse(nquads(NODES, RELS), Lang.NQUADS)));
    }

    @Test
    void everyValueTypeAndAwkwardNames() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("string", "line1\nline2 \"quoted\" \\ \t é 🔩");
        props.put("integer", -42L);
        props.put("double", 0.1);
        props.put("nan", Double.NaN);
        props.put("inf", Double.NEGATIVE_INFINITY);
        props.put("boolean", false);
        props.put("date", LocalDate.of(-44, 3, 15));
        props.put("localTime", LocalTime.of(10, 15));
        props.put("zonedTime", OffsetTime.of(8, 0, 0, 0, ZoneOffset.ofHours(-5)));
        props.put("zonedDateTime", ZonedDateTime.of(2026, 10, 5, 14, 3, 11, 0, ZoneId.of("Europe/Paris")));
        props.put("duration", new LpgDuration(14, 3, 3661, 5));
        props.put("wgs", new LpgPoint(LpgPoint.Crs.WGS84_2D, new double[] {2.35, 48.85}));
        props.put("cart3", new LpgPoint(LpgPoint.Crs.CARTESIAN_3D, new double[] {1, 2, 3}));
        props.put("bytes", new byte[] {1, 2, 3});
        props.put("dates", List.of(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
        props.put("empty", List.of());
        props.put("vector", LpgVector.INSTANCE);
        props.put(".hidden", "starts with a dot");
        props.put("tilde~key", "~ is not allowed in a prefixed name");
        props.put("part number", "space");
        props.put("部品", "non-ASCII");
        List<LpgNode> nodes = List.of(new LpgNode("4:db:0", List.of("a/b#c", "Pièce"), props));
        List<LpgRelationship> rels = List.of(new LpgRelationship("5:db:0", "USES / CONTAINS", "4:db:0", "4:db:0", Map.of()));

        String trig = trig(nodes, rels);
        assertTrue(IsoMatcher.isomorphic(parse(trig, Lang.TRIG), parse(nquads(nodes, rels), Lang.NQUADS)), trig);
        assertTrue(trig.contains("<http://acme.org/plm/prop/.hidden> \"starts with a dot\""), trig);
        assertTrue(trig.contains("<http://acme.org/plm/prop/tilde~key>"), trig);
        assertTrue(trig.contains("p:part%20number \"space\""), trig);
        assertTrue(trig.contains("p:nan \"NaN\"^^xsd:double"), trig);
        assertTrue(trig.contains("p:inf \"-INF\"^^xsd:double"), trig);
        assertTrue(trig.contains("p:double 1.0E-1"), trig);
        assertTrue(trig.contains("p:dates ( \"2026-01-01\"^^xsd:date \"2026-12-31\"^^xsd:date )"), trig);
        assertTrue(trig.contains("p:wgs \"<http://www.opengis.net/def/crs/OGC/1.3/CRS84> POINT(2.35 48.85)\"^^geo:wktLiteral"), trig);
        assertTrue(trig.contains("p:empty ()"), trig);
    }

    @Test
    void deterministic() {
        assertEquals(trig(NODES, RELS), trig(List.of(NODES.get(1), NODES.get(0)), RELS));
    }

    @Test
    void flushWritesCompleteBlocks() {
        StringBuilder out = new StringBuilder();
        SnapshotSerializer serializer = new SnapshotSerializer(IRIS, new TriGSink(out, IRIS));
        serializer.writeMetadata(META);
        assertTrue(out.toString().endsWith("^^xsd:dateTime .\n"), out.toString());
        serializer.writeNode(IRIS.snapshot("x"), NODES.get(0));
        assertTrue(out.toString().endsWith("p:tags ( \"steel\" \"EU\" \"steel\" ) .\n"), out.toString());
    }
}
