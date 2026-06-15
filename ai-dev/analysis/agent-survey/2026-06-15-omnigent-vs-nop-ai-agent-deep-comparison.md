# Omnigent vs Nop AI Agent: 元调度器（Meta-Harness）与引擎（Engine）的深度架构对比

> Status: open
> Date: 2026-06-15
> Scope: `~/ai/omnigent`（Databricks 出品的 Python 元调度框架）vs `nop-ai/nop-ai-agent`（Java 21 Agent 引擎）
> Conclusion:

## 一、总览

本报告从架构哲学、调度模型、执行循环、Agent 定义、工具系统、策略/安全、记忆与会话、多 Agent 编排、可靠性、协作与部署等 12 个维度，对 **Omnigent**（Databricks 出品的"元调度器 / meta-harness"，Python，Alpha）和 **Nop AI Agent**（`nop-ai-agent`，Java 21，Phase 1 基础版已落地）进行深度对比。

> **核心结论先行**：两者不在同一个抽象层——Omnigent 是"**包裹并治理异构 Agent CLI/SDK 的控制平面**"，本身不实现通用的 ReAct 循环（循环由被包裹的 Claude Code / Codex / Pi / OpenAI Agents SDK / Databricks Supervisor 等拥有）；nop-ai-agent 是"**自己拥有 ReAct 循环的 Agent 引擎**"。这是 agent-survey 中此前**未曾出现的全新对比维度**——此前所有调研（MiMoCode、OpenCode、nanobot、OpenSquilla、OpenHarness 等）都假设框架自带 agent 引擎。Omnigent 是首个真正意义的 meta-harness 样本。

| 维度 | Omnigent | Nop AI Agent |
|------|----------|--------------|
| **出品** | Databricks, Inc.（Apache-2.0，Alpha） | Nop 平台（DSL-First 引擎，Phase 1） |
| **语言/运行时** | Python ≥3.12，FastAPI/SQLAlchemy | Java 21，Nop IoC / XDEF / XDSL |
| **架构原型** | Meta-Harness（包裹异构 Agent） | Agent Engine（拥有自己的循环） |
| **核心循环归属** | **不拥有**——委托给被包裹的 harness（SDK 进程内 / 原生 CLI 子进程） | **拥有**——同步 ReAct 单循环 + 单轮内并行工具 |
| **关键抽象** | Harness + Turn + Policy（6 阶段 ALLOW/DENY/ASK）+ Session | AgentModel + IAgentEngine + AgentSession + 6 层安全调度链 |
| **持久化** | SQLAlchemy + Alembic（SQLite / Postgres，成熟） | 3 后端 SessionStore + 3 后端 CheckpointManager（InMem/File/DB JDBC） |
| **沙箱** | **OS 牢笼**（bubblewrap / seatbelt / seccomp + L7 egress 代理 + 云沙箱） | **应用层门控**（虚拟 shell `nop-ai-shell` in-JVM 解释 + `IToolFileSystem` 路径禁锢 + 6 层安全链 + worktree 设计）；不走 OS 沙箱路线（`ISandboxBackend` 为 deferred Layer-4 槽，主动选择） |
| **协作面** | Server + Host + WebSocket + Web UI + 多设备同会话（杀手级） | 服务端嵌入引擎，无多用户协作 UI |
| **测试** | pytest 分片 + Playwright + known_failures.yaml | JUnit 5，agent 模块 179 个测试文件 |

## 二、Context（调研背景）

- **为什么需要这个分析**：调研 `~/ai/` 下 agent 相关项目时发现 Omnigent 是 Databricks 出品的"meta-harness"，定位为**包裹并治理多个已有 AI 编程助手**（Claude Code、Codex、Pi、OpenAI Agents SDK）的统一层。这与 agent-survey 中此前的所有样本（都自带 agent 引擎）截然不同，是一个**未被既有调研覆盖的全新架构原型**。
- **要回答的问题**：meta-harness 架构对 Nop AI Agent 的引擎设计有什么启示？Nop 该不该走"包裹异构 agent"的路线？两者的策略/安全模型如何互鉴？
- **约束**：Omnigent 处于 Alpha、单仓巨大（`omnigent/` 包顶层 64 个入口，`cli.py` 9127 行、`runner/app.py` 11808 行、`server/routes/sessions.py` 762 KB），分析聚焦其独有的设计决策（harness 包裹、policy 治理、server/runner/host 三层），不逐文件展开。

## 三、项目定位

### Omnigent

**Omnigent** 是一个 **"AI agent 的元调度框架（meta-harness）"**——在已有的 AI 编程 agent（Claude Code、Codex CLI、Pi、OpenAI Agents SDK、Databricks Supervisor）和用户自写 agent 之上提供统一层：共享会话状态、治理层（policy）、OS 隔离（sandbox）、云执行、实时多设备协作、HTTP/SSE 服务器。

> README（`~/ai/omnigent/README.md:5-7`）原文：*"A meta-harness for all your AI agents … swap or combine harnesses without rewriting, keep them in check with policies and sandboxing, and collaborate in real time on the same live session, from any device."*

- **出品**：Databricks, Inc.（`pyproject.toml:11-13`；`NOTICE:1` 版权 `(2026) Databricks, Inc.`）
- **许可 / 状态**：Apache-2.0；`Development Status :: 3 - Alpha`（`pyproject.toml:16`）
- **语言**：Python ≥3.12（`requires-python = ">=3.12"`）
- **入口**：`omnigent` / `omni` 两个 CLI（`pyproject.toml:186-187`）
- **核心问题**：把 Claude Code / Codex / Pi / OpenAI Agents / YAML agent 当作可互换的 "harness"，在一个编排器下统一，附带共享会话状态、治理层、OS 隔离、云执行、实时多用户协作、HTTP/SSE 服务器（同一 live session 可从 CLI、浏览器、手机访问）

### Nop AI Agent

**nop-ai-agent** 是 Nop 平台的 **Agent 引擎模块**——DSL-First，agent/tool/plan 都是 XML DSL，通过 XDEF codegen 生成 Java 模型类，支持 Delta 定制。定位为**面向无人值守自动化的 Java Agent 引擎**（不是终端工具）。

- **出品**：Nop 平台（`nop-entropy` 仓库）
- **语言**：Java 21，Nop IoC
- **定位**：大规模无人值守自动化，可与 Nop IoC / ORM / GraphQL 深度集成的企业级应用 Agent 引擎

## 四、架构哲学对比

### 4.1 "包裹" vs "拥有"——最根本的分歧

这是两者最根本、且 agent-survey 此前未曾触及的差异：

```
Omnigent:  用户 → Server(控制平面) → Runner(数据平面) → Harness 子进程(拥有循环)
                                                     ├── claude-sdk / claude-native
                                                     ├── codex / codex-native
                                                     ├── pi / openai-agents / databricks_supervisor
                                                     └── (LLM↔工具的 ReAct 循环在这里面跑)

Nop:       用户 → IAgentEngine → ReActAgentExecutor(拥有循环)
                                 ├── IChatService.call()  ← LLM 调用在引擎内
                                 └── IToolManager.callTool() ← 工具执行在引擎内
```

**证据**：

- Omnigent 的 Claude SDK executor（`~/ai/omnigent/omnigent/inner/claude_sdk_executor.py:1612-1691`）注释明确：*"The SDK receives the latest user message as a prompt and runs its full agent loop (which may include multiple internal tool calls). We observe the message stream and yield ExecutorEvents for the Session to record."*——**Omnigent 只是观察流并持久化/治理，不驱动通用 ReAct 循环**。
- Omnigent 的 "native" 概念（`omnigent/harness_aliases.py:14-23`）：`NATIVE_HARNESSES = {claude-native, native-claude, codex-native, native-codex}`——这些 harness 从第三方 CLI 自身的磁盘 transcript 启动。
- Nop 的 `ReActAgentExecutor.java:576-1102` 自己实现完整 `while (iteration < maxIterations)` 循环，`chatService.call()` 与 `toolManager.callTool()` 都在引擎内。

**分析**：Omnigent 的策略是"**承认并复用现有最强的 agent CLI/SDK，在它们之上叠加治理/协作/隔离**"，这让它能瞬间获得 Claude Code / Codex 的全部能力而不重造轮子，代价是**循环的内部行为不可控**（只能通过 hook/forwarder 观察）。Nop 的策略是"**自建引擎，循环每一步都可控、可 Delta 定制、可治理**"，代价是**起步工作量巨大且需要自己追赶 provider 生态**。

### 4.2 配置 vs 代码

| | Omnigent | Nop AI Agent |
|---|---|---|
| **Agent 定义** | YAML spec（`docs/AGENT_YAML_SPEC.md`）+ 可打包为 tar bundle（`spec/`） | XML DSL（`agent.xdef` → `.agent.xml`），声明式 |
| **Tool 定义** | YAML 四类型（mcp / function / agent / inherit）；function 工具 schema 从 Python 签名**自动生成** | XML（`tool.xdef` → `.tool.xml`），schema 是自由 XML，通过 VFS 发现 |
| **Schema 驱动** | Pydantic v2 运行时验证 | XDEF → `_gen` Java 代码生成 |
| **Delta 定制** | 无 Delta 系统，靠 agent spec 覆盖 + policy 三层叠加 | **原生支持**：Delta 层可覆盖任意 `.agent.xml` |
| **Policy 配置** | 三级叠加：server-wide（admin）→ agent spec（dev）→ session（user），YAML 声明 | 声明式 `<permissions>` + `<path-rules>`，按 agent 配置 |

**分析**：Omnigent 的 YAML + Pydantic 是 Python 生态主流风格，且 function 工具"从签名自动生成 schema"对开发者很友好。Nop 的 XML DSL + Delta 定制是 Java 企业级的差异化王牌——**Omnigent 完全没有 Delta 系统**。

### 4.3 控制平面 / 数据平面分离

Omnigent 有清晰的 **Server（控制平面）↔ Runner（数据平面）↔ Host（机器守护）** 三层：

- **Server**（`server/`，32 个入口，56 个 API 路径）：FastAPI，管会话/策略/资源/协作/auth/Web UI
- **Runner**（`runner/`，`app.py` 11808 行）：FastAPI 子集，**拥有 harness 子进程、MCP 连接、async inbox、工具分发**——数据平面
- **Host**（`host/`）：机器守护进程，WebSocket 连 Server，按需 spawn runner 子进程；支持 per-session git worktree（`host/git_worktree.py`）

Nop 的部署模型（设计文档 `01-architecture-baseline.md` §五）是"**天然分布式的 actor 系统**"——单进程最简、多实例自动扩展为多进程，Gateway → 持久化层(DB) → Agent 实例消费。但**没有显式的 server/runner/host 三分离**，引擎层是单体（`DefaultAgentEngine`）。

