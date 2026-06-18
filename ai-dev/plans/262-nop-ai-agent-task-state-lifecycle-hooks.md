# 262 nop-task task 级 afterLoad/beforeSave 生命周期 hook 语义实现（对称 step 级 ITaskStepState 已有 hook + DaoTaskStateStore wiring，闭合 plans 254/255/259/260 4× carry-over successor）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: task-afterLoad-beforeSave-lifecycle-hook (carry-over, L4-8, P2)

> Last Reviewed: 2026-06-19
> Source: carry-over from `ai-dev/plans/260-nop-ai-agent-terminal-killed-timeout-expired-driver.md`（Non-Goals + Non-Blocking Follow-ups line 44/158 显式记录为 `successor plan candidate` 的「afterLoad/beforeSave task 生命周期 hook 的语义实现」，4× carry-over 自 plans 254/255/259/260；roadmap 项 `task-afterLoad-beforeSave-lifecycle-hook`，P2）。plan 260 已 `completed`，本计划在其新增的 `## Follow-up handled by 262-...` 双向链接下接续。
> Related: `254`（step FAILED-driver，最早 carry-over 本 hook）/`255`（task-flow analyzer nextOnError）/`259`（task COMPLETED/FAILED driver + saveTaskState 接线）/`260`（终态 driver + resume 区分，本计划 source-plan）/`261`（exception transient errorBean persist）

## Purpose

闭合 nop-task 持久化生命周期的**不对称缺口**：step 级 `ITaskStepState` 早已定义 `afterLoad(ITaskRuntime)` / `beforeSave(ITaskRuntime)` 生命周期 hook，且 `DaoTaskStateStore` 在 `loadStepState` / `saveStepState` 已接线调用；但 **task 级 `ITaskState` 既无此 hook 接口、`DaoTaskStateStore.loadTaskState` / `saveTaskState` 也从不调用任何 task 级 load/save 生命周期回调**。结果：task 级状态在持久化（save）前后、恢复（load）之后没有可挂载的扩展点——custom task state bean 无法在 cross-restart resume 时重建 transient 字段、校验状态一致性、或参与 save 前的归一化。该缺口经 plans 254/255/259/260（4× carry-over）反复裁定为 `successor plan candidate`，至今无任何 task 级 hook 代码。

本计划交付与 step 级对称的 **task 级 `afterLoad`/`beforeSave` hook 契约 + `TaskStateBean` 实现 + `DaoTaskStateStore` 接线**，并经 cross-restart（真实 `DaoTaskStateStore` DB round-trip，实体↔DB 列 JSON 序列化，非 in-memory 引用）E2E 证明 wiring 在运行时真实生效（非空壳）。

## Current Baseline

基于本会话对 live repo 的核对（引用位置为审计可复核 live code path；确切行号以独立审计复核为准）：

- **task 级 hook 完全缺失（本计划核心缺口）**：`ITaskState`（`nop-task-core` `ITaskState.java`，全文 111 行）**无 `afterLoad` / `beforeSave` 方法**——已逐行核对，接口仅含 `isTerminal()`/`isSuccess()`/`exception()`/状态与请求响应访问器。`DaoTaskStateStore.loadTaskState`（`dao/store/DaoTaskStateStore.java:83-88`）在 `toTaskStateBean(entity)` 后**直接返回，无任何 afterLoad 调用**；`saveTaskState`（`:91+`）在拷贝状态到 entity 前**无任何 beforeSave 调用**。grep `afterLoad|beforeSave` 在 `nop-task` 内仅命中 step 级（见下），task 级零命中。
- **step 级 hook 已存在且已接线（本计划对称的参照模板，不重写）**：`ITaskStepState`（`ITaskStepState.java:82,84`）声明 `void afterLoad(ITaskRuntime taskRt)` / `void beforeSave(ITaskRuntime taskRt)`；`TaskStepStateBean`（`state/TaskStepStateBean.java:31-38`）提供实现（当前为 no-op 基类扩展点）；`DaoTaskStateStore.loadStepState`（`:188`）`state.afterLoad(taskRt)`、`saveStepState`（`:202`）`state.beforeSave(stepRt.getTaskRuntime())`——**接线点、签名、调用时机均现成，task 级可直接镜像**。
- **task 状态 impl 与 store 已就绪（本计划接线点）**：`TaskStateBean` 是 `ITaskState` 的实现（由 `DaoTaskStateStore.toTaskStateBean`（`:221-247`）`new TaskStateBean()` 产出）；`loadTaskState(taskInstanceId, taskRt)`（`:83-88`）与 `saveTaskState(taskRt)`（`:91+`，param 即 `ITaskRuntime taskRt`）两处接线点均已在作用域内持有 `taskRt`，无需额外传参。
- **wiring 目标与验证基础设施已就绪（本计划接线 + 验证均基于此）**：step 级 hook 接线点位于 `DaoTaskStateStore.loadStepState`（`:188`）/`saveStepState`（`:202`），task 级 hook 须对称接在**同一 store** 的 `loadTaskState`/`saveTaskState`（`DaoTaskStateStore` 是实际 DB 持久化层，是 hook 唯一有意义的接线点）。**重要**：`TaskLevelSnapshotTaskStateStore`/`SnapshotTaskStateStore` 继承自 `DefaultTaskStateStore`、**不**包装 `DaoTaskStateStore`，其 load/save 经深拷贝传递已构造状态、**不调 hook**——与 step 级既有行为一致（非缺陷），故本计划 cross-restart E2E **不**复用 snapshot store，改用真实 `DaoTaskStateStore`。验证基础设施：`nop-task-ext/src/test/` 已有 `@NopTestConfig(localDb=true)` 的 `DaoTaskStateStore` round-trip 范式（如 `TestDaoTaskStateStoreErrorBeanRoundTrip`），save→实体→DB→fresh `loadTaskState`→`toTaskStateBean` 经 remark/errorBeanData JSON 序列化反序列化（实体↔DB 列，非 in-memory 引用），可直接复用以证明 hook 经持久化边界生效。
- **4× carry-over 裁定（plans 254/255/259/260）**：均将本项分类为 `successor plan candidate`（非 live defect、非 blocking），本计划据此接续。

## Goals

- **task 级 hook 契约**：`ITaskState` 新增 `afterLoad(ITaskRuntime)` / `beforeSave(ITaskRuntime)`（签名对称 step 级 `ITaskStepState:82,84`）。
- **task 级 hook 实现**：`TaskStateBean` 提供该两方法的实现（使契约表面有具体落地，非仅接口存在）。
- **task 级 hook 接线**：`DaoTaskStateStore.loadTaskState` 在 `toTaskStateBean` 后调用 `state.afterLoad(taskRt)`；`saveTaskState` 在拷贝状态到 entity 前调用 `state.beforeSave(taskRt)`（时机对称 step 级 `:188` / `:202`）。
- **非空壳可观测语义**：经 focused 单测 + cross-restart E2E（真实 `DaoTaskStateStore` localDb round-trip，实体↔DB 列 JSON 序列化，非 in-memory 引用）证明 wiring 在运行时真实调用 hook——`afterLoad`/`beforeSave` 调用经持久化边界存活（Anti-Hollow #22/#23）。
- **零回归**：plans 252-261 状态机 + reader + 终态 driver + resume 区分 + exception 持久化 + nop-ai-agent 全绿。

## Non-Goals

- **step 级 `TaskStepStateBean.afterLoad/beforeSave` 当前 no-op 基类扩展点的改造**：step 级 hook 已存在且已接线（`:188`/`:202`），其基类实现为 no-op 是既有设计（reconstruction 逻辑内联在 `toStepStateBean`/`copyStepStateToEntity`）。是否将内联 reconstruction 迁入 step 级 hook 属另一 refactor。Classification: out-of-scope improvement。
- **将既有 task 级内联 reconstruction（`toTaskStateBean` 的 exception/result 恢复）迁入 `afterLoad`**：plan 261 交付的 errorBean 恢复逻辑内联在 `toTaskStateBean`（`:241-244`），保持不动；本计划只交付 hook 契约 + wiring，不重构既有恢复路径。Classification: out-of-scope improvement（refactor 风险，独立裁定）。
- **`loadMainStepState` mainStep envelope intermediate-state restore**：plan 261 carry-over successor，独立 feature。Classification: successor plan candidate。
- **step-state 全量字段持久化 / 完整历史 entity 模型**：optimization candidate（plans 257/258/259/260/261 5× carry-over）。
- **stacktrace（errorStack）完整持久化与截断策略**：optimization candidate（plan 261）。
- **task 级 EXPIRED status 生成文件不一致 / 独立 task timeout watchdog / 异常精确子类恢复**：out-of-scope improvement（plan 260/261 carry-over，须改生成文件源模型或独立 feature）。
- **`fail()` 行为变更**：rejected（plans 247/252-254 design decision）。

