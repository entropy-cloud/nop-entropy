# 2295 unit-test-coverage-roadmap WI3 — nop-xlang 补强

> Plan Status: active
> Last Reviewed: 2026-10-02
> Source: ai-dev/backlog/unit-test-coverage-roadmap.md（WI3 条目）；ai-dev/analysis/2026-10/2026-10-02-unit-test-wi0-coverage-baseline.md
> Related: docs-for-ai/02-core-guides/testing.md；ai-dev/plans/2292-unit-test-wi0-coverage-baseline.md

## Purpose

nop-xlang（WI0 实测 47.70% 行 / 44949L，语义基线裁定模块）增量补强：xpl/xscript 求值边界（字面量/运算符/内建函数/错误路径）、解析错误恢复、xdef 校验边角、WI0 快照点名的 0% 类（DeltaDiffer、XSchemaToJsonSchema、ExpressionToFilterBeanTransformer、JavaToXLangTransformer 等）。protected area 产品代码零修改。

## Current Baseline

- 47.70% 行 / 35.33% 分支 / 44949L，src/test 现有 99 个 .java（67 个 Test*.java，以 live 为准）+ 大量 `.test` 数据用例集（数据驱动形态，WI0 已按语义基线裁定，不强求 55%）。
- **口径注记（对抗审查确认）**：WI0 快照的 nop-xlang 报告由补跑管线生成，root pom 的 jacoco excludes（含 `parse/antlr/**`）未对 nop-kernel 子树生效，故 47.70% 的分母**含 antlr 生成类**。Phase 2 必须复用同一管线（coverage-baseline.sh）保证口径可比，不得中途改口径。
- WI0 快照 lowCoverageClasses 列 41 个靶点，其中含 antlr 生成类（XLangParserBaseListener 等 4 个）与接口/抽象壳——**靶点过滤规则：剔除 antlr 生成物、接口、抽象类、内部类后约剩 30 个**，可选靶点示例：DeltaDiffer 142L/0%（public 构造器，TestDeltaMerger 只测了 Merger 侧）、XSchemaToJsonSchema 103L/0%、ExpressionToFilterBeanTransformer 135L/0%、JavaToXLangTransformer 142L/0%、XDslCleaner、BizActionGenHelper。
- **测量管线约束（WI0 实测）**：nop-xlang 在 nop-kernel 无 parent 子树，`-Pcoverage test -pl :nop-xlang` 不产 exec。**验证**用 `mvnq -- test -pl :nop-xlang -am -fae`；**测量**统一走 `删模块 exec → ai-dev/tools/coverage-baseline.sh --skip-test --label wi3-2026-10-02`。
- 已知产品缺陷（2026-09-30 审计）：JsPromise 错误路径偏离 JS 语义三处——修复独立立项，其回归测试届时并入；本 WI 不修产品。

## Goals

- WI0 快照 0% 靶点中 ≥10 个可纯逻辑测试类补测。
- xpl/xscript 求值边界新增 ≥10 个语义用例（错误路径优先）。
- `mvnq -- test -pl :nop-xlang -am -fae` 全绿；经 baseline 脚本复测记录增量。

## Non-Goals

- 不修改产品代码（含 JsPromise 缺陷）；不为 antlr 生成代码补测（antlr 生成物无测试价值且属生成代码）；不强求行覆盖目标。

## Scope

### In Scope

- `nop-kernel/nop-xlang/src/test/**` 新增测试与测试资源。

### Out Of Scope

- 产品代码、antlr 生成物、`.gen` 模板；nop-xlang-java/truffle 子模块（后续按基线另裁）。

## Execution Plan

### Phase 1 - 增量测试编写

Status: in progress
Targets: `nop-kernel/nop-xlang/src/test/**`

- Item Types: `Fix`

- [ ] WI0 快照 0% 靶点中选定 ≥10 个补测（按靶点过滤规则剔除 antlr 生成类/接口/抽象类/内部类）。
- [ ] xpl/xscript 求值边界语义用例 ≥10 个（内建函数边界、运算符错误路径、解析错误恢复）。
- [ ] `mvnq -- test -pl :nop-xlang -am -fae` 全绿。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 新增/扩展测试 ≥20 个用例，含显式语义断言。
- [ ] 测试全绿（既有测试零回归，以 live 为准）。
- [ ] No owner-doc update required。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 覆盖增量实测与裁定

Status: planned
Targets: `ai-dev/analysis/2026-10/`、roadmap WI3 checkbox

- Item Types: `Proof` + `Decision`

- [ ] 重建 nop-xlang 覆盖报告：删模块 `target/*.exec` → `ai-dev/tools/coverage-baseline.sh --skip-test --label wi3-2026-10-02`（与 WI0 同管线同口径），记录增量。
- [ ] 残余缺口显式裁定（语义基线口径）。
- [ ] 独立子 agent closure audit 通过后勾选 roadmap WI3 checkbox。

Exit Criteria:

- [ ] 增量数字记录在案。
- [ ] 裁定有记录。
- [ ] roadmap WI3 checkbox 与 plan/log 一致。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [ ] 全部新增测试绿（含既有测试零回归）
- [ ] 产品代码/pom 零修改（git 证据）
- [ ] 覆盖增量实测记录
- [ ] 残余缺口显式裁定（无静默降级）
- [ ] No owner-doc update required（已裁定）
- [ ] Anti-Hollow Check：新增测试断言语义（audit 抽查）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2295-unit-test-wi3-nop-xlang.md --strict` 退出码 0
- [ ] roadmap WI3 checkbox 与 plan/log 一致

## Deferred But Adjudicated

（执行结束时按实测填写）

## Non-Blocking Follow-ups

- JsPromise 错误路径三处偏离（既有审计发现）——修复立项时回归测试并入本 WI 记账。

## Closure

Status Note: （完成时填写）

Completed:

Closure Audit Evidence:

- Reviewer / Agent:（待独立子 agent closure audit 后填写）

Follow-up:

- （待填写）

## Optional Sections

- Risks And Rollback: 纯测试增量，可单独回滚。
