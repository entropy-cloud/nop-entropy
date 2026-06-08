# Agent Todo 机制调研：5 框架对比与 Nop 设计建议

> Status: done
> Date: 2026-06-07
> Scope: nop-ai-agent Todo 子系统设计输入
> Conclusion: Replace-All 模式 + 独立工具 + `{content, status}` 三状态 + 每个 Agent 独立 todo 列表。已落地到 `update-todos.tool.xml`。

## Context

nop-ai-agent 需要一个 Todo 工具，让 Agent 在多步骤任务中跟踪进度、组织工作、向用户展示状态。这是 roadmap 中的明确下一步。当前 baseline 中只提到"Todo 系统是 Plan 的轻量级补充，属于单个 Agent"（`01-architecture-baseline.md` §Plan 与 Todo 系统），但没有详细设计。

本次调研覆盖 5 个框架的 Todo/Task 机制实现，提取设计模式、权衡取舍，为 Nop 的 Todo 子系统设计提供输入。

## 调研对象

| 框架 | 实现方式 | 调研文件 |
|------|----------|----------|
| **opencode** (v1 + v2 core) | `todowrite` 工具 + SQLite 持久化 + 事件发布 | `packages/opencode/src/tool/todo.ts`, `packages/core/src/session/todo.ts`, `packages/opencode/src/tool/todowrite.txt` |
| **oh-my-claudecode** | Stop-hook 拦截 + Todo/Task 双系统检查 + 强制继续 | `src/hooks/todo-continuation/index.ts` |
| **cline** | `task_progress` 参数（嵌入每个工具调用） + Markdown checklist | `src/core/prompts/system-prompt/components/task_progress.ts` |
| **agent-zero** | `TaskScheduler` + 3 种任务类型 (AdHoc/Scheduled/Planned) + 文件持久化 | `helpers/task_scheduler.py` |
| **Claude Code 原生 Task** | TaskCreate/TaskUpdate 工具 + 每会话文件系统 | oh-my-claudecode 中 `Task` 接口定义 |

## 调研结果

### 1. opencode：Replace-All 模式（基线参考）

**核心设计**：
- Todo 作为**独立工具**暴露给 LLM（`todowrite`）
- 参数是**完整替换**：每次调用传入整个 todo 列表，服务端 delete-all + insert-all
- 数据模型极简：`{ content, status, priority }` + 隐含 position（数组序号）
- 持久化：SQLite 表 `(session_id, content, status, priority, position)`
- 事件驱动：更新后发布 `todo.updated` 事件，UI 实时刷新

**关键规则**（来自 `todowrite.txt` 工具描述）：
- 3+ 步骤才使用，简单任务跳过
- 恰好一个 `in_progress`，完成后立即标记 `completed`
- `completed` 仅在**实际完成验证后**标记，不能基于意图
- 被阻塞时保持 `in_progress` 并新增一条描述阻塞的 todo
- 用户原始命令原样保留

**权限控制**：
- 子 agent 默认 deny `todowrite`，防止递归（opencode v1 analysis 中提到）
- 权限检查通过 `assertPermission` gate

**v2 变化**：
- v1 (`packages/opencode/`) 用 Zod Schema，v2 core (`packages/core/`) 用 Effect Schema——两个包并存是因为 v2 是渐进重写，v1 的 Zod schema 仍用于 TUI 侧
- 持久化逻辑完全相同（delete-all + insert-all 事务）
- 事件系统升级到 EventV2
- v2 core 的 `SessionTodo.Info` 与 v1 的 `Todo.Info` 字段完全相同（content/status/priority），Schema 库的差异对 Nop 设计无影响

**优点**：
- 实现极简（核心 ~90 行）
- LLM 理解成本低（一个工具，一个数组参数）
- 原子性保证（事务替换）

**缺点**：
- 无 id 字段（v1），无法引用特定 todo 项
- 无阻塞/依赖关系
- 无分层/分组
- 每次 LLM 必须返回完整列表（token 开销随列表增长）

### 2. oh-my-claudecode：Sisyphean 强制继续（弹性哲学对立面）

**核心设计**：
- **不是独立的 Todo 工具**，而是 Stop-hook 层的"继续策略"
- Agent 停止时，hook 检查是否有未完成 todo/task，有则注入继续提示
- 支持**双系统**：Claude Code 原生 Task 系统 + legacy Todo 文件
- 优先级：Task 系统 > legacy todo

