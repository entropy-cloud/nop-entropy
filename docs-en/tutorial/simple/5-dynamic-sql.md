# Getting Started with Nop: Dynamic SQL Management

The Nop platform provides MyBatis-like dynamic SQL management capabilities, but its features are far richer and more powerful than MyBatis. At the same time, its implementation is actually simpler—based on NopORM, implementing SqlLibManager requires just a little over 300 lines of code.

Tutorial video: https://www.bilibili.com/video/BV1Xi421S7oG/

## I. Usage Instructions

### 1.1 Add a `sql-lib.xml` file

```xml
<!-- /nop/demo/sql/demo.sql-lib.xml -->
<sql-lib x:schema="/nop/schema/orm/sql-lib.xdef" xmlns:x="/nop/schema/xdsl.xdef">
  <sqls>
    <eql name="findFirstByName" sqlMethod="findFirst">
      <source>
        select o from DemoEntity o where o.name like ${'%' + name + '%'}
      </source>
    </eql>
    《
  </sqls>
</sql-lib>
```

* If sqlMethod is not specified, it will be inferred automatically based on the SQL statement and whether the range parameter is passed. Optional values include findFirst/findPage/findAll/execute, etc.
* Expressions introduced via `${expr}` are automatically converted into SQL parameters; they are not directly concatenated into the SQL string. If an expression returns a collection object, it will automatically be expanded into a set of parameters.

### 1.2 Add a Mapper class

Use the `@SqlLibMapper` annotation to specify the associated `sql-lib.xml` file.

```java
@SqlLibMapper("/nop/demo/sql/demo.sql-lib.xml")
public interface DemoMapper {
  DemoEntity findFirstByName(@Name("name") String name);
}
```

### 1.3 Register the Mapper Bean in beans.xml

```xml
<bean id="io.nop.demo.biz.DemoMapper"
      class="io.nop.orm.sql_lib.proxy.SqlLibProxyFactoryBean"
      ioc:type="@bean:id" ioc:bean-method="build">
  <property name="mapperClass" value="@bean:type"/>
</bean>
```

* When generating code from Excel models, if a table has the mapper tag, the Mapper interface class and the Mapper Bean definition above are generated automatically.

| MyBatis            | Nop Platform                                       |
| ------------------ | -------------------------------------------------- |
| Configure dynamic SQL via XML | Apply configuration corrections through unified Delta customization |
| Encapsulate SQL execution via Mapper interfaces | Nop Platform uses a unified @Name annotation to define parameter names and passes context via IEvalContext |
| Generate dynamic SQL using a small fixed set of tag functions | Introduce custom tags via the XPL tag library |
| Generate SQL parameters via expressions | Expressions use a general expression engine; with XPL’s SQL output mode, expression outputs are converted into SQL parameters |
| Support transactions, result caching, etc. | Leverage the Dao layer’s JdbcTemplate to automatically support transactions and result caching |
| Manage SQL statements | Manage various query languages including EQL, SQL, DQL |

## II. Why using XML files is an advantage

Frameworks like JPA and MyBatisPlus have largely moved away from XML and rely entirely on Java annotations for configuration. This has led many to believe that XML is completely outdated, and some strongly oppose any framework that uses XML. This is actually a stereotype and a misconception.

Using XML files has many advantages over annotations.

### 2.1 Adjust SQL without stopping the application during debugging

All model files in the Nop platform support dynamic loading. As long as you modify a model file or any of its dependencies, the model parsing cache updates automatically. This makes debugging far more convenient than using Java annotations.

### 2.2 Adjust SQL via Delta customization

After a sql-lib model file has been packaged into a JAR, there’s no need to modify the original XML file. Simply add a file with the same name under the delta directory of a separate Delta module to override the file in the base module.

With Java annotations, we cannot customize the SQL inside a JAR in a simple, generic way.

```xml
<sql-lib x:extends="super">
  <sqls>
    <sql name="findUserRoles">
      <source>
        ...
      </source>
    </sql>
  </sqls>
</sql-lib>
```

We can customize just a specific SQL statement or just a specific property; whereas with MyBatis, customizing a mapper file requires replacing the entire file.

For more details on Delta customization, see [delta-customization.md](../../dev-guide/delta/delta-customization.md)

### 2.3 No-code development can adjust SQL online

All model files in the Nop platform are managed through a unified virtual file system, which can treat a configuration table in the database as virtual files. In this way, we can configure SQL statements via the UI, while developers can treat them as ordinary sql-lib model files, reusing the platform’s built-in model cache and dependency tracking capabilities.

### 2.4 Secondary abstraction capability

Model file loading in Nop supports metaprogramming, and the XPL template language used to generate SQL also supports a custom tag mechanism. This makes it easy to identify common patterns in SQL construction and provide custom abstractions. For example, if a SQL fragment appears frequently, you can abstract it into a function using a custom tag library. With Java annotations, you generally can only use the abstractions built into the framework, with limited opportunities to further simplify configuration.

### 2.5 IDE hints and visual designers generated from meta-models

MyBatis IDE plugins require dedicated development. In Nop, any DSL file that introduces the corresponding meta-model via `x:schema` can automatically gain syntax hints, breakpoint debugging, and more via the general `nop-idea-plugin`.
Similarly, an online visual designer can be automatically derived from the meta-model to directly design the corresponding DSL file.

## III. Powerful XPL Template Language

A fundamental design issue with MyBatis is that it only provides a small number of built-in tags, which clearly lack abstraction power in practice.
In the Nop platform, we use the XPL template language to generate SQL and can introduce an unlimited number of custom abstractions via XPL tag libraries.

