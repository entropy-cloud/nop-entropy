# 2298 unit-test-coverage-roadmap WI6 — nop-batch 引擎面补强

> Plan Status: active
> Last Reviewed: 2026-10-02
> Source: ai-dev/backlog/unit-test-coverage-roadmap.md（WI6 条目）；ai-dev/analysis/2026-10/2026-10-02-unit-test-wi0-coverage-baseline.md
> Related: docs-for-ai/02-core-guides/testing.md；ai-dev/plans/2292-unit-test-wi0-coverage-baseline.md

## Purpose

nop-batch 引擎面补强：nop-batch-core chunk 处理/断点续传/checkpoint 语义（55.40%，已达标但引擎语义面偏薄）、nop-batch-dsl 模型解析（44.35%）、nop-batch-exp 表达式求值（65.74%）。已知 P1（taskKey 无唯一约束并发防重）修复时回归并入（独立立项）。

## Current Baseline

- nop-batch-core 55.40% / 2018L；靶点 RangeSplitUtils 97L/0%。
- nop-batch-dsl 44.35% / 699L；靶点 JdbcBatchSupport 38L/0%。
- nop-batch-exp 65.74% / 721L；靶点 ImportTableConfig 39.47%。
- 三模块均在 nop-batch 组（parent 链到 root），测量走统一 baseline 脚本（删 exec → `--skip-test --label wi6-2026-10-02`）；验证 `mvnq -- test -pl :<module> -am -fae`。mvnq = `ai-dev/tools/mvnq`。

## Goals

- batch-core：chunk 处理/断点续传/checkpoint 语义用例 ≥10 个（含 RangeSplitUtils 分片边界）。
- batch-dsl：模型解析语义用例 ≥6 个。
- batch-exp：表达式求值边界 ≥4 个。
- 三模块测试全绿，记录增量。

## Non-Goals

- 不修改产品代码；taskKey P1 只记录不修；不做容器级批处理 E2E。

## Scope

### In Scope

- `nop-batch/nop-batch-core|nop-batch-dsl|nop-batch-exp/src/test/**` 新增测试与资源。

### Out Of Scope

- 产品代码、pom（三模块已有 junit 依赖）、生成管线。

## Execution Plan

### Phase 1 - 增量测试编写

Status: planned
Targets: `nop-batch/*/src/test/**`

- Item Types: `Fix`

- [ ] batch-core ≥10 用例（chunk/checkpoint/断点语义 + RangeSplitUtils 边界）。
- [ ] batch-dsl ≥6 用例；batch-exp ≥4 用例。
- [ ] 三模块 `mvnq -- test -pl :<module> -am -fae` 全绿。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 新增测试 ≥20 用例，含显式语义断言。
- [ ] 三模块测试全绿。
- [ ] No owner-doc update required。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 覆盖增量实测与裁定

Status: planned
Targets: `ai-dev/analysis/2026-10/`、roadmap WI6 checkbox

- Item Types: `Proof` + `Decision`

- [ ] 删三模块 exec → baseline 脚本复测，记录增量。
- [ ] 残余缺口显式裁定（engine 层目标 45%）。
- [ ] 独立子 agent closure audit 通过后勾选 roadmap WI6 checkbox。

Exit Criteria:

- [ ] 增量数字记录在案。
- [ ] 裁定有记录。
- [ ] roadmap WI6 checkbox 与 plan/log 一致。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [ ] 三模块全部新增测试绿（含既有测试零回归）
- [ ] 产品代码/pom 零修改（git 证据）
- [ ] 覆盖增量实测记录
- [ ] 残余缺口显式裁定（无静默降级）
- [ ] No owner-doc update required（已裁定）
- [ ] Anti-Hollow Check：chunk/checkpoint 测试断言语义（audit 抽查）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2298-unit-test-wi6-nop-batch.md --strict` 退出码 0
- [ ] roadmap WI6 checkbox 与 plan/log 一致

## Deferred But Adjudicated

（执行结束时按实测填写）

## Non-Blocking Follow-ups

- taskKey 无唯一约束并发防重（P1）——修复立项时回归测试并入本 WI 记账。

## Closure

Status Note: （完成时填写）

Completed:

Closure Audit Evidence:

- Reviewer / Agent:（待独立子 agent closure audit 后填写）

Follow-up:

- （待填写）

## Optional Sections

- Risks And Rollback: 纯测试增量，可单独回滚。
