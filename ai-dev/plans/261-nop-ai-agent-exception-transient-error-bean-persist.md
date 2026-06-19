# 261 nop-task exception 持久化 transient 优化——跨重启保留完整 ErrorBean（params + cause chain）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: exception-transient-optimization (carry-over, L4-8, P3)

> Last Reviewed: 2026-06-19
> Source: carry-over from `ai-dev/plans/260-nop-ai-agent-terminal-killed-timeout-expired-driver.md`（Non-Goals line 42「跨重启 exception 持久化 transient 优化：optimization candidate（plans 247/252/254/258/259 carry-over）」+ Non-Blocking Follow-ups line 156 同项）。该 carry-over 自 plans 247/252/254/258/259/260 共 6× 反复裁定为 `optimization candidate`，当前 nop-task 终态 driver / reader / DB 持久化 / resume 短路均已闭合（plans 252-260 completed），exception 持久化序列化细节是最后一项遗留优化。plan 260 已 `completed`。
> Related: `257`（continuation-skip reader，消费 isDone/exception）/`258`（终态 step-state DB 持久化 saveTerminalStateIfDone）/`259`（task COMPLETED/FAILED driver + saveTaskState + resume 短路）/`260`（step EXPIRED/KILLED + task KILLED/TIMEOUT driver + reason-carrying exception）

## Purpose

闭合 nop-task 状态机跨重启 exception 持久化的 **transient lossy gap**：`DaoTaskStateStore` 在 save 时经 `ErrorMessageManager.buildErrorMessage` 从原始 exception 提取出完整 `ErrorBean`（含 errorCode / description / **params** / **cause chain** / errorStack），但**只取 errCode + errMsg 两个字段写入 DB**，其余全部丢弃；load 时只能从 errCode + errMsg 重构一个**无 params、无 cause chain 的泛型 `NopException`**。结果：cross-restart resume 重抛的 exception 丢失了 `.param(...)` 诊断属性与 cause chain——调试跨重启失败时只能看到错误码和消息文本，无法看到原始异常类型、参数上下文、嵌套原因链。

本计划交付完整 ErrorBean 的持久化（新增 `errorBeanData` 列存储 ErrorBean JSON）+ 基于完整 ErrorBean 的 exception 重构，使 resume 重抛的 exception 保留 params + cause chain，与 in-process 执行时抛出的 exception 诊断信息对齐。

## Current Baseline

基于本会话对 live repo 的核对（引用位置为审计可复核 live code path；确切行号以独立审计复核为准）：

- **exception 字段为 transient、不直接序列化**：`TaskStepStateBean.exception`（`nop-task/nop-task-core/src/main/java/io/nop/task/state/TaskStepStateBean.java:28`）声明为 `private transient Throwable exception;`。Java 序列化不保留它；DB 持久化经 `DaoTaskStateStore` 另行处理（见下）。

- **step 级 save 仅提取 errCode + errMsg，丢弃完整 ErrorBean（本计划核心缺口）**：`DaoTaskStateStore.copyStepStateToEntity`（`nop-task/nop-task-dao/src/main/java/io/nop/task/dao/store/DaoTaskStateStore.java:308-316`）调 `ErrorMessageManager.instance().buildErrorMessage(null, exp, false, false)` 得到完整 `ErrorBean`，但只取 `errorBean.getErrorCode()` → `entity.setErrCode(...)` + `errorBean.getDescription()` → `entity.setErrMsg(...)`。ErrorBean 的 **params / cause / errorStack / details / bizFatal 全部丢弃**。

- **step 级 load 重构泛型 NopException，无 params 无 cause**：`DaoTaskStateStore.toStepStateBean`（`DaoTaskStateStore.java:263-270`）从 entity 的 errCode + errMsg 重构 `new NopException(errCode, null, true, true).description(errMsg)`——无 params、无 cause chain、无原始异常类型。

- **task 级 save / load 同一 lossy 模式（镜像 step 级）**：`saveTaskState`（`DaoTaskStateStore.java:110-118`）同样只取 errCode + errMsg；`toTaskStateBean`（`DaoTaskStateStore.java:227-234`）同样重构泛型 NopException。

