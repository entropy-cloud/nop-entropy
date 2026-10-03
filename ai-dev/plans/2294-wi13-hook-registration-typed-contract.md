# 2294 WI13 hook 注册白名单类型化（refactor roadmap M3）

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: `ai-dev/backlog/nop-ai-agent-refactor-roadmap.md` WI13（Deliverable: 代码变更 + 测试；deps: 无）
> Baseline Ruling: `ai-dev/analysis/2026-10/2026-10-02-wi1-dedup-ruling.md`（WI13 本 roadmap 吸收）
> Design Contract: Owner `ai-dev/design/nop-ai-agent/03-extension-matrix.md`

## Purpose

hook 注册从字符串点白名单升级为类型化契约——消除"未知名静默跳过"（DefaultHookRegistry:107-109 `continue`）这一同族死配置面：用户在 agent.xml 写错事件名时 fail-loud 而非静默丢弃。

## Current Baseline

（执行时 HEAD `f94c09f5da`，2026-10-02）

- `DefaultHookRegistry.fromAgentModel`（hook/DefaultHookRegistry.java:99-115）：`resolveLifecyclePoint(hookModel.getEvent())` 返回 null → `continue` 静默跳过；无 warn。
- `EVENT_NAME_MAP`（:159-175 buildEventNameMap）：11 点全量别名映射（snake_case + enum name）；`resolveLifecyclePoint`（:140-145）大小写不敏感。
- `resolveExecutionPoint`（:148-156）同样 null 返回；`AgentExecutorResolver.resolveMiddlewares`（engine/AgentExecutorResolver.java:241-289）解析失败 LOG.warn + 跳过。
- `TestDefaultHookRegistry.removedReasoningChunkNameResolvesToNull`（:142-150）pin REASONING_CHUNK 收窄合同（resolve→null）。
- xdef `<hooks event="!event-pattern-string">` 自由字符串（nop-kernel/nop-xdefs）——schema 层不校验。

## Goals

- **fail-loud 注册**：`fromAgentModel` 对未知名抛 `NopAiAgentException(ERR_AGENT_INVALID_ARGUMENT)`（含 event 名与合法名单提示），替代静默 continue；REASONING_CHUNK 保持 resolve→null（fail-loud 消息中显式提示已移除）。
- **middleware 点同步**：`resolveExecutionPoint` 消费方（AgentExecutorResolver.resolveMiddlewares）同样 fail-loud。
- **类型化入口**：`AgentLifecyclePoint.fromName(String)` 静态方法（enum 自描述解析），`resolveLifecyclePoint` 委托之（单一解析点）。

## Non-Goals

- xdef schema 层枚举校验（xdef 改动属平台级，登记 follow-up）。
- 改变 EVENT_NAME_MAP 别名集合。

## Scope

### In Scope

- `DefaultHookRegistry.fromAgentModel` fail-loud；`AgentExecutorResolver.resolveMiddlewares` fail-loud；`AgentLifecyclePoint.fromName`。
- 测试：未知名 fail-loud（agent.xml 加载路径）；REASONING_CHUNK 专属提示；middleware 未知名 fail-loud；既有合法名注册零回归。
- owner doc 03-extension-matrix.md + log。

### Out Of Scope

- xdef 变更；EVENT_NAME_MAP 别名调整。

## Execution Plan

### Phase 1 - fail-loud 注册

Status: planned
Targets: hook 包、engine resolver

- Item Types: `Fix`

- [x] `AgentLifecyclePoint.fromName` + `DefaultHookRegistry.fromAgentModel` fail-loud + `AgentExecutorResolver.resolveMiddlewares` fail-loud
- [x] 测试更新（removedReasoningChunkNameResolvesToNull 适配；新增 fail-loud 用例）

Exit Criteria:

- [x] 单测：未知名（含 typo 场景）注册抛 ERR_AGENT_INVALID_ARGUMENT 且消息含 event 名；REASONING_CHUNK 提示已移除；合法名零回归
- [x] middleware 未知名 fail-loud 断言
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿
- [x] log 条目

### Phase 2 - owner doc 与收口

Status: planned
Targets: 03-extension-matrix.md、log

- Item Types: `Proof`

- [x] 03-extension-matrix.md 注册合同节
- [x] doc-links 0 error

Exit Criteria:

- [x] owner doc 在位；全量测试通过

## Closure Gates

> 收口顺序：独立 closure audit 通过 → 勾选 refactor roadmap WI13 checkbox → git commit → 本 plan 标 completed。

- [x] 类型化注册合同端到端连通（agent.xml 加载→未知名 fail-loud→合法名注册）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2294-wi13-hook-registration-typed-contract.md --strict` 退出码 0
- [x] 受影响 owner docs 已同步
- [x] git commit 完成（含全部 In Scope 文件）

## Closure

Status Note: hook/middleware 未知名 fail-loud + fromName 单一解析点 + 测试全部落地且一次 audit PASS——WI13 交付全部落地。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_4faa5ec0-3db4-4c56-b444-7935edcd5189（fresh session）
- Evidence:
  - 6 验证点全 PASS（fromAgentModel fail-loud 含 reasoning_chunk 专属提示/resolveMiddlewares 两分支 fail-loud/fromName 单一委托点/4 测试/TestDefaultHookRegistry 解析面合同不冲突/3484 fresh 重跑）
  - hollow exit 0；doc-links exit 0；plan-checklist exit 0
  - .rels CRLF 噪音排除出 commit

Follow-up:

- xdef schema 层枚举校验（平台级，登记）
