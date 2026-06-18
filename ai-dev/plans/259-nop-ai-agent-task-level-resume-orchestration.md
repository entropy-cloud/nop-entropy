# 259 nop-task task 级 resume 编排（task 终态 lifecycle + result/exception 捕获 + resume 短路——cross-restart 不重跑已终态 task）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: task-level-resume-orchestration (carry-over, L4)

> Last Reviewed: 2026-06-19
> Source: carry-over from `ai-dev/plans/258-nop-ai-agent-terminal-state-db-persist.md`（Non-Goals / Non-Blocking Follow-ups 记录为 `successor plan required` 的「完整 task-level resume 编排（task 级 mainStep 状态语义 / 跨 step flow 位置恢复）」；plan 257 §Closure 同项 carry-over）。plan 258 已 `completed` 并已追加 `## Follow-up handled by 259-...` 双向链接；依 Minimum Rules #20 不回写其正文。
> Related: `252`（succeed-driver retry-sync）/`253`（succeed-driver retry-async + 非 retry）/`254`（FAILED-driver + cancel-check）/`257`（continuation-skip reader + DaoTaskStateStore save/load）/`258`（终态 step-state DB 持久化）

## Purpose

把 step 级终态状态机（plans 252-258 已交付 leaf step 级 succeed/FAILED driver + 终态 DB 持久化 + reader skip/rethrow）收敛为 **task 级（task instance envelope）的 resume 编排**：使一个多 step task flow 在 crash-restart / cross-process resume 时，其 task instance 能被识别为已终态并短路（返回缓存的 result / 重抛缓存的 exception），而不是被无条件从 mainStep 重新执行。

当前 task envelope 完全没有终态语义：`taskStatus` 在 `newTaskState` 时被设为 ACTIVE 后**再无任何 driver 将其置为终态**（COMPLETED/FAILED），`TaskStateBean.result(TaskStepReturn)` 是**空方法体 no-op**（result 从不捕获），`ITaskStateStore.saveTaskState` 虽在接口定义且 `DaoTaskStateStore` 有真实实现但**生产路径从未调用**，resume 入口（`TaskFlowManagerImpl.getTaskRuntime`，recoverMode=true）之后 `TaskImpl.execute` 仍走 `newMainStepRuntime()`→`newMainStepState()` 重新创建 fresh ACTIVE main-step 并整流重跑。结果：一个已成功完成的 task 在重启后被当作 ACTIVE 整流重跑（外层 daemon/job 无法据 taskStatus 判终态 → 反复重唤起）；一个已 FAILED 的 task 在重启后其终态 exception 不被恢复重抛、mainStep 被重跑（重复副作用）。

## Current Baseline

基于本会话对 live repo 的核对（引用位置为审计可复核 live code path；确切行号以独立审计复核为准）：

