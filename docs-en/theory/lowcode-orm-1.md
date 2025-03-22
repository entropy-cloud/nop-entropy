  # What ORM is needed for a low-code platform?

Low-code platforms aim to minimize the amount of manually written code. The core tools they rely on must be explicit information models, such as data models, form models, workflow models, report models, etc. Among these, the data model undoubtedly holds a position of prominence. As an ORM (Object-Relational Mapping) engine built on top of the data model, what value can it bring to a low-code platform?

To address this question, we need to revisit the basic concepts of ORM: What is ORM? Why can ORM simplify the code writing for the data access layer? Which common business semantics can be uniformly expressed in the ORM layer? In the context of a low-code platform, the data structure must support user-defined adjustments. The logic path from the front-end UI display to the back-end data storage should be as compact as possible. What support can an ORM engine provide for this? If we are not satisfied with pre-defined scenarios for certain low-code applications but aim to implement a smooth upgrade path from LowCode to ProCode, what requirements will we have for the ORM engine?

This article will be based on reversible computing theory for the initial theoretical analysis of ORM engine design and implementation, and introduce the NopOrm engine used in Nop Platform 2.0. NopOrm contains the main functions of Hibernate + MyBatis + Spring Data JDBC + GraphQL, but due to its use of innovative designs and theoretical analysis-based functionality characteristics selection, the amount of effective hand-written code required is not large (approximately within 20,000 lines). Based on relatively simplified implementation, NopOrm provides more feature support for business development. Additionally, based on reversible computing theory's universal underlying framework, NopOrm offers flexibility and expandability that other ORM engines cannot match, all at no cost.

## What is ORM?

What is ORM? The authoritative explanation on Hibernate's website answers this question: [What is Object/Relational Mapping](https://hibernate.org/orm/what-is-an-orm/). According to Hibernate's explanation, ORM solves the so-called object-relational impedance mismatch (Object-Relational Impedance Mismatch), which arises due to misalignment between the relational schema and the object model. Specifically, there are five aspects of mismatch:

1. **Granularity (粒度):** Relational databases manage data using table and column granularity, while object models can use more sophisticated structures. For example, an address field in a user table might be split into multiple fields and mapped to an Address component class.

2. **Inheritance (继承):** Object-oriented programming languages often heavily rely on class inheritance to reuse concepts and implementation details. However, relational databases lack a similar mechanism for reusing concepts.

3. **Identity (身份):** Relational models use primary keys to distinguish between different objects, while object models use object pointers to represent distinct objects, leading to a conceptual mismatch.

4. **Associations (关联):** Relational models use foreign keys to express relationships between records, while object models use properties to represent these relationships.

5. **Data Navigation (数据导航):** In the object model, you can traverse an entire object graph using attributes like `a.b.c`, whereas in a relational model, you need to explicitly specify associated tables, fields, and their relationships.

Do Hibernate's proposed five aspects truly capture ORM's essence? Here, I analyze ORM from a different angle.

If a technology has inherent superiority, it must **more fully leverage certain information** compared to other available options. It is not merely about solving an impedance mismatch issue in form. The greatest secret of relational database theory is that **relational databases store decomposed, unrelated, and independent atomic data**, not relationships.

Following the third normal form strictly, changing one field's value generally does not affect others!

> Wikipedia: Third Normal Form (Third NF) states that **all data elements must be uniquely identifiable by a primary key and must be independent of each other, with no functional dependencies**. This means that for a 2nd NF-compliant data structure, there may still be dependencies between non-key fields.


In surface, the database defines primary keys and foreign keys as fields, but they serve little practical purpose in actual logical expressions. The complexity of querying associated data using SQL is due to the fact that **we must explicitly specify which tables and their fields need to be joined together each time**, and this join information is not inherent to the system but is instead injected into it through code during access.

