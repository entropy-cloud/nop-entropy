# 265 nop-task exception stacktrace 持久化（闭合 stacktrace/errorStack carry-over——新增 errorStack 列，cross-restart resume 保留原始 stack 诊断）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: stacktrace-errorstack-persistence (carry-over, L4, P3)

> Last Reviewed: 2026-06-19
> Source: carry-over from `ai-dev/plans/264-nop-ai-agent-step-state-full-field-roundtrip.md`（`## Non-Blocking Follow-ups`:156 登记的「stacktrace（errorStack）完整持久化与截断策略（optimization candidate，需新增 entity 列）」），原始登记见 `ai-dev/plans/261-nop-ai-agent-exception-transient-error-bean-persist.md`（Non-Goals:51 + Follow-up:165）。该项经 plans 247/252/254/258/259/260/261 共 7× 反复登记为 optimization candidate，plan 264 已交付 step-state 全量字段 round-trip 并将 stacktrace 显式留作本 follow-up。plan 264 已追加 `## Follow-up handled by 265-...` 向前追溯链接。
> Related: `261`（errorBeanData 列 + serializeErrorBeanData/loadException/rebuildExceptionFromErrorBean 基线）/`264`（step-state 全量字段 round-trip，本计划 source-plan，已 completed）

## Purpose

闭合被 7 份计划反复登记为 optimization candidate 的「stacktrace（errorStack）完整持久化与截断策略」carry-over。

cross-restart resume 重抛的 exception 目前只保留 errCode + errMsg + errorBeanData（params + cause chain，plan 261），**丢弃原始 stack**——`DaoTaskStateStore` 经 `buildErrorMessage(null, exp, false, false)` 取 ErrorBean 时 `includeStack=false`（ErrorMessageManager:239-240 仅在 `includeStack=true` 时 `error.setErrorStack(getStacktrace(e))`），故持久化的 errorBeanData JSON 不含 `errorStack` 字段；load 侧 `rebuildExceptionFromErrorBean` 也不恢复 stack。结果是 task/step FAILED 经 restart resume 后，诊断只剩错误码与描述，丢失定位原始失败位置的 stack 信息。

本计划新增 `errorStack` 列（nullable VARCHAR），save 侧写入截断后的原始 stack，load 侧在 rebuild 的 exception 上恢复该诊断，使 cross-restart resume 保留原始失败位置的可观测诊断。

## Current Baseline

基于本会话对 live repo 的核对（引用位置为审计可复核 live code path；确切行号以独立审计复核为准）：

- **stack 当前未被持久化（carry-over 仍成立）**：`DaoTaskStateStore` task 级 save（`nop-task/nop-task-dao/.../store/DaoTaskStateStore.java`，`:134` `ErrorBean errorBean = ErrorMessageManager.instance().buildErrorMessage(null, exp, false, false)`）与 step 级 save（`:379` 同构调用）取 ErrorBean 时 `includeStack=false`。`ErrorMessageManager.defaultBuildErrorMessage`（`nop-core/.../exceptions/ErrorMessageManager.java:217-243`）仅在 `includeStack=true` 时执行 `error.setErrorStack(getStacktrace(e))`（`:239-240`）。故序列化进 `errorBeanData` 的 ErrorBean 其 `errorStack` 恒为 null。
- **errorBeanData 列已存在且 round-trip（plan 261 基线，本计划不动其语义）**：`serializeErrorBeanData`（`:432-451`）clone ErrorBean + 递归补 cause chain（`buildCauseErrorBean:458-467`，cause 层 `includeStack=false`）→ JSON ≤ `ERROR_BEAN_DATA_MAX_LEN`(4000) 写 `entity.setErrorBeanData`，超长/失败返回 null（调用方仅写 errCode+errMsg，有日志）。load 侧 `loadException`（`:477-493`）优先从 errorBeanData 解析 ErrorBean → `rebuildExceptionFromErrorBean`（`:503-521`）重建含 errorCode/description/params/bizFatal/cause 的 NopException。
- **getStacktrace 已内置截断（ ErrorMessageManager:254-283）**：NopException 取 `getXplStack()` 截前 5 帧；其他 Throwable 遍历 `getStackTrace()` 跳过 `shouldIgnore` 后取前 5 帧，`join('\n')`。即 stack 体积有界（~数百字符）。
- **ErrorBean 已有 errorStack 字段（nop-api-core/.../ErrorBean.java:217-218 `errorStack(String)` 流式 setter）**，JSON 序列化/反序列化已支持——这意味着「无新增列、复用 errorBeanData」路径理论上存在（置 `includeStack=true` 即让 stack 流入既有 errorBeanData JSON）。**本计划不采用该路径**（见设计裁定 2），但仍记录其为已评估的替代方案。
- **carry-over 裁定（plans 247/252/254/258/259/260/261/264）**：均将 stacktrace 持久化分类为 optimization candidate（非 confirmed live defect、非 contract drift——当前 errCode+errMsg+errorBeanData 已驱动 resume 正确性与基本诊断），本计划据此接续。

