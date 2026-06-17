# 233 nop-ai-agent nop-task DAG 集成（团队任务 → nop-task 工作流图节点 + 依赖序同步编排）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L4-nop-task-dag-integration

> Last Reviewed: 2026-06-17
> Source: carry-over from `ai-dev/plans/227-nop-ai-agent-team-task-update.md`（Non-Goals「nop-task DAG 集成」+ Non-Blocking Follow-ups 第三条：「将团队任务映射为 nop-task 工作流 DAG 节点。Classification: successor plan required（依赖 nop-task 模块集成裁定）」；同一 successor 亦在 plans 229/230/231/232 的 Non-Goals 中显式延期）；`ai-dev/design/nop-ai-agent/nop-ai-agent-multi-agent.md:184`（设计意图：「Agent 作为 Nop Flow 的节点类型，通过 Flow 图定义多 Agent 编排逻辑」）；`ai-dev/design/nop-ai-agent/nop-ai-agent-actor-runtime-vision.md:35`（「ITaskStep + Decorator (nop-task) | Agent 编排的 DAG 执行，内置 retry/timeout/rate-limit」）+ `:401`（nop-task DAG 集成仍为显式 successor）；`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 4 验收标准 line 259（「多 Agent 任务可以通过 Flow / Task 组织」——完整 Flow/Task 组织能力中 nop-task DAG 集成仍为 successor）
> Related: `225`（交付 `ITeamTaskStore` 契约 + `TeamTask.blockedBy` 字段——本计划消费 blockedBy 作为 DAG 边）、`227`（交付 `team-task-update` 状态机 + `DbTeamTaskStore`——本计划在其持久化层之上叠加 DAG 编排，不改持久化契约）、`228`（Team ACL——本计划编排执行复用既有 teamManager 反查调用者团队）、`231`（声明式团队自动绑定——本计划编排消费已绑定成员）

## Purpose

把 nop-ai-agent 的团队任务从"扁平记录 + 状态机 + blockedBy 仅存储不解析"扩展为"团队任务可被组织为 nop-task 工作流 DAG 图节点，依赖关系经 nop-task DAG 引擎解析（拓扑排序 + 环检测 + 依赖序调度），并支持依赖序同步编排执行（每个图节点委派给成员 agent 执行并在成功时标记任务 COMPLETED）"。本计划闭合 roadmap §4 Layer 4 验收标准「多 Agent 任务可以通过 Flow / Task 组织」的剩余核心能力（Team ACL 已由 plan 228 闭合；nop-task DAG 集成 + blockedBy 解析为本计划）。本计划同时裁定 carry-over 反复标注的"nop-task 模块集成裁定"设计决策。

## Current Baseline

基于 live repo 核对（`nop-ai/nop-ai-agent/src/main` + `nop-task/nop-task-core`，2026-06-17）：

- **`TeamTask` 不可变数据对象含 `blockedBy: List<String>`**（plan 225/227 ✅）：字段为依赖任务 ID 列表，`getBlockedBy()` 返回不可变视图。`io.nop.ai.agent.team.TeamTask:47,114-116`。**blockedBy 仅存储不解析**——`ITeamTaskStore.java:21` Javadoc 明确「stored verbatim but not resolved」。
- **`TeamTaskStatus` 四态状态机**（plan 227 ✅）：CREATED → CLAIMED → COMPLETED / CREATED|CLAIMED → ABANDONED。`io.nop.ai.agent.team.TeamTaskStatus`。
- **`ITeamTaskStore` 契约 = create + 3 读 + 3 状态转换**（plan 227 ✅）：`createTask` / `getTask` / `getTasksByTeam` / `getTasksByCreator` / `claimTask` / `completeTask` / `abandonTask`，转换返回 `Optional<TeamTask>`（CAS-empty 非异常）。`io.nop.ai.agent.team.ITeamTaskStore`。
- **`InMemoryTeamTaskStore` + `DbTeamTaskStore` 已落地**（plan 227 ✅）：DbTeamTaskStore raw JDBC + 构造期 `initSchema` + 条件 UPDATE on STATUS CAS，跨进程共享。团队任务持久化层**已完整且跨进程可用**。
- **团队工具 + 引擎接线已落地**（plan 225/227/228 ✅）：`team-task-create` / `team-task-update` / `team-status` / `team-send-message` 四个 IToolExecutor + `AgentToolExecuteContext.getTeamTaskStore()` / `getTeamManager()`（`AgentToolExecuteContext.java:413,424`）+ `DefaultAgentEngine` teamManager/teamTaskStore 字段。
- **零 nop-task 集成**：`nop-ai-agent/pom.xml` 依赖仅为 nop-ai-toolkit / nop-ai-core / nop-message-core / nop-dao（pom.xml:18-34），**无 nop-task 依赖**。grep `TaskDag|ITaskDag|TaskFlow|ITaskFlowManager|GraphTaskStep` 在 `nop-ai/nop-ai-agent/src/main` 返回 0 命中。
- **团队任务零环检测**：当前 `blockedBy` 可任意填写，包括成环（A.blockedBy=B 且 B.blockedBy=A）。createTask 不校验，store 不解析——成环任务被静默存储（违反 Minimum Rules #24 精神的潜在 gap：缺失能力静默接受而非快速失败）。
- **nop-task 模块成熟 DAG 引擎已存在**（`nop-task/nop-task-core`）：
  - `TaskFlowModel`（`implements IGraphTaskStepModel`，本身就是图步骤）+ `GraphTaskStepModel`（DAG 容器步骤，`enterSteps`/`exitSteps` 入出口节点集）。
  - 每个 `TaskStepModel` 声明依赖边：`waitSteps: Set<String>`（等待前置成功）+ `waitErrorSteps`（等待前置失败）。`_TaskStepModel.java` waitSteps 字段 + `TaskStepModel.addWaitStep`。
  - **环检测**：`GraphStepAnalyzer.analyze` 构建拓扑 `Dag` + `containsLoop()`，成环抛 `ERR_TASK_GRAPH_STEP_CONTAINS_LOOP`。`TaskFlowModel.init()` 经 `TaskFlowAnalyzer.analyze` 触发分析。
  - **运行时调度**：`GraphTaskStep` 把 waitSteps 编排为 `CompletableFuture`，enter 节点先跑、前置完成后继节点才跑（依赖序调度）。
  - **执行入口**：`ITaskFlowManager.getTask/parseTask` → `ITask`，`taskFlowManager.newTaskRuntime(task, saveState, svcCtx)` → `ITaskRuntime`，`task.execute(taskRt).syncGetOutputs()` 同步获取结果。`ITaskStep.execute(ITaskStepRuntime) → TaskStepReturn` 是自定义步骤的唯一执行契约。
  - **自定义步骤委派**：`<simple bean="..."/>`（bean 实现 `ITaskStep`）是最直接的委派点；`<step customType="..."/>` 经 `TransformCustomStepHelper` 转 XPL。
- **nop-task DB 持久化是 stub（关键约束）**：`DaoTaskStateStore`（`nop-task-dao`）每个方法返回 null/空（`isSupportPersist()` 返回 true 但无实际实现），唯一可用的是 in-memory `DefaultTaskStateStore`。ORM 实体 `NopTaskInstance`/`NopTaskStepInstance`/`NopTaskDefinition` 已定义但运行时↔ORM 桥未接通。**因此团队任务持久化不能依赖 nop-task 持久化层**——本计划保留 `DbTeamTaskStore` 作为团队任务状态唯一事实源。
- **依赖方向安全**：`nop-task-core` 被 `nop-ai-coder` / `nop-batch-dsl` / `nop-cli-core` 等下游消费（explore 确认），不依赖 nop-ai-agent。`nop-ai-agent → nop-task-core` 为合法单向依赖，无环。
- **设计意图明确**：`nop-ai-agent-multi-agent.md:184`「Agent 作为 Nop Flow 的节点类型，通过 Flow 图定义多 Agent 编排逻辑」；vision `:35`「ITaskStep + Decorator (nop-task) | Agent 编排的 DAG 执行」。本计划是该意图的落地切片。

## Goals

- **nop-task-core 依赖引入**：`nop-ai-agent/pom.xml` 新增 `nop-task-core` 编译依赖（单向，无环）。
- **团队任务 → nop-task DAG 图模型桥**：一个桥接组件从某团队的 `getTasksByTeam` 任务集构造 nop-task 内存图模型——每个团队任务映射为一个图节点（节点名 = taskId），`TeamTask.blockedBy` 映射为 nop-task `waitSteps`（等待前置成功）。enterSteps = 无 blockedBy 的任务，exitSteps = 无任务依赖它的任务。
- **环检测（闭合真实 gap）**：桥接构造图模型后经 nop-task 的图分析（`GraphStepAnalyzer` 等价能力）校验拓扑；成环的 `blockedBy`（如 A→B→A）被**拒绝并快速失败**（抛异常或返回错误结果），而非静默接受。这是团队任务今天完全没有的能力。
- **就绪/阻塞拓扑查询**：基于 nop-task 图模型拓扑，暴露"哪些任务就绪（所有 blockedBy 已 COMPLETED）/ 哪些被阻塞（存在未完成依赖）"的查询，供编排与未来 successor（blockedBy 自动调度、auto-spawn）消费。
- **依赖序同步编排执行（端到端）**：一个编排器经 nop-task 运行时（`ITaskFlowManager` / `ITask.execute`）执行图，每个图节点委派给对应成员 agent 执行（经 `IAgentEngine.execute`），节点成功后经 `ITeamTaskStore.completeTask` 标记该团队任务 COMPLETED。nop-task 图调度器保证被依赖的任务先于依赖者完成（B blockedBy A 则 A 完成后 B 才跑）。
- **端到端验证**（Anti-Hollow #22）：3 个任务 A（无依赖）/ B（blockedBy A）/ C（blockedBy B）→ 编排执行 → 断言完成顺序 A→B→C 且全部 COMPLETED，且 B 的执行确未早于 A 完成（依赖序真实生效，非仅类型存在）。
- roadmap §4 Layer 4 验收标准「多 Agent 任务可以通过 Flow / Task 组织」升级为「nop-task DAG 集成已落地（任务可组织为 nop-task 工作流图 + 依赖序同步编排）」。

## Non-Goals

- **blockedBy 自动调度守护进程**（独立 carry-over `L4-blockedBy-resolution-engine`）：定时扫描就绪任务并自动 claim/派发。本计划交付就绪查询与同步编排，但不交付后台守护调度。Classification: successor plan required（部分依赖本计划的 DAG 基础）。
- **auto-spawn 成员 agent**（独立 carry-over `L4-auto-spawn-member-agent`）：声明式自动启动/调度成员 agent 执行。本计划编排消费**已绑定**成员 agent（InMemoryTeamManager/DbTeamManager 已绑定会话），不自动 spawn。Classification: successor plan required。
- **异步/长时/跨进程流编排执行**：nop-task `ITask.execute` 为 CompletableFuture 模型，本计划切片用同步 `syncGetOutputs()` 编排（编排器阻塞至整图完成）。分钟/小时级长时异步编排、跨进程图执行为 successor（异步团队通信工具 + DB 共享任务已为该场景存在）。本切片交付依赖序同步编排这一可端到端验证的语义。
- **采用 nop-task 的 `DaoTaskStateStore` 持久化团队任务**：DaoTaskStateStore 是 stub（无实现）。团队任务持久化唯一事实源保持为 `DbTeamTaskStore`/`ITeamTaskStore`。本计划不实现 nop-task DB 持久化，不把团队任务状态迁入 `NopTaskInstance`。
- **LLM 直面工具（如 `team-execute-flow` 工具）**：本计划交付程序化编排器 API（集成商 / lead hook 可调）。LLM 直面触发工具为 successor（依赖 auto-spawn + 调度策略裁定）。
- **nop-task 重试/超时/rate-limit decorator 接入**：vision `:35` 提及 ITaskStep + Decorator 内置 retry/timeout/rate-limit。本计划只交付 DAG 拓扑 + 同步编排，不接入 nop-task decorator 体系（团队任务的重试/超时已有独立 carry-over `L4-retry-recovery-mode` / `L4-task-lifecycle-extras`）。
- **动态增删图节点 / 运行时改图**：本计划图模型由当前任务集快照构造，执行期内不变。运行时动态改图为 successor。
- **Team ACL 在编排路径的额外强制**：复用既有 `ITeamAclChecker`（plan 228），不在编排器新增权限矩阵。编排器调用者须为团队 LEAD（经 teamManager 反查裁定），但不扩展 ACL 模型。

## Scope

### In Scope

- `nop-ai/nop-ai-agent/pom.xml` — 新增 `nop-task-core` 编译依赖
- `io.nop.ai.agent.team.flow` 包（新包）：
  - 团队任务 → nop-task 图模型桥接组件（构造 TaskFlowModel/图步骤，blockedBy → waitSteps，enter/exit 推导）
  - 就绪/阻塞拓扑查询（基于图拓扑 + 任务当前 status）
  - 环检测（经 nop-task 图分析等价能力，成环快速失败）
  - 依赖序同步编排器（经 nop-task 运行时执行图 + 每节点委派成员 agent + completeTask 标记）
- 既有 `IAgentEngine` / `ITeamTaskStore` / `ITeamManager` / `AgentToolExecuteContext` —— **只读消费**，不改契约（编排器经构造注入或 context 访问既有组件）
- 测试文件：
  - 桥接 + 环检测 + 就绪查询 focused 测试（线性链 / 菱形依赖 / 成环拒绝 / 就绪 vs 阻塞）
  - 同步编排端到端测试（A→B→C 依赖序执行 + COMPLETED 标记 + B 不早于 A 的 Anti-Hollow 断言 + 菱形并行就绪分支）
- 设计文档 `ai-dev/design/nop-ai-agent/nop-ai-agent-task-flow-integration.md`（记录 4 项设计裁定）
- `nop-ai-agent-roadmap.md` §4 Layer 4 验收标准 + `nop-ai-agent-actor-runtime-vision.md` §8.2/`:401` 同步

### Out Of Scope

- blockedBy 自动调度守护进程（Non-Goal）
- auto-spawn 成员 agent（Non-Goal）
- 异步/跨进程流执行 / nop-task DaoTaskStateStore 持久化（Non-Goal）
- LLM 直面编排工具（Non-Goal）
- nop-task decorator（retry/timeout/rate-limit）接入（Non-Goal）
- 运行时动态改图（Non-Goal）
- 新增团队任务表列 / 改 ITeamTaskStore 契约（编排器只读消费既有 store）

## Execution Plan

### Design Decisions (Pre-Adjudicated)

以下裁定在 plan 撰写阶段已确定（回应 carry-over 反复标注的"nop-task 模块集成裁定"），执行时直接遵循，不再作为 in-flight Decision。

1. **集成定位 = nop-task 作为团队任务的 DAG 编排引擎，而非持久化层**。nop-ai-agent 新增 `nop-task-core` 编译依赖（单向无环）。团队任务在编排时被映射为 nop-task 内存图模型；**团队任务持久化保持为 `DbTeamTaskStore`/`ITeamTaskStore`**（唯一事实源），**不采用** nop-task 的 `DaoTaskStateStore`（它是无实现的 stub，ORM 实体 `NopTaskInstance`/`NopTaskStepInstance` 运行时桥未接通）。理由：(1) 团队任务已有可用且跨进程的 DB 持久化；(2) nop-task DB 持久化不可靠会导致空壳风险；(3) 取 nop-task 之长（DAG 拓扑 / 环检测 / 依赖序调度），避 nop-task 之短（持久化 stub）。这直接回应"依赖 nop-task 模块集成裁定"。

2. **依赖映射 = 每个团队任务一个图节点，blockedBy → nop-task waitSteps**。节点名 = taskId。`TeamTask.blockedBy` 列表映射为该节点的 `waitSteps`（等待前置成功）。enterSteps = blockedBy 为空的任务；exitSteps = 不被任何其他任务 blockedBy 的任务。环检测经 nop-task 图分析（`GraphStepAnalyzer` 等价能力）——成环的 blockedBy（A→B→A）被拒绝并快速失败（抛异常），闭合"今天成环被静默存储"的真实 gap（Minimum Rules #24 精神：缺失/非法应快速失败而非静默接受）。

3. **执行模型 = 依赖序同步编排**。编排器经 nop-task 运行时（`ITaskFlowManager.newTaskRuntime` + `ITask.execute(...).syncGetOutputs()`）执行图。每个图节点的步骤执行委派给对应成员 agent（经 `IAgentEngine.execute`，成员 agent 会话已由 teamManager 绑定）；节点步骤成功返回后，编排器经 `ITeamTaskStore.completeTask(taskId, ...)` 标记该团队任务 COMPLETED。nop-task 图调度器保证依赖序：B blockedBy A 则 A 的节点完成后 B 的节点才执行。理由：(1) `syncGetOutputs()` 提供可端到端验证的同步语义（Anti-Hollow #22）；(2) 委派成员 agent 与既有 `call-agent`/团队执行模式一致；(3) 长时异步 / 跨进程为独立 successor（异步团队工具 + DB 共享任务已覆盖该场景）。

4. **节点 → 成员 agent 解析 = 经 teamManager 已绑定成员**。编排器不自动 spawn 成员。每个任务节点执行时，经 `ITeamManager` 反查团队已绑定成员会话，将任务委派给对应成员 agent 执行（具体成员解析策略：优先 `TeamTask.claimedBy`，未 claim 时由编排器按团队成员花名册解析或交由 lead 裁定——实现细节属源码层，plan 只约束"消费已绑定成员、不 spawn"）。未绑定任何成员的团队执行编排 = 快速失败（No Silent No-Op #24），而非静默跳过。auto-spawn 为独立 successor。

### Phase 1 - nop-task-core 依赖 + DAG 图模型桥 + 环检测 + 就绪查询 + focused 测试

Status: completed
Targets: `nop-ai-agent/pom.xml`、`io.nop.ai.agent.team.flow`（桥接 + 拓扑查询新包）

- Item Types: `Decision`（集成裁定 1/2）、`Fix`（团队任务零环检测 gap + blockedBy 不解析 gap）、`Proof`

- [x] `nop-ai-agent/pom.xml` 新增 `nop-task-core` 编译依赖（`io.github.entropy-cloud:nop-task-core`），验证 `./mvnw dependency:tree -pl nop-ai/nop-ai-agent` 无环、无冲突引入
- [x] 新建 `io.nop.ai.agent.team.flow` 桥接组件：从 `ITeamTaskStore.getTasksByTeam(teamId)` 任务集构造 nop-task 内存图模型（每任务一节点，blockedBy → waitSteps，enter/exit 推导），**真实调用 nop-task 模型构建/分析 API**（非自行重写 DAG）
- [x] 环检测：桥接构造后经 nop-task 图分析等价能力校验拓扑；成环 blockedBy（线性 A→B→A、自环 A→A）被拒绝并快速失败（抛异常），**不静默接受**
- [x] 就绪/阻塞拓扑查询：基于图拓扑 + 各任务当前 status 暴露就绪（所有 blockedBy 已 COMPLETED）/ 阻塞（存在未 COMPLETED 依赖）查询
- [x] 编写 focused 测试：线性链（A→B→C 拓扑正确）/ 菱形依赖（B、C 同依 A，D 依 B、C）/ 成环拒绝（A→B→A 与自环均快速失败，断言抛异常而非静默）/ 就绪 vs 阻塞（部分依赖 COMPLETED 时就绪集合正确变化）
- [x] 编写 NoOp 零回归验证：默认配置（NoOpTeamManager/NoOpTeamTaskStore）下桥接/查询对空团队/空任务集诚实处理（空图或快速失败，No Silent No-Op）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `nop-ai-agent/pom.xml` 含 `nop-task-core` 依赖；`./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过（无依赖环）
- [x] `io.nop.ai.agent.team.flow` 包下桥接组件存在，**真实消费 nop-task 图模型 API**（经 import/调用 nop-task 类型可验证，非自行实现 DAG）
- [x] **环检测落地**：成环 blockedBy 被快速失败（测试断言抛异常），闭合"今日静默存储成环"gap
- [x] **无静默跳过**（Minimum Rules #24）：非法/成环输入抛异常而非静默返回空图；NoOp/空输入诚实处理
- [x] **接线验证**（Minimum Rules #23）：focused 测试断言桥接确实调用了 nop-task 图模型构建/分析（非仅自建结构）
- [x] focused 测试全绿（线性 / 菱形 / 成环拒绝 / 就绪-阻塞）
- [x] No owner-doc update required（owner doc 更新在 Phase 3）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 依赖序同步编排执行 + 端到端验证

