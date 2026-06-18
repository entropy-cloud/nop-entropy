# 245 nop-ai-agent Daemon Multi-Member + Async Dispatch Parity（无人值守自动化 dispatch 路径对齐 orchestrator）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L4-daemon-multi-member-async-dispatch

> Last Reviewed: 2026-06-18
> Source: carry-over from `ai-dev/plans/244-nop-ai-agent-multi-member-per-task-routing.md`（§Non-Goals line 51「`TeamTaskSchedulerDaemon` per-task 多成员派发」+ §Non-Blocking Follow-ups line 238 + line 242）；同源 carry-over 见 plans 241（「per-cycle async 派发」Non-Blocking Follow-ups line 242）、240、239。roadmap §4 Layer 4 全 ✅；本工作项为 §4 之外 carry-over successor queue 的最高优先项（3 个 closed plans 241/243/244 反复引用，依赖项 241/243/244 刚全部闭合，前置条件就绪）。
> Related: `236`（交付 `TeamTaskSchedulerDaemon` 无人值守自动化 dispatch 主干——本计划扩展其 dispatch 路径）、`241`（交付程序化 `executeAsync(teamId)` async 编排——本计划对齐 daemon 侧 async 派发）、`243`（交付 dedicated spawn executor + explicit-propagation tenant 传播——本计划复用）、`244`（交付 `ITaskMemberRouter` + `AllMustSucceedReduction` + bound/spawn/mixed fan-out 节点——本计划复用）、`240`（交付 team-task reclaim/timeout-abandon 恢复模型——本计划失败语义对齐，task 保留 CLAIMED 由 reclaim 恢复）、`242`（交付跨进程 daemon 协调——本计划为单进程 async，跨进程为 successor）

## Purpose

把 nop-ai-agent 的**无人值守自动化 dispatch 路径**（`TeamTaskSchedulerDaemon`，plan 236 交付）从「单成员 + 同步 `engine.execute().join()`」提升至与**程序化 orchestrator**（`TeamTaskFlowOrchestrator`）对等的「per-task 多成员 fan-out + async 派发」，闭合模块核心定位"无人值守自动化"相对交互式编排的能力不对称。

**为何需要独立 successor**：plans 241/243/244 把交互式 orchestrator 路径升级为 async + 多成员 fan-out，但 daemon 的 dispatch 循环仍硬编码「一任务选一个 bound 成员（`resolveBoundMember`）+ 同步 `engine.execute(request).join()` + 单 `SpawnMemberRequest(team, task, daemonSessionId)` 三参构造（target=null 自解析）」。把 async / 多成员塞进任一前驱计划都会破坏其零回归闭合。本计划以**复用 plan 244 已交付的 `ITaskMemberRouter` + `AllMustSucceedReduction` + bound/spawn fan-out 节点执行模型 + plan 241/243 async 基础设施**的方式，把 daemon 的 per-task dispatch 升级为同样的 fan-out + async 语义，单成员 daemon 团队逐行零回归。

闭合后，daemon 与 orchestrator 在 per-task dispatch 层**行为等价**（多成员并发 + reduction + async + 诚实失败 + tenant 隔离），daemon 保留其独有的无人值守关注点（per-cycle 调度循环、daemon session 身份、reclaim/timeout 恢复 plans 240/242）。

## Current Baseline

基于 live repo 核对（引用位置为审计可复核的 live code path / 已闭合计划 evidence）：

