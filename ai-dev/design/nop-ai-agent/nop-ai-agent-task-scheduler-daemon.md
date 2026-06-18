# 团队任务 blockedBy 自动调度守护进程设计（定时扫描就绪任务自动 claim/派发，闭合无人值守多 Agent 编排链路）

> Status: landed
> Last Reviewed: 2026-06-17
> Owner plan: `ai-dev/plans/236-nop-ai-agent-task-scheduler-daemon.md`（Work Item: `L4-blockedBy-resolution-engine`）
> Related: `nop-ai-agent-task-flow-integration.md`（plan 233 交付 `TeamTaskTopology` 就绪查询 + `TeamTaskFlowOrchestrator` 同步编排——本守护进程消费其就绪查询）、`nop-ai-agent-actor-runtime-vision.md` §10 Phase 4（`ScheduledRecoveryManager` 守护进程生命周期范式——本守护进程复用）

## 1. 定位

把 nop-ai-agent 的团队任务从"依赖序同步编排需程序化/人工触发调用 `TeamTaskFlowOrchestrator.execute(teamId)`"扩展为"启动守护进程后，就绪任务（status=CREATED 且所有 blockedBy 已 COMPLETED）被定时自动 claim/派发并在依赖序约束下执行至完成，无需手动编排调用"。

这是 roadmap §4 Layer 4「无人值守多 Agent 自主编排」链路的关键缺口闭合：plan 233 已交付同步编排的"能力"，本设计交付"自动触发"。它是 LLM 直面编排工具（`team-execute-flow`）的前置依赖；auto-spawn 成员 agent 已由 plan 237 / `L4-auto-spawn-member-agent` 落地（在本守护进程 dispatch 路径叠加 `IMemberSpawner` 扩展点，使未绑定成员的团队也能自动 spawn 执行，详见 `nop-ai-agent-member-auto-spawn.md`）。

团队任务持久化、团队生命周期、成员绑定、就绪/阻塞拓扑查询、依赖序同步编排、守护进程生命周期范式在本设计之前已由既有 plan（225 / 227 / 228 / 230 / 231 / 233 / 222）落地。本设计在其之上叠加自动调度能力，**不改既有契约**（守护进程只读消费 `IAgentEngine` / `ITeamTaskStore` / `ITeamManager` / `TeamTaskTopology` / `IScheduledExecutor`）。

## 2. 设计决策

### 决策 1：调度模型 — 单实例周期性每任务委派守护进程（不复用 `execute(teamId)`）

守护进程按配置周期（默认 5s）周期性扫描，每周期对每个目标团队：(1) 经 `TeamTaskTopology.getReadyTasks()` 获知就绪任务集（所有 blockedBy 已 COMPLETED 的非终态任务），并过滤至 `status==CREATED`（排除仍在执行的 CLAIMED 任务）；(2) 对每个 CREATED 就绪任务经 `ITeamTaskStore.claimTask` CAS 认领；(3) 认领成功后经既有成员 agent 委派原语（`ITeamManager` 成员解析 + `IAgentEngine.execute`，与 plan 233 `MemberAgentTaskStep` 同一委派机制）同步执行，成功 `completeTask`，失败诚实处理。

**守护进程不复用 `TeamTaskFlowOrchestrator.execute(teamId)`**：该入口为整团队一次性执行（每次重建整图 + 每节点重新 claim），`MemberAgentTaskStep` 虽对已 COMPLETED 任务幂等（claim 空且 COMPLETED 记为成功），但整图重建对周期性增量推进冗余，且对**被他人 CLAIMED 的任务**会 claim 失败而短路失败——不适合作守护进程的周期性增量推进。

守护进程的"调度"指自身的周期扫描 + 每任务 claim/委派循环；nop-task DAG 的**依赖序/拓扑分析仍委托 `TeamTaskTopology`**（非自行重写图分析）。

理由：
- 复用 plan 233 已落地的拓扑/就绪查询 + 既有 claim/engine/complete 原语，最小新增面。
- CAS claim 天然 idempotent 多周期安全（CAS 失败 = 他人已认领 = 空 Optional，非异常）。
- 每任务同步委派提供可端到端验证语义（Anti-Hollow #22），且天然支持跨周期增量推进。
- **SchedulerDaemon team 级 scan lease 协调已落地（plan 242 / `L4-cross-process-daemon-coordination`）**：daemon 扫描每个 team 前经 `IDaemonCoordinator.tryAcquireScanLease` 获取该 team 短时 lease，另一实例持有活跃 lease 则跳过该 team（降冗余扫描优化层，NoOp shipped 默认零回归；`claimTask` CAS 仍是正确性地板）。`ScheduledRecoveryManager` 跨进程协调仍为 successor（60s 低频幂等，冗余成本低）。

