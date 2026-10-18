# C4 线程池治理——请求热路径五模块 第 1 轮盲审报告

## 审计单元

| 项 | 值 |
|---|---|
| 审计单元 | nop-service-framework 请求热路径五模块（8 源码根，406 文件 / 42,748 行） |
| 源码根 | nop-graphql/nop-graphql-core、nop-graphql-orm、nop-graphql-message、nop-graphql-grpc、nop-biz、nop-gateway、nop-biz-auth-core、nop-biz-file-core |
| 维度 | C4——线程池与执行器治理（执行载体选择、全局池使用方式、自建池判定、池关闭责任、调度器治理） |
| 基线 | `_tmp/cra-baseline-nop-service-framework-hotpath.txt`（`== C4: thread creation and pools ==` 4 站点 + `== C4: scheduled/timer ==` 3 站点） |
| 方法论 | `ai-dev/skills/concurrency-resource-audit-prompt.md` 附录 A 13 字段 + F1–F11 缺陷族画像 |
| 日期 | 2026-10-18 |

## 盲审纪律声明

本轮为盲审轮次：**未读取** `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件（中途一次全仓库 grep 命中 `ai-dev/plans`/`ai-dev/audits` 文件内容后立即停止使用该内容，后续所有 grep 均显式排除 `ai-dev/`）。全部结论以当前 live code 为准。与既往轮次的关联仅使用任务下发时明示的三条移交线索：C3-007（`BizActionService.callActionAsync` 裸 `executor.execute`）、C3-011（`flushUserContextAsync` fire-and-forget）、CL-004（WS keepAlive 全局共享 timer 同步网络写已登记——本轮从池治理角度深化）。WI 2.1 设施本体缺陷（globalTimer 死配置、TaskQueue 弹性扩容、`destroy()` 无 awaitTermination）按纪律仅作 F3 互引，不重复立案。

## 检查范围说明

审计对象五模块自身**没有任何 `new Thread(...)` / `Executors.newXxx` 站点**（基线与全量 grep 双确认）。全部执行载体来自：平台全局池（`GlobalExecutors`）、自建平台池（`DefaultThreadPoolExecutor`）、JDK 全局设施（`CompletableFuture.delayedExecutor`）、IoC 按名解析的 bean 执行器、以及宿主线程池与完成线程。`nop-biz-file-core` 的 `ChunkFileUploadHandler` 为纯 stub（三方法全部 `throw UnsupportedOperationException`，无临时文件清理载体可审——该职责实际在模块外实现），相应检查项空转并如实登记。

### 请求执行载体清单（全景表）

| # | 载体 | 创建方式 / 参数 | 关闭责任 | daemon / 命名 | 使用方（本五模块内） | 判定 |
|---|------|----------------|---------|--------------|---------------------|------|
| 1 | HTTP 宿主 IO 线程（外部） | 宿主实现 | 宿主 | — | `GatewayHttpFilter.filterAsync` 入口、auth filter 公开路径、`getRequestBody().getTextAsync()` 发起 | 正常（filter 链入口不做重活，符合接口约定） |
| 2 | 宿主全局 worker 池（quarkus `ExecutorRecorder.getCurrent()` 主执行器 / 已阻塞线程内联） | 宿主实现（`QuarkusExecutorHelper.executeBlocking`:69-82） | 宿主 | — | `AuthHttpServerFilter._filterAsync:155` `executeBlocking` 包住 **整个下游请求链**（`next.get()` 在其内执行）；半衰期 token 同步刷新 | **无隔离**——auth 阻塞工作与全部请求处理共享同一池（→ C4-004） |
| 3 | `GlobalExecutors.globalTimer()`：DefaultScheduledExecutor | `GlobalExecutors.java:38-49`：daemon、`maxPoolSize=CFG_CONCURRENT_GLOBAL_TIMER_MAX_POOL_SIZE` **默认 5**（`CommonConfigs.java:22-23`）、核心 4/队列 10000（ThreadPoolConfig 默认） | 平台级永驻（设计如此） | daemon / `nop-global-timer` | ① `JsonRpcWebSocketHandler.java:82` keepAlive——**每 WS 连接 1 个固定延迟任务（10s/10s）**，定时器线程上同步网络写（→ C4-002）；② `DownloadHelper.java:39` 临时文件一次性延迟删除（→ C4-005）；③ `globalWorker()`（max 30，`nop-global-worker`）本五模块 **零直接使用** | 占用面登记完毕；timer 池 5 线程承载平台全部定时职责 |
| 4 | `nop-grpc-thread-pool`（自建 `DefaultThreadPoolExecutor`） | `GrpcServer.java:82` `DefaultThreadPoolExecutor.newExecutor(getThreadPoolConfig())`；未配置时 name=`nop-grpc-thread-pool`、daemon=true，其余取 ThreadPoolConfig 默认 core=4/max=20/queue=10000（`TaskQueue` Tomcat 式弹性扩容——WI 2.1 F3 互引） | `doStop():159-163` `ownExecutor` 时 `executor.destroy()`（=shutdown，无 awaitTermination——F3 互引）；注入 bean 时 ownExecutor=false 由 IoC 负责，职责划分正确 | daemon / `nop-grpc-thread-pool` | GrpcServer gRPC 请求执行（gRPC `builder.executor`） | 本五模块中治理最完整的自建池；残余项 → C4-006 |
| 5 | JDK `CompletableFuture.delayedExecutor`（JVM 全局单 daemon 线程 `CompletableFutureDelayScheduler`） | JDK 内部 | 无（JDK 内部，进程级） | daemon / JDK 固定名 | `InvokeProcessor.delayedFuture:198-202` 网关 URL 重试定时；**complete 后重试发起也在该线程执行**（→ C4-003） | 请求工作占用 JVM 全局共享单线程调度器 |
| 6 | IoC 按名解析执行器（`defaultWorkExecutor` / xbiz `executor` 属性） | bean 定义缺失（详见 C4-001） | IoC 容器生命周期（无泄漏） | 未定 | `BizActionService.callActionAsync:84-91` 非顺序 `execute` / 顺序 `executeForPartition` | **默认路径确定性失败**（→ C4-001） |
| 7 | 完成线程（CompletableFuture 回调线程，随前序完成点漂移） | 无创建 | — | — | GraphQL 游走回合（`thenCompose`/`thenFetchNext`）、gateway 拦截器链、AiFailover 重试发起 | 无独立池；执行位置=前序 future 完成线程 |
| 8 | java-dataloader dispatch | 无独立线程 | — | — | `GraphQLExecutionContext.dispatchAll:156-170` → `loader.dispatch()`；`OrmBatchLoader.load` 在 **dispatch 调用线程**（游走线程或完成线程）内联执行 | 批量加载与请求游走共享载体，无独立批处理池 |
| 9 | 订阅源发射线程（消息消费者 / 上游 publisher 线程，外部） | 外部 | 外部 | — | `GraphQLEngine.subscribeRpc` → `GraphQLSubscriptionPublisher`（纯透传）→ `JsonRpcWebSocketHandler.SubscriptionSubscriber.onNext:458-473` **在源发射线程同步 `session.sendMessage` 网络写**（并入 C4-002 失效面） | 同步网络写于外来载体（CL-004 同族扩展面） |

### 请求执行模型结论

GraphQL 引擎（`GraphQLEngine.executeRpcAsync/executeGraphQLAsync` → `SingleSessionFunctionInvoker.invokeAsync` → `OrmTemplateImpl.runInSessionAsync:234-241` 内联开 session → `GraphQLExecutor` 游走）**全程调用方线程驱动**：第一回合在 auth filter 的 `executeBlocking` worker（载体 2），后续回合在各自 future 的完成线程。`FutureHelper.futureCall`（`GraphQLExecutor.java:308` tryAction、`BeanMethodBatchFetcher.java:69`）为**内联包装**而非池提交（`FutureHelper.java:122-129`）。即：平台热路径五模块中，真正承载请求工作的池只有宿主全局 worker 池与 gRPC 自建池两个；`defaultWorkExecutor` 按设计应承担的"biz action 工作池"角色在当前仓库中缺位。

---

## 发现清单

### [nop-service-framework-hotpath-C4-001] callActionAsync 的执行器按名字符串解析，默认 bean `defaultWorkExecutor` 全仓库无定义——默认动作执行路径 100% 确定性失败，executor 声明无启动期校验、bizSequential 强转无类型守卫

- **文件**: `nop-service-framework/nop-biz/src/main/java/io/nop/biz/service/BizActionService.java:70, 82-94, 108-113`；`nop-service-framework/nop-biz/src/main/java/io/nop/biz/BizConstants.java:40`
- **证据片段**:
  ```java
  // BizActionService.callActionAsync
  String executorName = getWorkExecutorBean(actionModel);   // null → "defaultWorkExecutor"
  ...
  if (actionModel.isBizSequential()) {
      int hash = bizHashFunction.getBizHash(request);
      IPartitionedExecutor executor = (IPartitionedExecutor) beanContainer.getBean(executorName);
      executor.executeForPartition(hash, task);
  } else {
      Executor executor = (Executor) beanContainer.getBean(executorName);
      executor.execute(task);
  }
  ```
  ```java
  // BizConstants.java:40
  String DEFAULT_WORK_EXECUTOR_NAME = "defaultWorkExecutor";
  ```
- **严重程度**: P2
- **并发场景**: 任何经 `IBizActionService.callActionAsync`（nop-api-core 公共接口，本模块为唯一实现）派发的异步 biz action；执行器 bean 由 IoC 容器在**每请求**时按名解析。
- **触发时序**: 非竞态、确定性失败链：T1 应用以默认配置启动（未自定义任何名为 `defaultWorkExecutor` 的 bean）→ T2 请求进入 `callActionAsync`，actionModel 无 `executor` 属性 → `getBean("defaultWorkExecutor")` → `BeanContainerImpl.getBean:294-299` 查 `enabledBeans` 未命中且无 parentContainer → 抛 `ApiErrors.ERR_IOC_UNKNOWN_BEAN_FOR_NAME` → 被 `catch (Throwable)` 捕获 → `future.completeExceptionally` → **每次调用必失败**。顺序分支同理，且多一层 `(IPartitionedExecutor)` 强转：action 声明了 `biz-sequential` 但同名 bean 是普通 `Executor` 时，每请求 ClassCastException。另：`executor` 属性为 xbiz 字符串，拼写错误同样只在**首个请求**时暴露，无启动期 fail-fast。
- **现状**: 全仓库检索（所有 `*.beans.xml`、`*.beans`、Java、yaml/json，排除 `_gen`/target）**无任何 id 为 `defaultWorkExecutor` 的 bean 定义**；`IBizActionService` 在仓库内也无产品代码调用方（仅接口定义与本实现 + 单测）。因此这是"潜伏的公共 API 契约"：接口在 nop-api-core（Protected Area，仅对照），实现在热路径模块，默认装配缺失。
- **风险**: C3-007 移交的"该 executor 的身份"问题就此闭环：**身份未定义、生命周期归 IoC、参数无默认**。任何下游（外部 RPC 分发器、未来引擎接线）一旦按接口装配，默认动作全失败；顺序执行强转 CCE 每请求抛出；执行器参数（core/max/队列/拒绝策略）完全由应用自行提供，平台无推荐配置、无命名约定校验。
- **建议**: 在 `biz-defaults.beans.xml` 提供默认 `defaultWorkExecutor`（`DefaultThreadPoolExecutor` + 显式 ThreadPoolConfig：命名 `nop-biz-worker`、daemon、有界队列、Abort 拒绝），或启动期（`BizObjectManager.delayInit`）扫描全部 actionModel 的 `executor` 名并 `tryGetBean` 预检，缺失即启动失败并点名 action；`isBizSequential` 时校验 bean 类型为 `IPartitionedExecutor` 并在启动期报错。
- **失效面枚举**（F3）: ① 非顺序分支 `executor.execute`（:90）——默认 bean 缺失，**未修**；② 顺序分支 `executeForPartition`（:86-87）——默认 bean 缺失 + 强转无守卫，**未修**；③ `getWorkExecutorBean`（:108-113）——null 回退名缺失，**未修**；④ 全部 xbiz `executor` 属性声明点——无启动期校验，**未修**；⑤ `nop-biz/src/test/java/io/nop/biz/service/TestBizActionService.java`——只测 action 缺失分支，未测 executor bean 缺失分支（测试代码不构成生产守卫），**无保护力**；⑥ 同族对照：`GraphQLEngine` 的 invoker 接线全部走 `ioc:optional` ref（`biz-defaults.beans.xml:11-16`）——存在即注入、缺失即 null 回退，是同类问题的正确形态，可复用其模式。
- **信心水平**: 确定
- **误报排除**: 不属于"有界声明存在的池"误报（根本没有池）；不属于低频路径（callActionAsync 是设计的异步动作主入口）；bean 缺失是装配事实而非理论竞态。
- **复核状态**: 复核通过，维持 P2（2026-10-18 维度复核）——证据逐项亲核成立：`BizActionService.java:70,82-94,108-113` 双分支与 `catch(Throwable)`、`BizConstants.java:40` 常量、全仓 grep `defaultWorkExecutor` 仅该常量一处（无任何 bean 定义）、xbiz `executor=`/`biz-sequential` 实测 0/717、`callActionAsync` 生产调用方 0（仅接口+实现+2 测试）、`BeanContainerImpl.java:255,268,299` 抛 `ERR_IOC_UNKNOWN_BEAN_FOR_NAME`、`xbiz.xdef` 文档「bean id=executor+"Executor"后缀」与实现原样查找的背离成立。前置条件（callActionAsync 被接线）现状为零调用方，P2 恰当。现状=未修。

---

### [nop-service-framework-hotpath-C4-002] 每 WS 连接的 keepAlive 定时任务挂在平台全局共享 5 线程定时器上，定时器线程执行同步网络写；取消依赖 onClose/onThrowable 回调成对，构造后接线失败路径任务永驻（CL-004 池治理深化，同族互引）

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/ws/JsonRpcWebSocketHandler.java:82-84, 112-117, 126-128, 357-363`；载体参数 `nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/GlobalExecutors.java:38-49`、`nop-kernel/nop-commons/src/main/java/io/nop/commons/CommonConfigs.java:22-23`
- **证据片段**:
  ```java
  // JsonRpcWebSocketHandler 构造器（每连接一次，onOpen 时创建）
  this.keepAliveSender = GlobalExecutors.globalTimer().scheduleWithFixedDelay(this::sendKeepAlive, 10, 10,
          TimeUnit.SECONDS);
  ...
  private void sendKeepAlive() {
      try {
          session.sendMessage(createNotification("ping"));   // 定时器线程上的同步网络写
      } catch (IOException e) {
          LOG.warn("nop.websocket.send-keep-alive-fail", e);
      }
  }
  ```
