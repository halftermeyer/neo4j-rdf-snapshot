# Decisions

Choices made where `SPEC.md` is silent or ambiguous. One entry per decision: context, options, choice, why.
Entries marked **[spec]** propose a change to the spec.

**SPEC v0.3** (2026-10-07) adopted D1, D2, D4 to D8, D11, D13, D14 and the spec changes of D6, D7 and D21; those entries remain as rationale. It superseded D9 (Cartesian CRS IRIs) and D16 (maps are now containers), and added `UUID` (D24).

## Identifiers

### D1. Which characters stay unencoded in names and element IDs

- **Context.** SPEC §5: "percent-encoded as RFC 3986 path segments (UTF-8). `:` is kept as is." A path segment may legally contain sub-delims (`!$&'()*+,;=`) and `@` unencoded.
- **Options.** (a) Keep every `pchar` legal in a segment. (b) Keep only the unreserved set plus `:`.
- **Choice.** (b): bytes outside `A-Z a-z 0-9 - . _ ~ :` become `%XX`, with upper-case hex.
- **Why.** One rule with no exceptions, easy to reimplement. It also gives local names that work as TriG prefixed names without backslash escapes (`PN_LOCAL` needs `\!`, `\&`, … for sub-delims).

### D2. Trailing slash in `base`

- **Context.** `{base}/e/{id}` with `base = http://acme.org/plm/` would give `plm//e/`.
- **Choice.** Ignore one trailing `/` of `base`.
- **Why.** Avoids an empty path segment, which is almost certainly not what the user meant.

### D3. Deterministic blank node labels

- **Context.** List cells are blank nodes. Output must be byte-identical across runs.
- **Options.** (a) A counter in emission order. (b) A label derived from context.
- **Choice.** (b): `b` + the first 128 bits (32 hex digits) of SHA-256 over `graph IRI ␀ owner ␀ key ␀ position`. For a list cell: owner = the subject IRI, key = the predicate IRI, position = the index in the list.
- **Why.** A label doesn't change when an unrelated element is added to or removed from the scope, which makes diffs readable. Including the graph IRI means blank nodes from two snapshots stay distinct when their N-Quads files are concatenated.

## Literals

### D4. Lexical form of `xsd:double`

- **Context.** SPEC §10 writes `1.2e0`; §7.4 just says `xsd:double`.
- **Choice.** The XSD canonical form: one non-zero digit before the point, at least one after, `E`, the exponent (`1.2E0`, `4.0E0`, `1.0E2`, `-0.0E0`, `INF`, `-INF`, `NaN`). The digits are the shortest decimal that round-trips (Java `Double.toString`). A 32-bit float input is first widened through its shortest decimal (`1.1f` → `1.1E0`).
- **Why.** One spelling per value is what makes output byte-identical and easy to compare. `1.2E0` is also a valid bare TriG `DOUBLE` token.

### D5. Lexical forms of temporal values

- **Choice.**
  - Seconds are always written (`10:15:00`); fractional seconds have no trailing zeros, nanosecond precision at most.
  - Years have at least four digits, never a `+` sign; negative years keep `-` (`-0044-03-15`, ISO and XSD 1.1 agree on year 0).
  - A zero offset is written `Z`; other offsets `±hh:mm`.
- **Why.** These are the XSD canonical forms. Java's `toString` differs (`10:15`, `+12345-01-01`), so formatting is done by hand.

### D6. Offsets with a seconds component (adopted in SPEC v0.3, §7.5)

- **Context.** Neo4j accepts offsets such as `+01:00:30`. XSD offsets have minutes only.
- **Options.** (a) Emit `+01:00:30` (ill-typed literal). (b) Drop the seconds. (c) Skip the property.
- **Choice.** (b).
- **Why.** Keeps a valid literal and loses at most 59 seconds of offset, which almost never occurs in practice.
- **Spec change.** Add this to the known losses of §11.

### D7. Durations with mixed signs (adopted in SPEC v0.3, §7.5)

