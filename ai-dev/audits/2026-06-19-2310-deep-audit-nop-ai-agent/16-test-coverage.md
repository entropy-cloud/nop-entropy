# 维度 16：测试覆盖与质量

## 检查范围

src/test ~500+ 测试类（JUnit5 + Nop AutoTest，无 Mockito）；核对高风险/核心路径类测试存在性与质量；fixture 覆盖；弱断言识别。

## 总体评估

测试套件**非常全面且设计精良**（完全手写测试替身无 mock 框架）。覆盖 ReAct 循环、并发保护、多租户隔离、跨实例持久化、恢复、安全、Sandbox、DSL fixture。多数 NoOp 测试验证明确契约值（非仅 assertDoesNotThrow），多数 Wiring 测试通过 SQL/哨兵验证端到端行为，DSL fixture 覆盖 path-rules/team/delta/unknown-mode 容错。

## 第 1 轮（初审）发现

### [维度16-01] TestNoOpAuditLogger 仅断言 assertDoesNotThrow，未约束 NoOp 契约

- **文件**: `src/test/java/io/nop/ai/agent/security/TestNoOpAuditLogger.java:7-38`
- **证据片段**:
  ```java
  @Test void testLogDoesNotThrow() { AuditEvent event = new AuditEvent(...); assertDoesNotThrow(() -> logger.log(event)); }
  @Test void testMultipleLogsDoNotThrow() { for (int i=0;i<100;i++) { ... assertDoesNotThrow(() -> logger.log(event)); } }
  ```
- **严重程度**: P3
- **现状**: 模块所有其他 TestNoOp* 验证明确契约值（TestNoOpDenialLedger 断言 count==0/isThresholdExceeded==false；TestNoOpSecurityLevelResolver 断言 STANDARD；多数 assertSame 验证单例）。TestNoOpAuditLogger 仅 assertDoesNotThrow，未验证单例标识，未验证日志静默（无副作用积累/无输出）。
- **风险**: 安全审计日志默认配置是"静默即黄金"契约。回归导致 log() 开始累积事件/写 System.out/非单例时，测试无法捕获。
- **建议**: 加 assertSame(INSTANCE,INSTANCE)；挂 Logback ListAppender 断言调用 100 次后 events.isEmpty()；可选断言实现 IAuditLogger。
- **信心水平**: 高（与 11 个兄弟 TestNoOp* 对比，唯一用 assertDoesNotThrow 作唯一断言形式）
- **误报排除**: TestAuditLoggerDefault 确端到端验证 WARN-on-NoOp；本发现针对单元级契约，比同类弱。
- **复核状态**: 未复核

### [维度16-02] DefaultAgentEngine 并发测试缺少正向"多不同会话并行运行"用例

- **文件**: `src/test/java/io/nop/ai/agent/engine/TestDefaultAgentEngineConcurrencyGuard.java:59-403`
- **证据片段**: 现有方法 concurrentExecuteFailFast/finallyDoesNotMisremoveHandle/cancelWindowHonoreded/restoreSessionGuardConsistentWithExecute/noRegressionNormalPath——均为会话内保护或顺序，无正向多会话并发。
- **严重程度**: P3
- **现状**: 验证了 putIfAbsent 拒绝同会话重复执行+取消窗口，但无正向测试断言 N 个不同 session-id 同时 execute 时全部完成互不阻塞。runningExecutions 是 ConcurrentHashMap（按 session-id 键），executor 是 cachedThreadPool，均设计为允许多会话并行。
- **风险**: 未来重构意外把 runningExecutions 变单槽/换 size-1 固定池/引入跨会话锁，现有套件绿色但生产静默降并发为 1。
- **建议**: 加测试：CountDownLatch 屏障同时提交 4-8 不同 session-id execute（ScriptedChatService 阻塞 50-100ms），断言全部完成+总挂钟时间明显短于顺序和+无"already executing"异常。
- **信心水平**: 中-高
- **误报排除**: team flow 测试（TestAsyncSpawnStepParallelBranches）验证子代理层并发，但非 DefaultAgentEngine.execute 入口；TestPlan271AsyncTimeoutReliability 仅单执行。
- **复核状态**: 未复核

