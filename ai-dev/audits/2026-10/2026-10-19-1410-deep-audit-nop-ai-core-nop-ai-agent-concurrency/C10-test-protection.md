# C10 测试保护力 — nop-ai-core-nop-ai-agent（WI 3.1）

> 审计人：C10 维度（并发正确性的测试保护力，最后批次）。日期：2026-10-20。
> 输入：同目录 C1/C3/C4/C5/C7 五维 `## 最终保留项`（合计 128 条：P0×0 / P1×30 / P2×76 / P3×22）+
> `ai-dev/skills/concurrency-resource-audit-prompt.md` L26–118（F 画像与前缀）+ L329–350（C10 正文）+ L351–377（附录 A）。
> 评估对象即测试代码本身；源码根与审计基线一致（nop-ai 零改动）。

## 审计范围与口径

**范围**：对五维 128 条最终保留发现逐条做变异推演——「若缺陷被修复后又回退（缺陷复发），现有哪个测试会红？」。检索面为 nop-ai-core / nop-ai-agent（及缺陷链涉及的 nop-http-client-jdk）`src/test/java` 全量 763 个测试文件（core 65 + agent 698）。

**三态判定口径**（本批 128 条全部为「缺陷在码、套件绿」状态，故判定按下述操作化）：

- **会红·有保护**：存在对缺陷语义的确定性断言，缺陷复发必红。逻辑约束：缺陷当前在码且 CI 绿 ⇒ 确定性红测试不可能存在；唯一可能是「环境门内会红」（docker/LM Studio 门内执行）。**本批实测为 0**（docker 门内集成用例只覆盖 happy path，见 C1-007 行）。
- **假保护**：载体类或缺陷语义的邻近面有测试，但断言的不是缺陷语义、或覆盖不到触发时序/装配条件——修复落地后这些测试仍绿，回退（缺陷复发）它们仍绿。
- **无测试**：按类名/方法名/行为关键词 grep 测试源零命中，或唯一触达测试整体 `@Disabled`/环境门内且该轴无用例。

**检索纪律**：每条「无测试」判定均给出至少一条检索路径（类名 grep / 方法名 grep / 行为关键词），见矩阵证据列。已验证的共性检索：`grep -rl "<载体类名>" nop-ai/*/src/test/java`。

**与缺陷本体计数关系**：缺保护登记（P2）与缺陷本体分开计数，按同案保护面合并为 19 条（C10-001…019），每条列出覆盖的缺陷成员。

---

## 保护力矩阵

判定分布：**会红·有保护 ×0 ／ 假保护 ×103 ／ 无测试 ×25**（P1×30 全部落入假保护 23 + 无测试 7）。

### C1 共享可变状态（27 条）

| ID | 模块 | 判定 | 证据 |
|----|------|------|------|
| C1-001 | nop-ai-core | 假保护 | `TestCorePromptsParse.java` 仅钉 prompt 解析行为（PromptTemplateManager 唯一测试引用）；缓存惰性记忆化原子性零断言 |
| C1-002 | nop-ai-core | 无测试 | grep `getMarkdownTplWithoutDetail\|PromptOutputModel` → 仅 `TestPromptErrorCodeConversion.java:35`（错误码转换）；惰性发布可变对象零断言 |
| C1-003 | nop-ai-core | 假保护 | `TestXmlResponseParser/TestCodeResponseParser` 等钉静态实例上的解析行为；7 个 static 持有者换装/可见性零断言；`DefaultSystemPromptLoader` 测试引用 0 命中 |
| C1-004 | nop-ai-core | 假保护 | `TestEmbeddingServiceImpl.java` 12 用例钉重试/failover 语义；grep `setRetryScheduler\|shutdown\|close` 于该文件 = 0，调度器生命周期零断言 |
| C1-005 | nop-ai-agent | 假保护 | `TestDefaultPermissionProvider.java` 11 用例钉优先级语义（含 5 参 resolve）；跨会话规则串扰/并发写零断言 |
| C1-006 | nop-ai-agent | 假保护 | `TestDefaultDenialLedger/TestFingerprintPostDenialGuard/TestResumeSessionPostDenialGuardReset` 钉计数与 resume-reset（唯一删除路径被正面钉住）；TTL/容量零断言 |
| C1-007 | nop-ai-agent | 假保护 | `TestDockerSandboxBackend.java:417` 钉 `--name`（kill 前置条件）+:356 钉退出码分类；超时路径真实 destroy 无用例——docker 集成用例 :536 Assumptions 门内且仅 happy path（exit 0 echo） |
| C1-008 | nop-ai-agent | 假保护 | `TestSteeringInjection.java`（9 用例）/`TestInjectionSemanticsTrichotomy.java:179`（Thread.sleep(20)）钉 steering/staging 语义；resume/wake/restore × injectInput 并发交错零用例 |
| C1-009 | nop-ai-agent | 无测试 | grep `runningInjectionTargets` 于 agent 测试源 = 0 命中；`TestEngineEntryCleanupSymmetry` 仅钉 actorRuntime/lock 两件、`TestInvariantGate3EntryPointCleanup` 仅钉 runningExecutions/checkpoint——双清理清单均不含本载体 |
| C1-010 | nop-ai-agent | 假保护 | `TestDefaultAgentEngineCancel.java:229-235` 钉 ghost-session fail-fast、:238-330 钉 graceful/forced happy path；装配窗口 TOCTOU 时序零用例 |
| C1-011 | nop-ai-agent | 无测试 | grep `remove` 于 `TestDBCheckpointManager.java` = 0 命中；且 `TestInvariantGate3EntryPointCleanup.java:87,136` 反向钉住 `default void remove` 源 marker（见反向钉死 R2） |
| C1-012 | nop-ai-agent | 假保护 | `TestDBCheckpointManager.java`（22 用例）/`TestFileBackedCheckpointManager.java`（14+ 用例）全单线程行为/roundtrip（:488 注记 byWatermark 装载语义）；putIfAbsent 竞态/整表替换发散零用例 |
| C1-013 | nop-ai-agent | 假保护 | `TestEngineLifecycleAndCheckpointBounded.close_shutsDownSelfCreatedPools` 钉 close 关自建池；`TestEngineConfigAndHelpers.configSetterRoundTrips` 钉 setter 往返——setter 换装可见性/孤儿池轴零断言 |
| C1-014 | nop-ai-agent | 假保护 | `TestSessionGoalTracker`（windowSliding 钉 per-session 窗口驱逐）/`TestDefaultWaitCoordinator`（7 用例钉 wait 语义）；跨会话账本增长/终态清理零断言 |
| C1-015 | nop-ai-agent | 假保护 | `TestCalibratedTokenEstimator.java` 7 用例全单线程（EMA 收敛/钳制）；并发丢失更新零用例 |
| C1-016 | nop-ai-agent | 假保护 | `TestInMemoryMemoryStoreProvider.concurrentGetOrCreateReturnsSameInstance`（真并发，但保护「同实例」轴）；无删除路径轴零断言 |
| C1-017 | nop-ai-agent | 假保护 | `TestDefaultBackgroundTaskRegistry.java` 10 用例仅断言 statuses/完成语义（:53 `statuses.size()==1`）；注册表规模/淘汰零断言 |
| C1-018 | nop-ai-agent | 假保护 | `TestLocalAgentMessenger.java:269-284` 钉 handler 订阅 cancel；replySubscriptions（:123-134 载体）生命周期零断言 |
| C1-019 | nop-ai-agent | 假保护 | `TestDBMessageService.java:250-279` 单订阅者 cancel happy path（缺陷下同样绿）；同 topic 静默替换/cancel 窃取后注册者场景零用例 |
| C1-020 | nop-ai-agent | 假保护 | `TestMultiTenantDbIsolation.sessionStoreIsolatesByTenant` + `TestDefaultAgentEngineMultiTenantIsolation` 钉租户隔离（与缓存旁路兼容）；实例分裂/last-writer-wins 丢注入零断言 |
| C1-021 | nop-ai-agent | 假保护 | `TestFileBackedSessionStore.java` 30 用例（corrupt JSON fail-fast :237、discovery 隔离 :456）+ `TestSessionStoreIntraExecutionPersistence`（journal replay roundtrip）；锁外装载 × save 交叠/误 heal 删 delta 零用例（grep heal/torn 仅注释） |
| C1-022 | nop-ai-agent | 假保护 | `TestTeamExecuteFlowExecutor.java:759-800` 钉 setMemberSpawner 往返（缺陷下同样绿）；7 组 plain 字段可见性零断言 |
| C1-023 | nop-ai-agent | 假保护 | `TestTeamManagerQuotaEnforcement`（6 用例）+ `TestResourceGuardQuotaEndToEnd` 全单线程顺序 enforce；并发 check-then-act 突破零用例 |
| C1-024 | nop-ai-agent | 假保护 | `TestInMemoryTeamTaskStore/Reclaim/Transitions` 钉状态转换与 reclaim；reclaim≠删除，tasks/teamIndex 增长零断言 |
| C1-025 | nop-ai-agent | 假保护 | `TestInMemoryContributionRegistry.java:127-208` unregister 用例全单线程顺序；register/unregister 交错回删零用例 |
| C1-026 | nop-ai-agent | 假保护 | `TestInMemoryWriteIntentRegistry.releaseSessionClearsItsIntents` 钉 intents 清除（与空桶保留兼容）；键骨架增长/O(N) 扫描零断言；`TestWriteIntentRegistryLifecycle` 为引擎装配面 |
| C1-027 | nop-ai-agent | 假保护 | `TestFencingTokenService.java` 7 用例钉 token 判定语义；计数器增长/复位零断言（休眠面，全库零生产接线已在 C1 复核确认） |

### C3 异步链路（32 条）