- **Context.** SPEC §11: a `DURATION` with mixed-sign components "may not be representable". Neo4j stores months, days, seconds and nanoseconds with nanoseconds always in `[0, 1e9)`, so `-0.5s` is seconds `-1`, nanos `500000000`.
- **Options.** (a) Emit an ill-typed literal. (b) Skip the property. (c) Normalize days into seconds (wrong: a day is not always 86400 s).
- **Choice.** Combine seconds and nanoseconds first. If months, days and the combined seconds then all have the same sign (or are zero), emit `[-]PnYnMnDTnHnMn.nS` with years = months / 12, hours and minutes taken from seconds, zero components omitted, `PT0S` for zero. Otherwise skip the property, like `VECTOR`. A list containing such a duration is skipped as a whole.
- **Why.** Ill-typed literals break downstream tools. Skipping is the known loss the spec already announces; (c) would change the value.
- **Spec change.** §11 should say explicitly that such properties are not serialized.

### D8. Points (adopted in SPEC v0.3, §7.5)

- **Context.** SPEC §7.4: WGS-84 → `geo:wktLiteral` "with CRS84"; Cartesian → no CRS.
- **Choice.**
  - 2D WGS-84 (SRID 4326): `<http://www.opengis.net/def/crs/OGC/1.3/CRS84> POINT(lon lat)`.
  - 3D WGS-84 (SRID 4979): `<http://www.opengis.net/def/crs/OGC/0/CRS84h> POINT Z(lon lat h)`. CRS84 is two-dimensional; CRS84h is its 3D (ellipsoidal height) counterpart in GeoSPARQL 1.1.
  - Cartesian 2D / 3D: `<lpg:Cartesian2D> POINT(x y)` / `<lpg:Cartesian3D> POINT Z(x y z)` (see D9).
  - Coordinates in plain decimal notation, shortest round-trip digits, no trailing zeros (`2.0` → `2`, `1e-7` → `0.0000001`).
- **Why.** CRS84 is written explicitly, as the spec asks. Plain decimals are the most widely parsed WKT number form.

### D9. Cartesian points read as CRS84 (superseded by SPEC v0.3)

- **Context.** GeoSPARQL says a `wktLiteral` without CRS IRI is in CRS84. A Cartesian point without CRS will therefore be read as longitude/latitude by any GeoSPARQL engine.
- **Choice (v0.2).** No CRS, as the spec then said, with a proposal for `lpg:` CRS IRIs.
- **Now.** SPEC v0.3 §7.5: `<lpg:Cartesian2D> POINT(x y)` and `<lpg:Cartesian3D> POINT Z(x y z)`, implemented.

### D10. Byte arrays

- **Choice.** `xsd:base64Binary`, standard alphabet (RFC 4648 §4), with padding, no line breaks.
- **Why.** This is the canonical `xsd:base64Binary` form.

### D11. Literal escaping

- **Choice.** Canonical N-Triples for both sinks: `\" \\ \n \r \t \b \f` as ECHAR, other C0 controls and U+007F as `\uXXXX`, everything else verbatim (UTF-8). `xsd:string` literals are written without datatype. IRIs are written verbatim, except characters `IRIREF` forbids, which become `\uXXXX` (only an unusual `base` can produce them).
- **Why.** One escaping for both formats; output is readable and byte-stable.

## Serialization

### D12. Order of the output

- **Context.** The prompt asks for byte-identical output for the same input.
- **Choice.**
  1. Default graph: snapshot metadata (`a`, `lpg:database`, `lpg:scope`, `prov:generatedAtTime`).
  2. Data graph: labels, then relationship types, then property keys, each sorted by name. Then nodes, then relationships, each sorted by element ID.

  Within a node: `a`, labels sorted by name, `lpg:elementId`, properties sorted by key. Within a relationship: `a`, `lpg:type`, `lpg:elementId`, `lpg:source`, `lpg:target`, properties sorted by key. List cells come after their subject. All sorting is by Java `String.compareTo` (UTF-16 code units).
