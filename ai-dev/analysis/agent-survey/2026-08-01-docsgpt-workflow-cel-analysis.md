# DocsGPT 类型化工作流与 CEL 条件路由深度分析 & Nop AI Agent DSL 工作流

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/docsgpt`（Python，agent 平台 RAG+工作流）vs `nop-ai-agent`（plan 包 + compact + memory）
> Conclusion:

## 一、总览

**DocsGPT** 是开源 AI agent 平台（可视化工作流构建器 + RAG + 研究模式）。核心：**类型化工作流节点**（6 种）+ **CEL 条件路由** + **per-source 共享预算 Dispatcher** + **GraphRAG PPR+IDF**。

| 维度 | DocsGPT | nop-ai-agent |
|------|---------|--------------|
| 工作流节点 | 6 类型（START/END/AGENT/NOTE/STATE/CONDITION/CODE） | AgentPlan 线性 |
| 条件路由 | CEL 表达式 + sourceHandle 分支 | 无 |
| 检索 | per-source Dispatcher 共享 token budget | memory 多适配器 |
| 图记忆 | GraphRAG PPR + IDF 降权 | 无 |

## 二、核心机制

### 2.1 类型化工作流（`workflows/schemas.py:8-16`、`workflow_engine.py:56`）
- 6 节点类型；`MAX_EXECUTION_STEPS=50`；`_execute_node` 按 type 分派 handler；`_state_delta` 跟踪每节点状态变更。

### 2.2 CEL 条件路由（`cel_evaluator.py`、`schemas.py:75`）
- Google CEL 表达式做条件节点求值；`sourceHandle` 映射分支。

### 2.3 per-source 共享预算 Dispatcher（`retriever/dispatcher.py:51`）
- 按 retriever key 分组构建独立实例，**共享 token budget** 防饥饿；全 classic 时退化为单实例（字节级兼容）。

### 2.4 GraphRAG + PPR（`graph_rag.py:39`）
- networkx Personalized PageRank + IDF 降权 hub 节点；查询时无额外 LLM 调用。

## 三、对 nop-ai-agent 的借鉴要点

1. **CEL 条件求值器**（最高价值，天然契合 DSL-first）——nop 的 DSL 已有表达式能力（XPL/Delta）；DocsGPT 的 CEL 做**条件分支路由**的范式，可让 plan 工作流的条件节点用轻量表达式而非全量 LLM 调用判定。
2. **per-source 共享预算 Dispatcher**（高价值，memory 包）——nop memory 多适配器检索时，按来源分组 + 统一 token 预算管理，防止单一来源饥饿。
3. **类型化节点 + _state_delta**（中价值）——DSL 工作流的节点类型设计与执行日志（每节点状态增量），对应 conductor 的 Decider（`2026-08-01-conductor-decider-replay-analysis.md`）状态跟踪。
4. **GraphRAG PPR + IDF 降权**（中价值，memory 包）——图谱记忆检索时用 PPR 传播 + 稀有实体加权，无额外 LLM 调用。

## 四、结论

DocsGPT 的 CEL 条件路由 + per-source 预算管理与 nop 的 DSL-first 高度契合。局限：体量大、强依赖 PostgreSQL/pgvector、部署复杂（Docker+Celery+Redis）。

## References
- `~/ai/docsgpt/workflows/{schemas.py,workflow_engine.py,cel_evaluator.py}`、`retriever/dispatcher.py`、`graph_rag.py`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-plan-dsl.md`、`nop-ai-agent-memory.md`
- `ai-dev/analysis/agent-survey/2026-08-01-conductor-decider-replay-analysis.md`、`2026-08-01-trustgraph-context-graph-analysis.md`
