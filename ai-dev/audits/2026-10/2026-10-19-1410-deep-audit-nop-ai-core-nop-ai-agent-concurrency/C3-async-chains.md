# C3 异步链路正确性 — nop-ai-core-nop-ai-agent（WI 3.1）

## 审计范围与口径

- **批次 X1/3 覆盖分区**：`nop-ai/nop-ai-core/src/main/java` 全量（非 `_gen` 101 文件 / 14,176 行；`_gen/` 与 `_` 前缀生成类 18 文件按审计纪律排除）。批次 X2/X3 覆盖 `nop-ai-agent` 等后续分区，发现编号由后续批次续接（自 C3-010 起）。
- **批次 X2/3 覆盖声明**：`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent` 下 6 包——`security`（74 文件）、`plan`（44 文件手写）、`engine`（47 文件）、`runtime`（34 文件，含 coordination/lock/recovery）、`model`（17 文件手写）、`reliability`（30 文件）。完整读审 engine 异步主战场（`DefaultAgentEngine`/`AgentSessionLifecycle`/`AgentToolDispatcher`/`LlmCallCoordinator`/`ReActAgentExecutor` 关键段/`AgentExecutionContext`/`AgentCompactionCoordinator`/`SingleTurnExecutor`/`AgentCallDelegate`/`SessionLockRenewal`/`DefaultAgentEventPublisher`/`AgentSessionSupport`）+ runtime 全部异步协调面（`InMemoryActorRuntime`/`AgentActor`/`DbSessionTakeoverLock`/`DbDaemonCoordinator`/`ScheduledRecoveryManager`/两个 functional recovery handler）+ reliability（`DefaultWaitCoordinator`/`SisypheanSustainer`/checkpoint 管理器结构核查）+ security 异步面（`DockerSandboxBackend`/`NoOpSandboxBackend` reader 线程与 kill 路径、`ThreadLocalTenantResolver`）；`plan`/`model` 包 grep 全量扫描确认无异步入口（纯同步执行模型）。降档口径：本分区全部异步面为请求触达/长时运行路径，启动期降档不适用。
- **批次 X3/3 覆盖声明（C3 收官批）**：`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent` 下 team（55 文件，含 flow/scheduler）、tool（21）、session（21）、message（18）、memory（17）五包 + 16 小包（budget/compact/completion/conflict/contribution/guardrail 含 rule//hook/middleware/quota/recipe/repair/router/skill/talent/usage）+ 根（NopAiAgentErrors/NopAiAgentException/SessionIds）。完整读审异步主战场：`MemberFanOutDispatcher`/`TeamTaskFlowOrchestrator`/`MemberAgentTaskStep`/`SpawnMemberAgentTaskStep`/`BoundMemberFanOutStep`/`AllMustSucceedReduction`/`ExecutionRecorder`/`DefaultMemberSpawner`/`TeamTaskSchedulerDaemon`/`TaskDispatchCoordinator`/`InMemoryTeamTaskStore`/`LocalAgentMessenger`/`DBMessageService`/`AiAgentMessageTable`/`MailboxMessageHandler`/`DeferredAckMailbox`/`CallAgentExecutor`/`DefaultBackgroundTaskRegistry`/`AgentDispatchExecutor`/`TaskOutputExecutor`/`TaskStopExecutor`/`SendMessageExecutor`/`TeamExecuteFlowExecutor`/`ThreadLocalTenantResolver`/`FileBackedSessionStore`/`DBSessionStore`（关键段）；其余 session/memory/compact/16 小包文件按 C3 要点（CF 链/回调/调度器/共享可变状态）grep 全量扫描逐一核查（InMemoryWriteIntentRegistry/InMemoryContributionRegistry/FileSystemSkillProvider/DefaultHookRegistry/DefaultResourceGuard/InMemorySpillStore 等命中点已人工复核，无 C3 级缺陷面）。降档口径：本分区全部异步面为请求触达/长时运行路径，启动期降档不适用。C3 三批次（X1 nop-ai-core 全量 + X2 nop-ai-agent 六包 + X3 本批）至此收官。
- **方法**：完整读审 C3 主战场文件 `command/AiCommand.java`（406 行）、`service/ChatServiceImpl.java`（625 行）、`service/EmbeddingServiceImpl.java`（446 行）、`reliability/` 包全部 21 文件、`persist/` 4 文件、`service/DefaultChatLogger.java`/`ChatLogHelper.java`/`LlmConfigHelper.java`、`dialect/` 全部 7 文件（共享可变状态核查）、`prompt/PromptTemplateManager.java`；其余模型/配置/解析类（`model/`、`response/`、`file/`、`simplifier/`、`api/`）按 C3 要点（CF 链 / Flow / 回调 / 调度器）grep 全量扫描确认无异步入口。
- **异步入口盘点（C3 步骤 1 口径）**：`grep thenApply|thenCompose|whenComplete|exceptionally|orTimeout|runAsync|supplyAsync|new CompletableFuture|SubmissionPublisher|Flow.` 全模块命中仅 3 个文件——`EmbeddingServiceImpl`（9 处）、`AiCommand`（5 处）、`ChatServiceImpl`（4 处）+ `AiGenHelper`/`LlmErrorClassifier` 注释文本。异步面收敛于上述三文件，无漏盘。
- **跨模块契约证据**：`IChatService` 的 SSE 上游由 `nop-network/nop-http-client-jdk` 的 `ServerEventPublisher`/`AbstractServerEventSubscription` 提供；本文引用其行为（cancel 无终止信号、submit 阻塞语义）作为触发时序的契约证据，缺陷定位在 nop-ai-core 侧的使用方式。
- **口径纪律**：严重度按共享前缀校准；写不出具体时序的降 P2 标注；降档口径声明——本模块全部异步面均为请求触达路径，无"启动期单线程降档"适用场景；`ThresholdBreaker`/`ProviderFailoverQueue` 的 per-key compute 临界区实现本身经核查无 C3 级缺陷（见 CL-017 的语义线索，非缺陷条目）。
- **盲审合并说明**：本文件经两轮独立盲审读合并产出。第二轮复核逐条对照本机 JDK 26 `src.zip` 核实 `SubmissionPublisher` 契约（subscriber 回调经 publisher executor **异步**派发——`ConsumerTask`/`tryStart`，SubmissionPublisher.java:1205-1213；onError/onComplete 内抛出"not recorded or handled before cancellation"，javadoc L100-110），据此新增 C3-009（下游派发执行器缺省 commonPool）、修正 C3-003 的回调线程归属（publisher 消费线程而非 SSE reader 线程，悬挂结论不变）。

## 发现

### [C3-001] callStream 取消传播单向：cancelToken 取消后下游 SubmissionPublisher 永不关闭（悬挂流）

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/ChatServiceImpl.java:202-233`
- **证据片段**:
  ```java
  SubmissionPublisher<ChatStreamChunk> publisher = new SubmissionPublisher<>();
  Flow.Publisher<IServerEventResponse> eventPublisher = httpClient.fetchServerEventFlow(httpRequest, cancelToken);
  eventPublisher.subscribe(new Flow.Subscriber<>() {
      @Override
      public void onSubscribe(Flow.Subscription subscription) {
          subscription.request(Long.MAX_VALUE);
          if (cancelToken != null) {
              cancelToken.appendOnCancelTask(subscription::cancel);   // 只取消上游
          }
      }
      ...
      public void onError(Throwable throwable) { publisher.closeExceptionally(throwable); }
      public void onComplete() { publisher.close(); }
  });
  return publisher;   // 取消路径下 close/closeExceptionally 均不可达
  ```
- **严重程度**: P1
- **并发场景**: 调用方请求线程（注册 cancel 任务）× SSE reader 线程（`ServerEventPublisher.executor()` = JDK client executor 或 `GlobalExecutors.globalWorker`）× 下游订阅者线程。三个线程经 `SubmissionPublisher` 桥接。
- **触发时序**: T1 用户/上层取消 cancelToken（如会话中止）→ `appendOnCancelTask(subscription::cancel)` 执行 → 上游 `AbstractServerEventSubscription.cleanup` 置 `isCancelled`（nop-http-client-jdk `AbstractServerEventSubscription.java:84-91`）；reader 线程在 `if (isCancelled()) return;`（`ServerEventSubscription` executor 块 / `parseEvents`）**静默退出，不回调 onError/onComplete**（Flow 规范允许 cancel 后不发终止信号）。T2 桥接 subscriber 从此收不到任何信号 → `publisher` 既不 `close()` 也不 `closeExceptionally()` → 返回给下游的流永不终止。下游 `aggregateStreamToResponse` 的 `future`（L393）永不完成，`AiCommand.execute()` 的 `FutureHelper.syncGet`（AiCommand.java:273-275，无超时）**永久阻塞调用线程**；nop-ai-gateway `FailoverStreamFlow` 等直接订阅 `callStream` 的消费者同样悬挂并泄漏订阅注册。
- **现状**: 取消信号只向上游传播（subscription::cancel），缺少向下游的终止信号（publisher.close）——资源清理不对称（F8）。
- **风险**: 每次用户中止都产生一个悬挂流 + 挂起的聚合 future + 阻塞的调用线程；`execute()` 无超时兜底，阻塞不可自愈；悬挂的 `SubmissionPublisher` 及其订阅注册按次累积（长时运行内存增长）。
- **建议**: `appendOnCancelTask` 中同时闭合下游：`cancelToken.appendOnCancelTask(() -> { subscription.cancel(); publisher.close(); })`（或 `closeExceptionally(new CancellationException(...))` 让下游可区分取消与正常完成）。注意 lambda 需捕获 `publisher`，在 L205 注册点之前完成赋值。
- **失效面枚举**: (a) `ChatServiceImpl.callStream` L202-233——主缺陷（本条）；(b) `ChatServiceImpl.aggregateStreamToResponse` L391-440——同族：其内部 future 无任何取消联动（调用方 cancel 返回的 CompletableFuture 不停止聚合、不取消上游），同族未修；(c) `EmbeddingServiceImpl.embedAllAsync` L263 `fetchAsync(request, null)`——cancel 面整体缺失（独立立条 C3-004）；(d) `AiCommand.callChat` L334-336——有取消预检 + CancellationException future（良性对照，无需修）。
- **信心水平**: 确定
- **误报排除**: 非"原语使用"误报——`SubmissionPublisher` 单用正确，缺陷在复合语义（上游 cancel 契约允许静默终止，本侧未闭合下游信号）；也非已声明边界：`IChatService.callStream` javadoc 未声明"取消后下游流可不终止"。
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现——回码核验成立：L205 appendOnCancelTask 仅 subscription::cancel、onError/onComplete 均 close 面核验；同站点跨维指针 C4-002（C4 轴 P1），同族先例 WI 2.4 C3-001（future 永不完成→悬挂）

### [C3-002] callStream 背压缺失：下游慢消费经 SubmissionPublisher.submit 无中断阻塞 SSE reader 线程（共享 globalWorker 线程池饥饿）

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/ChatServiceImpl.java:202-233`
- **证据片段**:
  ```java
  SubmissionPublisher<ChatStreamChunk> publisher = new SubmissionPublisher<>();  // 默认 buffer=256
  ...
  public void onSubscribe(Flow.Subscription subscription) {
      subscription.request(Long.MAX_VALUE);      // 上游无界拉取，与下游需求完全解耦
  }
  public void onNext(IServerEventResponse item) {
      ChatStreamChunk chunk = dialect.parseStreamChunk(item.getData());
      if (chunk != null) {
          publisher.submit(chunk);   // 下游 buffer 满时在本线程 uninterruptibly 阻塞
      }
  }
  ```
- **严重程度**: P1
- **并发场景**: SSE reader 线程（`ServerEventPublisher.executor()` fallback 为 `GlobalExecutors.globalWorker`——平台共享 worker 池）与下游订阅者（内部聚合器或外部消费者，如网关流式转发）速度解耦；上游按 MAX_VALUE 全速拉取。
- **触发时序**: T1 下游订阅者消费慢或长时间不 `request`（网关背压、下游 GC 停顿、消费者 bug）→ `SubmissionPublisher` 默认 256 缓冲填满（LLM 流式输出 routinely 数百至上千 delta chunk，256 上限极易触达）；T2 reader 线程执行 `publisher.submit(chunk)`，按 JDK 语义 **uninterruptibly 阻塞**直至缓冲有空间；后果：该 reader 占用的线程若为 `globalWorker` 共享线程，则其他 LLM 流与平台任务被排在同池饥饿；取消（cancel → cleanup → `future.cancel(true)`）与线程中断均无法解除该阻塞（submit 不可中断），只能等下游恢复或 TCP 层断连制造 reader 异常。
- **现状**: 上游 `request(Long.MAX_VALUE)` 与下游需求之间无背压桥接；`SubmissionPublisher` 默认配置（buffer 256、submit 阻塞语义）被直接暴露为公共 API 返回值。
- **风险**: 负载上升 + 下游减速的组合必然触达；单条慢流可钉住共享 worker 线程，多条并发慢流耗尽共享池，形成平台级流式调用 stall（校准中"负载上升必然触发的队列溢出/线程饥饿"P1 族）。
- **建议**: 有序方案：(1) `aggregateStreamToResponse` 内部路径已是 `request(Integer.MAX_VALUE)` 全速聚合不受影响，重点修 `callStream` 公共面——用有界 drop 策略替代阻塞：改 `offer(chunk, (sub, item) -> { ...; return false; })` + 溢出时对上游 `subscription.request(1)` 节流，或引入平台 `IBlockingQueue`/`QueueOverflowPolicy`（`io.nop.commons.concurrent`）做显式溢出策略；(2) 至少为 reader 线程与 globalWorker 解耦（专用 executor），并登记 `GlobalExecutors.globalWorker` fallback 在长阻塞任务下的暴露面。
- **失效面枚举**: (a) `callStream` L202-233 桥接——主缺陷（本条）；(b) `aggregateStreamToResponse` L400-437 订阅者 `request(Integer.MAX_VALUE)`（L403）——全速聚合，本身不触发，良性；(c) 全模块 grep `SubmissionPublisher` 仅此一处构造——无同类兄弟漏修。
- **信心水平**: 确定（JDK `SubmissionPublisher.submit` 阻塞语义为规范行为）
- **误报排除**: 非"原语使用误报"校准项——问题不在 SubmissionPublisher 本身，而在桥接层显式 `request(Long.MAX_VALUE)` 放弃背压 + 将默认阻塞语义暴露到共享线程池上；也不是有界声明覆盖的场景（256 buffer 的存在恰是阻塞触发条件而非防护）。
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现——回码核验成立：L207 request(Long.MAX_VALUE)、onNext publisher.submit 直核；与 nop-stream 异步族同案对照：WI 1.2 C4-004（阻塞任务占共享 globalWorker 池，P1）；同站点跨维指针 C4-001（缺省池面 P2）

### [C3-003] 聚合流终止回调先做可失败日志 I/O 再 complete future：日志抛异常 → 返回 future 永不完成（悬挂）

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/ChatServiceImpl.java:411-436`
- **证据片段**:
  ```java
  public void onError(Throwable throwable) {
      ChatResponse errResponse = parseStreamError(throwable, dialect, config);
      if (errResponse != null) {
          ...
          if (logMessage) {
              chatLogger.logResponse(request, errResponse);   // 文件 I/O，可抛出
          }
          future.complete(errResponse);                       // 抛出则不可达 → 悬挂
      } else {
          future.completeExceptionally(throwable);
      }
  }
  public void onComplete() {
      ChatResponse response = aggregator.toResponse();
      ...
      if (logMessage) {
          chatLogger.logResponse(request, response);          // 同型
      }
      future.complete(response);
  }
  ```
- **严重程度**: P1
- **并发场景**: `SubmissionPublisher` 消费线程（JDK 语义：subscriber 回调经 publisher 的 executor **异步**派发——本处 publisher 以无参构造于 ChatServiceImpl.java:202，实际执行线程为 `ForkJoinPool.commonPool`，见 C3-009）× 磁盘/文件系统状态；`DefaultChatLogger.logResponse` 内 `ResourceHelper.writeText`（DefaultChatLogger.java:44-60/62-79）无 try/catch。JDK 契约（SubmissionPublisher javadoc L100-110）：onError/onComplete 内抛出的异常**不记录、不处理**，仅取消该订阅——complete 调用被跳过后无任何补救路径。
- **触发时序**: T1 流式响应正常完成或收到 onError，publisher 消费线程（commonPool）执行 `chatLogger.logResponse`；T2 日志落盘失败（磁盘满、目录权限、`logDir` 指向的挂载点不可用）抛出异常 → JDK 仅静默取消订阅，`future.complete(...)`（L420/L435）未执行且无任何兜底 → `aggregateStreamToResponse` 返回的 future 永不完成；T3 上游 `AiCommand.execute()`（AiCommand.java:274）`FutureHelper.syncGet` 无超时等待 → 调用线程永久阻塞，且 retryNTimes 链、响应缓存全部停摆。
- **现状**: complete 路径依赖一个不受本模块控制的 fallible 副作用（日志 I/O）先行成功；异常既未吞也未路由到 completeExceptionally（F9 + 组合链悬挂）。
- **风险**: 存储故障从"日志降级"放大为"LLM 调用整体悬挂 + 请求线程池逐渐耗尽"；错误路径（onError 分支）同样暴露——本来要交付的错误响应也因日志失败变成悬挂。
- **建议**: 回调内 try/catch 包裹日志调用（失败仅 LOG.warn），或调整顺序先 `complete` 后记日志；同类兜底可复用 `FutureHelper.thenCompleteAsync` 的 handler try/catch 形态。另建议在 `aggregateStreamToResponse` 上加 `orTimeout`（与 C3-007 合并治理）。
- **失效面枚举**: (a) `aggregateStreamToResponse.onError` L412-424——logResponse 在 complete 前（本条）；(b) `aggregateStreamToResponse.onComplete` L427-436——同型（一并修）；(c) `callAsync` 非流式 `thenApply` L161-163/171-173——logResponse 抛出使成功响应变 exceptionally（不悬挂，轻症同族，修复时应统一）；(d) `callStream` L193-195 `logRequest` 同步抛出——传播为调用线程同步异常（归入 C3-005 同族登记）；(e) `chatLogger` 未注入时 onComplete/onError 内 NPE——同一悬挂形态（IoC 可选注入面，随 (a)(b) 一并兜底）。
- **信心水平**: 确定（抛出面为文件 I/O；触发需存储故障，但后果是悬挂而非失败）
- **误报排除**: 非 F9"吞异常"的镜像误报——这里异常未吞而是**阻断了 complete**，后果（悬挂）比吞掉更重；也非测试专用代码豁免面（生产 chat 链路）。
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现——回码核验成立：onError/onComplete 内 logResponse 先于 future.complete、无 try/catch 直核；chatLogger 可选注入 NPE 面同核验（L73 字段 + shouldLogMessage 无判空）；RetryHelper._retryNTimes 无 catch 复证 syncGet 悬挂不可自愈

### [C3-004] EmbeddingServiceImpl 幽灵重试：消费者取消返回 future 后重试/账号链 fallback 仍继续发真实请求

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/EmbeddingServiceImpl.java:257-281`（dispatch/whenComplete）, `345-378`（handleFailure）, `443-445`（scheduleRetry）
- **证据片段**:
  ```java
  httpClient.fetchAsync(request, null).whenComplete((resp, err) -> {   // cancelToken=null
      try {
          if (err != null) {
              onTransportError(err, future);
          } ...
  ```
  ```java
  RetryOutcome outcome = retryPolicy.shouldRetry(ctx);
  if (outcome.isRetry()) {
      long delayMs = Math.max(0, outcome.getDelayMs());
      scheduleRetry(delayMs, () -> dispatch(future));   // 不检查 future.isDone()
      return;
  }
  ```
