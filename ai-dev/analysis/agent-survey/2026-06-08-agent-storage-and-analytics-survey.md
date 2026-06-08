# Agent 框架存储结构与可分析性深度调研

> Status: done
> Date: 2026-06-08
> Scope: opencode v2 架构（源码级） + Claude Code（实测分析） + cline + agent-zero + oh-my-claudecode 的持久化存储结构、写入流程、可派生分析
> Conclusion: opencode v2 采用事件溯源 + CQRS 投影，`session_message` 单表消息模型取代了 v1 的 message+part 双层模型。Claude Code 采用 JSONL 追加写 + progress 嵌入设计。Nop 应直接采用 v2 架构设计理念 + Claude Code 的文件版本快照 + 其他框架的增补设计。

---

## 一、opencode v2 架构：事件溯源 + CQRS 投影

### 1.1 架构总览

opencode 经历了 v1 → v2 的架构迁移。v1 的 `message` + `part` 双层模型已被 v2 的 `session_message` 单表模型取代。v2 的核心是**事件溯源（Event Sourcing）+ CQRS 投影**：

```
事件总线（EventV2）
  │
  ├── 投影到 session 表（会话元数据、token 聚合）
  ├── 投影到 session_message 表（对话内容，v2 核心）
  ├── 投影到 session_input 表（输入队列）
  ├── 投影到 session_context_epoch 表（上下文快照）
  ├── 投影到 event 表（持久化事件日志）
  └── 投影到 v1 遗留表 message + part（向后兼容）
```

**v1 已废弃，仅保留向后兼容投影。** Nop 直接实现 v2 即可。

### 1.2 v2 核心表结构

#### session 表（活跃写入，聚合统计）

```typescript
// packages/core/src/session/sql.ts — SessionTable
{
  id: string,                       // 会话 ID（ses_xxx）
  project_id: string,               // 项目 ID（FK → project）
  parent_id: string | null,         // 父会话 ID（子 agent 树）
  workspace_id: string | null,      // 工作区 ID
  slug: string,                     // 人类可读标识（如 "eager-cactus"）
  directory: string,                // 工作目录
  path: string | null,              // 子目录
  title: string,                    // 会话标题
  agent: string | null,             // agent 类型（build/explore/general/houyi...）
  model: { id, providerID, variant? } | null,  // 当前模型
  version: string,
  cost: number,                     // 累计成本（美元）—— 由 step.ended 事件累加
  tokens_input: number,             // 累计输入 token
  tokens_output: number,            // 累计输出 token
  tokens_reasoning: number,         // 累计推理 token
  tokens_cache_read: number,        // 累计缓存读取 token
  tokens_cache_write: number,       // 累计缓存写入 token
  summary_additions: number | null, // 代码变更行数
  summary_deletions: number | null,
  summary_files: number | null,
  summary_diffs: FileDiff[] | null, // 文件变更详情
  revert: { messageID, partID?, snapshot?, diff? } | null,  // 回滚信息
  permission: PermissionV1.Ruleset | null,  // 权限规则
  metadata: Record<string, unknown> | null,
  time_created: number,             // 创建时间（ms epoch）
  time_updated: number,             // 最后更新时间
  time_compacting: number | null,   // 最后压缩时间
  time_archived: number | null,     // 归档时间
}
```

#### session_message 表（v2 核心，活跃写入）

```typescript
// packages/core/src/session/sql.ts — SessionMessageTable
{
  id: string,                // 消息 ID
  session_id: string,        // FK → session
  type: MessageType,         // 消息类型（见下）
  seq: number,               // 事件序列号（单调递增，用于排序）
  time_created: number,
  time_updated: number,
  data: JSON,                // 类型相关的 JSON 数据
}
// UNIQUE INDEX (session_id, seq)
```

**8 种消息类型**（`SessionMessage.Message` 联合类型）：

| type | 写入时机 | data 结构 |
|------|---------|-----------|
| `user` | 用户发消息 / 子 agent 收到 prompt | `{text, files[], agents[], references[]}` |
| `assistant` | LLM 开始新 turn | `{agent, model, content[], snapshot?, cost?, tokens?, finish?, error?}` |
| `compaction` | 上下文压缩 | `{reason: "auto"\|"manual", summary, include?}` |
| `shell` | shell 命令执行 | `{callID, command, output}` |
| `synthetic` | 系统注入消息（如 background task 结果） | `{sessionID, text}` |
| `system` | 上下文更新通知 | `{text}` |
| `agent-switched` | agent 类型切换 | `{agent}` |
| `model-switched` | 模型切换 | `{model: {id, providerID}}` |

**assistant 消息的 content 字段**（数组，内含 3 种子类型）：

```typescript
type AssistantContent =
  | { type: "text", id: string, text: string }
  | { type: "reasoning", id: string, text: string, providerMetadata? }
  | { type: "tool", id: string, name: string, state: ToolState, provider?, time: {...} }
```

**tool 的 state 状态机**：

```
pending → running → completed
                  → error
```

```typescript
type ToolState =
  | { status: "pending", input: string }                          // 输入解析中
  | { status: "running", input: {}, structured: {}, content: [] } // 执行中
  | { status: "completed", input: {}, structured: {}, content: [], result? } // 成功
  | { status: "error", input: {}, structured: {}, content: [], error, result? } // 失败
```

#### session_input 表（输入队列，活跃写入）

```typescript
// packages/core/src/session/sql.ts — SessionInputTable
{
  id: string,              // 消息 ID（与 session_message.id 对应）
  session_id: string,      // FK → session
  prompt: Prompt,          // JSON: {text, files[], agents[], references[]}
  delivery: "steer" | "queue",  // 投递模式
  admitted_seq: number,    // 入队时的事件序列号
  promoted_seq: number | null,  // 提升时的事件序列号（null=未提升）
  time_created: number,
}
```

**状态机**：`admitted（入队）→ promoted（提升，写入 session_message）`

- `steer`：实时插入模式（用户中断当前 turn）
- `queue`：排队模式（等当前 turn 完成后再处理）

#### session_context_epoch 表（上下文快照）

```typescript
// packages/core/src/session/sql.ts — SessionContextEpochTable
{
  session_id: string,          // PK, FK → session
  baseline: string,            // 基线内容
  snapshot: JSON,              // 系统上下文快照
  baseline_seq: number,        // 基线事件序列号
  replacement_seq: number | null, // 替换序列号（null=未替换）
  revision: number,            // 修订版本号
}
// 注意：agent 列（默认值 'build'）在迁移中加入，但 sql.ts 源码中未定义——可能是 DB 残留
```

#### todo 表（独立写入，不经过事件系统）

```typescript
// packages/core/src/session/sql.ts — TodoTable
{
  session_id: string,      // FK → session
  content: string,         // todo 内容
  status: string,          // pending | in_progress | completed
  priority: string,        // high | medium | low
  position: number,        // 排序位置（Replace-All 模式）
  time_created: number,
  time_updated: number,
}
// PK: (session_id, position)
```

#### project 表（项目级上下文）

```typescript
// packages/core/src/project/sql.ts — ProjectTable
{
  id: string,              // PK (hash of worktree path)
  worktree: string,        // 项目根目录绝对路径
  vcs: string | null,      // 版本控制类型（"git"）
  name: string | null,     // 项目名称
  icon_url: string | null,
  icon_color: string | null,
  time_created: number,
  time_updated: number,
  time_initialized: number | null,
  sandboxes: string[],     // 沙箱目录列表
  commands: { start?: string } | null,  // 自定义命令
}
// 关联：project_directory 表（project_id, directory, type: main|root|git_worktree）
```

**实测数据**：project 表记录了项目级上下文（worktree 路径 + VCS 类型），session 通过 `project_id` 关联。例如 nop-entropy 项目有 4325 个 session，nop-chaos-flux 有 798 个。但 project 表**不含运行时信息**（OS、JVM 版本、构建工具版本等）。

#### event / event_sequence 表（事件日志）

```typescript
// packages/core/src/event/sql.ts
{
  // event_sequence
  aggregate_id: string,    // 聚合根 ID（= session_id）
  seq: number,             // 当前序列号
  owner_id: string | null,

  // event
  id: string,              // 事件 ID
  aggregate_id: string,    // FK → event_sequence
  seq: number,             // 事件序列号（单调递增）
  type: string,            // 事件类型
  data: JSON,              // 事件数据
}
// UNIQUE INDEX (aggregate_id, seq)
```

### 1.3 事件类型（v2 Durable Events）