**分析**：Omnigent 的三层分离是 meta-harness 的必然结果——因为要 spawn 异构子进程，必须有一个数据平面（runner）专门管子进程生命周期。Nop 的引擎一体化更适合自建循环，但在多进程部署时缺少 Omnigent 那种"host 守护 + server 协调"的成熟拓扑。

## 五、执行模型对比

### 5.1 核心循环

```
Omnigent（不拥有循环，委托给 harness）:
  _run_turn_bg(msg, conv):                       # runner/app.py:7458-7513
    1. 解析 agent spec + 子 agent spec（walk parent tree）
    2. 构造 spawn 环境（per harness）
    3. 加载会话历史 + 压缩上下文（proactive + reactive）
    4. 跑 cost advisor（optimize/advise）
    5. 构造 tool schema
    6. _stream_message_to_harness —— 流给 harness 子进程
       └── harness 内部跑自己的 ReAct/多轮（Omnigent 观察 + 持久化 + 策略）

Nop（ReActAgentExecutor.execute，自拥有循环）:
  while (iteration < maxIterations):              # 默认 10
    1. 检查 cancel / denialLedger.isPaused
    2. shouldForceStop（token > 90%）/ shouldTriggerCompaction（token > 80% 或 msg > 30）
    3. PRE_REASONING hook（可 veto）
    4. input guardrail 检查
    5. modelRouter.route → chatService.call(request)   ← 同步阻塞 LLM 调用
    6. 失败 → failed；无 tool calls → completionJudge → break
    7. dispatchLoop: 6 层安全检查（post-denial-guard → tool-access → permission → path-access → security-matrix → approval-gate）
    8. CompletableFuture.allOf(...).join()        ← 单轮内并行工具，barrier
    9. 每工具结果 → checkpoint → POST_ACTING hook
    10. iteration++
```

**关键差异**：

| 特征 | Omnigent | Nop AI Agent |
|---|---|---|
| **循环归属** | 委托给 harness（不可控内部） | 引擎自拥有（每步可控） |
| **循环结构** | 取决于 harness（Claude SDK 全自动；native CLI 跑自己的 TUI 循环） | 单层同步 ReAct + 单轮内并行工具 |
| **工具执行** | harness 内部跑；runner 通过 tool dispatch + MCP relay 治理 | `CompletableFuture.allOf().join()` 单轮内并行 + barrier |
| **结束判定** | 取决于 harness（native 由 CLI 决定） | 无 tool calls → `CompletionJudge` 决策（complete/continue/escalate）+ 死循环守卫（3 次 continue） |
| **流式** | SSE 流（`HarnessApp` 基类 15s 心跳，`_scaffold.py:80`） | `Flow.Publisher<ChatStreamChunk>` 仅用于外部观察；`REASONING_CHUNK` hook 声明但未接线 |
| **Steering** | in-band message injection（`ctx.next_injection`） | 设计文档定义，未实现 |
| **重入** | native harness 的 preStop/postStop 由被包 CLI 决定 | hook 重入仅在 2 个 `*_TOOL_RESULT_PROCESSED` 点，3 次上限 |
| **超时** | IDLE 240s + 绝对 3600s 看门狗（`_scaffold.py:102-109`） | `maxIterations`（默认 10）+ `toolTimeoutSeconds` + forced-stop 90% + 死循环守卫 |

**分析**：Omnigent 的循环哲学是"**我在循环外面，循环的丰富性由 harness 提供**"——所以它能享受 Claude Code 的 preStop/postStop ReAct 钩子、Codex 的 completion gate 等，但无法统一改造。Nop 的循环哲学是"**我拥有循环，可预测、可终止、可治理**"——更适合无人值守自动化对可预测性的要求。

### 5.2 Harness 抽象——可插拔后端 vs 内置单一后端

> **澄清**：两者都有"harness"（执行脚手架——拥有循环 + 工具 + FS + 安全 + 会话）。区别不在于"有无 harness"，而在于**harness 是否可插拔/可替换**。Nop 的引擎（`DefaultAgentEngine` + `AgentToolExecuteContext` + `IToolManager` + 6 层安全链 + `IToolFileSystem`）就是 Nop 的内置 harness；Omnigent 把 harness 做成**可声明切换的异构后端注册表**。本节聚焦这一可插拔性差异。

**Omnigent 的 Harness 注册表**（`~/ai/omnigent/omnigent/runtime/harnesses/__init__.py:34-68`）—— 8 个 harness key，每个映射到 `create_app() -> FastAPI` 工厂：

| Harness | 类型 | 实现 |
|---|---|---|
| `claude-sdk` / `claude` | SDK（进程内） | 驱动 Claude Agent SDK |
| `claude-native` | 原生 CLI | 通过 tmux PTY 驱动真实 `claude` CLI |
| `codex` | SDK | Codex SDK |
| `codex-native` | 原生 CLI | 通过 app-server JSON-RPC 驱动真实 `codex` CLI |
| `pi` | SDK | Pi |
| `openai-agents` | SDK | OpenAI Agents SDK |
| `databricks_supervisor` | SDK | Databricks Supervisor |

**两种家族**：SDK harness（进程内）vs **native** harness（驱动真实 CLI 子进程）。`claude_native_*` / `codex_native_*` 系列文件实现 native 侧。

**Native bridge 的会合点机制**（`claude_native_bridge.py:1-25`）：两个活进程需要会合——Claude Code 在用户终端资源里跑；Omnigent harness turn 在 Web UI 提交消息时跑。模块拥有一个小型文件系统会合目录 + 两个辅助面：一个 MCP stdio server（Claude Code 作为子进程启动它）+ 一个 tmux send-keys 路径。**Web UI 消息通过 tmux send-keys 投递给 native CLI**（键入用户正附加的同一 pane）；Codex 则用 **app-server JSON-RPC over Unix socket**。

**Transcript 镜像**：`claude_native_forwarder.py`（147 KB）和 `codex_native_forwarder.py`（196 KB）是常驻后台进程，watch CLI 的磁盘 transcript 并把每个渲染的 chat item 镜像到 Omnigent session，让 Web UI / 手机看到同样的视图。

**分析**：这是 meta-harness 最重的工程——为了让异构 CLI 在统一 session 下可观察、可治理、可协作，Omnigent 用文件系统会合 + tmux/JSON-RPC + transcript forwarder 搭了一整套"逆向工程"桥。代价是大量胶水代码（`claude_native*.py` / `codex_native*.py` 合计数十万行）。**可插拔 harness 后端是 Omnigent 独有的维度**——Nop 的内置 harness 是单一后端（自有引擎），不支持声明切换到 Claude Code/Codex 后端。这是两者定位差异的直接体现（Omnigent 复用现有 CLI，Nop 自建引擎），**不存在谁优谁劣**——选择可插拔后端就必然要承受 native bridge 的胶水成本，选择自建单一后端就失去现成交互能力但获得循环每步可控。

## 六、Agent 定义与编排对比

### 6.1 Agent 定义

| 维度 | Omnigent（YAML spec） | Nop（agent.xdef） |
|---|---|---|
| **格式** | YAML + 可打包 tar bundle（`spec/AGENTSPEC.md`） | XML DSL + XDEF schema |
| **核心字段** | `name`、`prompt`/`instructions`、`executor.harness`、`tools`、`policies`、`params`、`os_env`、`terminals`、`async`、`cancellable`、`timers` | `name`、`tagSet`、`mode`、`chatOptions`、`tools`、`availableSkills`、`permissions`、`constraints`、`path-rules`、`prompt`、`hooks` |
| **Harness 选择** | `executor.harness: claude-sdk \| claude-native \| codex \| ...`（**声明用哪个 agent 后端**） | 无（引擎就是后端） |
| **权限** | `policies:` 声明（三级叠加） | `<permissions>` + `<path-rules>` 显式声明 |
| **约束** | `async`、`cancellable`、`timers`、`context_window` | `maxIterations`、`toolTimeoutSeconds`、`maxParallelTools`、`tokenCompactionThreshold` |
| **Hook** | 通过 harness 的 ExtensionEvent（Claude/Codex 有 preStop/postStop） | DSL 级 `<hooks><on event>` + XPL 表达式，12 个生命周期点 |
| **模型生成** | 无代码生成 | XDEF → `_gen` Java 类 |
| **Delta 定制** | 无 | **原生支持** |
| **示例** | `examples/polly/`（多 agent 编码编排器）、`examples/debby/`（双头头脑风暴） | `.agent.xml` 文件 |

### 6.2 多 Agent 编排

| | Omnigent | Nop AI Agent |
|---|---|---|
| **子 Agent 机制** | `sys_session_create/send/get_history/close/list` 工具（`tools/builtins/spawn.py`，57 KB）；子 agent 是 child conversation，**继承 root conversation 的 session policy**（`runtime/policies/builder.py:179-186`） | `call-agent` 工具 + `CallAgentExecutor`（fork+exec 同步）；`send-message`（fire-and-forget） |
| **并行写隔离** | **git worktree 已实现**（`host/git_worktree.py`，每个 teammate 一个隔离 worktree，symlink node_modules，slug 校验，fast resume，cleanup_stale） | **git worktree 在设计范围内**（`nop-ai-agent-branch-affinity-scheduling.md`，status `active`，已审计）：`IWorktreeManager`（git CLI ProcessBuilder，JGit 不支持 worktree）+ Worktree Registry（DB 表，含 nodeId 用于跨节点 orphaned 检测）+ orphaned recovery（TTL 1h）+ `call-agent(workspace=shared\|worktree)` + slug 校验 + 资源上限（4/session、16/node）。**但当前零 Java 实现**——`call-agent` 尚无 `workspace` 参数 |
| **当前多 agent 并行** | worktree 物理并行（生产） | `CompletableFuture` 单轮内并行工具；跨 agent 当前同步串行（worktree 待实现） |
| **权限继承** | 子 agent 继承 root 会话 policy（叠加式） | **`ParentPermissionConstraint`——`父权限 ∩ 子配置`**（收紧式，`CallAgentExecutor.java:169-181`） |
| **通信** | async inbox（`tools/builtins/async_inbox.py`）；agent 间通过 inbox 异步 | `IAgentMessenger`（NoOp 默认 / Local 内存 / DB）；`call-agent` 当前**同步阻塞**（不消费 mailbox） |
| **编排示例** | Polly：3 个子 agent（claude_code/codex/pi）并行 worktree 写代码，再路由给不同 vendor reviewer | 设计：Coordination Bus + `IConflictStrategy`（`scope_claim`/`operation_intent`/`conflict_alert`），未实现 |
| **冲突检测** | 无（写冲突由 git worktree 物理隔离 + OS file lock 兜底） | 设计文档三层冲突检测（独创），未实现 |
| **调度模型** | 无显式调度单元（host 按需 spawn runner） | **Branch Affinity Scheduling**（设计）：调度单元 = `(project, branch)`，亲和表映射到 nodeId——直接复用 git 分布式协作模型（"Agent 是分布式开发者"） |

**分析**：