- **ErrorBean 基础设施已具备完整字段（本计划依赖、不新建）**：`ErrorBean`（`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/ErrorBean.java:30-51`）是 `@DataBean`（Jackson 可序列化），含 `errorCode` / `description` / **`params`**（`Map<String,Object>`，即 NopException `.param(...)` 属性）/ **`cause`**（`ErrorBean`，cause chain）/ `errorStack`（String）/ `details` / `bizFatal` / `severity` / `forPublic` / `sourceLocation`。`ErrorMessageManager.buildErrorMessage` 已从 Throwable 填充这些字段。`ErrorBean` 可经 `JsonTool` 序列化为 JSON。

- **exception 重构基础设施已存在，但 cause chain 未保留（本计划须增强或绕过）**：`NopRebuildException.rebuild(ErrorBean)`（`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/exceptions/NopRebuildException.java:47-65`）从 ErrorBean 重构 `NopRebuildException`，保留 errorCode / description / params / bizFatal / forPublic / sourceLocation——**但 cause 传 null**（`:61` `new NopRebuildException(errorCode, null, false, true)`），cause chain 未恢复。

- **ORM entity 无 ErrorBean 列（本计划须新增）**：`NopTaskStepInstance`（`nop-task/model/nop-task.orm.xml:255-361`）有 `errCode`（propId=38, VARCHAR 200）+ `errMsg`（propId=39, VARCHAR 4000）+ `stateBeanData`（propId=47, VARCHAR 4000，已用于 resultValue JSON）；最高 propId=53（remark）。`NopTaskInstance`（同 orm.xml，最高 propId=37 remark）有 `errCode`（propId=28, VARCHAR 200）+ `errMsg`（propId=29, VARCHAR 500）。**两 entity均无存储完整 ErrorBean 的列**。

- **resume 路径消费重构的 exception（本计划优化其输入质量）**：plans 257-260 交付的 reader / resume 短路（`TaskStepExecution` 终态重抛 `state.exception()`；`TaskImpl.execute` resume `else` 分支 `synthesizeResumeException`）消费 `state.exception()`。本计划不改变消费侧逻辑，只提升 `state.exception()` 重构后保留的诊断信息丰富度。

- **ORM 模型变更属 Protected Area（plan-first）**：`nop-task/model/nop-task.orm.xml` 结构变更需 plan-first + owner doc + test。**本计划即为该 plan-first 过程**，显式记录列新增、包含 owner doc 裁定、包含 round-trip 测试。

## Goals

- **持久化完整 ErrorBean**：step 级 + task 级终态 exception 经 `ErrorBean` 完整序列化（errorCode / description / params / cause chain）持久化到 DB 新增列 `errorBeanData`（JSON），不再只存 errCode + errMsg。
- **重构保留 params + cause chain**：load 时优先从 `errorBeanData`（完整 ErrorBean JSON）重构 exception，保留 `.param(...)` 属性 + cause chain；使 resume 重抛的 exception 与 in-process 执行时诊断信息对齐。
- **向后兼容**：`errorBeanData` 为 nullable 新列；历史行（null errorBeanData）回退现有 errCode + errMsg 重构路径，行为不变。
- **零回归**：plans 252-260 状态机 + reader + 终态持久化 + resume 短路 + decorator/bizFatal/reliability + nop-task-core/ext/dao + nop-ai-agent 全绿。

## Non-Goals

- **stacktrace（errorStack）的完整持久化**：stacktrace 引用的是 pre-restart 执行栈，post-restart 诊断价值有限且体积大（VARCHAR 4000 可能不足）。若 ErrorBean 标准 JSON 序列化包含 errorStack 且不超长则保留；超长时截断或跳过。**params + cause chain 是本计划的核心诊断增量**，errorStack 是 best-effort。Classification: optimization candidate（本计划范围内 best-effort，不做 stacktrace 专项优化）。
- **step-state 全量字段持久化 / 完整历史 entity 模型**：optimization candidate（plans 257/258/259/260 carry-over）。本计划只优化 exception 序列化，不扩展 step-state 其它字段持久化。
- **loadMainStepState（mainStep envelope intermediate-state restore）**：successor plan candidate（plan 259 carry-over）。本计划交付 leaf step + task 两级 exception 持久化，不涉及 mainStep 断点续跑。
- **`fail()` 行为变更**：rejected（plans 247/252-260 design decision，`fail()` 仅保存 exception 不设终态 status）。本计划不改 `fail()`，只改 DB 序列化细节。
- **异常类精确类型恢复（如 NopTaskCancelledException / NopTimeoutException）**：`NopRebuildException.rebuild` 产出的是 `NopRebuildException`（NopException 子类），不恢复原始精确子类。恢复精确子类需 exception 类型注册表（type → constructor 映射），属独立架构决策。Classification: out-of-scope improvement。本计划目标是 params + cause chain 保留，非精确类型恢复。