事件以 `session.next.*` 命名，按 session 为聚合根（aggregate_id = session_id），seq 单调递增。

**30 种 Durable 事件**（会被持久化和投影）：

| 事件类型 | 触发时机 | 投影目标 |
|----------|---------|---------|
| `session.created` | 创建会话 | session 表 INSERT |
| `session.updated` | 更新会话 | session 表 UPDATE |
| `session.deleted` | 删除会话 | session 表 DELETE |
| `session.next.moved` | 会话移动 | session 表 UPDATE + epoch reset |
| `session.next.agent.switched` | 切换 agent | session.agent UPDATE + session_message INSERT |
| `session.next.model.switched` | 切换模型 | session.model UPDATE + session_message INSERT + epoch replacement |
| `session.next.prompted` | 用户发消息 | session_message(user) INSERT + session_input INSERT |
| `session.next.prompt.admitted` | 输入入队 | session_input INSERT |
| `session.next.prompt.promoted` | 输入提升 | session_message(user) INSERT |
| `session.next.context.updated` | 上下文更新 | session_message(system) INSERT + epoch replacement |
| `session.next.synthetic` | 系统注入 | session_message(synthetic) INSERT |
| `session.next.shell.started` | Shell 开始 | session_message(shell) INSERT |
| `session.next.shell.ended` | Shell 结束 | session_message(shell) UPDATE |
| `session.next.step.started` | LLM 调用开始 | session_message(assistant) INSERT |
| `session.next.step.ended` | LLM 调用结束 | session_message(assistant) UPDATE（填入 cost/tokens/finish）+ session 聚合统计 UPDATE |
| `session.next.step.failed` | LLM 调用失败 | session_message(assistant) UPDATE（填入 error） |
| `session.next.text.started` | 文本输出开始 | assistant.content PUSH text |
| `session.next.text.ended` | 文本输出结束 | assistant.content UPDATE text |
| `session.next.reasoning.started` | 推理开始 | assistant.content PUSH reasoning |
| `session.next.reasoning.ended` | 推理结束 | assistant.content UPDATE reasoning |
| `session.next.tool.input.started` | 工具输入开始 | assistant.content PUSH tool(pending) |
| `session.next.tool.input.ended` | 工具输入结束 | assistant.content UPDATE tool.input |
| `session.next.tool.called` | 工具调用执行 | assistant.content UPDATE tool(running) |
| `session.next.tool.progress` | 工具进度更新 | assistant.content UPDATE tool.structured/content |
| `session.next.tool.success` | 工具调用成功 | assistant.content UPDATE tool(completed) |
| `session.next.tool.failed` | 工具调用失败 | assistant.content UPDATE tool(error) |
| `session.next.compaction.started` | 压缩开始 | session_message(compaction) INSERT |
| `session.next.compaction.delta` | 压缩增量 | session_message(compaction) UPDATE |
| `session.next.compaction.ended` | 压缩结束 | session_message(compaction) UPDATE + epoch replacement |
| `message.updated` / `message.part.updated` / `message.removed` / `message.part.removed` | v1 遗留事件 | message + part 表（向后兼容） |

**3 种 Ephemeral 事件**（流式传输，不持久化）：
- `session.next.text.delta` — 文本流式片段
- `session.next.tool.input.delta` — 工具输入流式片段
- `session.next.reasoning.delta` — 推理流式片段

### 1.4 完整写入流程

#### 流程 1：用户发消息 → LLM 响应（最常见的流程）

```
1. 用户输入
   ├── event: session.next.prompt.admitted  → session_input INSERT (admitted_seq=N)
   ├── event: session.next.prompt.promoted  → session_input UPDATE (promoted_seq=N)
   └── event: session.next.prompted
       └── projector: session_message INSERT (type="user")

2. LLM 开始推理（provider 返回第一个 token 前）
   └── event: session.next.step.started
       ├── projector: 关闭上一个未完成的 assistant（设 time.completed）
       └── projector: session_message INSERT (type="assistant", content=[])

3. LLM 推理过程
   ├── event: session.next.reasoning.started → assistant.content PUSH reasoning(id, text="")
   ├── event: session.next.reasoning.delta   → (ephemeral, 不持久化)
   ├── event: session.next.reasoning.ended   → assistant.content UPDATE reasoning.text
   ├── event: session.next.text.started      → assistant.content PUSH text(id, text="")
   ├── event: session.next.text.delta        → (ephemeral, 不持久化)
   └── event: session.next.text.ended        → assistant.content UPDATE text.text

4. LLM 调用工具（如果有）
   ├── event: session.next.tool.input.started → assistant.content PUSH tool(pending)
   ├── event: session.next.tool.input.ended   → assistant.content UPDATE tool.input
   ├── event: session.next.tool.called        → assistant.content UPDATE tool→running
   ├── event: session.next.tool.progress      → assistant.content UPDATE tool.structured/content
   └── event: session.next.tool.success/failed → assistant.content UPDATE tool→completed/error

5. LLM 完成一轮
   └── event: session.next.step.ended
       ├── projector: assistant UPDATE (cost, tokens, finish, snapshot)
       └── projector: session UPDATE (累加 cost/tokens_*)
```

#### 流程 2：上下文压缩

```
1. event: session.next.compaction.started  → session_message INSERT (type="compaction", reason="auto")
2. event: session.next.compaction.delta     → session_message UPDATE (summary += delta)
3. event: session.next.compaction.ended     → session_message UPDATE (summary=final)
   └── projector: session_context_epoch UPDATE (replacement_seq)
```

#### 流程 3：子 agent 调度

```
1. task 工具被调用
   ├── 创建子 session (parent_id=当前 session)
   └── event: session.created → session INSERT

2. 子 agent 运行（流程 1 的完整循环，独立 session_id）

3. 子 agent 完成
   ├── 如果 foreground: 结果作为工具调用返回值，父 agent 在同一轮继续
   └── 如果 background: 结果注入到父 agent 下一轮
       └── event: session.next.synthetic → session_message INSERT (type="synthetic")
```

#### 流程 4：todo 更新

```
1. LLM 调用 todowrite 工具
   └── 直接操作 todo 表（DELETE ALL + INSERT ALL，不经过事件系统）
```

### 1.5 数据关系图（v2）

```
project ──1:N──> session ──1:N──> session_message
                  │                    │
                  │                    ├── type=user: 用户消息
                  │                    ├── type=assistant: LLM 响应（含 content[] 子内容）
                  │                    ├── type=compaction: 上下文压缩
                  │                    ├── type=shell: Shell 命令执行
                  │                    ├── type=synthetic: 系统注入
                  │                    ├── type=system: 上下文更新
                  │                    ├── type=agent-switched: Agent 切换
                  │                    └── type=model-switched: 模型切换
                  │
                  ├──1:N──> session_input (输入队列: admitted → promoted)
                  ├──1:1──> session_context_epoch (上下文快照)
                  ├──1:N──> todo (Replace-All 待办)
                  └── parent_id → session (子 agent 树)

event_sequence ──1:N──> event (以 session_id 为聚合根, seq 单调递增)
```

### 1.6 v1 已废弃的表和字段

| 表/字段 | 状态 | v2 替代 |
|---------|------|---------|
| `message` 表 | **废弃，仅保留向后兼容投影** | `session_message` 单表 |
| `part` 表 | **废弃，仅保留向后兼容投影** | `session_message.data` 中的 content[] |
| part type=`snapshot` | **废弃** | session 表的 summary_* 字段 |
| part type=`subtask` | **废弃** | `session_message(type="shell")` |
| part type=`agent` | **废弃** | `session_message(type="agent-switched")` |
| part type=`retry` | **废弃** | Retried 事件被注释掉 |
| `session.share_url` | **可能废弃** | v2 SessionInfo 已无 share 字段 |
| `workspace` 表 | **0 行数据** | 未使用的功能 |
| `permission` 表 | **0 行数据** | 权限规则存在 session.permission JSON 中 |
| v1 event types (`session.created`, `message.updated`, `message.part.updated`) | **废弃** | v2 `session.next.*` 系列事件 |

---

## 二、cline：VSCode Extension State + 文件系统

### 2.1 存储架构

| 层 | 存储位置 | 格式 | 内容 |
|----|----------|------|------|
| **全局配置** | VSCode globalState / 文件 | JSON | API 配置、模型设置、MCP 服务器 |
| **任务历史** | `~/.cline/tasks/` | JSON 文件 | HistoryItem[]（任务元数据索引） |
| **对话数据** | `{workspace}/.cline/` | JSON 文件 | 每个任务一个目录，含 api_conversation_history.json + ui_messages.json |

