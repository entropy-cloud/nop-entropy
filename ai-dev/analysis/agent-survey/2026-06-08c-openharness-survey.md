# OpenHarness 存储、Worktree 隔离与安全防护机制调研

> Status: done
> Date: 2026-06-08
> Scope: OpenHarness（HKUDS）的存储架构、Swarm 多 agent worktree 隔离、内存系统、会话持久化、**核心安全防护机制**（工作循环防护、上下文压缩、权限检查）
> Source: https://github.com/HKUDS/OpenHarness 本地克隆 `~/ai/OpenHarness`

---

## 一、OpenHarness 概览

OpenHarness 是港大 HKUDS 开源的 Agent 基础设施框架，核心特性：

| 维度 | 说明 |
|------|------|
| 语言 | Python ≥3.10 |
| 定位 | 轻量 Agent 基础设施（tool-use / skills / memory / multi-agent） |
| 上层产品 | **ohmo**——飞书/Slack/Telegram/Discord 的个人 AI Agent |
| Agent 后端 | Claude Code / Codex 订阅（不额外需要 API Key） |
| 多 agent | Swarm 模式：tmux/iTerm2 pane 可视化 + subprocess/in_process 后端 |
| 工具数 | 43+ |
| 测试 | 114 passing + 6 E2E suites |

---

## 二、存储架构

### 2.1 目录结构

```
~/.openharness/
├── memory/                            # 项目级持久化记忆
│   └── {project_name}-{hash12}/       # 项目路径 SHA1 前 12 位
│       └── MEMORY.md                   # 记忆入口 Markdown
├── worktrees/                          # Swarm agent 的 git worktree
│   └── {slug}/                         # 扁平化 slug（/ → +）
│       ├── .git                        # linked worktree 文件
│       ├── node_modules → /main/repo/node_modules  # symlink
│       └── ...                         # 完整工作目录副本
└── state/                              # 应用状态（内存中，非持久化）

.ohmo/                                  # ohmo 产品级存储（项目内）
├── sessions/                           # 会话快照
│   ├── latest.json                     # 最新会话快照
│   ├── latest-{hash12}.json            # 按 session_key 的最新快照
│   ├── session-{sid}.json              # 每会话一 JSON
│   └── transcript.md                   # Markdown 导出
└── workspace config
```

### 2.2 会话持久化（ohmo Session Storage）

ohmo 的 `session_storage.py` 实现了 JSON 文件持久化：

```python
# 会话快照结构
{
  "app": "ohmo",
  "session_id": "hex12",
  "session_key": "optional_group_key",
  "cwd": "/project/path",
  "model": "glm-5",
  "system_prompt": "...",
  "messages": [ConversationMessage.model_dump()],
  "usage": { input_tokens, output_tokens, ... },
  "tool_metadata": { ... },
  "created_at": timestamp,
  "summary": "用户第一条消息前80字",
  "message_count": N
}
```

**三级存储**：

| 文件 | 用途 |
|------|------|
| `latest.json` | 全局最新会话（覆盖写） |
| `latest-{hash12}.json` | 按 session_key 的最新会话（如飞书群组维度） |
| `session-{sid}.json` | 每会话独立文件（不覆盖） |

**工具元数据**：`tool_metadata` 保留工具调用的结构化元信息（非完整 input/output）。

### 2.3 记忆系统（Memory）

OpenHarness 的记忆系统基于 **Markdown 文件**：

| 组件 | 文件 | 说明 |
|------|------|------|
| `paths.py` | 项目级记忆路径 | `~/.openharness/memory/{name}-{hash12}/MEMORY.md` |
| `manager.py` | 增删改查 | `add_memory_entry` / `list_memory_files` / `remove_memory_entry` |
| `search.py` | 语义搜索 | `find_relevant_memories` |
| `relevance.py` | 相关性选择 | `select_relevant_memories` / `format_relevant_memories` |
| `usage.py` | 使用追踪 | `mark_memory_used` |
| `migrate.py` | 迁移 | `migrate_memory` |
| `schema.py` | Schema | 记忆文件格式 |
| `agent.py` | Agent 记忆 | agent 维度的记忆 |
| `team.py` | 团队记忆 | swarm 团队共享记忆 |

