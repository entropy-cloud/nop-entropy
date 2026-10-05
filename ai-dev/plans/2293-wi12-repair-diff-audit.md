# 2293 WI12 修复前后差异审计（refactor roadmap M3）

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: `ai-dev/backlog/nop-ai-agent-refactor-roadmap.md` WI12（Deliverable: 代码变更 + 测试；deps: 无）
> Baseline Ruling: `ai-dev/analysis/2026-10/2026-10-02-wi1-dedup-ruling.md`（WI12 本 roadmap 吸收）
> Design Contract: Owner `ai-dev/design/nop-ai-agent/04-tool-invocation.md`

## Purpose

IToolCallRepairer 修复链输出修复前后参数差异记录（可观测、可审计），与 7-checkpoint 安全链的"审批所见 vs 执行所用"一致性对齐。

## Current Baseline

（执行时 HEAD `fffc9112b6`，2026-10-02 考察锚点；roadmap 原锚点 :1176 已漂移至 :1626）

- 接口：`IToolCallRepairer.repair(ChatToolCall, AgentExecutionContext): ChatToolCall`（repair/IToolCallRepairer.java:8）——replace 语义非 mutate（ChainRepairer:74-80 顺序 4 stage，全部 copy-on-change）。实现：NoOpToolCallRepairer/ChainRepairer + 4 stage。
- 唯一调用点：`ReActAgentExecutor:1626`（runToolFanout dispatchLoop 内）——repair 前=LLM 原始输出（循环变量），repair 后=返回值重赋。
- **可观测缺口**：修复前原始 LLM 输出完全无记录；TOOL_CALL_STARTED payload 仅 TOOL_NAME/ITERATION（:1630-1632），无 callId、无修复差异。
- **一致性现状**：审批所见（CheckContext.create :1637 收 repair 后 call）=执行所用（AiToolCall 构造 :640-645 同一 call）——已一致；缺的是修复前记录。
- **发现缺口**：heartbeat wakeup-turn 派生调用路径（`adjudicateHeartbeatReply` → addCallsToPhase）**不经过 repair**——WI12 需裁定补齐或豁免。
- 事件句柄：dispatch loop 手上只有 hookInvoker（auditLogger 不在 executor 字段——走事件通道爆炸半径小）；payload 契约前向兼容（WI9）。

## Goals

- **修复差异记录**：TOOL_CALL_STARTED payload 扩展——`callId`（join key）、变更时 `repairApplied=true` + `nameBefore`/`nameAfter` + `argsBefore`/`argsAfter`（getArgumentsText JSON，截断防护 512 字符）。变更检测 = 引用不同兜底 + name/argsText 字符串比较。
- **callId 补齐**：TOOL_CALL_STARTED payload 增加 `callId`（audit 关联 join key）。
- **heartbeat 派生路径裁定**：补齐 repair（adjudicateHeartbeatReply 的 derivedCalls 逐 call 过 repairer，与主路径语义一致）。
- **payloads 契约**：`AgentEventPayloads` 增 CALL_ID（恢复）/REPAIR_APPLIED/NAME_BEFORE/NAME_AFTER/ARGS_BEFORE/ARGS_AFTER。

## Non-Goals

- audit logger 通道（executor 无句柄，爆炸半径大——登记 follow-up）。
- IToolCallRepairer 签名变更（diff 在调用点计算）。
- gateway 侧接线。

## Scope

### In Scope

- `ReActAgentExecutor` runToolFanout：变更检测 + payload 扩展；`adjudicateHeartbeatReply`：派生调用补 repair。
- `AgentEventPayloads` 六常量；测试（无修复零记录/有修复全字段/heartbeat 路径 repair）。
- owner doc 04-tool-invocation.md + log。

### Out Of Scope

- audit logger 通道；IToolCallRepairer 签名变更。

## Execution Plan

### Phase 1 - 修复差异记录

Status: planned
Targets: ReActAgentExecutor、AgentEventPayloads

- Item Types: `Feature`

- [x] 变更检测 helper（before/after name+argsText 比较）+ TOOL_CALL_STARTED payload 扩展 + callId
- [x] heartbeat 派生调用补 repair（adjudicateHeartbeatReply）
- [x] payloads 六常量

Exit Criteria:

- [x] 集成测试：①repair 无变更 → repairApplied 缺席；②repair 有变更 → repairApplied/name/args 全字段断言；③heartbeat 派生调用经 repair（修复后参数到达 AiToolCall）
- [x] 既有 repair 测试零回归；`./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿
- [x] log 条目

### Phase 2 - owner doc 与收口

Status: planned
Targets: 04-tool-invocation.md、log

- Item Types: `Proof`

- [x] 04-tool-invocation.md 修复差异审计节
- [x] doc-links 0 error

Exit Criteria:

- [x] owner doc 在位；全量测试通过

## Closure Gates

> 收口顺序：独立 closure audit 通过 → 勾选 refactor roadmap WI12 checkbox → git commit → 本 plan 标 completed。

- [x] 修复差异记录端到端连通（repair→变更检测→事件 payload）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2293-wi12-repair-diff-audit.md --strict` 退出码 0
- [x] 受影响 owner docs 已同步
- [x] git commit 完成（含全部 In Scope 文件）

## Closure

Status Note: 修复差异记录（callId+repairApplied+name/args before/after）+ heartbeat 派生 repair 补齐全部落地且一次 audit PASS——WI12 交付全部落地。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_81c945f5-8c96-40ac-8415-77f87f741f3d（fresh session）
- Evidence:
  - 6 验证点全 PASS（beforeRepair 真实拷贝/isRepaired 引用+权威比较/payload 六字段+512 截断/heartbeat 派生 repair 在 checkpoint 前 六常量/TestRepairDiffAudit 3 用例/既有 repair 测试零回归）
  - surefire agent 3480 / 0F / 0E；hollow exit 0；doc-links exit 0；lint exit 0
  - 观察项已处理：.rels CRLF 噪音排除出 commit

Follow-up:

- audit logger 通道（executor 需暴露句柄——登记）
