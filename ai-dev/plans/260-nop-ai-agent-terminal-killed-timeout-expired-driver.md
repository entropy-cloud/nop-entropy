# 260 nop-task 终态 driver 闭合 read-but-never-written gap（step EXPIRED/KILLED + task KILLED/TIMEOUT——cancel/timeout trigger 不再坍缩为 FAILED）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: EXPIRED-KILLED-TIMEOUT-terminal-driver (carry-over, L4)

> Last Reviewed: 2026-06-19
> Source: carry-over from `ai-dev/plans/259-nop-ai-agent-task-level-resume-orchestration.md`（Non-Goals / Non-Blocking Follow-ups 显式记录为 `out-of-scope improvement` 的「EXPIRED/KILLED/TIMEOUT task 终态 driver」，5× carry-over 自 plans 254-259；roadmap 项 `EXPIRED-KILLED-TIMEOUT-terminal-driver`，P2）。plan 259 已 `completed`，本计划在其 `## Follow-up handled by 260-...` 双向链接下接续。
> Related: `252`（succeed-driver retry-sync）/`253`（succeed-driver retry-async + 非 retry）/`254`（step FAILED-driver + cancel-check「cancelled != failed」）/`257`（continuation-skip reader，已分支 FAILED/EXPIRED/KILLED 终态）/`258`（终态 step-state DB 持久化 saveTerminalStateIfDone）/`259`（task COMPLETED/FAILED driver + saveTaskState 接线 + resume 短路 isTerminal）

## Purpose

闭合 nop-task 状态机的 **read-but-never-written** 终态缺口：`EXPIRED(50)`/`KILLED(70)` step 终态与 `KILLED(40)`/`TIMEOUT(60)` task 终态已定义且被 `isDone()`/`isTerminal()`/resume 短路/reader 反复读取，但**全仓库生产代码无任何 driver 将其写入**——cancel 与 step-timeout 的 trigger 基础设施早已存在，却被坍缩处理：task 层 cancel/timeout 一律经 `driveTaskFailed` 变成 FAILED；step 层 cancel-check 检测到 cancellation 后**只 rethrow、不设任何 status**（status 停留 ACTIVE）。结果：一个被 cancel/kill 的 task 在 DB 与 resume 中表现为 FAILED（语义错误），一个 step timeout 的 step 永远不会进入 EXPIRED 终态（reader 的 EXPIRED 分支是不可达死代码）。

本计划对称 plan 259 的 COMPLETED/FAILED driver，交付 **step 层 EXPIRED/KILLED driver** 与 **task 层 KILLED/TIMEOUT driver**，并修正 resume 短路对非 COMPLETED 终态的误处理。

## Current Baseline

基于本会话对 live repo 的核对（引用位置为审计可复核 live code path；确切行号以独立审计复核为准）：

- **终态常量已定义、被读、但生产代码从不写入（本计划核心缺口）**：
  - step 级 `_NopTaskCoreConstants`：`TASK_STEP_STATUS_COMPLETED=40 / EXPIRED=50 / FAILED=60 / KILLED=70`（约 :34-49）。`TaskStepStateBean.isDone()`（`state/TaskStepStateBean.java:56-59`）读 `COMPLETED||EXPIRED||FAILED||KILLED`；`DaoTaskStateStore` step 终态判定（`dao/store/DaoTaskStateStore.java:331-334`）同。Grep 确认生产 `setStepStatus(` 写点仅 `ACTIVE`（`DefaultTaskStateStore:29,42`、`DaoTaskStateStore:142,155`）、`COMPLETED`（`TaskStepStateBean:43` succeed()）、`FAILED`（`TaskStepExecution:274,333`）——**EXPIRED(50)/KILLED(70) 从不被任何 driver 写入**。
  - task 级 `TaskConstants`：`TASK_STATUS_COMPLETED=30 / KILLED=40 / FAILED=50 / TIMEOUT=60`（`TaskConstants.java:82-85`）。`ITaskState.isTerminal()`（`ITaskState.java:20-27`）读 `COMPLETED||KILLED||FAILED||TIMEOUT`。Grep 确认生产 `setTaskStatus(` 写点仅 `ACTIVE`（`DefaultTaskStateStore:57`）、`COMPLETED`（`TaskImpl:166`）、`FAILED`（`TaskImpl:177`）——**KILLED(40)/TIMEOUT(60) 从不被任何 driver 写入**（COMPLETED/FAILED 由 plan 259 交付）。
