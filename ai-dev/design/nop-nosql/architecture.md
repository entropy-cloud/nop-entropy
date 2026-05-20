# Nop NoSQL 架构设计

> Status: active
> Created: 2026-05-20
> Updated: 2026-05-20

## 1. 设计哲学

nop-nosql 的核心设计哲学是 **业务语义优先**：应用代码通过队列、限流器、锁、排行榜等业务概念与 NoSQL 交互，而非直接调用 Redis 数据结构命令。

### 分层模型

nop-nosql 分为三层，每层有不同的用户群体：

```
应用代码（业务开发者）
  ↓
┌─────────────────────────────────────────┐
│  业务模式层                               │  应用代码主要使用这一层
│  INosqlQueue / INosqlLock / INosqlRateLimiter
│  INosqlRanking / INosqlCounter / INosqlSessionStore
│  INosqlCache (ICache 适配)               │
├─────────────────────────────────────────┤
│  原语层                                   │  基础设施代码、框架内部使用
│  INosqlKeyValueOperations (extends IAsyncMap)
│  INosqlHashOperations / INosqlListOperations
│  INosqlSetOperations / INosqlZSetOperations
│  IMessageService                         │
├─────────────────────────────────────────┤
│  驱动实现层                               │  内部细节，不暴露
│  LettuceMessageService / LettuceRedisConnectionProvider
│  Lua Scripts / PrefixTextCodec           │
└─────────────────────────────────────────┘
```

**关键原则**：
- 应用代码通过 `INosqlService` 的工厂方法获取业务模式对象（`queue(key)`、`lock(key)`）
- 原语层（`hashOps(key)`、`listOps(key)`）供基础设施代码使用，应用代码通常不直接接触
- 驱动细节完全不暴露

### 与 Spring Data Redis 的关键差异

| 维度 | Spring Data Redis | nop-nosql |
|------|------------------|-----------|
| 抽象起点 | Redis 数据结构（Value/Hash/List/Set/ZSet） | 业务模式（队列/锁/限流器/排行榜） |
| 操作入口 | `RedisTemplate.opsForValue().set()` | `service.counter("hits").increment()` |
| 数据结构可见性 | 用户必须理解 Redis 数据结构 | 数据结构是实现细节，用户面对业务概念 |
| 原子操作 | 用户手写 Lua 脚本 | 内置为业务模式方法（`lock.tryLock()`、`rateLimiter.tryAcquire()`） |
| 同步/异步 | 同步为主，Reactive 为单独体系 | 同步 + 异步双轨 |
| 缓存集成 | `RedisCacheManager` 对接 Spring Cache | `NosqlCache` 实现 Nop `ICache` |
| 配置模型 | 暴露驱动级参数（驱动选择、连接池实现、SSL bundle） | `RedisConfig` 仅暴露连接必需参数 |

## 2. 模块结构

```
nop-nosql/
├── nop-nosql-core/        # 抽象层（不含驱动依赖）
│   ├── INosqlService          # 顶层服务入口 + 业务模式工厂
│   │
│   │  ── 业务模式接口 ──
│   ├── INosqlQueue            # 队列（FIFO/LIFO）
│   ├── INosqlLock             # 分布式锁
│   ├── INosqlRateLimiter      # 令牌桶限流器
│   ├── INosqlRanking          # 排行榜
│   ├── INosqlCounter          # 原子计数器
│   ├── INosqlSessionStore     # 会话存储
│   │
│   │  ── 原语接口 ──
│   ├── INosqlKeyValueOperations # KV 操作（extends IAsyncMap）
│   ├── INosqlHashOperations   # Hash 操作（extends INosqlKeyValueOperations）
│   ├── INosqlListOperations   # 列表操作
│   ├── INosqlSetOperations    # 集合操作
│   ├── INosqlZSetOperations   # 有序集合操作
│   │
│   │  ── 支撑类 ──
│   ├── NosqlCache             # ICache → INosqlKeyValueOperations 适配
│   ├── RedisConfig            # 连接配置（配置层使用，不暴露到业务层）
│   ├── RedisScripts           # Lua 脚本常量
│   └── resources:nop/redis/*.lua  # Lua 脚本资源
│
├── nop-nosql-lettuce/     # Lettuce 驱动实现
│   ├── LettuceMessageService  # INosqlService 的 Lettuce 实现（主要实现类）
│   ├── LettuceNosqlService    # 空类（预留，未使用）
│   ├── LettuceRedisConnectionProvider  # 连接管理（池化、生命周期）
│   ├── LettuceExecutor        # Lua 脚本执行器
│   ├── LettuceHelper          # 类型转换工具
│   └── PrefixTextCodec        # 值序列化（基于 PrefixEncodeHelper）
```

