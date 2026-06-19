# 266 nop-ai-agent — Exception 精确子类恢复 + cause chain 各层 stack 持久化

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L4-8
> **Last Reviewed**: 2026-06-19
> **Completed**: 2026-06-19
> **Source**: plans 261/265 carry-over（261 Non-Goals:52 + 265 Non-Blocking Follow-ups:147）；roadmap §2.2/§5 P3
> **Related**: 261-nop-ai-agent-exception-transient-error-bean-persist, 265-nop-ai-agent-stacktrace-persistence

## Bundled Items

- **exception-precise-subclass-restoration**（PRIMARY）— source-plan: `ai-dev/plans/261-nop-ai-agent-exception-transient-error-bean-persist.md`（Non-Goals:52，Classification: out-of-scope improvement）。cross-restart resume 重构的 exception 总是 generic `NopException`，丢失原始精确子类，使 `instanceof` 分类逻辑静默失败；~85-105 prod lines。
- **cause-chain-per-level-stack-persistence**（BUNDLE sibling）— source-plan: `ai-dev/plans/265-nop-ai-agent-stacktrace-persistence.md`（Non-Blocking Follow-ups:147）。同一文件（`DaoTaskStateStore`）、同一 rebuild 路径、同一 rationale（resume-exception fidelity）。~45 prod lines；bundled 以满足 ≥100-line workload threshold（合并 ~130-150 prod lines）。

## Purpose

收口两份已完成计划反复登记为 out-of-scope improvement 的 carry-over：cross-restart resume 重构的 exception 应保留 (a) 原始精确子类（使 `instanceof` 分类逻辑成立）与 (b) cause chain 各层的 stack 诊断信息（不止 top-level）。两项同处 `DaoTaskStateStore` rebuild 路径、共用同一持久化/重构数据通路（`errorBeanData`/ErrorBean），合并为一个 plan。

## Current Baseline

（已有事实，来自 plans 261/265 Closure + roadmap §4 ✅）

- plan 261 已交付 `errorBeanData` 列（VARCHAR 4000，存储完整 ErrorBean JSON 含 errorCode/description/params/cause chain）于 `NopTaskInstance` + `NopTaskStepInstance`，save/load 路径在 `DaoTaskStateStore` 已 wired；cause chain 经内部递归重构（`initCause`）round-trip 可恢复。
- plan 265 已交付正交 `errorStack` 列（VARCHAR 4000）持久化 top-level exception 的截断 stack（`extractErrorStack` 用独立 includeStack=true ErrorBean 取 stack），load 时经 `exp.param(PARAM_ERROR_STACK, ...)` 附加。
- **剩余 gap（本 plan 收口）**：
  - `DaoTaskStateStore` rebuild 路径（`rebuildExceptionFromErrorBean`）总是构造 generic `NopException`（plan 261 设计裁定 6 显式拒绝引入注册表，留作 out-of-scope）。诚实现状：task 级 cross-restart resume 经 `TaskImpl.synthesizeResumeException` 按 status code 合成 generic exception（不依赖精确子类），现存 `instanceof` 子类检查发生在 in-process step 执行路径；故精确子类丢失的影响是**诊断保真度 / 消费方一致性**（store rebuild 出的 exception 不保留原始精确子类，任何对 loaded exception 做子类判断/诊断的消费方无法区分），非阻断当前 resume 流程的 live defect（与 plans 261/265 诚实分类一致）。
  - cause chain 各层 exception 的 stack 未持久化（plan 265 仅 top-level；`buildCauseErrorBean` 用 includeStack=false；Non-Blocking Follow-ups:147）。resume 后只能定位 top-level 失败位置，nested cause 的 stack 丢失。

## Goals

- store rebuild 路径（`loadStepState`/`loadTaskState` → 重构 exception）重构出的 exception 保留**原始精确子类**（如 `NopTaskCancelledException`/`NopAiAgentException` 等 NopException 子类），使对 **loaded/rebuilt exception** 做 `instanceof` 判断/诊断的消费方成立。（注：task 级 resume 入口 `synthesizeResumeException` 按 status 合成、不经此路径；本 Goal 的可观测面是 store rebuild 输出，非 task 级 resumed exception。）
- cause chain 各层 exception 的 stack 经持久化边界存活并在 resume 时恢复（截断以适配 4000），使 resumed exception 的 cause chain 各层定位信息可观测。
- 零回归：plans 252-265 状态机 + reader + 终态持久化 + resume 短路 + decorator/reliability + nop-task-core/ext/dao + nop-ai-agent 全绿；既有 `errorBeanData`（params+cause）+ `errorStack`（top-level）契约不动。

