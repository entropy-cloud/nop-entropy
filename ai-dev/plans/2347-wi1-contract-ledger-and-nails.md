# 2347 WI1 声明面-消费方契约清单与 fail-fast 钉子基线

> Plan Status: completed
> Last Reviewed: 2026-10-09
> Source: `ai-dev/backlog/nop-stream-xdsl-full-coverage-roadmap.md`（WI1 行，M1 契约基线）
> Related: plan 2346（WI0 裁定门）；`docs-for-ai/03-modules/nop-stream-user-guide.md`（清单落点）

## Purpose

把 xdsl-full-coverage roadmap 的声明面契约固化为 owner doc 清单（XDSL 章节新增「声明面支持矩阵」）+ 每类 fail-fast 一个构建期钉子测试，作为后续 WI5–WI16 逐项放行的唯一底账（放行三步法第一步的对照基准）。

## Current Baseline（2026-10-09 live 核对）

- **roadmap v3 fail-fast 表已部分失效**（2026-10-09 multi-engine/sql-enhancement 合并）：`<sideOutput>` 已接线（`AdvancedTransforms.java:896-934` buildSideOutput + SideOutputTapOperator + tag edge 配对校验）；`sliding-event-time`/`session-event-time` 参数化已交付（`AdvancedTransforms.java:320-352`）；`accumulationMode` 仅 `ACCUMULATING_AND_RETRACTING` 仍拒绝（`AdvancedTransforms.java:255-274`）；`maxDelay`（:221-224）与 `emit` UPDATE/CLOSE（:241-244）已接线；`allowedLateness` 双处已放行（:209-216）。
- **仍 fail-fast 的面（live 锚点）**：strategy/节点级 `triggerId`（`AdvancedTransforms.java:257-262`/:283-288）；五注册表 + coders（`StreamModelDslBuilder.java:436-478` failFastOnUnsupportedRegistries）；onStart/onEnd/onError 回调（:463-467）；边级流控四属性（validateEdgeDeclarations，:876 起）；source `params`/`maxParallelism`/`outputType`/`consistencyCapability`（:1164-1196）；sink `params`/`maxParallelism`/`inputType`/`consistencyCapability`（:1269-1295）；`<window>` parallelism 虚拟元素拒绝（`AdvancedTransforms.java:186-194`）；`<custom>` 的 `<params>`/`<source>` 体（buildCustom，custom source body 已有钉）；HASH 边语义族（validateEdgeDeclarations）；`<sql>` 顶层唯一约束（expandSqlModel）。
- **既有钉子覆盖**：streams/sideInputs/environments/coders 注册表 + schemas 消费（`TestStreamModelDslBuilderFailFast`）；边流控四属性（`TestStreamModelEdgeContract` 参数化）；triggerId 双处 + allowedLateness/accumulationMode/maxDelay/emit 组合（`TestStreamCheckpointAndWindowContract`、`TestWindowAccumulationModeDeclaration`、`TestWindowEmitDeclaration`、`TestWindowMaxDelayWiring`）；custom source body（`TestAdvancedTransforms:357`）；source params/consistencyCapability + sink maxParallelism（`TestStreamFlowAuditFixes`）；`<window>` parallelism（`TestStreamCheckpointAndWindowContract:191`）。
- **钉子缺口（本 plan 补齐）**：`<requirements>` 注册表、`<checkpointParticipants>` 注册表、onStart/onEnd/onError 回调（3 项零钉子）；source `maxParallelism`/`outputType`、sink `params`/`inputType`/`consistencyCapability`（5 项端点属性零钉子）。
- **清单落点**：`docs-for-ai/03-modules/nop-stream-user-guide.md` §XDSL 声明式编排（:141-233）无支持矩阵小节；§触发语义映射表 :285 起是触发族对照非声明面台账。

## Goals

- user-guide XDSL 章节新增「### 声明面支持矩阵（WI1 契约基线）」：已实现面表 + fail-fast 面表（声明形态/live 锚点/钉子测试/放行归属 WI），作为声明面变化的单一事实源（roadmap Cross-Cutting #7）。
- 补齐 9 个钉子测试缺口（3 注册表/回调 + 5 端点属性 + 1 未知 windowFnId），全部落在既有测试类的既有 helper 模式上。
- roadmap Baseline 表加一行指针注记（live 台账指向 user-guide 矩阵，避免双状态面维护）。

## Non-Goals

- 不放行任何 fail-fast 面（WI5–WI16 的职责）。
- 不重写 roadmap Baseline 表内容（只加指针注记；v4 版本注记已声明行号漂移交 WI1 重钉——本 plan 以 user-guide 矩阵的新鲜锚点履行「重钉」，roadmap 表以指针收口）。
- 不新增错误码、不改 builder 行为（零产品行为变更，纯 Proof + doc）。

## Scope

### In Scope