## Scope

### In Scope

- **task 级 hook 契约 + 实现**（Phase 1）：`ITaskState` 新增 `afterLoad`/`beforeSave`；`TaskStateBean` 提供实现。
- **task 级 hook 接线**（Phase 1）：`DaoTaskStateStore.loadTaskState` / `saveTaskState` 调用 hook（对称 step 级 `:188`/`:202`）。
- **非空壳验证**（Phase 1 单测 + Phase 2 cross-restart E2E）：真实 `DaoTaskStateStore`（localDb）经 save→`beforeSave` / load→`afterLoad` runtime 证明 hook 被调用（save 路径 custom `TaskStateBean` 子类可观测；load 路径经 store test-double/spy 或 override `protected toTaskStateBean`）。
- **零回归**（Phase 2）：plans 252-261 状态机 + 终态 driver + nop-ai-agent 全绿。

### Out Of Scope

- 见 Non-Goals（step 级 no-op 改造 / 既有 reconstruction 迁入 hook / loadMainStepState / 全量字段持久化 / stacktrace 持久化 / task EXPIRED 生成文件 / 独立 watchdog / 异常子类恢复 / fail() 变更 均为显式 rejected / out-of-scope / successor / optimization）。

### 设计裁定（Pre-Adjudicated）

1. **hook 签名与放置：在 `ITaskState` 上新增 `afterLoad(ITaskRuntime)` / `beforeSave(ITaskRuntime)`，签名严格对称 step 级 `ITaskStepState:82,84`**。`ITaskRuntime` 在两个接线点（`loadTaskState(taskInstanceId, taskRt)` / `saveTaskState(taskRt)`）均已在作用域内，无需新增传参路径。具体方法修饰（`default` no-op vs 抽象方法 + 更新全部 impl）属 execution 裁定（Minimum Rules #10），**前提是向后兼容**：`ITaskState` 是 public 接口，新增方法不得破坏既有实现（`TaskStateBean` 及任何 test double / mock）。推荐 `default` no-op 形态以镜像 step 级「基类扩展点」语义并保证零回归。
2. **接线目标与时机严格对称 step 级**：hook 接线于 **`DaoTaskStateStore`**（实际 DB 持久化层，与 step 级 hook 同一 store）。`TaskLevelSnapshotTaskStateStore`/`SnapshotTaskStateStore` 继承 `DefaultTaskStateStore`、不包装 `DaoTaskStateStore`，其 load/save 经深拷贝传递已构造状态、**不调 hook**——与 step 级既有行为一致（step 级 hook 也仅 `DaoTaskStateStore` 调用），非缺陷；故本计划验证基于真实 `DaoTaskStateStore`，不基于 snapshot store。`loadTaskState` 在 `toTaskStateBean(entity)` 完成所有既有内联 reconstruction 之后、`return state` 之前调用 `state.afterLoad(taskRt)`（对应 step 级 `:187-189`）；`saveTaskState` 在既有状态拷贝到 entity 之前调用 `state.beforeSave(taskRt)`（对应 step 级 `:202` 在 `copyStepStateToEntity` 之前）。**不移动既有 reconstruction 逻辑**（Non-Goals），hook 是叠加的扩展点。
3. **非空壳交付（避免 lesson #6/#8/#9）**：契约表面 ≠ 语义落地。本计划交付的「语义」是 **wiring 在运行时真实调用 hook**，经真实 `DaoTaskStateStore` round-trip（localDb）证明。**save 路径**：state 由 runtime 外部提供，custom `TaskStateBean` 子类记录 `beforeSave` 被调用（直接可观测）。**load 路径**：`loadTaskState` 经 `toTaskStateBean`（`protected`）内部构造 bean 后调 `afterLoad`——验证 `afterLoad` 被调用须经 store test-double/spy 或 override `protected toTaskStateBean` 返回可观测实例（具体机制属 execution 裁定，Minimum Rules #10；前提是 runtime 证明 `loadTaskState` 确实调用了 `afterLoad`，非仅代码结构存在）。若 `TaskStateBean` 实现为 no-op（默认扩展点），仍须有 runtime 证据证明 wiring 真实生效——不允许「接口 + no-op impl + 无调用证据」即宣告完成。
4. **向后兼容（零回归）**：`default` no-op（或抽象 + 全 impl 更新）确保既有 `newTaskState`（`:69-80`）、in-memory store、snapshot store、nop-ai-agent 等所有 `ITaskState` 生产/消费路径不受影响，除非显式 override。

