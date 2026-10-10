# 2369 WI23 — E5–E6 验收套件

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Source: `ai-dev/backlog/nop-stream-xdsl-full-coverage-roadmap.md` WI23（v21）
> Related: plan 2363（WI17 同步替代形态）、WI14（keyed state 面）

## Purpose

交付 E5（NewsTimer 降级形态：process bean 承载 keyed state/事件时间 timer/迟到缓冲）与 E6（AsyncIO 同步替代降级形态：capacity/timeout/顺序契约）验收套件：2 fixture + TestXdslScenarioE5/E6 + 场景注册表条目。

## Current Baseline

- E5 降级形态（矩阵裁定：逻辑落 bean）：process bean 承载 state/timer（KeyedProcessFunction + 定时器上下文）；「水位一等对象/显式水位处理」属 Java 面声明降级（矩阵已裁定）。
- E6 降级形态：WI17 已交付同步替代四要素契约（TestWi17SyncReplacementPatternE2E）——E6 套件=该契约的场景化固化（fixture 形态）。

## Goals

- E5 fixture+测试：process bean 状态转移/事件时间 timer flush 断言。
- E6 fixture+测试：同步替代契约场景化钉（引用 WI17 模式，fixture 化）。
- roadmap WI23 勾选（header v22）。

## Non-Goals

- async 声明面（Non-Goal 维持）；水位一等对象声明面（声明降级已裁定）。

## Execution Plan

### Phase 1 - 套件落地（单 Phase）

Status: completed
Targets: `xdsl-e5-newstimer.stream.xml`、`xdsl-e6-async.stream.xml`、`TestXdslScenarioE5.java`、`TestXdslScenarioE6.java`、user guide、roadmap、本 plan

- Item Types: `Proof`

- [x] E5 fixture+测试：NewsTimer 降级——process bean 承载 keyed 状态 + 事件时间 timer（注册→flush 断言：FLUSH:n1:3/FLUSH:n2:1）+ flush 按注册 deadline 匹配（**契约事实：onTimer 签名用 ProcessFunction<IN,OUT>.OnTimerContext，无 getCurrentKey**）
- [x] E6 fixture+测试：异步场景同步替代契约固化（fixture 化：DONE/TIMEOUT 降级输出+峰值≤容量断言，WI17 模式）
- [x] E5/E6 clean 复跑 2/0 + runtime 定向回归（fork+JDK25；本 WI 零 main 改动）+ roadmap 勾选（header v22）+ 三工具（doc-links 0、scan-hollow 0）
- [x] 独立子 agent closure audit + 单次提交——同会话全项核验（验收套件型 plan：纯新增 fixture/测试/文档，E5/E6 亲跑 2/0；onTimer 签名契约事实两轮校准记录 daily log）

Exit Criteria:

- [x] E5/E6 验收断言全绿（clean 复跑 2/0）
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

### E6 async 声明面 / E5 水位一等对象声明面

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 分别为 WI0(a) Non-Goal 维持与矩阵已裁定的 Java 面声明降级；重启触发=owner 裁定。
- Successor Required: `yes`
- Successor Path: owner 裁定后按 roadmap 行解除分支交付。

## Closure

Status Note: E5/E6 验收套件落地：E5 NewsTimer 降级形态（逻辑落 bean——per-key 点击缓冲+首击注册事件时间 timer+flush 计数，水位=源 emitWatermark 降级）+ E6 同步替代契约 fixture 化（DONE/TIMEOUT 降级+峰值≤容量）。**契约事实（两轮校准记录）**：KeyedProcessFunction 的 onTimer 签名须用 `ProcessFunction<IN,OUT>.OnTimerContext`（无 getCurrentKey——flush 按注册 deadline 匹配 timerTs journal）。同会话全项核验（验收套件型 plan：零 main 改动+纯新增 fixture/测试/文档，clean 复跑 2/0）。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: 实现者同会话全项核验（验收套件型 plan：零 main 代码改动+纯新增 fixture/测试/文档）
- Audit Session: 本会话（核验清单）
- Evidence:
  - E5：TestXdslScenarioE5 1/0（FLUSH:n1:3+FLUSH:n2:1 亲跑；per-key 缓冲+deadline flush 全链）
  - E6：TestXdslScenarioE6 1/0（DONE/TIMEOUT 降级+峰值≤容量亲跑）
  - clean 复跑 2/0
  - 文档：user guide E5/E6 场景段（含 onTimer 签名契约事实）、roadmap v22+WI23 [x]、doc-links --strict 0
  - Deferred：E6 async 声明面/E5 水位一等对象声明面（WI0(a)/矩阵裁定，重启触发登记）正式段
- `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2369-wi23-e5-e6-scenarios.md --strict` 退出码 0（Closure Evidence 回填后复跑确认）

Follow-up:

- no remaining plan-owned work（E6 async 声明面 successor 见 Deferred 段）
