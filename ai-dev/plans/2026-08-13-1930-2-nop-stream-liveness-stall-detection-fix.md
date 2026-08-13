# 2 G52 per-task liveness 停滞检测修复：空闲/已完成任务不再被误判停滞（AR-01 P1）

> Plan Status: active
> Last Reviewed: 2026-08-13
> Mission: nop-stream-invariant-loop
> Source: `ai-dev/audits/2026-08-13-1930-open-audit-nop-stream-invariant-loop.md` AR-01（G52 liveness 停滞检测误杀空闲/已完成任务）
> Related: `2026-08-13-1243-1-nop-stream-engine-state-recovery-and-timer-fixes.md`（AR-02 空闲期 PT 定时器修复——"空闲≠停滞"族的第一层）；`2026-08-13-0132-2-nop-stream-checkpoint-recovery-fix.md`（恢复路径机制基线）

## Purpose

把 G52 per-task 停滞检测的行为缺陷收口：停滞检测必须区分"空闲但健康"、"已正常完成"与"真正停滞"三类状态，不再默认配置下把健康但空闲的分布式作业在约 4 分钟内误杀（FAILED）；同时使停滞驱动的恢复路径与 FAILED-report 驱动的恢复路径门控语义一致（`autoRecoverOnFailedReport`）。

## Current Baseline

- **live 缺陷点（已核实）**：
  - `JobCoordinator.detectFailures`（`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:1006-1024`）：`taskStallDetected` 判定仅比较 `subtaskLiveness.get(livenessKey) < now - taskTimeoutMs`（默认 60s，:91 `DEFAULT_TASK_TIMEOUT_MS = 60_000L`），**无任何"任务已 COMPLETED"或"任务空闲但存活"的排除**。
  - `subtaskLiveness` 只在 `reportNodeTaskLiveness`（:953 `put`）与 `reportTaskStatus`（:889-891 `put`）时更新；收到 COMPLETED 报告（:860+ 主路径）**不做 assignment/liveness 清理**（grep 全类无 `subtaskLiveness.remove`，:890 仅 put）。
  - `StreamTaskInvokable`（`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/StreamTaskInvokable.java:105,395-396`）：`lastProgressTime` 仅在有数据处理时更新（SOURCE/SELF_CONTAINED 启动、sourceOp.collect()、processInputGate 每元素）→ 空闲源/已完成上游任务其值停留在启动时刻。
  - `TaskManager`（`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:207-250`）：心跳每 5s 上报 `inv.getLastProgressTime()`。
  - 两个生产 executor 均 `coordinator.setAutoRecoverOnFailedReport(false)`（`RpcDistributedExecutor.java:236` / `EmbeddedDistributedExecutor.java:193`），但 `detectFailures` 停滞路径**不受该开关门控**（:1005-1024 直连 `requestRecovery()`）。
  - `reportTaskStatus` 收到 COMPLETED 报告时不清理 assignment/liveness（:889-891 仍 put 当前时间——注意这里其实会用 now 刷新 liveness，但 `RunningTask.run()` finally 已把任务移出 `runningTasks`（TaskManager.java:706-718）→ 心跳不再上报 → coordinator 侧条目最终过期）。
- **后果链（源码全链验证）**：空闲或已完成的合法任务 → 60s 后 `taskStallDetected` → `requestRecovery()` → `globalRecovery()`（restartCount++，重新 deployTask）→ 新 attempt 再次空闲/完成 → 每 ~60s 一次 → 第 4 次 `restartCount > maxRestarts(3)` → `failJob`。健康但空闲的分布式作业默认配置约 4 分钟被 FAILED；混合有界/无界作业中已完成 source 每 60s 被重新部署重跑（checkpoint 关闭时全量重放）。
- **现有测试**：`TestJobCoordinatorPerTaskFailure.staleLivenessTriggersRecoveryViaDetectFailures` 仅覆盖真停滞（数据不推进），无"空闲不触发"与"已完成不触发"用例。
- **不变式 #5(b)**（lease 活性族）：停滞判定与 lease failover 双轨并存；本修复不改变 lease 面（节点死亡仍由 lease 检测），只修正 task 级停滞的误判面。

## Goals

