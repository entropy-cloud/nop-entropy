# C2 维度（锁与同步）第 1 轮初审报告

## 基本信息

- **审计单元**: `nop-service-framework-hotpath`（请求热路径五模块，8 源码根，406 文件 / 42,748 行）
  - nop-graphql（core 145f / orm 11f / message 3f / grpc 37f）
  - nop-biz（89f）
  - nop-gateway（44f）
  - nop-biz-auth-core（60f）
  - nop-biz-file-core（17f）
- **仓库路径**: `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`）
- **维度定义 C2**: 锁的正确性与纪律——锁序与死锁环、锁粒度与锁内 I/O、Condition/wait 丢失唤醒、synchronized 范围、CAS 复合操作、锁选错对象。
- **方法论依据**: `ai-dev/skills/concurrency-resource-audit-prompt.md` C2 节 + `ai-dev/skills/deep-audit-prompts.md` 附录 A；基线 `_tmp/cra-baseline-nop-service-framework-hotpath.txt`。

## 盲审纪律声明

本报告为**第 1 轮盲审轮次**产物。审计过程中未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何历史文件；仅阅读了允许的两份方法论文件与当前 live code。对 C1 轮移交项（BufferedStreamingPublisher、BizObjHelper）按提示词描述独立取证；对 C1 轮已立案站点（UserContextImpl、GraphQLSubscriptionManager）按本轮任务要求**从锁角度审同一设施**，并在失效面枚举中以「C1 盲审互引」方式标注（无法引用 C1 具体编号）。

## 检查范围说明（锁站点矩阵）

对五模块全部 `synchronized` / `ReentrantLock` / `Collections.synchronizedXxx` / `computeIfAbsent`+`compute` / CAS 复合操作站点逐一判定（grep 口径：`synchronized|ReentrantLock|ReadWriteLock|Collections.synchronized|.lock()|.lockInterruptibly|Condition` 与 `computeIfAbsent|computeIfPresent|.compute(|merge(`，src/main/java，排除 `_gen`，两口径交叉核对）：

| # | 站点 | 载体 | 判定 |
|---|------|------|------|
| 1 | `GraphQLExecutionContext.dispatchAll()` :156 | synchronized(this)（每请求上下文） | **立案 C2-001**（锁内 dispatch→biz/DB） |
| 2 | `BeanMethodBatchFetcher.get()` :64 | synchronized(context) | **立案 C2-001（同锁族）**——注册+load 与 dispatchAll 同锁（P1-2.6-004 修复意图明确），临界区内仅入队，轻 |
| 3 | `OrmBatchLoader.makeDataLoader()` :45 | synchronized(context) | **立案 C2-001（同锁族）** |
| 4 | `GraphQLExecutor` resultLock :56,391,414,474,481 | private final Object（每引擎单例） | **立案 C2-006**（全局锁守护每请求局部状态） |
| 5 | `BufferedStreamingPublisher` BufferedSubscription monitor :158,190,208,340,371,383,393,404 | synchronized(this)（每流实例） | **立案 C2-002**（C1 移交深化：锁内 HTTP 发起/重试回调/重连） |
| 6 | `BizObjHelper.loadByProp()` :34 | CHM.computeIfAbsent（每请求 MapCache） | **立案 C2-003**（C1 移交深化：锁内 biz invoke/DB + 嵌套 compute 形态） |
| 7 | `UserContextImpl` :267,276,285,293 | synchronized(this)（4/25 访问器） | **立案 C2-004**（混合同步纪律 + clearDirty/publish 复合操作；C1 同设施互引） |
| 8 | `GraphQLSubscriptionManager` register/unregister :84-124 | CHM×2 无统一锁 | **立案 C2-005**（双 map 复合更新原子性；C1 TOCTOU 同设施互引） |
| 9 | `LoginAttemptStores.sharedLocalDefault()` :31 | synchronized(class) + volatile DCL | 正面样例，复核通过（DCL 配 volatile 正确） |
| 10 | `LocalLoginAttemptStore.incrementLoginFailCount()` :69 | synchronized(this) RMW | 正面样例，复核通过（丢失更新防护成立；读者无锁读 stale 值对限流计数无害） |
| 11 | `JwtAuthTokenProvider.ensureKeys()` :87 + 未同步读 :105,111,118,125 | synchronized(this) + plain HashMap | 复核通过：所有读者必先经 ensureKeys 获锁一次，monitor entry 建立 HB 后读——发布安全成立；全局监视器每请求经过但临界区 O(1)，不立案（矩阵注记） |
| 12 | `LocalMfaChallengeStore` markVerified/incrFail :122,143 | CHM.compute + AtomicInteger | 正面样例，复核通过（首写迁移原子、失败计数原子、双参 remove） |
| 13 | `LocalSmsCodeStore.verify` :70 / `LocalEmailCodeStore.verify` :93 | CHM.compute | 正面样例，复核通过（验证+消费+超限作废单 compute 原子完成） |
| 14 | `CancelTokenManager` registerInternal/cancel :63-110 | CHM CAS 环 + remove(k,v) | 正面样例，复核通过（putIfAbsent 自旋、值比对条件删除，幂等重绑定语义自洽） |
| 15 | `AiRateLimitGatewayInterceptor` :114-132 DCL + :161 TokenBucket | synchronized(this) DCL + volatile + synchronized per-bucket | 正面样例，复核通过（DCL 配 volatile；桶级细粒度锁；key 空间有界） |
| 16 | `GatewayContextImpl` :68-107 synchronized 访问器 | synchronized(this)（每请求） | 不立案：底层 attribute store 为 CHM（`ExecutionContextImpl` :43），synchronized 冗余但无害；**纪律不一致注记**：`getQueryParams/setQueryParams/getHttpMethod/setHttpMethod` :110-135 未同步——因 store 本身线程安全，无正确性后果（P3 矩阵注记） |
| 17 | `BizObjectImpl.asProxy()` :283 | synchronized 惰性初始化 | 复核通过（check-then-act 全程在锁内，安全发布成立；无锁外读） |
| 18 | `TypeRegistry.normalizeType` :107,114,121 | CHM.computeIfAbsent | 复核通过（lambda 内无对同 map 的递归计算，registerType 写的是另一 CHM；调用面为启动/刷新期 schema 构建，非请求热路径） |
| 19 | `GraphQLEngine` documentCache :321 | Caffeine LoadingCache per-key load | 复核通过（miss 时 per-key 串行加载，无全局锁；`parseDocumentWithLoader` :283-291 预热后放入，stampede 被 Caffeine per-key future 吸收） |
| 20 | `GatewayInterceptorModel.getOrCreateInterceptor` :25-37 | volatile 良性竞态 | 复核通过（重复创建幂等，注释已声明契约） |
| 21 | `MediaTypeHelper` :34-37,57-63 | volatile flag + CHM | 复核通过（check-then-act 竞态已被注释声明为幂等无害，CHM put 提供发布） |
| 22 | `TransactionActionDecorator.decorate` :38-50 / `BizInterceptorModel` | 无状态包装 | 复核通过（无实例字段，无同步纪律问题；事务边界由 ITransactionTemplate 承接，锁面在 nop-dao 越界不审） |
| 23 | `GraphQLScalarType`/`GraphQLDirectiveLocation` static HashMap :45-47/:23 | static 初始化只读 | 复核通过（static initializer 写、后只读，类初始化 HB） |
| 24 | `GraphQLArgsHelper` :42 LinkedHashMap.computeIfAbsent | 每请求局部非并发容器 | 复核通过（单请求构建路径，无跨线程共享证据） |
| 25 | `GraphQLBizModels` :79,119 computeIfAbsent | 启动期注册 | 复核通过（非热路径） |

**锁序图**（文本；节点=监视器，边=A 持有下获取 B）：