**Stop-hook 过滤链**（哪些停止应该被拦截，哪些不应该）：
```
isUserAbort → 跳过（用户主动停止）
isExplicitCancelCommand → 跳过（/cancel --force）
isContextLimitStop → 跳过（上下文溢出，不能阻止 compact）
isRateLimitStop → 跳过（429 无限重试循环）
isAuthenticationError → 跳过（401/403）
isScheduledWakeupStop → 跳过（计划唤醒，不是真正的停止）
isOversizeToolResultRedirectStop → 跳过（大工具结果重定向的合成停止）
→ 其他 → 检查未完成 todo，有则继续
```

**Task 系统**（Claude Code 原生）：
- 每个任务一个 JSON 文件：`~/.claude/tasks/{sessionId}/{taskId}.json`
- 支持 `blocks` / `blockedBy` 依赖
- 支持 `activeForm`（HUD 显示当前工作描述）
- 状态：`pending | in_progress | completed | deleted`

**优点**：
- "永不放弃"哲学：自动检测未完成工作并强制继续
- 多层过滤避免死循环
- Task 系统支持依赖关系

**缺点**：
- 高度耦合 Claude Code 的 Stop-hook 机制
- 主文件 766 行（另有 3 个测试文件 + HUD 渲染文件），大量防御性代码
- 对"正常完成但 todo 未清理"的情况处理不明确

### 3. cline：嵌入式参数模式（非独立工具）

**核心设计**：
- Todo **不是独立工具**，而是**部分工具调用的可选参数**（`act_mode_respond`、`attempt_completion` 等有此参数，`execute_command` 等没有）
- `task_progress` 参数：Markdown checklist 格式 (`- [ ] / - [x]`)，**语义是完整状态快照**（"after this tool use is completed"，不是剩余待办）
- PLAN MODE → ACT MODE 切换时**必须**创建 checklist

**完整数据流**：
```
提交给 LLM: system prompt(规则) + 触发 prompt(动态提醒) + 当前 checklist(prompt 注入)
                    ↓
LLM 返回:    工具调用参数中附带修改后的完整 checklist
                    ↓
服务端:      拦截 task_progress → 存到 taskState + 写 Markdown 文件 + 发 UI
              执行工具 → 返回正常结果给 LLM（不含 checklist）
              下次通过 prompt 注入最新 checklist
```

**触发型 prompt**（动态注入到 user message 中，控制 LLM 何时创建/更新）：
- PLAN→ACT 切换：`"task_progress CREATION REQUIRED - 你必须在下一次工具调用中创建 checklist"`
- 每隔 N 次 API 调用未更新：`"You've made X API requests without task_progress"`
- 全部完成：`"All items completed! Use attempt_completion"`

**LLM 返回示例**（task_progress 在工具调用参数中，不在执行结果中）：
```xml
<execute_command>
<command>npm install react</command>
<requires_approval>false</requires_approval>
<task_progress>
- [x] Set up project structure
- [x] Install dependencies
- [ ] Create components
- [ ] Test application
</task_progress>
</execute_command>
```

**关键差异**：
- 不需要专门的 todo 工具调用（节省一次工具调用 round-trip）
- `task_progress` 是只写参数：LLM 写入，服务端拦截消费，**不回传给 LLM**——下次通过 prompt 注入
- 与 opencode Replace-All 的语义完全相同：LLM 每次提交完整列表，服务端只做存储。区别仅在传输方式

**优点**：
- 零额外工具调用开销
- 进度与行动天然绑定
- 简单直观（Markdown checklist）

**缺点**：
- 依赖模型厂商支持"自定义工具参数"的能力
- 无法独立查询/展示 todo 状态（必须等工具调用）
- 有持久化：服务端写入 Markdown 文件 + `taskState`，可通过 `openFocusChainFile` 打开编辑
- 格式松散（纯文本 checklist，无结构化查询）

### 4. agent-zero：重量级任务调度器（自治 Agent 场景）

**核心设计**：
- 3 种任务类型：`AdHoc`（即时）、`Scheduled`（cron 定时）、`Planned`（时间点列表）
- 完整生命周期：`IDLE → RUNNING → (SUCCESS → IDLE) | (ERROR → ERROR)`
- 每个任务有独立 `context_id`（会话），可复用或隔离
- 文件持久化：`usr/scheduler/tasks.json`
- 线程安全：`threading.RLock` + `DeferredTask`

