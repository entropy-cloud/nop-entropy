# Flink Agents vs nop-ai-agent 深度对比分析

> Status: open
> Date: 2026-10-13
> Scope: `~/sources/flink-agents`（apache/flink-agents，0.4-SNAPSHOT，Java+Python 的 ASF Agentic AI 框架，HEAD `5edd3d5`）vs `nop-ai/nop-ai-agent`（2.0.0-SNAPSHOT，Java Agent 运行时引擎）
> Conclusion: Flink Agents 把 agent 执行**降格为 Flink DataStream 算子内的事件路由**——"计划=静态事件触发图、持久化=Flink keyed state + Kafka/Fluss 外置 action state、跨语言=py4j/Pemja 双桥 + plan JSON 字节级一致"；nop-ai-agent 是**库内嵌 + DB 协调的轮次引擎**，治理/团队/恢复完备但 plan 域未接线、引擎非流式。两者真正互补的是"状态基础设施"与"治理/声明模型"两条轴；Flink 算子形态本身不值得 nop 引入

---

## 一、总览

**Flink Agents** 是 ASF Flink 伞下的独立项目（2025-06 建仓），README 一句话"Agentic AI framework based on Apache Flink"明显低估了实现：它把 agent 的 action 图直接编译成一个 Flink `AbstractStreamOperator`，用 Flink 的 keyed state / checkpoint / Kafka·Fluss state backend 解决 agent 最难的持久化与恢复问题，并以 Java/Python 双实现 + plan JSON 快照做跨语言一致性。

| 维度 | Flink Agents | nop-ai-agent |
|------|--------------|--------------|
| 形态 | **Flink 算子**（`ActionExecutionOperator`，1911 行），跑在 Flink 集群作业里 | **库内嵌 JVM 引擎**（`DefaultAgentEngine`），普通服务进程 + DB 协调多实例 |
| 版本/成熟度 | 0.4-SNAPSHOT，pre-1.0，无 git tag，~73K 行 Java main + ~82K Python | 2.0.0-SNAPSHOT 内部版，531 main / 483 test 文件，~74K 行 Java |
| 模型定义 | code-first（`Agent.addAction`/`@Action`）+ YAML DSL（`YamlLoader`/`AgentSpec`） | xdef 声明（`agent.xdef` → `*.agent.xml`）+ **delta 覆盖** + recipe 合并 |
| 执行模型 | **事件驱动 action 图**：`Action.triggerConditions`（CEL）路由，图可含环，无轮次概念 | **轮次 ReAct 循环**：`ReActAgentExecutor` 外层 while + 迭代上限，round boundary 是唯一注入点 |
| LLM 调用 | action 内同步 `chat(...)`（`ChatModelAction.java:352`） | 引擎内同步 `chatService.call` + 墙钟超时（`LlmCallCoordinator`）——**两边都非 token 流式** |
| 持久化 | 三层：Flink keyed state（任务/事件/记忆）+ `DurableExecutionManager` 幂等重放 + **Kafka/Fluss 外置 action state store** | 会话三存储（内存/文件/DB）+ checkpoint journal（幂等键）+ session journal 增量折叠 |
| 恢复 | Flink checkpoint 恢复（算子状态重建），action 结果按 durable id 重放 | `ScheduledRecoveryManager` daemon + `restorePendingSessions` 批扫 + DB 租约/锁接管 |
| 治理 | 薄：Bash 命令白名单（tree-sitter 解析）、`allowed_commands`/script dirs | **七层**：PermissionMatrix / PathAccessChecker / ApprovalGate / DenialLedger / Docker 沙箱 / Guardrail / AuditLogger + Budget |
| 多 agent | sub-agent = 一种 `ResourceType.AGENT` 资源，模型侧暴露 `_subagent_<name>` 工具，**算子内 per-key 执行** | call-agent 工具（异步邮箱 + 同步兜底 + 委托深度钳制）+ `<team>` 声明 + **nop-task DAG 真实运行时** + ACL |
| 跨语言 | **Java/Python 1:1 双实现**：driver 走 py4j（PyFlink），task 内走 Pemja，plan JSON 做字节级快照对账 | Java 单语言，跨语言诉求由 **XLang/xpl DSL** 承担 |
| 计划域 | 静态事件图（编译期定死），"计划"由 ReAct 循环内 LLM 即兴决定；**无 replanner** | `plan/runtime/`（`PlanRunner`/`PlanReplanner`/`StagnationDetector`）**存在但未接线**——engine `mode="plan"` 抛 `ERR_AGENT_PLAN_MODE_NOT_IMPLEMENTED`（`AgentExecutorResolver.java:219`） |
| 模型接入 | 9 个 Java provider + Python 独占项；`MODEL_ROUTER` 是一等资源（规则 / LLM-judge 策略） | `IChatService` + gateway（failover/限流/路由）；`SmartModelRouter` 感知预算降级 |
| 内存 | sensory（每 run 清）/ short-term（TTL）= Flink MapState；long-term 可插拔（仅 Mem0 实现） | 上下文压缩管线（`PipelineCompactor` 三层 + spill）+ memory 工具 + 无独立长期记忆后端 |
| 运行成本 | 需要 Flink 集群（session/job 模式），checkpoint 与算子线程耦合 | 单 JVM 起步，横向扩展靠 DB 锁/租约，无额外基础设施 |

