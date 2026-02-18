# NopORM 高级特性指南

## 概述

NopORM提供了丰富的高级特性，用于处理复杂的业务场景，包括分库分表、字段加密、数据脱敏、性能监控、Hook机制等。这些特性都是内置的，无需额外依赖第三方框架，可以大幅提升开发效率和运行时性能。

## 核心功能

### 1. 分库分表

NopORM支持内置的分库分表功能，通过IShardSelector接口实现动态路由到不同的数据源。

#### 1.1 核心组件

| 类名 | 说明 |
|------|------|
| `io.nop.dao.shard.ShardSelection` | 分库分表选择结果 |
| `io.nop.dao.shard.ShardPropValue` | Shard 属性值 |
| `io.nop.dao.shard.IShardSelector` | 分片选择器接口 |
| `io.nop.dao.shard.EmptyShardSelector` | 空分片选择器（默认实现） |

#### 1.2 实体配置

在实体模型中配置分库分表（配置在`<entity>`标签上）：

```xml
<!-- entity.xdef -->
<entity name="User"
        tableName="user"
        useShard="true"
        shardProp="tenantId">
    <columns>
        <column name="id" stdSqlType="VARCHAR" primary="true"/>
        <column name="tenantId" stdSqlType="VARCHAR"/>
        <column name="name" stdSqlType="VARCHAR"/>
    </columns>
</entity>
```

**配置说明**：
- `useShard="true"` - 启用分库分表功能
- `shardProp="tenantId"` - 指定用于分片的属性名（对应column的name）

#### 1.3 自定义ShardSelector

实现IShardSelector接口来提供分片逻辑，不需要在BizModel中编写任何分片代码：

```java
package com.example.shard;

import io.nop.dao.shard.IShardSelector;
import io.nop.dao.shard.ShardSelection;

import java.util.Collections;
import java.util.List;

public class TenantShardSelector implements IShardSelector {

    @Override
    public boolean isSupportShard(String entityName) {
        // 返回true表示支持该实体的分片
        return "User".equals(entityName);
    }

    @Override
    public ShardSelection selectShard(String entityName, String shardProp, Object shardValue) {
        // 根据分片属性的值选择对应的分片
        // 例如：tenantId=123 -> shard_123
        String shardName = "shard_" + shardValue;
        return new ShardSelection("default", shardName);
    }

    @Override
    public List<ShardSelection> selectShards(String entityName, String shardProp, Object beginValue, Object endValue) {
        // 查询范围时可能需要访问多个分片
        return Collections.singletonList(
            new ShardSelection("default", "shard_" + beginValue)
        );
    }
}
```

#### 1.4 注册ShardSelector

通过IoC容器注册自定义的ShardSelector：

```xml
<!-- app.beans.xml -->
<beans x:schema="/nop/schema/beans/beans.xdef">
    <bean id="shardSelector" class="com.example.shard.TenantShardSelector"/>
</beans>
```

#### 1.5 自动分片机制

ORM会自动处理分片逻辑，无需在BizModel中编写任何分片代码：

```java
@BizModel("User")
public class UserBizModel extends CrudBizModel<NopAuthUser> {

    // ✅ 无需处理分片，ORM会自动路由到正确的分片
    @BizQuery
    public NopAuthUser getUserById(String userId) {
        return requireEntityById(userId);
    }

    // ✅ ORM会根据实体上的shardProp值自动选择分片
    @BizMutation
    public NopAuthUser createUser(Map<String, Object> data) {
        return doSave(data);
    }

    // ✅ 跨分片查询由ShardSelector自动处理
    @BizQuery
    public PageBean<NopAuthUser> findUsers(String keyword, int pageNo, int pageSize) {
        QueryBean query = new QueryBean();
        if (StringHelper.isNotEmpty(keyword)) {
            query.setFilter(FilterBeans.contains("name", keyword));
        }
        return findPage(query, pageNo, pageSize);
    }
}
```

### 2. 字段加密

NopORM支持自动字段加密功能，通过TAG_ENC标记和ITextCipher实现。

#### 3.1 核心组件

