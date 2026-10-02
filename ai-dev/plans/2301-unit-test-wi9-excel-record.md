# 2301 unit-test-coverage-roadmap WI9 — nop-format 第一批（nop-excel / nop-record）

> Plan Status: draft
> Last Reviewed: 2026-10-02
> Source: ai-dev/backlog/unit-test-coverage-roadmap.md（WI9 条目）；ai-dev/analysis/2026-10/2026-10-02-unit-test-wi0-coverage-baseline.md
> Related: docs-for-ai/02-core-guides/testing.md；ai-dev/plans/2292-unit-test-wi0-coverage-baseline.md

## Purpose

nop-excel（16.44%）模型解析/公式计算/导出（golden 快照模式）补强；nop-record 二进制编解码 roundtrip 补强（59.17%，已达标但 roundtrip 语义面增补）。

## Current Baseline

- nop-excel 16.44% 行 / 14.79% 分支 / 4604L；nop-record 59.17% / 4519L（已达标 ≥30%）。
- 既有测试：excel 11 个测试文件、record 24 个——写法参照。
- 测量管线：删模块 exec → `ai-dev/tools/coverage-baseline.sh --skip-test --label wi9-2026-10-02`；验证 `mvnq -- test -pl :<module> -am -fae`。mvnq = `ai-dev/tools/mvnq`。

## Goals

- nop-excel：≥15 个语义用例（xlsx 模型解析、公式计算、导出输出 golden 快照——按 testing.md「XPL Tag 输出 Golden JSON 快照」惯例，录入方法 @Disabled）。
- nop-record：≥6 个 roundtrip 用例（encode→decode 恒等、边界值、类型宽度）。
- 两模块测试全绿，记录增量。

## Non-Goals

- 不修改产品代码；不做真实 Office 套件兼容性 E2E。

## Scope

### In Scope

- `nop-format/nop-excel|nop-record/src/test/**` 与 golden fixtures（test resources）。

### Out Of Scope

- 产品代码、pom；nop-format 其他模块（WI10）。

## Execution Plan

### Phase 1 - 增量测试编写

Status: planned
Targets: `nop-format/nop-excel|nop-record/src/test/**`

- Item Types: `Fix`

- [ ] excel ≥15 用例（解析/公式/导出 golden；显式断言核心字段 + 快照全结构比对）。
- [ ] record ≥6 roundtrip 用例。
- [ ] 两模块 `mvnq -- test -pl :<module> -am -fae` 全绿。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 新增测试 ≥21 用例，含显式语义断言；golden 文件入库。
- [ ] 两模块测试全绿。
- [ ] No owner-doc update required。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 覆盖增量实测与裁定

Status: planned
Targets: `ai-dev/analysis/2026-10/`、roadmap WI9 checkbox

- Item Types: `Proof` + `Decision`

- [ ] 删两模块 exec → baseline 脚本复测，记录增量。
- [ ] 残余缺口显式裁定（periphery 目标 30%）。
- [ ] 独立子 agent closure audit 通过后勾选 roadmap WI9 checkbox。

Exit Criteria:

- [ ] 增量数字记录在案。
- [ ] 裁定有记录。
- [ ] roadmap WI9 checkbox 与 plan/log 一致。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [ ] 两模块全部新增测试绿（含既有测试零回归）
- [ ] 产品代码/pom 零修改（git 证据）
- [ ] 覆盖增量实测记录
- [ ] 残余缺口显式裁定（无静默降级）
- [ ] No owner-doc update required（已裁定）
- [ ] Anti-Hollow Check：导出/编解码测试断言产出内容而非仅调用（audit 抽查）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2301-unit-test-wi9-excel-record.md --strict` 退出码 0
- [ ] roadmap WI9 checkbox 与 plan/log 一致

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
