# NopORM vs MyBatis Differences

## Declarative vs Imperative Styles

In ORM, the abstract model is
Entities are data themselves. Modifying entity attributes means modifying data directly, without calling `dao.update`. Since MyBatis is half-functional as an ORM, it requires using actions to express save semantics. Calling `save` twice results in two database accesses.  
In declarative style, you call `saveOrUpdate` once to indicate that the entity needs to be saved. In reality, only during `session.flush()` will the database be updated.

MyBatis is not beneficial for encapsulation. For example:
- If `methodA` modifies some data that needs saving and `methodB` also modifies the same data, calling `save` in `methodA` alone would suffice.
- Calling `saveOrUpdate` separately for both methods results in two separate database updates. However, if called simultaneously, it should be merged into a single update. But MyBatis cannot achieve this, leading to loss of save semantics' compositeness.

## Automatic Dependency Sorting During Updates

Using MyBatis often leads to database lock conflicts in complex scenarios. For example:
- Thread A updates A then B.
- Thread B updates B then A.

NopORM, however, checks all entities in memory during `session.flush()`, calculates the difference set, generates update SQL based on dependencies and primary key order, ensuring updates are always applied in the correct sequence: A first, then B.

## Minimizing Database Connection Usage

NopORM supports optimistic concurrency, so read operations can be performed without transactions. Transactions should only be started during `OrmSession.flush()`, minimizing database connection holding time.

Additionally, NopORM's `ITransactionTemplate` helper class provides lazy initialization for connections, opening them only when needed. Background operations not involving the database can be performed while the connection is unused, further reducing its占用。

## JDBC Batch Optimization

During `OrmSession.flush()`, NopORM automatically utilizes JDBC's built-in batch mechanism to execute multiple updates in one call, significantly improving performance.

## Primary Key Modification Prohibition

Once an entity is saved in NopORM, its primary key cannot be modified. In standard relational database design, primary key changes are generally highly discouraged as well.

```javascript
var entity = dao.newEntity();
entity.setId("1");
entity.setName("test");
dao.saveEntity(entity);
entity.setId("33"); // This modification is incorrect and will have no effect
dao.saveOrUpdateEntity("33");
```