**模块依赖方向**：

- `nop-nosql-core` → `nop-commons`（`IAsyncMap`、工具类）、`nop-core`（PrefixEncodeHelper 序列化）、`nop-dao`
- `nop-nosql-lettuce` → `nop-nosql-core`、`lettuce-core`
- 上层业务模块 → `nop-nosql-core`（仅依赖抽象层，不依赖具体驱动）

**IoC 注册**：当前两个模块均未包含 beans.xml 注册文件。IoC 注册由使用方（如 nop-quarkus-web 或具体应用）负责。

## 3. 核心设计决策

### 3.1 三层分离：业务模式 / 原语 / 驱动

**选择了**：将接口分为三层——业务模式层（Queue/Lock/Ranking）给应用代码，原语层（KV/Hash/List）给基础设施代码，驱动层给框架内部。

**理由**：应用开发者关心的是"往队列里放消息"、"获取一把锁"、"查排行榜前 10 名"，不是"RPUSH 到 Redis List"、"SET NX PX"或"ZREVRANGE"。三层分离让每层面向不同的用户群体，接口命名和抽象粒度都更贴合该群体的心智模型。

**拒绝了**：像 Spring 那样只有一层 RedisTemplate，让所有用户面对数据结构 API。Spring 的 ValueOperations/HashOperations/ListOperations 本质是 Redis 命令映射，用户必须知道 Redis 才能使用。

### 3.2 业务模式通过 INosqlService 工厂方法获取

**选择了**：通过 `INosqlService` 的工厂方法获取业务模式对象，如 `service.queue("email-queue")`、`service.lock("order:123")`。

**理由**：
- 与原语层的 key 绑定模式一致（`service.hashOps("user:123")`）
- 单一入口，用户只需要注入 `INosqlService`
- 返回的对象是轻量的（持有 key 引用），无需缓存

**拒绝了**：为每个业务模式定义独立的 Spring Bean（如 `INosqlQueue emailQueue`）。这会导致 bean 数量爆炸，且 key 管理分散。

### 3.3 INosqlHashOperations 继承 INosqlKeyValueOperations

**选择了**：`hashOps("user:123")` 返回的对象实现 `INosqlKeyValueOperations`——它是对该 Hash key 下 field→value 映射的完整封装，语义等价于 `IAsyncMap<String, Object>`。

**理由**：Hash 对象已持有 Redis key，调用 `hash.get("name")` 等价于对该 Hash 的 field 操作。这与 KV 操作的 `map.get("user:123")` 在 Map 语义上完全一致——都是"通过 string key 存取 value"的映射。继承使 `NosqlCache` 可以零成本适配两者。

**拒绝了**：让 Hash 和 KV 平级独立（如 Spring 的做法）。Spring 平级是因为 `HashOperations` 每次调用要传 key，无法复用 Map 语义。Nop 通过绑定 key 消除了这个冗余。

### 3.4 同步 + 异步双轨 API

**选择了**：同时提供同步方法（`get`/`put`）和异步方法（`getAsync`/`putAsync`）。

**理由**：大量业务场景只需同步调用，强制异步增加使用复杂度。

**已知差异**：LettuceMessageService 中部分同步方法是桩实现（返回 false/null），异步方法正常工作。**当前建议：优先使用异步方法**。

**拒绝了**：纯异步 API（类似 Reactive Redis）。Nop 的 IoC 容器和业务代码以同步模型为主。

