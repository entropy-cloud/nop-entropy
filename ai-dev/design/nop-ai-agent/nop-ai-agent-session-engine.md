# Nop AI Session Engine

## 1. 目标

本篇定义 Session 在 Java 引擎层中的运行时设计。

## 2. Session 引擎职责

Session 引擎负责：

1. 加载会话状态
2. 为一次执行构造会话态上下文
3. 在执行后回写会话状态
4. 管理快照、分叉和压缩前后状态切换

## 3. 推荐对象

- `SessionManager`
- `SessionLoader`
- `SessionWriter`
- `SessionForkService`
- `SessionCompressionCoordinator`

## 4. Session 与执行上下文的关系

- Session 是持久化状态源
- `AgentExecutionContext` 是一次执行的内存态工作集

## 5. 关键运行时流程

### 5.1 加载

1. 根据 `sessionId` 通过 VFS 加载 session（如快照不存在或过期，从 Event Log 按 session-and-storage.md §5.3 重建）
2. 从 `session_header` 获取 `planId`，加载对应 plan（独立存储，见 session-and-storage.md §6）
3. 构造内存态对象

### 5.2 分叉

1. 创建快照
2. 生成子 session
3. 写入父引用

### 5.3 压缩

1. 检查阈值
2. 生成快照
3. 更新活动消息集
4. 回写压缩记录

## 6. 本篇结论

Session 不只是文件格式问题，也是 Java 引擎设计问题。
