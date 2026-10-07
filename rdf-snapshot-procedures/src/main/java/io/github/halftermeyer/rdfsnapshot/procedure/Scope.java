package io.github.halftermeyer.rdfsnapshot.procedure;

import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;

/**
 * The elements in scope (SPEC §3): every node and relationship in any column, recursively inside
 * lists, map values and paths, plus the endpoints of every in-scope relationship. Element IDs are
 * kept sorted.
 */
public final class Scope {
    public final SortedSet<String> nodeIds = new TreeSet<>();
    public final SortedSet<String> relationshipIds = new TreeSet<>();

    public static Scope collect(Transaction tx, String scopeQuery) {
        Scope scope = new Scope();
        try (Result result = tx.execute(scopeQuery)) {
            while (result.hasNext()) {
                for (Object value : result.next().values()) {
                    scope.add(value);
                }
            }
        }
        return scope;
    }

    public void add(Object value) {
        switch (value) {
            case Node node -> nodeIds.add(node.getElementId());
            case Relationship rel -> {
                relationshipIds.add(rel.getElementId());
                // closure rule
                nodeIds.add(rel.getStartNode().getElementId());
                nodeIds.add(rel.getEndNode().getElementId());
            }
            case Path path -> {
                path.nodes().forEach(this::add);
                path.relationships().forEach(this::add);
            }
            case Iterable<?> list -> list.forEach(this::add);
            case Map<?, ?> map -> map.values().forEach(this::add);
            case null, default -> {
                // other values are ignored (SPEC §3)
            }
        }
    }
}
