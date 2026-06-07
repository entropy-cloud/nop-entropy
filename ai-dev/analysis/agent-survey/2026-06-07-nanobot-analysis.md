# nanobot 技术分析

> Status: open
> Date: 2026-06-07
> Scope: ~/ai/nanobot — HKUDS 开源超轻量个人 AI Agent
> Conclusion:

## Context

- nanobot 是香港大学数据科学实验室 (HKUDS) 开源的超轻量个人 AI Agent 框架
- Python 原生，以异步 agent loop 为核心，内建 WebUI、18 个聊天渠道、35+ LLM Provider、Dream 两阶段记忆、MCP、持续目标（sustained goals）、子 agent 等完整能力
- 当前版本 v0.2.1（2026-06-01），从 2026-02-02 首次发布，4 个月内经历了 ~100 个版本迭代，开发节奏极快
- 调研目的：深入理解 nanobot 的架构设计、agent loop 机制、记忆系统、工具体系，为 Nop Agent 生态提供参考

## Analysis

### 项目定位

- **组织**: HKUDS (香港大学数据科学实验室)，个人项目由 Xubin Ren 维护
- **许可**: MIT
- **语言**: Python 3.11+，asyncio 全异步，ruff lint
- **LOC**: ~25–30K 行 Python 核心（nanobot/ 目录，含 agent/bus/channels/providers/session/config/security/utils/command/cron/pairing/templates 等），WebUI 为 React/TypeScript Vite SPA
- **Provider 注册表**: 37 个 `ProviderSpec` 条目（registry.py 单一事实来源）
- **GitHub**: https://github.com/HKUDS/nanobot
- **定位**: **可真正拥有的超轻量个人 AI Agent**——不是框架/SDK，是可直接运行的个人 Agent 产品
- **差异化**: 极小的可读核心 + 18 渠道（16 适配器 + CLI + API） + 37 Provider + 持久工作流 + 自托管

### 顶层架构