```xml
<sql-lib>
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

* Tags like `<sql:fragment>` and `<sql:when-dialect>` are custom tags in the `sql.xlib` tag library; they are not built-in engine features.
* We can add more business-related tags, such as `<app:FilterTopProduct/>`, etc.
* The XPL template language includes many constructs such as `<c:if>` and `<c:for>`, supports JavaScript-like expressions, and allows importing Java classes directly via `import`.

## III. Master-Detail Queries Oriented to OLAP

Raqsoft has open-sourced a [front-end BI system](http://www.raqsoft.com.cn/r/os-bi) and proposed a distinctive DQL (Dimensional Query Language) at the technical level. For details, see the Raqsoft Academy article:

[Say Goodbye to Wide Tables, Use DQL to Build the New Generation of BI - Raqsoft Academy](http://c.raqsoft.com.cn/article/1653901344139?p=1&m=0)

Raqsoft’s view is that end users find complex SQL JOINs hard to understand. For ease of multidimensional analysis, they often resort to wide tables, which creates a series of difficulties for data preparation. DQL simplifies the mental model of JOIN operations for end users and offers performance advantages over SQL.

For example, DQL can simplify aggregate queries over master-detail associations:

```sql
-- SQL
SELECT T1.订单编号,T1.客户,SUM(T2.价格)
FROM 订单表T1
JOIN 订单明细表T2 ON T1.订单编号=T2.订单编号
GROUP BY T1.订单编号,T1.客户

-- DQL
SELECT 订单编号,客户,订单明细表.SUM(价格)
FROM 订单表
```

The Nop platform implements similar composite query capabilities via the QueryBean abstraction:

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

You can provide a visual designer on the frontend to design query objects directly.

In Java code:

```java
QueryBean query = new QueryBean();
query.fields(mainField("orderNo"), mainField("customer"),
   subField("orderDetials","price").sum());
query.setSourceName("Order");
```

## IV. More Advanced Features Not Found in MyBatis and JPA

### 4.1 Batch loading of associated properties

ORM engines that support associated collections can easily run into the N+1 problem. In the `sql-lib` file, you can configure batchLoadSelection to implement a GraphQL-like batch loading mechanism that reduces the number of database accesses.

```xml
<eql name="findBySqlFilter">
  <batchLoadSelection>
    simsCollege { simsClasses }
  </batchLoadSelection>

  <source>
    select o
    from SimsClass o
    where 1=1
    <sql:filter>and o.classId in (:ids)</sql:filter>
  </source>
</eql>
```

### 4.2 Enable data permission filtering

When the enableFilter attribute is set to true, a corresponding data permission filter is automatically appended for each entity. Combined with the NopORM engine’s built-in IEqlAstTransformer mechanism, EQL can be strictly validated and restricted.

```xml
<eql name="findFirstByName" enableFilter="true" sqlMethod="findFirst">
    <source>
        select u from NopAuthUser u where u.userName like ${'%' + name + '%'}
    </source>
</eql>
```

In general, for security reasons, we do not grant users permission to edit the EQL language. However, with enableFilter and astTransformer mechanisms, we can effectively restrict the data scope accessible via EQL and eliminate SQL injection attacks.

### 4.3 Multi-datasource support

Use the querySpace attribute to select a different DataSource and thereby access different databases:

```xml
<sql querySpace="report">
  ...
</sql>
```

In `beans.xml`, register the corresponding DataSource. The bean id format is `nopDataSource_{querySpace}`:

```xml
<bean id="nopDataSource_report"  class="com.zaxxer.hikari.HikariDataSource">
  ...
</bean>
```

### 4.4 Use native SQL to retrieve entity objects

Typically, we use the `<eql>` node to load entity data. However, if rowType is set to the entity type, the `<sql>` node can also be used to load entity data.

Once the result is wrapped as an entity object, associated properties automatically support lazy loading.

```xml
  <sql name="testOrmEntityRowMapper" rowType="io.nop.app.SimsClass" sqlMethod="findFirst"
       colNameCamelCase="true" >
      <source>
          select o.class_id, o.class_name, o.college_id
          from sims_class o
      </source>
  </sql>
```

* With colNameCamelCase enabled, returned column names like `class_id` are automatically converted to entity property names like `classId`.
* If the SQL result does not include the primary key field, a new entity object is created. Otherwise, the entity is loaded by id from the current OrmSession and its properties are updated.
* If the corresponding entity data has already been loaded into memory and modified before executing the SQL, execution will throw the exception `nop.err.orm.entity-prop-is-dirty`. If it has not been modified, the entity’s properties will be updated.
* You can change this behavior via the ormEntityRefreshBehavior property. errorWhenDirty is the default. useFirst preserves the first loaded entity data and ignores the current SQL query results. useLast uses the latest query results.

For more details, see [sql-lib.md](../../dev-guide/orm/sql-lib.md)

### 4.5 Use directly as a data dictionary

If the sql name ends with `_dict`, you can call it via DictProvider; the result is wrapped as a DictBean object.

```javascript
 DictBean dict = DictProvider.instance().getDict(null, "sql/test.demo_dict", null, scope);
```

The SQL must include value and label fields:

```xml
<eql name="demo_dict">
    <source>
        select o.collegeId as value, o.collegeName as label
        from SimsCollege o
    </source>
</eql>
```

<!-- SOURCE_MD5:e5973ea9832c6e6117caec4a176940ab-->
