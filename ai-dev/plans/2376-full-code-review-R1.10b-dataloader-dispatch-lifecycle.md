# 2376 full-code-review R1.10b — GraphQL DataLoader 调度生命周期重构（P1-2.6-004 承接）

> Plan Status: draft
> Last Reviewed: 2026-10-09
> Source: finding `P1-2.6-004`（`2.6-biz-graphql-deep-audit.md` dim12-07）；从 plan 2375（R1.10）拆出——见「拆分缘由」
> Related: plan 2375（R1.10，P1-2.6-001 事务修复，已独立交付）

## Purpose

修复 DataLoader 唯一调度点时序缺陷（异步 operation+批量 @BizLoader 组合静默挂死）。**本缺陷涉及 GraphQL 执行核心的异步调度生命周期重设计，须独立深度设计而非随批修复**。

## 拆分缘由（R1.10 执行记录）

plan 2375 初版拟定的修复锚点（fetchNext 同步返回后 dispatch）在实测中触发测试挂死（graphql-core 测试套件 2 分钟无输出）——该锚点对「异步 op body 内注册 loader 并 await 其结果」的形态不成立：body 完成本身依赖 load future，任何「完成后 dispatch」形态都晚于挂死点。正确修复需重设计调度生命周期（候选：BeanMethodBatchFetcher 注册后 trailing dispatch / graphql-java 原生 DataLoader 生命周期对齐 / 定时兜底 dispatch），须独立 plan 承载。原 :98/:179 提前 dispatch 已恢复（挂死消失，既有测试绿）。

## Current Baseline

- 缺陷：`GraphQLExecutor.executeOneAsync` :98 与 `executeAsync` :179 的 dispatchAll 在 invokeOperation(s) 发起后立即执行；异步 operation 体内（biz 方法 await loader 结果前）注册的批量 loader 错过 dispatch → 永不执行 → await 侧静默挂死（探针 `_tmp/fcr26-probe/DataLoaderDispatchProbe.java` B 用例）。
- `dispatchAll` 幂等（synchronized+快照清空+递归 re-dispatch），`GraphQLExecutor.dispatchAll`（:192-197）递归承接 promise 完成后的新注册。
- 已否定的修复形态：①完成后 dispatch（死锁——body 完成依赖 load）；②fetchNext 同步返回后 dispatch（本 plan 拆分缘由——实测挂死：async body 的 fetchNext 在 load 完成后才走）。
- 既有测试：`TestBeanMethodBatchFetcher`（5 用例，含 dispatchAll 先例）在原始代码上绿。

## Goals

- 异步 operation+批量 loader 组合不再挂死（端到端超时断言）。
- 同步既有行为零回归。

## Candidate Directions (须在执行前定稿其一)

1. **BeanMethodBatchFetcher.load 注册后 trailing dispatch**：fetcher 注册 loader 后调 `context.dispatchAll()`（fire-and-forget）——每次 load 注册即触发全量 dispatch，递归 re-dispatch 收敛。简单直接，但 dispatch 频次=注册次数（性能权衡需评估）。
2. **定时兜底 dispatch**：注册首个 loader 时调度延迟 dispatch（如 50ms）——低频但引入时延。
3. **operationInvoker 完成前锚点**：在 biz 方法返回值可用、load future 未 await 的切点插入 dispatch（需 invokeAsync 语义支持，最深但最精确）。

## Non-Goals

- P1-2.6-001（plan 2375 已交付）；其他 GraphQL 发现。

## Execution Plan

### Phase 1 - 方案定稿 + 实现 + 端到端回归

Status: planned
Targets: `GraphQLExecutor.java` / `BeanMethodBatchFetcher.java`（按定稿方案）

- Item Types: `Fix`

- [ ] 从候选方向定稿其一并留档理由
- [ ] 实现 + 端到端回归测试（审计探针 B 用例形态：异步 op+批量 loader，**带 3s 超时断言**——修复前挂死即超时 FAIL）
- [ ] stash 判别 FAIL→PASS 留档

Exit Criteria:

- [ ] 端到端回归绿且带超时断言；stash 判别记录在案
- [ ] graphql-core 全模块绿（含 TestBeanMethodBatchFetcher 5 用例）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 挂死消除（超时断言回归锁定）
- [ ] graphql-core 全模块绿
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] Anti-Hollow Check：stash 判别真实 FAIL→PASS
- [ ] check-plan-checklist --strict 退出码 0

## Closure

Status Note: <<完成时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立子 agent>>
- Evidence: <<PASS-FAIL + 证据>>