**TaskPlan 子模型**：
```python
todo: list[datetime]      # 待执行时间点
in_progress: datetime | None  # 当前执行中
done: list[datetime]       # 已完成
```

**特点**：
- 任务有 `system_prompt` + `prompt`，每次运行启动 Agent 实例
- 支持 attachment（文件/URL）
- 支持项目隔离（project_name, project_color）
- 有取消、重试、状态一致性校验
- 1278 行，远超简单 todo 列表

**优点**：
- 完整的任务调度能力（cron、计划执行）
- 自治场景下的可靠执行
- 独立上下文隔离

**缺点**：
- 复杂度极高，不适合交互式编程 Agent
- 无 LLM 直接操作的 todo 工具（由 scheduler 驱动）
- 持久化方式简陋（单 JSON 文件 + thread lock）

### 5. Claude Code 原生 Task 系统（文件系统 + 依赖图）

**核心设计**（从 oh-my-claudecode 的 Task 接口推断）：
- 每个任务独立 JSON 文件 + `.lock` 文件防止并发写
- 支持 `blocks` / `blockedBy` 依赖图
- 4 种状态：`pending | in_progress | completed | deleted`
- 额外字段：`subject`, `description`, `activeForm`

**与 opencode todo 的对比**：

| 维度 | opencode Todo | Claude Code Task |
|------|--------------|------------------|
| 存储 | SQLite 表 | 文件系统（每任务一文件） |
| 依赖 | 无 | blocks/blockedBy |
| ID | 无 | 有 |
| 活动描述 | 无 | activeForm |
| 删除 | cancelled 状态 | deleted 状态 + 文件删除 |
| 可视化 | 终端 HUD | TUI sidebar |

## 模式提取

### Pattern A: Replace-All（opencode）
- LLM 每次提交完整列表，服务端原子替换
- 适合：LLM 驱动、列表规模可控（<30 项）
- 关键：必须事务化（delete + insert），防止中间状态

### Pattern B: Stop-Hook Continuation（oh-my-claudecode）
- Agent 停止时自动检查未完成项，强制继续
- 适合：自治/长时间运行 Agent
- 关键：必须过滤"合理停止"（上下文溢出、限流、用户主动取消）

### Pattern C: Embedded Parameter / Prompt-Driven Checklist（cline）
- Todo 作为工具调用的附加参数，不单独暴露
- 规则通过 system prompt + 触发型 prompt 注入（prompt 约束），不是工具 schema 约束
- **语义与 Pattern A 完全相同**：LLM 每次提交完整 checklist 快照，服务端只做存储
- 适合：想减少工具调用开销的场景
- 关键：依赖模型支持自定义参数；本质是 "prompt 约束 vs schema 约束" 的选择
- 与 Pattern A 的根本区别仅是**传输通道**：A 用独立工具调用，C 挂载在其他工具调用上；两者都是 Replace-All 语义
- 与 Pattern A 的**约束机制差异**：A 用工具 schema（结构化，可程序化验证），C 用 prompt（灵活，不可程序化验证）

### Pattern D: Scheduled Dispatcher（agent-zero）
- 完整的任务调度器，独立于 Agent 循环
- 适合：自治 Agent 的定时/计划执行
- 关键：与 Agent 生命周期解耦

### Pattern E: File-per-Task with Dependencies（Claude Code）
- 每任务一个文件，支持依赖图
- 适合：需要细粒度并发控制和依赖管理
- 关键：文件锁防止并发写

## 与 Nop 的关联分析

### 已有设计决策的约束

1. **Todo 属于单个 Agent**（baseline 已定）：不需要跨 Agent 的任务共享
2. **Plan 系统已存在**：Todo 是 Plan 的轻量级补充，不是替代
3. **Sisyphean 策略已设计**（reliability doc）：Stop-hook 检查 todo 列表，与 oh-my-claudecode 的思路一致
4. **Layer 归属**：Todo 工具属于 Layer 2 Execution（与 Hook、Tool 同层），持久化属于 Layer 4 Platform

### 关键设计问题

#### Q1: Replace-All vs Item-level CRUD？

**Replace-All 优点**（opencode 模式）：
- 实现简单，一次调用解决
- LLM 天然适合"给我完整列表"模式
- 原子性好