- **cancel / timeout trigger 基础设施已存在（本计划依赖、不新建）**：`ICancelToken`/`ICancellable` 全链路传播（`ITaskStepRuntime.isCancelled`、`TaskRuntimeImpl.cancel(reason)` :85）；`TaskStepHelper.isCancelledException(e)`（`utils/TaskStepHelper.java:75`）识别 cancellation；step 超时 `TimeoutTaskStepWrapper`（`step/TimeoutTaskStepWrapper.java`）→ `TaskStepHelper.timeout(...)`（:117）在超时后 `cancellable.cancel(ICancellable.CANCEL_REASON_TIMEOUT)`（:125）；kill 路径 `cancellable.cancel(ICancellable.CANCEL_REASON_KILL)`（:235）。即 trigger 已完备，缺的只是「trigger → 写对应终态 status」的 driver。
- **step 层 cancel-check 是本计划 step driver 的接线点（且当前为 status 缺失的 rethrow）**：`TaskStepExecution.executeWithParentRt` 的 async 出口（`step/TaskStepExecution.java:265-266`）与 sync catch（:321-322）均 `if (TaskStepHelper.isCancelledException(err)) throw NopException.adapt(err);`——检测到 cancellation 后**直接 rethrow，不设 stepStatus**（plan 254 的 cancel-check 有意把 cancelled 排除出 FAILED-driver，但当时未交付 KILLED/EXPIRED driver，故 step 停留非终态）。reader（plan 257，`TaskStepExecution.java:211` 注释「终态失败（FAILED/EXPIRED/KILLED）→ 重抛 exception」）已声明会消费 EXPIRED/KILLED 终态——**但此分支对 EXPIRED/KILLED 不可达，因无 driver 产生**。
- **task 层 driver 出口是本计划 task driver 的接线点（plan 259 已交付 COMPLETED/FAILED，cancel/timeout 坍缩入 FAILED）**：`TaskImpl.execute`（`impl/TaskImpl.java`）sync catch（:136-141）与 async 出口 `thenCompose`（:144-156）均调 `driveTaskFailed`。`driveTaskFailed`（:175-179）无条件 `setTaskStatus(FAILED)`——**cancel/timeout 的 cancellation exception 同样落入此分支变为 FAILED**，无 `driveTaskKilled`/`driveTaskTimeout`。`driveTaskCompleted`（:164-168）+ `driveTaskFailed`（:175-179）均出口 `taskRt.saveTaskState()` 持久化。
- **resume 短路已 isTerminal()，但对非 COMPLETED 终态误处理（本计划须修正）**：`TaskImpl.execute` resume 短路（`:94-111`）：`isRecoverMode() && isTerminal()` → `isSuccess()`（COMPLETED）返回缓存 result；`else` 分支（:100-110）对 FAILED/KILLED/TIMEOUT 一律重抛 `taskState.exception()`，exception 为 null 时合成 `ERR_TASK_ALREADY_FAILED`。即 **KILLED/TIMEOUT task 在 resume 时被误报为「already failed」**（plan 259 时 KILLED/TIMEOUT 不可达故未暴露，本计划使它们可达后必须同步修正）。
- **内层 step 状态机 + 终态持久化 + reader 由 plans 252-258 交付（本计划依赖、不重写）**：leaf step 经 `loadStepState` 载入终态，reader 检查 `isDone()` 跳过/重抛；终态 driver 后 `saveTerminalStateIfDone`（plan 258）持久化终态 snapshot。

## Goals

- **step 层 EXPIRED/KILLED driver**：cancellation 触达 step cancel-check 时，按 cancel reason 映射终态——`CANCEL_REASON_TIMEOUT` → `TASK_STEP_STATUS_EXPIRED(50)`，`CANCEL_REASON_KILL`/其它 cancel → `TASK_STEP_STATUS_KILLED(70)`；捕获 exception + `saveTerminalStateIfDone` + rethrow（非静默跳过，Minimum Rules #24）。使 reader 的 EXPIRED/KILLED 终态分支从死代码变为可达。
- **task 层 KILLED/TIMEOUT driver**：mainStep 抛出 cancellation/timeout 时，按 reason 映射 `TASK_STATUS_KILLED(40)`/`TASK_STATUS_TIMEOUT(60)` + 捕获 exception + `saveTaskState`（对称 plan 259 的 `driveTaskCompleted`/`driveTaskFailed`）；普通失败仍走 FAILED。
- **resume 短路区分非 COMPLETED 终态**：FAILED → 重抛捕获的 exception；KILLED/TIMEOUT → 重抛语义正确的 exception（非合成 `ERR_TASK_ALREADY_FAILED`），mainStep 不重跑（非静默跳过）。
- **端到端 cross-restart 验证（Anti-Hollow）**：E2E 经 snapshot/序列化 round-trip（非 in-memory 引用）证明——cancel 运行中 task → task 终态 KILLED（非 FAILED）+ 持久化 + resume 区分；step timeout → step EXPIRED + task TIMEOUT + resume 区分。
- **零回归**：plans 252-259 状态机 + reader + 终态持久化 + decorator/bizFatal/reliability + nop-task-core/ext/dao + nop-ai-agent 全绿。