## Scope

### In Scope

- **ORM 列新增**（Phase 1）：在 `nop-task/model/nop-task.orm.xml` 为 `NopTaskStepInstance` + `NopTaskInstance` 各新增 nullable 列 `errorBeanData`（VARCHAR，precision 4000，存储 ErrorBean JSON）；regenerate 生成 `_NopTaskStepInstance` / `_NopTaskInstance` 对应字段。
- **ErrorBean 持久化 wiring**（Phase 1）：`DaoTaskStateStore` step save（`copyStepStateToEntity`）+ task save（`saveTaskState`）在提取 errCode + errMsg 之外，**追加**完整 ErrorBean JSON 序列化到 `errorBeanData`（超长 / 序列化失败时非致命跳过，与现有 resultValue 序列化模式一致）。
- **ErrorBean 重构 wiring**（Phase 1）：`DaoTaskStateStore` step load（`toStepStateBean`）+ task load（`toTaskStateBean`）优先从 `errorBeanData` 反序列化 ErrorBean 并重构 exception（保留 params + cause chain）；null 时回退现有 errCode + errMsg 路径。
- **cause chain 重构**（Phase 1）：`NopRebuildException.rebuild` 或 DaoTaskStateStore 内部重构逻辑须恢复 cause chain（当前 `rebuild` 传 null cause）。具体增强方式属 execution 裁定（Minimum Rules #10），前提是 ErrorBean 的 `cause` 字段经 round-trip 后反映到重构 exception 的 `getCause()`。
- **round-trip 测试 + cross-restart E2E + 零回归**（Phase 2）：exception 经 save → DB → load round-trip 后 params + cause chain 保留；cross-restart resume 重抛的 exception 含 params；零回归。

### Out Of Scope

- 见 Non-Goals（stacktrace 专项优化 / 全量字段持久化 / loadMainStepState / fail() 变更 / 精确异常类型恢复 均为显式 rejected / out-of-scope / successor / optimization）。

### 设计裁定（Pre-Adjudicated）

1. **新增列 `errorBeanData` 而非复用现有列**。复用 `errMsg`（VARCHAR 500 task / 4000 step）会改变其语义（plain text → JSON），破坏既有 UI / 查询对 errMsg 的消费。复用 `stateBeanData`（已用于 resultValue）会混合语义。**新增 nullable 列是向后兼容、语义清晰的最小侵入方案**。列 precision=4000（与 step 级 errMsg 对齐）；ErrorBean JSON 超 4000 时非致命截断/跳过（与现有 resultValue 序列化 `json.length() <= 4000` 模式一致）。具体 propId 分配属 execution 裁定。ORM 变更属 Protected Area，本 plan 即 plan-first。
2. **load 路径优先 errorBeanData，回退 errCode + errMsg**。`toStepStateBean` / `toTaskStateBean`：`errorBeanData` 非空 → 反序列化 ErrorBean → 重构 exception（params + cause）；`errorBeanData` 为空 → 现有 errCode + errMsg 重构（历史行兼容）。两路径共存，非互斥替换。
3. **cause chain 重构须落地（非可选）**。当前 `NopRebuildException.rebuild(ErrorBean)`（`:61`）传 null cause。本计划须使 cause chain 经 round-trip 可恢复。**推荐选项 (b)**：在 `DaoTaskStateStore` 内部从反序列化的 ErrorBean 递归构造含 cause 的 NopException（ErrorBean.cause → 递归重构 → `initCause`），将变更限定在 nop-task-dao 模块内。**选项 (a)（增强 `NopRebuildException.rebuild`）不推荐**：`rebuild` 是 `nop-api-core`（kernel）public static 方法，全仓库多处调用（`nop-orm-rpc`/`nop-http-api`/`nop-retry-engine`/`nop-ai-core`/`nop-core` 等），增强它会改变所有调用方行为，属跨模块公共 API 变更（接近 Protected Area「框架核心引擎 plan-first」），需独立 plan-first 评估，不在本计划 scope。前提（两选项共同）：`exception.getCause()` round-trip 后非 null（当原始 exception 有 cause 时）。
4. **params 保留须可观测**。round-trip 后重构 exception 的诊断参数（NopException `.getParam(key)` 或 ErrorBean.params）须反映原始 `.param(...)` 值。Exit Criteria 以具体 param key/value 断言定义。
5. **save 仍保留 errCode + errMsg 列写入**（向后兼容 + 简单查询）。`errorBeanData` 是追加列，不替换 errCode + errMsg。既有消费 errCode + errMsg 的 UI / 查询不受影响。
6. **不引入 exception 类型注册表**（精确子类恢复）。rebuild 产出 `NopRebuildException`（NopException 子类），保留 errorCode + params + cause，不恢复 `NopTaskCancelledException` 等精确子类。resume 短路按 errorCode 区分终态（plans 259-260 已按 errCode 分发 KILLED/TIMEOUT/FAILED），不依赖精确子类。

