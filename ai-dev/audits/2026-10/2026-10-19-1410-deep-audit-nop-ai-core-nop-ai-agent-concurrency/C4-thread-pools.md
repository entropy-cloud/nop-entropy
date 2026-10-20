# C4 线程池与执行器治理 — nop-ai-core-nop-ai-agent（WI 3.1）

## 审计范围与口径

- **批次 X1/3 覆盖分区**：`nop-ai/nop-ai-core/src/main/java`（119 个 .java，其中非生成 101 文件 / 14,176 行；`_gen/` 18 个生成文件按纪律排除）。`nop-ai/nop-ai-agent/src/main/java` 的全部 C4 站点（DefaultAgentEngine、SessionLockRenewal、DBMessageService、InMemoryActorRuntime、TaskDispatchCoordinator、TeamTaskFlowOrchestrator、DockerSandboxBackend 等）归后续批次 X2/X3，本文件不覆盖。
- **方法**：以 `_tmp/cra-baseline-nop-ai-core-nop-ai-agent.txt` C4a（thread creation and pools）/ C4b（schedulers/timers）nop-ai-core 分节圈定重点（两节 nop-ai-core 侧仅命中 `EmbeddingServiceImpl` 一处），再以全模块正则扫描（`new Thread` / `Executors.` / `Executor` / `ThreadFactory` / `ForkJoinPool` / `commonPool` / `parallelStream` / `SubmissionPublisher` / `Timer` / `supplyAsync` / `runAsync` / `schedule`）逐文件复核，确认无遗漏站点。
- **盘点结论**：本模块自建线程设施仅 2 处——`EmbeddingServiceImpl` 内部惰性重试调度器、`ChatServiceImpl.callStream` 缺省 executor 的 `SubmissionPublisher`。其余线索均为注释/javadoc 引用（`LlmErrorClassifier`、`IRetryPolicy`、`IFileOperator`）。`ThresholdBreaker` / `ProviderFailoverQueue` 采用 lazy 状态转换（无后台定时器，javadoc 明文声明为设计决策），不构成调度器站点。
- **口径纪律声明**：发现按附录 A 13 字段格式输出，每条含触发时序与 F3 失效面枚举；严重度按领域校准；低频/启动路径粗锁、原语正确使用、有界声明明确者不报；平台基础设施（nop-http 的 `ServerEventPublisher`/`AbstractServerEventSubscription`）自身实现不重审，仅作为使用方证据链引用。启动期站点不适用降档口径（本模块两处站点均为请求触达面）。
- **批次 X2/3 覆盖分区**：`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/` 下 6 包逐文件审读——`security`（74f）/ `plan`（66f，含 model+runtime 子包，手写口径）/ `engine`（47f）/ `runtime`（34f，含 coordination/lock/recovery）/ `model`（33f，`_gen` 与 `recipe` 生成物排除）/ `reliability`（30f）。全 6 包正则扫描（`new Thread`/`Executors.`/`ExecutorService`/`ForkJoinPool`/`commonPool`/`parallelStream`/`scheduleWith`/`scheduleAt`/`new Timer`/`SubmissionPublisher`/`GlobalExecutors`/`IThreadPoolExecutor`）确认线程设施站点 9 处（见下方 X2 清单表）；`plan`/`model`/`reliability` 三包零线程设施（grep 零命中，`PlanScheduler` 为纯 DAG 计算）。`message`/`team`/`tool`/`session` 等包的 C4 站点（`DBMessageService` poller、`TeamTaskSchedulerDaemon`、`TaskDispatchCoordinator`/`TeamTaskFlowOrchestrator` 自建池、`DefaultBackgroundTaskRegistry`、session store 锁面）归批次 X3，本批次不覆盖。本批次全部站点为请求触达面或长时运行面，降档口径不适用。
- **批次 X3/3 覆盖分区（C4 收官批）**：`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/` 下 `team`（55f，含 flow/scheduler）/ `tool`（21f）/ `session`（21f）/ `message`（18f）/ `memory`（17f）五包逐文件审读，加上其余 16 小包（budget/compact/completion/conflict/contribution/fencing/guardrail 含 rule//hook/middleware/quota/recipe/repair/router/skill/talent/usage）与根（NopAiAgentErrors/NopAiAgentException/SessionIds）全量正则扫描复核。线程设施站点 7 处（见下方 X3 清单表）；`session`/`memory`/16 小包/根零线程设施（`new Thread`/`Executors.`/`ExecutorService`/`ForkJoinPool`/`commonPool`/`parallelStream`/`scheduleWith`/`scheduleAt`/`new Timer`/`SubmissionPublisher`/`Thread.sleep`/`CountDownLatch`/`Semaphore` 全部零命中；compact 为 ReAct 循环内同步触发，无独立触发线程）。本批次全部站点为请求触达面或长时运行面，降档口径不适用。基线 C4a/C4b 的 `message/DBMessageService`、`team/scheduler/TaskDispatchCoordinator`、`team/flow/TeamTaskFlowOrchestrator`、`team/scheduler/TeamTaskSchedulerDaemon:237`、`tool/AgentDispatchExecutor`、`tool/DefaultBackgroundTaskRegistry` 六分节全部落入本批逐一复核，无遗漏站点。至此 C4 维度三批次（X1 nop-ai-core + X2/X3 nop-ai-agent）覆盖收官：nop-ai-agent 全部 27 包 + 根的线程池/执行器/定时器站点已全量盘点。

## 线程池清单表

| 池 / 执行器 | 位置 | 参数 | 队列 | 拒绝策略 | 关闭路径 | 判定 |
|---|---|---|---|---|---|---|
| `EmbeddingServiceImpl` 内部重试调度器（`ScheduledThreadPoolExecutor`，惰性创建） | `EmbeddingServiceImpl.java:126-142,443-445` | core=1，daemon，命名 `nop-ai-embedding-retry`，`removeOnCancelPolicy(true)` | STPE 内置无界 delayed 队列（任务为一次性短任务，退避延迟受 `StandardRetryPolicy` 上限 30s/60s 约束） | 无界队列 → 无拒绝面 | **无任何关闭路径**（无 destroy/stop 方法；`ai-defaults.beans.xml:12-13` 未注册 stop 钩子）；`setRetryScheduler` 换装后旧内部池成孤儿 | 缺陷（C4-003/C4-004/C4-005） |
| `ChatServiceImpl.callStream` 的 `SubmissionPublisher`（缺省 executor） | `ChatServiceImpl.java:202-233` | 缺省 = `ForkJoinPool.commonPool()`（commonPool parallelism==1 时退化为 per-publisher 临时线程） | 内部 buffer 默认 256（`Flow.defaultBufferSize`），满时 `submit()` 阻塞上行读线程（背压） | 无丢弃；阻塞背压 | 正常/错误路径 `close()`/`closeExceptionally()`（L224/229）；**取消路径无 close** | 缺陷（C4-001/C4-002） |
| （非自有）上游 SSE 订阅执行线程 | `ChatServiceImpl.java:204-231` | `ServerEventPublisher.executor()` = `GlobalExecutors.globalWorker()`（nop-http 设施） | — | — | 随订阅取消/终止 | 平台设施，仅作证据引用 |
| （无线程设施确认）`ThresholdBreaker` / `ProviderFailoverQueue` / persist 层 / `DefaultChatLogger` / `AiCommand` | 全模块扫描 | 无线程、无调度器（熔断器为 lazy cooldown 转换，javadoc 明文设计） | — | — | — | 无发现 |

## 发现