### 3.5 RedisConfig 是配置层概念，与驱动实现绑定

**选择了**：`RedisConfig` 包含 host/port/password/database/connectionTimeout 等连接参数，以及 clusterNodes/masterName 等部署参数。配置类天然是驱动实现绑定的——它不暴露到业务层。

**理由**：配置层的职责是将部署信息传递给驱动实现，必然包含后端特定的参数（如 Redis 的 database 编号）。这和 JDBC 的 DataSource 配置包含数据库特定参数是同样的道理。业务层通过 `INosqlService` 接口交互，完全不接触 `RedisConfig`。

**已知限制**：当前 `LettuceRedisConnectionProvider.buildRedisURI()` 存在以下未连通的配置字段：
- `clusterNodes`：未使用，始终通过 host/port 构建单节点 URI
- `masterName`：配置了 Sentinel master ID，但未配置 Sentinel 节点地址列表
- `useSsl`：配置字段存在但未在 Lettuce URI 构建中消费

当前仅支持非加密的单机直连模式。

### 3.6 PrefixTextCodec 序列化而非 JDK 序列化

值序列化使用 `PrefixEncodeHelper`（类型前缀 + 文本编码）。

**理由**：可读、可调试、跨语言兼容、无安全风险。与 Nop 平台统一的序列化策略一致。

### 3.7 Lua 脚本集中管理于 nop-nosql-core

所有 Lua 脚本存放在 `nop-nosql-core` 的 `resources:nop/redis/` 下。业务模式层的原子语义通过 Lua 脚本实现，驱动层只负责执行。

**已知限制**：`LettuceExecutor` 当前仅支持 `evalsha`，不支持 `eval` 降级。

### 3.8 连接池使用 RoundRobinSupplier

`LettuceRedisConnectionProvider` 使用 Nop 的 `RoundRobinSupplier` 管理连接，默认池大小为 1。Lettuce 是异步驱动，单连接即可支撑高并发。

## 4. 业务模式层设计

业务模式层是应用代码的主要交互层。每个业务模式通过 `INosqlService` 的工厂方法获取，返回的对象持有特定的 key 前缀，提供面向业务语义的操作。

**同步/异步约定**：业务模式层的每个操作均提供同步和异步两个版本，异步版本统一加 `Async` 后缀（如 `enqueue`/`enqueueAsync`），与原语层约定一致。下文表格中仅列出同步版本名称，异步版本通过加后缀获得。

### 4.1 INosqlQueue — 队列

获取方式：`service.queue("email-queue")`

底层实现：Redis List（RPUSH + LPOP）

| 操作 | 语义 | Redis 原语 |
|------|------|-----------|
| enqueue(item) | 入队一个元素 | RPUSH |
| enqueueBatch(items) | 批量入队 | RPUSH (multi) |
| dequeue() | 出队一个元素，空则返回 null | LPOP |
| dequeueBatch(maxCount) | 批量出队 | LPOP count (Redis 6.2+) 或 Lua |
| peek() | 查看队首元素但不移除 | LINDEX 0 |
| size() | 队列长度 | LLEN |
| clear() | 清空队列 | DEL |

**设计约束**：
- 队列是纯 FIFO 语义。如果需要优先级，应使用多个队列而非优先级队列
- dequeueBatch 在 Redis 6.2 以下版本需要通过 Lua 脚本实现原子性
- 不提供阻塞式 dequeue（BLPOP），因为 Nop 平台以非阻塞异步模型为主

### 4.2 INosqlLock — 分布式锁

获取方式：`service.lock("order:123")`

底层实现：KV（SET NX PX）+ Lua（CAS 释放）

| 操作 | 语义 | 实现方式 |
|------|------|---------|
| tryLock(leaseTimeMs) | 尝试获取锁，立即返回。成功则持有 leaseTime 毫秒后自动释放 | SET key uuid NX PX |
| unlock() | 释放锁。仅当调用者是锁的持有者时才释放（CAS） | Lua: GET 比较 + DEL |
| isHeld() | 当前实例是否持有锁 | 本地状态判断 |

