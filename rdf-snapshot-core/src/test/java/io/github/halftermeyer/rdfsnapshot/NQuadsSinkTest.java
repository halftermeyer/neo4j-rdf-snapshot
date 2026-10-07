package io.github.halftermeyer.rdfsnapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.halftermeyer.rdfsnapshot.sink.NQuadsSink;
import io.github.halftermeyer.rdfsnapshot.term.BlankNode;
import io.github.halftermeyer.rdfsnapshot.term.Iri;
import io.github.halftermeyer.rdfsnapshot.term.Literal;
import io.github.halftermeyer.rdfsnapshot.term.Vocab;
import org.junit.jupiter.api.Test;

class NQuadsSinkTest {
    private static final Iri S = new Iri("http://x/s");
    private static final Iri P = new Iri("http://x/p");
    private static final Iri G = new Iri("http://x/g");

    private static String write(io.github.halftermeyer.rdfsnapshot.term.Term object, Iri graph) {
        StringBuilder out = new StringBuilder();
        new NQuadsSink(out).quad(S, P, object, graph);
        return out.toString();
    }

    @Test
    void defaultAndNamedGraph() {
        assertEquals("<http://x/s> <http://x/p> <http://x/o> .\n", write(new Iri("http://x/o"), null));
        assertEquals("<http://x/s> <http://x/p> <http://x/o> <http://x/g> .\n", write(new Iri("http://x/o"), G));
    }

    @Test
    void stringsArePlainOtherLiteralsTyped() {
        assertEquals("<http://x/s> <http://x/p> \"a\" .\n", write(Literal.string("a"), null));
        assertEquals("<http://x/s> <http://x/p> \"4\"^^<http://www.w3.org/2001/XMLSchema#integer> .\n",
                write(new Literal("4", Vocab.XSD_INTEGER), null));
    }

    @Test
    void blankNodes() {
        assertEquals("<http://x/s> <http://x/p> _:b0 <http://x/g> .\n", write(new BlankNode("b0"), G));
    }

    @Test
    void literalEscaping() {
        String nasty = "q\"b\\n\nr\rt\tb\bf\f\u0000\u001F\u007Fé🔩";
        assertEquals("<http://x/s> <http://x/p> \"q\\\"b\\\\n\\nr\\rt\\tb\\bf\\f\\u0000\\u001F\\u007Fé🔩\" .\n",
                write(Literal.string(nasty), null));
    }

    @Test
    void iriEscapingForUnsafeBase() {
        assertEquals("<http://x/s> <http://x/p> <http://x/a\\u0020b\\u003E> .\n", write(new Iri("http://x/a b>"), null));
    }
}
