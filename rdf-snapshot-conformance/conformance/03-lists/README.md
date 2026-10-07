# 03-lists

List properties (SPEC §7.5).

Checks:

- lists are RDF collections, in order, with duplicates kept (`tags`, `counts`, `flags`);
- an empty list is `rdf:nil` (`empty`), so it stays distinct from an absent property (the second node has no `empty`).
