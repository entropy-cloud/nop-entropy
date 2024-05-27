# Nop入门：动态SQL管理

Nop平台提供了类似MyBatis的动态SQL管理能力，但是功能特性远比MyBatis丰富、强大。同时它的实现反而更加简单，在NopORM的基础上实现SqlLibManager只需要300多行的代码。

讲解视频： https://www.bilibili.com/video/BV1Xi421S7oG/

## 一. 使用说明

### 1.1 增加一个`sql-lib.xml`文件

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

* 如果没有指定sqlMethod，则会根据SQL语句和是否传入range参数来自动推定。sqlMethod的可选值有findFirst/findPage/findAll/execute等
* 通过`${expr}`形式引入的表达式会被自动替换为SQL参数，并不是直接作为字符串拼接在一起。如果表达式返回的是集合对象，还会自动展开成一组参数。

### 1.2 增加一个Mapper类

通过`@SqlLibMapper`注解指定关联的`sql-lib.xml`文件。

```java
@SqlLibMapper("/nop/demo/sql/demo.sql-lib.xml")
public interface DemoMapper {
  DemoEntity findFirstByName(@Name("name") String name);
}
```

### 1.3 在beans.xml注册Mapper Bean

```xml
<bean id="io.nop.demo.biz.DemoMapper"
      class="io.nop.orm.sql_lib.proxy.SqlLibProxyFactoryBean"
      ioc:type="@bean:id" ioc:bean-method="build">
  <property name="mapperClass" value="@bean:type"/>
</bean>
```

* 通过Excel模型生成代码时，如果数据表具有mapper标签，则会自动生成Mapper接口类以及上述Mapper Bean的定义。

| MyBatis            | Nop平台                                            |
| ------------------ | ------------------------------------------------ |
| 通过XML配置动态SQL       | 通过统一的Delta定制实现配置修正                               |
| 通过Mapper接口封装SQL的执行 | Nop平台使用统一的@Name注解定义参数名，通过IEvalContext来传递上下文对象    |
| 通过固定的几个标签函数生成动态SQL | Nop平台中通过Xpl标签库引入自定义标签                            |
| 通过表达式生成SQL参数       | 表达式使用通用的表达式引擎，利用Xpl模板语言的SQL输出模式将输出的表达式结果转换为SQL参数 |
| 支持事务、结果数据缓存等       | 利用Dao层的JdbcTemplate，自动支持事务和结果缓存                  |
| 管理SQL语句            | 同时管理EQL、SQL、DQL等各类查询语言                           |

## 二. 为什么使用XML文件是一种优点

目前JPA和MyBatisPlus这种框架已经基本不使用XML配置格式，全部通过Java注解来实现配置。这导致很多人误以为XML格式已经完全过时，甚至看到使用XML格式的框架就会强烈反对。
但是这实际上是一种刻板印象和不正确的认知。

使用XML文件相比于注解存在很多好处。

### 2.1 调试时不用停机就可以调整SQL语句

Nop平台中的所有模型文件都支持动态加载，只要修改模型文件，或者它所依赖的文件，模型解析缓存会自动更新。所以在调试时远比使用Java注解便捷

### 2.2 通过Delta定制调整SQL语句

当sql-lib模型文件已经打包到Jar包中之后，无需修改的原始xml文件，在独立的delta模块的delta目录下增加一个同名文件即可覆盖基础模块中的文件。

而使用Java注解的情况下，我们无法通过简单、通用的方式来定制jar包中的SQL语句。

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

我们可以只定制指定的某个SQL语句，或者只定制某个指定属性。而在MyBatis中定制mapper文件的时候只能整体替换。

关于Delta定制的详细介绍，参见[delta-customization.md](../../dev-guide/delta/delta-customization.md)

### 2.3 无代码开发可以在线调整SQL语句

Nop平台中所有的模型文件统一使用虚拟文件系统进行管理，而虚拟文件系统可以将数据库中的某个配置表也当作虚拟文件来处理。通过这种方式，我们可以实现在界面上配置SQL语句，
而编程时可以将它看作是一个普通的sql-lib模型文件，并且复用Nop平台内置的模型缓存、模型依赖关系追踪等能力。

### 2.4 二次抽象能力

Nop平台的模型文件加载时支持元编程处理，并且生成SQL语句时使用的XPL模板语言也支持自定义标签支持机制。这使得我们可以轻松发现SQL构造中的通用模式，并提供自定义的抽象。
比如说我们发现一个SQL片段经常出现，可以用自定义标签库将它抽象为一个函数，而使用Java注解，一般我们只能使用框架内置的抽象，没有进一步简化配置的可能性。