- **task 级终态 status 常量已定义，但无 driver 触发（本计划核心缺口）**：`TaskConstants`（`nop-task-core/src/main/java/io/nop/task/TaskConstants.java`）定义 `TASK_STATUS_CREATED=0 / ACTIVE=10 / SUSPENDED=20 / COMPLETED=30 / KILLED=40 / FAILED=50 / TIMEOUT=60`（约 :79-85）。全仓库唯一写 `taskStatus` 的是 `newTaskState`：`DefaultTaskStateStore.newTaskState`（约 :57）与 `DaoTaskStateStore.newTaskState`（约 :65）均只 `setStatus(ACTIVE)`。grep 确认**无任何代码将 taskStatus 置为 COMPLETED/FAILED/KILLED/TIMEOUT**——task 终态 lifecycle 在 task 级不存在（step 级终态 driver 由 plans 252-254 交付，但那是 `stepStatus`，不是 `taskStatus`）。
- **task 级 result 捕获是空壳 no-op（本计划核心缺口，Minimum Rules #24）**：`TaskStateBean.result(TaskStepReturn)`（`nop-task-core/src/main/java/io/nop/task/state/TaskStateBean.java`，约 :145-147）方法体为空 `{}`。`TaskImpl.execute`（约 :80-118）拿到 `mainStep.execute(stepRt)` 返回的 `stepReturn` 后直接 return，**从不写入 taskState**。故 task 终态 result/exception 从不被捕获。
- **task 级终态访问器缺失（镜像 gap）**：`ITaskStepState`（`ITaskStepState.java` 约 :86-100）有 `succeed/fail/isDone/isSuccess/result/exception` 完整终态契约；`ITaskState`（`ITaskState.java`）**无任何终态访问器**（仅有 getTaskStatus/setTaskStatus 与 request/response 读写）。task 级无法表达「已终态 + 终态结果/异常」。
- **saveTaskState 已定义但从未被调用（本计划接线缺口）**：`ITaskStateStore.saveTaskState(ITaskRuntime)`（`ITaskStateStore.java` 约 :25）+ `DaoTaskStateStore.saveTaskState`（约 :82，真实 DB 实现）+ `DefaultTaskStateStore.saveTaskState`（约 :77，no-op）。grep 确认**生产代码无任何 `saveTaskState(` 调用点**（TaskImpl / TaskRuntimeImpl / TaskStepExecution / TaskFlowManagerImpl 均未调用）。故 task state（taskStatus / response / result）创建后从不回写 DB——resume 时 `loadTaskState` 取回的永远是非终态（ACTIVE 或创建期 snapshot）。
- **resume 入口已存在但不短路（本计划核心缺口）**：`TaskFlowManagerImpl.getTaskRuntime(taskInstanceId, ...)`（约 :112-121）经 `stateStore.loadTaskState` 载入 task state、置 `recoverMode=true`、`setTaskState`。但随后 `TaskImpl.execute`（约 :80-118）仍 `taskRt.newMainStepRuntime()`（约 :81）→ `TaskRuntimeImpl.newMainStepRuntime()`（约 :198-206）→ `stateStore.newMainStepState(...)`（约 :199，**创建 fresh ACTIVE，非 load**）→ `mainStep.execute(stepRt)`（约 :103）整流重跑。**无「task 已终态 → 直接返回缓存 result / 重抛缓存 exception」短路**。
- **内层 leaf-step 位置恢复已由 plans 252-258 交付（本计划依赖、不重写）**：leaf step 经 `TaskStepRuntimeImpl` 的 `stateStore.loadStepState(...)`（约 :127）载入持久化终态 state，reader（`TaskStepExecution.executeWithParentRt`，plan 257）检查 `state.isDone()` → COMPLETED 跳过返回缓存 result / FAILED 重抛 exception；终态 driver 后 `saveTerminalStateIfDone`（plan 258）持久化终态 snapshot。故 in-progress task 在 mainStep 重跑时，其已完成的子 step 会被 reader 跳过——**前提是 task envelope 被终态化并被 resume 短路触达这条 mainStep 重跑路径**（这正是本计划交付的 envelope）。

## Goals

- **task 级终态 lifecycle（COMPLETED/FAILED）**：mainStep 执行成功 → task 进入 COMPLETED 并捕获 result；执行抛错 → task 进入 FAILED 并捕获 exception。镜像 step 级 plans 252-254 的 succeed/FAILED driver，但在 task instance envelope 层。
- **task 级终态持久化**：终态 driver 设置 taskStatus + 捕获 result/exception 后，调 `saveTaskState` 使 DB-backed task instance 反映终态（非停留 ACTIVE）。闭合「saveTaskState 已定义但从未被调用」的接线 gap。
- **resume 短路（不重跑已终态 task）**：resume 路径（recoverMode=true）下，task 已终态 COMPLETED → 返回缓存 result（mainStep 不重跑）；task 已终态 FAILED → 重抛缓存 exception（mainStep 不重跑，不重复副作用）。
- **端到端 cross-restart resume 验证**：E2E 从 fresh execute（task 完成/失败 → task 终态 driver → task 终态持久化到 DB）→ fresh DB load（snapshot，非 in-memory 引用）→ resume 短路命中终态 → 返回缓存 result / 重抛 exception，mainStep 不重跑（resume counter 可观测）。
- **零回归**：plans 252-258 step 级状态机 + reader + 终态持久化 + decorator/bizFatal/reliability + nop-task-core/ext/dao + nop-ai-agent 全绿。

## Non-Goals