**设计特点**：
- 记忆以 Markdown 文件存储，可人工编辑
- `scan_memory_files` 扫描记忆目录，`search` 做语义检索
- 记忆按项目隔离（路径 hash）
- Agent 和 Team 维度的记忆独立管理

### 2.4 应用状态（State）

`AppStateStore` 是一个极简的**内存中可观察状态存储**：

```python
class AppStateStore:
    def __init__(self, initial_state: AppState): ...
    def get(self) -> AppState: ...           # 获取当前快照
    def set(self, **updates) -> AppState: ... # 更新 + 通知 listeners
    def subscribe(self, listener) -> unsubscribe: ...
```

**非持久化**——应用重启后状态丢失。这是 UI 状态（当前模式、显示配置等），不需要持久化。

---

## 三、Swarm 多 Agent 与 Worktree 隔离

### 3.1 Swarm 架构

OpenHarness 的 Swarm 模块是最完整的多 agent 实现之一：

```
swarm/
├── types.py              # 核心类型定义（TeammateIdentity, SpawnConfig, SpawnResult...）
├── registry.py           # Backend 注册表（subprocess / in_process / tmux / iterm2）
├── worktree.py           # Git worktree 隔离管理
├── subprocess_backend.py # 子进程后端
├── in_process.py         # 进程内后端
├── mailbox.py            # Agent 间消息邮箱
├── lockfile.py           # 文件锁
├── permission_sync.py    # 权限同步
├── spawn_utils.py        # Spawn 工具
└── team_lifecycle.py     # 团队生命周期管理
```

### 3.2 后端类型

| 后端 | 说明 | 可视化 | 隔离级别 |
|------|------|--------|---------|
| `subprocess` | 子进程启动独立 oh 进程 | 无 | 进程级 |
| `in_process` | 同进程内协程 | 无 | 协程级 |
| `tmux` | tmux pane 中启动 | tmux pane 边框 + 颜色 | 进程 + 文件系统级 |
| `iterm2` | iTerm2 原生 pane | iTerm2 原生 UI | 进程 + 文件系统级 |

**检测优先级**：`in_process fallback` → `tmux（在 tmux 内）` → `subprocess（兜底）`

### 3.3 Git Worktree 隔离（worktree.py）

**这是 OpenHarness 最独到的设计**——Swarm agent 自动获得独立的 git worktree。

#### WorktreeManager

```python
class WorktreeManager:
    base_dir: Path  # 默认 ~/.openharness/worktrees/

    async def create_worktree(repo_path, slug, branch=None, agent_id=None) -> WorktreeInfo
    async def remove_worktree(slug) -> bool
    async def list_worktrees() -> list[WorktreeInfo]
    async def cleanup_stale(active_agent_ids) -> list[str]
```

#### 创建流程

```
create_worktree(repo_path="/project", slug="researcher")
│
├── 1. 验证 slug（[a-zA-Z0-9._-]+，最长 64 字符，禁止 .. 和绝对路径）
├── 2. 生成 flat_slug（/ → +），如 "researcher" → "researcher"
├── 3. worktree_path = ~/.openharness/worktrees/researcher
├── 4. Fast Resume：如果目录已存在且是有效 git worktree，直接返回
├── 5. git worktree add -B worktree-researcher {worktree_path} HEAD
├── 6. symlink 常用大目录（node_modules, .venv, __pycache__, .tox）
└── 7. 返回 WorktreeInfo

WorktreeInfo:
  slug = "researcher"
  path = ~/.openharness/worktrees/researcher
  branch = "worktree-researcher"
  original_path = /project
  created_at = timestamp
  agent_id = "researcher@team1"
```

#### 关键设计

