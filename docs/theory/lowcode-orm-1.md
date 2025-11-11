# 低代码平台需要什么样的ORM引擎？(1)

低代码平台试图最小化手工编写的代码量，它所能够依赖的核心工具必然是各类显式建立的信息模型，例如数据模型、表单模型、流程模型、报表模型等。其中，数据模型无疑是其中的重中之重。作为建立在数据模型基础之上的ORM（Object Relational Mapping）引擎，它可以为低代码平台带来哪些价值？

为了回答这一问题，我们有必要回到ORM的基本概念：什么是ORM？ORM为什么可以简化数据访问层的代码编写？哪些常见的业务语义可以统一下放到ORM层来表达？在低代码平台的语境下，数据结构需要支持用户自定义调整，从前端展现界面到后台数据存储的逻辑路径需要被尽量压缩，ORM引擎可以为此提供哪些支持？如果我们不满足于事先限定的某些低代码应用场景，而是希望实现一条从LowCode到ProCode的平缓的升级路径，我们对ORM引擎会提出什么样的要求？

本文将基于可逆计算理论，对ORM引擎的设计和实现进行初步的理论分析，并介绍Nop Platform2.0中所使用的NopOrm引擎的实现方案。NopOrm大致包含了Hibernate+ MyBatis + Spring Data JDBC + GraphQL的主要功能，但是因为它使用了大量创新性的设计并依据理论分析对功能特性进行了一定的取舍，所以实际需要手工编写的有效代码量并不大（大概在2万行以内）。在相对精简的代码实现基础上，对于业务开发而言，NopOrm实际上提供了更多的特性支持，同时基于可逆计算理论的通用底层方案，NopOrm免费提供了其他ORM引擎所无法达到的灵活性和可扩展性。

## 一. 什么是ORM?

