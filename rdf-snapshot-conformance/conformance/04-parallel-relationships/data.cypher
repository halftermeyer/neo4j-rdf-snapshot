CREATE (a:Station {name: 'A'}), (b:Station {name: 'B'}),
       (a)-[:LINK {line: 1}]->(b),
       (a)-[:LINK {line: 1}]->(b),
       (a)-[:LINK {line: 2}]->(b),
       (b)-[:LINK]->(a);
