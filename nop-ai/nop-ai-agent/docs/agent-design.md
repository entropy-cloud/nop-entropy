# AI Agent 设计文档

## 1. 设计概述

### 1.1 核心原则

- **单进程执行**：所有 agent 运行在单一进程中
- **纯文件存储**：不依赖数据库，所有持久化数据通过文件系统存储
- **数据不可变**：history 快照一旦创建永不删除
- **Advisor Agent 决策**：一致性检查、重试决策、压缩触发等通过调用专门的 Advisor Agent 实现
- **统一调用机制**：所有 agent 调用（包括决策）都通过 `call-agent` 工具
- **权限分离**：通过配置限制 agent 可调用的工具和可访问的文件
- **失败由 prompt 决策**：子 agent 失败后返回错误消息，由调用方的 prompt 智能决策
- **内存锁机制**：使用进程内的锁机制保护并发访问

### 1.2 核心概念

- **Session（会话）**：Agent 的一次完整对话，所有上下文和状态信息持久化存储
- **Plan（计划）**：任务执行计划和进度跟踪，始终启用
- **Subagent（子代理）**：独立会话的代理实例，用于处理子任务
- **Advisor Agent（顾问 Agent）**：专门负责决策的 agent，如 ConsistencyChecker、RetryAdvisor、CompressionAdvisor
- **Tools（工具）**：Agent 可调用的系统功能集合
- **CompactAgent**：专门负责对话摘要压缩的 agent，无状态
- **Permissions（权限）**：Agent 的工具调用和文件访问权限配置

### 1.3 文件结构约定

```
.nop/sessions/
├── {sessionId}/
│   ├── session.json           # 会话状态和消息
│   ├── plan.xml               # 任务计划和进度
│   ├── out-{timestamp}.log    # 工具执行输出日志
│   └── history-{snapshot}.json # 压缩的历史快照（不可变）
│
├── .permissions/              # 权限配置目录（只读）
│   ├── agent-default.yml      # 默认 agent 权限
│   ├── agent-coder.yml        # 特定 agent 权限
│   ├── agent-consistency-checker.yml
│   ├── agent-retry-advisor.yml
│   └── agent-compression-advisor.yml
│
└── .system/                   # 系统文件（agent 只读）
    ├── skills/                 # 技能定义
    └── tools/                  # 工具定义
```

### 1.4 系统工具

| 工具名 | 功能 | 权限要求 |
|--------|------|----------|
| `compact` | 压缩对话消息（调用 CompactAgent） | COMPACT |
| `snapshot` | 生成历史快照 | SNAPSHOT |
| `call-agent` | 调用其他 agent（包括 Advisor Agent） | CALL_AGENT |
| `load-skill` | 加载指定技能 | LOAD_SKILL |
| `shell` | 执行 shell 脚本 | SHELL |
| `read-file` | 读取文件内容 | READ_FILE |
| `write-file` | 写入文件内容 | WRITE_FILE |

### 1.5 核心机制

1. **会话延续**：传入 sessionId 时自动加载历史状态
2. **计划一致性检查**：通过 `call-agent` 调用 ConsistencyChecker agent
3. **智能压缩**：通过 `call-agent` 调用 CompressionAdvisor agent 判断，然后调用 CompactAgent 执行
4. **会话分叉**：从当前 session 派生新的 session，通过 parentSessionPath 指向父 session 的 history
5. **工具调用保护**：失败时通过 `call-agent` 调用 RetryAdvisor agent 判断是否重试
6. **错误日志隔离**：失败过程单独保存，成功调用后才记录到主上下文
7. **子 agent 调用**：失败返回错误消息，由主 agent 的 prompt 智能决策

---

## 2. Advisor Agent 机制

### 2.1 设计原则

Advisor Agent 是专门负责决策的普通 agent，通过 `call-agent` 工具调用。

**核心约定：**

