# SoloEngine vs Mission Driver：设计深度对比分析

> Status: open
> Date: 2026-07-26
> Scope: SoloEngine (Python AI agent platform) vs Mission Driver (Node.js dev-loop orchestration engine)
> Conclusion: 两者名字都有"驱动引擎"的含义，但实际解决完全不同的问题——SoloEngine 是 AI agent 编排引擎，Mission Driver 是 AI 开发过程编排引擎。功能层面几乎无重叠，但设计模式上有有趣的对称性。

## Context

- SoloEngine 是一个通用 AI agent 编排平台，Python 实现，核心是 ReAct agent loop + Canvas DAG 编译器 + MCP 集成
- Mission Driver 是一个 AI 开发工作流编排引擎，Node.js 实现，核心是 FSM state machine + `opencode run` 子进程管理 + 文件级持久化
- 两者共享一个基本假设：由 AI（LLM）驱动执行过程，但前者驱动的是 AI agent 的推理-行动循环，后者驱动的是 AI 开发者的 plan-execute-audit 循环

## 1. 核心定位差异

| 维度 | SoloEngine | Mission Driver |
|------|-----------|----------------|
| 本质 | AI agent 运行时引擎 | AI 开发流程编排引擎 |
| 驱动对象 | LLM 推理→工具调用的 ReAct 循环 | AI 开发者（opencode）执行 plan→audit 的开发生命周期 |
| 输出 | 智能体对话/工具执行结果 | 完成的功能代码、提交记录、文档更新 |
| 使用者 | 终端用户（对话式交互） | AI 开发者（opencode agent） |
| 状态空间 | 对话历史 + tool call 结果 | 任务状态机 + plan checklist + audit 状态 |

**一句话**：SoloEngine 让 AI agent 能"推理并行动"；Mission Driver 让 AI agent 能"规划并交付"。

## 2. 架构对比

```
SoloEngine:                          Mission Driver:
┌──────────────────────┐            ┌────────────────────────┐
│  Canvas (ReactFlow)  │            │  Mission JSON (DSL)    │
│  DAG 可视化编辑器     │            │  声明式任务配置         │
├──────────────────────┤            ├────────────────────────┤
│  AgenticFlowCompiler │            │  Flow Engine (FSM)     │
│  DAG→Runtime 编译    │            │  状态机执行引擎         │
├──────────────────────┤            ├────────────────────────┤
│  SoloAgent (ReAct)   │            │  Runner / Executor     │
│  ReActCore 推理循环   │            │  opencode subprocess    │
├──────────────────────┤            ├────────────────────────┤
│  Plugins:            │            │  Prompts (12个 .md)     │
│  Tools/MCP/Memory/   │            │  LLM 提示模板           │
│  RAG/Skills          │            └────────────────────────┘
└──────────────────────┘
```

**高度对称**：两个系统都有一个"编译/编排层" + "执行层"，但 SoloEngine 的编排对象是 agent，Mission Driver 的编排对象是开发任务。

## 3. 控制流模型

| 维度 | SoloEngine | Mission Driver |
|------|-----------|----------------|
| 控制模型 | DAG（有向无环图） | FSM（有限状态机） |
| 执行单位 | Agent node（一个 SoloAgent 实例） | Step（script / tool / agent / group / subflow 5 种类型）|
| 路径选择 | Topological sort + orchestrator 分发 | Transition table（marker-driven） |
| 并行能力 | 多 entry node 并行 | 无并行（顺序 FSM） |
| 循环 | ReAct 内循环 + DAG 边无环 | 主流程：CHECK→REVIEW→EXEC→DRAFT→AUDIT |
| 子流程 | Subagent（TaskTool 委托） | subflow step（嵌套 FlowEngine） |

SoloEngine 的 DAG 是空间拓扑（多个 agent 并行协作），Mission Driver 的 FSM 是时间序列（多个开发阶段顺序衔接）。

## 4. Agent 模型

### 4.1 SoloEngine

- 每个 agent 节点 = `SoloAgent` 实例，包含独立的 `ReActCore`
- agent 之间通过 `TaskTool`（SubAgent 委托）通信
- 每个 agent 有自己独立的 LLM、tools、MCP、skills、memory

### 4.2 Mission Driver

