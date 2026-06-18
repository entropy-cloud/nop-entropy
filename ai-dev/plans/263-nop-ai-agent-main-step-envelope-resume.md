# 263 nop-task mainStep envelope intermediate-state restore（resume 路径加载持久化 mainStep 状态，使中断 task 从 mainStep 中间 flow 位置续跑，闭合 plans 258/259/261/262 carry-over successor）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: loadMainStepState (carry-over, L4-8, P1)

> Last Reviewed: 2026-06-19
> Source: carry-over from `ai-dev/plans/262-nop-ai-agent-task-state-lifecycle-hooks.md`（Non-Goals:39 + Non-Blocking Follow-ups:143 显式记录为 `successor plan candidate` 的「`loadMainStepState` mainStep envelope intermediate-state restore」，3× carry-over 自 plans 258/259/261，plan 262 再次登记；roadmap 项 `loadMainStepState`，P1）。plan 262 已 `completed` 并已追加 `## Follow-up handled by 263-...` 双向链接；依 Minimum Rules #20 不回写其正文。
> Related: `257`（continuation-skip reader，leaf-step resume）/`258`（leaf step 终态 DB 持久化）/`259`（task COMPLETED/FAILED driver + resume 短路）/`260`（KILLED/TIMEOUT driver）/`261`（exception transient persist）/`262`（task 级 afterLoad/beforeSave hook，本计划 source-plan）

## Purpose

闭合 nop-task cross-restart resume 的**最后一块功能缺口**：leaf-step 终态（COMPLETED/FAILED/KILLED/TIMEOUT/EXPIRED）经 plan 257 continuation-skip reader 在 resume 时恢复并跳过，task-envelope 终态经 plan 259/260 resume 短路（`TaskImpl.execute` 命中 `isTerminal()` 直接返回缓存 result / 重抛 exception，mainStep 不重跑）；但 **task 中断于 in-flight 状态（mainStep 处于中间 cross-step flow 位置、尚未终态）时，resume 无法从 mainStep 的中间位置续跑——它从 `bodyStepIndex=0` 重跑 mainStep**。

根因：`ITaskStateStore` 接口只有 `newMainStepState(ITaskState)`（恒创建 fresh ACTIVE 状态，stepPath=`@main`），**没有 `loadMainStepState`**（grep 全仓库零命中）；`TaskRuntimeImpl.newMainStepRuntime()` 恒调 `newMainStepState`，从不加载持久化的 mainStep 状态。而 composite mainStep（Sequential/Selector/Loop/LoopN/Graph，经 `TaskStepBuilder.buildMainStep` 构造）在执行中**确实**经 `stepRt.saveState()` → `DaoTaskStateStore.saveStepState` 把自身 `bodyStepIndex`（flow 位置）持久化到 DB（stepPath=`@main`，`copyStepStateToEntity:314` 写、`toStepStateBean:275-276` 读，round-trip 成立）。即：**mainStep 的中间位置被持久化了，但 resume 路径把它丢弃并从头重跑**。

本计划交付 **resume 路径加载持久化 mainStep 状态的契约 + 实现 + 接线**，使 composite mainStep 在 cross-restart resume 时从其已持久化的 `bodyStepIndex` 续跑（与 leaf-step reader、task-envelope 短路共同构成完整的 task-level cross-restart resume），并经真实 `DaoTaskStateStore` cross-restart round-trip E2E 证明续跑语义生效（非空壳）。

## Current Baseline

基于本会话对 live repo 的核对（引用位置为审计可复核 live code path；确切行号以独立审计复核为准）：

