# Decisions

Choices made where `SPEC.md` is silent or ambiguous. One entry per decision: context, options, choice, why.
Entries marked **[spec]** propose a change to the spec.

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

- **Context.** List cells, property specs and constraints are blank nodes. Output must be byte-identical across runs.
- **Options.** (a) A counter in emission order. (b) A label derived from context.
- **Choice.** (b): `b` + the first 128 bits (32 hex digits) of SHA-256 over `graph IRI ␀ owner ␀ key ␀ position`, where:
  - list cell: owner = subject term (IRI, or `_:label` for a list hanging from a blank node), key = predicate IRI, position = index in the list;
  - property spec: owner = label/type IRI, key = `lpg:hasProperty` + ` ` + property key IRI, position 0;
  - constraint: owner = label/type IRI, key = `lpg:hasConstraint` + ` ` + kind + ` ` + key IRIs in order, position 0.
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

### D6. Offsets with a seconds component **[spec]**

- **Context.** Neo4j accepts offsets such as `+01:00:30`. XSD offsets have minutes only.
- **Options.** (a) Emit `+01:00:30` (ill-typed literal). (b) Drop the seconds. (c) Skip the property.
- **Choice.** (b).
- **Why.** Keeps a valid literal and loses at most 59 seconds of offset, which almost never occurs in practice.
- **Spec change.** Add this to the known losses of §11.

### D7. Durations with mixed signs **[spec]**

- **Context.** SPEC §11: a `DURATION` with mixed-sign components "may not be representable". Neo4j stores months, days, seconds and nanoseconds with nanoseconds always in `[0, 1e9)`, so `-0.5s` is seconds `-1`, nanos `500000000`.
- **Options.** (a) Emit an ill-typed literal. (b) Skip the property. (c) Normalize days into seconds (wrong: a day is not always 86400 s).
- **Choice.** Combine seconds and nanoseconds first. If months, days and the combined seconds then all have the same sign (or are zero), emit `[-]PnYnMnDTnHnMn.nS` with years = months / 12, hours and minutes taken from seconds, zero components omitted, `PT0S` for zero. Otherwise skip the property, like `VECTOR`. A list containing such a duration is skipped as a whole.
- **Why.** Ill-typed literals break downstream tools. Skipping is the known loss the spec already announces; (c) would change the value.
- **Spec change.** §11 should say explicitly that such properties are not serialized.

### D8. Points

- **Context.** SPEC §7.4: WGS-84 → `geo:wktLiteral` "with CRS84"; Cartesian → no CRS.
- **Choice.**
  - 2D WGS-84 (SRID 4326): `<http://www.opengis.net/def/crs/OGC/1.3/CRS84> POINT(lon lat)`.
  - 3D WGS-84 (SRID 4979): `<http://www.opengis.net/def/crs/OGC/0/CRS84h> POINT Z(lon lat h)`. CRS84 is two-dimensional; CRS84h is its 3D (ellipsoidal height) counterpart in GeoSPARQL 1.1.
  - Cartesian 2D / 3D: `POINT(x y)` / `POINT Z(x y z)`.
  - Coordinates in plain decimal notation, shortest round-trip digits, no trailing zeros (`2.0` → `2`, `1e-7` → `0.0000001`).
- **Why.** CRS84 is written explicitly, as the spec asks. Plain decimals are the most widely parsed WKT number form.

### D9. Cartesian points read as CRS84 **[spec]**

- **Context.** GeoSPARQL says a `wktLiteral` without CRS IRI is in CRS84. A Cartesian point without CRS will therefore be read as longitude/latitude by any GeoSPARQL engine.
- **Choice.** Follow the spec (no CRS) for now.
- **Spec change.** Resolve the §12 open question with an `lpg:` CRS IRI for Cartesian points (e.g. `lpg:Cartesian2D`, `lpg:Cartesian3D`), or a dedicated literal datatype, so the value is not silently misread.

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
  1. Default graph: snapshot metadata (`a`, `lpg:database`, `lpg:scope`, `lpg:schemaGraph`, `prov:generatedAtTime`).
  2. Data graph: labels, then relationship types, then property keys, each sorted by name. Then nodes, then relationships, each sorted by element ID.
  3. Schema graph (see the schema section).

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