- **严重程度**: P2
- **并发场景**: 消费者线程（对返回的 `CompletableFuture` 调 `cancel(true)`，典型为调用方超时放弃）× 单线程 retry scheduler（`nop-ai-embedding-retry`）× http 回调线程；三者经 `EmbedCall` 可变状态（`attempt`/`fallbackSteps`/`currentAccount`，单飞行串行，无数据竞争）与共享 API 配额交互。
- **触发时序**: T1 消费者超时放弃，调用 `future.cancel(true)` → future 进入 cancelled 态；T2 retryPolicy 判 RETRY → `scheduleRetry` 照常排程 → `dispatch` 再发真实 HTTP 请求；T3 响应回来后 `future.complete(...)` 返回 false 被静默忽略，`handleFailure` 继续按策略 RETRY/FALLBACK——幽灵请求链持续到 STOP/链耗尽。最坏 `maxAttempts(3) × (1 + MAX_FALLBACK_STEPS(16))` 量级真实请求在消费者已放弃后继续消耗全局 API 配额、推进限流计数。
- **现状**: `EmbedCall` 状态机仅以 retry policy 为终止条件，不感知返回 future 的取消/完成态；`fetchAsync` 的 cancelToken 参数传 `null`，平台级取消通道也未接。
- **风险**: 大批量 RAG 嵌入场景下，上游超时批量取消 → 幽灵请求放大对 provider 的请求量（与限流器语义对抗），并拖长 scheduler 队列占用；虽次数有界，属取消路径资源浪费与配额误耗。
- **建议**: `dispatch`/`scheduleRetry` 前检查 `future.isDone()`（cancelled 即停止）；`IEmbeddingModel` 若保持无 cancelToken 契约，则在 javadoc 显式声明"取消仅解除等待、不停止底层调用"作为边界，否则补 cancelToken 参数并下传 `fetchAsync`。
- **失效面枚举**: (a) `handleFailure` RETRY 分支 L352-356——无 isDone 检查（本条主面）；(b) `handleFailure` FALLBACK 分支 L357-376——同步 `dispatch(future)` 同样不查取消（一并修）；(c) `dispatch` whenComplete L263-275——对已完成 future 的 complete 静默忽略（与 (a)(b) 同根）；(d) `ChatServiceImpl.callAsync/callStream`——正确传递 cancelToken（良性对照）；(e) `AiCommand` 重试链——callChat 预检 + CancellationException future 使 retryNTimes 停止（良性对照）。
- **信心水平**: 确定
- **误报排除**: 非有界声明覆盖——无任何文档声明"取消后请求会继续"；后果按次有界故不定 P1，但触发链完全具体（一次 cancel 即触发）。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现——回码核验成立：fetchAsync(request, null)（L263）、handleFailure RETRY/FALLBACK 两分支均无 future.isDone 检查、scheduleRetry 直排程；后果按次有界，P2 校准正确

### [C3-005] callAsync 同步抛出逃逸异步错误包络：限流/无 baseUrl/凭证失效绕过重试计数与 returnExceptionAsResponse 转换

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/command/AiCommand.java:303-307`（retry lambda）, `333-341`（callChat）; `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/ChatServiceImpl.java:363-367`（checkRateLimit 抛出点）
- **证据片段**:
  ```java
  // AiCommand.callChat — chatService.callAsync 在返回 CompletionStage 前可同步抛出
  return chatService.callAsync(request, cancelToken)
          .thenApply(response -> new ChatExchange(request, response));
  ```
  ```java
  // ChatServiceImpl.checkRateLimit — 同步抛 NopException(ERR_AI_RATE_LIMITED)
  if (!rateLimiter.tryAcquire(1, timeoutMs)) {
      throw new NopException(ERR_AI_RATE_LIMITED)
              .param(ARG_LLM_NAME, provider)
              .param(ARG_HTTP_STATUS, 429);
  }
  ```
- **严重程度**: P2
- **并发场景**: 调用方线程直接执行 `callAsync` 前置段（配置加载、限流 tryAcquire、凭证解析）；无第二线程参与——缺陷形态是同步异常逃逸异步契约，而非数据竞争。
- **触发时序**: T1 provider 限流配额耗尽 → `checkRateLimit` 在 `callAsync` 返回前同步抛 `NopException`；T2 异常沿 `AiCommand.callChat`（L339）→ `executeOnceAsync`（L345）→ `retryNTimes` lambda（L303）同步传播——`RetryHelper._retryNTimes`（nop-commons RetryHelper.java:80-90）对 `task.apply` 无 try/catch，`thenCompose` 包络未建立 → `executeAsync` 以同步异常结束。后果：(1) `executeOnceAsync` 内实现 `returnExceptionAsResponse=true` 错误包络的 `thenCompleteAsync`（L354-370）从未被挂上——调用方拿到裸异常而非 invalid ChatExchange；(2) `retryTimesPerRequest` 重试未计数（限流恰是最应退避重试的错误之一，且其 429 分类本可路由账号链 fallback）。
- **现状**: 同一 LLM 失败有两种交付形态——已发出 HTTP 后的失败走 future（被包络转换），发出前的失败走同步 throw（绕过包络）；`IChatService.callAsync` 契约未声明同步抛出面。
- **风险**: 上层按"拿到 ChatExchange（含 invalidReason）"编程的调用方（`ai:Execute` 标签、脚本）对限流场景收到未封装异常，错误处理路径分裂；重试语义静默失效。
- **建议**: 在 `AiCommand.callChat`（或 `callAsync` 实现内部）将前置段包进 `try { ... } catch (Throwable e) { return FutureHelper.reject(e); }`，统一为异步失败形态；或最小改动：`retryNTimes` 的 lambda 内 catch 后返回 `FutureHelper.reject`，使其至少进入重试与包络。
- **失效面枚举**: (a) `callAsync` 同步抛出点全集：`checkRateLimit`（ChatServiceImpl L131/L185 调用，L363-367 抛出）、`LlmConfigHelper.loadConfig`（L127）、`resolveBaseUrl`（L333 ERR_AI_SERVICE_NO_BASE_URL）、`resolveApiKeyForRequest` 凭证 fail-closed NopException（L300-301，javadoc 声明"不吞，异常向上传播中止调用"——声明的同步性止于 callAsync 契约，未声明对 AiCommand 包络的穿透）；(b) `callStream` 前置段 L180-199 同步抛出（流式同族）；(c) `EmbeddingServiceImpl.embedAllAsync` L147-171 同构同步抛出面（blank text/no provider/unsupported style/no embed url/rate limit）——登记同族；(d) `AiCommand` 缓存路径 L293（load 异常已 catch，良性对照）。
- **信心水平**: 确定
- **误报排除**: 非"错误处理维度越界"——本条报的是异步契约一致性（sync/async 失败形态分裂导致包络与重试失效），属 C3 异常传播范畴；非 owner doc 已声明边界（javadoc 仅声明 credential 抛出"中止调用"，未声明绕过 returnExceptionAsResponse）。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现——回码核验成立：checkRateLimit L363-366 同步抛 NopException、callChat L339 直调 callAsync、RetryHelper 无 try/catch（nop-commons RetryHelper.java:80-90 复核）——包络与重试双双旁路成立

### [C3-006] 响应缓存读/写异常语义不对称：load 失败降级、save 失败使成功的 LLM 响应以异常交付

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/command/AiCommand.java:291-301`（load 有 catch）, `309-315`（save 无 catch）; `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/persist/DefaultAiChatResponseCache.java:70-73`; `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/persist/IChatExchangePersister.java:12-14`（default save → `ResourceHelper.writeText`）
- **证据片段**:
  ```java
  if (chatCache != null) {
      try {
          ChatExchange cached = chatCache.loadCachedResponse(request, options, promptName);
          ...
      } catch (Exception e) {
          LOG.info("nop.ai.load-cache-fail:promptName={}", promptName);   // 读失败 → 降级
      }
  }
  ...
  if (chatCache != null) {
      return promise.thenApply(res -> {
          if (res.isValid()) {
              chatCache.saveCachedResponse(res, promptName);   // 写失败 → 整个请求 exceptionally
          }
          return res;
      });
  }
  ```
- **严重程度**: P2
- **并发场景**: 缓存写发生在重试链完成线程（http 回调线程或 scheduler 线程）上的同步文件 I/O（`writeText` + 可选 AES 加密 `AESTextCipher`，DefaultChatExchangePersister L41-45）；与消费者线程经 future 交互。
- **触发时序**: T1 LLM 调用成功、重试链交付有效 ChatExchange；T2 完成线程执行 `saveCachedResponse` → `ResourceHelper.writeText` 抛 I/O 异常（磁盘满/权限/路径创建失败）；T3 `thenApply` 使整个 `executeAsync` future 变 exceptionally——注意此点在 `executeOnceAsync` 的 `returnExceptionAsResponse` 包络（L354-370）**之后**，异常不再被转换为 invalid 响应。结果：已消耗 token 的成功响应以失败交付，与 load 路径"缓存故障仅降级"（L298-300）语义不对称。
- **现状**: 缓存被当作"尽力而为"的读加速，但写路径是"必须成功"的强依赖——同一子系统两种失效语义。
- **风险**: 存储抖动期间所有启用响应缓存的请求在 LLM 成功后报错；`postProcess`/输出解析结果随响应一起丢失，调用方重试将再次消耗 token。
- **建议**: `thenApply` 内 try/catch 包裹 `saveCachedResponse`（失败 LOG.warn 后照常返回 res），与 load 路径对齐；或将 save 改为 `FutureHelper.thenCompleteAsync`/`whenComplete` 尾挂不影响主结果。附带补强：load 的 catch（L298-300）当前以 `LOG.info` 只打 promptName、**异常对象整体丢弃**（无堆栈无消息），坏缓存只能靠一行 info 观测——修复时将 throwable 传入日志并升为 WARN。
- **失效面枚举**: (a) `executeAsync` save 点 L312——模块内唯一缓存写点，无其他兄弟写点漏修；(b) 对照 load 点 L293——已有降级（本条即补其镜像面）；(c) `DefaultChatLogger.logResponse` 写文件失败——已在 C3-003 立条（不同根因：悬挂 vs 失败交付）；(d) `DefaultAiChatResponseCache.loadCachedResponse` 的 `resource.delete()`（过期清理 L46-49）抛 I/O——被 AiCommand 的 catch 覆盖（良性）。
- **信心水平**: 确定
- **误报排除**: 非"资源清理不对称"泛化——具体为同一缓存子系统读/写异常语义矛盾且有明确交付后果；F2"半个成功"族的读路径镜像（成功已取得、附加写失败否定整体）。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现——回码核验成立：load 有 catch（LOG.info 丢 throwable）、save 在 thenApply 内裸调且位于 executeOnceAsync 包络之后——读/写语义不对称逐行确证

### [C3-007] `ChatOptions.requestTimeout` 契约被静默忽略：per-request 超时无消费点，全链路无 orTimeout 兜底

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/command/AiCommand.java:153`; `nop-ai/nop-ai-api/src/main/java/io/nop/ai/api/chat/ChatOptions.java:91-94`; `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/ChatServiceImpl.java:272`
- **证据片段**:
  ```java
  // ChatOptions.java（nop-ai-api 契约）
  /**
   * 请求超时时间（毫秒）
   */
  private Long requestTimeout;
  ```
  ```java
  // ChatServiceImpl.buildHttpRequest — 唯一超时来源是全局配置，options 未参与
  httpRequest.setTimeout(CFG_AI_SERVICE_READ_TIMEOUT.get());
  ```
- **严重程度**: P2
- **并发场景**: 调用方线程设置 options → 请求/回调线程执行链；无超时守卫的 future 跨线程交付。
- **触发时序**: T1 调用方按契约设置 `options.setRequestTimeout(5000)`（或经 `AiCommand.chatOptions(map)` L153 的 `requestTimeout` 键）；T2 `buildHttpRequest` 忽略该值、仅用全局 `CFG_AI_SERVICE_READ_TIMEOUT`；T3 LLM 供应商在全局超时内缓慢滴流（SSE 空闲心跳场景下 read-timeout 可能长期不触发），`aggregateStreamToResponse` future（L393）与非流式 future（L147）均无 `orTimeout`/`completeOnTimeout` → 调用方等待远超其声明超时；`AiCommand.execute()` 的 `syncGet`（L274）无超时参数，阻塞不可控。
- **现状**: 契约字段（"请求超时时间（毫秒）"）全模块 grep 无消费点（仅 L153 的 map 赋值入口），超时兜底完全依赖全局配置 + http client 对 `HttpRequest.timeout` 的具体实现语义。
- **风险**: 调用方超时意图静默失效；慢/挂起的 LLM 连接按全局包络而非请求包络占用调用线程与链路资源；悬挂类缺陷（C3-001/003）因无 per-request 超时而从"可恢复"恶化为"永久"。
- **建议**: `buildHttpRequest` 中 `requestTimeout != null` 时覆盖全局值；或在 `aggregateStreamToResponse`/`callAsync` 返回 future 上加 `orTimeout(requestTimeout)`（completeOnTimeout 不可用——需区分超时错误分类，建议 `orTimeout` + 上层 `LlmErrorClassifier` 已将 timeout 归 TRANSIENT）。
- **失效面枚举**: (a) 非流式 `callAsync` L147——返回链无 orTimeout（本条）；(b) 流式 `aggregateStreamToResponse` L393——同族；(c) `EmbeddingServiceImpl` L174/`embedAllAsync` L174 future——仅 `request.setTimeout`，无 future 级兜底（同族）；(d) `AiCommand.execute` L274 syncGet 无超时（同族消费侧）；(e) `AiCommand.chatOptions` L153 map 入口——制造契约有效的表象（随修复一并处理或 javadoc 标注 unsupported）。
- **信心水平**: 确定（消费点缺失可 grep 全模块证实）
- **误报排除**: 非"有界声明存在"豁免——存在的是**无效的** per-request 声明与全局兜底，调用方可见契约（ChatOptions javadoc + AiCommand map 键）与实际行为矛盾；非 C10/文档维度越界，属超时语义（orTimeout 缺失面）C3 要点。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现——grep 复证：requestTimeout 全 nop-ai 无消费点（仅 ChatOptions bean + AiCommand:153 map 赋值）；buildHttpRequest L272 仅用 CFG_AI_SERVICE_READ_TIMEOUT；全模块无 orTimeout/completeOnTimeout

### [C3-008] aggregateStreamToResponse 晚订阅竞态：bridge 先于聚合器订阅时 chunk 静默丢失（理论风险，未具体化现实触发链）

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/ChatServiceImpl.java:400-437`（L400 订阅聚合器）, `202-233`（L205 订阅 bridge 即触发 fetch）
- **证据片段**:
  ```java
  // callStream 内：L205 eventPublisher.subscribe(bridge) → onSubscribe → request(MAX)
  //   → AbstractServerEventSubscription.startRequest() → client.sendAsync(...) —— fetch 已发起
  // aggregateStreamToResponse 内：callStream 返回后才订阅聚合器
  callStream(request, cancelToken).subscribe(new Flow.Subscriber<ChatStreamChunk>() {
      public void onSubscribe(Flow.Subscription subscription) {
          subscription.request(Integer.MAX_VALUE);
      }
      public void onNext(ChatStreamChunk item) { aggregator.addChunk(item); }
  ```
- **严重程度**: P2（理论风险，未具体化现实触发链——按时序可写出交错，但窗口为 ns 级代码间隙 vs ms 级网络时延，未能指出可稳定复现的触发条件，按纪律降 P2 标注）
- **并发场景**: 调用方线程（执行 L233 `return publisher` → L400 `.subscribe(aggregator)`）× http 完成线程/reader 线程（投递首个事件）。
- **触发时序**: T1 L205 `eventPublisher.subscribe(bridge)` 已发起 HTTP；T2 若非 2xx 错误信号或首包事件在调用线程执行到 L400 之前由 http 线程送达 bridge：错误信号经 `closeExceptionally` 自愈（JDK SubmissionPublisher 对晚订阅者补发 onError/onComplete），但**数据 chunk** 经 `publisher.submit` 在零订阅者时静默丢弃；T3 聚合器最终 onComplete 产出缺段响应，无任何告警。
- **现状**: `callStream` 是热发布者（订阅前 fetch 已启动），订阅者注册与数据起始之间无 happens-before 保证；正确性依赖"网络时延 > 数行同步代码"的时序假设。
- **风险**: 低概率缺段响应（文本/工具调用参数开头丢失）——一旦发生极难归因；随 http client 实现变化（同步完成的 mock/内存 client、回调内联执行）窗口可能扩大。
- **建议**: 结构性修复为冷发布：`callStream` 返回的 publisher 在其 `subscribe` 回调中才执行 `fetchServerEventFlow` + 桥接（保证先有消费者再启动数据面）；或 `aggregateStreamToResponse` 先订阅 publisher 再返回（将订阅动作挪进 callStream 前的同步序列，保证 happens-before 于 fetch 启动）。
- **失效面枚举**: (a) `aggregateStreamToResponse` L400——主窗口（本条）；(b) `callStream` 公共 API 的外部直接订阅者（nop-ai-gateway `FailoverStreamFlow`/`ChatServiceFailoverAdapter`，跨单元登记）——同一热发布窗口；(c) 终止信号（onError/onComplete）经 JDK close 语义自愈——非丢失面，已排除。
- **信心水平**: 很可能（竞态存在性确定；现实触发概率极低）
- **误报排除**: 非纯理论"看到共享字段就报"——交叠序列可完整写出且后果（静默缺段）明确；降 P2 并标注系遵守口径纪律而非否认竞态。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现——热发布时序（callStream 内 L205 订阅 bridge 即启动 fetch、L233 才返回 publisher）逐行核验；ns 级窗口 vs ms 级网络时延的自评降档符合口径纪律（理论风险标注合规）

