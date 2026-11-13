# C7 长时运行稳定性 — nop-ai-core-nop-ai-agent（WI 3.1）

## 审计范围与口径

- **批次**：X1/3（本文件为该批次初审产物；后续批次追加时在「发现」节连续编号）。
- **覆盖分区**：`nop-ai/nop-ai-core/src/main/java` 全量（119 个 .java / 19,227 行，含 `_gen/` 生成物；非生成源约 100 文件全数列入审读清单）。逐文件审读方式：reliability / service / persist / dialect / command / initialize / file 包全文精读；response / prompt / simplifier / splitter / model 包按 C7 画像（单调增长、挂死点、时钟、状态机）定向扫描。
- **方法**：以 `_tmp/cra-baseline-nop-ai-core-nop-ai-agent.txt` 的 C7a/C7b/C7c 节（ROOT: nop-ai-core 分节：C7a 计数器增量为空、C7b 仅 `LocalFileOperator.java:268`/`DefaultAiChatResponseCache.java:53` 两处 delete、C7c 若干 session/request 状态写）为起点，在其上做全模块 C7 六步盘点（单调增长、挂死可检测性、重试风暴、自愈语义、时钟与期限、热路径慢性病）；数量结论均人工复现到 file:line。
- **跨模块证据**：C7 判断涉及的 nop-kernel（`CoreMetrics`、`RetryHelper`）与 nop-http（`JdkHttpClient`/`ServerEventPublisher` 超时语义）仅作证据引用，不作为发现对象（平台基础设施不重审，只审 nop-ai-core 的使用方式）。
- **降档口径声明**：本模块为 LLM 调用链长时运行形态，**不适用启动期单线程降档**。核查结果：模块内唯一常驻线程为 `EmbeddingServiceImpl` 懒创建的 daemon 重试调度器（`EmbeddingServiceImpl.java:126-142`），属运行期懒创建、非启动期降档；其任务（`dispatch` → `fetchAsync`）非阻塞，单线程不构成瓶颈，不报缺陷。
- **盲审纪律**：未读 `ai-dev/audits|plans|bugs|lessons|backlog/**`，未查 git 修复历史；现状标注留待复核阶段。
- **批次 X2/3 覆盖声明**：本批次覆盖 `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/` 下 6 包——security（74f/6,090）、plan（44f/3,151 手写）、engine（47f/14,342）、runtime（34f/5,139，含 coordination/lock/recovery）、model（17f/285）、reliability（30f/3,909）。engine 的 ReAct 循环 / LlmCallCoordinator / AgentToolDispatcher / SessionLockRenewal / AgentSessionLifecycle / DefaultAgentEngine、runtime 的 DbSessionTakeoverLock / ScheduledRecoveryManager / Default{Orphan,SessionTimeout}Handler / DbDaemonCoordinator / InMemoryActorRuntime、reliability 的全部 checkpoint 后端 / SessionGoalTracker / DefaultWaitCoordinator / SisypheanSustainer 全文精读；security 其余 checker/matrix 纯函数类、model 生成 stub 按 C7 画像定向扫描。session / tool / team / message / compact / fencing / memory 等包不在本分区（归属批次 X3）。**降档口径：本分区为 agent 执行引擎长时运行形态，不适用启动期单线程降档**（模块内常驻线程均为运行期懒创建：lockRenewExecutor / agentExecutor / actor executor / recovery daemon，全部纳入挂死点与生命周期核查）。
- **批次 X3/3 覆盖声明（C7 三批次收官 + Phase 2 全维度收官）**：本批次覆盖 `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/` 下 team（55f/10,490，含 flow/scheduler 全文精读：TeamTaskFlowOrchestrator / MemberFanOutDispatcher / MemberAgentTaskStep / SpawnMemberAgentTaskStep / TeamTaskSchedulerDaemon / TaskDispatchCoordinator / DefaultMemberSpawner / 双 TeamManager / 双 TeamTaskStore）、tool（21f/3,781：DefaultBackgroundTaskRegistry / CallAgentExecutor / TeamExecuteFlowExecutor / TaskOutput/TaskStop 全文精读，其余 executor 定向扫描）、session（21f/4,112：FileBacked/DB/InMemory 三 SessionStore + AgentSession + SessionFileWriter + 增量 journal 路径全文精读）、message（18f/2,403：DBMessageService / DeferredAckMailbox / LocalAgentMessenger 全文精读）、memory（17f/1,528：双 provider + InMemoryAiMemoryStore 全文精读）及 16 小包 + 根（budget/compact/completion/conflict/contribution/guardrail(含 rule/)/hook/middleware/quota/recipe/repair/router/skill/talent/usage + 根，按 C7 画像定向扫描：状态集合、周期任务、时钟依赖、热路径序列化）。至此 C7 维度三批次（X1 nop-ai-core 全量、X2 agent engine/security/plan/runtime/model/reliability、X3 本批 team/tool/session/message/memory/16 小包 + 根）对 `nop-ai-core` + `nop-ai-agent` 两模块的覆盖收官，Phase 2 全维度（C1–C10 对应批次）在本文件维度上同步收官。基线使用：`_tmp/cra-baseline-nop-ai-core-nop-ai-agent.txt` C7a/C7b/C7c 节 ROOT: nop-ai-agent 分节中属于本批分区的行（compact/InMemorySpillStore SEQ、message/DeferredAckMailbox deliveryIdSeq、team scheduler/flow spawn 线程工厂与 shutdown、tool/DefaultBackgroundTaskRegistry idCounter、message/DBMessageService poller shutdown、session 写入与 journal 清理行）均已人工复现并纳入上文盘点。**降档口径：本分区为团队编排长程 + 消息/会话存储长程形态，不适用启动期单线程降档**（常驻线程均为运行期懒创建/外部注入：daemon 扫描任务挂在注入的 IScheduledExecutor、db-message-poller 单线程、spawn worker 懒创建固定池，全部纳入挂死点与生命周期核查）。

## 单调增长源清单（C7 步骤 1 盘点结论）

| # | 增长源 | 界 | 结论 |
|---|---|---|---|
| 1 | `ThresholdBreaker.entries`（携带失败记忆的条目永不收缩） | 无上界（按 distinct 失败 model key） | → C7-007 |
| 2 | `DefaultAiChatResponseCache` 磁盘缓存（默认 TTL=0 永不过期，仅读时惰性删） | 无上界（按 distinct 请求哈希） | → C7-008 |
| 3 | `DefaultChatLogger` 会话日志（每调用 request/response 两文件，无保留期） | 无上界（按调用量） | → C7-009 |
| 4 | `LlmConfigHelper.secretCache`（含空串负缓存） | 有界（按 provider 数）但负缓存永驻 | → C7-011 |
| 5 | `ProviderFailoverQueue.health` / `ChatServiceImpl.rateLimiters` / `EmbeddingServiceImpl.rateLimiters` / `LlmDialectFactory.DIALECTS` | 有界（provider/apiStyle 枚举量级） | 无缺陷 |
| 6 | `DBCheckpointManager.byWatermark/bySession/loadedSessions`（X2） | 无上界（按全部历史 checkpoint × 会话） | → C7-013 |
| 7 | `SessionGoalTracker.entries` / `DefaultWaitCoordinator.sessions` / `DefaultDenialLedger.counts`（内存）与 `ai_agent_denial` 行（X2） | 无上界（按 distinct 会话数；denial 行还按 denial 次数） | → C7-017 |
| 8 | `FileBackedCheckpointManager` per-session `journal.md` 磁盘追加（X2） | 无上界（按 checkpoint 数；终态仅清内存，盘上 journal 永存） | **已声明边界**（javadoc :80-81 自述「no rotation/cap…non-blocking follow-up」，不立发现） |
| 9 | `ai_agent_checkpoint` 表行（X2） | 无上界（INSERT-only，注释自述「rows are never deleted」） | **已声明边界**（DBCheckpointManager.java:374 自述），与 #6 的**内存缓存**无界相区分 |
| 10 | `PlanExecutionState.errors` / `agentExecutor` 线程（X2） | 执行期内有界（attempts × tasks）/ 无界池 | errors 有界不立发现；线程池面 → CL-064 |
| 11 | `DefaultBackgroundTaskRegistry.tasks`（X3） | 无上界（按历史后台任务总数，单条 ≤8KB tail） | → C7-027 |
| 12 | `LocalAgentMessenger.replySubscriptions` × `DBMessageService.consumers`（X3） | 无上界（按历史 distinct 发起会话数；每条目转化为常驻 DB 轮询） | → C7-026 |
| 13 | `FileBackedSessionStore`/`DBSessionStore`/`InMemorySessionStore` 的 sessions/persistedStates/sessionLocks（X3） | 无上界（按 distinct 会话；终态不释放，`store.remove` 零生产调用方） | → C7-028 |
| 14 | memory 双 provider 的 `stores` map（X3） | 无上界（按 distinct 会话；单 store 内 item 无 retention 已声明） | → C7-030 |
| 15 | `InMemorySpillStore.store` / `InSessionCompactionSnapshotArchive.store`（X3） | 会话存活期内无上界（溢出原文 / 每次压缩全量副本） | → C7-031 |
| 16 | `ai_agent_message` CONSUMED 行 / usage 表 / `ai_agent_team(_task)` 行（X3，DB append-only） | 无上界（运维保留期问题；message 索引 selectivity 劣化有行为影响） | → CL-071 |
| 17 | `tryAppend` 指纹 O(prefix) 重算（X3，CPU 非存储） | 每会话 O(S×N) 序列化；机制已声明、包络未声明 | → CL-068 |
| 18 | `ExecutionRecorder` / `TaskDispatchCoordinator.inFlightDispatches` / `AgentSession.seenInputKeys(1024)` / `pendingInjections(256)`（X3） | 有界（per-run GC / whenComplete 清理 / FIFO+上限 / 抛异常上限） | 无缺陷 |

## 挂死点清单（C7 步骤 2 盘点结论）

| # | 挂死点 | 是否有界 | 结论 |
|---|---|---|---|
| 1 | 流式聚合：SSE 中途静默/半关闭（headers 已达、body 停滞） | **无界**（JDK client body 无读超时） | → C7-001 |
| 2 | `checkRateLimit` 的 `tryAcquire(timeoutMs)` | 有界（默认 1000ms，fail-fast 抛 429） | 无缺陷（MA6.3-AR-6 已修形态） |
| 3 | `EmbeddingServiceImpl.scheduleRetry` 延迟等待 | 理论无界（受 C7-003 retryAfter 无上限影响） | → C7-003 |
| 4 | `AiCommand` 经 `FutureHelper` 的异步链 | 依赖上层（nop-ai-agent）超时，本模块无兜底 | 归入 C7-001 同族风险面 |
| 5 | 接管锁续期管道（X2）：`tryRenew` 抛 SQLException 时仅 WARN 无升级（SessionLockRenewal.java:74-77）；单线程 lockRenewExecutor 串行全部会话续期（:44-54） | 异常路径无界容忍 / 单线程容量上限 | → C7-012 / C7-019 |
| 6 | 心跳等待循环 `runHeartbeatToolPhase` while(true)（X2） | 有界：受 per-tool 机械 cap（cap≤0 退化为 Long.MAX_VALUE 无界）+ forceStop 兜底 | → C7-014 |
| 7 | 沙箱 reader 线程 `awaitReader` 后 interrupt（X2） | **无界**：阻塞在 `InputStream.read` 的线程对 interrupt 无响应，泄漏 | → C7-020 |
| 8 | 恢复扫描 `scanOnceSafe` 异常仅 WARN（X2） | 每 60s 重试（有节奏，可观测） | 无缺陷（对照 F5：失败可见，非静默挂死）；Error 面同族复查 → C7-029 |
| 9 | team-flow 成员执行等待（X3）：`MemberAgentTaskStep` 无 per-member 期限；orchestrator overall deadline 只 `taskRt.cancel` 不取消成员会话 | **无界**（成员挂死时）；僵尸执行 + 重claim 双执行 | → C7-022 |
| 10 | spawn 路径 bounded get（X3）：`DefaultMemberSpawner` 超时后 `spawned-<uuid>` 会话无人取消 | worker 有界 / 引擎侧执行**无界** | → C7-023 |
| 11 | call-agent async 信箱路径等待（X3）：caller orTimeout 有界，callee 子代理执行无取消协调 | caller 有界 / callee 继续消耗 | → C7-024 |
| 12 | DBMessageService 毒消息重投循环（X3）：claim→抛→releaseClaim→PENDING，50ms 级永续 | **不收敛**（无尝试上限/退避/DLQ；≥maxBatch 毒消息时队列完全停摆） | → C7-025 |
| 13 | `scheduleWithFixedDelay` Error 逃逸（X3）：scanOnceSafe/pollAllTopics/sweep 只捕 Exception 族 | 命中 Error 即**永久静默停摆**（F5） | → C7-029 |

## 发现

### [C7-001] 流式聚合路径无全程期限与中途静默检测——SSE 挂死使 future 永不完成且不可见

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/ChatServiceImpl.java:391-440`（aggregateStreamToResponse）、`:202-233`（callStream 订阅链）、`:272`（唯一超时设置点）
- **证据片段**:
  ```java
  // ChatServiceImpl.java:272 —— 唯一的超时注入点（非流式/流式共用）
  httpRequest.setTimeout(CFG_AI_SERVICE_READ_TIMEOUT.get()); // 默认 60000
  // ChatServiceImpl.java:400-437 —— 聚合 subscriber：无任何 deadline/watchdog
  callStream(request, cancelToken).subscribe(new Flow.Subscriber<ChatStreamChunk>() {
      public void onNext(ChatStreamChunk item) { aggregator.addChunk(item); }
      public void onError(Throwable throwable) { ... future.completeExceptionally(throwable); }
      public void onComplete() { ... future.complete(response); }
  });
  ```
- **严重程度**: P1
- **并发场景**: HTTP worker / 客户端 executor 线程（JDK client `GlobalExecutors.globalWorker` 或 client.executor）执行 SSE body 读取；调用方线程在聚合 future 上等待。挂死时每条流永久占用一个 reader 线程 + 一个聚合链。
- **触发时序**: T1 调用方进入 `callAsync`（默认 stream=true）→ `aggregateStreamToResponse` 订阅；T2 provider 返回 200 + `text/event-stream` 响应头后连接静默（服务端 hang、中间代理黑洞、长工具执行后进程假死）；JDK client 路径 `HttpRequest.Builder.timeout` 仅覆盖到响应头（`JdkHttpClient.java:218-219` 只对 `sendAsync` 头阶段生效），`ServerEventPublisher.parseEvents` 的 `reader.readLine()`（`ServerEventPublisher.java:128-135`）在无 socker 读超时下**永久阻塞** → onNext/onError/onComplete 三者均不发生 → 聚合 future 永不完成。后果：调用方（agent 引擎等待该 future）永久挂起，且无任何日志/指标可见（F5：「不产出也不报错」无看门狗）。
- **现状**: `CFG_AI_SERVICE_READ_TIMEOUT` 单值同时用于非流式与流式；流式路径在本模块内没有任何 inactivity deadline、总期限或 stall 检测，行为完全取决于底层 client 实现（Apache client 的 responseTimeout 提供非活动上界，JDK client 不覆盖 body）。
- **风险**: 长时运行下 provider/代理中途静默是「何时发生」而非「是否发生」；每次发生泄漏一个 executor 线程与一条挂起调用，累积后耗尽线程池并使对应 agent 会话永久卡死；健康检测（心跳/stall）对该形态完全不可见。
- **建议**: 在 `aggregateStreamToResponse`/`callStream` 增加模块级 watchdog：(1) future 级总期限（复用 `CoreMetrics.timeoutToExpireTime` + `orTimeout` 语义）；(2) chunk 间 inactivity 上界（独立于 `CFG_AI_SERVICE_READ_TIMEOUT` 的 `nop.ai.service.stream-idle-timeout`），超时经 `subscription.cancel()` + `closeExceptionally` 走既有错误路径；优先复用 `io.nop.commons.concurrent` 的 CycleSynchronizer/ScheduledExecutor 设施而非自旋。
- **失效面枚举**: (a) `callAsync` 非流式路径（`:147-175`）——JDK `ofByteArray` 下 sendAsync future 覆盖整个交换，有 client 兜底，**已覆盖**；(b) `callStream` 公开 API（外部直接订阅者）——与聚合路径同裸露，**同族未覆盖**；(c) `EmbeddingServiceImpl.embedAllAsync`（`:263` fetchAsync + `:288` setTimeout）——非流式，同 (a)，**已覆盖**；(d) Apache client 流式路径——responseTimeout 提供非活动上界（client 侧，非本模块），**部分覆盖**。
- **信心水平**: 确定
- **误报排除**: 不属于「有界声明存在的队列/池」——本路径无任何声明上界；也不是「原语使用非问题」：缺陷是聚合层缺失期限语义，而非 Flow 原语误用。
- **复核状态**: 已保留(P1)｜新发现——回码核验成立：aggregateStreamToResponse 订阅链无任何 deadline/watchdog 直核（:400-437）；缺省 HTTP client 链路补证——`http-client-jdk.beans.xml:10` `nopRawHttpClient ioc:default=true` → JdkHttpClient，`builder.timeout` 仅覆盖响应头到达（JdkHttpClient.java:218-221），SSE body `readLine()` 无 socket 读超时（ServerEventPublisher.java:138-140 `while ((line = reader.readLine()) != null)`）——挂死前提在缺省装配成立，P1 维持（校准档：挂死不可见 + 每次泄漏 reader 线程与挂起调用）。同链指针（复核登记）：C3-001(P1)/C3-002(P1)/C4-002(P1) 为同链 publisher 生命周期/背压面，本条为聚合层期限与可检测性面，失效层不同不并案，修复批次协同

### [C7-002] 流式中途断连（干净半关闭）被 onComplete 误判为成功完成——截断内容以成功响应交付

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/ChatServiceImpl.java:427-436`（onComplete 处理）、`:483-566`（StreamAggregator，finishReason 仅记录不校验）
- **证据片段**:
  ```java
  // ChatServiceImpl.java:427-436
  public void onComplete() {
      ChatResponse response = aggregator.toResponse();   // finishReason 可能为 null
      response.setRequestId(request.getRequestId());
      response.setResponseTime(CoreMetrics.currentTimeMillis());
      if (logMessage) { chatLogger.logResponse(request, response); }
      future.complete(response);                          // 截断流照常 complete
  }
  ```
- **严重程度**: P1
- **并发场景**: 单调用链（HTTP reader 线程 → 聚合 subscriber → future 完成回调），非竞态问题，而是失败路径语义缺失（F2「半个成功」）。
- **触发时序**: T1 流式调用进行中，服务端/代理在发出部分 chunk 后干净关闭连接（TCP FIN，无 `[DONE]`、无 finish_reason chunk）；T2 `ServerEventPublisher.parseEvents` 的 `readLine()` 返回 null 正常退出 → `onComplete()` → `toResponse()` 产出 `finishReason == null` 的 ChatResponse → `future.complete(response)`。交叠后果：截断回复被类型层认定为**成功响应**，上层 `hasStreamedContent` 流式守卫（`StandardRetryPolicy.java:119-121`）永不触发（无异常），无重试/修复路径，截断内容直接交付。
- **现状**: 聚合器记录 `finishReason`（`:499-501`）但不校验「是否收到终止信号」；SSE 协议的完成判据（终止 chunk / finish_reason）在 complete 前被丢弃。
- **风险**: 长时运行下网络抖动导致的静默截断被吞成正常响应：agent 层拿到无 finishReason 的截断文本继续推理（可能基于残缺工具调用参数），数据完整性违约且无告警；重试体系对该失效面完全失效。
- **建议**: `onComplete` 中校验 `aggregator` 是否收到 `StreamItemPhase.DONE`/非空 `finishReason`（OpenAI/Anthropic/Gemini/Ollama/Responses 五方言均产出终止 chunk，信号已在流中，只差消费）；缺失时按传输级错误 `completeExceptionally`（带截断字节数诊断），或至少在 ChatResponse 上置 `invalid` 标记交上层裁决（fail-loud，符合 Minimum Rules #24）。
- **失效面枚举**: (a) `aggregateStreamToResponse`——**缺陷点**；(b) `callStream` 直接订阅者（nop-ai-agent 消费公开 API）——同一 onComplete 语义，**同族同病**；(c) 非流式 `callAsync`——body 完整接收后才 parse，无此面，**不适用**；(d) 五方言终止信号产出点：`OpenAiDialect.java:297-301`、`AnthropicDialect.java:630/638`、`GeminiDialect.java:456-459`、`OllamaDialect.java:367-370`、`ResponsesDialect.java:576`——信号齐备，聚合端未消费。
- **信心水平**: 确定
- **误报排除**: 非「已声明边界」：模块 javadoc（`aggregateStreamToResponse` 注释）只声明「已流出内容后的错误仍走异常」，未声明「无终止信号的干净关闭按成功处理」；这是语义空洞而非声明行为。
- **复核状态**: 已保留(P1)｜新发现——回码核验成立：onComplete 直接 `future.complete(response)` 无 finishReason/终止信号校验（:427-436），StreamAggregator.toResponse 仅 set 不判（:531-535 直核）；方言终止信号在流中产出直核（OpenAiDialect.java:297-301 `finishReason != null → Phase.DONE`）——信号齐备、聚合端未消费的论证成立。F2「半个成功」P1 档直录（校准明文）；相邻面 C3-032（带内错误路由）为不同失效层，修复同批协同

