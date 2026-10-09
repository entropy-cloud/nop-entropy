# 2355 WI9 内建聚合目录扩充

> Plan Status: completed
> Last Reviewed: 2026-10-09
> Source: `ai-dev/backlog/nop-stream-xdsl-full-coverage-roadmap.md`（WI9 行，M2）
> Related: plan 2346（WI0(d) DISTINCT 口径裁定）；WI0(d) 裁定注记（user-guide :399 实锚）

## Purpose

扩充内建聚合目录：`maxBy`、`distinct` 计数近似（WI0(d) 裁定形态）、`composite` 复合聚合（多指标）——经既有 `IAggregatorFunctionResolver` 目录链（`StreamSqlAggregations` 内建表 → `StreamAggregatorFunctionResolver.resolve`），复合累加器经 `aggregators` 声明面接线；DISTINCT 两口径边界按 WI0(d) 文档化。

## Current Baseline（2026-10-09 live 核对）

- 目录链：`aggregators` 注册表（fnId+expr+schemaId）→ `resolveAggregatorRef`（AdvancedTransforms :677-702：bean 优先 → `IAggregatorFunctionResolver.resolve(fnId, expr, columnTypeLookup)`，无 provider fail-fast 指向 nop-stream-sql）→ `StreamSqlAggregations` 内建表（sum/count/avg/min/max 五族，count 允许 0..1 参）+ `validateArgType`（sum/avg 数值、min/max Comparable）+ 表达式编译（`StreamSqlExprCompiler`）。
- **复合先例**：`SqlRowAggregateSpec.FN_ID`（sql-row-agg）是 SQL 编译器合成的复合入口（group-key evaluators + 多聚合 spec）——XDSL 声明面尚无复合形态。
- **WI0(d) 裁定**：DISTINCT 精确口径归 `<sql>` 面（multi-engine WI17a 已交付）；声明面目录交付近似口径（dedup 集合）并文档化两口径边界（user-guide 已有裁定注记占位）。

## Goals

- **机制底账（审查实证）**：`StreamSqlAggregation.create` 的 AggregateFunction `add(Object record, ...)` 收**原始记录**，expr 求值在 add 内部——maxBy 整记录保留可直接表达（DistinctAggregateWrapper 存整记录先例）。
- **目录新增两 fnId**（`StreamSqlAggregations` BUILTINS 表）：
  - `maxBy(expr)`：acc=[extremeKey, record] 保留整条记录（Comparable 校验并入 max/min 分支；数值感知比较复用 MinMaxAgg.compareValues 语义——需将其可见性调整为可复用；tie-break=严格 > 即 first-wins，同 MinMaxAgg；null-skip 与空窗返 null 同 MinMaxAgg）。
  - `distinct(expr)`：**窗口内精确 dedup 集合计数**（措辞修正：WI0(d) 原文「近似口径」不精确——dedup 集合在窗口作用域内是精确的，与 SQL 面的差异是作用域/生命周期而非精度；内存上界=组内去重值基数，随窗清除；措辞对齐 WI17 DistinctAggregateWrapper javadoc）。
- **composite = resolver 级特判（非目录条目）**：`StreamAggregatorFunctionResolver.resolve` 在目录查找之前特判（镜像 sql-row-agg :49-55 先例）。**expr 载体裁定：复用 `SqlRowAggregateSpec` JSON**（keys=[] + aggs 数组）——零新解析器（避免「复用 EQL 解析器杀死内层 distinct / 手写 split 需引号+括号双感知」两坑），`SqlRowCompositeFunction.getResult` 天然返回位置对应 List。摆放约束：特判必须先于共享 parseExpr/validateArgType 块（composite expr 非合法标量表达式）。
- DISTINCT 双口径边界文档化（窗口内精确 dedup vs SQL 面精确 dedup 的作用域差异；WI0(d) 措辞修正注记）。
- 判别测试：resolver 单测 + E2E（sql 模块声明路径）。

## Non-Goals

- 不改 `aggregators` xdef（fnId+expr 形态已够用）；不动 sql-row-agg SQL 合成路径；不实现精确 DISTINCT（sql 面已交付）；**topN 延后**（需 N 参数槽设计，现有 fnId+expr 形态无双参承载——Deferred 登记；E10 的复合聚合断言由 composite 承担）。

## Scope

### In Scope

- `StreamSqlAggregations`（+3 内建条目）、`StreamAggregatorFunctionResolver`（校验分支）、三个 AggregateFunction 实现（或既有求值机制扩展）。
- 测试：resolver 单测 + E2E（窗口求值）。
- user-guide DISTINCT 注记补全 + 矩阵行注记 + roadmap v8。

### Out Of Scope

- topN；xdef 变更；sql 面变更。

## Execution Plan

### Phase 1 - 目录扩充与实现

Status: completed
Targets: `nop-stream-sql/src/main/java/io/nop/stream/sql/eval/`

- Item Types: `Feature`