| ID | 模块 | 判定 | 证据 |
|----|------|------|------|
| C3-001 | nop-ai-core | 假保护 | ChatServiceImpl 唯一直测类 `TestChatServiceImpl` 整类 `@Disabled`（LM Studio 门）；`TestChatServiceImplCallPathContract`（1 用例）钉 fetchServerEventFlow 接线合同；取消→publisher 关闭轴零断言 |
| C3-002 | nop-ai-core | 无测试 | grep 背压轴（request 计数/阻塞 submit）于 core 测试源 = 0 命中；`TestStreamAggregator` 9 用例仅断言聚合语义不触及订阅请求量 |
| C3-003 | nop-ai-core | 假保护 | `TestChatLogHelper/TestChatLoggerRedaction` 钉日志内容/脱敏；「日志 I/O 先于 complete」次序零断言；主类 @Disabled |
| C3-004 | nop-ai-core | 假保护 | `TestEmbeddingServiceImpl` 钉重试/账号链语义（:218 no-silent-retry、:228 NON_TRANSIENT 不重试）；取消后幽灵重试零用例（ICancelToken 仅出现在 stub 签名 :258） |
| C3-005 | nop-ai-core | 假保护 | `TestChatServiceImplErrorResponse`（4 用例）/`TestLlmErrorMapping` 钉错误响应映射；grep `returnExceptionAsResponse` = 0——同步抛出逃逸包络轴零断言 |
| C3-006 | nop-ai-core | 无测试 | 缓存 load/save 失败注入零用例（grep save.*fail/failing persister = 0）；`TestAiCommandExecute` 仅 3 用例钉重试计数 |
| C3-007 | nop-ai-core | 无测试 | grep `requestTimeout` 于 core+agent 测试源 = 0 命中——死契约无任何测试触达 |
| C3-008 | nop-ai-core | 无测试 | 晚订阅竞态唯一触达点在 @Disabled 的 `TestChatServiceImpl.testCallStream`；active 测试零触达 aggregateStreamToResponse 订阅时序 |
| C3-009 | nop-ai-core | 无测试 | 派发执行器选择不可观测——grep `commonPool\|getExecutor` 于 ChatServiceImpl 相关测试 = 0 断言；CallPathContract 合同测试不含执行器轴 |
| C3-010 | nop-ai-agent | 假保护 | `TestSessionLifecycleSaveBeforeRelease.doExecute_saveFailure_propagatesErrorAndStillReleasesLock:199-218` 钉终局 save 失败→错误传播+锁释放；注册→dispatch 前置窗口的槽位/injectionTargets/续期孤儿化零用例 |
| C3-011 | nop-ai-agent | 假保护 | `TestHeartbeatWaitPhase.abandonCancelsPendingWithSyntheticResult` 钉 ABANDON 合成结果（ReAct 层唯一 tracker kill 点）；dispatcher orTimeout 不传播/cancelCall 无 kill 通道零断言 |
| C3-012 | nop-ai-agent | 假保护 | `TestSingleTurnExecutor.testHangingChatServiceTimesOutAndFails` 钉超时触发；`f.cancel(true)` no-op 后幽灵任务续跑零断言 |
| C3-013 | nop-ai-agent | 假保护 | `TestInjectionSemanticsTrichotomy.terminalResidualDrainCapturesInjectionFromFinalLlmCall:195-221` 钉终局 drain 捕获（正确面）；live-after-drain 丢失/staging 双线程写零用例 |
| C3-014 | nop-ai-agent | 假保护 | `TestResumeSession`/`TestWaitForWakeEndToEnd` happy path + `TestTakeoverLockHeartbeatRenewal` 三恢复路径钉续期；恢复路径 injectionTargets 注册缺失零断言 |
| C3-015 | nop-ai-agent | 无测试 | grep cancelSession×tenant 于测试源 = 0 命中；`TestSessionLifecycleLockGate.wakeSession_restoresCallerTenantContextAfterSyncPhase` 只钉 wake 兄弟的正确模式 |
| C3-016 | nop-ai-agent | 无测试 | grep sessionMailboxes 清理于测试源 = 0 命中；`TestEngineLifecycleAndCheckpointBounded`/gate3 清理断言均不含邮箱双表 |
| C3-017 | nop-ai-agent | 假保护 | `TestDefaultWaitCoordinator.timeoutConditionSatisfiedWhenDeadlinePassed` + `TestWaitForWakeEndToEnd.timeoutConditionAdvancesToProceedOnClockAdvance` 钉 deadline 触发；陈旧 deliverWake 提前满足后续等待零用例 |
| C3-018 | nop-ai-agent | 假保护 | `TestDefaultOrphanRecoveryHandler` 9 用例钉同步 outcome 模式；fire-and-forget future 丢弃轴对同步调用不可见，零断言 |
| C3-019 | nop-ai-agent | 假保护 | `TestToolResultCommitOrdering` 7 用例钉提交次序 happy path；逃生舱（timeout≤0）批内失败中止整批零用例 |
| C3-020 | nop-ai-agent | 假保护 | `TestScheduledRecoveryManagerScheduling`（8 用例）钉 start/stop/schedule 周期；`scanOnceSafe` 仅捕 RuntimeException 的守卫面零断言（Error 逃逸无注入用例） |
| C3-021 | nop-ai-agent | 假保护 | `TestDBMessageService`（8）+`CrossInstance`（4）+`Reliability`（8，含 `scheduledSweepKeepsRunningAndDoesNotBreakPolling`）钉投递/sweep/跨实例；单 poller 内联互饿/嵌套委派冻结零用例 |
| C3-022 | nop-ai-agent | 无测试 | grep `close\|leak\|shutdown` 于 `TestTeamTaskFlowOrchestrator.java`/`TestTeamExecuteFlowExecutor.java` = 0 命中（后者仅 setter 往返 :759-800）——orchestrator 池泄漏零断言 |
| C3-023 | nop-ai-agent | 假保护 | 同 C1-017：`TestDefaultBackgroundTaskRegistry` 钉 submit/complete/status；账本增长零断言 |
| C3-024 | nop-ai-agent | 假保护 | 同 C1-018：`TestLocalAgentMessenger` 钉 request/response/timeout/demux；reply 订阅数增长 × 轮询放大零断言 |
| C3-025 | nop-ai-agent | 假保护 | `TestMemberFanOutDispatchTimeout.hangingMemberTimesOutCancelsSessionAndReportsHonestFailure`（:65 记录 cancelSession）钉 fan-out BOUND 正确兄弟；单成员/ spawn 路径无 per-member 期限轴零用例 |
| C3-026 | nop-ai-agent | 假保护 | `TestAsyncSpawnStepTenantPropagation.noTenantLeakAcrossSequentialRuns`（:65-67,:307,:352）钉 spawn 路径跨 run 无泄漏；reduce+complete 回调清除 enclosing 租户窗口轴零用例 |
| C3-027 | nop-ai-agent | 假保护 | `TestTeamTaskSchedulerDaemon`（start/stop/graceful :433-468）+ `TestDBMessageService.closeStopsPollingAndReleasesThread` 钉生命周期；Error 逃逸停摆零注入用例 |
| C3-028 | nop-ai-core | 假保护 | 五方言流式用例 fixtures 全部单 entry（`TestOpenAiDialect:195-214` index:0 单条、`TestOllamaDialect:191-213` 单 tool_call）；同事件多 entry 截断零 fixture；`TestStreamAggregator.testAggregator_multipleToolCallsDistinguishedByItemIndex` 只保护聚合侧 |
| C3-029 | nop-ai-agent | 无测试 | grep 审计失败注入（throwing audit logger）= 0 命中；`TestAuditLoggerDefault`/`CollectingAuditLogger`（测试助手）仅钉默认行为——副作用中止主流程轴零断言 |
| C3-030 | nop-ai-core | 假保护 | `TestResponsesDialect.java:485-495` 钉 Responses 流式 usage（正确兄弟）；`TestStreamAggregator.testAggregator_usage:49-65` 钉聚合通道；OpenAi/Gemini/Ollama parseStreamChunk 源头零 usage 断言 |
| C3-031 | nop-ai-core | 假保护 | `TestOllamaDialect.testParseStreamChunkToolCallsPreserveArguments:191-213` fixture 以 `"content":null` 钉住——恰好掩蔽空串变体（反向钉死 R1）；`content:""` 行为零断言 |
| C3-032 | nop-ai-core | 无测试 | grep `error` 于 `TestAnthropicDialect` 流式段 = 0 命中；五方言流式带内错误载荷零断言（`TestLlmDialectErrorResponse` 仅非流式 parseErrorResponse） |

### C4 线程池（16 条）

| ID | 模块 | 判定 | 证据 |
|----|------|------|------|
| C4-001 | nop-ai-core | 假保护 | 同 C3-001/009：主类 @Disabled + 合同测试不可观测执行器/盘写位置；commonPool 承载与同步盘写零断言 |
| C4-002 | nop-ai-core | 假保护 | `TestServerEventPublisherStreamFix.testNoEventsAfterCancel`（nop-http-client-jdk）钉「cancel 后不再发事件」（缺陷下同样绿）；publisher.close 缺失/无终态信号零断言 |
| C4-003 | nop-ai-core | 假保护 | `TestEmbeddingServiceImpl` 重试用例基于 Exception 路径（:218/:228）；Error 逃逸/`ScheduledFuture` 丢弃零断言 |
| C4-004 | nop-ai-core | 假保护 | 同 C1-004：`TestEmbeddingModelBeanAssembly` 装配 + `TestEmbeddingServiceImpl` 重试语义；bean stop 钩子缺失/换装孤儿池零断言（grep shutdown = 0） |
| C4-005 | nop-ai-core | 假保护 | `TestEmbeddingServiceImpl` 无任何取消语义用例（ICancelToken 仅 stub 签名 :258）；取消不可达轴零断言（零直接断言，测试面存在故记假保护下沿） |
| C4-006 | nop-ai-agent | 假保护 | `TestCallAgentDelegationDepthGuard` 5 用例钉深度守卫（正确面）；同池嵌套 join/池耗尽级联零用例 |
| C4-007 | nop-ai-agent | 假保护 | `TestTakeoverLockHeartbeatRenewal` 钉续期周期/lease-lost abort；`SessionLockRenewal.renewOnceSafe` Error 逃逸停转零注入用例 |
| C4-008 | nop-ai-agent | 假保护 | `TestTakeoverLockHeartbeatRenewal.doExecuteRenewsPeriodically` 钉共享调度器存在与周期；DB 延迟队头阻塞级联零用例 |
| C4-009 | nop-ai-agent | 假保护 | `TestSingleTurnExecutor` 10 用例钉单轮执行/超时；执行器选择（commonPool 兜底）零断言 |
| C4-010 | nop-ai-agent | 无测试 | 同 C3-022：grep `close\|leak\|shutdown` 于 TeamExecuteFlowExecutor/Orchestrator 测试 = 0 命中 |
| C4-011 | nop-ai-agent | 无测试 | grep `AgentDispatchExecutor` 于 agent 测试源 = 0 命中——类级零测试触达 |
| C4-012 | nop-ai-agent | 假保护 | `TestDefaultBackgroundTaskRegistry`+`TestBackgroundTaskEndToEnd` 钉提交/完成桥；grep `timeoutMs` 于两文件 = 0——超时无执行点轴零断言 |
| C4-013 | nop-ai-agent | 假保护 | 同 C3-027：轮询/daemon 生命周期被钉，Error 逃逸零注入用例 |
| C4-014 | nop-ai-agent | 假保护 | `TestTaskDispatchCoordinator.dispatchClaimedTaskCompletedSynchronouslyForSingleBoundTarget` 单 bound happy path；悬挂成员→节点 future 永不 settle 零用例（fan-out BOUND 兄弟被 `TestMemberFanOutDispatchTimeout` 钉住） |
| C4-015 | nop-ai-agent | 假保护 | `TestSpawnDispatchTimeout.daemonInFlightQueueDrainsAfterSpawnTimeout`+`daemonPathWithRealSpawnerAndHangingEngineDrainsQueue` 钉超时 drain；stop()-during-queue 丢弃/inFlight 泄漏零用例 |
| C4-016 | nop-ai-agent | 假保护 | 同 C1-018/C3-024：reply 订阅生命周期零断言 |

### C5 内存边界（21 条）