| 设计 | 说明 |
|------|------|
| **分支命名** | `worktree-{slug}`，`-B` 强制重置孤儿分支 |
| **Symlink 大目录** | `node_modules`/.venv 等从主仓库 symlink，避免重复 |
| **Fast Resume** | 如果 worktree 目录已存在且有效，直接复用 |
| **Slug 验证** | 防路径穿越，限长 64 字符，只允许 `[a-zA-Z0-9._-]` |
| **存储位置** | `~/.openharness/worktrees/`，独立于项目目录 |
| **清理** | `cleanup_stale(active_agent_ids)` 自动删除已不活跃 agent 的 worktree |

#### 删除流程

```
remove_worktree(slug)
│
├── 1. 删除 symlink（node_modules 等）
├── 2. 通过 git rev-parse --git-common-dir 找到主仓库
├── 3. git worktree remove --force {worktree_path}
└── 4. Fallback：从 base_dir 尝试删除
```

#### 与 TeammateSpawnConfig 的集成

```python
@dataclass
class TeammateSpawnConfig:
    name: str
    team: str
    prompt: str
    cwd: str
    parent_session_id: str
    model: str | None = None
    worktree_path: str | None = None   # ← worktree 隔离路径
    session_id: str | None = None
    permissions: list[str] = []
    task_type: Literal["local_agent", "remote_agent", "in_process_teammate"] = "local_agent"
    ...
```

每个 teammate 可以指定 `worktree_path` 获得独立的文件系统隔离。

### 3.4 与 opencode ProjectCopy 的对比

| 维度 | opencode ProjectCopy | OpenHarness WorktreeManager |
|------|---------------------|---------------------------|
| **触发方式** | HTTP API 手动调用 | Agent spawn 时自动创建 |
| **存储位置** | 用户指定目录 | `~/.openharness/worktrees/` |
| **分支策略** | `--detach HEAD`（无分支） | `-B worktree-{slug}`（创建分支） |
| **大目录处理** | 无 | symlink（node_modules 等） |
| **清理策略** | 手动 remove | `cleanup_stale` 自动清理 |
| **Slug 验证** | 无 | 严格验证（长度、字符、路径穿越） |
| **Fast Resume** | 无 | 有（复用已有 worktree） |
| **与 agent 关联** | 无（纯 worktree 管理） | `agent_id` 关联，支持按 agent 清理 |
| **数据库记录** | `project_directory` 表 | 无（文件系统状态） |

**核心区别**：opencode 的 ProjectCopy 是**用户手动管理工作副本**的工具；OpenHarness 的 WorktreeManager 是**agent 自动获得隔离工作目录**的基础设施。

---

## 四、消息邮箱（Mailbox）

Swarm agent 间通过 `mailbox.py` 实现消息传递：

- 每个 agent 有独立邮箱
- 支持 `send_message(agent_id, TeammateMessage)` 发送
- 消息包含 `text`, `from_agent`, `color`, `timestamp`, `summary`
- 进程间通过 stdin 注入消息

---

## 五、与已有框架的对比

### 5.1 存储层对比

| 维度 | OpenHarness | opencode v2 | Claude Code | cline |
|------|------------|-------------|-------------|-------|
| **会话存储** | JSON 文件（session-{sid}.json） | SQLite session_message | JSONL（transcripts + projects） | JSON 文件 |
| **记忆系统** | Markdown 文件 + 语义搜索 | 无 | 无 | 无 |
| **应用状态** | 内存 AppStateStore | SQLite event + projector | 无 | VSCode globalState |
| **多 agent 隔离** | git worktree（自动） | git worktree（手动 API） | 无 | 无 |
| **Agent 间通信** | Mailbox 消息 | 无 | 无 | 无 |

### 5.2 独到设计

| 设计 | OpenHarness | 其他框架 |
|------|------------|---------|
| **Worktree 自动隔离** | ★★★★★ Agent spawn 自动创建，完成后自动清理 | opencode 手动 API，其他无 |
| **Symlink 大目录** | ★★★★ 避免重复 node_modules 等 | 无 |
| **Markdown 记忆** | ★★★ 可人工编辑，语义搜索 | 无 |
| **多后端 Swarm** | ★★★★ subprocess / in_process / tmux / iTerm2 | 无 |
| **Pane 可视化** | ★★★ tmux/iTerm2 原生 pane 边框+颜色 | 无 |
| **Slug 安全验证** | ★★★★ 防路径穿越、限长、字符限制 | opencode 无验证 |

