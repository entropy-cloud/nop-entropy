# The Introduction to Dynamic SQL Management with Nop

Nop provides a similar dynamic SQL management capability as MyBatis, but its functionality is significantly more extensive and powerful. Additionally, its implementation is even simpler. Using NopORM as the foundation, implementing SqlLibManager only requires around 300 lines of code.

Video Explanation: [Link to Bilibili Video](https://www.bilibili.com/video/BV1Xi421S7oG/)

---


## 一. Usage Instructions


### 1.1 Adding an `sql-lib.xml` File

```xml
<!-- /nop/demo/sql/demo.sql-lib.xml -->
<sql-lib x:schema="/nop/schema/orm/sql-lib.xdef" xmlns:x="/nop/schema/xdsl.xdef">
  <sqls>
    <eql name="findFirstByName" sqlMethod="findFirst">
      <source>
        select o from DemoEntity o where o.name like '${'%' + name + '%'}
      </source>
    </eql>
  </sqls>
</sql-lib>
```

- If the `sqlMethod` is not specified, it will automatically determine the method based on the SQL query and whether a range parameter is provided.
- Expressions introduced using `${expr}` will be automatically replaced with SQL parameters instead of being directly concatenated as strings. If the expression returns a collection, it will expand into multiple parameters.


### 1.2 Adding a Mapper Class

Use the `@SqlLibMapper` annotation to specify the associated `sql-lib.xml` file.

```java
@SqlLibMapper("/nop/demo/sql/demo.sql-lib.xml")
public interface DemoMapper {
    DemoEntity findFirstByName(@Name("name") String name);
}
```


### 1.3 Registering a Mapper Bean in `beans.xml`

```xml
<bean id="io.nop.demo.biz.DemoMapper"
      class="io.nop.orm.sql_lib.proxy.SqlLibProxyFactoryBean"
      ioc:type="@bean:id" ioc:bean-method="build">
  <property name="mapperClass" value="@bean:type"/>
</bean>
```

- If a table uses mapper tags, the corresponding Mapper interface and bean definitions will be automatically generated.

| MyBatis          | Nop Platform         |
|-----------------|-----------------------|
| Dynamic SQL via XML        | Dynamic SQL Configuration via Delta Updates           |
| Mapper Interfaces for SQL Execution | Uniform `@Name` Annotation for Parameter Naming with `@Name` Resolver       |
| Fixed Tags for Query Generation    | Customizable Tags for Query Generation                  |
| Expression Substitution for SQL Parameters    | General Expression Engine with Xpl Template Language         |
| Transaction and Caching Support        | Built-in Transaction Handling and Cache Management      |


## 1.4 Why Use XML Files as an Advantage

- **Ease of Debugging**: Adjustments to SQL queries can be made without halting the application.
- **Flexibility**: Direct modifications to SQL queries are not required; instead, dynamic parameters are used.
- **Maintainability**: Changes to data models do not require manual adjustments to SQL queries.

---


### 二. Why Use XML Files as an Advantage

While frameworks like JPA and MyBatisPlus have largely moved away from XML configuration in favor of annotations, many still perceive XML configuration as outdated or even obsolete. This is a common misconception based on a lack of understanding of how modern XML-based frameworks like Nop operate.

Using XML files offers several advantages over pure annotation usage:

- **Ease of Debugging**: Adjustments to SQL queries can be made without halting the application.
- **Flexibility**: Direct modifications to SQL queries are not required; instead, dynamic parameters are used.
- **Maintainability**: Changes to data models do not require manual adjustments to SQL queries.

---

# Dynamic Loading of Model Files

Nop platform supports dynamic loading of all model files. If you modify a model file or any of its dependencies, the model analysis cache will automatically update. This makes debugging much more convenient compared to using Java annotations.

---

## 2.1 Customization via Delta

When a `sql-lib` model file is bundled into the Jar package, you no longer need to modify the original XML files. Simply add a file with the same name in the independent delta module's delta directory to override the base module's files.

However, when using Java annotations, we cannot achieve this level of customization for SQL statements in a straightforward manner.

---

## 2.2 Customizing SQL Statements Using Delta

After the `sql-lib` model files are packaged into the Jar, you no longer need to edit the original XML files. By adding a file with the same name in the delta directory of the independent delta module, you can override the XML files of the base module.

When using Java annotations, we cannot achieve this level of customization for SQL statements through simple means.

---

## 2.3 Online Customization of SQL Statements

In the Nop platform, all model files are managed through a virtual file system. This virtual file system allows database configuration tables to be treated as virtual files. Through this approach, you can configure SQL statements on the interface, while during programming, the `sql-lib` model files are handled as regular files, leveraging Nop's built-in model caching, dependency tracking, and other capabilities.

---

## 2.4 Double Abstraction

When loading model files in the Nop platform, it supports meta-programming. Additionally, the XPL template language used during SQL generation also supports custom tags. This allows for easy discovery of common patterns in SQL construction and provides customizable abstractions.
- For example, if a frequently occurring SQL fragment is identified, it can be abstracted into a function using custom tag libraries, while still leveraging Java annotations.

Without this double abstraction capability, we would not be able to further simplify configuration possibilities.

---

## 2.5 Generating IDE Features Based on Meta Models

The MyBatis IDE plugin requires separate development. However, in the Nop platform, any DSL file can be imported using `x:schema` to associate with a meta model, and then the generic `nop-idea-plugin` plugin can automatically derive code suggestions, breakpoint support, and other features.
- Similarly, online visualization can be achieved by deriving from meta models.

---

## 3. The Power of XPL Template Language

A fundamental issue with MyBatis is that it provides only a limited number of built-in tags. In practical use, this leads to a lack of abstraction capabilities. 
- In the Nop platform, we leverage the XPL template language to generate SQL statements, enabling the introduction of infinite custom abstractions through XPL tag libraries.

---

## Example Code

```xml
<sql-lib x:extends="super">
  <x:config>
    <c:import from="/nop/orm/xlib/sql.xlib"/>
  </x:config>

  <sqls>
    <sql name="findWithDialect">
      <arg name="product"/>

      <source>
        select
        <sql:fragment id="colList"/>
        from my_entity
        where 1=1
        <sql:when-dialect name="h2">
          and a = 1
        </sql:when-dialect>
        <sql:filter>and o.classId in (:ids)</sql:filter>
        <c:if test="${product.main}">
          <c:script>
            import app.MyHelper;
          </c:script>
          and b > ${MyHelper.getXXX(product)}
        </c:if>
      </source>
    </sql>
  </sqls>
</sql-lib>
```

---

* `<sql:fragment>` and `<sql:when-dialect>` tags are custom tags defined in the `sql.xlib` library, not built-in features of the engine.
* Additional business-related custom tags, such as `<app:FilterTopProduct/>`, can be added.
* The XPL template language supports constructs like `<c:if>`, `<c:for>`, and similar structures, allowing for expressions similar to JavaScript. These can be directly integrated using `import` statements for Java classes.

---

## 3. Multidimensional Queries for OLAP

Rinse Inc. has developed an open-source [front-end BI system](http://www.raqsoft.com.cn/r/os-bi), which introduces a unique DQL (Dimensional Query Language) at the technical level. For detailed information, please refer to the article from Qian College.

[ Farewell to Wide Tables, Welcome DQL: Revolutionizing Modern BI - Qian College](http://c.raqsoft.com.cn/article/1653901344139?p=1&m=0)

Rinse Inc.'s stance is that end-users find complex SQL JOIN operations difficult to understand. To facilitate multi-dimensional analysis, wide tables are often used, which present a series of challenges for data preparation. In contrast, DQL simplifies the mental model for users regarding JOIN operations and offers better performance compared to SQL.

For example, using DQL can streamline aggregated queries between main and sub-tables.

### Example of Using DQL for Aggregated Queries

```sql
-- SQL
SELECT T1.orderNumber, T1.customer, SUM(T2.price) 
FROM OrderTable T1
JOIN DetailedOrderTable T2 ON T1.orderNumber = T2.orderNumber
GROUP BY T1.orderNumber, T1.customer
```

```dql
-- DQL
SELECT orderNumber, customer, SUM(price) 
FROM OrderTable
```

Nop platform implements a similar capability to DQL through its QueryBean abstraction.

### Example of DQL Implementation in Nop Platform

```xml
<sql-lib>
  <sqls>
    <query name="findCustomStats">
      <source>
        <fields>
          <field name="orderNo"/>
          <field name="customer"/>
          <field owner="orderDetails" name="price" aggFunc="sum"/>
        </fields>
        <sourceName>Order</sourceName>
      </source>
    </query>
  </sqls>
</sql-lib>
```

### Query Construction in Java Code

```java
QueryBean query = new QueryBean();
query.fields(
    mainField("orderNo"),
    mainField("customer"),
    subField("orderDetails", "price").sum()
);
query.setSourceName("Order");
```

## 4. Advanced Features Not Present in MyBatis and JPA

### 4.1 Batch Loading of Related Attributes

The Nop ORM easily suffers from the `N+1` problem when handling collections, which can be mitigated by configuring a `batchLoadSelection` mechanism similar to GraphQL, reducing database query counts.

### Example of Batch Loading in Nop Platform

```xml
<eql name="findBySqlFilter">
  <batchLoadSelection>
    simsCollege { simsClasses }
  </batchLoadSelection>
  <source>
    select o from SimsClass o where 1=1
    <sql:filter>and o.classId in (:ids)</sql:filter>
  </source>
</eql>
```

### 4.2 Enabling Data Access Filtering

Enabling `enableFilter` property adds automatic data access filtering for each entity. Combined with the built-in `IEqlAstTransformer`, it allows strict control over EQL queries to prevent unauthorized access and SQL injection.

### Example of Enabling Data Filtering in Nop Platform

```xml
<eql name="findFirstByName" enableFilter="true" sqlMethod="findFirst">
  <source>
    select u from NopAuthUser u where u.userName like '${%${name}%}'
  </source>
</eql>
```

### 4.3 Supporting Multiple Data Sources

The `querySpace` property allows specifying a different data source, enabling access to multiple databases.

### Example of Using Multiple Data Sources in Nop Platform

```xml
<sql querySpace="report">
  ...
</sql>
```

In `beans.xml`, you need to register the corresponding DataSource with an ID following the format `nopDataSource_{querySpace}`.

```xml
<bean id="nopDataSource_report" class="com.zaxxer.hikari.HikariDataSource">
  ...
</bean>
```


### 4.2 Using Native SQL Queries

Generally, we use the `<eql>` node to load entity data. However, if you set `rowType` to an entity type, you can also use the `<sql>` node to load entity data.

After the result is wrapped as an entity, it will automatically provide lazy loading of associated properties.

```xml
<sql name="testOrmEntityRowMapper" rowType="io.nop.app.SimsClass" sqlMethod="findFirst"
       colNameCamelCase="true">
  <source>
    select o.class_id, o.class_name, o.college_id
    from sims_class o
  </source>
</sql>
```

* Setting `colNameCamelCase` will automatically convert the returned column name `class_id` to the entity property `classId`.
* If the SQL query does not return a primary key field, it will create a new entity. Otherwise, it will load the entity from the current OrmSession and update its properties.
* If the corresponding entity data has already been loaded into memory and modified, executing the SQL query will throw an exception `nop.err.orm.entity-prop-is-dirty`. If not modified, it will update the entity's properties.
* You can change this behavior using `ormEntityRefreshBehavior`. The default behavior is `errorWhenDirty`. Using `useFirst` will retain the first loaded entity data, ignoring the current SQL query result. Using `useLast` will use the last query result.

For more detailed information, refer to [sql-lib.md](../../dev-guide/orm/sql-lib.md).


### 4.3 Direct Usage as a Dictionary

If the SQL name ends with `_dict`, you can call it using `DictProvider`.

```javascript
DictBean dict = DictProvider.instance().getDict(null, "sql/test.demo_dict", null, scope);
```

The SQL query must include both `value` and `label` fields.

```xml
<eql name="demo_dict">
  <source>
    select o.collegeId as value, o.collegeName as label
    from SimsCollege o
  </source>
</eql>
```