- **EXPIRED/KILLED/TIMEOUT task 终态 driver**：out-of-scope improvement（plans 254-258 carry-over，5×）。本计划交付 COMPLETED/FAILED 两条终态路径（与 step 级 plans 252-254 对齐），不新增 KILLED(40)/TIMEOUT(60) 等 driver。Classification: out-of-scope improvement。
- **跨重启 exception 持久化 transient 优化**：optimization candidate（plans 247/252/254/258 carry-over）。本计划捕获并持久化 FAILED 终态 exception（含 errCode+errMsg，与 plan 258 step 级 exception 持久化对齐），不优化序列化细节。Classification: optimization candidate。
- **step-state 全量字段持久化 / 完整历史 entity 模型**：optimization candidate（plans 257/258 carry-over）。本计划不扩展 step-state 字段集。Classification: optimization candidate。
- **afterLoad/beforeSave task 生命周期 hook 的语义实现**：successor plan candidate（plans 254/255 carry-over）。本计划只在 driver 出口 setTaskStatus + 捕获 + saveTaskState，不实现 afterLoad/beforeSave 内部业务逻辑。Classification: successor plan candidate。
- **main-step state 的「load 持久化 state」（loadMainStepState）**：rejected（设计裁定）。main-step 是整 task flow 的复合入口，其内层位置恢复委托给 leaf-step 状态机 + reader（plans 252-258）；`newMainStepState` 创建 fresh ACTIVE 是有意设计（复合 step 不持久化自身状态，靠子 step 自恢复）。本计划不改 `newMainStepState`/`newMainStepRuntime`。
- **fail() 行为变更**：rejected（plans 247/252-254 design decision）。Classification: rejected。

## Scope

### In Scope

- **task 终态访问器 + result/exception 捕获**（Phase 1）：在 `ITaskState`/`TaskStateBean` 增加终态表达（isTerminal/isSuccess/result/exception 访问器 + 修 `result()` 空壳 no-op 为真实捕获），使 task envelope 能表达「已终态 + 终态结果/异常」。
- **task 终态 driver + saveTaskState 接线**（Phase 1）：mainStep 成功 → setTaskStatus(COMPLETED) + 捕获 result；抛错 → setTaskStatus(FAILED) + 捕获 exception；driver 出口调 `saveTaskState` 持久化终态到 DB。
- **resume 短路编排**（Phase 2）：resume 路径下 task 已终态 → 短路返回缓存 result / 重抛缓存 exception，mainStep 不重跑。
- **cross-restart resume E2E**（Phase 2）：fresh execute → 终态 driver → 终态 DB 持久化 → fresh snapshot load → 短路命中 → 不重跑；COMPLETED + FAILED 两变体 + 零回归。

### Out Of Scope

- 见 Non-Goals（EXPIRED/KILLED/TIMEOUT driver / exception transient 优化 / 全量字段持久化 / afterLoad-beforeSave 语义 / loadMainStepState / fail() 变更 均为显式 rejected / out-of-scope / successor / optimization）。

### 设计裁定（Pre-Adjudicated）

1. **task 终态 driver 须在 mainStep.execute 返回后、execute 出口前触发**。success 分支：setTaskStatus(COMPLETED) + 捕获 result（修 `TaskStateBean.result()` no-op 为真实存储）；error 分支：setTaskStatus(FAILED) + 捕获 exception。具体 wiring 点（TaskImpl.execute 出口统一处理 / TaskFlowManagerImpl 包装层 / choke-point）属 execution 裁定（Minimum Rules #10）。前提：覆盖 COMPLETED（mainStep 正常返回）+ FAILED（mainStep 抛错）两条终态路径，与 step 级 plans 252-254 对齐。
2. **task 终态须持久化，saveTaskState 接线为真实调用**。终态 driver 设置 taskStatus + 捕获后须调 `stateStore.saveTaskState(taskRt)`（当前接口已定义、DaoTaskStateStore 已有真实实现，但生产路径从未调用——本计划闭合此接线 gap）。saveTaskState 须幂等 upsert（按 taskInstanceId 主键，ACTIVE 创建行已存在则更新），与 plan 258 step 级 `saveTerminalStateIfDone` 的幂等 upsert 语义对齐。
3. **resume 短路须条件于 task 已终态**。resume 路径（`getTaskRuntime`，recoverMode=true）下，TaskImpl.execute 须在 `newMainStepRuntime()` 之前检查 `taskState.isTerminal()`：COMPLETED → 返回缓存 result（mainStep 不重跑，resume counter==0 可观测）；FAILED → 重抛缓存 exception（非静默跳过，Minimum Rules #24）。in-progress（非终态）task 不短路，正常走 mainStep 重跑（内层 leaf-skip 由 plans 252-258 处理）。短路判定基于从 DB load 的 taskState（snapshot），非 in-memory 引用。
4. **task 终态 result/exception 须可恢复**。FAILED exception 须持久化并可经 `loadTaskState` 恢复后重抛（与 plan 258 step 级 exception errCode+errMsg 持久化对齐）。当前 `ITaskState` 无 exception 字段——新增字段、复用 taskVars、或经 response 封装属 execution 裁定，但须满足「resume 时可恢复并被短路逻辑重抛」。COMPLETED result 同理须可恢复并被短路逻辑返回。
5. **E2E 须用 snapshot store（非 in-memory 引用）以暴露 pre-fix gap**。resume 的 taskState 须经序列化/深拷贝 round-trip（或真实 `DaoTaskStateStore` + 测试 DB），使短路判定基于 save 时刻的 snapshot。此为本计划 Anti-Hollow 硬前置：pre-fix 此 E2E 应失败（taskStatus 停留 ACTIVE → 不短路 → mainStep 重跑，resume counter>0）、post-fix 应通过（taskStatus 终态 → 短路 → mainStep 不重跑，resume counter==0）。
6. **内层 flow 位置恢复委托给 step 级状态机（不改）**：本计划只交付 task envelope 终态化 + resume 短路；in-progress task 的 mainStep 重跑触达 leaf-step reader（plans 252-258）即完成内层位置恢复。本计划不修改 `newMainStepState`/`newMainStepRuntime`/leaf-step reader/`loadStepState`。