---

## 六、对 Nop 的设计建议

### 6.1 从 OpenHarness 借鉴

| 借鉴 | 说明 | Nop 如何融入 |
|------|------|------------|
| **Worktree 自动隔离** | 子 agent spawn 时自动创建 worktree，完成后清理 | `call-agent` 工具增加 `worktree: true` 参数，自动创建隔离工作目录 |
| **Symlink 大目录** | `node_modules`/`.venv` 等从主仓库 symlink | Nop 项目中 symlink `target/`、`.m2/` 等构建产物目录 |
| **cleanup_stale** | 按 `active_agent_ids` 清理不活跃的 worktree | session 结束时自动清理该 session 的所有 worktree |
| **Slug 验证** | 严格的路径安全验证 | Nop 的 worktree slug 也需要验证 |
| **Markdown 记忆** | 跨会话的项目级记忆 | Nop 的 `ai_session_context` 已覆盖上下文快照，Markdown 记忆可作为补充 |

### 6.2 OpenHarness 的问题

| 问题 | 说明 |
|------|------|
| **无数据库** | 所有存储基于文件系统，无法做 SQL 聚合查询 |
| **会话快照覆盖写** | `latest.json` 每次覆盖，丢失历史快照 |
| **AppStateStore 非持久化** | 应用状态重启丢失 |
| **worktree 无数据库记录** | 纯文件系统状态，无法查询"哪些 agent 有 worktree" |
| **记忆系统无版本管理** | MEMORY.md 只有最新版本，无法回溯 |

### 6.3 Nop 的优势

OpenHarness 证明了 worktree 自动隔离是可行的，但它的纯文件系统方案有明显短板。Nop 可以：

1. **用数据库记录 worktree**：在 `ai_session` 表新增 `worktree_path` 字段，session 结束时自动清理
2. **用 JGit 替代 git CLI**：不依赖系统 git，跨平台兼容
3. **用 Nop ORM 管理记忆**：将 Markdown 记忆存入数据库，支持 SQL 搜索
4. **保留 OpenHarness 的 symlink 策略**：避免 worktree 中重复大目录

---

## 七、核心安全防护机制

> 本节分析 OpenHarness 的三层安全防护：工作循环防护、上下文压缩、权限检查。
> 核心文件：`query.py`（1057行）、`compact/__init__.py`（1725行）、`permissions/checker.py`（200行）

### 7.1 工作循环防护（query.py）

#### 7.1.1 Turn 上限

```python
# query.py:153, query_engine.py:37
max_turns: int | None = 200    # query.py 默认（单次用户输入的 agent 轮次上限）
max_turns: int | None = 8      # QueryEngine 默认（更保守）
```

**关键设计**：
- `run_query()` 主循环 `while context.max_turns is None or turn_count < context.max_turns`
- 超过上限时抛出 `MaxTurnsExceeded(max_turns)` 异常（`query.py:129-134`）
- `QueryEngine.set_max_turns()` 允许运行时动态调整，但强制 `max(1, int(max_turns))`

**对比**：

| 框架 | 默认 max_turns | 说明 |
|------|---------------|------|
| OpenHarness query.py | 200 | 内层 run_query 默认 |
| OpenHarness QueryEngine | 8 | 外层 QueryEngine 默认（更保守） |
| opencode | 无硬上限 | 依赖 context window 自然终止 |
| Claude Code | 无硬上限 | 同上 |

#### 7.1.2 Completion Token 安全上限

```python
# query.py:48
MAX_SAFE_COMPLETION_TOKENS = 128_000

# query.py:90-100 — 保守的 per-request output token cap
def _bounded_completion_tokens(max_tokens, context_window_tokens=None):
    limit = min(MAX_SAFE_COMPLETION_TOKENS, context_window_tokens)
    return max(1, min(int(max_tokens), limit))
```

