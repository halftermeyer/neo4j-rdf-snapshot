# 08-name-encoding

Labels, a relationship type and property keys whose names need percent-encoding (SPEC §5, DECISIONS D1).

Checks:

- space → `%20`, `/` → `%2F`, `#` → `%23`, `%` → `%25`, non-ASCII → UTF-8 bytes (`è` → `%C3%A8`, `部品` → `%E9%83%A8%E5%93%81`);
- `:` is kept (`ns:local`), as are `.` and `~`;
- every term keeps its original name in `lpg:name`;
- the label `Name` and the property key `Name` get different IRIs (separate namespaces);
- names that are not valid TriG prefixed names (`.hidden`, `x~y`) still round-trip: the TriG output writes them as full IRIs.
