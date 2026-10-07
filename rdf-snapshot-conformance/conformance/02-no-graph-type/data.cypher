CREATE (:Part {partNumber: 'P-001', mass: 1.2, tags: ['steel', 'EU', 'steel']})
  -[:USES {quantity: 4}]->
  (:Substance:Material {casNumber: '335-67-1'});
