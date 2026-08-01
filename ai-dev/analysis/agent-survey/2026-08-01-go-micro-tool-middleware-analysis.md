# Go-Micro 工具中间件栈与 Checkpoint 状态机分析 & Nop AI Agent 安全/恢复

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/go-micro`（Go，agent harness runtime + MCP/A2A gateway）vs `nop-ai-agent`（security 6 层 + checkpoint）
> Conclusion:

## 一、总览与机制
Go-Micro 把 harness 定义为"agent 周围的运行时——工具/记忆/guardrail/工作流/服务发现/协议"。核心：**工具 handler 中间件栈**（guardrail 洋葱模型：context→trace→plan→step→loop→spend→approve→checkpoint→retry→x402Pay→timeout→base，`agent/builtin.go:124-148`）；**Checkpoint/Resume**（每次 Ask 保存 flow.Run，完成返回缓存不重调模型，paused/input-required 断点续跑，`checkpoint.go:70-159`）；内置工具 plan/delegate/request_input（human-in-loop）；A2A 网关（AgentCard + JSON-RPC task 生命周期 working→completed/failed/input-required）；组合执行边界（MaxSteps/LoopLimit 重复检测/MaxSpend x402 预算/ToolCallTimeout）。

## 二、对 nop-ai-agent 的借鉴要点
1. **工具 handler 中间件栈**（高价值）——多层 guardrail 洋葱组合范式，直接映射 nop security 6 层（每层独立、可拒绝、洋葱包裹）；与 plano 声明式 filter chain（`2026-08-01-plano-declarative-filter-chain-analysis.md`）互补：go-micro 是代码级、plano 是声明式。
2. **Checkpoint 状态机**（高价值）——done 返回缓存、paused→running 恢复、input-required 分支，是 checkpoint 实现的参考范本（对应 hatchet WAIT_FOR `2026-08-01-hatchet-durable-execution-analysis.md`、rivet sleep/wake `2026-08-01-rivet-actor-runtime-analysis.md`）。
3. **组合执行边界**（中价值）——MaxSteps/LoopLimit（重复检测）/MaxSpend/Timeout 组合式安全护栏。
4. **ApproveTool hook**（中价值）——工具执行前的 human-in-loop 审批点（对应 AGT 审批流 `2026-08-01-agent-governance-toolkit-analysis.md`）。

## 三、结论
Go-Micro 的工具中间件栈 + checkpoint 状态机是 nop security/reliability 的直接参考。局限：纯 Go、与 go-micro service registry 耦合、A2A/MCP 与框架强绑定不易移植 Java。

## References
- `~/ai/go-micro/agent/`（builtin.go、checkpoint.go、options.go）、`gateway/a2a/a2a.go`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-security.md`、`nop-ai-agent-reliability.md`
- `ai-dev/analysis/agent-survey/2026-08-01-plano-declarative-filter-chain-analysis.md`、`2026-08-01-hatchet-durable-execution-analysis.md`、`2026-08-01-rivet-actor-runtime-analysis.md`、`2026-08-01-agent-governance-toolkit-analysis.md`
