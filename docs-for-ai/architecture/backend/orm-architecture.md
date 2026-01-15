# Nop Platform ORM Engine Architecture

## 概述

Nop Platform的ORM引擎是一个基于模型驱动设计的轻量级、高性能的对象关系映射框架。它不依赖Hibernate等第三方ORM框架，而是从零开始实现，遵循可逆计算原理，提供了灵活、高效的数据库操作能力。

## 核心设计理念

### 1. 模型驱动 (Model-Driven)

ORM引擎的核心是实体元模型（Entity Meta Model），所有操作都基于元模型进行：

```java
// 实体元模型
IEntityModel userModel = OrmMetas.instance().getEntityModel("io.nop.auth.entity.NopAuthUser");

// 获取属性信息
IPropModel prop = userModel.getProp("email");

// 获取表名
String tableName = userModel.getTableName();
```

### 2. 声明式映射

通过注解或配置文件声明映射关系：

```java
@Entity(table = "nop_auth_user")
public class NopAuthUser implements IOrmEntity {
    @Id
    @Column(name = "user_id")
    private String userId;

    @Column(name = "user_name")
    private String userName;

    @ManyToOne
    @JoinColumn(name = "dept_id")
    private NopAuthDept dept;

    @OneToMany(mappedBy = "user")
    private List<NopAuthRole> roles;
}
```

### 3. 约定优于配置

采用智能约定减少配置：
- 表名: 类名转蛇形（NopAuthUser → nop_auth_user）
- 列名: 属性名转蛇形（userName → user_name）
- 主键: id或实体名+Id
- 关联: 通过属性名自动推断关联关系

## 核心组件架构

### 1. 元模型层 (Meta Model Layer)

#### IEntityModel (实体模型)
- **定义**: `nop-kernel/nop-api-core`
- **职责**: 描述实体的完整元信息

```java
public interface IEntityModel {
    // 基础信息
    String getName();              // 实体名称
    String getClassName();        // Java类名
    String getTableName();        // 数据库表名

    // 属性信息
    List<IPropModel> getProps();
    IPropModel getProp(String propName);

    // 主键信息
    IPropModel getIdProp();

    // 关联信息
    List<IRelationModel> getRelations();
    IRelationModel getRelation(String relationName);

    // 索引信息
    List<IIndexModel> getIndexes();
}
```

#### IPropModel (属性模型)
- **职责**: 描述属性的元信息

```java
public interface IPropModel {
    // 基础信息
    String getName();              // 属性名
    String getColumnName();        // 数据库列名
    Class<?> getPropType();       // Java类型

    // 类型信息
    IStdDataType getStdDataType(); // 标准数据类型
    int getPrecision();           // 精度
    int getScale();              // 小数位

    // 约束信息
    boolean isMandatory();       // 是否必填
    boolean isPrimaryKey();       // 是否主键
    boolean isUnique();          // 是否唯一
    Object getDefaultValue();     // 默认值

    // 关联信息
    IRelationModel getRelation(); // 关联关系
}
```

#### IRelationModel (关联模型)
- **职责**: 描述实体间的关系

```java
public interface IRelationModel {
    // 关系类型
    RelationType getRelationType(); // ONE_TO_ONE, ONE_TO_MANY, MANY_TO_ONE, MANY_TO_MANY

    // 关联信息
    String getJoinColumn();        // 连接列
    String getJoinTable();         // 连接表
    String getMappedBy();         // 映射属性

    // 反向关联
    IRelationModel getInverseRelation();

    // 级联操作
    CascadeType[] getCascadeTypes();
}
```

### 2. 会话层 (Session Layer)

#### IOrmSession (ORM会话)
- **定义**: `nop-persistence/nop-orm`
- **职责**: 管理实体对象的生命周期和持久化操作

```java
public interface IOrmSession {
    // 实体操作
    <T extends IOrmEntity> T save(T entity);
    <T extends IOrmEntity> T update(T entity);
    <T extends IOrmEntity> void delete(T entity);
    <T extends IOrmEntity> T load(Class<T> entityClass, Serializable id);
    <T extends IOrmEntity> T get(Class<T> entityClass, Serializable id);

    // 查询操作
    <T extends IOrmEntity> T findFirstByExample(T example);
    <T extends IOrmEntity> List<T> findAllByExample(T example);
    <T extends IOrmEntity> List<T> findAllByQuery(IEntityDao<T> dao, QueryBean query);

    // 关联加载
    <T extends IOrmEntity> void loadProp(T entity, String propName);
    <T extends IOrmEntity> void loadProps(T entity, Collection<String> propNames);

    // 刷新和清空
    void refresh(IOrmEntity entity);
    void clear();

    // 状态管理
    EntityState getEntityState(IOrmEntity entity);
}
```

