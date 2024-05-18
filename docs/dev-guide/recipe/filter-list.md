# 如何实现对列表列表数据的过滤

问题：希望为表格增加过滤条件，比如只显示type=2的数据，或者只显示用户有权限看到的数据。
存在以下多种解决方案：

## 1. 通过数据权限实现过滤

后台内置的findPage、findList和findFirst动作都会应用数据权限检查接口 IDataAuthChecker。
通过nop.auth.enable-data-auth来启用数据权限，缺省为true

* 通过auth模块的数据权限菜单配置数据权限，它对应于NopAuthRoleDataAuth表。
* 在/nop/main/auth/app.data-auth.xml文件中配置数据权限

```xml
<data-auth>
    <objs>
        <obj name="MyEntity">
            <role-auths>
                <role-auth roleId="manager">
                    <filter>
                        <eq name="type" value="1" />
                    </filter>
                </role-auth>
            </role-auths>
        </obj>
    </objs>
</data-auth>
```

具体数据权限配置参见 [auth.md](../auth/auth.md)

### 2. 在前台拼接过滤条件

XView模型中定义的grid可以配置filter条件，使用该grid生成的表格查询数据时会自动携带过滤条件。

```
<grid id="list">
  <cols>
    ...
  </cols>

  <filter>
    <eq name="type" value="1" />
  </filter>
</grid>
```

**注意在grid中配置的条件是作为前端的查询条件传播到后台的**

### 3. 后台XMeta对象配置过滤条件

xmeta配置文件中可以配置filter过滤条件。如果在meta中配置，则新增、修改的时候也会按照这里的过滤条件自动设置。例如

```
<filter>
   <eq name="type" value="1" />
</filter>
```

则新增和修改的时候都会固定设置type=1。

基于已有的xmeta，可以新建xmeta。例如 MyEntity\_ext.xmeta

```
<meta x:extends="MyEntity.xmeta">
  <filter>
     <eq name="type" value="1" />
  </filter>
</meta>
```

前台调用时使用对象名`MyEntity_ext`就会自动应用这里的meta。例如

```
query{
   MyEntity_ext__findPage{
      ...
   }
}
```

在meta中增加的过滤条件是在后台拼接的，前台看不见。而且它会影响到新建和修改操作，**新建和修改会按照过滤条件中的值进行设置**，确保实体满足filer要求

### 4. 在后台BizModel中增加新的方法

```
class MyEntityBizModel extends CrudBizModel<MyEntity>{
    @BizQuery
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public PageBean<T> findPage_ext(@Name("query") @Description("@i18n:biz.query|查询条件") QueryBean query,
                                FieldSelectionBean selection, IServiceContext context) {
        if (query != null)
            query.setDisableLogicalDelete(false);

        return doFindPage(query, this::addExtQuery, selection, context);
    }

    protected void defaultPrepareQuery(@Name("query") QueryBean query, IServiceContext context) {
        query.addFilter(FilterBeans.eq(MyEntity.PROP_NAME_status,1));
    }
}
```

在前台可以继承已有的页面，然后定制其中的api数据请求链接

```
<form id="edit">
  <cells>
    <cell id="productId">
       <gen-control>
          return {
                "x:extends":"xxx.page.yaml",
                initApi: {
                   url: "@query:MyEntity__findPage_ext/{@gridSelection}"
                }
            }
       </gen-control>
    </cell>
  </cells>
</form>
```

### 5. 采用子查询进行过滤

```xml
<filter>
    <eq name="type" value="${3}" />
    <filter:sql xpl:lib="/nop/core/xlib/filter.xlib">
        o.id in (select t.task.id from MyTask t where t.userId = ${$context.userId || '1'})
    </filter:sql>
</filter>
```

`<filter:sql>`会生成`<sql value="SQL" />`这样的过滤条件。

这里的o表示当前实体类，整个filter段会被翻译为where条件，例如上面的过滤条件会被翻译为

```sql
o.type = 3
and o.id in (select t.task.id from MyTask t where t.userId = ${$context.userId || '1'})
```

> sql节点的value属性必须是SQL类型，不能是简单的文本字符串，这样约定的目的是避免前台提交filter查询条件插入子查询形成SQL注入攻击。要求value必须是SQL类型，就只能是在后台通过程序来构造。

在Java程序中可以通过如下方式追加子查询条件

