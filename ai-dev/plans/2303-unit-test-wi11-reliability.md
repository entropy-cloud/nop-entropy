# 2303 unit-test-coverage-roadmap WI11 — 可靠性外围（cluster / retry / tcc / network / graph）

> Plan Status: draft
> Last Reviewed: 2026-10-02
> Source: ai-dev/backlog/unit-test-coverage-roadmap.md（WI11 条目）；ai-dev/analysis/2026-10/2026-10-02-unit-test-wi0-coverage-baseline.md
> Related: docs-for-ai/02-core-guides/testing.md（异步防挂起六规则）；ai-dev/plans/2292-unit-test-wi0-coverage-baseline.md

## Purpose

可靠性外围补强：nop-cluster-core（13.95%）、nop-retry（engine 83.55% 已达标；api/dao/service NO-EXEC 结构性用例）、nop-tcc-core（6.11%）、nop-network（组内模块按基线补缺）、nop-graph-core（79.28% 已达标；已知 TarjanSCC lowLink 缺陷修复时回归并入）。异步/并发域**严格遵守 testing.md 防挂起六规则**。

## Current Baseline

- nop-cluster-core 13.95% / 1104L；nop-tcc-core 6.11% / 540L；nop-retry-engine 83.55% / 602L（已达标），retry-api/dao/service NO-EXEC；nop-network 组模块（nop-vertx-* / nop-http-* 等）数字见快照 JSON；nop-graph-core 79.28% / 637L（已达标）。
- 测量管线：删模块 exec → `ai-dev/tools/coverage-baseline.sh --skip-test --label wi11-2026-10-02`；验证 `mvnq -- test -pl :<module> -am -fae`。mvnq = `ai-dev/tools/mvnq`。

## Goals

- nop-cluster-core ≥8 用例（注册/发现/心跳语义；并发用例带 @Timeout(10)、future.get 带超时）。
- nop-tcc-core ≥8 用例（try/confirm/cancel 语义）。
- nop-retry api/dao/service 结构性用例 ≥6 个。
- nop-network：按快照找 1-2 个低覆盖可测模块补 ≥6 用例。
- nop-graph-core：不回退确认（TarjanSCC 回归测试待缺陷修复立项时并入）。
- 测试全绿，记录增量。

## Non-Goals

- 不修改产品代码；TarjanSCC/retry 幂等键 P1 缺陷只记录不修；不做跨进程/真实网络 E2E。

## Scope

### In Scope

- `nop-cluster|nop-retry|nop-tcc|nop-network|nop-graph/*/src/test/**`。
- **pom 裁定**：缺 junit 依赖的模块新增 test-scope junit 并记录。

### Out Of Scope

- 产品代码；已达标模块增量（cluster/retry-engine/tcc/graph 外）。

## Execution Plan

### Phase 1 - 增量测试编写

Status: planned
Targets: `nop-cluster|nop-retry|nop-tcc|nop-network|nop-graph/*/src/test/**`

- Item Types: `Fix`

- [ ] cluster-core ≥8、tcc-core ≥8、retry 结构性 ≥6、network 补缺 ≥6 用例；全部遵守防挂起六规则。
- [ ] 各模块 `mvnq -- test -pl :<module> -am -fae` 全绿。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 新增测试 ≥28 用例，含显式语义断言；异步测试类均有 @Timeout。
- [ ] 各模块测试全绿。
- [ ] pom 变更（如有）仅 test-scope 依赖且已记录。
- [ ] No owner-doc update required。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 覆盖增量实测与裁定

Status: planned
Targets: `ai-dev/analysis/2026-10/`、roadmap WI11 checkbox

- Item Types: `Proof` + `Decision`

- [ ] 删模块 exec → baseline 脚本复测，记录增量；graph/retry-engine 不回退确认。
- [ ] 残余缺口显式裁定（periphery 目标 30%）。
- [ ] 独立子 agent closure audit 通过后勾选 roadmap WI11 checkbox。

Exit Criteria:

- [ ] 增量数字记录在案。
- [ ] 裁定有记录。
- [ ] roadmap WI11 checkbox 与 plan/log 一致。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [ ] 各模块全部新增测试绿（含既有测试零回归）
- [ ] 产品代码零修改；pom 仅 test-scope 新增且记录
- [ ] 覆盖增量实测记录
- [ ] 残余缺口显式裁定（无静默降级）
- [ ] No owner-doc update required（已裁定）
- [ ] Anti-Hollow Check：并发/TCC 语义测试断言行为；无裸 future.get()/take()（audit 抽查）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2303-unit-test-wi11-reliability.md --strict` 退出码 0
- [ ] roadmap WI11 checkbox 与 plan/log 一致

## Deferred But Adjudicated

（执行结束时按实测填写）

## Non-Blocking Follow-ups

- TarjanSCC lowLink 缺陷、retry 幂等键生命周期 P1——修复立项时回归测试并入本 WI 记账。

## Closure

Status Note: （完成时填写）

Completed:

Closure Audit Evidence:

- Reviewer / Agent:（待独立子 agent closure audit 后填写）

Follow-up:

- （待填写）
