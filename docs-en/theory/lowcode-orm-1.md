# What Kind of ORM Engine Does a Low-Code Platform Need? (1)

A low-code platform seeks to minimize the amount of hand-written code. The core tools it can rely on are various explicitly constructed information models, such as data models, form models, process models, report models, etc. Among these, the data model is undoubtedly the most critical. As an ORM (Object Relational Mapping) engine built on the foundation of the data model, what value can it bring to a low-code platform?

To answer this, we need to return to the basics of ORM: What is ORM? Why can ORM simplify the code in the data access layer? Which common business semantics can be uniformly delegated to the ORM layer? In the context of a low-code platform, data structures need to support user-defined adjustments, and the logical path from the front-end presentation to the back-end data storage needs to be compressed as much as possible—what support can an ORM engine provide for this? If we are not satisfied with pre-defined low-code scenarios and instead want a smooth upgrade path from LowCode to ProCode, what requirements should we place on the ORM engine?

This article provides a preliminary theoretical analysis of ORM engine design and implementation based on Reversible Computation theory, and introduces the NopOrm engine used in Nop Platform 2.0. NopOrm roughly encompasses the main functionality of Hibernate + MyBatis + Spring Data JDBC + GraphQL. However, because it uses a large amount of innovative design and, guided by theoretical analysis, makes certain trade-offs in features, the amount of hand-written, effective code required is not large (around or below 20,000 lines). On the relatively streamlined code base, NopOrm actually provides more feature support for business development, and with a general underlying solution based on Reversible Computation theory, NopOrm freely provides flexibility and extensibility that other ORM engines cannot achieve.

## I. What is ORM?