```
nanobot/
├── nanobot/
│   ├── agent/
│   │   ├── loop.py          # AgentLoop: 核心处理引擎，状态机驱动的消息处理
│   │   ├── runner.py        # AgentRunner: LLM 对话循环 + 工具执行 + context governance
│   │   ├── context.py       # ContextBuilder: 系统提示 + 上下文组装（identity + skills + memory + runtime）
│   │   ├── memory.py        # MemoryStore + Consolidator: 文件 I/O + token 预算压缩
│   │   ├── autocompact.py   # AutoCompact: TTL 驱动的空闲会话压缩（冷/热双路径摘要恢复）
│   │   ├── subagent.py      # SubagentManager: 后台子 agent 管理（含隔离工具集）
│   │   ├── hook.py          # AgentHook 生命周期 + CompositeHook 扇出（错误隔离）+ SDKCaptureHook
│   │   ├── progress_hook.py # AgentProgressHook: runner 事件 → 渠道进度 UI 桥接
│   │   ├── model_presets.py # 运行时模型预设切换（ProviderSnapshot + 变更检测）
│   │   ├── skills.py        # SkillsLoader: skill 发现和加载
│   │   └── tools/           # 工具体系（24 模块，含内置 + MCP + 插件）
│   │       ├── base.py      # Tool 抽象基类（含自定义 Schema 类型系统）
│   │       ├── schema.py    # JSON Schema 片段类型系统（String/Integer/Array/Object 等）
│   │       ├── registry.py  # ToolRegistry: 动态注册 + builtin/MCP 分离排序 + 缓存
│   │       ├── loader.py    # ToolLoader: pkgutil 扫描 + entry_points 插件
│   │       ├── context.py   # RequestContext + contextvars 绑定（channel/chat_id/session_key）
│   │       ├── file_state.py   # FileStates: per-session 文件读写状态追踪（防重复/防过期）
│   │       ├── filesystem.py   # 读写文件、编辑、目录列表
│   │       ├── shell.py        # Shell 执行（含 bwrap 沙箱后端 + SSRF 防护）
│   │       ├── exec_session.py # 长时间运行命令会话（yield/poll/write_stdin）
│   │       ├── sandbox.py      # 沙箱后端注册表（bwrap bind-mount + tmpfs 隔离）
│   │       ├── web.py          # Web 搜索/抓取
│   │       ├── search.py       # Grep/find_files
│   │       ├── spawn.py        # 子 agent 生成
│   │       ├── long_task.py    # 持续目标（sustained goals）
│   │       ├── mcp.py          # MCP 服务器管理（运行时连接/重载/工具发现）
│   │       ├── apply_patch.py  # 统一 diff patch 应用
│   │       ├── cron.py         # 定时任务
│   │       ├── image_generation.py  # 图片生成
│   │       ├── cli_apps.py     # CLI 应用扩展（@appname 路由）
│   │       └── self.py         # Agent 自修改工具（运行时状态/预设切换）
│   ├── bus/
│   │   ├── queue.py           # MessageBus: 异步 inbound/outbound 消息队列
│   │   ├── events.py          # InboundMessage / OutboundMessage + runtime control 常量（MCP reload 等）
│   │   ├── runtime_events.py  # RuntimeEventBus: 进程内状态通知（turn 生命周期、goal/model 变更）
│   │   └── progress.py        # 进度回调工厂：agent progress → outbound metadata（tool hints/reasoning/file edits）
│   ├── providers/             # LLM Provider 体系
│   │   ├── base.py            # LLMProvider: 抽象基类 + 精细化重试策略
│   │   ├── anthropic_provider.py
│   │   ├── openai_compat_provider.py
│   │   ├── openai_responses/  # OpenAI Responses API（含 converters + parsing）
│   │   ├── azure_openai_provider.py
│   │   ├── bedrock_provider.py
│   │   ├── github_copilot_provider.py
│   │   ├── openai_codex_provider.py
│   │   ├── fallback_provider.py  # 熔断器 + 流式感知 failover + 多级 fallback 链
│   │   ├── image_generation.py
│   │   ├── transcription.py
│   │   ├── factory.py         # Provider 工厂（ProviderSnapshot 含 signature 变更检测）
│   │   └── registry.py        # Provider 注册表（35+ ProviderSpec 单一事实来源）
│   ├── channels/              # 18 渠道适配器
│   │   ├── base.py            # BaseChannel: 抽象基类 + 权限 + 流式 + reasoning
│   │   ├── manager.py         # ChannelManager: 发现 + 协调 + 流式 delta 合并 + 重复抑制
│   │   ├── registry.py        # 渠道注册表
│   │   ├── telegram.py, discord.py, slack.py, feishu.py, ...
│   │   ├── websocket.py       # WebUI WebSocket
│   │   └── ...
│   ├── session/               # 会话管理
│   │   ├── manager.py         # SessionManager: 持久化 + 历史 + atomic write
│   │   ├── goal_state.py      # 持续目标状态追踪（含 LLM 超时豁免 + runtime context 注入）
│   │   └── turn_continuation.py  # Turn 延续：max_iterations 后透明续接（最多 12 轮）
│   ├── config/
│   │   ├── schema.py          # Pydantic 配置模型（518 行）
│   │   └── loader.py          # 配置加载
│   ├── security/
│   │   ├── workspace_access.py  # 工作区访问控制 + contextvars 绑定 + 沙箱状态
│   │   └── network.py         # SSRF 防护（internal URL 检测 + 分层 loopback 策略）
│   ├── skills/                # 内建 skill 定义（SKILL.md）
│   │   ├── long-goal/SKILL.md
│   │   ├── cron/SKILL.md
│   │   ├── memory/SKILL.md
│   │   ├── github/SKILL.md
│   │   ├── image-generation/SKILL.md
│   │   ├── clawhub/SKILL.md   # 外部 skill 市场
│   │   ├── skill-creator/SKILL.md  # Skill 创建向导
│   │   └── ...
│   ├── command/               # Slash 命令路由（三层分发：priority/exact/prefix）
│   │   ├── __init__.py        # CommandRouter + CommandContext
│   │   └── builtin.py         # ~20 内建命令（/new /stop /restart /status /model /goal /skills 等）
│   ├── cron/
│   │   ├── service.py         # CronService: at/every/cron 三种调度 + FileLock 持久化
│   │   └── types.py           # CronSchedule 类型定义
│   ├── pairing/
│   │   └── store.py           # 配对码存储（8 位码 + TTL + atomic write + thread-safe）
│   ├── templates/             # 默认 prompt 模板（agent 系统提示 + 工作区种子文件）
│   │   ├── SOUL.md, USER.md, AGENTS.md, HEARTBEAT.md
│   │   ├── memory/MEMORY.md
│   │   └── agent/             # 11 个系统提示模板（identity, tool_contract, dream, consolidator 等）
│   ├── apps/cli/              # CLI App 扩展（@appname 路由 + 发现 + 运行）
│   ├── api/server.py          # OpenAI 兼容 HTTP API
│   ├── cli/commands.py        # CLI 入口
│   ├── utils/                 # 工具库（19 文件）
│   │   ├── gitstore.py        # Git-backed 记忆版本控制（dulwich + line ages）
│   │   ├── helpers.py         # strip_think, IncrementalThinkExtractor, atomic write, token 估算
│   │   ├── prompt_templates.py   # Jinja2 模板渲染
│   │   ├── document.py          # PDF/DOCX/XLSX/PPTX 文档解析
│   │   ├── file_edit_events.py  # 文件编辑事件载荷构建
│   │   ├── progress_events.py   # 工具事件 payload 构建
│   │   ├── media_decode.py      # 媒体解码
│   │   └── ...
│   └── nanobot.py             # Python SDK facade
├── webui/                     # React Vite SPA（内嵌 wheel）
├── bridge/                    # TypeScript 桥接服务（WhatsApp Baileys，跨运行时渠道）
├── docs/                      # 文档
└── tests/                     # 测试
```

