# IEntityDao 使用指南

## 概述

`IEntityDao`是Nop平台提供的核心数据访问接口，用于统一处理实体的CRUD操作，是开发中最常用的数据访问组件。每个实体都会自动生成对应的DAO接口，如`NopAuthUserDao`对应`NopAuthUser`实体。

**重要提示**：在业务开发中，通常不需要直接使用`IEntityDao`接口，而是继承`CrudBizModel`类，它提供了更简便的API方法，包括简化的CRUD操作、分页查询等。`CrudBizModel`内部使用`IEntityDao`来执行实际的数据访问操作。

## 核心功能

`IEntityDao`提供了全面的实体操作方法，包括：
- 基本CRUD操作
- 复杂条件查询
- 分页查询
- 批量操作
- 关联属性加载
- 字段选择查询

## 基本使用

### 依赖注入

通过依赖注入获取DAO实例：

```java
@Inject
private NopAuthUserDao userDao;
```

### CRUD操作

```java
// 保存实体
userDao.saveEntity(user);

// 更新实体
userDao.updateEntity(user);

// 删除实体
userDao.deleteEntity(user);

// 根据主键获取实体（如果不存在返回null）
NopAuthUser user = userDao.getEntityById(id);

// 根据主键加载实体（总是返回对象，可能是proxy）
NopAuthUser user = userDao.loadEntityById(id);

// 根据主键列表批量获取实体
List<NopAuthUser> users = userDao.batchGetEntitiesByIds(Arrays.asList(id1, id2, id3));
```

## QueryBean 查询

`QueryBean`是Nop平台用于构建结构化查询的核心类，支持复杂条件查询、排序、分页等功能。

### 基本查询

```java
// 创建查询对象
QueryBean query = new QueryBean();

// 添加过滤条件
query.setFilter(FilterBeans.and(
    FilterBeans.eq("status", 1),
    FilterBeans.gt("createTime", startTime)
));

// 添加排序
query.addOrderField(OrderFieldBean.forField("createTime", true)); // 降序

// 执行查询
List<NopAuthUser> users = userDao.findAllByQuery(query);
```

### 分页查询

```java
// 设置分页参数
query.setOffset(0);  // 起始位置
query.setLimit(20);   // 每页大小

// 分页查询
List<NopAuthUser> users = userDao.findPageByQuery(query);

// 或者使用CrudBizModel的分页方法（返回PageBean对象）
PageBean<NopAuthUser> page = bizModel.findPage(query, 1, 20);

// 结果处理
long total = page.getTotal();
List<NopAuthUser> items = page.getItems();
```

### 条件构建

`FilterBeans`工具类提供了丰富的条件构建方法：

| 方法 | 描述 | 示例 |
|------|------|------|
| eq(name, value) | 等于 | `FilterBeans.eq("name", "admin")` |
| ne(name, value) | 不等于 | `FilterBeans.ne("status", 0)` |
| gt(name, value) | 大于 | `FilterBeans.gt("age", 18)` |
| ge(name, value) | 大于等于 | `FilterBeans.ge("score", 60)` |
| lt(name, value) | 小于 | `FilterBeans.lt("price", 100)` |
| le(name, value) | 小于等于 | `FilterBeans.le("count", 10)` |
| in(name, values) | 包含于 | `FilterBeans.in("id", Arrays.asList(1,2,3))` |
| notIn(name, values) | 不包含于 | `FilterBeans.notIn("status", Arrays.asList(0,1))` |
| between(name, min, max) | 范围查询 | `FilterBeans.between("age", 18, 30)` |
| startsWith(name, value) | 以...开头 | `FilterBeans.startsWith("name", "A")` |
| endsWith(name, value) | 以...结尾 | `FilterBeans.endsWith("email", "@gmail.com")` |
| contains(name, value) | 包含 | `FilterBeans.contains("name", "test")` |
| isNull(name) | 为空 | `FilterBeans.isNull("deletedAt")` |
| notNull(name) | 不为空 | `FilterBeans.notNull("createdAt")` |
| and(filters) | 逻辑与 | `FilterBeans.and(filter1, filter2)` |
| or(filters) | 逻辑或 | `FilterBeans.or(filter1, filter2)` |
| not(filter) | 逻辑非 | `FilterBeans.not(filter)` |

### Example 查询