**防止**：用户配置过大的 `max_tokens` 导致每次请求都失败。自动适配 provider 限制。

#### 7.1.3 Prompt Too Long 错误检测与恢复

```python
# query.py:66-87 — 17种错误模式检测
def _is_prompt_too_long_error(exc):
    needles = (
        "prompt too long", "context_length_exceeded", "context length",
        "maximum context", "context window", "input tokens exceed",
        "messages resulted in", "reduce the length of the messages",
        "configured limit", "too many tokens", "too large for the model",
        "maximum context length", "exceed_context",
        "exceeds the available context size", "available context size",
    )
```

**恢复策略**（`query.py:768-777`）：

```
API 调用失败
├── completion_token_limit_error?
│   ├── 提取 provider 限制值 → 调低 effective_max_tokens → 重试
│   └── continue（不消耗 turn 配额）
├── prompt_too_long_error + 未尝试过 reactive compact?
│   ├── reactive_compact_attempted = True
│   ├── 强制 full compact → 重试
│   └── continue
├── network error?
│   └── yield ErrorEvent + return（终止）
└── 其他错误
    └── yield ErrorEvent + return（终止）
```

**关键**：`reactive_compact_attempted` 标志确保只尝试一次 reactive compact，避免无限重试。

#### 7.1.4 空 Assistant 消息防护

```python
# query.py:792-800
if final_message.is_effectively_empty():
    yield ErrorEvent(message="Model returned an empty assistant message...")
    return  # 终止查询，不将空消息加入历史
```

#### 7.1.5 工具执行异常隔离

```python
# query.py:853-855 — 多工具并发执行
raw_results = await asyncio.gather(
    *[_run(tc) for tc in tool_calls], return_exceptions=True
)
```

**`return_exceptions=True`**：单个工具失败不会导致其他工具的协程被取消。这是 Anthropic API 的硬性要求——每个 `tool_use` 必须有对应的 `tool_result`。

#### 7.1.6 Tool Output Offloading

```python
# query.py:524-553
def _offload_tool_output_if_needed(tool_name, tool_use_id, output):
    if len(output) <= tool_output_inline_chars():
        return output, None  # 小输出直接内联
    # 大输出写入文件，返回 preview + 文件路径
    artifact_path = tool_artifacts/{timestamp}-{tool_name}-{uuid12}.txt
    return inline_preview, artifact_path
```

### 7.2 上下文压缩（compact/__init__.py）

#### 7.2.1 四级压缩瀑布

OpenHarness 的压缩系统忠实翻译自 Claude Code，采用**四级瀑布**，从廉价到昂贵：

```
auto_compact_if_needed() 被调用（每个 turn 开始时）
│
├── 1. Microcompact（零成本）
│   ├── 清除旧的 compactable 工具结果（read_file, bash, grep, glob...）
│   ├── 替换为 "[Old tool result content cleared]"
│   ├── keep_recent=5（保留最近 5 个工具结果）
│   └── 如果 tokens_freed > 0 且低于阈值 → 结束
│
├── 2. Context Collapse（零成本）
│   ├── 对超大文本块做 head+tail 截断（900 + 500 chars）
│   ├── 中间用 "...[collapsed N chars]..." 替代
│   └── 如果低于阈值 → 结束
│
├── 3. Session Memory Compaction（零成本）
│   ├── 将旧消息摘要为 `{role}: {text[:160]}` 格式
│   ├── 最多 48 行 / 4000 chars
│   ├── 保留最近 12 条消息原文
│   └── 如果有效 → 结束
│
└── 4. Full Compact（LLM 调用）
    ├── 先做 microcompact
    ├── 分割 older/newer（preserve_recent=6）
    ├── 发送 older + compact_prompt 给 LLM 生成摘要
    ├── 用摘要替换 older，保留 newer 原文
    └── 超时 25 秒，最多 2 次流式重试 + 3 次 PTL 重试
```

