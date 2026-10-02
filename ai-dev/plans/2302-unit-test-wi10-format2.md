# 2302 unit-test-coverage-roadmap WI10 — nop-format 第二批（pdf / mermaid / converter / office-model / chart-export）

> Plan Status: active
> Last Reviewed: 2026-10-02
> Source: ai-dev/backlog/unit-test-coverage-roadmap.md（WI10 条目）；ai-dev/analysis/2026-10/2026-10-02-unit-test-wi0-coverage-baseline.md
> Related: docs-for-ai/02-core-guides/testing.md；ai-dev/plans/2292-unit-test-wi0-coverage-baseline.md

## Purpose

nop-format 第二批：nop-pdf（28.03%）、nop-mermaid（6.40%）、nop-converter（9.30%）、nop-office-model（0，无测试）、nop-office-doc-model（0，无测试）、nop-chart-export（46.72% 已达标确认）。

## Current Baseline

- nop-pdf 28.03% / 5619L；nop-mermaid 6.40% / 641L；nop-converter 9.30% / 731L；nop-office-model 与 nop-office-doc-model NO-EXEC（无测试；pom 实测各有 junit 依赖，直接可写测试）；nop-chart-export 46.72% / 1828L（已达标）。
- 测量管线：删模块 exec → `ai-dev/tools/coverage-baseline.sh --skip-test --label wi10-2026-10-02`；验证 `mvnq -- test -pl :<module> -am -fae`。mvnq = `ai-dev/tools/mvnq`。

## Goals

- nop-pdf ≥10 用例（pdf 模型解析/生成语义）。
- nop-mermaid ≥6 用例（mermaid 文本解析语义）。
- nop-converter ≥6 用例（转换语义）。
- office-model 两模块 ≥6 结构性用例。
- chart-export 不回退确认。
- 测试全绿，记录增量。

## Non-Goals

- 不修改产品代码；不做视觉回归（像素比对）。

## Scope

### In Scope

- `nop-format/nop-pdf|nop-mermaid|nop-converter|nop-office-model|nop-office-doc-model|nop-chart-export/src/test/**` 与测试资源。
- **pom 裁定**：office-model 两模块 junit 依赖已具备，无 pom 变更。

### Out Of Scope

- 产品代码；excel/record（WI9）。

## Execution Plan

### Phase 1 - 增量测试编写

Status: planned
Targets: `nop-format/*/src/test/**`

- Item Types: `Fix`

- [ ] pdf ≥10、mermaid ≥6、converter ≥6、office-model 两模块合计 ≥6 用例。
- [ ] 各模块 `mvnq -- test -pl :<module> -am -fae` 全绿。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 新增测试 ≥28 用例，含显式语义断言。
- [ ] 各模块测试全绿。
- [ ] pom 变更（如有）仅 test-scope 依赖且已记录。
- [ ] No owner-doc update required。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 覆盖增量实测与裁定

Status: planned
Targets: `ai-dev/analysis/2026-10/`、roadmap WI10 checkbox

- Item Types: `Proof` + `Decision`

- [ ] 删模块 exec → baseline 脚本复测，记录增量；chart-export 不回退确认。
- [ ] 残余缺口显式裁定（periphery 目标 30%）。
- [ ] 独立子 agent closure audit 通过后勾选 roadmap WI10 checkbox。

Exit Criteria:

- [ ] 增量数字记录在案。
- [ ] 裁定有记录。
- [ ] roadmap WI10 checkbox 与 plan/log 一致。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [ ] 各模块全部新增测试绿（含既有测试零回归）
- [ ] 产品代码零修改；pom 仅 test-scope 新增且记录
- [ ] 覆盖增量实测记录
- [ ] 残余缺口显式裁定（无静默降级）
- [ ] No owner-doc update required（已裁定）
- [ ] Anti-Hollow Check：解析/转换测试断言产出语义（audit 抽查）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2302-unit-test-wi10-format2.md --strict` 退出码 0
- [ ] roadmap WI10 checkbox 与 plan/log 一致

## Deferred But Adjudicated

（执行结束时按实测填写）

## Non-Blocking Follow-ups

（执行结束时填写）

## Closure

Status Note: （完成时填写）

Completed:

Closure Audit Evidence:

- Reviewer / Agent:（待独立子 agent closure audit 后填写）

Follow-up:

- （待填写）
