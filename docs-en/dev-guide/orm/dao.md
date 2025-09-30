`IEntityDao` interface provides CRUD encapsulation for single-entity objects. In general, when developing CRUD features, there is no need to write a Service class, nor to wrap SQL statements as in MyBatis.
The methods provided on EntityDao are already rich enough to accomplish fairly complex tasks.

> The generated code has already implemented GraphQL-layer CRUD services via `CrudBizModel`. In most cases you only need to subclass `CrudBizModel` and apply minor customizations; there is no need to manually implement the full CRUD logic.

# Basic conventions

1. The `findFirst` prefix means “find the first record,” for example `findFirstByExample`, `findFirstByQuery`, etc.
2. The `findAll` prefix means “return all records that match the condition,” for example `findAllByExample`, `findAllByQuery`, etc.
3. The `findPage` prefix means “return a paginated set of records that match the condition,” for example `findPageByExample`, `findPageByQuery`, etc.
4. `loadEntityById` follows Hibernate’s convention: it only builds a Proxy object in memory and does not actually query the database.
5. `getEntityById` follows Hibernate’s convention: it will automatically initialize the Proxy to ensure the entity data is available in memory; if the database has no corresponding entity, it returns `null`.
6. The `require` prefix indicates the result must not be `null`; if it is `null`, an exception will be thrown.
7. The `batch` prefix indicates bulk operations, for example `batchDeleteEntities` means deleting entities in batch.

## Obtaining the Dao object

```java
@Inject
IDaoProvider daoProvider;

dao = daoProvider.daoFor(MyEntity.class);
```

`daoProvider` centrally manages all `dao` objects in the system and lets you obtain the corresponding `dao` by entity name, entity Java class, table name, etc.

## Obtaining the dao in subclasses of CrudBizModel

1. Dao for the current entity: `this.dao()`
2. Dao for a specified entity type: `this.daoFor(MyEntity.class)`

## Common functions

## 1. Query by properties

```java
MyEntity example = dao.newEntity();
example.setMyField("a");
// Find the first one that matches the criteria
MyEntity entity = dao.findFirstByExample(example);

// "require" means it throws an exception if nothing is found
entity = dao.requireFirstByExample(example);
```

## 2. Build complex query criteria

```java
QueryBean query = new QueryBean();
query.setFilter(and(eq(MyEntity.PROP_NAME_myField,"a"), gt(MyEntity.PROP_NAME_myStatus,3)));
query.setLimit(5);

List<MyEntity> list = dao.findPageByQuery(query);
```

The `FilterBeans` class defines helper functions such as `and/or/eq/gt`, etc., which can be used to build filter conditions. `gt` means greater than, `ge` means greater than or equal to, `lt` means less than, `le` means less than or equal to, and `eq` means equal to.

## 3. Create and save an entity

```java
MyEntity entity = dao.newEntity();
...
dao.saveEntity(entity);
```

In general, you should create entities using `dao.newEntity()` rather than directly calling `new MyEntity()`. This is because when you extend the entity class via Delta customization, `dao.newEntity()` may return an instance of the extended class rather than the current entity class. For example, in a Delta module you can define `class MyEntityEx extends MyEntity`, and then configure the ORM model so that the entity name `test.MyEntity` maps to the Java class `MyEntityEx`. In that case, `dao.newEntity()` actually returns `MyEntityEx`.

```xml
<orm>
    <entity name="test.MyEntity" class="test.MyEntityEx" >...</entity>
</orm>
```

## 4. Modify an entity

```java
dao.saveOrUpdateEntity(entity);
```

According to common ORM engine principles, simply changing entity properties does not require calling `dao.updateEntity`. NopORM manages all entity objects via `OrmSession`, and when `session.flush` is executed it will automatically check whether objects in the current session have been modified; if so, the changes are synchronized to the database. `dao.updateEntity()` is basically a no-op and only performs some state checks.

`dao.saveOrUpdateEntity` determines, based on the entity’s state flags, whether it is a newly created (Transient) entity; if so, it calls `saveEntity`, otherwise it calls `updateEntity`.

## 5. Delete an entity

```java
dao.deleteEntity(entity);
```

When deleting an entity, if its associated child collections are configured with `cascade-delete`, the elements in those child collections will also be deleted automatically.

## 6. Batch-load properties

A common JPA performance issue is the N+1 problem caused by lazy loading of associated objects. `IEntityDao` provides a `batchLoadProperties` function to load all associated properties in one go.

```java
List<MyEntity> list = dao.findAll();
dao.batchLoadProps(list, Arrays.asList("parent","children","parent.parent"));
```

The internal implementation is somewhat similar to GraphQL’s `BatchDataLoader`, but it is specially optimized for ORM entities.

<!-- SOURCE_MD5:66e2705ad432fdd87c8ffd1bc8593958-->