- **daemon 单成员同步 dispatch（核心缺口，本计划闭合）**：`TeamTaskSchedulerDaemon`（`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/scheduler/TeamTaskSchedulerDaemon.java`）per-cycle dispatch 对每个 claimed task 解析**单个** bound 成员（`resolveBoundMember`）或经**单** `IMemberSpawner.spawnMember(new SpawnMemberRequest(team, task, daemonSessionId))`（plan 244 closure evidence `TeamTaskSchedulerDaemon.java:771` 引用此三参构造 = target null = 自解析单 target），并**同步** `engine.execute(request).join()` 阻塞 daemon 线程至该单成员完成。
- **grep 证据（NEXT_ITEM 已核实，审计可复核）**：`ITaskMemberRouter`/`MemberDispatchPlan`/`FanOut`/`executeAsync` 仅出现在 `team/flow/`（orchestrator 路径），`team/scheduler/TeamTaskSchedulerDaemon.java` **零出现**。daemon dispatch 与 orchestrator 完全不对称。
- **程序化 orchestrator 已就绪 ✅（plan 241/243/244 全闭合）**：`TeamTaskFlowOrchestrator.executeAsync(teamId)`（async 编排）+ `ITaskMemberRouter`/`MemberDispatchPlan`/`DispatchTarget`（per-task 路由扩展点，NoOp shipped 默认 = 单成员）+ `IReductionStrategy`/`AllMustSucceedReduction`（归约，shipped 默认全成功）+ bound/spawn/mixed fan-out 节点（`MemberAgentTaskStep`/`SpawnMemberFanOutStep`/`MixedMemberFanOutStep`，N 成员 `CompletableFuture` 组合 + 单次 complete）+ dedicated spawn executor（`ai-agent-spawn-worker-N`，`resolveSpawnExecutor`）+ explicit-propagation tenant（captured tenant 注入 worker `set`/`clear`）。详见 plan 244 §Current Baseline + §设计裁定。
- **`IMemberSpawner` 契约已为 fan-out 就绪 ✅（plan 244）**：`spawnMember(SpawnMemberRequest)` 单结果同步**接口签名不变**；`SpawnMemberRequest` 已增可选 additive target 字段（向后兼容，三参构造 target=null / target-aware 构造）；`DefaultMemberSpawner.spawnMember` 优先 request.target 否则 fallback `resolveSpawnTarget`。daemon 三参构造调用点（target=null）当前逐行零回归——本计划让 daemon 派发消费 router 选定的 N target（携带 target 的 `SpawnMemberRequest`）。
- **daemon 恢复模型已就绪 ✅（plan 240/242）**：task 失败保留 CLAIMED（非 abandon），由 reclaim/timeout-abandon（plan 240）+ 跨进程协调（plan 242，successor 外）恢复。本计划失败语义对齐：fan-out/reduction 失败 → task 保留 CLAIMED，由既有恢复模型处理。
- **配额已就绪 ✅（plan 234）**：`IResourceGuard` `TEAM_PARALLEL_BOUND_MEMBERS`/`TEAM_MEMBERS` 维度约束 fan-out 度。
- **核心缺口总结**：daemon dispatch 单成员 + 同步阻塞；无 router 消费、无 fan-out、无 reduction、无 async per-cycle 派发。

## Goals

- **daemon per-task dispatch 消费 router + fan-out + reduction**：daemon 的 per-task 派发消费 plan 244 的 `ITaskMemberRouter` dispatch plan（NoOp shipped 默认 = 单成员逐行零回归）+ bound/spawn/mixed fan-out 节点执行（N 成员 `CompletableFuture` 组合）+ `AllMustSucceedReduction`（全 completed → 单次 completeTask；任一失败 → task 保留 CLAIMED）。daemon 与 orchestrator 在 per-task 层行为等价。
- **daemon async per-cycle 派发**：daemon per-cycle dispatch **不再同步阻塞**于单个 task 的 `engine.execute().join()`；单 task 的多成员 fan-out 经 async future 派发，daemon 线程不阻塞在该 task 完成上（per-cycle async 派发 carry-over from plan 241）。daemon 保留其调度循环语义（per-cycle 选择 task、并发上限受 `IResourceGuard` 约束）。
- **复用而非重写（Anti-Hollow）**：daemon 复用 plan 241/243/244 已交付的 fan-out 节点执行 + reduction + dedicated spawn executor + explicit-propagation tenant 范式，**不重新实现**并行的多成员/async 逻辑（避免双并行代码路径的空壳风险）。具体复用方式（共享 dispatch 组件 / daemon 构造同一 fan-out future）记录于 design doc。
- **诚实失败语义对齐 plan 244/240（No Silent No-Op #24）**：空路由（无可派发成员）→ 诚实 throw + task 保留 CLAIMED；任一成员非 completed / 抛异常 / spawner 三态（NO_SPAWN/SPAWN_FAILED/throws/null）/ complete CAS 失败 → 诚实失败 + task 保留 CLAIMED；已 COMPLETED 幂等显式成功。无空方法体/continue/吞异常/TODO。
- **零回归**：单成员 daemon 团队（NoOp/Single shipped 默认路由）行为逐行不变（= 既有 plan 236 单成员 dispatch）；既有 plans 236/240 daemon 测试 + plans 241/243/244 orchestrator 测试全绿。`IMemberSpawner.spawnMember` 接口签名零变更（plan 244 已锁定）。
- **tenant 隔离零回归**：daemon fan-out 下 `claimTask`/`completeTask` 按调用方 tenant 隔离读写；spawn worker re-apply captured tenant + finally clear（复用 plan 243 范式）；不泄漏。
- **设计文档 + roadmap/vision 同步**：新增/更新 `ai-dev/design/nop-ai-agent/` daemon dispatch parity 设计（复用裁定 + 拒绝替代方案，无类签名）；更新 `nop-ai-agent-actor-runtime-vision.md`（无人值守 vs 交互式 parity 落地）+ roadmap §4 successor 标注。

