# 278 nop-ai-agent 引擎资源生命周期、恢复与委派边界

> Plan Status: completed
> Last Reviewed: 2026-06-20
> Module: nop-ai-agent
> Work Item: WI-ENGINE-LIFECYCLE
> Source: `ai-dev/audits/2026-06-19-2310-adversarial-review-nop-ai-agent/01-open-findings.md`（AR-02/AR-04/AR-05/AR-09/AR-10）
> Related: 深度审核 14-01（finally 移除 handle 竞态，不同根因互补）、02-02（DefaultAgentEngine God Object）

## Purpose

把 `DefaultAgentEngine` 三个执行入口的清理对称性缺口、人工恢复路径的 reset 不完整、委派递归无深度守卫、引擎/cache 无生命周期终止入口等 confirmed live defect 收口为"注册与清理在对称词法作用域、资源有界、递归有深度上限"的正确语义。

## Current Baseline

- `DefaultAgentEngine`（约 3435 行）是引擎核心，承载 lock/team/message 完整子系统，懒创建 `lockRenewExecutor`（`:~1735-1744`）与 `agentExecutor`（`:~1875-1895`）两个线程池。
- 三个入口 `doExecute`（`:~2266-2310`）、`resumeSession`（`:~2576-2607`）、`restoreSession`（`:~2766-2797`）的注册序列为：`runningExecutions.putIfAbsent`（`:~2225`）→ `tryAcquire` takeover lock（`:~2220`）→ `startLockRenewal` 心跳（`:~2230`）→ `actorRuntime.createActor`（`:~2267`）→ `autoBindTeam`（`:~2273`）→ `try{execute}finally{cleanup}`（cleanup 在 `:~2283`）。`autoBindTeam` 与 `createActor` **都在内层 try 之前**。外层 `catch`（`:~2335`）只捕获 `supplyAsync` 提交失败，不捕获 lambda body 异常。
- **AR-04（confirmed P1，结构）**：`autoBindTeam`（`:~2273`，抛点 `:~3315/3354/3389/3409`）与 `createActor`（`:~2267`）都在内层 try 之前；任一抛出 → 内层 finally 永不进入 → handle/actor/lock/心跳四重泄漏 + sessionId 永久"砖"。
- **AR-02（confirmed P1）**：`resumeSession`（`:~2498`，位于 tenant-scoped try `:~2490-2511` 内）只 `denialLedger.reset(sessionId)`，从不调 `postDenialGuard.reset(sessionId)`；`IPostDenialGuard.reset`（`:~95-100`）Javadoc 自称"human-intervention recovery entry point"，`FingerprintPostDenialGuard.reset`（`:~85`，做 `deniedFingerprints.remove(sessionId)`）实现了却无人调用 → 恢复后同指纹调用被立即拦截，3 轮内再次 pause。
- **AR-05（confirmed P1，事实）**：`CallAgentExecutor.executeSubAgent`（`:~345-399`，递归点 `:~364` 调 `engine.execute`）无深度计数/visited-set/per-session 调用栈；对比 `TeamTaskGraphBuilder.java:129` 对 team blockedBy 有环检测。**关键约束**：`engine.execute(AgentMessageRequest)` 在 `DefaultAgentEngine.java:~2178` 构建全新的 `AgentExecutionContext`（非 `AgentToolExecuteContext`），`AgentToolExecuteContext` 在 ReAct 循环内（`ReActAgentExecutor.java:~1657`）每次迭代从零重建——**因此深度计数不能经由 `AgentToolExecuteContext` 跨 agent 边界传递**。仓内唯一跨 agent 传递通道是 `AgentMessageRequest.metadata`，`ParentPermissionConstraint` 即如此传递（`CallAgentExecutor.java:~336-342` 写 metadata key，`DefaultAgentEngine.java:~2960` 读）。
- **AR-09（confirmed P2）**：`DefaultAgentEngine` 无 `close()`/`shutdown()`/`destroy()`，不实现 `AutoCloseable`；懒创建的池无主、永不终止。`IAgentEngine` 不 extends AutoCloseable。**约束**：仓内有 **~32 个 in-tree test stub 实现 `IAgentEngine`**（RecordingAgentEngine ×~20、StubEngine/ConfigurableFanOutEngine/E2EAgentEngine/ConcurrencyRecordingEngine 等，分布在 team/tool/team-flow/scheduler/runtime-recovery 测试树）——直接让接口 extends AutoCloseable 会破坏全部 stub 编译。
- **AR-10（confirmed P2）**：`FileBackedCheckpointManager`（`:~98-102`）五个 `ConcurrentHashMap` 只写不删、无 LRU/TTL、无 `remove(sessionId)`、类不实现 AutoCloseable。`ICheckpointManager` 现有 4 个实现者（NoOp/ToolExecution/DB/FileBacked），仅 `FileBacked` 是本计划目标；`byWatermark` 增长与全进程累计工具执行次数成正比。**关键约束**：引擎 inner finally（`:~2283/2589/2779`）对所有退出路径（含 `paused`）都触发；而 `paused` 是**非终态**（`isTerminalStatus` `:~2931-2937` = completed/failed/cancelled/forced_stopped/escalated，**不含 paused**），paused 会话必须保留 checkpoint 供 `restoreSession` 恢复。此外 cancel-without-handle 分支（`:~2013`，`session.setStatus(cancelled)`）不进入 inner finally。

