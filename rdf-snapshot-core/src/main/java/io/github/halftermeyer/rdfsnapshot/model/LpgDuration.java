package io.github.halftermeyer.rdfsnapshot.model;

/**
 * A Cypher {@code DURATION}: months, days, seconds and nanoseconds, each component with its own
 * sign, as Neo4j stores it.
 */
public record LpgDuration(long months, long days, long seconds, long nanos) {}
