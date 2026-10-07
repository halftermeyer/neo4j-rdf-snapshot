package io.github.halftermeyer.rdfsnapshot.model;

/**
 * Marker for a Cypher {@code VECTOR} value. Vectors are not serialized (SPEC §7.4), so the
 * content is not kept: adapters only need to say that a property holds a vector.
 */
public enum LpgVector {
    INSTANCE
}
