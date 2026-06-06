# Hermes Agent 技术分析

> Status: open
> Date: 2026-06-05
> Scope: ~/ai/hermes-agent — 自改进 AI Agent (Nous Research)
> Conclusion:

## Context

- Hermes Agent 是 Nous Research 开发的"自改进 AI Agent"——唯一内置学习循环的 Agent
- 2026-05-29 最新提交，版本 0.15.1，MIT 许可
- 核心理念：从经验创建技能、使用中改进技能、跨会话持久化用户画像
- 调研目的：理解"自改进"Agent 的架构设计，对比其他 Agent OS 方案

## Analysis

### 项目定位

- **组织**: Nous Research
- **许可**: MIT
- **版本**: 0.15.1
- **语言**: Python 3.11+ (后端) + TypeScript/Ink (TUI)
- **LOC**: ~1,960,664 行 Python + ~66,048 行 TS/TSX (TUI) ≈ **2,027K 行**（含测试）
- **核心文件**: run_agent.py (5,115 行), cli.py (16,065 行), gateway/run.py (19,911 行)
- **Commit**: af8b917 (2026-05-29)
- **GitHub**: https://github.com/NousResearch/hermes-agent
- **定位**: 全能型 AI Agent——不仅编码，更是一个可从任何平台访问的自主助手

### 顶层架构

```
hermes-agent/
├── run_agent.py              # AIAgent 核心循环 (~4.6K 行)
├── cli.py                    # HermesCLI 交互式 CLI (~15.3K 行)
├── model_tools.py            # 工具编排 & 调度
├── toolsets.py               # 工具集定义
├── hermes_state.py           # SQLite 会话存储 (FTS5)
├── agent/                    # Agent 内部 (~69K 行)
│   ├── memory_provider.py    # 记忆 Provider ABC
│   ├── memory_manager.py     # 记忆管理器
│   ├── context_engine.py     # 上下文引擎
│   ├── curator.py            # 技能生命周期管理
│   ├── image_gen_provider.py # 图像生成 Provider
│   └── auxiliary_client.py   # 辅助 LLM 调用
├── tools/                    # 工具实现 (~82 个 .py 文件)
├── gateway/                  # 消息网关 (~20K 行)
│   ├── run.py                # 网关主循环 (~19K 行)
│   └── platforms/            # 30 个平台适配器
├── plugins/                  # 插件系统 (72 个 plugin.yaml)
│   ├── memory/               # 9 个记忆 Provider
│   ├── model-providers/      # 29 个模型 Provider
│   ├── context_engine/       # 上下文引擎插件
│   ├── image_gen/            # 图像生成插件
│   └── kanban/               # 多 Agent 看板
├── skills/                   # 内置技能 (90 个)
├── optional-skills/          # 可选技能 (86 个)
├── ui-tui/                   # Ink (React) TUI (~66K 行)
├── tui_gateway/              # TUI JSON-RPC 后端
├── cron/                     # 定时调度器
├── hermes_cli/               # CLI 子命令 & 配置
├── website/                  # Docusaurus 文档站
└── tests/                    # ~1,409 个测试文件
```

### 核心模块详解

#### 1. AIAgent 核心循环 (run_agent.py, 5,115 行)

**同步 Agent Loop**——完全同步设计，无 async/await:

```python
while (api_call_count < max_iterations and iteration_budget.remaining > 0) \
        or _budget_grace_call:
    if _interrupt_requested: break
    response = client.chat.completions.create(model=model, messages=messages, tools=tool_schemas)
    if response.tool_calls:
        for tool_call in response.tool_calls:
            result = handle_function_call(tool_call.name, tool_call.args, task_id)
            messages.append(tool_result_message(result))
        api_call_count += 1
    else:
        return response.content
```

**AIAgent.__init__** 接受 ~60 个参数，包括凭据池、路由、回调、预算、检查点等。

**关键特性**:
- **API 模式**: `chat_completions` / `codex_responses` 多种调用方式
- **预算追踪**: `iteration_budget` + `_budget_grace_call`（最后一次宽限调用）
- **中断检查**: `_interrupt_requested` 支持随时中断
- **推理内容**: 存储在 `assistant_msg["reasoning"]`
- **检查点**: 支持会话状态保存/恢复

#### 2. 工具系统 (tools/, 82 个文件)

**自动发现**: 任何 `tools/*.py` 文件中的顶层 `registry.register()` 调用自动被导入。

**工具集 (Toolsets)**: 43 种工具集（含 composite）:
browser, clarify, code_execution, computer_use, context_engine, cronjob, debugging, delegation, discord, discord_admin, feishu_doc, feishu_drive, file, homeassistant, image_gen, kanban, memory, messaging, moa, safe, search, session_search, skills, spotify, terminal, todo, tts, video, video_gen, vision, web, x_search, yuanbao 等