- **严重程度**: P2（与已登记的 CL-004 同族互引；本轮从治理角度补量化与取消不对称，复核轮可据 CL-004 原判级合并升降）
- **并发场景**: N 个 WebSocket 连接 × 每连接 1 个 `scheduleWithFixedDelay` 任务，全部落在平台唯一的 `nop-global-timer`（daemon，**maxPoolSize 默认 5**，同时承载全平台定时职责）；与 DownloadHelper 清理任务（C4-005）及其他模块 globalTimer 用户共享。
- **触发时序**: ①（占用）T1 客户端 TCP 接收窗口收缩/半开连接 → T2 timer 线程执行 `session.sendMessage` 阻塞于底层写（写超时由会话实现保证与否不属本模块控制）→ T3 该连接的写占据 5 个 timer 线程之一可达秒级到分钟级；连接数上升时按概率叠加 → 平台全部定时任务（含其他模块）被推迟。②（取消不对称，F8）T1 `JsonRpcWebSocketEndpoint.onOpen` 先 `new JsonRpcWebSocketHandler`（**定时任务即已启动**）→ T2 后续接线（`BeanContainer.getBeanByType(IUserContextExtractor)` 或 `setUserContextExtractor` 抛出/断连）发生在 `session.getUserProperties().put("graphql.handler", handler)` 之前 → T3 会话关闭时 endpoint `onClose:132-137` 取 handler 得 null → `keepAliveSender.cancel` 落空 → 该 10s 周期任务**永驻**全局定时器，每周期执行一次失败发送 + warn 日志。
- **现状**: keepAlive 的网络写在全局共享定时器线程上同步执行（CL-004 已登记的形态）；本轮新增：载体参数量化（5 线程默认值 + 每连接一任务的增长模型）与生命周期不对称（构造即启动、取消仅两个回调入口、handler 注册晚于任务启动）。
- **风险**: 长时运行下泄漏任务数单调增长（每次接线失败 +1，无上限）；海量连接场景 5 线程定时器被周期性网络写+慢写占满，全局定时职责（文件清理、其他模块任务）延迟雪崩。连接数 N 与周期 10s 的组合：timer 任务基数=N，任一时刻活跃写任务≈阻塞概率×N/5。
- **建议**: ① keepAlive 写改异步/带写超时的发送 API，或移到 `globalWorker`/会话自身的写队列；② 生命周期对称：handler 注册进 userProperties 后再启动定时任务，或提供无回调兜底（`session` 关闭状态探测 + 任务自取消）；③ 每连接任务改为连接级单一定时器聚合（如平台级心跳管理器按连接注册表统一调度）。
- **失效面枚举**（F3）: 同文件 `session.sendMessage` 同步写全站点：① `sendKeepAlive:359`（timer 线程）——**本案**；② `SubscriptionSubscriber.onNext:464` / `onComplete:489`（订阅源发射线程，外来载体）——**同族未修**；③ `handlePing:167/169`、`handleUnsubscribe:210`、`handleTokenRefresh:255`（WS 容器消息线程——其原生线程，可接受）；④ `sendError:380` / `sendErrorFromGraphQL:384`（调用线程各异）——**同族未修**；⑤ `DownloadHelper.java:39`（globalTimer 同池其他用户，见 C4-005）；⑥ 取消路径：`onThrowable:114-116`、`onClose:126-128` 成对存在，但依赖 endpoint `userProperties` 接线成功——**接线失败路径未修**。
- **信心水平**: 确定（载体与生命周期事实）；占用后果为很可能（依赖会话实现写超时语义）
- **误报排除**: 非"低频路径粗粒度锁"类误报——这是每连接常驻任务+共享池阻塞的结构性占用；非平台设施本体重审——globalTimer 5 线程默认值仅作占用面参数引用（WI 2.1 F3 互引），发现本体是使用方载体选择与生命周期。
- **复核状态**: 复核通过，维持 P2（2026-10-18 维度复核）——证据逐项亲核成立：构造器 `scheduleWithFixedDelay(sendKeepAlive,10,10,s)` 构造即启动、`sendKeepAlive` 定时器线程同步 `session.sendMessage`、`GlobalExecutors.java:38-49` + `CommonConfigs.java:22-23` 默认 maxPoolSize=5、取消成对（`onThrowable`/`onClose`）、endpoint `onOpen` 中 `new JsonRpcWebSocketHandler`（:74，任务即启动）到 `userProperties.put`（:82）之间存在可抛异常的接线段（`getBeanByType`/`setUserContextExtractor`→`bindUserIdentityIfNeeded`），而 `onClose:132-137`/`onError:140-144` 按 userProperties 取 handler 得 null 即漏取消——生命周期不对称成立（窗口窄但真实）。与 C8-007 聚合面互引维持。现状=未修。

