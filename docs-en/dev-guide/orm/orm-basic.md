# Basic Usage

QueryBean provides encapsulation for complex query conditions. The supported query operators can be referenced at [filter.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/query/filter.xdef). This definition is part of the meta-model.


## 1. Complex Query Conditions


## MyBatisPlus
```javascript
// MyBatisPlus
LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
wrapper.eq(User::getUsername, "张三")
        .and(w -> w.between(User::getAge, 18, 30))
        .or().eq(User::getGender, 1)
        .orderByDesc(User::getCreateTime);
List<User> userList = userMapper.selectList(wrapper);

// NopORM

QueryBean query = new QueryBean();
query.addFilter(eq(PROP_Name_username,"张三"))
        .addFilter(and(
          or(
            between(PROP_NAME_age, 18,30),
            eq(PROP_NAME_gender, 1)
          ),
          eq(PROP_NAME_createTime, true)
        ))
        .addOrderField(PROP_NAME_createTime, true);

IEntityDao<User> dao = daoProvider.daoFor(User.class);
List<User> userList = dao.findAllByQuery(query);

// Pagination
query.setOffset(100);
query.setLimit(20);
List<User> userList = dao.findPageByQuery(query);

// Single Record Query
User user = dao.findFirstByQuery(query);
```


## NopORM
```javascript
QueryBean query = new QueryBean();
query.addFilter(eq("product.productType.name","abc"));
dao.findFirstByQuery(query);
```


## 2. Equal Conditions Only

```javascript
User example = new User();
example.setStatus(10);

IEntityDao<User> dao = daoProvider.daoFor(User.class);
List<User> userList = dao.findAllByExample(example);
User user = dao.findFirstbyExample(example);
long count = dao.countByExample(example);
```


## 3. Embedded Subqueries

```javascript
QueryBean query = new QueryBean();
query.addFilter(SQL.begin("o.id in (select y.xx from tbl y where y.id=?", 3).end().asFilter());
dao.findPageByQuery(query);
```



If querying based on fields in the main table, you can directly use composite properties in NopORM. For example, `o.product.productType.name` will automatically generate an associated query.

```javascript
QueryBean query = new QueryBean();
query.addFilter(eq("product.productType.name","abc"));
dao.findFirstByQuery(query);
```

If needing to query based on fields in the subtable, you can use the methods described in the previous section or directly use EQL.

```sql
select distinct o.book from BookAuthor o where o.author.name like '张%'
```

This query finds books written by "张三" through the author table. The `BookAuthor` is a related table, and `o.author.name` performs an association query on the `author` table. The result will be a list of books associated with "张三".

If "distinct" is not used, duplicate entries may appear in the results due to the multi-to-many relationship.




The Nop platform emphasizes that a single model can have multiple expression forms, which can be freely converted into each other. As a typical example, the **Filter** component provides a standard complex condition expression form.


The information structure of the model is defined by `[filter.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/query/filter.xdef)` meta-model.

---


In **Java**, you can use helper functions such as `and`, `or`, `eq`, `gt` available in the `FilterBeans` class to construct a `TreeBean` object.
- Example: 
```java
FilterBeans.create()
    .and("status", "active")
    .get();
```


In **XML** and **Xpl** template language, the same condition can be expressed using:
```xml
<eq name="status" value="1"/>
```
- Example in Xpl:
```xpl
<bo:FindPage>
    <filter>
        <eq name="status" value="1"/>
    </filter>
</bo:FindPage>
```


The **FilterBeanToSQLTransformer** class converts filter information into SQL queries, such as:
- Example:
```sql
o.status = 1
```


The **FilterBeanToPredicateTransformer** class converts filter information into Java's `Predicate` interface and executes the condition in memory.

---


In the rule engine, the conditions used are defined by the **Filter** model.

![rule-model](../rule/images/rule-model.png)

---


In the **Xpl** template language, you can use filters with greater freedom:
- Example:
```xpl
<eql name="active_findPage">
    <source>
        <bo:FindPage>
            <filter>
                <eq name="status" value="1"/>
            </filter>
        </bo:FindPage>
    </source>
</eql>
```

---


The **Xbiz** model implements query functions.

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

---


The **bo.xlib** class provides encapsulation for functions like `doFindPage` in the **CrudBizModel**.

---


The **sql-lib** framework allows you to define and manage SQL statements. It supports similar functionality to MyBatis, where you can use the `SqlMapper` interface or directly call methods via `SqlLibManager`.

```java
@SqlLibMapper("/app/mall/sql/LitemallGoods.sql-lib.xml")
public interface LitemallGoodsMapper {
    void syncCartProduct(@Name("product") LitemallGoodsProduct product);
}
```

---


The **LitmallGoods.sql-lib.xml** file defines SQL queries using EQL syntax.

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

---


EQL (Entity Query Language) is used to query data. It supports composite properties, such as `o.product.type`, and automatically handles foreign key relationships.

---


If you use `<sql>` tags, it indicates the use of native SQL syntax. This means that **sql-lib** manages both SQL and EQL queries simultaneously.

---


For data permission configuration:
- File location: `/nop/main/auth/app.data-auth.xml`
- Example:
```xml
<auth>
    <dataPermission>
        <resource name="LitemallGoods">
            <action name="read">true</action>
            <action name="update">false</action>
        </resource>
    </dataPermission>
</auth>
```

---

```markdown

## Data Authorization Configuration

The following XML configuration snippet demonstrates how to set up data authorization rules:

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

In the XPL template language, custom tags are used to simplify the creation of filters. For example:

```xml
<and>
  <eq name="status" value="1"/>
  <app:FilterByTask/>