- `docs-for-ai/03-modules/nop-stream-user-guide.md`：新增声明面支持矩阵小节（两张表）。
- `nop-stream-flow` 测试：`TestStreamModelDslBuilderFailFast` 增 3 钉（requirements/checkpointParticipants/生命周期回调）、`TestStreamFlowAuditFixes` 增 5 钉（端点属性缺额）。
- `ai-dev/backlog/nop-stream-xdsl-full-coverage-roadmap.md`：Baseline 节指针注记一行。

### Out Of Scope

- builder/xdef 代码改动；新错误码；连接器文档（connectors.md 能力矩阵不在本 WI 范围）。

## Execution Plan

### Phase 1 - 钉子测试补齐（9 个）

Status: completed
Targets: `nop-stream/nop-stream-flow/src/test/java/io/nop/stream/flow/builder/`

- Item Types: `Proof`

- [x] `TestStreamModelDslBuilderFailFast` 增 4 钉（复用 `assertTopLevelRegistryFailsFast` helper）：
  - `requirementsRegistryFailsFast`：`<requirements><requirement value="EFFECTIVELY_ONCE"/></requirements>`（枚举常量已核对 `StreamRequirement.java`，无 EXACTLY_ONCE）
  - `checkpointParticipantsRegistryFailsFast`：`<checkpointParticipants><participant value="op1"/></checkpointParticipants>`
  - `lifecycleCallbacksFailsFast`：`<onStart>streamCtx;</onStart>`——xpl-fn 体是**函数体语句**（在 xdef 签名参数 `streamCtx` 作用域内编译），不是函数字面量；lambda 形态会被 `XLangCompileTool` 的 `ERR_XPL_FN_BODY_IS_FUNCTION` 守卫在 parse 阶段拒绝（到不了目标 fail-fast），故用平凡表达式体；断言 build() 抛 `nop.err.stream.not-implemented` 且消息含 lifecycle callbacks 描述
  - `unknownWindowFnIdFailsFast`：`<windowingStrategies><strategy strategyId="s1" windowFnId="no-such-window"/></windowingStrategies>` + 引用它的 window transform——白名单/参数化/bean 三路皆不命中时 `ERR_STREAM_REF_UNKNOWN`（roadmap :46 行登记归 WI7 的面，全测试目录原无钉子，本钉补齐第 9 个缺口；断言错误码 `nop.err.stream.ref-unknown` 而非 not-implemented）
- [x] `TestStreamModelDslBuilderFailFast` 增 5 端点属性钉（复用 `parseInline` + transforms XML；outputType/inputType 为**子元素**形态非属性——`<source id="s1" bean="x"><outputType>string</outputType></source>`，generic-type 体，编码时以 xdef 实测为准）：
  - source `maxParallelism="4"` 属性
  - source `<outputType>string</outputType>` 子元素
  - sink `<params><param name="k" value="v"/></params>` 体（param 形态以 xdef 为准）
  - sink `<inputType>string</inputType>` 子元素
  - sink `consistencyCapability="IDEMPOTENT"` 属性（默认 AT_LEAST_ONCE，取非默认枚举常量，编码时核对 `SinkConsistencyCapability` 实际值域）
  - 上述钉子若放置本类不顺（该类现有 helper 均为 registry 口径），允许移入 `TestAdvancedTransforms`（已有 parseInline 等价 helper）——以「单文件内聚」为准择一，不新开测试类

Exit Criteria:

- [x] 9 个新钉子全部存在且通过（`./mvnw test -pl nop-stream/nop-stream-flow -am -Dtest=TestStreamModelDslBuilderFailFast,TestStreamFlowAuditFixes` 绿）
- [x] 无静默跳过：每个钉子断言的是 typed fail-fast（错误码+token），非异常存在性
- [x] No owner-doc update required for code（行为无变化）；`ai-dev/logs/` 条目已更新

### Phase 2 - 声明面支持矩阵落 owner doc

Status: completed
Targets: `docs-for-ai/03-modules/nop-stream-user-guide.md`

- Item Types: `Proof`

- [x] XDSL 章节末尾（§实例锚点之后、§连接器使用指引之前）新增「### 声明面支持矩阵（WI1 契约基线）」：
  - 表 A 已实现面：窗口（tumbling/sliding/session 参数化 + duration/slide + legacy 白名单 + bean 优先）、allowedLateness/maxDelay/accumulationMode(ACCUMULATING)/emit 两档、aggregate 目录、等值 join（含 leftTimeout/rightTimeout）、CEP、sideOutput tap+tag 边、schemas、sql 顶层、checkpoint 配置族、Delta——每行含声明形态与代码锚点（类名+方法名，不写易漂移的裸行号）
  - 表 B fail-fast 面：triggerId 双处（WI5）、ACCUMULATING_AND_RETRACTING（WI5）、`<sideInputs>` 注册表含 broadcast（WI11）、五注册表（streams/environments/requirements/checkpointParticipants/coders，WI12）、生命周期回调（WI13）、边流控四属性（WI14）、端点 8 属性（WI15）、custom params/source 体（WI15）、白名单外 windowFnId（WI7）、window parallelism（保持拒绝，R9 静态诊断）、HASH 边 live 拒绝子面（HASH 无 keyExpr / 非 HASH 边带 keyExpr / REBALANCE 与 BROADCAST 无消费方——HASH+keyExpr 进 keyBy 已是合法路径）、sql 顶层唯一约束（WI16，WI0(e) 维持）——每行含锚点、钉子测试类名、放行归属 WI
  - 矩阵头部声明「本矩阵是声明面变化的单一事实源；放行即删对应 fail-fast 钉子并改放行断言（放行三步法）」
