# nop-task DAG 集成设计（团队任务 → nop-task 工作流图 + 依赖序同步编排）

> Status: landed
> Last Reviewed: 2026-06-18
> Owner plan: `ai-dev/plans/233-nop-ai-agent-task-flow-dag-integration.md`（Work Item: `L4-nop-task-dag-integration`）
> Related: `nop-ai-agent-multi-agent.md` §「Agent 作为 Nop Flow 节点类型」、`nop-ai-agent-actor-runtime-vision.md` §8.2 / §2.2（ITaskStep + Decorator）、`nop-ai-agent-orchestrator-auto-spawn.md`（plan 238 编排器 auto-spawn 集成，扩展决策 4 无已绑定成员路径）

## 1. 定位

把 nop-ai-agent 的**团队任务**（`TeamTask` + `blockedBy` 依赖列表）组织为 **nop-task 工作流 DAG 图**，由 nop-task 的 DAG 引擎负责拓扑排序、环检测、依赖序调度，并由一个**依赖序同步编排器**驱动端到端执行：每个图节点委派给一个已绑定的成员 agent 执行，节点成功后标记对应团队任务 COMPLETED。

这是 roadmap §4 Layer 4 验收标准「多 Agent 任务可以通过 Flow / Task 组织」的核心落地切片。本设计回应 carry-over 中反复标注的「nop-task 模块集成裁定」决策点，给出最终裁定。

团队任务持久化、团队生命周期、成员绑定、团队 ACL 在本设计之前已由既有 plan（225 / 227 / 228 / 230 / 231）落地。本设计在其之上叠加 DAG 编排能力，**不改既有契约**（编排器只读消费 `IAgentEngine` / `ITeamTaskStore` / `ITeamManager`）。

## 2. 设计决策

### 决策 1：集成定位 — nop-task 作为 DAG 编排引擎，而非持久化层

nop-ai-agent 新增 `nop-task-core` 编译依赖（单向，`nop-ai-agent → nop-task-core`，无环）。团队任务在编排时被映射为 nop-task **内存图模型**；团队任务持久化的唯一事实源保持为 `DbTeamTaskStore` / `ITeamTaskStore`，**不采用** nop-task 的 `DaoTaskStateStore`。

理由：
- 团队任务已有可用且跨进程的 DB 持久化（`DbTeamTaskStore` raw JDBC + 条件 UPDATE CAS）。
- nop-task 的 `DaoTaskStateStore` 是无实现的 stub（每个方法返回 null/空，ORM 实体 `NopTaskInstance`/`NopTaskStepInstance` 运行时↔ORM 桥未接通），把团队任务状态迁入会导致空壳风险。
- 取 nop-task 之长（成熟的 DAG 拓扑 / 环检测 / 依赖序调度），避 nop-task 之短（持久化 stub）。编排运行使用 nop-task 的内存 `DefaultTaskStateStore`（`saveState=false`），不触碰其 DB 持久化路径。

### 决策 2：依赖映射 — 每个团队任务一个图节点，blockedBy → nop-task waitSteps

- 每个团队任务映射为一个图节点（节点名 = taskId）。
- `TeamTask.blockedBy`（仅集合内引用）映射为该节点的 `waitSteps`（等待前置成功）。引用集合外未知 taskId 的 dangling 边不构成图边。
- `enterSteps` = 无集合内 blockedBy 的任务（DAG 源）；自然 sink = 不被任何任务依赖的任务。
- **环检测**经 nop-task 的 `GraphStepAnalyzer`（构建拓扑 `Dag` + `containsLoop()`）。成环的 `blockedBy`（线性 A→B→A、自环 A→A、三节点环）被**拒绝并快速失败**，而非静默存储。这闭合了「今日团队任务存储静默接受成环 blockedBy」的真实 gap（Minimum Rules #24 精神）。

### 决策 3：执行模型 — 依赖序同步编排

编排器经 nop-task 运行时执行图：
- `ITaskFlowManager.newTaskRuntime(task, saveState=false, svcCtx)` 创建运行时（内存 state store）。
- `ITask.execute(taskRt).syncGetOutputs()` 同步执行整图（编排器阻塞至整图完成）。
- nop-task 的 `GraphTaskStep` 调度器保证依赖序：B blockedBy A 则 A 的节点完成后 B 的节点才执行；菱形 A→{B,C}→D 中 B、C 在 A 完成后方可并行，D 在 B、C 均完成后才执行。
- 每个图节点的步骤委派给对应成员 agent（经 `IAgentEngine.execute`，同步 join），节点成功后经 `ITeamTaskStore.completeTask` 标记该团队任务 CLAIMED→COMPLETED。