### 2.2 核心数据模型

#### HistoryItem（任务索引）

```typescript
type HistoryItem = {
  id: string
  ulid?: string
  ts: number                        // 创建时间戳
  task: string                      // 任务描述（用户输入的第一条消息）
  tokensIn: number
  tokensOut: number
  cacheWrites?: number
  cacheReads?: number
  totalCost: number
  size?: number                     // 对话大小
  modelId?: string
  isFavorited?: boolean
  cwdOnTaskInitialization?: string
  conversationHistoryDeletedRange?: [number, number]
}
```

#### 对话数据文件

每个任务存储为 `{task_id}/` 目录：

```
~/.cline/tasks/{task_id}/
├── api_conversation_history.json   # 原始 LLM API 对话
├── ui_messages.json                # UI 展示消息
└── task_metadata.json              # 任务元数据（环境信息）
```

### 2.3 关键设计特点

1. **无数据库**：全部基于文件系统，通过 `atomicWriteFile`（temp + rename 模式）保证原子性
2. **VSCode globalState 作为缓存**：内存缓存 + 文件持久化，启动时从文件加载
3. **StateManager 单例**（964 行）：管理 globalState/taskState/sessionOverride 三层缓存
4. **无事件系统**：不支持实时事件推送或历史回放
5. **task_progress 嵌入式参数**：不独立存储，每次通过工具调用参数传入

### 2.4 源码位置

| 文件 | 用途 |
|------|------|
| `apps/vscode/src/core/storage/StateManager.ts` (964 行) | 状态管理器 |
| `apps/vscode/src/core/storage/disk.ts` (692 行) | 文件读写 |
| `apps/vscode/src/shared/HistoryItem.ts` | HistoryItem 类型 |
| `apps/vscode/src/shared/ExtensionMessage.ts` | ClineMessage 类型 |

---

## 三、agent-zero：JSON 文件 + Pydantic 模型

### 3.1 存储架构

| 存储位置 | 格式 | 内容 |
|----------|------|------|
| `usr/scheduler/tasks.json` | JSON | 所有任务（AdHoc/Scheduled/Planned） |

### 3.2 核心数据模型

#### TaskPlan（计划状态机）

```python
class TaskPlan(BaseModel):
    todo: list[datetime]          # 待执行时间列表（排序）
    in_progress: datetime | None  # 当前执行中
    done: list[datetime]          # 已完成（排序）
```

唯一一个用**时间而非内容**来索引 todo 项的框架。

#### 三种任务类型

| 类型 | 特有字段 | 用途 |
|------|---------|------|
| `AdHocTask` | `token: str`（一次性令牌） | 一次性任务 |
| `ScheduledTask` | `schedule: TaskSchedule`（cron） | 定时任务 |
| `PlannedTask` | `plan: TaskPlan` | 计划任务（多时间点执行） |

#### BaseTask 公共字段

```python
class BaseTask(BaseModel):
    uuid: str
    context_id: str | None
    state: TaskState  # IDLE | RUNNING | ERROR | STOPPED
    name: str
    system_prompt: str
    prompt: str
    attachments: list[str]
    project_name: str | None
    created_at: datetime
    updated_at: datetime
    last_run: datetime | None
    last_result: str | None
```

### 3.3 关键设计特点

1. **单文件持久化**：`usr/scheduler/tasks.json`，`read_file`/`write_file` 操作
2. **线程安全**：`threading.RLock` + `asyncio` 锁
3. **Pydantic 验证**：保证数据完整性
4. **无对话存储**：不存储 LLM 对话历史，只存储任务定义和结果
5. **1278 行 TaskScheduler**：重量级调度器

### 3.4 源码位置

| 文件 | 用途 |
|------|------|
| `helpers/task_scheduler.py` (1278 行) | 任务调度器（数据模型 + 持久化 + 调度） |

---

## 四、oh-my-claudecode：文件系统 + JSONL + JSON

### 4.1 存储架构

```
{cwd}/.omc/state/
├── team/
│   └── {team_name}/
│       ├── tasks/
│       │   └── {task_id}.json       # 每任务一 JSON
│       ├── workers/{worker}/inbox.md # Leader→Worker 消息
│       ├── leader/inbox.md           # Worker→Leader 消息
│       ├── dispatch/requests.json    # 调度请求队列
│       └── events.jsonl              # 事件日志（追加写）
└── team-bridge/
    └── {team_name}/{worker}.heartbeat.json
```

### 4.2 核心数据模型

#### TaskFile（任务文件）

```typescript
interface TaskFile {
  id: string
  subject: string
  description: string
  activeForm?: string
  status: TeamTaskStatus  // pending | in_progress | completed | failed | cancelled
  owner: string
  blocks: string[]        // 此任务阻塞的任务
  blockedBy: string[]     // 阻塞此任务的依赖
  metadata?: Record<string, unknown>
  claimedBy?: string
  claimedAt?: number
  claimPid?: number       // 进程级锁
}
```

### 4.3 关键设计特点

1. **每任务一文件**：`{task_id}.json`，`writeAtomic`（temp + rename）保证原子性
2. **依赖图**：`blocks[]` + `blockedBy[]`，claim 时检查前置条件
3. **乐观锁**：`version` 字段防止并发冲突
4. **进程级锁**：`claimPid` 防止僵尸 worker 占用
5. **JSONL 事件日志**：追加写，记录任务状态转换
6. **在项目目录内**：状态跟随项目

### 4.4 源码位置

| 文件 | 用途 |
|------|------|
| `src/team/state/tasks.ts` | 任务状态管理 |
| `src/team/types.ts` | 类型定义 |
| `src/team/events.ts` | 事件日志 |

---

## 四-B、Claude Code（Anthropic 官方 CLI）：JSONL 追加写 + 文件系统

> 基于 Claude Code v2.1.12 实测数据，`~/.claude/` 目录，3311 个会话，1.0GB transcripts。

### 4B.1 存储架构

```
~/.claude/
├── transcripts/                              # 全局会话记录（JSONL，追加写）
│   └── ses_{id}.jsonl                         # 每会话一 JSONL
├── projects/                                 # 按项目组织的会话记录
│   └── {path-encoded}/                       # 项目路径编码（/ → -）
│       ├── sessions-index.json               # 会话索引（firstPrompt/messageCount/gitBranch）
│       ├── {session-id}/                     # 会话子目录
│       └── {session-id}.jsonl                # 会话记录（与 transcripts 不同，含 progress/system/file-history-snapshot 类型）
├── todos/                                    # Todo 列表
│   └── {session-id}-agent-{agent-id}.json    # 每 agent 每 session 一 JSON
├── plans/                                    # Agent 生成的计划
│   └── {slug}.md                             # Markdown 格式
├── file-history/                             # 文件变更历史
│   └── {session-id}/
│       └── {hash}@v{N}                       # 文件快照版本（v1, v2, v3...）
├── history.jsonl                             # 命令历史（用户输入 + project + sessionId）
├── settings.json                             # 全局设置（env 等配置）
├── stats-cache.json                          # 统计缓存（dailyActivity + dailyModelTokens）
├── debug/                                    # 调试日志
│   └── {session-id}.txt                      # 每会话一文件
├── session-env/                              # 会话环境变量
├── backups/                                  # 备份
├── statsig/                                  # A/B 测试/特性开关缓存
├── plugins/                                  # 插件目录
└── shell-snapshots/                          # Shell 快照
```

### 4B.2 核心数据模型

#### 会话记录（JSONL，每行一条消息）

**transcripts/ 中的消息类型**（精简版，3 种）：

```typescript
// 全局 transcripts — 仅记录 API 级别交互
| type        | 说明           | 字段 |
|-------------|---------------|------|
| user        | 用户消息       | timestamp, content |
| tool_use    | 工具调用       | timestamp, tool_name, tool_input |
| tool_result | 工具返回       | timestamp, tool_name, tool_input, tool_output |
```

**projects/ 中的消息类型**（完整版，5 种 + 共有字段）：