- **mainStep 加载契约完全缺失（本计划核心缺口）**：`ITaskStateStore`（`nop-task-core` `ITaskStateStore.java`，26 行）仅有 `newMainStepState(ITaskState)`（`:13`，恒 fresh）、`loadStepState(parentState, stepName, stepType, taskRt)`（`:17`，加载**子** step）；**无 `loadMainStepState`**——grep `loadMainStepState` 全仓库（非 target）零命中。`newMainStepState` 在 `DaoTaskStateStore`（`:157-166`）与 `DefaultTaskStateStore`（`:24-31`）均构造全新 ACTIVE 状态（stepPath=`TaskConstants.MAIN_STEP_NAME`=`@main`，`TaskConstants:29`）。
- **resume 路径恒 fresh（本计划接线点）**：`TaskRuntimeImpl.newMainStepRuntime()`（`:198-206`）恒执行 `stateStore.newMainStepState(this.getTaskState())`（`:199`），从不尝试加载持久化 mainStep 状态。结果：resume 时 mainStep 状态的 `bodyStepIndex` 恒为默认 0。
- **resume 短路仅覆盖终态（前置依赖，不变）**：`TaskImpl.execute`（`:90-163`）短路条件 `if (taskRt.isRecoverMode() && taskState.isTerminal())`（`:98`）只对**终态** task 生效（COMPLETED 返回缓存 result / FAILED/KILLED/TIMEOUT 重抛，mainStep 不重跑，plans 259/260）。**in-progress（ACTIVE 非终态）task 不短路**（`:97` 注释明示），走到 `:119` `taskRt.newMainStepRuntime()` → `:141` `mainStep.execute(stepRt)` ——即 in-flight 中断的 task resume 时 mainStep 从头重跑。本计划只针对这条**非短路**路径，不动终态短路（Non-Goals）。
- **composite mainStep 确实持久化 flow 位置（本计划加载的数据源，已成立）**：`TaskStepBuilder.buildMainStep`（`:84-86`）→ `buildDecoratedStep` 产出 composite step（Sequential/Selector/Loop/LoopN/Graph 等）。composite step 执行中调 `stepRt.setBodyStepIndex(...)` + `stepRt.saveState()` 持久化自身 flow 位置——例：`SequentialTaskStep.execute`（`:75-76` sync 路径、`:87-88` async 路径）、`SelectorTaskStep`（`:54/:66/:69/:74/:93/:98/:101/:107`）、`LoopTaskStep`（`:156/:170`）、`LoopNTaskStep`（`:193/:207`）。`stepRt.saveState()` → `TaskStepRuntimeImpl:84` `stateStore.saveStepState(this)`。对 mainStep，其 state 的 `stepPath=@main`，故 `DaoTaskStateStore.saveStepState`（`:201-216`）→ `findStepEntity(taskInstanceId, "@main")`（`:203/:220-227`）→ `copyStepStateToEntity`（`:301-356`）把 `bodyStepIndex`（`:314`）、`stepStatus`（`:309-310`）、result（`:319-329`）持久化。`toStepStateBean`（`:266-299`）round-trip 还原 `bodyStepIndex`（`:275-276`）、`stepStatus`（`:274`）、result（`:280-288`）。**结论：mainStep 中间位置已可持久化且可反序列化，唯独 resume 不加载它**。
- **leaf-step resume 已成立（前置依赖，不动）**：plan 257 continuation-skip reader——子 step 经 `loadStepState`（`:187-198`）加载持久化终态，`isDone()` 命中则跳过 body。本计划交付的 mainStep 加载与之**互补**：leaf-level 由 reader 处理，mainStep-envelope level 由本计划的 load 处理。
- **验证基础设施已就绪（本计划复用）**：`nop-task-ext/src/test/` 已有 `@NopTestConfig(localDb=true)` 的真实 `DaoTaskStateStore` cross-restart round-trip 范式（`TestTaskLevelResume`、`TestTaskKilledTimeoutResumeE2E`、plan 262 `TestTaskStateLifecycleHookCrossRestart`），save→实体→DB→fresh store `loadTaskState`/`loadStepState` 经 JSON 序列化反序列化（实体↔DB 列，非 in-memory 引用），可直接复用以证明 mainStep `bodyStepIndex` 经持久化边界存活并驱动续跑。composite step 测试范式见 `nop-task-core/src/test/.../step/`（如 `TestTerminalStatePersistence` 已用 `MAIN_STEP_NAME` 构造 main state，`:77`）。
- **3× carry-over 裁定（plans 258/259/261，plan 262 再次登记）**：均将本项分类为 `successor plan candidate`（功能缺口，非 confirmed live defect、非 optimization），本计划据此接续。

## Goals

