package io.github.halftermeyer.rdfsnapshot.sink;

import io.github.halftermeyer.rdfsnapshot.IriMinter;
import io.github.halftermeyer.rdfsnapshot.term.BlankNode;
import io.github.halftermeyer.rdfsnapshot.term.Iri;
import io.github.halftermeyer.rdfsnapshot.term.Literal;
import io.github.halftermeyer.rdfsnapshot.term.Term;
import io.github.halftermeyer.rdfsnapshot.term.Vocab;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Writes pretty TriG, laid out as in SPEC §10: prefixes, then the default graph at top level,
 * then each named graph in a block; statements grouped by subject, objects of the same predicate
 * joined with {@code ,}, {@code rdf:type} as {@code a}, RDF collections inline as {@code ( ... )}.
 *
 * <p>Relies on the emission contract of {@link QuadSink}: it keeps one subject block in memory
 * (an IRI subject and the blank nodes emitted right after it) and writes it when the next IRI
 * subject, a new graph, {@link #flush()} or {@link #close()} comes.
 */
public final class TriGSink implements QuadSink {
    private static final Pattern PN_LOCAL =
            Pattern.compile("([A-Za-z0-9_:]|%[0-9A-Fa-f]{2})(([A-Za-z0-9_:.\\-]|%[0-9A-Fa-f]{2})*([A-Za-z0-9_:\\-]|%[0-9A-Fa-f]{2}))?");
    private static final Pattern INTEGER = Pattern.compile("[+-]?[0-9]+");
    private static final Pattern DOUBLE =
            Pattern.compile("[+-]?([0-9]+\\.[0-9]*|\\.[0-9]+|[0-9]+)[eE][+-]?[0-9]+");

    private final Appendable out;
    /** prefix → namespace, in header order */
    private final Map<String, String> prefixes = new LinkedHashMap<>();

    private boolean started;
    private boolean inGraph;
    private Iri graph;

    /** The pending subject block: subject → predicate → objects, in emission order. */
    private final Map<Term, Map<Iri, List<Term>>> block = new LinkedHashMap<>();
    private final Map<BlankNode, Integer> references = new HashMap<>();

    public TriGSink(Appendable out, IriMinter iris) {
        this.out = out;
        prefixes.put("rdf", Vocab.RDF);
        prefixes.put("xsd", Vocab.XSD);
        prefixes.put("prov", Vocab.PROV);
        prefixes.put("geo", Vocab.GEO);
        prefixes.put("lpg", Vocab.LPG);
        prefixes.put("e", iris.elementNamespace());
        prefixes.put("l", iris.labelNamespace());
        prefixes.put("t", iris.typeNamespace());
        prefixes.put("p", iris.propertyKeyNamespace());
        prefixes.put("s", iris.snapshotNamespace());
    }

    @Override
    public void quad(Term subject, Iri predicate, Term object, Iri graphName) {
        if (!started) {
            writePrefixes();
            started = true;
        }
        if (!inGraph || !Objects.equals(graph, graphName)) {
            writeBlock();
            switchGraph(graphName);
        } else if (subject instanceof Iri && !block.isEmpty() && !block.containsKey(subject)) {
            writeBlock();
        }
        block.computeIfAbsent(subject, s -> new LinkedHashMap<>())
                .computeIfAbsent(predicate, p -> new ArrayList<>())
                .add(object);
        if (object instanceof BlankNode b) {
            references.merge(b, 1, Integer::sum);
        }
    }

    @Override
    public void flush() {
        writeBlock();
    }

    @Override
    public void close() {
        writeBlock();
        if (inGraph && graph != null) {
            write("}\n");
        }
        inGraph = false;
    }

    private void writePrefixes() {
        int width = prefixes.keySet().stream().mapToInt(String::length).max().orElse(0) + 1;
        StringBuilder header = new StringBuilder();
        prefixes.forEach((prefix, ns) -> {
            header.append("@prefix ").append(prefix).append(':')
                    .append(" ".repeat(width - prefix.length())).append('<');
            TextEscaping.appendIri(header, ns);
            header.append("> .\n");
        });
        write(header);
    }

    private void switchGraph(Iri graphName) {
        StringBuilder s = new StringBuilder();
        if (inGraph && graph != null) {
            s.append("}\n");
        }
        if (graphName != null) {
            s.append('\n').append(term(graphName)).append(" {\n");
        }
        write(s);
        graph = graphName;
        inGraph = true;
    }

    /** Writes the pending block: the first subject, then blank nodes that could not be inlined. */
    private void writeBlock() {
        if (block.isEmpty()) {
            return;
        }
        String indent = graph == null ? "" : "    ";
        StringBuilder s = new StringBuilder();
        List<Term> subjects = new ArrayList<>(block.keySet());
        List<Term> inlined = new ArrayList<>();
        for (Term subject : subjects) {
            if (inlined.contains(subject) || isInlinable(subject)) {
                continue;
            }
            s.append('\n').append(indent).append(term(subject));
            appendPredicates(s, block.get(subject), " ;\n" + indent + "    ", inlined);
            s.append(" .\n");
        }
        write(s);
        block.clear();
        references.clear();
    }

    /** {@code separator} goes between predicates: a new line for statements, {@code " ; "} inside {@code [ ]}. */
    private void appendPredicates(StringBuilder s, Map<Iri, List<Term>> predicates, String separator, List<Term> inlined) {
        boolean first = true;
        for (Map.Entry<Iri, List<Term>> entry : predicates.entrySet()) {
            if (!first) {
                s.append(separator);
            } else {
                s.append(' ');
            }
            first = false;
            s.append(entry.getKey().equals(Vocab.RDF_TYPE) ? "a" : term(entry.getKey())).append(' ');
            List<Term> objects = entry.getValue();
            for (int i = 0; i < objects.size(); i++) {
                if (i > 0) {
                    s.append(", ");
                }
                s.append(object(objects.get(i), inlined));
            }
        }
    }

    private String object(Term object, List<Term> inlined) {
        if (object.equals(Vocab.RDF_NIL)) {
            return "()";
        }
        if (object instanceof BlankNode b && isInlinable(b)) {
            List<Term> items = listItems(b);
            if (items != null) {
                inlined.addAll(listCells(b));
                StringBuilder s = new StringBuilder("(");
                for (Term item : items) {
                    s.append(' ').append(object(item, inlined));
                }
                return s.append(" )").toString();
            }
            inlined.add(b);
            StringBuilder s = new StringBuilder("[");
            appendPredicates(s, block.get(b), " ; ", inlined);
            return s.append(" ]").toString();
        }
        return term(object);
    }

    /** A blank node described in this block and referenced exactly once. */
    private boolean isInlinable(Term t) {
        return t instanceof BlankNode b && block.containsKey(b) && references.getOrDefault(b, 0) == 1;
    }

    /** The items of a well-formed collection starting at {@code head}, or {@code null}. */
    private List<Term> listItems(BlankNode head) {
        List<Term> items = new ArrayList<>();
        Term cell = head;
        while (!cell.equals(Vocab.RDF_NIL)) {
            if (!(cell instanceof BlankNode b) || !block.containsKey(b) || references.getOrDefault(b, 0) != 1) {
                return null;
            }
            Map<Iri, List<Term>> p = block.get(b);
            List<Term> first = p.get(Vocab.RDF_FIRST);
            List<Term> rest = p.get(Vocab.RDF_REST);
            if (p.size() != 2 || first == null || rest == null || first.size() != 1 || rest.size() != 1) {
                return null;
            }
            items.add(first.getFirst());
            cell = rest.getFirst();
        }
        return items;
    }

    private List<Term> listCells(BlankNode head) {
        List<Term> cells = new ArrayList<>();
        Term cell = head;
        while (!cell.equals(Vocab.RDF_NIL)) {
            cells.add(cell);
            cell = block.get((BlankNode) cell).get(Vocab.RDF_REST).getFirst();
        }
        return cells;
    }

    private String term(Term term) {
        return switch (term) {
            case Iri iri -> iri(iri.value());
            case BlankNode b -> "_:" + b.label();
            case Literal l -> literal(l);
        };
    }

    private String iri(String value) {
        String best = null;
        String bestNs = "";
        for (Map.Entry<String, String> e : prefixes.entrySet()) {
            String ns = e.getValue();
            if (value.startsWith(ns) && ns.length() > bestNs.length()) {
                String local = value.substring(ns.length());
                if (local.isEmpty() || PN_LOCAL.matcher(local).matches()) {
                    best = e.getKey() + ":" + local;
                    bestNs = ns;
                }
            }
        }
        if (best != null) {
            return best;
        }
        StringBuilder s = new StringBuilder("<");
        TextEscaping.appendIri(s, value);
        return s.append('>').toString();
    }

    private String literal(Literal l) {
        String lex = l.lexicalForm();
        Iri dt = l.datatype();
        if (dt.equals(Vocab.XSD_INTEGER) && INTEGER.matcher(lex).matches()
                || dt.equals(Vocab.XSD_DOUBLE) && DOUBLE.matcher(lex).matches()
                || dt.equals(Vocab.XSD_BOOLEAN) && (lex.equals("true") || lex.equals("false"))) {
            return lex;
        }
        StringBuilder s = new StringBuilder("\"");
        TextEscaping.appendString(s, lex);
        s.append('"');
        if (!dt.equals(Vocab.XSD_STRING)) {
            s.append("^^").append(iri(dt.value()));
        }
        return s.toString();
    }

    private void write(CharSequence s) {
        try {
            out.append(s);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
