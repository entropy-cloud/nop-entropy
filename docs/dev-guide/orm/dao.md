`IEntityDao`接口提供了针对单实体对象的CRUD封装，一般开发CRUD功能无需编写Service类，也不需要像MyBatis那样封装SQL语句。
EntityDao上提供的方法已经足够丰富，可以完成相当复杂的功能。

> 自动生成的代码已经通过`CrudBizModel`实现了GraphQL层面的CRUD服务，一般只需要继承`CrudBizModel`，并做少量定制即可，不需要手工编写完整的CRUD实现。

# 基本约定

1. `findFirst`前缀表示查找第一条，比如`findFirstByExample`, `findFirstByQuery`等
2. `findAll`前缀表示返回满足条件的所有条目，比如`findAllByExample`, `findAllByQuery`等
3. `findPage`前缀表示返回分页返回满足条件的条目，比如`findPageByExample`, `findPageByQuery`等
4. `loadEntityById`按照Hibernate的惯例，它只是在内存中构建一个Proxy对象，并不真的查询数据库
5. `getEntityById`按照Hibernate的惯例，它会自动加载Proxy对象，确保内存中获取到实体数据，如果数据库中没有对应实体数据，则返回`null`
6. `require`前缀表示返回结果必须不为`null`，如果为`null`，则会抛出异常
7. `batch`前缀表示批量操作，例如`batchDeleteEntities`表示批量删除实体

## 获取Dao对象

```java
@Inject
IDaoProvider daoProvider;

dao = daoProvider.daoFor(MyEntity.class);
```

`daoProvider`统一关系系统中所有的`dao`对象，可以按照实体名、实体Java类、表名等不同方式获取到对应的`dao`对象。

## 在CrudBizModel的派生类中获取dao

1. 获取当前实体的dao: `this.dao()`
2. 获取指定类型实体的dao: `this.daoFor(MyEntity.class)`

## 常用函数

## 1. 按照属性查询

```java
MyEntity example = dao.newEntity();
example.setMyField("a");
// 查找满足条件的第一个
MyEntity entity = dao.findFirstByExample(example);

// require表示如果没有找到，则会抛出异常
entity = dao.requireFirstByExample(example);
```

## 2. 构造复杂查询条件

```java
QueryBean query = new QueryBean();
query.setFilter(and(eq(MyEntity.PROP_NAME_myField,"a"), gt(MyEntity.PROP_NAME_myStatus,3)));
query.setLimit(5);

List<MyEntity> list = dao.findPageByQuery(query);
```

`FilterBeans`类中定义了一些辅助函数，如`and/or/eq,gt`等，可以用于构建过滤条件。`gt`表示大于，`ge`表示大于等于，`lt`表示小于，`le`表示小于等于，`eq`表示等于

## 3. 新建并保存实体

```java
MyEntity entity = dao.newEntity();
...
dao.saveEntity(entity);
```

一般情况下我们应该使用`dao.newEntity()`函数创建实体，而不要直接使用`new MyEntity()`这种方式。这是因为当我们通过Delta定制方式来扩展实体类时，
`dao.newEntity()`返回的Java对象可能是扩展类的对象，而不是当前实体类的对象。例如，我们在Delta模块中可以定义了`class MyEntityEx extends MyEntity`,
然后配置ORM模型，使得`test.MyEntity`这个实体类名对应的Java类为`MyEntityEx`，则`dao.newEntity()`实际返回的是`MyEntityEx`

```xml
<orm>
    <entity name="test.MyEntity" class="test.MyEntityEx" >...</entity>
</orm>
```

## 4. 修改实体

```java
dao.saveOrUpdateEntity(entity);
```

按照ORM引擎的一般原理，如果只是修改实体属性是不需要调用`dao.updateEntity`方法的。因为NopORM会通过`OrmSession`来管理所有的实体对象，当`session.flush`的时候
会自动检查当前session中所有对象是否被修改，如果有修改，就会自动将修改同步到数据库中。`dao.updateEntity()`基本上是一个空函数，它只会做一些状态检查工作。

`dao.saveOrUpdateEntity`会根据实体上的状态标记信息来确定是否是新建的实体（Transient），如果是，则调用`saveEntity`，否则调用`updateEntity`。

## 5. 删除实体

```java
dao.deleteEntity(entity);
```

删除实体的时候，如果它的关联子表集合配置了`cascade-delete`，则子表集合中的元素也会被自动删除。

## 6. 批量加载属性

JPA的一个常见性能问题是关联对象延迟加载导致出现N+1问题。`IEntityDao`提供了一个`batchLoadProperties`函数用于一次性加载所有关联属性。

```java
List<MyEntity> list = dao.findAll();
dao.batchLoadProps(list, Arrays.asList("parent","children","parent.parent"));
```

内部实现方式有些类似于GraphQL的`BatchDataLoader`，只是它针对ORM实体的情况做了特别的优化。