- **mainStep 加载契约**：`ITaskStateStore` 提供「按持久化状态恢复 mainStep」的契约（对称已有的 `newMainStepState` 创建路径与 `loadStepState` 子 step 加载路径），向后兼容（既有实现 / in-memory store / snapshot store / 所有 `ITaskStateStore` 消费方不受影响）。
- **DaoTaskStateStore 实现**：经 `findStepEntity(taskInstanceId, "@main")` 加载持久化 mainStep 状态，复用既有 `toStepStateBean`（已 round-trip `bodyStepIndex`/`stepStatus`/result），并调用 step 级 `afterLoad(taskRt)` hook（对称 `loadStepState:196`，plan 262 交付的扩展点）；无持久化行时返回 null（fresh execute 路径，`newTaskState` 不写 mainStep 行）。
- **resume 接线**：`TaskRuntimeImpl.newMainStepRuntime()` 在 resume（`recoverMode=true`）路径下优先尝试加载持久化 mainStep 状态；命中则用之（mainStep 从已持久化 `bodyStepIndex` 续跑），未命中（fresh execute / in-memory store / 无持久化行）回退既有 `newMainStepState`（零回归）。
- **非空壳可观测语义**：经 focused 单测 + cross-restart E2E（真实 `DaoTaskStateStore` localDb round-trip）证明——composite mainStep 在 cross-restart resume 后从已持久化的中间 `bodyStepIndex` 续跑，**已完成的前置子步骤不被重复执行**（Anti-Hollow #22/#23/#24）。
- **零回归**：plans 252-262 状态机 + reader + 终态 driver + resume 区分/短路 + exception 持久化 + task 级 lifecycle hook + nop-ai-agent 全绿；终态 resume 短路行为不变。

## Non-Goals

- **终态 task resume 短路改造**：`TaskImpl.execute:98` 的 `isTerminal()` 短路（plans 259/260）保持原样。本计划只处理**非终态** in-flight task 的 mainStep 续跑，终态路径短路在 `newMainStepRuntime()` 之前，不受影响。Classification: rejected（既有设计，独立闭合）。
- **leaf-step continuation-skip reader 改造**：plan 257 reader（子 step 经 `loadStepState` 恢复并跳过）保持原样。本计划与之互补，不重写其逻辑。Classification: out-of-scope improvement。
- **step-state 全量字段持久化 / 完整历史 entity 模型**：optimization candidate（plans 257/258/259/260/261/262 6× carry-over）。本计划复用既有 round-trip 字段集（`bodyStepIndex`/`stepStatus`/result），不扩列。
- **stacktrace（errorStack）完整持久化与截断策略**：optimization candidate（plan 261）。
- **mainStep 状态持久化写入路径的改造**：composite step 经 `saveState()` 的既有写入（`SequentialTaskStep:76` 等）保持原样，本计划只新增**读取**路径。是否给更多 composite step（如 Graph）补充 `saveState` 频率为独立优化。Classification: out-of-scope improvement。
- **`fail()` 行为变更**：rejected（plans 247/252-254 design decision）。
- **跨 step 变量 / evalScope 中间状态持久化**：本计划只恢复 mainStep 的**控制流位置**（`bodyStepIndex`）与终态结果，不恢复执行期 evalScope/局部变量中间态（既有 reader 同样不恢复）。Classification: out-of-scope improvement（独立 feature，需独立序列化设计）。

## Scope

### In Scope

- **mainStep 加载契约 + DaoTaskStateStore 实现**（Phase 1）：`ITaskStateStore` 新增加载 mainStep 持久化状态的契约（向后兼容）；`DaoTaskStateStore` 经 `findStepEntity(taskInstanceId,"@main")` + `toStepStateBean` + `afterLoad` 实现，无行返回 null。
- **resume 接线**（Phase 1）：`TaskRuntimeImpl.newMainStepRuntime()` 在 resume 路径优先加载持久化 mainStep 状态，命中续跑、未命中回退 fresh。
- **非空壳验证**（Phase 1 单测 + Phase 2 cross-restart E2E）：真实 `DaoTaskStateStore`（localDb）经 save（mainStep 写 `bodyStepIndex=N`）→ fresh load（恢复 `bodyStepIndex=N`）→ resume 续跑，证明 mainStep 从 N 续跑且前置子步骤不重复执行。
- **零回归**（Phase 2）：plans 252-262 + nop-ai-agent 全绿。

### Out Of Scope

- 见 Non-Goals（终态短路改造 / leaf-step reader 改造 / 全量字段持久化 / stacktrace / mainStep 写入路径改造 / fail() 变更 / 跨 step 变量持久化 均为显式 rejected / out-of-scope / optimization）。

