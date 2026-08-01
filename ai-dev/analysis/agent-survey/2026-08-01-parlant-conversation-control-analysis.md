# Parlant 对话控制与 Guideline 关系图深度分析 & Nop AI Agent Guardrail/Hook

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/parlant`（Python，对话上下文工程/控制引擎）vs `nop-ai-agent`（guardrail + hook 15 生命周期点）
> Conclusion:

## 一、总览

**Parlant** 把"上下文裁剪/规则匹配"做成引擎一等公民，而非 prompt 工程。核心：**Guideline 匹配引擎**（condition-action，仅命中项注入上下文）、**关系解析器**（依赖/排除图，靠结构收敛而非 LLM 注意力）、**12 生命周期 Hook**（含 BAIL 中断语义）、**Planner 迭代式步进**（needs_additional_iteration）。

| 维度 | Parlant | nop-ai-agent |
|------|---------|--------------|
| 规则 | Guideline（condition-action）+ 依赖/排除关系图 | security 6 层（无关系建模） |
| Hook | 12 点 + CALL_NEXT/RESOLVE/BAIL | 15 点 + middleware |
| 注入 | 仅命中规则注入（匹配引擎评估） | 全量 system prompt |
| Planner | needs_additional_iteration 迭代准备 | 无 |

## 二、核心机制

### 2.1 Guideline 匹配引擎（`guideline_matcher.py:69`）
- 规则为 condition-action 对；每轮用 LLM 评估哪些规则命中，仅命中项注入上下文（`GuidelineMatchingResult`）。

### 2.2 关系解析器 RelationalResolver
- 规则间依赖/排除关系：依赖使上下文收敛、排除使上下文收窄——靠结构而非 LLM 注意力。

### 2.3 12 生命周期 Hook（`hooks.py:50`）
- `on_acknowledging/on_preparing/on_preparation_iteration_start/on_generating_preamble/on_draft_generated/on_message_generated`…；每钩返回 `CALL_NEXT/RESOLVE/BAIL`，**BAIL 中断并丢弃响应**。

### 2.4 Planner（`planners.py:33`）
- `Plan` 带 `needs_additional_iteration`；回调点 `on_guidelines_matched→on_guidelines_resolved→on_tools_inferred→on_tools_called`。

## 三、对 nop-ai-agent 的借鉴要点

1. **12 点细粒度 Hook + BAIL 语义**（高价值）——可直接对标 nop 的 15 hook。BAIL（中断并丢弃响应）比 nop 当前的"拦截改写"更激进，适用于 guardrail 的"硬阻断"场景（如 AGT 的结构性不可绕过 `2026-08-01-agent-governance-toolkit-analysis.md`）。grok-build 的 GateKind(Observe/Tool/Stop)（`2026-08-01-grok-build-deterministic-replay-analysis.md`）与 BAIL 是两种互补的拦截语义。
2. **Guideline 依赖/排除关系图**（最高价值）——nop guardrail 当前是无序/线性的检查链；借鉴 parlant 给规则建模**依赖（收敛）与排除（收窄）关系**，让 guardrail 靠结构而非 LLM 注意力收敛。这对复杂规则集（企业合规）价值大。
3. **仅命中规则注入**（中价值）——对应 trustgraph 的上下文选择（`2026-08-01-trustgraph-context-graph-analysis.md`）：不把所有约束塞进 system prompt，而是按当前上下文匹配注入。
4. **needs_additional_iteration 迭代准备**（中价值）——丰富 plan 包的步进模型（工具准备的迭代循环）。

## 四、结论

Parlant 的 Guideline 关系图（依赖/排除）是 nop guardrail 从线性链走向关系图的关键借鉴；BAIL 中断语义补强拦截能力。局限：强绑客服对话域、匹配引擎本身要额外 LLM 调用（延迟/成本翻倍）、Python 异步重。

## References
- `~/ai/parlant/`（guideline_matcher.py、hooks.py、planners.py）
- `ai-dev/design/nop-ai-agent/nop-ai-agent-guardrail.md`、`nop-ai-agent-hook.md`
- `ai-dev/analysis/agent-survey/2026-08-01-agent-governance-toolkit-analysis.md`、`2026-08-01-grok-build-deterministic-replay-analysis.md`、`2026-08-01-trustgraph-context-graph-analysis.md`
