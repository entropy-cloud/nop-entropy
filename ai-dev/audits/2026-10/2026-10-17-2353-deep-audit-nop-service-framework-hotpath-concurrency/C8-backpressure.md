# C8 背压·流控·过载行为审计报告（第 1 轮初审·盲审）

## 基本信息

- **审计单元**: nop-service-framework-hotpath（请求热路径五模块 / 8 源码根 / 406 文件 / 42,748 行）
  - `nop-service-framework/nop-graphql/nop-graphql-core`（145 files / 17,097 lines）
  - `nop-service-framework/nop-graphql/nop-graphql-orm`（11 files / 859 lines）
  - `nop-service-framework/nop-graphql/nop-graphql-message`（3 files / 162 lines）
  - `nop-service-framework/nop-graphql/nop-graphql-grpc`（37 files / 3,174 lines）
  - `nop-service-framework/nop-biz`（89 files / 9,985 lines）
  - `nop-service-framework/nop-gateway`（44 files / 4,743 lines）
  - `nop-service-framework/nop-biz-auth-core`（60 files / 5,760 lines）
  - `nop-service-framework/nop-biz-file-core`（14 files）
- **仓库路径**: `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`）
- **维度**: C8——背压、流控与过载行为（生产-消费边界有界降级、限流实现、并发无上界面、过载可观测性）
- **判级口径**: 请求热路径被高并发调用——过载面按「常见条件」门槛最低判定（高 QPS 即触发）；P0=默认用法+常见条件即不可逆；P1=高 QPS 下确定性 OOM/不可逆/服务级停摆；写不出过载数字的背压类发现一律 P2 标注。
- **盲审纪律声明**: 本轮为盲审初审。未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件；仅阅读方法论文件 `ai-dev/skills/concurrency-resource-audit-prompt.md`、`ai-dev/skills/deep-audit-prompts.md`、基线 `_tmp/cra-baseline-nop-service-framework-hotpath.txt`（C8 节）与 live code。prompt 中给出的既有登记号（C3-003、C4-002、CL-003、CL-008、CL-011、WI 2.1 C8-004）仅作为「深化 vs 不重复」的边界提示，本报告不引用其内容作为证据，一切结论以当前 live code 复现为准。
- **现状标注**: 本轮为第 1 轮初审，所有条目 `复核状态: 未复核`。

## 检查范围说明

### 已读关键文件

- 订阅/WS 路径：`GraphQLSubscriptionManager.java`、`SubscriptionInfo.java`、`JsonRpcWebSocketHandler.java`、`IWebSocketSession.java`、`GraphQLSubscriptionPublisher.java`、`RpcSubscriptionPublisher.java`、`GraphQLEngine.subscribeGraphQL/subscribeRpc`（690-860 行）、`subscription-defaults.beans.xml`
- Gateway 流式/转发：`BufferedStreamingPublisher.java`、`StreamingProcessor.java`、`StreamingResponse.java`、`GatewayHttpFilter.java`、`GatewayRouteExecution.java`、`GatewayHandler.java`、`InvokeProcessor.java`、`ForwardProcessor.java`、`OpenAiDeltaAccumulator.java`、`LoggingInterceptor.java`、`ModelBasedGatewayInterceptor.java`、`AiFailoverGatewayInterceptor.java`、`AiRateLimitGatewayInterceptor.java`、`GatewayConfigs.java`、`GatewayStreamingModel`
- 限流/流控设施使用面：`DefaultFlowControlRunner.java`（nop-rpc-core，prompt 点名对象）、`SentinelFlowControlRunner.java`（nop-cluster-sentinel，`ioc:default=true` 缺省 runner）、`DefaultRateLimiter.java`（nop-commons，仅审使用方式）、`GraphQLFlowControlInvoker.java`、`FlowControlFetcher.java`、`GraphQLEngine` 流控接线（693-694、894 行）、`sentinel-defaults.beans.xml`
- GraphQL 执行面：`GraphQLExecutor.java`（全文）、`CancelTokenManager.java`、`SysBizModel.java`、`MetricsGraphQLHook.java`、`GraphQLConfigs`（max-depth=7 / max-operation-count=10 / max-page-size=1000）、`GraphQLSelectionResolver`（maxDepth 强制点）、`OrmBatchLoader.java`、`GraphQLExecutionContext.dispatchAll`、`JsonRpcService`（批上限）
- 文件上传/下载：`ChunkFileUploadHandler.java`、`NopFileStoreBizModel.java`、`AbstractGraphQLFileService.java`、`IFileStore.java`、`DownloadHelper.java`、（依赖面核对）`DaoResourceFileStore.saveFile`（nop-file-dao，LimitedInputStream 钳制确认）
- 认证热路径：`AbstractUserContextCache.java`、`LocalUserContextCache.java`、`AuthHttpServerFilter`（链面扫读）
- gRPC：`GrpcServer.java` + `ThreadPoolConfig`/`ExecutorHelper` 默认值核对（平台设施本体不重审）
- 适配器依赖面核对（F3 清扫用，非立案对象）：`JakartaWebSocketSession.java`（nop-quarkus-web）、`SpringWebSocketSession.java`（nop-spring-web-starter）、`LocalMessageService.java`（nop-message-core，发布线程语义确认）、`GlobalExecutors.globalTimer`（默认 maxPoolSize=5）

### 缓冲/队列/累积载体清单表（C8 基线盘点）

| # | 载体 | 位置 | 有界性 | 满/过载行为 | 判定 |
|---|---|---|---|---|---|
| B1 | `Attempt.buffer`（越窗后） | nop-gateway `BufferedStreamingPublisher.java:119,265` | 窗口内有界（bufferSize=10）；越窗后**无界** ArrayDeque | 无界累积（下游 demand 耗尽即堆积） | 既有立案（C3-003），本轮深化其生产侧根因（见 C8-003 失效面） |
| B2 | `OpenAiDeltaAccumulator.contentBuilder/toolCallAccumulators` | nop-gateway `OpenAiDeltaAccumulator.java:14-15` | **无上界**（随上游流式响应长度增长） | 每请求内存随响应长度线性增长 | 本轮立案 C8-003 |
| B3 | `buckets`（限流桶 LocalCache） | nop-gateway `AiRateLimitGatewayInterceptor.java:114-132` | 有界：maximumSize=maxTrackedKeys(100k) + expireAfterAccess | 淘汰后重建满桶 | 有界声明成立；时钟缺陷另立案 C8-005 |
| B4 | `activeOperations`（WS 每连接订阅表 CHM） | nop-graphql-core `JsonRpcWebSocketHandler.java:56,324` | 每连接 1000（硬编码，setter 无调用方）；**无跨连接全局上界** | 拒绝（TOO_MANY_SUBSCRIPTIONS/4409） | 本轮立案 C8-007 |
| B5 | `subscriptionsById/subscriptionsByOperation` | nop-graphql-core `GraphQLSubscriptionManager.java:51-52,87-100` | 全局 1000 上限为 check-then-act（可击穿）；ops 列表可积累僵尸条目 | 异常拒绝（可被竞态绕过） | 本轮立案 C8-004 |
| B6 | GrpcServer executor 队列 | nop-graphql-grpc `GrpcServer.java:81-85` + `ThreadPoolConfig` 默认 | 有界（core 4 / max 20 / queue 10000），metrics 开启 | 拒绝策略由 ExecutorHelper 决定 | 合规（平台设施，使用方式无缺陷） |
| B7 | `cancelTokens`（在途请求登记 CHM） | nop-graphql-core `CancelTokenManager.java:51,127-137` | 以在途请求并发数为界，完成/异常路径 `whenComplete` 清理 | — | 合规（有界 + 清理配对） |
| B8 | IMessageService → `onMessage` 分发 | nop-graphql-core `GraphQLSubscriptionManager.java:66-79,169-190` | 本模块内无队列，同步直推 `pushToClient` | 阻塞发布/消费线程 | 并入 C8-001（阻塞面） |
| B9 | WS keepAlive 每连接定时任务 | nop-graphql-core `JsonRpcWebSocketHandler.java:82-83` | 任务数=连接数（无上界），落全局 timer（5 线程） | 阻塞式 send 占用 timer 线程 | 并入 C8-001/C8-007（C4-002 聚合面量化） |
| B10 | Gateway 在途重试持有 | nop-gateway `InvokeProcessor.java:139-167,198-237` | **无并发上界**（无准入控制）；单请求最多持有 ~90s+ | 请求级内存持有累积 | 本轮立案 C8-002 |
| B11 | 每请求 DataLoader（ORM 批加载） | nop-graphql-orm `OrmBatchLoader.java:40-55` | 请求级生命周期；键数随 selection（页上限 1000 钳制） | — | 合规（请求级 + 页上限） |
| B12 | 限流站点（IRateLimiter 用法） | `GraphQLFlowControlInvoker`、`FlowControlFetcher`（经 `GraphQLExecutor.withFlowControl:337-341`） | fail-fast 拒绝（Sentinel 缺省）/ tryAcquire(0ms) 非阻塞 | 拒绝 | 语义缺陷立案 C8-008 |
| B13 | 用户上下文缓存 ×3 | nop-biz-auth-core `LocalUserContextCache.java:19-38` | 有界（maxLoginUserCount×N）+ TTL + metrics | 淘汰 | 合规 |
| B14 | 每请求 GraphQL 结果树 | nop-graphql-core `GraphQLExecutor` + `GraphQLConfigs` | 深度≤7、顶层操作≤10、每层页≤1000；**选择宽度无上限**（层数×页积无复合上限，实际受 DB 成本支配） | — | 有界声明部分成立，观察项不立案 |
| B15 | JSON-RPC 批量命令 | nop-graphql-core `JsonRpcService:67-76` | 有界（max-operation-count=10，超限 400） | fail-fast | 合规 |

### 生产-消费边界清单（C8 输出要求）

