# LPG → RDF Snapshot Format

**Status:** draft v0.3, 2026-10-07 **Target:** Neo4j, Cypher 25+, Enterprise Edition **Reference implementation:** [halftermeyer/neo4j-rdf-snapshot](https://github.com/halftermeyer/neo4j-rdf-snapshot)

### Changes since v0.2

- §3: maps are containers, like lists and paths (nodes and relationships inside map values are in scope).
- §5: exact percent-encoding rule; trailing `/` of `base` ignored.
- §6: CRS IRIs for Cartesian points; `lpg:uuid` datatype; `prov:generatedAtTime` listed.
- §7: vocabulary terms declared only when used; points (2D/3D, WGS-84 and Cartesian), `UUID`, offsets with seconds, mixed-sign durations; canonical lexical forms; determinism.
- §8: the schema graph is **optional**; reading the GRAPH TYPE from a read-only context.
- §11, §12: losses and open questions updated.

## 1. Purpose

A canonical, mechanical serialization of a Cypher-scoped subgraph of a Neo4j database into RDF.

The output is a **snapshot of database records**. It makes no modelling decision. Mapping these records to a domain ontology is explicitly out of scope: it is the ontologist's job, done downstream with standard tools (OWL/RDFS axioms, SPARQL `CONSTRUCT`, rules, SHACL).

Design principles:

- **Mechanical.** The only inputs are a base IRI and a scope query. No templates, no mapping configuration.
- **Faithful.** Lossless for in-scope elements, except the cases listed in §11.
- **Record ≠ thing.** IRIs identify database records, not real-world entities. Saying what a record denotes is a semantic act, left to the ontologist.
- **Clinical.** One representation per fact, no derived views. `rdf:type` is reserved for the classes of the meta-model (`lpg:Node`, `lpg:Relationship`, …). Labels and relationship types are stated with `lpg:label` and `lpg:type`, never as `rdf:type`: classifying records is already interpretation (§9).
- **Read-only.** No write-back. Downstream results refer to elements through their `elementId`; writing anything back is the user's business, in Cypher.

## 2. Non-goals

- Business or domain IRIs (no user-defined URI templates, no `uri` property convention).
- Virtual objects, or paths as first-class objects.
- Derived views in the snapshot (e.g. direct `source type target` triples, labels as `rdf:type`).
- Write-back or deserialization into Neo4j.
- Mapping to domain ontologies.

## 3. Inputs and scope

| Input | Description |
| --- | --- |
| `base` | Base IRI of the project, e.g. `http://acme.org/plm` |
| `scope` | A read-only Cypher query |

Scope semantics:

- Every `NODE` and `RELATIONSHIP` found in any returned column is in scope, **including inside containers**: `LIST`, `MAP` (values, not keys) and `PATH`, recursively. Paths are decomposed into their nodes and relationships; the path itself has no identity.
- All other returned values are ignored.
- **Closure rule:** the endpoints of every in-scope relationship are added to the scope.
- Elements are deduplicated.
- The query and the serialization of the data run in **a single read transaction**, so the data of a snapshot is a consistent state of the database.

## 4. Three levels

The format separates three levels, as in classic meta-modelling:

| Level | Namespace | Content |
| --- | --- | --- |
| Meta-model | `lpg:` | What a node, a label, a relationship, a property key is. Generic, fixed. |
| Graph vocabulary | `l:`, `t:`, `p:` | The names used by *this* graph: its labels, relationship types, property keys. What the GRAPH TYPE describes. |
| Records | `e:` | The nodes and relationships of the snapshot. |

The graph vocabulary is on the **signifier** side. `l:Part` names the label "Part" of this database; it says nothing about what a part is. It is the anchor the ontologist attaches domain meaning to (§9).

IRIs are used for the graph vocabulary, not literals, because:

- property keys must be IRIs anyway (they are used as predicates);
- one must be able to say things about labels and types (original name, GRAPH TYPE description);
- `"Part"` from two databases would be the same literal and would silently merge; base-prefixed IRIs stay distinct until someone explicitly aligns them.

## 5. Identifiers

| Thing | IRI pattern |
| --- | --- |
| Element (node or relationship) | `{base}/e/{elementId}` |
| Label | `{base}/label/{name}` |
| Relationship type | `{base}/type/{name}` |
| Property key | `{base}/prop/{name}` |
| Snapshot (data graph) | `{base}/snapshot/{snapshotId}` |
| Schema graph (optional, §8) | `{base}/snapshot/{snapshotId}/schema` |

**Base.** One trailing `/` of `base` is ignored, so `http://acme.org/plm/` and `http://acme.org/plm` give the same IRIs.

**Encoding.** Names and element IDs are encoded as UTF-8; every byte outside `A-Z a-z 0-9 - . _ ~ :` becomes `%XX`, with upper-case hex. This single rule keeps local names usable as TriG prefixed names without escapes. The original name of every vocabulary term is kept in `lpg:name`.

**Separate namespaces** for labels, types and keys: a label `Name` and a property key `Name` must not share an IRI.

**Stability.** Neo4j does not guarantee that an `elementId` is stable across transactions, and the ID of a deleted element can be reused. **Element IRIs are only meaningful within their snapshot.** They must not be compared or persisted across snapshots. Anything that must last (links to domain IRIs, alignments) is recomputed from the snapshot by downstream rules.

## 6. Meta-vocabulary (`lpg:`)

The namespace below is a placeholder.

```turtle
@prefix lpg:  <https://example.org/lpg#> .
@prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
@prefix xsd:  <http://www.w3.org/2001/XMLSchema#> .

# ---- records
lpg:Element          a rdfs:Class .
lpg:Node             a rdfs:Class ; rdfs:subClassOf lpg:Element .
lpg:Relationship     a rdfs:Class ; rdfs:subClassOf lpg:Element .

lpg:elementId a rdf:Property ; rdfs:domain lpg:Element ;      rdfs:range xsd:string .
lpg:label     a rdf:Property ; rdfs:domain lpg:Node ;         rdfs:range lpg:Label .
lpg:type      a rdf:Property ; rdfs:domain lpg:Relationship ; rdfs:range lpg:RelationshipType .
lpg:source    a rdf:Property ; rdfs:domain lpg:Relationship ; rdfs:range lpg:Node .
lpg:target    a rdf:Property ; rdfs:domain lpg:Relationship ; rdfs:range lpg:Node .

# ---- graph vocabulary
lpg:Label            a rdfs:Class .                                # an individual, not a class
lpg:RelationshipType a rdfs:Class .                                # an individual, not a property
lpg:PropertyKey      a rdfs:Class ; rdfs:subClassOf rdf:Property . # keys are used as predicates

lpg:name a rdf:Property ; rdfs:range xsd:string .                 # original Neo4j name

# ---- values
lpg:uuid        a rdfs:Datatype .     # lexical form: lower-case 8-4-4-4-12 hex
lpg:Cartesian2D a rdfs:Resource .     # CRS IRI for Cartesian 2D points in WKT literals
lpg:Cartesian3D a rdfs:Resource .     # CRS IRI for Cartesian 3D points in WKT literals

# ---- schema description (GRAPH TYPE, optional, §8)
lpg:PropertySpec         a rdfs:Class .
lpg:Constraint           a rdfs:Class .
lpg:KeyConstraint        a rdfs:Class ; rdfs:subClassOf lpg:Constraint .
lpg:UniquenessConstraint a rdfs:Class ; rdfs:subClassOf lpg:Constraint .

lpg:identifying   a rdf:Property ; rdfs:range xsd:boolean .     # identifies an element type
lpg:implies       a rdf:Property ; rdfs:domain lpg:Label ; rdfs:range lpg:Label .
lpg:fromLabel     a rdf:Property ; rdfs:domain lpg:RelationshipType ; rdfs:range lpg:Label .
lpg:toLabel       a rdf:Property ; rdfs:domain lpg:RelationshipType ; rdfs:range lpg:Label .
lpg:hasProperty   a rdf:Property ; rdfs:range lpg:PropertySpec .
lpg:key           a rdf:Property ; rdfs:domain lpg:PropertySpec ; rdfs:range lpg:PropertyKey .
lpg:datatype      a rdf:Property ; rdfs:domain lpg:PropertySpec ; rdfs:range rdfs:Datatype .
lpg:listOf        a rdf:Property ; rdfs:domain lpg:PropertySpec ; rdfs:range rdfs:Datatype .
lpg:required      a rdf:Property ; rdfs:domain lpg:PropertySpec ; rdfs:range xsd:boolean .
lpg:hasConstraint a rdf:Property ; rdfs:range lpg:Constraint .
lpg:onKeys        a rdf:Property ; rdfs:domain lpg:Constraint ; rdfs:range rdf:List .

# ---- snapshot
lpg:Snapshot    a rdfs:Class .
lpg:database    a rdf:Property ; rdfs:domain lpg:Snapshot ; rdfs:range xsd:string .
lpg:scope       a rdf:Property ; rdfs:domain lpg:Snapshot ; rdfs:range xsd:string .
lpg:schemaGraph a rdf:Property ; rdfs:domain lpg:Snapshot .
```

## 7. Serialization rules

### 7.1 Snapshot metadata

In the default graph:

```turtle
<snapshot-iri> a lpg:Snapshot ;
    lpg:database "<database name>" ;
    lpg:scope "<scope query>" ;
    prov:generatedAtTime "<export start, UTC, to the second>"^^xsd:dateTime ;
    lpg:schemaGraph <schema-graph-iri> .          # only if a schema graph is emitted
```

The default `snapshotId` is the same instant, formatted `yyyyMMdd'T'HHmmss'Z'`.

### 7.2 Graph vocabulary

Every label, relationship type and property key **used by at least one serialized triple** is declared once. A property key whose in-scope values are all skipped (§11) is not declared.

```turtle
l:Part        a lpg:Label ;            lpg:name "Part" .
t:USES        a lpg:RelationshipType ; lpg:name "USES" .
p:partNumber  a lpg:PropertyKey ;      lpg:name "partNumber" .
```

### 7.3 Nodes

```turtle
<element-iri> a lpg:Node ;
    lpg:label <label-iri>, ... ;
    lpg:elementId "<elementId>" ;
    <key-iri> <value> ;
    ... .
```

### 7.4 Relationships

```turtle
<rel-iri> a lpg:Relationship ;
    lpg:type   <type-iri> ;
    lpg:elementId "<elementId>" ;
    lpg:source <source-iri> ;
    lpg:target <target-iri> ;
    <key-iri>  <value> .
```

A relationship is a resource and nothing else. No direct `source type target` triple is emitted: it would be a derived view, it would collapse parallel relationships, and it would already be a modelling choice (§9.3).

### 7.5 Property values

Property keys are the only part of the graph vocabulary used as predicates. RDF needs a predicate, and `p:quantity` is a fresh predicate with no built-in meaning: it reads "the LPG property `quantity` of this record".

| Cypher type | RDF |
| --- | --- |
| `STRING` | `xsd:string` |
| `INTEGER` | `xsd:integer` |
| `FLOAT` | `xsd:double` |
| `BOOLEAN` | `xsd:boolean` |
| `DATE` | `xsd:date` |
| `LOCAL TIME` | `xsd:time`, no offset |
| `ZONED TIME` | `xsd:time` with offset |
| `LOCAL DATETIME` | `xsd:dateTime`, no offset |
| `ZONED DATETIME` | `xsd:dateTime` with offset (named zone dropped) |
| `DURATION` | `xsd:duration`, see below |
| `POINT` WGS-84 2D | `geo:wktLiteral`: `<http://www.opengis.net/def/crs/OGC/1.3/CRS84> POINT(lon lat)` |
| `POINT` WGS-84 3D | `geo:wktLiteral`: `<http://www.opengis.net/def/crs/OGC/0/CRS84h> POINT Z(lon lat h)` |
| `POINT` Cartesian 2D | `geo:wktLiteral`: `<…lpg#Cartesian2D> POINT(x y)` |
| `POINT` Cartesian 3D | `geo:wktLiteral`: `<…lpg#Cartesian3D> POINT Z(x y z)` |
| `UUID` | `lpg:uuid` |
| `LIST<T>` | RDF collection of the mapped elements |
| `VECTOR` | not serialized |
| Byte array | `xsd:base64Binary` |

**Points.** A CRS IRI is always written. GeoSPARQL reads a `wktLiteral` without CRS as CRS84, so a Cartesian point without one would be silently misread as longitude/latitude; the `lpg:` CRS IRIs prevent that.

**Offsets.** XSD offsets have hours and minutes only. An offset with a seconds component (e.g. `+01:00:30`) is written without its seconds.

**Durations.** Seconds and nanoseconds are combined first. If months, days and combined seconds all have the same sign (or are zero), the value is written as `[-]PnYnMnDTnHnMn.nS` (years = months / 12, zero components omitted, `PT0S` for zero). Days are never converted into seconds. Otherwise the duration has no `xsd:duration` form and the property is **not serialized**. A list containing such a duration is not serialized as a whole.

**Lists** are serialized as RDF collections (`rdf:List`), which keeps order and duplicates. An empty list is `rdf:nil`, so it stays distinguishable from an absent property:

```turtle
<n> p:tags ( "steel" "EU" "steel" ) .
<m> p:tags () .
```

Collections are opaque to RDFS reasoning. Flattening them (e.g. into a domain predicate) is a downstream concern.

### 7.6 Canonical forms and determinism

- Literals are written in their **XSD canonical lexical form** (e.g. `1.2E0` for a double, seconds always present in times, `Z` for a zero offset, padded base64 without line breaks). `xsd:string` literals are written without datatype.
- Escaping follows canonical N-Triples, for every output syntax.
- **Determinism:** the same data and the same scope give the same output, `prov:generatedAtTime` (and a default `snapshotId`) aside. The order of statements and the labels of blank nodes are implementation-defined; conformance is checked by RDF isomorphism.

## 8. Schema graph: the GRAPH TYPE, described as data (optional)

The schema graph is **optional**. An implementation that cannot read the GRAPH TYPE, or a database without one, emits no schema graph and no `lpg:schemaGraph` triple. Without a schema graph, an ontologist only has observations: any axiom derived from the data alone is a hypothesis about future data, not a guarantee.

When emitted, the schema graph is a **clinical description** of the GRAPH TYPE in the `lpg:` vocabulary. It is not translated into SHACL or RDFS: any such translation is an interpretation, covered in §9.

| GRAPH TYPE | Description |
| --- | --- |
| Node element type `(:A => …)` | `l:A lpg:identifying true` |
| Implied labels `(:A => :B&C)` | `l:A lpg:implies l:B, l:C` |
| Property type `k :: T` | `l:A lpg:hasProperty [ lpg:key p:k ; lpg:datatype xsd:… ]` |
| List type `k :: LIST<T NOT NULL>` | `[ lpg:key p:k ; lpg:listOf xsd:… ]` |
| `NOT NULL` | `lpg:required true` on the property spec |
| `IS KEY`, `IS UNIQUE` | `l:A lpg:hasConstraint [ a lpg:KeyConstraint ; lpg:onKeys ( p:k … ) ]` (resp. `lpg:UniquenessConstraint`) |
| Relationship element type `(:A)-[:R => …]->(:B)` | `t:R lpg:identifying true ; lpg:fromLabel l:A ; lpg:toLabel l:B` (endpoints only when declared) |
| Relationship property types | `t:R lpg:hasProperty [ … ]`, same as nodes |

**Semantics reminder.** A GRAPH TYPE is a set of **constraints**, checked at commit: a transaction that would leave the graph inconsistent fails (closed world). Data read in one transaction conforms to the GRAPH TYPE of that transaction.

**Reading the GRAPH TYPE.** `SHOW` commands are not allowed inside a read-only procedure. An implementation may read the schema in a **separate transaction**. In that case the data and the schema come from two reads, and their consistency is only guaranteed if the schema did not change in between; the implementation must document this.

*Informative (observed on Neo4j 2026.09).* The GRAPH TYPE is stored as constraints. `SHOW CONSTRAINTS` returns structured rows: the constraint `type` (property type, existence, label existence for implied labels, source/target label for endpoints, key, uniqueness), the labels or types, the properties, the enforced label, the property type, and a `classification` column (`dependent` for constraints generated by element types, which identifies identifying labels and types). This is enough to build the schema graph without parsing the textual output of `SHOW CURRENT GRAPH TYPE`.

## 9. Standard interpretation (opt-in, outside the format)

Everything in this section is **interpretation**. It is not part of the snapshot. It is shipped as separate, reusable artifacts that the ontologist imports or not.

### 9.1 Labels and types as classes

The most common interpretation step is to treat "records with label Part" as a class. Three equivalent ways, depending on the reasoner available.

**OWL 2 RL (recommended).** A `hasValue` restriction. `l:Part` stays an individual, no punning, OWL DL compatible:

```turtle
ex:PartRecord owl:equivalentClass
    [ a owl:Restriction ; owl:onProperty lpg:label ; owl:hasValue l:Part ] .

ex:PartRecord rdfs:subClassOf ex:Component .
```

Use `rdfs:subClassOf` towards domain classes, **not** `owl:equivalentClass`: an equivalence would make every inferred `ex:Component` receive `lpg:label l:Part`, polluting the meta-model level with labels that do not exist in the database.

Combinations of labels use `owl:intersectionOf`. Relationship types work the same way with `owl:onProperty lpg:type`.

**RDFS only (fallback).** Two axioms make labels and types act as `rdf:type` under RDFS entailment (rule rdfs7):

```turtle
lpg:label rdfs:subPropertyOf rdf:type .
lpg:type  rdfs:subPropertyOf rdf:type .
```

Valid RDF/RDFS, but outside OWL DL. Check that the chosen reasoner applies rdfs7 when the super-property is `rdf:type` itself, then chains `rdfs:subClassOf`.

**Rules.** The most explicit form:

```sparql
CONSTRUCT { ?n a ex:Component } WHERE { ?n lpg:label l:Part }
```

### 9.2 Schema graph to SHACL and RDFS

When a schema graph is present and labels are read as classes, it can be translated by generic rules:

- `lpg:implies` → `rdfs:subClassOf` (sound: the data conforms by construction), or a SHACL shape checking the implied label;
- `lpg:hasProperty` with `lpg:datatype` / `lpg:required` → SHACL `sh:datatype` / `sh:minCount 1`;
- `lpg:fromLabel` / `lpg:toLabel` → SHACL shapes on `lpg:source` / `lpg:target`, targeted by relationship type.

The faithful reading of a GRAPH TYPE is SHACL (constraints). RDFS axioms are derived liftings, valid only because the data conforms. Their value is downstream: reasoning on, and validating, derived data.

### 9.3 Direct relation triples

If an ontologist wants relationships as plain triples, one generic rule rebuilds them. Whether the predicate is the type IRI itself (punning) or a domain predicate is their decision:

```sparql
CONSTRUCT { ?s ex:uses ?o }
WHERE     { ?r lpg:type t:USES ; lpg:source ?s ; lpg:target ?o }
```

Parallel relationships collapse at this point, by the ontologist's choice, not in the snapshot.

## 10. Example

GRAPH TYPE (used only by the optional schema graph):

```cypher
ALTER CURRENT GRAPH TYPE SET {
  (:Part => {partNumber :: STRING NOT NULL, mass :: FLOAT, tags :: LIST<STRING NOT NULL>}),
  (:Substance => :Material {casNumber :: STRING NOT NULL}),
  (:Part)-[:USES => {quantity :: INTEGER NOT NULL}]->(:Substance)
}
```

Data:

```
(:Part {partNumber: 'P-001', mass: 1.2, tags: ['steel', 'EU', 'steel']})
  -[:USES {quantity: 4}]->
(:Substance:Material {casNumber: '335-67-1'})
```

Output (element IDs shortened for readability):

```trig
@prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix xsd:  <http://www.w3.org/2001/XMLSchema#> .
@prefix prov: <http://www.w3.org/ns/prov#> .
@prefix lpg:  <https://example.org/lpg#> .
@prefix e:    <http://acme.org/plm/e/> .
@prefix l:    <http://acme.org/plm/label/> .
@prefix t:    <http://acme.org/plm/type/> .
@prefix p:    <http://acme.org/plm/prop/> .
@prefix s:    <http://acme.org/plm/snapshot/> .

# ---------- snapshot metadata (default graph)
s:20261005T140311Z a lpg:Snapshot ;
    lpg:database "plm" ;
    lpg:scope "MATCH (p:Part {partNumber: 'P-001'})-[r:USES]->(m) RETURN p, r, m" ;
    prov:generatedAtTime "2026-10-05T14:03:11Z"^^xsd:dateTime ;
    lpg:schemaGraph <http://acme.org/plm/snapshot/20261005T140311Z/schema> .   # only with a schema graph

# ---------- data graph
s:20261005T140311Z {

    l:Material   a lpg:Label ;            lpg:name "Material" .
    l:Part       a lpg:Label ;            lpg:name "Part" .
    l:Substance  a lpg:Label ;            lpg:name "Substance" .
    t:USES       a lpg:RelationshipType ; lpg:name "USES" .
    p:casNumber  a lpg:PropertyKey ;      lpg:name "casNumber" .
    p:mass       a lpg:PropertyKey ;      lpg:name "mass" .
    p:partNumber a lpg:PropertyKey ;      lpg:name "partNumber" .
    p:quantity   a lpg:PropertyKey ;      lpg:name "quantity" .
    p:tags       a lpg:PropertyKey ;      lpg:name "tags" .

    e:4:a1b2:12 a lpg:Node ;
        lpg:label l:Part ;
        lpg:elementId "4:a1b2:12" ;
        p:mass 1.2E0 ;
        p:partNumber "P-001" ;
        p:tags ( "steel" "EU" "steel" ) .

    e:4:a1b2:57 a lpg:Node ;
        lpg:label l:Material, l:Substance ;
        lpg:elementId "4:a1b2:57" ;
        p:casNumber "335-67-1" .

    e:5:a1b2:3 a lpg:Relationship ;
        lpg:type t:USES ;
        lpg:elementId "5:a1b2:3" ;
        lpg:source e:4:a1b2:12 ;
        lpg:target e:4:a1b2:57 ;
        p:quantity 4 .
}

# ---------- schema graph (optional)
<http://acme.org/plm/snapshot/20261005T140311Z/schema> {

    l:Part lpg:identifying true ;
        lpg:hasProperty [ lpg:key p:partNumber ; lpg:datatype xsd:string ; lpg:required true ] ,
                        [ lpg:key p:mass ;       lpg:datatype xsd:double ] ,
                        [ lpg:key p:tags ;       lpg:listOf   xsd:string ] .

    l:Substance lpg:identifying true ;
        lpg:implies l:Material ;
        lpg:hasProperty [ lpg:key p:casNumber ; lpg:datatype xsd:string ; lpg:required true ] .

    t:USES lpg:identifying true ;
        lpg:fromLabel l:Part ;
        lpg:toLabel   l:Substance ;
        lpg:hasProperty [ lpg:key p:quantity ; lpg:datatype xsd:integer ; lpg:required true ] .
}
```

## 11. Guarantees and known losses

**Guarantees.**

- The data of a snapshot is a consistent state: one read transaction.
- In-scope nodes and relationships (labels, type, endpoints, properties, list order and duplicates, empty lists, parallel relationships) can be rebuilt from the snapshot, except for the losses below.

**Known losses.**

- `VECTOR` properties are not serialized.
- `DURATION` values with mixed-sign components are not serialized (and neither is a list containing one).
- Zoned temporal values keep their offset only; the named time zone (e.g. `Europe/Paris`) is dropped.
- Offsets with a seconds component lose their seconds.
- Without a schema graph, the universal statements of the GRAPH TYPE (implied labels, required properties, endpoints, keys) are not in the snapshot.

## 12. Open questions

- Final IRI of the `lpg:` namespace (and so of `lpg:uuid`, `lpg:Cartesian2D`, `lpg:Cartesian3D`).
- `VECTOR`: always excluded, or opt-in.
- Named time zones: a dedicated `lpg:` property, or an accepted loss.
- Schema graph: read in a separate transaction (with the documented consistency caveat), or wait for a way to read the GRAPH TYPE inside the read transaction.
- Confirm how `UUID` values surface in Cypher and in the procedure API, and the exact lexical form.
- Ship the §9 interpretation artifacts (OWL 2 RL, RDFS, rules) as files next to the spec, and test them on Jena (rdfs7 with `rdf:type`, `hasValue` support in the chosen OWL profile).

## 13. Downstream (informative)

Mapping the snapshot to a domain knowledge graph follows naturally in strata, run in order:

1. **Interpretation**: labels and types as classes (§9.1).
2. **Lifting**: from records to things (a `denotes`-style link, minting domain IRIs such as CAS numbers, entity resolution).
3. **Alignment**: RDFS/OWL axioms towards domain ontologies.
4. **Restructuring**: `CONSTRUCT` for what axioms cannot express (direct relations, n-ary contraction, splits, list flattening into a domain predicate).
5. **Business rules**.
6. **Validation**: domain shapes on the result.

Every derived fact should remain traceable to the record IRIs of the snapshot, and so to `elementId`: this is the only bridge back to what users see in Neo4j.