### 3. 持久化层 (Persistence Layer)

#### OrmTemplate (ORM模板)
- **定义**: `nop-persistence/nop-orm`
- **职责**: 管理会话和事务，执行SQL

```java
public interface IOrmTemplate {
    // 会话管理
    IOrmSession currentSession();
    IOrmSession openSession();
    void closeSession(IOrmSession session);

    // SQL执行
    int executeSql(String sql, Object... args);
    int executeSql(String sql, Map<String, Object> args);
    List<Map<String, Object>> queryForList(String sql, Object... args);
    Map<String, Object> queryForMap(String sql, Object... args);

    // 批量操作
    void executeBatch(String sql, List<Object[]> argsList);

    // 事务操作
    <T> T runInTransaction(ITransactionAction<T> action);
    <T> T runInTransaction(String txnGroup, Propagation propagation, ITransactionAction<T> action);

    // 数据库操作
    IDialect getDialect();
    IDataSource getDataSource();
}
```

#### IDialect (数据库方言)
- **职责**: 处理不同数据库的差异

```java
public interface IDialect {
    // 数据库类型
    String getDatabaseType();

    // SQL生成
    String getLimitSql(String sql, int offset, int limit);
    String getSequenceSql(String sequenceName);

    // 类型映射
    String getTypeName(IStdDataType dataType, int precision, int scale);
    Object convertValue(Object value, Class<?> targetType);

    // 函数支持
    boolean supportsFunction(String functionName);
    String getFunctionSql(String functionName, Object... args);
}
```

### 4. 缓存层 (Cache Layer)

#### 二级缓存 (L2 Cache)
- **定义**: `nop-persistence/nop-orm`
- **职责**: 缓存实体对象，减少数据库访问

```java
public interface IEntityCache {
    // 缓存操作
    void put(String cacheKey, Object value);
    Object get(String cacheKey);
    void remove(String cacheKey);
    void clear();

    // 缓存配置
    void setCacheConfig(CacheConfig config);

    // 统计信息
    CacheStats getStats();
}
```

#### 查询缓存 (Query Cache)
- **职责**: 缓存查询结果

```java
public interface IQueryCache {
    // 查询缓存
    void putQuery(String queryKey, List<Object> params, Object result);
    Object getQuery(String queryKey, List<Object> params);

    // 缓存失效
    void invalidateQuery(String queryKey);
    void clear();
}
```

## 实体状态管理

ORM引擎跟踪实体对象的状态，采用脏检查机制：

### 实体状态

| 状态 | 说明 | 操作 |
|------|------|------|
| TRANSIENT | 临时状态，未关联会话 | save() → PERSISTENT |
| PERSISTENT | 持久化状态，关联会话 | 修改 → DIRTY |
| DETACHED | 游离状态，已关闭会话 | merge() → PERSISTENT |
| REMOVED | 删除状态 | remove() |

### 脏检查 (Dirty Checking)

```java
// 加载实体
User user = session.get(User.class, "1");

// 修改属性
user.setName("New Name");

// ORM引擎检测到变更
session.flush(); // 自动执行UPDATE
```

### 快照机制 (Snapshot)

```java
// 加载时创建快照
User user = session.get(User.class, "1");
Snapshot snapshot = session.createSnapshot(user);

// 修改后对比
user.setName("New Name");
List<PropChange> changes = session.detectChanges(user, snapshot);

// 只更新变更的列
UPDATE user SET name = ? WHERE user_id = ?
```

## SQL生成策略

### 1. Insert语句生成

```java
// 基于元模型生成
INSERT INTO nop_auth_user (
    user_id, user_name, email, create_time
) VALUES (
    ?, ?, ?, ?
)
```

特性：
- 只插入有值的列
- 自动填充主键（如果配置）
- 处理默认值
- 支持批量插入

### 2. Update语句生成

```java
// 基于脏检查生成
UPDATE nop_auth_user
SET user_name = ?, email = ?, update_time = ?
WHERE user_id = ?
```

特性：
- 只更新变更的列
- 支持乐观锁
- 支持条件更新
- 支持批量更新

### 3. Delete语句生成

```java
// 基于主键生成
DELETE FROM nop_auth_user
WHERE user_id = ?
```

