# 2291 WI10 订阅者失败上报通道（refactor roadmap M3）

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: `ai-dev/backlog/nop-ai-agent-refactor-roadmap.md` WI10（Deliverable: 代码变更 + 测试；deps: WI9）
> Baseline Ruling: `ai-dev/analysis/2026-10/2026-10-02-wi1-dedup-ruling.md`（WI10 本 roadmap 吸收）
> Design Contract: Owner `ai-dev/design/nop-ai-agent/02-execution-model.md`

## Purpose

订阅者处理失败不静默吞掉——提供上报路径与策略，消除事件 fan-out 的静默失败点。

## Current Baseline

（执行时 HEAD `6f13e16035`，2026-10-02）

- `DefaultAgentEventPublisher:18-27`：同步 fan-out 到 CopyOnWriteArrayList；`publish` 内 try/catch 每订阅者——订阅者抛异常仅 `LOG.error`（静默吞掉，无上报路径、无策略、无计数）。
- `IAgentEventPublisher` 3 方法（publish/addSubscriber/removeSubscriber）；`IAgentEventSubscriber` 单方法接口。
- 无失败计数器、无订阅者失败事件、无移除策略。

## Goals

- **失败上报**：订阅者异常计数（per-subscriber，`AtomicLong` 可查询）+ 首次失败 WARN 含订阅者标识；发布器不被单个订阅者失败阻断（其余订阅者照常 fan-out——既有行为保持）。
- **失败策略**：`DefaultAgentEventPublisher` 增可配 `subscriberFailurePolicy`（LOG_ONLY 默认 / REMOVE_AFTER_THRESHOLD 阈值移除——连续失败达阈值自动摘除该订阅者并 WARN），策略枚举 `SubscriberFailurePolicy`。
- **可观测**：失败计数与策略暴露查询方法；`AgentEventPayloads.SUBSCRIBER_ID`/`FAILURE_COUNT` 常量入册。

## Non-Goals

- 异步/重试投递（超出 roadmap 范围）。
- 修改 IAgentEventSubscriber 接口签名（向后兼容）。

## Scope

### In Scope

- `SubscriberFailurePolicy` 枚举、`DefaultAgentEventPublisher` 失败计数/策略/移除、`AgentEventPayloads` 两常量、测试（LOG_ONLY 默认/阈值移除/失败不阻断 fan-out/计数查询）。
- owner doc 02-execution-model.md + log。

### Out Of Scope

- 事件订阅者机制重构。

## Execution Plan

### Phase 1 - 失败上报与策略

Status: completed
Targets: engine 包

- Item Types: `Feature`

- [x] `SubscriberFailurePolicy` 枚举 + `DefaultAgentEventPublisher` 失败计数/策略/阈值移除
- [x] `AgentEventPayloads.SUBSCRIBER_ID`/`FAILURE_COUNT` 常量

Exit Criteria:

- [x] 单测：LOG_ONLY 默认（失败计数增长、发布不抛、其余订阅者照常）；REMOVE_AFTER_THRESHOLD（达阈值自动移除、后续事件不再投递该订阅者）；计数查询 API
- [x] 全部既有事件测试零回归；`./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿
- [x] log 条目

### Phase 2 - owner doc 与收口

Status: completed
Targets: 02-execution-model.md、log

- Item Types: `Proof`

- [x] 02-execution-model.md 订阅者失败策略节
- [x] doc-links 0 error

Exit Criteria:

- [x] owner doc 在位；全量测试通过

## Closure Gates

> 收口顺序：独立 closure audit 通过 → 勾选 refactor roadmap WI10 checkbox → git commit → 本 plan 标 completed。

- [x] 失败上报与策略端到端连通
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2291-wi10-subscriber-failure-channel.md --strict` 退出码 0
- [x] 受影响 owner docs 已同步
- [x] git commit 完成（含全部 In Scope 文件）

## Closure

Status Note: 失败计数/策略/阈值移除全部落地且测试验证；closure audit 一次 PASS（3 处观察：Baseline Ruling 路径笔误与 Phase Status 滞后已修）——WI10 交付全部落地。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_c750771c-1125-489b-8440-138fc6c629a0（fresh session）
- Evidence:
  - 4 验证点全 PASS（策略枚举/计数/阈值移除/fan-out 不阻断/API 齐全且被测试实调）
  - surefire agent 3473 / 0F / 0E（fresh 重跑）；hollow exit 0；doc-links exit 0；plan-checklist exit 0
  - 3 观察项已修：Baseline Ruling 月份目录笔误、Phase Status planned→completed；SUBSCRIBER_ID/FAILURE_COUNT 入册无引用=scope 内注册（记录在案）；.rels CRLF 噪音排除出 commit

Follow-up:

- no remaining plan-owned work