---

### [nop-service-framework-hotpath-C4-003] 网关 URL 转发重试链把重试定时与重试发起都放在 JDK 全局单线程 Delayer 上，重试不观测客户端断连取消，双层重试叠加单请求最多 16 次上游调用、挂起可达约 90 秒

- **文件**: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/executor/InvokeProcessor.java:135-137, 149-167, 198-202, 208`；叠加层 `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/interceptor/AiFailoverGatewayInterceptor.java:64-70, 100-121`；入口 `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/http/GatewayHttpFilter.java:145-155`
- **证据片段**:
  ```java
  // InvokeProcessor
  int maxRetries = 3;
  return invokeUrlWithRetry(url, request, invoke, svcCtx, maxRetries);
  ...
  return httpClient.fetchAsync(httpRequest, null).thenCompose(httpResponse -> {
      if (httpStatus == 429 && retriesLeft > 0) {
          long delayMs = parseRetryAfter(retryAfter, retriesLeft);      // 上限 MAX_RETRY_DELAY_MS=30_000
          return delayedFuture(delayMs).thenCompose(v ->
                  invokeUrlWithRetry(url, request, invoke, svcCtx, retriesLeft - 1));
      }
  ...
  private CompletionStage<Void> delayedFuture(long delayMs) {
      CompletableFuture<Void> future = new CompletableFuture<>();
      CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS).execute(() -> future.complete(null));
      return future;
  }
  ```
- **严重程度**: P1
- **并发场景**: 每个经网关 URL 转发的请求在 429/5xx 时由 JDK `CompletableFuture.delayedExecutor`（**JVM 全局共享单 daemon 线程** `CompletableFutureDelayScheduler`）完成延迟后，`thenCompose` 的重试发起（`buildHttpRequest` + `httpClient.fetchAsync`）在 **Delayer 线程**上执行；与外层 `AiFailoverGatewayInterceptor`（fallback ≤ `min(fallbackUrls, maxRetries=3)`）形成双层乘法。
- **触发时序**: ①（幽灵重试）T1 客户端发起网关请求，上游返回 429 → T2 请求挂入 Delayer 等待 `Retry-After`（≤30s）→ T3 客户端断开连接（`GatewayHttpFilter` 仅对流式响应 `abort()`，:239-241；普通 invoke 链无 cancel 挂钩）→ T4 重试链照常触发，对已无人接收的请求继续发起至多 3 次上游调用，全程占用 HTTP 客户端连接与请求 future。②（占用+放大）T1 上游故障返回 5xx，QPS=R 的请求全部进入重试 → T2 每请求 5xx 退避 2-3s / 4-5s / 8-9s（jitter ±1s）或 429 时最长 30s×3 → T3 每请求挂起 14-22s（5xx）至 ~90s（429 满额），在途请求与 Delayer 队列条目按 R×挂起时长积累；再叠 failover 层，单请求上游调用次数最多 (1+3)×(1+3)=**16 次**，故障上游被放大 4-16 倍。
- **现状**: 重试定时不使用平台 `globalTimer`/`IScheduledExecutor` 而用 JDK 全局 Delayer（无指标、无上限治理、进程级共享）；重试发起在 Delayer 单线程上执行请求构造与 fetch 调用（发起链包含 URL 表达式求值 `invoke.getUrl().invoke(svcCtx.getEvalScope())`，XPL 求值代价进入全局单线程）；客户端取消不传播进重试链。
- **风险**: 上游 429 风暴（AI 网关的常态故障模式）时，全局单线程 Delayer 队列与在途 future 线性堆积；重试放大拖垮本可自愈的上游；客户端已放弃的请求持续消耗出口连接与带宽。
- **建议**: ① 重试前检查 `IGatewayContext`/请求 future 的取消状态（挂接 client abort → `future.cancel` → `thenCompose` 短路）；② 延迟载体改平台 `globalTimer().schedule`（可观测、池大小受控）或专用有界重试调度器；③ 重试发起移出 Delayer 线程（`delayedFuture` 完成后 `thenComposeAsync(fn, globalWorker())`）；④ 双层重试建立协调（failover 层感知内层已重试次数，乘法改加法）。
- **失效面枚举**（F3）: ① `InvokeProcessor.invokeUrlWithRetry` 429 分支（:153-159）——**本案未修**；② 5xx 分支（:162-167）——**同族未修**（同 delayedFuture）；③ `AiFailoverGatewayInterceptor.tryInvokeWithFallback/tryFallbackUrl`（:100-156）——即时重试（完成线程发起，不经 Delayer），有 maxRetries 上界，但同样**不观测客户端取消**，同族未修；④ `BufferedStreamingPublisher.handleError→startAttempt`（:298-326）——流式窗口重订阅，在上游 onError 线程即时发起，次数仅受 `retryCallback` 策略约束（代码无上限），同族未修；⑤ 对照组：`TransactionActionDecorator.java:42`、`IFileStore.java:16` 等 `FutureHelper.futureCall` 为内联包装，无重试，不涉案。
- **信心水平**: 确定（载体、取消缺失、叠加倍数均为代码事实；Delayer 单线程为 JDK 公开实现语义）
- **误报排除**: 非过载理论推演——重试次数、退避公式、上界常量全部为代码常量；客户端断连不取消是 `GatewayHttpFilter` 中 abort 仅覆盖 streaming 路径的直接代码事实。
- **复核状态**: 复核裁定 **P1 → P2（降级）**，发现本体保留（2026-10-18 维度复核，独立复核 ×7 加法与 Delayer 共享面后终裁）——①存活部分亲核成立：`invokeUrl:135` maxRetries=3 → 主 URL 至多 4 次 fetch；`AiFailoverGatewayInterceptor` fallback 直连 `httpClient.fetchAsync`、每 URL 恰 1 次、上界 `min(|fallbackUrls|,3)`、绕过 InvokeProcessor——**4+3=7 加法语义成立**；90s（429 带 Retry-After，`MAX_RETRY_DELAY_MS=30_000` 钳制 ×3）与 5xx 挂起 7-10s 成立；取消传播缺失成立（`GatewayHttpFilter` 全文仅 :239-241 流式 `abort()`）。②**降级事由（复核勘误 8）**：本项目 `java.version=25`/`release=25`（pom.xml:25-30），JDK 25 实测（25.0.1 src.zip）`CompletableFuture.delayedExecutor`/`orTimeout` 已改走 `ForkJoinPool.asyncCommonPool()` 的 `java.util.concurrent.DelayScheduler`——该单线程**仅做定时到点转交（relay）**，`future.complete` 与 `thenCompose` 重试发起（`buildHttpRequest`+`fetchAsync` 调起）均在 common pool **多线程**工作线程上执行：P1 的判定支柱「全局单线程被重试发起重活占用、直接推迟其他 orTimeout 站点超时判定」在目标运行时上**被证伪**（第 1 轮信心水平所依「Delayer 单线程为 JDK 公开实现语义」系 JDK≤23 语义）。③存活的「幽灵重试」腿与 C3-010 同缺陷本体（互为前置耦合对已裁定），重复计分收归 C3-010；存活危害=常见故障下的过载放大（有界挂起+7×上游调用放大+出口连接/内存堆积），不满足本 campaign P1 bar（高 QPS 下确定性 OOM/不可逆/服务级停摆——请求处理线程全程异步释放，网关自身不停摆）。④附复核勘误 9：上游 fetch 未设 per-request 超时属实，但默认装配 `HttpClientConfig` readTimeout=30s/connectTimeout=10s（`HttpClientConfig.java:35-37`）使每次调用默认有界，第 2 轮勘误 6 的「无界」表述补全。修复方向四项（取消检查、平台定时器/专用调度器、发起移出调度线程、双层重试协调）全部维持有效。现状=未修。详见文末「维度复核结论」。

---

### [nop-service-framework-hotpath-C4-004] 认证过滤器把整个下游请求链接在宿主全局 worker 池的 executeBlocking 载体上执行，token 半衰期同步刷新与 flushUserContextAsync 回写共享同一无隔离载体（含 C3-011 移交的载体判定）

- **文件**: `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/filter/AuthHttpServerFilter.java:70-75, 154-191, 342-384`；载体实现（对照，越界仅引用）`nop-quarkus/nop-quarkus-web/src/main/java/io/nop/quarkus/web/utils/QuarkusExecutorHelper.java:69-82`；回写链 `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/login/AbstractLoginService.java:50-61`、`AbstractUserContextCache.java:127-132`
- **证据片段**:
  ```java
  // AuthHttpServerFilter._filterAsync —— "耗时的操作不能在IO线程上执行"
  return routeContext.executeBlocking(ctx.wrapWithContext(() -> {
      ...
      return getUserContextAsync(authToken, routeContext)
              .thenCompose(userContext -> { ... return handleUserContext(userContext, routeContext, next, authToken); })
      ...
  })).thenApply(v -> null);
  ...
  // handleUserContext —— next.get()（整个下游请求链）在 runOnContext 内执行
  ctx.runOnContext(() -> {
      if (needRefresh) {
          String accessToken = loginService.refreshToken(userContext, authToken);   // 同步刷新
          ...
      }
      CompletionStage<Void> promise = next.get().whenComplete((v, e) -> {
          loginService.flushUserContextAsync(userContext);                          // fire-and-forget（C3-011）
      });
      FutureHelper.bindResult(promise, future);
  });
  ```
- **严重程度**: P2
- **并发场景**: 每个非公开请求的过滤器链在 `executeBlocking` 载体上执行：宿主 IO 线程时调度到 **quarkus 主执行器（宿主全局 worker 池，与全部请求处理共享）**，已在 worker 线程时内联执行（`QuarkusExecutorHelper.executeBlocking:69-82`）；`BaseContext.runOnContext`（`nop-api-core/context/BaseContext.java:228-236`）为**当前线程内联**执行（任务队列 enqueue 后立即 flush），无线程切换。
- **触发时序**: ①（同步刷新占载体）T1 请求 token 寿命过半（`isNeedRefresh:320-329`：`timeToLive < expireSeconds*500L`，`autoRefreshToken` **默认 true**）→ T2 worker 线程进入 `runOnContext` 内联块，同步调用 `loginService.refreshToken`（无超时包装、无专用调度器；实现为远程 IdP 调用时耗时=RTT）→ T3 同一 worker 线程继续执行 `next.get()` 即整个下游 GraphQL/biz 请求。并发 128 个半衰期请求同时慢刷新（quarkus 默认 worker ~128）即占满全局 worker 池，所有请求（含无需刷新者）排队饿死。②（C3-011 载体判定）`flushUserContextAsync` 在请求完成回调线程执行 → `AbstractUserContextCache.saveUserContextAsync:128-132` → `ICache.putAsync`——LocalCache 实现在调用线程内联完成，Redis 实现走其异步 I/O 线程；无专用 flush 执行器、并发度随请求速率无界（背压缺失面归 C8，本条登记载体归属）。
- **现状**: 平台注释明言"耗时的操作不能在IO线程上执行"，但卸载目标=宿主全局 worker 池，auth 域无独立隔离池；刷新是同步调用且与下游请求串联在同一载体上；`flushUserContextAsync` 的返回 CompletionStage 被丢弃（失败不可见——C3-011 本体），其载体为请求完成线程/缓存实现线程。
- **风险**: 认证域慢操作（刷新、会话缓存 I/O）与业务请求互相拖累，无故障隔离；远程刷新实现接入后，半衰期流量即全池阻塞；停机时在途刷新无统一取消/等待点。
- **建议**: ① token 刷新改异步（`refreshTokenAsync` + 超时），或移入带界的专用认证执行器 bean（`DefaultThreadPoolExecutor` + ThreadPoolConfig，命名 `nop-auth-worker`）；② 下游 `next.get()` 移出 executeBlocking callable（仅用户上下文加载留在 blocking 段），恢复"过滤器只做轻活"的载体分工；③ flush 回写按用户聚合去抖（同会话多请求合并为一次 putAsync）。
- **失效面枚举**（F3）: ① `_filterAsync` executeBlocking 包裹整链（:155-191）——**本案未修**；② `handleUserContext` 同步 `refreshToken`（:357-366）——**未修**；③ `processOAuthCode→loginWithOAuthCode→loginAsync`（:196, 332-340）——异步形态正确（CompletionStage），**不涉案**；④ `next.get().whenComplete` 内 `flushUserContextAsync`（:374-376）——载体如上，失败不可见归 C3-011，**载体面本案登记**；⑤ `getUserContextAsync`（:434-438）→ `AbstractLoginService.doGetUserContext:77-86` → cache `getAsync`——同 flush 载体（调用线程/缓存实现线程），**同族登记**；⑥ `handlePublicPath`（:292-294）纯透传 `next.get()`——不经 executeBlocking，**形态正确**。
- **信心水平**: 确定（载体事实）；全池饿死后果为很可能（依赖 refreshToken 实现代价，仓库内无远程实现——降档理由已计入判级）
- **误报排除**: 不按"executeBlocking 用法"报宿主实现缺陷（宿主越界仅对照）；非误报的关键在于 `next.get()`（下游请求链）也在 blocking 段内，超出"卸载耗时操作"的原意，属执行载体选择缺陷而非设施误用误报。
- **复核状态**: 复核通过，维持 P2（2026-10-18 维度复核）——证据逐项亲核成立：`_filterAsync:155-191` executeBlocking 包住含 `next.get()` 的整链、`QuarkusExecutorHelper.executeBlocking:69-82` 阻塞线程内联/IO 线程转 `ExecutorRecorder.getCurrent().execute`、`BaseContext.runOnContext`（`BaseContext.java:228-236`）无 sync 等待者时当前线程 `flush()` 内联执行、`flushUserContextAsync` 链（`AbstractLoginService.java:50-61`）。第 2 轮勘误 4（quarkus 池参数）与勘误 5（远程刷新实现在仓 → C4-007 立案）复核确认。本体为载体选择缺陷，与 C4-007（实现层同步阻塞远程调用）F3 互引不重复。现状=未修。

---

### [nop-service-framework-hotpath-C4-005] 下载临时文件清理任务一次性挂在全局共享定时器上，删除失败被调度器静默吞掉且无兜底扫描——分钟级延迟任务驻留定时器队列，失败即文件永驻

- **文件**: `nop-service-framework/nop-biz/src/main/java/io/nop/biz/download/DownloadHelper.java:39-42`
- **证据片段**:
  ```java
  WebContentBean content = new WebContentBean("application/zip",
          resource.toFile(), fileName);

  GlobalExecutors.globalTimer().schedule(() -> {
      resource.delete();
      return null;
  }, waitMinutes, TimeUnit.MINUTES);
  IoHelper.safeCloseObject(zipOutput);
  return content;
  ```
- **严重程度**: P2
- **并发场景**: 每次下载导出在平台全局 `nop-global-timer`（max 5 线程，与 C4-002 全部用户共享）的延迟队列挂一个一次性任务，驻留 `waitMinutes` 分钟后执行 `resource.delete()` 文件删除 I/O。
- **触发时序**: T1 下载完成，清理任务入队（持有 `IResource`/File 引用，驻留分钟级）→ T2 `waitMinutes` 后某 timer 线程执行 `resource.delete()` → T3a 删除成功——正常；T3b 文件被占用/删除抛异常（`Callable` 异常被调度器捕获吞掉，无重试无日志无指标）→ 文件**永驻** temp 目录，进程不重启不清除。写不出多线程交错（单任务顺序执行），故按"失败路径资源泄漏 + 静默吞异常（F9）"立 P2，非竞态类。
- **现状**: 清理正确性与全局定时器强耦合：① 失败静默（一次性任务异常后无任何感知路径，对照 C4-002 的周期任务至少每 10s 重试一次）；② 无兜底（无启动期/周期性 temp 目录扫描对账）；③ 任务对象驻留延迟队列分钟级（`waitMinutes` 由调用方传入，本模块内无调用点，公共 helper——上游常用值即驻留时长）。
- **风险**: 长时运行大下载量部署 temp 目录单调增长（F4 同族：只增不删账本的文件版）；删除 I/O 与 WS keepAlive 共享 5 线程定时器（同池占用面）。
- **建议**: ① 删除任务包装 try/catch + 失败记录（error 日志或指标）+ 短退避重试一次；② 增加平台级 temp 目录周期对账扫描（按文件 mtime 清理超期文件），使单次任务失败可自愈；③ 与 C4-002 建议的连接级聚合一并规划 globalTimer 的用户治理。
- **失效面枚举**（F3）: ① `DownloadHelper.downloadZip` 两处重载（:18-23 委托 :25-52 单一实现）——**本案**；② grep 五模块 `globalTimer().schedule` 全站点仅此一处 cleanup 形态（另两处为 C4-002 keepAlive 与 C4-003 的 JDK Delayer）；③ 同族对照：`IFileStore.saveFileAsync`（nop-biz-file-core）无清理职责，不涉案；④ `ChunkFileUploadHandler` 为 stub 无清理载体（检查项空转，如实登记）。
- **信心水平**: 确定
- **误报排除**: 非"临时文件清理钩子缺失"的 C6 重复计数——清理钩子存在，缺陷在失败不可见与无兜底（F9/F4 形态、调度治理载体归属 C4）；非平台设施本体重审（调度器吞异常语义 F3 互引 nop-kernel）。
- **复核状态**: 复核通过，维持 P2（2026-10-18 维度复核）——证据逐项亲核成立：`DownloadHelper.java:39-42` 定时删除任务 boolean 返回值忽略、`ScheduledFuture` 丢弃、`:49` catch 路径 `resource.delete()` 同样忽略 boolean（第 3 轮新增面确认）、`FileResource.java:91-92` 对照存在；全仓 `deleteOnExit` 仅散布于资源实现（`IFile.java:72`/`FileResource.java:91`），未检索到平台级 temp 对账机制——「无兜底」面成立。与第 2 轮 CL-007 裁定（boolean 主面 + 异常次面 + 对账兜底三件绑定同一修复单元）及 C3-CL-006（重启遗留）三方绑定关系复核确认。现状=未修。

---

### [nop-service-framework-hotpath-C4-006] GrpcServer 自建池治理基本合格（自建即自毁、命名、daemon），残余：destroy 无 awaitTermination 致停机在途任务无界期；默认 10000 深队列使排队延迟先于背压显形

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-grpc/src/main/java/io/nop/graphql/grpc/server/GrpcServer.java:81-85, 134-146, 148-164`；载体（F3 互引）`nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/DefaultThreadPoolExecutor.java:91-93`、`ThreadPoolConfig.java:16-31`
- **证据片段**:
  ```java
  if (executor == null) {
      executor = DefaultThreadPoolExecutor.newExecutor(getThreadPoolConfig());   // 未配置时 name=nop-grpc-thread-pool, daemon=true
      ownExecutor = true;
      builder.executor(executor);
  }
  ...
  protected void doStop() {
      if (server != null) { server.shutdownNow(); server = null; }
      if (executor != null && ownExecutor) {
          executor.destroy();      // DefaultThreadPoolExecutor.destroy = executor.shutdown()，无 awaitTermination
          executor = null; ownExecutor = false;
      }
  }
  ```
- **严重程度**: P3
- **并发场景**: gRPC 请求处理线程池：core=4 / max=20 / queue=10000（TaskQueue Tomcat 式弹性扩容——WI 2.1 F3 互引）/ Abort 拒绝，daemon、命名完整；`GrpcServer` 生命周期为 `LifeCycleSupport`，`ownExecutor` 标记区分自建与注入。
- **触发时序**: ①（停机）T1 `doStop` 调 `server.shutdownNow()`（在途 RPC 被取消）→ T2 立即 `executor.destroy()`=`shutdown()`：已入队任务仍会执行完，`doStop` 不等待 → 容器声明停止后池线程可能仍在跑残余任务，时长无上界（正常路径秒级；任务含慢 I/O 时放大）。②（排队）T1 上游持续超过 20 线程消化能力 → T2 弹性队列积压直至 10000 → T3 之后新请求才被 Abort 拒绝——背压显形前每请求已承受深队列排队延迟。
- **现状**: 本五模块中唯一的自建池，关闭责任归属正确（自建自毁 / 注入归 IoC，`ownExecutor` 双态正确），命名 daemon 完备；残余两点均为治理精度问题而非正确性缺陷。
- **风险**: 优雅停机语义弱化（停机完成声明与实际静止不一致，影响滚动发布的连接切换判定）；深队列使 gRPC 客户端超时先于服务端拒绝触发，故障表现为超时雪崩而非快速失败。
- **建议**: `doStop` 在 `destroy()` 后加有界 `awaitTermination`（如 5-10s，超时 `shutdownNow` 并记录未完成任务数）；为 gRPC 场景提供更浅的默认队列（如 200-1000）或文档声明队列容量与超时的匹配关系。
- **失效面枚举**（F3）: ① `GrpcServer.doStop` 自建分支（:159-163）——**本案**；② `GrpcServer` 注入分支（`setExecutor:42-44`）——destroy 责任归容器，**职责划分正确不涉案**；③ `DefaultThreadPoolExecutor.destroy` 全平台调用面（nop-kernel F3 互引，WI 2.1 范畴）；④ `ThreadPoolConfig` 默认参数全平台使用面（同 F3 互引）。
- **信心水平**: 确定
- **误报排除**: 不按"自建池"一票否决——该池满足有界队列+拒绝策略+命名+关闭责任四项基本治理，判 P3 仅登记残余；`destroy()` 无 await 属 nop-kernel 设施本体（F3 互引不立案本体），本条立案的是 `GrpcServer` 使用侧未做停机等待补偿。
- **复核状态**: 复核通过，维持 P3（2026-10-18 维度复核）——证据逐项亲核成立：`GrpcServer.java:81-85` 自建分支 + `ownExecutor` 双态、`doStop:152-163` 先 `server.shutdownNow()` 后 `executor.destroy()`、`DefaultThreadPoolExecutor.destroy:91-93` = `executor.shutdown()` 无 awaitTermination、`ThreadPoolConfig.java:17-25` 默认 core=4/max=20/queue=10000、未配置时命名 `nop-grpc-thread-pool`+daemon。两点残余（停机无等待、深队列先排队后背压）均为治理精度问题，P3 恰当。现状=未修。

---

## 维度集外线索

### [nop-service-framework-hotpath-CL-010]（维度集外线索，不立案展开）流式响应每个元素的 JSON 序列化在上游 HTTP 客户端事件循环线程上执行

- 形态归属：C9（并发高性能——共享 IO 载体上的热路径重活）。
- 证据：`nop-gateway/src/main/java/io/nop/gateway/http/GatewayHttpFilter.java:200-234, 246-260`——`writeStreamingResponse` 的 publisher 链中 `onNext → serializeStreamElement → JsonTool.serialize(item, false)` 在**上游 SSE publisher 的发射线程**（即 HTTP 客户端实现的事件循环线程）执行；`StreamingProcessor.createMappedPublisher:148-166` 的 `JSON.parse`/拦截器 `proceedOnStreamElement` 同在该线程。每个 AI 流式 chunk 一次序列化+解析，代价随 chunk 率线性占用共享事件循环，慢序列化直接拖慢同事件循环上的其他连接的收发。
- 量化口径建议：chunk 率 × 单次 serialize 微秒级成本 × 活跃流数；建议 JMH 基准形态：JsonTool.serialize 对典型 SSE chunk bean 的单次耗时。
- 与 C4 关系：非池治理缺陷（无池可选），不立案。

### [nop-service-framework-hotpath-CL-011]（维度集外线索，不立案展开）流量控制以"在请求载体线程上阻塞等待许可"实现（默认 maxWaitMillis=0 不阻塞，配置化阻塞）

- 形态归属：C8/C9（背压实现方式与热路径阻塞）。
- 证据：`nop-rpc/nop-rpc-core/src/main/java/io/nop/rpc/core/flowcontrol/DefaultFlowControlRunner.java:36-48`——`rateLimiter.tryAcquire(1, maxWaitMillis)` 同步等待；使用方 `nop-graphql-core/engine/FlowControlFetcher.java:32`、`GraphQLFlowControlInvoker.java:33` 将其挂接进 GraphQL fetch 链。`maxWaitMillis>0` 时，被限流请求的**宿主 worker 线程**被占满等待时长（与 C4-004 的全局 worker 无隔离面叠加）；默认 0 = 立即拒绝，无此问题。
- 与 C4 关系：执行载体未被滥用（默认路径干净），阻塞是配置化选择而非治理缺陷，登记线索供 C8/C9 维度取用。

---

## 总计

| 类别 | 数量 | 明细 |
|---|---|---|
| C4 立案发现 | 6 | P0×0，P1×1（C4-003），P2×4（C4-001/002/004/005），P3×1（C4-006） |
| 维度集外线索 | 2 | CL-010（C9 流式序列化占事件循环）、CL-011（C8/C9 限流阻塞载体） |
| 载体清单站点 | 9 | 全景表 9 类载体；`globalWorker` 本五模块零直接使用、`ChunkFileUploadHandler` 清理载体空转（stub）已如实登记 |

判级校准自查：无 P0（未发现"默认用法+常见条件即触发且不可逆"形态——C4-003 的 P1 挂钩"错误/取消路径幽灵任务 + 负载上升必然放大"，触发条件为上游 429/5xx 属网关常态故障）。治理类发现均给出配置组合或过载数字（C4-001 bean 缺失+全站 grep 证明、C4-002 maxPoolSize=5×每连接任务、C4-003 16 次调用/90s 挂起、C4-004 半衰期默认 true×128 worker、C4-005 分钟级队列驻留、C4-006 4/20/10000 参数组）。每条发现含 F3 失效面枚举；测试代码保护力缺口已在 C4-001⑤ 登记。

---

## 维度复核结论

复核人：C4 维度独立复核子代理（与初审三轮互不交叉）。复核基点：仓库 `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`，分支 `concurrency-audit-remediation`；对照面按复核阶段口径开放（cra-index、plan 2470 判级口径、C8 轮 CL-011 终判）。方法：全部 8 条发现 + 6 条 CL 逐条亲开 live code 核证；对 C4-003/C4-007 按特别复核指令独立执行算术与机制终裁；JDK 语义以项目工具链实测为准（本机 JDK 25.0.1 src.zip + Zulu 26.0.1 双版本比对）。

### 逐条裁定汇总表

| 发现 | 初审/深挖判级 | 复核裁定 | 裁定要点 |
|---|---|---|---|
| C4-001 | P2 | **保留 P2** | 全部证据亲核成立（bean 全仓缺失、0/717 xbiz 采用、xdef 文档背离、无启动期校验）；前置条件（callActionAsync 接线）现状零调用方，潜伏契约 P2 恰当 |
| C4-002 | P2 | **保留 P2** | 载体（5 线程 globalTimer 上同步网络写）与生命周期不对称（onOpen :74→:82 接线间隙、onClose 按 userProperties 取 null 即漏取消）亲核成立；与 C8-007 聚合面互引维持 |
| C4-003 | P1 | **降级 P2** | ×7 加法、90s/7-10s 挂起、无取消均复核成立；但 P1 判定支柱「JDK 全局单线程 Delayer 执行重试发起重活」在目标运行时（Java 25）上被证伪（复核勘误 8）；存活危害为有界过载放大，幽灵重试腿归 C3-010（耦合对），不满足本 campaign P1 bar |
| C4-004 | P2 | **保留 P2** | executeBlocking 包整链、runOnContext 内联、flush 载体归属亲核成立；本体=载体选择缺陷，与 C4-007 分层不重复 |
| C4-005 | P2 | **保留 P2** | boolean 主面 + :49 catch 面亲核成立；无平台对账机制复核成立；三方（C4-005+CL-007+C3-CL-006）绑定同一修复单元维持 |
| C4-006 | P3 | **保留 P3** | 自建自毁/命名/daemon/默认参数 4-20-10000、destroy=shutdown 无 await 亲核成立；P3 恰当 |
| C4-007 | P1 | **维持 P1**（附勘误 9） | a-e 逐项终裁：syncGet 阻塞、:359 调用链、默认 true 半衰期窗口、失败不降频放大机制、勘误 7 修复面更正全部复核成立；「无界」更正为默认 30s 有界后，brownout 触发下全会话进入刷新窗口 → 吞吐坍缩 ≈ max(8C,200)/30s，仍为确定性服务级停摆，P1 成立 |
| C4-008 | P2 | **维持 P2**（附勘误 9/10） | 锁内无超时同步拉取 + monitor 串行化 + 失败静默亲核成立；401 窗口链收窄（stale-serve：稳定态失败旧钥续用，401 仅限冷启动失败/KID 轮换且拉取失败）；「无界钉住」→ 默认 30s 有界；事件驱动确定性阻塞维持 P2 |

### C4-003 终裁说明（任务特别指令）

- **×7 加法算术**：独立复核成立。内层 `invokeUrl:135` maxRetries=3 → 主 URL 至多 1+3=4 次 fetch；外层 `AiFailoverGatewayInterceptor.tryInvokeWithFallback:73-109` 仅在内层耗尽（终态 `ERR_GATEWAY_UPSTREAM_429/FAILED`）后启动，`tryFallbackUrl:111-145` 每个 fallback URL 恰 1 次 fetch、无延迟、完全绕过 InvokeProcessor，上界 `min(|fallbackUrls|, maxRetries=3)`（`gateway-defaults.beans.xml:64-71` 默认装配、fallbackUrls 默认空）→ **4+min(|fallbackUrls|,3) ≤ 7，加法无乘法**。第 2 轮勘误 1 维持。
- **Delayer 共享面（CL-022 的 10 站点 orTimeout）**：JDK 语义勘误成立、站点计数勘误成立。项目 `pom.xml:25-30` 锁定 `java.version=25`/`release=25`（字节码 major 69，运行时必为 JDK ≥25）；实测 JDK 25.0.1 源码：`CompletableFuture.DelayedExecutor.execute` → `ForkJoinPool.asyncCommonPool().scheduleDelayedTask(...)`，`orTimeout` → `arrangeTimeout` 同路；`java.util.concurrent.DelayScheduler extends Thread`（每池单线程）**仅做"到点 relay 到池"**，任务体（含 `future.complete` 触发的 `thenCompose` 重试发起、orTimeout 的 `Timeout.run` 超时完成）均在 common pool 多线程工作线程执行。即：JDK ≤23 的「`CompletableFuture.Delayer` 单 daemon 线程执行超时完成与其非 async 依赖」语义在本项目运行时**不存在**。全景表第 5 行「complete 后重试发起也在该线程执行」、第 2 轮 §1.2「重试发起……直接推迟其他 10 个站点的超时判定」随此更正（复核勘误 8）。
- **站点计数小勘误**：orTimeout 生产站点实测 **11 处**（第 2 轮正文写"10 个"、清单列 11 项——清单无缺漏，正文计数笔误）；加 InvokeProcessor 的 delayedExecutor 共 12 站点共享 common pool DelayScheduler 定时队列。
- **终裁**：降级 P2。P1 三支柱中「全局单线程占用」被证伪、「幽灵重试」与 C3-010 同缺陷本体（重复计分收归 C3-010，互为前置耦合对维持）、「放大+挂起」为有界过载放大——修复四建议（取消检查、平台定时器/专用调度器、发起移出调度线程、双层重试协调）全部维持，且取消检查在 nop-http-api 断连原语（CL-025）落地前不可实现，编排不变。

### C4-007 终裁说明（任务特别指令）

- a) **阻塞事实**：`OAuthLoginServiceImpl.refreshToken:258` `FutureHelper.syncGet(httpClient.fetchAsync(...))` 亲核成立；`syncGet`（`FutureHelper:158-175`）→ `ContextProvider.getOrCreateContext().syncGet` → `ContextTaskQueue.syncGet:46-105`；实测 await 期间外层 flush 的 `processingThread` 角色仍持于等待线程（`endProcess` 仅在 `processing==0` 时清空，嵌套 syncGet 内层 `endProcess` 使 `processing` 2→1 不清角色），本请求上下文全部后续任务被钉于该线程。
- b) **调用链**：`AuthHttpServerFilter:70-75`（默认 true）→ `:320-329` 半衰期判定 → `:155-191` executeBlocking 载体 → `:357-378` runOnContext 内联 lambda → `:359` 同步 `refreshToken` → `:374` 下游链，亲核成立。
- c) **默认+概率**：`autoRefreshToken` 默认 true 亲核成立；补充实测放大器：**IdP 失败路径不降频**——刷新失败返回 null（`:260-263`）后不更新 cookie/头，token 停留在半衰期窗口，故障期间该会话**每请求重复阻塞**；SSO 装配 `sso-defaults.beans.xml:15-21` 单属性切换亲核成立。
- d) **修复面勘误 7 合理性**：复核确认。`ILoginSpi.refreshTokenAsync:59` 唯一实现 `LoginApiBizModel:153-157`（`parseRefreshToken → getUserContextAsync → buildLoginResult` 返回缓存 accessToken，无轮换、无 IdP 调用）；`OAuthLoginServiceImpl:49` 仅 `extends AbstractLoginService` 不实现 ILoginSpi；签名 `(RefreshTokenRequest, IServiceContext)` 与过滤器现场 `(IUserContext, AuthToken)` 形状不匹配——「不可按字面落地」成立；更正方向（`ILoginService` 契约异步面 + `OAuthLoginServiceImpl` 剥离 syncGet 外壳 + `LoginServiceImpl` override 为 `FutureHelper.success(...)` + `req.timeout(...)`）经 `:258` 内部链本为异步形状、`HttpRequest.timeout:123-141` 在位亲核确认可行；串行组合约束（刷新领先 `next.get()`）合理。
- e) **终裁：P1 维持**，附复核勘误 9：「数秒至无界」「仅部署级 readTimeout 时才有界」不完整——`HttpClientConfig.java:35-37` 默认 `readTimeout=30s/writeTimeout=30s/connectTimeout=10s`（`http-api.beans.xml` `nopHttpClientConfig` ioc:config 绑定，`JdkHttpClient:218-222` 在无 request 级超时时使用之），默认部署每次刷新钉线**有界 ≈30s**。修正后 P1 论证：单属性一等部署形态 + 默认开启 + IdP brownout（可达不响应——SSO 部署常见故障形态）+ 失败不降频 → 全部会话在 TTL/2 后进入刷新窗口 → 无隔离全局 worker 池（max=max(8C,200)、无界队列）被按 30s/次钉满 → 全站（含非 SSO 相关路径）确定性吞吐坍缩至 ≈ maxThreads/30s，故障表现为延迟雪崩而非快速失败——满足本 campaign P1 bar（确定性服务级停摆）。触发条件精确化：IdP 连接拒绝（快速失败）危害有限，P1 挂钩 brownout/慢响应形态。