Status: completed
Targets: `io.nop.ai.agent.team.flow`（编排器）、端到端测试

- Item Types: `Fix`（多 Agent 任务经 Flow/Task 组织的执行能力 gap）、`Proof`

- [x] 新建依赖序同步编排器（`io.nop.ai.agent.team.flow`）：经 nop-task 运行时（`ITaskFlowManager.newTaskRuntime` + `ITask.execute(...).syncGetOutputs()`）执行 Phase 1 构造的图；每个图节点步骤委派对应成员 agent（经 `IAgentEngine.execute`，成员会话由 teamManager 已绑定）；节点成功后经 `ITeamTaskStore.completeTask` 标记 COMPLETED
- [x] 节点 → 成员 agent 解析遵循裁定 4（消费已绑定成员，不 spawn）；未绑定成员的团队执行编排快速失败（No Silent No-Op）
- [x] 编排器只读消费既有 `IAgentEngine` / `ITeamTaskStore` / `ITeamManager`，不改其契约
- [x] 编写端到端测试：构造 `DefaultAgentEngine`（InMemoryTeamManager + InMemoryTeamTaskStore + mock LLM 成员 agent）→ 程序化建团 + 绑定 lead/member → 创建 3 任务 A（无依赖）/ B（blockedBy A）/ C（blockedBy B）→ 调编排器执行 → 断言完成顺序 A→B→C 且全部 COMPLETED → **Anti-Hollow 断言**：B 的成员 agent 执行未早于 A 完成（依赖序真实生效，经执行时间戳/计数器/执行序记录验证，非仅最终状态）
- [x] 编写端到端菱形测试：A→{B,C}→D，断言 B、C 均在 A 完成后执行、D 在 B、C 均完成后执行
- [x] 编写失败传播测试：某节点成员 agent 执行失败时，依赖其后继节点不执行（依赖序语义），且失败诚实报告（非静默跳过）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 编排器存在于 `io.nop.ai.agent.team.flow`，**真实经 nop-task 运行时执行图**（`ITask.execute` 调用可验证，非自行重写调度）
- [x] **端到端验证**（Minimum Rules #22）：从编排器入口 → nop-task 图调度 → 成员 agent 委派 → completeTask 标记，完整路径跑通且有测试覆盖
- [x] **接线验证**（Minimum Rules #23）：端到端测试断言编排器确实调用 nop-task `ITask.execute`（非仅自建循环），且 completeTask 实际改变 store 中任务 status
- [x] **Anti-Hollow 断言**：B 执行未早于 A 完成（依赖序真实生效，有执行序证据，非仅最终 COMPLETED 状态）
- [x] **无静默跳过**（Minimum Rules #24）：未绑定成员快速失败；节点失败诚实报告且后继不执行（非静默成功）
- [x] 端到端测试全绿（线性 A→B→C + 菱形 A→{B,C}→D + 失败传播）
- [x] No owner-doc update required（owner doc 更新在 Phase 3）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 设计文档 + roadmap/vision 同步 + 全量回归

