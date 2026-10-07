package io.github.halftermeyer.rdfsnapshot.term;

/** An RDF term: IRI, blank node or literal. */
public sealed interface Term permits Iri, BlankNode, Literal {}
