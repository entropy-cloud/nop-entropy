# Agent 优秀设计模式汇总：Nop 平台可参考的架构方案

> Status: open
> Date: 2026-06-07
> Scope: 基于 21 篇 agent-survey 分析文档 + 6 个核心框架源码深度分析
> Purpose: 为 Nop 平台 AI Agent 模块设计提供经过验证的、源码级的参考方案

---

## 概述

本文档从 15+ 个 Agent 框架（21 篇分析文档 + 6 个框架源码级深度分析）的调研中，提取出 **28 个经过源码验证的设计模式**，按 Nop 可借鉴的优先级分为 P0（直接采用）、P1（适配采用）、P2（参考借鉴）三档。每个模式包含：来源框架、源码位置、核心算法、数据结构、Java/Nop 映射方案。

**调研覆盖**：Reasonix、PilotDeck、OpenCode、oh-my-opencode、oh-my-claudecode、nanobot、VoltAgent、Hermes、DeepAgents、AgentScope Java、Solon AI、Spring AI Alibaba、pi-agent、oh-my-pi 等。

---

## 一、P0 — 直接采用的设计模式

### 1.1 Cache-First 三区域上下文架构

**来源**: DeepSeek-Reasonix
**源码**: `src/memory/runtime.ts`

**问题**: 多数 Agent 每轮重排序/注入时间戳，导致 LLM 缓存命中率 <20%。DeepSeek 按 10% 未命中率计费，低命中意味着高成本。

**方案**: 上下文分为三个区域：

```
Zone 1: ImmutablePrefix (SHA-256 哈希固定)
  → system prompt + tool specs + few-shots
  → 计算一次后不再变动，缓存命中候选
  → 指纹算法: sha256(JSON.stringify({system, tools, shots})).slice(0, 16)

Zone 2: AppendOnlyLog (单调追加)
  → 对话消息，只追加不修改
  → 滑动窗口 200 条，FIFO 淘汰
  → _version 计数器每次追加递增

Zone 3: VolatileScratch (每轮重置)
  → reasoning, planState, notes
  → reset() 在每轮开始时调用
  → 永不上传到 LLM（蒸馏后才折叠进 Log）
```

**实测效果**: 435M input tokens, 99.82% cache hit, $12 vs 无缓存 $61（~80% 成本节省）。

**Nop 映射**:
- `ImmutablePrefix` → Nop 的 `IEvalScope` 不可变作用域，SHA-256 通过 `MessageDigest` 实现
- `AppendOnlyLog` → `ArrayDeque` 环形缓冲区 + 版本号
- `VolatileScratch` → 每请求 ThreadLocal/ScopableBean
- 缓存未命中推理链（priority chain）: cold-start → system-prompt-changed → tool-list-changed → schema-or-order-changed → unknown

> **与 1.7 关联**: VolatileScratch 是 Context Governance 管线操作的对象；MicroCompact (1.7 Tier 1) 本质上是在 AppendOnlyLog 上做的清理。

---

### 1.2 Tool-Call Repair 四阶段修复管线

**来源**: DeepSeek-Reasonix
**源码**: `src/repair/` (flatten.ts, scavenge.ts, truncation.ts, storm.ts)

**问题**: LLM 输出的工具调用 JSON 经常存在：参数丢失、JSON 截断、重复调用风暴。

**方案**: Chain of Responsibility 模式的四阶段管线：

| 阶段 | 触发条件 | 算法 |
|------|---------|------|
| **flatten** | schema 叶子参数 >10 或深度 >2 | 自动展平为点记法 (`a.b.c`)，dispatch 时重新嵌套 |
| **scavenge** | 每轮 | 正则扫描 `reasoning_content` 寻找遗漏的 tool-call JSON（3 种模式匹配器），MAX_SCAVENGE_INPUT=100KB 防 ReDoS |
| **truncation** | JSON 不完整 | 括号栈状态机：trim → 去尾逗号 → null 填充 → 闭合括号 → fallback `"{}"` |
| **storm** | 每轮 | 滑动窗口(windowSize=6)追踪最近调用，相同(name, args)≥3次 → 抑制 |

