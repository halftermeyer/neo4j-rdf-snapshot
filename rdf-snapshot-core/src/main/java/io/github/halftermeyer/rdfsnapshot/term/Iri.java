package io.github.halftermeyer.rdfsnapshot.term;

import java.util.Objects;

/** An absolute IRI. The value is not validated: minted IRIs are already percent-encoded. */
public record Iri(String value) implements Term {
    public Iri {
        Objects.requireNonNull(value, "value");
    }

    @Override
    public String toString() {
        return "<" + value + ">";
    }
}
