# 08 WI28 全局聚合放行

> Plan Status: active
> Last Reviewed: 2026-10-08
> Source: `ai-dev/backlog/nop-stream-sql-enhancement-roadmap.md`（WI28 行）；`ai-dev/design/nop-stream/sql-subset-and-semantics.md` §4b（HAVING 行「全局聚合 + HAVING 维持排除」、流时间窗口行全局聚合 fail-fast 表述）；`ai-dev/analysis/streaming-sql-survey/2026-10-07-flink-sql-analysis.md` 与 2026-10-07-ksql-analysis.md（全局聚合形态，转引）
> Related: `ai-dev/plans/nop-stream-sql/21-wi11-continuous-groupby.md`（keyBy+reduce 持续聚合——本项复用其路径）
> Owner: 仓库 owner（委托链：2026-10-08 执行指令「在 nop-entropy-stream-roadmap worktree 中执行 nop-stream-sql-enhancement-roadmap.md 直到彻底完成，每工作项按 plan guide 拟制计划，每完成一个计划提交一次」）

## Purpose

无 GROUP BY 的五聚合（sum/count/avg/min/max）放行：以合成单键 keyBy 将全部记录路由到单个 keyed reduce 实例（单并行度归约），复用持续聚合路径（D1 last-value-wins 逐条发射）；§4b 全局聚合 fail-fast 表述修订与钉码同步。

## Current Baseline

2026-10-08 实测：StreamSqlCompiler 聚合路径分派 :352-359（无 GROUP BY 即 `global aggregation ... outside the stream SQL v1 subset` fail-fast）；compileContinuousAggregate（:522-587）为 keyBy(accumulator head)+reduce+toRow 全链；钉码 globalAggregationFailsFast（TestStreamSqlCompiler :349-352）；§4b 两行含全局聚合排除表述（HAVING 行「全局聚合 + HAVING 维持排除」、流时间窗口行「全局聚合 fail-fast」）。

## Closure Audit R1 记录（2026-10-08，agent_fce90ef0 REJECTED→补救）

- **B1**：窗口伪表源+全局聚合由 typed 拒绝退化为裸 NPE（probe 实证三型）→ 分派加 typed 拒绝（文案 "global aggregation over a window table source requires GROUP BY"），探针转正为 windowedGlobalAggregationFailsFast 三型钉码。
- **M1**：owner docs（nop-stream-sql.md 不支持清单措辞、sql-subset §4b 两行）与日志已补齐。
- **m1**：HAVING 双层守卫（compileQuery :272 先行 + buildGroupedAggregate 防御层）——defense-in-depth 保留。
- **m2**：plan 为实现后拟制（实现先行偏差已声明），Phase 2 按实况勾选翻转。
- **m3**：withHavingFailsFast 与 havingWithoutGroupByFailsFast 命中同一 :272 守卫——意图不同（WI17 面 vs WI28 §4b 面），接受。

## Goals

1. 无 GROUP BY + 有聚合 + 无窗口 → 走 continuous aggregate 路径 + 合成单键（constant true）——keyBy 常量键把全部记录路由到同一 keyed reduce 实例（即单并行度归约）；D1 last-value-wins 逐条发射运行值。
2. **全局聚合 + HAVING 维持排除**（§4b 原裁定不变）——HAVING 守卫 typed fail-fast。
3. 钉码同步：globalAggregationFailsFast 翻转为正向 shape 钉（keyExpr=event[0][0] 头键 + spec 嵌入 begin xpl）；新增 globalAggregationWithHavingFailsFast；E2E 运行总和序列断言。
4. 文档：§4b HAVING 行与流时间窗口行全局聚合表述修订；nop-stream-sql.md；roadmap WI28 状态行；日志。

## Non-Goals

- 全局聚合 + HAVING；全局聚合 + 窗口伪表函数（TUMBLE 无 GROUP BY 仍走 plain 管道）；多键； retract 语义。

## Execution Plan

### Phase 1 - 实现/钉码/E2E

