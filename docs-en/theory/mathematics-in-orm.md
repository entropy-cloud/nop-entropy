# From a Mathematical Perspective: Design of ORM Engines


## Object-Relationship Mapping


## Relation Database Model:
```
Database = Set of Table
Table = Set of Column
```


```
OrmModel -> Database
EntityClass -> Table
EntityProperty -> Column
```

Any object automatically derives its differences.

Automatic derivation leads to INSERT, UPDATE, SELECT, and DELETE statements.

```
EntityModel -> Insert + Update + Select + Delete
```



```
a.b.c = a join b + b join c
```

Resolver(A) = Resolver(B) + Resolver(C). If it is a Tree structure analysis, it corresponds to GraphQL.

