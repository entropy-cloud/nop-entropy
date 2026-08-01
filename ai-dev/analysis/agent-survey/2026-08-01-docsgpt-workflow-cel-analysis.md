# DocsGPT 类型化工作流与 CEL 条件路由深度分析 & Nop AI Agent DSL 工作流

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/docsgpt`（Python，agent 平台 RAG+工作流，402 文件）vs `nop-ai-agent`（plan 包 + compact + memory）
> Conclusion:

## 一、总览

**DocsGPT** 是开源 AI agent 平台（可视化工作流构建器 + RAG + 研究模式）。核心：**类型化工作流节点**（7 种）+ **CEL 条件路由** + **per-source 共享预算 Dispatcher** + **GraphRAG PPR+IDF**。

| 维度 | docsgpt | nop-ai-agent |
|------|---------|--------------|
| 工作流节点 | 7 类型（START/END/AGENT/NOTE/STATE/CONDITION/CODE） | AgentPlan 线性 |
| 条件路由 | CEL 表达式 + sourceHandle 分支 | 无 |
| 检索 | per-source Dispatcher 共享 token budget | memory 多适配器 |
| 图记忆 | GraphRAG PPR + IDF 降权 | 无 |
| 持久化 | PostgreSQL/pgvector | JDBC |

## 二、核心机制详解

### 2.1 类型化工作流（`workflows/schemas.py:8-16`、`workflow_engine.py:56`）
- **7 节点类型**：START / END / AGENT / NOTE / STATE / CONDITION / CODE。
- `MAX_EXECUTION_STEPS = 50`：防无限执行。
- `_execute_node` 按 type 分派 handler。
- **`_state_delta`**：跟踪每节点状态变更（增量 diff）。

### 2.2 CEL 条件路由（`cel_evaluator.py`、`schemas.py:75`）
- Google CEL（Common Expression Language）表达式做条件节点求值。
- `ConditionCase.expression`（`schemas.py:75`）：CEL 表达式定义分支条件。
- `sourceHandle`：映射到具体分支（条件命中哪个分支就走哪条边）。

### 2.3 per-source 共享预算 Dispatcher（`retriever/dispatcher.py:51`）
- 按 retriever key 分组，每组构建独立 retriever 实例。
- **共享 token budget**——防止单一来源饥饿（每个来源公平分配预算）。
- 全 classic 时退化为单实例（字节级兼容）。

### 2.4 GraphRAG + PPR（`graph_rag.py:39`）
- networkx **Personalized PageRank**（PPR）：从查询实体出发传播重要性。
- **IDF 降权 hub 节点**：高频连接的"枢纽"节点被降权（防止 hub 主导结果）。
- 查询时**无额外 LLM 调用**——纯图算法。

## 三、对 nop-ai-agent 的借鉴要点

1. **CEL 条件求值器**（最高价值，天然契合 DSL-first）——nop 的 DSL 已有表达式能力（XPL/Delta）；DocsGPT 的 CEL 做**条件分支路由**的范式，可让 plan 工作流的条件节点用轻量表达式而非全量 LLM 调用判定。nop 的 AgentPlanDecision/AgentPlanCriterion 可对接 CEL 式条件路由。
2. **per-source 共享预算 Dispatcher**（高价值，memory 包）——nop memory 多适配器检索时，按来源分组 + 统一 token 预算管理，防止单一来源饥饿。
3. **类型化节点 + _state_delta**（中价值）——DSL 工作流的节点类型设计与执行日志（每节点状态增量），对应 conductor 的 Decider（`2026-08-01-conductor-decider-replay-analysis.md`）状态跟踪。
4. **GraphRAG PPR + IDF 降权**（中价值，memory 包）——图谱记忆检索时用 PPR 传播 + 稀有实体加权，无额外 LLM 调用。

## 三.5 Harness 可靠性（Retry/Replan/Resume）

- **执行步数上限**（`workflows/workflow_engine.py:56`）：`MAX_EXECUTION_STEPS=50`——防无限循环（工作流卡死的最后防线）。
- **CEL 条件路由的确定性重试**：条件分支用 CEL 表达式（确定性），失败分支可重定向到其他路径——**不需要 LLM 重规划**，纯表达式判定。
- **per-source 共享预算**（`retriever/dispatcher.py:51`）：token 预算共享防单一来源饥饿——检索失败时其他来源仍可用。
- **对 nop 的启示**：`MAX_EXECUTION_STEPS` 是 nop-task 的 `LoopLimit` 语义；CEL 确定性分支可减少 LLM 重规划成本。

## 四、优缺点

### 优点
1. CEL 条件路由让分支判定无需 LLM——确定性 + 低成本。
2. per-source 共享预算防饥饿——公平检索。
3. GraphRAG PPR + IDF 纯图算法——无 LLM 调用。
4. 可视化工作流构建器——人机友好。

### 缺点
1. 体量大（402 文件）。
2. 强依赖 PostgreSQL/pgvector。
3. 部署复杂（Docker + Celery + Redis）。

## 五、结论

DocsGPT 的 CEL 条件路由 + per-source 预算管理与 nop 的 DSL-first 高度契合。

## 六.5 Harness 机制维度覆盖（对照参考框架 D1-D12）

> 参考：`2026-08-01-harness-mechanism-reference-framework.md`（Agent Harness 十二大机制维度）

覆盖维度：**D5**（类型化工作流 7 节点+CEL 条件路由）、**D3**（per-source 共享预算 Dispatcher+GraphRAG PPR）、**D12**（MAX_EXECUTION_STEPS=50）。缺失/薄弱：D6、D9。

## 对比结论：nop-ai-agent 全面超越性分析

**nop-ai-agent 已超越的部分**：
- **工作流**：nop-task（ChooseTaskStep/GraphTaskStep）比 docsgpt 的类型化工作流更成熟（nop-task 已落地）。
- **DSL**：nop XPL/Delta 表达式比 docsgpt 的 CEL 更强大（nop 有完整表达式引擎）。

**必要参考的增量（以超越方式吸收）**：
- **per-source 共享预算 Dispatcher**（多路检索按来源分组 + 统一 token 预算）：nop memory 多适配器检索可增加——真正增量（防单一来源饥饿）。

**总评**：nop-ai-agent **全面超越** docsgpt（nop-task + XPL 更强）；per-source 预算一个增量吸收。

## References
- `~/ai/docsgpt/workflows/{schemas.py:8-16,workflow_engine.py:56,cel_evaluator.py}`、`retriever/dispatcher.py:51`、`graph_rag.py:39`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-plan-dsl.md`、`nop-ai-agent-context-model.md`
- `ai-dev/analysis/agent-survey/2026-08-01-conductor-decider-replay-analysis.md`、`2026-08-01-trustgraph-context-graph-analysis.md`