| 边界 | 生产者 | 消费者 | 背压机制 | 判定 |
|---|---|---|---|---|
| 域事件 → WS 订阅推送 | IMessageService 发布线程（LocalMessageService 缺省=发布者线程同步回调） | `pushToClient`/`SubscriptionSubscriber.onNext` 同步 send | **无**（同步阻塞直推） | C8-001 |
| WS 订阅流（Flow） | 订阅源 publisher | `SubscriptionSubscriber`（request(1) 拉动） | Flow demand（有） | 合规；但 send 阻塞使 demand 失效（C8-001） |
| 上游 SSE → 网关客户端 | HTTP 客户端流 | 映射链自驱动 request(1) → BufferedStreamingPublisher 缓冲 | 窗口后有界性失效 | 既有立案（C3-003），根因深化见 C8-003 |
| 客户端请求 → 上游 HTTP | 网关（无准入上限） | 上游服务 | 429 重试放大（反向背压） | C8-002 |
| GraphQL 请求 → 引擎执行 | HTTP/WS/RPC 入口 | operationInvoker + 每字段 fetcher | flowControlRunner（缺省 Sentinel，可拒绝） | C8-008 |
| 请求 → gRPC server | gRPC 传输 | 有界线程池（queue 10000） | 池拒绝 | 合规 |

---

## 第 1 轮（初审）发现

### [nop-service-framework-hotpath-C8-001] WS 订阅推送同步阻塞发送，单慢客户端可阻塞业务发布线程并钉死共享全局定时器（CL-008 深化为完整条目）

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/ws/JsonRpcWebSocketHandler.java:457-469,357-363,82-83`；`nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/subscription/GraphQLSubscriptionManager.java:180-187,204-218`
- **证据片段**:
  ```java
  // JsonRpcWebSocketHandler.SubscriptionSubscriber.onNext —— 生产者线程上同步阻塞发送
  public void onNext(ApiResponse<?> executionResult) {
      if (!session.isClosed()) {
          try {
              ...
              session.sendMessage(createDataMessage(operationId, executionResult)); // 阻塞式
          } catch (Exception e) { LOG.warn("nop.websocket.send-next-fail", e); }
          subscription.get().request(1);
      }
  ...
  // :82-83 每连接一个 10s 周期任务，落在平台全局共享定时器上
  this.keepAliveSender = GlobalExecutors.globalTimer().scheduleWithFixedDelay(
          this::sendKeepAlive, 10, 10, TimeUnit.SECONDS);
  ```
  适配器依赖面（F3 清扫证据，非立案对象）：`JakartaWebSocketSession.sendMessage` 用 `session.getBasicRemote().sendText(message)`（Jakarta WebSocket 同步远程，默认无写超时，慢客户端 TCP 零窗口时阻塞可达秒~分钟级）。
- **严重程度**: P1
- **并发场景**: ①订阅源 publisher 的发射线程（缺省 LocalMessageService 在**发布者线程**同步回调 `invokeMessageListener`——即触发数据变更的业务请求线程）→ `pushToClient`/`onNext` → 阻塞 send；②`GlobalExecutors.globalTimer()`（平台全局定时器，默认 maxPoolSize=5，全平台共享：WS keepAlive、DownloadHelper 临时文件清理等全部落此池）上的 keepAlive 任务 → 阻塞 send。
- **触发时序**: T1=业务线程完成数据变更后发布事件 → `GraphQLSubscriptionManager.onMessage`（或订阅源 onNext）→ `pushToClient` 对慢客户端 C1 调 `session.sendMessage` → C1 TCP 零窗口，`basicRemote.sendText` 阻塞 L 秒（无写超时）→ 同一回调循环内其余订阅（C2..Cn）的推送全部排队等待；若走 LocalMessageService 同步路径，**T1 业务线程本身被阻塞 L 秒**；并发下 N 个业务线程相继卡在同一慢客户端 → HTTP worker 池被逐步占满 → 全服务请求停摆。并行时序：5 个慢客户端的 keepAlive send（每连接 10s 一次）同时阻塞 → globalTimer 5 线程全被钉死 → **全平台**定时任务（其他连接的 ping、临时文件清理等）停摆 → 大量健康客户端因收不到 ping 判死断连（级联掉线）。
- **过载数字**: 生产速率 = 订阅事件速率 E 条/s；消费速率 = 1/L 条/s（串行推送循环，L=慢客户端单条发送延迟）。健康客户端 L≈亚毫秒；零窗口慢客户端 L→10s~分钟级（无写超时）。消费坍缩后积压速率 = E − 1/L ≈ E（全部积压）。定时器面：阻塞并发度 5（池上限）× L 即全平台 timer 停摆 L 秒；keepAlive 任务数 = 连接数 N（每连接 1 个/10s），N>5×(10s/L) 时池饱和。
- **现状**: 订阅推送为同步阻塞式直推，无发送队列、无写超时、无按会话隔离的异步发送器；`SubscriptionInfo.flowSubscription` 字段（"Flow subscription for backpressure handling"）存在于模型上但 `pushToClient` 路径完全未使用 Flow 需求信号。
- **风险**: 一个慢客户端（移动弱网常态）即可使发布线程/全局定时器阻塞，属「常见条件 + 高 QPS 热路径」确定性服务级停摆面；且阻塞发生在业务请求线程时不可逆地放大尾延迟（该缺陷的失效面横跨订阅推送与请求线程池两个失效层）。
- **建议**: ①推送改异步/带界队列 + 溢出策略（复用平台 `IBlockingQueue` + `QueueOverflowPolicy`，慢客户端队列满即断开该会话，不反压全局）；②`IWebSocketSession` 实现侧改 `getAsyncRemote()` 或加写超时（适配器在本轮范围外，需单独立项）；③keepAlive 移出全局共享 timer 或改为非阻塞入队；④为积压深度/断连数补指标（见 C8-006）。
- **失效面枚举**（F3 同族清扫）: ①`JsonRpcWebSocketHandler.SubscriptionSubscriber.onNext:464`（同步 send）——立案本体；②`sendKeepAlive:359`（同步 send，且在共享 timer 上）——立案本体；③`GraphQLSubscriptionManager.pushToClient:217`（同步 send，经 IMessageService 分发线程/发布者线程）——立案本体（该管理器当前无库内 registerSubscription 调用方，属接线休眠，但 bean 已激活订阅，形态同族）；④`JsonRpcWebSocketHandler.onClose/handleUnsubscribe/handleTokenRefresh` 中的 `session.sendMessage`（响应类小消息，同阻塞原语，同族未修但单次且在连接自身线程，风险低）；⑤`SpringWebSocketSession`（nop-spring-web-starter）与 `JakartaWebSocketSession` 同为同步 send——适配器层同族，需在适配器维度独立清扫；⑥`GraphQLSubscriptionPublisher.TransformingSubscriber.onNext:66`（downstream.onNext 直推，最终落到 ① 的阻塞 send）。全部同族站点已列，无遗漏入口。
- **信心水平**: 确定（阻塞原语与线程归属均可从 live code 直接复现；L 的上界取决于容器实现，已标注）
- **误报排除**: 不是「低频路径粗锁」误报——推送在每事件×每订阅的热路径上；不是「有界声明存在」误报——此处无任何队列/超时声明；不是平台设施误报——阻塞点在本模块代码对 send 的同步调用方式，适配器仅是放大器。
- **复核状态**: 未复核
- **复核状态（维度复核 2026-10-18）**: **保留，维持 P1**。机制面逐项亲验成立：①两适配器（`JakartaWebSocketSession.java:25`、`SpringWebSocketSession.java:25`）均 `getBasicRemote().sendText` 同步发送，`getAsyncRemote` 全仓 0 命中，模块内无写超时；②`JsonRpcWebSocketHandler.java:82-83` keepAlive 每连接 10s 周期任务落 `GlobalExecutors.globalTimer()`；③`LocalMessageService.invokeMessageListener:199-211` 发布者线程同步回调证实。**特别指令 c（globalTimer vs JDK Delayer 区分）确认**：globalTimer 为平台 `DefaultScheduledExecutor`（`ScheduledThreadPoolExecutor` 实体），非 JDK `CompletableFuture.Delayer`——C4-003 复核勘误 8（JDK 25 `delayedExecutor` 走 ForkJoinPool common pool 中继、C4-003 降 P2）**不影响本条**，两者无涉。**量化修正（方向利好本条）**：`ExecutorHelper.newScheduledExecutor:63-65` 对 ScheduledThreadPoolExecutor 强制 `config.setMaxPoolSize(0)` 并以 `corePoolSize` 建池（`ThreadPoolConfig.java:17` 默认 4）——`CommonConfigs` 的 `global-timer.maxPoolSize=5` 旋钮实为**死配置**，真实全局定时器 = **4 线程**（非 5）；钉死门槛比初审更低：持续阻塞形态 4 个同时停顿 send 即钉死全池；有限 L 形态（计入 scheduleWithFixedDelay 周期 10+L）阈值 L=10s→N>8、L=30s→N≈5.3、L=60s→N≈4.7——初审 ρ=N·L/50 应为 N·L/40，「钉死门槛 1~5 个」精化为「4~8 个，持续阻塞 4 个」，单数字慢客户端钉死全池的定性结论不变。globalTimer 平台级用户实测 27 处生产站点（nop-retry RetryScanner、nop-cluster AbstractPollingLeaderElector、nop-job AbstractBatchScanner、nop-task TaskFlowManagerImpl 等），「全平台定时任务停摆」面成立。manager 推送腿「接线休眠」标注复核成立（registerSubscription 生产 0 调用方）。现状：未修复（产品代码最后提交 2026-08-26，早于本审计）。

### [nop-service-framework-hotpath-C8-002] Gateway 对上游 429/5xx 重试×failover 放大至 ×7 且在途请求无并发上界——过载时放大洪峰而非有界降级

- **文件**: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/executor/InvokeProcessor.java:135-137,149-167,198-237`；`nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/interceptor/AiFailoverGatewayInterceptor.java:35,73-109,111-145`
- **证据片段**:
  ```java
  // InvokeProcessor: 上游说"太挤了"(429)，网关反而每个请求再打 3 次
  int maxRetries = 3;
  ...
  // 429 Too Many Requests — 退避重试
  if (httpStatus == 429 && retriesLeft > 0) {
      String retryAfter = httpResponse.getHeaders().get("Retry-After");
      long delayMs = parseRetryAfter(retryAfter, retriesLeft);
      return delayedFuture(delayMs).thenCompose(v ->
              invokeUrlWithRetry(url, request, invoke, svcCtx, retriesLeft - 1));
  }
  // AiFailoverGatewayInterceptor: 重试耗尽后再打最多 3 个 fallback，fallback 之间无退避
  if (status == 429 && nextFallbackIndex < maxFallbackAttempts()) {
      return tryFallbackUrl(fallbackUrls.get(nextFallbackIndex), request, svcCtx, nextFallbackIndex + 1);
  }
  ```
- **严重程度**: P1
- **并发场景**: HTTP worker（异步链）× 上游 AI 提供商过载；每个在途网关请求以挂起的 CompletionStage 形式持有完整请求体（`GatewayHttpFilter:145 getTextAsync` 全量物化的 String + `JSON.parse` 树）与上下文，等待 `delayedFuture`（Retry-After 值，钳制 30s）。
- **触发时序**: T0=上游饱和开始返回 429 → 到达速率 C 的每个客户端请求被网关改写为最多 1+3 次主上游调用（每次间隔最长 30s）+ 最多 3 次 fallback 调用（合计 ≤7 次上游请求）→ 上游实际承受 4C 的重试流量（恰好打击其要求停止的时段）；客户端请求不被快速拒绝而是被持有 → 网关在途并发 ≈ C × (重试窗口 ~30-90s)，随 C 线性增长且**无任何准入上限**（`GatewayConfigs` 仅有 model-path 一项配置；无信号量/并发闸）→ 内存面 = 在途数 × 请求体大小（String+JSON 树约 2-5 倍放大），高 C 下确定性 OOM 轨迹；同时 `Retry-After` 精确延时使全部被拒请求在同一时刻齐发重试（无 jitter 的同步重试波）。
- **过载数字**: 放大系数 = 1 主调用 × (1+3 重试) + 3 fallback = **×7** 上游请求/网关请求；单请求最长持有 ≈ 3×30s(主) + fallback 时间 ≈ **90s+**；在途并发上界 = ∞（无准入）；每在途内存 = 请求体字节数 × ~3-5。
- **现状**: 重试是硬编码（maxRetries=3 不可配置），429 语义被实现为「代客户端重试」而非「向客户端回 429 + 退避」；failover 链对 fallback 之间无退避、无 jitter；除 AiRateLimitGatewayInterceptor（需在网关模型中显式启用，按可伪造 XFF 分桶）外无全局限流。
- **风险**: 上游过载 → 网关放大并自持洪峰 → 网关自身内存/连接耗尽（高 QPS 下确定性）；上游恢复被推迟（反背压失效）。属「负载上升必然触发」的过载行为缺陷。
- **建议**: ①429 不做服务端重试（或默认 0 次、可配置、全 jitter 化），直接向客户端透传 429+Retry-After；②为在途请求数增加全局信号量/高水位闸（复用平台 `ISemaphore`/`HighWatermarkSemaphore`，满则立即 503 有界降级）；③fallback 尝试间加指数退避+jitter；④maxRetries/fallback 上限提为 `GatewayConfigs` 可配置项。
- **失效面枚举**（F3 同族清扫）: ①`InvokeProcessor.invokeUrlWithRetry` 429 分支（:153）——立案本体；②同方法 5xx 分支（:162，2^n×1s+jitter 退避，×3）——同族，行为较合理但同样无并发闸，状态=有退避无上界；③`AiFailoverGatewayInterceptor.tryFallbackUrl` 429/5xx 分支（:128-132，无退避）——立案本体；④`BufferedStreamingPublisher.handleError` 的 retryCallback（:298-326，窗口内重订阅由回调方决定，无网关侧次数/速率约束）——同族未约束，回调属拦截器注入面，状态=依赖回调自律；⑤`GraphQLFlowControlInvoker`/Sentinel 链路（拒绝语义正确，非同族缺陷）。
- **信心水平**: 确定
- **误报排除**: 不是「有界声明存在」误报——重试次数硬编码且无总并发闸；不是测试/压测代码——InvokeProcessor 是网关 URL 路由的默认执行路径；「429 退避重试」看似合理，但服务端代重试把背压方向反转为放大，且与本仓库 GatewayRejectException「原样送达 429 让 SDK 退避」的既有语义（GatewayHandler.processRoute 注释自证，今核位于 GatewayHandler.java:145-146）相矛盾。
- **复核状态**: 未复核
- **复核状态（维度复核 2026-10-18）**: **保留，维持 P1**。特别指令三项复核：a) **×7 加法亲验成立**（沿 C4 勘误 1 口径）——`InvokeProcessor.invokeUrl:135` maxRetries=3 硬编码 + `invokeUrlWithRetry:152-167`（429 分支按 Retry-After 钳 `MAX_RETRY_DELAY_MS=30_000`；5xx 分支 2^n×1s+jitter）= 主 URL ≤4 次；`AiFailoverGatewayInterceptor.tryFallbackUrl:126-133` 直连 `httpClient.fetchAsync` **单发无内层重试**、`maxFallbackAttempts()=min(|fallbackUrls|, maxRetries=3)`（:51-53 有 setter 可配）= fallback ≤3 次，4+3=**7** 成立（fallback 绕过 InvokeProcessor 重试链，非乘法）。b) **内存参数化抽验**：挂起链捕获 `request`（data=JSON 树）+ `svcCtx`（GatewayContextImpl.setRequest）复核成立；精确化一处——首层 lambda 执行后裸 `text` String 大概率不再强可达，每在途 ≈ **S×(3~6)**（解析树 3~5× 为主）而非 S×(5~7)，同一数量级，OOM 轨迹不变（λ=1000/s、S=10KB、H=45s → 1.4~2.3GB，随 λ 线性无上界）。c) **「无准入闸」grep 复核 = 0 命中**（Semaphore/HighWatermark/RateLimiter/maxInflight/maxConcurren/tryAcquire/admission 于 nop-gateway/src/main/java），`GatewayConfigs.java` 仅 `nop.gateway.model-path` 一项——复核成立。**Delayer 表述勘误（沿 C4 复核勘误 8）**：第 2 轮 §②「JDK `CompletableFuture.Delayer`（JVM 全局单线程调度器 + 内部无界 DelayedWorkQueue）」系 JDK≤23 语义；项目 `release=25` 下 `delayedExecutor` 走 ForkJoinPool common pool 的 `DelayScheduler`（单线程仅到点 relay、任务在 common pool 多线程执行）——本条 P1 支柱是**在途无上界的内存累积 + ×7 放大**，不依赖该线程语义，勘误不触及判级（C4-003 降 P2 的事由「单线程占用被证伪」是另一根支柱，与本条不同柱）。P1 维持依据：429 过载是 AI 网关的设计常态场景（上游提供商限流为常规事件），在途数随 λ 线性增长且模块内零上界，请求不被快速拒绝而是持有完整 body 解析树挂起 ≈90s+，满足本 campaign「高 QPS 下确定性 OOM」判级。现状：未修复。

