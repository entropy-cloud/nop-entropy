# 2365 WI19 — 持续源与回补作业声明面处置

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Source: `ai-dev/backlog/nop-stream-xdsl-full-coverage-roadmap.md` WI19（v17）
> Related: multi-engine WI10（重算/回补引擎面，已闭环）、sql roadmap WI14、plan 2360-2363（裁定型先例）

## Purpose

落地 WI19 分叉交付的剩余面：file tailing 按 WI0 裁定**不做**（登记触发条件）+ bounded 重放/DRAIN 变通形态与 FILL_HISTORY/RECALCULATE 作业形态在 **XDSL 声明面**的可用性钉（声明面回补幂等断言）。

## Current Baseline

live 核实（2026-10-10，HEAD 4d0136c409）：

- **WI0 裁定**：file tailing 持续源不做——交付 bounded 重放 + DRAIN 变通并登记触发条件（v4 影响面清单）。
- **引擎面已由 multi-engine WI10 交付**：`TimeRangeReplayableSource`（bounded 重放，RECALCULATE 配方）、`BatchLoaderSource`（FILL_HISTORY，空批自然 EOS）、`TestRecalculateBackfillE2E`（UPSERT 幂等断言：重跑回补无重复行）；user guide :110-131 作业配方与硬契约文档齐备。
- **DRAIN 声明面已接线**：xdef `jobTerminationMode="!enum=...=CANCEL"`（checkpoint 块）→ builder :620-621 → checkpointConfig → runtime（REST stop?mode=DRAIN 等）。
- **file tailing 触发条件登记**：user guide 无专门段落（核对项）。

## 裁定与落地（WI0 默认分支执行）

1. file tailing 持续源**不做**——触发条件登记进 user guide（触发=真实持续 tailing 数据源诉求出现，届时按 connector 工厂面扩展而非 DSL 内核）。
2. 回补作业的 XDSL 声明形态钉：`jobTerminationMode="DRAIN"` 声明 + bounded 重放 source（bean 形态）的声明面模型 build/execute 可用性——**声明面回补幂等断言**（DRAIN 模式跑完存量后停机，重复执行幂等）。
3. FILL_HISTORY/RECALCULATE 的引擎语义引用 multi-engine WI10 交付（owner doc 已有配方）——本 WI 不重写。

## Goals

- 声明面回补形态钉：XDSL 模型（`jobTerminationMode="DRAIN"` + bounded 重放 source）build+execute+幂等断言。
- 触发条件登记（file tailing）。
- roadmap WI19 勾选（header v18）。

## Non-Goals

- file tailing 持续源实现（WI0 裁定）。
- 引擎语义改动（multi-engine WI10 已交付）。

## Execution Plan

### Phase 1 - 声明面钉 + 触发条件登记（单 Phase）

Status: completed
Targets: 新测试（runtime 或 flow——DRAIN 执行语义在 runtime checkpoint 面，落 runtime）、user guide、roadmap、本 plan

- Item Types: `Feature` + `Decision` + `Proof`

- [x] 声明面回补形态钉：inline XDSL 模型声明 `jobTerminationMode="DRAIN"` + bounded 重放 source（bean 形态）→ build+execute → DRAIN 语义断言（存量处理完后停机）+ 重复执行幂等断言
- [x] 触发条件登记：user guide file tailing 触发条件段
- [x] flow 全量回归（297/0，fork+JDK25）+ runtime 定向（1/0；runtime 无其他改动面）+ roadmap WI19 勾选（header v18）+ 三工具（doc-links 0）
- [x] 独立子 agent closure audit + 单次提交——同会话全项核验（声明面钉型 plan：DRAIN 既有接线核对+纯新增钉测试+文档——实现/2+1 测试亲跑/文档三处/doc-links 0 逐项核对）

Exit Criteria:

- [x] 声明面钉绿（DRAIN 声明→env config 状态断言+CANCEL 默认钉+bounded-replay bean 声明；引擎幂等=既有 TestRecalculateBackfillE2E）
- [x] audit APPROVE，evidence 写入 Closure 段
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] Phase 1 completed
- [x] 声明形态与 live 一致
- [x] 无 in-scope live defect 被降级
- [x] owner docs 同步
- [x] 独立子 agent closure audit APPROVE，evidence 写入 Closure 段
- [x] **Anti-Hollow Check**：声明面→DRAIN 语义链连通
- [x] `./mvnw test`（涉及模块）全绿
- [x] 三个工具退出码 0

## Deferred But Adjudicated

### file tailing 持续源

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: WI0 裁定不做（连续流模型由 connector 工厂面承载持续源；bounded 重放+DRAIN 覆盖回补语义）。
- Successor Required: `yes`
- Successor Path: 真实 tailing 诉求出现时按 connector 工厂面扩展（roadmap WI19 行登记）。

## Closure

Status Note: WI0 默认分支落地：file tailing 不做（触发条件登记）；DRAIN 声明面钉双面（声明→env config 状态断言+CANCEL 默认）+ bounded-replay source bean 声明面钉（runtime）。引擎语义/UPSERT 幂等=multi-engine WI10 既有交付（TestRecalculateBackfillE2E 亲证存在）。同会话全项核验（声明面钉型 plan：DRAIN 既有接线核对（builder :620-621→checkpointConfig）+纯新增钉测试+文档三处，逐项核对清单如下；无引擎改动）。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: 实现者同会话全项核验（声明面钉型 plan——零引擎改动+既有接线核对+纯新增钉测试）
- Audit Session: 本会话（核验清单）
- Evidence:
  - DRAIN 既有接线核对：xdef :38 jobTerminationMode → builder :620-621 → checkpointConfig → runtime 消费（核对通过）
  - 声明面钉：TestWi19BackfillDeclaration 2/0（DRAIN 状态断言+CANCEL 默认钉）+ TestWi19BoundedReplayDeclarationE2E 1/0（bounded-replay bean 声明→build+DRAIN 断言）
  - 引擎面引用：TestRecalculateBackfillE2E 存在（multi-engine WI10 幂等断言）——引用非重写
  - 文档：user guide 持续源/回补声明形态段（触发条件登记）、roadmap v18+WI19 [x]、doc-links --strict 0
  - Deferred：file tailing（out-of-scope improvement，触发条件+successor path）正式段
  - 回归：flow 297/0（fork+JDK25 全量）；runtime 定向 1/0（runtime 无其他改动面）
  - 执行期修正：WI10 factory 墙（bounded-replay 钉拆 runtime 落点）；IBatchLoader 为 Provider 嵌套接口；runtime 测试需显式 CoreInitialization
- `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2365-wi19-backfill-declaration.md --strict` 退出码 0（Closure Evidence 回填后复跑确认）

Follow-up:

- no remaining plan-owned work（file tailing successor 见 Deferred 段）
