# Nop Platform Backend API Architecture

## 概述

Nop Platform采用分层架构设计，从上到下分为：表现层、服务层、数据访问层。每个层次都有清晰的职责边界，通过标准接口进行交互。这种架构设计实现了关注点分离，提高了系统的可维护性和可扩展性。

## 架构分层

### 1. 表现层 (Presentation Layer)

表现层负责接收外部请求，进行参数验证，调用服务层处理，并将结果返回给客户端。Nop平台支持多种表现层技术：

#### GraphQL API
- **实现**: `nop-graphql` 模块
- **入口**: GraphQL Engine根据BizModel定义自动生成GraphQL Schema
- **特点**:
  - 声明式API定义，通过`@BizQuery`、`@BizMutation`、`@BizAction`注解
  - 自动类型推导和Schema生成
  - 支持查询、变更、订阅三种操作
  - 内置分页、过滤、排序支持

```java
@BizModel("User")
public class UserBizModel extends CrudBizModel<NopAuthUser> {

    @BizQuery
    public User getUser(@Name("userId") String userId) {
        return dao().getEntityById(userId);
    }

    @BizMutation
    @Transactional
    public User createUser(@Name("user") User user) {
        return save(user);
    }
}
```

#### REST API
- **实现**: 基于Quarkus/Spring框架集成
- **特点**:
  - 自动REST接口生成（基于GraphQL接口）
  - 支持标准HTTP方法（GET、POST、PUT、DELETE）
  - JSON请求/响应格式
  - 自动参数绑定和验证

### 2. 服务层 (Service Layer)

服务层封装业务逻辑，是系统的核心层次。它位于表现层和数据访问层之间，负责：

#### BizModel (业务模型)
- **定义**: `nop-service-framework/nop-biz`
- **基类**: `CrudBizModel<T>`
- **职责**:
  - 封装业务逻辑
  - 协调多个实体操作
  - 实现事务边界
  - 权限验证和数据过滤

```java
@BizModel("Order")
public class OrderBizModel extends CrudBizModel<NopOrder> {

    @Inject
    private IOrderItemBizModel orderItemBizModel;

    @BizMutation
    @Transactional
    public Order createOrder(@Name("order") OrderData data) {
        // 1. 验证业务规则
        validateOrderData(data);

        // 2. 创建订单
        Order order = new Order();
        BeanTool.copyProperties(order, data);
        dao().saveEntity(order);

        // 3. 创建订单项
        for (OrderItemData itemData : data.getItems()) {
            OrderItem item = new OrderItem();
            BeanTool.copyProperties(item, itemData);
            item.setOrderId(order.getId());
            orderItemBizModel.saveOrderItem(item);
        }

        // 4. 更新库存
        updateInventory(data.getItems());

        return order;
    }
}
```

#### 核心特性

1. **注解驱动**
   - `@BizModel`: 标记业务模型
   - `@BizQuery`: 定义查询方法
   - `@BizMutation`: 定义变更方法
   - `@BizAction`: 定义动作方法

2. **内置CRUD**
   - `findPage()`: 分页查询
   - `findList()`: 列表查询
   - `findFirst()`: 查询单个对象
   - `findCount()`: 统计数量
   - `save()`: 创建/更新
   - `delete()`: 删除

3. **事务管理**
   - 声明式事务: `@Transactional`
   - 编程式事务: `txn(() -> {...})`
   - 支持事务传播级别

4. **权限控制**
   - 数据权限自动过滤
   - 方法级权限验证
   - 基于角色的访问控制

### 3. 数据访问层 (Data Access Layer)

数据访问层负责与数据库交互，提供统一的数据操作接口。

#### IEntityDao (实体DAO接口)
- **定义**: `nop-persistence/nop-dao`
- **职责**:
  - 基础CRUD操作
  - 实体查询
  - 批量操作
  - 关联查询