**核心结论先行**：这是两条**正交**的技术路线——flink-agents 回答"如何让 agent 像流作业一样获得 exactly-once 级的状态与恢复"，nop-ai-agent 回答"如何让 agent 在企业 JVM 应用里可声明、可治理、可编队"。值得 nop 拿走的是**机制**（durable call 幂等重放、外置 action state + 恢复标记对齐清理、跨语言序列化对账测试、CEL 触发条件校验、MODEL_ROUTER 一等资源化、tree-sitter 级命令白名单），而不是**形态**（Flink 算子、py4j/Pemja 双桥）。

---

## 二、Context（调研背景）

- **为什么需要这个分析**：agent-survey 目录已覆盖 60+ 外部 agent 项目（opencode/agentscope/mosshell/agno 等），但**尚无一个"数据流基础设施厂商"做的 agent 框架**。Flink Agents 是 ASF 伞下项目，其"把 agent 执行放进流算子"的路线在 survey 中独一无二，且与 nop"可逆计算 + 声明式模型"路线形成有意思的对照。
- **要回答的问题**：flink-agents 的核心机制（事件图/算子执行/三层持久化/跨语言）具体如何实现？与 nop-ai-agent 的轮次引擎 + DB 治理路线差异在哪？哪些机制值得 nop 吸收，哪些形态不该学？
- **约束**：flink-agents 是 pre-1.0（0.4-SNAPSHOT），治理/安全面基本空白；nop-ai-agent 治理完备但 plan 域未接线、引擎非流式。**本分析基于两侧实际源码，不采信 README/文档的单方宣称**（差异见 §3.9、§5）。
- **源码位置**：flink-agents 在 `~/sources/flink-agents`（浅克隆，50 commits）；nop-ai-agent 在 `nop-ai/nop-ai-agent`。

---

## 三、Flink Agents 实现详解

### 3.1 模块结构与依赖方向

Maven 聚合（根 `pom.xml`）：`api, examples, plan, runtime, e2e-test, integrations, dist, ide-support`；`python/` 与 `docs/` **不是** Maven 模块。

| 模块 | artifactId | 职责 |
|------|-----------|------|
| `api/` | flink-agents-api | 纯抽象，Flink 依赖为 `provided`，不依赖任何 flink-agents 模块 |
| `plan/` | flink-agents-plan | 编译器 + plan IR：`AgentPlan`、Action、CEL 条件、资源 provider、Bash 工具 |
| `runtime/` | flink-agents-runtime | Flink 算子与全部运行时服务 |
| `integrations/` | chat-models / embedding-models / vector-stores / mcp | 4 个子模块 |
| `dist/` | flink-agents-dist-flink-{1.20..2.3} | 按 Flink 版本 shade uber-jar |

- 依赖方向严格 `runtime → plan → api`，无反向边。
- 唯一例外是 **api 用反射回指 runtime**：`api/.../AgentsExecutionEnvironment.java:89-97` 里 `Class.forName("…runtime.env.RemoteExecutionEnvironment")`——为保住 Maven 依赖方向不惜反射，解耦洁癖可见一斑（也说明 api/runtime 边界并非天然干净）。

### 3.2 API 层：Agent / Action / Event / Resource