### [C3-009] callStream 下游派发执行器缺省 ForkJoinPool.commonPool：聚合、日志 I/O 与 AiCommand 延续链全部落在 JVM 共享池

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/ChatServiceImpl.java:202`（构造点）, `400-437`（聚合回调承载于该池）; `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/command/AiCommand.java:303-316, 349-371`（以聚合 future 为源的延续链）
- **证据片段**:
  ```java
  // ChatServiceImpl.callStream — 无参构造：JDK javadoc 明示缺省 executor 为 ForkJoinPool.commonPool()
  SubmissionPublisher<ChatStreamChunk> publisher = new SubmissionPublisher<>();
  ...
  // aggregateStreamToResponse 的 onComplete（经 publisher 派发，JDK SubmissionPublisher.java:1205-1213
  //   ConsumerTask 由 executor 执行）内执行同步文件 I/O：
  public void onComplete() {
      ChatResponse response = aggregator.toResponse();
      ...
      chatLogger.logResponse(request, response);   // JSON 序列化 + 3×正则全文替换 + writeText 落盘
      future.complete(response);                   // AiCommand 的 thenApply/thenCompose 链继承本线程
  }
  ```
- **严重程度**: P2
- **并发场景**: `ForkJoinPool.commonPool`（JVM 全局共享，默认 parallelism ≈ cores-1）同时承载：(1) bridge→聚合器的逐 chunk 派发与 `StreamAggregator` 累积；(2) 聚合终态回调 `chatLogger.logResponse`（同步文件 I/O）；(3) AiCommand 延续链——`promptTemplate.processChatResponse`（可执行模板声明的 postProcess/outputBuilder XPL 函数）、`ToolCallAccumulator.toToolCall` 的 JSON.parse、`chatCache.saveCachedResponse`（JSON 序列化 + 可选 AES 加密 + 写盘）。plain `thenApply/thenCompose` 无显式 executor，在完成线程（即本池）同步执行。
- **触发时序**: T1 并发 N 条流式调用（N 逼近 commonPool parallelism）；T2 各流终态回调在 commonPool worker 上执行 logResponse 文件写（毫秒~百毫秒级）+ 缓存 save（序列化/加密/落盘）；T3 commonPool worker 全部阻塞在 I/O → 同池的并行流、平台其他 commonPool 任务排队饥饿；LLM 流式完成延迟随并发与文件系统尾延迟放大（可量化：每响应至少一次同步落盘 × 共享池 worker 数上限）。
- **现状**: 模块无执行模型声明，无参 `SubmissionPublisher` 是 C3 要点 2（"未指定 executor 落在哪个池，与模块声明的执行模型是否一致"）的直接命中；阻塞 I/O 被默认安置到 CPU 尺寸的 JVM 共享池。
- **风险**: 与 C3-002（上游 reader 线程阻塞）叠加形成上下游双侧池饥饿；延续函数在无调用方上下文的线程执行（IContext ThreadLocal 不跨此边界，context 依赖组件在链中取值为 null）；commonPool 饥饿外溢至整个 JVM。
- **建议**: 构造时显式传入专用 executor（平台 `io.nop.commons.concurrent.executor.IThreadPoolExecutor`/`StandardThreadPoolExecutor` 或 `GlobalExecutors` 治理线程），或将重 I/O（logResponse/saveCachedResponse）移出回调线程（与 C3-003/CL-016 合并治理）。
- **失效面枚举**: (a) `ChatServiceImpl.java:202`——全模块唯一 `SubmissionPublisher` 构造点（grep 证实，无同类兄弟漏修），本条主面；(b) 聚合回调 L400-437 与 (c) `AiCommand` 延续链 L303-316/349-371——受害面而非独立构造点，修复 (a) 即覆盖；(d) `EmbeddingServiceImpl` 无 Flow 派发面（其回调在 http 客户端完成线程与 retry scheduler 线程，属 CL-016 同族性能面，不重复立条）。
- **信心水平**: 确定（缺省 executor 归属由 JDK 源码/javadoc 双重证实）
- **误报排除**: 非"原语使用误报"校准项——报的是执行器选择与所承载工作（阻塞 I/O + 任意脚本函数）的失配，而非 SubmissionPublisher/CompletableFuture 本身；也非"有界声明存在"豁免——executor 根本未配置。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现——回码核验成立：L202 无参 SubmissionPublisher（缺省 commonPool 为 JDK 契约）；跨单元指针：WI 2.1 CL-021 裸 JDK 载体绕过设施移交面（点名 ai-agent）+ 同单元 C4-001

### [C3-010] doExecute 槽位注册后、dispatch 前的同步保存失败孤儿化执行槽 + 接管锁 + 续期任务：会话被永久砖化

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java:771-792`（注册与保存序列）, `794-802`（无清理的外层 catch）; 对照 `1080-1089`（dispatchExecution 自身的对称清理）
- **证据片段**:
  ```java
  handle = registerExecutionSlot(sessionId, setup.ctx());   // L771: tryAcquire + putIfAbsent + 启动续期任务
  runningInjectionTargets.put(sessionId, setup.ctx());       // L774
  ...
  if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isEmpty()) {
      setup.session().markInputSeen(request.getIdempotencyKey());
      sessionStore.save(setup.session());                    // L791: 注释明言 "Save failure throws synchronously"
  }
  future = dispatchExecution(request, sessionId, setup, handle);
  } catch (NopAiAgentException e) {                          // L794: 仅分流 duplicate，其余原样 rethrow——
      ... throw e;                                           //      不清 runningExecutions / 锁 / 续期 / injectionTargets
  ```

- **严重程度**: P1
- **并发场景**: HTTP/消息调用线程（doExecute 同步段）× `SessionLockRenewal` 单线程调度器（`nop-ai-agent-lock-renew`）× 后续任意 execute/injectInput 调用线程。
- **触发时序**: T1 doExecute 在 L771 成功注册槽位（putIfAbsent 成功、`tryAcquire` 拿到接管锁、L923 `startLockRenewal` 已排定周期任务）→ T1 执行到 L791 `sessionStore.save` 抛出（FileBackedSessionStore 磁盘满 / DB store JDBC 故障，注释自认此路径会同步抛出）→ 异常落入 L794 外层 catch，非 duplicate 即 rethrow，**四个资源全部孤儿化**：`runningExecutions` 残留 handle（后续 execute 全部 "session already executing"）；接管锁行持续被续期任务无限续命（`renewOnceSafe` 只查 `runningExecutions.containsKey`——仍在，照常 tryRenew，TTL 自愈机制被续期本身击穿）；`runningInjectionTargets` 残留 ctx（injectInput 走 live 分支向永不消费的队列投递）；调度器任务永不取消。
- **现状**: dispatchExecution 的外层 catch（L1080-1089）对提交失败做了完全对称的三件套清理，但 L774-793 这一"注册成功→提交前"窗口没有等价清理路径——清理纪律在两段之间断裂。
- **风险**: 一次保存 I/O 故障即把该 sessionId 永久砖化直至进程重启（锁被无限续期、他实例无法接管、本实例无法重试）；续期任务是单线程调度器上的常驻任务，逐次累积；窗口内 injectInput 的 steering 消息静默丢失。
- **建议**: 把 L789-792 的 markInputSeen/save 移入 dispatchExecution 的清理覆盖范围（或将 doExecute 的外层 catch 补齐 `runningExecutions.remove(sessionId, handle)` + `releaseLockQuietly` + `cancelLockRenewalQuietly` + `runningInjectionTargets.remove` 四件套，镜像 L1080-1089）；保存失败时 idempotency key 尚未生效，语义上本来就是"输入被拒绝"，清理后重试即可。
- **失效面枚举**: (a) `doExecute` L789-793——本条主面（唯一在"槽位已注册、dispatch 未进入"区间执行同步 I/O 的路径，grep 证实）；(b) `resumeSession` L245-359——槽位注册与 supplyAsync 之间无保存调用，deliverWake/reset 类副作用均在含 catch 清理的首层 try 内——已覆盖；(c) `wakeSession` L481-533——同 (b)，`deliverWake` L508 在 try 内——已覆盖；(d) `restoreSession`→`acquireRestoreExecutionSlot` L880-910 + `dispatchRestoreExecution` L919-1007——注册与 dispatch 相邻无间隙——已覆盖。
- **信心水平**: 确定
- **误报排除**: 非"低频路径粗粒度同步"豁免项——这是错误路径资源泄漏（P1 校准项）；触发链每一步都有代码行定位，保存失败是 owner 注释自认可同步抛出的真实路径，非纯理论竞态。
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现——回码逐行核验成立：L771 注册→L791 save（注释自认可抛）→L794 catch 仅分流 duplicate 不清理；renewOnceSafe 以 runningExecutions.containsKey 为续命判据（L67-68）击穿 TTL 自愈确证。附注：dispatchExecution 自身 catch（L1080-1089）亦漏 runningInjectionTargets.remove（三件套非四件套），修复应两窗同批

### [C3-011] 工具机械超时（orTimeout）与中断/取消路径均不向底层工具执行传播取消：幽灵工具进程持续运行；cancelCall javadoc 声称的进程级 kill 通道实际不存在

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentToolDispatcher.java:683-701`（orTimeout 无取消传播）, `328-336`（cancelCall 无 tracker 调用）, `207-217`/`265-271`（中断→f.cancel(true)）；`engine/ReActAgentExecutor.java:1615-1627`（ABANDON——唯一 tracker kill 点）
- **证据片段**:
  ```java
  // buildToolFutures L690-700：超时后仅把 future 转成错误 ToolCallOutput，
  // 底层 toolManager.callTool 的执行链（CompletableFuture 源头）不受影响
  toolFuture = toolFuture.orTimeout(effectiveTimeoutMs, TimeUnit.MILLISECONDS)
          .exceptionally(ex -> { ... return new ToolCallOutput(timedCall, AiToolCallResult.errorResult(...)); });
  // cancelCall L328-336：javadoc 声称 "(when a progress tracker is wired) cancels the tool
  // process itself"，实现只有两行——future.cancel(true)（对 CF 链是 no-op）+ 记账：
  public boolean cancelCall(String callId) {
      CompletableFuture<ToolCallOutput> future = futuresByCallId.get(callId);
      if (future == null || future.isDone() || cancelledCallIds.contains(callId)) return false;
      future.cancel(true);
      cancelledCallIds.add(callId);
      return true;
  }
  ```
- **严重程度**: P1
- **并发场景**: ReAct 执行线程（等待相/提交相）× 工具执行线程（`toolManager.callTool` 的派发线程池，含 BashExecutor/Docker/Host sandbox 子进程）× 进度 tracker 取消通道（`IToolProgressTracker.cancelExecution`）。
- **触发时序**: T1 某工具调用挂死（bash 死循环、docker 容器无输出）；T2 ReAct 线程在 `buildToolFutures` L690 的 orTimeout 到期（默认 `toolTimeoutMs=300s` 或 toolTimeoutPolicy cap）→ future 转为超时错误结果 → 提交相把错误响应写给 LLM、循环继续；T3 底层工具执行**继续运行**：orTimeout 不取消上游 CF、`exceptionally` 不触碰执行链、无任何 `toolProgressTracker.cancelExecution(callId)`（全 engine 包 grep：该 kill 通道仅 ReActAgentExecutor L1622 的 heartbeat-ABANDON 裁定与 DefaultAgentEngine L692-697 的后台任务 stop 通道调用）。sandbox 类工具要等到**自身** `wallSeconds` 预算（DockerSandboxBackend.java:262-276 / NoOpSandboxBackend.java:113-134 自 kill）才终结，engine cap 若短于 wallSeconds 则窗口期为差值；非 sandbox 工具无内部时限，无限期运行。forced cancel 路径同构：`cancelSession(forced=true)` 中断 ReAct 线程 → `awaitCompletionSlice`/`awaitWakeup` 捕获 InterruptedException 后对 pending futures `f.cancel(true)`（L209-211/267-269，对未完成的 CF 链同样 no-op）→ 会话失败退出，但幽灵工具继续跑。
- **现状**: 超时/中断被正确"呈现给 LLM"（错误结果路径完备），但 C3 要点 3 的"超时后原任务是否仍在跑（幽灵任务）"答案为是；进程级 kill 通道存在却只接了一条裁定路径，且 cancelCall 的 javadoc 与实现不符。
- **风险**: 挂死工具泄漏执行线程 + OS 进程/容器/HTTP 连接；反复超时的长时运行会话逐次累积幽灵（F8 资源清理不对称）；幽灵工具（如写文件类）在会话认定其"已超时终止"之后仍可能改动工作区，违反会话的一致性假设；幽灵 bash 持有文件锁可阻塞后续轮次的同名工具。
- **建议**: 在 orTimeout 的 `exceptionally`（或超时分支前置）与中断/AR-15 取消路径统一调用 `toolProgressTracker.cancelExecution(callId)`（复用 ABANDON 已验证的通道），或为 `toolManager.callTool` 增加取消传播契约（上游 CF cancel → 执行链 interrupt）；修正 cancelCall javadoc 或把 tracker 调用下沉进 cancelCall。
- **失效面枚举**: (a) `buildToolFutures` L690 orTimeout——本条主面（LEAK）；(b) `awaitCompletionSlice` L209-211 中断取消环——LEAK；(c) `awaitWakeup` L267-269 中断取消环——LEAK；(d) `startToolPhase`/`addCallsToPhase`/`buildToolFutures` 的 AR-15 catch（L443-445/488-492/707-709）`f.cancel(true)`——LEAK（同 no-op 语义）；(e) `cancelCall`/`abandon`——future 级取消生效但无进程级 kill（javadoc 失实）；(f) heartbeat ABANDON L1622——**唯一正确面**（tracker cancel + abandon）；(g) 后台任务 stop 通道 `DefaultAgentEngine.cancelBackgroundViaTracker` L692-697——正确面；(h) `AgentCallDelegate` L93 orTimeout + L111-119 `cancelSession` 补偿——会话级补偿存在（工具级幽灵由 (a) 承接，不重复立条）。
- **信心水平**: 确定（javadoc 与实现的背离、tracker 通道的调用点分布均 grep 证实）
- **误报排除**: 非"future.cancel 原语使用"误报校准项——报的是取消语义在异步链上的断链（C3 要点 3 定向检查命中）且同模块存在正确参照面 (f)/(g) 证明通道可用；非"已声明边界"——javadoc 声称有 kill 通道恰说明设计意图未被实现。
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现——grep 复证：tracker.cancelExecution 全模块仅 DefaultAgentEngine:695 与 ReActAgentExecutor:1622 两调用点；cancelCall javadoc「cancels the tool process itself」与实现（仅 future.cancel）失实逐行确证

### [C3-012] LLM 调用超时后 f.cancel(true) 对 CompletableFuture 为 no-op：挂死的 chatService.call 作为幽灵任务继续占用 agent 执行器线程与上游连接

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/LlmCallCoordinator.java:785-808`（`callChatWithTimeout`）, `engine/SingleTurnExecutor.java:156-176`（同构 `callWithTimeout`）, `41-43`（缺省构造落 commonPool）
- **证据片段**:
  ```java
  CompletableFuture<ChatResponse> f = CompletableFuture.supplyAsync(
          () -> chatService.call(request, null), timeoutExecutor);   // timeoutExecutor = agent 执行器
  try {
      return f.get(llmTimeoutMs, TimeUnit.MILLISECONDS);
  ...
  } catch (TimeoutException e) {
      f.cancel(true);            // supplyAsync 返回的 CompletableFuture：mayInterruptIfRunning 被忽略——
      throw new CompletionException(e);   // chatService.call 在 agent 执行器线程上继续跑到自然结束
  }
  ```
- **严重程度**: P2
- **并发场景**: ReAct 执行线程（`f.get` 等待方）× agent 执行器 worker 线程（`chatService.call` 实际执行方，即 `nop-ai-agent-exec` cachedThreadPool）× 重试循环（超时→CompletionException→`handleTransportLevelOutcome` 重试/回退再发起下一次 attempt）。
- **触发时序**: T1 provider 挂死（TCP 半开/网关不响应）；T2 `f.get(llmTimeoutMs)` 超时 → `f.cancel(true)` 无中断效果 → worker 线程上的 HTTP 调用继续（连接 + 线程 + 响应缓冲被占）；T3 重试策略对超时分类为可重试 → 同一会话发起第二次 attempt（第二个 supplyAsync）→ 每个超时 attempt 泄漏一个幽灵调用；多会话 × 重试上限内反复超时 → agent 执行器线程数与悬挂连接无上界增长（cachedThreadPool 按需建线程，幽灵不饱和池但单调累积）。
- **现状**: `get(timeout)` 的可中断设计（Plan 271 finding 14-03）保证了等待方不被挂死——等待方语义正确；但超时方的资源回收完全依赖 `cancel(true)` 这一无效调用，且注释未声明幽灵语义。与 C3-011 同族（超时不取消底层），执行载体不同（线程 + HTTP 连接 vs OS 进程），修复点独立。
- **风险**: 长时运行部署中 provider 抖动期幽灵累积；每幽灵持有一个 agent 执行器线程与一条上游连接/请求缓冲；`SingleTurnExecutor` 缺省构造（L41-43）把幽灵落在 `ForkJoinPool.commonPool()`，外溢至 JVM 全局池（C3-009 同口径）。
- **建议**: `chatService.call` 无法中断时至少：超时后从 tracker/连接层取消（若 IChatService 暴露取消凭据）；或将 LLM 调用移交给支持中断的执行载体（虚拟线程 PerTaskExecutor + 可中断 HTTP 客户端）；javaddoc 明确声明超时后的幽灵窗口与上限（重试 cap 已有，补"每会话并发幽灵上限"更稳）。
- **失效面枚举**: (a) `LlmCallCoordinator.callChatWithTimeout` L806——主面；(b) `SingleTurnExecutor.callWithTimeout` L174——同构兄弟（F3 清扫命中，本条并案）；(c) `SingleTurnExecutor` 缺省构造 L42 的 commonPool——加重因子并入；(d) `AgentToolDispatcher` L690 orTimeout——进程级幽灵，已由 C3-011 立条，不重复；(e) `AgentCallDelegate` L93 orTimeout——有 cancelSession 补偿（C3-011 (h)），非本族裸面。
- **信心水平**: 确定（`CompletableFuture.cancel` 对 supplyAsync 链不中断底层任务为 JDK 契约）
- **误报排除**: 非"cancel 原语本身使用"误报——报的是超时通道声明了资源保护意图（构造 javadoc 明言"prevents ... indefinitely"）但回收动作无效；等待方正确性不否认幽灵方泄漏。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现——回码核验成立：callChatWithTimeout L792-808 与 SingleTurnExecutor.callWithTimeout L156-176 同构 f.get(timeout)→f.cancel(true)（CF 契约 no-op）；SingleTurnExecutor 缺省构造落 commonPool（L41-43）确证

