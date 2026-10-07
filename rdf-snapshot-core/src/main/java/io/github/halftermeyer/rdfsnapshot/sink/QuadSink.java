package io.github.halftermeyer.rdfsnapshot.sink;

import io.github.halftermeyer.rdfsnapshot.term.Iri;
import io.github.halftermeyer.rdfsnapshot.term.Term;

/**
 * Receives the quads of a snapshot. The serializer only ever talks to a sink.
 *
 * <p>Emission contract, which pretty-printing sinks may rely on:
 * <ul>
 *   <li>quads arrive grouped by graph, and a graph is never reopened once another one started;
 *       the default graph comes first;</li>
 *   <li>within a graph, quads arrive grouped by subject; the blank nodes describing a subject's
 *       objects (list cells, property specs, constraints) are emitted right after that subject,
 *       before the next IRI subject, and each is referenced exactly once.</li>
 * </ul>
 */
public interface QuadSink {

    /**
     * @param graph the graph name, or {@code null} for the default graph
     */
    void quad(Term subject, Iri predicate, Term object, Iri graph);

    /** Called after each complete subject block. Buffered output may be written out. */
    default void flush() {}

    /** Called once, after the last quad. */
    default void close() {}
}