### [C7-003] Retry-After 四源解析无上限——不可信服务端输入可把重试推迟数小时/天

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/dialect/AbstractLlmDialect.java:181-214`（resolveRetryAfterMs）、`:290-308`（parseRetryAfterHeader）、`:223-260`（parseRetryAfterText）；`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/reliability/StandardRetryPolicy.java:145-165, 206-213`；`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/EmbeddingServiceImpl.java:353-354, 443-445`
- **证据片段**:
  ```java
  // AbstractLlmDialect.java:294-296 —— 秒值直乘 1000，无上限
  Long seconds = ConvertHelper.toLong(value, null);
  if (seconds != null) { return seconds * 1000L; }
  // StandardRetryPolicy.java:206-212 —— retryAfterMs 作 floor，显式不受 maxDelayMs 约束
  private long computeRateLimitedDelay(long retryAfterMs, int attempt) {
      long jitterCap = backoffCap(attempt);
      ... return retryAfterMs + jitter;   // retryAfterMs 原样透传
  }
  ```
- **严重程度**: P2
- **并发场景**: HTTP 响应回调线程解析错误响应 → 重试决策线程 →（embedding 路径）`nop-ai-embedding` 调度器线程持有延迟任务。长时运行中每次 429 都信任服务端给出的等待时长。
- **触发时序**: T1 provider（或中间代理/劫持者）返回 `retry-after: 999999999`（≈31.7 年）或 HTTP-date 远期时间（`:299-303` 直接取 `target - now`），或 body `retryAfterPath` 字段超大毫秒值，或错误文案 "try again in 500 minutes"（`:252` → 8.3h）；T2 `EmbeddingServiceImpl.handleFailure`（`:353`）仅做 `Math.max(0, delayMs)` 负值防护后 `scheduleRetry(delayMs, ...)`。交叠后果：延迟任务携带 `texts`/`EmbedCall` 常驻调度器队列数小时～数年，调用方 future 同期悬空（对外表现为调用「挂起」而非失败），配额早已恢复也不重试。
- **现状**: `maxDelayMs`/`OVERLOADED_MAX_DELAY_MS` 对自算曲线有效，但对 retryAfterMs 是**显式无上限**（javadoc 声明「retryAfterMs 可超过 maxDelay」——上限例外有文档，绝对 sanity cap 缺失无文档）；解析端对负值/0 有滤（`parseRetryAfterText` 的 `millis > 0`），对超大值无滤。
- **风险**: 单条恶意/故障 429 即可制造小时级不可重试悬空调用；批量发生时调度器队列被超长延迟任务占据（`setRemoveOnCancelPolicy` 只作用于取消，不作用于此），长时运行内存与调用悬挂面随攻击面线性放大。
- **建议**: 解析端归一时加 sanity 上限（如 10 分钟，超出取上限并 WARN 记录原始值），或策略端 `Math.min(retryAfterMs, MAX_RETRY_AFTER_CAP_MS)`；建议复用既有 `StandardRetryPolicy.OVERLOADED_MAX_DELAY_MS` 同级常量并写入口径文档。
- **失效面枚举**: 四个解析源逐一登记：(a) `retry-after-ms` 头（`findHeaderMs` `:278-285`）——无上限，**同病**；(b) `retry-after` 头秒值/HTTP-date（`:290-308`）——无上限，**同病**；(c) body `retryAfterPath` 字段（`:194-204`）——无上限，**同病**；(d) 错误消息文本（`:223-260`）——无上限（分钟乘子放大），**同病**。消费端：(e) `StandardRetryPolicy` RATE_LIMITED/OVERLOADED/TRANSIENT-floor 三分支（`:145-165`）——全部原样透传，**同病**；(f) `EmbeddingServiceImpl.scheduleRetry`（`:443-445`）——**同病**；(g) nop-ai-agent `LlmCallCoordinator` 重试循环——批外，登记给对应批次。
- **信心水平**: 确定
- **误报排除**: 「retryAfterMs 不受 maxDelayMs 约束」是 owner 设计显式裁定（javadoc 引设计 §3.7 / Plan 2304），本发现不否定该裁定，报的是**绝对上限缺失**这一未声明面，二者不冲突。
- **复核状态**: 已保留(P2)｜新发现——回码核验成立：四源解析均无绝对上限（resolveRetryAfterMs 四分支 :181-214 + parseRetryAfterHeader 秒值直乘 :294-296 + parseRetryAfterText 分钟乘子 :252）；消费端 floor 原样透传直核（computeRateLimitedDelay `return retryAfterMs + jitter`、computeOverloadedDelay `Math.max(delay, retryAfterMs)`、TRANSIENT `Math.max(delay, retryAfterMs)`——StandardRetryPolicy.java:206-213/:190-193/:168-170）；「retryAfter 不受 maxDelay 约束」javadoc 已声明（设计 §3.7/Plan 2304），绝对 sanity cap 缺失未声明——初审未否定 floor 裁定的口径正确。P2 维持（需不可信服务端输入叠加，后果为延迟悬挂非数据损失）

### [C7-004] 熔断与 provider 冷却的期限判定基于墙钟——时钟回拨推迟/冻结故障恢复

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/reliability/ThresholdBreaker.java:173,185,205,276`；`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/reliability/ProviderFailoverQueue.java:45,112`
- **证据片段**:
  ```java
  // ThresholdBreaker.java:185 —— OPEN→HALF_OPEN 惰性转移
  if (now - entry.openedAt >= cooldownMs) {   // now = CoreMetrics.currentTimeMillis()
  // ProviderFailoverQueue.java:112 —— 冷却判定
  return (nowMs.getAsLong() - entry.lastFailureAt) >= cooldownMs;  // 默认 CoreMetrics::currentTimeMillis
  ```
- **严重程度**: P2
- **并发场景**: 多 HTTP worker 线程并发调用 `allowCall`/`isProviderAvailable`；时间源为 `CoreMetrics.currentTimeMillis()`，其默认实现即 `System.currentTimeMillis()`（`nop-api-core/CoreMetrics.java:21-25`，已核实）——墙钟，非单调钟。
- **触发时序**: T1 某 model-key 在 T0 熔断（`openedAt=T0`）；T2 运维/NTP 将系统时钟回拨 D（D > cooldownMs=60s 的人工纠时或 VM 迁移常见）；T3 之后所有 `allowCall` 中 `now - openedAt` 为负/过小 → HALF_OPEN 转移被无限推迟 → 对该 model 的所有调用被拒直至墙钟重新爬过 `openedAt + cooldown`（拒绝窗口 = D + cooldown，可达小时级）。同序列作用于 `ProviderFailoverQueue`：冷却中的 provider 保持「不可用」，failover 游走器持续跳过刚恢复的 provider（`ProviderFailoverChain.nextAvailable` 的 skip 逻辑）。反向（时钟前跳）则瞬时放行探测，削弱冷却语义。
- **现状**: `ProviderFailoverQueue` javadoc 自述 2026-09-12 已将 `System.currentTimeMillis()` 统一为 `CoreMetrics`——该统一只达成可测试性（可注入 seam），两个类的时间源仍同为墙钟；`CoreMetrics.nanoTime()` 单调源存在但未被用于期限判定。
- **风险**: 长时运行服务必然经历时钟步进；回拨窗口内熔断恢复与 failover 去重同时失效（可恢复的 provider 被持续拒绝/跳过），可用性损失与故障时长不成比例且难排查（表现为「时间对齐后自愈」）。
- **建议**: 期限差值改用单调源 `CoreMetrics.nanoTime()`（deadline 存 nanoTime 差值），或两类的 `nowMs` seam 默认改指单调实现；保留墙钟仅用于日志/展示。改动点收敛（两个类的取时行），回归面小。
- **失效面枚举**: (a) `ThresholdBreaker` OPEN 冷却（`:185`）——**同病**；(b) `ThresholdBreaker` HALF_OPEN probe 逃逸（`:205`）——**同病**（回拨同样推迟逃逸）；(c) `ProviderFailoverQueue` 冷却判定（`:112`）——**同病**；(d) `DefaultAiChatResponseCache.isExpired`（`:64-67`）——与文件 mtime 比较，mtime 本身是墙钟，改单调无意义，**不适用**（豁免）；(e) `EmbeddingServiceImpl` 重试延迟——相对 delay 经调度器执行，无期限差值比较，**不适用**。
- **信心水平**: 确定（机制）/ 很可能（实际回拨窗口内的事故频率）
- **误报排除**: 不是「低频路径粗粒度锁」类优雅性问题：这是 C7 步骤 5 明确命中的时钟单调性缺陷，且有具体可用性后果链；「统一 CoreMetrics」不构成误报豁免，因为默认时钟即墙钟。
- **复核状态**: 已保留(P2)｜新发现——回码核验成立：CoreMetrics 默认实现即 `System.currentTimeMillis()`（nop-api-core CoreMetrics.java:23-24），`nanoTime()` 单调源存在未用于期限判定（:27-28,:72）；ThresholdBreaker compute 内 `now = CoreMetrics.currentTimeMillis()` 直核（:173/:186）；ProviderFailoverQueue 默认 `CoreMetrics::currentTimeMillis` 直核（:45）——「统一 CoreMetrics 只达成可测试性」定性准确。跨单元墙钟敏感族对照（nop-stream-core-runtime C7-010/018、nop-wf-nop-task C7-006、nop-retry-nop-tcc C7-007/011、nop-job C7-008）：载体各异、模块不同，不重复立项；判级与 WI 2.3 C8-005 先例一致（触发需环境事件叠加 → P2）

### [C7-005] ThresholdBreaker probe 超时逃逸无身份令牌——30s 默认值下慢 LLM 调用常态性触发陈旧结果污染状态机

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/reliability/ThresholdBreaker.java:192-213`（逃逸逻辑）、`:98`（DEFAULT_PROBE_TIMEOUT_MS=30_000）、`:248-265, 287-298`（recordSuccess/recordFailure 无 probe 身份校验）
- **证据片段**:
  ```java
  // ThresholdBreaker.java:205-211 —— 逃逸：无旧 probe 是否仍存活的判据
  if (probeTimeoutMs > 0 && now - entry.probeStartedAt >= probeTimeoutMs) {
      LOG.warn("... re-taking the probe slot ...");
      entry.probeStartedAt = now;      // 只刷新时间戳，旧 probe 的未来回执无法识别
      holder[0] = true;
  }
  ```
- **严重程度**: P2
- **并发场景**: 多个并发调用竞争同一 model-key 的 HALF_OPEN probe 槽位；LLM 调用常态耗时 30s–120s，而 `DEFAULT_PROBE_TIMEOUT_MS=30s`。
- **触发时序**: T1 probe-A（慢但存活的 LLM 调用，55s）进入 HALF_OPEN 持槽；T2 30s 后任一并发 caller B 走逃逸分支夺槽成为 probe-B → 同一后端并发双 probe（probe 排他性破，「exactly one probe」不变量失效，恰在后端变慢时加倍施压）；T3a probe-A 55s 后失败回执 `recordFailure` → 在 HALF_OPEN 分支被判为「当前 probe 失败」→ 转 OPEN 且 `probeInFlight=false`，此时 probe-B 仍在飞 → probe-B 的最终回执落入 OPEN 防御分支被丢弃 → 模型已恢复仍需再等一个完整 cooldown 才能重新探测；T3b 变体：probe-A 成功回执 → CLOSED，与仍在飞的 probe-B 并存，B 的失败回执落入 CLOSED 分支计连续失败——状态机语义与物理 probe 完全错位。
- **现状**: 逃逸机制本身是对「probe 卡死永久楔死 HALF_OPEN」的必要修复（javadoc 已声明），但 (1) 无 probe token 使陈旧回执不可鉴别；(2) 30s 默认值把「卡死」判定错配到 LLM 常态延迟段，使逃逸从兜底变为常态路径；(3) javadoc 未声明陈旧回执的归属语义。
- **风险**: 长时运行下慢后端恢复被系统性推迟（每次需多耗一个 cooldown），且恢复探测期并发压力翻倍；状态查询（`getState`）对外呈现与真实在飞调用不符的状态，误导运维。
- **建议**: (1) 引入 per-probe 世代号（`probeGeneration++`），回执携带世代号，陈旧回执静默丢弃；(2) 默认 probeTimeout 与 LLM 调用超时对齐（≥ `CFG_AI_SERVICE_READ_TIMEOUT` 量级，如 90s）或可配；(3) javadoc 补陈旧回执语义。
- **失效面枚举**: (a) `ThresholdBreaker`——唯一功能熔断实现，**缺陷点**；(b) `AlwaysClosed` pass-through——无状态机，**不适用**；(c) `recordSuccess` HALF_OPEN 分支（`:248-255`）与 `recordFailure` HALF_OPEN 分支（`:287-293`）——两处均无世代校验，**同病**；(d) `ProviderFailoverQueue`——无 probe 概念（纯冷却），**不适用**。
- **信心水平**: 确定（状态机语义）/ 很可能（30s 与 LLM 延迟错配在生产负载的触发频率）
- **误报排除**: 非对「FutureTask/原语使用」的泛化报备：缺陷是逃逸机制与回执协议的组合空洞，有具体 T1/T2/T3 交错与状态错位后果；逃逸机制存在本身不构成「已声明边界」，因其未声明陈旧回执行为。
- **复核状态**: 已保留(P2)｜新发现——回码核验成立：probe 逃逸仅刷新 `probeStartedAt` 无世代令牌（:205-211 直核——注释自证「re-taking the probe slot」无旧 probe 存活判据）；recordSuccess/recordFailure 的 HALF_OPEN 分支均无 probe 身份校验（:248-265/:287-298 直核）；DEFAULT_PROBE_TIMEOUT_MS=30_000 直核（:98）——30s 与 LLM 常态延迟错配使逃逸从兜底变常态的论证成立。P2 维持

### [C7-006] AiCommand 重试无退避 + 全模块重试路径绕过入口限流器——故障同步失败时的无间隔重试风暴

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/command/AiCommand.java:303-307`（retryNTimes 无延迟）；`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/ChatServiceImpl.java:131,185`（checkRateLimit 仅入口一次）；`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/EmbeddingServiceImpl.java:171,257-281`（dispatch 不复查限流）
- **证据片段**:
  ```java
  // AiCommand.java:303-307 —— RetryHelper.retryNTimes 内部零延迟（nop-kernel 已核实）
  CompletionStage<ChatExchange> promise = RetryHelper.retryNTimes((index) -> {
              adjustTemperature(options, index);
              return executeOnceAsync(request.copy(), options, scope, cancelToken);
          }, ChatExchange::isValid, retryTimesPerRequest);   // 默认 3
  ```
- **严重程度**: P2
- **并发场景**: 大量 `ai:Execute`/`ai:TaskStep` 并发执行；provider 过载或非确定性输出导致 `ChatExchange::isValid` 批量不通过时，所有在飞任务同时发起 3 连发立即重试。
- **触发时序**: T1 provider 短暂过载返回无效结果；T2 N 个并发 AiCommand 各自进入 `retryNTimes`，即刻（0 退避）重发第 2、3 次（`RetryHelper._retryNTimes` 已核实无 sleep）；T3 若 provider 限流，重试请求绕过 `checkRateLimit`（仅在 `embedAllAsync`/`callAsync` 入口执行一次，重试 dispatch 不再过 limiter）直接打到已过载后端。交叠后果：N×3 的瞬时重放波峰与原始负载叠加，无任何抖动间隔（对比 `StandardRetryPolicy` 的 full-jitter 设计意图）。
- **现状**: 同模块内 `StandardRetryPolicy` 已实现退避+上限的规范形态，但 AiCommand 路径未复用（用了 nop-kernel 的零退避 `retryNTimes`）；两条路径的重试语义不一致且无文档裁定。
- **风险**: 重试风暴放大下游故障（C7 步骤 3 命中项）：过载 → 立即重试 → 更过载的正反馈；上限（3 次）使风暴有界但不使其平缓，批处理任务高峰期（多 TaskStep 并发）可造成 provider 侧连锁 429。
- **建议**: AiCommand 重试改为经 `StandardRetryPolicy`（复用 full-jitter + retryAfter floor），或给 `retryNTimes` 传入递增延迟；重试 dispatch 前复查 limiter（`tryAcquire` fail-fast 已是现成设施）。
- **失效面枚举**: (a) `AiCommand.executeAsync` retryNTimes——无退避，**缺陷点**；(b) `EmbeddingServiceImpl` 重试链——有 full-jitter 退避但绕过 limiter，**半同病**；(c) `ChatServiceImpl`——自身无重试（重试在上层 coordinator），入口 limiter 单次检查，**同病面 (b)**；(d) `RetryHelper.retryNTimes` 其他使用点——grep 全模块仅 AiCommand 一处，**无其他兄弟**；(e) nop-ai-agent `LlmCallCoordinator` 重试循环——批外，登记对应批次核对其退避与 limiter 交互。
- **信心水平**: 确定（零退避事实）/ 很可能（风暴后果量级）
- **误报排除**: 非「平台基础设施自身不重审」的越界：`RetryHelper` 属 nop-kernel 不报，报的是 nop-ai-core **使用方式**（选了零退避原语承载热路径重试）；也非「有界即豁免」——校准条款豁免的是配置了上限的缓存/队列，不豁免缺失退避的重试策略。
- **复核状态**: 已保留(P2)｜新发现——回码核验成立：`RetryHelper.retryNTimes → _retryNTimes` 零延迟直核（nop-commons RetryHelper.java:75-88，递归直调无 sleep/delay 分支）；AiCommand :303-307 调用点直核；checkRateLimit 仅 callAsync/embedAllAsync 入口单次直核（:131/:185）——重试 dispatch 绕过 limiter 成立。P2 维持（3 次上限使风暴有界；未达「负载必然触发的死锁/溢出」P1 档）

### [C7-007] ThresholdBreaker 失败记忆条目永不收缩——无效/任意 model key 驱动表单调增长

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/reliability/ThresholdBreaker.java:103`（entries map）、`:70-79`（收缩语义 javadoc）、`:176-180`（仅 pristine 收缩）、`:274-286`（失败记忆条目保留）
- **证据片段**:
  ```java
  // ThresholdBreaker.java:70-74 —— 收缩只针对 pristine 条目（自述）
  // a <i>pristine</i> entry — state CLOSED, zero consecutive failures, no probe
  // in flight — is semantically identical to an absent entry ... so it is dropped
  // An entry that carries failure memory (CLOSED with failures > 0) ... is never dropped.
  private final ConcurrentMap<String, BreakerEntry> entries = new ConcurrentHashMap<>();
  ```
- **严重程度**: P2（model 名用户可控的部署）/ P3（model 名仅来自配置）
- **并发场景**: 多 HTTP worker 并发以不同 `provider:model` key 调用 `recordFailure`/`allowCall`；key 由 `ModelKeys.buildModelKey`（`ChatOptions.provider + ":" + model`）拼接，model 名可来自请求方 ChatOptions。
- **触发时序**: T1 请求方（或攻击者）以 N 个不同无效 model 名发起调用（每个失败 1–2 次即停止，或失败至 OPEN 后不再访问）；T2 每次 `recordFailure` 为新 key 建 `BreakerEntry` 且 `consecutiveFailures>0` → 收缩分支（`removeIfPristine`）永不命中；T3 这些条目无 TTL、无清扫、无失效回调 → 表随 distinct 失败 key 数单调增长（每条目约几十字节 + map 开销，百万级 key 达百 MB 量级）。OPEN 条目还随 cooldown→HALF_OPEN→OPEN 周期性醒转（每次 `allowCall` 都会 compute）。
- **现状**: javadoc 明确声称「the table only holds entries that remember something, so gateways routing to arbitrary model keys do not grow the table without bound」——该声明对**曾经失败过的 key** 不成立：失败记忆恰是保留条件，声明与实现存在缺口（自愈语义缺失，C7 步骤 4）。
- **风险**: 长时运行 + 任意 key 路由（多租户/agent 自由选型场景）下内存缓慢累积无回收路径；javadoc 的无界增长豁免声明使该面在评审中不可见。
- **建议**: 为 OPEN/CLOSED-with-failures 条目加 TTL（如 lastTransitionTime + 24h，惰性清除即可，复用 `allowCall` 的 compute 顺带过期判定）；或对 distinct key 数设上限 + LRU 驱逐（`GlobalCacheRegistry`/`CacheConfig` 平台设施）。
- **失效面枚举**: (a) `ThresholdBreaker.entries`——**缺陷点**；(b) `ProviderFailoverQueue.health`（`:39`）——同型 map，但 key 来自 failover 配置（非用户输入），有界，**不适用**；(c) `ChatServiceImpl.rateLimiters`（`:72`）/`EmbeddingServiceImpl.rateLimiters`（`:91`）——key 来自 llm.xml 配置 provider，有界，**不适用**；(d) `LlmDialectFactory.DIALECTS`——枚举量级，**不适用**。
- **信心水平**: 很可能（增长机制确定；危害取决于部署是否暴露任意 model 名）
- **误报排除**: 非「有界声明存在的缓存」误报豁免对象——恰恰因为 javadoc 已声明无界增长豁免而实现不满足该声明前提（只对 pristine 成立）；并发守卫无关，纯 F4/F7 长时运行增长面。
- **复核状态**: 已保留(P2，条件面)｜新发现——回码核验成立：recordFailure CLOSED 分支注释自证「Failure memory must survive → entry stays」永不删除（:274-286）；OPEN 条目同样永久保留（:176-180 仅 pristine 收缩直核）——javadoc 无界增长豁免声明只对 pristine 成立的缺口定性准确。条件化判级维持初审双档：model 名经 ChatOptions 可达请求方 → P2；封闭配置部署 → P3。与 F4 跨单元账本族（WI 1.1 C1-006/WI 1.6 C7-001/WI 1.7 C7-003）载体不同，不重复立项

### [C7-008] 响应缓存默认永不过期且无主动清扫，损坏条目不自愈——磁盘单调增长 + 毒条目永久损耗

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/persist/DefaultAiChatResponseCache.java:44-73`；`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/AiCoreConfigs.java:39-42`；`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/command/AiCommand.java:296-301`；`nop-kernel/nop-core/.../ResourceHelper.java:718-734`（writeText 非原子，证据）
- **证据片段**:
  ```java
  // AiCoreConfigs.java:40-42 —— 默认 0 = 永不过期，惰性过期不主动清扫（自述）
  IConfigReference<Long> CFG_AI_SERVICE_CACHE_TTL =
          varRef(s_loc, "nop.ai.service.cache-ttl", Long.class, 0L);
  // AiCommand.java:297-300 —— 损坏条目：吞异常仅 INFO（无 cause），不删除毒条目
  } catch (Exception e) {
      LOG.info("nop.ai.load-cache-fail:promptName={}", promptName);
  }
  ```
- **严重程度**: P3
- **并发场景**: 多 worker 并发对同一请求哈希落缓存（`save` 经 `ResourceHelper.writeText` 直写，无 tmp+rename 原子性）→ 并发写/进程中断可产生半截 JSON；后续每次读命中该条目均解析失败。
- **触发时序**: T1 两个并发同参请求同时 `saveCachedResponse` 同一 cache path，或进程在 writeText 中途崩溃；T2 文件截断；T3 之后所有同参请求走 `loadCachedResponse` → `parseBeanFromText` 抛 → AiCommand 吞为 INFO → 全量 LLM 重调；毒条目**永不删除**（删除只发生在 TTL>0 且过期的 `:53`），TTL=0 默认下连过期删除都不会发生。
- **现状**: 「0=永不过期」与「不主动清扫」是配置描述中已声明的兼容默认（属声明边界）；但「损坏条目无删除/自愈路径」与「异常仅 INFO 无原因字段」未声明，且与并发写非原子组合后毒化是现实可达的。
- **风险**: 长时运行磁盘单调增长（无回收路径，即便 TTL>0 未再读的条目也永存）；毒条目使命中请求永久付出全量 LLM 成本且排障信息只有一行无 cause 的 INFO。
- **建议**: (1) `loadCachedResponse` 解析失败时删除毒条目（delete-on-corrupt）并 WARN 带 cause；(2) save 改 tmp+`Files.move(ATOMIC_MOVE)`（对照 nop-ai-agent `SessionFileWriter.java:117-129` 已有形态）；(3) 评估默认 TTL 或加惰性清扫（load miss 时顺带清扫同前缀过期项）。
- **失效面枚举**: (a) `DefaultAiChatResponseCache`（本文件）——**缺陷点**；(b) `DefaultChatLogger` 日志落盘——增长面独立成条（C7-009），**另列**；(c) nop-ai-agent `SessionFileWriter`/`CheckpointSnapshotWriter`——已有 tmp+rename 形态（基线 C7b 证据），**已覆盖**，反证 (a) 是同族漏改（F3）。
- **信心水平**: 确定
- **误报排除**: 未把「TTL=0 默认」本身报为缺陷（那是声明默认）；报的是损坏条目无自愈 + 写非原子这两个未声明面。
- **复核状态**: 已保留(P3)｜新发现——回码核验成立：「TTL=0 永不过期 + 惰性过期不主动清扫」为 @Description 显式声明兼容默认（AiCoreConfigs.java:39-42 直核）——声明边界成立，初审不将其立项的口径正确；损坏条目无删除/自愈 + 吞异常仅 INFO 无 cause 直核（AiCommand :296-301，对照 CL-060）。P3 维持（毒条目后果 = 缓存 miss 全量重调，成本面非正确性面）。同站点分账核对：C5-002(P2) 为磁盘无界/容量面，与本条（自愈缺失面）分账合理，7.1 修复同文件协同

