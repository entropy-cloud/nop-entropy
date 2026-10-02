# 2299 unit-test-coverage-roadmap WI7 — nop-sys / nop-rule / nop-dyn 补强

> Plan Status: draft
> Last Reviewed: 2026-10-02
> Source: ai-dev/backlog/unit-test-coverage-roadmap.md（WI7 条目）；ai-dev/analysis/2026-10/2026-10-02-unit-test-wi0-coverage-baseline.md
> Related: docs-for-ai/02-core-guides/testing.md；ai-dev/plans/2292-unit-test-wi0-coverage-baseline.md

## Purpose

可复用业务模块补强：nop-sys（sys-api 57 main/0 test 序列号/数据字典/分布式锁语义、sys-dao 67.99% 补强）、nop-rule（rule-core 71.19% 已达标、rule-api 21 main/0、rule-dao 17 main/0）、nop-dyn（dyn-api 45 main/0、dyn-dao 29.58% 动态表单校验）。

## Current Baseline

- nop-sys-api：NO-EXEC（无 src/test、无 junit 依赖）；nop-sys-dao 67.99% / 1331L（靶点 SysDictLoader 0%、OrmEntityChangeLogInterceptor 8.97%）。
- nop-rule-core 71.19%（靶点 RuleServiceImpl 64L/0%）；nop-rule-api / nop-rule-dao：NO-EXEC（无 junit 依赖）。
- nop-dyn-api：NO-EXEC（无 junit 依赖）；nop-dyn-dao 29.58% / 693L（靶点 DynEntityMetaToOrmModel 347L/16.43%、NopDynFunctionMeta 0%）。
- 测量管线：删模块 exec → `ai-dev/tools/coverage-baseline.sh --skip-test --label wi7-2026-10-02`；验证 `mvnq -- test -pl :<module> -am -fae`。mvnq = `ai-dev/tools/mvnq`。
- WI0 失败遗留：nop-rule-service 的 TestNopRuleDefinitionBizModel 3 errors（FILE_HASH 快照非确定性字段）——本 WI 顺带修复该快照测试（testing.md「快照不匹配诊断」：ORM 字段补 tagSet 或重录 output；如需改 orm 模型 tagSet 属测试资源修正，记录后执行）。

## Goals

- nop-sys-api：序列号/数据字典/分布式锁语义纯逻辑用例 ≥8 个。
- nop-rule-api + nop-rule-dao：结构性用例 ≥6 个。
- nop-dyn-api：结构性用例 ≥4 个；nop-dyn-dao：DynEntityMetaToOrmModel 转换语义 ≥4 用例。
- nop-sys-dao 补强 ≥3 用例；nop-rule-service 快照测试修复并转绿。
- 各模块测试全绿，记录增量。

## Non-Goals

- 不修改产品代码；nop-rule-core 已达标仅不回退确认。

## Scope

### In Scope

- `nop-sys|nop-rule|nop-dyn/*/src/test/**` 新增测试与资源。
- **pom 裁定**：允许且仅允许为缺测试依赖模块（sys-api/rule-api/rule-dao/dyn-api）新增 `junit-jupiter` test-scope 依赖并逐项记录。
- nop-rule-service 既有快照测试修复（测试资源 + 必要的 orm tagSet 标注，逐项记录）。

### Out Of Scope

- 产品代码；nop-rule-core 增量（已达标）。

## Execution Plan

### Phase 1 - 增量测试编写与快照修复

Status: planned
Targets: `nop-sys|nop-rule|nop-dyn/*/src/test/**`、nop-rule-service 快照

- Item Types: `Fix`

- [ ] sys-api ≥8 用例（序列号/字典/锁语义，参照 nop-sys 现有测试与 docs-for-ai 对应模块文档）。
- [ ] rule-api + rule-dao ≥6 用例；dyn-api ≥4 用例；dyn-dao ≥4 用例；sys-dao ≥3 用例。
- [ ] TestNopRuleDefinitionBizModel 快照修复转绿（按 testing.md 诊断流程；orm tagSet 变更需记录）。
- [ ] 各模块 `mvnq -- test -pl :<module> -am -fae` 全绿。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 新增测试 ≥25 用例，含显式语义断言；快照测试修复且 CHECKING 模式绿。
- [ ] 各模块测试全绿。
- [ ] pom/orm tagSet 变更（如有）仅测试面且已记录。
- [ ] No owner-doc update required。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 覆盖增量实测与裁定

Status: planned
Targets: `ai-dev/analysis/2026-10/`、roadmap WI7 checkbox

- Item Types: `Proof` + `Decision`

- [ ] 删模块 exec → baseline 脚本复测，记录增量。
- [ ] 残余缺口显式裁定（engine 层目标 45%）。
- [ ] 独立子 agent closure audit 通过后勾选 roadmap WI7 checkbox。

Exit Criteria:

- [ ] 增量数字记录在案。
- [ ] 裁定有记录。
- [ ] roadmap WI7 checkbox 与 plan/log 一致。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [ ] 全部新增测试绿（含既有测试零回归；nop-rule-service 快照修复后绿）
- [ ] 产品代码零修改；pom 仅 test-scope 新增且记录
- [ ] 覆盖增量实测记录
- [ ] 残余缺口显式裁定（无静默降级）
- [ ] No owner-doc update required（已裁定）
- [ ] Anti-Hollow Check：序列号/锁语义测试断言行为而非仅实例化（audit 抽查）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2299-unit-test-wi7-sys-rule-dyn.md --strict` 退出码 0
- [ ] roadmap WI7 checkbox 与 plan/log 一致

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

## Optional Sections

- Risks And Rollback: 纯测试增量 + 快照修复可独立回滚；orm tagSet 变更按 testing.md「ORM 模型变更 + 重新录制快照一起提交」执行。