- 没有内置 agent——将**整个 opencode 进程**视为 agent
- 每个 `agent` step 通过 `opencode run <prompt>` 启动一个独立的 AI 子进程
- 子进程间通信完全通过文件系统（写入 code → 下一次 prompt 消费）
- session 延续通过正则提取 `ses_xxx` ID 传递给下次调用

### 4.3 差异

| 维度 | SoloEngine | Mission Driver |
|------|-----------|----------------|
| Agent 粒度 | Python `SoloAgent` 实例 | 整个 `opencode` CLI 进程 |
| Agent 内聚 | LLM + Tools + MCP 在同一进程中 | LLM + Tools 完全由 opencode 管理 |
| Agent 隔离 | asyncio 协程隔离 | 操作系统进程隔离 |
| 通信 | 进程内 call_subagent() | 文件系统（写代码→读代码）|
| 会话延续 | 进程内历史维持 | `--session` 参数恢复 |

## 5. 状态管理与持久化

| 维度 | SoloEngine | Mission Driver |
|------|-----------|----------------|
| 状态存储 | SQLite (soloengine.db) + CAS 文件 | 文件系统（plan .md + mission .json + log） |
| 状态模型 | 对话消息 + agent 配置 + 文件变更 | Plan checklist `[x]`/`[ ]` + Plan Status + workflow 字段 |
| 恢复机制 | 数据库查询 + 对话历史回填 | 扫描 plan checkboxes 确定恢复点 |
| 快照 | 无 | `run-state.json` + `events.jsonl` + checkpoint |
| 审计 | 无 | `CLOSURE_SCRIPT_CHECK` + `CLOSURE_AUDIT` step |

Mission Driver 的持久化模式非常独特：**不可变文件状态**。Plan 的 checkbox 被"真实"地 ticked，下次运行时只需扫描 checkbox 状态就能确定 resume 位置，不需要回放步骤。这是"文件即数据库"的极致应用。

## 6. MCP 与工具系统

| 维度 | SoloEngine | Mission Driver |
|------|-----------|----------------|
| MCP 集成 | 完整 MCP Client（stdio/sse/http），20+ 内置服务器 | 无（通过 opencode 间接使用） |
| 工具 | 内置 Python 工具 + MCP 工具 | 通过 `tool` step 执行 bash 命令 |
| 渐进发现 | MCPTool 三级(list→schema→execute) | 无（bash 或 AI prompt 直接执行） |
| 文件操作 | Read/Write/SearchReplace + CAS diff | 无（由 AI agent 自行处理） |

Mission Driver 不直接管理工具——它的每个 step 要么执行 bash 命令，要么通过 `opencode run` 将决策权交给 AI。工具全部由 `opencode` 进程内部管理。

## 7. 错误处理与容错

| 维度 | SoloEngine | Mission Driver |
|------|-----------|----------------|
| 重试 | 无（抛出异常） | `markerAliases` + 修正重试（最多2轮） |
| 瞬态故障 | 无 | 独立 retry budget（默认6次，指数退避） |
| 死循环保护 | `max_iters` | `maxCycleVisits`(8) + `ping_pong`检测 + `maxTotalSteps`(500) |
| 超时 | 无 | 60 分钟 activity watchdog |
| 熔断 | 无 | 瞬态提供者检测（429/rate_limit/quota） |
| Agent 挂起 | cancel_event | orphan 进程清理（reap-orphans.mjs） |

Mission Driver 在容错方面明显更健壮——这是因为它操作的是不可逆的外部操作（文件写入、git 提交），而 SoloEngine 的 ReAct 循环内部失败可以简单地"再问一次 LLM"。

## 8. 状态机 vs DAG：深层对比

```
Mission Driver FSM 状态转换:
┌─────┐   ┌─────┐   ┌─────┐   ┌─────┐   ┌─────┐
│CHECK│→│REVIEW│→│ EXEC │→│DRAFT│→│AUDIT│→...
└─────┘   └─────┘   └─────┘   └─────┘   └─────┘
    ↑                                        │
    └────────────────────────────────────────┘

SoloEngine DAG 拓扑:
  ┌─────────┐
  │Designer │────┐
  └─────────┘    ├──→┌───────────┐
  ┌─────────┐    │   │Orchestrator│→ result
  │Coder    │────┘   └───────────┘
  └─────────┘
  ┌─────────┐
  │Reviewer │────→(feedback loop)
  └─────────┘
```