```java
public interface IEntityDao<T> {
    // 单实体操作
    T saveEntity(T entity);
    T updateEntity(T entity);
    void deleteEntity(T entity);
    T getEntityById(Serializable id);
    T loadEntityById(Serializable id);

    // 查询操作
    T findFirstByExample(T example);
    List<T> findAllByExample(T example);
    List<T> findAllByQuery(QueryBean query);
    PageBean<T> findPageByQuery(QueryBean query);
    int countByQuery(QueryBean query);

    // 批量操作
    List<T> batchGetEntitiesByIds(List<? extends Serializable> ids);
    void batchSaveEntities(List<T> entities);
    void batchDeleteEntities(List<T> entities);
}
```

#### IOrmEntityDao (ORM实体DAO)
- **定义**: `nop-persistence/nop-orm`
- **职责**:
  - ORM映射
  - 关联加载（懒加载、急加载）
  - 级联操作
  - 缓存管理

```java
public interface IOrmEntityDao<T extends IOrmEntity> extends IEntityDao<T> {
    // ORM特有操作
    T getEntityById(Serializable id, LoadMode loadMode);
    void loadProp(T entity, String propName);
    void loadProps(T entity, Collection<String> propNames);

    // 关联查询
    List<T> findListByProp(String propName, Object propValue);
    List<T> findListByQuery(IEntityDao<T> dao, QueryBean query);

    // 级联操作
    void cascadeSave(T entity);
    void cascadeDelete(T entity);
}
```

#### OrmTemplate (ORM模板)
- **定义**: `nop-persistence/nop-orm`
- **职责**:
  - 会话管理
  - 事务管理
  - SQL执行
  - 实体状态跟踪

```java
public interface IOrmTemplate {
    // 会话操作
    IOrmSession currentSession();
    IOrmSession openSession();
    void closeSession(IOrmSession session);

    // SQL执行
    int executeSql(String sql, Object... args);
    List<Map<String, Object>> queryForList(String sql, Object... args);
    Map<String, Object> queryForMap(String sql, Object... args);

    // 批量操作
    void executeBatch(String sql, List<Object[]> argsList);

    // 事务操作
    <T> T runInTransaction(ITransactionAction<T> action);
}
```

## 核心设计模式

### 1. 模板方法模式

`CrudBizModel`提供标准的CRUD操作模板，子类可以重写特定方法进行定制：

```java
public class MyUserBizModel extends CrudBizModel<NopAuthUser> {

    @Override
    protected void beforeSave(NopAuthUser entity, boolean isNew) {
        // 保存前验证
        if (isNew && emailExists(entity.getEmail())) {
            throw new NopException(ERR_EMAIL_EXISTS)
                .param("email", entity.getEmail());
        }
    }

    @Override
    protected void afterSave(NopAuthUser entity, boolean isNew) {
        // 保存后操作
        if (isNew) {
            sendWelcomeEmail(entity);
        }
    }
}
```

### 2. 策略模式

不同的查询策略可以根据场景选择：

```java
// 简单等值查询 - 使用Example
User example = new User();
example.setStatus(1);
List<User> users = dao().findAllByExample(example);

// 复杂条件查询 - 使用QueryBean
QueryBean query = new QueryBean();
query.setFilter(FilterBeans.and(
    FilterBeans.eq("status", 1),
    FilterBeans.contains("name", keyword)
));
List<User> users = dao().findAllByQuery(query);

// 高性能查询 - 使用原始SQL
List<User> users = ormTemplate.queryList(
    "SELECT * FROM user WHERE status = ? AND name LIKE ?",
    1, "%" + keyword + "%"
);
```

### 3. 依赖注入模式

通过IoC容器管理组件依赖关系：

```java
@BizModel("Order")
public class OrderBizModel extends CrudBizModel<NopOrder> {

    @Inject
    private IOrderItemBizModel orderItemBizModel;

    @Inject
    private IInventoryService inventoryService;

    @Inject
    private INotificationService notificationService;

    // 自动注入依赖，无需手动创建
}
```

### 4. 拦截器链模式

