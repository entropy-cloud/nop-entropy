# Basic Usage

QueryBean provides encapsulation for complex query criteria. The supported query operators are defined in the metamodel of [filter.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/query/filter.xdef).

## 1. Complex query criteria

```javascript
// MyBatisPlus
LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
wrapper.eq(User::getUsername, "张三")
        .and(w -> w.between(User::getAge, 18, 30)
                .or().eq(User::getGender, 1))
        .orderByDesc(User::getCreateTime);
List<User> userList = userMapper.selectList(wrapper);

// NopORM

QueryBean query = new QueryBean();
query.addFilter(eq(PROP_Name_username,"张三"))
    .addFilter(and(
      or(
        between(PROP_NAME_age, 18,30),
        eq(PROP_NAME_gender, 1)
      )))
     .addOrderField(PROP_NAME_createTime, true);

IEntityDao<User> dao = daoProvider.daoFor(User.class);
List<User> usreList = dao.findAllByQuery(query);

// If paginated query
query.setOffset(100);
queyr.setLimit(20);
List<User> userList = dao.findPageByQuery(query);

// If only querying a single record
User user = dao.findFirstByQuery(query);
```

## 2. Query by equality conditions only

```javascript
User example = new User();
user.setStatus(10);

IEntityDao<User> dao = daoProvider.daoFor(User.class);
List<User> userList = dao.findAllByExample(example);
User user = dao.findFirstbyExample(example);
long count = dao.countByExample(example);
```

## 3. Embedding subqueries

```javascript
QueryBean query = new QueryBean();
query.addFilter(SQL.begin("o.id in (select y.xx from tbl y where y.id=?", 3).end().asFilter());
dao.findPageByQuery(query);
```

## 4. Join queries

If you query based on fields on the main table, you can directly use composite properties. In the NopORM engine, a composite property like o.product.productType.name will automatically generate a join query based on the primary/foreign key association configuration.

```
QueryBean query = new QueryBean();
query.addFiler(eq("product.productType.name","abc"));
dao.findFirstByQuery(query);
```

If you need to find main-table objects by attributes in a child table, you can use the subquery filter introduced in the previous section, or directly use the EQL query language:

```
select distinct o.book from BookAuthor o where o.author.name like '张%'
```

The above statement searches for books written by authors whose names match the condition. BookAuthor is an association table. The author table is joined through o.author.name, and o.book returns the associated book objects.

If you omit the distinct keyword, because it is a many-to-many relationship, the returned collection may contain duplicates.

## 5. Query conditions in sql-lib and other tag libraries

The Nop platform emphasizes that the same model information can have multiple representations that are freely convertible. As a typical use case, Filter provides a standard representation for complex predicate conditions. Its information structure is defined by the [filter.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/query/filter.xdef) metamodel.

1. In Java, we can use helper functions like and/or/eq/gt on FilterBeans to build TreeBean objects.
2. In XML and the Xpl template language, we can express the same condition using XML syntax such as `<eq name="status" value="1" />`.
3. The FilterBeanToSQLTransformer class can convert Filter information into SQL query statements, e.g., `o.status = 1`.
4. The FilterBeanToPredicateTransformer class can convert Filter information into Java’s Predicate interface to evaluate in memory.

In the rule engine, the predicate conditions we use are also defined by the Filter model.
![rule-model.png](../rule/images/rule-model.png)

We can use Filter more freely in the xpl template language.

* Implement a query function in the xbiz model

```xml

<query name="active_findPage">
  <source>
    <bo:FindPage>
      <filter>
        <eq name="status" value="1"/>
      </filter>
    </bo:FindPage>
  </source>
</query>
```

bo.xlib provides wrappers for functions such as doFindPage in CrudBizModel.

* Define SQL statements in sql-lib
  sql-lib provides SQL management functionality similar to MyBatis. You can invoke SQL statements managed in sql-lib via the SqlMapper interface, or call them directly via SqlLibManager.

```java

@SqlLibMapper("/app/mall/sql/LitemallGoods.sql-lib.xml")
public interface LitemallGoodsMapper {
  void syncCartProduct(@Name("product") LitemallGoodsProduct product);
}
```

In LitmallGoods.sql-lib.xml

```xml

<sql-lib>
  <sqls>
    <eql name="syncCartProduct" sqlMethod="execute">
      <arg name="product"/>

      <source>
        update LitemallCart o
        set o.price = ${product.price},
        o.goodsName = ${product.goods.name},
        o.picUrl = ${product.url},
        o.goodsSn = ${product.goods.goodsSn}
        where o.productId = ${product.id}
      </source>
    </eql>
  </sqls>
</sql-lib>
```

Here, eql indicates the use of EQL object query syntax. It accesses data via entity names and property names. Its syntax is similar to SQL but supports composite properties. For example, o.product.type will automatically recognize foreign key associations and convert them into join conditions.

If you use the sql tag, it represents the native SQL syntax. That is, sql-lib manages both SQL and EQL statements.

* Use in data access control configuration
  Configure data permissions in /nop/main/auth/app.data-auth.xml

```xml

<data-auth>
  <objs>
    <obj name="MyEntity">
      <role-auths>
        <role-auth roleId="manager">
          <filter>
            <eq name="type" value="1"/>
          </filter>
        </role-auth>
      </role-auths>
    </obj>
  </objs>
</data-auth>
```

In the xpl template language, we introduce custom tags to simplify writing Filters, for example:

```xml

<and>
  <eq name="status" value="1"/>
  <app:FilterByTask/>
</and>
```

`<app:FilterByTask>` is a custom tag. As long as it can output an XML node that meets the required format (actually of type XNode, which implements the ITreeBean interface), it will work.

You can also add dynamic conditions:

```
<bo:FindPage>
  <filter>
     <c:if test="${request.status}">
        <eq name="status" value="${request.status}" />
     </c:if>
  </filter>
</bo:FindPage>
```

You can also leverage metaprogramming to simplify tag authoring. For example, `<sql:filter>` is a macro tag executed at compile time, essentially transforming the source structure:

```xml

<sql:filter>and o.classId = :myVar</sql:filter>
```

Equivalent to writing the following by hand:

```xml

<c:if test="${!_.isEmpty(myVar)}">
  and o.classId = ${myVar}
</c:if>
```

## SingleSession Support

Methods on IEntityDao delegate to the underlying ormTemplate. OrmTemplate adopts a Spring-like template pattern: each function will automatically open a session for use, and if there is already a session object in the context, it will be reused.

The NopGraphQL engine automatically opens an OrmSession during execution, so in general business code you do not need to manually open a session.

If you want to use the ORM outside the GraphQL engine, annotate the function with `@SingleSession` and `@Transactional` (note: use the Transactional defined within the Nop platform). They will automatically open the OrmSession and the transaction manager.

```java
public class TccRecordRepository implements ITccRecordRepository {
  // Here we force a new transaction. In general, by setting propagation, it will automatically inherit the transaction from the context.
  @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
  @Override
  public CompletionStage<Void> saveTccRecordAsync(ITccRecord record, TccStatus initStatus) {
    return FutureHelper.futureCall(() -> {
      NopTccRecord tccRecord = (NopTccRecord) record;
      tccRecord.setStatus(initStatus.getCode());
      recordDao().saveEntityDirectly(tccRecord);
      return tccRecord;
    });
  }
  // ...
}
```

All beans that use annotations like `@Transctional` need to be registered in the NopIoC `beans.xml` file, because AOP is implemented using NopIoC’s built-in capabilities. See [aop.md](../ioc/aop.md)

```xml

<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef">
  <bean id="nopTccRecordRepository" class="io.nop.tcc.dao.store.TccRecordStore"/>
</beans>
```

### If you need to open a session manually, you can use the following approach

```javascript

@Inject
IOrmTemplate ormTemplate;

ormTemplate.runInSession(session->{
  ...
})
```

Transaction management is similar:

```javascript

@Inject
ITransactionTemplate transactionTemplate;

transactionTemplate.runInTransaction(txn->{
   ...
})
```

## OrmEntity entity properties
All entity classes in NopORM inherit from OrmEntity. OrmEntity does not support JSON serialization, but provides helper functions to access field values on the entity.

The current field values on the entity are `entity.orm_initedValues()`, and the pre-modification values are `orm_dirtyOldValues()`.

## Differences from MyBatis

NopORM is a full-fledged ORM engine similar to JPA, so it uses OrmSession to manage all entities loaded into memory. Overall usage is similar to JPA and Hibernate, and compared to MyBatis it requires far fewer manual invocation steps.

### 1. No need to call update when modifying.

In general, we use the IEntityDao interface to perform CRUD on entities. Internally, it uses OrmTemplate to invoke the underlying NopORM engine. OrmTemplate is similar to Spring’s HibernateTemplate: when calling its methods, it automatically opens an OrmSession and, after the operation completes, calls `session.flush()` to flush in-memory modifications to the database.

Therefore, after loading an entity from the database, we only need to call setter methods; there is no need to call any update method. The engine will detect whether the entity has been modified, and if so, it will automatically update the database. When updating the database, unlike MyBatis, NopORM automatically generates the update statement based on the fields that have changed. Thus, even if a setter is called, if the property value has not actually changed, the entity’s state will not become dirty and the database will not be updated.

```javascript
@SingleSession
@Transactional
public void changeEntityStatus(String id, int status){
  IEntityDao<MyEntity> dao = daoProvider.daoFor(MyEntity.class);
  MyEntity entity = dao.requireEntity(id);
  entity.setStatus(3);

  // No need to call dao.updateEntity(entity) here.
}
```

If called within a BizModel function, you do not need to use the @SingleSession and @Transactional annotations; the NopGraphQL engine handles it uniformly.

### 2. When inserting, you also don’t necessarily need to call save
As long as you associate the new entity with other entities already present in the OrmSession, when NopORM flushes, it will automatically traverse along object associations to that entity. If it finds that the entity has not yet been saved, it will automatically generate an insert statement.

```javascript
 MyEntity entity = dao.newEntity();
 entity.setName("ssS");
 parent.getChildren().add(entity);
```

### 3. Generally avoid methods like updateDirectly
To maximize performance, NopORM also provides updateDirectly and other approaches that bypass OrmSession and generate SQL updates directly. However, this is essentially a performance backdoor and should generally be avoided.

### 4. Prefer EQL over SQL
NopORM provides an SQL statement management mechanism similar to MyBatis XML. In `sql-lib.xml` you can use multiple query syntaxes including EQL, SQL, and DQL.

EQL is similar to Hibernate’s HQL and allows expressions like `entity.parent.name` for property association syntax, but EQL is much more powerful. In EQL you can freely use various join syntaxes, WITH clauses, LIMIT clauses, UPDATE RETURNING clauses, etc.

* From a design perspective, `EQL = SQL + AutoJoin`. In principle, all syntax that SQL has is supported by EQL. On top of that, EQL adds the feature of automatically inferring table joins based on property associations.
* In actual implementation, EQL supports most of the standard SQL-92 syntax. For database compatibility, it only supports features common to multiple mainstream databases and does not support syntax proprietary to a particular database. For SQL functions, compatibility conversions are implemented via dialect configurations.
* EQL supports GIS-related functions such as `st_contains`.
<!-- SOURCE_MD5:8be09ad034f3221930d6b590b7069a07-->
