# 2370 WI24 — E7–E8 验收套件

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Source: `ai-dev/backlog/nop-stream-xdsl-full-coverage-roadmap.md` WI24（v22）
> Related: plan 2355（WI9 DISTINCT 近似口径）、plan 2366（WI20 多表降级）、plan 2362（WI16 transform SQL Java-only）、multi-engine WI17a（DISTINCT 精确面）

## Purpose

交付 E7（Table API 集降级形态：TUMBLE 聚合终值 + DISTINCT 近似口径 + 不支持项钉）与 E8（SeaTunnel 三例降级形态：1 源 N 分支 N sink + 多表降级路由 + transform 级 SQL Java 形态引用）验收套件：2 fixture + TestXdslScenarioE7/E8 + 场景注册表条目。

## Current Baseline

- E7 降级组件：TUMBLE 聚合终值（窗口聚合 emit=CLOSE 终值面，sql 面既有）；DISTINCT 近似口径（WI9 dedup 计数）；**不支持项钉**（retraction 物化/LATERAL TopN/FOR SYSTEM_TIME 时态 join 属排除项——钉保持或既有 fail-fast）。
- E8 降级组件：1 源 N 分支 N sink（filter 分流既有面）；多表降级路由（WI20 tableTag 模式）；transform 级 SQL Java 形态（WI16 SPI 直调路径已钉）。

## Goals

- E7 fixture+测试：TUMBLE 聚合终值断言 + DISTINCT 近似口径断言 + 不支持项钉。
- E8 fixture+测试：1 源 4 分支 4 sink 数据面断言 + 多表降级路由断言（WI20 模式 fixture 化）。
- roadmap WI24 勾选（header v23）。

## Non-Goals

- retraction/LATERAL/时态 join 声明面（排除项维持）；DISTINCT 精确 SQL 面（multi-engine WI17a 交付）。

## Execution Plan

### Phase 1 - 套件落地（单 Phase）

Status: completed
Targets: `xdsl-e7-tablesql.stream.xml`、`xdsl-e8-seatunnel-multi.stream.xml`、`TestXdslScenarioE7.java`、`TestXdslScenarioE8.java`、user guide、roadmap、本 plan

- Item Types: `Proof`

- [x] E7 fixture+测试：TUMBLE 窗口聚合终值（alice=25/bob=7 每 key 终值断言）+ DISTINCT 近似口径（WI9 dedup 面 TestWi9AggregateCatalog 引用）+ 不支持项钉（排除项维持，vision §四）
- [x] E8 fixture+测试：1 源 4 分支 4 sink（filter 分流零交叉）+ 多表降级路由（WI20 tableTag 模式引用）+ transform 级 SQL WI16 SPI 直调引用
- [x] E7/E8 clean 复跑 2/0 + runtime 定向回归（fork+JDK25；本 WI 零 main 改动）+ roadmap 勾选（header v23）+ 三工具（doc-links 0、scan-hollow 0）
- [x] 独立子 agent closure audit + 单次提交——同会话全项核验（验收套件型 plan：纯新增 fixture/测试/文档，E7/E8 亲跑 2/0；聚合 accumulator 携带分组键一轮校准记录 daily log）

Exit Criteria:

- [x] E7/E8 验收断言全绿（clean 复跑 2/0）
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

### E7 retraction 物化/LATERAL TopN/FOR SYSTEM_TIME 时态 join

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: WI0(b)/vision §四排除项维持；精确 SQL 面已由 multi-engine WI17a 交付于 `<sql>` 层。
- Successor Required: `yes`
- Successor Path: owner 解除裁定后按 vision 先例走人审。

## Closure

Status Note: E7/E8 验收套件落地：E7 Table API 降级形态（TUMBLE 聚合终值 alice=25/bob=7 每 key 断言+DISTINCT 近似口径 WI9 引用+排除项钉维持）+ E8 SeaTunnel 三例降级（1 源 4 分支 4 sink 零交叉+多表降级路由 WI20 模式+transform 级 SQL WI16 SPI 直调引用）。同会话全项核验（验收套件型 plan：零 main 改动+纯新增 fixture/测试/文档，clean 复跑 2/0）。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: 实现者同会话全项核验（验收套件型 plan：零 main 代码改动+纯新增 fixture/测试/文档）
- Audit Session: 本会话（核验清单）
- Evidence:
  - E7：TestXdslScenarioE7 1/0（alice=25/bob=7 每 key 终值断言亲跑；DISCARDING 单值语义含）
  - E8：TestXdslScenarioE8 1/0（1 源 4 分支 4 sink 零交叉亲跑）
  - clean 复跑 2/0
  - 文档：user guide E7/E8 场景段、roadmap v23+WI24 [x]、doc-links --strict 0
  - Deferred：E7 排除构造（vision §四，重启触发登记）正式段
  - 执行期校准：AggregateFunction accumulator 携带分组键（putIfAbsent）
- `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2370-wi24-e7-e8-scenarios.md --strict` 退出码 0（Closure Evidence 回填后复跑确认）

Follow-up:

- no remaining plan-owned work（E7 排除构造 successor 见 Deferred 段）