## Goals

- **新增 `errorStack` 列（task + step 双 entity）**：`NopTaskInstance` 与 `NopTaskStepInstance` 各新增 nullable VARCHAR 列 `errorStack`，precision 与既有 errorBeanData(4000)/errMsg 对齐（具体 precision 与 propId 属 execution 裁定，受截断策略约束）。
- **save 侧写入截断 stack**：task 级（`:134` 邻近）与 step 级（`:379` 邻近）save 路径在 exception != null 时写入 `entity.setErrorStack(truncatedStack)`，stack 经截断策略保证不超过列 precision；**不得改变 errorBeanData（params+cause）既有持久化契约**（设计裁定 1）。
- **load 侧恢复 stack 诊断**：`rebuildExceptionFromErrorBean`（`:503-521`）/ `loadException` 路径使 cross-restart resume rebuild 出的 exception 暴露原始 stack 诊断（经测试可观测——如 rebuilt exception 携带原始 stack 信息，或 resume 路径输出原始 stack）。
- **非空壳可观测语义**：focused 单测 + cross-restart E2E（真实 `DaoTaskStateStore` localDb round-trip）证明 errorStack 经 DB 边界存活、loaded exception 的原始 stack 诊断可观测（Anti-Hollow #22/#23/#24）。
- **零回归**：plans 252-264 状态机 + reader + 终态 driver + resume + exception 持久化（errorBeanData params+cause 不变）+ task 级 lifecycle hook + mainStep envelope resume + step-state 全量字段 round-trip + nop-ai-agent 全绿。

## Non-Goals

- **复用 errorBeanData 列持久化 stack（无新增列路径）**：置 `includeStack=true` 可让 stack 流入既有 errorBeanData JSON，但会改 plan 261 刚交付的 `serializeErrorBeanData` 超长返回 null 逻辑——若 stack 推高 JSON 超 4000，当前逻辑会**整体丢弃** errorBeanData（含 params+cause），造成对一个刚完成功能的回归。Classification: rejected（为保护 plan 261 errorBeanData 契约、避免回归，选择正交新列；plan 261 已为「持久化需新列」设立 plan-first 先例）。
- **持久化完整（非 5-frame 截断）stack**：`getStacktrace` 已截前 5 帧；本计划沿用该截断语义，不引入「完整 stack dump」。Classification: out-of-scope improvement（体积/价值比低，5-frame XPL stack 已定位原始失败位置）。
- **精确 exception 子类 / Java Throwable.stackTrace 完整恢复**：cross-restart rebuild 的 NopException 其 Java `getStackTrace()` 本质是 resume 站点栈，非原始栈；本计划恢复的是 ErrorBean.errorStack 诊断字符串，不替换 Java stackTrace。Classification: rejected（设计如此，对称 plan 261）。
- **cause chain 各层 stack 持久化**：仅持久化顶层 exception stack；cause 层 stack 不单独持久化（plan 261 cause 层 `includeStack=false` 保持）。Classification: out-of-scope improvement。
- **errorStack 列的独立查询/UI 消费**：本计划只保证持久化 + resume 诊断可观测，不交付按 errorStack 的 SQL 查询或前端展示。Classification: out-of-scope improvement。
- **其余 plan 264 Non-Goals**：新增 ORM 列持久化 parentRunId/partitionIndex/bizObj*/extType/extState、stateBean 跨 step 中间变量持久化、独立 history/审计表、composite saveState 频率校准——均不在本计划 scope（仍为 out-of-scope improvement）。

## Scope

### In Scope