### [C3-013] injectInput 终局边界竞态：live 分支在终局 drain 之后投递 → steering 消息静默丢失；staging 分支与终局保存并发 → 同一 AgentSession 双线程无同步写

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java:1006-1010`（remove 先于终局同步、remove 后无补偿 drain）, `617-623`（injectInput 分支判定）, `1045-1049`（终局保存）；`engine/ReActAgentExecutor.java:539-544`（终局残留 drain 位置）；`runtime/InMemoryActorRuntime.java:460-496`（actor 消费线程 enqueue+ack）
- **证据片段**:
  ```java
  // 引擎 worker 终局 finally 的顺序（DefaultAgentEngine）：
  runningExecutions.remove(sessionId, handle);        // L1006
  runningInjectionTargets.remove(sessionId);          // L1010 —— 注入目标自此不可达，但此后无 drain
  ...
  session.replaceMessages(ctx.getMessages());         // L1045 终局持久化（ctx 队列里残留的注入随之丢弃）
  sessionStore.save(session);                         // L1049
  // ReActAgentExecutor L542 终局 drain 在 executor 内部、future 完成前执行：
  for (ChatMessage residualMsg : ctx.drainSteering()) { ctx.addMessage(residualMsg); }
  ```
- **严重程度**: P2
- **并发场景**: 引擎 worker 线程（终局 finally：executor 内 drain → remove 注入目标 → replaceMessages → save）× injectInput 调用线程（get 存活目标 → enqueue）× actor 消费线程（mailbox poll → enqueue → ack）。
- **触发时序**: 丢失面 1（injectInput）：T1 worker 已过 ReActAgentExecutor L542 的终局 drain、正执行 L1010 remove 与 L1045-1049 终局持久化（含 sessionStore 磁盘 I/O，毫秒~十毫秒级窗口）；T2 injectInput 在 L617 `runningInjectionTargets.get(sessionId)` 仍取到 liveTarget → `enqueueSteering` 落入 ctx 队列 → 该队列随 ctx 丢弃，消息无任何持久化或告警地丢失。丢失面 2（actor）：T1 actor 消费线程在终局 drain 之后、`destroyActor`（DefaultAgentEngine L1022-1025，位于终局保存之前）之前 poll 到 mailbox 消息 → L465 enqueue + L490 **ack（标记已消费）** → 永不入史；该窗口横跨整个终局持久化 I/O（秒级可达），actor 轮询间隔 1s 使命中现实可见。撕裂面 3（staging）：T2 injectInput 在 L1010 之后取不到 liveTarget → 走 L621-622 `session.addPendingInjection` + `sessionStore.save(session)`，与 T1 的 `replaceMessages`（写同一 AgentSession 的 messages 列表）+ save 并发——`pendingInjections` 为普通 `ArrayList`、`messages` 亦非并发容器，双线程无同步读改写同一对象（F1 快照撕裂同族）。
- **现状**: 注释（L1007-1009）声称"终局同步前 drain 掉最后一次 LLM 调用期间到达的注入"，drain 确实存在但在 executor 内部先于 remove 执行——remove 与 drain 之间的窗口未被关闭；三个交错面共用同一修复点。
- **风险**: 用户/上游显式投递的 steering 输入在会话收尾边界静默丢失（F2"半个成功"）；actor 路径消息被 ack 后丢失使 mailbox 语义（at-least-once）失效；staging 并发写在 FileBacked store 下可产出撕裂的会话快照或序列化 CME。
- **建议**: 把 `runningInjectionTargets.remove` 提前到 executor 终局 drain 之前（或 drain 之后追加一次 remove+补 drain 的闭环：remove 后再 `drainSteering` 一次并入 replaceMessages）；actor 路径在 destroyActor 后对队列残留做回退（重新入 mailbox 或 staging）；staging 分支与终局保存之间以执行槽存在性判据（runningExecutions 而非注入目标表）互斥。
- **失效面枚举**: (a) doExecute 路径 live 分支窗口——主面 1；(b) actor 消费线程 enqueue 窗口——主面 2（同根，宽窗口）；(c) staging 分支并发保存——主面 3；(d) `runSteeringWakeupTurn` 的 snapshot→drain-commit 间隙——同一 ReAct 线程内串行，非交错面，已排除；(e) `bridgeBackgroundCompletion`→injectInput——复用 (a)/(c) 判定分支，无独立窗口。
- **信心水平**: 很可能（交错序列可完整写出且窗口含真实 I/O；概率受限于边界时序）
- **误报排除**: 非"看到共享字段就报"——三个交错面的 T1/T2/后果均已具体化到行级；降 P2 是因窗口窄、后果为单条消息丢失而非状态损坏，非否认竞态存在。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现——回码逐行核验成立：终局 finally 的 remove（L1009/L1010）先于 replaceMessages+save（L1045-1049）；executor 内终局 drain（ReActAgentExecutor L542-544）在 remove 之前——注释声称的 drain 语义与实际次序缺口确证；三交错面共用同一修复点成立

### [C3-014] resume/wake/restore 恢复执行不注册 runningInjectionTargets：恢复期间的 injectInput 全部走 staging 分支，与 worker 终局保存在同一 AgentSession 上双线程并发读写

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentSessionLifecycle.java:292-304`（resume 槽位注册，无 injectionTargets 注册）, `481-493`（wake 同构）, `617-618`（restore 同构）, `408-412`/`571-575`/`971-975`（三路径 worker 终局保存）；`engine/DefaultAgentEngine.java:614-623`（injectInput 以注入目标表为唯一"运行中"判据）
- **证据片段**:
  ```java
  // injectInput 的"运行中"判据（DefaultAgentEngine L614-619）：
  // "running" = an execution slot is registered ... getStatus() is NOT a reliable in-flight signal
  AgentExecutionContext liveTarget = runningInjectionTargets.get(sessionId);
  if (liveTarget != null) { liveTarget.enqueueSteering(message, semantics); }
  else { session.addPendingInjection(semantics.name(), ...); sessionStore.save(session); }
  // 但 runningInjectionTargets 只在 doExecute (L774) put——resume/wake/restore 从不注册；
  // 其 worker 终局却在同一 session 对象上：session.replaceMessages(ctx.getMessages()); sessionStore.save(session);
  ```
- **严重程度**: P2
- **并发场景**: 恢复执行 worker 线程（`resumeSession`/`wakeSession`/`restoreSession` 的 supplyAsync lambda）× injectInput 调用线程——同一 `AgentSession` 实例（`pendingInjections` 为普通 `ArrayList`，`messages` 同为非并发容器，`AgentSession.java:77`）。
- **触发时序**: T1 运维对 paused 会话调用 `resumeSession` → 槽位注册（runningExecutions putIfAbsent L299）但注入目标表无条目；T2 执行期间用户 `injectInput` → L617 取不到 liveTarget → 判定"未运行"→ L621 `session.addPendingInjection`（ArrayList.add）+ L622 `sessionStore.save(session)`（JSON 全量序列化）；T2' 同一时刻 T1 worker 到达终局 → L408 `session.replaceMessages`（对 messages 列表 clear/addAll）+ L412 save——两个线程同时对同一非线程安全容器结构读改写并各自触发全量序列化落盘。
- **现状**: `buildBaseExecutionContext` 注释（AgentSessionLifecycle L144-146）明言 staging 合并是"single-writer: caller's thread **before** the execution slot is registered"——恢复路径使该单写者契约失效：槽位已注册而 injectInput 无从得知。恢复执行期间的 steering 不会丢（下次执行合并），但并发写本身未受任何同步保护。
- **风险**: staging 分支的 `save` 序列化遍历 messages 时 worker 恰在 `replaceMessages` 中 clear/addAll → `ConcurrentModificationException` 或写出撕裂快照（F1）；injectInput 线程的 save 与 worker 终局 save 交错可后写覆盖前写（`addPendingInjection` 的修改被 worker save 的旧快照回卷——丢失窗口）。恢复窗口（人工审批后的 resume）恰是用户倾向补发输入的场景，触发概率不低于正常执行路径。
- **建议**: 三条恢复路径在槽位注册成功后同步 `runningInjectionTargets.put(sessionId, ctx)` 并在 worker 终局 finally 与 doExecute 对称 remove（同时顺带修复 C3-013 的丢失面 1 对恢复路径的覆盖）；或 injectInput 的判据改为 `runningExecutions.containsKey(sessionId)` + 从 handle 取 ctx。
- **失效面枚举**: (a) `resumeSession`——本条主面；(b) `wakeSession`——同构（L488 putIfAbsent 无目标注册）；(c) `restoreSession`/`acquireRestoreExecutionSlot` L892——同构；(d) `doExecute`——已注册目标（无此缺陷），作为对照面。三处恢复路径为同一 F3 修复批次，禁止只修 (a)。
- **信心水平**: 确定（注册缺失为 grep 可证事实；并发写在容器类型上为确定数据竞争）
- **误报排除**: 非理论竞态——交错与后果（CME/撕裂/丢更新）均可具体化；非"无状态单例并发调用"豁免——争用的是有状态 per-session 可变容器。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现——回码核验成立：resume 槽位注册（putIfAbsent）无 runningInjectionTargets.put 直核；跨维指针：同单元 C1 维度 C1-C 批 P1（runningInjectionTargets resume 裸 ArrayList）为同站点并发面，跨维合并归裁定阶段

### [C3-015] cancelSession 清除调用线程租户 ThreadLocal 而非保存-恢复：与 resumeSession/wakeSession 的 previousTenant 纪律不对称

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java:507-553`（`set(null)` + finally `clear()`）；对照 `AgentSessionLifecycle.java:267/313/343`（resume 的正确模式）、`467-468/502/518`（wake 同）、`security/ThreadLocalTenantResolver.java:48-63`
- **证据片段**:
  ```java
  // cancelSession（DefaultAgentEngine L511-552）：无条件覆盖 + 清除，不保存调用方原值
  ThreadLocalTenantResolver.set(null);
  try {
      ... // 槽位查找 / 发布事件 / checkpointManager.remove
      return CompletableFuture.completedFuture(null);
  } finally {
      ThreadLocalTenantResolver.clear();       // 调用方线程原有的租户上下文一并被清除
  }
  // 对照 resumeSession（AgentSessionLifecycle L267/343）：
  String previousTenant = ThreadLocalTenantResolver.current();
  ... } finally { ThreadLocalTenantResolver.set(previousTenant); }   // 保存-恢复
  ```
- **严重程度**: P2
- **并发场景**: 携带租户上下文的请求线程（如经网关鉴权后设置 ThreadLocalTenantResolver 的管理接口调用 cancelSession）× 池化线程复用。
- **触发时序**: T1 请求线程上下文 tenant=A，调用 `cancelSession`；T2 L512 `set(null)` 覆盖 → finally L551 `clear()` remove；T3 同一线程返回后继续处理该请求的后续租户敏感操作（如再查/再写会话、审批落库）——租户上下文已丢失，DB store / denial ledger 的 tenant WHERE 退化为 null（全租户可见）。`forkSession` L558-592 同样以 `clear()` 收尾（先 set 新值再 clear，调用方原值同被清除）。
- **现状**: 同类路径中恢复路径（resume/wake）已采用保存-恢复纪律并在注释中明言"restoring the caller's context afterward so the synchronous phase never leaks tenant state"——cancelSession/forkSession 未跟进，清理纪律族内不一致（F3）。
- **风险**: 池化线程的租户上下文被单次 cancel 调用抹除后，同请求后续操作的越权可见面（方法自身注释亦承认 null=全数据可见）；跨租户数据破坏的形态与 resumeSession 注释自述的历史缺陷同族。
- **建议**: 三处统一为 `String previous = current(); try { ... } finally { set(previous); }`（ThreadLocalTenantResolver.set(null) 已实现 remove 语义，恢复 null 等价清除）。
- **失效面枚举**: (a) `cancelSession` L512/551——本条主面；(b) `forkSession` L558/592——同族（先 set 请求租户，同样不恢复原值）；(c) `resumeSession`/`wakeSession`/`dispatchRestoreExecution`——(a)(b) 之外的路径分别采用保存-恢复 / 显式 null 语义（restore 注释声明恢复路径即 null 语义），已排除。
- **信心水平**: 确定
- **误报排除**: 非"ThreadLocal 未清理"误报校准项的反面误用——问题恰是清理过度（破坏调用方上下文）而非泄漏；有 resume 路径的保存-恢复注释作为设计意图内证。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现——回码核验成立：cancelSession L512 set(null)+L551 finally clear()、forkSession 同型；resume 的 previousTenant 保存-恢复（AgentSessionLifecycle L268/L343）为家族内证确证

### [C3-016] 会话 mailbox 与消息代理订阅只增不删：每会话注册的 IMailbox + IMessageSubscription 在执行终局、会话终结与引擎 close 均无清理路径

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentSessionSupport.java:31-32/52-75`（两 map 仅 put/get，无 remove）, `66-68`（订阅注册）；调用点 `engine/DefaultAgentEngine.java:883`、`engine/AgentSessionLifecycle.java:284/478/870`
- **证据片段**:
  ```java
  // AgentSessionSupport.ensureSessionMailbox（四条执行入口每次都会调用）：
  sessionMailboxes.computeIfAbsent(sessionId, sid -> {
      IMailbox mailbox = mailboxFactory.apply(sid);        // 惰性建 mailbox
      ...
      IMessageSubscription subscription = callDelegate.getMessenger().registerHandler(inboxTopic, handler);
      if (subscription != null) { sessionMailboxSubscriptions.put(sid, subscription); }
      return mailbox;
  });
  // 全模块 grep：sessionMailboxes / sessionMailboxSubscriptions 无任何 remove/cancel 调用点
  ```
- **严重程度**: P1
- **并发场景**: 引擎 worker 线程（ensureSessionMailbox 注册）× 消息代理派发线程（订阅 handler 长期存活、持续向死会话 mailbox 投递）× 引擎生命周期（close）。
- **触发时序**: T1 每个会话首次执行（doExecute/resume/wake/restore 四入口，L883/L284/L478/L870）注册一个 mailbox + 一条 inbox topic 订阅；T2 执行终局清理（worker finally：checkpoint remove / actor destroy / 锁释放 / `runningExecutions` remove）与 `close()`（DefaultAgentEngine L1119-1153：仅关闭三个自建池）均不触及这两张表与订阅；T3 长时运行下会话数单调增长 → mailbox 对象 + 代理订阅 handler 按会话数无上界累积；消息代理对已终结会话的 inbox topic 继续派发（消息写入无人消费的 mailbox，取决于实现可能持久化堆积）。
- **现状**: 注册（register/put）与注销（cancel/remove）在引擎内完全不对称（F8），账本只增不删（F4 的内存版）；与同文件中被完整清理的 `runningExecutions`/`runningInjectionTargets` 形成对照，属遗漏而非设计取舍（无任何注释声明保留意图）。
- **风险**: 长时运行部署必然增长（每执行过的新会话一条订阅 + 一个 mailbox 及其内部队列/溢出缓冲）；订阅 handler 强引用会话级对象延迟其回收；代理侧死订阅持续消耗派发路径。
- **建议**: worker 终局（isTerminalStatus 分支）与 `close()` 中对非终局保留语义外的会话补 `subscription.cancel() + 两 map remove`；若 mailbox 需跨执行保留（离线注入语义），至少订阅应在终局取消、mailbox 保留到会话删除钩子。
- **失效面枚举**: (a) `sessionMailboxes`——主面；(b) `sessionMailboxSubscriptions`——主面（同一修复点）；(c) `AgentCallDelegate.callAgentSubscription`——引擎级单订阅，`registerCallAgentHandler` 有 cancel 旧订阅逻辑，非本族裸面；(d) `InMemoryActorRuntime.executors`——create/destroy 对称（destroyActorInternal remove），已排除。
- **信心水平**: 确定
- **误报排除**: 非"有界声明存在"豁免——两 map 无容量/淘汰配置；非低频路径——四条执行入口每次命中；累积率与会话创建率 1:1，属"长时运行必然增长"校准项。
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现——grep 复证：sessionMailboxes/sessionMailboxSubscriptions 全引擎无 remove/cancel 调用点；四执行入口每次注册；F4/F8 判定成立

### [C3-017] DefaultWaitCoordinator：sessions 表项只置空字段从不移除；TIMEOUT 条件的定时 deliverWake 任务不随条件消费取消——陈旧 deadline 任务可提前满足同会话的后续等待条件

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/DefaultWaitCoordinator.java:47/83-96`（requestWait put + schedule 无句柄保存）, `99-113`（checkWait 仅置 null 不 remove）, `116-123`（deliverWake computeIfPresent）
- **证据片段**:
  ```java
  public void requestWait(String sessionId, WaitCondition condition) {
      WaitState state = new WaitState();
      state.condition = condition;
      state.satisfied = false;
      sessions.put(sessionId, state);                      // 只 put，全类无 sessions.remove
      if (condition.getType() == WaitCondition.Type.TIMEOUT && scheduler != null) {
          long delay = Math.max(0, condition.getDeadlineMs() - clock.getAsLong());
          scheduler.schedule(() -> { deliverWake(sessionId, null); return null; },
                  delay, TimeUnit.MILLISECONDS);           // 返回的 Future 被丢弃——无法取消（F8）
      }
  }
  // checkWait 消费分支：state.condition = null; state.satisfied = false; —— 表项本身保留
  ```
- **严重程度**: P2
- **并发场景**: 调度器线程（TIMEOUT 到期 deliverWake）× ReAct 执行线程（requestWait/checkWait）× wakeSession 调用线程（deliverWake）。
- **触发时序**: T1 会话在 t0 注册 TIMEOUT 条件（deadline D1）；T2 外部 deliverWake 提前唤醒 → checkWait 消费（condition=null），但 D1 的定时任务仍在调度队列；T3 同一会话执行后续逻辑，在 D1 之前注册**新**等待条件 W2（如 EVENT 型，语义上只能由显式事件满足）；T4 D1 到期陈旧任务触发 `deliverWake(sessionId)` → W2 被标记 satisfied → 下次 checkWait 直接 PROCEED——事件从未到达，等待条件被无关的历史 deadline 击穿。
- **现状**: 两个独立缺陷共用一个类：(1) `sessions` 表项永不移除（仅字段置空），每会话永久残留一个 WaitState；(2) 定时任务生命周期与条件生命周期未绑定（schedule 返回值丢弃），且 deliverWake 对"无活跃条件"的表项照常置 satisfied。
- **风险**: (2) 破坏 WAIT_FOR 原语的语义正确性（错误恢复/提前放行，治理语义被绕过）；(1) 为每会话常驻小对象的无界累积（服务端长时运行按会话数增长）。
- **建议**: requestWait 保存 `ScheduledFuture` 于 WaitState；checkWait 消费与 deliverWake 满足时 `cancel(false)` 旧任务；deliverWake 校验 `condition != null` 才置 satisfied；checkWait 消费后 `sessions.remove(sessionId)`（下次 requestWait 重建）。
- **失效面枚举**: (a) TIMEOUT 型条件定时任务——主面（EVENT/USER_INPUT 型无 schedule，不受 (2) 影响，但仍受陈旧任务误唤醒）；(b) `sessions` 表项累积——全部条件类型共有；(c) `NoOpWaitCoordinator`——shipped 默认恒 NONE，不受影响（已排除）。
- **信心水平**: 确定（时序可完整写出；T3 注册第二个条件在同会话内为正常业务序列）
- **误报排除**: 非原语使用误报——缺陷在条件生命周期与定时任务生命周期未绑定的复合语义，非 schedule 调用本身。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现——回码逐行核验成立：requestWait put + schedule 返回值丢弃、checkWait 仅置空 condition 不 remove、deliverWake computeIfPresent 无条件置 satisfied——陈旧 deadline 提前满足后续 EVENT 条件的时序闭合；累积子面与同单元 C7-017（P2）同站点，指针登记

### [C3-018] 恢复路径 fire-and-forget future 无任何消费点：restore/cancel 执行在 dispatch 后异步失败时静默无观察，扫描结果已先行报告成功

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/runtime/recovery/DefaultOrphanRecoveryHandler.java:166-184`（handleResume 丢弃 future）, `runtime/recovery/DefaultSessionTimeoutHandler.java:220-237`（localCancelled 同构）；异步失败落点 `engine/AgentSessionLifecycle.java:928-999`（supplyAsync lambda 无 exceptionally/handle）
- **证据片段**:
  ```java
  // DefaultOrphanRecoveryHandler.handleResume：
  try {
      // Fire-and-forget (design 裁定 1): do NOT .join() —— 扫描循环不阻塞（设计正确）
      engine.restoreSession(sessionId, RECOVERY_APPROVER, RECOVERY_REASON);
      return new RecoveryOutcome(sessionId, RecoveryMode.RESUME, true,
              "RESUME: restoreSession triggered (fire-and-forget)");   // 此刻即报告 succeeded=true
  } catch (RuntimeException e) { ... }   // 只捕获同步异常——dispatch 之后的异步失败无人观察
  // dispatchRestoreExecution 的 supplyAsync lambda：worker 内异常经 finally 清理后
  // 使 future 异常完成——无 .exceptionally/.whenComplete，无日志落点，无状态回写
  ```