**Nop 映射**: `Chain<ToolCall>` 拦截器链，每阶段实现 `BiFunction<ToolCall, ToolCall>` 接口。`storm` 用 `LinkedHashMap` + `removeEldestEntry` 实现滑动窗口。

> **与 2.3 关联**: Storm 阶段是工具级别的去重，熔断器 (2.3) 是 turn 级别的终止——两层保护互补。

---

### 1.3 事件溯源内核（Event Sourcing + CQRS）

**来源**: DeepSeek-Reasonix
**源码**: `src/core/events.ts` (333 行), `src/core/reducers.ts` (239 行)

**核心设计**:

```
Event (27 种判别联合类型):
  user.message | model.turn.started | model.final |
  tool.preparing | tool.intent | tool.dispatched | tool.denied | tool.result |
  session.compacted | checkpoint.created | policy.budget.warning | ...

Reducer<TView> = (view: TView, event: Event) => TView
  → 纯函数投影，无 I/O，无变异

7 个投影视图:
  conversation | budget | plan | workspace | capabilities | status | session

State Rebuild = fold(emptyProjections, events, apply)
  apply(state, ev) = {
    conversation: conversation(state.conversation, ev),
    budget:       budget(state.budget, ev),
    ...
  }
```

**关键**: `session.compacted` 是唯一"替换"事件（等价于快照），避免无限重放。

**Nop 映射**:
- `Reducer<TView>` → Java `BiFunction<TView, Event, TView>`
- 7 投影 → CQRS 读模型
- 事件类型 → sealed interface + pattern matching (Java 21+)
- 与 Nop 的 XDsl 事件重放 + `IXdslStore` 天然契合

---

### 1.4 Agent 定义即 Schema（数据驱动 Agent）

**来源**: OpenCode
**源码**: `packages/opencode/src/agent/agent.ts` (433 行)

**核心数据结构**:

```typescript
AgentInfo = {
  name: string,
  mode: "primary" | "subagent" | "all",
  hidden?: boolean,
  temperature?: number, topP?: number,
  steps?: number,           // 最大迭代数
  permission: Ruleset,      // 扁平权限规则集
  model?: { modelID, providerID },
  prompt?: string,
  description?: string,
  color?: string,
}
```

**三种定义方式**:
1. **代码**: `Agent.Info` 对象（7 个内置 Agent）
2. **Markdown**: `.opencode/agent/*.md`（YAML frontmatter + prompt body）
3. **LLM 动态生成**: `Agent.generate(description)` → `generateObject(GeneratedAgent)`

**Nop 映射**:
- Agent 定义 → `@XDef` schema + `*.agent.xml` XDSL 文件
- 三种定义方式 → Nop 已有的代码/模型/Delta 定制三通道
- LLM 动态生成 → Nop biz action 中调用 LLM 生成 Agent 配置

---

### 1.5 子 Agent 权限派生（35 行算法）

**来源**: OpenCode
**源码**: `packages/opencode/src/agent/subagent-permissions.ts` (35 行)

**三源合并算法**:

```
deriveSubagentPermission(parentAgent, parentSession, subagent):
  1. parentAgent 的 edit 类 deny 规则    → 传播（如 plan 模式的编辑限制）
  2. parentSession 的 deny + external_directory 规则 → 传播
  3. 默认 deny: todowrite + task          → 防止递归子 agent
  合并: [...(1), ...(2), ...(3)]
```

**核心洞察**: 父 **agent** 的 deny 规则（而非 session）覆盖子 agent 权限。这防止子 agent 绕过 plan 模式的编辑限制。

**Nop 映射**: `IAuthorizationProvider.deducePermissions(parent, child)` 方法，三源合并到 `List<PermissionRule>`。默认 deny `task` 防止无限递归。

> **与 2.5 关联**: 权限派生控制"agent 能做什么"，Guardrail (2.5) 控制"输入/输出是否安全"——两者共同构成安全边界。

---

### 1.6 事件驱动状态机 Agent Loop

**来源**: nanobot
**源码**: `nanobot/agent/loop.py` (1272 行)

**状态机设计**:

```python
TurnState: RESTORE → COMPACT → COMMAND → BUILD → RUN → SAVE → RESPOND → DONE

_TRANSITIONS = {
    (RESTORE, "ok"):       COMPACT,
    (COMPACT, "ok"):       COMMAND,
    (COMMAND, "dispatch"): BUILD,
    (COMMAND, "shortcut"): DONE,
    (BUILD, "ok"):         RUN,
    (RUN, "ok"):           SAVE,
    (SAVE, "ok"):          RESPOND,
    (RESPOND, "ok"):       DONE,
}
```

**关键机制**:
- Per-session `ReentrantLock`（Java）/ `asyncio.Lock`（Python）保证串行
- 跨 session `Semaphore(3)` 控制并发
- Mid-turn 消息注入: 活跃 session 新消息进 pending queue，上限 3 条/turn
- Checkpoint/Restore: 工具执行期间自动保存，`/stop` 取消后可恢复

**Nop 映射**: `TurnState` + `_TRANSITIONS` 可用 XDSL 声明式定义，映射到 Nop 工作流引擎。

---

### 1.7 五阶段 Context Governance 管线

**来源**: nanobot
**源码**: `nanobot/agent/runner.py`

**每轮执行的管线**:

```
messages → drop_orphan_tool_results
         → backfill_missing_tool_results
         → microcompact
         → apply_tool_result_budget
         → snip_history
         → drop_orphan (re-clean)
         → backfill (re-clean)
```

**MicroCompact 算法**:
- 可压缩工具集: `read_file, exec, grep, find_files, web_search, web_fetch, list_dir`
- 保留最近 10 条，500 chars 以下的旧结果替换为 `[{tool_name} result omitted]`

**Tool Result Offloading**:
- 超过 `max_tool_result_chars`(默认 50000) 的结果持久化到文件
- Agent 收到文件路径引用 + 头尾摘要

**Snip History**:
- 预算 = `context_window - max_output - 1024`
- 从最早处截断到合法 user-turn 边界

**Nop 映射**: 管线 → `IXplInterceptor` 链，每个阶段一个拦截器。MicroCompact 的可压缩工具名集合 → 配置驱动的 `Set<String>`。

---

### 1.8 CanonicalMessage 提供者无关消息格式

**来源**: PilotDeck
**源码**: `src/model/protocol/canonical.ts` (259 行)

**核心设计**: 仅 2 个角色 (user/assistant)，工具交互是 content block 而非独立角色：

```
CanonicalMessage = { role: "user" | "assistant", content: CanonicalContentBlock[] }

CanonicalContentBlock =
  | TextBlock         { type: "text", text }
  | ThinkingBlock     { type: "thinking", text, signature? }
  | ImageBlock        { type: "image", source, data, mimeType }
  | ToolCallBlock     { type: "tool_call", id, name, input }
  | ToolResultBlock   { type: "tool_result", toolCallId, content[] }
  | ToolResultRef     { type: "tool_result_reference", path, preview }  ← 磁盘懒加载
```

**关键**: `ToolResultReference` 是代理模式——预览内联，完整内容在磁盘上按需加载。

**Nop 映射**: sealed interface 层级，`ToolResultReference` 用 `LazyContentBlock` 实现。

---

## 二、P1 — 适配采用的设计模式

### 2.1 Smart Router 六步路由管线

**来源**: PilotDeck
**源码**: `src/router/`

**管线**:

```
Step 1: Scenario Detection → subagent / explicit / default
Step 2: Short Continuation → ≤30 chars 且匹配正则 → 继承前轮 tier
Step 3: Judge Classification → 便宜模型分类 simple/medium/complex
Step 4: Orchestration → 注入编排 prompt，精简工具集
Step 5: Execute with Fallback Chain → 主模型失败→备选
Step 6: Zero-usage Retry Detection → 检测空用量自动重试
```

**Judge Prompt**: 明确的 tier 描述 + 路由规则 + 关键的"短续接必须继承前 tier"规则。

**Fallback 错误分类**:
- 可自纠正: `invalid_tool_arguments` → 重试
- 不可重试: `prompt_too_long`, `context_overflow` → 不重试