### C4-008 核实说明

锁结构（单飞意图正确）、锁内 `sendRequest:112` 无 per-request 超时同步拉取、每小时 TTL 确定性事件、失败静默吞且占用速率窗均亲核成立；两处表述按勘误 9/10 更正（见下）。P2 维持：事件驱动的确定性锁内阻塞 + IdP 慢响应时与 C4-007 同型钉线级联（同一无隔离 worker 池、同一 IdP 依赖、同一部署条件），触发频度低于 C4-007（每小时/冷启动/轮换 vs 每会话半衰期），不足以升 P1；危害窄于初审 T3b 表述（401 窗口仅冷启动+轮换），不足以降 P3。

### 复核新增勘误（第 8~10 号，承第 2/3 轮勘误 1~7）

| # | 位置 | 原表述 | 更正 | 依据 |
|---|---|---|---|---|
| 8 | C4-003 本体/全景表第 5 行/第 2 轮 §1.2/CL-022 | 重试定时与发起在 JDK 全局单线程 Delayer 上执行，推迟其他 orTimeout 站点超时判定 | 项目运行时 JDK ≥25（pom release=25）：`delayedExecutor`/`orTimeout` 走 `ForkJoinPool.asyncCommonPool()` 的 `java.util.concurrent.DelayScheduler`（单定时线程仅到点 relay），任务体在 common pool 多线程工作线程执行 | JDK 25.0.1 src.zip 实测（`CompletableFuture` DelayedExecutor/TaskSubmitter/Timeout/arrangeTimeout、`ForkJoinPool.scheduleDelayedTask`、`DelayScheduler extends Thread` 类文档） |
| 9 | C4-007「数秒至无界」、C4-008 T3c「无界钉住」、第 2 轮勘误 6 | 调用点无超时保证/时长仅部署级配置时有界（暗示默认无界） | 默认装配即有界：`HttpClientConfig.java:35-37` 默认 readTimeout=30s/writeTimeout=30s/connectTimeout=10s，`http-api.beans.xml` `nopHttpClientConfig` ioc:config 默认装配，`JdkHttpClient:218-222` 无 request 级超时时采用 | HttpClientConfig 字段默认值 + beans 装配 + JdkHttpClient 超时选择链实测 |
| 10 | C4-008 T3b | 拉取失败 → 全部排队请求 `lookupCachedKey` 得 null → 认证 401，60 秒窗口 | 失败后 `lastRequestTime` 照常推进 → TTL 门判未过期 → 已知 kid 返回**旧钥**（stale-serve），稳定态失败不产生 401；401 窗口仅限冷启动失败（currentKeys 空）与 KID 轮换且窗口内拉取失败 | `JWKPublicKeyLocator.java:77,97-103` 逐行实测 |