Status: completed
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-task-flow-integration.md`（新）、`nop-ai-agent-roadmap.md` §4、`nop-ai-agent-actor-runtime-vision.md` §8.2/`:401`

- Item Types: `Proof`

- [x] 新建 `ai-dev/design/nop-ai-agent/nop-ai-agent-task-flow-integration.md`：记录 4 项设计裁定（集成定位 / 依赖映射 / 执行模型 / 节点解析）+ 拒绝的替代方案（采用 DaoTaskStateStore / 自行重写 DAG / 异步长时编排）及原因。遵循 design doc 规范（只记最终设计状态与决策，不放类签名/代码）
- [x] 更新 `nop-ai-agent-roadmap.md` §4 Layer 4 验收标准 line 259：「多 Agent 任务可以通过 Flow / Task 组织」标注 nop-task DAG 集成已落地（任务可组织为 nop-task 工作流图 + 依赖序同步编排 + 环检测）；保留 blockedBy 自动调度守护 / auto-spawn / 异步跨进程 / LLM 直面工具仍为 successor 的未完成状态
- [x] 更新 `nop-ai-agent-actor-runtime-vision.md` `:401`：nop-task DAG 集成从 successor 标注为已落地（vision `:35` ITaskStep + Decorator 方向的 DAG 拓扑 + 同步编排切片落地；decorator 体系接入为 successor）
- [x] 验证全量测试：`./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] 运行 `node ai-dev/tools/check-doc-links.mjs --strict`（退出码 0）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `nop-ai-agent-task-flow-integration.md` 存在，含 4 项裁定 + 拒绝替代方案，无类签名/代码（符合 design doc 规范）
- [x] roadmap §4 + vision §8.2/`:401` 已更新（nop-task DAG 集成已落地，successor 未完成状态保留）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿（零回归）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] `nop-task-core` 依赖引入 `nop-ai-agent/pom.xml`（`./mvnw compile` 通过，无依赖环）
- [x] 团队任务 → nop-task 图模型桥落地为真实（非空壳）代码，**真实消费 nop-task 图模型/分析 API**
- [x] 环检测落地（成环 blockedBy 快速失败，闭合今日静默存储 gap）
- [x] 就绪/阻塞拓扑查询落地
- [x] 依赖序同步编排器落地为真实（非空壳）代码，**真实经 nop-task `ITask.execute` 执行图**
- [x] NoOp / 空输入 / 未绑定成员均诚实处理（无静默跳过 #24）
- [x] 必要 focused verification + 端到端验证已完成（环检测 / 就绪查询 / A→B→C 依赖序 / 菱形 / 失败传播）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（blockedBy 自动调度守护 / auto-spawn / 异步跨进程 / DaoTaskStateStore 持久化 / LLM 直面工具 / decorator 接入 / 动态改图 均显式 Non-Goals 切出）
- [x] 受影响 owner docs 已同步到 live baseline（design doc + roadmap §4 + vision §8.2）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）编排器运行时确实调用 nop-task `ITask.execute` 且 completeTask 改变 store status（不只是类型存在），（b）端到端 A→B→C 依赖序路径完整连通且 B 不早于 A，（c）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无；blockedBy 自动调度守护进程 / auto-spawn 成员 agent / 异步跨进程流执行 / nop-task DaoTaskStateStore 持久化 / LLM 直面编排工具 / nop-task decorator 接入 / 运行时动态改图 均为显式 Non-Goals 独立 successor，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **blockedBy 自动调度守护进程**（独立 carry-over `L4-blockedBy-resolution-engine`）：定时扫描就绪任务自动 claim/派发。Classification: successor plan required（消费本计划就绪查询 + 同步编排基础）。
- **auto-spawn 成员 agent**（独立 carry-over `L4-auto-spawn-member-agent`）：声明式自动启动/调度成员执行。Classification: successor plan required（依赖任务调度 + 本计划编排）。
- **异步/长时/跨进程流编排执行**：nop-task CompletableFuture 异步模型 + 跨进程。Classification: successor plan required。
- **LLM 直面编排工具（如 `team-execute-flow`）**：Classification: successor plan required（依赖 auto-spawn + 调度策略裁定）。
- **nop-task decorator（retry/timeout/rate-limit）接入**：Classification: successor plan required（与 `L4-retry-recovery-mode` / `L4-task-lifecycle-extras` 协同）。
- **运行时动态增删图节点 / 改图**：Classification: successor plan required。

