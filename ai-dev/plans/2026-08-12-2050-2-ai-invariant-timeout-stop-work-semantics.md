# 2 AI Invariant Loop — Timeout 语义统一：超时 = 停止工作（SPAWN 分支 / Orchestrator / Channel fan-out）+ 门禁②分支级判定升级

> Plan Status: completed
> Mission: nop-ai-invariant-loop
> Last Reviewed: 2026-08-12
> Source: `ai-dev/audits/2026-08-12-1119-open-audit-nop-ai-invariant-loop.md` [P1][AR-2] / [P1][AR-3] / [P1][AR-4]（open-ended adversarial audit）
> Related: `2026-08-12-1411-2-ai-invariant-i4-fix-execution.md`（I4 R-2-1/R-2-3 修复本体）；Plan 1 `2026-08-12-2050-1`（拆分契约面，独立 closure surface）
> Review: 四轮独立子 agent 对抗性审查（fresh sessions ses_009f550f7ffe / ses_009edfd57ffe / ses_009e65689ffe / ses_009e1c2b4ffe），R4 共识 = executable as-is（4 Minor 已吸收，无 must-fix）→ draft → active

## Purpose

收口 audit 总评方向 (3)：**timeout 语义统一——超时 = 停止工作，不是只停等待**。三处已确认缺陷：AR-2（SPAWN 执行路径无 per-member timeout——`MemberFanOutDispatcher` SPAWN 分支 **与 `SpawnMemberAgentTaskStep` 单播路径**均裸奔，I4 R-2-3 只覆盖 BOUND 分支）、AR-3（orchestrator 整体 deadline 语义缺陷：与 per-member 同值 + 超时不取消底层图执行）、AR-4（gateway channel mode-1 fan-out 超时不 cancel 底层 listener，池线程泄漏）。同时把门禁②从「文件级 marker 存在」升级为「每条执行分支都有 marker」，消除 audit 总评指出的系统性盲区（"门禁绿但兄弟分支仍裸奔"）。

## Current Baseline

（2026-08-12 live 核实）

- **AR-2 SPAWN 路径裸奔（两处）**：
  1. `MemberFanOutDispatcher.dispatch`（`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/flow/MemberFanOutDispatcher.java:231-237`）BOUND 分支走 `executeBoundMember`（:233，内有 `orTimeout(memberExecTimeoutMs)` :326 + 超时 `cancelSession` :334）；SPAWN 分支走 `spawnOneTarget`（:235 → `supplyAsync(spawnAndInterpret)` :365-372）→ `DefaultMemberSpawner.spawnMember`（`team/DefaultMemberSpawner.java:171-188`）`agentEngine.execute(execRequest)` 后 `future.join()`（:174）**无界等待**。
  2. `SpawnMemberAgentTaskStep.execute`（`team/flow/SpawnMemberAgentTaskStep.java:251`）直接 `memberSpawner.spawnMember(spawnReq)`——**同步无界调用**（`spawnMember` 是同步契约，见该类 javadoc :48/:210；`DefaultMemberSpawner` 内部 `execute().join()` 无界）。该 step 由 `TeamTaskFlowOrchestrator` 在 DAG 节点级创建（`TeamTaskFlowOrchestrator.java:810`），当前构造器（`SpawnMemberAgentTaskStep.java:164-167`）**无 timeout 参数**。此路径不在门禁②判定标准内（§3.2 判定标准 ④ 只机械派生 `dispatch`/`executeAsync` 两个入口，`SpawnMemberAgentTaskStep` 不在 grep 面）——"门禁绿但兄弟路径裸奔"的直接实例。