### 2.5 基于元模型生成IDE提示和可视化设计器

MyBatis的IDE插件需要单独去编写。而在Nop平台中，任何DSL文件只要通过`x:schema`引入对应的元模型，通过通用的`nop-idea-plugin`
插件即可自动推导得到语法提示、断点调试等功能。
类似的，可以根据元模型自动推导得到在线可视化设计器，直接设计对应的DSL文件。

## 三. 强大的XPL模板语言

MyBatis的一个根本性设计问题在于它只提供了少数内置的标签，在实际使用过程中明显可以感觉到抽象能力不足。
在Nop平台中，我们使用XPL模板语言来生成SQL语句，可以通过XPL标签库来引入无限多的自定义抽象。

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

* `<sql:fragment>`和`<sql:when-dialect>`这样的标签都是`sql.xlib`标签库中自定义的标签，并不是引擎中内置的功能。
* 我们增加更多业务相关的标签，比如 `<app:FilterTopProduct/>`等。
* XPL模板语言内置了`<c:if>`、`<c:for>`等大量语法结构，支持类似于JavaScript的表达式语法。可以直接通过`import`导入java类

## 三. 面向OLAP的主子表查询

润乾公司开源了一个[前端BI系统](http://www.raqsoft.com.cn/r/os-bi)，它在技术层面提出了一个别致的DQL(Dimentional Query
Language)语言。具体介绍可以参考乾学院的文章

[告别宽表，用 DQL 成就新一代 BI - 乾学院](http://c.raqsoft.com.cn/article/1653901344139?p=1&m=0)

润乾的观点是终端用户难以理解复杂的SQL JOIN，为了便于多维分析，只能使用大宽表，这为数据准备带来一系列困难。而DQL则是简化了对终端用户而言JOIN操作的心智模型，并且在性能上相比于SQL更有优势。

例如，使用DQL可以简化主子表关联的汇总查询

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

Nop平台通过QueryBean抽象实现了类似于DQL的这种组合查询能力

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

可以在前端提供一个可视化设计器直接设计query对象。

在Java代码中

```java
QueryBean query = new QueryBean();
query.fields(mainField("orderNo"), mainField("customer"),
   subField("orderDetials","price").sum());
query.setSourceName("Order");
```



## 四. 更多MyBatis和JPA不具备的高级功能

### 4.1 批量加载关联属性

支持关联集合的ORM引擎很容易产生`N+1`问题，在`sql-lib`文件中可以配置batchLoadSelection实现类似GraphQL的批量加载机制，减少数据库访问次数。

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

### 4.2 启用数据权限过滤

开启enableFilter属性为true之后，会针对每个实体对象自动追加对应的数据权限过滤条件。如果结合NopORM引擎内置的IEqlAstTransformer机制，可以对EQL进行严格的格式检查和权限限制。

```xml
<eql name="findFirstByName" enableFilter="true" sqlMethod="findFirst">
    <source>
        select u from NopAuthUser u where u.userName like ${'%' + name + '%'}
    </source>
</eql>
```

一般情况下因为考虑到安全性问题，我们并不会把EQL语言的编辑权限开放给用户，但是借助于enableFilter和astTransformer机制，我们可以有效的限制用户使用EQL时访问的数据范围，杜绝SQL注入攻击。

### 4.3 多数据源支持

通过querySpace属性可以指定使用不同的DataSource，从而访问不同的数据库

```xml
<sql querySpace="report">
  ...
</sql>
```

在`beans.xml`中需要注册对应的DataSource, bean的id的格式为`nopDataSource_{querySpace}`

```xml
<bean id="nopDataSource_report"  class="com.zaxxer.hikari.HikariDataSource">
  ...
</bean>
```

### 4.4 使用Native SQL查询得到实体对象

一般情况下我们使用`<eql>`节点来加载实体数据。但是如果设置rowType为实体类型，则也可以使用`<sql>`节点来加载实体数据。

返回结果包装为实体对象后，会自动提供关联属性延迟加载功能。

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

更详细的介绍参见[sql-lib.md](../../dev-guide/orm/sql-lib.md)

### 4.5 直接作为数据字典

如果sql的名称以`_dict`为后缀，则可以通过DictProvider来调用它，获取到的结果被包装为DictBean对象。

```javascript
 DictBean dict = DictProvider.instance().getDict(null, "sql/test.demo_dict", null, scope);
```

SQL语句要求必须包含value和label字段

```xml
<eql name="demo_dict">
    <source>
        select o.collegeId as value, o.collegeName as label
        from SimsCollege o
    </source>
</eql>
```