什么是ORM？在hibernate的网站上，常年挂着对这个问题的权威解释：[What is Object/Relational Mapping](https://hibernate.org/orm/what-is-an-orm/)。Hibernate的解释是ORM解决了所谓对象-关系阻抗（Object-Relational Impedance Mismatch）的问题，即关系范式与对象范式之间存在失配，需要一个框架去解决适配的问题。具体来说有5个方面的失配：

1. 粒度(Granularity): 关系数据库通过表和字段的粒度来管理数据，而对象模型可以采用更丰富的管理结构。比如说用户表中地址信息可能拆分成多个字段，可以被映射为Address组件类。

2. 继承(Inheritance)：面向对象程序语言中一般会大量使用类继承来复用已有概念和具体功能实现，而在关系数据库中缺少类似的手段来实现复用。

3. 唯一性(Identity)：关系模型通过主键来区分不同的对象，而对象模型中不同的对象对应不同的对象指针，两者之间存在概念的不一致性。

4. 关联(Associations)：关系模型通过外键来表达记录之间的关联关系，而对象模型则使用对象属性来表达关联关系。

5. 数据导航(Data Navigation)：在对象模型中可以通过a.b.c这种属性访问的形式遍历整个对象图，而在关系模型中，我们需要明确指定关联表、关联字段和它们之间的关联方式。

Hibernate所提出的这5个方面到底是不是体现了ORM的本质？这里我想从一个不同的角度去分析一下。

首先，一个技术如果存在本质上的优越性，那么它一定是**相比于其他可选方案更充分的利用了某些信息**，而绝不仅仅是为了解决某种形式上的适配问题。一个更好的ORM相比于一个平庸的ORM，它更充分的利用了哪些信息？这涉及到了关系数据库理论中最大的秘密： **关系数据库中无关系**！关系数据库虽然张口闭口不离关系两字，但**真相是关系数据库中存储的是关系被分解之后得到的、不相关的、相互独立的原子数据！**

如果我们严格的按照关系数据库理论中的第三范式来建立表模型，则修改任意一个字段的值，原则上并不会影响其他字段的值！

> 百度百科：第三范式 (Third Normal Form,3rd NF)是指**表中的所有数据元素不但要能唯一地被主关键字所标识,而且它们之间还必须相互独立,不存在其他的函数关系**。 也就是说，对于一个满足2nd NF 的数据结构来说，表中有可能存在某些数据元素依赖于其他非关键字数据元素的现象,必须消除

表面上看起来数据库中定义了主键和外键字段，但是它们除了起一些完整性校验作用之外，在真正的逻辑表达中并不起作用。使用SQL语言访问复杂关联数据之所以比较啰嗦，是因为**每次访问数据的时候我们都需要明确指定哪些表的哪些字段需要按照什么条件关联在一起**，即关联信息是当我们访问数据的时候，明确通过代码注入到系统中的，并不属于系统内置的知识。实际上，很多大型软件系统的开发人员甚至在口口相传一个古老的秘诀：不要在大表上建立外键关联，它们会影响程序性能，**外键关联对于应用开发而言压根就没有用，或者说如果有作用，那也是负作用！**

**关系模型采用的是一种对称的访问模式**，即所有的表、所有的字段都是平权的，它们之间本质上没有任何的差异。通过join语句，我们可以在任何表的任何字段之间建立关联，并不受主键、外键概念的限制。之所以我们倾向于使用主键去读取记录，本质上是因为主键上一般存在着主键索引，可以加速访问。我们同样可以在其他字段上建立唯一索引，主键没有任何排他的特殊性。

当我们从一个与业务无关的、通用的存储模型向上逐步走到便于业务处理的应用模型的时候，必然会发现一些字段在业务上存在着特殊重要的意义（对称性发生破缺），而且它们之间的关联关系是相对稳定、并被频繁使用的，没有必要每次都重复表达。

> 关系模型概念上的均一性和普遍性往往被认为是理论的优美之处。但是现实世界是复杂的，发展的方向就是逐步识别出不同之处，并找到自然的表达形式将这些不同表达出来。
> 
> 均匀的关系模型是对称性最高的、最简化的模型。在面对物理约束时，它隐含的假设是集合之间很少发生相互作用，单表（表单到数据表之间的映射）和主从表是最广泛的情况。试着想象一下关系模型，在思维中一般我们只能看到两个数据表，当考虑到多个表的时候，因为这些表之间没有明确的可区分性，因此它们的意象是模
> 糊的。只有明确意识到主键，外键，主表，从表，字典表，事实表，纬度表这些不同的概念的时候，当对称性发生破缺的时候，我们思维中的模型才能够丰富化起来。
> 
> 数据仓库理论中建立的星型模式和雪花模式，强调了针对主题域的、允许部分冗余的关系分解。它实际上是强调了表之间的不等价性，不再是所有的
> 表都处于同一地位。Fact Table和Dimension Table之间的区别被识别出来，并被明确处理。从关系完全分解到关系部分分解，可以构成一个模型级列，在不同的复杂性层次上，我们可以根据理论的指导选择具体的实现模型。

**ORM的特殊价值在于它识别出了主键和外键的特殊性，实现了对两两关系的内蕴表达及充分利用**。

首先，在ORM中主键具有特殊的意义，它成为了对象缓存的key。通过对象缓存，ORM可以确保主键一致的对象实际对应于同一个对象指针，从而自动维护了a.b.c.a == a这样的恒等关系，即通过不同的属性路径在对象图上遍历，可以确保到达同一个对象节点。

第二，在ORM中外键关联信息得到固化并被反复利用。考察如下SQL语句

```sql
select * from a, b
where a.fldA = b.fldB
and a.fldC = 1 and b.fldD = 2
```

a.fldA = b.fldB 可以称为关联条件，而a.fldC=1和b.fldD=2可以称作是坐标条件。SQL的复杂性很大程度上来源于我们频繁的需要在各处指定完全一样的关联条件而无法把它们抽象成可复用的组分。在ORM所提供的对象空间中，对象之间的两两关联只要指定一次，就可以在增删改查等各种操作过程中起到作用，特别是在对象查询语句中，通过两两关联可以自动推导出多实体之间的关联关系，即a.b.c.d=3可以自动被推导为

```sql
select ... 
from A a join B b on a.xx = b.id join C c on b.yy = c.id
where c.d = 3
```

借助于自动属性关联，单表模型的适用范围得到了极大的扩展：任何一个单表都自动成为了主题表，任何关联表上的字段都自动成为主题表上可以直接访问的字段。比如说我们在前台放置一个查询字段a.b.c，与放置一个查询字段d，对后台的查询处理管道而言可以是完全一致的。如果采用领域驱动设计(DDD)，则基于主题表实体对象，也很容易实现所谓的聚合根模式（Aggregate Root）。

基于以上的理论分析，我们可以发现Hibernate所提出的5个方面中，粒度和继承的问题是相对次要的概念，我们并不一定需要在引擎的核心中为此投入很大的精力！另一方面，类似MyBatis的数据访问引擎缺乏可以利用对象关联关系的查询语言，它必然算不上是一个完善的ORM。

> 在实际开发过程中，我们完全可以采用组合来代替继承（面向对象技术这些年的发展也一直在宣扬组合优于继承）。其实在Java语言中，继承与延迟加载相结合的情况下本身就会产生概念矛盾。在实际加载实体之前有可能就需要创建一个proxy对象，但是此时它的类型是未定的，而当proxy延迟加载完毕之后，ORM引擎无法在保证对象指针唯一性的情况下将它转换为具体的对象类型。

## 二. EQL = SQL + AutoJoin

关于ORM引擎，长期以来一直存在的一种批评的声音是：对象查询语法限制很大，特别是对非主外键关联的多表联合查询支持很差，不支持任意表之间的任意关联，也不支持select \* from (select xxx)这样的以子查询为数据源的查询语句等。但是，这个问题到底是ORM引擎本质上存在的问题，还是Hibernate这种具体实现所存在的问题？

根据上一节的理论分析，能够充分利用对象关联关系的对象查询语言是ORM引擎的本质性价值之一，那么实现这一本质性价值的**最小化的对象查询语言**应该是什么？NopOrm引擎中的对象查询语言EQL(Entity Query Language)被定义为**SQL语言的超集**，它是在SQL查询语法的基础上（理论上可以支持所有SQL语法）增加一个最小化的对象关联属性扩展。EQL放弃了所有Hibernate所引入的对象特有的查询语法，仅仅是增加了对a.b.c这样的属性关联语法的处理，因此它在使用上非常类似传统的SQL语言，可以很自然的支持如下查询语句:

```sql
with a as (
  select o.u ...
)
select a.*, b.d
from a, (select c.xx, c.d from C c where c.d.e > 3) b 
where a.u = b.xx
limit 3 offset 2
```

在NopOrm中，SQL和EQL的执行被抽象成了统一的接口ISqlExecutor，它们返回的结果都被封装成了IDataSet接口（JDBC的ResultSet的替代品），在使用层面唯一的区别就是EQL返回的结果字段有可能是对象或者对象集合，而不仅仅是原子数据类型。接口具体定义参见

[ISqlExecutor](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-persistence/nop-dao/src/main/java/io/nop/dao/api/ISqlExecutor.java)

EQL对SQL语言的扩展只涉及到两个地方：

1. 类似from MyEntity o left join o.relField，在from语句中主动指定关联方式

2. 类似o.a.b.c，通过qulified name的形式来访问对象关联属性

> 这里还有一个特殊处理规则：一般情况下o.a.b会翻译为表之间的inner join关联，但是如果是order by o.a.b且在其他地方没有使用过o.a.b，则优先使用left join关联，避免o.a为null时使用inner join影响结果集的条目数。

因为对象属性关联这一语法的翻译过程基本上与SQL语言的其他语法是正交的，它可以被封装到一个独立的AST Transformer中去实现，因此我们为SQL语言增加新的语法支持时并不会影响到EQL语法的转换。如果采用以下文章中介绍的AST自动解析技术，我们甚至可以做到只要修改antlr的g4语法定义，就可以自动实现EQL到SQL的转换，EQL兼容所有SQL语法成为一件相对简单的工作。

[Antlr4如何自动解析得到AST而不是ParseTree](https://zhuanlan.zhihu.com/p/534178264)

目前很多底层框架都需要解析SQL语句来获得数据结构信息，例如

1. 阿里的[Druid数据库连接池](https://github.com/alibaba/druid/wiki/%E5%B8%B8%E8%A7%81%E9%97%AE%E9%A2%98)需要解析SQL语句来防止SQL注入攻击和实现SQL审计

2. [Apache ShardingSphere](https://shardingsphere.apache.org/index_zh.html)需要解析SQL语句来实现分库分表和数据加密等功能

3. 阿里的[Seata](https://seata.io/zh-cn/)需要解析SQL语句来实现AT模式下的分布式事务

既然ORM引擎已经实现了EQL解析(SQL语言的超集)，那么只需要付出很少的成本就可以实现类似的功能。甚至在这些框架自身的分层隔离性做得比较好的情况下，应该可以直接把它们集成到EQL的执行引擎中。

关于ORM对象查询语言，另一个长久以来流传的说法是：对象查询语言翻译过程不透明，翻译得到的SQL语句看起来也很"丑陋"。例如：

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

如果放弃HQL所引入的所有对象特有的查询语法，仅仅保留对象属性关联，则EQL语法其实并不比普通的SQL语法更复杂晦涩。实际上它与SQL语法的对应关系非常简单直观，甚至可以只使用SQL语法来访问所有实体数据（SQL语法是合法的EQL语法）。比如我们可以使用

```sql
select s.statusName
from MyUser o, MyStatus s
where o.statusId = s.id

或者
select o.status.statusName
from MyUser o
```

至于HQL翻译得到的SQL语句中为什么引入了大量自动生成的别名，使得SQL语句显得不那么友好，在2022年4月发布的[Hibernate 6.0](https://www.infoq.com/news/2022/04/red-hat-releases-hibernate-6/)中，Hibernate团队给出了一个解释：原因是在6.0之前，Hibernate总是通过列名来从ResultSet中读取数据，所以需要每一列都有一个唯一的别名。而在Hibernate6.0中，已经改成了通过列的下标来读取数据，因此不再需要生成别名了！通过此次修改，Hibernate6.0的性能也进一步得到了提升。

说实话，这个解释听起来让人有点尴尬。为什么会出现这种事情？大概这是Gavin King在2003年实现Hibernate第一版的时候，对JDBC的API还不熟悉所留下的后遗症吧。

## 三. 动态ORM映射

在低代码平台或者一般的SAAS应用中，都存在用户自定义数据存储的需求。因为不同的用户需要根据自己的需求来设计不同的存储结构，所以我们必须提供一套在运行时可以定制的动态ORM映射机制。

吴多益在[从实现原理看低代码](https://zhuanlan.zhihu.com/p/451340998)一文中介绍了后端低代码常见的几种用户自定义存储方案。

1. 通过动态实体直接映射关系型数据库

2. 使用文档数据库

3. 使用行来代替列，即横表转纵表

4. 使用元信息+宽表，预留大量字段

5. 使用单文件

在Nop平台中，以上5种方案都可以借助NopOrm引擎来直接实现，并且这5种方式可以共存于同一个OrmSession中，也就是说我们可以将部分实体数据保存到普通的数据库表中，部分数据采用纵表保存，部分数据保存到Redis缓存或者ElasticSearch文档数据库中，而另外一些数据保存到数据文件中，在使用层面上它们都是普通的Java对象，并组成一个统一的对象图，在应用层面无法识别出底层具体是采用了哪种存储机制。在合适的时候，我们甚至可以切换数据存储方式，比如一开始为了避免修改数据库，我们使用纵表来保存扩展数据，随着数据量的增长和业务逻辑的逐步稳定，我们可以切换到普通数据表或者宽表形式的存储，在应用层可以保持原有对象结构不变，并不需要做出任何改变。

### 3.1 直接使用关系型数据库

NopORM支持动态属性配置，当实体模型中定义的属性没有在Java实体类中定义时，它会作为动态属性来存储，并按照定义时指定的类型进行数据类型转换，在所有应用使用层面与普通Java属性字段没有区别。

```xml
<entity name="io.nop.app.SimsExam" className="实现类，一般与entityName相同">
    <columns>
        ...
        <column name="examScoreScale" propId="20" code="EXAM_SCORE_SCALE" stdSqlType="TINYINT"/>
        <!-- 不生成java实体代码 -->
        <column name="extField" propId="21" code="EXT_FIELD" 
            stdSqlType="INTEGER" notGenCode="true"/>
    </columns>
</entity> 
```

 在上面的配置中，如果SimsExam实体类上存在examScoreScale和extField属性，则会使用该属性对应的get/set方法来存取属性，如果不存在，则会在基类DynamicOrmEntity的属性集合dynamicValues中存放属性值。

extField的模型定义中指定了notGenCode=true，它表示根据orm.xml模型定义来生成Java实体代码的时候，不会为该字段生成get/set方法，从而总是作为动态属性来存取。

如果我们不需要生成代码，则可以通过className属性为实体指定实现类为io.nop.orm.support.DynamicOrmEntity，从而将所有字段都作为动态属性来存取。

在Nop平台中内置支持了类似于Ruby语言的MethodMissing机制，允许为对象动态增加属性。在Java代码中，我们可以通过BeanTool.getProperty(entity,"extField")或者entity.prop\_get("extField")来获得动态属性的值。

Nop平台内置的脚本语言XScript识别IPropGetMissingHook和IPropSetMissingHook扩展接口，因此在脚本代码或者表达式语言中访问动态实体属性时，形式与访问普通属性相同。

```java
entity.extField = 3;
let x = entity.examScoreScale;
```

### 3.2 使用文档型数据库

在NopOrm的实体模型定义中，可以为每一种实体类型指定不同的persistDriver，例如persistDriver="elasticSearch"表示将使用ElasticSearchEntityPersistDriver来存取实体。它对应于ORM引擎中的IEntityPersistDriver接口，支持批量和异步的实体数据存取。

[IEntityPersistDriver](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-persistence/nop-orm/src/main/java/io/nop/orm/driver/IEntityPersistDriver.java)

同时对于针对单实体的数据查询，NopOrm通过IEntityDao.findPage(QueryBean)函数进行了统一的封装。如果PersistDriver实现了IEntityDaoExtension接口，应用层就可以通过IEntityDao接口使用到底层Driver所提供的复杂查询能力。

[IEntityDao](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-persistence/nop-dao/src/main/java/io/nop/dao/api/IEntityDao.java)

另一种扩展方式是使用关系数据库中的文本字段来保存JSON字符串，然后在使用的时候将JSON字符串解析为Map使用。例如

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

在上面的示例中，我们使用了NopOrm引擎中的Component机制来将jsonText字段解析为Map对象，在程序中我们可以通过如下方式访问对应属性

```java
BeanTool.getProperty(entity,"jsonExtComponent.fld1")
```

如果觉得Component的配置有些冗长，可以利用Nop平台内置的元编程能力来进行简化。例如，可以替换为如下配置

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

`<orm-gen:JsonComponentSupport>`标签将识别字段上的`tagSet="json"`标记，自动为该字段生成对应JsonComponent配置。同时，我们可以利用alias配置来简化应用层使用的属性名。通过上面的配置，在XScript脚本和EQL查询语言中，我们得到如下等价关系

```
entity.extFld1 == entity.jsonExtComponent.fld1
```

alias可以为一个复杂的属性路径提供一个简短的属性名，从而屏蔽底层具体的存储结构。

Hibernate的设计思想是以对象范式为基础，反向推导出关系数据库存储结构。NopOrm的的设计思想与此相反，它的处理策略是以数据库设计为基础的正向设计，遵循关系系统范式，从简单到复杂，先通过column映射数据库表的所有原子字段，然后再逐步构造更复杂的ComponentProperty，ComputedProperty，EntityReferenceProperty，EntitySetProperty等相互纠缠的对象结构。

Hibernate以对象范式为基础在处理复杂数据关系时存在着本质性的困难。比如说，如果有多个组件和关联属性都映射到同一个数据库字段，它们会出现数据冲突。在这种情况下，我们到底以哪个组件设置的属性值为准呢？关系数据库解决数据冲突问题的秘诀在于，当把所有数据结构都分解到原子的数据类型上之后，所有的冲突就自动消失了。Hibernate实现代码中很大一部分复杂性就在于它需要维护一个非常冗长的，从相互纠缠的对象结构到干净、独立的数据库字段之间的双向映射。

采用JsonComponet方式来实现扩展存储的缺点是不太好支持查询和排序。如果底层的数据库支持JSON数据类型，则可以在EQL AST Transformer中做一个局部变换，将EQL语法中的实体属性访问，例如entity.jsonExtComponent.fld1翻译为数据库所支持的json属性访问，例如 `json_extract(entity, "$.fld1")`

### 3.3  使用行代替列

关系数据库中行和列是不对称的，增加行很容易，数量也不受限制，但是列的个数一般非常有限，而且增加/删除列的操作是一个成本很高的操作（随着列式数据库的流行，这一点可能会发生变化）。如果我们要得到一个行与列对称的模型，则可以采用所谓纵表的方案

```
rowId colId value
```

我们可以建立一个只有三个字段的扩展表，rowId和colId可以看作是对称的坐标系统，分别对应于行坐标和列坐标，而value是坐标系中某一个给定位置处的值。

具体实现的时候数据结构可能会更复杂一些，例如增加fieldType列来标记value实际对应的数据类型，增加多个值字段，便于实现正确的排序，便于使用数据库内置的日期操作函数等。

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

如何将行转换为列？在对象层面，这等价于如何将列表中的一条记录转换为对象的一个扩展属性。在可逆计算理论中，这实际上是一个标准的结构变换操作：**对于任意的集合结构，我们可以为集合元素规定一个keyProp属性，从而把它转换为对象属性结构**。

例如 如果设置了keyProp=name，则entity.extFields.myKey 可以被翻译为  `entity.extFields[row => row.name == 'myKey']`

> keyProp的存在是定义一个稳定的领域坐标系统的关键。例如，在前台的虚拟DOM Diff算法中，为了能够稳定快速的识别出发生变化的组件，我们需要为组件指定`v-key`属性。

具体配置实例可以参见

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

上面的示例中演示了两种纵表设计，一种是全局扩展表，它支持所有实体表的扩展字段都存放到一张表中，通过entityName字段来区分不同的实体。另一种是专用扩展表，针对每一个特定的实体表可以建立一张单独的扩展表，参见SimsExamExtField。

如果结合横纵变换和上一节中介绍的alias属性别名机制，则可以进一步对扩展字段进行化简。比如上例中的extFldA实际对应的是ext.fldA.string。在EQL查询语言中，

```sql
select xxx from SimsExam o where o.extFldA = 'a'
-- 将被转换为
select xxx 
from SimsExam o left join SimeExtField f
   on f.entityId = and f.entityName = 'SimsExam'
where f.fieldName = 'fldA' and f.stringValue = 'a'   
```

因为行列转换是内置在EQL  AST Transformer中的机制，所以实际上我们可以对纵表字段进行查询和排序操作，只是性能较低而已。

上述行列转换的能力本质上是通用的，并不限定于对KVTable的转换。**任意的一对多子表都可以通过指定keyProp属性来转换为主表的关联属性**。例如

`entity.orders.odr333.orderDate` 表示获取编号为odr333的订单的orderDate属性。

### 3.4 元信息+宽表

因为ORM引擎本身就具有大量元信息，因此元信息+宽表的模式实际上是一般的ORM引擎都能够内置支持的。例如

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

上面的示例中所有的实体数据都存放在统一的GLOBAL\_STORE\_TABLE表中，其中为了存放MyEntity实体的数据，entityName这一列的值被设置为固定的字符串`"MyEntity"`。同时value1和value2被重命名为name和amount。VALUE2属性在数据库中的类型是VARCHAR，在java中的类型是Integer，通过指定stdDataType属性我们可以明确区分这两个层面的数据类型，并且自动实现两者之间的转换。基于上面的定义，我们可以像访问普通数据库表一样使用EQL语法来查询

```sql
select * from MyEntity o where o.name = 'a' and o.amount > 3
-- 会被翻译为
select * from GLOBAL_STORE_TABLE o
where o.ENTITY_NAME = 'MyEntity'
   and o.VALUE1 =  'a' and o.VALUE2 > '3'
```

借助于上一节中提到的别名机制，我们可以将多个一对一或者一对多的表拼接为一个逻辑上的大宽表。例如

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

### 3.5 使用单文件

NopOrm引擎支持为每种实体指定专用的persistDriver。因此，原则上只要实现了IEntityPersistDriver接口，即可将数据保存到数据文件中。如果进一步实现了IEntityDaoExtension接口，即可支持对数据文件中的记录进行复合查询和排序。

在Nop平台中，针对单表或者单实体的复合查询条件被抽象为QueryBean消息对象，它可以被自动转换为一个可执行的查询过滤器

```java
Predicate<Object> filter = QueryBeanHelper.toPredicate(
            queryBean.getFilter(), evalScope);
```

因此基于json或者csv文件实现一个简易的单实体存储模型并不是一件很复杂的事情。

随着数据湖技术的发展，目前单个数据文件已经逐步发展为具有内置索引，可以支持算子下推的某种数据库单表的替代品。在不久的未来，集成iceberg这样具有丰富特性的数据文件存储可能会变成一件很简单的事情。

## 未完待续

能坚持看到这里的应该已经没几位同学了吧。为了避免阅读量降到零，我决定本文的上半部分就此打住。在本文的下半部分中，我将继续讨论性能相关的N+1问题的解决，以及Dialect定制、GraphQL集成、可视化集成等相关的技术方案。

如果对可逆计算理论不熟悉，可以参考我此前的文章

[可逆计算：下一代软件构造理论](https://zhuanlan.zhihu.com/p/64004026)

[可逆计算的技术实现](https://zhuanlan.zhihu.com/p/163852896)

[从张量积看低代码平台的设计](https://zhuanlan.zhihu.com/p/531474176)

基于可逆计算理论设计的低代码平台NopPlatform已开源：

- gitee: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- github: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- 开发示例：[docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- [可逆计算原理和Nop平台介绍及答疑\_哔哩哔哩\_bilibili](https://www.bilibili.com/video/BV1u84y1w7kX/)
