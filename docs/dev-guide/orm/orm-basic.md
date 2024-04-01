# 基本使用方式

QueryBean提供了复杂查询条件封装，它所支持的查询操作符可以参考[filter.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/query/filter.xdef)
元模型中的定义。

## 1. 复杂查询条件

```javascript
// MyBatisPlus
LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
wrapper.eq(User::getUsername, "张三")
        .and(w -> w.between(User::getAge, 18, 30)
                .or().eq(User::getGender, 1))
        .orderByDesc(User::getCreateTime);
List<User> userList = userMapper.selectList(wrapper);

// NopORM

QueryBean query = new QueryBean();
query.addFilter(eq(PROP_Name_username,"张三"))
    .addFilter(and(
      or(
        between(PROP_NAME_age, 18,30),
        eq(PROP_NAME_gender, 1)
      )))
     .addOrderField(PROP_NAME_createTime, true);
      
IEntityDao<User> dao = daoProvider.daoFor(User.class);      
List<User> usreList = dao.findAllByQuery(query);      

// 如果分页查询
query.setOffset(100);
queyr.setLimit(20);
List<User> userList = dao.findPageByQuery(query);

// 如果只查询一条记录
User user = dao.findFirstByQuery(query);
```

## 2. 仅根据等于条件进行查询

```javascript
User example = new User();
user.setStatus(10);

IEntityDao<User> dao = daoProvider.daoFor(User.class);     
List<User> userList = dao.findAllByExample(example);
User user = dao.findFirstbyExample(example);
long count = dao.countByExample(example);
```

## 3. 嵌入子查询

```javascript
QueryBean query = new QueryBean();
query.addFilter(SQL.begin("o.id in (select y.xx from tbl y where y.id=?", 3).end().asFilter());
dao.findPageByQuery(query);
```

## 4. 联表查询

如果按照主表上的字段进行查询，可以直接使用复合属性，在NopORM引擎中，o.product.productType.name这种复合属性会自动根据主外键关联配置生成关联表查询语句

```
QueryBean query = new QueryBean();
query.addFiler(eq("product.productType.name","abc"));
dao.findFirstByQuery(query);
```

如果需要按照子表中的属性查找主表对象，可以使用上一节中介绍的子查询过滤，也可以直接使用EQL查询语言

```
select distinct o.book from BookAuthor o where o.author.name like '张%'
```

以上查询语句根据作者的名称查找他所写过的书, BookAuthor是一个关联表，通过o.author.name实现对author表的关联查询，而通过o.book返回关联的书籍对象。

如果不写distinct关键字，则因为是多对多关系，返回的集合中可能会包含重复元素。

## 5. 在sql-lib等标签库中的查询条件

Nop平台非常强调同一种模型信息存在多种表达形式，并且这些形式之间可以自由的转换。作为一个典型用例，Filter提供了一种标准的复杂判断条件表达形式。
它的信息结构由[filter.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/query/filter.xdef)
元模型来定义。

1. 在Java中我们可以通过FilterBeans类上的and/or/eq/gt等帮助函数来构建TreeBean对象。
2. 在XML和Xpl模板语言中，我们可以用`<eq name="status" value="1" />`这种XML语法来表达同样的判断条件
3. FilterBeanToSQLTransformer类可以将Filter信息转换为SQL查询语句，例如 `o.status = 1`
4. FilterBeanToPredicateTransformer类可以将Filter信息转换为Java中的Predicate接口，在内存中执行判断

在规则引擎中我们使用的判断条件也是由Filter模型来定义。
![rule-model.png](../rule/images/rule-model.png)

我们在xpl模板语言中更加自由的使用Filter过滤

* 在xbiz模型中实现查询函数

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

bo.xlib中提供了对CrudBizModel中doFindPage等函数的封装

* 在sql-lib中定义SQL语句
  sql-lib提供了类似MyBatis的SQL管理功能，可以通过SqlMapper接口来调用sql-lib中管理的sql语句，也可以直接通过SqlLibManager来调用

```java

@SqlLibMapper("/app/mall/sql/LitemallGoods.sql-lib.xml")
public interface LitemallGoodsMapper {
    void syncCartProduct(@Name("product") LitemallGoodsProduct product);
}
```

在LitmallGoods.sql-lib.xml中

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

其中eql表示使用EQL对象查询语法，它使用实体名、属性名来访问数据，语法与SQL类似，但是支持复合属性，例如 o.product.type
会自动识别外键关联，并转换为关联查询条件。

如果使用sql标签，则表示使用原生SQL语法。也就是说sql-lib中是同时管理SQL查询语句和EQL查询语句。

* 在数据权限配置中使用
  在/nop/main/auth/app.data-auth.xml文件中配置数据权限

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

在xpl模板语言中，我们引入自定义标签来简化Filter的编写，例如

```xml

<and>
    <eq name="status" value="1"/>
    <app:FilterByTask/>
</and>
```

`<app:FilterByTask>`是一个自定义标签，它只要能够输出一个符合格式要求的XML节点即可（实际上是XNode类型，它实现了ITreeBean接口）。

也可以增加动态判断条件

```
<bo:FindPage>
  <filter>
     <c:if test="${request.status}">
        <eq name="status" value="${request.status}" />
     </c:if>
  </filter>
</bo:FindPage>
```

也可以利用元编程机制简化标签编写,例如 `<sql:filter>`是一个宏标签，它在编译期执行，相当于是对源码结构进行变换

```xml

<sql:filter>and o.classId = :myVar</sql:filter>
```

等价于手写如下代码

```xml

<c:if test="${!_.isEmpty(myVar)}">
    and o.classId = ${myVar}
</c:if>
```

## SingleSession支持

IEntityDao上的方法是调用底层的ormTemplate来实现的，而OrmTemplate采用了类似Spring的模板模式，每个函数都会自动开启session来使用，如果上下文环境中已经有session对象，则自动复用.

NopGraphQL引擎执行时已经自动开启了OrmSession，所以一般的业务代码中不需要考虑手动打开session。

如果要在GraphQL引擎之外使用ORM，可以在函数上标注 `@SingleSession`和 `@Transactional`(
注意使用Nop平台内定义的Transactional)注解，
它们会自动打开OrmSession和事务管理器。

如果要手工打开session，可以采用如下方法

```javascript

@Inject
IOrmTemplate ormTemplate;

ormTemplate.runInSession(session->{
  ...
})
```

事务管理类似

```javascript

@Inject
ITransactionTemplate transactionTemplate;

transactionTemplate.runInTransaction(txn->{
   ...
})
```