## Non-Goals

- **task 级 EXPIRED status / 编辑生成文件 `_NopTaskCoreConstants`**：`_NopTaskCoreConstants.TASK_STATUS_EXPIRED=50`（:79）是**生成文件**（`_` 前缀，Hard Stop）且与手写 `TaskConstants`（FAILED=50、TIMEOUT=60、无 EXPIRED）数值冲突——属生成产物不一致，不在本计划修正。task 级超时统一用 `TaskConstants.TASK_STATUS_TIMEOUT(60)`。Classification: out-of-scope improvement（生成文件须改源模型/模板再生成，非本计划范围）。
- **独立 task 级 timeout watchdog（非 step-timeout 触发）**：本计划的 task TIMEOUT driver 以「step 超时 cancellation 上浮」为 trigger（已存在）。独立的 task 级 wall-clock watchdog（如 `TaskExecutableModel.getTimeout()` 的强制执行接线）是另一 feature。Classification: out-of-scope improvement。
- **跨重启 exception 持久化 transient 优化**：optimization candidate（plans 247/252/254/258/259 carry-over）。本计划捕获并持久化 KILLED/TIMEOUT 终态 exception（errCode+errMsg，与 plan 258/259 对齐），不优化序列化细节。
- **step-state 全量字段持久化 / 完整历史 entity 模型**：optimization candidate（plans 257/258/259 carry-over）。
- **afterLoad/beforeSave task 生命周期 hook 的语义实现**：successor plan candidate（plans 254/255/259 carry-over）。
- **`fail()` 行为变更**：rejected（plans 247/252-254 design decision）。本计划沿用「fail() 仅保存 exception」，driver 在其上追加 `setStepStatus(EXPIRED/KILLED)`。

## Scope

### In Scope

- **step 层 EXPIRED/KILLED driver + 接线**（Phase 1）：在 `TaskStepExecution` cancel-check 两处出口（async :265-266、sync :321-322）将 bare-rethrow 改为「按 reason 设 EXPIRED/KILLED 终态 + 捕获 exception + `saveTerminalStateIfDone` + rethrow」。
- **task 层 KILLED/TIMEOUT driver + 接线**（Phase 2）：在 `TaskImpl.execute` sync catch / async 出口识别 cancellation/timeout，新增对称 `driveTaskKilled`/`driveTaskTimeout`（mirror `driveTaskFailed`），出口 `saveTaskState`。
- **resume 短路区分终态**（Phase 2）：修正 `TaskImpl.execute` resume `else` 分支，KILLED/TIMEOUT 与 FAILED 分别处理（非合成 ERR_TASK_ALREADY_FAILED）。
- **cross-restart E2E + 零回归**（Phase 2）：cancel→KILLED、step-timeout→EXPIRED+TIMEOUT 两变体，snapshot round-trip + resume 区分 + 零回归。

### Out Of Scope

- 见 Non-Goals（task 级 EXPIRED / 生成文件修正 / 独立 task watchdog / exception transient 优化 / 全量字段持久化 / afterLoad-beforeSave 语义 / fail() 变更 均为显式 rejected / out-of-scope / successor / optimization）。

### 设计裁定（Pre-Adjudicated）