- **共享根因**：`IMemberSpawner.spawnMember(SpawnMemberRequest)` 签名与 `SpawnMemberRequest` 均**无 timeout 字段**（`scheduler/SpawnMemberRequest.java:49-89`，字段仅 team/task/daemonSessionId/target）。timeout 要到达 `DefaultMemberSpawner` 内部的有界 `get()`，必须经由 `SpawnMemberRequest` 传递（`spawnMember` 是接口签名，加参 = 跨模块公共 API 变更，需人工确认；**加 `SpawnMemberRequest` 字段 = 非接口签名变更**，遵循 `target` 字段先例 :86）。`SpawnMemberRequest` 构造点：`MemberFanOutDispatcher.java:363`、`SpawnMemberAgentTaskStep.java:248`、`TestMemberSpawner.java:153`（test）——三处须同步。
- **AR-2 daemon 队列泄漏**：`TaskDispatchCoordinator.dispatchClaimedTask`（`team/scheduler/TaskDispatchCoordinator.java:213-216`）把 dispatch future 加入 `inFlightDispatches`（:246），`:247-258 whenComplete` 负责移除——future 不 settle 则条目永不移除；`awaitInFlightDispatches`（:138-154）只是等待方 `f.get(remaining, TimeUnit)` 超时返回，不清理队列。
- **AR-3 orchestrator 语义缺陷**：`TeamTaskFlowOrchestrator.executeAsync`（`team/flow/TeamTaskFlowOrchestrator.java:609-626`）对整体 result future 用 `.orTimeout(memberExecTimeoutMs, TimeUnit.MILLISECONDS)`（:618）——整体 deadline 与 per-member 同值（默认 120s，`DefaultAgentEngineConfig.DEFAULT_MEMBER_EXEC_TIMEOUT_MS`），合法 N 层顺序 DAG（每层合法跑满 120s）会被 120s 整体 deadline 误杀；超时后 `.exceptionally` 只把结果 future 变 failed（:619-626），**不 cancel 底层 nop-task 图**——成员 agent 继续执行（zombie 消耗 LLM 配额）。nop-task 暴露取消入口：`ITaskRuntime extends ICancellable`（`cancel(reason)` 可用，live 核实）。对比库内既有模式：BOUND 分支超时会 `cancelSession`（`MemberFanOutDispatcher.java:334`）、`SingleTurnExecutor:146`/`AgentToolDispatcher:250,268` 超时会 `future.cancel(true)`。
- **AR-4 channel fan-out 线程泄漏**：`ChannelMessageServiceImpl.fanOutToListeners`（`nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/ChannelMessageServiceImpl.java:368-381`）`CompletableFuture.runAsync(() -> listener.onInbound(message), executor).orTimeout(dispatchTimeoutMs, ...)`（:371-373）——`orTimeout` 返回**新 stage**，只让该 stage 超时，**不中断源 `runAsync` 任务**（cancel 只向下传播不向上传播）；listener 继续在池线程上跑；`whenComplete`（:374-379）只 LOG.error 不 cancel。池大小 `max(2, availableProcessors)`（`resolveFanOutExecutor` :213 方法，池创建实现 :224-226），持续挂起 listener → 池耗尽 → 后续 inbound 在无界队列无限排队。**修复机制必须 cancel 原始 `runAsync` future（保留引用），而非 orTimeout 产生的 stage**。库内既有 cancel 模式：`SingleTurnExecutor:146`、`AgentToolDispatcher:250,268`、`MemberFanOutDispatcher:335`。
- **门禁②盲区（系统性）**：`TestInvariantGate2OrchestrationTimeout.TABLE` 行 15/16（`MemberFanOutDispatcher.dispatch`/`TeamTaskFlowOrchestrator.executeAsync`，marker=`orTimeout`）与 gateway 模块 gate 测试（`TestInvariantGate2GatewayTimeout` 存在，live 核实）均为**文件级 marker 检查**（`source.contains(marker)`，`TestInvariantGate2OrchestrationTimeout.java:134-137`）——文件任一位置含 `orTimeout` 即绿。**live 事实（2026-08-12 核实）：`MemberFanOutDispatcher.java` 当前已含 2 个 `orTimeout` 字符串（:308 注释 + :326 代码）**——任何 `contains` 式或裸 occurrence 计数检查都无法区分"注释里有一个"与"每条执行分支各有一个"，**必须升级为代码级（去除注释后）occurrence 计数**：修复后 `MemberFanOutDispatcher` 代码级 `orTimeout` 恰为 2（BOUND :326 + SPAWN 新增）；负例 fixture 移除 SPAWN 代码 marker → 计数 1 → 门禁红。
- **catalog §3.2 表 15 行**（`ai-dev/audits/nop-ai-invariants/invariant-catalog.md:186`）：`MemberFanOutDispatcher.dispatch` 单一条目声明 "per-member orTimeout(memberExecTimeoutMs) + 超时取消子会话"——对 BOUND/SPAWN 两种目标形态未区分，需拆两条或标注分支级。
- **gate-gaps.yaml**（`ai-dev/audits/nop-ai-invariants/gate-gaps.yaml`）：gate-2 family 当前 4 条 not-applicable，无 missing-declaration 条目；本次修复后无需加清单（修复即实判绿）。
- **I4 修复先例（R-2-3）**：BOUND 分支 orTimeout + cancelSession 模式已在 live（`MemberFanOutDispatcher.java:316-349`），SPAWN 分支复用同一 deadline 来源 `DefaultAgentEngineConfig.memberExecTimeoutMs`（:150 默认 120000，setter 拒绝非正数 :965-969）。
- I5 full-green 基线（2026-08-12）：`./mvnw test -pl nop-ai -am -T 1C` BUILD SUCCESS；五族门禁零命中。

## Goals