**设计约束**：
- 锁的值是调用方实例的唯一标识（如 UUID），防止误解锁
- unlock 必须通过 Lua 脚本实现 CAS 释放（GET + 比较 + DEL 必须原子），对应 `remove_if_match.lua`
- 锁自动过期（leaseTime），防止持有者崩溃导致死锁
- 不提供阻塞等待（`lock(waitTime)`）。阻塞等待需要自旋或 BLPOP，增加复杂度且容易误用。需要等待的场景应通过业务层的重试机制实现

**拒绝了**：
- 可重入锁：需要维护持有计数，实现复杂度高（参考 Redisson 的 RLock）。首版不支持，按需增加
- 看门狗自动续期：需要后台线程定期续约，引入线程管理复杂度。首版不支持，由调用方按需续期

### 4.3 INosqlRateLimiter — 限流器

获取方式：`service.rateLimiter("api:limit:user:123", new RateLimiterConfig(100, 200))`

其中 RateLimiterConfig 包含 rate（每秒填充令牌数）和 capacity（桶容量，即最大突发量）。

底层实现：`rate_limit.lua`（令牌桶算法，已存在）

| 操作 | 语义 | 返回值 |
|------|------|--------|
| tryAcquire(permits) | 尝试消费 permits 个令牌 | RateLimitResult: allowed + remainingTokens |
| getAvailableTokens() | 查询当前可用令牌数（不消费） | long |

**算法语义**（令牌桶）：
1. 桶初始满（令牌数 = capacity）
2. 每秒按 rate 填充令牌，不超过 capacity
3. 请求消费 N 个令牌：如果桶内 >= N，消费成功；否则失败
4. 自动清理：令牌数和上次刷新时间各设 TTL = fill_time * 2

**设计约束**：
- **配置一致性要求**：同一 key 的不同调用方必须使用相同的 rate/capacity 配置。`rate_limit.lua` 不会校验与历史调用的一致性——如果同一 key 使用不同配置，令牌状态会产生既不遵循 A 配置也不遵循 B 配置的中间结果，限流行为不可预测。这属于调用方契约违反。
- 实现建议：`RateLimiterConfig` 应实现 `equals`/`hashCode`，调用方应通过共享常量引用配置，避免不同服务实例使用不同硬编码值
- 未来可选增强：将 rate/capacity 固化到 Redis key，首次调用时写入，后续调用校验一致性
- 时间戳由调用方提供（`System.currentTimeMillis()`），避免 Redis 服务端时钟与客户端不一致

### 4.4 INosqlRanking — 排行榜

获取方式：`service.ranking("game:scores")`

底层实现：Redis Sorted Set (ZSet)

| 操作 | 语义 | Redis 原语 |
|------|------|-----------|
| add(member, score) | 添加或更新成员分数 | ZADD |
| incrementScore(member, delta) | 原子增减分数 | ZINCRBY |
| getRank(member) | 获取排名（0-based，分数最高 = 0） | ZREVRANK |
| getScore(member) | 获取分数 | ZSCORE |
| getTopN(n) | 获取前 N 名（含 member、score、rank） | ZREVRANGE + ZREVRANK |
| getAround(member, distance) | 获取某成员前后各 distance 名 | ZREVRANK → ZREVRANGE |
| size() | 总人数 | ZCARD |
| remove(member) | 移除成员 | ZREM |

**设计约束**：
- 排名是降序（分数最高排第 0 名），符合"排行榜"直觉
- getTopN 和 getAround 返回 RankingEntry（含 member、score、rank 三个字段）
- 不提供同分同排名（dense ranking）语义——这需要 Lua 脚本实现，首版使用 Redis 原生排名（competition ranking，同分不同名）

### 4.5 INosqlCounter — 原子计数器

获取方式：`service.counter("page:views:home")`

底层实现：KV（INCRBY + TTL）