- **Why.** Matches the layout of SPEC §10. Sorting by element ID is the only order that doesn't depend on the scope query.

### D13. Property keys used only by skipped values

- **Context.** SPEC §7.1 declares "every property key encountered in scope". A key may hold only vectors (or unrepresentable durations).
- **Choice.** A key is declared only if at least one in-scope value for it is serialized.
- **Why.** A declared key that no triple uses describes data that isn't in the snapshot, and the conformance case for `VECTOR` asks that the property not appear at all.

### D14. `prov:generatedAtTime`

- **Context.** SPEC §10 shows `prov:generatedAtTime` in the snapshot metadata; §6 doesn't list it.
- **Choice.** Emit it, from the instant the export starts, truncated to seconds, in UTC (`Z`).
- **Why.** The example is normative enough, and the term comes from PROV, not `lpg:`.

### D15. Emission contract of `QuadSink`

- **Context.** The TriG sink must group by subject and inline blank nodes, and the procedure must stream chunks, all through the same `QuadSink` interface.
- **Choice.** The serializer guarantees: graphs in sequence (default graph first, never reopened); quads grouped by subject; the blank nodes describing a subject's objects emitted right after it, before the next IRI subject, each referenced once. It calls `flush()` after each complete subject block.
- **Why.** A pretty printer can then work on one subject block at a time, so memory stays bounded, and `flush()` marks the natural chunk boundaries.

## Procedure

### D16. Nodes inside maps (reversed by SPEC v0.3)

- **Context.** SPEC v0.2 §3 looked inside `LIST` and `PATH` values only. A `MAP` can contain nodes (`RETURN {n: n}`).
- **Choice (v0.2).** Maps were ignored, as a literal reading of §3.
- **Now.** SPEC v0.3 §3 makes maps containers: nodes and relationships in map values are in scope, recursively. Conformance case 05 checks it.

### D17. Config validation

- **Choice.** Unknown config keys are an error, as is a missing or blank `base`, a `format` other than `'nquads'` / `'trig'`, or an empty `snapshotId`. The default `snapshotId` uses the same instant as `prov:generatedAtTime` (D14).
- **Why.** A typo such as `snapshotID` would otherwise be silently ignored.

### D18. Procedure class loading in Neo4j 2026.x

- **Observed** (2026.09). `plugins/*` is on the server classpath, and the classes carrying procedure annotations are also loaded again by a separate `ProcedureClassLoader`. All other classes of the jar resolve through the parent loader. The two sides are different runtime packages, so any package-private access from the `@Procedure` class fails with `IllegalAccessError`, including access to compiler-generated classes such as enum switch maps.
- **Choice.** `ExportProcedure` only delegates to the public `SnapshotExport.run(...)`; the result record `Chunk` is a public top-level class.

### D19. Streaming and chunks

- **Choice.** The scope query is collected first (element IDs only, sorted). The procedure then returns a lazy stream: metadata, vocabulary (one pass over the elements), then one step per node and per relationship, each loaded by element ID in the same transaction. Text is handed out in chunks of about 16 KiB, always cut at a subject block boundary. Concatenating the chunks gives the document.
- **Why.** Memory use is bounded by the ID sets, not by the size of the output. Element properties are read twice (vocabulary pass, then writing), which costs time but keeps memory flat.

### D20. `lpg:database`

- **Choice.** The name of the database the procedure runs in (`GraphDatabaseService.databaseName()`).

## Schema graph

### D21. Schema graph (SPEC §8) not implemented