```typescript
// 项目级 JSONL — 含完整 agent 工作过程
// 所有消息共有字段：
{
  parentUuid: string | null,     // 父消息 UUID（链式结构）
  isSidechain: boolean,          // 是否 sidechain（子 agent）
  userType: "external",
  cwd: string,                   // 工作目录
  sessionId: string,             // 会话 ID
  version: string,               // Claude Code 版本
  gitBranch: string,             // 当前 git 分支
  uuid: string,                  // 消息 UUID
  timestamp: string,             // ISO 8601
}

// 5 种消息类型：
| type                 | 说明             | 额外字段 |
|----------------------|-----------------|---------|
| user                 | 用户消息         | message.role, message.content[], thinkingMetadata, todos |
| assistant            | LLM 响应        | message.id, message.model, message.content[]（thinking/text/tool_use）, message.usage |
| progress             | Agent 进度       | data.message（嵌套的 user/assistant/tool_result 消息）, data.normalizedMessages, data.type=agent_progress, data.prompt |
| system               | 系统事件         | subtype（turn_duration）, durationMs |
| file-history-snapshot| 文件变更快照     | messageId, snapshot.trackedFileBackups, snapshot.timestamp |
```

#### sessions-index.json（项目级会话索引）

```json
{
  "version": 1,
  "entries": [
    {
      "sessionId": "uuid",
      "fullPath": "/path/to/session.jsonl",
      "fileMtime": 1771171350135,
      "firstPrompt": "用户的第一条消息...",
      "messageCount": 18,
      "created": "2026-02-14T11:09:59.590Z",
      "modified": "2026-02-15T16:02:30.116Z",
      "gitBranch": "",
      "projectPath": "/Users/abc/app",
      "isSidechain": false
    }
  ]
}
```

#### stats-cache.json（统计缓存）

```json
{
  "version": 1,
  "lastComputedDate": "2026-02-14",
  "dailyActivity": [
    { "date": "2026-02-14", "messageCount": 1920, "sessionCount": 4, "toolCallCount": 297 }
  ],
  "dailyModelTokens": [
    { "date": "2026-02-14", "tokensByModel": { "glm-4.7": 180690 } }
  ]
}
```

### 4B.3 实测数据

| 维度 | 值 |
|------|-----|
| 总会话数 | 3311 |
| 总存储大小 | 1.3 GB（transcripts 1.0GB + projects 294MB） |
| transcripts 消息类型 | tool_use: 179411, tool_result: 174324, user: 11482 |
| 最大单会话 | 38 MB |
| 平均会话大小 | ~310 KB |
| file-history | 1.3 MB（6 个会话） |
| todos | 52 KB（10 个 agent 文件，多数为空 `[]`） |
| plans | 12 KB（1 个 Markdown 文件） |

### 4B.4 关键设计特点

1. **JSONL 追加写**：每条消息一行 JSON，天然支持增量写入，无需重写整个文件。与 cline 的 atomicWriteFile（全量覆盖）形成鲜明对比
2. **双存储位置**：transcripts/（全局，API 级别精简）+ projects/（按项目，含完整 agent 工作过程）。同一会话在两处都有，但粒度不同
3. **UUID 链式结构**：parentUuid 构成消息链，可以重建完整对话树。支持 isSidechain 标记子 agent
4. **progress 消息**：记录子 agent 的完整工作过程（嵌套的 user/assistant/tool_result），这是 opencode 没有的——opencode 用 parent_id 关联独立 session，Claude Code 把子 agent 进度嵌入父会话
5. **file-history-snapshot**：消息流内嵌文件快照指针，可以关联到 file-history/ 中的实际文件版本
6. **项目路径编码**：`/Users/abc/app` → `-Users-abc-app`，将路径安全映射为目录名
7. **sessions-index.json**：预计算的会话索引（firstPrompt/messageCount/gitBranch），避免遍历所有 JSONL 文件
8. **stats-cache.json**：预聚合的统计缓存（日维度的 messageCount/sessionCount/toolCallCount/tokensByModel）
9. **完整 API 消息保留**：assistant 消息的 content[] 完整保留了 LLM 返回的 thinking（推理过程）+ text + tool_use（含完整 input），不截断
10. **无数据库**：全部基于文件系统，无 SQLite。统计查询依赖预计算的 stats-cache.json

### 4B.5 源码位置

Claude Code 是闭源的，分析基于：
- `~/.claude/` 目录结构的实测分析
- 泄露源码（2026-03-31 npm source map 事件）的公开分析报告

---

## 五、五框架对比

 ### 5.1 存储层对比

| 维度 | opencode v2 | Claude Code | cline | agent-zero | oh-my-claudecode |
|------|------------|-------------|-------|------------|------------------|
| **存储引擎** | SQLite (drizzle ORM) | 文件系统 (JSONL) | 文件系统 (JSON) | 文件系统 (JSON) | 文件系统 (JSON/JSONL) |
| **架构模式** | 事件溯源 + CQRS 投影 | JSONL 追加写 | 文件读写 | 单文件 Pydantic | 每任务一文件 |
| **事务支持** | SQLite 事务 | 无（追加写天然原子） | atomicWriteFile | write_file | atomicWriteFile |
| **查询能力** | SQL 全功能 | 文件遍历 + 预计算索引 | 文件遍历 | 文件遍历 | 文件遍历 |
| **对话存储** | session_message（单表，JSON data） | JSONL（transcripts + projects 双位置） | api_conversation_history.json | 不持久化 | 无 |
| **工具调用** | assistant.content[type=tool] 含完整 input/output/state | progress 消息嵌套 tool_use/tool_result（含完整 input/output） | ui_messages.json | 无 | 无 |
| **token 统计** | session 聚合 + step 级粒度 | stats-cache.json（日维度预聚合） | HistoryItem 聚合 | 无 | 无 |
| **子 agent** | session.parent_id 树 | isSidechain + parentUuid 链式嵌入 | 无 | context_id 关联 | owner 字段 |
| **项目上下文** | project 表（worktree/VCS/name） | projects/{path-encoded}/ 目录 + sessions-index.json | 工作目录路径 | project_name 字段 | .omc/state/ 目录位置 |
| **上下文压缩** | session_context_epoch + compaction message | 无（无压缩） | 无 | 无 | 无 |
| **输入队列** | session_input (admitted→promoted) | 无 | 无 | 无 | 无 |
| **文件版本** | session.summary_* + snapshot hash | file-history/{hash}@v{N} | 无 | 无 | 无 |

### 5.2 数据粒度对比

| 数据维度 | opencode v2 | Claude Code | cline | agent-zero | oh-my-claudecode |
|----------|------------|-------------|-------|------------|------------------|
| **LLM 调用级别** | step.started/ended（含 cost/tokens） | assistant message.usage | 无 | 无 | 无 |
| **工具调用级别** | tool.called/success/failed 含完整 state | progress(tool_use/tool_result) 含完整 input/output | ClineMessage(type=tool) | 无 | 无 |
| **推理过程** | reasoning.started/ended | assistant.content[type=thinking]（含完整 thinking + signature） | 无 | 无 | 无 |
| **文件变更** | session.summary_* + snapshot hash | file-history-snapshot 指针 + file-history 版本文件 | 无 | 无 | 无 |
| **任务状态** | todo（Replace-All） | todos/ JSON 文件 | task_progress（嵌入参数） | TaskPlan(todo/in_progress/done) | TaskFile(blocks/blockedBy) |
| **依赖关系** | 无（LLM 自行管理） | 无 | 无 | TaskPlan 严格顺序 | blocks/blockedBy 图 |
| **子 agent 进度** | 独立 session（通过 parent_id 关联） | progress 消息嵌入父会话 | 无 | 无 | inbox.md 消息传递 |

---

## 六、opencode v2 实测数据分析

以下基于 `~/.local/share/opencode/opencode.db` 的 6136 个会话、849490 个 part 的实际数据。注意这些数据目前还在 v1 格式（part 表），但分析结论同样适用于 v2 的 session_message 结构。

### 6.1 工具调用频率与错误率

| 工具 | 调用次数 | 错误率 | 备注 |
|------|---------|--------|------|
| read | 106247 | 1.51% | 最常用工具 |
| bash | 56792 | 0.51% | 第二常用 |
| grep | 28943 | 0.14% | |
| glob | 25542 | 0.13% | |
| edit | 19166 | 4.87% | |
| write | 6209 | 8.39% | |
| todowrite | 8042 | 1.83% | |
| task | 4298 | 1.88% | 子 agent 调度 |
| question | 465 | **68.82%** | 用户未回答 = "error" |
| webfetch | 795 | 30.82% | 网络不稳定 |

### 6.2 会话维度统计

| 维度 | 值 |
|------|-----|
| 根会话 | 1219 |
| 子会话 | 4917 (80.2%) |
| 会话时长 >60min | 373 (30.6%) |
| 会话时长 5~30min | 408 (33.5%) |
| 会话时长 <1min | 133 (10.9%) |

