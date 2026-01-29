# 服务层开发指南

## 概述

Nop平台服务层基于BizModel设计，提供了CrudBizModel基类用于快速实现CRUD操作，同时支持复杂业务逻辑的扩展。

## 核心组件

### 1. BizModel - 业务模型

**定义**：标记业务模型的注解，用于将Java类转换为GraphQL API
**位置**：`io.nop.biz.api.BizModel`
**作用**：
- 标记业务模型类
- 自动生成GraphQL类型和操作
- 支持业务逻辑封装

**使用示例**：
```java
@BizModel("User")
public class UserBizModel {
    // 业务方法
    @BizQuery
    public List<User> findUsersByStatus(@Name("status") int status, 
                     FieldSelection selection, 
                     IServiceContext context){
       ...
    }
}
```

- 前端可以通过graphql查询`{bizObjName}__{bizAction}`来调用服务函数，例如`User__findUsersByStatus(status:1){ name, deptName}`
- 也可以通过REST方式进行调用，`/r/{bizObjName}__{bizAction}?@selection={selection}`, 通过body传json map作为参数。例如 `/r/User__findUsersByStatus?@selection=name,deptName`, body为`{status:1}`, HTTP Method固定为POST。 
- selection对应于GraphQL中的字段选择，可以通过`selection.hasField(fieldName)`来判断是否前端要求返回这个字段的值
- context是单次服务函数执行时的上下文环境对象，通过它可以获取IUserContext等登陆用户信息

### 2. CrudBizModel - CRUD业务模型基类

**定义**：提供通用CRUD操作的抽象基类
**位置**：`io.nop.biz.crud.CrudBizModel`
**核心功能**：
- 内置CRUD操作实现
- 事务管理支持
- 数据权限控制
- 业务扩展点
- **框架中立**：不依赖特定框架，可运行在Spring/Quarkus/Solon等多种底层框架之上

**内置方法**：
- `findCount()`：查询记录总数
- `findPage()`：分页查询
- `findFirst()`：查询第一条记录
- `findList()`：列表查询
- `save()`：保存实体
- `update()`：更新实体
- `delete()`：删除实体
- `get()`: 根据id获取单条记录
- `batchGet()`: 根据ids批量获取记录列表
- `batchUpdate()`: 批量更新
- `batchDelete()`: 批量删除
- `batchModify()`: 批量增删改
- `save_update()`: 有id就修改，否则新增
- `updateByQuery()`: 更新满足条件的记录
- `deleteByQuery()`: 删除满足条件的记录
- `copyForNew()`: 复制新建
- `addManyToManyRelations()/updateManyToManyRelations()/removeManyToManyRelations()`: 管理多对多关联

**扩展点**：
- `defaultPrepareSave()`：保存前处理
- `defaultPrepareQuery()`：查询前处理
- `defaultPrepareUpdate()`：更新前处理
- `defaultPrepareDelete()`：删除前处理
- `checkDataAuth()`：数据权限检查
- `afterSave()`：保存后处理

**派生类中可用的帮助函数**：
- `getEntity()`: 相比于`dao().getEntity()`增加了数据权限检查
- `requireEntity()`:  getEntity之后验证返回实体非空
- `doSave/doUpdate/doFindPage/doFindFirst/doFindList`: save等函数的内部实现，`save相当于doSave(data,this::defaultPrepareSave)`.
- `buildEntityDataForSave()/buildEntityDaoForUpdate()`: 根据XMeta配置验证Map数据合法，并自动进行类型转换
- `copyToEntity`: 将Map数据拷贝到实体上，支持复杂主子表结构一次性更新

## 开发流程

### 1. 继承CrudBizModel

**示例**：
```java
@BizModel
public class UserBizModel extends CrudBizModel<User> {
    public UserBizModel() {
        setEntityName(User.class.getName());
    }
    
    // 业务方法
}
```

### 2. 注入业务组件

**示例**：
```java
@Inject
protected IPasswordEncoder passwordEncoder;

```

### 3. 实现业务方法

**示例**：
```java
@BizQuery
public User getUserByOpenId(@Name("openId") String openId, FieldSelection selection, IServiceContext context) {
    // 使用Example查询
    QueryBean query = new QueryBean();
    query.addFilter(FilterBeans.eq(User.PROP_ID_openId,openId));
    return findFirst(example,selection, context);
}

@BizMutation
public void resetUserPassword(@Name("userId") String userId, @Name("newPassword") String newPassword, IServiceContext context) {
    User user = this.requireEntity(userId，"resetUserPassword", context);
    user.setPassword(passwordEncoder.encode(newPassword));
    dao().saveEntity(user);
}
```

