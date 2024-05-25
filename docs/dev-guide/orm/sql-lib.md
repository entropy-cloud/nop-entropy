# 统一的SQL管理

当我们需要构造比较复杂的SQL或者EQL语句的时候，通过一个外部模型文件对它们进行管理无疑是有着重要价值的。MyBatis提供了这样一种把SQL语句模型化的机制，但是仍然有很多人倾向于在Java代码中通过QueryDsl这样的方案来动态拼接SQL。这实际上是在说明
**MyBatis的功能实现比较单薄，没有能够充分发挥模型化的优势**。

在NopOrm中，我们通过sql-lib模型来统一管理所有复杂的SQL/EQL/DQL语句。在利用Nop平台已有基础设施的情况下，实现类似MyBatis的这一SQL语句管理机制，大概只需要500行代码。具体实现代码参见

[SqlLibManager](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-orm/src/main/java/io/nop/orm/sql_lib/SqlLibManager.java)

[SqlItemModel](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-orm/src/main/java/io/nop/orm/sql_lib/SqlItemModel.java)

[SqlLibInvoker](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-orm/src/main/java/io/nop/orm/sql_lib/proxy/SqlLibInvoker.java)

测试用的sql-lib文件参见

[test.sql-lib.xml](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-orm/src/test/resources/_vfs/nop/test/sql/test.sql-lib.xml)