What is ORM? The Hibernate website has long provided an authoritative explanation: [What is Object/Relational Mapping](https://hibernate.org/orm/what-is-an-orm/). Hibernate explains that ORM solves the so-called Object-Relational Impedance Mismatch, i.e., the mismatch between the relational paradigm and the object paradigm, necessitating a framework for adaptation. Specifically, there are five aspects of mismatch:

1. Granularity: Relational databases manage data at the table and column granularity, whereas object models can use richer structural management. For example, address information in a user table may be split into multiple columns and mapped to an Address component class.

2. Inheritance: Object-oriented programming languages generally use class inheritance extensively to reuse existing concepts and implementations, whereas relational databases lack similar means of reuse.

3. Identity: The relational model distinguishes different objects via primary keys, while the object model uses different object pointers; there is a conceptual inconsistency between the two.

4. Associations: The relational model expresses associations between records via foreign keys, whereas the object model expresses associations via object properties.

5. Data Navigation: In an object model, you can traverse the entire object graph using property access like a.b.c, whereas in the relational model you must explicitly specify associated tables, associated columns, and how they join.

Do these five aspects proposed by Hibernate truly reflect the essence of ORM? Here I would like to analyze from a different perspective.

First, if a technology has intrinsic superiority, it must make more complete use of certain information compared to alternative solutions, rather than merely solving a superficial adaptation problem. What information does a better ORM utilize more fully than a mediocre ORM? This involves the biggest secret in relational database theory: relational databases have no relations! Although relational databases never stop talking about relations, the truth is that they store unrelated, independent atomic data that result from decomposing relations!

If we strictly build table models according to the Third Normal Form in relational database theory, then modifying any single column value in principle will not affect other column values!

> Baidu Baike: The Third Normal Form (3rd NF) means that all data elements in a table must not only be uniquely identified by the primary key but must also be independent of each other and have no other functional dependencies. That is, for a data structure that satisfies 2nd NF, there may exist data elements that depend on other non-key data elements, which must be eliminated.

On the surface, databases define primary key and foreign key columns, but apart from providing integrity checks, they do not play a role in actual logical expression. The reason accessing complex associated data via SQL is verbose is that every time we access data we need to explicitly specify which tables and which columns should be joined under what conditions—in other words, association information is injected via code at access time; it is not built-in system knowledge. In fact, developers of many large software systems often pass down an old secret: don’t define foreign key constraints on large tables; they impact performance. Foreign key constraints are basically useless for application development—or if they have any effect, it’s a negative one!

The relational model uses a symmetric access pattern: all tables and columns are equal; there is essentially no difference among them. Via join statements, we can associate any columns of any tables, unconstrained by primary or foreign key concepts. The reason we tend to read records via primary keys is fundamentally that primary keys typically have indexes to speed access. We can similarly define unique indexes on other columns; primary keys have no exclusive special status.

As we move upward from a generic, business-agnostic storage model to an application model amenable to business processing, we inevitably find that certain fields are of special importance in business (symmetry breaking), and their associations are relatively stable and frequently used—there is no need to repeat them every time.

> The uniformity and universality of the relational model is often regarded as theoretical elegance. But the real world is complex, and the direction of development is to gradually identify differences and find natural forms of expression to surface them.
> 
> The uniform relational model is the most symmetric, minimal model. Faced with physical constraints, it implicitly assumes that sets rarely interact; single tables (mapping forms to data tables) and master-detail tables are the most prevalent cases. Try imagining the relational model: mentally, we usually only see two tables; when considering multiple tables, because these tables lack clear distinguishability, their imagery is ambiguous. Only when we become aware of concepts like primary keys, foreign keys, master tables, detail tables, dictionary tables, fact tables, and dimension tables—when symmetry breaks—can the models in our minds become richer.
> 
> In data warehouse theory, star and snowflake schemas emphasize relation decomposition by subject area and allow partial redundancy. They in fact emphasize the non-equivalence of tables—no longer are all tables on the same footing. The distinction between Fact Tables and Dimension Tables is recognized and explicitly handled. From full relation decomposition to partial relation decomposition, we can form a model-level lattice: at different levels of complexity, we can choose specific implementation models under theoretical guidance.

The special value of ORM lies in recognizing the special nature of primary keys and foreign keys, and in implementing the intrinsic expression of pairwise relationships and fully exploiting them.

First, in ORM, primary keys have special meaning: they become the keys of the object cache. Through object caching, ORM ensures that objects with the same primary key correspond to the same object pointer, thereby automatically maintaining the identity a.b.c.a == a; that is, traversing the object graph along different property paths will still reach the same object node.

Second, in ORM, foreign key association information is solidified and reused. Consider the following SQL:

```sql
select * from a, b
where a.fldA = b.fldB
and a.fldC = 1 and b.fldD = 2
```

a.fldA = b.fldB can be called the association condition, while a.fldC = 1 and b.fldD = 2 can be called coordinate conditions. Much of SQL’s complexity stems from frequently having to specify identical association conditions in many places without being able to abstract them as reusable components. In the object space provided by ORM, pairwise associations between objects need only be specified once, and then they are effective across all CRUD operations. Especially in the object query language, pairwise associations can automatically infer multi-entity relationships; for example, a.b.c.d = 3 can automatically be deduced as

```sql
select ... 
from A a join B b on a.xx = b.id join C c on b.yy = c.id
where c.d = 3
```

Thanks to automatic property association, the applicability of single-table models is greatly expanded: any single table automatically becomes a subject table, and any column from an associated table automatically becomes a field directly accessible on the subject table. For example, whether we place a front-end query field a.b.c or a field d, the back-end query processing pipeline can treat them identically. If adopting Domain-Driven Design (DDD), then based on the subject-table entity object, it is easy to implement the so-called Aggregate Root pattern.

Based on the above theoretical analysis, we can see that among the five aspects proposed by Hibernate, granularity and inheritance are relatively secondary concepts—we do not necessarily need to invest heavily in them at the engine core! Conversely, data access engines like MyBatis lack a query language that can leverage object association relationships; they cannot be considered a complete ORM.

> In practice, we can replace inheritance with composition (the evolution of OO over the years has advocated composition over inheritance). In fact, in Java, inheritance combined with lazy loading is conceptually contradictory. Before the actual entity is loaded, a proxy object may need to be created, but its type is undetermined; after the proxy is lazily loaded, the ORM engine cannot convert it to a concrete object type while preserving object pointer uniqueness.

## II. EQL = SQL + AutoJoin

There has long been a criticism of ORM engines: object query syntax is very limiting, especially with poor support for multi-table joins that are not based on primary/foreign keys, no support for arbitrary associations between arbitrary tables, and no support for queries like select * from (select xxx). But is this an inherent problem of ORM engines, or a problem with specific implementations like Hibernate?

From the theoretical analysis above, an object query language that fully leverages object associations is one of the essential values of an ORM engine. So what should the minimal object query language that realizes this essential value be? The EQL (Entity Query Language) in NopOrm is defined as a superset of SQL; it adds a minimal object-association attribute extension on top of SQL query syntax (in principle, it can support all SQL syntax). EQL abandons all Hibernate-introduced object-specific query syntax and only adds support for a.b.c-style property association, so it is very similar to traditional SQL in usage and can naturally support queries such as:

```sql
with a as (
  select o.u ...
)
select a.*, b.d
from a, (select c.xx, c.d from C c where c.d.e > 3) b 
where a.u = b.xx
limit 3 offset 2
```

In NopOrm, both SQL and EQL execution are abstracted into a unified interface ISqlExecutor, and their results are wrapped as IDataSet (a replacement for JDBC’s ResultSet). The only difference at the usage level is that EQL result fields may be objects or object collections, not just atomic data types. See:

[ISqlExecutor](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-persistence/nop-dao/src/main/java/io/nop/dao/api/ISqlExecutor.java)

EQL’s extensions to SQL only involve two places:

1. Explicitly specify associations in the from clause, e.g., from MyEntity o left join o.relField

2. Access object-associated properties via a qualified name, e.g., o.a.b.c

> There is a special handling rule: normally, o.a.b translates to inner joins between tables, but if it appears only in order by o.a.b and is not used elsewhere, left join is preferred to avoid reducing the number of rows in the result set when o.a is null.

Because the translation of object property association syntax is essentially orthogonal to other SQL syntax, it can be implemented in an independent AST Transformer. Therefore, adding new SQL syntax support will not affect the EQL translation. If we adopt the AST auto-parsing technique introduced in the following article, we can even achieve that by merely modifying the antlr g4 grammar definition, EQL-to-SQL conversion is automatically realized, making EQL compatible with all SQL syntax a relatively simple task:

[How Antlr4 Automatically Parses to AST Instead of ParseTree](https://zhuanlan.zhihu.com/p/534178264)

Currently many underlying frameworks need to parse SQL to obtain data structure information. For example:

1. Alibaba’s [Druid DB Connection Pool](https://github.com/alibaba/druid/wiki/%E5%B8%B8%E8%A7%81%E9%97%AE%E9%A2%98) parses SQL to prevent SQL injection and to implement SQL auditing.

2. [Apache ShardingSphere](https://shardingsphere.apache.org/index_zh.html) parses SQL to implement sharding and encryption, etc.

3. Alibaba’s [Seata](https://seata.io/zh-cn/) parses SQL to implement distributed transactions under the AT mode.

Since the ORM engine already implements EQL parsing (a superset of SQL), we can implement similar functionality at very little cost. If these frameworks have good layered isolation, it should even be possible to integrate them directly into the EQL execution engine.

Another long-standing claim about ORM object query languages is that the translation process is opaque and the resulting SQL looks “ugly.” For example:

```sql
select
author0_.id as id1_0_0_,
book2_.title as title3_1_1_,
books1_.bookId as bookId1_2_0__,
from
Author author0_
left outer join
Book book2_
on books1_.bookId=book2_.id
left outer join
Publisher publisher3_
on book2_.publisherid=publisher3_.id
where
author0_.id=100
```

If we abandon all HQL-introduced object-specific query syntax and retain only object property association, then EQL syntax is no more complex or obscure than ordinary SQL. In fact, the correspondence to SQL is very straightforward and intuitive; you can even use SQL syntax alone to access all entity data (SQL syntax is valid EQL syntax). For example:

```sql
select s.statusName
from MyUser o, MyStatus s
where o.statusId = s.id

or
select o.status.statusName
from MyUser o
```

As to why HQL’s translated SQL introduced a lot of auto-generated aliases, making the SQL less friendly—in [Hibernate 6.0](https://www.infoq.com/news/2022/04/red-hat-releases-hibernate-6/), released in April 2022, the Hibernate team gave an explanation: before 6.0, Hibernate always read data from ResultSet by column name, so each column needed a unique alias. In Hibernate 6.0, it reads by column index, so aliases are no longer needed! This change further improved Hibernate 6.0’s performance.

Honestly, this explanation is a bit embarrassing. Why did this happen? Perhaps it’s a lingering side effect from Gavin King not being familiar with the JDBC API when implementing the first version of Hibernate back in 2003.

## III. Dynamic ORM Mapping

In low-code platforms or typical SaaS applications, there is always a need for user-defined data storage. Because different users need to design different storage structures according to their needs, we must provide a dynamic ORM mapping mechanism that can be customized at runtime.

In his article [Low-Code from the Perspective of Implementation Principles](https://zhuanlan.zhihu.com/p/451340998), Wu Duoyi introduces several common user-defined storage schemes for back-end low-code:

1. Map relational databases via dynamic entities

2. Use document databases

3. Use rows instead of columns, i.e., convert a wide (horizontal) table to a vertical table

4. Use metadata + wide table, reserving a large number of columns

5. Use a single file

In the Nop platform, all five schemes can be implemented directly via the NopOrm engine, and they can even coexist within the same OrmSession. That is, we can store some entity data in ordinary database tables, some data in vertical tables, some in Redis caches or ElasticSearch document databases, and other data in data files. At the usage level, they are all ordinary Java objects forming a unified object graph, and the application layer cannot tell which storage mechanism is used underneath. When appropriate, we can even switch storage mechanisms—for example, we might initially use a vertical table to store extension data to avoid changing the database. As data volume grows and business logic stabilizes, we can switch to ordinary tables or wide tables, while keeping the original object structure unchanged at the application layer without needing any changes.

### 3.1 Direct Use of Relational Databases

NopORM supports dynamic property configuration. When a property defined in the entity model is not defined in the Java entity class, it is stored as a dynamic property and converted according to the type specified in the definition, with no difference from ordinary Java property fields at the usage level.

```xml
<entity name="io.nop.app.SimsExam" className="Implementation class, generally the same as entityName">
    <columns>
        ...
        <column name="examScoreScale" propId="20" code="EXAM_SCORE_SCALE" stdSqlType="TINYINT"/>
        <!-- Do not generate Java entity code -->
        <column name="extField" propId="21" code="EXT_FIELD" 
            stdSqlType="INTEGER" notGenCode="true"/>
    </columns>
</entity> 
```

In the above configuration, if the SimsExam entity class has properties examScoreScale and extField, their getters/setters will be used. If not, the property values will be stored in the dynamicValues property collection of the DynamicOrmEntity base class.

The model definition of extField specifies notGenCode=true, indicating that when generating Java entity code from the orm.xml model definition, getters/setters will not be generated for this field, and it will therefore always be accessed as a dynamic property.

If we do not need code generation, we can specify the entity’s implementation class as io.nop.orm.support.DynamicOrmEntity via the className property, thereby treating all fields as dynamic properties.

The Nop platform has built-in support similar to Ruby’s MethodMissing mechanism, allowing dynamic addition of properties to objects. In Java code, we can obtain dynamic property values via BeanTool.getProperty(entity, "extField") or entity.prop_get("extField").

The Nop platform’s built-in scripting language XScript recognizes the IPropGetMissingHook and IPropSetMissingHook extension interfaces. Therefore, when accessing dynamic entity properties in scripts or expressions, the form is the same as accessing ordinary properties:

```java
entity.extField = 3;
let x = entity.examScoreScale;
```

### 3.2 Using Document Databases

In NopOrm’s entity model definition, you can specify different persistDriver for each entity type. For example, persistDriver="elasticSearch" indicates that ElasticSearchEntityPersistDriver will be used to persist the entity. It corresponds to the ORM engine’s IEntityPersistDriver interface and supports batch and asynchronous persistence.

[IEntityPersistDriver](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-persistence/nop-orm/src/main/java/io/nop/orm/driver/IEntityPersistDriver.java)

Meanwhile, for single-entity queries, NopOrm provides unified encapsulation via the IEntityDao.findPage(QueryBean) function. If the PersistDriver implements the IEntityDaoExtension interface, the application layer can use the complex query capabilities provided by the underlying driver through the IEntityDao interface.

[IEntityDao](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-persistence/nop-dao/src/main/java/io/nop/dao/api/IEntityDao.java)

Another extension approach is to store JSON strings in text columns in the relational database, and parse the JSON into a Map at use time. For example:

```xml
<entity name="io.nop.app.SimsClass">
    <columns>
      ...
      <column name="jsonExt"  code="JSON_EXT" propId="101" 
           stdSqlType="VARCHAR" precision="4000" />
    </columns>

    <components>
       <component name="jsonExtComponent" needFlush="true" className="io.nop.orm.support.JsonOrmComponent">
         <prop name="_jsonText" column="jsonExt"/>
       </component>
    </components>
 </entity>
```

In the above example, we use the Component mechanism in NopOrm to parse the jsonText column into a Map object. In the program, we can access the corresponding property as follows:

```java
BeanTool.getProperty(entity,"jsonExtComponent.fld1")
```

If you find the Component configuration somewhat verbose, you can simplify it using the Nop platform’s built-in metaprogramming capabilities. For example, it can be replaced with the following configuration:

```xml
<orm x:schema="/nop/schema/orm/orm.xdef"
     xmlns:x="/nop/schema/xdsl.xdef" xmlns:xpl="/nop/schema/xpl.xdef">

    <x:post-extends>
        <orm-gen:JsonComponentSupport xpl:lib="/nop/orm/xlib/orm-gen.xlib" />
    </x:post-extends>

    <entities>
        <entity name="io.nop.app.SimsClass">
            <columns>
                <column name="collegeId" propId="100" lazy="true"/>
                <column name="jsonExt"  code="JSON_EXT" propId="101" tagSet="json" stdSqlType="VARCHAR" precision="4000" />
            </columns>
            <aliases>
                <alias name="extFld1" propPath="jsonExtComponent.fld1" type="String"/>
            </aliases>
        </entity>
   </entities>
</orm>
```

The <orm-gen:JsonComponentSupport> tag recognizes the tagSet="json" marker on the column and automatically generates the corresponding JsonComponent configuration for that column. At the same time, we can use alias configurations to simplify property names used at the application layer. With the above configuration, in the XScript language and the EQL query language, we obtain the following equivalence:

```
entity.extFld1 == entity.jsonExtComponent.fld1
```

An alias can provide a short property name for a complex property path, thereby hiding the specific underlying storage structure.

Hibernate’s design philosophy is object-paradigm-first, reverse-engineering the relational database storage structure. NopOrm’s philosophy is the opposite: it follows database-first forward design, adheres to relational system normal forms, and proceeds from simple to complex—first mapping all atomic database columns via column, and then gradually constructing more complex ComponentProperty, ComputedProperty, EntityReferenceProperty, EntitySetProperty, and other entangled object structures.

Hibernate’s object-first approach faces intrinsic difficulties when handling complex data relationships. For example, if multiple components and association properties map to the same database column, data conflicts occur. In such cases, which component’s property value should prevail? The relational database’s secret for resolving data conflicts is that when all data structures are decomposed down to atomic data types, all conflicts automatically disappear. A large portion of complexity in Hibernate’s implementation arises from maintaining a very lengthy bidirectional mapping between entangled object structures and clean, independent database columns.

The drawback of using JsonComponent for extended storage is that it is not convenient for queries and sorting. If the underlying database supports a JSON data type, we can perform a local transformation in the EQL AST Transformer and translate entity.jsonExtComponent.fld1 in EQL syntax into the database’s supported JSON property access, e.g., `json_extract(entity, "$.fld1")`.

### 3.3 Using Rows Instead of Columns

Rows and columns are asymmetric in relational databases: adding rows is easy and unbounded, but the number of columns is generally limited, and adding/removing columns is costly (this may change with the growing popularity of columnar databases). If we want a model where rows and columns are symmetric, we can adopt the so-called vertical-table scheme:

```
rowId colId value
```

We can create an extension table with only three columns; rowId and colId can be seen as a symmetric coordinate system, corresponding to row and column coordinates, and value is the value at a given point in the coordinate system.

In implementation, the data structure may be more complex—for example, add a fieldType column to mark the actual data type of value, and add multiple value columns to support correct sorting, or to use built-in date functions, etc.

```
class OrmKeyValueTable{
    String entityId;
    String fieldName;
    byte fieldType;
    String stringValue;
    Integer intValue;
    BigDecimal decimalValue;
    DateTime dateTimeValue;
}
```

How do we convert rows to columns? At the object level, this is equivalent to converting a record in a list into an extended property on an object. In Reversible Computation theory, this is a standard structural transformation: for any collection structure, we can designate a keyProp property for collection elements, thereby converting it into an object property structure.

For example, if keyProp=name is set, then entity.extFields.myKey can be translated as `entity.extFields[row => row.name == 'myKey']`.

> The existence of keyProp is key to defining a stable domain coordinate system. For example, in the front-end virtual DOM diff algorithm, to stably and quickly identify changed components, we need to specify a v-key property for components.

See the configuration example:

[app.orm.xml](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-orm/src/test/resources/_vfs/nop/test/orm/app.orm.xml)

```xml
<entity name="io.nop.app.SimsExam">
    ...

    <aliases>
        <alias name="extFldA" propPath="ext.fldA.string" type="String"/>
        <alias name="extFldB" propPath="ext.fldB.boolean" type="Boolean" notGenCode="true"/>
    </aliases>

    <relations>
        <to-many name="ext" refEntityName="io.nop.app.SimsExtField" keyProp="fieldName">
            <join>
                <on leftProp="_id" rightProp="entityId"/>
                <on leftValue="SimsExam" rightProp="entityName"/>
            </join>
        </to-many>

        <to-many name="examExt" refEntityName="io.nop.app.SimsExamExtField" keyProp="fieldName">
            <join>
                <on leftProp="examId" rightProp="examId"/>
            </join>
        </to-many>
    </relations>
</entity>
```

The above example demonstrates two vertical-table designs: one is a global extension table that supports storing extension fields for all entity tables into a single table, distinguishing different entities via the entityName column; the other is a dedicated extension table, where a separate extension table is created for a specific entity table (see SimsExamExtField).

If we combine row-column transformations with the alias mechanism introduced in the previous section, we can further simplify extension fields. For example, extFldA in the example actually corresponds to ext.fldA.string. In EQL:

```sql
select xxx from SimsExam o where o.extFldA = 'a'
-- will be transformed into
select xxx 
from SimsExam o left join SimeExtField f
   on f.entityId = and f.entityName = 'SimsExam'
where f.fieldName = 'fldA' and f.stringValue = 'a'   
```

Because row-column transformation is built into the EQL AST Transformer, we can actually perform query and sort operations on vertical-table fields—albeit with lower performance.

This row-column conversion capability is fundamentally general and is not limited to KVTable conversions. Any one-to-many child table can be converted into an associated property of the main table by specifying keyProp. For example:

`entity.orders.odr333.orderDate` means “get the orderDate property of the order whose identifier is odr333.”

### 3.4 Metadata + Wide Table

Since the ORM engine itself contains a wealth of metadata, the metadata + wide table pattern is something that general ORM engines can support out of the box. For example:

```xml
<entity name="xxx.MyEntity" tableName="GLOBAL_STORE_TABLE">
   <columns>
      <column name="id" code="ID" stdSqlType="BIGINT" />
      <column name="entityName" code="ENTITY_NAME" 
          stdSqlType="VARCHAR" precision="100" fixedValue="MyEntity" />
      <column name="name" code="VALUE1" stdSqlType="VARCHAR" 
              precision="100" />
      <column name="amount" code="VALUE2" stdSqlType="VARCHAR" 
          precision="100" stdDataType="int" />
   </columns>
</entity>
```

In the above example, all entity data is stored in the unified GLOBAL_STORE_TABLE. For storing data of the MyEntity entity, the value of the entityName column is set to the fixed string "MyEntity". Meanwhile, value1 and value2 are renamed to name and amount. VALUE2 is of type VARCHAR in the database but Integer in Java; by specifying stdDataType we can clearly distinguish data types at these two layers and automatically convert between them. Based on the above definition, we can use EQL to query as if accessing an ordinary database table:

```sql
select * from MyEntity o where o.name = 'a' and o.amount > 3
-- will be translated to
select * from GLOBAL_STORE_TABLE o
where o.ENTITY_NAME = 'MyEntity'
   and o.VALUE1 =  'a' and o.VALUE2 > '3'
```

Leveraging the alias mechanism mentioned in the previous section, we can stitch multiple one-to-one or one-to-many tables into a single logical wide table. For example:

```xml
<entity name="xxx.MyEntityFacade">
   ...
   <aliases>
     <alias name="fldA" propPath="myOneToOneRel.fldA" type="String" />
     <alias name="fldB" propPath="myManyToOneRel.fldB" type="Integer" />
     <alias name="fldC" propPath="myOneToManyRel.myKey.fldC"
           type="Double" />
   </aliases>
</entity>
```

### 3.5 Using a Single File

The NopOrm engine supports specifying a dedicated persistDriver for each entity. Therefore, in principle, as long as the IEntityPersistDriver interface is implemented, data can be stored in a data file. If the IEntityDaoExtension interface is further implemented, composite queries and sorting on records in data files can be supported.

In the Nop platform, composite query conditions for a single table or single entity are abstracted as a QueryBean message object, which can be automatically converted into an executable query filter:

```java
Predicate<Object> filter = QueryBeanHelper.toPredicate(
            queryBean.getFilter(), evalScope);
```

Therefore, implementing a simple single-entity storage model based on JSON or CSV files is not complicated.

With the development of data lake technologies, individual data files have gradually evolved into something like a single-table replacement for databases, with built-in indexes and operator pushdown support. In the near future, integrating feature-rich data file storage such as Iceberg may become very simple.

## To Be Continued

If you’ve made it this far, there probably aren’t many readers left. To avoid the view count falling to zero, I’ll end the first half here. In the second half, I will continue discussing performance-related solutions to the N+1 problem, as well as Dialect customization, GraphQL integration, visualization integration, and related technical approaches.

If you are unfamiliar with Reversible Computation theory, you can refer to my earlier articles:

[Reversible Computation: The Next-Generation Software Construction Theory](https://zhuanlan.zhihu.com/p/64004026)

[Technical Implementation of Reversible Computation](https://zhuanlan.zhihu.com/p/163852896)

[Low-Code Platform Design Through the Lens of Tensor Product](https://zhuanlan.zhihu.com/p/531474176)

The low-code platform NopPlatform, designed based on Reversible Computation theory, is open-source:

- gitee: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- github: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- Development Example: [docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- [Principles of Reversible Computation and Introduction & Q&A on the Nop Platform_bilibili](https://www.bilibili.com/video/BV1u84y1w7kX/)
<!-- SOURCE_MD5:8b2364ce765b64043de9b50c4ffd3937-->