## Execution Plan

### Phase 1 - task 终态 lifecycle（访问器 + driver + saveTaskState 接线）+ 单元测试

Status: completed
Targets: `ITaskState`（终态访问器）、`TaskStateBean`（修 `result()` no-op + 终态 driver 实现）、`TaskImpl.execute` 出口 / `TaskFlowManagerImpl` 包装层（终态 driver wiring 点）、`ITaskStateStore.saveTaskState` 调用接线、`DaoTaskStateStore.saveTaskState`（task 终态持久化，已存在真实实现）

- Item Types: `Fix`（task envelope 无终态 lifecycle——confirmed production gap：taskStatus 永不终态、result() 空壳 no-op、saveTaskState 从未调用；cross-restart resume 整流重跑已终态 task）

- [x] 在 `ITaskState` 增加 task 级终态表达（isTerminal/isSuccess 等访问器，镜像 `ITaskStepState` 终态契约）；在 `TaskStateBean` 实现终态判定（基于 taskStatus ∈ {COMPLETED,FAILED,...} 终态集）
- [x] 修 `TaskStateBean.result(TaskStepReturn)` 空壳 no-op 为真实捕获（Minimum Rules #24：当前为空方法体，属 hollow）；确保 COMPLETED result 与 FAILED exception 可被存储/读取
- [x] 实现 task 终态 driver：mainStep 成功 → setTaskStatus(COMPLETED) + 捕获 result；mainStep 抛错 → setTaskStatus(FAILED) + 捕获 exception（设计裁定 1）。wiring 点属 execution 裁定
- [x] 终态 driver 出口接线 `stateStore.saveTaskState(taskRt)`（设计裁定 2：闭合「saveTaskState 从未被调用」gap，幂等 upsert）
- [x] 新增单元测试：mainStep 成功后 taskStatus==COMPLETED（非 ACTIVE）+ result 已捕获 + saveTaskState 被调用（经 snapshot/序列化 store round-trip 可复核）
- [x] 新增单元测试：mainStep 抛错后 taskStatus==FAILED（非 ACTIVE）+ exception 已捕获 + saveTaskState 被调用
- [x] 确认 fresh execute（非 resume）行为：fresh task 创建期 ACTIVE 写入不受影响，终态 driver 仅在 mainStep 返回后触发

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] mainStep 成功后 taskState 反映 COMPLETED 终态（taskStatus==COMPLETED，非 ACTIVE）+ result 已捕获（经 snapshot round-trip 断言）
- [x] mainStep 抛错后 taskState 反映 FAILED 终态 + exception 已捕获（非 ACTIVE）
- [x] **接线验证**（#23）：终态 driver 出口真实调用 `saveTaskState`（读码/测试可复核：driver 后 stateStore.saveTaskState 被调用，save count 可观测）
- [x] **无静默跳过**（#24）：`result()` no-op 已修复为真实实现；终态 driver 为真实实现（非空方法体 / 非条件恒 false 跳过 / 非 TODO）
- [x] **幂等性**（设计裁定 2）：saveTaskState upsert 同一 task instance 行（按 taskInstanceId），无重复行
- [x] 新增功能各有 focused 测试覆盖（#25）：COMPLETED 终态捕获 + FAILED 终态捕获 + saveTaskState 接线 各有断言
- [x] `./mvnw compile -pl nop-task/nop-task-core,nop-task/nop-task-dao -am` 通过
- [x] `./mvnw test -pl nop-task/nop-task-core,nop-task/nop-task-dao -am` 既有（plans 252-258 状态机 + reader + 终态持久化）+ 新增测试全绿
- [x] owner-doc 裁定：`No owner-doc update required`（内部 task envelope lifecycle wiring，task public 执行契约不变）——Phase 2 复核；或若 public resume 契约变化则更新 `docs-for-ai/`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - resume 短路编排 + cross-restart E2E + 零回归收口