</and>
```

`<app:FilterByTask>` is a custom tag that can output an XML node of the required format (it's actually of type XNode and implements ITreeBean interface).

Dynamic conditions can also be added:

```xml
<bo:FindPage>
  <filter>
    <c:if test="${request.status}">
      <eq name="status" value="${request.status}" />
    </c:if>
  </filter>
</bo:FindPage>
```

Additionally, meta-programming capabilities allow for simplified tag creation. For example, `<sql:filter>` is a macro tag that executes at compile-time and modifies the code structure:

```xml
<sql:filter>and o.classId = :myVar</sql:filter>
```

This is equivalent to manually writing:

```xml
<c:if test="${!_.isEmpty(myVar)}">
  and o.classId = ${myVar}
</c:if>
```



Methods on `IEntityDao` are implemented by calling the underlying `ormTemplate`. The `OrmTemplate` follows a similar pattern to Spring's template, with each method automatically starting a session if one doesn't already exist in the context. 

When using NopGraphQL, the engine automatically handles `OrmSession`, so typical business code does not need to manually open sessions.

If you want to use ORM outside of the GraphQL engine, you can annotate methods with `@SingleSession` and `@Transactional` (using Nop's own `Transactional` annotation). These annotations will automatically manage `OrmSession` and transaction management.

```java
public class TccRecordRepository implements ITccRecordRepository {
  // Forcefully start a new transaction with propagation
  @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
  @Override
  public CompletionStage<Void> saveTccRecordAsync(ITccRecord record, TccStatus initStatus) {
    return FutureHelper.futureCall(() -> {
      NopTccRecord tccRecord = (NopTccRecord) record;
      tccRecord.setStatus(initStatus.getCode());
      return recordDao().saveEntityDirectly(tccRecord);
    });
  }
  // ...
}
```

All methods annotated with `@Transactional` require registration in NopIoC's `beans.xml`. This is because AOP in NopIoC is handled through its built-in configuration, not through Spring's AOP. Refer to [aop.md](../ioc/aop.md) for more details.

```xml
<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef">
  <bean id="nopTccRecordRepository" class="io.nop.tcc.dao.store.TccRecordRepository"/>
</beans>
```

### If you want to manually open a session, here's how to do it:

```javascript
@Inject
IOrmTemplate ormTemplate;

ormTemplate.runInSession(session => {
  // Session operations can be performed here
});
```


## Transaction Management is similar:

```javascript
@Inject
ITransactionTemplate transactionTemplate;

transactionTemplate.runInTransaction(txn => {
  // Transaction operations can be performed here
});
```


## OrmEntity Entity Properties

All entities in NopORM inherit from the `OrmEntity` class. The `OrmEntity` does not support JSON serialization but provides helper functions to retrieve field values of entities.

The current field values of an entity are obtained via `entity.orm_initedValues()`, while the previous field values are obtained via `orm_dirtyOldValues()`.


### Differences from MyBatis

NopORM is a full ORM engine similar to JPA and Hibernate. It uses `OrmSession` to manage all entities loaded into memory, similar to how JPA and Hibernate work. Compared to MyBatis, NopORM requires fewer manual call steps.


### 1. No need to manually call the update method

In general, we use the `IEntityDao` interface to implement CRUD operations for entities. It internally uses `OrmTemplate` to interact with the underlying NopORM engine.
The `OrmTemplate` is similar to Spring's `HibernateTemplate`. When you call its methods, it automatically opens an `OrmSession` and calls `session.flush()` after the operation to flush any changes to the database.

After loading entities from the database into memory, we only need to call the set method. We do not need to manually call any update method; the engine will check if the entity has been modified and update the database accordingly.

When updating the database compared to MyBatis, NopORM automatically generates the appropriate `update` statements based on the modified fields. Therefore, even if you call the set method but the actual fields of the entity have not been modified, the engine will not update the database.


### Example:

```javascript
@SingleSession
@Transactional
public void changeEntityStatus(String id, int status) {
  IEntityDao<MyEntity> dao = daoProvider.daoFor(MyEntity.class);
  MyEntity entity = dao.requireEntity(id);
  entity.setStatus(3);

  // Here, you do not need to call dao.updateEntity(entity);
}
```

If you call this function within a business model function (e.g., `BizModel`), you do not need to use the `@SingleSession` and `@Transactional` annotations. The NopGraphQL engine will handle it automatically.


### 2. No need to manually call the save method

Just link the new entity with existing entities in the `OrmSession`, and when the session is flushed, the engine will automatically traverse through the object relationships to generate an `insert` statement if the entity has not been saved yet.

```javascript
MyEntity entity = dao.newEntity();
entity.setName("ssS");
parent.getChildren().add(entity);
```



To maximize performance, NopORM also provides methods like `updateDirectly` to bypass the `OrmSession` and directly generate SQL updates. However, this is considered a performance vulnerability in most cases and should generally be avoided.



NopORM provides a similar XML configuration for managing SQL statements as MyBatis does, using the `sql-lib.xml` file to support multiple query languages like EQL, SQL, and DQL.

EQL is similar to Hibernate's HQL but far more powerful. It supports joins, subqueries, limits, with clauses, update returning clauses, etc.
From a design perspective, **EQL = SQL + AutoJoin**. EQL supports all the syntax that standard SQL does while adding additional features like auto-joining related entities.

* In the actual implementation, the EQL syntax supports most standard SQL-92 syntax. However, it is designed for database compatibility by supporting only the common syntax features among multiple mainstream databases, excluding database-specific extensions. For SQL functions, compatibility translation has been implemented through `dialect` configuration.
* EQL supports GIS-related functions such as `st_contains`