- 停滞检测语义修正：心跳/存活信号与数据进度解耦（或等价排除 COMPLETED/空闲任务），使"空闲但健康"与"已正常完成"的任务不再触发停滞恢复。
- 停滞驱动恢复路径与 FAILED-report 恢复路径门控一致（`autoRecoverOnFailedReport` 同时门控停滞路径，或文档化并测试其分工）。
- 回归测试："空闲任务不触发 recovery"、"完成的上游任务不触发 recovery"（当前仅真停滞用例）。
- 真停滞（数据不推进但进程存活）仍必须触发恢复（既有行为不回退）。
- 门禁复跑零命中（JUnit 门禁 + mjs `all`）+ `./mvnw test -pl nop-stream -am -T 1C` 全绿。

## Non-Goals

- 不改变节点 lease 检测（`InMemoryClusterRegistry`/JDBC registry 面，P2 维持）。
- 不改变 `autoRecoverOnFailedReport=true` 时 FAILED 报告触发恢复的既有语义。
- 不处理 AR-03（`RunningTask.run()` finally 竞态，P2 backlog）。
- 不处理日志占位符错位（AR-04，P2 backlog）。
- 不引入新的不变式门禁（如执行中发现新失败类 → I6 评估 Loop Rule）。

## Scope

### In Scope

- `JobCoordinator.detectFailures` 停滞判定逻辑修正（排除 COMPLETED / 空闲任务或改为心跳时间戳语义）。
- `JobCoordinator.reportTaskStatus` COMPLETED 路径的 assignment/liveness 清理（若采用"排除已完成"方案）。
- `TaskManager` 心跳/`StreamTaskInvokable` liveness 信号语义（若采用"心跳时间戳解耦"方案）。
- 停滞路径门控 `autoRecoverOnFailedReport`（或等价的显式语义裁定 + 文档化）。
- 回归测试（空闲不触发 / 已完成不触发 / 真停滞仍触发）。
- 文档：`checkpoint-design.md` 或 `core-design.md`（若 G52/停滞检测语义需同步）、roadmap backlog 状态流转、daily log。

### Out Of Scope

- 节点 lease 检测语义。
- FAILED 报告路径行为（`autoRecoverOnFailedReport=true` 时）。
- AR-03 竞态（P2，backlog 触发条件维持）。
- 其余 P2 backlog 批次。

## Execution Plan

### Phase 1 - 停滞检测语义修正（Fix + 裁定）

