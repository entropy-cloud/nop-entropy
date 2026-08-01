# Claude-Code-Router 协议感知管线与 Context Archive 分析 & Nop AI Agent 路由/压缩

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/claude-code-router`（TS，本地 coding agent 控制面 + 模型网关，238 文件）vs `nop-ai-agent`（ChatModelProvider + compact + reliability）
> Conclusion:

## 一、总览

**Claude-Code-Router** 是本地模型网关 + coding agent 路由控制面，统一 Claude Code/Codex/Grok/Kimi/OpenCode。

| 维度 | claude-code-router | nop-ai-agent |
|------|-------------------|--------------|
| 协议 | 协议感知管线（anthropic/openai 双向转换） | ChatOptions 统一 |
| 路由 | config-compiler→execution-plan→policy-engine→model-resolution | ChatModelProvider |
| 容灾 | failure-classifier + ordered fallback | reliability（LlmErrorClassifier 已有） |
| 压缩 | Context Archive（协议层 compact handoff/replay） | compact 管线 |

## 二、核心机制详解

### 2.1 协议感知请求管线（`gateway/request/pipeline.ts:78`）
- `GatewayRequestPipeline`：识别请求协议（`anthropic_messages` / `openai_chat` / `openai_responses`）。
- `protocol-adapter`：协议双向转换（anthropic↔openai 格式互转）。

### 2.2 路由子系统（`routing/`）
- 四步链：`config-compiler` → `execution-plan` → `policy-engine` → `model-resolution`。
- `failure-classifier`（`:classifyRouteFailure().shouldFallback`）：判定失败是否应降级到下一个 provider。

### 2.3 有序 failover（`upstream/retry-policy.ts`）
- 解析 `retry-after` 响应头。
- 指数退避。
- 凭据池 + 密钥轮换 + **ordered fallback**（有序回退：P1→P2→P3）。

### 2.4 Context Archive（`gateway/context-archive/protocol.ts`）
- **协议层 compact handoff / replay**（`:appendCompactHandoffTask`）。
- 在网关层做上下文压缩与交接——不依赖具体 agent 引擎。

## 三、对 nop-ai-agent 的借鉴要点

1. **Context Archive 协议级 compact/replay**（高价值）——nop compact 管线（MicroCompressionCompactor/Layer2/Layer3）在"网关/边界层"的成熟参照：上下文压缩与交接在协议层完成（而非引擎内部）。
2. **failure-classifier + ordered fallback 容灾决策**（高价值）——增强 nop provider 路由与降级策略。nop reliability 已有 `LlmErrorClassifier`/`ErrorClassification`（`2026-08-01-cc-switch-provider-circuit-breaker-analysis.md`）；claude-code-router 的增量是 **ordered fallback 队列**（有序回退 P1→P2→P3，与 cc-switch 的熔断+故障转移互补）。
3. **协议适配器模式**（中价值）——多模型协议统一（anthropic/openai 格式双向转换），对应 nop ChatOptions 的协议抹平。

## 三.5 Harness 可靠性（Retry/Replan/Resume）

- **有序 failover 重试**（`upstream/retry-policy.ts`）：解析 `retry-after` 响应头 + 指数退避 + 凭据池 + 密钥轮换 + ordered fallback（P1→P2→P3）——**多级重试**。
- **failure-classifier 降级决策**（`routing/`）：`classifyRouteFailure().shouldFallback` 判定失败是否应降级——**分类后再重试**（区分可重试/不可重试错误）。
- **Context Archive replay**（`gateway/context-archive/protocol.ts`）：协议层 compact handoff / replay——上下文压缩后的重放恢复。
- **对 nop 的启示**：`retry-after` 尊重 + failure-classifier（先分类再重试）是 nop reliability 的参考；Context Archive 的协议层 replay 对应 nop compact。

## 四、结论

Claude-Code-Router 的 Context Archive + failure-classifier + ordered fallback 是 nop compact/provider 路由的网关层参考。局限：网关/中间件非 agent 框架、TS 生态。

## 六.5 Harness 机制维度覆盖（对照参考框架 D1-D12）

> 参考：`2026-08-01-harness-mechanism-reference-framework.md`（Agent Harness 十二大机制维度）

覆盖维度：**D3**（Context Archive 协议层 compact）、**D12**（retry-after+指数退避+ordered fallback+failure-classifier）、**D11**（网关可观测）。缺失/薄弱：D1、D5（网关层）。

## 对比结论：nop-ai-agent 全面超越性分析

**nop-ai-agent 已超越的部分**：
- **provider 路由**：nop `ChatModelProvider` + reliability（LlmErrorClassifier/ErrorClassification/ThresholdBreaker）比 claude-code-router 的网关路由更原生。
- **压缩**：nop PipelineCompactor 3 层比其 Context Archive 协议层方案更完整。

**必要参考的增量（以超越方式吸收）**：
- **failure-classifier + ordered fallback**（先分类再重试 + 有序回退 P1→P2→P3）：nop provider 降级可增加有序回退队列——真正增量（与 cc-switch failover 统一）。

**总评**：nop-ai-agent **全面超越** claude-code-router（Provider/可靠性/压缩更原生）；ordered fallback 一个增量吸收。

## References
- `~/ai/claude-code-router/gateway/request/pipeline.ts:78`、`routing/`、`upstream/retry-policy.ts`、`gateway/context-archive/protocol.ts`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-react-engine.md`（compact 包）、`nop-ai-agent-reliability.md`
- `ai-dev/analysis/agent-survey/2026-08-01-cc-switch-provider-circuit-breaker-analysis.md`、`2026-08-01-context-mode-compaction-analysis.md`