1. **Worktree 两者理念高度一致，差异在成熟度**——Omnigent 的 worktree 是**生产实现**；Nop 的 worktree 是**已审计的 active 设计**（`IWorktreeManager` 走 git CLI 因 JGit 不支持、Registry 表、orphaned recovery、资源上限、分支亲和调度）。Nop 的设计**比 Omnigent 更系统**（Branch Affinity Scheduling + Worktree Registry + 跨节点 orphaned + 与 Snapshot 的分工——"Worktree 用隔离，Snapshot 用保护"），但尚未落地。**此项不是"借鉴 Omnigent"，而是"推进 Nop 既有设计实现"**。
2. **权限继承方向相反**：Omnigent 子 agent **继承并叠加** root policy（宽松+治理）；Nop 子 agent 是 **父权限 ∩ 子配置**（收紧，安全优先）。两者哲学不同——Omnigent 信任治理层，Nop 信任最小权限。
3. **Nop 的 Coordination Bus**（LLM 可见的协调消息 + 三层冲突检测）是独创设计，Omnigent 完全没有——但 Nop 未实现。

## 七、工具系统对比

| | Omnigent | Nop AI Agent |
|---|---|---|
| **定义格式** | YAML 四类型（mcp / function / agent / inherit）；function 工具 schema 从 Python 签名**自动生成**（`inner/tools.py:83-111`） | XML（`tool.xdef` → `.tool.xml`），schema 是自由 XML，通过 VFS 发现 |
| **注册** | `ToolManager` 注册表（per-workflow-execution）；MCP 生命周期 runner 拥有 | `IToolExecutor` SPI + `<ioc:collect-beans by-type>` 自动收集 |
| **内置工具** | ~20 个 builtin（`tools/builtins/`：spawn、sys_terminal、os_env、async_inbox、timer、web_search、agents...） | 23 个 `.tool.xml`（filesystem/search/shell/network/memory/multi-agent/skills/other） |
| **客户端工具** | **client-side tools**（`tools/client_specified/`）——LLM 看到但由调用方（SDK/Web UI）执行，经 `status: "action_required"` SSE 返回 | 无（工具都在引擎内执行） |
| **MCP** | 内置 MCP client + server；`ProxyMcpManager` → `/mcp` endpoint → `_evaluate_tool_call_policy`（避免与 native hook 双重评估） | `nop-ai-mcp-server`（独立模块） |
| **并行** | harness 内部并行；runner 通过 inbox 异步 | `CompletableFuture.allOf()` 单轮内并行 + `maxParallelTools` |
| **Schema 来源** | Python 签名自动生成 / MCP / 显式 | 自由 XML（无 JSON Schema 自动生成） |

**关键差异**：

1. **客户端工具**是 Omnigent 独有概念——工具可以"呈现给 LLM 但由调用方执行"。这让 Web UI 能本地执行敏感工具。Nop 所有工具都在引擎内执行。
2. **function 工具 schema 自动生成**对开发者很友好（写 Python 函数即得 schema）。Nop 需要手写 `.tool.xml` 的自由 XML schema。
3. **Nop 的工具经 VFS + Delta 可定制**，适合企业级；Omnigent 的 Python 函数注册更灵活。

## 八、策略（Policy）vs 安全（Security）对比——两者都是亮点

这是两个框架**最值得互鉴**的领域，但解决的问题不同：

### 8.1 Omnigent 的 Policy 系统（治理平面）

**Policy = 声明式门，在 6 个执行阶段之一返回 ALLOW / DENY / ASK**（`policies/types.py:54-71`）：

- **6 阶段**：`REQUEST`、`RESPONSE`、`TOOL_CALL`、`TOOL_RESULT`、`LLM_REQUEST`、`LLM_RESPONSE`
- **3 级叠加**（`docs/POLICIES.md:11-23`）：server-wide（admin）→ agent spec（dev）→ session（user）；评估顺序 **session 先 → agent → admin 后**
- **纯评估器 vs 运行时编排的刻意分离**（`policies/__init__.py:1-23`）：policy 子类是纯的（无跨调用可变状态、无 DB I/O、不知道 conversation）；状态和编排（组合循环、ASK 暂存、fail-closed）在 `runtime.policies`
- **PolicyEngine 组合循环**（`runtime/policies/engine.py:222-342`）：YAML 顺序遍历 → 跳过不匹配的 phase/condition → 累积 `set_labels` 写 → DENY 短路（应用已累积写入）→ ASK 返回（写入待 approve 才应用）→ 否则 ALLOW
- **fail-closed 不变量**：任何异常 → fail-closed；返回动作校验；classifier-only spec 失败时 substitute ALLOW
- **内置 policy**（`policies/builtins/`）：`safety`（max_tool_calls_per_session、ask_on_os_tools、enforce_sandbox、deny_pii_in_llm_request）、`cost`（soft ask + hard max 强制模型降级而非停止）、`google`/`github`（vendor MCP 访问）、`working_dir`、`risk_score`、`routing`、**`cel`（Common Expression Language，非图灵完备，保证终止）**、`prompt`（LLM 分类器）
- **Native CLI policy 桥**（`native_policy_hook.py:1-165`）：Claude/Codex 的 `PreToolUse`/`PostToolUse` hook 统一翻译为 Omnigent `EvaluationRequest`；**ASK → "deny"（fail-closed，ASK 由服务端 URL-based elicitation 解决）**；ALLOW → `None`（让 harness 自己的 consent 仍触发）
- **安全 allowlist**（`policies/registry.py:154-189`）：`is_registered_handler()` 拒绝 `subprocess.Popen`/`builtins.exec` 注入

### 8.2 Nop 的 6 层安全调度链（引擎内强制）

**6 层检查在 dispatch 顺序**（`ReActAgentExecutor.dispatchLoop`，行 795-955）：

| 顺序 | 层 | 默认行为 |
|---|---|---|
| 0 | Layer 3 post-denial-guard（盲重试阻断） | `PassThroughPostDenialGuard`（指纹检测） |
| 1 | Layer 1 tool-access（硬编码拒绝列表） | `DefaultToolAccessChecker` 拒绝 bash/write-file/delete-file/... |
| 2 | Layer 1 permission provider | `AllowAllPermissionProvider` |
| 3 | Layer 1 path-access | `DefaultPathAccessChecker` 拒绝 `~/.ssh`/`~/.aws`/`/etc/`/`.env`/`id_rsa`/路径穿越 |
| 4 | Layer 2 security-level + permission-matrix | `NoOpSecurityLevelResolver` + `PassThroughPermissionMatrix` |
| 5 | Layer 3 approval-gate | `AutoApproveGate` |

每个 deny 经 `handleDenialAndCheckThreshold`（行 1182-1212）：记录指纹 → 记录 `DenialRecord` 到 `denialLedger` → 超阈值则 `ctx.setStatus(paused)` + `SESSION_PAUSED` 事件 → **sticky-pause**（需人工 `resumeSession` 恢复）。

**PATH_ARG_KEYS**（`security/ToolPathArgKeys.java:18-21`）：扫描工具参数中的 `path/file/filePath/filename/directory/dir/destination/output/input/source/target/cwd` 键 → 运行时路径检查——Nop 独有的细粒度路径安全。

**secure-by-default 警告**（`DefaultAgentEngine.warnIfInsecureDefaults`，行 226-251）：检测到 `AllowAll*Checker` 或 `NoOpAuditLogger` 时发一次性 WARN。审计默认 `Slf4jAuditLogger`。

### 8.3 对比

| 维度 | Omnigent Policy | Nop Security |
|---|---|---|
| **解决的问题** | 跨异构 harness 的**治理/同意/成本**（ALLOW/DENY/ASK） | 引擎内的**细粒度强制**（6 层链 + 拒绝阈值 + sticky-pause） |
| **粒度** | 6 阶段 × 3 级叠加；CEL/prompt/cost 分类器 | resource + action 显式声明 + path 参数扫描 + 拒绝指纹 |
| **声明性** | YAML + Python callable + CEL（非图灵完备） | XML DSL + XPL 表达式（hook） |
| **ASK/HITL** | **一等公民**（ASK 暂存 + URL-based elicitation + 服务端解决） | approval-gate（默认 auto-approve），sticky-pause 需人工 resume |
| **成本治理** | **一等公民**（`cost` policy：soft ask + hard max 强制模型降级；`cost_plan.py` advisor） | `IModelRouter`（默认 PassThrough，无成本治理） |
| **隔离范式** | **OS 牢笼**（bubblewrap/seatbelt/seccomp + L7 egress 代理，`inner/bwrap_sandbox.py`/`inner/seatbelt_sandbox.py`/`inner/egress/`） | **应用层门控**：`IToolFileSystem` 路径禁锢（文件工具）+ 虚拟 shell（`nop-ai-shell`，in-JVM 解释，无 fork/exec）+ 6 层安全链 + worktree 隔离（设计） |
| **fail-closed** | PolicyEngine 任何异常 → fail-closed | `NoOpAgentMessenger.request` 抛 `UnsupportedOperationException`；secure-by-default 警告 |

### 8.4 隔离范式对比——OS 牢笼 vs 应用层门控（核心哲学差异，非缺口）

两者选择了**正交的隔离路线**，这是核心哲学差异，不是某一方的"缺口"：

**Omnigent = OS 牢笼（kernel-enforced jail）**：
- 真实进程（bash/git/npm）在 mount-namespace + seccomp 过滤的牢里跑，**有完整 syscall 面，但被禁锢在 jail root**
- 哲学："agent 在 OS 牢里，但里面什么都能做"
- 逃逸需内核漏洞

**Nop = 应用层门控（application-layer gating）**：
- **不启动 OS 牢**。`ISandboxBackend` 仅是 javadoc 中 Layer-4 的 deferred 设计槽（`IApprovalGate.java:15` 等），无实现类——这是**主动设计选择**，不是遗漏
- 隔离栈（实际落地）：
  1. **`IToolFileSystem` 路径禁锢**（`nop-ai-toolkit/fs/IToolFileSystem.java`）——所有文件工具（read/write/patch/delete/grep/glob...）经此抽象；`LocalToolFileSystem`（`fs/LocalToolFileSystem.java:40-50`）的 `isPathAllowed()` 做 canonical-path 包含检查（chroot-like，解析 symlink）
  2. **虚拟 shell `nop-ai-shell`**（`nop-ai-shell/`，从 JLine3 迁移的 in-JVM 命令引擎）——`ShellCommandExecutor`（`executor/ShellCommandExecutor.java:65-67`）用 `BashSyntaxParser` 解析成 AST **in-JVM 解释，无 `ProcessBuilder`/`Runtime.exec`**；命令经 `ShellCommandRegistry` 白名单解析，未注册命令返回 exit 127 **不落到 OS**（`ExternalCommandAdapter` 抛 `UnsupportedOperationException`）；命令经 `IShellCommandExecutionContext.fileSystem()`（`commands/IShellCommandExecutionContext.java:30`）操作 `IToolFileSystem`
  3. **6 层安全调度链**（§8.2）+ `DefaultPathAccessChecker` 拒绝敏感路径 + `PATH_ARG_KEYS` 参数扫描
  4. **危险工具 deny-by-default**（`DefaultToolAccessChecker` 默认拒绝 bash/write-file/delete-file/...）