除了`QueryBean`，`IEntityDao`还支持Example查询，通过实体对象构建查询条件：

```java
// 创建Example对象
NopAuthUser example = new NopAuthUser();
example.setStatus(1);
example.setName("admin");

// 执行查询（返回第一条匹配记录）
NopAuthUser user = userDao.findFirstByExample(example);

// 执行查询（返回所有匹配记录）
List<NopAuthUser> users = userDao.findAllByExample(example);

// 执行计数查询
long count = userDao.countByExample(example);

// 执行分页查询（需要提供orderBy、offset和limit参数）
List<NopAuthUser> users = userDao.findPageByExample(example, null, 0, 20);
```

## 批量操作

### 批量获取实体

```java
// 根据主键列表批量获取实体
List<NopAuthUser> users = userDao.batchGetEntitiesByIds(Arrays.asList(id1, id2, id3));

// 获取主键到实体的映射
Map<Object, NopAuthUser> userMap = userDao.batchGetEntityMapByIds(Arrays.asList(id1, id2, id3));
```

### 批量保存/更新/删除

```java
// 批量保存实体
userDao.batchSaveEntities(users);

// 批量更新实体
userDao.batchUpdateEntities(users);

// 批量删除实体
userDao.batchDeleteEntities(users);
```

## 关联属性加载

### batchLoad 方法

`batchLoad`相关方法用于批量加载实体的关联属性，解决N+1查询问题：

```java
// 批量加载实体集合的指定属性
userDao.batchLoadProps(users, Arrays.asList("roles", "departments"));

// 为单个实体加载属性
userDao.batchLoadPropsForEntity(user, "roles", "departments");

// 根据字段选择器批量加载属性
FieldSelectionBean selectionBean = new FieldSelectionBean();
selectionBean.addField("roles");
selectionBean.addField("departments");
userDao.batchLoadSelection(users, selectionBean);
```

### 懒加载与急加载

```java
// 急加载关联属性（通过IOrmTemplate）
NopAuthUser user = ormTemplate.loadById(id);
ormTemplate.batchLoadProps(Collections.singleton(user), Arrays.asList("roles", "departments"));

// 使用QueryBean指定要加载的属性
QueryBean query = new QueryBean();
query.setLeftJoinProps(Arrays.asList("roles", "departments"));
NopAuthUser user = userDao.findFirstByQuery(query);
```

## 字段选择查询

### FieldSelectionBean

通过`FieldSelectionBean`指定要查询的字段，减少数据库查询的数据量：

```java
// 创建字段选择器
FieldSelectionBean selection = new FieldSelectionBean();
selection.addField("id");
selection.addField("name");
selection.addField("email");

// 查询指定字段
List<NopAuthUser> users = userDao.findListBySelection(selection, FilterBeans.eq("status", 1));
```

### QueryBean 字段选择

```java
// 创建查询对象
QueryBean query = new QueryBean();
query.addField(QueryFieldBean.forField("id"));
query.addField(QueryFieldBean.forField("name"));
query.addField(QueryFieldBean.forField("email"));

// 执行查询
List<NopAuthUser> users = userDao.findListByQuery(query);
```

## 实际项目示例

### nop-app-mall 项目示例

在`nop-app-mall`项目中，`LitemallGoodsBizModel`使用`LitemallGoodsDao`进行商品管理：

```java
@BizModel
public class LitemallGoodsBizModel extends CrudBizModel<LitemallGoods> {
    
    @Inject
    private LitemallGoodsDao goodsDao;
    
    // 查询商品列表
    @BizQuery
    public PageBean<LitemallGoods> queryGoods(QueryBean query) {
        // 添加默认过滤条件
        query.setFilter(FilterBeans.and(
            query.getFilter(),
            FilterBeans.eq("isOnSale", true)
        ));
        
        // 执行分页查询
        return goodsDao.findPageByQuery(query);
    }
    
    // 批量更新商品状态
    @BizMutation
    @Transactional
    public void batchUpdateStatus(List<String> goodsIds, Boolean isOnSale) {
        // 批量获取商品
        List<LitemallGoods> goodsList = goodsDao.batchGetEntitiesByIds(goodsIds);

        // 更新状态
        for (LitemallGoods goods : goodsList) {
            goods.setIsOnSale(isOnSale);
        }

        // 批量保存
        goodsDao.batchSaveEntities(goodsList);
    }
}
```