In reality, many large software systems have developers whispering an ancient secret: never establish foreign key relationships in large tables because they degrade program performance. **Foreign keys are entirely useless for application development, or if they have any use at all, it's a negative one!**

The relational model employs a symmetric access pattern, meaning all tables and fields are on equal footing without any inherent differences. Using the `JOIN` statement, we can establish associations between any table fields without being constrained by the concepts of primary keys and foreign keys. The reason we tend to use primary keys for reading records is that primary keys usually have primary key indexes, which accelerate access. We can also create unique indexes on other fields; the primary key has no special exclusivity.

As we move from a general storage model, which is unrelated to business, to an application model that facilitates business processing, we inevitably encounter certain fields that hold significant business meaning (symmetry is broken) and whose relationships are stable and frequently used, with no need to repeatedly express these relationships every time.

> The uniformity and universality of the relational model are often considered its theoretical beauties. However, the real world is more complex, and its direction of development is to identify differences and find natural ways to express them.
> 
> The uniform relational model is the most symmetrical and simplified model. When dealing with physical constraints, it assumes that collections interact rarely, and the most common scenarios are single tables (mapping from form tables to data tables) and parent-child tables. Try to imagine the relational model: we can usually only see two data tables in our minds when considering multiple tables because these tables lack distinctiveness, making their image abstract. Only when we explicitly recognize concepts such as primary keys, foreign keys, parent tables, child tables, dictionary tables, fact tables, and dimension tables does our mental model become more refined, breaking symmetry.
> 
> The star schema and snowflake schema in data warehouse theory emphasize relationship decomposition within a domain while allowing some redundancy. This actually highlights the non-equivalence of tables, no longer treating all tables as equal. The distinction between Fact Tables and Dimension Tables is identified and explicitly handled. From complete decomposition to partial decomposition forms a model hierarchy, allowing us to choose specific implementation models based on theoretical guidance at different levels of complexity.

**The unique value of an ORM lies in its recognition of the special nature of primary keys and foreign keys and its implementation of inherent associations between them.**

First, in an ORM, primary keys have special significance as they become object cache keys. Through object caching, the ORM ensures that objects pointing to the same primary key correspond to the same object reference, thus automatically maintaining a.b.c.a == a relationships, allowing traversal through different attribute paths on the object graph to ensure reaching the same node.

Second, foreign key associations are solidified and repeatedly utilized in an ORM. Consider the following SQL statement:

```sql
select * from a, b
where a.fldA = b.fldB
and a.fldC = 1 and b.fldD = 2
```

The condition `a.fldA = b.fldB` can be called an association condition, while `a.fldC = 1` and `b.fldD = 2` are coordinate conditions. The complexity of SQL is largely due to the frequent need to specify identical association conditions in various places without being able to abstract them into reusable components.

In the ORM's object space, associations between objects only need to be specified once. They then take effect during operations like insert, update, delete, and especially query statements, where associations can automatically infer multi-entity relationships, such as `a.b.c.d = 3` being automatically converted into:

```sql
select ... 
from A a join B b on a.xx = b.id join C c on b.yy = c.id
where c.d = 3
```

With the help of automatic attribute associations, the applicability of single-table models has been greatly expanded: any single table automatically becomes a subject table, and any field from associated tables automatically becomes a directly accessible field on the subject table. For example, placing a query field `a.b.c` in the frontend is functionally equivalent to placing a query field `d` in the backend processing pipeline. Using domain-driven design (DDD), this is also easily implemented as an aggregate root pattern (Aggregate Root).

Based on these theoretical analyses, we can see that among Hibernate's five aspects, granularity and inheritance are relatively secondary concepts, and we do not need to invest significant effort in them within the core of the engine! On the other hand, data access engines like MyBatis lack a query language that can leverage object relationships, making them far from a complete ORM.