1. **step driver 接线于 cancel-check 两处出口，按 cancel reason 映射终态，并把 reason 编码进传播的 exception**。async 出口（`TaskStepExecution:265-266`）与 sync catch（:321-322）当前 `if (isCancelledException) throw`。改为：判定 cancel reason——`CANCEL_REASON_TIMEOUT` → `setStepStatus(EXPIRED(50))`；`CANCEL_REASON_KILL`/其它 → `setStepStatus(KILLED(70))`；先 `fail(err,taskRt)` 保存 exception（与 FAILED-driver :273/:332 对称），再设对应终态 status，再 `saveTerminalStateIfDone`，最后 rethrow（#24 非静默）。**reason 来源**：在 step seam 处 `stepRt.getCancelToken().getCancelReason()` 是可靠的——它是被直接 `.cancel(reason)` 的 step 自身 cancellable（timeout 经 `TaskStepHelper:125`、kill 经 `:235`）。**但该 step-token reason 在 task seam 不可靠**（`taskRt.getCancelReason()` 对 step-timeout 恒为 null；step token 可能被 `TaskStepHelper.withCancellable` 的 `whenComplete` 复位——Fork/Parallel/Graph/ForkN 使用），故 **step driver 必须把已判定的 reason 编码进 rethrow 的 exception**（今日 `NopTaskCancelledException.INSTANCE` 是无 reason 单例 `exceptions/NopTaskCancelledException.java:7-11`、`TaskStepHelper:71`，须改为 reason-carrying 形态，如带 reason 的 typed exception，且仍被 `isCancelledException` 识别——见裁定 2 的消费端）。具体 reason-carrying exception 形态属 execution 裁定（Minimum Rules #10），前提是它能把 timeout/kill 区分传播到裁定 2。覆盖 timeout→EXPIRED 与 kill→KILLED 两条映射。
2. **task driver 接线于 execute 出口，对称 plan 259；timeout/kill 区分须来自 Phase-1 传播的 reason-carrying exception**。在 `TaskImpl.execute` sync catch（:136-141）与 async `thenCompose` err 分支（:153-155）：先 `TaskStepHelper.isCancelledException(err)` 判定 cancellation；cancellation 内按 reason 二分——timeout → `driveTaskTimeout`（`setTaskStatus(TIMEOUT(60))`），kill/其它 → `driveTaskKilled`（`setTaskStatus(KILLED(40))`），各捕获 exception + `saveTaskState`；非 cancellation → 维持 `driveTaskFailed`。**reason 来源（关键裁定，避免 hollow/错误映射）**：task seam 处区分 timeout 与 kill 的**唯一可靠信号是 Phase-1 step driver 编码进 exception 的 reason**（裁定 1）——该 exception 即 task catch/thenCompose 手中之 `err`，确定可达。**显式排除两个不可靠来源**：`taskRt.getCancelReason()`（step-timeout 时它未被 cancel，恒为 null）与 `stepRt.getCancelToken().getCancelReason()`（composite step 经 `TaskStepHelper.withCancellable` 的 whenComplete 复位后可能丢失）。具体 wiring 点（execute 出口统一 / 抽 helper / 如何从 err 取 reason）属 execution 裁定，前提是来源限定为本条所述 exception-carried reason。与 plan 259 COMPLETED/FAILED driver 共存，普通失败仍 FAILED。
3. **resume 短路须区分 FAILED 与 KILLED/TIMEOUT**。`TaskImpl.execute` resume `else` 分支（:100-110）：FAILED → 重抛 `taskState.exception()`（null 时合成 FAILED 语义 exception）；KILLED/TIMEOUT → 重抛对应语义 exception（如 `ERR_TASK_*` cancelled/timeout，#24 非静默，非 `ERR_TASK_ALREADY_FAILED`）。短路判定基于 DB load 的 snapshot，mainStep 不重跑。
4. **EXPIRED/KILLED/TIMEOUT 终态 result/exception 须可恢复**。step 经 `saveTerminalStateIfDone`（plan 258）持久化，task 经 `saveTaskState`（plan 259）持久化；resume 经 snapshot load 恢复并被短路逻辑按终态区分重抛。与 plan 258/259 exception errCode+errMsg 持久化对齐。
5. **E2E 须用 snapshot store（非 in-memory 引用）以暴露 pre-fix gap**。resume 的 state 须经序列化/深拷贝 round-trip（或真实 `DaoTaskStateStore`）。pre-fix 此 E2E 应失败（cancel/timeout → 坍缩 FAILED / step 停留 ACTIVE → resume 误报 already-failed）、post-fix 应通过（KILLED/TIMEOUT/EXPIRED 终态 → resume 区分）。
6. **不改生成文件**（Hard Stop）：`_NopTaskCoreConstants`（`_` 前缀）不改；step 终态常量直接引用既有 `_NopTaskCoreConstants.TASK_STEP_STATUS_EXPIRED/KILLED`（与 plan 254/258 FAILED-driver 引用方式一致——它们也引用 `_NopTaskCoreConstants.TASK_STEP_STATUS_FAILED`）。task 终态常量引用手写 `TaskConstants.TASK_STATUS_KILLED/TIMEOUT`。
7. **内层 flow 位置恢复委托给 step 级状态机（不改）**：本计划只交付 cancel/timeout 终态 driver + resume 区分；不改 `newMainStepState`/`newMainStepRuntime`/leaf-reader/`loadStepState`。