- Advisor Agent 是普通 agent，有独立的 session
- 调用方式统一为 `call-agent` 工具
- Advisor Agent 返回结构化的决策结果（JSON 格式）
- Advisor Agent 的 prompt 定义在系统配置中
- Advisor Agent 的权限限制为只读（不修改文件或状态）

### 2.2 Advisor Agent 类型

| Advisor Agent | 功能 | 返回格式 | 调用场景 |
|---------------|------|----------|----------|
| `consistency-checker` | 检查 plan 和 chat 的一致性 | `{consistent, inconsistencies, summary}` | Session 加载时、压缩前 |
| `retry-advisor` | 判断是否应该重试 | `{shouldRetry, reason, suggestedAction, modifiedParams}` | 工具调用失败后 |
| `compression-advisor` | 判断是否需要压缩 | `{shouldCompress, reason, priority, suggestions}` | 检查压缩触发时机 |

### 2.3 调用约定

**标准调用流程：**

1. 准备输入参数（如 plan、chat、错误信息等）
2. 使用 `call-agent` 工具调用 Advisor Agent
3. 解析 Advisor Agent 返回的 JSON 结果
4. 根据结果执行相应操作

**输入格式：**

- 参数通过 `call-agent` 工具的 arguments 传递
- 支持字符串、对象、数组等类型
- 复杂对象（如 plan、chat）以 JSON 字符串传递

**输出格式：**

- 必须返回 JSON 格式
- 结构必须符合 Advisor Agent 定义的 schema
- 包含决策原因和建议

**超时控制：**

- Advisor Agent 调用设置默认超时（30 秒）
- 超时后返回默认决策（如"不重试"、"不压缩"）

### 2.4 ConsistencyChecker Agent

**功能：**

检查 plan 和 chat 的一致性，识别不一致的地方。

**输入：**

- `plan`（JSON/XML 字符串）：plan.xml 内容
- `session`（JSON 字符串）：session.json 内容
- `sessionId`（字符串）：会话 ID

**输出格式：**

```json
{
  "consistent": true|false,
  "inconsistencies": [
    {
      "type": "missing_in_chat|missing_in_plan|status_mismatch",
      "taskId": "task-id",
      "description": "问题描述",
      "severity": "low|medium|high",
      "suggestion": "修复建议"
    }
  ],
  "summary": "总体评价"
}
```

**Prompt 约定：**

- 角色：一致性检查专家
- 任务：分析 plan 和 session 中的 messages，识别不一致
- 分析要点：
  - plan 中标记为 completed 的任务是否在 messages 中被提及
  - messages 中提到的任务是否都存在于 plan 中
  - 任务进度在 plan 和 messages 中是否一致
- 返回：JSON 格式的检查结果

**权限配置：**

```yaml
agent: consistency-checker
permissions:
  tools:
    - read-file
  file-access:
    - pattern: ".nop/sessions/**/session.json"
      access: read-only
    - pattern: ".nop/sessions/**/plan.xml"
      access: read-only
```

### 2.5 RetryAdvisor Agent

**功能：**

分析工具调用失败的原因，判断是否应该重试。

**输入：**

- `errorType`（字符串）：错误类型
- `errorMessage`（字符串）：错误消息
- `stackTrace`（字符串）：堆栈跟踪
- `toolName`（字符串）：工具名
- `arguments`（对象）：工具调用参数
- `retryCount`（整数）：已重试次数

**输出格式：**

```json
{
  "shouldRetry": true|false,
  "reason": "决策原因",
  "suggestedAction": "retry|modify_params|skip|abort",
  "modifiedParams": {
    // 修改后的参数（如果建议修改）
  }
}
```

**Prompt 约定：**

- 角色：错误处理专家
- 任务：分析错误原因，判断是否可以重试
- 判断原则：
  - 应该重试：网络超时、服务暂不可用等临时性错误
  - 不应该重试：参数错误、权限错误等永久性错误
  - 修改参数后重试：某些错误可以通过修改参数避免
  - 跳过：非关键任务失败，不影响主流程
  - 终止：关键任务失败，无法继续