### 4. 重写扩展点

**示例**：
```java
@Override
protected void defaultPrepareSave(User user) {
    passwordPolicy.checkPassword(user.getPassword());
    user.setPassword(passwordEncoder.encode(user.getPassword()));
    
    user.setId(userIdGenerator.generateUserId());
    user.setStatus(1);
}
```


## 服务扩展机制

### 1. 通过@BizLoader扩展返回结果

**场景**：在不修改原有服务函数代码的情况下，为返回结果对象增加额外的字段

**实现方式**：
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

**关键特性**：
- 无需从原有BizModel类继承
- `autoCreateField=true`：自动为对外暴露的类型增加字段
- `@LazyLoad`：延迟加载，前台明确请求时才返回，确保接口兼容性
- 完全不修改原有服务函数代码

### 2. 扩展输入请求对象

**场景**：修改服务函数的输入参数，增加更多请求信息

**实现方式**：
```java
@BizModel("LoginApi")
public class LoginApiBizModelDelta {
    @Inject
    LoginApiBizModel loginApiBizModel;

    @BizMutation("login")
    @Auth(publicAccess = true)
    @Priority(NORMAL_PRIORITY - 100)
    public CompletionStage<LoginResult> loginAsync(
        @RequestBean LoginRequestEx request, IServiceContext context) {
        request.setAttr("a","123");
        return loginApiBizModel.loginAsync(request, context);
    }
}
```

**关键特性**：
- 通过`@Inject`引入原有服务实现
- 通过`@Priority`注解控制函数优先级（值越小优先级越高）
- 多个同名函数会根据优先级选择实现
- 同名函数优先级相同时会抛出异常

### 3. 通过XBiz模型实现扩展

**场景**：通过配置文件实现服务函数，支持无代码开发

**实现方式**：
```xml
<!-- /_delta/default/nop/auth/model/LoginApi/LoginApi.xbiz -->
<biz x:schema="/nop/schema/biz/xbiz.xdef" xmlns:x="/nop/schema/xdsl.xdef"
     x:extends="super" xmlns:bo="bo" xmlns:c="c">

    <actions>
        <query name="myMethod" >
            <arg name="msg" type="String" optional="true" />
            <return type="String" />
            <source>
                return "hello:" + msg;
            </source>
        </query>
    </actions>
</biz>
```

**关键特性**：
- xbiz文件路径：`/{moduleId}/model/{bizObjName}/{bizObjName}.xbiz`
- XBiz模型优先级最高，会覆盖Java中的同名服务函数
- 支持从高代码到低代码再到无代码的平滑过渡
- 配合可视化设计器可实现完全无代码开发

### 4. Header作为扩展信道

**场景**：在服务函数间传递跨系统的扩展数据

**实现方式**：
```java
@BizModel("LoginApi")
public class LoginApiBizModel implements ILoginSpi {

    @BizMutation("login")
    @Auth(publicAccess = true)
    public CompletionStage<LoginResult> loginAsync(
           @RequestBean LoginRequest request, IServiceContext context) {
        String header = (String)context.getRequestHeader("nop-tenant");
        context.setResponseHeader("x-xxx",value);
        ...
    }
}
```

**关键特性**：
- headers可以在不同的运行时环境中映射到不同实现
- gRPC：映射为gRPC消息的headers
- REST：映射为HTTP协议的headers
- Kafka：映射为Kafka消息的headers
- 采用`data + ext_data`配对设计，确保在任何局部都可以加入扩展信息

## 业务方法注解

### 1. 查询方法

**@BizQuery**：标记查询方法

**示例**：
```java
@BizQuery
public List<User> findActiveUsers(IServiceContext context, FieldSelection selection, IServiceContext context) {
    // 使用QueryBean查询
    QueryBean query = new QueryBean();
    query.setFilter(FilterBeans.eq("status", 1));
    return doFindList(query, this::invokeDefaultPrepareQuery, selection, context);
}
```

**注意**： 一般不直接调用到`dao().findAllByQuery()`, 而是调用CrudBizModel基类中的doXX函数，这些函数会考虑数据权限问题，并触发内置回调函数。

### 2. 变更方法

**@BizMutation**：标记变更方法（新增、更新、删除）

**示例**：
```java
@BizMutation
public void approveUser(@Name("userId") String userid,
        FieldSelection selection, IServiceContext context) {
    ...
}
```

- 对于select，context之外的参数，需要使用`@Name`注解来说明参数名



## 最佳实践