- 设计意图（`nop-ai-shell-design.md` §7.4）明确列出 OS 沙箱路线下不可能发生、而虚拟 shell 路线下**根本不存在**的风险：进程注入（无 fork/exec）、网络 exfiltration（curl/wget 受限或不存在）、syscall（无 ptrace/seccomp bypass）、文件系统逃逸（所有操作经 `IToolFileSystem`）
- 哲学："每个工具单独门控；危险工具 deny-by-default；文件工具路径受限"

> **实现状态诚实标注**（重要 nuance）：虚拟 shell 当前是**自包含库，仅被自身单测调用，尚未接线到 agent 的 `bash` 工具**——生产 `BashExecutor`（`nop-ai-toolkit/tools/BashExecutor.java:43-55`）仍用真实 OS `sh -c`/`cmd /c`，且**不经 `IToolFileSystem`**。`nop-ai-shell-design.md:21,28` 明确承认此现状："当前 Agent 的 bash 工具通过 ProcessBuilder 启动真实 OS 进程"、"nop-ai-shell 与 toolkit 完全未连接"。当前 `bash` 的隔离仅靠 deny-by-default + approval-gate。**接线虚拟 shell 替换 `BashExecutor`（设计 §4.3 的 `ShellBashExecutor`）是进行中的设计**。文件工具的 `IToolFileSystem` 禁锢则已落地生效。

**分析**：

1. **隔离范式正交，不可简单评判优劣**——Omnigent 的 OS 牢笼对"必须跑真实 bash/git/npm 工具链"的场景更实用（agent 在牢里有完整工具链）；Nop 的应用层门控对"工具调用经 Java 执行器路由、危险工具 deny-by-default"的场景更细粒度（无 syscall 面、无进程注入）。两者解决不同问题。
2. **成本治理是 Omnigent 的真差异化**——`cost` policy 能在预算压力下强制模型降级而非停止任务，`cost_plan.py` 每轮 brain-model 选择。Nop 的 `IModelRouter` 默认 PassThrough，**无成本治理**——这是 Nop 可借鉴的真缺口。
3. **Nop 的 PATH_ARG_KEYS + denial-ledger + sticky-pause + secure-by-default 警告 + 虚拟 shell 无进程模型**是 Omnigent 没有的细粒度引擎内安全——这适合无人值守场景。
4. **两者都 fail-closed**，但实现层不同。
5. **Nop 不需要照搬 OS 沙箱**——其虚拟 shell + `IToolFileSystem` 路线在理念上对"in-JVM agent 执行"更自洽（无 OS 进程即无 OS 攻击面）。真正待办是**把虚拟 shell 接线到 `bash` 工具**（消除当前 `BashExecutor` 的真实 OS 进程路径），而非引入 bubblewrap/seatbelt。

## 九、记忆与会话系统对比

| | Omnigent | Nop AI Agent |
|---|---|---|
| **会话持久化** | SQLAlchemy + Alembic；本地 SQLite（`~/.omnigent/chat.db`），部署 Postgres | 3 后端：`InMemorySessionStore`（默认）/ `FileBackedSessionStore`（JSON）/ `DBSessionStore`（JDBC，`ai_agent_session` 表，MERGE） |
| **消息模型** | `Conversation` + `ConversationItem`（`MessageData`/`FunctionCallData`/`FunctionCallOutputData`/`ReasoningData`/`CompactionData`/`ErrorData`/`NativeToolData`/`SlashCommandData`/`TerminalCommandData`/`ResourceEventData`），projector 架构 | `ChatMessage` 5 子类（system/user/assistant/tool/custom） |
| **压缩** | runner 内 compaction context（proactive + reactive）+ cost advisor；forwarder 镜像 transcript | **3 层 pipeline 实现**（`PipelineCompactor`）：micro-compression → turn pruning → LLM summary；触发 token > 80% 或 msg > 30；forced-stop 90% |
| **工作记忆** | 无独立 working memory（由 harness/memory 工具管理） | `IAiMemoryStore` + `InMemoryAiMemoryStore`（ConcurrentHashMap）+ 3 工具（read/write/search-memory）+ **system-prompt 自动注入 budgeted memory**（默认 1024 token，pinned/priority packing） |
| **长期记忆** | 无独立长期记忆子系统 | 设计：`IMessageService` + 向量存储 + retain/recall/reflect（Layer 4，未实现） |
| **Checkpoint** | 非 native harness 的崩溃恢复（`runner/app.py:4905-4937`）；native 由 CLI transcript 镜像 | **完整实现**：3 触发类型（LLM_TURN/TOOL_EXECUTION/COMPACTION）+ 3 后端（NoOp/File/DB）+ `resumeSession`（sticky-pause）+ `restoreSession`（crash-restart）+ `restorePendingSessions`（启动批量恢复） |
| **Fork** | 会话 fork（`/fork` API）+ 跨 harness fork（native transcript 重建） | `forkSession`（`InMemorySessionStore.forkSession`，深拷贝 messages/planId/metadata，`inheritContext` 控制） |
| **Resume** | `resume_dispatch.py`：读 `omnigent.wrapper` label → 路由到对应 native wrapper；持久化 launch state（cwd） | `resumeSession`（sticky-pause 恢复）+ `restoreSession`（崩溃恢复） |

**分析**：

1. **持久化两者都成熟**——Omnigent SQLAlchemy/Alembic 是 Python 生态标准；Nop 3 后端 SessionStore + 3 后端 CheckpointManager 是 Java 企业级。
2. **Nop 的 checkpoint/resume/restore 更完整**——3 种触发类型 + sticky-pause + crash-restart + 启动批量恢复，这是无人值守自动化的基石。Omnigent 的崩溃恢复仅限非 native harness。
3. **Nop 的 compaction 已实现 3 层 pipeline**；Omnigent 的 compaction 在 runner 数据平面 + cost advisor，但细节由 harness 决定。
4. **Nop 的 working memory budgeted 注入**是设计亮点——system-prompt 自动注入。Omnigent 无独立 working memory 抽象。
5. **Omnigent 的消息模型更丰富**（10+ item 类型，含 native tool / slash command / terminal command / resource event）——反映它包裹异构 CLI 的现实。

## 十、可靠性设计对比

| | Omnigent | Nop AI Agent |
|---|---|---|
| **超时** | IDLE 240s + 绝对 3600s 看门狗（`_scaffold.py:102-109`） | `maxIterations`（默认 10）+ `toolTimeoutSeconds` + forced-stop 90% + 死循环守卫（3 次 continue） |
| **Checkpoint** | 非 native 崩溃恢复 | 完整 3 触发类型 + 3 后端 + resume/restore |
| **重试** | retry-across-harnesses（designs/RETRY_ACROSS_HARNESSES.md） | 设计：`IRetryPolicy` + `ICircuitBreaker`，未实现 |
| **熔断** | 无显式 | 设计：`ICircuitBreaker`，未实现 |
| **token 估算** | tiktoken（依赖） | **启发式 chars/4 + 4/msg，EMA 校准**（`CalibratedTokenEstimator`，无 BPE/jtokkit） |
| **取消** | SIGTERM/SIGINT graceful | `cancelSession`（graceful 设取消标志下个迭代边界停 / forced 中断执行线程） |
| **崩溃恢复** | 非 native harness 自动恢复未完成 turn；native 由 CLI transcript 镜像 | `restoreSession` + `restorePendingSessions`（启动时批量） |
| **审计** | SQLite 消息持久化 + policy 评估日志 | `IDenialLedger` + `IAuditLogger`（默认 Slf4j）+ `IPostDenialGuard` 指纹 |

**分析**：

1. **Nop 的 token 估算明显偏弱**——chars/4 + EMA 校准 vs Omnigent 的 tiktoken（BPE 精确）。agent-survey 中 `2026-06-10-token-estimation-and-context-compression-survey.md` 也曾指出这点。**这是 Nop 可借鉴的 P1 项**（引入 jtokkit 或类似 BPE tokenizer）。
2. **Nop 的 checkpoint/resume 远超 Omnigent**——这是自建引擎的优势（每步可埋点）。
3. **Omnigent 的 retry-across-harnesses** 是 meta-harness 独有——一个 harness 失败可切另一个。Nop 无对应（也无需，因为它只有一个引擎）。

## 十一、LLM Provider 与协作面对比

### 11.1 LLM Provider

| | Omnigent（`llms/`） | Nop（`nop-ai-core`） |
|---|---|---|
| **统一接口** | `Client.responses.create()`（镜像 OpenAI Responses API） | `IChatService`（callAsync + call + callStream） |
| **Adapter/Dialect** | 10+ adapter：anthropic / openai / gemini / bedrock / vertex / databricks + OpenAI 兼容（groq/deepseek/xai/openrouter/ollama/moonshot） | **4 dialect**：OpenAI / Anthropic / Gemini / Ollama（`LlmDialectFactory.java:19-25`） |
| **模型 catalog** | `model_catalog.py`（947 行，per-provider 枚举 + TTL 缓存 5min）+ 71 个 JSON catalog 文件 | `{provider}.llm.xml` via XDEF |
| **reasoning effort** | `{none,minimal,low,medium,high,xhigh,max}` per harness 子集 | 无独立 reasoning effort 抽象 |
| **模型覆盖** | **广**（借助 OpenAI Responses API 统一 + 多 adapter） | **窄**（4 dialect） |

**分析**：Omnigent 借助 OpenAI Responses API 统一面 + 多 adapter 获得**远比 Nop 广的 provider 覆盖**。Nop 的 `ILlmDialect` SPI 设计更干净（纯函数契约保证前缀缓存安全），但实现数量少。**Nop 的 provider 覆盖是明显短板**——agent-survey 多次指出。

### 11.2 协作面（Omnigent 杀手级）

这是 Omnigent 最显著、Nop **完全没有**的能力：

- **Server + Host + WebSocket + Web UI**：FastAPI server（56 API 路径）+ 内置静态 Web UI bundle（`omnigent/server/static/web-ui/`）+ host 守护（WebSocket 连 server）+ presence（live 多用户光标）
- **多设备同会话**：同一 live session 可从 CLI、浏览器、手机访问
- **Server-managed hosts**（`server/managed_hosts.py`）：server 可 per-session 启动云沙箱，笔记本无需保持在线
- **Auth**（`server/auth.py`）：三模式——内置 accounts（多用户，邀请制）/ oidc（Google/GitHub/Okta/Microsoft）/ header（X-Forwarded-Email 代理后）
- **Cloud sandbox**：`omnigent sandbox --provider <X>`（modal/daytona/islo/cwsandbox）bootstraps host inside remote sandbox
- **多部署**：docker-compose（omnigent + postgres）/ render / railway / fly / hf-spaces / modal / kubernetes

