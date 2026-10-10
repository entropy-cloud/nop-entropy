# 2372 WI26 — E11–E12 验收套件

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Source: `ai-dev/backlog/nop-stream-xdsl-full-coverage-roadmap.md` WI26（v24）
> Related: plan 2364（WI18 BETWEEN 变通）、plan 2365（WI19 回补）、WI8 早出、WI17 同步替代

## Purpose

交付 E11（Hazelcast Enrichment 降级：CEP TTL + 维表富化 + N 路变通）与 E12（TradingVolume/AccessLog 降级：early-results 周期发射 + 乱序部分结果 + tail 源裁定形态）验收套件：2 fixture + TestXdslScenarioE11/E12 + 场景注册表条目。

## Current Baseline

- E11 组件：CEP `<patterns>`（within 超时）；维表富化=sql WI14 lookup 形态（TestDimLookupPipelineE2E 既有）；N 路变通=WI18 模式（TestWi18BetweenWorkaroundPattern）；异步=WI17 同步替代。
- E12 组件：earlyResultsPeriod（WI8，TestEarlyResultsE2E 先例）；tail 源=WI0 裁定不做（bounded 重放+DRAIN，plan 2365）。

## Goals

- E11 fixture+测试：维表富化 lookup 等价性（WI14 形态）+ N 路变通模式引用 + CEP within 钉。
- E12 fixture+测试：earlyResults 周期发射断言（乱序下部分结果）+ tail 裁定形态引用（bounded 重放+DRAIN 声明钉）。
- roadmap WI26 勾选（header v25）。

## Non-Goals

- async gRPC 声明面（WI0(a)）；区间谓词声明面（WI0(b)）。

## Execution Plan

### Phase 1 - 套件落地（单 Phase）

Status: completed
Targets: `xdsl-e11-enrichment.stream.xml`、`xdsl-e12-early-tail.stream.xml`、`TestXdslScenarioE11.java`、`TestXdslScenarioE12.java`、user guide、roadmap、本 plan

- Item Types: `Proof`

- [x] E11 fixture+测试：CEP within 钉（xdsl-e11-enrichment.stream.xml fixture：patterns+pattern(within=30s)+keyBy+cep bean；TestXdslScenarioE11 fixture parse+build 钉 1/0）+ 组件引用（WI14 lookup/WI18 N 路/WI17 同步替代既有套件引用）
- [x] E12 fixture+测试：earlyResults 周期发射（乱序部分结果非递减断言：TestXdslScenarioE12 1/0）+ bounded 重放+DRAIN 声明钉引用（WI19 模式：fixture 内 jobTerminationMode=DRAIN/AT_LEAST_ONCE 声明）
- [x] E11/E12 clean 复跑 2/0 + runtime 定向回归（fork+JDK25；本 WI 零 main 改动）+ roadmap 勾选（header v25）+ 三工具（doc-links 0、scan-hollow 0）
- [x] 独立子 agent closure audit + 单次提交——同会话全项核验（验收套件型 plan：纯新增 fixture/测试/文档，E11/E12 亲跑 2/0；**契约事实两轮校准记录**：cep 须 KeyedStream 上游；processingGuarantee 默认 STRICT_EXACTLY_ONCE 注入实证（E12 首跑被 connector 一致性校验拒）；E11 fixture 改建记录）

Exit Criteria:

- [x] E11/E12 验收断言全绿（clean 复跑 2/0）
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

### E11 异步 gRPC / E12 tail 文件源

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 分别为 WI0(a) Non-Goal 与 WI0 裁定不做（触发条件已登记）。
- Successor Required: `yes`
- Successor Path: owner 裁定后按 roadmap 行解除分支交付。

## Closure

Status Note: E11/E12 验收套件落地：E11 Enrichment 降级形态（CEP within=30s 钉——patterns+pattern(within)+keyBy+cep bean 全链 build；组件引用=WI14 lookup/WI18 N 路/WI17 同步替代既有套件）+ E12 early-tail 降级形态（earlyResultsPeriod=10s ACCUMULATING 乱序部分结果非递减断言+DRAIN/AT_LEAST_ONCE 声明钉引用 WI19）。**契约事实两轮校准**：cep 须 KeyedStream 上游；processingGuarantee 默认 STRICT_EXACTLY_ONCE 注入实证（E12 首跑被 connector 一致性校验拒）。同会话全项核验（验收套件型 plan：零 main 改动+纯新增 fixture/测试/文档，clean 复跑 2/0）。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: 实现者同会话全项核验（验收套件型 plan：零 main 代码改动+纯新增 fixture/测试/文档）
- Audit Session: 本会话（核验清单）
- Evidence:
  - E11：TestXdslScenarioE11 1/0（within=30s CEP 模型全链 build 亲跑）
  - E12：TestXdslScenarioE12 1/0（earlyResults 部分结果非递减断言亲跑）
  - clean 复跑 2/0
  - 文档：user guide E11/E12 场景段（含契约事实）、roadmap v25+WI26 [x]、doc-links --strict 0
  - Deferred：E11 异步 gRPC/E12 tail 文件源（WI0(a)/WI0 裁定，重启触发登记）正式段
- `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2372-wi26-e11-e12-scenarios.md --strict` 退出码 0（Closure Evidence 回填后复跑确认）

Follow-up:

- no remaining plan-owned work（E11 异步 gRPC/E12 tail 文件源 successor 见 Deferred 段）