### [C7-009] 会话聊天日志按调用双文件落盘无保留期——磁盘单调增长无清理/归档路径

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/DefaultChatLogger.java:47-58, 67-78`；`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/ChatLogHelper.java:29-36`
- **证据片段**:
  ```java
  // ChatLogHelper.java:31-35 —— 日期分区 + 会话目录 + 每调用两文件
  LocalDate date = CoreMetrics.currentDate();
  String today = date.getYear() + "/" + ... ;
  String fileName = today + '/' + sessionId + '/'
          + request.getRequestTime() + '-' + request.getRetryTimes() + '-' + request.getRequestId() + postfix;
  ```
- **严重程度**: P3
- **并发场景**: 每次启用 `logMessage` 的 LLM 调用写 `-request.yaml` + `-response.yaml` 两文件（含全文内容）；多 worker 并发追加不同文件（无争用）。
- **触发时序**: T1 高频调用（agent 会话 × 天/周）；T2 `{logDir}/{year}/{MM-dd}/{sessionId}/` 下文件数与体积随调用量线性增长；T3 模块内无任何 TTL/轮转/归档代码路径（grep 全模块无 cleanup 调用点），增长只受磁盘容量约束。
- **现状**: F4 族（append-only 持久化无清理）；日期分区提供按天删除的**可能性**（目录即分区键），但删除动作不存在于代码，完全依赖外部运维脚本（无文档声明该依赖）。
- **风险**: 长时运行默认配置下 `/nop/ai/log` 无界增长直至写满盘（连带冲击同盘的 VFS/缓存写）；且日志含完整对话内容，过期数据无生命周期也是合规面。
- **建议**: 增加保留期配置（如 `nop.ai.service.log-retention-days`）+ 启动/定时惰性清扫（按日期目录名删除超期分区，复用 `LocalFileOperator.delete` 或 `FileHelper.deleteAll`）；或文档明示外部轮转依赖。
- **失效面枚举**: (a) `DefaultChatLogger` request/response 落盘——**缺陷点**；(b) `DefaultAiChatResponseCache`——同类增长，**另列 C7-008**；(c) mock 目录（`CFG_AI_SERVICE_MOCK_DIR`）——测试态写入，生产默认不触达，**不适用**；(d) slf4j 应用日志——外部 logback 轮转体系，**不适用**。
- **信心水平**: 确定（增长机制）/ 很可能（实际增长速率取决于 logMessage 开关率）
- **误报排除**: 非将「日志需要轮转」这类通用运维常识泛化报备：C7 步骤 1 明确要求盘点 append-only 持久化的清理路径，此为本模块内最大的按调用线性增长源，且分区结构存在而清理动作缺失属可低成本补齐的自愈缺口。
- **复核状态**: 已保留(P3)｜新发现——回码核验成立：DefaultChatLogger 全文件无 delete/clean/rotate/purge（grep 直核）；ChatLogHelper 日期分区路径展开直核（:31-35）——分区存在而清理动作缺失定性准确。与同站点 C5-003(P2) 判级分叉登记留 7.1（C7 侧：logMessage 可选 + 日期目录即分区键提供外部轮转接管面，P3 可辩护；C5 侧：自管文件无轮转接管 P2 亦可辩护——分账不分立，修复同一补 retention 配置）

### [C7-010] LocalFileOperator.readFileContent 全量读入后截窗——offset/limit 不作用于读取量，大文件读取内存峰值失控

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/file/LocalFileOperator.java:92-122`
- **证据片段**:
  ```java
  // LocalFileOperator.java:102,115 —— 先全量读，再内存截取
  String content = FileHelper.readText(file, "UTF-8");   // 全文件载入
  ...
  content = content.substring((int) offset, (int) offset + limit);  // limit 只作用于截取
  ```
- **严重程度**: P2
- **并发场景**: agent 文件工具并发调用（多会话同时读文件）；单次调用内存峰值 = 2× 文件字节大小（String 载入 + substring 拷贝），与请求的 `limit` 无关。
- **触发时序**: T1 会话日志/数据文件随 C7-009 增长至 GB 级；T2 agent 发起 `readFileContent("...", offset, limit=4000)`（只想要 4KB 窗口）；T3 `readText` 将整文件载入为 String（GB 级 char[]），再 substring——并发数次即触发堆耗尽/OOM；`offset` 检查 `offset >= content.length()` 也必须先完成全量读。
- **现状**: 参数语义声明了窗口读取（`FileContent` 携带 offset/limit），实现退化为「全量读+内存切片」；`readLines`（`:125-139`）与 `grepFiles`（`:406` readLines 逐文件全量 List）同型。
- **风险**: 长时运行服务里文件单调增长（与 C7-009 复合）使该 OOM 概率随运行时长单调上升；降档豁免不适用——这是运行期请求路径而非启动期。
- **建议**: 改为 `RandomAccessFile`/`Files.newByteChannel` 定位 offset 后按 limit+1 读取（多读 1 字节判定 hasMore），`readLines` 大文件路径改流式逐行（`FileHelper` 若无现成设施则在模块内补）；`hasMoreData` 顺带修正（当前恒 false，`:111,116`）。
- **失效面枚举**: (a) `readFileContent`——**缺陷点**；(b) `readLines(path)`（`:125-139`）——全量 List，**同病**；(c) `grepFiles`（`:395-432`）——逐文件 readLines 全量，但有 limitPerFile/totalLimit 结果限流（读取量仍全量），**半同病**；(d) `findFilesByFilter`（`:190-204`）——有 maxFileCount 终止守卫，**已覆盖**；(e) `globGrep`（`:441`）——以 maxFileCount=0 调 (d)（注释自述「0表示无限制」），**同病面**。
- **信心水平**: 确定
- **误报排除**: 非「一次性载入 vs 流式」的泛化性能建议：limit 参数的契约语义（窗口读）与实现（全量读）直接矛盾，且触发条件（大文件）由本模块自身的 C7-009 增长源持续制造，构成可具体化的 OOM 链。
- **复核状态**: 已保留(P2)｜新发现——回码核验成立：`FileHelper.readText` 全量读后 `content.substring(offset, offset+limit)` 直核（:102/:115）——limit 不作用于读取量、峰值≈2×文件；`hasMoreData` 恒 false 直核（:111 初始化后无置真路径）。与同站点 C5-004(P2) 同判级收敛（C5 定性「limit 只约束返回物不约束物化」一致），7.1 并单

### [C7-011] secret 负缓存永不过期——secret 文件事后创建/轮换不自愈，需手工触发 clearSecretCache

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/LlmConfigHelper.java:135-154`
- **证据片段**:
  ```java
  // LlmConfigHelper.java:140-150 —— 空串也入缓存，永不失效
  apiKey = secretCache.computeIfAbsent(provider, k -> {
      File secretFile = new File(secretDir, provider + ".txt");
      if (secretFile.exists()) { ... return secret; }
      return "";                                    // 负缓存：文件缺席也永久记忆
  });
  ```
- **严重程度**: P3
- **并发场景**: 多 worker 并发首次解析同一 provider key；`MapCache` 构造第二参 true（同步缓存）护住 computeIfAbsent 原子性（无竞态——本条不是并发缺陷，是自愈语义缺失）。
- **触发时序**: T1 服务启动时 `{secretDir}/{provider}.txt` 尚未就绪（secret 注入晚于首调用/部署顺序问题）→ 缓存 `""`；T2 运维放入/轮换 secret 文件；T3 缓存永不失效（无 TTL、无文件 mtime 校验），`resolveApiKey` 持续返回 `""` → AUTH_INVALID 直至有人手工调 `clearSecretCache()`（无自动触发点，grep 全模块仅 setter 链暴露）。
- **现状**: 正缓存（已有 secret 的轮换）同样不自愈；`clearSecretCache` 是公开 bean 方法（可经运维调用），但模块内无任何自动失效路径，也无文档声明该运维依赖。
- **风险**: 长时运行中 secret 轮换（安全合规的常规操作）后所有走该 provider 的调用持续 401，账号链 failover 也救不了（链上账号同源 `resolveApiKey` 兜底同样拿旧值）；自愈语义缺失使故障恢复依赖人工介入。
- **建议**: 负缓存加短 TTL（如 30s 惰性过期），或按 secretFile mtime 校验失效；至少在 AUTH_INVALID 分类回执中提示「secret 缓存可能过期，可调 clearSecretCache」。
- **失效面枚举**: (a) `LlmConfigHelper.resolveApiKey` secretCache——**缺陷点**（唯一缓存点，无同族兄弟）；(b) `AiCoreConfigs` 配置变量路径的 apiKey（`AppConfig.var`）——配置热更新由 config 体系负责，**不适用**；(c) credentialResolver 路径（`ChatServiceImpl.java:300-305`）——每次调用实时解析，无缓存，**不适用**。
- **信心水平**: 确定
- **误报排除**: 非对缓存原语（MapCache/computeIfAbsent）的泛化报备：报的是负缓存条目无失效语义这一具体自愈缺口，触发链（secret 迟到/轮换）在长时运行中为常态运维动作。
- **复核状态**: 已保留(P3)｜新发现——回码核验成立：负缓存 `return ""` 入 computeIfAbsent 永驻（LlmConfigHelper.java:140-150 直核，无 TTL/mtime 失效路径）；clearSecretCache 无自动触发点。P3 维持（触发链为 secret 迟到/轮换运维常态，后果 fail-visible AUTH_INVALID 非静默）

### [C7-012] 接管锁续期异常被无限容忍——lease 丢失只认 false 返回，续期抛错期间本实例永不中止，非对称 DB 分区下双执行

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/SessionLockRenewal.java:65-78`（renewOnceSafe catch RuntimeException 仅 WARN）；`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/runtime/lock/DbSessionTakeoverLock.java:286-316`（tryRenew SQLException → throw，:312-315）
- **证据片段**:
  ```java
  // SessionLockRenewal.java:69-77 —— 异常与"续期失败"两种结局被区别对待
  try {
      boolean renewed = config.getSessionTakeoverLock().tryRenew(sessionId, ownerId, config.getLockLeaseMs());
      if (!renewed) {
          handleLeaseLost(handle, sessionId, ownerId);   // 只有 false 才中止执行
      }
  } catch (RuntimeException e) {
      LOG.warn("... (will retry next interval): {}", ...); // 抛错 → 无限重试，永不升级
  }
  ```
- **严重程度**: P1
- **并发场景**: 引擎 worker 线程执行 ReAct 循环；`lockRenewExecutor` 单线程周期调 `tryRenew`（默认 renew interval 10min、lease 30min，`DefaultAgentEngineConfig.java:154-155`）；其他实例的 `ScheduledRecoveryManager`/`tryAcquire` 依据 `LOCK_EXPIRES_AT <= now` 抢占。
- **触发时序**: T1 实例 A 执行会话 S，lease 30min；T2 A 与 DB 之间发生非对称网络分区（A 的连接池耗尽 / 网络命名空间故障，B 可达 DB）——A 的每次 `tryRenew` 在 `dataSource.getConnection()`/`executeUpdate` 抛 SQLException → `renewOnceSafe` 仅 WARN，`handleLeaseLost` 永不触发；T3 lease 到期后 B 侧 `tryAcquire` 走 expired-preempt 分支（`DbSessionTakeoverLock.java:203-209`）抢占成功并开始执行 S；T4 A 侧执行**继续**（LLM 调用、工具副作用照常产生），直到分区恢复后下一次 `tryRenew` 返回 false 才中止。交叠后果：双实例并发执行同一会话，窗口 = 整个分区时长 + 一个续期间隔。
- **现状**: 续期协议把「续期失败」的判据窄化为 false 返回值；异常路径的容忍无失败次数上限、无「连续 N 次异常即按 lease 丢失处置」的升级语义，javadoc 亦未声明该差异。
- **风险**: 接管锁的存在意义（防双执行）在 DB 抖动/分区这一长时运行必然事件面前失效；双执行的代价是重复工具副作用 + 双份 LLM 成本 + 终态互相覆盖。DB 故障演练（连接池打满 > lease 时长）即可稳定复现。
- **建议**: `renewOnceSafe` 增加连续异常计数：超过 `ceil(lockLeaseMs / lockRenewIntervalMs)` 次即调 `handleLeaseLost`（保守等价于「lease 必然已过期」）；同时区分「连接失败」与「SQL 语义失败」两类日志，便于排障。
- **失效面枚举**: (a) `SessionLockRenewal.renewOnceSafe`——**缺陷点**；(b) `DefaultAgentEngine.registerExecutionSlot` / `AgentSessionLifecycle.{resumeSession,wakeSession,acquireRestoreExecutionSlot}` 的 `startLockRenewal` 调用点（DefaultAgentEngine.java:923、AgentSessionLifecycle.java:304/493/897）——四处共用同一续期实现，**同病**；(c) `DbDaemonCoordinator`（scan-lease，无续期循环，依赖周期 re-acquire）——**不适用**；(d) `handleLeaseLost` 本身（:79-91）——语义正确，仅不可达，**已覆盖**。
- **信心水平**: 确定（机制）/ 很可能（生产非对称分区频率）
- **误报排除**: 非对 DB 基础设施自身的报备（连接池属外部）；报的是 nop-ai-agent 续期循环对异常结局的语义空洞——与 false 结局的处理不一致且无文档裁定。
- **复核状态**: 已保留(P1，条件面)｜新发现——回码核验成立：renewOnceSafe `catch (RuntimeException) → LOG.warn` 无升级语义直核（:71-77）；tryRenew `SQLException → throw new NopAiAgentException`（RuntimeException 子类）直核（DbSessionTakeoverLock.java:312-315）——异常结局确实落入无限容忍分支、仅 false 触发 handleLeaseLost 的协议窄化成立；lease 30min/renew 10min 默认直核（DefaultAgentEngineConfig:154-155）；非对称分区双执行时序（T1-T4）无虚构。装配面核对：缺省 `sessionTakeoverLock = NoOpSessionTakeoverLock.noOp()`（config:154）——条件面披露但**不降档**，依据本单元 C3-021 判例（模块交付的 DB fencing 实现，多实例部署即其设计场景；WI 2.5 C7-012 条件面 P1 同判例）。fencing/租约族跨单元对照 WI 1.6（C3-003 续租缺失/C1-001 CAS 守卫）：模块不同不重复立项

