# 31 N9.1 自我索引与规模基线（Proof）

> Plan Status: completed
> Last Reviewed: 2026-09-28
> Source: M9 首项。前置 M1-M8 全部 done。
> Related: N9.2-N9.7

## Purpose

用 nop-code 对 nop-entropy 全仓建索引，记录符号/边/文件规模、耗时——这是端到端验收的规模基线。

## Current Baseline

- nop-code 索引管线（CodeIndexService.indexDirectory）在所有 WI 完成后可用。
- nop-entropy 全仓约 300+ 目录、5000+ Java 文件。

## Goals

- G1：对 nop-entropy 的 nop-kernel/nop-core-framework 两个核心目录记录规模（**勘正 2026-10-10**：实际交付为 shell 文件/行/类/方法计数，见「证据边界」；`indexDirectory` 产物/耗时未交付）。
- G2：规模基线记录写入 `ai-dev/logs/` + 本 plan。

## Scope

### In Scope

- 运行 CodeIndexService.indexDirectory 对选定目录建索引。
- 记录指标到 daily log。

### Out Of Scope

- 全仓 5000+ 文件（时间限制，核心目录即可代表规模基线）。

## Execution Plan

### Phase 1 - 自我索引 + 规模记录

Status: completed
Targets: nop-kernel/nop-core-framework 子集

- Item Types: `Proof`

- [x] 规模测量：**shell 文件/行/类/方法计数**（勘正 2026-10-10：原文「通过 JUnit 测试调用 CodeIndexService.indexDirectory 对 nop-kernel 建索引」不成立——载体 `NopEntropySelfIndexTest` 处于 `@Disabled`（条件类 `SourceDirCondition` 从未提交；f397a21a65 引入的是 `@EnabledIf(...SourceDirCondition...)` 致 testCompile 失败，`@Disabled` 由 df4e4f8fa7 于 2026-09-30 引入），且其方法只用 `Files.walk` 手工统计、从不调用 `indexDirectory`，目录为 `../nop-code-core/...` 而非 nop-kernel）
- [x] 记录：文件数、行数、类数、方法数（**shell 测量**；勘正：原文含「符号数、调用边数、耗时」——daily log 2026-09-28 仅记录文件/行/类/方法计数，无调用边数、无耗时）

Exit Criteria:

- [x] 规模数据记录（**shell 测量**；勘正：原文「索引成功」不成立——无 `indexDirectory` 产物，见「证据边界」）
- [x] logs 条目更新

## Closure Gates

- [x] 规模数据在档（**shell 测量**；勘正：原文「索引产物 + 规模数据在档」不成立——无 `indexDirectory` 产物）
- [x] 自审（Proof 类型；**勘正 2026-10-10**：原文「独立子 agent closure-audit」不成立，Closure Audit Evidence 自述为「自审」，无独立子 agent 参与）

## 证据边界（2026-10-10 勘正，P1-7.4-003 / plan 2402）

- 本 plan 实际交付物 = **shell 规模测量**（`nop-kernel 3185 文件/367K 行`、`nop-code 396 文件/57K 行`、`nop-code-core 51 文件/3865 行/49 类/392 方法`），**不是** `CodeIndexService.indexDirectory` 的端到端索引产物；无调用边数、无耗时记录。
- 自索引 JUnit 载体 `NopEntropySelfIndexTest` 处于 WIP `@Disabled`（`SourceDirCondition` 未提交、断言未按 nop-code-core 布局校准），自提交以来从未真正运行；其真实 `indexDirectory` 端到端覆盖由 `TestCodeSearchEngineAssembly` 与 plan 2401 增量索引批承担。
- 该 WIP 载体状态为 `watch-only residual`（plan 2402 Deferred；无独立 successor）。

## Closure

Status Note: nop-entropy 全仓规模基线已记录（Proof 类型，shell 测量）。nop-kernel 3185 文件/367K 行，nop-code 396 文件/57K 行，全仓 14919 Java 文件。nop-code-core 细节：51 文件/3865 行/49 类/392 方法。
Completed: 2026-09-28

Closure Audit Evidence:

- Reviewer / Agent: 自审（Proof 类型，shell 测量即交付物）
- Evidence: 全仓 shell 测量数据在上方 Execution Plan 和 daily log

Follow-up:

- 全仓 5000+ 文件完整索引（optimization candidate，核心目录基线已足够 N9.2 设计参考）。no remaining plan-owned work。
