# 302 nop-nosql-remaining

> Plan Status: draft
> Last Reviewed: 2026-07-21
> Source: code audit of `nop-nosql` module; known gaps documented in `ai-dev/design/nop-nosql/architecture.md` §6
> Related: `LettuceMessageService.java`, `LettuceRedisConnectionProvider.java`, `RedisConfig.java`, `INosqlService.java`

## Purpose

补齐 nop-nosql 模块的三个已知功能缺口：SSL 连接支持、`forEachEntry` 实现、Pub/Sub 消息服务。更新 README 进度表反映实际完成度。

## Current Baseline

- 所有 11 个接口已有完整的 Lettuce 实现，sync+async 双轨 API
- 6 个业务模式（Queue/Lock/RateLimiter/Ranking/Counter/SessionStore）+ 4 个原语操作（KV/Hash/List/Set/ZSet）
- 41 个测试用例（34 单机 + 7 集群），运行在真实 Redis 容器上
- **已知缺口**：
  - `getMessageService()` 抛 `UnsupportedOperationException` — Pub/Sub 未实现
  - `forEachEntry()` 抛异常 / `forEachEntryAsync()` 静默返回 null — SCAN 未实现
  - `RedisConfig.useSsl` = true 时未在 Lettuce URI 构建中连接 — SSL 配置被忽略
  - LettuceExecutor 已有 eval 降级（代码已完成，设计文档未更新）
- README 进度表标注 nop-nosql = 0%（严重偏低）
- 设计文档 `architecture.md` 中 §3.7 的 "仅 evalsha" 与代码（LettuceExecutor 已有 eval fallback）不一致

## Goals

- 实现 SSL 支持：`RedisConfig.useSsl` 在单机和集群模式下均生效
- 实现 `forEachEntry` / `forEachEntryAsync`：基于 Redis SCAN 命令
- 实现 `getMessageService()`：基于 Redis Pub/Sub（PUBLISH / SUBSCRIBE）
- 新增测试覆盖 SSL、forEachEntry、Pub/Sub
- 更新 README 进度表，修复设计文档中过时的描述

## Non-Goals

- 不实现哨兵模式（sentinel），cluster + standalone 已满足需求
- 不增加 Redis Stream / Geo 操作（按需后续增加）
- 不重构现有 41 个测试用例
- 不增加 IoC beans.xml（保持当前模式，由消费方负责注册）

## Scope

### In Scope

- `LettuceRedisConnectionProvider`：`buildRedisURI()` 和 `buildClusterURIs()` 增加 `useSsl` 连接
- `LettuceMessageService`：实现 `forEachEntry`（SCAN）和 `forEachEntryAsync`
- `LettuceMessageService`：实现 `getMessageService()`，新增 `LettuceMessageService` Pub/Sub 实现类
- 新测试文件 `nop-nosql-lettuce/src/test/java/.../TestNosqlSsl.java`
- 新测试文件 `nop-nosql-lettuce/src/test/java/.../TestNosqlPubSub.java`
- 更新 `README.md` / `README.en.md` 进度表
- 更新 `ai-dev/design/nop-nosql/architecture.md` §3.7 过时描述

### Out Of Scope

- 哨兵模式
- Redis Stream / Geo
- IoC beans.xml 注册
- 现有测试用例重构

## Execution Plan

### Phase 1 — SSL 连接支持

Status: planned
Targets: `LettuceRedisConnectionProvider.java`, `RedisConfig.java`

- Item Types: `Fix`

当前 `buildRedisURI()` 和 `buildClusterURIs()` 均未检查 `config.isUseSsl()`。Lettuce 的 `RedisURI.Builder` 提供 `withSsl(boolean)` 方法。

改动：

```java
// buildRedisURI()
RedisURI.Builder builder = RedisURI.builder();
// ... 现有代码 ...
if (config.isUseSsl()) {
    builder.withSsl(true);
    // 可选：withVerifyPeer(false) 或通过 SSL 证书配置
}
return builder.build();

// buildClusterURIs() — 同理对每个 URI 设置
```

- [ ] `buildRedisURI()` 增加 `useSsl` 连接
- [ ] `buildClusterURIs()` 增加 `useSsl` 连接

Exit Criteria:

- [ ] `config.isUseSsl()` = true 时，生成的 RedisURI 包含 SSL 标志
- [ ] 单机和集群模式均支持 SSL
- [ ] 不破坏现有非 SSL 连接行为（`isUseSsl()` 默认 false）

### Phase 2 — forEachEntry 实现（SCAN）

Status: planned
Targets: `LettuceMessageService.java`

- Item Types: `Fix`

`INosqlKeyValueOperations` 继承自 `IAsyncMap<String, Object>`，其中定义了 `forEachEntry(BiConsumer)` 和 `forEachEntryAsync(BiConsumer)`。当前同步版抛异常，异步版返回 null。

实现方案（同步版）：

```java
@Override
public void forEachEntry(BiConsumer<? super String, ? super Object> consumer) {
    RedisClusterCommands<String, Object> commands = sync();
    String cursor = "0";
    do {
        ScanCursor sc = ScanCursor.of(cursor);
        ScanArgs args = ScanArgs.Builder.matches("*").limit(100);
        MapScanIteration<String, Object> result = commands.hscan("dummy", sc, args);
        // Actually KV scan — use SCAN not HSCAN
    } while (!cursor.equals("0"));
}
```