### 设计裁定（Pre-Adjudicated）

1. **加载契约形态（向后兼容为硬约束）**：`ITaskStateStore` 是 `nop-task-core` public 接口，新增方法**不得破坏既有实现**（`DaoTaskStateStore`、`DefaultTaskStateStore`、`TaskLevelSnapshotTaskStateStore`/`SnapshotTaskStateStore`、任何 test double / mock）。具体方法形态（新增 `default` 方法返回 null = 无持久化 vs 复用既有 `loadStepState(null, MAIN_STEP_NAME, STEP_TYPE_TASK, taskRt)`）属 execution 裁定（Minimum Rules #10），**前提是**：(a) 向后兼容（既有 impl 零变更或仅 `default` 继承）；(b) 能正确解析 mainStep 的持久化路径——`newMainStepState` 把 stepPath 设为 `TaskConstants.MAIN_STEP_NAME`=`@main`（`DaoTaskStateStore:161` / `DefaultTaskStateStore:26`），加载须按同一 path 命中同一持久化行；(c) 加载后调用 step 级 `afterLoad(taskRt)` hook（对称 `loadStepState:196`，plan 262 扩展点），保证 task 级与 step 级 load lifecycle 对称。若选择复用 `loadStepState`，execution 须验证 `buildStepPath(null, "@main")` 产出的 path 与写入侧 `@main` 完全一致（否则 entity 查询 miss）；若不一致或语义不清，应新增 dedicated 方法。
2. **接线目标与时机**：加载发生在 `TaskRuntimeImpl.newMainStepRuntime()`（`:198-206`），**仅在 `recoverMode=true` 时尝试加载**。fresh execute（`recoverMode=false`）恒走 `newMainStepState`（fresh execute 时 `newTaskState` 只写 task instance 行、不写 mainStep 行，故即便尝试加载也必然 miss，但 recoverMode 门控使语义清晰且与 `TaskImpl.execute:98` 终态短路条件 `isRecoverMode()` 对称）。命中持久化 mainStep 状态 → 用之（mainStep 从 `bodyStepIndex` 续跑）；未命中（无行 / in-memory store / 不支持持久化）→ 回退 `newMainStepState`（零回归，fresh ACTIVE）。**不移动既有终态短路**（Non-Goals，终态短路在 `newMainStepRuntime()` 之前于 `TaskImpl.execute:98` 完成）。
3. **非空壳交付（避免 lesson #6/#8/#9）**：契约表面 ≠ 语义落地。本计划交付的「语义」是 **resume 后 composite mainStep 从已持久化的中间 `bodyStepIndex` 续跑，前置已完成子步骤不重复执行**，经真实 `DaoTaskStateStore` round-trip（localDb）证明。可观测证据：(a) cross-restart 前后 mainStep `bodyStepIndex` 相等（经 DB 序列化边界存活）；(b) resume 后 mainStep 从该 index 继续（而非从 0）；(c) 已完成的前置子步骤 body 执行计数为 0（Anti-Hollow，非「从头重跑靠 reader 逐一 skip」的伪装——须断言 mainStep 控制流位置本身被恢复，证明方式见 Exit Criteria）。具体断言机制（计数器 / spy step / 可观测 composite）属 execution 裁定，前提是 runtime 证据非仅代码结构存在。若加载返回 null（无持久化行）是 fresh 路径的合法回退，不是静默跳过；但**有持久化行却未被加载消费**属缺陷（#24）。
4. **向后兼容（零回归）**：契约新增 / 接线不得影响 fresh execute、终态短路、in-memory store、snapshot store、nop-ai-agent 既有行为，除非显式 override。`DefaultTaskStateStore`（非持久化）加载须返回 null（对称其 `loadStepState:62-64` 返回 null），使 in-memory 路径恒回退 `newMainStepState`。

## Execution Plan

### Phase 1 - mainStep 加载契约 + DaoTaskStateStore 实现 + newMainStepRuntime resume 接线 + 单元测试