- **`Agent`（`api/.../agents/Agent.java:36`）不是接口，是一个可变注册表**：`Map<String, Tuple3<String[],Function,Map>> actions` + `Map<ResourceType, Map<String,Object>> resources`。`addAction`/`addResource`/`addResourcesIfAbsent` 就是全部装配 API。
- **`ReActAgent extends Agent`**（`ReActAgent.java:67`）：构造时注册 `_default_chat_model` 资源，并把 `InputEvent.EVENT_TYPE` 绑到 `startAction`；`startAction` 发 `ChatRequestEvent`，`@Action(EventType.ChatResponseEvent) stopAction` 发 `OutputEvent`。**ReAct 循环不是框架内置的循环，而是由事件边串起来的三个内置 action**。
- **事件是一等公民**：`Event` 基类带 `id/type/attributes/attachments/upstreamEventId/upstreamActionName`，内置 17 种类型（`_input_event`、`_chat_request_event`、`_tool_request_event`、`_context_retrieval_*`、`_long_term_{get,search,update}_event`、`_sensory_*`、`_short_term_*`、`_model_routing_event`…）。
- **`ResourceType` 枚举（`ResourceType.java:26-37`）**：`CHAT_MODEL, CHAT_MODEL_CONNECTION, EMBEDDING_MODEL, VECTOR_STORE, PROMPT, TOOL, MCP_SERVER, SKILLS, MODEL_ROUTER, AGENT`——**sub-agent、模型路由、技能都统一为资源**，这是它跨语言序列化能简化的根因。
- **`RunnerContext`（action 执行期上下文）**：`sendEvent` / 三层记忆读写 / `getResource` / `executeAsync` / **`durableExecute(DurableCallable)`** / `gather`。`durableExecute` 是幂等重放的入口（见 §3.5）。
- **三种装配路线都真实可用**：① 代码 `addAction` + `@Action` 注解（Java）/ `@action` 装饰器（Python，`python/flink_agents/api/decorators.py:53`）；② `env.fromDataStream(...).apply(agent).toDataStream()`；③ YAML DSL（`api/yaml/YamlLoader.java:339` + `YamlAgentsDocument/AgentSpec/ActionSpec/ToolSpec`，`env.loadYaml(path)`）。

### 3.3 Plan 层："计划"其实是静态事件触发图

- 核心 IR `plan/AgentPlan.java:93`（Jackson 可序列化）：`Map<String, Action> actions` + `Map<ResourceType, Map<String, ResourceProvider>> resourceProviders`。构造函数从 `Agent` 抽取动作/资源、扫描注解、做 sub-agent 环检测。
- 内置 action 三个：`ChatModelAction` / `ToolCallAction` / `ContextRetrievalAction`（`AgentPlan.java:254-256`）。
- `plan/actions/Action.java:43`：`name` + `Function exec(Event, RunnerContext)` + **`List<String> triggerConditions`** + `config`。条件经 `plan/condition/ConditionExpressionValidator` 编译为 CEL 表达式（`ExpressionCondition`）或事件类型匹配（`TriggerCondition`）。
- **图结构 = 事件类型/CEL 条件的有向图，可含环**（官方文档 `docs/content/docs/development/workflow_agent.md:36-38` 原话）。没有拓扑序、没有 DAG 校验、**没有动态 replanner**——"planning" 完全是 ReAct 循环里 LLM 的运行时决策。
- 结论：**flink-agents 的 plan 模块是"编译器"，不是"规划器"**。它把 agent 定义编译成可序列化的 `AgentPlan` JSON（供 Python 侧/远端任务重建），"plan" 这个词在该项目里与 nop 的 `AgentPlan`（phases/tasks/gates 显式计划域）**不是同一概念**。

### 3.4 Runtime：算子内的邮箱执行

- **执行载体是 Flink 算子**：`runtime/CompileUtils.java:100-118` 把 keyed 流 `transform(agentName, outType, new ActionExecutionOperatorFactory(...))`。
- `runtime/operator/ActionExecutionOperator.java:121`（1911 行）`extends AbstractStreamOperator<OUT> implements OneInputStreamOperator, BoundedOneInput`：
  - 输入包装为 `InputEvent`（`:489`）→ `EventRouter`（`:544`）按事件类型 + CEL 匹配 action → `ActionTask` 入队 → 在**同一算子内**用 `mailboxExecutor.submit(...)`（`:626`）消费；
  - 只有 `_output_event` 走 `output.collect` 向下游吐出（`:563-576`），其余事件**在算子内回环**（`:1868`）；
  - 每 key 有独立 pending 队列（`enqueuePendingInputEvent`，`:508`）。
- ReAct 执行链（全部在算子内）：`InputEvent → ReActAgent.startAction → ChatRequestEvent → ChatModelAction → ToolRequestEvent → ToolCallAction → ToolResponseEvent → ChatModelAction … → ChatResponseEvent → stopAction → OutputEvent`。
- 并行策略二选一：mailbox 协作式（默认）或 `runtime/operator/parallel/*` 的 worker 池（`parallelExecutionWithoutCoroutineEnabled`，兼容 JDK<21）。
- **LLM 调用是同步的**（`plan/actions/ChatModelAction.java:352` 的 `chat(...)`）——即 flink-agents 的"流式"是 **DataStream 输入流层面的流式，不是 LLM token 流式**；长 LLM 调用占用算子执行线程，靠 checkpoint 语义与异步批量执行（`runtime/async/AsyncBatchExecutor`）权衡。