小勘误：CL-022 正文 orTimeout 计数 10 → 实测 **11**（清单无缺漏）；CL-024 `HttpPluginResourceResolver.java:165` 为同步 `download`（非 `fetch`，同族阻塞远程 I/O、方法名不同），sync `fetch` 站点为 `:241` 一处；第 1 轮 C4-004「quarkus 默认 worker ~128」已被第 2 轮勘误 4 更正，复核维持更正。

### CL 处置汇总

| CL | 处置 | 说明 |
|---|---|---|
| CL-010 | **确认**（移交 C9） | `GatewayHttpFilter:213-221,246-260` 上游发射线程同步序列化亲核成立；与 C4 维度无关，不立案 |
| CL-011 | **驳回** | 与 C8 轮终判一致：`DefaultFlowControlRunner:18` maxWaitMillis 默认 0 且无生产配置点，`tryAcquire(1,0)` 非阻塞立即拒绝；缺省 runner 为 SentinelFlowControlRunner 亦非阻塞。「配置化阻塞」无生产现实性，本维度不立案；C8-008（keying 缺陷）另立有效 |
| CL-022 | **确认**（跨单元线索，附勘误 8 + 计数 11） | 共享面成立但身份更正：共享 common pool DelayScheduler 定时队列（单定时线程仅 relay）；「orTimeout 完成回调在单线程执行」在 JDK 25 不成立。供 nop-ai/nop-message 单元复核其 orTimeout 站点时按更正后语义取用 |
| CL-023 | **确认**（移交认证语义/安全维度） | `LoginApiBizModel.refreshTokenAsync:153-157` 无轮换语义、`ILoginService.parseRefreshToken:112-114` 回退 parseAuthToken（以 access-token 校验链验 IdP refresh token）亲核成立；C4-007 修复计划不得将其当刷新通道 |
| CL-024 | **确认**（移交各审计单元） | 双检锁内无超时同步 fetch（`AddAccessTokenHttpClientEnhancer:113-121,131`）亲核成立；7 站点中 6 处精确、`:165` 方法名小勘误（download）；按「无 per-request 超时 + 载体身份 + 锁内阻塞」三要素 + 勘误 9（默认 30s 有界）复核 |
| CL-025 | **确认**（C3-010 修复前置，平台原语先行） | `IHttpServerContext` 内 `abort|disconnect|isClosed|cancel` grep 零命中亲核成立；断连观测原语须先于 C4-003/C3-010 取消修复落地 |

