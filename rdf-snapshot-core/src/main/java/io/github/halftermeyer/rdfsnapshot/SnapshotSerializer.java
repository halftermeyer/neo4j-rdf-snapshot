package io.github.halftermeyer.rdfsnapshot;

import io.github.halftermeyer.rdfsnapshot.model.LpgNode;
import io.github.halftermeyer.rdfsnapshot.model.LpgRelationship;
import io.github.halftermeyer.rdfsnapshot.model.SnapshotMetadata;
import io.github.halftermeyer.rdfsnapshot.sink.QuadSink;
import io.github.halftermeyer.rdfsnapshot.term.BlankNode;
import io.github.halftermeyer.rdfsnapshot.term.Iri;
import io.github.halftermeyer.rdfsnapshot.term.Literal;
import io.github.halftermeyer.rdfsnapshot.term.Term;
import io.github.halftermeyer.rdfsnapshot.term.Vocab;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Writes a snapshot to a {@link QuadSink} (SPEC §7).
 *
 * <p>The phase methods let callers stream: metadata, then the vocabulary, then nodes and
 * relationships one by one. Callers that stream must pass nodes and relationships sorted by
 * element ID to get deterministic output; {@link #write} does it for them. Every phase method
 * ends with {@link QuadSink#flush()}.
 */
public final class SnapshotSerializer {
    private final IriMinter iris;
    private final QuadSink sink;

    public SnapshotSerializer(IriMinter iris, QuadSink sink) {
        this.iris = iris;
        this.sink = sink;
    }

    public IriMinter iris() {
        return iris;
    }

    /**
     * Writes a whole snapshot, then closes the sink. Nodes and
     * relationships are deduplicated and sorted by element ID.
     */
    public void write(SnapshotMetadata metadata, Collection<LpgNode> nodes, Collection<LpgRelationship> rels) {
        List<LpgNode> sortedNodes = sortedByElementId(nodes, LpgNode::elementId);
        List<LpgRelationship> sortedRels = sortedByElementId(rels, LpgRelationship::elementId);

        Vocabulary vocabulary = new Vocabulary();
        sortedNodes.forEach(vocabulary::addNode);
        sortedRels.forEach(vocabulary::addRelationship);

        Iri graph = iris.snapshot(metadata.snapshotId());
        writeMetadata(metadata);
        writeVocabulary(graph, vocabulary);
        sortedNodes.forEach(n -> writeNode(graph, n));
        sortedRels.forEach(r -> writeRelationship(graph, r));
        sink.close();
    }

    /** Snapshot metadata, in the default graph. */
    public void writeMetadata(SnapshotMetadata metadata) {
        Iri snapshot = iris.snapshot(metadata.snapshotId());
        emit(snapshot, Vocab.RDF_TYPE, Vocab.LPG_SNAPSHOT, null);
        emit(snapshot, Vocab.LPG_DATABASE, Literal.string(metadata.database()), null);
        emit(snapshot, Vocab.LPG_SCOPE, Literal.string(metadata.scope()), null);
        LocalDateTime utc = LocalDateTime.ofInstant(metadata.generatedAt(), ZoneOffset.UTC);
        emit(snapshot, Vocab.PROV_GENERATED_AT_TIME,
                new Literal(ValueMapper.dateTime(utc) + "Z", Vocab.XSD_DATE_TIME), null);
        sink.flush();
    }

    /** Declares labels, then relationship types, then property keys, each sorted by name. */
    public void writeVocabulary(Iri graph, Vocabulary vocabulary) {
        for (String label : vocabulary.labels()) {
            declare(graph, iris.label(label), Vocab.LPG_LABEL_CLASS, label);
        }
        for (String type : vocabulary.types()) {
            declare(graph, iris.type(type), Vocab.LPG_RELATIONSHIP_TYPE, type);
        }
        for (String key : vocabulary.propertyKeys()) {
            declare(graph, iris.propertyKey(key), Vocab.LPG_PROPERTY_KEY, key);
        }
    }

    /** One vocabulary term: {@code <term> a <class> ; lpg:name "name"}. */
    public void declare(Iri graph, Iri term, Iri vocabularyClass, String name) {
        emit(term, Vocab.RDF_TYPE, vocabularyClass, graph);
        emit(term, Vocab.LPG_NAME, Literal.string(name), graph);
        sink.flush();
    }

    public void writeNode(Iri graph, LpgNode node) {
        Iri subject = iris.element(node.elementId());
        emit(subject, Vocab.RDF_TYPE, Vocab.LPG_NODE, graph);
        for (String label : new TreeSet<>(node.labels())) {
            emit(subject, Vocab.LPG_LABEL, iris.label(label), graph);
        }
        emit(subject, Vocab.LPG_ELEMENT_ID, Literal.string(node.elementId()), graph);
        writeProperties(graph, subject, node.properties());
        sink.flush();
    }

    public void writeRelationship(Iri graph, LpgRelationship rel) {
        Iri subject = iris.element(rel.elementId());
        emit(subject, Vocab.RDF_TYPE, Vocab.LPG_RELATIONSHIP, graph);
        emit(subject, Vocab.LPG_TYPE, iris.type(rel.type()), graph);
        emit(subject, Vocab.LPG_ELEMENT_ID, Literal.string(rel.elementId()), graph);
        emit(subject, Vocab.LPG_SOURCE, iris.element(rel.sourceElementId()), graph);
        emit(subject, Vocab.LPG_TARGET, iris.element(rel.targetElementId()), graph);
        writeProperties(graph, subject, rel.properties());
        sink.flush();
    }

    /** Properties sorted by key; list cells after all the subject's own quads. */
    private void writeProperties(Iri graph, Iri subject, Map<String, Object> properties) {
        List<Runnable> pendingLists = new ArrayList<>();
        for (Map.Entry<String, Object> property : new TreeMap<>(properties).entrySet()) {
            Iri predicate = iris.propertyKey(property.getKey());
            switch (ValueMapper.map(property.getValue())) {
                case ValueMapper.Single single -> emit(subject, predicate, single.literal(), graph);
                case ValueMapper.Collection list -> {
                    Term head = list(graph, subject, predicate.value(), list.elements(), pendingLists);
                    emit(subject, predicate, head, graph);
                }
                case ValueMapper.Skipped skipped -> {}
            }
        }
        pendingLists.forEach(Runnable::run);
    }

    /**
     * Allocates the cells of an RDF collection and queues their quads.
     *
     * @return the head of the list, {@code rdf:nil} if empty
     */
    Term list(Iri graph, Term owner, String key, List<? extends Term> elements, List<Runnable> pending) {
        if (elements.isEmpty()) {
            return Vocab.RDF_NIL;
        }
        List<BlankNode> cells = new ArrayList<>(elements.size());
        for (int i = 0; i < elements.size(); i++) {
            cells.add(BlankNodes.of(graph.value(), owner, key, i));
        }
        pending.add(() -> {
            for (int i = 0; i < cells.size(); i++) {
                emit(cells.get(i), Vocab.RDF_FIRST, elements.get(i), graph);
                emit(cells.get(i), Vocab.RDF_REST, i + 1 < cells.size() ? cells.get(i + 1) : Vocab.RDF_NIL, graph);
            }
        });
        return cells.get(0);
    }

    void emit(Term subject, Iri predicate, Term object, Iri graph) {
        sink.quad(subject, predicate, object, graph);
    }

    QuadSink sink() {
        return sink;
    }

    private static <T> List<T> sortedByElementId(Collection<T> elements, java.util.function.Function<T, String> id) {
        Map<String, T> byId = new TreeMap<>();
        for (T element : elements) {
            byId.putIfAbsent(id.apply(element), element);
        }
        return new ArrayList<>(byId.values());
    }
}