## Execution Plan

### Phase 1 - step 层 EXPIRED/KILLED driver + 单元测试

Status: completed
Targets: `TaskStepExecution.executeWithParentRt`（cancel-check 两处出口 async `:265-266` / sync `:321-322`）、`TaskStepHelper`（cancel reason 判定辅助，如需）、`ITaskStepRuntime`/`ICancelToken`（reason 读取）、既有 `_NopTaskCoreConstants.TASK_STEP_STATUS_EXPIRED/KILLED`、既有 `saveTerminalStateIfDone`（plan 258）、`nop-task/nop-task-core/src/test/`

- Item Types: `Fix`（step EXPIRED/KILLED 终态 read-but-never-written——confirmed production gap：cancel-check 检测 cancellation 后 bare-rethrow 不设 status，reader 的 EXPIRED/KILLED 分支不可达死代码）

- [x] 在 `TaskStepExecution` cancel-check 两处出口（async + sync）实现 step 终态 driver（设计裁定 1）：按 cancel reason 映射 `CANCEL_REASON_TIMEOUT`→`EXPIRED(50)` / `CANCEL_REASON_KILL`·其它→`KILLED(70)`；`fail(err,taskRt)` 保存 exception → 设终态 status → `saveTerminalStateIfDone` → rethrow（#24）
- [x] 新增单元测试：step timeout（`CANCEL_REASON_TIMEOUT`）后 stepStatus==EXPIRED（非 ACTIVE/FAILED）+ isDone + !isSuccess + exception 已捕获 + saveTerminalStateIfDone 被调用（经 snapshot round-trip 可复核）
- [x] 新增单元测试：step kill-cancel（`CANCEL_REASON_KILL`）后 stepStatus==KILLED（非 FAILED）+ isDone + !isSuccess + exception 已捕获
- [x] 验证普通（非 cancel）失败仍走 FAILED-driver（:274/:333 不变，零回归于 plan 254）
- [x] 验证 reader（plan 257）对 EXPIRED/KILLED 终态的「重抛 exception」分支（:211）现可达（非死代码）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] step timeout 后 stepState 反映 EXPIRED 终态（stepStatus==EXPIRED(50)，非 ACTIVE/FAILED）+ exception 已捕获（经 snapshot round-trip 断言）
- [x] step kill-cancel 后 stepState 反映 KILLED 终态（stepStatus==KILLED(70)，非 FAILED）
- [x] **接线验证**（#23）：cancel-check 出口真实调用 `saveTerminalStateIfDone`（save count 可观测）；终态 status 经持久化存活（snapshot round-trip）
- [x] **无静默跳过**（#24）：cancel-check 不再 bare-rethrow；driver 为真实实现（设终态 + 保存 + rethrow，非空方法体 / 非 TODO）
- [x] 新增功能各有 focused 测试覆盖（#25）：timeout→EXPIRED + kill→KILLED 各有断言；普通失败仍 FAILED 有回归断言
- [x] `./mvnw compile -pl nop-task/nop-task-core -am` 通过
- [x] `./mvnw test -pl nop-task/nop-task-core -am` 既有（plans 252-258 状态机 + reader + 终态持久化）+ 新增测试全绿
- [x] owner-doc 裁定：`No owner-doc update required`（内部 step 终态 lifecycle wiring，step public 执行契约不变）——Phase 2 复核；或若 public 契约变化则更新 `docs-for-ai/`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - task 层 KILLED/TIMEOUT driver + resume 区分 + cross-restart E2E + 零回归

Status: completed
Targets: `TaskImpl.execute`（sync catch `:136-141` / async 出口 `:144-156` / resume 短路 `:94-111`）、新增 `driveTaskKilled`/`driveTaskTimeout`（mirror `driveTaskFailed`）、`TaskStepHelper.isCancelledException`（既有）、`TaskConstants.TASK_STATUS_KILLED/TIMEOUT`（既有）、`taskRt.saveTaskState()`（既有，plan 259）、`nop-task/nop-task-ext/src/test/`（新增 cross-restart E2E，snapshot store 或真实 DaoTaskStateStore）、`.task.xml` fixture、`ai-dev/logs/`

