# 2290 WI9 AgentEvent payload 类型化 + ignorable 兼容（refactor roadmap M3）

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: `ai-dev/backlog/nop-ai-agent-refactor-roadmap.md` WI9（Deliverable: 代码变更 + 测试；deps: 无）
> Baseline Ruling: `ai-dev/analysis/2026-10/2026-10-02-wi1-dedup-ruling.md`（WI9 本 roadmap 吸收；与 autonomous WI3 接触面=事件契约，WI3 消费按类型化契约挂数据项）
> Design Contract: Owner `ai-dev/design/nop-ai-agent/02-execution-model.md`

## Purpose

事件 payload 判别类型化 + 消费者可声明跳过未知事件的兼容契约——消除"消费者只能强转 Map"的治理债，为下游消费（观测/UI 桥接未来形态）提供类型化契约。

## Current Baseline

（执行时 HEAD `90a536fe4d`）

- `AgentEvent`（engine/AgentEvent.java:6-58）：type/sessionId/agentName/`Map<String,Object> payload`——消费者拿 Map 后按约定 key 强转取值，无类型契约。
- `AgentEventType` 22 值（含 WI8 新增 INJECTION_QUEUE_UPDATED）；发布点经 `AgentEvent.create(type, sessionId, agentName, payload)`。
- `IAgentEventPublisher` 3 方法（publish/addSubscriber/removeSubscriber）；订阅者自解析 payload Map。
- WI7/WI8 已建立 payload 约定 key（semantics/action/pendingCount/idempotencyKey 等）散落在发布点。
- 测试：TestAgentEventPublisher 等。

## Goals

- **typed payload 判别**：新增 `AgentEventPayloads`（常量 key 契约类：全事件 payload key 的权威常量 + javadoc 契约），`AgentEvent` 增加 `typedPayload()` 类型化视图（不做破坏性变更——`getPayload()` 保持，typed 视图提供 key-常量安全访问与类型转换助手）。
- **ignorable 兼容契约**：`AgentEvent` 增加 `schemaVersion` 字段（默认 1）；`IAgentEventSubscriber` javadoc 契约——订阅者遇到未知字段/事件时按 schemaVersion 判断跳过（向前兼容）；`DefaultAgentEventPublisher` 发布时自动打版本。
- **发布点 key 统一**：全仓 publish 调用点的 payload key 改引 `AgentEventPayloads` 常量（编译期防拼写漂移）。

## Non-Goals

- 不改变 payload 的序列化形态（仍是 Map——typed 视图是读取助手，非替代）。
- 不新增事件类型（WI8 已加 INJECTION_QUEUE_UPDATED）。

## Scope

### In Scope

- `AgentEventPayloads` 常量类；`AgentEvent.schemaVersion` + `typedPayload()`；发布点 key 常量化；测试（typed 视图/缺 key 行为/schemaVersion 默认与传递）。
- owner doc 02-execution-model.md 事件契约节 + log。

### Out Of Scope

- WI10（订阅者失败上报通道，独立项）。

## Execution Plan

### Phase 1 - 类型化实现

Status: planned
Targets: engine 包

- Item Types: `Feature`

- [x] `AgentEventPayloads` 常量类（closure audit 两轮后补全至 45 key 覆盖全部已观测 payload key（含 inline Map.of 形式与 security/conflict 族）；CALL_ID 无发布点使用已移除）
- [x] `AgentEvent.schemaVersion`（默认 1，create 重载传递）+ `typedPayload()` 读取助手（实现命名，与 owner doc/测试一致）
- [x] 全仓 publish 调用点 key 常量化（closure audit 两轮补齐：HashMap-builder 与 inline Map.of 两形式、8 文件 55+ 处转换，含 security/conflict 族 24 处）

Exit Criteria:

- [x] 单测：typed 视图取值/缺 key 返回 null 不抛/schemaVersion 默认值与显式传递
- [x] 全部既有事件测试零回归；`./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿
- [x] log 条目

### Phase 2 - owner doc 与收口

Status: planned
Targets: 02-execution-model.md、log

- Item Types: `Proof`

- [x] 02-execution-model.md 事件契约节（schemaVersion/ignorable/typed 视图）
- [x] doc-links 0 error

Exit Criteria:

- [x] owner doc 在位；全量测试通过

## Closure Gates

> 收口顺序：独立 closure audit 通过 → 勾选 refactor roadmap WI9 checkbox → git commit → 本 plan 标 completed。

- [x] typed payload + ignorable 契约端到端连通
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2290-wi9-event-payload-typing.md --strict` 退出码 0
- [x] 受影响 owner docs 已同步
- [x] git commit 完成（含全部 In Scope 文件）

## Closure

Status Note: typed 视图/schemaVersion ignorable 契约/45 key 全入册/双形式（HashMap-builder + inline Map.of）raw key 全仓清零全部落地；closure audit 三轮——round1 FAIL 常量化不完全、round2 FAIL inline 形式 9 处+10 key、round3 唯一残余 plan 两行 stale 命名已改——WI9 交付全部落地。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_a57875d4-86a1-4fd7-b826-5197bd19929b（fresh session，三轮）
- Evidence:
  - round1 FAIL：HashMap-builder 形式 31 处已转换但 8/15 常量零引用、19 key 未入册
  - round2 FAIL：inline Map.of 形式 9 处残留 + 10 key 未入册（首轮 grep 模式过窄漏检）
  - round3：双模式扫描清零（payload.put + Map.of 两形式）、45 key 全入册、9 处 inline 逐点验证、owner doc 规范句与 live 一致——唯 plan L25/L38 stale 命名 payloadTyped→typedPayload 两行修复后全 PASS
  - surefire agent 3470 / 0F / 0E；hollow scan exit 0；doc-links exit 0；java-lint 0 error
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2290-wi9-event-payload-typing.md --strict` exit 0（completed 后 0 failed）

Follow-up:

- no remaining plan-owned work
