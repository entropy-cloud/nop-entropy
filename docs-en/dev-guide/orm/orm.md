# Execution Principles

NopORM is a full-featured ORM engine similar to Hibernate. It manages all entity objects loaded into memory through OrmSession. Within the same session, each entity is loaded by id from the database only once, and the resulting entity object is cached in the session.

```java
MyEntity entity = dao.getEntityById("3");
List<MyEntity> entities = dao.findAll();
assertTrue(entityes.contains(entity));
```

The entity corresponding to the same id is guaranteed to be a unique object. In the example above, findAll queries entity data from the database, and before returning it to the caller it checks whether an entity with the same id already exists.
If it already exists in the session, the existing entity object is used to replace the entity object loaded from the database.

> Note: Even if the data in the database has been modified and is inconsistent with the entity data in the session, we still only use the object in the session and automatically discard the data returned from the database. This, in a sense, elevates the transactional guarantees at the data layer and effectively achieves repeatable read by id.

* You do not need to call session.update(entity) or dao.updateEntity(entity) to modify an entity, because NopORM automatically tracks changes to all entities in the session, and during session.flush
  it will synchronize changes automatically to the database.
* Adding an entity to a collection implicitly means the entity should also be synchronized to the database. Therefore, after calling parent.getChildren().add(child), there is no need to call dao.saveEntity(child) to persist the child entity.
* Removing an entity from a collection will automatically delete that entity, unless its owner property is set to null.

```java
parent.getChildren().remove(child);
// child.setParent(null);
```

If you do not call child.setParent(null), removing child from the parent’s children collection indicates that child should be deleted from the database. If you set the child’s parent to null, it indicates the association between the parent entity and the child entity is being removed.

## No Support for Auto-Increment Primary Keys

The NopOrm engine does not support MySQL’s built-in auto-increment primary key feature. The specific reasons are as follows:

1. Databases such as Oracle do not support auto-increment primary keys. The Nop platform favors approaches compatible with multiple mainstream databases to facilitate product migration across databases.
2. Supporting auto-increment primary keys is challenging in distributed databases.
3. It is difficult to maintain a unified auto-increment primary key strategy when sharding databases and tables.
4. NopOrm requires entities to have an id before being persisted to the database. Many related design features depend on the entity already having an id, so we cannot wait to insert the entity and then obtain the id.

On the Nop platform, as long as the primary key column is marked with the seq tag, ids are automatically generated via the ISequenceGenerator interface, without relying on the database’s auto-increment mechanism.

The nop-sys-dao module provides a default implementation of ISequenceGenerator. It uses the nop_sys_sequence table to record sequence usage; by default, all entities share a single "default" sequence record, though you can assign a dedicated sequence to each entity.
You can also use the nop_sys_sequence table to specify random generation strategies such as UUID, or leverage database-native sequence statements to return monotonically increasing ids.

## Built-in ORM Features

NopORM comes with many commonly used business features:

1. Field encryption: automatically encrypt when saving to the database and automatically decrypt when loading into memory
2. Field masking: for data such as card numbers, automatically replace most content with asterisks when returned via GraphQL, retaining only the last few digits.
3. Logical delete: deletion operations correspond only to setting a deleted flag. This mechanism is implemented at the ORM layer, and EQL object query syntax automatically adds the corresponding filters
4. Maker-Checker approval mechanism: submitting a modification triggers an approval workflow; changes take effect only after approval (this mechanism is not fully implemented yet; the interfaces are already in place)
5. Extension fields: add extension fields to entities without altering tables; you can sort and filter by extension fields. Using extension fields in code is largely the same as native fields; they are stored in a vertical table, and the EQL query language automatically pivots between vertical and horizontal representations
6. Database and table sharding, multi-tenant filtering
7. Automatically record modifier and modification time
8. A trigger model allows implementing database-like triggers at the application layer; a trigger is fired whenever an entity is saved.
9. Automatically parse JSON fields via the Component mechanism; similarly, multiple fields can be encapsulated as a Component object to provide custom enhancement methods.

<!-- SOURCE_MD5:34bc61dc1b518891a51421aa81c3171a-->