理由：
- `syncGetOutputs()` 提供可端到端验证的同步语义（Anti-Hollow #22）——可断言「B 的成员 agent 执行未早于 A 完成」。
- 委派成员 agent 与既有 `call-agent` / 团队执行模式一致。
- 分钟/小时级长时异步编排、跨进程图执行为独立 successor（异步团队通信工具 + DB 共享任务已为该场景存在）。

### 决策 4：节点 → 成员 agent 解析 — 消费已绑定成员，无已绑定成员在图运行期 auto-spawn（plan 238）

编排器对每个任务节点执行时，经 `ITeamManager` 反查团队**已绑定**成员会话，将任务委派给对应成员 agent 执行。解析策略（在图**构建期**运行）：优先 `TeamTask.claimedBy`；未 claim 时按团队成员花名册解析（优先 MEMBER 角色，回退任意已绑定成员）。**已绑定成员的团队：bound 路径逐行不变（零回归）**。

**无已绑定成员的节点（plan 238 扩展）**：`resolveMember` 不再在构建期 fail-fast，而是返回 null，构建期为该节点选择运行期 spawn 执行型 step；真正的成员 agent spawn 发生在**图运行期**（该节点被 nop-task DAG 调度器触发时），经注入的 `IMemberSpawner`（plan 237 契约复用原样）基于团队声明式 `TeamMemberSpec.agentModel` spawn 一个成员 agent 执行该任务。spawn 在节点运行期而非构建期发生，以保持 DAG 依赖顺序（被依赖任务仍在其前驱完成后才 spawn-执行）。`IMemberSpawner` 经 orchestrator 构造器/setter 注入（wire-at-consumer，null-safe→`NoOpMemberSpawner` shipped 默认）。

**诚实失败契约**：无已绑定成员 + NoOp shipped 默认 spawner = 节点运行期诚实 fail（`NO_SPAWN`→抛异常→图短路→`execute()` 返回 `TeamTaskFlowResult{success=false}`，失败任务留 CLAIMED 非 abandon）。这是相对 plan 233 baseline 的**有意 API 契约变更**：pre-238 无已绑定成员 = `execute()` 在构建期**抛 `NopAiAgentException`**；plan 238 后 = 运行期 step 内抛异常、被既有 try/catch 捕获 → `execute()` 返回 `TeamTaskFlowResult{success=false}`（业务语义「无已绑定成员 = 诚实失败」不变，仅可观测形态 throw→return-failed-result）。详见 `nop-ai-agent-orchestrator-auto-spawn.md`。

## 3. 合成 sink 节点（拓扑完整性）

nop-task 图在其任一 **exit 节点**完成时即 resolve 整图 future 并取消其余运行中节点。一个团队任务集若含多条独立链（多个自然 sink），直接映射会导致「第一个 sink 完成即取消其余任务」。

为保证**无论 DAG 形状如何，每个团队任务都执行**，编排器把所有真实任务节点标记为非 exit，并追加**一个合成 sink 节点**：其 `waitSteps` = 全部真实 taskId，是图的唯一 exit。该 sink 是合法的拓扑终止标记（无后继，不可能成环），不是 Minimum Rules #24 意义上的「静默 no-op」。整图 future 仅在该 sink 运行时（即所有真实任务完成后）resolve 一次。

## 4. 失败传播

某节点成员 agent 执行失败（抛异常或返回非 completed 终态）时，节点步骤抛出异常。该异常经 `GraphTaskStep` 调度器传播：整图 future 异常完成、取消令牌触发、后继节点的 `waitFuture` 异常完成 → 后继节点不执行（依赖序失败传播）。编排器捕获异常并返回**失败结果**（`success=false` + 失败 taskId + 被跳过的后继 taskId），而非静默成功。失败任务保留在 CLAIMED 态（不自动 abandon），重试/恢复为独立 successor（`L4-retry-recovery-mode`）。