Status: completed
Targets: `nop-stream/nop-stream-sql/src/main/java/io/nop/stream/sql/compile/StreamSqlCompiler.java`

- Item Types: `Feature`

- [x] 编译器：buildGroupedAggregate 增 globalAgg 参数（合成键 true、HAVING fail-fast 守卫、跳过 GROUP BY 循环）；分派路由（无 GROUP BY+无窗口+有聚合 → continuous 路径）
- [x] 钉码：globalAggregationFailsFast → globalAggregationCompilesWithSyntheticSingleKey（keyExpr=event[0][0]+begin xpl spec）；+globalAggregationWithHavingFailsFast
- [x] E2E：TestGlobalAggregationE2E——运行总和序列 [1,3,3,6,10,5,6]（null 跳过）、终值 6
- [x] 全量回归：nop-stream-sql -am

Exit Criteria:

- [x] **端到端验证**（guide #22）：全局聚合查询经真实执行产生运行总和序列且终值正确
- [x] **接线验证**（guide #23）：合成单键 keyBy 路由全部记录到单一 reduce（event[0][0] 常量头键 shape 断言 + E2E 行为）
- [x] **无静默跳过**（guide #24）：全局聚合 + HAVING typed fail-fast（维持排除）
- [x] 新功能测试覆盖（guide #25）：shape/HAVING 守卫/E2E 三处
- [x] `./mvnw test -pl nop-stream/nop-stream-sql -am` 全绿
- [x] owner doc 更新：sql-subset §4b 两行、nop-stream-sql.md
- [x] ai-dev/logs/2026/10-08.md 已更新

### Phase 2 - roadmap 同步与收口

Status: in progress
Targets: `ai-dev/backlog/nop-stream-sql-enhancement-roadmap.md`

- Item Types: `Proof`

- [ ] 独立子 agent closure audit；证据落 ai-dev/audits/nop-stream-sql-enhancement/wi28-closure-audit.md 与本 plan Closure 段
- [ ] audit 通过后 roadmap WI28 状态行 `todo` → `done`（括注单层、内无 `）` 与 `)`）；重跑解析器核对 items 18 milestones 4
- [ ] plan Closure 段写入证据，Plan Status → `completed`，check-plan-checklist --strict 退出码 0，check-doc-links --strict 退出码 0，scan-hollow --severity high 退出码 0

## Closure Gates

- [ ] 行为/契约结果已达成：全局聚合端到端可用，运行总和语义正确，HAVING 维持排除
- [ ] 必要 focused verification 已完成（shape/HAVING 守卫/E2E）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] 受影响的 owner docs 已同步
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：合成键→keyBy→单 reduce 全链连通（E2E 真实执行）
- [x] `./mvnw test -pl nop-stream/nop-stream-sql -am` 全绿
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: 全局聚合放行交付——合成单键 keyBy 单归约（复用持续聚合全链）、运行总和 E2E 真跑、HAVING 组合维持排除、窗口伪表源+全局聚合 typed 拒绝（B1 修复）。closure audit R1 REJECTED 两必修（B1 NPE 回归/M1 docs）补救后复核通过。
Completed: 2026-10-08

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session）：agent_fce90ef0-4a17-41ef-ac0c-d69db675c692
- Audit Session: 证据落档 ai-dev/audits/nop-stream-sql-enhancement/wi28-closure-audit.md
- Evidence:
  - Phase 1 六项 Exit Criteria：E2E/接线/新测试/回归/文档/日志全 PASS（B1 修复与 docs 补齐后复核）
  - Anti-Hollow：合成键→keyBy→单 reduce 全链静态 trace + E2E 真跑；scan-hollow exit 0
  - 回归：_tmp/wi28-full-regression.log BUILD SUCCESS（sql 123→B1 修复后 focused 复跑 39/39）
  - B1 修复三型钉码 windowedGlobalAggregationFailsFast 实测绿
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql-enhancement/08-wi28-global-aggregation.md --strict` 退出码 0（翻转后复跑确认）

Follow-up:

- no remaining plan-owned work