```java
QueryBean query= ...;
SQL sql = SQL.begin().sql("o.id in (select t.task.id from MyTask t where t.userId = ?",userId).end();
query.addFilter(FilterBeans.assertOp("sql",sql));
```

SQL.begin()会返回一个SqlBuilder对象，它提供了很多帮助函数用于简化SQL语句的拼接。

### 6. 在XBiz模型文件中配置过滤条件

```xml
<query name="active_findPage" x:prototype="findPage">

    <source>
        <c:import class="io.nop.auth.api.AuthApiConstants" />

        <bo:DoFindPage query="${query}" selection="${selection}" xpl:lib="/nop/biz/xlib/bo.xlib">
            <filter>
                <eq name="status" value="${AuthApiConstants.USER_STATUS_ACTIVE}" />
            </filter>
        </bo:DoFindPage>
    </source>
</query>
```

* 在xbiz文件中配置的方法优先级更高，如果和Java中BizModel的函数名重名，则会覆盖Java中的实现。

* bo.xlib提供了一系列缺省实现，我们可以在缺省实现的基础上增加额外的过滤条件。

* 自动生成xbiz文件中已经包含了findPage/findList/save/update等标准CRUD函数的参数声明，通过`x:prototype="findPage"`继承已有配置，则可以简化类似函数的编写。
  一般在标准函数上的扩展，我们命名时都采用规则`{extName}_{stdName}`，这样前台可以自动推定得到GraphQL调用的参数类型和返回值类型，从而避免在前台再声明类型。

上面的代码完全展开后对应如下代码(可以在`_dump`目录下查看)

```xml
<!--LOC:[49:26:0:0]/nop/core/xlib/biz-gen.xlib#/nop/auth/model/NopAuthUser/_NopAuthUser.xbiz
 @name=[6:22:0:0]/nop/auth/model/NopAuthUser/NopAuthUser.xbiz
-->
<query name="active_findPage">
    <arg name="query" type="io.nop.api.core.beans.query.QueryBean"/>
    <arg name="selection" type="io.nop.api.core.beans.FieldSelectionBean" kind="FieldSelection"/>
    <arg name="svcCtx" type="io.nop.core.context.IServiceContext" kind="ServiceContext"/>
    <return type="PageBean&lt;io.nop.auth.dao.entity.NopAuthUser&gt;"/>
<!--LOC:[8:14:0:0]/nop/auth/model/NopAuthUser/NopAuthUser.xbiz-->
    <source>
        <c:import class="io.nop.auth.api.AuthApiConstants"/>
        <bo:DoFindPage query="${query}" selection="${selection}" xpl:lib="/nop/biz/xlib/bo.xlib">
            <filter>
                <eq name="status" value="${AuthApiConstants.USER_STATUS_ACTIVE}"/>
            </filter>
        </bo:DoFindPage>
    </source>
</query>
```

### 7. 为子表属性增加过滤条件

在xmeta中可以为子表属性配置`graphql:queryMethod`，它的值对应于GraphqlQLQueryMethod枚举类，包含findCount/findFirst/findList/findPage/findConnection等值。
其中findPage表示分页查询返回PageBean对象，而findConnection表示分页查询，返回GraphQLConnection对象。

> Connection是GraphQL Relay框架中的一个概念，具体介绍参见 [connection.md](../graphql/connection.md)


指定了`graphql:queryMethod`的prop支持分页查询条件和排序条件，具体类型对应于`GraphQLConnectionInput`。

```xml
<prop name="resourcesConnection" graphql:queryMethod="findConnection">
  <schema bizObjName="NopAuthResource" />

  <graphql:filter>
     <eq name="siteId" value="@prop-ref:siteId" />
  </graphql:filter>

  <graphql:orderBy>
     <field name="displayName" desc="false" />
  </graphql:orderBy>
</prop>
```

通过`graphql:filter`和`graphql:orderBy`可以指定子表查询的过滤条件和排序条件。在过滤条件中，通过`@prop-ref:{propName}`这种形式可以表示引用当前实体中的属性。
因此通过这种语法我们跳过ORM引擎，在任意两个实体之间实现关联查询。

如果ORM引擎已经定义了关联对象属性，则我们可以简化处理，使用`graphql:connectionProp`来指定对应的ORM属性，从中获取到关联条件定义来替代`graphql:filter`。

```xml
<prop name="resourcesConnection" graphql:queryMethod="findConnection" graphql:connectionProp="resources" />
```
