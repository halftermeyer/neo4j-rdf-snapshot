# 04-parallel-relationships

Four `LINK` relationships between the same two nodes, two of them identical (same direction, same properties).

Checks:

- every relationship is its own resource, even when identical to another one (SPEC §7.4): nothing collapses;
- direction is kept through `lpg:source` / `lpg:target`;
- the scope returns relationships only, so the nodes come in through the closure rule.