**分析**：Omnigent 是一个**完整的多用户、多设备、云原生产品**。Nop AI Agent 是**服务端嵌入引擎**——无 Web UI、无多用户协作面、无 cloud sandbox。这反映了两者定位的根本差异：Omnigent 是"产品 + 平台"，Nop 是"引擎 + 框架"。Nop 若要做产品形态，需要上层应用补齐（Nop 平台的 GraphQL/前端可承担部分，但协作/presence/cloud sandbox 需新建）。

## 十二、测试与工程化对比

| | Omnigent | Nop AI Agent |
|---|---|---|
| **测试框架** | pytest（asyncio_mode=auto，300s/test 超时，shard + rerunfailures + playwright） | JUnit 5 + Nop AutoTest |
| **测试规模** | tests/ 64+ 子目录，e2e/e2e_ui/e2e_live/integration 分层；`known_failures.yaml`（55 KB）；native bridge 测试巨大（`test_claude_native.py` 215 KB） | agent 模块 **179 个测试文件**，toolkit 21，core 13；重集成覆盖（`TestEndToEndReAct`、`TestSubAgentPermissionEndToEnd`、`TestBudgetedMemoryInjectionEndToEnd`） |
| **类型检查** | pyrefly + mypy（pre-commit） | Java 强类型 |
| **代码质量** | ruff + mypy + pre-commit | checkstyle 约定 |
| **CI 分片** | pytest-shard 跨 CI 分片 + cov 合并 | mvn test -pl <module> |

**分析**：两者测试都很扎实。Omnigent 因 Alpha + 大量胶水代码，有 `known_failures.yaml` 容忍已知失败；Nop 的测试更聚焦引擎正确性（集成 E2E 覆盖好）。

## 十三、设计哲学差异总结

| 维度 | Omnigent（Meta-Harness） | Nop AI Agent（Engine） |
|---|---|---|
| **核心信念** | 复用最强 agent CLI/SDK，叠加治理/协作/隔离 | 自建引擎，循环每步可控可定制 |
| **抽象层** | 控制平面 + 数据平面 + host（三层） | 引擎单体（五层：Application→Platform→Engine→LLM→Tool） |
| **循环归属** | 委托给 harness（不可控内部） | 引擎自拥有（同步 ReAct） |
| **治理重心** | 跨异构后端的 policy（ALLOW/DENY/ASK + 成本 + HITL） | 引擎内 6 层安全 + 拒绝阈值 + sticky-pause |
| **扩展性** | YAML spec + Python callable + policy 三级叠加 | XML DSL + Delta 定制 + IoC（王牌） |
| **隔离范式** | **OS 牢笼**（bubblewrap/seatbelt/seccomp + egress；真实进程在 jail 内有完整 syscall 面） | **应用层门控**（`IToolFileSystem` 路径禁锢 + 虚拟 shell in-JVM 解释无 fork/exec + 6 层安全链 + worktree 设计）；`ISandboxBackend` 是 deferred Layer-4 槽，无实现——主动选择非 OS 路线 |
| **协作** | 多用户、多设备、云原生（杀手级） | 服务端嵌入（无协作面） |
| **Provider** | 广（10+ adapter，Responses API 统一） | 窄（4 dialect） |
| **多 Agent** | worktree 物理并行（**已实现**）+ inbox 异步 | worktree（**已审计设计**，未实现）+ 同步 fork+exec + 权限收紧 |
| **可靠性** | retry-across-harnesses + 看门狗 | checkpoint/resume/restore + denial-ledger |
| **产品形态** | 完整产品 + 平台 | 引擎 + 框架 |

## 十四、Omnigent 的独特优势

1. **Meta-Harness 架构**——包裹异构 agent CLI/SDK，瞬间获得 Claude Code/Codex 全部能力，不重造轮子。这是 agent-survey 中**首个真正意义的 meta-harness 样本**。
2. **Harness 抽象 + Native Bridge**——8 个 harness key + tmux/JSON-RPC 桥 + transcript forwarder，让异构 CLI 在统一 session 下可观察、可治理、可协作。
3. **Policy 治理平面**——6 阶段 × 3 级叠加 × ALLOW/DENY/ASK × CEL（非图灵完备）× 成本治理 × HITL 一等公民。专为多 agent 后端 + 多用户 + 多设备设计。
4. **OS 牢笼隔离范式**——bubblewrap/seatbelt/seccomp + L7 egress 代理 + 云沙箱（modal/daytona/islo/cwsandbox）。**与 Nop 的应用层门控范式正交**（见 §8.4）：适合"agent 必须在牢内跑真实 bash/git/npm 工具链"的场景。
5. **Worktree 并行多 Agent（已实现）**——每个子 agent 一个独立 git worktree，物理隔离文件写入，真实并行编码。
6. **多设备协作面**——Server + Host + WebSocket + Web UI + presence + 多 auth 模式，完整云原生产品。
7. **Cost 治理**——`cost` policy 在预算压力下强制模型降级而非停止；`cost_plan.py` 每轮 brain-model 选择。
8. **Client-side tools**——工具呈现给 LLM 但由调用方执行，让 Web UI 本地执行敏感工具。
9. **Retry-across-harnesses**——一个 harness 失败可切另一个（meta-harness 独有）。
10. **Provider 广覆盖**——10+ adapter，OpenAI Responses API 统一面 + tiktoken 精确 token 估算。

## 十五、Nop AI Agent 的独特优势

1. **DSL-First + Delta 定制**——agent/tool/plan 都是 XML DSL，Delta 层可覆盖任意配置。**Omnigent 完全没有 Delta 系统**。
2. **自拥有 ReAct 循环**——每步可控、可治理、可 hook（12 生命周期点）、可 Delta 定制。适合无人值守对可预测性的要求。
3. **应用层门控隔离范式（非 OS 沙箱路线）**——`IToolFileSystem` 路径禁锢（文件工具）+ 虚拟 shell `nop-ai-shell`（in-JVM 解释，无 fork/exec，无进程注入/syscall 面）+ 6 层安全链 + 危险工具 deny-by-default。与 Omnigent 的 OS 牢笼正交；对"in-JVM agent 执行"更自洽（无 OS 进程即无 OS 攻击面）。**注**：虚拟 shell 接线到 `bash` 工具（`ShellBashExecutor`）是进行中的设计。
4. **6 层安全调度链**——post-denial-guard → tool-access → permission → path-access → security-matrix → approval-gate + denial-ledger 阈值 + sticky-pause + PATH_ARG_KEYS 路径扫描 + secure-by-default 警告。
5. **Checkpoint/Resume/Restore 完整**——3 触发类型 + 3 后端 + sticky-pause + crash-restart + 启动批量恢复。远超 Omnigent。
6. **3 层 Compaction Pipeline**——micro-compression → turn pruning → LLM summary，已实现。
7. **Working Memory Budgeted 注入**——system-prompt 自动注入 budgeted memory（pinned/priority packing）。
8. **Worktree + Branch Affinity Scheduling（已审计设计）**——`IWorktreeManager`（git CLI）+ Worktree Registry（跨节点 orphaned 检测）+ orphaned recovery（TTL 1h）+ `call-agent(workspace=shared|worktree)` + "Worktree 用隔离，Snapshot 用保护"分工。**设计比 Omnigent 更系统，但尚未落地**。
9. **Java/JVM 生态**——可与 Nop IoC/ORM/GraphQL 深度集成，企业级应用优势。
10. **内部 Agent 化（设计）**——薄接口 + 结构化契约，引擎能力可 Agent 实现（Omnigent 无此抽象）。
11. **Coordination Bus（设计）**——`scope_claim`/`operation_intent`/`conflict_alert` 三层冲突检测，LLM 可见（Omnigent 无）。
12. **测试覆盖扎实**——agent 模块 179 测试文件，重集成 E2E。

## 十六、对 Nop AI Agent 的设计启示（借鉴 / 不适用 / 风险）

### P0 — 借鉴 / 推进

1. **接线虚拟 shell 到 `bash` 工具**（推进 Nop 既有设计）——当前生产 `BashExecutor`（`nop-ai-toolkit/tools/BashExecutor.java:43-55`）仍用真实 OS `sh -c`，**不经 `IToolFileSystem`**。`nop-ai-shell-design.md` §4.3 的 `ShellBashExecutor` 是进行中设计。**这是 Nop 隔离范式的最后一块拼图**——落地后 Nop 的应用层门控隔离（虚拟 shell + `IToolFileSystem`）才闭环，无需引入 OS 沙箱。Omnigent 的 OS 沙箱路线对 Nop 不适用（Nop 是 in-JVM 执行，无 OS 进程即无 OS 攻击面）。
2. **Cost 治理**（借鉴）——Omnigent 的 `cost` policy（soft ask + hard max 强制模型降级）+ `cost_plan.py` advisor。Nop 的 `IModelRouter` 默认 PassThrough，无成本治理。建议实现成本感知路由（每轮 brain-model 选择）。
3. **Worktree 并行多 Agent**（推进 Nop 已审计设计）——`nop-ai-agent-branch-affinity-scheduling.md`（status `active`）已设计 `IWorktreeManager`（git CLI）+ Worktree Registry + orphaned recovery + Branch Affinity Scheduling。**不是借鉴 Omnigent，而是推进 Nop 既有设计实现**（当前零 Java 代码）。Omnigent 的 worktree 生产实现可作为工程参考（slug 校验、symlink 大目录、cleanup_stale）。
4. **精确 Token 估算**（借鉴）——Omnigent 用 tiktoken（BPE）。Nop 是 chars/4 + EMA，明显偏弱。建议引入 jtokkit（agent-survey `2026-06-10-token-estimation...` 也曾指出）。

### P1 — 借鉴