Status: completed
Targets: `ITaskStateStore.java`（`nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStateStore.java`）、`DaoTaskStateStore.java`（`nop-task/nop-task-dao/src/main/java/io/nop/task/dao/store/DaoTaskStateStore.java`，`newMainStepState:157-166` / `loadStepState:187-198` / `findStepEntity:220-227` / `toStepStateBean:266-299`）、`DefaultTaskStateStore.java`（`:24-31` / `loadStepState:62-64`）、`TaskRuntimeImpl.java`（`newMainStepRuntime:198-206`）、`nop-task-ext/src/test/`（`@NopTestConfig(localDb=true)`，镜像 `TestTaskLevelResume`/plan 262 `TestTaskStateLifecycleHookCrossRestart` 范式）

- Item Types: `Follow-up`（carry-over successor enhancement——mainStep 持久化状态加载缺失，经 3× carry-over 裁定为 successor plan candidate，非 confirmed live defect）

- [x] `ITaskStateStore` 新增「加载持久化 mainStep 状态」契约（设计裁定 1，向后兼容；形态 execution 裁定，前提是按 path `@main` 命中、调 `afterLoad`、既有 impl 零破坏）
- [x] `DaoTaskStateStore` 实现该契约：经 `findStepEntity(taskInstanceId, "@main")` 加载，复用 `toStepStateBean`（已 round-trip `bodyStepIndex`/`stepStatus`/result），调 step 级 `afterLoad(taskRt)`（对称 `loadStepState:196`）；无行返回 null
- [x] `DefaultTaskStateStore`（及任何既有 impl）保持非持久化语义：加载返回 null（对称 `loadStepState:62-64`），使 in-memory 路径恒回退 `newMainStepState`（零回归）
- [x] `TaskRuntimeImpl.newMainStepRuntime()`（`:198-206`）在 `recoverMode=true` 时优先尝试加载持久化 mainStep 状态；命中用之、未命中回退 `newMainStepState`（设计裁定 2，fresh execute 不受影响）
- [x] 新增 focused 单测（`nop-task-ext`，`@NopTestConfig(localDb=true)`，真实 `DaoTaskStateStore`）：composite mainStep（至少覆盖 Sequential 多子步场景）执行至中间 `bodyStepIndex=N`（mainStep 已 saveState 写入 N）→ 模拟中断 → fresh load → resume → 断言 mainStep `bodyStepIndex` 经 DB round-trip 恢复为 N 且 mainStep 从 N 续跑（前置子步骤执行计数为 0，Anti-Hollow）
- [x] 验证默认路径零回归：fresh execute（`recoverMode=false`）恒走 `newMainStepState`；终态 task resume 短路（`TaskImpl.execute:98`）行为不变；in-memory store resume 回退 fresh

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `ITaskStateStore` 声明加载 mainStep 持久化状态的契约（读接口可见，向后兼容——既有 impl 零破坏或仅 `default` 继承）
- [x] `DaoTaskStateStore` 实现按 `@main` 加载 + `toStepStateBean` + `afterLoad`；无行返回 null（读 store 可见）
- [x] `TaskRuntimeImpl.newMainStepRuntime()` 在 resume 路径优先加载、未命中回退 fresh（读 runtime 可见，recoverMode 门控对称 `TaskImpl.execute:98`）
- [x] **接线验证**（#23）：单测断言 resume 时持久化 mainStep 状态在 runtime 被加载消费（mainStep `bodyStepIndex` 经 DB round-trip 恢复），不只验证类型/方法存在
- [x] **无静默跳过**（#24）：加载为真实读取消费（非空方法体 / 非 TODO / 非 `continue`）；加载返回 null 仅在「无持久化行 / 非持久化 store」合法回退；**有持久化行却未被加载消费** 不可发生
- [x] **新功能必有测试**（#25）：mainStep 从中间 `bodyStepIndex` 续跑 + 前置子步骤不重复执行 各有 focused 断言；fresh execute 零回归路径有断言
- [x] `./mvnw compile -pl nop-task/nop-task-core,nop-task/nop-task-dao,nop-task/nop-task-ext -am` 通过
- [x] `./mvnw test -pl nop-task/nop-task-core,nop-task/nop-task-dao,nop-task/nop-task-ext -am` 既有 + 新增测试全绿
- [x] owner-doc 裁定：`No owner-doc update required`（理由：内部 resume 持久化扩展，task public 执行契约不变，新增 `default` 方法向后兼容）
- [x] `ai-dev/logs/` 对应日期条目已更新（Phase 2 一起更新）

