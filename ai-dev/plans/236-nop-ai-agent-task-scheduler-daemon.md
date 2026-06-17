# 236 nop-ai-agent blockedBy 自动调度守护进程（定时扫描就绪任务自动 claim/派发，闭合无人值守多 Agent 编排链路）

> **Plan Status**: planned
> **Module**: nop-ai-agent
> **Work Item**: L4-blockedBy-resolution-engine

> Last Reviewed: 2026-06-17
> Source: carry-over from `ai-dev/plans/233-nop-ai-agent-task-flow-dag-integration.md`（Non-Goals「blockedBy 自动调度守护进程」+ Non-Blocking Follow-ups：「blockedBy 自动调度守护进程（独立 carry-over `L4-blockedBy-resolution-engine`）：定时扫描就绪任务自动 claim/派发。Classification: successor plan required（消费本计划就绪查询 + 同步编排基础）」）；同一 successor 在 plans 227、234 中亦显式延期；`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 4（`L4-blockedBy-resolution-engine` P1 carry-over）；`nop-ai-agent-actor-runtime-vision.md`（无人值守自动化执行定位）
> Related: `233`（交付 `TeamTaskTopology` 就绪/阻塞拓扑查询 + `TeamTaskFlowOrchestrator` 依赖序同步编排——本守护进程消费其就绪查询与成员委派原语，**不调用 `TeamTaskFlowOrchestrator.execute(teamId)`**，理由见 Design Decision 1）、`222`（交付 `ScheduledRecoveryManager`/`IRecoveryManager` 守护进程生命周期范式——本守护进程复用）、`225`/`227`（`ITeamTaskStore` CAS claim/complete 契约——本守护进程消费 idempotent claim）

## Purpose

把 nop-ai-agent 团队任务从"依赖序同步编排需程序化/人工触发调用 `TeamTaskFlowOrchestrator`"扩展为"启动守护进程后，就绪任务（status=CREATED 且所有 blockedBy 已 COMPLETED）被定时自动 claim/派发并在依赖序约束下执行至完成，无需手动编排调用"。这是闭合 roadmap §4 Layer 4「无人值守多 Agent 自主编排」链路的关键缺口：plan 233 已交付同步编排的"能力"，本计划交付"自动触发"。它是 `L4-auto-spawn-member-agent` 与 LLM 直面编排工具（`team-execute-flow`）的前置依赖。

## Current Baseline

基于 live repo 核对（`nop-ai/nop-ai-agent/src/main`，2026-06-17，含 roadmap 步骤的 grep 确认 `NO_MATCHES_FOUND` for `BlockedByResolution|TaskReadinessScan|IBlockedByResolution`）：

- **就绪/阻塞拓扑查询已落地**（plan 233 ✅）：`TeamTaskTopology.getReadyTasks()` 基于 nop-task 图拓扑 + 任务当前 status 暴露就绪任务集（所有 blockedBy 已 COMPLETED 的非终态任务，含 CREATED 与 CLAIMED）。守护进程消费此查询即可获知每周期可派发任务集（并需自行过滤 `status==CREATED`，见 Design Decision 4）。
- **依赖序同步编排器已落地**（plan 233 ✅）：`TeamTaskFlowOrchestrator.execute(String teamId)` 经 nop-task 运行时（`ITaskFlowManager.newTaskRuntime` + `ITask.execute(...).syncGetOutputs()`）执行**整团队** DAG，每节点（`MemberAgentTaskStep`）内部 `claimTask`（仅 CREATED 成功）+ 成员 agent 委派 + `completeTask` 标记。`MemberAgentTaskStep` 对已 COMPLETED 任务幂等处理（`claimTask` 返回空且状态 COMPLETED 时记为成功、不抛异常），但整团队入口**每次重建整图**且对**被他人 CLAIMED 的任务**会因 claim 失败而短路失败。因此守护进程**不复用 `execute(teamId)`** 作周期性增量推进（详见 Design Decision 1），改以**每任务委派**复用既有 `claimTask`/`IAgentEngine.execute`/`completeTask` 原语推进就绪任务。
- **`ITeamTaskStore` CAS claim/complete 契约已落地**（plan 225/227 ✅）：`claimTask` / `completeTask` / `abandonTask` 返回 `Optional<TeamTask>`（CAS-empty 非异常），跨进程共享（`DbTeamTaskStore`）。守护进程多周期扫描天然 idempotent——CAS 失败（已被他人 claim）= 空 Optional，不抛异常。
- **`TeamTaskStatus` 四态状态机**（plan 227 ✅）：CREATED → CLAIMED → COMPLETED / CREATED|CLAIMED → ABANDONED。守护进程只扫描 CREATED 状态就绪任务。
- **团队工具/引擎接线已落地**（plan 225/227/228 ✅）：`DefaultAgentEngine` teamManager/teamTaskStore 字段、`AgentToolExecuteContext.getTeamTaskStore()` / `getTeamManager()`。守护进程经构造注入或 context 访问既有组件。
- **零自动调度守护代码**：`nop-ai/nop-ai-agent/src/main` 经 grep 确认无 `BlockedByResolution` / `TaskReadinessScan` / `IBlockedByResolution` / `TeamTaskSchedulerDaemon` 任何相关代码。就绪任务目前只能由显式调用 `TeamTaskFlowOrchestrator` 的集成商/lead 触发，无法自动推进。
- **nop-task DB 持久化仍是 stub**（plan 233 已裁定）：守护进程的任务状态事实源保持 `DbTeamTaskStore` / `ITeamTaskStore`，不依赖 nop-task 持久化。
- **auto-spawn 成员 agent 尚未实现**（独立 carry-over `L4-auto-spawn-member-agent`）：守护进程消费**已绑定**成员 agent（teamManager 已绑定会话），不自动 spawn。未绑定成员的团队执行 = 快速失败（No Silent No-Op #24），与 plan 233 编排器裁定一致。
- **同模块已有守护进程范式**（plan 222 ✅）：`io.nop.ai.agent.runtime.recovery.ScheduledRecoveryManager`（+ `IRecoveryManager` / `NoOpRecoveryManager`）在本模块已实现幂等 `start()`/`stop()` + `scanOnce()` + 经 `IScheduledExecutor`（nop-commons）周期调度 + `volatile ScheduledFuture` 生命周期 + NoOp 默认零回归。本守护进程复用同一生命周期/调度/NoOp 范式（不重造调度基础设施）。

## Goals

- **守护进程组件**：一个可启动/停止的后台调度守护进程（scheduled daemon），按配置周期扫描指定团队（或全量团队）的就绪任务。
- **就绪解析 + 自动 claim + 派发**：每周期，经 `TeamTaskTopology.getReadyTasks()` 识别并过滤至 `status==CREATED` 且所有 blockedBy 已 COMPLETED 的任务，经 `ITeamTaskStore.claimTask`（CAS idempotent）认领，经既有成员 agent 委派原语（`ITeamManager` 成员解析 + `IAgentEngine.execute`，与 `MemberAgentTaskStep` 同一委派机制）同步执行，成功后 `completeTask`，失败诚实报告（abandon 或 fail-fast，不静默跳过）。**守护进程不复用 `TeamTaskFlowOrchestrator.execute(teamId)`**（整团队一次性入口，每次重建整图、对被他人 CLAIMED 任务会短路失败，不适合作周期性增量推进，详见 Design Decision 1）。
- **依赖序自动保证**：任务永远不会在其 blockedBy 未全部 COMPLETED 前被派发（就绪查询天然保证）；当依赖完成后，下一周期被依赖者自动变为就绪并被派发。这是"无人值守"的核心——无需人工触发，DAG 自然推进。
- **生命周期管理**：守护进程支持 start/stop；停止时优雅处理（进行中任务不被强制中断，但不再派发新任务）；可配置扫描周期、扫描团队范围。
- **idempotent / 多周期安全**：CAS claim 保证多周期扫描不会重复派发同一任务（已被 claim 的任务 = CREATED→CLAIMED，不在就绪 CREATED 集中）。
- **端到端验证**（Anti-Hollow #22）：构造 3 任务 A（无依赖）/ B（blockedBy A）/ C（blockedBy B）→ 启动守护进程（不调用任何手动编排 API）→ 断言 A/B/C 全部自动转 COMPLETED，且完成顺序 A→B→C（依赖序自动生效，B 派发严格晚于 A 完成），且守护进程生命周期可停止（stop 后新任务不再被派发）。
- roadmap §4 Layer 4 `L4-blockedBy-resolution-engine` 标注已落地；为 `L4-auto-spawn-member-agent` 解除前置依赖阻塞。

## Non-Goals

- **auto-spawn 成员 agent**（独立 carry-over `L4-auto-spawn-member-agent`）：本守护进程消费**已绑定**成员 agent，不自动 spawn。未绑定成员的团队 = 快速失败。Classification: successor plan required（依赖本计划的调度基础）。
- **LLM 直面编排工具（`team-execute-flow` 工具）**（独立 carry-over）：本守护进程是程序化后台调度，不是 LLM 可调工具。LLM 直面工具为 successor（依赖本计划 + auto-spawn + 调度策略裁定）。
- **异步/长时/跨进程流编排执行**：本守护进程的每周期派发为同步执行（复用 plan 233 `syncGetOutputs()` 同步编排语义）。多守护进程跨进程协调（分布式锁/共享调度状态）为 successor（依赖 DB-backed 协调 + 存储裁定）。本切片交付单实例守护进程的自动调度。
- **nop-task DaoTaskStateStore 持久化**：任务状态事实源保持 `DbTeamTaskStore` / `ITeamTaskStore`（plan 233 裁定）。
- **nop-task decorator（retry/timeout/rate-limit）接入**：任务的重试/超时已有独立 carry-over。本守护进程失败任务诚实报告（abandon 或 fail-fast），不内建重试 decorator。
- **运行时动态改图**：每周期基于当前任务集快照解析就绪，不动态增删图节点。
- **任务 RE-CLAIM / 超时自动 ABANDON**（独立 carry-over `task-reclaim` / `task-timeout-auto-abandon`）：本守护进程只处理 CREATED 就绪任务，不重置 ABANDONED 任务、不强占 CLAIMED 任务、不对 CLAIMED 任务做超时检测。这些生命周期扩展为独立 successor（依赖任务重置语义/超时策略裁定）。
- **多租户/用户隔离强化**：复用既有 teamManager 团队隔离（plan 228 ACL），不扩展隔离模型（独立 carry-over `L4-user-isolation` / `multi-tenant-llm-key`）。

## Scope

### In Scope

- `io.nop.ai.agent.team.scheduler` 包（新包）：
  - 守护进程组件（periodic scan → ready resolution → idempotent claim → dispatch → complete/abandon）
  - 生命周期管理（start/stop，可配置扫描周期、团队范围）
- 既有 `TeamTaskTopology`（就绪查询）/ `ITeamTaskStore`（CAS claim/complete/abandon）/ `ITeamManager`（成员解析）/ `IAgentEngine`（成员执行）/ `ScheduledRecoveryManager` 范式（生命周期）—— **只读消费**，不改契约（**不调用 `TeamTaskFlowOrchestrator.execute(teamId)`**，理由见 Design Decision 1）
- 守护进程接线（经构造注入或工厂创建，可被 `DefaultAgentEngine` / 集成商启动）
- 测试文件：
  - 守护进程 focused 测试（单周期就绪解析 / CAS idempotent claim / 依赖序自动推进 / 生命周期 start-stop / 空团队诚实处理 / 未绑定成员快速失败）
  - 端到端无人值守测试（A→B→C 自动完成，断言完成顺序，断言无手动编排调用，断言 stop 后停止派发）
- 设计文档 `ai-dev/design/nop-ai-agent/nop-ai-agent-task-scheduler-daemon.md`（记录设计裁定）
- `nop-ai-agent-roadmap.md` §4 Layer 4 `L4-blockedBy-resolution-engine` + `nop-ai-agent-actor-runtime-vision.md` 同步

### Out Of Scope

- auto-spawn 成员 agent（Non-Goal）
- LLM 直面编排工具（Non-Goal）
- 异步/跨进程/分布式守护协调（Non-Goal）
- nop-task DaoTaskStateStore 持久化 / decorator 接入 / 动态改图（Non-Goal）
- 任务 RE-CLAIM / 超时自动 ABANDON（Non-Goal，独立 successor）
- 多租户/用户隔离扩展（Non-Goal）

## Execution Plan

### Design Decisions (Pre-Adjudicated)

以下裁定在 plan 撰写阶段已确定，执行时直接遵循，不再作为 in-flight Decision。

1. **调度模型 = 单实例周期性每任务委派守护进程**。守护进程按配置周期（默认可配，初始建议 5s 量级）周期性扫描，每周期对每个目标团队：(1) 经 `TeamTaskTopology.getReadyTasks()` 获知就绪任务集（所有 blockedBy 已 COMPLETED 的非终态任务），并过滤至 `status==CREATED`（见裁定 4，排除仍在执行的 CLAIMED 任务）；(2) 对每个 CREATED 就绪任务经 `ITeamTaskStore.claimTask` CAS 认领（idempotent，CAS 失败=他人已认领=空 Optional，跳过不抛异常）；(3) 认领成功后经既有成员 agent 委派原语（`ITeamManager` 成员解析 + `IAgentEngine.execute`，与 plan 233 `MemberAgentTaskStep` 同一委派机制）同步执行，成功 `completeTask`，失败诚实处理（abandon 或 fail-fast）。**守护进程不复用 `TeamTaskFlowOrchestrator.execute(teamId)`**：该入口为整团队一次性执行（每次重建整图 + 每节点重新 claim），`MemberAgentTaskStep` 虽对已 COMPLETED 任务幂等（claim 空且 COMPLETED 记为成功），但整图重建对周期性增量推进冗余，且对**被他人 CLAIMED 的任务**会 claim 失败而短路失败——不适合作守护进程的周期性增量推进。守护进程的"调度"指自身的周期扫描 + 每任务 claim/委派循环；nop-task DAG 的**依赖序/拓扑分析仍委托 `TeamTaskTopology`**（非自行重写图分析）。理由：(1) 复用 plan 233 已落地的拓扑/就绪查询 + 既有 claim/engine/complete 原语，最小新增面；(2) CAS claim 天然 idempotent 多周期安全；(3) 每任务同步委派提供可端到端验证语义（Anti-Hollow #22）且天然支持跨周期增量推进（裁定 2）；(4) 跨进程/分布式协调为 successor。守护进程并发模型（单线程串行委派 vs 每任务线程/CompletableFuture 并行委派就绪任务集）属实现细节，plan 只约束"依赖序自动保证 + idempotent + 不静默跳过"。

2. **依赖序自动保证 = 经就绪查询天然实现，而非运行时阻塞**。守护进程不"等待"依赖完成，而是每周期只派发**当前就绪**任务（所有 blockedBy 已 COMPLETED）。当 A 完成后，下一周期 B 自动变为就绪（其 blockedBy={A} 且 A 已 COMPLETED）从而被派发。这避免运行时阻塞与死锁，天然支持无人值守推进。CAS claim 保证 B 不会被多周期重复派发。

3. **生命周期 = 显式 start/stop，优雅停止，复用本模块既有守护进程范式**。守护进程启动后周期性扫描直到显式 stop。生命周期/调度基础设施**复用 plan 222 已落地的 `ScheduledRecoveryManager`/`IRecoveryManager` 范式**：幂等 `start()`/`stop()`、`scanOnce()` 单次扫描入口、经 `IScheduledExecutor`（nop-commons）周期调度、`volatile ScheduledFuture` 生命周期管理、NoOp 默认实现（零回归）。本计划新增的守护进程采用同一范式（不重造调度基础设施），仅 `scanOnce` 内部语义不同（就绪任务解析 + claim + 委派）。stop 时：进行中（已 claim 正在执行）的任务不被强制中断（等其自然完成或失败），但不再派发新任务（新的 CREATED 就绪任务停止认领）。理由：(1) 强制中断进行中 LLM 调用语义复杂且危险；(2) 优雅停止保证已派发任务的 COMPLETED/ABANDONED 状态落库；(3) 测试可断言"stop 后新任务不被派发"；(4) 复用既有范式避免不一致并降低 review 摩擦。扫描周期、团队范围（全量 vs 指定 teamId 集合）可配置。

4. **失败语义 = 诚实报告，不静默跳过，不内建重试，绝不误弃他人进行中任务**。派发失败（成员 agent 抛异常 / completeTask CAS 失败 / 未绑定成员）= 诚实处理：`abandonTask` 将任务转 ABANDONED（供未来 task-reclaim successor 消费）或抛异常 fail-fast，**绝不静默跳过**（No Silent No-Op #24）。**关键安全约束**：守护进程只对**自己 CAS 认领成功**（CREATED→CLAIMED）的任务在执行失败时调用 `abandonTask`；对 `TeamTaskTopology.getReadyTasks()` 返回的 CLAIMED 任务（他人正在执行）一律跳过、不 claim、不 abandon（兑现 Non-Goal「不强占 CLAIMED 任务」）。CAS 认领失败（他人已抢先 claim）= 空 Optional，静默跳过该任务（合法并发，非错误）。重试/超时由独立 successor（nop-task decorator / `L4-retry-recovery-mode`）裁定，本守护进程不内建。空团队/无 CREATED 就绪任务/全量已完成 = 守护进程静默空转（这是合法的正常状态，非静默跳过——没有需要处理的工作）。

### Phase 1 - 守护进程核心（周期扫描 + 就绪解析 + idempotent claim + 派发 + 生命周期）+ focused 测试

Status: planned
Targets: `io.nop.ai.agent.team.scheduler`（守护进程新包）

- Item Types: `Fix`（无人值守自动调度能力 gap）、`Decision`（调度/依赖序/生命周期/失败裁定）、`Proof`

- [ ] 新建 `io.nop.ai.agent.team.scheduler` 守护进程组件：周期性扫描（可配置周期，复用 `ScheduledRecoveryManager`/`IScheduledExecutor` 范式），每周期经 `TeamTaskTopology.getReadyTasks()` 识别就绪任务集并过滤至 `status==CREATED` 且所有 blockedBy 已 COMPLETED
- [ ] CREATED 就绪任务经 `ITeamTaskStore.claimTask` CAS idempotent 认领；认领成功后经既有成员 agent 委派原语（`ITeamManager` 成员解析 + `IAgentEngine.execute`）同步执行（**不复用 `TeamTaskFlowOrchestrator.execute(teamId)`**）；成功 `completeTask`，失败诚实处理（仅对自认领成功任务 abandon/fail-fast；CLAIMED 他人任务跳过不误弃，不静默跳过）
- [ ] 生命周期管理：start/stop（复用 `ScheduledRecoveryManager` 幂等 start/stop + `IScheduledExecutor` 调度 + NoOp 默认范式）；stop 优雅处理（进行中任务不强制中断，不再派发新任务）；扫描周期、团队范围可配置
- [ ] 守护进程只读消费既有 `TeamTaskTopology`（就绪查询）/ `ITeamTaskStore`（claim/complete/abandon）/ `ITeamManager`（成员解析）/ `IAgentEngine`（成员执行）/ `ScheduledRecoveryManager` 范式（生命周期），不改其契约；**不调用 `TeamTaskFlowOrchestrator.execute(teamId)`**（整团队一次性入口不适用增量推进，详见 Design Decision 1）
- [ ] 编写 focused 测试：单周期就绪解析正确（线性/菱形依赖的就绪集，过滤至 CREATED）/ CAS idempotent（模拟多周期扫描不重复派发）/ 依赖序自动推进（A 完成后下周期 B 就绪）/ CLAIMED 他人任务不被误弃（getReadyTasks 含 CLAIMED 时守护进程跳过不 abandon）/ 生命周期 start-stop（stop 后新任务不被派发）/ 空团队与无 CREATED 就绪任务诚实空转（合法）/ 未绑定成员快速失败（No Silent No-Op）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 守护进程组件存在于 `io.nop.ai.agent.team.scheduler`，真实消费 `TeamTaskTopology.getReadyTasks()` 就绪查询 + `ITeamTaskStore` CAS claim/complete + `IAgentEngine` 成员委派（非自行重写 nop-task 图分析/依赖序；不调用 `execute(teamId)`）
- [ ] **CAS idempotent 验证**：focused 测试断言多周期扫描不重复派发同一任务（经 claimTask CAS 返回空 Optional 或状态非 CREATED）
- [ ] **CLAIMED 安全验证**：focused 测试断言 `getReadyTasks()` 返回的 CLAIMED（他人进行中）任务被跳过、不被 claim、不被 abandon（兑现 Non-Goal 不强占 CLAIMED 任务）
- [ ] **依赖序自动保证验证**：focused 测试断言任务永不在 blockedBy 未完成前被派发；依赖完成后下周期自动就绪
- [ ] **生命周期验证**：focused 测试断言 start 后开始派发、stop 后新 CREATED 就绪任务不再被派发（进行中任务自然结束）
- [ ] **无静默跳过**（Minimum Rules #24）：未绑定成员/派发失败/completeTask CAS 失败均诚实处理（abandon 或抛异常），不静默跳过；空团队/无 CREATED 就绪任务空转为合法正常状态（有测试区分两者）
- [ ] **接线验证**（Minimum Rules #23）：focused 测试断言守护进程确实调用 `TeamTaskTopology.getReadyTasks()` + `claimTask` + `IAgentEngine.execute` + `completeTask`（非仅自建循环）
- [ ] focused 测试全绿
- [ ] No owner-doc update required（owner doc 更新在 Phase 2）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 端到端无人值守验证 + 引擎接线 + 设计文档 + roadmap/vision 同步 + 全量回归

Status: planned
Targets: 守护进程引擎接线、端到端测试、`ai-dev/design/nop-ai-agent/nop-ai-agent-task-scheduler-daemon.md`（新）、`nop-ai-agent-roadmap.md` §4、`nop-ai-agent-actor-runtime-vision.md`

- Item Types: `Proof`

- [ ] 守护进程接线：可被 `DefaultAgentEngine` / 集成商经构造注入或工厂创建并启动（具体注入点属实现细节，plan 约束"可被启动且只读消费既有组件"）
- [ ] 编写端到端无人值守测试：构造 `DefaultAgentEngine`（InMemoryTeamManager + InMemoryTeamTaskStore + mock LLM 成员 agent）→ 程序化建团 + 绑定 lead/member → 创建 3 任务 A（无依赖）/ B（blockedBy A）/ C（blockedBy B）→ **启动守护进程（不调用任何手动 `TeamTaskFlowOrchestrator.execute`）** → 断言 A/B/C 全部自动转 COMPLETED → **Anti-Hollow 断言**：完成顺序 A→B→C（B 派发严格晚于 A 完成，经执行时间戳/计数器/执行序记录验证，非仅最终状态）→ 断言 stop 后新建任务不被派发
- [ ] 编写端到端菱形无人值守测试：A→{B,C}→D 自动完成，断言依赖序
- [ ] 新建 `ai-dev/design/nop-ai-agent/nop-ai-agent-task-scheduler-daemon.md`：记录 4 项设计裁定（调度模型 / 依赖序自动保证 / 生命周期 / 失败语义）+ 拒绝的替代方案（运行时阻塞等待依赖 / 分布式跨进程协调 / 内建重试 decorator）及原因。遵循 design doc 规范（只记最终设计状态与决策，不放类签名/代码）
- [ ] 更新 `nop-ai-agent-roadmap.md` §4 Layer 4：`L4-blockedBy-resolution-engine` 标注已落地（定时扫描就绪任务自动 claim/派发已交付）；保留 auto-spawn / 异步跨进程 / LLM 直面工具 / task-reclaim / 超时自动 abandon 仍为 successor 未完成状态
- [ ] 更新 `nop-ai-agent-actor-runtime-vision.md`：无人值守自动调度守护已落地（为 auto-spawn 解除前置阻塞）
- [ ] 验证全量测试：`./mvnw test -pl nop-ai/nop-ai-agent -am`
- [ ] 运行 `node ai-dev/tools/check-doc-links.mjs --strict`（退出码 0）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] **端到端验证**（Minimum Rules #22）：从守护进程 start → 周期扫描 → 就绪解析 → CAS claim → 编排派发 → 成员 agent 执行 → completeTask → 依赖完成后继任务下周期自动就绪 → 全图 COMPLETED，完整路径跑通且**无任何手动编排调用**（证明无人值守）
- [ ] **Anti-Hollow 断言**：B 派发严格晚于 A 完成（依赖序自动生效，有执行序证据，非仅最终 COMPLETED 状态）
- [ ] **生命周期端到端验证**：stop 后新建 CREATED 就绪任务不被派发（有测试覆盖）
- [ ] **接线验证**（Minimum Rules #23）：端到端测试断言守护进程确实经 `TeamTaskTopology.getReadyTasks()` + `claimTask` + `IAgentEngine.execute` 成员委派（非仅自建循环），completeTask 实际改 store status
- [ ] `nop-ai-agent-task-scheduler-daemon.md` 存在，含 4 项裁定 + 拒绝替代方案，无类签名/代码（符合 design doc 规范）
- [ ] roadmap §4 + vision 已更新（`L4-blockedBy-resolution-engine` 已落地，successor 未完成状态保留）
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿（零回归）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 守护进程组件落地为真实（非空壳）代码，真实消费 plan 233 `TeamTaskTopology` 就绪查询 + 既有 claim/engine/complete 原语 + `ScheduledRecoveryManager` 生命周期范式
- [ ] 周期扫描 + idempotent claim + 依赖序自动派发生效
- [ ] 生命周期 start/stop 落地（stop 后不派发新任务）
- [ ] NoOp / 空团队 / 无就绪任务 / 未绑定成员 / 派发失败均诚实处理（无静默跳过 #24）
- [ ] 必要 focused verification + 端到端无人值守验证已完成（线性 A→B→C + 菱形 + 生命周期 stop）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（auto-spawn / LLM 直面工具 / 异步跨进程 / DaoTaskStateStore 持久化 / decorator / 动态改图 / task-reclaim / 超时 abandon 均显式 Non-Goals 切出）
- [ ] 受影响 owner docs 已同步到 live baseline（design doc + roadmap §4 + vision）
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）守护进程运行时确实调用 `TeamTaskTopology.getReadyTasks()` + `claimTask` + `IAgentEngine.execute` + `completeTask`（不只是类型存在），（b）端到端无人值守路径完整连通（start → 全图 COMPLETED 无手动编排），（c）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无；auto-spawn 成员 agent / LLM 直面编排工具 / 异步跨进程流执行 / nop-task DaoTaskStateStore 持久化 / nop-task decorator 接入 / 运行时动态改图 / 任务 RE-CLAIM / 超时自动 ABANDON / 多租户用户隔离 均为显式 Non-Goals 独立 successor，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **auto-spawn 成员 agent**（独立 carry-over `L4-auto-spawn-member-agent`）：声明式自动启动/调度成员执行。Classification: successor plan required（本计划解除其调度前置阻塞）。
- **LLM 直面编排工具（`team-execute-flow`）**：Classification: successor plan required（依赖本计划 + auto-spawn + 调度策略裁定）。
- **异步/长时/跨进程流编排执行 + 分布式守护协调**：Classification: successor plan required。
- **任务 RE-CLAIM / 超时自动 ABANDON**（独立 carry-over `task-reclaim` / `task-timeout-auto-abandon`）：Classification: successor plan required（依赖任务重置/超时策略裁定）。
- **nop-task decorator（retry/timeout/rate-limit）接入**：Classification: successor plan required。

## Closure

Status Note: <<完成或关闭时填写：为什么这个 plan 可以关闭>>
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Audit Session: <<如用子 agent，记录 session ID>>
- Evidence:
  - 每条 Exit Criterion 的验证结果（PASS/FAIL + 对应的 live code path 或 test name）
  - 每条 Closure Gate 的验证结果（PASS/FAIL + evidence 来源）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0
  - Anti-Hollow 检查结果：<<端到端无人值守调用链追踪结果>>；`scan-hollow-implementations.mjs` 退出码为 0
  - Deferred 项分类检查：<<确认无 in-scope live defect 被降级>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
- <<或者明确写 no remaining plan-owned work>>
