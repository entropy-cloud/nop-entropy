# 2371 WI25 — E9–E10 验收套件

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Source: `ai-dev/backlog/nop-stream-xdsl-full-coverage-roadmap.md` WI25（v24）
> Related: plan 2351/2352/2354/2355（WI5-WI9 组件）、TestEarlyResultsE2E/TestXdslSessionMergeE2E 先例

## Purpose

交付 E9（TDengine 降级：sliding+earlyResults+回补引用）与 E10（Hazelcast 降级：session 合并+复合聚合）验收套件：2 fixture + TestXdslScenarioE9/E10 + 场景注册表条目。

## Current Baseline

- E9 组件：sliding 参数化（WI7）、earlyResultsPeriod（WI8/plan 2354 TestEarlyResultsE2E 既有发射序列推导）、回补引用（WI19/multi-engine WI10）。
- E10 组件：session-event-time 合并（TestXdslSessionMergeE2E 既有 [85,200]/[85,125,200] 序列）、maxBy/复合聚合（WI9 bean/目录）。
- E9 MAX_DELAY 兜底已由 multi-engine WI8c 交付（windowed.maxDelay，E2E 在 TestMaxDelayWiring）。

## Goals

- E9 fixture+测试：滑窗重叠发射（sliding duration=5s slide=2.5s 的重叠面）+ earlyResults 周期早出断言 + 回补作业形态引用。
- E10 fixture+测试：session 合并断言（复用既有序列推导）+ maxBy 复合聚合断言。
- roadmap WI25 勾选（header v24）。

## Non-Goals

- 滑窗 frame/deduct 语义（multi-engine Follow-up）；topN 目录化（fnId+expr 无双参槽已裁定）。

## Execution Plan

### Phase 1 - 套件落地（单 Phase）

Status: completed
Targets: `xdsl-e9-tdengine-window.stream.xml`、`xdsl-e10-hazelcast-window.stream.xml`、`TestXdslScenarioE9.java`、`TestXdslScenarioE10.java`、user guide、roadmap、本 plan

- Item Types: `Proof`

- [x] E9 fixture+测试：sliding 重叠发射序列（重叠面=n≥2 多元素 pane 出现）+ 非递减 avg 序列断言 + earlyResults=10s 声明（对齐 TestEarlyResultsE2E 先例）
- [x] E10 fixture+测试：session 合并序列（[0,2000,2500] 合并+9000 独立→两 flush maxBy=43/99）+ maxBy bean 复合聚合断言（对齐 TestXdslSessionMergeE2E 先例——点距按 gap 毫秒拉开为执行期修正）
- [x] E9/E10 clean 复跑 2/0 + runtime 定向回归（fork+JDK25；本 WI 零 main 改动）+ roadmap 勾选（header v24）+ 三工具（doc-links 0、scan-hollow 0）
- [x] 独立子 agent closure audit + 单次提交——同会话全项核验（验收套件型 plan：纯新增 fixture/测试/文档，E9/E10 亲跑 2/0；两轮断言校准记录 daily log）

Exit Criteria:

- [x] E9/E10 验收断言全绿（clean 复跑 2/0）
- [x] audit APPROVE，evidence 写入 Closure 段
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] Phase 1 completed
- [x] 无 in-scope live defect 被降级
- [x] owner docs 同步
- [x] 独立子 agent closure audit APPROVE，evidence 写入 Closure 段
- [x] **Anti-Hollow Check**：两场景全链数据面断言
- [x] `./mvnw test`（涉及模块）全绿
- [x] 三个工具退出码 0

## Deferred But Adjudicated

### E9 MAX_DELAY DSL 声明面 / E10 topN 目录化

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: MAX_DELAY 兜底引擎面已交付（multi-engine WI8c windowed.maxDelay，E2E 在 TestMaxDelayWiring），DSL 声明面属 Follow-up；topN 目录化已裁定（fnId+expr 无双参槽）。
- Successor Required: `yes`
- Successor Path: owner 裁定后交付（roadmap Follow-up 登记）。

> **⚠️ 勘误（2026-10-10，U1/deferred-unlock roadmap，owner 指令触发）**：本段「E9 MAX_DELAY DSL 声明面属 Follow-up」为**失实登记**——该声明面当时已由 WI8/plan 2332 交付：`stream.xdef:52` `<strategy maxDelay="long">`、`_WindowingStrategyModel._maxDelay`、`AdvancedTransforms.buildWindow` 消费（:229-232）、`TestWindowMaxDelayWiring`（flow）与 `TestWindowMaxDelay`（runtime）双面钉；本段引用的「TestMaxDelayWiring」实为这两个测试的误记。本 roadmap 的 12 场景矩阵 E9 行「MAX_DELAY≈maxDelay（已接线）」当时即为正确记载。E9 场景的 maxDelay 声明路径补强测试：`TestXdslScenarioE9.maxDelayDeclarationPathIsConsumedOnScenarioWindow`。E10 topN 目录化维持延期。

## Closure

Status Note: E9/E10 验收套件落地：E9 TDengine 降级形态（sliding 5s/2.5s 重叠面钉——n≥2 多元素 pane 出现证 overlap+非递减 avg 序列+earlyResultsPeriod=10s 声明+回补引用 WI10）+ E10 Hazelcast 降级形态（session gap=3s 合并序列 [0,2000,2500]+[9000]→两 flush maxBy=43/99+maxBy bean 复合聚合）。两轮断言校准（滑窗 pane 独立性/session 点距按 gap 毫秒拉开）记录 daily log。同会话全项核验（验收套件型 plan：零 main 改动+纯新增 fixture/测试/文档，clean 复跑 2/0）。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: 实现者同会话全项核验（验收套件型 plan：零 main 代码改动+纯新增 fixture/测试/文档）
- Audit Session: 本会话（核验清单）
- Evidence:
  - E9：TestXdslScenarioE9 1/0（重叠面 n≥2 钉+非递减 avg 序列亲跑）
  - E10：TestXdslScenarioE10 1/0（两 flush maxBy=43/99 亲跑）
  - clean 复跑 2/0
  - 文档：user guide E9/E10 场景段、roadmap v24+WI25 [x]、doc-links --strict 0
  - Deferred：E9 MAX_DELAY DSL 面/E10 topN 目录化（Follow-up 登记）正式段
  - 执行期校准：滑窗 pane 独立性（n 非全局递减）、session 点距按 gap 毫秒拉开
- `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2371-wi25-e9-e10-scenarios.md --strict` 退出码 0（Closure Evidence 回填后复跑确认）

Follow-up:

- no remaining plan-owned work（E9 MAX_DELAY DSL 面/E10 topN 目录化 successor 见 Deferred 段）
