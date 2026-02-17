# 服务层开发指南

## 概述

Nop平台服务层基于BizModel设计，提供了CrudBizModel基类用于快速实现CRUD操作，同时支持复杂业务逻辑的扩展。

> **BizModel 编写核心规范**：请参考 [BizModel 编写指南](./bizmodel-guide.md)，其中包含完整的编写规范、参数/返回类型约定、数据访问方式等核心内容。本文档重点介绍服务层架构和扩展机制。

## 重要说明

### 模型驱动架构（Model-Driven Architecture）

**Nop 平台使用模型驱动架构，无需手动编写简单的 CRUD 代码！**

#### 核心特性

1. **内置 CRUD 操作**：继承 CrudBizModel 后，已经自动内置了完整的 CRUD 操作
2. **参数使用 Map/QueryBean**：不使用自定义 DTO 对象，直接使用 Map 和 QueryBean
3. **元数据驱动**：通过 xmeta 元数据模型定义数据结构和验证规则
4. **字段级别自适应**：修改模型后自动适应，无需重新生成代码

#### 内置 CRUD 方法

继承 `CrudBizModel` 后，自动拥有完整的 CRUD 操作能力。所有可用方法定义在 ICrudBiz 接口中（见下方）。

**主要方法分类**：

- **查询操作**：`findCount()`, `findPage()`, `findFirst()`, `findList()`, `get()`, `batchGet()`, `asDict()`
- **新增操作**：`save()`, `saveOrUpdate()`, `copyForNew()`
- **修改操作**：`update()`, `batchUpdate()`, `updateByQuery()`
- **删除操作**：`delete()`, `batchDelete()`, `deleteByQuery()`
- **批量增删改**：`batchModify()`
- **多对多关联**：`addManyToManyRelations()`, `removeManyToManyRelations()`, `updateManyToManyRelations()`
- **树形结构**：`findRoots()`, `findTreeEntityPage()`, `findTreeEntityList()`, `findListForTree()`, `findPageForTree()`
- **逻辑删除**：`deleted_findPage()`, `deleted_get()`, `recoverDeleted()`

**调用方式**：

- **GraphQL**: `{bizObjName}__{methodName}(...)`，例如 `User__findPage(query: {limit: 20})`
- **REST**: `POST /r/{bizObjName}__{methodName}`，body 传 JSON 参数，例如 `POST /r/User__findPage`

#### 无需编程的场景

以下场景**不需要手动编写代码**：

| 场景 | 解决方案 |
|------|---------|
| 简单 CRUD 操作 | 直接调用 `ICrudBiz` 接口方法 |
| 字段扩展 | 修改 xmeta 模型即可，自动生效 |
| 数据验证 | 在 xmeta 中定义验证规则，自动生效 |
| 权限控制 | 在 xmeta 中定义数据权限，自动生效 |

#### 需要编程的场景

以下场景**需要手动编写代码**：

| 场景 | 解决方案 |
|------|---------|
| 复杂业务逻辑 | 重写扩展点（defaultPrepareSave, defaultPrepareQuery 等） |
| 复杂查询 | 自定义 BizQuery 方法，使用 Map/QueryBean 参数 |
| 跨模块调用 | 使用 IBizObjectManager 或定义接口 |
| 事务监听器 | 使用 ITransactionTemplate |

---

## 核心组件

### 1. BizModel - 业务模型

**定义**：标记业务模型的注解，用于将Java类转换为GraphQL API
**位置**：`io.nop.api.core.annotations.biz.BizModel`
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
                     FieldSelectionBean selection,
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
- 内置CRUD操作实现（通过 ICrudBiz 接口提供所有标准方法）
- 业务扩展点
- **框架中立**：不依赖特定框架，可运行在Spring/Quarkus/Solon等多种底层框架之上

#### ICrudBiz 接口定义