### 6.3 模型使用分布

| 模型 | 会话数 | 输入 token | 平均输入/会话 |
|------|--------|-----------|-------------|
| glm-5.1 | 581 | 84.4M | 145K |
| glm-4.7-flash | 18 | 513K | 28.5K |
| deepseek-v4-flash-free | 15 | 4.9M | 325K |

### 6.4 可派生的分析

#### P0：可直接查询

| 分析 | 数据源 |
|------|--------|
| 工具使用频率/错误率 | session_message(type="assistant") → content[type="tool"] |
| 会话耗时分布 | session (time_updated - time_created) |
| 子 agent 深度 | session.parent_id 递归查询 |
| 模型 token 消耗 | session.cost / tokens_* 按 model 分组 |
| 代码变更量 | session.summary_additions/deletions/files |
| Todo 状态分布 | todo.status 全局/按会话 |
| LLM 调用耗时 | assistant.time.completed - time.created |
| 上下文压缩频率 | session_message(type="compaction") 按 session 统计 |

#### P1：需关联查询

| 分析 | 方法 |
|------|------|
| 每次工具调用的 token 成本 | 关联 step.ended 事件的 tokens + 同一 assistant 下的 tool content |
| 子 agent 对父 agent 进度的贡献 | 关联 parent session todo + child session session_message |
| 文件热点 | session.summary_diffs 解析 |
| 错误恢复模式 | 找 tool(type=error) 后续的 step.started/ended |
| 输入队列行为 | session_input admitted/promoted 时间差 |

#### P2：需复杂分析

| 分析 | 方法 |
|------|------|
| 工具调用序列模式挖掘 | 按时间排序提取 tool content 序列 |
| Agent 策略效果评估 | 关联 todo 完成 + 代码变更 |
| 上下文窗口利用趋势 | step.ended tokens 随时间变化 |
| 并行子 agent 效率对比 | 同父会话下子 agent duration vs result |
| 输入队列优化 | steer vs queue 投递模式的效果对比 |

### 6.5 v2 相比 v1 的分析优势

| 优势 | 说明 |
|------|------|
| **单表查询** | 不再需要 message + part JOIN，session_message 单表含全部信息 |
| **seq 排序** | 事件序列号保证严格顺序，无需依赖 time_created |
| **结构化 content** | assistant 消息的 content[] 直接含 tool/text/reasoning，无需解析 part.type |
| **tool state machine** | pending→running→completed/error 状态机，比 v1 的 status 字段更结构化 |
| **输入队列分析** | session_input 的 admitted/promoted 可分析用户中断行为 |

---

## 七、存储结构深度对比与问题分析

### 7.1 opencode v2 的问题

#### 问题 1：SQLite 数据膨胀——4GB，但这不是 Nop 的问题

实测数据：opencode 的 SQLite 已经膨胀到 **4.0GB**。

| 表 | 存储占用 | 占比 |
|----|---------|------|
| part（v1） | 2.3 GB | 56% |
| message（v1） | 1.8 GB | 44% |
| 索引 | 174 MB | 4% |
| 其他表 | <5 MB | <1% |

根因分析：

| 问题 | 原因 | 数据 |
|------|------|------|
| user message 嵌入完整 diff | `summary.diffs[]` 把每个变更文件的完整内容（before+after）存入 user message 的 data JSON | 最大单条 373MB，17 条 >10MB，97 条 >1MB |
| v1 冗余存储 | 同一 assistant turn 被存两次（v1 message+part + v2 session_message） | v1 占 99%+ 空间 |
| SQLite 单文件限制 | SQLite 单文件 = 单机 + 无分布式 + 无弹性扩展 | 4GB 后查询性能开始下降 |
| 无自动清理机制 | session 没有 TTL 或自动归档删除 | 6136 个 session 全量保留 |

**但这些对 Nop 不成立**：

| opencode v2 的困境 | Nop 的解决方式 |
|-------------------|--------------|
| SQLite 单文件限制 | Nop ORM 天然支持 MySQL/PostgreSQL，4GB 根本不是问题 |
| v1 + v2 双写冗余 | Nop 直接实现 v2，无 v1 遗留，节省 50% 空间 |
| 担心数据太大需要截断 | **Nop 需要保存完整数据**——agent 的完整工作过程（推理、工具调用的 input/output、文件变更）是可审计、可回溯、可分析的基础，不应该截断 |
| 无 TTL | Nop 作为企业级平台，数据按企业策略归档，不需要自动删除 |

**结论**：opencode v2 的数据膨胀问题本质上是 **SQLite + v1 遗留** 的问题，不是"数据太多"的问题。Nop 使用真实数据库 + 直接实现 v2（无 v1），这个问题不存在。**完整数据保留是 Nop 的正确策略**。

#### 问题 2：session_message 的 JSON data 嵌套深度

v2 的 assistant 消息 data 是深度嵌套的 JSON：

```json
{
  "type": "assistant",
  "agent": "build",
  "model": {"id": "glm-5.1", "providerID": "zhipuai-coding-plan"},
  "content": [
    {"type": "reasoning", "id": "r1", "text": "..."},
    {"type": "text", "id": "t1", "text": "..."},
    {"type": "tool", "id": "c1", "name": "read", "state": {
      "status": "completed",
      "input": {"pattern": "**/*.java"},
      "structured": {},
      "content": [{"type": "text", "text": "...很长的文件内容..."}],
      "result": "Found 54 files..."
    }}
  ],
  "tokens": {"input": 24593, "output": 162, "reasoning": 0, "cache": {"read": 448, "write": 0}},
  "cost": 0.0012
}
```

**问题**：一个 assistant 消息可能包含多个工具调用，每个工具调用的完整 input/output 都嵌套在 content[] 中。一个长会话的 assistant 消息可能达到 MB 级别。

**SQLite 查询限制**：无法直接用 SQL 查询嵌套 JSON 数组中的特定 tool 调用（SQLite 的 `json_extract` 不支持 JSON 数组过滤）。需要全量读出后在应用层过滤。

**Nop 的改进方向**：
- 考虑将 tool 调用独立存储（类似 v1 的 part 拆分），但保留 v2 的消息关联模型
- 或者使用 PostgreSQL 的 `jsonb` 路径查询能力（如果将来迁移）

#### 问题 3：事件溯源的复杂性

v2 的事件溯源架构引入了显著复杂性：

| 组件 | 代码量 | 复杂度 |
|------|--------|--------|
| `event.ts` | 30 种事件定义 + 3 种 Ephemeral | 事件类型爆炸 |
| `projector.ts` | 所有事件的投影逻辑 | 单文件包含全部投影规则 |
| `message-updater.ts` | immer produce 状态更新 | 每种事件类型一个分支处理 |
| `input.ts` | admitted → promoted 状态机 | 竞争条件处理 |
| `context-epoch.ts` | baseline/replace/advance 状态机 | revision 乐观锁 + 重试 |

**优点**：
- 完整的审计追踪（每个状态变更都有事件记录）
- 支持事件回放（可以从 event 表重建所有投影）
- 天然支持实时推送（事件 = UI 更新信号）

**缺点**：
- 调试困难：状态变更分散在事件 → 投影器 → 表更新三步中
- 写入放大：每个状态变更需要 event INSERT + projection UPDATE/INSERT
- 新增事件类型需要修改 3+ 个文件（event.ts + projector.ts + message-updater.ts）

**Nop 的取舍**：不需要完整的事件溯源。可以保留事件日志用于审计，但写入路径直接操作表（跳过投影器）。

### 7.2 cline 的问题

| 问题 | 影响 |
|------|------|
| **无数据库** | 无法做聚合查询（如"过去一周用了多少 token"必须遍历所有 JSON 文件） |
| **全局 StateManager 单例** | 多窗口无法共享状态，配置变更需要重启 |
| **无增量写入** | 每次对话变更都要重写整个 `api_conversation_history.json`（atomicWriteFile = 全量覆盖） |
| **大文件性能** | 长对话的 JSON 文件可达数十 MB，每次读写都是全量操作 |
| **无事件系统** | 无法做实时推送或历史回放 |
| **VSCode 绑定** | 存储层依赖 VSCode Extension API，无法独立运行 |

**优点**：
- 极简实现，零依赖
- 文件可直接人工检查（JSON 可读）
- 原子写入保证一致性

### 7.3 agent-zero 的问题