| ID | 模块 | 判定 | 证据 |
|----|------|------|------|
| C5-001 | nop-ai-core | 假保护 | `TestStreamAggregator` 9 用例钉聚合正确性；无界累积/`request(MAX_VALUE)`/键累加上界零断言；主链集成测试 @Disabled |
| C5-002 | nop-ai-core | 假保护 | `TestAiChatResponseCacheTtl.testTtlExpiryDeletesEntry` 钉 TTL>0 过期；默认 TTL=0/无容量/整条物化零断言 |
| C5-003 | nop-ai-core | 假保护 | `TestChatLogHelper`/`TestChatLoggerRedaction` 钉内容/脱敏；轮转/保留期零断言 |
| C5-004 | nop-ai-core | 无测试 | grep `LocalFileOperator\|IFileOperator` 于 nop-ai 全部测试源 = 0 命中——文件操作符类级零测试 |
| C5-005 | nop-ai-core | 假保护 | `TestEmbeddingServiceImpl` wire-level 语义用例；物化/装箱放大轴无内存断言（测试本就不观测堆） |
| C5-006 | nop-ai-core | 无测试 | grep `DefaultSystemPromptLoader` 于 core 测试源 = 0 命中 |
| C5-007 | nop-ai-core | 无测试 | grep `AiXDefHelper` 于 nop-ai-core 测试源 = 0 命中（仅 nop-ai-coder happy path 引用）；循环引用 SOE 零用例 |
| C5-008 | nop-ai-agent | 假保护 | `TestCheckpointJournalSnapshotFormat`/`TestFileBackedCheckpointManager` 钉格式/roundtrip/水位恢复（:56-58 跨实例检索）；~4× 物化/byWatermark 常驻/journal 磁盘无界零断言 |
| C5-009 | nop-ai-agent | 无测试 | 同 C1-011：`TestDBCheckpointManager` grep remove = 0；gate3 反向钉 `default void remove` marker |
| C5-010 | nop-ai-agent | 假保护 | `TestInSessionCompactionSnapshotArchive` 9 用例钉快照 copy/防御性语义（`putDefensiveCopyArchivedOriginalIsImmutableToLaterMutation`）；无驱逐/无上界零断言 |
| C5-011 | nop-ai-agent | 假保护 | `TestSpillStore.deleteRemovesContentAndIsIdempotent` 钉 delete API 本体；引擎读后不删的调用面缺失零断言（`oversizedToolResultSpilledToSessionStore` 钉 spill 发生，反向固化驻留语义） |
| C5-012 | nop-ai-agent | 无测试 | 同 C3-016：邮箱双表清理 grep = 0 |
| C5-013 | nop-ai-agent | 假保护 | 同 C1-006/C1-014：reset-on-resume 被钉（`TestResumeSessionPostDenialGuardReset`），终态清扫缺失零断言 |
| C5-014 | nop-ai-agent | 假保护 | `TestAgentActor.receivedMessagesRecordsAndReturnsSnapshot` 钉记录/快照行为；无界增长零断言 |
| C5-015 | nop-ai-agent | 假保护 | `TestInMemorySessionStore`/`TestFileBackedSessionStore`/`TestDBSessionStore` 三实现行为测试丰富；终态后清理/淘汰零断言（无 isEmpty-on-terminal 用例） |
| C5-016 | nop-ai-agent | 假保护 | `TestRestorePendingSessions` 8 用例钉过滤/故障隔离（`restorePendingSessions_statusFiltering`）；全表扫描/全量 pin 成本零断言 |
| C5-017 | nop-ai-agent | 假保护 | `TestDeferredAckMailbox` 20 用例：`concurrentOfferAndPollNoLossNoDuplication`（真并发保护核心轮转）、`deadLetterAfterMaxDeliveryAttempts` 钉 DLQ 进位；deadLetter 无界永驻/inFlight 无 stale 回收零断言，且 `unboundedDefaultNeverRejects:104` 反向钉住无界默认（R3） |
| C5-018 | nop-ai-agent | 假保护 | 同 C1-018/C3-024/C4-016 |
| C5-020 | nop-ai-agent | 假保护 | 同 C1-016：并发同实例轴被钉，释放契约缺失零断言 |
| C5-021 | nop-ai-agent | 假保护 | 同 C1-017/C3-023 |
| C5-022 | nop-ai-agent | 假保护 | `TestInMemoryTeamTaskStoreReclaim/Transitions`+`TestDbTeamTaskStore` 钉状态机/reclaim；无删除/全量拓扑重建零断言（opt-in 面，daemon 用例仅显式装配场景） |

### C7 长时运行（32 条）

| ID | 模块 | 判定 | 证据 |
|----|------|------|------|
| C7-001 | nop-ai-core | 无测试 | 聚合期限/静默检测唯一触达点在 @Disabled `TestChatServiceImpl`；`TestStreamAggregator` 无 deadline 断言 |
| C7-002 | nop-ai-core | 无测试 | `TestStreamAggregator` 9 用例无「onComplete 无 finishReason」用例（testAggregator_noUsage 只测 usage 缺省）；半关闭误判成功零断言 |
| C7-003 | nop-ai-core | 假保护 | `TestRetryAfterMessageSource` 7 用例钉四源解析（seconds/minutes/ms）；上限钳制零断言；`TestStandardRetryPolicy` 钉基 delay/max delay 构造校验 |
| C7-004 | nop-ai-core | 假保护 | `TestProviderFailoverQueue` 用注入时钟（:39-57）钉冷却机制——恰证明机制可测而生产接线仍是墙钟；`TestThresholdBreaker` cooldown 转换用真实 sleep；墙钟依赖零断言 |
| C7-005 | nop-ai-core | 假保护 | `TestThresholdBreaker.stuckProbeTimesOutAndSlotIsRetaken`/`probeTimeoutZeroDisablesEscape` 钉逃逸机制本体；probe 身份令牌缺失（陈旧结果污染）零断言 |
| C7-006 | nop-ai-core | 假保护 | `TestAiCommandExecute` 钉重试计数；`TestChatServiceRateLimit`（5 用例）钉入口限流；无退避/重试绕过限流零断言 |
| C7-007 | nop-ai-core | 假保护 | `TestThresholdBreaker.perModelStateIsolated` 钉 per-model 隔离；entries 收缩语义零断言 |
| C7-008 | nop-ai-core | 假保护 | `TestAiChatResponseCacheTtl.testPersisterRoundTripPreservesInvalidState` 钉 roundtrip（含 invalid 态保留——与毒条目不自愈兼容）；主动清扫/自愈零断言 |
| C7-009 | nop-ai-core | 假保护 | 同 C5-003 |
| C7-010 | nop-ai-core | 无测试 | 同 C5-004：LocalFileOperator 零测试触达 |
| C7-011 | nop-ai-core | 假保护 | `TestLlmConfigHelper` 仅做 static secretCache 跨测试清理（:27,:97-119 测试卫生）；负缓存永不过期语义零断言 |
| C7-012 | nop-ai-agent | 假保护 | `TestTakeoverLockHeartbeatRenewal.leaseLostAbortsExecutionAndMarksSessionFailed` 钉 `tryRenew==false → abort`（javadoc :64-79 明示窄契约）；续期抛 RuntimeException 无限容忍分支零用例；`TestDbSessionTakeoverLockDualInstanceE2E` 仅钉抢占 |
| C7-013 | nop-ai-agent | 无测试 | 同 C1-011/C5-009 |
| C7-014 | nop-ai-agent | 假保护 | `TestHeartbeatWaitPhase:262` 断言 `chat.calls.get() >= 4`（wakeup 调用发生——正面钉住 wakeup 行为）；wakeup 数量预算/永久入史成本零断言（条件反向钉死 R4） |
| C7-015 | nop-ai-agent | 假保护 | `TestSessionLifecycleLockGate` 仅覆盖 resume×3/wake×3 锁门用例——**无 restore 锁门用例**（测试家族自身镜像 F3 漏修结构）；restore 前置 setStatus 轴零断言 |
| C7-016 | nop-ai-agent | 假保护 | `TestScheduledRecoveryManagerOrphanHandler.endToEndAbortHandlerAbortsOrphanSession` 钉单轮恢复；每 60s 重放无预算零断言 |
| C7-017 | nop-ai-agent | 假保护 | 同 C1-006/C1-014/C5-013：resume-reset 被钉，终态清理缺失零断言 |
| C7-018 | nop-ai-agent | 假保护 | `TestDbSessionTakeoverLock.tryAcquirePreemptsStaleLockAfterTtl` 钉 TTL 抢占（真实时钟 sleep）；墙钟回拨/前跳零断言 |
| C7-019 | nop-ai-agent | 假保护 | 同 C4-008 |
| C7-020 | nop-ai-agent | 假保护 | `TestNoOpSandboxBackend` 钉 timeout+进程树销毁（:71,:94-97）与子进程 pipe drain（:127,:139）；孙进程持管道→reader 泄漏场景零用例；docker 集成仅 happy path |
| C7-021 | nop-ai-agent | 假保护 | `TestPlanExecutorEndToEnd.rollbackPhase_infiniteLoop_hitsCycleSafetyBound` 钉安全界熔断（保护「界存在」）；零退避热循环成本轴零断言（加退避后用例仍绿） |
| C7-022 | nop-ai-agent | 假保护 | `TestTeamTaskFlowOrchestratorOverallTimeout.overallTimeoutCancelsUnderlyingGraph` 钉图取消（缺陷下同样绿）；成员会话 cancelSession 缺失零断言 |
| C7-023 | nop-ai-agent | 假保护 | `TestSpawnStepTimeoutHonestFailure.hangingEngineSpawnStepFailsBoundedlyAndReleasesWorker` 钉有界失败+worker 释放；spawned 会话取消缺失零断言 |
| C7-024 | nop-ai-agent | 假保护 | `TestLocalAgentMessenger.requestTimesOutWhenNoReplyArrives`+`TestCallAgentAsyncEndToEnd` 钉 caller 侧超时；callee 侧继续执行无协调零断言 |
| C7-025 | nop-ai-agent | 假保护 | `TestDBMessageService.consumeLaterRetriesMessage:218-243` 正面钉住 at-least-once 重投（缺陷机制的被保护面）；毒消息尝试上限/DLQ/队首停摆零断言；`TestDBMessageServiceReliability` 钉 sweep（DB 侧兜底，恰反衬 DeferredAck 缺 sweep 无测试） |
| C7-026 | nop-ai-agent | 假保护 | 同 C1-018/C3-024/C4-016：订阅增长 × 轮询 QPS 退化零断言 |
| C7-027 | nop-ai-agent | 假保护 | 同 C1-017/C3-023/C5-021 |
| C7-028 | nop-ai-agent | 假保护 | 同 C5-015/C5-016：终态工作集退出零断言 |
| C7-029 | nop-ai-agent | 假保护 | 同 C3-027/C4-013 |
| C7-030 | nop-ai-agent | 假保护 | 同 C1-016/C5-020 |
| C7-031 | nop-ai-agent | 假保护 | 同 C5-010/C5-011：归档正确性被钉，容量上界零断言 |
| C7-032 | nop-ai-agent | 无测试 | 同 C3-022/C4-010 |

---

## 反向钉死检查

逐条排查 128 条发现对应测试是否断言了缺陷语义本身（修复会打红、须同步改写）。命中 4 项（1 严格行为钉 + 1 源码 marker 钉 + 2 条件钉），另列 4 项 watch-only（注释级/前置条件级机制钉，修复不改断言但须同步注释或扩 fixture）。

**R1（样本掩蔽，C3-031）** `nop-ai-core/src/test/java/io/nop/ai/core/dialect/TestOllamaDialect.java:191-213`——`testParseStreamChunkToolCallsPreserveArguments` 注释自认「P0 回归」，fixture 以 `"content":null` 钉住。C3-031 的空串变体（`"content":""` 恒命中 text 支）恰好被该 fixture 掩蔽：修复（isEmpty 守卫 + 多 entry 产出）不会打红此测试，但**修复必须同批补 `"content":""` 与同事件双 tool_call fixture**，否则回归样本继续只覆盖 null 形态。C3-028 同理：五方言流式 tool_call fixtures 全部单 entry（TestOpenAiDialect:195-214 等），多 entry 契约修复须同步扩样本，现测试在新旧实现下均绿。

