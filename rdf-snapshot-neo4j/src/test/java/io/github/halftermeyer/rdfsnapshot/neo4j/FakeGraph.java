package io.github.halftermeyer.rdfsnapshot.neo4j;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;

/** Just enough of the embedded API, through dynamic proxies, to run the snapshot code without a database. */
final class FakeGraph {
    private final Map<String, Node> nodes = new HashMap<>();
    private final Map<String, Relationship> rels = new HashMap<>();

    Node node(String id, List<String> labels, Map<String, Object> properties) {
        Node node = proxy(Node.class, (p, m, a) -> switch (m.getName()) {
            case "getElementId" -> id;
            case "getLabels" -> labels.stream().map(Label::label).toList();
            case "getAllProperties" -> properties;
            default -> object(p, m.getName(), a, id);
        });
        nodes.put(id, node);
        return node;
    }

    Relationship rel(String id, String type, Node start, Node end, Map<String, Object> properties) {
        Relationship rel = proxy(Relationship.class, (p, m, a) -> switch (m.getName()) {
            case "getElementId" -> id;
            case "getType" -> RelationshipType.withName(type);
            case "getStartNode" -> start;
            case "getEndNode" -> end;
            case "getAllProperties" -> properties;
            default -> object(p, m.getName(), a, id);
        });
        rels.put(id, rel);
        return rel;
    }

    static Path path(List<Node> pathNodes, List<Relationship> pathRels) {
        return proxy(Path.class, (p, m, a) -> switch (m.getName()) {
            case "nodes" -> pathNodes;
            case "relationships" -> pathRels;
            default -> object(p, m.getName(), a, "path");
        });
    }

    /** A transaction whose {@code execute} returns {@code rows}, whatever the query. */
    Transaction tx(List<Map<String, Object>> rows) {
        return proxy(Transaction.class, (p, m, a) -> switch (m.getName()) {
            case "execute" -> result(rows);
            case "getNodeByElementId" -> nodes.get((String) a[0]);
            case "getRelationshipByElementId" -> rels.get((String) a[0]);
            default -> object(p, m.getName(), a, "tx");
        });
    }

    private static Result result(List<Map<String, Object>> rows) {
        Iterator<Map<String, Object>> it = new ArrayList<>(rows).iterator();
        return proxy(Result.class, (p, m, a) -> switch (m.getName()) {
            case "hasNext" -> it.hasNext();
            case "next" -> it.next();
            case "close" -> null;
            default -> object(p, m.getName(), a, "result");
        });
    }

    private static Object object(Object proxy, String method, Object[] args, String name) {
        return switch (method) {
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> name;
            default -> throw new UnsupportedOperationException(method);
        };
    }

    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(FakeGraph.class.getClassLoader(), new Class<?>[] {type}, handler);
    }
}