- 返回：JSON 格式的决策结果

**权限配置：**

```yaml
agent: retry-advisor
permissions:
  tools:
    - read-file
  file-access:
    - pattern: ".nop/sessions/**/error-*.log"
      access: read-only
```

### 2.6 CompressionAdvisor Agent

**功能：**

判断当前对话是否需要压缩。

**输入：**

- `sessionId`（字符串）：会话 ID
- `agentType`（字符串）：agent 类型
- `messageCount`（整数）：消息数量
- `tokenCount`（整数）：Token 数
- `recentMessages`（数组）：最近 10 条消息
- `plan`（JSON/XML 字符串）：plan.xml 内容
- `compressionCount`（整数）：已经压缩次数
- `lastCompressionTime`（字符串）：上次压缩时间

**输出格式：**

```json
{
  "shouldCompress": true|false,
  "reason": "决策原因",
  "priority": "low|medium|high",
  "suggestions": [
    "压缩建议1",
    "压缩建议2"
  ]
}
```

**Prompt 约定：**

- 角色：上下文管理专家
- 任务：判断对话是否需要压缩
- 判断要点：
  - 分析对话的长度和复杂度
  - 判断当前上下文是否已经过长，影响 LLM 理解
  - 判断是否有足够的内容可以被压缩
  - 考虑压缩的时机
- 决策原则：
  - 应该压缩：消息数量超过阈值、Token 数超过阈值
  - 可以等待：消息数量适中，但接近阈值
  - 不需要压缩：对话简短，压缩会导致信息丢失
- 返回：JSON 格式的决策结果

**权限配置：**

```yaml
agent: compression-advisor
permissions:
  tools:
    - read-file
  file-access:
    - pattern: ".nop/sessions/**/session.json"
      access: read-only
    - pattern: ".nop/sessions/**/plan.xml"
      access: read-only
```

---

## 3. 会话管理

### 3.1 Session 结构

**session.json 结构（会话状态和消息）：**

- `sessionId`：会话 ID
- `agent`：agent 类型
- `createdAt`：创建时间
- `updatedAt`：更新时间
- `parentSession`：父会话引用（可选）
  - `id`：父会话 ID
  - `snapshotId`：快照 ID
  - `timestamp`：时间戳
- `compressions`：压缩记录
  - `compression`
    - `snapshotId`：快照 ID
    - `timestamp`：时间戳
- `messages`：消息列表
  - `role`：角色（system / user / assistant / tool）
  - `content`：内容
  - `timestamp`：时间戳（可选）
  - `toolCall`：工具调用信息（role=tool 时）

**plan.xml 结构（任务计划）：**

- `tasks`：任务列表
  - `task`：任务
    - `id`：任务 ID
    - `title`：标题
    - `description`：描述（可选）
    - `status`：状态（pending / in_progress / completed / failed）

### 3.2 并发控制约定

**锁粒度：**

- 每个 Session 独立使用一个锁
- 不同 Session 的操作不互相阻塞
- 同一 Session 的所有文件操作（session.json、plan.xml 等）在同一锁内完成

**读写分离：**

- 读操作可以并发
- 写操作互斥
- 读写操作互斥

**死锁预防：**

- 所有 Session 内的文件操作必须按固定顺序加锁
- 禁止嵌套锁（同一 Session 内加锁时不能再次请求其他 Session 的锁）

### 3.3 会话加载约定

加载 Session 时执行以下步骤：

1. 读取 session.json 和 plan.xml
2. 检查 session.json 中的 parentSession 引用
3. 调用 ConsistencyChecker agent 检查 plan 和 session 的一致性
4. 如果发现不一致，记录警告日志
5. 可选：根据配置自动修复不一致（默认不自动修复）

**调用 ConsistencyChecker：**

```json
{
  "agent": "consistency-checker",
  "arguments": {
    "plan": "<plan.xml 内容>",
    "session": "<session.json 内容>",
    "sessionId": "session-123"
  }
}
```