**Item-level CRUD 优点**（Claude Code Task 模式）：
- 支持依赖关系
- 支持并发修改（多个子 agent 各自操作不同 task）
- 支持独立查询和事件通知

**建议**：**Replace-All 作为主模式**，数据模型 `{id, content, status, priority}`，不包含 `dependsOn`。原因：
- Nop 当前场景是交互式编程 Agent（类似 opencode），不是自治 Agent
- Replace-All 与 Sisyphean 策略天然配合（检查未完成 = 检查 status != completed）
- 子 agent 默认无 todo 写权限，不需要并发修改
- `dependsOn` 在 Replace-All 模式下无意义：LLM 每次提交完整列表，依赖关系由 LLM 自己在 content 中表达（如 "完成步骤 2 后再执行此项"），不需要结构化字段。如果将来需要 Item-level CRUD（例如子 Agent 并发操作），`dependsOn` 应在那时引入

**`id` 字段的定位**：Replace-All 模式下 `id` 不是用于跨调用引用，而是用于事件通知和 HUD 渲染（UI 需要知道"哪个项状态变了"）。opencode v1 无 id、v2 core 也未加 id，但 Claude Code Task 系统的实践表明 id 对 UI 友好。Nop 预留 id 但不在 Replace-All 的业务逻辑中使用。

#### Q2: 独立工具 vs 嵌入式参数？

**cline 的嵌入式模式不适合 Nop**：
- Nop 的工具调用通过 IToolExecutor 接口，所有工具参数由 tool.xml schema 定义
- 嵌入式参数要求修改每个工具的 schema，违背接口隔离原则
- NopAgent 的工具调用循环不依赖特定模型能力

**opencode 的独立工具模式适合 Nop**：
- 符合 IToolExecutor 接口
- 可通过 IoC/Delta 注册或替换
- 权限控制通过 IPermissionProvider 统一管理

**关键发现：Nop 的 parallel tool calls 使嵌入式参数完全无价值**

cline 嵌入式参数的唯一优势是"省一次 round-trip"。但 Nop 已支持单次 LLM 响应中调用多个工具（parallel tool calls），LLM 可以在同一次响应中同时调用 `todowrite` + 其他工具：

```
LLM 一次返回:
  tool_call_1: todowrite([{content: "Install deps", status: "completed"}, ...])
  tool_call_2: execute_command("npm install react")
```

这和 cline 的嵌入式参数在 round-trip 成本上**完全等价**——都是一次 LLM 调用完成"更新 todo + 执行动作"。

而嵌入式参数的代价不小：
- 要给每个工具的 schema 加 `task_progress` 参数，违背接口隔离原则
- 依赖模型厂商支持自定义工具参数
- 规则只能通过 prompt 约束，无法程序化验证

结论：**独立 `todowrite` 工具 + parallel tool calls = 最简实现 + 零额外开销 + 无设计妥协**。Pattern C（嵌入式参数）对 Nop 无采纳价值。

#### Q3: 持久化策略？

- **opencode**：SQLite 表，按 session_id 隔离
- **oh-my-claudecode**：文件系统，按 session_id 目录隔离
- **agent-zero**：单 JSON 文件

**Nop 的选择**：
- Layer 4 Platform 层的 IMemoryAdapter / IMessageService 已预留持久化抽象
- Todo 持久化应跟随 Session 存储策略（内存 for 测试，DB for 生产）
- 具体存储实现是 Layer 4 的设计范围

#### Q4: Todo 与 Plan 的边界？

当前 baseline 定义：
- **Plan**：结构化执行计划，步骤+依赖+完成条件
- **Todo**：轻量级待办列表，Plan 的补充

调研后发现两者的**核心区别**在于抽象级别和生命周期：
- Plan 是**高抽象级别**的（"做什么"），生命周期**跨整个任务**（创建后可能被修改——发现新需求、调整步骤顺序），但变更频率较低
- Todo 是**低抽象级别**的（"当前在做什么"），生命周期**实时变化**（每完成一步就更新），变更频率极高

两者的区别不是"预定义 vs 增量"（Plan 也会增量修改），而是"战略 vs 战术"：

建议的分工：
```
Plan = 战略层：做什么、为什么、顺序依赖（较稳定，变更需谨慎）
Todo = 战术层：当前每一步的执行状态（实时变化，高频更新）
```

这个分工与 Sisyphean 策略的关系：Sustainer 检查 Todo 的未完成项来决定是否继续，不直接检查 Plan——Plan 是目标，Todo 是进度。