服务方法调用通过拦截器链处理：

1. **参数验证拦截器**: 验证输入参数
2. **权限验证拦截器**: 检查访问权限
3. **事务拦截器**: 管理事务边界
4. **日志拦截器**: 记录方法调用
5. **异常处理拦截器**: 统一异常处理
6. **结果转换拦截器**: 格式化返回结果

## 请求处理流程

### 1. GraphQL查询流程

```
1. 客户端发送GraphQL查询
   ↓
2. GraphQL Engine解析查询
   ↓
3. 根据Schema找到对应的BizModel和方法
   ↓
4. 拦截器链处理
   - 参数验证
   - 权限验证
   - 事务开启
   ↓
5. BizModel方法执行
   - 可能调用多个DAO
   - 可能调用其他BizModel
   ↓
6. 返回结果
   ↓
7. 拦截器后处理
   - 事务提交/回滚
   - 日志记录
   ↓
8. 格式化为GraphQL响应
   ↓
9. 返回给客户端
```

### 2. CRUD操作流程

```
1. 客户端调用save(data)
   ↓
2. CrudBizModel处理
   - 数据验证
   - 自动填充（ID、时间等）
   ↓
3. beforeSave(entity, isNew)
   - 子类可重写
   ↓
4. dao().saveEntity(entity)
   ↓
5. OrmTemplate执行SQL
   - INSERT或UPDATE
   ↓
6. afterSave(entity, isNew)
   - 子类可重写
   ↓
7. 返回实体对象
```

## 关键API层次关系

```
┌─────────────────────────────────────────┐
│       Presentation Layer               │
│  ┌─────────────┐  ┌─────────────┐   │
│  │ GraphQL API │  │ REST API    │   │
│  └──────┬──────┘  └──────┬──────┘   │
└─────────┼────────────────┼───────────┘
          │                │
          └───────┬────────┘
                  ↓
┌─────────────────────────────────────────┐
│       Service Layer                   │
│  ┌─────────────┐  ┌─────────────┐   │
│  │CrudBizModel │  │CustomBiz   │   │
│  └──────┬──────┘  │BizModel    │   │
└─────────┼──────────└──────┬──────┘   │
          │                  │           │
          └────────┬─────────┘
                   ↓
┌─────────────────────────────────────────┐
│    Data Access Layer                 │
│  ┌─────────────┐  ┌─────────────┐   │
│  │IEntityDao   │  │IOrmEntity  │   │
│  │             │  │Dao          │   │
│  └──────┬──────┘  └──────┬──────┘   │
└─────────┼────────────────┼───────────┘
          │                │
          └────────┬───────┘
                   ↓
          ┌────────────────┐
          │  OrmTemplate  │
          └───────┬──────┘
                  ↓
          ┌────────────────┐
          │  Database     │
          └────────────────┘
```

## 最佳实践

### 1. 服务层设计

**✅ 推荐**:
```java
@BizModel("User")
public class UserBizModel extends CrudBizModel<NopAuthUser> {

    @BizQuery
    public User getUser(String userId) {
        return dao().getEntityById(userId);
    }

    @BizMutation
    @Transactional
    public User createUser(User user) {
        validateUser(user);
        return save(user);
    }
}
```

**❌ 不推荐**:
```java
// 直接在BizModel中写SQL，违反分层原则
@BizQuery
public List<User> getUsersBySql() {
    return ormTemplate.queryList("SELECT * FROM user");
}
```

### 2. 事务边界

**✅ 推荐**: 事务边界在服务层方法上
```java
@BizMutation
@Transactional
public void transfer(String fromId, String toId, BigDecimal amount) {
    // 业务逻辑
}
```

**❌ 不推荐**: 在数据访问层使用事务
```java
// DAO不应该管理事务
@Transactional
public void saveEntity(T entity) {
    // ...
}
```

### 3. 异常处理

**✅ 推荐**: 使用统一异常类
```java
if (user == null) {
    throw new NopException(ERR_USER_NOT_FOUND)
        .param("userId", userId);
}
```

