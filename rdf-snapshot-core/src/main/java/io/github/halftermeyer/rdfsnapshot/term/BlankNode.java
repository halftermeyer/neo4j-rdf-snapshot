package io.github.halftermeyer.rdfsnapshot.term;

import java.util.Objects;

/**
 * A blank node with a deterministic label. The label is a valid N-Quads / TriG
 * {@code BLANK_NODE_LABEL} without the {@code _:} prefix.
 */
public record BlankNode(String label) implements Term {
    public BlankNode {
        Objects.requireNonNull(label, "label");
    }

    @Override
    public String toString() {
        return "_:" + label;
    }
}
