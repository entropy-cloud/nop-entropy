# Differences Between NopORM and MyBatis

## Declarative vs. Imperative

The abstract model of an ORM is
an entity is the data itself; modifying an entity’s properties means modifying the data, and you do not need to call dao.update. Because MyBatis is a half-baked ORM, it forces you to express the save semantics via explicit actions. Calling save twice will trigger two database accesses. An ORM is declarative: calling saveOrUpdate 100 times merely marks the entity as needing to be saved; the database is actually updated only once when session.flush occurs.

MyBatis’s style of ORM works against encapsulation. For example, methodA modifies some data that needs to be saved, and methodB modifies the same data that also needs to be saved. When you call methodA alone, exactly one save should be performed,
and calling methodB alone should update the database once. However, if both methodA and methodB are invoked together, their database modifications should be merged so that only one database update occurs. MyBatis cannot achieve this, ultimately breaking the composability of the save semantics.

## Automatic Dependency Ordering When Updating the Database

With MyBatis, database lock conflicts often occur in complex scenarios. For example, one thread updates A then B, while another updates B then A. In contrast, NopORM, at session.flush time, checks which in-memory entities have been modified, computes the modification Delta, and generates SQL for database changes based on the Delta. During this computation it sorts all entities according to table dependency relationships and primary key values, and executes SQL in that prescribed order, ensuring that updates are always applied A before B.

## Reduce Database Connection Hold Time

NopORM has built-in optimistic locking, so executing reads outside a transaction will not affect database consistency. We can open a transaction only during OrmSession.flush, without holding a database connection throughout the entire read phase.

The ITransactionTemplate helper provided by NopORM also implements lazy connection acquisition: a transaction is started only when we first actually need a database connection. While a backend service function is running, we can first perform work unrelated to database access, further reducing how long a database connection is held.

## JDBC Batch Optimization

OrmSession.flush automatically leverages JDBC’s built-in batch mechanism to execute operations in bulk, greatly improving performance.

## Primary Keys Are Immutable

Once an entity has been saved in NopORM, its primary key cannot be modified. In typical relational database design, changing primary keys is also strongly discouraged.

```javascript
var entity = dao.newEntity();
entity.setId("1");
entity.setName("test");
dao.saveEntity(entity);
entity.setId("33"); // This kind of modification is incorrect and will not actually take effect
dao.saveOrUpdateEntity("33");
```
<!-- SOURCE_MD5:8f48bde70c9aeb429ef904f3924b7c0f-->