### 3.5 持久化：三层，正好是 nop 最值得研究的部分

| 层 | 机制 | 证据 |
|----|------|------|
| ① 工作状态 | Flink keyed/operator state：`ListState<ActionTask>` + `ListState<Event>` pending 输入 + `MapState` sensory/short-term 记忆 + `ValueState<Long>` 序号 | `runtime/operator/OperatorStateManager.java:82-143` |
| ② 幂等调用重放 | `DurableExecutionManager`（503 行）：`ValueState<Long> lastCompletedSequenceNumber` + `UnionListState` 恢复标记，按 durable id（`"chat:<model>"`、`"tool-call:"`、`"route:<router>"`）**重放已算出的结果而不是重跑** | `runtime/operator/DurableExecutionManager.java:148` |
| ③ 外置 action 结果 | `ActionStateStore` SPI（`BackendType {KAFKA, FLUSS}`）+ `KafkaActionStateStore`/`FlussActionStateStore`，配 **checkpoint 对齐的清理协调器与恢复标记** | `runtime/actionstate/*`；配置键 `api/configuration/AgentConfigOptions.java:65-149` |

- 长期记忆可插拔（`runtime/memory/`），**目前只有 `Mem0LongTermMemory` 一个实现**（Pemja 调 Python mem0）。
- 事件/轨迹日志可插拔（`EventLogWriter` + SLF4J/File logger）。
- 没有"session service"概念——唯一的 session 是 sub-agent 调用的 `sessionId` 索引（`RunnerContextImpl.java:168`）。

**要点**：flink-agents 把"agent 执行到一半崩了怎么办"完全外包给 Flink checkpoint 机制 + 一层显式的幂等重放账本。这与 nop 的"checkpoint journal + 幂等键发散检测 + 会话重建"是**同一问题的两种解**：一个靠流处理基础设施，一个靠应用层账本。

### 3.6 跨语言：双桥 + 字节级对账

- **driver 侧（Python 启动作业）**：py4j——`python/flink_agents/runtime/remote_execution_environment.py:189` 经 `pyflink.util.java_utils.invoke_method` 调 Java `CompileUtils.connectToAgent(KeyedStream, String agentPlanJson)`（`runtime/CompileUtils.java:48-68`）。Python 先自行编译出 `AgentPlan` JSON 再交给 Java。
- **task 侧（算子内执行 Python action）**：**Pemja**（JVM 内嵌 CPython）——`runtime/python/PythonBridgeManager`、`PythonActionExecutor`（入口 `flink_runner_context.create_flink_runner_context`），Python 侧 `FlinkRunnerContext` 约 1784 行。
- **一致性保障是测试而非文档**：`e2e-test/cross-language-agent-plan-snapshots/{java,python}/agent_plan_with_internal_subagent.json` 做**字节级 plan JSON 快照对账**，另有 event snapshots、trigger-condition fixtures 跨语言夹具。
- 最近一次 HEAD 提交 `5edd3d5`（#1242）正是这条路线的收口：**sub-agent 统一为普通 `ResourceDescriptor`**（`InternalSubagentResourceDescriptorBuilder.java:28-31`，args = `agent/description/input_schema`），删除 Python 特判序列化路径——Java/Python 用同一套描述符表达 sub-agent。

### 3.7 多 agent / sub-agent

- 注册：`Agent.addResource(name, ResourceType.AGENT, agent)`（裸 `Agent` 自动包装，`Agent.java:150-153`）。
- 模型可见性：`BaseChatModelSetup.java:172` 把 sub-agent 包装成 `SubagentTool`，callable 名 `_subagent_<name>`（`SubagentSetup.java:43`）。
- 分发：`ToolCallAction.java:139-146` 识别 `_subagent_` 前缀 → 解析 `AGENT` 资源而非 `Tool`。
- 执行：`runtime/subagent/InternalSubagentSetup`——mailbox 限定、延迟执行，子 `AgentPlan` 序列化进描述符、远端任务重建后执行；`RunnerContextImpl.registerInternalCallOwner/awaitSubagentCall`（`:244-282`）做静默期汇合。
- **没有 broker/scheduler 服务**：编排完全在算子内、按 key 进行。即"多 agent = 同一 keyed 流里的嵌套 action 调用"。

