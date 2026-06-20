# Engine Lifecycle & Checkpoint Cache Bounds

> Status: active design doc
> Source: plan 278 (AR-09 + AR-10), `ai-dev/plans/278-nop-ai-agent-engine-resource-lifecycle-recovery-delegation-bounds.md`

## Decision

`IAgentEngine` 有显式的生命周期终止入口（`close()`），自创建的线程池可被关闭；`ICheckpointManager` 有显式的 per-session cache 清理入口（`remove(sessionId)`），引擎在 session 进入终态时调用它，使长跑部署中内存 cache 不随累计 session 数无界增长。

两个入口都通过接口的 **default no-op 方法** 引入，保证现有实现者（含 ~32 个 in-tree 测试 stub 与 NoOp/DB 后端）源码兼容、行为不变——只有持有无界 in-memory cache 或自创建池的实现才需要 override。

## IAgentEngine.close() 契约

- `IAgentEngine extends AutoCloseable`，并提供 `@Override default void close() throws Exception {}` no-op 默认方法。extends AutoCloseable 使 `@Override` 合法且支持 try-with-resources；default no-op 保持现有实现者源码兼容。
- `DefaultAgentEngine.close()` override：
  - **仅关闭自创建的池**（`lockRenewExecutor` / `agentExecutor`，由 `ownLockRenewExecutor` / `ownAgentExecutor` 标志区分）；外部注入的池（经 `setXxxExecutor` 清除 own 标志）不被关闭——调用方拥有其生命周期。
  - **幂等**：`AtomicBoolean closed` 的 `compareAndSet(false, true)` 守门，二次 close 为 no-op + LOG.debug。
  - **不取消在途执行**：取消在途 session 是调用方职责（经 `cancelSession` 或在 close 前等待完成）。
  - **best-effort**：池关闭失败捕获 RuntimeException 记 WARN 不重抛；`shutdown()`（非 `awaitTermination`）不抛 InterruptedException。

## ICheckpointManager.remove(sessionId) 契约

- `ICheckpointManager` 增加 `default void remove(String sessionId) {}` 幂等 no-op 默认方法。NoOp / DB 及未来 stub 继承默认；持有无界 in-memory cache 的实现 override。
- override 实现：
  - `FileBackedCheckpointManager.remove`：清五个 cache（`bySession` / `byWatermark` / `snapshotCache` / `saveCounters` / `loadedSessions`）。on-disk journal.md / snapshot.json 不删除（持久审计 trail）；后续访问经 `ensureSessionLoaded` 从盘重载。
  - `ToolExecutionCheckpoint.remove`：清 `bySession` 及对应 `byWatermark` 条目。
- **幂等**：对无 cache 的 session 调 remove 是安全 no-op。

## 引擎调用门（terminal-only）

引擎在 session 到达**终态**时调 `checkpointManager.remove(sessionId)`，由 `isTerminalStatus(status)` 守门：

- 终态 = `completed` / `failed` / `cancelled` / `forced_stopped` / `escalated` / `truncated`。
- **paused 不是终态**——paused session 必须保留 checkpoint 供 `restoreSession` 恢复；不得对 paused 清理。

调用点（对称覆盖所有终态退出路径）：

1. 三个执行入口（`doExecute` / `resumeSession` / `restoreSession`）的 inner finally：`if (isTerminalStatus(session.getStatus())) checkpointManager.remove(sessionId)`。
2. cancel-without-handle 分支（session 不在 `runningExecutions`，不进 inner finally）：`session.setStatus(cancelled)` 后直接 `checkpointManager.remove(sessionId)`，与 inner finally 对称。

## 拒绝的替代方案

- **不**让 `close()` 取消在途执行——会引入隐式 cancel 语义，与显式 `cancelSession` 契约冲突。
- **不**对 paused session 清理 checkpoint——会破坏 crash/restore 恢复契约。
- **不**删除 on-disk journal/snapshot——它们是审计 trail，清理仅针对 in-memory cache 增长。
- **不**用独立 `IAgentEngine` 子接口承载 close——default 方法已满足源码兼容，无需分裂接口层次。