#### Q5: Sisyphean 策略的过滤链？

oh-my-claudecode 的 7 层过滤链很有参考价值。Nop 的 Sisyphean 策略（`nop-ai-agent-reliability.md`）已提到"stop-hook + todo 检查强制继续"，但未定义过滤链。

建议 Nop 至少需要过滤：
1. 用户主动停止（不应继续）
2. 上下文溢出（需要 compact，不是继续）
3. 限流/认证失败（外部错误，不是工作未完成）
4. Agent 正常退出且 todo 已清空（正常结束）

这与已有的 ICircuitBreaker / ISustainer 互斥选择一致。

## 推荐方案摘要

| 维度 | 推荐 | 参考 |
|------|------|------|
| 操作模式 | Replace-All（整体替换） | opencode |
| 暴露方式 | 独立 IToolExecutor 工具（不是新 Layer 接口）+ parallel tool calls 消除额外 round-trip | opencode |
| 工具名 | `update-todos`（已有 tool.xml，命名符合项目规范） | nop-ai-toolkit 现有 |
| 参数名 | `todos`（不用 `task_progress`——"progress"对 LLM 歧义大，需大量 prompt 补救） | opencode |
| 数据模型 | `{content, status}` 仅两字段（无 id、无 priority、无 dependsOn） | opencode + 数据验证 |
| 状态机 | `pending → in_progress → completed`（3 状态，无 cancelled） | opencode + 数据验证 |
| 约束 | 恰好一个 in_progress；不再需要的任务直接移除 | opencode todowrite.txt |
| 持久化 | 跟随 Session 存储策略（IMemoryAdapter） | — |
| Sisyphean 集成 | ISustainer 检查非 completed 项，in_progress 指导续接 | oh-my-claudecode |
| 子 agent 权限 | 默认 deny todo 写 | opencode |
| Layer 归属 | 工具注册在 IToolExecutor 下，无独立 Layer 接口；持久化 Layer 4 | baseline |

## opencode 生产数据验证（SQLite 统计）

数据来源：`~/.local/share/opencode/opencode.db`，1308 个会话共 9091 条 todo 记录。

### priority 字段：85.5% 是 high，无区分度

| priority | 数量 | 占比 |
|----------|------|------|
| high | 7344 | **85.5%** |
| medium | 1162 | 13.5% |
| low | 84 | **1.0%** |

LLM 几乎把所有任务都标为 high。priority 不提供实际区分信息，数组顺序已隐含优先级。

**结论：删掉 priority。**

### cancelled 状态：仅占 0.2%

| status | 数量 | 占比 |
|--------|------|------|
| completed | 7909 | 92.1% |
| pending | 473 | 5.5% |
| in_progress | 191 | 2.2% |
| cancelled | 18 | **0.2%** |

1308 个会话中 cancelled 仅 18 条。LLM 处理"不再需要的任务"的方式是直接从列表中删除，不标记 cancelled。

**结论：删掉 cancelled，不需要的任务直接移除。**

### 最终设计的数据支撑

| 设计决策 | opencode 生产数据验证 |
|---------|---------------------|
| 删掉 priority | 85.5% high，无区分度 |
| 删掉 cancelled | 仅 0.2% 使用率，LLM 更倾向直接删除 |
| 保留 in_progress | 占 2.2%（191 条），Sisyphean 续接需要"当前正在做哪个" |
| 保留 pending/completed | 92.1% completed + 5.5% pending，核心状态 |
| Replace-All 模式 | opencode 的 delete-all + insert-all 事务，9091 条记录无数据问题 |

## 多 Agent 场景下的 Todo 行为（opencode SQLite 实证 + 源码分析）

### 数据

| 指标 | 值 |
|------|-----|
| 子 agent 会话总数 | 4915 |
| 有自己 todo 的子 agent | 605 (12.3%) |
| 父 agent todo 总量 | 4473 |
| 子 agent todo 总量 | 4118 |
| 子 agent todo priority 分布 | 87.0% high, 12.1% medium, 0.8% low |

### 关键发现