## Closure

Status Note: 所有三个 Phase 均已完成且 checklist 全勾，独立子 agent closure-audit（fresh session）对 11 项验证点（A–K）全部判定 PASS。团队任务 → nop-task 工作流 DAG 图桥（真实经 `GraphStepAnalyzer` 环检测）+ 依赖序同步编排器（真实经 `ITaskFlowManager.newTaskRuntime` + `ITask.execute(...).syncGetOutputs()` 执行 `GraphTaskStep` DAG 调度）已端到端落地并验证；Anti-Hollow 断言（`startB > completionA`）证明依赖序真实生效；No Silent No-Op 五种情形均诚实失败；既有 `IAgentEngine`/`ITeamTaskStore`/`ITeamManager` 契约只读消费未改。所有 in-scope successor（blockedBy 自动调度守护 / auto-spawn / 异步跨进程 / DaoTaskStateStore 持久化 / LLM 直面工具 / decorator 接入 / 动态改图）均显式切出为独立 Non-Goals successor，无 in-scope live defect 被静默降级。
Completed: 2026-06-17

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（explore，fresh session）— task id `ses_12aebbf04ffekNv8Sa7SxFZerK`
- Audit Session: ses_12aebbf04ffekNv8Sa7SxFZerK
- Evidence:
  - **A. 编排器真实用 nop-task 运行时** — PASS：`TeamTaskFlowOrchestrator.java:165-170` 实例化 `GraphTaskStep` + `GraphStepNode` + `TaskImpl`，`:172` 调 `taskFlowManager.newTaskRuntime(task, false, null)`，`:174` 调 `task.execute(taskRt).syncGetOutputs()`。非自建调度。
  - **B. 图桥真实用 nop-task 分析** — PASS：`TeamTaskGraphBuilder.java:125-130` 调 `new GraphStepAnalyzer().analyze(graph)`（真实环检测，非本地重写）。
  - **C. completeTask 真实改 store** — PASS：`MemberAgentTaskStep.java:80` `taskStore.claimTask(...)`、`:123` `taskStore.completeTask(...)` 每节点真实调用；CAS 失败抛异常，不静默跳过。
  - **D. Anti-Hollow 依赖序断言** — PASS：`TestTeamTaskFlowOrchestrator.java:183-190` `assertTrue(startB > completionA, ...)` + `assertTrue(startC > completionB, ...)`，计数器由 `ExecutionRecorder` 在 delegate 真实 start/complete 时刻 `AtomicInteger.incrementAndGet()` 写入，证明 B 启动严格晚于 A 完成（非仅最终 COMPLETED 状态）。
  - **E. 无静默跳过** — PASS：空任务集（orchestrator:122-125 throw）/ 未知团队（:127-129 throw）/ 成环（TeamTaskGraphBuilder:128-129 throw，执行前）/ 未绑定成员（orchestrator:231-234 throw）/ 节点失败（MemberAgentTaskStep 4 处 throw + orchestrator:176-181 返回 success=false 失败结果）均诚实处理。
  - **F. 失败传播** — PASS：`nodeFailurePropagatesAndSuccessorsAreSkipped`（B 失败 → C 在 skipped 集合 + 仍 CREATED + delegate 未被调用）+ `nodeFailureByExceptionAlsoPropagates`（A 抛异常 → B skipped）。
  - **G. 测试覆盖 scope** — PASS：`linearChainExecutesInDependencyOrderAndCompletesAll` / `diamondDependencyBothBranchesAfterAAndDAfterBoth` / `nodeFailurePropagatesAndSuccessorsAreSkipped` / `nodeFailureByExceptionAlsoPropagates` + 4 个 NoOp fast-fail 测试。Phase 1 `TestTeamTaskGraphBuilder` 14 个 focused 测试。
  - **H. pom 依赖** — PASS：`nop-ai/nop-ai-agent/pom.xml:35-38` 含 `nop-task-core` 编译依赖，单向无环。
  - **I. 设计文档合规** — PASS：`nop-ai-agent-task-flow-integration.md` 存在，含 4 项裁定 + 5 项拒绝替代方案，零代码块（```` ``` ```` 计数 = 0），仅契约引用。
  - **J. roadmap/vision 更新** — PASS：roadmap §4 验收标准 `[x]`（:260）+ `L4-nop-task-dag-integration` 行 ✅（:256）；vision §8.2（:403）+ §2.2（:35）标注已落地 + successor 保留。
  - **K. 既有契约只读** — PASS：`IAgentEngine`/`ITeamTaskStore`/`ITeamManager` 接口零方法增删改，新代码仅 constructor-inject 消费。
  - **构建验证**：`./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → BUILD SUCCESS（含 nop-task-core / nop-ai-agent 全绿，零回归）；`./mvnw compile` 随 test 隐含通过。
  - **check-doc-links.mjs --strict**：退出码 0（"No errors found"）。新编辑文件（design doc / roadmap / vision）零 broken link；plan 233 中 6 个 `nop-ai-agent/pom.xml` 引用为计划 prose 预存（非本次引入、非本次 scope）。
  - **check-plan-checklist.mjs**：见下方命令退出码记录（标记 completed 前运行）。

Follow-up:

- 无 plan-owned 剩余工作。所有显式 successor（blockedBy 自动调度守护 `L4-blockedBy-resolution-engine` / auto-spawn `L4-auto-spawn-member-agent` / 异步跨进程流编排 / LLM 直面编排工具 / nop-task decorator 接入 / 运行时动态改图 / DaoTaskStateStore 持久化）均为独立 Non-Goals successor，已在 `Non-Goals` 与 `Non-Blocking Follow-ups` 中显式切出。

## Follow-up handled by 236-nop-ai-agent-task-scheduler-daemon.md

> 2026-06-17 追加（carry-over 追溯指针，非内容回写）：本计划 Non-Goals「blockedBy 自动调度守护进程」+ Non-Blocking Follow-ups 列出的独立 carry-over `L4-blockedBy-resolution-engine`（定时扫描就绪任务自动 claim/派发，消费本计划就绪查询 + 同步编排基础）现由 `ai-dev/plans/236-nop-ai-agent-task-scheduler-daemon.md` 接管实施。本计划本体（含 Closure Evidence）保持历史记录状态不变。
