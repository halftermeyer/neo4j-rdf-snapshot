MATCH (p:Part {partNumber: 'P-001'})-[r:USES]->(m) RETURN p, r, m