### [C7-013] DBCheckpointManager 未覆写 remove()——AR-10 终态清理对 DB 后端空转，byWatermark/bySession/loadedSessions 三图随全部历史 checkpoint 无界增长

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/DBCheckpointManager.java:93-95`（三缓存）、`:204-212`（write-through 只进不出）、`:135-137`（cacheEnabled）；`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/ICheckpointManager.java:119-121`（default remove = no-op）；引擎终态清理点 `engine/DefaultAgentEngine.java:1054-1056`、`engine/AgentSessionLifecycle.java:417-419, 577-579, 977-979`
- **证据片段**:
  ```java
  // ICheckpointManager.java:119-121 —— 默认 no-op，注释明言 DB 实现依赖它
  default void remove(String sessionId) {
  }
  // DBCheckpointManager.java:204-212 —— write-through 只增不删，全类无 evict/TTL/clear
  if (cacheEnabled()) {
      byWatermark.put(checkpoint.getWatermark(), checkpoint);
      ... bySession.computeIfAbsent(sid, ...).add(checkpoint);
  ```
- **严重程度**: P1
- **并发场景**: 多 ReAct worker 线程并发 `saveCheckpoint`（每 iteration 一次 LLM_TURN + 每工具 2-3 次阶段 checkpoint）；引擎 worker 在终态 finally 调 `checkpointManager.remove(sessionId)`。
- **触发时序**: T1 部署使用 `DBCheckpointManager`（cacheEnabled 默认 true：`currentTenant()==null`）；T2 会话 S 执行产生 N 个 checkpoint，全部进 `byWatermark`（key=watermark，含 execStartTime 去重段，永不重复命中旧键）与 `bySession`；T3 S 终态 → 引擎调 `remove(S)` → 命中接口 default no-op → **三图零清理**；T4 长时运行下每会话的 N 个 Checkpoint 对象（含 input/output summary 全文，CLOB 同款负载）常驻堆，按「会话数 × 每会话 checkpoint 数」单调累积。`loadedSessions` 负缓存连无 checkpoint 的会话也各留一条。
- **现状**: Plan 278（AR-10）为防「cache 不无限增长」而设立，`ToolExecutionCheckpoint` 与 `FileBackedCheckpointManager` 均正确覆写 remove；DB 后端是**同族漏修**（接口注释甚至点名「NoOp, DB…continue to compile and behave identically」，把 no-op 当作 DB 的预期行为，未陈述其内存后果）。DB 表行不删是已声明边界，但**内存缓存**无界未声明。
- **风险**: 周级运行 + DB checkpoint 后端 + 租户未启用的部署（默认形态）下堆缓涨无回收路径；`getLatestCheckpoint` 对热会话还在线性扫 `bySession` 全量 list（`:238-253` 三重比较），旧会话越多性能越差。
- **建议**: DBCheckpointManager 覆写 `remove(sessionId)`：按 session 前缀清理 `byWatermark`（仿 `FileBackedCheckpointManager.remove` 的先取 list 再逐键删）+ 移除 `bySession`/`loadedSessions` 条目；如需保 DB 行为"identically"，只清内存即达意。
- **失效面枚举**: (a) `DBCheckpointManager`——**缺陷点**；(b) `ToolExecutionCheckpoint.remove`（:90-102）——**已覆盖**；(c) `FileBackedCheckpointManager.remove`（:201-218）——**已覆盖**；(d) `NoOpCheckpoint`——无状态，**不适用**；(e) 引擎 5 个终态清理调用点——全部只认 `ICheckpointManager.remove` 接口，**调用侧已覆盖**，缺口纯在实现侧。
- **信心水平**: 确定
- **误报排除**: 非 F4 泛化（「append-only 需要清理」的常识性报备）：AR-10 清理协议已存在且两个兄弟实现已实现，唯独 DB 实现空转——是可定位的同族漏改（F3），且增长粒度是每-iteration 级而非每-会话级。
- **复核状态**: 已降级(P2 条件面，原 P1)｜新发现——缺陷本体全部直核成立：DBCheckpointManager 全类零 remove（grep 直核）、write-through put-only（:204-212）、接口 default remove no-op（ICheckpointManager.java:119-121）、引擎终态清理只认接口（DefaultAgentEngine:1054-1056 等）。降档依据（装配面证据）：缺省 `checkpointManager = NoOpCheckpoint.noOp()`（DefaultAgentEngineConfig:125 直核），DB 后端为显式装配面；同站点 C5-009(P2)/C1-011(P2) 双维独立复核均按缺省装配校准 P2——按 opt-in 先例（C1-005/C5-022 判例）条件化降档 P2，三维判级差（P1/P2/P2）就此收敛。真实装配 DB 后端即回 P1 级增长率（per-iteration Checkpoint 含 CLOB 同款负载），7.1 排期按非缺省面权衡

### [C7-014] 心跳等待期 wakeup turn 无数量预算且永久写入 ctx——长工具调用的 LLM 成本随等待时长线性放大，WAIT 碎片挤占上下文直至 force-stop

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java:1270-1329`（runHeartbeatToolPhase while(true)）、`:1492-1562`（runHeartbeatWakeupTurn）、`:1550-1553`（addMessage 永久入史）、`:1474-1481`（nextWaitSlice）；`engine/DefaultAgentEngineConfig.java:171`（heartBeatIntervalMs 默认 60_000，**默认开启**）；`engine/AgentToolDispatcher.java:427-431`（cap≤0 → deadline=Long.MAX_VALUE）
- **证据片段**:
  ```java
  // ReActAgentExecutor.java:1306-1309 —— 每个 slice 到期且工具未完 → 一次全上下文 LLM 调用
  } else {
      runHeartbeatWakeupTurn(ctx, sessionId, agentName, session, toolExecCtx,
              execStartTime, checkpointSeq, options, state);
  }
  // :1550-1552 —— 每个非阻塞 wakeup 把 heartbeat 证据 + 回复永久写入会话史
  ctx.addMessage(heartbeatMessage);
  if (assistantMsg != null) { ctx.addMessage(assistantMsg); }
  ```
- **严重程度**: P2
- **并发场景**: ReAct worker 线程独占驱动等待循环；每次 wakeup 经 `doWakeupCallWithRetry`（另在 timeoutExecutor 上派生阻塞 call 线程）。等待期无 compaction/compaction-check（注释自述 "No WAIT_FOR/assessGoal/compaction"），唯一的上下文护栏是 `loopGuard.shouldForceStop`。
- **触发时序**: T1 某工具经 `toolTimeoutPolicy` 配 4h cap（或 cap≤0 禁用 → Long.MAX_VALUE）；T2 工具运行 > 60s（默认 heartbeat 间隔）后每个 slice 到期触发一次 wakeup：全上下文请求 + WAIT 裁决，每次向 ctx 永久追加 ≥2 条消息；T3 4h 等待 = ~240 次 wakeup LLM 调用（每次携带不断膨胀的完整 ctx，成本随等待时长**超线性**放大），ctx 中堆积 ~480+ 条 heartbeat/回复碎片；T4a cap 到期工具被杀，碎片永留会话史污染后续轮次；T4b cap 禁用形态下唯一终止是 ctx 撑到 0.9×maxContextTokens 触发 `forced_stopped`——一次挂死工具以「上下文洪水」方式处决整个执行，且期间 LLM 花费无任何上限计数。
- **现状**: wakeup turn 被声明为「非 iteration、无 iteration 预算」（:1262-1269 javadoc）——语义声明成立，但**wakeup 次数上限、WAIT 碎片的 ctx 生命周期、等待期 compaction 豁免的组合后果**均无声明；机械 cap 是唯一预算。
- **风险**: 默认开启（60s）+ 长时运行工具（构建/批处理类工具常态 >60s）下成本放大是「何时发生」而非「是否发生」；cap 禁用（文档化 escape hatch）+ 挂死工具组合转化为无界花费 + forced-stop 假死。wait 期碎片还会抬高后续 iteration 的 compaction/forceStop 判定基线。
- **建议**: (1) 增加每等待期 wakeup 预算（如 `maxHeartbeatTurns`，超限后降级为纯机械等待）；(2) WAIT 裁决的 heartbeat 证据消息改用请求副本携带（对照占位符已走 `requestMessages` copy 的 WI3 invariant 1——同机制现成），仅 commit 终局结果；(3) 等待循环每 K 个 slice 顺带评估 `shouldTriggerCompaction`。
- **失效面枚举**: (a) `runHeartbeatWakeupTurn`——**缺陷点**；(b) `runSteeringWakeupTurn`（:1350-1426）——成功路径 commit steering + 回复入史（真实用户输入，合理）；失败路径有单 slice 节流，**半覆盖**；(c) facade 路径 `executeAllowedCalls`（AgentToolDispatcher.java:602-625）——无 heartbeat，`awaitCompletionSlice` 纯 deadline 驱动，**不适用**；(d) `adjudicateHeartbeatReply` ABANDON 路径——有 synthetic 结果 + tracker cancel，**已覆盖**。
- **信心水平**: 确定（机制与放大系数）/ 很可能（实际成本影响取决于工具时长分布）
- **误报排除**: 不否定 heartbeat 机制本身（设计裁定「唤醒轮不计迭代」是显式决策）；报的是该决策与「无次数上限 + 永久入史 + 等待期无 compaction」三个未声明面的复合后果，非对「有界声明存在的队列」的误报（此处无任何有界声明）。
- **复核状态**: 已保留(P2)｜新发现——回码核验成立：while(true) slice 循环 + 每 slice 到期 wakeup turn 直核（:1306-1309）；`ctx.addMessage(heartbeatMessage)` + assistantMsg 永久入史直核（:1550-1553）；heartBeatIntervalMs 默认 60_000 开启直核（DefaultAgentEngineConfig:171，注释确认非正值 = 禁用）；cap≤0 → `Long.MAX_VALUE` 直核（AgentToolDispatcher:427-431 注释自证）。wakeup 无次数预算 + WAIT 期无 compaction 组合后果未声明的定性成立。P2 维持（不否定「唤醒轮不计迭代」设计裁定，报三未声明面复合）

### [C7-015] restoreSession 在接管锁获取前变更共享会话状态——round-4 已修类（resume/wake）的 F3 漏修兄弟

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentSessionLifecycle.java:605-621`（restoreSession 顺序）、`:824-851`（transitionToRunningAndPublishRestored，:832 setStatus）、`:880-910`（acquireRestoreExecutionSlot，:888 tryAcquire）
- **证据片段**:
  ```java
  // AgentSessionLifecycle.java:611-618 —— 先变状态/发事件（:612-613），后抢锁（:618）
  CheckpointVerification verification = verifyLatestCheckpoint(sessionId, session);
  transitionToRunningAndPublishRestored(session, sessionId, agentName, approver, reason, ...); // setStatus(running)+SESSION_RESTORED
  ExecutionWiring wiring = buildExecutionWiring(agentName, session, sessionId);
  CancelHandle handle = new CancelHandle(wiring.ctx(), null);
  acquireRestoreExecutionSlot(sessionId, handle);   // tryAcquire 在此——失败已晚
  ```
- **严重程度**: P2
- **并发场景**: 恢复入口线程（人工 restoreSession / 恢复守护 RESUME / restorePendingSessions）与本实例其他执行线程共享 sessionStore 的缓存 AgentSession 实例；跨实例由 DB 锁仲裁。
- **触发时序**: T1 实例 A 调 `restoreSession(S)`，走到 :832 将**共享缓存** session 置为 running 并发布 SESSION_RESTORED 审计事件；T2 :888 `tryAcquire` 失败（实例 B 持有活跃 lease）→ 抛 NopAiAgentException；T3 A 的缓存中 S 停留在 running、审计事件已发出，但本地零执行发生——`getSessionStatus(S)` 报 running、事件消费者收到「已恢复」假象，直至 B 的终态保存传播回 A 的存储视图才自愈。对照 round-4 修复注释（:306-312 resumeSession / :495-501 wakeSession 均声明「failed tryAcquire leaves zero in-memory mutations」），restoreSession 是同类三兄弟中唯一未后置变更的。
- **现状**: resumeSession/wakeSession 已按「抢锁成功后才变更共享 session + 发事件」修复并留有注释；restoreSession 的状态变更与事件发布仍在锁前，且 `buildExecutionWiring`（loadAgentModel，:864）也在锁前执行。
- **风险**: 锁竞争失败（多实例恢复风暴/人工误操作常态场景）下状态被污染 + 审计流出现假 SESSION_RESTORED；依赖 status 的观测/路由决策（如监控按 running 会话数扩容）被误导。自愈依赖远端终态写回，窗口不可控。
- **建议**: 将 `transitionToRunningAndPublishRestored` 调用移入 `acquireRestoreExecutionSlot` 成功之后（与 resumeSession :313-344 的顺序对齐）；`verifyLatestCheckpoint` 为只读可留在锁前。
- **失效面枚举**: (a) `restoreSession`——**缺陷点**；(b) `resumeSession`（:295-344）——锁后变更，**已覆盖**；(c) `wakeSession`（:484-519）——锁后变更，**已覆盖**；(d) `restorePendingSessions`（:1061-1091）——先 isHeld 过滤再调 restoreSession，缩小但不消除 (a) 的窗口，**半覆盖**；(e) `DefaultOrphanRecoveryHandler.handleResume`——fire-and-forget 走 (a)，**同病**。
- **信心水平**: 确定（顺序事实与竞态链）
- **误报排除**: 非把「先读后写」的通用顺序问题泛化报备：变更发生在**共享缓存对象**上（resumeSession 注释自证该共享语义），且同族两处已按同一标准修复——按 F3 纪律逐一核对兄弟后确认此处为漏修。
- **复核状态**: 已保留(P2)｜新发现——回码核验成立：restoreSession 先 `transitionToRunningAndPublishRestored`（setStatus running + SESSION_RESTORED 事件，:832-852）+ `buildExecutionWiring` 后才 `acquireRestoreExecutionSlot`（:611-616/:618 顺序直核）；resumeSession round-4 修复注释「only NOW mutate the shared live session…zero in-memory mutations」直核（:306-312）——F3 漏修兄弟定性成立（三兄弟中唯一未后置变更）。P2 维持

### [C7-016] orphan RESUME 恢复无重试上限/退避/升级——持续性同步失败的会话每 60s 重放假 SESSION_RESTORED 事件

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/runtime/recovery/DefaultOrphanRecoveryHandler.java:166-184`（handleResume 无尝试记账）；`runtime/recovery/ScheduledRecoveryManager.java:433-449`（每轮扫描对每个 orphan 重放 handler）；`engine/AgentSessionLifecycle.java:605-620`（失败点在锁前的 buildExecutionWiring）
- **证据片段**:
  ```java
  // DefaultOrphanRecoveryHandler.java:172-174 —— 无 attempt 计数、无退避、无升级
  engine.restoreSession(sessionId, RECOVERY_APPROVER, RECOVERY_REASON);
  return new RecoveryOutcome(sessionId, RecoveryMode.RESUME, true,
          "RESUME: restoreSession triggered (fire-and-forget)");
  ```
- **严重程度**: P2
- **并发场景**: `ScheduledRecoveryManager` 守护线程（默认 60s fixed-delay）逐 orphan 调 handler；引擎侧 restoreSession 同步阶段抛错即返回 failed outcome，状态仍 running/pending → 下一轮依旧入选 orphan 集。
- **触发时序**: T1 实例崩溃遗留会话 S 持久态 status=running；T2 恢复守护检测为 orphan → handleResume → restoreSession：`validateRestorableSession` 通过 → `transitionToRunningAndPublishRestored` 发布 SESSION_RESTORED → `buildExecutionWiring` 内 `loadAgentModel(agentName)` 因 agent 模型已被删除/改名而**持续**抛错（持久条件，非瞬态）；T3 异常被 handler catch 为 failed outcome，S 状态未变 → 60s 后同一序列重放。交叠后果：每分钟 2 条 WARN + 1 条假 SESSION_RESTORED 审计事件，无限循环；无尝试计数、无指数退避、无「连续 N 次失败升级 ABORT/人工」路径。恢复守护自身的 `scanOnceSafe` 异常也仅 WARN（:393-396），对该风暴无感知。
- **现状**: handler javadoc 声明了「per-session failure isolation」（单次失败不中断扫描），未声明重试预算语义；`RecoveryScanResult` 聚合 outcomes 但无跨轮记忆，风暴在指标上表现为每轮固定 1 条 failed outcome，不可辨。
- **风险**: 长时运行中「持久不可恢复 orphan」（模型删除、store 损坏、checkpoint 表损坏）是常态事件；每会话每分钟的日志/事件放大随 orphan 数线性增长（F4 日志放大），且 SESSION_RESTORED 假事件污染审计流；恢复守护线程被无效重试占用。
- **建议**: handler 侧增加 per-session attempt 记忆（内存 map + 上限，或复用 `ai_agent_session` 扩展列/独立表），超限升级为 ABORT（现有 `handleAbort` 语义现成）或 SKIPPED+告警；RESUME 失败加指数退避（复用 `StandardRetryPolicy` 的 backoff 曲线思想）。
- **失效面枚举**: (a) `DefaultOrphanRecoveryHandler.handleResume`——**缺陷点**；(b) `handleAbort`（:191-225）——条件 UPDATE 幂等，失败即 affected=0（会话已终态化后不再入选 orphan 集），**自终止**；(c) `DefaultSessionTimeoutHandler` 三分类（:163-209）——FORCE_FAILED 后状态终态化，**自终止**；(d) `NoOp*` 默认 handler——SKIP 无重放面，**不适用**；(e) `restorePendingSessions`——人工触发不自动重放，**不适用**。
- **信心水平**: 确定（重放机制）；触发条件（持久失败的 orphan）在长时运行中很可能
- **误报排除**: 非对「重试需要退避」的泛化常识报备：C7 步骤 3 明确命中「定时任务失败重试与新调度叠加」；此处定时器周期（60s）× 无上限 × 假事件副作用三者齐备，且修复路径（ABORT 升级）模块内现成。
- **复核状态**: 已保留(P2)｜新发现——回码核验成立：handleResume 无 attempt 记账/退避/升级直核（:166-184 catch 仅返回 failed outcome）；每轮扫描 per-orphan 重放直核（ScheduledRecoveryManager:433-449 「Invoke the orphan-recovery handler once per detected orphan」）；默认 60s 间隔直核（DEFAULT_SCAN_INTERVAL_SEC=60 :125）。持久失败 orphan 每分钟 2 WARN + 1 假 SESSION_RESTORED 无限重放的时序成立。相邻面指针：C3-018（fire-and-forget 异步失败静默，P2）为不同失效层（C3 = 结果不可见；本条 = 重放无收敛），修复同批协同

### [C7-017] 会话级治理注册表无终态清理——GoalTracker / WaitCoordinator / DenialLedger 随 distinct 会话数单调增长

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/SessionGoalTracker.java:60, 108`（entries 只进不出）；`reliability/DefaultWaitCoordinator.java:47, 83-96, 116-123`（sessions 只进不出，checkWait 仅置空 condition）；`security/DefaultDenialLedger.java:48, 76-77, 105-110`（counts 仅 resume 时 reset）；`security/DBDenialLedger.java:222`（DELETE 仅 reset 路径）；引擎终态清理仅覆盖 checkpointManager（`engine/DefaultAgentEngine.java:1054-1056`）
- **证据片段**:
  ```java
  // SessionGoalTracker.java:108 —— 每个记录过工具迭代的会话永久占据一个条目
  SessionState entry = entries.computeIfAbsent(sessionId, k -> new SessionState());
  // DefaultWaitCoordinator.java:104-107 —— 消费只清 condition，WaitState 外壳永留
  if (state.satisfied || isConditionSatisfied(state.condition)) {
      WaitCondition consumed = state.condition;
      state.condition = null; ...
  ```
- **严重程度**: P2
- **并发场景**: 多 ReAct worker 并发读写三个单例 bean 的 ConcurrentHashMap；引擎终态 finally 无对应清理调用（对照 checkpointManager.remove 的成对形态）。
- **触发时序**: T1 会话 S1 记录一次工具迭代（GoalTracker 入表）、请求过一次 WAIT_FOR（WaitCoordinator 入表）、被 deny 一次（DenialLedger 入表）；T2 S1 正常 completed——引擎终态清理只调 `checkpointManager.remove(S1)`，三个注册表条目**全部残留**；T3 周级运行 × 每日数万会话 → 三表按 distinct 会话数单调增长（GoalTracker 触达面最宽：所有带工具调用的会话；WaitState 外壳含 condition/wakePayload 引用，持有最后一次 WaitCondition JSON 串）。DBDenialLedger 的 denial 行同样仅在 resumeSession 时 DELETE——未 resume 的 denied 会话行永久留存。
- **现状**: 三个类均为接口型 SPI（IGoalTracker/IWaitCoordinator/IDenialLedger），接口均无 `remove(sessionId)` 生命周期方法——清理协议在抽象层缺失，而非实现遗漏；NoOp 实现无状态故默认配置无症状，功能实现一接入即触发。
- **风险**: 长时运行内存缓涨（单条目小但无上限、无自愈）；与 C7-007（ThresholdBreaker 失败记忆条目）同族——F4「内存中的历史累积」在治理组件层的又一实例；DB denial 行的无限累积还拖慢 `getDenialCount` 的 COUNT 扫描。
- **建议**: 三个接口各加 `default void removeSession(String sessionId) {}`，引擎终态 finally 与 checkpointManager.remove 并列调用；或给条目加惰性 TTL（对照 C7-007 建议的 lastTransition + 24h 惰性清除）。DBDenialLedger 增加按 UPDATED_AT 的定期清扫（可挂 ScheduledRecoveryManager 扫描步骤）。
- **失效面枚举**: (a) `SessionGoalTracker.entries`——**缺陷点**；(b) `DefaultWaitCoordinator.sessions`——**缺陷点**；(c) `DefaultDenialLedger.counts`——**缺陷点**（reset 仅 resume 路径可达）；(d) `DBDenialLedger` 表——**同病**（行级）；(e) `NoOpGoalTracker/NoOpWaitCoordinator/NoOpDenialLedger`——无状态，**不适用**；(f) `checkpointManager` 三实现——已有 remove 协议，**已覆盖**（DB 实现的空转另列 C7-013）；(g) `ThresholdBreaker.entries`——X1 已列 C7-007，**另列不重复计**。
- **信心水平**: 确定
- **误报排除**: 非对单例 bean 持有 map 的泛化报备：每个表的增长源、触达面（哪些路径写入）与缺失的清理协议均已具体化；接口层清理协议缺失使其区别于「实现漏调一处」的单点问题。
- **复核状态**: 已保留(P2)｜新发现——回码核验成立：SessionGoalTracker.entries computeIfAbsent 无任何 remove（全文件直核）；DefaultWaitCoordinator.sessions put-only + checkWait 仅置空 condition（:104-107 直核）；DefaultDenialLedger counts.remove 仅 reset（:65）、reset 仅 AgentSessionLifecycle:322 resume 路径可达；DBDenialLedger DELETE 仅 reset 路径（:218-228 直核）。装配面修正（复核补证）：GoalTracker/WaitCoordinator 缺省 NoOp（config:142/:187），但 **DefaultDenialLedger 缺省即有状态**（config:122 `new DefaultDenialLedger()`）——初审「NoOp 实现无状态故默认配置无症状」表述不准确，缺省装配下 DenialLedger 面即增长（条目小、仅 denied 会话触达），该修正强化而非削弱发现，综合维持 P2。同站点族判级分叉（C5-013 P3/C3-017 P2 异面/C1-006/C1-014）留 7.1 归并

### [C7-018] 租约期限判定基于墙钟——C7-004 同族：时钟回拨冻结锁回收与接管，时钟前跳引发大规模抢占

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/runtime/lock/DbSessionTakeoverLock.java:161-162, 203-209, 291-292`；`runtime/coordination/DbDaemonCoordinator.java:163-164, 205-211`；`runtime/recovery/ScheduledRecoveryManager.java:480-486`（清理 DELETE `LOCK_EXPIRES_AT <= now`）；`runtime/recovery/DefaultSessionTimeoutHandler.java:184-190`（`lockExpiresAt <= now` 分类）
- **证据片段**:
  ```java
  // DbSessionTakeoverLock.java:161-162 / :291-292 —— 全部期限差值基于墙钟
  long now = CoreMetrics.currentTimeMillis();   // 默认实现即 System.currentTimeMillis()
  long expiresAt = now + leaseMs;
  // :208-209 抢占判据
  + "AND (" + ... LOCK_OWNER + " = ? OR " + LOCK_EXPIRES_AT + " <= ?)"
  ```
- **严重程度**: P2
- **并发场景**: 多实例引擎并发 `tryAcquire/tryRenew/release` + 各实例 `ScheduledRecoveryManager` 清理/接管 + timeout handler 分类；时间源 `CoreMetrics.currentTimeMillis()`（X1 已核证默认为墙钟）。
- **触发时序**: T1 实例 A 持会话 S 活跃 lease（expiresAt=T0+30min），每 10min 续期写入 `now+30min`；T2 NTP 步进/人工纠时使全集群时钟回拨 D=1h——A 的下次续期写入更小的 expiresAt，B 侧 `tryAcquire` 的 `LOCK_EXPIRES_AT <= now` 立即判活锁为过期 → 抢占 A 的**健康执行**（A 侧靠 C7-012 的 false→abort 才停下，期间双执行）；清理 DELETE 亦不误删（判断同向），但 timeout handler 的 `lockExpiresAt <= now` 把活跃锁分类为 FORCE_FAILED。反向（前跳）时：全表 lease 同时「过期」→ 下一轮扫描全部被判 stale/orphan → 集群范围抢占风暴（恢复守护 RESUME 模式下即全量 restore 风暴）。`DbDaemonCoordinator` 的 scan-lease 同构同病（团队扫描被单点冻结/风暴）。
- **现状**: `CoreMetrics` 统一（可测试 seam）不改变单调性缺失；`CoreMetrics.nanoTime()` 单调源存在未用于期限判定。X1 C7-004 已裁 ThresholdBreaker/ProviderFailoverQueue 同病，本条目把同族面延伸到 nop-ai-agent 的租约存储层（X1 范围外的兄弟，F3 补扫）。
- **风险**: 长时运行服务必然经历时钟步进；回拨窗口内租约语义整体失真（健康执行被抢占/被分类失败），前跳触发全量恢复风暴——恢复机制自身成为故障源。
- **建议**: 租约表的比较基准保持墙钟（跨进程共享存储只能用墙钟），但**本端活性判定**改单调钟：续期侧维护 `lastRenewNano`，`renewOnceSafe` 发现 `nano 差 > lease` 即按 lease 丢失处置（先于 DB 判据）；恢复守护清理/RESUME 增加最小租约年龄门槛 + 随机抖动，削平前跳风暴。
- **失效面枚举**: (a) `DbSessionTakeoverLock` acquire/renew 抢占判据——**同病**；(b) `DbDaemonCoordinator` acquire/renew 抢占判据——**同病**；(c) `ScheduledRecoveryManager.deleteStaleLocks`——**同病**（回拨期不误删，前跳期集中删）；(d) `DefaultSessionTimeoutHandler` 分类判据——**同病**（回拨把活锁分类 FORCE_FAILED）；(e) `DefaultWaitCoordinator` TIMEOUT deadline——单机内存条件，回拨仅推迟/提前单会话唤醒，**轻症不另立**；(f) X1 (a)-(c)（ThresholdBreaker/ProviderFailoverQueue）——已列 C7-004，**不重复计**。
- **信心水平**: 确定（机制）/ 很可能（步进窗口内事故频率）
- **误报排除**: 不以「墙钟不能用于跨进程租约」为由否定存储层设计（租约写入必须用墙钟）；报的是本端活性/分类判据未用单调钟、以及恢复动作无抖动防同跳风暴这两个未声明面，与 C7-004 的裁定口径一致。
- **复核状态**: 已保留(P2，条件面)｜新发现——回码核验成立：DbSessionTakeoverLock tryAcquire/tryRenew 全部 `CoreMetrics.currentTimeMillis()` + `LOCK_EXPIRES_AT <= ?` 抢占判据直核（:161-162/:203-209/:291-316）；DbDaemonCoordinator 同构直核（:163-164/:205-211 `COL_EXPIRES_AT <= ?`）；「跨进程共享存储只能用墙钟、本端活性判定可改单调钟」的修复方向论证成立（不以墙钟存储否定设计）。条件面同 C7-012（DB lock + 恢复守护装配面），维持 P2。跨单元墙钟族对照同 C7-004

### [C7-019] 全引擎共享单线程续期调度器且续期 JDBC 无期限——DB 抖动下续期延迟随会话数线性放大至租约过期

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/SessionLockRenewal.java:44-54`（懒创建 single-thread scheduler）、`:62-63`（scheduleWithFixedDelay per session）；`runtime/lock/DbSessionTakeoverLock.java:302-311`（getConnection + UPDATE，无 statement/deadline 参数）
- **证据片段**:
  ```java
  // SessionLockRenewal.java:45-50 —— 全引擎所有会话的续期共用一个线程
  lockRenewExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "nop-ai-agent-lock-renew");
      t.setDaemon(true); ...
  // :62-63 每会话一个 fixed-delay 任务，全部排队到同一线程
  return exec.scheduleWithFixedDelay(() -> renewOnceSafe(handle, sessionId, ownerId), ...);
  ```
- **严重程度**: P2
- **并发场景**: N 个并发长执行会话 × 单续期线程；`tryRenew` 的 JDBC 往返时长取决于外部连接池/DB 负载（无语句级 deadline，连接池等待亦计入）。
- **触发时序**: T1 引擎承载 N=200 个活跃会话（每个 10min 一次续期，均值负载 1/3 次/秒，尚有余量）；T2 DB 进入抖动段（单次 UPDATE 50ms + 连接获取偶发 1-2s）——单线程吞吐骤降，任务队列积压，个别会话的续期实际触发时刻延迟 δ；T3 当 δ > `lockLeaseMs - 上次续期以来时长`（最坏一次抖动即可），该会话 lease 到期：同机其他实例（或同机恢复守护）按过期抢占 → 健康执行被中止（false→abort）/恢复风暴；N 越大、抖动越长，风暴面越大。延迟对调用方不可见（无慢续期指标/日志阈值）。
- **现状**: fixed-delay 语义使单任务慢不叠加自身，但**跨会话串行**无任何隔离；续期延迟无观测点（javadoc/指标均未覆盖「续期实际触发 vs 计划触发」的偏移）。
- **风险**: 水平扩容（更多会话/实例）线性放大续期线程负载，使「lease 30min」的名义安全性随规模衰减；F5 面：无进度可见性的排队挂起——从单个会话视角，续期「不产出也不报错」。
- **建议**: 续期线程池改为小规模 `ScheduledThreadPoolExecutor`（如 2-4 线程）或复用平台 `IScheduledExecutor`；`renewOnceSafe` 记录调度偏移并在超阈值（如 > lease/3）时 WARN；长期方向是 lease 时长与 renew 间隔的配置校验（启动时断言 `renewInterval × N_max < lease` 的容量提示）。
- **失效面枚举**: (a) `SessionLockRenewal.getLockRenewExecutor` 单线程——**缺陷点**；(b) `DbSessionTakeoverLock.tryRenew` 无 deadline——**同病面**（延迟来源）；(c) 外部注入 executor（`setLockRenewExecutor`）——容量责任转移给装配方，**不适用**；(d) `ScheduledRecoveryManager` 的 `IScheduledExecutor` 注入式调度——平台设施注入、扫描幂等，**不适用**。
- **信心水平**: 确定（串行结构）/ 很可能（触发所需规模与 DB 抖动组合）
- **误报排除**: 非对「单线程调度器」原语本身的报备：报的是其承载的全引擎关键活性路径（fencing 前提）与无界会话数、无 deadline JDBC、无偏移观测的组合；平台设施（nop-commons 调度器）未被使用亦非缺陷本身，仅作为修复建议。
- **复核状态**: 已保留(P2，条件面)｜新发现——回码核验成立：懒创建 `newSingleThreadScheduledExecutor("nop-ai-agent-lock-renew")` + per-session `scheduleWithFixedDelay` 全部排队同一线程直核（:45-54/:62-63）；tryRenew `dataSource.getConnection + executeUpdate` 无语句级 deadline 直核（DbSessionTakeoverLock:302-311）。同站点指针（复核登记）：C4-008(P2 保留，续期单线程 DB 队头阻塞) 已独立登记同站同根——C7 侧意见：两案证据互补不冲突，主处置归 C4-008 并单，本条随单吸收。条件面同 C7-012，维持 P2

### [C7-020] 沙箱后端 success 路径 reader 线程泄漏——孙进程持有 stdout 管道时 interrupt 无效，线程单调累积且输出静默丢失

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/NoOpSandboxBackend.java:103-143`（execute）、`:150-185`（drainStream）、`:187-197`（awaitReader）；`security/DockerSandboxBackend.java:255-296, 555-582, 586-596`（同构实现）
- **证据片段**:
  ```java
  // NoOpSandboxBackend.java:187-196 —— grace 后 interrupt：对阻塞在 read() 的线程无效
  private static void awaitReader(Thread reader, int wallSeconds) {
      long graceMillis = Math.max(500L, Math.min(2_000L, wallSeconds * 500L));
      try { reader.join(graceMillis); } catch (InterruptedException e) { ... }
      if (reader.isAlive()) { reader.interrupt(); }   // InputStream.read 不响应 interrupt
  }
  ```
- **严重程度**: P2
- **并发场景**: 工具执行线程同步调 sandbox 后端；每调用派生一个 daemon reader 线程阻塞在 `process.getInputStream().read()`。
- **触发时序**: T1 工具命令产生孙进程且孙进程继承 stdout（shell 后台任务 `cmd &`、nohup 化守护、任何 fork-不-exec-close 的子进程）；T2 主进程退出、`waitFor` 返回，success 路径进入 `awaitReader`——管道写端仍被孙进程持有，`read()` 永不返回 EOF；T3 grace（0.5-2s）后 `reader.interrupt()`——阻塞 I/O 不响应中断，reader 线程**永久存活**；T4 `capturedToString` 读到 null（drainStream 的 finally 未执行）→ 工具结果以**空输出**交付且无任何 marker。每次此类调用净泄漏一个线程（线程栈 + 4KB buf + 已产出的 StringBuilder），重复触发单调累积。
- **现状**: 消费侧对 reader 存活有兜底（join+interrupt），但对「interrupt 无效」这一 I/O 语义无第二道防线（无 `process.destroy()` 补刀、无 capturedRef 超时兜底值、结果不携带 `outputIncomplete` 标记）；timeout 路径有 `killTree`（NoOpSandboxBackend:127）而 **success 路径没有**——同族两路径清理不对称（F8）。
- **风险**: 长时运行中 shell 类工具的后台化用法常见；线程与缓冲随触发次数线性泄漏（daemon 不阻退出但耗尽运行期内存/句柄），且「空输出」是静默的数据完整性失真——LLM 拿到空结果继续推理，排障无从下手。
- **建议**: success 路径 grace 后先 `process.descendants().forEach(ProcessHandle::destroyForcibly)`（先杀孙进程关管道）再给一次短 join；仍存活则结果置 `timedOut/outputIncomplete=true` 标记（SandboxResult 已有 timedOut 字段，扩展语义即可）；DockerSandboxBackend 同步修复。
- **失效面枚举**: (a) `NoOpSandboxBackend.execute` success 路径——**缺陷点**；(b) `NoOpSandboxBackend` timeout 路径——有 killTree，**已覆盖**；(c) `DockerSandboxBackend.execute` success 路径（:277-296）——同构无杀进程兜底，**同病**（docker CLI 场景孙进程概率低但存在：`-v` 挂载脚本内后台进程）；(d) `DockerSandboxBackend` timeout 路径——killContainer 后 awaitReader，**已覆盖**；(e) `SisypheanSustainer`/其他可靠性组件——无进程模型，**不适用**。
- **信心水平**: 确定（机制）；触发依赖工具命令形态，长时运行中很可能
- **误报排除**: 非对 `Thread.interrupt` 原语语义的泛化报备：缺陷是「interrupt 作为唯一兜底 + 结果静默置空 + 清理路径不对称」的组合；进程树清理属模块自身职责（timeout 路径已实现 killTree，证明能力存在），非平台设施缺失。
- **复核状态**: 已保留(P2)｜新发现——回码核验成立：awaitReader join + interrupt 直核（:188-197，注释自证「InputStream.read may or may not honour interrupt」——对管道 read 实为无效）；success 路径无 killTree、timeout 路径有（:122 vs :139 对照直核）——F8 清理不对称成立；interrupt 后 capturedRef 未置值 → 空输出无 marker 交付成立。P2 维持

### [C7-021] plan 任务无类型失败零退避热循环——失败任务即刻重试直至安全界熔断

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/plan/runtime/PlanExecutor.java:216-238`（失败 → pending，无延迟）、`:194-199`（cycle bound）、`:376-383`（computeSafetyBound）
- **证据片段**:
  ```java
  // PlanExecutor.java:236-238 —— 无类型失败一律重试（pending），while(true) 立即重跑
  } else {
      state.setTaskStatus(taskNo, AgentExecStatus.pending);
  }
  // :194-195 唯一终止是安全界
  while (true) { if (++cycles > safetyMaxCycles) { throw ... } }
  ```
- **严重程度**: P3
- **并发场景**: 单线程 PlanExecutor 驱动；TaskRunner.run 失败但 `getFailureType()==null`（未分类失败）时任务回到 pending，`drivePhaseTasks` 的 while(true) 在同一轮立即重跑 ready 集——失败任务以零间隔连发。
- **触发时序**: T1 某任务的 TaskRunner 持续失败且不产出 FailureType；T2 每轮循环：重跑该任务 → 失败 → `recordError` 追加一条错误（`errors` 列表同步增长）→ 回 pending → 立即重跑；T3 直至 `safetyMaxCycles = max(100, (tasks+1)×threshold+50)`（:376-383，大 plan 下上千次）才 fail-loud——期间无任何 sleep/jitter，错误记录与 runner 副作用（如外部调用）以最大速率重复。
- **现状**: 注释声明「Untyped failures always retry (pending) — zero regression」（:222-223）——立即重试是显式默认；有界（安全界）+ fail-loud，缺的只是重试间隔与 errors 上限。
- **风险**: 大 plan + 持久失败组合下 CPU/下游空转放大（每 cycle 重跑全部 ready 任务），errors 列表同速膨胀（单次执行内存内，执行结束即弃，影响限于该次执行的时长与日志量）。
- **建议**: untyped 重试路径加最小退避（如 `min(1000ms, 50ms × 2^k)`，复用 `LlmCallCoordinator.sleepBackoff` 形态）+ `errors` 按 taskNo 设滚动上限；或在 W2-3 语义中要求 TaskRunner 必须产出 FailureType（fail-loud 于分类缺失）。
- **失效面枚举**: (a) `drivePhaseTasks` untyped 分支——**缺陷点**；(b) typed 分支——escalation 阈值终态化（failed），**有界**；(c) `checkPhaseGate` RETRY 循环——由 gate 自身 attempt/guard 双界约束（:254-259），**已覆盖**；(d) `respondToStagnation` ROLLBACK/SPLIT——外层 recoveries 计数封顶（:150-153），**已覆盖**。
- **信心水平**: 确定
- **误报排除**: 非把「重试无退避」从 C7-006 泛化套用：此处有显式安全界与已声明默认（zero regression 注释），危害限于单次执行内，故按校准降 P3 记录而非 P1/P2。
- **复核状态**: 已保留(P3)｜新发现——回码核验成立：untyped 失败 → pending 即刻重跑直核（:222-238，注释自证「always retry (pending) — zero regression」显式默认）；`computeSafetyBound = max(100, (tasks+1)×threshold+50)` 直核（:376-383）。有界 + fail-loud + 影响限于单次执行 → 初审 P3 降档理由成立，维持 P3

### [C7-022] team-flow 单目标 bound 成员节点无 per-member 期限且整体 deadline 只取消图不取消成员会话——僵尸成员执行 + 重claim 后同任务双执行

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/flow/MemberAgentTaskStep.java:152`（engine future 裸用无 orTimeout）、`team/flow/TeamTaskFlowOrchestrator.java:634-667`（overall deadline 仅 `taskRt.cancel`，无 `cancelSession`）
- **证据片段**:
  ```java
  // MemberAgentTaskStep.java:152 —— 无任何期限包装
  CompletableFuture<AgentExecutionResult> engineFuture = agentEngine.execute(request);
  // TeamTaskFlowOrchestrator.java:654-656 —— overall 超时只取消 nop-task 图
  try {
      taskRt.cancel(ICancellable.CANCEL_REASON_TIMEOUT);
  ```