```java
interface ICrudBiz<T> {
  // 查询操作
  long findCount(@Optional @Name("query") QueryBean query, IServiceContext context);

  PageBean<T> findPage(@Optional @Name("query") QueryBean query, FieldSelectionBean selection, IServiceContext context);

  T findFirst(@Optional @Name("query") QueryBean query, FieldSelectionBean selection, IServiceContext context);

  List<T> findList(@Optional @Name("query") QueryBean query, FieldSelectionBean selection, IServiceContext context);

  T get(@Name("id") String id, @Optional @Name("ignoreUnknown") boolean ignoreUnknown, IServiceContext context);

  List<T> batchGet(@Name("ids") Collection<String> ids, @Optional @Name("ignoreUnknown") boolean ignoreUnknown, IServiceContext context);

  DictBean asDict(IServiceContext context);

  // 新增操作
  T save(@Name("data") Map<String, Object> data, IServiceContext context);

  T saveOrUpdate(@Name("data") Map<String, Object> data, IServiceContext context);

  T copyForNew(@Name("data") Map<String, Object> data, IServiceContext context);

  // 修改操作
  T update(@Name("data") Map<String, Object> data, IServiceContext context);

  void batchUpdate(@Name("ids") Set<String> ids, @Name("data") Map<String, Object> data, @Optional @Name("ignoreUnknown") boolean ignoreUnknown, IServiceContext context);

  int updateByQuery(@Name("query") QueryBean query, @Name("data") Map<String, Object> data, IServiceContext context);

  // 删除操作
  boolean delete(@Name("id") String id, IServiceContext context);

  Set<String> batchDelete(@Name("ids") Set<String> ids, IServiceContext context);

  int deleteByQuery(@Name("query") QueryBean query, IServiceContext context);

  // 批量增删改
  void batchModify(@Name("data") List<Map<String, Object>> data, @Optional @Name("common") Map<String, Object> common, @Optional @Name("delIds") Set<String> delIds, IServiceContext context);

  // 多对多关联
  void addManyToManyRelations(@Name("id") String id, @Name("propName") String propName, @Name("relValues") Collection<String> relValues, @Optional @Name("filter") TreeBean filter, IServiceContext context);

  void removeManyToManyRelations(@Name("id") String id, @Name("propName") String propName, @Name("relValues") Collection<String> relValues, @Optional @Name("filter") TreeBean filter, IServiceContext context);

  void updateManyToManyRelations(@Name("id") String id, @Name("propName") String propName, @Name("relValues") Collection<String> relValues, @Optional @Name("filter") TreeBean filter, IServiceContext context);

  // 树形结构
  List<T> findRoots(@Optional @Name("query") QueryBean query, FieldSelectionBean selection, IServiceContext context);

  PageBean<StdTreeEntity> findTreeEntityPage(@Optional @Name("query") QueryBean query, FieldSelectionBean selection, IServiceContext context);

  List<StdTreeEntity> findTreeEntityList(@Optional @Name("query") QueryBean query, FieldSelectionBean selection, IServiceContext context);

  List<T> findListForTree(@Optional @Name("query") QueryBean query, FieldSelectionBean selection, IServiceContext context);

  PageBean<T> findPageForTree(@Optional @Name("query") QueryBean query, FieldSelectionBean selection, IServiceContext context);

  // 逻辑删除
  PageBean<T> deleted_findPage(@Optional @Name("query") QueryBean query, FieldSelectionBean selection, IServiceContext context);

  T deleted_get(@Name("id") String id, @Optional @Name("ignoreUnknown") boolean ignoreUnknown, IServiceContext context);

  T recoverDeleted(@Name("id") String id, IServiceContext context);

  // 辅助方法
  T newEntity();

  IObjMeta getObjMeta();

  String getBizObjName();

  // 实体操作
  void deleteEntity(@Name("entity") T entity, IServiceContext context);

  void saveEntity(@Name("entity") T entity, IServiceContext context);

  void updateEntity(@Name("entity") T entity, IServiceContext context);

  void assignToEntity(@Name("entity") T entity, @Name("data") Map<String, Object> data, IServiceContext context);

  T buildEntityForSave(@Name("data") Map<String, Object> data, @Name("action") String action, IServiceContext context);

  void checkAllowAccess(@Name("entity") T entity, @Name("action") String action, IServiceContext context);
}
```

#### 扩展点

- `defaultPrepareSave(EntityData<T> entityData, IServiceContext context)`：保存前处理
- `defaultPrepareQuery(QueryBean query, IServiceContext context)`：查询前处理
- `defaultPrepareUpdate(EntityData<T> entityData, IServiceContext context)`：更新前处理
- `defaultPrepareDelete(T entity, IServiceContext context)`：删除前处理
- `afterEntityChange(T entity, IServiceContext context, String action)`：实体变更后处理

#### 帮助函数

- `getEntity(id, action, ignoreUnknown, includeLogicalDeleted, context)`: 相比于`dao().getEntity()`增加了数据权限检查和元数据过滤
- `requireEntity(id, action, context)`: getEntity之后验证返回实体非空
- `doSave/doUpdate/doFindPage/doFindFirst/doFindList`: save等函数的内部实现，`save相当于doSave(data,null,this::invokeDefaultPrepareSave,context)`.
- `buildEntityDataForSave()/buildEntityDataForUpdate()`: 根据XMeta配置验证Map数据合法，并自动进行类型转换
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
public User getUserByOpenId(@Name("openId") String openId, FieldSelectionBean selection, IServiceContext context) {
    QueryBean query = new QueryBean();
    query.setFilter(FilterBeans.eq(User.PROP_ID_openId, openId));
    return doFindFirst(query, this::invokeDefaultPrepareQuery, selection, context);
}