| 操作 | 语义 | Redis 原语 |
|------|------|-----------|
| increment(delta) | 原子增减 delta（支持负数） | INCRBY |
| get() | 获取当前值 | GET |
| getAndIncrement(delta) | 原子地获取当前值然后增减 | Lua（GET + INCRBY 原子执行，返回旧值） |
| reset(value) | 重置为指定值 | SET |
| getAndReset() | 原子地获取当前值然后重置为 0 | GETSET |

**设计约束**：
- increment 的 delta 是 long 类型，支持正负
- 首次 increment 时如果 key 不存在，Redis 自动从 0 开始
- 不提供带 TTL 的 increment（INCRBY + EXPIRE 不是原子的），需要的话应通过 Lua 脚本

### 4.6 INosqlSessionStore — 会话存储

获取方式：`service.sessionStore("session")`

内部约定：实际 Redis key 为 `{prefix}:{sessionId}`，如 `session:abc123`。

底层实现：Redis Hash（field-value）+ TTL

| 操作 | 语义 | Redis 原语 |
|------|------|-----------|
| get(sessionId) | 获取会话全部字段 | HGETALL |
| getField(sessionId, field) | 获取单个字段 | HGET |
| set(sessionId, data, ttlMs) | 创建/覆盖会话（含 TTL） | HMSET + PEXPIRE |
| setField(sessionId, field, value) | 更新单个字段 | HSET |
| touch(sessionId, ttlMs) | 刷新 TTL（续期） | PEXPIRE |
| remove(sessionId) | 销毁会话 | DEL |
| exists(sessionId) | 会话是否存在 | EXISTS |

**设计约束**：
- Hash 天然支持按字段读写，比 KV 存储整个序列化对象更灵活（可以只更新一个字段而不必读取-修改-写回整个对象）
- TTL 在每次 set 时设置，touch 时刷新
- key 前缀在获取时绑定，不同业务（session vs. temp-token）使用不同前缀
- **原子性说明**：`set()` 的 HMSET + PEXPIRE 是两个独立命令，非原子执行。在极端情况（写入数据后、设置 TTL 前连接断开）下会产生无 TTL 的会话数据。建议实现时通过 Lua 脚本或 Pipeline 缩小窗口，运维侧可设置 Redis 最大 TTL 兜底策略

## 5. INosqlService 接口设计

`INosqlService` 是用户面对的唯一入口，同时提供原语层和业务模式层的工厂方法：

```
INosqlService extends INosqlKeyValueOperations
  │
  ├── 业务模式工厂方法
  │   queue(key)           → INosqlQueue
  │   lock(key)            → INosqlLock
  │   rateLimiter(key, config) → INosqlRateLimiter
  │   ranking(key)         → INosqlRanking
  │   counter(key)         → INosqlCounter
  │   sessionStore(prefix) → INosqlSessionStore
  │
  ├── 原语工厂方法
  │   hashOps(key)         → INosqlHashOperations
  │   listOps(key)         → INosqlListOperations
  │   setOps(key)          → INosqlSetOperations
  │   zSetOps(key)         → INosqlZSetOperations
  │
  └── 消息
      getMessageService()  → IMessageService
```

**使用示例**（业务语义）：

```
// 队列：发送邮件
service.queue("email-queue").enqueueAsync(emailTask);

// 锁：防止重复下单
lock = service.lock("order:123");
if (lock.tryLock(30000)) {
    try { processOrder(); } finally { lock.unlock(); }
}

// 限流：API 频率控制
result = service.rateLimiter("api:limit:" + userId, CONFIG).tryAcquire(1);
if (!result.allowed) throw new RateLimitException();

// 排行榜
ranking = service.ranking("game:scores");
ranking.incrementScore("player1", 100);
top10 = ranking.getTopN(10);

// 计数器
service.counter("page:views:home").increment(1);

// 会话
store = service.sessionStore("session");
store.set(sessionId, userData, 30min);
store.setField(sessionId, "lastAccess", now);
```

## 6. 实现状态

### 第一层：业务模式层（已实现）

6 个业务模式接口及 Lettuce 实现已完成。通过 `INosqlService` 的工厂方法获取：

