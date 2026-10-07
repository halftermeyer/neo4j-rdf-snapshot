# 02-no-graph-type

The data of SPEC §10, in a database with no GRAPH TYPE.

Checks:

- no schema graph is emitted;
- the snapshot metadata has no `lpg:schemaGraph` triple;
- the data graph is the same as with a graph type (compare with `01-spec-example`).
