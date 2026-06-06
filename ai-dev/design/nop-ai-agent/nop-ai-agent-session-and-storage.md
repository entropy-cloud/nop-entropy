# Nop AI Agent 会话与存储设计

## 1. 目标

本篇定义 `nop-ai-agent` 的会话、持久化和文件布局设计，回答下面问题：

1. Agent 会话需要保存哪些状态
2. 会话如何延续、加载、分叉和压缩
3. 文件存储在第一阶段承担什么角色
4. 哪些存储约束应该作为实现前提固定下来

本篇只讨论本地会话与文件存储，不讨论分布式会话存储。

## 2. 设计定位

当前阶段建议把 `.nop/` VFS 路径作为 Agent 可见的逻辑接口，底层存储可替换。

也就是说：

- `.nop/` 是 VFS 逻辑路径，Agent 通过统一接口读写，不感知底层存储
- Phase 1 底层可用文件系统实现（JSONL + JSON + 文件）
- Phase 2+ 底层可替换为数据库（IOrmSession），Agent 无感
- Plan 对外发布按 AGE 规范写入 `docs/plans/`，与 VFS 内部存储分离

## 3. Session 的角色

Session 表示一次 Agent 会话的持久化上下文。它至少需要承载：

- 会话标识
- 消息历史
- plan 状态
- 压缩记录
- 父子会话关系
- 调试和错误日志引用

Session 不是运行时本身，但运行时通常会依赖它来完成上下文延续和恢复。

## 4. 逻辑存储布局（VFS）

> **核心概念**：`.nop/` 是 **VFS（虚拟文件系统）逻辑路径**，不是物理文件系统。AI Agent 通过 `.nop/` 路径访问数据，运行时引擎负责映射到实际存储（Phase 1 可以是文件，Phase 2+ 可以是数据库）。Agent 只看到逻辑路径，不感知底层存储实现。

### 4.1 Session 级存储

```text
.nop/sessions/{sessionId}/
  events          ← Event Log (source of truth, append-only)
  snapshot        ← 派生快照 (可选, 用于快速加载)
  out/{ts}.log    ← 工具标准输出归档
  error/{ts}.log  ← 错误过程记录
```

语义：

- `events`
  - Append-only event log，所有状态变更（消息、compaction）追加到此
  - 是 session 状态的 source of truth（见 §5）
- `snapshot`
  - 从 event log 派生的快照缓存，可按需重建
- `out/`、`error/`
  - 工具执行输出和错误归档

### 4.2 Plan 存储（跨 Session）

**Plan 是项目级实体，不属于任何单个 session。** Plan 跨 session 存在：一个 plan 可能在 session A 中创建，在 session B 中继续执行。

Plan 不存放在 `.nop/sessions/` 下，而是有独立的项目级存储路径（见 §6）。

Session 通过 planId 引用 plan，但不拥有 plan 的生命周期。

**存储后端映射**：

| Phase | `events` | `snapshot` | Agent 感知 |
|-------|---------|-----------|-----------|
| Phase 1 | JSONL 文件 | JSON 文件 | 无（Agent 只见 VFS 路径） |
| Phase 2+ | DB 表（IOrmSession） | DB 表 / 缓存 | 无（Agent 只见 VFS 路径） |

Agent 通过 VFS 接口（`IVfsAgentStore` 或类似）读写上述路径，不直接操作文件或数据库。

## 5. 数据模型：Event Log 为 Source of Truth

**决策**：Append-only event log 是 source of truth。派生快照（如保留）是从 event log 派生的缓存，用于快速加载。

**理由**（基于 10 框架调研）：
- Reasonix 使用 `.jsonl` 消息 + `.events.jsonl` 事件 + 7 个纯函数 Reducer，支持完整 Event Sourcing
- OpenCode 使用 SQLite + 消息增量写入 + compaction 边界重建
- pi-agent 使用 JSONL 树（9 种 entry type），从 CompactionEntry 重建上下文
- 纯 JSON 快照模型（`session.json` 含 inline messages）在 crash recovery 和 compaction 边界管理上不如 Event Sourcing 稳健

**Phase 1 实现策略**：
- Event log 以 JSONL 格式 append-only 写入（每次 ReAct 迭代或状态变更追加一条）
- 派生快照可作为**派生缓存**，从最近 CompactionEntry + 保留消息重建
- 不需要存储每个 snapshot 的完整副本（增量日志 + 按需重建）

### 5.1 Event Log Entry 类型