守护进程并发模型（单线程串行委派 vs 每任务线程/CompletableFuture 并行委派就绪任务集）属实现细节；本切片交付单线程串行委派（与 plan 233 同步编排语义一致），并行委派为 successor。

### 决策 2：依赖序自动保证 — 经就绪查询天然实现，非运行时阻塞

守护进程不"等待"依赖完成，而是每周期只派发**当前就绪**任务（所有 blockedBy 已 COMPLETED）。当 A 完成后，下一周期 B 自动变为就绪（其 blockedBy={A} 且 A 已 COMPLETED）从而被派发。这避免运行时阻塞与死锁，天然支持无人值守推进。CAS claim 保证 B 不会被多周期重复派发。

### 决策 3：生命周期 — 显式 start/stop，优雅停止，复用本模块既有守护进程范式

守护进程启动后周期性扫描直到显式 stop。生命周期/调度基础设施**复用 plan 222 已落地的 `ScheduledRecoveryManager`/`IRecoveryManager` 范式**：幂等 `start()`/`stop()`、`scanOnce()` 单次扫描入口、经 `IScheduledExecutor`（nop-commons）周期调度、`volatile ScheduledFuture` 生命周期管理、NoOp 默认实现（零回归）。

stop 时：进行中（已 claim 正在执行）的任务不被强制中断（`cancel(false)` graceful），但不再派发新任务。理由：强制中断进行中 LLM 调用语义复杂且危险；优雅停止保证已派发任务的 COMPLETED/ABANDONED 状态落库；测试可断言"stop 后新任务不被派发"；复用既有范式避免不一致并降低 review 摩擦。

扫描周期、团队范围（全量 vs 指定 teamId 集合）可配置。

### 决策 4：失败语义 — 诚实报告，不静默跳过，不内建重试，绝不误弃他人进行中任务

派发失败（成员 agent 抛异常 / 返回非 completed 终态 / completeTask CAS 失败 / 未绑定成员）= 诚实处理：仅对**自己 CAS 认领成功**（CREATED→CLAIMED）的任务调用 `abandonTask` 将其转 ABANDONED（供未来 task-reclaim successor 消费），**绝不静默跳过**（No Silent No-Op #24）。

**关键安全约束**：守护进程只对自己 CAS 认领成功的任务在失败时调用 `abandonTask`；对 `TeamTaskTopology.getReadyTasks()` 返回的 CLAIMED 任务（他人正在执行）一律跳过、不 claim、不 abandon（兑现 Non-Goal「不强占 CLAIMED 任务」）。CAS 认领失败（他人已抢先 claim）= 空 Optional，静默跳过该任务（合法并发，非错误，记入 `SchedulerScanResult.claimLostTasks`）。

重试/超时由独立 successor（nop-task decorator / `L4-retry-recovery-mode` / `task-timeout-auto-abandon`）裁定，本守护进程不内建。空团队/无 CREATED 就绪任务/全量已完成 = 守护进程静默空转（这是合法的正常状态，非静默跳过——没有需要处理的工作）。

## 3. 失败隔离

`scanOnce` 采用 per-task 失败隔离：一个任务的派发失败（成员 agent 抛异常等）被 try/catch 捕获，该任务被 abandon，扫描继续处理下一个就绪任务。这与 `ScheduledRecoveryManager` 的 per-session 隔离范式一致（一个 orphan recovery handler 失败不中止整个扫描）。一个团队的失败也不影响其他团队（per-team 隔离：未知/已 disbanded 团队 LOG.warn 跳过）。

## 4. 拒绝的替代方案

