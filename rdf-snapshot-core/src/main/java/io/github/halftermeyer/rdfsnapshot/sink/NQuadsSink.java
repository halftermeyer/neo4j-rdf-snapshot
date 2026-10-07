package io.github.halftermeyer.rdfsnapshot.sink;

import io.github.halftermeyer.rdfsnapshot.term.BlankNode;
import io.github.halftermeyer.rdfsnapshot.term.Iri;
import io.github.halftermeyer.rdfsnapshot.term.Literal;
import io.github.halftermeyer.rdfsnapshot.term.Term;
import io.github.halftermeyer.rdfsnapshot.term.Vocab;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Writes N-Quads, one quad per line, in emission order. {@code xsd:string} literals are written
 * without datatype, as in canonical N-Triples.
 */
public final class NQuadsSink implements QuadSink {
    private final Appendable out;
    private final StringBuilder line = new StringBuilder(256);

    public NQuadsSink(Appendable out) {
        this.out = out;
    }

    @Override
    public void quad(Term subject, Iri predicate, Term object, Iri graph) {
        line.setLength(0);
        term(subject);
        line.append(' ');
        term(predicate);
        line.append(' ');
        term(object);
        if (graph != null) {
            line.append(' ');
            term(graph);
        }
        line.append(" .\n");
        try {
            out.append(line);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void term(Term term) {
        switch (term) {
            case Iri iri -> {
                line.append('<');
                TextEscaping.appendIri(line, iri.value());
                line.append('>');
            }
            case BlankNode bnode -> line.append("_:").append(bnode.label());
            case Literal literal -> {
                line.append('"');
                TextEscaping.appendString(line, literal.lexicalForm());
                line.append('"');
                if (!literal.datatype().equals(Vocab.XSD_STRING)) {
                    line.append("^^");
                    term(literal.datatype());
                }
            }
        }
    }
}