## Non-Goals

- **不增强 `nop-api-core` 的 `NopRebuildException.rebuild`**（kernel public static API，全仓库多处调用 `nop-orm-rpc`/`nop-http-api`/`nop-retry-engine`/`nop-ai-core`/`nop-core`；plan 261 设计裁定 3 选项 a 已裁定为跨模块公共 API 变更，需独立 plan-first）。本 plan 的 registry/重构逻辑限定在 `nop-task-dao` 模块内。
- **不改 ORM/列结构**。复用既有 `errorBeanData`（ErrorBean JSON）+ `errorStack`（top-level 截断 stack）列；精确子类 FQCN 与各 cause 层 stack 复用 ErrorBean 标准字段或 params 通路编码，不新增列。
- **不引入跨模块通用 exception 注册表，不新增模块依赖**。registry 仅服务于 nop-task rebuild 路径，以 **FQCN 字符串键 + 反射构造**注册 nop-task / nop-ai-agent NopException 子类（无编译期依赖——`nop-task-dao` 的 pom 不依赖 `nop-ai-agent`，反射避免颠覆分层，反向依赖通用 task DAO 层→具体 AI 应用层架构上 rejected）；未注册 / 缺类（`ClassNotFoundException`）/ 历史 FQCN → 安全回退 generic `NopException` 重构（向后兼容，无新列/无 kernel 改动）。
- 其余 plan 264/265 carry-over（新增 ORM 列 parentRunId/partitionIndex/bizObj*/extType/extState、stateBean 跨 step 中间变量、独立 history/审计表、composite saveState 频率、cross-step evalScope、step/task afterLoad 重构迁移）仍 out-of-scope improvement，不在本计划 scope。

## Scope

### In Scope

- **Exception 精确子类恢复（Phase 1）**：在 `nop-task-dao` 内提供 exception 类型注册表（FQCN → 工厂/构造映射），save 侧在 `errorBeanData` 序列化前捕获原始 exception FQCN（复用 ErrorBean 标准字段或既有 params 通路，不新增列/不增强 kernel），rebuild 路径（`rebuildExceptionFromErrorBean`）consult registry 构造精确子类；未注册 FQCN 安全回退 generic `NopException`（向后兼容历史行 + 未注册类型）。
- **cause chain 各层 stack 持久化（Phase 2）**：save 侧为 cause chain 各层 exception 取截断 stack（复用既有 `extractErrorStack` 截断策略，bounded per-cause 预算），经 budget-aware 序列化随 ErrorBean 持久化（total 超 4000 时优先剥离 cause 层 stack，绝不回归 params+cause，设计裁定 5）；load/rebuild 路径为各 cause 层恢复 stack 诊断（`param` 附加或等价可观测方式）。
- **round-trip + cross-restart E2E + 零回归测试（Phase 2）**：精确子类 + cause 各层 stack 经 save→DB→fresh load round-trip 可观测；cross-restart resume 重构的 exception 经 `instanceof` 断言为精确子类、cause 各层 stack 可观测；既有 `errorBeanData`（params+cause）+ `errorStack`（top-level）契约零回归。

### Out Of Scope

- 见 Non-Goals（kernel `NopRebuildException.rebuild` 增强 / ORM 新列 / 跨模块通用 registry / 其余 plan 264/265 carry-over）。

### 设计裁定（Pre-Adjudicated）