视频： [如何用500行代码实现类似MyBatis的功能](https://www.bilibili.com/video/BV1xX4y1e7Tv/)

sql-lib提供了如下特性

## 1  统一管理SQL/EQL/DQL

在sql-lib文件中存在三种节点，sql/eql/query分别对应于SQL语句，EQL语句和上一节介绍的润乾DQL查询模型，对它们可以采取统一的方式进行管理。

```xml

<sql-lib>
    <sqls>
        <sql name="xxx">...</sql>
        <eql name="yyy">...</eql>
        <query name="zz">...</query>
    </sqls>
</sql-lib>
```

模型化的第一个好处就是Nop平台内置的Delta定制机制。假设我们已经开发了一个Base产品，在客户处部署的时候需要针对客户的数据情况进行SQL优化，则我们
**无需修改任何Base产品的代码**，只需要添加一个sql-lib的差量化模型文件，就可以实现对任意SQL语句的定制。例如

```xml

<sql-lib x:extends="raw:/original.sql-lib.xml">
    <sqls>
        <!-- 同名的sql语句会覆盖基类文件中的定义 -->
        <eql name="yyy">...</eql>
    </sqls>
</sql-lib>
```

关于Delta定制，另一个常见用法是结合元编程机制。假设我们的系统是一个领域模型很规整的系统，存在大量类似的SQL语句，则我们可以通过元编程机制先在编译期自动生成这些SQL语句，然后再通过Delta定制来对它们进行改进就可以了。例如

```xml

<sql-lib>
    <x:gen-extends>
        <app:GenDefaultSql />
    </x:gen-extends>

    <sqls>
        <!-- 在这里可以对自动生成SQL进行定制 -->
        <eql name="yyy">...
        </eql>
    </sqls>
</sql-lib>
```

## 2. XPL模板的组件抽象能力

MyBatis只提供了foreach/if/include等少数几个固定标签，真正编写起高度复杂的动态SQL语句时可以说是有心无力。很多人觉得在xml中拼接sql比较麻烦，归根结底是因为MyBatis提供的是一个不完善的解决方案，它
**缺少二次抽象的机制**。 而在java程序中我们总可以通过函数封装来实现对某一段SQL拼接逻辑的复用，对比MyBatis却只有内置的三板斧，基本没有提供任何辅助复用的能力。

NopOrm直接采用XLang语言中的XPL模板语言来作为底层的生成引擎，因此它自动继承了XPL模板语言的标签抽象能力。

> XLang是专为可逆计算理论而生的程序语言，它包含XDefinition/XScript/Xpl/XTransform等多个部分，其核心设计思想是对抽象语法树AST的生成、转换和差量合并，可以认为它是针对Tree文法而设计的程序语言。

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

Xpl模板语言不仅内置了`<c:for>`,`<c:if>`等图灵完备语言所需的语法元素，而且允许通过自定制标签机制引入新的标签抽象（可以类比于前端的vue组件封装）。

有些模板语言要求所有能在模板中使用的函数需要提前注册，而Xpl模板语言可以直接调用Java。

```xml

<sql>
    <source>
        <c:script>
            import test.MyService;

            let service = new MyService();
            let bean = inject("MyBean"); // 直接获取IoC容器中注册的bean
        </c:script>
    </source>
</sql>
```

## 3. 宏(Macro)标签的元编程能力

MyBatis拼接动态SQL的方式很笨拙，因此一些类MyBatis的框架会在SQL模板层面提供一些特殊设计的简化语法。例如有些框架引入了隐式条件判断机制

```sql
select xxx
from my_entity
where id = :id
[and name=:name]
```

通过自动分析括号内的变量定义情况，自动增加一个隐式的条件判断，仅当name属性值不为空的时候才输出对应的SQL片段。

在NopOrm中，我们可以通过宏标签来实现类似的**局部语法结构变换**

```xml

<sql>
    <source>
        select o from MyEntity o
        where 1=1
        <sql:filter>and o.classId = :myVar</sql:filter>
    </source>
</sql>
```

`<sql:filter>`是一个宏标签，它在编译期执行，相当于是对源码结构进行变换，等价于手写的如下代码

```xml

<c:if test="${!_.isEmpty(myVar)}">
    and o.classId = ${myVar}
</c:if>
```

具体标签的实现参见

[sql.xlib](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-orm/src/main/resources/_vfs/nop/orm/xlib/sql.xlib)

本质上这个概念等价于Lisp语言中的宏，特别是它与Lisp宏一样，可以用于程序代码中的任意部分（即AST的任意节点都可以被替换为宏节点）。只不过，它采用XML的表现形式，相比于Lisp惜字如金的数学符号风格而言，显得更加人性化一些。

微软C#语言的LINQ（语言集成查询）语法，其实现原理是在编译期获取到表达式的抽象语法树对象，然后交由应用代码执行结构变换，本质上也是一种编译期的宏变换技术。在XLang语言中，除了Xpl模板所提供的宏标签之外，还可以使用XScript的宏函数来实现SQL语法和对象语法之间的转换。例如

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

XScript的模板表达式会自动识别宏函数，并在编译期自动执行。因此我们可以定义一个宏函数linq，它将模板字符串在编译期解析为SQL语法树，然后再变换为普通的JavaScript
AST，从而相当于是在面向对象的XScript语法（类似TypeScript的脚本语言）中嵌入类SQL语法的DSL，可以完成类似LinQ的功能，但是实现方式要简单得多，形式上也更接近SQL的原始形式。

> 以上仅为概念示例，目前Nop平台仅提供了xpath/jpath/xpl等宏函数，并没有提供内置的linq宏函数。

## 4. 模板语言的SQL输出模式

模板语言相对于普通程序语言而言，它的设计偏置是将输出（Output）这一副作用作为第一类（first
class）的概念。当我们没有做任何特殊修饰的时候，就表示对外输出，而如果我们要表示执行其他逻辑，则需要用表达式、标签等形式明确的隔离出来。Xpl模板语言作为一种Generic的模板语言，它对输出这一概念进行了强化，增加了多模式输出的设计。

Xpl模板语言支持多种输出模式（Output Mode）

* text: 普通文本的输出，不需要进行额外转义

* xml: XML格式文本的输出，自动按照XML规范进行转义

* node: 结构化AST的输出，会保留源码位置

* sql：支持SQL对象的输出，杜绝SQL注入攻击

sql模式针对SQL输出的情况做了特殊处理，主要增加了如下规则

1. 如果输出对象，则替换为?，并把对象收集到参数集合中。例如 `id = ${id}` 实际将生成id=?的sql文本，同时通过一个List来保存参数值。

2. 如果输出集合对象，则自动展开为多个参数。例如 `id in (${ids})` 对应生成id in (?,?,?)。

如果确实希望直接输出SQL文本，拼接到SQL语句中，可以使用raw函数来包装。

```
from MyEntity_${raw(postfix)} o
```

此外，NopOrm对于参数化SQL对象本身也建立了一个简单的包装模型

```
SQL = Text + Params
```

通过sql = SQL.begin().sql("o.id = ? ", name).end() 这种形式可以构造带参数的SQL语句对象。Xpl模板的sql输出模式会自动识别SQL对象，并自动对文本和参数集合分别进行处理。

## 5. 自动验证

外部文件中管理SQL模板存在一个缺点：它无法依赖类型系统进行校验，只能期待运行时测试来检查SQL语法是否正确。如果数据模型发生变化，则可能无法立刻发现哪些SQL语句受到影响。
对于这个问题，其实存在一些比较简单的解决方案。毕竟，SQL语句既然已经作为结构化的模型被管理起来了，我们能够对它们进行操作的手段就变得异常丰富起来。
NopOrm内置了一个类似Contract Based
Programming的机制：每个EQL语句的模型都支持一个validate-input配置，我们可以在其中准备一些测试数据，然后ORM引擎在加载sql-lib的时候会自动运行validate-input得到测试数据，并以测试数据为基础执行SQL模板来生成EQL语句，然后交由EQL解析器来分析它的合法性，从而实现以一种准静态分析的方式检查ORM模型与EQL语句的一致性。

## 6. 调试支持

与MyBatis内置的自制简易模板语言不同，NopOrm使用Xpl模板语言来生成SQL语句，因此可以很自然的可以利用XLang语言调试器来调试。Nop平台提供了IDEA开发插件，支持DSL语法提示和断点调试功能。它会自动读取sql-lib.xdef元模型定义文件，根据元模型自动校验sql-lib文件的语法正确性，并提供语法提示功能，支持在source段增加断点，进行单步调试等。

Nop平台中所有的DSL都是基于可逆计算原理构建的，它们都使用统一的元模型定义语言XDefinition来描述，所以并不需要针对每一种DSL来单独开发IDE插件和断点调试器。为了给自定义的sql-lib模型增加IDE支持，唯一需要的就是在模型根节点上增加属性x:
schema="/nop/schema/orm/sql-lib.xdef"，引入xdef元模型。

XLang语言还内置了一些调试特性，方便在元编程阶段对问题进行诊断。

1. outputMode=node输出模式下生成的AST节点会自动保留源文件的行号，因此当生成的代码编译报错时，我们直接对应到源文件的代码位置。

2. Xpl模板语言节点上可以增加xpl:dump属性，打印出当前节点经动态编译后得到的AST语法树

3. 任何表达式都可以追加调用扩展函数`$`，它会自动打印当前表达式对应的文本、行号以及表达式执行的结果, 并返回表达式的结果值。例如

```
x = a.f().$(prefix) 实际对应于
x = DebugHelper.v(location,prefix, "a.f()",a.f())
```

## 7. 根据Dialect生成对应的SQL语句

利用标签库可以引入各种自定义的扩展逻辑。比如根据不同的数据库方言生成不同的SQL语句。

```
select
<sql:when-dialect name="h2">
    ...
</sql:when-dialect>
        from my_entity
```

## 8. Mapper接口

只要在Excel数据模型中为实体增加mapper标签，代码生成的时候就会自动生成类似MyBatis的强类型的Mapper接口，通过它可以调用SqlLibManager所管理的SQL模型文件。例如[LitemallGoodsMapper.java](https://gitee.com/canonical-entropy/nop-app-mall/blob/master/app-mall-dao/src/main/java/app/mall/dao/mapper/LitemallGoodsMapper.java)。

```java

@SqlLibMapper("/app/mall/sql/LitemallGoods.sql-lib.xml")
public interface LitemallGoodsMapper {

    void syncCartProduct(@Name("product") LitemallGoodsProduct product);
}
```

通过SqlLibMapper注解可以指定当前接口所关联的SQL模型文件。

## 9. 使用native sql加载实体对象
一般情况下我们使用`<eql>`节点来加载实体数据。但是如果设置rowType为实体类型，则也可以使用`<sql>`节点来加载实体数据

```xml
  <sql name="testOrmEntityRowMapper" rowType="io.nop.app.SimsClass" sqlMethod="findFirst"
       colNameCamelCase="true" >
      <source>
          select o.class_id, o.class_name, o.college_id
          from sims_class o
      </source>
  </sql>
```

* 设置了colNameCamelCase会自动将`class_id`这样的返回字段名转换为`classId`这样的实体属性名
* 如果SQL语句返回的结果中没有包含主键字段，则会新建实体对象，否则会根据id加载当前OrmSession中的实体，并更新实体上的属性。
* 如果执行SQL之前对应的实体数据已经加载到内存中，且已经被修改，则执行SQL会抛出异常`nop.err.orm.entity-prop-is-dirty`。如果没有被修改，则会更新实体属性。
* 可以通过ormEntityRefreshBehavior来改变上面的行为。errorWhenDirty是缺省行为。useFirst将保留第一次加载的实体数据，忽略当前SQL查询得到的数据。useLast则使用最后一次查询得到的数据。

## 与MyBatis的对比

|MyBatis|Nop平台|
|---|---|
|通过XML配置动态SQL|通过统一的Delta定制实现配置修正|
|通过Mapper接口封装SQL的执行|Nop平台使用统一的@Name注解定义参数名，通过IEvalContext来传递上下文对象|
|通过标签函数生成动态SQL|Nop平台中通过Xpl标签库引入自定义标签|
|通过表达式生成SQL参数|表达式使用通用的表达式引擎，利用Xpl模板语言的SQL输出模式将输出的表达式结果转换为SQL参数|
|支持事务、结果数据缓存等|利用Dao层的JdbcTemplate，自动支持事务和结果缓存|
|管理SQL语句|同时管理EQL、SQL、DQL等各类查询语言|

利用Nop平台的内置机制，还可以自动支持如下功能：

1. 多数据源、多租户、分库分表

2. 将SQL语句直接暴露为前台可访问的字典表，此时字典表名称格式为sql/{sqlName}

3. 使用EQL查询语言时支持批量属性加载，在获取到结果数据之后，可以直接指定加载结果数据中的关联属性。

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

## 扩展配置

### enableFilter属性设置为true，将会启用数据权限过滤

OrmSessionFactory支持IEntityFilterProvider配置，nop-auth-service模块提供的缺省实现对应于数据权限过滤

```xml

<eql name="xxx" enableFilter="true">
    <source>
        select u.xx from MyEntity u, OtherEntity t where u.fldA = t.fldA
    </source>
</eql>
```

也可以使用在构造SQL对象时直接指定enableFilter属性

```javascript
   SQL sql = SQL.begin().enableFilter(true).sql("...").end();
```

启用enableFilter后，会自动利用`IServiceContext.bindingCtx()`获取当前上下文中的IServiceContext，并调用`IDataAuthChecker.getFilter()`
来获取到数据权限过滤条件，转换为SQL语句后拼接到原始的SQL语句中。

> 如果允许用户直接编写SQL语句，则可以利用enableFilter特性来自动追加数据权限过滤条件，避免产生数据泄露

### allowUnderscoreName设置为true，将会允许在EQL中直接使用数据库字段名

```
<eql name="xx" allowUnderscoreName="true">
  <source>
     select o.statusId, o.status_id from MyEntity o
  </source>
</eql>
```

使用statusId或者status\_id都可以访问实体上的属性。

也可以使用在构造SQL对象时直接指定allowUnderscoreName属性

```javascript
   SQL.begin().allowUnderscoreName(true).sql("....").end();
```