### 3.8 工具与集成

- 定义：`@Tool`（必须 static → `FunctionTool`）/ `@tool` 装饰器 / `Tool` 子类 / `MCPTool`；注册为 `ResourceType.TOOL` 资源，在 chat model 描述符 `tools=[...]` 中按名引用。
- 执行：`ToolCallAction.processToolRequest`（`ToolCallAction.java:74`）→ 顺序或并行 fan-out（`TOOL_CALL_ASYNC` + `ASYNC_BATCH_PARALLELISM`），可挂 durable id `"tool-call:"`。
- 内置：`plan/tools/bash/BashTool` + `BashValidator`（**tree-sitter-bash 解析 AST**，`allowed_commands` 白名单，`ChatModelAction.java:291-304` 注入）、`runtime/skill/LoadSkillTool`。
- MCP：`integrations/mcp` 用官方 `io.modelcontextprotocol` Java SDK（`McpSyncClient` + streamable HTTP transport），带 Bearer/Basic/ApiKey 认证。
- Provider 覆盖：Java chat = ollama / openai（含 Azure、vLLM）/ anthropic / bedrock / gemini / watsonx；embedding = ollama/openai/bedrock；vector = elasticsearch/milvus/opensearch/s3vectors。**Python 独占**：dashscope、chroma、mem0——**两语言 provider 集合并不对等**（`api/resource/ResourceName.java` 是双语言命名对账中心）。

### 3.9 文档/宣称 vs 代码

- 代码 > 宣称：README 一句话远不及实际（DataStream 算子、Kafka/Fluss action state、MCP、模型路由、sub-agent 全部真实存在）。
- 代码 < 宣称/预期：`pom.xml` 仍留 `<!-- TODO: build website -->` 与占位 `<name>Flink Agents : </name>`；`feedback/*` 整包是从 flink-statefun 拷来的（`Checkpoints.java:26` 注释自认）；治理/安全面基本只有 Bash 白名单。
- 测试与流程是 ASF 正规军：360 个 Java 测试类 + 128 个 Python 测试 + 56 个 e2e 文件；Hugo 文档中英双语；`AGENTS.md`/`code_review.md`/`review-guides/python-java-bridge.md`；AI 辅助提交必须带 `Generated-by:`。

---

## 四、nop-ai-agent 现状速览（对照面）

细节见 `ai-dev/design/nop-ai-agent/`（40+ 专题 design 文档），此处只列与对比相关的骨架：

- **声明模型**：xdef 驱动（`nop-kernel/nop-xdefs/.../nop/schema/ai/agent.xdef` 定义 AgentModel：mode、tools/activeTags/denyTags、permissions、constraints、hooks、middlewares、team、recipes），运行期从 VFS `/<name>.agent.xml` 加载并经 recipe 合并，**支持 `_delta` 覆盖**（测试实证 `test-team-delta-base.agent.xml`）。工具是 `*.tool.xml`，技能 `*.skill.yaml`。
- **引擎**：`IAgentEngine`/`DefaultAgentEngine` → `AgentExecutorResolver.resolveExecutor` 分派：`react`（`ReActAgentExecutor`，1981 行）/ `single-turn` 可用，**`plan` 抛 `ERR_AGENT_PLAN_MODE_NOT_IMPLEMENTED`**（`AgentExecutorResolver.java:219`）；`plan/runtime/` 的 `PlanRunner`/`PlanReplanner`/`StagnationDetector` 独立存在但未接线。
- **轮次模型**：外层 while（sustainer 续命）+ 内层迭代上限（默认 10）；steering 消息只在 round boundary（工具全部完成后、下次 LLM 调用前）注入；LLM 调用是同步 `chatService.call` + 墙钟超时——**`callStream` 只在 API/gateway 层，引擎不消费**。
- **状态/恢复**：三会话存储（内存/文件/DB）+ `ai_agent_session_journal` 增量折叠；`reliability/` checkpoint journal（`CheckpointType.LLM_TURN/TOOL_EXECUTION` + 幂等键）；`ScheduledRecoveryManager` + `DbDaemonCoordinator` 跨进程协调；`ai_agent_session_lock` 租约 + `SessionLockRenewal` 防双跑。
- **治理**：§1 表格七层全部有实现类（`security/`、`guardrail/`、`budget/`），由 `engine/AgentSecurityConsultation` 的 7 检查点链统一编排；**默认多为 NoOp，功能实现需 opt-in**。
- **多 agent**：`CallAgentExecutor`（委托深度 + `ParentPermissionConstraint` 钳制子权限）+ `<team>` 声明 → `TeamModelConverter` → `ITeamManager`（DB/内存实现）+ **nop-task 集成**（`team/flow/` 的 `MemberAgentTaskStep` 等 5 个 `ITaskStep`、`TeamTaskFlowOrchestrator`）+ `TeamTaskSchedulerDaemon` 调度。
- **模型层**：`IChatService`（含 `callStream`）+ gateway failover/限流 + `SmartModelRouter`（复杂度 + `BudgetSnapshot` 预算耗尽降级）+ `ITalent` 动态准入 + 压缩管线（`PipelineCompactor` 三层 + spill）。