**实测**: Smart Routing 开启 $2.83 vs 关闭 $12.58，节省 ~77%。

**Nop 映射**: `IRouter` 接口 + 可插拔策略。Judge 分类 → 便宜模型快速调用。

---

### 2.2 三级渐进上下文压缩

**来源**: PilotDeck
**源码**: `src/context/compaction/`

```
Tier 1: MicroCompaction (零成本)
  → 替换旧 tool_result >1536 bytes 为 "[Old tool result content cleared]"
  → 仅处理 COMPACTABLE_TOOL_NAMES: read_file, bash, grep, glob, web_*
  → 保留最新 1 条

Tier 2: SnipEngine (无 LLM 调用)
  → 保留 head(2) + tail(4) turn
  → 工具对完整性: 跨边界 tool_call/tool_result 互删
  → 插入 <snip-boundary> 标记

Tier 3: CompactionEngine (LLM 摘要)
  → 保留尾部 35% 消息
  → LLM 摘要头部 (专用 summarizer prompt)
  → 插入 <compact-boundary> 标记

阈值: warningRatio=0.8, blockingRatio=0.95
```

**Nop 映射**: 三层对应三个 `ICompactor` 实现，按阈值逐级升级。

---

### 2.3 熔断器 + 流式感知 Failover

**来源**: nanobot + PilotDeck

**nanobot FallbackProvider** (`nanobot/providers/fallback_provider.py`):
- `_PRIMARY_FAILURE_THRESHOLD = 3` 连续失败后断路
- `_PRIMARY_COOLDOWN_S = 60` 半开探测
- 错误分类: transient (timeout/connection/5xx/429) vs non-transient (auth/permission/content_filter)
- **流式保护**: 已流出内容时跳过 failover，防止重复文本

**PilotDeck 熔断器** (`AgentLoop.ts`):
- `MAX_CONSECUTIVE_ALL_INVALID_TURNS = 3`
- 检测: 所有工具调用返回 `invalid_tool_input`，连续 3 轮 → 终止
- 任何一轮有成功 → 重置计数器

**Nop 映射**: `AtomicInteger` 计数器 + 阈值。错误分类 → `EnumSet<FallbackErrorKind>`。

---

### 2.4 Memory 三适配器模式

**来源**: VoltAgent
**源码**: `packages/core/src/memory/`

```
Memory (门面)
  ├── StorageAdapter    → 对话持久化 + Working Memory + Workflow State
  ├── EmbeddingAdapter  → text→vector (embed, embedBatch)
  └── VectorAdapter     → 向量存储 + 语义搜索

Working Memory:
  scope: "conversation" | "user"
  支持: Markdown 模板 或 Zod/JSON Schema 验证
```

**Nop 映射**: 三个 IoC bean 接口 `IStorageAdapter`, `IEmbeddingAdapter`, `IVectorAdapter`。Working Memory → Session 扩展属性。

---

### 2.5 Guardrail Pipeline（一等概念）

**来源**: VoltAgent
**源码**: `packages/core/src/workflow/internal/guardrails.ts`

**三动作模型**: `allow | modify | block`
- Input guardrail 可修改用户输入
- Output guardrail 可修改模型输出
- **Streaming guardrail**: 逐块检查，可 `abort()` 终止流
- 每个 guardrail 看到前一个 guardrail 的输出（`input` vs `originalInput`）

**Nop 映射**: `IGuardrail` 接口，流式 → `Flowable<GuardrailResult>`，链式组合。

---

### 2.6 持续目标（Sustained Goals）

**来源**: nanobot
**源码**: `nanobot/session/goal_state.py`

**生命周期**:
1. `/goal` 命令设置 `session.metadata["goal_state"]`
2. 每次 turn 注入 goal objective 到 Runtime Context（即使历史被压缩）
3. LLM 超时豁免: 活跃 goal 返回 `0.0`（无超时）
4. Turn 延续: `max_iterations` 后透明续接（最多 12 轮）
5. 完成 → `status="completed"` + recap

**Nop 映射**: Goal 存储在 Session 扩展属性。`goal_active_predicate` → biz action 前置检查。