### 3.4 会话压缩约定

**触发时机：**

- 每次 Session 加载后检查
- 每次 agent 交互后检查
- 达到配置的消息数量或 Token 数阈值时

**压缩流程：**

1. 调用 CompressionAdvisor agent 判断是否需要压缩
2. 如果不需要压缩，直接返回
3. 创建 history 快照（不可变）
4. 调用 CompactAgent 生成压缩后的 messages
5. 替换 session.json 中的 messages 为压缩后的内容
6. 在 session.json 中记录压缩操作

**调用 CompressionAdvisor：**

```json
{
  "agent": "compression-advisor",
  "arguments": {
    "sessionId": "session-123",
    "agentType": "coder",
    "messageCount": 150,
    "tokenCount": 60000,
    "recentMessages": [/* 最近 10 条消息 */],
    "plan": "<plan.xml 内容>",
    "compressionCount": 2,
    "lastCompressionTime": "2026-01-27T10:00:00Z"
  }
}
```

**CompactAgent 约定：**

- 无状态设计，不写回原 Session
- 避免递归压缩（CompactAgent 不能调用 compact 工具）
- 压缩后的 chat 必须包含：
  - 最早的 system message
  - 最早的 user prompt
  - plan 中的所有任务状态摘要
  - 最近的 N 条消息（N 可配置）

---

## 4. Subagent 调用和失败处理

### 4.1 Subagent 创建约定

**创建流程：**

1. 检查调用方是否有权限调用目标 agent
2. 创建新的子 Session
3. 记录父 Session ID
4. 子 Session 继承父 Session 的权限配置

**权限继承：**

- 子 Agent 的工具调用权限由父 Session 的权限配置决定
- 子 Agent 的文件访问权限与父 Session 相同
- 子 Agent 不能提升权限（即使通过 prompt）

### 4.2 Subagent 执行约定

**超时控制：**

- 默认超时时间：5 分钟（可配置）
- 超时后强制终止子 Agent 执行
- 超时视为失败，返回超时错误信息

**执行监控：**

- 记录子 Agent 的执行时长
- 记录工具调用次数
- 记录输出 Token 数

### 4.3 失败处理约定

**失败信息格式：**

- 错误类型
- 错误消息
- 堆栈跟踪（可选）
- 执行时长
- 相关 Session ID

**失败决策：**

- 子 Agent 失败后，调用方通过调用 RetryAdvisor agent 判断如何处理
- 调用方可以：
  - 重试（使用相同或不同的参数）
  - 修改参数后重试
  - 忽略错误继续执行
  - 终止当前任务

**重试限制：**

- 最大重试次数：3 次（可配置）
- 重试间隔：1 秒（可配置）
- 超过最大重试次数后必须终止或跳过

**重试决策流程：**

1. 子 Agent 返回失败信息
2. 调用方通过 `call-agent` 调用 RetryAdvisor agent
3. RetryAdvisor 返回决策结果（shouldRetry / suggestedAction / modifiedParams）
4. 调用方根据建议执行相应操作

**调用 RetryAdvisor：**

```json
{
  "agent": "retry-advisor",
  "arguments": {
    "errorType": "TimeoutException",
    "errorMessage": "Execution timed out after 5 minutes",
    "stackTrace": "<堆栈跟踪>",
    "toolName": "shell",
    "arguments": {"command": "npm install"},
    "retryCount": 1
  }
}
```

---

## 5. 权限系统

### 5.1 权限配置约定

**配置文件位置：**

- 默认权限：`.permissions/agent-default.yml`
- 特定 agent 权限：`.permissions/agent-{name}.yml`

**权限继承：**

- 特定 agent 权限覆盖默认权限
- 未指定的权限使用默认值
- 权限配置只读，运行时不可修改

**配置结构：**