### [C4-001] `callStream` 流式发布依赖缺省 `ForkJoinPool.commonPool`，终止回调在共享池上执行同步磁盘 I/O

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/ChatServiceImpl.java:202,223-231,400-437`
- **证据片段**:
  ```java
  // ChatServiceImpl.java:202 —— 未指定 executor，缺省 ForkJoinPool.commonPool()
  SubmissionPublisher<ChatStreamChunk> publisher = new SubmissionPublisher<>();
  ...
  // :227-230 —— onComplete/onError 终态经该 publisher 的 executor（commonPool）派发
  @Override
  public void onComplete() {
      publisher.close();
  }
  // :427-434 —— aggregateStreamToResponse 订阅者的 onComplete 跑在 commonPool 线程上，
  // 内含 JSON 序列化 + 3 段正则全文脱敏 + ResourceHelper.writeText 同步磁盘写
  public void onComplete() {
      ChatResponse response = aggregator.toResponse();
      ...
      chatLogger.logResponse(request, response);
      future.complete(response);
  }
  ```
- **严重程度**: P2
- **并发场景**: 每路 LLM 流式响应的 chunk 派发与终态回调全部运行在 JVM 全局 `ForkJoinPool.commonPool()` 工作线程上；`SubmissionPublisher` 把下游订阅者（`aggregateStreamToResponse` 内部订阅者及任何外部订阅者）的 `onNext`/`onComplete`/`onError` 异步派发到该池。平台自带 `io.nop.commons.concurrent.executor`（`IThreadPoolExecutor`、`GlobalExecutors.globalWorker()/globalTimer()`）未被使用。
- **触发时序**: T1：N 路并发流式调用进入 `callStream`（L202），每个 chunk 的下游派发占用一个 commonPool 线程（模块内聚合订阅者执行快，不阻塞）；T2：流终止时 `onComplete` 在 commonPool 线程执行 `chatLogger.logResponse`——当 `nop.ai.service.log-message=true` 且 `LlmModel.isLogMessage()`（`AiCoreConfigs.java:19-20`，配置开启）时，该回调做全量 `JSON.serialize` + 3 段正则替换 + `ResourceHelper.writeText` 同步磁盘写，单线程占用毫秒到十毫秒级；T3：并发流数 × 终态 I/O 使 commonPool（parallelism = cores-1）出现排队，波及全平台 commonPool 使用方（并行流、任何未显式指定 executor 的异步任务）。交叠后果：平台共享池被 AI 流式路径的 I/O 占用/拖慢。若外部订阅者在 `onNext` 内做阻塞 socket 写（当前仓库内 `nop-ai-gateway` 的 `FailoverStreamFlow` 包装链经 `ChatServiceFailoverAdapter.callStream` 消费本 publisher），占用时间随客户端速度放大，届时升级为 P1 隔离违约。
- **现状**: 流式面完全依赖 `SubmissionPublisher` 缺省 executor（commonPool），未指定专用池；终态回调在共享池上做同步磁盘 I/O 与全文正则脱敏。
- **风险**: 共享池占用面随并发流数线性放大，AI 慢 I/O 与平台通用任务（并行流、缺省异步）互相干扰；nop-ai-agent 侧代码已显式注释规避 commonPool 争用（基线 `ReActAgentExecutorBuilder.java:588`、`DefaultAgentEngine.java:959`），而本模块流式主路径与之相悖。
- **建议**: 为 `SubmissionPublisher` 显式指定 executor：复用平台 `GlobalExecutors.globalWorker()` 或按 `ThreadPoolConfig` 自建命名 `IThreadPoolExecutor`（与上游 `ServerEventPublisher` 使用 `GlobalExecutors.globalWorker()` 的既有约定对齐）；`logResponse` 的序列化+脱敏+写盘移出共享池回调（异步化或换专用日志池）。
- **失效面枚举**（F3：同族缺省-executor 站点全量清扫）: ① `ChatServiceImpl.callStream:202` —— 本条，缺陷主体；② `EmbeddingServiceImpl.scheduler():126-142` —— 已显式自建 daemon 命名池，非缺省依赖（其问题见 C4-004）；③ `aggregateStreamToResponse:400-437` —— 经同一 publisher 派发，同受影响，随 ① 一并修复；④ `response/*` 五个解析器、`persist/*`、`DefaultChatLogger`、`AiCommand`、reliability 包 —— 全模块扫描无线程/executor 创建，无同族站点。
- **信心水平**: 确定（结构性事实：缺省 executor + 终态磁盘 I/O 在共享池执行，均可由代码直接证明；「外部订阅者阻塞放大」部分为很可能）
- **误报排除**: 非报告 `SubmissionPublisher`/原语本身使用问题（误报校准第 2 条），报告的是缺省池依赖的治理缺陷 + 共享池上确定性同步 I/O；模块内聚合路径非阻塞的事实已如实计入定级（P2 而非 P1）。
- 复核状态: 保留(P2)｜新发现——回码核验成立：L202 无参 `SubmissionPublisher`（缺省 commonPool 为 JDK 契约）、`DefaultChatLogger.logResponse` 确为 `JSON.serialize`+3 正则+`ResourceHelper.writeText` 同步盘写、`shouldLogMessage` 双配置门（`CFG_AI_SERVICE_LOG_MESSAGE`×`LlmModel.isLogMessage`，L370-371）均直核；同站点跨维指针 C3-002（P1 背压轴）/C3-009（P2 缺省池轴）成立，并单留 7.1；WI 2.1 CL-021 移交面（裸 JDK 载体绕过设施，点名 ai-agent）

### [C4-002] 流取消路径 `SubmissionPublisher` 永不关闭：下游订阅者永久收不到终止信号（F8 清理不对称）

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/ChatServiceImpl.java:202-233`（依赖证据：`nop-network/nop-http/nop-http-client-jdk/.../ServerEventPublisher.java:119-121,128-129,133,141`、`nop-network/nop-http/nop-http-api/.../AbstractServerEventSubscription.java:118-121,155-161`）
- **证据片段**:
  ```java
  // ChatServiceImpl.java:207-212 —— 取消时只取消上游订阅，自身 publisher 的生命周期
  // 完全托付给上游的终态回调
  public void onSubscribe(Flow.Subscription subscription) {
      subscription.request(Long.MAX_VALUE);
      if (cancelToken != null) {
          cancelToken.appendOnCancelTask(subscription::cancel);
      }
  }
  // 依赖证据 ServerEventPublisher.java:128-131 —— 上游取消后静默 return，onComplete 不派发
  parseEvents(reader);
  if (isCancelled())
      return;
  onComplete();
  // AbstractServerEventSubscription.java:155-159 —— cleanup 只置位+取消 future，无终态派发
  public synchronized void cleanup(String reason) {
      isCancelled = true;
      if (future != null) { future.cancel(true); }
  ```
- **严重程度**: P1
- **并发场景**: `callStream` 的取消链路（HTTP worker/调用方线程触发 cancelToken → 上游订阅的 `globalWorker` 读线程在 `readLine` 阻塞/被门控短路）与 `callStream` 下游订阅者线程（commonPool 派发）之间的终态信号传递。
- **触发时序**: T1：LLM 响应头已到达、body 流式解析中（`ServerEventPublisher` 的 executor 块已进入 `parseEvents` 读循环），用户中断请求（客户端断开 → cancelToken 取消）→ T2：`subscription.cancel()` → `cleanup()` 置 `isCancelled=true` 并 `future.cancel(true)`——此时 sendAsync future 已正常完成，cancel 为 no-op，不触发 L73-78 的 `onError` 兜底 → T3：读线程在 `parseEvents` 门控点（L141）/`executor` 块门控点（L120、L128）静默返回，`ChatServiceImpl` 匿名订阅者的 `onError`/`onComplete` 均不执行 → `publisher.close()/closeExceptionally()` 永不调用（L209-211 只注册了 `subscription::cancel`，未把 `publisher` 的关闭挂到 cancelToken）→ 交叠后果：`callStream` 返回的 `SubmissionPublisher` 永远不关闭，其全部下游订阅者收不到任何终止信号，依赖流结束语义的调用方（如聚合 future、SSE 响应收尾）永久挂起；上游 `globalWorker` 读线程在 `readLine` 上滞留至连接自然 EOF（连接不断则无限期）。
- **现状**: 正常路径（onComplete→`close()`）与错误路径（onError→`closeExceptionally`）都关闭 publisher，唯独取消路径既不关 publisher 也不派发终态——资源清理不对称（F8）。
- **风险**: 平台主聊天流式 API 的取消语义违约：用户中途取消后下游永久悬挂、连接/线程滞留；长时运行下累积未关闭 publisher 与滞留读线程（每路取消+一次）。
- **建议**: 在 `ChatServiceImpl.callStream` 中把自身 publisher 的关闭与上游取消绑定：`cancelToken.appendOnCancelTask(() -> { subscription.cancel(); publisher.closeExceptionally(newCancellation()); })`；同时在 nop-http 层（跨模块修复建议）让取消路径统一派发终态，消除静默 return 分支。
- **失效面枚举**（F3）: ① `ChatServiceImpl.callStream:202-233` —— 本条缺陷主体；② `aggregateStreamToResponse:400-437` —— 消费同一 publisher 链，取消场景同样收不到终态（其内部 future 同样永不完成），随 ① 一并修复；③ `EmbeddingServiceImpl` —— 无 publisher/取消参数，无同族站点；④ nop-gateway `FailoverStreamFlow`/`BufferedStreamingPublisher`、`nop-service-framework/StreamingProcessor` —— 本 publisher 的下游消费方，受悬挂影响但非本模块修复面（引用证据，不重复立案）。
- **信心水平**: 确定（「响应已开始后取消 → 无终态」由上游代码门控分支直接证明；「响应头等待期取消」窗口因 `future.cancel(true)` 触发 whenComplete→`onError`（L76，不受 isCancelled 门控）能正常终止，已在时序中区分）
- **误报排除**: 不是 Flow 原语误用泛报——是本模块持有的 publisher 资源在取消路径无清理且将清理责任托付给契约上不保证终态的第三方（Reactive Streams 规范：cancel 后终态可选），上游代码实证了静默分支的存在。
- 复核状态: 保留(P1)｜新发现——回码核验成立：jdk `ServerEventPublisher` executor 块与 `parseEvents` 共 3 处 `isCancelled` 静默 return 分支、`AbstractServerEventSubscription.cleanup` 仅置位+cancel future 无终态派发均直核；「头等待期取消经 `future.cancel(true)`→whenComplete→`onError`（`onError` 不受 isCancelled 门控）正常终止」的时序区分亦复核成立；「错误/取消路径的资源泄漏」属校准 P1 档；同站点跨维指针 C3-001（P1）成立留 7.1 并单；下游面与 WI 2.3 C3-003（BufferedStreamingPublisher 背压断裂）相邻已在失效面 ④ 登记

### [C4-003] 重试调度任务异常被静默丢弃：`catch (Exception)` 不拦 Error + `scheduleRetry` 丢弃 `ScheduledFuture` → embed future 永不完成且零日志

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/EmbeddingServiceImpl.java:257-281,344-354,443-445`
- **证据片段**:
  ```java
  // EmbeddingServiceImpl.java:443-445 —— 一次性调度，返回的 ScheduledFuture 被丢弃
  protected void scheduleRetry(long delayMs, Runnable task) {
      scheduler().schedule(task, delayMs, TimeUnit.MILLISECONDS);
  }
  // :257-280 —— dispatch 仅捕获 Exception；Error 逃逸后进入 STPE，
  // 异常被吞进被丢弃的 ScheduledFuture，无 afterExecute 日志
  void dispatch(CompletableFuture<List<VectorData>> future) {
      attempt++;
      try {
          ...
          request.setBody(JsonTool.serialize(body, false));
          httpClient.fetchAsync(request, null).whenComplete((resp, err) -> { ... });
      } catch (Exception e) {
          future.completeExceptionally(e);
      }
  }
  ```
- **严重程度**: P2
- **并发场景**: `nop-ai-embedding-retry` 调度线程执行退避重试任务 `() -> dispatch(future)`（由 `handleFailure:354` 在 HTTP 回调线程上调度），调用方线程阻塞/等待 `embedAllAsync` 返回的 future。
- **触发时序**: T1：第 N 次尝试失败，`handleFailure` 判 RETRY，`scheduleRetry(delay, () -> dispatch(future))` 入队（L354）→ T2：延迟到期，调度线程执行 `dispatch`，`JsonTool.serialize`（L293）或 `resolveBaseUrl`（L311-328）抛出 `Error`（如深嵌套输入的 `StackOverflowError`、`NoClassDefFoundError`、OOM）→ T3：`Error` 不是 `Exception`，穿透 L276 的 `catch (Exception)` 逃逸；`schedule(Runnable)` 的一次性任务把异常捕获进被丢弃的 `ScheduledFuture`，`ScheduledThreadPoolExecutor` 未覆写 `afterExecute`，无任何日志 → 交叠后果：`embedAllAsync` 返回的 future 既不 complete 也不 completeExceptionally，调用方永久挂起（RAG 索引作业等同步 join 场景）或该文档静默丢失且无任何可观测痕迹。
- **现状**: 调度任务链的最外层兜底只到 `Exception`；`Error` 路径既无兜底 complete 也无日志。
- **风险**: 罕见但后果为「静默挂死 + 零可观测性」；正是 F9（静默吞异常→状态不一致）在调度器上的形态。
- **建议**: `dispatch` 的兜底改 `catch (Throwable)`（`Error` 亦 `future.completeExceptionally`）或以 try/finally 保证 future 终态；给内部调度器加 `afterExecute` 记录任务异常（对照 `ThresholdBreaker` 同类的可观测性标准）。
- **失效面枚举**（F3）: ① `EmbeddingServiceImpl.dispatch:257-281` —— 本条缺陷主体；② 同文件 `whenComplete` 回调体 `:263-275` —— 已有独立 try/catch(Exception)+completeExceptionally 兜底，`Error` 同样穿透但发生在 HTTP 客户端回调线程（异常进入未被消费的 whenComplete 返回 stage），形态同族，建议随本条一并收口；③ `ChatServiceImpl` —— 无调度器、无一次性任务丢弃面，无同族；④ `AiCommand` 的 `RetryHelper.retryNTimes`（同步链）—— 无该形态。
- **信心水平**: 确定（逃逸路径代码可证；`Error` 触发条件罕见）
- **误报排除**: 非泛泛的"建议 catch Throwable"风格化意见——具体指认了「一次性调度 + 返回值被丢弃 + 无 afterExecute」三要素叠加导致零观测挂死，且写出了触发时序。
- 复核状态: 保留(P2)｜新发现——回码核验成立：`dispatch` 兜底止于 `catch (Exception)`（L276）、`scheduleRetry` 丢弃返回的 `ScheduledFuture`（L443-445）、STPE 无 afterExecute 直核；跨单元判级 tension 登记：WI 1.7 C6-001 同形态（Error 逃逸 future 永不完成）为 P1，差异在彼处伴生持久 RUNNING 任务行+资源不关闭（多层后果），本条为纯内存悬挂且触发面更窄（仅 serialize/resolveBaseUrl 段），P2 可辩护；修复应随 WI 2.1 C3-001（周期任务设施无统一异常绝缘层，29 站点族）平台绝缘层同批收口

### [C4-004] 重试调度器无关闭路径：bean 未注册 stop 钩子，`setRetryScheduler` 换装后旧内部池成永久孤儿线程（F8）

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/EmbeddingServiceImpl.java:88-142,122-124`；`nop-ai/nop-ai-core/src/main/resources/_vfs/nop/ai/beans/ai-defaults.beans.xml:12-13`
- **证据片段**:
  ```java
  // EmbeddingServiceImpl.java:130-141 —— 惰性创建，daemon+命名，但全类无 shutdown
  synchronized (schedulerLock) {
      if (this.retryScheduler == null) {
          ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, r -> {
              Thread t = new Thread(r, "nop-ai-embedding-retry");
              t.setDaemon(true);
              return t;
          });
          executor.setRemoveOnCancelPolicy(true);
          this.retryScheduler = executor;
      }
      return this.retryScheduler;
  }
  // ai-defaults.beans.xml:12-13 —— bean 定义无 ioc:stop / destroy-method
  <bean id="nopAiEmbeddingModel" class="io.nop.ai.core.service.EmbeddingServiceImpl" ioc:default="true"
        ioc:type="io.nop.ai.core.api.embedding.IEmbeddingModel"/>
  ```
- **严重程度**: P2
- **并发场景**: IoC 容器生命周期线程（bean 装配/卸载）与调度器 worker 线程；测试/运维路径调用 `setRetryScheduler` 换装（setter javadoc 自述"传入 null 恢复默认"，L118-121）。
- **触发时序**: T1：应用运行中发生过至少一次 embedding 重试 → 内部池 A 已惰性创建（core=1，`allowCoreThreadTimeOut` 默认 false，worker 线程常驻）→ T2：容器重启/热换装或测试代码调用 `setRetryScheduler(customB)` 再 `setRetryScheduler(null)` → T3：池 A 失去唯一引用，但其 core 线程永不终止（无 shutdown 调用点）→ 交叠后果：每轮「用过重试→换装」遗留一个名为 `nop-ai-embedding-retry` 的常驻孤儿线程及排队任务；容器在逃任务（在途退避重试）不被 drain 即被放弃。另 `setRemoveOnCancelPolicy(true)`（L137）为死配置——全类无任何任务取消调用。
- **现状**: 内部调度器创建后无任何 shutdown/awaitTermination 路径，bean 定义也未注册销毁钩子；换装 setter 不处理旧池。
- **风险**: 单实例长驻应用内影响低（daemon 线程、单池）；容器重启、测试套件反复装配、运维换装场景下线程逐步累积且在途重试静默丢弃，违背池生命周期与持有者生命周期对称（F8）。
- **建议**: 优先复用平台设施 `GlobalExecutors.globalTimer()`（nop-ai-agent `ScheduledRecoveryManager.java:199` javadoc 已引用该约定），彻底移除私有池；若保留，则实现 `stop()`/`destroy()`（`shutdown()+awaitTermination`）并在 beans.xml 注册 `ioc:stop`，`setRetryScheduler` 换装时对旧内部池执行 shutdown。
- **失效面枚举**（F3）: ① `EmbeddingServiceImpl` 内部调度器 —— 本条缺陷主体（模块内唯一自建池）；② `ChatServiceImpl`、`persist/*`、`prompt/*`、`DefaultChatLogger`、reliability 包 —— 全模块扫描无其他自建池/线程，无同族；③ beans.xml 全部 6 个 bean —— 仅 `nopAiEmbeddingModel` 持有线程设施，其余无需销毁钩子。
- **信心水平**: 确定（孤儿线程与缺销毁钩子由代码+bean 定义直接证明；daemon 标记与命名规范的缓解作用已如实计入定级）
- **误报排除**: 非"daemon 线程即无害"式放过，也非对长驻单例池的过度要求——缺陷点是关闭钩子完全缺失 + 换装路径存在具体孤儿时序，均为 C4 步骤 3 明确要求核查的形态。
- 复核状态: 保留(P2)｜新发现——回码核验成立：全类无任何 shutdown 调用点、`ai-defaults.beans.xml` 六 bean 中仅 `nopAiEmbeddingModel` 持线程设施且定义无 `ioc:stop`/destroy-method、`setRetryScheduler`（L122-124）换装不处理旧池直核；daemon+命名+单池的缓解已如实计入；裸 JDK 池属 WI 2.1 CL-021 移交面（ai-agent 点名扩及 nop-ai-core 同族）；修复建议「复用 `GlobalExecutors.globalTimer()`」须与 WI 2.1 C4-001（globalTimer 死配置 maxPoolSize=0 + 4 线程全平台共享定时器阻塞放大，P1）协调——不得先于该平台修复落，否则新增消费方会复刻 WI 1.5 MA-04 占用形态

### [C4-005] Embedding 重试/账号 failover 链对调用方取消完全不可达：无 `ICancelToken` 参数、调度任务不可取消 → 调用方放弃后继续打满重试与账号链

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/EmbeddingServiceImpl.java:144-177,233-251,344-378,443-445`
- **证据片段**:
  ```java
  // EmbeddingServiceImpl.java:145-146,156-157 —— 接口签名无 ICancelToken（对照 ChatServiceImpl.callAsync 有）
  public CompletionStage<VectorData> embedAsync(io.nop.ai.core.api.document.AiDocument doc,
                                                EmbeddingOptions options) {
  ...
  // :352-354 —— RETRY 路径调度后无任何取消句柄/取消检查
  if (outcome.isRetry()) {
      long delayMs = Math.max(0, outcome.getDelayMs());
      scheduleRetry(delayMs, () -> dispatch(future));
      return;
  }
  ```
- **严重程度**: P2
- **并发场景**: 调用方线程（超时放弃后继续运行）与 `nop-ai-embedding-retry` 调度线程上的在途退避任务；每次放弃的调用在调度器 delayed 队列中仍持有 `EmbedCall` 强引用（含 `texts` 全量载荷）。
- **触发时序**: T1：调用方对 `embedAllAsync` 设置超时/放弃（future 被丢弃或 cancel——`CompletableFuture.cancel` 只改本地状态）→ T2：`StandardRetryPolicy` 退避到期，`dispatch` 照常构建并发送付费 LLM embedding 请求（L263 `fetchAsync`）；分类 FALLBACK 时 `accountChain.next()`（L363）继续遍历账号链直至 `MAX_FALLBACK_STEPS=16`（L80、L358）→ 交叠后果：每次调用方放弃触发最多 `maxAttempts(3) × 账号链` 次无效上游调用（计费+限流额度占用），且调度队列滞留大 payload 引用延长 GC 压力；批量索引作业大面积失败时放大为重试风暴。
- **现状**: `IEmbeddingModel` 全部异步方法无取消通道；`EmbedCall` 无取消标志；`scheduleRetry` 返回 void 无句柄；重试循环不检查"调用方是否已不再关心"。
- **风险**: 非泄漏（每次调用有界：`StandardRetryPolicy` 默认 3 次、链 16 步封顶），但在配额耗尽/账号链耗尽的故障场景中，被放弃的调用仍持续消耗配额与费用，延长恢复窗口；与同模块 `ChatServiceImpl.callAsync(request, cancelToken)` 的平台惯例不一致。
- **建议**: 为 `embedAsync/embedAllAsync` 增加 `ICancelToken` 参数（对齐 `IChatService`），`dispatch` 入口与 `handleFailure` RETRY 分支检查取消并短路；`scheduleRetry` 保留 `ScheduledFuture` 句柄，取消时 `cancel(false)`（`removeOnCancelPolicy(true)` 已就位却从未被使用，见 C4-004）。
- **失效面枚举**（F3）: ① `IEmbeddingModel.embedAsync/embedAllAsync` 两入口 —— 均无取消通道，本条覆盖；② `EmbeddingServiceImpl.handleFailure` RETRY/FALLBACK 两分支 —— 均无取消检查，本条覆盖；③ `ChatServiceImpl.callAsync/callStream` —— 已携带 `ICancelToken` 并注册取消任务（对照组，平台惯例存在且可行）；④ `AiCommand` 重试链 —— 经 `cancelToken.isCancelled()` 在 `callChat:334-337` 检查，已有取消感知（无同族缺陷）。
- **信心水平**: 确定（无取消通道为签名级事实；浪费规模由两处封顶常量证明）
- **误报排除**: 非把"无界重试"虚报为 OOM/泄漏（有封顶，故 P2 而非 P1）；报告的是取消语义缺失这一可证接口缺陷 + 具体资源浪费时序，而非风格偏好。
- 复核状态: 保留(P2)｜新发现——回码核验成立：`embedAsync`/`embedAllAsync` 签名无 `ICancelToken`（L145/L156）、`handleFailure` RETRY 分支调度后无取消检查（L352-355）、封顶常量 `DEFAULT_MAX_ATTEMPTS=3`（StandardRetryPolicy:58）与 `MAX_FALLBACK_STEPS=16`（L80）直核；对照组 `AiCommand.callChat:334` 确有 `cancelToken.isCancelled()` 检查（平台惯例存在且可行，同族豁免正确）

## 发现（批次 X2/3 续：nop-ai-agent 六包）

### 批次 X2/3 线程池清单表（nop-ai-agent：security/plan/engine/runtime/model/reliability）

| 池 / 线程设施 | 位置 | 参数 | 队列 | 拒绝策略 | 关闭路径 | 判定 |
|---|---|---|---|---|---|---|
| `DefaultAgentEngine` agentExecutor（cached，惰性） | `DefaultAgentEngine.java:469-479` | cached（max 无上界），daemon，命名 `nop-ai-agent-exec`（**无索引后缀，与 owner doc `nop-ai-agent-exec-*` 不符**） | SynchronousQueue | 永不拒绝（无限建线程） | `close():1135-1143`（仅 shutdown，无 awaitTermination；仅 own 标志为真时）；引擎为 Builder 编程式装配，销毁钩子注册责任在装配方（范围外） | 缺陷（C4-006） |
| `DefaultAgentEngine` backgroundExecutor（cached，Builder 期） | `DefaultAgentEngine.java:640-648` | 同上，命名 `nop-ai-background-task`（无索引） | SynchronousQueue | 永不拒绝 | `close():1144-1152`（own 标志）；任务注册表本体在 tool 包（X3 面） | 缺陷面同 C4-006（注入固定池嵌套），主体归 X3 |
| `SessionLockRenewal` lockRenewExecutor（STPE 单线程，惰性） | `SessionLockRenewal.java:44-54` | core=1 daemon 命名 `nop-ai-agent-lock-renew`；**全会话共享单线程** | STPE 无界 delayed 队列（fixedDelay 不堆积但会延迟） | 无拒绝面 | `DefaultAgentEngine.close():1126-1134`（own 标志） | 缺陷（C4-007/C4-008） |
| `SingleTurnExecutor` 缺省 timeoutExecutor | `SingleTurnExecutor.java:40-43,54-60` | 缺省 `ForkJoinPool.commonPool()`（向后兼容构造器与 null 兜底） | commonPool 共享 | — | — | 缺陷（C4-009） |
| `LlmCallCoordinator` timeoutExecutor（生产接线 = agentExecutor） | `AgentExecutorResolver.java:207`、`LlmCallCoordinator.java:785-809` | 复用 agentExecutor（同池回派） | 同 agentExecutor | 同上 | 同 agentExecutor | 缺陷（C4-006，owner doc 已声明 LLM 回派形态，见该条误报排除） |
| `InMemoryActorRuntime` per-actor 单线程执行器 | `InMemoryActorRuntime.java:301-307` | 每 actor 1 平台线程，daemon，命名 `agent-actor-<uuid>`；1s sleep 轮询；opt-in（缺省 NoOpActorRuntime 关闭） | 无界 queue（单消费者循环） | — | `destroyActorInternal:364-398` shutdownNow+awaitTermination(5s)——**对称良好** | 接受（watch：NoOp 配额下 actor 数无上界 → CL-032/CL-035） |
| `ScheduledRecoveryManager` 注入 `IScheduledExecutor` | `ScheduledRecoveryManager.java:356-375` | 平台设施注入（javadoc 建议注入 `GlobalExecutors.globalTimer()`），start/stop 幂等对称 | 取决于注入池 | — | `stop():367-375` cancel(false) | 缺陷（C4-007 同族站点） |
| `DockerSandboxBackend` per-execution reader Thread | `DockerSandboxBackend.java:254-258` | 每次 execute 新建，daemon，命名 `nop-docker-sandbox-reader`（无索引） | — | — | `awaitReader:580-590` join(≤2s)+interrupt（对阻塞 read 无效，注释已声明边界） | 接受（边界已注释；进程面残留 → CL-034） |
| `NoOpSandboxBackend` per-execution reader Thread | `NoOpSandboxBackend.java:106-109` | 同上，命名 `nop-sandbox-stdout-reader`；killTree 覆盖 descendants | — | — | `awaitReader:187-204`（同上） | 接受（边界已注释声明） |
| （无线程设施确认）`plan`/`model`/`reliability` 全包、`runtime/coordination`（`DbDaemonCoordinator` 纯 SQL CAS）、`DefaultAgentEventPublisher`（CopyOnWriteArrayList 同步派发）、`AgentSessionSupport` | 全 6 包扫描 | 无线程、无池、无调度器 | — | — | — | 无发现 |

### [C4-006] call-agent 子代理委派经 `engine.execute` 在同一 agentExecutor 上嵌套提交并阻塞 join 整个子执行期：注入有界池时负载触发池耗尽互等超时级联；缺省 cached 池下每执行线程占用按委派深度倍增（owner doc 仅声明 LLM 回派形态，委派形态未声明且无运行时守卫）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentCallDelegate.java:92-93`（依赖证据：`AgentExecutorResolver.java:207`、`DefaultAgentEngine.java:469-479,959-961,1000,1079`、`LlmCallCoordinator.java:785-809`、`docs-for-ai/03-modules/nop-ai.md:123`）
- **证据片段**:
  ```java
  // AgentCallDelegate.java:92-93 —— 父代理线程内委派：子执行提交到同一个
  // getAgentExecutor() 池，父线程 orTimeout().join() 阻塞整个子执行期
  CompletableFuture<AgentExecutionResult> future = engine.execute(execRequest);
  AgentExecutionResult result = future.orTimeout(req.getTimeoutMs(), TimeUnit.MILLISECONDS).join();
  // DefaultAgentEngine.java:1079 —— engine.execute 的 supplyAsync 落点即本池
  }, getAgentExecutor());
  // ReActAgentExecutorBuilder.java:586-588 —— javadoc 声称 "virtual threads by
  // default"，实际 :471 为平台线程 cachedThreadPool（文档与实现不符）
  ```
- **严重程度**: P2（注入有界池时升级 P1 级后果：负载 ≥ 池宽必然触发全池互等超时级联）
- **并发场景**: 池 `nop-ai-agent-exec` 内的父 ReAct 循环线程（L1000 已 `join` 阻塞于本执行）在 call-agent 工具处理器内再次向同一池提交子执行并 `join`；缺省池为 cached 平台线程池（无限建线程），`setAgentExecutor`/`Builder.agentExecutor` 接受任意 `ExecutorService` 且无固定池运行时告警/守卫。
- **触发时序**: T1：N 个父代理并发执行，各占 1 个池线程；某父代理 LLM 决策调用 call-agent 工具 → `handleCallAgentRequest` 在父的池线程上运行 → `engine.execute(subRequest)` 向同池提交子执行（需第 2 个线程）→ 父线程 `join()` 阻塞（最长 `callAgentTimeoutMs`=120s）→ T2：委派深度 D 的链路每层同型（`CallAgentExecutor` 深度守卫只限 D，不建新池），加各层 LLM 回派（`callChatWithTimeout:789` 同池再提交 1 线程）→ 单执行谱系并发占用 1+N(在飞 LLM)+D 个池线程；T3（注入固定大小池场景，池宽 W）：W 个父执行占满池线程并全部阻塞于 `join`/`get` → 子任务在固定池的（缺省无界）队列中排队永不启动 → 全部在 120s 超时后级联失败（`orTimeout` 兜底使其为超时风暴而非永久死锁）。交叠后果：有界池配置下负载上升必然触发；缺省 cached 池下线程数随 并发数×(2+深度) 无声明上限增长（每线程 ~1MB 栈保留）。
- **现状**: 三入口 `execute`/`resumeSession`/`wakeSession`/`restoreSession` 全部派发到同一 agentExecutor；call-agent 委派是该池上未声明的第二类同池嵌套（第一类 LLM 回派已被 owner doc `nop-ai.md:123` 明文声明并给出"建议 cached/virtual 池"缓解建议，委派嵌套与恢复类入口派发未声明）；javadoc"virtual threads by default"与实现不符。
- **风险**: 运维按直觉注入 `Executors.newFixedThreadPool`（普通池选择）→ 负载 ≥ 池宽时全池超时级联，agent 服务整体不可用且错误表现为"LLM call timed out"难以归因；缺省池下高并发多级委派放大平台线程占用（千级并发 → 数千线程）。
- **建议**: ① 委派路径与 LLM 回派改用独立执行器或虚拟线程（`Executors.newVirtualThreadPerTaskExecutor()`，与 javadoc 声称对齐）；② `setAgentExecutor` 注入时检测 `getMaximumPoolSize()` 有界性并 LOG.warn 或 fail-fast（把 owner doc 建议升格为守卫）；③ 修正 `ReActAgentExecutorBuilder` javadoc 与实际缺省池的描述。
- **失效面枚举**（F3：同池嵌套提交+阻塞等待全量清扫）: ① `AgentCallDelegate.handleCallAgentRequest:92-93` —— 本条主体（整子执行期嵌套，未声明）；② `LlmCallCoordinator.callChatWithTimeout:789-792` —— 同池回派 LLM 调用，**owner doc 已声明**（"ReAct LLM 超时回派到同一池时有自死锁风险"），随 ① 修复一并收口，不重复立案；③ `SingleTurnExecutor.callWithTimeout:157-158` —— 同回派形态，但生产接线（`AgentExecutorResolver:215-216`）传入专用 executor，且缺省构造器的 commonPool 问题单列为 C4-009；④ `AgentSessionLifecycle.restorePendingSessions:1080-1081` —— 调用方线程（管理/恢复路径）`join`，非池线程嵌套，无同池死锁面；⑤ `DefaultAgentEngine.dispatchExecution:1000`、`AgentSessionLifecycle:386,554,951` —— 池内 `join` 的是 executor.execute 返回的下游 stage（ReAct 循环在同线程同步推进），不构成对同池的二次提交，非本族。
- **信心水平**: 确定（嵌套结构与占用链为代码直接可证；固定池级联链每一步均有 API 语义支撑）
- **误报排除**: 非对 cached 池原语的泛泛报（校准第 2 条）——报告的是「同池嵌套提交+阻塞等待」复合结构与守卫缺失；owner doc 已声明的 LLM 回派形态已在失效面 ② 中按"已声明边界"区分处理，本条主张仅覆盖**未声明**的委派嵌套面与守卫缺失。
- 复核状态: 保留(P2，注入有界池条件下面升 P1 级)｜新发现——高风险条目子项复核：① handler 运行线程——call-agent 工具体经 `ToolManagerImpl.callTool` 的 `executeNow` 惰性直调（未排队路径 inline 于父 agentExecutor 线程；排队路径在 `thenCompose` 完成线程），两种路径下「同池提交子执行+阻塞 join」结构均成立，条目时序取未排队路径成立；② 缺省池为平台线程 cached（L469-479）、`engine.execute` 落点 L1079、javadoc "virtual threads by default"（L588）与实现不符均直核；③ 条件化降档有证据（缺省 cached 池下无死锁，级联需运维注入固定池），维持 P2；家族对照 WI 1.5 C3-002（MA-03 同池嵌套死锁族，P1——彼为 shipped 接线即嵌套，本条缺省不死锁故条件化 P2 成立，修复模式应对齐：独立执行器/虚拟线程）；owner doc 已声明/未声明形态的区分复核维持

### [C4-007] 会话租约续期与恢复扫描两个周期任务只捕 `RuntimeException`：`Error` 逃逸后按 JDK 语义静默停止调度且不可感知——续期停转使租约 30min 后过期被他实例抢占而本地执行不中止（double-execution），恢复扫描停转使孤儿会话检测失明（F9）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/SessionLockRenewal.java:65-78`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/runtime/recovery/ScheduledRecoveryManager.java:383-397`
- **证据片段**:
  ```java
  // SessionLockRenewal.java:69-77 —— 兜底只到 RuntimeException
  try {
      boolean renewed = config.getSessionTakeoverLock().tryRenew(sessionId, ownerId, config.getLockLeaseMs());
      if (!renewed) { handleLeaseLost(handle, sessionId, ownerId); }
  } catch (RuntimeException e) {
      LOG.warn("... (will retry next interval): {}", ...);   // Error 直接穿透
  }
  // ScheduledRecoveryManager.java:393-396 —— 同型
  } catch (RuntimeException e) {
      LOG.warn("ScheduledRecoveryManager: periodic scan failed ...", e.toString());
  }
  ```
- **严重程度**: P2
- **并发场景**: `nop-ai-agent-lock-renew` 单调度线程执行每会话 `renewOnceSafe`；注入调度器线程执行 `scanOnceSafe`；两处均为 `scheduleWithFixedDelay` 周期任务——JDK 语义：任务抛出任意 Throwable（含 Error）后该周期任务被永久取消，异常仅存入返回的 `ScheduledFuture`，无人读取即零日志零通知。
- **触发时序**: T1：会话执行中，续期任务（间隔 10min）到期执行 `tryRenew`，JDBC 驱动/堆内存抛出 `Error`（如 `NoClassDefFoundError`、OOM 边缘 `OutOfMemoryError`、驱动 `StackOverflowError`）→ 穿透 `catch (RuntimeException)` 逃逸 → T2：调度器按 JDK 语义取消该周期任务：后续续期永不再执行、无任何日志；`handle.renewHandle` 持有该 future 但全代码无异常检查点 → T3：本侧执行照常继续，租约 `lockLeaseMs`=30min 到期 → 他实例 `tryAcquire` 条件 UPDATE 命中 `LOCK_EXPIRES_AT <= now` 分支（`DbSessionTakeoverLock.java:203-224`）接管并执行 → 双执行，而设计用于防止该后果的 `handleLeaseLost` 中止路径（L79-91）永不触发。交叠后果：takeover lock 机制在其设计目标场景（实例故障/抢占）下静默失效。恢复扫描同型逃逸后孤儿会话/超时会话检测整体失明，同样零日志。
- **现状**: 两个周期任务的最外层兜底均只到 `RuntimeException`；正常 `RuntimeException`（DB 抖动）路径已正确 warn 重试，唯 `Error` 路径既不中止执行也不可观测。
- **风险**: 罕见触发但后果是该机制存在的唯一目的（双执行）的反向失效；长时运行 + 热部署/OOM 边缘环境为现实触发面；失效完全静默（对照 F9："scheduleWithFixedDelay 任务抛异常后是否停止调度且该停止是否被感知"——未感知）。
- **建议**: 兜底改 `catch (Throwable)`：续期路径对 `Error` 直接走 `handleLeaseLost`（宁可误中止不可双执行）；恢复路径对 `Error` 记 ERROR 级日志后重抛给调度器包装层；或为两个调度器统一加 `afterExecute` 钩子落日志（对照批次 X1 C4-003 建议的同款收口）。
- **失效面枚举**（F3：范围内周期任务 catch 面全量清扫）: ① `SessionLockRenewal.renewOnceSafe:74` —— 本条主体；② `ScheduledRecoveryManager.scanOnceSafe:393` —— 本条同族（后果：恢复检测失明）；③ `InMemoryActorRuntime.runConsumptionLoop:521`（catch Exception → FAILED）—— `Error` 逃逸后循环线程死亡且 actor 状态滞留非终态（不转 FAILED），至引擎 finally `destroyActor` 前为僵尸 actor（登记 CL-035，后果轻故不并列）；④ `DBMessageService` poller（`scheduleWithFixedDelay`，L213-217）与 `TeamTaskSchedulerDaemon`（L237）—— 同族站点，属 `message`/`team` 包，归批次 X3 审读（本批次仅登记线索不立案）。
- **信心水平**: 确定（JDK 周期任务异常语义 + 两处 catch 边界 + `renewHandle` future 无读取点，均可代码直证；`Error` 触发条件本身罕见）
- **误报排除**: 非泛泛的"建议 catch Throwable"——具体指认「周期任务 + 兜底止于 Exception + 返回 future 无人读」三要素叠加导致"静默停转不可感知"，并给出与批次 X1 C4-003（同类判定 P2）一致的触发时序；`RuntimeException` 路径的正确重试行为已如实计入（不否定现有 warn-retry 设计）。
- 复核状态: 保留(P2)｜新发现——回码核验成立：`renewOnceSafe` 兜底止于 `RuntimeException`（L74）、`ScheduledRecoveryManager.scanOnceSafe` 同型（L393-396）、`renewHandle` 全代码无异常读取点、`lockLeaseMs=1_800_000`/`lockRenewIntervalMs=600_000`（DefaultAgentEngineConfig:154-155）直核；**追加登记**：同站点 C3-020 为 P3——与本条构成第二处 P3/P2 分叉（与 C3-027↔C4-013 同型，任务指派未列，本复核补登记）留 7.1；C4 侧意见：P2 成立（后果为 takeover lock 设计目标的反向失效、失效完全静默），且 C3-020/C3-027 与本条、C4-013 应一并并入 WI 2.1 C3-001 平台周期任务绝缘层修复批次（29 站点族扩展 nop-ai 站点）

### [C4-008] 全部会话的租约续期共享单线程调度器且续期为同步 JDBC I/O：DB 延迟尖峰下队头阻塞使后排会话续期实际执行推迟，超过 2 个间隔（20min）即租约过期——跨实例抢占/误中止级联（租约 30min 仅 3 倍裕量）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/SessionLockRenewal.java:44-54,61-63`（依赖证据：`DbSessionTakeoverLock.java:286-316`、`DefaultAgentEngineConfig.java:154-155`）
- **证据片段**:
  ```java
  // SessionLockRenewal.java:46-50 —— 每引擎仅一个单线程调度器，全会话共享
  lockRenewExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "nop-ai-agent-lock-renew");
      t.setDaemon(true); return t;
  });
  // :62-63 —— 每会话一个 fixedDelay 周期任务入同一队列；任务体为同步 JDBC UPDATE
  return exec.scheduleWithFixedDelay(() -> renewOnceSafe(handle, sessionId, ownerId),
          config.getLockRenewIntervalMs(), config.getLockRenewIntervalMs(), TimeUnit.MILLISECONDS);
  ```
- **严重程度**: P2
- **并发场景**: 单个 `nop-ai-agent-lock-renew` 线程串行执行所有在执行会话的续期任务；每个任务内 `tryRenew` 做一次同步 JDBC UPDATE（`DbSessionTakeoverLock.tryRenew`，持连接池连接）；DB 慢/连接池争用时单次续期耗时不可控，`scheduleWithFixedDelay` 只防堆积不防延迟。
- **触发时序**: T1：多实例部署接入 `DbSessionTakeoverLock`，M 个会话并发执行，续期任务以 10min 间隔入单线程队列 → T2：DB 延迟尖峰（连接池争用/慢查询/网络抖动），单次 `tryRenew` 从毫秒级恶化到数十秒级，队列中后排会话的续期被队头阻塞逐个推迟 → T3：某会话续期实际执行时刻较计划推迟累计 >20min（2 个间隔）→ 其租约（30min）已过期 → 交叠后果二选一：(a) 他实例 `tryAcquire` 命中 expired 分支接管 → 双执行窗口；(b) 本侧续期此刻执行 `tryRenew` 返回 false（行已被抢占删除）→ `handleLeaseLost` 误中止本侧健康执行（session 置 failed）。M 越大或 DB 越慢，级联面越大。
- **现状**: 续期为硬实时需求（必须在租约窗口内完成）却部署在与其他全部会话共享的单线程上，无池隔离、无超时控制（JDBC 无 statement timeout 设置）、无积压可观测；裕量系数仅 3（30min/10min）。
- **风险**: 多实例生产部署 + DB 抖动场景下由"单会话锁失效"放大为"批量会话锁失效"（双执行或误中止风暴）；故障后无指标可区分"DB 慢"与"锁被抢占"。
- **建议**: ① 续期调度器改用平台 `GlobalExecutors.globalTimer()` 或小型多线程（2-4）调度池，消解单点队头阻塞；② `tryRenew` 的 JDBC 语句加超时（`Statement.setQueryTimeout`）防单次慢调用拖垮整队；③ 暴露"上次续期成功时间 vs 计划时间"滞后指标（对照 F5：挂死/滞后对健康检测可见）。
- **失效面枚举**（F3：共享调度线程上的阻塞 I/O 全量清扫）: ① `SessionLockRenewal` 单线程续期调度器 —— 本条主体；② `ScheduledRecoveryManager` 若注入共享 `globalTimer`，`scanOnce` 的 3 段无超时 JDBC 查询（L480-550）同样占线程延迟同池其他任务 —— 同形态，注入专用调度器时可缓解，随 ① 建议一并评估；③ `DefaultAgentEngine` agentExecutor —— LLM I/O 与工具 I/O 与循环共存，但为 cached 多线程模型且非硬实时需求，无单点队头，非本族；④ `DBMessageService` poller / `TeamTaskSchedulerDaemon`（message/team 包，基线 C4b L213-217/L237）—— 单线程 + DB 轮询同族站点，归批次 X3。
- **信心水平**: 很可能（结构与裕量为代码+配置直证；触发依赖 DB 延迟 × 会话规模的组合条件，未在代码路径中直接可见）
- **误报排除**: 非对 `scheduleWithFixedDelay` 原语的误用指控（fixedDelay 防堆积正确）——报告的是「硬实时续期 × 单线程共享 × 无超时 JDBC」的隔离缺失；缺省 `NoOpSessionTakeoverLock` 下 tryRenew 为内存 no-op 无 I/O，本条仅在接入功能性 DB 锁（设计支持的多实例部署形态）时成立，已在时序中限定。
- 复核状态: 保留(P2)｜新发现——回码核验成立：全会话共享单线程调度器（L44-50）、每会话 `scheduleWithFixedDelay` 入同一队列（L61-63）、`DbSessionTakeoverLock.tryRenew` 为同步条件 UPDATE（JDBC 无语句超时设置）、30min/10min 仅 3 倍裕量均直核；降档口径核对：触发依赖「DB 延迟尖峰 × 多实例部署 + 功能性 DB 锁」组合条件，非纯负载驱动，P2 成立、信心水平"很可能"标注恰当；**修复建议修正**：建议① 中「改用 `GlobalExecutors.globalTimer()`」与 WI 2.1 C4-001 冲突（该设施死配置 maxPoolSize=0 + 4 线程全平台共享阻塞放大，P1；WI 1.5 MA-04 同案方向是隔离而非汇聚）——硬实时续期上共享 globalTimer 会复刻 MA-04 占用形态，应取其备选项（小型专用多线程调度池 + 语句超时 + 滞后指标）为主方向，7.1 协调

### [C4-009] `SingleTurnExecutor` 向后兼容构造器与 null 兜底缺省 `timeoutExecutor = ForkJoinPool.commonPool()`：阻塞 LLM HTTP 调用（最长 120s）运行在 JVM 全局共享池上，与 `DefaultAgentEngine` 自述的 commonPool 饥饿规避理由直接相悖

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/SingleTurnExecutor.java:40-43,54-60,156-177`（对照证据：`DefaultAgentEngine.java:959-960`）
- **证据片段**:
  ```java
  // SingleTurnExecutor.java:41-42 —— 向后兼容构造器：commonPool
  public SingleTurnExecutor(IChatService chatService, IAgentEventPublisher eventPublisher) {
      this(chatService, eventPublisher, 120_000L, ForkJoinPool.commonPool());
  }
  // :59 —— 全参构造器的 null 兜底同样回落 commonPool
  this.timeoutExecutor = timeoutExecutor != null ? timeoutExecutor : ForkJoinPool.commonPool();
  // DefaultAgentEngine.java:959-960 —— 同模块自述："commonPool defaults to
  // ~3-7 threads JVM-wide"，故 execute 派发避开 commonPool —— 本类缺省与之相悖
  ```
- **严重程度**: P2
- **并发场景**: 每路 single-turn 代理执行经 `callWithTimeout:157-158` 把阻塞的 `chatService.call`（同步 HTTP，缺省上限 120s）提交到 commonPool；调用线程（agent-exec 池线程）同时阻塞在 `f.get(120s)`——双线程占用面之一落在 JVM 全局池。生产接线（`AgentExecutorResolver:215-216`）传入专用 executor 不受影响；暴露面为直接使用向后兼容构造器与传 null 的调用方（含测试/嵌入方）。
- **触发时序**: T1：嵌入方以 `new SingleTurnExecutor(chatService, publisher)` 构造（公开 API）并发起 N 路 single-turn 执行 → 每路在 commonPool 占 1 线程直至 LLM 返回（秒级到 120s）→ T2：N > commonPool parallelism（cores-1 ≈ 3-7）时 commonPool 队列积压 → T3：全平台 commonPool 使用方（并行流、一切未显式指定 executor 的 `CompletableFuture` 异步任务）被 AI 慢 I/O 阻塞饿死；上游 LLM 超时时 `future.cancel(true)` 中断对阻塞 socket read 不生效（L174），线程仍滞留至 HTTP 层自身超时。交叠后果：平台级共享池被单模块 LLM I/O 占用——与同模块 `DefaultAgentEngine:959-960` 注释陈述的规避理由自相矛盾。
- **现状**: 缺省 executor 落 commonPool 的公开构造面未收敛；同文件 javadoc（L46-53）只声明超时语义，未声明缺省池的共享占用代价。
- **风险**: 嵌入式使用（nop-ai-agent 定位为可嵌入引擎，owner doc store 层边界一节明示）下按最短构造器接入即触发；平台共享池饥饿的影响面远超本模块。
- **建议**: 缺省改用平台设施 `GlobalExecutors.globalWorker()`（nop-http `ServerEventPublisher` 既有约定）或虚拟线程 per-task executor；至少在两处缺省点加 LOG.warn 声明共享池占用；javadoc 补缺省池语义。
- **失效面枚举**（F3：范围内缺省-executor 站点全量清扫）: ① `SingleTurnExecutor` 1 参构造器 :42 —— 本条主体；② `SingleTurnExecutor` 全参构造器 null 兜底 :59 —— 同族，随 ① 一并收口；③ `LlmCallCoordinator.callChatWithTimeout:786-787` —— `timeoutExecutor == null` 分支为调用线程直接同步调用（不落 commonPool），非同族；④ `AgentExecutorResolver:207,215-216` —— 生产接线显式传专用 executor（对照组，平台惯例存在且可行）；⑤ 批次 X1 C4-001（nop-ai-core `ChatServiceImpl.callStream` 缺省 SubmissionPublisher → commonPool）—— 同缺省池家族的 nop-ai-core 侧站点，已立案，不重复。
- **信心水平**: 确定（缺省值为代码直证；触发链为 API 语义推演，生产主路径不受影响的事实已计入定级）
- **误报排除**: 非报告 commonPool 原语本身（校准第 2 条），报告的是公开构造面的缺省池治理缺陷；生产接线不受影响已如实声明并据此维持 P2 而非 P1（对照 X1 C4-001 同族判级）。
- 复核状态: 保留(P2)｜新发现——回码核验成立：两缺省点（L42 向后兼容构造器、L59 null 兜底）均落 commonPool、生产接线 `AgentExecutorResolver:215-216` 传 `agentExecutorSupplier.get()` 专用 executor、`DefaultAgentEngine:958-960` 自述 commonPool 饥饿规避理由均直核；现状补充：同族前案 2026-06-15-1146 nop-ai-agent 审计维度14-10（P3，`DefaultAgentEngine:661` supplyAsync 缺省 commonPool）在现网代码已改为 `getAgentExecutor()` 接线（实质修复），本条为其残余不同站点（SingleTurnExecutor 公开构造面），非重复立案；与 C4-011（P1）的判级梯度成立（本条非生产接线、C4-011 为生产主路径）；WI 2.1 CL-021 移交面

## 发现（批次 X3/3 续：nop-ai-agent 团队编排执行器 + 治理原语线程面，C4 收官批）

### 批次 X3/3 线程池清单表（nop-ai-agent：team/tool/message/session/memory + 16 小包 + 根）

| 池 / 线程设施 | 位置 | 参数 | 队列 | 拒绝策略 | 关闭路径 | 判定 |
|---|---|---|---|---|---|---|
| `TeamTaskFlowOrchestrator` ownedSpawnExecutor（fixed，惰性） | `TeamTaskFlowOrchestrator.java:468-478` | fixed `max(2, cpus)`，daemon，命名 `ai-agent-spawn-worker-N`（有索引） | 无界 `LinkedBlockingQueue`（`newFixedThreadPool` 缺省） | 永不拒绝（队列无界） | `close():448-458`（shutdownNow+awaitTermination(2s)）**存在但生产代码零调用点**（唯一生产构造点 `TeamExecuteFlowExecutor.java:234` 为 per-invocation 构造后丢弃） | 缺陷（C4-010/C4-015） |
| `TaskDispatchCoordinator` ownedSpawnExecutor（daemon 侧 fixed，惰性） | `TaskDispatchCoordinator.java:113-123` | 同上，命名 `ai-agent-daemon-spawn-worker-N` | 无界 | 永不拒绝 | `TeamTaskSchedulerDaemon.stop():257` → `shutdownOwnSpawnExecutor():102-107`（仅 shutdownNow，无 awaitTermination；queued 任务被丢弃） | 缺陷（C4-015） |
| `DBMessageService` poller（单线程 STPE，惰性） | `DBMessageService.java:208-218` | core=1，daemon，命名 `db-message-poller-<consumerId>`；**双周期任务（50ms poll + 60s sweep）+ 全部 topic 的 inline 消费投递共享该线程** | STPE 无界 delayed 队列（fixedDelay 不堆积但会延迟） | 无拒绝面 | `close():228-241` shutdownNow+awaitTermination(5s)——对称；start/close 为显式方法，bean 钩子注册责任在装配方 | 缺陷（C4-013；隔离面 CL-040） |
| `AgentDispatchExecutor`（工具分发执行器适配器） | `AgentDispatchExecutor.java:32-53`（接线 `AgentToolDispatcher.java:560`） | 缺省 `ForkJoinPool.commonPool()`（`CompletableFuture.runAsync` 一参重载），无自建池 | commonPool 共享提交队列 | — | 无自有生命周期（共享池适配器，javadoc 自述） | 缺陷（C4-011） |
| `DefaultBackgroundTaskRegistry.backgroundExecutor`（引擎接线 cached） | `DefaultBackgroundTaskRegistry.java:70,110-116` + `DefaultAgentEngine.java:640-648` | cached（X2 清单已列池参数）；注册表条目 `tasks` map **无任何 remove** | cached SynchronousQueue | `RejectedExecutionException` → `IllegalStateException` fail-loud（`:128-129`，正确） | 池随引擎 close（X2 面）；**条目账本无清理、`timeoutMs` 无强制执行点** | 缺陷（C4-012） |
| `MemberFanOutDispatcher` / `SpawnMemberAgentTaskStep` 借用 spawn 池 | `MemberFanOutDispatcher.java:368-384`、`SpawnMemberAgentTaskStep.java:232-241` | 复用 orchestrator/coordinator 的 spawn 池 | 同上 | 同上 | 同上 | 同 C4-010/C4-015 面 |
| （无线程设施确认）`session`/`memory` 全包、16 小包（budget/compact/completion/conflict/contribution/fencing/guardrail 含 rule//hook/middleware/quota/recipe/repair/router/skill/talent/usage）、根 3 文件、`DbTeamManager`/`DbTeamTaskStore`/`InMemoryTeam*`、`TeamExecuteFlowExecutor` 等其余工具执行器 | 全分区正则扫描 | 无线程、无池、无调度器；compact 为 ReAct 循环内同步触发 | — | — | — | 无发现 |

### [C4-010] `TeamExecuteFlowExecutor` 每次工具调用构造 per-invocation `TeamTaskFlowOrchestrator` 且从不调用 `close()`：含 spawn 节点的每次 team-execute-flow 成功执行惰性泄漏一个 fixed spawn 线程池（max(2,cpus) 个常驻 core 线程，永不 shutdown）——F8 池生命周期与持有者生命周期彻底不对称

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/tool/TeamExecuteFlowExecutor.java:230-256`（依赖证据：`team/flow/TeamTaskFlowOrchestrator.java:206,215,448-458,468-478`）
- **证据片段**:
  ```java
  // TeamExecuteFlowExecutor.java:234-236 —— DD#3 per-invocation 构造，未注入 spawnStepExecutor
  TeamTaskFlowOrchestrator orchestrator = new TeamTaskFlowOrchestrator(
          engine, taskStore, teamManager, null, memberSpawner, null, memberExecTimeoutMs);
  ...
  return flowFuture.thenApply(result -> mapResult(call.getId(), result));
  // 方法终结 —— 全文件无 orchestrator.close()；全仓库 main 源码无任何 close() 调用点
  // TeamTaskFlowOrchestrator.java:472-475 —— 首个 spawn 节点即惰性建池（fixed，core 线程不超时）
  int poolSize = Math.max(2, Runtime.getRuntime().availableProcessors());
  ownedSpawnExecutor = Executors.newFixedThreadPool(poolSize, factory);
  ```
- **严重程度**: P1（正常 spawn 路径每次调用即泄漏；daemon 标记缓解 JVM 退出悬挂但不停止累积）
- **并发场景**: 每次 team-execute-flow 工具调用（运行在工具分发面，见 C4-011）构造一个一次性 orchestrator；DAG 含 SPAWN 目标时（`DefaultTaskMemberRouter` bound 优先 + spawn 兜底，团队无 bound 成员而有 memberSpecs 即触发——auto-spawn 是文档化的正常业务路径），图构建期 `resolveSpawnExecutor()`（`TeamTaskFlowOrchestrator.java:930,947,957`）惰性创建池，节点触发时 supplyAsync 提交使 fixed 池按需启动 core 线程。
- **触发时序**: T1：一次 team-execute-flow 调用进入，团队任务节点路由为 SPAWN 目标 → 图构建时创建池 A（对象仅被本 orchestrator 持有）→ T2：节点触发，`SpawnMemberAgentTaskStep.execute` 向池 A 提交 supplyAsync，池 A 启动 1..max(2,cpus) 个 `ai-agent-spawn-worker-N` core 线程（`allowCoreThreadTimeOut` 缺省 false，任务完成后线程永久 park 存活）→ T3：DAG future 完成，`flowFuture.thenApply` 返回，orchestrator 与池 A 失去全部引用——但 core 线程是 GC root（ThreadGroup 持有），池 A 不可回收 → 下一次调用重复 T1-T3 再泄漏一池。交叠后果：泄漏量 = 调用次数 × max(1, min(并发 spawn 节点数, cpus)) 个常驻线程；长期运行的生产 agent 服务（每会话触发 team 流程）线性累积至 OS 线程上限（数千），`OutOfMemoryError: unable to create native thread`。
- **现状**: `close()` 已实现（shutdownNow+awaitTermination(2s)，L448-458）且 javadoc 明文要求 "Long-lived orchestrators should call this on disposal"，但唯一生产构造点从未调用——工具执行器框架（`IToolExecutor.executeAsync`）无销毁回调可用，per-invocation 持有池的模式与该框架根本不兼容。
- **风险**: 每调用泄漏有界（≤cpus 线程）但调用次数无界；对比 X1 C4-004（单例孤儿池，P2）量级放大 N 倍；线程名带索引可观测（jstack 可见 `ai-agent-spawn-worker-*` 随调用数增长），但无任何告警机制。
- **建议**: spawn 池上提为引擎级共享单例（对齐 daemon 侧 `TaskDispatchCoordinator` 模式：每 daemon 一池、`stop()` 关闭），orchestrator 构造器接受注入的 `spawnStepExecutor`（setter 已存在，`:421-423`）并由 `TeamExecuteFlowExecutor` 从 `AgentToolExecuteContext` 透传引擎共享池；若保留 per-invocation 池，则在 `flowFuture.whenComplete` 终态回调中调用 `orchestrator.close()`（此时在途 spawn 任务已随 DAG 终态 settle，shutdownNow 无在途风险）。
- **失效面枚举**（F3：`TeamTaskFlowOrchestrator`/spawn 池生命周期全量清扫）: ① `TeamExecuteFlowExecutor:234` per-invocation 构造 —— 本条缺陷主体（生产唯一构造点）；② `TaskDispatchCoordinator:113-123` daemon 侧 owned 池 —— 生命周期对称（`TeamTaskSchedulerDaemon.stop():257` 关闭），无本条缺陷（其 shutdownNow 丢任务面另立 C4-015）；③ 测试代码 `TestTeamTaskFlowOrchestratorAsync` 5 处构造 —— 无 close，daemon 线程不阻塞测试 JVM，不按生产标准立案；④ 注入式 `setSpawnStepExecutor` 路径 —— 注入方持有生命周期，`close()` 正确跳过，无缺陷；⑤ `MemberFanOutDispatcher`/`SpawnMemberAgentTaskStep` —— 池的纯使用方，随 ① 修复一并受益。
- **信心水平**: 确定（构造点唯一性、close 零调用点、core 线程不超时均为代码直证）
- **误报排除**: 非对 daemon 线程"一票否决"式泛报（校准：daemon 缓解已在定级中如实计入）——缺陷点是「per-invocation 持有池 × 框架无销毁回调 × close 已实现但结构性不可达」三要素叠加的成功路径确定性泄漏，javadoc 自述的释放契约（"released by close()"）与实际调用图直接矛盾。
- 复核状态: 保留(P1)｜新发现——回码核验成立：`TeamExecuteFlowExecutor:234` per-invocation 构造（spawnStepExecutor 传 null）、全仓 main 源码 orchestrator `.close()` 零调用点（grep 复验）、`resolveSpawnExecutor` 惰性 `newFixedThreadPool(max(2,cpus))`（core 线程不超时）、`close()` 已实现含 awaitTermination(2s)、spawn worker 命名带索引（`ai-agent-spawn-worker-N`，L492）均直核；「正常 spawn 路径每次调用即泄漏」成立（auto-spawn 为文档化正常业务路径）；同站点跨维双登记 C3-022（P1）成立留 7.1 并单；P1 非 P0 的定级差正确（泄漏有界 per 调用 × daemon 标记，非主请求路径即时耗尽）

### [C4-011] 工具分发执行器 `AgentDispatchExecutor` 落缺省 `ForkJoinPool.commonPool()`：Bash 进程执行/HTTP 请求/文件 I/O 等阻塞工具体（单次最长 toolTimeoutMs=300s）全部占用 JVM 全局共享池——与同模块 `DefaultAgentEngine:959` 的 commonPool 饥饿规避自述直接相悖，负载上升必然平台级共享池饥饿

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/tool/AgentDispatchExecutor.java:32-53`（接线证据：`engine/AgentToolDispatcher.java:552-568`；阻塞消费方证据：nop-ai-toolkit `BashExecutor.java:104`、`HttpRequestExecutor.java:97` 及 15+ 文件族 executor 统一 `context.getExecutor().submit(...)`）
- **证据片段**:
  ```java
  // AgentDispatchExecutor.java:33-34 —— 三方法全部一参重载，缺省 commonPool
  public void execute(Runnable command) {
      CompletableFuture.runAsync(command);          // ← ForkJoinPool.commonPool()
  }
  // AgentToolDispatcher.java:560 —— ReAct 工具分发的唯一执行器装配点
  AgentDispatchExecutor.INSTANCE,
  // javadoc:18-19 自述 "Runs on the shared CompletableFuture.runAsync ForkJoin
  // pool"——但声明的只是无状态/无生命周期，未声明阻塞占用与饥饿边界
  ```
- **严重程度**: P1（负载上升必然触发共享池饥饿；timeout cap 置 0 时升级为永久线程占用）
- **并发场景**: 每路 ReAct 循环的每次工具 fan-out（`AgentToolDispatcher.executeAllowedCalls`，per-tool `orTimeout` 于 agent-exec 线程等待）把工具体提交到 commonPool 执行——工具体是**本质阻塞**的：Bash 进程 `waitFor`（缺省上限 300s，`DefaultAgentEngineConfig.java:161`）、阻塞 HTTP、文件系统 I/O。commonPool parallelism = cores-1（典型 3-7）。
- **触发时序**: T1：N 路并发 agent 执行各自 fan-out 工具调用（N≥2 个会话即常见），每个在途工具占 1 个 commonPool 线程直至完成（秒级到 300s）→ T2：在途工具数 > commonPool parallelism 时后续工具在 commonPool 提交队列排队（无界），`executeAllowedCalls` 的 per-tool deadline 空转到工具实际启动——工具并发吞吐被全局池宽硬封顶 → T3：全平台 commonPool 使用方（并行流、一切未指定 executor 的 `CompletableFuture` 异步、X1 C4-001 的流式派发）被 AI 阻塞工具体饿死。交叠后果：`toolTimeoutPolicy` 将某工具 cap 配 0（`AgentToolDispatcher.java:681-684` "cap <= 0 disables the timeout"，配置合法）时，一个挂死的工具体永久占用 1 个 commonPool 线程，反复触发后池宽逐步归零、全 JVM 异步面停摆。
- **现状**: 缺省执行器为共享池的选择在 javadoc 中自述 "deliberately"，但声明的只是「无状态、无生命周期释放」——阻塞占用量级、池宽封顶、cap=0 永久占用面均未声明，无任何运行时守卫或告警；同模块两处注释（`DefaultAgentEngine.java:959-961`、`ReActAgentExecutorBuilder.java:588`）明确以「避免与 commonPool 用户争用」为理由自建池，本站点与其直接相悖。
- **风险**: 多会话 agent 服务的工具吞吐上限 = commonPool 宽度（与引擎自建 agentExecutor 无关）；平台级共享池污染远超本模块边界；与 C4-009（`SingleTurnExecutor` 缺省 commonPool，P2）同族但本站点是**生产主路径唯一接线**且载荷为确定性阻塞体，故升 P1。
- **建议**: 复用引擎自有 agentExecutor 或按 `ThreadPoolConfig` 自建命名 `IThreadPoolExecutor`（平台 `io.nop.commons.concurrent.executor` 设施，与 `SimpleToolExecuteContext` 的 `IThreadPoolExecutor` 槽位类型天然匹配）注入 `AgentToolDispatcher`；至少在 javadoc 补阻塞占用与池宽封顶边界声明，并对 cap=0 配置加 LOG.warn。
- **失效面枚举**（F3：工具分发面缺省池 + 阻塞体全量清扫）: ① `AgentDispatchExecutor.execute:34` —— 本条主体；② `AgentDispatchExecutor.submit(Callable):40-46`、`submit(Runnable,result):52` —— 同类两方法，随 ① 一并收口；③ toolkit 全部 15+ 工具 executor（Bash/HTTP/文件族）—— 纯消费方，经 `context.getExecutor()` 间接受影响，随 ① 一并修复；④ `DefaultAgentEngine` agentExecutor / backgroundExecutor —— 已自建池（X2 C4-006 面），非本族；⑤ `SingleTurnExecutor` 缺省构造器 —— 同缺省池家族但为兼容构造面非生产接线（C4-009 已立案）；⑥ X1 `ChatServiceImpl.callStream` 缺省 SubmissionPublisher —— 同家族 nop-ai-core 侧（C4-001 已立案），不重复。
- **信心水平**: 确定（缺省池、阻塞体性质、接线唯一性、超时可禁用均为代码直证；「N>池宽」的到达条件由并发会话数平凡满足）
- **误报排除**: 非报告 `CompletableFuture.runAsync` 原语本身（校准第 2 条）；javadoc 的 "deliberately" 声明已按纪律第 6 条检验——其声明范围（无状态/无释放）不覆盖本条主张的阻塞占用与饥饿面，故属「守卫缺失、边界未声明」而非「已声明边界」。
- 复核状态: 保留(P1)｜新发现——回码核验成立：`AgentDispatchExecutor` 三方法一参 `runAsync`（缺省 commonPool）、`AgentToolDispatcher` L560 唯一装配点（prepareDispatchContext）、toolkit `context.getExecutor().submit` 实测 18 文件（条目称 15+，成立）、cap<=0 禁超时为合法配置（L681-684 注释直核）、`toolTimeoutMs=300_000`（DefaultAgentEngineConfig:161）均直核；「生产主路径唯一接线 + 确定性阻塞体 + 池宽封顶」升 P1 与跨单元同族判级一致（WI 1.2 C4-004 Debezium 无限阻塞占 globalWorker P1、WI 2.3 C8-001 WS 推送钉死 globalTimer P1）；同文件 C6/C9 overlay 线索 CL-025（无界 runAsync 面）随本条处置；修复建议注入 `IThreadPoolExecutor` 的槽位类型匹配经 `AgentToolExecuteContext` 构造直核可行

### [C4-012] `DefaultBackgroundTaskRegistry.tasks` 账本只增不删：终态后台任务条目（含 8KB outputTail）永久驻留，且 `timeoutMs` 记录后无任何强制执行点——挂死的后台体永久占 backgroundExecutor 线程且条目永远 RUNNING（F4 + F5）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/tool/DefaultBackgroundTaskRegistry.java:59,97-131,133-170`（接线证据：`engine/DefaultAgentEngine.java:640-663`）
- **证据片段**:
  ```java
  // DefaultBackgroundTaskRegistry.java:59 —— 唯一账本，全文件无 tasks.remove(...)
  private final ConcurrentHashMap<String, Entry> tasks = new ConcurrentHashMap<>();
  // :100 —— submit 仅 put；:127 仅回填 entry.future
  tasks.put(taskId, new Entry(taskId, toolName, sessionId, callId, timeoutMs));
  // Entry:328 —— timeoutMs 逐条记录；全代码库无任何 sweep/比较点读取它强制 TIMEOUT
  public final long timeoutMs;
  ```
- **严重程度**: P1（长时运行必然触发的无界增长 + 挂死任务对生命周期管理不可见）
- **并发场景**: ReAct 工具线程提交后台任务（`runInBackground`），engine 接线的 cached backgroundExecutor 线程执行体；任意调用方线程经 `snapshot`/`readOutput`/`stop` 观测。条目仅在 `submit` 写入，`complete`/`stop` 只做状态迁移，无任何移除路径（grep 全文件仅 `tasks.size():305` 一处读）。
- **触发时序**: T1：agent 会话高频使用 runInBackground（Bash 长任务等），每任务 `tasks.put` 一条（含 ≤8KB `outputTail` + envelope 字符串，约 8.5KB/条）→ T2：任务终态（COMPLETED/FAILED/TIMEOUT）后条目永不回收 → 长驻服务账本单调增长：1 万任务 ≈ 85MB、10 万任务 ≈ 850MB（纯堆内）。并行地：T3：某后台体挂死（非 interruptible 阻塞，`future.cancel(true)` 与 tracker kill 均不可达）→ 无任何组件按 `entry.timeoutMs` 做超时裁定（`STATUS_TIMEOUT` 仅由 toolkit `BashExecutor` 自身进程超时写入），条目永远 RUNNING、占死 1 个 cached 池线程（cached 池核心线程不回收），且对 `snapshot` 观测者无任何「已超时」信号（F5：挂死生产者对健康检测不可见）。
- **现状**: 注册表 javadoc 宣称 "Secure-by-default posture: bounded output capture (8KB tail)"——单条输出有界，但条目数无界且零清理（TTL/上限/淘汰全无）；`timeoutMs` 是纯元数据。
- **风险**: 长时运行 OOM（超出「单条 8KB」声明包络的累积面）；挂死后台任务泄漏 cached 线程并永久占用 taskId 命名空间；对照 X1 CL-030（chat 日志无 TTL）与本条构成模块内「账本无清理」复发族。
- **建议**: ① 终态条目延迟移除（如终态后保留 10min 供 `task-output` 读取，经注入 `IScheduledExecutor`/`GlobalExecutors.globalTimer()` 定期清扫，或 `snapshot` 读时惰性过期）；② 增加 sweep 任务按 `submitTime + timeoutMs` 对 RUNNING 条目强制 `complete(taskId, STATUS_TIMEOUT, ...)` + `future.cancel(true)`（对齐 stop() 的 kill 链）；③ 暴露 `tasks.size()` 指标。
- **失效面枚举**（F3：注册表全部生命周期入口清扫）: ① `submit:97-102` —— 唯一写入点，本条主体；② `run:104-131` —— 提交执行，无清理职责但 `catch (Exception)` 兜底不拦 `Error`（Error 路径条目滞留 RUNNING，随 ③ 的 sweep 一并收口）；③ `complete:133-170`/`stop:258-285` —— 仅状态迁移不删条目，本条覆盖；④ `attachFuture:297-302`/`snapshot`/`readOutput` —— 纯读/回填，无清理点，本条覆盖；⑤ 对照 `TaskDispatchCoordinator.inFlightDispatches:46`（X3 侧另一账本）—— 有 whenComplete 自清（其失效面另立 C4-015），非本族；⑥ toolkit `BashExecutor` 自身进程超时 —— 仅覆盖 bash 工具的进程面，不覆盖注册表账本，非同族豁免。
- **信心水平**: 确定（无 remove、无 timeout 强制点均为全文件/全库 grep 直证；增长速率按 8KB/条上界推算）
- **误报排除**: 非把「有界单条」误报为「有界总量」——javadoc 的 bounded 声明明确限于 output capture；dead-letter/TIMEOUT 语义存在但无执行者是功能性缺失而非文档化边界（对照校准第 4 条：无上限声明即不豁免）。
- 复核状态: 保留(P1)｜新发现——回码核验成立：`tasks` 全文件无 remove（仅 put/get/size 三种读态）、`timeoutMs` 仅存储（L98-100/L328/L347-352）与快照回显（L203）、无任何比较/强制点、`run` 的 catch(Exception) 兜底不拦 Error 均直核；F4 无界账本 + timeoutMs 死配置 + F5 挂死不可见三面闭合；同站点跨维双登记 C3-023（P1，建议主处置归 C4-012）+ C7-027 成立留 7.1 并单；跨单元同族 WI 1.1 C1-006（任务分配账本只增不删，P1）判级一致；修复建议② 的 globalTimer 扫描同样须与 WI 2.1 C4-001 协调（不得直接落共享 4 线程定时器）

### [C4-013] `DBMessageService` 双周期任务与 `TeamTaskSchedulerDaemon` 扫描任务兜底止于 `Exception`/`RuntimeException`：`Error` 逃逸按 JDK 语义静默永久停转——消息投递与 stale sweep 双双死亡（at-least-once 的 poller 心跳停止）且零日志（X2 C4-007 同族，X3 侧立案收官）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/message/DBMessageService.java:315-329,572-581`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/scheduler/TeamTaskSchedulerDaemon.java:262-276`（调度点：`DBMessageService.java:213,217`、`TeamTaskSchedulerDaemon.java:237-238`）
- **证据片段**:
  ```java
  // DBMessageService.java:323-327 —— poll 兜底只到 Exception（Error 穿透）
  try { pollTopic(topic); } catch (Exception e) { LOG.error(...); }
  // :576-580 —— sweep 同型 catch (Exception)
  // TeamTaskSchedulerDaemon.java:272-275 —— scan 兜底只到 RuntimeException
  } catch (RuntimeException e) {
      LOG.warn("TeamTaskSchedulerDaemon: periodic scan failed (will retry next interval): {}", ...);
  ```
- **严重程度**: P2
- **并发场景**: 单线程 `db-message-poller-<consumerId>` 上的两个 `scheduleWithFixedDelay` 周期任务（50ms pollAllTopics + 60s sweepStaleClaimedMessagesSafely）；注入 `IScheduledExecutor` 线程上的 `scanOnceSafe`（5s 间隔）。JDK 语义：任务抛出任意 Throwable 后该周期任务被永久取消，异常仅存入被丢弃的 `ScheduledFuture`（`scheduleHandle` 从不读取，`TeamTaskSchedulerDaemon.java:58`），无 afterExecute 日志。
- **触发时序**: T1：poller 线程执行 `pollTopic` → `findPending`/`claimMessage` 的 JDBC 路径或消费者反序列化抛出 `Error`（`NoClassDefFoundError`/OOM 边缘/驱动 `StackOverflowError`）——注意 `NopAiAgentException` 抛出点均在 `catch (Exception)` 内，唯独 `Error` 穿透 `pollAllTopics:326` 的兜底 → T2：`scheduleWithFixedDelay` 按 JDK 语义取消该任务：全部 topic 的消息投递与 stale-CLAIMED sweep **同时**永久停止，零日志（`closed` 标志仍为 false，`sendAsync` 照常入队）→ T3：表内 PENDING 消息无限积压，发送方（`LocalAgentMessenger.request` 的调用方）全部在 reply 超时后失败，而系统外观「健康」（poller 线程存活 park 在队列等待）。daemon 侧同型：`scanOnceSafe` 的 `Error` 逃逸 → 团队任务调度停转，CLAIMED 任务滞留，零日志。
- **现状**: `RuntimeException`/`Exception` 路径的 warn/error-retry 设计正确；唯 `Error` 路径既不中止也不可感知。与 X2 C4-007（`SessionLockRenewal`/`ScheduledRecoveryManager`）完全同族——X2 失效面 ④ 已预告本两处归 X3 审读，本条收官立案。
- **风险**: 罕见触发但后果是消息传输层的静默整体失效（对照 F9：「scheduleAtFixedRate 任务抛异常后是否停止调度且该停止是否被感知——未感知」）；停转后唯一线索是消息延迟的下游症状，无法归因。
- **建议**: 三处兜底改 `catch (Throwable)`（poll/sweep 对 `Error` 记 ERROR 后继续依赖 fixedDelay 下一轮；scan 对 `Error` 记 ERROR 后重抛给包装层）；或为三个调度器统一加 `afterExecute` 钩子（对照 X1 C4-003 同款建议，全模块周期任务一次收口）。
- **失效面枚举**（F3：模块内周期任务 catch 面全量清扫，收官）: ① `DBMessageService.pollAllTopics:326` —— 本条主体；② `DBMessageService.sweepStaleClaimedMessagesSafely:578` —— 本条同族；③ `TeamTaskSchedulerDaemon.scanOnceSafe:272` —— 本条主体（注意其兜底更窄，止于 `RuntimeException`，连受检 Exception 包裹亦无）；④ X2 C4-007 已立案两处（`SessionLockRenewal:74`、`ScheduledRecoveryManager:393`）—— 同族已立案不重复；⑤ `InMemoryActorRuntime.runConsumptionLoop:521` —— 常驻（非周期）任务，X2 CL-035 已登记 watch；⑥ `DefaultBackgroundTaskRegistry.run:119` —— 一次性任务非周期，其 Error 面随 C4-012 ③ 收口。至此全模块周期任务 catch 面清点完毕，无遗漏站点。
- **信心水平**: 确定（三处 catch 边界、`scheduleHandle` 无读取点、JDK 周期任务语义均可代码直证；`Error` 触发条件本身罕见）
- **误报排除**: 非「建议 catch Throwable」风格化泛报——具体指认「周期任务 + 兜底止于 Exception/RuntimeException + 返回 future 无人读」叠加导致投递/调度静默死亡且零观测，与 X2 C4-007 同判据同判级（P2）。
- 复核状态: 保留(P2)｜新发现——回码核验成立：`pollAllTopics`/`sweepStaleClaimedMessagesSafely` 兜底止于 Exception（L326/L578）、`TeamTaskSchedulerDaemon.scanOnceSafe` 止于 RuntimeException（L272-275，更窄——连受检 Exception 包裹亦无）、`scheduleHandle` 无读取点、`DBMessageService.close()` shutdownNow+awaitTermination(5s) 对称均直核；同站点 C3-027 P3 vs 本条 P2 分叉维持留 7.1（C4 侧意见：P2 成立——消息传输心跳 + 团队调度双静默停转的失效面大于一般周期任务，at-least-once 安全网随 poller 一并死亡；但 C3-020/C3-027/C4-007/C4-013 四案应一并并入 WI 2.1 C3-001 平台绝缘层，分叉不影响修复归属）

### [C4-014] `MemberAgentTaskStep`（单 bound 节点路径）无 per-member `orTimeout` 亦无超时 `cancelSession`：悬挂成员 agent 使节点 future 永不 settle，整体图超时的 `taskRt.cancel` 不可达在途引擎执行——成员执行成为无界 zombie；与 fan-out 路径 `executeBoundMember` 的双层防护不对称（F3 同族漏修面）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/flow/MemberAgentTaskStep.java:152,162-216`（对照证据：`team/flow/MemberFanOutDispatcher.java:312-345`、`TeamTaskFlowOrchestrator.java:634,653-667`）
- **证据片段**:
  ```java
  // MemberAgentTaskStep.java:152 —— 无 orTimeout、无超时 cancel 链
  CompletableFuture<AgentExecutionResult> engineFuture = agentEngine.execute(request);
  // 对照 MemberFanOutDispatcher.java:321-331 —— fan-out 路径同操作有完整防护
  CompletableFuture<AgentExecutionResult> engineFuture =
          agentEngine.execute(request).orTimeout(memberExecTimeoutMs, TimeUnit.MILLISECONDS);
  ... on TimeoutException: agentEngine.cancelSession(target.getSessionId(), "...timeout...", true);
  ```
- **严重程度**: P2
- **并发场景**: nop-task 图调度线程触发 bound 节点 → 引擎 agentExecutor 线程执行成员 agent（时长不可控，LLM 慢/工具挂死可达分钟级以上）；编排调用方线程在 `execute().join()`/`orTimeout(overall)` 上等待。
- **触发时序**: T1：bound 成员节点触发，`engine.execute` 返回 future 挂入 `whenComplete`——无 per-member deadline → T2：成员 agent 挂死（LLM 层超时缺失或工具无限阻塞），节点 future 永不 settle → T3：整体图 `orTimeout(maxDepth × memberExecTimeoutMs)`（`TeamTaskFlowOrchestrator.java:634`）到期，`exceptionally` 分支执行 `taskRt.cancel(CANCEL_REASON_TIMEOUT)`（`:655`）——该 cancel 按 javadoc 自述只「checks the cancel token before starting any successor step」，**不中断在途成员执行、不调用 cancelSession** → 编排结果已如实上报失败，但成员 agent 的引擎执行继续无限期运行（占 agentExecutor 线程、烧 LLM 配额），其完成回调事后触发 `completeTask` CAS（任务仍 CLAIMED 且 epoch 未变时反而**事后把任务置 COMPLETED**，与已上报的失败结果状态分叉）。对照 fan-out 路径：超时即 `cancelSession` 杀 zombie（`MemberFanOutDispatcher.java:328-337`），两路径防护强度不一致。
- **现状**: per-member deadline（I3 R-2-3）只落到了 fan-out 调度器与 spawn 请求（`SpawnMemberRequest.memberExecTimeoutMs` → `DefaultMemberSpawner:188` bounded get），plans 233/241 的原始单 bound 节点步骤 `MemberAgentTaskStep` 未同步加装——同族修复漏改的教科书形态。
- **风险**: 单成员团队（shipped `DefaultTaskMemberRouter` 缺省单目标 bound 路径，即零回归主路径）的挂死成员无任何恢复手段：等待面有整体图 backstop 封顶（故非 P1），但 zombie 引擎执行与事后状态分叉无界且不可观测。
- **建议**: `MemberAgentTaskStep` 增加与 fan-out 相同的 `orTimeout(memberExecTimeoutMs)` + TimeoutException 分支 `agentEngine.cancelSession(memberSessionId, ..., true)`（构造器已持引擎引用）；或在 `TeamTaskFlowOrchestrator` 超时分支对全部在途节点统一 `cancelSession`（收集 recorder 的 start 未 complete 集合）。
- **失效面枚举**（F3：成员执行超时防护全量清扫）: ① `MemberAgentTaskStep:152` —— 本条主体（无防护）；② `MemberFanOutDispatcher.executeBoundMember:321` —— 已有 orTimeout+cancelSession（对照组，防护完整）；③ `SpawnMemberAgentTaskStep:232-241` —— 无 dispatcher 层 orTimeout，靠 spawner 内部 bounded get（`DefaultMemberSpawner:188`）兜底，防护弱一档但缺省 spawner 下有界，随 ① 修复范式一并评估；④ `MemberFanOutDispatcher.spawnOneTarget:383` —— 双层防护齐全（对照注释明言 two-layer protection）；⑤ daemon 路径 —— 经 ②/④ 的 dispatcher，同享防护，无同族缺陷。
- **信心水平**: 确定（防护不对称与 cancel 不可达在途执行均为代码+注释直证；「事后 completeTask 状态分叉」由 CAS epoch 条件推演，标注很可能）
- **误报排除**: 非漏看整体图 backstop（已如实计入故降 P2 非 P1）——报告的是同族站点防护强度不一致这一 F3 实证缺陷形态，与「或超时后 zombie 无恢复手段」的具体后果。
- 复核状态: 保留(P2)｜新发现——回码核验成立：`MemberAgentTaskStep:152` 裸 `engine.execute`（无 orTimeout 无超时 cancelSession）、fan-out 路径 `executeBoundMember` 有 `orTimeout`+TimeoutException 分支 `cancelSession(...,true)` 完整防护（L321-337）、整体图 backstop（`overallTimeoutMs`，orTimeout+exceptionally 限 TimeoutException 触发 cancel）与 AR-3 cancel 自述「checks the cancel token before starting any successor step」均直核；同族漏修形态（I3 R-2-3 防护未同步 plans 233/241 原始单 bound 路径）成立——F3 实证缺陷形态，非新规则要求；同站点跨维双登记 C3-025（P2）成立留 7.1 并单

### [C4-015] 取消/关闭路径不可达已提交的 spawn 工作：图超时 `taskRt.cancel` 只阻后继步骤、池队列中已提交的 spawn 任务照常执行成员 agent（再耗一个 memberExecTimeoutMs 配额）；daemon `stop()` 的 `shutdownNow` 丢弃 queued spawn 任务致其 dispatch future 永不 settle——`inFlightDispatches` 条目泄漏且任务滞留 CLAIMED（AR-3 声明意图与实际机制落差）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/flow/TeamTaskFlowOrchestrator.java:646-667`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/scheduler/TaskDispatchCoordinator.java:102-107,150-166,253-275`（依赖证据：`TeamTaskSchedulerDaemon.java:245-260`、`SpawnMemberAgentTaskStep.java:232-241`）
- **证据片段**:
  ```java
  // TeamTaskFlowOrchestrator.java:650-655 —— cancel 自述仅「stops further graph progress」
  //   ("so this stops further graph progress")，不触及已提交池任务
  try { taskRt.cancel(ICancellable.CANCEL_REASON_TIMEOUT); } ...
  // TaskDispatchCoordinator.java:103-105 —— stop 侧 shutdownNow：queued 任务被移除
  ownedSpawnExecutor.shutdownNow();   // 无 awaitTermination、无在途 drain
  // :258-260 —— inFlightDispatches 仅在 future settle 时移除（被丢弃的 supplyAsync 永不 settle）
  inFlightDispatches.add(dispatched);
  dispatched.whenComplete((outcome, ex) -> { inFlightDispatches.remove(dispatched); ... });
  ```
- **严重程度**: P2
- **并发场景**: orchestrator 侧：整体超时取消发生时，spawn 池（无界队列）中已提交未启动的 `supplyAsync(spawnAndComplete)` 任务继续被 worker 领取执行——完整走 spawnMember → 成员 agent 执行 → completeTask（最大再耗一个 `memberExecTimeoutMs`=120s 的成员执行配额）。daemon 侧：`stop()` 对 owned 池 `shutdownNow`，队列中未启动的 spawn 任务被丢弃——`CompletableFuture` 无自动取消语义，其 future 永不完成。
- **触发时序**（daemon 泄漏链）: T1：daemon 扫描认领 K 个任务并 fan-out，其中 J 个 spawn 目标入池排队（池宽 < J），dispatch future 加入 `inFlightDispatches` → T2：运维调用 `stop()`（或引擎关闭链）→ `shutdownOwnSpawnExecutor` → 池 shutdownNow，J 个 queued 任务被移出队列且**永不执行** → T3：这 J 个 dispatch 的 `allOf` 永不完成 → `whenComplete` 移除钩子永不运行 → `inFlightDispatches` 永久滞留 J 条（每次 stop 前的在途量累积）；对应任务的 `completeTask` 永不运行，滞留 CLAIMED，依赖人工 `reclaimTask` 恢复。`awaitInFlightDispatches` 对这些 future `get(remaining)` 必然超时返回 false（优雅关闭语义失真：明明已丢弃却表现为「超时未完成」）。
- **现状**: orchestrator 的 AR-3 注释声称 cancel 的目的是「member agents / spawn workers do not continue as zombies consuming quota」，但机制上 cancel 仅阻新步骤——已提交 spawn 工作的 zombie 面与声明意图不符（缓解：zombie 受 spawner bounded get 封顶一档）。daemon 侧 shutdownNow 后无 drain/取消语义，泄漏与滞留均为确定性后果。
- **风险**: 单次 stop 泄漏量小（J 条引用），反复 start/stop 周期（容器滚动重启、运维巡检）线性累积；CLAIMED 滞留任务若 reclaim 工具未接入则永久卡死团队任务队列槽位。
- **建议**: ① orchestrator 超时分支对「已提交未 settle」的 spawn future 主动 `cancel(false)` 并对在途成员 `cancelSession`（与 C4-014 建议合并为统一的取消传播层）；② `TaskDispatchCoordinator` 保存 queued 任务的 `SpawnMemberAgentTaskStep→future` 映射，`shutdownOwnSpawnExecutor` 前先对未启动任务 `future.completeExceptionally(cancelled)` 使 dispatch 链正常 settle、`inFlightDispatches` 正常排空；③ `shutdownOwnSpawnExecutor` 补 `awaitTermination`（对照 orchestrator `close():451-452` 已有 2s）。
- **失效面枚举**（F3：取消/关闭 → 已提交工作的传播面全量清扫）: ① `TeamTaskFlowOrchestrator` 超时 cancel → queued spawn 任务 —— 本条主体 A；② `TaskDispatchCoordinator.shutdownOwnSpawnExecutor:102-107` → queued spawn 任务 → `inFlightDispatches` 泄漏 —— 本条主体 B；③ `DBMessageService.close():228-241` shutdownNow —— 在途 `onMessage` 被中断后消息滞留 CLAIMED，由 stale sweep（5min）兜底恢复，有安全网非本族；④ `TeamTaskSchedulerDaemon.stop():253` `cancel(false)` 周期句柄 —— 语义正确（不中断在途、不认领新任务），非本族；⑤ fan-out BOUND 目标 —— 引擎 future 有 per-member orTimeout+cancelSession 兜底（C4-014 ②），取消传播存在，非本族。
- **信心水平**: 确定（shutdownNow 丢队列任务 + CompletableFuture 无自动取消 + whenComplete 移除依赖 settle，三要素均 API 语义直证；orchestrator 侧 zombie 量级由 spawner bounded get 封顶）
- **误报排除**: 非对 `shutdownNow` 原语的泛泛指控——具体指认「无界队列中已提交任务的 future 永不 settle → 自清账本失灵」的确定性泄漏链，并区分了有安全网的 ③ 与无安全网的 ①②。
- 复核状态: 保留(P2)｜新发现——回码核验成立：`shutdownOwnSpawnExecutor` 仅 shutdownNow 无 awaitTermination 无 drain（L102-107）、`inFlightDispatches` 仅 whenComplete settle 时移除（L258-260）、`awaitInFlightDispatches` 对被丢弃 future `get(remaining)` 必超时返回 false 均直核；特别佐证：`inFlightDispatchCount` javadoc 自述不变式「bounded per-member timeout 保证 settlement → whenComplete 移除必触发——no permanent leak」恰被 stop 路径丢弃 queued 任务打破（声明契约与机制落差的双重点名成立）；AR-3 注释「do not continue as zombies consuming quota」与 cancel 仅阻后继步骤的机制落差直核；泄漏量级（per-stop J 条 × start/stop 周期累积）维持 P2

### [C4-016] `LocalAgentMessenger.replySubscriptions` 只增不删（subscribe 无 unsubscribe，F8）：每 distinct senderId 永久驻留一个 reply topic 订阅；DBMessageService 底座下轮询 SQL 数随订阅数线性增长（50ms × N topic/轮）——长时运行消息面退化 + 双侧 consumers map 泄漏

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/message/LocalAgentMessenger.java:57,88-111,123-134`（底座证据：`message/DBMessageService.java:114,208-218,315-329`；消费方证据：`tool/CallAgentExecutor.java:295-329`）
- **证据片段**:
  ```java
  // LocalAgentMessenger.java:57 —— 订阅表全文件无 remove/cancel 调用点
  private final Map<String, IMessageSubscription> replySubscriptions = new ConcurrentHashMap<>();
  // :128-132 —— 每 replyTopic（由 senderId 派生，AgentMessageTopics.replyTopic）一次 put，永不回收
  synchronized (replySubscriptions) {
      if (replySubscriptions.get(replyTopic) == null) {
          IMessageSubscription subscription = messageService.subscribe(replyTopic, new ReplyDemultiplexer());
          replySubscriptions.put(replyTopic, subscription);
  ```
- **严重程度**: P2
- **并发场景**: 每次 `request`（call-agent 的 messaging 子代理派发，`CallAgentExecutor:329`）在请求线程 ensure 本 senderId 的 reply 订阅；DBMessageService 底座时其单线程 poller 每 50ms 对 `consumers.keySet()` 全量轮询（每 topic 一条 `SELECT ... WHERE TOPIC=? AND STATUS=PENDING`）。
- **触发时序**: T1：会话 S1 首次 call-agent messaging 派发 → `replyTopic(senderId=S1)` 订阅创建并永久驻留（请求终态不回收——`pendingRequests:105` 有 whenComplete 自清，订阅表无对应物）→ T2：会话 S2..Sn 陆续派发，各自新增订阅；引擎会话 id 随执行创建（长驻服务 n 无界）→ T3：DB 底座下 poller 单轮 SQL 数 = 活跃+僵尸订阅总数，随 n 线性增长（n=1000 时每 50ms 执行 1000 条 indexed SELECT，poller 吞吐饱和、投递延迟膨胀，`scheduleWithFixedDelay` 使实际轮询周期被动拉长）。交叠后果：消息面延迟随历史会话数单调退化，且双侧 `consumers`/`replySubscriptions` map 均无界（F4 伴随 F8）。
- **现状**: `registerHandler` 返回的订阅由调用方持有可 cancel（对称面存在）；唯 messenger **内部自建**的 reply 订阅无任何回收路径——request 的 `whenComplete`（`:105`）已具备终态钩子位，未挂订阅回收。
- **风险**: 长时运行退化（poll 吞吐饱和）+ 内存无界增长；LocalMessageService 底座下无 DB 轮询放大但仍有无界订阅/消费者累积。
- **建议**: reply 订阅引用计数化：`pendingRequests` 为空且空闲超时后 `subscription.cancel()` + `replySubscriptions.remove(replyTopic)`（挂到 `request` 的 whenComplete + 空闲 sweep，注入 `GlobalExecutors.globalTimer()`）；或按会话生命周期在会话销毁钩子中统一 cancel。
- **失效面枚举**（F3：messenger 订阅生命周期全量清扫）: ① `LocalAgentMessenger.replySubscriptions:128-132` —— 本条主体；② `LocalAgentMessenger.registerHandler:120` —— 返回订阅交调用方管理（API 对称），非本条缺陷，但若调用方普遍忽略 cancel 则形成同族放大面（watch）；③ `pendingRequests:51,105` —— 有终态自清（对照组，卫生良好）；④ `DBMessageService.consumers:114` —— 被 ① 单向注入增长，随 ① 一并收敛；⑤ `DeferredAckMailbox`（runtime/Actor 面）—— opt-in 且 dead-letter 保留为文档化设计，另立 CL-037 不混排。
- **信心水平**: 确定（订阅表无回收点、replyTopic 按 senderId 派生、pollAllTopics 全量遍历均为代码直证；退化量级由轮询公式推算）
- **误报排除**: 非对「共享订阅」设计本身的否定（按 senderId 共享是对的）——缺陷是只有创建没有回收的 F8 不对称，且给出了与既有 `pendingRequests` 自清模式对称的修复位。
- 复核状态: 保留(P2，严重度分叉登记留 7.1)｜新发现——回码核验成立：`replySubscriptions` 全文件无 remove/cancel 调用点（仅 `pendingRequests` 有 whenComplete 自清 L105/L208）、`ensureReplySubscription` 只建不删（L123-134）直核；**复核新增指针（任务指派未列，本复核补登记）**：本条与 C3-024（P1）、C7-026（P1）为同站点三重登记且三案未互指——C3/C7 侧 P1 与本条 P2 构成严重度分叉；C4 侧意见：「长时运行必然触发的无界增长」落入校准 P1 档且 DB 底座下每订阅固化 20 QPS 常驻轮询（退化确定性），本条 P2 略低于同站双 P1 口径，建议 7.1 并单统一校准（倾向 P1），主处置归并单后条目、本条随单吸收

## 维度集外线索（overlay C2/C6/C8/C9）

### [nop-ai-core-nop-ai-agent-CL-029]（C3 面）`aggregateStreamToResponse` 返回的 future 取消不传播到内部 Flow 订阅与 cancelToken，上游 LLM 流继续完整执行

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/ChatServiceImpl.java:391-440`
- **线索**: `stream=true` 时 `callAsync` 经 `aggregateStreamToResponse`（L391）订阅 `callStream`，内部订阅者只注册了 `subscription.request(Integer.MAX_VALUE)`（L403），返回的 `CompletableFuture`（L393）与该订阅、以及与传入的 `cancelToken` 之间无任何取消绑定（对照 `callStream` L209-211 有 `appendOnCancelTask(subscription::cancel)`）。调用方若只取消返回的 stage（`future.cancel`）而非 cancelToken，HTTP 流与上游订阅不受影响：LLM 继续产出全量 token（计费照付）、`StreamAggregator` 继续累积、终态回调照常执行——孤儿流直到服务端自然结束。与 C4-002 同属取消传播家族但发生在聚合层（async chain 取消语义），建议 C3 维度立案复核；C4-002 的修复（publisher 关闭绑定）不能替代本条（这里是 stage→订阅方向的传播缺失）。
- **状态**: 未复核

### [nop-ai-core-nop-ai-agent-CL-030]（C8 面，F4）`nop.ai.service.log-dir` 请求/响应日志逐请求落盘、按日期分片、全模块无 TTL/清理路径

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/DefaultChatLogger.java:41-79`；`ChatLogHelper.java:29-36`
- **线索**: `logRequest/logResponse` 每次调用在 `{logDir}/{yyyy}/{MM-dd}/{sessionId}/{requestTime}-{retryTimes}-{requestId}{postfix}` 落盘完整 YAML（含脱敏后全量 messages/响应内容），流式与非流式每请求各写 request/response 两文件。全模块（`DefaultChatLogger`、`ChatLogHelper`、`DefaultAiChatResponseCache` 的 TTL 仅覆盖 cache 目录）不存在任何日志清理/TTL/归档路径——append-only 账本只增不删（F4 形态；日期分片缓解单目录膨胀但不减总量）。长期运行磁盘占用随请求量线性增长，需 C8 维度评估是否立案（若认定为文档化运维边界则降级）。
- **状态**: 未复核

### [nop-ai-core-nop-ai-agent-CL-031]（C8 面）`StreamAggregator` 无界 StringBuilder 累积完整流式响应（声明包络内，watch-only）

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/ChatServiceImpl.java:483-529`
- **线索**: 聚合器对 text/reasoning 用无界 `StringBuilder`、tool_call 按 `itemIndex` 在 `LinkedHashMap` 中累积（L484-486、L523），内存上界 = 单响应全量 token 文本。该上界受模型 `maxTokens` 声明包络约束，不属「超出声明包络的 OOM」；且 `ToolCallAccumulator.apply` 对同一 `itemIndex` 重复 `ADDED` 事件会重复拼接 `arguments`（L585-593：`getArguments()` 全量通道与 DELTA 通道无互斥，方言若两者都发则翻倍累积）——建议 C8/C2 维度复核方言层是否存在双通道并发供给的真实输入；包络内问题，watch-only。
- **状态**: 未复核

### [nop-ai-core-nop-ai-agent-CL-032]（C2 面）`InMemoryActorRuntime.createActor` 配额检查为非原子 check-then-act：并发 create 各读同一 `activeCount`，租户级 actor 上限可被并发提交击穿

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/runtime/InMemoryActorRuntime.java:275-286`
- **线索**: `int activeCount = getActiveActors().size(); resourceGuard.checkConcurrent(..., activeCount + 1, 0)` —— 读取当前活跃数与 `registry.register(actor)` 之间无原子性（无同步块/无 CAS 配额预留）。T1/T2：租户配额上限为 K，K 个并发 `createActor`（不同 session，来自不同执行的 worker 线程）同时读到 activeCount=K-1 → 全部通过 → 实际活跃数达 2K-1。配额被击穿后无回收机制（仅 actor 销毁时自然回落）。同 session 重复创建已被上游 `runningExecutions` 每会话单执行守卫排除，本条仅覆盖跨 session 并发面。建议 C2 维度复核是否立案（DefaultResourceGuard 接入时才有实效；NoOp 缺省无影响）。
- **状态**: 未复核

### [nop-ai-core-nop-ai-agent-CL-033]（C9 面）自建线程命名全部无索引后缀：`nop-ai-agent-exec`/`nop-ai-background-task`/`nop-docker-sandbox-reader`/`nop-sandbox-stdout-reader` 多线程同名，且与 owner doc 声明的 `nop-ai-agent-exec-*` 不一致

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java:471-475,644-648`、`security/DockerSandboxBackend.java:254-257`、`security/NoOpSandboxBackend.java:106-108`
- **线索**: 四处自建线程工厂均以固定字符串命名、无单调索引（对照平台 `NamedThreadFactory` 惯例与 `TaskDispatchCoordinator.java:134` 的 `counter.incrementAndGet()` 同模块先例）。cached 池高并发下数十线程同名，jstack/日志中不可区分归属；owner doc `nop-ai.md:123` 声明线程名为 `nop-ai-agent-exec-*`（带 `-*`），与代码实际（无后缀）不符——按 reconcile-on-touch 应同任务修正（改代码加索引或改文档）。C9 可观测性线索，P3 量级。
- **状态**: 未复核

### [nop-ai-core-nop-ai-agent-CL-034]（C6 面）`DockerSandboxBackend` 清理链进程级残留：`runShortCommand` waitFor(5s) 超时后不 destroy 子进程；`killContainer` 双层吞异常且 kill 失败时 reader 线程滞留无状态可见

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/DockerSandboxBackend.java:515-539,580-590`
- **线索**: 超时清理链 `killContainer → runShortCommand("docker","kill"/"rm","-f")`：(1) `p.waitFor(5, SECONDS)` 返回 false（docker CLI 挂死）后方法直接返回，未 `p.destroyForcibly()`，孤儿 docker 进程滞留；(2) 两个 cleanup 调用均 `catch (Exception ignored)` 静默——kill 与 rm 全失败时容器滞留且仅 TIMEOUT 异常消息可见（对照 `NoOpSandboxBackend.killTree:220-239` 有 descendants 清理与 2s waitFor，Docker 侧更弱）。(3) kill 失败 → `docker run` 进程持 stdout 管道 → reader 线程（daemon）阻塞在 `in.read` 滞留，`awaitReader` 的 interrupt 对进程管道 read 无效（该方法注释已自认）。对照 NoOp 侧已有注释声明边界，Docker 侧进程残留面未声明。建议 C6 维度复核（低频：需 docker daemon 故障叠加）。
- **状态**: 未复核

### [nop-ai-core-nop-ai-agent-CL-035]（C2/F9 面）`InMemoryActorRuntime` 消费循环 `Error` 逃逸：循环线程死亡而 actor 状态滞留非终态，配额计数与注册表滞留僵尸 actor 直至引擎 finally 销毁

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/runtime/InMemoryActorRuntime.java:515-525,548-554`
- **线索**: `runConsumptionLoop` 兜底 `catch (Exception e) → FAILED`；`Error` 逃逸后线程死亡但既不转 FAILED 也不从 `executors`（L133）移除——`actor.getStatus()` 保持 RUNNING/IDLE（均属 `isActiveStatus`），`getActiveActors()` 计数含僵尸、`createActor` 幂等路径（L257-263）会持续返回该死 actor（其消费能力已丧失）。缓解面：引擎执行 finally（`DefaultAgentEngine.dispatchExecution` L1022-1024）总会 `destroyActor` 兜底回收，僵尸存活期 = 单次执行剩余时长；且 opt-in（缺省 NoOpActorRuntime 关闭）。与 C4-007 同族（周期/常驻任务 Error 逃逸不可感知）但后果轻一档，watch-only。
- **状态**: 未复核

### [nop-ai-core-nop-ai-agent-CL-036]（C2 面）`TeamTaskFlowOrchestrator`/`TaskDispatchCoordinator.resolveSpawnExecutor` 惰性建池为无同步 check-then-act，`spawnStepExecutor` 非 volatile：共享 orchestrator 实例并发 `executeAsync` 可双建池其一成孤儿（shipped per-invocation 接线不可达，watch）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/flow/TeamTaskFlowOrchestrator.java:206,421-423,468-478`、`team/scheduler/TaskDispatchCoordinator.java:49,94-96,113-123`
- **线索**: `if (ownedSpawnExecutor == null) { ownedSpawnExecutor = Executors.newFixedThreadPool(...); }` 无同步块/无 double-checked/无 `synchronized`（对照同模块 `DefaultAgentEngine.getAgentExecutor():469` 有 `synchronized` 修饰的同类惰性建池先例）。T1/T2：共享 orchestrator 上两请求线程并发进入 → 双池创建、字段相互覆盖 → 先建池若已被步骤捕获则 `close()` 只能关后建池，先建池成孤儿（daemon 线程常驻）。shipped 生产接线（`TeamExecuteFlowExecutor:234`）为 per-invocation 构造单线程使用，该窗口不可达；但类是公开 API，测试/嵌入方按单例持有即触发。`spawnStepExecutor` 非 volatile 的运行期 `setSpawnStepExecutor` 与在途 `resolveSpawnExecutor` 间另有可见性窗口（后果轻：多建一个会被 close 的 owned 池）。建议 C2 复核是否随 C4-010 的池上提改造一并加同步。
- **状态**: 未复核

### [nop-ai-core-nop-ai-agent-CL-037]（C8/F4 面）`DeferredAckMailbox` 缺省无界容量且 dead-letter 列表永久保留：容量检查不含 dead-letter、无任何清理/导出路径

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/message/DeferredAckMailbox.java:32-35,58-60,106,158-198,236-244`
- **线索**: `capacity <= 0` 缺省无界（javadoc 明文），`offer` 的容量检查只算 `pending + inFlight`；`deadLetter` 列表只在 `nack(id, true)` 达到 `maxDeliveryAttempts`（缺省 5）时追加，永久保留「for inspection」且无清理/上限/导出API。投递持续失败的消息经 5 次 nack 后转入 dead-letter 即永久驻留（含完整 envelope payload）——消费方 bug 导致批量 nack 时 dead-letter 无界增长。使用面 opt-in（`InMemoryActorRuntime` actor mailbox，X2 已登记 watch 家族），故仅 CL 登记：建议 C8 复核 dead-letter 上限/快照导出与 pending/inFlight 的容量口径是否应含 dead-letter。
- **状态**: 未复核

### [nop-ai-core-nop-ai-agent-CL-038]（C8/F4 面）`ai_agent_message` CONSUMED 行与 `ai_agent_team_task` 终态行无任何删除路径：store-layer 契约声明的清理责任（stale sweep / 状态机终态清理）与实现不符

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/message/DBMessageService.java:493-566`（全 message 包 grep 无 DELETE）、`team/DbTeamTaskStore.java`（全 team 包 DELETE 仅 `DbTeamManager.removeMember:508` 成员表）；契约：`ai-dev/design/nop-ai-agent/store-layer-contract.md` 表清单「DBMessageService → ai_agent_message → 清理责任: stale sweep」「DbTeamTaskStore → 状态机终态清理」
- **线索**: `ai_agent_message` 的 CONSUMED 行（含全量 message body）无任何 DELETE/TTL/归档路径——契约声明的清理责任「stale sweep」实际只做 CLAIMED→PENDING 重置（`sweepStaleClaimedMessages:533-566`），不清行；消息传输表随历史请求量单调增长（与 CL-030 chat 日志无 TTL 同族，本条为 DB 面）。`ai_agent_team_task` 终态（COMPLETED/ABANDONED/FAILED）任务行同样零删除，契约声明的「状态机终态清理」无对应实现。表有 (TOPIC, STATUS) 索引，轮询查询不退化，但磁盘占用与 F4 账本语义违约成立。建议 C8 复核：按契约补 cleanup（stale sweep 扩展为终态行保留窗口清理）或修订契约声明为「宿主运维」。
- **状态**: 未复核

### [nop-ai-core-nop-ai-agent-CL-039]（C2 面）`FileBackedSessionStore.remove` 在持锁期间从 `sessionLocks` 移除锁对象：后续 `save` 可获取新锁对象与删除中的 remove 并发进入（会话复活窗口，per-session 单执行守卫缓解）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/session/FileBackedSessionStore.java:176-203,547-549`
- **线索**: `remove` 在 `synchronized(lock)` 内部执行 `sessionLocks.remove(sessionId)`（L182）；此后任一线程调用 `save(session)` 时 `sessionLock(sessionId):547-549` 的 `computeIfAbsent` 会创建**新**锁对象并进入与 remove 的文件删除段并发——`save` 的 fold/journal 追加可在 `Files.deleteIfExists`（L189-190）之后重建 session 文件与 `sessions` map 条目（已删除会话在磁盘与内存双复活）。缓解：引擎侧 per-session 单执行守卫（`runningExecutions`）使同一 session 的 save/remove 交叠需要「删除会话 + 异源写入」的组合（恢复/管理路径并发），窗口窄且后果为可再删除的复活态。建议 C2 复核：锁映射的移除应在 `synchronized(lock)` 块**之后**（先删数据后摘锁），或改为不摘锁（锁对象数量以活跃会话为界可接受）。
- **状态**: 未复核

### [nop-ai-core-nop-ai-agent-CL-040]（C4 面，F5）`DBMessageService` 单线程 poller 同时承担双周期任务与全部 topic 的 inline 消费投递：慢/挂死消费者阻塞全部 topic 投递与 stale sweep，且无「上次成功轮询」滞后指标——挂死对健康检测不可见

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/message/DBMessageService.java:208-218,315-365`
- **线索**: `pollTopic:358` 在 poller 线程 inline 调用 `consumer.onMessage`（消费契约允许同步返回——仅返回 `CompletionStage` 时才异步）；单线程 STPE 上 pollAllTopics 与 sweepStaleClaimedMessagesSafely 串行互斥。一个同步阻塞/挂死的消费者 handler 使：① 全部 topic 的投递停摆（队头阻塞，同 X2 C4-008 的共享单线程形态）；② sweep 同样停摆（stale-CLAIMED 安全网失效）；③ 无任何「轮询滞后/上次成功时间」指标可区分「无消息 idle」与「消费者挂死」（F5：不抛异常也不产出的静默挂死不可见）。对照：消费方 `LocalAgentMessenger.HandlerAdapter:167` 对 handler 异常已有 catch（不挂死但耗时仍阻塞）。建议 C4 复核是否与 C4-013 的调度器收口一并加投递超时（handler 级 `orTimeout`）与滞后指标。
- **状态**: 未复核

## 维度复核结论（WI 3.1 Phase 4 独立复核）

复核人独立回码验证全部 16 条发现（逐条核对文件/行号/证据片段/触发时序/失效面枚举与 live code），并对照 `ai-dev/skills/concurrency-resource-audit-prompt.md` 口径纪律、`ai-dev/audits/cra-index.md` WI 1.1–2.5 台账、C3 维度复核登记的跨维指针与 2026-06 nop-ai-agent 历史审计查重。

### 逐条判定表

| 发现 | 初审严重度 | 判定 | 最终严重度 | 现状 | 核验要点 |
|---|---|---|---|---|---|
| C4-001 | P2 | 保留 | P2 | 新发现未修复 | 缺省 publisher→commonPool、终态回调同步盘写（serialize+3 正则+writeText）、双配置门均直核；同站 C3-002/C3-009 并单留 7.1 |
| C4-002 | P1 | 保留 | P1 | 新发现未修复 | 上游 3 处 isCancelled 静默 return + cleanup 无终态派发直核；头等待/体流式两窗口时序区分复核成立；同站 C3-001 |
| C4-003 | P2 | 保留 | P2 | 新发现未修复 | catch(Exception)+ScheduledFuture 丢弃+无 afterExecute 三要素直核；跨单元 tension（WI 1.7 C6-001 同形态 P1）已登记，差异可辩护 |
| C4-004 | P2 | 保留 | P2 | 新发现未修复 | 全类无 shutdown、beans.xml 无 ioc:stop、换装不处理旧池直核；修复指向 globalTimer 处须与 WI 2.1 C4-001 协调 |
| C4-005 | P2 | 保留 | P2 | 新发现未修复 | 接口无 ICancelToken、RETRY 无取消检查、封顶常量直核；对照组 AiCommand:334 成立 |
| C4-006 | P2 | 保留 | P2（有界池注入条件下面 P1 级） | 新发现未修复 | 同池嵌套+join、javadoc 与实现不符直核；handler 运行线程子项经 ToolManagerImpl executeNow 直调复核，两路径结构均成立；条件化降档有证据（缺省 cached 池无死锁）；家族对照 WI 1.5 MA-03 |
| C4-007 | P2 | 保留 | P2 | 新发现未修复 | 两处兜底止于 RuntimeException + renewHandle 无读取点 + 30min/10min 配置直核；**追加**：同站 C3-020 为 P3，第二处 P3/P2 分叉留 7.1 |
| C4-008 | P2 | 保留 | P2 | 新发现未修复（缺省 NoOp 锁下不可达已声明） | 单线程共享+同步条件 UPDATE+3 倍裕量直核；**修复建议修正**：globalTimer 选项与 WI 2.1 C4-001 冲突，取专用小池为主方向 |
| C4-009 | P2 | 保留 | P2 | 新发现未修复 | 两缺省点+生产接线专用 executor 直核；同族前案维度14-10（2026-06-15，P3）已实质修复，本条为残余不同站点非重复 |
| C4-010 | P1 | 保留 | P1 | 新发现未修复 | per-invocation 构造+close 零调用点+惰性 fixed 池直核；同站 C3-022；P1 非 P0 定级差正确 |
| C4-011 | P1 | 保留 | P1 | 新发现未修复 | 缺省 runAsync+唯一装配点+18 工具文件+cap=0 合法配置直核；P1 与 WI 1.2 C4-004/WI 2.3 C8-001 同族判级一致 |
| C4-012 | P1 | 保留 | P1 | 新发现未修复 | 无 remove+timeoutMs 无强制点+Error 面直核；同站 C3-023/C7-027；跨单元同族 WI 1.1 C1-006（P1）一致 |
| C4-013 | P2 | 保留 | P2 | 新发现未修复 | 三处兜底+scheduleHandle 无读取点直核；C3-027 P3 vs P2 分叉维持留 7.1（C4 侧意见：P2 成立，四案并入 WI 2.1 C3-001 绝缘层） |
| C4-014 | P2 | 保留 | P2 | 新发现未修复 | 单 bound 裸 execute vs fan-out 完整防护直核；同站 C3-025 |
| C4-015 | P2 | 保留 | P2 | 新发现未修复 | shutdownNow 无 drain+inFlightDispatches 自清失灵直核；inFlightDispatchCount javadoc 不变式恰被 stop 打破，佐证成立 |
| C4-016 | P2 | 保留 | P2（分叉登记，倾向 P1） | 新发现未修复 | 只建不删直核；**追加指针**：同站 C3-024(P1)/C7-026(P1) 三重登记未互指，P2 vs P1 分叉留 7.1 |

**判定统计**：保留 16 / 降级 0 / 驳回 0。第 1 轮证据质量整体为「确定」级（16/16 条核心机制均代码直证），无一条因时序不能具体化触发强制降档；round 2 零新增经独立普查复核维持。

### 最终保留项（严重度分布）

- **P1 ×4**：C4-002（取消路径 publisher 永不关闭）、C4-010（per-invocation spawn 池泄漏）、C4-011（工具体阻塞占用 commonPool，生产主路径）、C4-012（后台任务账本无界+超时无执行者）。
- **P2 ×12**：C4-001、C4-003、C4-004、C4-005、C4-006（有界池注入条件下面 P1 级）、C4-007、C4-008、C4-009、C4-013、C4-014、C4-015、C4-016（严重度分叉登记，倾向 P1）。
- **P0 ×0**；无降级、无驳回；条件化/分叉登记 3 条（C4-006 条件升档、C4-013 与 C4-016 严重度分叉）均留 7.1 终裁。

### 现状标注分布

- 新发现未修复 16/16（均未见于 cra-index P0/P1 台账与各单元工单，非重复立项）。
- 部署面限定 2 条：C4-007/C4-008 依赖功能性 `DbSessionTakeoverLock` 接入（缺省 NoOp 不可达）；C4-009 生产主路径不受影响（公开构造面暴露）。
- 同族历史前案 1 条：C4-009 对照 2026-06-15-1146 维度14-10（P3，`DefaultAgentEngine:661` supplyAsync 缺省 commonPool）——该旧站点现网已改 `getAgentExecutor()` 接线实质修复，本条为其残余不同站点。

### 同案命中清单（含跨维指针核对意见）

**同站点跨维双/三重登记（并单归 7.1，C4 侧指针全部核对成立）**：

| C4 侧 | 对侧 | 指针核对意见 |
|---|---|---|
| C4-002（P1） | C3-001（P1） | 成立，完全同站点（取消轴 vs 终态派发轴），并单后 publisher 关闭绑定一动多得 |
| C4-001（P2） | C3-002（P1 背压轴）/C3-009（P2 缺省池轴） | 成立，同站点三轴分立；本条 P2 与 C3-009 P2 一致，C3-002 的 P1 属背压轴独立后果，不并档 |
| C4-010（P1） | C3-022（P1） | 成立，同站同根，C3 侧已建议并单 |
| C4-012（P1） | C3-023（P1）+C7-027 | 成立；C4-012 载荷更重（timeoutMs 无执行点+F5 面），主处置归 C4-012 维持 |
| C4-013（P2） | C3-027（P3） | 成立，分叉留 7.1；C4 侧意见 P2（消息传输心跳+团队调度双停转、daemon 侧兜底更窄），修复归属不受分叉影响 |
| C4-014（P2） | C3-025（P2） | 成立，同站同根并单 |
| C4-007（P2） | C3-020（P3） | **任务指派未列，本复核补登记**：第二处 P3/P2 分叉，同型留 7.1 |
| C4-016（P2） | C3-024（P1）+C7-026（P1） | **任务指派未列，本复核补登记**：同站点三重登记且三案未互指；倾向统一 P1 口径 |

**跨单元同族对照（非同站，不并单，修复批次对齐）**：

| C4 侧 | 台账同族 | 对齐要点 |
|---|---|---|
| C4-006 | WI 1.5 C3-002（MA-03 同池嵌套死锁族，P1）、WI 1.1 C4-001 | 修复模式对齐（独立执行器/虚拟线程+有界池守卫）；判级差有证据（本条缺省 cached 池不死锁） |
| C4-011 | WI 1.2 C4-004（MA-06 阻塞体占共享池，P1）、WI 2.3 C8-001（WS 钉死 globalTimer，P1） | 平台池占用族 P1 判级一致；C3-002 侧已引 WI 1.2 C4-004 同案 |
| C4-003/C4-007/C4-013 | WI 2.1 C3-001（周期任务无统一异常绝缘层，29 站点族，P1）、WI 1.6 C8-001、WI 1.7 C6-001 | nop-ai 站点为该绝缘层族的模块侧扩展；四案（加 C3-020/027）应并入同一平台绝缘批次；1.7 C6-001 P1 与本族 P2 的判级差源于持久 RUNNING 行+资源泄漏多层后果，登记备 7.1 校准 |
| C4-004/C4-006/C4-007/C4-008/C4-009/C4-010/C4-013 | WI 2.1 CL-021（12+ 处裸 JDK 载体绕过设施，移交面点名 ai-agent） | 本模块 7 处裸 `Executors.*`/缺省 commonPool 站点为该移交面的具体化；C3 侧（C3-009/CL-025）已承接确认，C4 侧此处补登记 |
| C4-002 失效面 ④ | WI 2.3 C3-003（BufferedStreamingPublisher 背压断裂，P1） | 下游消费方面相邻，已在条目登记引用不重复立案 |
| C4-008/C4-012/C4-016 修复建议中的 `GlobalExecutors.globalTimer()` 指向 | WI 2.1 C4-001（globalTimer 死配置+4 线程共享阻塞放大，P1）、WI 1.5 MA-04 | **警示**：三条的「复用平台设施」建议在 WI 2.1 C4-001 修复落地前不得直接指向 globalTimer，否则新增占用方复刻 MA-04/C8-001 形态；7.1 排批次时须先平台后使用方 |

### 高风险条目子项复核说明

1. **C4-006（同池嵌套）**：子项「handleCallAgentRequest 在父池线程运行」经 `ToolManagerImpl.callTool` 实现复核——未排队路径 `executeNow.get()` 在调用线程（父 agentExecutor 线程）内联执行，排队路径在 `thenCompose` 完成线程执行；两种路径下「向 getAgentExecutor() 提交子执行 + 阻塞 join」结构不变，时序取未排队路径成立。条件化降档（P2/有界池注入下面 P1）核对：缺省 `getAgentExecutor()` 为 cached 无上界（死锁不可达，仅线程放大），级联需运维注入固定池——条件有代码证据，符合「条件化降档须给证据」。
2. **C4-002（取消不关闭）**：两窗口时序区分逐分支复核——头等待期取消：`cleanup`→`future.cancel(true)` 使 whenComplete 以异常完成→`onError`（`AbstractServerEventSubscription.onError` 不受 isCancelled 门控）→`publisher.closeExceptionally` 正常终止；体流式期取消：future 已正常完成（cancel no-op）→`parseEvents`/executor 块门控静默 return→无终态。分叉点准确，P1 维持。
3. **C4-011（commonPool 阻塞占用）**：阻塞体清单抽验（BashExecutor 全 body 经 `context.getExecutor().submit` 下沉 commonPool，18 文件族）+ cap<=0 禁超时合法路径 + 300s 上限配置三子项直核；P1 与跨单元同族判级一致，维持。
4. **C4-012/C4-016（账本/订阅无界）**：全文件 grep 复验零 remove/零强制点；两条的 P1/P2 判级对照其同站登记（C3-023 P1 / C3-024+C7-026 双 P1）呈现同缺陷跨维判级漂移，已作为分叉统一留 7.1，避免 7.1 前单侧改档造成新漂移。
5. **C4-008（globalTimer 修复建议冲突）**：复核发现建议①选项 A（改用 globalTimer）与 WI 2.1 C4-001 直接冲突，已在条目复核状态行修正为「专用小型多线程调度池为主方向」；此为建议层面的修正，不动摇缺陷本体与 P2 判级。

## 最终保留项

C4 维度最终保留 **16 条**（P1×4、P2×12，P0×0，无降级无驳回），全部为新发现未修复：

| 严重度 | 条目 |
|---|---|
| P1 | C4-002（callStream 取消路径 publisher 永不关闭）、C4-010（TeamExecuteFlowExecutor per-invocation spawn 池泄漏）、C4-011（AgentDispatchExecutor 工具体阻塞占用 commonPool）、C4-012（DefaultBackgroundTaskRegistry 账本无界+超时无执行者） |
| P2 | C4-001（缺省 commonPool 承载流式派发+终态盘写）、C4-003（embed 重试 Error 逃逸零观测挂死）、C4-004（重试调度器无关闭路径）、C4-005（embedding 链无取消通道）、C4-006（call-agent 同池嵌套，有界池注入条件下面 P1 级）、C4-007（续期/恢复周期任务 Error 静默停转）、C4-008（续期单线程队头阻塞，租约 3 倍裕量）、C4-009（SingleTurnExecutor 缺省 commonPool）、C4-013（poller/daemon Error 静默停转）、C4-014（单 bound 节点无超时防护）、C4-015（取消/关闭不可达已提交 spawn 工作）、C4-016（reply 订阅只增不删，严重度分叉登记倾向 P1） |

**留 7.1 事项**：① 三组同站并单（C4-002↔C3-001、C4-010↔C3-022、C4-012↔C3-023/C7-027）与两组严重度分叉（C4-013↔C3-027 P2/P3、C4-016↔C3-024/C7-026 P2/P1）及本复核补登记的 C4-007↔C3-020 P2/P3 分叉；② C4-006 条件升档口径；③ 「复用 globalTimer」类修复建议与 WI 2.1 C4-001 的批次先后；④ WI 2.1 C3-001 平台绝缘层批次纳入 nop-ai 四周期任务站点（C4-003/C4-007/C4-013 及 C3-020/027 对应面）。