- AR-2：SPAWN **两条路径**（`MemberFanOutDispatcher` SPAWN 分支 + `SpawnMemberAgentTaskStep` 单播）都获得 per-member timeout——timeout 经 `SpawnMemberRequest` 新字段到达 `DefaultMemberSpawner`，`future.join()` 改为 `future.get(memberExecTimeoutMs, TimeUnit)`，超时诚实失败（`SpawnMemberResult.spawnFailed`）；spawn worker 线程必被释放；`TaskDispatchCoordinator.inFlightDispatches` 不再因不 settle 的 future 永久泄漏。
- AR-3：orchestrator 整体 deadline 与 per-member 解耦（`memberExecTimeoutMs × maxDepth` 或独立配置项）；整体超时时通过 `ITaskRuntime.cancel(reason)` 取消底层图（nop-task 已有取消能力），或明确记录"图仍在跑"的失败状态，不静默。
- AR-4：channel mode-1 fan-out 超时后 cancel **原始 `runAsync` future**（非 orTimeout stage），对齐库内 cancel 模式。
- 门禁②升级：team-flow fan-out / channel fan-out 条目从「文件级 marker」升级为「**分支级 occurrence 计数**」（BOUND + SPAWN 两分支、mode-1 + mode-2 两等待面各 ≥1 个 marker），负例 fixture 必须能红。
- catalog §3.2 表 15 行拆分/标注，gate-gaps.yaml 同步。

## Non-Goals

- **不修 P2**：`dispatchTimeoutMs` 死旋钮（AR-9）等 P2 项属 `## Follow-up Backlog`，不在本 plan。
- 不改变 `memberExecTimeoutMs` 默认值；**不改 `IMemberSpawner.spawnMember` 接口签名**（timeout 经 `SpawnMemberRequest` 字段传递，非公共 API 变更；如执行中发现必须改接口，暂停并人工确认）。
- 不重构 channel 池策略（`resolveFanOutExecutor` 池大小/队列策略不动，只补 cancel）。
- 不改门禁①③④⑤（只动门禁② team-flow/gateway 条目判定）。

## Scope

### In Scope

- `SpawnMemberRequest` 新增 `memberExecTimeoutMs` 字段 + 构造器（非接口变更）+ 3 个构造点同步（`MemberFanOutDispatcher:363` / `SpawnMemberAgentTaskStep:248` / `TestMemberSpawner:153`）。
- `DefaultMemberSpawner.spawnMember`（:171-188）有界等待改造 + 超时诚实失败。
- `MemberFanOutDispatcher` SPAWN 分支（:235 `spawnOneTarget`）超时处置（如需要，与 spawner 层修复分工由执行裁定：优先 spawner 层覆盖两路径）。
- **`SpawnMemberAgentTaskStep`（:141-178 构造器 + :251 调用）加 timeout 传递**——构造器加 `memberExecTimeoutMs` 参数（`TeamTaskFlowOrchestrator:810` 创建点传入，orchestrator 已有该字段 :214）。
- `TaskDispatchCoordinator.inFlightDispatches` 队列泄漏防护（future 有界化后 whenComplete 自然触发；如不足再补整体 deadline）。
- `TeamTaskFlowOrchestrator.executeAsync` deadline 计算 + 超时取消/记录语义。
- `ChannelMessageServiceImpl.fanOutToListeners`（mode-1）超时 cancel 原始 future；mode-2（`dispatchInbound:346-348` sendAsync().orTimeout）核实是否需同款处置并裁定。
- 门禁②：`TestInvariantGate2OrchestrationTimeout` / `TestInvariantGate2GatewayTimeout` occurrence 计数判定 + catalog §3.2 表 15 行拆分 + gate-gaps.yaml 同步 + 负例测试（SPAWN 无 marker / 计数不足时必须红）。

### Out Of Scope

- P2 项（AR-5~9）→ roadmap `## Follow-up Backlog`。
- 门禁③④⑤、其他模块（nop-ai-core/toolkit/shell）。

## Execution Plan

### Workstream 1 - AR-2：SPAWN 双路径 per-member timeout（spawner 层根修）

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/scheduler/SpawnMemberRequest.java`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/DefaultMemberSpawner.java`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/flow/SpawnMemberAgentTaskStep.java`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/flow/MemberFanOutDispatcher.java`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/scheduler/TaskDispatchCoordinator.java`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/flow/TeamTaskFlowOrchestrator.java`（:810 创建点）

- Item Types: `Fix | Proof | Decision`

