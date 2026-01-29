# Performance (docs-for-ai)

`docs-for-ai` 不维护“行业通用性能优化指南”（索引、分页、N+1、缓存、事务边界等）。这类内容容易在缺少项目/模块上下文时误导，而且很快过期。

这里只保留 Nop 特有、且对写代码/写文档有约束意义的入口与原则。

## 你应该看哪里

- Nop 与传统框架差异、以及常见误用清单：`docs-for-ai/getting-started/nop-vs-traditional-frameworks.md`
- 与持久层/查询相关的权威文档入口：`docs-for-ai/getting-started/dao/entitydao-usage.md`
- 若你在 docs 中引用缓存/事务/ORM 注解/接口：**必须先在仓库中搜索确认真实存在与真实用法**。

## docs-for-ai 的性能相关写作约束（只保留 Nop 特有）

1. **不要把 Spring/第三方框架的缓存/事务/监控注解当作 Nop 默认**（例如 `@Transactional` 的语义、`@Cacheable` 的参数等，在不同体系下可能不同）。
2. 文档示例要能在仓库中“搜到来源”（注解/类/方法要可验证）。
3. 性能结论必须基于可复现的基准/压测或仓库内已有 benchmark；不要在 docs-for-ai 写没有依据的数值或结论。

    }
  }
}
```

## 并发优化

### 1. 读写分离

配置主从数据库：

```yaml
# application.yaml
datasource:
  master:
    url: jdbc:mysql://master-host:3306/nop
    username: root
    password: password
  slave:
    url: jdbc:mysql://slave-host:3306/nop
    username: root
    password: password
```

```java
// 查询操作使用从库
@BizQuery
public User getUser(String userId) {
    return slaveDao.getEntityById(userId);
}

// 写操作使用主库
@BizMutation
@Transactional
public User createUser(User user) {
    return masterDao.saveEntity(user);
}
```

### 2. 连接池优化

配置合适的连接池参数：

```yaml
datasource:
  hikari:
    maximum-pool-size: 20
    minimum-idle: 5
    idle-timeout: 600000
    max-lifetime: 1800000
    connection-timeout: 30000
```

## 监控和诊断

### 1. 慢查询日志

记录慢查询：

```java
public class SlowQueryInterceptor implements IOrmInterceptor {
    @Override
    public void afterQuery(String sql, long costTime) {
        if (costTime > 1000) { // 超过1秒
            log.warn("Slow query: {} ({}ms)", sql, costTime);
        }
    }
}
```

### 2. 性能监控

使用Micrometer监控性能：

```java
public class PerformanceMonitor {
    private final MeterRegistry registry;

    @BizQuery
    public User getUser(String userId) {
        Timer.Sample sample = Timer.start(registry);
        try {
            return dao().getEntityById(userId);
        } finally {
            sample.stop(Timer.builder("user.get")
                .description("Time to get user")
                .register(registry));
        }
    }
}
```

### 3. 缓存命中率

监控缓存命中率：

```java
public class CacheMonitor {
    public void printCacheStats() {
        CacheStats stats = cacheManager.getStats("userCache");
        log.info("Cache hit rate: {}%", stats.getHitRate() * 100);
        log.info("Cache size: {}", stats.getSize());
    }
}
```

## 性能测试

### 1. 压力测试

使用JMeter进行压力测试：

1. 创建测试计划
2. 模拟多用户并发访问
3. 监控响应时间、吞吐量
4. 识别性能瓶颈

### 2. 性能分析

使用JProfiler或VisualVM分析：

1. 启动性能分析
2. 执行典型场景
3. 分析热点方法
4. 优化慢代码

## 常见性能问题

| 问题 | 原因 | 解决方案 |
|------|------|---------|
| N+1查询 | 循环中查询关联数据 | 使用JOIN或批量加载 |
| 大事务 | 事务包含过多操作 | 减小事务边界 |
| 缺少索引 | 常用查询字段无索引 | 添加索引 |
| 过度缓存 | 缓存了不常修改的数据 | 合理使用缓存 |
| 循环查询 | 在循环中执行数据库查询 | 改用批量查询 |
| 深层关联 | 多层JOIN查询 | 优化查询或使用缓存 |
| 全表扫描 | 查询条件不使用索引 | 添加索引或优化查询 |

## 相关文档

- [ORM架构文档](../architecture/backend/orm-architecture.md)
- [IEntityDao使用指南](../getting-started/dao/entitydao-usage.md)
- [QueryBean使用指南](../getting-started/dao/querybean-guide.md)
- [事务管理指南](../getting-started/core/transaction-guide.md)

> 注：缓存相关文档请以核心组件目录下现有文档为准（本仓库中未提供 `cache-guide.md`）。

## 总结

Nop Platform性能优化是一个系统工程，需要从多个层面综合考虑：

1. **数据库优化**: 索引、批量操作、字段选择
2. **查询优化**: 避免N+1、使用合适的数据结构
3. **缓存优化**: 合理使用二级缓存和查询缓存
4. **事务优化**: 控制事务边界、避免事务中IO
5. **ORM优化**: 合理选择加载策略、使用DTO
6. **监控诊断**: 记录慢查询、性能监控、持续优化


遵循这些最佳实践，可以构建高性能、可扩展的Nop Platform应用系统。
