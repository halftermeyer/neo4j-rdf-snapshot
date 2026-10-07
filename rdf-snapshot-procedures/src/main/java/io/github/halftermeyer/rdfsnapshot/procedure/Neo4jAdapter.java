package io.github.halftermeyer.rdfsnapshot.procedure;

import io.github.halftermeyer.rdfsnapshot.model.LpgDuration;
import io.github.halftermeyer.rdfsnapshot.model.LpgNode;
import io.github.halftermeyer.rdfsnapshot.model.LpgPoint;
import io.github.halftermeyer.rdfsnapshot.model.LpgRelationship;
import io.github.halftermeyer.rdfsnapshot.model.LpgVector;
import java.lang.reflect.Array;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Vector;
import org.neo4j.graphdb.spatial.Point;

/** Converts Neo4j elements and property values into the core input model. */
public final class Neo4jAdapter {
    private Neo4jAdapter() {}

    public static LpgNode node(Node node) {
        List<String> labels = new ArrayList<>();
        for (Label label : node.getLabels()) {
            labels.add(label.name());
        }
        return new LpgNode(node.getElementId(), labels, properties(node.getAllProperties()));
    }

    public static LpgRelationship relationship(Relationship rel) {
        return new LpgRelationship(
                rel.getElementId(),
                rel.getType().name(),
                rel.getStartNode().getElementId(),
                rel.getEndNode().getElementId(),
                properties(rel.getAllProperties()));
    }

    private static Map<String, Object> properties(Map<String, Object> raw) {
        Map<String, Object> converted = new TreeMap<>();
        raw.forEach((key, value) -> converted.put(key, value(value)));
        return converted;
    }

    /**
     * Property values as returned by the embedded API: scalars, temporal types, {@code DurationValue}
     * (a {@link TemporalAmount}), {@link Point}, {@link Vector}, and Java arrays for lists
     * ({@code byte[]} is a byte array, not a list).
     */
    public static Object value(Object value) {
        return switch (value) {
            case byte[] bytes -> bytes;
            case Character c -> c.toString();
            case Vector v -> LpgVector.INSTANCE;
            case Point p -> new LpgPoint(
                    LpgPoint.Crs.fromSrid(p.getCRS().getCode()), p.getCoordinate().getCoordinate());
            case TemporalAmount d -> new LpgDuration(
                    d.get(ChronoUnit.MONTHS), d.get(ChronoUnit.DAYS), d.get(ChronoUnit.SECONDS), d.get(ChronoUnit.NANOS));
            case Object array when array.getClass().isArray() -> {
                int length = Array.getLength(array);
                List<Object> list = new ArrayList<>(length);
                for (int i = 0; i < length; i++) {
                    list.add(value(Array.get(array, i)));
                }
                yield list;
            }
            default -> value;
        };
    }
}