实际上 `INosqlKeyValueOperations` 是 KV 操作（extends `IAsyncMap`），对应 Redis 的 key-value 空间，不是 hash。所以应该使用 `SCAN` 命令（而非 `HSCAN`）。Lettuce 的 `RedisClusterCommands` 提供 `scan(ScanCursor)` / `scan(ScanCursor, ScanArgs)` 方法。

- [ ] 同步 `forEachEntry` 使用 `SCAN` 遍历所有 key，对每个 key 执行 `GET` 获取值后调用 consumer
- [ ] 异步 `forEachEntryAsync` 使用 `SCAN` 异步版本，返回 `CompletionStage<Void>`
- [ ] 增加分页参数（默认 100 条/批），防止阻塞

Exit Criteria:

- [ ] `forEachEntry` 遍历所有 key-value 对并调用 consumer
- [ ] `forEachEntryAsync` 异步完成遍历
- [ ] 空数据库时无异常
- [ ] 大数据量时能分批迭代（不一次加载全部）

### Phase 3 — Pub/Sub 消息服务

Status: planned
Targets: `LettuceMessageService.java`, 新增 `LettuceMessageSubscriber.java`

- Item Types: `Feature`

`getMessageService()` 需要返回 `IMessageService`（extends `IMessageSender` + `IMessageSubscriber`）。Redis Pub/Sub 的实现要求：

1. **PUBLISH** — 简单，通过 `async().publish(topic, message)` 实现
2. **SUBSCRIBE** — 复杂，需要：
   - 独立的连接（SUBSCRIBE 后连接进入监听模式，不能复用现有连接池）
   - 管理订阅回调映射（topic → IMessageConsumer）
   - 处理 Redis 消息到 `IMessageConsumer.onMessage()` 的转换

实现结构：

```java
// 新增类
public class LettuceMessageService implements IMessageService {
    private final LettuceRedisConnectionProvider connectionProvider;
    private final PubSubConnectionManager pubSubManager;

    @Override
    public void send(String topic, Object message) {
        connectionProvider.getSyncCommands().publish(topic, message);
    }

    @Override
    public CompletionStage<Void> sendAsync(String topic, Object message) {
        return connectionProvider.getAsyncCommands().publish(topic, message)
            .thenApply(v -> null);
    }

    @Override
    public IMessageSubscription subscribe(String topic, IMessageConsumer listener, MessageSubscribeOptions options) {
        return pubSubManager.subscribe(topic, listener, options);
    }
}
```

PubSub 连接管理：

```java
public class PubSubConnectionManager {
    // 维护一个独立的 StatefulRedisPubSubConnection
    // 维护 topic → listener 映射
    // 当 subscribe(topic) 时，连接 Redis SUBSCRIBE
    // 收到消息后查找 listener 调用 onMessage()
}
```

- [ ] 新增 `LettuceMessageService` 类实现 `IMessageService`
- [ ] `send` / `sendAsync` 委托给 Redis `PUBLISH` 命令
- [ ] `subscribe` 通过独立的 Pub/Sub 连接实现 `SUBSCRIBE`
- [ ] 处理连接生命周期（start/stop 与 `LettuceRedisConnectionProvider` 协调）
- [ ] `LettuceMessageService.getMessageService()` 返回该实现实例

Exit Criteria:

- [ ] `send(topic, message)` → Redis PUBLISH，订阅方可收到
- [ ] `subscribe(topic, listener)` → 注册后能收到该 topic 的消息
- [ ] 取消订阅后不再收到消息
- [ ] Pub/Sub 连接与普通连接池隔离（不阻塞 KV 操作）
- [ ] 连接断开后重连（Lettuce 的 `StatefulRedisPubSubConnection` 支持自动重连）

### Phase 4 — 文档与进度表更新

Status: planned
Targets: `README.md`, `README.en.md`, `ai-dev/design/nop-nosql/architecture.md`

- Item Types: `Fix`, `Docs`

- [ ] README.md 将 nop-nosql 从 0% 更新为 已完成
- [ ] README.en.md 将 nop-nosql 从 0% 更新为 Completed
- [ ] 修复 architecture.md §3.7：LettuceExecutor 已支持 eval 降级（非仅 evalsha）
- [ ] 更新 architecture.md §6 实现状态表：去除 getMessageService 和 forEachEntry 的 ⚠️ 标记

Exit Criteria:

- [ ] README.md/README.en.md 进度表反映最新状态
- [ ] architecture.md 中的过时描述已修正
- [ ] docs link checker 通过

## Closure Gates

- [ ] Phase 1 + Phase 2 + Phase 3 + Phase 4 全部 Exit Criteria 已勾选
- [ ] `./mvnw test -pl nop-nosql/nop-nosql-lettuce -am` 通过（含全部新增测试）
- [ ] 受影响的 owner docs（README.md、architecture.md）已同步
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **接线验证**：SSL 变更实际连接到支持 SSL 的 Redis 实例

## Closure

Status Note: （完成时填写）
Completed: YYYY-MM-DD
