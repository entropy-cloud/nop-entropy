# 数据层开发指南

## 概述

Nop平台提供多种数据访问方式，包括ORM框架、JDBC模板、SQL库等，支持不同场景的数据访问需求。

## 核心组件

### 1. IEntityDao - ORM数据访问

**定义**：实体数据访问接口，基于ORM框架实现，是开发中最常用的数据访问组件
**位置**：自动生成，如`io.nop.auth.dao.entity.NopAuthUserDao`
**核心功能**：
- 实体的增删改查操作
- 支持复杂查询和条件过滤
- 自动处理关联关系
- 批量操作支持

**使用场景**：
- 实体的CRUD操作
- 基于实体的复杂查询
- 需要自动处理关联关系的场景

**详细用法**：请参考[IEntityDao 使用指南](entitydao-usage.md)

### 2. IOrmTemplate - ORM模板

**定义**：ORM操作模板，提供更灵活的ORM操作
**位置**：`io.nop.orm.api.IOrmTemplate`
**核心功能**：
- 提供事务管理
- 支持原生SQL查询
- 支持批量操作
- 支持SQL与ORM混合使用

**使用场景**：
- 需要事务管理的场景
- 混合使用SQL和ORM的场景
- 需要批量操作的场景

**示例代码**：
```java
@Inject
protected IOrmTemplate ormTemplate;

// 事务管理
ormTemplate.runInTxn(() -> {
    // 执行多个ORM操作
    userDao.save(user1);
    userDao.save(user2);
    return null;
});

// 原生SQL查询
List<Map<String, Object>> result = ormTemplate.getSession().createNativeQuery(
    "SELECT * FROM nop_auth_user WHERE status = :status", Map.class)
    .setParameter("status", 1)
    .list();
```

### 3. IJdbcTemplate - JDBC模板

**定义**：直接JDBC操作模板，提供底层JDBC访问
**位置**：`io.nop.dao.api.IJdbcTemplate`
**核心功能**：
- 直接执行SQL语句
- 支持预编译和批量操作
- 提供连接管理和事务支持
- 支持多种结果集映射

**使用场景**：
- 需要高性能的场景
- 复杂SQL查询
- 数据库特定功能
- 批量数据操作

**示例代码**：
```java
@Inject
protected IJdbcTemplate jdbcTemplate;

// 查询单个值
int count = jdbcTemplate.queryForInt("SELECT COUNT(*) FROM nop_auth_user");

// 查询列表
List<UserVO> users = jdbcTemplate.queryForList(
    "SELECT id, name, email FROM nop_auth_user WHERE status = ?", 
    UserVO.class, 1);

// 执行更新
int updated = jdbcTemplate.update(
    "UPDATE nop_auth_user SET status = ? WHERE id = ?", 0, id);

// 批量更新
jdbcTemplate.batchUpdate(
    "INSERT INTO nop_auth_user (id, name, email) VALUES (?, ?, ?)",
    users.stream().map(user -> 
        new Object[]{user.getId(), user.getName(), user.getEmail()}
    ).collect(Collectors.toList()));
```

### 4. SqlLib - SQL库

**定义**：SQL模板库，集中管理SQL语句
**位置**：通常存放在`_vfs`目录下，如`/sql/nop-auth/user.sql.xml`
**核心功能**：
- 集中管理SQL语句
- 支持动态SQL
- 支持SQL模板复用
- 支持参数映射

**使用场景**：
- 大量复杂SQL语句
- 需要动态生成SQL的场景
- 支持多种数据库方言
- 需要SQL语句版本管理

**SQL模板示例**：
```xml
<sql:lib xmlns:sql="http://nop-xlang.github.io/schema/sql.xdef">
  <sql:template name="findUserByName">
    <sql:param name="name" type="string"/>
    <sql:result type="UserVO"/>
    <sql:content>
      SELECT id, name, email, status
      FROM nop_auth_user
      WHERE name = :name
      AND status = 1
    </sql:content>
  </sql:template>
  
  <sql:template name="findUserByEmail">
    <sql:param name="email" type="string"/>
    <sql:result type="UserVO"/>
    <sql:content>
      SELECT id, name, email, status
      FROM nop_auth_user
      WHERE email = :email
    </sql:content>
  </sql:template>
</sql:lib>
```