- **严重程度**: P2
- **并发场景**: 恢复扫描线程（`ScheduledRecoveryManager.scanOnce` 调用 handler）× agent 执行器 worker 线程（restore/cancel 的实际执行体）。
- **触发时序**: T1 扫描检测到孤儿会话 → handleResume 调用 `restoreSession` → 同步段成功（tryAcquire/putIfAbsent/supplyAsync 提交）→ outcome 报告 `succeeded=true`；T2 worker 执行中终局保存 `sessionStore.save` 抛出（磁盘/JDBC 故障）→ worker finally 释放锁 → future 异常完成——**无消费者**：异常被丢弃，会话停留在非终局状态且无任何 WARN/ERROR/事件；T3 下一轮扫描将其再次判为孤儿 → 无限静默重试循环（每 scanIntervalSec 一次，每次都"成功"）。
- **现状**: C3 要点 2 定向检查（"无 exceptionally/handle 的链，异常去哪了？返回的 future 被谁消费？"）命中——同步失败路径做到了 non-silent（catch + failed outcome），异步失败路径完全静默，两者语义不对称。
- **风险**: 恢复风暴掩盖：故障存储下每分钟一次的无效恢复循环无任何可观测信号；"至少一次"兜底依赖扫描重探测，但每次失败的证据丢失，运维无法区分"恢复成功"与"恢复永久失败"；超时 LOCAL_CANCELLED 的 fire-and-forget 同理（取消未真正生效时无告警）。
- **建议**: handler 内对返回的 future 附加 `exceptionally(ex -> LOG.warn(...)+回写失败 outcome/事件)`（不 join、不阻塞扫描循环，保持 fire-and-forget 的不阻塞语义）；或复用 `restorePendingSessions` 已有的失败聚合路径统一上报。
- **失效面枚举**: (a) `DefaultOrphanRecoveryHandler.handleResume`——主面；(b) `DefaultSessionTimeoutHandler.localCancelled`——同构（cancelSession 的 future 几乎恒同步完成，实际窗口小，并案登记）；(c) `restorePendingSessions`（AgentSessionLifecycle L1080-1091）——`join()` 消费 + Throwable 记录，已正确，作为对照面；(d) `bridgeBackgroundCompletion`→injectInput 同步调用——异常由 DefaultBackgroundTaskRegistry 的 NOTIFICATION_FAILED 状态承接（tool 包，跨单元），非本面。
- **信心水平**: 确定
- **误报排除**: 非"吞异常"泛化——方法自身注释以 non-silent 为设计目标且同步面已做到，缺陷精确位于异步完成路径的观察点缺失；不建议改为 join（会破坏不阻塞扫描的裁定），修复建议保留原并发语义。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现——回码核验成立：handleResume 仅 catch RuntimeException（同步段）、报告 succeeded=true 后异步失败无消费者；restorePendingSessions join()+Throwable 记录对照面确证

### [C3-019] toolTimeoutMs<=0 逃生舱下 commitToolResults 无逐 future 隔离：批内首个失败工具的 join 异常中止整批提交，剩余已完成结果丢失、tool_call 配对断裂

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentToolDispatcher.java:686-702`（exceptionally 包装仅在 effectiveTimeoutMs>0 时挂接）, `740-742`（commit 循环裸 `f.join()`）
- **证据片段**:
  ```java
  CompletableFuture<ToolCallOutput> toolFuture = toolManager.callTool(...).thenApply(...);
  long effectiveTimeoutMs = resolveToolTimeoutMs(chatToolCall.getName());
  if (effectiveTimeoutMs > 0) {
      toolFuture = toolFuture.orTimeout(...)
              .exceptionally(ex -> ... error ToolCallOutput ...);   // 超时>0：失败被转为错误结果
  }                                                                 // <=0（逃生舱）：无任何包装
  ...
  // commitToolResults：
  for (CompletableFuture<ToolCallOutput> f : futuresArray) {
      ToolCallOutput output = f.join();     // 首个异常完成的 future 在此抛 CompletionException
  ```

- **严重程度**: P3
- **并发场景**: 单 ReAct 线程内的批提交循环 × 工具执行线程（异常完成方）——非多线程竞态，属异步异常传播路径的完整性缺陷（C3 要点 2）。
- **触发时序**: T1 部署配置 `toolTimeoutPolicy`/`toolTimeoutMs` <=0（构造注释明言 "backward-compatible escape hatch"）；T2 同批 N 个工具调用中第 k 个执行抛错（callTool 实现异常）→ future 异常完成；T3 commit 循环 `f.join()` 抛 CompletionException → 第 k+1..N 个已完成 future 的结果永不提交 → ctx 中第 k..N 个 tool_call 无 tool_response 配对 → 下一次 LLM 请求携带 open tool_calls（配对不变量破坏，restore 路径将其判为 orphan 告警）。
- **现状**: 默认路径（timeout>0）的 exceptionally 同时兜住 TimeoutException 与底层执行异常（L691-700 的 else 分支文案"tool execution failed"证实意图），逃生舱路径是唯一裸面。
- **风险**: 仅在显式禁用超时的部署 reachable，故 P3；一旦触发为"半个成功"（F2）：同批后续工具的副作用已发生而结果不可见。
- **建议**: `f.join()` 处包 `handle((out, ex) -> ex == null ? out : ToolCallOutput 错误包装)`，与 exceptionally 分支共用同一错误结果构造，两条路径语义归一。
- **失效面枚举**: (a) `commitToolResults` L741 join——唯一 commit 面（`awaitCompletionSlice` 的 ExecutionException 分支 L214-217 会先把它转为 NopAiAgentException 抛出，但 executeAllowedCalls facade 的 while 循环在全部完成前不会进入 commit；异常完成使 allOf 立即抛——两处均无逐 future 隔离，同一修复批）；(b) timeout>0 默认路径——exceptionally 兜住，已排除。
- **信心水平**: 确定
- **误报排除**: 非"错误处理维度越界"——报的是异步链异常传播导致的状态不一致（F2/要点 2），非错误码格式。
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜新发现——回码核验成立：exceptionally 仅挂于 effectiveTimeoutMs>0 分支（L690-701）、commit 循环裸 f.join()（L740-742）；逃生舱限定面（toolTimeoutPolicy 全空或值≤0）核验，P3 正确

### [C3-020] 周期任务仅捕获 RuntimeException：Error 逃逸静默杀死恢复扫描守护 / 单会话锁续期任务——续期死亡使租约无告警过期，打开跨实例双执行窗口

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/runtime/recovery/ScheduledRecoveryManager.java:383-397`（scanOnceSafe 仅 catch RuntimeException）, `engine/SessionLockRenewal.java:65-78`（renewOnceSafe 同构）
- **证据片段**:
  ```java
  // ScheduledRecoveryManager.scanOnceSafe：
  private void scanOnceSafe() {
      try { RecoveryScanResult result = scanOnce(); ... }
      catch (RuntimeException e) { LOG.warn(...); }   // Error（OOM/NoClassDefFoundError 等）逃逸——
  }                                                    // scheduleWithFixedDelay 按契约抑制后续全部执行
  // SessionLockRenewal.renewOnceSafe（每会话一个周期任务）：
  try { boolean renewed = config.getSessionTakeoverLock().tryRenew(...);
        if (!renewed) { handleLeaseLost(handle, sessionId, ownerId); } }
  catch (RuntimeException e) { LOG.warn(... "will retry next interval" ...); }
  ```
- **严重程度**: P3
- **并发场景**: 恢复扫描守护线程（`nop-ai-agent-lock-renew` 单线程调度器同时承载全部会话续期任务）× 执行 worker 线程 × 其他 JVM 实例。
- **触发时序**: 面 1：T1 scanOnce 内（或注入的 recovery handler 深处）抛出 Error（如批量结果构建 OOM）→ 逃逸 scanOnceSafe → 调度器按 `ScheduledExecutorService` 契约抑制该任务后续执行 → 恢复守护静默死亡（无 stop 日志、无健康探针）。面 2（后果更重）：T1 某会话的 `renewOnceSafe` 抛 Error → 该会话续期任务死亡；T2 租约到期不再续 → `handleLeaseLost` 永远不会被调用（它只在 tryRenew 返回 false 时触发——任务死了连 false 都没有）；T3 其他实例在 TTL 过后 `tryAcquire` 抢占并开始执行同一会话，而本实例的 ReAct 循环对租约丢失**零感知**继续执行 → 正是该机制要防止的双执行（状态双写/文件互踩）。
- **现状**: 设计对"续期失败"（false 返回 / SQLException）有完整处置链（leaseLost→cancel→interrupt），但对"任务本身死亡"无兜底；`scheduleWithFixedDelay` 的 Throwable 抑制契约使 catch (RuntimeException) 成为唯一的守护，覆盖不全。
- **风险**: 触发前提是 Error 逃逸（OOM 边缘、类加载失败），概率低，故 P3；面 2 一旦发生是 P0 级后果（跨实例双执行）且完全无告警——防御缺失与后果等级不对称。
- **建议**: 两处 catch 改为 `catch (Throwable t)`（Error 记 ERROR 日志 + 可选健康计数后 rethrow Error 或吞并降级——续期场景建议吞并并强制 `handleLeaseLost`（任务已不可信，按租约可能丢失处置最安全）；恢复守护场景至少 ERROR 日志暴露守护死亡；中期可接入平台 `CycleSynchronizer`/调度治理的健康探针。
- **失效面枚举**: (a) `ScheduledRecoveryManager.scanOnceSafe`——守护死亡面；(b) `SessionLockRenewal.renewOnceSafe`——续期死亡→双执行窗口面（后果更重）；(c) `team/scheduler/TeamTaskSchedulerDaemon` 的同构 catch——跨包（team）兄弟，移交对应批次核对；(d) `message/DBMessageService` poller——跨包（message）兄弟，同上移交。
- **信心水平**: 很可能（逃逸路径与调度器抑制契约确定；Error 触发前提使现实概率低）
- **误报排除**: 非"低频路径粗粒度同步"豁免——缺陷是守护覆盖面不全而非同步使用；非 F9 泛化——精确到"Error 绕过 catch 导致周期任务永久抑制"这一调度器契约行为。
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜新发现——回码核验成立：scanOnceSafe L393 仅 catch RuntimeException、renewOnceSafe L74 同构且 handleLeaseLost 仅挂于 tryRenew 返回 false——任务死亡即租约静默过期。跨单元同案指针：WI 2.1 nop-kernel-concurrency-C3-001（周期任务设施统一绝缘层 29 站点族，P1）——本条两站点应并入该平台绝缘层修复批次统一治理

### [C3-021] DBMessageService 单线程 poller 串行内联执行全部 topic 消费回调：call-agent handler 阻塞派发线程期间全模块消息冻结、嵌套 call-agent 链必然超时、stale-claim 清扫与投递同线程互饿

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/message/DBMessageService.java:208-218`（单线程 poller 同时承载投递与清扫两个 fixedDelay 任务）, `331-365`（pollTopic 在 poller 线程内联调用 `consumer.onMessage`，无超时守卫）
- **证据片段**:
  ```java
  poller = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "db-message-poller-" + consumerId));
  poller.scheduleWithFixedDelay(this::pollAllTopics, ...);          // 投递
  poller.scheduleWithFixedDelay(this::sweepStaleClaimedMessagesSafely, ...); // 清扫——同一线程
  ...
  Object result = consumer.onMessage(topic, message, context);      // L358：内联、无超时
  ```
- **严重程度**: P1
- **并发场景**: 单 poller 线程（全部 topic 的投递 + stale-claim 清扫）× call-agent handler 执行线程（engine `handleCallAgentRequest` 在派发线程上 `orTimeout().join()` 最长 callAgentTimeoutMs=120s，见 CL-022）× 各会话 ReAct worker（等待 messenger 回包）。
- **触发时序**: T1 会话 A 的 call-agent 工具经 messenger 发 REQUEST（DB INSERT 后返回 future）；T2 poller 认领该消息并在 poller 线程内联调用 handler → `engine.execute(...).join()` 阻塞最长 120s；T3 期间子代理（运行于引擎 executor）发起嵌套 call-agent：REQUEST 已 INSERT，但唯一 poller 线程仍阻塞在 T2 → 子请求永不被投递 → 子代理工具侧 orTimeout(默认 60s) 必然超时——**嵌套委派在 DB transport 装配下深度 ≥2 即确定性失败**；T4 同时全模块其他 topic（reply/inbox）投递冻结、清扫任务在单线程队列中排队——CLAIMED 消息堆积，at-least-once 兜底被同一线程阻塞所饿死。
- **现状**: C3 要点 1 异步入口盘点的投递面：派发并行度=1 且无消费超时；CL-022 已登记"死锁安全性完全依赖 messenger 派发并行度"的抽象依赖，本条在 message 包内证实 DBMessageService 正是该依赖的最坏实现（单线程串行），抽象风险坐实为装配即可触发的缺陷。
- **风险**: 多代理协作场景（call-agent/send-message/团队 inbox 全走同一 DBMessageService）下一级委派即冻结全局消息面最长 120s/次；嵌套委派功能不可用；长时间 handler 卡死（子代理 hang + join 上限）期间消息表只进不出。
- **建议**: 投递与清扫分离到两个调度器（清扫不被投递阻塞）；每 topic（或全局）有界消费线程池 + `QueueOverflowPolicy` 平台设施（`IBlockingSource/IBlockingConsumer`）；或 handler 契约改为异步载荷（CL-022 建议方向），禁止在派发线程 join 引擎 future。
- **失效面枚举**: (a) DBMessageService pollTopic 内联投递——主面；(b) sweepStaleClaimedMessagesSafely 同线程排队——同一修复点（互饿面）；(c) LocalAgentMessenger（内存 transport）——派发模型属平台 LocalMessageService（跨模块，不重审），本条不覆盖；(d) NoOpAgentMessenger——无投递，排除；(e) engine `handleCallAgentRequest` 的 join 本体——CL-022 已登，本条为其登记的具体化实现面。
- **信心水平**: 确定
- **误报排除**: 非"平台基础设施不重审"——DBMessageService 是 nop-ai-agent 自建 transport（非 nop-kernel）；非低频路径——call-agent 是 LLM 工具主路径；非"有界声明"——单线程为构造硬编码，非配置声明。
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现——回码核验成立：单线程 poller 双 fixedDelay 任务（L213-217）、pollTopic 内联 consumer.onMessage 无超时（L358）。装配条件面复核：引擎缺省 messenger 为 NoOp（DefaultAgentEngine:158），DBMessageService 为显式装配面——不适用零调用方降档（生产多实例 DB 装配正是该类的交付语义，WI 2.5 C7-012 条件面 P1 先例）；跨维指针：同单元 C4 CL-040（单线程互饿）、C7-025（毒消息 P1）；CL-022 抽象依赖由本条坐实

### [C3-022] TeamExecuteFlowExecutor 每次工具调用新建 orchestrator 且从不调用 close()：含 SPAWN 目标的 flow 惰性创建 max(2,NCPU) 固定线程池，随调用次数无上界累积

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/tool/TeamExecuteFlowExecutor.java:234-235`（per-invocation `new TeamTaskFlowOrchestrator`，无 close 调用）, `team/flow/TeamTaskFlowOrchestrator.java:468-478`（首次 SPAWN 节点惰性 `newFixedThreadPool(max(2,NCPU))`）, `448-458`（close() 是唯一释放点）
- **证据片段**:
  ```java
  // TeamExecuteFlowExecutor.doExecuteAsync —— 每次工具调用：
  TeamTaskFlowOrchestrator orchestrator = new TeamTaskFlowOrchestrator(
          engine, taskStore, teamManager, null, memberSpawner, null, memberExecTimeoutMs);
  ... flowFuture.thenApply(result -> mapResult(call.getId(), result));   // 无 finally close()
  // TeamTaskFlowOrchestrator.resolveSpawnExecutor：
  ownedSpawnExecutor = Executors.newFixedThreadPool(Math.max(2, NCPU), factory); // core 常驻
  ```
- **严重程度**: P1
- **并发场景**: LLM 工具执行线程（team-execute-flow 工具，可并发多路）× spawn worker 池线程（fixed pool core 线程无超时回收）。全模块 grep 证实 `orchestrator.close()` 无任何调用点（daemon 路径走 TaskDispatchCoordinator 自有池并由 stop() 关闭，与此无关）。
- **触发时序**: T1 每次 team-execute-flow 调用新建 orchestrator；T2 图构建期 `buildNodeStepForPlan` 对含 SPAWN 目标的 plan 调用 `resolveSpawnExecutor()`（TeamTaskFlowOrchestrator.java:930/947/957——即使 spawner 是 NoOp、节点随后诚实失败，池已在**构建期**创建）；T3 spawn 步骤 supplyAsync 提交使 fixed pool 创建 core 线程（无 allowCoreThreadTimeOut，JVM 存活期常驻）；T4 调用结束 orchestrator 及其池被遗弃（无引用、永不 shutdown）→ 每次含 SPAWN 目标的 flow 调用泄漏最多 max(2,NCPU) 条永久线程，线程数随调用次数线性无界增长。
- **现状**: javadoc 明言 "The orchestrator is cheap to build"（TeamExecuteFlowExecutor.java:67）——per-call 构建本身是裁定（DD#3），但 ownedSpawnExecutor 的所有权语义（close() 释放）与 per-call 生命周期组合被遗漏（F8 注册/释放不对称：创建在构建期自动发生，释放入口存在但无人可达）。
- **风险**: 长时运行服务上 team 编排为常用工具路径，线程数按 flow 调用次数线性增长直至线程耗尽/内存压力；与"长时运行必然触发的无界增长"校准直接命中。
- **建议**: doExecuteAsync 中 try/finally 调 `orchestrator.close()`；或将 spawn 池提升为 executor 级单例共享池（与 daemon 路径共用一个受管 `IThreadPoolExecutor`），orchestrator 改为只引用不拥有。
- **失效面枚举**: (a) TeamExecuteFlowExecutor per-call orchestrator——主面；(b) TeamTaskSchedulerDaemon/TaskDispatchCoordinator——单例、stop() 有 `shutdownOwnSpawnExecutor()`（TaskDispatchCoordinator.java:102-107），已对称，排除；(c) DefaultBackgroundTaskRegistry.backgroundExecutor——引擎装配注入、所有权在引擎，非本族裸面（其 tasks 表泄漏另见 C3-023）。
- **信心水平**: 确定
- **误报排除**: 非"daemon 线程不阻碍 JVM 退出即可豁免"——泄漏主体是长时运行服务内的常驻线程数增长，非 JVM 退出问题；非低频路径——团队 flow 编排是该模块多代理协作的核心工具。
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现——回码核验成立：per-invocation new TeamTaskFlowOrchestrator（L234-235）、全文件无 close 调用、resolveSpawnExecutor 构建期惰性建池（L468-478，调用点 L930/947/957 直核）；同站点跨维双登记：C4-010（P1）——跨维合并归裁定阶段

