# First-Level Cache

Similar to Hibernate, NopORM is a fully-fledged ORM engine that provides the Session concept. All entity data obtained through the ORM engine are cached into the currently open OrmSession object.
When the `session.flush()` function is called, the NopORM engine recursively traverses all entity objects in the session. If it finds `entity.orm_isDirty()`,
or the entity is in a transient state or Deleting state,
it generates the corresponding SQL statements to actually modify the database. When executing SQL statements, it tries to leverage JDBC batch optimizations to improve database performance.

> Generally, when a transaction is open, the performance of PreparedStatement.executeBatch is the highest. See JdbcBatcher.java for the specific code.

## Implementing OrmSession-level business data caching via entity objects

During the lifetime of an OrmSession, all retrieved entity data are cached in the OrmSession, and it ensures that a single primary key value corresponds to exactly one Java entity object.
If two SQL queries both include the same entity data, the same entity object will be used automatically, and the data retrieved from the database the second time will be discarded. For example

```javascript
   List<MyEntity> list = dao.findPage(query1);
   assertEquals("abc", list.get(0).getName());

   list.get(0).setName("sss");

   List<MyEntity> list2 = dao.findPage(query2);
   assertTrue(list.get(0) == list2.get(0));
   assertEquals("sss", list2.get(0).getName());

   assertEquals("sss", dao.getEntityById(list2.get(0).getId());
```

1. An entity list is returned based on query1, where the name of the first record is abc, which also matches the value in the database.
2. After the first query, we modify the entity object's property to sss. Since we did not call session.flush at this point, the in-memory modifications have not been synchronized to the database.
3. When we query again, at the JDBC level the current value from the database is returned, i.e., name=abc.
4. However, when the JDBC result set is wrapped into Java entity objects, the NopORM engine looks up the entity by its primary key to see whether the corresponding entity object already exists in the current OrmSession. At this point it finds that the OrmSession
   already contains the corresponding record; the engine will automatically discard the value obtained from the database (`name=abc`
   ) and use the existing entity object in the current OrmSession instead (`name=sss`).
5. When fetching an entity by id, if the current OrmSession already contains the corresponding entity object, it is returned directly, skipping database access.

By leveraging NopORM's built-in session-level caching mechanism (known as the first-level cache in Hibernate), we can naturally use entity objects as a caching container for business data.

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

Because entity objects are always cached by primary key, certain business-level cached computation results can be stored directly on the entity.

OrmEntity has a built-in Map-typed cache property `_t`. In the reporting engine, we can use this property to cache intermediate results of report computations.

```javascript
entity.make_t().put("total",computeTotal());
entity.get_t().get("total");
entity.computeIfAbsent(key, loader);
```

## Cached data that conceptually do not belong to entity objects

If the business data to be cached is not convenient to set on the entity object, we can use the sessionCache object built into OrmSession.

```javascript
interface IOrmTemplate{
    ICache<Object, Object> sessionCache();

    <T> T cacheGet(Object key, Supplier<T> loader);
}
```

When retrieving data through the NopORM engine, you can specify the cache key object.

```javascript
Object cacheKey = Arrays.asList("MyEntity","queryCondition",key1,key2);
ormTemplate.cacheGet(cacheKey, ()-> dao.findListByQuery(query)));
```
<!-- SOURCE_MD5:0f02198387c350e163c373a879fd404b-->