#### 7.2.2 Auto-Compact 阈值计算

```python
# compact/__init__.py:55, 1080-1093
AUTOCOMPACT_BUFFER_TOKENS = 13_000
MAX_OUTPUT_TOKENS_FOR_SUMMARY = 20_000

def get_autocompact_threshold(model, context_window_tokens=None, auto_compact_threshold_tokens=None):
    context_window = get_context_window(model, context_window_tokens)
    reserved = min(MAX_OUTPUT_TOKENS_FOR_SUMMARY, 20_000)
    effective = context_window - reserved
    return effective - AUTOCOMPACT_BUFFER_TOKENS
    # 例：200K context → threshold ≈ 167K tokens
```

#### 7.2.3 失败保护

```python
# compact/__init__.py:57
MAX_CONSECUTIVE_AUTOCOMPACT_FAILURES = 3

# should_autocompact() 检查
if state.consecutive_failures >= MAX_CONSECUTIVE_AUTOCOMPACT_FAILURES:
    return False  # 连续失败 3 次后停止尝试
```

#### 7.2.4 Tool Pair 保护

```python
# compact/__init__.py:483-503
def _split_preserving_tool_pairs(messages, preserve_recent):
    split_index = max(0, len(messages) - preserve_recent)
    # 如果分割点切断 tool_use → tool_result 对，向前移动分割点
    while split_index > 0 and _boundary_crosses_tool_pair(messages[split_index-1], messages[split_index]):
        split_index -= 1
```

**保证**：压缩边界永远不会切断 `assistant.tool_use → user.tool_result` 对。

#### 7.2.5 Compact 附件系统

压缩时保留 8 类结构化附件，通过 `CompactAttachment` 传递给压缩后的对话：

| 附件 | 来源 | 说明 |
|------|------|------|
| `task_focus` | `tool_metadata.task_focus_state` | 当前目标、活跃工件、下一步 |
| `recent_verified_work` | `tool_metadata.recent_verified_work` | 最近验证过的工作 |
| `recent_attachments` | 消息中的 path/attachment 模式 | 本地文件附件路径 |
| `recent_files` | `tool_metadata.read_file_state` | 最近读取的文件（路径+span+preview） |
| `plan` | `tool_metadata.permission_mode` | plan 模式上下文 |
| `invoked_skills` | `tool_metadata.invoked_skills` | 使用过的 skill 列表 |
| `async_agents` | `tool_metadata.async_agent_state` | 异步 agent 活动 |
| `recent_work_log` | `tool_metadata.recent_work_log` | 最近执行的操作日志 |

#### 7.2.6 Reactive Compact（prompt too long 恢复）

```python
# query.py:768-777 — API 返回 prompt too long 时的紧急压缩
if not reactive_compact_attempted and _is_prompt_too_long_error(exc):
    reactive_compact_attempted = True
    # force=True 跳过阈值检查，强制完整压缩
    async for event, usage in _stream_compaction(trigger="reactive", force=True):
        yield event, usage
    if was_compacted:
        continue  # 重试本轮
```

#### 7.2.7 PTL Retry（压缩本身 prompt too long）

```python
# compact/__init__.py:60, 346-361
MAX_PTL_RETRIES = 3

def truncate_head_for_ptl_retry(messages):
    # 按用户消息分组，丢弃最旧的 1/5
    drop_count = max(1, len(groups) // 5)
    retained = [msg for group in groups[drop_count:] for msg in group]
    # 如果第一条是 assistant，注入 PTL_RETRY_MARKER
```

### 7.3 权限检查（permissions/checker.py）

#### 7.3.1 三级权限模式

```python
# permissions/modes.py
class PermissionMode(str, Enum):
    DEFAULT = "default"      # 读取自动允许，写入需确认
    PLAN = "plan"            # 只读，阻止所有写入工具
    FULL_AUTO = "full_auto"  # 全部自动允许
```

#### 7.3.2 硬编码敏感路径保护

