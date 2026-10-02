# 21 WI11 无窗口持续 GROUP BY

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: `ai-dev/backlog/nop-stream-sql-roadmap.md`（WI11 行、D1=(a) 重排表）、`ai-dev/design/nop-stream/sql-subset-and-semantics.md` §1
> Related: `ai-dev/plans/nop-stream-sql/14-wi9-aggregate-eval.md`
> Owner: 仓库 owner（2026-10-02 执行指令委托）

## Purpose

按 D1=(a) 裁定分支实现无窗口持续 GROUP BY：`<keyBy>` + `<reduce>`（last-value-wins 终值语义，**非 append-only 非 retract**）的 SQL GROUP BY 映射证据。三个分支各有分支专属断言（D1 取 (a) 时另须断言 §八 5/§八 7 的适用面）——D1=(a) 已生效，(b)/(c) 分支为对账性确认（不实现 (b) 的 upsert sink 路线）。

## Current Baseline

- `<reduce>` 已全链可用（roadmap §3.1）：`<keyBy>` → `<reduce>`（bean 或 xpl body），`KeyedStream.reduce` 逐条 emit 当前归约值（`StreamReduceOperator.java:87-106` last-value-wins）——D1=(a) 直接采用。TestAdvancedPipelineE2E 已有 keyBy→reduce→sink E2E 先例。
- **D1=(a) 的交付面是「语义标注」而非新算子**：模型/文档显式标注 last-value-wins；文档侧 sql-subset-and-semantics §1 已落。本 WI 交付=代码侧语义标注载体 + 分支专属断言 + SQL GROUP BY 映射证据（EQL 文本语义 → keyBy+reduce 拓扑）。
- WI9 交付：`StreamSqlExprCompiler` 可编译标量投影；聚合经 `StreamSqlAggregation.create` 产出 AggregateFunction——reduce 的 xpl body 形态已是既有通路（XplReduceFunction）。
- §八 5：manifest durable 前 sink transaction 不得 commit——D1=(a) 分支不触发（无 upsert sink 路线）；§八 7：无重放/无严格提交能力不得声明 STRICT_EXACTLY_ONCE——D1=(a) 分支适用面=语义声明不得标 STRICT（reduce 输出面非严格一次性）。
- TestAdvancedPipelineE2E 断言精确（`[1, 2, 2, 4, 6]`）——即 last-value-wins 的既有证据。

## Goals

- **语义标注载体**：`StreamReduceModel`（或其 builder 消费点）加 last-value-wins 语义标注——形态裁定：`AdvancedTransforms.buildReduce` javadoc + `StreamReduceOperator` javadoc 双处显式标注「D1=(a) last-value-wins 终值语义：非 append-only 非 retract」，模型 XML 声明面**不加属性**（语义为模型内在，非用户可选）。
- **分支专属断言**（TestContinuousGroupBy，nop-stream-flow）：
  1. (a) 分支：keyBy→reduce（bean 与 xpl 两形态）E2E 输出断言=逐条 emit 当前归约值（中间值可见——last-value-wins 证据，非仅终值）；断言输出流非 append-only 的可观测形态（同 key 后续 emit 覆盖前值语义=输出含中间归约值序列）。
  2. (b)/(c) 对账确认：`WindowOperator` 的 `ACCUMULATING_AND_RETRACTING` spec-only fail-fast 既有门禁仍绿（D10 既有测试）；`SinkConsistencyCapability.UPSERT_BY_KEY` 全仓零调用方（grep 断言或核对既有 grep 结果）——(b) 路线未被引入。
  3. §八 7 适用面：reduce 拓扑的语义声明断言——`<reduce>` 拓扑在 `enableCheckpointing` 下不声明 STRICT_EXACTLY_ONCE 能力（reduce 输出面为 best-effort/last-value-wins，文档化）。
- **SQL GROUP BY 映射证据**：EQL GROUP BY 语义文本 → keyBy(keyExpr)+reduce 拓扑的映射说明落 join-operator.md 同级（或 sql-subset-and-semantics §4b GROUP BY 行锚点更新）——映射文档化而非编译器实现（WI17 承接）。

## Non-Goals

- 不实现 retract/upsert 路线（D1=(b) 未采纳）；不改 StreamRecord 契约；不做窗口聚合（WI8c/WI10 已有）；不实现 SQL 编译器（WI17）。

## Scope

### In Scope

- `nop-stream/nop-stream-flow`：buildReduce 与 StreamReduceOperator 语义标注（javadoc）
- `nop-stream/nop-stream-flow`：TestContinuousGroupBy（(a) 分支双形态 E2E + (b)/(c) 对账断言 + §八 7 声明断言）
- 文档：sql-subset-and-semantics.md §4b GROUP BY 行锚点更新；当日日志

### Out Of Scope

- WI13 join；WI17 编译器；任何内核行为变更。