---

## 五、机制级对比

### 5.1 时间模型：事件级 vs 轮次级

| | Flink Agents | nop-ai-agent |
|---|---|---|
| 调度粒度 | **单个事件**（Event → 触发条件匹配 → ActionTask） | **一整轮**（reasoning → adjudication → tool fan-out → round boundary） |
| 外部注入时机 | 任何事件都可入队，per-key FIFO | 仅 round boundary `ctx.drainSteering` |
| 取消 | 算子任务队列与 mailbox 管理 | 多点检查 `ctx.isCancelRequested()` + forced cancel + 租约丢失强制失败 |
| 中间结果 | 每个 action 完成即入 durable 账本 | LLM_TURN / TOOL_EXECUTION checkpoint |

nop 此前 survey（MOSShell 对比）已指出轮次模型的代价：round boundary 是唯一注入/检查点时刻。flink-agents **同样不是 token 级双工**（LLM 调用同步），但它的事件粒度调度让"工具完成即触发下一个动作"不需要等待整轮结束——这是介于"轮次模型"和"token 级流式"之间的第三种时间模型：**事件级异步**。

### 5.2 恢复模型：基础设施重放 vs 应用账本重建

- flink-agents：崩溃 → Flink checkpoint 恢复 keyed state → 未完成 `ActionTask`/pending 事件原地重放 → `DurableExecutionManager` 按 durable id **跳过已成功的 chat/tool 调用** → 外置 Kafka/Fluss store 持有跨 checkpoint 的 action 结果。
- nop-ai-agent：崩溃 → daemon 扫描 pending 会话 → 按 checkpoint journal 校验一致性 → 会话重建续跑；工具副作用靠 RecoveryMode 文档约定幂等。

**判断**：flink 的 durable id 重放比 nop 的"检查点 + 约定"更强硬——它把"哪次 LLM 调用/工具调用已成功"做成状态机里可查询的账本，恢复时确定性跳过。nop 的 checkpoint journal 已有幂等键（`ai_agent_checkpoint.idempotencyKey` 列），**差距在"恢复路径是否消费这个键去跳过重放"而非"有没有键"**——值得对照 `DurableExecutionManager` 审计 nop 的 `restoreSession` 是否真正做到工具级幂等跳过。

### 5.3 声明模型：YAML 无 delta vs xdef + delta

- flink-agents YAML：`YamlLoader` → `AgentSpec`，一次性编译进 `AgentPlan` JSON。**没有 delta/覆盖/分层定制概念**——改基线 = 改原文件。
- nop：xdef 元模型 + VFS 加载 + `_delta` 覆盖 + recipe 合并，是平台级定制体系（换皮/多租户/版本化升级的根基）。

这是 nop 的**结构性优势**，flink-agents 完全没有对应物（其"跨环境一致性"靠 plan JSON 快照，而非模型分层）。

### 5.4 治理：白名单雏形 vs 七层防线

flink-agents 现有安全机制盘点（全仓库）：
1. `BashValidator` + `allowed_commands`（AST 级命令白名单，tree-sitter 解析）；
2. `allowedScriptDirs`（技能脚本目录白名单，`ChatModelAction.java:292-296`）；
3. `structuredOutputStrategy` 等输出约束。

无权限矩阵、无审批门、无拒绝账本、无沙箱、无内容护栏、无审计日志、无预算。**nop 在此维度全面领先，无争议**——这不是"差异"而是"代差"。

### 5.5 多 agent 编排

| | Flink Agents | nop-ai-agent |
|---|---|---|
| 表达 | `ResourceType.AGENT` + `_subagent_<name>` 工具 | `call-agent` 工具 + `<team>/<team-member>` 声明 |
| 执行位置 | 同一算子、同一 key 内嵌执行 | 独立 session + 异步邮箱（`IAgentMessenger`）/同步兜底 |
| 权限 | 无继承约束 | `ParentPermissionConstraint` 钳制 + `delegationDepth` 上限 + `ITeamAclChecker` |
| 跨实体编排 | 无（单作业内） | **nop-task DAG**（`TeamTaskFlowOrchestrator`）+ 调度 daemon + 卡死回收 |
| 跨语言 | 子 agent 可为 Python 实现（描述符重建） | 不适用 |