**R2（严格源 marker 钉，C1-011/C5-009/C7-013）** `nop-ai-agent/src/test/java/io/nop/ai/agent/gate/TestInvariantGate3EntryPointCleanup.java:87,:136`——gate 表 checkpoint-removal 行钉 `reliability/ICheckpointManager.java` 含 `default void remove` 字面量。该 marker 正是三维发现共同指向的陷阱本体（default no-op）。若修复将 default 改为真实现、或删除 default 强制覆写，此 gate 立即红——**修复 PR 必须同步改写 gate 表**，且 gate 现状实际是在「保护 no-op default 的存在」。

**R3（严格行为钉，C5-017）** `nop-ai-agent/src/test/java/io/nop/ai/agent/message/TestDeferredAckMailbox.java:104`——`unboundedDefaultNeverRejects` 断言默认容量下 offer 永不拒绝。C5-017 修复若将默认改为有界（对齐 capacity 检查语义），此测试立即红，须改写为有界默认下的断言。

**R4（条件行为钉，C7-014）** `nop-ai-agent/src/test/java/io/nop/ai/agent/engine/TestHeartbeatWaitPhase.java:262`——`assertTrue(chat.calls.get() >= 4, "wakeup LLM calls expected")` 正面断言一次长等待发生 ≥4 次 wakeup LLM 调用。C7-014 修复若引入 wakeup 数量预算（默认较小值），此测试在预算 <4 时打红——修复须核对预算默认值与该断言的相容性并同步改写。

**Watch-only（不改断言，须同步注释/机制说明）**：
- `TestAsyncSpawnStepTenantPropagation.java:65-67,:307,:352`——javadoc 三处自称「proves the finally-clear does its job」：C3-026 修复（clear → set(previousTenant)）后断言仍绿，但注释级机制说明失实，须同步。
- `TestTakeoverLockHeartbeatRenewal.java:64-79` javadoc 钉「tryRenew == false DOES abort」窄契约：C7-012 修复扩展异常分支后该表述不完整，须扩文档与用例。
- `TestDockerSandboxBackend.java:412-417` 钉 `--name` flag「so the timeout path can kill it」——只钉 kill 前置条件，不钉 kill 执行；C1-007 修复不受影响，但该注释给人「超时 kill 已被验证」的错觉。
- `TestDefaultAgentEngineCancel.java:229-235` 钉 ghost-session fail-fast 抛异常：C1-010 修复若引入「取消意图登记」（装配窗取消改为挂起意图），ghost 语义可能变化，修复时须复核该断言。

**结论**：反向钉死中无「修复即大面打红」的阻塞项；R2/R3 是修复 PR 的必改点，R1/R4 是修复同批的必扩样本/相容性核对点。

---

## 缺保护登记

> 全部 128 条中会红=0，按 C10 口径全量缺保护。按同案保护面合并登记 19 条（P2），成员与五维复核的跨维指针（C3-001↔C4-002、C3-022↔C4-010↔C7-032、C3-023↔C4-012↔C7-027、C3-024↔C4-016↔C7-026、C7-013↔C5-009↔C1-011 等）对齐。

### [C10-001] 流式发布主链零 active 测试：ChatServiceImpl 直测类整体 @Disabled（LM Studio 门）

- **文件**: `nop-ai/nop-ai-core/src/test/java/io/nop/ai/core/service/TestChatServiceImpl.java:35`（`@Disabled("Requires local LM Studio running at http://localhost:1234")`）
- **证据片段**:
  ```java
  @Disabled("Requires local LM Studio running at http://localhost:1234")
  public class TestChatServiceImpl extends JunitBaseTestCase {
  ```
- **严重程度**: P2
- **并发场景**: callStream 订阅链（HTTP worker/SSE reader 线程 × SubmissionPublisher 回调线程 × 聚合回调线程）
- **触发时序**: 变异推演——对 C3-001/002/003/008/009、C4-001/002、C5-001、C7-001/002 任一缺陷做「修复后回退」，唯一触达 callStream/aggregate 全链的测试类不执行（CI 跳过），其余合同/聚合单测全部保持绿：无任何红点。
- **现状**: 主链取消传播、背压、终止回调次序、晚订阅、执行器承载、publisher 生命周期、聚合无界、期限/半关闭十个缺陷面共享同一个不执行的测试类。
- **风险**: 修复无回归护栏；且 testCallStream:98 自身以 `subscription.request(Long.MAX_VALUE)` 复刻缺陷形态、:136 `TimeUnit.SECONDS.sleep(30)` 时间同步——即使门内执行也不构成保护。
- **建议**: 以 mock `IHttpClient.fetchServerEventFlow` 重写 active 化的 callStream 合同测试（参照 `TestChatServiceImplCallPathContract` 的 stub 模式）；增加取消后 onError/onComplete 必达断言与订阅 request 上限断言。
- **失效面枚举**: 成员 C3-001/C3-002/C3-003/C3-008/C3-009/C4-001/C4-002/C5-001/C7-001/C7-002（10 条，覆盖声明完整）；nop-http-client-jdk 侧 `TestServerEventPublisherStreamFix` 仅钉订阅门控不在本条重复。
- **信心水平**: 确定
- **误报排除**: 非泛泛「缺测试」——该类存在但被 @Disabled，属「测试存在而保护力为零」的最强形态，与 C10 口径的假保护/无测试均有区别，单独立项。
- **复核状态**: 已保留（C10 单轮终局）

### [C10-002] 方言流式协议族：单 entry fixture、零 usage/带内错误断言、空串变体被掩蔽

- **文件**: `nop-ai/nop-ai-core/src/test/java/io/nop/ai/core/dialect/TestOllamaDialect.java:191-213`、`TestOpenAiDialect.java:195-240`、`TestGeminiDialect.java:133-198`、`TestAnthropicDialect.java`（流式段无 error 用例）、`TestResponsesDialect.java:485-495`（唯一 usage 正例）
- **证据片段**:
  ```java
  // TestOllamaDialect:195 —— fixture 钉死 "content":null，空串变体不可达
  String data = "{\"model\":\"qwen3\",\"message\":{\"role\":\"assistant\",\"content\":null," +
          "\"tool_calls\":[{\"id\":\"call_42\", ...
  ```
- **严重程度**: P2
- **并发场景**: SSE 流式解析（单线程解析语义，但缺陷以帧形态触发）
- **触发时序**: 变异推演——C3-028 修复（多 chunk 契约）后回退单 entry 截断：现 fixtures 全部单 entry，测试全绿；C3-031 修复后回退空串抢占：fixture 为 null 不触发；C3-030/032 修复后回退：usage/错误路由无断言可红。
- **现状**: 四条 P1 协议缺陷（截断/usage 失活/空串抢占/错误路由）无一条有会红测试；且 R1 样本掩蔽使 Ollama 回归样本系统性偏窄。
- **风险**: 方言流式族（C3 复核建议的单修复批）落地后无回归护栏；新 provider 接入复制同款解析模式时无测试可拦。
- **建议**: 按 C3-028/030/031/032 修复批同步补参数化 fixture 矩阵：同事件多 entry × 5 方言、`content:""` × Ollama、stream usage × OpenAi/Gemini/Ollama、error 事件 × 5 方言；以真实抓帧样本（C3-031 复核建议）入 corpus。
- **失效面枚举**: 成员 C3-028/C3-030/C3-031/C3-032；正确对照面（Responses usage、Anthropic 事件模型免疫性）已有测试且须保持。
- **信心水平**: 确定
- **误报排除**: 非要求覆盖率为覆盖——四条均为 P1 且修复批已建议，护栏缺位直接决定修复可否安全落地。
- **复核状态**: 已保留

### [C10-003] 引擎终态清理/F4 族：清理断言止于 checkpoint 单件 + Tracking fake，22 条账本/注册表无清理断言

- **文件**: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/engine/TestEngineLifecycleAndCheckpointBounded.java`（completedSession_triggersCheckpointRemove：TrackingCheckpointManager fake）；`gate/TestInvariantGate3EntryPointCleanup.java:76-104`（gate 表 8 行）
- **证据片段**:
  ```java
  // gate3 表：清理清单钉在 4 件——runningExecutions / takeover lock / renewal / checkpoint.remove
  new EntryPoint("checkpoint-removal",
          List.of(EntryPoint.in("engine/DefaultAgentEngine.java", "getCheckpointManager().remove"),
                  EntryPoint.in("reliability/ICheckpointManager.java", "default void remove"))),
  ```
- **严重程度**: P2
- **并发场景**: 执行终局（worker 线程 finally 段）与进程生命周期（close）
- **触发时序**: 变异推演——修复（终态清理挂钩扩清单 + 各容器 remove 接线）后回退：`completedSession_triggersCheckpointRemove` 用的 Tracking fake 只证引擎调用过 `checkpointManager.remove`，DB 后端 no-op 与其余容器不在断言面；gate3 为源码 marker 存在性检查且清单不含 sessionStore/memory/mailbox/replySubscriptions/backgroundRegistry——全部保持绿。
- **现状**: F4 族 22 条成员（见失效面）的「终局无清理」语义在行为层零断言；唯一的清理对称性测试（AR-04/AR-10/AR-09）恰好覆盖了清单里已有的三件。
- **风险**: C5-015 族首修复（终态清理挂钩扩清单）落地后，任何后续重构删掉新增清理项不会被任何测试拦截。
- **建议**: 修复批同步新增 `TerminalCleanupRegistry` 形态的契约测试：每个注册容器实现 `remove(sessionId)` 后，终态执行断言全部容器 isEmpty（可参数化遍历引擎装配的容器清单）；gate3 表同步扩行。
- **失效面枚举**: C1-006/C1-009/C1-011/C1-014/C1-016/C1-017/C1-018、C3-016/C3-023、C4-012/C4-016、C5-009/C5-012/C5-013/C5-015/C5-020/C5-021、C7-013/C7-017/C7-026/C7-027/C7-028/C7-030（22 条；与 C5 复核 F4 族归并清单一致）。
- **信心水平**: 确定
- **误报排除**: 已存在的 TestEngineEntryCleanupSymmetry/gate3 证明项目有清理对称性测试惯例——缺的恰是新清单成员，属同惯例下的可执行缺口。
- **复核状态**: 已保留

### [C10-004] 会话存储并发装载/自愈/租户缓存旁路：三实现行为测试全单线程

- **文件**: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/session/TestFileBackedSessionStore.java`（30 用例）、`TestDBSessionStore.java`、`TestSessionStoreIntraExecutionPersistence.java`（journal replay roundtrip）
- **证据片段**:
  ```java
  // TestFileBackedSessionStore:237 —— 最接近的"损坏"用例是整文件 corrupt JSON fail-fast，
  // 无 torn-tail journal → 误 heal 删除已登记 delta 的任何用例
  void corruptJsonFailsFastWithNopAiAgentException() throws Exception {
  ```
