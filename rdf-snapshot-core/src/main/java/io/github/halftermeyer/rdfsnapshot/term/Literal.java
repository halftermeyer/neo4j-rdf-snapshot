package io.github.halftermeyer.rdfsnapshot.term;

import java.util.Objects;

/** A typed literal. Plain strings use {@code xsd:string}. */
public record Literal(String lexicalForm, Iri datatype) implements Term {
    public Literal {
        Objects.requireNonNull(lexicalForm, "lexicalForm");
        Objects.requireNonNull(datatype, "datatype");
    }

    public static Literal string(String value) {
        return new Literal(value, Vocab.XSD_STRING);
    }

    @Override
    public String toString() {
        return "\"" + lexicalForm + "\"^^" + datatype;
    }
}
