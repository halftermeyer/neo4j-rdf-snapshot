# 05-paths-and-nested-lists

Scope semantics (SPEC §3) with a path, nested lists and values that must be ignored.

Checks:

- a `PATH` is decomposed into its nodes and relationships, and has no resource of its own;
- nodes and relationships inside nested lists are found;
- elements returned several times (in the path and in the lists) appear once;
- a node inside a `MAP` is ignored: §3 only looks inside lists and paths (Step 4 is not in scope);
- other values (a number, a string) are ignored.
