# 2305 WI23 流式管线可行性评估 + 裁定（refactor roadmap M7，Decision）

> Plan Status: completed
> Last Reviewed: 2026-10-03
> Source: `ai-dev/backlog/nop-ai-agent-refactor-roadmap.md` WI23（Deliverable: ai-dev/design/ 下裁定文档；Item Type: Decision；Owner: `ai-dev/design/nop-ai-agent/nop-ai-agent-react-engine.md`；来源: `99-overall-comparison.md` ⑤-C-1 + `dsh-D1-agent-loop.md` ⑥-建议1）
> 划界: 只评估不实施（roadmap 明文）；关联 residual——WI21 流式 usage 缺口（plan 2303 Known Residuals）以本裁定为 successor 锚点
> 定位约束: 与 autonomous roadmap 一致——流式完整管线仍只评估不实施（⑤-C-1 裁定出口）
> Adversarial Review: 子代理 agent_4f3baa60——APPROVED（10/10 证据锚点 PASS，裁定逻辑对抗无否证）；Minor 5 处措辞修正（锚点限定/入口表述/重启条件载体/retry-policy 交互/usage 消费面）已吸收

## Purpose

裁定 dsh 流式整流持久化范式（D1-4：全链路流式 + StreamChunk 7 变体 + 整流记录持久化 token 级重放 + 中断固化）对 nop 的改造面，产出立项/不立项 Decision 报告（owner doc react-engine.md 新增裁定小节）。本 plan 只评估不实施；若裁定立项，实施归 successor plan/roadmap（本文件登记归属出口，不承接实施）。

## Current Baseline

（执行时 HEAD `4272ecfb3a`，2026-10-03 实测——dsh 比对报告快照为 2026-09，流式面已部分演进，按 live 复核）

- **基础设施层已有流式面**（dsh 报告快照后新增/在位）：`IChatService.callStream`（nop-ai-api，Flow.Publisher<ChatStreamChunk>）在位；`ChatServiceImpl` 流式实现 + 聚合器（:400 subscribe 聚合）；全部 5 个 dialect 有 `parseStreamChunk`（流式错误恢复 :463）；gateway 流式 failover 全链路（FailoverStreamFlow）。
- **引擎层无流式路径**：ReActAgentExecutor/SingleTurnExecutor/LlmCallCoordinator 零 `callStream` 引用——引擎 LLM 调用唯一路径仍是同步非流式 callAsync；REASONING_CHUNK 已于 2026-09-15 审计修复移除（AgentLifecyclePoint 现无该值），重引入需完整流式管线。
- **流式 usage 解析缺口**（WI21 plan 2303 Known Residuals 登记，锚本裁定）：OpenAI/Gemini/Ollama 的 parseStreamChunk 不解析 usage（连基础 token）；Anthropic 流式手写（不走 parseUsage→raw 不捕获）；Responses 共用 parser（raw 自动捕获）。
- **dsh 范式参照**（`dsh-D1-agent-loop.md` D1-4）：StreamChunk 7 变体 + AssistantStreamAttempt 累积 + 结算一次性持久化 `assistant/attempt`（token 级重放保真）+ abort 中断固化 `interrupted:true`；价值主张 = UI 可感知流式 + 重放审计。
- **需求面**：nop 当前无已登记的流式 UI 消费场景（steering/异步占位 WI2-WI5 的唤醒路径均非流式）；nop-chaos demo 无 agent 流式 UI；生产装配缺席（gateway 流式服务于直连 chat 场景而非 agent 循环）。

## Goals

- 产出 **Decision 裁定文档**：`ai-dev/design/nop-ai-agent/nop-ai-agent-react-engine.md` 新增"流式管线可行性评估与裁定（WI23）"小节，内容：
  1. **改造面评估**（四层）：引擎接线面（ReAct/SingeTurn/LlmCallCoordinator 换流式调用 + 流式治理面——guardrail/steering/占位唤醒与 token 流的交互）；事件管线面（REASONING_CHUNK 重引入 + AgentEvent payload 类型化 WI9 的兼容）；持久化面（dsh 整流记录持久化 vs nop 现有 TOOL_EXECUTION checkpoint/消息历史的边界）；dialect 面（流式 usage 解析补齐——WI21 residual 的承接出口）。
  2. **成本/收益**：收益=UI 可感知流式 + token 级重放保真 + 首 token 延迟可观测；成本=四层改造 + 流式治理语义重审（WI2 裁定的唤醒源/占位与流式的交互）+ 双路径维护（非流式路径保留）。
  3. **裁定**：立项/不立项 + 理由；若不立项，登记**重启条件**（可判定：如"生产流式 UI 消费场景立项"或"流式 usage 观测需求落地"）；若立项，登记 successor 归属出口（本 roadmap 不承接实施）。
- roadmap WI23 勾选 + Current Baseline 锚点；当日 log。

## Non-Goals

- 实施任何流式管线代码（只评估不实施——roadmap 定位约束）。
- WI21 流式 usage 缺口的代码补齐（其 successor 锚点指向本裁定的结论）。
- autonomous roadmap 定位内的流式项。

## Scope

### In Scope

- `ai-dev/design/nop-ai-agent/nop-ai-agent-react-engine.md` 新增裁定小节（Decision 文档）。
- roadmap WI23 勾选 + 锚点；log。

### Out Of Scope

- 任何源码变更；autonomous roadmap 项。

## Execution Plan

### Phase 1 - 裁定文档

Status: completed
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-react-engine.md`、roadmap、log

- Item Types: `Decision`

- [x] 裁定小节落 react-engine.md（改造面四层评估 + 成本收益 + **裁定：不立项** + 三条可判定重启条件/successor 出口）
- [x] roadmap WI23 勾选 + 锚点；log 条目

Exit Criteria:

- [x] 裁定文档在位且证据锚点与 live 一致（审查 10/10 锚点 PASS）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 条目更新

## Closure Gates

- [x] 裁定文档完成（四层评估 + 裁定：不立项 + 重启条件/successor 出口登记）
- [x] 独立子 agent closure-audit 已完成并记录证据（agent_4f3baa60——裁定项审查即验收：10/10 锚点 PASS + 裁定逻辑对抗无否证）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2305-wi23-streaming-ruling.md --strict` 退出码 0
- [x] 受影响 owner docs 已同步
- [x] git commit 完成

## Known Residuals

### 流式 usage 解析缺口（OpenAI/Gemini/Ollama 流式无 usage）
- Classification: watch-only residual
- Why Not Blocking Closure: WI21 登记的既有缺口，承接出口=本裁定结论（不立项则随流式管线重启条件一并重启）
- Successor Required: no

## Closure

Status Note: 纯 Decision 项收口——裁定**不立项**（无已登记生产需求；基础设施层流式面在位但四层治理语义重审在无消费方验证下是纯负债；WI2-WI5 异步模型与流式正交）。三条可判定重启条件登记（流式 UI 场景立项/流式 usage 观测需求落地/重放保真实证场景立项记录）；dialect 流式 usage 补齐可独立先行（successor 首 slice）。
Completed: 2026-10-03

Closure Audit Evidence:

- Reviewer / Agent: 独立审查子代理 agent_4f3baa60（裁定项审查即验收——fresh session）
- Evidence: 证据锚点 10/10 PASS（live 实测）；裁定逻辑对抗无否证（两条反方论据正面处理）；plan/裁定文档格式符合 guide；check-doc-links 0 error

Follow-up:

- 见 Known Residuals（流式 usage 缺口随重启条件封存）