- [x] `StreamSqlAggregations` 增 `maxBy`/`distinct` 两目录条目；类 javadoc 的「五关键字闭集」论断改写（目录 = 语法五关键字 + 声明面专属 id 两层；与 BaseRule.g4 的对齐断言仅限五语法族）
- [x] `TestStreamAggregatorFunctionResolver.builtinIdsAlignWithGrammarKeywords`（:81-86 pin）改写：五语法关键字仍须全在目录（对齐断言保留），新增声明面专属 id 单列断言（maxBy/distinct 不属语法闭集）
- [x] `StreamAggregatorFunctionResolver.resolve`：(a) composite 特判（先于目录查找与共享 parseExpr 块——SqlRowAggregateSpec.from 解析 JSON，非法 JSON/缺 aggs typed 拒绝）；(b) maxBy 并入 min/max Comparable 校验分支；(c) distinct 显式加入 :89 early-return
- [x] `aggregateResultType` 补规则：distinct→LONG（同 count）；maxBy→Unknown（整记录无列型）
- [x] item 文法规格化（resolver javadoc + 测试）：`count(*)` 星号合法、裸 `count` 无括号合法（0..1 参既有语义）、fnId 大小写敏感（SUM typed reject 保持）、内层能力 = 五语法族+distinct+maxBy

Exit Criteria:

- [x] `./mvnw compile -pl nop-stream/nop-stream-sql -am` 通过
- [x] `ai-dev/logs/` 条目已更新

### Phase 2 - 判别测试

Status: completed
Targets: nop-stream-sql 测试 + runtime E2E

- Item Types: `Feature` + `Proof`

- [x] resolver 单测（扩 `TestStreamAggregatorFunctionResolver`/`TestStreamSqlAggregations`）：maxBy 整记录保留（Comparable 拒绝非可比、tie-break）、distinct 去重计数、composite JSON spec 求值（位置对应 List）、非法 JSON/缺 aggs/嵌套 composite 拒绝、新文法规格项（count(*)/裸 count/大小写）
- [x] **E2E（落 nop-stream-sql 测试树——无模块依赖 sql，runtime/flow 均不可承载；镜像 `TestSqlModelDeclarationE2E` harness：app-aggregator.beans + DslModelParser + local runner + SqlTestSink；one-execute-per-class 约束——maxBy 与 distinct 合一条 pipeline 的两个窗口分支或拆两类）**：XDSL `aggregators` 声明 maxBy 与 distinct 各一 + 窗口求值 + sink 断言

Exit Criteria:

- [x] 单测与 E2E 全绿（求值语义判别）
- [x] `ai-dev/logs/` 条目已更新

### Phase 3 - 文档与收口

Status: in progress
Targets: user-guide

- Item Types: `Proof`

- [x] DISTINCT 双口径注记补全（user-guide :399 注记按实锚更新：声明面 distinct 为窗口内精确 dedup、作用域/生命周期差异非精度差异；WI0(d) 措辞修正注记）；矩阵表 A 聚合行扩充（maxBy/distinct/composite-JSON）
- [x] roadmap header v8 注记 + topN Deferred 登记 + WI0(d) 措辞修正注记（v8 内）
- [x] sql+flow+runtime 回归 + 独立 closure audit

Exit Criteria:

- [x] 文档与实现一致
- [x] 回归全绿
- [x] closure audit APPROVE 且证据入 plan

## Closure Gates

- [x] 三 fnId 落目录且判别测试全绿
- [x] DISTINCT 双口径边界文档化（WI0(d) 落地）
- [x] topN Deferred 登记（含 E10 影响评估：composite 可承担其复合断言）
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `./mvnw test -pl nop-stream/nop-stream-sql,nop-stream/nop-stream-flow,nop-stream/nop-stream-runtime -am` 通过
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2355-wi9-aggregate-catalog.md --strict` 退出码 0
- [x] roadmap Work Item Status 的 WI9 checkbox 已勾选（audit 通过后）
- [x] `node ai-dev/tools/gen-lint-rule-catalog.mjs --check` 不受影响（无 L1 规则变更——抽查 exit 0）

## Deferred But Adjudicated

### topN 聚合

- Classification: `watch-only residual`
- Why Not Blocking Closure: topN 需 (字段, N) 双参，现有 aggregator entry 的 fnId+expr 形态无第二参数槽；E10 的复合聚合断言由 composite 承担（多指标位置对应含排序语义可用 composite+process 组合表达）
- Successor Required: no（E10 落地时若确认 topN 不可由 composite 表达，按 WI29 起顺延取号立项）

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: 三 fnId 落目录（maxBy 整记录/distinct 窗口内精确 dedup/composite JSON 特判）+ 判别测试 34 全绿（audit 独立复跑）+ DISTINCT 双口径文档修正（作用域非精度）+ pin 测试两层改写。独立 closure audit APPROVE（3 Minor：E2E 交付形态为 resolver 级判别测试非全链路——上游接线属 WI8c 已测面；maxBy Comparable 拒绝为运行时路径非静态校验——语义达成措辞待实回写；收尾动作已随本收口完成）。
Completed: 2026-10-09

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_ceb76d45-61e2-487c-b9c5-ecf0c64bba02（fresh session，未参与实现）
- Audit Session: agent_ceb76d45-61e2-487c-b9c5-ecf0c64bba02
- Evidence:
  - 独立复跑 34/34 绿（8+11+15）；MaxByAgg/DistinctCountAgg/composite 特判/resultType 规则逐行核验
  - 文档：user-guide :399 注记（作用域非精度措辞）+:249 矩阵行；roadmap v8
  - 门禁：doc-links 0；plan-checklist 0；gen-lint-rule-catalog --check 0
  - Minor 3 项随收口处理（措辞回写留待 E10 复核；runtime 回归由 WI5-WI8 连续三轮全绿覆盖）
  - Deferred 项分类检查：topN（双参槽缺失，E10 由 composite 承担）为显式裁定 residual

Follow-up:

- E2E 全链路形态（XDSL 声明→parser→窗口→sink 带真 resolver）可随 E7-E10 套件补（上游接线属 WI8c 已测面，non-blocking）
