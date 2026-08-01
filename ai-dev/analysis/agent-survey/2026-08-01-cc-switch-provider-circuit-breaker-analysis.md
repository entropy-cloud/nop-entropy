# CC-Switch Provider 熔断与有序故障转移深度分析 & Nop AI Agent 模型路由弹性

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/cc-switch`（Tauri 多 Provider 管理器 + 内置代理网关，Rust+TS，v3.19.1）vs `nop-ai-agent`（ChatModelProvider + reliability）
> Conclusion:

## 一、总览

**CC-Switch** 把 provider 切换做成**运行时弹性系统**（熔断+故障转移+模型映射+计量），而非单纯改配置文件。核心：**熔断器**（Closed/Open/HalfOpen + 错误率阈值）、**provider_router 有序故障转移队列**（P1→P2 + 去重切换）、**请求/响应处理管线**（body_filter/model_mapper/thinking_budget_rectifier/SSE/usage 计量）、**SSOT provider 配置**。

| 维度 | cc-switch | nop-ai-agent |
|------|-----------|--------------|
| Provider 弹性 | 熔断器 + 有序故障转移 | reliability 重试/回退 |
| 模型归一化 | model_mapper / thinking_budget_rectifier | ChatOptions 统一 |
| 计量 | usage 追踪 | 无 |
| 配置 | SSOT（直写各 app 原生配置） | DSL |

## 二、核心机制

### 2.1 熔断器（`proxy/circuit_breaker.rs:14`）
- Closed/Open/HalfOpen 三态 + 错误率阈值。

### 2.2 有序故障转移（`proxy/provider_router.rs:37`）
- 按 failover 队列 P1→P2 选择；failover_switch 去重切换；每 provider 独立熔断。

### 2.3 请求/响应处理管线（`proxy/`）
- body_filter/model_mapper/thinking_budget_rectifier/SSE 流式/usage 计量/cache 注入。

## 三、对 nop-ai-agent 的借鉴要点

1. **熔断器模式**（高价值）——nop reliability 当前是重试/退避，无连续失败熔断。借鉴 cc-switch 的 Closed/Open/HalfOpen + 错误率阈值，做 LLM provider 弹性（与 AGT 的工具熔断 `2026-08-01-agent-governance-toolkit-analysis.md` 同机制，应用层是 provider）。
2. **有序故障转移队列 + 每 provider 独立熔断**（最高价值）——多 provider 场景的容灾：P1 熔断→自动 P2，每 provider 独立熔断状态。nop 的 ChatModelProvider 多实例需要这层运行时容错。
3. **跨 provider 归一化**（中价值）——model_mapper（模型名映射）/ thinking_budget_rectifier（思考预算修正），对应 nop ChatOptions 的能力差异抹平。
4. **usage 计量**（中价值）——token/cost 追踪，对应 nop 的配额/计量（quota 包）。

## 四、结论

cc-switch 补齐了 nop 在 provider 弹性（熔断+有序故障转移）上的缺口。局限：桌面应用中心（Tauri UI 重）、配置 schema 强绑特定 app。

## References
- `~/ai/cc-switch/src-tauri/src/proxy/`（circuit_breaker.rs、provider_router.rs）
- `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md`
- `ai-dev/analysis/agent-survey/2026-08-01-agent-governance-toolkit-analysis.md`