## 最佳实践

1. **优先使用IEntityDao**：对于简单的CRUD操作，优先使用自动生成的DAO接口
2. **复杂查询使用QueryBean**：复杂条件查询使用`QueryBean`构建结构化查询
3. **批量操作优化性能**：大量数据操作使用批量方法，减少数据库连接次数
4. **避免N+1查询**：使用`batchLoad`方法批量加载关联属性
5. **字段选择减少数据传输**：只查询需要的字段，提高查询性能
6. **合理使用事务**：在需要保证数据一致性的场景下使用事务

## 注意事项

1. **线程安全**：DAO接口是线程安全的，可以在多线程环境下使用
2. **事务管理**：通常在Service层管理事务，DAO层方法默认不开启事务
3. **关联查询**：复杂关联查询建议使用SQL库或原生SQL
4. **性能优化**：
   - 避免查询大量不必要的字段
   - 合理使用索引
   - 批量操作减少数据库交互次数
5. **异常处理**：DAO方法抛出的异常会被上层框架统一处理

## 与其他组件的关系

| 组件 | 关系 | 使用场景 |
|------|------|----------|
| IEntityDao | 核心数据访问接口 | 实体CRUD操作、简单查询、条件查询 |
| IOrmTemplate | ORM操作模板 | 事务管理、session管理、关联属性加载 |
| IOrmEntityDao | ORM实体DAO接口 | 继承自IEntityDao，提供ORM特定功能 |
| CrudBizModel | 业务模型基类 | 提供简化的CRUD API和业务逻辑封装，内部使用IEntityDao |
| IJdbcTemplate | JDBC操作模板 | 高性能、复杂SQL查询、原生SQL执行 |
| SqlLib | SQL模板库 | 大量复杂SQL、动态SQL |

### API层次关系

```
CrudBizModel (业务层)
    └── 使用 IOrmEntityDao
            └── 继承自 IEntityDao (数据访问层)
                    └── 由 OrmEntityDao 实现
                            └── 使用 IOrmTemplate
                                    └── 管理 IOrmSession
```

## 常见问题

### Q1: IEntityDao和IOrmEntityDao有什么区别？

**答案**:
- **IEntityDao**: 基础数据访问接口，提供基本的CRUD操作和查询功能
- **IOrmEntityDao**: 继承自IEntityDao，增加了ORM特定功能，如关联属性加载、延迟加载等

```java
// 使用IEntityDao
IEntityDao<NopAuthUser> dao = dao();
NopAuthUser user = dao.getEntityById(userId);

// 使用IOrmEntityDao
IOrmEntityDao<NopAuthUser> ormDao = (IOrmEntityDao<NopAuthUser>) dao;
ormDao.loadEntity(userId); // 加载实体并初始化关联属性
```

### Q2: 什么时候使用getEntityById，什么时候使用loadEntityById？

**答案**:
- **getEntityById**: 从数据库加载实体，返回null表示不存在
- **loadEntityById**: 从数据库加载实体，抛出异常表示不存在

```java
// 使用getEntityById
User user = dao.getEntityById(userId);
if (user == null) {
    // 处理用户不存在的情况
}

// 使用loadEntityById
try {
    User user = dao.loadEntityById(userId);
    // 处理用户存在的情况
} catch (NopException e) {
    // 处理用户不存在的情况
}
```

### Q3: 如何提高批量操作的性能？

**答案**: 使用批量方法减少数据库交互次数：

```java
// ❌ 低效：循环单个操作
for (String userId : userIds) {
    User user = dao.getEntityById(userId);
    user.setStatus(newStatus);
    dao.updateEntity(user);
}

// ✅ 高效：批量操作
List<User> users = dao.batchGetEntitiesByIds(userIds);
for (User user : users) {
    user.setStatus(newStatus);
}
dao.batchSaveEntities(users);
```

### Q4: 如何处理查询中的空值？

**答案**: 使用QueryBean的FilterBeans处理空值：

```java
QueryBean query = QueryBean.forQuery(User.class);

// 使用isNull和notNull判断空值
if (userName == null) {
    query.filter(User.PROP_NAME_userName, FilterBean.isNull());
} else {
    query.filter(User.PROP_NAME_userName, FilterBean.eq(userName));
}

// 使用isEmpty和isNotEmpty判断空字符串
if (email != null && email.isEmpty()) {
    query.filter(User.PROP_NAME_email, FilterBean.isEmpty());
}
```

