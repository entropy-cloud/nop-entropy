# OpenCode Session 存储机制分析

## 1. 数据存储位置

所有 session 数据存储在 `~/.local/share/opencode/storage/` 目录，使用 JSON 格式：

```
~/.local/share/opencode/storage/
├── session/[projectID]/[sessionID].json      # session 元数据
├── message/[sessionID]/[messageID].json       # 消息基本信息
├── part/[messageID]/[partID].json             # 消息的各个部分
└── session_diff/[sessionID].json             # 文件差异记录
```

## 2. 存储结构

### 2.1 Session 元数据

- 标题、创建/更新时间
- 项目 ID、目录路径
- 父 session ID（用于 session 继承）
- 摘要信息（代码变更统计）

### 2.2 Message（消息）

包含 user 和 assistant 两种角色的消息：

- `role`: "user" | "assistant"
- `time`: 创建时间、完成时间
- `agent`: 使用的 agent 名称
- `model`: 模型信息（providerID, modelID）
- `parentID`: 父消息 ID
- `tokens`: token 使用统计
- `cost`: 成本计算

### 2.3 Part（消息部分）

每个消息可以有多个 parts：

**文本部分**

- `type`: "text"
- `text`: 文本内容
- `time`: 开始/结束时间
- `metadata`: 元数据

**工具调用部分**

- `type`: "tool"
- `callID`: 调用 ID
- `tool`: 工具名称
- `state`: 状态（pending/running/completed/error）
- `input`: 工具输入参数
- `output`: 工具输出结果
- `title`: 工具执行标题
- `time`: 开始/结束时间

**推理部分**

- `type`: "reasoning"
- `text`: 推理内容
- `time`: 开始/结束时间

**其他部分**

- `file`: 文件附件
- `step-start/step-finish`: 步骤标记
- `compaction`: 压缩标记
- `retry`: 重试信息

## 3. 工具调用记录

工具调用完整记录包含：

```json
{
  "type": "tool",
  "callID": "call_xxx",
  "tool": "bash|edit|read|write|...",
  "state": {
    "status": "completed",
    "input": {
      /* 工具参数 */
    },
    "output": "执行结果",
    "title": "工具标题",
    "time": {
      "start": 1768806742594,
      "end": 1768806742691
    }
  }
}
```

## 4. 查看方法

### 4.1 查看 Session 列表

```bash
opencode session list
```

### 4.2 导出完整 Session 历史

```bash
opencode export [sessionID]
```

输出的 JSON 包含：

```json
{
  "info": {
    /* session 元数据 */
  },
  "messages": [
    {
      "info": {
        /* 消息基本信息 */
      },
      "parts": [
        /* 所有 parts，包括工具调用 */
      ]
    }
  ]
}
```

### 4.3 直接查看 JSON 文件

```bash
# 查看 session 元数据
cat ~/.local/share/opencode/storage/session/[projectID]/[sessionID].json

# 查看消息列表
ls ~/.local/share/opencode/storage/message/[sessionID]/

# 查看某个消息的所有 parts
ls ~/.local/share/opencode/storage/part/[messageID]/
```

### 4.4 查找工具调用

```bash
# 查找包含工具调用的 part 文件
find ~/.local/share/opencode/storage/part -name "*.json" -exec grep -l '"type": "tool"' {} \;

# 查看特定工具调用
cat ~/.local/share/opencode/storage/part/[messageID]/[partID].json
```

## 5. 日志记录

日志存储在 `~/.local/share/opencode/log/` 目录，按日期命名：

```bash
~/.local/share/opencode/log/2026-01-19T132027.log
```

日志包含：

- 事件发布（bus）
- 权限检查
- 消息 part 更新
- 工具执行状态
- 错误信息

查看最新日志：

```bash
tail -f ~/.local/share/opencode/log/$(ls -t ~/.local/share/opencode/log/*.log | head -1)
```

## 6. 代码实现位置

- **存储层**：`packages/opencode/src/storage/storage.ts`
  - `read()`: 读取数据
  - `write()`: 写入数据
  - `update()`: 更新数据
  - `remove()`: 删除数据
  - `list()`: 列出数据

- **Session 管理**：`packages/opencode/src/session/index.ts`
  - `create()`: 创建 session
  - `get()`: 获取 session
  - `messages()`: 获取消息列表
  - `update()`: 更新 session

- **Message/Part 结构**：`packages/opencode/src/session/message-v2.ts`
  - `Info`: 消息信息定义
  - `Part`: 各种 part 类型定义
  - `stream()`: 流式读取消息
  - `get()`: 获取消息和 parts

- **日志系统**：`packages/opencode/src/util/log.ts`
  - `Log.create()`: 创建 logger
  - 日志级别管理
  - 日志文件写入

- **导出命令**：`packages/opencode/src/cli/cmd/export.ts`
  - `ExportCommand`: 导出 session 为 JSON

## 7. 数据流向

1. **用户输入** → 创建 User Message + Text Part
2. **LLM 处理** → 创建 Assistant Message
3. **流式输出** → 逐步添加 Text Part / Reasoning Part
4. **工具调用** → 添加 Tool Part (pending)
5. **工具执行** → 更新 Tool Part (running → completed/error)
6. **持久化** → 所有数据写入 Storage

## 8. 存储原理

所有数据使用 JSON 格式存储，通过文件系统 API 操作：

```typescript
// 写入数据
await Storage.write(["message", sessionID, messageID], messageInfo);
await Storage.write(["part", messageID, partID], partInfo);

// 读取数据
const msg = await Storage.read(["message", sessionID, messageID]);
const parts = await Storage.list(["part", messageID]);
```

Storage 层提供了锁机制保证并发安全。

## 9. 关键特性

- **完整性**：所有交互（包括工具调用）都会持久化
- **时间序列**：所有记录都有精确的时间戳
- **层级结构**：Session → Message → Part 三级结构
- **可追溯**：支持消息父子关系和 session 继承
- **导出能力**：可以导出完整 session 历史

---

## 修改历史

- 2026-01-19: 初始创建，记录 OpenCode Session 存储机制