| 问题 | 影响 |
|------|------|
| **单文件 tasks.json** | 所有任务存储在一个文件中，任务数量大时读写性能下降 |
| **无对话存储** | 无法回溯 LLM 的推理过程和工具调用历史 |
| **线程模型复杂** | `threading.RLock` + `asyncio` 混合使用，容易死锁 |
| **TaskPlan 用时间索引** | 用 `datetime` 而非内容标识 todo 项，不适合非定时场景 |
| **Pydantic 验证开销** | 每次读写都做完整模型验证，大文件时性能差 |

**优点**：
- 任务模型设计优秀（AdHoc/Scheduled/Planned 三类型 + TaskPlan 状态机）
- Pydantic 保证数据完整性
- 自带 cron 调度器

### 7.4 oh-my-claudecode 的问题

| 问题 | 影响 |
|------|------|
| **在项目目录内** | `.omc/state/` 会被 git 跟踪（需要 .gitignore），不同分支隔离困难 |
| **每任务一文件** | 大量小文件，文件系统 I/O 开销大 |
| **乐观锁 + claimPid** | 僵尸 worker 检测依赖进程 PID，进程重启后失效 |
| **inbox.md 是 Markdown** | 非结构化消息传递，解析困难 |
| **无对话存储** | 依赖 Claude Code 原生存储，无法独立分析 |

**优点**：
- 依赖图设计优秀（blocks/blockedBy）
- 乐观锁版本号防并发
- JSONL 事件日志是好的审计模式
- 文件系统天然支持多进程并发（通过文件锁）

### 7.5 四框架综合评分

| 维度 | opencode v2 | cline | agent-zero | oh-my-claudecode |
|------|:-----------:|:-----:|:----------:|:----------------:|
| **查询能力** | ★★★★★ | ★☆☆☆☆ | ★☆☆☆☆ | ★★☆☆☆ |
| **写入性能** | ★★★★☆ | ★★☆☆☆ | ★★★☆☆ | ★★★☆☆ |
| **存储效率** | ★★☆☆☆ | ★★☆☆☆ | ★★★★☆ | ★★★☆☆ |
| **数据完整性** | ★★★★★ | ★★★☆☆ | ★★★★☆ | ★★★★☆ |
| **实时推送** | ★★★★★ | ★☆☆☆☆ | ☆☆☆☆☆ | ★★☆☆☆ |
| **分析能力** | ★★★★★ | ★★☆☆☆ | ★☆☆☆☆ | ★★☆☆☆ |
| **实现复杂度** | ★☆☆☆☆（复杂） | ★★★★★（简单） | ★★★★☆ | ★★★☆☆ |
| **可扩展性** | ★★★★☆ | ★★☆☆☆ | ★★☆☆☆ | ★★★★☆ |
| **多进程支持** | ★★★★★（WAL） | ★☆☆☆☆ | ★★☆☆☆ | ★★★★★ |

### 7.6 框架间的互补设计

各框架有独到之处，但 opencode v2 缺少的设计：

| 来自 | 设计 | opencode v2 是否有 | 价值 |
|------|------|:-----------------:|------|
| cline | 运行时环境元数据（OS/IDE/语言分布） | 部分有（project 表有项目级上下文：worktree/VCS/name，但无 OS/JVM 等运行时信息） | ★★★ 跨环境分析 |
| cline | 对话大小追踪（`size` 字段） | 无 | ★★★ 上下文窗口管理 |
| agent-zero | 任务调度器（cron + plan） | 无 | ★★★★ 定时任务 |
| agent-zero | TaskPlan 状态机（todo→in_progress→done） | 无（todo 是扁平列表） | ★★★ Plan 进度跟踪 |
| oh-my-claudecode | blocks/blockedBy 依赖图 | 无 | ★★★★ 多 agent 协作 |
| oh-my-claudecode | 乐观锁版本号（防并发冲突） | 无（依赖 SQLite 事务） | ★★☆ SQLite 已足够 |
| oh-my-claudecode | JSONL 追加写事件日志 | 有（event 表） | ★★★ event 表可替代 |
| oh-my-claudecode | 进程级锁（claimPid） | 无 | ★★☆ 不适用于 Java |

---

## 八、评判：哪个存储结构更好

### 8.1 评判框架

评判一个 Agent 存储结构好不好，需要从**四个根本问题**出发：

| # | 根本问题 | 为什么重要 |
|---|---------|-----------|
| Q1 | **能记住什么？** | Agent 的记忆粒度决定了推理质量。如果只能记住"任务状态"而记不住"推理过程"，就无法回溯和改进 |
| Q2 | **能查到什么？** | 存了不等于能用。文件系统存了 JSON，但 SQL 查不到，等于没存 |
| Q3 | **能撑多久？** | 生产环境下的持久性。单文件锁、V1+V2 双写膨胀、无 TTL——任何一个都会让系统在长期运行后不可用 |
| Q4 | **实现代价多大？** | 好架构如果需要 3000 行代码才能跑起来，在原型阶段不如 300 行的简陋方案 |

### 8.2 逐框架评判

#### opencode v2：**功能最强，但自身问题最重**

| 维度 | 评判 |
|------|------|
| **能记住什么** | ★★★★★ 最完整的记忆。8 种消息类型覆盖了 agent 工作的全生命周期：用户意图、LLM 推理过程（含 reasoning）、工具调用（含完整 input/output/state）、上下文压缩、agent 切换、模型切换。支持子 agent 树（parent_id）。**其他三个框架加起来都不如它记的多** |
| **能查到什么** | ★★★★★ SQLite + 聚合字段。session 表直接有 cost/tokens 的累计值，不需要聚合查询。SQL 全功能支持 JOIN/子查询/窗口函数。event 表支持历史回放。**唯一的遗憾是 assistant.content[] 嵌套 JSON 在 SQLite 中查询困难** |
| **能撑多久** | ★★★☆☆ 实测 4.0GB SQLite，但根因是 **SQLite 单文件 + v1 双写遗留**，不是设计理念错误。Nop 使用 MySQL/PostgreSQL + 直接实现 v2（无 v1），这个问题不存在。**完整数据保留是正确策略**——agent 的全部工作过程是可审计、可回溯、可分析的基础 |
| **实现代价** | ★☆☆☆☆ 最复杂。事件溯源 + CQRS 投影器 + 30 种事件类型 + 5 个状态机（input、context-epoch、tool、message-updater、event）。但**这个复杂度是事件溯源带来的，不是 session_message 设计本身的要求**。如果去掉 CQRS 投影器直接写表，复杂度降低 60%+ |

**结论**：opencode v2 的 **session_message 单表设计是四个框架中最好的消息模型**。数据膨胀是 SQLite + v1 遗留的工程问题，不影响设计理念。**取其精华（单表消息模型 + 聚合字段 + 输入队列 + 上下文快照），去其糟粕（事件溯源写入路径 + v1 兼容 + SQLite 限制）**。Nop 使用真实数据库 + 完整数据保留。

#### cline：**最简陋，但有一个亮点**

| 维度 | 评判 |
|------|------|
| **能记住什么** | ★★☆☆☆ 记住了 LLM 原始对话（api_conversation_history.json）和 UI 消息（ui_messages.json），但没有结构化。tool 调用混在 ClineMessage 里，没有独立状态。token 统计嵌入在 HistoryItem 中，没有累计值 |
| **能查到什么** | ★☆☆☆☆ **几乎不能查**。要回答"过去一周用了多少 token"，需要遍历所有 `{task_id}/api_conversation_history.json` 文件，每个文件可能几十 MB，全量解析 |
| **能撑多久** | ★★☆☆☆ atomicWriteFile 保证原子性，但每次对话变更都要**重写整个 JSON 文件**。长对话（几十 MB）时 I/O 开销巨大。无事件系统，无法实时推送 |
| **实现代价** | ★★★★★ 最简单。3 个 JSON 文件 + atomicWriteFile。964 行 StateManager 管理全部状态。但 VSCode 绑定导致无法独立运行 |

**亮点**：`task_metadata.json` 的三维度元数据设计（files_in_context + model_usage + environment_history）是其他框架都没有的。opencode 的 project 表只覆盖了"哪个项目"，cline 额外记录了"什么文件在读/编辑"、"什么模型在用"、"什么 OS/JVM 环境"。

**结论**：cline 的存储**不适合作为 Agent 框架的基础**，但 `task_metadata` 的三维度设计值得借鉴。

#### agent-zero：**任务调度最强，但不是 Agent 存储框架**