Status: planned
Targets: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java`、`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java`、`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/StreamTaskInvokable.java`

- Item Types: `Fix | Decision`

- [ ] Decision：裁定停滞判定方案——(a) liveness 改为心跳时间戳（TM 每跳上报墙钟，与数据进度解耦）或 (b) 停滞判定排除 COMPLETED/空闲任务（reportTaskStatus 收到 COMPLETED 时清除 assignment/liveness）。裁定依据：与 FAILED-report 恢复路径语义一致性、心跳周期（5s）与数据流量的耦合度、对"真停滞"检测时延的影响。裁定结论 + 拒绝理由落档 plan 或 design doc。**硬约束**：所选方案必须同时满足本节 Exit Criteria 的三条行为（空闲不触发 / 已完成不触发 / 真停滞仍触发）——纯"心跳墙钟"方案单独成立会静默丢失真停滞检测（心跳存活的任务永不超时），若选 (a) 必须补充等价信号（如 input-pending / busy 维度）使三条同时成立；不允许为满足空闲排除而牺牲真停滞面。
- [ ] Fix：按裁定方案实现停滞判定修正（涉及 `detectFailures` 判定逻辑 + `reportTaskStatus` COMPLETED 清理 与/或 `TaskManager` 心跳/`StreamTaskInvokable` liveness 语义）。
- [ ] Fix：停滞路径门控——`detectFailures` 的停滞驱动恢复与 `autoRecoverOnFailedReport` 语义对齐（若选门控方案）或显式裁定"停滞检测恒启用"并文档化理由。
- [ ] 类别清扫：grep 全仓 `subtaskLiveness` / `lastProgressTime` / `taskStallDetected` / `taskTimeoutMs` 消费点，确认无其他路径依赖旧语义。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] Decision 裁定记录在案（方案选择 + 拒绝理由）。
- [ ] 空闲任务（无数据流入 > 2×taskTimeoutMs）不再触发停滞恢复。
- [ ] 已完成任务（COMPLETED 报告后）不再触发停滞恢复。
- [ ] 真停滞任务（数据不推进但进程存活）仍触发恢复（既有 `staleLivenessTriggersRecoveryViaDetectFailures` 测试保持绿）。
- [ ] 停滞路径门控与 FAILED 路径门控语义一致（或显式裁定记录）。
- [ ] **无静默跳过**：新逻辑无空方法体/吞异常占位。
- [ ] 文档：`checkpoint-design.md` / `core-design.md` 停滞检测语义同步；否则 `No owner-doc update required`。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 回归测试（先红后绿）

Status: planned
Targets: `nop-stream/nop-stream-runtime/src/test`（TestJobCoordinatorPerTaskFailure 扩展或新增测试类）

- Item Types: `Fix | Proof`

- [ ] 复现测试（先红）：空闲任务（构造无数据心跳停滞场景，推进 wall clock 超 taskTimeoutMs）pre-fix 触发 recovery（红）→ post-fix 不触发（绿）。
- [ ] 复现测试（先红）：已完成上游任务（COMPLETED 报告后）pre-fix 触发 recovery（红）→ post-fix 不触发（绿）。
- [ ] 真停滞用例保持绿（`staleLivenessTriggersRecoveryViaDetectFailures`）。
- [ ] 如采用门控方案：`autoRecoverOnFailedReport=false` 时停滞不触发恢复的用例。
- [ ] 验证：新增测试 + 既有 `TestJobCoordinatorPerTaskFailure` 全绿。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 三条核心用例（空闲不触发 / 已完成不触发 / 真停滞触发）存在且全绿，先红后绿证据在案。
- [ ] 门控语义用例（如适用）存在且全绿。
- [ ] 既有相关测试未回退。
- [ ] **端到端验证**：分布式路径（RpcDistributedExecutor）空闲作业长时间运行不被误杀的验证——复用既有基建 `TestRpcDistributedExecutorE2E`（`nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/execution/TestRpcDistributedExecutorE2E.java`），扩展或新增空闲作业长跑用例（空闲时长 > restart 上限所需周期，断言不 FAILED）；若扩展面超出既有 E2E 基建能力，明确标注组件级验证范围 + 原因。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见本 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] AR-01 已修复（空闲/已完成任务不再误判停滞；真停滞仍触发）。
- [ ] 停滞路径与 FAILED 路径门控语义一致（或显式裁定记录）。
- [ ] 回归测试覆盖三态（空闲/已完成/真停滞）+ 门控语义。
- [ ] 类别清扫完成，无遗留依赖旧语义的消费点。
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift。
- [ ] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required。
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）修复后 `detectFailures` → `requestRecovery` 调用链在真停滞场景运行时连通（测试绿），（b）无空方法体/静默跳过/no-op 作为正常实现。
- [ ] `./mvnw test -pl nop-stream -am -T 1C`（全绿）
- [ ] `./mvnw compile -pl nop-stream -am`
- [ ] `node ai-dev/tools/check-nop-stream-invariants.mjs all` exit 0（JUnit 门禁 + output-contract/wiring 注册表；若改动影响注册表行号，同步注册表）
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream-core --severity high` exit 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream-runtime --severity high` exit 0
- [ ] `node ai-dev/tools/check-plan-checklist.mjs 2026-08-13-1930-2-nop-stream-liveness-stall-detection-fix.md --strict` exit 0
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### AR-03 `RunningTask.run()` finally 无条件移除槽位竞态（P2）

- Classification: `watch-only residual`（backlog 登记）
- Why Not Blocking Closure: 独立竞态面（窄窗口恢复时序），本 plan 停滞修复不依赖其解决；与 AR-01 叠加影响已在 backlog 触发条件注明。
- Successor Required: `no`

### AR-04 日志占位符错位（P2）

- Classification: `watch-only residual`（backlog 登记）
- Why Not Blocking Closure: 纯日志字段映射错误，不涉及行为；不阻塞本 plan 契约修复。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 若停滞判定方案选择"心跳时间戳解耦"，评估是否沉淀为不变式 #5 家族新成员（I6 评估 Loop Rule）。
- 分布式空闲作业长期运行 E2E（若仓库无对应基建，登记 backlog 触发条件）。

## Closure

Status Note: <<完成或关闭时填写>>
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<task id / daily log link / findings 摘要>>

Follow-up:

- <<只记录 non-blocking follow-up>>
