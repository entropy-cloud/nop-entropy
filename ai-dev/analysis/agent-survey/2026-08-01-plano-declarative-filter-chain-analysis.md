# Plano 声明式 Filter Chain 数据平面深度分析 & Nop AI Agent Guardrail 管道

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/plano`（基于 Envoy 的 AI 原生数据平面，Rust）vs `nop-ai-agent`（middleware 洋葱链 + security/guardrail）
> Conclusion:

## 一、总览

**Plano** 是唯一基于 Envoy 数据平面的 agent 代理（进程外，非进程内框架），由 Envoy 核心贡献者开发。核心：**声明式 YAML Filter Chain**（input/output 双向链，有序 agent ID 列表）、**字节级顺序传递**、**4B 参数路由模型**、**HermesLLM provider 无关抽象**。

| 维度 | Plano | nop-ai-agent |
|------|-------|--------------|
| Guardrail 管道 | 声明式 YAML filter chain（input+output 双链） | middleware 洋葱链（代码级） |
| 配置范式 | YAML-first（零代码编排） | DSL-first（XDEF） |
| 传递 | 字节级顺序（类型无关） | 对象级（类型化） |
| 路由 | 4B 路由模型 + turn-cap guardrail | 无显式路由 |

## 二、核心机制

### 2.1 Filter Chain 声明式管道（`crates/common/src/configuration.rs:58-104`）
- `AgentFilterChain`（input_filters: 有序 agent ID 列表）→ `ResolvedFilterChain`（解析为 Agent 对象 + filter_ids 同步）→ `FilterPipeline`（input + output 两条独立链）。

### 2.2 字节级顺序执行（`crates/brightstaff/src/handlers/agents/pipeline.rs:522-565`）
- `process_raw_filter_chain` 顺序将 bytes 传入链中每个 agent，每个接收上一个输出；支持 MCP 类型和 HTTP 类型 filter agent。

### 2.3 YAML-first 配置 + Orchestrator 路由（`README.md:52-92`、`orchestrator_model_v1.rs:15`）
- agents/model_providers/listeners/tracing/filter_chains 全声明式；4B 路由模型选 agent，turn-cap 防无限循环。

## 三、对 nop-ai-agent 的借鉴要点

1. **声明式 Filter Chain + input/output 双链分离**（最高价值，理念高度一致）——plano 是真正 DSL-first 的 agent 数据平面，与 nop 的"DSL-first"理念完全契合。借鉴：① guardrail/middleware 在 DSL 中声明为**有序 ID 列表**（而非代码装配）；② **input（请求侧）与 output（响应侧）独立配置**，对应 nop 的 pre/post hook 生命周期点。这比当前代码级洋葱链更声明式。
2. **ResolvedFilterChain 解析模式**（中价值）——DSL 声明 ID 列表 → 运行时解析为对象（`ResolvedFilterChain`），保持 filter_ids 与对象同步，便于审计/序列化。
3. **turn-cap guardrail**（中价值）——路由层的无限循环防护，对应 hive 的 doom-loop 检测（`2026-08-01-hive-dual-middleware-analysis.md`）。

## 四、结论

Plano 验证了"DSL-first agent 数据平面"的可行性，其声明式 filter chain + 双链分离是 nop guardrail 管道从代码级走向声明式的最佳模板。局限：Envoy 依赖重、字节级无语义理解、Rust-only、项目年轻。

## References
- `~/ai/plano/crates/common/src/configuration.rs`、`crates/brightstaff/src/handlers/agents/pipeline.rs`、`crates/hermesllm/`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-middleware.md`、`nop-ai-agent-guardrail.md`
- `ai-dev/analysis/agent-survey/2026-08-01-hive-dual-middleware-analysis.md`、`2026-08-01-litellm-hook-lifecycle-analysis.md`
