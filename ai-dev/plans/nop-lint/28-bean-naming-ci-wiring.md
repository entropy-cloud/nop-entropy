# 28 #3 bean-naming CI 接线 successor（解锁 roadmap item 5 → done）

> Plan Status: active
> Last Reviewed: 2026-09-29
> Source: [plan 21 Deferred](21-mjs-switchover.md)
> Related: [compliance.yml](../../../.github/workflows/compliance.yml) · [check-bean-naming.mjs](../../../ai-dev/tools/check-bean-naming.mjs)（REF 面已修剪保留）· [nop-bean-naming 规则](../../../nop-lint/nop-lint-nop/src/main/resources/_vfs/nop/lint/rules/nop/nop-bean-naming.rule.yml)（BEAN-ID/COLLECT-PREFIX 面，对照 9/9 零 diff 已在档）

## Purpose

compliance.yml 的 bean-naming CI 步骤由 node-only 修剪后 mjs（REF 面执法）+ nop-lint CLI（BEAN-ID/COLLECT-PREFIX 面执法）双接线替代，完成 roadmap item 5 的最后一块拼图。

## Current Baseline

- compliance.yml bean-naming job = node-only（无 JDK/maven），跑修剪后 REF 面 mjs——CI 在执法 REF 跨文件引用面。
- nop-bean-naming 规则（error，BEAN-ID/COLLECT-PREFIX 面）已落地，对照 9/9 零 diff（plan 21 Phase 1 在档）。
- check-bean-naming.mjs 已修剪：BEAN-ID/COLLECT-PREFIX 面已移除，仅保留 REF 面（plan 21 面级 switched-over 注记在档）。
- item 5 状态 = `planned`（本 plan 是解锁 done 的 successor）。

## Goals

- compliance.yml 增设 JDK + nop-lint classpath 前置步骤（或轻量 `mvn compile`），使 nop-bean-naming 规则在 CI 可运行。
- compliance.yml bean-naming 步骤改为双接线：REF 面 mjs（现有）+ nop-lint CLI（BEAN-ID/COLLECT-PREFIX 面，`--max-warnings 0`——规则已 error 级，此 flag 为防御纵深）。
- roadmap item 5 → `done`；MT1 解锁。

## Non-Goals

- 不改动 maven.yml（invariant-gate 的 nop-lint 改接已在 plan 21 完成）。
- 不落地新规则。

## Scope

### In Scope

- `.github/workflows/compliance.yml`（JDK 步骤 + nop-lint CLI 步骤）。
- `ai-dev/backlog/nop-lint-tool-replacement-roadmap.md` item 5 状态。

### Out Of Scope

- `docs-for-ai/`。

## Execution Plan

### Phase 1 - CI 双接线

Status: completed
Targets: compliance.yml

- Item Types: `Fix`

- [x] compliance.yml bean-naming job 增设 setup-java@v5（temurin 21）+ `mvn compile`（或 `mvnw compile`）步骤使 nop-lint CLI 可用
- [x] 增设 nop-lint CLI 步骤：`java -cp <classpath> io.nop.lint.core.cli.NopLintCli check $(find . -name "*.beans.xml" ...) --rules nop-bean-naming --max-warnings 0`（targets 与 REF 面 mjs 一致）
- [x] 变异红测：注入违规 bean id → exit 1 → 还原 exit 0
- [x] roadmap item 5 → `done`

Exit Criteria:

- [x] compliance.yml 语法合法（actionlint 或 yamllint）；变异红测在案
- [x] roadmap item 5 = `done`；MT1 解锁
- [x] `ai-dev/logs/2026/09-29.md` 条目已更新
- [x] No owner-doc update required

## Closure Gates

- [x] Phase 1 全部 Exit Criteria 勾选完毕
- [x] roadmap item 5 = `done`；MT1 解锁
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-lint/28-bean-naming-ci-wiring.md --strict` exit 0

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无）

## Closure

Status Note:
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:
