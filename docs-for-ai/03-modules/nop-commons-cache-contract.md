# nop-commons 缓存契约

> Status: active
> Owner: 本文档是 `io.nop.commons.cache` 包对外契约的唯一权威描述
> Last Reviewed: 2026-10-05

## `ICache` 默认方法的原子性契约

`ICache`（`nop-kernel/nop-commons/src/main/java/io/nop/commons/cache/ICache.java`）的两个默认方法有明确的并发契约：

| 方法 | 契约 | 默认实现机制 |
|---|---|---|
| `putIfAbsent(K,V)` | javadoc 明文要求 check-then-act 原子性：**并发对同一 key 调用时只有一个返回 true** | `synchronized(this)`（锁实例）。性能敏感实现应覆写 |
| `computeIfAbsent(K, Function)` | 单一胜者语义：并发时只有一个线程的写入生效，**所有线程最终拿到同一个值**；mapping 并发下可能被多次执行，但仅一个结果生效；返回 `null` 不写入 | 走 `putIfAbsent` 原子路径，竞争失败方回读先写入者的值 |

## 实现者矩阵（5 类）

| 实现 | `putIfAbsent` | `computeIfAbsent` | `getConfig()` | `refreshConfig()` | 备注 |
|---|---|---|---|---|---|
| `LocalCache` | 覆写：Caffeine `asMap().putIfAbsent`（原子） | 覆写：Caffeine `get(key, fn)`（mapping 恰执行一次） | 返回真实 `CacheConfig` | 真重建 Caffeine；支持配置热刷新 | 有界 + metrics + W-TinyLFU |
| `MapCache` | 覆写：`ConcurrentHashMap` | 覆写：`map.computeIfAbsent` | — | — | session 级常用 |
| `NosqlCache` | 覆写：委托 Redis `ops` | 覆写：委托 Redis `ops`（服务端原子） | — | — | |
| `JavaxCache` | 覆写：JCache `cache.putIfAbsent`（原子） | 覆写：JCache `getAndPut`（原子，单一胜者） | **恒返回 `null`** | **有明确语义的空实现**（见下） | 包装外部 JCache |
| `HazelcastCache` | 继承 `JavaxCache` | 继承 `JavaxCache` | 冗余覆写返回 `null` | 继承 | 分布式 |

**`JavaxCache.refreshConfig()` 的空体是有意设计**：其配置由底层 JCache 实现（Hazelcast/Ehcache）自管，nop 侧 `CacheConfig` 不适用。**调用方应以 `getConfig() == null` 判断该实现是否支持 nop 侧配置**，不要假定 `refreshConfig()` 一定有效果。

## 容量/过期配置能力

- 只有 `LocalCache` / `LocalCacheProvider` 具备 nop 侧的 `maximumSize` / `expireAfterWrite` / `expireAfterAccess` 配置（`CacheConfig`）与热刷新。
- `ICacheProvider` SPI 本身不含配置方法；判断 provider 是否可重配置用 `instanceof LocalCacheProvider`。
- ORM 装配点：`OrmSessionFactoryBean.refreshConfig()` 对 `sessionFactory.getGlobalCache()` 做 `instanceof LocalCacheProvider` 判定后重配置；非 `LocalCacheProvider` 时 `LOG.warn("nop.orm.global-cache-config-refresh-not-supported")`。

## `IConfigRefreshable` 驱动链

`IConfigRefreshable`（`nop-api-core`）的实现由 IoC 容器在配置变化时反射调用：
`DefaultBeanClassIntrospection` 按 `IConfigRefreshable` 登记 → `BeanDefinition.refreshConfigMethod.call0(...)`。

⚠️ **历史坑（2026-10-05 修复）**：`OrmSessionFactoryBean.refreshConfig()` 曾以 bean 自身的 `getGlobalCache() == null` 为守卫，而 IoC 装配下该字段经 `@Inject` setter 注入**恒非 null**，导致守卫恒假、global cache 配置热刷新在所有 IoC 部署中静默失效。判定必须以 `sessionFactory.getGlobalCache()`（实际被消费的一方）为准。
