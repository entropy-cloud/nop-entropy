# 从数学的角度看ORM引擎的设计

## 对象-关系映射

关系数据库模型：
```
  Database = Set of Table
  Table = Set of Column
```

对象-关系映射：
```
  OrmModel -> Database
  EntityClass -> Table
  EntityProperty -> Column
```

任何对象都自动导出它的差量

自动推导得到增删改查语句

```
 EntityModel -> Insert + Update + Select + Delete
```

## 超越关系模型的对象模型

```
  a.b.c = a join b + b join c
```

Resolver(A) = Resolver(B) + Resolver(C)。如果是Tree结构分析，则对应于GraphQL
