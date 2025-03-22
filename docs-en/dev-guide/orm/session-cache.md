# Level Cache

NopORM is similar to Hibernate, as a complete ORM engine designed with a session concept. All entity data retrieved through the ORM engine is cached in the current OrmSession object.
When the `session.flush()` function is called, NopORM will recursively traverse all entities in the session. If it detects that an entity is dirty (either through `entity.orm_isDirty()` or if the entity is in the `transient` or `deleting` state), it will generate the corresponding SQL statements to update the database. The generated SQL statements will attempt to use JDBC's batch optimization mechanism to improve database performance, especially when transactions are enabled. For more details, refer to `JdbcBatcher.java`.


## Entity-level caching using OrmSession

Within an open OrmSession, all retrieved entity data is cached in the session. **Ensure that a primary key maps to exactly one Java entity**. If two SQL queries return the same entity data, the second query will automatically reuse the same entity object from the session. However, any modifications made to this entity during the first query (without calling `session.flush()`) will not be persisted to the database.

1. When querying with `query1`, the resulting list of entities will have the first record's name set to "abc", which matches the database value.
2. After modifying the first entity's name to "sss" without flushing the session, the in-memory changes are not synced to the database.
3. The second query using `query2` will return the database's current values, i.e., the name remains "abc".
4. However, if the JDBC query result is wrapped into a Java entity object, NopORM will check if an existing entity with the same primary key exists in the current OrmSession. If it does, the engine will automatically discard the database-retrieved record (name="abc") and use the existing session entity instead (name="sss").
5. When retrieving an entity by its ID using `dao.getEntityById()`, if the corresponding entity already exists in the OrmSession, it will be returned directly without accessing the database.

By leveraging NopORM's built-in first-level caching mechanism (referred to as one-level caching in Hibernate), we can naturally cache entities within the session. This allows us to store business data directly in the entity objects as a form of caching.

```java
class MyEntity extends OrmEntity {
    private MyCachedData cachedData;

    public MyCachedData getCachedData() {
        if (cachedData == null) {
            cachedData = buildCachedDataFromRelatedObjects();
        }
        return cachedData;
    }
}
```

Since entities are always cached by their primary key, business-level caching results can be stored directly in the entity objects.

The OrmEntity class has an internal `Map<String, Object>` cache named `_t`, which is used to store intermediate calculation results from reports. This allows us to cache certain report calculations within the entity itself.

```javascript
entity.make_t().put("total", computeTotal());
entity.get_t().get("total");
entity.computeIfAbsent("total", loader);
```


## Data that does not belong to entities

For business data that cannot be stored in entity objects, we can utilize the sessionCache object within OrmSession.

```javascript
interface IOrmTemplate{
    ICache<Object, Object> sessionCache();

    <T> T cacheGet(Object key, Supplier<T> loader);
}
```

When retrieving data using the NoPoORM engine, you can specify the cache's key object.

```javascript
Object cacheKey = Arrays.asList("MyEntity","queryCondition",key1,key2);
ormTemplate.cacheGet(cacheKey, query -> dao.findListByQuery(query));
```