| 类名 | 说明 |
|------|------|
| `io.nop.commons.crypto.ITextCipher` | 加密接口 |
| `io.nop.commons.crypto.impl.AESTextCipher` | AES 加密实现 |
| `io.nop.dataset.binder.EncodedDataParameterBinder` | 加密数据绑定器 |
| `io.nop.orm.factory.DefaultOrmColumnBinderEnhancer` | 字段绑定器增强器 |
| `io.nop.orm.OrmConstants`（`TAG_ENC`） | 加密标记常量 |

#### 3.2 配置字段加密

```xml
<!-- app.orm.xml -->
<orm x:schema="/nop/schema/orm/orm.xdef">
    <entities>
        <entity name="User" tableName="user">
            <columns>
                <!-- 密码字段加密存储 -->
                <column name="password" stdDomain="string" tags="enc"/>
                
                <!-- 手机号加密存储 -->
                <column name="phone" stdDomain="string" tags="enc"/>
                
                <!-- 身份证号加密存储 -->
                <column name="idCard" stdDomain="string" tags="enc"/>
            </columns>
        </entity>
    </entities>
</orm>
```

#### 3.3 自动化机制

加密流程：

1. **保存时自动加密**：
   - 通过`EncodedDataParameterBinder`拦截
   - 使用AESTextCipher加密（支持AES/GCM/NoPadding、AES/CBC/PKCS5Padding）
   - 添加`ENC_VALUE_PREFIX`前缀

2. **读取时自动解密**：
   - 检测`ENC_VALUE_PREFIX`前缀
   - 自动解密并返回明文

3. **数据库存储**：
   - 存储加密值（带前缀），格式：`ENC_VALUE_PREFIX + Base64(加密结果)`
   - 例如：`ENC:a1b2c3...`

4. **应用层透明**：
   - 完全透明，业务代码无需关心加密逻辑

#### 3.4 测试验证

```java
@Test
public void testEncryptedColumn() {
    IEntityDao<SimsExam> dao = daoProvider().daoFor(SimsExam.class);
    SimsExam entity = new SimsExam();
    entity.setExamId("101");
    entity.setExamName("testExam");
    
    // 保存：自动加密
    dao.saveEntity(entity);
    
    // 读取：自动解密
    entity = dao.getEntityById("101");
    assertEquals("testExam", entity.getExamName());
    
    // 直接查询数据库：看到的是加密值
    Map<String, Object> row = jdbc().findFirst(new SQL("select * from sims_exam"));
    String examName = (String) row.get("EXAM_NAME");
    assertTrue(examName.startsWith(CommonConstants.ENC_VALUE_PREFIX));
}
```

### 3. 数据脱敏

NopORM支持自动数据脱敏功能，通过TAG_MASKED标记和ui:maskPattern配置实现。

#### 3.1 核心组件

| 组件 | 说明 |
|-----|------|
| OrmModelConstants.TAG_MASKED | 脱敏标记 |
| ui:maskPattern | 脱敏模式配置 |
| 元编程自动生成 | 自动生成transformOut |

#### 4.2 配置数据脱敏

```xml
<!-- app.xmeta.xml -->
<meta x:schema="/nop/schema/xmeta.xdef">
    <entity name="User">
        <props>
            <!-- 手机号脱敏：138****5678 -->
            <prop name="mobile" ui:maskPattern="3,4"/>
            
            <!-- 身份证号脱敏：110***********123 -->
            <prop name="idCard" ui:maskPattern="3,4"/>
            
            <!-- 邮箱脱敏：a***@example.com -->
            <prop name="email" ui:maskPattern="1,***"/>
            
            <!-- 银行卡号脱敏：6222************1234 -->
            <prop name="bankCard" ui:maskPattern="4,4"/>
        </props>
    </entity>
</meta>
```

#### 4.3 maskPattern格式

| 配置 | 示例值 | 脱敏效果 |
|-----|--------|---------|
| `3,4` | 1380012345678 | 138****5678 |
| `4,4` | 622212345678901234 | 6222************1234 |
| `3,4` | 11000119871230123 | 110***********123 |
| `1,***` | alice@example.com | a***@example.com |