### 核心数据流与双总线架构

nanobot 采用**双总线架构**，职责明确分离：

**总线 1 — `MessageBus`（bus/queue.py）**：用户消息投递
- `inbound: asyncio.Queue` — 渠道 → agent
- `outbound: asyncio.Queue` — agent → 渠道
- 简洁的异步队列，解耦渠道和 agent 核心

**总线 2 — `RuntimeEventBus`（bus/runtime_events.py）**：进程内状态通知
- `SessionTurnStarted` / `TurnRunStatusChanged` / `TurnCompleted` — turn 生命周期
- `GoalStateChanged` — 持续目标变更
- `RuntimeModelChanged` — 运行时模型切换
- 支持类型化订阅 `subscribe(handler, event_type=...)`
- `publish_nowait` 用于同步调用点
- 驱动 WebUI 状态更新（turn 指示器、goal 状态、模型预设切换）

```
外部渠道（Telegram/Discord/CLI/WebUI/API/...）
    │
    ▼ publish_inbound()
┌─────────────┐     ┌────────────────────┐
│  MessageBus  │     │  RuntimeEventBus    │
│  (消息投递)  │     │  (状态通知)          │
└─────┬───────┘     └────────────────────┘
      │ consume_inbound()
      ▼
┌─────────────┐
│  AgentLoop   │  状态机驱动（RESTORE→COMPACT→COMMAND→BUILD→RUN→SAVE→RESPOND→DONE）
└─────┬───────┘
      │
      ├─ ContextBuilder: 组装系统提示 + 历史 + 记忆 + skills + runtime context
      │
      ▼
┌─────────────┐
│ AgentRunner  │  LLM 对话循环：发送消息 → 接收 tool calls → 执行工具 → 流式响应
└─────┬───────┘
      │
      ├─ ToolRegistry: 动态工具注册（builtin + MCP + plugin）
      ├─ SubagentManager: 后台子 agent（隔离工具集 + 独立 runner）
      ├─ Consolidator: token 预算压缩
      ├─ AgentProgressHook: runner 事件 → 渠道进度（thinking/tool hints/file edits）
      │
      ▼ publish_outbound()
┌─────────────┐
│  MessageBus  │
└─────┬───────┘
      │
      ▼
ChannelManager → delta 合并 + 重复抑制 → 外部渠道 send()
```

### Agent Loop 状态机设计（关键创新）

`AgentLoop` 使用**事件驱动状态机**处理每条消息：

```python
_TRANSITIONS = {
    (RESTORE, "ok"): COMPACT,      # 恢复 checkpoint（如被 /stop 中断后）
    (COMPACT, "ok"): COMMAND,      # AutoCompact / Consolidator 压缩
    (COMMAND, "dispatch"): BUILD,  # Slash 命令分发
    (COMMAND, "shortcut"): DONE,   # 优先命令快速完成
    (BUILD, "ok"): RUN,            # 构建上下文（system prompt + history + memory）
    (RUN, "ok"): SAVE,             # 运行 LLM + 工具执行
    (SAVE, "ok"): RESPOND,         # 持久化会话
    (RESPOND, "ok"): DONE,         # 发送响应
}
```

**关键机制**：
1. **Per-session 串行，跨 session 并发**：每个 session_key 一把 asyncio.Lock，全局有并发信号量（默认 3）
2. **Mid-turn 注入**：活跃 session 的新消息通过 pending_queue 注入当前 turn，而非启动竞争任务；注入上限 3 条/turn、5 轮/cycle
3. **Checkpoint/Restore**：工具执行期间自动保存 checkpoint 到 session metadata，被 `/stop` 取消后可恢复部分上下文（tool results + assistant messages）
4. **统一 session 模式**：可选 `unified_session` 将所有渠道归并到 `unified:default` session
5. **Turn 延续**：当 sustained goal 活跃且 agent 达到 max_iterations 时，`turn_continuation` 透明注入续接消息（最多 12 轮），用户无感知

### AgentRunner — LLM 对话循环（最复杂的组件）

`AgentRunner.run()` 是一个多轮迭代循环，每次迭代：

