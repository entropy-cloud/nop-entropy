# C1 共享可变状态与可见性 — nop-ai-core-nop-ai-agent（WI 3.1）

## 审计范围与口径

- **批次**：CRA WI 3.1 批次 A/5，原子分区 A。审读根：`nop-ai/nop-ai-core/src/main/java`（119 文件 / 19,227 行，其中 18 个 `_gen/` 生成文件按纪律排除 → 实审 101 文件 / 约 14.2k 行，与批次口径一致）。15 个包逐一覆盖：api/command/dialect(8)/file/initialize/model/persist/prompt/reliability(20)/response(5)/service/simplifier/utils/commons/commons/splitter 等。
- **方法**：`ai-dev/skills/concurrency-resource-audit-prompt.md` L26–118（F1–F11 + 共享前缀）+ L121–145（C1 正文）+ L351–377（附录 A 格式）。盲审纪律遵守：未读 audits/plans/bugs/lessons/backlog，未查 git 修复历史。
- **口径纪律声明**：本模块形态为 LLM 客户端核心，交互面（`ChatServiceImpl.callAsync/callStream`、`EmbeddingServiceImpl.embedAsync`、`AiCommand.executeAsync`）均为用户/Agent 请求触达路径，**不适用启动期单线程降档**；仅纯启动期初始化站点（IoC setter 注入、`AiCoreInitializer` 注册、static 初始化块）逐站点论证降档（见清单表判定列）。
- **批次**：CRA WI 3.1 批次 B/5，原子分区 B。审读根：`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security`（74 文件 / 6,090 行，无嵌套子包，全量逐文件审读）+ `…/agent/plan`（手写 44 文件 / 3,151 行；`model/_gen` 22 个 `_` 前缀生成文件按纪律跳过）。方法与盲审纪律同批次 A；基线复核：C1a 节 security/plan 命中经人工复核全部为噪声（static 工厂方法 / import static，真实 static 非 final 可变字段为 0）；C1b 节命中 7 个 `static final Set.of` 不可变集合（通过）；C1c 节命中 `ThreadLocalTenantResolver.CURRENT`（实现正确——`set(null)`/`clear()` 均 remove，全部 set/clear 调用点位于 engine/team 包，配对纪律在 finally 中遵守，属其他批次分区，本批仅登记判定）。
- **降档裁定（本批次）**：security 组件经 `DefaultAgentEngineConfig`（:107-124）以单实例字段持有、跨全部 agent 会话/线程共享，全部为请求触达面，**不适用启动期单线程降档**；plan/runtime 为自包含宿主（全库 main 零外部调用方，javadoc 自认"engine has no phase-transition hook today"），`PlanExecutionState` 为 per-execute 栈封闭新建（`PlanExecutor.execute`:117），当前按栈封闭降档通过并附契约缺口注记（见清单 #B14）。
- **基线复核（批次 B 范围）**：`_tmp/cra-baseline-nop-ai-core-nop-ai-agent.txt` 中 ROOT: nop-ai-agent 分节的 C1a/C1b/C1c 行按路径过滤 security/plan 后逐条人工复核：C1a 噪声（static 方法声明、import static）过滤后真实 static 非 final 可变字段为 **0**；C1b 命中 7 个 `static final Set.of` 不可变集合（通过）；C1c 命中 `ThreadLocalTenantResolver`（判定见上）。批次 A 基线复核原文（nop-ai-core 范围）：C1a 噪声（static 方法声明、import static）过滤后，真实 static 非 final 可变字段共 **8 个**：5 个 response parser 单例 + `DefaultSystemPromptLoader._instance` + `PromptSyntaxStdDomainHandler.INSTANCE` + `LlmConfigHelper.secretDir`（基线 grep 因 `private static File` 形态漏报，本次补齐）。C1c 为空（本模块无 ThreadLocal，仅 `ThreadLocalRandom`——不是缺陷载体）。

- **批次**：CRA WI 3.1 批次 C/5，原子分区 C。审读根：`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/` 下 4 包全量逐文件审读——`engine`（47f/14,342）、`runtime`（34f/5,139，含 coordination/lock/recovery 3 个嵌套子包）、`model`（17f/285 手写；`_gen` 内 `_` 前缀生成文件按纪律跳过）、`reliability`（30f/3,909）。方法与盲审纪律同批次 A/B。基线复核（批次 C 范围）：C1a 节 engine/runtime/model/reliability 命中经人工复核全部为噪声（static 方法声明 / static final Logger / javadoc），真实 static 非 final 可变字段为 **0**（与批次 A 口径一致的自验 grep 复核）；C1b/C1c 节对 4 包命中为 **0**（C1c 仅 security 包 ThreadLocalTenantResolver，批次 B 已登记判定）。
- **降档裁定（本批次）**：engine/runtime 为请求触达 + 长时运行双面（`DefaultAgentEngine.execute/resumeSession/wakeSession/restoreSession` 均为用户/守护请求入口；`ScheduledRecoveryManager`/`SessionLockRenewal`/`InMemoryActorRuntime` 为常驻后台线程面），**不适用启动期单线程降档**；`DefaultAgentEngineConfig` 的组件 setter 换装沿用批次 B 判定（装配期 bean 注册链发布，通过；但 engine 上的 `setAgentExecutor`/`setLockRenewExecutor`/`setBackgroundProgressTracker` 为运行期公开 API，判定见 **C1-013**）；`ReActAgentExecutor.ExecutionState`/`LlmCallCoordinator.RetryState` 经逐字段复核均为 per-execute 栈封闭新建，通过。

### 批次 C 共享可变状态清单表（engine + runtime + model + reliability，载体 × 写者 × 读者 × 同步机制 × 判定）

