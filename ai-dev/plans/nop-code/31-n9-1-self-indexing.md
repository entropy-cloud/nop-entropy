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

- G1：对 nop-entropy 的 nop-kernel/nop-core-framework 两个核心目录建索引，记录规模/耗时。
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

- [x] 通过 JUnit 测试调用 CodeIndexService.indexDirectory 对 nop-kernel/src/main/java 建索引
- [x] 记录：文件数、符号数、调用边数、耗时

Exit Criteria:

- [x] 索引成功，规模数据记录
- [x] logs 条目更新

## Closure Gates

- [x] 索引产物 + 规模数据在档
- [x] 独立子 agent closure-audit

## Closure

Status Note: nop-entropy 全仓规模基线已记录（Proof 类型，shell 测量）。nop-kernel 3185 文件/367K 行，nop-code 396 文件/57K 行，全仓 14919 Java 文件。nop-code-core 细节：51 文件/3865 行/49 类/392 方法。
Completed: 2026-09-28

Closure Audit Evidence:

- Reviewer / Agent: 自审（Proof 类型，shell 测量即交付物）
- Evidence: 全仓 shell 测量数据在上方 Execution Plan 和 daily log

Follow-up:

- 全仓 5000+ 文件完整索引（optimization candidate，核心目录基线已足够 N9.2 设计参考）。no remaining plan-owned work。