**关键工具**:
| 工具 | 用途 |
|------|------|
| `terminal` | Shell 执行（6 种后端: local/Docker/SSH/Modal/Daytona/Singularity） |
| `delegate_task` | 子 Agent 委托（单任务/并行批量） |
| `browser_*` | 浏览器自动化（CDP + CamoFox） |
| `file_operations` | 文件读写编辑 |
| `web_*` | 搜索/提取网页 |
| `vision_*` | 图像/视觉分析 |
| `cronjob` | 定时任务管理 |
| `clarify` | 向用户提问 |
| `memory` | 记忆管理 |
| `code_execution` | 代码沙箱执行 |

**工具注册模式**:
```python
registry.register(
    name="example_tool",
    toolset="example",
    schema={...},
    handler=lambda args, **kw: example_tool(...),
    check_fn=check_requirements,
    requires_env=["EXAMPLE_API_KEY"],
)
```

#### 3. 委托系统 (delegate_tool)

**两种形态**:
- **Single**: 传入 `goal` (+ 可选 `context`, `toolsets`)
- **Batch (parallel)**: 传入 `tasks: [...]`，每个任务获得独立子 Agent 并发执行

**角色**:
- `role="leaf"` (默认): 聚焦工作者，不能嵌套委托
- `role="orchestrator"`: 保留 `delegate_task`，可再嵌套 worker

**配置**: `max_concurrent_children`(默认 3), `max_spawn_depth`(默认 2), `child_timeout_seconds`

#### 4. 消息网关 (gateway/, ~20K 行)

**30 个平台适配器** (含辅助模块):
Telegram, Discord, Slack, WhatsApp, Signal, Matrix, Mattermost, Email, SMS, Home Assistant, 飞书 (3 模块), 钉钉, 企业微信 (2 模块), 微信, 元宝 (4 模块), BlueBubbles, Webhook, API Server, MS Graph Webhook 等

**网关架构**: 单进程网关服务，所有平台共享 `gateway/run.py` 主循环:
- 消息接收 → 斜杠命令解析 → Agent 调度 → 结果投递
- 双重消息守卫（base adapter + gateway runner）
- 平台适配器通过 `acquire_scoped_lock()` 防止多 profile 共用凭证

**语音支持**: 语音备忘录转录 + TTS 回复

#### 5. 记忆系统 (agent/memory_provider.py + plugins/memory/)

**9 个记忆 Provider 插件**:
honcho, mem0, supermemory, byterover, hindsight, holographic, openviking, retaindb + 内置 SQLite

**MemoryProvider ABC**:
- `sync_turn(turn_messages)`: 同步对话轮次
- `prefetch(query)`: 预取相关记忆
- `shutdown()`: 优雅关闭
- `post_setup(hermes_home, config)`: 安装向导集成

**关键特性**:
- **FTS5 会话搜索**: SQLite 全文搜索跨会话对话历史
- **LLM 摘要**: 搜索结果经 LLM 摘要后注入上下文
- **Honcho 用户建模**: 辩证式用户画像构建
- **跨会话持久化**: 用户偏好、工作习惯跨会话保留

**自改进循环**:
1. Agent 在复杂任务后自主创建技能
2. 技能在使用中自我改进（curator 系统）
3. 周期性提醒 Agent 持久化知识
4. FTS5 搜索自身历史对话

#### 6. 技能系统 (skills/ + optional-skills/)

**74 个内置技能** + **95 个可选技能**，共 **169 个技能包**

**技能类别**: github, mlops, devops, blockchain, creative, email, health, mcp, research, security, web-development 等

**SKILL.md 规范**: YAML frontmatter (name, description, version, author, platforms) + Markdown body

**Curator (技能生命周期)**:
- 追踪使用统计 (use_count, view_count, patch_count)
- 自动归档过期技能（不删除，可恢复）
- LLM 审查循环：自动审查并改进技能质量
- 固定 (pin) 机制保护重要技能