In the actual development process, we can completely replace inheritance with composition (the development of object-oriented technologies over the years has always been advocating the superiority of composition over inheritance). In fact, in Java language, when inheritance is combined with lazy loading, it inherently leads to a concept contradiction. Before loading the entity, it's possible that a proxy object needs to be created, but at that time its type is undefined. After the proxy completes lazy loading, the ORM engine cannot, under the condition of ensuring object pointer uniqueness, convert it into a specific object type.


## 2. EQL = SQL + AutoJoin

The long-standing criticism of ORM engines has been that object query language (OQL) has significant limitations, particularly in support for multi-table joins, especially for non-primary foreign keys. It does not support arbitrary table associations or select * from (select xxx) type of subquery-based queries. However, is this a fundamental issue inherent to ORM engines themselves, or is it a limitation specific to the implementation of Hibernate?

Based on the theoretical analysis in the previous section, leveraging object association relationships is one of the core values of ORM engines. Therefore, what is the minimalistic object query language that can fully utilize object associations? The EQL (Entity Query Language) defined in NopOrm is considered a **superset of SQL**, built upon SQL query syntax (theoretically supporting all SQL syntax) with an additional minimalistic extension for object association attributes. Unlike Hibernate, which introduces unique querying syntaxes for objects, EQL discards all such object-specific query syntax and only adds support for attribute association syntax like a.b.c. This makes it very similar to traditional SQL in usage and naturally supports the following query statements:

```sql
with a as (
  select o.u ...
)
select a.*, b.d
from a, (select c.xx, c.d from C c where c.d.e > 3) b 
where a.u = b.xx
limit 3 offset 2
```