## Execution Plan

### Phase 1 - ORM 列新增 + ErrorBean 持久化 / 重构 wiring

Status: completed
Targets: `nop-task/model/nop-task.orm.xml`（NopTaskStepInstance + NopTaskInstance 各新增 `errorBeanData` 列）、regenerate `_NopTaskStepInstance` / `_NopTaskInstance`（`_gen`）、`DaoTaskStateStore`（`copyStepStateToEntity:308-316` save 追加 + `toStepStateBean:263-270` load 追加 + `saveTaskState:110-118` + `toTaskStateBean:227-234` + cause chain 重构内部逻辑，设计裁定 3 选项 b）

- Item Types: `Follow-up`（optimization candidate——exception 持久化序列化细节优化，非 live defect；6× carry-over 反复裁定为 optimization candidate）

- [x] 在 `nop-task/model/nop-task.orm.xml` 为 `NopTaskStepInstance` 新增 nullable 列 `errorBeanData`（VARCHAR 4000，displayName「错误Bean数据」/ i18n-en「Error Bean Data」）；为 `NopTaskInstance` 新增同名同类型列
- [x] `./mvnw install -pl nop-task/nop-task-dao -am -DskipTests` regenerate，确认 `_NopTaskStepInstance.getErrorBeanData()` / `setErrorBeanData(...)` + `_NopTaskInstance` 对应 getter/setter 已生成（Hard Stop：不手改 `_gen` 文件）
- [x] step save 追加 ErrorBean JSON 序列化（设计裁定 1/5）：`copyStepStateToEntity` 在现有 errCode + errMsg 提取后，追加完整 ErrorBean 经 `JsonTool.serialize(errorBean, false)` → `entity.setErrorBeanData(json)`（超 4000 / 序列化失败非致命跳过，与 resultValue 模式一致）
- [x] step load 追加 ErrorBean 重构（设计裁定 2）：`toStepStateBean` 优先读 `entity.getErrorBeanData()`：非空 → `JsonTool.parse(errorBeanData, ErrorBean.class)` → 重构 exception（保留 params + cause chain）；为空 → 现有 errCode + errMsg 重构（兼容历史行）
- [x] task save 追加 ErrorBean JSON 序列化：`saveTaskState` 对称 step save 追加 `entity.setErrorBeanData(...)`
- [x] task load 追加 ErrorBean 重构：`toTaskStateBean` 对称 step load 追加 errorBeanData 优先路径
- [x] cause chain 重构落地（设计裁定 3，推荐选项 b）：在 `DaoTaskStateStore` 内部从反序列化 ErrorBean 递归构造含 cause 的 NopException（ErrorBean.cause → 递归重构 → initCause），使 `exception.getCause()` round-trip 后非 null（当原始 exception 有 cause 时）。不增强 `NopRebuildException.rebuild`（kernel public API，多处调用方）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `nop-task/model/nop-task.orm.xml` 含 `NopTaskStepInstance` + `NopTaskInstance` 的 `errorBeanData` 列定义；`_NopTaskStepInstance` / `_NopTaskInstance` 生成类含对应 getter/setter（regenerate 后可复核）
- [x] step save 后 DB entity 的 `errorBeanData` 非空（含完整 ErrorBean JSON，含 params + cause），经 entity 字段断言可观测
- [x] task save 后 DB entity 的 `errorBeanData` 非空（同上）
- [x] step load 从 `errorBeanData` 重构的 exception 保留 params：具体 param key/value 经 round-trip 后断言匹配（设计裁定 4）
- [x] step load 重构的 exception 保留 cause chain：`exception.getCause()` round-trip 后非 null（当原始 exception 有 cause 时）且 cause 的 errorCode/description 匹配
- [x] task load 从 `errorBeanData` 重构的 exception 同上（params + cause chain 保留）
- [x] 向后兼容：`errorBeanData` 为 null 时（历史行 / 非 NopException 的 generic exception）回退现有 errCode + errMsg 重构，行为不变
- [x] **无静默跳过**（#24）：errorBeanData 序列化失败时非致命跳过须有日志（非空 catch），不静默吞掉；load 解析失败时回退 errCode + errMsg 须有日志
- [x] `./mvnw compile -pl nop-task/nop-task-dao,nop-task/nop-task-core -am` 通过
- [x] owner-doc 裁定：更新 `docs-for-ai/02-core-guides/model-first-development.md`（若 ORM 列新增模式需补充说明）或明确写 `No owner-doc update required`（若列新增遵循既有标准模式）；Phase 2 复核
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - round-trip 测试 + cross-restart E2E + 零回归