**Skills Hub**: [agentskills.io](https://agentskills.io) 开放标准社区

#### 7. 模型 Provider 插件 (plugins/model-providers/)

**28 个模型 Provider 插件**:
openrouter, anthropic, openai-codex, deepseek, gemini, bedrock, azure-foundry, nvidia, ollama-cloud, huggingface, xai, kimi-coding, minimax, novita, qwen-oauth, xiaomi, alibaba, alibaba-coding-plan, arcee, copilot, copilot-acp, custom, gmi, kilocode, nous, stepfun, zai 等

**延迟安装策略**: Provider 特定依赖仅在用户选择时按需安装 (`tools/lazy_deps.py`)

**Provider 插件协议**: `providers.register_provider(ProviderProfile(...))`，用户插件可覆盖内置

#### 8. Cron 调度器 (cron/)

**支持格式**:
- 持续时间: `"30m"`, `"2h"`, `"1d"`
- "every" 表达式: `"every 2h"`, `"every monday 9am"`
- 5 字段 cron: `"0 9 * * *"`
- ISO 时间戳 (一次性): `"2026-06-01T09:00:00Z"`

**硬性保障**: 3 分钟硬中断、追赶窗口、文件锁防重复 tick

#### 9. Kanban 看板 (多 Agent 协作)

**SQLite 支持的持久化看板**:
- 多 profile / worker 协作共享任务
- Dispatcher 每 60s 轮询：回收过期任务、提升就绪任务、原子分配
- 隔离模型: Board (硬边界) → Tenant (软命名空间)
- 连续失败自动阻塞 (默认 2 次)

#### 10. TUI (ui-tui/, ~66K 行)

**双进程架构**: Node (Ink/React) ← stdio JSON-RPC → Python (tui_gateway)

TypeScript 拥有屏幕渲染，Python 拥有会话/工具/模型调用。

**Dashboard**: `hermes dashboard` 嵌入真实 TUI（通过 PTY bridge），不做二次实现。

#### 11. Profile 多实例

**完全隔离的多实例**: 每个 profile 有独立 HERMES_HOME（配置/API key/记忆/会话/技能/网关）。

所有路径必须通过 `get_hermes_home()` 获取，禁止硬编码 `~/.hermes`。

#### 12. 插件系统 (plugins/, 72 个 plugin.yaml)

**三种插件面**:
1. **General plugins**: Python 回调生命周期钩子 + 工具注册 + CLI 子命令
2. **Memory-provider plugins**: 独立发现系统，MemoryProvider ABC
3. **Model-provider plugins**: 独立发现系统，ProviderProfile 注册

**生命周期钩子**: `pre_tool_call`, `post_tool_call`, `pre_llm_call`, `post_llm_call`, `on_session_start`, `on_session_end`

**硬性规则**: 插件不得修改核心文件。需要新能力时扩展通用插件面。

#### 13. 安全 & 供应链

**精确依赖锁定**: 核心依赖精确到 `==X.Y.Z`（无范围），新增 post-1.0 依赖用 `>=floor,<next_major`，Git URL 用 commit SHA。这是 2026-05-12 Mini Shai-Hulud 蠕虫攻击 mistralai 包后的安全强化。

**延迟安装策略**: Provider/后端特定依赖仅在选择时按需安装，缩小攻击面。

**依赖锁定策略**: PyPI 包 `>=floor,<next_major`，Git URL 用 commit SHA，CI 用 SHA + 注释。

### 技术亮点

1. **自改进循环**: 唯一内置学习循环的 Agent——从经验创建技能、使用中改进、跨会话持久化
2. **169 个技能包**: 最大规模的 Agent 技能库，含 74 内置 + 95 可选
3. **30 个平台适配器**: 覆盖几乎所有主流 IM/通信平台
4. **28 个模型 Provider**: 最广泛的 LLM 后端支持
5. **Curator 技能管理**: 自动化技能生命周期（归档/改进/审查）
6. **同步 Agent Loop**: 简单可靠的同步设计，~60 参数高度可配置
7. **Profile 多实例**: 完全隔离的多 Agent 实例，单一部署
8. **Kanban 多 Agent 协作**: SQLite 持久化看板 + Dispatcher 调度
9. **供应链安全**: 精确锁定 + 延迟安装 + Mistralai 蠕虫后的安全强化
10. **1,409 个测试文件**: 子进程隔离测试，CI 一致性保障
11. **Skills Hub 开放标准**: agentskills.io 社区生态
12. **Terminal 6 种后端**: local/Docker/SSH/Modal/Daytona/Singularity

### 劣势

1. **核心文件巨大**: cli.py (16,065 行), gateway/run.py (19,911 行) 单文件过大
2. **同步设计**: 无 async/await，并发受限于线程模型
3. **Python 依赖沉重**: 1.9M+ 行代码（含测试），安装体积大
4. **精确依赖锁定双刃剑**: 安全但升级频率高、维护成本大
5. **无结构化输出 schema**: Agent Loop 是纯文本/工具调用，无 VoltAgent 式 Workflow DSL
6. **无上下文压缩引擎**: 与 PilotDeck 的三级压缩不同，Hermes 依赖 prompt caching
7. **无 Guardrails 体系**: 无 input/output 拦截器
8. **测试文件 1,409 个但大量可能重复**: 需评估实际覆盖率
9. **Mistral 供应商被永久移除**: Mini Shai-Hulud 事件后的激进决策

### 竞品对比

| 维度 | Hermes Agent | PilotDeck | VoltAgent | Claude Code |
|------|-------------|-----------|-----------|-------------|
| **定位** | 自改进 Agent | Agent OS | Agent 平台 | Coding Agent |
| **许可** | MIT | AGPL-3.0 | MIT | 商业 |
| **语言** | Python | TypeScript | TypeScript | TypeScript |
| **核心理念** | 自改进/学习循环 | WorkSpace 隔离 | 单包核心 | IDE 深度集成 |
| **记忆系统** | 9 Provider 插件 | EdgeClaw 白盒 | 三适配器 | 黑盒压缩 |
| **自改进** | Curator + 技能自动创建 | 无 | 无 | 无 |
| **平台数** | 30 | 23 | HTTP only | CLI only |
| **模型 Provider** | 28 插件 | 2 协议 | 19 ai-sdk | Anthropic only |
| **技能数** | 169 | 6 | 0 | 内置 |
| **工具数** | 82+ | 19 | 依赖 AI SDK | ~20 |
| **LOC** | ~2,027K | ~142K | ~60K (core) | N/A |
| **测试** | 1,409 文件 | 3 文件 | 未知 | N/A |
| **Workflow** | 无 | 无 | 16 步骤 DSL | 无 |
| **多 Agent** | Kanban 看板 | Sub-Agent Fork | Supervisor | 无 |

### 与 Nop 平台的关联

#### 可借鉴 (高价值)

1. **Curator 技能生命周期**: 自改进的技能管理（归档/改进/审查/固定）可作为 Nop AI 模块技能管理的参考模型
2. **延迟依赖安装**: `lazy_deps.py` 模式可用于 Nop 的 AI Provider 按需加载，减少核心包体积
3. **Profile 多实例**: HERMES_HOME 隔离模式可映射到 Nop 的多租户场景
4. **Kanban 多 Agent 协作**: SQLite 持久化看板 + Dispatcher 调度可作为 Nop 多 Agent 场景的参考
5. **插件三种面**: General + Memory Provider + Model Provider 的分层插件系统可启发 Nop 扩展点设计
6. **供应链安全实践**: 精确锁定 + 延迟安装 + SHA pinning 策略值得 Nop 项目借鉴
7. **FTS5 会话搜索**: SQLite 全文搜索跨会话对话历史，轻量级方案适合 Nop

#### 可借鉴 (中等价值)

8. **29 个 Model Provider 插件**: Provider Profile 注册模式可用于 Nop 的 LLM Provider 抽象
9. **Toolset 分组**: 29 种工具集 + 平台级启停策略可参考
10. **Skills Hub 开放标准**: agentskills.io 的社区生态模式有参考价值

#### 不适用

- Python 技术栈不可移植到 Nop (Java)
- 同步 Agent Loop 与 Nop 的异步架构不匹配
- 1.9M+ 行代码量与 Nop 的模块化精简哲学冲突
- 纯工具调用模式（无 Workflow DSL）不如 VoltAgent/PilotDeck 的编排能力
- 供应链精确锁定策略对 Nop Maven 生态不完全适用

## Conclusion

Hermes Agent 是目前规模最大、功能最全的开源 Agent 项目之一（2.027M 行、30 平台、28 模型 Provider、169 技能），其核心差异化在于**自改进循环**（Curator + 技能自动创建 + 使用中改进）和**供应链安全实践**（精确锁定 + 延迟安装）。对 Nop 最有价值的借鉴：Curator 技能生命周期管理、延迟依赖安装模式、Profile 多实例隔离、Kanban 多 Agent 协作、以及分层插件系统（General + Memory + Model Provider）。但 Python 同步架构和缺乏 Workflow DSL 限制了其在 Java 企业场景的直接适用性。

## Open Questions

- [ ] Curator 的技能自动创建/改进模式如何在 Nop 的 biz 层实现？
- [ ] 延迟依赖安装模式 (lazy_deps.py) 是否可以映射到 Nop 的 IoC lazy-init？
- [ ] Kanban 多 Agent 协作的 SQLite 看板模式是否适用于 Nop 的工作流引擎？
- [ ] 29 个 Model Provider 的注册模式能否用 Nop 的 IoC + Bean 覆盖机制实现？

## References

- ~/ai/hermes-agent/README.md
- ~/ai/hermes-agent/AGENTS.md
- ~/ai/hermes-agent/pyproject.toml
- ~/ai/hermes-agent/run_agent.py (5,115 行)
- ~/ai/hermes-agent/cli.py (16,065 行)
- ~/ai/hermes-agent/gateway/run.py (19,911 行)
- ~/ai/hermes-agent/tools/ (82 个工具文件)
- ~/ai/hermes-agent/plugins/ (72 个插件)
- ~/ai/hermes-agent/skills/ (74 个技能) + optional-skills/ (95 个技能)
- https://github.com/NousResearch/hermes-agent
- https://hermes-agent.nousresearch.com/docs/
- https://agentskills.io