In NopOrm, both SQL and EQL execution are abstracted into a unified interface called ISqlExecutor. The results returned by both SQL and EQL are encapsulated into the IDataSet interface (a replacement for JDBC's ResultSet). The main difference in usage is that EQL may return fields as objects or object collections, rather than just atomic data types. The interface definition can be found at:

[ISqlExecutor](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-dao/src/main/java/io/nop/dao/api/ISqlExecutor.java)

EQL extends SQL language in only two aspects:
1. Similar to `from MyEntity o left join o.relField`, actively specifying the association method in the FROM clause.
2. Similar to `o.a.b.c`, accessing object association attributes via qualified names.

> There's a special processing rule: generally, `o.a.b` translates to an INNER JOIN between tables, but if `order by o.a.b` is used without other references to `o.a.b`, it prioritizes using LEFT JOIN to avoid issues when `o.a` might be null, thus affecting the result set count.

Since the translation of object attribute association syntax is largely orthogonal to other SQL syntaxes, it can be encapsulated in an independent AST transformer. This means adding new SQL syntax support does not interfere with EQL query conversion. Using the AST auto-parsing technology discussed in the referenced article, we can achieve seamless conversion from EQL to SQL by simply modifying the ANTLR4 grammar definition. Thus, making EQL compatible with all SQL syntax becomes a relatively straightforward task.

[How to Automatically Parse AST Instead of ParseTree using Antlr4](https://zhuanlan.zhihu.com/p/534178264)

Many lower-level frameworks require parsing SQL statements to obtain data structure information, such as:
1. Alibaba's [Druid database connection pool](https://github.com/alibaba/druid/wiki/%E5%B8%B8%E8%A7%81%E9%97%AE%E9%A2%98) for preventing SQL injection and implementing SQL auditing
2. [Apache ShardingSphere](https://shardingsphere.apache.org/index_zh.html) for parsing SQL statements to enable sharding, table splitting, and data encryption
3. Alibaba's [Seata](https://seata.io/zh-cn/) for parsing SQL statements to implement distributed transactions under the AT mode

Since ORM engines have already implemented EQL parsing (a superset of SQL), adding similar functionality requires minimal effort. Even in frameworks with well-separated layers and good isolation, they can likely be directly integrated into EQL's execution engine.

  
Another long-standing saying is that the translation process of object query language is not transparent, and the SQL statements generated look quite "ugly." For example:

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

If we abandon all the object-specific query syntax introduced by HQL and only keep the object attribute association, then EQL syntax is actually not any more complex or confusing than standard SQL syntax. In fact, its correspondence with SQL syntax is simple and intuitive, to the point that it can even be used entirely with SQL syntax (SQL is a valid EQL). For example:

```sql
select s.statusName
from MyUser o, MyStatus s
where o.statusId = s.id

or

select o.status.statusName
from MyUser o
```

As for why HQL-generated SQL statements introduced so many automatically generated aliases, making the SQL statements less friendly, the Hibernate team provided an explanation in April 2022 when they released [Hibernate 6.0](https://www.infoq.com/news/2022/04/red-hat-releases-hibernate-6/): The reason was that prior to version 6.0, Hibernate always read data from the ResultSet using column names, so each column needed a unique alias. In Hibernate 6.0, they switched to reading data by column index, so aliases are no longer generated! This change also improved the performance of Hibernate 6.0.

Sincerely, this explanation feels a bit awkward. Why did this happen? Probably because Gavin King, who implemented the first version of Hibernate in 2003, wasn't familiar with JDBC APIs at the time and left this as a legacy issue.
  

## Three. Dynamic ORM Mapping

In low-code platforms or general SaaS applications, there is a need for users to customize data storage. Since different users have different requirements for data storage structures, we must provide a dynamic ORM mapping mechanism that can be customized at runtime.

Wu Manyi discussed several common user-defined storage schemes in his article [From the Implementation Perspective: Low-Code](https://zhuanlan.zhihu.com/p/451340998), including:

1. Using dynamic entities to directly map to relational databases
2. Using document databases
3. Using rows instead of columns (transposing tables)
4. Using metadata plus wide tables, reserving numerous fields
5. Using single files

In the Nop platform, all five approaches can be implemented directly using the NopOrm engine, and they can coexist within the same OrmSession. This means we can store some entity data in regular database tables, use vertical tables for other data, or store it in Redis cache or Elasticsearch document databases, while still others are stored in data files. From the application layer, all these are treated as ordinary Java objects and form a unified object graph. It is even possible to switch storage methods at appropriate times, such as using vertical tables initially to avoid modifying the database, then switching to regular or wide tables when data volume grows and business logic stabilizes. The application layer can maintain the original object structure without any changes.


### 3.1 Direct Use of Relational Databases

NopORM supports dynamic property configuration. If an entity model defines a property that isn't defined in the Java entity class, it will be stored as a dynamic property and converted to the specified type during data storage. From the application layer, it is indistinguishable from a regular Java attribute field.

```xml
(entity name="io.nop.app.SimsExam" className="实现类，一般与entityName相同">
    <columns>
        ...
        <column name="examScoreScale" propId="20" code="EXAM_SCORE_SCALE" stdSqlType="TINYINT"/>
        <!-- 不生成java实体代码 -->
        <column name="extField" propId="21" code="EXT_FIELD" 
            stdSqlType="INTEGER" notGenCode="true"/>
    </columns>
</entity> 

In this configuration, if the SimsExam entity class contains the examScoreScale and extField properties, it will use the corresponding get/set methods to access the properties. If they do not exist, their values will be stored in the dynamicValues property collection of the base class DynamicOrmEntity.

The model definition for extField specifies notGenCode=true, indicating that when generating Java entity code based on the orm.xml model, no get/set methods will be generated for this field. As a result, it will always be accessed as a dynamic property.

If we do not need to generate code, we can set the className attribute of the entity to specify the implementation class as io.nop.orm.support.DynamicOrmEntity, which will make all fields dynamically accessible.

The Nop platform natively supports a mechanism similar to Ruby's MethodMissing, allowing dynamic addition of properties to objects. In Java code, we can retrieve the value of a dynamic property using BeanTool.getProperty(entity, "extField") or entity.prop_get("extField").

The built-in scripting language XScript in the Nop platform recognizes IPropGetMissingHook and IPropSetMissingHook extension interfaces. Therefore, when accessing dynamic entity properties from script code or expression language, it follows the same pattern as accessing regular properties.

```java
entity.extField = 3;
let x = entity.examScoreScale;
```

### 3.2 Using Document-Type Databases

In the entity model definition of NopOrm, you can specify a different persistDriver for each type of entity. For example, setting persistDriver="elasticSearch" means using the ElasticSearchEntityPersistDriver to store entities. It corresponds to the IEntityPersistDriver interface in the ORM engine and supports batch and asynchronous data storage for entities.

[IEntityPersistDriver](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-orm/src/main/java/io/nop/orm/driver/IEntityPersistDriver.java)

Additionally, for single-entity queries, NopOrm encapsulates them in the IEntityDao.findPage(QueryBean) function. If the PersistDriver implements the IEntityDaoExtension interface, then the application layer can use the IEntityDao interface to access the complex query capabilities provided by the underlying driver.

[IEntityDao](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-dao/src/main/java/io/nop/dao/api/IEntityDao.java)

Another extension method is to store JSON strings in text fields of relational databases and parse them into Maps when needed. For example,
```xml
(entity name="io.nop.app.SimsClass">
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

In the above example, we use the Component mechanism in NopOrm engine to parse the `jsonText` field into a Map object. In the program, you can access the corresponding property as follows:

```java
BeanTool.getProperty(entity,"jsonExtComponent.fld1")
```

If the Component configuration seems cumbersome, you can leverage Nop's built-in meta-programming capabilities for simplification. For instance, you can replace it with the following configuration:

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

The `<orm-gen:JsonComponentSupport>` tag will recognize the `tagSet="json"` attribute on fields and automatically generate corresponding JsonComponent configurations for them. Additionally, we can utilize alias configuration to simplify property names used in application layers. With this configuration, you will get the following equivalent relationships:

``` 
entity.extFld1 == entity.jsonExtComponent.fld1
```

The alias provides a concise property name for complex attribute paths, thus hiding the underlying storage structure.

Hibernate's design philosophy is rooted in object-oriented modeling and reverse-engineers database storage structures based on objects. In contrast, NopOrm's design approach is database-centric, following the relational system paradigm from simple to complex. It maps database columns to atomic fields first and then constructs more complex ComponentProperty, ComputedProperty, EntityReferenceProperty, and EntitySetProperty relationships step by step.

Hibernate faces inherent difficulties in managing complex data relationships due to its object-oriented foundation. For example, when multiple components and associations map to the same database field, it leads to data conflicts. The secret to resolving such conflicts lies in breaking down all data structures into atomic data types beforehand. This ensures that conflicts automatically disappear. A significant portion of Hibernate's complexity stems from its need to maintain a lengthy mapping between mutually intertwined object structures and clean, independent database fields.

  
  Adopting the JsonComponent approach to implement extended storage has a disadvantage in that it does not provide good support for querying and sorting. If the underlying database supports JSON data types, we can perform a local transformation within the EQL AST Transformer by translating the entity attribute access syntax in EQL, such as `entity.jsonExtComponent.fld1`, into JSON property accesses supported by the database, e.g., `json_extract(entity, "$.fld1")`.

### 3.3 Using Rows Instead of Columns

In relational databases, rows and columns are asymmetric. While rows can be added easily and in unlimited quantity, the number of columns is generally limited and increasing or decreasing the number of columns involves costly operations (this may change with the popularity of columnar databases). To achieve a model where rows and columns are symmetric, we can use a tabular approach:

```
rowId colId value
```

We can create an extended table with just three fields. Here, `rowId` and `colId` function as a coordinate system, representing row coordinates and column coordinates respectively, while `value` represents the value at a specific coordinate within the system.

The actual implementation may involve more complex data structures, such as adding a `fieldType` column to indicate the data type of `value`, multiple value fields for proper sorting, and built-in database date functions for operations like date handling.

```
class OrmKeyValueTable {
    String entityId;
    String fieldName;
    byte fieldType;
    String stringValue;
    Integer intValue;
    BigDecimal decimalValue;
    DateTime dateTimeValue;
}
```

How to convert rows into columns? At the object level, this is equivalent to how a list record is converted into an object's extended property. In reversible computing theory, this is a standard structural transformation: **for any collection structure, we can define a `keyProp` attribute to transform it into an object property structure**.

For example, if `keyProp` is set to `name`, then `entity.extFields.myKey` can be translated into `entity.extFields[row => row.name == 'myKey']`.

> The existence of `keyProp` is crucial for defining a stable domain coordinate system. For instance, in the virtual DOM Diff algorithm used in the frontend, we need a `v-key` attribute to quickly and reliably identify changed components.

For specific configuration examples, refer to:

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
### 3.4 Meta Information + Wide Tables

Given that most ORM engines inherently contain a significant amount of metadata, the "meta information + wide table" pattern is essentially something that general-purpose ORM engines can natively support. For example:

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

In the above example, all entity data is stored in a single unified `GLOBAL_STORE_TABLE` table. The `entityName` column is set to a fixed string `"MyEntity"` to store MyEntity entity data, while `VALUE1` and `VALUE2` are renamed to `name` and `amount`. The `VALUE2` attribute has a VARCHAR type in the database but an Integer type in Java. By specifying the `stdDataType` property, we can clearly differentiate between these two levels of data types and implement automatic conversion between them. Based on this configuration, we can use EQL syntax to query the table just like a regular database table:

```sql
select * from MyEntity o where o.name = 'a' and o.amount > 3
-- Which will be translated into
select * from GLOBAL_STORE_TABLE o
where o.ENTITY_NAME = 'MyEntity'
   and o.VALUE1 =  'a' and o.VALUE2 > '3'
```

Using the alias mechanism mentioned in the previous section, we can concatenate multiple one-to-one or one-to-many tables into a logical wide table. For example:

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
### 3.5 Single File Usage

The NopOrm engine supports specifying a dedicated `persistDriver` for each entity. In principle, any implementation of the `IEntityPersistDriver` interface can be used to save data into data files. If further implemented with the `IEntityDaoExtension` interface, it can support composite queries and sorting on records within data files.

In the Nop platform, composite query conditions for a single table or entity are abstracted as `QueryBean` message objects, which can be automatically converted into an executable query filter:

```java
Predicate<Object> filter = QueryBeanHelper.toPredicate(
        queryBean.getFilter(), evalScope);
```

Implementing a simple single-entity storage model based on JSON or CSV files is not overly complex.

With the advancement of data lake technology, individual data files have gradually evolved into embedded-index-enabled databases that support operator down推 (push). In the near future, integrating such as Iceberg, which offers rich features, may become a relatively straightforward task.

## Incomplete

By now, perhaps only a few classmates can persist to read this far. To prevent the reading length from dropping to zero, I decide to halt the first half of this article here. In the latter half of this article, I will continue discussing solutions for the N+1 problem related to performance and customizing dialects, GraphQL integration, visualization integration, etc.

If unfamiliar with reversible computation theory, you can refer to my previous articles:

[Reversible Computing: The Next-Generation Software Construction Theory](https://zhuanlan.zhihu.com/p/64004026)

[Reversible Computation Technology Implementation](https://zhuanlan.zhihu.com/p/163852896)

[Tensor Multiplication from the Perspective of Low-Code Platform Design](https://zhuanlan.zhihu.com/p/531474176)

Based on reversible computation theory, the low-code platform NopPlatform has been open-sourced:

- Gitee: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- GitHub: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- Development Example: [docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- [Reversible Computing Principles, Nop Platform Introduction and Q&A\_Bilibili](https://www.bilibili.com/video/BV1u84y1w7kX/)