### [C3-023] DefaultBackgroundTaskRegistry.tasks 只增不删：终局 Entry（含 8KB output tail）永久滞留 process-wide 注册表，无任何清理/TTL 路径

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/tool/DefaultBackgroundTaskRegistry.java:59`（`ConcurrentHashMap<String, Entry> tasks`）, `98-102`（submit 只 put）, `134-170`（complete 只改状态不 remove），全类无任何 `tasks.remove` 调用
- **证据片段**:
  ```java
  public String submit(String toolName, String sessionId, String callId, long timeoutMs) {
      String taskId = "bg-" + idCounter.incrementAndGet();
      tasks.put(taskId, new Entry(taskId, toolName, sessionId, callId, timeoutMs));  // 唯一入口
      return taskId;
  }
  // complete()/stop()/snapshot()/readOutput()：仅状态迁移与读取——全类 grep 无 tasks.remove(x)
  // Entry 持有 outputTail（≤8KB 捕获尾）+ totalBytes，终局后仍强可达
  ```
- **严重程度**: P1
- **并发场景**: 引擎 worker/后台 fork 线程（submit/run/complete）× LLM 工具线程（task-output/task-stop 读）——注册表为 Builder 级单例、process-wide 生命周期（javadoc L19-21），跨会话跨请求累积。
- **触发时序**: T1 每次 runInBackground 工具调用 submit 一条 Entry；T2 任务终局（COMPLETED/FAILED/TIMEOUT）后 Entry 携带 ≤8KB outputTail 永久留在 map 中；T3 无 TTL、无上限、无终局清理钩子（对照：同文件 `entry.done` latch、`notificationSent` 等簿记均只写不删）→ map 大小随时钟单调增长。task-output 工具按 taskId 点查不受影响，但堆占用无上界。
- **现状**: F4 无界账本只增不删（本模块第三处内存版：C3-016 sessionMailboxes、C3-017 WaitCoordinator.sessions 之后第 3 例）；与已完成任务"可追溯读取"的产品语义冲突无任何注释调和——无保留期声明。
- **风险**: 长时运行高吞吐部署下每后台任务 8KB 尾 + Entry 元数据按调用数线性累积（10 万次调用 ≈ 800MB+ 尾文本），最终 OOM 或老年代压力；taskId 单调序列使 Entry 永不被覆盖。
- **建议**: 终局后延迟清理（终局时间戳 + 周期 sweeper 或伪 TTL：task-output 读时惰性过期）；或复用平台 `ICache`/`CacheConfig`（容量+淘汰）替换裸 CHM；至少在 close()/会话终局钩子按 sessionId 批量清除。
- **失效面枚举**: (a) `tasks` 主 map——主面；(b) `sessionMailboxes`/`sessionMailboxSubscriptions`（engine 包）——C3-016 已登；(c) `DefaultWaitCoordinator.sessions`（reliability 包）——C3-017 已登；(d) `DeferredAckMailbox.deadLetter`——会话级生命周期随 mailbox 回收（mailbox 本体泄漏面归 C3-016），不重复登记；(e) `LocalAgentMessenger.pendingRequests`——whenComplete 终局移除对称（LocalAgentMessenger.java:105），已排除。
- **信心水平**: 确定
- **误报排除**: 非"有界声明存在"豁免——OUTPUT_TAIL_LIMIT 只界单条大小，不界条数；非 C5 维度越界——泄漏主体是注册表生命周期与异步终局路径的资源对称性（F8/F4），C3 口径内登记。
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现——grep 复证：tasks 全类无 remove；Entry 持 ≤8KB outputTail 终局滞留；同站点跨维双登记：C4-012（P1，另含 timeout 无执行点面）

### [C3-024] LocalAgentMessenger.replySubscriptions 每个 senderId 一条平台订阅只增不删（C3-016 的 messenger 侧兄弟）：DBMessageService 装配下每条死订阅固化为每 50ms 一次的永久 DB 轮询

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/message/LocalAgentMessenger.java:57`（`replyTopic → IMessageSubscription` map）, `123-134`（ensureReplySubscription 只建不删），全类无 cancel/remove；放大器 `message/DBMessageService.java:315-337`（poller 对 `consumers.keySet()` 全量周期轮询）, `367-379`（每 topic 每次 findPending 一条 SELECT）
- **证据片段**:
  ```java
  private void ensureReplySubscription(String replyTopic) {
      IMessageSubscription existing = replySubscriptions.get(replyTopic);
      if (existing != null) return;
      synchronized (replySubscriptions) {
          if (replySubscriptions.get(replyTopic) == null) {
              IMessageSubscription subscription = messageService.subscribe(replyTopic, new ReplyDemultiplexer());
              replySubscriptions.put(replyTopic, subscription);   // 无任何 cancel/remove 调用点
  ```
- **严重程度**: P1
- **并发场景**: messenger 单例（引擎级装配）× 发起 call-agent/request 的各会话线程（每会话 senderId 派生一个 replyTopic）× DBMessageService poller（50ms 周期对全部注册 topic 轮询）。
- **触发时序**: T1 每个首次发起 request 的会话（senderId=会话 id）触发 `AgentMessageTopics.replyTopic(senderId)` 订阅并放入 replySubscriptions；T2 会话终局后订阅永不取消（对比 pendingRequests 的 whenComplete 对称清理——同文件两账本一清理一不清理）；T3 每个历史会话遗留一条活跃平台订阅；T4 DBMessageService 装配下该订阅进入 `consumers` map → poller 每 50ms 对每个死 replyTopic 执行一次 `findPending` SELECT → 无效 DB 查询数随历史会话数线性增长（1000 个历史会话 ≈ 2 万次/分钟纯空查）。
- **现状**: C3-016 登记 engine 侧 per-session 订阅只增不删并排除 `AgentCallDelegate.callAgentSubscription`；messenger 侧 per-sender reply 订阅是其 F3 同族兄弟，未在既有失效面内。reply 订阅的正确清理点天然存在（对应 pendingRequests 落空后 / 会话终局）。
- **风险**: 内存（订阅对象 + handler 链）与 DB 轮询负载双线性增长；LocalMessageService 装配下表现为纯内存与派发扫描增长，DBMessageService 装配下直接转化为数据库负载。
- **建议**: request 侧在 `pendingRequests` 空且订阅 topic 无活跃请求时惰性 `subscription.cancel() + replySubscriptions.remove(topic)`；或引擎会话终局钩子按 senderId 精确取消（与 C3-016 修复同一终局清理点一并治理）。
- **失效面枚举**: (a) `replySubscriptions`——主面；(b) engine `sessionMailboxSubscriptions`——C3-016 已登（同修复批）；(c) `AgentCallDelegate.callAgentSubscription`——单例 + registerCallAgentHandler 有 cancel 旧订阅逻辑（CL-019 登记其并发窗口），已排除；(d) `pendingRequests`——终局对称清理，排除；(e) DBMessageService `consumers` map——仅经 (a)(b) 的 subscribe 间接增长，自身无独立 put 面。
- **信心水平**: 确定
- **误报排除**: 非 C3-016 重复登记——surface 不同（messenger reply topic vs engine inbox topic）、创建时机不同（request 时 vs 执行入口时）、放大形态不同（DB 轮询负载）；非"有界声明"——无容量/淘汰配置。
- **复核状态**: 未复核
- 复核状态: 保留(P1)｜新发现——回码核验成立：replySubscriptions L57 只建不删（L123-134）；DBMessageService.pollAllTopics L315-329 每 50ms 遍历 consumers.keySet() 复核——DB 轮询放大链闭合；同站点跨维双登记：C7-026（P1）

### [C3-025] team-flow 成员超时取消族清扫（F3）：单成员路径无 per-member orTimeout 且整体 backstop 只取消图不取消 agent；spawn 路径超时只释放 worker 不取消已 spawn 执行——与 fan-out BOUND 路径的 cancelSession 补偿不对称

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/flow/MemberAgentTaskStep.java:152`（裸 `agentEngine.execute(request)` 无 orTimeout）, `team/flow/TeamTaskFlowOrchestrator.java:634-667`（整体超时仅 `taskRt.cancel`，无 cancelSession）, `team/DefaultMemberSpawner.java:188-198`（TimeoutException 仅 SPAWN_FAILED 返回，无取消）; 对照面 `team/flow/MemberFanOutDispatcher.java:321-337`（BOUND 超时 → cancelSession(forced=true)）
- **证据片段**:
  ```java
  // MemberAgentTaskStep（单成员默认路径，DefaultTaskMemberRouter 单例 plan 命中此处）：
  CompletableFuture<AgentExecutionResult> engineFuture = agentEngine.execute(request);  // 无 orTimeout
  // TeamTaskFlowOrchestrator 整体超时（AR-3 注释声称"cancel ... so member agents do not
  // continue as zombies"，实际实现）：
  taskRt.cancel(ICancellable.CANCEL_REASON_TIMEOUT);   // 只阻断后继节点启动，
                                                       // 已在途的 engineFuture 无任何取消
  ```
- **严重程度**: P2
- **并发场景**: nop-task DAG 调度线程 × 引擎 member 执行线程（commonPool supplyAsync）× spawn worker 线程；对照 fan-out 路径（同 orchestrator 内多成员 plan）已有完整补偿。
- **触发时序**: 面 1：T1 单成员节点（maxDepth=1 → 整体超时=memberExecTimeoutMs）的 member agent hang；T2 整体超时触发 → `taskRt.cancel` → 报告失败、任务留 CLAIMED；T3 member agent 在引擎侧继续执行（无 cancelSession）持续消耗 LLM 配额；T4 daemon 按 plan 240 收回重派 → 同一任务的新成员执行与 T3 幽灵**并发双跑**。面 2：T1' spawn 路径 `future.get(memberExecTimeoutMs)` 超时 → SPAWN_FAILED、worker 释放；T2' spawned agent（fresh UUID 会话）继续执行无人取消——同族幽灵。
- **现状**: AR-3 注释的防僵尸声明对"已启动节点"不成立（cancel token 只在 GraphTaskStep 启动后继步骤前检查）；fan-out BOUND 路径已按 AUDIT-14-01 语义补齐 cancelSession（I3 R-2-3），单成员路径与 spawn 路径是同族未修兄弟。
- **风险**: 超时/取消路径幽灵执行消耗 LLM 配额 + 收回重派后的双执行副作用；每次超时触发面 1 双跑窗口，与 memberExecTimeoutMs 与 agent 实际执行时长的差值成正比。
- **建议**: MemberAgentTaskStep 补 `orTimeout(memberExecTimeoutMs)` + 超时分支 `engine.cancelSession(memberSessionId, ..., true)`（照抄 executeBoundMember:328-337）；DefaultMemberSpawner 超时分支对 `spawnedSessionId` 补同款取消；orchestrator AR-3 注释更正或在该路径实现同款补偿。
- **失效面枚举**: (a) MemberAgentTaskStep 单成员路径——主面 1；(b) DefaultMemberSpawner spawn 路径——主面 2；(c) MemberFanOutDispatcher.executeBoundMember——已修对照面，排除；(d) MemberFanOutDispatcher.spawnOneTarget——dispatcher 层 orTimeout 到期走 exceptionally → failed outcome，但底层 spawnAndInterpret 中的 spawner get 已由 (b) 界定、agent 幽灵归 (b) 同一修复批；(e) CallAgentExecutor.executeSubAgent——已按 AUDIT-14-01 补齐（CallAgentExecutor.java:463-480），对照面排除。
- **信心水平**: 确定
- **误报排除**: 非 AllMustSucceedReduction 已声明的 Non-Goal（该声明只覆盖 fan-out 兄弟成员 run-to-completion，且本条主面 1 是单成员路径、无声明覆盖）；非"引擎自有超时兜底"——fan-out 路径补 cancelSession 的事实证明引擎不自终 hang 执行。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现——回码逐行核验成立：MemberAgentTaskStep L152 裸 execute、AR-3 注释与实现差距（taskRt.cancel 仅断后继）、DefaultMemberSpawner L188-198 超时仅 SPAWN_FAILED、fan-out BOUND cancelSession 对照面确证；同站点跨维双登记：C4-014（P2）；面 1 收回重派双执行窗与 WI 1.6 C3-005（P1）同族对照——窗口受 memberExecTimeout 差值界定，P2 可辩护，留 7.1 校准

### [C3-026] MemberFanOutDispatcher reduce+complete 完成链在回调线程上 set(capturedTenant)+finally clear() 无保存-恢复（C3-015 家族新面）：BOUND 成员在 engine worker 租户窗口内同步完成时清除 enclosing 租户上下文

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/flow/MemberFanOutDispatcher.java:263-288`（thenApply 内 `ThreadLocalTenantResolver.set(capturedTenant)` + finally `clear()`）; 家族内证 `engine/AgentSessionLifecycle.java:343/518`（同族路径采用 `set(previousTenant)` 保存-恢复）; 窗口来源 `engine/DefaultAgentEngine.java:558-592`
- **证据片段**:
  ```java
  .thenApply(reductionSucceeded -> {
      ...
      ThreadLocalTenantResolver.set(capturedTenant);   // 覆盖当前线程既有租户（无保存）
      try {
          Optional<TeamTask> completed = taskStore.completeTask(...);
          ...
      } finally {
          ThreadLocalTenantResolver.clear();            // remove——不恢复调用方原值
      }
  });
  ```
- **严重程度**: P2
- **并发场景**: engine worker 线程（在自身租户窗口内完成最后一个 BOUND 成员 future，DependentChain 同步在同线程运行）× daemon scan 线程捕获的 capturedTenant（通常 null）。javadoc 自述"runs on whatever thread completed the last member future"。
- **触发时序**: T1 引擎 worker 线程 W 在自身租户窗口内（DefaultAgentEngine:558 set(tenantA) 起，:592 finally 前止）完成 bound 成员 future；T2 dispatch 的 allOf→reduce→complete 链同步在 W 上运行：:268 set(capturedTenant=null)（remove）覆盖 tenantA → CAS 执行 → :286 clear()；T3 控制返回引擎窗口，若引擎在 future 完成点之后、自身 finally 之前还有租户敏感 DB 操作（终局会话保存/用量落库，C3-015 时序已证此类操作存在），其 tenant WHERE 退化为 null → 全租户可见（越权读写面）。
- **现状**: C3-015 登记 cancelSession/forkSession 的同族缺陷并以 resume 路径的 `set(previousTenant)` 保存-恢复为设计意图内证；dispatch 完成链是其 team 包兄弟（C3-015 失效面未含本点），且本点是三处中唯一运行在"他人窗口内"的嵌套清除。
- **风险**: 多租户 DB store 装配 + BOUND 成员由引擎线程完成时，完成链窗口与引擎尾段操作的租户隔离被剥除；触发链确定但依赖 DB 装配与引擎尾段操作顺序，故 P2。
- **建议**: 统一家族修复模式：`String previous = ThreadLocalTenantResolver.current(); try { set(capturedTenant); ... } finally { set(previous); }`（set(null) 已实现 remove 语义，previous=null 等价清除）。
- **失效面枚举**: (a) MemberFanOutDispatcher.dispatch 完成链 :268/:286——主面；(b) MemberFanOutDispatcher.spawnOneTarget worker :376/:380——专用 spawn 线程自包含窗口（set→finally clear 配对完整、无 enclosing 租户），排除；(c) SpawnMemberAgentTaskStep worker :233/:237——同 (b) 自包含，排除；(d) engine cancelSession/forkSession——C3-015 已登；(e) daemon scanOnce:299 只 `current()` 读取不 set，排除。
- **信心水平**: 很可能（set/clear 无保存-恢复与嵌套窗口机制确定；后果依赖引擎在 future 完成点后的租户敏感操作这一 X2 范围事实）
- **误报排除**: 非"ThreadLocal 未清理泄漏"误报校准项的反面——问题是被嵌套清除的他人上下文（与 C3-015 同判例形态）；set(capturedTenant) 本身是声明的裁定行为，缺陷仅在缺保存-恢复。
- **复核状态**: 未复核
- 复核状态: 保留(P2)｜新发现——回码核验成立：dispatch 完成链 thenApply 内 set(capturedTenant)+finally clear() 无保存-恢复（L268/L286 直核），注释自述「runs on whatever thread completed the last member future」为嵌套窗口内证