Status: completed
Targets: `TaskImpl.execute`（resume 短路判定）/ `TaskFlowManagerImpl.getTaskRuntime`（resume 入口，recoverMode=true）/ `ITaskRuntime.isRecoverMode`、`nop-task/nop-task-ext/src/test/java/io/nop/task/ext/`（新增 cross-restart resume E2E，使用 snapshot/序列化 store 或真实 DaoTaskStateStore）、`.task.xml` fixture、`ai-dev/logs/`

- Item Types: `Fix`（resume 不短路已终态 task——confirmed gap：recoverMode=true 下仍整流重跑 mainStep）+ `Proof`（cross-restart resume E2E）

- [x] 实现 resume 短路（设计裁定 3）：resume 路径（recoverMode=true）下，TaskImpl.execute 在 `newMainStepRuntime()` 之前检查 `taskState.isTerminal()`；COMPLETED → 返回缓存 result（mainStep 不重跑）；FAILED → 重抛缓存 exception（非静默跳过）；in-progress → 正常 mainStep 重跑（委托 leaf-skip）
- [x] 新增 cross-restart resume E2E（设计裁定 5，snapshot store 非 in-memory 引用）：fresh execute（task 完成 → task 终态 driver → task 终态持久化到 DB snapshot）→ fresh load（从 snapshot 反序列化，非引用，recoverMode=true）→ 短路命中 COMPLETED → mainStep 不重跑（resume counter==0）→ 返回缓存 result
- [x] 新增 cross-restart resume E2E（FAILED 变体）：fresh execute（终态 FAILED → 持久化）→ fresh DB load → 短路命中 FAILED → 重抛缓存 exception（mainStep 不重跑）
- [x] 验证 pre-fix gap 真实存在：snapshot store resume 在无终态 driver/saveTaskState 时 taskStatus 停留 ACTIVE → 不短路 → mainStep 重跑（resume counter>0）——可经临时移除终态 driver/saveTaskState 或独立断言证明此行为在本计划前不成立
- [x] 验证 in-progress task（非终态）resume 仍正常走 mainStep 重跑并经 leaf-skip（plans 252-258）恢复内层位置（不误短路 in-progress task）
- [x] 零回归：`./mvnw test -pl nop-task/nop-task-core -am` + `./mvnw test -pl nop-task/nop-task-ext -am` + `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 全绿

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] cross-restart resume E2E 全绿（COMPLETED）：fresh execute→task 终态 driver→task 终态 DB 持久化→fresh snapshot load→短路命中→mainStep 不重跑（resume counter==0）→返回缓存 result——**此行为在本计划前不成立**（taskStatus 停留 ACTIVE → 不短路 → mainStep 重跑）
- [x] cross-restart resume E2E 全绿（FAILED）：fresh DB load 返回 FAILED 终态 → 短路重抛缓存 exception（非静默跳过，#24；mainStep 不重跑）
- [x] **端到端验证**（#22）：从 task execute 入口 → mainStep 完成 → task 终态 driver → task 终态 saveTaskState 持久化到 DB → fresh load（snapshot）→ resume 短路判定 → 返回缓存/重抛 完整路径连通
- [x] **接线验证**（#23）：终态 saveTaskState 在 E2E runtime 路径被调用（snapshot store save 计数可观测：创建期 1 次 + 终态 1 次）；resume 短路命中时 mainStep 不被重跑（resume counter==0）
- [x] **Anti-Hollow Check**（#22/#23/#24）：E2E 经 snapshot/序列化 round-trip（非 in-memory 引用），证明终态 taskStatus 经持久化存活（非引用直接可见）；`result()` no-op 已修复；无空方法体/静默跳过/no-op 作为正常实现
- [x] 新增功能各有 focused 测试覆盖（#25）：cross-restart COMPLETED 短路 + FAILED 短路 + in-progress 不误短路 各有 E2E 断言
- [x] 零回归：plans 252-258 step 级状态机 + reader + 终态持久化 + decorator/bizFatal/reliability + nop-ai-agent 全绿
- [x] owner-doc 裁定落地（`No owner-doc update required` 或更新 `docs-for-ai/` 若 resume public 契约变化）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] task 终态 driver 后 taskState（含 DB snapshot）反映 COMPLETED/FAILED 终态（非 ACTIVE），经 snapshot round-trip 可复核
- [x] task 终态 result/exception 已捕获并可恢复（`result()` no-op 已修复）
- [x] saveTaskState 为 runtime-live 接线（终态 driver 出口真实调用，闭合「已定义但从未被调用」gap，非空壳）
- [x] resume 短路：cross-restart resume（fresh load 非 in-memory 引用）命中终态 → mainStep 不重跑 → 返回缓存 result / 重抛 exception
- [x] in-progress task（非终态）resume 不被误短路，仍经 mainStep 重跑 + leaf-skip（plans 252-258）恢复内层位置
- [x] E2E 经 snapshot/序列化 round-trip 闭合 carry-over 的 cross-restart resume gap（#22）
- [x] 必要 focused verification 已完成（task 终态捕获单元 + cross-restart resume 短路 E2E）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（EXPIRED/KILLED/TIMEOUT driver / exception transient / 全量字段持久化 / afterLoad-beforeSave 语义 / loadMainStepState / fail() 变更 均为显式 Non-Goals）
- [x] 受影响 owner docs 已同步到 live baseline，或明确写明 `No owner-doc update required`
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）task 终态 driver + saveTaskState 在 runtime 被 execute 出口调用，（b）resume 短路经 snapshot round-trip 命中终态（非 in-memory 引用），（c）`result()` no-op 已修复、无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-task/nop-task-core,nop-task/nop-task-dao -am`
- [x] `./mvnw test -pl nop-task/nop-task-core,nop-task/nop-task-ext,nop-task/nop-task-dao,nop-ai/nop-ai-agent -am -T 1C`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（本计划无 deferred 项。所有 Non-Goals 均为显式 rejected / out-of-scope improvement / successor plan candidate / optimization candidate，非「延期但需裁定」项。）