## Goals

- 三个执行入口在 `createActor`/`autoBindTeam` 失败时对称释放 handle/actor/lock/心跳，sessionId 可再次执行（AR-04）。
- resumeSession 完整 reset 治理状态（ledger + guard），恢复路径真正可恢复（AR-02）。
- 委派调用有深度上限（经 `AgentMessageRequest.metadata` 跨 agent 传递），循环/自引用返回结构化错误而非栈溢出（AR-05）。
- 引擎有显式生命周期终止入口（`default` no-op 不破坏现有实现者），自创建的池可关闭（AR-09）；checkpoint cache 有界、**仅终态** session 可清理、不破坏 paused 恢复（AR-10）。

## Non-Goals

- 不拆分 `DefaultAgentEngine` God Object（深度审核 02-02，独立计划）。
- 不重构 team/message 子系统架构，仅修清理对称性与生命周期。
- 不处理 ReAct 主循环消息契约（见 Plan 277）。
- 不处理 DB 状态存储 CAS（见 Plan 279）。
- AR-15（fan-out 同步抛孤儿，P3）、AR-18（guard 指纹集无 tenant 维度，P3）作为 watch-only residual 暂留（见 Deferred）。

## Scope

### In Scope

- AR-04：三个入口 createActor+autoBindTeam 清理对称性。
- AR-02：resumeSession 补 postDenialGuard.reset。
- AR-05：CallAgentExecutor 委派递归深度守卫（经 metadata 传递）。
- AR-09：IAgentEngine 生命周期（default no-op close）+ DefaultAgentEngine.close()。
- AR-10：ICheckpointManager.remove（default no-op）+ FileBackedCheckpointManager cache 清理 + 引擎**仅终态**调用。

### Out Of Scope

- God Object 拆分、子系统架构重构。
- DB CAS（Plan 279）、ReAct 消息契约（Plan 277）。
- AR-18 多租户主题（同 guard 区域但属多租户隔离，follow-up）。

## Execution Plan

> Phase 依赖：Phase 1-3 相互独立；Phase 4 与 1-3 独立，但其 `close()` 不得干扰 Phase 1 标准化清理路径（以测试验证）。建议 Phase 2（一行补丁）可优先落地，Phase 1/3/4 并行。

