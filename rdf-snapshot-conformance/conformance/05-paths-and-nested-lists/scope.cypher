MATCH p = (:Step {n: 1})-[:NEXT]->{2}(:Step {n: 3}) MATCH (last:Step {n: 4}), (o:Other) RETURN p, [nodes(p), [relationships(p)[0]]] AS nested, {node: last} AS map, o.n AS number, 'text' AS string