```
G1 GraphQLExecutionContext(this)  ──(dispatchAll→dispatch→batchLoader→biz invoke)──▶ biz拦截链 ──▶ 事务管理器/DB连接池[越界]
G1 ──(reentrant)──▶ G1                    [BeanMethodBatchFetcher.get / OrmBatchLoader.makeDataLoader]
G1 ──(理论链，见C2-001)──▶ G1 经 syncGet 等待「完成回调需 G1 的 future」
G2 GraphQLExecutor.resultLock ──▶ 无出边（叶）[但被全部请求共享，见C2-006]
B1 BufferedSubscription(this) ──▶ HTTP客户端内部锁[越界] / retryCallback[外部代码] / lifecycle[外部代码] / 下游subscriber锁[未知]
B1 ──(startAttempt→subscribe→AttemptSubscriber.onSubscribe，reentrant)──▶ B1
B3 TokenBucket(this)＝叶；B4 AiRateLimit(this)＝叶
A1 UserContextImpl(this)＝叶 [但读者侧不取该锁 → 锁面失效，见C2-004]
A2 LocalLoginAttemptStore(this)＝叶；A3 LoginAttemptStores.class＝叶；A4 JwtAuthTokenProvider(this)＝叶
M1 BizObjHelper 请求cache CHM bin ──(mapping内)──▶ biz invoke ──▶ DB；──(嵌套compute，理论链)──▶ M1' 同map另一bin
M2 GraphQLSubscriptionManager subscriptionsByOperation bin＝叶 [双map复合无锁，见C2-005]
```

**死锁环结论**: 静态可证的双监视器交叉环未发现。两条理论链（G1 自环经 syncGet、M1 嵌套 compute bin 交叉）写不出完整可达调用链，按判级口径标 P2 并注明「理论链未完全具体化」，分别并入 C2-001 / C2-003。

**wait/notify 与阻塞等待面**: 五模块内无裸 `wait()/notify()`；`GraphQLExecutor` :618 的 `syncGet` 在 `waitAll().thenApply` 内执行，此时全部 future 已完成，非阻塞等待点；入口级 `IGraphQLEngine` :101/:110 `syncGet` 为 HTTP worker 同步取结果的标准形态。`JsonRpcWebSocketHandler` keepalive 定时器阻塞面登记为 CL-004（C4/C7 形态）。

---

## 第 1 轮（初审）发现

### [nop-service-framework-hotpath-C2-001] GraphQLExecutionContext.dispatchAll 持上下文监视器同步执行批量 loader 的完整 biz 调用与 ORM flush（锁内 I/O + 同请求线程串行化）

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/engine/GraphQLExecutionContext.java:156-170`；关联 `nop-graphql-orm/.../fetcher/OrmBatchLoader.java:31-38,45-54`、`nop-graphql-core/.../fetcher/BeanMethodBatchFetcher.java:64-81`、`nop-graphql-core/.../engine/GraphQLExecutor.java:200-205`
- **证据片段**:
  ```java
  @Override
  public synchronized CompletionStage<Void> dispatchAll() {   // ← 持 this（每请求上下文）监视器
      ...
      for (Map.Entry<String, DataLoader> entry : loaders.entrySet()) {
          DataLoader loader = entry.getValue();
          futures.add(loader.dispatch());                      // ← dispatch 在锁内执行
      }
      return FutureHelper.waitAll(futures);
  }
  // OrmBatchLoader.load（dispatch 同步回调，同一调用线程）:
  ormTemplate.requireSession().getBatchLoadQueue().flush();    // ← DB 批量加载 I/O 在 G1 锁内
  ```
- **严重程度**: P2
- **并发场景**: 同一 GraphQL 请求的多线程并发游走（GraphQLExecutor 的 query 并行字段/`_fetchSelections` 完成回调线程）+ 持 G1 监视器的 dispatch 线程。dispatchAll 是每个注册了 DataLoader 的请求的回合出口必经点（`GraphQLExecutor.java:98/120/145/156/189` 五个调度点 + 批完成递归 :203）。
- **触发时序**: T1（字段游走线程 A）到达回合出口 → `context.dispatchAll()` 获 G1 监视器 → `loader.dispatch()` 在同线程同步回调 `OrmBatchLoader.load` → `getBatchLoadQueue().flush()` 执行 SQL（网络往返，毫秒~十毫秒级）；同时 T2（同请求另一字段的完成回调线程）执行 `thenFetchNext → dispatchAll` 或 `BeanMethodBatchFetcher.get`（:64 `synchronized(context)`）或 `getRequest()`（:127 synchronized）→ 全部阻塞在 G1 上，直到 T1 的 DB flush + 整批 biz 调用结束。`BeanMethodBatchFetcher` 的批量分支同理：`FutureHelper.futureCall`（api-core :122-129，**同步执行 callable**）使 `realFetcher.apply` 即完整 biz action（拦截器链+事务+DB）在 G1 锁内运行。
- **现状**: `synchronized(context)` 的锁纪律是 P1-2.6-004 修复的刻意设计（注册/load/dispatch 三方同锁防丢 key，`BeanMethodBatchFetcher.java:61-79` 注释完整），**互斥正确性成立**；问题在临界区工作量——锁保护意图是「注册表快照+清空」的原子性（微秒级），实际临界区放大为整批数据装载（毫秒~百毫秒级）。
- **风险**: 带嵌套关联字段的 GraphQL 查询（CRUD 关系属性的标准产物，常见条件）在同请求内把并行游走退化为串行：任一批装载期间，其余字段的完成回调、loader 注册、请求读取全部停等；高并发下请求尾延迟被批装载时长放大。理论死锁链（未能完全具体化，故不升 P1）：若锁内 biz 调用最终同步等待某个「其完成回调需要 G1 监视器」的 future（如 executor 泵 `promise.thenAccept(v -> this.dispatchAll(context))` 挂在该 future 上），形成 G1 自环——T1 持 G1 在 syncGet 等待 F，F 的完成链需要 T2 执行 dispatchAll 而 T2 阻塞在 G1。未找到生产 biz 路径在批装载内 syncGet 同请求 future 的实例，标注为理论风险。
- **建议**: 收窄临界区——`dispatchAll` 仅在 `synchronized` 内做「快照+清空」（参照 :160-161 已有快照逻辑），把 `loader.dispatch()` 循环移到锁外执行；同锁三方纪律保持（load 注册仍在锁内，dispatch 消费的注册表已是快照，丢 key 竞态不回归——因 dispatch 消费的是快照，晚到 load 重新注册新实例被后续回合承接，与注释声明的承接机制一致）。改造后需回归 P1-2.6-004 对应测试。
- **失效面枚举（F3）**: 同锁族全部入口逐一核对——(1) `GraphQLExecutionContext.dispatchAll` :156（本体，本案）；(2) `GraphQLExecutor` 五个调度点 :98/:120/:145/:156/:189（调用方，随本体修复自动受益）；(3) `BeanMethodBatchFetcher.get` :64-81（临界区内仅 `loader.load()` 入队，轻工作，无锁内 I/O，豁免）；(4) `OrmBatchLoader.makeDataLoader` :45-54（仅注册，轻，豁免）；(5) `GraphQLExecutor.dispatchAll` :200-205（包装层，随本体）。无遗漏同族站点。
- **信心水平**: 确定（同步执行链有 api-core `FutureHelper.futureCall` :122-129 与 `ResolvedPromise` 语义背书；若底层 org.dataloader 版本引入异步 batch executor，则锁内 I/O 退化为「锁内任务派发」，本案自动降级——当前仓库版本为同步语义）
- **误报排除**: 不是「synchronized 原语使用本身」（误报校准第 2 条）——本案的判定对象是临界区内含 DB I/O 与完整 biz 调用的复合后果，且给出了具体线程交错；也不是低频路径粗锁（误报校准第 3 条）——loader-bearing 查询是每请求常见路径。
- **复核状态**: 未复核
- **复核裁定（独立复核 2026-10-18）**: **维持 P2**。全链亲验：`dispatchAll` synchronized :156-170 快照+清空+dispatch 循环均在 G1 锁内；`OrmBatchLoader.load` 首行 `getBatchLoadQueue().flush()`（SQL）经 java-dataloader 3.2.2 同步 BatchLoader 在 dispatch 线程执行（版本实测 `nop-kernel/nop-dependencies/pom.xml:80`）；`BeanMethodBatchFetcher` 批回调经 `FutureHelper.futureCall`（api-core :122-129 `task.call()` 同步执行亲验）在锁内跑完整 biz action；同锁三方契约（P1-2.6-004 注释）与 executor 五调度点、递归泵 :200-205 亲验一致。判级恰当：每请求监视器、临界区毫秒级 I/O、无死锁实证（G1 自环维持理论注记，沿第 2 轮 ③ 终判）。现状：新发现/未修复（文件最后提交 2026-10-11 P1-2.6-004 返工，早于本审计）。

### [nop-service-framework-hotpath-C2-002] BufferedStreamingPublisher 在流监视器内发起上游 HTTP 连接、执行重试回调与生命周期监听器，cancel/request 被慢连接阻塞（C1 移交项深化立案）

- **文件**: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/executor/BufferedStreamingPublisher.java:207-237,298-326,383-410`
- **证据片段**:
  ```java
  void startFirstAttempt() {
      synchronized (this) {                 // ← 持流监视器
          ...
          startAttempt(initialRequest);
      }
  }
  private void startAttempt(HttpRequest httpRequest) {
      ...
      Flow.Publisher<IServerEventResponse> eventPublisher =
              httpClient.fetchServerEventFlow(httpRequest, context);   // ← HTTP fetch 在锁内
      Flow.Publisher<Object> mapped = mapper.apply(eventPublisher);
      mapped.subscribe(new AttemptSubscriber(state));   // ← 映射链订阅期同步发起 HTTP（类注释 :217 声明）
  }
  // onError 路径同样在锁内：synchronized{ handleError(state, throwable) } → retryCallback.retry(...) → startAttempt(newRequest)
  ```
