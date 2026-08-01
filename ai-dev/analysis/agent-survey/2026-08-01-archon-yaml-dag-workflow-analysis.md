# Archon YAML DAG 工作流与声明式 Hook 深度分析 & Nop AI Agent 工作流编排

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/archon`（"AI coding 的 GitHub Actions"，TS/Bun，~1326 文件）vs `nop-ai-agent`（plan 包 + hook 12 生命周期点）
> Conclusion:

## 一、总览

**Archon** 是 YAML 驱动的 AI coding 工作流引擎（"AI coding 的 GitHub Actions"）。核心：**YAML DAG 执行器**（拓扑排序 + 同层并发）、**10 种节点类型**、**Loop 机制**（until/max_iterations/fresh_context/interactive 人审）、**21 种 per-node Hook**（与 Claude SDK 对齐）、**4 种 Trigger Rule**、**include/workflow 双层组合**。

| 维度 | archon | nop-ai-agent |
|------|--------|--------------|
| 工作流定义 | YAML DAG（10 节点类型） | AgentPlan 线性静态模型 |
| 并发 | 拓扑排序 + 同层 Promise.allSettled | 无 |
| Hook | 21 per-node（声明式 matcher+response） | 12 生命周期点（AgentLifecyclePoint） |
| 组合 | include（加载展平）/ workflow（运行时子 run） | 无 |
| Trigger | all_success/one_success/none_failed_min_one_success/all_done | 无显式 trigger |
| 隔离 | git worktree per run | AgentSession |

## 二、核心机制详解

### 2.1 YAML DAG 执行器（`packages/workflows/src/dag-executor.ts:1-7`）
- 拓扑排序执行 `nodes:` 工作流。
- 同层独立节点 `Promise.allSettled` 并发（失败不阻塞同层其他节点）。

### 2.2 10 节点类型（`schemas/dag-node.ts:500-510`）
- 互斥联合体：command / prompt / bash / loop / loop_group / approval / cancel / script / include / workflow。

### 2.3 Loop 机制（`schemas/loop.ts:19-48`）
- `until`：完成信号（LLM 判定/条件表达式）。
- `max_iterations`：最大迭代次数。
- `fresh_context`：每次迭代是否用全新上下文（防上下文累积）。
- `interactive`：人审门（每次迭代暂停等待人工确认）。
- `loop_group`：可嵌套子 DAG 递归迭代。

### 2.4 21 per-node Hook（`schemas/hooks.ts:10-32`）+ Trigger Rule（`schemas/dag-node.ts:23-28`）
- 21 个 hook 事件与 Claude SDK 对齐（PreToolUse/PostToolUse/SubagentStart 等）。
- 声明式配置：正则 `matcher` + 静态 `response`——无需写代码即可配置 hook 行为。
- **4 种 Trigger Rule**：
  - `all_success`：所有上游节点成功才触发。
  - `one_success`：任一上游成功即触发。
  - `none_failed_min_one_success`：至少一个成功且无失败。
  - `all_done`：所有上游结束即触发（无论成败）。

### 2.5 include vs workflow 双层组合
- `include:`（加载时展平）：编译期将引用的工作流内联展开——适合静态组合。
- `workflow:`（运行时子 run）：运行时启动独立子 run，有独立审计/成本行——适合动态子任务编排。

## 三、对 nop-ai-agent 的借鉴要点

1. **Trigger Rule 体系**（高价值）——给 nop plan/DAG 节点（jcode 借鉴 `2026-08-01-jcode-dag-first-agent-analysis.md`）的依赖语义增加 trigger：尤其 `all_done`（全部结束即汇聚，无论成败）和 `none_failed_min_one_success`（至少一个成功）覆盖真实场景（容错汇聚）。
2. **声明式 per-node Hook**（高价值）——21 事件 + matcher 与 nop 的 12 生命周期点高度对齐；参考其 Zod strict 模式做**编译期校验**（hook 配置在 DSL 层静态校验，而非运行时才发现配置错误）。
3. **include vs workflow 双层组合**（中价值）——静态展平（include，编译期合并）vs 运行时子 run（workflow，独立审计/成本行）的区分，对 nop plan 的"静态嵌入"与"动态子任务"有直接参考。
4. **Loop interactive gate**（中价值）——loop 节点内置人审暂停，实现 plan 迭代中 checkpoint + human-in-loop（与 AGT 审批流 `2026-08-01-agent-governance-toolkit-analysis.md`、mcp-gateway 会话挂起 `2026-08-01-mcp-gateway-session-security-analysis.md` 呼应）。
5. **fresh_context**（中价值）——每次 loop 迭代用全新上下文防累积，对应 nop compact 的极端情况（全量重置而非渐进压缩）。

## 三.5 Harness 可靠性（Retry/Replan/Resume）

- **节点级重试**（`packages/workflows/src/dag-executor.ts:439,508-519`）：DAG 节点支持 `retry` 配置——`max_attempts`（最大尝试次数）、`delay_ms`（重试延迟，默认 `DEFAULT_NODE_RETRY_DELAY_MS`）、`on_error`（默认 `transient`，仅瞬时错误重试）。
- **Loop 迭代上限**（`schemas/loop.ts:19-48`）：`max_iterations` 防止无限循环；`until` 完成信号提前退出。
- **人审重试门**：`interactive` 模式下每次迭代需人工确认，失败可重新迭代。
- **停滞恢复**：`fresh_context` 每次迭代用全新上下文，防止上下文累积导致的退化。
- **对 nop 的启示**：nop-task 的 `RetryTaskStepWrapper` 对应 `max_attempts`；`on_error: transient`（区分瞬时/永久错误）值得在 AgentTaskStep 中显式化。

## 四、优缺点

### 优点
1. 确定性 YAML 工作流 + AI 智能注入特定节点的混合模型——兼顾可控性与灵活性。
2. Trigger Rule 体系覆盖四种依赖语义，比简单"前驱完成"更丰富。
3. 声明式 hook（matcher+response）零代码配置。
4. include/workflow 双层组合区分静态与动态。

### 缺点
1. 强绑 Claude Code SDK。
2. 纯 TypeScript 无 JVM 集成。
3. 聚焦 coding 场景，非通用 agent。

## 五、结论

Archon 是"AI coding 工作流引擎"的成熟参考，Trigger Rule + 声明式 Hook + 双层组合三项可直接丰富 nop plan/DSL。

## 六.5 Harness 机制维度覆盖（对照参考框架 D1-D12）

> 参考：`2026-08-01-harness-mechanism-reference-framework.md`（Agent Harness 十二大机制维度）

覆盖维度：**D1**（YAML DAG 执行器+拓扑并发）、**D5**（Trigger Rule+Loop 机制+include/workflow 双层）、**D8**（21 per-node 声明式 Hook）、**D12**（节点级 retry max_attempts/on_error）。缺失/薄弱：D2（文件系统）、D6（审批）、D9（质量门）。

## 对比结论：nop-ai-agent 全面超越性分析

**nop-ai-agent 已超越的部分**：
- **流程引擎**：nop-task（GraphTaskStep/ChooseTaskStep/Retry）比 archon 的 YAML DAG 执行器更成熟（nop-task 已落地 team 集成）。
- **hook**：nop 12 个 AgentLifecyclePoint 比 archon 的 21 per-node hook 更系统化（nop 是引擎级 + middleware，archon 是声明式 matcher）。
- **DSL**：nop XDEF 类型化 DSL 比 archon 的 YAML 更强大（编译期校验）。

**必要参考的增量（以超越方式吸收）**：
- **Trigger Rule 体系**（all_success/one_success/none_failed_min_one_success/all_done）：nop plan DAG 依赖语义可增加 trigger 类型——以 nop-task 的 transition 条件实现。
- **include/workflow 双层组合**（静态展平 vs 运行时子 run）：nop plan 的静态嵌入/动态子任务可参考——nop-task CallTaskStep 已覆盖运行时子任务。

**总评**：nop-ai-agent **全面超越** archon（nop-task 复用 + XDEF DSL + 12 点 hook）；Trigger Rule + 双层组合作为 plan 语义参考吸收，实现 nop 原生。

## References
- `~/ai/archon/packages/workflows/src/dag-executor.ts:1-7`、`schemas/{dag-node.ts:23-28,500-510,loop.ts:19-48,hooks.ts:10-32}`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-plan-dsl.md`、`nop-ai-agent-hook-skill-engine.md`
- `ai-dev/analysis/agent-survey/2026-08-01-jcode-dag-first-agent-analysis.md`、`2026-08-01-spec-kit-workflow-engine-analysis.md`、`2026-08-01-agent-governance-toolkit-analysis.md`