@BizMutation
public void resetUserPassword(@Name("userId") String userId, @Name("newPassword") String newPassword, IServiceContext context) {
    User user = this.requireEntity(userId);
    user.setPassword(passwordEncoder.encode(newPassword));
    // 修改实体会自动保存，不需要手动调用dao.updateEntity。但是如果修改了有可能导致数据权限变化或者唯一键冲突的属性，则需要调用updateEntity(entity,context);
    // updateEntity(user,context);
}
```

### 4. 重写扩展点

**示例**：
```java
@Override
protected void defaultPrepareSave(EntityData<User> entityData, IServiceContext context) {
    User user = entityData.getEntity();
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
public List<User> findActiveUsers(FieldSelectionBean selection, IServiceContext context) {
    // 使用QueryBean查询
    QueryBean query = new QueryBean();
    query.setFilter(FilterBeans.eq("status", 1));
    return doFindList(query, this::invokeDefaultPrepareQuery, selection, context);
}
```

**注意**： 一般不直接调用到`dao().findAllByQuery()`, 而是调用CrudBizModel基类中的doXX函数，这些函数会考虑数据权限问题，并触发内置回调函数。

**QueryBean 分页处理**：
- ✅ 使用内置方法（如 `findPage()`），框架会自动处理 offset/limit
- ❌ 无需在 QueryBean 中手动设置 offset/limit
- ❌ 无需额外传递 pageNo/pageSize 参数给 doXX 函数

### 2. 变更方法

**@BizMutation**：标记变更方法（新增、更新、删除）

**示例**：
```java
@BizMutation
public void approveUser(@Name("userId") String userId,
        IServiceContext context) {
    ...
}
```

- 对于context之外的参数，需要使用`@Name`注解来说明参数名



## 最佳实践

1. **优先使用内置方法**：对于CRUD操作，基本都可以使用内置方法实现，不需要createUser，saveUser这种函数。
2. **自动开启事务**：`@BizMutation`会自动开启数据库事务，**无需额外使用** `@Transactional` 注解或 `txn()` 方法。而`@BizQuery`是无副作用的只读操作，不需要额外使用事务管理。
3. **业务逻辑封装**：避免一个函数包含太多内容，拆分成多个子函数。
4. **数据权限控制**：实现`checkDataAuth()`方法，进行数据权限控制。内置的doXXX等函数都自动执行了数据权限校验。
5. **异常处理**：统一使用NopException抛出业务异常，提供清晰的错误信息
6. **审计字段**：createdAt,createdBy,updateAt,updatedBy等审计字段由系统自动设置，不需要显式设置

## 注意事项

1. **使用CrudBizModel内置方法而非直接调用dao()**
   - ✅ **推荐**：使用 `getEntity()`, `requireEntity()`, `doFindList()`, `doFindPage()`, `doSave()`, `doUpdate()`, `doDelete()` 等父类方法
   - ❌ **避免**：直接调用 `dao().getEntityById()`, `dao().saveEntity()`, `dao().deleteEntity()` 等
   - **原因**：CrudBizModel 的内置方法会自动应用数据权限检查、触发内置回调函数（如 `defaultPrepareQuery`、`defaultPrepareSave` 等）

2. **事务管理**：`@BizMutation` 已自动开启事务，无需额外使用 `@Transactional` 或 `txn()`。只有在需要非常细粒度事务控制时才使用编程式事务。

3. **异常处理**：抛出NopException即可，框架会自动处理

## 常见问题

### Q1: 如何在BizModel中调用其他BizModel的方法？

**BizModel相互引用规范**：
- ❌ 禁止直接注入BizModel类
- ✅ 必须使用接口（ICrudBiz或自定义接口）
- 接口可通过Java类implements或xbiz配置实现

```java
interface IOrderBiz extends ICrudBiz<Order>{
    List<Order> findOrdersByUser(@Name("userId") String userId, FieldSelectionBean selection, IServiceContext context);
}

@BizModel("Order")
public class OrderBizModel extends CrudBizModel<Order> implements IOrderBiz {
    // 实现接口方法
    @Override
    @BizQuery
    public List<Order> findOrdersByUser(@Name("userId") String userId, FieldSelectionBean selection, IServiceContext context) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("userId", userId));
        return doFindList(query, this::invokeDefaultPrepareQuery, selection, context);
    }
}

@BizModel("User")
public class UserBizModel extends CrudBizModel<User> {

    @Inject
    protected IOrderBiz orderBiz;

    @BizQuery
    public List<Order> getUserOrders(@Name("userId") String userId,
                FieldSelectionBean selection, IServiceContext context) {
        return orderBiz.findOrdersByUser(userId, context);
    }
}
```



### Q2: 如何在BizModel中进行批量操作？

**答案**: 使用内置的批量操作方法：

```java
@BizMutation
public void batchUpdateStatus(@Name("userIds") List<String> userIds,
                             @Name("status") int status, IServiceContext context) {
    // ✅ 使用内置的 batchUpdate 方法，自动处理数据权限
    Map<String, Object> data = new HashMap<>();
    data.put("status", status);
    batchUpdate(new HashSet<>(userIds), data, false, context);
}
```

> **更多 BizModel 编写规范**：参见 [BizModel 编写指南](./bizmodel-guide.md)

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
## 相关文档

- [BizModel 编写指南](./bizmodel-guide.md) - BizModel 核心编写规范
- [CRUD 开发指南](./crud-development.md) - CRUD 扩展点和 XMeta 配置
- [DDD 在 Nop 中的实践](./ddd-in-nop.md) - 实体与 BizModel 职责划分
- [DTO 编码规范](../04-core-components/enum-dto-standards.md) - @DataBean 注解规范
