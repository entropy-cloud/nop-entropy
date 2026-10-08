# WI26 Closure Audit——06-wi26-hop-session-table-functions.md

> Auditor: 独立子 agent（fresh session，与起草审查 agent_2c57b5f5/agent_07fb8dc0 均不同 task）：agent_8da235aa-56aa-40cb-bfdb-5469de9986b7
> Date: 2026-10-08
> Verdict: 首轮 **REJECTED**（B-1 sql 回归 SKIPPED + M-1 措辞残留）→ 补救后同 agent 复核 **APPROVED**（0 Blocker / 0 Major / 1 笔误级 Minor 已修）

## 审计轮次

### R1（REJECTED）

九项验证中七项 PASS（grammar/AST 六步产物、手写补件、编译器泛化、flow/xdef、判别测试、corpus 快照、零越权），两项硬缺口：

1. **B-1**：执行者两轮全量回归中 nop-stream-sql 均 SKIPPED（首轮停于 EQL corpus、次轮停于 nop-batch flake）——存量 ~110 sql 测试未在最终代码上运行。
2. **M-1**：「仅 TUMBLE 时间列生效」lateness 措辞残留 docs-for-ai/nop-stream-sql.md :53 与 sql-subset §4b :108 两处，与 live 行为（windowSpecOf 三伪表函数统一映射）及 plan Goal 4 矛盾。
3. Minor：m-1 EqlASTKind ordinal 31/32 中段插入 vs plan「尾部追加 68/69」文本偏差；m-2 日志测试计数失实；m-3 doc-links 证据时序；m-4 布尔优先级写法。

### R2 补救复核（APPROVED）

| # | 补救项 | 结果 | 证据 |
|---|---|---|---|
| 1 | B-1 | 已解决 | `_tmp/wi26-full-regression3.log` BUILD SUCCESS、Reactor 0 SKIPPED；**nop-stream-sql 121/0**（110 存量+11 新增，三个新测试类轮内实跑绿）、eql 807/0、core 1754/0、flow 206/0；batch flake 本轮通过；时序完整（compiler 最终修改 02:03 < 回归 02:09） |
| 2 | M-1 | 已解决 | 两 doc 更正为「仅伪表函数（TUMBLE/HOP/SESSION）时间列生效……WI26 推广」，与 live 一致，全仓无「仅 TUMBLE」残留；doc-links --strict 0（auditor 复跑） |
| 3 | m-1 | 已解决 | plan「Closure Audit R1 记录」节如实记录（31/32 中段插入、无 ordinal 消费方、同族相邻接受理由）；基线原文保留 |
| 4 | m-2 | 大部分解决 | 主计数 +31 已更正；验证行「12/12」笔误随收口日志修正为 11/11 |
| 5 | m-4 | 已解决 | 布尔括号修复，语义等价，已被回归3覆盖 |

回归性检查：git status 41 条与首轮范围一致，无新增越权；grammar/手写件/测试/corpus 快照未被补救动作触碰，原 PASS 维持。

## Exit Criteria 终判

八条全 PASS（端到端 SESSION/HOP 真实执行精确 multiset；接线三段拼接；无静默跳过四门；五处测试覆盖；双 interval 三原则；四模块全绿；owner doc 两处；日志）。Anti-Hollow：E2E 从 compile 到 sink 全链真实执行，新增分支全部显式 fail-fast，无空壳/静默跳过。