1. **Context Governance**（关键管线）：
   ```
   messages → drop_orphan → backfill_missing → microcompact → apply_tool_result_budget → snip_history → drop_orphan → backfill_missing
   ```
2. **请求 LLM**：支持三种模式——streaming（原生流式 + thinking delta）、progress streaming（增量 think 提取 + 进度回调）、非流式
3. **工具执行**：支持并发工具执行（`asyncio.gather`），含 SSRF 防护、workspace 违规检测、重复外部查找抑制
4. **Mid-turn 注入**：在工具执行后和最终响应后，检查 pending queue 注入新消息
5. **Sustained goal 继续**：如果活跃 goal 存在且无其他注入，注入 continue prompt 保持 agent 运转
6. **流式内容保护**：已流出的内容不重试（`should_retry_guard`）

**Context Governance 管线详解**：
- `_drop_orphan_tool_results`: 移除无对应 assistant tool_call 的孤立项
- `_backfill_missing_tool_results`: 为缺失结果的 tool_call 填充 `[Tool result unavailable]` 占位
- `_microcompact`: 将可压缩工具（read_file, exec, grep, web_search 等 7 种）的旧结果缩写为 `[previously returned X chars]`
- `_apply_tool_result_budget`: 超过 `max_tool_result_chars` 的工具结果持久化到文件，agent 收到文件路径引用 + 头尾摘要
- `_snip_history`: 当消息超过 token 预算时，从最早处截断到合法的 user-turn 边界

### 记忆系统 — Dream 两阶段记忆

nanobot 的记忆系统分三层：

1. **Session History**（短期）：`SessionManager` 管理每会话消息历史，JSONL 持久化
2. **MEMORY.md**（长期）：通过 Dream 自动写入的长期事实
3. **history.jsonl**（归档）：被 Consolidator 压缩的历史摘要

**Dream 机制**（两阶段记忆整合）：
- **Phase 1**（Consolidator）：当 session 的 prompt token 接近上下文窗口时，自动归档旧消息到 history.jsonl
  - 使用 LLM 摘要（`consolidator_archive.md` 模板），失败时 fallback 为 raw_archive
  - 通过 `consolidation_ratio`（默认 0.5）控制压缩目标
  - 支持 replay-window overflow 压缩（超出最大回放消息数的部分）
  - 最多 5 轮压缩/次调用，每轮基于 token 估算决定压缩边界
  - 摘要文本通过 tiktoken 截断以适配 consolidation LLM 的输入 token 预算
- **Phase 2**（Dream cron job）：定期（默认每 2 小时）以 LLM agent 形式运行
  - 阅读 history.jsonl 中 cursor 之后的未处理条目（最多 20 条/批）
  - 更新 MEMORY.md、SOUL.md、USER.md 和自定义 skills
  - 拥有独立的受限工具集（read_file + edit_file + apply_patch + write_file，仅限 memory/skills 目录）
  - 使用 `GitStore`（dulwich）自动提交变更，支持 `line_ages()` 计算记忆新鲜度
  - Dream session 文件自动清理（保留最近 10 个）

**AutoCompact**：
- TTL 驱动的空闲会话压缩（`session_ttl_minutes` 配置，0 = 禁用）
- 检查时机：agent loop 空转周期（`bus.consume_inbound` timeout）
- 跳过条件：`dream:` 前缀的内部 session、已归档中的 session、活跃 session
- 归档策略：保留最近 8 条消息（`_RECENT_SUFFIX_MESSAGES`），归档其余部分
- **双路径摘要恢复**：热路径（进程内 `_summaries` dict）和冷路径（session metadata `_last_summary`，进程重启后恢复）
- 摘要注入到 Runtime Context，确保 agent 在压缩后仍能看到前文要点

### 持续目标（Sustained Goals）机制

Sustained goals 是 nanobot 的关键差异化功能，允许 agent 跨多个 turn 持续追踪目标：

1. **启动**：`/goal` 命令或 `long_task` 工具设置 `metadata[GOAL_STATE_KEY]`，包含 `status/objective/ui_summary/started_at`
2. **运行时注入**：每次 turn 开始时，`goal_state_runtime_lines()` 将活跃 goal 注入 Runtime Context（即使历史被压缩也可见）
3. **LLM 超时豁免**：`runner_wall_llm_timeout_s()` 对活跃 goal 返回 0.0（无超时），依赖 streaming idle timeout 保护
4. **Turn 延续**：当 `stop_reason="max_iterations"` 且 goal 活跃时，`turn_continuation` 透明注入续接消息（最多 12 轮，每轮携带 goal objective）
5. **完成**：`complete_goal` 标记 `status="completed"`，附带 recap 文本
6. **事件通知**：每次 goal 状态变更发布 `GoalStateChanged` 到 RuntimeEventBus

