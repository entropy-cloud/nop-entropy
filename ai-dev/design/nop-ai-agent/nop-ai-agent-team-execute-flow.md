# LLM 直面编排工具设计（team-execute-flow：LLM agent 经工具调用驱动 TeamTaskFlowOrchestrator）

> Status: landed
> Last Reviewed: 2026-06-18
> Owner plan: `ai-dev/plans/239-nop-ai-agent-team-execute-flow-tool.md`（Work Item: `L4-team-execute-flow-llm-tool`）
> Related: `nop-ai-agent-task-flow-integration.md`（plan 233 交付 `TeamTaskFlowOrchestrator` + `TeamTaskFlowResult`——本设计消费其 `execute` 契约）、`nop-ai-agent-orchestrator-auto-spawn.md`（plan 238 交付 orchestrator 侧 auto-spawn——本设计消费其 `IMemberSpawner` 接线）、`nop-ai-agent-member-auto-spawn.md`（plan 237 交付 `IMemberSpawner`/`NoOpMemberSpawner`/`DefaultMemberSpawner`）、`nop-ai-agent-roadmap.md` §4 Layer 4

## 1. 定位

交付 LLM 直面编排工具 `team-execute-flow`（`IToolExecutor` 实现），使 LLM agent（如 ReAct loop 内的 LEAD/MEMBER）可通过工具调用驱动 `TeamTaskFlowOrchestrator` 执行其所在团队的 task DAG。该工具是 Layer 4「无人值守多 Agent 自主编排」栈的**顶部 LLM 入口**——plan 233 交付同步编排能力、plan 236 交付自动调度守护、plan 237 交付成员 auto-spawn、plan 238 交付编排器侧 auto-spawn；本设计闭合最后一块缺口：LLM 经工具调用进入编排链路。

工具消费既有契约原样不改：`TeamTaskFlowOrchestrator.execute(teamId) → TeamTaskFlowResult`、`IMemberSpawner`、`AgentToolExecuteContext`（engine/teamManager/taskStore/teamAclChecker）、`ITeamAclChecker`。唯一扩展是 `DefaultTeamAclChecker` 矩阵新增一行（§5.1 矩阵数据扩展，非接口契约变更）。

## 2. 设计决策

### 决策 1：调度策略 = 同步执行

`TeamTaskFlowOrchestrator.execute(teamId)` 本身是同步的（经 nop-task `syncGetOutputs`）。工具调 `execute` 后将结果包装进 `CompletableFuture.completedFuture(mapResult(...))`。async 模型（CompletableFuture 链 / cross-process daemon）是独立 P1 successor，不属本切片。理由：orchestrator 已是同步；同步是使 LLM 入口可用的最小一步；async 需 nop-task async model（未落地）。

### 决策 2：调用者团队从 session 解析，不暴露 teamId 参数

工具经 `agentCtx.getTeamManager().getTeamBySession(callerSessionId)` 解析调用者所在团队，与 `team-status`/`team-task-create`/`team-task-update`/`team-send-message` 全部既有 team 工具一致。保证 LLM 只能编排自己所属团队（ACL-safe）；消除 LLM 误传他人 teamId 的风险；与既有 team 工具模式零分歧。`orchestrator.execute(team.getTeamId())`。

### 决策 3：工具经 wire-at-consumer 注入 `IMemberSpawner`，per-invocation 构造 orchestrator

工具持有一个 `IMemberSpawner` 字段（setter 注入，null-safe → `NoOpMemberSpawner.noOp()`，镜像 plan 238 决策5 + plan 237 daemon 接线惯例）。per-invocation：工具从 context 取 engine/teamManager/taskStore，结合注入的 spawner，构造 `new TeamTaskFlowOrchestrator(engine, taskStore, teamManager, null, spawner)`（taskFlowManager 传 null → orchestrator 内部回退 `TaskFlowManagerImpl`）。

理由：orchestrator 构造轻量、服务来自 context（per-invocation）、spawner 是共享 singleton（wire-at-consumer）；与既有 team 工具「服务从 context 取」模式一致；不需给 `AgentToolExecuteContext` 加字段；NoOp 默认零回归 + functional spawner 启用 auto-spawn。

### 决策 4：结果映射 `TeamTaskFlowResult` → `AiToolCallResult`，诚实不静默

三态映射，严格区分「DAG 结果」与「工具自身出错」：