- [x] `Fix` `SpawnMemberRequest` 新增 `memberExecTimeoutMs` 字段（`private final long memberExecTimeoutMs`）+ 构造器重载（保留既有 2 个构造器向后兼容；**既有构造器默认值 = `DefaultAgentEngineConfig.DEFAULT_MEMBER_EXEC_TIMEOUT_MS`，禁止用 0**——`get(0, TimeUnit)` 会立时超时，未同步的调用点会从"无界等待"静默变成"瞬间失败"，语义更坏）：`MemberFanOutDispatcher.java:363`（`new SpawnMemberRequest(team, task, dispatchSessionId, target, memberExecTimeoutMs)`）、`SpawnMemberAgentTaskStep.java:248`、`TestMemberSpawner.java:153` 三构造点同步。
- [x] `Fix` `DefaultMemberSpawner.spawnMember`（:171-188）：`future.join()`（:174）改为 `future.get(request.getMemberExecTimeoutMs(), TimeUnit.MILLISECONDS)`——**`TimeoutException`/`InterruptedException`/`ExecutionException` 均为 checked exception**，现有 catch 只覆盖 `CompletionException`/`RuntimeException`（:175-188），必须新增显式 catch：`TimeoutException` → `SpawnMemberResult.spawnFailed("spawned agent timed out after ...")` 诚实失败；`InterruptedException` → 恢复中断标志（`Thread.currentThread().interrupt()`）+ `spawnFailed`；`ExecutionException` → unwrap cause（`MemberFanOutDispatcher.java:330-331`/`:426-432` 的 unwrap 先例）+ `spawnFailed`；不得让 checked exception 漏出。
- [x] `Fix` **（必做，非可选项）** `MemberFanOutDispatcher.spawnOneTarget`（:359-373）**加 per-target `orTimeout(memberExecTimeoutMs)`**：`spawnOneTarget` 签名新增 `long memberExecTimeoutMs` 参数（`dispatch()` :235 调用点传入，`dispatch` 已有该参数）；对 `supplyAsync` 返回的 future 加 `.orTimeout(memberExecTimeoutMs, TimeUnit.MILLISECONDS)`——两层防护：spawner 层有界 `get()` 保证生产 engine 挂起时 worker 必释放；dispatcher 层 orTimeout 保证**即使 spawner 异常阻塞（如测试 mock 阻塞）**，`dispatch` 的 reduce `f.join()`（:251）也在 deadline 内返回（orTimeout 使 future 异常完成 → `allOf` 传播 → :298 `exceptionally` 转失败 outcome）。**必做理由**：WS4 门禁计数（修复后代码级 orTimeout = 2）与 WS1 行为测试（阻塞 mock 场景 dispatch 必在 memberExecTimeoutMs 内返回）都依赖该层。
- [x] `Fix` `SpawnMemberAgentTaskStep`：构造器（:164-167）新增 `long memberExecTimeoutMs` 参数并传入 `SpawnMemberRequest`（:248）；`TeamTaskFlowOrchestrator.java:810` 创建点传入 orchestrator 已有字段 `this.memberExecTimeoutMs`（:214）。
- [x] `Proof`（**必做，替换原"可选兜底"项**）：确认 spawner 层有界 `get()` + dispatcher 层 orTimeout 双层覆盖后，`supplyAsync` worker 在 timeout 后必然结束（worker 释放）；记录双层防护的职责分工（spawner 层 = 生产 worker 释放；dispatcher 层 = dispatch future 有界返回）。
- [x] `Fix`（配套）行为级测试（`nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/team/`）——**两个独立 fixture，不可合并为一个**（两路径的超时机制不同）：
  - fixture A（dispatcher 路径）：mock `IMemberSpawner` 的 `spawnMember` **在方法内阻塞**（`spawnMember` 是同步契约，mock 必须 block 在方法体内，不能"返回永不 settle 的 future"——方法不返回 future）→ 断言 `MemberFanOutDispatcher.dispatch` 在 `memberExecTimeoutMs` 内返回失败 outcome（靠 dispatcher 层 orTimeout；注意此 fixture 不覆盖 `SpawnMemberAgentTaskStep`——该链路上不经过 `DefaultMemberSpawner`，阻塞 mock 会让 step future 永不 settle）。
  - fixture B（step 路径）：真实 `DefaultMemberSpawner` + 挂起 `IAgentEngine`（`execute()` 返回永不 settle 的 future）→ 断言 `SpawnMemberAgentTaskStep.execute` 在 `memberExecTimeoutMs` 内返回失败态（靠 spawner 层有界 `get()` → `spawnFailed` → 节点失败 → 失败 outcome）。
  - 两条 fixture 的职责分工（执行时落实）：fixture A（dispatcher 层）= 断言 `dispatch` future 有界返回；**worker 释放只由 fixture B 断言**（A 中阻塞 mock 的 worker 不会返回——orTimeout 不 cancel 源任务，按 WS3 的 AR-4 同理分析）；两条 fixture 均断言 `inFlightDispatches` 清空。
- [x] `Proof` `TaskDispatchCoordinator`：确认 SPAWN future 有界化后 `inFlightDispatches` 条目在超时后必被 `whenComplete`（:247-248）移除（orTimeout 使 future 异常完成 → whenComplete 必触发）；若仍有不 settle 路径，补整体 deadline 兜底（`awaitInFlightDispatches` 超时后强制移除条目）。

Exit Criteria:

> 每个 Workstream 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Workstream Status 改为 `completed`。