| 被拒绝方案 | 理由 |
|-----------|------|
| 复用 `TeamTaskFlowOrchestrator.execute(teamId)` 作周期性推进 | 整团队一次性入口，每周期重建整图 + 每节点重新 claim；对被他人 CLAIMED 的任务会 claim 失败而短路失败；对已 COMPLETED 任务虽幂等但整图重建冗余。不适合作守护进程的增量推进（决策 1）。 |
| 运行时阻塞等待依赖完成（守护进程持锁等待 A 完成后再派发 B） | 引入运行时阻塞与死锁面；与"无人值守推进"目标相悖。本设计改为每周期只派发当前就绪任务，依赖序经就绪查询天然保证（决策 2）。 |
| 异步/长时/跨进程流编排执行 + 分布式守护协调（CompletableFuture 非阻塞 + 跨进程共享调度状态 + 分布式锁） | 本切片目标是单实例可端到端验证的自动调度语义。**SchedulerDaemon team 级 scan lease 协调已落地（plan 242 / `L4-cross-process-daemon-coordination`，降冗余扫描优化层，NoOp shipped 默认零回归）**；异步跨进程编排（`executeAsync` 非阻塞）已落地（plan 241）。`ScheduledRecoveryManager` 跨进程协调仍为 successor（60s 低频幂等冗余成本低）。CAS claim 已提供多守护进程并发安全（CAS 失败 = 合法跳过）。 |
| 内建重试 decorator（派发失败自动重试） | 团队任务重试/超时已有独立 successor `L4-retry-recovery-mode` / `task-timeout-auto-abandon` / nop-task decorator 接入。本守护进程失败任务诚实 abandon（供 task-reclaim successor 消费），不内建重试（决策 4）。 |
| auto-spawn 成员 agent（团队未绑定成员时自动 spawn） | 独立 carry-over `L4-auto-spawn-member-agent`（**已于 plan 237 落地**，详见 `nop-ai-agent-member-auto-spawn.md`）。本守护进程（plan 236）消费**已绑定**成员 agent，未绑定成员的团队执行 = 失败 abandon（No Silent No-Op，与 plan 233 编排器裁定一致）；plan 237 在本守护进程的 dispatch 路径叠加 `IMemberSpawner` 扩展点（NoOp shipped 默认零回归），使未绑定成员也能自动 spawn 执行。 |
| 运行时动态改图（每周期增删图节点） | 每周期基于当前任务集快照解析就绪，不动态增删图节点。运行时动态改图为 successor。 |

## 5. 边界（Non-Goals，均为独立 successor）

> **注**：auto-spawn 成员 agent 已由 plan 237 / `L4-auto-spawn-member-agent` 落地，详见 `nop-ai-agent-member-auto-spawn.md`。本守护进程的 dispatch 路径已集成 `IMemberSpawner` 扩展点（NoOp shipped 默认 = 零回归），未绑定成员的团队经 spawner fallback 自动 spawn 执行。

- 异步/长时流编排执行（`executeAsync` 非阻塞）— **已落地（plan 241 / `L4-async-cross-process-orchestration`）**。
- **SchedulerDaemon team 级 scan lease 协调** — **已落地（plan 242 / `L4-cross-process-daemon-coordination`，NoOp shipped 默认零回归）**。
- `ScheduledRecoveryManager` 跨进程协调 — successor（60s 低频幂等，冗余成本低）。
- ~~LLM 直面编排工具（如 `team-execute-flow`）~~ **已落地（plan 239 / `L4-team-execute-flow-llm-tool`，详见 `nop-ai-agent-team-execute-flow.md`）**。
- 任务 RE-CLAIM（重置 ABANDONED 任务为 CREATED）— successor `task-reclaim`。
- 超时自动 ABANDON（对 CLAIMED 任务做超时检测）— successor `task-timeout-auto-abandon`。
- nop-task decorator（retry/timeout/rate-limit）接入 — successor。
- 并行委派就绪任务集（CompletableFuture 并行）— successor（实现细节）。
- 多租户/用户隔离强化 — successor `L4-user-isolation` / `multi-tenant-llm-key`。

## 6. 落地证据

- 守护进程组件：`io.nop.ai.agent.team.scheduler` 包（`ITeamTaskSchedulerDaemon` 契约 + `NoOpTeamTaskSchedulerDaemon` shipped 默认零回归 + `SchedulerScanResult` 不可变快照 + `TeamTaskSchedulerDaemon` functional 实现）。
- 生命周期/调度基础设施复用：`synchronized` 幂等 start/stop + `IScheduledExecutor.scheduleWithFixedDelay` + `volatile Future` 句柄 + graceful `cancel(false)`（镜像 plan 222 `ScheduledRecoveryManager` 范式）。
- 引擎接线：`DefaultAgentEngine.teamTaskSchedulerDaemon` 字段 + `setTeamTaskSchedulerDaemon`/`getTeamTaskSchedulerDaemon`（null-safe，部署层管理 start/stop，引擎不调）。
- 真实消费 plan 233 `TeamTaskTopology.getReadyTasks()` 就绪查询 + `ITeamTaskStore` CAS claim/complete/abandon + `IAgentEngine.execute` 成员委派（非自行重写图分析；不调用 `execute(teamId)`）。
- 端到端验证：线性 A→B→C（断言 B 派发严格晚于 A 完成，C 派发严格晚于 B 完成）、菱形 A→{B,C}→D（B/C 在 A 后、D 在 B/C 后）、生命周期 stop（stop 后新建任务不被派发）。
- CAS 安全验证：多周期扫描不重复派发（COMPLETED 任务排除出就绪集）；CAS 失败 = 合法跳过（claimLost，非 abandon）；CLAIMED 他人任务被跳过不被 claim 不被 abandon。
- 全量回归：`./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿。