| Entry 类型 | 内容 |
|-----------|------|
| `session_header` | sessionId, agent, createdAt, parentSession, planId (引用) |
| `message` | user/assistant/tool 消息（含 toolCalls, toolCallId, think 等） |
| `compaction` | 压缩边界：type, summary, firstKeptEntryId, tokensBefore, tokensAfter |
| `state_change` | Actor 状态转换（created/ready/running/idle/failed/stopped） |

### 5.2 CompactionEntry 结构

比前期设计的 `{snapshotId, timestamp}` 更丰富：

```json
{
  "type": "compaction",
  "id": "cmp-001",
  "timestamp": "2026-06-06T10:05:00Z",
  "compactionType": "full",
  "compactionLayer": 3,
  "firstKeptEntryId": "msg-042",
  "tokensBefore": 95000,
  "tokensAfter": 28000,
  "summary": "...",
  "retainedMessageCount": 8
}
```

### 5.3 Session 重建算法

从 CompactionEntry 重建活跃上下文：

1. 定位最近的 CompactionEntry
2. 加载其 summary 内容
3. 加载 firstKeptEntryId 之后的所有保留消息
4. 组合为活跃上下文：[system_msg, summary, ...retained_messages]

## 6. Plan 存储（跨 Session）

**Plan 是项目级实体，不属于任何 session。**

- Plan 跨 session 存在：session A 创建 plan，session B 可以继续执行
- Plan 有且仅有一个存储，不在 `.nop/sessions/` 下
- Session 通过 `planId` 引用 plan，记录在 `session_header` entry 中
- Session 关闭或销毁不影响 plan 的生命周期

### 6.1 存储位置

Plan 按 AGE 规范存储在项目级目录：

```
docs/plans/{planId}-{title}.md
```

- 遵循 AGE（Attractor-Guided Engineering）目录约定
- 文件名：`{planId}-{title}.md`（如 `001-initial-react-engine.md`）
- 格式为 Markdown，遵循 AGE Plan Template（见 AGE `docs/plans/00-plan-authoring-and-execution-guide.md`）
- 运行时引擎通过 VFS 接口管理 plan 的读写，Agent 不直接操作文件

### 6.2 Session 与 Plan 的关系

```
Session (引用) ──planId──→ Plan (独立存在)
Session A ──planId=p001──→ Plan p001 (创建)
Session B ──planId=p001──→ Plan p001 (继续执行)
Session C ──planId=p002──→ Plan p002 (新任务)
```

- Session 启动时可加载 planId 对应的 plan
- Session 执行过程中更新 plan 状态（task checkbox、phase status 等）
- 多个 session 可引用同一个 plan（串行执行，非并行）
- Plan 的状态持久化由运行时引擎负责，与 session 的 Event Log 独立

## 7. 历史快照

### 7.1 为什么需要快照

快照的作用至少有三个：

- 压缩前保留原始上下文
- 会话分叉时作为父上下文引用
- 故障排查时追踪历史状态

### 7.2 快照策略

建议当前阶段采用不可变快照策略：

- 快照一旦生成，不原地修改
- 快照具有独立 `snapshotId`
- 子 session 只引用快照，不反向修改父 session

### 7.3 每消息快照（Session Tree）

**决策**：每条消息产生时都生成唯一的 snapshotId，形成可重建的 Session Tree。

**期望行为**：

1. 每条新消息（user、assistant、tool result）写入 session 时，生成一个 snapshotId
2. snapshotId 关联当前消息序列的完整状态（消息列表 + plan 状态）
3. 通过任意 snapshotId 可以重建该时间点的完整会话状态
4. Session Tree 的分支点就是 fork 操作产生的新 session

**用途**：

- 回溯任意历史状态
- 调试时对比不同时间点的上下文差异
- Fork 时基于任意 snapshot 创建分支

**实现策略**：

- Phase 1：增量 JSONL 日志 + 按需重建（不需要存储每个 snapshot 的完整副本）
- Phase 2：可选的完整快照持久化（用于快速恢复热路径）

**拒绝了**：每条消息都存储完整快照副本。理由是存储开销与消息数成二次方增长。增量日志 + 按需重建更合理。

### 7.4 快照保留弹性

“永不删除”可以作为当前保守策略，但更适合写成默认策略，而不是绝对规则。

后续如果引入归档或 GC，需要能够扩展。

## 8. Session 延续

### 8.1 基本语义

当请求带 `sessionId` 时，运行时可以尝试：

