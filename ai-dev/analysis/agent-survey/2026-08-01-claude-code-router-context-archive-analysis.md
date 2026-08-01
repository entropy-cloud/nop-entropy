# Claude-Code-Router 协议感知管线与 Context Archive 分析 & Nop AI Agent 路由/压缩

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/claude-code-router`（TS，本地 coding agent 控制面 + 模型网关，238 文件）vs `nop-ai-agent`（ChatModelProvider + compact）
> Conclusion:

## 一、总览与机制
Claude-Code-Router 是本地模型网关 + coding agent 路由控制面，统一 Claude Code/Codex/Grok/Kimi/OpenCode。核心：**协议感知请求管线**（GatewayRequestPipeline 识别 anthropic_messages/openai_chat/openai_responses，protocol-adapter 双向转换，`gateway/request/pipeline.ts:78`）；**路由子系统**（config-compiler→execution-plan→policy-engine→model-resolution + failure-classifier shouldFallback，`routing/`）；**有序 failover**（解析 retry-after/指数退避/凭据池+密钥轮换+ordered fallback，`upstream/retry-policy.ts`）；**Context Archive**（协议层 compact handoff/replay，`gateway/context-archive/protocol.ts` appendCompactHandoffTask）。

## 二、对 nop-ai-agent 的借鉴要点
1. **Context Archive 协议级 compact/replay**（高价值）——nop 3 层 compact 在"网关/边界层"的成熟参照：上下文压缩与交接在协议层完成（而非引擎内部）。
2. **failure-classifier + ordered fallback 容灾决策**（高价值）——增强 nop provider 路由与降级策略（与 cc-switch 熔断/故障转移 `2026-08-01-cc-switch-provider-circuit-breaker-analysis.md` 互补：cc-switch 是熔断状态机，claude-code-router 是 failure 分类 + 有序回退）。
3. **协议适配器模式**（中价值）——多模型协议统一（anthropic/openai 格式双向转换），对应 nop ChatOptions 的协议抹平。

## 三、结论
Claude-Code-Router 的 Context Archive + failure-classifier 是 nop compact/provider 路由的网关层参考。局限：本质是网关/中间件非 agent 框架、无 plan/工作流编排、TS 生态需 HTTP 桥接。

## References
- `~/ai/claude-code-router/gateway/request/pipeline.ts`、`routing/`、`upstream/retry-policy.ts`、`gateway/context-archive/protocol.ts`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-compaction.md`、`nop-ai-agent-reliability.md`
- `ai-dev/analysis/agent-survey/2026-08-01-cc-switch-provider-circuit-breaker-analysis.md`、`2026-08-01-context-mode-compaction-analysis.md`