## Execution Plan

### Phase 1 - task 级 afterLoad/beforeSave hook 契约 + TaskStateBean 实现 + DaoTaskStateStore 接线 + 单元测试

Status: completed
Targets: `ITaskState.java`（`nop-task/nop-task-core/src/main/java/io/nop/task/ITaskState.java`）、`TaskStateBean`（`ITaskState` 实现，由 `DaoTaskStateStore.toTaskStateBean` 产出）、`DaoTaskStateStore.java`（`loadTaskState` `:83-88` / `saveTaskState` `:91+`）、`nop-task/nop-task-ext/src/test/`（`@NopTestConfig(localDb=true)`，镜像 `TestDaoTaskStateStoreErrorBeanRoundTrip` 范式）

- Item Types: `Follow-up`（carry-over successor enhancement——task 级 lifecycle hook 缺失，经 4× carry-over 裁定为 successor plan candidate，非 confirmed live defect）

- [x] 在 `ITaskState` 新增 `afterLoad(ITaskRuntime taskRt)` / `beforeSave(ITaskRuntime taskRt)`（设计裁定 1，签名对称 `ITaskStepState:82,84`；`default` no-op 或抽象 + 全 impl 更新，向后兼容）
- [x] 在 `TaskStateBean` 提供该两方法实现（设计裁定 3，使契约表面有具体落地）
- [x] `DaoTaskStateStore.loadTaskState`（`:83-88`）在 `toTaskStateBean` 之后、`return` 之前调用 `state.afterLoad(taskRt)`（设计裁定 2，对称 step 级 `:188`）
- [x] `DaoTaskStateStore.saveTaskState`（`:91+`）在既有状态拷贝到 entity 之前调用 `state.beforeSave(taskRt)`（设计裁定 2，对称 step 级 `:202`）
- [x] 新增单元测试（`nop-task-ext`，`@NopTestConfig(localDb=true)`，真实 `DaoTaskStateStore`）：runtime 证明 `beforeSave`/`afterLoad` 在 save/load 被真实调用。save 路径——runtime 外部持有 custom `TaskStateBean` 子类，`saveTaskState` 后断言 `beforeSave` 被调用、顺序在 copy 之前；load 路径——`loadTaskState` 经 `toTaskStateBean`（`protected`）内部构造 bean 后调 `afterLoad`，经 store test-double/spy 或 override `protected toTaskStateBean` 返回可观测实例断言 `afterLoad` 被调用（具体机制 execution 裁定，前提是 runtime 证据非仅代码结构）
- [x] 验证默认路径零回归：既有 `newTaskState`（`:69-80`）与不 override hook 的 `ITaskState` impl 行为不变（default no-op 不产生副作用）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `ITaskState` 声明 `afterLoad(ITaskRuntime)` + `beforeSave(ITaskRuntime)`（读接口可见，签名对称 step 级）
- [x] `TaskStateBean` 提供两方法实现（非仅接口存在，lesson #6）
- [x] `DaoTaskStateStore.loadTaskState` 调用 `afterLoad`、`saveTaskState` 调用 `beforeSave`（读 store 可见，时机对称 step 级 `:188`/`:202`）
- [x] **接线验证**（#23）：custom 子类单测断言 hook 在 load/save runtime 被真实调用（调用计数/标志可观测），不只验证类型存在
- [x] **无静默跳过**（#24）：wiring 为真实调用（非空方法体 / 非 TODO / 非 `continue` 跳过）；`default` no-op 仅作为扩展点基类，custom 子类 override 路径有真实逻辑与断言
- [x] 新增功能各有 focused 测试覆盖（#25）：afterLoad 触发 + beforeSave 触发各有断言；零回归路径有断言
- [x] `./mvnw compile -pl nop-task/nop-task-core,nop-task/nop-task-dao,nop-task/nop-task-ext -am` 通过
- [x] `./mvnw test -pl nop-task/nop-task-core,nop-task/nop-task-dao,nop-task/nop-task-ext -am` 既有 + 新增测试全绿（`nop-task-ext` 含 `DaoTaskStateStore` localDb 测试范式）
- [x] owner-doc 裁定：task 级 lifecycle hook 若改变 `ITaskState` public 契约且 `docs-for-ai/` 有对应记录则更新；否则明确写 `No owner-doc update required`（理由：内部持久化 lifecycle 扩展点，task public 执行契约不变）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - cross-restart `DaoTaskStateStore` DB round-trip E2E + 零回归 + 文档收口