- [x] SPAWN 两条路径（dispatcher 分支 + `SpawnMemberAgentTaskStep`）均在 `memberExecTimeoutMs` 内必然 settle（代码 + 双 fixture 测试证据：fixture A 阻塞 mock → dispatch 靠 orTimeout；fixture B 挂起 engine → step 靠 spawner 有界 `get()`）
- [x] 超时路径诚实失败（`SpawnMemberResult.spawnFailed` / 失败 outcome），无静默
- [x] **端到端验证**：`TaskDispatchCoordinator.dispatchClaimedTask`（SPAWN target）→ `MemberFanOutDispatcher.dispatch` → `DefaultMemberSpawner` → engine 挂起 → 超时 → outcome 返回 + 队列清理完整走通（fixture A/B 行为级断言）；`TeamTaskFlowOrchestrator` → `SpawnMemberAgentTaskStep` → 超时失败同样走通（fixture B）
- [x] **接线验证**：测试断言 timeout 值确实从 `SpawnMemberRequest` 传到 spawn 执行点（不是测试里写死的常量；断言 `future.get(request.getMemberExecTimeoutMs()...)` 使用的值来源）
- [x] **无静默跳过**：新增超时路径在无法取消时显式记录（LOG），不吞异常
- [x] `./mvnw test -pl nop-ai-agent -am`（含 team 包全部测试）通过
- [x] No owner-doc update required（修复面 = 现有 I4 契约（memberExecTimeoutMs）的补齐；catalog 表 15 行拆分归 WS4）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Workstream 2 - AR-3：Orchestrator 整体 deadline 语义

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/flow/TeamTaskFlowOrchestrator.java`

- Item Types: `Fix | Decision`

- [x] `Decision` deadline 计算方式：整体 deadline = `memberExecTimeoutMs × maxDepth`（maxDepth = 图最大深度，从 `built.task` 图结构可算，live 核实可行性）或独立配置项（`teamFlowOverallTimeoutMs`，默认 `memberExecTimeoutMs × 2`）；裁定记录在 daily log。若 maxDepth 不可得则用配置项。
- [x] `Fix` `executeAsync`（:609-626）：整体 deadline 改用裁定值；超时路径通过 `ITaskRuntime.cancel(reason)`（nop-task `ICancellable` 能力，live 核实可用）取消底层图——**cancel 必须先 unwrap 异常（`orTimeout` 以 `CompletionException(TimeoutException)` 形式送达 :619，裸 `instanceof TimeoutException` 永不命中——用 `MemberFanOutDispatcher.java:330-331`/`:426-432` 的 unwrap 先例）再 `instanceof TimeoutException` 限定**（`.exceptionally` 同时接收节点失败异常，普通节点失败不应触发整图 cancel，否则 nop-task 的 GraphTaskStep 短路语义被改变）；仅超时（`TimeoutException`）时 cancel；取消失败或图继续执行时，`recorder.buildResult(false, ...)` 带 timeout 标记 + LOG 记录"底层图可能仍在执行"，不得静默。
- [x] `Fix`（配套）行为级测试：构造多层图（每层成员 mock 慢执行，单层 < 120s 但总时长 > 整体 deadline）→ 断言合法多层流不被误杀（整体 deadline > per-member 的证明）；构造整体挂起图 → 断言超时后返回失败结果且底层被 cancel（mock `ITaskRuntime.cancel` 被调用）或取消失败被记录。

Exit Criteria:

> 每个 Workstream 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Workstream Status 改为 `completed`。

- [x] 整体 deadline 与 per-member 解耦（代码可见：不是同一个 `memberExecTimeoutMs` 直接复用；Decision 裁定已记录）
- [x] 超时后不再"只等不停"：`ITaskRuntime.cancel` 被调用（或明确记录取消失败 + 继续执行状态）
- [x] 行为级测试覆盖「合法多层流不误杀」+「挂起图超时失败」两方向
- [x] **接线验证**：整体 deadline 值确实被 `orTimeout` 使用（测试断言/代码审查）；cancel 调用确实在超时路径上（测试断言 mock cancel 被调）
- [x] **无静默跳过**：超时路径不吞异常，取消失败显式记录
- [x] No owner-doc update required（orchestrator 内部语义修复；如引入新配置项则同步 `DefaultAgentEngineConfig` 或 beans 文档——按引入方式裁定）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Workstream 3 - AR-4：Channel fan-out 超时 cancel 原始 future

Status: completed
Targets: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/ChannelMessageServiceImpl.java`

- Item Types: `Fix | Proof`

- [x] `Fix` `fanOutToListeners`（:368-381）：**保留原始 `runAsync` future 引用**（如 `CompletableFuture<Void> raw = CompletableFuture.runAsync(...)`），在带 `orTimeout` 的 stage 的 `whenComplete` 中：若超时（`TimeoutException`）→ `raw.cancel(true)`（中断 `runAsync` 任务，对齐库内 `SingleTurnExecutor:146`/`AgentToolDispatcher:250,268` 模式）——**cancel 必须作用于源 future，不是 orTimeout 产生的 stage**（stage cancel 不会中断源任务）。
- [x] `Fix`（配套）行为级测试（gateway 模块）：挂起 listener（阻塞不返回）→ 断言 dispatch 在 `dispatchTimeoutMs` 内返回 + **原始 future 被 cancel**（`raw.isCancelled()` 或 listener 线程中断标志）；确认不阻塞 transport 线程（I3 R-2-1 原有断言保留）。
- [x] `Proof` mode-2（`dispatchInbound` :346-348 `sendAsync().orTimeout`——注意：`bridgeConsumer` :147-155 调的是 `fanOutToListeners`，sendAsync().orTimeout 在 `dispatchInbound`）是否需同款 cancel：sendAsync 是持久化等待面（at-least-once 契约），核实其 cancel 语义是否会破坏投递——裁定：需要则补，不需要则记录理由（daily log）。