- **严重程度**: P2
- **并发场景**: 网关流式路由（AI 代理 SSE/NDJSON）：HTTP 客户端 IO 线程（上游事件）、下游订阅者线程（`StreamingResponse` 写出）、客户端断连处理线程（`abort()` → `BufferedSubscription.cancel()`）。维持 C1 移交判定并深化：锁内不只是「HTTP fetch 引用获取」，而是 fetch + 映射链订阅（同步发起连接）+ 重试回调 + 重连 + `lifecycle.onFetchStarted/onStreamTerminated`（:227、:319，handleError 路径在锁内触发）全链。
- **触发时序**: T1（订阅线程）`startFirstAttempt()` 持 B1 监视器 → `startAttempt` → 映射链订阅同步发起对上游的 TCP/TLS 连接，上游接受缓慢时阻塞至 connect 超时（秒级）；T2（客户端断连线程）执行 `StreamingResponse.abort()` → `upstreamSubscription.cancel()` → `BufferedSubscription.cancel()` :190 `synchronized(this)` → 阻塞到连接建立完成才取消。同理窗口内上游报错时，T1 在 B1 内执行 `retryCallback.retry()`（外部回调，可能查路由/配置）并发起新连接，期间下游 `request(n)` :158 与上游 `onNext` :383 全部停等。
- **现状**: 监视器保护 attempt 状态机的意图正确（buffer/passed/forwarded/attempt/pendingComplete 契约自洽，`AttemptSubscriber.onSubscribe` :371 对被放弃 attempt 的取消防护完整）；缺陷是临界区含网络 I/O 与任意外部回调（retryCallback/lifecycle/mapper）。
- **风险**: 流式路由常见条件（缓冲启用 + 首段窗口 + 重试配置）。不取消即不释放：客户端断连的取消语义被慢连接拖长（取消延迟=connect 超时上限），断连后上游连接/握手资源滞留；多路复用同一线程池时放大为 IO 线程占用。未发现 B1 参与的双锁交叉环（HTTP 客户端内部锁获取后未回调回 B1 的路径——`onSubscribe` :371 取 B1 为 reentrant），死锁不成立。
- **建议**: 将「fetch+订阅」移出监视器：监视器内仅完成 `attempt` 槽位置换与 cancelled 检查（快照式），fetch 在锁外发起后经 `onSubscribe` 的 `state != attempt` 防护（:372 已有）裁决归属；`retryCallback.retry` 与 `lifecycle` 回调同样移出临界区（先在锁内快照决策输入，锁外执行回调，回锁内置换 attempt）。cancel 路径已用 `cancelled` AtomicBoolean 前置短路（:185），改造后取消可即时生效。
- **失效面枚举（F3）**: 同类「锁内外部交互」站点逐一核对——(1) `startFirstAttempt` :208（本案）；(2) `AttemptSubscriber.onError → handleError → retry/reconnect` :392-400（本案同链）；(3) `terminate` :340-357（`lifecycle.onStreamTerminated` 已在锁外 :347-349，正确范本）；(4) `cancel` :184-203（`upSub.cancel` 与 lifecycle 已移出锁外，正确范本）；(5) `request` :151-177（下游 `subscriber.onNext` :263/:276 在锁内调用——下游为 `StreamingResponse`，无再入 B1 的路径，无死锁，但与 (3)(4) 的「回调出锁」纪律不一致，随本案一并整改）。
- **信心水平**: 确定（锁内发起 HTTP 由代码直接可见；「映射链订阅期同步发起 HTTP」为类注释 :217-218 自述契约）
- **误报排除**: 非误报校准第 2/3 条——不是原语使用或低频粗锁；触发条件为流式路由 + 慢上游/客户端断连，属该 publisher 存在的全部意义场景。C1 轮仅移交线索，本轮已按完整 13 字段立案深化。
- **复核状态**: 未复核
- **复核裁定（独立复核 2026-10-18）**: **保留，降级 P2→P3（复核新取证）**。锁内回调面事实全部证实：`startFirstAttempt/startAttempt` 持 B1 发起 fetch+订阅、`onError` 锁内 `retryCallback`/重连、第 2 轮 C2-002-R2 的 terminate 三链监视器重入锁内化逐行亲验证实（request :172-177 / onError :392-400 / onComplete :403-409；`cancel` :190-199 为唯一真锁外范本亦核验成立）。**但核心量化危害被推翻**——初审「锁内阻塞至 connect 超时（秒级）、取消延迟=connect 超时上限」不成立：两个 shipping HTTP 客户端的 HTTP 发起均为**非阻塞任务提交**（JDK 客户端 `ServerEventSubscription.startRequest` = `client.sendAsync`；Apache 客户端 = HC5 异步 `client.execute(producer, consumer, ctx, FutureCallback)`；`AbstractServerEventSubscription.request` :31-47 惰性 `startRequest`）——持 B1 期间仅发生发布器构造+订阅+request(1) 提交，真实连接在客户端 IO 线程完成；映射拦截链（`proceedOnStreamElement`/JSON.parse）在上游 IO 线程 B1 锁外执行（StreamingProcessor:149-166 亲验）。残余锁内工作 = 下游信号写出（需求驱动下有界，阻塞性容器相关，盲区自评(4) 已声明）+ 缺省 null 的扩展点回调（StreamingProcessor:90-92 retryCallback/lifecycle 经 attribute 注入，缺省不安装）+ 微秒级提交。按判级校准，「秒级取消延迟」的 P2 承重前提不成立后回落 P3（防御性/锁纪律统一域）。**R2 失效面扩大维持为修复指引**：terminate 改「锁内快照 + 锁外回调」仍建议随修复轮执行。现状：新发现/未修复（文件最后提交 2026-09-12，早于本审计）。

### [nop-service-framework-hotpath-C2-003] BizObjHelper.loadByProp 在 CHM computeIfAbsent 映射函数内执行完整 biz invoke（校验链+DB 查询）：bin 锁内 I/O 与嵌套 compute 形态（C1 移交项深化立案）

- **文件**: `nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/BizObjHelper.java:32-42`；调用方 `nop-biz/.../crud/ObjMetaBasedValidator.java:445-475`；承载 `nop-kernel/nop-commons/.../cache/MapCache.java:86-88`
- **证据片段**:
  ```java
  public static Object loadByProp(IBizObject bizObj, String propName, Object propValue, IServiceContext context) {
      String bizObjName = bizObj.getBizObjName();
      Object refEntity = context.getCache().computeIfAbsent(          // ← MapCache→CHM.computeIfAbsent
              Arrays.asList(propValue, propName, bizObjName, BizConstants.KEY_REF_ENTITY), k -> {
          QueryBean query = new QueryBean();
          query.addFilter(FilterBeans.eq(propName, propValue));
          Map<String, Object> request = new HashMap<>();
          request.put(BizConstants.ARG_QUERY, query);
          return bizObj.invoke(METHOD_FIND_FIRST, request, null, context);  // ← 完整 biz action 在 bin 锁内
      });
      return refEntity;
  }
  ```