**格式说明**：
- `3,4`：保留前3位和后4位，中间用`*`替换
- `4,4`：保留前4位和后4位，中间用`*`替换
- `1,***`：保留第1个字符，后面用`***`替换

#### 4.4 自动化脱敏时机

| 场景 | 自动脱敏 | 配置来源 |
|-----|---------|---------|
| 日志输出 | ✅ 自动 | TAG_MASKED + LogHelper |
| GraphQL返回 | ✅ 自动 | ui:maskPattern + 元编程生成transformOut |
| API响应 | ✅ 自动 | ui:maskPattern + 元编程生成transformOut |
| 前端展示 | ✅ 自动 | ui:maskPattern + 元编程生成transformOut |

### 4. 性能监控

NopORM提供了内置的性能监控功能，通过IDaoMetrics和OrmMetricsImpl实现。

#### 4.1 核心组件

| 类名 | 说明 |
|------|------|
| `io.nop.dao.metrics.IDaoMetrics` | DAO 层监控接口 |
| `io.nop.orm.metrics.OrmMetricsImpl` | ORM 监控实现 |
| `io.nop.commons.metrics.GlobalMeterRegistry` | 指标注册器 |

#### 5.2 监控指标

| 指标 | 说明 |
|-----|------|
| nop.dao.connections.obtained | 获取数据库连接数 |
| nop.dao.query.executions | 查询执行次数 |
| nop.dao.query.execute-updates | 更新执行次数 |
| nop.dao.query.batch-updates | 批量更新次数 |
| nop.orm.sessions.open | Session打开次数 |
| nop.orm.sessions.closed | Session关闭次数 |
| nop.orm.sessions.flush | Session刷新次数 |
| nop.orm.entities.delete | 实体删除次数 |
| nop.orm.entities.load | 实体加载次数 |
| nop.orm.entities.update | 实体更新次数 |
| nop.orm.entities.save | 实体保存次数 |

#### 5.3 启用监控

```xml
<!-- application.yaml -->
nop:
  metrics:
    enabled: true
    prometheus:
      enabled: true
      port: 9090
```

#### 5.4 自定义监控Hook

```java
@Component
public class CustomOrmMetrics {

    @Inject
    private IDaoMetrics daoMetrics;

    @Inject
    private IOrmMetrics ormMetrics;

    public void onConnectionObtained() {
        daoMetrics.onObtainConnection();
    }

    public void onQueryExecuted(String sql, long duration) {
        daoMetrics.endQuery(null, null, 0, null);
    }

    public void onEntitySaved(String entityName) {
        ormMetrics.onFlushSaveEntity(entityName);
    }
}
```

### 5. Hook机制

NopORM提供了强大的Hook机制，通过IPropGetMissingHook、IPropSetMissingHook、IPropMakeMissingHook实现属性访问拦截。

#### 5.1 核心Hook接口

| Hook接口 | 说明 |
|---------|------|
| IPropGetMissingHook | 属性获取时Hook |
| IPropSetMissingHook | 属性设置时Hook |
| IPropMakeMissingHook | 属性制造时Hook |

#### 6.2 使用Hook实现审计

```java
@Component
public class AuditLogHook implements IPropGetMissingHook, IPropSetMissingHook {

    @Inject
    private AuditLogService auditLogService;

    @Override
    public Object propGetMissing(Object bean, String propName, Object currentValue) {
        // 记录属性访问日志
        auditLogService.logPropertyAccess(bean.getClass().getName(), propName);
        return currentValue;
    }

    @Override
    public Object propSetMissing(Object bean, String propName, Object newValue) {
        // 记录属性修改日志
        auditLogService.logPropertyChange(bean.getClass().getName(), propName, newValue);
        return newValue;
    }

    @Override
    public Object propMakeMissing(Object bean, String propName, Object value) {
        // 记录属性创建日志
        auditLogService.logPropertyCreate(bean.getClass().getName(), propName, value);
        return value;
    }
}
```

#### 6.3 使用Hook实现缓存预热

