# 2295 unit-test-coverage-roadmap WI3 — nop-xlang 补强

> Plan Status: completed
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

- [x] WI0 快照 0% 靶点补测 11 个（DeltaDiffer/XDslCleaner/XSchemaToJsonSchema/BizActionGenHelper/ExpressionToFilterBeanTransformer/JavaToXLangTransformer/PrintResolvedIdentifier/ObjXmlValueHelper/ReflectObjMetaParser/VarExecutableFunction/BetweenOpExecutable，全部退出低覆盖名单）。
- [x] xpl/xscript 求值边界语义用例 31 个（TestEvalBoundarySemantics 18 + TestXplBoundarySemantics 13：内建函数边界、运算符错误路径、解析错误恢复、c:unit outputMode 语义发现）。
- [x] `mvnq -- test -pl :nop-xlang` 855 tests 全绿（0F/0E，既有 skip 2）；`-am` 链经 CLI 排除 8 个并行工作流外来红类后退出码 0（偏差 1，nop-xlang 自身不受影响）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 新增 13 测试类 + 1 测试资源，共 123 用例，全部含显式语义断言。
- [x] 测试全绿（855 tests，既有零回归）。
- [x] No owner-doc update required。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 覆盖增量实测与裁定

Status: completed
Targets: `ai-dev/analysis/2026-10/`、roadmap WI3 checkbox

- Item Types: `Proof` + `Decision`

- [x] 重建 nop-xlang 覆盖报告（同管线同口径，分母含 antlr 生成类）：行覆盖 47.70%→49.73%（+2.03pp），分支 35.33%→37.37%；快照 coverage-baseline-wi3-2026-10-02.json；lowCoverageClasses 41→30。
- [x] 残余缺口显式裁定：antlr 生成类 4 + 接口 5 + 内部/抽象 4 按过滤规则永久剔除；可测残余 17 类（XLangASTOptimizer 1386L 等）显式裁定保留给后继 WI（golden 快照模式适配 AST 打印/优化器）。
- [x] 独立子 agent closure audit 通过后勾选 roadmap WI3 checkbox。

Exit Criteria:

- [x] 增量数字记录在案。
- [x] 裁定有记录。
- [x] roadmap WI3 checkbox 与 plan/log 一致（audit APPROVE 后已同步）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [x] 全部新增测试绿（含既有测试零回归：855/0F/0E）
- [x] 产品代码/pom 零修改（git 证据：仅 src/test 新增）
- [x] 覆盖增量实测记录（47.70%→49.73%）
- [x] 残余缺口显式裁定（无静默降级：/antlr/接口/内部类剔除、可测残余 17 类留给后继）
- [x] No owner-doc update required（已裁定）
- [x] Anti-Hollow Check：新增测试断言语义（audit 抽查：TestDeltaDiffer 错误码/结构断言、TestXplBoundarySemantics c:for index/转义/return 值断言）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2295-unit-test-wi3-nop-xlang.md --strict` 退出码 0
- [x] roadmap WI3 checkbox 与 plan/log 一致

## 执行偏差记录

1. `-am` 链外来红：工作区存在并行 WI1/WI2 执行器的进行中未跟踪测试（nop-commons 2 类 + nop-core 6 类），按"只新增 nop-xlang/src/test"约束不可修，采用 CLI 排除（`-Dtest='!…' -Dsurefire.failIfNoSpecifiedTests=false`）使 -am 链退出码 0；nop-xlang 单独跑无任何排除即全绿。WI1/WI2 最终门均已复核全绿（时序性中间态，非真实回归）。
2. xdef 测试资源新增 1 个（`src/test/resources/_vfs/test/wi3-clean.xdef`，测试资源范围）。

## Deferred But Adjudicated

### nop-xlang 残余低覆盖类（30 个）

- Classification: `optimization candidate`（后继 WI 靶点池）
- Why Not Blocking Closure: 语义基线口径下本 WI 已补 123 用例并实测 +2.03pp；残余 30 类中 13 个为 antlr 生成类/接口/内部抽象类（按过滤规则永久剔除），17 个可测类显式裁定保留——XLangASTOptimizer(1386L)/XLangExpressionPrinter(350L) 等适合 golden 快照模式，归入 WI13 复裁与后继靶点池。
- Successor Required: `yes`
- Successor Path: `ai-dev/backlog/unit-test-coverage-roadmap.md`（WI13 复裁）

## Non-Blocking Follow-ups

- 5 项产品缺陷嫌疑走独立 bug 流程（bugs/2026-10/2026-10-02-wi3-defect-suspects.md）：ExpressionToFilterBeanTransformer 常量在左语义反转、CallExpression.getArgument 越界、XSchemaToJsonSchema union 分支、JavaToXLangTransformer 字段丢失（janino 3.1.12）、JsPromise 三处（既有审计项，现有 TestJsPromise 可作回归基线）。

## Closure

Status Note: 13 测试类+1 资源入库、855/0F/0E、49.73%/44949 与 jacoco.xml 逐位一致（分母同 WI0 口径）、41→30 名单变动与靶点退出（82%/89%/71%/87%）可验证、缺陷嫌疑经源码逐字核实（transformBinary reverseOp 死计算）、残余 30 类裁定自洽。roadmap closure (a)(b) 满足，(c) 三处同步。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（Explore，agent_e94518f4-9630-464b-92ed-03424392e594，fresh session）

## Optional Sections

- Risks And Rollback: 纯测试增量，可单独回滚。
