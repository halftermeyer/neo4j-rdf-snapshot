# Conformance suite

Test cases for implementations of the snapshot format ([SPEC](../docs/SPEC.md)). Each directory of [`conformance/`](conformance) is one case:

```
conformance/<case>/
  graphtype.cypher   # optional, run first (no case uses it at the moment, see DECISIONS D21)
  data.cypher        # creates the data
  params.json        # optional, parameters for data.cypher
  scope.cypher       # the scope query
  expected.trig      # the expected snapshot
  README.md          # what the case checks
```

| Case | Checks |
|---|---|
| `02-no-graph-type` | SPEC §10 data, no schema graph, no `lpg:schemaGraph` |
| `03-lists` | order, duplicates, empty list as `rdf:nil` |
| `04-parallel-relationships` | identical parallel relationships stay distinct |
| `05-paths-and-nested-lists` | path decomposition, nested lists, deduplication, maps ignored |
| `06-closure-rule` | endpoints of in-scope relationships are added |
| `07-value-types` | every value type of SPEC §7.4; `VECTOR` and mixed-sign durations absent |
| `08-name-encoding` | percent-encoding of names, `lpg:name`, separate namespaces |

## Running a case

1. Start from an empty database named `conformance`, with no constraints.
2. Run `graphtype.cypher` if present, then `data.cypher`. The files are Cypher 25. Statements are separated by a `;` at the end of a line.
   In `params.json`, `{"base64": "..."}` stands for a byte array parameter (Cypher has no byte array literal); other JSON values are passed as they are.
3. Export with the content of `scope.cypher` (whitespace at both ends removed) as scope query, base `http://acme.org/plm` and snapshot ID `20261005T140311Z`.

## Comparing with `expected.trig`

Element IDs are chosen by the database, so the output can't match `expected.trig` byte for byte. Compare the output and `expected.trig` as RDF datasets, after normalizing both the same way:

1. For every `?e lpg:elementId ?id`, check that `?e` is `http://acme.org/plm/e/` + the percent-encoding of `?id` (SPEC §5). Then replace `?id` with the constant `"ELEMENT_ID"`.
2. Replace every IRI starting with `http://acme.org/plm/e/` with a blank node (one blank node per IRI).
3. Check that the `prov:generatedAtTime` value is a valid `xsd:dateTime`, then replace it with a constant.

The two normalized datasets must be isomorphic. In `expected.trig`, element IRIs are readable placeholders (`e:part` with `lpg:elementId "part"`) that go through the same normalization.

The JUnit runner in [`src/test`](src/test/java/io/github/halftermeyer/rdfsnapshot/conformance) does this against Neo4j Enterprise in a Testcontainer, for the `rdfsnapshot.export` procedure. It also checks, for every case:

- the N-Quads and TriG outputs are isomorphic to each other, without normalization;
- two exports of the same database are byte-identical, in each format, apart from the `prov:generatedAtTime` literal.

```
mvn verify          # from the repository root; needs Docker
```

Raw outputs are written to `target/conformance-output/<case>.nq` and `.trig`.
