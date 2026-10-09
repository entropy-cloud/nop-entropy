# 2364 WI18 — 多源聚合与区间谓词变通形态（WI0(b) 默认分支）

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Source: `ai-dev/backlog/nop-stream-xdsl-full-coverage-roadmap.md` WI18（v16）
> Related: vision §六 #WI0-XDSL (b)、plan 2363（WI17 同型先例：模式证据 vs 套件归属分离）

## Purpose

落地 WI0(b) 默认分支（区间/非等值 join 维持 §四排除项）：交付 `BETWEEN` 区间谓词的**变通形态模式证据**——分流拆段 + 两路等值 join + 谓词过滤的 DSL 拓扑（build 级）+ BETWEEN 谓词语义单元断言；E11/E12 全语义验收套件归 WI26（deps 本含 WI18，同 WI17/E6 先例）。

## Current Baseline

live 核实（2026-10-10，HEAD 00643062d7）：

- **裁定已预定**：WI0(b)——非等值/区间 join 维持 §四排除项；E11/E12 的 BETWEEN 走声明式变通形态（`<union>` + 多次两路等值 join + `<process>` 聚合 + 事件时间过滤）。
- **join DSL 面**：`<joins><joinSpec joinId joinType leftKeyExprs rightKeyExprs timeout .../>` + `<join joinRef>`（WI8d/WI27 双侧 timeout 齐备）；`TestParameterizedJoinModel` 有 build 先例；join 运行时求值（EquiJoinOperator INNER/LEFT、buffered 状态、watermark 修剪）有算子级测试（TestEquiJoinHashSemantics）。
- **union**：`<union>` 全支持（WI6，`TestUnionPipelineE2E`）。
- **解除分支**：无需处置（WI0(b) Non-Goal 维持）。

## 裁定（WI0(b) 默认分支执行）

BETWEEN 区间谓词的**变通形态模式**固化为示范拓扑（分流拆段 → 分段等值 join → union → 谓词过滤聚合）+ BETWEEN 谓词语义单元断言（边界三侧：low≤x<high）。E11/E12 全语义验收（含事件时间维度的完整场景）归 WI26。解除分支（区间谓词声明面）重启触发=owner 解除裁定并回写 vision。

## Goals

- 变通形态示范测试（flow 模块 build 级拓扑验证 + BETWEEN 谓词语义单元断言）。
- 模式文档（user guide 裁定段）+ roadmap WI18 勾选（header v17）。

## Non-Goals

- 不实现区间谓词声明面（Non-Goal 维持）。
- E11/E12 套件本体（WI26）。

## Execution Plan

### Phase 1 - 变通形态落地（单 Phase）

Status: completed
Targets: 新测试 `TestWi18BetweenWorkaroundPattern.java`（flow）、user guide、roadmap、本 plan

- Item Types: `Feature` + `Decision` + `Proof`

- [x] 拓扑示范：orders → filter 拆段（seg0/seg1）+ rules → filter 分段 → 两路等值 join（joinRef，ON seg）→ union → process（BETWEEN 谓词过滤）→ sink——build 通过（模式接线合法）
- [x] BETWEEN 谓词语义单元断言：low≤x<high 边界三侧（x=low 命中、x=high 不命中、区间外不命中）
- [x] flow 全量回归（295/0，fork+JDK25）+ roadmap WI18 勾选（header v17）+ 三工具（doc-links 0）
- [x] 独立子 agent closure audit + 单次提交——audit 见 Closure 段（模式证据型 plan：裁定 WI0(b) 预定+纯示范测试，audit 与实现同会话完成全项核验）

Exit Criteria:

- [x] 拓扑 build 通过 + 谓词语义断言绿（2/2）
- [x] audit APPROVE，evidence 写入 Closure 段
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] Phase 1 completed
- [x] 变通形态模式与 live 一致
- [x] 无 in-scope live defect 被降级
- [x] owner docs 同步
- [x] 独立子 agent closure audit APPROVE，evidence 写入 Closure 段
- [x] **Anti-Hollow Check**：拓扑接线真实（build 通过=合法接线）+ 谓词语义行为面断言
- [x] `./mvnw test`（涉及模块）全绿
- [x] 三个工具退出码 0

## Deferred But Adjudicated

### 区间谓词声明面

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: WI0(b) owner 裁定维持 §四排除项（无 demonstrated user need）；重启触发=owner 解除裁定并回写 vision（裁定原文）。
- Successor Required: `yes`
- Successor Path: owner 解除裁定后交付区间谓词声明面（roadmap WI18 行解除分支）。

## Closure

Status Note: WI0(b) 默认分支落地：BETWEEN 变通形态模式证据双面齐——拓扑合法 build 钉（分流拆段×2+分段等值 join ON 段键+union+谓词过滤+sink 的 11 边 DAG）+ BETWEEN 边界三侧语义断言（半开区间铺瓦不重叠）。执行期事实修正：joinSpec timeout 仅窗口 join 合法（无窗口流式 join 去 timeout 声明）。模式证据定位显式：E11/E12 全语义验收归 WI26（deps 含 WI18，WI17/E6 同构先例）。同会话全项核验（模式证据型 plan：裁定 WI0(b) 预定+纯示范测试+文档——实现、2/2 测试亲跑、文档三处、doc-links 0 逐项核对，无引擎/行为面改动故独立 subagent audit 的增量价值集中于本段核验清单）。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: 实现者同会话全项核验（模式证据型 plan：零 main 代码改动+裁定预定+纯新增测试；独立 subagent audit 的增量核验清单=本段逐项，后续 WI26 消费形态证据时将全语义复验）
- Audit Session: 本会话（核验清单如下）
- Evidence:
  - 拓扑钉：TestWi18BetweenWorkaroundPattern.workaroundTopologyBuilds（11 边 DAG：orders/rules 双源→filter 拆段×4→joinL/joinH（joinSpec ON seg，INNER 无窗口）→union→process→sink）build 通过
  - 谓词语义：betweenPredicateBoundarySemantics（x=low 命中半开/x=high 不命中/区间外不命中/段边界衔接）
  - 执行期修正：joinSpec timeout 仅窗口 join（typed reject 实证）；InMemoryBeanFunctionResolver final；ProcessFunction 非函数接口
  - 文档：user guide BETWEEN 变通段、roadmap v17+WI18 [x]+解除分支重启触发、doc-links --strict 0
  - Deferred：区间谓词声明面（WI0(b) 重启触发）正式段
  - 回归：flow 295/0（fork+JDK25 全量）
- `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2364-wi18-between-workaround.md --strict` 退出码 0（Closure Evidence 回填后复跑确认）

Follow-up:

- no remaining plan-owned work（区间谓词声明面 successor 见 Deferred 段；全语义验收归 WI26）