- **ORM 变更**（Phase 1，Protected Area plan-first——本计划即 plan-first 产物）：`NopTaskInstance` + `NopTaskStepInstance` 新增 nullable VARCHAR 列 `errorStack`。
- **save 侧写入**（Phase 1）：task 级 + step 级 save 路径写 `entity.setErrorStack(truncatedStack)`，截断策略保证不超列 precision 且不动 errorBeanData 契约。
- **load 侧恢复**（Phase 1）：rebuild/load 路径使 loaded exception 暴露原始 stack 诊断。
- **非空壳验证**（Phase 1 单测 + Phase 2 cross-restart E2E）：真实 `DaoTaskStateStore` localDb round-trip 证明 errorStack 经 DB 边界存活 + loaded 诊断可观测。
- **零回归**（Phase 2）：plans 252-264 + nop-ai-agent 全绿，含 errorBeanData params+cause 契约不变。

### Out Of Scope

- 见 Non-Goals（复用 errorBeanData 无列路径 / 完整非截断 stack / 精确子类与 Java stackTrace 恢复 / cause 层 stack / errorStack 独立查询消费 / 其余 plan 264 Non-Goals 均为显式 rejected / out-of-scope）。

### 设计裁定（Pre-Adjudicated）

1. **正交新列，不动 errorBeanData 契约（硬约束，可机械执行）**：新增 `errorStack` 列与 errorBeanData 正交。save 侧**必须**用一个与「喂给 `serializeErrorBeanData` 的 errorBean」**分离的** stack 来源写入新列——即额外独立取一次含 stack 的 ErrorBean（见设计裁定 3 的 access path），从其 `.getErrorStack()` 取截断后的 stack 字符串写 `entity.setErrorStack(...)`；**既有 `buildErrorMessage(null, exp, false, false)`（includeStack=false）取得的 errorBean 保持原样继续喂 `serializeErrorBeanData`，不得改成 includeStack=true**（否则 stack 会随该 errorBean 流入 errorBeanData JSON，破坏本裁定 + 回归 plan 261）。load 侧 `loadException`/`rebuildExceptionFromErrorBean` 的 errorBeanData 处理逻辑亦不动；errorStack 经由新列读回（设计裁定 4）。任何「为塞 stack 而改 errorBeanData 超长逻辑」的方案均归 Non-Goal（设计裁定 2）。截断策略只作用于 errorStack 列本身。回归守卫：focused 单测须断言「写入 errorStack 后，errorBeanData JSON 不含 errorStack 字段、且 params+cause 行为不变」。
2. **拒绝「复用 errorBeanData 无列路径」（已评估）**：置 `includeStack=true` 会让 stack 流入既有 errorBeanData JSON，但 plan 261 `serializeErrorBeanData` 在 JSON > 4000 时**返回 null 整体丢弃** errorBeanData——叠加 stack 后边界场景（深 cause chain + 长 params + stack）会触发整体丢弃，回归 params+cause 持久化。该路径被拒绝；记录于此以备审计。
3. **截断策略沿用 getStacktrace 5-frame 语义；access path 经 public build 而非 protected getStacktrace**：stack 体积有界（~数百字符），列 precision 选 4000（与 errorBeanData/step errMsg 对齐），即便 stack 接近上限也不会触及 errorBeanData。**注意 `ErrorMessageManager.getStacktrace` 为 `protected` 且位于 `io.nop.core.exceptions`，`DaoTaskStateStore`（`io.nop.task.dao.store`）不可直接调用**；access path 必须经 public 入口间接获取 stack——即对同一 exception 额外调用一次 `ErrorMessageManager.instance().defaultBuildErrorMessage(null, exp, true)`（public，`:217`，includeStack=true，内部调 getStacktrace）或 `buildErrorMessage(..., true, ...)`，从返回 ErrorBean 的 `.getErrorStack()` 取 stack 字符串，再按需截断。**此额外 build 的 ErrorBean 只用于取 errorStack，绝不传入 serializeErrorBeanData**（见设计裁定 1）。若 stack 字符串仍超列 precision（极端边界），截断到 precision 并保证不抛异常（非致命，有日志——对称既有 stateBeanData/errorBeanData 非致命跳过模式，Minimum Rules #24）。具体截断算法属 execution 裁定。
4. **load 侧恢复形式为 execution 裁定**：loaded exception 暴露原始 stack 诊断的具体形式（如 rebuilt NopException 经 param 携带原始 stack 字符串、或 resume 路径日志输出、或专属 accessor）属 execution 裁定；本计划只要求「经 focused 测试可观测」这一结果面（Minimum Rules #10：计划只描述 what 与验证，不描述 how）。
5. **task + step 双 entity 对称新增列**：对称 plan 261（errorBeanData 同时加到 source ORM `nop-task/model/nop-task.orm.xml` 的 `NopTaskInstance`（propId=38）+ `NopTaskStepInstance`（propId=54）），两 entity 均新增 errorStack，保证 task 级与 step 级 FAILED 诊断对称。**Source ORM 模型文件为 `nop-task/model/nop-task.orm.xml`**（plan 261 在此新增 errorBeanData）；`nop-task/nop-task-dao/.../orm/_app.orm.xml` 为**生成物**，`_vfs/.../orm/app.orm.xml` 为运行时挂载件——二者均**不可手改**（AGENTS.md Hard Stop），实施时只改 source，再经 `./mvnw install`（或对应 codegen）重新生成 `_app.orm.xml` / `_NopTask*Instance`。
6. **诚实分类**：本项经 8 份计划裁定为 optimization candidate（非 live defect、非 contract drift）。本计划不把它伪装成 defect 修复；Exit Criteria 证明的是「cross-restart resume 保留原始 stack 诊断」这一优化结果，而非「修复了导致 resume 错误的 bug」。