特性：
- 支持级联删除
- 支持软删除
- 支持批量删除
- 支持条件删除

### 4. Select语句生成

```java
// 基于QueryBean生成
SELECT
    u.user_id, u.user_name, u.email,
    d.dept_id, d.dept_name
FROM nop_auth_user u
LEFT JOIN nop_auth_dept d ON u.dept_id = d.dept_id
WHERE u.status = ?
ORDER BY u.create_time DESC
LIMIT 20 OFFSET 0
```

特性：
- 动态字段选择
- 动态过滤条件
- 动态排序
- 分页支持
- 关联查询
- 聚合查询

## 关联加载策略

### 1. 懒加载 (Lazy Loading)

```java
User user = dao().getEntityById("1");
// dept对象未加载

user.getDept(); // 触发懒加载
// SELECT * FROM nop_auth_dept WHERE dept_id = ?
```

特点：
- 按需加载，减少初始查询
- 会话关闭后无法访问
- 可能导致N+1查询问题

### 2. 急加载 (Eager Loading)

```java
// 通过loadProps预加载
User user = dao().getEntityById("1");
dao().loadProps(user, Arrays.asList("dept", "roles"));

// dept和roles已加载
user.getDept(); // 不触发查询
user.getRoles(); // 不触发查询
```

特点：
- 一次性加载关联数据
- 避免N+1查询
- 增加初始查询数据量

### 3. JOIN查询

```java
QueryBean query = new QueryBean();
query.setSources(List.of(
    new QuerySourceBean()
        .setAlias("u")
        .setEntityName("NopAuthUser"),
    new QuerySourceBean()
        .setAlias("d")
        .setEntityName("NopAuthDept")
        .setJoinType("LEFT")
        .setOnFilter(FilterBeans.eq("u.dept_id", "d.dept_id"))
));

List<User> users = dao().findAllByQuery(query);
```

特点：
- 单次查询获取主表和关联表数据
- 高效的关联查询
- 支持多层关联

## 级联操作

### 级联类型

| 类型 | 说明 | 示例 |
|------|------|------|
| PERSIST | 级联保存 | 保存用户时自动保存角色 |
| MERGE | 级联更新 | 更新用户时自动更新角色 |
| REMOVE | 级联删除 | 删除用户时自动删除角色 |
| REFRESH | 级联刷新 | 刷新用户时自动刷新角色 |
| DETACH | 级联分离 | 分离用户时自动分离角色 |

### 级联配置

```java
@Entity
public class User {
    @OneToMany(cascade = CascadeType.ALL)
    private List<Role> roles;
}
```

### 级联删除策略

**删除策略**:
- **CASCADE**: 级联删除关联对象
- **SET_NULL**: 将外键设置为null
- **RESTRICT**: 禁止删除（有关联时）
- **NO_ACTION**: 不做任何处理

```java
@ManyToOne
@JoinColumn(name = "dept_id", onDelete = "SET_NULL")
private Dept dept;
```

## 事务管理

### 事务隔离级别

```java
public enum IsolationLevel {
    DEFAULT,        // 使用数据库默认级别
    READ_UNCOMMITTED, // 读未提交
    READ_COMMITTED,   // 读已提交
    REPEATABLE_READ, // 可重复读
    SERIALIZABLE      // 串行化
}
```

### 事务传播

```java
public enum Propagation {
    REQUIRED,      // 必须在事务中运行，否则创建新事务
    REQUIRES_NEW,  // 创建新事务，挂起当前事务
    MANDATORY,     // 必须在现有事务中运行
    SUPPORTS,      // 支持当前事务，非事务环境下以非事务运行
    NOT_SUPPORTED,  // 不支持事务，挂起当前事务
    NEVER,         // 不支持事务，存在事务则抛异常
    NESTED         // 嵌套事务
}
```

## 性能优化

### 1. 批量操作

```java
// 批量插入
List<User> users = Arrays.asList(user1, user2, user3);
dao().batchSaveEntities(users);

// 批量更新
dao().batchUpdateByIds(users, Arrays.asList("name", "email"));

// 批量删除
dao().batchDeleteEntities(users);
```

### 2. 查询缓存

```java
@Entity
@Cacheable(
    cacheName = "userCache",
    cacheTimeout = 3600
)
public class User implements IOrmEntity {
    // ...
}
```

### 3. 字段选择

```java
QueryBean query = new QueryBean();
query.setFields(Arrays.asList("userId", "userName", "email"));
List<User> users = dao().findAllByQuery(query);
```

### 4. 索引优化