5. **Policy 阶段化 + CEL**——Omnigent 的 6 阶段（REQUEST/RESPONSE/TOOL_CALL/TOOL_RESULT/LLM_REQUEST/LLM_RESPONSE）+ CEL 非图灵完备表达式是治理平面的好抽象。Nop 可在 hook/security 之上叠加一层声明式 policy（用 XDSL + 表达式）。
6. **Provider 扩展**——Omnigent 10+ adapter。Nop 仅 4 dialect，覆盖明显不足。建议补 bedrock/vertex/databricks/OpenAI 兼容系（groq/deepseek/xai/openrouter）。
7. **ASK/HITL 一等公民**——Omnigent 的 ASK 暂存 + URL-based elicitation + 服务端解决。Nop 的 approval-gate 默认 auto-approve。建议在交互式场景支持 ASK。
8. **Client-side tools 概念**——工具呈现给 LLM 但由调用方执行。Nop 在未来前端集成时可参考。
9. **Retry-across-harnesses 思路**（虽然 Nop 只有一个引擎，但"失败切备用模型/provider"的思路可借鉴为 model failover）。
10. **Server/Runner/Host 三分离拓扑**——Omnigent 的 host 守护 + server 协调 + runner 数据平面。Nop 多进程部署时可参考（当前是单体引擎）。
11. **★双看门狗模型（IDLE + ABSOLUTE）**（详见 §17.3）——Omnigent 的 IDLE 240s（无 emit 触发，可重置）+ ABSOLUTE 3600s（不可重置）比 Nop 的单一 `maxIterations` 更精准。**填补 Nop"无进展超时检测"空白**——当前 Nop 若 LLM 调用 hang（非 tool），无看门狗能捕获。建议 Nop 引擎增加 IDLE 看门狗（hook/event/tool/LLM 响应重置）。
12. **worktree 工程防御**（详见 §17.1）——Nop worktree 设计整体领先 Omnigent 实现，但可补 5 个具体防御：`--end-of-options` 防 argv 注入、`git worktree list --porcelain` 解析主工作树防嵌套、`_GIT_TIMEOUT_S=120s` 统一超时、运行时 fail-loud 安装检测、路径冲突上限常量化。

### 不适用（Nop 不应走 meta-harness 路线）

1. **包裹异构 CLI/SDK**——Nop 的 DSL-First + Delta 定制要求循环每步可控，meta-harness"委托循环给 harness"与 Nop 哲学冲突。
2. **OS 沙箱（bubblewrap/seatbelt/seccomp）**——与 Nop 的 in-JVM 应用层门控隔离范式冲突。Nop 无 OS 进程（虚拟 shell 不 fork/exec），引入 OS 牢笼是路线倒退。`ISandboxBackend` 保持 deferred Layer-4 即可。
3. **tmux/PTY native bridge**——大量胶水代码（数十万行），只为复用现有 CLI，与 Nop 自建引擎目标不符。
4. **Python asyncio / FastAPI / SQLAlchemy / Pydantic**——技术栈与 Java 不同。
5. **多设备协作 Web UI**——Nop 定位引擎/框架，产品形态由上层应用补齐（Nop GraphQL/前端可承担）。
6. **Transcript forwarder 镜像**——专为 native CLI 设计，Nop 自建循环不需要。

### 风险

1. **Omnigent Alpha 状态**——大段胶水代码 + `known_failures.yaml`（55 KB）容忍已知失败，借鉴时需甄别成熟度。
2. **meta-harness 复杂度爆炸**——`cli.py` 9127 行、`runner/app.py` 11808 行、`server/routes/sessions.py` 762 KB，反映包裹异构 CLI 的工程代价。Nop 不应重蹈。
3. **Provider 锁定 OpenAI Responses API**——Omnigent 统一为 Responses API 面，但部分 provider（Anthropic）原生 API 不是 Responses 风格，adapter 有损失。Nop 的 `ILlmDialect` 纯函数契约更干净。

## 十七、Nop 已有设计 vs Omnigent 实现：逐点对比与可借鉴点

> 本节针对 Nop **已有设计**的三个领域（worktree、隔离、harness/watchdog），把两边的**具体设计细节**逐点对照，并明确指出 Nop 可参考 Omnigent 的**哪些具体工程决策**（而非泛泛"借鉴"）。
>
> **关键反转发现**：在 worktree 领域，**Nop 的设计远比 Omnigent 的实现更系统**——Omnigent 的 worktree 子系统仅 388 行（`host/git_worktree.py`），刻意保持极简；Nop 的 `nop-ai-agent-branch-affinity-scheduling.md`（764 行，已审计）设计了 Worktree Registry 表、跨节点 orphaned 检测、Branch Affinity Scheduling、资源上限、symlink 大目录、per-worktree 锁、Fast Resume、crash 策略等 Omnigent **完全没有**的机制。借鉴方向因此部分反转。

### 17.1 Worktree 子系统逐点对比

**Omnigent worktree 真实形态**（`~/ai/omnigent/omnigent/host/git_worktree.py`，388 行）：
- 公共 API 仅 4 个：`validate_branch_name`、`create_worktree`、`remove_worktree`、`WorktreeError`
- 状态存储：`conversations` 表上两个 nullable 列（`workspace`、`git_branch`）——**无独立 Registry 表**
- 触发方式：仅 `POST /v1/sessions` 请求体的 `git` 字段 opt-in——**非自动 per-subagent**
- **无** symlink 策略、**无** cleanup_stale、**无** TTL、**无** 并发锁、**无** slug 哈希、**无** 资源上限、**无** cross-host、**无** orphan sweeper
- crash 恢复：仅同步 rollback（`_rollback_worktree` in `hosts.py:443-474`），**无后台 reaper**——server 崩溃会留孤儿 worktree
- Polly 示例的 worktree 描述是**给 LLM 看的散文**，配置层无任何 worktree 自动化

| 设计点 | Omnigent（实现） | Nop（设计，`branch-affinity-scheduling.md`） | 谁更完整 | Nop 可参考 Omnigent 什么 |
|---|---|---|---|---|
| **slug 派生** | `_sanitize_dirname`：`branch.strip("/").replace("/", "-")`，无哈希无截断（`git_worktree.py:83-91`） | 正则 `^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$` + 禁 `..`/绝对路径（§3.4.3, §3.7.2） | **Nop 更严**（字符集+长度+穿越防护） | — |
| **分支创建** | `git worktree add -b <branch> <path> [--end-of-options <base>]`（`git_worktree.py:318-325`） | `git worktree add -B agent/worktree/{slug} {path} HEAD`（§3.4.3 步骤 4） | 平手（都用命名分支，都拒绝 detached HEAD） | **`--end-of-options` 防 argv 注入**（`base_branch` 用户可控时，`--exec-path` 等不能变成 git flag）——Nop 设计未提此防御，应补 |
| **路径布局** | `<parent-of-repo>/<repo-name>-worktrees/<slug>[-N]`（repo 的**兄弟目录**，`git_worktree.py:207-220`） | `{data_dir}/ai-worktrees/{project_hash}/{slug}`（**集中存储**，project_hash=SHA-1(project_id)[:12]，§3.4.4） | 各有理（Omnigent 跟 repo 走便于 git 发现；Nop 集中便于统一清理） | **main-work-tree 解析**：Omnigent 用 `git worktree list --porcelain` 取主工作树（`_main_work_tree`, `git_worktree.py:140-171`），防止 linked-worktree 路径传入时 worktree 嵌套——Nop 应在创建前做同样解析 |
| **路径冲突** | `-2, -3, ... -50` 后缀，超 50 报错（`_MAX_DIR_COLLISION_SUFFIX=50`, `git_worktree.py:211-219`） | `-2, -3, ...` 最多尝试 10 次（§3.4.3 步骤 2） | Omnigent 上限更宽松 | **显式上限常量化**——Nop 的"最多 10 次"应提为命名常量并文档化失败语义 |
| **git 调用** | `subprocess.run(["git", *args], timeout=120s, check=False)`，argv list 非 shell（`_run_git`, `git_worktree.py:110-122`） | ProcessBuilder 调系统 git（§3.4.3, §5.8a） | 平手 | **`_GIT_TIMEOUT_S=120s` 单次硬上限** + `FileNotFoundError`→"git 未安装"明确错误 + `TimeoutExpired`→明确错误——Nop 应在每个 git 调用加统一超时 + 安装检测 |
| **install 检测** | bwrap resolve 时 fail-loud 给安装提示（`bwrap_sandbox.py:232-238`）；git 调用 `FileNotFoundError`→"git is not installed on the host" | 部署文档注明 git≥2.17 依赖（§5.8a） | Omnigent 运行时检测更友好 | **运行时 fail-loud + 安装提示**——Nop 应在 worktree 首次调用时检测 git 存在性 + 版本，而非仅靠部署文档 |
| **crash 恢复** | 仅同步 rollback（`_rollback_worktree`），**无 sweeper**，server 崩溃留孤儿 | RecoveryManager 定时扫描 Registry，orphaned 超过 TTL（默认 1h）自动清理 + 启动扫描（§3.4.7, §3.4.6b 级联终止 30s） | **Nop 完胜**（Omnigent 是已知缺陷） | —（反而是 Omnigent 可借鉴 Nop 的 orphaned 回收） |
| **资源隔离** | **无任何上限**（grep `max_worktrees`/`WORKTREE_LIMIT` 零命中） | 单 session 最多 4、单 node 最多 16、总大小限制 + 磁盘空间预检 1GB（§3.7.2, §3.4.3 步骤 7） | **Nop 完胜** | — |
| **并发控制** | **无锁**（仅靠 git ref-locking + 路径冲突后缀兜底） | 每个 worktree 目录一个 `ReentrantLock`（支持 tryLock 超时防死锁，§3.7.2, §3.3.4） | **Nop 更稳** | — |
| **symlink 大目录** | **完全不存在**（worktree 是裸 `git worktree add`） | symlink `{target, node_modules, .m2, .gradle, .venv, __pycache__}`，Windows 用 junction（§3.4.3 步骤 5） | **Nop 独有创新**（Omnigent 没有） | — |
| **Snapshot 分工** | 无 snapshot 概念 | "Worktree 用隔离，Snapshot 用保护"——JGit 独立外部 git 快照，tree hash 无 commit，不跟随 symlink（§3.3） | **Nop 独有创新** | — |
| **Branch Affinity Scheduling** | 无（host 按需 spawn runner） | 调度单元=(project,branch)，强亲和/弱亲和/跨分支并行（§3.1） | **Nop 独有创新** | — |
| **Registry 持久化** | `conversations` 表两列（`workspace`/`git_branch`） | 独立 Worktree Registry 表（worktreeId/sessionId/parentSessionId/nodeId/slug/worktreePath/branch/status，§6.2） | **Nop 更完备**（支持跨节点 orphaned 检测） | Omnigent 的极简（两列）是反思点：**Nop 的 Registry 表 + 跨节点 orphaned 在单进程默认部署下可能过度设计**——可分层：单进程用内存 Registry，分布式才启用 DB Registry + nodeId |

**17.1 小结**：worktree 领域 Nop 设计整体领先，但可从 Omnigent 借鉴 5 个**具体工程防御**：(1) `--end-of-options` 防 argv 注入；(2) `git worktree list --porcelain` 解析主工作树防嵌套；(3) `_GIT_TIMEOUT_S=120s` 统一超时；(4) 运行时 fail-loud 安装检测；(5) 路径冲突上限常量化。同时 Omnigent 的**极简哲学**是反思点——Nop 应考虑 Registry 的分层启用（单进程内存 / 分布式 DB）。

### 17.2 隔离范式逐点对比（虚拟 shell + IToolFileSystem vs OS 牢笼）

