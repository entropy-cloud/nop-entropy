# Nop AI Agent 会话与存储设计

## 1. 目标

本篇定义 `nop-ai-agent` 的会话、持久化和文件布局设计，回答下面问题：

1. Agent 会话需要保存哪些状态
2. 会话如何延续、加载、分叉和压缩
3. 文件存储在第一阶段承担什么角色
4. 哪些存储约束应该作为实现前提固定下来

本篇只讨论本地会话与文件存储，不讨论分布式会话存储。

## 2. 设计定位

当前阶段建议把文件存储视为第一版实现策略，而不是长期唯一架构。

也就是说：

- 第一阶段可以使用文件系统保存 session 状态
- 设计上要保留未来替换为数据库或其他存储的空间
- 文档里可以定义推荐文件结构，但不要把具体目录布局写成不可变协议

## 3. Session 的角色

Session 表示一次 Agent 会话的持久化上下文。它至少需要承载：

- 会话标识
- 消息历史
- plan 状态
- 压缩记录
- 父子会话关系
- 调试和错误日志引用

Session 不是运行时本身，但运行时通常会依赖它来完成上下文延续和恢复。

## 4. 推荐存储布局

第一阶段建议使用下面的目录约定：

```text
.nop/sessions/
  {sessionId}/
    session.json
    plan.xml
    history-{snapshotId}.json
    out-{timestamp}.log
    error-{timestamp}.log
```

语义：

- `session.json`
  - 会话主状态和消息历史
- `plan.xml`
  - 结构化计划状态
- `history-{snapshotId}.json`
  - 历史快照，供压缩和分叉使用
- `out-{timestamp}.log`
  - 工具标准输出或运行输出归档
- `error-{timestamp}.log`
  - 错误过程记录

这是一种推荐布局，不应被理解为未来不能替换。

## 5. `session.json` 的职责

`session.json` 负责保存会话级核心状态。建议至少包含：

- `sessionId`
- `agentId` 或 `agent`
- `createdAt`
- `updatedAt`
- `parentSession`
- `compressions`
- `messages`

其中：

- `parentSession`
  - 用于记录分叉来源
- `compressions`
  - 用于记录压缩历史
- `messages`
  - 保存当前活动上下文中的消息

推荐最小结构如下：

```json
{
  "sessionId": "session-123",
  "agent": "coder",
  "createdAt": "2026-04-14T10:00:00Z",
  "updatedAt": "2026-04-14T10:10:00Z",
  "parentSession": {
    "id": "session-root",
    "snapshotId": "snapshot-001",
    "timestamp": "2026-04-14T09:58:00Z"
  },
  "compressions": [
    {
      "snapshotId": "snapshot-010",
      "timestamp": "2026-04-14T10:05:00Z"
    }
  ],
  "messages": [
    {
      "messageType": "system",
      "role": "system",
      "content": "..."
    },
    {
      "messageType": "user",
      "role": "user",
      "content": "..."
    },
    {
      "messageType": "assistant",
      "role": "assistant",
      "content": "...",
      "think": "...",
      "toolCalls": [
        {
          "id": "tool-1",
          "name": "read-file",
          "arguments": {
            "path": "README.md"
          }
        }
      ]
    },
    {
      "messageType": "tool",
      "role": "tool",
      "toolCallId": "tool-1",
      "name": "read-file",
      "content": "file content"
    },
    {
      "messageType": "assistant",
      "role": "assistant",
      "content": "..."
    }
  ]
}
```

`messages` 应明确视为 `ChatMessage` 多态序列化结果，而不是简化成仅有 `role/content` 的临时结构。

因此至少要保留：

- assistant 的 `think`
- assistant 的 `toolCalls`
- tool message 的 `toolCallId`
- tool message 的 `name`

否则运行时恢复后将丢失工具链路和推理上下文。

## 6. `plan.xml` 的职责

`plan.xml` 应与 `session.json` 分开保存。

原因：

- plan 是结构化控制对象
- 消息历史是对话对象
- 两者生命周期和更新频率不同

因此建议继续维持：

- 对话状态放 `session.json`
- 结构化计划放 `plan.xml`

建议最小结构至少包括：

- `task.id`
- `task.title`
- `task.description`
- `task.status`

状态建议支持：

- `pending`
- `in_progress`
- `completed`
- `failed`

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

1. 加载 `session.json`
2. 加载 `plan.xml`
3. 恢复消息上下文
4. 检查是否需要压缩或恢复

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

建议会话压缩流程：

1. 检查消息长度或 token 预算
2. 必要时触发压缩判断
3. 在压缩前生成历史快照
4. 生成压缩后的消息集合
5. 回写 `session.json`
6. 记录压缩元数据

建议压缩后必须保留以下信息：

1. 最早的 `system message`
2. 最早的用户初始目标或首条 `user message`
3. 当前 `plan` 的任务状态摘要
4. 最近 N 条消息

这样做的原因是：

- 保留系统约束
- 保留最初任务意图
- 保留结构化执行状态
- 保留最近工作记忆

同时建议禁止递归压缩：

- 执行压缩的 agent 或压缩流程本身，不应再次触发相同压缩逻辑

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
2. Session 压缩时：先生成快照，再写回压缩后的 `session.json`，最后写压缩记录
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

1. `session.json`
2. `plan.xml`
3. 历史快照
4. session 延续
5. session 分叉
6. Session 级锁
7. 原子文件更新

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
- 将 `session.json` 与 `plan.xml` 分层保存

这些是当前阶段应稳定的契约，但保留未来替换底层存储的空间。