### [维度16-03] TestReActAgentExecutor 仅覆盖执行器层表层场景

- **文件**: `src/test/java/io/nop/ai/agent/engine/TestReActAgentExecutor.java:40-348`（6 方法）
- **证据片段**: testNoToolCallImmediateReturn/testSingleToolCallExecution/testMultipleToolCallsInOneTurn/testMaxIterationsReached/testLlmCallFailure/testToolExecutionError。
- **严重程度**: P3
- **现状**: 直接执行器测试仅基本成功/失败路径。引擎层 E2E（TestEndToEndReAct/TestForcedStop/TestCompactionIntegration 等）确实深入驱动执行器，实际行为覆盖高。但执行器微妙边界（空工具调用列表+非空内容、同消息混合工具调用与最终内容、达 max-iter 仍需工具）在执行器层缺覆盖，依赖引擎 E2E 会导致失败放大定位困难。
- **风险**: 低（引擎 E2E 覆盖行为），风险在诊断工作量。
- **建议**: 加 2-3 执行器层测试：testEmptyToolCallsListIgnored/testToolCallsWithContentAppendsBoth/testCancelTokenHonoredMidLoop。
- **信心水平**: 中
- **误报排除**: TestDefaultAgentEngineCancel/TestForcedStop 确实驱动 ReAct 循环；差距在直接执行器单元深度。
- **复核状态**: 未复核

## 核心路径测试覆盖矩阵

| 核心类/路径 | 有测试 | 强度 | 缺口 |
|---|---|---|---|
| ReActAgentExecutor（主循环/max-iter/LLM/tool failure） | 是 | 中 | 直接测试仅表层（见16-03） |
| DefaultAgentEngine execute/restore/resume/fork | 是 | 强 | 无重大缺口 |
| cancel（graceful/forced/events/lifecycle） | 是 | 强 | 无 |
| 并发保护（putIfAbsent/fail-fast/cancel-window） | 是 | 强 | 缺正向多会话并发（见16-02） |
| 多轮/多租户/fork | 是 | 强 | 无 |
| PathAccessChecker（Default/RuleBased/ParentConstrained/AllowAll） | 是 | 强 | 无 |
| PermissionMatrix/DenialLedger（InMemory/DB/NoOp+CrossInstance） | 是 | 强 | 无 |
| Sandbox（Docker/NoOp/Config/Request） | 是 | 强 | 无 |
| ActorRuntime/SessionTakeoverLock/RecoveryManager/TeamTaskSchedulerDaemon | 是 | 强 | 无 |
| DBSessionStore/DBCheckpointManager/DBDenialLedger/DbTeamManager/DBMessageService | 是 | 强 | 无 |
| DbUsageRecorder | 是 | 中-强 | 缺 CrossInstance 变体（影响小，仅 INSERT） |
| DbDaemonCoordinator/FencingTokenService/TeamTaskFlowOrchestrator | 是 | 强 | 无 |
| DSL fixture（path-rules/team/delta/unknown-mode） | 是 | 强 | 无 |
| NoOp* 默认实现（~20 类） | 是 | 大多很强 | TestNoOpAuditLogger 异类（见16-01） |
| 路径规则继承/Delta/Unknown-mode 容错/多成员 fan-out/并发竞态/超时/多租户隔离/跨实例持久化 | 是 | 强 | 无 |

## 维度复核结论

3 项均为测试质量改进建议（P3），非行为 bug。模块测试整体质量高，核心高风险路径覆盖充分。AutoTest 快照未审计（规则豁免）。

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 16-01 | P3 | src/test/.../security/TestNoOpAuditLogger.java | 仅 assertDoesNotThrow，未约束 NoOp 契约 |
| 16-02 | P3 | src/test/.../engine/TestDefaultAgentEngineConcurrencyGuard.java | 缺正向多会话并发测试 |
| 16-03 | P3 | src/test/.../engine/TestReActAgentExecutor.java | 执行器层仅表层场景 |
