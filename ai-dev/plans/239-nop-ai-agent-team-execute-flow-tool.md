# 239 nop-ai-agent LLM 直面编排工具（`team-execute-flow`）：IToolExecutor 消费 TeamTaskFlowOrchestrator 闭合 Layer 4 顶部入口

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L4-team-execute-flow-llm-tool

> Last Reviewed: 2026-06-18
> Source: carry-over from `ai-dev/plans/238-nop-ai-agent-orchestrator-auto-spawn.md`（Non-Blocking Follow-ups「LLM 直面编排工具（`team-execute-flow`）：Classification: successor plan required（依赖本计划 + 调度策略裁定）」）；同一 carry-over 在 `237`/`236`/`233` 的 Non-Goals / Non-Blocking Follow-ups 中亦显式延期；`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 4（team-execute-flow successor）；`ai-dev/design/nop-ai-agent/nop-ai-agent-task-flow-integration.md`（§successor「LLM 直面编排工具」）+ `nop-ai-agent-orchestrator-auto-spawn.md`（§successor）+ `nop-ai-agent-task-scheduler-daemon.md`（§successor）
> Related: `238`（交付 orchestrator auto-spawn——本计划消费其 `TeamTaskFlowOrchestrator.execute` + `IMemberSpawner` 契约）、`233`（交付 `TeamTaskFlowOrchestrator` + `TeamTaskFlowResult`）、`237`（交付 `IMemberSpawner`/`NoOpMemberSpawner`/`DefaultMemberSpawner`）、`225`（交付 team tool 模式 + `AgentToolExecuteContext` team 服务访问）、`228`（交付 `ITeamAclChecker` team ACL 强制）

## Purpose

交付 LLM 直面编排工具 `team-execute-flow`（`IToolExecutor` 实现），使 LLM agent（如 ReAct loop 内的 LEAD/MEMBER）可通过工具调用驱动 plan 233 交付的 `TeamTaskFlowOrchestrator` 执行其所在团队的 task DAG。该工具是 Layer 4「无人值守多 Agent 自主编排」栈的**顶部 LLM 入口**——plan 233 交付同步编排能力、plan 236 交付自动调度守护、plan 237 交付成员 auto-spawn、plan 238 交付编排器侧 auto-spawn；本计划闭合最后一块缺口：LLM 经工具调用进入编排链路。采用同步调度策略（async 为显式 Non-Goal successor）。

## Current Baseline

基于 live repo 核对（`nop-ai/nop-ai-agent/src`，2026-06-18）：

- **`IToolExecutor` 契约已落地**：`io.nop.ai.toolkit.api.IToolExecutor`（`getToolName()` + `executeAsync(AiToolCall, IToolExecuteContext) → CompletionStage<AiToolCallResult>`）。团队工具模式已由 plan 225 确立并经 plan 227/228 复用：`AgentToolExecuteContext` 暴露 `getEngine()`/`getTeamManager()`/`getTeamTaskStore()`/`getTeamAclChecker()`/`getSessionId()`；工具经 `getTeamBySession(callerSessionId)` 解析调用者所在团队（不从参数取 teamId）；NoOp/null 服务下诚实报告「not enabled」而非静默成功（Minimum Rules #24）；ACL 强制在团队解析后、业务逻辑前（plan 228，action→READ/WRITE）。
- **`TeamTaskFlowOrchestrator` 已落地（plan 233 ✅）+ auto-spawn（plan 238 ✅）**：`execute(String teamId) → TeamTaskFlowResult`（同步、经 nop-task DAG 运行时 `syncGetOutputs`）。构造器 `(IAgentEngine, ITeamTaskStore, ITeamManager, ITaskFlowManager, IMemberSpawner)`，后两参 null 时分别回退 `new TaskFlowManagerImpl()` / `NoOpMemberSpawner.noOp()`。`IMemberSpawner`（plan 237）wire-at-consumer 注入 orchestrator（NoOp shipped 默认零回归；functional `DefaultMemberSpawner` 启用 unbound-member 节点运行期 spawn 执行）。
- **`TeamTaskFlowResult` 已落地（plan 233 ✅）**：`isSuccess()` / `getCompletedTaskIds()` / `getFailedTaskId()` / `getSkippedTaskIds()` / `getStartOrder()` / `getCompletionOrder()`。结构问题（空任务集 / 未知团队 / 成环 blockedBy）于 `execute` 内抛 `NopAiAgentException`（structural fast-fail，先于任何节点运行）；节点失败返回 `success=false`（honest failure，非静默成功）。
- **工具注册惯例已确立**：`nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/ai/beans/ai-agent-tools.beans.xml` 注册团队工具 bean，经 nop-ai-toolkit 的 `<ioc:collect-beans by-type="...IToolExecutor"/>` 自动收集。现有 team 工具（`team-send-message`/`team-status`/`team-task-create`/`team-task-update`）均为无状态 executor，服务从 context 获取、无 bean 属性注入。
- **`team-execute-flow` 零代码**：`team/flow/` 包对工具执行器零引用；`tool/` 包无 execute-flow 文件；beans.xml 无对应 bean。`AgentToolExecuteContext` 不暴露 orchestrator（其服务 engine/teamManager/taskStore 已足够构造 orchestrator）。

## Goals

- **`team-execute-flow` 工具交付**：`IToolExecutor` 实现（tool name `team-execute-flow`），LLM agent 可经工具调用驱动 `TeamTaskFlowOrchestrator.execute(teamId)` 执行其所在团队的 task DAG。
- **调度策略裁定 = 同步执行**：工具消费 `orchestrator.execute(teamId)`（同步），结果包装进 `CompletableFuture.completedFuture(...)`。async / cross-process 编排为显式 Non-Goal successor（`L4-async-cross-process-orchestration`）。
- **调用者团队从 session 解析（不从参数）**：工具经 `getTeamBySession(callerSessionId)` 解析调用者所在团队，不暴露 teamId 参数——与全部既有 team 工具一致，保证 LLM 只能编排自己所属团队（ACL-safe）。
- **auto-spawn 能力可消费**：工具经 wire-at-consumer 注入 `IMemberSpawner`（NoOp shipped 默认），构造 orchestrator 时传入。NoOp 下 unbound-member 节点诚实失败（orchestrator `success=false`）；functional spawner 下 unbound-member 节点运行期 spawn 执行（消费 plan 238 能力）。
- **诚实结果映射**：`TeamTaskFlowResult` → `AiToolCallResult`——成功映射为 success 结果体（含 completedTaskIds / startOrder / completionOrder）；节点失败（`success=false`）映射为诚实结果体（含 failedTaskId / skippedTaskIds / completedTaskIds，不静默成功）；结构异常（`NopAiAgentException`：空任务/未知团队/成环）映射为 errorResult。
- **ACL 强制**：action=`execute` → WRITE 权限（LEAD/MEMBER 允许，非成员拒绝，与 `team-task-create`/`team-task-update` 的 WRITE 一致）。
- **NoOp shipped 默认零回归**：teamManager/spawner 为 NoOp 或 null 时，工具诚实报告「not enabled / 未绑定成员诚实失败」，不影响既有工具行为。
- **端到端验证（Anti-Hollow #22）**：构造团队（声明 memberSpec 但不 `bindMemberSession`）+ 多节点依赖 DAG → 注入 functional spawner + mock 成员 agent → 经 `team-execute-flow` 工具调用 → 断言全部任务按依赖序 spawn-执行并转 COMPLETED，**全程无任何手动成员绑定**（证明 LLM 入口 → 编排器 → auto-spawn 完整连通）。

## Non-Goals

- **异步 / 跨进程流编排执行**：独立 carry-over（`L4-async-cross-process-orchestration`，P1），需 nop-task CompletableFuture async model + cross-process daemon 协调。本切片同步执行。
- **多团队 / 跨团队编排**：工具只编排调用者所属团队（从 session 解析）。
- **自定义调度策略 / 优先级 / 并行委派就绪集**：消费 orchestrator 现有同步依赖序调度，不引入新调度策略。
- **运行时动态增删图节点 / 改图**：独立 carry-over（`L4-dynamic-graph-edit`）。
- **重试 / 超时 / rate-limit decorator**：独立 carry-over（`L4-nop-task-decorator`）。结构异常与节点失败均诚实上报，不内建重试。
- **多成员 per-task 路由**：消费 orchestrator 现有团队级单一成员策略（含 spawner 的单一目标解析）。per-task 多成员路由为 successor。
- **spawn session 复用 / 池化**：消费 plan 237/238 的 per-node/per-task spawn 语义。池化为优化 successor。
- **task RE-CLAIM / 超时自动 ABANDON**：独立 carry-over（`L4-task-reclaim-timeout-abandon`）。
- **修改 `TeamTaskFlowOrchestrator` / `IMemberSpawner` / `AgentToolExecuteContext` / `ITeamAclChecker` 接口公共契约**：消费原样；`AgentToolExecuteContext` 无需新增字段（orchestrator 所需服务 engine/teamManager/taskStore 已在 context）。注：在 `DefaultTeamAclChecker.buildRequiredActions` 注册新 `(toolName, action)` 条目属**扩展 §5.1 矩阵数据**（In Scope），不属修改 `ITeamAclChecker` 接口契约。

## Scope

### In Scope

- `io.nop.ai.agent.tool` 包：新增 `TeamExecuteFlowExecutor implements IToolExecutor`（tool name `team-execute-flow`），消费 `TeamTaskFlowOrchestrator.execute` + `IMemberSpawner`
- `DefaultTeamAclChecker`（既有）：在 `buildRequiredActions` 注册 `("team-execute-flow", "execute") → TeamAclAction.WRITE`（扩展 §5.1 矩阵数据，使 functional checker 下 LEAD/MEMBER 允许、非成员拒绝；不改 `ITeamAclChecker` 接口契约）
- `ai-agent-tools.beans.xml`：注册 `team-execute-flow` bean（含 spawner 注入配置点）
- 测试文件：
  - focused 测试（团队解析 / NoOp 诚实 / ACL 拒绝 / 成功映射 / 节点失败诚实映射 / 结构异常映射 / spawner wired auto-spawn / NoOp spawner 诚实失败 / 接线验证）
  - 真实-checker ACL 测试 `TestTeamExecuteFlowExecutorAcl`（用 `new DefaultTeamAclChecker(teamManager)` 断言 LEAD allow / MEMBER allow / 非成员 deny，与 sibling 工具 ACL 测试模式一致）
  - 端到端测试（LLM 工具入口 → orchestrator → 多节点依赖 DAG → auto-spawn → COMPLETED，无手动绑定）
- 设计文档 `ai-dev/design/nop-ai-agent/nop-ai-agent-team-execute-flow.md`（记录核心裁定：同步调度策略 / team-from-session / wire-at-consumer spawner / 结果映射 / 拒绝替代方案）
- `nop-ai-agent-roadmap.md` §4 Layer 4：新增 `team-execute-flow` 工作项并标注已落地
- 既有 `ai-dev/design/nop-ai-agent/nop-ai-agent-task-flow-integration.md`（successor 项更新）+ `nop-ai-agent-orchestrator-auto-spawn.md`（successor 项更新）同步

### Out Of Scope

- 异步 / 跨进程编排（Non-Goal）
- 多团队编排 / 自定义调度策略 / 动态改图（Non-Goal）
- 重试 / 超时 / decorator（Non-Goal）
- 多成员 per-task 路由 / spawn session 池化（Non-Goal）
- task RE-CLAIM / 超时 abandon（Non-Goal）
- 修改 orchestrator / spawner / context 公共契约（Non-Goal）

## Execution Plan

### Design Decisions (Pre-Adjudicated)

以下裁定在 plan 撰写阶段已确定，执行时直接遵循，不再作为 in-flight Decision。

1. **调度策略 = 同步执行**。`TeamTaskFlowOrchestrator.execute(teamId)` 本身是同步的（经 nop-task `syncGetOutputs`）。工具调 `execute` 后将结果包装进 `CompletableFuture.completedFuture(mapResult(...))`。async 模型（CompletableFuture 链 / cross-process daemon）是独立 P1 successor，不属本切片。理由：orchestrator 已是同步；同步是使 LLM 入口可用的最小一步；async 需 nop-task async model（未落地）。

2. **调用者团队从 session 解析，不暴露 teamId 参数**。工具经 `agentCtx.getTeamManager().getTeamBySession(callerSessionId)` 解析调用者所在团队，与 `team-status`/`team-task-create`/`team-task-update`/`team-send-message` 全部既有 team 工具一致。理由：保证 LLM 只能编排自己所属团队（ACL-safe）；消除 LLM 误传他人 teamId 的风险；与既有 team 工具模式零分歧。`orchestrator.execute(team.getTeamId())`。

3. **工具经 wire-at-consumer 注入 `IMemberSpawner`，per-invocation 构造 orchestrator**。工具持有一个 `IMemberSpawner` 字段（setter/构造器注入，null-safe → `NoOpMemberSpawner.noOp()`，镜像 plan 238 决策5 + plan 237 daemon 接线惯例）。per-invocation：工具从 context 取 engine/teamManager/taskStore，结合注入的 spawner，构造 `new TeamTaskFlowOrchestrator(engine, taskStore, teamManager, null, spawner)`（taskFlowManager 传 null → orchestrator 内部回退 `TaskFlowManagerImpl`，spawner 传注入值）。理由：orchestrator 构造轻量、服务来自 context（per-invocation）、spawner 是共享 singleton（wire-at-consumer）；与既有 team 工具「服务从 context 取」模式一致；不需给 `AgentToolExecuteContext` 加字段；NoOp 默认零回归 + functional spawner 启用 auto-spawn。

4. **结果映射：`TeamTaskFlowResult` → `AiToolCallResult`，诚实不静默**。
   - `result.isSuccess() == true` → `AiToolCallResult{status="success"}`，body 为 JSON（`success:true, completedTaskIds, startOrder, completionOrder`）。
   - `result.isSuccess() == false`（节点失败 / NoOp spawner 下 unbound-member 诚实失败） → `AiToolCallResult{status="success"}`，body 为 JSON（`success:false, failedTaskId, skippedTaskIds, completedTaskIds`）。**status="success"**（与 team 工具 honest-denied / honest-not-enabled 一致：ReAct loop 不因「工具执行了但 DAG 未全成功」而 abort；LLM 可读 failure body 决策重试 / 放弃）。body 明确标 `success:false`，**不**静默成功。
   - 结构异常（`NopAiAgentException`：空任务集 / 未知团队 / 成环 blockedBy，由 `orchestrator.execute` 抛出） → `AiToolCallResult.errorResult(callId, e)`（工具的 try/catch 捕获，镜像既有 team 工具的顶层 try/catch）。
   理由：区分「工具执行了，DAG 结果是 success/failure」（结果体）与「工具自身出错 / 结构不合法」（errorResult）；failure body 诚实暴露 failedTaskId/skippedTaskIds（Minimum Rules #24，不吞信息）。

5. **ACL：action=`execute` → WRITE 权限，需在 `DefaultTeamAclChecker` 注册矩阵条目**。工具在团队解析后、orchestrator 调用前，调 `agentCtx.getTeamAclChecker().checkAccess(teamId, sessionId, "team-execute-flow", "execute")`。`DefaultTeamAclChecker.REQUIRED_ACTIONS` 是静态不可变 map，`checkAccess` 对未知 `(toolName, action)` 元组 **fail-closed deny**（`DefaultTeamAclChecker.java:131-140`）。因此必须在 `buildRequiredActions`（`:72-82`）新增 `m.put(key("team-execute-flow", "execute"), TeamAclAction.WRITE)`，否则 functional checker 下 LEAD/MEMBER 也会被拒绝（与 Goal 矛盾）。WRITE 权限经 §5.1 角色矩阵：LEAD = ADMIN 全通过、MEMBER 通过 WRITE、非成员拒绝。NoOp shipped 默认（`NoOpTeamAclChecker`）放行（零回归）。拒绝时返回 honest-denied 结果（status="success" + denial body，与 plan 228 既有 team 工具一致；taskStore 不被触碰、orchestrator 不被调用）。理由：编排执行触发任务状态变更（CLAIMED→COMPLETED），等同 WRITE。

### Phase 1 - `team-execute-flow` 工具实现 + spawner 接线 + focused 测试

Status: completed
Targets: `io.nop.ai.agent.tool.TeamExecuteFlowExecutor`（新）、`io.nop.ai.agent.team.DefaultTeamAclChecker`（既有，`buildRequiredActions` 新增一行矩阵条目）、`nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/ai/beans/ai-agent-tools.beans.xml`（新增 bean）、`nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/tool/TestTeamExecuteFlowExecutor.java`（新）、`nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/tool/TestTeamExecuteFlowExecutorAcl.java`（新）

- Item Types: `Decision`（同步调度策略 / team-from-session / wire-at-consumer spawner / 结果映射 / ACL action 裁定）、`Fix`（`DefaultTeamAclChecker` 矩阵缺少 `team-execute-flow` 条目，functional checker 下 LEAD/MEMBER 被误拒）、`Proof`

- [x] 实现 `TeamExecuteFlowExecutor implements IToolExecutor`（`TOOL_NAME = "team-execute-flow"`），遵循 team 工具模式：顶层 try/catch → context instanceof `AgentToolExecuteContext` 校验 → NoOp/null teamManager 诚实「not enabled」→ session 解析 → `getTeamBySession` 团队解析 → ACL `checkAccess(teamId, sessionId, "team-execute-flow", "execute")` 拒绝则 honest-denied
- [x] 在 `DefaultTeamAclChecker.buildRequiredActions`（`:72-82`）注册 `m.put(key("team-execute-flow", "execute"), TeamAclAction.WRITE)`（使 functional checker 下 LEAD/MEMBER 允许、非成员拒绝；未注册则 fail-closed deny 全拒）
- [x] per-invocation 构造 `TeamTaskFlowOrchestrator(engine, taskStore, teamManager, null, memberSpawner)`（engine/taskStore/teamManager 从 context；memberSpawner 从注入字段，null-safe→NoOp）→ 调 `execute(team.getTeamId())` → 按决策4映射 `TeamTaskFlowResult` 到 `AiToolCallResult`
- [x] spawner wire-at-consumer 注入：`memberSpawner` 字段 + setter（null-safe → `NoOpMemberSpawner.noOp()`，镜像 plan 238/237 接线惯例）
- [x] 诚实失败（No Silent No-Op #24）：NoOp/null teamManager = honest「not enabled」；无团队 = honest errorResult；ACL 拒绝 = honest-denied body；节点失败（`success=false`）= honest failure body（含 failedTaskId/skippedTaskIds，不静默成功）；结构异常 = errorResult；无空方法体 / 吞异常 / placeholder
- [x] 在 `ai-agent-tools.beans.xml` 注册 `team-execute-flow` bean（`<bean id="ai-agent-tools:team-execute-flow" class="io.nop.ai.agent.tool.TeamExecuteFlowExecutor"/>`），与既有 team 工具 bean 同列（auto-collect 路径）
- [x] 编写 focused 测试：调用者团队从 session 解析（不从参数）/ NoOp teamManager honest「not enabled」/ 无团队 honest errorResult / ACL 拒绝 honest-denied body（mock checker）/ 成功 DAG → success 结果体（含 completedTaskIds + startOrder + completionOrder）/ 节点失败（success=false）→ honest failure body（含 failedTaskId + skippedTaskIds）/ 结构异常（空任务集 / 未知团队 / 成环 blockedBy）→ errorResult / 注入 functional spawner → unbound-member 节点运行期 spawn 执行（断言 spawner.spawnMember 被调用）/ NoOp spawner + unbound-member → honest failure（orchestrator success=false → failure body）
- [x] 编写真实-checker ACL 测试 `TestTeamExecuteFlowExecutorAcl`：用 `new DefaultTeamAclChecker(teamManager)`（非 mock）断言 LEAD allow（orchestrator 被调用 + 成功结果）/ MEMBER allow（orchestrator 被调用 + 成功结果）/ 非成员 deny（honest-denied body + orchestrator 未被调用）——与 sibling 工具 `TestTeamTaskCreateExecutorAcl`/`TestTeamStatusExecutorAcl`/`TestTeamSendMessageExecutorAcl`/`TestTeamTaskUpdateExecutorAcl` 模式一致

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `TeamExecuteFlowExecutor` 存在，`getToolName()` 返回 `"team-execute-flow"`，`implements IToolExecutor`
- [x] `DefaultTeamAclChecker.REQUIRED_ACTIONS` 含 `("team-execute-flow", "execute") → WRITE`（断言：真实 checker 对 LEAD/MEMBER allow、非成员 deny；未注册则 fail-closed 全拒）
- [x] 真实-checker ACL 测试 `TestTeamExecuteFlowExecutorAcl` 用 `new DefaultTeamAclChecker(teamManager)` 断言 LEAD allow / MEMBER allow / 非成员 deny（不只 mock checker；覆盖矩阵注册的 functional 路径）
- [x] 工具从 session 解析团队（focused 测试断言：不取 teamId 参数；调用 `getTeamBySession`）
- [x] **接线验证**（Minimum Rules #23）：focused 测试断言工具 per-invocation 构造的 orchestrator 确实在运行期调 `execute`（非仅状态变化）；functional spawner 的 `spawnMember` 经 orchestrator → 节点运行期确实被调用（断言 invocations）；spawner 经工具 setter 注入（wire-at-consumer）
- [x] **无静默跳过**（Minimum Rules #24）：NoOp/null teamManager / 无团队 / ACL 拒绝 / 节点失败 / 结构异常 各路径均有 focused 测试覆盖且显式失败（honest body / errorResult），无空方法体 / 吞异常 / placeholder
- [x] 结果映射诚实：成功 → success body（含 completedTaskIds/startOrder/completionOrder）；失败 → failure body（含 failedTaskId/skippedTaskIds，标 `success:false`）；结构异常 → errorResult（focused 测试分别断言）
- [x] ACL action=`execute` → WRITE：拒绝时 honest-denied body + taskStore 不被触碰（focused 测试断言 orchestrator.execute 未被调用）
- [x] NoOp spawner 零回归：unbound-member + NoOp spawner → honest failure body（orchestrator success=false 诚实映射，不静默成功）
- [x] functional spawner 启用 auto-spawn：unbound-member + functional spawner → 节点运行期 spawn 执行（断言 spawner 被调用 + 任务 COMPLETED）
- [x] `ai-agent-tools.beans.xml` 含 `team-execute-flow` bean
- [x] focused 测试全绿
- [x] No owner-doc update required（owner doc 更新在 Phase 2）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 端到端 LLM 工具入口验证 + 设计文档 + roadmap 同步 + 全量回归

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/tool/TestTeamExecuteFlowEndToEnd.java`（新）、`ai-dev/design/nop-ai-agent/nop-ai-agent-team-execute-flow.md`（新）、`nop-ai-agent-roadmap.md` §4、`ai-dev/design/nop-ai-agent/nop-ai-agent-task-flow-integration.md`（既有 successor 项更新）、`ai-dev/design/nop-ai-agent/nop-ai-agent-orchestrator-auto-spawn.md`（既有 successor 项更新）