```yaml
agent: {agent-name}
extends: {parent-agent-name}  # 可选，继承其他 agent 的权限

tools:
  allowed: [tool-list]  # 允许调用的工具
  denied: [tool-list]   # 禁止调用的工具

file-access:
  - pattern: {file-pattern}
    access: {access-level}
```

### 5.2 工具权限约定

**权限检查：**

- 每次调用工具前检查权限
- 权限检查失败立即抛出异常
- 权限检查结果不缓存（每次都检查）

**权限评估：**

- 先检查 denied 列表，如果匹配则拒绝
- 再检查 allowed 列表，如果不匹配则拒绝
- allowed 列表中的 "*" 表示允许所有工具

### 5.3 文件访问权限约定

**访问级别：**

- `read`：可读取和写入
- `read-only`：只能读取，不能修改
- `deny`：完全禁止访问

**匹配规则：**

- 按配置顺序从上到下匹配
- 第一个匹配的规则生效
- 路径匹配支持 glob 模式（如 `**/*.java`）

**系统目录约束：**

- `.nop/**`：默认 read-only
- `.nop/.permissions/**`：完全禁止访问
- `.nop/.system/**`：默认 read-only

**路径规范化：**

- 所有文件路径必须规范化（解析相对路径、解析符号链接）
- 禁止目录遍历攻击（如 `../../etc/passwd`）

### 5.4 Subagent 调用权限约定

**权限检查：**

- 调用 `call-agent` 工具前检查调用方是否有权限调用目标 agent
- 检查目标 agent 是否在 allowed 列表中
- 检查目标 agent 是否在 denied 列表中

**子 agent 权限继承：**

- 子 agent 继承父 agent 的所有权限
- 子 agent 不能提升权限
- 子 agent 的权限配置来源记录在 Session 中

---

## 6. 工具调用保护

### 6.1 工具执行流程

**标准流程：**

1. 检查工具调用权限
2. 验证参数格式
3. 检查文件访问权限（如果是文件操作）
4. 获取超时配置
5. 执行工具
6. 记录成功调用到 session.json
7. 失败调用记录到错误日志

### 6.2 超时控制约定

**超时配置：**

- 默认超时：5 分钟
- 可通过参数调整
- 超时后强制终止工具执行

**超时处理：**

- 超时视为失败
- 返回超时错误信息
- 通过调用 RetryAdvisor agent 判断是否重试

### 6.3 参数验证约定

**验证时机：**

- 在工具执行前验证
- 验证失败立即返回错误，不执行工具

**验证内容：**

- 必填参数是否存在
- 参数类型是否正确
- 参数值是否在有效范围内
- 文件路径是否合法

### 6.4 错误日志约定

**日志格式：**

- 时间戳
- Session ID
- 工具名
- 参数
- 错误类型
- 错误消息
- 堆栈跟踪

**日志位置：**

- `.nop/sessions/{sessionId}/error-{timestamp}.log`
- 日志文件永久保存

---

## 7. 会话分叉

### 7.1 分叉创建约定

**创建流程：**

1. 创建父 Session 的 history 快照
2. 创建新的子 Session 目录
3. 复制父 Session 的 session.json 和 plan.xml 到子 Session
4. 在子 Session 的 session.json 中记录父 Session 引用

**快照约定：**

- 快照文件不可变，永不删除
- 快照文件名格式：`history-{timestamp}.json`
- 快照包含完整的 chat 内容

### 7.2 父子关系约定

**引用格式：**

- 子 Session 在 plan.xml 中记录父 Session 引用
- 引用包含：父 Session ID、快照 ID、时间戳
- 父 Session 不记录子 Session 引用（单向引用）

**加载约定：**

- 加载子 Session 时可以追溯父 Session
- 父 Session 不验证子 Session 是否存在
- 父 Session 删除或修改不影响已分叉的子 Session

### 7.3 历史快照引用约定

**引用方式：**

- 通过快照 ID 引用（而非文件名）
- 快照 ID 可以是时间戳或 UUID
- 引用记录在 plan.xml 的 `parent-session.snapshot-id` 字段