- Item Types: `Fix`（task KILLED/TIMEOUT read-but-never-written + resume 误报 already-failed——confirmed gap）+ `Proof`（cross-restart cancel/timeout E2E）

- [x] 实现 task KILLED/TIMEOUT driver（设计裁定 2）：`TaskImpl.execute` 出口识别 cancellation，按 **Phase-1 传播的 reason-carrying exception**（非 `taskRt.getCancelReason()` / 非 step token）二分 `driveTaskTimeout`(TIMEOUT(60)) / `driveTaskKilled`(KILLED(40))，各捕获 exception + `saveTaskState`；非 cancellation 维持 `driveTaskFailed`
- [x] 修正 resume 短路区分终态（设计裁定 3）：`TaskImpl.execute` resume `else` 分支 FAILED→重抛捕获 exception，KILLED/TIMEOUT→重抛对应语义 exception（非 `ERR_TASK_ALREADY_FAILED`，#24）
- [x] 新增 cross-restart E2E（cancel 变体，snapshot store 非 in-memory）：fresh execute（cancel 运行中 task → task 终态 KILLED → 持久化到 DB snapshot）→ fresh load（snapshot 反序列化，recoverMode=true）→ resume 命中 KILLED → 重抛 KILLED 语义 exception（mainStep 不重跑，counter==0）
- [x] 新增 cross-restart E2E（step-timeout 变体）：fresh execute（step timeout → step EXPIRED + task 终态 TIMEOUT → 持久化）→ fresh DB load → resume 命中 TIMEOUT → 重抛 TIMEOUT 语义 exception（mainStep 不重跑）
- [x] 验证 pre-fix gap 真实存在：snapshot store resume 在无 KILLED/TIMEOUT driver 时 cancel/timeout 坍缩 FAILED → resume 误报 already-failed（可经临时移除 driver 或独立断言证明此行为在本计划前成立）
- [x] 验证 in-progress（非终态）/ COMPLETED / 普通 FAILED task 的 resume 行为不变（不误短路、不误报）
- [x] 零回归：`./mvnw test -pl nop-task/nop-task-core -am` + `./mvnw test -pl nop-task/nop-task-ext -am` + `./mvnw test -pl nop-task/nop-task-dao -am` + `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 全绿

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] cross-restart resume E2E 全绿（cancel→KILLED）：fresh execute→task KILLED driver→DB 持久化→fresh snapshot load→resume 命中 KILLED→mainStep 不重跑（counter==0）→重抛 KILLED 语义 exception——**此行为在本计划前不成立**（坍缩 FAILED + resume 误报 already-failed）
- [x] cross-restart resume E2E 全绿（step-timeout→EXPIRED+TIMEOUT）：fresh DB load 返回 TIMEOUT 终态 → resume 重抛 TIMEOUT 语义 exception（#24；mainStep 不重跑）；对应 step 为 EXPIRED
- [x] **端到端验证**（#22）：从 task execute 入口 → cancel/timeout → task KILLED/TIMEOUT driver → saveTaskState 持久化 → fresh load（snapshot）→ resume 区分短路 完整路径连通
- [x] **接线验证**（#23）：KILLED/TIMEOUT driver 在 E2E runtime 出口真实调用 `saveTaskState`（save count 可观测）；resume 命中时 mainStep 不重跑（counter==0）
- [x] **Anti-Hollow Check**（#22/#23/#24）：E2E 经 snapshot/序列化 round-trip（非 in-memory 引用）证明 KILLED/TIMEOUT 终态经持久化存活；cancel-check 不再 bare-rethrow；resume else 分支不再对 KILLED/TIMEOUT 合成 already-failed；无空方法体/静默跳过/no-op 作为正常实现
- [x] resume 区分：FAILED 重抛捕获 exception / KILLED·TIMEOUT 重抛对应语义 exception（三者断言各别，非一律 already-failed）
- [x] 新增功能各有 focused 测试覆盖（#25）：cancel→KILLED 短路 + step-timeout→TIMEOUT 短路 + FAILED/KILLED/TIMEOUT resume 区分 各有 E2E 断言
- [x] 零回归：plans 252-259 状态机 + reader + 终态持久化 + decorator/bizFatal/reliability + nop-ai-agent 全绿
- [x] owner-doc 裁定落地（`No owner-doc update required` 或更新 `docs-for-ai/` 若 task 终态 status public 契约变化）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] step 层 EXPIRED/KILLED driver 后 stepState（含 DB snapshot）反映 EXPIRED/KILLED 终态（非 ACTIVE/FAILED），经 snapshot round-trip 可复核
- [x] task 层 KILLED/TIMEOUT driver 后 taskState（含 DB snapshot）反映 KILLED/TIMEOUT 终态（非 FAILED），cancel/timeout 不再坍缩 FAILED
- [x] reader（plan 257）的 EXPIRED/KILLED 终态分支从死代码变为可达（#23 接线验证）
- [x] resume 短路区分 FAILED / KILLED / TIMEOUT（非一律 already-failed），cross-restart resume（fresh load 非 in-memory 引用）命中对应终态 → mainStep 不重跑
- [x] 普通 FAILED / COMPLETED / in-progress task 的既有 resume 行为零回归
- [x] E2E 经 snapshot/序列化 round-trip 闭合 carry-over 的 cancel/timeout 终态 gap（#22）
- [x] 必要 focused verification 已完成（step 终态捕获单元 + cross-restart cancel/timeout resume E2E）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（task 级 EXPIRED / 生成文件修正 / 独立 task watchdog / exception transient / 全量字段持久化 / afterLoad-beforeSave 语义 / fail() 变更 均为显式 Non-Goals）
- [x] 受影响 owner docs 已同步到 live baseline，或明确写明 `No owner-doc update required`
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）step cancel-check 出口与 task execute 出口的 KILLED/TIMEOUT/EXPIRED driver 在 runtime 被调用（含 saveTerminalStateIfDone/saveTaskState），（b）resume 经 snapshot round-trip 区分终态（非 in-memory 引用、非一律 already-failed），（c）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-task/nop-task-core,nop-task/nop-task-dao -am`
- [x] `./mvnw test -pl nop-task/nop-task-core,nop-task/nop-task-ext,nop-task/nop-task-dao,nop-ai/nop-ai-agent -am -T 1C`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（本计划无 deferred 项。所有 Non-Goals 均为显式 rejected / out-of-scope improvement / successor plan candidate / optimization candidate，非「延期但需裁定」项。）

