# Basic Conventions

1. `findFirst` prefix indicates finding the first entry, such as `findFirstByExample`, `findFirstByQuery`, etc.
2. `findAll` prefix indicates returning all entries that meet the condition, such as `findAllByExample`, `findAllByQuery`, etc.
3. `findPage` prefix indicates returning paginated results, such as `findPageByExample`, `findPageByQuery`, etc.
4. `loadEntityById` follows Hibernate conventions; it only constructs a Proxy object in memory and does not actually query the database.
5. `getEntityById` follows Hibernate conventions; it automatically loads the Proxy object to ensure entity data is available in memory. If no corresponding entity data exists in the database, it returns `null`.
6. `require` prefix indicates that the result must not be `null`; if `null` is returned, an exception will be thrown.
7. `batch` prefix indicates bulk operations, such as `batchDeleteEntities`, which performs bulk deletion of entities.

# Getting the Dao Object

```java
@Inject
IDaoProvider daoProvider;

Dao = daoProvider.daoFor(MyEntity.class);
```

The `daoProvider` centralizes access to all `dao` objects within the system. You can retrieve a specific `dao` object using various methods, such as by entity name, entity Java class, or table name.

# Retrieving Dao in CrudBizModel Derivatives

1. Retrieve the current entity's dao: `this.dao()`
2. Retrieve the dao for a specific entity type: `this.daoFor(MyEntity.class)`

# Common Functions

## 1. Querying by Property

```java
MyEntity example = dao.newEntity();
example.setMyField("a");
// Find the first matching entry
MyEntity entity = dao.findFirstByExample(example);

// Using require to ensure no null is returned
entity = dao.requireFirstByExample(example);
```

## 2. Constructing Complex Queries

```java
QueryBean query = new QueryBean();
query.setFilter(
    and(
        eq(MyEntity.PROP_NAME_myField, "a"),
        gt(MyEntity.PROP_NAME_myStatus, 3)
    )
);
query.setLimit(5);

List<MyEntity> list = dao.findPageByQuery(query);
```

The `FilterBeans` class provides helper functions like `and`, `or`, `eq`, `gt`, etc., which can be used to construct complex filter conditions.

## 3. Creating and Saving Entities

```java
MyEntity entity = dao.newEntity();
// ... (set properties)
dao.saveEntity(entity);
```

In general, it is recommended to use `dao.newEntity()` instead of directly instantiating new MyEntity objects. This is because when extending entities through the Delta customization module, `dao.newEntity()` may return extended classes rather than the base entity class.

For example:
- In the Delta module, you can define a derived class like `class MyEntityEx extends MyEntity`.
- Configuring the ORM model in this way ensures that `test.MyEntity` maps to `MyEntityEx`, so `dao.newEntity()` will return `MyEntityEx`.

```xml
<orm>
    <entity name="test.MyEntity" class="test.MyEntityEx">
        <!-- ... -->
    </entity>
</orm>
```

## 4. Updating Entities

```java
dao.saveOrUpdateEntity(entity);
```

This method updates the entity in the database, preserving existing data and only modifying the changes made to the entity.

According to the general principles of ORM engines, if you are only modifying entity attributes, there is no need to call the `dao.updateEntity` method. NopORM uses `OrmSession` to manage all entity objects, and during `session.flush`, it automatically checks whether any objects in the current session have been modified. If so, those modifications will be synchronized with the database.

The `dao.saveOrUpdateEntity` method determines if the entity is a newly created (Transient) entity based on its state markings. If it is identified as a new entity, `saveEntity` will be called; otherwise, `updateEntity` will be invoked.


## 5. Delete Entity
```java
dao.deleteEntity(entity);
```

When deleting an entity, if the associated child entities have cascade-delete enabled, those child entities will be automatically deleted as well.


## 6. Batch Load Properties
JPA often encounters a performance issue known as the "N+1 problem" due to lazy loading of associated objects. The `IEntityDao` provides a `batchLoadProperties` function that allows bulk loading of all related properties at once.
```java
List<MyEntity> list = dao.findAll();
dao.batchLoadProps(list, Arrays.asList("parent","children","parent.parent"));
```

The internal implementation is similar to GraphQL's `BatchDataLoader`, with special optimizations made for ORM entities.