- **严重程度**: P2
- **并发场景**: 实体保存校验路径（`ObjMetaBasedValidator.validateRefValue` :466，`gen_*save/update` mutation 必经的 ref 值校验）。同请求内嵌套校验链可对同一 CHM 递归进入 computeIfAbsent（A 对象校验引用 B → B 的 findFirst 校验链再 loadByProp 其他对象）。`context.getCache()` 为每请求 `MapCache("service-cache", threadSafe=true)`（`ServiceContextImpl.java:137-141`），底层 `ConcurrentHashMap`。
- **触发时序**: T1 执行 `validateRefValue(A)` → `computeIfAbsent(keyA)` 持 keyA 所在 bin 的节点监视器 → 映射函数内 `bizObj.invoke(findFirst)` 走拦截器链→校验器→DB 查询（毫秒级）；期间同请求的并发游走线程（并行 GraphQL 字段触发同一 context 校验/装载）或嵌套校验链的 computeIfAbsent(keyB) 若与 keyA 同 bin（哈希碰撞/相邻桶）则阻塞在 bin 监视器上；若校验链形成 A→B→A' 递归且 key 相同，CHM 直接抛 `IllegalStateException: Recursive update` 使整个 mutation 失败。跨 bin 的嵌套 compute 在两线程互持对方目标 bin 时构成理论 ABBA 环（未找到稳定复现的哈希布局，标注理论链）。
- **现状**: findFirst 结果缓存语义正确（同请求同引用只查一次库），CHM 提供了防 stampede 互斥；缺陷与 C2-001 同型——bin 锁的持有时长被映射函数内的重工作（biz action = 权限检查+校验器+事务+SQL）放大，且嵌套 compute 是 CHM 规范明确「不可靠」的形态（java.util.Map.computeIfAbsent 契约：mapping function 不得修改本 map）。
- **风险**: 保存校验是 mutation 热路径；请求内多 ref 字段并发/嵌套校验时出现 bin 级串行与偶发 Recursive update 异常（fail-loud 但请求失败）；同键返回 null（引用不存在）时不缓存，每次校验重复查库（相邻 C5/C9 形态，顺带登记不改判级）。
- **建议**: 锁内只做「查缓存」，映射逻辑移出：`get(key)` 命中即返；miss 时锁外执行 biz invoke，再 `putIfAbsent(key, result)`（先到者胜，重复查询容忍一次）。或改用平台 `LocalCache.computeIfAbsentAsync`/Caffeine per-key loading（`MapCache` 已具备）以获得 per-key 而非 bin 级互斥。同时为 null 结果引入哨兵值避免重复查库。
- **失效面枚举（F3）**: 全审计范围 `computeIfAbsent/compute` 带 I/O 映射的站点逐一核对——(1) `BizObjHelper.loadByProp` :34（本案，唯一映射内含 biz invoke/DB）；(2) `AiRateLimitGatewayInterceptor` :58（new TokenBucket，纯构造，豁免）；(3) `OpenAiDeltaAccumulator` :86（new ToolCallAccumulator，豁免）；(4) `GraphQLSubscriptionManager` :99（new CopyOnWriteArrayList，豁免，另案 C2-005）；(5) `TypeRegistry` :107/:114/:121（启动期，lambda 无同 map 递归，豁免）；(6) `GraphQLBizModels` :79/:119（启动期，豁免）；(7) `LocalSmsCodeStore` :70 / `LocalEmailCodeStore` :93 / `LocalMfaChallengeStore` :122/:143（auth store，映射轻且无嵌套，正面样例）；(8) `GraphQLArgsHelper` :42（非并发容器，豁免）。嵌套 compute 形态唯一本案。
- **信心水平**: 确定（映射内 biz invoke 与 MapCache→CHM 承载链均有代码证据；ABBA 环为理论链未具体化，按口径维持 P2）
- **误报排除**: 非误报校准第 2 条——CHM/computeIfAbsent 使用本身不是问题，「映射函数内重 I/O + 嵌套 compute」这一复合误用才是；非越界审计 nop-kernel——被审对象是 nop-biz 的使用方式（MapCache 仅作承载证据引用）。
- **复核状态**: 未复核
- **复核裁定（独立复核 2026-10-18）**: **维持 P2**。全链亲验：`loadByProp` :33-42 computeIfAbsent 映射内 `bizObj.invoke(METHOD_FIND_FIRST)`；承载链 `ServiceContextImpl.getCache` :136-142 `new MapCache<>("service-cache", true)` → `MapCache.computeIfAbsent` :86-88 直通 CHM（:31 threadSafe→ConcurrentHashMap 实测）；唯一生产调用方 `ObjMetaBasedValidator` :466（gen_*save/update ref 校验必经）实测。嵌套 compute/Recursive update 机制描述正确（同键同线程递归 CHM 抛 IllegalStateException）、ABBA 环维持理论注记（生产递归链未寻得，两轮口径一致）。判级恰当：mutation 校验热路径 bin 锁内完整 biz action，与 C2-001 同型。现状：新发现/未修复（文件最后提交 2025-12-05，早于本审计）。

### [nop-service-framework-hotpath-C2-004] UserContextImpl 混合同步纪律：仅 4 个写方法持锁而角色集读写与 dirty 复合操作无锁，saveUserContextAsync 的 clearDirty→发布链与并发 markDirty 竞态丢失持久化（C1 同设施互引）

- **文件**: `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/login/UserContextImpl.java:220-231,267-298,320-331,344-353`；`nop-biz-auth-core/.../login/AbstractUserContextCache.java:127-132`
- **证据片段**:
  ```java
  public Set<String> getRoles() { return roles; }              // 无锁读
  public void setRoles(Set<String> roles) { ... this.roles = roles; dirty = true; }  // 无锁写
  @Override public synchronized void addRole(String roleId) {  // 持 this 锁写
      if (roles == null) roles = new HashSet<>();
      if (roles.add(roleId)) dirty = true;
  }
  ...
  @Override public boolean isUserInRole(String roleId) {
      ...
      return roles != null && roles.contains(roleId);          // 无锁读 HashSet
  }
  // AbstractUserContextCache.saveUserContextAsync:
  public CompletionStage<Void> saveUserContextAsync(IUserContext userContext) {
      userContext.clearDirty();                                // 无锁复合操作：清脏→随后发布到共享缓存
      return userSessionCache.putAsync(...).thenCompose(v -> userContextCache.putAsync(...));
  }
  ```