---

### 2.7 Deferred-Ack Mailbox 消息系统

**来源**: oh-my-opencode Team Mode
**源码**: `src/features/team-mode/team-mailbox/` (505 行)

**基于文件系统的异步消息传递**:

```
发送: team_send_message()
  → 背压检查 (payload≤32KB, inbox≤256KB)
  → atomicWrite → .tmp.{uuid} → fsync → rename → {messageId}.json

投递: 三阶段预留
  Unreserved → reserveMessageForDelivery() → Reserved → commit → Processed
  失败 → release → 回退到 Unreserved

确认: poll 路径 (injector hook) 或 live delivery (idle member)
```

**崩溃恢复**: `.delivering-` 文件 >10 分钟 → 回退 + 检查 session 历史是否已包含。

**Nop 映射**: 用 DB 事务替代文件锁。消息表 + 状态字段 + 原子 UPDATE。at-least-once 语义保持。

---

### 2.8 Plugin Contribution 七种贡献类型

**来源**: PilotDeck
**源码**: `src/extension/`

| 贡献类型 | 用途 |
|---------|------|
| Tool | 注册新工具 |
| Command | 注册斜杠命令 |
| Hook | 生命周期钩子 |
| MCP Server | 提供 MCP 服务 |
| Permission Rule | 自定义权限规则 |
| Prompt | 注入提示模板 |
| Router | 自定义路由策略 |

**5 种 Hook 执行器**: Agent / Callback / Command(shell) / HTTP / Prompt

**Nop 映射**: 每种贡献类型 → Nop 扩展点 (`@XExtension`)。Hook 执行器 → `IHookExecutor` 接口多态。

---

### 2.9 Curator 技能生命周期管理

**来源**: Hermes Agent
**源码**: `agent/curator.py`

**状态机**:
```
ACTIVE → STALE (30 天无使用) → ARCHIVED (90 天)
  ↑         reactivated if used
```

**LLM 审查循环**:
1. 扫描 agent 创建的技能，寻找前缀聚类
2. 确定伞形分类
3. 三策略: 合并到现有伞形 / 创建新伞形 / 降级为子文件
4. 从不删除，只归档（可恢复）

**Nop 映射**: `ISkillCurator` 服务 + 定时任务。技能状态 → ORM 实体 + `status` 字段。LLM 审查 → biz action 调用便宜模型。

---

## 三、P2 — 参考借鉴的设计模式

### 3.1 Workflow Chain DSL（16 步骤类型）

**来源**: VoltAgent
**源码**: `packages/core/src/workflow/steps/types.ts`

16 种步骤: andThen, andWhen, andAll, andRace, andForEach, andBranch, andGuardrail, andSleep, andSleepUntil, andDoWhile, andDoUntil, andMap, andTap, andWorkflow, andAgent

**Suspend/Resume**: `AbortSignal` 中断 + checkpoint 数据持久化 + `resume(input)` 恢复。

**Time Travel**: `workflow.timeTravel(executionId, stepId)` 从任意历史步骤重执行。

**Nop 参考**: Nop 已有 `nop-wf`，可参考 suspend/resume 的 checkpoint 数据结构和 time travel 调试能力。

---

### 3.2 Team Mode 并行多 Agent

**来源**: oh-my-opencode
**源码**: `src/features/team-mode/` (6378 行)

**核心**: Lead + 最多 8 并行成员，通过 `team_*` 工具族通信，独立 session + 可选 git worktree 隔离。

**共享任务列表**: 原子文件锁认领 + `blockedBy` 依赖 + 5 分钟过期检测。

**崩溃恢复**: 4 种异常状态自动恢复（creating 卡住 / lead 死亡 / worker 死亡 / deleting 卡住）。

**Nop 参考**: 可用 Nop 工作流引擎 + DB 事务替代文件锁。

---

### 3.3 双总线架构

**来源**: nanobot
**源码**: `nanobot/bus/`

```
MessageBus (消息投递): inbound Queue + outbound Queue
RuntimeEventBus (状态通知): 类型化事件 + subscribe(event_type) + publish_nowait
```