**plan 238 补充**：无已绑定成员节点的运行期 spawn 执行（`SpawnMemberAgentTaskStep`）遵循同一失败模型——spawn 失败（DISPATCHED 非 completed / NO_SPAWN / SPAWN_FAILED / spawner 抛异常）= 抛异常 + 任务留 CLAIMED（非 daemon 的 abandon），与 bound-member 节点（`MemberAgentTaskStep`）失败后任务状态一致。

## 5. 拒绝的替代方案

| 被拒绝方案 | 理由 |
|-----------|------|
| 采用 nop-task `DaoTaskStateStore` 持久化团队任务 | `DaoTaskStateStore` 是无实现 stub（每方法返回 null/空），运行时↔ORM 桥未接通。迁移会导致空壳风险。团队任务持久化保持 `DbTeamTaskStore` 唯一事实源。 |
| 自行重写 DAG（拓扑排序 + 环检测 + 调度） | 重复造轮子，且 nop-task 已有成熟、被下游（nop-ai-coder / nop-batch-dsl / nop-cli-core）验证的 DAG 引擎。本设计明确消费 nop-task 的 `GraphStepAnalyzer`（环检测）与 `GraphTaskStep`（运行时调度），非自建。 |
| 异步/长时/跨进程流编排执行（CompletableFuture 非阻塞 + 跨进程图状态） | 本切片目标是可端到端验证的依赖序同步语义。长时异步 / 跨进程为独立 successor（异步团队通信工具 + DB 共享任务已覆盖该场景基础设施）。 |
| 用 nop-task `<simple bean=.../>` 步骤 + 全局 BeanContainer 解析每个任务的委派 bean | nop-task 的 simple 步骤在**构建期**经全局 `BeanContainer.instance().getBean(bean)` 解析，要求每个动态团队任务注册全局 bean，侵入单例容器、不利于测试与并发。本设计改为在编排器内**直接构造运行时 `GraphTaskStep` + 每节点委派步骤**，同样真实经 nop-task 运行时调度（`ITask.execute` / `GraphTaskStep`），但避免全局 bean 注册。 |
| nop-task decorator（retry/timeout/rate-limit）接入 | vision §2.2 提及 ITaskStep + Decorator 内置 retry/timeout/rate-limit。本切片只交付 DAG 拓扑 + 同步编排，不接入 decorator 体系（团队任务重试/超时已有独立 successor `L4-retry-recovery-mode` / `L4-task-lifecycle-extras`）。 |

## 6. 边界（Non-Goals，均为独立 successor）

- ~~`blockedBy` 自动调度守护进程（定时扫描就绪任务自动 claim/派发）~~ **已落地（plan 236 / `L4-blockedBy-resolution-engine`，详见 `nop-ai-agent-task-scheduler-daemon.md`）**。
- ~~auto-spawn 成员 agent（守护进程侧，未绑定成员时 spawn 执行）~~ **已落地（plan 237 / `L4-auto-spawn-member-agent`，详见 `nop-ai-agent-member-auto-spawn.md`）**。
- ~~编排器（`TeamTaskFlowOrchestrator`）auto-spawn 集成（无已绑定成员节点在图运行期 spawn 执行）~~ **已落地（plan 238 / `L4-orchestrator-auto-spawn-integration`，详见 `nop-ai-agent-orchestrator-auto-spawn.md`）**。
- 异步/长时/跨进程流编排执行 — successor。
- ~~LLM 直面编排工具（如 `team-execute-flow`）~~ **已落地（plan 239 / `L4-team-execute-flow-llm-tool`，详见 `nop-ai-agent-team-execute-flow.md`）**。
- nop-task decorator（retry/timeout/rate-limit）接入 — successor。
- 运行时动态增删图节点 / 改图 — successor。

## 7. 落地证据

- 环检测 / 就绪-阻塞拓扑查询：`TeamTaskGraphBuilder` + `TeamTaskTopology`（Phase 1，真实消费 `GraphStepAnalyzer`）。
- 依赖序同步编排器：`TeamTaskFlowOrchestrator`（Phase 2，真实经 `ITaskFlowManager.newTaskRuntime` + `ITask.execute(...).syncGetOutputs()` 执行 `GraphTaskStep`）。
- 端到端验证：线性 A→B→C（断言 B 启动严格晚于 A 完成）、菱形 A→{B,C}→D（B/C 在 A 后、D 在 B/C 后）、失败传播（节点失败→后继不执行→诚实失败结果）。
- 全量回归：`./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿。