### [nop-service-framework-hotpath-C8-003] OpenAiDeltaAccumulator 流式聚合缓冲无响应长度上界（C3-003 同族其他缓冲面）

- **文件**: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/interceptor/OpenAiDeltaAccumulator.java:14-15,63-71,86-98`；`nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/interceptor/LoggingInterceptor.java:30-55`
- **证据片段**:
  ```java
  final class OpenAiDeltaAccumulator {
      private String id;
      private String model;
      private String object;
      private final StringBuilder contentBuilder = new StringBuilder();          // 无上界
      private final Map<Integer, ToolCallAccumulator> toolCallAccumulators = new LinkedHashMap<>(); // 无上界
  ...
      Object argsObj = func.get("arguments");
      if (argsObj instanceof String) {
          acc.argumentsBuilder.append((String) argsObj);                        // 逐 delta 无限追加
      }
  ```
- **严重程度**: P2
- **并发场景**: SSE 每元素在映射链线程（`StreamingProcessor.createMappedPublisher.onNext` → `invocation.proceedOnStreamElement` → `LoggingInterceptor.onStreamElement`）上追加；每个启用 LoggingInterceptor 的流式路由每请求一个 accumulator，随 `IGatewayContext` 生命周期存活至流完成。
- **触发时序**: T1=上游（LLM 提供商或其被攻陷账号）持续发送 SSE delta 且**永不发送 finish/终止帧**（失控生成/恶意上游）→ 每元素 `accumulate()` 向 `contentBuilder`/`argumentsBuilder` 追加 → 单请求内存随流时长线性增长、无任何字节/时长上限 → 叠加高 QPS 时并发请求数，堆占用 = 并发 × 已累积字节数，无界。与 C3-003 的差异：C3-003 是转发路径的无界 ArrayDeque（背压断裂），本条是日志聚合路径的纯累积缓冲（无消费端），两者同族不同失效层。
- **过载数字**: 增长速率 = 上游 delta 字节速率 B（可 ≥ 网络线速，如 10MB/s）；单请求 t 秒后内存 ≈ B×t（另有序列化放大，`toMap` 完成时再复制一份）；并发 K 个失控流时堆增速 = K×B。无任何 maxResponseSize/maxStreamDuration 钳制。
- **现状**: 聚合仅用于日志（`onStreamComplete` 时 `logger.logStreamingResponse(accumulator.toMap(), ...)`），无大小上限、无流时长上限、无上游空闲超时；流永不完成时缓冲与连接一同滞留（连接关闭后随 context 释放，但关闭依赖客户端/超时配置，模块内无兜底）。
- **风险**: 恶意或失控上游可定向放大网关堆占用；正常超长生成（10 万 token 级）也会造成可感知的瞬时内存尖峰 × 并发数。
- **建议**: 为 accumulator 加字节上限（如 `nop.gateway.streaming.log-max-bytes`，超限截断并标记 truncated）与流最大时长（复用平台 `HighWatermarkSemaphore` 或简单字节数检查即可）；`onStreamError` 路径同步清理 accumulator（当前 `LoggingInterceptor.onStreamError` 不清理，依赖 context 整体释放）。
- **失效面枚举**（F3 同族清扫）: ①`contentBuilder`（正文聚合）——立案本体；②`toolCallAccumulators` 各 `argumentsBuilder`（工具调用参数聚合，键 `index` 数也无上界）——立案本体；③`BufferedStreamingPublisher.Attempt.buffer`（同文件族另一缓冲）——既有立案（C3-003），不重复；④`StreamingResponse`（仅持引用，无缓冲）——无缺陷；⑤`GatewayContextImpl` attribute 表（per-request 生命周期）——无缺陷。同族已穷举。
- **信心水平**: 确定
- **误报排除**: 不是「per-request 有界即合规」误报——本条的无界维度是**上游响应长度**（输入侧无界输入，维度定义第 3 条「单批超大输入绕过批上限」）；不是测试代码；且与既有 C3-003（转发缓冲/背压断裂）根因不同：本条无消费端、纯累积。
- **复核状态**: 未复核
- **复核状态（维度复核 2026-10-18）**: **保留，维持 P2**。证据亲验成立：`OpenAiDeltaAccumulator.java:14-15` 双缓冲无上界、`:68/:98` 逐 delta 无限追加、`:117` toMap 完成时再复制一份（序列化放大）；`LoggingInterceptor.onStreamStart:32-33` 每流式请求一个 accumulator 落 context attribute、`onStreamComplete:49-55` 清理、`onStreamError:64-67` **不清理**（依赖 context 整体释放）——初审现状描述逐行核实。P2 判级成立：触发需失控/恶意上游持续发送且单请求级生命周期，非高 QPS 确定性 OOM 形态。现状：未修复。

### [nop-service-framework-hotpath-C8-004] GraphQLSubscriptionManager 注册守卫为 check-then-act：并发注册可击穿 1000 上限并产生永久僵尸条目（无界账本）

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/subscription/GraphQLSubscriptionManager.java:84-104`
- **证据片段**:
  ```java
  public void registerSubscription(SubscriptionInfo subscription) {
      String operationId = subscription.getOperationId();
      if (subscriptionsById.size() >= maxActiveSubscriptions) {   // 检查（非原子）
          throw new NopException(ERR_GRAPHQL_SUBSCRIPTION_TOO_MANY)...
      }
      if (subscriptionsById.containsKey(operationId)) {           // 检查（非原子）
          throw new NopException(ERR_GRAPHQL_SUBSCRIPTION_DUPLICATE_OPERATION)...
      }
      subscriptionsById.put(operationId, subscription);           // 行动（与检查间无原子性）
      subscriptionsByOperation
              .computeIfAbsent(subscription.getOperationName(), k -> new CopyOnWriteArrayList<>())
              .add(subscription);
  ```
- **严重程度**: P2
- **并发场景**: 多个 WS 连接/多线程同时调用 `registerSubscription`（bean 为单例，`subscriptionsById` 为 CHM）。对照同文件 `OrmBatchLoader.makeDataLoader:45-53`——同仓库同类问题已用 `synchronized(context)` 串行化，此处未做。
- **触发时序**: T1、T2 携同一 operationId="X" 并发注册：T1 执行 containsKey(X)=false → T2 执行 containsKey(X)=false → T1 `put(X,S1)` → T2 `put(X,S2)`（S1 被**静默覆盖且永不 cancel**，其上游 Flow.Subscription 若存在则泄漏）→ byOperation 列表同时收下 S1、S2 两个条目 → `findMatchingSubscriptions:196` 只读 `subscriptionsById.values()`（仅剩 S2），S1 成为**不可见僵尸**；此后 `unregisterSubscription(X)` 只从 byId 移除 S2 并 `ops.remove(S2)`，S1 永久滞留 ops 列表。重复竞态（客户端断线重连风暴重发同 id）→ ops 列表单调增长（F4 无界账本），且 S1 不计入 maxActiveSubscriptions，上限对僵尸条目无效。size 检查（:87）与 put（:97）之间的窗口同样允许并发注册击穿 1000 上限（击穿幅度=竞态窗口内并发数）。
- **过载数字**: 上限声明值 1000；实际并发击穿后 = 1000 + W（W=竞态窗口内并发注册数，断线重连风暴下可达数百）；僵尸账本增长率 = 竞态命中次数，无回收路径。
- **现状**: 双重 check-then-act 守卫无原子性；`SubscriptionInfo.cancel()`（被覆盖方的上游取消）与列表清理在覆盖路径上双双缺失。
- **风险**: 守卫承诺的「最多 1000 个活跃订阅」在高并发注册下不成立；僵尸条目使 subscriptionsByOperation 无界增长；被覆盖订阅的上游资源不被取消（F8 清理不对称）。
- **建议**: 仿照 `OrmBatchLoader.makeDataLoader` 的既有裁定：对同一把锁（如 `synchronized(this)` 或 CHM+`putIfAbsent` 判定胜者）串行化「检查+put」，`put` 返回非空即视为重复并 throw + cancel 败者；`subscriptionsByOperation` 的 add 仅在 byId 胜出后执行。
- **失效面枚举**（F3 同族清扫）: ①`registerSubscription` size 守卫（:87）——立案本体；②`registerSubscription` duplicate 守卫（:92）——立案本体；③`JsonRpcWebSocketHandler.validSubscription`（:323-336，同形态 size+containsKey 检查后于 `sendStreamingMessage:353` put）——同族未修；缓解因素为单连接内 WS 消息按适配器惯例串行投递（未在本模块内保证），状态=同族登记、触发条件弱于本体；④`OrmBatchLoader.makeDataLoader`（:45-53）——已用 `synchronized(context)` 修复，状态=同族已修（正面对照）。
- **信心水平**: 确定（代码形态）；现状补充：当前仓库内 `registerSubscription` 无生产调用方（仅测试调用），缺陷处于「接线休眠」状态，bean 本体已随 `subscription-defaults.beans.xml` 激活 IMessageService 订阅，端点模块接入即触发——按 live code 形态立案，标注休眠。
- **误报排除**: 不是「CHM 原语本身」误报——问题在跨方法的三步复合操作；不是「有界声明存在」误报——上限守卫恰恰是本条证明失效的对象；未按 P1 报因当前无生产调用方（休眠路径），符合「写不出生产触发频率即降级」的纪律。
- **复核状态**: 未复核
- **复核状态（维度复核 2026-10-18）**: **保留，维持 P2**。check-then-act 亲验成立：`GraphQLSubscriptionManager.java:87`（size 检查）/:92（containsKey）/:97（put）三步无原子性，覆盖路径不 cancel 败者、byOperation 列表收僵尸条目（:98-100）；正面对照 `OrmBatchLoader.makeDataLoader` 的 `synchronized(context)` 同锁串行化亲验在场（初审判定引用的历史修复属实）。休眠现状复核成立：registerSubscription 生产 0 调用方（仅测试），manager bean 经 subscription-defaults.beans.xml 激活（ioc:default=true + @PostConstruct start() 订阅 `graphql-subscription/*` 主题）——按 live 形态立案、标注休眠的处理正确。P2 维持。现状：未修复。

### [nop-service-framework-hotpath-C8-005] AiRateLimitGatewayInterceptor 令牌桶基于墙钟回填：时钟回拨期间全桶停注、网关级 429 风暴（CL-003 深化为完整条目）

- **文件**: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/interceptor/AiRateLimitGatewayInterceptor.java:146-179,56-68`
- **证据片段**:
  ```java
  private void refill() {
      long now = CoreMetrics.currentTimeMillis();          // 墙钟，非单调钟
      long elapsed = now - lastRefillTime;
      if (elapsed >= refillIntervalMs) {
          long intervals = elapsed / refillIntervalMs;
          tokens = Math.min(capacity, tokens + intervals * refillRate);
          lastRefillTime = now;
      }
  }
  ...
  rejected.setHeader("Retry-After", String.valueOf(refillIntervalMs / 1000)); // 恒为 1（默认配置）
  ```
- **严重程度**: P2
- **并发场景**: 全部请求线程经 `bucket.tryConsume()`（每桶 synchronized）读取墙钟；无后台 refill 线程（惰性回填），时钟语义完全由请求线程采样。
- **触发时序**: T0=桶内 `lastRefillTime=t0`；T1=NTP 步进校正/宿主机迁移/人工对时使墙钟回跳 Δ（云主机秒~分钟级步进常见）→ 此后所有请求线程采样得 `elapsed = now - t0 < 0 < refillIntervalMs` → `refill()` 永不注token → 每桶在耗尽存量（默认 capacity=10，按 refillRate=1/s 约 10s 抽干）后对所有 key 持续返回 `tryConsume()=false` → `onRequest` 抛 GatewayRejectException(429) → **网关全量 429 长达 Δ**，且响应头 `Retry-After=1` 诱导客户端 1s 后立即重试（进一步放大请求速率）。正向跳变则一次性回填至 capacity（有界，行为良性但同样是墙钟敏感）。
- **过载数字**: 停注时长 = |Δ|（时钟回跳量）；全量拒绝开始于 Δ 事件后 ≤ capacity/refillRate = 10s；期间拒绝率 100%（合法请求全部 429）；`Retry-After` 恒 = refillIntervalMs/1000 = 1s，与实际 token 恢复时刻无关。
- **现状**: 限流实现已收敛 key 空间（normalizeKey 限长限字符集、maxTrackedKeys=100k、TTL 淘汰语义无损）——内存面合规；但 refill 的时基用 `CoreMetrics.currentTimeMillis()`（墙钟）而非单调钟，回填逻辑对负 elapsed 无防护。
- **风险**: 时钟步进事件（运维动作/虚拟化迁移/NTP 校正）期间网关对全部 AI 流量硬拒绝；对以 XFF 分桶的多客户端同时失效（共享同一墙钟）。
- **建议**: 时基改单调钟（`System.nanoTime()` 差值），仅在正 elapsed 时回填（`elapsed < 0` 时将 `lastRefillTime` 前移至 now，丢弃负区间）；`Retry-After` 按 `(1 - tokens)/refillRate * refillIntervalMs` 估算真实恢复时间并钳制下限 1s。
- **失效面枚举**（F3 同族清扫，热路径墙钟承重使用点）: ①`TokenBucket.refill:171`（承重——限流判定）——立案本体；②`TokenBucket` 构造器 `lastRefillTime=CoreMetrics.currentTimeMillis():158`——同族（初值墙钟，改为单调钟时需一并替换），状态=同族未修；③`BufferedStreamingPublisher.windowStart:118,253`（承重轻——仅窗口 N/T 越窗判定，回跳最多延迟越窗一次，不改数据正确性），状态=同族低危；④`InvokeProcessor.parseRetryAfter` HTTP-date 换算（:221，墙钟语义本身即 HTTP 规范要求，合规）；⑤`MetricsGraphQLHook`（micrometer Timer 内部单调钟，合规）；⑥`SubscriptionInfo.createTime:71`（展示用，合规）。承重缺陷仅①②。
- **信心水平**: 确定（触发依赖环境时钟事件，时序完全具体化）
- **误报排除**: 不是「时钟回拨属罕见理论风险」的泛化报告——NTP 步进校正与云主机迁移是生产常态，且失效面是网关热路径 100% 拒绝；不是平台设施重审——`CoreMetrics` 是 API 时钟工具，选型错误在本模块；未按 P1 报因触发需要环境事件叠加（非纯 QPS 触发），按判级口径落 P2。
- **复核状态**: 未复核
- **复核状态（维度复核 2026-10-18）**: **保留，维持 P2**。证据逐行亲验成立：`TokenBucket.refill:170-178` 用 `CoreMetrics.currentTimeMillis()` 墙钟、`elapsed` 负值无防护（`elapsed >= refillIntervalMs` 对负 elapsed 恒 false → 回拨 Δ 期间停注 Δ）；构造器 `:158` 同墙钟；拒绝路径 `:60-65` `Retry-After = refillIntervalMs/1000` 恒 1s（默认配置）；惰性回填（无后台 refill 线程）+ 每桶 synchronized 确认。量化数字（capacity=10、refillRate=1/s、refillIntervalMs=1000ms）与 live 默认值一致。P2 判级成立（需环境时钟事件叠加，非纯 QPS 触发）。与 C8-008 的「独立设施不同缺陷面」关系判定复核认可。现状：未修复。

### [nop-service-framework-hotpath-C8-006] 网关过载可观测性为零：限流拒绝/在途积压/流式缓冲/上游重试全部无指标（WI 2.1 C8-004 零指标族，网关子面）

- **文件**: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/`（全模块）；代表证据 `AiRateLimitGatewayInterceptor.java:60-66`、`GatewayConfigs.java:20-21`、`InvokeProcessor.java:152-167`
- **证据片段**:
  ```java
  // 限流拒绝：仅一条 warn 日志，无计数器（grep MeterRegistry|micrometer 于 nop-gateway main = 0 命中）
  if (!bucket.tryConsume()) {
      LOG.warn("Rate limit exceeded for key={}", key);
      ApiResponse<?> rejected = ApiResponse.buildSuccess(null);
      rejected.setHttpStatus(429);
  ```
  `GatewayConfigs` 全部配置仅 `nop.gateway.model-path` 一项——无任何指标/并发闸配置面。对照：GraphQL 层**有**可观测接线（`MetricsGraphQLHook`：graphql.execute/invoke/data-fetch 成败 Timer + `RpcServerStat.runningCount` 在途计数，经 `nop-biz/_vfs/nop/biz/beans/biz-defaults.beans.xml` 装配）——零指标面精确局限于 nop-gateway。
- **严重程度**: P2
- **并发场景**: 全部请求线程在过载事件（限流拒绝、上游 429 重试、流式缓冲堆积、fallback 切换）发生时的可观测路径——仅有同步 LOG（且限流拒绝日志按 key 逐条打印，过载时日志本身放大）。
- **触发时序**: 过载发生（如 C8-002 的上游 429 洪峰）→ 运维查询在途请求数/拒绝数/重试数/缓冲深度 → 无任何 metric 可查（无 MeterRegistry 引用、无 Micrometer 计时、无 gauge）→ 只能靠日志聚合（被限流 warn 日志自身洪峰干扰）→ 过载不可量化、不可告警、容量规划无依据。
- **过载数字**: 指标覆盖面 = 0/4（限流拒绝数、在途并发、流式缓冲深度、上游重试/fallback 计数全部缺失）；对照 GraphQL 层 6 个 Timer + runningCount 已覆盖。
- **现状**: nop-gateway 模块零 micrometer 接线；平台 `IRateLimiter` 自带 `acquireSuccessCount/acquireFailCount`（`DefaultRateLimiter.java:17-18,50-58`），但网关自研 TokenBucket 未接任何计数面；Sentinel runner 的 block 事件也仅 LOG.warn（Sentinel 自身指标依赖外部 dashboard 配置，模块内未接线）。
- **风险**: C8-001/C8-002/C8-005 的过载事件在发生时不可见、结束后不可追溯；限流阈值调优无数据支撑；与平台「graphql 层有 metrics」的可观测水位不一致。
- **建议**: 复用 `GlobalMeterRegistry` + `MetricsGraphQLHook` 同款模式为网关补四组指标：`gateway.rate-limit.rejected`（Counter，tag:key 类别）、`gateway.inflight`（Gauge）、`gateway.streaming.buffer.depth`（Gauge，BufferedStreamingPublisher 暴露）、`gateway.upstream.retry|fallback`（Counter）；限流拒绝日志加采样/速率抑制。
- **失效面枚举**（F3 同族清扫）: ①`AiRateLimitGatewayInterceptor.onRequest` 拒绝点——立案本体；②`SentinelFlowControlRunner` block 分支（仅 warn，无 metric 出口）——同族未接；③`DefaultRateLimiter` 成败计数（已有计数器字段但范围内无导出方）——同族半成品；④`InvokeProcessor` 重试/fallback 分支——同族未接；⑤`BufferedStreamingPublisher` 生命周期回调（`onFetchStarted/onStreamTerminated` 有回调钩子但无 metric 消费方）——同族未接；⑥GraphQL `MetricsGraphQLHook`——已覆盖（正面对照）。网关侧五个面全部缺失。
- **信心水平**: 确定（grep 口径：`grep -rn "MeterRegistry\|micrometer" nop-gateway/src/main/java` = 0 命中，可复现）
- **误报排除**: 不是「日志即观测」误报——维度定义第 4 条明确要求积压深度/拒绝数指标；不是把 GraphQL 已有指标错报为缺失——已明确区分两层；非跨维度（C9 性能）问题——本条是过载行为可见性，属 C8 定义原文。
- **复核状态**: 未复核
- **复核状态（维度复核 2026-10-18）**: **保留，维持 P2**。grep 口径复现：`grep -rn "MeterRegistry\|micrometer" nop-gateway/src/main/java` = 0 命中（独立重跑确认）；GraphQL 层正面对照亲验：`biz-defaults.beans.xml:18,23` graphQLHook → nopMetricsGraphQLHook 装配在场，`MetricsGraphQLHook` 与 `RpcServerStat` 接线属实；`AiRateLimitGatewayInterceptor.onRequest:60-65` 拒绝点仅 LOG.warn 无计数面核实。P2 维持。现状：未修复。

### [nop-service-framework-hotpath-C8-007] WS 订阅聚合面无全局上界：每连接硬编码 1000 × 连接数无准入、上限不可配置（C4-002 聚合面量化）

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/ws/JsonRpcWebSocketHandler.java:56,59,82-84,323-336,349-355`
- **证据片段**:
  ```java
  protected final Map<String, Flow.Subscriber<ApiResponse<?>>> activeOperations = new ConcurrentHashMap<>();
  private int maxActiveOperations = 1000;                       // 硬编码，无配置面
  ...
  private boolean validSubscription(String operationId, String method) throws IOException {
      if (activeOperations.size() >= maxActiveOperations) {     // 仅每连接上界
          LOG.warn("nop.websocket.too-many-active-operations:opId={}", operationId);
          sendError(operationId, JsonRpcWebSocketErrorCodes.TOO_MANY_SUBSCRIPTIONS, ...);
  ```
- **严重程度**: P2
- **并发场景**: N 个 WS 连接（每连接一个 handler 实例，`JsonRpcWebSocketEndpoint`（nop-quarkus-web/nop-spring-web-starter）每连接 `new JsonRpcWebSocketHandler(...)`）× 每连接至多 1000 个活跃 Flow 订阅 + 1 个 keepAlive 定时任务（全局 timer 5 线程共享）。跨连接无任何全局注册表/信号量/准入闸。
- **触发时序**: 攻击者或失控前端以 C 连接/s 建连、每连接注册 ≤1000 订阅 → 聚合活跃订阅 = 1000N 无上界；每个活跃订阅持有 `SubscriptionSubscriber` + Flow 链 + 其 publisher 闭包捕获的执行上下文（selection 树、authHeaders 副本等，KB~百 KB 级/订阅）→ 堆占用随连接数线性无界增长；同时 keepAlive 任务数 = N（10s 周期）落 5 线程全局 timer，N 大时 timer 调度延迟单调上升（与 C8-001 阻塞面叠加）。
- **过载数字**: 聚合订阅上界 = 1000×N（N 无限）；N=1000 连接 × 1000 订阅 = 10^6 订阅 × ~10KB 上下文 ≈ **10GB 量级堆**（每订阅上下文按保守 10KB 估，实测随 selection 复杂度上浮）；timer 任务到达率 = N/10s，N=5000 时 500 任务/s 对 5 线程池即接近饱和。
- **现状**: 每连接 1000 的守卫存在且拒绝语义正确（TOO_MANY_SUBSCRIPTIONS/4409）；但 ① 无跨连接全局上界；② `setMaxActiveOperations`/`setMaxActiveSubscriptions` 全仓无调用方（含 beans/配置文件），上限不可调；③ 活跃订阅总数无 gauge（见 C8-006）。
- **风险**: 高连接数部署（网关型/多租户前端）下订阅聚合内存与定时器调度无防护；批量建连成为低成本放大器。
- **建议**: 引入全局订阅注册表或平台 `HighWatermarkSemaphore`（高水位拒绝新订阅）；`maxActiveOperations` 提为配置（`@cfg:nop.graphql.ws.max-active-operations|1000`，端点构造处注入）；配合 C8-006 暴露 `graphql.ws.subscriptions` gauge。
- **失效面枚举**（F3 同族清扫）: ①`JsonRpcWebSocketHandler.maxActiveOperations`（每连接）——立案本体；②`GraphQLSubscriptionManager.maxActiveSubscriptions`（全局 1000，但守卫可击穿且当前无注册调用方，见 C8-004）——同族，失效形态不同；③keepAlive 每连接 timer 任务（:82）——同族聚合面（C4-002 已立案任务存在性，本条量化聚合），状态=量化并入；④`activeOperations` 条目清理（onComplete/onError 双参 remove + onClose 全清）——配对正确，无缺陷。
- **信心水平**: 确定（结构面）；内存估算标注为量级推算（10KB/订阅为保守值，非实测）
- **误报排除**: 不是「有界声明存在」误报——每连接 1000 的界真实存在，本条立的界是**聚合面无上界**与**界不可配置**两个不同失效层；不是对 WS 服务器通用行为的苛求——准入控制属本模块订阅管理职责（模块已自建 per-connection 守卫，说明其承认该职责）；判定 P2 因需要大规模连接数（非默认小流量即可触发）。
- **复核状态**: 未复核
- **复核状态（维度复核 2026-10-18）**: **保留，维持 P2（证据面局部修正）**。主面复核成立：`JsonRpcWebSocketHandler.java:59` maxActiveOperations=1000 硬编码、`setMaxActiveOperations` 全仓 grep（java/xml/yaml/properties）= **0 调用方**（quarkus/spring 两端点构造 handler 均不注入）——per-connection 上限不可配置、跨连接聚合（1000×N）无全局闸、订阅持有链钉住执行上下文，三点均成立；keepAlive 任务数=连接数（非每订阅）的算术复核成立。**证据面修正一处（两轮均漏）**：初审判定「`setMaxActiveSubscriptions` 全仓无调用方（含 beans/配置文件）」、第 2 轮「仅 2 处测试调用」均不完整——`subscription-defaults.beans.xml:13` 以 `<property name="maxActiveSubscriptions" value="@cfg:nop.graphql.subscription.max-active|1000"/>` 经 IoC 注入（2026-08-23 commit 60bd61896c 即已存在），**manager 全局上限实为可配置项**。「上限不可配置」子主张收窄为「per-connection 上限不可配置」；且该 manager 注册路径休眠（registerSubscription 0 生产调用方），其可配置上限对 live WS 路径不生效——聚合无上界主面不受影响。内存量级推算标注合理（保守值已声明）。P2 维持。现状：未修复。

### [nop-service-framework-hotpath-C8-008] DefaultFlowControlRunner 忽略 FlowControlEntry 维度：单全局桶无 keying，且 invoker/fetcher 双站点共享同一限流器导致许可语义混淆（CL-011 复核=不成立，另立新缺陷）

- **文件**: `nop-network/nop-rpc/nop-rpc-core/src/main/java/io/nop/rpc/core/flowcontrol/DefaultFlowControlRunner.java:36-58`（prompt 点名对象）；使用面 `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/engine/GraphQLFlowControlInvoker.java:33,49`、`FlowControlFetcher.java:32,41`、`GraphQLExecutor.java:309,323,337-341`、`GraphQLEngine.java:693-694,894`
- **证据片段**:
  ```java
  // DefaultFlowControlRunner: entry 的 resource/origin/bizKey 全部被忽略——所有调用点共用一个 Guava 桶
  public <T> CompletionStage<T> runAsync(FlowControlEntry entry, Supplier<CompletionStage<T>> task) {
      boolean acquired = rateLimiter.tryAcquire(1, maxWaitMillis);   // maxWaitMillis 默认 0，非阻塞
  ...
  // GraphQLEngine:693-694 与 :894 —— 同一个 runner 实例同时挂在请求级 invoker 与每 operation fetcher 两层
  if (flowControlRunner != null)
      executionInvoker = new GraphQLFlowControlInvoker(flowControlRunner, executionInvoker);
  ...
  return new GraphQLExecutor(operationInvoker, graphQLHook, flowControlRunner, this);
  ```
- **严重程度**: P2
- **并发场景**: 全部请求线程在同一 `IRateLimiter` 实例上 `tryAcquire`；限流许可被两个粒度的调用点消耗——请求级（`GraphQLFlowControlInvoker`，resource="/graphql"）与 operation 级（`GraphQLExecutor.withFlowControl` 包裹的每个顶层 operation fetcher，resource=selection 名）。
- **触发时序**: 部署方以 nop-rpc-core 自带的 `DefaultFlowControlRunner` 装配 `flowControlRunner`（非缺省；缺省为 nop-cluster-sentinel 的 `SentinelFlowControlRunner`，`ioc:default=true`）并设定 maxPermitsPerSecond=R → 一个携带 K 个顶层 operation 的请求消耗 1+K 个许可（invoker 1 次 + 每operation fetcher 各 1 次，同一桶）→ 实际请求容量 = R/(1+K) 而非 R，配置者按「每秒 R 个请求」理解即被静默击穿；同时 origin/bizKey（`FlowControlEntry` 已携带的租户维度）被完全丢弃——单一大租户即可耗尽全局桶，全部其他租户被拒（noisy neighbor 无隔离）。
- **过载数字**: 语义偏差系数 = 1+K（K=文档顶层 operation 数，上限 10 → 最多 11×）；全局桶 R 对所有租户共享，单租户吃满 R 后其余租户拒绝率 100%。
- **现状（含 CL-011 复核结论）**: ①对 prompt 所示 CL-011「阻塞式限流」：live code 复核为**不成立**——`tryAcquire(1, maxWaitMillis=0)` 非阻塞、立即拒绝（Guava tryAcquire 0 超时语义）；`maxWaitMillis` 无任何生产配置点，缺省 0；缺省 runner 为 `SentinelFlowControlRunner`（`SphU.entry` 抛 BlockException 即拒，亦非阻塞）。本条目维持 CL-011 不成立的盲审结论，另立上述 keying/语义缺陷。②缺省 Sentinel 路径 keying 正确（resource 逐 operation 传递，origin 作为 Sentinel origin），本缺陷特定于 DefaultFlowControlRunner 实现。
- **风险**: 选择内置 runner 的部署获得「看似有限流、实则语义错误」的保护：请求容量被 K 静默稀释、租户间无隔离、`FlowControlEntry` 的 origin/bizKey 采集成本白付。
- **建议**: DefaultFlowControlRunner 按 `entry.getResource()`（或 resource+origin）分桶（`LocalCache<String,IRateLimiter>`，仿 `AiRateLimitGatewayInterceptor.buckets()` 的有界缓存模式）；或在 javadoc 明示「全局单桶、许可粒度=调用点」并从 invoker/fetcher 双站点中去掉一个（单点限流）。
- **失效面枚举**（F3 同族清扫）: ①`DefaultFlowControlRunner.runAsync`（:36-46）——立案本体；②`DefaultFlowControlRunner.run`（:49-58）——同族同缺陷（同步版，无生产调用方）；③`SentinelFlowControlRunner.runAsync/run`——同族已正确 keying（按 resource 埋点），状态=合规对照；④`GraphQLFlowControlInvoker`（请求级消耗点）与⑤`GraphQLExecutor.withFlowControl`（operation 级消耗点）——双站点叠加为本条语义缺陷的构成面，修复须同时覆盖两处；⑥`FlowControlRpcServiceInterceptor`（nop-rpc-api，RPC 出口同 runner 接口）——同族潜在双消耗，本次未展开（超出 5 模块热路径），登记待查。
- **信心水平**: 确定（代码语义）；实际部署影响取决于 runner 选型（缺省 Sentinel 不受影响）——已如实标注
- **误报排除**: 不是平台设施重审——`DefaultFlowControlRunner` 是 prompt 点名的审计对象且属 nop-rpc 对外实现类；不是把「有限流」误报为「无限流」——缺陷是粒度/语义而非存在性；与 C8-005 互补（一个是网关自研桶的时钟缺陷，一个是平台 runner 的 keying 缺陷，不同失效层）。
- **复核状态**: 未复核
- **复核状态（维度复核 2026-10-18）**: **保留，维持 P2**。证据亲验成立：`DefaultFlowControlRunner.java:36-46` `entry` 参数完全未使用（单全局桶）、`tryAcquire(1, maxWaitMillis=0)` 非阻塞立即拒绝——**CL-011「阻塞式限流」盲审不成立结论复核维持**（Guava 0 超时 tryAcquire 语义）；双消耗站点亲验：`GraphQLFlowControlInvoker.newFlowEntryForExecution`（resource="/graphql" 请求级）+ `FlowControlFetcher.newFlowEntry`（resource=selection 名 operation 级），经 `GraphQLEngine.getExecutionInvoker:693-694` 与 `GraphQLExecutor.withFlowControl:335-341` 同一 runner 实例两层挂接——1+K 许可稀释语义成立（K≤10 → 最多 11×）。缺省 runner 复核：`sentinel-defaults.beans.xml` `nopFlowControlRunner` `ioc:default=true`（SentinelFlowControlRunner），DefaultFlowControlRunner 仅在显式装配时生效——标注如实。P2 维持。现状：未修复。

---

## 维度集外线索（不立案展开）

### [nop-service-framework-hotpath-CL-012]（维度集外线索，不立案展开）ForwardProcessor 路由转发链无深度守卫，配置成环即每请求无限递归

- **文件**: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/executor/ForwardProcessor.java:51-100`
- **证据片段**:
  ```java
  public CompletionStage<ApiResponse<?>> forward(GatewayForwardModel forward, ApiRequest<?> request,
                                                 IGatewayContext context,
                                                 Function<GatewayRouteModel, CompletionStage<ApiResponse<?>>> routeExecutor) {
      ...
      GatewayRouteModel targetRoute = findRouteById(targetRouteId);
      ...
      return executeAndRestore(routeExecutor, targetRoute, savedRoute, context);
  ```
- **形态归属**: 配置校验/正确性（fail-fast 守卫缺失），非背压/流控形态——A→B→A 转发环将按请求同步递归直至 StackOverflowError。触发条件为网关模型配置错误（管理面输入），非流量过载，不属于 C8 判级口径，故登记为线索。建议：forward 深度计数（context attribute 递增，>N 拒绝）。
- **复核处置（维度复核 2026-10-18）**: **确认**。`ForwardProcessor.forward:51-100` 亲验：determineTargetRouteId → findRouteById → executeAndRestore(routeExecutor.apply(targetRoute)) 全链无深度计数/环检测；「配置错误形态、维度集外」归类认可，维持线索登记不立案。

### [nop-service-framework-hotpath-CL-013]（维度集外线索，不立案展开）GatewayHttpFilter 全量物化请求体，模块内无大小上界

- **文件**: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/http/GatewayHttpFilter.java:143-146,344-351`
- **证据片段**:
  ```java
  context.resumeRequest();
  return context.getRequestBody().getTextAsync().thenCompose(text -> {
      ApiRequest<?> request = buildRequest(context, text);
  ...
  protected Object parseRequestData(IHttpServerContext context, String requestText) {
      ...
      return JSON.parse(requestText);
  ```
- **形态归属**: C5 内存有界性（无界输入全量载入 + JSON 树 2-5 倍放大）。模块内未见任何 body 大小钳制；实际上界取决于 HTTP 服务器适配器（本审计范围外）。与 C8-002 叠加时（重试持有期内每请求保留全量 body），放大面已在 C8-002 计入。建议在适配器/网关层确认 max body size 配置存在。
- **复核处置（维度复核 2026-10-18）**: **确认**。`GatewayHttpFilter.java:145-147`（getTextAsync→buildRequest）与 `:344-351`（strict JSON.parse）亲验；独立补查——模块内唯一「maxBodyLength」是 `Slf4jGatewayLogger.java:14` 的**日志截断**（4096，非承重），请求体大小在 nop-gateway 内确无钳制点；「实际上界取决于 HTTP 适配器、C5 形态登记」处置认可。

### [nop-service-framework-hotpath-CL-014]（维度集外线索，不立案展开）DownloadHelper 临时 zip 无磁盘配额、删除仅靠进程内延迟任务（重启遗留，F4 族）

- **文件**: `nop-service-framework/nop-biz/src/main/java/io/nop/biz/download/DownloadHelper.java:25-52`
- **证据片段**:
  ```java
  os = resource.getOutputStream();
  zipOutput = zipTool.newZipOutput(os, zipOptions);
  action.accept(zipOutput);                     // 全量 zip 同步写盘，无大小/时长上限
  ...
  GlobalExecutors.globalTimer().schedule(() -> {
      resource.delete();                        // 进程内延迟删除；重启即遗留
  }, waitMinutes, TimeUnit.MINUTES);
  ```
- **形态归属**: C6 资源生命周期 / C7 单调增长（磁盘账本无恢复路径）：并发下载无配额（磁盘面无上界）、删除任务落在共享全局 timer（进程重启前未触发的删除永久丢失，临时目录只增不删）、zip 生成在请求线程同步执行（时长无超时）。方法当前在 5 模块 main 内未发现调用方（公共 helper，供宿主模块使用），按线索登记。
- **复核处置（维度复核 2026-10-18）**: **确认**。`DownloadHelper.java:25-52` 亲验：`:39-42` globalTimer 延迟删除（重启即遗留）、`:33` 同步 zip 写盘无上限，均成立；补充正面对照一处——异常路径 `:49` 有 `resource.delete()` 即时清理（失败不留盘），线索形态描述无需修正；「5 模块 main 内无调用方」复核成立，维持线索登记。

---

## Stub 判定复核记录

- **ChunkFileUploadHandler**（`nop-biz-file-core/.../chunk/ChunkFileUploadHandler.java:14-32`）：复核确认**纯 stub**——`startChunkApi/chunkApi/finishChunkApi` 三方法一律 `throw new UnsupportedOperationException(...)`，无任何分片存储/速率/大小逻辑。C4 轮「纯 stub」判定维持成立；「分片上传无速率/大小限制」不构成缺陷立案（无实现即无热路径），分片协议 bean 面存在但 fail-fast。其同文件族的整文件上传路径 `NopFileStoreBizModel.upload` 已有 maxFileSize（默认 16MB）+ 后端 `DaoResourceFileStore.saveFile` 以 `LimitedInputStream(is, maxLength)` 二次钳制（不信任客户端声明长度），上传流控面合规。
- **JsonRpcWebSocketHandler 同族守卫**（C8-004 失效面③）：`validSubscription` 与 `activeOperations.put` 之间无原子性，因 WS 消息单连接串行投递惯例而触发条件弱，未单独立案，已在该条失效面枚举中登记。

## 最终保留项（本轮输出清单）

| 编号 | 严重程度 | 文件 | 摘要 |
|---|---|---|---|
| C8-001 | P1 | nop-graphql-core `JsonRpcWebSocketHandler.java:457-469,82-83`；`GraphQLSubscriptionManager.java:204-218` | WS 同步阻塞推送：慢客户端阻塞发布线程/钉死全局 timer（CL-008 深化） |
| C8-002 | P1 | nop-gateway `InvokeProcessor.java:135-167`；`AiFailoverGatewayInterceptor.java:73-145` | 上游 429/5xx 重试×failover 放大 ×7，在途无并发上界 |
| C8-003 | P2 | nop-gateway `OpenAiDeltaAccumulator.java:14-15,63-98` | 流式日志聚合缓冲无响应长度上界（C3-003 同族其他缓冲） |
| C8-004 | P2 | nop-graphql-core `GraphQLSubscriptionManager.java:84-104` | 注册守卫 check-then-act 可击穿 + 僵尸条目无界账本（休眠路径） |
| C8-005 | P2 | nop-gateway `AiRateLimitGatewayInterceptor.java:146-179` | 令牌桶墙钟回填：时钟回拨 → 全桶停注、网关级 429（CL-003 深化） |
| C8-006 | P2 | nop-gateway（全模块） | 过载零指标：限流拒绝/在途/缓冲/重试均无 metric（WI 2.1 C8-004 族网关子面） |
| C8-007 | P2 | nop-graphql-core `JsonRpcWebSocketHandler.java:56,59,323-336` | WS 订阅聚合无全局上界（1000×N）、上限不可配置（C4-002 聚合量化） |
| C8-008 | P2 | nop-rpc-core `DefaultFlowControlRunner.java:36-58`（+graphql 双消耗站点） | 限流单全局桶无 keying、invoker/fetcher 双站点共享许可（CL-011 复核=不成立） |
| CL-012 | — | nop-gateway `ForwardProcessor.java:51-100` | 转发环无深度守卫（配置正确性形态） |
| CL-013 | — | nop-gateway `GatewayHttpFilter.java:143-146,344-351` | 请求体全量物化无模块内上界（C5 形态） |
| CL-014 | — | nop-biz `DownloadHelper.java:25-52` | 临时 zip 无配额、重启遗留（C6/C7 形态） |

## 总计

- **立案发现**: 8 条（P1×2：C8-001、C8-002；P2×6：C8-003 ~ C8-008；P0×0、P3×0）
- **维度集外线索**: 3 条（CL-012 ~ CL-014，从 CL-012 起连续编号，不立案展开）
- **既有登记处置**: CL-003 → 深化为完整条目 C8-005；CL-008 → 深化为完整条目 C8-001；C3-003 → 不重复立案，同族其他缓冲深化为 C8-003；C4-002 → 聚合面量化并入 C8-001/C8-007；CL-011 → 盲审复核为**不成立**（live code 非阻塞），在 C8-008 中记录并另立 keying 缺陷；WI 2.1 C8-004 零指标族 → 网关子面立案 C8-006
- **复核状态**: 全部条目未复核（待阶段二维度复核）

---

## 维度复核结论

复核人：C8 维度独立复核子代理（与初审两轮互不交叉）。复核基点：仓库 `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`，分支 `concurrency-audit-remediation`（产品代码最后提交 2026-08-26，早于本审计——全部发现现状=未修复）。对照面按复核阶段口径开放：`cra-index.md`、C4 r2/r3（C4-003 ×7 勘误 + JDK 25 Delayer 勘误 8 + 降 P2 终裁）、plan 2470（单元范围与判级口径）、live code 全量亲验。方法：10 条发现 + 3 条 CL 逐条亲开 file:line 核证；C8-001/C8-002/C8-009 按特别复核指令独立执行机制、算术与 grep 终裁。

### 逐条裁定

| 编号 | 初审判级 | 复核裁定 | 要点 |
|---|---|---|---|
| C8-001 | P1 | **保留，维持 P1** | 机制全部亲验（basicRemote 同步无写超时 / keepAlive 落 globalTimer / LocalMessageService 发布者线程同步回调）；**globalTimer ≠ JDK Delayer 区分确认**——C4-003 勘误 8 不影响本条；量化修正：真实全局定时器为 **4 线程**（非 5，`ExecutorHelper.newScheduledExecutor:63-65` 强制 maxPoolSize=0 按 corePoolSize=4 建池，`global-timer.maxPoolSize` 旋钮死配置），钉死门槛「1~5 个」精化为「4~8 个（有限 L）/ 4 个（持续阻塞）」，方向利好本条；globalTimer 平台 27 处生产站点实测，全平台停摆面成立 |
| C8-002 | P1 | **保留，维持 P1** | ×7 加法（4 主 + 3 fallback 单发）亲验成立；无准入闸 grep=0 复现；GatewayConfigs 单配置亲验；内存参数化同数量级成立（精化 S×(5~7)→S×(3~6)，裸 String 首层 lambda 后大概率不可达）；round2「Delayer 全局单线程」表述按 C4 勘误 8 更正（JDK 25 走 ForkJoinPool common pool relay）——P1 支柱（在途无上界内存累积 + 放大）不依赖该线程语义，与 C4-003 降级事由不同柱 |
| C8-003 | P2 | **保留，维持 P2** | 双缓冲无上界、逐 delta 追加、toMap 二次复制、onStreamError 不清理全部逐行亲验 |
| C8-004 | P2 | **保留，维持 P2** | check-then-act 三步亲验；僵尸条目/覆盖不 cancel 成立；休眠现状成立；OrmBatchLoader 正面对照在场 |
| C8-005 | P2 | **保留，维持 P2** | 墙钟 refill + 负 elapsed 无防护 + Retry-After 恒 1s 逐行亲验；默认值数字与 live 一致 |
| C8-006 | P2 | **保留，维持 P2** | gateway grep=0 独立复现；GraphQL 层 metrics 正面对照（biz-defaults.beans.xml:18,23）亲验在场 |
| C8-007 | P2 | **保留，维持 P2（证据面局部修正）** | per-connection 1000 硬编码 + setter 0 调用方 + 聚合无上界成立；**修正**：`subscription-defaults.beans.xml:13` 经 `@cfg:nop.graphql.subscription.max-active|1000` 注入 setMaxActiveSubscriptions（两轮初审均漏查 beans 注入即调用方）——manager 全局上限实为可配置，「上限不可配置」子主张收窄为 per-connection 面；该 manager 注册路径休眠，主面不受影响 |
| C8-008 | P2 | **保留，维持 P2** | entry 全忽略 + tryAcquire(1,0) 非阻塞 + 双站点共享 runner + 缺省 Sentinel ioc:default 全部亲验；CL-011「阻塞式限流」不成立结论复核维持 |
| C8-009 | P2 | **保留，维持 P2** | 三站点 WARN 不关会话 + PONG 空分支 + setMaxIdleTimeout 全仓 0 + 三线程域并发写无串行化全部亲验；池 4 线程修正使饱和阈值更低（方向不变） |
| C8-010 | P3 | **保留，维持 P3** | grep=0 复现；16MB 钳制兜底亲验（`@cfg:nop.file.upload.max-size|16777216`）；纯可观测性缺失定性准确 |
| CL-012 | 线索 | **确认** | ForwardProcessor 全链无深度守卫亲验；配置错误形态、维度集外归类认可 |
| CL-013 | 线索 | **确认** | getTextAsync+JSON.parse 全量物化亲验；模块内仅日志截断（Slf4jGatewayLogger:14，非承重），body 无钳制点 |
| CL-014 | 线索 | **确认** | globalTimer 延迟删除重启遗留 + 同步 zip 亲验；补充正面对照：异常路径 :49 即时删除 |

### 终裁说明（特别复核指令）

1. **C8-001 终裁 P1 维持**：a) basicRemote 同步发送无写超时成立（两适配器 `getBasicRemote().sendText`，`getAsyncRemote` 全仓 0）；b) ρ 量化修正后方向不变——钉死门槛为个位数慢客户端（真实 4 线程池比初审认定的 5 线程更小）；c) globalTimer（平台 DefaultScheduledExecutor，4 线程）与 JDK Delayer 无涉，C4-003 降 P2 勘误不外溢到本条；d) keepAlive 腿无条件 live（`/ws/jsonrpc` 端点已装配）、平台 27 处 globalTimer 用户承载 retry 扫描/leader 选举/job 扫描/task 调度，「常见条件 + 服务级平台面停摆」满足 campaign P1 bar。审计自注的 L 容器相关性维持（容器可能自带阻塞发送超时，降低单客户端 L，但个位数客户端钉死 4 线程池的结论在两类容器语义下均成立）。
2. **C8-002 终裁 P1 维持**：a) ×7 加法沿 C4 勘误 1 确认；b) 内存参数化抽验成立（系数精化 S×(3~6)，OOM 轨迹不变）；c) 无准入闸 grep=0 确认；d) 与 C4-003 降 P2 的区分——C4 降级事由是其 P1 支柱「JDK 单线程 Delayer 被重试重活占用」在 JDK 25 被证伪，而 C8-002 的 P1 支柱是「429 过载常态场景下在途请求随 λ 线性累积、模块内零上界、每在途挂起 ≈90s+ 持有 body 解析树」的高 QPS 确定性 OOM 轨迹，该支柱独立于线程语义且全部成立。round2 的 Delayer 单线程表述已按勘误 8 标注更正（不改事实、不改判级）。
3. **C8-009 终裁 P2 维持**：三站点仅 WARN、keepAlive 单向 ping（PONG 空分支）、无 setMaxIdleTimeout 三点全部成立；池 4 线程修正使其聚合面更严重但判级口径不变（半开连接增长率依赖部署环境，参数化合理）。

### 争议点与勘误登记

- **勘误 R1（C8-001/C8-009 量化，方向利好）**：`GlobalExecutors.globalTimer()` 真实线程数 = 4（`ThreadPoolConfig.java:17` corePoolSize 默认 4；`ExecutorHelper.newScheduledExecutor:63-65` 对 ScheduledThreadPoolExecutor 强制 `setMaxPoolSize(0)` 并按 corePoolSize 建池）——`CommonConfigs.CFG_CONCURRENT_GLOBAL_TIMER_MAX_POOL_SIZE`（默认 5）为死配置旋钮。初审「5 线程」「ρ=N·L/50」「5 个慢客户端钉死」全部按 4 线程/ρ=N·L/40/「4~8 个（有限 L，计入 scheduleWithFixedDelay 周期 10+L）；持续阻塞 4 个」修正。
- **勘误 R2（C8-002/round2 §② 表述，沿 C4 勘误 8）**：「JDK Delayer 全局单线程调度器 + 无界 DelayedWorkQueue」系 JDK≤23 语义；JDK 25 下 delayedExecutor 走 ForkJoinPool common pool DelayScheduler（单线程仅 relay）。不改判级（见终裁 2）。
- **勘误 R3（C8-007 证据面）**：`setMaxActiveSubscriptions` 并非「全仓无调用方」——`subscription-defaults.beans.xml:13` 的 IoC property 注入即调用方（配置项 `nop.graphql.subscription.max-active`，默认 1000）。两轮初审 grep 均未把 beans property 注入计为调用方。子主张收窄，主面（per-connection 硬编码 + 聚合无上界 + 注册路径休眠）维持。
- **精化（C8-002 内存系数）**：每在途 ≈ S×(3~6)（裸 text String 在首层 lambda 执行后大概率不被挂起链强可达，持久持有的是 JSON.parse 树 + context），初审 S×(5~7) 偏保守上端，同数量级不影响 OOM 轨迹。
- **降级/驳回**：无。本轮零降级零驳回的原因：初审两轮的承重证据（阻塞原语、重试算术、grep 口径、check-then-act 形态、墙钟时基）全部可独立复现；唯二证据瑕疵（R3、内存系数）均不动摇判级支柱。

### 复核统计

- **发现 10 条**：保留 10（P1×2 维持：C8-001/002；P2×7 维持：C8-003~009；P3×1 维持：C8-010）；降级 0；驳回 0。
- **维度集外线索 3 条**：确认 3（CL-012/013/014），驳回 0。
- **勘误 3 项**（R1 池线程数 / R2 Delayer 表述 / R3 beans 注入调用方）+ 精化 1 项（内存系数），均不改判级。
- **现状标注**：全部 10 条发现 = 未修复（产品代码最后提交 2026-08-26，先于审计；分支上后续提交均为 CRA 审计文档）。

## 最终保留项（复核后输出清单）

| 编号 | 复核后严重程度 | 文件 | 摘要 | 复核状态 |
|---|---|---|---|---|
| C8-001 | P1 | nop-graphql-core `JsonRpcWebSocketHandler.java:82-83,457-469`；`GraphQLSubscriptionManager.java:204-218` | WS 同步阻塞推送：慢客户端阻塞发布线程/钉死平台全局定时器（实为 4 线程，27 处平台用户） | 已复核·保留 |
| C8-002 | P1 | nop-gateway `InvokeProcessor.java:135-167`；`AiFailoverGatewayInterceptor.java:73-145` | 上游 429/5xx 重试×failover 放大 ×7（4+3 加法），在途无并发上界，高 QPS 确定性 OOM 轨迹 | 已复核·保留 |
| C8-003 | P2 | nop-gateway `OpenAiDeltaAccumulator.java:14-15,63-98` | 流式日志聚合缓冲无响应长度上界（C3-003 同族其他缓冲） | 已复核·保留 |
| C8-004 | P2 | nop-graphql-core `GraphQLSubscriptionManager.java:84-104` | 注册守卫 check-then-act 可击穿 + 僵尸条目无界账本（休眠路径） | 已复核·保留 |
| C8-005 | P2 | nop-gateway `AiRateLimitGatewayInterceptor.java:146-179` | 令牌桶墙钟回填：时钟回拨 → 全桶停注、网关级 429（CL-003 深化） | 已复核·保留 |
| C8-006 | P2 | nop-gateway（全模块） | 过载零指标：限流拒绝/在途/缓冲/重试均无 metric（WI 2.1 C8-004 族网关子面） | 已复核·保留 |
| C8-007 | P2 | nop-graphql-core `JsonRpcWebSocketHandler.java:56,59,323-336` | WS 订阅聚合无全局上界（1000×N）、per-connection 上限不可配置（C4-002 聚合量化；manager 全局上限实为可配置项——勘误 R3） | 已复核·保留 |
| C8-008 | P2 | nop-rpc-core `DefaultFlowControlRunner.java:36-58`（+graphql 双消耗站点） | 限流单全局桶无 keying、invoker/fetcher 双站点共享许可（CL-011 复核=不成立） | 已复核·保留 |
| C8-009 | P2 | nop-graphql-core `JsonRpcWebSocketHandler.java:466-468,357-363,149-150` | WS 会话无死活检测：发送失败仅告警不断连、keepAlive 单向 ping、无 idle timeout | 已复核·保留 |
| C8-010 | P3 | nop-biz-file-core（全 17 文件）；nop-biz `DownloadHelper.java` | 文件上传/下载链路零指标（C8-006 零指标家族 file 子面） | 已复核·保留 |
| CL-012 | — | nop-gateway `ForwardProcessor.java:51-100` | 转发环无深度守卫（配置正确性形态） | 已复核·确认 |
| CL-013 | — | nop-gateway `GatewayHttpFilter.java:143-147,344-351` | 请求体全量物化无模块内上界（C5 形态） | 已复核·确认 |
| CL-014 | — | nop-biz `DownloadHelper.java:25-52` | 临时 zip 无配额、重启遗留（C6/C7 形态） | 已复核·确认 |