### 工具体系

**发现机制**：
- **内置工具**：`pkgutil.iter_modules` 扫描 `nanobot/agent/tools/` 包
- **外部插件**：第三方包通过 `entry_points(group="nanobot.tools")` 声明（内置工具使用 pkgutil 扫描，第三方在自己的 pyproject.toml 声明 entry points）
- **MCP 工具**：运行时连接 MCP 服务器（stdio/SSE/streamableHttp 三种传输），三种能力类型（`MCPToolWrapper`/`MCPResourceWrapper`/`MCPPromptWrapper`），均注册为 `mcp_*` 前缀工具；Resource/Prompt wrapper 标记 `read_only=True`；支持运行时 hot reload（通过 RuntimeEventBus 控制消息 + ack future）；含 session 断线自动重连、nullable JSON Schema 规范化、Windows stdio 命令适配
- **作用域控制**：工具通过 `_scopes` 属性标记可用范围（`core` / `subagent`），subagent 只加载受限工具集

**工具基础设施**：
- **自定义 Schema 类型系统**（`schema.py`）：`StringSchema/IntegerSchema/ArraySchema/ObjectSchema` 等，避免 Pydantic 开销；`@tool_parameters` 装饰器提供声明式 DSL
- **contextvars 绑定**（`tools/context.py`）：`_CURRENT_REQUEST_CONTEXT` 绑定 channel/chat_id/session_key，工具通过 `current_request_session_key()` 透明访问
- **FileState 追踪**（`tools/file_state.py`）：per-session 文件读写状态（mtime + content hash），支持读前编辑警告和读去重
- **workspace scope 绑定**（`security/workspace_access.py`）：`_CURRENT_WORKSPACE_SCOPE` contextvar，per-turn 绑定项目路径和沙箱状态

**核心工具清单**（24 模块，其中 ~15 用户面工具 + 基础设施模块）：

| 类别 | 工具 | 描述 |
|------|------|------|
| 文件 | read_file, write_file, edit_file, apply_patch, list_dir | 文件系统操作 |
| 执行 | exec (shell), exec_session | Shell 执行（含 bwrap 沙箱后端）；exec_session 支持长时间命令 yield/poll/write_stdin |
| 搜索 | grep, find_files | 代码搜索 |
| Web | web_search, web_fetch | 网络搜索/抓取 |
| Agent | spawn (子agent), message (用户交互) | Agent 能力 |
| 系统 | cron (定时任务), long_task (持续目标) | 自动化 |
| 图像 | image_generation | 图片生成（多 provider） |
| MCP | mcp_* (动态) | MCP 协议工具 |
| 自修改 | my (agent 自我修改) | 运行时状态、预设切换 |
| CLI Apps | cli_apps | @appname 路由到已安装 CLI 工具 |

### Channel 体系 — 18 渠道全平台覆盖

**BaseChannel** 接口设计：
- `start()` / `stop()` / `send(msg)` — 基本生命周期
- `send_delta()` / `send_reasoning_delta()` / `send_reasoning_end()` — 流式输出 + 推理流
- `send_file_edit_events()` — 文件编辑实时事件
- `_handle_message()` — 权限检查 + 配对码生成 + 消息转发

**ChannelManager 优化**：
- **流式 delta 合并**（`_coalesce_stream_deltas`）：LLM 产出速度超过渠道处理速度时，合并同一 (channel, chat_id) 的连续 `_stream_delta` 消息，减少 API 调用
- **重复抑制**（`_should_suppress_outbound`）：SHA1 内容指纹 + `origin_message_id` 范围化，防止流式/非流式双路径发送相同响应
- **投递重试**：`send_max_retries`（默认 3 次）配置化

**权限模型**（三级）：
1. `allow_from: ["*"]` — 全开放
2. `allow_from: ["user_id_1", ...]` — 白名单
3. **Pairing Code**（`pairing/store.py`）：未授权用户 DM 时生成 8 位配对码（ABCD-EFGH），TTL 10 分钟，需管理员通过 `/pairing approve` 批准；thread-safe (`threading.Lock`)，atomic write 持久化

**已支持渠道**（18 个 = 16 适配器 + CLI + API）：
Telegram, Discord, Slack, Feishu (飞书), WeChat (微信), WeCom (企微), DingTalk (钉钉), QQ, Matrix, WhatsApp (bridge), Signal, Email, MS Teams, MoChat, WebSocket (WebUI), NapCat, CLI, API

### Provider 体系

**LLMProvider** 基类设计：
- `chat()` — 抽象方法，基础对话
- `chat_stream()` — 默认 fallback 到 chat()，Provider 可覆盖实现原生流式（含 `on_thinking_delta` 和 `on_tool_call_delta`）
- `chat_with_retry()` / `chat_stream_with_retry()` — 带重试的封装
- `_run_with_retry()` — 通用重试引擎

