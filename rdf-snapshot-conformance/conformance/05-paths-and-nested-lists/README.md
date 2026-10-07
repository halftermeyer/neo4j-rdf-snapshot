# 05-paths-and-nested-lists

Scope semantics (SPEC §3) with a path, nested lists, a map and values that must be ignored.

Checks:

- a `PATH` is decomposed into its nodes and relationships, and has no resource of its own;
- nodes and relationships inside nested lists are found;
- elements returned several times (in the path and in the lists) appear once;
- a node inside a `MAP` value is in scope (Step 4), but the relationship from Step 3 to Step 4 is not, since no column returns it;
- other values (a number, a string) are ignored.
