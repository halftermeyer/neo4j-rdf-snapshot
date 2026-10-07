# 02-no-graph-type

The data of SPEC §10, in a database with no GRAPH TYPE.

Checks:

- no schema graph is emitted;
- the snapshot metadata has no `lpg:schemaGraph` triple;
- the data graph matches SPEC §10 (element IRIs up to renaming, database `conformance`, `1.2E0` in canonical form, DECISIONS D4).

This implementation never emits a schema graph (DECISIONS D21), so this is also its output for databases that do have a graph type.