### [C3-027] C3-020 移交兄弟核对（F3 收口）：TeamTaskSchedulerDaemon.scanOnceSafe 与 DBMessageService 投递/清扫任务的守护覆盖同样不含 Error——守护死亡使团队调度 / 消息投递+stale-claim 兜底静默停摆

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/scheduler/TeamTaskSchedulerDaemon.java:262-276`（scanOnceSafe 仅 catch RuntimeException）, `message/DBMessageService.java:315-329`（pollAllTopics 两层仅 catch Exception）, `572-581`（sweepStaleClaimedMessagesSafely 仅 catch Exception）
- **证据片段**:
  ```java
  // TeamTaskSchedulerDaemon.scanOnceSafe：
  try { SchedulerScanResult result = scanOnce(); ... }
  catch (RuntimeException e) { LOG.warn("... will retry next interval ..."); }  // Error 逃逸
  // DBMessageService.sweepStaleClaimedMessagesSafely：
  try { sweepStaleClaimedMessages(staleClaimTimeoutMs); }
  catch (Exception e) { LOG.error(...); }                                       // Exception ≠ Throwable
  ```
- **严重程度**: P3
- **并发场景**: daemon 调度线程（scheduleWithFixedDelay）× DBMessageService 单 poller 线程（同 C3-021）——均为 `ScheduledExecutorService` 周期任务，Throwable 抑制契约同 C3-020。
- **触发时序**: 面 1：T1 scanOnce 深处（路由器/store/派发链）抛 Error → 逃逸 scanOnceSafe → 调度器抑制后续执行 → 团队任务调度守护静默死亡（CREATED 任务永不再派发，无告警）。面 2：T1' pollAllTopics 或 sweep 中抛 Error（如 payload 反序列化 StackOverflowError、堆边缘 OOM）→ 两任务均被抑制 → 消息投递与 stale-claim 兜底同时静默死亡——at-least-once 保证的无告警失效。
- **现状**: C3-020 失效面枚举 (c)(d) 显式移交本批次核对的两个兄弟；核对结论：同构缺陷成立（daemon 与 C3-020 主面逐字同构；DBMessageService 用 catch Exception，覆盖面比 RuntimeException 稍宽但同样不含 Error）。
- **风险**: 与 C3-020 同判——触发前提是 Error 逃逸（低概率），但面 2 后果叠加 C3-021 的单线程结构：poller 一死即投递+兜底双亡且零信号。
- **建议**: 三处 catch 升级为 `catch (Throwable t)`（ERROR 日志 + 健康计数；按 C3-020 建议的处置分级）；中期接入平台调度治理健康探针。
- **失效面枚举**: (a) `TeamTaskSchedulerDaemon.scanOnceSafe`——主面 1；(b) `DBMessageService.pollAllTopics`（:325/:360 两层 catch Exception）——主面 2；(c) `DBMessageService.sweepStaleClaimedMessagesSafely`（:578）——主面 2 同线程；(d) `ScheduledRecoveryManager`/`SessionLockRenewal`——C3-020 主面，不重复；(e) `DeferredAckMailbox`/team flow 其余同步路径——无周期任务语义，排除。
- **信心水平**: 确定（同构性）；触发概率低故 P3
- **误报排除**: 非 F9 泛化——精确对应 scheduleWithFixedDelay 的 Throwable 抑制契约；非 C3-021 重复——本条是守护覆盖面（Error 维度），C3-021 是单线程结构（串行维度），两者叠加但独立成立。
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜新发现——回码核验成立：daemon scanOnceSafe L272 仅 catch RuntimeException、DBMessageService 两层 catch Exception（L325/L360/L578）均不含 Error；同站点跨维双登记：C4-013（P2）——严重度分叉（P3 vs P2）留 7.1 终裁；并入 WI 2.1 C3-001 平台绝缘层




## 维度集外线索（overlay C2/C6/C8/C9）

### [nop-ai-core-nop-ai-agent-CL-013]（C2/C9）LlmConfigHelper.secretCache 静态缓存 computeIfAbsent 持锁执行文件 I/O；static 可变 secretDir 与运行期 clear 无同步

`service/LlmConfigHelper.java:42-52, 135-143`。`secretCache` 为 static `MapCache`，`resolveApiKey` 在 `computeIfAbsent` 内做 `FileHelper.readText(secretFile)`——密钥文件缺失时每个 provider 的首次解析在缓存锁内做磁盘探测；慢存储/网络盘下并发首调用的**不同** provider 也会在 map 锁上串行（MapCache 内部 synchronized 结构），与 `nop.ai.secret-dir` 热更新（`ChatServiceImpl.setSecretDir` L103-106 为 @InjectValue setter）+ `clearSecretCache()`/`reset()`（L52-67，公开运行期方法）之间无 happens-before：reset 与并发 `resolveApiKey` 交错可能让清空与重装交错出短暂中间态（后果轻——重读文件，但 `secretDir` 引用切换与缓存清除非原子）。建议核对 MapCache 锁粒度并将文件 I/O 移出锁内（先读后 putIfAbsent）。C1 维度若未覆盖 static 字段清单，此处应补登。

### [nop-ai-core-nop-ai-agent-CL-014]（C8）EmbeddingServiceImpl.retryScheduler 惰性创建、无生命周期关闭

`service/EmbeddingServiceImpl.java:88-142, 443-445`。默认惰性创建单线程 daemon `ScheduledThreadPoolExecutor`（`schedulerLock` 双检 + volatile，创建本身正确），但无 `destroy`/`shutdown` 路径：bean 销毁、容器热重载或测试反复 new 服务实例时每实例泄漏一条常驻线程（daemon 使 JVM 退出不受阻，故非 P 级缺陷）。`setRetryScheduler(null)` 恢复默认时旧注入 scheduler 的关闭责任也未界定。建议挂 IoC `@Deinit`/stop 钩子关闭，或复用平台 `GlobalExecutors`/`IThreadPoolExecutor` 治理。

### [nop-ai-core-nop-ai-agent-CL-015]（C1）PromptTemplateManager.loadPromptTemplateFromPath 检查-后-行动双建实例

`prompt/PromptTemplateManager.java:17-24`。`promptModel.getPromptTemplate() == null → new ModelBasedPromptTemplate(...) → promptModel.setPromptTemplate(...)` 非原子：并发加载同一 prompt 的两个线程各建实例、各持返回值，仅其一进入模型缓存。模板实例当前为无状态包装（ ModelBasedPromptTemplate 仅持 final promptModel），实际后果是身份分裂而非状态撕裂；但 `PromptModel.setPromptTemplate` 若将来承担身份语义（如 per-template 缓存）即成缺陷。建议 `ResourceComponentManager` 层内建 compute 语义或同步化该段。

### [nop-ai-core-nop-ai-agent-CL-016]（C9）DefaultChatLogger 每请求同步双文件写 + 全文正则脱敏在请求/回调线程执行

`service/DefaultChatLogger.java:44-79`。`logRequest`/`logResponse` 各做：全量 JSON 序列化 + 3 条正则对全文 replaceAll + 同步 `ResourceHelper.writeText`，分别运行在调用方线程（请求阶段）与 http 回调线程（响应阶段，见 C3-003 场景）。高 QPS 下热路径 I/O 与正则 CPU 无异步缓冲/采样开关（`logDir=none` 仅关文件写，`LOG.info` 全量 content 仍打）。与 C3-003 独立：此处是性能/吞吐面而非正确性面。建议脱敏+写盘移入单线程后台 appender 或平台日志管线。

### [nop-ai-core-nop-ai-agent-CL-017]（C2）ThresholdBreaker HALF_OPEN 探测超时逃逸后，迟到旧探测的 record 结果会错误驱动状态机

`reliability/ThresholdBreaker.java:allowCall HALF_OPEN 分支 / recordSuccess / recordFailure`。探测超时逃逸（probeTimeoutMs 后下一位调用者重取探测位）设计正确且必要，但旧探测（卡顿后终于返回）在 `recordSuccess`/`recordFailure` 中无法被识别为"过期探测"：旧探测迟到失败 → HALF_OPEN→OPEN 重启冷却，使刚准入的新探测被无辜回收；迟到成功 → 条目清空（CLOSED），新探测的最终结果落到无记忆状态。后果是熔断器在探测卡顿场景下偏保守/短暂失真，无悬挂、无泄漏（entry shrink 逻辑健全），故仅作 C2 语义线索登记：建议 record 方法携带探测凭据（probeStartedAt 时间戳比对）或接受现状并在 javadoc 声明该近似语义。

### [nop-ai-core-nop-ai-agent-CL-018]（C2/C8）AgentSessionSupport.ensureSessionMailbox 在 computeIfAbsent 锁内执行 registerHandler（代理 I/O）；CL-013 同族兄弟

`engine/AgentSessionSupport.java:56-68`。`sessionMailboxes.computeIfAbsent` 的 lambda 内调用 `messenger.registerHandler(inboxTopic, handler)`——对 LocalAgentMessenger 是轻量 map 操作，对 DB/远程代理实现则是潜在网络 I/O，持有 ConcurrentHashMap bin 锁执行：首个为某会话建 mailbox 的线程会阻塞**不同 key** 的并发 ensureSessionMailbox（同 bin 冲突时）。与已登记 CL-013（LlmConfigHelper.secretCache computeIfAbsent 持锁文件 I/O）为同一缺陷族在 engine 包的兄弟实例（F3 清扫命中）。另注：lambda 返回 null（mailboxFactory 缺席）时 CHM 不存条目但副作用（registerHandler/put 订阅）已执行，下次调用重复注册路径。建议把 mailbox 创建与订阅注册移出 compute 锁（先构建后 putIfAbsent），与 C3-016 的清理缺失一并治理。

### [nop-ai-core-nop-ai-agent-CL-019]（C1）AgentCallDelegate.registerCallAgentHandler 对 volatile 订阅句柄做检查-后-行动：并发 setMessenger 可泄漏旧订阅

`engine/AgentCallDelegate.java:48-65`。`existing = this.callAgentSubscription; if (existing != null) existing.cancel(); ... subscription = registerHandler(...); this.callAgentSubscription = subscription;` 非原子：两线程并发 setMessenger 均读到 existing==null → 各自注册 → 字段仅保留后者，前者的 handler 永久留在 messenger 上无人取消（主题 `call-agent` 上重复投递、handler 泄漏）。setMessenger 属配置/装配期操作，现实概率低，故 CL 登记；若 messenger 支持运行期热切换（如配置中心刷新）应升级为缺陷。修复：synchronized 化该方法或以 CAS 语义（AtomicReference#updateAndGet）串行化"取消旧 + 注册新"。

### [nop-ai-core-nop-ai-agent-CL-020]（C6/C9）DefaultAgentEventPublisher.publish 在调用方线程同步扇出全部订阅者：慢订阅者拖住引擎 worker/ReAct/injectInput 关键路径

`engine/DefaultAgentEventPublisher.java:36-57`。`publish` 逐个同步调用 `subscriber.onEvent`，发布点遍布引擎关键线程：worker 的终局事件、ReAct 循环的 EXECUTION_STARTED/TOOL_CALL_COMPLETED/COMPACTION、injectInput 的 INJECTION_QUEUE_UPDATED、actor 消费线程（InMemoryActorRuntime L473）。慢/阻塞订阅者直接放大执行延迟并延长接管锁持有时间（worker 终局发布点在 releaseLockQuietly 之前）；异常已有 per-subscriber catch + 熔除策略（WI10，实现健全），非异常面。建议治理性评估：订阅者执行移入有界队列异步派发（平台 `IBlockingSource/IBlockingConsumer` + `QueueOverflowPolicy`），或至少在 javadoc 声明"订阅者不得阻塞"契约并加超时守卫。

### [nop-ai-core-nop-ai-agent-CL-021]（C2/C6）InMemoryActorRuntime.destroyActorInternal 在引擎 worker 终局 finally 内同步 awaitTermination 最长 5s，且发生在锁释放与会话持久化之前

`runtime/InMemoryActorRuntime.java:364-398`（`executor.shutdownNow()` + `awaitTermination(shutdownTimeoutMs)`）；调用点 `engine/DefaultAgentEngine.java:1022-1025`（终局 finally 中、`releaseLockQuietly` L1067 之前）。消费循环线程若正阻塞在 `mailbox.poll()`（实现相关）或同步事件发布（CL-020 面污染）中，中断响应延迟将直接计入 worker 终局：接管锁延迟释放（最长 5s/次）、终局会话保存延迟、后续同会话请求排队。与 C3-013 的 actor 丢失窗口同源（destroy 与 drain 的相对次序）。建议 destroyActor 异步化（提交到独立清理执行器）或先行 release 锁再等待 actor 终止；至少把 awaitTermination 从终局关键段移出。

### [nop-ai-core-nop-ai-agent-CL-022]（C3）handleCallAgentRequest 以 orTimeout().join() 同步阻塞 messenger 派发线程最长 timeoutMs：嵌套 call-agent 链的死锁安全性完全依赖 messenger 派发并行度

`engine/AgentCallDelegate.java:86-127`。handler 在 messenger 的派发线程上 `engine.execute(...).orTimeout(...).join()` 阻塞等待子代理执行完成（默认 callAgentTimeoutMs=120s）；子代理（CallAgentExecutor）再经 messenger 发起下层 call-agent 时，若 IAgentMessenger 实现为单线程串行派发（接口未声明并行度契约），父 handler 占住唯一派发线程 → 子请求的响应/注册无法被处理 → 链式死锁。当前模块内 LocalAgentMessenger（message 包）派发模型未审计（跨单元），仅登记依赖：IAgentEngine 契约应显式声明"handleCallAgentRequest 的调用线程不得为子代理执行所依赖的单线程"或 handler 改异步（返回 CompletableFuture 载荷的 messenger 契约）。超时→cancelSession 补偿（L111-119）实现正确，非缺陷面。

### [nop-ai-core-nop-ai-agent-CL-023]（C3）槽位注册到 worker 绑定线程之间（enqueue 窗口）的 forced cancel 中断静默丢失：强制取消退化为纯协作式取消

`engine/DefaultAgentEngine.java:525-533`（`handle.thread` 为 null 时跳过 interrupt）+ `972`/`AgentSessionLifecycle.java:369/544/934`（worker 进入 lambda 才绑定线程）。CancelHandle 注释（AgentSessionLifecycle L109-127）承认该窗口但未补偿：若 cancelSession(forced=true) 落在 supplyAsync 提交后、lambda 执行前，interrupt 无处投递且事后不补发——lambda 随后照常运行，仅靠 `ctx.isCancelRequested()` 在 ReAct 循环顶/等待相的检查点软退出；正在进行的首次 LLM 调用（`f.get` 可中断但无人中断它）将持续到自身完成或超时。后果有限（多等一次 LLM 往返，最终仍会取消），故 CL 登记。修复方向：CancelHandle 增加取消已请求标志，worker 绑定线程后检查该标志并自补 interrupt。

### [nop-ai-core-nop-ai-agent-CL-024]（C3）DBMessageService 订阅生命周期三缺陷：同 topic 后订阅静默顶替先订阅、陈旧订阅 cancel 可注销新持有者、suspend/resume 标志从未被投递路径读取

`message/DBMessageService.java:297-313`（subscribe 用 `consumers.put(topic, listener)` 直接覆盖；unregisterConsumer 无条件 `consumers.remove(topic)`）+ `640-674`（DbSubscription.cancel/suspend/resume）。三个独立语义洞：(1) 同 topic 二次 subscribe 静默顶替——先订阅者无任何信号丢失投递资格，也无异常（LocalAgentMessenger.replySubscriptions 有 get==null 守卫不触发此路径，但 registerHandler 直通 messageService.subscribe 可重复）；(2) 所有权不校验——T1 A 订阅 topic X → T2 B 订阅顶替 → T3 A 生命周期结束调 cancel → `consumers.remove(X)` 把 B 的注册一并拔除，B 从此静默收不到消息（时序具体，但 shipped 装配中引擎从不 cancel 会话订阅（C3-016），现实可达性低，故 CL）；(3) `suspended` 标志只在 DbSubscription 内部读写，pollAllTopics/pollTopic 从不检查 `isSuspended()`——IMessageSubscription 契约（nop-api-core IMessageSubscription.java 声明 suspend/resume）被实现为静默 no-op，挂起期间投递照常。建议：subscribe 检测已占用即抛/返回失败；cancel 校验 `consumers.get(topic) == this` 才移除；pollTopic 循环内读取 suspended 标志跳过投递。

### [nop-ai-core-nop-ai-agent-CL-025]（C6/C9）AgentDispatchExecutor 将引擎工具派发桥接到 ForkJoinPool.commonPool：无界 runAsync、无拒绝策略、与引擎自身 commonPool 供给叠加

`tool/AgentDispatchExecutor.java:33-53`。`execute/submit` 全部落 `CompletableFuture.runAsync`（commonPool），平台设施 `IThreadPoolExecutor`/`ThreadPoolConfig`/`StandardThreadPoolExecutor` 仅被适配为空壳（getConfig 返回裸配置、stats 返回空壳）。风险形态：DefaultAgentEngine 自身一参 supplyAsync 也在 commonPool（TeamTaskFlowOrchestrator.java:189-198 javadoc 已明文警告"spawn worker 同步 join 引擎 future 时共享 commonPool 会在并发 ≥ parallelism 时停摆"）——工具体若同步等待任何引擎 future（当前 CallAgentExecutor 走异步、未触发；但契约上 `context.getExecutor().submit(...)` 的 toolkit 工具可在 commonPool 内做任意阻塞 I/O），即复现同一 stall 形态且 commonPool 拒绝策略不可配。建议：装配处换平台受管池（有界队列 + QueueOverflowPolicy），或将 commonPool 依赖显式写入 javadoc 契约并加部署警告。

### [nop-ai-core-nop-ai-agent-CL-026]（C3/C5）InMemorySpillStore 普通 HashMap 跨线程读写无同步；spill 条目无上限、无删除路径

`compact/InMemorySpillStore.java:26-60`。`store` 为普通 `HashMap`，`put`（ReAct worker 压缩溢出路径）与 `get`（read-spill 工具经 AgentDispatchExecutor commonPool 线程）分属不同线程，无任何同步——并发 put/get 是数据竞争（HashMap 非线程安全，resize 期间读可见不一致）。触发时序难以具体化（spill 与 read-spill 通常分属相邻 turn），按纪律不构成 P 级缺陷，登记为 C3/C1 线索：改 `ConcurrentHashMap` 零成本。另 `store` 只 put 从不 remove，条目生命周期完全依赖"session 持有引用、会话结束整体 GC"的声明（javadoc L5-7）——单会话内无限 spill 仍无上界（read-spill 工具语义上要求条目存活，属已声明边界，不计缺陷）。

### [nop-ai-core-nop-ai-agent-CL-027]（C3）DefaultBackgroundTaskRegistry.stop()/run() 的两处窗口：future 赋值前 stop 读到 null 跳过取消；stop 状态检查后 body 正常完成则 stop 仍报 FAILED

`tool/DefaultBackgroundTaskRegistry.java:115-131`（`run` 中 `entry.future = future` 在 submit 之后赋值）+ `259-285`（`stop` 在 synchronized 块外执行 hook/cancelFuture/complete）。窗口 1：submit 与 future 赋值之间 stop() → cancelFuture 读到 null 跳过取消 → complete(FAILED) 先胜 → body 照常执行成幽灵（结果因状态非 RUNNING 被丢弃）。窗口 2：stop 的 RUNNING 检查（synchronized 内）与 complete(FAILED)（synchronized 外）之间 body 正常 complete(COMPLETED) → stop 的 FAILED 为幂等 no-op，但返回值已向 LLM 报 FAILED——与终局通知（COMPLETED）矛盾。两窗口均窄且后果轻（错误状态回报/短时幽灵），与 CL-023 的 enqueue 窗口取消丢失同族，登记不升级。修复方向：stop 全程持 synchronized(entry) 并将 future 读取移入；或 Entry 增加取消请求标志由 body 侧自检。

### [nop-ai-core-nop-ai-agent-CL-028]（C3/C1）FileBackedSessionStore.remove 与并发 save 交错可使已删除会话文件复活（陈旧幽灵快照）

`session/FileBackedSessionStore.java:177-203`（remove 持 per-session 锁删除 cache/journal/快照文件并从 `sessionLocks` 移除锁对象）+ save 路径（`synchronized (sessionLock(sessionId))`）。时序：T1 引擎侧持有旧 AgentSession 引用的保存路径（injectInput 线程）刚进入 `sessionLock(sessionId)` 拿到锁对象 L1；T2 remove 在 L1 上排队；T1 写完释放；T2 完成 sessions/locks 移除与文件删除；T3 同一线程（或另一持有旧 session 引用的线程）再次 save：`sessionLock(sessionId)` 因 map 已移除而新建 L2 → 全锁序列对 remove 无感知 → 旧会话文件 + cache 条目整体复活（重启后 restorePendingSessions 会将其作为孤儿恢复）。到达前提是 destroy/remove 与在途 save 并发（强制停止 + steering 并发），现实概率低；后果是已销毁会话的幽灵持久化。建议：save 侧在锁内复查 `sessions.containsKey`（或 remove 设置墓碑标记）后再写盘。

---

## 维度复核结论（C3 独立复核，2026-10-18）

- **复核员与口径**：C3 维度（异步链路正确性）独立复核员，按 `ai-dev/skills/concurrency-resource-audit-prompt.md` 口径纪律与严重度定义执行三态判定（保留/降级/驳回）+ 现状标注。复核对象：C3-001…027（本文件）+ C3-028/029（round2）+ C3-030/031/032（round3），共 32 条。
- **复核方法**：全部 32 条逐条回码验证（源码根：本审计工作树 commit 22dc8bd3df，与审计基线一致、`nop-ai` 零改动）；证据片段与 file:line 逐一对照 live code；时序论证逐步核验；降档口径核对（本单元全部异步面为请求触达/长时运行路径，启动期降档不适用——各批次口径声明与覆盖声明复核一致）；跨单元对照 `ai-dev/audits/cra-index.md` P1 台账与 CL 台账（nop-stream 异步族、WI 1.x future 悬挂族、WI 2.1 C3-001 平台绝缘层、WI 2.1 CL-021 移交面、WI 2.3 CL-022 Delayer 移交面）；同单元 C1/C4/C5/C7 维度同站点跨维条目逐一比对（指针登记，跨维合并归裁定阶段）。

### 逐条判定表（C3-001…032）

| ID | 文件 | 原严重度 | 判定 | 最终严重度 | 现状 | 跨维/跨单元同案指针 |
|----|------|---------|------|-----------|------|--------------------|
| C3-001 | 主文件 | P1 | 保留 | P1 | 新发现 | C4-002（同站点）；WI 2.4 C3-001 同族 |
| C3-002 | 主文件 | P1 | 保留 | P1 | 新发现 | C4-001（同站点）；WI 1.2 C4-004 同案（共享 globalWorker 阻塞） |
| C3-003 | 主文件 | P1 | 保留 | P1 | 新发现 | WI 2.4 C3-001 同族（future 永不完成）；单元内姊妹 C3-006 |
| C3-004 | 主文件 | P2 | 保留 | P2 | 新发现 | — |
| C3-005 | 主文件 | P2 | 保留 | P2 | 新发现 | — |
| C3-006 | 主文件 | P2 | 保留 | P2 | 新发现 | — |
| C3-007 | 主文件 | P2 | 保留 | P2 | 新发现 | grep 复证 requestTimeout 全模块零消费点 |
| C3-008 | 主文件 | P2 | 保留 | P2 | 新发现（理论风险标注合规） | — |
| C3-009 | 主文件 | P2 | 保留 | P2 | 新发现 | C4-001（同站点）；WI 2.1 CL-021 移交面（裸 JDK 载体，点名 ai-agent） |
| C3-010 | 主文件 | P1 | 保留 | P1 | 新发现 | —（附注见下） |
| C3-011 | 主文件 | P1 | 保留 | P1 | 新发现 | kill 通道调用点分布 grep 复证（仅 2 处） |
| C3-012 | 主文件 | P2 | 保留 | P2 | 新发现 | 与 C3-011 同族异载体（发现自身已划界） |
| C3-013 | 主文件 | P2 | 保留 | P2 | 新发现 | 终局次序逐行核验（drain 在 executor 内、remove 在引擎 finally） |
| C3-014 | 主文件 | P2 | 保留 | P2 | 新发现 | C1 维度 C1-C 批 P1（runningInjectionTargets resume 裸 ArrayList）同站点——跨维合并归裁定阶段 |
| C3-015 | 主文件 | P2 | 保留 | P2 | 新发现 | — |
| C3-016 | 主文件 | P1 | 保留 | P1 | 新发现 | grep 复证零 remove/cancel |
| C3-017 | 主文件 | P2 | 保留 | P2 | 新发现 | 累积子面与 C7-017（P2）同站点；语义击穿面为本条独有 |
| C3-018 | 主文件 | P2 | 保留 | P2 | 新发现 | — |
| C3-019 | 主文件 | P3 | 保留 | P3 | 新发现 | 逃生舱限定面核验（resolveToolTimeoutMs ≤0 路径） |
| C3-020 | 主文件 | P3 | 保留 | P3 | 新发现 | C4-007（同站点）；**WI 2.1 nop-kernel-concurrency-C3-001 平台周期任务绝缘层（29 站点族）——建议并入该平台修复批次** |
| C3-021 | 主文件 | P1 | 保留 | P1 | 新发现 | C4 CL-040、C7-025（同文件异面）；CL-022 抽象依赖由本条坐实 |
| C3-022 | 主文件 | P1 | 保留 | P1 | 新发现 | C4-010（P1）同站点双登记 |
| C3-023 | 主文件 | P1 | 保留 | P1 | 新发现 | C4-012（P1）同站点双登记 |
| C3-024 | 主文件 | P1 | 保留 | P1 | 新发现 | C7-026（P1）同站点双登记 |
| C3-025 | 主文件 | P2 | 保留 | P2 | 新发现 | C4-014（P2）同站点双登记；WI 1.6 C3-005（P1）同族对照 |
| C3-026 | 主文件 | P2 | 保留 | P2 | 新发现 | C3-015 家族（发现自身已划界） |
| C3-027 | 主文件 | P3 | 保留 | P3 | 新发现 | C4-013（P2）同站点双登记——严重度分叉（P3 vs P2）留 7.1 终裁；并入 WI 2.1 C3-001 平台绝缘层 |
| C3-028 | round2 | P1 | 保留 | P1 | 新发现 | 方言流式族修复批（与 030/031/032 同批）；与 C7-002 已划界 |
| C3-029 | round2 | P2 | 保留 | P2 | 新发现 | C3-006 先例同族（发现自身已划界）；兄弟站点已收口 |
| C3-030 | round3 | P1 | 保留 | P1 | 新发现（一处校正见高风险子项说明） | 方言流式族修复批 |
| C3-031 | round3 | P1 | 保留（条件面） | P1 | 新发现 | 与 C3-028 互为前提（「两案必居其一」闭合） |
| C3-032 | round3 | P2 | 保留 | P2 | 新发现 | 与 C7-002 已划界；方言流式族修复批 |

### 判定统计与分布

- **三态判定**：保留 32 / 降级 0 / 驳回 0。全部条目证据片段、file:line、触发时序经回码核验成立；无一条达到降级（证据失实）或驳回（误报）门槛。
- **最终保留严重度分布**：P1×13（001/002/003/010/011/016/021/022/023/024/028/030/031）、P2×16（004/005/006/007/008/009/012/013/014/015/017/018/025/026/029/032）、P3×3（019/020/027）——与初审/深挖原始判级一致（无升无降）。
- **现状标注分布**：新发现 32 / 已知未修复 0 / 已修复 0。与 dispatch-log 历史审计对照口径一致（`ai-dev/audits/` 下 nop-ai 并发主题直接审计 0 命中，本单元为该两模块首次并发审计）；cra-index WI 1.1–2.5 台账经逐条比对，与本单元 C3 维度零同单元重复立项（全部命中为跨维同站点指针或跨单元同族对照，见上表与下方清单）。
- **台账计数勘误（供 7.1 对账）**：dispatch-log「C3 合计 P1×12/P2×10/P3×5」与「深挖后 C3×32（P1×15/P2×12/P3×5）」为誊写算误——按三文件实际条目：初审 P1×10/P2×14/P3×3，终局 **P1×13/P2×16/P3×3 = 32**。cra-index/roadmap 对账以本节为准。

### 同案命中清单（跨维/跨单元指针，不构成重复立项）

| 本单元条目 | 同案面 | 处置建议 |
|-----------|--------|---------|
| C3-001/C3-002/C3-009 ↔ 同单元 C4-001/C4-002 | callStream publisher（取消不关闭/缺省池/背压）三轴分立 | 裁定阶段并单：publisher 修复一动三得，轴不同不互相吸收 |
| C3-014 ↔ 同单元 C1（C1-C 批 P1） | runningInjectionTargets 恢复路径缺失注册 × 裸 ArrayList | 同站点两面（注册缺失=C3 面、容器裸奔=C1 面），修复同一 diff |
| C3-017（累积面）↔ 同单元 C7-017 | DefaultWaitCoordinator.sessions 只增不删 | 修复点同一（IWaitCoordinator 生命周期协议缺失），语义击穿面（陈旧 deadline）仅 C3 立案 |
| C3-020/C3-027 ↔ WI 2.1 nop-kernel-concurrency-C3-001 | 周期任务 catch 不含 Error → scheduleWithFixedDelay 静默永久停摆 | **并入 WI 2.1 平台绝缘层统一修复**（平台层落地即覆盖 nop-ai 四站点：ScheduledRecoveryManager/SessionLockRenewal/TeamTaskSchedulerDaemon/DBMessageService 双任务），严重度随平台裁定 |
| C3-021 ↔ 同单元 C4 CL-040、C7-025 | DBMessageService：单线程互饿（CL-040）/ 毒消息重投（C7-025 P1）/ Error 停摆（C3-027 面 2） | 同文件四轴（串行度/守护/毒消息/轮询），裁定阶段合并为 DBMessageService 治理工单，勿四处分修 |
| C3-022 ↔ 同单元 C4-010（P1） | TeamExecuteFlowExecutor per-invocation orchestrator 池泄漏 | 完全同站点同根，裁定阶段并单（两轴：线程池生命周期 C4 / 异步终局不对称 C3） |
| C3-023 ↔ 同单元 C4-012（P1） | DefaultBackgroundTaskRegistry.tasks 只增不删 | C4-012 载荷更重（另含 timeoutMs 无执行点 + 挂死任务 F5 面），建议主处置归 C4-012，C3 面随单吸收 |
| C3-024 ↔ 同单元 C7-026（P1） | LocalAgentMessenger.replySubscriptions 只增不删 × DB 轮询放大 | 同站点同根，裁定阶段并单 |
| C3-025 ↔ 同单元 C4-014（P2） | MemberAgentTaskStep 无 per-member orTimeout/超时取消 | 同站点同根，裁定阶段并单 |
| C3-021/单元 CL-022 ↔ WI 2.3 CL-022（Delayer 移交 nop-message） | **编号巧合，不同案**：WI 2.3 CL-022 是 nop-message 的 JDK Delayer orTimeout 11 站点共享面（Wave 4 相邻单元）；本单元 CL-022 是 AgentCallDelegate join 依赖 | 登记防混淆；messenger 契约异步化修复时两单元协同点在 IAgentMessenger/IMessageService 契约层 |
| C3-009/C3-012 ↔ WI 2.1 CL-021（移交面点名 ai-agent） | 裸 JDK 载体（commonPool）绕过平台执行器设施 | 移交面承接确认：本单元 C3-009/C3-012/CL-025 为该移交在 nop-ai 的具体化站点，修复批次对齐平台设施替换 |
| C3-003 ↔ WI 2.4 C3-001；C3-002 ↔ WI 1.2 C4-004；C3-025 面 1 ↔ WI 1.6 C3-005 | future 永不完成族 / 共享 worker 池阻塞族 / 超时幽灵双执行族 | 跨单元同族对照，无重复立项（站点/模块不同）；Phase 8 批次按族对齐修复模式 |

### 高风险条目子项复核说明

1. **C3-028（R2，方言流式 tool_call 截断）**——修复语义敏感，逐站点回码：
   - OpenAiDialect `parseToolCallDelta`：for 循环内 `return; // 单 chunk 返回，取首个 entry` 直核成立（同一 SSE 事件内多 entry 仅首条产出）。
   - GeminiDialect：:496（text 首 part）与 :512（functionCall 首 part）两处提前 `return chunk` 直核；「text 先于 functionCall 则整帧 functionCall 全丢」变体由代码结构确证（text 支 return 在 functionCall 支之前）。
   - OllamaDialect :341 `get(0)` 直核（但见 C3-031——该支在真实线格式下可能整体不可达，两案修复须同批）。
   - 免疫对照：Anthropic 事件模型单事件单 content_block、Responses output_item 单事件单 item，逐 switch-case 核验成立。
   - 畸形累积子 claim：`ToolCallAccumulator.toToolCall` 仅在 `callId==null && name==null && argumentsBuilder.length()==0` 时返回 null——裸 DELTA（args 片段先到、声明条目被截断）确可产出 name=null 的 toolCall，复证成立。
   - **矩阵结论与代码一致**；修复方向（多 chunk 契约）与 StreamAggregator 既有 per-itemIndex 多累积器（computeIfAbsent 直核）兼容，回归面判断正确。