## Non-Goals

- **改 `IMemberSpawner.spawnMember` 契约**（plan 244 已锁定接口签名不变，本计划消费而非修改）。Classification: rejected alternative。
- **`TeamTaskSchedulerDaemon` 跨进程协调 / nop-job `IJobScheduler` 集成**（plan 242/240 carry-over）：本计划为单进程 async dispatch，跨进程 daemon 协调 + cluster election + nop-job 调度替换 `IScheduledExecutor` 为独立 successor。Classification: successor plan required。
- **team-task claimer-liveness 交叉检测 / `team-task-reclaim` LLM 工具**（plan 240 carry-over）：claim 仍时间检测，reclaim 工具独立交付。Classification: successor plan required。
- **nop-task decorator（retry/timeout/rate-limit）接入**（plan 236 carry-over）：本计划只对齐 dispatch 语义，cross-cutting decorator 为独立 successor。Classification: successor plan required。
- **fan-out 在途取消 / partitioning / pipeline / quorum-majority reduction**（plan 244 carry-over）：daemon 复用 plan 244 shipped `AllMustSucceedReduction`（先失败 fast-fail + 在途 run-to-completion 结果丢弃）；取消/分片/pipeline/quorum 仍为 orchestrator 层 successor，daemon 在其落地后自然继承。Classification: successor plan required。
- **daemon 调度策略变更**（per-cycle task 选择算法 / 并发 task 数模型）：本计划不改 daemon 既有的 per-cycle 调度与 reclaim/timeout 模型（plans 236/240），只升级单 task 的 dispatch 语义。Classification: out-of-scope。
- **spawn session 池化 / 运行时动态改图**（plan 239 carry-over）：Classification: optimization candidate / successor plan required。

## Scope

### In Scope

- daemon per-task dispatch 消费 `ITaskMemberRouter` dispatch plan + bound/spawn/mixed fan-out 节点执行 + `AllMustSucceedReduction`（NoOp shipped 默认单成员逐行零回归）
- daemon async per-cycle 派发（单 task 多成员 fan-out 经 async future，daemon 线程不阻塞于单 task 完成上）
- 复用 plan 241/243/244 fan-out/reduction/dedicated executor/tenant 基础设施（不重写并行逻辑）
- 诚实失败语义逐条对齐（空路由 / 成员失败 / spawner 三态 / CAS 失败 / null + task 保留 CLAIMED）
- 单成员 daemon 零回归 + tenant 隔离零回归
- 测试（新）：daemon fan-out 并发证据 / daemon async 非阻塞证据 / reduction 各路径 / honest failure / 单成员零回归 / tenant 隔离 / daemon-vs-orchestrator parity 对比
- 设计文档 daemon dispatch parity + vision + roadmap successor 标注

### Out Of Scope

- 见 Non-Goals（契约变更 / 跨进程协调 / nop-job / claimer-liveness / reclaim 工具 / decorator / 在途取消 / partitioning / pipeline / quorum / 调度策略变更 / spawn 池化 / 动态改图 均为显式 rejected / successor / out-of-scope）

### 设计裁定（Pre-Adjudicated）

1. **daemon 复用 plan 244 已交付的 fan-out + reduction 节点执行模型，不重写并行逻辑**。daemon 的 per-task 派发消费同一 `ITaskMemberRouter` dispatch plan + 同一 `AllMustSucceedReduction` + 同一 bound/spawn/mixed fan-out future 组合。理由：(1) 避免「daemon 与 orchestrator 双并行 dispatch 代码」的空壳/漂移风险（Anti-Hollow #8/#22）；(2) plan 244 已把 fan-out 节点执行 + reduction 抽象为可复用组件，daemon 是其第二个消费者；(3) 单成员 NoOp 默认使 daemon 单成员路径逐行零回归。具体复用接线方式（共享 dispatch 组件 / daemon 直接构造 fan-out future）属实现层，记录于 design doc。

