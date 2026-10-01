# WI2 Closure Audit——07-wi2-window-codegen-dialect-flags.md

- Audit 日期：2026-10-02
- Auditor：独立子 agent（fresh session，与 plan 起草审查 agent 及执行 agent 均不同 task）
- 裁定：首轮 **FAIL**（2 Major 测试缺口）→ 补强后复核 **PASS**（无 Blocker）

## 1. 首轮审计核验（机制本体全部 PASS）

| 项 | 裁定 | 证据 |
|---|---|---|
| 双通道测试实测 | PASS | TestEqlCompileSql 24/24（(a) 关态错误码+feature 参数、(b) 开态 SQL、(c) RANGE 开/GROUPS 关、(d) 位置与列 resolve、(e)(f)(g)） |
| 能力门真实生效 | PASS | windowNameScopes 栈 push/pop、先门后 super、no-from 含 windowClause、decl 遍历门——非 getter 空壳 |
| 生成管线未触碰 | PASS | nop-dao pom 与 `_gen` 零 diff；改动全集 = plan 允许清单 |
| wrapper 绑定端到端 | PASS | fixture x:extends h2 + supportWindowFrameRows=true 真实加载，按属性绑定（Range/Groups false 互证） |
| 全量回归 | PASS | nop-orm-eql 92/92；nop-dao 反应堆零失败 |
| scan-hollow / owner doc / 日志 / doc-links / roadmap 未越界 | 全 PASS | 两模块 0 findings；能力开关段一致；日志如实含 4 踩坑与两轮审查史；strict 0；roadmap diff 空 |

首轮 FAIL 项：Major-1 EQL 通道 round-trip 断言缺失；Major-2 三单位双态字面未满足（RANGE 缺关态、GROUPS 缺开态）。

## 2. 补强与复核（PASS）

- Major-1 → `testEqlChannelRoundTrip`：parse → toSqlString contains frame/window 片段（真实 EQL 通道，非 SQL 通道复用）——复核实测绿。
- Major-2 → `testWindowFrameRangeOffGroupsOnGate`：RANGE 关态（rows/groups 为 true 仍抛，反证门读位正确）+ GROUPS 开态（SQL 精确 contains `groups 1 preceding`）——复核实测绿；三单位开/关双态矩阵闭合。
- 顺带修正：visitSqlWindowFrameBound offset 变体双空格（PRECEDING/FOLLOWING 不再补前导空格），被 GROUPS 精确断言钉住。
- 4 Minor 落实：IDialect 缩进、visitor FQN/import 序、plan (h2) 措辞、surefire flag 记入日志。
- 复核实测：nop-orm-eql -am 全量 94/94 BUILD SUCCESS；`_gen`/pom/roadmap 仍零 diff。

## 3. 残余观察（不阻塞）

- FOLLOWING-offset 变体无精确子串断言（PRECEDING 侧已钉、代码对称）。

## 4. Phase 4 收口确认

- [x] roadmap WI2 `todo` → `done`（括注零圆括号字符）；解析器 items 31 milestones 7
- [x] plan Closure 段写入本 audit 证据（两轮审计 + 复核），Plan Status → completed
- [x] scan-hollow 两模块 0（执行与 audit 双重复核）
- [x] check-plan-checklist.mjs --strict 退出码 0