- **严重程度**: P1
- **并发场景**: `UserContextImpl` 实例经 `LocalUserContextCache`/`AbstractUserContextCache` 的 `userContextCache`（Caffeine LocalCache）跨请求共享——同一用户的多个并发请求（多标签页/ token 刷新 + 业务请求并发）在不同 HTTP worker 线程上读写**同一实例**。
- **触发时序**: T1（请求 A，业务变更角色）执行 `addRole("admin")`（持 A1 锁，roles+dirty 已更新）；T2（持久化路径）`saveUserContextAsync` 执行 `clearDirty()`（:345-347，**无锁**）→ dirty=false → 序列化/上传缓存。交错 1：T2 的 `clearDirty` 在 T1 的 `addRole` 之后、序列化读取 `roles` 之前执行——本次变更被持久化但 dirty 已清，若序列化读取发生在 T1 写入前（交错 2：`clearDirty` 先执行，T1 的 addRole 在序列化快照后才可见）→ **角色变更永久丢失**（缓存与 Redis/Dao 持久层均不含新角色），直到该用户下一次任意 mutation 才意外带出。同理 `setRoles`（无锁）与 `addRole`（持锁）并发时 HashSet 替换可吞掉 addRole 的写入（check-then-set 非原子）。锁角度结论：4 个 synchronized 方法提供的互斥面对未持锁的 21 个访问器不闭合——锁选对了对象（this）但**锁面覆盖不全**，读者与兄弟写者均绕过该锁。
- **现状**: 混合同步纪律使 `synchronized` 成为装饰：写-写（setRoles vs addRole）、读-写（getRoles/isUserInRole/serializeToJson vs addRole）、dirty 复合操作（clearDirty/markDirty vs 持锁置脏）三个复合面上互斥均不成立。
- **风险**: 权限相关的会话状态（roles/attrs/mfaRestricted）在并发下丢失更新或读到撕裂集合（HashSet.contains/迭代 vs add 扩容 → CME 或不一致判定）；鉴权判定（`isUserInRole`）读到瞬时不一致角色集。触发条件常见（同用户并发请求），后果为权限语义漂移，故 P1。
- **建议**: 与 C1 轮对该设施的立案同根因合并修复：要么全访问器统一 `synchronized`（对象小，争用可忽略），要么全面去锁改用不可变快照 + `volatile` 引用发布（`roles` 用不可变 Set 整体替换，dirty 用 AtomicBoolean，`saveUserContextAsync` 的 clearDirty 改为「序列化后以 CAS 清脏」防清掉并发新变更）。修复须覆盖 F3 全列。
- **失效面枚举（F3）**: 同设施同族成员逐一核对——(1) `UserContextImpl` 全部 21 个未同步访问器（sessionId/tenantId/locale/timeZone/userId/openId/userName/nickName/primaryRole/deptId/deptName/loginType/roles/accessToken/refreshToken/lastAccessTime/mfaRestricted/dirty×3，本案）；(2) `UserContextImpl` 4 个 synchronized 方法（addRole/removeRole/setAttrs/setAttr——锁面失效的施加点，随本案统一）；(3) `AbstractUserContextCache.saveUserContextAsync` :128-132（clearDirty→publish 复合操作，本案证据）；(4) `serializeToJson` :49-74（无锁遍历 roles/attrs，同族读面）；(5) `JsonRpcWebSocketHandler` 的 volatile authHeaders/boundUserId（:60-62，已用 volatile 正确发布，豁免）；(6) `ExtensibleBean` 父类 attrs 存储（越界 nop-kernel，仅对照）。C1 轮已从可见性角度立案同设施（盲审互引，编号待复核阶段对齐）——本案为锁面/复合操作角度，合并裁定阶段应按「同根因不同失效面」合并为单一修复工单。
- **信心水平**: 确定（共享机制——userContextCache 跨请求持有同一实例——有 `AbstractUserContextCache.getUserContextAsync` :103-121 直接证据；交错序列可完整写出）
- **误报排除**: 非误报校准第 1 条——不是「无状态单例被并发调用」，实例字段可变且跨线程共享；非「看到共享字段就报线程不安全」——已给出具体丢失更新交错与锁面闭合性论证。判级依据「罕见但可具体化的竞态」+权限后果从 P2 上调 P1。
- **复核状态**: 未复核
- **复核裁定（独立复核 2026-10-18）**: **保留，降级 P1→P2，并入 C1-001（主 ID），与 C1 复核口径一致**。机制面全部亲验证实：混合同步纪律（4 个 synchronized :267-293 vs getRoles/setRoles :220-228、isUserInRole :322-331、clearDirty/markDirty :345-353 均无锁）；`AbstractUserContextCache.saveUserContextAsync` :127-132 clearDirty→put 链；共享实例机制（`auth-core-defaults.beans.xml` :45-48 默认装配 LocalUserContextCache + `getUserContextAsync` :101-121 缓存实例无拷贝外发）。**降级理由（与 C1-001 复核同构）**：P1 危害链「角色变更永久丢失……缓存与持久层均不含新角色」存在跨部署拼接缺陷——Local 部署下缓存条目即实例本体，变更留存于共享实例、下一次 flush 自然带出（无永久丢失）；持久化语义部署（Dao）下 DaoUserContextCache 每请求新建实例，共享面不存在。「机制」与「后果」无单一部署同时成立。**可达性修正（复核 grep 实测）**：`.addRole(`/`.removeRole(` 全仓 src/main 零生产调用方（仅 `TestUserContextImplSemantics`），setRoles 三个生产写点（LoginServiceImpl:704 / OAuthLoginServiceImpl:181 / demo WebAuthFilter:57）均发布前——P1 旗舰交错（addRole vs clearDirty）平台内当前不可达，触发依赖下游应用经 IUserContext 公共 API 运行期调用。维持 P2 而非 P3 依据：锁面不闭合为结构缺陷、IUserContext 为公共扩展面、修复必做，已并入 C1-001 设施工单（C1 复核「维度复核结论」第 3 条明示并案）。现状：新发现/未修复（文件最后提交 2026-08-17，早于本审计）。

### [nop-service-framework-hotpath-C2-005] GraphQLSubscriptionManager register/unregister 双 map 复合更新无统一锁：并发交错产生永不清理的孤儿订阅列表（C1 TOCTOU 同设施互引）

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/subscription/GraphQLSubscriptionManager.java:84-124`
- **证据片段**:
  ```java
  public void registerSubscription(SubscriptionInfo subscription) {
      ...
      subscriptionsById.put(operationId, subscription);            // map1 写（无锁）
      subscriptionsByOperation
              .computeIfAbsent(subscription.getOperationName(), k -> new CopyOnWriteArrayList<>())
              .add(subscription);                                  // map2 复合：取列表→追加（无锁）
  }
  public void unregisterSubscription(String operationId) {
      SubscriptionInfo subscription = subscriptionsById.remove(operationId);  // map1 删
      if (subscription != null) {
          List<SubscriptionInfo> ops = subscriptionsByOperation.get(subscription.getOperationName());
          if (ops != null) {
              ops.remove(subscription);
              if (ops.isEmpty()) {
                  subscriptionsByOperation.remove(subscription.getOperationName());  // map2 删空列表
              }
          }
      }
  }
  ```
- **严重程度**: P2
- **并发场景**: WS 订阅注册（WS 消息线程）与会话关闭清扫 `cancelSessionSubscriptions` :129-136 / 消息推送失败路径 `pushToClient → unregisterSubscription` :207（IMessageService 消费线程）并发操作同一 operationId / 同一 operationName 的列表。
- **触发时序**: T1（WS 线程）register(A)：:97 `subscriptionsById.put(A, s)` 完成 → 被 T2 抢占；T2（消息线程）unregister(A)：:110 `byId.remove(A)` 成功，:114 取 X 的列表（已空或移除 A 后为空），:117-118 `ops.isEmpty()` 为真 → `byOperation.remove(X)`；T1 恢复：:98 `computeIfAbsent(X)` **重建新列表**并 `.add(A)`。终态：A 在 `byOperation[X]` 中但不在 `byId` 中——孤儿条目。后续 `getSubscriptionsByOperation(X)` 持续返回 A；`pushToClient(A)` 对孤儿执行 `unregisterSubscription(A)` 时 `byId.remove(A)` 返回 null → 走不到 `ops.remove` 分支 → **孤儿永不清理**。
- **现状**: 两个 CHM 各自操作原子，但「map1 + map2」的复合不变式（byOperation 是 byId 的二级索引，二者一致）无任何锁/CAS 保护；C1 轮已从 check-then-act（容量与重复检查 TOCTOU）角度立案同设施，本案为二级索引一致性的锁角度，合并裁定阶段应判定同族相邻。
- **风险**: 订阅管理器为常驻单例：孤儿随时间累积（每次 register/unregister 同 opName 交错即 +1 条目），构成慢泄漏（C7 形态伴随）与幽灵匹配面；`maxActiveSubscriptions` 按 byId 计数，孤儿绕过该上限的可观测性。
- **建议**: 以 per-operationName 或全局细粒度锁统一 register/unregister（订阅注册为低频管理面操作，粗锁无性能顾虑——参照误报校准第 3 条，这正是「低频路径允许粗锁」的适用场景）；或重构为单一 `Map<String, List<SubscriptionInfo>>`（byId 值内含 opName）消除双 map 不变式。
- **失效面枚举（F3）**: 同类「复合不变式无锁」站点逐一核对——(1) `registerSubscription` :84-104（本案）；(2) `unregisterSubscription` :109-124（本案对称面）；(3) `cancelSessionSubscriptions` :129-136（快照流→逐个 unregister，间接触发本案时序，随修复自动闭合）；(4) `destroy` :264-280（PreDestroy 单线程，豁免）；(5) `pushToClient` :204-218（read→unregister 复合，随 (2) 闭合）；(6) 同设施 C1 已立案的容量/重复 TOCTOU（:87-95，盲审互引）。graphql 侧其余双容器站点（`JsonRpcWebSocketHandler.activeOperations` 用双参 `remove(k,v)` :479/:494 防误删——正确范本）。
- **信心水平**: 确定（交错序列完整可写出；consequence 链每一跳都有代码行号）
- **误报排除**: 非误报校准第 2 条——问题不在 CHM 原语而在跨容器复合操作原子性缺失；非「理论竞态凑数」——交叠后果（永久孤儿）有明确终态与不可清理证明（`unregisterSubscription` 对 byId-miss 的短路分支）。
- **复核状态**: 未复核
- **复核裁定（独立复核 2026-10-18）**: **维持 P2，与 C1-003 同设施并案**。逐行亲验：register :84-104（容量门/containsKey/put/computeIfAbsent+add 复合无锁）、unregister :109-124（byId.remove→ops.remove→remove-if-empty）；孤儿交错（register put 后被抢占 → unregister 摘除 byId → register 恢复 `computeIfAbsent` 重建列表+add）可完整构造；不可清理证明成立——孤儿清理三通道（unregister 的 byId-miss 短路 :111、cancelSessionSubscriptions 遍历 byId :129-136、destroy）均不可达孤儿。可达性注记沿 C1 复核口径：registerSubscription 8 源码根内无生产调用方（grep 实测），维持 P2（非 P3）依据=设施经 beans 装配、实触达在 ws 集成模块（超单元）。并入 C1 复核裁定的「订阅注册表工单（C1-003+C1-005+CL-002）」，二级索引一致性随该工单修复自动闭合。现状：新发现/未修复（文件最后提交 2026-08-26，早于本审计）。

### [nop-service-framework-hotpath-C2-006] GraphQLExecutor 以引擎级全局监视器（resultLock）守护每请求局部的结果 LinkedHashMap：跨请求串行化点位于每请求每字段写结果必经之路

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/engine/GraphQLExecutor.java:56,391-393,414-416,474-476,481-483`
- **证据片段**:
  ```java
  public class GraphQLExecutor implements IGraphQLExecutor {   // 每引擎单例（GraphQLEngine.java:894）
      private final Object resultLock = new Object();          // 引擎级全局锁
      ...
      // 三处每请求结果写入（:391/:414 mutation 结果、:474/:481 字段结果）：
      synchronized (resultLock) {
          result.put(alias, v);                                // result 为该请求的局部 LinkedHashMap
      }
  ```
