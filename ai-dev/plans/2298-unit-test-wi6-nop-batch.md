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

Status: completed
Targets: `nop-batch/*/src/test/**`

- Item Types: `Fix`

- [x] batch-core ≥10 用例（chunk/checkpoint/断点语义 + RangeSplitUtils 边界）。（TestRangeSplitUtils 14 用例 + TestBatchTaskCheckpoint 8 用例 = 22）
- [x] batch-dsl ≥6 用例；batch-exp ≥4 用例。（TestJdbcBatchSupport 7 用例；TestImportTableConfig 6 + TestFieldsProcessorEval 6 = 12）
- [x] 三模块 `mvnq -- test -pl :<module> -am -fae` 全绿。（batch-core 带 -am 绿；batch-dsl/batch-exp 因 -am 链上游 nop-dao 的 WI4 测试中间版本 testCompile 失败改为不带 -am `-pl :<module> -fae`，三模块全绿零回归。**收口勘误**：该"上游阻断"经主会话核实为时序性中间态——WI4 最终提交版 TestJdbcDataSetSemantics.java:115 为强转 JdbcDataSet 的合法调用，且 nop-dao 两次独立验证 161 tests 全绿；见偏差节）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 新增测试 ≥20 用例，含显式语义断言。（41 用例：core 22 + dsl 7 + exp 12）
- [x] 三模块测试全绿。（core 59 / dsl 20 / exp 19，0 failures 0 errors）
- [x] No owner-doc update required。（纯测试增量，未改任何产品语义/API/约定）
- [x] `ai-dev/logs/` 对应日期条目已更新。（plan owner 已补记）

### Phase 2 - 覆盖增量实测与裁定

Status: completed
Targets: `ai-dev/analysis/2026-10/`、roadmap WI6 checkbox

- Item Types: `Proof` + `Decision`

- [x] 删三模块 exec → baseline 脚本复测，记录增量。（`coverage-baseline-wi6-2026-10-02.json`：batch-core 55.40%→61.20%（+5.80pp）、batch-dsl 44.35%→52.07%（+7.72pp）、batch-exp 65.74%→69.76%（+4.02pp）；靶点类 RangeSplitUtils 0%→96.91%（94/97L）、JdbcBatchSupport 0%→97.37%（37/38L）、ImportTableConfig 39.47%→100%（38/38L）；三模块新快照 lowCoverageClasses 均清空）
- [x] 残余缺口显式裁定（engine 层目标 45%）。（三模块全部超过 engine 目标 45%；残余缺口为接口 default 方法、2 行级 dsl model 类与 BatchTaskContextImpl setter 面（126L）、BlockingSourceBatchLoader/SplitBatchConsumer 等 consumer/loader 面——与本 WI 靶点无关，裁定不在本 WI 内追补，见 Deferred But Adjudicated）
- [ ] 独立子 agent closure audit 通过后勾选 roadmap WI6 checkbox。（audit 进行中）

Exit Criteria:

- [x] 增量数字记录在案。
- [x] 裁定有记录。
- [ ] roadmap WI6 checkbox 与 plan/log 一致。（待独立 closure audit）
- [x] `ai-dev/logs/` 对应日期条目已更新。（plan owner 已补记）

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

（2026-10-02 WI6 执行实测）

- 三模块 lowCoverageClasses 已清空（新快照 `coverage-baseline-wi6-2026-10-02.json`），WI6 靶点全部覆盖（RangeSplitUtils 96.91%、JdbcBatchSupport 97.37%、ImportTableConfig 100%）。
- 残余低覆盖面裁定不在本 WI 追补：batch-core 的 BatchTaskContextImpl setter/回调面（126L missed）、ResourceRecordLoaderProvider（62L，需资源 IO 环境）、BatchTaskBuilder 装配分支（42L）、BlockingSourceBatchLoader/SplitBatchConsumer（blocking/dispatch 语义需专用 harness）；batch-exp 的 ImportDbTool 编排层（72L，需 JDBC 环境）；batch-dsl 的 2 行级 model 类（getter/setter 面）。均超过 engine 层 45% 目标，无静默降级。
- taskKey 无唯一约束并发防重（P1）维持独立立项，回归测试并入时记账。

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