- **严重程度**: P2
- **并发场景**: 缓存未命中装载线程 × save（APPEND 写 journal）线程；租户模式多实例
- **触发时序**: 变异推演——C1-012/C1-021 修复（per-session 装载锁）后回退：roundtrip/replay/heal-happy 语义不变，全绿；C1-020 修复（租户键缓存）后回退：isolation 断言（TestMultiTenantDbIsolation）仍绿。
- **现状**: 装载×保存交叠、误 heal 删 delta、persistedStates 陈旧覆盖、租户 last-writer-wins 丢注入零用例。
- **风险**: C1-021（P2 修复顺位首位）落地后无并发回归护栏；heal 路径（删文件）无任何测试触达即上生产语义。
- **建议**: 两线程 + latch 构造「装载读 journal × 并发 APPEND」交错用例；heal 分支单独用例断言「torn tail 只删尾部、已完整行保留」；租户模式双 store 实例并发 injectInput + 终局 save 后断言注入不丢。
- **失效面枚举**: C1-012/C1-020/C1-021/C5-016；兄弟 FileBackedCheckpointManager/DBCheckpointManager 同族装载竞态归 C1-012 成员。
- **信心水平**: 确定
- **误报排除**: 存储层是该模块测试密度最高的包（20 个引用文件），缺口是时序轴而非覆盖懒惰。
- **复核状态**: 已保留

### [C10-005] 引擎执行窗口并发面：injectInput 交错、装配窗取消、恢复前置状态变更零并发用例

- **文件**: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/engine/TestSteeringInjection.java`（9 用例，Thread.sleep 协调）、`TestInjectionSemanticsTrichotomy.java:195-221`、`TestSessionLifecycleLockGate.java`（仅 resume/wake 锁门）
- **证据片段**:
  ```java
  // TestSessionLifecycleLockGate 用例清单：resume×3 + wake×3，无 restore 锁门用例
  void resumeSession_lockHeldByAnother_keepsSessionPausedAndEvidenceIntact() { ... }
  void wakeSession_lockHeldByAnother_keepsSessionWaitingAndConditionUnsatisfied() { ... }
  // （无 restoreSession_lockHeldByAnother_* —— 与 C7-015 的 F3 漏修结构同构）
  ```
- **严重程度**: P2
- **并发场景**: 注入线程 × ReAct worker × 恢复路径（resume/wake/restore）；取消线程 × 装配段
- **触发时序**: 变异推演——C1-008/C3-013/C3-014 修复（三恢复路径注册 injectionTargets + 容器同步）后回退：现有用例全部单线程编排（sleep 保证次序），staging/live 判定走既有分支，全绿；C1-010/C3-010 修复后回退：ghost fail-fast 与 save-failure 用例语义未变，全绿；C7-015 修复后回退：无 restore 锁门用例可红。
- **现状**: 五维 P1 中最高风险面（C1-008）的触发时序在测试面完全不存在；恢复路径的测试只覆盖「串行恢复成功」。
- **风险**: C1-008 修复（runningInjectionTargets 注册 + pendingInjections 同步容器）无护栏；且 steering 测试用 `Thread.sleep(300)/(500)` 协调（engine/TestSteeringInjection.java:433,:459），本身平台性脆弱。
- **建议**: latch 构造「execute 返回前 injectInput」「resume 进行中 injectInput」「restore 锁被他人持有」三组确定性交错用例；恢复路径参数化遍历 resume/wake/restore 三兄弟断言注入目标注册一致（F3 防漏修）。
- **失效面枚举**: C1-008/C1-010/C3-010/C3-013/C3-014/C7-015（6 条；C3-014↔C1 同站点双登记按同案合并）。
- **信心水平**: 确定
- **误报排除**: TestDefaultAgentEngineConcurrencyGuard 存在（双 execute 互斥有 latch 用例），证明引擎并发测试可行——缺的是注入/取消/恢复轴。
- **复核状态**: 已保留

### [C10-006] 取消传播与幽灵执行族：超时「会触发」被钉、「触发后底层是否停」零断言

- **文件**: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/engine/TestSingleTurnExecutor.java`（testHangingChatServiceTimesOutAndFails）、`engine/TestHeartbeatWaitPhase.java`（abandonCancelsPendingWithSyntheticResult）、`nop-ai-core/.../TestEmbeddingServiceImpl.java`（ICancelToken 仅 stub 签名 :258）
- **证据片段**:
  ```java
  // 超时用例只断言 future 按期失败完成，不断言挂死的底层调用是否被取消/中断
  void testHangingChatServiceTimesOutAndFails() throws Exception { ... }
  ```
- **严重程度**: P2
- **并发场景**: 调用方超时/取消线程 × 底层 HTTP/工具进程执行线程
- **触发时序**: 变异推演——C3-011/C3-012/C4-005/C7-024 修复（取消传播）后回退：future 层超时语义不变，全绿；C3-007 修复（接线 requestTimeout）后回退：grep 零触达。
- **现状**: 「orTimeout/cancel 到点」有测试，「到点之后底层幽灵是否终止」零断言——恰好是四条缺陷的共同语义；embedding 链取消通道与 requestTimeout 死契约完全无触达。
- **风险**: 引擎取消传播批（C3 复核修复批 5）落地后无护栏；幽灵工具进程/幽灵 LLM 连接在负载下复现无测试可查。
- **建议**: 挂死 stub（latch 阻塞）+ 超时后断言 stub 的「中断/关闭」回调被触达（如 stub 记录 onCancel/onClose 调用）；embedding 链同款。
- **失效面枚举**: C3-004/C3-007/C3-011/C3-012/C4-005/C4-009/C7-024（7 条）。
- **信心水平**: 确定
- **误报排除**: 「有超时测试」不等于「有取消语义测试」——本条明确区分两层，非覆盖率诉求。
- **复核状态**: 已保留

### [C10-007] team-flow/spawn 超时僵尸与池泄漏：正确兄弟（fan-out BOUND）被钉、缺陷路径零断言

- **文件**: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/team/flow/TestMemberFanOutDispatchTimeout.java:46-65`（BOUND 路径 cancelSession 被记录断言）、`TestSpawnStepTimeoutHonestFailure.java`、`TestTeamTaskFlowOrchestratorOverallTimeout.java`、`tool/TestTeamExecuteFlowExecutor.java:759-800`（仅 setter 往返）
- **证据片段**:
  ```java
  // fan-out 正确路径被完整钉住（超时→cancelSession→诚实失败），
  // 单成员/ spawn 路径无 orTimeout/cancelSession 轴无用例
  public CompletableFuture<Void> cancelSession(String sessionId, String reason, boolean forced) { ... }
  ```
- **严重程度**: P2
- **并发场景**: orchestrator 超时回调线程 × 成员 agent 执行线程 × spawn 派发队列
- **触发时序**: 变异推演——C3-025/C4-014/C7-022/C7-023 修复（补 per-member 期限与 cancelSession）后回退：BOUND 用例仍绿（它钉的就是已有防护）、overall-timeout 用例仍绿（graph-cancel 语义未变）；C3-022/C4-010/C7-032 修复（orchestrator close 接线）后回退：grep close/leak 零命中，无红点。
- **现状**: F3 不对称的测试镜像——同一文件族里 fan-out 有超时-取消测试，单成员与 spawn 没有；池泄漏三重登记（C3-022/C4-010/C7-032）类级零泄漏断言。
- **风险**: 僵尸成员执行（P1 分叉登记中）修复后无护栏；泄漏测试缺位使「每调用一个 fixed 池」这类确定性泄漏可以无声累积。
- **建议**: 单成员/spawn 路径参数化复制 fan-out 超时用例（同款记录型 engine 断言 cancelSession 被调）；TeamExecuteFlowExecutor 增加线程计数断言（调用 N 次含 spawn flow 后 `Thread.getAllStackTraces` 中 spawn 池线程数不随 N 增长，或 orchestrator close 后池 terminated）。
- **失效面枚举**: C3-022/C3-025/C4-010/C4-014/C4-015/C7-022/C7-023/C7-032（8 条；含 C4-015 的 stop-discard 轴——`TestSpawnDispatchTimeout` 已钉 drain-after-timeout，缺 stop-during-queue）。
- **信心水平**: 确定
- **误报排除**: 非缺覆盖率——测试家族已验证该模式可测（BOUND 用例），漏的正是 F3 缺陷路径。
- **复核状态**: 已保留

### [C10-008] 周期任务异常绝缘：调度「会跑」被钉、Error 逃逸停摆零注入用例

- **文件**: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/runtime/recovery/TestScheduledRecoveryManagerScheduling.java`（8 用例）、`team/scheduler/TestTeamTaskSchedulerDaemon.java:433-468`、`message/TestDBMessageService.java:280`（closeStopsPollingAndReleasesThread）
- **证据片段**:
  ```java
  // TestScheduledRecoveryManagerScheduling —— 全部用例基于正常 scanOnce 返回，
  // 无一用例向 scanOnceSafe 注入 Error（如 OutOfMemoryError 子类/AssertionError）
  void scheduledTaskRunsScanOnce() throws Exception { ... }
  ```
- **严重程度**: P2
- **并发场景**: 调度器线程（scheduleWithFixedDelay）× 扫描/轮询/续期任务体
- **触发时序**: 变异推演——修复（catch Throwable 或统一绝缘层）后回退为仅捕 RuntimeException：现有用例注入的均为 Exception（SQLException 等），周期任务照常，全绿。
- **现状**: 六条 Error 逃逸缺陷共享同一 JDK 语义点，测试面零触达；该语义完全可测（任务体抛 AssertionError 即可确定性复现停摆）。
- **风险**: WI 2.1 C3-001 平台绝缘层批次纳入 nop-ai 四站点时，模块侧无护栏验证接线正确。
- **建议**: 参数化「任务体第 N 轮抛 AssertionError → 断言第 N+1 轮仍执行」的绝缘层契约测试，四处站点（recovery/renewal/daemon/poller）共用。
- **失效面枚举**: C3-020/C3-027/C4-003/C4-007/C4-013/C7-029（6 条；与平台绝缘层批次对齐）。
- **信心水平**: 确定
- **误报排除**: Error 注入测试在本仓可行（非不可测），且 gate/评审无法替代行为断言。
- **复核状态**: 已保留

### [C10-009] DBMessageService 治理面：投递/sweep/关闭被钉，单线程互饿、毒消息停摆、订阅窃取零用例

- **文件**: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/message/TestDBMessageService.java`（8 用例，含 consumeLaterRetriesMessage:218-243）、`TestDBMessageServiceReliability.java`（8 用例 sweep/SQLException）、`TestDBMessageServiceCrossInstance.java`（4 用例）
- **证据片段**:
  ```java
  // consumeLaterRetriesMessage —— at-least-once 重投被正面钉住（缺陷机制的被保护面），
  // 无尝试上限/DLQ 断言；慢 consumer 阻塞 poller 的互饿场景无用例
  assertTrue(attempts.get() >= 2, "message should be redelivered at least once");
  ```
- **严重程度**: P2
- **并发场景**: 单 poller 线程 × 消费回调（可能阻塞/嵌套委派）× 50ms 轮询
- **触发时序**: 变异推演——C3-021 修复（handler 移出 poller 线程）后回退：投递/sweep 语义不变，全绿；C7-025 修复（maxDeliveryAttempts + DLQ）后回退：`attempts >= 2` 在前 2 次投递内不变，全绿；C1-019 修复（cancel 身份校验）后回退：单订阅者 cancel 用例语义不变，全绿。
- **现状**: 治理四轴（串行度/守护/毒消息/窃取）中 sweep 与 SQLException 传播已被 Reliability 用例保护（DB 兄弟面），缺陷轴全部无测试。
- **风险**: DBMessageService 治理工单（C3 复核修复批 3）四轴一单落地后，仅 sweep 轴有护栏。
- **建议**: 慢 consumer（sleep 阻塞）断言其他 topic 投递延迟上界；≥maxBatch+1 条恒败消息断言队列不停摆且进 DLQ；同 topic 双订阅 cancel 旧订阅断言新订阅仍收消息。
- **失效面枚举**: C1-019/C3-021/C7-025（3 条；CL-040 面随工单吸收）。
- **信心水平**: 确定
- **误报排除**: at-least-once 重投测试不是毒消息保护——它钉的是机制被保护面，本条按语义区分。
- **复核状态**: 已保留

### [C10-010] 租约/接管锁异常容忍与墙钟：false 分支被钉、异常分支与墙钟轴零用例

- **文件**: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/engine/TestTakeoverLockHeartbeatRenewal.java:64-79,314-340`、`runtime/lock/TestDbSessionTakeoverLock.java`（10 用例）、`TestDbSessionTakeoverLockDualInstanceE2E.java`（2 用例）
- **证据片段**:
  ```java
  // javadoc 明示窄契约：tryRenew == false 才 abort；抛 RuntimeException 的分支无用例
  * <li>{@link #leaseLostAbortsExecutionAndMarksSessionFailed} — lease-lost → session failed (Phase 2)</li>
  ```