- [x] roadmap Baseline 节「全部为构建期 typed fail-fast…」段末追加一行指针：「2026-10-09 起 live 台账以 `docs-for-ai/03-modules/nop-stream-user-guide.md` 声明面支持矩阵为准（行号已随 multi-engine/sql-enhancement 合并漂移；sideOutput/sliding/session/ACCUMULATING/maxDelay/emit 已放行，见矩阵表 A）。」

Exit Criteria:

- [x] 矩阵两表齐备，每个锚点/测试类名在仓库真实存在（抽验）
- [x] 表 B 的放行归属与 roadmap 各 WI 行 deps/描述一致（sideInputs 单列归 WI11，五注册表归 WI12；HASH 行按 live 拒绝子面表述，不照抄 roadmap v3 旧行文）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 条目已更新

### Phase 3 - 回归与收口

Status: completed
Targets: `nop-stream/nop-stream-flow`

- Item Types: `Proof`

- [x] `./mvnw test -pl nop-stream/nop-stream-flow -am` 全绿
- [x] 独立 closure audit

Exit Criteria:

- [x] 模块全量测试通过
- [x] closure audit APPROVE 且证据入 plan

## Closure Gates

- [x] 9 个钉子缺口全部补齐且通过
- [x] user-guide 声明面支持矩阵落地（表 A + 表 B + 单一事实源声明）
- [x] roadmap Baseline 指针注记已加
- [x] 无产品行为变更（diff 仅测试与文档）
- [x] 受影响 owner docs 已同步（user-guide 矩阵）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `./mvnw test -pl nop-stream/nop-stream-flow -am` 通过
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2347-wi1-contract-ledger-and-nails.md --strict` 退出码 0
- [x] roadmap Work Item Status 的 WI1 checkbox 已勾选（audit 通过后）

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: 9 个 fail-fast 钉子缺口全部补齐（3 注册表/回调 + 1 未知 windowFnId + 5 端点属性），user-guide 声明面支持矩阵两表落地（表 A 13 行 / 表 B 12 行），roadmap Baseline 指针注记移交单一事实源。零产品行为变更（diff 仅测试 + 文档）。独立 closure audit APPROVE（0 Blocker / 0 Major / 3 Minor + 1 Nit，Minor 均为收口流程预待办已随本收口补齐，Nit 属并发会话遗留垃圾文件不入本任务）。
Completed: 2026-10-09

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_dc5c2a79-839b-4655-b0d0-0b69f69a8cf3（fresh session，未参与实现）
- Audit Session: agent_dc5c2a79-839b-4655-b0d0-0b69f69a8cf3
- Evidence:
  - Phase 1：三类钉子 28/28 绿（audit 独立复跑 `-Dtest=...FailFast,...AuditFixes,...WindowAssignerBranches` → Tests run: 28, Failures: 0）；钉子质量抽查 PASS（typed 断言错误码+token、lifecycle 函数体形态、单字段不混叠）；builder 抛出与钉子断言逐一对齐（failFastOnUnsupportedRegistries:436 / failFastOnUnsupportedSourceConfig:1164 / failFastOnUnsupportedSinkConfig:1269 / resolveWindowAssigner:363）
  - Phase 2：矩阵两表齐备，抽验锚点全部真实（validateSideOutputDeclarations:595、firstDeclaredFlowControlAttr:1069、buildCustom:789、expandSqlModel:295 等）；放行归属与 roadmap WI5/WI7/WI11/WI12/WI13/WI14/WI15/WI16 逐行一致；roadmap Baseline 指针注记落地（:54）
  - Phase 3：flow 模块全量 219/219 绿（audit 独立复跑）；diff 范围核实零 src/main 变更
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（0 errors；28 warnings 全属并发会话未跟踪文件）
  - `node ai-dev/tools/check-plan-checklist.mjs` 退出码 0
  - Anti-Hollow：钉子即「已知坏样例必须被拒」正控自测——每钉断言 typed 错误码而非异常存在性；矩阵锚点/测试类名经 audit 逐一核实存在
  - Deferred 项分类检查：无 deferred 项；执行偏差（端点钉落 TestStreamFlowAuditFixes）已披露且符合 plan 择一规则

Follow-up:

- no remaining plan-owned work