Exit Criteria:

> 每个 Workstream 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Workstream Status 改为 `completed`。

- [x] mode-1 超时后原始 `runAsync` future 被 cancel（`raw.cancel(true)`，代码 + 测试双证据）
- [x] 挂起 listener 场景池线程不泄漏（行为级测试断言）
- [x] mode-2 裁定记录（补 cancel 或记录不适用理由）
- [x] **接线验证**：`dispatchTimeoutMs` 超时路径确实触发对原始 future 的 cancel（测试断言 cancel 发生 + 作用于 raw 引用）
- [x] **无静默跳过**：cancel 失败显式记录，不吞
- [x] `./mvnw test -pl nop-ai-gateway -am` 通过
- [x] No owner-doc update required（gateway 内部修复，无公共契约变更）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Workstream 4 - 门禁②分支级判定升级 + catalog 同步

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/gate/TestInvariantGate2OrchestrationTimeout.java`、gateway 模块 `TestInvariantGate2GatewayTimeout`、`ai-dev/audits/nop-ai-invariants/invariant-catalog.md` §3.2 表 15 行、`ai-dev/audits/nop-ai-invariants/gate-gaps.yaml`

- Item Types: `Fix | Proof`

- [x] `Fix` 门禁② team-flow 条目判定升级：`MemberFanOutDispatcher.dispatch`（TABLE 行 15）marker 判定从 `source.contains("orTimeout")` 升级为 **代码级（去注释 + 去字符串字面量）occurrence 计数 ≥ 2**（BOUND 分支 :326 + SPAWN 分支新增标记各一个）——实现方式：`Entry` 增加 `minOccurrences` 字段（默认 1），`tableEntriesDeclareTimeoutOrAreRegistered`（:127-154）改用 `countCodeOccurrences(source, marker)`（**剥离 `//` 与 `/* */` 注释及字符串字面量后计数**——`MemberFanOutDispatcher.java:308` 注释含 orTimeout，裸计数会把注释算进去导致修复前就计数=2 绿；字符串字面量同理防未来 LOG 文案污染计数）`>= e.minOccurrences`；`TeamTaskFlowOrchestrator.executeAsync` 行 16 保持 ≥1；gateway `TestInvariantGate2GatewayTimeout` 的 `dispatchInbound` 条目**采用按 mode 拆两条目**（mode-1 / mode-2 各一条、各 ≥1）而非计数 ≥2——**live 事实：`ChannelMessageServiceImpl` 修复前代码级 orTimeout 已有 2 个（:348 mode-2 + :373 mode-1），计数 ≥2 对 AR-4 修复前后零判别力**；拆两条目后 mode-1 条目在 WS3 前有 1 个 marker、修复后 2 个（orTimeout + cancel 处），branch-level 判别成立。**注意：任何 marker 计数都无法检测 AR-4 的 cancel 回归（`orTimeout` 在修复前后都在），cancel 语义只能靠 WS3 行为级测试（`raw.isCancelled()`）拦截**——在 WS3 与 WS4 各自记录该局限。
- [x] `Fix` 计数辅助实现位置：`InvariantGateSupport`（nop-ai-agent 测试支撑类，gateway gate 测试 self-contained 不共享 test-jar）→ **两个模块各实现一份 `countCodeOccurrences`**（agent 模块放入 `InvariantGateSupport`；gateway 模块在其 gate 测试类内实现同语义辅助），实现需含去注释 + 去字符串字面量，两实现语义一致（以 agent 版为准，gateway 版同步）；避免两版行为分叉。
- [x] `Fix` 负例测试（防 inert 登记）：现有 negative 测试机制（fake entry）扩展到计数场景——fixture 把 SPAWN 分支代码 marker 移除（或计数降为 1）→ 门禁必须红；恢复 → 绿。**`contains` 与裸 occurrence 计数在此场景必然失灵（BOUND 的 marker 还在文件里 + 注释里的 marker 也算数），负例必须用代码级计数断言**；修复前基线记录：当前文件代码级 orTimeout = 1（:326；:308 是注释不计），修复后 = 2——负例 fixture（去掉 SPAWN 代码 marker）→ 代码级计数 = 1 → 红，与修复前状态区分。
- [x] `Fix` catalog §3.2 表 15 行拆分：`MemberFanOutDispatcher.dispatch` 拆为 BOUND/SPAWN 两条（或行内标注"分支级：两分支均须声明 timeout marker（代码级计数 ≥2）"）；表 16 行（orchestrator）标注整体 deadline 语义、表 17 行（channel）**拆为 mode-1/mode-2 两条目**（与 gateway 门禁测试拆条一致）并标注 cancel 语义。`SpawnMemberAgentTaskStep` 登记裁定：该 step 的 timeout 契约由 spawner 层（`SpawnMemberRequest.memberExecTimeoutMs` → `DefaultMemberSpawner` 有界 `get()`）承载，step 自身无独立等待面（fixture B 证据）→ 登记 not-applicable + 理由；若执行中发现 step 需要自有 marker（如判定标准扩展），则补表登记——裁定记录。
- [x] `Proof` gate-gaps.yaml：确认修复后 gate-2 family 状态（4 条 not-applicable 不变，无新增 missing）；若判定升级引入的新检查有漏网实例（如 `SpawnMemberAgentTaskStep` 若裁定需补表），按门禁机制登记 missing-declaration（不允许为保绿白名单化）。
- [x] `Proof` 负例验证（棘轮）：临时移除 SPAWN 分支代码 marker（或计数降 1）→ 门禁红；恢复 → 绿（记录红/绿对照；对照基准 = 修复后代码级计数 2 vs 负例 1）。