### Phase 1 - 修复引擎入口清理对称性（AR-04）

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java`（`:~2220-2310` doExecute、`:~2542-2607` resumeSession、`:~2738-2797` restoreSession）

- Item Types: `Fix`

- [x] 把 `actorRuntime.createActor`（`:~2267`）**与** `autoBindTeam`（`:~2273`）一起移进现有内层 `try` 块（该 try 的 finally `:~2283-2309` 已通过 `actorRuntime.getActorBySession(sessionId).ifPresent(...)` 销毁 actor），使 createActor 失败与 autoBindTeam 失败都落入对称清理。三个入口（doExecute/resume/restore）统一应用同一模式。**不采用**"仅把 autoBindTeam 移进 try"的不足方案（会遗留 createActor 失败泄漏）。

Exit Criteria:

- [x] 新增测试：声明 team-member 但 team 未 ACTIVE（或 bind 失败），以及 createActor 失败场景，分别调 execute/resume/restore，**在远小于 `lockLeaseMs` 时间窗内**断言：`runningExecutions.containsKey(sessionId)==false`、`actorRuntime.getActorBySession(sessionId).isPresent()==false`、takeover lock 已释放（可立即重新 acquire）、心跳 `ScheduledFuture.isCancelled()==true`。
- [x] repo-observable：同一 sessionId 在失败后可再次成功 execute（不被"session already executing"永久砖）。
- [x] **无静默跳过**：清理 catch 中不吞异常（至少 `LOG.warn(...,e)` 传 throwable），失败显式记录。
- [x] 若改 live baseline：`ai-dev/design/`（引擎入口生命周期，见 Phase 4 命名文档）已更新；否则 `No owner-doc update required`。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 修复恢复路径 reset 完整性（AR-02）

Status: completed
Targets: `DefaultAgentEngine.java`（`:~2498` resumeSession，tenant-scoped try `:~2490-2511` 内）；协作方 `security/IPostDenialGuard.java`、`security/FingerprintPostDenialGuard.java`、`engine/ReActAgentExecutor.java`（`:~1702` pre-dispatch consult、`:~2208` record after deny）

- Item Types: `Fix`

- [x] resumeSession 在 `denialLedger.reset(sessionId)` 旁补 `postDenialGuard.reset(sessionId)`。**放置于 tenant-scoped try 块内、紧随 `denialLedger.reset` 之后**，使未来 tenant-aware guard 实现继承正确的 tenant 上下文。

Exit Criteria:

- [x] 新增集成测试：s1 因 3 次同类 deny 被 pause → resumeSession → ReAct 恢复后下一次**同指纹**调用不被 pre-dispatch 拦截、不在 3 轮内再次 pause。
- [x] repo-observable：resumeSession 调用 `postDenialGuard.reset`（grep 确认）。
- [x] **接线验证**：reset 后 `FingerprintPostDenialGuard` 的指纹集确实被清空（测试断言 `checkBeforeDispatch` 不命中）。
- [x] 若改 live baseline：owner-doc 已更新；否则 `No owner-doc update required`。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - 新增委派递归深度守卫（AR-05）

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/tool/CallAgentExecutor.java`（`:~345-399` executeSubAgent）；`DefaultAgentEngine.java:~2178`（构建 AgentExecutionContext）、`:~2960`（ParentPermissionConstraint 提取点）；传播通道 `AgentMessageRequest.metadata`

- Item Types: `Fix`

- [x] 经 `AgentMessageRequest.metadata` 传递 per-顶层-session 委派深度（**不**经由 `AgentToolExecuteContext`——它在每次 ReAct 迭代从零重建、且 engine 边界构建的是 `AgentExecutionContext`）。**首选扩展 `ParentPermissionConstraint` 增加 `parentDepth` 字段**（复用 `:~336-342` 写、`:~2960` 读的同一通道），因其自动经 messenger 异步路径（`executeViaMessenger` + `CallAgentRequestPayload` + `buildConstraintMetadata`）传播；若改用独立 `DelegationDepth` metadata key，则必须显式扩展 `CallAgentRequestPayload` 否则 mailbox 异步链路无守卫。`CallAgentExecutor.executeSubAgent` 入口读父深度 +1 写入子 request metadata；`DefaultAgentEngine.doExecute` 在 `:~2960` 提取 ParentPermissionConstraint 处一并提取深度并暴露到 `AgentExecutionContext`，**再经 ReAct 构建 `AgentToolExecuteContext`（`:~1657`）时透传**，供子 CallAgentExecutor（从 `AgentToolExecuteContext` 读，`:~201/223/317`）回读。入口检查 `depth >= MAX_DELEGATION_DEPTH` 即返回结构化 errorResult。
- [x] `MAX_DELEGATION_DEPTH`：提供 setter 使其可配置；做一次仓内 team-flow（`SpawnMemberAgentTaskStep`/`MemberAgentTaskStep` 链）最大委派深度审计，保守取默认值（如 4），允许向上覆盖。

Exit Criteria:

- [x] 新增测试：**使用真实 `DefaultAgentEngine`（非 RecordingAgentEngine stub）+ 真实 call-agent 链**，覆盖 self-referencing agent（`agentId="self"`）与 A↔B 互引，断言得到结构化错误结果而非 StackOverflowError；每一层不残留孤儿 session/actor/lock。
- [x] repo-observable：CallAgentExecutor 有 depth 检查与 `MAX_DELEGATION_DEPTH`（可配置）常量；深度经 metadata 沿调用链传递（grep 确认，非经 AgentToolExecuteContext）。
- [x] **无静默跳过**：超深时返回 errorResult（显式失败），不静默返回 null/空。
- [x] 若改 live baseline / public contract：`ai-dev/design/delegation-bounds.md`（MAX_DELEGATION_DEPTH + 传播通道契约）已更新或新建。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 4 - 引擎生命周期与 cache 有界化（AR-09 + AR-10）

Status: completed
Targets: `IAgentEngine.java`；`DefaultAgentEngine.java`（`:~1735-1744`、`:~1875-1895`、`:~2013` cancel-without-handle、`:~2283/2589/2779` inner finally、`:~2931-2937` isTerminalStatus）；`reliability/ICheckpointManager.java`；`reliability/FileBackedCheckpointManager.java`（`:~98-102`）；`session/FileBackedSessionStore.java`（`:~85`）

- Item Types: `Fix`

- [x] AR-09：`IAgentEngine extends AutoCloseable` 并增加 `@Override default void close() throws Exception {}` no-op 默认方法（`extends AutoCloseable` 使 `@Override` 合法且支持 try-with-resources；default no-op 保持 ~32 个 in-tree test stub 与外部实现者源码兼容）；`DefaultAgentEngine.close()` override，仅关闭**自创建**的池（lockRenewExecutor/agentExecutor），不动外部注入池。契约：幂等（二次 close 为 no-op + LOG.debug）；**不**取消在途执行（调用方职责）；遇 `InterruptedException` 恢复中断标志 + LOG.warn + 不重抛。
- [x] AR-10：`ICheckpointManager` 增加 `default void remove(String sessionId) {}` 幂等 no-op 默认方法（NoOp 及未来 stub 继承默认）；`FileBackedCheckpointManager` override 清理五个 cache；`ToolExecutionCheckpoint` override 清理其 in-memory per-session map（与 FileBacked 同为无界增长形态，需清理）；`DBCheckpointManager` 暂用默认 no-op（与 AR-08 后续协同，见 Plan 279）。引擎在 session 终态时调用——**仅当 `isTerminalStatus(session.getStatus())` 为 true 时**调 `checkpointManager.remove(sessionId)`，inner finally（`:~2283/2589/2779`）内加该终态门（**不得**对 `paused` 清理）；并在 cancel-without-handle 分支（`:~2013`）补对称调用。

Exit Criteria:

- [x] 新增测试：构造引擎 → 执行一次 session → close() → 验证自创建池 `isShutdown()==true`；外部注入池未被关闭；二次 close 不抛异常。
- [x] 新增测试：验证 ~32 个 in-tree IAgentEngine test stub 仍编译通过（`./mvnw test-compile` 在依赖 nop-ai-agent 的模块范围内通过）。
- [x] 新增测试：模拟多 session 长跑——**paused** session 保留 checkpoint（`restoreSession` 仍可恢复）、**completed** session 调 remove 后 byWatermark/bySession 不残留、**cancel-without-handle** session（`:~2013`）也被清理；验证 byWatermark 不随累计 session 数无界增长。
- [x] repo-observable：IAgentEngine 有 `default close()`；DefaultAgentEngine 有 close() override；ICheckpointManager 有 `default remove()`；FileBackedCheckpointManager override remove。
- [x] **接线验证**：remove() 仅在终态路径被调用（测试覆盖 paused 保留 + completed/终态清理 + cancel-without-handle 清理）。
- [x] 若改 live baseline / public contract（IAgentEngine.close / ICheckpointManager.remove）：`ai-dev/design/engine-lifecycle.md`（IAgentEngine.close 与 ICheckpointManager.remove 契约）已更新或新建。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [x] AR-04：三个入口 createActor/autoBindTeam 失败时四重资源对称释放、sessionId 可再执行（锁窗内断言）。
- [x] AR-02：resumeSession 完整 reset，恢复后不再 3 轮内 re-pause。
- [x] AR-05：委派递归有可配置深度上限（经 metadata 传递），循环/自引用返回结构化错误（真实引擎测试）。
- [x] AR-09：引擎有 default no-op close()，自创建池可终止，~32 stub 仍编译。
- [x] AR-10：checkpoint cache 仅终态可清理，paused 恢复不被破坏，cancel-without-handle 也清理。
- [x] 不存在被静默降级到 deferred/follow-up 的 in-scope confirmed live defect。
- [x] 受影响 owner docs（引擎生命周期、接口契约）已同步或显式 `No owner-doc update required`。
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。
- [x] **Anti-Hollow Check**：close()/remove() 确实在运行时被调用（不只类型存在）；remove() 仅终态触发；无空方法体/静默跳过/no-op 作为正常实现。
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过；Phase 4 接口变更另跑 `./mvnw test-compile`（覆盖依赖 nop-ai-agent 的模块，确认 stub 兼容）。
- [x] checkstyle / 代码规范检查通过。