```java
@Component
public class CacheWarmupHook implements IPropGetMissingHook {

    @Inject
    private ICacheService cacheService;

    @Override
    public Object propGetMissing(Object bean, String propName, Object currentValue) {
        // 检查是否已缓存
        String cacheKey = bean.getClass().getName() + ":" + propName;
        Object cachedValue = cacheService.get(cacheKey);
        
        if (cachedValue != null) {
            return cachedValue;
        }
        
        // 查询并缓存
        Object dbValue = queryFromDatabase(bean, propName);
        cacheService.put(cacheKey, dbValue);
        return dbValue;
    }

    private Object queryFromDatabase(Object bean, String propName) {
        // 查询数据库逻辑
        return null;
    }
}
```

## 示例代码

### 完整示例：加密 + 脱敏

```java
@BizModel("User")
public class UserBizModel extends CrudBizModel<NopAuthUser> {

    @BizQuery
    public NopAuthUser getUserById(String userId) {
        return requireEntityById(userId);
    }

    @BizMutation
    public NopAuthUser createUser(Map<String, Object> data) {
        // 创建用户（password字段会自动加密）
        return doSave(data, null, (entityData, ctx) -> {
            NopAuthUser entity = entityData.getEntity();
            entity.setCreateTime(new Date());
        }, null);
    }

    @BizQuery
    public PageBean<NopAuthUser> findUsers(String keyword, int pageNo, int pageSize) {
        // 构建查询
        QueryBean query = new QueryBean();

        // 添加搜索条件
        if (StringHelper.isNotEmpty(keyword)) {
            query.setFilter(FilterBeans.or(
                FilterBeans.contains("name", keyword),
                FilterBeans.contains("mobile", keyword),
                FilterBeans.contains("email", keyword)
            ));
        }

        // 查询结果（mobile/email字段会自动脱敏）
        return findPage(query, pageNo, pageSize);
    }
}
```

## 最佳实践

### 1. 分库分表

1. **合理选择分片键**：
   - 使用业务关联度高的字段作为分片键
   - 避免使用经常变更的字段作为分片键
   - 考虑数据分布均匀性

2. **实现ShardSelector**：
   - 实现IShardSelector接口自定义分片逻辑
   - 在IoC容器中注册ShardSelector实现
   - 无需在BizModel中编写分片代码

3. **自动分片机制**：
    - ORM会自动根据shardProp值调用ShardSelector
    - 查询、插入、更新都会自动路由到正确分片

### 2. 字段加密

1. **敏感字段标记enc**：
   - 密码、手机号、身份证号等敏感字段
   - 通过tags="enc"统一管理

2. **自定义加密算法**：
   - 如需自定义加密算法，实现ITextCipher接口
   - 通过Bean容器注入自定义加密器

3. **性能考虑**：
   - 加密会增加CPU开销
   - 考虑使用索引友好的加密算法

### 3. 数据脱敏

1. **配置合适的maskPattern**：
   - 根据业务需求选择脱敏规则
   - 手机号：`3,4`（保留前3位和后4位）
   - 身份证号：`3,4`（保留前3位和后4位）

2. **脱敏时机**：
   - 日志、GraphQL、API、前端都会自动脱敏
   - 无需手动调用脱敏逻辑

3. **敏感字段标记**：
   - 使用TAG_MASKED标记敏感字段
   - 日志输出时会自动脱敏

### 4. 性能监控

1. **启用监控**：
   - 在配置文件中启用metrics.enabled=true
   - 配置Prometheus端口等参数

2. **监控关键指标**：
   - 关注查询执行次数
   - 关注慢查询（长时间执行）
   - 关注连接池使用情况

3. **使用MeterRegistry**：
   - 通过GlobalMeterRegistry注册自定义指标
   - 集成到Prometheus等监控系统

### 5. Hook机制

1. **审计日志**：
   - 使用IPropGetMissingHook记录属性访问
   - 使用IPropSetMissingHook记录属性修改

2. **缓存预热**：
   - 使用IPropGetMissingHook实现缓存逻辑
   - 减少数据库查询

3. **性能监控**：
   - 在Hook中记录性能指标
   - 实现自定义的性能统计