2. **async per-cycle 派发 = 单 task dispatch 经 async future，daemon 线程不阻塞于该 task 完成上**。daemon per-cycle 选择 task 后，其多成员 fan-out 经 async future 派发，daemon 不在该 task 上 `.join()` 阻塞；完成跟踪经 future + 既有 `ITeamTaskStore` 状态机（CLAIMED→COMPLETED 单次，失败保留 CLAIMED）。并发 task 数受 `IResourceGuard` 既有约束。理由：(1) 对齐 plan 241 `executeAsync` async 语义；(2) 保留 daemon 既有 per-cycle 调度循环（plans 236/240）不变。daemon 是否「fire-and-forget 多 task」 vs 「有限 in-flight」属调度策略层，本计划采用保守的「不阻塞单 task 完成」语义（不引入新的 per-cycle 多 task 并发模型，调度策略变更为 Non-Goal）。

3. **reduction shipped 默认 = `AllMustSucceedReduction`（复用 plan 244），先失败 fast-fail + 在途 run-to-completion 结果丢弃**。task 失败保留 CLAIMED（非 abandon），由 reclaim（plan 240）恢复。取消在途为 successor。理由：与 plan 244 + plan 240 失败模型逐条对齐。

4. **claim 保持同步单次、complete 在全部 N 成员 completed 后单次（对齐 plan 244 裁定 4）**。daemon fan-out 下 claim（CREATED→CLAIMED）同步单次，N 成员并发，全 completed 后单次 completeTask。任一失败 → 不 complete + task 保留 CLAIMED。

5. **拒绝 daemon 重写一套独立的多成员/async 逻辑**：双并行代码路径必然漂移（orchestrator 升级时 daemon 滞后——正是本计划要消除的不对称根因）。daemon 必须复用 plan 244 交付的扩展点与节点执行模型。

6. **拒绝改 `IMemberSpawner.spawnMember` 接口签名 / daemon spawn 调用模型**（plan 244 已锁定）：daemon 消费携带 router 选定 target 的 `SpawnMemberRequest`（plan 244 target-aware 构造），`DefaultMemberSpawner.spawnMember` 优先 request.target，接口签名不变。

## Execution Plan

### Phase 1 - daemon per-task dispatch 消费 router + bound/spawn fan-out + reduction（单成员零回归 + 多成员 fan-out 接通）

Status: completed
Targets: `io.nop.ai.agent.team.scheduler.TeamTaskSchedulerDaemon`（dispatch 路径消费 router + fan-out）、`io.nop.ai.agent.team.flow`（复用 plan 244 `ITaskMemberRouter`/`AllMustSucceedReduction`/bound/spawn/mixed fan-out 节点）、`ai-dev/design/nop-ai-agent/`（daemon dispatch parity 设计）

- Item Types: `Fix`（daemon 单成员硬编码 = carry-over gap）、`Decision`（复用而非重写 / async per-cycle / all-must-succeed / claim 同步单次 complete / 先失败 fast-fail 不取消）、`Proof`

- [x] daemon per-task dispatch 改为消费 plan 244 `ITaskMemberRouter` dispatch plan：`TeamTaskSchedulerDaemon` 注入 router（wire-at-consumer，null-safe → NoOp/Single shipped 默认 = 单成员逐行零回归），dispatch 路径用 router 解析的 dispatch plan 替代既有单成员 `resolveBoundMember` / 单 `SpawnMemberRequest` 三参构造自解析
- [x] daemon 对 dispatch plan 的 bound/spawn/mixed target 走 plan 244 fan-out 节点执行（claim 同步单次 CREATED→CLAIMED + N 成员 `CompletableFuture` 组合 + `AllMustSucceedReduction` 全 completed → 单次 completeTask；任一失败 → task 保留 CLAIMED）；NoOp 单成员 plan → 既有单成员 dispatch 路径逐行等价
- [x] 诚实失败语义逐条对齐（#24）：空 plan（无可派发成员）→ 诚实 throw + task 保留 CLAIMED；成员非 completed / future 异常 / spawner 三态（NO_SPAWN/SPAWN_FAILED/throws/null）/ complete CAS 失败 → 诚实失败 + task 保留 CLAIMED；已 COMPLETED 幂等显式成功；无空方法体/continue/吞异常/TODO
- [x] `IMemberSpawner.spawnMember` 接口签名零变更（plan 244 锁定）；daemon spawn 调用消费携带 router 选定 target 的 `SpawnMemberRequest`（target-aware），单成员 daemon（target=null 三参构造自解析）逐行零回归

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `TeamTaskSchedulerDaemon` dispatch 路径消费 `ITaskMemberRouter` dispatch plan（grep 可复核：daemon 现引用 `ITaskMemberRouter`/`MemberDispatchPlan`/fan-out 节点执行，零回归 fallback 为 NoOp 单成员）
- [x] NoOp/Single shipped 默认下单成员 daemon 团队行为与 plan 236 单成员 dispatch 逐行等价（既有 daemon 测试零回归）
- [x] 多成员 dispatch plan 下 daemon fan-out N 成员并发 + `AllMustSucceedReduction` 全 completed → 单次 completeTask（claim 同步单次）
- [x] **无静默跳过**（#24）：空 plan / 成员失败 / spawner 三态 / CAS 失败 各路径诚实 throw + task 保留 CLAIMED；已 COMPLETED 幂等显式成功
- [x] **接线验证**（#23）：daemon dispatch 运行时确实调用 router + fan-out 节点执行（非既有单成员 stub 路径）
- [x] `IMemberSpawner.spawnMember` 接口签名零变更（grep 确认）；单成员 daemon spawn 调用点逐行零回归
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] focused 测试在 Phase 3（#25）；本 Phase compile + 既有 daemon 测试零回归即可
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - daemon async per-cycle 派发 + tenant 传播 + 失败语义对齐

