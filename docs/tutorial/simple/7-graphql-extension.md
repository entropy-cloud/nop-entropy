# Nop入门： 如何创造性的扩展GraphQL

Nop平台没有使用`graphql-java`等常用的GraphQL开源库，而是选择从零开始实现NopGraphQL引擎。NopGraphQL引擎创造了很多崭新的实现方案，拓宽了GraphQL的应用范围，提高了GraphQL的实用性。

详细文档参见 [graphql/index.md](../../dev-guide/graphql/index.md)

## 一. 利用Fragment定义简化GraphQL查询

GraphQL要求在前端调用时指定返回的字段，对于字段比较多的情况会显得比较繁琐。此时我们可以利用GraphQL语言的Fragment功能来定义一些常用的字段集合，然后在查询时引用这些Fragment，从而简化查询。

### 1.1 在XMeta中增加selection定义，并以`F_`为前缀

Nop平台中每一个后台服务对象都有一个相关联的XMeta元数据模型文件，在其中可以通过为GraphQL类型补充元数据信息。

```xml
<meta>
  <selections>
    <selection id="F_defaults">
      userId, userName, status, relatedRoleList{ roleName}
    </selection>
  </selections>
</meta>
```

* 这里约定了必须用`F_`为前缀才是前台可以访问的Fragment定义。selection还有其他的用处。
* 如果不配置`F_defaults`，它会根据GraphQL类型的所有非lazy字段自动推定。如果明确指定了，则以指定的内容为准

### 1.2 在前台查询时引用Fragment

使用GraphQL方式调用后台服务时可以使用Fragment

```graphql
query{
   NopAuthUser__findList{
     ...F_defaults, groupMappings{...F_defaults}
   }
}
```

或者使用REST方式来调用后台服务，通过`@selection`参数使用Fragment

```
/r/NopAuthUser__findList?@selection=...F_defaults,groupMappings
```

* REST方式调用时，如果不传`@selection`参数，则等价于返回`F_defaults`

**REST方式下的selection如果只表达到对象层级，则会自动向下展开。**

## 二. 通过`@TreeChildren`指令简化树形结构查询

对于单位树、菜单树这样的树形结构的获取，NopGraphQL通过Directive机制提供了一个扩展语法，可以直接表达递归拉取数据，例如

```
NopAuthDept_findList{
    value: id,
    label: displayName
    children @TreeChildren(max=5)
}
```

* `@TreeChildren(max=5)`表示按照本层的结构最多嵌套5层。

## 三. Map类型

GraphQL是一种强类型的框架，它要求所有数据都有明确的类型定义，这在某些动态场景中使用时并不方便。例如有的时候我们可能需要把一个扩展集合返回到前端。

NopGraphQL引入了一个特殊的Scalar类型: Map，可以利用它来描述那些动态数据结构。例如

```graphql
type QueryBean{
    filter: Map
    orderBy: [OrderFieldBean]
}
```

## 四. XMeta元数据模型

利用XMeta元数据模型可以通过配置实现很多功能。

### 4.1 通过mapToProp映射到已有属性

```xml
<prop name="a" mapToProp="b.a">
</prop>
```

* 通过mapToProp属性可以为已有属性指定一个别名。当前台访问属性a时，实际获取的是关联对象b上的属性a

### 4.2 通过getter直接指定计算表达式

在NopGraphQL中可以在BizModel服务类中通过`@BizLoader`注解来引入动态计算的字段。

```java
@BizModel("LoginApi")
public class LoginApiBizModelDelta {
    @BizLoader(autoCreateField = true, forType = LoginResult.class)
    @LazyLoad
    public String location(@ContextSource LoginResult result,
                           IServiceContext context) {
        return "loc:" + result.getUserInfo().getUserId();
    }
}
```

对于一些轻量级的计算表达式，这种定义服务函数的方式显得有些过于复杂，此时我们可以直接在XMeta中通过getter表达式来定义

```xml
<prop name="myValue">
  <getter>
    return entity.name + 'Ext'
  </getter>
</prop>
```

### 4.3 字段级别的权限控制

在xmeta文件中，可以为`prop`指定`auth`设置

```xml

<prop name="xx">
    <auth permissions="NopAuthUser:query" roles="admin" for="read"/>
    <auth permissions="NopAuthUser:mutation" roles="hr" for="write"/>
</prop>
```

* 通过这里的配置可以实现字段级别的读写权限控制. `for="read"`表示控制字段读权限，`for="write"`控制字段写权限，而`for="all"`同时允许读和写
* NopGraphQL引擎在执行实际动作之前会检查SelectionSet中每个字段的访问权限，因此不会出现执行完业务操作后才发现无权访问某个结果字段的情况。
* 关于数据权限和关联子表的过滤条件配置，参见[4-complex-query.md](4-complex-query.md)

### 4.4 自动生成数据字典文本字段
业务开发中一个非常常见的需求是将后台业务字段的值根据某个数据字典配置翻译为显示文本。在Nop平台中，XMeta模型文件在加载阶段会利用元编程机制动态判断是否配置了数据字典。
如果是，则会自动生成一个字典翻译字段。

```xml

<prop name="status">
    <schema type="Integer" dict="auth/user-status"/>
</prop>
```
经过元编程转换后实际生成如下字段定义

```xml

<prop name="status" graphql:labelProp="status_label">
    <schema type="Integer" dict="auth/user-status"/>
</prop>
<prop name="status_label" internal="true" graphql:dictName="auth/user-status"
      graphql:dictValueProp="status">
    <schema type="String"/>
</prop>
```

### 4.5 掩码显示
处于安全性考虑，一些敏感的用户信息不允许打印到日志文件中，返回给前台演示的时候也需要进行掩码处理，不能显示全部内容，只能显示前几位、后几位等，
例如信用卡卡号，用户的电话号码等。

通过`ui:maskPattern`可以指定掩码显示模式，当GraphQL返回字段值时会自动按照此模式进行处理。

```xml
<prop name="email" ui:maskPattern="3*4">

</prop>
```

* `ui:maskPattern="3*4"` 表示保留前3位以及后4位字符，其他用\*来替换。