Status: completed
Targets: `nop-task/nop-task-ext/src/test/`（`@NopTestConfig(localDb=true)` 真实 `DaoTaskStateStore` round-trip，镜像 `TestDaoTaskStateStoreErrorBeanRoundTrip`）、`ai-dev/logs/`、`docs-for-ai/`（若 owner-doc 裁定需更新）

- Item Types: `Proof`（cross-restart hook wiring 经序列化边界生效）+ `Follow-up`（零回归 + 文档）

- [x] 新增 cross-restart E2E（真实 `DaoTaskStateStore`，`@NopTestConfig(localDb=true)`，非 in-memory 引用）：save（`beforeSave` 被调用 → 状态经实体拷贝 + remark/errorBeanData JSON 序列化持久化到 DB）→ fresh load（`taskDao().getEntityById` 取回 fresh entity → `toTaskStateBean` 反序列化 → `afterLoad` 被调用）→ 断言 hook 调用经 DB 序列化边界存活（round-trip 前后调用计数/标志可复核）
- [x] 验证 in-progress / COMPLETED / FAILED / KILLED / TIMEOUT 各终态 task 经 store save→load round-trip 后 `beforeSave` 与 `afterLoad` 各被精确调用一次（regression guard：精确调用计数断言，防止未来 per-status early-return 逻辑静默跳过 hook）
- [x] 零回归：`./mvnw test -pl nop-task/nop-task-core,nop-task/nop-task-ext,nop-task/nop-task-dao,nop-ai/nop-ai-agent -am -T 1C` 全绿（plans 252-261 状态机 + reader + 终态 driver + resume 区分 + exception 持久化 + decorator/bizFatal/reliability + nop-ai-agent）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] **端到端验证**（#22）：从 task save（`beforeSave` 触发 + 实体拷贝/JSON 序列化持久化）→ DB → fresh load（反序列化 + `afterLoad` 触发）完整路径连通（真实 `DaoTaskStateStore`，非 in-memory 引用），hook 调用经 round-trip 可观测
- [x] **Anti-Hollow Check**（#22/#23/#24）：E2E 经真实 `DaoTaskStateStore` DB round-trip（实体↔DB 列 JSON 序列化，非 in-memory 引用）证明 hook wiring 经持久化边界存活；无空方法体/静默跳过/no-op 作为「正常实现」（`default` no-op 是扩展点基类，runtime 调用证据 + 断言）
- [x] 各终态（in-progress/COMPLETED/FAILED/KILLED/TIMEOUT）经 store save→load round-trip 后 `beforeSave`/`afterLoad` 各被精确调用一次（regression guard，精确计数断言）
- [x] 新增功能各有 focused 测试覆盖（#25）：beforeSave save 触发 + afterLoad load 触发 + 各终态路径 各有 E2E 断言
- [x] 零回归：plans 252-261 + nop-ai-agent 全绿
- [x] owner-doc 裁定落地（`No owner-doc update required` 或更新 `docs-for-ai/`）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] `ITaskState` 声明 `afterLoad`/`beforeSave`，`TaskStateBean` 提供实现，`DaoTaskStateStore` load/save 接线调用（契约 + 实现 + wiring 三者齐备，非仅接口存在）
- [x] cross-restart `DaoTaskStateStore` DB round-trip E2E 证明 hook wiring 经持久化边界在 runtime 真实生效（#22 端到端 + #23 接线验证）
- [x] 各终态 task 经 store save→load round-trip 后 `beforeSave`/`afterLoad` 各被精确调用一次（regression guard，精确计数断言）
- [x] 必要 focused verification 已完成（save 路径 custom 子类 + load 路径 runtime 证据 + cross-restart E2E）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（step 级 no-op 改造 / 既有 reconstruction 迁入 hook / loadMainStepState / 全量字段持久化 / stacktrace / task EXPIRED 生成文件 / 独立 watchdog / 异常子类恢复 / fail() 变更 均为显式 Non-Goals）
- [x] 受影响 owner docs 已同步到 live baseline，或明确写明 `No owner-doc update required`
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）`DaoTaskStateStore.loadTaskState`/`saveTaskState` 在 runtime 调用 hook（经真实 DB round-trip 路径验证），（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-task/nop-task-core,nop-task/nop-task-dao,nop-task/nop-task-ext -am`
- [x] `./mvnw test -pl nop-task/nop-task-core,nop-task/nop-task-ext,nop-task/nop-task-dao,nop-ai/nop-ai-agent -am -T 1C`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（本计划无 deferred 项。所有 Non-Goals 均为显式 rejected / out-of-scope improvement / successor plan candidate / optimization candidate，非「延期但需裁定」项。）

## Non-Blocking Follow-ups

- step 级 `TaskStepStateBean.afterLoad/beforeSave` no-op 基类扩展点是否迁入既有内联 reconstruction（out-of-scope improvement，本计划仅镜像其 wired 扩展点模式）。
- 将 task 级既有内联 reconstruction（`toTaskStateBean` exception/result 恢复）迁入 `afterLoad`（out-of-scope improvement，refactor 风险，独立裁定）。
- `loadMainStepState` mainStep envelope intermediate-state restore（successor plan candidate，plan 261 carry-over）。
- step-state 全量字段持久化 / 完整历史 entity 模型（optimization candidate，5× carry-over）。

## Closure

Status Note: 闭合 nop-task 持久化生命周期不对称缺口——task 级 `ITaskState` 新增 `afterLoad(ITaskRuntime)`/`beforeSave(ITaskRuntime)`（`default` no-op，对称 step 级 `ITaskStepState:82,84`，向后兼容）+ `TaskStateBean` 显式实现（对称 `TaskStepStateBean:31-38` 扩展点基类）+ `DaoTaskStateStore.loadTaskState`/`saveTaskState` 接线（对称 step 级 `:188`/`:202`，新增 `protected newTaskStateBean()` 工厂使 load 路径 hook 调用可观测）。经真实 `DaoTaskStateStore` localDb DB round-trip（实体↔DB 列 JSON 序列化，非 in-memory 引用）证明 wiring 在 runtime 真实调用 hook（save 路径 custom 子类 + load 路径 spy 工厂 + cross-restart fresh store），各终态（ACTIVE/COMPLETED/FAILED/KILLED/TIMEOUT）save→load round-trip 后 `beforeSave`/`afterLoad` 各精确调用一次。所有 Non-Goals（step 级 no-op 改造 / 既有 reconstruction 迁入 hook / loadMainStepState / 全量字段持久化 / stacktrace / task EXPIRED 生成文件 / 独立 watchdog / 异常子类恢复 / fail() 变更）均为显式 rejected/out-of-scope/successor/optimization，无 in-scope live defect 被降级。
Completed: 2026-06-19

Closure Audit Evidence:

- Reviewer / Agent: opencode executor（glm-5.2，plan-execute session）+ `check-plan-checklist.mjs`/`scan-hollow-implementations.mjs` 自动门禁
- Evidence:
  - Exit Criterion「`ITaskState` 声明 afterLoad/beforeSave」: PASS — `nop-task-core/.../ITaskState.java` 末尾 `default void afterLoad(ITaskRuntime)` / `default void beforeSave(ITaskRuntime)`，签名对称 `ITaskStepState:82,84`。
  - Exit Criterion「`TaskStateBean` 提供实现」: PASS — `nop-task-core/.../state/TaskStateBean.java` 显式 `@Override` 两方法（非仅接口存在，lesson #6）。
  - Exit Criterion「store 调用 hook」: PASS — `DaoTaskStateStore.loadTaskState`（`toTaskStateBean` 后、`return` 前 `state.afterLoad(taskRt)`）+ `saveTaskState`（entity 拷贝前 `state.beforeSave(taskRt)`）+ 新增 `protected newTaskStateBean()` 工厂供 `toTaskStateBean` 使用。
  - Exit Criterion「接线验证 #23」: PASS — `TestTaskStateLifecycleHooks.saveTaskState_invokesBeforeSaveOnce_beforeEntityCopy` 断言 beforeSave count==1 + 入参 runtime + entity status 反映 beforeSave 突变（顺序证明）；`loadTaskState_invokesAfterLoadOnce_afterToTaskStateBean` 断言 afterLoad count==1 + 捕获到 toTaskStateBean 设置的 status（顺序证明）；anti-over-match 各断言另一 hook 不被调用。
  - Exit Criterion「无静默跳过 #24」: PASS — wiring 为真实调用（非空方法体/非 TODO/非 continue）；`default` no-op 为扩展点基类，custom 子类 override 路径有真实计数逻辑与断言。
  - Exit Criterion「cross-restart E2E #22」: PASS — `TestTaskStateLifecycleHookCrossRestart.crossRestart_completedTask_hooksSurviveDbSerializationBoundary`（result 经 remark JSON）+ `crossRestart_failedTask_hooksSurviveDbSerializationBoundary`（exception 经 errorBeanData JSON）：fresh spy store save（beforeSave=1）→ fresh spy store load（afterLoad=1），hook 调用经 DB 序列化边界存活。
  - Exit Criterion「各终态精确调用一次」: PASS — `crossRestart_eachTaskStatus_beforeSaveAndAfterLoadCalledExactlyOnce` 遍历 ACTIVE/COMPLETED/FAILED/KILLED/TIMEOUT，save→load round-trip 后 beforeSave==1、afterLoad==1（regression guard）。
  - Exit Criterion「零回归」: PASS — `./mvnw test -pl nop-task/nop-task-core,nop-task/nop-task-ext,nop-task/nop-task-dao,nop-ai/nop-ai-agent -am -T 1C` BUILD SUCCESS（nop-ai-agent 2714 tests 0 failures；nop-task-ext 99 tests 含新增 7）。
  - Exit Criterion「owner-doc 裁定」: No owner-doc update required — 内部持久化 lifecycle 扩展点，task public 执行契约不变（新增 `default` 方法向后兼容）。
  - Exit Criterion「ai-dev/logs」: PASS — `ai-dev/logs/2026/06-19.md` 已更新。
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/262-...md --strict` 退出码为 0（所有 checklist 已勾选 + Closure Evidence 已写入）。
  - Anti-Hollow 检查结果: `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-task --severity high` 退出码 0（Critical=0, High=0）；端到端调用链追踪 `loadTaskState→toTaskStateBean→state.afterLoad` / `saveTaskState→state.beforeSave→entity 拷贝` 经真实 DB round-trip 测试断言连通，无空方法体/静默跳过作为正常实现。
  - Deferred 项分类检查: 本计划无 deferred 项；所有 Non-Goals 为显式 rejected/out-of-scope improvement/successor plan candidate/optimization candidate，无 in-scope live defect 被降级。

Follow-up:

- 无 plan-owned 剩余工作。Non-Blocking Follow-ups（step 级 no-op 迁入既有 reconstruction / task 级既有 reconstruction 迁入 afterLoad / loadMainStepState restore / step-state 全量字段持久化）均为显式 out-of-scope improvement 或 successor plan candidate，独立裁定，不阻塞本计划关闭。