- **严重程度**: P1
- **并发场景**: orchestrator 调用线程（`executeAsync` 的 orTimeout 定时器线程）× 引擎内成员 agent 执行 worker 线程；fan-out 步（daemon 路径与 multi-member 路径）走 `MemberFanOutDispatcher`，单目标 bound 计划走本步。
- **触发时序**: T1 图触发单目标节点，`MemberAgentTaskStep.execute` 在 :152 提交 `engine.execute`（无 orTimeout），成员 agent 卡在 C7-001 已证的「LLM 调用 body 无读超时」挂死形态；T2 `TeamTaskFlowOrchestrator.executeAsync` 的 `orTimeout(overallTimeoutMs)` 在 memberExecTimeoutMs×maxDepth 后触发 :646-667：`taskRt.cancel` 阻止后续节点，future 以失败结果完成——但**没有任何路径调用 `engine.cancelSession(memberSessionId)`**，成员执行继续作为僵尸消耗 LLM 配额（引擎侧 runningExecutions 条目同被 X2 CL-065 证明可无限期驻留）；T3 恢复守护（DefaultTeamTaskRecoveryHandler 配置为 RECLAIM 时）按 UPDATED_AT 超时把留在 CLAIMED 的任务重置 CREATED → 下轮 claim 重新派发 → 同一任务两个成员并发执行（epoch 绑定只挡住 stale complete，不挡重复执行成本与副作用）。
- **现状**: I3 R-2-3 的「per-member orTimeout + 超时取消会话」修复只落在 `MemberFanOutDispatcher.executeBoundMember`（:321-336），单目标短路径 `MemberAgentTaskStep` 与 orchestrator 整体 deadline 路径均未覆盖。
- **风险**: 挂死成员每次泄漏一个完整 agent 执行（LLM 配额 + 引擎执行槽 + DB 会话行）；反复编排含挂死成员的团队时僵尸数单调累积；RECLAIM 配置下叠加同任务双执行的副作用重复（工具写文件/外部调用）。
- **建议**: (1) `MemberAgentTaskStep` 对 `engineFuture` 施加 `orTimeout(memberExecTimeoutMs)` 并在超时分支 `engine.cancelSession(memberSessionId, …, true)`（对齐 `MemberFanOutDispatcher.executeBoundMember:328-336` 与 `CallAgentExecutor.executeSubAgent:471-479` 的已修形态）；(2) orchestrator overall-deadline 分支遍历 recorder 已启动节点调用 `cancelSession`，或在 `TaskStepReturn` 链上挂 cancel 传播。
- **失效面枚举**: (a) `MemberFanOutDispatcher.executeBoundMember`——orTimeout + cancelSession，**已修复**；(b) `MemberFanOutDispatcher.spawnOneTarget`——orTimeout + 下层 bounded get，**部分覆盖**（取消面缺口 → C7-023）；(c) `MemberAgentTaskStep`（单目标 bound）——**无期限无取消，缺陷点**；(d) `SpawnMemberAgentTaskStep`——经 spawner bounded get，**同 (b) 缺口**；(e) orchestrator overall-deadline——只 `taskRt.cancel`，**缺陷点**；(f) `CallAgentExecutor.executeSubAgent`（sync 路径）——orTimeout + cancelSession，**已修复**（:459-479）；(g) `CallAgentExecutor.executeViaMessenger`（async 路径）——无取消协调，**独立登记 → C7-024**。
- **信心水平**: 确定
- **误报排除**: 非「看到共享 future 就报线程不安全」：缺陷是同一「超时即取消底层执行」契约（`IAgentEngine.cancelSession` javadoc `engine/IAgentEngine.java:249` 明示取消是调用方责任）在同族路径上的 F3 漏修，且挂死前提由同文件 X1 批次 C7-001 的具体时序背书，非理论挂死。
- **复核状态**: 已保留(P1)｜新发现——回码核验成立：MemberAgentTaskStep:152 裸 `agentEngine.execute(request)` 无 orTimeout 直核；orchestrator overall deadline 仅 `taskRt.cancel(ICancellable.CANCEL_REASON_TIMEOUT)` 无 cancelSession 直核（:646-667）。**复核新增同站指针（任务指派未列，本复核补登记）**：同站点 C3-025(P2 保留，面 1 覆盖本条两站点) + C4-014(P2 保留)——三案同站未互指，判级分叉（C7 P1 vs C3/C4 P2）留 7.1 统一。C7 侧意见：僵尸泄漏持久性（挂死执行永不终止 → 引擎槽 + LLM 配额永续占用，非 C3-025 所论「双执行窗受 memberExecTimeout 差值界定」的有界面）直接命中校准 P1 档「错误/取消/重试路径的资源泄漏」，P1 可辩护；修复三案同 diff