**重试策略**：
- 两种模式：`standard`（3 次后退）和 `persistent`（无限重试，相同错误 10 次后停止）
- 智能错误分类：区分 transient（429 rate_limit_exceeded / 5xx / timeout）和 non-transient（insufficient_quota / billing）
- 429 精细化：区分可重试（rate_limit）和不可重试（quota）语义 token
- Retry-After 多源解析：HTTP header（retry-after-ms / retry-after）+ 响应体文本提取 + 多种时间单位
- 图片 fallback：non-transient 错误时自动移除图片重试，成功后永久 strip
- 流式内容保护：已流出的内容不重试（`should_retry_guard`）

**FallbackProvider**（熔断器模式）：
- `_PRIMARY_FAILURE_THRESHOLD=3` 次连续失败后切换到 fallback
- `_PRIMARY_COOLDOWN_S=60s` 冷却后自动恢复主 provider
- 流式感知 failover：已流出内容时跳过 fallback
- 支持多级 fallback 链（`fallback_models` 配置多个备选预设）
- 错误分类：`_FALLBACK_ERROR_KINDS`（timeout/connection/server_error/rate_limit/overloaded）vs `_NON_FALLBACK_ERROR_KINDS`（authentication/auth/permission/content_filter/refusal/context_length/invalid_request）

**Provider 注册表**（`registry.py` — 单一事实来源）：
- `PROVIDERS` 元组含 37 个 `ProviderSpec` 条目
- 每个 `ProviderSpec` 声明：name, keywords, env_key, backend, detection strategy（API key prefix / base URL keyword）, thinking_style, default_model
- 覆盖：OpenAI, Anthropic, Azure, Bedrock, GitHub Copilot, OpenAI Codex, OpenRouter, DeepSeek, Gemini, Zhipu, Qwen/DashScope, Moonshot/Kimi, MiniMax, Mistral, StepFun, Xiaomi MiMo, VolcEngine, vLLM, Ollama, LM Studio, Atomic Chat, LongCat, Hugging Face, Novita, Skywork, Ant Ling, NVIDIA NIM, Groq, BytePlus, SiliconFlow, AiHubMix, OpenVINO, Qianfan 等

**ProviderSnapshot**（变更检测）：
- frozen dataclass 含 provider/model/context_window_tokens + signature tuple
- signature 包含所有影响 provider chain 的配置字段（model, api_key, base_url, temperature, max_tokens, fallback signatures 等）
- runner 在每 turn 开始时比较 signature，仅在变更时重建 provider

### Session 管理

**Session** 数据结构：
- `key`: `channel:chat_id` 格式
- `messages`: 完整消息列表（含 tool calls / tool results）
- `last_consolidated`: 已归档的消息游标
- `metadata`: 会话元数据（title, goal_state, _last_summary, workspace_scope 等）

**持久化**：
- JSONL 格式，每文件最多 2000 条消息
- Atomic write（tmp + fsync + os.replace）防止崩溃损坏
- 支持 session TTL 自动过期清理
- 会话预览文本生成（_text_preview，含 subagent announce 清理）

**Context Replay**：
- `get_history()` 支持 `max_messages`、`max_tokens`、`include_timestamps` 参数
- Token budget 从 context_window 推导：`context_window - max_output - 1024`
- 保留最近的合法消息后缀（`retain_recent_legal_suffix`）

### 安全体系

1. **Workspace 沙箱**：`restrict_to_workspace` 限制文件操作范围；contextvars 绑定 `WorkspaceScope`（含 project_path + access_mode + sandbox_status），per-turn 绑定
2. **SSRF 防护**：`contains_internal_url()` 检测 private/internal URL；分层 loopback 策略：仅 WebSocket full-access + unrestricted workspace 允许 loopback（开发场景），CLI/API 渠道一律禁止
3. **Shell 安全**：允许列表 + bwrap 沙箱后端（bind-mount workspace rw，media ro，父目录 tmpfs 隐藏 config.json）
4. **配对码**：DM 场景下的用户认证，8 位码 + TTL + atomic write
5. **文件系统隔离**：工具通过 `allowed_dir` + `extra_allowed_dirs` 精确控制
6. **Workspace 分层**：`WorkspaceScopeResolver` 从消息/会话 metadata 解析 scope，WebUI 可按请求发送不同 workspace

### Slash 命令系统

- **三层分发**：priority（`/stop` `/restart`，在 session lock 前处理保证响应性）→ exact match → prefix longest-first
- **~20 内建命令**：`/new` `/stop` `/restart` `/status` `/clear` `/model` `/compact` `/goal` `/skills` `/pairing` `/history` `/diff` 等
- **BuiltinCommandSpec**：结构化命令元数据，支持 UI 命令面板自动补全
- **命令可插拔**：通过 `CommandRouter.register()` 扩展