1. **registry 限定 nop-task-dao 模块，经 FQCN 字符串键 + 反射构造（无编译期依赖）**，不增强 kernel `NopRebuildException.rebuild`。理由见 Non-Goals + plan 261 设计裁定 3。**架构约束**：`nop-task-dao` 的 pom 依赖仅 nop-api-core/nop-orm/nop-task-core，**不依赖 `nop-ai-agent`**（反向依赖会颠覆分层），故 registry 不能编译期引用 `NopAiAgentException` 等 nop-ai-agent 子类。采用 **FQCN 字符串键 → `Class.forName` → 反射构造**（注册集合以 FQCN 字符串 + 工厂注册，覆盖 nop-task/nop-ai-agent NopException 子类）；部署缺类（`ClassNotFoundException`）时该 FQCN 回退 generic `NopException` 重构 + 日志（#24）。构造精确子类时须保留 errorCode + params + cause chain（与现有 generic 重构的诊断信息对齐）。
2. **不新增 ORM 列**。精确子类 FQCN 与各 cause 层 stack 经既有 `errorBeanData`（ErrorBean JSON）通路编码（按 ErrorBean schema 标准字段或 params 命名空间裁定）；top-level `errorStack` 列不动（仍只承载 top-level 截断 stack，plan 265 契约零回归）。
3. **向后兼容硬约束**：未注册 FQCN / 历史 `errorBeanData`（无 FQCN 字段）→ 回退现有 generic `NopException` 重构，行为与 plan 261 一致；各 cause 层 stack 缺失时仅降级诊断（不抛、不静默吞掉，按 #24 有日志），不影响 cause chain 本身的恢复。
4. **测试用真实 DB-backed `DaoTaskStateStore`**（参照 plan 261/265 既有 `TestDaoTaskStateStore*RoundTrip` 模式 + `@NopTestConfig(localDb=true)`），禁止用 in-memory snapshot store（不经 entity 序列化，无法暴露 `errorBeanData` 序列化 gap）。
5. **cause 层 stack 经 budget-aware 编码，保证 plan 261 errorBeanData（params+cause）契约零回归**。cause 层 stack 编入各 cause ErrorBean 标准 `errorStack` 字段（截断到 bounded per-cause 预算），序列化必须 **budget-aware**：若 total JSON 将超 4000 cap，**优先剥离/截断 cause 层 stack**（best-effort 诊断），绝不因 cause stack 挤占而丢弃 params+cause。现存 `serializeErrorBeanData` 的 total-discard fallback 仅作为非 cause-stack 溢出（如 params 本身巨大）的最后手段保留，行为与 plan 261/265 一致。plan 265 契约（top-level bean 仍 includeStack=false；top-level stack 仍在独立 `errorStack` 列）零回归。Exit Criteria 须含守卫测试：构造 deep cause chain + 各层 stack 使 total 趋近/超过 4000，断言 params+cause 仍 round-trip 存活（cause 层 stack 降级剥离，不致命）。

## Execution Plan

### Phase 1 - Exception 精确子类恢复（registry + FQCN capture + rebuild consult）

Status: completed
Targets: `nop-task/nop-task-dao/src/main/java/io/nop/task/dao/store/DaoTaskStateStore.java`（rebuild 路径 `rebuildExceptionFromErrorBean` + save 路径 FQCN 捕获 + 新增 nop-task-dao 内 exception 类型注册表）、注册 nop-task/nop-ai-agent 自有 NopException 子类、`ai-dev/logs/`

- Item Types: `Follow-up`（诚实分类：plans 261/265 及 8 份前置 plan 均裁定为 out-of-scope improvement / optimization candidate，非 live defect——task 级 cross-restart resume 经 `TaskImpl.synthesizeResumeException` 按 status code 合成 generic exception，现存 `instanceof` 子类检查发生在 in-process step 执行路径而非 resumed exception 上。本项为诊断保真度/一致性改进：使 store rebuild 出的 exception 保留精确子类，供对 loaded exception 做子类判断/诊断的消费方使用）