## Execution Plan

### Phase 1 - ORM 新列 + save/load stack 持久化 + 截断策略 + 单元测试

Status: completed
Targets: `nop-task/model/nop-task.orm.xml`（**source ORM 模型**，新增 `errorStack` 列；plan 261 在此新增 `NopTaskInstance` propId=38 / `NopTaskStepInstance` propId=54 的 errorBeanData。`_app.orm.xml` 为生成物、`_vfs/.../app.orm.xml` 为运行时挂载件，二者不可手改——Protected Area plan-first 即本计划）、`DaoTaskStateStore.java`（`copyStepStateToEntity` step 级 save 邻近 `:377-383` + task 级 save `:129-142` / `loadException:477-493` + `rebuildExceptionFromErrorBean:503-521`）、`nop-task-ext/src/test/`（`@NopTestConfig(localDb=true)`，镜像 `TestDaoTaskStateStoreErrorBeanRoundTrip` / `TestDaoTaskStateStoreFullFieldRoundTrip` 范式）

- Item Types: `Follow-up`（carry-over optimization——8× 登记 optimization candidate，非 confirmed live defect）

- [x] source ORM 模型 `nop-task/model/nop-task.orm.xml` 为 `NopTaskInstance` + `NopTaskStepInstance` 新增 nullable VARCHAR 列 `errorStack`（precision 与 errorBeanData 对齐）；经 `./mvnw install`（或对应 codegen）重新生成 `_app.orm.xml` / `_NopTask*Instance`（不手改生成物）
- [x] task 级 save（`:134` 邻近）+ step 级 save（`:377` 邻近）：exception != null 时，**额外独立**取一次含 stack 的 ErrorBean（经 `defaultBuildErrorMessage(null, exp, true)` 等 public 入口，见设计裁定 3），从 `.getErrorStack()` 取 stack → 截断 ≤ 列 precision → `entity.setErrorStack(truncatedStack)`；**既有 includeStack=false 的 errorBean 不得改动、继续喂 `serializeErrorBeanData`**（设计裁定 1/3，非致命）
- [x] `rebuildExceptionFromErrorBean` / load 路径：使 loaded exception 暴露原始 stack 诊断（设计裁定 4，形式属 execution 裁定）
- [x] 新增 focused 单测（`nop-task-ext`，`@NopTestConfig(localDb=true)`，真实 `DaoTaskStateStore`）：构造含非空 stack 的 FAILED step/task state → saveStepState/saveTaskState → fresh load → 断言 errorStack 经 entity↔DB 边界 round-trip 等值 + loaded exception 原始 stack 诊断可观测；**回归守卫**：断言 errorBeanData（params+cause）行为不变且其 JSON 不含 errorStack 字段（设计裁定 1）
- [x] 截断边界单测：构造超长 stack（或深 cause）→ 断言 errorStack 被截断到列 precision 且不抛异常（非致命），errorBeanData 不受影响

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `_app.orm.xml` / `_NopTaskInstance` / `_NopTaskStepInstance` 含 `errorStack` 列（重新生成，diff 可复核）
- [x] task 级 + step 级 save 路径写 `entity.setErrorStack(truncatedStack)`（读 store 代码可见）
- [x] load 路径使 loaded exception 原始 stack 诊断可观测（读 store 代码可见，非空方法体）
- [x] **接线验证**（#23）：单测断言 errorStack 经真实 entity↔DB 边界 round-trip 生效（fresh load 读回值 == save 写入值，截断后），非仅方法存在
- [x] **无静默跳过**（#24）：新增 save/load 为真实读写（非空 `{}` / 非 TODO / 非 `continue`）；截断失败有日志（非静默吞掉，对称既有 stateBeanData/errorBeanData 非致命跳过模式）
- [x] **新功能必有测试**（#25）：errorStack round-trip + 截断边界 + loaded 诊断可观测 + errorBeanData 契约回归守卫 各有 focused 断言
- [x] `./mvnw compile -pl nop-task/nop-task-dao,nop-task/nop-task-ext -am` 通过
- [x] `./mvnw test -pl nop-task/nop-task-dao,nop-task/nop-task-ext -am` 既有 + 新增测试全绿
- [x] owner-doc 裁定：roadmap（`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`）carry-over 登记状态更新；其余 owner-docs `No owner-doc update required`（理由：内部 DB 持久化诊断列，task/step public 执行契约不变）
- [x] `ai-dev/logs/` 对应日期条目已更新（Phase 2 一起更新）