**核心差异重申**：Omnigent = kernel-enforced jail（真实进程有完整 syscall 面，禁锢在 mount-namespace）；Nop = application-layer gating（in-JVM 解释无 fork/exec，文件操作经 `IToolFileSystem`）。两者**正交**，不是缺口关系。

| 防护点 | Omnigent（OS 牢笼） | Nop（应用层门控） | Nop 可参考什么 |
|---|---|---|---|
| **敏感路径** | `_cwd_scan.py` 递归 DFS 扫描 cwd，mask dotfile + escaping symlink（basename 以 `.` 开头且不在 allow_hidden，或 symlink 目标在 safe_roots 外）；deprioritized dirs（`node_modules, .venv, .mypy_cache, .codex-tmp`）最后走以省预算；`follow_symlinks=False` 防循环（`_cwd_scan.py:95-100,129+`） | `DefaultPathAccessChecker` **硬编码 deny-list**（`~/.ssh`/`~/.aws`/`/etc/`/`.env`/`id_rsa`/路径穿越，`security/DefaultPathAccessChecker.java:11-76`）+ per-agent `RuleBasedPathAccessChecker` glob 规则 | **递归 cwd 扫描 + escaping-symlink 检测**——Nop 当前只检查"工具参数里的 path"，不检测"workDir 内的 symlink 是否逃逸"。可借鉴 `_cwd_scan` 的扫描模型作为 `IPathAccessChecker` 的增强模式（尤其当 workDir 是用户提供的项目目录时） |
| **dotfile allowlist** | `cwd_allow_hidden` 默认 `(".venv",)`，其余 dotfile 全 mask（`bwrap_sandbox.py:94-175`） | 无 dotfile 概念（仅路径模式匹配） | **dotfile allowlist 语义**——workDir 内默认 mask 所有 dotfile，显式 allowlist 才放行（适合防止 `.npmrc`/`.pypirc` 泄露 token） |
| **网络 egress** | L7 egress 代理 + TLS MITM：per-host 叶子证书由 CA 签，规则 DSL `"METHODS host/path/glob"` 默认拒绝，DNS safe-host 正则 `^[A-Za-z0-9.\-]+$`（防 NUL/`%`/CRLF/smuggling），阻断 RFC1918/loopback，随机端口 + fail-loud 绑定（防 port-squat MITM）（`inner/egress/`） | **无网络 egress 控制**（`http-request`/`graphql-query` 工具无出站过滤） | **egress 规则 DSL + DNS 安全正则**——若 Nop 未来对 `http-request` 工具加出站控制，Omnigent 的规则格式（`"GET api.github.com/orgs/**"` 默认拒绝）+ DNS 正则（防 Claude Code CVE-class smuggling）可直接参考；**随机端口 + fail-loud 绑定**是防同主机 MITM 的关键 |
| **CA 信任** | 不写系统信任库，经 5 个 env vars 注入 scratch 里的 bundle（`SSL_CERT_FILE/REQUESTS_CA_BUNDLE/NODE_EXTRA_CA_CERTS/CURL_CA_BUNDLE/PIP_CERT`，`controller.py:50-56`）；sandbox 内可写的 bundle 仅用于客户端信任，代理上游用 host-only 不可变 bundle | 无（无 TLS 拦截需求） | **env-var 注入 CA 模式**——若 Nop 加 egress 控制，避免写系统信任库（用户资产），用 env vars 注入 scratch bundle |
| **seccomp** | `BASELINE_DENYLIST_SYSCALLS`（k8s RuntimeDefault + 本地补充 `ptrace`/`process_vm_readv`），多架构覆盖（i386/x32/ARM32），`clone(CLONE_NEW*)` 按位拒绝、`clone3` 返回 ENOSYS（非 EPERM，兼容 glibc 2.34+ fallback）（`_seccomp.py:424-546`, `bwrap_sandbox.py:508-611`） | 不适用（in-JVM 无 syscall 面） | **不直接适用**，但 Nop 的虚拟 shell P1 命令（git/mvn/npm）**委托真实 OS 进程**（`nop-ai-shell-design.md` §6.2）时，这些外部进程**没有 seccomp 保护**——可参考 Omnigent 的 baseline denylist 为 Nop 的 `ProcessBuilder` 外部进程加 seccomp/jail |
| **隔离范围** | **per-helper-process**（`resolve_sandbox` 仅在 helper/terminal/executor spawn 时调用，`os_env.py:770`/`terminal.py:1587` 等；非 per-session/turn）——policy 随 spawn 构建，生命周期=helper 进程生命周期 | per-tool-call（每次工具调用经 `IToolFileSystem` + 6 层安全链） | **per-spawn 构建策略**的设计决策——Nop 的外部进程委托也应"每次 spawn 构建一次策略"，而非每命令重复 |
| **native CLI + tmux** | tmux server 在 host 不沙箱；pane 内的整个进程树沙箱；tmux control socket 加入 `deny_unix_socket_paths`（bwrap overlay `/dev/null`，seatbelt AF_UNIX deny），防沙箱内 `tmux -S <sock> run-shell` 攻击 unsandboxed server（`terminal.py:842-866,1600`） | 不适用（无 native CLI） | **控制 socket 否认**思路——若 Nop 未来引入外部守护进程（如长驻 mvn daemon），其控制 socket 应被 path-access-checker 否认 |

**17.2 小结**：隔离范式正交，Nop 不应引入 OS 沙箱。但 Omnigent 有 3 个**应用层可借鉴的具体防护**：(1) `_cwd_scan` 递归扫描 + escaping-symlink 检测（强化 `IPathAccessChecker`）；(2) dotfile allowlist 语义；(3) egress 规则 DSL + DNS 安全正则（未来 `http-request` 出站控制参考）。另外 Nop 虚拟 shell P1 委托真实进程时缺 seccomp，可参考 Omnigent baseline denylist。

### 17.3 Harness / Watchdog 逐点对比

| 点 | Omnigent（`runtime/harnesses/_scaffold.py`） | Nop（`ReActAgentExecutor`） | Nop 可参考什么 |
|---|---|---|---|
| **看门狗模型** | **双看门狗**：IDLE 240s（无 emit 触发）+ ABSOLUTE 3600s（总上限）；IDLE 在每次非心跳 emit 时 reschedule，ABSOLUTE **从不** reschedule（`_scaffold.py:97-109,1373-1462`）；IDLE 取代了之前的固定累计 cap（会误杀长但健康的 turn） | **无 IDLE 看门狗**——仅 `maxIterations`（默认 10）+ `toolTimeoutSeconds`（per-tool）+ forced-stop 90% token；无"无进展超时"检测 | **★最高价值借鉴：双看门狗模型**——Nop 应增加 IDLE 看门狗（如 60-120s 无 hook/event/tool/LLM 响应则判定卡死）。当前 Nop 若 LLM 调用 hang，只能靠 `toolTimeoutSeconds`（但 LLM 调用不是 tool）。双看门狗（IDLE 可重置 + ABSOLUTE 不可重置）比单一 `maxIterations` 更精准 |
| **心跳** | 15s heartbeat（`_HEARTBEAT_INTERVAL_S=15`，`_scaffold.py:80,1464-1477`），匹配 workflow 调度 | 无心跳概念 | **长 turn 心跳**——Nop 长任务（如 plan 模式、多 agent 编排）应发心跳事件，让 `AgentEventPublisher` 的外部观察者知道仍在运行 |
| **优雅关停** | `_SHUTDOWN_GRACE_S=4.5s` < process_manager 的 5s SIGKILL 升级（`_scaffold.py:82-88`）；lifespan teardown 取消在途 Future + 调子类 shutdown hook + drain | `cancelSession`（graceful 设取消标志下个迭代边界停 / forced 中断执行线程） | **grace < kill 升级时序**——Nop 的 forced cancel 可参考"grace 期 < 上层 kill 期"的时序，确保 teardown 有窗口完成 |
| **in-band steering** | `message` 事件带 `previous_response_id == 当前 response_id` → 路由到 `_push_injection`（steering），非新 turn（`_scaffold.py:1163-1178`）；subclass 在 LLM 迭代间短超时（~1s）poll `ctx.next_injection()` | steering 设计文档定义，**未实现** | **`previous_response_id` steering 模式**——Nop 实现 steering 时可参考此具体路由模式（按 correlationId 关联在途 turn） |
| **policy eval 超时** | `_POLICY_EVAL_TIMEOUT_S=35s`（≥ classifier 30s），超时 **fail-open**（返回 ALLOW，`_scaffold.py:90-95,640-646`）——stalled verdict 不 hang executor | fail-closed（policy/安全链异常→拒绝） | **哲学差异，非借鉴**——Omnigent fail-open（可用性优先，因 policy 是治理层），Nop fail-closed（安全优先，因安全是强制层）。**Nop 不应改**，但应文档化此选择的理由 |
| **policy 评估接线** | 两条路径：(1) in-process scaffold `ctx.evaluate_policy`（harness 自有循环时，LLM_REQUEST 前 + TOOL_CALL 前）；(2) native CLI hook 桥（`/v1/sessions/{id}/policies/evaluate`，ALLOW→None 让 harness consent 仍触发，DENY/ASK→deny，ASK fail-closed 由服务端 elicitation 解决）（`native_policy_hook.py:103-116`） | 6 层调度链在 dispatchLoop 内（`ReActAgentExecutor:795-955`），无独立 policy eval 超时 | **policy 与 consent 的分层**——Omnigent 把"policy 判定"与"harness 自身 consent"分离（ALLOW 不抑制 harness consent）。Nop 若引入交互式 ASK，可参考此分层 |
| **TurnContext 5 方法** | `emit`/`dispatch_tool`/`elicit`/`next_injection`/`evaluate_policy`（`_scaffold.py:347-693`）；每个非心跳 emit 重置 IDLE 看门狗 | `AgentExecutionContext` + hook 系统（12 点） | **emit-as-progress 语义**——Nop 的 hook 系统可借鉴"特定事件类型重置看门狗"的语义（如 POST_REASONING/POST_ACTING 重置 IDLE） |

**17.3 小结**：harness 领域最高价值借鉴是 **Omnigent 的双看门狗模型（IDLE 可重置 + ABSOLUTE 不可重置）**——这填补 Nop"无进展超时检测"的空白。其次是长 turn 心跳、grace/kill 升级时序、`previous_response_id` steering 路由模式。policy fail-open vs fail-closed 是哲学差异，Nop 应保持 fail-closed 但文档化理由。

### 17.4 可借鉴清单（具体工程决策，按领域）