**Nop 参考**: MessageBus → `IMessageService`。RuntimeEventBus → Nop 已有的事件机制，可增加类型化订阅。

---

### 3.4 IntentGate 意图路由

**来源**: oh-my-opencode
**源码**: `src/hooks/keyword-detector/` (635 行)

关键词→模式路由: ultrawork / search / analyze / team / hyperplan → 注入模式特定 prompt。

**Nop 参考**: 简单的正则匹配 + prompt 模板选择。可作为 Nop Agent 的前置路由拦截器。

---

### 3.5 Kanban 多 Agent 协作

**来源**: Hermes Agent
**源码**: `hermes_cli/kanban_db.py`

SQLite 7 表 schema，任务状态列 `triage→todo→ready→running→blocked→review→done`。`claim_lock` + `claim_expires` 原子认领。`consecutive_failures` 每任务熔断。

**Nop 参考**: Nop 已有 `nop-wf` 和 `nop-task`，可直接用 DB 事务实现更可靠的版本。

---

### 3.6 Sisyphean 执行模型（"永不放弃"）

**来源**: oh-my-claudecode

**核心**: Stop-hook 拦截退出事件，检查 todo 列表，强制继续执行。与 PilotDeck 的"快速熔断"形成鲜明对比——两种截然不同的弹性哲学。

| 哲学 | 代表 | 行为 |
|------|------|------|
| "永不放弃" | oh-my-claudecode (Sisyphean) | Stop-hook 确保任务完成，at-least-once 语义 |
| "快速熔断" | PilotDeck (Circuit Breaker) | 3 轮失败即终止，fail-fast 语义 |

**Nop 参考**: 可作为配置选项，让用户选择弹性策略。在 biz action 的后置拦截器中实现。

---

### 3.7 Always-on 5 阶段管线

**来源**: PilotDeck
**源码**: `src/always-on/runtime/DiscoveryFire.ts` (1045 行)

**5 阶段**: Discovery → Workspace Preparation → Execution → Report Generation → Cleanup

- Discovery: LLM 分析聊天历史，自主发现可做任务
- Workspace: git worktree 或 snapshot copy 隔离
- 门控: enabled? → 项目存在? → 休眠? → agent 忙? → 日预算?

**Nop 参考**: Nop 已有 `nop-wf` + `nop-task`，可组合实现类似离线任务发现。

---

### 3.8 可组合中间件管道

**来源**: DeepAgents
**源码**: `libs/deepagents/middleware/`

6 个可组合中间件: FilesystemMiddleware, MemoryMiddleware, SkillsMiddleware, SubAgentMiddleware, SummarizationMiddleware, PatchToolCallsMiddleware。

每个中间件可：添加工具、修改 system prompt、拦截 before/after agent 步骤、定义额外 state schema。

`SummarizationMiddleware` 特别值得参考：
- 三种触发器: `("messages", N)`, `("tokens", N)`, `("fraction", F)`
- 模型自适应: 有 `max_input_tokens` 时自动切换到 fraction 模式
- 参数截断独立于摘要（更轻量，可单独生效）
- 摘要消息含后端文件路径，可回溯完整历史

**Nop 参考**: 中间件 → `IBizInterceptor` 链。SummarizationMiddleware 的触发器模型可映射到 Nop 的配置驱动阈值。

---

### 3.9 Provider 精细化重试策略

**来源**: nanobot
**源码**: `nanobot/providers/base.py`

```
两种模式:
  standard: 3 次后退
  persistent: 无限重试，相同错误 10 次后停止

429 精细化:
  rate_limit_exceeded → 可重试 (Retry-After)
  insufficient_quota → 不重试

Retry-After 多源解析:
  HTTP header (retry-after-ms / retry-after)
  + 响应体文本提取
  + 多种时间单位

图片 fallback:
  non-transient → 自动移除图片重试
  成功后永久 strip
```

**Nop 映射**: `IRetryPolicy` 接口 + `RetryPolicyBuilder`。429 语义分类 → `Map<Integer, ErrorKind>`。

---

### 2.10 AgentScope Java — Formatter Pattern（Provider 消息格式转换）