2. **C3-030（R3，流式 usage 全链路缺失）**——矩阵逐格回码：OpenAi parseStreamChunk（:248-305）仅读 delta/finish_reason、零 usage；buildBody（:151-184）无 stream_options/include_usage（全仓 grep 零命中复证）；Gemini/Ollama parseStreamChunk 区间零 usage/eval_count 读取；Responses :571-574 复用 parseUsageFromResponses（正确对照）；StreamAggregator usage 通道（:502-505 last-wins + :536 落 response）与 `TestStreamAggregator.testAggregator_usage` 单测在册——「通道建成、源头断流」判定准确；ReActAgentExecutor :1046 `usage != null` 门直核。
   - **一处校正（不改判定）**：发现正文与矩阵称 Anthropic「:538-546 只读 model → promptTokens 恒 null」——回码显示 message_delta 处理器（:613-621）实际读取 `input_tokens/cache_read_input_tokens/cache_creation_input_tokens` 四字段，故「恒 null」仅在 classic 协议（message_delta.usage 仅 output_tokens）下成立；若 provider 新版在 message_delta 携带 input_tokens，则 Anthropic 流式 usage 通道部分可用。发现自身的信心注记已完整披露该版本依赖（「届时『残缺』降为『部分残缺』」），且 P1 判级站于 OpenAI/Gemini/Ollama 三站点全缺 + buildBody 双重缺失，不受该校正影响。修复建议 (d) 中「StreamAggregator 需字段级合并」的判断仍成立（message_start 与 message_delta 分帧携带字段时 last-wins 会互相覆盖）。
3. **C3-031（R3，Ollama 空串抢占）**——代码事实全核验：阶梯首支 `if (content != null)`（:327）、`getStringByPath` 经 `toString()` 保值空串（AbstractLlmDialect:566-572 直核——空串非 null，命中 text 支）、仓库 P0 回归样本 TestOllamaDialect:195 区段确以 `"content":null` 钉住（恰好掩盖空串变体）、OpenAI 同型阶梯 `!isEmpty` 判定对照（:265-266）确证「同族判定纪律分叉」。线格式前提（Go Message.Content 无 omitempty 恒序列化空串）在本仓不可直接证实——发现自评「很可能」诚实；复核认定其「两案必居其一」闭合论证成立：前提成立则本条 P1（thinking/tool_calls 全量丢失，比 C3-028 更彻底）；前提不成立则 C3-031 自动降级、C3-028 的 Ollama get(0) 面恢复为真实缺陷（同 P1 档）——任一线格式下 Ollama 流式路径均有 P1 级缺陷在案，修复须 isEmpty 守卫 + 多 entry 产出双根同除，并以真实 ollama serve 抓帧样本回归。
4. **C3-032（R3，流式带内错误路由缺失）**——五站点逐行核验：Anthropic :646-661 error 事件 → `[ERROR] ` text DELTA 直核（聚合后 isSuccess()==true、伪造内容入 ctx 的链路闭合）；OpenAi/Gemini/Ollama parseStreamChunk 区间 grep 零 error 分支（带内错误载荷落入仅 id/model 的空 chunk 或 null，被 StreamAggregator type==null 分支跳过——静默丢确证）；Responses :579-580 default null 直核（response.failed → DONE+finishReason 与非流式一致，发现自身已正确排除该子面）；归一通道对照（parseErrorResponse 携带 errorClassification/Retry-After、parseStreamError 仅 ARG_HTTP_STATUS 可达）直核成立。「与 C7-002 划界」（有信号路由错 vs 无终止信号）正确，无重复计数。

### 复核附注（不改判定的核验增量）

- **C3-010**：dispatchExecution 自身的外层 catch（L1080-1089）清算是「三件套」（runningExecutions.remove + releaseLockQuietly + cancelLockRenewalQuietly），**同样不含 runningInjectionTargets.remove**——发现的修复建议（doExecute 外层 catch 补四件套）应扩展为两个窗口（注册→dispatch 前 + dispatchExecution 提交失败）统一四件套镜像，否则提交失败路径仍残留注入目标（后果较轻：injectInput 向已死执行投递，随终局 remove 自愈，但窗口内 staging/live 分支误判同理存在）。
- **C3-021**：装配条件面复核——引擎 Builder 缺省 `messenger = NoOpAgentMessenger.noOp()`（DefaultAgentEngine:158），shipped beans（nop-ai-service `_service.beans.xml`/`app-service.beans.xml`）未接线任何 IAgentMessenger 实现，DBMessageService 为部署方显式装配的生产多实例传输。不适用「零生产实例化条件化降档」：该类是模块交付的 DB 传输实现（schema init/claim/at-least-once 全套），其触发面正是其设计场景，与 WI 2.5 C7-012（条件面 P1）判例一致——维持 P1，条件面随条目披露。
- **降档口径核对**：三批次覆盖声明均声明「启动期降档不适用」（全部异步面为请求触达/长时运行路径）——复核无异议；C3-019/C3-020/C3-027 的 P3 为触发前提限定（逃生舱配置/Error 逃逸）而非调用面降档，口径正确。

## 最终保留项

C3 维度 32 条全部保留（P1×13 / P2×16 / P3×3），无降级、无驳回。按修复优先级分组：

- **P1×13**：
  - 流式悬挂/背压/执行面（nop-ai-core）：C3-001（取消单向悬挂）、C3-002（背压缺失共享池饥饿）、C3-003（日志 I/O 前置悬挂）
  - 引擎执行槽与取消链（nop-ai-agent engine）：C3-010（注册→dispatch 窗口孤儿化+接管锁无限续命）、C3-011（工具超时/取消不传播+javadoc 失实）
  - 长时运行无界账本（F4/F8）：C3-016（sessionMailboxes+订阅）、C3-022（flow orchestrator 池泄漏↔C4-010）、C3-023（BackgroundTaskRegistry↔C4-012）、C3-024（replySubscriptions×DB 轮询↔C7-026）
  - 消息面（message）：C3-021（DBMessageService 单线程 poller 内联，嵌套委派确定性失败）
  - 方言流式协议族（同批修复）：C3-028（tool_call 单事件截断）、C3-030（usage 全链路失活）、C3-031（Ollama 空串抢占，条件面，与 028 互为前提）
- **P2×16**：C3-004（幽灵重试）、C3-005（同步抛出逃逸包络）、C3-006（缓存读写语义不对称）、C3-007（requestTimeout 死契约）、C3-008（晚订阅竞态，理论风险）、C3-009（commonPool 承载阻塞 I/O）、C3-012（LLM 幽灵调用）、C3-013（injectInput 终局边界三面）、C3-014（恢复路径注入目标缺失↔C1 同站点）、C3-015（cancelSession 租户清除）、C3-017（陈旧 deadline 击穿 WAIT_FOR）、C3-018（恢复 fire-and-forget 静默）、C3-025（成员超时取消族不对称↔C4-014）、C3-026（fan-out 完成链租户嵌套清除）、C3-029（审计/记账副作用中止主流程）、C3-032（流式带内错误路由缺失）
- **P3×3**：C3-019（逃生舱路径批提交无隔离）、C3-020（周期任务 Error 逃逸↔并入 WI 2.1 C3-001 平台绝缘层）、C3-027（同族兄弟收口↔同上，严重度分叉留 7.1）

**修复批次建议（供 7.1 裁定）**：(1) 方言流式协议族单批（C3-028/030/031/032——多 chunk 契约 + usage 采集 + isEmpty 守卫 + 错误出口，集成抓帧样本回归）；(2) callStream publisher 三轴并单（C3-001/002/003/009 ↔ C4-001/002）；(3) DBMessageService 治理工单（C3-021/027 ↔ CL-040/C7-025）；(4) 注册表/订阅终局清理批（C3-016/022/023/024 ↔ C4-010/012/C7-026）；(5) 引擎取消传播批（C3-011/012/025）；(6) 平台绝缘层承接（C3-020/027 并入 WI 2.1 C3-001）。跨维合并归裁定阶段，本节仅登记指针。
