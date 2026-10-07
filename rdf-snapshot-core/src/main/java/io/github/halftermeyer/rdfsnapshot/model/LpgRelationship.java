package io.github.halftermeyer.rdfsnapshot.model;

import java.util.Map;
import java.util.Objects;

/**
 * A relationship record.
 *
 * @param properties property values, see {@link io.github.halftermeyer.rdfsnapshot.ValueMapper}
 *                   for the accepted Java types
 */
public record LpgRelationship(
        String elementId,
        String type,
        String sourceElementId,
        String targetElementId,
        Map<String, Object> properties) {
    public LpgRelationship {
        Objects.requireNonNull(elementId, "elementId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(sourceElementId, "sourceElementId");
        Objects.requireNonNull(targetElementId, "targetElementId");
        properties = Map.copyOf(properties);
    }
}