flink-agents 的 sub-agent 本质是"**一次带前缀的工具调用**"，强在序列化统一（跨语言字节级一致），弱在没有独立生命周期/权限域/调度。nop 反之：编队与治理强，序列化对账弱。

### 5.6 计划域：两边都没跑通"动态规划"

- flink-agents：静态事件图，无 replanner——"计划" = ReAct 里的即兴决策。
- nop-ai-agent：`PlanRunner`/`PlanReplanner`/`StagnationDetector`/`GateCheckResult` 代码齐全，但 engine 入口抛 `ERR_AGENT_PLAN_MODE_NOT_IMPLEMENTED`。

**这是本次调研最有行动价值的发现之一**：ASF 的 agent 框架同样选择"不做动态规划器"，说明 `mode="plan"` 未接线不完全是 nop 的欠账，而可能是业界共识——**先让事件/轮次循环可靠，规划域等真实需求出现再接线**。区别在于 nop 已把 plan 元模型（phases/tasks/gates/criteria）设计完并写了独立测试，接线成本远低于从零设计。

### 5.7 模型路由与资源化

- flink-agents：`MODEL_ROUTER` 是 `ResourceType`，策略可插拔（`RuleBasedRoutingExecutor`、`LlmJudgeRoutingExecutor`、`CustomRoutingExecutor`），事件类型里专门有 `_model_routing_event`——**路由是执行图里的一个节点**。
- nop：`IModelRouter`（`SmartModelRouter`）在 `LlmCallCoordinator` 内部调用，输入是 Complexity + `BudgetSnapshot`——**路由是 LLM 调用的前置策略**，不进入事件流。

nop 的路由更强（预算感知降级、talent 准入），flink 的路由更"可组合"（作为资源/事件可被 YAML 声明、可被模型触发）。

---

## 六、与当前项目的关系

### 6.1 值得借鉴（按价值排序）

1. **durable call 幂等重放账本**（`DurableExecutionManager`）：恢复时按 durable id 确定性跳过已成功调用。→ 审计 nop `restoreSession`/RecoveryMode 是否真在恢复路径消费 `ai_agent_checkpoint.idempotencyKey` 跳过重放；若只是"记录不消费"，这是 P0 级恢复语义缺口。
2. **外置 action state + 恢复标记对齐清理**（`ActionStateStore` + `KafkaActionStateCleanupCoordinator`）：外部存储与 checkpoint 边界对齐做垃圾回收。→ nop 的 `ai_agent_session_journal` 折叠清理可参考"与恢复标记对齐"避免清理窗口内崩溃丢账。
3. **跨实现序列化字节级对账测试**（`cross-language-agent-plan-snapshots`）：同一模型在两种实现下产出 JSON 快照逐字节比对。→ nop 可对 `*.agent.xml → AgentModel → plan JSON / prompt 序列化` 做快照钉扎，防 xdef 升级静默漂移（nop 已有快照测试传统，此处是"跨序列化路径对账"的用法扩展）。
4. **CEL 触发条件校验前置**（`ConditionExpressionValidator` 在编译期校验 action 触发条件）：→ nop `plan/runtime` 接线时，对 plan 触发规则/门控表达式做编译期校验而非运行期首跑失败。
5. **Bash 工具 AST 级命令白名单**（tree-sitter-bash + `allowed_commands`）：→ nop 的 bash tool 现为进程级约束，可加 AST 解析做命令级白名单（nop 已有 treesitter 依赖基础）。
6. **`MODEL_ROUTER` 资源化 + LLM-judge 路由策略**：→ `SmartModelRouter` 可增补 LLM-judge 策略与 YAML 可声明路由，路由决策进 hook 可观测。
7. **sub-agent 统一描述符**：把"可调用的另一个 agent"收敛为一个可序列化描述符 + 前缀约定 → nop 的 `call-agent` DSL 参数可对齐此形态，降低跨进程/跨语言传递 agent 引用的耦合。

### 6.2 不可借鉴 / 明确否决