### Q5: 如何进行模糊查询？

**答案**: 使用FilterBeans的contains、startsWith、endsWith方法：

```java
QueryBean query = QueryBean.forQuery(User.class);

// 包含查询
query.filter(User.PROP_NAME_userName, FilterBean.contains("john"));

// 开头匹配
query.filter(User.PROP_NAME_userName, FilterBean.startsWith("john"));

// 结尾匹配
query.filter(User.PROP_NAME_userName, FilterBean.endsWith("doe"));

// 正则匹配
query.filter(User.PROP_NAME_email, FilterBean.regex(".*@example\\.com"));
```

### Q6: 如何进行排序查询？

**答案**: 使用QueryBean的orderBy方法：

```java
QueryBean query = QueryBean.forQuery(User.class);

// 单字段排序
query.orderBy(User.PROP_NAME_createTime, true); // true表示升序

// 多字段排序
query.orderBy(User.PROP_NAME_status, false) // 降序
     .orderBy(User.PROP_NAME_createTime, true); // 升序
```

### Q7: 如何进行分页查询？

**答案**: 使用QueryBean的setOffset和setLimit方法：

```java
QueryBean query = QueryBean.forQuery(User.class);
query.setFilter(FilterBean.eq(User.PROP_NAME_status, 1));

// 设置分页参数
int pageNo = 1;
int pageSize = 20;
query.setOffset((pageNo - 1) * pageSize);
query.setLimit(pageSize);

// 执行查询
PageBean<User> page = dao.findPageByQuery(query);
List<User> users = page.getItems();
int totalCount = page.getTotalCount();
```

### Q8: 如何使用事务？

**答案**: 使用@Transactional注解或txn()方法：

```java
// 使用@Transactional注解
@Transactional
public void updateUserStatus(String userId, Integer status) {
    User user = dao.getEntityById(userId);
    user.setStatus(status);
    dao.updateEntity(user);
}

// 使用txn()方法
public void updateUserStatus(String userId, Integer status) {
    txn(() -> {
        User user = dao.getEntityById(userId);
        user.setStatus(status);
        dao.updateEntity(user);
    });
}
```

### Q9: 如何处理并发修改？

**答案**: 使用乐观锁或悲观锁：

```java
// 乐观锁：使用@Version注解
@Entity(table = "t_user")
public class User {
    @Id
    private String userId;

    @Version
    private Integer version;

    // getter和setter
}

// 悲观锁：使用select for update
public User getUserForUpdate(String userId) {
    User user = dao.getEntityById(userId);
    dao.lockEntity(user);
    return user;
}
```

### Q10: 如何使用原生SQL？

**答案**: 使用IJdbcTemplate执行原生SQL：

```java
@Inject
private IJdbcTemplate jdbcTemplate;

public List<User> findUsersByNativeSql(String sql) {
    return jdbcTemplate.findList(sql, User.class);
}

public int executeNativeSql(String sql, Object... params) {
    return jdbcTemplate.update(sql, params);
}
```

## 相关文档

- [QueryBean使用指南](./querybean-guide.md) - 查询对象详解
- [FilterBeans使用指南](./filterbeans-guide.md) - 过滤条件详解
- [服务层开发指南](../service/service-layer-development.md) - BizModel开发详解
- [GraphQL服务开发指南](../api/graphql-guide.md) - GraphQL API开发
- [事务管理指南](../core/transaction-guide.md) - 事务管理完整指南
- [异常处理指南](../core/exception-guide.md) - 异常处理完整指南
- [数据层开发](./data-layer-development.md) - 数据层开发指南
- [数据处理指南](./data-processing.md) - 数据处理指南

## 总结

`IEntityDao`是Nop平台中最常用的数据访问组件，提供了全面的实体操作方法，支持从简单CRUD到复杂条件查询的各种场景。通过合理使用`QueryBean`和批量操作方法，可以提高数据访问效率，减少数据库负载。在实际开发中，建议优先使用`IEntityDao`进行数据访问，只有在需要特殊功能时才考虑使用底层的`IOrmTemplate`或`IJdbcTemplate`。