**❌ 不推荐**: 直接抛出RuntimeException
```java
if (user == null) {
    throw new RuntimeException("User not found: " + userId);
}
```

### 4. 数据验证

**✅ 推荐**: 在服务层验证
```java
@BizMutation
public User createUser(User user) {
    // 业务规则验证
    if (StringHelper.isEmpty(user.getName())) {
        throw new NopException(ERR_NAME_REQUIRED);
    }
    return save(user);
}
```

### 5. 使用内置方法

**✅ 推荐**: 使用CrudBizModel内置方法
```java
@BizQuery
public PageBean<User> findUsers(QueryBean query, int pageNo, int pageSize) {
    return findPage(query, pageNo, pageSize);
}
```

**❌ 不推荐**: 重复实现已有功能
```java
@BizQuery
public PageBean<User> findUsers(QueryBean query, int pageNo, int pageSize) {
    int offset = (pageNo - 1) * pageSize;
    int count = dao().countByQuery(query);
    List<User> list = dao().findAllByQuery(query);
    return new PageBean<>(list, count, pageNo, pageSize);
}
```

## 性能优化建议

### 1. 查询优化

- **使用字段选择**: 只查询需要的字段
- **合理使用分页**: 避免一次性加载大量数据
- **使用索引**: 确保常用查询字段有索引
- **避免N+1查询**: 使用批量查询或关联查询

```java
QueryBean query = new QueryBean();
query.setFilter(FilterBeans.eq("status", 1));

// ✅ 字段选择
query.setFields(List.of("id", "name", "email"));

// ✅ 分页
query.setOffset((pageNo - 1) * pageSize);
query.setLimit(pageSize);

List<User> users = dao().findAllByQuery(query);
```

### 2. 批量操作

- **使用批量方法**: 减少数据库交互次数
- **批量插入**: 一次插入多条记录

```java
// ✅ 批量保存
List<User> users = Arrays.asList(user1, user2, user3);
dao().batchSaveEntities(users);

// ❌ 循环保存
for (User user : users) {
    dao().saveEntity(user);
}
```

### 3. 缓存使用

- **配置二级缓存**: 对频繁访问但不常修改的数据
- **使用查询缓存**: 对重复查询启用缓存

```java
@Entity
@Cacheable
public class User implements IOrmEntity {
    // ...
}
```

## 扩展点

### 1. 自定义拦截器

如需扩展拦截器（例如 `IBizInterceptor`），请以仓库真实注册/装配方式为准；docs-for-ai 不提供带 Spring `@Component` 的示例代码。

### 2. 自定义查询策略

```java
public class CustomQueryStrategy implements IQueryStrategy {
    @Override
    public <T> List<T> query(IEntityDao<T> dao, QueryBean query) {
        // 自定义查询逻辑
        // ...
    }
}
```

### 3. 自定义事务管理

自定义事务管理能力请以仓库真实事务体系为准（参考事务管理指南），docs-for-ai 不提供带 Spring `@Component` 的示例代码。

## 相关文档

- [GraphQL服务开发指南](../03-development-guide/graphql-guide.md)
- [服务层开发指南](../03-development-guide/service-layer-development.md)
- [IEntityDao使用指南](../03-development-guide/entitydao-usage.md)
- [事务管理指南](../04-core-components/transaction-guide.md)
- [异常处理指南](../04-core-components/exception-guide.md)

## 总结

Nop Platform的API架构采用清晰的分层设计，从表现层到数据访问层，每层都有明确的职责：

1. **表现层**: GraphQL/REST API，负责请求接收和响应
2. **服务层**: BizModel，封装业务逻辑
3. **数据访问层**: IEntityDao/IOrmEntityDao，提供统一数据操作接口

通过模板方法、策略模式、依赖注入等设计模式，实现了高度的灵活性和可扩展性。遵循最佳实践，可以构建高性能、可维护的业务系统。