- **严重程度**: P2
- **并发场景**: 全部并发 GraphQL/RPC 请求共享同一 `GraphQLExecutor` 单例 → 共享同一 `resultLock`；锁内保护区是**各请求自己**的 `LinkedHashMap`（`executeAsync` :169 `new LinkedHashMap<>()` / `fetchSelections` :432 局部 ret），不同请求的 result 之间无任何共享。
- **触发时序**: T1（请求 A 的完成回调线程）执行 `synchronized(resultLock){ resultA.put(...) }`；T2（请求 B 的完成回调线程）同时到达任意一处 result 写入 → 阻塞，直至 T1 释放。N 个并发请求 × 每请求 F 个字段 → 该全局监视器上的 acquire 次数为 O(N×F)，每次临界区仅一次 LinkedHashMap.put（O(1)），但 monitor 无偏向（JDK15+），高并发多字段下形成可证明的跨请求串行化点。
- **现状**: 正确性无恙（互斥充分，且 waitAll 完成语义提供了 ret 的发布）；缺陷是锁选错层级——为请求局部数据选了引擎全局锁，锁粒度与保护对象不匹配。
- **风险**: 高并发下吞吐随字段数放大衰减（可量化口径：并发请求数 × 每请求字段数 × put 持锁时长，单次约百纳秒级，万级 QPS + 深查询时进入可观测区间）；不构成正确性风险。
- **建议**: 锁下放到请求粒度：`DataFetchingEnvironment`/上下文内持有一个 per-request 锁对象（或直接以 result map 自身 `synchronized(result)`——同请求内写-写互斥已足够，跨请求自然隔离）；或改用并发结构消除锁。改动局限于四处 put 站点。
- **失效面枚举（F3）**: resultLock 全部使用点逐一核对——:391-393（mutation 串行链结果写入，本案）、:414-416（并行 operation 结果写入，本案）、:474-476（异步字段结果写入，本案）、:481-483（同步字段结果写入，本案）。全仓 grep 确认无第五处。同族「全局锁守护请求局部状态」其他站点：无（C2-001 的 G1 为每请求对象，不属此族）。
- **信心水平**: 确定（单例判定链：GraphQLEngine 为 IoC 单例 bean → `newGraphQLExecutor()` :894 每引擎一次；四站点的 result 均为请求局部变量）
- **误报排除**: 非误报校准第 2/3 条——原语使用无问题，问题在锁层级选择；非低频路径——executeAsync/fetchSelections 是每请求必经，字段写入是每字段必经。判 P2 而非 P3 的依据：判级口径「锁粒度过粗/可证明的争用热点」，该热点由构造直接可证。
- **复核状态**: 未复核
- **复核裁定（独立复核 2026-10-18）**: **撤案成立，改判正面样例（附 P3 注记）**。第 2 轮推翻证据链经独立核实全部成立：`newGraphQLExecutor()` :893-894 为 `return new GraphQLExecutor(...)` 每次调用新建；三入口 :558（executeRpcAsync）/:601（executeGraphQLAsync）/:878（fetchResultWithSelection）均为每请求/每调用粒度亲验；全仓 grep 无 GraphQLExecutor 的 beans.xml 单例注册（仅测试日志命中）；resultLock 四站点守护的 response :169 / ret :265/:432 均为方法局部 LinkedHashMap，写者为同请求各异步字段完成回调（waitAll 扇出后可跨线程）——互斥必要且正确，锁粒度=请求级恰与保护对象共享范围一致。第 1 轮「单例判定链」确系对 `newGraphQLExecutor()` 的误读。P3 残余注记（单请求并行字段在该请求级监视器上的争用）维持不立案。详见文末「维度复核结论」。

---

## 正面样例（复核确认，不立案）

| 站点 | 结论 |
|------|------|
| `LoginAttemptStores.sharedLocalDefault()` :28-40 | volatile + synchronized(class) DCL，发布正确（C1 正面样例复核确认） |
| `LocalLoginAttemptStore.incrementLoginFailCount` :67-75 | 读取-递增-写回单临界区，丢失更新防护成立 |
| `LocalMfaChallengeStore` :117-153 | CHM.compute 首写迁移 + AtomicInteger + 双参 remove，复合操作全部原子 |
| `LocalSmsCodeStore.verify` :64-89 / `LocalEmailCodeStore.verify` :88-111 | 验证+消费+超限作废单 compute 完成，无 TOCTOU |
| `CancelTokenManager` :63-110 | putIfAbsent 自旋注册 + remove(k,v) 条件删除 + 幂等重绑定，归属语义自洽 |
| `AiRateLimitGatewayInterceptor` :114-132,161-178 | volatile DCL 正确；TokenBucket per-实例锁粒度恰当；key 空间有界 |
| `JwtAuthTokenProvider.ensureKeys` :87-106 | 所有未同步读均被「先经一次 synchronized ensureKeys」覆盖，monitor-entry HB 使 HashMap 发布安全成立 |
| `BizObjectImpl.asProxy` :283-289 | synchronized 惰性初始化，全程锁内，无锁外读 |
| `MediaTypeHelper` :34-37 | check-then-act 竞态已被注释声明为幂等无害（CHM put 提供发布），符合声明边界豁免 |
| `GatewayInterceptorModel.getOrCreateInterceptor` :25-37 | volatile + 幂等重复创建，声明契约成立 |

## 维度集外线索（不立案展开）

### [nop-service-framework-hotpath-CL-004]（维度集外线索，不立案展开）JsonRpcWebSocketHandler 保活定时任务在全局共享 timer 线程上执行潜在阻塞的网络写

- **文件**: `nop-graphql-core/.../ws/JsonRpcWebSocketHandler.java:82,357-363`
- **形态**: C4/C7（线程池治理/挂死传播，F5）。`GlobalExecutors.globalTimer().scheduleWithFixedDelay(this::sendKeepAlive, ...)` 每 10s 对 `IWebSocketSession.sendMessage`（接口 :30 为同步签名，`throws IOException`）发起网络写；TCP 窗口满/对端停滞时写阻塞或长时间排队，将阻塞**平台全局共享 timer 线程**，殃及所有其他定时任务（含 `DownloadHelper` 的延迟清理）。附带 F8：若容器既不回调 `onClose` 也不回调 `onThrowable`（泄漏的 handler），keepalive 任务永不取消，handler+session 永久驻留。建议：keepalive 移交独立单线程 executor 或异步写接口；handler 生命周期与 timer 任务取消做对称绑定。
- **复核裁定（独立复核 2026-10-18）**: **确认（线索保留）**。:82-84 `globalTimer().scheduleWithFixedDelay(this::sendKeepAlive)` 与 :357-363 `session.sendMessage(createNotification("ping"))`、`IWebSocketSession.sendMessage` :30 同步签名亲验；阻塞性与 F8 滞留面依赖容器 session 实现，形态定性准确。处置沿第 1 轮建议移交 C4/C7。