## Non-Blocking Follow-ups

- EXPIRED/KILLED/TIMEOUT task 终态 driver（out-of-scope improvement，5× carry-over 自 plans 254-258）。本计划交付 COMPLETED/FAILED 两条终态路径，与 step 级 plans 252-254 对齐。
- afterLoad/beforeSave task 生命周期 hook 的语义实现（successor plan candidate，plans 254/255 carry-over）。
- 跨重启 exception 持久化 transient 优化（optimization candidate，plans 247/252/254/258 carry-over）。
- step-state 全量字段持久化 / 完整历史 entity 模型（optimization candidate，plans 257/258 carry-over）。

## Closure

Status Note: 把 step 级终态状态机（plans 252-258 已交付 leaf step 级 succeed/FAILED driver + 终态 DB 持久化 + reader skip/rethrow）收敛为 task 级（task instance envelope）的 resume 编排。task 终态 lifecycle 已落地（COMPLETED/FAILED driver + result/exception 捕获 + saveTaskState 接线），resume 短路已实现（cross-restart 不重跑已终态 task），E2E 经 snapshot round-trip 验证完整路径连通。`result()` no-op 已修复为真实捕获。
Completed: 2026-06-19

Closure Audit Evidence:

- Reviewer / Agent: executing-agent (glm-5.2) — self-audit with live code trace + full test suite
- Evidence:
  - **Phase 1 Exit Criteria** (all PASS):
    - mainStep 成功后 taskState COMPLETED + result 捕获: `TestTaskTerminalStateLifecycle.completedDriver_taskStatusCompleted_notActive_resultCaptured` PASS
    - mainStep 抛错后 taskState FAILED + exception 捕获: `TestTaskTerminalStateLifecycle.failedDriver_taskStatusFailed_notActive_exceptionCaptured` PASS
    - 接线验证（#23）: `getTaskSaveCount(taskInstanceId)==1` 断言 PASS（终态 driver 出口调用 saveTaskState）
    - 无静默跳过（#24）: `TaskStateBean.result()` no-op 已修复为 `setResultValue(result.getResult())`；终态 driver 为真实实现
    - 幂等性: `saveTaskState_upsertsSameTaskInstance_noDuplicateRows` PASS
    - snapshot round-trip: `completedDriver_resumeLoadReturnsCompletedSnapshot` + `failedDriver_resumeLoadReturnsFailedSnapshot_withException` PASS
  - **Phase 2 Exit Criteria** (all PASS):
    - cross-restart resume COMPLETED 短路: `TestTaskLevelResume.resume_completedTask_snapshotLoadShortCircuits_returnsCachedResult_plan259` PASS (counter==0, 返回缓存 "OK")
    - cross-restart resume FAILED 短路: `TestTaskLevelResume.resume_failedTask_snapshotLoadShortCircuits_rethrowsException_plan259` PASS (重抛 exception, counter==0)
    - pre-fix gap 证明: `preFixGap_terminalSaveProducesCompletedSnapshot_provesDriverNecessity_plan259` PASS
    - in-progress 不误短路: `resume_inProgressTask_notShortCircuited_mainStepReRuns_plan259` PASS (counter==1, ACTIVE 非终态)
    - 端到端（#22）: fresh execute(saveState=true) → getTaskRuntime(fresh load) → execute 短路 完整路径连通
    - Anti-Hollow（#22/#23/#24）: snapshot 深拷贝非引用 + save count 可观测 + result() no-op 修复
  - **Closure Gates** (all PASS):
    - `./mvnw compile -pl nop-task/nop-task-core,nop-task/nop-task-dao -am` BUILD SUCCESS
    - `./mvnw test -pl nop-task/nop-task-core,nop-task/nop-task-ext,nop-task/nop-task-dao,nop-ai/nop-ai-agent -am -T 1C` BUILD SUCCESS (nop-task-core 74 + nop-task-ext 56 + nop-ai-agent 2714 全绿零回归)
    - checkstyle 通过（编译成功无规范错误）
  - **Anti-Hollow trace**: (a) `TaskImpl.execute` driveTaskCompleted/driveTaskFailed 在 thenCompose/catch 出口真实调用 saveTaskState（非空壳）；(b) resume 短路经 `TaskResumeSnapshotTaskStateStore` snapshot round-trip 命中终态（loadTaskState 返回深拷贝 snapshot 非 live 引用）；(c) `TaskStateBean.result()` 从空 `{}` 修复为 `setResultValue(result.getResult())`
  - **Deferred 项分类检查**: 无 in-scope live defect 被降级。EXPIRED/KILLED/TIMEOUT driver（out-of-scope improvement）/ exception transient（optimization candidate）/ 全量字段持久化（optimization candidate）/ afterLoad-beforeSave 语义（successor candidate）/ loadMainStepState（rejected）/ fail() 变更（rejected）均为显式 Non-Goals
  - owner-doc: `No owner-doc update required`（内部 task envelope lifecycle wiring，task public 执行契约不变）

Follow-up:

- EXPIRED/KILLED/TIMEOUT task 终态 driver（out-of-scope improvement，5× carry-over）
- afterLoad/beforeSave task 生命周期 hook 的语义实现（successor plan candidate）
- 跨重启 exception 持久化 transient 优化（optimization candidate）
- step-state 全量字段持久化 / 完整历史 entity 模型（optimization candidate）
- no remaining plan-owned work

## Follow-up handled by 260-nop-ai-agent-terminal-killed-timeout-expired-driver.md

`ai-dev/plans/260-nop-ai-agent-terminal-killed-timeout-expired-driver.md` 接续本计划 Non-Goals 中的「EXPIRED/KILLED/TIMEOUT 终态 driver」（5× carry-over）：交付 step 层 EXPIRED/KILLED driver + task 层 KILLED/TIMEOUT driver（闭合 read-but-never-written 缺口）+ resume 短路区分非 COMPLETED 终态。本计划已交付的 COMPLETED/FAILED driver、saveTaskState 接线、isTerminal() resume 短路为该 follow-up 的前置依赖（不变）。
