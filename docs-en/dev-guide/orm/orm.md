# Execution Principle

NopORM is a complete ORM engine similar to Hibernate, and it uses the OrmSession to manage all loaded entity objects in memory. Within the same session, each entity object will be loaded from the database only once, based on its ID. Once loaded into memory, the entity object will be cached within the session.

```java
MyEntity entity = dao.getEntityById("3");
List<MyEntity> entities = dao.findAll();
assertTrue(entities.contains(entity));
```

Each entity with the same ID is guaranteed to be a unique object. In the example above, `findAll` retrieves entity data from the database and returns it to the caller. The caller then checks whether an entity with the same ID already exists in the session. If one exists, it replaces the newly retrieved entity data from the database.

> **Note:** Even if the database has been modified and the session's entity data does not match, we will only use the session's entity. This effectively enhances the transaction level by ensuring read consistency based on the ID.
>
> **Note:** Updating an entity does not require calling `session.update(entity)` or `dao.updateEntity(entity)`, as NopORM automatically tracks all modifications within the session and synchronizes them to the database during a flush operation. Adding an entity to a collection implies that the entity itself will be saved to the database, so there is no need to call `dao.saveEntity(child)` after adding it.
>
> **Note:** Deleting an entity from a collection will automatically delete it from the database unless its `owner` property has been set to null.

```java
parent.getChildren().remove(child);
// child.setParent(null);
```

If `child.setParent(null)` is not called, the entity will be deleted from the parent's children collection and also removed from the database. If `child.setParent(null)` is called, it breaks the association between the parent and the child entity.

## Does Not Support Auto-Increment

The NopOrm engine does not support MySQL's built-in auto-increment feature. The reasons are as follows:

1. Databases like Oracle do not support auto-increment.
2. Distributed databases have difficulty supporting auto-increment.
3. When splitting databases, maintaining uniform auto-increment is challenging.
4. NopOrm requires entities to have an ID before they are saved to the database, as many design aspects rely on this property.

In Nop platform, if a primary key is annotated with `@Sequence`, NopOrm will use the `ISequenceGenerator` interface to generate sequential IDs automatically. By default, all entities share a single sequence in `nop_sys_sequence`. You can also configure individual sequences for each entity by defining them in `nop_sys_sequence`.

Additionally, you can specify a UUID generator or use database-supported sequences.

## Built-In ORM Features

NopORM includes many useful built-in features:

1. **Encryption of Fields**: Automatically encrypts fields when saving to the database and decrypts them when loading into memory.
2. **Masking of Fields**: Masks sensitive information (like credit card numbers) with asterisks (*) during retrieval, retaining only the last few digits.
3. **Logical Deletion**: Represents deletion by setting a `deleted` flag instead of physically removing records from the database.
4. **MakerChecker Mechanism**: Triggers an approval process before any modification is committed to the database. The mechanism is not fully implemented yet but has its own interfaces in place.
5. **Custom Fields**: Adds expandable fields without requiring additional tables, allowing for sorting and filtering by these fields using EQL.
6. **Multi-Tenant Filtering**: Automatically filters records based on the tenant ID, enhancing performance for multi-tenant applications.
7. **Track Changes**: Records who made changes and when they were made, along with the modification time.
8. **Triggers**: Allows defining database triggers at the application layer, providing similar functionality to database-native triggers.
9. **Components**: Enables encapsulation of multiple fields into a `Component` object for custom processing.