## Execution Plan

### Phase 1 - 语义标注与分支断言

Status: completed
Targets: `nop-stream/nop-stream-flow`

- Item Types: `Feature`

- [x] buildReduce 与 StreamReduceOperator 语义标注（D1=(a) last-value-wins：非 append-only 非 retract；执行期形态裁定：StreamReduceOperator 用 `//` 文件级注释、buildReduce 用 javadoc——语义为模型内在非 API 契约）
- [x] TestContinuousGroupBy 3 用例：(a) 分支 E2E 中间值可见断言 [1,2,2,4,6]（bean 形态——xpl 形态由既有 dispatch 单测承载）；(b) 对账——UPSERT_BY_KEY 声明外零调用方非空转扫描断言（repo-root 解析 + 声明计数=1 防空转）；§八 7——reduce 拓扑 guarantee 非 STRICT 断言（CheckpointConfig 声明面）
- [x] sql-subset-and-semantics.md §4b GROUP BY 行锚点更新（keyBy+reduce 映射 + 语义标注引用）
- [x] ai-dev/logs/ 当日条目更新

Exit Criteria:

- [x] **端到端验证**：(a) 分支 E2E 绿，中间归约值序列断言精确（last-value-wins 可观测证据）
- [x] (b)/(c) 对账断言绿（未引入 retract 路线）
- [x] §八 7 断言落地（reduce 拓扑 guarantee 非 STRICT——CheckpointConfig 声明面）
- [x] `./mvnw test -pl nop-stream/nop-stream-flow` 绿（零退化）
- [x] ai-dev/logs/ 当日条目已更新

### Phase 2 - 收口

Status: completed
Targets: plan 与 roadmap

- Item Types: `Proof`

- [x] 独立子 agent closure audit（不同 task_id）：三轮轨迹——首轮 FAIL（B-1 扫描恒空转、B-2 §八 7 无可执行断言、B-3 plan/日志未同步）、二轮（B-1/B-2 关闭，B-3 日志半项 + J-1 javadoc 失实）、三轮（B-3 落盘但 WI7 标题行尾重复一行机械残留）、四轮（机械修复后确认 CLOSED，最终 PASS）；证据落 ai-dev/audits/nop-stream-sql/wi11-closure-audit.md
- [x] audit 通过后 roadmap WI11 `todo` → `done`（括注单层一对）；`parseRoadmapMarkdown` 复核 31 工作项 + 7 里程碑、21 done、无静默丢弃
- [x] Plan Status → `completed`；check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0

Exit Criteria:

- [x] 独立 audit 证据落档两处
- [x] roadmap WI11 = done + 解析器 31 + 7 复核通过
- [x] check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0

## Closure Gates

- [x] D1=(a) 分支 E2E 绿且中间值序列断言精确
- [x] 语义标注两处落档（buildReduce + StreamReduceOperator），无 append-only 表述
- [x] (b)/(c) 对账断言绿（UPSERT_BY_KEY 声明外零调用非空转扫描 + D10 门禁既有承载）
- [x] §八 7 断言落地（guarantee 非 STRICT）
- [x] 独立子 agent closure-audit 已完成并记录证据（不同 task_id）
- [x] `./mvnw test -pl nop-stream/nop-stream-flow` 绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/21-wi11-continuous-groupby.md --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

### retract / upsert 路线（D1=(b)）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: D1 owner 已裁 (a)，(b) 需 UPSERT_BY_KEY 首次接线（范围显著变大）；若日后改判须先改 sql-subset-and-semantics §1
- Successor Required: `no`
- Successor Path: sql-subset-and-semantics §1 改判后新立项

## Closure

Status Note: 无窗口持续 GROUP BY 按 D1=(a) 落地——语义标注两处（last-value-wins / 非 append-only / 非 retract），分支断言 3 用例（中间值序列判别、UPSERT_BY_KEY 非空转扫描、§八 7 guarantee 非 STRICT）。独立 closure audit 三轮轨迹（首轮 FAIL 三项 → 修正 → 最终 PASS），日志落盘两轮失实的根因（replace 锚点失配）已用精确锚点 + git show 自证消除。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，三轮独立实读实跑）
- Audit Session: 证据落档 ai-dev/audits/nop-stream-sql/wi11-closure-audit.md（三轮轨迹，最终 PASS）
- Evidence:
  - 语义标注两处三要素实读核验；TestContinuousGroupBy 3/3 隔离与 flow 全量 156 绿
  - B-1 修复自证（扫描声明计数=1 非空转）；B-2 探针实证（AT_LEAST_ONCE 来源 + STRICT 声明可捕获）
  - B-3 git show --stat 自证日志文件在 commit 内
  - 门禁：doc-links strict 0、roadmap 31+7
- `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/21-wi11-continuous-groupby.md --strict` 退出码 0

Follow-up:

- no remaining plan-owned work