- **严重程度**: P2
- **并发场景**: 续期调度线程 × DB 异常（非对称分区）× 他实例抢占线程
- **触发时序**: 变异推演——C7-012 修复（异常计失败→超限 abort）后回退：tryRenew 抛错时原实现无限容忍，但现测试从不注入续期异常，全绿；C7-018 修复（单调钟）后回退：双实例用例用真实时钟不触发回拨场景，全绿；C4-008/C7-019 修复（专用池）后回退：续期仍周期发生，全绿。
- **现状**: fencing 协议只测了 happy path 与 false 返回分支；DB 层 10 用例钉单实例语义与 TTL 抢占（真实 sleep）。
- **风险**: 双执行（P1、条件面）修复后无护栏；墙钟轴完全无注入时钟用例（对照：TestProviderFailoverQueue 已示范注入时钟模式）。
- **建议**: tryRenew 抛 SQLException/NopAiAgentException 注入用例断言 N 次后执行被中止；续期/租约判定引入可注入时钟参数并用回拨序列用例（复用 TestProviderFailoverQueue 模式）。
- **失效面枚举**: C4-008/C7-012/C7-018/C7-019（4 条）。
- **信心水平**: 确定
- **误报排除**: 条件面（DB 锁装配）已随 C7 复核披露，测试缺口针对协议分支而非装配。
- **复核状态**: 已保留

### [C10-011] 沙箱进程/reader 清理：命令构造与 NoOp 兄弟销毁被钉、超时 destroy 与孙进程管道零用例

- **文件**: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/security/TestDockerSandboxBackend.java:412-417,:536`（Assumptions 门内 happy path）、`TestNoOpSandboxBackend.java:71-97,:127-139`
- **证据片段**:
  ```java
  // docker 集成门内仅验证 exit 0 happy path；超时 kill 路径无门内用例
  if (!isDockerAvailable() || !isImageCached("alpine:3.19")) { return; }
  ```
- **严重程度**: P2
- **并发场景**: 超时看门线程 × docker CLI/沙箱进程 × reader drain 线程 × 孙进程
- **触发时序**: 变异推演——C1-007 修复（超时 destroy + interrupt 无效补偿）后回退：门内集成用例不含超时场景，单测只验命令构造，全绿；C7-020 修复后回退：孙进程持管道场景无用例。
- **现状**: NoOp 兄弟的 timeout+destroy 有完整用例（保护了正确兄弟），Docker 分叉（F3）与孙进程泄漏轴零覆盖。
- **风险**: 沙箱进程泄漏在 CI（无 docker）下不可见；本地长跑缓慢累积不可察。
- **建议**: 门内（有 docker 时）补「sleep 30 + wallSeconds=1」用例断言容器被 kill（docker ps 不残留同名容器）；NoOp/共享层补孙进程持管道用例断言 reader 线程在超时+缓冲上限后退出。
- **失效面枚举**: C1-007/C7-020（2 条；NoOpSandboxBackend 已护面不计入）。
- **信心水平**: 确定
- **误报排除**: docker-gated 不等于无价值——判定为「会红（门内）但 CI 永不执行」，按口径登记缺保护。
- **复核状态**: 已保留

### [C10-012] 复合操作竞态（配额/贡献注册表/EMA/意图键）：全部单线程顺序断言

- **文件**: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/team/TestTeamManagerQuotaEnforcement.java`（6 用例）、`contribution/TestInMemoryContributionRegistry.java:127-208`、`engine/TestCalibratedTokenEstimator.java`、`conflict/TestInMemoryWriteIntentRegistry.java`
- **证据片段**:
  ```java
  // 配额用例：顺序 addMember/bindMemberSession 断言 enforceQuota 抛出——
  // check-then-act 突破需要并发交织，顺序用例下缺陷不可见
  void inMemoryTeamMembersEnforcedOnAddMember() { ... }
  ```
- **严重程度**: P2
- **并发场景**: 并发 addMember/bindMemberSession（AgentTeamBinder 并发触达）；register/unregister 交叠；多会话 EMA record
- **触发时序**: 变异推演——修复（原子化配额/全局全序 unregister/AtomicDouble 化 EMA）后回退：顺序用例语义不变，全绿。
- **现状**: 四条「检查-然后-行动」缺陷的测试全部为顺序断言；TestInMemoryMemoryStoreProvider 已示范并发用例写法（concurrentGetOrCreate）但未推广到这些站点。
- **风险**: F10 族修复后无护栏；配额语义（enforced fail-fast）在并发下的回归只能靠生产暴露。
- **建议**: CountDownLatch 起跑 barrier + N 线程并发 addMember 断言成功数 ≤ 上限；unregister/register 交错用例断言无「已注册被回删」；EMA 并发 record 后断言因子在合法区间。
- **失效面枚举**: C1-015/C1-023/C1-025/C1-026（4 条）。
- **信心水平**: 确定
- **误报排除**: 并发配额测试可确定性构造（barrier 后 join 计数），非 flaky 诉求。
- **复核状态**: 已保留

### [C10-013] 换装面/静态单例：setter 往返被钉、可见性与生命周期零断言

- **文件**: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/engine/TestEngineConfigAndHelpers.java`（configSetterRoundTrips）、`tool/TestTeamExecuteFlowExecutor.java:759-800`、`nop-ai-core/.../TestEmbeddingServiceImpl.java`（无 setRetryScheduler 触达）、`response/TestXmlResponseParser.java` 等
- **证据片段**:
  ```java
  // setter 往返测试在缺陷（plain 字段）下同样绿——可见性语义对单线程测试不可见
  void configSetterRoundTrips() { ... }
  ```
- **严重程度**: P2（P3 缺陷族的保护缺口，随族记 P2 下沿）
- **并发场景**: 装配/换装线程 × 读线程（scan/poller/worker）
- **触发时序**: 变异推演——volatile 化修复后回退：单线程测试全部不可感知，全绿（该族缺陷本身即「运行期换装面」，测试面无运行期换装场景）。
- **现状**: 六条换装/静态单例缺陷中 setter 往返、默认值、解析行为被钉，跨线程可见性/旧资源 shutdown 零断言；DefaultSystemPromptLoader/7 个 static 持有者的换装轴零触达。
- **风险**: 全家族 volatile 化清扫（C1 复核修复建议）无法回归验证；JIT 优化下偶发可见性失败不可诊断。
- **建议**: 该族以 jcstress/Nullable 化检查不现实，建议最低限度：换装后旧调度器 shutdown 的生命周期断言（C1-004/C4-004）+ 换装面「运行期调用方」的契约测试标记（@Deprecated 警告类测试）。
- **失效面枚举**: C1-001/C1-003/C1-004/C1-005/C1-013/C1-022、C4-004（7 条；C4-004 与 C1-004 同站点）。
- **信心水平**: 很可能（可见性缺陷的测试策略本身低性价比，登记为最低优先护栏）
- **误报排除**: 换装面多为 P3/休眠面，本条不放大严重度，仅登记「修复无法被测试验证」事实。
- **复核状态**: 已保留

### [C10-014] 休眠账本与惰性发布：零断言轴（opt-in/零生产接线面）

- **文件**: `nop-ai/nop-ai-core/src/test/java/io/nop/ai/core/model/TestPromptErrorCodeConversion.java:35`、`nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/team/TestInMemoryTeamTaskStoreReclaim.java`、`fencing/TestFencingTokenService.java`
- **证据片段**:
  ```java
  // PromptOutputModel 唯一测试触达点是错误码转换，惰性发布路径零断言
  PromptOutputModel output = new PromptOutputModel();
  ```
- **严重程度**: P2（成员多为 P3 缺陷，保护缺口随族登记）
- **并发场景**: 多会话共享 JVM 内 singleton 容器（休眠面）
- **触发时序**: 变异推演——修复（补删除 API/去 memoization）后回退：无任何断言可红（成员缺陷当前也未被任何测试感知）。
- **现状**: 四条休眠/opt-in 面缺陷（PromptOutputModel 发布、TeamTaskStore 增长、FencingToken 计数器、team daemon 重建）零保护；C1-024/C5-022 的 reclaim 用例只护状态机不护规模。
- **风险**: 激活条款触发（生产接线）时缺陷与缺保护同时生效；登记为修复批次外.watch。
- **建议**: 若实施 F4 统一清理策略（Phase 8），为每个自建容器补「N 次写入 + 全部终态 → size 收敛」参数化边界用例（小规模模拟，符合 C10 正文步骤 5）。
- **失效面枚举**: C1-002/C1-024/C1-027/C5-022（4 条）。
- **信心水平**: 确定
- **误报排除**: 休眠面不豁免登记——激活即回 P1/P2 的成员（C5-022 附激活条款）在激活时需要现成护栏。
- **复核状态**: 已保留

### [C10-015] 磁盘无界四件套：TTL 过期被钉、容量/轮转/保留期零断言，文件操作符类级零测试

- **文件**: `nop-ai/nop-ai-core/src/test/java/io/nop/ai/core/persist/TestAiChatResponseCacheTtl.java`（4 用例）、`service/TestChatLogHelper.java`+`TestChatLoggerRedaction.java`、（LocalFileOperator：全 nop-ai 测试源 0 命中）
- **证据片段**:
  ```java
  // 唯一的"过期"用例钉 TTL>0 行为；默认 TTL=0/无容量/无轮转轴零断言
  public void testTtlExpiryDeletesEntry() throws Exception { ... }
  ```
- **严重程度**: P2
- **并发场景**: 长时运行磁盘累积（单线程写路径即可触发）
- **触发时序**: 变异推演——修复（容量上界/轮转/保留期/默认 TTL）后回退：roundtrip/TTL>0/内容脱敏断言全部不变，全绿；文件操作符切片/分页契约（limit 语义）零测试可红。
- **现状**: 缓存/日志/负缓存/提示词缓存的「无界」轴与 LocalFileOperator 整类零保护；C7-010 的 limit 契约被实现违反却无契约测试兜底。
- **风险**: 磁盘面缺陷在生产以周/月尺度暴露，修复后同样无回归护栏；文件工具 limit 语义可被无声破坏。
- **建议**: LocalFileOperator 补契约测试（offset/limit 读取量断言：以远大于 limit 的文件断言读取字节数上界）；缓存/日志补「写入 N 条 → 目录条目数 ≤ 上界」边界用例。
- **失效面枚举**: C5-002/C5-003/C5-004/C5-006/C7-008/C7-009/C7-010/C7-011（8 条）。
- **信心水平**: 确定
- **误报排除**: LocalFileOperator 是线上可控文件工具，limit 契约测试属单元测试基线而非大规模压测。
- **复核状态**: 已保留

### [C10-016] 心跳/等待治理：wakeup 行为被正面钉住、预算与陈旧唤醒轴零断言

- **文件**: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/engine/TestHeartbeatWaitPhase.java:262`（`chat.calls.get() >= 4`）、`reliability/TestDefaultWaitCoordinator.java`（7 用例）、`engine/TestWaitForWakeEndToEnd.java`
- **证据片段**:
  ```java
  assertTrue(chat.calls.get() >= 4, "wakeup LLM calls expected: " + chat.calls.get());
  ```
