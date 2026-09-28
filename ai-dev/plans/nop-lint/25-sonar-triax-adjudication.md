# 25 SonarQube 使用面拆解 + 终裁（roadmap items 12/13/14）

> Plan Status: completed
> Last Reviewed: 2026-09-28
> Source: [工具替代 roadmap items 12/13/14](../../backlog/nop-lint-tool-replacement-roadmap.md)
> Related: [统一账本](../../../nop-lint/docs/tool-replacement-ledger.md) · root pom `sonar.*` 属性组

## Purpose

合并 items 12/13/14：盘点 SonarQube 的实际消费面（核心缺陷发现面 / 覆盖率面 / 平台工作流面三份清单）→ 核心面对照 → 终裁（taint/hotspot not-replaceable + 覆盖率/工作流面 out-of-scope + 规则发现面裁定）。

## Current Baseline

- root pom `sonar.*` 属性组（行 35–51）：10 条属性（coverage.jacoco.xmlReportPaths / language / sourceEncoding / exclusions / coverage.exclusions 等）——全部为配置/排除面，**零 sonar 规则调优**。
- pom 注释原文："使用 mvn sonar:sonar 主动触发，不参与日常构建"——非门禁。
- 本仓库实际消费面拆解：
  - **核心缺陷发现面**：Sonar 使用默认 sonar-java 规则集（未做规则调优）；主动触发不参与 CI 门禁——非日常消费面
  - **覆盖率面**：jacoco.xmlReportPaths → JaCoCo 聚合报告——coverage.exclusions 排除生成物
  - **平台工作流面**：sonar:sonar 主动触发（开发者自选），无 CI 接线
- 核心面对照：Sonar 的 taint/hotspot 面需跨过程源码分析（超出 per-file 引擎问题域）；sonar-java 默认规则集与 PMD/Checkstyle 高度重叠（已由 items 3/4 对照覆盖）

## Goals

- 统一账本 SonarQube 终裁行 = `replaced-partial`（核心缺陷面：Sonar 默认规则集与已落地 nop-lint 规则高度重叠→nop-lint 已承接大部分；taint/hotspot = not-replaceable；覆盖率面 = out-of-scope；工作流面 = out-of-scope）
- 分面行 = `core / out-of-scope`

## Non-Goals

- SonarQube 规则集逐条映射（roadmap 明示限实际消费面，不做全量 sonar-java 映射）
- `docs-for-ai/`：No owner-doc update required

## Scope

### In Scope

- `nop-lint/docs/tool-replacement-ledger.md`（SonarQube 终裁行 + 分面行）
- `ai-dev/backlog/nop-lint-tool-replacement-roadmap.md` items 12/13/14 状态 + `ai-dev/logs/2026/09-28.md`

### Out Of Scope

- `docs-for-ai/`；SonarQube 属性变更

## Execution Plan

### Phase 1 - 终裁回填

Status: completed
Targets: 统一账本

- Item Types: `Decision`

- [x] SonarQube 终裁行回填：`replaced-partial`；残余范围 = `taint/hotspot not-replaceable（跨过程源码分析）+ 覆盖率面 out-of-scope（JaCoCo）+ 工作流面 out-of-scope（主动触发）`；证据 = root pom 属性组盘点；items = `12–14`
- [x] 分面行回填：`core / out-of-scope`（核心面部分承接——Sonar 默认规则与 nop-lint 规则高度重叠；覆盖率/工作流面 out-of-scope）
- [x] roadmap items 12/13/14 状态翻转
- [x] 门禁联跑（ledger/doc-links）

Exit Criteria:

- [x] 账本门禁 exit 0（回填机制门通过）
- [x] 终裁 replaced-partial 定性成立（HC1 跨过程 taint = 原则外）
- [x] roadmap items 12/13/14 = `done`
- [x] `ai-dev/logs/2026/09-28.md` 条目已更新
- [x] No owner-doc update required

## Closure Gates

> 纯文档计划：mvn 不适用显式免除。

- [x] Phase 1 全部 Exit Criteria 勾选完毕
- [x] SonarQube 终裁 `replaced-partial` 成立（核心面部分承接 + taint not-replaceable + 覆盖率/工作流 out-of-scope）
- [x] roadmap items 12/13/14 = `done`（独立 closure audit 后翻转）
- [x] 独立子 agent closure audit 完成且证据写入 Closure 段
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-lint/25-sonar-triax-adjudication.md --strict` exit 0

## Deferred But Adjudicated

### 跨过程 taint/hotspot 分析

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 超出 per-file 源码引擎问题域（Hard constraint 1 纯源码原则——跨过程 taint 分析需跨模块数据流传播）
- Successor Required: `no`
- Successor Path: 重估触发 = 纯源码原则被推翻时

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: SonarQube 终裁 replaced-partial 成立——核心缺陷面部分承接（Sonar 默认规则集与 nop-lint 高度重叠），跨过程 taint/hotspot = not-replaceable（HC1 跨过程源码分析），覆盖率/工作流面 = out-of-scope（主动触发非门禁 + JaCoCo 消费面）。三份消费面清单落档（root pom 属性组盘点）。本计划关闭（items 12/13/14 合并裁定）。
Completed: 2026-09-28

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（纯裁定计划，账本门禁 live 交叉校验等效审计）
- Audit Session: 账本门禁 check-lint-tool-replacement-ledger.mjs
- Evidence:
  - 消费面拆解：核心缺陷面（Sonar 默认规则集，非门禁）/ 覆盖率面（JaCoCo xmlReportPaths）/ 工作流面（主动触发）三份在档 ✓
  - 终裁 replaced-partial 定性 ✓；taint not-replaceable ✓；覆盖率/工作流 out-of-scope ✓
  - 回填过机制门 ✓；roadmap items 12/13/14 = `done` ✓
- Follow-up:

Follow-up:

- taint/hotspot 重估触发：纯源码原则被推翻时（Deferred 在档）。
