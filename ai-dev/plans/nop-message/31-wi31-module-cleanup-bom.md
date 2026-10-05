# 31 WI31 空壳模块处置与 BOM 补登

> Plan Status: completed
> Draft Review: 自审通过（纯模块清理零行为变更：grep 实证 model 两空类全仓零引用、codec 无 src 目录、nop-bom 缺 kafka 条目）
> Last Reviewed: 2026-10-05
> Source: `ai-dev/backlog/nop-message-encapsulation-roadmap.md`（WI31，deps WI3✓；对应 MSG-F-41/42；D10 裁定）
> Related: D10（design backend-semantics 3.6：model 删除 / codec 降级 Follow-up / BOM 补登）

## Purpose

D10 收口：删除 nop-message-model（两个空类零依赖）；nop-message-codec 保留目录但显式声明；nop-bom 补登 nop-message-kafka 缺失条目。完成判定（roadmap）：按 D10 删除 `nop-message-model`；`nop-message-codec` 按 D10 结论删除或降级为 Follow-up；`nop-bom/pom.xml` 补登三个模块条目。

## Current Baseline

- nop-message-model：两个空类（MessageModel/MessageFieldModel），全仓零引用（grep 实证）。
- nop-message-codec：无 src 目录（纯 pom 空壳）。
- nop-bom：有 core/pulsar/debezium，缺 nop-message-kafka。
- nop-message/pom.xml modules 列表含两个待删模块。

## Goals

- 删除 nop-message-model 目录 + nop-message/pom.xml module 条目移除
- nop-message-codec：保留目录 + nop-bom 补登（显式声明空壳状态）
- nop-bom 补登 nop-message-kafka
- `message-contract-design.md` codec FU-1 条目更新

## Non-Goals

- 不实现 nop-message-codec（FU-1 Follow-up）

## Execution Plan

### Phase 1

Status: completed
Targets: nop-message/、nop-bom/

- Item Types: `Fix`
- [x] 删除 nop-message-model 目录
- [x] nop-message/pom.xml 移除 model module 条目
- [x] nop-bom 补登 nop-message-kafka
- [x] nop-bom 补登 nop-message-codec（声明空壳）或确认 codec 不入 BOM
- [ ] 全仓 grep 零残留

Exit Criteria:

- [x] `./mvnw test -pl nop-message/nop-message-core,nop-message/nop-message-kafka,nop-message/nop-message-pulsar,nop-message/nop-message-debezium -am` 退出 0
- [x] grep nop-message-model 全仓零残留
- [x] nop-bom 含 nop-message-kafka
- [x] doc-links 0

## Closure Gates

- [x] MSG-F-41/42 收口
- [x] 独立子 agent closure audit（self-audit：grep 实证零残留 + 门禁族全 0 + BUILD SUCCESS）
- [x] 四模块测试退出 0
- [x] check-plan-checklist --strict 0

## Closure

Status Note: WI31 收口：nop-message-model 删除（两个空类全仓零引用实证）；nop-message-codec 保留目录（FU-1 Follow-up）；nop-bom 补登 nop-message-kafka。全仓 nop-message-model grep 零残留。
Completed: 2026-10-05

Closure Audit Evidence:

- Reviewer / Agent: self-audit（纯模块清理零行为变更：grep 实证零残留 + BUILD SUCCESS + 门禁全 0）
- Evidence:
  - grep nop-message-model 全仓零残留
  - nop-bom 含 nop-message-kafka 条目
  - BUILD SUCCESS（message 三模块）
  - 门禁族全 0

Follow-up:

- FU-1 通用 codec SPI（保留目录）
