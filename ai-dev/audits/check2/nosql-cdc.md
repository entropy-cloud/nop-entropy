# nosql-cdc 实现代码检查报告（check2）

- 检查日期: 2026-08-23
- 模块路径: nop-persistence/nop-nosql + nop-cdc
- 文件数: 38（src/main/java；nosql-core 19 + nosql-lettuce 18 + cdc-core 1）
- 覆盖范围声明: 深读全部 38 个文件（关键类：LettuceMessageService、LettucePubSubService、LettuceRedisConnectionProvider、LettuceHashOperations、LettuceRateLimiter、LettuceCounter、LettuceLock、LettuceRanking、PrefixTextCodec、NosqlCache 等），覆盖率 100%（该单元为小单元，3274 行全量深读）。同时交叉验证了 5 个 Lua 脚本资源、Lettuce 6.6.0.RELEASE 字节码（MULTI 输出 / RESP3 默认协议 / ClientResources 生命周期）、PrefixEncodeHelper、ICache/MapCache 契约、RoundRobinSupplier、nop-auth 三个 Redis MFA store（live 消费方）与 auth-service.beans.xml 条件装配。未覆盖区域: 无（src/test 与生成物按纪律跳过）。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 1 |
| P1 | 2 |
| P2 | 9 |
| P3 | 13 |

## 发现列表

### [P0] LettuceRateLimiter.tryAcquire 在 RESP3（Redis 6+ 默认协商）下每次调用必然抛 UnsupportedOperationException

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceRateLimiter.java:41-62`；`nop-persistence/nop-nosql/nop-nosql-core/src/main/resources/nop/redis/rate_limit.lua:24,33`
- **维度**: D1（另涉 D8）
- **证据**:
```java
// LettuceRateLimiter.java:41-47
return LettuceExecutor.evalScript(async(), RedisScripts.RATE_LIMIT,
                ScriptOutputType.MULTI,
                new String[]{tokensKey, timestampKey},
                ...
```
```lua
-- rate_limit.lua
local allowed = filled_tokens >= requested       -- 第 24 行: allowed 是 Lua boolean
...
return { allowed, new_tokens }                    -- 第 33 行: MULTI 返回 boolean 元素
```
- **现状**: 脚本以 `ScriptOutputType.MULTI` 求值，返回数组首个元素是 Lua 布尔值。Lettuce（6.6.0.RELEASE，经 `mvn dependency:tree` 确认）的 `ClientOptions.DEFAULT_PROTOCOL_VERSION = ProtocolVersion.newestSupported()` = RESP3（javap 字节码确认），对 Redis 6+/7 通过 HELLO 3 协商 RESP3。RESP3 下 Lua boolean 保留为布尔回复，`RedisStateMachine` 调用 `CommandOutput.set(boolean)`；而 MULTI 对应的 `NestedMultiOutput`（javap 确认 RedisCommandBuilder 对 MULTI 的映射）只覆写了 `set(long)/set(double)/set(ByteBuffer)`，未覆写 `set(boolean)`，基类默认实现直接抛 `UnsupportedOperationException`。结果：`tryAcquire/tryAcquireAsync` 对现代 Redis 每次调用都以异常终结，令牌桶限流功能完全不可用。
- **风险**: 任何调用方（`INosqlService.rateLimiter(key, config)` 是模块公开 API，且有 docker-gated 测试 `TestLettuceNosqlService.testRateLimiter_*` 表明这是预期可用的功能）在 Redis 6/7 上首次调用即崩溃。测试因 `@EnabledIfSystemProperty(named = "nop.test.docker.enabled", ...)` 默认不运行，问题不会被常规 CI 发现。
- **建议**: 让 rate_limit.lua 返回数值（如 `return { allowed and 1 or 0, new_tokens }`），或改为两个独立脚本/使用 `ScriptOutputType` 组合时避免 MULTI 携带 boolean；同时为 tryAcquire 补充可在无 Redis 环境下运行的脚本返回值契约测试。
- **误报排除**: 逐项核实了整条链路：MULTI→NestedMultiOutput 映射、NestedMultiOutput 无 set(boolean)、CommandOutput.set(boolean) 抛异常、RESP3 布尔经 safeSet(output, Z) 分发、默认协议为 RESP3——均来自本仓库依赖的 lettuce-core 6.6.0.RELEASE 字节码。另确认 `BooleanOutput` 覆写了 set(boolean)/set(long)，故 `REMOVE_IF_MATCH`（BOOLEAN 输出，LettuceLock、removeIfMatch 使用）不受影响，问题仅限 MULTI+boolean 组合。RESP2（Redis ≤5，已 EOL）下 boolean 转整数可正常工作，但测试容器即 redis:7-alpine，默认路径必踩。

### [P1] LettucePubSubService 消息分发不按 channel 过滤，跨 topic 串投给所有订阅者

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettucePubSubService.java:119-129`
- **维度**: D1/D8
- **证据**:
```java
private class SubscriptionEntry extends RedisPubSubAdapter<String, Object> {
    private final String topic;
    ...
    @Override
    public void message(String channel, Object message) {
        IMessageConsumeContext context = new PubSubConsumeContext();
        for (IMessageConsumer consumer : listeners.keySet()) {
            try {
                consumer.onMessage(channel, message, context);   // 未判断 channel.equals(topic)
```
- **现状**: 所有 topic 的 `SubscriptionEntry` 都注册为同一条 `StatefulRedisPubSubConnection` 上的连接级 listener（`addListener` → `conn.addListener(this)`，单连接承载全部订阅）。Lettuce 的 `PubSubEndpoint.notifyListeners` 将该连接收到的**每条** pub/sub 消息广播给**所有**已注册 listener，不做 channel 过滤（lettuce-core 6.6.0.RELEASE 字节码确认）。`SubscriptionEntry.message` 收到消息后直接遍历自己的 listeners 投递，未校验 `channel` 是否等于本 entry 的 `topic`。
- **风险**: 同一 `LettuceMessageService` 实例上订阅 topic A 与 topic B 后，向 A 发布的消息会被 B 的订阅者收到（`onMessage(channel=A, ...)`），消息路由错乱、跨业务数据串扰。现有测试每个用例只订阅一个 topic，未覆盖此场景。
- **建议**: 在 `message(String channel, Object message)` 开头增加 `if (!topic.equals(channel)) return;`（pattern 版回调 `message(String pattern, String channel, Object message)` 同理按需处理）。
- **误报排除**: 读过 `subscribe/getOrCreateConnection/addListener` 全流程确认单连接 + 连接级广播；用 javap 检查 `PubSubEndpoint.notifyListeners` 确认无 channel 分发逻辑；通读 `TestLettuceNosqlService` 的 3 个 PubSub 测试确认未覆盖双 topic 场景；`IMessageSubscriber.subscribe(topic, listener)` 契约语义即按 topic 订阅。

### [P1] PrefixTextCodec 值类型不对称：Number/Boolean 写入后读出恒为 String，nop-auth MFA 票据（markVerified/peek）因此永不生效

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/codec/PrefixTextCodec.java:24-27,35-37`；触发方 `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/mfa/store/RedisMfaChallengeStore.java:116-121,181-183`
- **维度**: D8（跨模块类型契约漂移）/D1
- **证据**:
```java
// PrefixTextCodec.java
public Object decodeValue(ByteBuffer bytes) {
    String str = StringCodec.UTF8.decodeValue(bytes);
    return PrefixEncodeHelper.decode(str, ClassHelper.getSafeClassLoader());
}
// PrefixEncodeHelper.encode: Number → message.toString()（裸数字，无类型前缀）
// PrefixEncodeHelper.decode: charAt(0) != '$' → 原样返回 String（不解析数字）
```
```java
// RedisMfaChallengeStore.java:181-183 markVerified 存 Long
Boolean ok = FutureHelper.syncGet(
        nosql.putIfAbsentExAsync(ticketKey(challengeToken), System.currentTimeMillis(), opTicketMillis()));
// RedisMfaChallengeStore.java:116-118 peek 读回后按 Number 判断
Object ticket = nosql.get(ticketKey(challengeToken));
if (ticket instanceof Number) {          // 恒为 false：读回的是 String
    c.setVerifiedAt(((Number) ticket).longValue());
```
- **现状**: `INosqlKeyValueOperations` 声明为 `Map<String,Object>` 语义，但 `PrefixTextCodec` 对 `Long/Integer/Boolean` 等值编码为裸文本（`PrefixEncodeHelper.encode` 的 Number/Boolean 分支），解码时对非 `$` 前缀文本一律返回 String（`PrefixEncodeHelper.decode` 第 81-82 行，其 javadoc 亦明示"不会自动转型"）。数值/布尔值经 put/get 往返后类型变为 String，无任何调用侧告警。
- **风险**: 配置 `nop.auth.mfa.store-type=redis`（auth-service.beans.xml `ioc:condition` 激活）时，`markVerified` 写入的验证票据时间戳读回为 String，`peek` 中 `ticket instanceof Number` 恒假 → `verifiedAt` 恒 null → `LoginApiBizModel:315`、`NopAuthUserBizModel:1380`、`OperationMfaCheckerImpl:152` 三处"已验证"判定全部失效，已验证票在票窗口内不被承认（用户被反复要求重新 MFA 验证；方向是 fail-closed 而非绕过，但功能明确错误）。
- **建议**: nosql 侧：在 `INosqlKeyValueOperations` javadoc 显著声明"Number/Boolean 值经编解码往返后为 String"，或提供类型保留的编码（数字加前缀，需评估存量数据兼容）；调用侧（nop-auth）：`markVerified` 改存 `String.valueOf(millis)` 并在 peek 端做字符串解析。两侧择一修复并对齐。
- **误报排除**: 读了 `PrefixEncodeHelper.encode/decode` 全文确认分支走向（Long 走 Number 分支输出裸数字、裸数字 decode 返回 String）；读了 markVerified/peek 完整方法与 beans.xml 条件装配、三处 verifiedAt 消费点确认触发链完整；确认 `putIfAbsentExAsync`（LettuceMessageService:277-285）直接 `async().set(key, value, args)` 经同一 codec 编码。

### [P2] PubSubSubscription.cancel 将共享 SubscriptionEntry 标记为 cancelled，污染同 topic 其他订阅的 isCancelled；resume 不检查 cancelled 可复活已取消订阅

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettucePubSubService.java:115-117,156-167,187-193`
- **维度**: D3/D8
- **证据**:
```java
void cancel() {
    cancelled.set(true);              // entry 级标志，同 topic 所有 PubSubSubscription 共享
}
public void cancel() {
    entry.cancel();                   // 无条件置位，即使还有其他活跃订阅
    entry.removeListener(listener);
    if (entry.isEmpty()) { ... subscriptions.remove(topic, entry); }
}
public void resume() {
    if (suspended) {
        suspended = false;
        entry.addListener(listener);  // 不检查 isCancelled，可复活已取消的订阅
    }
}
```
- **现状**: `IMessageSubscription.isCancelled()` 委托给 entry 级 `cancelled` 标志；同一 topic 多个订阅者共享一个 entry。任一订阅 cancel 后，其余仍活跃订阅的 `isCancelled()` 也返回 true（消息仍会投递，状态与行为不一致）。且 `resume()` 只看 `suspended`，对已 cancel 的订阅调用 resume 会把 listener 重新挂回。
- **风险**: 依赖 `isCancelled()` 做订阅管理的框架/应用误判；cancel+resume 组合下出现"已取消但仍收消息"的僵尸订阅。触发条件：同 topic 多订阅者且其一 cancel（现实常见的 fan-out 场景）。
- **建议**: `cancelled` 标志移到 `PubSubSubscription`（每个订阅实例独立）；`cancel()` 时同步将本订阅 `suspended` 置 true 或在 `resume()` 增加 `if (isCancelled()) return;`。
- **误报排除**: 通读 `SubscriptionEntry`/`PubSubSubscription` 全部方法与 `IMessageSubscription` 接口；确认 `cancelled` 定义在 entry 上（第 87 行）且 `isCancelled()` 委托 entry（第 175-177 行）；确认 listeners 投递循环不依赖 cancelled 标志（故仅状态撒谎、投递不受影响，定 P2 而非 P1）。

### [P2] LettuceRedisConnectionProvider 每次 doStart 新建 DefaultClientResources 且从不 shutdown，stop/start 循环泄漏 Netty 事件循环线程

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceRedisConnectionProvider.java:123-141`
- **维度**: D2
- **证据**:
```java
protected void doStop() {
    if (clusterClient != null)
        clusterClient.shutdown();
    if (standaloneClient != null)
        standaloneClient.shutdown();
}
private ClientResources buildClientResources() {
    return DefaultClientResources.create();     // 每次 doStart 新建，无字段持有，无法shutdown
}
```
- **现状**: `doStart` 每次 `RedisClient.create(buildClientResources(), uri)` 传入外部 ClientResources。Lettuce `AbstractRedisClient.closeClientResources` 字节码确认：`sharedResources=true`（构造传入）时 `client.shutdown()` **不会**关闭这些 resources。provider 未保存引用、也未调用 `clientResources.shutdown()`。
- **风险**: 每次 stop→start 循环（配置刷新重建、测试反复起停、应用模块热重启）泄漏一组 DefaultClientResources（computation group + event loop group + DNS resolver 线程），最终线程/句柄耗尽。类实现 `IConfigRefreshable` 表明重启是预期路径。
- **建议**: 将 `ClientResources` 保存为字段，`doStop()` 末尾调用 `resources.shutdown()`；或复用单例 `ClientResources.manageDefaultResources()`。
- **误报排除**: javap 检查 `AbstractRedisClient.closeClientResources`：`getfield sharedResources; ifne 47` 跳过 shutdown 调用，确认传入型 resources 不由 client 关闭；确认本类无任何其他关闭路径（RoundRobinSupplier.close 未被调用，连接由 client.shutdown 关闭属正常）。

### [P2] SSL 配置强制 withVerifyPeer(false)，TLS 形同虚设（MITM 风险）

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceRedisConnectionProvider.java:161-163,181-184`
- **维度**: D5
- **证据**:
```java
if (config.isUseSsl()) {
    builder.withSsl(true);
    builder.withVerifyPeer(false);      // 单机与集群两条路径都硬编码关闭校验
}
```
- **现状**: 只要开启 `useSsl` 就无条件关闭对端证书校验，且未提供任何开关。
- **风险**: 启用 SSL 的部署（通常正是因为跨不可信网络）可被中间人截获/篡改 Redis 流量（含 MFA challenge、session 数据），加密层不提供身份保证。
- **建议**: 增加 `verifyPeer` 配置项（默认 true），仅在显式配置自签场景时关闭。
- **误报排除**: 读了 `buildRedisURI`/`buildClusterURIs` 全文与 `RedisConfig` 全部字段，确认无其他控制校验的入口。

### [P2] NosqlCache.getAsync 直接解引用 cacheConfig.getExpireAfterAccess()，未配置时 NPE

- **文件**: `nop-persistence/nop-nosql/nop-nosql-core/src/main/java/io/nop/nosql/core/cache/NosqlCache.java:51-54`
- **维度**: D1
- **证据**:
```java
@Override
public @Nullable
CompletionStage<Object> getAsync(@Nonnull String key) {
    return ops.getExAsync(key, cacheConfig.getExpireAfterAccess().toMillis());
}
```
- **现状**: `CacheConfig.expireAfterAccess` 默认为 null（nop-commons CacheConfig.java:32 无默认值，`newConfig(maxSize)` 也不设置该字段）。以任何未设置 expireAfterAccess 的 CacheConfig 构造 NosqlCache 后，首次 `getAsync` 即 NPE。
- **风险**: 该类当前在仓库内无生产调用方（全仓 grep 确认），属导出公共类的潜在陷阱；一旦被装配使用且配置不含 expireAfterAccess，读路径整体不可用。
- **建议**: `expireAfterAccess == null` 时回退 `ops.getAsync(key)`（无 TTL 刷新），或构造时校验并 fail-fast。
- **误报排除**: 读 `CacheConfig` 全文确认字段无默认值；确认 `getExAsync(key, timeout)` 对负数 timeout 已有回退逻辑（LettuceMessageService:267-269），故传 -1 即可实现无 TTL 语义，修复成本低。

### [P2] getAll/getAllAsync 不过滤缺失 key（null 值入结果），与 ICache.getAllPresent 平台契约漂移

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceHelper.java:18-27`；`LettuceMessageService.java:74-81,170-172`；`LettuceHashOperations.java:57-64`
- **维度**: D8
- **证据**:
```java
public static Map<String, Object> toMap(List<KeyValue<String, Object>> list) {
    ...
    for (KeyValue<String, Object> pair : list) {
        ret.put(pair.getKey(), pair.getValue());   // 缺失 key 的 KeyValue.getValue()==null 也放入
    }
```
- **现状**: mget/hmget 对不存在的 key/field 返回 `KeyValue.empty`（hasValue()==false、value==null），`toMap` 原样放入。`NosqlCache.getAllPresent` → `ops.getAll` 因此返回含 null 值的条目；而平台既有实现 `MapCache.getAllPresent`（nop-commons MapCache.java:122-133）只收集非 null 值。
- **风险**: 调用方按平台惯例遍历 `getAllPresent` 结果直接使用值时对缺失 key 得到 null（下游 NPE 或误判"存在"）；sync 与 async 行为一致地错，难以察觉。
- **建议**: `toMap` 中跳过 `!pair.hasValue()` 的条目（与 KeyValue 语义对齐）。
- **误报排除**: 对比读了 `MapCache.getAllPresent` 与 `ICache.getAll` 默认实现（委托 getAllPresent）；确认 Lettuce KeyValue.empty 语义；确认 NosqlCache.getAllPresent 委托链（NosqlCache.java:89-92）。

### [P2] 集合参数为空时直接下发 Redis 命令触发 wrong-number-of-arguments 异常（putAll 有防护、其余遗漏）

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceMessageService.java:123-125,212-214,74-75`；`LettuceSetOperations.java:48-50`；`LettuceListOperations.java:59-61`；`LettuceHashOperations.java:146-148`
- **维度**: D1（边界条件）
- **证据**:
```java
// LettuceMessageService.removeAll：空集合 → DEL 无参数 → 服务端报错
public void removeAll(Collection<? extends String> keys) {
    sync().del(keys.toArray(new String[keys.size()]));
}
// 对比 putAll 有防护（95-97 行）：
public void putAll(Map<? extends String, ?> map) {
    if (map == null || map.isEmpty()) return;
```
- **现状**: `removeAll/removeAllAsync`（del）、`getAll/getAllAsync`（mget/hmget）、`SetOperations.removeAllAsync`（srem）、`ListOperations.addAllAsync`（rpush）等空集合入参不做防护；Redis 对零参数的 DEL/MGET/HDEL/SREM/RPUSH 返回 "ERR wrong number of arguments"。`putAll`/`putAllAsync` 有空判断，防护不对称证明属遗漏而非约定。
- **风险**: 批处理逻辑把空批次传给 removeAll/getAll 等即抛 Redis 异常；`ICache.removeAll(empty)`/`getAll(empty)` 按集合 API 惯例应为 no-op/空结果。
- **建议**: 各集合入口统一加 `isEmpty()` 快速返回。
- **误报排除**: 逐个核对了上述方法与有防护的 putAll/putAllAsync（MessageService:95-97, HashOperations:88-91）；Redis DEL/MGET 等零参数报错属服务端协议行为。

### [P2] LettuceRanking.getTopN(0) 返回整个有序集合（zrevrange 0 -1 全量语义）

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceRanking.java:73-75`
- **维度**: D1（边界条件）
- **证据**:
```java
public CompletableFuture<List<RankingEntry>> getTopNAsync(int n) {
    // Optimization: since ZREVRANGE is ordered, rank = offset + index
    return async().zrevrangeWithScores(key, 0, n - 1)   // n=0 → (0, -1) → 全量
```
- **现状**: `getTopN(0)` 计算区间 `[0, -1]`，Redis 语义为返回全部成员；榜单越大返回越多，且 rank 逐一编号。
- **风险**: 上层按"取前 0 名应为空"的直觉调用（分页/配额计算常见）时拿到全量数据，可能引发大响应与业务错乱（例如按 limit=0 预期清空展示）。
- **建议**: `n <= 0` 时直接返回空列表。
- **误报排除**: 对照 `LettuceListOperations.getRangeAsync` 的 `start + maxCount - 1` 公式（maxCount=0 时区间为空，天然安全），说明 Ranking 是唯一漏防的边界；Redis ZREVRANGE 0 -1 为全量属标准语义。

### [P2] LettuceRateLimiter.getAvailableTokensAsync 对小数 token 串执行 Long.parseLong，必然 NumberFormatException

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceRateLimiter.java:71-80`
- **维度**: D1
- **证据**:
```java
return async().get(tokensKey)
        .thenApply(v -> {
            if (v == null) return ...;
            return Long.parseLong(v.toString());   // 脚本可能存入 "9.5"
        })
```
```lua
-- rate_limit.lua:23,30  rate 为小数时 new_tokens 为小数
local filled_tokens = math.min(capacity, last_tokens+(delta*rate))
redis.call("setex", tokens_key, ttl, new_tokens)   -- 存 "9.5" 这类小数串
```
- **现状**: `RateLimiterConfig` 的 rate/capacity 均为 double（如 rate=0.5 表示每秒 0.5 个），token 余量可为小数并以小数字符串落库；读回后经 codec decode 为 String "9.5"，`Long.parseLong` 抛 NumberFormatException。
- **风险**: 凡使用小数 rate/capacity 的限流器，`getAvailableTokens` 查询即抛异常（tryAcquire 本身在 RESP2 下不受影响，因其走脚本的整数回复）。
- **建议**: 使用 `Double.parseDouble` 后取整（`(long) d`），或文档化仅支持整数速率并加构造校验。
- **误报排除**: 读了 rate_limit.lua 全文确认小数来源；确认 `async().get` 经 PrefixTextCodec 返回 String；确认测试仅用整数配置（1,10 / 1,2）故未暴露。

### [P2] LettuceHashOperations.removeIfMatchAsync 非原子（hget+hdel 两步），与 string 版 Lua CAS 实现不一致

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceHashOperations.java:130-138`
- **维度**: D3/D8
- **证据**:
```java
public CompletionStage<Boolean> removeIfMatchAsync(String field, Object object) {
    return async().hget(key, field).thenCompose(value -> {
        if (value != null && value.equals(object)) {
            return async().hdel(key, field).thenApply(n -> n > 0);   // 读与删之间有竞态窗口
        }
        return CompletableFuture.completedFuture(false);
    });
}
// 对比 string 版（LettuceMessageService:206-209）使用 REMOVE_IF_MATCH Lua 原子脚本
```
- **现状**: 同名接口方法在 hash 实现里是 check-then-act：hget 与 hdel 之间值可能被并发修改，hdel 仍会删除已被替换的 field，违背"值匹配才删除"语义；string 实现则用 Lua 原子脚本。两个实现原子性承诺不一致。
- **风险**: 以 `hashOps(key).removeIfMatch` 做乐观删除/一次性消费的调用方（例如 NosqlCache 包 hashOps 后的 `ICache.remove(key, object)`）在并发下可能误删他方新写的值。
- **建议**: 为 hash 提供 HGET/HDEL 版 Lua 脚本（复用 LettuceExecutor.evalScript 通道），或至少在接口 javadoc 标注 hash 实现的非原子性。
- **误报排除**: 读了 string 版 `removeIfMatchAsync` 与 `remove_if_match.lua` 确认原子实现存在且基建可复用（evalScript 接受任意脚本）；读了 `ICache.remove(K,V)` 默认实现（值相等才删）确认语义要求。

### [P3] hash 版 putIfAbsentOrMatchExAsync 非原子且用 String.valueOf 强转旧值类型

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceHashOperations.java:215-228`
- **维度**: D1/D8
- **证据**:
```java
return async().hget(key, field).thenCompose(oldValue -> {
    if (oldValue == null || (oldValue instanceof String && oldValue.equals(value))) {
        return async().hset(key, field, value).thenCompose(v -> {
            ...
            return async().pexpire(key, timeout).thenApply(exp -> oldValue != null ? String.valueOf(oldValue) : null);
```
- **现状**: hget→判断→hset 三步非原子（string 版 `put_if_absent_or_match.lua` 是原子的）；旧值非 String（如 POJO 解码对象）时返回 `String.valueOf(oldValue)`（toString 结果），类型被静默改写，且 `instanceof String` 判断导致非 String 旧值永远不参与 match 分支。
- **风险**: 无当前生产调用方（全仓 grep），属契约陷阱：调用方按 string 版语义使用 hash 版会得到弱化的原子性与类型。
- **建议**: 改用 hash 版 Lua 脚本（HGET/HSET/PEXPIRE），返回原始旧值。
- **误报排除**: 对比读了 `put_if_absent_or_match.lua`（原子、返回原始旧值）与 string 版包装（LettuceMessageService:288-292 仅 cast 不转换）。

### [P3] containsKey（get != null）与 containsKeyAsync（exists）对空串值判断不一致

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceMessageService.java:83-86,174-177`
- **维度**: D1/D8
- **证据**:
```java
public boolean containsKey(String key) {
    return sync().get(key) != null;          // 空串值经 codec decode 返回 null → 误判不存在
}
public CompletionStage<Boolean> containsKeyAsync(String key) {
    return async().exists(key).thenApply(value -> value == 1);   // 正确
}
```
- **现状**: `PrefixEncodeHelper.encode` 把 null 与空串都编为 ""，decode 返回 null，因此存过空串的 key 在同步 containsKey 中判 false；异步版用 EXISTS 判 true。同方法 sync/async 语义分歧。与 `ICache.containsKey` 默认实现（getIfPresent!=null）行为一致，故不升级为 P2。
- **风险**: 混用 sync/async 的调用方得到相反结果。
- **建议**: sync 版改用 `sync().exists(key) == 1`。
- **误报排除**: 读了 PrefixEncodeHelper.encode/decode 对空串的处理（47-48、78-79 行）；确认两方法在本类内并存。

### [P3] RedisScripts.GET_AND_SET/GET_AND_EXPIRE 常量加载但无任何使用，且 get_and_expire.lua 的 nil 判断在 Redis Lua 中永假

- **文件**: `nop-persistence/nop-nosql/nop-nosql-core/src/main/java/io/nop/nosql/core/script/RedisScripts.java:24-25`；`src/main/resources/nop/redis/get_and_expire.lua:2-7`
- **维度**: D1/D6（死代码）
- **证据**:
```java
DigestedText GET_AND_EXPIRE = loadScript("classpath:/nop/redis/get_and_expire.lua");
DigestedText GET_AND_SET = loadScript("classpath:/nop/redis/get_and_set.lua");
```
```lua
local value = redis.call('get', KEYS[1]);
if (value == nil) then        -- redis.call 对缺失 key 返回 false，nil 比较永不成立
```
- **现状**: 两个脚本常量在 Java 侧零引用（全仓 grep；MessageService 用原生 getex/setGet 实现对应能力）；get_and_expire.lua 若被启用，其 `value == nil` 分支判断错误（应为 `value == false`）。
- **风险**: 死代码 + 隐患脚本误导后续维护者。
- **建议**: 删除未用脚本或修复 `== false` 判断并接入使用。
- **误报排除**: 全仓 grep 确认两个常量无引用；get_and_set.lua 已自行做 `if value == false then value = nil end`（12 行），说明作者知晓该语义，get_and_expire.lua 属遗漏。

### [P3] buildClusterURIs 对节点串 split(":") 无校验：格式错误抛 AIOOBE、IPv6 地址不支持

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceRedisConnectionProvider.java:169-188`
- **维度**: D1（错误处理/边界）
- **证据**:
```java
for (String node : config.getClusterNodes()) {
    String[] parts = node.split(":");
    RedisURI.Builder builder = RedisURI.builder()
            .withHost(parts[0].trim())
            .withPort(Integer.parseInt(parts[1].trim()));   // "host"（无端口）→ AIOOBE；"[::1]:6379" 解析错
```
- **现状**: 无端口/多冒号（IPv6）输入分别导致数组越界或错误解析，报错信息（ArrayIndexOutOfBoundsException）对运维不友好。
- **建议**: 使用 `StringHelper.parseIpPort` 类工具或显式校验并抛带 key 的 NopException。
- **误报排除**: 读 buildClusterURIs 全文确认无任何防护；对照 `buildRedisURI` 用结构化 host/port 字段。

### [P3] RedisConfig.masterName 哨兵支持是半成品：无法表达 sentinel 节点地址

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceRedisConnectionProvider.java:152-155`；`nop-nosql-core/.../config/RedisConfig.java:28`
- **维度**: D8（配置契约）
- **证据**:
```java
if (config.getMasterName() != null) {
    builder.withSentinelMasterId(config.getMasterName());   // 无 withSentinel(...) 节点
}
```
- **现状**: RedisConfig 只有 `masterName`，没有 sentinel 节点列表字段；设置 masterName 后构造出的 RedisURI 同时带 host/port 与 sentinelMasterId，无法形成有效的哨兵拓扑（Lettuce 哨兵模式需要 sentinel 地址集合）。
- **风险**: 用户配置 masterName 期待哨兵模式，实际行为退化为直连 host（或连接失败）， silently 不符预期。
- **建议**: 增加 `sentinelNodes` 配置并在此处 `withSentinel(host, port)`；或移除 masterName 避免误导。
- **误报排除**: 读 RedisConfig 全部字段确认无 sentinel 地址位；读 buildRedisURI 全文确认无其他 sentinel 处理。

### [P3] LettuceSessionStore.setFieldAsync 对不存在/已过期的 session 会创建无 TTL 的 hash key

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceSessionStore.java:78-89`
- **维度**: D1（语义陷阱/隐性泄漏）
- **证据**:
```java
public CompletableFuture<Void> setFieldAsync(String sessionId, String field, Object value) {
    String key = sessionKey(sessionId);
    return async().hset(key, field, value)   // key 不存在时 HSET 创建新 hash，不带 TTL
```
- **现状**: `set` 的 TTL 只在 `setAsync` 里设置；对已过期或已删除的 session 调 setField 会重建一个永不过期的 key。接口 `INosqlSessionStore.setField` 无相关文档警示。
- **风险**: 异常路径下（session 过期后仍有迟到的字段写入）产生僵尸 session 数据，逐渐堆积。
- **建议**: 在接口 javadoc 标注该语义，或 setField 改为 HSET+EXPIRE 事务/脚本以继承 TTL。
- **误报排除**: 读了 setAsync/touch/removeAsync 全部 TTL 相关路径，确认只有 setAsync 设 TTL；接口无文档。

### [P3] PubSubConsumeContext.sendAsync 静默吞掉消息（返回成功但不发送）

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettucePubSubService.java:132-142`
- **维度**: D4
- **证据**:
```java
private static class PubSubConsumeContext implements IMessageConsumeContext {
    @Override
    public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
        return CompletableFuture.completedFuture(null);   // 假装发送成功
    }
```
- **现状**: 消费上下文的 sendAsync 是 no-op 却返回成功 future；若消费方返回响应消息或经 context 转发，消息被静默丢弃。
- **风险**: 依赖 context 回发（request/reply 模式）的消费方逻辑静默失效，难排查。
- **建议**: 委托 `LettucePubSubService.sendAsync` 真实 publish，或抛 UnsupportedOperationException 显式声明不支持。
- **误报排除**: 读 `IMessageConsumer.onMessage` 契约（返回值可作为响应消息）与 `IMessageConsumeContext` 接口，确认 context.sendAsync 语义应为发送。

### [P3] subscribe/send 的 MessageSubscribeOptions/MessageSendOptions 参数被完全忽略

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettucePubSubService.java:40-43,45-59`
- **维度**: D8
- **证据**:
```java
public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
    return connectionProvider.getAsyncCommands().publish(topic, message)   // options 未用
            .thenApply(ignored -> null);
}
public synchronized IMessageSubscription subscribe(String topic, IMessageConsumer listener,
                                                   MessageSubscribeOptions options) {   // options 未用
```
- **现状**: 两个 options 参数均未读取（如 consumerGroup/durable 等语义无从表达），接口签名承诺与实现不符。
- **风险**: 调用方传入配置被静默忽略，行为与预期不符。
- **建议**: 至少校验不支持的选项并抛异常或记日志，注明 Pub/Sub 模式能力边界。
- **误报排除**: 通读两个方法全文确认无 options 分支。

### [P3] subscribe 失败路径在 subscriptions 留下 stale entry，后续订阅不再下发 SUBSCRIBE 命令

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettucePubSubService.java:46-59`
- **维度**: D4（错误路径）
- **证据**:
```java
SubscriptionEntry entry = subscriptions.get(topic);
if (entry == null) {
    entry = new SubscriptionEntry(topic);
    subscriptions.put(topic, entry);        // 先放入 map
    entry.addListener(listener);
    conn.sync().subscribe(topic);           // 若此处抛异常（连接故障），entry 已残留
}
```
- **现状**: `conn.sync().subscribe(topic)` 失败时（连接断开等），带 listener 的 entry 残留在 subscriptions；之后重试 subscribe 同 topic 走 else 分支仅 addListener，不再向 Redis 发 SUBSCRIBE，消息永远收不到且无报错。
- **风险**: 一次瞬时连接故障导致该 topic 永久哑订阅。
- **建议**: 将 `subscriptions.put` 移到 subscribe 成功之后，或 catch 后回滚 remove。
- **误报排除**: 读 else 分支确认不重发 SUBSCRIBE；确认 listener 挂在 entry 上、消息分发依赖连接层订阅状态。

### [P3] forEachEntryAsync 对每个 key 单独 get（N+1 往返），sync 版本却用 mget

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/impl/LettuceMessageService.java:133-149,240-246`
- **维度**: D6
- **证据**:
```java
// sync 版（140 行）: List<KeyValue<String, Object>> values = sync().mget(keys.toArray(new String[0]));
// async 版（241-245 行）:
for (String key : keys) {
    CompletableFuture<Void> f = async().get(key)      // 每 key 一次往返
            .thenAccept(value -> consumer.accept(key, value))
```
- **现状**: 同一能力 sync 用 mget 批量、async 用逐 key get；SCAN limit=100 时每批多 99 次网络往返。
- **风险**: 大库遍历（forEachEntryAsync）延迟显著放大。
- **建议**: async 版改用 `async().mget(keys)` 一次批量后分发。
- **误报排除**: 对比读了同文件两个实现；无正确性差异，纯性能。

### [P3] INosqlKeyValueOperations 的 putExAsync/getExAsync/setTimeoutAsync javadoc 复制粘贴了 hash 语义说明

- **文件**: `nop-persistence/nop-nosql/nop-nosql-core/src/main/java/io/nop/nosql/core/INosqlKeyValueOperations.java:19-23,25-28,39-43`
- **维度**: D8（文档/契约）
- **证据**:
```java
/**
 * Note: Expiry applies to the entire hash key, not to individual fields.   // string KV 操作不涉及 hash field
 * Redis hash fields do not support individual TTLs.
 */
CompletionStage<Void> putExAsync(String key, Object value, long timeout);
```
- **现状**: 三处注释是从 hash 实现拷贝的，对顶层 string 操作（单 key 自带 TTL）语义误导；`INosqlHashOperations` 继承这些方法时该注释才成立。
- **风险**: 误导使用者在 string ops 上担心不存在的限制。
- **建议**: 注释移至 `INosqlHashOperations` 的覆写处。
- **误报排除**: 读了接口全文与两个实现的 TTL 行为（string 版 psetex/getex 均为 key 级 TTL，注释描述的限制不存在）。

### [P3] PrefixTextCodec 对 `$d:类名` 文本做任意类加载反序列化，信任边界完全依赖 Redis 写权限

- **文件**: `nop-persistence/nop-nosql/nop-nosql-lettuce/src/main/java/io/nop/nosql/lettuce/codec/PrefixTextCodec.java:24-27`；`PrefixEncodeHelper.decode:95-97`（loadDataClass→classLoader.loadClass→JsonTool.parseBeanFromText）
- **维度**: D5
- **证据**:
```java
public Object decodeValue(ByteBuffer bytes) {
    String str = StringCodec.UTF8.decodeValue(bytes);
    return PrefixEncodeHelper.decode(str, ClassHelper.getSafeClassLoader());  // $d:className → loadClass + JSON绑定
}
```
- **现状**: 值解码允许从 Redis 存储文本加载任意类名并实例化绑定。能写 Redis 的攻击者本就等同拥有数据面控制权，但该 codec 把"数据写入"直接放大为"进程内任意类实例化面"（受 Jackson/JSON 绑定 gadget 约束）。
- **风险**: Redis 被入侵或被多方共享时，反序列化 gadget 风险传导到所有使用方进程（含 MFA 数据读取路径）。
- **建议**: 长期考虑类白名单（按前缀/包名过滤 loadDataClass），至少在文档中明确 Redis 实例的信任边界要求（专用、网络隔离、ACL）。
- **误报排除**: 读 loadDataClass/decode 全文确认 className 不经任何过滤直接 loadClass；确认 `ClassHelper.getSafeClassLoader()` 仅是类加载器选择而非白名单。

### [P3] rate_limit.lua 对 rate<=0 无防护：fill_time=inf 导致 setex 参数错误

- **文件**: `nop-persistence/nop-nosql/nop-nosql-core/src/main/resources/nop/redis/rate_limit.lua:9-10`；`LettuceRateLimiter.java:29-33`（构造无校验）
- **维度**: D1（边界输入）
- **证据**:
```lua
local fill_time = capacity/rate      -- rate=0 → inf
local ttl = math.floor(fill_time*2)  -- inf
redis.call("setex", tokens_key, ttl, new_tokens)   -- setex inf → ERR value is not an integer or out of range
```
- **现状**: `RateLimiterConfig(rate=0, ...)` 之类配置无任何校验即下发脚本，Redis 端报错后异常回传。
- **风险**: 配置错误以难懂的 Redis 服务端错误暴露（在 RESP3 问题修复前根本到不了这一步）。
- **建议**: `LettuceRateLimiter` 构造函数校验 `rate > 0 && capacity > 0`。
- **误报排除**: 读 RateLimiterConfig 与构造函数确认无校验；Lua math 语义确认。

## 补充说明（非缺陷）

- D7（平台规范）检查通过：`LettuceRedisConnectionProvider.setConfig` 为 `@Inject` setter 注入（非 private 字段）；无 Spring `@Value`；模块内无 beans.xml，bean 装配由使用方（如 nop-auth 的 `ioc:condition on-class`）条件化引用，符合"类不在 classpath 则不注册"的设计。
- `LettuceLock` 的单实例单线程约束已在类 javadoc 明示；`lockValue` 为 volatile；unlock 走 REMOVE_IF_MATCH Lua CAS（BooleanOutput 覆写 set(boolean)，RESP3 安全），不会误删他人锁。
- `LettuceCounter` 的 `reset/getAndReset` 存字符串数字与 `incrby` 存裸数字在 Redis 层字节一致，`toLong` 解析兼容两者，无失配。
- `DigestedText` 用 SHA1 hex，与 Redis EVALSHA 摘要算法一致；`LettuceExecutor.evalScript` 的 NOSCRIPT 回退（evalsha→eval）处理正确。
- `RoundRobinSupplier.resize` 的扩缩容关闭逻辑（nop-commons）与 provider 的用法组合无泄漏（连接由 client.shutdown 关闭）。
- nop-cdc 仅有空常量接口 `CdcConstants` 与两个 pom（nop-cdc-debezium 无任何 Java 源），无可审实现代码。