## Deferred But Adjudicated

### AR-15（fan-out 构建循环同步抛孤儿 tool future）

- Classification: `watch-only residual`
- Why Not Blocking Closure: P3；触发需 `toolManager.callTool` 同步抛（契约未文档化）；与本计划"引擎资源生命周期"主题相邻但属 ReAct fan-out 取消语义，独立于四重泄漏/递归/池生命周期。
- Successor Required: `yes`
- Successor Path: 独立小计划处理 fan-out future `.cancel(true)` 语义（可与 Plan 277 Phase 1 的 tool 处理合并考量）。

### AR-18（FingerprintPostDenialGuard 指纹集无 tenant 维度）

- Classification: `watch-only residual`
- Why Not Blocking Closure: P3；与 AR-02 同 guard 区域但属多租户隔离主题（与深度审核 13-01 同模式）；触发依赖 sessionId 跨租户复用。Phase 2 已把 reset 放入 tenant-scoped try 为未来 tenant-aware 留好接入点。
- Successor Required: `yes`
- Successor Path: 多租户隔离一致性专题计划（含 13-01、13-03、14-04）。

## Non-Blocking Follow-ups

- 深度审核 02-02 DefaultAgentEngine God Object 渐进拆分（提取 SessionTakeoverLockManager/TeamAutoBinder）。
- 深度审核 14-02 lease-lost 中断打不断 fan-out join（与 AR-15 相关，不同根因）。

## Closure

