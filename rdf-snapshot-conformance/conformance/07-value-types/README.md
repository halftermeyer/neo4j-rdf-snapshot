# 07-value-types

One node with a property of every Cypher value type of SPEC §7.4.

Checks the literal of each type, in the canonical lexical forms of DECISIONS D4 to D10:

- `STRING` with characters that need escaping and non-ASCII characters;
- `INTEGER` at the 64-bit limit, negative;
- `FLOAT`: ordinary, tiny, `NaN`, `INF`, `-0.0`;
- `DATE` (including a negative year), `LOCAL TIME` (seconds always written), `ZONED TIME`, `LOCAL DATETIME`;
- `ZONED DATETIME` with a named zone (`Europe/Paris`): the offset is kept, the zone name is dropped (SPEC §11);
- `DURATION`, positive and negative;
- `POINT`: WGS-84 2D and 3D, Cartesian 2D and 3D;
- byte array, as `xsd:base64Binary`. Cypher has no byte array literal, so the value comes from `params.json`: `{"base64": "..."}` stands for a byte array parameter;
- lists of dates and of floats.

Not serialized, and their keys not declared:

- `embedding`, a `VECTOR` (SPEC §7.4);
- `mixedDuration`, `P1M-1D`: months and days have opposite signs, so there is no `xsd:duration` for it (SPEC §11, DECISIONS D7).
