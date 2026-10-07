package io.github.halftermeyer.rdfsnapshot.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.core.Quad;

/**
 * Makes an output comparable to {@code expected.trig}.
 *
 * <p>Element IDs are assigned by the database, so element IRIs ({@code {base}/e/...}) become blank
 * nodes, after checking that each one matches its {@code lpg:elementId}, and the
 * {@code lpg:elementId} literals become a constant. {@code prov:generatedAtTime} is checked to be
 * an {@code xsd:dateTime} and replaced by a constant. Everything else is compared as is.
 */
final class Normalizer {
    static final String LPG = "https://example.org/lpg#";
    static final Node ELEMENT_ID = NodeFactory.createURI(LPG + "elementId");
    static final Node GENERATED_AT = NodeFactory.createURI("http://www.w3.org/ns/prov#generatedAtTime");
    static final Node ANY_ELEMENT_ID = NodeFactory.createLiteralString("ELEMENT_ID");
    static final Node ANY_TIME = NodeFactory.createLiteralDT("2000-01-01T00:00:00Z", XSDDatatype.XSDdateTime);

    private Normalizer() {}

    static DatasetGraph normalize(DatasetGraph in, String base) {
        String elementNs = base + "/e/";
        Map<String, Node> blank = new HashMap<>();
        DatasetGraph out = DatasetGraphFactory.create();
        in.find().forEachRemaining(q -> {
            Node s = q.getSubject();
            Node o = q.getObject();
            if (q.getPredicate().equals(ELEMENT_ID)) {
                assertTrue(o.isLiteral(), "lpg:elementId must be a literal: " + q);
                assertEquals(elementNs + encodeSegment(o.getLiteralLexicalForm()), s.getURI(),
                        "element IRI does not match its lpg:elementId");
                o = ANY_ELEMENT_ID;
            } else if (q.getPredicate().equals(GENERATED_AT)) {
                assertEquals(XSDDatatype.XSDdateTime.getURI(), o.getLiteralDatatypeURI());
                assertTrue(o.getLiteral().isWellFormed(), "ill-formed prov:generatedAtTime: " + o);
                o = ANY_TIME;
            }
            out.add(Quad.create(element(q.getGraph(), elementNs, blank), element(s, elementNs, blank),
                    q.getPredicate(), element(o, elementNs, blank)));
        });
        return out;
    }

    private static Node element(Node n, String elementNs, Map<String, Node> blank) {
        if (n.isURI() && n.getURI().startsWith(elementNs)) {
            return blank.computeIfAbsent(n.getURI(), k -> NodeFactory.createBlankNode());
        }
        return n;
    }

    /**
     * SPEC §5 encoding, written independently of the implementation under test: UTF-8 bytes
     * outside {@code A-Z a-z 0-9 - . _ ~ :} become {@code %XX}.
     */
    static String encodeSegment(String s) {
        StringBuilder out = new StringBuilder();
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xFF);
            if (Character.isLetterOrDigit(c) && c < 0x80 || "-._~:".indexOf(c) >= 0) {
                out.append(c);
            } else {
                out.append(String.format("%%%02X", b & 0xFF));
            }
        }
        return out.toString();
    }
}
