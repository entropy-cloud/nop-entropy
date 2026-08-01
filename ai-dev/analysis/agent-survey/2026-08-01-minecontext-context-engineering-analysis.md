# MineContext 四阶段上下文工程与类型感知合并分析 & Nop AI Agent 上下文/压缩

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/minecontext`（Python+TS，主动上下文感知 AI 伙伴）vs `nop-ai-agent`（compact + memory）
> Conclusion:

## 一、总览与机制
MineContext 是完整的上下文工程生命周期（采集→处理→存储→检索→消费→主动推送），local-first 隐私优先。核心：**四阶段 Workflow 引擎**（Intent→Context→Execution→Reflection DAG，每节点 BaseNode，`workflow.py:48-55` WorkflowState 流转）；**LLM 驱动上下文策略**（`llm_context_strategy.py:43` analyze_and_plan_tools，6 类检索工具 + ContextSufficiency 充分性判断循环）；**类型感知上下文合并**（`context_merger.py:35-53` 按类型注册合并策略，similarity_threshold=0.85 + associative_similarity_threshold=0.6 双阈值）；**可插拔存储适配**（base_storage.py 接口 + ChromaDB/Qdrant/SQLite 三实现）。

## 二、对 nop-ai-agent 的借鉴要点
1. **Intent→Context→Execution→Reflection + 充分性判断循环**（中价值）——四阶段 + ContextSufficiency 迭代，AgentSession 消息流循环结构参考。
2. **类型感知合并 + 双阈值**（高价值，compact 包）——按上下文类型注册不同合并策略 + 双相似度阈值（严格 0.85 / 关联 0.6），直接用于 compact 压缩管线的去重/合并。
3. **可插拔存储适配器接口**（中价值，memory 包）——base_storage 统一接口 + 多后端实现，nop memory 适配器设计范式（与 txtai 的 Factory 范式 `2026-08-01-txtai-embeddings-factory-analysis.md` 呼应）。

## 三、结论
MineContext 的类型感知合并 + 双阈值是 compact 管线的直接增强。局限：桌面端截图场景导向、依赖 VLM、活跃度下降（近 3 月未更新）、前端耦合重。

## References
- `~/ai/minecontext/context_agent/core/workflow.py`、`llm_context_strategy.py`、`context_merger.py`、`storage/base_storage.py`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-compaction.md`、`nop-ai-agent-memory.md`
- `ai-dev/analysis/agent-survey/2026-08-01-txtai-embeddings-factory-analysis.md`
