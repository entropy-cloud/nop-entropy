# Plano 声明式 Filter Chain 数据平面深度分析 & Nop AI Agent Guardrail 管道

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/plano`（基于 Envoy 的 AI 原生数据平面，Rust，~732 文件，v0.4.30）vs `nop-ai-agent`（middleware 洋葱链 + security/guardrail）
> Conclusion:

## 一、总览

**Plano** 是唯一基于 Envoy 数据平面的 agent 代理（进程外，非进程内框架），由 Envoy 核心贡献者开发。

| 维度 | plano | nop-ai-agent |
|------|-------|--------------|
| Guardrail 管道 | 声明式 YAML filter chain（input+output 双链） | middleware 洋葱链（代码级） |
| 配置范式 | YAML-first（零代码编排） | DSL-first（XDEF） |
| 传递 | 字节级顺序（类型无关） | 对象级（类型化） |
| 路由 | 4B 路由模型（plano_orchestrator_v1）+ turn-cap guardrail | 无显式路由 |
| LLM 抽象 | HermesLLM（provider 无关） | ChatModelProvider |

## 二、核心机制详解

### 2.1 Filter Chain 声明式管道（`crates/common/src/configuration.rs:58-104`）
- **`AgentFilterChain`**：含 `input_filters: Vec<String>`（有序 agent ID 列表）。
- **`ResolvedFilterChain`**：解析为具体 Agent 对象 + `filter_ids` 保持同步。
- **`FilterPipeline`**：`input` + `output` 两条独立链——请求侧和响应侧分别配置。

### 2.2 字节级顺序执行（`crates/brightstaff/src/handlers/agents/pipeline.rs:522-565`）
- `process_raw_filter_chain`：顺序将 bytes 传入链中每个 agent。
- 每个 agent 接收上一个的输出——**类型无关的字节传递**。
- 支持 MCP 类型和 HTTP 类型 filter agent。

### 2.3 YAML 配置驱动（`README.md:52-92`）
- `config.yaml` 声明：agents（id+url）/ model_providers / listeners（type+router+agents+descriptions）/ tracing / filter_chains——零代码编排。

### 2.4 Orchestrator 模型路由（`brightstaff/src/router/orchestrator_model_v1.rs:15,203`）
- **4B 参数路由模型**（plano_orchestrator_v1）：做 agent 选择。
- **turn-cap 外层 guardrail**：防止无限循环。

### 2.5 HermesLLM（`crates/hermesllm/`）
- provider 无关的 LLM 抽象层：统一 OpenAI/Anthropic/Bedrock 请求/响应/流式格式转换。

## 三、对 nop-ai-agent 的借鉴要点

1. **声明式 Filter Chain + input/output 双链分离**（最高价值，理念高度一致）——plano 是真正 DSL-first 的 agent 数据平面。借鉴：① guardrail/middleware 在 DSL 中声明为**有序 ID 列表**（而非代码装配）；② **input（请求侧）与 output（响应侧）独立配置**，对应 nop 的 pre/post hook 生命周期点。比当前代码级洋葱链更声明式。
2. **ResolvedFilterChain 解析模式**（中价值）——DSL 声明 ID 列表 → 运行时解析为对象，保持 filter_ids 与对象同步，便于审计/序列化。
3. **turn-cap guardrail**（中价值）——路由层的无限循环防护，对应 hive 的 doom-loop 检测（`2026-08-01-hive-dual-middleware-analysis.md`）。

## 三.5 Harness 可靠性（Retry/Replan/Resume）

- **turn-cap guardrail**（`orchestrator_model_v1.rs:15`）：路由层防无限循环——**超限即停止**（不重试死循环）。
- **字节级 filter 链重试**：`process_raw_filter_chain` 顺序传递——某 filter 失败可从该 filter 重试（幂等字节传递）。
- **4B 路由模型 replan**：模型路由失败 → 换 agent 重试（turn-cap 内）。
- **对 nop 的启示**：turn-cap 是 nop LoopLimit 的参考；filter 链幂等重试对应 nop middleware 的重试语义。

## 四、结论

Plano 验证了"DSL-first agent 数据平面"的可行性，其声明式 filter chain + 双链分离是 nop guardrail 管道从代码级走向声明式的最佳模板。局限：Envoy 依赖重、字节级无语义、Rust-only、年轻项目。

## 六.5 Harness 机制维度覆盖（对照参考框架 D1-D12）

> 参考：`2026-08-01-harness-mechanism-reference-framework.md`（Agent Harness 十二大机制维度）

覆盖维度：**D1**（声明式 filter chain input/output 双链）、**D8**（YAML-first 零代码编排）、**D9**（turn-cap guardrail）、**D12**（filter 链幂等重试）。缺失/薄弱：D2、D6。

## 对比结论：nop-ai-agent 全面超越性分析

**nop-ai-agent 已超越的部分**：
- **middleware 洋葱链**：nop middleware（可拦截/仅通知双轨）+ 12 hook 点——plano 的 filter chain 是字节级传递（无语义），nop 是对象级类型化（更强）。
- **DSL**：nop XDEF 类型化 DSL 与 plano 的 YAML-first 理念一致，但 nop 有编译期校验 + Delta 机制，更强。
- **安全**：nop security 6 层比 plano 的 filter 链更系统化。

**必要参考的增量（以超越方式吸收）**：
- **声明式 filter 链**（DSL 声明有序 ID 列表 + input/output 双链分离）：nop middleware 是代码装配——声明式配置是真正增量（对应 nop DSL-first 理念，使 guardrail 管道可审计/可序列化）。

**总评**：nop-ai-agent 在 middleware/DSL/安全上**全面超越**；声明式 filter chain（有序 ID 列表 + 双链分离）一个增量值得吸收，nop 以 XDEF 实现（超越 plano 的字节级方案）。

## References
- `~/ai/plano/crates/common/src/configuration.rs:58-104`、`crates/brightstaff/src/handlers/agents/pipeline.rs:522-565`、`crates/hermesllm/`、`brightstaff/src/router/orchestrator_model_v1.rs:15,203`、`README.md:52-92`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-middleware-design.md`、`guardrail-contract.md`
- `ai-dev/analysis/agent-survey/2026-08-01-hive-dual-middleware-analysis.md`、`2026-08-01-litellm-hook-lifecycle-analysis.md`