**来源**: AgentScope Java

每种 LLM Provider 有独立的 `Formatter` 将统一消息格式转为 Provider 特定格式。`Model` 接口仅 2 个方法: `stream()` + `getModelName()`。

5 个实现 + Formatter 模式: DashScope, OpenAI, Gemini, Anthropic, Ollama。

**Nop 映射**: 与 Nop 的 `IDialect` 模式高度一致。`IChatModel` 接口 + `IModelDialect` SPI 自动发现 + `ChatDialectManager` 注册。这是 Java 原生的 Provider 抽象方案，可直接参考。

---

### 2.11 Solon AI — Talent 动态准入

**来源**: Solon AI

```java
interface Talent {
    boolean isSupported(Prompt prompt);     // 动态准入
    void onAttach(Prompt prompt);           // 生命周期
    String getInstruction(Prompt prompt);   // 动态指令
    Collection<FunctionTool> getTools(Prompt prompt); // 动态工具
}
```

仅在关键词匹配/上下文分析通过时激活。17 个预构建 Talent (cli, web, file, data, lsp, text2sql...)。

**Nop 映射**: `ITalent` 接口 → Nop 扩展点 (`@XExtension`)。`isSupported` 用 XPL 表达式判断。与 Nop Delta 定制哲学高度一致——上下文依赖的行为激活。

---

## 四、设计模式分组速查

### 按功能域分组

| 功能域 | P0 模式 | P1 模式 | P2 模式 |
|--------|--------|--------|--------|
| **上下文管理** | 1.1 三区域架构, 1.7 五阶段管线 | 2.2 三级压缩, 2.6 持续目标 | 3.8 可组合中间件 |
| **工具系统** | 1.2 修复管线, 1.5 权限派生 | 2.8 Contribution 类型 | — |
| **状态管理** | 1.3 事件溯源, 1.6 状态机 Loop | 2.7 Deferred-Ack Mailbox | 3.5 Kanban |
| **Agent 定义** | 1.4 Agent 即 Schema | — | 3.2 Team Mode |
| **消息抽象** | 1.8 CanonicalMessage | — | 3.3 双总线 |
| **路由/成本** | — | 2.1 Smart Router, 2.3 熔断器 | 3.6 Sisyphean |
| **记忆系统** | — | 2.4 三适配器 Memory, 2.9 Curator | — |
| **安全/护栏** | — | 2.5 Guardrail Pipeline | 3.4 IntentGate |
| **Provider 抽象** | — | 2.10 Formatter, 2.11 Talent | — |
| **工作流/调度** | — | — | 3.1 Workflow DSL, 3.7 Always-on |

### 按实现复杂度分组

| 复杂度 | 模式 | 预估 Java 代码量 |
|--------|------|----------------|
| **低 (<500 行)** | 1.5 权限派生, 1.8 CanonicalMessage, 2.6 持续目标, 3.4 IntentGate | 200-500 行/个 |
| **中 (500-2000 行)** | 1.2 修复管线, 1.6 状态机 Loop, 1.7 五阶段管线, 2.2 三级压缩, 2.3 熔断器, 2.5 Guardrail | 500-1500 行/个 |
| **高 (2000+ 行)** | 1.1 三区域架构, 1.3 事件溯源, 1.4 Agent Schema, 2.1 Smart Router, 2.4 三适配器 Memory, 2.7 Deferred-Ack, 2.9 Curator | 2000-5000 行/个 |

---

## 五、建议实施路径

> 以下按分层架构组织（非时间阶段）。所有接口始终存在，高级能力通过添加接口实现。详见 `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`。

### Layer 1: Agent 内核（低复杂度 P0）

1. **Agent Schema** (1.4) → 用 XDef 定义 Agent 模型
2. **CanonicalMessage** (1.8) → sealed interface
3. **权限派生** (1.5) → 3 源合并算法 (~200 行)
4. **状态机 Loop** (1.6) → XDSL 声明 TurnState
5. **Formatter Pattern** (2.10) → IChatModel + IModelDialect SPI
6. **Talent 动态准入** (2.11) → ITalent 扩展点

### Layer 2: 上下文与工具（中高复杂度 P0）

