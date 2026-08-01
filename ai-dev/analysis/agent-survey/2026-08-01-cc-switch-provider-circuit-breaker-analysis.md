# CC-Switch Provider 熔断与有序故障转移深度分析 & Nop AI Agent 模型路由弹性

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/cc-switch`（Tauri 多 Provider 管理器 + 内置代理网关，Rust+TS，v3.19.1，~638 文件）vs `nop-ai-agent`（ChatModelProvider + reliability ICircuitBreaker/ThresholdBreaker）
> Conclusion:

## 一、总览

**CC-Switch** 把 provider 切换做成**运行时弹性系统**（熔断+故障转移+模型映射+计量）。核心：**熔断器**、**provider_router 有序故障转移队列**、**请求/响应处理管线**、**SSOT provider 配置**。

| 维度 | cc-switch | nop-ai-agent |
|------|-----------|--------------|
| Provider 弹性 | 熔断器 + 有序故障转移 | reliability 已有 ICircuitBreaker/ThresholdBreaker/CircuitState(CLOSED/OPEN/HALF_OPEN) |
| 熔断阈值 | 错误率阈值 | 计数阈值（默认 3） |
| 模型归一化 | model_mapper / thinking_budget_rectifier | ChatOptions 统一 |
| 计量 | usage 追踪 | usage-and-billing |
| 配置 | SSOT（直写各 app 原生配置） | DSL |

## 二、核心机制详解

### 2.1 熔断器（`proxy/circuit_breaker.rs:14-16`）
- **Closed/Open/HalfOpen** 三态 + **错误率阈值**。
- 与 nop 的 `ThresholdBreaker`（计数阈值）不同：cc-switch 用错误率（更平滑，允许偶发失败）。

### 2.2 有序故障转移（`proxy/provider_router.rs:37`）
- 按 **failover 队列 P1→P2** 选择 provider。
- `failover_switch` **去重切换**（防止来回震荡）。
- **每 provider 独立熔断状态**——P1 熔断不影响 P2 的状态。

### 2.3 请求/响应处理管线（`proxy/`）
- `body_filter`：请求体过滤。
- `model_mapper`：模型名映射（provider 间模型名差异抹平）。
- `thinking_budget_rectifier`：思考预算修正（thinking token 限额跨 provider 归一化）。
- SSE 流式处理 + usage 计量 + cache 注入。

### 2.4 SSOT Provider 配置（`provider.rs:7`）
- 直写各 agent 原生配置（claude_*/codex_*/gemini_* 等）——单一真相源（SSOT），无副本同步。

## 三、对 nop-ai-agent 的借鉴要点

> 注：nop `reliability` 包**已实现熔断器**（ICircuitBreaker/ThresholdBreaker/CircuitState CLOSED/OPEN/HALF_OPEN + AlwaysClosed 默认），本节聚焦 cc-switch 相对 nop 已有能力的**增量**。

1. **有序故障转移队列 + 每 provider 独立熔断**（最高价值，nop 缺失）——nop 熔断是单 provider 维度的连续失败计数（ThresholdBreaker 默认阈值 3）；cc-switch 的增量是**跨 provider 有序 failover 队列**（P1 熔断→自动切 P2，failover_switch 去重切换）。nop 的 ChatModelProvider 多实例场景需要这层"熔断后切谁"的编排。
2. **错误率阈值 vs 计数阈值**（中价值）——nop ThresholdBreaker 是连续失败**计数**阈值；cc-switch 用**错误率**阈值（更平滑，允许偶发失败）。可作为 nop 熔断策略的可选增强。
3. **跨 provider 归一化**（中价值）——model_mapper（模型名映射）/ thinking_budget_rectifier（思考预算修正），对应 nop ChatOptions 的能力差异抹平。
4. **SSOT provider 配置**（低价值）——单一真相源理念，nop DSL 已是 SSOT。

## 三.5 Harness 可靠性（Retry/Replan/Resume）

- **熔断器三态**（`proxy/circuit_breaker.rs:14-16`）：Closed/Open/HalfOpen + 错误率阈值——熔断后快速失败（不重试堆积），冷却后半开探活。
- **有序故障转移**（`provider_router.rs:37`）：failover 队列 P1→P2 + failover_switch 去重切换——**provider 级重试**（P1 熔断自动切 P2）。
- **对 nop 的启示**：nop 已有 ThresholdBreaker（计数阈值），cc-switch 的错误率阈值 + 跨 provider failover 队列是增量（熔断后切谁）。

## 四、结论

nop reliability 已具备熔断器；cc-switch 的真正增量是**跨 provider 有序故障转移队列**与**错误率阈值**（vs nop 的计数阈值）。局限：桌面应用中心（Tauri UI 重）、配置 schema 强绑特定 app。

## 六.5 Harness 机制维度覆盖（对照参考框架 D1-D12）

> 参考：`2026-08-01-harness-mechanism-reference-framework.md`（Agent Harness 十二大机制维度）

覆盖维度：**D12**（熔断 Closed/Open/HalfOpen+有序故障转移）、**D11**（usage 计量）。缺失/薄弱：D1-D9（provider 弹性层，非 harness 循环）。

## 对比结论：nop-ai-agent 全面超越性分析

**nop-ai-agent 已超越的部分**：
- **熔断器**：nop reliability 已有 `ICircuitBreaker`/`ThresholdBreaker`/`CircuitState`（CLOSED/OPEN/HALF_OPEN）——cc-switch 的熔断 nop 已具备且更原生。

**必要参考的增量（以超越方式吸收）**：
- **跨 provider 有序故障转移队列**（P1 熔断→自动切 P2 + failover_switch 去重）：nop 熔断是单 provider 维度——跨 provider failover 队列是真正增量。
- **错误率阈值**（vs nop 计数阈值，更平滑）：nop ThresholdBreaker 策略可选增强。

**总评**：nop-ai-agent 熔断器**已超越** cc-switch（ThresholdBreaker 更原生）；有序故障转移 + 错误率阈值两个增量吸收（熔断后切谁）。

## References
- `~/ai/cc-switch/src-tauri/src/proxy/`（circuit_breaker.rs:14-16、provider_router.rs:37）、`provider.rs:7`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md`
- `ai-dev/analysis/agent-survey/2026-08-01-agent-governance-toolkit-analysis.md`
