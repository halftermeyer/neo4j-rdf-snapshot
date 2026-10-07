# neo4j-rdf-snapshot

Reference implementation of the LPG → RDF snapshot format for Neo4j.

A snapshot is a mechanical serialization of a Cypher-scoped subgraph into an RDF dataset. The only inputs are a base IRI and a scope query. The default graph holds metadata about the snapshot; a named graph holds the records: every in-scope node and relationship, with its labels or type, endpoints and properties, plus the labels, types and property keys it uses. The format makes no modelling decision. Mapping the records to a domain ontology happens downstream, with standard tools. The format is defined in [docs/SPEC.md](docs/SPEC.md); implementation choices are in [docs/DECISIONS.md](docs/DECISIONS.md).

## Record ≠ thing

IRIs identify database records, not real-world entities: `e:4:…:12` is "node 12 of this snapshot", not "part P-001". Labels and types are stated with `lpg:label` and `lpg:type`, never as `rdf:type`, and relationships are resources, not direct triples. Saying what a record denotes, which labels are classes, or which relationships are plain triples is interpretation, and is left to the ontologist (SPEC §9). Element IRIs are only meaningful within their snapshot.

## Modules

- `rdf-snapshot-core`: the serializer. Pure Java 21, no runtime dependencies. It writes to a `QuadSink`; N-Quads and TriG sinks are included, and other sinks (e.g. a Jena dataset) plug in the same way.
- `rdf-snapshot-neo4j`: the Neo4j side, with no procedure class, for embedding in other plugins. `Snapshotter.write(tx, database, scopeQuery, base, snapshotId, generatedAt, sink)` writes the snapshot of a transaction to any `QuadSink`; `SnapshotRules` holds the input rules and defaults.
- `rdf-snapshot-procedures`: the Neo4j procedure `rdfsnapshot.export`, built on `rdf-snapshot-neo4j`.
- `rdf-snapshot-conformance`: the [conformance suite](rdf-snapshot-conformance/README.md).

### Using the library from another project

Through [JitPack](https://jitpack.io), from a tag or commit:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.halftermeyer.neo4j-rdf-snapshot</groupId>
    <artifactId>rdf-snapshot-neo4j</artifactId>
    <version>v0.1.0</version>   <!-- a tag or commit -->
</dependency>
```

Neo4j itself is a `provided` dependency. When shading into a plugin, relocate `io.github.halftermeyer.rdfsnapshot` (DECISIONS D25).

## Compatibility

| Library | SPEC | Neo4j | Java |
| --- | --- | --- | --- |
| 0.1.0 | v0.3 | 2026.09+ Enterprise | 21 |

Releases are tagged `vX.Y.Z`; the release procedure is in [docs/RELEASING.md](docs/RELEASING.md).

## Quickstart

Requirements: Java 21, Maven, Neo4j Enterprise 2026.x. Docker is needed for the tests only.

```
mvn verify                                  # unit tests + conformance suite (Docker)
mvn package -DskipTests                     # build only
cp rdf-snapshot-procedures/target/rdf-snapshot-procedures-*.jar $NEO4J_HOME/plugins/
```

Restart Neo4j, then:

```cypher
CREATE (:Part {partNumber: 'P-001', mass: 1.2, tags: ['steel', 'EU', 'steel']})
  -[:USES {quantity: 4}]->
  (:Substance:Material {casNumber: '335-67-1'});

CALL rdfsnapshot.export(
  "MATCH (p:Part {partNumber: 'P-001'})-[r:USES]->(m) RETURN p, r, m",
  {base: 'http://acme.org/plm'})
YIELD chunk
RETURN chunk;
```

Concatenate the `chunk` rows to get the document. The output, run in a database named `plm` (prefixes omitted):

```trig
s:20261007T085108Z a lpg:Snapshot ;
    lpg:database "plm" ;
    lpg:scope "MATCH (p:Part {partNumber: 'P-001'})-[r:USES]->(m) RETURN p, r, m" ;
    prov:generatedAtTime "2026-10-07T08:51:08Z"^^xsd:dateTime .

s:20261007T085108Z {

    l:Material a lpg:Label ;
        lpg:name "Material" .

    l:Part a lpg:Label ;
        lpg:name "Part" .

    l:Substance a lpg:Label ;
        lpg:name "Substance" .

    t:USES a lpg:RelationshipType ;
        lpg:name "USES" .

    p:casNumber a lpg:PropertyKey ;
        lpg:name "casNumber" .

    p:mass a lpg:PropertyKey ;
        lpg:name "mass" .

    p:partNumber a lpg:PropertyKey ;
        lpg:name "partNumber" .

    p:quantity a lpg:PropertyKey ;
        lpg:name "quantity" .

    p:tags a lpg:PropertyKey ;
        lpg:name "tags" .

    e:4:53851f55-dc2c-4d87-8433-d326f8ff631a:0 a lpg:Node ;
        lpg:label l:Part ;
        lpg:elementId "4:53851f55-dc2c-4d87-8433-d326f8ff631a:0" ;
        p:mass 1.2E0 ;
        p:partNumber "P-001" ;
        p:tags ( "steel" "EU" "steel" ) .

    e:4:53851f55-dc2c-4d87-8433-d326f8ff631a:1 a lpg:Node ;
        lpg:label l:Material, l:Substance ;
        lpg:elementId "4:53851f55-dc2c-4d87-8433-d326f8ff631a:1" ;
        p:casNumber "335-67-1" .

    e:5:53851f55-dc2c-4d87-8433-d326f8ff631a:1152921504606846976 a lpg:Relationship ;
        lpg:type t:USES ;
        lpg:elementId "5:53851f55-dc2c-4d87-8433-d326f8ff631a:1152921504606846976" ;
        lpg:source e:4:53851f55-dc2c-4d87-8433-d326f8ff631a:0 ;
        lpg:target e:4:53851f55-dc2c-4d87-8433-d326f8ff631a:1 ;
        p:quantity 4 .
}
```

### `rdfsnapshot.export(scopeQuery, config) YIELD chunk`

| Config key | Default | |
|---|---|---|
| `base` | required | base IRI, e.g. `http://acme.org/plm` |
| `format` | `'trig'` | `'trig'` or `'nquads'` |
| `snapshotId` | UTC timestamp `yyyyMMdd'T'HHmmss'Z'` | last segment of the snapshot IRI |

The procedure is read-only. The scope query runs in the procedure's transaction, so the snapshot is one consistent read. Every node and relationship in any column is in scope, including inside lists, map values and paths. Endpoints of in-scope relationships are added. Output is deterministic: the same data gives the same bytes, apart from `prov:generatedAtTime`.

## Non-goals

- No business or domain IRIs, no URI templates, no mapping configuration.
- No derived views: no `source type target` triples, no labels as `rdf:type`.
- No virtual objects; paths have no identity.
- No write-back or import into Neo4j.
- No reasoning and no domain ontology.
- Not serialized: `VECTOR` properties, durations with mixed signs, named time zones (the offset is kept). See SPEC §11 and DECISIONS.
- No schema graph (SPEC §8): GRAPH TYPE handling is not implemented (DECISIONS D21).

## License

[Apache License 2.0](LICENSE)