### Prompt 模板系统

`templates/` 目录是 prompt engineering 的核心：
- **工作区种子文件**：`SOUL.md`（agent 人格）、`USER.md`（用户画像）、`AGENTS.md`（工作区指令）、`HEARTBEAT.md`（定期任务）、`memory/MEMORY.md`（长期记忆模板）
- **agent 系统提示**（`templates/agent/`）：`identity.md`、`platform_policy.md`、`tool_contract.md`、`consolidator_archive.md`、`dream.md`、`evaluator.md`、`skills_section.md`、`subagent_system.md`、`subagent_announce.md`、`max_iterations_message.md`
- **渲染**：`prompt_templates.py` 使用 Jinja2 模板引擎

### 配置系统

- **Pydantic** schema（518 行），支持 camelCase JSON 兼容（`AliasChoices`）
- 配置文件：`~/.nanobot/config.json`
- 主要配置块：`providers`, `agents`, `channels`, `tools`, `dream`
- **Model Presets**：命名模型预设，`model_presets.py` 实现 `ProviderSnapshot` 切换，支持运行时热切换 + `RuntimeModelChanged` 事件通知
- **Fallback Models**：`fallback_models` 配置备选模型链，`FallbackProvider` 熔断器封装

### Skill 体系

- 内置 skills 以 `SKILL.md` 形式定义，加载到 agent context
- 外部 skill 通过 ClawHub 市场搜索安装
- `SkillsLoader` 负责发现和加载，支持 `always` 模式（始终注入上下文）和按需模式（仅摘要 + 按需加载）
- 支持 `disabled_skills` 配置排除特定 skill
- `skill-creator` skill 提供创建新 skill 的向导

### WebUI

- **内嵌 wheel**：构建输出打包进 Python wheel，无需额外构建步骤
- Vite + React SPA，WebSocket 连接 gateway（多 chat 多路复用）
- 功能：聊天界面、设置管理、模型切换（preset badge）、项目工作区、流式文件编辑活动展示、i18n locale 切换、LAN 访问
- Dev 模式：Vite dev server 代理 /api /webui /auth /WebSocket 到 gateway

### API 兼容

- OpenAI 兼容 HTTP API：`/v1/chat/completions`、`/v1/models`
- SSE 流式支持
- 固定 session key，程序化访问

### 关键设计模式

**contextvars 请求作用域绑定**：
nanobot 广泛使用 `contextvars.ContextVar` 避免显式传递上下文对象：
- `_CURRENT_REQUEST_CONTEXT` — per-request 路由上下文（channel, chat_id, session_key）
- `_current_file_states` — per-session 文件读写追踪
- `_CURRENT_WORKSPACE_SCOPE` — per-turn workspace scope + sandbox status

工具通过 `current_request_session_key()`、`current_workspace_scope()`、`current_file_states()` 透明访问，无需穿透整个调用栈。

**CompositeHook 错误隔离**：
`CompositeHook` 将生命周期事件扇出到多个 hook，每个 hook 独立 try/except，一个 faulty custom hook 不会 crash agent loop。仅 `finalize_content` 是无隔离的 pipeline（bug 应暴露）。

**Atomic Write 模式**：
Session、pairing store、history.jsonl 都使用同一模式：write to tmp → fsync → os.replace()。防止崩溃时的数据损坏。

**ProviderSnapshot 签名变更检测**：
frozen dataclass 含所有影响 provider chain 的配置字段 tuple，cheap 比较，仅在变更时重建 provider。

**IncrementalThinkExtractor**：
维护 "already emitted" 游标，增量 `<think ...</think` 块流式传输时逐块呈现 reasoning，不重复之前已发送的文本。对实时 thinking 显示至关重要。

**GitStore 记忆版本控制**：
使用 dulwich（纯 Python Git）对记忆文件进行版本控制：`auto_commit()` 追踪变更、`line_ages()` 计算行龄辅助 Dream 刷新、`revert()` 支持回滚。

## Key Findings

### 1. 极简核心 + 高内聚设计

nanobot 的核心 agent loop（loop.py + runner.py）约 2400 行 Python，却实现了完整的：
- 状态机驱动的消息处理
- 多轮 LLM 对话循环（含 context governance）
- Mid-turn 消息注入 + Turn 延续
- Checkpoint/Restore
- 并发工具执行
- Sustained goals（持续目标）
- 流式 + thinking delta + file edit events

**设计哲学**：将 LLM 编排保持极小可读，所有扩展通过工具、渠道、Provider、Hook 外挂。