```java
@Entity
@Table(indexes = {
    @Index(name = "idx_user_email", columns = {"email"}),
    @Index(name = "idx_user_status", columns = {"status", "createTime"})
})
public class User implements IOrmEntity {
    // ...
}
```

### 5. 读写分离

docs-for-ai 不提供基于 Spring 组件模型的示例（如 `@Component`），也不假设读写分离的默认实现方式。

如果要说明读写分离：

- 先在仓库源码中搜索确认真实存在的 DataSource 抽象、命名方式、以及事务/ORM 模板 API。
- 示例里出现的注解/接口/类名必须能在仓库中找到来源。

## 扩展点

### 1. 自定义类型转换器

如需扩展类型转换器，请以仓库中已有的 `ITypeConverter` 及其实现为准；docs-for-ai 不提供不可验证的示例代码。

### 2. 自定义拦截器

如需扩展 ORM 拦截器，请以仓库中已有的 `IOrmInterceptor` 及其实现为准；docs-for-ai 不提供不可验证的示例代码。

### 3. 自定义SQL生成器

```java
public class MySqlGenerator implements ISqlGenerator {
    @Override
    public String generateInsert(IEntityModel entityModel, IOrmEntity entity) {
        // 自定义INSERT语句生成
    }

    @Override
    public String generateUpdate(IEntityModel entityModel, IOrmEntity entity, Set<String> changedProps) {
        // 自定义UPDATE语句生成
    }
}
```

## 最佳实践

### 1. 使用懒加载避免不必要查询

```java
// ✅ 推荐：使用懒加载
User user = dao().getEntityById("1");
// 只有需要时才加载dept
if (user.getDept() != null) {
    System.out.println(user.getDept().getDeptName());
}

// ❌ 不推荐：总是急加载所有关联
dao().loadProps(user, Arrays.asList("dept", "roles", "permissions", "orders"));
```

### 2. 批量操作代替循环

```java
// ✅ 推荐：批量操作
List<User> users = dao().findAllByQuery(query);
dao().batchSaveEntities(users);

// ❌ 不推荐：循环操作
for (User user : users) {
    dao().saveEntity(user);
}
```

### 3. 合理使用查询缓存

```java
// ✅ 推荐：对频繁访问但不常修改的数据启用缓存
@Entity
@Cacheable
public class SystemConfig implements IOrmEntity {
    // ...
}

// ❌ 不推荐：对频繁修改的数据启用缓存
@Entity
@Cacheable
public class Order implements IOrmEntity {
    // ...
}
```

### 4. 使用字段选择减少数据传输

```java
// ✅ 推荐：只查询需要的字段
QueryBean query = new QueryBean();
query.setFields(Arrays.asList("userId", "userName", "email"));
List<User> users = dao(). findAllByQuery(query);

// ❌ 不推荐：查询所有字段
List<User> users = dao().findAllByQuery(query);
```

### 5. 避免N+1查询

```java
// ✅ 推荐：使用JOIN或批量加载
QueryBean query = new QueryBean();
query.setSources(List.of(
    new QuerySourceBean()
        .setAlias("u")
        .setEntityName("NopAuthUser"),
    new QuerySourceBean()
        .setAlias("d")
        .setEntityName("NopAuthDept")
        .setJoinType("LEFT")
        .setOnFilter(FilterBeans.eq("u.dept_id", "d.dept_id"))
));
List<User> users = dao().findAllByQuery(query);

// ❌ 不推荐：循环查询
for (User user : users) {
    Dept dept = user.getDept(); // 每次都触发查询
    System.out.println(dept.getDeptName());
}
```

## 相关文档

- [IEntityDao使用指南](../getting-started/dao/entitydao-usage.md)
- [QueryBean使用指南](../getting-started/dao/querybean-guide.md)
- [FilterBeans使用指南](../getting-started/dao/filterbeans-guide.md)
- [事务管理指南](../getting-started/core/transaction-guide.md)
- [API架构文档](./api-architecture.md)

## 总结

Nop Platform的ORM引擎是一个轻量级、高性能的对象关系映射框架，具有以下特点：

1. **模型驱动**: 基于元模型进行所有操作
2. **声明式映射**: 约定优于配置，减少配置量
3. **状态管理**: 自动跟踪实体状态，实现脏检查
4. **智能缓存**: 支持二级缓存和查询缓存
5. **灵活扩展**: 提供丰富的扩展点

通过合理使用懒加载、批量操作、查询缓存等特性，可以构建高性能的数据访问层。
