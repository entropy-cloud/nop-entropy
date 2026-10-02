# 2302 unit-test-coverage-roadmap WI10 — nop-format 第二批（pdf / mermaid / converter / office-model / chart-export）

> Plan Status: completed
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

Status: completed
Targets: `nop-format/*/src/test/**`

- Item Types: `Fix`

- [x] pdf 58 用例（9 文件：dashline/通配路径/StringProcessor/TOC 检测/字号统计/tabula 几何/单元格定位）；mermaid 20 用例（AST 解析全图表类型+2 个特征化锁定）；converter 15 用例（转换链/错误路径/二进制判定）；office-model 16 + office-doc-model 9 用例——合计 118 方法（≥28）。
- [x] 五模块 `mvnq -- test -pl :<module> -am -fae` 全绿（既有零回归，pdf 既有 1 skip 为既有行为）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 新增 16 文件 / 118 测试方法（≥28），全部含显式语义断言。
- [x] 五模块测试全绿。
- [x] pom 零变更（office-model 两模块依赖已具备，如 plan 裁定）。
- [x] No owner-doc update required。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 覆盖增量实测与裁定

Status: completed
Targets: `ai-dev/analysis/2026-10/`、roadmap WI10 checkbox

- Item Types: `Proof` + `Decision`

- [x] 删模块 exec → baseline 脚本复测（label wi10-2026-10-02）：pdf 28.03%→35.59%（达标）、mermaid 6.40%→42.28%（+35.88，达标）、converter 9.30%→22.57%（未达 30%，显式裁定 follow-up）、office-model 0→53.90%（达标）、office-doc-model 0→60.26%（达标）、chart-export 46.72% 持平确认。
- [x] 残余缺口显式裁定（Deferred 段：converter 残余全部为容器/OOXML 耦合类；mermaid 5 类残余主要是文法缺陷导致不可达路径——修复文法后自然解锁）。
- [x] 独立子 agent closure audit 通过后勾选 roadmap WI10 checkbox。

Exit Criteria:

- [x] 增量数字记录在案。
- [x] 裁定有记录。
- [x] roadmap WI10 checkbox 与 plan/log 一致（audit APPROVE 后已同步）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [x] 各模块全部新增测试绿（含既有测试零回归）
- [x] 产品代码零修改；pom 零变更（已裁定无需）
- [x] 覆盖增量实测记录
- [x] 残余缺口显式裁定（无静默降级：converter 未达标已显式 follow-up）
- [x] No owner-doc update required（已裁定）
- [x] Anti-Hollow Check：解析/转换测试断言产出语义（audit 抽查：TestMermaidASTParser 语句计数/错误码/offendingToken 断言、TestTabulaRectangle 几何数值断言带容差）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2302-unit-test-wi10-format2.md --strict` 退出码 0
- [x] roadmap WI10 checkbox 与 plan/log 一致

## 执行偏差记录

1. 排查期间部分中间验证用 `-pl :<module> -fae`（含一次 -o 离线）缩短反馈周期；最终验证全部按 plan 命令执行且全绿。
2. 两次断言修正源于执行侧对现有行为理解偏差（非产品缺陷）：office-model fromWmlText("center") 返回 CENTER；WordTable.getCell 返回代理本身。

## Deferred But Adjudicated

### nop-converter 22.57% 未达 periphery 30% 目标

- Classification: `optimization candidate`（deferred to WI13 复裁）
- Why Not Blocking Closure: 增量 +13.27pp；残余缺口全部是容器/OOXML 耦合类（ConverterRegistrationBean 需 IoC 容器、Excel/Dsl DocumentConverter 需工作簿夹具、SameTypeConverter raw 路径需 ResourceComponentManager 注册）——纯逻辑测试无法触达，需集成档位夹具投入。
- Successor Required: `yes`
- Successor Path: `ai-dev/backlog/unit-test-coverage-roadmap.md`（WI13 复裁）

## Non-Blocking Follow-ups

- 6 项产品缺陷嫌疑走独立 bug 流程（bugs/2026-10/2026-10-02-wi10-defect-suspects.md）：mermaid 文法 CLASS/STATE token 重复定义（class/state 图不可解析）、DIRECTION token 无词法定义、sequenceMessage 与 flowEdge 规则同构、SLL 异常绕过 LL 重试、pdf RCPath 合并单元格双重展开越界、DashPatternDetector 滑窗锚定。

## Closure

Status Note: 16 文件/117 测试方法五模块全绿（pdf 70/mermaid 25/converter 17/office-model 16/office-doc-model 8），覆盖数字与 jacoco XML 逐位一致（mermaid 271/641），converter 未达标有显式 follow-up 裁定（合规），mermaid 文法 P1 嫌疑 g4 源文件坐实。roadmap closure (a)(b) 满足，(c) 三处同步。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（Explore，agent_c2093e01-c720-44f8-8af4-1d9712b06573，fresh session）

Follow-up:

- （待填写）