- **Flink 算子形态本身**：把 LLM 长调用放进流算子线程，checkpoint barrier 时延与背压耦合；运维上引入 Flink 集群，与 nop"普通 JVM 服务 + DB"的部署形态冲突。**否决理由**：nop 的问题不是缺流处理基础设施，而是应用层账本语义——§6.1 第 1、2 条拿机制即可，不必拿形态。
- **py4j + Pemja 双语言桥**：Java 进程内嵌 CPython + driver 侧 py4j，复杂度高且两语言 provider 集合不对等。nop 的跨语言诉求已由 XLang/xpl 承担，重复造桥是负资产。
- **api 反射 runtime 的解耦 hack**：为保 Maven 依赖方向用 `Class.forName` 反调，边界脆弱，不值得模仿。
- **无 delta 的一次性 YAML**：模型分层定制缺失，与 nop 可逆计算路线根本冲突。
- **算子内嵌套 sub-agent（无独立生命周期）**：nop 需要的是权限域/调度域隔离的编队，弱形态不解决需求。

### 6.3 对 survey 目录的定位补充

agent-survey 此前的项目多为"agent 框架/编排/harness"，flink-agents 是第一个"**流处理基础设施做 agent**"的样本；与 `2026-06-13-agno-vs-goal-driver-vs-nop-agent-survey.md`（框架层）、`2026-06-08c-openharness-survey.md`（harness 层）形成第三类——**运行时基础设施层**。它证明：状态/恢复可以完全外包给流处理引擎，代价是形态锁定与集群运维。

---

## 七、结论

- **路线判定**：flink-agents = "agent as dataflow operator"（基础设施优先），nop-ai-agent = "agent as governed JVM engine"（声明与治理优先）。两者在**时间模型上其实接近**（都是事件/轮次级、都非 token 流式、LLM 调用都同步），真正的分野在**状态基础设施**与**治理深度**两端。
- **采纳**：§6.1 七条机制级借鉴，其中第 1 条（恢复路径消费幂等键跳过重放）建议列为恢复语义审计项。
- **否决**：Flink 算子形态、py4j/Pemja 双桥、api 反射 runtime、无 delta YAML（§6.2）。
- **意外收获**：ASF 框架同样未交付动态规划器（`plan` 模式在 flink-agents 是静态事件图，在 nop 是未接线抛错），说明 plan 域接线应按 nop 既有 `plan/runtime` 设计小步推进，而非视为必须立刻补齐的行业差距。
- 后续工作：本分析为 `open`，若采纳 §6.1-1/2 进入恢复语义审计，另起 `ai-dev/analysis/` 审计条目或直接开 plan。

## Open Questions

- [ ] nop `restoreSession` 是否在恢复路径**消费** `ai_agent_checkpoint.idempotencyKey` 跳过已成功 LLM/tool 调用？（对照 `DurableExecutionManager` 审计）
- [ ] `ai_agent_session_journal` 折叠清理是否存在"清理窗口内崩溃丢账"风险？是否需要恢复标记对齐？
- [ ] flink-agents 算子内同步 LLM 调用在长超时/慢模型下的 checkpoint 时延表现，有无官方 benchmark？（e2e-test 未见相关用例）
- [ ] `mode="plan"` 接线时，nop 的 plan 触发规则表达式是否已有编译期校验层？
- [ ] flink-agents 0.4 → 1.0 路线图是否包含治理面（权限/审批）？若包含，其分层方式可再对照一次。

## References

- flink-agents 源码：`~/sources/flink-agents`（HEAD `5edd3d5`，0.4-SNAPSHOT）
  - 算子：`runtime/src/main/java/org/apache/flink/agents/runtime/operator/ActionExecutionOperator.java`
  - 幂等重放：`runtime/src/main/java/org/apache/flink/agents/runtime/operator/DurableExecutionManager.java`
  - plan IR：`plan/src/main/java/org/apache/flink/agents/plan/AgentPlan.java`
  - 事件/资源：`api/src/main/java/org/apache/flink/agents/api/{event,resource}/`
  - 跨语言快照：`e2e-test/cross-language-agent-plan-snapshots/`
  - 官方文档：https://nightlies.apache.org/flink/flink-agents-docs-main/
- nop-ai-agent 源码：`nop-ai/nop-ai-agent`
  - 引擎分派：`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentExecutorResolver.java:219`
  - ReAct 循环：`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java`
  - xdef：`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/agent.xdef`
- 设计文档：`ai-dev/design/nop-ai-agent/`（`01-architecture-baseline.md`、`02-execution-model.md`、`-reliability.md`、`-multi-agent.md`、`-plan-dsl.md`）
- 同族 survey：`ai-dev/analysis/agent-survey/2026-06-13-agno-vs-goal-driver-vs-nop-agent-survey.md`、`.../2026-08-03-mosshell-vs-nop-ai-agent-deep-comparison.md`