```python
# permissions/checker.py:18-37 — 不可覆盖的硬编码保护
SENSITIVE_PATH_PATTERNS = (
    "*/.ssh/*",                          # SSH keys
    "*/.aws/credentials",                # AWS
    "*/.aws/config",
    "*/.config/gcloud/*",                # GCP
    "*/.azure/*",                        # Azure
    "*/.gnupg/*",                        # GPG
    "*/.docker/config.json",             # Docker
    "*/.kube/config",                    # Kubernetes
    "*/.openharness/credentials.json",   # 自身凭证
    "*/.openharness/copilot_auth.json",
)
```

**关键**：这些模式**始终生效**，无论用户配置或权限模式如何设置。使用 `fnmatch` 匹配，防御 LLM 指令注入访问凭证文件。

#### 7.3.3 评估流程

```
evaluate(tool_name, is_read_only, file_path, command)
│
├── 1. 敏感路径检查（硬编码，不可覆盖）
│   └── fnmatch 匹配 → denied
├── 2. 工具拒绝名单 → denied
├── 3. 工具允许名单 → allowed
├── 4. 路径规则（用户自定义 glob）
│   └── deny 规则 → denied
├── 5. 命令拒绝模式（如 "rm -rf /"）
│   └── 匹配 → denied
├── 6. FULL_AUTO 模式 → allowed
├── 7. 只读工具 → allowed
├── 8. PLAN 模式 → denied（阻止写入）
└── 9. DEFAULT 模式 + 写入工具 → requires_confirmation
```

#### 7.3.4 Bash 安装命令提示

```python
# permissions/checker.py:176-199
install_markers = ("npm install", "pip install", "cargo install", ...)
# 如果 bash 命令匹配安装命令模式，附加友好提示
```

### 7.4 工作追踪与 Focus 状态（query.py）

OpenHarness 在 `tool_metadata` 中维护丰富的**运行时状态**，用于跨 turn 保持焦点：

| 追踪器 | 上限 | 说明 |
|--------|------|------|
| `read_file_state` | 6 | 最近读取的文件（path + span + preview + timestamp） |
| `invoked_skills` | 8 | 调用过的 skill 列表 |
| `async_agent_state` | 8 | 异步 agent 活动事件 |
| `async_agent_tasks` | 12 | 异步 agent 任务（agent_id + task_id + status） |
| `recent_work_log` | 10 | 最近执行的操作 |
| `recent_goals` | 5 | 最近的用户目标 |
| `active_artifacts` | 8 | 活跃的工件路径 |
| `verified_state` | 10 | 已验证的工作 |
| `task_focus_state` | — | 综合焦点状态（goal + next_step + artifacts） |

**设计特点**：
- `_append_capped_unique()`：新值推入，重复值移到末尾，超过上限截断旧值
- `_record_tool_carryover()`：在每次工具执行后统一更新所有追踪器
- 所有状态在压缩时通过 `CompactAttachment` 保留

### 7.5 安全防护总结

| 防护层级 | 机制 | 实现 |
|---------|------|------|
| **循环防护** | `max_turns` 上限 | `MaxTurnsExceeded` 异常（默认 8-200） |
| **循环防护** | completion token 上限 | `MAX_SAFE_COMPLETION_TOKENS=128K` |
| **循环防护** | 空 assistant 消息检测 | `is_effectively_empty()` → 终止 |
| **上下文管理** | 四级压缩瀑布 | microcompact → collapse → session_memory → full LLM |
| **上下文管理** | 连续失败保护 | 3 次后停止自动压缩 |
| **上下文管理** | tool pair 保护 | 分割边界不切断 tool_use/result 对 |
| **上下文管理** | reactive compact | prompt too long 时紧急压缩重试 |
| **权限控制** | 敏感路径硬编码 | SSH/AWS/GCP/Azure/GPG/Docker/K8s 凭证 |
| **权限控制** | 三级权限模式 | default / plan / full_auto |
| **权限控制** | 命令拒绝模式 | glob 匹配危险命令 |
| **工具执行** | 异常隔离 | `return_exceptions=True` 防级联取消 |
| **工具输出** | 大输出 offloading | 超限写文件，返回 preview |
| **状态追踪** | 9 类 focus tracker | 跨 turn 保持工作焦点 |

