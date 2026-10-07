package io.github.halftermeyer.rdfsnapshot.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A node record.
 *
 * @param properties property values, see {@link io.github.halftermeyer.rdfsnapshot.ValueMapper}
 *                   for the accepted Java types
 */
public record LpgNode(String elementId, List<String> labels, Map<String, Object> properties) {
    public LpgNode {
        Objects.requireNonNull(elementId, "elementId");
        labels = List.copyOf(labels);
        properties = Map.copyOf(properties);
    }
}
