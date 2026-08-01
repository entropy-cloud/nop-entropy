# Archon YAML DAG 工作流与声明式 Hook 深度分析 & Nop AI Agent 工作流编排

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/archon`（"AI coding 的 GitHub Actions"，TS/Bun）vs `nop-ai-agent`（plan 包 + hook）
> Conclusion:

## 一、总览

**Archon** 是 YAML 驱动的 AI coding 工作流引擎（"AI coding 的 GitHub Actions"）。核心：**YAML DAG 执行器**（拓扑排序 + 同层并发）、**10 种节点类型**、**Loop 机制**（until/max_iterations/fresh_context/interactive 人审）、**21 种 per-node Hook**（与 Claude SDK 对齐）、**4 种 Trigger Rule**、**include/workflow 双层组合**。

| 维度 | Archon | nop-ai-agent |
|------|--------|--------------|
| 工作流定义 | YAML DAG（10 节点类型） | AgentPlan 线性静态模型 |
| 并发 | 拓扑排序 + 同层 Promise.allSettled | 无 |
| Hook | 21 per-node（声明式 matcher+response） | 15 生命周期点 |
| 组合 | include（加载展平）/ workflow（运行时子 run） | 无 |
| Trigger | all_success/one_success/none_failed_min_one_success/all_done | 无显式 trigger |

## 二、核心机制

### 2.1 YAML DAG 执行器（`packages/workflows/src/dag-executor.ts:1-7`）
- 拓扑排序执行；同层独立节点 `Promise.allSettled` 并发。

### 2.2 10 节点类型（`schemas/dag-node.ts:500-510`）
- command/prompt/bash/loop/loop_group/approval/cancel/script/include/workflow（互斥联合体）。

### 2.3 Loop + interactive（`schemas/loop.ts:19-48`）
- `until` 完成信号 + `max_iterations` + `fresh_context` + `interactive` 人审门。

### 2.4 21 per-node Hook（`schemas/hooks.ts:10-32`）+ Trigger Rule（`dag-node.ts:23-28`）
- 声明式 matcher（正则）+ 静态 response，无需写代码。
- Trigger：all_success/one_success/none_failed_min_one_success/all_done。

## 三、对 nop-ai-agent 的借鉴要点

1. **Trigger Rule 体系**（高价值）——给 nop plan/DAG 节点（jcode 借鉴 `2026-08-01-jcode-dag-first-agent-analysis.md`）的依赖语义增加 trigger：尤其 `all_done`（全部结束即汇聚，无论成败）和 `none_failed_min_one_success`（至少一个成功）覆盖真实场景。
2. **声明式 per-node Hook**（高价值）——21 事件 + matcher 与 nop 的 15 生命周期点高度对齐；参考其 Zod strict 模式做**编译期校验**（hook 配置在 DSL 层静态校验）。
3. **include vs workflow 双层组合**（中价值）——静态展平（include，编译期合并）vs 运行时子 run（workflow，独立审计/成本行）的区分，对 nop plan 的"静态嵌入"与"动态子任务"有直接参考。
4. **Loop interactive gate**（中价值）——loop 节点内置人审暂停，实现 plan 迭代中 checkpoint + human-in-loop（与 AGT 审批流 `2026-08-01-agent-governance-toolkit-analysis.md`、mcp-gateway 会话挂起 `2026-08-01-mcp-gateway-session-security-analysis.md` 呼应）。

## 四、结论

Archon 是"AI coding 工作流引擎"的成熟参考，Trigger Rule + 声明式 Hook + 双层组合三项可直接丰富 nop plan/DSL。局限：强绑 Claude Code SDK、纯 TS、聚焦 coding 场景。

## References
- `~/ai/archon/packages/workflows/src/dag-executor.ts`、`schemas/{dag-node.ts,loop.ts,hooks.ts}`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-plan-dsl.md`、`nop-ai-agent-hook.md`
- `ai-dev/analysis/agent-survey/2026-08-01-jcode-dag-first-agent-analysis.md`、`2026-08-01-spec-kit-workflow-engine-analysis.md`、`2026-08-01-agent-governance-toolkit-analysis.md`
