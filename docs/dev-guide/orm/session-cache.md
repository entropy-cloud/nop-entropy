# 一级缓存

NopORM与Hibernate类似，作为一个完整设计的ORM引擎，它提供了Session概念，所有通过ORM引擎获取的实体数据都缓存到当前打开的OrmSession对象中。
当调用`session.flush()`函数时，NopORM引擎会递归遍历session中所有实体对象，如果发现`entity.orm_isDirty()`
或者实体处于transient状态或者Deleting状态，
则生成对应的sql语句真正对数据库进行修改。执行SQL语句时会尽量利用JDBC的Batch优化机制，提升数据库性能

> ，一般在开启事务的情况下，PrepareStatement的executeBatch函数执行性能最高。具体代码参见JdbcBatcher.java

## 借助于实体对象实现OrmSession级别的业务数据缓存

在OrmSession打开期间，所有获取到的实体数据都会缓存到OrmSession中，而且**确保一个主键值只对应于唯一的一个Java实体对象**。
如果两次SQL查询都包含了同样的实体数据，则会自动使用同一个实体对象，第二次从数据库中查询得到的数据会被自动放弃。例如

```javascript
   List<MyEntity> list = dao.findPage(query1);
   assertEquals("abc", list.get(0).getName());
   
   list.get(0).setName("sss");
   
   List<MyEntity> list2 = dao.findPage(query2);
   assertTrue(list.get(0) == list2.get(0));
   assertEquals("sss", list2.get(0).getName());
   
   assertEquals("sss", dao.getEntityById(list2.get(0).getId());
```

1. 根据查询条件query1返回实体列表，其中第一条记录的name的值为abc，它对应数据库中的值也是abc。
2. 在第一次查询后我们将实体对象的属性修改为sss，此时因为我们没有调用session.flush，所以内存中的修改并没有同步到数据库中。
3. 我们再次查询时，在JDBC层面会返回数据库中的当前值，即name=abc
4. 但是当JDBC查询得到的数据集被包装为Java实体对象时，NopORM引擎会根据实体对象的主键查找当前OrmSession中是否已经存在了对应的实体对象。此时发现OrmSession中
   已经存在了对应的记录，引擎会自动丢弃从数据库中获取到的记录值（`name=abc`
   ），而使用当前OrmSession中已经存在的实体对象来代替(`name=sss`)。
5. 根据id获取实体时，如果当前OrmSession中已经存在对应实体对象，则会直接返回，跳过数据库访问。

利用NopORM引擎这种内置的Session级别的缓存机制（在Hibernate的技术体系中称为一级缓存），我们可以很自然的将实体对象作为业务数据的一种缓存容器。

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

因为实体对象总是按主键被缓存，因此一些业务层面的缓存计算结果可以直接存放在实体上。

OrmEntity内置了一个Map类型的缓存属性`_t`，我们在报表引擎中可以利用这个属性来缓存一些报表计算的中间结果。

```javascript
entity.make_t().put("total",computeTotal());
entity.get_t().get("total");
```

## 概念层面不属于实体对象的缓存数据

如果需要缓存的业务数据不方便设置到实体对象上，则我们可以使用OrmSession中内置的sessionCache对象。

```javascript
interface IOrmTemplate{
    ICache<Object, Object> sessionCache();

    <T> T cacheGet(Object key, Supplier<T> loader);
}    
```

通过NopORM引擎获取数据时，可以指定缓存的key对象。

```javascript
Object cacheKey = Arrays.asList("MyEntity","queryCondition",key1,key2);
ormTemplate.cacheGet(cacheKey, ()-> dao.findListByQuery(query)));
```