Exit Criteria:

> 每个 Workstream 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Workstream Status 改为 `completed`。

- [x] 门禁②对 team-flow fan-out / channel fan-out 的**分支级代码计数判定**生效（负例验证红/绿对照记录；`contains` 式检查已替换为去注释/去字符串计数；channel 为按 mode 拆条；cancel 回归由 WS3 行为测试承接，门禁局限已记录）
- [x] catalog §3.2 表 15 行拆分/标注完成，表 16/17 语义同步；`SpawnMemberAgentTaskStep` 登记裁定已记录
- [x] gate-gaps.yaml 状态与 live 一致（无白名单化）
- [x] `./mvnw test -pl nop-ai-agent,nop-ai-gateway -am`（含门禁测试）通过
- [x] owner-doc 已更新：catalog §3.2 / gate-gaps.yaml 属门禁 owner 文档，本次即为更新主体；`docs-for-ai/` 无门禁机制描述，`No docs-for-ai update required`（如发现 catalog 有 docs-for-ai 引用面则补）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Workstream 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] AR-2（SPAWN 两路径无界等待）已修复：`MemberFanOutDispatcher` SPAWN 分支 + `SpawnMemberAgentTaskStep` 均在 `memberExecTimeoutMs` 内 settle
- [x] AR-3（orchestrator deadline 同值 + 超时不取消）已修复：整体 deadline 解耦 + 超时 `ITaskRuntime.cancel` 落地
- [x] AR-4（channel fan-out 线程泄漏）已修复：超时 cancel 原始 `runAsync` future
- [x] 门禁②分支级计数判定升级完成且负例验证红/绿对照成立
- [x] 行为/契约结果已达成（三个行为级测试套件全绿）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（audit 总评方向 3 三实例全收口；`SpawnMemberAgentTaskStep` 已纳入 scope 或显式裁定登记，不属静默裸奔）
- [x] 受影响 owner docs（catalog §3.2 / gate-gaps.yaml）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）三个修复点的调用链在运行时确实连通（行为级测试断言超时 → cancel/失败处置真实触发），（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-ai -am`
- [x] `./mvnw test -pl nop-ai -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

无（三个 P1 均为 in-scope 必须修复项；`SpawnMemberAgentTaskStep` 已纳入 WS1 scope，不属 deferred）。

## Non-Blocking Follow-ups

- Classification: `optimization candidate`（spawn 超时后 spawned session 的清理深度——`cancelSession` 是否级联回收 spawned session 全部子资源）
  - Why Not Blocking Closure: WS1 采用有界 `get()` + `spawnFailed` 诚实失败，已满足"超时 = 停止等待 + worker 释放"契约；session 级联回收是深层清理优化，不改变本 plan 三个 P1 的收口语义。
  - Successor Required: `no`
- Classification: `watch-only residual`（nop-task 图取消能力若不足——`ITaskRuntime.cancel` 已 live 核实存在）
  - Why Not Blocking Closure: WS2 已核实 `ICancellable.cancel(reason)` 可用；若执行中发现 cancel 不完整（图部分节点继续跑），记录为框架能力 gap + 复触发登记，不阻塞本 plan。
  - Successor Required: `no`

## Closure