1. **优先使用内置方法**：对于CRUD操作，基本都可以使用内置方法实现，不需要createUser，saveUser这种函数。
2. **自动开启事务**：`@BizMutation`会自动开启数据库事务，而`@BizQuery`是无副作用的只读操作，不需要额外使用`@Transactional`注解
3. **业务逻辑封装**：避免一个函数包含太多内容，拆分成多个子函数。
4. **数据权限控制**：实现`checkDataAuth()`方法，进行数据权限控制。内置的doXXX等函数都自动执行了数据权限校验。
5. **异常处理**：统一使用NopException抛出业务异常，提供清晰的错误信息
6. **审计字段**：createdAt,createdBy,updateAt,updatedBy等审计字段由系统自动设置，不需要显式设置

## 注意事项

1. **一般不要在BizModel中直接使用dao()方法，尽量使用CrudBizModel内置的方法**
2. **不要在BizModel中手动管理事务，使用@BizMultation注解**
3. **不要在BizModel中手动处理异常，抛出NopException即可**

## 常见问题

### Q1: 如何在BizModel中调用其他BizModel的方法？

**答案**: 使用IBizObjectManager获取其他业务对象

```java
@BizModel("User")
public class UserBizModel extends CrudBizModel<User> {

    @Inject
    protected IBizObjectManager bizObjectManager;

    @BizQuery
    public List<Order> getUserOrders(@Name("userId") String userId，
                FieldSelection selection， IServiceContext context) {
         IBizObject orderObj = bizObjectManager.getBizObject("Order");           
        return (List<Order>) orderObj.invoke("findOrdersByUser", Map.of("userId",userId), selection, context);
    }
}
```

如果需要复用，可以定义一个接口，然后在OrderBizModel上实现这个接口，这样就可以注入这个接口来使用了。注意，一般不要直接注入BizModel对象。

```java
interface IOrderBiz{
    List<User> findOrdersByUser(String userId, IServiceContext context);
}

@BizModel("User")
public class UserBizModel extends CrudBizModel<User> {

    @Inject
    protected IOrderBiz orderBiz;

    @BizQuery
    public List<Order> getUserOrders(@Name("userId") String userId，
                FieldSelection selection， IServiceContext context) {
        return orderBiz.findOrdersByUser(userId,context);
    }
}
```

### Q2: 如何在BizModel中进行批量操作？

**答案**: 使用DAO的批量方法：

```java
@BizMutation
public void batchUpdateStatus(@Name("userIds") List<String> userIds,
                             @Name("status") int status, IServiceContext context) {
        List<User> users = dao().batchGetEntitiesByIds(userIds);
        for (User user : users) {
            this.checkDataAuthForEntity(user,context);
            user.setStatus(status);
        }
        dao().batchSaveEntities(users);
}
```

### Q3：关联查询

底层使用的是NopORM，它自动支持关联属性查询，比如

```java
QueryBean query = new QueryBean();
query.addFilter(FilterBeans.eq("dept.parent.name"),"ABC");
```

类似于JPA，关联属性对象会自动转换为join查询语句。

### Q4: 国际化如何处理
不用考虑国际化问题，抛出异常码会自动在框架层进行翻译。

### Q5: 如何避免N+1查询问题
NopORM中所有关联属性都是懒加载，调用IEntityDao上的batchLoadProps等函数来批量预加载，内部实现原理类似GraphQL的DataLoader

```java
dao.batchLoadProps(entities, List.of("dept.users","manager.dept"));
```

## 总结

BizModel是Nop平台服务层的核心组件，它封装了业务逻辑，为GraphQL和REST API提供服务。

**关键要点**：

1. **继承CrudBizModel**: 自动获得完整的CRUD功能
2. **使用注解定义服务**: @BizModel、@BizQuery、@BizMutation
3. **依赖注入**: 使用@Inject注入其他服务
4. **事务管理**: 使用@BizMutation自动开启事务
5. **异常处理**: 抛出NopException，框架自动处理

遵循这些最佳实践，可以构建清晰、可维护的服务层代码。

## 相关文档

- [IEntityDao使用指南](../dao/entitydao-usage.md)
- [QueryBean使用指南](../dao/querybean-guide.md)： 分页/排序/过滤条件都通过QueryBean指定
- [事务管理指南](../core/transaction-guide.md)：一般不需要显式使用事务，只在需要非常细粒度事务控制时才参考这个问题。否则就是整个服务函数一个事务，抛出异常后会自动回滚。
- [异常处理指南](../core/exception-guide.md)：只使用通用的NopException异常类，不要引入自定义异常类
- [GraphQL服务开发指南](../api/graphql-guide.md)