- **DAG 成功**（`result.isSuccess() == true`）→ `AiToolCallResult{status="success"}`，body 为 JSON（`success:true, completedTaskIds, startOrder, completionOrder`）。
- **DAG 节点失败**（`result.isSuccess() == false`，含 NoOp spawner 下 unbound-member 诚实失败）→ `AiToolCallResult{status="success"}`，body 为 JSON（`success:false, failedTaskId, skippedTaskIds, completedTaskIds`）。**status="success"**（与 team 工具 honest-denied / honest-not-enabled 一致：ReAct loop 不因「工具执行了但 DAG 未全成功」而 abort；LLM 可读 failure body 决策重试 / 放弃）。body 明确标 `success:false`，**不**静默成功。
- **结构异常**（`NopAiAgentException`：空任务集 / 未知团队 / 成环 blockedBy，由 `orchestrator.execute` 抛出）→ 工具内 try/catch 捕获 → `errorResult`（技术故障，区别于 DAG 结果）。

理由：区分「工具执行了，DAG 结果是 success/failure」（结果体）与「工具自身出错 / 结构不合法」（errorResult）；failure body 诚实暴露 failedTaskId/skippedTaskIds（Minimum Rules #24，不吞信息）。

### 决策 5：ACL action=`execute` → WRITE 权限，需在 `DefaultTeamAclChecker` 注册矩阵条目

工具在团队解析后、orchestrator 调用前，调 `agentCtx.getTeamAclChecker().checkAccess(teamId, sessionId, "team-execute-flow", "execute")`。`DefaultTeamAclChecker` 对未知 `(toolName, action)` 元组 fail-closed deny，因此必须在 `buildRequiredActions` 新增 `("team-execute-flow", "execute") → TeamAclAction.WRITE`。WRITE 权限经 §5.1 角色矩阵：LEAD = ADMIN 全通过、MEMBER 通过 WRITE、非成员拒绝。NoOp shipped 默认（`NoOpTeamAclChecker`）放行（零回归）。拒绝时返回 honest-denied 结果（status="success" + denial body；taskStore 不被触碰、orchestrator 不被调用）。

理由：编排执行触发任务状态变更（CLAIMED→COMPLETED），等同 WRITE。

## 3. 拒绝的替代方案

| 被拒绝方案 | 理由 |
|-----------|------|
| 异步执行（CompletableFuture 链 / cross-process daemon） | orchestrator 已是同步；async 需 nop-task CompletableFuture async model（未落地）。同步是使 LLM 入口可用的最小一步。async 为独立 P1 successor。 |
| 暴露 teamId 参数（LLM 显式传 teamId） | 与全部既有 team 工具模式分歧；引入 LLM 误传他人 teamId 的风险。team-from-session 保证 LLM 只能编排自己所属团队（ACL-safe）。 |
| orchestrator 作单例 bean 注入工具（而非 per-invocation 构造） | orchestrator 所需服务（engine/taskStore/teamManager）来自 per-invocation context（每次工具调用可能不同），单例 bean 无法承载。per-invocation 构造轻量、与既有 team 工具「服务从 context 取」模式一致。 |
| 给 `AgentToolExecuteContext` 新增 orchestrator 字段 | orchestrator 所需服务已在 context（engine/teamManager/taskStore）；新增字段会污染 context 公共契约且无必要。工具 per-invocation 构造 + spawner wire-at-consumer 已足够。 |
| 把 spawner 注入 `AgentToolExecuteContext`（而非工具自身） | spawner 的唯一 consumer 是工具构造的 orchestrator（per-invocation），不是 engine dispatch 路径。wire-at-consumer（镜像 plan 238/237 daemon 接线惯例 + `IResourceGuard`→TeamManager 范式）使所有权清晰。 |
| 节点失败映射为 errorResult（而非 status="success" + failure body） | errorResult 表示「工具自身出错 / 技术故障」，会使 ReAct loop abort。DAG 节点失败是业务结果（工具执行了，DAG 未全成功），应映射为结果体让 LLM 决策重试 / 放弃。与 team 工具 honest-denied / honest-not-enabled 一致。 |

## 4. 边界（Non-Goals，均为独立 successor）