**不可变性：**

- history 文件一旦创建永不删除
- 即使父 Session 被删除，history 文件保留
- 多个子 Session 可以引用同一个 history 文件

---

## 8. 设计约束和约定

### 8.1 文件操作约束

**原子性：**

- 单文件操作保证原子性（使用原子文件替换）
- 多文件操作在同一锁内完成
- 操作失败不保留部分修改

**一致性：**

- chat.json 和 plan.xml 必须同步更新
- history 文件创建后立即生效
- 删除操作需要先检查引用关系

**性能：**

- 避免频繁读写大文件
- 避免在锁内执行耗时操作
- 缓存频繁读取的小文件

### 8.2 并发控制约束

**锁范围：**

- 锁粒度为 Session 级别
- 不同 Session 之间不互相阻塞
- 避免跨 Session 的锁依赖

**锁顺序：**

- 固定的加锁顺序，避免死锁
- 禁止嵌套锁（同一 Session 内加锁时不能请求其他 Session 的锁）
- 读写锁分离

**超时：**

- 锁获取设置超时（避免无限等待）
- 超时后抛出异常
- 避免在锁内执行耗时操作

### 8.3 安全约束

**路径访问：**

- 所有文件路径必须规范化
- 禁止目录遍历攻击
- 限制文件系统访问范围

**权限检查：**

- 所有工具调用前检查权限
- 所有文件操作前检查权限
- 权限检查结果不缓存

**隔离性：**

- 子 Agent 不能修改系统配置
- 子 Agent 不能访问权限配置文件
- .nop 目录默认只读

---

## 9. 错误处理和日志

### 9.1 错误分类

**可恢复错误：**

- 网络超时
- 服务暂不可用
- 临时性资源不足

**不可恢复错误：**

- 权限错误
- 参数验证错误
- 配置错误

**可修复错误：**

- 某些参数错误
- 某些格式错误
- 某些状态错误

### 9.2 错误传播约定

**工具层错误：**

- 工具执行失败记录到错误日志
- 返回标准化错误信息给调用方
- 由调用方通过调用 RetryAdvisor agent 决定如何处理

**Agent 层错误：**

- Agent 执行失败返回错误消息给调用方
- 不自动重试（除非通过调用 RetryAdvisor agent 建议）
- 错误消息包含足够上下文信息

**Advisor Agent 错误：**

- Advisor Agent 超时或失败时，返回默认决策
- 不影响主流程继续执行
- 记录 Advisor Agent 失败日志

### 9.3 日志约定

**日志级别：**

- ERROR：错误，需要立即处理
- WARN：警告，可能的问题
- INFO：信息，正常流程记录
- DEBUG：调试，详细信息

**日志内容：**

- 时间戳
- Session ID
- Agent 类型
- 操作类型
- 结果状态
- 相关参数

**日志存储：**

- 系统日志：`.nop/logs/system.log`
- Session 日志：`.nop/sessions/{sessionId}/session.log`
- 错误日志：`.nop/sessions/{sessionId}/error-{timestamp}.log`

---

## 10. 性能优化约定

### 10.1 缓存策略

**权限缓存：**

- 权限配置可以缓存（文件修改时失效）
- 缓存时间：5 分钟（可配置）

**Session 缓存：**

- 频繁访问的 Session 可以缓存
- 缓存时间：1 分钟（可配置）
- 写操作时失效缓存

**Prompt 模板缓存：**

- Prompt 模板可以永久缓存
- 模板修改时需要重启

### 10.2 批量操作约定

**消息追加：**

- 单次追加多条消息
- 在同一锁内完成
- 避免频繁加锁解锁

**文件读取：**

- 批量读取相关文件
- 缓存小文件内容
- 避免重复读取

**LLM 调用：**

- 合并多个决策为单次 Agent 调用
- 使用批量 API（如果支持）
- 缓存相似输入的结果

### 10.3 资源管理

**文件描述符：**

- 及时关闭文件
- 避免同时打开过多文件
- 使用文件池（如果需要）