| 模式 | 接口 | Lettuce 实现 | 工厂方法 | 状态 |
|------|------|-------------|---------|------|
| 分布式锁 | INosqlLock | LettuceLock | `lock(key)` | ✅ SET NX PX + Lua CAS |
| 原子计数器 | INosqlCounter | LettuceCounter | `counter(key)` | ✅ INCRBY + GETSET |
| 队列 | INosqlQueue | LettuceQueue | `queue(key)` | ✅ RPUSH + LPOP |
| 限流器 | INosqlRateLimiter | LettuceRateLimiter | `rateLimiter(key, config)` | ✅ rate_limit.lua |
| 排行榜 | INosqlRanking | LettuceRanking | `ranking(key)` | ✅ ZADD/ZREVRANGE |
| 会话存储 | INosqlSessionStore | LettuceSessionStore | `sessionStore(prefix)` | ✅ HSET/HGETALL + PEXPIRE |

### 第二层：原语层（已实现）

**KV 操作**（`INosqlKeyValueOperations`，基于 LettuceMessageService）：

| 方法 | 同步 | 异步 | 说明 |
|------|------|------|------|
| get/getAll/put/putAll/remove/removeAll/containsKey/clear/computeIfAbsent/getSize | ✅ | ✅ | |
| putIfAbsent / getAndSet / removeIfMatch | ✅ | ✅ | 同步委托到异步 join() |
| forEachEntry | ⚠️ 空实现 | ⚠️ return null | SCAN 实现待优化 |
| putExAsync / getExAsync / putIfAbsentExAsync / getAndSetExAsync | — | ✅ | 带 TTL 操作 |
| setTimeoutAsync / getTimeoutAsync | — | ✅ | TTL 管理 |
| putIfAbsentOrMatchExAsync | — | ✅ | 接入 put_if_absent_or_match.lua |

**数据结构操作**：

| 接口 | 实现状态 |
|------|---------|
| INosqlHashOperations | ✅ LettuceHashOperations（HGET/HSET/HGETALL/HSETNX + TTL） |
| INosqlListOperations | ✅ LettuceListOperations（RPUSH/LPOP/LRANGE/LLEN/LTRIM） |
| INosqlSetOperations | ✅ LettuceSetOperations（SADD/SREM/SISMEMBER/SCARD/SMEMBERS/SPOP） |
| INosqlZSetOperations | ✅ LettuceZSetOperations（ZADD/ZREM/ZSCORE/ZRANK/ZREVRANK/ZCARD/ZINCRBY/ZREVRANGE） |

### 第三层：驱动层（Lettuce 基本可用）

- 连接管理：✅ LettuceRedisConnectionProvider（RoundRobin 池化）
- 序列化：✅ PrefixTextCodec
- Lua 执行：✅ LettuceExecutor（仅 evalsha）
- 消息服务：⚠️ getMessageService() 返回 null

### Lua 脚本清单

| 脚本 | 用途 | 被代码引用 |
|------|------|-----------|
| remove_if_match.lua | CAS 删除 | ✅ LettuceLock.unlock / LettuceMessageService.removeIfMatchAsync |
| rate_limit.lua | 令牌桶限流器 | ✅ LettuceRateLimiter.tryAcquire |
| get_and_expire.lua | 读取并刷新 TTL | ✅ RedisScripts 已注册，待业务场景使用 |
| get_and_set.lua | 原子 get-and-set（支持 TTL） | ✅ RedisScripts 已注册，待业务场景使用 |
| put_if_absent_or_match.lua | CAS 写入 | ✅ LettuceMessageService.putIfAbsentOrMatchExAsync |

## 7. 与 Spring Data Redis 的能力对比