7. **五阶段管线** (1.7) → Context Governance
8. **修复管线** (1.2) → Tool-Call Repair
9. **三级压缩** (2.2) → 渐进压缩
10. **事件溯源** (1.3) → Event + Reducer 投影
11. **三区域架构** (1.1) → 缓存优化
12. **熔断器** (2.3) → 循环保护

### Layer 3: 路由与记忆（P1）

13. **Smart Router** (2.1) → 成本优化
14. **三适配器 Memory** (2.4) → 记忆系统
15. **Guardrail** (2.5) → 安全护栏
16. **持续目标** (2.6) → 长时任务

### Layer 4: 协作与扩展（P2）

17. **Contribution 类型** (2.8) → 插件系统
18. **Curator** (2.9) → 技能管理
19. **Deferred-Ack** (2.7) → 多 Agent 通信
20. **Sisyphean / Always-on** (3.6/3.7) → 弹性策略 + 离线任务

---

## 六、与 Nop 现有能力的映射

| Agent 设计模式 | Nop 现有能力 | 差距 |
|--------------|------------|------|
| Agent Schema (1.4) | `@XDef` schema 定义 | 需新增 Agent XDef 模型 |
| 状态机 Loop (1.6) | `nop-wf` 工作流引擎 | 需适配 Agent turn 语义 |
| 权限派生 (1.5) | `nop-auth` 权限体系 | 需增加 Agent 级别规则 |
| 事件溯源 (1.3) | `IXdslStore` 事件重放 | 核心契合，需定义事件类型 |
| CanonicalMessage (1.8) | `IXMeta` schema | 需定义消息 XMeta |
| 三区域缓存 (1.1) | `IEvalScope` 不可变作用域 | 需新增缓存指纹计算 |
| 工具修复 (1.2) | `IBizInterceptor` 拦截器链 | 需新增修复拦截器 |
| Smart Router (2.1) | `IGraphQLActionRouter` | 需新增 LLM 路由策略 |
| 三适配器 Memory (2.4) | `IEntityDao` + 向量存储 | 需新增 Memory 门面 |
| Guardrail (2.5) | `IBizInterceptor` | 需新增流式拦截器 |
| Checkpoint 持久化 | Nop ORM 多 DB 支持 | 可参考 Spring AI Alibaba 6 后端模式 |
| Curator (2.9) | Delta 定制 + 定时任务 | 需新增技能状态管理 |
| Formatter (2.10) | `IDialect` SPI | 直接参考 AgentScope Java |
| Talent (2.11) | Delta 定制 + XPL | 直接参考 Solon AI |

---

## 七、关键数据点速查

| 指标 | 数值 | 来源 |
|------|------|------|
| 缓存命中率 | 99.82% | Reasonix 三区域架构 |
| 成本节省 | ~77% | PilotDeck Smart Routing |
| 权限派生代码量 | 35 行 | OpenCode |
| Agent Loop 核心代码量 | ~2400 行 | nanobot |
| 压缩 Tier 1 阈值 | 1536 bytes | PilotDeck MicroCompaction |
| 压缩 Tier 2 head/tail | 2/4 turn | PilotDeck SnipEngine |
| 压缩 Tier 3 保留尾部 | 35% | PilotDeck CompactionEngine |
| 熔断阈值 | 3 次连续失败 | nanobot / PilotDeck |
| 熔断冷却 | 60 秒 | nanobot |
| Storm 窗口 | 6 条, ≥3 重复抑制 | Reasonix |
| Tool Result 截断 | 8000 tokens (dispatch + turn-end shrink 统一阈值) | Reasonix (源码: mcp/registry.ts:46, DEFAULT_MAX_RESULT_TOKENS) |
| Token 预算告警 | 80% warning, 95% blocking | PilotDeck |
| 事件类型数 | 27 种 | Reasonix |
| 投影视图数 | 7 个 | Reasonix |
| Sustained Goal 续接 | 最多 12 轮 | nanobot |

---

*参考来源: `ai-dev/analysis/agent-survey/` 下 21 篇分析文档 + 6 个框架的源码级深度分析*