**线程池：**

- 工具执行使用线程池
- 限制并发工具调用数
- 超时任务自动取消

**内存管理：**

- 大文件流式处理
- 及时释放大对象
- 避免内存泄漏

---

## 11. 配置管理

### 11.1 配置文件位置

**核心配置：**

- 主配置：`.nop/config.yml`
- 权限配置：`.permissions/`
- Agent 定义：`.permissions/agent-*.yml`

**配置优先级：**

1. 命令行参数
2. 环境变量
3. Session 配置（如果有）
4. Agent 配置
5. 默认配置

### 11.2 配置热加载

**支持热加载：**

- 权限配置修改后自动重新加载
- Agent Prompt 修改后自动重新加载

**不支持热加载：**

- 主配置修改需要重启

### 11.3 配置验证

**加载时验证：**

- 配置文件格式验证
- 必填字段检查
- 字段类型检查

**运行时验证：**

- 配置值范围验证
- 依赖关系验证
- 一致性验证

---

## 12. 扩展性约定

### 12.1 扩展点

**新 Agent：**

- 创建 Agent 配置文件
- 定义 Agent 权限
- 编写 Agent Prompt

**新 Advisor Agent：**

- 创建 Advisor Agent 配置文件
- 定义输入输出格式
- 编写 Advisor Agent Prompt
- 配置权限（通常只读）

**新工具：**

- 实现工具接口
- 添加工具定义到配置
- 定义工具权限

### 12.2 插件机制

**Plugin 接口：**

- 实现 Plugin 接口
- 插件配置：`.nop/plugins/{plugin-name}/config.yml`
- 插件目录：`.nop/plugins/{plugin-name}/`

**Plugin 加载：**

- 启动时扫描插件目录
- 按顺序加载插件
- 插件失败不影响系统启动

---

## 13. 总结

### 13.1 核心设计理念

1. **统一调用机制**：所有 agent 调用（包括决策）都通过 `call-agent` 工具
2. **Advisor Agent**：决策通过专门的 Advisor Agent 实现
3. **文件存储**：纯文件系统，history 不可变
4. **单进程**：使用进程内锁机制
5. **权限分离**：系统目录只读，权限配置不可访问
6. **无状态压缩**：CompactAgent 无状态，避免递归
7. **Prompt 决策**：子 agent 失败由调用方通过 RetryAdvisor agent 智能决策

### 13.2 关键约定

| 约定 | 说明 |
|------|------|
| 并发控制 | Session 级别的读写锁，跨 Session 不阻塞 |
| 原子操作 | 单文件原子替换，多文件同一锁内 |
| 权限检查 | 工具和文件操作前必须检查权限 |
| 决策机制 | 通过 `call-agent` 调用 Advisor Agent |
| 错误处理 | 失败由调用方通过 RetryAdvisor agent 决策 |
| 数据不可变 | history 文件永不删除 |

### 13.3 Advisor Agent 列表

| Advisor Agent | 输入 | 输出 | 调用场景 |
|---------------|------|------|----------|
| consistency-checker | plan, chat, sessionId | {consistent, inconsistencies, summary} | Session 加载、压缩前 |
| retry-advisor | error info, retryCount | {shouldRetry, reason, suggestedAction} | 工具/Agent 调用失败 |
| compression-advisor | session info, stats | {shouldCompress, reason, suggestions} | 检查压缩触发 |

### 13.4 实施优先级

**P0（核心功能）：**

1. Session 文件存储和加载
2. Session 级别的并发控制
3. `call-agent` 工具实现
4. 基本权限系统
5. 工具调用框架

**P1（Advisor Agent）：**

1. ConsistencyChecker Agent
2. RetryAdvisor Agent
3. CompressionAdvisor Agent
4. CompactAgent 实现
5. Subagent 调用机制

**P2（完善功能）：**

1. 会话分叉
2. 错误日志记录
3. 性能优化
4. 配置热加载