- [x] 在 `nop-task-dao` 内新增 exception 类型注册表（**FQCN 字符串键 + 反射构造**，无编译期依赖，设计裁定 1），注册 nop-task/nop-ai-agent NopException 子类（注册集合经 live repo grep 确认，覆盖仍存在于 live repo 的子类）
- [x] save 侧（step `copyStepStateToEntity` + task `saveTaskState` 的 ErrorBean 序列化处）捕获原始 exception FQCN，复用 ErrorBean 标准字段或既有 params 通路编码（不新增列），随 `errorBeanData` 持久化
- [x] rebuild 路径（`rebuildExceptionFromErrorBean`）consult registry：FQCN 命中 → 构造精确子类（保留 errorCode + params + cause chain）；未命中 / 无 FQCN（历史行）→ 回退现有 generic `NopException` 重构
- [x] 未注册类型 / `ClassNotFoundException`（部署缺类）/ FQCN 缺失 回退路径有日志（#24 不静默吞掉）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] exception 类型注册表存在于 `nop-task-dao`，注册集合经 live repo grep 可复核（注册哪些子类、注册点位置可观测）
- [x] save 后 entity 的 `errorBeanData` 含原始 exception FQCN（经 ErrorBean 标准字段或 params 通路，round-trip 后可读回），不新增 ORM 列（`nop-task/model/nop-task.orm.xml` 无新列定义）
- [x] rebuild 经 registry 命中精确子类：cross-restart resume 重构的 exception 经 `instanceof` 断言为已注册的精确子类（具体子类 + 断言位置可观测）
- [x] 向后兼容：未注册 FQCN / 历史 `errorBeanData`（无 FQCN）→ 回退 generic `NopException` 重构，行为与 plan 261 一致（既有 round-trip 测试零回归）
- [x] **无静默跳过**（#24）：未注册类型回退路径 / FQCN 捕获失败有日志，不静默吞掉
- [x] `./mvnw compile -pl nop-task/nop-task-dao,nop-task/nop-task-core -am` 通过
- [x] owner-doc 裁定：`No owner-doc update required`（内部 DB 序列化诊断改进，step/task public 执行契约不变，经 registry/FQCN 编码细节属实现层面）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - cause chain 各层 stack 持久化 + round-trip/E2E 测试 + 零回归

Status: completed
Targets: `nop-task/nop-task-dao/src/main/java/io/nop/task/dao/store/DaoTaskStateStore.java`（`buildCauseErrorBean` 各层 stack 捕获 + rebuild 各 cause 层 stack 恢复）、`nop-task/nop-task-ext/src/test/`（round-trip / cross-restart E2E 用真实 DB-backed `DaoTaskStateStore` + `@NopTestConfig(localDb=true)`，禁止 in-memory snapshot store）、`ai-dev/logs/`

- Item Types: `Follow-up`（诚实分类：plan 265 Non-Blocking Follow-ups:147 裁定为 out-of-scope improvement，非 live defect——plan 265 已交付 top-level stack，本项扩展诊断保真度至 cause chain 各层）

- [x] save 侧为 cause chain 各层 exception 取截断 stack（复用既有 `extractErrorStack` 截断策略，适配 4000），随 ErrorBean cause chain 持久化（不新增列；top-level `errorStack` 列契约不动）
- [x] load/rebuild 路径为各 cause 层恢复 stack 诊断（`param` 附加或等价可观测方式），与 plan 265 top-level PARAM_ERROR_STACK 模式对齐
- [x] round-trip 单测：精确子类 + cause 各层 stack 经 save→DB→fresh load 可观测（`instanceof` 断言 + cause 各层 stack 断言）
- [x] cross-restart resume E2E：fresh `DaoTaskStateStore` 实例 save→DB→fresh load→重构 exception，`instanceof` 为精确子类、cause 各层 stack 可观测
- [x] 既有 `errorBeanData`（params+cause）+ `errorStack`（top-level）契约零回归守卫测试
- [x] 截断边界测试（cause 层 stack 超 4000 截断，不致命）

Exit Criteria:

- [x] cause chain 各层 exception 的 stack 经 round-trip 在 resumed exception 上可观测（具体 cause 层 + stack 断言位置可观测）
- [x] top-level `errorStack` 列契约零回归（plan 265 既有 errorStack round-trip 测试通过）
- [x] `errorBeanData`（params+cause）契约零回归（plan 261 既有 round-trip 测试通过）
- [x] **端到端验证**（#22）：fresh `DaoTaskStateStore` save→DB→fresh load→重构 exception 完整路径，断言精确子类 + cause 各层 stack
- [x] **接线验证**（#23）：registry 在 rebuild 路径运行时确实被 consult（非仅类型存在）——经 `instanceof` 断言或计数器/mock verify
- [x] **无静默跳过**（#24）：各 cause 层 stack 捕获失败有日志（非空 catch），不静默吞掉
- [x] **新功能测试**（#25）：新增精确子类恢复 + cause 层 stack 的 focused 测试已列出（覆盖 `instanceof` / cause 层 stack / 向后兼容回退 / 截断 / 零回归守卫）
- [x] owner-doc 裁定落地（与 Phase 1 一致）：`No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] cross-restart resume 重构的 exception 保留原始精确子类（`instanceof` 成立）——Bundled Item 1 收口
- [x] cause chain 各层 stack 经持久化边界存活并在 resume 时可观测——Bundled Item 2 收口
- [x] kernel `NopRebuildException.rebuild` 未被改动（Non-Goals 守卫，跨模块公共 API 零回归）
- [x] ORM/列结构未变（无新列，复用 `errorBeanData`/`errorStack`）——Non-Goals 守卫
- [x] 向后兼容：未注册 FQCN / 历史行回退 generic `NopException`，行为与 plan 261 一致
- [x] `errorBeanData`（params+cause）+ `errorStack`（top-level）契约零回归（plans 261/265 既有测试通过）
- [x] 必要 focused verification 已完成（精确子类 `instanceof` + cause 层 stack + 向后兼容回退 + 截断 + 零回归守卫 + cross-restart E2E + Anti-Hollow）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 受影响 owner docs 已同步到 live baseline，或明确写明 `No owner-doc update required`
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）registry 在 rebuild 路径运行时确实被 consult，（b）cause 层 stack 经真实 DB 路径 round-trip，（c）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-task/nop-task-dao,nop-task/nop-task-core,nop-task/nop-task-ext -am`
- [x] `./mvnw test -pl nop-task/nop-task-core,nop-task/nop-task-ext,nop-task/nop-task-dao,nop-ai/nop-ai-agent -am -T 1C`
- [x] checkstyle / 代码规范检查通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-task --severity high` 退出码 0

## Deferred But Adjudicated

（本计划无 deferred 项。其余 plan 264/265 carry-over 均为显式 out-of-scope improvement，见 Non-Goals。）

## Non-Blocking Follow-ups

- 其余 plan 264/265 Non-Goals（新增 ORM 列 parentRunId/partitionIndex/bizObj*/extType/extState、stateBean 跨 step 中间变量、独立 history/审计表、composite saveState 频率、cross-step evalScope、step/task afterLoad 重构迁移、errorStack 独立查询/前端展示）仍为 out-of-scope improvement。
- 跨模块通用 exception 类型注册表（若未来 kernel `NopRebuildException.rebuild` 增强需独立 plan-first 评估）。

## Closure

Status Note: plan 266 收口 plan 261 §Non-Goals:52「精确异常子类恢复」+ plan 265 §Non-Blocking Follow-ups:147「cause 层 stack」两项 carry-over。store rebuild 路径经 `TaskExceptionRegistry`（FQCN 字符串键 + 反射构造，无编译期依赖）恢复精确子类，cause 各层 stack 经 budget-aware 序列化编入 errorBeanData 各 cause ErrorBean。无新增 ORM 列，无 kernel 改动，既有 errorBeanData + errorStack 契约零回归。20 新增 focused 测试（11 registry 单元 + 9 DB-backed round-trip/E2E）+ 四模块全绿。
Completed: 2026-06-19

Closure Audit Evidence:

- Reviewer / Agent: independent closure-audit subagent (fresh session, task id `ses_122500386ffeCGyK98XV43O6nb`, `explore` subagent_type, separate from implementation session)
- Audit Session: closure-audit subagent session
- Evidence:
  - Exit Criterion Phase 1「registry 存在 + 注册集合可复核」: PASS — `TaskExceptionRegistry.java` 存在于 `nop-task-dao/src/main/java/io/nop/task/dao/store/`，`getRegisteredFqcns()` 返回 NopTaskFailException/NopTaskCancelledException（compile-time）+ NopAiAgentException/NopAiException（reflective FQCN）。`TestTaskExceptionRegistry.registeredFqcns_containsAllKnownSubclasses` PASS。
  - Exit Criterion Phase 1「errorBeanData 含 FQCN」: PASS — `captureExceptionClass` 经 reserved param `__exceptionClass` 编入 ErrorBean.params，`TestDaoTaskStateStoreExceptionSubclassAndCauseStackRoundTrip.stepRoundTrip_preservesPreciseSubclass` 断言 errorBeanData JSON contains FQCN。无新 ORM 列（`nop-task.orm.xml` 本计划无改动——errorStack 列为 plan 265 pre-existing）。
  - Exit Criterion Phase 1「rebuild 经 registry 命中精确子类」: PASS — `rebuildExceptionFromErrorBean` consult `exceptionRegistry.create(fqcn,...)`，`stepRoundTrip_preservesPreciseSubclass` 断言 `loadedExp instanceof NopTaskFailException` 成立（plan 266 core delta，pre-266 为泛型 NopException）。
  - Exit Criterion Phase 1「向后兼容」: PASS — `stepLoad_legacyRowWithoutFqcn_fallsBackToGenericNopException` 断言历史行无 FQCN → `assertFalse(loadedExp instanceof NopTaskFailException)`。既有 plan 261 ErrorBean round-trip 测试 5/5 PASS 零回归。
  - Exit Criterion Phase 2「cause 各层 stack round-trip 可观测」: PASS — `stepRoundTrip_preservesCauseLevelStack` 断言 cause exception `getParam("errorStack")` 非 null + 含原始 frame。`stepRoundTrip_deepCauseChain_eachLevelStackObservable` 断言 2 层 cause 各自 stack 独立可观测。
  - Exit Criterion Phase 2「top-level errorStack 契约零回归」: PASS — plan 265 既有 `TestDaoTaskStateStoreErrorStackRoundTrip` 5/5 PASS（top-level bean includeStack=false 不变，errorStack 列承载 top-level stack）。
  - Exit Criterion Phase 2「errorBeanData params+cause 契约零回归」: PASS — plan 261 既有 `TestDaoTaskStateStoreErrorBeanRoundTrip` 5/5 PASS。`stepRoundTrip_errorBeanAndErrorStackContractsUnchanged` 守卫测试 PASS。
  - **端到端验证**（#22）: PASS — `crossRestartResume_preciseSubclassAndCauseStackSurviveDbBoundary` 经 fresh `DaoTaskStateStore` 实例 save→DB→fresh load→重构 exception，断言精确子类 instanceof + cause stack 经 DB 边界存活。
  - **接线验证**（#23）: PASS — cross-restart E2E 中 `instanceof NopTaskFailException` 成立证明 registry 在 rebuild 路径运行时确实被 consult（非仅类型存在）。`TestTaskExceptionRegistry.resolveReflective_onClasspathClass_returnsInstance` 直接验证反射解析路径。
  - **无静默跳过**（#24）: PASS — `TaskExceptionRegistry.create` 的 ClassNotFound/no-ctor/create-failed 路径均有 `LOG.warn`；`extractErrorStack` 失败有日志；`serializeErrorBeanData` budget-aware 剥离有日志。`stepLoad_unregisteredFallback_isObservableNotSilent` 验证回退路径非静默。
  - **新功能测试**（#25）: PASS — 20 新增 focused 测试：registry 注册集合/compile-time 命中/反射命中/ClassNotFound 回退/未注册回退/null 回退/缓存/防御/param 泄漏（11）+ 精确子类 round-trip/task/向后兼容/cause stack/深层 cause/契约守卫/budget-aware/cross-restart E2E/无静默（9）。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0（re-run after closure evidence filled）
  - Anti-Hollow 检查结果：端到端调用链 `saveStepState → copyStepStateToEntity → serializeErrorBeanData → captureExceptionClass + buildCauseErrorBean → errorBeanData JSON → DB → loadStepState → toStepStateBean → loadException → rebuildExceptionFromErrorBean → exceptionRegistry.create → instanceof 精确子类` 完整连通；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-task --severity high` 退出码为 0（Critical=0/High=0/Medium=0/Low=0）
  - Deferred 项分类检查：本计划无 deferred 项；plan 264/265 carry-over（新增 ORM 列等）仍为显式 Non-Goals out-of-scope improvement，非 in-scope live defect 被降级
  - kernel `NopRebuildException.rebuild` 守卫：`git diff HEAD -- NopRebuildException.java` 空（未改动）
  - ORM 守卫：本计划无 `nop-task.orm.xml` 改动（errorStack 列为 plan 265 pre-existing 未提交改动）

Follow-up:

- 跨模块通用 exception 类型注册表（若未来 kernel `NopRebuildException.rebuild` 增强需独立 plan-first 评估）仍为 Non-Goals。
- 其余 plan 264/265 carry-over（新增 ORM 列 parentRunId/partitionIndex/bizObj*/extType/extState、stateBean 跨 step 中间变量、独立 history/审计表、composite saveState 频率、cross-step evalScope、step/task afterLoad 重构迁移、errorStack 独立查询/前端展示）仍为 out-of-scope improvement。
