# IEntityDao 使用指南

## 概述

`IEntityDao`是Nop平台提供的核心数据访问接口，用于统一处理实体的CRUD操作，是开发中最常用的数据访问组件。每个实体都会自动生成对应的DAO接口，如`NopAuthUserDao`对应`NopAuthUser`实体。

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
userDao.save(user);

// 更新实体
userDao.update(user);

// 删除实体
userDao.delete(user);

// 根据主键获取实体
NopAuthUser user = userDao.findById(id);

// 根据主键列表批量获取实体
List<NopAuthUser> users = userDao.findByIds(Arrays.asList(id1, id2, id3));
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
List<NopAuthUser> users = userDao.findListByQuery(query);
```

### 分页查询

```java
// 分页查询
PageBean<NopAuthUser> page = userDao.findPageByQuery(query, 1, 20);

// 结果处理
long total = page.getTotal();
List<NopAuthUser> users = page.getItems();
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

// 执行查询
List<NopAuthUser> users = userDao.findByExample(example);

// 执行分页查询
PageBean<NopAuthUser> page = userDao.findPageByExample(example, 1, 20);
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
// 急加载关联属性
NopAuthUser user = userDao.findById(id, "roles", "departments");

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
        List<LitemallGoods> goodsList = goodsDao.findByIds(goodsIds);
        
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
| IEntityDao | 核心数据访问接口 | 实体CRUD操作、简单查询 |
| IOrmTemplate | ORM操作模板 | 事务管理、混合使用ORM和SQL |
| IJdbcTemplate | JDBC操作模板 | 高性能、复杂SQL查询 |
| SqlLib | SQL模板库 | 大量复杂SQL、动态SQL |

## 总结

`IEntityDao`是Nop平台中最常用的数据访问组件，提供了全面的实体操作方法，支持从简单CRUD到复杂条件查询的各种场景。通过合理使用`QueryBean`和批量操作方法，可以提高数据访问效率，减少数据库负载。在实际开发中，建议优先使用`IEntityDao`进行数据访问，只有在需要特殊功能时才考虑使用底层的`IOrmTemplate`或`IJdbcTemplate`。