- **Context.** The schema graph describes the GRAPH TYPE. Milestone 3 investigated it on Neo4j 2026.09.0 Enterprise; GRAPH TYPE handling was then removed from the backlog.
- **Choice.** No schema graph, no `lpg:schemaGraph` triple, no `includeSchema` config key. The procedure accepts `base`, `format` and `snapshotId` only. Conformance case 01 (SPEC §10, which needs a graph type) is not in the suite; case 02 covers the same data without one.
- **Observed** (useful when this comes back):
  - `SHOW CURRENT GRAPH TYPE` returns one column, `specification`: the graph type as a Cypher string (`{ (:Part => {...}), ... }`). `"{}"` when nothing is defined.
  - The graph type is stored as constraints: `ALTER CURRENT GRAPH TYPE SET` reports "Added N constraints". `SHOW CONSTRAINTS` gives structured rows: `type` (`NODE_PROPERTY_TYPE`, `NODE_PROPERTY_EXISTENCE`, `NODE_LABEL_EXISTENCE` for implied labels, `RELATIONSHIP_SOURCE_LABEL` / `RELATIONSHIP_TARGET_LABEL` for endpoints, `NODE_KEY`, `NODE_PROPERTY_UNIQUENESS`, the relationship variants), `labelsOrTypes`, `properties`, `enforcedLabel`, `propertyType` (e.g. `LIST<STRING NOT NULL>`, `STRING | INTEGER`, `VECTOR<FLOAT32 NOT NULL>(3)`), and `classification`: `dependent` for constraints generated by element types (which identifies identifying labels and types), `independent` / `undesignated` for `CONSTRAINT` clauses.
  - Every constraint belongs to the current graph type, including one made with a plain `CREATE CONSTRAINT`. Indexes do not.
  - A node element type must have a property type or an implied label (`(:A =>)` is rejected), so every identifying label leaves at least one dependent constraint. Relationship endpoints are optional (`()-[:R => {...}]->()`). `ANY` and `VECTOR ... NOT NULL` are rejected as property types.
  - **Blocker:** inside a `Mode.READ` procedure, `SHOW CONSTRAINTS` and `SHOW CURRENT GRAPH TYPE` fail ("not allowed ... overridden by READ"). The public schema API (`tx.schema().getConstraints()`) gives the constraint type, owner, keys and property types, but neither the implied/endpoint label nor the classification. The options were: the kernel's internal `ConstraintDescriptor` in the same transaction; `SHOW CONSTRAINTS` in a second transaction; or a non-READ procedure mode.
  - The public `PropertyType` enum has a `UUID` member, which SPEC §7.4 doesn't map.
- **Spec.** v0.3 made §8 optional and allows reading the schema in a separate transaction, with a documented consistency caveat. GRAPH TYPE handling is still out of the backlog here.

### D24. `UUID` values

- **Observed** (2026.09). `uuid('3F2504E0-…')` returns a value of type `UUID` (`randomUUID()` still returns a `STRING`). It can be stored as a property, also in lists (`LIST<UUID NOT NULL>`). The embedded API returns `java.util.UUID`, and arrays of it for lists.
- **Choice.** `"<uuid>"^^lpg:uuid` with `UUID.toString()` as lexical form, which is the lower-case 8-4-4-4-12 form of SPEC v0.3 §6. Conformance case 07 checks a single value and a list.

## Conformance suite

### D22. Comparing outputs with element IDs and timestamps

- **Context.** Element IDs are chosen by the database and `prov:generatedAtTime` is the export instant, so neither can be written into `expected.trig`. Yet two runs must be byte-identical.
- **Choice.** Before the isomorphism check, element IRIs become blank nodes, once each has been checked against its `lpg:elementId`. `lpg:elementId` literals and `prov:generatedAtTime` (after a datatype check) become constants. The check that two runs give identical bytes, and the check that the N-Quads and TriG outputs are isomorphic, mask the `prov:generatedAtTime` literal only (two exports may fall in different seconds). The procedure has no option to fix the timestamp.
- **Why.** These rules check everything that doesn't depend on the database instance, and need no test-only feature in the procedure. Element IRIs are compared up to renaming, which is exactly what SPEC §5 says they guarantee within one snapshot.

### D23. Conformance case 1

- **Context.** The prompt asks for the full SPEC §10 example with its GRAPH TYPE as case 1.
- **Choice.** Not included, because GRAPH TYPE handling was dropped (D21). Case 02 has the same data and scope, so the data graph of §10 is still covered.
