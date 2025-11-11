# Unified SQL Management

When we need to construct fairly complex SQL or EQL statements, managing them through an external model file is undoubtedly of great value. MyBatis provides a mechanism to model SQL statements, yet many developers still prefer dynamically concatenating SQL in Java code using solutions like QueryDsl. This essentially indicates that
**MyBatis’s implementation is rather thin and fails to fully leverage the advantages of modeling**.

In NopOrm, we use the sql-lib model to centrally manage all complex SQL/EQL/DQL statements. Leveraging the existing infrastructure of the Nop platform, implementing a MyBatis-like SQL statement management mechanism takes roughly 500 lines of code. See the implementation:

[SqlLibManager](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-persistence/nop-orm/src/main/java/io/nop/orm/sql_lib/SqlLibManager.java)

[SqlItemModel](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-persistence/nop-orm/src/main/java/io/nop/orm/sql_lib/SqlItemModel.java)

[SqlLibInvoker](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-persistence/nop-orm/src/main/java/io/nop/orm/sql_lib/proxy/SqlLibInvoker.java)

See the test sql-lib file:

[test.sql-lib.xml](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-orm/src/test/resources/_vfs/nop/test/sql/test.sql-lib.xml)

Video: [How to implement MyBatis-like functionality with 500 lines of code](https://www.bilibili.com/video/BV1xX4y1e7Tv/)

sql-lib provides the following features

## 1  Unified management of SQL/EQL/DQL

The sql-lib file contains three kinds of nodes: sql/eql/query correspond to SQL statements, EQL statements, and the Runqian DQL query model introduced in the previous section. They can be managed in a unified manner.

```xml

<sql-lib>
    <sqls>
        <sql name="xxx">...</sql>
        <eql name="yyy">...</eql>
        <query name="zz">...</query>
    </sqls>
</sql-lib>
```

The first benefit of modeling is the Nop platform’s built-in Delta customization mechanism. Suppose we have already developed a Base product, and during deployment at a customer site we need to optimize SQL based on the customer’s data profile; then we
**do not need to modify any Base product code**. We only need to add a Delta-based sql-lib model file to customize any SQL statement. For example:

```xml

<sql-lib x:extends="raw:/original.sql-lib.xml">
    <sqls>
        <!-- SQL statements with the same name will override definitions in the base file -->
        <eql name="yyy">...</eql>
    </sqls>
</sql-lib>
```

Another common usage of Delta customization is in combination with a metaprogramming mechanism. If our system has a very regular domain model with a large number of similar SQL statements, we can use metaprogramming to automatically generate these SQL statements at compile time, and then refine them via Delta customization. For example:

```xml

<sql-lib>
    <x:gen-extends>
        <app:GenDefaultSql />
    </x:gen-extends>

    <sqls>
        <!-- You can customize the auto-generated SQL here -->
        <eql name="yyy">...
        </eql>
    </sqls>
</sql-lib>
```

## 2. Component abstraction capability of XPL templates

MyBatis only provides a few fixed tags such as foreach/if/include. It is quite inadequate when it comes to writing highly complex dynamic SQL statements. Many developers find stitching SQL in XML troublesome; fundamentally this is because MyBatis offers an incomplete solution—it
**lacks a mechanism for secondary abstraction**. In Java programs we can always use function encapsulation to reuse certain SQL concatenation logic, whereas MyBatis offers only a handful of built-ins and basically no support for reuse.

NopOrm adopts the XPL template language from XLang as its underlying generation engine, thereby inheriting XPL’s tag abstraction capabilities.

> XLang is a programming language born for Reversible Computation theory. It comprises XDefinition/XScript/Xpl/XTransform, and its core design focuses on generation, transformation, and Delta merging of abstract syntax trees (AST). You can think of it as a programming language designed for tree grammars.

```xml

<sql name="xxx">
    <source>
        select
        <my:MyFields/>
        <my:WhenAdmin>
            ,
            <my:AdmninFields/>
        </my:WhenAdmin>
        from MyEntity o
        where
        <my:AuthFilter/>
    </source>
</sql>
```

The Xpl template language not only includes `<c:for>`, `<c:if>` and other syntax elements required by a Turing-complete language, but also allows introducing new tag abstractions through a customizable tag mechanism (similar to front-end Vue component encapsulation).

Some template languages require pre-registering all functions used in templates, whereas Xpl can directly call Java.

```xml

<sql>
    <source>
        <c:script>
            import test.MyService;

            let service = new MyService();
            let bean = inject("MyBean"); // Directly obtain a bean registered in the IoC container
        </c:script>
    </source>
</sql>
```

## 3. Metaprogramming capabilities of Macro tags

MyBatis’s way of concatenating dynamic SQL is clumsy, so some MyBatis-like frameworks offer specially designed simplified syntax at the SQL template level. For example, some frameworks introduce implicit conditional mechanisms:

```sql
select xxx
from my_entity
where id = :id
[and name=:name]
```

By automatically analyzing variables inside the brackets, an implicit condition is added. Only when the value of name is not empty will the corresponding SQL fragment be output.

In NopOrm, we can implement similar **local syntactic structure transformations** using macro tags:

```xml

<sql>
    <source>
        select o from MyEntity o
        where 1=1
        <sql:filter>and o.classId = :myVar</sql:filter>
    </source>
</sql>
```

`<sql:filter>` is a macro tag executed at compile time, essentially transforming the source structure. It is equivalent to the following handwritten code:

```xml

<c:if test="${!_.isEmpty(myVar)}">
    and o.classId = ${myVar}
</c:if>
```

See the implementation of specific tags:

[sql.xlib](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-persistence/nop-orm/src/main/resources/_vfs/nop/orm/xlib/sql.xlib)

This concept is essentially equivalent to macros in Lisp, especially in that it can be used in any part of program code (i.e., any AST node can be replaced by a macro node). It just adopts an XML representation, which is more human-friendly compared to Lisp’s terse mathematical-symbol style.

Microsoft C# language’s LINQ (Language Integrated Query) achieves its syntax by obtaining the expression’s AST at compile time and then delegating structural transformation to application code—essentially a compile-time macro transformation technique. In XLang, apart from macro tags provided by Xpl templates, you can use XScript macro functions to transform between SQL syntax and object syntax. For example:

```xml

<c:script><![CDATA[
    function f(x,y){
    return x + y;
    }
    let obj = ...
    let {a,b} = linq `
    select sum(x + y) as a , sum(x * y) as b
    from obj
    where f(x,y) > 2 and sin(x) > cos(y)
    `
]]></c:script>
```

XScript’s template expressions automatically recognize macro functions and execute them at compile time. Thus we can define a macro function linq, which parses the template string into an SQL AST at compile time, then transforms it into a regular JavaScript AST, effectively embedding a SQL-like DSL into the object-oriented XScript syntax (similar to TypeScript). This accomplishes LINQ-like functionality with a much simpler implementation, and in a form closer to SQL’s original style.

> The above is only a conceptual example. Currently the Nop platform only provides macro functions such as xpath/jpath/xpl, and does not include a built-in linq macro function.

## 4. SQL output mode of the template language

Compared to general-purpose programming languages, a template language is biased to treat output (a side effect) as a first-class concept. When nothing is specially marked, it represents output; if other logic needs to be described, expressions/tags explicitly isolate it. As a generic template language, Xpl strengthens the notion of output and introduces a multi-mode output design.

Xpl supports multiple Output Modes:

* text: plain text output, no extra escaping
* xml: XML format output, automatically escaped per XML rules
* node: structured AST output, with source locations preserved
* sql: supports SQL object output, preventing SQL injection attacks

The sql mode adds special handling for SQL output, mainly the following rules:

1. If an object is output, it is replaced with ?, and the object is collected into a parameter set. For example, `id = ${id}` actually generates `id=?` in SQL text and uses a List to store parameter values.
2. If a collection object is output, it automatically expands into multiple parameters. For example, `id in (${ids})` generates `id in (?,?,?)`.

If you do want to output SQL text directly and concatenate it into SQL, you can wrap it with the raw function.

```sql
from MyEntity_${raw(postfix)} o
```

In addition, NopOrm provides a simple wrapper model for parameterized SQL objects themselves:

```java
SQL = Text + Params
```

Using sql = SQL.begin().sql("o.id = ? ", name).end() constructs a parameterized SQL object. Xpl’s sql output mode automatically recognizes SQL objects and handles the text and parameter collections separately.

## 5. Automatic validation

Managing SQL templates in external files has a drawback: it cannot rely on the type system for validation and must rely on runtime testing to check SQL correctness. If the data model changes, it may not be immediately obvious which SQL statements are affected. There are, however, some fairly simple solutions. Since SQL statements are already managed as structured models, the ways we can operate on them become abundantly rich.
NopOrm has a mechanism similar to Contract Based Programming: each EQL statement model supports a validate-input configuration where we can prepare test data. When the ORM engine loads a sql-lib, it automatically runs validate-input to obtain test data, then executes the SQL template based on the test data to produce an EQL statement. The EQL parser then analyzes its validity, thereby implementing a quasi-static analysis to check consistency between the ORM model and EQL statements.

## 6. Debugging support

Unlike MyBatis’s built-in, homemade, lightweight template language, NopOrm uses the Xpl template language to generate SQL statements, so it naturally leverages the XLang debugger. The Nop platform provides an IntelliJ IDEA plugin with DSL syntax hints and breakpoint debugging. It automatically reads the sql-lib.xdef meta-model definition file, validates sql-lib files using the meta-model, provides syntax hints, supports adding breakpoints in the source block, and single-stepping, etc.

All DSLs on the Nop platform are built on the principles of Reversible Computation and use the unified meta-model definition language XDefinition for descriptions, so there is no need to develop separate IDE plugins and debuggers for each DSL. To add IDE support for a custom sql-lib model, the only requirement is to add the attribute x:
schema="/nop/schema/orm/sql-lib.xdef" on the model root node to reference the xdef meta-model.

XLang also has some built-in debugging features that help diagnose issues during metaprogramming:

1. AST nodes generated under outputMode=node automatically retain source file line numbers, so when compiled code reports errors, we can directly map them back to source locations.
2. Xpl template language nodes can add the xpl:dump attribute to print the AST produced by dynamic compilation of the current node.
3. Any expression can append the extension function `$`, which automatically prints the text, line number, and the result of the expression, and returns the expression’s result. For example:

```java
x = a.f().$(prefix)
// Corresponds to
x = DebugHelper.v(location,prefix, "a.f()",a.f())
```

## 7. Generate SQL statements based on Dialect

Tag libraries can introduce various custom extension logic—for example, generating different SQL statements according to the database dialect.

```xml
select
<sql:when-dialect name="h2">
    ...
</sql:when-dialect>
from my_entity
```

## 8. Mapper interfaces

As long as you add a mapper tag for an entity in the Excel data model, code generation will automatically produce type-safe Mapper interfaces similar to MyBatis. Through them you can invoke SQL model files managed by SqlLibManager. For example [LitemallGoodsMapper.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master).

```java

@SqlLibMapper("/app/mall/sql/LitemallGoods.sql-lib.xml")
public interface LitemallGoodsMapper {

    void syncCartProduct(@Name("product") LitemallGoodsProduct product);
}
```

Use the SqlLibMapper annotation to specify the SQL model file associated with the current interface.

## 9. Load entity objects using native SQL
In most cases we use the `<eql>` node to load entity data. However, if rowType is set to an entity type, the `<sql>` node can also be used to load entity data.

```xml
  <sql name="testOrmEntityRowMapper" rowType="io.nop.app.SimsClass" sqlMethod="findFirst"
       colNameCamelCase="true" >
      <source>
          select o.class_id, o.class_name, o.college_id
          from sims_class o
      </source>
  </sql>
```

* When colNameCamelCase is set, return column names like `class_id` are automatically converted to entity property names like `classId`.
* If the SQL result does not include the primary key field, a new entity object will be created; otherwise the entity will be loaded by id from the current OrmSession and its properties updated.
* If, before executing SQL, the corresponding entity data has already been loaded into memory and modified, executing SQL will throw `nop.err.orm.entity-prop-is-dirty`. If it has not been modified, the entity properties will be updated.
* You can change this behavior via ormEntityRefreshBehavior. errorWhenDirty is the default behavior. useFirst retains the first loaded entity data and ignores the current SQL query’s data. useLast takes the data from the most recent query.

## 10. Pass in a Map or JavaBean
For cases with many parameters, you can aggregate them into a Map parameter or a JavaBean object.

```java
interface MyMapper{
  List<MyEntity> findByXX(@Name("query")MyQuery query);
}
```
In sql-lib you can access them using expressions or composite properties.

```xml
<eql name="findByXX" >
  <source>
    select o from MyEntity o
    where o.fldA = ${query.fldA}
    <sql:filter> and o.fldB = :query.fldB </sql:filter>
  </source>
</eql>
```

## 11. Field Mapping

```xml
<sql name="findByXX" colNameCamelCase="true">
  <fields>
    <field name="my_date" as="myDate2" stdSqlType="DATE" />
  </fields>
</sql>
```

1. **`stdSqlType` attribute**
  - Specifies the SQL type used when reading data from `IDataSet` (e.g., `DATE`/`TIMESTAMP`/`BLOB`)
  - The system calls the corresponding method based on the type (e.g., `getDate()` instead of `getObject()`)
  - Defaults to `getObject()` if not specified

2. **`as` attribute**
  - Renames the field (e.g., `my_date → myDate2`)
  - Supports nested properties (e.g., `a.b.c` produces `{a: {b: {c: value}}}`)
  - If not specified:
    - If `colNameCamelCase=true`, automatically converts to camelCase (e.g., `user_name → userName`)
    - Otherwise keeps the original column name

3. **`colNameCamelCase` attribute**
  - Global switch (located on the `<sql>` node)
  - Enables camelCase conversion for all fields without an explicit `as`
  - Priority: explicit `as` > camelCase conversion > original column name

---

## 12. Row Type (rowType)

```xml
<sql name="findByXX" rowType="xxx.MyEntity" colNameCamelCase="true">
  ...
</sql>
```

1. **Entity type**
  - When `rowType` is an ORM entity class (e.g., `xxx.MyEntity`):
    - If the query result includes the entity’s **primary key field**, the entity object is automatically constructed
    - The entity is bound to the current `OrmSession` (supports lazy loading/dirty checking and other ORM features)

2. **Non-entity type**
  - When `rowType` is a regular DTO or Map:
    - The query result is directly converted to the specified type
    - Supports basic types (e.g., `rowType="java.lang.Integer"`)
    - Supports nested objects (must match field mapping rules)

3. **`colNameCamelCase` working in concert**
  - CamelCase conversion also applies to `rowType` mapping
  - Ensure the converted field names match target class property names

#### Notes:
- If the entity primary key does not appear in the query result, it falls back to a plain object (without ORM features)
- Nested property paths (e.g., `a.b.c`) require the target class to have the corresponding structure


## Comparison with MyBatis

| MyBatis         |Nop Platform|
|-----------------|---|
| Dynamic SQL via XML configuration    | Configuration fixes via unified Delta customization|
| Encapsulate SQL execution via Mapper interfaces | Nop Platform uses the unified @Name annotation to define parameter names and passes context via IEvalContext|
| Generate dynamic SQL via a few fixed tag functions | Introduce custom tags via the Xpl tag library|
| Generate SQL parameters via expressions    | Expressions use a general-purpose expression engine; Xpl’s SQL output mode converts expression outputs into SQL parameters|
| Supports transactions, result data caching, etc.    | Using the Dao layer’s JdbcTemplate, transactions and result caching are supported automatically|
| Manage SQL statements         | Manage EQL, SQL, DQL, and other query languages together|

With the Nop platform’s built-in mechanisms, you also automatically get the following:

1. Multiple data sources, multi-tenancy, and database/table sharding

2. Expose SQL statements directly as dictionary tables accessible from the frontend; the dictionary table name is sql/{sqlName}

3. When using the EQL query language, support batch property loading. After obtaining result data, you can directly specify associated properties to load.

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

## Extension configuration

### Set enableFilter to true to enable data permission filtering

OrmSessionFactory supports configuration of IEntityFilterProvider; the default implementation provided by the nop-auth-service module corresponds to data permission filtering.

```xml

<eql name="xxx" enableFilter="true">
    <source>
        select u.xx from MyEntity u, OtherEntity t where u.fldA = t.fldA
    </source>
</eql>
```

You can also specify enableFilter directly when constructing an SQL object.

```java
SQL sql = SQL.begin().enableFilter(true).sql("...").end();
```

After enabling enableFilter, `IServiceContext.bindingCtx()` is automatically used to obtain the IServiceContext from the current context, and `IDataAuthChecker.getFilter()`
is called to obtain the data permission filter condition. It is converted to SQL and concatenated to the original SQL statement.

> If users are allowed to write SQL directly, you can leverage enableFilter to automatically append data permission filters and avoid data leaks.

### Set allowUnderscoreName to true to allow direct use of database column names in EQL

```xml
<eql name="xx" allowUnderscoreName="true">
  <source>
     select o.statusId, o.status_id from MyEntity o
  </source>
</eql>
```

Either statusId or status_id can access the entity’s property.

You can also specify allowUnderscoreName directly when constructing an SQL object.

```java
SQL.begin().allowUnderscoreName(true).sql("....").end();
```
<!-- SOURCE_MD5:5e5c48e9ea45683d1b339b323775472c-->