FSM 是"时间线"——每一步依赖前一步的结果；DAG 是"空间图"——节点可以并行，结果需要聚合。

## 9. 配置与 DSL

| 维度 | SoloEngine | Mission Driver |
|------|-----------|----------------|
| 顶层配置 | Canvas JSON + agent_presets.json | Mission JSON（missions/*.json） |
| 流程定义 | DAG 边 + 节点属性 | Flow JSON（FSM step + transitions） |
| 执行定义 | SoloAgentConfig dataclass | Plan MD（YAML frontmatter + checkboxes）|
| 提示模板 | system_prompt 字符串 | 12 个 markdown prompt 模板 |
| 层级 | 3 层：Canvas→Config→Prompt | 4 层：Mission→Flow→Plan→Prompt |

Mission Driver 的 4 层 DSL 层次更丰富，且有 `"extends": "base"` 继承机制。SoloEngine 的层次虽然少，但 Canvas 可视化编辑器提供了更直观的操作体验。

## 10. 消息与事件模型

| 维度 | SoloEngine | Mission Driver |
|------|-----------|----------------|
| 事件类型 | ExecutionEvent（agent_start/complete/error/tool_call等） | step result marker + workflow events |
| 流式 | WebSocket 实时推送 | 无（纯文件/日志输出） |
| 事件存储 | 无 | `events.jsonl` + `run-state.json` |
| 跨进程 | HTTP + WebSocket | 文件系统（stdout → log file） |
| 进度可视化 | RunPanel（消息列表+文件变更+diff） | 终端日志 |

## 11. 总结对比

| 对比维度 | SoloEngine | Mission Driver |
|---------|-----------|----------------|
| 领域 | AI agent 编排 | AI 开发编排 |
| 控制模型 | DAG + ReAct | FSM |
| Agent 粒度 | Python 类实例 | OS 进程 |
| 状态持久化 | SQLite + CAS 文件 | 文件系统（plan/mission/log） |
| 恢复 | 对话历史回填 | checkbox 扫描 |
| MCP | 完整 Client | 无（委托给 opencode）|
| 工具 | 内置 Python + MCP | bash + AI prompt |
| 容错 | 基础（max_iters） | 健壮（retry + watchdog + ping_pong）|
| 前端 | React Canvas DAG | 终端 + markdown 文件 |
| 配置层数 | 3 层 | 4 层 |
| 扩展性 | plugin 接口 | prompt 模板 + flow JSON |

## 结论

**SoloEngine 和 Mission Driver 解决完全不同的问题，功能上几乎无重叠。**

- **SoloEngine** = "AI agent 运行时"：让多个 AI agent 在 DAG 中协作，通过 ReAct 循环推理和行动。适合构建多 agent 协作系统。
- **Mission Driver** = "AI 开发流程引擎"：让一个 AI 开发者（opencode）在 FSM 控制下按 plan→execute→audit 循环交付软件功能。适合自动化软件工程。

**潜在融合点**：

1. Mission Driver 的容错模式（瞬态检测 + retry budget + ping_pong 检测）对 SoloEngine 的 ReAct 循环很有借鉴价值
2. SoloEngine 的 Canvas 可视化设计对 Mission Driver 的任务拓扑可视化有参考意义
3. SoloEngine 的 MCP 客户端架构可以为 Mission Driver 提供更多标准化的外部服务访问能力
4. Mission Driver 的 "不可变文件即状态" 持久化模式在 SoloEngine 的会话管理中值得探索

## Open Questions

- [ ] 是否存在一个场景需要同时用到 SoloEngine 的 agent 编排和 Mission Driver 的开发编排？
- [ ] Mission Driver 的 FSM + marker 模式能否作为 SoloEngine DAG 中单个 agent node 的内部状态机？
- [ ] 如果将 Mission Driver 的 checkpoint/recovery 机制适配到 SoloEngine 会话管理中，能否实现跨 LLM 调用的持久化推理？

## References

- `~/ai/soloengine` — SoloEngine full source
- `ai-dev/tools/mission-driver.sh` — Mission Driver launcher
- `missions/*.json` — Mission configurations
- `ai-dev/analysis/2026-07-25-opendcai-vs-age-vs-mission-driver.md` — Previous MD analysis
- `~/app/attractor-guided-engineering-template/tools/mission-driver/` — Full MD engine source