| 能力 | Spring Data Redis | nop-nosql |
|------|------------------|-----------|
| **业务模式** | | |
| 队列 | ❌ 需自己封装 List 操作 | ✅ INosqlQueue |
| 分布式锁 | ❌ 需第三方（Redisson等） | ✅ INosqlLock |
| 限流器 | ❌ 需第三方（bucket4j等） | ✅ INosqlRateLimiter |
| 排行榜 | ❌ 需自己封装 ZSet 操作 | ✅ INosqlRanking |
| 原子计数器 | ❌ 需自己封装 INCRBY | ✅ INosqlCounter |
| 会话存储 | ❌ 需自己封装 Hash+TTL | ✅ INosqlSessionStore |
| **原语** | | |
| KV 操作 | ✅ ValueOperations | ✅ 异步 + 同步双轨 |
| Hash/List/Set/ZSet | ✅ 各 Operations | ✅ 完整实现 |
| Stream / Geo | ✅ | ❌ 按需 |
| **基础设施** | | |
| Cache 集成 | ✅ RedisCacheManager | ✅ NosqlCache |
| Pub/Sub | ✅ | 🔨 接口声明 |
| 脚本执行 | ✅ eval/evalsha 自动降级 | 🔨 evalsha only |
| Sentinel / Cluster | ✅ | ⚠️ 字段存在未连通 |
| 多驱动 | ✅ Lettuce/Jedis | 🔨 架构支持，仅 Lettuce |

**核心差异总结**：Spring Data Redis 提供完整的 Redis 命令映射，业务模式需要用户自己组合或引入第三方库。nop-nosql 反过来——内置业务模式，Redis 数据结构是实现细节。

## 8. 约束和边界

### 模块边界

- **nop-nosql-core 不依赖任何 Redis 驱动** — 驱动级 import 只出现在实现模块
- **INosqlService 是用户面对的唯一入口** — 用户不直接接触驱动 API
- **业务模块只依赖 nop-nosql-core** — 不依赖具体驱动实现
- **IoC 注册不是 nosql 模块自身的职责** — 由使用方负责 bean 注册
- **RedisConfig 不暴露到业务层** — 它是配置层概念，与驱动实现绑定

### 数据流方向

```
业务代码 → INosqlService
              │
              ├── 业务模式: queue(key) / lock(key) / ranking(key) ...
              │       ↓
              │   原语层: List / KV / ZSet / Hash
              │       ↓
              │   驱动实现（LettuceMessageService）
              │       ↓
              │   连接管理（LettuceRedisConnectionProvider）
              │       ↓
              │   Lettuce StatefulRedisClusterConnection
              │
              └── 原语层: hashOps(key) / listOps(key) ...
                      ↓ （同上）
```

数据流是单向的，业务模式层组合原语层，原语层由驱动层实现。

### 不可违反的约束

1. core 模块的 import 中不得出现 lettuce、jedis 等驱动包
2. 业务模式接口不得暴露 Redis 命令名或数据结构名（如 zadd、lpush）
3. 序列化策略统一使用 PrefixEncodeHelper，不得引入 JDK 序列化
4. 业务模式的操作语义必须由原语层的标准操作组合实现，不得绕过原语层直接调用驱动 API

## 9. 拒绝的替代方案

### 拒绝：直接包装 RedisTemplate

引入 Spring 依赖，与 Nop IoC 理念冲突。泛型签名在动态类型场景下笨重。无法控制 API 设计方向。

### 拒绝：完全自研驱动层

工作量巨大且无业务价值。Lettuce 已经是成熟的高性能异步驱动。

### 拒绝：Reactive 响应式 API

Nop 的 IoC 和业务代码以同步模型为主。`CompletionStage` 异步 API 已满足异步场景需求。

### 拒绝：可重入锁和看门狗续期作为首版

可重入锁需要维护持有计数和重入计数，实现复杂度高。看门狗需要后台线程定期续约，引入线程管理复杂度。首版聚焦 tryLock + unlock + 自动过期，覆盖 80% 场景。如需要可重入，后续增加 `INosqlReentrantLock` 接口。

### 拒绝：阻塞式队列操作

BLPOP/BRPOP 是阻塞命令，会占用连接资源。Nop 平台以异步非阻塞模型为主，阻塞 dequeue 与整体架构风格不一致。需要消费队列的场景应使用异步 poll + 定时重试模式。