Status: completed
Targets: `io.nop.ai.agent.team.scheduler.TeamTaskSchedulerDaemon`（async per-cycle dispatch + 非阻塞）、复用 plan 243 dedicated spawn executor + explicit-propagation tenant

- Item Types: `Fix`（同步 `.join()` 阻塞 = carry-over gap）、`Proof`

- [x] daemon per-cycle dispatch 不再同步阻塞于单 task 的 `engine.execute().join()`；单 task 多成员 fan-out 经 async future 派发，daemon 线程不在该 task 完成上阻塞（per-cycle async 派发 carry-over from plan 241）；完成跟踪经 future + `ITeamTaskStore` 状态机
- [x] daemon fan-out 复用 plan 243 dedicated spawn executor（`ai-agent-spawn-worker-N`）+ explicit-propagation tenant（captured tenant 注入 worker `set`/`clear`）；bound 成员经 `IAgentEngine.execute` 既有 future（plan 241 范式）
- [x] tenant 隔离：daemon fan-out 下 `claimTask`/`completeTask` 按调用方 tenant 隔离读写；spawn worker 观测 captured tenant（非 null）+ finally clear（不泄漏）
- [x] 诚实失败语义 async 路径对齐（#24）：fan-out/reduction 失败 → task 保留 CLAIMED + 由 reclaim（plan 240）恢复；无静默跳过

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] daemon per-cycle dispatch 不阻塞于单 task 完成上（可观测证据：daemon 线程在 fan-out 派发后未 `.join()` 等待该 task，async future 跟踪完成）
- [x] daemon spawn fan-out 复用 dedicated spawn executor（`ai-agent-spawn-worker-N` 线程观测）+ tenant `set`/`clear`
- [x] tenant 隔离：跨租户不可见 + worker 观测 captured tenant + 不泄漏
- [x] **无静默跳过**（#24）：async 失败路径 task 保留 CLAIMED，无吞异常/空方法体
- [x] 单成员 daemon async 行为与既有零回归（NoOp 默认）
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - focused 测试（daemon fan-out 并发 + async 非阻塞 + reduction + honest failure + 零回归 + tenant + parity 对比）

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/team/scheduler/`（新测试）

- Item Types: `Proof`

- [x] 编写 daemon fan-out 并发测试：daemon per-cycle dispatch 多成员 task，断言 N 成员真正并发（可观测并发证据——执行时间区间重叠 / 并发计数 ≥2，非仅最终 COMPLETED）
- [x] 编写 daemon async 非阻塞测试：断言 daemon 线程在 fan-out 派发后不阻塞于单 task 完成（async future 跟踪）
- [x] 编写 daemon all-must-succeed reduction 测试：N 成员全 completed → task COMPLETED 单次（断言 completeTask 调用一次）；任一成员失败 → task 保留 CLAIMED + 在途 run-to-completion 结果丢弃；CAS 失败 → task 保留 CLAIMED
- [x] 编写 daemon honest failure 测试：空路由（无可派发成员）→ 诚实 throw + task 保留 CLAIMED；spawner fan-out 下 NO_SPAWN/SPAWN_FAILED/throws/null 各路径逐成员 honest failure
- [x] 编写 daemon 单成员零回归测试：NoOp/Single shipped 默认 → 既有 plan 236 单成员 dispatch 语义等价（既有 daemon 测试全绿）
- [x] 编写 daemon tenant 隔离 fan-out 测试：跨租户不可见 + spawn worker 观测 captured tenant + 不泄漏
- [x] 编写 daemon-vs-orchestrator parity 对比测试：同一 dispatch plan 在 daemon 与 orchestrator 下 per-task 结果等价（行为 parity 证明）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] daemon fan-out 并发测试全绿（N 成员真正并发可观测证据）
- [x] daemon async 非阻塞测试全绿（daemon 线程不阻塞于单 task 完成）
- [x] daemon reduction 各路径测试全绿（全 completed → 单次 completeTask；任一失败 → task 保留 CLAIMED）
- [x] daemon honest failure 测试全绿（空路由 + spawner 三态；task 保留 CLAIMED）
- [x] daemon 单成员零回归测试全绿（NoOp 默认逐行等价 + 既有 daemon 测试全绿）
- [x] daemon tenant 隔离测试全绿（跨租户不可见 + 不泄漏）
- [x] daemon-vs-orchestrator parity 对比测试全绿（per-task 结果等价）
- [x] **接线验证**（#23）：daemon dispatch 运行时确实调用 router + fan-out（task 状态机 CLAIMED→COMPLETED 经 store 验证 + N 成员 future 被组合）
- [x] **无静默跳过**（#24）：所有失败路径诚实 future 异常 / task 保留 CLAIMED；无空方法体/continue/TODO/吞异常
- [x] 新增功能各有对应 focused 测试覆盖
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过

### Phase 4 - 端到端验证 + 设计文档 + roadmap/vision 同步 + 全量回归

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/team/scheduler/`（新 E2E）、`ai-dev/design/nop-ai-agent/`（daemon dispatch parity 设计）、`nop-ai-agent-actor-runtime-vision.md`、`nop-ai-agent-roadmap.md`

