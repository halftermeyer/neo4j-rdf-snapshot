package io.github.halftermeyer.rdfsnapshot;

import io.github.halftermeyer.rdfsnapshot.model.LpgNode;
import io.github.halftermeyer.rdfsnapshot.model.LpgRelationship;
import java.util.Collections;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * The graph vocabulary of a snapshot (SPEC §7.1): labels, relationship types and property keys
 * used in scope, sorted by name. A property key is only included if some in-scope value for it
 * is serialized (a key holding only vectors is left out).
 */
public final class Vocabulary {
    private final SortedSet<String> labels = new TreeSet<>();
    private final SortedSet<String> types = new TreeSet<>();
    private final SortedSet<String> propertyKeys = new TreeSet<>();

    public Vocabulary addNode(LpgNode node) {
        labels.addAll(node.labels());
        addKeys(node.properties());
        return this;
    }

    public Vocabulary addRelationship(LpgRelationship rel) {
        types.add(rel.type());
        addKeys(rel.properties());
        return this;
    }

    private void addKeys(Map<String, Object> properties) {
        properties.forEach((key, value) -> {
            if (!propertyKeys.contains(key) && ValueMapper.isSerialized(value)) {
                propertyKeys.add(key);
            }
        });
    }

    public SortedSet<String> labels() {
        return Collections.unmodifiableSortedSet(labels);
    }

    public SortedSet<String> types() {
        return Collections.unmodifiableSortedSet(types);
    }

    public SortedSet<String> propertyKeys() {
        return Collections.unmodifiableSortedSet(propertyKeys);
    }
}
