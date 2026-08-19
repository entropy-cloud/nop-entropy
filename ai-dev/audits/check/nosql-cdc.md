# nosql-cdc 实现代码检查报告

- 检查日期: 2026-08-19
- 模块路径: nop-persistence/{nop-nosql,nop-cdc}
- 文件数: 39（src/main/java；nosql-core 19 + nosql-lettuce 19 + cdc-core 1）
- 覆盖范围声明:
  - nop-nosql 两个子模块（nop-nosql-core、nop-nosql-lettuce）全部 38 个 main Java 文件逐一全文阅读；
    5 个 lua 脚本（resources/nop/redis/*.lua）全文阅读；两个模块的 resources 下不存在 beans.xml（bean 由使用方编程式装配，本仓库内无运行时装配点）。
  - nop-cdc：nop-cdc-core 仅 1 个文件 `CdcConstants.java`（空接口，无任何常量/方法）；nop-cdc-debezium 无 Java 源码（空壳占位模块）。cdc 模块无实现代码可审。
  - 为验证命中点，另全文/片段阅读了依赖：`RoundRobinSupplier`、`PrefixEncodeHelper`、`ClassHelper`（safe classloader）、`CacheConfig`、`FutureHelper.syncGet`、`LifeCycleSupport`，并对本机 lettuce-core 6.6.0.RELEASE 字节码（`NestedMultiOutput`、`AbstractRedisClient`、`RedisCommandBuilder`）做了反汇编验证。
  - 测试代码（nop-nosql-lettuce/src/test）与 target/ 不在范围内。外部消费方（nop-auth-service MFA stores）仅做可达性交叉引用，未审计其自身实现。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 1 |
| P2 | 8 |
| P3 | 10 |

## 发现列表

### [P1] NosqlCache.getAsync 在 expireAfterAccess 未配置时必然 NPE，配置为 ZERO 时发送非法 GETEX PX 0

- **文件**: `nop-persistence/nop-nosql/nop-nosql-core/src/main/java/io/nop/nosql/core/cache/NosqlCache.java:52-54`
- **维度**: D1
- **证据**:
```java
@Override
public @Nullable
CompletionStage<Object> getAsync(@Nonnull String key) {
    return ops.getExAsync(key, cacheConfig.getExpireAfterAccess().toMillis());
}
```
- **现状**: `CacheConfig`（nop-kernel/nop-commons .../cache/CacheConfig.java:30-34）中 `expireAfterAccess` 默认为 `null`，且标准工厂 `newConfig(int)` / `newConfig(int, long)` 只设置 `expireAfterWrite`，从不设置 `expireAfterAccess`。用这两类工厂构造 `NosqlCache` 后，任何一次 `getAsync` 调用都会在 `.toMillis()` 处抛 NPE。若调用方显式设置 `expireAfterAccess = Duration.ZERO`，则会走到 `LettuceMessageService.getExAsync(key, 0)` → `GETEX key PX 0`，被 Redis 以 "invalid expire time" 拒绝。实现侧 `getExAsync` 只把 `timeout < 0` 当作"不设置过期"，0 值直接透传。
- **风险**: 该模块公开的缓存门面在最常见的 CacheConfig 用法下异步读全挂（NPE），属于确定性缺陷。当前仓库内无 NosqlCache 运行时调用方（nop-auth-service MFA 文档注释明确提及 "NosqlCache.getAsync 会 GETEX 滑动刷新"，说明该路径被视为可用语义），故降为 P1 而非 P0。
- **建议**: `getAsync` 中判空：`expireAfterAccess == null || <= 0` 时退化为 `ops.getAsync(key)`；同时在 `getExAsync` 一致处理 `timeout == 0`（与 `timeout < 0` 同路径），并为 NosqlCache 补一个最小回归测试（null/ZERO/正数三态）。
- **误报排除**: 已确认 `CacheConfig.getExpireAfterAccess()` 返回字段原值，无默认 Duration 兜底；`NosqlCache` 构造函数不校验、不补默认值。

### [P2] LettucePubSubService 停止后连接不置空、订阅表不清空，重启后拿到已关闭连接

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettucePubSubService.java:61-82`
- **维度**: D2/D3
- **证据**:
```java
private StatefulRedisPubSubConnection<String, Object> pubSubConnection; // volatile

private StatefulRedisPubSubConnection<String, Object> getOrCreateConnection() {
    if (pubSubConnection == null) {
        synchronized (this) {
            if (pubSubConnection == null) {
                pubSubConnection = connectionProvider.createPubSubConnection();
            }
        }
    }
    return pubSubConnection;
}

@Override
protected void doStop() {
    StatefulRedisPubSubConnection<String, Object> conn = pubSubConnection;
    if (conn != null) {
        conn.close();
    }
}
```
- **现状**: `doStop` 关闭连接但不把 `pubSubConnection` 置 null，也不清空 `subscriptions`。`LifeCycleSupport` 支持 restart；stop→start 后再次 `subscribe` 时：旧 entry 仍留在 `subscriptions`（走 `addListener` 分支，listener 数不为 1 时不重挂连接监听），且 `getOrCreateConnection` 返回已关闭的旧连接，`conn.sync().subscribe(topic)` 抛连接已关闭异常。
- **风险**: 生命周期重启后消息订阅功能整体不可用；即使不考虑 restart，stop 后残留的 `subscriptions` 状态也与实际连接状态不一致。
- **建议**: `doStop` 中 `conn.close()` 后 `pubSubConnection = null`，并清空 `subscriptions`（或标记所有 entry 取消）；重启语义下让首个 `subscribe` 重建连接与订阅。
- **误报排除**: 已核对 `LifeCycleSupport`（nop-kernel/nop-commons）存在 `start/stop/doStart/doStop` 且允许 restart（allowRestart 标志）；`subscribe()` 路径确实直接复用 `pubSubConnection`。

### [P2] PubSubSubscription.cancel 与 subscribe 并发竞态可孤立新订阅者

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettucePubSubService.java:156-167`
- **维度**: D3
- **证据**:
```java
@Override
public void cancel() {
    entry.cancel();
    entry.removeListener(listener);
    if (entry.isEmpty()) {
        StatefulRedisPubSubConnection<String, Object> conn = pubSubConnection;
        if (conn != null) {
            conn.sync().unsubscribe(topic);
        }
        subscriptions.remove(topic, entry);
    }
}
```
- **现状**: `subscribe()` 是 `synchronized`，但 `cancel()` 不是。线程 A 执行 cancel：`removeListener` 后 `isEmpty()` 判定为 true；在线程 A 执行 `unsubscribe` 之前，线程 B 调用 `subscribe(topic, ...)` 从 `subscriptions` 取到同一个 entry 并 `addListener(newListener)`；随后 A 执行 `unsubscribe(topic)` 并把 entry 从 map 移除。B 的新 listener 挂在一个已被退订、且已从注册表移除的 entry 上，永远收不到消息，且不会自愈（后续新订阅会新建 entry，但 B 持有的订阅对象已孤立）。
- **风险**: 多消费者并发退订/订阅同一 topic 时静默丢订阅（无异常、无日志）。
- **建议**: `cancel()` 对 entry 的移除与 `subscribe()` 互斥（对 service 对象 synchronized，或改用 `subscriptions.compute` 原子块 + 先从 map 移除再 unsubscribe 的顺序）。
- **误报排除**: 已确认 `subscribe`（synchronized）与 `cancel`（无同步）不对称；`removeListener/addListener` 仅操作 ConcurrentMap，不提供跨方法原子性。

### [P2] LettuceRedisConnectionProvider 泄漏自建 ClientResources（每次 start/stop 周期泄漏一组 Netty 线程）

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceRedisConnectionProvider.java:124-141`
- **维度**: D2
- **证据**:
```java
@Override
protected void doStop() {
    if (clusterClient != null)
        clusterClient.shutdown();
    if (standaloneClient != null)
        standaloneClient.shutdown();
}

private ClientResources buildClientResources() {
    return DefaultClientResources.create();
}
```
- **现状**: `RedisClient.create(ClientResources, RedisURI)` 传入非 null resources 时，lettuce 内部 `sharedResources = true`；已反汇编确认 `AbstractRedisClient.closeClientResources` 仅在 `sharedResources == false` 时才会 shutdown ClientResources。因此 `client.shutdown()` 关闭连接但不会关闭这里自建的 `DefaultClientResources`（含 eventLoopGroup、定时器等）。restart 或单元测试反复 start/stop 时，每个周期泄漏一组 Netty 资源线程。
- **风险**: 生命周期反复重启导致线程/内存缓慢泄漏；虽然单次应用关停影响小，但与 `LifeCycleSupport` 的 restart 能力组合后是现实的资源耗尽路径。
- **建议**: 要么复用一个共享的 ClientResources（字段持有并在 `doStop` 中 `shutdownAsync().await()`），要么在 doStop 显式关闭自建 resources。
- **误报排除**: 已通过 lettuce-core 6.6.0.RELEASE 字节码验证 `closeClientResources` 的 `sharedResources` 分支与构造器赋值逻辑。

### [P2] useSsl=true 时无条件关闭 TLS 证书校验（withVerifyPeer(false)）

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceRedisConnectionProvider.java:161-164,181-184`
- **维度**: D5
- **证据**:
```java
if (config.isUseSsl()) {
    builder.withSsl(true);
    builder.withVerifyPeer(false);
}
```
- **现状**: 单机与集群两处 URI 构建都在启用 SSL 的同时无条件禁用对端证书校验，且 `RedisConfig` 没有任何开关控制该行为。
- **风险**: 开启 SSL 的部署（通常正是含敏感数据/公网传输的场景）实际处于可中间人攻击状态：攻击者可截获/篡改包含认证凭据（password）在内的所有 Redis 流量，安全收益归零。
- **建议**: 增加 `verifyPeer` 配置项（默认 true），仅允许显式降级；至少在文档中说明当前行为。
- **误报排除**: 两处代码均无条件执行 `withVerifyPeer(false)`，无配置分支。

### [P2] Hash 子接口的 CAS 语义实现为非原子的 check-then-act，与 KV 级 Lua 原子实现不一致

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceHashOperations.java:110-113,131-138,216-228`
- **维度**: D8
- **证据**:
```java
// removeIfMatchAsync —— KV 级同名方法使用 remove_if_match.lua 原子 CAS
public CompletionStage<Boolean> removeIfMatchAsync(String field, Object object) {
    return async().hget(key, field).thenCompose(value -> {
        if (value != null && value.equals(object)) {
            return async().hdel(key, field).thenApply(n -> n > 0);
        }
        return CompletableFuture.completedFuture(false);
    });
}
```
`putIfAbsentOrMatchExAsync` 与 `getAndSetAsync` 同样是 `hget` → 判断 → `hset/pexpire` 的两步序列。
- **现状**: 同名方法在 KV 层（`LettuceMessageService.removeIfMatchAsync` 走 `REMOVE_IF_MATCH` Lua；`getAndSetExAsync` 走 `SET ... GET` 原子命令）是原子的，在 Hash 层却退化为两次独立网络调用之间的非原子窗口。并发下两个客户端可同时通过 `hget` 校验、双双执行写入/删除，破坏 CAS 语义（例如并发 consume 同一验证码字段可双删双过）。
- **风险**: 使用方按 KV 层语义类推 Hash 层时引入并发正确性漏洞；且失败模式静默（无异常，只是窗口内判定失真）。
- **建议**: 为 hash 字段补 Lua 版本（`HEXISTS/HGET + HDEL/HSET` 可在脚本内原子完成），或至少在 `INosqlHashOperations` javadoc 明确标注这些方法非原子。
- **误报排除**: 已对照 KV 层实现确认 Lua/原子命令与 hash 层非原子实现并存；接口层无任何非原子警告（仅 TTL 相关方法有 javadoc 说明）。

### [P2] getTopN(0)/getRange(start,0) 边界值返回"全部数据"而非空集

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceRanking.java:73-75`、`.../LettuceListOperations.java:69-71`
- **维度**: D1/D8
- **证据**:
```java
// LettuceRanking.getTopNAsync
return async().zrevrangeWithScores(key, 0, n - 1)   // n=0 → ZREVRANGE key 0 -1 → 整个 zset

// LettuceListOperations.getRangeAsync
return async().lrange(key, start, start + maxCount - 1);  // maxCount=0,start=0 → LRANGE key 0 -1 → 整个 list
```
- **现状**: Redis 区间语义中 `end = -1` 表示"到最后一个元素"。`getTopN(0)` 与 `getRange(0, 0)` 的索引计算恰好产生 -1，静默返回全量数据。`INosqlRanking.getTopN` / `INosqlListOperations.getRange` 接口未定义 0 值行为，调用方以"数量=0 应返回空"的直觉使用时会得到意外的大结果集。同类问题：`LettuceQueue.dequeueBatchAsync(0)` / `LettuceListOperations.leftPopMultiAsync(0)` 会发送 `LPOP key 0`，被 Redis 以 "value is out of range" 拒绝（抛异常而非返回空）。
- **风险**: 静默的错误结果（对 getTopN/getRange）与不一致的边界行为（对 dequeueBatch/lpopMulti），分页/榜单类调用传入 0 时轻则全量拉取打爆内存，重则异常中断。
- **建议**: 在入口对 `n <= 0` / `maxCount <= 0` 短路返回空集合，保持四个方法的边界行为一致，并补测试。
- **误报排除**: Redis 文档语义明确 `-1` 为末元素；代码无任何 0 值防护。

### [P2] LettuceSessionStore.set 为 HMSET 合并语义，旧字段残留（"整体设置会话"契约漂移）

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceSessionStore.java:60-76`
- **维度**: D8
- **证据**:
```java
@Override
public CompletionStage<Void> setAsync(String sessionId, Map<String, Object> data, long ttlMs) {
    String key = sessionKey(sessionId);
    return async().hmset(key, data)
            .thenCompose(v -> {
                if (ttlMs > 0) {
                    return async().pexpire(key, ttlMs);
                }
                ...
```
- **现状**: 接口方法名为 `set`（整体设置会话数据），实现是 `HMSET + PEXPIRE`：只覆盖传入的字段，上一轮会话中存在、本轮 `data` 中不存在的字段不会被删除。接口 javadoc 只披露了"非原子（HMSET 后 PEXPIRE）"，没有披露合并语义。会话刷新（如登出后重建、属性集合缩小）时旧属性（角色、标记位等）静默残留。附带问题：`setField` 在会话 key 尚未 `set` 过时执行，会创建一个无 TTL 的永久 key。
- **风险**: 会话状态污染 → 旧权限/旧标记继续生效，属于隐性数据正确性问题。
- **建议**: `set` 改为 `DEL + HMSET + PEXPIRE`（或事务/Lua）；至少在接口 javadoc 标注合并语义；`setField` 考虑可选 TTL 或文档说明必须先 `set`。
- **误报排除**: 已确认 HMSET 不会删除已有字段、也不清除已有 TTL；接口注释仅提及非原子性，未提合并。

### [P2] PrefixTextCodec 解码按存储内容加载任意类（反序列化攻击面）

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/codec/PrefixTextCodec.java:24-27`
- **维度**: D5
- **证据**:
```java
@Override
public Object decodeValue(ByteBuffer bytes) {
    String str = StringCodec.UTF8.decodeValue(bytes);
    return PrefixEncodeHelper.decode(str, ClassHelper.getSafeClassLoader());
}
```
- **现状**: 存储值形如 `$d:<类名>\n{json}` 时，`PrefixEncodeHelper.decode` 会用 `ClassHelper.getSafeClassLoader()` 加载该类名并 JSON 反序列化。已核实 `s_safeClassLoader = name -> forName(name)`（nop-commons ClassHelper.java:160），即无任何白名单的全量 classpath 加载。任何能写该 Redis DB 的主体（被入侵的共租应用、内网横向）都能让本进程实例化任意 classpath 上的类并注入 JSON 属性，构成 Jackson 风格的 gadget 攻击面。
- **风险**: Redis 内容被污染时的远程类实例化/RCE 风险；即使不利用 gadget，也可通过加载耗时类造成 CPU/内存压力。
- **建议**: 为解码引入类型白名单（如仅允许 `io.nop.*` 与基础集合类型），或提供可配置的受控 IClassLoader；至少限制 `$d:` 前缀可加载的包前缀。
- **误报排除**: 已读 `PrefixEncodeHelper.decode`（nop-core）与 `ClassHelper` 的 safeClassLoader 定义，确认无过滤；该编码是 nosql 默认 codec（provider 的默认字段值）。

### [P3] LettuceRateLimiter：小数 rate 下 getAvailableTokens 解析 NumberFormatException，rate<=0 时 Lua 直接报错

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceRateLimiter.java:71-80`
- **维度**: D1
- **证据**:
```java
return async().get(tokensKey)
        .thenApply(v -> {
            if (v == null) return config.getCapacity() > 0 ? (long) config.getCapacity() : 0L;
            return Long.parseLong(v.toString());
        })
```
- **现状**: `rate_limit.lua` 中 `new_tokens` 可为小数（`rate` 为小数时 `last_tokens+(delta*rate)` 非整数），`SETEX` 存的是 `"12.5"` 这类文本；`getAvailableTokens` 用 `Long.parseLong` 解析必然抛 NumberFormatException。另外 `RateLimiterConfig` 不校验 `rate<=0`：`fill_time = capacity/rate` 为 inf，`setex` 的 ttl 非法导致每次 `tryAcquire` 报错。tryAcquire 主路径（MULTI 输出整数走 Long，已反汇编 NestedMultiOutput 确认不经 codec）不受影响。
- **风险**: 限流配置为小数速率时辅助查询接口不可用；非法配置无前置失败提示。
- **建议**: 解析改用 `Double.parseDouble` 再取整；构造器校验 `rate > 0 && capacity >= 0`。
- **误报排除**: 已核对 lua 脚本与 codec 解码路径（非 `$` 前缀文本按 String 返回），确认 `"12.5"` 会到达 parseLong。

### [P3] RedisConfig.masterName 配置静默失效（无 Sentinel 节点支持）

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceRedisConnectionProvider.java:152-155`
- **维度**: D1
- **证据**:
```java
if (config.getMasterName() != null) {
    builder.withSentinelMasterId(config.getMasterName());
}
```
- **现状**: 只设置了 sentinel master id，从未调用 `withSentinel(host, port)` 添加哨兵节点。lettuce 在无哨兵节点时按普通 standalone URI 处理，masterName 被静默忽略，实际直连 `config.host`。
- **风险**: 用户配置 sentinel 后以为获得主从自动切换，实际是单点直连，故障时切换不生效。
- **建议**: 要么补 sentinel 节点配置支持，要么对 `masterName != null` 且无 sentinel 节点的情况快速失败。
- **误报排除**: 已核对整个 provider 无 sentinel 节点装配代码；RedisConfig 也无 sentinel hosts 字段。

### [P3] buildClusterURIs 用 split(":") 解析节点，格式异常时数组越界/不支持 IPv6

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceRedisConnectionProvider.java:171-175`
- **维度**: D1
- **证据**:
```java
String[] parts = node.split(":");
RedisURI.Builder builder = RedisURI.builder()
        .withHost(parts[0].trim())
        .withPort(Integer.parseInt(parts[1].trim()));
```
- **现状**: 节点串缺少端口时 `parts[1]` 抛 ArrayIndexOutOfBoundsException；IPv6 地址（含多个冒号）解析错误。配置错误没有清晰的错误信息。
- **风险**: 配置错误时的诊断体验差（裸 AIOOBE/NFE 而非带配置项的错误）。
- **建议**: 校验 parts 长度并抛带 `node` 参数的 NopException；或直接使用 `RedisURI.create("redis://" + node)`。
- **误报排除**: 代码无长度检查，直接取 `parts[1]`。

### [P3] getAll/removeAll/getAllAsync 传空集合时被 lettuce 前置校验抛异常（与 putAll 的空守卫不一致）

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceMessageService.java:74-81,123-125`
- **维度**: D1
- **证据**:
```java
public Map<String, Object> getAll(Collection<? extends String> keys) {
    List<KeyValue<String, Object>> list = sync().mget(keys.toArray(new String[keys.size()]));
    ...
public void removeAll(Collection<? extends String> keys) {
    sync().del(keys.toArray(new String[keys.size()]));
}
// 对比 putAll：
public void putAll(Map<? extends String, ?> map) {
    if (map == null || map.isEmpty())
        return;
```
- **现状**: 已反汇编确认 lettuce `RedisCommandBuilder` 对 mget/del 等命令有 `LettuceAssert.notEmpty` 前置校验，空集合直接抛 IllegalArgumentException。同类方法 `putAll/putAllAsync`、`LettuceHashOperations.putAllAsync` 都做了空守卫，读/删路径没有。
- **风险**: 上层批量读删（如缓存批量失效收到空列表）在边界处抛异常而非幂等空操作。
- **建议**: 统一空集合短路返回空 Map/直接完成。
- **误报排除**: 已核对 lettuce 6.6.0 RedisCommandBuilder 字节码中的 notEmpty 调用。

### [P3] RedisScripts.GET_AND_SET / GET_AND_EXPIRE 及对应 lua 文件为无引用死代码

- **文件**: `nop-persistence/nop-nosql/nop-nosql-core/src/main/java/io/nop/nosql/core/script/RedisScripts.java:24-25`
- **维度**: D4（维护性）
- **证据**:
```java
DigestedText GET_AND_EXPIRE = loadScript("classpath:/nop/redis/get_and_expire.lua");
DigestedText GET_AND_SET = loadScript("classpath:/nop/redis/get_and_set.lua");
```
- **现状**: 全仓库 grep（排除 target/_tmp）确认这两个脚本常量只在定义处出现。对应功能由 `GETEX` 与 `SET ... GET` 命令实现。
- **风险**: 死代码误导后来者以为 getAndSetEx/getEx 走脚本路径；静态加载也会无谓读两个资源文件。
- **建议**: 删除两个常量与对应 lua 资源，或注释说明保留原因。
- **误报排除**: grep 覆盖全仓库 *.java，无其他引用。

### [P3] LettuceLock.isHeld 只反映本地标记；unlockAsync 失败后 lockValue 残留

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceLock.java:52-70`
- **维度**: D8
- **证据**:
```java
public CompletableFuture<Void> unlockAsync() {
    String value = lockValue;
    if (value == null) {
        return CompletableFuture.completedFuture(null);
    }
    return LettuceExecutor.evalScript(async(), RedisScripts.REMOVE_IF_MATCH, ...)
            .thenAccept(removed -> lockValue = null).toCompletableFuture();
}

public boolean isHeld() {
    return lockValue != null;
}
```
- **现状**: `isHeld` 是纯本地状态：lease 到期后仍返回 true；`unlockAsync` 的脚本调用异常（网络错误）时 `lockValue` 不会被清空，后续 `isHeld` 一直为 true，再次 `unlock` 会重试删除（此时 CAS 值已不匹配，无害但语义混乱）。类注释已声明单线程使用，并发场景不在承诺内。
- **风险**: 使用方把 `isHeld` 当作锁的真实持有时做出错误判断（如 lease 过期后仍认为持锁）。
- **建议**: javadoc 标注 `isHeld` 为"本地视图，不校验 Redis 真实状态与 lease 到期"。
- **误报排除**: 加锁（SET NX PX + UUID）与解锁（Lua 比对值删除）本身是正确的 Redisson 风格 CAS，无越权删除问题；此条仅针对状态视图与失败残留。

### [P3] NosqlCache 的 clear/estimatedSize 作用于整个 Redis DB；clearForTenant 为空操作

- **文件**: `nop-persistence/nop-nosql/nop-nosql-core/src/main/java/io/nop/nosql/core/cache/NosqlCache.java:74-77,125-127,140-142`
- **维度**: D8
- **证据**:
```java
@Override
public long estimatedSize() {
    return ops.getSize();       // dbsize：整个数据库的 key 数
}
@Override
public void clear() {
    ops.clear();                // flushdb：清空整个数据库
}
@Override
public void clearForTenant(String tenantId) {
    // 空
}
```
- **现状**: `ICache` 语义按名字空间隔离，但实现直接映射到 DB 级命令：若该 Redis DB 还存有非缓存数据，`clear()` 会连带清空。`clearForTenant` 静默无操作（多租户清缓存不生效且无提示）。
- **风险**: 与其他 ICache 实现的语义差异在共享 DB 场景造成数据误删；租户隔离操作静默失效。
- **建议**: 文档标注"假定独占 Redis DB"；`clearForTenant` 至少抛 UnsupportedOperationException 或按 key 前缀扫描删除。
- **误报排除**: `LettuceMessageService.clear()` 即 `flushdb()`，已核对；无前缀过滤逻辑。

### [P3] PubSubConsumeContext.sendAsync 是假发送（静默丢弃消息）

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettucePubSubService.java:132-142`
- **维度**: D4/D8
- **证据**:
```java
private static class PubSubConsumeContext implements IMessageConsumeContext {
    @Override
    public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
        return CompletableFuture.completedFuture(null);
    }
}
```
- **现状**: 消费者上下文里调用 `sendAsync` 会静默完成而不发布任何消息（不是转发到 connectionProvider 的 publish）。
- **风险**: 消费者内部产生的事件被无声吞掉，排查困难。
- **建议**: 要么真正调用 `LettucePubSubService.sendAsync`，要么抛 UnsupportedOperationException 显式暴露不支持。
- **误报排除**: 类中无其他实现路径，确为固定返回完成态。

### [P3] refreshConfig → RoundRobinSupplier.resize 会立即关闭仍在使用的连接

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceRedisConnectionProvider.java:89-98`（配合 nop-kernel RoundRobinSupplier.resize）
- **维度**: D3/D6
- **证据**:
```java
@Override
public void refreshConfig() {
    if (connectionSupplier != null) {
        int n = config.getConnectionPoolSize();
        if (n <= 0) {
            n = 1;
        }
        connectionSupplier.resize(n);
    }
}
```
- **现状**: `resize` 缩容时直接 `closeNext` 关闭被裁剪的连接，而这些连接可能正被其他线程的 sync/async 命令使用，产生瞬时命令失败；扩容时同步建立新连接，若 Redis 不可达，异常会从 `refreshConfig` 抛出打断配置刷新流程。此外 refreshConfig 只响应 poolSize 变化，host/port 等变更静默无效。
- **风险**: 配置热更新窗口内的可用性抖动与误导（改 host 无效）。
- **建议**: 关闭前先从轮询数组摘除并延迟关闭；refreshConfig 对不支持的配置变更给出告警。
- **误报排除**: 已读 RoundRobinSupplier.resize 源码，缩容路径无缓冲直接 safeClose。

### [P3] KV 级 putIfAbsentOrMatchExAsync 对非 String 旧值强转 CCE（hash 级同名方法有防御）

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceMessageService.java:288-292`
- **维度**: D1/D8
- **证据**:
```java
public CompletionStage<String> putIfAbsentOrMatchExAsync(String key, String value, long timeout) {
    return LettuceExecutor.evalScript(async(), RedisScripts.PUT_IF_ABSENT_OR_MATCH,
                    ScriptOutputType.VALUE, new String[]{key}, new Object[]{value, timeout})
            .thenApply(v -> (String) v);
}
```
- **现状**: Lua 返回的旧值经 `ScriptOutputType.VALUE` 由 codec 解码：若该 key 此前存的是 `$d:` 前缀的 bean/Map（解码为非 String 对象），`(String) v` 抛 ClassCastException。对比 `LettuceHashOperations.putIfAbsentOrMatchExAsync`（218 行）做了 `oldValue instanceof String` 判断 + `String.valueOf` 兜底，两级实现防御不一致。
- **风险**: 同一 key 被混用为通用 KV 与字符串 CAS 槽位时异常。
- **建议**: 与 hash 版对齐：`v == null ? null : String.valueOf(v)` 或 instanceof 校验。
- **误报排除**: 已确认 VALUE 输出走 codec.decodeValue → PrefixEncodeHelper.decode 可返回 bean 对象。

## 附注（非缺陷，检查结论）

- D7 平台规范：两模块内唯一注入点是 `LettuceRedisConnectionProvider.setConfig` 的 public setter `@Inject`，符合 Nop IoC 规范（无 private 字段注入）；模块内无 beans.xml，bean 由使用方编程式装配（当前仓库内 nop-auth-service MFA 以构造器持有 `INosqlService`），未见违规。错误处理未发现 bare RuntimeException 抛出、空 catch、printStackTrace。
- 限流器解码疑点已排除：`ScriptOutputType.MULTI` 的整数元素由 lettuce 直接以 Long 填充（不经 codec，已反汇编 `NestedMultiOutput` 验证），`Long.valueOf(1L).equals(arr[0])` 成立，非缺陷。
- nop-cdc 模块当前无可审计实现代码（空接口 + 空壳子模块）。