### 2. 双总线架构（§核心数据流与双总线架构）

MessageBus（消息投递）+ RuntimeEventBus（状态通知）职责分离，独立演进。

### 3. Context Governance 管线（§AgentRunner）

管线式 context 治理确保 token 预算内结构合法，易于增加新的治理步骤。

### 4. Dream 两阶段记忆（创新点）

将记忆整合分为两个阶段：
1. **被动压缩**（Consolidator）：token 预算驱动的自动归档
2. **主动反思**（Dream cron）：定期 agent 运行，阅读归档并更新长期记忆

GitStore line_ages 让 Dream 能评估哪些记忆条目过时需要刷新——这比简单的摘要压缩更接近人类的记忆整理过程。

### 5. Mid-turn 注入 + Turn 延续

两个互补机制处理长时间运行的 agent：
- **Mid-turn 注入**：新消息即时进入活跃 turn（子 agent 结果、用户追问）
- **Turn 延续**：max_iterations 后透明续接（最多 12 轮），sustained goal 无感知延续

### 6. contextvars 请求作用域模式

通过 contextvars 绑定 request context / file state / workspace scope，工具透明访问而无需穿透调用栈。这是 async Python agent 系统的关键模式。

### 7. Plugin 发现机制

双通道工具发现：
1. `pkgutil.iter_modules` — 零配置内置发现
2. `importlib.metadata.entry_points` — 标准化外部插件

**Nop 借鉴点**：类似模式可用于 Nop 的 XDSL 工具发现。

### 8. Provider 精细化可靠性

重试策略的细致程度是生产级 agent 的标杆：
- 区分 standard/persistent 模式
- 429 语义分类（rate_limit vs quota）
- 熔断器 fallback（3 次失败切换 + 60s 冷却恢复）
- 流式内容保护
- ProviderSnapshot 签名变更检测

## Architecture Comparison: nanobot vs Nop Agent

| 维度 | nanobot | Nop Agent（预期） |
|------|---------|-------------------|
| **语言** | Python 3.11+ (asyncio) | Java 21 (Nop 框架) |
| **核心循环** | AgentLoop 状态机 + AgentRunner 迭代 | 可参考状态机模式 |
| **消息总线** | asyncio.Queue（进程内）+ RuntimeEventBus | 可扩展为分布式 |
| **LLM 调用** | 原生 SDK（openai + anthropic） | 需自行适配或使用 langchain4j |
| **记忆** | Dream 两阶段 + JSONL + GitStore | 可用关系型 DB + 向量存储 |
| **工具发现** | pkgutil + entry_points | 可用 XDSL + IoC |
| **渠道** | 18 渠道 | 可通过 WebHook + 消息队列 |
| **配置** | Pydantic + JSON | Nop IoC + XDSL |
| **持久化** | 文件系统（JSONL + MD + Git） | 关系型 DB |
| **扩展模型** | Plugin (entry_points) + MCP | Delta 定制 + XDSL |
| **上下文管理** | contextvars 请求绑定 | 可用 ThreadLocal / ScopableBean |
| **可靠性** | 熔断器 + 精细化重试 + atomic write | Nop IoC 已有类似能力 |
| **安全** | workspace sandbox + SSRF + bwrap + pairing code | 需设计 |
| **热重载** | MCP hot reload + model preset 运行时切换 | Nop Delta 热加载 |

## Takeaways for Nop

1. **状态机 Agent Loop** 可作为 Nop Agent 核心的参考模式，TurnState 状态机比 if-else 更清晰可维护
2. **Context Governance 管线** 是生产级 agent 的必备组件，microcompact + snip_history + tool result offloading 模式值得移植
3. **Mid-turn 注入 + Turn 延续** 是长时运行 agent 的关键能力，需要消息队列层面的支持
4. **双阶段记忆** 模式（被动压缩 + 主动反思 + GitStore line ages）比纯摘要更有长期价值
5. **Plugin 发现** 的双通道模式（内置扫描 + entry_points）与 Nop 的 XDSL + IoC 发现机制高度互补
6. **Provider 精细化可靠性**（熔断器 + 429 语义分类 + 流式保护 + signature 变更检测）是生产可靠性的关键
7. **Skill 体系** 的 markdown 定义模式简单有效，可作为 Nop Agent skill 定义的参考
8. **contextvars 模式** 对应 Java 的 ThreadLocal / ScopableBean，可在 Nop 中复用已有模式
9. **Prompt 模板系统**（Jinja2 + agent/ 子目录）的模板化 prompt 管理方式值得参考
10. **Sustained Goals** 的生命周期（启动→运行时注入→超时豁免→turn 延续→完成→事件通知）是复杂 agent 工作流的设计范本
