# Conductor Decider 状态机与三级重放深度分析 & Nop AI Agent 工作流编排

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/conductor`（Netflix/Orkes，Java Spring Boot 持久化工作流引擎）vs `nop-ai-agent`（plan 包 + team 包编排）
> Conclusion:

## 一、总览

**Conductor** 是 Netflix 开源、互联网级生产验证的持久化工作流引擎（4039 文件，极活跃）。核心：**Decider 状态机模式**（inspect state vs blueprint → decisions）、**TaskMapper 注册表**（per-type 可插拔映射器，30+ 任务类型含原生 AI 任务）、**三级重放**、**工作流版本化**。

| 维度 | Conductor | nop-ai-agent |
|------|-----------|--------------|
| 决策模式 | Decider：状态+蓝图→决策 | ReAct 循环 |
| 任务类型 | TaskMapper 注册表（含 LLM_TEXT_COMPLETE/CALL_MCP_TOOL/AGENT） | AgentPlan 静态模型 |
| 重放 | 三级：restart / rerun-from-task / retry-step | checkpoint 单点 |
| 版本化 | in-flight 锁定定义版本 | 无 |

## 二、核心机制

### 2.1 Decider 模式（`core/.../DeciderService.java:53-59`）
- 引擎无业务逻辑：检查当前状态 vs 工作流蓝图，产出"调度/完成/失败"决策。
- 幂等决策：同一状态多次评估结果一致。

### 2.2 TaskMapper 注册表（`core/.../execution/mapper/`）
- 30+ 任务类型各有独立 Mapper，将 JSON 定义翻译为可执行任务；含 `LLM_TEXT_COMPLETE`/`CALL_MCP_TOOL`/`AGENT` 等原生 AI 任务（`TaskType.java:21-57`）。

### 2.3 三级重放 + 版本化（`WorkflowExecutor.java:48-60`）
- restart（从头）/ rerun-from-task（从指定任务）/ retry-step（单步重试）。
- 运行中执行锁定其定义版本，部署新版不破坏在途工作流。

### 2.4 动态运行时
- 动态 fork（动态 fan-out）、动态子工作流、LLM 生成 JSON 定义即执行。

## 三、对 nop-ai-agent 的借鉴要点

1. **Decider 状态机模式**（最高价值）——nop plan 运行时（spec-kit/jcode 借鉴）的核心执行器应是"检查 PlanRun 状态 vs 静态 AgentPlan 蓝图 → 产出调度决策"的幂等 Decider。这正是"定义与状态分离"的执行体现。
2. **TaskMapper 注册表**（高价值，天然契合 DSL-first）——每种 plan 节点类型注册独立 Mapper（Java），完美匹配 nop 的扩展模式（类似 XDEF 的 type-based 注册）。
3. **三级重放**（高价值）——补强 hatchet 的 checkpoint（`2026-08-01-hatchet-durable-execution-analysis.md`）：hatchet 提供 WAIT_FOR/MEMO 语义，conductor 提供"从任意任务恢复"的粒度选择。
4. **工作流版本化**（中价值）——长期运行的 plan/team 编排，部署新版 AgentModel 不应破坏在途执行；可借鉴"in-flight 锁定版本"。

## 四、结论

Conductor 是生产验证最成熟的编排引擎，Decider 模式 + TaskMapper 注册表 + 三级重放 + 版本化是 nop plan 运行时与 team 编排的工业级参照。局限：基础设施重（持久化后端+消息中间件）、JSON DSL 不可读、Spring Boot 强耦合——借鉴设计而非代码。

## References
- `~/ai/conductor/core/src/main/java/com/netflix/conductor/core/`（DeciderService、WorkflowExecutor、execution/mapper/、TaskType）
- `ai-dev/design/nop-ai-agent/nop-ai-agent-plan-dsl.md`
- `ai-dev/analysis/agent-survey/2026-08-01-hatchet-durable-execution-analysis.md`、`2026-08-01-spec-kit-workflow-engine-analysis.md`