| # | 载体 | 位置 | 写者 | 读者 | 同步机制 | 判定 |
|---|------|------|------|------|----------|------|
| C1 | `DefaultAgentEngine.runningExecutions`（CHM<String,CancelHandle>） | engine/DefaultAgentEngine.java:121 | `putIfAbsent`:918（注册）/`remove(sessionId, handle)`:1006（两参删除） | cancelSession:514、SessionLockRenewal.renewOnceSafe:66 | CHM 原子复合操作 + 两参 remove 防互踩 | 通过 |
| C2 | `DefaultAgentEngine.runningInjectionTargets`（CHM<String,ctx>） | engine/DefaultAgentEngine.java:127-128 | doExecute put:774 / 终局 finally remove:1010 | injectInput get:617 | CHM 原子单操作，但 get→enqueue 与 remove→terminal-save 无复合原子性；resume/wake/restore 三路径从未注册 | **C1-008 / C1-009** |
| C3 | `CancelHandle.thread`/`renewHandle` | engine/AgentSessionLifecycle.java:128-137 | worker 起始:969/:369 赋值；同步阶段 :923 赋 renewHandle | cancelSession(forced):529-532、SessionLockRenewal.handleLeaseLost:87-90、终局 finally:1070 | 双字段 volatile（javadoc :109-126 明示 HB 论证） | 通过 |
| C4 | `AgentExecutionContext.cancelRequested/cancelReason/leaseLost` | engine/AgentExecutionContext.java:37-38,46 | cancel 线程（cancelSession:518-519）+ renewal 调度线程（handleLeaseLost:84-86） | ReAct 循环每迭代/终局 finally:1015 | volatile ×3 | 通过 |
| C5 | `AgentExecutionContext.steeringQueue`（ConcurrentLinkedQueue） | engine/AgentExecutionContext.java:87 | injectInput 线程:619、Actor 消费线程（InMemoryActorRuntime:465） | ReAct 线程 drain（:542/:1434/:1796） | CLQ 无锁协调 + 单消费者纪律（javadoc :74-86） | 通过（终局窗口丢注入归 C1-009） |
| C6 | `AgentExecutionContext` 其余字段（messages ArrayList/status/iteration/tokensUsed） | engine/AgentExecutionContext.java:26-36 | ReAct 线程单写 | 同线程终局读 | 栈封闭（ctx 不逃逸出执行槽；runningInjectionTargets 暴露面仅 enqueueSteering） | 通过 |
| C7 | `DefaultAgentEngine.agentExecutor` + `ownAgentExecutor`；`SessionLockRenewal.lockRenewExecutor` + `ownLockRenewExecutor` | DefaultAgentEngine.java:130-131；SessionLockRenewal.java:24-25 | synchronized 惰性 getter:469-479/:44-54（写）；公开裸 setter:481-484/:33-37（换装写） | 每次执行 dispatch、每次锁续期 | **非对称**：读侧 synchronized、写侧无锁无 volatile（own* 为 volatile 但 executor 本体不是） | **C1-013** |
| C8 | `DefaultAgentEngine.toolProgressTracker`/`backgroundTaskRegistry`/`backgroundExecutor` | DefaultAgentEngine.java:113-116 | Builder 装配 / `setBackgroundProgressTracker`:700-705 运行期写 | 后台任务桥线程（bridgeBackgroundCompletion:679、snapshot 钩子:663-666） | 装配期一次性通过；运行期 tracker 换装 plain 写无 volatile（同 C1-013 族） | C1-013（注记） |
| C9 | `AgentCallDelegate.messenger` + `callAgentSubscription` | engine/AgentCallDelegate.java:34-35 | setMessenger:41-45（subscription 检查-然后-换装 :48-65 非原子） | ensureSessionMailbox:65（读 messenger）、消息总线回调 | subscription volatile；messenger 非 volatile；并发双 setMessenger 可泄漏一条未取消订阅 | C1-013（注记，低频配置路径） |
| C10 | `DefaultAgentEventPublisher.subscribers`（CopyOnWriteArrayList）+ `failureCounts`（CHM<sub,AtomicLong>）+ volatile policy/threshold | engine/DefaultAgentEventPublisher.java:24-33 | add/removeSubscriber 运行期；publish 内 computeIfAbsent+increment:41-43、remove:51 | 每事件 publish 遍历 | COW 遍历无锁 + CHM + volatile；remove 幂等 | 通过 |
| C11 | `AgentSessionSupport.sessionMailboxes`/`sessionMailboxSubscriptions`（CHM ×2） | engine/AgentSessionSupport.java:31-32 | `ensureSessionMailbox` computeIfAbsent:56-72（compute 内跨 map 放订阅，原子性依赖 CHM bin 锁） | getSessionMailbox:112 | CHM；**无任何 remove 路径**（engine 全生命周期零清理） | **C1-014**（F4） |
| C12 | `CalibratedTokenEstimator.factor`（volatile double） | engine/CalibratedTokenEstimator.java:49 | `record`:72-84 EMA 读-改-写（多会话并发共享同一 estimator 实例，DefaultAgentEngineConfig:113 单字段持有） | estimateTokens:63-69 每次压缩/force-stop 估算 | volatile 保证可见性但 RMW 非原子（丢失更新） | **C1-015** |
| C13 | `AgentActor.status/lastActiveAt/steeringQueue`（volatile ×3）+ `receivedMessages`（synchronizedList） | runtime/AgentActor.java:86-93,89 | 消费单线程 + destroy 线程（destroyActorInternal:364-398 stop→shutdownNow→awaitTermination→再断言 STOPPED:392） | 任意外部观察者 | volatile + synchronized 快照副本:204-208；destroy 与循环初启竞态由 initial-status 守卫:413-417 + 终局再断言消除 | 通过（javadoc 契约与实现一致） |
| C14 | `InMemoryActorRegistry` 双索引（byActorId/sessionToActorId/actorToTenant CHM ×3） | runtime/InMemoryActorRegistry.java:43-45 | register:69-92（actorId 先写、session 索引后写）；unregister:95-107（逆序） | get/getBySession/getAll | CHM + 写序约定（javadoc :19-25）；同 sessionId 并发 createActor 的幂等检查-然后-行动由引擎执行槽串行化掩护，公开直调为理论竞态 | 通过（注记：配额检查-然后-行动见 C1-015 族注记） |
| C15 | `InMemoryActorRuntime.executors`（CHM<String,ExecutorService>） | runtime/InMemoryActorRuntime.java:133 | createActor put+submit:306-307；destroy remove+shutdownNow:372-385 | 同 | CHM + put 先于 submit（HB）；register-与-destroy 交叠窗口由循环 initial-status 守卫兜住（线程立即退出，无泄漏） | 通过 |
| C16 | `ScheduledRecoveryManager.scheduleHandle`（volatile Future）+ synchronized start/stop + 4 个裸 handler 字段 | runtime/recovery/ScheduledRecoveryManager.java:142,153,165,179,189 | start/stop:356-375；setter:238-329（启动期注入） | scanOnce 调度线程 | volatile handle + synchronized 生命周期；handler setter 启动期写 → start 前完成（装配期降档通过；start 后换装无 volatile，注记） | 通过（附注） |
| C17 | `DbSessionTakeoverLock`/`DbDaemonCoordinator`——DB 行为共享可变状态外置载体 | runtime/lock/DbSessionTakeoverLock.java:156-229；runtime/coordination/DbDaemonCoordinator.java:158-231 | tryAcquire INSERT→条件 UPDATE 两步 CAS | tryRenew/release/isHeld | PK 唯一 + 条件 UPDATE 原子性（并发抢占方第二步对已更新行 WHERE 不匹配 → 0 行）；租户 WHERE 非严格式 `OR TENANT_ID IS NULL`（TenantSql:31）恰好弥合引擎 acquire（caller 线程无租户上下文）/release（worker 线程有租户）的上下文不对称 | 通过（锁设施本体正确） |
| C18 | `ToolExecutionCheckpoint.bySession/byWatermark`；`FileBackedCheckpointManager` 五缓存；`DBCheckpointManager` 三缓存 | reliability/ToolExecutionCheckpoint.java:45-46；FileBackedCheckpointManager.java:100-105；DBCheckpointManager.java:93-95 | saveCheckpoint（CHM+syncList 写穿透）；remove（Tool:90-102 / File:201-218 覆写） | 恢复路径读 | CHM + Collections.synchronizedList；**DB 实现未覆写 remove（ICheckpointManager:114 default no-op）**；两实现 ensureSessionLoaded 检查-然后-行动 + 装载整表替换列表 | **C1-011 / C1-012** |
| C19 | `SessionGoalTracker.entries` / `DefaultWaitCoordinator.sessions`（CHM<String,SessionState/WaitState>） | reliability/SessionGoalTracker.java:60；DefaultWaitCoordinator.java:47 | recordIteration/requestWait（per-entry monitor / volatile 字段） | assessGoal/checkWait | 原子性通过；**两 map 均无任何删除 API，终局路径亦无清理调用** | **C1-014**（F4） |
| C20 | model 包 17 文件 + `Checkpoint`/`CheckpointSnapshot`/`IterationSnapshot`/`WaitCondition` 等值对象 | model/*.java（AgentExecStatus 为 enum，余为 9 行接口 stub）；reliability/Checkpoint.java:57-69 全 final | 装载期/构造期一次性 | 只读 | enum/不可变/final freeze | 通过 |
| C21 | `ReActAgentExecutor.ExecutionState`/`LlmCallCoordinator.RetryState`/`AgentHookInvoker`/`AgentSecurityConsultation`/`AgentExecutorResolver`/`AgentPromptAssembly`/`AgentLoopGuard`/`AgentCompactionCoordinator`/`SingleTurnExecutor` 实例字段 | 各文件字段扫描（全部 final 协作者或 per-execute 局部 holder） | 构造器/execute 局部 | 同一执行线程 | final + 栈封闭 | 通过（F10 守卫面：ExecutionState.checkpointSeq 由单 ReAct 线程独占推进） |
| C22 | `DefaultAgentEngineConfig` 全部组件字段（:107-187） | 装配期 setter（批次 B 已判定）；`usageRecorderNoOpWarned` volatile:128 | 装配期 / markUsageRecorderNoOpWarned 运行期 | 每请求读 | 装配期 bean 发布链；运行期标记 volatile | 通过（沿用批次 B 判定） |

### 共享可变状态清单表（批次 A：nop-ai-core，载体 × 写者 × 读者 × 同步机制 × 判定）

| # | 载体 | 位置 | 写者 | 读者 | 同步机制 | 判定 |
|---|------|------|------|------|----------|------|
| 1 | `s_instance`/`_instance` ×5 | response/Xml·Markdown·Code·Yaml·JsonResponseParser | static init + 公开 `registerInstance`（**库内 main 无调用方**，grep 复核） | `ChatExchange.parseXxxContent` 每请求 | 无（非 volatile 非final） | **C1-003** |
| 2 | `DefaultSystemPromptLoader._instance` | prompt/DefaultSystemPromptLoader.java:11 | static init（public 可换装，无库内写者） | `AiCommand.addSystemPrompt`:401 每请求 | 无 | **C1-003** |
| 3 | `PromptSyntaxStdDomainHandler.INSTANCE` | initialize/PromptSyntaxStdDomainHandler.java:28 | static init；`AiCoreInitializer.initialize()` 启动期注册进 StdDomainRegistry | XDEF 解析（启动/模型加载期） | 启动期单线程写 + 注册容器发布 | 启动期降档通过 |
| 4 | `LlmConfigHelper.secretDir` + `secretCache` | service/LlmConfigHelper.java:42-43 | `ChatServiceImpl.setSecretDir`（@InjectValue，IoC 装配期）/`reset()`（测试）；缓存写入=每请求 `computeIfAbsent` | `resolveApiKey`:139 每请求 | secretDir：启动期 bean 装配写 → bean 注册（CHM）发布链可见；secretCache：`MapCache(name, threadSafe=true)`=ConcurrentHashMap | 降档通过（secretDir 理论上非 volatile，依赖装配期-发布链 HB，逐站点论证成立） |
| 5 | `PromptModel.promptTemplate`（全局缓存模型上的惰性记忆化） | model/PromptModel.java:23 | `PromptTemplateManager.loadPromptTemplateFromPath`:16-24（每请求热路径） | 同方法读 | 无（非 volatile，检查-然后-行动） | **C1-001** |
| 6 | `PromptOutputModel.markdownTplWithoutDetail` | model/PromptOutputModel.java:20,53-62 | 惰性 getter（克隆后变异再发布） | `PromptModel.getMarkdownTplWithoutDetailForResult`（**休眠：全库 main 无调用方**） | 无 | **C1-002** |
| 7 | `ChatServiceImpl.httpClient/chatLogger/credentialResolver` | service/ChatServiceImpl.java:71-82 | IoC setter（装配期） | 每请求 | bean 容器发布链（装配写先行于注册） | 降档通过 |
| 8 | `ChatServiceImpl.rateLimiters` | ChatServiceImpl.java:72 | 每请求 `computeIfAbsent` | 同 | CHM 原子复合操作 | 通过 |
| 9 | `EmbeddingServiceImpl.retryScheduler` + `schedulerLock` | service/EmbeddingServiceImpl.java:88-89 | 惰性 DCL + 运行期 setter | 每次重试调度 | volatile DCL 正确；setter 换装不关旧实例 | **C1-004** |
| 10 | `EmbeddingServiceImpl.rateLimiters` / `retryPolicy` | EmbeddingServiceImpl.java:86,91 | computeIfAbsent / 普通 setter | 每请求 | CHM / setter（retryPolicy 运行期换装与 C1-003 同族，当前实现无状态 → 注记不立案） | 通过（附注） |
| 11 | `LlmDialectFactory.DIALECTS` | dialect/LlmDialectFactory.java:17 | static 块（5 内置方言）+ 公开 `register`（覆盖模式，无库内运行期调用方） | `getDialect` 每请求 | ConcurrentHashMap | 通过 |
| 12 | `ThresholdBreaker.entries` + `BreakerEntry` 5 字段 | reliability/ThresholdBreaker.java:103,321-329 | `entries.compute` 临界区内 | `getState` 只读 volatile state | CHM per-key 临界区 + volatile | 通过（javadoc 声称 "synchronized on entry instance" 与实际 CHM compute 机制不符——文档性偏差，非缺陷） |
| 13 | `ProviderFailoverQueue.health` + `ProviderHealth` 3 字段 | reliability/ProviderFailoverQueue.java:39,134-140 | `computeIfAbsent` + `synchronized(entry)` | `isProviderAvailable` 同锁；`getConsecutiveFailures` 无锁读 | entry monitor + 全字段 volatile（双保险） | 通过 |
| 14 | `ErrorMessageClassifier` 静态表 ×2 | reliability/ErrorMessageClassifier.java:37-39 | static 块一次性填充 | 每次错误分类只读遍历 | 类初始化 JLS 17.5 安全发布 | 通过 |
| 15 | `ChatServiceImpl.StreamAggregator`/`ToolCallAccumulator`（per-request 可变累积器） | ChatServiceImpl.java:483-624 | Flow.Subscriber onNext/onComplete/onError | 同一 subscriber 回调链 | Flow 规范：同一 Subscription 信号串行 + SubmissionPublisher 文档化内存一致性效应（跨回调 HB） | 通过 |
| 16 | `EmbeddingServiceImpl.EmbedCall`（attempt/fallbackSteps/currentAccount） | EmbeddingServiceImpl.java:240-242 | dispatch→whenComplete→scheduleRetry 串行链 | 同链 | CompletableFuture 完成语义 + 调度器提交-执行 HB | 通过 |
| 17 | `AccountChain.cursor` / `ProviderFailoverChain.cursor` | reliability/AccountChain.java:24; ProviderFailoverChain.java:28 | per-execution 游走 | 同 | javadoc 声明非线程安全、per-call 新建（EmbeddingServiceImpl.java:250 实证） | 通过（契约+用法一致） |
| 18 | 8 个方言实现实例字段 | dialect/*.java（grep 复核：仅 static final，零实例字段） | — | — | 无状态单例 | 通过（设计意图，非缺陷） |
| 19 | `AiCommand` 可变 builder 字段 + `getChatLogger` 惰性查 bean | command/AiCommand.java:58-68,277-281 | builder 链式 set + 惰性 get | executeAsync | 单次构建-单次执行使用约定；getChatLogger 双写幂等（同一 bean 查找结果）良性 | 通过（附注：`makeChatOptions` 同形态幂等） |
| 20 | 全部 model/ 数据 bean（LlmModel 等经 `ResourceComponentManager` 全局缓存） | model/*.java | 缓存加载器内部 parse+`init()`（装载临界区内一次性） | 每请求 | ResourceLoadingCache 装载锁 + 缓存发布（复核 `ResourceComponentManager.loadComponentModel`:386-390 走 `cache.require`） | 通过（**发布后**的再写仅 #5/#6 两处，已立案） |

### 批次 B 共享可变状态清单表（security + plan，载体 × 写者 × 读者 × 同步机制 × 判定）

| # | 载体 | 位置 | 写者 | 读者 | 同步机制 | 判定 |
|---|------|------|------|------|----------|------|
| B1 | `DefaultPermissionProvider.agentPermissions`/`sessionPermissions` | security/DefaultPermissionProvider.java:15-16 | 公开 `configure()`:26-30 + **5 参 `resolve()`:61-67（查询方法的写副作用）**（库内 main 零调用方，grep 复核） | 3 参 `resolve()`:33 经 `buildSources()`:69-81（引擎每工具调用 `AgentSecurityConsultation`:176-178 读） | 无（非 volatile，普通 plain 写） | **C1-005** |
| B2 | `DefaultDenialLedger.counts`（CHM<sessionId,AtomicInteger>） | security/DefaultDenialLedger.java:48 | 引擎默认单例（`DefaultAgentEngineConfig`:122），每 denial `computeIfAbsent+incrementAndGet`:76-77 | `isPaused`/`getDenialCount`:91,100 | CHM + AtomicInteger（原子性通过） | **C1-006**（F4 无界：仅 `reset(sessionId)`:105-110 可删，无 TTL/全局清理） |
| B3 | `FingerprintPostDenialGuard.deniedFingerprints`（CHM<sessionId,Set>） | security/FingerprintPostDenialGuard.java:39 | 引擎默认单例（`DefaultAgentEngineConfig`:123 经 `DefaultPostDenialGuard`:25 委托），每 denied action `computeIfAbsent(...).add`:81 | `checkBeforeDispatch`:64-65 | CHM + `ConcurrentHashMap.newKeySet()`（原子性通过） | **C1-006**（F4 同族：仅 `reset`:85-90 可删，无 TTL） |
| B4 | `DBDenialLedger`——DB 表 `ai_agent_denial` 为共享可变状态的外置载体 | security/DBDenialLedger.java:140-263 | `recordDenial` INSERT:184 / `reset` DELETE:233（跨进程/实例共享） | COUNT:240-263 | DB 隔离（javadoc :38-44 声明）；INSERT 与 COUNT 为两条独立语句，非事务包裹 | 通过（同族瞬态偏差有界：并发下 COUNT 至多多记/少记一次门槛判定，下一次 recordDenial 重读收敛；行仅 reset 删除——F4 的 DB 侧形态，随 C1-006 一并登记注记，修复归 C6） |
| B5 | `DefaultPathAccessChecker.sensitivePrefixPatterns`/`sensitiveFilenames`（ArrayList/HashSet 挂 final 引用） | security/DefaultPathAccessChecker.java:64-66 | **仅构造器**:80-87 `addAll`，构造后无任何变异 API（grep 复核） | `checkAccess`/`checkSensitiveRealPath`:113,156（每路径检查热路径） | final 字段冻结（JMM final freeze 覆盖集合内容）+ 构造后只读 | 通过（安全发布依赖持有方安全通道——引擎装配期注册链成立） |
| B6 | `ThreadLocalTenantResolver.CURRENT`（static ThreadLocal<String>） | security/ThreadLocalTenantResolver.java:36 | `set()`:48-54（null→remove）/`clear()`:61-63；调用点全在 engine/team 包（grep 复核 13 处，均 try/finally 配对或 previous 保存-恢复） | `current()`/`resolveTenantId()`:69-76 | ThreadLocal 隔离 + finally remove 纪律 | 通过（池化线程泄漏面由调用方配对消除；配对站点属 engine/team 批次分区，此处仅登记载体判定） |
| B7 | `ParentPermissionConstraint` 三集合 + 包装 checker（RuleBased/ParentConstrained ×2）实例字段 | security/ParentPermissionConstraint.java:117-119、RuleBasedPathAccessChecker.java:86、ParentConstrained*AccessChecker.java:79-80/:48-49 | 构造器一次性 `Set.copyOf`/`List.copyOf`/`unmodifiableList` | 每工具/路径检查 | 不可变副本 + final | 通过（跨线程传播经 `AgentMessageRequest` metadata，值不可变，无发布缺口） |
| B8 | `SecurityCheckpointChain.checkpoints`（Builder ArrayList → `List.copyOf`） | security/SecurityCheckpointChain.java:31,42 | build() 临界（单线程构建） | `evaluate` 遍历每工具调用 | 构建后不可变副本 | 通过 |
| B9 | `DefaultSecurityLevelResolver`/`DefaultLevelHintsProducer`/`DefaultContentTrustEvaluator`/`Slf4jAuditLogger`/`DefaultToolAccessChecker`/`DefaultApprovalGate`/Auto·NoOp·AllowAll·PassThrough·Null 各实现 | security/*.java（字段扫描复核：仅 final 引用或零实例字段） | 无运行期写 | 每工具调用 | 无状态/不可变单例 | 通过（设计意图） |
| B10 | `DockerSandboxBackend`/`NoOpSandboxBackend` 实例字段（dockerImage/config/allowedBaseDirs） | security/DockerSandboxBackend.java:134-136、NoOpSandboxBackend.java:66-67 | 构造器（`List.copyOf`:188） | 每次 execute | final + 不可变副本；per-call Process/reader thread/`AtomicReference<StringBuilder>` 隔离（`capturedRef.set` 在 finally:571/178 → AtomicReference HB） | 通过（per-call 线程生命周期缺口另立 **C1-007**） |
| B11 | `DefaultDenialLedger`/`FingerprintPostDenialGuard` 之外的静态表（DENIED_TOOLS、SENSITIVE_*、NETWORK/HIGH_IMPACT_TOOLS、ENV_KEY_PATTERN、DDL 常量） | security/DefaultToolAccessChecker.java:11、DefaultPathAccessChecker.java:21,29-57、DefaultLevelHintsProducer.java:47-54、DefaultSecurityLevelResolver.java:50-56、AiAgentDenialTable.java | static init/static 块一次性 | 只读 | 类初始化 JLS 安全发布；`Set.of` 不可变 | 通过（基线 C1b 命中项，全部通过） |
| B12 | plan/model 全部 22 个手写模型（AgentPlan/AgentPlanTaskModel/AgentPlanGate 等） | plan/model/*.java（字段扫描：零非 final 实例字段） | 装载期（`ResourceComponentManager` freeze(true) 级联冻结，PlanExecutionState javadoc :26-31 交叉确认） | executor/scheduler 只读 | 冻结协议（宿主裁定 §14.4.3） | 通过（`AgentPlanError` 有 setter 但实例仅由 `PlanExecutionState.recordError`:237-245 新建并 confined，见 B14） |
| B13 | plan/runtime 无状态协作者（PlanScheduler/PlanRunner/PlanReplanner/StagnationDetector/PlanDagBuilder/ReplanPolicy/FailureEscalationPolicy/AgentPlanValidator） | plan/runtime/*.java（字段扫描：仅 final 或零实例字段） | 构造器 | execute 循环 | final 不可变；`decide()` 纯函数 | 通过（可跨并发 execute 共享） |
| B14 | `PlanExecutionState` 全部可变集合（taskStatus/phaseStatus/errors/runtimeTasks 等 11 个 LinkedHashMap/ArrayList/LinkedHashSet + `currentPhase`/`planStatus`:76-77） | plan/runtime/PlanExecutionState.java:47-77 | `PlanExecutor.execute`:117 per-execute 新建后循环线程串行写 | 同 execute 线程（`statusProvider()`:313-318 活视图仅交同线程 scheduler） | **栈封闭**（无任何同步/并发守卫，无线程安全 javadoc 契约） | 通过（附注：封闭性纯靠"TaskRunner 同步返回"这一未成文的调用约定维持；javadoc :14-15 明言生产接线将"delegate to the agent engine"——一旦 TaskRunner 异步化/DAG 并行驱动，全部集合立即成为无同步共享态。契约缺口登记，不构成本维当前缺陷） |

- **批次**：CRA WI 3.1 批次 D/5，原子分区 D。审读根：`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/` 下 5 包全量逐文件审读——`team`（55f/10,490，含 flow/scheduler 2 个嵌套子包）、`tool`（21f/3,781）、`session`（21f/4,112）、`message`（18f/2,403）、`memory`（17f/1,528），合计 132 文件 / 约 22.3k 行，范围内无 `_` 前缀生成文件。方法与盲审纪律同批次 A/B/C。基线复核（批次 D 范围）：C1a 节 5 包命中经人工复核全部为噪声（import static / static 方法声明 / static final / 1 个 `DBMessageService.MessageRow` 内部类声明），自验 grep 复核真实 static 非 final 可变字段为 **0**；C1b 命中 3 处：`DefaultTeamAclChecker.REQUIRED_ACTIONS`（`buildRequiredActions` 以 `Map.copyOf` 收尾 :75-92，不可变，通过）、`DBMessageService.CAPABILITIES`/`WARN_ONCE`（`EnumSet.of` 可变类型 :80-86，`getCapabilities()`:88-91 将可变集合直接逸出为 public API 返回值——库内 grep 无变异调用方，防御性缺口注记不立案）；C1c 命中 team 包对 `ThreadLocalTenantResolver` 的 8 处 set/clear/current 调用（MemberFanOutDispatcher:268/286/376/380、SpawnMemberAgentTaskStep:233/237、TeamTaskSchedulerDaemon:299、TeamTaskFlowOrchestrator:725），写点全部 try/finally 配对（captured-tenant 传播纪律与引擎 :967/:1077 同构），通过。
- **降档裁定（本批次）**：5 包全部为请求触达/长时运行面（team 编排工具与调度守护线程、session 存储的执行/injectInput 双写者、message 信箱与 DB 轮询线程、memory 工具与 per-session provider），**不适用启动期单线程降档**；仅 `DbTeamManager`/`DBSessionStore`/`DBMessageService`/`DbModelSwitchedMessageWriter` 构造器 `initSchema()` 与 `DBMessageService` 4 个配置 setter（start 前装配写）按装配期降档通过（start 后换装缺口并入 **C1-022** 汇总）。

### 批次 D 共享可变状态清单表（team + tool + session + message + memory，载体 × 写者 × 读者 × 同步机制 × 判定）

| # | 载体 | 位置 | 写者 | 读者 | 同步机制 | 判定 |
|---|------|------|------|------|----------|------|
| D1 | `InMemoryTeamManager.teams`/`sessionToTeamId`（CHM×2） | team/InMemoryTeamManager.java:66-67 | createTeam putIfAbsent:139、disband compute:173-184、bind compute:289-304（含跨 map 会话索引写） | getTeam/getTeamBySession/getActiveTeams | CHM 原子复合操作 + 激活转换 compute 内重检:311-316 | 通过（配额检查-然后-行动缺口另案 **C1-023**） |
| D2 | `Team.status`/`disbandedAt`（plain）+ `TeamMember.sessionId`/`actorId`（plain） | team/Team.java:55-56、TeamMember.java:44-45 | 仅经 manager 的 CHM compute（javadoc 契约 :32-44） | 经 CHM get/values 迭代 | CHM 检索操作与前置更新建 HB | 通过 |
| D3 | `InMemoryTeamTaskStore.tasks`/`teamIndex`（CHM + 不可变快照列表） | team/InMemoryTeamTaskStore.java:59-60 | createTask putIfAbsent+compute:79-89、claim/complete/abandon/reclaim 四转换 compute CAS:144-297 | 读路径快照 | CHM per-key compute CAS + epoch 绑定 + `unmodifiableList` 快照；TeamTask 不可变（TeamTask.java:48-94） | 原子性通过；**无删除 API 无界累积 → C1-024** |
| D4 | `TeamTaskSchedulerDaemon` 换装字段（daemonCoordinator:55/daemonOwnerId:56/scanLeaseMs:57 plain + scheduleHandle:58 volatile） | scheduler/TeamTaskSchedulerDaemon.java:55-58 | 运行期公开 setter:204-225 | scan 线程:299/314/392 | volatile handle + synchronized start/stop:232-260；其余 plain 无 volatile | **C1-022**（C1-013 同族） |
| D5 | `TaskDispatchCoordinator` 换装字段 + `ownedSpawnExecutor` 惰性自建 + `inFlightDispatches`（CLQ） | scheduler/TaskDispatchCoordinator.java:40-50 | setter:82-96、resolveSpawnExecutor:113-123（单 scan 线程触达）、whenComplete remove:259-260 | scan 线程、awaitInFlightDispatches:150-166 | CLQ add/remove 成对（future 必 settle）；惰性池检查-然后-行动由单 scan 线程掩护；stop→shutdownNow 与在途 dispatch 的拒绝窗口（后果=honest failure，任务留 CLAIMED）注记 | **C1-022**（注记） |
| D6 | `TeamTaskFlowOrchestrator` 换装字段（memberSpawner:169/taskMemberRouter:187/spawnStepExecutor:206/ownedSpawnExecutor:215/memberExecTimeoutMs:225） | flow/TeamTaskFlowOrchestrator.java:169-225 | setter:357-437、resolveSpawnExecutor:468-478、close:448-458 | buildGraphForExecution（调用线程） | per-invocation 新建（tool/TeamExecuteFlowExecutor.java:234 DD#3）→ 实际单线程；共享实例跨线程直调才暴露 | **C1-022**（注记） |
| D7 | `ExecutionRecorder`（per-run，跨并行 DAG 节点共享） | flow/ExecutionRecorder.java:38-46 | 并行节点 markStart/markComplete/markFailed | buildResult:79-97 | CHM + AtomicInteger + AtomicReference（javadoc :25-26 契约与实现一致） | 通过 |
| D8 | flow step 载体（MemberAgentTaskStep/SpawnMemberAgentTaskStep/Bound·Spawn·MixedMemberFanOutStep/DispatchTarget/MemberExecOutcome 等） | flow/*.java（字段扫描全 `private final`） | 构造期一次性 | 同一 DAG run | final + per-run 栈封闭 | 通过 |
| D9 | `DefaultBackgroundTaskRegistry.tasks`（CHM<String,Entry>）+ Entry per-entry monitor | tool/DefaultBackgroundTaskRegistry.java:59,322-354 | submit:100、run:127、complete:134-170（synchronized(entry)，first-terminal-wins 幂等）、stop:259-285 | snapshot/readOutput:183-232、bridge:679（引擎侧直读 Entry volatile 字段） | CHM + per-entry monitor + volatile 字段群 | 原子性通过；**tasks 无删除路径 → C1-017**；run/attachFuture 的 future 迟注册窗口注记 |
| D10 | tool executor 换装字段（CallAgentExecutor.maxDelegationDepth:116、TeamExecuteFlowExecutor.memberSpawner:123） | tool/*.java | 运行期 public setter（CallAgentExecutor:126-132、TeamExecuteFlowExecutor:145） | 每工具调用（executor 单例跨会话共享） | plain 非 volatile | **C1-022** |
| D11 | `InMemorySessionStore.sessions`（CHM，save=no-op 共享活引用） | session/InMemorySessionStore.java:21 | getOrCreate computeIfAbsent:25 | 全部线程 | CHM；对象内并发由引擎单写者纪律掩护（破口已立案 C1-008） | 通过（沿批次 C 判定） |
| D12 | `FileBackedSessionStore` sessions/persistedStates/sessionLocks（CHM×3）+ per-session 锁 | session/FileBackedSessionStore.java:106-122 | save 全序列化（锁内）:311-323、loadPersisted **锁外装载**:395-433、remove:177-203 | get:163-174、listAllSessions:244-288 | per-session 锁串行 diff→write→state-update（javadoc :113-121）；装载路径不在锁内 | **C1-021** |
| D13 | `DBSessionStore` 同三件套 + 租户模式缓存旁路 | session/DBSessionStore.java:79-93,139-141 | save:335-357（锁内）、loadFromDb:581-622（锁外） | get/getOrCreate（**租户模式每次新建 AgentSession 实例**） | per-session 锁（仅同进程 save 侧）；DB 条件 UPDATE CAS | **C1-020 / C1-021** |
| D14 | `SessionFileWriter.ioLock`（per-store 实例单锁） | session/SessionFileWriter.java:69,105-136 | write（**serialize 在锁外**:103） | — | ioLock 串行文件写 + tmp + `ATOMIC_MOVE` 原子替换:113-123 | 通过（serialize 锁外迭代活集合的暴露面与 C1-008 同根——无同步的会话并发变异，不重复计数） |
| D15 | `DeferredAckMailbox` pending/inFlight/deadLetter/keysByCorrelationId | message/DeferredAckMailbox.java:58-69 | offer/poll/ack/nack（单 lock:99-209） | 计数器/快照访问器 | 单监视器全序化 + AtomicLong deliveryId + correlationId 去重索引在 ack/dead-letter/nack(requeue=false) 三路径配对清理 | 通过（**deadLetter 无界保留 → CL-010**） |
| D16 | `LocalAgentMessenger.pendingRequests`（CHM）+ `replySubscriptions`（CHM + DCL） | message/LocalAgentMessenger.java:51-57 | request put/两参 remove/whenComplete 清理:93-105、ensureReplySubscription DCL:123-134 | ReplyDemultiplexer:197-217 | CHM + `remove(correlationId, future)` 两参删除 + DCL；pending 条目终态清理配对 | pendingRequests 通过；**replySubscriptions 只增不删 → C1-018** |
| D17 | `DBMessageService.consumers`（CHM）+ poller/closed/started | message/DBMessageService.java:114-123 | subscribe put:304 / DbSubscription.cancel→unregisterConsumer:650-653 | pollAllTopics/pollTopic:315-365 | CHM；**同 topic put 静默替换 + cancel 无身份校验**；pollTopic 三重 containsKey 复核:332/344/351 | **C1-019**；config setter（:120-123）非 volatile 并入 C1-022 注记 |
| D18 | `InMemoryMemoryStoreProvider.stores` / `AdapterBackedMemoryStoreProvider.stores`（CHM） | memory/InMemoryMemoryStoreProvider.java:20、AdapterBackedMemoryStoreProvider.java:51 | getOrCreate computeIfAbsent:27/:76 | memory 工具（AgentToolDispatcher 持 provider） | CHM 原子装载 | **无删除路径 → C1-016**（引擎默认装配 DefaultAgentEngineConfig:126） |
| D19 | `InMemoryAiMemoryStore.items` / `InMemoryStorageAdapter.items` / `InMemoryVectorAdapter.index`（per-session 实例内部） | memory/InMemoryAiMemoryStore.java:38、InMemoryStorageAdapter.java:49、InMemoryVectorAdapter.java:46 | add/update/remove（CHM 单操作）；vector/storage 全方法 synchronized | 读路径（stream/values 迭代） | CHM 单操作原子；复合读 `synchronized(this)`（readBudgeted:92）与未持锁的写者无互斥——但不存在复合写，无实害 | 通过（javadoc "each mutation is guarded by synchronized(this)" :28-30 与实现不符 → CL-009；items 无 TTL 为已声明 deferred） |
| D20 | `InSessionCompactionSnapshotArchive.store`（HashMap，per-session） | session/InSessionCompactionSnapshotArchive.java:38 | compaction 写侧（ReAct 单线程） | read-back/审计（同线程工具） | 引擎单写者纪律 + `List.copyOf` 防御拷贝:55 | 通过（封闭性依赖单写者约定，同批次 B #B14 注记口径） |

- **批次**：CRA WI 3.1 批次 E/5（C1 收官批），原子分区 E。审读根：`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/` 下 16 个小包全量逐文件审读——budget（3f/213）/compact（14f/1,729）/completion（7f/727）/conflict（7f/531）/contribution（6f/559）/fencing（5f/478）/guardrail（14f/1,192，含 rule/ 子包 7f）/hook（7f/578）/middleware（7f/521）/quota（6f/526）/recipe（1f/305）/repair（8f/614）/router（5f/511）/skill（14f/1,704）/talent（2f/102）/usage（6f/533）+ 根 3 文件（NopAiAgentErrors/NopAiAgentException/SessionIds，335 行），合计 115 文件 / 11,158 行，范围内无 `_` 前缀生成文件。方法与盲审纪律同批次 A–D。基线复核（批次 E 范围）：C1a 节 16 包+根命中经人工复核全部为噪声（static 方法声明 / import static），自验 grep 复核真实 static 非 final 可变字段为 **0**（`InMemorySpillStore.SEQ` 为 `static final AtomicLong` 不可变引用且 incrementAndGet 使用正确，不入账）；C1b/C1c 节对 16 包+根命中为 **0**。**C1 维度五批次（A=nop-ai-core，B=security+plan，C=engine+runtime+model+reliability，D=team+tool+session+message+memory，E=16 治理小包+根）至此收官**。
- **降档裁定（本批次）**：16 包为治理原语 + 钩子/中间件装配 + 技能/配方装配面，触达形态分三档：(a) 请求触达共享态——`InMemoryWriteIntentRegistry`（引擎默认装配 DefaultAgentEngineConfig:145，每写工具派发注册）与 `InMemoryContributionRegistry`（opt-in，每请求 prompt 装配/每执行 hook 解析读）不适用启动期降档；(b) per-execute 栈封闭——`DefaultHookRegistry` 每次执行由 `AgentExecutorResolver.resolveExecutor`:151 新建（fromAgentModel → resolve 链内完成全部注册 → ReActAgentExecutor 同线程消费），EnumMap/ArrayList 无同步但封闭性成立；(c) 装配期一次性——SmartModelRouter Builder（build() List.copyOf 防御拷贝后 final 发布）、quota/DefaultResourceGuard（final 不可变配置 + 计数外置）、completion/guardrail 判定器 config、ChainRepairer stages、DbUsageRecorder（无共享可变状态）、各 NoOp 单例（static init 安全发布），逐站点降档通过。

### 批次 E 共享可变状态清单表（16 治理小包 + 根，载体 × 写者 × 读者 × 同步机制 × 判定）

| # | 载体 | 位置 | 写者 | 读者 | 同步机制 | 判定 |
|---|------|------|------|------|----------|------|
| E1 | `DefaultHookRegistry.hooks/middlewares/executionMiddlewares`（EnumMap→ArrayList ×3） | hook/DefaultHookRegistry.java:38-45 | register/registerMiddleware/registerExecutionMiddleware:57-82（computeIfAbsent().add 复合非原子——EnumMap 缺省实现 get/put 两步） | AgentHookInvoker:63/116/144 每生命周期点读取、buildChain:95-101 | 无同步；**registry 为 per-execute 新建**（AgentExecutorResolver:151，注册在 resolve 链内完成、执行在同线程） | 通过（per-execute 栈封闭；`getHooks` 返回 live view 的 unmodifiable 包装在封闭纪律下无暴露；公开 IHookRegistry 运行期换装面同 C1-013 族注记） |
| E2 | `FileSystemSkillProvider.cached`（volatile） | skill/FileSystemSkillProvider.java:54 | getSkills DCL:66-77 synchronized(this) 惰性装载 | SkillResolver.loadRegistry:111 每次技能解析 | volatile + synchronized DCL 正确；unmodifiableCollection 只读包装 | 通过（设计 §7.3「skills 不运行期变更」声明一致） |
| E3 | `InMemoryWriteIntentRegistry.intentsByPath`（CHM<String,List>） | conflict/InMemoryWriteIntentRegistry.java:40 | registerAndGetConflicting computeIfAbsent:53 + synchronized(bucket) 复合读改:61-69（每写工具派发，AgentSecurityConsultation:585） | 同路径读 + releaseSession:82-86（4 终局路径：AgentSessionLifecycle:393/559/958、DefaultAgentEngine:1019） | CHM + per-bucket monitor（TOCTOU-safe，javadoc :21-33 声明与实现一致） | 复合原子性通过；**路径键只增不删 + releaseSession O(全部历史路径) 全桶扫描 → C1-026** |
| E4 | `InMemoryContributionRegistry.byType/bySource`（CHM×2 + 内层 LinkedHashMap monitor） | contribution/InMemoryContributionRegistry.java:69,76 | register:87-107（synchronized(inner) 内联维护 bySource）；unregisterSource:116-136（**bySource.remove 在全部内层锁之外先行**） | getContributions:144-175 锁内快照（AgentPromptAssembly:217 每请求 / AgentExecutorResolver:225 每执行读） | CHM 检索 + 内层监视器；register×register 交叉源碰撞锁内 fail-loud:91-97 | **C1-025**（unregister 与 register 交叠 → 已完成注册被过期键快照回删） |
| E5 | `DefaultFencingTokenService.issueCounters/recordedHighWater`（CHM<String,AtomicLong>×2） | fencing/DefaultFencingTokenService.java:58-59 | issue computeIfAbsent+incrementAndGet:63-64；validate CAS 循环:76-89 | 同 | CAS only-if-greater 正确（无丢失更新，javadoc :20-25 与实现一致） | 原子性通过；**双 map 无删除/复位 API → C1-027**（休眠面：引擎零接线） |
| E6 | `InMemorySpillStore.store`（HashMap）+ `SEQ`（static final AtomicLong） | compact/InMemorySpillStore.java:24,27 | put/get/delete:47-65（spill 写入=ToolResultTruncator 派发路径） | ReadSpillExecutor 读回（tool 包，批次 D 口径） | 无同步；per-session 实例（AgentSession:499-503 惰性 getOrCreateSpillStore），单写者=ReAct 线程 | 通过（D11/D20 同一口径，封闭破口引 C1-008；惰性 getOrCreate 为 plain 字段检查-然后-行动，理论双实例面同族注记） |
| E7 | `InSessionCompactionArchive.store`（HashMap） | compact/InSessionCompactionArchive.java:36 | put putIfAbsent:47（ReferenceCompactionStrategy 压缩写侧） | getByHash/contains 读回（同线程工具） | 无同步；per-session（AgentSession:461-464），单写者纪律 | 通过（同 E6；内容寻址去重，长寿命会话内堆增长为设计 §8.2 已声明语义——archive 即压缩换堆的设计本体） |
| E8 | `SmartModelRouter.tierSequences` | router/SmartModelRouter.java:69 | Builder 阶段 EnumMap 变异:276-295；build() List.copyOf 防御拷贝:338-341 | route/getFallback 只读:138,205 | final + 构建后不可变 | 通过 |
| E9 | `RuleGraphGuardrail`（ruleSet/resolver/aggregator final 三件套）+ `GuardrailRuleSet`（unmodifiable+构造期 fail-loud 校验）+ `RuleResultAggregator`（final mode） | guardrail/rule/RuleGraphGuardrail.java:49-51、GuardrailRuleSet.java:44-58、RuleResultAggregator.java:40 | 构造期一次性 | check 每内容检查只读遍历:83-127 | 不可变 + final freeze | 通过（**每次检查重新 Pattern.compile ×3 站点 → CL-011**，性能面非可见性缺陷） |
| E10 | completion 判定器（Rule/Llm × config final） | completion/RuleBasedCompletionJudge.java:31、LlmCompletionJudge.java:49 | 构造期 | decide 每完成判定 | final config；per-execution ctx 变异（tokensUsed:231、metadata:239-240）由单 ReAct 线程串行 | 通过（**judge LLM 直调无超时 → CL-012**） |
| E11 | `DefaultResourceGuard.config`（final QuotaConfig） | quota/DefaultResourceGuard.java:45 | 构造期 | checkConcurrent 每配额判定:65-87 | final 不可变；计数由执法点供给（guard 本体无状态，javadoc :33-37 声明与实现一致） | 通过（检查-然后-行动缺口在执法点：InMemoryTeamManager=**C1-023**、InMemoryActorRegistry=批次 C 清单 C14 注记） |
| E12 | usage 双实现 | usage/SimpleUsageRecorder.java:22（无状态）、DbUsageRecorder.java:92-93（final dataSource/tenantResolver） | record 每 LLM 调用（每 record 独立 try-with-resources Connection:150/188） | — | 无共享可变状态；initSchema 构造期单线程:121-128 | 通过（javadoc :50-53 线程安全声明与实现一致） |
| E13 | `ChainRepairer.stages` + 4 个无状态 stage | repair/ChainRepairer.java:32,43-47 | 构造期 add | repair 每次 LLM 工具调用后遍历:74-80 | final List（构造后无变异 API）；stage 均无状态（字段扫描仅 final toolManager） | 通过 |
| E14 | RecipeResolver（静态工具） | recipe/RecipeResolver.java:97-188 | resolve 装配期（resolveExecutor 链内） | — | clone-before-mutate（裁定 I:107-108）；合并状态全为局部新建集合 | 通过（缓存共享实例未被就地变异——`_gen` 模型冻结协议之外的正确姿势） |
| E15 | `HookContext`（per-event 可变载体）/`MiddlewareChain`（final 链节）/`AttemptContext`（不可变） | hook/HookContext.java:11-18、middleware/MiddlewareChain.java:29-31、middleware/AttemptContext.java:25-27 | AgentHookInvoker 每次 invoke new:67/120/152 | 同链执行线程（onion 串行） | per-invocation 新建 + proceed 递归新建子链:60 | 通过（reenter 语义由 executor 重入计数守卫——批次 C C21 口径） |
| E16 | 根 3 文件 + 其余无状态原语（SessionIds/NopAiAgentErrors 常量表；budget NoOp；talent；conflict FailFastStrategy/contribution NoOp/guardrail NoOp/hook NoOp/middleware FilterChainResolver 静态解析/router PassThrough/skill NoOp 群；值对象 WriteIntent/Contribution/FencingToken/BudgetSnapshot/RoutingResult/SkillAssemblyResult 等） | 各文件（字段扫描：static final INSTANCE / 纯静态 / 全 final 字段） | 类初始化一次性 | 只读 | 类初始化 JLS 安全发布 + static final / final freeze | 通过 |

---

## 发现

### [C1-001] PromptTemplateManager 对全局缓存 PromptModel 的非原子惰性记忆化（检查-然后-行动，F10）

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/prompt/PromptTemplateManager.java:16-24`；`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/model/PromptModel.java:23-35`
- **证据片段**:
  ```java
  // PromptTemplateManager.java:17-23 —— promptModel 来自 ResourceComponentManager 全局缓存（跨线程共享同一实例）
  PromptModel promptModel = (PromptModel) ResourceComponentManager.instance().loadComponentModel(promptPath);
  IPromptTemplate promptTemplate = promptModel.getPromptTemplate();
  if (promptTemplate == null) {
      promptTemplate = new ModelBasedPromptTemplate(promptModel);
      promptModel.setPromptTemplate(promptTemplate);   // 对共享缓存模型的 plain 写，无 volatile/无锁
  }
  return promptTemplate;
  ```
  ```java
  // PromptModel.java:23,33-35 —— 非 volatile 惰性字段挂在全局缓存的单例模型上
  private IPromptTemplate promptTemplate;
  public void setPromptTemplate(IPromptTemplate promptTemplate) { this.promptTemplate = promptTemplate; }
  ```
- **严重程度**: P2
- **并发场景**: HTTP worker / Agent 执行线程池多请求并发，同一 prompt（如 `nop.ai.prompts/xxx.prompt.yaml`）首次被并发加载。`loadComponentModel` 返回的是 `ResourceComponentManager` 全局组件缓存中的**同一** `PromptModel` 实例（已复核 `ResourceComponentManager.loadComponentModel`:386-390 → `cache.require(resourcePath).model`，装载期 init 在缓存装载锁内完成，但**发布后**的 memoization 写在锁外）。
- **触发时序**: T1（请求 A，worker-1）执行到 `getPromptTemplate()` 读得 null（首用）→ T2（请求 B，worker-2）同点也读到 null（promptTemplate 写尚未发生/不可见）→ T1 构造 TemplateA、T2 构造 TemplateB（重复构造）→ T1 `setPromptTemplate(A)`、T2 `setPromptTemplate(B)` plain 写交叠 → 同一共享模型最终留存 A 或 B 不确定；后续读取线程因无 volatile 理论上可持续读到 stale null 并重复构造，或读到经非安全发布的 wrapper。
- **现状**: 典型 F10「检查-然后-行动守卫不原子」落在提示词点名的全局模型缓存共享面上。后果有界：`ModelBasedPromptTemplate` 仅含一个 `final` 字段（JMM final freeze 保证即使竞态发布其自身字段可见），wrapper 无状态，A/B 语义等价——实际损害为重复构造与 memoization 失效，不产生撕裂状态或错误数据。
- **风险**: 不修复的持续代价：并发首用窗口内每次请求重复构造 wrapper（轻量，无累积）；共享缓存模型被请求线程反复 plain 写，与该模型的其他并发读者（`getTemplate()/getInputs()` 遍历）形成无同步写-读对，依赖运气而非契约。该模式一旦被后来者复制到携带可变非 final 状态的模板实现上（`IPromptTemplate` 是公开扩展接口），立即升级为可见性缺陷。
- **建议**: 最小修复：`PromptModel.promptTemplate` 字段加 `volatile`（写-读建立 HB，重复构造无害化）；或去掉 memoization——`loadPromptTemplateFromPath` 每次 `new ModelBasedPromptTemplate(promptModel)`（wrapper 无状态，构造成本可忽略，且消除对共享模型的写面）。优先后者（零共享写）。同族站点若需原子化，参考平台 `MapCache`/CHM `computeIfAbsent` 语义。
- **失效面枚举**（F3 同族清扫：全部「检查-然后-放入缓存」形态逐一登记）:
  - `PromptTemplateManager.loadPromptTemplateFromPath`（本条，live，热路径）；
  - `PromptOutputModel.getMarkdownTplWithoutDetail`（同族兄弟，休眠面，立案为 C1-002）；
  - `ChatServiceImpl.checkRateLimit`:357 / `EmbeddingServiceImpl.checkRateLimit`:217 —— CHM `computeIfAbsent`，原子，通过；
  - `LlmConfigHelper.resolveApiKey`:140 —— `MapCache(threadSafe=true).computeIfAbsent`，原子，通过（负缓存语义为既定契约，非并发缺陷）；
  - `ThresholdBreaker.allowCall/recordSuccess/recordFailure` —— `entries.compute` 单临界区，通过；
  - `ProviderFailoverQueue.recordProviderFailure`:72 —— `computeIfAbsent`+`synchronized(entry)`，通过；
  - `ResourceComponentManager.loadComponentModel(url,transform)`:343 —— `entry.transformed.computeIfAbsent`，平台设施，原子，通过。
- **信心水平**: 确定（竞态机制确定；后果良性为实测代码结构得出）
- **误报排除**: 不属于「无状态单例被并发调用是设计意图」——被写的是全局缓存模型的实例字段，写点在请求热路径而非装配期；也不属于「CHM/原子原语使用」误报类——此处正是原语缺失的裸检查-然后-行动。
- **复核状态**: 保留(P2)｜新发现

### [C1-002] PromptOutputModel.getMarkdownTplWithoutDetail 惰性发布「构造后变异」的可变对象（休眠面，F1 同构）

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/model/PromptOutputModel.java:53-62`（字段 :20）；上游入口 `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/model/PromptModel.java:55-58`
- **证据片段**:
  ```java
  // PromptOutputModel.java:53-62 —— 挂在全局缓存模型上的非 volatile 惰性记忆化
  public MarkdownDocument getMarkdownTplWithoutDetail() {
      if (markdownTplWithoutDetail == null) {
          if (markdownTpl != null) {
              MarkdownDocument doc = markdownTpl.cloneInstance();
              doc.removeSectionWithTag(MarkdownConstants.TAG_DETAIL);  // 构造后变异
              markdownTplWithoutDetail = doc;                          // plain 写发布可变对象
          }
      }
      return markdownTplWithoutDetail;
  }
  ```
- **严重程度**: P2（休眠面：`getMarkdownTplWithoutDetailForResult`/`getMarkdownTplWithoutDetail` 经全库 grep 确认当前 main 代码零调用方，仅模型类自身定义；一旦任一未来调用方接入即激活）
- **并发场景**: 多请求线程并发首次读取同一 prompt 输出模型的 markdown 模板（激活后为请求触达路径，不适用启动期降档）。
- **触发时序**: T1 执行到 `markdownTplWithoutDetail == null` 判空（true）→ T2 同点判空（true，写不可见）→ 双方各自 clone+`removeSectionWithTag` 变异 → T1 先 plain 写 doc-A、T2 后 plain 写 doc-B → (a) 重复构造、后写覆盖；(b) 读取方 T3 经无同步读拿到 doc-B 引用而无 HB 边，可能观察到 `removeSectionWithTag` 执行到一半的 MarkdownDocument 内部 section 列表（可变对象非安全发布 → 撕裂视图）。
- **现状**: 与 C1-001 同族（共享缓存模型上的裸惰性记忆化），但比 C1-001 更重：发布的是**构造后仍被变异的可变对象**，而非 final-only wrapper——unsafe publication 的后果从「无」升级为「读者可见部分变异的文档结构」。
- **风险**: 激活后：同一模型的两批请求可能拿到不同 doc 实例（身份发散）；撕裂读会表现为 markdown 模板匹配（`doc.matchTpl`）结果不确定的偶发解析错误，难复现。当前为休眠面，风险敞口 = 未来调用方接入成本（public API，零门槛）。
- **建议**: 与 C1-001 同一修复族：字段加 `volatile` 只解决 HB，正确修法是**去掉惰性**——`markdownTpl` 已在 `init()`（装载临界区内）赋值，`getMarkdownTplWithoutDetail` 的克隆-变异应一并移入 `init()` 预计算（或 `synchronized`/`computeIfAbsent` 化），使共享模型在发布后回归只读。
- **失效面枚举**（F3 同族清扫）:
  - `PromptOutputModel.getMarkdownTplWithoutDetail`（本条，休眠）；
  - `PromptOutputModel.getMarkdownTitles`/`getXdefObj`/`getXdefForAi` —— 全部 `init()` 期一次赋值、发布后只读，通过；
  - `PromptModel.promptTemplate` —— 同族 live 兄弟，立案 C1-001；
  - `_gen/_PromptOutputModel` 及其他 `_gen` 模型基类字段 —— 生成文件，装载期写后只读，按纪律不立案且无惰性写形态；
  - `LlmModel`（纯生成子类，无自定义字段/无惰性写）—— 通过。
- **信心水平**: 确定（机制）/ 有趣的猜测（实际损害——依赖未来调用方接入）
- **误报排除**: 不是「启动期单线程写降档」可豁免的站点——该 getter 是公开运行期 API，写点在调用时而非装载期；也不是 owner doc 已声明边界——无任何文档声明该访问器的线程安全契约。
- **复核状态**: 保留(P2)｜新发现

### [C1-003] 7 个 static 非 final 单例持有者构成无同步的运行期换装面（extension 面 + 可见性）

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/response/XmlResponseParser.java:23-34`、`MarkdownResponseParser.java:8-18`、`CodeResponseParser.java:8-16`、`YamlResponseParser.java:10-16`、`JsonResponseParser.java:9-17`、`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/prompt/DefaultSystemPromptLoader.java:11-18`、`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/initialize/PromptSyntaxStdDomainHandler.java:28`
- **证据片段**:
  ```java
  // XmlResponseParser.java:23-34（其余 4 parser + DefaultSystemPromptLoader 同构）
  private static XmlResponseParser s_instance = new XmlResponseParser();   // 非 final 非 volatile
  public static void registerInstance(XmlResponseParser parser) {          // 公开换装 API，无同步
      s_instance = parser;
  }
  public static XmlResponseParser instance() { return s_instance; }        // 请求热路径读
  ```
  ```java
  // DefaultSystemPromptLoader.java:11 —— 更宽：public static，任何代码可直接覆写
  public static DefaultSystemPromptLoader _instance = new DefaultSystemPromptLoader();
  ```
- **严重程度**: P2（可写出具体时序，但损害以「库内无换装调用方」为前提 → 扩展面理论风险；按纪律标注）
- **并发场景**: 换装线程（扩展方/IoC 后置装配/运维代码在运行期调用 `registerInstance` 或直接改 `INSTANCE`/`_instance`）与请求线程（`ChatExchange.parseXmlContent` 等 5 个解析入口 :184-213、`AiCommand.addSystemPrompt`:401，均在 LLM 响应解析热路径）并发。
- **触发时序**: T1（换装线程）执行 `registerInstance(customParser)`，plain 写 `s_instance` → T2（请求线程）在同一点读到新引用但与 T1 写之间无 happens-before（非 volatile）→ 若 customParser 为携带非 final 可变字段的子类，T2 可见引用而其字段初始化不可见（unsafe publication），解析行为依赖半初始化状态；即使全状态可见，窗口内不同请求分别用旧/新 parser，换装非原子生效点。
- **现状**: 7 个持有者全部非 final 非 volatile；库内 main 代码无任何换装调用方（grep 复核，5 个 `registerInstance` 零 caller；`PromptSyntaxStdDomainHandler.INSTANCE` 仅 `AiCoreInitializer` 启动期注册一次——该站点按启动期降档论证通过）。当前 7 个默认实例均无状态或仅 final 字段，故现状无实害。
- **风险**: 该组 API 是模块的公开扩展契约（parser 可子类化、构造器 protected 专为子类设计）。任一扩展方注册携带状态的实现并在运行期换装，即获得无同步发布——症状为偶发解析错乱，与 C1-002 同为「可见性靠运气」；且 `YamlResponseParser._instance` 为 package-private 非 private、`DefaultSystemPromptLoader._instance` 为 public，换装入口比 API 更宽。
- **建议**: 统一加 `volatile`（7 处，成本为零，语义不变——换装可见 + 安全发布）；若有意限制为「启动期换装后不再变」，则收缩契约：字段改 `volatile` 之外，在 javadoc 显式声明「仅启动期调用 registerInstance」，或改经 `StdDomainRegistry` 式受控注册容器。不建议引入锁（低频写高频读）。
- **失效面枚举**（F3 同族清扫：全部 static 非 final 单例/注册点逐一登记）:
  - Xml/Markdown/Code/Yaml/Json 5 个 `ResponseParser.s_instance`（本条，运行期换装面）；
  - `DefaultSystemPromptLoader._instance`（本条，public 直写面）；
  - `PromptSyntaxStdDomainHandler.INSTANCE`（同族，但唯一写点 `AiCoreInitializer.initialize()` 在启动期 → 降档通过，不单独立案）；
  - `LlmConfigHelper.secretDir`（static 非 final，唯一非测试写点为 IoC 装配期 setter → 启动期降档通过）；
  - `LlmDialectFactory.DIALECTS`（static final 容器 + 公开 register 运行期换装 API——容器本身 CHM 原子，put/putIfAbsent 安全发布 value，通过；换装语义注记：`register(overwrite=true)` 与 `getDialect` 的 get 竞态无原子切换点，但 CHM 读到旧或新引用均安全发布，无撕裂）；
  - `NoRetryPolicy.INSTANCE`/`AlwaysClosed.INSTANCE`/`NoOpProviderFailoverQueue.INSTANCE`（static final，不可换装，通过）。
- **信心水平**: 确定（机制与清单）；损害部分为「很可能」（依赖扩展方行为）
- **误报排除**: 不是「原语使用不是问题」类误报——问题恰在原语缺失（裸 static 写）；也不是低频路径粗锁的优雅性问题——读侧是每请求热路径。基线 C1a 命中且经人工复核确认为真实载体（非 static 方法噪声）。
- **复核状态**: 保留(P2)｜新发现

### [C1-004] EmbeddingServiceImpl.retryScheduler 换装/复位路径废弃旧调度器且永不 shutdown

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/EmbeddingServiceImpl.java:88-142`
- **证据片段**:
  ```java
  // EmbeddingServiceImpl.java:88-89,122-142
  private volatile ScheduledExecutorService retryScheduler;
  private final Object schedulerLock = new Object();
  public void setRetryScheduler(ScheduledExecutorService retryScheduler) {  // javadoc: 传入 null 恢复默认
      this.retryScheduler = retryScheduler;                                  // 直接覆盖，废弃实例无 shutdown
  }
  private ScheduledExecutorService scheduler() {
      ScheduledExecutorService sched = this.retryScheduler;
      if (sched != null) return sched;                    // volatile 读，DCL 正确
      synchronized (schedulerLock) {
          if (this.retryScheduler == null) {
              ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, ...daemon...);
              this.retryScheduler = executor;             // 新实例；若此前存在被 setter 覆盖掉的旧实例 → 泄漏
          }
          return this.retryScheduler;
      }
  }
  ```
- **严重程度**: P3
- **并发场景**: 运行期配置线程调用 `setRetryScheduler(custom)` 或 `setRetryScheduler(null)`（javadoc 明示 null=恢复默认惰性创建），与请求/重试链线程并发经 `scheduler()` 取用。
- **触发时序**: T1（重试链）在某次失败后已用默认惰性实例 E1 调度过任务 → T2（配置线程）`setRetryScheduler(null)`（或注入 custom）→ E1 引用被覆盖，E1 无 `shutdown()` 调用 → 其 1 条 daemon 线程（`nop-ai-embedding-retry`）与已排队任务保留存活；若此后再 `setRetryScheduler(null)`，T3 经 `scheduler()` 再建 E2 → 每次换装循环净增一个永不回收的单线程执行器。
- **现状**: DCL 本身正确（volatile 双检 + 锁内再检，无误报点）；缺陷仅在替换语义不闭合——`register`（set）无对称 `unregister`（shutdown），F8「资源清理不对称」的轻量变体。当前库内 `setRetryScheduler` 仅测试使用，生产单例 bean 装配后不换装。
- **风险**: 长时运行进程若引入运行期重配（该 setter 是 public API），每次循环泄漏 1 daemon 线程 + 待决任务引用（持有 `EmbedCall`→`future`→调用栈引用链，任务未执行期间延迟其回收）。daemon 线程不阻塞 JVM 退出，量级为每循环 1 线程，故 P3 而非 P1。
- **建议**: `setRetryScheduler` 语义补全：覆盖前对被替换的**惰性自建**实例调用 `shutdownNow()`（注入的 custom 实例所有权归调用方，javadoc 声明即可）；或更简单——去掉「null 恢复默认」语义（null 时保留原实例），消除重复自建循环。复用平台惯例可参考 `Cancellable.appendOnCancelTask` 的成对取消模式。
- **失效面枚举**（F3 同族清扫：模块内全部自建执行器/调度器生命周期点）:
  - `EmbeddingServiceImpl.scheduler()`（本条，自建 ScheduledThreadPoolExecutor，唯一自建执行器）；
  - `ChatServiceImpl` —— 无自建执行器（`SubmissionPublisher` 默认执行器为 commonPool，JVM 共享，无需关闭；publisher per-request 随 close/terminate 回收）；
  - `callStream` 的 per-call `SubmissionPublisher` —— `onError→closeExceptionally`/`onComplete→close` 成对；异常路径缺口另立线索 CL-001（非本维度）；
  - `StandardRetryPolicy`/`NoRetryPolicy` —— 无状态无资源；
  - `ThresholdBreaker`/`ProviderFailoverQueue`/`AccountChain`/`ProviderFailoverChain` —— 纯内存状态，无线程资源；
  - `EmbeddingServiceImpl.rateLimiters`（DefaultRateLimiter，per-provider 有界）—— 无线程资源。
- **信心水平**: 确定（代码路径确定；触发依赖运行期换装行为）
- **误报排除**: 不属于「有界声明存在的池」误报——此处无任何容量/生命周期声明，且 daemon 属性不能豁免「替换不关旧」的清理不对称；不按 P1 报是因为当前唯一调用方为测试、泄漏量为每循环 1 daemon 线程，符合 P3 校准。
- **复核状态**: 保留(P3)｜新发现

### [C1-005] DefaultPermissionProvider 在共享单例上以查询方法写副作用承载 per-session 权限规则（非 volatile、跨会话规则串扰，F10）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/DefaultPermissionProvider.java:14-30,61-67`；单例持有证据 `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngineConfig.java:107,956-957`（`setPermissionProvider` 注册单一实例）；引擎读取点 `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentSecurityConsultation.java:176-178`（每工具调用 3 参 `resolve`）
- **证据片段**:
  ```java
  // DefaultPermissionProvider.java:14-16 —— 两个非 final 非 volatile 的实例级规则表，挂在被引擎单例持有的 provider 上
  private final List<AgentPermissionModel> defaultRules;
  private List<AgentPermissionModel> agentPermissions;     // 非 final 非 volatile
  private List<AgentPermissionModel> sessionPermissions;   // 非 final 非 volatile
  // :61-66 —— 5 参 resolve 是查询方法，却先写共享实例字段再读回判定
  public Permission resolve(String toolName, String agentName, String sessionId,
                            List<AgentPermissionModel> sessionPerms,
                            List<AgentPermissionModel> agentPerms) {
      this.sessionPermissions = sessionPerms != null ? sessionPerms : Collections.emptyList();  // 直接保留调用方列表引用，不拷贝
      this.agentPermissions = agentPerms != null ? agentPerms : Collections.emptyList();
      return resolve(toolName, agentName, sessionId);      // 经 buildSources() 读回刚写的字段
  ```
- **严重程度**: P1（竞态机制与触发链具体；后果为授权判定误用他会话规则 → 权限提升/误拒。前提：该 provider 是 `@SecureDefault` 出厂默认实现类，`configure`/5 参 `resolve` 为公开 API 但库内 main 零调用方——集成方按参数形态 per-call 调用即触发，见「现状」）
- **并发场景**: 单个 `DefaultAgentEngine` 的全部 agent 会话共享一个 provider 实例（engine config 单字段持有）。多会话 HTTP/Agent 工作线程并发调用 5 参 `resolve`（其签名以 `sessionPerms` 为参数，唯一合理用法就是每会话/每工具调用传入各自规则），与引擎 checkpoint 链的 3 参 `resolve`（读字段）并发。
- **触发时序**: T1（会话 A worker）执行 5 参 `resolve` 到 :64 写 `this.sessionPermissions = A_rules` → T2（会话 B worker）同点写 `this.sessionPermissions = B_rules`（A 写被覆盖，无任何同步）→ T1 继续执行 `buildSources()`:71 读到 **B_rules** 并据此对 A 的 toolName 做 deny/allow 判定 → 若 B 的规则允许而 A 的规则拒绝该工具，A 越权获得 ALLOW（权限提升）；反向则误拒。写读之间无 volatile/HB 边，即使无交叠，读线程也可能长期看到 stale/null 规则表（null 时 `buildSources` 走 defaultRules=empty → `Permission.deny("No permission rules configured")`:37，默认拒绝兜底使 stale-null 后果降为全部误拒）。
- **现状**: 写路径有两个（`configure`:26-30、5 参 `resolve`:61-67），库内 main 均零调用方（grep 复核：引擎仅走 3 参只读路径）；但字段值设计为 per-session 列表却存储在会话间共享的单例字段上，且 5 参 `resolve` 保留调用方 List 的**活引用**（不拷贝）——调用方事后变更列表同样无同步地反映进并发判定。形态 = "per-request 状态存单例" 反模式 + 查询方法写副作用（F10 检查-然后-行动的载体级变体）。
- **风险**: 不修复时，任何按 API 形态正常使用该出厂默认类的集成方获得一个跨会话串扰的权限层：偶发的、难复现的授权判定漂移（安全面），且因无同步无法用 HB 论证排除；类上亦无线程安全契约声明可援引豁免。
- **建议**: 去状态化：3 参 `resolve` 增加规则表参数（或在 checkpoint 构建时把 session/agent 规则经 `SecurityCheckpoint.CheckContext` 携带），`buildSources` 改为纯函数入参合并，删除两个字段（与同包 `SecurityCheckpointChain`/`ParentPermissionConstraint` 的不可变传参风格对齐）；过渡修复：字段改 `volatile` 并在 5 参路径做 `List.copyOf` 防御性拷贝（仅建立 HB 与快照语义，串扰窗口仍在，须在 javadoc 声明"configure 仅启动期"）。复用平台设施：无必需；纯去状态化即可。
- **失效面枚举**（F3 同族清扫：security 包全部"实例字段承载可变规则/状态 + 公开写入口"形态逐一登记）:
  - `DefaultPermissionProvider.agentPermissions`/`sessionPermissions`（本条，live API 面，休眠调用方）；
  - `DefaultPathAccessChecker.sensitivePrefixPatterns`/`sensitiveFilenames` —— 仅构造器写入、无变异 API，final freeze 通过（清单 B5）；
  - `DefaultAgentEngineConfig` 全部组件 setter（permissionProvider/toolAccessChecker/pathAccessChecker/denialLedger 等 :107-124）—— 装配期一次性换装，bean 注册链发布，通过（与批次 A 清单 #7 同判）；
  - `DefaultDenialLedger`/`FingerprintPostDenialGuard` —— 有公开写入口但写原子（CHM 复合操作），仅剩生命周期问题，另案 C1-006；
  - `ThreadLocalTenantResolver` —— 写入口 static set/clear 配对正确，通过（清单 B6）；
  - `EmbeddingServiceImpl.retryScheduler` setter 换装 —— nop-ai-core 分区，批次 A 已立案 C1-004（同族"setter 换装不闭合"，跨模块并列登记）。
- **信心水平**: 确定（竞态机制与字段清单）；触发前提「集成方使用 5 参/configure」为很可能（API 形态唯一合理用法，但当前零调用方）
- **误报排除**: 不属于「无状态单例被并发调用是设计意图」——该类恰恰不是无状态（两个可变规则表字段）；不属于「低频路径粗锁」——`resolve` 是每工具调用热路径；也不属于「原语使用不是问题」——此处是裸 plain 写读，无任何原语。休眠面已按纪律在严重度与现状栏显式标注，不虚报为 live 缺陷。
- **复核状态**: 已降级(P2)｜新发现（降级：零生产实例化，先例 WI 2.5 C7-005；生产接线即回 P1）

### [C1-006] 引擎默认账本双件套（DefaultDenialLedger.counts / FingerprintPostDenialGuard.deniedFingerprints）per-session 条目仅 reset 可删、无 TTL/容量上限（F4 无界账本）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/DefaultDenialLedger.java:48,105-110`；`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/FingerprintPostDenialGuard.java:39,85-90`（`DefaultPostDenialGuard.java:25` 委托同一实现）；单例注册证据 `DefaultAgentEngineConfig.java:122-123`
- **证据片段**:
  ```java
  // DefaultDenialLedger.java:48,105-110 —— 引擎默认实例（@SecureDefault），map 只增，唯一删除路径是显式 reset(sessionId)
  private final ConcurrentHashMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();
  @Override
  public void reset(String sessionId) {
      if (sessionId == null) { return; }
      counts.remove(sessionId);          // 无 TTL、无容量上限、无全局清理
  }
  // FingerprintPostDenialGuard.java:81 —— 每 denied action 追加一个指纹，同样仅 reset 可删
  deniedFingerprints.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(fingerprint);
  ```
- **严重程度**: P2
- **并发场景**: 单引擎全会话共享的默认单例（`DefaultAgentEngineConfig` 字段初始化即 new）。每会话首次 denial（`DefaultToolAccessChecker` 硬编码 deny 列表命中、审批门拒绝等常规路径）即在 map 中创建条目；条目按 sessionId 无限累积，跨全部并发会话线程写入（CHM 保证原子，无可见性问题——缺陷纯在生命周期）。
- **触发时序**: 非交叠竞态，而是长时运行单调增长：T1（任意 worker）对会话 S1 记录 denial → `counts.put(S1,…)` 与 `deniedFingerprints.put(S1, {fp1,fp2,…})` 留存 → main 代码唯一的账本清理点是 `AgentSessionLifecycle` 的 **resumeSession 粘性恢复路径**（`AgentSessionLifecycle.java:322` `getDenialLedger().reset`、`:331` `getPostDenialGuard().reset`——人工恢复被暂停会话时才触发，属 design §6.2 声明的暂停证据存续语义）→ 会话 S1 **正常结束/放弃**路径无任何清理钩子：未达阈值的 denial 计数与全部已记录指纹（`AgentSecurityConsultation.java:330` 每个被拒动作都 `recordDeniedAction`）按 sessionId 永久留存至进程退出；会话数 × 每 session 指纹数单调增长。
- **现状**: 原子性/可见性正确（CHM + AtomicInteger/newKeySet 复合操作，非缺陷）；缺陷为 F4 账本生命周期：两实现均无 TTL、无容量上限、无淘汰策略，唯一删除路径是粘性恢复的 reset（暂停证据存续为**已声明边界**，不属缺陷）；但**未暂停会话**（denial 未达阈值即正常结束/放弃）的计数与指纹留存不在该声明覆盖内——设计意图只解释"暂停的会话需要保留证据"，不解释"已结束会话的指纹永久留存"。javadoc 自认 "in-memory only"（DefaultDenialLedger:29-32）但未声明增长边界。
- **风险**: 长时运行服务器上按「历史全部会话 × 被拒动作数」线性增长、永不收缩： denial ledger 每 session 一个小条目（String+AtomicInteger），fingerprint guard 每 session 一组 SHA-256 指纹串（每条 32 hex chars）。绝对量级小（数万会话 × 数十指纹 ≈ MB 级），不构成短期 OOM，但属必然单调增长且无任何观测/告警钩子；`DBDenialLedger` 的 DB 表同形态（行仅 reset 删除，无 TTL）把同一问题外置到持久层。
- **建议**: 为两个账本补齐生命周期：最小做法是引擎在会话关闭路径（`AgentSessionLifecycle` 已有终态钩子）调用 `reset(sessionId)` 成对清理（F8 register/unregister 对称）；更通用做法是换用平台 `io.nop.commons.cache.CacheConfig` 有界缓存（容量+淘汰）承载 per-session 条目，或对 `deniedFingerprints` 每 session 的指纹集合设上限（超出即整会话降级为「仅计数」）。`DBDenialLedger` 同步补 TTL 清理任务（归 C6 修复面）。
- **失效面枚举**（F3 同族清扫：本模块全部 per-session/append 型账本载体逐一登记）:
  - `DefaultDenialLedger.counts`（本条，引擎默认，无 TTL）；
  - `FingerprintPostDenialGuard.deniedFingerprints`（本条，引擎默认委托链 DefaultPostDenialGuard:25，无 TTL）；
  - `DBDenialLedger` → 表 `ai_agent_denial`（同族 DB 形态：行仅 reset 删除、无 TTL——持久化侧修复归 C6，本维度登记不重复计数）;
  - `PlanExecutionState.errors`（plan 侧 append-only 错误列表 :51,237-245）—— per-execute 栈封闭、随 execute 结束整对象丢弃，无跨请求累积，通过；
  - `ThreadLocalTenantResolver.CURRENT` —— ThreadLocal 随线程生命周期，set/clear 配对正确，非账本，通过；
  - compact 包 `InMemorySpillStore`/`InSessionCompactionArchive`（团队分区批次范围，仅登记指针不展开）。
- **信心水平**: 确定（结构事实：正常结束路径无删除调用、唯一 reset 在粘性恢复路径已逐行确认；增长速率取决于 denial 频率，量级评估按条目大小实测字段类型）
- **误报排除**: 不属于「有界声明存在的缓存」误报——两实现均无任何容量/TTL 声明；不按 P1 报是因条目小、增长慢、daemon 不阻塞退出路径，符合 P2「有界但边界配置缺陷/长时运行增长」与「并发测试缺失」之间的校准（无可见性缺陷是 CHM 正确使用，不计入）。量级若与 owner 文档声明包络矛盾可在复核阶段升级。
- **复核状态**: 保留(P2)｜新发现

### [C1-007] DockerSandboxBackend 超时路径不 destroy 本地 docker CLI 进程 + reader 线程 interrupt 对 InputStream.read 无效（F8 清理不对称，与 NoOp 兄弟实现分叉）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/DockerSandboxBackend.java:262-276,515-539,547-590`；对照兄弟实现 `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/NoOpSandboxBackend.java:121-134,187-239`
- **证据片段**:
  ```java
  // DockerSandboxBackend.java:268-276 —— 超时路径只 kill 容器，从不 process.destroy() 本地 docker run CLI
  if (timedOut) {
      killContainer(containerName);              // 内部 runShortCommand 见下：自身也可能泄漏进程
      awaitReader(reader, config.getWallSeconds());  // join grace 后 interrupt——对阻塞在 InputStream.read 的线程无效
      throw new SandboxException(SandboxFailureReason.TIMEOUT, ...);   // 此后 process/reader 引用全部丢弃
  }
  // :528-539 —— runShortCommand 的 docker kill/rm 进程 waitFor(5s) 超时后不 destroy 直接返回
  private static void runShortCommand(String... argv) throws IOException, InterruptedException {
      Process p = new ProcessBuilder(Arrays.asList(argv)).redirectErrorStream(true).start();
      ...
      p.waitFor(5, TimeUnit.SECONDS);            // 返回 false（未退出）时无 destroyForcibly，进程泄漏
  }
  ```
- **严重程度**: P2（错误路径资源泄漏 + 触发链依赖 docker daemon 运行期故障；对照 P1「错误路径泄漏」因前提为外部 daemon 异常而降半档，复核可裁定）
- **并发场景**: 每个 sandbox 执行自持一个 reader daemon 线程（`execute`:254-258）与最多 3 个短命 CLI 子进程。多会话并发执行超时命令时各自独立泄漏，无共享可变状态（泄漏为 per-call 资源，非共享字段——本条按 F8「清理不对称」落在并发资源域）。
- **触发时序**: T1（worker）`execute` 到 :262 `waitFor(wallSeconds)` 超时（timedOut=true，容器内命令挂死）→ T1 `killContainer`:269——若此刻 docker daemon 已不可达/挂死，`docker kill` 子进程自身 5s 超时且不 destroy（:538），`docker rm -f` 同样 → 本地 `docker run` CLI 进程因 daemon 无响应而滞留（其 stdout 管道保持打开）→ T1 `awaitReader`:270 join grace（500ms-2s）耗尽，`reader.interrupt()`:588 对阻塞在 `InputStream.read`（:553，不可中断）的 reader 无效 → `SandboxException` 抛出，execute 返回 → 泄漏：1 个 `docker run` 进程 + 1 个 reader daemon 线程（持有 process/capturedRef 引用链）+ 至多 2 个 kill/rm 进程，每次超时叠加。
- **现状**: 机制不对称：兄弟实现 `NoOpSandboxBackend` 超时路径走 `killTree(process)`:122（descendants `destroyForcibly` + 根进程 `destroyForcibly`:220-239），Docker 路径仅 kill **容器**（远端资源），从不清理**本地** CLI 进程树；`awaitReader` 的 interrupt 兜底在两实现中同样无效（:588/:202，NoOp :199-201 注释自认 "may or may not honour interrupt"）。附带后果：grace 耗尽且 reader 未 set 时 `capturedToString`:575-578 返回 ""，已产出输出被静默丢弃（两实现同）。
- **风险**: daemon 故障窗口内的反复超时按次泄漏进程与线程（进程为 OS 级资源，量级高于批次 A C1-004 的纯线程泄漏）；泄漏的 reader 线程持有 `Process` 引用阻止其回收。正常 daemon 下 `docker kill` 成功 → CLI 退出 → EOF → 无泄漏，故为条件性错误路径缺陷。
- **建议**: 对齐 NoOp 兄弟：超时与 finally 路径补 `process.descendants().forEach(ProcessHandle::destroyForcibly)` + `process.destroyForcibly()`（复用 `NoOpSandboxBackend.killTree` 逻辑，建议提取共享工具方法消除双实现分叉）；`runShortCommand` 在 `waitFor(5s)` 返回 false 时补 `p.destroyForcibly()`；reader 兜底改为「关闭底层流」（`process.getInputStream().close()` 触发 read 抛 IOException 退出）替代无效 interrupt。输出丢失问题可顺带用 `CompletableFuture<StringBuilder>` 或在 grace 耗尽时对 captured 做一次性安全快照。
- **失效面枚举**（F3 同族清扫：security 包全部自建线程/子进程生命周期点）:
  - `DockerSandboxBackend.execute` reader 线程 + CLI 进程（本条，超时路径泄漏）;
  - `DockerSandboxBackend.runShortCommand` kill/rm 子进程（本条同案，5s 超时不 destroy）;
  - `NoOpSandboxBackend.execute` reader 线程 —— `killTree` destroyForcibly 覆盖正常超时；仅存"descendant destroyForcibly best-effort 失败 + interrupt 无效"的残余窗口（:222-233 catch ignored），量级远低，注记不立案；
  - `NoOpSandboxBackend.killTree` 内部 `waitFor(2s)`（:235）—— 意义不同（等死确认，超时即放弃但根进程已 destroy），通过；
  - plan/runtime —— 无线程/子进程创建（grep 复核 `new Thread|ProcessBuilder|Executor` 零命中），通过；
  - `DefaultLevelHintsProducer`/checkers —— 无线程资源，通过。
- **信心水平**: 确定（代码路径逐行确认：Docker 超时分支无 destroy、runShortCommand 无 destroy、read 不可中断均为语言/库事实）；触发前提（daemon 运行期故障）为很可能
- **误报排除**: 不属于「为压测写的简化并发模型」；不属于「daemon 线程不阻塞 JVM 退出即无问题」——泄漏物含 OS 进程与引用链，且 jvmdaemon 豁免只适用于退出语义；与 C1-004（批次 A，setter 换装泄漏）区分：本条在错误/超时路径（P1 校准面），彼在换装路径，互不重复计数。
- **复核状态**: 保留(P2)｜新发现

### [C1-008] injectInput 暂存分支在 resume/wake/restore 执行期间并发改写共享 AgentSession（runningInjectionTargets 仅 doExecute 注册，F1/F10）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java:617-623`（injectInput 分支判定与暂存写）、`:774`（唯一 put 点）；受害面 `engine/AgentSessionLifecycle.java:408-412`（resume 终局 save；wake:571-575、restore:971-975 同构）、`engine/ReActAgentExecutor.java:1136-1142`（LLM 轮内会话同步）与 `engine/AgentToolDispatcher.java:844-850`（每工具结果会话同步）；共享载体字段 `session/AgentSession.java:23,77`（`messages`/`pendingInjections` 均为普通 ArrayList）
- **证据片段**:
  ```java
  // DefaultAgentEngine.java:617-622 —— "running" 判定只看 runningInjectionTargets；
  // resume/wake/restore 三条执行路径从未 put（grep 复核全库仅 :774 一处 put）
  AgentExecutionContext liveTarget = runningInjectionTargets.get(sessionId);
  if (liveTarget != null) {
      liveTarget.enqueueSteering(message, semantics);
  } else {
      session.addPendingInjection(semantics.name(), ...);   // 写共享 AgentSession 的 ArrayList
      sessionStore.save(session);                            // 与 worker 线程的 save 并发
  }
  ```
  ```java
  // ReActAgentExecutor.java:1139-1140 —— worker 线程执行中对同一 session 对象做
  // 整表替换 + 持久化（InMemory 存储共享活引用；FileBacked 序列化遍历同一列表）
  persistedLlm.replaceMessages(ctx.getMessages());
  sessionStore.save(persistedLlm);
  ```
- **严重程度**: P1（竞态机制与触发链具体：向已 resume/wake 的会话并发 injectInput 是该组 API 的正常用法；后果为 ConcurrentModificationException 或撕裂持久化会话）
- **并发场景**: 单引擎全会话共享一个 `DefaultAgentEngine`；会话 S 经 `resumeSession`/`wakeSession`/`restoreSession` 派发 worker（agentExecutor 线程）执行期间，另一 HTTP/守护线程调用 `injectInput(S, msg, …)`。`runningInjectionTargets` 在这三条路径上恒为空（只有 doExecute 在 :774 注册），injectInput 必然走暂存分支，与 worker 线程形成对同一 `AgentSession` 实例的无同步双写。
- **触发时序**: T1（worker，ReAct 线程）在 resume 执行中完成一次 LLM 轮，进入 `saveLlmTurnCheckpoint`:1139 执行 `replaceMessages`（clear+addAll ctx.messages）并 `sessionStore.save`（FileBacked 存储下 JSON 序列化遍历 messages/pendingInjections）→ T2（injectInput 线程）此刻走暂存分支 :621 执行 `pendingInjections.add(...)` → T1 的序列化遍历 pendingInjections 时列表结构性变化 → ConcurrentModificationException（T1 的 save 失败，终态持久化中断）或序列化读到半新半旧状态落盘（撕裂会话文件，restore 后消息/注入不一致）。反向时序（T2 先 save、T1 后 save）则 T2 的暂存注入被 T1 的整文件覆盖写回滚丢失。
- **现状**: 默认 `InMemorySessionStore` 下 save 为 no-op（共享活引用），CME 风险点在 `replaceMessages`（clear/addAll）与遍历方（下一轮 LLM 请求构建 `new ArrayList<>(ctx.getMessages())` 同线程，不受影响）；一旦换用 FileBacked/DB 会话存储（生产持久化配置），双写即真实化。三恢复路径不注册 live 目标与 doExecute 不对称，:124-126 注释声称 "registered at slot registration"（暗示全部槽位）与实现不符。
- **风险**: 不修复时，生产持久化配置 + 运行中恢复会话的每次并发注入都是一次掷骰：最轻丢一条用户注入，最重 CME 打断终局持久化（会话停留 running 状态，依赖锁 TTL 过期后才能被其他实例 restore）或落盘撕裂内容被后续 restore 当作 source of truth 加载。
- **建议**: 最小修复：resumeSession/wakeSession/restoreSession 在 `putIfAbsent` 成功后同步 `runningInjectionTargets.put(sessionId, ctx)`、终局 finally 与 `runningExecutions.remove` 并点移除（对齐 doExecute 的双注册表纪律），使运行中恢复会话的 injectInput 走线程安全的 CLQ 活队列分支；过渡修复：injectInput 暂存分支改 `synchronized(session)` 或对 pendingInjections 换 CopyOnWriteArrayList（session 包归属另批，此处为引擎侧修复建议）。
- **失效面枚举**（F3 同族清扫：全部「引擎线程写 sessionStore/session 对象」的站点逐一登记）:
  - resume/wake/restore 执行期 + injectInput 暂存分支并发（本条，live API 面）；
  - doExecute 路径 —— injectInput 被注册表导流至 CLQ，无暂存并发（设计正确）；仅注册前同步窗口（assembleExecutionSetup 期间 put:774 未执行）存在同形态双写窗口，窗口为同步段且 doExecute 侧 :791 的 save 同为调用线程，交叠后果同本条，随本条一并修复（注册点前移到 assembleExecutionSetup 之后立即执行即可覆盖）；
  - `DefaultAgentEngine.cancelSession`:539 `session.setStatus(cancelled)`（无 save，仅状态字段单写）—— 无列表并发，通过；
  - AgentSessionLifecycle 终局 save ×3 / ReActExecutor 会话同步 ×2 / Dispatcher 每工具同步 —— 写者均为同一 worker 线程串行，互相之间通过（单写者纪律，:1445 javadoc 自认 "Single-writer safe"）；
  - `bridgeBackgroundCompletion`:689 → injectInput —— FOLLOWUP 注入，同样落入本条分支判定，随本条修复覆盖。
- **信心水平**: 确定（代码路径与字段类型逐行确认；CME/撕裂机制为 Java 集合与序列化语义事实）
- **误报排除**: 不属于「无状态单例被并发调用是设计意图」——被并发写的是 per-session 可变载体（ArrayList），且双线程分属请求/执行两域；不属于「CHM/CLQ 本身使用」误报——缺陷恰在裸 ArrayList 挂在跨线程共享对象上且无任何原语；休眠性已排除：resume/wake 是公开生命周期 API，FileBacked 存储是模块自带实现。
- **复核状态**: 保留(P1)｜新发现

### [C1-009] runningInjectionTargets 生命周期双缺口：拒绝执行路径泄漏条目（F8）+ 终局 remove-先于-terminal-save 的 get/enqueue 交叠窗口丢 steering 注入（F10）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java:765-804`（doExecute 注册）、`:949-1091`（dispatchExecution：外层 catch :1080-1089 清理清单缺 runningInjectionTargets；终局 finally :1006-1010 先 remove 后 save :1045-1056）；受害面 `:617-623`（injectInput 判定）与 `engine/ReActAgentExecutor.java:539-544`（终局残余 drain，位于 execute 返回前）
- **证据片段**:
  ```java
  // DefaultAgentEngine.java:1080-1089 —— supplyAsync 提交失败（RejectedExecutionException）
  // 的对称清理：runningExecutions / 锁 / 续期任务均回收，唯独 runningInjectionTargets 不回收
  } catch (RuntimeException e) {
      runningExecutions.remove(sessionId, handle);
      lifecycle.releaseLockQuietly(sessionId, instanceId);
      SessionLockRenewal.cancelLockRenewalQuietly(handle.renewHandle);
      throw e;                                   // :774 put 的条目永久残留
  }
  // :1007-1010 —— 终局：先摘除活目标，之后才 replaceMessages + save（:1045-1049）
  runningExecutions.remove(sessionId, handle);
  runningInjectionTargets.remove(sessionId);
  ```
- **严重程度**: P2（(a) 触发前提为线程池拒绝——cachedThreadPool 无界，实际仅在引擎 close 后提交时发生，操作期罕见但后果链长；(b) 交叠窗口窄但后果为静默消息丢失，且 T2 在 get 与 enqueue 之间任意停顿即拉宽窗口）
- **并发场景**: (a) 引擎 shutdown 后（`close()`:1119 已跑、agentExecutor 已 shutdown）仍有最后一次 execute 抵达 doExecute：:774 put 成功，:961 supplyAsync 抛 RejectedExecutionException 进外层 catch；(b) 执行收尾期：T2（injectInput 线程）:617 get 到非null liveTarget，T1（worker）越过 ReAct 终局残余 drain（executor 返回）并执行 :1010 remove 与 :1045-1049 terminal save，T2 随后才 `liveTarget.enqueueSteering`。
- **触发时序**: (a) T1 提交被拒 → catch 清理三件套但 `runningInjectionTargets["S"]=旧ctx` 残留 → 此后至 S 的下一次成功执行前，所有 `injectInput(S,…)` :617 判定"运行中"→ enqueue 进已死 ctx 的 CLQ → 消息永不 drain、永不持久化（用户输入静默丢失）；(b) T2 :617 读得 liveTarget（非null）→ T1 :1010 remove + :1042 save 完成（残余 drain 已在 executor 返回前做过）→ T2 :619 enqueueSteering 落入无消费者的队列 → 该条注入丢失，且 INJECTION_QUEUE_UPDATED 事件显示 pending=1 永不清零（可观测性假象）。
- **现状**: (a) 是 F8「register/unregister 不对称」：:774 的 put 与外层 catch 的清理不成对（三个兄弟资源都成对）；(b) 是 check-then-act 复合操作非原子：`get(sessionId)`→`enqueueSteering` 与 worker 的 `remove(sessionId)`→`terminal save` 之间无任何协调，代码注释（:1007-1009 "drain any injections that arrived during the final LLM call"）只覆盖 drain 前已入队的注入，不覆盖 drain 后、remove 前被引用住的注入。
- **风险**: (a) 每发生一次，该会话的注入通道即失联直至下次成功执行（若会话不再执行则永久失联），无日志无告警；(b) 单条 steering 消息静默丢弃——与 C1-008 同为「注入不丢」契约（design §6.2/裁定 4 loss-free 链）的破口，量级低但难复现。
- **建议**: (a) 外层 catch 补 `runningInjectionTargets.remove(sessionId)`（与 :774 put 成对）；(b) 将终局序改为「terminal save 完成后再 remove」不可行（save 依赖 ctx 已冻结），正确做法是把 `runningInjectionTargets` 的移除挪到终局 save 之后，并在 injectInput 侧把 get→enqueue 改为 `runningInjectionTargets.computeIfPresent(sessionId, (k, ctx) -> { ctx.enqueueSteering(...); return null; })` 式原子复合——若 remove 已先发生则自然落入暂存分支（消息进下一执行），消除引用住死队列的窗口。
- **失效面枚举**（F3 同族清扫：dispatchExecution/resume/wake/restore 四个执行槽的全部注册-清理对）:
  - doExecute：runningExecutions put:918/remove:1006+1083 成对；锁/续期 :914/:923 与 :1067/:1070+1086/:1088 成对；**runningInjectionTargets put:774 与 catch 清理不成对（本条 a）**；
  - resumeSession/wakeSession/restoreSession：runningExecutions 与锁/续期均成对（:299/:389、:488/:557、:892/:954 + 各自 catch）；runningInjectionTargets 三路径从未注册——其代价已单独立案 C1-008，不重复计数；
  - cancelSession 无注册行为，finally 只清 ThreadLocal:551，成对；
  - SessionLockRenewal.renewOnceSafe:66-68 对已释放会话的续期重入——release 后残留一次 tryRenew 重续/误报 lease-lost，后果为一次无效 DB UPDATE 与对已完成 ctx 的 setCancelRequested（无害），注记不立案。
- **信心水平**: 确定（(a) 清理清单缺失为逐行比对事实；(b) 交叠机制确定，触发窗口实测为语句级+线程停顿）
- **误报排除**: 不属于「CHM 单操作原子即可」误报——缺陷恰在 get 与 enqueue 两步复合不原子；不按 P1 报是因 (a) 前提（池拒绝）在 cachedThreadPool 下仅 close 后可达，(b) 窗口窄、单条丢失，符合 P2「罕见但可具体化」与「写不出高频触发链」之间的校准。
- **复核状态**: 保留(P2)｜新发现

### [C1-010] cancelSession 与四执行入口同步装配窗口的 TOCTOU：handle 未注册即判「未运行」，取消意图静默丢失（F10）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java:507-553`（cancelSession：:514 `runningExecutions.get` 判定、:534-547 else 分支）、`:765-804`（doExecute 同步装配段：assembleExecutionSetup :820-886 含 VFS 模型加载与 sessionStore 访问，注册点迟到至 :771）；同族入口 `engine/AgentSessionLifecycle.java:250-295`（resume 状态检查→tryAcquire→putIfAbsent :299）、`:455-488`（wake）、`:606-618`（restore）
- **证据片段**:
  ```java
  // DefaultAgentEngine.java:514-519 —— 判定依据只有已注册的执行槽
  AgentSessionLifecycle.CancelHandle handle = sessionId != null ? runningExecutions.get(sessionId) : null;
  if (handle != null) { ... } else {
      ...
      session.setStatus(AgentExecStatus.cancelled);      // :539 —— 未运行分支
      lifecycle.publishCancelled(sessionId, agentName, reason);   // :542 —— 对外宣告已取消
      config.getCheckpointManager().remove(sessionId);   // :546 —— 顺带清掉检查点
  ```
- **严重程度**: P2（窗口为整个同步装配段——含 VFS 模型加载与会话存储 IO，实战可达数十毫秒以上；后果为用户可见的控制面违约，无数据损坏）
- **并发场景**: T2（管理/用户线程）调用 `cancelSession(S)`；T1（请求线程）正在 `execute(S)` 的 `doExecute` 同步装配段（`assembleExecutionSetup`: loadAgentModel → getOrCreate → buildBaseExecutionContext → resolveExecutor，注册 `putIfAbsent` 发生在其后的 `registerExecutionSlot`:771-774）。resume/wake/restore 三入口同构：状态预检（:250 paused / :455 waiting / restore 非终态）与 `putIfAbsent`（:299/:488/:892）之间同为无注册窗口。
- **触发时序**: T1 进入 assembleExecutionSetup（尚未注册）→ T2 cancelSession :514 读 handle==null → 走 else 分支：setStatus(cancelled) + publishCancelled + checkpointManager.remove → T1 完成装配、:771 注册执行槽、:969 worker 置 running → 执行全程照常跑至终态。T2 已收到「已取消」事件与 completedFuture 返回，但会话实际完整执行——取消语义静默失效；附带 :546 删掉的检查点使该执行崩溃后恢复面降级。
- **现状**: cancel 判定是典型「检查-然后-行动」：`runningExecutions.get` 的快照与 T1 的 `putIfAbsent` 之间无 happens-before 协调。代码注释（:525-528、:895-900）只论证了「注册后、线程绑定前」的 forced-cancel 窗口（thread==null），未覆盖「装配期未注册」窗口——后者更宽。resume/wake 侧状态预检（paused/waiting）与取消写入（cancelled）之间还存在状态机写入竞争：两者都 plain 写 `session.status`。
- **风险**: 不修复时，「取消一个正在（即将）执行的会话」在装配窗口内返回成功但实际不生效；依赖 cancel 语义做资源控制/合规中止的调用方获得假阴性。发生概率随装配段耗时（远程模型注册表/DB 会话存储）上升。
- **建议**: 最小修复：cancelSession 的 else 分支不直接判定「未运行」，改为与执行入口互斥——先 `runningExecutions.putIfAbsent(sessionId, 取消哨兵 handle)`（或对会话取一把与 tryAcquire 同级的轻量互斥），判定后再释放；更工程化做法是把「注册执行槽」前移到 doExecute/resume/wake/restore 的最前（getOrCreate session 之后立即注册，装配失败走 catch 清理——现有 catch 清理清单已能覆盖）。恢复路径同改。
- **失效面枚举**（F3 同族清扫：全部以 runningExecutions/状态字段为判据的控制面入口）:
  - cancelSession vs doExecute 装配段（本条主面）；
  - cancelSession vs resume/wake/restore 装配段（本条同族，:250-299/:455-488/:606-618 同构窗口）；
  - cancelSession 无 handle 分支的 checkpointManager.remove:546 —— 与即将开始的执行的检查点写入（另一线程）并发删除/写入，后果为该执行失去既有检查点，随本条修复（互斥化）一并消除；
  - `getSessionStatus`:499-505 读 `session.getStatus()`（普通字段，无同步）—— 观测面读，值的时滞为既定语义（:615-616 注释自认 status 不是 in-flight 信号），注记不立案；
  - restorePendingSessions :1070 `isHeld` → restoreSession 的检查-然后-行动 —— restoreSession 内部以 tryAcquire+putIfAbsent 原子复核，通过。
- **信心水平**: 确定（窗口与两分支代码路径逐行确认；实际触发频率取决于装配段耗时）
- **误报排除**: 不属于「低频管理路径粗锁的优雅性问题」——cancel 是与 execute 同频级的用户控制面，且后果是语义违约而非性能；不属于「已声明边界」——代码无任何文档声明装配窗口内取消不保证生效。
- **复核状态**: 保留(P2)｜新发现

### [C1-011] DBCheckpointManager 未覆写 remove()：引擎终局清理对 DB 后端静默 no-op，三缓存跨会话无界累积（F4 + F3 兄弟分叉）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/DBCheckpointManager.java:93-95`（byWatermark/bySession/loadedSessions 三缓存——全类无 `remove` 覆写，grep 复核）；接口缺省 `reliability/ICheckpointManager.java:114-116`（`default void remove(String sessionId) {}`）；调用点 `engine/DefaultAgentEngine.java:546,1055,1060`、`engine/AgentSessionLifecycle.java:418,424,578,583,978,983`；覆写了的兄弟 `reliability/FileBackedCheckpointManager.java:201-218`、`reliability/ToolExecutionCheckpoint.java:90-102`
- **证据片段**:
  ```java
  // ICheckpointManager.java:108-116 —— 缺省 no-op，javadoc 点名应由带缓存的实现覆写
  // "Implementations with in-memory caches (e.g. FileBackedCheckpointManager,
  //  ToolExecutionCheckpoint) override this to clear their per-session state."
  default void remove(String sessionId) {
  }
  // DBCheckpointManager.java:93-95 —— DB 实现恰恰有三级会话缓存，却未覆写
  private final ConcurrentHashMap<String, Checkpoint> byWatermark = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, List<Checkpoint>> bySession = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Boolean> loadedSessions = new ConcurrentHashMap<>();
  ```
- **严重程度**: P2（DB 后端为生产形态的检查点管理器；终局清理是引擎在全部终态路径上的固定动作，长时运行部署必然单调增长；无可见性/原子性问题，纯生命周期缺陷）
- **并发场景**: 非交叠竞态，而是 F4 账本生命周期：每会话终局时引擎调用 `checkpointManager.remove(sessionId)`（DefaultAgentEngine:1055 等 10 处），Tool/FileBacked 实现清缓存，DB 实现命中接口缺省 no-op——`bySession`（每会话一个 synchronizedList + Checkpoint 对象含 CLOB 级 summary 字符串）、`byWatermark`（每检查点一条）、`loadedSessions`（每会话一条）跨全部历史会话只增不删。写者/读者为多会话并发 worker 与恢复路径（CHM 保证操作原子，无可见性缺陷）。
- **触发时序**: 长时运行单调增长序列：会话 S 执行 → 每工具/每 LLM 轮 `saveCheckpoint` 写穿透追加 :204-212 → 会话终局 → 引擎 remove → **no-op** → S 的全部缓存条目留存至进程退出；下一会话重复。javadoc :60-68 声明 loadedSessions 是「防重复 DB 往返的负缓存」——该设计使 remove 缺失的后果被放大：即使有人想重载也无法触发（putIfAbsent 恒失败）。
- **现状**: 接口 javadoc 的豁免理由（"NoOp, DB and future stubs continue to compile and behave identically"）与实现演化脱节：DB 实现早已不是纯透传（自带写穿透三级缓存），覆写清单（FileBacked/Tool）恰好漏掉它——F3「修实例不修类别」的镜像形态（接口演进时按旧清单覆写）。DB 表行不删除另有 :374 注释声明（"rows are never deleted"），持久层无界属已声明边界（归 C6 评估 TTL），本条只立内存缓存面。
- **风险**: 长时运行 DB 后端部署中，内存占用按「历史全部会话 × 检查点数 ×（水印串+摘要串）」线性增长且永不收缩；每条 checkpoint 携带 input/output summary（经 ToolResultTruncator 截断但阈值默认数千字符），数万会话可达百 MB 级。与 C1-006（denial ledger）同族但单条载体更大。
- **建议**: DBCheckpointManager 覆写 `remove(sessionId)`：`bySession.remove` → 同步清理对应 `byWatermark` 条目（照抄 FileBackedCheckpointManager:201-218 的模式）+ `loadedSessions.remove(sessionId)`；或抽一个三实现共享的「CHM+syncList 缓存清理」工具方法消除三处分叉（顺带消除 C1-012 的装载竞态面的修复重复）。
- **失效面枚举**（F3 同族清扫：ICheckpointManager 全部实现与终局清理调用点）:
  - `DBCheckpointManager`（本条，未覆写，三缓存无界）；
  - `FileBackedCheckpointManager.remove`:201-218 —— 覆写完整（五缓存全清），通过；
  - `ToolExecutionCheckpoint.remove`:90-102 —— 覆写完整（双索引全清），通过；
  - `NoOpCheckpoint` —— 无状态，no-op 即正确，通过；
  - 终局调用点 10 处（engine+lifecycle）—— 调用纪律成对（终态才清、paused 保留为已声明边界），通过；
  - `SessionGoalTracker.entries`/`DefaultWaitCoordinator.sessions`/`AgentSessionSupport.sessionMailboxes` —— 同族 F4 但接口无 remove 可覆写，单独立案 C1-014。
- **信心水平**: 确定（全类 grep 无 remove、接口缺省语义、10 处调用点逐一定位）
- **误报排除**: 不属于「有界声明存在的缓存」误报——三级缓存均无容量/TTL 配置；不属于「DB 行不删已声明边界」——本条立的是内存缓存面（声明只覆盖表行）；与 C1-006 区分：彼为 security 账本（批次 B 已立案），本条为 reliability 检查点缓存且是「接口缺省方法吞噬覆写义务」的独立机制。
- **复核状态**: 保留(P2)｜新发现

### [C1-012] 检查点缓存「ensureSessionLoaded 检查-然后-行动」+「装载整表替换列表」与写穿透追加无同步——空读窗口与缓存-DB 内容发散（DB/FileBacked 双实现同族，F10/F1）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/DBCheckpointManager.java:300-304`（ensureSessionLoaded putIfAbsent 判定）、`:352-378`（loadSessionFromDb：:361 byWatermark putIfAbsent、:376 `bySession.put(sessionId, 同步列表)` 整表替换）、`:204-212`（saveCheckpoint 写穿透 computeIfAbsent+add）；同族兄弟 `reliability/FileBackedCheckpointManager.java:279-281`（同形态 putIfAbsent）、`:323`（`bySession.put` 替换）、`:140-159`（写穿透追加）
- **证据片段**:
  ```java
  // DBCheckpointManager.java:300-304 —— 输家线程跳过装载，直接读可能为空的缓存
  private void ensureSessionLoaded(String sessionId) {
      if (loadedSessions.putIfAbsent(sessionId, Boolean.TRUE) == null) {
          loadSessionFromDb(sessionId);      // 慢 DB IO 在此窗口内进行
      }
  }
  // :376 —— 装载完成以整表替换收尾（与 saveCheckpoint 的 computeIfAbsent+add 无同步）
  bySession.put(sessionId, Collections.synchronizedList(active));
  ```
- **严重程度**: P2（可写出具体时序；后果为恢复期验证降级与观测误报，非数据损坏——会话本体是 source of truth）
- **并发场景**: 单引擎多会话共享一个 checkpoint manager；同一会话 S 的「首读装载」与「写穿透保存」并发：恢复路径读者（restorePendingSessions 顺序遍历、孤儿恢复守护线程 RESUME、restoreSession 校验）触发 ensureSessionLoaded，另一实例/线程的执行 worker 对 S 持续 saveCheckpoint。DB 后端缓存按 currentTenant()==null 启用（:135-137），单租户部署常态命中。
- **触发时序**: (a) 空读窗口：T1 getLatestCheckpoint(S) putIfAbsent 胜出、进入慢速 DB 装载 → T2 getCheckpoints(S) putIfAbsent 失败跳过装载 → T2 `bySession.get(S)`==null → 返回空列表/最新检查点 null → `AgentSessionLifecycle.logOpenToolCallDiff`:760-786 把全部 open tool call 判为 neverCommitted（EXECUTING 级 WARN 误报）或跳过本应执行的 divergence 校验 :670-695；(b) 发散写：T2（worker）saveCheckpoint :209 对旧列表 add 同时 INSERT DB → T1 装载完成 :376 以 DB 快照整表替换 bySession[S] → T2 刚加的条目滞留在孤儿旧列表中 → 此后读路径只见替换后列表，该检查点在缓存中永久缺失（DB 中存在），getLatestCheckpoint 可能返回错误的"最新"（时间戳比较只在残缺列表内进行）。
- **现状**: putIfAbsent 只保证「装载动作单次执行」，不建立「装载完成前读者等待」的语义——检查（loadedSessions 标记）与行动（读 bySession）之间无 happens-before；`bySession.put` 替换与 `computeIfAbsent+add` 追加分别原子、复合不原子。FileBacked 同族（:279-281/:323/:149-150）逐行同构。
- **风险**: 恢复路径的检查点验证（design §13.2 Decision A/B）与孤儿分级告警在并发首读窗口给出错误结论——verification 静默降级（不阻塞恢复，javadoc :667-669 明示 best-effort）+ WARN 级误报噪声；(b) 的缓存-DB 发散在会话终局 remove（DB 实现为 no-op，见 C1-011）后永久固化，影响该会话后续 restore 的 resume-point 选择。
- **建议**: 单锁化装载：以 sessionId 为粒度的 per-session 装载锁（`ConcurrentHashMap<String,Object>` 锁对象 + 双检 loadedSessions），装载完成前读者在锁上等待；`loadSessionFromDb` 的 `bySession.put` 改为 `bySession.computeIfAbsent(sid, k -> newList).addAll` 不可行（会重复），改为「装载临界区内先 putIfAbsent 占位列表，再向占位列表同步追加」——使写穿透的 computeIfAbsent 拿到同一列表实例，替换语义消失。或复用平台 `MapCache`/`IAsyncCache` 的原子装载语义。
- **失效面枚举**（F3 同族清扫：模块内全部「全局缓存惰性装载 + 写穿透」复合站点）:
  - `DBCheckpointManager.ensureSessionLoaded`/`loadSessionFromDb`（本条，三读入口 getLatestCheckpoint/getCheckpoint(经 bySession 路径无)/getCheckpoints）；
  - `FileBackedCheckpointManager.ensureSessionLoaded`:279-281 / `bySession.put`:323（本条同族，五缓存版）；
  - `ToolExecutionCheckpoint` —— 无惰性装载（纯内存，put 即装），通过；
  - `SessionGoalTracker`/`DefaultWaitCoordinator` —— computeIfAbsent 后同步初始化，无替换写，通过；
  - `AgentSessionSupport.ensureSessionMailbox`:56-72 —— computeIfAbsent 内完成全部初始化（含跨 map 放订阅），装载-发布单临界区，通过；
  - nop-ai-core `ResourceComponentManager.loadComponentModel` 装载锁路径 —— 平台设施（批次 A 清单 #20 已判定），通过。
- **信心水平**: 确定（机制为 CHM 语义 + 语句顺序的代码事实）；实际触发频率为很可能（依赖恢复路径与执行写并发首读同会话）
- **误报排除**: 不属于「CHM/computeIfAbsent 本身使用」误报——问题恰在 putIfAbsent 结果被当作「装载完成」信号（复合操作缺 HB）；后果按校准不虚报：验证路径 best-effort 有明确注释边界，故 P2 而非 P1。
- **复核状态**: 保留(P2)｜新发现

### [C1-013] 运行期换装面三处「synchronized 惰性 getter + 裸 setter」非对称锁：executor/锁续期线程池可被无锁覆写、孤儿自建池不 shutdown（F10/F8，批次 A C1-004 同族跨模块并列）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java:130-131,469-484`（agentExecutor：`private ExecutorService`（非 volatile）+ `synchronized getAgentExecutor` + **非 synchronized** `public setAgentExecutor`）；`engine/SessionLockRenewal.java:24,33-54`（lockRenewExecutor 同构：裸 setter :33-37 / synchronized getter :44-54）；同族注记面 `engine/DefaultAgentEngine.java:113-116,700-705`（toolProgressTracker 运行期 plain 写）、`engine/AgentCallDelegate.java:34,41-65`（messenger 非 volatile + registerCallAgentHandler 检查-然后-换装非原子）
- **证据片段**:
  ```java
  // DefaultAgentEngine.java:130-131,469-484 —— 写侧无锁、读侧 synchronized：互斥不成立
  private ExecutorService agentExecutor;            // 非 volatile 非 final
  private volatile boolean ownAgentExecutor;
  synchronized ExecutorService getAgentExecutor() {
      if (agentExecutor == null) { agentExecutor = Executors.newCachedThreadPool(...); ownAgentExecutor = true; }
      return agentExecutor;
  }
  public void setAgentExecutor(ExecutorService agentExecutor) {   // 公开 API，无锁无 volatile
      this.agentExecutor = Objects.requireNonNull(agentExecutor, ...);
      this.ownAgentExecutor = false;
  }
  ```
- **严重程度**: P3（触发前提为运行期换装——当前库内唯一调用方是 Builder 装配期单线程；机制缺陷确定，实际损害依赖集成方运行期重配行为，与批次 A C1-004 同判）
- **并发场景**: 配置/运维线程调用 `setAgentExecutor(custom)`（public API，javadoc 无「仅启动期」限定），与请求线程经 `getAgentExecutor()`（每次 dispatchExecution:1079 提交时调用）并发；SessionLockRenewal 侧同构（setter 由 engine.setLockRenewExecutor:458 暴露，getter 由每次锁续期 startLockRenewal:61 调用）。
- **触发时序**: T1（请求线程）进入 synchronized getAgentExecutor 读 `agentExecutor==null`（T2 的写不可见）→ T2（配置线程）setAgentExecutor(custom) plain 写 → T1 继续在锁内自建默认池 E1、置 ownAgentExecutor=true → 提交请求分裂：本请求走 E1，后续请求读得 custom → 两个池并存；引擎 close():1135-1143 只 shutdown ownAgentExecutor==true 时的 E1——若时序相反（先惰性建 E1 后换装 custom），own 标志被清为 false，E1 成为无 shutdown 的孤儿池（cachedThreadPool daemon 线程，60s 空闲回收线程但 ExecutorService 对象与已排队任务引用滞留）。SessionLockRenewal 侧同构；setMessenger 双调用并发时 registerCallAgentHandler:49-63 读-取消-写非原子，后写者覆盖前订阅，被覆盖者永不 cancel（消息总线处理器泄漏）。
- **现状**: 三个载体同构缺陷：字段本体非 volatile + 读写锁不对称（写侧绕开读侧的监视器锁，synchronized 对 set 无约束力）；own* 标志 volatile 但不覆盖字段发布。装配期单线程使用（当前唯一实际用法）无实害——按批次 A C1-004 先例「setter 换装不闭合」降档 P3 并登记注记面。
- **风险**: 集成方一旦按 API 形态做运行期重配（热切换线程池/改造隔离），获得：请求分裂双池、close 回收错对象、孤儿调度线程池（lockRenewExecutor 为单线程调度器，含已排队续期任务引用链）；messenger 双换装窗口内泄漏的订阅使旧 handler 继续接收消息。
- **建议**: 统一修法（成本近零）：`agentExecutor`/`lockRenewExecutor` 字段加 `volatile`（setter 写-读建立 HB，可见性闭合）；语义补全：setter 覆盖前若 `ownXxx==true` 且旧实例非 null 则 `shutdown()`（对齐批次 A C1-004 建议与 close() 的 own 语义）；`registerCallAgentHandler` 加 `synchronized`（低频配置路径，粗锁合理）。javadoc 显式声明换装的生效时点。
- **失效面枚举**（F3 同族清扫：engine 包全部「运行期公开 setter + 惰性/共享读」载体）:
  - `DefaultAgentEngine.setAgentExecutor`（本条，非对称锁 + 孤儿池）；
  - `SessionLockRenewal.setLockRenewExecutor`（本条同构）；
  - `DefaultAgentEngine.setBackgroundProgressTracker`:700-705（plain 写非 volatile，读侧 :664/:693 后台线程）—— 同族可见性面，随本条 volatile 化一并修复；
  - `AgentCallDelegate.setMessenger`/`registerCallAgentHandler`（本条注记，订阅换装非原子）；
  - `AgentSessionSupport.setMailboxFactory`:44-46（装配期写，ensureSessionMailbox computeIfAbsent 读）—— 装配期降档通过；
  - `DefaultAgentEngineConfig` 组件 setter 群 —— 批次 B 已判定（装配期 bean 发布链），不重复计数；
  - nop-ai-core `EmbeddingServiceImpl.setRetryScheduler`（批次 A C1-004）—— 同族跨模块原案，本条为其 agent 侧并列登记。
- **信心水平**: 确定（非对称锁结构与清理缺失为代码事实）；触发前提（运行期换装）为很可能
- **误报排除**: 不属于「低频路径使用粗粒度 synchronized 合理」误报——问题恰是写侧**没有**任何同步；不属于「daemon 线程不阻塞退出即无害」——孤儿调度器持有任务引用链且 close 的 own 语义被破坏；按 P3 校准与批次 A C1-004 保持跨模块一致。
- **复核状态**: 保留(P3)｜新发现

### [C1-014] per-session 状态账本三件套无任何删除路径：SessionGoalTracker.entries / DefaultWaitCoordinator.sessions / AgentSessionSupport.sessionMailboxes+订阅表（F4 无界账本）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/SessionGoalTracker.java:60`（`entries` CHM——全类无 remove/reset API，IGoalTracker 接口亦无清理方法，grep 复核）；`reliability/DefaultWaitCoordinator.java:47`（`sessions` CHM——checkWait:104-107 只置 `condition=null` 复用条目，无移除路径；IWaitCoordinator 接口无清理方法）；`engine/AgentSessionSupport.java:31-32`（`sessionMailboxes`/`sessionMailboxSubscriptions` CHM——ensureSessionMailbox:56-72 只增，engine 全生命周期无清理调用）
- **证据片段**:
  ```java
  // SessionGoalTracker.java:60,108 —— 每会话一个 SessionState（LinkedList 窗口），无任何移除
  private final ConcurrentMap<String, SessionState> entries = new ConcurrentHashMap<>();
  SessionState entry = entries.computeIfAbsent(sessionId, k -> new SessionState());
  // DefaultWaitCoordinator.java:87,104-107 —— requestWait put 后，checkWait 只消费条件不删条目
  sessions.put(sessionId, state);
  ... if (state.satisfied || isConditionSatisfied(...)) { state.condition = null; ... }
  ```
- **严重程度**: P3（单条目小：SessionGoalTracker 窗口≤5 串、WaitState 3 字段、mailbox+订阅各一引用；三者中 goalTracker/waitCoordinator 为 opt-in 功能件、mailbox 仅在 mailboxFactory 装配时创建——但均为「必然单调增长 + 无观测/告警钩子」的 F4 形态）
- **并发场景**: 写者为多会话并发 worker/恢复线程（CHM 复合操作原子，无可见性缺陷——纯生命周期问题）；增长源为「历史全部曾执行会话」，含大量已终态会话。引擎终局清理清单（DefaultAgentEngine:1045-1060 / lifecycle 各终局 finally）只覆盖 checkpointManager.remove 与 writeIntentRegistry.releaseSession，三个账本不在清单内且无处可挂（无 API）。
- **触发时序**: 长时运行单调增长序列：会话 S 执行（首次 recordIteration/requestWait/ensureSessionMailbox 创建条目）→ S 终局 completed/failed → 终局清理不触及三账本 → 条目按 sessionId 永久留存至进程退出 → 会话数 × 条目大小线性增长。DefaultWaitCoordinator 另有放大器：requestWait 为每个 TIMEOUT 条件在 scheduler 上挂一次性 deliverWake 任务（:89-95），任务持有 sessionId 引用直至触发（会话先行终态时 no-op，不取消）。
- **现状**: 与 C1-006（批次 B，denial ledger 双件套）同族同判：原子性正确、缺陷纯在 F4 账本生命周期。DefaultWaitCoordinator 的 checkWait 消费制（condition=null）意味着活跃等待被消费后条目退化为空壳（3 字段），增长量级最小的壳；SessionGoalTracker 条目含最多 5 条签名字符串（每条 toolName+args JSON 串）；mailbox 订阅条目持 IMailbox+handler+底层消息总线资源引用，三者中最重。
- **风险**: 长时运行服务器上按历史会话数线性增长、永不收缩；绝对量级 MB 级（非短期 OOM），但 mailbox 订阅条目在功能性 messenger 下可能挂总线级资源（handler 注册），泄漏面大于纯内存条目。无任何容量声明或 javadoc 边界。
- **建议**: 引擎终局清理清单补齐：为 IGoalTracker/IWaitCoordinator 增加与 IDenialLedger.reset 对称的 `remove(sessionId)`，在终态路径与 checkpointManager.remove 并点调用；mailbox 侧在终局 finally 调 `sessionMailboxSubscriptions.remove(sid).cancel()` + `sessionMailboxes.remove(sid)`（register/cancel 成对，F8 对称）；或换平台 `CacheConfig` 有界缓存承载。
- **失效面枚举**（F3 同族清扫：本模块全部 per-session/append 型账本逐一登记）:
  - `SessionGoalTracker.entries`（本条，opt-in，无删除 API）；
  - `DefaultWaitCoordinator.sessions`（本条，@SecureDefault 可选件，无删除 API；TIMEOUT 调度任务不可取消为伴生注记）；
  - `AgentSessionSupport.sessionMailboxes`/`sessionMailboxSubscriptions`（本条，条件创建，无删除调用）；
  - `ToolExecutionCheckpoint`/`FileBackedCheckpointManager` —— 有覆写 remove 且终局调用，通过（DB 版缺口另案 C1-011）；
  - `DefaultDenialLedger`/`FingerprintPostDenialGuard` —— 批次 B C1-006 已立案，不重复计数；
  - `DefaultAgentEngine.runningExecutions`/`runningInjectionTargets` —— 终局 remove 成对（缺口已单案 C1-009），通过；
  - `InMemoryActorRegistry` —— destroyActorInternal unregister 成对（终局 finally:1022-1024 destroy），通过。
- **信心水平**: 确定（三账本「无删除路径」为逐文件 grep + 接口全量复核的结构事实；增长速率取决于会话吞吐）
- **误报排除**: 不属于「有界声明存在的缓存」误报——三处均无容量/TTL 声明；不按 P2 报是因单条目小、两件为 opt-in、增长慢，符合批次 B C1-006「条目小、增长慢」的 P3 校准先例（该案 P2 因含 DB 外置形态，本条三件均为纯内存小条目）。
- **复核状态**: 保留(P3)｜新发现

### [C1-015] CalibratedTokenEstimator EMA 读-改-写丢失更新：volatile 双精度因子上的非原子复合递增（多会话共享单实例）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/CalibratedTokenEstimator.java:49,72-84`（`volatile double factor` + `record()` 读-改-写）；单实例持有证据 `engine/DefaultAgentEngineConfig.java:113`（`tokenEstimator` 为 config 单字段，全部会话的 ReAct 执行共享）；写点调用链 `engine/ReActAgentExecutor.java:1087-1089`（每成功 LLM 轮 `tokenEstimator.record(...)`，ReAct worker 线程）
- **证据片段**:
  ```java
  // CalibratedTokenEstimator.java:81-83 —— volatile 只保证可见性，RMW 三步非原子
  double observedRatio = (double) actualPromptTokens / (double) base;
  double newFactor = factor * (1.0 - SMOOTHING_ALPHA) + observedRatio * SMOOTHING_ALPHA;  // 读
  newFactor = Math.max(MIN_FACTOR, Math.min(MAX_FACTOR, newFactor));                       // 改
  factor = newFactor;                                                                        // 写（覆盖并发更新）
  ```
- **严重程度**: P3（后果为校准因子统计漂移——丢失更新使 EMA 慢收敛/偏置，不产生撕裂数据或越界值：clamp 仍生效；compaction/force-stop 的估算精度是性能面而非正确性面）
- **并发场景**: 单引擎全部并发会话的 ReAct worker 线程共享同一 `CalibratedTokenEstimator` 实例（config 单字段，AgentLoopGuard:92/ReActExecutor 各路径同用）。每个成功 LLM 轮触发一次 record——多会话高吞吐下为高频复合写。
- **触发时序**: T1（会话 A worker）:81 读得 factor=1.0 → T2（会话 B worker）:81-83 同读 1.0 并写回 1.0+r_B·α → T1 以陈旧基数完成计算写回 1.0+r_A·α → T2 的更新被覆盖丢失。EMA 每次交叠丢一次样本，收敛变慢且比率向高吞吐会话的样本偏置。
- **现状**: volatile 选择正确解决了可见性（estimateTokens 读侧无撕裂——volatile double 读写原子），但 C1 正文第 5 步点名的「`++` 复合递增」形态在本类以浮点 EMA 变体出现：无 CAS/无锁、无任何原子原语。javadoc（:18-27）声明"per instance, keyed on the dialect"——按实例校准是设计意图，但未声明并发 record 的更新语义。
- **风险**: 不修复的持续代价：校准因子（供 compaction 触发 :59 与 force-stop :92-93 阈值判断消费）系统性低估/高估风险上升，可能提前/推迟压缩与强制停止——影响 token 成本与上下文安全边际，均为有界性能/成本面，无崩溃或数据损坏。
- **建议**: 最小修复：`AtomicReference<Double>` + `accumulateAndGet`（或 `synchronized record`——写频为每 LLM 轮一次，锁开销可忽略，读侧仍走 volatile 语义）；更优：改用「每会话本地 EMA + 全局只读聚合」消除共享写面。无需引入平台重设施。
- **失效面枚举**（F3 同族清扫：engine 包全部「volatile/普通字段上的读-改-写」站点）:
  - `CalibratedTokenEstimator.record`:81-83（本条，多线程共享实例，live）；
  - `AgentExecutionContext.setTokensUsed`:177-179（`ctx.setTokensUsed(get()+…)` :1073）—— ctx 为 per-execution 单写者（ReAct 线程），栈封闭，通过；
  - `DefaultAgentEventPublisher` 失败计数 —— CHM+AtomicLong.incrementAndGet 原子，通过；
  - `ToolPhaseSession.reentryCounters`/`ExecutionState.bailCount` 等 —— per-execute 局部，单线程，通过；
  - `FileBackedCheckpointManager.saveCounters` —— CHM+AtomicInteger.incrementAndGet:150 原子，通过；
  - `InMemoryActorRuntime.createActor:279-282` 配额判定（`getActiveActors().size()` 快照 → checkConcurrent）—— check-then-act 非原子，并发 create 同租户可超限 +1；配额为软限制且引擎侧执行槽已按会话串行，注记不立案（如需闭合应改 guard 侧原子预留）。
- **信心水平**: 确定（RMW 非原子为 JMM 事实；共享单实例结构经 config 字段确认）；后果量化为很可能（取决于并发吞吐）
- **误报排除**: 不属于「volatile 字段使用即安全」误报——问题恰在 volatile 不覆盖 RMW 原子性；不按 P2 报是因后果限于估算精度（统计自愈、clamp 有界），符合 P3「性能优化候选」校准。
- **复核状态**: 保留(P3)｜新发现

### [C1-016] InMemoryMemoryStoreProvider.stores 引擎默认装配的 per-session 记忆存储注册表无任何删除路径（F4 无界账本）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/memory/InMemoryMemoryStoreProvider.java:20,23-28`（`stores` CHM——全类无 remove/evict，`IMemoryStoreProvider` 接口（21 行）亦无清理方法，grep 复核）；引擎默认装配证据 `engine/DefaultAgentEngineConfig.java:126`（`private IMemoryStoreProvider memoryStoreProvider = new InMemoryMemoryStoreProvider()` 字段初始化即默认）、消费点 `engine/AgentToolDispatcher.java:66,119`；store 内容载体 `memory/InMemoryAiMemoryStore.java:38`；同构 opt-in 兄弟 `memory/AdapterBackedMemoryStoreProvider.java:51,72-77`
- **证据片段**:
  ```java
  // InMemoryMemoryStoreProvider.java:20,27 —— 引擎默认单例上的 per-session 注册表，只增不删
  private final ConcurrentHashMap<String, InMemoryAiMemoryStore> stores = new ConcurrentHashMap<>();
  public IAiMemoryStore getOrCreate(String sessionId) {
      ...
      return stores.computeIfAbsent(sessionId, k -> new InMemoryAiMemoryStore());  // 无任何 remove 调用方
  }
  ```
- **严重程度**: P2
- **并发场景**: 单引擎全部会话共享一个 provider 实例（config 单字段，AgentToolDispatcher 持有）。任一会话首次调用 read-memory / write-memory / search-memory 工具即在 `stores` 创建条目；写者为多会话并发 worker 线程（`computeIfAbsent` 原子，无可见性缺陷——缺陷纯在生命周期）。
- **触发时序**: 非交叠竞态，而是长时运行单调增长：会话 S 执行 → memory 工具写入 N 条 `AiMemoryItem`（含用户内容字符串，无容量/TTL——`InMemoryAiMemoryStore` javadoc :32-34 自认 "retention / TTL / capacity limits" deferred）→ S 终局 completed/failed → 引擎终局清理清单（checkpointManager.remove、writeIntentRegistry.releaseSession 等，批次 C 已盘点）不触及 memory provider → store 条目（含全部 items）按 sessionId 永久留存至进程退出 → 「历史全部曾执行会话 × 每会话记忆量」线性增长、永不收缩。
- **现状**: 原子性/可见性正确（CHM 复合操作）；缺陷为 F4 账本生命周期。javadoc 的 deferred 声明覆盖「store 内容无 retention 上限」，但**注册表本身**（每历史会话一个 store 实例，空壳亦不回收）不在该声明内——声明解释"items 无 TTL"，不解释"已结束会话的 store 永久驻留 provider 且无处可挂清理"。与 C1-014（reliability/engine 三账本）同族，但本案为**引擎默认装配**（非 opt-in 功能件）且单条目可含任意大的用户记忆内容，载体重量高于 C1-014 的 P3 先例。
- **风险**: 长时运行服务器上按历史会话数线性累积：每条目为 CHM + 全部记忆内容（write-memory 写入的 LLM 产出文本，单条可达 KB 级且无上限）。数万会话 + 活跃记忆使用下可达百 MB 级；无任何观测/告警钩子、无容量声明（注册表层）。
- **建议**: 为 `IMemoryStoreProvider` 增加与 `IDenialLedger.reset` 对称的 `remove(String sessionId)`，在引擎终局清理清单（与 checkpointManager.remove 并点）调用；或换平台 `io.nop.commons.cache.CacheConfig` 有界缓存承载 per-session store（容量 + 淘汰）。`AdapterBackedMemoryStoreProvider` 同步补齐。
- **失效面枚举**（F3 同族清扫：本批次全部 per-session 注册表/账本逐一登记）:
  - `InMemoryMemoryStoreProvider.stores`（本条，live 引擎默认，无删除 API）；
  - `AdapterBackedMemoryStoreProvider.stores`（同构 opt-in，同族同修）；
  - `InMemoryAiMemoryStore.items`/`InMemoryStorageAdapter.items`/`InMemoryVectorAdapter.index` —— per-session 实例内部载体，实例随 provider 条目存续，生命周期并入本条（内容级 TTL 为已声明 deferred，不重复立案）；
  - `AgentSession.compactionArchive`/`spillStore`/`compactionSnapshotArchive` —— session 本体持有、随 session 生命周期，session 的留存策略为 store 语义已声明边界；
  - `DefaultBackgroundTaskRegistry.tasks`/`LocalAgentMessenger.replySubscriptions` —— 同族 F4 但机制独立，分别立案 C1-017/C1-018；
  - `InMemoryTeamManager.teams`（disband 后按 javadoc :46-49 有意保留供 history/audit——已声明边界，通过）；`InMemoryTeamTaskStore.tasks` —— 立案 C1-024。
- **信心水平**: 确定（无删除路径为逐文件 grep + 接口全量复核的结构事实；增长速率取决于会话吞吐与记忆使用）
- **误报排除**: 不属于「有界声明存在的缓存」误报——注册表无任何容量/TTL 声明；不属于「已声明边界」——deferred 声明只覆盖 items 的 retention 策略，未声明 provider 注册表全生命周期保留；按 P2 报是因引擎默认装配 + 内容无上界，符合「长时运行必然触发的无界增长」P1 与「条目小 opt-in」P3 之间的校准（量级介于 C1-006 与 C1-014 之间）。
- **复核状态**: 保留(P2)｜新发现

### [C1-017] DefaultBackgroundTaskRegistry.tasks 进程级后台任务注册表无删除路径：每任务 Entry（含 8KB 输出尾）永久留存（F4 无界账本）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/tool/DefaultBackgroundTaskRegistry.java:59,98-102`（`tasks` CHM——全类无 `tasks.remove`，grep 复核）；引擎装配证据 `engine/DefaultAgentEngine.java:652-656`（每引擎一个实例，`initBackgroundSupport` 默认自建）、`engine/DefaultAgentEngine.java:1119-1144`（close() 只关执行器，不清注册表——注册表亦无 clear API）
- **证据片段**:
  ```java
  // DefaultBackgroundTaskRegistry.java:59,98-101 —— runInBackground 工具的地址able状态机，条目只增
  private final ConcurrentHashMap<String, Entry> tasks = new ConcurrentHashMap<>();
  public String submit(String toolName, String sessionId, String callId, long timeoutMs) {
      String taskId = "bg-" + idCounter.incrementAndGet();
      tasks.put(taskId, new Entry(taskId, toolName, sessionId, callId, timeoutMs));  // 无任何删除路径
  ```
- **严重程度**: P2
- **并发场景**: 单引擎全部会话共享注册表（Builder/initBackgroundSupport 单实例）。每会话每次 `runInBackground` 工具调用 submit 一个条目；写者为多会话并发 worker + 后台 fork 执行线程（CHM + per-entry monitor + volatile 字段群保证原子性——无可见性缺陷，缺陷纯在生命周期）。
- **触发时序**: 长时运行单调增长序列：会话 S 调用 runInBackground → submit 创建 Entry（status RUNNING）→ 任务完成 → complete() 置终态 + bridge 注入 FOLLOWUP 通知 → **终态 Entry 永不移除**（snapshot/readOutput 可无限期回读是有意语义，但无 TTL/无容量上限/无清理调用点）→ 每历史后台任务留存一个 Entry（taskId/toolName/sessionId/callId + `outputTail` ≤8KB :48,177-179 + CountDownLatch）→ 「历史全部后台任务 × ~8KB」线性增长。
- **现状**: 原子性正确且状态机幂等（first-terminal-wins、notificationSent exactly-once）；缺陷为 F4 账本生命周期：类 javadoc 声明 "bounded in-memory tail"——**有界的是单条 outputTail，不是注册表整体**；全类及引擎 close() 均无清理路径。附带注记（不单独立案）：`run`:116-127 在 `exec.submit` 返回后才写 `entry.future`（`attachFuture`:297-302 同形态）——submit 与赋值之间 stop() 读到 future==null 时 fallback cancel 通道落空（tracker kill 通道不受影响），任务被标记 FAILED 而底层 body 未被取消；窗口为语句级，且 FAILED 终态下 body 自身 complete() 幂等落空，后果为短时僵尸执行。
- **风险**: 长时运行部署按后台任务历史数无界累积：1 万任务 ≈ 80MB（纯 tail）+ Entry 元数据；`bridgeBackgroundCompletion`（引擎 :679-697）持有 Entry 引用链的窗口不延长（调用即返回），泄漏面为纯内存。无观测/告警钩子。
- **建议**: 终态 Entry 延迟淘汰：complete() 记录 terminalAt，poller/sweep（或复用平台 CacheConfig 有界缓存）按 TTL 淘汰终态条目（快照语义改为「终态后 T 内可读」并在 javadoc 声明）；或提供 `removeCompletedBefore(long cutoff)` 由引擎终局/定时清理调用。`run`/`attachFuture` 的 future 赋值改为构造后先 `entry.future = future` 再 submit（或 `synchronized(entry)` 内赋值）闭合迟注册窗口。
- **失效面枚举**（F3 同族清扫：本注册表全部生命周期点 + 同族注册表）:
  - `DefaultBackgroundTaskRegistry.tasks`（本条，无删除路径）；
  - `Entry.done` CountDownLatch / `notificationSent` / `originalStatus` —— per-entry 字段，随 Entry 存续，并入本条；
  - `run`/`attachFuture` future 迟注册窗口（本条注记，F8-lite）；
  - `LocalAgentMessenger.replySubscriptions` —— 同族 F4，立案 C1-018；
  - `InMemoryMemoryStoreProvider.stores` —— 同族 F4，立案 C1-016；
  - nop-stream AR-06 ledger 族（审计史实证）—— 跨模块先例，不重复计数。
- **信心水平**: 确定（无删除路径为逐行 grep + close() 全文复核；单条大小按 OUTPUT_TAIL_LIMIT 实测字段）
- **误报排除**: 不属于「有界声明存在的缓存」误报——"bounded" 声明只覆盖单条 tail 字段，不覆盖注册表规模；不属于「终态可回读是有意语义即豁免」——可回读语义与「永不淘汰」之间缺 TTL 声明（对照 DBMessageService CONSUMED 行 CL-008 同形）；按 P2 报因 runInBackground 为常规工具路径、8KB/条目、引擎单例全生命周期保留。
- **复核状态**: 保留(P2)｜新发现

### [C1-018] LocalAgentMessenger.replySubscriptions 按 senderId 惰性注册回复订阅且无任何注销路径（F4/F8，register 无 unregister）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/message/LocalAgentMessenger.java:57,88-89,123-134`（`replySubscriptions` CHM——ensureReplySubscription 只增；全类无 close/unregister API，grep 复核）
- **证据片段**:
  ```java
  // LocalAgentMessenger.java:57,123-134 —— 每个首次出现的 replyTopic（=senderId=会话）挂一个平台订阅
  private final Map<String, IMessageSubscription> replySubscriptions = new ConcurrentHashMap<>();
  private void ensureReplySubscription(String replyTopic) {
      IMessageSubscription existing = replySubscriptions.get(replyTopic);
      if (existing != null) return;
      synchronized (replySubscriptions) {
          if (replySubscriptions.get(replyTopic) == null) {
              IMessageSubscription subscription =
                      messageService.subscribe(replyTopic, new ReplyDemultiplexer());  // 只增不删
              replySubscriptions.put(replyTopic, subscription);
          }
      }
  }
  ```
- **严重程度**: P3
- **并发场景**: messenger 为引擎装配的单例（`IAgentMessenger` 单字段持有）；`request`（call-agent 异步路径、send-message 工具）在任意 worker 线程触发，`replyTopic = AgentMessageTopics.replyTopic(senderId)`——senderId 即调用方 sessionId。DCL 本身正确（CHM 快读 + synchronized 双检）；缺陷纯在生命周期：每个历史会话首次 request 后其订阅永久留存。
- **触发时序**: 长时运行单调增长序列：会话 S1 首次 call-agent 异步调用 → ensureReplySubscription(reply-S1) 注册订阅 + ReplyDemultiplexer consumer → S1 终局 → **订阅与 consumer 无任何移除调用**（pendingRequests 条目经 whenComplete 清理，与订阅无关）→ 下一会话重复；「历史全部发起过 request 的会话」×（1 订阅 + 1 consumer 对象 + 平台消息服务侧 handler 注册）线性增长。
- **现状**: F8「register/unregister 不对称」：`registerHandler` 返回的 `IMessageSubscription` 由调用方管理 cancel，但 messenger **自身**的 reply 订阅是内部注册、无对内取消路径，也无实例级 close()（对照 `DBMessageService.close()`:228-241 的生命周期对称先例）。单条目小（订阅句柄 + 空 consumer），故 P3；但 consumer 挂接平台 `IMessageService`（LocalMessageService）的 handler 注册表，泄漏面延伸到平台侧。
- **风险**: 长时运行服务器按历史会话数线性累积订阅与平台 handler；每条 ReplyDemultiplexer 持有 messenger 外部引用（内部类），间接固定 messenger 与 messageService 引用链。绝对量级 MB 以下，无功能性损害（不匹配的 correlationId 走 debug 分支），属「必然单调增长 + 无观测钩子」的轻量 F4。
- **建议**: 提供 messenger 生命周期对称：`close()`/`dispose()` 遍历 `replySubscriptions.values().forEach(IMessageSubscription::cancel)` 并清空（F8 成对）；或复用平台 `CacheConfig` 有界缓存承载 replyTopic→subscription（淘汰时 cancel 被逐出订阅）；会话终局主动清理需要 sessionId→subscription 索引，成本高于前两者，不推荐。
- **失效面枚举**（F3 同族清扫：messenger/message 面全部注册-注销对）:
  - `LocalAgentMessenger.replySubscriptions`（本条，内部注册无注销）；
  - `LocalAgentMessenger.registerHandler` 返回的外部订阅 —— cancel 语义由调用方持有（引擎 registerCallAgentHandler 的换装缺口已在 C1-013 注记），通过；
  - `LocalAgentMessenger.pendingRequests` —— whenComplete 两参 remove 配对:105，通过；
  - `DBMessageService.consumers` —— put/remove 成对但无身份校验，立案 C1-019；
  - `DeferredAckMailbox` 三结构 —— 单锁内配对迁移，deadLetter 无界另立 CL-010；
  - `AgentSessionSupport.sessionMailboxSubscriptions` —— 批次 C C1-014 已立案，不重复计数。
- **信心水平**: 确定（无注销路径为逐行 grep + 接口复核）
- **误报排除**: 不属于「DCL/CHM 使用即安全」误报——装载原子性与生命周期缺陷正交；不按 P2 报是因单条目小、无功能损害、平台 handler 注册随 messenger 单例有界（每会话一条而非每请求一条），符合 P3「条目小、增长慢」校准（与 C1-014 先例一致）。
- **复核状态**: 保留(P3)｜新发现

### [C1-019] DBMessageService.consumers 同 topic 重复订阅静默替换 + DbSubscription.cancel 无身份校验窃取后注册者（F10/F8）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/message/DBMessageService.java:114,297-313,640-653`（`consumers.put`:304 静默替换；`unregisterConsumer`:310-313 无条件 `consumers.remove(topic)`；`DbSubscription.cancel`:650-653 调用之）
- **证据片段**:
  ```java
  // DBMessageService.java:304,310-313,650-653 —— 订阅注册表「put 覆盖 + remove 不校验身份」
  public IMessageSubscription subscribe(String topic, IMessageConsumer listener, ...) {
      ...
      consumers.put(topic, listener);          // 同 topic 二次订阅静默顶掉前者，无日志
      return new DbSubscription(topic);
  }
  void unregisterConsumer(String topic) {
      consumers.remove(topic);                 // 不校验 cancel 方是否当前注册者
  }
  private class DbSubscription implements IMessageSubscription {
      public void cancel() { cancelled = true; unregisterConsumer(topic); }
  ```
- **严重程度**: P2
- **并发场景**: `DBMessageService` 为 DB 传输形态的单例消息服务；同一 topic 的两次 `subscribe`（两个引擎实例共享一个 DBMessageService、引擎重注册 call-agent handler 未先 cancel 旧订阅、集成方与引擎各自订阅同一业务 topic）从任意线程并发/先后发生。`consumers` 为普通 CHM——put 与 remove 各自原子，但「注册→取消」的生命周期绑定不存在。
- **触发时序**: (a) 替换：T1 `subscribe(T, handlerA)` → T2 `subscribe(T, handlerB)` → consumers[T]=handlerB，handlerA 的 subscription 仍「未取消」（isCancelled()==false）但永不再收到消息——静默失效，无日志；(b) 窃取：接 (a)，T1 持有 subA 调 `subA.cancel()` → `consumers.remove(T)` 把 handlerB 一并摘除 → subB 仍「活跃」但投递停止（pollTopic :332 `consumers.get(topic)==null` 直接跳过）→ 该 topic 消息在表中滞留 PENDING 持续累积，直至有人重新订阅。
- **现状**: (a) 是「检查-然后-行动」缺守卫：put 前无 `putIfAbsent` 语义判定也无被替换方通知；(b) 是 F8 清理不对称的变体：cancel 与注册实例无身份绑定（对照本仓库引擎侧两参 remove 纪律——`DefaultAgentEngine.runningExecutions.remove(sessionId, handle)`:1006）。平台 `LocalMessageService` 同名 topic 语义不在本模块审计面，本条只立 DBMessageService 实现自身。
- **风险**: 消息面静默失效：call-agent topic 的 handler 被顶掉/摘除后，异步 call-agent 全部超时失败（request 无消费者应答），且 DB 表 PENDING 行持续累积（与 CL-008 的 CONSUMED 行无界叠加）；无任何告警区分「未订阅」与「订阅被窃」。
- **建议**: `subscribe` 改 `putIfAbsent`：已存在时按契约抛 `ERR_AGENT_INVALID_STATE`（fail-fast，No Silent No-Op）或返回聚合订阅；`DbSubscription` 持有注册时的 listener 引用，`cancel()` 改 `consumers.remove(topic, registeredListener)` 两参删除（对齐引擎执行槽纪律）；替换场景显式 LOG.warn + 对被替换方 `cancel` 语义声明。
- **失效面枚举**（F3 同族清扫：message 面全部注册表写点）:
  - `DBMessageService.subscribe`/`unregisterConsumer`（本条，put 覆盖 + 无身份 remove）；
  - `LocalAgentMessenger.registerHandler` → 平台 `IMessageService.subscribe` —— 平台设施本体不重审（共享前缀口径），LocalAgentMessenger 自身无注册表；
  - `LocalAgentMessenger.replySubscriptions` —— DCL + 只增（生命周期另案 C1-018），无替换/窃取形态；
  - `DeferredAckMailbox` —— 单监视器内 offer/ack/nack 配对，无注册表语义，通过；
  - `AgentSessionSupport.sessionMailboxSubscriptions` —— 批次 C C1-013 注记面（setMessenger 订阅换装），同族跨包并列不重复计数。
- **信心水平**: 确定（put 覆盖与无身份 remove 为逐行代码事实；触发前提「同 topic 二次订阅」为很可能——多引擎共享 DBMessageService 是 DB 传输形态的自然部署）
- **误报排除**: 不属于「CHM 单操作原子即可」误报——缺陷恰在生命周期绑定缺失而非原子性；不属于「平台 IMessageService 契约如此」——consume 端注册表是本类私有状态，平台契约只约束 subscribe 返回句柄可 cancel；按 P2 报因后果为静默消息面失效且可写出具体时序，未达 P1 因触发需要非默认的多订阅方部署形态。
- **复核状态**: 保留(P2)｜新发现

### [C1-020] DBSessionStore 租户模式绕过会话缓存：同一 sessionId 多 AgentSession 实例并存，injectInput 暂存注入被执行实例的后续保存以 last-writer-wins 覆盖丢失（F1/F2）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/session/DBSessionStore.java:139-141,182-195`（`cacheEnabled()==currentTenant()==null`——租户模式 get/getOrCreate 完全绕过 `sessions` 缓存，每次调用从 DB 新建实例）；受害面 `engine/DefaultAgentEngine.java:605-623`（injectInput 暂存分支：`sessionStore.get` → `addPendingInjection` → `sessionStore.save`）；覆盖机制 `DBSessionStore.java:335-357,380-381`（journal 条目 `state` 块整体携带 `SessionFileWriter.serializeState` 的 `pendingInjections`，replay 逐条 applyTo 后者覆盖前者）
- **证据片段**:
  ```java
  // DBSessionStore.java:139-141,182-194 —— 租户模式下每次 get 都是全新实例
  private boolean cacheEnabled() { return currentTenant() == null; }
  public AgentSession get(String sessionId) {
      if (cacheEnabled()) { AgentSession cached = sessions.get(sessionId); if (cached != null) return cached; }
      AgentSession loaded = loadFromDb(sessionId);   // 租户模式：每次调用 new 一个 AgentSession
      ...
  }
  // SessionFileWriter.serializeState:193 —— journal state 块整体快照 pendingInjections（整表语义）
  state.put(FIELD_PENDING_INJECTIONS, session.getPendingInjections());
  ```
- **严重程度**: P2（租户模式是该 DB store 的旗舰生产形态——plan 232 多租户隔离；后果为用户注入静默丢失 + 双实例状态互相回滚，无 CME/损坏但违背「注入不丢」契约）
- **并发场景**: 租户模式（tenantResolver 返回非 null）下，会话 S 的 ReAct worker 持有执行开始时 `getOrCreate` 返回的实例 A；另一 HTTP/守护线程调用 `injectInput(S,…)` 走暂存分支 `sessionStore.get(S)`——缓存被旁路，返回从 DB 新装载的实例 B。A 与 B 是同一逻辑会话的两个独立可变对象，各自调用 save（per-session 锁只串行化保存动作，不统一对象身份）。
- **触发时序**: T1（injectInput 线程）get 得实例 B → `addPendingInjection` 写入 B.pendingInjections → B.save() tryAppend 追加 journal 行 R1（state.pendingInjections={注入}）→ T2（worker）完成一轮 LLM 调用，A.save() tryAppend（消息指纹匹配，state 来自 A.pendingInjections=空）追加 journal 行 R2（state.pendingInjections=[]）→ 崩溃恢复 replay 按 seq 顺序 applyTo：R2 的整表 state 覆盖 R1 → **暂存注入静默丢失**。反向时序（T2 先存）则注入留存——每次注入是一次掷骰。scalar 列 UPDATE（STATUS/UPDATED_AT）同样 last-writer-wins，但为幂等观测面（无害）。
- **现状**: 类 javadoc :330-332 只声明了**跨进程**写者竞争的退化语义（"cross-process writer winning the (SESSION_ID, SEQ) insert race degrades the save to a fold (last-writer-wins)"），未声明**同进程内**租户模式绕过缓存导致的多实例并存——javadoc :80-85 说明旁路动机（sessionId 键不能区分租户，防跨租户泄漏），动机成立但代价（单进程内对象身份分裂）未被任何文档或同步机制承接。单租户模式（缓存启用）不受影响，与批次 C C1-008 的形态互补不重叠。
- **风险**: 租户生产部署中，「向运行中/暂停会话注入输入」在暂存分支静默丢失（或被回滚），依赖注入语义的 FOLLOWUP 后台任务通知（bridgeBackgroundCompletion）同样暴露于该窗口；恢复后 pending 面与用户预期不一致且无日志可查。
- **建议**: 租户模式恢复实例身份唯一性：按 (tenantId, sessionId) 复合键启用 per-thread-safe 缓存（绕过动机是跨租户隔离，不是不能加租户维度）；或 injectInput 暂存分支改走条件 UPDATE（`pendingInjections` JSON 列 APPEND 语义）而非整实例 save；过渡修复：injectInput 后立即以同实例再做一次 save 前置 `synchronized(sessionLock)` 重读合并（成本高，不推荐）。与 C1-021 的装载锁化修复一并设计。
- **失效面枚举**（F3 同族清扫：全部「store.get 返回的实例被跨线程并发变异」站点）:
  - injectInput 暂存分支 vs 执行 worker（本条，租户模式 live）；
  - `forkSession`:537-560（get(parent) 后 read-only 拷贝，无并发变异，通过）；
  - `cancelSession`:539 `session.setStatus(cancelled)`——经 store.get 所得实例；租户模式下与 worker 的终局 save 竞态：cancelled 状态可能被 worker 实例的下一标量 UPDATE 覆盖回 running（观测面时滞，且 :615-616 注释已声明 status 非 in-flight 信号），注记不立案；
  - `resumeSession/wakeSession/restoreSession` 的 getOrCreate——执行槽 putIfAbsent 保证单执行者，通过（批次 C C1-010 已立案装配窗口面）；
  - `FileBackedSessionStore` —— 缓存恒启用（无租户旁路），无此形态，通过。
- **信心水平**: 确定（缓存旁路为逐行代码事实；覆盖机制为 journal replay 整表 applyTo 的语义事实）
- **误报排除**: 不属于「C1-008 已立案重复计数」——C1-008 立的是共享活引用下的并发变异（ArrayList/CME），本条立的是租户模式对象身份分裂导致的 last-writer-wins 丢失，机制、形态、修复方向均不同；不属于「跨进程 last-writer-wins 已声明」——javadoc 声明的是 SEQ 插入竞争的 fold 退化，非同进程双实例。
- **复核状态**: 保留(P2)｜新发现

### [C1-021] 会话存储「缓存未命中装载」在 per-session 锁外执行：与并发 save 交叠触发误 self-heal 删除已登记 journal delta + persistedStates 陈旧覆盖（FileBacked/DB 双实现同族，C1-012 同族，F10/F1）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/session/FileBackedSessionStore.java:163-174,395-433,447-495`（get 缓存未命中 → `loadPersisted`:395 **不在 sessionLock 内**；heal 路径 :413-423 持锁折叠、journal 删除 :485-493；非 heal 路径 :424-431 **无锁** `persistedStates.put`）；同族 `DBSessionStore.java:581-622,631-677`（loadFromDb/registerPersistedState 同形态，:669-676 无锁 put）；对照正确侧 `FileBackedSessionStore.save`:311-323（全序列在锁内）
- **证据片段**:
  ```java
  // FileBackedSessionStore.java:168-173,430 —— 装载（含 journal 读 + heal 判定）不取 per-session 锁
  public AgentSession get(String sessionId) {
      AgentSession cached = sessions.get(sessionId);
      if (cached != null) return cached;
      AgentSession loaded = loadPersisted(sessionId);   // 读 snapshot + journal + 可能的 heal，全程无锁
      ...
      persistedStates.put(sessionId, state);            // 非 heal 路径：无锁整表覆盖（:430）
  ```
- **严重程度**: P2（可写出具体时序；后果为崩溃窗口丢失已应持久化的消息 delta 与持久化簿记发散——均被自愈机制部分兜底但自愈本身即是伤害路径）
- **并发场景**: 单 store 实例多会话并发；同一会话 S 的「缓存未命中首读装载」（恢复路径/新线程首次 get）与「执行 worker 的 save」（持有 sessionLock 追加 journal 行或折叠）交叠。FileBacked 的 journal 为共享文件，DB 版为共享行集——装载线程与保存线程对同一 journal 无共享锁。
- **触发时序**: (a) 误 heal：T1（save 线程，持锁）tryAppend 正在向 journal 追加第 k 行（`Files.write(APPEND)`:355）→ T2（装载线程，无锁）`readAllLines`:455 恰好读到半行/部分行 → `decodeEntry` 抛 → 判定 torn tail → `healed=true` → T2 `Files.deleteIfExists(journalFile)`:487 **删除含 T1 已完整写入各行的 journal 文件** → T1 继续：state.journalEntryCount 已 ++，后续 save 以「第 k 行已持久化」为前提追加 → 进程在下次折叠前崩溃 → 重放时这些 delta 不存在 → **折叠边界之外的消息丢失被放大**。(b) 簿记覆盖：T2 非 heal 路径读到 k-1 行（T1 尚未写）→ 无锁 `persistedStates.put`（journalEntryCount=k-1）覆盖 T1 刚写的 k → 下一 save 以 seq=k 追加 → journal 出现两条 seq=k → 下次重放 seq 校验失败自愈丢弃尾部，代价同 (a) 的窗口放大 + 一次多余折叠。DB 版同构（registerPersistedState :631-677 非锁 SELECT + put；heal 路径 :658 持锁折叠，与非锁装载交叠同形）。
- **现状**: per-session 锁的 javadoc :113-121 明确声称「per-session locks serializing the whole diff→write→state-update save sequence」——只串行化了 save 侧；装载路径（同为读-改-写 persistedStates 的复合操作）不在锁内，锁的覆盖面与并发实体（save + load 双写者）不匹配。与 C1-012（checkpoint 缓存 ensureSessionLoaded 检查-然后-行动）同族：putIfAbsent/缓存未命中被当作「装载完成」信号，装载与并发写之间无 happens-before。
- **风险**: 生产持久化配置下，恢复/注入路径与执行路径对同一冷会话并发首读时：(a) 每次交叠都可能把「已确认追加的 delta」从磁盘上抹掉，崩溃窗口的丢数据边界从折叠边界扩大到整个 journal；(b) 簿记发散导致的重复 seq 自愈每次浪费一次整表折叠。发生频率随注入/恢复与执行的并发度上升。
- **建议**: 装载路径全程持 `sessionLock(sessionId)`（get/loadPersisted 与 save/remove 同锁——装载含慢 IO，可用「锁内双检缓存」把慢读移出高频路径但保持 heal 折叠与 persistedStates 写入在锁内）；heal 判定前对 journal 做一次重读复核（torn tail 在锁内复核后消失即为他线程并发写，非损坏）；DB 版 registerPersistedState 同锁化。或复用 C1-012 建议的 per-session 装载锁模板（两案统一修）。
- **失效面枚举**（F3 同族清扫：模块内全部「缓存未命中惰性装载 + 持久化簿记」复合站点）:
  - `FileBackedSessionStore.loadPersisted`（本条，含 heal 删除面）；
  - `DBSessionStore.loadFromDb`/`registerPersistedState`（本条同族，DB 行集版）；
  - `DBCheckpointManager.ensureSessionLoaded`/`FileBackedCheckpointManager.ensureSessionLoaded` —— 同族先例已立案 C1-012（无 heal 删除面，后果较轻），修复时统一锁模板；
  - `InMemorySessionStore.getOrCreate` —— computeIfAbsent 原子装载且无持久化簿记，通过；
  - `ResourceComponentManager.loadComponentModel` —— 平台设施装载锁内完成（批次 A 清单 #20），通过；
  - `listAllSessions`（File:244-288 / DB:271-314）—— 启动/恢复期遍历装载，与 save 的交叠同本条，随本条锁化一并消除。
- **信心水平**: 确定（锁覆盖面与 put 位置为逐行代码事实；torn-read 触发窗口为语句级 + IO 交叠，机制为文件/重放语义事实）
- **误报排除**: 不属于「ATOMIC_MOVE 保证读一致性」误报——原子替换保护的是 snapshot 文件，本条立的是 journal 文件的 APPEND 与 readAllLines 交叠及 heal 删除；不属于「自愈机制兜底即无害」——自愈删除正是伤害路径本身（F2 半个成功的变体：装载方把并发写误判为损坏而清理）；与 C1-012 区分：彼为 checkpoint 缓存空读窗口，本条为 session journal 的删除性误 heal，后果更重，单独立案。
- **复核状态**: 保留(P2)｜新发现

### [C1-022] 批次 D 运行期换装面汇总：7 组「plain 非 volatile 字段 + 运行期公开 setter」无可见性保障（C1-013 同族跨批并列）

- **文件**: 载体清单（均为字段 plain 非 volatile + public setter，无 synchronized/volatile）：`team/scheduler/TeamTaskSchedulerDaemon.java:55-57`（daemonCoordinator:55 / daemonOwnerId:56 / scanLeaseMs:57；setter :204-225，scan 线程读 :299/314/392）；`team/scheduler/TaskDispatchCoordinator.java:40,42,49`（taskMemberRouter / memberSpawner / spawnStepExecutor；setter :82-96）；`team/flow/TeamTaskFlowOrchestrator.java:169,187,206,225`（memberSpawner / taskMemberRouter / spawnStepExecutor / memberExecTimeoutMs；setter :357-437，javadoc :165-167 自认 "Mutable via setMemberSpawner... Reads go through the field directly"）；`tool/TeamExecuteFlowExecutor.java:123,145`（memberSpawner，executor 单例跨会话共享）；`tool/CallAgentExecutor.java:116,126-132`（maxDelegationDepth，每 call-agent 调用读 :178）；`message/DBMessageService.java:120-123`（pollIntervalMs/maxBatch/staleClaimTimeoutMs/sweepIntervalMs；setter :154-189，poller 线程读）；`memory/InMemoryVectorAdapter.java:48-52`/`InMemoryStorageAdapter.java:51`（tenantResolver，per-session 实例）
- **证据片段**:
  ```java
  // TeamTaskFlowOrchestrator.java:169,357-359（代表形态——其余 6 组同构）
  private IMemberSpawner memberSpawner = NoOpMemberSpawner.noOp();   // plain 非 volatile
  public void setMemberSpawner(IMemberSpawner memberSpawner) {
      this.memberSpawner = memberSpawner != null ? memberSpawner : NoOpMemberSpawner.noOp();
  }
  ```
- **严重程度**: P3（与批次 A C1-004、批次 C C1-013 同判：机制缺陷确定，当前库内唯一实际用法为装配期单线程注入——executor 提交链的 happens-before 覆盖 start 前写入；实际损害依赖集成方运行期重配行为）
- **并发场景**: 配置/运维线程调用任一 setter（全部为 public API，javadoc 无「仅启动期」限定——orchestrator 侧甚至明文声明运行期可重配），与请求/scan/poller 线程的裸字段读并发。无 volatile、无锁、无 HB 边。
- **触发时序**: T1（worker 线程）执行 call-agent 读 `maxDelegationDepth`（:178，plain 读）→ T2（配置线程）`setMaxDelegationDepth(2)` plain 写 → T1 因无 HB 可能持续读到旧值（重配不生效或迟滞生效）；若新值为新构造对象（spawner/router/executor），unsafe publication 下 T1 可见引用而其内部字段初始化不可见。daemon 侧 `setDaemonOwnerId`/`setScanLeaseMs` 在运行期换装还会使 scan 线程以混合身份 acquire/release 扫描租约（releaseScanLease 失败走 LOG.warn 路径 :392-397——有观测但租约语义混乱）。
- **现状**: 与 C1-013 完全同构的家族蔓延：批次 D 范围内 grep 「public void set*」与对应字段复核得 7 组载体（见文件栏）。两处 `resolveSpawnExecutor` 惰性自建池（TeamTaskFlowOrchestrator:468-478、TaskDispatchCoordinator:113-123）为无锁检查-然后-行动——前者因 per-invocation 新建（TeamExecuteFlowExecutor:234 DD#3）、后者因单 scan 线程（scheduleWithFixedDelay 串行）当前实际单线程化，注记通过；但 daemon.stop()（synchronized）→ shutdownOwnSpawnExecutor 与在途 scan 的 supplyAsync 存在 RejectedExecutionException 窗口（后果=honest failure 任务留 CLAIMED 可被 reclaim，有界），随本条注记。
- **风险**: 集成方一旦按 API 形态做运行期重配（热切换 spawner/router、调深限、调轮询参数），获得：重配不生效/迟滞生效、unsafe publication、租约身份混乱、shutdown 拒绝窗口。无数据损坏。
- **建议**: 统一最小修复（成本近零）：7 组字段全部加 `volatile`（写-读建立 HB + 安全发布）；javadoc 显式声明换装生效时点；惰性 spawn 池维持现状（单线程掩护成立）但在 javadoc 声明「stop 与 dispatch 的拒绝窗口」语义。
- **失效面枚举**（F3 同族清扫：批次 D 全部「运行期 setter + 共享读」载体，已逐一列于文件栏）:
  - TeamTaskSchedulerDaemon 3 字段 / TaskDispatchCoordinator 3 字段 / TeamTaskFlowOrchestrator 4 字段（本条）；
  - TeamExecuteFlowExecutor.memberSpawner / CallAgentExecutor.maxDelegationDepth（本条，tool executor 单例）；
  - DBMessageService 4 个 config 字段 / InMemoryVectorAdapter·InMemoryStorageAdapter.tenantResolver（本条）；
  - `scheduleHandle`（volatile）与 start/stop（synchronized）—— 生命周期面正确，通过；
  - 同族跨批：批次 A C1-004（EmbeddingServiceImpl.retryScheduler）、批次 C C1-013（DefaultAgentEngine.agentExecutor / SessionLockRenewal.lockRenewExecutor / setBackgroundProgressTracker / AgentCallDelegate.messenger）—— 修复时应一次清扫全家族。
- **信心水平**: 确定（字段-写者-读者清单为逐文件扫描事实）；触发前提（运行期重配）为很可能
- **误报排除**: 不属于「装配期写 + bean 发布链」降档可豁免——orchestrator/daemon 的 setter 在 javadoc 层面就是运行期扩展点（明文 "Mutable via"）；不属于「低频配置路径粗锁优雅性」——问题是无任何同步而非锁粒度；按 P3 与 C1-004/C1-013 保持跨批一致。
- **复核状态**: 保留(P3)｜新发现

### [C1-023] InMemoryTeamManager 配额检查-然后-行动非原子：并发 addMember/bindMemberSession 突破 TEAM_MEMBERS/maxParallelMembers 强制上限（F10）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/InMemoryTeamManager.java:217-221`（addMember：`enforceQuota(... members.size() + 1 ...)` 与 `members.putIfAbsent` 两步无原子性）、`:273-304`（bindMemberSession：boundCount 快照循环 :275-280 → `enforceQuota`:282-284 → `members.compute`:289-304 三步无原子性）；配额网关语义 `quota/DefaultResourceGuard.checkConcurrent`（enforced fail-fast，见 :103-116 enforceQuota javadoc "No Silent No-Op"）；并发触达面 `engine/AgentTeamBinder.java:90-133`（autoBindMember——每个成员会话的执行 setup 并发调用）
- **证据片段**:
  ```java
  // InMemoryTeamManager.java:275-289 —— 快照计数 → 判定 → 绑定，三步之间无互斥
  int boundCount = 0;
  for (TeamMember m : members.values()) { if (m.isBound()) boundCount++; }
  int projectedCount = target.isBound() ? boundCount : boundCount + 1;
  enforceQuota(QuotaDimension.TEAM_PARALLEL_BOUND_MEMBERS, teamId,
          projectedCount, team.getSpec().getMaxParallelMembers(), "bindMemberSession");
  ...
  members.compute(memberName, (name, member) -> { ... member.bind(sessionId, actorId); ... });
  ```
- **严重程度**: P2（可写出具体时序；后果为 plan 234 明文「hint→enforced 升级」的并行绑定上限被并发绑定突破——资源控制语义违约，无数据损坏）
- **并发场景**: 单 manager 实例跨全部团队共享；同一团队的两个/多个成员会话并发执行（不同 sessionId 的 doExecute 互不串行——执行槽按会话隔离），各自在 assembleExecutionSetup 经 `AgentTeamBinder.autoBindMember`:126 并发调用 bindMemberSession。addMember 侧同形态（当前库内 main 无调用方，休眠）。
- **触发时序**: maxParallelMembers=2、当前已绑定 1：T1（成员 X 的 worker）:275-280 读 boundCount=1 → projected=2 → 配额通过 → T2（成员 Y 的 worker）同点同读 boundCount=1（X 的 compute 未发生/不可见）→ projected=2 → 也通过 → 双方各自 compute 绑定 → 实际并行绑定数=3 > 上限 2。K 个并发绑定最多超限 K-1。addMember 同理（size()+1 快照）。
- **现状**: 典型 F10 检查-然后-行动落在资源控制面上。批次 C C1-015 失效面曾对 `InMemoryActorRuntime.createActor` 同形态「注记不立案」，理由是彼处配额为软限制且引擎执行槽按会话串行掩护；此处两项理由均不成立——plan 234 把该维度定义为**强制**（enforced，deny 即抛）、且触发路径恰是「不同会话并发」这一执行槽不覆盖的正交维度，故立案而非注记。附带注记：`addMember`:209 的 DISBANDED 预检与 disbandTeam:173-184 的 compute 之间存在 TOCTOU（向已解散团队加成员，后果有界）；`AgentTeamBinder.autoBindLead`:73-80 的 getTeamBySession 探测-创建两步在执行槽串行化下无同会话并发，通过。
- **风险**: 并行成员 fan-out（多成员团队常态）下 maxParallelMembers 失去强制意义：每团队超限量 ≈ 并发绑定数-1；依赖该上限做 LLM 并发/成本控制的部署获得系统性低估。无崩溃/损坏。
- **建议**: 把配额判定移入 `members.compute` 临界区内（compute 内重算 boundCount + checkConcurrent + bind，拒绝时返回原成员不改绑定并抛/返回失败——CHM per-key 临界区恰好覆盖同队全部成员的绑定互斥）；addMember 侧以「预占位 compute + 配额内确认」或 per-team 锁（`ConcurrentHashMap<teamId, Object>`）串行化判定-写入。DbTeamManager 的同名方法以 DB 计数为准（跨进程语义另行评估，归 quota 维度复核）。
- **失效面枚举**（F3 同族清扫：模块内全部「共享集合快照计数 → checkConcurrent → 写入」站点）:
  - `InMemoryTeamManager.addMember`:217-221（本条同案，休眠调用方）；
  - `InMemoryTeamManager.bindMemberSession`:273-304（本条主面，live 经 autoBindMember）；
  - `DbTeamManager.addMember`/`bindMemberSession`（DB 版，:439/:533 起）—— 唯一约束兜底重复成员，但并行绑定计数同样存在跨连接检查-然后-行动，随本条修复评估（DB 侧需原子条件 UPDATE 或 SELECT FOR UPDATE，归 quota/C6 复核）；
  - `InMemoryActorRuntime.createActor:279-282` —— 批次 C C1-015 失效面注记先例（软限制 + 执行槽掩护），维持注记不立案，修复模板可共享；
  - `AgentTeamBinder.autoBindMember:121-124` 幂等预检 → bind 的 TOCTOU —— 同名成员双会话并发绑定时 last-writer-wins 重绑（配置错误场景，后果为前会话绑定被顶掉且 sessionToTeamId 索引被清），注记随本条修复（compute 内重检已绑定即拒绝）一并消除。
- **信心水平**: 确定（三步非原子为代码事实；并发触达面经 AgentTeamBinder 调用链确认）
- **误报排除**: 不属于「CHM/compute 本身使用」误报——compute 只使绑定原子，不使「计数→判定→绑定」复合原子；不属于「软限制注记」先例——plan 234 明文 enforced + No Silent No-Op；不按 P1 报因后果为限额突破（有界、无损坏），符合 P2「可证明的检查-然后-行动缺口」校准。
- **复核状态**: 保留(P2)｜新发现

### [C1-024] InMemoryTeamTaskStore.tasks/teamIndex 无删除 API：终态任务跨团队生命周期永久累积（F4 无界账本，opt-in 功能件）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/InMemoryTeamTaskStore.java:59-60`（`tasks`/`teamIndex` CHM×2——全类无 remove，grep 复核）；接口面 `team/ITeamTaskStore.java:67-213`（契约只定义 create/get/查询/四状态转换，无 delete/清理方法）；对照有删除路径的兄弟契约 `reliability/ICheckpointManager.remove`（批次 C C1-011 已立案其缺省陷阱）
- **证据片段**:
  ```java
  // InMemoryTeamTaskStore.java:59-60 —— 终态(COMPLETED/ABANDONED)任务与 teamIndex 条目只增不删
  private final ConcurrentHashMap<String, TeamTask> tasks = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, List<String>> teamIndex = new ConcurrentHashMap<>();
  // createTask:85-89 —— teamIndex.compute 追加式重建不可变快照列表，永不收缩
  ```
- **严重程度**: P3（opt-in 功能件——引擎默认 `NoOpTeamTaskStore`（NoOpTeamTaskStore.java），仅 `engine.config().setTeamTaskStore` 装配后生效；单条目为不可变小对象；但增长为「历史全部团队 × 全部任务」必然单调且无观测钩子）
- **并发场景**: 写者为多会话并发 worker + 调度守护线程（CHM compute CAS 保证原子性——无可见性缺陷，纯生命周期问题）；任务的终态转换（complete/abandon）不释放任何结构条目，teamIndex 的不可变快照列表随创建只增。
- **触发时序**: 长时运行单调增长序列：团队 T 创建 → 工具/守护线程持续 createTask → 任务逐个到达终态 → **终态不删除**（无 API 也无调用方）→ 团队解散（disbandTeam 仅改状态、按 javadoc 有意保留团队供审计——但该声明不覆盖任务账本）→ tasks 中 T 的全部任务与 teamIndex 条目留存至进程退出。
- **现状**: 与 C1-016/C1-014 同族 F4；`DBTeamTaskStore` 的表行同形态（DB 外置面，归 C6 评估 TTL，随 C1-011/C1-006 先例登记不重复计数）。javadoc（ITeamTaskStore :31-41）详细声明了状态机但未声明增长边界或「终态保留为审计语义」。
- **风险**: 长时运行 + 活跃团队编排部署下按任务历史数线性累积（TeamTask 含 subject/description/blockedBy 文本，单条百字节- KB 级）；reclaim 语义依赖 CLAIMED→CREATED 回转，不影响终态堆积。无短期 OOM，无告警。
- **建议**: 为 ITeamTaskStore 增加终态清理契约：`purgeTerminalBefore(long cutoff, Set<TeamTaskStatus> terminalStates)`（in-memory 实现同步维护 teamIndex 重建；DB 实现归 C6 TTL）；或换平台 `CacheConfig` 有界缓存承载 tasks（teamIndex 改为派生查询）；引擎/守护线程在团队 disband 后调用成对清理（F8 对称）。
- **失效面枚举**（F3 同族清扫：team 包全部任务/团队账本）:
  - `InMemoryTeamTaskStore.tasks`/`teamIndex`（本条，无删除 API）；
  - `InMemoryTeamManager.teams` —— disband 后保留为 javadoc :46-49 已声明审计语义，通过（已声明边界）；
  - `InMemoryTeamManager.sessionToTeamId` —— removeMember:246-248 与 bind 重绑 :296-299 两处清理配对，通过；
  - `DbTeamTaskStore` → `ai_agent_team_task` 表 —— DB 外置同族，归 C6（同 CL-008 口径）；
  - `DefaultBackgroundTaskRegistry.tasks`/`InMemoryMemoryStoreProvider.stores`/`LocalAgentMessenger.replySubscriptions` —— 同族 F4 分别立案 C1-017/C1-016/C1-018。
- **信心水平**: 确定（无删除路径为逐文件 grep + 接口全量复核）
- **误报排除**: 不属于「有界声明存在的缓存」误报——无任何容量声明；不属于 C1-016 重复计数——彼为引擎默认装配的记忆注册表，本条为 opt-in 任务账本（P3 校准与 C1-014「opt-in + 条目小」先例一致）。
- **复核状态**: 保留(P3)｜新发现

### [C1-025] InMemoryContributionRegistry.unregisterSource 与 register 交叠：已完成注册被过期键快照回删（复合操作无全局全序，F10）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/contribution/InMemoryContributionRegistry.java:112-137`（`unregisterSource`——`:116` `bySource.remove(source)` 在全部内层锁之外先行取得键集所有权，`:125-136` 仅持各 type 内层锁回删）；对照 `:81-109`（`register`——synchronized(inner) 内联维护 bySource）
- **证据片段**:
  ```java
  // unregisterSource:116 —— 无锁摘除整个 source 的键集（此后新注册进入新建集合，本快照不再感知）
  Set<TypeIdKey> keys = bySource.remove(source);
  ...
  for (TypeIdKey key : keys) {
      Map<String, Contribution> inner = byType.get(key.type);
      synchronized (inner) {
          Contribution c = inner.get(key.id);
          if (c != null && source.equals(c.getSource())) {
              inner.remove(key.id);   // :133 —— 会命中竞态窗口内他人刚完成注册的同一 (type,id)
          }
      }
  }
  ```
- **严重程度**: P2（实现为 opt-in——引擎默认 `NoOpContributionRegistry`（DefaultAgentEngineConfig:146），需 integrator 显式 `setContributionRegistry`；触发需同一 (type,id) 的 register 与 unregisterSource 运行期交叠（插件/贡献源热装卸场景）；后果为可重注册自愈的静默缺失而非数据损坏）
- **并发场景**: 贡献注册/拆卸线程（integrator 装配或运行期插件装卸）× 每请求读线程（`AgentPromptAssembly:217` 读 PROMPT、`AgentExecutorResolver:225` 每执行读 HOOK——读路径有锁内快照保护，无暴露）。竞态只发生在两个**写**复合操作之间：两操作各自原子，但彼此之间无全局全序（register 的键登记在内层锁内、unregisterSource 的键摘除在一切锁外）。
- **触发时序**: T1 `unregisterSource("s")` 执行到 :116（keys={K(type,id)} 已从 bySource 摘除，旧集合快照在手）→ T2 `register(K, source="s")` 完整执行 :87-106（`inner.put(K, c2)`、向 unregisterSource 摘除后新建的 bySource 集合添加 K，返回 true）→ T1 继续执行 :130 synchronized(inner)、:131 `inner.get(K)`=c2、:132 source 相等 → :133 `inner.remove(K)`。交叠后果：T2 的 register 返回 true 但贡献已被删除——此后每请求 hook/prompt 装配静默缺失该贡献，无任何日志或错误（register 的成功返回值与实际状态背离，语义违约）。
- **现状**: `unregisterSource` 的「先摘键、后回删」顺序使键集快照天然过期；javadoc :102-104 声称 "bySource inner set is mutated only here and in unregisterSource (which also acquires the matching per-type locks)"——与实现不符（unregisterSource 摘除 bySource 条目时**不持任何** per-type 锁，同族文档性偏差，与 CL-009/批次 A 清单 #12/CL-006 第四例）。
- **风险**: 动态贡献源（hook/prompt 贡献的热更新、插件卸载重装）场景下，卸载与重装交叠即丢失注册；因无错误信号，表现为「该 hook 突然不再生效」的静默行为漂移，排查面极大。
- **建议**: 最小修法——`:131-134` 删除条件追加「且该键仍属于当前 bySource 中该 source 的现存集合」（remove-后重验，与批次 D D3 CAS 重检同构）；或结构修法——unregisterSource 改为「按键 type 分组 → 逐内层锁内校验 source 并 remove → **最后** `bySource.remove(source)`」，使键摘除成为全序终点；同时修正 javadoc :102-104 的锁纪律描述。
- **失效面枚举**（F3 同族清扫：该注册表全部复合操作对）:
  - register × unregisterSource（本条，竞态立案）；
  - register × register 同 (type,id) 跨源碰撞——synchronized(inner) 内 fail-loud :91-97，通过；
  - register × register 同源重注册（幂等替换 :98-100），通过；
  - unregisterSource × unregisterSource 同 source 并发——第二个调用 :117 keys==null 早退，通过；
  - getContributions × unregisterSource/register——读快照在 synchronized(inner) 内 :149-151/:166-173，通过；
  - getSources——CHM keySet 快照拷贝 :179-186，通过。
- **信心水平**: 确定（交错序列逐行可构造，无推测环节）
- **误报排除**: 不属于「CHM/computeIfAbsent 本身使用」误报——缺陷不在原语，在两个复合操作之间缺乏全局全序；不属于「低频路径粗锁豁免」——本站点问题恰是锁覆盖缺口而非锁过粗；不按 P1 报——opt-in 组件 + 管理面窄窗口 + 可自愈后果，对齐 C1-023 的 P2 校准（后果有界）。
- **复核状态**: 保留(P2)｜新发现

### [C1-026] InMemoryWriteIntentRegistry.intentsByPath 路径键只增不删 + releaseSession 每会话终局 O(全部历史路径) 全桶扫描（F4 无界账本 lite + 长运行退化）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/conflict/InMemoryWriteIntentRegistry.java:40,52-53`（键建立：`computeIfAbsent(path, k -> new ArrayList<>())`，全类无任何 `intentsByPath.remove`，grep 复核）；`:74-87`（releaseSession 遍历 `intentsByPath.values()` 全桶）；javadoc `:29-33` 自认 "Released lists are not deleted from the map (a path key may become empty but is cheap to retain)"
- **证据片段**:
  ```java
  // :53 —— 每个曾被任何会话写过的规范化路径永久建桶
  List<WriteIntent> bucket = intentsByPath.computeIfAbsent(path, k -> new ArrayList<>());
  // :82 —— 每会话终局 release 扫描全部历史桶（含全部空桶），成本随进程历史路径总数线性增长
  for (List<WriteIntent> bucket : intentsByPath.values()) {
      synchronized (bucket) {
          bucket.removeIf(i -> sessionId.equals(i.getSessionId()));
      }
  }
  ```
- **严重程度**: P3（键条目为空 ArrayList + 路径字符串，单条约百字节级；无短期 OOM；但增长面=「进程历史全部被写文件路径」，多租户长运行必然单调且无观测钩子；releaseSession 的 O(N) 终局扫描随历史劣化——引擎默认装配（DefaultAgentEngineConfig:145），非 opt-in，故高于 C1-024 一档但仍为 P3 因条目极小）
- **并发场景**: 注册写者=各会话 ReAct 线程（每写工具派发一次，AgentSecurityConsultation:585，引擎共享单例跨全部会话）；删除写者=各会话终局 finally 线程（4 处：AgentSessionLifecycle:393/559/958、DefaultAgentEngine:1019）。bucket 内条目的增删有 per-bucket monitor 配对（原子性通过，javadoc TOCTOU 声明与实现一致）；缺陷纯在**键生命周期**。
- **触发时序**: 长时运行单调增长序列——会话 S1 写路径 P → P 进入 intentsByPath 永久留存（session 释放后空桶亦留存，javadoc 有意为之）→ S2…Sn 各写不同路径，键集按「历史全部 distinct 路径」累积 → 每个会话终局 releaseSession 均遍历**全部**历史桶（含其他会话的活桶与全部死桶）→ 随活跃会话数 × 历史路径数乘积增长，终局路径的锁获取次数与遍历成本线性上升，无淘汰、无告警。
- **现状**: javadoc :30-33 的保留声明仅覆盖「per-path 监视器稳定性」动机（"keeps the per-path monitor stable…avoids race conditions"），未声明键集无界增长边界与 releaseSession 全量扫描成本——保留动机成立，但增长后果未被声明豁免（对照 C1-024 中 `InMemoryTeamManager.teams` 的「已声明审计语义」豁免口径，本条无等价声明）。
- **风险**: 长运行部署下每会话终局成本随进程历史路径总数线性增长（终局路径位于执行 finally——拖长执行槽释放）；键集内存缓慢累积；均为渐变退化、无突增故障点。
- **建议**: (a) 空桶回收——removeIf 后在 `synchronized(bucket)` 内确认空即 `intentsByPath.remove(path, bucket)` 两参删除（与 computeIfAbsent 竞态需 remove-后重验桶内仍空——两参 remove 保证只删同一实例，新注册者拿到的必是新桶，监视器稳定性语义可保）；(b) releaseSession 增 `sessionId → paths` 反向索引（注册时 O(1) 记账），使 release 为 O(该会话路径数)；(c) javadoc 补充增长边界声明。
- **失效面枚举**（F3 同族清扫：conflict 包全部状态 + 引擎内同形态注册表）:
  - `intentsByPath` 键集（本条，无删除 API）；
  - bucket 内 `WriteIntent` 条目——releaseSession removeIf 配对清理 :84（通过；条目随会话终局删除）；
  - `WriteIntent`/`ConflictDecision`/`ConflictResult` 值对象——全 final 不可变（WriteIntent.java:22-25），通过；
  - 同族 F4 注册表：`DefaultAgentEngineConfig:145` 本条为唯一引擎默认装配的 conflict 面；兄弟 F4 已立案 C1-014/C1-016/C1-017/C1-024/C1-027。
- **信心水平**: 确定（无键删除路径为逐文件 grep + 接口全量复核）
- **误报排除**: 不属于「有界声明存在的缓存」——无容量声明；不属于「已声明边界」——javadoc 声明仅覆盖监视器稳定性动机，不覆盖无界增长与 release 扫描成本；不属于 C1-024 重复计数——彼为 opt-in 任务账本，本条为引擎默认、请求触达的写意图注册表（P3 校准依据为条目极小 + 渐变退化，非 opt-in 豁免）。
- **复核状态**: 保留(P3)｜新发现

### [C1-027] DefaultFencingTokenService.issueCounters/recordedHighWater 双计数器注册表无删除/复位 API（F4 无界账本 lite，休眠面）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/fencing/DefaultFencingTokenService.java:58-59,63,70`（`issueCounters`/`recordedHighWater` CHM<String,AtomicLong>×2——`computeIfAbsent` 建立 per-actor 条目，全类无 remove/reset/clear，grep 复核）
- **证据片段**:
  ```java
  // :58-59 —— 每 actor 双条目（发行计数 + 已验收高水位），只增不删
  private final ConcurrentHashMap<String, AtomicLong> issueCounters = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, AtomicLong> recordedHighWater = new ConcurrentHashMap<>();
  // :63/:70 —— 首 issue/validate 建 per-actor 条目；javadoc :30-32 自认 in-memory 计数「进程重启即失」，
  // 但未声明运行期生命周期管理（actor 销毁不复位、无 per-actor 清除钩子）
  ```
- **严重程度**: P3（休眠面——引擎零接线：全库 grep `FencingToken` 仅 `IMemberSpawner:29`/`ITaskMemberRunner` javadoc 提及，无装配点、无生产调用方，DB-backed 后继自认 Non-Goal（javadoc :47-51）；条目极小（引用 + AtomicLong）；但语义上「高水位永不失效」+ 成员会话 churn 场景 per-actor 单调累积）
- **并发场景**: CAS 原子性正确（issue 计数 incrementAndGet 无重复、validate only-if-greater CAS 循环无丢失更新——javadoc :15-25 与实现逐行一致，不立案）；缺陷纯在 per-actor 条目生命周期：actor 销毁（成员会话终局）不复位其计数器条目，recovery 场景 javadoc :30-31 预留的「复位为 DB-max+1」语义无任何实现挂点。
- **触发时序**: 长时运行序列（前提：未来接线启用）——成员 actor A 会话创建 → issue/validate 建双条目 → A 会话终局销毁（无任何钩子触及两 map）→ 新成员 churn 循环 → 条目按「进程历史全部 actorId」累积至进程退出；同 actorId 复用则计数跨会话延续——高水位跨会话残留反而可能误杀新会话的合法低号 token（stale 判定，:85-88），语义缺陷重于内存增长。
- **现状**: 原子性设计（javadoc §State 两计数器独立 + CAS）精确且正确；生命周期维度（谁删、何时复位、actor 与条目的绑定）零声明零实现——与 C1-014/C1-016 同族「只建不删」形态，但多一层「跨生命周期计数残留」的语义风险。
- **风险**: 接线激活后：(a) 多租户/成员频繁 churn 的长运行部署条目无界累积（量级小）；(b) actorId 复用时旧高水位使新会话 token 被判 stale——fencing 保护面误伤（正确性风险 > 内存风险）。
- **建议**: 接线时配套 per-actor 生命周期钩子——actor destroy/unregister 处 `issueCounters.remove(actorId)` + `recordedHighWater.remove(actorId)`（或按会话 epoch 组合键使跨会话天然隔离）；recovery 复位语义落地为显式 API（javadoc :30-31 已预留）；可换平台 `CacheConfig` 有界缓存承载。
- **失效面枚举**（F3 同族清扫：fencing 包全部状态 + 治理原语注册表族）:
  - `issueCounters`/`recordedHighWater`（本条，双 map 均无删除 API）；
  - `FencingToken`/`FencingTokenDecision` 值对象——全 final 不可变（FencingToken.java:29-31），通过；
  - `NoOpFencingTokenService`——无状态单例，通过；
  - 同族 F4：C1-014/C1-016/C1-017/C1-024/C1-026（各自立案）；DB 外置形态（DbSessionTakeoverLock/DbDaemonCoordinator 行生命周期）属批次 C 清单 C17/C6 口径。
- **信心水平**: 确定（无删除路径为全类事实；触发面为休眠推定，接线前提已显式标注）
- **误报排除**: 不属于「平台基础设施自身不重审」——本类为本模块手写治理原语（plan 235 交付物）；不属于「原语使用误报」——CAS 使用本身判定通过，立案对象是生命周期缺口；不按 P1/P2 报——全库零生产调用方（休眠面）+ 条目极小，对齐 C1-024「opt-in 功能件」P3 校准（本条连 opt-in 装配点都不存在，更轻）。
- **复核状态**: 保留(P3)｜新发现

---

## 维度集外线索（overlay C2/C6/C8/C9）

### [nop-ai-core-nop-ai-agent-CL-001] callStream 内部 subscriber 的 onNext 抛异常 → 外层 SubmissionPublisher 永不闭合 → callAsync future 永久挂起

- **位置**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/ChatServiceImpl.java:205-231`（内部 subscriber，`onNext` 无 try/catch、无 `publisher.closeExceptionally` 兜底）、`:216`（`dialect.parseStreamChunk(item.getData())` 抛点）；抛出源实证 `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/dialect/OpenAiDialect.java:254`（`JSON.parse(data)` 无容错，畸形 SSE payload / `deltaMap.get("content")` 非 String 的 ClassCastException 同路径）；受害端 `ChatServiceImpl.java:391-440`（`aggregateStreamToResponse` 的 `CompletableFuture<ChatResponse> future` 永不 complete）与 `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/command/AiCommand.java:273-275`（`execute` → `FutureHelper.syncGet` 无限阻塞）。
- **形态**: Flow 规范下 subscriber 的 `onNext` 抛异常即取消该订阅且异常被 `SubmissionPublisher` 的分发循环吞掉（不转发 onError）。此时外层 `publisher`（:202）既未 `close` 也未 `closeExceptionally`，其下游 subscriber 收不到任何终结信号。
- **为何超出本维度**: 状态本身无共享/可见性问题（StreamAggregator per-request 判定为通过，见清单 #15）；缺陷是异步链的**错误传播断裂与无限期挂起**，属 C3（async chain）/C9（吞异常→状态不一致）/C5（挂起面）交叉。
- **建议归维**: C3 为主审（与 C9 联合裁定「onNext 容错 + closeExceptionally 兜底」契约）。注意修复时勿把异常吞成 null chunk——需区分「单事件畸形可跳过」与「链路已断必须终结」。
- **佐证强度**: 高——`parseStreamChunk` 的 `JSON.parse` 抛出路径逐行确认，无任何 catch；`onNext`/`onError`/`onComplete` 三分支中仅后两者闭合 publisher。

### [nop-ai-core-nop-ai-agent-CL-002] 响应缓存文件 save 非原子 + 并发同 hash 请求写读竞态 → 撕裂缓存文件（F2/F6 同构）

- **位置**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/persist/IChatExchangePersister.java:9-15`（默认 `save` = `ResourceHelper.writeText(resource, serialize(exchange))` 直写，无 temp+rename 原子替换，已复核 `ResourceHelper.writeText`:714-723 无锁无原子化）；竞态面 `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/persist/DefaultAiChatResponseCache.java:47-73`（`loadCachedResponse` 读/删 与 `saveCachedResponse` 写并发）；触发入口 `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/command/AiCommand.java:291-315`（cache miss → LLM → 保存；同 prompt 同消息体两并发请求命中同一 hash 路径）。
- **形态**: 共享可变状态以**缓存文件**形态存在：T1/T2 双请求同 hash 并发 save 交错写同一文件 → 落盘撕裂；T3 并发 load 读到半截 JSON → `deserialize` 抛异常 → 被 `AiCommand`:298-300 catch 降级为 cache miss（可见性有界：不产生错误数据，但该 hash 的缓存持续失效直至下一次干净写覆盖）。另 `loadCachedResponse` 的 TTL 过期 `resource.delete()`（:59）与并发 `load` 存在删除-读取 TOCTOU 窗口（后果同为 miss，无害）。
- **为何超出本维度**: 文件非内存共享状态，撕裂源于落盘无原子屏障与多步写读缺少协调，属 C6（resource/persist，F6 落盘屏障族）与 F2（半个成功——写一半进程退出留下永久撕裂文件）交叉；本维度仅登记其内存侧判定（`DefaultAiChatResponseCache` 实例字段经 IoC 装配期写，通过）。
- **建议归维**: C6 主审。修复方向：`save` 改 temp-file + 原子 `Files.move(ATOMIC_MOVE)`（平台 `LocalFileLock`/原子替换惯例），或 per-hash 文件级互斥。
- **佐证强度**: 中高——写入路径无原子化已逐行确认；并发同 hash 请求在实际负载（多 worker 重复执行同一 task step）中可自然发生。

### [nop-ai-core-nop-ai-agent-CL-003] PlanExecutor 异常/终止路径遗留 running 中间态且无 finally 复位（状态机一致性，F9/F2 同构）

- **位置**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/plan/runtime/PlanExecutor.java:112-186`（`execute` 无 try/finally 包裹主循环）；状态写点：`:118`（`setPlanStatus(running)`）、`:208`（每任务 `setTaskStatus(running)`）、`:282-285`（BLOCKED 终止分支）；抛出点：`:151/:168`（恢复循环上限）、`:196-198`（drivePhaseTasks 安全上限）、`:258`（gate 检查上限）、`PlanReplanner.apply` ABORT `UnsupportedOperationException`:181-182；同构问题：`respondToStagnation` 返回 terminal（:307-309）时 drivePhaseTasks 中已置 running 的任务被原地冻结在 running 状态，无失败/取消终结化。
- **形态**: T1 执行到 :196 抛安全上限异常（或 replanner apply 抛 ABORT）→ `execute` 栈展开，`PlanExecutionState.planStatus` 停留在 running、若干任务停留在 running → 状态对象随栈丢弃故无内存泄漏（C1 维度判定通过），但**可观测结果层面**：宿主（未来生产接线方）拿到的要么是异常无状态快照、要么是含 running 任务的结果对象——"仍在执行"与"已废弃"不可区分；BLOCKED 终止分支返回的 `PlanExecutionResult` 同样携带 running 任务计数。
- **为何超出本维度**: 状态对象本身 per-execute 栈封闭（见清单 #B14），无共享/可见性问题；缺陷是执行链异常路径的**状态机终结化缺失与半途结果外泄**，属 C9（吞异常/异常路径状态不一致）/C2（状态机）交叉，与 F2"半个成功"同构。
- **建议归维**: C9 主审。修复方向：`execute` 以 try/finally 兜底置 `planStatus=failed/aborted`；terminal 返回前对非终态任务统一置 `cancelled`；`PlanExecutionResult` 增加 `incompleteTasks` 计数字段使半途可见。
- **佐证强度**: 高——所有抛出点与状态写点逐行确认无终结化对冲；当前 plan/runtime 全库 main 零调用方（休眠面），激活前提与批次 C1-005 类似为未来生产接线。

### [nop-ai-core-nop-ai-agent-CL-004] PlanScheduler 对重复 taskNo 静默合并 (a,b)->a，与 PlanDagBuilder"重复即抛"契约分叉（静默吞冲突，F9 同构）

- **位置**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/plan/runtime/PlanScheduler.java:124-125`（`Collectors.toMap(AgentPlanTaskModel::getTaskNo, Function.identity(), (a, b) -> a)` 静默取首个）；对照契约 `PlanDagBuilder.java:33-43`（javadoc：Duplicate taskNo → throw）与 `PlanExecutionState.registerRuntimeTask:336-338`（runtime/frozen 撞号显式抛 `ERR_AGENT_INVALID_STATE`）。
- **形态**: 同一 plan 的任务收集在三处采用三种冲突语义：DagBuilder 抛（校验路径）、registerRuntimeTask 抛（overlay 写路径）、Scheduler 静默丢弃后者（调度读路径）。若任何上游绕过 DagBuilder 校验（如直接以代码构造 AgentPlan 调 `PlanScheduler.getReadyTasks`——public API 无校验前置）或 overlay 与 frozen 意外撞号，调度将静默按第一个实例判定就绪性，另一个同名任务永远不可见、无任何日志。
- **为何超出本维度**: 无共享可变状态参与（纯读路径的合并语义），缺陷是**冲突被静默吞掉导致调度结果不完整**，属 C9（静默吞异常→状态不一致）与模型校验面交叉。
- **建议归维**: C9 主审。修复方向：`(a,b)->a` 改为抛 `ERR_AGENT_INVALID_ARGUMENT`（与 DagBuilder 对齐）或至少 `LOG.warn` + 计数；或在 `getReadyTasks` 入口前置轻量 taskNo 去重校验。
- **佐证强度**: 中——三种冲突语义的代码事实确凿；实际触发需要上游未走 DagBuilder 校验（当前 executor 挂接点尚未存在，休眠面）。

### [nop-ai-core-nop-ai-agent-CL-005] 工具/LLM 超时只取消包装 Future 不取消底层执行：挂死工具与 LLM 调用占用线程不可见（F5/F8）

- **位置**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentToolDispatcher.java:683-701`（`toolManager.callTool(...).orTimeout(...).exceptionally(...)`——orTimeout 只完成派生阶段，原始 callTool Future 从不 cancel）；`engine/LlmCallCoordinator.java:785-809`（`callChatWithTimeout`：supplyAsync 到 timeoutExecutor + `f.get(llmTimeoutMs)`，超时 `f.cancel(true)`:806——cancel 只中断，非中断阻塞 IO 不退）；兄弟对照 `ToolPhaseSession.cancelCall`:328-336 / abandon 路径 `toolProgressTracker.cancelExecution(callId)`:1622（进程级 kill 通道仅心跳 ABANDON 判定可达，facade 路径 executeAllowedCalls:617-625 无此通道）
- **形态**: T1（ReAct 线程）对挂死工具等待 orTimeout 超时 → exceptionally 产出错误 ToolCallOutput 提交 LLM → 底层工具任务继续在其执行器线程上无限运行（每次超时净占 1 线程，Bash 类工具含挂死的子进程）；LLM 侧同构——超时后 `chatService.call` 任务滞留 timeoutExecutor（默认未装配时无超时保护；装配后线程池若小，几个挂死调用即耗尽槽位，后续 `f.get` 全部超时 = 全引擎 LLM 面瘫痪且无告警区分「熔断」与「槽位饿死」）。
- **为何超出本维度**: 共享可变状态无涉（Future 链 HB 正确，C1 已判定通过）；缺陷是**超时/取消语义只作用于包装层**导致的线程资源耗尽与挂死生产者不可见，属 C3（async chain 取消传播）/C6（资源泄漏）/C8（长时运行退化）交叉，F5（挂死对健康检测不可见）+ F8（清理不对称）。
- **建议归维**: C3 主审（取消传播契约），C6 联合裁定（线程/进程泄漏）。修复方向：orTimeout 的 timeout 分支补 `底层Future.cancel(true)` + 可用时 `toolProgressTracker.cancelExecution(callId)`（对齐 abandon 路径）；`callChatWithTimeout` 记录超时任务并在完成时对账、或改用平台 `IThreadPoolExecutor` 的任务级超时设施。
- **佐证强度**: 高——orTimeout 语义（不取消上游）与 cancel(true) 对非中断 IO 无效均为 JDK 语言事实；代码两站点均无对冲调用。

### [nop-ai-core-nop-ai-agent-CL-006] CheckpointJournalWriter 单 ioLock 串行全部会话追加（javadoc 声明 per-file 锁不符）+ journal.md/checkpoint 表 append-only 无轮转（F4/C6）

- **位置**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/CheckpointJournalWriter.java:41`（`private final Object ioLock = new Object()`——per-写器实例单锁）对照 javadoc :29-31（"append writes are synchronized on a per-file lock... Cross-session writes target independent files and do not contend"——与实现不符：一个 FileBackedCheckpointManager 持有一个 JournalWriter，全会话共用一个 monitor）；增长面 `FileBackedCheckpointManager.java:140-159`（每会话 journal.md 只追加）与 `DBCheckpointManager.java:374`（"The ai_agent_checkpoint table rows are never deleted" 自认）
- **形态**: 共享可变状态本体（ioLock 保护正确，无竞态），缺陷在两点超出 C1：(a) 锁粒度声明与实践不符——多会话并发 saveCheckpoint 在单 monitor 上串行，恢复路径高频写时为可证明的争用热点（C2），且文档性偏差与批次 A 清单 #12（ThresholdBreaker javadoc 不符）同族；(b) journal.md 与 DB 表均为 append-only 永不轮转/清理的持久账本（F4 的持久化形态 + F6 旁支：`Files.write(APPEND)` 无 fsync，断电窗口内容未定义——持久化屏障归 C6）。
- **为何超出本维度**: 内存侧共享状态的原子性/可见性无缺陷（C1 判定通过并入批次 C 清单表 C18）；锁粒度属 C2（争用热点）、持久层无界与落盘屏障属 C6。
- **建议归维**: C2 主审 (a)（per-session 锁分段：`ConcurrentHashMap<Path, Object>` 文件级锁即可兑现 javadoc 承诺）；C6 主审 (b)（journal 轮转/TTL + fsync 策略，与 C1-011 的 DB 行 TTL 合并裁定）。
- **佐证强度**: 高——单 ioLock 与 javadoc 矛盾为逐行对照事实；append-only 无轮转为两实现 javadoc 自认。

### [nop-ai-core-nop-ai-agent-CL-007] DefaultMemberSpawner 超时只放弃等待不取消底层 spawned execution：僵尸成员执行继续消耗配额且 reclaim 后重复 spawn（CL-005 同族兄弟分叉，F5/F8）

- **位置**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/DefaultMemberSpawner.java:182-198`（`future.get(memberExecTimeoutMs)` 超时 → 仅返回 SPAWN_FAILED，无 `engine.cancelSession(spawnedSessionId)` 对冲）；对照兄弟实现 `team/flow/MemberFanOutDispatcher.java:324-337`（`executeBoundMember` 超时分支显式 `agentEngine.cancelSession(target.getSessionId(), …, true)`，javadoc :302-310 明示 "cancel the underlying execution so it does not keep consuming quota as a zombie"）；再对照 `tool/CallAgentExecutor.java:463-480`（同族对冲先例）
- **形态**: T1（spawn worker）对挂死的 spawned 成员执行 `future.get` 超时 → SPAWN_FAILED → fan-out reduce 判败 → 任务留 CLAIMED → plan 240 reclaim 后重新 claim → 重新 spawn **新**成员执行——旧执行未被取消，持续占用 agentExecutor 线程 + LLM 配额 + 检查点写入；每次 reclaim 循环净增一个僵尸执行（F5「挂死生产者不可见」+ F8「清理不对称」）。三个同族站点中两个有 cancel 对冲、唯独 DefaultMemberSpawner 缺失——F3 兄弟分叉的典型形态。
- **为何超出本维度**: Future 链 HB 正确（C1 判定通过，清单 D8）；缺陷是超时/取消语义只作用于等待方的资源泄漏与重复派发，属 C3（取消传播）/C6（线程与配额泄漏）/C8 交叉。
- **建议归维**: C3 主审（对齐 CL-005 裁定）。修复方向：`catch (TimeoutException)` 分支补 `agentEngine.cancelSession(spawnedSessionId, "spawn timeout …", true)`（失败 LOG.warn 不掩蔽超时，照抄 MemberFanOutDispatcher:329-336 模板）；与 CL-005 的 orTimeout-不取消底层修复合并立项。
- **佐证强度**: 高——超时分支无任何 cancel 调用为逐行确认；兄弟实现的对冲模板同模块可直接复制。

### [nop-ai-core-nop-ai-agent-CL-008] DBMessageService 已消费消息行永不删除：ai_agent_message 表 append-only 无 TTL/清理（F4 无界账本 DB 形态，归 C6）

- **位置**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/message/DBMessageService.java:493-515`（`markConsumed` 仅 `UPDATE … SET STATUS=CONSUMED, CONSUMED_AT=?`，无 DELETE/归档）、`:253-294`（sendAsync 只 INSERT）、全类无任何清除语句（grep 复核 `DELETE FROM` 仅零命中）；消费查询只过滤 PENDING:367-399
- **形态**: 消息表为共享可变状态的外置 DB 载体：每条投递过的消息（含完整 `MESSAGE_BODY` JSON CLOB）以 CONSUMED 终态永久留存；long-running 部署按消息历史数线性增长，`findPending` 的扫描随表膨胀劣化（TOPIC+STATUS 索引缓解但死行持续占存储）。与 C1-006 的 `ai_agent_denial`、C1-011 的 `ai_agent_checkpoint`（"rows are never deleted" 自认）同族——但本表**无任何保留语义声明**，且增长速率与消息吞吐同阶（每条 steering/FOLLOWUP/call-agent 信封一行）。
- **为何超出本维度**: 内存侧（`consumers` CHM）已立案 C1-019；缺陷纯在持久层生命周期（F4 的 DB 形态），归 C6。
- **建议归维**: C6 主审（与 C1-006 DBDenialLedger TTL、C1-011 checkpoint 表 TTL 合并裁定统一清理策略：按 CONSUMED_AT 的定期 purge 或分区归档）。
- **佐证强度**: 高——无删除语句为全类 grep 事实；增长速率为发送路径直推。

### [nop-ai-core-nop-ai-agent-CL-009] InMemoryAiMemoryStore 线程安全 javadoc 与实现不符：「each mutation is guarded by synchronized(this)」实为写方法全部未同步（虚假契约，文档性偏差）

- **位置**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/memory/InMemoryAiMemoryStore.java:28-30`（javadoc："a ConcurrentHashMap backs the key→item map, and **each mutation is guarded by synchronized(this)** on the compound read-modify-write sequences"）；实现事实：`add`:79-85 / `update`:122-132 / `remove`:135-140 / `batchAdd`:143-150 均无 synchronized，唯一持锁读 `readBudgeted`:92 与未持锁写者无互斥，`clear`:155 持锁
- **形态**: 实际并发安全性成立（CHM 单操作原子、无复合写、读走弱一致迭代），但 javadoc 声明的同步纪律不存在——后续维护者依契约做「synchronized 读 + 假定写互斥」的复合操作（如 check-then-add 的去重语义）会获得无互斥保证的错误安全感；`readBudgeted` 的 synchronized(this) 在该契约叙事下也从未真正挡住写者。与批次 A 清单 #12（ThresholdBreaker javadoc "synchronized on entry instance" 不符）、批次 C CL-006（CheckpointJournalWriter per-file 锁声明不符）同族第三例。
- **为何超出本维度**: 无实际竞态（C1 判定通过，清单 D19）；缺陷是线程安全文档契约失真，属 C2（并发语义文档）/审计可维护性面。
- **建议归维**: C2 主审（文档一致性批次裁定；与前两例合并立「javadoc 并发契约失真」族）。修复：javadoc 改写为实际机制（"single CHM operations are atomic; readBudgeted/clear take this monitor for snapshot stability, which does NOT exclude writers"）或补齐写侧同步使契约成真。
- **佐证强度**: 高——声明与四个写方法体逐行对照。

### [nop-ai-core-nop-ai-agent-CL-010] DeferredAckMailbox.deadLetter 无界保留且无清理/导出 API（F4-lite，归 C6/C8）

- **位置**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/message/DeferredAckMailbox.java:60,165-179,226-244`（`deadLetter` ArrayList——nack(requeue=true) 达 `maxDeliveryAttempts` 后追加，javadoc :28-29 自认 "retained for inspection; never redelivered"；仅 `deadLetterCount`/`deadLetterEntries` 两个读访问器，无 remove/purge）
- **形态**: 每 mailbox（per-session 生命周期，见批次 C C1-014 的 `sessionMailboxes` 注册表）内的死信列表只增：反复 nack 的毒消息每次死亡循环最多贡献 `maxDeliveryAttempts` 条（默认 5）后转死信，条目含完整 `AgentMessageEnvelope` payload 引用（未序列化，持有原始 ChatMessage/大文本）。会话长期存活（长时运行的常驻 agent 会话）+ 持续毒消息投递时线性累积。
- **为何超出本维度**: 单锁内配对迁移无竞态（C1 判定通过，清单 D15）；缺陷是死信账本的生命周期（无上限声明、无清理路径），且量级受 per-mailbox 生命周期约束，属 C6/C8 轻量面。
- **建议归维**: C6 主审（与 CL-008 消息表 TTL 统一裁定保留策略）。修复：死信上限（超出淘汰最旧并 LOG.warn）或按 `offeredAt` 的 TTL 淘汰；配套导出 API 供运维取证。
- **佐证强度**: 中——无清理路径为逐行事实；实际触发依赖长寿命会话 + 毒消息反复投递的组合。

### [nop-ai-core-nop-ai-agent-CL-011] RuleGraphGuardrail/RuleResultAggregator 每次内容检查重新编译正则（3 站点热路径分配浪费 + 坏 pattern 静默失效与 fail-loud 声明相悖，归 C8/C9）

- **位置**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/guardrail/rule/RuleGraphGuardrail.java:120-127`（`matches`——seed 阶段每候选规则 `Pattern.compile(r.getPattern())`）、`guardrail/rule/RuleResultAggregator.java:126-131`（aggregate 重匹配第二次 compile）、`:133-141`（`applyModify` 第三次 compile）；对照同模块先例 `guardrail/PromptInjectionGuardrail.java:37-61`（4 条 static final Pattern 预编译——正确姿势）
- **形态**: 一次 `RuleGraphGuardrail.check()` 中同一规则的 pattern 最多编译 2 次（seed + aggregate）+ 每条 MODIFY 规则第 3 次（replaceAll）；内容 guardrail 挂在 LLM 输入/输出检查热路径（ReAct 每迭代），规则集来自 YAML、pattern 运行期不可变——编译结果完全可在 `GuardrailRule` 构造期缓存。附带一致性缺陷：compile 失败被 catch 降级为 no-match（RuleGraphGuardrail:123-124、RuleResultAggregator:128-131/138-141 `LOG.warn` 后返回空/原样），坏 pattern 静默失效——与 `GuardrailRuleSet` 构造期 fail-loud 校验纪律（最小规则 #24）相悖，且 warn 在每请求热路径上可刷屏。
- **为何超出本维度**: 无共享可变状态缺陷（不可变判定见批次 E 清单 E9）；属内存/分配浪费与可量化的热路径 CPU 浪费 + 吞错误降级面（F9 旁支）。
- **建议归维**: C8 主审（分配/CPU 退化），C9 联合裁定坏 pattern 降级语义。修复：`GuardrailRule` 构造期 `Pattern.compile` 缓存（编译失败即构造失败，与 RuleSetLoader 的 fail-loud 解析链对齐），check 路径零编译。
- **佐证强度**: 高——3 站点逐行确认；PromptInjectionGuardrail 预编译先例为同包对照（F3 兄弟分叉）。

### [nop-ai-core-nop-ai-agent-CL-012] 压缩摘要与完成裁决两站点直调 chatService.call 无超时包装：挂死 LLM 调用阻塞 ReAct 线程且不可见（CL-005 同族新兄弟，F5/F8）

- **位置**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/compact/Layer3FullSummaryStrategy.java:176`（`chatService.call(request, null)`——压缩管线 Layer3 摘要调用，经 `PipelineCompactor.compact:100` 在 ReAct 线程同步串行执行）、`completion/LlmCompletionJudge.java:90`（完成判定 LLM 调用，完成判定路径同步执行）；对照家族头 CL-005（`AgentToolDispatcher:683-701` orTimeout 不取消底层、`LlmCallCoordinator.callChatWithTimeout:785-809` 为引擎主调用唯一超时通道）与兄弟 CL-007（DefaultMemberSpawner 超时不取消）
- **形态**: 两站点均在 ReAct 线程上同步阻塞等待 LLM 全量响应，无 orTimeout/get(timeout) 任何包装。挂死时 ReAct 迭代永久停滞：无异常、无产出（F5「不抛异常也不产出」的静默挂死对健康检测不可见），`cancelRequested` volatile 标志无人读取（线程阻塞在 LLM socket read 中，循环顶检查点永不抵达）；PipelineCompactor 的 per-layer try/catch（:99-105）只兜异常不兜挂起。
- **为何超出本维度**: 调用链 HB/共享状态无涉（C1 判定通过，批次 E 清单 E10）；缺陷是超时/取消语义未覆盖全部等待点的挂起面与线程占用，属 C3（取消传播）/C5（挂起面）/C8 交叉。
- **建议归维**: C3 主审（与 CL-005/CL-007 合并「超时必须作用于所有等待点」族裁定）。修复：套用 `callChatWithTimeout` 同款包装（timeoutExecutor + `f.get(timeout)`），两站点的降级分支已就绪（Layer3 → fallbackPruner 降层、Judge → fallback decision），超时化后语义即完整闭环。
- **佐证强度**: 高——两站点无超时包装为逐行事实；引擎内已有现成超时模板可直接复制（CL-005 家族第三次登记，同族面扩散证据）。



---

## 维度复核结论（WI 3.1 C1 独立复核，2026-10-18）

- **复核方法**：27 条发现逐条回码验证——file:line 全部在审计工作树（nop-entropy-concurrency-audit）逐一命中，证据语义与 T1/T2 交错序列独立重推演；关键旁证（默认装配、调用方 grep、兄弟实现对照、javadoc 契约）独立取证。对照 `ai-dev/audits/cra-index.md` WI 1.1–2.5 台账完成查重与同案登记。原条目未修改，仅在条目末尾回填复核状态行。
- **总判定**：保留 26 / 降级 1（C1-005 P1→P2）/ 驳回 0 / 待定 0。第 1 轮 27 条全部通过证据核验（无 file:line 失实、无时序虚构、无严重度虚报）；唯一降级为休眠 API 面的条件化降档（口径先例 WI 2.5 C7-005）。第 2 轮（C1-round2.md）零新增立案成立（一处事实性更正见该文件复核节）。
- **现状标注口径**：27 条全部为**新发现**——cra-index（WI 1.1–2.5 台账 + P1 汇总 + CL 台账）无任何 nop-ai 单元登记项；2026-06 各 nop-ai-agent 审计属另一 campaign（功能/依赖/测试维度），未登记本批并发面载体。无已知未修复、无已修复。

### 逐条判定表

| ID | 判定 | 最终严重度 | 现状标注 | 一句话依据 |
|----|------|-----------|---------|-----------|
| C1-001 | 保留 | P2 | 新发现 | 证据行逐行命中；`ModelBasedPromptTemplate` 仅 1 个 final 字段复核确认，后果良性（重复构造+memoization 失效）评估成立 |
| C1-002 | 保留 | P2 | 新发现 | 证据命中；全库 grep 复核确认零 main 调用方（休眠面标注成立）；发布可变异对象重于 C1-001 的论证成立 |
| C1-003 | 保留 | P2 | 新发现 | 7 个 static 非 final 持有者逐一定位命中；`registerInstance` 零库内 main 调用方确认；Yaml `_instance` package-private、`DefaultSystemPromptLoader._instance` public 事实确认 |
| C1-004 | 保留 | P3 | 新发现 | setter 覆盖无 shutdown + DCL 本身正确逐行确认；复核更正：`setRetryScheduler` 全库（含测试）实为零调用方，休眠程度高于原条目「仅测试使用」表述，P3 维持 |
| C1-005 | 已降级 | P2（原 P1） | 新发现 | 写副作用机制逐行确认成立；但引擎缺省装配 `AllowAllPermissionProvider`（ReActAgentExecutorBuilder:623 兜底），本类**零生产实例化**（仅测试 new）——按 WI 2.5 C7-005 先例（零生产实例化 P1→P2）降档；任何生产接线以 5 参 `resolve` per-call 形态使用即回 P1（安全面） |
| C1-006 | 保留 | P2 | 新发现 | `reset` 为唯一删除路径且仅粘性恢复触达（AgentSessionLifecycle:322/331）+ 正常终局无清理逐行确认；config:122-123 引擎默认装配确认 |
| C1-007 | 保留 | P2 | 新发现 | 超时分支无 destroy、`runShortCommand` 5s 超时不 destroy、interrupt 对 `InputStream.read` 无效三点逐行确认；NoOp 兄弟 `killTree` 分叉确认（F3 成立） |
| C1-008 | 保留 | P1 | 新发现 | 唯一 put（:774）+ resume/wake/restore（AgentSessionLifecycle:299/:488/:892）零注册 + 裸 ArrayList（AgentSession:77）无同步 `addPendingInjection`（:83-89）全链确认；公开生命周期 API + 模块自带持久化存储 → 触发链 live，P1 成立（高风险条目，见子项复核） |
| C1-009 | 保留 | P2 | 新发现 | 外层 catch 清理清单缺 `runningInjectionTargets`（:1082-1089 三件套回收但漏此件）+ 终局 remove:1010 先于 terminal save:1050 逐行确认 |
| C1-010 | 保留 | P2 | 新发现 | cancelSession:514 get 判定 + else 分支 setStatus/publishCancelled/checkpoint remove:546 逐行确认；装配窗口（assembleExecutionSetup 含 VFS 加载）宽于代码已注记的 forced-cancel 窗口的论证成立 |
| C1-011 | 保留 | P2 | 新发现 | DBCheckpointManager 三缓存（:93-95）全类无 remove + `ICheckpointManager`:119-120 缺省 no-op 逐行确认；FileBacked/Tool 兄弟覆写完整确认（F3 镜像成立） |
| C1-012 | 保留 | P2 | 新发现 | `ensureSessionLoaded` putIfAbsent（:301-303）+ `loadSessionFromDb` 整表替换（:376）+ 写穿透 computeIfAbsent+add（:209）复合无同步逐行确认；FileBacked:279 同构确认 |
| C1-013 | 保留 | P3 | 新发现 | `agentExecutor` 非 volatile（:130）+ synchronized getter/裸 setter 非对称 + SessionLockRenewal 同构逐行确认；当前唯一用法为 Builder 装配期单线程 → P3 与批次 A C1-004 跨批一致 |
| C1-014 | 保留 | P3 | 新发现 | 三账本无删除 API（entries:60 / sessions:47 / mailboxes:31-32）+ 引擎终局清单仅 checkpointManager.remove（:1052-1058）逐行确认 |
| C1-015 | 保留 | P3 | 新发现 | volatile double factor（:49）+ record 读-改-写三步（:81-84）逐行确认；config:113 单实例全会话共享确认；后果限于估算精度的校准成立 |
| C1-016 | 保留 | P2 | 新发现 | stores:20 无删除 API + config:126 **默认即 `new InMemoryMemoryStoreProvider()`**（引擎默认装配，非 opt-in）逐行确认 |
| C1-017 | 保留 | P2 | 新发现 | tasks.put:100 唯一写点、全类无 remove、OUTPUT_TAIL_LIMIT=8KB（:48）逐行确认；「bounded」声明仅覆盖单条 tail 不覆盖注册表规模的解读成立 |
| C1-018 | 保留 | P3 | 新发现 | replySubscriptions:57 只增（put:131）无注销/无 close 逐行确认；DCL 本身正确（快读+synchronized 双检） |
| C1-019 | 保留 | P2 | 新发现 | `consumers.put`（:304）静默覆盖 + `unregisterConsumer` remove 无身份校验（:310-313）+ `DbSubscription.cancel`（:650-653）逐行确认 |
| C1-020 | 保留 | P2 | 新发现 | `cacheEnabled()==currentTenant()==null`（:139-141）+ 租户模式 get 每次从 DB 新建实例（:182-195）逐行确认；与 C1-008 机制互补不重叠的论证成立（高风险条目，见子项复核） |
| C1-021 | 保留 | P2 | 新发现 | loadPersisted 全程锁外（get:163-174）+ `readAllLines`:455 与 APPEND 写 :355 交叠 + heal `deleteIfExists`:487 在锁外 + save 全程锁内（:311-323）逐行确认；误 heal 删除已确认 delta 机制经 `replayJournal` 全文复核成立（高风险条目，见子项复核） |
| C1-022 | 保留 | P3 | 新发现 | 7 组载体字段-行号逐一命中（plain 非 volatile + public setter）；per-invocation 新建/单 scan 线程掩护的注记成立 |
| C1-023 | 保留 | P2 | 新发现 | addMember `size()+1` 快照→putIfAbsent 两步、bindMemberSession 快照→enforceQuota→compute 三步逐行确认；javadoc「serialised by compute」不覆盖配额判定的契约缺口确认 |
| C1-024 | 保留 | P3 | 新发现 | 全类无 remove/purge/delete grep 确认 + 接口无清理契约确认；opt-in（config 默认 NoOpTeamTaskStore）确认 |
| C1-025 | 保留 | P2 | 新发现 | `bySource.remove`（:116）锁外摘键 + 过期快照回删（:131-134）交错序列逐行可构造确认；javadoc :102-104「also acquires the matching per-type locks」与实现不符确认；opt-in（config:146 NoOp 默认）确认 |
| C1-026 | 保留 | P3 | 新发现 | computeIfAbsent:53 建键无删除 + releaseSession 全桶扫描（:82）+ javadoc 保留声明仅覆盖监视器稳定性动机确认；config:145 **引擎默认装配**确认 |
| C1-027 | 保留 | P3 | 新发现 | 双 map（:58-59）无删除 API 确认；全库 grep 零生产接线（仅 javadoc 引用与测试门清单）确认休眠面标注 |

### 高风险条目子项复核说明

- **C1-008（P1，本维唯一 P1）**：子项复核三点。(1) 注册不对称：全文件 grep 确认 `runningInjectionTargets` 仅有 get:617 / put:774（doExecute）/ remove:1010 三处，resume（lifecycle:299）/wake（:488）/restore（DefaultAgentEngine:892）均只 `runningExecutions.putIfAbsent`，三恢复路径执行期 injectInput 必落暂存分支；(2) 受害载体：`AgentSession.pendingInjections` 为裸 ArrayList（:77），`addPendingInjection`（:83-89）无同步无 COW；(3) 后果链：`ReActAgentExecutor` 每轮 `replaceMessages`+`sessionStore.save`（:1139-1140）与暂存分支的 add+save 无任何共享锁——FileBacked 序列化遍历期间结构性变化 → CME/撕裂落盘，机制为集合与序列化语义事实。触发面为公开 API 正常用法（运行中恢复会话 + 并发注入），不属启动期/休眠豁免。**P1 维持**。
- **C1-021（P2 顶格）**：子项复核 `replayJournal` 全文（:448-496）。确认 decodeEntry 失败即 `healed=true` → `deleteIfExists(journalFile)`（:487）删除整个 journal，且该读-判-删全程在 per-session 锁外；save 侧 `Files.write(APPEND)`:355 在锁内但锁对装载线程无约束力。交错后果：并发在途 delta 被判 torn tail 连同已完整写入各行一并删除，save 方簿记（journalEntryCount）与磁盘发散，崩溃窗口丢数据边界从折叠边界放大到整个 journal。**未升 P1 的理由**：显性化需三重复合（同会话冷装载 × 在途追加 × 后续崩溃先于下次折叠），且 heal 折叠分支会以 replay 后快照重建基线部分兜底——符合 P2「罕见但可具体化」校准上沿；修复与 C1-012 统一 per-session 装载锁模板，应列 P2 修复顺位首位。
- **C1-005（P1→P2 降档）**：子项复核实例化面——`DefaultAgentEngineConfig.permissionProvider`（:107）无默认初始化，缺省兜底为 `AllowAllPermissionProvider`（ReActAgentExecutorBuilder:623），`DefaultPermissionProvider` 全库仅测试实例化。缺陷机制（5 参 resolve 写共享字段 + 保留调用方活引用 + 无 HB）逐行成立，但按 WI 2.5 C7-005「零生产实例化 P1→P2」条件化降档先例降为 P2，并附**激活回升条款**：任一集成方按 API 形态（per-call 传 sessionPerms——该签名唯一合理用法）生产接线即回 P1。修复方向（去状态化）不变。
- **C1-020（P2）**：子项复核租户旁路动机与代价——javadoc :80-85 旁路动机（sessionId 键不区分租户）成立，但同进程对象身份分裂未被任何同步或文档承接；journal `state` 块整表携带 pendingInjections 的 last-writer-wins 覆盖机制与 save/replay 结构一致。P2 维持（丢注入为单条、无损坏，租户模式为该 store 旗舰形态的加重项已在严重度理由中体现）。

### 同案命中清单（cra-index 对照）

| 本维条目 | 台账同案/关联 | 关系说明 |
|---------|--------------|---------|
| C1-013、C1-022 | WI 2.2 C1-007（配置发布协议三字段裸写裸读，P1） | 同案：裸写裸读/运行期换装可见性族——彼为 live 面（7 写点×8 读站）P1，此为 setter 零运行期调用方休眠面 P3，校准一致 |
| C1-019、C1-025 | WI 2.4 CL-415（SqlDataTypeMapping.register 并发无同步，watch） | 同案：注册表并发写族——平台侧 watch 级、模块侧立案，载体不同不重复立项 |
| C1-007 | WI 2.1 CL-021（raw JDK/Netty 载体绕过设施移交面，含 nop-ai-agent） | 关联非同案：本条缺陷本体为进程清理不对称（F8），非载体治理；修复批次可与 CL-021 的 nop-ai-agent 移交面同批清扫 |
| C1-001 | WI 2.1 CL-035（ClassModel.beanModel 双检锁缺 volatile） | 同案：共享缓存模型惰性记忆化缺 volatile 同族，修法同源（volatile 化或去 memoization） |
| C1-003 | WI 2.1 CL-030（GlobalMeterRegistry 非原子替换） | 同案：静态持有者非原子换装族 |
| C1-005 | WI 2.5 C7-005 | 降档口径先例（零生产实例化 P1→P2），非同案 |
| C1-006/011/014/016/017/018/024/026/027（F4 族） | WI 1.1 C1-006、WI 1.6 C7-001、WI 1.7 C7-003 | 跨单元同族对照（账本只增不删）：载体与模块均不同，不重复立项；Phase 8 批量修复时按同族统一清理策略设计 |

## 最终保留项（27 条全保留；最终严重度分布 P1×1 / P2×17 / P3×9）

| ID | 标题 | 最终严重度 | 现状 | 同案指针 |
|----|------|-----------|------|---------|
| C1-008 | injectInput 暂存分支与 resume/wake/restore 执行并发改写共享 AgentSession | P1 | 新发现 | 关联 WI 2.4 C2-001（session 零互斥平台侧同族） |
| C1-021 | 会话存储锁外装载 + 误 heal 删除已登记 journal delta | P2 | 新发现 | C1-012 同族（统一装载锁模板）；P2 修复顺位首位 |
| C1-005 | DefaultPermissionProvider 共享单例 per-session 规则写副作用 | P2（原 P1，降档） | 新发现 | 口径先例 WI 2.5 C7-005；激活即回 P1 |
| C1-020 | DBSessionStore 租户模式绕过缓存多实例 last-writer-wins 丢注入 | P2 | 新发现 | C1-008 互补；与 C1-021 修复联动 |
| C1-023 | InMemoryTeamManager 配额检查-然后-行动突破并行绑定上限 | P2 | 新发现 | — |
| C1-019 | DBMessageService.consumers 同 topic 静默替换 + cancel 无身份窃取 | P2 | 新发现 | 同案 WI 2.4 CL-415 |
| C1-025 | InMemoryContributionRegistry unregister 过期键快照回删已完成注册 | P2 | 新发现 | 同案 WI 2.4 CL-415 |
| C1-017 | DefaultBackgroundTaskRegistry.tasks 终态 Entry 永不淘汰（8KB/条） | P2 | 新发现 | F4 族 |
| C1-016 | InMemoryMemoryStoreProvider.stores 引擎默认无删除路径 | P2 | 新发现 | F4 族 |
| C1-011 | DBCheckpointManager 未覆写 remove：终局清理静默 no-op | P2 | 新发现 | F4 族；WI 1.1 C1-006 对照 |
| C1-012 | 检查点缓存 ensureSessionLoaded 空读窗口 + 整表替换发散 | P2 | 新发现 | C1-021 同族 |
| C1-006 | DefaultDenialLedger/FingerprintPostDenialGuard 无 TTL 无上限 | P2 | 新发现 | F4 族；WI 1.1 C1-006 对照 |
| C1-007 | DockerSandboxBackend 超时不 destroy 本地进程 + interrupt 无效 | P2 | 新发现 | 关联 WI 2.1 CL-021；F3 与 NoOp 分叉 |
| C1-010 | cancelSession 装配窗口 TOCTOU：取消意图静默丢失 | P2 | 新发现 | — |
| C1-009 | runningInjectionTargets 拒绝路径泄漏 + remove 先于 terminal-save 丢注入 | P2 | 新发现 | C1-008 同根 |
| C1-003 | 7 个 static 非 final 单例无同步运行期换装面 | P2 | 新发现 | 同案 WI 2.1 CL-030 |
| C1-002 | PromptOutputModel 惰性发布构造后变异对象（休眠面） | P2 | 新发现 | C1-001 同族 |
| C1-001 | PromptTemplateManager 全局缓存模型非原子惰性记忆化 | P2 | 新发现 | 同案 WI 2.1 CL-035 |
| C1-022 | 批次 D 7 组 plain 字段 + 运行期公开 setter 无可见性保障 | P3 | 新发现 | 同案 WI 2.2 C1-007；C1-013 同族 |
| C1-013 | agentExecutor/lockRenewExecutor 非对称锁 + 孤儿池不 shutdown | P3 | 新发现 | 同案 WI 2.2 C1-007；批次 A C1-004 原案 |
| C1-004 | EmbeddingServiceImpl.retryScheduler 换装废弃旧调度器不 shutdown | P3 | 新发现 | 换装族原案 |
| C1-018 | LocalAgentMessenger.replySubscriptions 只增不删 | P3 | 新发现 | F4 族 |
| C1-015 | CalibratedTokenEstimator EMA 读-改-写丢失更新 | P3 | 新发现 | — |
| C1-014 | SessionGoalTracker/DefaultWaitCoordinator/sessionMailboxes 三账本无删除 | P3 | 新发现 | F4 族 |
| C1-024 | InMemoryTeamTaskStore 终态任务永久累积（opt-in） | P3 | 新发现 | F4 族 |
| C1-026 | InMemoryWriteIntentRegistry 路径键只增不删 + releaseSession O(N) 扫描 | P3 | 新发现 | F4 族 |
| C1-027 | DefaultFencingTokenService 双计数器无删除/复位（休眠面） | P3 | 新发现 | F4 族 |

> 修复立项建议（供 7.1/Phase 8 参考）：P1×1（C1-008）入批量修复；P2 修复顺位 C1-021 → C1-008 同 diff 面（runningInjectionTargets 三恢复路径注册）→ C1-020（与 C1-021 统一存储锁设计）→ C1-011/C1-006/C1-016/C1-017（F4 引擎默认四件统一终局清理清单）→ 其余 P2 逐项裁决；P3 中 C1-004/C1-013/C1-022 换装族一次清扫（全家族 volatile 化）。