### 统计

- 8 条立案：**保留 7（其中 C4-007 维持 P1）、降级 1（C4-003 P1→P2）、驳回 0**；无新增立案。
- 复核后判级分布：P0×0，**P1×1（C4-007）**，P2×6（C4-001/002/003/004/005/008），P3×1（C4-006）。
- CL 6 条：确认 5（CL-010/022/023/024/025），驳回 1（CL-011，与 C8 轮一致）。
- 复核新增勘误 3 项（第 8~10 号）+ 2 项小勘误（CL-022 计数、CL-024 :165 方法名）。
- 现状标注：全部 8 条 live code **未修、行号未漂移**（逐文件亲核：InvokeProcessor、AiFailoverGatewayInterceptor、GatewayHttpFilter、AuthHttpServerFilter、OAuthLoginServiceImpl、JWKPublicKeyLocator、BizActionService、JsonRpcWebSocketHandler、DownloadHelper、GrpcServer、ContextTaskQueue、AbstractLoginService、AbstractUserContextCache、HttpClientConfig、JdkHttpClient、IHttpClient、ILoginSpi、LoginApiBizModel、LoginServiceImpl、SsoConfig、sso-defaults.beans.xml、gateway-defaults.beans.xml）。
- 判级口径校验：沿 plan 2470 热路径口径（每请求必经链「常见条件」门槛最低；P1=确定性 OOM/不可逆/服务级停摆）。C4-003 降级与 C4-007 维持的差分依据：前者请求处理线程全程异步释放、网关自身不停摆；后者钉满的是请求处理载体本身。