Status Note: 5 个 in-scope confirmed live defect（AR-02/04/05/09/10）全部修复并经 focused tests + 独立 closure audit 验证。清理对称性、恢复完整性、递归有界、引擎生命周期、cache 有界化五项目标均达成。AR-15/AR-18 作为 P3 watch-only residual 显式裁定移出（见 Deferred But Adjudicated），无非阻塞 in-scope defect 残留。
Completed: 2026-06-20

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，task_id `ses_11b938dd4ffe6lvGKtyNlgedVn`，general subagent，非实现 session）
- Audit Session: `ses_11b938dd4ffe6lvGKtyNlgedVn`（2026-06-20，read-only 对抗性 closure audit）
- Evidence:
  - **Phase 4 Exit Criteria 1-7 全 PASS**：
    - EC1（close 自创建池/不动外部/幂等）：`DefaultAgentEngine.java:3008-3034`（`ownLockRenewExecutor` :3016 / `ownAgentExecutor` :3025 / `closed.compareAndSet` :3010）；测试 `close_shutsDownSelfCreatedPools`/`close_doesNotShutDownExternallyInjectedPools`/`close_isIdempotent`（`TestEngineLifecycleAndCheckpointBounded.java:78/103/132`）。
    - EC2（~32 stub 编译）：`IAgentEngine.java:7` `extends AutoCloseable` + `:208-210` default no-op close；grep `implements IAgentEngine` in src/test = 28 文件（多 stub/文件）；`./mvnw test-compile -pl nop-ai/nop-ai-agent -am` BUILD SUCCESS。
    - EC3（paused 保留/completed 清理/cancel-without-handle 清理/byWatermark 有界）：4 个 `checkpointManager.remove` 调用点（`:2027` cancel-without-handle、`:2326` doExecute、`:2648` resume、`:2849` restore inner finally），三 inner finally 均 `if (isTerminalStatus(...))` 守门；`isTerminalStatus` (:3052-3059) 不含 paused；测试 `completedSession_triggersCheckpointRemove`/`pausedSession_doesNotTriggerCheckpointRemove`/`cancelWithoutHandle_triggersCheckpointRemove`/`multiSession_byWatermarkDoesNotGrowUnbounded`。
    - EC4（repo-observable 契约）：`IAgentEngine.java:7,208-210`、`DefaultAgentEngine.java:3008`、`ICheckpointManager.java:116`、`FileBackedCheckpointManager.java:198-216`、`ToolExecutionCheckpoint.java:89-102`。
    - EC5（接线验证 remove 仅终态）：三 inner finally 守门 + `TrackingCheckpointManager.removeCalledFor` 断言。
    - EC6（design doc）：`ai-dev/design/nop-ai-agent/engine-lifecycle.md` 已新建（IAgentEngine.close 契约 + ICheckpointManager.remove 契约 + terminal-only 调用门，paused 排除）。
    - EC7（daily log）：`ai-dev/logs/2026/06-20.md:1-13` plan-278 条目。
  - **Closure Gates 全 PASS**：
    - AR-04：三入口 createActor+autoBindTeam 在 inner try 内（doExecute :2296/:2302、resume :2626/:2632、restore :2827/:2833）；`TestEngineEntryCleanupSymmetry`（4 tests）覆盖 autoBindTeam-fail + createActor-fail 跨三入口。
    - AR-02：`resumeSession` 调 `denialLedger.reset`(:2532)+`postDenialGuard.reset`(:2542)；`TestResumeSessionPostDenialGuardReset` 用真实 `DefaultPostDenialGuard` 验证同指纹不再被拦、不再 re-pause。
    - AR-05：`CallAgentExecutor.java:173-183` depth 检查 + `DEFAULT_MAX_DELEGATION_DEPTH=4`(:107) + `setMaxDelegationDepth`(:113/121)；经 metadata key `__nopAiAgent.delegationDepth`(:94/:424) 传播，`DefaultAgentEngine.extractDelegationDepth`(:3103-3120)→`ctx.setDelegationDepth`(:2208)→`ReActAgentExecutor`→`AgentToolExecuteContext`(:1725)；`TestCallAgentDelegationDepthGuard`（5 tests）用真实引擎覆盖 self-ref + A↔B。
    - AR-09/AR-10：见 Phase 4 EC1-EC5。
    - No silent downgrade：AR-15/AR-18 显式 `watch-only residual`(P3) + successor path，out of scope（Non-Goals :31-37）。
    - Owner docs：`engine-lifecycle.md` + `delegation-bounds.md` 新建；README/audit-tracker/roadmap 同步。
    - Independent audit：本条（fresh session task_id `ses_11b938dd4ffe6lvGKtyNlgedVn`）。
    - Anti-Hollow：close()/remove() 均有真实方法体（非空），default no-op 为兼容默认（正确），真实逻辑在 override；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码 0（0 findings）。
    - Build/test：`./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` BUILD SUCCESS（全模块全绿，零回归）；`./mvnw test-compile -pl nop-ai/nop-ai-agent -am` BUILD SUCCESS（28 stub 文件编译通过）。
    - Checkstyle：root `pom.xml` 的 maven-checkstyle-plugin block 被注释（:140-162），无活跃自动 checkstyle 门禁；改动文件 import 顺序符合 AGENTS.md 约定（java.*→third-party→io.nop.*）。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0。
  - Anti-Hollow 检查结果：见上（PASS）；`scan-hollow-implementations.mjs` 退出码 0。
  - Deferred 项分类检查：AR-15/AR-18 均为 P3 watch-only residual + 明确 successor path + Why Not Blocking Closure，无非阻塞 in-scope defect 被降级。

Follow-up:

- AR-15（fan-out 同步抛孤儿 tool future，P3）：独立小计划处理 fan-out future `.cancel(true)` 语义（可与 Plan 277 Phase 1 tool 处理合并考量）。
- AR-18（FingerprintPostDenialGuard 指纹集无 tenant 维度，P3）：多租户隔离一致性专题计划（含 13-01、13-03、14-04）。
- 深度审核 02-02 DefaultAgentEngine God Object 渐进拆分（提取 SessionTakeoverLockManager/TeamAutoBinder）。
- 深度审核 14-02 lease-lost 中断打不断 fan-out join（与 AR-15 相关，不同根因）。