1. **子 agent 有独立 todo 列表**：每个子 agent session 有自己的 todo，与父 agent 的 todo 完全独立
2. **父 agent todo = 调度层**：跟踪"派了哪个子 agent"或"哪个 wave 完成"（如 "Wave 0-5: Baseline tests"）
3. **子 agent todo = 执行层**：跟踪具体步骤（如 "Explore existing code" → "Create GraphExecutionPlan" → "Build and verify"）
4. **父子 todo 无联动**：父 agent 不读子 agent 的 todo，子 agent 通过 task 工具返回值汇报结果，父 agent 根据结果更新自己的 todo
5. **多子 agent 并行无冲突**：因为每个 agent 的 todo 列表完全隔离，并行子 agent 之间互不影响

### opencode task 工具的调度模式（源码：`task.ts`）

opencode 的 `task` 工具有两种运行模式（源码 338 行）：

**Foreground（默认）**：阻塞等待子 agent 完成，返回结果后才继续父 agent 下一轮。

```typescript
// task.ts:295-325 — 默认模式，阻塞等待
const result = yield* Effect.raceFirst(
  background.wait({ id: nextSession.id }),
  background.waitForPromotion(nextSession.id),
)
```

**Background（实验性，需 `OPENCODE_EXPERIMENTAL_BACKGROUND_SUBAGENTS=true`）**：立即返回，子 agent 完成后通过 `inject` 把结果注入父 agent 下一轮对话。

```typescript
// task.ts:283-286 — 后台模式，立即返回
if (runInBackground) {
  yield* notify(info.id)
  return backgroundResult()  // 立即返回，不等待
}
```

### 实际数据中的并行模式

SQLite 数据显示，并行子 agent 有两种来源：

**来源 1：LLM 在一个 response 中并行调用多个 task 工具**（parallel tool calls）

```
Phase 2.1~2.4 四个子 agent在 74 秒内全部启动（并行），各自运行 410~1100 秒：

Phase 2.1: RuntimeException → NopException      start=0s,  end=417s
Phase 2.2: 补全空标记接口                         start=25s, end=1102s
Phase 2.3: 提取 DigestHelper 工具类               start=54s, end=1067s
Phase 2.4: countLines + 小修复                    start=74s, end=484s

→ 四个子 agent 重叠运行约 400 秒（并行），父 agent 等全部返回后才进入下一轮
```

**来源 2：Background 模式（较少使用）**

数据中大量子 agent 对在 5ms~10s 内启动，典型的 parallel tool calls 模式。Background 模式在源码中标记为实验性，实际使用较少。

### Todo 与子 agent 的对应关系：**没有 1:1 映射约束**

实测数据中，父 agent 的 todo item 和子 agent 之间有以下对应模式：

| 父 agent todo item | 对应子 agent 数量 | 案例 |
|---------------------|-------------------|------|
| "子 agent 审查开发计划直到达成共识" | 2 个并行（architect-reviewer + code-quality-reviewer） | 1 条 todo → 多个子 agent |
| "Phase 1 Step 1.1：单文件大纲 Query" | 1 个 | 1 条 todo → 1 个子 agent |
| "Wave 0-5: Baseline tests + core implementations" | 3~5 个（跨多个 wave 的子 agent） | 1 条 todo → 多个子 agent |
| "全量 mvn test 验证" | 0 个（父 agent 自己执行） | 1 条 todo → 0 个子 agent |

**结论：todo item 不表示"我派了哪个子 agent"，而是"我要完成什么事"。** 粒度完全由 LLM 自行决定。

### Replace-All 模式下的并行更新时序

opencode 的 Replace-All（delete-all + insert-all）模式下：
- 所有 todo 的 `time_created` 相同（整个列表在一次事务中写入）
- `time_created == time_updated` 的情况占绝大多数（只写入一次，无增量变更历史）
- 并行子 agent 全部返回后，父 agent 在下一轮一次性 Replace-All 更新多个 item 为 completed

这意味着 **Replace-All 模式天然支持并行场景**：不需要追踪每个 item 的独立状态变更，只需在子 agent 全部返回后一次性更新整个列表。

### 对 Nop 设计的结论

1. **不需要为多 Agent 场景做额外设计**。每个 Agent 持有自己的 todo 列表：
   - Sisyphean 对子 Agent 独立工作——子 Agent 有未完成 todo 时自行继续
   - 父 Agent 等待子 Agent 通过 `call-agent` 返回值汇报结果，然后更新自己的 todo
   - 无需共享、合并、或跨 Agent 聚合 todo