1. 加载 `snapshot`（如不存在或过期，从 `events` 按 §5.3 重建）
2. 从 `session_header` 获取 `planId`，加载对应 plan（见 §6）
3. 检查是否需要压缩或恢复

### 8.2 延续边界

Session 延续应该解决“上下文怎么继续”，而不是隐式改变当前 Agent 的执行策略。

也就是说：

- 延续会话 != 自动恢复所有运行时细节
- 第一阶段只需要恢复消息、plan 和必要元数据

## 9. Session 分叉

### 9.1 分叉场景

分叉适用于：

- 子 agent 独立完成子任务
- 需要从当前上下文派生实验分支
- 保留父会话不变，避免污染主上下文

### 9.2 分叉流程

推荐流程：

1. 为父 session 生成历史快照
2. 创建子 session 目录
3. 复制或重建子 session 的初始状态
4. 记录父 session 和 snapshot 引用

### 9.3 父子关系

推荐单向关系：

- 子 session 记录父 session
- 父 session 不维护子列表作为强约束

这样可降低耦合和维护成本。

## 10. 会话压缩

### 10.1 为什么压缩和 session 强相关

压缩不仅是运行时行为，也是会话存储行为，因为压缩会改变活动上下文，但又需要保留压缩前状态。

### 10.2 推荐流程

基于 §5 的 Event Sourcing 模型，会话压缩流程：

1. 检查消息长度或 token 预算
2. 必要时触发压缩判断（5 层渐进管道，见 `nop-ai-agent-reliability.md` §7）
3. 执行压缩：生成 CompactionEntry（含 summary、firstKeptEntryId、tokensBefore/After）
4. 追加 CompactionEntry 到 Event Log（VFS `events`）
5. 更新派生快照（VFS `snapshot`，如有）
6. 压缩后保留内容见 `nop-ai-agent-reliability.md` §7.4 最小保真规则

### 10.3 CompactAgent 的定位

`CompactAgent` 可以作为一种实现方式存在，但不应在总体设计里被写死成唯一方案。

更稳妥的表述是：

- 压缩可以由专门 Agent、内部策略或 advisor + compressor 组合完成

## 11. 并发控制

### 11.1 锁粒度

第一阶段建议使用 Session 级锁。

含义：

- 一个 session 内的多文件更新在同一互斥边界内完成
- 不同 session 之间互不阻塞

### 11.2 基本规则

推荐固定以下规则：

- Session 级互斥写
- 读写互斥
- 避免跨 Session 嵌套锁
- 避免在锁内做长时间 LLM 调用或工具调用

### 11.3 原子性

存储层应保证：

- 单文件更新使用原子替换
- 多文件更新在同一事务性边界内完成或按顺序补偿

建议固定几个关键写回顺序：

1. Session 分叉时：先生成父快照，再创建子 session，再写入父引用
2. Session 压缩时：先生成快照，再追加 CompactionEntry 到 Event Log，最后更新派生快照
3. Plan 更新伴随消息更新时：若两者必须一致，应在同一 Session 锁边界内完成

## 12. 存储层边界

会话与存储层负责：

- 保存和读取会话状态
- 维护快照和分叉引用
- 记录日志与压缩元数据

会话与存储层不负责：

- 驱动 ReAct 主循环
- 决策是否重试
- 执行工具
- 直接实现 Skill 或 Hook 行为

## 13. 第一阶段建议保留的存储能力

建议第一阶段正式保留：

1. VFS 接口（`.nop/` 逻辑路径，后端可替换）
2. Event Log（session 级 source of truth）
3. 派生快照（可选缓存）
4. Plan 独立存储（项目级，AGE 规范，跨 session）
5. 历史快照
6. session 延续
7. session 分叉
8. Session 级锁
9. 原子更新

## 14. 后置能力

建议后置：

- 分布式 session store
- 多节点锁
- 自动快照清理
- 更复杂的存储索引
- 高级缓存层

## 15. 本篇结论

本篇固定了 Session、快照、分叉、压缩回写和 Session 级锁的第一阶段设计边界：

- 使用文件存储作为第一版实现策略
- 使用快照保证压缩与分叉前的历史可追溯
- 使用 Session 级锁保护多文件状态更新
- `.nop/` 是 VFS 逻辑路径，后端存储可替换（Phase 1 文件，Phase 2+ 数据库）
- Plan 是项目级实体，独立于 session，按 AGE 规范存储在 `docs/plans/`

这些是当前阶段应稳定的契约，但保留未来替换底层存储的空间。