---

## 最终保留项

复核后 C4 维度最终保留 8 条（1 降级 7 维持），全部进入修复规划候选；勘误 8~10 为修复设计与文案的强制输入。

| ID | 复核后判级 | 保留要点（复核修正后） | 修复方向（复核确认有效） |
|---|---|---|---|
| C4-007 | **P1** | SSO 部署下半衰期刷新在认证载体线程同步阻塞 IdP HTTP（无 request 级超时，默认客户端 30s 兜底）；brownout 下失败不降频，全会话进窗 → 无隔离 worker 池钉满、全站吞吐坍缩 | `ILoginService` 契约异步面 + `OAuthLoginServiceImpl` 剥离 syncGet（勘误 7 路径）+ `req.timeout(...)` 显式超时 + 按会话聚合刷新（串行组合约束保持） |
| C4-001 | P2 | `defaultWorkExecutor` 全仓无定义 + 0/717 xbiz 采用 + 无启动期校验 + xdef 文档背离；callActionAsync 一经接线默认路径必失败 | 默认 bean（显式 ThreadPoolConfig 有界+Abort）或启动期 actionModel executor 预检 + bizSequential 类型守卫 |
| C4-002 | P2 | 每 WS 连接 1 个 10s 周期任务挂 5 线程全局 timer，timer 线程同步网络写；构造即启动、取消依赖晚注册的 handler，接线失败路径任务永驻 | 写改异步/带超时；注册后再启动定时或关闭状态探测自取消；连接级心跳聚合（与 C8-007 联动） |
| C4-003 | **P2**（降级） | 网关重试链无取消传播（幽灵重试 4+3=7 次上游调用）+ 429 满额挂起 ≈90s + JDK 全局共享调度设施无治理（勘误 8：任务体在 common pool 执行，非单线程占用） | 取消检查（依赖 CL-025 原语先行）+ 延迟载体迁平台 timer/专用调度器 + 发起移出调度线程 + 双层重试加法协调；与 C3-010 同批落地 |
| C4-004 | P2 | executeBlocking 包住含 next.get() 的整条下游链，认证域无隔离池；刷新/回写共享宿主全局 worker 载体 | 下游链移出 blocking 段 + 专用 `nop-auth-worker` 有界执行器 + flush 按会话去抖 |
| C4-005 | P2 | 临时 zip 清理挂全局 timer 一次性任务；delete() boolean 主面 + 异常次面 + :49 catch 面均无感知，失败文件永驻、重启遗留 | boolean 检查+日志 + try/catch + 平台级 `temp:/download` 全子树 mtime 对账（与 C3 轮 CL-007、C3-CL-006 同一修复单元） |
| C4-006 | P3 | 自建池停机 destroy=shutdown 无 awaitTermination（在途无界期）；默认 10000 深队列使排队延迟先于背压显形 | 有界 awaitTermination（超时 shutdownNow 记数）+ gRPC 浅队列默认/文档 |
| C4-008 | P2 | JWKS 定位器 synchronized 锁内无 per-request 超时同步 IdP 拉取（每小时 TTL 确定性 + 冷启动/轮换事件），失败静默吞且占用 60s 速率窗；IdP 慢响应与 C4-007 同型钉线级联 | 异步单飞（在飞 future 登记表）+ 显式 timeout + 失败保留旧钥宽限（**保留 stale-serve 语义**，勘误 10）+ 区分 TTL 过期/kid 缺失 + 指标；与 C4-007 同批规划 |

保留项之外的口径记录：CL-010/023/024 按归属移交（C9/认证语义/各审计单元）；CL-022 按勘误 8 更正后供跨单元取用；CL-025 作为 C3-010×C4-003 耦合对的平台前置登记；CL-011 终判不成立（与 C8 轮一致）。