| 领域 | 借鉴项 | Omnigent 证据 | 落地建议 |
|---|---|---|---|
| worktree | `--end-of-options` 防 argv 注入 | `git_worktree.py:318-325` | `IWorktreeManager` 创建分支时 base 参数加 `--end-of-options` |
| worktree | `git worktree list --porcelain` 解析主工作树 | `git_worktree.py:140-171` | 创建前解析主工作树，防 linked-worktree 嵌套 |
| worktree | `_GIT_TIMEOUT_S=120s` 统一超时 + fail-loud | `git_worktree.py:18,110-122` | 每个 git 调用统一超时 + 安装/超时明确错误 |
| worktree | Registry 分层启用（反思 Omnigent 极简） | `db_models.py:390-395`（仅两列） | 单进程内存 Registry / 分布式 DB Registry + nodeId |
| 隔离 | `_cwd_scan` 递归扫描 + escaping-symlink 检测 | `_cwd_scan.py:95-100,129+` | `IPathAccessChecker` 增强模式（workDir 是用户项目时） |
| 隔离 | dotfile allowlist 语义 | `bwrap_sandbox.py:94-175` | workDir 内默认 mask dotfile，显式 allowlist |
| 隔离 | egress 规则 DSL + DNS 安全正则 | `inner/egress/rules.py:5-65` | 未来 `http-request` 出站控制参考 |
| 隔离 | seccomp baseline denylist | `_seccomp.py:424-546` | 虚拟 shell P1 委托真实进程（git/mvn/npm）时加 jail |
| harness | **双看门狗（IDLE + ABSOLUTE）** | `_scaffold.py:97-109,1373-1462` | ★最高价值——Nop 增加 IDLE 看门狗（无进展超时） |
| harness | 长 turn 心跳 15s | `_scaffold.py:80,1464-1477` | `AgentEventPublisher` 发心跳事件 |
| harness | grace(4.5s) < kill(5s) 升级时序 | `_scaffold.py:82-88` | forced cancel 时序参考 |
| harness | `previous_response_id` steering 路由 | `_scaffold.py:1163-1178` | Nop steering 实现参考 |


## Conclusion

Omnigent 与 Nop AI Agent **不在同一抽象层**，是两种正交的架构原型：

- **Omnigent = Meta-Harness（控制平面）**：包裹异构 agent CLI/SDK，叠加治理/协作/隔离，本身不拥有循环。强在 policy 治理、OS 牢笼隔离、worktree 并行（已实现）、多设备协作、provider 广覆盖。代价是大量胶水代码 + 循环内部不可控。
- **Nop AI Agent = Engine（引擎）**：自拥有 ReAct 循环，每步可控可 Delta 定制可治理。强在 DSL-First、应用层门控隔离（虚拟 shell + `IToolFileSystem`）、6 层安全、checkpoint/resume、compaction pipeline、JVM 生态。代价是起步工作量大、provider 覆盖窄、虚拟 shell 尚未接线到 bash 工具、worktree 仅设计未实现。

**对 Nop 的核心启示**：

1. **Nop 不应走 meta-harness 路线，也不应引入 OS 沙箱**——前者与 DSL-First + 循环可控哲学冲突，后者与 Nop 的 in-JVM 应用层门控隔离范式冲突（无 OS 进程即无 OS 攻击面）。Nop 的隔离路线（虚拟 shell + `IToolFileSystem`）在理念上对 in-JVM 执行更自洽。
2. **P0 推进：接线虚拟 shell 到 bash 工具 + cost 治理 + 推进 worktree 已审计设计 + 精确 token 估算**——前两项是 Nop 既有设计闭环，后两项是真实工程缺口（Omnigent 已验证可行）。
3. **Nop 的 checkpoint/resume/restore + 应用层门控隔离 + Delta 定制是 Omnigent 没有的差异化**，应继续做深，这是企业级无人值守场景的王牌。
4. **meta-harness 是 agent-survey 中此前未出现的架构原型**——本报告填补了这一对比维度。后续若评估"包裹异构 agent"需求，Omnigent 是该路线的标杆样本。

被否决的方案：**(1) Nop 转型为 meta-harness**——与 DSL-First + 循环可控哲学冲突 + 复杂度爆炸 + 失去 Delta 定制差异化。**(2) Nop 引入 OS 沙箱（bubblewrap/seatbelt）**——与 in-JVM 应用层门控范式冲突，无 OS 进程即无 OS 攻击面，引入 OS 牢笼是路线倒退。

后续工作：本报告作为 agent-survey 的 meta-harness 维度基线；具体借鉴项（OS 沙箱/cost 治理/worktree 并行/token 估算）若进入实施，指向 `ai-dev/design/nop-ai-agent/` 对应设计文档更新 + `ai-dev/plans/`。

## Open Questions

- [ ] Omnigent 的 CEL policy 表达式能否映射为 Nop 的 XDSL + 表达式层？两者在声明式治理上是否有融合点？
- [ ] Omnigent 的 worktree（已实现）与 Nop 设计中的 Branch Affinity Scheduling + Coordination Bus 能否结合——用 worktree 物理隔离 + Coordination Bus 逻辑协调？Nop worktree 设计（`nop-ai-agent-branch-affinity-scheduling.md`）推进实现时可直接参考 Omnigent 的 slug 校验 / symlink 大目录 / cleanup_stale 工程。
- [ ] Nop 虚拟 shell 接线到 `bash` 工具（`ShellBashExecutor`）后，哪些 OS 工具链命令（mvn/npm/git）仍需真实 OS 进程？是否需要"白名单 external fallback"（虚拟 shell 优先 + 受控 external adapter）？
- [ ] Omnigent 的 client-side tools 概念是否适用于 Nop 未来的前端集成（GraphQL/MCP 场景）？
- [ ] Omnigent 的 retry-across-harnesses 思路能否改造为 Nop 的"model/provider failover"（单引擎内失败切备用模型）？

## References

### Omnigent（外部，`~/ai/omnigent/`）
- `~/ai/omnigent/README.md` — 项目定位
- `~/ai/omnigent/pyproject.toml` — Databricks 出品、Apache-2.0、Alpha、依赖
- `~/ai/omnigent/NOTICE` — 版权
- `~/ai/omnigent/omnigent/harness_aliases.py:14-23` — NATIVE_HARNESSES
- `~/ai/omnigent/omnigent/runtime/harnesses/__init__.py:34-68` — 8 个 harness 注册表
- `~/ai/omnigent/omnigent/inner/claude_sdk_executor.py:1612-1691` — "SDK 跑自己的循环，我们观察"
- `~/ai/omnigent/omnigent/claude_native_bridge.py:1-25` — native bridge 会合点架构
- `~/ai/omnigent/omnigent/native_policy_hook.py:1-165` — native CLI policy 桥
- `~/ai/omnigent/omnigent/policies/types.py:54-71` — 6 阶段 policy
- `~/ai/omnigent/omnigent/runtime/policies/engine.py:222-342` — PolicyEngine 组合循环
- `~/ai/omnigent/omnigent/policies/builtins/` — safety/cost/google/github/cel/prompt 内置 policy
- `~/ai/omnigent/omnigent/runner/app.py:7458-7513` — `_run_turn_bg`
- `~/ai/omnigent/omnigent/runner/app.py:4905-4937` — 非 native 崩溃恢复
- `~/ai/omnigent/omnigent/runtime/harnesses/_scaffold.py:80,102-109` — 心跳 + 看门狗
- `~/ai/omnigent/omnigent/host/git_worktree.py` — per-session worktree
- `~/ai/omnigent/omnigent/inner/bwrap_sandbox.py` / `inner/seatbelt_sandbox.py` / `inner/egress/` — OS 沙箱
- `~/ai/omnigent/omnigent/cost_plan.py:1-221` — cost advisor 契约
- `~/ai/omnigent/omnigent/tools/builtins/spawn.py` — sys_session_* 子 agent 工具
- `~/ai/omnigent/omnigent/server/` — FastAPI server + Web UI + auth
- `~/ai/omnigent/openapi.json` — 56 API 路径
- `~/ai/omnigent/docs/POLICIES.md` — policy 指南
- `~/ai/omnigent/docs/AGENT_YAML_SPEC.md` — agent YAML spec
- `~/ai/omnigent/examples/polly/config.yaml` — 多 agent 编码编排示例
- `~/ai/omnigent/examples/debby/config.yaml` — 双头头脑风暴示例

### Nop AI Agent（本仓库）
- `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java:576-1102` — ReAct 循环
- `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java:673-707` — forkSession
- `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/ToolPathArgKeys.java:18-21` — PATH_ARG_KEYS
- `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/DefaultPathAccessChecker.java:11-76` — 敏感路径拒绝 + 穿越防护
- `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/tool/CallAgentExecutor.java:169-181` — 父权限约束
- `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/compact/PipelineCompactor.java:36-127` — 3 层压缩
- `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/` — checkpoint/resume/restore
- `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/CalibratedTokenEstimator.java` — token 启发式估算
- `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/IApprovalGate.java:15` — `ISandboxBackend` deferred Layer-4 注释（证明 OS 沙箱是主动选择不做）
- `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/fs/IToolFileSystem.java` — 文件系统抽象（17 方法）
- `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/fs/LocalToolFileSystem.java:27-50` — workDir 禁锢 + `isPathAllowed` canonical-path 检查
- `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/BashExecutor.java:43-55` — 当前 bash 工具仍用真实 OS `sh -c`（虚拟 shell 尚未接线）
- `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/executor/ShellCommandExecutor.java:65-67,178-183` — 虚拟 shell in-JVM 解释（无 ProcessBuilder）
- `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/adapter/ExternalCommandAdapter.java:8-12` — external fallback 抛 UnsupportedOperationException
- `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/commands/IShellCommandExecutionContext.java:30` — `fileSystem()` 暴露 IToolFileSystem
- `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/dialect/LlmDialectFactory.java:19-25` — 4 dialect
- `ai-dev/design/nop-ai-agent/01-architecture-baseline.md` — 五层架构 + 部署模型 + 通信模型
- `ai-dev/design/nop-ai-agent/02-execution-model.md` — 双循环/Hook/Steering 设计
- `ai-dev/design/nop-ai-agent/nop-ai-agent-multi-agent.md` — Coordination Bus 设计
- `ai-dev/design/nop-ai-agent/nop-ai-agent-branch-affinity-scheduling.md` — **Branch Affinity Scheduling + Worktree 设计（status active，已审计；零 Java 实现）**
- `ai-dev/design/nop-ai-agent/nop-ai-shell-design.md` — **虚拟 shell 设计（§4.3 ShellBashExecutor 接线计划；§7.4 隔离风险对比）**
- `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md:32-33` — `ISandboxBackend` 列为 Layer-4 deferred（证明 OS 沙箱是主动选择不做）

### 相关调研
- `ai-dev/analysis/agent-survey/2026-06-05-agent-design-elements-synthesis.md` — 15 维度对比词汇表（本报告复用并扩展）
- `ai-dev/analysis/agent-survey/2026-06-12-nop-ai-vs-mimo-code-deep-comparison.md` — 对比报告模板参考
- `ai-dev/analysis/agent-survey/2026-06-08c-openharness-survey.md` — worktree 隔离调研
- `ai-dev/analysis/agent-survey/2026-06-10-token-estimation-and-context-compression-survey.md` — token 估算缺口
