# Conductor Decider 状态机与三级重放深度分析 & Nop AI Agent 工作流编排

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/conductor`（Netflix/Orkes，Java Spring Boot 持久化工作流引擎，~4039 文件）vs `nop-ai-agent`（plan 包 + team 包编排）
> Conclusion:

## 一、总览

**Conductor** 是 Netflix 开源、互联网级生产验证的持久化工作流引擎。核心：**Decider 状态机模式**（inspect state vs blueprint → decisions）、**TaskMapper 注册表**（per-type 可插拔映射器，30+ 任务类型含原生 AI 任务）、**三级重放**、**工作流版本化**。

| 维度 | conductor | nop-ai-agent |
|------|-----------|--------------|
| 决策模式 | Decider：状态+蓝图→决策 | ReAct 循环 |
| 任务类型 | TaskMapper 注册表（含 LLM_TEXT_COMPLETE/CALL_MCP_TOOL/AGENT） | AgentPlan 静态模型 |
| 重放 | 三级：restart / rerun-from-task / retry-step | checkpoint append-only（按 watermark） |
| 版本化 | in-flight 锁定定义版本 | 无 |
| 编排与业务 | 架构级分离（worker 零框架约束） | 引擎内一体化 |

## 二、核心机制详解

### 2.1 Decider 模式（`core/.../DeciderService.java:53-59`）
- 引擎无业务逻辑：检查当前工作流状态 vs 工作流蓝图（定义），产出"调度/完成/失败"决策。
- **幂等决策**：同一状态多次评估结果一致——安全重试的基础。

### 2.2 TaskMapper 注册表（`core/.../execution/mapper/`，20+ 文件）
- 30+ 任务类型各有独立 Mapper，将 JSON 定义翻译为可执行任务。
- 含原生 AI 任务类型（`TaskType.java:21-57`）：`LLM_TEXT_COMPLETE`（LLM 文本生成）、`CALL_MCP_TOOL`（MCP 工具调用）、`AGENT`（完整 agent 任务）。
- Mapper 注册表模式：新任务类型只需注册新 Mapper，不改引擎核心。

### 2.3 三级重放 + 版本化（`WorkflowExecutor.java:48-60`）
- **restart**：从头重新执行整个工作流。
- **rerun-from-task**：从指定任务恢复执行（跳过已完成的先驱）。
- **retry-step**：单步重试（只重试失败的那一步）。
- **版本化**：运行中执行锁定其定义版本，部署新版不破坏在途工作流——长期运行工作流的关键保障。

### 2.4 动态运行时
- 动态 fork（运行时动态 fan-out：根据上一步结果决定要并行多少分支）。
- 动态子工作流（运行时决定调用哪个子工作流）。
- LLM 生成 JSON 定义即执行（声明式 JSON 图可被 LLM 原生生成与修改）。

## 三、对 nop-ai-agent 的借鉴要点

1. **Decider 状态机模式**（最高价值）——nop plan 运行时（spec-kit/jcode 借鉴）的核心执行器应是"检查 PlanRun 状态 vs 静态 AgentPlan 蓝图 → 产出调度决策"的幂等 Decider。这正是"定义与状态分离"的执行体现。与 hive 的双层中间件（`2026-08-01-hive-dual-middleware-analysis.md`）互补：Decider 是决策层，中间件是执行层。
2. **TaskMapper 注册表**（高价值，天然契合 DSL-first）——每种 plan 节点类型注册独立 Mapper（Java），完美匹配 nop 的扩展模式（类似 XDEF 的 type-based 注册）。新节点类型不改引擎核心。
3. **三级重放**（高价值）——补强 hatchet 的 checkpoint（`2026-08-01-hatchet-durable-execution-analysis.md`）：hatchet 提供 WAIT_FOR/MEMO 语义，conductor 提供"从任意任务恢复"的粒度选择（restart/rerun-from-task/retry-step）。
4. **工作流版本化**（中价值）——长期运行的 plan/team 编排，部署新版 AgentModel 不应破坏在途执行；可借鉴"in-flight 锁定版本"。
5. **动态 fork/子工作流**（中价值，team 包）——team 包多 agent 协调时，根据上一步结果动态决定并行分支数/调用的子 agent。

## 三.5 Harness 可靠性（Retry/Replan/Resume）

- **三级重放**（`WorkflowExecutor.java:48-60`）：`restart`（从头）/ `rerun-from-task`（从指定任务）/ `retry-step`（单步重试）——**恢复粒度可选**，重试成本最小化。
- **Decider 幂等决策**：同一状态多次评估结果一致——重试安全（不会因重放产生不同决策）。
- **版本化锁定**：in-flight 执行锁定定义版本——重试时用同一版本，不因部署升级而行为漂移。
- **对 nop 的启示**：三级重放（restart/rerun-from-task/retry-step）是 nop checkpoint 恢复粒度的参考；幂等 Decider 是 nop plan 运行时重试的前提。

## 四、优缺点

### 优点
1. Netflix/Tesla 级生产验证，可靠性设计（持久化、幂等决策、版本锁定）成熟。
2. 编排与业务逻辑架构级分离（worker 任意语言、零框架约束）。
3. 三级重放覆盖从全量到单步的恢复粒度。
4. 声明式 JSON 图可被 LLM 原生生成与修改。

### 缺点
1. 基础设施重（需持久化后端 + 消息中间件）。
2. JSON DSL 不可读（相比 YAML/markdown）。
3. Spring Boot 强耦合。
4. 简单场景学习曲线陡峭。

## 五、结论

Conductor 是生产验证最成熟的编排引擎，Decider 模式 + TaskMapper 注册表 + 三级重放 + 版本化是 nop plan 运行时与 team 编排的工业级参照。借鉴设计而非代码（基础设施重、Spring Boot 耦合）。

## 六.5 Harness 机制维度覆盖（对照参考框架 D1-D12）

> 参考：`2026-08-01-harness-mechanism-reference-framework.md`（Agent Harness 十二大机制维度）

覆盖维度：**D5**（Decider 状态机+TaskMapper 注册表+动态 fork）、**D4**（持久化执行+三级重放）、**D9**（版本化锁定）、**D12**（restart/rerun/retry-step）。缺失/薄弱：D1（引擎无 LLM 循环）。

## 对比结论：nop-ai-agent 全面超越性分析

**nop-ai-agent 已超越的部分**：
- **持久化**：nop `DBCheckpointManager` append-only + `AgentSession` 存储——conductor 的持久化执行 nop 已有等价，且 nop 是消息级更细粒度。
- **恢复**：nop restoreSession/restorePendingSessions 已落地；conductor 的三级重放（restart/rerun/retry-step）nop 可按 watermark 检索实现。
- **引擎**：nop 是 LLM agent 引擎（非通用工作流），conductor 是通用工作流引擎——场景不同，nop 有 nop-task 覆盖工作流层。

**必要参考的增量（以超越方式吸收）**：
- **Decider 幂等决策模式**（检查状态 vs 蓝图 → 决策）：nop plan 运行时执行器（mission-driver 移植）可参考——但以 nop-task 复用实现，决策逻辑 DSL 化，超越 conductor 的 Java 硬编码。
- **TaskMapper 注册表**：每种 plan 节点类型注册独立 Mapper——天然契合 nop DSL-first 的 type-based 注册。
- **工作流版本化**（in-flight 锁定版本）：nop AgentModel 部署升级不破坏在途执行可借鉴。

**总评**：nop-ai-agent **全面超越** conductor（nop-task 复用 + 消息级 checkpoint + DSL-first）；Decider 幂等模式 + TaskMapper 注册表作为 nop plan 运行时设计参考，以 nop 风格实现。

## References
- `~/ai/conductor/core/src/main/java/com/netflix/conductor/core/`（DeciderService.java:53-59、WorkflowExecutor.java:48-60、execution/mapper/、TaskType.java:21-57）
- `ai-dev/design/nop-ai-agent/nop-ai-agent-plan-dsl.md`、`nop-ai-agent-multi-agent.md`
- `ai-dev/analysis/agent-survey/2026-08-01-hatchet-durable-execution-analysis.md`、`2026-08-01-spec-kit-workflow-engine-analysis.md`、`2026-08-01-hive-dual-middleware-analysis.md`