### [nop-service-framework-hotpath-CL-005]（维度集外线索，不立案展开）JsonRpcWebSocketHandler 订阅注册 check-then-act 与会话关闭清扫的竞态窗口

- **文件**: `nop-graphql-core/.../ws/JsonRpcWebSocketHandler.java:182-189,323-336,349-355,120-129`
- **形态**: C1/C6（共享可变状态/资源生命周期）。`validSubscription` 的 `containsKey` 检查（:330）与 `sendStreamingMessage` 的 `put`（:353）之间无原子性；依赖「单连接消息串行投递」的容器假设。若 `onClose` 清扫（:122）与在途订阅的 put 交错：清扫 miss → 订阅在会话关闭后注册 → 底层 GraphQL 订阅执行无人取消（`cancelOperation` 已扫空），无限流订阅持续运行至自然结束，且 onNext 因 session closed 停止 request(1) 但注册条目滞留。触发：客户端发送 subscribe 后立即断连。单连接串行投递成立时不可达，标注为依赖容器线程模型的防御缺失。
- **复核裁定（独立复核 2026-10-18）**: **确认（线索保留，与 C1-005 同位点归并）**。`validSubscription` :323-336 / `sendStreamingMessage` :349-355（put :353，:351-352 已有「先注册再 subscribe」顺序注释）/ `onClose` :120-129 亲验。C1 复核已对同设施 C1-005 维持 P2 并归「订阅注册表工单」；本维度不重复立案，随该工单修复覆盖。

### [nop-service-framework-hotpath-CL-006]（维度集外线索，不立案展开）DownloadHelper.downloadZip 临时 zip 依赖进程内延迟任务清理

- **文件**: `nop-biz/src/main/java/io/nop/biz/download/DownloadHelper.java:39-42`
- **形态**: C6/C7（资源生命周期/长时运行）。`GlobalExecutors.globalTimer().schedule(delete, waitMinutes)`：进程在到期前重启/崩溃 → 临时 zip 永久滞留（无启动清扫钩子）；下载热路径下临时文件随下载量累积。另 zip 全量落盘物化属 C5 包络问题。建议：启动期清扫 `download` 临时目录 + 目录级 TTL 巡检。
- **复核裁定（独立复核 2026-10-18）**: **确认（线索保留）**。:39-42 `globalTimer().schedule(() -> resource.delete(), waitMinutes, MINUTES)` 亲验；进程重启滞留面成立，启动清扫建议保留。移交 C6/C7。

---

## 统计与总结

| 类别 | 数量 | 编号 |
|------|------|------|
| P1 发现 | 1 | C2-004 |
| P2 发现 | 5 | C2-001, C2-002, C2-003, C2-005, C2-006 |
| P0 发现 | 0 | —（静态可证死锁环：未发现） |
| 正面样例 | 10 | 见上表 |
| 维度集外线索 | 3 | CL-004, CL-005, CL-006（CL-001~003 已被占用） |

**总评**: 该热路径切片的锁纪律整体水准较高——CAS 复合操作（CancelTokenManager、MFA/SMS store）、DCL（LoginAttemptStores、AiRateLimit）、双参 remove、发布安全（JwtAuthTokenProvider 的 monitor-entry HB）等经典易错面全部正确，且多处带明确的修复注释契约。系统性缺陷集中在**单一模式**：三个立案（C2-001/002/003 同型）都是「为微秒级原子性设计的锁，临界区被毫秒级 I/O/外部回调放大」——锁的选择正确但工作量失配。C2-004（锁面不闭合）与 C2-005（复合不变式无锁）是仅有的两处互斥性缺口。P0 死锁环经全模块锁序图排查未发现；两条理论链（G1 syncGet 自环、CHM bin 交叉）已按口径标注并维持 P2。

**盲区自评**: (1) org.dataloader 库版本的 dispatch 线程模型未直接验证库源码，C2-001 依赖「同步回调」推断（有 api-core futureCall 证据链背书）；(2) nop-graphql-grpc 37 文件未发现锁站点（grep 零命中），Protobuf marshaller 的线程安全性未逐字段深挖；(3) WS 容器（quarkus/spring 端点）的单连接消息串行性假设未在本仓库内验证，影响 CL-005 的可达性判定；(4) 事务管理器/DB 连接池内部锁序在 nop-dao 越界，G1→DB 边的环检测未闭合到池实现。

---

## 维度复核结论（独立复核子代理，2026-10-18）

复核方式：与初审/深挖代理相互独立，以怀疑姿态对 6 条发现（含撤案建议 1 条）+ 4 条 CL 线索逐条亲开 live code 核对证据（file:line 全量重开，含跨模块承载链：java-dataloader 3.2.2 版本实测、JDK/Apache HTTP 客户端 `ServerEventPublisher`/`AbstractServerEventSubscription`、`StreamingProcessor.createMappedPublisher`、`ServiceContextImpl`/`MapCache`、auth-core-defaults.beans.xml 装配）；本阶段解除盲审，对照 `ai-dev/audits/cra-index.md`（WI 1.1–2.2 台账，**不含本单元**）、`ai-dev/plans/2461~2470-*.md`（判级口径沿 2470/skill 校准表）、`ai-dev/bugs/`、`ai-dev/lessons/`、同目录 C1 轮复核结论（C1-001 降档与 C2-004 合并建议——本轮指令要求对照的口径）与 C3-round2 的 CL-018 终判。git log 实测：本维度涉及文件最后提交为 2025-12-05 ~ 2026-10-11，均早于本审计（2026-10-17），**无审计后修复提交**，全部现状=新发现/未修复。

### 裁定汇总表

| 编号 | 初审判级 | 复核裁定 | 复核要点 |
|---|---|---|---|
| C2-001 | P2 | **维持 P2** | 同步链全证实（dispatchAll 锁内 dispatch → OrmBatchLoader.flush SQL / futureCall 同步 biz action；dataloader 3.2.2 同步语义 + api-core :122-129 亲验）；每请求锁、毫秒级临界区、无死锁实证，判级恰当 |
| C2-002 | P2 | **保留，降级 P2→P3** | 锁内回调面（含 R2 terminate 三链重入锁内化）事实全部证实；但「锁内阻塞至 connect 超时（秒级）、取消延迟=connect 超时上限」被两个 shipping HTTP 客户端推翻（均非阻塞任务提交）——P2 承重量化不成立，残余为有界延迟+纪律统一，修复指引保留 |
| C2-003 | P2 | **维持 P2** | loadByProp 映射内 biz invoke、MapCache→CHM 承载链、唯一调用方 :466 全亲验；嵌套 compute/ABBA 维持理论注记 |
| C2-004 | P1 | **保留，降级 P1→P2，并入 C1-001（主 ID）** | 锁面不闭合与共享实例机制全证实；P1 危害链跨部署拼接不成立（Local 丢 flush 无永久丢失 / Dao 无共享实例）+ addRole/removeRole 全仓零生产调用方（grep 实测）——与 C1 复核口径**一致** |
| C2-005 | P2 | **维持 P2，与 C1-003 同设施并案** | 双 map 复合无锁、孤儿交错与不可清理证明逐行证实；可达性注记沿 C1 复核（8 根内无生产调用方、ws 集成模块超单元）；随「订阅注册表工单」修复自动闭合 |
| C2-006 | P2 | **撤案成立，改判正面样例（附 P3 注记）** | 三入口每请求/每调用新建 executor + 无 IoC 单例注册独立核实；resultLock=每请求锁且互斥必要正确；第 1 轮「单例判定链」系误读 |
| CL-004 | 线索 | **确认** | :82-84/:357-363/:30 亲验；移交 C4/C7 维持 |
| CL-005 | 线索 | **确认（与 C1-005 同位点归并）** | :323-336/:349-355/:351-352 注释/:120-129 亲验；随订阅注册表工单覆盖，不重复立案 |
| CL-006 | 线索 | **确认** | :39-42 亲验；移交 C6/C7 维持 |
| CL-018 | 线索 | **确认（按 C3 终判收窄表述）** | await 无超时亲验；与 C3-round2「单元侧无条件不可达」终判一致——本单元使用站点无新增立案，api-core 本体归属 nop-kernel 单元 |

**统计**：6 条发现——0 驳回、1 撤案（C2-006→正面样例）、2 降级（C2-004 P1→P2、C2-002 P2→P3）、3 维持；复核后本维度判级分布 **P1×0 / P2×4（C2-001/003/004/005，其中 004、005 分别并入 C1-001/C1-003 设施工单）/ P3×1（C2-002）**。CL×4 全部确认（CL-005 归并 C1-005 工单、CL-018 归属 nop-kernel 单元、CL-004/006 按预设移交 C4/C6/C7）。