- **严重程度**: P2
- **并发场景**: 心跳 wakeup 轮（LLM 调用线程）× deliverWake 调度任务
- **触发时序**: 变异推演——C7-014 修复（wakeup 预算）后回退：预算内行为与 `>=4` 断言冲突点见反向钉死 R4；C3-017 修复（deliverWake 随消费取消）后回退：单次等待语义用例不变，全绿。
- **现状**: wakeup「会发生」被钉、发生多少次无界未断言；陈旧 deadline 任务提前满足后续等待（语义击穿）零用例。
- **风险**: 长工具调用的 LLM 成本放大修复后需人工核对测试相容性（见 R4）；等待语义击穿可复现（两次串行 wait 同 session）却无用例。
- **建议**: 两次串行 requestWait 用例断言第一次的 TIMEOUT deliverWake 不满足第二次；预算用例与 R4 改写同批。
- **失效面枚举**: C7-014/C3-017（2 条；CL-066 面随修复吸收）。
- **信心水平**: 确定
- **误报排除**: 两次串行 wait 的陈旧唤醒可确定性复现，非理论构造。
- **复核状态**: 已保留

### [C10-017] 会话内归档与 journal 物化：copy/格式语义被钉、容量上界零断言

- **文件**: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/session/TestInSessionCompactionSnapshotArchive.java`（9 用例）、`compact/TestSpillStore.java`（9 用例，含 deleteRemovesContentAndIsIdempotent）、`reliability/TestCheckpointJournalSnapshotFormat.java`
- **证据片段**:
  ```java
  // delete API 有测试（机制在）——但引擎读后不删的调用面缺失无断言
  void deleteRemovesContentAndIsIdempotent() { ... }
  ```
- **严重程度**: P2
- **并发场景**: 长会话多次压缩/spill（单会话内累积）
- **触发时序**: 变异推演——修复（归档驱逐/journal 轮转/读后删）后回退：copy/格式/delete 机制断言不变，全绿。
- **现状**: C5-010/C5-011/C7-031 的无上界轴与 C5-008 的 ~4× 物化轴全部零断言；spill 的「delete 存在但无人调用」是纯调用面缺口。
- **风险**: 长会话 OOM（P1×2）修复后无护栏；journal 磁盘无界以天尺度暴露。
- **建议**: 引擎级集成用例：触发 K 次压缩后断言归档条目 ≤ 配置上界；spill 读取消费后断言 store size 归零；journal 折叠后断言文件数/大小上界。
- **失效面枚举**: C5-008/C5-010/C5-011/C7-031（4 条；C7-031 子面拆分随 C5-010/011 归并）。
- **信心水平**: 确定
- **误报排除**: 小规模参数化模拟（K 次压缩）即可断言上界，符合 C10 正文「不要求真实大规模压测」。
- **复核状态**: 已保留

### [C10-018] 可靠性策略面（breaker/重试风暴/Retry-After/恢复预算）：机制被钉、上界与预算零断言

- **文件**: `nop-ai/nop-ai-core/src/test/java/io/nop/ai/core/reliability/TestThresholdBreaker.java`（22 用例，含并发 2 例）、`TestStandardRetryPolicy.java`、`dialect/TestRetryAfterMessageSource.java`；`nop-ai-agent/.../TestPlanExecutorEndToEnd.java`（cycle bound）、`runtime/recovery/TestScheduledRecoveryManagerOrphanHandler.java`
- **证据片段**:
  ```java
  // breaker 并发测试保护原语正确性；probe 逃逸"无身份令牌"与 entries 不收缩零断言
  void stuckProbeTimesOutAndSlotIsRetaken() throws InterruptedException { ... }
  void concurrentAllowCallAdmitsAtMostOneHalfOpenProbe() throws InterruptedException { ... }
  ```
- **严重程度**: P2
- **并发场景**: 多线程故障注入（breaker 已有真并发测试）；重试调度线程
- **触发时序**: 变异推演——C7-005 修复（probe 身份令牌）后回退：逃逸机制用例不变，全绿；C7-003 修复（Retry-After 钳制）后回退：解析用例不含超大值，全绿；C7-021 修复（失败退避）后回退：cycle bound 用例仍到达熔断，全绿。
- **现状**: nop-ai-core 最优质测试资产（22 用例含 2 真并发）恰好停在「原语正确」，策略上界/身份/预算轴零断言。
- **风险**: 重试风暴类缺陷在故障演练/上游故障时暴露；修复后无法回归。
- **建议**: Retry-After 上界断言（构造 1e9 秒响应断言被钳到 maxDelay）；breaker entries 收缩断言（N 个 model key 失败后 entries.size 有界）；orphan 恢复预算断言（连续失败 M 次后升级）。
- **失效面枚举**: C7-003/C7-004/C7-005/C7-006/C7-007/C7-016/C7-021（7 条）。
- **信心水平**: 确定
- **误报排除**: 本条不动原语级并发测试的判定（它们是有效保护面），缺的是策略参数边界。
- **复核状态**: 已保留

### [C10-019] 引擎杂项并发轴（信封逃逸/缓存不对称/租户/fire-and-forget/审计中止/池承载/物化/邮箱死锁轴）

- **文件**: 按成员分散：`TestChatServiceImplErrorResponse.java`（错误映射相邻）、`TestAuditLoggerDefault.java`（默认行为）、`TestAgentActor.java:receivedMessagesRecordsAndReturnsSnapshot`、`TestDeferredAckMailbox.java`（核心轮转有真并发）、`TestCallAgentDelegationDepthGuard.java`（深度守卫）
- **证据片段**:
  ```java
  // C3-005 的 returnExceptionAsResponse 在全部测试源 grep = 0 —— 包络逃逸轴无契约测试
  ```
- **严重程度**: P2
- **并发场景**: 各成员自身并发场景（见五维条目）
- **触发时序**: 变异推演——各成员修复后回退均无红点：信封逃逸（零触达）、缓存 save 失败（零失败注入）、晚订阅（唯一触达 @Disabled）、cancelSession 租户（零触达）、fire-and-forget（同步 outcome 断言不可见）、审计中止（零失败注入）、同池嵌套（深度守卫测试不涉池）、AgentDispatchExecutor（类级零引用）、物化/装箱（无内存断言）、AiXDefHelper 递归（零触达）、邮箱 core 并发正确但死锁/无界轴零断言。
- **现状**: 16 条低聚合度缺陷各缺 1-2 个针对性用例；其中 C5-017 的邮箱有本模块最好的并发测试（ concurrentOfferAndPollNoLossNoDuplication），缺陷轴（deadLetter 无界/R3 钉死项）在旁。
- **风险**: 单点修复各自无护栏；随修复批次逐条补齐即可，无跨批依赖。
- **建议**: 按修复 PR 逐条附护栏用例：信封契约测试（同步抛出断言走 returnExceptionAsResponse）、persister 失败注入、租户 save-restore 对称参数化（resume/wake/cancel/fan-out 四兄弟遍历）、审计 logger 抛异常断言主流程不中止、AgentDispatchExecutor 装配断言（专用池非 commonPool）。
- **失效面枚举**: C3-005/C3-006/C3-008/C3-009/C3-015/C3-018/C3-019/C3-026/C3-029/C4-006/C4-011/C5-005/C5-007/C5-014/C5-017/C5-018（16 条）。
- **信心水平**: 确定
- **误报排除**: 聚合登记不稀释单点——每成员在修复 PR 层面仍需独立用例，本条是其登记容器。
- **复核状态**: 已保留

---

## 系统性观察

**S1（最重要）流式主链的「测试存在性幻觉」**：nop-ai-core 对 ChatServiceImpl（10 条缺陷的共同载体）只有三类触达——整类 `@Disabled` 的直测（TestChatServiceImpl，需本地 LM Studio）、不可观测执行器/生命周期的接线合同测试、以及聚合器/方言的纯函数单测。三层叠加造成「该类有测试」的直觉，实际对取消传播、背压、publisher 生命周期、期限、半关闭五个 P1 轴的保护力为零。且唯一集成用例自身以 `request(Long.MAX_VALUE)` + `sleep(30)` 复刻缺陷形态。mock fetchServerEventFlow 的 active 化改造是本模块测试保护力的第一优先投资。

**S2 引擎编排层并发测试真空与 Thread.sleep 依赖**：原语层有真并发测试（TestThresholdBreaker 2 例、TestDeferredAckMailbox 1 例、TestInMemoryMemoryStoreProvider 1 例、TestDefaultAgentEngineConcurrencyGuard latch 模式），证明团队具备并发测试能力；但五维 P1 集中的编排交错面（resume/wake/restore×inject、装载×save、cancel×装配窗、超时×底层取消、fan-out×spawn）零并发用例。同时 nop-ai 测试面有 41 个文件、66 处 `Thread.sleep` 时序协调（TestSteeringInjection:433/459、TestInjectionSemanticsTrichotomy:179 等）——平台性脆弱，慢 CI 下偶发假红会进一步侵蚀测试可信度。

**S3 不变式门钉住了「不完整清单」本身**：`TestInvariantGate3EntryPointCleanup` 与 `TestEngineEntryCleanupSymmetry` 是优秀的防倒退资产，但其清理清单恰好止于缺陷发生前的四件（runningExecutions/takeover lock/renewal/checkpoint），F4 族 22 条缺陷全部在清单之外；gate 还以源码 marker 方式钉住 `default void remove`（陷阱本体）。不变式门提供的是「防删除」而非「防缺失」——扩清单修复必须与扩 gate 表同批，否则门成为修复的阻力点（R2）。

**S4 测试家族自身镜像 F3 漏修结构**：TestSessionLifecycleLockGate 覆盖 resume×3 + wake×3，独缺 restore 锁门用例——与 C7-015「restore 是 round-4 修复的 F3 漏修兄弟」精确同构；TestMemberFanOutDispatchTimeout 钉住 fan-out BOUND 的超时-取消，单成员/spawn 路径无用例——与 C3-025/C4-014/C7-022/023 的 F3 分叉同构；TestNoOpSandboxBackend 钉 timeout+destroy，Docker 兄弟缺失同款用例——与 C1-007 的分叉同构。**「为修复过的兄弟写测试、漏修兄弟无测试」是本模块反复出现的模式**：修复批次应强制「参数化遍历全部同族入口」的测试形态，而非逐案补用例。

**S5 docker/LM Studio 条件门静默跳过**：TestDockerSandboxBackend:536（`isDockerAvailable()` 不满足即 return）与 TestChatServiceImpl（@Disabled）在 CI 恒绿。C1-007/C7-020 的沙箱清理轴在无 docker 的 CI 上零执行。建议条件门测试在 CI 报告中输出 skipped 明细（现有 JUnit 触达即可），并将「门内用例数」纳入模块测试资产台账。

**S6 泄漏检测钩子存在但覆盖面冻结在旧清单**：`close_shutsDownSelfCreatedPools`（引擎自建池）、`completedSession_triggersCheckpointRemove`（AR-10）、AR-04 对称清理三件套——三个既有钩子证明「关闭后断言空/释放」的测试形态已是项目惯例（C10 正文步骤 4 要求的形态），但无一延伸到 session store、memory provider、mailbox 双表、reply 订阅、spawn 池、background registry。补齐成本低（复用 Tracking fake 模式），收益覆盖 22 条 F4 族。

---

## 终止判定

C10 为单轮维度（`concurrency-resource-audit-prompt.md` 附录 B 第 3 条：C10 放最后批次，依赖前序发现清单，无独立深挖轮次）。本轮完成 128 条全量变异推演与反向钉死排查，显式声明**单轮终止，不设 round 2**。

统计汇总：

| 指标 | 数值 |
|------|------|
| 保护力矩阵 | 128 行（会红·有保护 ×0 / 假保护 ×103 / 无测试 ×25） |
| P1×30 分布 | 假保护 ×22 / 无测试 ×8（C3-002、C3-015、C3-016、C4-011、C7-001、C7-002、C7-013、C7-032） |
| 反向钉死命中 | 4（R1 样本掩蔽 / R2 源 marker 严格钉 / R3 行为严格钉 / R4 条件钉）+ watch-only ×4 |
| 缺保护登记 | 19 条（C10-001…019，全部 P2，覆盖 128 条成员） |
| 系统性观察 | 6 项（S1–S6），首要为 S1 流式主链测试存在性幻觉 |

---

## C10 复核结论

> 复核人：C10 维度独立复核员（WI 3.1）。日期：2026-10-20。Status: closed。
> 复核方式：五维 `## 最终保留项` 全量对账 + 矩阵行抽查（无测试 grep 复现 10 组 / 假保护载体核实 16 条，均超配额）+ R1–R4 及 watch-only 全 8 项对照断言原文 + 19 条登记格式与覆盖闭合程序化校验。本节为追加节，不改动上文任何原内容；下述修正项以本节裁定为准。