### Phase 2 - cross-restart `DaoTaskStateStore` DB round-trip E2E + 零回归 + 文档收口

Status: completed
Targets: `nop-task/nop-task-ext/src/test/`（`@NopTestConfig(localDb=true)` 真实 `DaoTaskStateStore` cross-restart round-trip）、`ai-dev/plans/264-nop-ai-agent-step-state-full-field-roundtrip.md`（Follow-up handled-by 链接，已写入）、`ai-dev/logs/`、`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`（carry-over 状态）

- Item Types: `Proof`（errorStack 经 DB 序列化边界生效 + resume 诊断可观测）+ `Follow-up`（零回归 + 文档收口 + carry-over 关闭）

- [x] 新增 cross-restart E2E（真实 `DaoTaskStateStore`，`@NopTestConfig(localDb=true)`，非 in-memory 引用）：fresh execute 含 FAILED（非空 stack）的 step/task 至 save（写 errorStack + DB）→ 模拟中断 → fresh store `loadTaskState`/`loadStepState`/`loadMainStepState` → 断言 errorStack 经 DB 边界存活（save 值 == fresh load 值，截断后）+ loaded exception 原始 stack 诊断可观测
- [x] Anti-Hollow 断言：errorStack save 写入非 null → DB → fresh load 读回等值（非「写而丢失」伪装）；loaded 诊断可观测（非「写而不读」伪装）；errorBeanData params+cause 不受影响（设计裁定 1 回归守卫）
- [x] 零回归：`./mvnw test -pl nop-task/nop-task-core,nop-task/nop-task-ext,nop-task/nop-task-dao,nop-ai/nop-ai-agent -am -T 1C` 全绿（plans 252-264 状态机 + reader + 终态 driver + resume + exception 持久化 + task lifecycle hook + mainStep envelope resume + step-state 全量字段 round-trip + decorator/bizFatal/reliability + nop-ai-agent）
- [x] 收口 carry-over：确认 plan 264 的 `## Follow-up handled by 265-...` 链接已写入（本计划起草时已追加）；roadmap 中 stacktrace carry-over 登记状态更新为已闭合

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] **端到端验证**（#22）：从 fresh execute（写 errorStack + DB）→ DB → fresh load（读回 + 诊断可观测）完整路径连通（真实 `DaoTaskStateStore`，非 in-memory 引用）
- [x] **Anti-Hollow Check**（#22/#23/#24）：E2E 经真实 `DaoTaskStateStore` DB round-trip 证明 errorStack 经持久化边界存活、loaded 诊断可观测、errorBeanData 契约不变；无空方法体/静默跳过/no-op 作为「正常实现」
- [x] 新增功能各有 focused/E2E 测试覆盖（#25）：errorStack round-trip + 截断边界 + loaded 诊断可观测 + errorBeanData 回归守卫
- [x] 零回归：plans 252-264 + nop-ai-agent 全绿（含 errorBeanData params+cause 契约 + 既有 round-trip 字段集 + resume 行为不变）
- [x] owner-doc 裁定落地：roadmap carry-over 状态更新；其余 `No owner-doc update required`
- [x] plan 264 Follow-up handled-by 双向链接已写入；`ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] `errorStack` 列在 `NopTaskInstance` + `NopTaskStepInstance` 双 entity 成立（ORM 模型 + 重新生成 entity，契约 + 实现）
- [x] task 级 + step 级 save 写入截断 stack；load 路径恢复原始 stack 诊断（双entity对称）
- [x] cross-restart `DaoTaskStateStore` DB round-trip E2E 证明 errorStack 经持久化边界存活 + loaded 诊断可观测（#22 端到端 + #23 接线验证）
- [x] **errorBeanData（params+cause）契约零回归**（设计裁定 1）：errorBeanData 持久化行为与 plan 261 一致，stack 持久化路径正交、不触及 serializeErrorBeanData/loadException 的 errorBeanData 处理
- [x] 必要 focused verification 已完成（单测 errorStack round-trip + 截断边界 + loaded 诊断 + errorBeanData 回归守卫 + cross-restart E2E + Anti-Hollow 断言）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（复用 errorBeanData 无列路径 / 完整非截断 stack / 精确子类恢复 / cause 层 stack / errorStack 独立查询消费 / 其余 plan 264 Non-Goals 均为显式 Non-Goals）
- [x] 受影响 owner docs 已同步到 live baseline（roadmap carry-over 状态），或明确写明 `No owner-doc update required`
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）errorStack round-trip 在 runtime 经真实 DB 路径生效（非仅类型/方法存在），（b）loaded 诊断可观测，（c）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-task/nop-task-dao,nop-task/nop-task-ext -am`
- [x] `./mvnw test -pl nop-task/nop-task-core,nop-task/nop-task-ext,nop-task/nop-task-dao,nop-ai/nop-ai-agent -am -T 1C`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（本计划无 deferred 项。复用 errorBeanData 无列路径、完整非截断 stack、精确子类与 Java stackTrace 恢复、cause 层 stack、errorStack 独立查询/UI 消费，以及 plan 264 其余 Non-Goals，均为显式 rejected / out-of-scope improvement——非「延期但需裁定」项。）