2. **`call-agent` 工具应支持两种模式**：
   - Foreground（默认）：阻塞等待子 agent 返回，适合有依赖关系的串行任务
   - Background（可选）：立即返回，子 agent 完成后异步注入结果，适合独立的并行任务

3. **Todo 不是调度器**。它只是 LLM 的工作清单，串行/并行策略由 LLM 通过 tool call 时序自行决定：
   - 串行 = 逐个调用 `call-agent`，每次等返回
   - 并行 = 一个 LLM turn 中同时调用多个 `call-agent`（parallel tool calls）
   - 后台 = `call-agent(background=true)` 立即返回，父 agent 继续其他工作

4. **Todo item 与子 agent 没有 1:1 映射约束**。一个 todo item 可以对应 0 个子 agent（父 agent 自己做）、1 个子 agent、或多个并行子 agent。粒度由 LLM 自行决定。

5. **不需要 `dependsOn` 或执行策略字段**。opencode 实证表明 LLM 通过 content 文本描述 + tool call 时序完全足以表达串行/并行关系。

## Open Questions

- [ ] Todo 是否需要 `activeForm` 字段（用于 HUD 显示"正在做什么"）？Claude Code Task 有此字段，opencode 无。
- [x] ~~`dependsOn` 字段在 Replace-All 模式下是否有意义？~~ → **已决**：Replace-All 模式下无意义。LLM 在 content 中表达顺序，不需要结构化字段。如果将来切换到 Item-level CRUD 则重新评估。
- [x] ~~多 Agent 并行时 Todo 如何隔离？~~ → **已决**：opencode 实证表明每个 Agent 独立 todo 列表完全可行，无需跨 Agent 聚合。
- [x] ~~Todo item 与子 agent 是否需要 1:1 映射？~~ → **已决**：不需要。实测数据中 1 条 todo 可对应 0~N 个子 agent，粒度由 LLM 决定。Todo 表示"要完成什么事"，不是"派了谁"。
- [x] ~~并行子 agent 场景下 todo 如何更新？~~ → **已决**：Replace-All 模式天然支持——并行子 agent 全部返回后，父 agent 一次性 Replace-All 更新整个列表。不需要追踪每个 item 的独立变更。
- [x] ~~Nop 的 call-agent 是否需要 background 模式？~~ → **已决**：需要。opencode 源码证实 foreground+background 双模式是并行调度的核心。Foreground 为默认（阻塞等待），Background 为可选（立即返回 + 异步注入结果）。
- [ ] Todo 列表的最大长度限制？（opencode 未限制，但 token 开销随列表增长）
- [ ] Todo 与 IGoalTracker（Layer 3）的关系？IGoalTracker 管理长期目标，Todo 管理即时任务——是否需要关联？
- [ ] 多轮对话中 Todo 的跨 Session 持久化需求？

## 未覆盖的框架

以下框架有任务管理机制但因时间限制未深入调研：
- **OpenAI Codex CLI**：有 `collaboration-mode-templates`（plan/execute 模板），任务管理通过模板驱动
- **AutoGPT Platform**：有 Todoist 集成和 copilot 工具系统，偏向自动化工作流
- **agno (multi-agent)**：可能有 Agent 级别的任务分配机制

## References

- `ai-dev/design/nop-ai-agent/01-architecture-baseline.md` — Plan 与 Todo 系统
- `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` — Sisyphean 策略
- `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` — Layer 组织
- `ai-dev/analysis/2026-06-07-agent-design-patterns-for-nop.md` — 28 模式分析
- `~/ai/opencode/packages/opencode/src/tool/todo.ts` — opencode todowrite v1 实现
- `~/ai/opencode/packages/opencode/src/tool/task.ts` — opencode task 工具实现（foreground/background 双模式）
- `~/ai/opencode/packages/core/src/session/todo.ts` — opencode todowrite v2 core
- `~/ai/opencode/packages/opencode/src/tool/todowrite.txt` — opencode 工具描述（使用规则）
- `~/ai/oh-my-claudecode/src/hooks/todo-continuation/index.ts` — oh-my-claudecode 强制继续 hook
- `~/ai/cline/apps/vscode/src/core/prompts/system-prompt/components/task_progress.ts` — cline 嵌入式参数
- `~/ai/agent-zero/helpers/task_scheduler.py` — agent-zero 任务调度器

> **注**：`~/ai/` 下的文件是调研时在宿主机器上的源码位置，不在 nop-entropy 仓库内。