### Phase 2 - cross-restart `DaoTaskStateStore` DB round-trip E2E + 零回归 + 文档收口

Status: completed
Targets: `nop-task/nop-task-ext/src/test/`（`@NopTestConfig(localDb=true)` 真实 `DaoTaskStateStore` cross-restart round-trip）、`ai-dev/logs/`、`docs-for-ai/`（若 owner-doc 裁定需更新）

- Item Types: `Proof`（cross-restart mainStep 续跑经序列化边界生效）+ `Follow-up`（零回归 + 文档）

- [x] 新增 cross-restart E2E（真实 `DaoTaskStateStore`，`@NopTestConfig(localDb=true)`，非 in-memory 引用）：fresh execute composite mainStep 至中间位置（save 写 `bodyStepIndex=N` 经 `copyStepStateToEntity:314` + DB 列）→ 模拟中断 → fresh store `loadTaskState` + resume `newMainStepRuntime` → 加载持久化 mainStep 状态（`findStepEntity` + `toStepStateBean` 反序列化恢复 `bodyStepIndex=N` + `afterLoad`）→ mainStep 从 N 续跑完成 → 断言续跑语义经 DB 序列化边界存活
- [x] Anti-Hollow 断言：resume 后**前置已完成子步骤 body 执行计数为 0**（证明 mainStep 控制流位置本身被恢复、从 N 续跑，非「从 0 重跑靠 leaf reader 逐一 skip」的伪装）；resume 后**未完成的后续子步骤正常执行**（续跑真实推进，非跳过全部）
- [x] 零回归：`./mvnw test -pl nop-task/nop-task-core,nop-task/nop-task-ext,nop-task/nop-task-dao,nop-ai/nop-ai-agent -am -T 1C` 全绿（plans 252-262 状态机 + reader + 终态 driver + resume 区分/短路 + exception 持久化 + task 级 lifecycle hook + decorator/bizFatal/reliability + nop-ai-agent）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] **端到端验证**（#22）：从 fresh execute（mainStep 写中间 `bodyStepIndex=N`）→ DB → fresh load（恢复 N）→ resume 续跑完成 完整路径连通（真实 `DaoTaskStateStore`，非 in-memory 引用），续跑语义经 round-trip 可观测
- [x] **Anti-Hollow Check**（#22/#23/#24）：E2E 经真实 `DaoTaskStateStore` DB round-trip 证明 mainStep `bodyStepIndex` 经持久化边界存活并驱动续跑；前置已完成子步骤不重复执行（计数为 0）、后续子步骤正常执行；无空方法体/静默跳过/no-op 作为「正常实现」
- [x] 新增功能各有 focused 测试覆盖（#25）：中间位置续跑 + 前置不重复 + 后续推进 各有 E2E 断言
- [x] 零回归：plans 252-262 + nop-ai-agent 全绿（含终态 resume 短路行为不变）
- [x] owner-doc 裁定落地（`No owner-doc update required`——内部 resume 持久化扩展，task public 执行契约不变，新增 `default` 方法向后兼容）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] `ITaskStateStore` 声明 mainStep 加载契约，`DaoTaskStateStore` 实现（按 `@main` 加载 + `toStepStateBean` + `afterLoad` + 无行 null），`TaskRuntimeImpl.newMainStepRuntime()` resume 接线（契约 + 实现 + wiring 三者齐备，非仅接口存在）
- [x] cross-restart `DaoTaskStateStore` DB round-trip E2E 证明 mainStep 从已持久化中间 `bodyStepIndex` 续跑、前置子步骤不重复执行（#22 端到端 + #23 接线验证）
- [x] 终态 task resume 短路（plans 259/260）行为不变（零回归，regression guard 断言）
- [x] 必要 focused verification 已完成（单测 mainStep 续跑 + cross-restart E2E + Anti-Hollow 计数断言）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（终态短路改造 / leaf-step reader 改造 / 全量字段持久化 / stacktrace / mainStep 写入路径改造 / fail() 变更 / 跨 step 变量持久化 均为显式 Non-Goals）
- [x] 受影响 owner docs 已同步到 live baseline，或明确写明 `No owner-doc update required`
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）resume 路径在 runtime 加载并消费持久化 mainStep 状态（经真实 DB round-trip 路径验证，非仅类型存在），（b）mainStep 从中间位置续跑、前置子步骤不重复执行，（c）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-task/nop-task-core,nop-task/nop-task-dao,nop-task/nop-task-ext -am`
- [x] `./mvnw test -pl nop-task/nop-task-core,nop-task/nop-task-ext,nop-task/nop-task-dao,nop-ai/nop-ai-agent -am -T 1C`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（本计划无 deferred 项。所有 Non-Goals 均为显式 rejected / out-of-scope improvement / optimization candidate，非「延期但需裁定」项。）

## Non-Blocking Follow-ups

- 给更多 composite step（如 Graph）补充/校准 `saveState` 频率以提升续跑粒度（out-of-scope improvement，本计划只新增读取路径，写入路径既有行为不变）。
- step-state 全量字段持久化 / 完整历史 entity 模型（optimization candidate，6× carry-over）。
- 跨 step 变量 / evalScope 中间状态持久化（out-of-scope improvement，独立 feature，需独立序列化设计）。

## Closure

Status Note: plan 263 闭合 nop-task cross-restart resume 的最后一块功能缺口——mainStep envelope intermediate-state restore。resume 路径现在加载持久化 mainStep 状态（stepPath=`@main`），使 composite mainStep（Sequential/Selector/Loop/LoopN/Graph）从其已持久化的 `bodyStepIndex`（flow 位置）续跑，与 leaf-step reader（plan 257）、task-envelope 终态短路（plans 259/260）共同构成完整的 task-level cross-restart resume。契约 + 实现 + 接线 + Anti-Hollow E2E 验证齐备，零回归。
Completed: 2026-06-19

Closure Audit Evidence:

- Reviewer / Agent: independent explore subagent `ses_122f81ae8ffetMseUwVqQI7bOT`
- Evidence:
  - **Contract** PASS: `ITaskStateStore.java:27-29` — `default ITaskStepState loadMainStepState(ITaskState, ITaskRuntime)` returns null (backward compatible).
  - **Implementation** PASS: `DaoTaskStateStore.java:180-188` — `@Override loadMainStepState` uses `findStepEntity(taskInstanceId, "@main")` + `toStepStateBean` (round-trips bodyStepIndex/stepStatus/result) + `state.afterLoad(taskRt)`; returns null if no entity.
  - **Wiring** PASS: `TaskRuntimeImpl.java:198-218` — `newMainStepRuntime()` in `recoverMode=true` calls `loadMainStepState` first, falls back to `newMainStepState` if null; `recoverMode=false` always fresh.
  - **Anti-Hollow** PASS: `loadMainStepState` is a real DB read (not stub/no-op); loaded state consumed by `SequentialTaskStep.execute:47` reading `bodyStepIndex`; counter-based assertions prove preceding sub-steps not re-executed (counter=1 for N=2, not 3); no TODO/FIXME/empty body in new code.
  - **Tests** PASS: `TestMainStepEnvelopeResume` (9 tests) + `TestMainStepEnvelopeResumeCrossRestart` (4 tests) — all contain real bodyStepIndex/counter assertions. Surefire: 9+4=13 new tests, 0 failures.
  - **Backward compatibility** PASS: `DefaultTaskStateStore` inherits default (null); all 10+ impl/subclasses verified unbroken; grep confirms only 6 `loadMainStepState` call sites (1 interface + 1 override + 1 caller + 3 tests).
  - **Zero regression** PASS: `./mvnw test -pl nop-task/nop-task-core,nop-task/nop-task-ext,nop-task/nop-task-dao,nop-ai/nop-ai-agent -am -T 1C` → BUILD SUCCESS (nop-task-core 86 + nop-task-ext 82 + nop-ai-agent 2714 = 0 failures/errors).
  - **Deferred 项分类检查**: 无 in-scope live defect 被降级——所有 Non-Goals（终态短路改造/leaf reader 改造/全量字段持久化/stacktrace/mainStep 写入路径/fail() 变更/跨 step 变量持久化）均为显式 rejected/out-of-scope/optimization。

Follow-up:

- 给更多 composite step（如 Graph）补充/校准 `saveState` 频率以提升续跑粒度（out-of-scope improvement）。
- step-state 全量字段持久化 / 完整历史 entity 模型（optimization candidate，6× carry-over）。
- 跨 step 变量 / evalScope 中间状态持久化（out-of-scope improvement，独立 feature）。