| 维度 | 评判 |
|------|------|
| **能记住什么** | ★☆☆☆☆ **不存对话历史**。只存任务定义和结果。无法回溯 LLM 的推理过程。这在 agent 框架中是致命缺陷——没有记忆就没有学习 |
| **能查到什么** | ★☆☆☆☆ 单文件 tasks.json，Pydantic 模型。能查任务状态，不能查对话历史。1278 行 TaskScheduler 做了完整的调度（cron + plan + 依赖），但存储层只是 read_file/write_file |
| **能撑多久** | ★★★☆☆ 单文件 + Pydantic 验证保证数据完整性。但任务数量大时读写性能下降。threading.RLock + asyncio 混合锁模型容易死锁 |
| **实现代价** | ★★★★☆ TaskScheduler 1278 行很重，但存储层（read_file/write_file）极简 |

**亮点**：TaskPlan 状态机（todo → in_progress → done）和三种任务类型（AdHoc/Scheduled/Planned）是所有框架中最结构化的任务模型。**如果 Nop 需要 Plan 系统的进度跟踪，应该参考这个设计**。

**结论**：agent-zero **不是一个 Agent 存储框架，而是一个任务调度器**。它的存储设计不适用于 Agent 对话场景，但任务模型值得借鉴。

#### oh-my-claudecode：**协作模型最强，但存储在项目目录内是设计错误**

| 维度 | 评判 |
|------|------|
| **能记住什么** | ★★☆☆☆ 不存对话历史（依赖 Claude Code 原生）。只存任务定义 + 事件日志 |
| **能查到什么** | ★★☆☆☆ 每任务一 JSON + JSONL 事件日志。可以查单个任务状态，可以查事件序列。但跨任务聚合需要遍历所有 JSON 文件 |
| **能撑多久** | ★★★★☆ atomicWriteFile + 乐观锁 + claimPid。文件系统天然支持多进程并发（通过文件锁）。但 `.omc/state/` 在项目目录内是个**设计错误**：git 跟踪、分支隔离困难、跨项目共享不可能 |
| **实现代价** | ★★★☆☆ 每任务一文件 + 依赖图 + 事件日志。实现量中等 |

**亮点**：**blocks/blockedBy 依赖图**是所有框架中唯一的任务间依赖关系设计。这对多 agent 协作至关重要——当 agent A 的任务依赖 agent B 的输出时，需要显式声明依赖关系，而不是靠 LLM 自己管理。

**结论**：oh-my-claudecode 的**依赖图设计独此一家**，但存储位置在项目目录内是设计错误。整体存储架构不适合作为 Agent 框架基础。

#### Claude Code：**工程化程度最高，但无结构化查询**

| 维度 | 评判 |
|------|------|
| **能记住什么** | ★★★★☆ JSONL 追加写，完整保留 user/tool_use/tool_result 三种 API 级消息。projects/ 额外保留 assistant（含 thinking）+ progress（子 agent 嵌入）+ file-history-snapshot + system（turn_duration）。**与 opencode v2 的 8 种消息类型相比，Claude Code 只有 5 种，但子 agent 进度嵌入父会话的设计比 opencode 的独立 session 更紧凑** |
| **能查到什么** | ★★☆☆☆ **无数据库，无 SQL 查询**。stats-cache.json 提供日维度预聚合统计，但无法做"过去一周用了多少 token"的动态查询（必须重新扫描所有 JSONL 文件）。sessions-index.json 提供会话级索引（firstPrompt/messageCount/gitBranch），但无法按消息内容搜索。**与 opencode v2 的 SQL 全功能查询能力形成最大反差** |
| **能撑多久** | ★★★★☆ JSONL 追加写天然原子性（每行一行 JSON），无需 atomicWriteFile 的全量覆盖。无 v1 遗留问题。但无自动归档/清理。3311 会话 1.0GB，增长可控。双存储位置（transcripts + projects）有一定冗余 |
| **实现代价** | ★★★★☆ 极简——JSONL 追加写 + 文件系统目录组织。无 ORM，无数据库，无事件系统。statsig 用于 A/B 测试而非存储 |

**亮点**：
1. **progress 消息**：子 agent 的完整工作过程（user→assistant→tool_use→tool_result）嵌入父会话的 progress 消息中。这比 opencode 的独立 session + parent_id 关联更紧凑——一个文件就能看到完整的 agent 协作过程
2. **双存储位置**：transcripts/ 是 API 级精简记录（3 种类型），projects/ 是完整 agent 工作记录（5 种类型）。不同粒度的数据分开存储
3. **file-history 版本快照**：`{hash}@v{N}` 格式的文件版本，可以在任意时间点恢复文件内容
4. **stats-cache.json 预聚合**：避免运行时全量扫描 JSONL 文件
5. **完整数据保留**：不截断 tool output，不截断 thinking，不截断 diff

**问题**：
1. **无结构化查询**：这是最大的缺陷。无法用 SQL 做 JOIN/聚合/过滤。所有分析都需要扫描 JSONL 文件
2. **双位置冗余**：同一会话在 transcripts/ 和 projects/ 都有记录，但粒度不同——维护两套存储的一致性是负担
3. **无上下文压缩**：长会话的 JSONL 文件会持续增长（最大 38MB），没有 compaction 机制
4. **无输入队列**：没有 opencode 的 session_input admitted/promoted 机制，无法处理用户中断

**结论**：Claude Code 的 **JSONL 追加写 + progress 嵌入** 设计是工程化程度最高的文件系统方案。在"无数据库"的约束下做到了最优解。但它缺乏 opencode v2 的 SQL 查询能力和上下文压缩能力。

### 8.3 综合排名

| 排名 | 框架 | 总评 |
|:----:|------|------|
| **1** | **opencode v2** | 功能最完整，问题最多，但问题都是工程层面的（可修复），设计理念正确 |
| **2** | **Claude Code** | 工程化程度最高的文件系统方案，progress 嵌入设计优秀，但无结构化查询 |
| **3** | oh-my-claudecode | 有独到设计（依赖图），但存储位置错误，不存对话历史 |
| **4** | cline | 最简陋，但 task_metadata 三维度设计有借鉴价值 |
| **5** | agent-zero | 不是 Agent 存储框架，是任务调度器。不存对话历史是致命缺陷 |

### 8.4 关键洞察

**五个框架实际上在解决不同层次的问题**：

| 层次 | 解决的问题 | 做得最好的框架 |
|------|-----------|--------------|
| **对话存储** | 记住 Agent 的全部工作过程 | opencode v2（8 种消息类型） ≈ Claude Code（5 种类型 + progress 嵌入） |
| **任务调度** | 管理任务的创建/调度/执行 | agent-zero（cron + TaskPlan） |
| **协作依赖** | 管理多 agent 间的任务依赖 | oh-my-claudecode（blocks/blockedBy） |
| **上下文追踪** | 追踪文件/模型/环境变化 | cline（task_metadata 三维度） |
| **文件版本** | 记录文件的历史变更 | Claude Code（file-history + snapshot 指针） |

**没有一个框架同时解决了所有五个层次的问题**。opencode v2 覆盖了对话存储层（最完整），Claude Code 覆盖了对话存储 + 文件版本（工程化最优），但两者都缺少任务调度和协作依赖。

---

## 九、哪个更适合 Nop

### 9.1 Nop 的特殊性

Nop 不是 VSCode 插件（如 cline），不是 Python 脚本（如 agent-zero），也不是 Claude Code 的扩展（如 oh-my-claudecode）。Nop 是一个 **Java 全栈框架**，其 AI Agent 模块需要：

| Nop 特性 | 存储需求 |
|---------|---------|
| Java 21 + Maven | 不依赖 Node.js/VSCode API，可以用 JDBC 直接操作 SQLite |
| Nop ORM | 可以用 ORM 模型定义表结构，自动生成 DDL |
| Nop IoC | 不需要全局单例，bean 生命周期由容器管理 |
| Nop GraphQL | 存储层可以暴露为 GraphQL API，供 UI 消费 |
| 多租户/企业场景 | 需要权限控制、审计日志、数据归档——SQLite 可能不够 |
| XLang 模板驱动 | 任务调度可以用 XLang 模板，不需要 Python 的 cron |

### 9.2 为什么 opencode v2 的**设计理念**最适合 Nop

**不是照搬 v2 的实现，而是采用它的设计理念**：