### [C7-023] DefaultMemberSpawner bounded get 超时后不取消 spawned 会话——spawn 路径僵尸执行的 F3 兄弟

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/DefaultMemberSpawner.java:182-198`
- **证据片段**:
  ```java
  // DefaultMemberSpawner.java:188-198 —— get 有界，但 TimeoutException 分支无 cancelSession
  result = future.get(request.getMemberExecTimeoutMs(), TimeUnit.MILLISECONDS);
  } catch (TimeoutException e) {
      LOG.warn("DefaultMemberSpawner: spawned agent timed out after {}ms ... — SPAWN_FAILED", ...);
      return SpawnMemberResult.spawnFailed(
              "spawned agent timed out after " + request.getMemberExecTimeoutMs() + "ms");
  ```
- **严重程度**: P1
- **并发场景**: spawn executor worker 线程（`ai-agent-*-spawn-worker-N`）执行 bounded `get`；被 spawn 的 `spawned-<uuid>` 会话在引擎自己的执行线程上继续运行。
- **触发时序**: T1 spawn worker 在 :188 对 `engine.execute` 做 bounded get（新会话 `spawned-<UUID>`）；T2 成员执行挂死（C7-001 形态）→ `get` 超时 → worker 返回 SPAWN_FAILED 并释放；T3 `spawned-<uuid>` 会话无人取消——`spawnedSessionId` 在超时分支根本未被引用——执行永久占引擎槽与 LLM 配额；T4 daemon/编排层对该任务按 C7-022 时序重试派发，spawn 出**又一个**新会话——每个挂死成员净增一个永不终止的执行。
- **现状**: `MemberFanOutDispatcher.executeBoundMember` 的 javadoc（:302-311）明文宣告「超时必须 cancel 底层执行避免 zombie 消耗配额（AUDIT-14-01 语义）」，同仓库内 spawner 是该裁定的 F3 漏修兄弟；javadoc :46-53 只声明「释放 spawn worker」，未声明「放弃底层执行」。
- **风险**: 每次超时 spawn 泄漏一个完整 agent 执行；spawn 密集型团队（declarative memberSpec 驱动自动扩员）在下游 LLM 抖动期间僵尸数与超时次数线性相关，长期蚕食引擎并发额度（`tenantMaxConcurrentActors` 默认 10，僵尸可直接把正常派发挤到 quota deny）。
- **建议**: TimeoutException 分支补 `agentEngine.cancelSession(spawnedSessionId, "spawn timeout after …", true)`（cancel 失败 WARN 不掩盖原错误，形态照抄 `MemberFanOutDispatcher.java:328-336`）；并把「超时必取消」契约上移到 `IMemberSpawner` 接口 javadoc，防止后续第三个 spawn 实现再漏。
- **失效面枚举**: (a) `DefaultMemberSpawner.spawnMember`——**缺陷点**；(b) `MemberFanOutDispatcher.executeBoundMember`——已取消，**已修复**；(c) `CallAgentExecutor.executeSubAgent`——已取消，**已修复**；(d) `NoOpMemberSpawner`——不产生执行，**无失效面**；(e) spawn 会话 id 由本类 :170 生成且未外传给调用方，调用方（`SpawnMemberAgentTaskStep`/`MemberFanOutDispatcher.spawnOneTarget`）**无从代为取消**——修复必须落在本类或 `SpawnMemberResult`（新增 sessionId 字段）。
- **信心水平**: 确定
- **误报排除**: 非「bounded get 已存在即足够」：get 只保证 worker 线程释放，引擎侧执行体不受影响——这正是 executeBoundMember javadoc 显式否定过的「wait ends but execution continues」形态；亦非平台基础设施问题（cancelSession API 存在且已被两个兄弟路径使用）。
- **复核状态**: 已保留(P1)｜新发现——回码核验成立：TimeoutException 分支仅 SPAWN_FAILED 返回、`spawnedSessionId` 未被引用直核（:188-198）；executeBoundMember javadoc「超时必须 cancel 底层执行避免 zombie」裁定对照面直核——F3 漏修兄弟定性成立。同站指针（本复核补登记）：C3-025 面 2(P2) + C4-014(P2)——分叉同 C7-022 留 7.1，C7 侧意见同 C7-022（泄漏持久性支持 P1）；修复三案同 diff

### [C7-024] call-agent async 信箱路径超时无取消协调——caller 侧 orTimeout 丢弃 future，callee 侧子代理执行不可达地继续

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/tool/CallAgentExecutor.java:301-343`（executeViaMessenger 无 cancel）、`message/LocalAgentMessenger.java:100-133`（request 只 orTimeout，超时后无 aborted 通知通道）
- **证据片段**:
  ```java
  // CallAgentExecutor.java:328-329 —— 超时只作用于 caller 侧 future
  Duration timeout = Duration.ofMillis(timeoutMs);
  return messenger.request(envelope, timeout)
          .handle((response, ex) -> { ... });   // ex=TimeoutException → 仅返回 errorResult
  ```
- **严重程度**: P2
- **并发场景**: ReAct 工具分发线程（caller）× 信箱 handler 执行线程（callee 端子代理执行）经 `IMessageService` 解耦；DB 传输时中间还有 `DBMessageService` 轮询线程。
- **触发时序**: T1 caller 经 `executeViaMessenger` 发 REQUEST（payload 内含 resolvedSessionId）后 `orTimeout` 等待；T2 callee handler 收到消息开始执行子代理（长任务）；T3 caller 60s 超时返回 errorResult，ReAct 循环继续下一轮——**没有任何 cancelSession 路径触达 callee**（sync 路径 :471-479 的取消逻辑在 async 分支不存在）；T4 子代理执行完毕，RESPONSE 送回 reply topic，`ReplyDemultiplexer` 查无 pending → 仅 `LOG.debug("reply-no-pending")`（LocalAgentMessenger.java:233-236）——结果静默丢弃，执行期间资源全程消耗。
- **现状**: sync 路径注释（:439-443, :463-469）明确陈述「orTimeout 只取消 Future 不取消底层 engine.execute，所以必须 cancelSession」——async 路径连 future 都不持有引擎侧对象，且 REQUEST 已投递不可撤回，比 sync 路径更需显式取消协议（如 REQUEST 携带 correlationId 的取消信令），当前完全没有。
- **风险**: 常态下 callee 受自身内部期限约束（浪费有界）；挂死形态（C7-001）下与 C7-022 同型僵尸——且因跨信箱解耦，连「父会话被 cancelSession」的引擎级传播都不覆盖子会话。messenger 路径一旦成为默认装配（plan 224 方向），该缺口从边缘变主路径。
- **建议**: 短期：caller 超时分支向 `AgentMessageTopics.callAgentTopic()` 补发一条 CANCEL 控制信封（携带 correlationId），handler 收到后对已启动的子会话 `cancelSession`；长期：`IAgentMessenger.request` 增加返回 handle 的变体，超时由 messenger 统一投递取消。
- **失效面枚举**: (a) `executeSubAgent`（sync）——cancelSession 已实现，**已修复**；(b) `executeViaMessenger`（async）——**缺陷点**；(c) `SendMessageExecutor`——fire-and-forget 语义，无等待无取消需求，**不适用**；(d) `AgentDispatchExecutor`——同 fire-and-forget，**不适用**；(e) 信箱 handler 侧（引擎注册的 call-agent handler，engine 包 X2 分区）——收到即执行、无 caller-aborted 探测点，本条登记 caller 侧缺口，handler 侧由 C2/C7-024 复核阶段合并核查。
- **信心水平**: 确定
- **误报排除**: 非「异步工具超时返回错误即完成」的常规形态：本模块 sync 兄弟路径已确立「超时必须取消底层执行」的裁定与实现，async 路径是同一契约下的漏修；且 RESPONSE 到达后被静默丢弃证明系统并无「迟到结果复用」机制兜底。
- **复核状态**: 已保留(P2)｜新发现——回码核验成立：executeViaMessenger `messenger.request(envelope, timeout).handle(...)` 无任何取消协调直核（:301-343，handle 仅转 errorResult）；sync 路径 orTimeout + cancelSession 对照直核（:459-479）；LocalAgentMessenger.request orTimeout + pendingRequests whenComplete 清理直核（:105/:116）；迟到 RESPONSE 仅 `LOG.debug("reply-no-pending")` 直核（:212）。相邻面指针：C3-012（LLM 幽灵调用，P2）取消传播族——修复并入引擎取消传播批。P2 维持（常态 callee 自身期限有界；挂死形态叠加才成僵尸——比 C7-022/023 少一层确定性）

### [C7-025] DBMessageService 毒消息无限重投——无尝试计数/退避/DLQ，队首阻塞可使整队列停摆且 ERROR 日志 50ms 级放大

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/message/DBMessageService.java:337-364`（pollTopic 投递-失败-releaseClaim 循环）、`:360-363`（异常 → releaseClaim 回 PENDING）、`:604-605`（ConsumeLater → releaseClaim）、`:96-97`（50ms / batch 10 默认）
- **证据片段**:
  ```java
  // DBMessageService.java:355-363 —— 失败仅回 PENDING，下轮 50ms 后原样重投
  try {
      Object message = deserializeFromDb(row.messageBody);
      Object result = consumer.onMessage(topic, message, context);
      handleConsumerResult(row.sid, result);
  } catch (Exception e) {
      LOG.error("nop.ai.agent.message.db-deliver-error:sid={}, topic={}", row.sid, topic, e);
      releaseClaim(row.sid);
  }
  ```
- **严重程度**: P1
- **并发场景**: 单 `db-message-poller` 线程按 `scheduleWithFixedDelay(50ms)` 驱动；多实例共享 DB 时各实例独立竞争同一毒消息。
- **触发时序**: T1 反序列化即抛（schema 演进后旧 payload）或 consumer 对某消息恒抛 → `releaseClaim` 置回 PENDING；T2 下一轮 `findPending` 按 `ORDER BY CREATED_AT LIMIT 10` 取批——最老毒消息**必然**在批内 → claim → 抛 → release；T3 每 50ms 重复 T2：单条毒消息 = 20 次/秒 ERROR 日志 + 每轮消耗 1/10 批位；T4 累积 ≥10 条毒消息（如一次 schema 变更批量劣化历史消息）后批内全是毒消息——**新消息永远进不了批**，该 topic 完全停摆且无任何告警升级（错误形态与正常投递失败不可区分）。
- **现状**: at-least-once 语义（javadoc :51-57）只声明「不丢」，未声明「毒消息会无限重投」；同包 `DeferredAckMailbox` 已实现 `maxDeliveryAttempts=5` + DEAD_LETTERED 终态（:38-39, :209-231），DB 传输是该防护的 F3 漏修兄弟——两条传输路径的毒消息防护能力不对称。
- **风险**: 故障注入或 schema 演进后消息队列从「延迟」退化为「永续高频错误循环 + 队列停摆」；日志放大直接冲击采集管道；停摆无独立可观测信号（只能靠人肉 grep 日志频率发现）。
- **建议**: 表加 `DELIVERY_COUNT`/`NEXT_RETRY_AT` 两列：claim 时 `DELIVERY_COUNT=DELIVERY_COUNT+1`、失败时置指数退避的 `NEXT_RETRY_AT`、超阈值（对齐 DeferredAckMailbox 的 5）置 `STATUS=DEAD_LETTERED(30)` 终态并 WARN 一次；`findPending` 增加 `NEXT_RETRY_AT <= now` 谓词。形态直接复用同包 `DeferredAckMailbox` 的状态机。
- **失效面枚举**: (a) `pollTopic` 同步抛——**缺陷点**；(b) `handleConsumerResult` CompletionStage 异常分支（:586-589）——`releaseClaimQuietly` 回 PENDING，**同缺陷**；(c) `ConsumeLater` 分支（:604-605）——consumer 主动重试语义，**同缺陷**（无上限重试）；(d) `DeferredAckMailbox`——deliveryCount + dead-letter 齐备，**已覆盖**；(e) `LocalMessageService`（平台 nop-kernel 侧）——不属本模块审计面，列为对照。
- **信心水平**: 确定
- **误报排除**: 非「at-least-once 本来就允许重复投递」：缺陷不是重复投递，而是**无收敛的重投循环 + 队首阻塞停摆**，且同包内存信箱已有反例证明防护是本模块既定标准；亦非把 F9 泛化——这里异常被记录（不吞），缺的是重试上限。
- **复核状态**: 已保留(P1，条件面)｜新发现——回码核验成立：pollTopic `catch (Exception) → LOG.error + releaseClaim` 回 PENDING 直核（:355-363）；findPending `ORDER BY CREATED_AT LIMIT ?` 直核（:378-379）——最老毒消息必然在批内的队首阻塞论证成立；50ms/maxBatch 10 默认直核（:96-97）；DeferredAckMailbox maxDeliveryAttempts + DEAD_LETTERED 对照面直核（DeferredAckMailbox.java:39-55）——同包双传输毒消息防护不对称（F3）成立。条件面披露（messenger/DB 传输为部署方显式装配，缺省 NoOpAgentMessenger），不降档依据 C3-021 判例。工单归并：同意 C3 复核建议——C3-021/027 ↔ CL-040/C7-025 并为 DBMessageService 治理工单

### [C7-026] LocalAgentMessenger reply-topic 订阅只增不删 × DBMessageService 全 topic 轮询——DB 查询负载随历史 sender 会话数线性单调增长

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/message/LocalAgentMessenger.java:57,124-133`（replySubscriptions 无任何 remove）、`message/DBMessageService.java:315-329`（pollAllTopics 每 50ms 遍历全部 topic）
- **证据片段**:
  ```java
  // LocalAgentMessenger.java:124-133 —— 只查重与 put，全文件无 remove/cancel 调用
  IMessageSubscription existing = replySubscriptions.get(replyTopic);
  if (existing != null) { return; }
  synchronized (replySubscriptions) {
      if (replySubscriptions.get(replyTopic) == null) {
          IMessageSubscription subscription = messageService.subscribe(replyTopic, new ReplyDemultiplexer());
          replySubscriptions.put(replyTopic, subscription);
  // DBMessageService.java:319 —— 每轮对每个 topic 各发一次 SELECT
  for (String topic : consumers.keySet()) { ... pollTopic(topic); }
  ```
- **严重程度**: P1
- **并发场景**: messenger 单例（装配级）被全部 agent 会话共享；`replyTopic = replyTopic(senderId)`（`AgentMessageTopics.replyTopic`），senderId = 各 call-agent 发起会话的 sessionId（`CallAgentExecutor.java:307-309`）；DB 传输时轮询线程周期触达全部订阅。
- **触发时序**: T1 每个新会话首次 call-agent → `ensureReplySubscription` 创建一条**永久**订阅（map 全文件无删除路径，`request` 超时/完成只清 `pendingRequests` 不清订阅）；T2 进程长跑下订阅数 = 历史 distinct 发起会话数，单调增长；T3 `DBMessageService.pollAllTopics` 每 50ms 对**每个**积累的 reply topic 各执行一条 `SELECT … WHERE TOPIC=? AND STATUS=?`——N 个历史会话后稳态负载 = 20×N 查询/秒，DB 与轮询线程同时劣化；T4 轮询线程单轮时长随 N 线性拉长 → 固定 delay 被拉长 → 全部 topic（含活跃 topic）的投递延迟同步上升。
- **现状**: 订阅与请求生命周期解耦是有意设计（共享订阅 demux），但「reply topic 与发起会话同生命周期、会话终态后订阅失效」这一回收面完全没有实现；`IMessageSubscription.cancel` 存在且 `DbSubscription.cancel` 可用，仅无人调用。
- **风险**: 长时运行必然触发的无界增长（校准 P1 档）；以周计的运行 + 千级会话量即达 2 万查询/秒的无效负载；轮询线程饱和后**所有**信箱消息（含 call-agent 请求本身）延迟劣化，形成自我放大的慢性病。
- **建议**: (1) `pendingRequests` 清空且该 replyTopic 无在途请求时延迟注销订阅（引用计数 + idle TTL，如 5 分钟无 pending 即 `subscription.cancel()` + map remove）；(2) 或改为单一共享 reply topic + correlationId demux（牺牲 per-sender 隔离，需评估跨会话窃听面）；(3) `DBMessageService.pollAllTopics` 对连续空闲 topic 增加自适应轮询间隔（空结果翻倍至上限）。
- **失效面枚举**: (a) `LocalAgentMessenger.replySubscriptions`——**缺陷点**；(b) `pendingRequests`——whenComplete 清理（:116），**已覆盖**；(c) `DBMessageService.consumers`——`DbSubscription.cancel`→`unregisterConsumer` 有删除路径，**接口已覆盖**（缺的是 (a) 侧调用）；(d) `InMemoryActorRuntime` 信箱——runtime 包 X2 分区，不重复登记。
- **信心水平**: 确定
- **误报排除**: 非把「缓存复用」误报为泄漏：泄漏点是订阅条目数量随历史会话单调增长且每个条目都转化为持续 DB 轮询负载（非静态配置量级）；`subscribe` 按 topic 覆盖写不产生重复条目也不减少条目数。
- **复核状态**: 已保留(P1)｜新发现——回码核验成立：ensureReplySubscription put-only DCL、全文件无 cancel/remove 直核（:124-133）；pollAllTopics 每 50ms 对 consumers.keySet() 全量逐 topic SELECT 直核（:313-329）——20×N QPS 乘法链与轮询线程拉长反馈环成立。三重登记核对意见：C3-024(P1 保留)/C4-016(P2 分叉登记，倾向 P1)/C5-018(P2)——C7 侧意见：**统一 P1**（「长时运行必然触发的无界增长」校准直录 + 每订阅固化为 20 QPS 常驻 DB 轮询的确定性退化；C4-016 的 P2 为同站孤例）；条件面披露同 C7-025（messenger 装配面，不降档依据 C3-021 判例）。7.1 并单（倾向 C3-024 为主处置）

### [C7-027] DefaultBackgroundTaskRegistry.tasks 只增不删——终态后台任务条目（含 8KB 输出尾）永久驻留进程内存

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/tool/DefaultBackgroundTaskRegistry.java:59,98-102`（append-only map，全类无 remove/evict/TTL）
- **证据片段**:
  ```java
  // DefaultBackgroundTaskRegistry.java:59,100 —— 唯一写入路径，无对应删除路径
  private final ConcurrentHashMap<String, Entry> tasks = new ConcurrentHashMap<>();
  tasks.put(taskId, new Entry(taskId, toolName, sessionId, callId, timeoutMs));
  ```
- **严重程度**: P1
- **并发场景**: 引擎级单例 registry（Builder-owned），ReAct 工具分发线程 `submit`、后台 fork 线程 `complete`、任意会话的 `task-output` 读线程并发触达。
- **触发时序**: T1 每次 `run_in_background` 工具调用 `submit` 一条 Entry；T2 任务终态（COMPLETED/FAILED/TIMEOUT）后 `complete` 只改状态字段，条目永久保留（`size()` 与 `rawStatus` 是仅有的观测口，无清理 API）；T3 长跑进程上条目数 = 历史后台任务总数，每条含 taskId/toolName/sessionId/callId 字符串 + 至多 8KB `outputTail`（:48, :172-179）+ CountDownLatch + Future 引用（持有后台线程栈引用直至线程复用）——单调增长无任何收敛点。
- **现状**: registry 提供了完整的终态机与幂等语义，但接口（`IBackgroundTaskRegistry`，nop-ai-toolkit）根本没有 `remove(taskId)`/TTL 形态的方法；`snapshot==null` 的 fail-loud 语义（TaskOutputExecutor「registry was restarted」分支）说明作者模型里存在「条目消失」态，却没有实现它的路径。
- **风险**: 校准 P1 档「长时运行必然触发的无界增长」：高频 agent 工作流（每会话数十个后台任务）× 周级运行 = 数十万条 × ~8KB = GB 级驻留；且 `Entry.future` 引用使已完成任务的线程栈帧（若线程池线程阻塞在任务相关引用上）延迟可回收性。
- **建议**: (1) 终态后延迟回收：`complete` 里 `scheduledExecutor.schedule(() -> tasks.remove(taskId), retentionMs)`（默认 1h，保留 task-output 的合理读取窗口）；(2) 或容量上限 + 最旧终态条目驱逐（终态优先于运行态驱逐）；(3) 接口加 `evictTerminalBefore(long ts)` 供运维兜底。
- **失效面枚举**: (a) `DefaultBackgroundTaskRegistry.tasks`——**缺陷点**；(b) `pendingRequests`（LocalAgentMessenger）——有 whenComplete 清理，**已覆盖**；(c) `inFlightDispatches`（TaskDispatchCoordinator）——whenComplete 移除（:258-270），**已覆盖**；(d) `DefaultHookRegistry` 注册表——配置期写入，非请求期增长，**不适用**。
- **信心水平**: 确定
- **误报排除**: 非 F4 的持久化账本（本条是纯内存），但同属「只增不删」族；非误报校准中「有界声明的缓存」——`OUTPUT_TAIL_LIMIT` 只界单条体积，不界条目数，无任何声明覆盖条目总数。
- **复核状态**: 已保留(P1)｜新发现——回码核验成立：tasks 唯一写入口 submit-put、全类无 remove/evict/TTL（grep 直核）；Entry 含 ≤8KB outputTail 直核。缺省装配直核（DefaultAgentEngine:654 customRegistry 空兜底 new，C5-021 复核同证）。四维登记核对意见：C3-023(P1)/C4-012(P1，主处置——另含 timeoutMs 无执行点 + F5 面)/C5-021(P2)/C1-017——C7 侧意见：维持 P1（缺省装配 + 校准「长时运行必然触发的无界增长」直录），主处置归 C4-012、本条随单吸收；跨单元同族 WI 1.1 C1-006(P1) 判级一致

### [C7-028] 会话终结协议不收敛——终态会话永久驻留 store（内存三 map 无释放、盘/DB 行无归档、启动恢复全量扫描成本随历史单调增长）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/session/FileBackedSessionStore.java:106-122`（sessions/persistedStates/sessionLocks 三 map）、`session/DBSessionStore.java:79-93`（同构三 map，租户模式 persistedStates 照样增长）、`engine/AgentSessionLifecycle.java:413-424`（终态清理只触达 checkpointManager）
- **证据片段**:
  ```java
  // AgentSessionLifecycle.java:417-424 —— 终态路径只删 checkpoint，从不触达 sessionStore
  if (isTerminalStatus(session.getStatus())) {
      config.getCheckpointManager().remove(sessionId);
  }
  // FileBackedSessionStore.java:106-112 —— 三个 per-session map，remove() 是唯一删除路径且无生产调用方
  private final ConcurrentHashMap<String, AgentSession> sessions = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, SessionPersistenceState> persistedStates = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Object> sessionLocks = new ConcurrentHashMap<>();
  ```
- **严重程度**: P2（行为面；内存面归 C5 维度，此处登记行为收敛缺口）
- **并发场景**: 终态清理跑在 ReAct worker 线程；store 缓存被后续任意 `get`/`getOrCreate`（HTTP/actor/messaging 入口）触达；启动期 restore 编排单线程扫描。
- **触发时序**: T1 会话到达终态（completed/failed/…）→ 生命周期保存终态 + 删 checkpoint——`sessionStore.remove` 在引擎主源码中**零调用方**（全模块 grep 仅 spill/memory 的同名方法命中）；T2 `FileBackedSessionStore.sessions`（完整消息历史对象）、`persistedStates`（每消息一个 fingerprint Long）、`sessionLocks` 各留一份，直到进程结束；T3 `DBSessionStore` 租户模式下缓存被绕过但 `persistedStates`/`sessionLocks` 同样按 distinct 会话累积；T4 进程重启 → `restorePendingSessions` → `listAllSessions` 全表/全目录反序列化**所有**历史终态会话（含每会话 journal 重放 + 全量指纹重算）——启动时间与历史会话总数线性相关，无归档/TTL 机制把终态会话移出扫描集。
- **现状**: 「终态持久化供审计/恢复」是已声明特性（终态先存后清 checkpoint 的注释即为此设计），但「终态会话永不退出工作集」未被声明：内存缓存 javadoc 自述仅「for read performance」，盘/DB 侧无任何保留期/归档路径，restore 扫描无终态过滤谓词。
- **风险**: 高会话流失率的长跑部署下：内存驻留（C5 面）+ 启动恢复扫描时间随历史线性劣化（行为面）+ 盘/DB 无界增长；`listAllSessions` 反序列化一个损坏行仅 WARN 跳过，但**损坏行也永远留在扫描集里**，每次重启重复 WARN。
- **建议**: (1) 终态 hook 上给 store 增加可选 `releaseTerminal(sessionId)`（内存三 map 移除，盘/DB 行保留——缓存与持久化分离，读路径退化为 lazy reload，已是现成能力）；(2) `listAllSessions`/DB 扫描加 `STATUS NOT IN (terminal)` 或 `UPDATED_AT > cutoff` 谓词（两 store 的行均已含 STATUS 列，DB 侧一列索引即可）；(3) 补终态会话归档 runbook（导出后删除）文档。
- **失效面枚举**: (a) `FileBackedSessionStore` 三 map——**缺陷点**；(b) `DBSessionStore` sessions/persistedStates/sessionLocks——**缺陷点**（租户模式 sessions 被绕过，另两 map 仍增长）；(c) `InMemorySessionStore.sessions`——**缺陷点**（存储即缓存，终态语义上仍无回收）；(d) `checkpointManager`——终态已 remove（X2 C7-013 登记其 DB 后端 remove 空转的兄弟缺陷）；(e) write-intent registry——终态已 `releaseSession`（AgentSessionLifecycle.java:393 等），**已覆盖**。
- **信心水平**: 确定（零调用方事实）；增长速率评级依赖会话流失率（很可能）
- **误报排除**: 非「持久化存储天经地义无限增长」：盘/DB 增长只作风险陈述，定级依据是**内存缓存 + 启动扫描**这两个可避免的非持久化劣化面；亦非与 X2 C7-017 重复——那条是 engine/reliability 的治理注册表，本条是 session 包 store 自身，且 X2 批次明确将 session 包划归本批。
- **复核状态**: 已保留(P2)｜新发现——回码核验成立：FileBackedSessionStore 三 map（sessions/persistedStates/sessionLocks）put-only 直核（:106-122）；模块级 grep 复验 sessionStore.remove 零生产调用方（仅 checkpoint 族 .remove 命中）；终态清理只触达 checkpointManager 直核（AgentSessionLifecycle:417-424）。分账核对：C5-015(P1 族首，内存面)/C5-016(P2 恢复全量扫描面)/本条 P2（终态协议行为收敛面）——三面分工清晰无重复计数，维持 P2；会话终结协议族修复以 C5-015 为族首并单（C5 复核已建议，C7 侧同意）

### [C7-029] 周期任务只捕 RuntimeException/Exception——Error 逃逸使 scheduleWithFixedDelay 永久静默停摆（扫描守护与消息轮询双兄弟）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/scheduler/TeamTaskSchedulerDaemon.java:262-276`（scanOnceSafe catch RuntimeException）、`message/DBMessageService.java:315-329`（pollAllTopics 逐 topic catch Exception；:213,217 两处 scheduleWithFixedDelay）
- **证据片段**:
  ```java
  // TeamTaskSchedulerDaemon.java:272-275 —— Error 不在捕获面内
  } catch (RuntimeException e) {
      LOG.warn("TeamTaskSchedulerDaemon: periodic scan failed (will retry next interval): {}", e.toString());
  }
  ```
- **严重程度**: P2
- **并发场景**: `IScheduledExecutor`/`ScheduledExecutorService` 的单守护线程执行周期任务；Error 可来自任务链任意层（JDBC 驱动静态初始化、`NoClassDefFoundError`、栈深依赖输入的 `StackOverflowError`、堆耗尽 `OutOfMemoryError`）。
- **触发时序**: T1 扫描/轮询任务执行中抛出 Error（如 DB 驱动类在元空间回收后重载失败）；T2 `scheduleWithFixedDelay` 契约：任务抛出任何 Throwable 即**抑制全部后续执行**，异常仅存入无人观察的 Future（`scheduleHandle` 只被 cancel，从不 `get`）；T3 daemon/轮询线程静默消失——无日志、无指标、无重启；T4 团队任务永不再派发 / DB 消息永不再投递，系统表现为「莫名停摆」，与 F5「不产出也不报错」挂死形态完全一致。
- **现状**: 两个周期入口的防抛包装只覆盖受检异常族；`sweepStaleClaimedMessagesSafely`（:572-581，catch Exception）同型。平台 `ThreadHelper`/`IThreadPoolExecutor` 有捕获包装惯例，但 `scheduleWithFixedDelay` 的抑制语义只能靠任务自身兜 Error。
- **风险**: 罕见但后果为「静默永久失去调度能力」且无自愈；长时运行下 Error 概率随堆/元空间老化上升；停摆后 stale-claim 清扫同死，CLAIMED 消息/任务双滞留。
- **建议**: 三处包装改为 `catch (Throwable t)` 内对 Error 分支 `LOG.error(...) + 可选 health-flag` 后吞并以保住调度（对照 F5：失败必须可见）；或包一层 `try { run() } catch (Throwable) { log; }` 的 `SafeRunnable` 装饰器统一应用；同时给 `scheduleHandle` 增加周期性活性探测（上次成功扫描时间戳心跳）。
- **失效面枚举**: (a) `TeamTaskSchedulerDaemon.scanOnceSafe`——**缺陷点**；(b) `DBMessageService.pollAllTopics`——**缺陷点**（外层无防抛，逐 topic catch Exception）；(c) `DBMessageService.sweepStaleClaimedMessagesSafely`——**缺陷点**（catch Exception）；(d) X2 `ScheduledRecoveryManager.scanOnceSafe`——X2 挂死点清单 #8 已按「每 60s 重试可观测」判定，但同样未防 Error，复核阶段应按本条同族复查；(e) `InMemoryActorRuntime` poll 循环——X2 分区，不重复登记。
- **信心水平**: 确定（机制）；具体触发链依赖 Error 场景（很可能）
- **误报排除**: 非「catch Exception 已足够」的风格意见：`ScheduledExecutorService` 抑制后续执行的语义使未捕获 Error 从「该次失败」升级为「守护永久死亡」，是行为缺陷非性格问题；亦非要求捕获 OOM 后继续运行的一般化主张——此处守护线程不持有大状态，吞 Error 保调度 + 告警是合理恢复面。
- **复核状态**: 已保留(P2)｜新发现——回码核验成立：scanOnceSafe `catch (RuntimeException)` 直核（:272-275）；pollAllTopics/sweepStaleClaimedMessagesSafely `catch (Exception)` 直核（:325/:578）；scheduleWithFixedDelay Throwable 抑制契约 + scheduleHandle 无读取点直核。同站指针：C4-013(P2 保留，站点完全重合)/C3-027(P3 保留)——判级分叉（P2/P2/P3）留 7.1；C7 侧意见：C4-013 的 P2 论证（消息投递心跳 + 团队调度双静默停转、stale-claim 安全网随 poller 同死）成立，倾向 P2；修复**不独立立项**——并入 WI 2.1 C3-001 平台周期任务绝缘层（C4-013 已登记该归属，C7 侧同意）

### [C7-030] memory provider per-session store 注册表无终态清理——C7-017 治理注册表族的 memory 包兄弟

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/memory/InMemoryMemoryStoreProvider.java:22-31`、`memory/AdapterBackedMemoryStoreProvider.java:44-70`（stores map 仅 computeIfAbsent，无 remove API）
- **证据片段**:
  ```java
  // InMemoryMemoryStoreProvider.java:22,27-30 —— 仅增无删
  private final ConcurrentHashMap<String, InMemoryAiMemoryStore> stores = new ConcurrentHashMap<>();
  public IAiMemoryStore getOrCreate(String sessionId) {
      return stores.computeIfAbsent(sessionId, k -> new InMemoryAiMemoryStore());
  ```
- **严重程度**: P2
- **并发场景**: provider 为引擎级单例（ReActAgentExecutorBuilder 默认装配）；各会话的 ReAct worker / memory 工具线程并发 `getOrCreate`。
- **触发时序**: T1 每个 distinct sessionId 首次触达 memory 工具（read/write/search-memory）→ 创建一个 `InMemoryAiMemoryStore`（或 AdapterBacked 组合）放入 map；T2 会话终态——引擎终态清理清单（checkpoint / write-intent / 治理注册表族）**不包含 memory provider**，接口亦无 `remove(sessionId)`；T3 条目连同其内全部 memory item（单 store 内保留无上界——javadoc :32-34 自声明 deferred）驻留至进程结束；T4 高会话流失率下 store 数随历史会话数单调增长。
- **现状**: 单 store 内「无 retention/TTL」已由 javadoc 显式声明（按口径规则 6 不立发现）；**未声明**的是 provider 层 per-session 条目在会话终态后无任何释放路径——与 X2 C7-017（GoalTracker/WaitCoordinator/DenialLedger）完全同构的「会话级注册表无终态清理」族。
- **风险**: 内存面（每条目 = 空 store 起步、随工具使用增长）归 C5 维度量化；本维度登记的是终态清理协议缺口：引擎侧有集中的终态清理点却未纳管该注册表，后续任何 per-session 资源（X2 三件套、本条、C7-028 store 缓存）各自为战，漏一个就是一个泄漏源。
- **建议**: `IMemoryStoreProvider` 增加 `releaseSession(String sessionId)` 默认空实现，引擎终态清理点（`AgentSessionLifecycle` 的 isTerminalStatus 分支）与 write-intent `releaseSession` 并列调用；两 provider 实现各自 remove。
- **失效面枚举**: (a) `InMemoryMemoryStoreProvider.stores`——**缺陷点**；(b) `AdapterBackedMemoryStoreProvider.stores`——**缺陷点**（同构）；(c) `NoOpStorageAdapter/NoOpVectorAdapter/NoOpEmbeddingAdapter`——无状态单例，**不适用**；(d) X2 已登记的 GoalTracker/WaitCoordinator/DenialLedger——同族，**已登记**（C7-017）；(e) `InMemoryTeamManager.sessionToTeamId`——per-member 覆盖写 + removeMember 清理，**有界**。
- **信心水平**: 确定
- **误报排除**: 非重复报告已声明边界：声明的是「store 内 item 无 retention」，本条是「provider 内 store 条目无终态释放」——两层生命周期，前者 declared、后者未声明且引擎已有终态清理惯例可循。
- **复核状态**: 已保留(P2)｜新发现——回码核验成立：InMemoryMemoryStoreProvider.stores computeIfAbsent 无 remove 直核（:22-31）；AdapterBackedMemoryStoreProvider.stores 同构直核（:51/:76）。缺省装配直核（config:126 `new InMemoryMemoryStoreProvider()`）。同站三登记核对：C5-020(P2)/C1-016(P2)——判级收敛，维持 P2；归并修复（接口加 releaseSession 默认实现 + 引擎终态清理点并列调用）

### [C7-031] 会话内大内容归档三件套无上界——spill store / compaction 快照归档 / 引用归档在长会话内单调累积最大体积内容

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/compact/InMemorySpillStore.java:29-49`（超限工具结果原文明文，无容量/驱逐）、`session/InSessionCompactionSnapshotArchive.java:39-56`（每次压缩存全量 pre-compaction 消息列表副本，无驱逐）
- **证据片段**:
  ```java
  // InMemorySpillStore.java:47 —— put 无任何容量判断
  store.put(spillId, content);
  // InSessionCompactionSnapshotArchive.java:54-55 —— 每次压缩事件存一份全量防御性副本
  store.put(snapshotId, List.copyOf(messages));
  ```
- **严重程度**: P2
- **并发场景**: spill 写入方为工具分发线程（`AgentToolDispatcher` 溢出路径），读取方为 `read-spill` 工具线程；快照归档写入方为压缩协调器，均在会话生命周期内跨线程触达（HashMap 非线程安全见 CL-070）。
- **触发时序**: T1 会话内工具反复产出超限结果 → 每个 spill id 把**完整原文**（触发溢出即大体积）存入 session 级 HashMap，`delete` 仅 read-spill 手动路径可达、无自动回收；T2 长会话每触发一次上下文压缩 → `List.copyOf(全量消息)` 存入快照归档且永不清理——第 k 次压缩时会话已含 k-1 份更大历史；T3 会话存活期间（长时运行 agent 场景：常驻运维会话/长任务会话）两 map 单调增长，峰值内存 ≈ 历次溢出原文总量 + Σ(每次压缩时历史总量)——后者随压缩次数二次增长。
- **现状**: 「session 级生命周期、会话结束即回收」已声明（两 javadoc 均明示）；**未声明**的是会话**存活期内**无容量上限/驱逐——spill 的存在意义就是承接「放不进上下文的大内容」，却无任何预算约束；快照归档「审计可回放」语义未声明保留份数（保留全部 vs 最近 N 份未裁定）。
- **风险**: 长会话 × 大工具输出（bash/文件类工具天然 MB 级）→ 会话内存峰值失控并加速触发 C5 维度的包络外风险；压缩频率越高（上下文越紧张）快照累积越快，与压缩的降负目的背反。
- **建议**: (1) `InMemorySpillStore` 加总字节预算（如 `CompactConfig` 注入 maxSpillBytes，超限拒新 spill 并 fail-loud 提示先 read-spill/delete）；(2) 快照归档保留最近 N 份（如 3）或按总字节上限驱逐最旧；(3) 引用归档（`InSessionCompactionArchive`）因内容寻址去重风险较低，随 (1)(2) 顺带核查即可。
- **失效面枚举**: (a) `InMemorySpillStore.store`——**缺陷点**；(b) `InSessionCompactionSnapshotArchive.store`——**缺陷点**；(c) `InSessionCompactionArchive.store`——内容寻址去重，增长慢一档，**同族随行核查**；(d) `InMemoryAiMemoryStore.items`——retention 缺失已声明（javadoc :32-34），**不立发现**（provider 层见 C7-030）。
- **信心水平**: 确定（无上界事实）；峰值量级依赖工具输出分布（很可能）
- **误报排除**: 非「会话级生命周期即有界」：长时运行 agent 的会话可持续天/周（本维度对象），会话级=进程级；亦非重复 C5——C5 审消息列表与全局缓存包络，本条是 compact 包归档设施自身的无预算缺陷，且按分区归属本批。
- **复核状态**: 已保留(P2)｜新发现——回码核验成立：InMemorySpillStore `store.put(spillId, content)` 无容量判断直核（:47，plain HashMap）；InSessionCompactionSnapshotArchive `store.put(snapshotId, List.copyOf(messages))` 每次压缩全量副本无驱逐直核（:54-55）。同链分账核对：C5-010(P1，快照归档子面——触发线 0.8×128k tokens/30 messages 极低 + 终态不清档)/C5-011(P2，spill 子面)——C7-031 为三件套复合登记（含引用归档随行核查面），复合评级 P2 与子面判级并存：7.1 按子面拆分归并（快照归档随 C5-010 P1、spill 随 C5-011 P2），本条作为族清单不独立定级修复

### [C7-032] TeamExecuteFlowExecutor 每次调用新建 orchestrator 且从不 close()——ownedSpawnExecutor 固定线程池按调用次数泄漏

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/tool/TeamExecuteFlowExecutor.java:241-244`（per-invocation 构造，全文件无 close）、`team/flow/TeamTaskFlowOrchestrator.java:448-458,468-478`（懒创建 ownedSpawnExecutor，close() 为唯一释放路径）
- **证据片段**:
  ```java
  // TeamExecuteFlowExecutor.java:242-244 —— 每次工具调用一个新 orchestrator
  TeamTaskFlowOrchestrator orchestrator = new TeamTaskFlowOrchestrator(
          engine, taskStore, teamManager, null, memberSpawner, null, memberExecTimeoutMs);
  // TeamTaskFlowOrchestrator.java:448-450 —— 释放只能靠 close()，主源码零调用方
  public void close() {
      if (ownedSpawnExecutor != null) { ownedSpawnExecutor.shutdownNow();
  ```
- **严重程度**: P2
- **并发场景**: 每次工具调用在 ReAct 工具分发线程新建 orchestrator；含 spawn 节点的 DAG 在 `resolveSpawnExecutor` 懒创建 `Executors.newFixedThreadPool(max(2, CPUs), daemonFactory)`（:473-475）。
- **触发时序**: T1 LLM 调用 team-execute-flow 且路由计划含 SPAWN 目标 → orchestrator 懒创建固定池（核心线程随任务提交逐个创建、空闲常驻）；T2 DAG 完成，orchestrator 对象被丢弃——`close()` 在 tool/team 主源码中**零调用方**（仅 javadoc 建议长寿命持有者调用）；T3 池线程为 daemon 不会阻止 JVM 退出，但在进程存活期内永久驻留（fixed pool 核心线程 idle 不回收）；T4 每次含 spawn 的调用泄漏至多 CPU 数个线程——LLM 反复试错重试该工具时线程数按调用次数阶梯单调增长。
- **现状**: plan 243 裁定 3 明确要求「owned pool released by close()」——释放契约已声明且 API 已实现，唯一生产装配路径未接；注入式 `setSpawnStepExecutor` 路径无此问题（所有权归注入方）。
- **风险**: 线程为 daemon 故不致命，但每线程 1MB 栈预留 + 调度器扫描开销随泄漏增长；线程数是排障者最常看的健康指标，幽灵线程积累直接干扰容量评估；与 C4 维度 CL-064（无界 agentExecutor）叠加时线程指标失真加剧。
- **建议**: (1) 最小修：`flowFuture.whenComplete((r, e) -> orchestrator.close())` 挂在工具返回链上；(2) 或把 spawn 池提升为 executor 单例会话级共享（tool 持有），避免 per-invocation 创建语义；(3) `resolveSpawnExecutor` 改用核心线程超时回收（`allowCoreThreadTimeOut`）作为纵深防御。
- **失效面枚举**: (a) `TeamExecuteFlowExecutor` per-invocation 路径——**缺陷点**；(b) `TeamTaskSchedulerDaemon.stop()`→`dispatchCoordinator.shutdownOwnSpawnExecutor()`——daemon 路径已有关停钩子（:257），**已覆盖**（前提是 stop 被调用，daemon 生命周期归装配方，与 CL-059 同型）；(c) 注入 spawnExecutor 的两个装配——所有权明确，**不适用**；(d) 测试直用 orchestrator 不 close——daemon 线程，测试环境可接受，**不立发现**。
- **信心水平**: 确定
- **误报排除**: 非「daemon 线程无需关停」的过度报告：JVM 退出语义没问题，缺陷是**进程存活期内**的资源按调用次数单调增长，与类自身 javadoc 承诺的「released by close()」不符；泄漏实体（线程池）由工具每次调用重复制造，非一次性装配残留。
- **复核状态**: 已保留(P2，分叉登记)｜新发现——回码核验成立：per-invocation `new TeamTaskFlowOrchestrator(...)` 直核（实测 :234，初审 :241-244 存在约 8 行行号漂移，不影响事实）；全文件无 close() 调用（grep 直核）；ownedSpawnExecutor 懒创建 fixed 池 + close 为唯一释放路径直核（TeamTaskFlowOrchestrator:448-478）。**复核新增同站指针（任务指派未列，本复核补登记）**：同站点 C3-022(P1 保留)/C4-010(P1 保留)——三案同站，判级分叉（C7 P2 vs C3/C4 双 P1）留 7.1。C7 侧意见：C4-010 的 P1 论证（auto-spawn 为文档化正常业务路径 × 每调用泄漏至多 CPU 数线程、daemon 标记不阻进程内存期内累积）可辩护，倾向与同站多数收敛 P1；按反漂移纪律不单侧改档，随 7.1 并单校准

## 维度集外线索（overlay C2/C6/C8/C9）

### [CL-055] (C2) ChatLogHelper.makeSessionId 与 ChatServiceImpl.callAsync 对共享 ChatRequest 的就地变更

`ChatLogHelper.java:38-48` 的 `makeSessionId` 在 sessionId 为 null 时直接 `request.getOptions().setSessionId(sessionId)` 回写；`ChatServiceImpl.java:133-136, 187-190` 同样就地 `setRequestTime`/`setRequestId`。同一 `ChatRequest` 若被并发提交（调用方复用对象/重放），两处写构成数据竞争（sessionId 生成、requestTime 非原子）。无运行时守卫，归属 C2 竞态面。

### [CL-056] (C2) AiCommand 实例可变字段无并发守卫

`AiCommand.java:60-73` 的 `promptTemplate/chatOptions/prevMessages/retryTimesPerRequest` 均为实例可变字段，`AiCommand.create()` 工厂暗示 per-task 实例，但类上无任何「非线程安全/单次使用」契约声明或运行时守卫（对照 F10：声明契约缺失+守卫缺失）。若被当作单例 bean 复用（NopIoC 无扫描、全靠 beans.xml 装配，装配形态决定生命周期），跨线程 set/use 竞态成立。归属 C2 生命周期核查项。

### [CL-057] (C6) StreamAggregator 聚合无上界——恶意/故障 provider 无限流式输出直通堆内存

`ChatServiceImpl.java:484-486` 的 `textBuilder/reasoningBuilder` 与 `toolCallAccumulators` 随 chunk 无上限累积，`callStream` 订阅链 `subscription.request(Long.MAX_VALUE)`（`:208`）+ 聚合端 `request(Integer.MAX_VALUE)`（`:403`）使背压全程失效——provider 不停发、连接不断开，聚合内存随流线性增长直至 OOM。单调用理论上受 provider 输出上限约束，但「故障 provider/中间层重复重放」无本地防护。归属 C6 内存包络核查项。

### [CL-058] (C6) LocalFileOperator.globGrep 无限制遍历 + grepFiles 全文件行表驻留

`LocalFileOperator.java:441` 以 `maxFileCount=0`（自注释「0表示无限制」）触发目录全遍历，结果 List 全量驻留；`grepFiles`（`:406`）对每个文件 `readLines` 全量载入后逐行匹配。大目录 × 大文件组合下单次调用内存与时长无界。与 C7-010 同根（窗口/限额参数未作用于读取面），但入口是独立公开方法，C6 维度应单独核对调用方（agent 工具）是否传入了防护性上限。

### [CL-059] (C8) EmbeddingServiceImpl 懒创建重试调度器无生命周期回收

`EmbeddingServiceImpl.java:126-142` 懒创建的 `ScheduledThreadPoolExecutor("nop-ai-embedding-retry")` 为实例级常驻线程，类未实现 destroy/@PreDestroy（setter 可注入替换但替换后旧实例不关停）。daemon 线程不阻 JVM 退出，但 bean 重建/替换场景下旧调度器连同其延迟队列（含 C7-003 的超长延迟任务）残留至进程结束。归属 C8 资源生命周期核查项（对照 nop-ai-agent `DefaultAgentEngine.close()` 的成对关停形态）。

### [CL-060] (C9) AiCommand 缓存加载失败吞异常仅 INFO 无原因

`AiCommand.java:296-301` 的 `catch (Exception e) { LOG.info("nop.ai.load-cache-fail:promptName={}", promptName); }` 丢弃 throwable（无 cause、无 message、级别 INFO）。运行时行为上降级为缓存 miss 是合理自愈，但可观测性违约使 C7-008 的毒条目形态不可诊断（排障者看不到任何解析失败证据）。F9 族（静默吞异常）在 error-handling/日志维度的登记线索：建议 WARN + cause，且该模式应 grep 全模块（本模块仅此一处 catch-吞形态）。

### [CL-061] (C2) injectInput 活目标注销先于终态持久化——注入与终态保存的丢写窗口

`DefaultAgentEngine.java:1006-1010` 的 finally 先 `runningExecutions.remove` 再 `runningInjectionTargets.remove(sessionId)`，**之后**才执行终态 `sessionStore.save`（:1045-1049）。窗口内到达的 `injectInput`（:617-623）查不到活目标 → 走 `session.addPendingInjection + save` 分支，与 worker 线程的终态 `replaceMessages + save` 并发写同一 session 对象/存储行——staged 注入可能被终态全量保存覆盖（store 的 merge 语义按字段粒度而定，session 包归属批次 X3 核实）。同时 `addPendingInjection` 若在 `session.replaceMessages` 之后执行，注入与消息列表的先后无 happens-before。建议终态保存完成后再移除 runningInjectionTargets，或注入路径改走引擎级信箱。归属 C2 竞态面。

### [CL-062] (C2) CalibratedTokenEstimator EMA 更新为 volatile double 非原子读改写

`CalibratedTokenEstimator.java:80-83` 的 `record` 执行 `factor = factor*(1-α) + observedRatio*α`，`factor` 为 volatile double——多会话并发 LLM 回执（`ReActAgentExecutor.recordLlmTurnUsage:1088` 调用点）并发更新丢失（read-modify-write 非原子）。后果仅是校准因子收敛偏慢/抖动（强制停止与压缩阈值随之漂移），无结构性破坏。归属 C2 原子性核查项，修复方向 AtomicDouble 语义（`AtomicLong` 位模式）或 per-update 加锁。

### [CL-063] (C2) CheckpointJournalWriter 单一全局 ioLock 串行化全部会话，javadoc 声明与实现不符

`CheckpointJournalWriter.java:41` 的 `private final Object ioLock` 是**实例级单锁**，`appendCheckpoint`（:63-72）在锁内做 exists 检查 + 文件写——所有会话的 checkpoint journal 追加在此串行（写发生在 ReAct 热路径上，`AgentToolDispatcher.commitToolResults:819` / `ReActAgentExecutor.saveLlmTurnCheckpoint:1116`）。类 javadoc（:29-31）声称 "per-file lock…Cross-session writes target independent files and do not contend"，与实现相悖：一个慢盘写入阻塞**全部**会话的 checkpoint 落盘。锁粒度/热路径争用归 C2/C4 维度；另注意 `Files.write(APPEND)` 无 fsync（F6 面，checkpoint 属 best-effort 可接受，但声明应明示）。

### [CL-064] (C4) agentExecutor 无界 cachedThreadPool 兜底 LLM 超时任务——超时后任务继续占用 worker

`DefaultAgentEngine.java:469-479` 懒创建 `newCachedThreadPool`（无上界）兼任执行池与 `timeoutExecutor`（`AgentExecutorResolver.java:207` 注入）；`LlmCallCoordinator.callChatWithTimeout:789-808` 超时后 `f.cancel(true)`——线程中断不中止底层 HTTP 读（X1 C7-001 已证 JDK 路径 body 无读超时），每个超时的 LLM 调用占住一个 agentExecutor 线程直至底层调用自然结束（挂死形态下永久占用）。`SingleTurnExecutor.java:41-43` 默认构造更直接用 `ForkJoinPool.commonPool()` 作 timeoutExecutor，超时泄漏共享公共池线程。反复超时 × 无界池 = 线程数随故障时长单调增长。归属 C4 线程池维度（与 X1 C7-001 构成跨模块放大链）。

### [CL-065] (C6) steering 注入队列无界 ConcurrentLinkedQueue——injectInput/actor 路径无上限入队

`AgentExecutionContext.java:87` 的 `steeringQueue = new ConcurrentLinkedQueue<>()` 无容量上限：`injectInput`（引擎 API，:603-624）与 actor 消费循环（`InMemoryActorRuntime.java:465`）均可无限入队；消费仅发生在轮次边界/等待期 wakeup，会话长时间阻塞在单个工具上时注入积压无背压、无上限告警（仅 INJECTION_QUEUE_UPDATED 事件的 PENDING_COUNT 可观测，:332）。恶意/失控调用方可持续注入撑大队列。归属 C6 内存包络核查项（对照平台 `HighWatermarkSemaphore`/`QueueOverflowPolicy` 设施未用）。

### [CL-066] (C8) DefaultWaitCoordinator TIMEOUT 延迟唤醒任务不可取消——长 deadline 任务在调度器堆积

`DefaultWaitCoordinator.java:89-95` 的 `requestWait` 为 TIMEOUT 条件 `scheduler.schedule(deliverWake, delay)` 但不保留 handle：会话被 `deliverWake` 提前唤醒后，到期任务照常触发（对已消费条件无害，`checkWait` 见 condition==null 返回 none），但延迟任务在调度器队列驻留至 deadline——长 deadline（小时/天级 WAIT_FOR）× 高频 wait 请求 = 队列单调膨胀，且引擎 `close()` 不会清理该调度器（外部注入，责任在装配方）。归属 C8 资源生命周期核查项：建议保留 ScheduledFuture 并在消费/替换条件时 cancel。

### [CL-067] (C2) sessionLocks 在 remove() 中持锁自删——锁身份更换窗口使并发 save 失去串行化

`FileBackedSessionStore.java:177-203` 与 `DBSessionStore.java:198-235` 的 `remove(sessionId)` 在持有 `sessionLock(sessionId)` 的情况下把锁对象从 `sessionLocks` map 移除：T1 save-A 阻塞在旧锁对象上（remove 前 `sessionLock()` 已返回旧锁）→ T2 remove 持锁删文件并 `sessionLocks.remove` → T3 新的 save-B `sessionLock()` computeIfAbsent 生成**新锁对象**直接进入——A 与 B 持不同锁并发写同一会话（journal 追加交错/丢 delta）。缓解事实：主源码中 `sessionStore.remove` 当前**零生产调用方**（见 C7-028），故为理论风险；一旦按 C7-028 建议接入终态 release 路径，该竞态即变为可达——修复方向：remove 不删锁条目（锁 map 归 C7-028 的缓存治理统一处理），或改为 `compute` 内比较身份后删除。

### [CL-068] (C5/C9) tryAppend O(prefix) 全量 JSON 序列化指纹重算——声明过的机制、未声明的二次包络

`FileBackedSessionStore.java:340-346` / `DBSessionStore.java:371-377` 每次 save 对持久化前缀逐条调用 `ChatMessageFingerprints.fingerprint`（`ChatMessageFingerprints.java:41-52`：`JsonTool.stringify(message)` 全量序列化 + FNV 逐字符），fold 时 `resetAfterFold` 再全量重算一次。单会话 S 次 save × N 条消息 = O(S×N) 次 JSON 序列化，CPU 随会话历史二次增长。javadoc（ChatMessageFingerprints :23-27）已声明「deliberately traded」属已声明代价（按口径规则 6 不立发现），但声明只覆盖机制未覆盖包络：无任何文档给出「会话消息数上限/每秒 save 次数」使该代价可预算。建议 C5/C9 复核时量化：缓存消息级 fingerprint（以 message 身份 + 脏标记失效）可将单次 save 降为 O(delta)。

### [CL-069] (C2) DBMessageService claim 无世代 fencing + DbSubscription.suspend 死标志

两点同文件登记：(1) `claimMessage/markConsumed/releaseClaim`（DBMessageService.java:402-464,493-515）均无 claim 世代列——慢消费者（异步 > staleClaimTimeoutMs=5min，:107）场景：sweep 把 CLAIMED 重置 PENDING 后第二实例 re-claim 并完成，第一实例收尾时的 `releaseClaimQuietly`（:586-589）会把**别人的活跃 claim** 无条件重置 PENDING → 第三次投递；对比 team-task claim 已有 CLAIM_EPOCH 世代防护（plan 279），消息 claim 是同族未修兄弟。(2) `DbSubscription.suspend()/resume()`（:666-673）只写 `suspended` volatile 标志，`pollTopic` 从不读取——挂起的订阅照常收消息，API 语义失效（死代码）。归属 C2 核查项。

### [CL-070] (C2) 会话级 HashMap 归档被多线程触达——spill store 与快照归档非线程安全

`InMemorySpillStore.store`（compact/InMemorySpillStore.java:31，plain HashMap）的写方为工具分发线程（spill 溢出路径）、读方为 read-spill 工具线程；`InSessionCompactionSnapshotArchive.store`（session/InSessionCompactionSnapshotArchive.java:38）与 `InSessionCompactionArchive.store`（compact/InSessionCompactionArchive.java:34）同为 plain HashMap，写方为压缩协调线程、读方为 read-ref 审计路径。工具分发与压缩/读取若并发（同一会话的 worker 线程 + 工具线程形态，参照 FileBackedSessionStore javadoc :115-121 对「真实并发写者」的自述），HashMap 无同步即结构损坏风险。归属 C2 可见性核查项：需先确认工具是否真在会话 worker 之外线程执行（engine 包 X2 分区），再定级。

### [CL-071] (C6/F4) append-only 自建表无保留期——ai_agent_message CONSUMED 行、usage 行、team/team_task 行

`ai_agent_message` 的 CONSUMED 行（status=20）永不删除（DBMessageService 仅 INSERT 与状态迁移，无任何 DELETE/归档；对比 session journal 行有 fold 删除与 remove 清理）——传输型临时数据按消息总量线性增长；`DbUsageRecorder`（usage/DbUsageRecorder.java:131-176）逐 LLM 响应 INSERT `nop_ai_chat_response` 型表，无轮转；`ai_agent_team`/`ai_agent_team_task`（DbTeamManager/DbTeamTaskStore）INSERT-only，disband/ABANDONED 行保留。以上属经典 F4 账本族，多为运维保留期问题而非代码缺陷，但 `ai_agent_message` 的 TOPIC+STATUS 索引（AiAgentMessageTable.DDL_CREATE_INDEX）在 CONSUMED 行占多数后 selectivity 劣化会拖慢 `findPending`——建议 C6 复核时与 owner doc 对齐保留期与索引策略。

### [CL-072] (C4/C8) DefaultBackgroundTaskRegistry 记录 timeoutMs 但 registry 内无强制者——后台任务超时 seam 未闭合

`DefaultBackgroundTaskRegistry.Entry.timeoutMs`（:328,347-353）仅作为快照元数据存储：registry 内无任何调度器按其触发超时终态化，`stop()` 只能被显式调用（task-stop 工具）。后台 body 若阻塞且无人 stop，则 RUNNING 态无 deadline（对照 run 方法 :115-126 仅兜异常不兜挂死）。超时强制预期在 engine 侧装配（X2 分区 C7-014 已登记 per-tool 机械 cap 面），本条登记 seam 本身：`submit(toolName, sessionId, callId, timeoutMs)` 的 timeout 参数在 tool 包内无消费方，复核阶段应核实 engine 侧是否读取该值，避免「参数存在但无人执行」的契约空洞。

### [CL-073] (C2) AgentSession.seenInputKeys LinkedHashMap 无同步——幂等去重簿记的并发面

`AgentSession.seenInputKeys`（session/AgentSession.java:39,52-65）为 plain LinkedHashMap，`markInputSeen`/`hasSeenInput` 无同步。该结构的并发触达面：ReAct worker 线程（dispatch 前 recording）与 injectInput/steering 调用线程（`FileBackedSessionStore` javadoc :115-121 自证会话存在双写者形态）是否并发调用 markInputSeen 需 engine 包证据；若成立则 HashMap 结构损坏 + 去重失效双后果。`pendingInjections`（ArrayList，:77-89）同型，但有 256 上限 + 引擎信箱化改造（CL-061）后可能收敛为单线程触达。归属 C2 核查项（依赖 X2 分区的调用方线程模型证据）。

### [CL-074] (F6) SessionFileWriter 原子 rename 无 fsync——进程崩溃模型成立、断电模型不成立

`SessionFileWriter.java:106-129` 写 `.tmp` 后 `Files.move(ATOMIC_MOVE)`：rename 原子性齐备，但 `Files.write` 无 `force`/fsync——进程崩溃（kill -9）后 OS 页缓存数据仍可写回（安全），主机断电/内核崩溃则可能丢近期写甚至留下空/截断 session.json（torn file），后续 `listAllSessions` 走 WARN-skip、`get` 走 fail-fast（FileBackedSessionStore :227-234 已声明损坏隔离）。javadoc :51-60 声明「Crash-survival semantics」未界定故障模型是否含断电。对照 X2 CL-063（checkpoint journal 同型），建议两处统一声明「durability = process-crash 模型，非 power-loss」；如需 power-loss 保障再补 `FileChannel.force`。

---

## 维度复核结论（WI 3.1 C7 独立复核）

> 复核人：C7 维度独立复核员（与初审批次隔离）；复核日期：2026-10-20。
> 输入：本文件 C7-001…032（X1/X2/X3 三批）+ C7-round2.md（零新增）+ `ai-dev/skills/concurrency-resource-audit-prompt.md` 口径 + `ai-dev/audits/cra-index.md`（WI 1.1–2.5 台账）+ 同单元 C1/C3/C4/C5 复核已裁定项（跨维指针输入）。
> 方法：32 条全部逐条回码验证（证据行、时序链、严重度校准、失效面枚举、装配面核对），非抽样；同单元 C3-async-chains.md / C4-thread-pools.md / C5-memory-bounds.md / C1-shared-mutable-state.md 的复核保留项逐条比对查重。

### 一、判定统计

| 判定 | 条数 | 条目 |
|---|---|---|
| 已保留（维持原档） | 31 | C7-001…012、014…032 全部（含 11 条分叉/条件登记，见下） |
| 已降级 | 1 | C7-013（P1 → P2 条件化，缺省装配面，收敛 C5-009/C1-011） |
| 已驳回 | 0 | — |

- 证据核验：32/32 证据片段与 file:line 逐条回码命中，无事实性错误；1 处行号漂移（C7-032 初审 :241-244 → 实测 :234，约 8 行，不影响事实认定，已在条目批注）。
- 时序论证核验：P1 级 9 条触发时序全部重建成立（C7-001 挂死链经缺省 HTTP client 补证、C7-012 非对称分区链经 tryRenew 异常包装类型补证、C7-025 队首阻塞经 `ORDER BY CREATED_AT` 补证）。
- 复核修正 2 处：C7-017 初审「默认配置无症状」表述不准确（DefaultDenialLedger 缺省即有状态，config:122 直核——修正后强化而非削弱发现，维持 P2）；C7-032 行号漂移（见上）。

### 二、最终保留严重度分布

| 严重度 | 条数 | 条目 |
|---|---|---|
| P1 | 8 | C7-001（流式无期限/静默不可见）、C7-002（半关闭误判成功）、C7-012（续期异常无限容忍→双执行，条件面：DB 锁装配）、C7-022/023（成员/spawn 超时僵尸，与 C3-025/C4-014 判级分叉）、C7-025（毒消息无限重投，条件面：DB 传输装配）、C7-026（reply 订阅无界 × DB 轮询放大）、C7-027（后台任务账本无界，缺省装配） |
| P2 | 20 | C7-003、004、005、006、007（条件面）、010、**013（降档后）**、014、015、016、017、018（条件面）、019（条件面）、020、024、028、029、030、031、032 |
| P3 | 4 | C7-008、009、011、021 |

### 三、现状标注分布

- **新发现（未修复）：32/32**。cra-index WI 1.1–2.5 全部 P1 台账与 CL 台账逐条比对零命中（本单元为 WI 3.1 首轮审计）；历史审计目录无 nop-ai C7 同案前案。
- 跨单元同族对照（载体不同，**不重复立项**，Phase 8 按家族统一策略）：
  - F4 账本族：C7-007 ↔ WI 1.1 C1-006 / WI 1.6 C7-001 / WI 1.7 C7-003；
  - 墙钟敏感族：C7-004/C7-018 ↔ nop-stream-core-runtime C7-010/018、nop-wf-nop-task C7-006、nop-retry-nop-tcc C7-007/011、nop-job C7-008（判级先例一致 P2）；
  - fencing/租约族：C7-012/C7-018/C7-019 ↔ WI 1.6（C3-003 续租缺失、C1-001 CAS 守卫等）；
  - 周期任务异常绝缘：C7-029 ↔ WI 2.1 C3-001（平台级 29 站点绝缘层，**C7-029 修复归该层，不独立立项**）。

### 四、同案命中清单与跨维指针核对意见

**任务指派已列 9 组（核对结论）**：

| 组 | 同站登记（复核后判级） | C7 侧核对意见 |
|---|---|---|
| C7-017 ↔ C3-017 | C3-017 P2（异面：陈旧 deadline 语义击穿）；另 C5-013 P3、C1-006/C1-014 | 维持 P2；装配面复核修正（DenialLedger 缺省有状态）；判级分叉（P2/P3）留 7.1 归并 |
| C7-025 ↔ C3-021/C4-CL-040 | C3-021 P1、CL-040 confirmed | 收敛 P1；同意并单为 DBMessageService 治理工单（串行/守护/毒消息/轮询四轴一单） |
| C7-026 ↔ C3-024/C4-016 | C3-024 P1、C4-016 P2（分叉登记倾向 P1）、C5-018 P2 | 三重登记倾向统一 **P1**，C7 侧同意（必然无界增长 + 每订阅 20 QPS 确定性轮询退化）；7.1 并单 |
| C7-027 ↔ C3-023/C4-012 | C3-023 P1、C4-012 P1（主处置）、C5-021 P2、C1-017 | 维持 P1；主处置归 C4-012，本条随单吸收 |
| C7-028 ↔ C5-015/016 | C5-015 P1（族首，内存面）、C5-016 P2（恢复扫描面） | 三面分账清晰无重复；维持 P2（行为收敛面）；族修复随 C5-015 |
| C7-013 ↔ C5-009/C1-011 | C5-009 P2、C1-011 P2 | **C7 侧降档 P1→P2（条件化）**，三维判级差就此收敛（缺省 NoOpCheckpoint，config:125 直核；opt-in 先例 C1-005/C5-022） |
| C7-030 ↔ C5-020/C1-016 | 均 P2 | 收敛 P2；归并修复（接口 releaseSession） |
| C7-031 ↔ C5-010/011 | C5-010 P1（快照归档子面）、C5-011 P2（spill 子面） | C7-031 复合登记维持 P2，7.1 按子面拆分归并（快照随 C5-010、spill 随 C5-011） |
| C7-008/009/010 ↔ C5-002/003/004 | 均 P2 | C7-010 与 C5-004 同判级收敛；C7-008 与 C5-002 分账合理（声明默认 vs 容量面）；C7-009 P3 vs C5-003 P2 分叉登记留 7.1 |

**本复核新增登记（任务指派未列，回码比对发现）**：

| 组 | 同站登记（复核后判级） | C7 侧意见 |
|---|---|---|
| **C7-022 ↔ C3-025（面1）/C4-014** | C3-025 P2、C4-014 P2（两案互指、均未指 C7） | 三案同站未互指；判级分叉（C7 P1 vs P2×2）留 7.1。C7 侧 P1 论据：僵尸泄漏**持久性**（挂死执行永不终止，非 C3-025 所论「双执行窗受差值界定」的有界面），命中校准「错误/取消路径资源泄漏」；修复三案同 diff |
| **C7-023 ↔ C3-025（面2）/C4-014** | 同上 | 同上；spawn 路径（面2）与单成员路径（面1）建议同批修复 |
| **C7-032 ↔ C3-022/C4-010** | C3-022 P1、C4-010 P1（两案互指、均未指 C7） | 判级分叉（C7 P2 vs P1×2）留 7.1；C4-010 的 P1 论证（auto-spawn 文档化正常路径 × 每调用泄漏）可辩护，倾向收敛 P1；按反漂移纪律不单侧改档 |
| C7-019 ↔ C4-008 | C4-008 P2（续期单线程 DB 队头阻塞） | 同站同根收敛 P2；主处置归 C4-008 并单 |
| C7-029 ↔ C4-013/C3-027 | C4-013 P2、C3-027 P3（站点完全重合） | 维持 P2（C4-013 论证成立）；修复并入 WI 2.1 C3-001 平台绝缘层，不独立立项 |
| C7-001 ↔ C3-001/002、C4-002 | C3-001 P1/C3-002 P1/C4-002 P1（同链 publisher 生命周期/背压面） | 同链异层（C7 = 聚合层期限/静默可检测性），不并案，修复批次协同 |
| C7-016 ↔ C3-018 | C3-018 P2（fire-and-forget 异步失败静默） | 相邻面（C3 = 结果不可见；C7 = 重放无收敛），修复同批 |
| C7-024 ↔ C3-012 | C3-012 P2（LLM 幽灵调用） | 取消传播族相邻面；并入引擎取消传播批 |
| C7-012 ↔ C4-007 | C4-007 P2（SessionLockRenewal:74 Error 逃逸面） | 同文件互补面（C4 = Error 停摆；C7 = RuntimeException 无限容忍），修复同文件同 diff |

### 五、高风险条目子项复核说明

- **C7-001（挂死前提补证）**：初审未给出缺省 client 证据，本复核补齐——`nop-http-client-jdk` 的 `http-client-jdk.beans.xml:10` 定义 `nopRawHttpClient ioc:default=true`（JdkHttpClient）；`HttpRequest.Builder.timeout` 的 JDK 语义仅覆盖响应头到达（JdkHttpClient.java:218-221 设置点直核）；`ServerEventPublisher.parseEvents` 的 `while ((line = reader.readLine()) != null)` 无 socket 读超时（:138-140 直核）。三段链闭合，挂死形态在**缺省装配**成立，P1 维持。Apache client 路径 responseTimeout 提供非活动上界的豁免面核实成立（失效面枚举 (d) 维持）。
- **C7-012（异常结局类型链）**：`DbSessionTakeoverLock.tryRenew` 捕获 SQLException 后 `throw new NopAiAgentException(ERR_AGENT_INTERNAL_DETAIL, e)`（:312-315 直核）——NopAiAgentException 为 RuntimeException 子类，恰好落入 `renewOnceSafe` 的 `catch (RuntimeException)`（:71-77），与 `!renewed → handleLeaseLost` 的 false 分支形成协议窄化。时序 T1-T4（非对称分区 → lease 到期 → B 侧 expired-preempt 抢占 :203-209 → A 侧继续执行）无一步虚构。P1 维持，条件面（DB 锁显式装配、多实例拓扑）随条目披露。
- **C7-013（三维判级收敛裁定）**：缺陷本体（三缓存 put-only + 接口 default no-op + 全类零 remove）全部直核成立；降档仅基于装配面（缺省 NoOpCheckpoint，DefaultAgentEngineConfig:125 直核）+ 同站双维（C5-009/C1-011）已按同一依据校准 P2 的收敛事实。按 opt-in 先例（C1-005/C5-022）判为「已降级(P2 条件面)」而非「保留(P1)」——真实装配 DB 后端即回 P1 级增长率，修复排期权衡留给 7.1。
- **C7-025（毒消息循环必然性）**：`findPending` 的 `ORDER BY CREATED_AT LIMIT ?`（:378-379 直核）使最老毒消息必然占据批位；50ms 轮询 + maxBatch 10（:96-97 直核）→ ≥10 条毒消息即整队列停摆的推算成立；`DeferredAckMailbox` 的 maxDeliveryAttempts=5 + DEAD_LETTERED 终态（:39-55 javadoc 直核）坐实同包双传输防护不对称（F3）。P1 维持。
- **C7-026（增长乘法链复算）**：订阅数 = 历史 distinct 发起会话数（put-only DCL 直核，`pendingRequests` 的 whenComplete 清理为对照组）；每订阅每 50ms 一条 `SELECT … WHERE TOPIC=?`（pollAllTopics 逐 topic 直核）→ N 会话稳态 20×N QPS；轮询单轮时长随 N 线性拉长 → fixed-delay 被拉长 → 全 topic 投递延迟上升的反馈环成立。P1 维持，条件面（messenger 装配）披露。
- **C7-022/023（三站点逐一回码 + 漏登记指针发现）**：MemberAgentTaskStep:152（裸 execute）、TeamTaskFlowOrchestrator:646-667（仅 taskRt.cancel）、DefaultMemberSpawner:188-198（TimeoutException 无 cancel、spawnedSessionId 未引用）逐点直核；fan-out `executeBoundMember` 已修对照面（:321-337 orTimeout + cancelSession）直核。回码比对发现 C3-025（P2）+ C4-014（P2）已完整覆盖同站三站点但未与 C7 互指——已作为新增指针补登记（见第四节），判级分叉留 7.1。

### 六、降档口径核对结论

1. **长时运行形态不适用启动期单线程降档**：初审 X1/X2/X3 三批均已在覆盖声明中自查（三处「不适用启动期单线程降档」声明），复核确认无一发现错误套用启动期降档——口径合规。
2. **休眠/缺省装配面条件化降档**（对照 C1-005/C5-022 判例，须给装配面证据）：
   - **适用 1 条**：C7-013（缺省 NoOpCheckpoint，config:125 直核）→ P2 条件化（与 C5-009/C1-011 收敛）。
   - **不适用 5 条（披露不降档）**：C7-012/018/019（DbSessionTakeoverLock 缺省 NoOp，但为模块交付的 DB fencing 实现，多实例部署即设计场景）、C7-025/026（messenger/DB 传输为部署方装配，但为模块交付的完整 DB 传输实现：schema init/claim/at-least-once 全套）——依据本单元 C3-021 复核判例（「该类是模块交付的 DB 传输实现……其触发面正是其设计场景」，与 WI 2.5 C7-012 条件面 P1 判例一致），维持原档、条件面随条目披露。
   - **装配面修正 1 条**：C7-017 复核补证 DefaultDenialLedger 缺省即有状态（config:122）——初审「默认配置无症状」表述不准确，修正后强化发现，维持 P2。
3. **C7-round2 五项深挖面零新增**：复核抽查其否定性结论的证据（token 估算 O(n) 无分配、conflict 无等待原语、repair 单遍收敛、router 三重上限）均成立，零新增维持。

### 七、留 7.1 协调方事项（C7 侧汇总）

1. 同站并单与判级分叉统一：C7-022/023↔C3-025/C4-014（P1 vs P2×2）、C7-032↔C3-022/C4-010（P2 vs P1×2）、C7-026 三重登记（倾向统一 P1）、C7-029↔C4-013/C3-027（倾向 P2）、C7-009↔C5-003（P3 vs P2）、C7-031 子面拆分（快照归档随 C5-010 P1）。
2. C7-013 降档后的修复排期权衡（非缺省面 P1 级增长率）。
3. 条件面披露条目的部署面标注：C7-012/018/019（DB 锁 + 多实例）、C7-025/026（DB 传输装配）、C7-007（model 名可达性）。
4. C7-029 修复归属 WI 2.1 C3-001 平台绝缘层批次（与 C4-013/C3-027/C4-007 同批）。
5. DBMessageService 治理工单（C3-021/027 ↔ CL-040/C7-025）与 replySubscriptions 并单（C3-024 ↔ C4-016 ↔ C7-026）的批次合并。

## 最终保留项

- **P1 × 8**：C7-001（流式聚合无期限/静默不可见）、C7-002（干净半关闭误判成功交付截断内容）、C7-012（续期异常无限容忍 → 非对称分区双执行；条件面：DB 锁装配）、C7-022（team-flow 单目标成员无期限且整体 deadline 不取消会话；↔C3-025/C4-014 分叉）、C7-023（spawn 超时不取消 spawned 会话；↔C3-025/C4-014 分叉）、C7-025（DB 毒消息无限重投 + 队列停摆；条件面：DB 传输装配；↔C3-021 工单并单）、C7-026（reply 订阅无界 × DB 轮询线性放大；三重登记倾向统一 P1）、C7-027（后台任务账本无界，缺省装配；主处置归 C4-012）。
- **P2 × 20**：C7-003（Retry-After 无上限）、C7-004（熔断/冷却墙钟）、C7-005（probe 逃逸无令牌）、C7-006（AiCommand 零退避重试 + 绕过限流）、C7-007（失败记忆表无界；条件面：model 名可达性）、C7-010（文件窗口读退化为全量读）、C7-013（DB checkpoint 缓存无清理；**降档 P1→P2 条件化**，收敛 C5-009/C1-011）、C7-014（heartbeat wakeup 无预算 + 永久入史）、C7-015（restoreSession 锁前变更共享状态）、C7-016（orphan RESUME 无重试预算）、C7-017（治理注册表族无终态清理；装配面修正）、C7-018（租约期限墙钟；条件面）、C7-019（单线程续期调度器；条件面；↔C4-008 并单）、C7-020（沙箱 reader 线程泄漏 + 空输出）、C7-024（call-agent async 无取消协调）、C7-028（终态会话工作集不退出；分账 C5-015/016）、C7-029（周期任务 Error 逃逸；↔C4-013，归 WI 2.1 C3-001）、C7-030（memory provider 无释放；收敛 C5-020/C1-016）、C7-031（spill/归档三件套无预算；子面拆分随 C5-010/011）、C7-032（per-invocation orchestrator 池泄漏；↔C3-022/C4-010 分叉）。
- **P3 × 4**：C7-008（缓存毒条目无自愈）、C7-009（聊天日志无保留期；↔C5-003 判级分叉）、C7-011（secret 负缓存永驻）、C7-021（plan untyped 失败零退避热循环）。
- **P0 × 0**；降档 1（C7-013）、驳回 0；分叉/条件登记 11 条（C7-007/012/018/019/025 条件面 + C7-009/022/023/029/031/032 分叉登记）均留 7.1 终裁。