**使用示例**：
```java
@Inject
protected SqlLibManager sqlLibManager;

// 获取SQL模板
SqlTemplate template = sqlLibManager.getSqlTemplate("/sql/nop-auth/user.sql.xml", "findUserByName");

// 执行SQL
UserVO user = template.execute(UserVO.class, Map.of("name", "admin"));
```

## 选择指南

| 组件 | 适用场景 | 优势 | 劣势 |
|------|----------|------|------|
| IEntityDao | 实体CRUD、关联查询 | 简单易用、自动处理关联 | 复杂SQL支持有限 |
| IOrmTemplate | 事务管理、混合使用 | 灵活、支持事务、混合使用 | 学习成本较高 |
| IJdbcTemplate | 高性能、复杂SQL | 高性能、灵活、支持批量 | 手动处理映射、底层操作 |
| SqlLib | 大量复杂SQL、动态SQL | 集中管理、动态生成、支持多种数据库 | 需要额外配置 |

## 最佳实践

1. **优先使用IEntityDao**：对于简单的CRUD操作，优先使用IEntityDao
2. **合理使用事务**：在需要保证数据一致性的场景下，使用事务管理
3. **复杂SQL使用SqlLib**：大量复杂SQL语句使用SqlLib集中管理
4. **性能优化**：
   - 避免N+1查询问题
   - 合理使用批量操作
   - 优化SQL语句
5. **事务边界**：
   - 事务边界尽可能小
   - 避免在事务中执行耗时操作
6. **异常处理**：
   - 正确处理数据访问异常
   - 记录详细的错误日志

## 注意事项

1. **线程安全**：所有数据访问组件都是线程安全的
2. **资源管理**：自动管理数据库连接和资源
3. **事务传播**：支持事务传播机制
4. **参数绑定**：使用预编译语句，避免SQL注入
5. **结果集映射**：自动处理结果集映射，支持多种映射方式
6. **连接池**：内置连接池管理，无需手动配置

## 示例：分层数据访问

```java
// Service层
@BizModel
public class UserBizModel extends CrudBizModel<User> {
    @Inject
    protected IJdbcTemplate jdbcTemplate;
    
    @Inject
    protected SqlLibManager sqlLibManager;
    
    public UserBizModel() {
        setEntityName(User.class.getName());
    }
    
    // 使用IEntityDao进行简单CRUD
    @BizQuery
    public List<User> findActiveUsers() {
        return dao().findList(User.STATUS.eq(1));
    }
    
    // 使用SqlLib进行复杂查询
    @BizQuery
    public List<UserStats> getUserStats() {
        SqlTemplate template = sqlLibManager.getSqlTemplate("/sql/user.sql.xml", "getUserStats");
        return template.executeList(UserStats.class);
    }
    
    // 使用IJdbcTemplate进行批量操作
    @BizMutation
    public void batchUpdateUserStatus(List<String> userIds, int status) {
        jdbcTemplate.batchUpdate(
            "UPDATE user SET status = ? WHERE id = ?",
            userIds.stream().map(id -> 
                new Object[]{status, id}
            ).collect(Collectors.toList()));
    }
}
```

## 相关文档

- [IEntityDao使用指南](./entitydao-usage.md) - 数据访问接口详解
- [QueryBean使用指南](./querybean-guide.md) - 查询对象详解
- [FilterBeans使用指南](./filterbeans-guide.md) - 过滤条件详解
- [服务层开发指南](../service/service-layer-development.md) - BizModel开发详解
- [事务管理指南](../core/transaction-guide.md) - 事务管理完整指南
- [数据处理指南](./data-processing.md) - 数据处理指南

## 总结

Nop平台提供了多种数据访问方式，满足不同场景的需求：

1. **IEntityDao**: 优先使用，支持实体CRUD和关联查询
2. **IOrmTemplate**: 灵活，支持事务管理和混合使用
3. **IJdbcTemplate**: 高性能，适合复杂SQL和批量操作
4. **SqlLib**: 集中管理，适合大量复杂SQL和动态SQL

选择合适的数据访问组件，可以提高开发效率和系统性能。遵循最佳实践，可以构建高效、可靠的数据访问层。