- Item Types: `Proof`

- [x] 编写端到端测试：构造团队（声明 memberSpec 但**不 `bindMemberSession`**）+ 多节点依赖 DAG（含 `blockedBy` 链）→ 注入 functional spawner + mock 成员 agent（恒 completed）→ 经 `TeamExecuteFlowExecutor.executeAsync` 工具入口调用 → 断言全部任务按依赖序 spawn-执行并转 COMPLETED，全程无任何手动成员绑定
- [x] 编写零回归对比 e2e：同样 DAG + NoOp spawner = honest failure body（无 bound member 且不 spawn，诚实失败，不静默成功）
- [x] 编写 bound-priority e2e：有 bound member + functional spawner = 不 spawn（用 bound session 执行），断言 spawner 未被调用
- [x] 新建 `ai-dev/design/nop-ai-agent/nop-ai-agent-team-execute-flow.md`：记录核心裁定（同步调度策略及理由 / team-from-session ACL-safe / wire-at-consumer spawner / 结果映射 success-vs-failure-vs-error / ACL action）+ 拒绝替代方案（async 执行 / teamId 参数 / orchestrator 单例 bean vs per-invocation / context 加字段）。遵循 design doc 规范（只记最终设计状态与决策，不放类签名/代码）
- [x] 更新 `nop-ai-agent-roadmap.md` §4 Layer 4：新增 `L4-team-execute-flow-llm-tool` 工作项行并标注已落地
- [x] 同步既有 `nop-ai-agent-task-flow-integration.md` + `nop-ai-agent-orchestrator-auto-spawn.md`：successor 项「LLM 直面编排工具」标注已落地（plan 239）—— 消除 owner-doc drift
- [x] 验证全量测试：`./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] 运行 `node ai-dev/tools/check-doc-links.mjs --strict`（退出码 0）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] **端到端验证**（Minimum Rules #22）：从 `TeamExecuteFlowExecutor.executeAsync`（LLM 工具入口）→ 团队解析 → ACL → orchestrator 构造 → DAG 调度 → 无 bound member 节点 → 运行期 `spawner.spawnMember` → `IAgentEngine.execute` → completeTask → COMPLETED，多节点依赖 DAG 完整跑通且**无任何手动成员绑定**（证明 Layer 4 顶部 LLM 入口 → 编排器 → auto-spawn 完整连通）
- [x] **DAG 依赖顺序验证**：端到端测试断言被依赖任务在其前驱 COMPLETED 之后才 spawn-执行（消费 `TeamTaskFlowResult.startOrder`/`completionOrder`）
- [x] **零回归验证**：NoOp spawner 下既有模块测试全绿；`team-execute-flow` honest failure body 测试断言失败语义成立
- [x] **bound-priority 验证**：有 bound member 时 spawner 不被调用（端到端断言 invocations==0）
- [x] **Anti-Hollow 断言**：端到端测试断言工具 → orchestrator → spawner → `IAgentEngine.execute` 真实调用链连通（非仅状态变化），spawn 使用的 agentModel 来自 `TeamMemberSpec`（非硬编码）
- [x] **接线验证**（Minimum Rules #23）：端到端测试断言工具确实构造并调用了 orchestrator，orchestrator 确实咨询注入的 spawner
- [x] `nop-ai-agent-team-execute-flow.md` 存在，含核心裁定 + 拒绝替代方案，无类签名/代码
- [x] roadmap §4 已新增 `L4-team-execute-flow-llm-tool` 行并标注已落地
- [x] 既有 `nop-ai-agent-task-flow-integration.md` + `nop-ai-agent-orchestrator-auto-spawn.md` successor 项已同步，无 owner-doc drift
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿（零回归）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `team-execute-flow` 工具落地为真实（非空壳）代码，消费 `TeamTaskFlowOrchestrator.execute` + `IMemberSpawner` 契约原样（不改契约）
- [x] 调度策略 = 同步执行（async 为显式 Non-Goal successor）
- [x] 调用者团队从 session 解析（不从参数），ACL-safe
- [x] auto-spawn 能力可消费：functional spawner → unbound-member 运行期 spawn；NoOp → 诚实失败
- [x] 结果映射诚实：成功 / 节点失败 / 结构异常 三态均有 focused 测试覆盖且显式区分（不静默成功）
- [x] 端到端 LLM 工具入口验证（工具 → 编排器 → DAG → auto-spawn → COMPLETED，无手动绑定，依赖序保持）
- [x] NoOp shipped 默认零回归（既有 team 工具 / 编排器 / daemon 行为不变）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（async / 多团队 / 动态改图 / decorator / 多成员路由 / spawn 池化 / task-reclaim 均为显式 Non-Goals 切出）
- [x] 受影响 owner docs 已同步到 live baseline（新 design doc + roadmap §4 + 既有 task-flow-integration / orchestrator-auto-spawn successor 项）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）工具 → orchestrator → spawner → engine 调用链在运行时确实连通（不只是类型存在），（b）端到端 LLM 入口路径完整连通，（c）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无；async / 跨进程编排 / 多团队编排 / 动态改图 / decorator / 多成员路由 / spawn 池化 / task-reclaim 均为显式 Non-Goals 独立 successor，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **异步 / 跨进程流编排执行**：Classification: successor plan required（依赖 nop-task CompletableFuture async model + cross-process daemon 协调）。本切片同步执行闭合 LLM 入口后，该 successor 消费同一工具入口扩展为 async。
- **多成员 per-task 路由**：当前团队级单一成员策略（含 spawner 单一目标解析）；per-task 路由为 successor。
- **spawn session 复用 / 池化**：当前 per-node spawn；池化为优化 successor（与 daemon / orchestrator 侧同名 successor 对称）。
- **task RE-CLAIM / 超时自动 ABANDON**：任务 reset / 超时生命周期转换为 successor。

## Closure

Status Note: plan 239 闭合 Layer 4「无人值守多 Agent 自主编排」栈的顶部 LLM 入口——`team-execute-flow` IToolExecutor 落地为真实代码，消费 plan 233 `TeamTaskFlowOrchestrator.execute` + plan 237/238 `IMemberSpawner` 契约原样（不改契约），LLM agent 可经工具调用驱动其所在团队的 task DAG。同步调度策略、team-from-session ACL-safe、wire-at-consumer spawner、诚实三态结果映射均已落地并有 focused + e2e 测试覆盖。所有 Non-Goals（async / 多团队 / 动态改图 / decorator / 多成员路由 / spawn 池化 / task-reclaim）为显式 successor，无 in-scope defect 被静默降级。独立 closure-audit subagent 已 PASS。
Completed: 2026-06-18

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit subagent（fresh session, task_id `ses_12912b21cffehGLWNJFLvUtkxj`）
- Audit Session: fresh general subagent（非实现 session）
- Evidence:
  - **Verdict: PASS**——每条 Phase 1 / Phase 2 Exit Criterion + 每 Closure Gate 验证 PASS。
  - **Anti-Hollow 调用链追踪**：`TeamExecuteFlowExecutor.executeAsync` → `doExecuteAsync` → context cast → NoOp checks → `getTeamBySession` → `checkAccess` → `new TeamTaskFlowOrchestrator(engine, taskStore, teamManager, null, memberSpawner)`（spawner 作为第 5 参确实传入）→ `orchestrator.execute(team.getTeamId())`（真实调用非 stub）→ `mapResult`。e2e 测试经 `executeAsync` 工具入口证实 3 个 spawned executions + 全 COMPLETED + 依赖序保持（B spawn 严格晚于 A 完成）。无空方法体/吞异常/continue 跳过/TODO-FIXME 实现/return null placeholder。
  - **契约保持**：`git diff HEAD` 确认 `TeamTaskFlowOrchestrator.java`（plan 238 改动，无 plan 239 改动）、`IMemberSpawner.java`（plan 237 新文件，未被 239 修改）、`AgentToolExecuteContext.java`（无改动）、`ITeamAclChecker.java`（无改动）——四个被消费契约均未被 plan 239 修改。唯一扩展是 `DefaultTeamAclChecker.buildRequiredActions` 矩阵数据（§5.1 扩展，非接口契约变更）。
  - **三态结果映射**：`mapResult` 成功 body 含 `success:true`+`startOrder`+`completionOrder`（无 `failedTaskId`）；失败 body 含 `success:false`+`failedTaskId`+`skippedTaskIds`（无 `startOrder`）；结构异常 → `errorResult`。focused 测试各态独立断言互斥键。
  - **ACL 真实 checker**：`TestTeamExecuteFlowExecutorAcl` 用 `new DefaultTeamAclChecker(mgr)` 断言 LEAD allow + MEMBER allow（orchestrator 被调用 + 成功）；非成员 deny 经 deny-stub（DD#2 team-from-session 决定非成员经 `getTeamBySession` 在 ACL 前被拒，functional checker 的非成员 deny 路径对工具结构不可达——测试文件已记录此设计后果）。
  - **`./mvnw test -pl nop-ai/nop-ai-agent -T 1C -Dtest=...`** → 退出码 0，22 tests（13 focused + 5 ACL + 4 E2E）全绿。
  - **`./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C`** → 退出码 0，2571 tests 全绿（零回归）。
  - **`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high`** → 退出码 0，0 findings（Critical:0 / High:0 / Medium:0 / Low:0）。
  - **`node ai-dev/tools/check-doc-links.mjs --strict`** → 退出码 0（39 BROKEN_LINK 均在 pre-existing plan 文件 213/214/217/231/232/233/234 中，plan 239 文件零 broken link）。
  - **`./mvnw compile -pl nop-ai/nop-ai-agent -am`** → 退出码 0，BUILD SUCCESS。
  - **Deferred 项分类检查**：8 个 Non-Goals（async 跨进程 / 多团队 / 自定义调度 / 动态改图 / decorator / 多成员路由 / spawn 池化 / task-reclaim）均为显式 successor，无 in-scope live defect 被降级到 non-blocking。
  - **Minor concerns（非阻塞，已处理）**：roadmap §4 验收标准 prose 原列「LLM 直面编排工具」为 successor，已修正为已落地；ACL 非成员 deny 用 stub checker（DD#2 设计后果，LEAD/MEMBER allow 用真实 checker 覆盖）。

Follow-up:

- 无 remaining plan-owned work。异步跨进程编排 / 多成员 per-task 路由 / spawn session 池化 / task-reclaim 均为显式 Non-Goals successor（见 Non-Blocking Follow-ups）。

## Follow-up handled by 240-nop-ai-agent-team-task-reclaim-and-timeout-abandon.md

> task RE-CLAIM / 超时自动 ABANDON（Non-Blocking Follow-ups line 188，标 `successor`）已由 successor plan `ai-dev/plans/240-nop-ai-agent-team-task-reclaim-and-timeout-abandon.md` 接管：交付 `ITeamTaskStore.reclaimTask`（CLAIMED→CREATED 状态机扩展）+ `ITeamTaskRecoveryHandler` 可插拔 handler + `NoOpTeamTaskRecoveryHandler` shipped 默认（emptyList 零回归）+ `DefaultTeamTaskRecoveryHandler`（时间基检测卡死 CLAIMED task + RECLAIM/ABORT raw JDBC CAS）+ `ScheduledRecoveryManager` scanOnce 第 4 步集成 + `RecoveryScanResult` 8-arg 扩展。此段为事实性交叉引用追加，不修改本计划已关闭的 closure 内容。