Status: completed
Targets: `nop-task/nop-task-core/src/test/`、`nop-task/nop-task-ext/src/test/`（round-trip / cross-restart E2E 须用真实 DB-backed `DaoTaskStateStore` + `@NopTestConfig(localDb=true)`，参照既有 `TestDaoTaskStateStoreRoundTrip` 模式；**禁止用 in-memory snapshot store** 如 `TaskLevelSnapshotTaskStateStore`——它不经 entity 序列化，exception 引用直接拷贝，无法暴露 errorBeanData 序列化 gap）、`ai-dev/logs/`

- Item Types: `Proof`（round-trip params + cause chain 保留 + cross-restart resume 诊断增量 E2E）

- [x] 新增单元测试（step 级 round-trip，真实 DB-backed `DaoTaskStateStore` + `@NopTestConfig(localDb=true)`）：构造含 `.param(key, value)` + cause chain 的 NopException → `saveStepState` → DB entity → `loadStepState` → 断言重构 exception 的 params 含 key/value + getCause() 非 null + cause errorCode 匹配
- [x] 新增单元测试（task 级 round-trip，同 DB 模式）：对称 step 级，task exception 经 `saveTaskState` → `loadTaskState` round-trip 保留 params + cause
- [x] 新增单元测试（向后兼容）：errorBeanData 为 null（模拟历史行）→ load 回退 errCode + errMsg 重构 → exception errCode + description 正确（行为与 plan 258/259 一致）
- [x] 新增单元测试（序列化失败容错）：ErrorBean JSON 超 4000 字符 → save 非致命跳过 errorBeanData（errCode + errMsg 仍写入）→ load 回退 errCode + errMsg（非静默失败，有日志）
- [x] 新增 cross-restart E2E（真实 DB-backed `DaoTaskStateStore` + `@NopTestConfig(localDb=true)`，非 in-memory snapshot store）：fresh execute（step FAILED with params + cause → 终态 driver → `DaoTaskStateStore.saveStepState` 持久化 errorBeanData 到 DB entity）→ fresh `DaoTaskStateStore.loadStepState`（从 DB entity 反序列化 ErrorBean）→ resume 重抛 exception 含 params + cause chain——**此诊断增量在 plan 258/259 后不成立**（load 只从 errCode + errMsg 重构泛型 NopException，params + cause 丢失）
- [x] 零回归：`./mvnw test -pl nop-task/nop-task-core -am` + `./mvnw test -pl nop-task/nop-task-ext -am` + `./mvnw test -pl nop-task/nop-task-dao -am` + `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` 全绿

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] step round-trip：含 params + cause 的 NopException 经 `saveStepState` → `loadStepState`（真实 DB-backed `DaoTaskStateStore`，非 in-memory snapshot）后 params key/value 匹配 + getCause() 非 null + cause errorCode 匹配（经 DB entity 字段断言可复核）
- [x] task round-trip：同上（task 级 saveTaskState → loadTaskState）
- [x] 向后兼容：历史行（null errorBeanData）load 行为与 plan 258/259 一致（errCode + errMsg 重构，零回归）
- [x] **端到端验证**（#22）：从 step execute（抛含 params + cause 的 exception）→ 终态 driver → `DaoTaskStateStore.saveStepState`（errorBeanData JSON 序列化到 DB entity）→ fresh `DaoTaskStateStore.loadStepState`（从 DB entity 反序列化 ErrorBean）→ resume 重抛 exception（含 params + cause）完整路径连通——**须用真实 DB-backed store（`@NopTestConfig(localDb=true)`），非 in-memory snapshot store**（snapshot store exception 引用直接拷贝不经序列化，无法暴露 errorBeanData gap）
- [x] **接线验证**（#23）：`copyStepStateToEntity` / `saveTaskState` 真实写入 DB entity 的 `errorBeanData`（entity 字段可观测）；`toStepStateBean` / `toTaskStateBean` 真实读取 `errorBeanData` 并重构含 params + cause 的 exception
- [x] **Anti-Hollow Check**（#22/#23/#24）：round-trip 经真实 DB-backed `DaoTaskStateStore`（entity JSON 序列化 / 反序列化，非 in-memory snapshot 引用直接拷贝）证明 errorBeanData 经持久化存活；load 优先 errorBeanData 而非停留在 errCode + errMsg；序列化失败有日志非静默吞掉；无空方法体 / TODO / placeholder
- [x] 新增功能各有 focused 测试覆盖（#25）：step round-trip params + cause / task round-trip / 向后兼容 / 序列化失败容错 / cross-restart E2E 各有断言
- [x] 零回归：plans 252-260 状态机 + reader + 终态持久化 + resume 短路 + decorator/bizFatal/reliability + nop-ai-agent 全绿
- [x] owner-doc 裁定落地（`No owner-doc update required` 或更新 `docs-for-ai/` 若 ORM 列新增模式需文档化）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] ORM 新增 `errorBeanData` 列（NopTaskStepInstance + NopTaskInstance），regenerate 后生成类含 getter/setter
- [x] step save / load 经 errorBeanData 持久化 + 重构完整 ErrorBean（params + cause chain 保留）
- [x] task save / load 同上（task 级 errorBeanData 持久化 + 重构）
- [x] cause chain 经 round-trip 可恢复（`exception.getCause()` 非 null 当原始有 cause 时）
- [x] 向后兼容：null errorBeanData 回退 errCode + errMsg，行为与 plan 258/259 一致
- [x] cross-restart resume 重抛的 exception 含 params + cause chain（诊断增量在 plan 258/259 后首次成立）
- [x] 序列化失败非致命跳过有日志（#24 无静默跳过）
- [x] 必要 focused verification 已完成（step/task round-trip + 向后兼容 + 序列化容错 + cross-restart E2E）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（stacktrace 专项 / 全量字段持久化 / loadMainStepState / fail() 变更 / 精确类型恢复 均为显式 Non-Goals）
- [x] 受影响 owner docs 已同步到 live baseline，或明确写明 `No owner-doc update required`
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）`copyStepStateToEntity` / `saveTaskState` 在 runtime 真实写入 DB entity 的 errorBeanData，（b）`toStepStateBean` / `toTaskStateBean` 真实读取并重构含 params + cause 的 exception，（c）round-trip 经真实 DB-backed entity 序列化（非 in-memory snapshot 引用直接拷贝），（d）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-task/nop-task-dao,nop-task/nop-task-core -am`
- [x] `./mvnw test -pl nop-task/nop-task-core,nop-task/nop-task-ext,nop-task/nop-task-dao,nop-ai/nop-ai-agent -am -T 1C`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（本计划无 deferred 项。所有 Non-Goals 均为显式 rejected / out-of-scope improvement / successor plan candidate / optimization candidate，非「延期但需裁定」项。）

## Non-Blocking Follow-ups

- stacktrace（errorStack）完整持久化与截断策略（optimization candidate；本计划 best-effort 保留，不做专项优化）。
- step-state 全量字段持久化 / 完整历史 entity 模型（optimization candidate，plans 257/258/259/260 carry-over）。
- loadMainStepState（mainStep envelope intermediate-state restore）（successor plan candidate，plan 259 carry-over）。
- 异常精确子类恢复（exception 类型注册表 type → constructor 映射）（out-of-scope improvement）。

## Closure

Status Note: Plan 261 闭合了 nop-task exception 持久化的 transient lossy gap——cross-restart resume 重抛的 exception 现在保留 `.param(...)` 诊断属性与 cause chain（经新增 `errorBeanData` 列持久化完整 ErrorBean JSON），与 in-process 执行时诊断信息对齐。所有 Exit Criteria + Closure Gates 已逐条勾选并经验证成立；6× carry-over（plans 247/252/254/258/259/260）反复裁定为 optimization candidate 的最后一项遗留优化闭合。向后兼容（null errorBeanData 回退 errCode+errMsg）+ 序列化容错（超长/失败非致命跳过有日志）+ cause chain 限定在 nop-task-dao（不触及 kernel `NopRebuildException.rebuild`）均落地。
Completed: 2026-06-19

Closure Audit Evidence:

- Reviewer / Agent: explore subagent（独立 closure-audit session `ses_1234f5802ffelJBZy185lYtHwv`，fresh session 非 implementation 复用）
- Audit Session: `ses_1234f5802ffelJBZy185lYtHwv`
- Evidence:
  - 12 audit tasks 全 PASS（APPROVED），逐条对照 live code：
    - ORM 列存在（`nop-task.orm.xml`：NopTaskInstance errorBeanData propId=38 L238-239 + NopTaskStepInstance propId=54 L332-333，VARCHAR 4000 nullable）— PASS
    - 生成类 getter/setter（`_NopTaskStepInstance.getErrorBeanData():1702`/`setErrorBeanData:1710` + `_NopTaskInstance:1942/1950`，标准生成无手改）— PASS
    - step save 接线（`copyStepStateToEntity:317-327` 调 serializeErrorBeanData + 条件 setErrorBeanData）— PASS
    - task save 接线（`saveTaskState:122-132` 对称）— PASS
    - step load 接线（`toStepStateBean:274` loadException 优先 errorBeanData）— PASS
    - task load 接线（`toTaskStateBean:242` 对称）— PASS
    - cause 重构（`rebuildExceptionFromErrorBean:445-463` cause 经构造器传入非 initCause）— PASS
    - cause save 填充（`serializeErrorBeanData:374-393` + `buildCauseErrorBean:400-409` 递归 getCause）— PASS
    - 无静默跳过 #24（serializeErrorBeanData/loadException 各失败分支 LOG.warn 含异常对象，无空 catch）— PASS
    - 向后兼容（`loadException:419-435` null errorBeanData 回退 errCode+errMsg，无 params 无 cause）— PASS
    - 测试存在（`TestDaoTaskStateStoreErrorBeanRoundTrip` 5 tests @NopTestConfig localDb=true：step/task round-trip params+cause + 向后兼容 + 超长容错 + cross-restart E2E fresh store，断言 getParam + getCause）— PASS
    - 无 Protected Area 违规（kernel 方法 `NopRebuildException.rebuild` 未修改，git diff 确认 nop-kernel 无变更，cause 重构限定在 nop-task-dao）— PASS
  - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` 退出码 0（无未勾选项 + Closure Evidence 已写入）
  - Anti-Hollow 检查：`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-task/nop-task-dao --severity high` 退出码 0（Total findings: 0，无 high/critical 空壳实现）；端到端调用链追踪：fresh execute → step FAILED driver → saveStepState(errorBeanData JSON 序列化到 DB entity) → fresh DaoTaskStateStore loadStepState(反序列化 ErrorBean) → resume 重抛 exception(含 params + cause) 经 `TestDaoTaskStateStoreErrorBeanRoundTrip.crossRestartResume_rethrowsExceptionWithParamsAndCause` 真实 DB-backed 断言连通
  - Deferred 项分类检查：所有 Non-Goals（stacktrace 完整持久化 / 全量字段持久化 / loadMainStepState / fail() 变更 / 精确异常子类恢复）均为显式 rejected / out-of-scope improvement / successor / optimization candidate，无 in-scope live defect 被降级

Follow-up:

- stacktrace（errorStack）完整持久化与截断策略（optimization candidate；本计划 best-effort 保留，不做专项优化）
- step-state 全量字段持久化 / 完整历史 entity 模型（optimization candidate，plans 257/258/259/260 carry-over）
- loadMainStepState（mainStep envelope intermediate-state restore）（successor plan candidate，plan 259 carry-over）
- 异常精确子类恢复（exception 类型注册表 type → constructor 映射）（out-of-scope improvement）

## Follow-up handled by 266-nop-ai-agent-exception-subclass-and-cause-stack.md

异常精确子类恢复（exception 类型注册表，限定 nop-task-dao 模块、不增强 kernel `NopRebuildException.rebuild`）已由 plan 266 接管，bundled with cause-chain-per-level-stack-persistence。

- no remaining plan-owned work（本计划 in-scope 全部闭合）
