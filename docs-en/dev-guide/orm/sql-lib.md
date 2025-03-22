# Unified SQL Management

When constructing complex SQL or EQL statements, managing them through an external model file is undoubtedly of significant value. MyBatis provides a mechanism to modelize SQL statements, but many still prefer dynamically concatenating SQL using QueryDsl within Java code. This essentially highlights
**MyBatis's limited functionality**, failing to fully leverage the potential of modelization.

In NopOrm, we use the sql-lib model to uniformly manage all complex SQL/EQL/DQL statements. By leveraging existing infrastructure in the Nop platform, implementing a similar mechanism to MyBatis for managing SQL statements can be achieved with approximately 500 lines of code. For specific implementation details, refer to

[SqlLibManager](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-orm/src/main/java/io/nop/orm/sql_lib/SqlLibManager.java)

[SqlItemModel](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-orm/src/main/java/io/nop/orm/sql_lib/SqlItemModel.java)

[SqlLibInvoker](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-orm/src/main/java/io/nop/orm/sql_lib/proxy/SqlLibInvoker.java)

For testing purposes, refer to the sql-lib file:

[test.sql-lib.xml](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-orm/src/test/resources/_vfs/nop/test/sql/test.sql-lib.xml)

Video: [How to Implement MyBatis-like Functionality with 500 Lines of Code](https://www.bilibili.com/video/BV1xX4y1e7Tv/)

**sql-lib Features**

## 1. Unified Management of SQL/EQL/DQL

In the sql-lib file, there are three nodes corresponding to SQL statements, EQL statements, and the query model discussed in the previous section. These can be managed uniformly.

```xml
<sql-lib>
    <sqls>
        <sql name="xxx">...</sql>
        <eql name="yyy">...</eql>
        <query name="zz">...</query>
    </sqls>
</sql-lib>
```

The first benefit of modelization is the built-in Delta customization mechanism in the Nop platform. Suppose you have developed a Base product and need to optimize SQL queries for specific customer data during deployment. You
**do not need to modify any Base product code**, only add a delta model file in sql-lib. This allows for customizing any SQL statement.

```xml
<sql-lib x:extends="raw:/original.sql-lib.xml">
    <sqls>
        <!-- Same-named SQL statements will override definitions in the base file -->
        <eql name="yyy">...</eql>
    </sqls>
</sql-lib>
```

Another common use of Delta customization is combined with meta-programming. Suppose your system is a well-structured domain model with many similar SQL statements. You can use meta-programming to generate these SQL statements at compile time and then refine them using Delta customization.

```xml
<sql-lib>
    <x:gen-extends>
        <app:GenDefaultSql />
    </x:gen-extends>

    <sqls>
        <!-- Customization of automatically generated SQL -->
        <eql name="yyy">...</eql>
    </sqls>
</sql-lib>
```

## 2. XPL Template's Component Abstractability

MyBatis only provides a limited set of tags like foreach, if, include, etc. Writing highly complex dynamic SQL statements can be described as half-hearted. Many find it cumbersome to work with XML for constructing SQL, essentially because MyBatis offers an incomplete solution.

# Lack of Double Abstraction Mechanism

In Java programs, we can encapsulate SQL concatenation logic by wrapping it in functions. However, MyBatis only provides a one-size-fits-all approach with its built-in macros, offering very limited support for reusable logic.

NopOrm leverages XLang's XPL template language as its underlying rendering engine, inheriting the abstraction capabilities of XPL templates.

# XLang Language

XLang is designed specifically for reversible computation theories. It consists of multiple components such as XDefinition/XScript/Xpl/XTransform and is focused on generating, transforming, and merging abstract syntax trees (ASTs), making it akin to a Tree-based language.

```xml
<sql name="xxx">
    <source>
        select
        <my:MyFields/>
        <my:WhenAdmin>,
            <my:AdmninFields/>
        </my:WhenAdmin>
        from MyEntity o
        where
        <my:AuthFilter/>
    </source>
</sql>
```

Xpl templates not only include essential syntax elements like `<c:for>` and `<c:if>`, but also allow custom tag abstraction through a user-defined tag mechanism, similar to Vue components.

# Macro Tags in Templates

Some template systems require all functions usable within the template to be pre-registered. However, Xpl templates allow direct Java calls without prior registration.

```xml
<sql>
    <source>
        <c:script>
            import test.MyService;

            let service = new MyService();
            let bean = inject("MyBean"); // Directly retrieve beans from the IOC container
        </c:script>
    </source>
</sql>
```

# Macro Tag's Meta-Programming Ability

MyBatis concatenates dynamic SQL in a very inefficient manner. Some MyBatis-like frameworks provide special syntax at the SQL template level for simplified logic, such as conditional statements.

```sql
select xxx
from my_entity
where id = :id
[and name=:name]
```

By automatically analyzing variable definitions within parentheses, NopOrm adds an implicit condition check only when the `name` attribute is not empty.

In NopOrm, we can achieve similar **local syntax transformation** using macro tags:

```xml
<sql>
    <source>
        select o from MyEntity o
        where 1=1
        <sql:filter>and o.classId = :myVar</sql:filter>
    </source>
</sql>
```

`<sql:filter>` is a macro tag that executes at compile time, effectively transforming the code structure. It is equivalent to writing:

```xml
<c:if test="${!_.isEmpty(myVar)}">
    and o.classId = ${myVar}
</c:if>
```

For specific implementations of this macro tag, refer to:

[sql.xlib](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-orm/src/main/resources/_vfs/nop/orm/xlib/sql.xlib)

In essence, this concept is equivalent to macros in Lisp, especially since it allows for replacement of any AST node with a macro. However, unlike Lisp's minimalistic symbol-based syntax, XLang uses XML-like structures, making it more user-friendly.

# Macro Tags in C#

For C#, Microsoft's LINQ provides a query syntax that compiles at runtime into method calls. While this is efficient, it lacks the flexibility of true macro support. In contrast, Xpl templates provide both built-in macros and custom tag abstraction for dynamic SQL handling.

The final translated content ends here:

```xml
<!-- c:script -->
<c:script>
<![CDATA[
    function f(x, y) {
        return x + y;
    }
    let obj = ...;
    let { a, b } = linq `
    select sum(x + y) as a , sum(x * y) as b
    from obj
    where f(x, y) > 2 and sin(x) > cos(y)
`]];
</c:script>
```

XScript template expressions will automatically identify macro functions and execute them at compile time. Therefore, we can define a macro function `linq` that parses the template string into an SQL syntax tree at compile time and then transforms it into standard JavaScript
AST. This effectively embeds embedded SQL-like DSL similar to LINQ into the object-oriented XScript syntax (similar to TypeScript), enabling capabilities similar to LINQ but with a much simpler implementation and structure.

> The above is just an example of a concept. Currently, the Nop platform only provides macro functions like xpath/jpath/xpl and does not provide built-in `linq` macro functions.

## 4. SQL Output Pattern

In comparison to regular programming languages, template language's design bias is to treat output (Output) as the first class concept. When no special modifications are applied, it handles standard output. If we want to execute other logic, we must isolate these using expressions, labels, etc. Xpl template language enhances this concept by strengthening output patterns and enabling multipattern outputs.

Xpl template language supports various output modes (Output Mode):

- **text**: Standard text output without additional escaping.
- **xml**: XML formatted text output with automatic XML escaping.
- **node**: Structural node output that retains source location.
- **sql**: Support for SQL object output to prevent SQL injection attacks.

The sql mode provides special handling for SQL outputs, adding the following rules:

1. For output objects, replace them with '?' and collect the object into a parameter collection. For example, `id = ${id}` will generate id=?, collecting the value into parameters.
2. For output collections, expand them into multiple parameters. For example, `id in (${ids})` will generate IN (?,?), expanding the collected parameter values.

If we truly want to directly output SQL text and concatenate it into the SQL statement, we can use the raw function to wrap it.

```sql
from MyEntity_${raw(postfix)} o
```

Additionally, NopOrm has established a simple wrapping model for parameterized SQL objects:

```java
SQL = Text + Params
```

Using `sql = SQL.begin().sql("o.id = ? ", name).end()` enables constructing parameterized SQL statements. The xpl template's sql output mode will automatically identify SQL objects and handle text and collection parameters separately.

## 5. Automatic Validation

The external file managing the SQL template has a drawback: it cannot rely on the type system for validation; instead, it depends on runtime testing to check SQL correctness. If the data model changes, it may be challenging to immediately identify affected SQL statements.
 
However, there are straightforward solutions. Since SQL statements are managed as structured models, we have powerful tools to manipulate them. NopOrm includes a mechanism similar to Contract Based Programming: each EQL statement model supports a `validate-input` configuration. We can prepare test data in advance. When the ORM engine loads `sql-lib`, it automatically executes `validate-input` to generate test data and runs the SQL template, allowing the EQL parser to validate its legality, achieving static verification of ORM models and EQL statements.

## 6. Debugging Support

Unlike MyBatis's built-in lightweight template language, NopOrm uses Xpl templates to generate SQL statements, making it natural to utilize XLang debugging tools. The Nop platform provides an IDEA plugin supporting DSL syntax suggestions and breakpoint debugging. It reads `sql-lib.xdef` based on the model root node:

```java
// In IDEA:
from MyEntity_${raw(postfix)} o
```

NopOrm's DSLs are built on reversible computation principles, enabling unified modeling with XDefinition. Therefore, we don't need separate IDE plugins or debuggers for each DSL. To support SQL-LIB models in IDEA:

1. Add this to your model root:
   ```xml
   <x:property name="schema">/nop/schema/orm/sql-lib.xdef</x:property>
   ```
2. Import the XDefinition-based model for debugging.

This setup ensures that NopOrm's ORM and EQL models can be effectively debugged using standard tools, achieving consistent and efficient development practices.

The XLang language incorporates built-in debugging features to facilitate issue diagnosis during the meta-programming phase.

1. **outputMode=node**  
   In output mode set to "node", generated AST nodes automatically retain the line numbers from the source file. This allows for precise error mapping back to the original code when compilation errors occur.

2. **Xpl Template Language Extension**  
   Xpl template nodes can be extended with the `xpl:dump` attribute, which prints out the AST structure dynamically generated after debug compilation.

3. **Expression Extensions**  
   Any expression can be extended by calling the `$` method, which automatically logs the corresponding text, line number, and execution result of the expression. For example:

   ```java
   x = a.f().$(prefix)
   ```
   This corresponds to:
   ```java
   x = DebugHelper.v(location, prefix, "a.f()", a.f());
   ```


## Generating SQL Statements Based on Dialect
Using tags allows for the introduction of various custom extension logic. For example, generating different SQL statements based on database dialects.

```xml
select
<sql:when-dialect name="h2">
    ...
</sql:when-dialect>
from my_entity
```


## Mapper Interface Generation
Adding `mapper` tags within an Excel data model generates corresponding MyBatis-like strongly-typed Mapper interfaces. These interfaces can then be used to call SQL models managed by SqlLibManager. For example, the interface for [LitemallGoodsMapper.java](https://gitee.com/canonical-entropy/nop-app-mall/blob/master/app-mall-dao/src/main/java/app/mall/dao/mapper/LitemallGoodsMapper.java) is defined as follows:

```java
@SqlLibMapper("/app/mall/sql/LitemallGoods.sql-lib.xml")
public interface LitemallGoodsMapper {

    void syncCartProduct(@Name("product") LitemallGoodsProduct product);
}
```


## Loading Entities with Native SQL
Normally, we use the `<eql>` node to load entity data. However, if `rowType` is set to an entity type, we can also use the `<sql>` node for loading.

```xml
<sql name="testOrmEntityRowMapper" rowType="io.nop.app.SimsClass" sqlMethod="findFirst"
     colNameCamelCase="true">
    <source>
        select o.class_id, o.class_name, o.college_id
        from sims_class o
    </source>
</sql>
```

* **colNameCamelCase="true"** will automatically convert return field names like `class_id` into properties like `classId`.
* If the SQL query returns a primary key and the corresponding entity is not already loaded, a new entity will be created. Otherwise, it will load the existing entity from the OrmSession.
* If the SQL query resultset contains modified entities, an exception `nop.err.orm.entity-prop-is-dirty` will be thrown if the entity data has changed since the last update. If no changes are detected, the entity's properties will be updated with the query results.
* The behavior can be customized using `ormEntityRefreshBehavior`. The default behavior is `errorWhenDirty`, which skips updating entities unless an error occurs. The `useFirst` option retains the first loaded instance, while `useLast` uses the last query result.


## Comparison with MyBatis


| **MyBatis**       | **Nop Platform** |
|------------------|-----------------|
| XML Configuration for Dynamic SQL | Unified Delta Customization for Configuration Adjustment |
| Mapper Interfaces for SQL Execution | Nop Platform uses unified `@Name` annotation to define parameter names, transmitted via `IEvalContext` |
| Fixed Tag Functions for Dynamic SQL Generation | Nop Platform utilizes Xpl tags for custom tag definitions |
| Expression-based Parameter Setting | General expression engine used for parameter generation, leveraging Xpl template language for SQL output format |
| Transaction Support and Result Caching | JdbcTemplate in DAO layer supports transactions and result caching |
| Management of SQL Statements | Simultaneous management of EQL, SQL, DQL, etc., query languages |

Nop Platform also provides built-in mechanisms for additional functionalities:

1. **Multiple Data Sources, Tenants, and Sharding**  
2. **Direct exposure of SQL statements in a dictionary table**, with the dictionary name format `sql/{sqlName}`  
3. **Batch loading support in EQL queries**, enabling association attribute specification after query execution  

```xml
<eql name="findActiveTasks">
    <batchLoadSelection>
        relatedEntity{ myProp }, myParent{ children }
    </batchLoadSelection>

    <source>
        select o from MyEntity o where o.status = 1
    </source>
</eql>
```


## Extended Configuration


### EnableFilter Property Set to True
Enables data permission filtering via the built-in mechanisms of Nop Platform. The `OrmSessionFactory` supports `IEntityFilterProvider`, which is provided by the `nop-auth-service` module.

```xml
<eql name="xxx" enableFilter="true">
    <source>
        select u.xx from MyEntity u, OtherEntity t where u.fldA = t.fldA
    </source>
</eql>
```

Additionally, the `enableFilter` property can be set directly when constructing the SQL object:

```java
SQL sql = SQL.begin().enableFilter(true).sql("...").end();
```

When `enableFilter` is enabled, it automatically utilizes `IServiceContext(bindingCtx())` to retrieve the current context and invoke `IDataAuthChecker.getFilter()` to obtain data permission filtering conditions, which are then appended to the original SQL statement.

> If direct SQL statement composition is allowed, `enableFilter` can be used to automatically append data permission filtering conditions, preventing unauthorized data access.


### AllowUnderscoreName Property Set to True
Permits direct use of database column names in EQL queries by enabling underscored attribute access.

```xml
<eql name="xx" allowUnderscoreName="true">
    <source>
        select o.statusId, o.status_id from MyEntity o
    </source>
</eql>
```

Attributes like `statusId` or `status_id` can both be accessed via the entity object.

The `allowUnderscoreName` property can also be set directly when constructing the SQL object:

```java
SQL sql = SQL.begin().allowUnderscoreName(true).sql("....").end();
```

