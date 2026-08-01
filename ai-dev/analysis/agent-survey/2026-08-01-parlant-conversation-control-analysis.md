# Parlant 对话控制与 Guideline 关系图深度分析 & Nop AI Agent Guardrail/Hook

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/parlant`（Python，对话上下文工程/控制引擎，298 文件）vs `nop-ai-agent`（guardrail + 12 生命周期点 AgentLifecyclePoint）
> Conclusion:

## 一、总览

**Parlant** 把"上下文裁剪/规则匹配"做成引擎一等公民。核心：**Guideline 匹配引擎**（condition-action，仅命中项注入上下文）、**关系解析器**（依赖/排除图）、**12 生命周期 Hook**（CALL_NEXT/RESOLVE/BAIL 三态）、**Planner 迭代式步进**。

| 维度 | parlant | nop-ai-agent |
|------|---------|--------------|
| 规则 | Guideline（condition-action）+ 依赖/排除关系图 | security 多层（无关系建模） |
| Hook | 12 点 + CALL_NEXT/RESOLVE/BAIL | 12 点（AgentLifecyclePoint）+ middleware |
| 注入 | 仅命中规则注入（匹配引擎评估） | 全量 system prompt |
| Planner | needs_additional_iteration 迭代准备 | 无 |
| Canned Response | 预审模板关键时刻跳过 LLM | — |

## 二、核心机制详解

### 2.1 Guideline 匹配引擎（`guideline_matcher.py:69`）
- 规则为 **condition-action 对**。
- 每轮用 LLM 评估哪些规则命中当前上下文（`GuidelineMatchingResult`）。
- **仅命中项注入上下文**——不是全塞进 system prompt，而是动态筛选。

### 2.2 关系解析器 RelationalResolver
- 规则间**依赖关系**：依赖使上下文收敛（命中规则 A → 自动拉入规则 B）。
- 规则间**排除关系**：排除使上下文收窄（命中规则 A → 排除规则 C）。
- **靠结构而非 LLM 注意力**——规则关系是显式图，不是隐式 prompt 约束。

### 2.3 12 生命周期 Hook（`hooks.py:50`）
- 事件点：`on_acknowledging` / `on_preparing` / `on_preparation_iteration_start` / `on_generating_preamble` / `on_draft_generated` / `on_message_generated` 等。
- 每钩返回 **`CALL_NEXT`**（继续链）/ **`RESOLVE`**（提前解析）/ **`BAIL`**（中断并丢弃响应）。
- **BAIL 能中断并丢弃响应**——最激进的拦截语义。

### 2.4 Planner（`planners.py:33`）
- `Plan` 带 **`needs_additional_iteration`**——迭代式工具准备循环。
- 回调链：`on_guidelines_matched → on_guidelines_resolved → on_tools_inferred → on_tools_called`。

### 2.5 Canned Response
- 预审模板：关键时刻跳过 LLM 直接输出（如合规话术）。

## 三、对 nop-ai-agent 的借鉴要点

1. **12 点细粒度 Hook + BAIL 语义**（高价值）——可直接对标 nop 的 12 hook（AgentLifecyclePoint）。BAIL（中断并丢弃响应）比 nop 当前的"拦截改写"更激进，适用于 guardrail 的"硬阻断"场景（如 AGT 的结构性不可绕过 `2026-08-01-agent-governance-toolkit-analysis.md`）。grok-build 的 GateKind(Observe/Tool/Stop)（`2026-08-01-grok-build-deterministic-replay-analysis.md`）与 BAIL 是两种互补的拦截语义。
2. **Guideline 依赖/排除关系图**（最高价值）——nop guardrail 当前是无序/线性的检查链；借鉴 parlant 给规则建模**依赖（收敛）与排除（收窄）关系**，让 guardrail 靠结构而非 LLM 注意力收敛。这对复杂规则集（企业合规）价值大。
3. **仅命中规则注入**（中价值）——对应 trustgraph 的上下文选择（`2026-08-01-trustgraph-context-graph-analysis.md`）：不把所有约束塞进 system prompt，而是按当前上下文匹配注入。
4. **needs_additional_iteration 迭代准备**（中价值）——丰富 plan 包的步进模型（工具准备的迭代循环——`on_guidelines_matched → on_guidelines_resolved → on_tools_inferred → on_tools_called` 四步准备链）。

## 三.5 Harness 可靠性（Retry/Replan/Resume）

- **Planner 迭代式重试**（`planners.py:33`）：`needs_additional_iteration`——工具准备不足时迭代重试（on_guidelines_matched→on_tools_inferred→on_tools_called 循环）。
- **BAIL 中断语义**（`hooks.py:50`）：钩子返回 BAIL 中断并丢弃响应——**失败即中断**，不重试污染上下文。
- **Guideline 关系图收敛**：依赖/排除关系靠结构收敛——重试时上下文可控。
- **对 nop 的启示**：`needs_additional_iteration` 是 nop plan 步进模型的重试原语；BAIL 是 guardrail 硬阻断的参考。

## 四、优缺点

### 优点
1. Guideline 依赖/排除关系图让规则靠结构收敛——可验证、可审计。
2. BAIL 语义是真正的"硬阻断"。
3. 仅命中规则注入——动态上下文裁剪。

### 缺点
1. 强绑客服对话域。
2. 匹配引擎本身要额外 LLM 调用（延迟/成本翻倍）。
3. Python 异步实现重。

## 五、结论

Parlant 的 Guideline 关系图（依赖/排除）是 nop guardrail 从线性链走向关系图的关键借鉴；BAIL 中断语义补强拦截能力。

## 六.5 Harness 机制维度覆盖（对照参考框架 D1-D12）

> 参考：`2026-08-01-harness-mechanism-reference-framework.md`（Agent Harness 十二大机制维度）

覆盖维度：**D1**（12 Hook+CALL_NEXT/RESOLVE/BAIL）、**D3**（Guideline 仅命中注入+关系图）、**D5**（Planner needs_additional_iteration）、**D12**（迭代式重试）。缺失/薄弱：D2、D9。

## 对比结论：nop-ai-agent 全面超越性分析

**nop-ai-agent 已超越的部分**：
- **hook 体系**：nop 12 个 AgentLifecyclePoint + middleware 洋葱链（可拦截）——parlant 的 12 Hook（CALL_NEXT/RESOLVE/BAIL）与 nop 同量级，但 nop 的 PRE_ACTING/POST_ACTING + middleware 更工程化。
- **上下文注入**：nop `AgentPromptAssembly` 结构化组装 + ContentOrigin 标记来源，比 parlant 的仅命中规则注入更系统化。
- **guardrail**：nop security 6 层 + guardrail-contract 比 parlant 的 Guideline 匹配更完整。

**必要参考的增量（以超越方式吸收）**：
- **Guideline 依赖/排除关系图**：nop guardrail 是线性链——规则间"依赖（收敛）/排除（收窄）"关系建模是真正增量（企业合规复杂规则集场景）。
- **BAIL 语义**（中断并丢弃响应）：nop 拦截是"改写"——硬阻断语义可增加（与 grok GateKind Stop 统一）。

**总评**：nop-ai-agent 在 hook/上下文/guardrail 上**全面超越**；Guideline 关系图 + BAIL 两个增量值得吸收（以 nop 规则 DSL 实现）。

## References
- `~/ai/parlant/`（guideline_matcher.py:69、hooks.py:50、planners.py:33）
- `ai-dev/design/nop-ai-agent/guardrail-contract.md`、`nop-ai-agent-hook-skill-engine.md`
- `ai-dev/analysis/agent-survey/2026-08-01-agent-governance-toolkit-analysis.md`、`2026-08-01-grok-build-deterministic-replay-analysis.md`、`2026-08-01-trustgraph-context-graph-analysis.md`