### C2-004 终裁（本轮核心裁定之一）

1. **合并立案（指令 a）：同意 C1 复核建议**。同设施双镜头成立——C1-001=可见性/dirty 丢更新镜头，C2-004=锁面不闭合/复合操作镜头，两者根因同源（UserContextImpl 混合同步纪律 + AbstractUserContextCache clearDirty→发布契约），修复面完全重叠。裁定合并为单一修复工单，**主 ID 归 C1-001**（C1 复核已定），C2-004 为并案镜头；合并修复面覆盖 C1-001 + C2-004 + C1-002 + C1-009 的全部 F3（沿 C1 复核第 3 条）。
2. **判级（指令 b）：跟随 C1-001 降 P2，且独立复核得出相同结论**。机制面（混合同步、共享实例、clearDirty 竞态交错）全部亲验证实、可构造；但 P1 的承重后果「角色变更永久丢失（缓存与持久层均不含新角色）」跨部署拼接不成立——Local 部署（auth-core-defaults.beans.xml :45-48 默认装配）下缓存条目即实例本体，变更留存于共享实例、下一次 flush 自然带出；Dao 部署下 DaoUserContextCache 每请求新建实例（共享面消失）。与 C1 口径的关系：**一致，无分歧**——C1 复核明示「建议 C2 复核对 C2-004 同步降档」，本次独立取证（部署装配 + 缓存无 loader + 每请求新建）指向同一结论。
3. **第 2 轮「addRole/removeRole 平台内生产调用方为零」修正的判级影响（指令 c）**：复核 grep 全仓 src/main 实测 `.addRole(`/`.removeRole(` 仅命中 `TestUserContextImplSemantics`（测试）——零生产调用方成立；`setRoles` 三个生产写点（LoginServiceImpl:704 / OAuthLoginServiceImpl:181 / demo WebAuthFilter:57）均发布前。P1 旗舰交错（addRole 持锁 vs clearDirty 无锁）平台内当前不可达，触发依赖下游应用经 IUserContext 公共 API 运行期调用。影响：撤走 P1 所需的「常见条件」可达性；维持 P2 而非 P3 的依据与 C1 复核对 C1-002 的裁定同构——IUserContext 为平台公共扩展面、锁面不闭合为结构缺陷、修复必做（已并案）。
4. **现状**：新发现/未修复。

### C2-006 终裁（本轮核心裁定之二）

**撤案成立，改判正面样例（附 P3 注记）**。第 2 轮推翻论证经独立核实三要素全部成立：(1) **调用链**——`newGraphQLExecutor()` :893-894 实现为 `return new GraphQLExecutor(...)` 每次调用新建实例；全部三处调用点 :558（executeRpcAsync，每 RPC 请求）/:601（executeGraphQLAsync，每 GraphQL 请求）/:878（fetchResultWithSelection，每次嵌套选取）均为每请求/每调用粒度，亲验无「每引擎一次」语义。(2) **无单例注册**——全仓 grep 无 `io.nop.graphql.core.engine.GraphQLExecutor` 的 beans.xml 注册（仅测试日志命中），单例注入路径不存在。(3) **订阅旁路**——subscribeGraphQL 不经 GraphQLExecutor（沿第 2 轮，与第 1 轮矩阵一致）。resultLock 四站点守护的 response :169 / ret :265/:432 均为方法局部 LinkedHashMap，同请求异步字段完成回调可跨线程 → synchronized(resultLock) 互斥必要且正确，锁粒度=请求级恰与保护对象共享范围一致。第 1 轮的「单例判定链」信心依据确系对 `newGraphQLExecutor()` 方法的误读——**撤案并移入正面样例表（附 P3 残余注记：单请求并行字段在该请求级监视器上的争用，不立案）**。

### 与 C1 复核口径的对照声明（指令要求）

- **C2-004**：与 C1 复核**一致**（降 P2 + 合并主 ID C1-001）。C1 复核的「同步降档建议」系其边界外提示，本轮独立重开部署装配、缓存语义、调用方 grep 三面证据后得出相同裁定，非简单跟随。
- **C2-005**：与 C1 复核对 C1-003（同设施）的**维持 P2 口径一致**（含可达性注记与工单归并）。
- **C2-002 降级**为本轮唯一主动偏离第 2 轮「P2 维持」建议的裁定，依据为复核新取证（shipping 客户端非阻塞性），偏离理由已在条目复核裁定行完整留档；第 2 轮 C2-002-R2 的失效面修正作为事实采纳、修复指引保留。

### 争议点与保留意见

1. **C2-002 降 P3**是本轮唯一实质争议：第 2 轮以「失效面扩大不改变量级」维持 P2，但该判断未重审「秒级 connect 阻塞」这一 P2 承重前提——复核以 nop-network 两个 shipping 客户端实现（JDK `sendAsync`、Apache HC5 异步 `execute`）+ `AbstractServerEventSubscription` 惰性 `startRequest` 证实持锁期间仅任务提交。保留意见：若第三方部署注入同步阻塞型 IHttpClient 实现（接口契约允许 subscribe 期阻塞），秒级危害恢复，判级应回到 P2——该前提已留档。
2. **C2-005 维持 P2** 依赖「ws 集成模块（超 8 源码根）存在调用方」的外部假设——若后续核实集成模块无调用，可再降档（与 C1 复核争议点 3 同一保留）。
3. C1 轮 K1 类站点（GraphQLExecutionContext 读写不对称）在 C2 轮取「P3 注记不立案」、C1 轮取「C1-004 P2 立案」——两维口径差异沿 C1 复核裁定（立案归属 C1 维度）处理，本轮无异议。

## 最终保留项

复核后本维度保留项及修复归属（避免碎片化修复，与 C1 复核工单划分对齐）：

1. **维持立案（P2）**：
   - **C2-001**——dispatchAll 临界区收窄：锁内仅「快照+清空」，`loader.dispatch()` 循环移锁外执行；同锁三方纪律（P1-2.6-004）保持，dispatch 消费快照、晚到 load 由后续回合承接。回归 P1-2.6-004 对应测试。
   - **C2-003**——锁内只做查缓存：miss 锁外执行 biz invoke 后 `putIfAbsent`（先到者胜），或改平台 `LocalCache.computeIfAbsentAsync`/Caffeine per-key loading；null 结果引入哨兵值。
   - **C2-004（并案镜头，主 ID C1-001）**——修复方案随 C1-001 设施工单（全访问器统一同步域或不可变快照+volatile 发布；dirty 改 AtomicBoolean + CAS 认领；clearDirty 移持久化成功后）；失效面含第 2 轮 ⑤ 注记的 OAuthLoginServiceImpl.setAccessToken/setRefreshToken 写者路径。
   - **C2-005（并案镜头，主工单 C1-003 订阅注册表工单）**——per-operationName 或全局细粒度锁统一 register/unregister，或重构单 map 消除双 map 不变式；随 C1-003 的 putIfAbsent+compute 单键原子化方案一并验收（断言无孤儿条目不变式）。
2. **降级保留（P3）**：**C2-002**——锁纪律统一改造仍建议随修复轮执行（成本低）：startAttempt 的 fetch+订阅移锁外（attempt 槽位快照置换 + :372 已有归属防护裁决）、retryCallback/lifecycle 快照后锁外执行、terminate 改「锁内快照 + 锁外回调」（沿 C2-002-R2 修复指引，三链 exactly-once 语义回归）。标注：若引入同步阻塞型 IHttpClient 实现须复评 P2。
3. **撤案**：**C2-006** → 正面样例表（`GraphQLExecutor` resultLock ×4：每请求锁、互斥必要正确；附 P3 注记）。
4. **CL 线索处置**：CL-004→C4/C7；CL-005→并 C1-005 订阅/WS 设施工单；CL-006→C6/C7；CL-018→维持 CL（api-core 本体归属 nop-kernel 单元；P3 备忘：`executeRpc` 同步桥补前置假设说明）。
5. **正面样例表增补（复核确认）**：`GraphQLExecutor` resultLock ×4（C2-006 撤案改判）；`BufferedStreamingPublisher.cancel` :190-199（唯一真锁外回调范本，C2-002 修复参照——第 2 轮已列，复核确认）。