- **异步 / 跨进程流编排执行**：独立 carry-over（`L4-async-cross-process-orchestration`，P1），需 nop-task CompletableFuture async model + cross-process daemon 协调。本切片同步执行。
- **多团队 / 跨团队编排**：工具只编排调用者所属团队（从 session 解析）。
- **自定义调度策略 / 优先级 / 并行委派就绪集**：消费 orchestrator 现有同步依赖序调度。
- **运行时动态增删图节点 / 改图**：独立 carry-over（`L4-dynamic-graph-edit`）。
- **重试 / 超时 / rate-limit decorator**：独立 carry-over（`L4-nop-task-decorator`）。结构异常与节点失败均诚实上报，不内建重试。
- **多成员 per-task 路由**：消费 orchestrator 现有团队级单一成员策略（含 spawner 的单一目标解析）。per-task 多成员路由为 successor。
- **spawn session 复用 / 池化**：消费 plan 237/238 的 per-node/per-task spawn 语义。池化为优化 successor。
- **task RE-CLAIM / 超时自动 ABANDON**：独立 carry-over（`L4-task-reclaim-timeout-abandon`）。
- **修改 `TeamTaskFlowOrchestrator` / `IMemberSpawner` / `AgentToolExecuteContext` / `ITeamAclChecker` 接口公共契约**：消费原样。在 `DefaultTeamAclChecker.buildRequiredActions` 注册新 `(toolName, action)` 条目属扩展 §5.1 矩阵数据，不属修改接口契约。

## 5. 落地证据

- 新组件：`io.nop.ai.agent.tool.TeamExecuteFlowExecutor implements IToolExecutor`（tool name `team-execute-flow`）。消费 `TeamTaskFlowOrchestrator.execute` + wire-at-consumer `IMemberSpawner`（null-safe → NoOp shipped 默认）。
- ACL 矩阵扩展：`DefaultTeamAclChecker.buildRequiredActions` 新增 `("team-execute-flow", "execute") → TeamAclAction.WRITE`（functional checker 下 LEAD/MEMBER allow、非成员 deny；NoOp shipped 默认放行零回归）。
- 工具注册：`ai-agent-tools.beans.xml` 新增 `<bean id="ai-agent-tools:team-execute-flow" class="io.nop.ai.agent.tool.TeamExecuteFlowExecutor"/>`（auto-collect 路径）。
- 诚实结果映射（三态）：成功 → success body（含 completedTaskIds/startOrder/completionOrder）；节点失败 → failure body（含 failedTaskId/skippedTaskIds，标 `success:false`，不静默成功）；结构异常 → errorResult。focused 测试分别覆盖三态。
- ACL denial honest 路径：denial 返回 status="success" + denial body（allowed=false/toolName/action/reason），orchestrator 不被调用、taskStore 不被触碰（focused 测试 + 真实-checker ACL 测试断言）。
- 端到端 LLM 工具入口验证（Anti-Hollow #22）：声明团队（声明 memberSpec 但**不 `bindMemberSession`**）+ 多节点依赖 DAG（线性 A→B→C + 菱形 A→{B,C}→D）→ 注入 functional `DefaultMemberSpawner` + mock 成员 agent → 经 `TeamExecuteFlowExecutor.executeAsync` 工具入口调用 → 全部任务按依赖序 spawn-执行并转 COMPLETED，**全程无任何手动成员绑定**。Anti-Hollow 执行序证据：B spawn 严格晚于 A 完成，C spawn 严格晚于 B 完成，D spawn 晚于 B 和 C 完成。
- 零回归对比验证：同样 DAG + NoOp spawner = honest failure body（无 bound member 且不 spawn，诚实失败 `success:false`，不静默成功）。
- bound-priority 验证：有 bound member + functional spawner = spawner 不被调用（invocations==0），dispatch 用 bound session（无 `spawned-` 前缀）。
- 接线验证（Wiring #23）：focused 测试断言工具 per-invocation 构造的 orchestrator 确实运行期调 `execute`；functional spawner 的 `spawnMember` 经 orchestrator → 节点运行期确实被调用（断言 invocations）；spawner 经工具 setter 注入（wire-at-consumer，null-safe → NoOp）。
- Anti-Hollow 断言：spawned execution 携带 teamTaskId/teamId/daemonSessionId 元数据；spawned agentName 来自 `TeamMemberSpec.agentModel`（断言非硬编码）；每次 spawn 新 session（per-node 非复用）。
- 全量回归：`./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿（2571 tests，零回归）。