Status Note: WS1-4 全部完成且独立 closure audit APPROVED——AR-2（SPAWN 双路径有界等待）、AR-3（orchestrator 整体 deadline 解耦 + 超时取消底层图）、AR-4（channel fan-out 超时 cancel 原始 future）三 P1 修复落地，门禁②分支级计数判定升级 + 棘轮红/绿对照成立，catalog/gate-gaps 同步 live baseline。
Completed: 2026-08-12

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure audit 子 agent（general，review-only，fresh session）
- Audit Session: `ses_0098af0f6fferNvWH3ETu5FK37`（报告内另记录 `ses_a9k2timeoutclosureaudit`）
- Evidence:
  - WS1 八条 Exit Criteria 逐条 PASS：live 证据 `DefaultMemberSpawner.java:188` 有界 get（Timeout/Interrupted/Execution/Completion 显式 catch → spawnFailed）；`MemberFanOutDispatcher.java:387` SPAWN per-target orTimeout（BOUND :326）；`TestSpawnDispatchTimeout` 3 测试（阻塞 mock → dispatch 有界失败 outcome + cause 链含 TimeoutException；队列 drain 到 0；coordinator → dispatcher → 真实 spawner → 挂起 engine e2e）+ `TestSpawnStepTimeoutHonestFailure`（单线程池 probe 证明 worker 释放）；接线 = 300ms 配置 vs 120s 默认（5s 窗口判别）
  - WS2 七条 Exit Criteria 逐条 PASS：`TeamTaskFlowOrchestrator.java:787` computeGraphDepth + :820 overallTimeoutMs = memberExecTimeoutMs × maxDepth（饱和）+ :634 orTimeout(built.overallTimeoutMs)；:645-646 unwrap 后 TimeoutException 限定 → :655 taskRt.cancel(CANCEL_REASON_TIMEOUT) + LOG（取消失败 :659-661 显式记录）；节点失败不 cancel；`TestTeamTaskFlowOrchestratorOverallTimeout` 2 测试（合法 2 层流不误杀 + 挂起 reduce → 超时失败 + 真实 taskRt isCancelled/reason=="timeout"）
  - WS3 六条 Exit Criteria 逐条 PASS：`ChannelMessageServiceImpl.java:393` raw runAsync 保留 + :427 raw.cancel(true) + :435 worker.interrupt()（workerRef runnable 内捕获 :394、finally 消费中断标志 :410、未启动竞态守卫 :398-402）；`TestChannelFanOutTimeoutCancel`（interrupt 标志 + 单线程池 probe + transport 线程不被阻塞）；mode-2 裁定 = 不 cancel（at-least-once 持久化面，理由记录 daily log）
  - WS4 六条 Exit Criteria 逐条 PASS：`InvariantGateSupport.countCodeOccurrences`（去 //、/* */、字符串/字符字面量）+ Entry.minOccurrences + dispatch 行 = 2；gateway 门禁按 mode 拆条 + 本地同语义实现 + 按 mode 方法体派生；负例两模块各 1（不足计数 → 红）；棘轮红/绿对照实跑（临时移除 :387 SPAWN marker → 红 `found 1 code occurrence(s)... 2 required`；恢复 → 绿）；catalog §3.2 表 15/16/17 + SpawnMemberAgentTaskStep not-applicable 登记 + gate-gaps.yaml 4 not-applicable 不变零新增
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-08-12-2050-2-ai-invariant-timeout-stop-work-semantics.md --strict` exit 0（closure 后复跑：0 unchecked + Closure Evidence 已写入）
  - Anti-Hollow 检查结果：三条调用链运行时连通——(a) SPAWN: step.execute → supplyAsync → spawnMember → 有界 get → spawnFailed → 节点失败 → 失败结果（测试断言行为非仅完成）；(b) 整体: executeAsync → orTimeout → unwrap → TimeoutException → taskRt.cancel（断言 isCancelled+reason）；(c) channel: raw.cancel + worker.interrupt → listener 中断 → 池 probe；`scan-hollow-implementations.mjs --module nop-ai --severity high` = 仅 2 条 pre-existing（PlanReplanner/NoOpProviderFailoverQueue，I5/I6 同基线），本 diff 零发现
  - Deferred 项分类检查：Non-Blocking Follow-ups 两项（spawn session 级联回收 = optimization candidate；nop-task cancel 深度 = watch-only residual）均非 in-scope live defect，无静默降级
  - 验证命令：`./mvnw test -pl nop-ai -am -T 1C` BUILD SUCCESS full-green（三模块 + 全部门禁族零命中）；checkstyle gate N/A（仓库未启用，pre-existing 同 I5/I6 口径）；`check-import-order.mjs` 本 diff 文件零命中
  - 审计发现：0 Blocker / 0 Major；2 Minor（catalog 行号陈旧，doc-only）——已修复（catalog 表 16 :581 / 表 17 :329 / SpawnMemberAgentTaskStep 创建点 :934）

Follow-up:

- 无（Non-Blocking Follow-ups 两监督项按计划保留，不属 plan-owned work）

## Optional Sections

### Risks And Rollback

- 风险 1：`SpawnMemberRequest` 加字段影响 3 个构造点（2 main + 1 test）——已在 WS1 列出，非公共 API 变更（类在 nop-ai-agent 模块内，`IMemberSpawner` 签名不动）。若执行中发现必须改 `IMemberSpawner` 签名 → 暂停 + 人工确认。
- 风险 2：WS3 cancel 挂起 listener 可能中断 listener 正在进行的业务（onInbound 中途）；对齐库内既有 cancel 模式（SingleTurnExecutor/AgentToolDispatcher），中断语义一致，可接受。
- 风险 3：WS2 多层图 deadline 计算 maxDepth 若不可得，退化为独立配置项；Decision 项已预留。
- 风险 4：WS4 计数判定若与既有 negative 测试机制冲突（fake entry 机制按 id 匹配），需要扩展测试支撑——已在 WS4 列出实现方式。
- Rollback：各 WS 独立 revert；门禁 WS4 的判定升级若引入误报，回退判定标准即可，不影响 WS1-3 修复。