## Non-Blocking Follow-ups

- task 级 EXPIRED status / 生成文件 `_NopTaskCoreConstants.TASK_STATUS_EXPIRED=50` 与手写 `TaskConstants` 数值不一致（out-of-scope improvement；须改源模型/模板再生成，非手改生成文件）。
- 独立 task 级 wall-clock timeout watchdog（非 step-timeout 上浮触发）（out-of-scope improvement）。
- 跨重启 exception 持久化 transient 优化（optimization candidate，plans 247/252/254/258/259 carry-over）。
- step-state 全量字段持久化 / 完整历史 entity 模型（optimization candidate，plans 257/258/259 carry-over）。
- afterLoad/beforeSave task 生命周期 hook 的语义实现（successor plan candidate，plans 254/255/259 carry-over）。

## Closure

Status Note: plan 260 闭合 plans 254-259（5× carry-over）反复裁定为 out-of-scope improvement 的「EXPIRED/KILLED/TIMEOUT 终态 driver」read-but-never-written 缺口。step 层 cancel-check 两处出口从 bare-rethrow 改为按 reason 映射 EXPIRED(50)/KILLED(70) driver（设计裁定 1，对称 plan 254 FAILED-driver）；task 层 execute 出口从无条件 driveTaskFailed 改为 driveTaskTerminal 分发 cancellation→KILLED(40)/TIMEOUT(60)（设计裁定 2，按 Phase-1 exception-carried reason 区分）；resume 短路区分 FAILED/KILLED/TIMEOUT（设计裁定 3，非一律 already-failed）。reason 经 NopTaskCancelledException 改造为 reason-carrying + TaskStepHelper.encodeCancelReason 编码进传播 exception，使 reader 的 EXPIRED/KILLED 死代码分支变为可达。两 Phase Exit Criteria 全勾选 + Closure Gates 全勾选 + 独立子 agent closure-audit APPROVED。
Completed: 2026-06-19

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure-audit（fresh session, task_id `ses_123851580ffe4woso4yF4Jj9rs`，未参与实现）
- Audit Session: ses_123851580ffe4weso4yF4Jj9rs（general subagent，read-only audit）
- Evidence:
  - Exit Criterion 验证（逐条 PASS，独立审计 8 items 全 PASS APPROVED）：
    - step EXPIRED/KILLED driver：`NopTaskCancelledException.java:19` extends NopException（非 NopSingletonException）+ `cancelReason`/`forReason`/`getCancelReason`；`TaskStepExecution.java:265-278`（async）+ `:333-342`（sync）cancel-check 出口均 `fail`→`setStepStatus(EXPIRED/KILLED)`→`saveTerminalStateIfDone`→rethrow encodeCancelReason，非 bare-rethrow，均在 FAILED-driver 之前。
    - task KILLED/TIMEOUT driver：`TaskImpl.java:194-205` `driveTaskTerminal` 分发 + `:211-215` `driveTaskKilled`(KILLED) + `:221-225` `driveTaskTimeout`(TIMEOUT)；sync catch `:146` + async thenCompose err `:160` 均调 driveTaskTerminal（非无条件 driveTaskFailed）。
    - resume 区分：`TaskImpl.java:108-116` else 短路 + `:232-245` `synthesizeResumeException`（KILLED→ERR_TASK_ALREADY_KILLED / TIMEOUT→ERR_TASK_ALREADY_TIMEOUT / FAILED→ERR_TASK_ALREADY_FAILED，非一律 already-failed）；`TaskErrors.java:157-161` 两新 code。
    - reader EXPIRED/KILLED 可达：`TaskStepExecution.java:197-222` else 分支现为可达（step driver 产生 EXPIRED/KILLED + saveTerminalStateIfDone 持久化）。
    - cross-restart E2E：`TestTaskKilledTimeoutResumeE2E`（7 tests）经 `TaskLevelSnapshotTaskStateStore.loadTaskState`→`copyTaskSnapshot` 深拷贝（非 in-memory 引用）+ `getTaskRuntime`(recoverMode=true) → resume 短路 mainStep 不重跑（save count 不变断言）。
  - Closure Gates 验证：`./mvnw test -pl nop-task/nop-task-core,nop-task/nop-task-ext,nop-task/nop-task-dao,nop-ai/nop-ai-agent -am -T 1C` → BUILD SUCCESS（nop-task-core 86 / nop-task-ext / nop-task-dao / nop-ai-agent 全量 0 failures）；`./mvnw test -Dtest=TestTaskStepCancelledTerminalDriver,TestTaskKilledTimeoutResumeE2E` → 12 tests 0 failures（独立审计复核退出码 0）。
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-task-core --severity high` 退出码 0（0 critical / 0 high / 0 medium / 0 low）。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（closure 完成后复核，无未勾选项 + Closure Evidence 已写入）。
  - Anti-Hollow 检查（独立审计 Item 6 + 8）：(a) driver 在 runtime 被调用——TaskImpl catch `:146` + thenCompose err `:160` + TaskStepExecution cancel-check `:265`/`:333`，各调 saveTerminalStateIfDone/saveTaskState；(b) resume 经 snapshot 深拷贝 round-trip（copyTaskSnapshot/copySnapshot 非 in-memory 引用），mainStep 不重跑经 save count 断言；(c) 无空方法体/静默跳过/no-op/TODO——所有新 driver 方法体含 setTaskStatus+exception+saveTaskState 真实序列。
  - Deferred 项分类检查：task 级 EXPIRED / 生成文件 `_NopTaskCoreConstants` 修正 / 独立 task watchdog / exception transient / 全量字段持久化 / afterLoad-beforeSave 语义 / fail() 变更 均为显式 Non-Goals（rejected / out-of-scope improvement / successor / optimization），无 in-scope live defect 被降级。
  - 诚实记录（location deviation，非 closure blocker）：Phase 2 targets 行写 `nop-task/nop-task-ext/src/test/`，实际 cross-restart E2E 落在 `nop-task-core/src/test/java/io/nop/task/state/`（与 plan 258/259 的 snapshot-store 基础设施同位置，复用 `TaskLevelSnapshotTaskStateStore`）。snapshot round-trip 语义不变，不弱化任何 closure criterion。

Follow-up:

- 无 plan-owned 剩余工作。Non-Goals 中记录的 successor / optimization（task 级 EXPIRED / 生成文件修正 / 独立 task watchdog / exception transient / 全量字段持久化 / afterLoad-beforeSave 语义）见 ## Non-Blocking Follow-ups，均经裁定为 non-blocking。