| v2 设计理念 | 为什么适合 Nop | Nop 如何改进实现 |
|------------|--------------|----------------|
| **session_message 单表** | 8 种消息类型覆盖 agent 全生命周期，Nop 可以用 ORM 单表 + JSON 字段 | 去掉 v1 兼容；tool 调用从 content[] 拆为独立行，解决嵌套 JSON 查询困难 |
| **session 聚合字段** | cost/tokens 累计值直接在表上，不需要运行时聚合 | Nop ORM 可以用 `@Transient` + `@Formula` 实现 |
| **session_input 输入队列** | admitted → promoted 状态机，支持用户中断和优先级调度 | 直接采用，Nop 的异步框架比 Node.js 更适合队列处理 |
| **session_context_epoch** | 上下文压缩快照，长会话必备 | 直接采用，Nop ORM 的 version 字段天然支持乐观锁 |
| **project 表** | 项目级上下文（worktree/VCS），session 通过 project_id 关联 | 直接采用，新增 runtime_metadata 补充运行时环境 |

### 9.3 为什么 v2 的**事件溯源写入路径**不适合 Nop

v2 的事件溯源（Event Sourcing + CQRS）是 opencode 为了支持 **UI 实时推送 + 历史回放** 而引入的。但这个复杂度在 Nop 中不值得：

| v2 事件溯源的成本 | Nop 的替代方案 |
|-----------------|--------------|
| 30 种事件类型 + 3 个文件修改 | Nop ORM 的 `@Listener` 或 `EntityLifeCycleObserver`，一种事件类型一个 listener |
| CQRS 投影器（事件 → 表写入） | 直接用 DAO 写表。Nop 的 `@PostCommit` 可以保证事务后通知 |
| immer produce 状态更新 | Java 的不可变对象 + Builder 模式，不需要 immer |
| 写入放大（event INSERT + projection UPDATE） | 直接写表 + 可选的审计日志（异步写入，不在关键路径上） |

**核心判断**：事件溯源的**审计能力**值得保留（event 表），但**写入路径**应该直接操作表。这是"取其精华，去其糟粕"的关键。

### 9.4 Nop 必须保存完整数据

opencode v2 的数据膨胀问题让人产生"需要截断数据"的直觉，但这个直觉是错的。Nop 作为企业级 Java 框架，场景完全不同：

| 问题 | opencode 的困境 | Nop 的正确策略 |
|------|----------------|--------------|
| **数据库选型** | SQLite 单文件，4GB 开始性能下降 | MySQL/PostgreSQL，TB 级不是问题 |
| **v1 遗留** | v1 + v2 双写，空间翻倍 | 直接实现 v2，无 v1 遗留 |
| **tool output** | opencode 考虑截断 | **完整保留**——tool 的 input/output 是 agent 工作过程的核心记录，截断 = 丢失可审计性 |
| **diff 嵌入** | user message 嵌入完整 diff 导致单条 373MB | **完整保留**——但用 MySQL/PG 的 BLOB/TEXT 列存储，不放在 JSON 里 |
| **数据归档** | 无 TTL，全量保留 | 企业策略归档（不是自动删除），按合规要求保留 |
| **上下文窗口管理** | 无对话大小追踪 | session 新增 `total_bytes` 字段，**用于监控而非截断** |

**为什么完整数据重要**：

1. **可审计性**：企业场景要求 agent 的每一步操作都有据可查——调了什么工具、传了什么参数、返回了什么结果
2. **可回溯性**：agent 出错时需要回溯完整推理链路，截断了就断链了
3. **可分析性**：tool 调用序列模式挖掘、错误恢复模式分析、上下文窗口利用趋势——都需要完整数据
4. **可学习性**：未来的 agent 训练/微调需要完整对话数据作为语料

 ### 9.5 从其他框架增补的设计

| 来源 | 设计 | 为什么 Nop 需要 | 如何融入 |
|------|------|---------------|---------|
| cline | `task_metadata`（files_in_context + model_usage + environment） | Nop 需要追踪文件上下文变化，用于上下文窗口管理 | session 新增 `context_metadata` JSON 字段 |
| agent-zero | TaskPlan 状态机（todo → in_progress → done） | Nop 的 Plan 系统（ai-dev/plans/）需要结构化进度跟踪 | todo 表新增 `plan_id` 关联，或独立的 plan_progress 表 |
| oh-my-claudecode | blocks/blockedBy 依赖图 | 多 agent 协作时任务间依赖 | todo 表新增 `depends_on` JSON 数组（简化版，不单独建表） |

> **Claude Code 的 file-history 版本快照值得记录但不纳入 Nop**：Nop 项目都在 git 管理下，`git checkout` / `git diff` 已提供完整的文件版本回滚能力。session_message 中的 tool 调用记录（write/edit 的 input/output）也间接保留了文件变更历史。不需要独立的文件快照表。

### 9.6 最终建议：opencode v2 设计 + Nop 工程化

```
设计来源：opencode v2（session_message 单表 + 聚合字段 + 输入队列 + 上下文快照）
增补来源：cline（上下文元数据）+ agent-zero（TaskPlan）+ oh-my-claudecode（依赖图）
实现方式：Nop ORM + MySQL/PostgreSQL + 直接写表（不经过 CQRS 投影器）+ 可选审计日志
数据策略：完整保留，不做截断

注：Claude Code 的 file-history 版本快照不纳入——Nop 项目在 git 管理下，git 已提供完整文件版本能力
```

7 张表，每张表都有明确的职责边界：

| 表 | 职责 | 写入频率 |
|----|------|---------|
| `ai_project` | 项目上下文 | 低（创建时写一次） |
| `ai_session` | 会话元数据 + 聚合统计 | 中（每个 LLM turn 更新一次） |
| `ai_session_message` | 对话内容（8 种类型） | 高（每条消息 INSERT） |
| `ai_session_input` | 输入队列 | 中（用户发消息时 INSERT） |
| `ai_session_context` | 上下文压缩快照 | 低（压缩时 UPDATE） |
| `ai_todo` | Replace-All 待办 + 依赖关系 | 中（LLM 调用 update-todos 时 REPLACE） |
| `ai_event`（可选） | 审计日志 | 高（异步写入，不在关键路径） |

---

## References

- `~/ai/opencode/packages/core/src/session/sql.ts` — v2 表定义
- `~/ai/opencode/packages/core/src/session/message.ts` — SessionMessage 联合类型（8 种消息类型）
- `~/ai/opencode/packages/core/src/session/event.ts` — v2 事件定义（30 种 Durable + 3 种 Ephemeral）
- `~/ai/opencode/packages/core/src/session/projector.ts` — CQRS 投影器（事件 → 表的写入逻辑）
- `~/ai/opencode/packages/core/src/session/message-updater.ts` — 消息状态更新器（immer produce）
- `~/ai/opencode/packages/core/src/session/input.ts` — 输入队列（admitted → promoted）
- `~/ai/opencode/packages/core/src/v1/session.ts` — v1 类型定义（已废弃，保留向后兼容）
- `~/ai/opencode/packages/core/src/database/migration/` — 迁移文件（v1→v2 演进历史）
- `~/ai/opencode/packages/stats/core/src/database/schema.ts` — 远程统计服务（MySQL，不适用于 Nop）
- `~/ai/cline/apps/vscode/src/core/storage/StateManager.ts` (964 行) — cline 状态管理
- `~/ai/cline/apps/vscode/src/core/storage/disk.ts` (692 行) — cline 文件持久化
- `~/ai/agent-zero/helpers/task_scheduler.py` (1278 行) — agent-zero 任务调度器
- `~/ai/oh-my-claudecode/src/team/state/tasks.ts` — oh-my-claudecode 任务状态管理
- `~/ai/oh-my-claudecode/src/team/types.ts` — oh-my-claudecode 类型定义
 - `ai-dev/analysis/2026-06-07c-agent-todo-mechanism-survey.md` — Todo 机制调研
 - `nop-ai/nop-ai-toolkit/src/main/resources/_vfs/nop/ai/tools/update-todos.tool.xml` — Nop todo 工具
- `~/.claude/` — Claude Code v2.1.12 实测数据目录（3311 会话，1.0GB transcripts）
  - `~/.claude/transcripts/` — 全局会话 JSONL（API 级，3 种消息类型）
  - `~/.claude/projects/` — 项目级会话 JSONL（完整级，5 种消息类型）
  - `~/.claude/file-history/` — 文件版本快照（`{hash}@v{N}` 格式）
  - `~/.claude/stats-cache.json` — 预聚合统计缓存
  - `~/.claude/sessions-index.json` — 项目级会话索引
  - `~/.claude/history.jsonl` — 命令历史

> **注**：`~/ai/` 下的文件是调研时在宿主机器上的源码位置，不在 nop-entropy 仓库内。