### 7.6 对 Nop 的安全防护设计建议

| 借鉴 | OpenHarness 实现 | Nop 建议 |
|------|-----------------|---------|
| **max_turns** | 默认 8（QueryEngine） | Nop `call-agent` 应支持 `maxTurns` 参数，默认保守值（如 20） |
| **四级压缩瀑布** | micro → collapse → session_memory → full LLM | Nop Layer 2 实现 microcompact + context collapse，Layer 3 可选 LLM summarization |
| **敏感路径硬编码** | fnmatch 匹配 `.ssh`/`.aws` 等 | Nop 应内置同样的路径保护，且**不可被 Delta 定制覆盖** |
| **tool pair 保护** | 压缩时不切断 tool_use/result 对 | Nop 的消息压缩必须保证 API 协议完整性 |
| **reactive compact** | prompt too long 紧急压缩 | Nop 应在 API 返回 context exceeded 时自动触发压缩 |
| **focus tracker** | 9 类 tool_metadata 追踪器 | Nop 可用 `ai_session_context` 表存储，压缩时作为附件注入 |
| **CompactAttachment** | 8 类结构化附件 | Nop 的压缩应保留 goal/artifacts/skills 等关键状态 |

---

## 八、与已有框架的全面安全对比

| 维度 | OpenHarness | opencode | Claude Code |
|------|------------|----------|-------------|
| **Turn 上限** | ★★★★★ `max_turns` + `MaxTurnsExceeded` | ★★☆☆☆ 依赖 context window | ★★☆☆☆ 同上 |
| **压缩瀑布** | ★★★★★ 四级（含 LLM summarization） | ★★★☆☆ 无（依赖外部） | ★★★★★ 原始实现 |
| **敏感路径保护** | ★★★★☆ 硬编码 + fnmatch | ★★☆☆☆ 无 | ★★★☆☆ 有但较简单 |
| **权限模式** | ★★★★☆ default/plan/full_auto | ★★☆☆☆ 无 | ★★★★☆ 类似 |
| **异常恢复** | ★★★★☆ completion limit + PTL retry | ★★☆☆☆ 基础重试 | ★★★★☆ 类似 |
| **Focus 追踪** | ★★★★★ 9 类追踪器 + 8 类附件 | ★☆☆☆☆ 无 | ★★★☆☆ 部分追踪 |

---

## References

- `~/ai/OpenHarness/` — 本地克隆（`git clone https://github.com/HKUDS/OpenHarness`）
- `src/openharness/engine/query.py`（1057行）— 核心查询循环、turn 上限、异常恢复、focus 追踪
- `src/openharness/engine/query_engine.py`（306行）— 高层对话引擎，session memory 集成
- `src/openharness/services/compact/__init__.py`（1725行）— 四级压缩瀑布、microcompact、LLM summarization
- `src/openharness/permissions/checker.py`（200行）— 权限检查、敏感路径保护
- `src/openharness/permissions/modes.py`（13行）— 权限模式枚举
- `src/openharness/engine/cost_tracker.py`（24行）— 用量聚合
- `src/openharness/services/token_estimation.py`（15行）— Token 估算（chars/4）
- `src/openharness/swarm/worktree.py` — WorktreeManager（git worktree 自动隔离）
- `src/openharness/swarm/types.py` — Swarm 类型定义（TeammateIdentity, SpawnConfig, SpawnResult）
- `src/openharness/swarm/registry.py` — Backend 注册表（subprocess/in_process/tmux/iterm2）
- `src/openharness/state/store.py` — AppStateStore（内存可观察状态）
- `src/openharness/memory/` — 记忆系统（Markdown 文件 + 语义搜索）
- `ohmo/session_storage.py` — ohmo 会话持久化（JSON 文件 + 三级存储）
