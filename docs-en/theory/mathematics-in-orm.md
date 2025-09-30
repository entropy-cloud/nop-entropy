# Designing an ORM Engine from a Mathematical Perspective

## Object-Relational Mapping

Relational database model:
```
  Database = Set of Table
  Table = Set of Column
```

Object-relational mapping:
```
  OrmModel -> Database
  EntityClass -> Table
  EntityProperty -> Column
```

Any object automatically exports its Delta

Automatically derive the INSERT/UPDATE/SELECT/DELETE statements

```
 EntityModel -> Insert + Update + Select + Delete
```

## Object Models Beyond the Relational Model

```
  a.b.c = a join b + b join c
```

Resolver(A) = Resolver(B) + Resolver(C). For tree-structured analysis, this corresponds to GraphQL

<!-- SOURCE_MD5:c180a5f8c15870123b339d470f0357c1-->