### 1. 对账结果（矩阵完整性）

- 128 行 ↔ 五维最终保留清单逐条对账**完全一致**：C1×27 + C3×32 + C4×16 + C5×21 + C7×32 = 128，无遗漏、无多记、无重复行。
- C5-019（C5 复核已驳回）确认**不在矩阵** ✓。
- 严重度总量复算一致：P1×30 / P2×76 / P3×22，P0×0。
- 判定分布复算一致：会红·有保护 ×0 / 假保护 ×103 / 无测试 ×25 ✓。
- **对账差异数：0**（矩阵层）；但 P1 子分布统计行有误，见修正项 M1。

### 2. 抽查结果

**无测试判定 grep 复现（10 组，全部 0 命中，判定维持）**：C4-011（`AgentDispatchExecutor`）、C5-004（`LocalFileOperator|IFileOperator`）、C3-007（`requestTimeout`）、C1-009（`runningInjectionTargets`）、C5-006（`DefaultSystemPromptLoader` 于 core）、C5-007（`AiXDefHelper` 于 core）、C1-011/C5-009/C7-013（`TestDBCheckpointManager` 内 `remove`）、C1-002（`PromptOutputModel` 仅 `TestPromptErrorCodeConversion` 触达）、C3-002（`TestStreamAggregator` 9 用例无 request/背压轴）、C3-016/C5-012（`sessionMailboxes` 于 agent 测试源）。

**假保护判定载体核实（16 条，全部找到所引测试与断言面，判定维持）**：C1-005（11 用例）、C1-007（:412-417 `--name` 钉 kill 前置、:356 退出码分类、:536 起 docker 门内 happy path）、C1-013（`close_shutsDownSelfCreatedPools`:84）、C1-017（10 用例、:53 `statuses.size()==1`）、C1-021（`corruptJsonFailsFastWithNopAiAgentException`:237）、C3-004（`ICancelToken` 仅 stub 签名 :258/:268/:274）、C3-012（`testHangingChatServiceTimesOutAndFails`:352）、C3-021/C7-025（`consumeLaterRetriesMessage`:219、:242 `attempts >= 2` 断言原文一致；Reliability sweep :240）、C3-025（:65 cancelSession 记录型断言）、C3-026/C3-030/C3-031（方言 fixture 与 usage 断言逐行核实）、C4-014（`dispatchClaimedTaskCompletedSynchronouslyForSingleBoundTarget`:130）、C5-002（TTL 两用例 :86/:117）、C7-004（注入时钟 :39-57）、C7-005/C7-007/C10-018（breaker 用例逐条命中）、C7-015（`TestSessionLifecycleLockGate` 7 用例含 resume×3、`restoreSession` 全文 0 命中——独缺 restore 结构确认）、C7-016（:96）、C7-021（:447 cycle bound）。

**假保护 vs 无测试边界抽查结论：0 误判**。docker/`@Disabled` gating 判定核实：`TestChatServiceImpl` 整类 @Disabled（:23-24）、docker 门为 guard-return 跳过——CI 恒绿口径成立。

**反向钉死 R1–R4 对照断言原文（4/4 方向一致）**：
- R1 ✓ `TestOllamaDialect.java:191-213`「P0 回归」注释 :192、fixture `"content":null` :195，空串变体被掩蔽论证成立。
- R2 ✓ `TestInvariantGate3EntryPointCleanup.java:87` 与 `:136` 均字面钉住 `"default void remove"`（本复核逐字确认）。
- R3 ✓ `unboundedDefaultNeverRejects` 断言默认容量 1000 次 offer 全成功——方向正确；**引用行号修正：实际 :134-141（方法 :135），原文写 :104**（:104 是相邻的 `boundedCapacityOfferFullReturnsFalse`）。
- R4 ✓ `TestHeartbeatWaitPhase.java:262` `assertTrue(chat.calls.get() >= 4, ...)` 逐字一致。
- watch-only 4 项：3 项核实（`TestTakeoverLockHeartbeatRenewal` javadoc「DOES abort」、`TestDockerSandboxBackend:412-417`、`TestDefaultAgentEngineCancel:229-235` ghost fail-fast）；1 项路径笔误（见 M5）。

### 3. 修正项（不改变任何矩阵行三态判定，均为统计/引用/成员清单级修正）

- **M1（P1 子分布统计行错误，2 处：判定分布行与终止判定表）**：原文「P1×30 全部落入假保护 22 + 无测试 8（C3-002、C3-015、C3-016、C4-011、C7-001、C7-002、C7-013、C7-032）」与五维最终保留项的严重度分配不符——C3-015（P2）、C7-013（P2 降档）、C7-032（P2 分叉登记）均非 P1，且漏列 P1 的 C3-022、C4-010。**修正为：P1 无测试 ×7（C3-002、C3-016、C3-022、C4-010、C4-011、C7-001、C7-002）/ P1 假保护 ×23**。总量 103/25 不受影响（按 ID 逐行复算核实）。
- **M2（覆盖闭合缺口：C3-024 零归属）**：C3-024（replySubscriptions×DB 轮询，P1）未出现在任何登记的失效面枚举中，而其同站点兄弟 C4-016/C7-026（C10-003）、C5-018（C10-019）、C1-018（C10-003）均已登记。**修正：C3-024 主归属补入 C10-003**（与登记前言自引的「C3-024↔C4-016↔C7-026」指针一致）。
- **M3（唯一主归属违规：C3-008/C3-009 双登记）**：两 ID 同时列于 C10-001（流式主链）与 C10-019（杂项）失效面枚举，违反「每条唯一主归属」。**修正：主归属定 C10-001（晚订阅与执行器承载属 callStream 链面）；C10-019 移除该两成员或改标「次级引用，主归属 C10-001」，成员数 16→14。**
- **M4（C10-003 计数标签不一致）**：失效面枚举实际列 23 条（C1×7/C3×2/C4×2/C5×6/C7×6），标签写「22 条」。**修正：按 M2 补入 C3-024 后重算标签（24 条），或按 M3 口径重列；「与 C5 复核 F4 族归并清单一致」的表述同步校正（C5 清单含 C5-018/C5-022/C1-026/C1-024，与本文枚举为「站点面互换」关系，非逐条相同）。**
- **M5（行号/路径/名称引用漂移 6 处，判定不受影响）**：① R3/C5-017 行 `:104`→实为 `:135`；② C10-001 `TestChatServiceImpl.java:35`→`@Disabled` 实为 `:23`，`testCallStream` `request(Long.MAX_VALUE)` `:98`→实为 `:108`、`sleep(30)` `:136`→实为 `:137`；③ watch-only `TestAsyncSpawnStepTenantPropagation` 路径 `engine/`→实为 `team/flow/`（行锚 :67/:307/:352 核实无误）；④ C7-007 方法名 `perModelStateIsolated`→实为 `perModelStateIsIsolated`（:235）；⑤ C10-011「Assumptions 门内」实为 `if (!isDockerAvailable()...) return;` guard-return（语义等同，措辞校正）；⑥ C10-003 证据片段行号为示意性（gate3 实际锚点 :76-104 区间内 :87 命中）。
- **M6（范围声明数字失实，不影响逐条判定）**：检索面声明「763 个测试文件（core 65 + agent 698）」，实测 core 63 + agent 483（java 文件；加 nop-http-client-jdk 9 ≈ 555）；S2「66 处 Thread.sleep」实测 79 处（41 文件一项准确）。文件数差异不影响结论——本复核按同一检索面对全部抽查条目复现命中/零命中。
- **M7（测试计数小差，方向均为声明偏保守）**：`TestFileBackedSessionStore` 声明 30 用例实测 29 `@Test`、`TestDBCheckpointManager` 声明 22 实测 21、`TestThresholdBreaker` 声明 22 实测 26。计数差 ±1~4 不改变任一判定。

### 4. 判定统计

| 项 | 数值 |
|----|------|
| 矩阵行对账差异 | 0（128↔128，C5-019 正确缺席） |
| 矩阵行抽查 | 26 条（无测试 10 + 假保护 16），误判 0，**修正行 0、维持 128/128** |
| 反向钉死核对 | R1–R4 方向 4/4 一致 + watch-only 3/4 核实、1 项路径笔误（M5-③） |
| 登记条目三态 | **保留 19 / 驳回 0**；格式合规 19/19（12 字段全、正文 16-19 行均 ≥10、编号连续） |
| 登记成员清单需修正 | 2 条（C10-003 补 C3-024；C10-019 去 C3-008/C3-009 双归属），条目本体均保留 |
| 统计/引用修正项 | M1–M7 共 7 项（0 项推翻判定） |

### 5. 最终保留缺保护登记数

**19 条（C10-001…019，全部 P2）维持保留**，为最终登记数；其中 C10-003/C10-019 按 M2/M3 修正成员清单后闭合 128 条唯一主归属（125 条已唯一 + C3-024 补入 + C3-008/C3-009 定主 C10-001）。C10 缺保护结论（会红×0、P1 面全量缺保护、修复批须同步护栏与 gate 表改写）经独立复核**成立**。
