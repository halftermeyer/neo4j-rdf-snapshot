CREATE (:Person {name: 'Ann'})-[:OWNS {since: 2020}]->(:Car {plate: 'AB-123'}),
       (:Person {name: 'Bob'});