## 常见问题

### Q1: 分库分表后如何查询所有数据？

**答案**: ShardSelector的selectShards方法会返回所有需要查询的分片，ORM会自动合并结果。对于跨分片查询场景，由ShardSelector自动处理：

```java
// ShardSelector实现
@Override
public List<ShardSelection> selectShards(String entityName, String shardProp,
                                          Object beginValue, Object endValue) {
    // 返回所有需要查询的分片列表
    List<ShardSelection> shards = new ArrayList<>();
    for (int i = (int) beginValue; i <= (int) endValue; i++) {
        shards.add(new ShardSelection("default", "shard_" + i));
    }
    return shards;
}

// BizModel无需处理分片逻辑
@BizQuery
public List<NopAuthUser> findUsersInRange(int minTenantId, int maxTenantId) {
    // ORM会自动调用ShardSelector.selectShards并合并结果
    QueryBean query = new QueryBean();
    query.setFilter(FilterBeans.range("tenantId", minTenantId, maxTenantId));
    return dao().findAllByQuery(query);
}
```

### Q2: 字段加密后如何查询？

**答案**: 字段加密是应用层透明的，ORM会自动处理加密和解密，查询逻辑无需修改：

```java
// ✅ 正确：直接查询，ORM自动解密
QueryBean query = new QueryBean();
query.setFilter(FilterBeans.eq("email", "user@example.com"));
List<User> users = dao().findAllByQuery(query);

// ❌ 错误：手动处理加密
String encrypted = encrypt("user@example.com");
query.setFilter(FilterBeans.eq("email", encrypted));
List<User> users = dao().findAllByQuery(query);  // 查询不到
```

### Q3: 如何自定义加密算法？

**答案**: 实现ITextCipher接口并通过Bean容器注入：

```java
@Component
public class CustomTextCipher implements ITextCipher {

    @Override
    public ITextCipher encKey(String encKey) {
        return this;
    }

    @Override
    public String encrypt(String text) {
        // 自定义加密逻辑
        return customEncrypt(text);
    }

    @Override
    public String decrypt(String text) {
        // 自定义解密逻辑
        return customDecrypt(text);
    }

    private String customEncrypt(String text) {
        // 实现自定义加密算法
        return Base64.getEncoder().encodeToString(text.getBytes());
    }

    private String customDecrypt(String text) {
        // 实现自定义解密算法
        return new String(Base64.getDecoder().decode(text));
    }
}
```

### Q4: 性能监控数据如何导出？

**答案**: 监控数据会自动暴露到Prometheus端点，可以配置Grafana等可视化工具：

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'nop-orm'
    scrape_interval: 15s
    static_configs:
      - targets: ['localhost:9090']
```

## 相关文档

- [ORM架构详解](./02-architecture/orm-architecture.md) - ORM架构完整文档
- [数据访问层开发](./data-access.md) - 数据访问层开发指南
- [服务层开发](./service-layer.md) - 服务层开发指南
- [IEntityDao使用指南](./entitydao-usage.md) - IEntityDao使用完整指南
- [QueryBean使用指南](./querybean-guide.md) - QueryBean使用完整指南
- [FilterBeans使用指南](./filterbeans-guide.md) - FilterBeans使用完整指南
- [事务管理指南](../04-core-components/transaction.md) - 事务管理完整指南
- [测试规范](../07-best-practices/testing.md) - 测试规范
- [代码风格规范](../07-best-practices/code-style.md) - 代码风格规范

## 总结

NopORM提供了丰富的高级特性，包括：

1. **分库分表**：内置支持，通过IShardSelector实现自动路由，BizModel无需编写分片代码
2. **字段加密**：内置支持，通过TAG_ENC标记，完全透明
3. **数据脱敏**：内置支持，通过TAG_MASKED + ui:maskPattern，全方位自动
4. **性能监控**：内置支持，集成Prometheus等监控工具
5. **Hook机制**：内置支持，IPropGetMissingHook、IPropSetMissingHook、IPropMakeMissingHook

这些特性都是内置的，无需额外依赖第三方框架，可以大幅提升开发效率和运行时性能。