- Item Types: `Proof`

- [x] 编写端到端测试（daemon fan-out 完整路径）：构造 daemon + 多成员团队 + 多 task（含 fan-out）→ per-cycle dispatch → async 非阻塞 → N 成员并发 → 全 COMPLETED；断言 fan-out 并发 + 完成跟踪
- [x] 编写端到端测试（daemon honest failure 传播）：fan-out 成员失败 → task 保留 CLAIMED + daemon reclaim（plan 240 模型）可恢复；在途 run-to-completion 结果丢弃
- [x] 编写端到端测试（daemon 单成员 NoOp 默认对比）：NoOp/Single 默认下 daemon 结果与既有 plan 236 单成员 dispatch 语义等价（零回归证明）
- [x] 编写端到端测试（daemon-vs-orchestrator parity）：同一 dispatch plan 在 daemon 与 orchestrator 下 per-task 结果等价
- [x] 新增/更新 design doc（daemon dispatch parity：复用裁定 + 拒绝替代方案，无类签名/代码，遵循 design doc 规范）
- [x] 更新 `nop-ai-agent-actor-runtime-vision.md`：无人值守 vs 交互式 dispatch parity 落地；跨进程协调/nop-job 仍为 successor
- [x] 更新 roadmap successor 标注（daemon 多成员 + async 派发 ✅）
- [x] 验证全量测试：`./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] 运行 `node ai-dev/tools/check-doc-links.mjs --strict`（退出码 0）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] **端到端验证**（#22）：从 daemon per-cycle dispatch 入口 → router dispatch plan → fan-out 节点（claim 同步 + N 成员并发 + reduction + 单次 complete）→ async 完成跟踪 → 最终 task 状态，完整路径跑通（fan-out / honest failure / 单成员零回归对比 / daemon-vs-orchestrator parity 四场景）
- [x] **fan-out 并发 Anti-Hollow 断言**：E2E 断言 daemon fan-out 真正并发（可观测证据）+ async 非阻塞
- [x] **接线验证**（#23）：E2E 断言 daemon dispatch 运行时确实调用 router + fan-out（task 状态机 CLAIMED→COMPLETED）+ N 成员 future 被组合（非单成员 stub）
- [x] **无静默跳过**（#24）：honest failure 经 future 异常 / task 保留 CLAIMED 诚实上报；空路由/NoOp 幂等为显式语义
- [x] design doc 已创建/更新（daemon dispatch parity 复用裁定 + 拒绝替代方案，无类签名/代码）
- [x] roadmap successor 标注 ✅；vision 已更新
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿（零回归，含既有 daemon + orchestrator 测试）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] daemon per-task dispatch 消费 `ITaskMemberRouter` + bound/spawn fan-out + `AllMustSucceedReduction` 落地为真实（非空壳）代码——NoOp 单成员逐行零回归 + 多成员 fan-out 真实工作
- [x] daemon async per-cycle 派发落地（不阻塞于单 task 完成）+ 复用 plan 243 dedicated executor + tenant 传播
- [x] 诚实失败语义逐条对齐（空路由 / 成员失败 / spawner 三态 / CAS 失败 / null + task 保留 CLAIMED 不 abandon，由 reclaim 恢复）
- [x] daemon 复用（非重写）plan 241/243/244 fan-out/reduction/executor/tenant 基础设施——无双并行代码路径
- [x] `IMemberSpawner.spawnMember` 接口签名 + `SpawnMemberResult` + `NoOpMemberSpawner` 行为**零变更**（plan 244 锁定）
- [x] 单成员零回归（NoOp 默认 + 既有 plans 236/240 daemon + plans 241/243/244 orchestrator 测试全绿）
- [x] 端到端：daemon dispatch → fan-out → 并发 + async 非阻塞 → 完成跟踪（fan-out / honest failure / 单成员对比 / parity 四场景）完整路径跑通
- [x] tenant 隔离零回归（fan-out 下跨租户不可见 + 不泄漏）
- [x] daemon-vs-orchestrator per-task parity 验证完成
- [x] 必要 focused verification 已完成（fan-out 并发 / async 非阻塞 / reduction / honest failure / 零回归 / tenant / parity 各有测试）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（跨进程协调 / nop-job / claimer-liveness / reclaim 工具 / decorator / 在途取消 / partitioning / pipeline / quorum / 调度策略变更 / spawn 池化 / 动态改图 均显式 Non-Goals）
- [x] 受影响 owner docs（daemon dispatch parity design doc + vision + roadmap successor 标注）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）daemon dispatch 运行时确实调用 router + fan-out 节点（非既有单成员 stub），（b）fan-out 真正并发 + async 非阻塞（可观测证据），（c）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无；跨进程协调 / nop-job / claimer-liveness / reclaim 工具 / decorator / 在途取消 / partitioning / pipeline / quorum / 调度策略变更 / spawn 池化 / 动态改图 均为显式 Non-Goals 独立 successor，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **`TeamTaskSchedulerDaemon` 跨进程协调 + nop-job `IJobScheduler` 集成**（plan 242/240 carry-over）：Classification: successor plan required（cluster election + 调度替换 `IScheduledExecutor`）。
- **team-task claimer-liveness 交叉检测**（plan 240 carry-over）：Classification: successor plan required。
- **`team-task-reclaim` LLM 工具**（plan 240 carry-over）：Classification: successor plan required。
- **nop-task decorator（retry/timeout/rate-limit）接入**（plan 236 carry-over）：Classification: successor plan required。
- **fan-out 在途取消 / partitioning / pipeline / quorum-majority reduction**（plan 244 carry-over）：Classification: successor plan required（orchestrator 层先落地，daemon 自然继承）。
- **spawn session 池化**（plan 239 carry-over）：Classification: optimization candidate。
- **运行时动态增删图节点 / 改图**（plan 239 carry-over）：Classification: successor plan required。

## Closure

Status Note: Plan 245 closed — the daemon's unattended dispatch path (`TeamTaskSchedulerDaemon`) is now at parity with the programmatic orchestrator (`TeamTaskFlowOrchestrator`) at the per-task dispatch layer: multi-member fan-out + reduction + async + honest failure (retain-CLAIMED) + tenant isolation, all via a single shared canonical `MemberFanOutDispatcher` (no dual code path). The NoOp shipped router default reproduces the pre-245 single-member behavior line-for-line (zero regression on the happy path; the intentional failure-semantics change abandon→retain-CLAIMED is documented and tested). All 4 phases complete, all closure gates ticked, independent closure audit APPROVED.
Completed: 2026-06-18

Closure Audit Evidence:

- Reviewer / Agent: independent closure-audit subagent (fresh session, task id `ses_127b9ea58ffeAXsoqruIyeJdK4`, opencode `general` agent — not the implementation session)
- Audit Session: ses_127b9ea58ffeAXsoqruIyeJdK4
- Evidence:
  - Exit Criteria — all 4 Phases PASS: every Phase 1/2/3/4 Exit Criterion ticked `[x]` with live code/test evidence cited below.
  - Closure Gates — all 17 PASS (tickboxed above).
  - **Wiring (Anti-Hollow #8/#22)** PASS: daemon `dispatchClaimedTask` calls `taskMemberRouter.route(team, task)` at `TeamTaskSchedulerDaemon.java:929` + `MemberFanOutDispatcher.dispatch(...)` at `:970-973`; old `resolveBoundMember` / `dispatchToBoundMember` / `DispatchOutcome` enum fully removed (grep 0 matches).
  - **Shared canonical dispatcher** PASS: `MemberFanOutDispatcher.java` (390 lines) contains the fan-out + reduce + complete chain; all 3 step variants delegate to it (`BoundMemberFanOutStep.java:159`, `SpawnMemberFanOutStep.java:172`, `MixedMemberFanOutStep.java:137`); `FanOutReduceComplete.java` deleted.
  - **Async non-blocking** PASS: the only `.join()` is inside `if (dispatched.isDone())` (safe, future already complete); async branch tracks via `inFlightDispatches` queue + `awaitInFlightDispatches` method (L556); no scan-thread blocking on a single task.
  - **Retain-CLAIMED failure semantics** PASS: `abandonTask` is NOT called anywhere on the new fan-out path (only stale javadoc ref remains); failures return `DispatchTally.failedNoDispatch` / `failedAfterDispatch` → task stays CLAIMED + `failedTasks` counter.
  - **`IMemberSpawner.spawnMember` signature unchanged** PASS: `IMemberSpawner.java:96` — `SpawnMemberResult spawnMember(SpawnMemberRequest)`.
  - **`SchedulerScanResult` new counter** PASS: `getFailedTasks()` (`:212`) + `getFailedTaskIds()` (`:236`).
  - **Tests real (not stubs)** PASS: `TestTeamTaskSchedulerDaemonMultiMemberAsyncDispatch` 9 substantive tests (peak≥2 + interval overlap / scan<400ms-with-500ms-task / completeTaskCount==1 / CLAIMED assertions / tenant observed+no-leak / parity / diamond dependency order); no `assertTrue(true)` stubs; all 9 green.
  - **Docs landed** PASS: `nop-ai-agent-daemon-dispatch-parity.md` (12.5 KB); vision note at `nop-ai-agent-actor-runtime-vision.md:420`; roadmap row `L4-daemon-multi-member-async-dispatch` ✅ at `nop-ai-agent-roadmap.md:268`.
  - **No silent no-ops (#24)** PASS: every failure path (router throws / null / empty plan / done-FAILED / done-throws / async-failure) is explicit `LOG.warn` + task stays CLAIMED + failed counter; no `continue` / empty body / TODO / swallowed exception.
  - **Anti-Hollow Check**: (a) daemon truly calls router + dispatcher at runtime (no single-member stub) — CONFIRMED; (b) fan-out truly concurrent (peak≥2 + overlap) + async non-blocking (scan<400ms with 500ms task) — CONFIRMED via green tests; (c) no empty bodies / silent skips — CONFIRMED.
  - `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → `Tests run: 2713, Failures: 0, Errors: 0, Skipped: 0` BUILD SUCCESS (implementation session); audit session re-ran → `Tests run: 2714, Failures: 0` BUILD SUCCESS.
  - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` → exit code 0.
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` → exit code 0 (0 high/critical findings).
  - `node ai-dev/tools/check-doc-links.mjs --strict` → exit code 0 (40 pre-existing BROKEN_LINK warnings in OTHER plans 200-234, none introduced by this plan; 1 style warning in this plan's Current Baseline code-path reference consistent with existing plan style).
  - Deferred 项分类检查: all Non-Goals (cross-process coordination / nop-job / claimer-liveness / reclaim tool / decorator / in-flight cancellation / partitioning / pipeline / quorum reduction / scheduling-policy change / spawn pooling / dynamic graph mutation) are explicit rejected/successor/out-of-scope items, not silently-downgraded in-scope defects.

Follow-up:

- No remaining plan-owned work. Successors are tracked as explicit Non-Goals / Non-Blocking Follow-ups (cross-process daemon coordination + nop-job integration; claimer-liveness cross-check; `team-task-reclaim` LLM tool; nop-task decorator; fan-out in-flight cancellation / partitioning / pipeline / quorum-majority reduction; daemon scheduling-policy changes; spawn session pooling; runtime dynamic graph mutation).