## Non-Blocking Follow-ups

- 其余 plan 264 Non-Blocking Follow-ups（新增 ORM 列持久化 parentRunId/partitionIndex/bizObj*/extType/extState、stateBean 跨 step 中间变量持久化、独立 history/审计表、composite saveState 频率校准）仍为 out-of-scope improvement，不在本计划 scope。
- errorStack 列的独立查询 / 前端展示（out-of-scope improvement）。
- cause chain 各层 stack 持久化（out-of-scope improvement）。

## Follow-up handled by 266-nop-ai-agent-exception-subclass-and-cause-stack.md

cause chain 各层 stack 持久化（nested cause 的截断 stack 经 errorBeanData 通路持久化并在 resume 时恢复）已由 plan 266 接管，bundled with exception-precise-subclass-restoration（plan 261 carry-over）。

## Closure

Status Note: 闭合被 8 份计划反复登记为 optimization candidate 的「stacktrace（errorStack）完整持久化与截断策略」carry-over。新增正交 `errorStack` 列持久化截断后的原始 stack，save 侧经独立 includeStack=true ErrorBean 取 stack（既有 includeStack=false errorBeanData 契约不动），load 侧在 rebuild 的 exception 上恢复该诊断，使 cross-restart resume 重抛的 exception 保留定位原始失败位置的 stack 信息。经真实 `DaoTaskStateStore` DB round-trip E2E（fresh store 实例）证明 errorStack 经持久化边界存活 + loaded 诊断可观测 + errorBeanData（params+cause）契约零回归。
Completed: 2026-06-19

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure auditor subagent（fresh session，`ses_122936f4fffeMI8JgKEbfIE2lS`，explore agent，非实现阶段同一 session）
- Evidence（每条 Exit Criterion + Closure Gate 验证结果，均经 live code path 复核）：
  - ORM source model：PASS — `nop-task/model/nop-task.orm.xml` `NopTaskInstance` propId=39 + `NopTaskStepInstance` propId=55 各含 nullable VARCHAR 4000 `errorStack` 列。
  - 重新生成 entity：PASS — `_NopTaskInstance.java`(PROP_NAME_errorStack=39/getter/setter) + `_NopTaskStepInstance.java`(propId=55) + `_app.orm.xml`（生成物，格式统一未手改）均含 errorStack。
  - save 侧：PASS — `DaoTaskStateStore` task 级（`:143-158`）+ step 级 `copyStepStateToEntity`（`:396-409`）均 `entity.setErrorStack(extractErrorStack(exp))`；**回归守卫成立**：errorBeanData 仍经 `buildErrorMessage(null, exp, false, false)`（includeStack=false）取 bean 喂 `serializeErrorBeanData`，stack 经独立 `extractErrorStack`（`defaultBuildErrorMessage(null, exp, true)` includeStack=true，`extractErrorStack:460-483`）取得，该独立 bean 绝不传入 serializeErrorBeanData；截断 ≤ 4000（`ERROR_STACK_MAX_LEN`）。
  - load 侧：PASS — `loadException` 4 参签名（`errorStack`），`toTaskStateBean`/`toStepStateBean` 均传 `entity.getErrorStack()`，非空经 `exp.param(PARAM_ERROR_STACK, ...)` 附加；`rebuildExceptionFromErrorBean`/errorBeanData 解析逻辑不动。
  - 无静默跳过 #24：PASS — `extractErrorStack` 真实逻辑（非空体/非 TODO/非 continue），capture 失败 `LOG.warn(..., e)` 非静默吞掉。
  - 接线验证 #23：PASS — 单测断言 errorStack 经真实 entity↔DB 边界 round-trip（fresh load 读回值 == save 写入值，截断后）。
  - 端到端 #22：PASS — `TestDaoTaskStateStoreErrorStackRoundTrip.crossRestartResume_*` 经 fresh `DaoTaskStateStore` 实例 save→DB→fresh load→exception param 完整路径。
  - 新功能测试 #25：PASS — 5 tests 覆盖 errorStack round-trip + 截断边界 + loaded 诊断 + errorBeanData 回归守卫 + cross-restart E2E + legacy 向后兼容。
  - 零回归：PASS — `./mvnw test -pl nop-task/nop-task-core,nop-task/nop-task-ext,nop-task/nop-task-dao,nop-ai/nop-ai-agent -am -T 1C` BUILD SUCCESS（nop-task-ext 92 含新增 5 / nop-ai-agent 2714 / 0 failures / 0 errors；既有 ErrorBean/FullField round-trip 测试零回归）。
  - owner-doc：PASS — `nop-ai-agent-roadmap.md` 新增 `stacktrace-errorstack-persistence` ✅ 行；`ai-dev/logs/2026/06-19.md` plan-265 条目已写入；plan 264 `## Follow-up handled by 265` 双向链接存在。
  - Deferred 诚实性：PASS — 无 in-scope item 被降级；Non-Goals（复用 errorBeanData 无列路径 / 完整非截断 stack / 精确子类 / cause 层 stack / errorStack 独立查询）均显式 rejected/out-of-scope。
- `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict`：退出码 0（无未勾选项 + Closure Evidence 已写入）。
- Anti-Hollow 检查：runtime-wired 端到端（save `entity.setErrorStack` → DB（updateEntityDirectly）→ fresh load（getEntityById/findStepEntity）→ `loadException` 读 `entity.getErrorStack()` → `exp.param`）非仅类型/方法存在；无空方法体/静默跳过/no-op 作为正常实现。`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-task --severity high` 退出码 0（Total=0 findings）。

Follow-up:

- no remaining plan-owned work。Non-Blocking Follow-ups（新增 ORM 列 parentRunId/partitionIndex/bizObj*/extType/extState、stateBean 跨 step 中间变量持久化、独立 history/审计表、composite saveState 频率、errorStack 独立查询/前端展示、cause 层 stack 持久化）仍为 out-of-scope improvement，不在本计划 scope。
