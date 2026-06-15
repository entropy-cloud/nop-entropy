# 维度 14：异步与事务模式（nop-ai-agent）

## 第 1 轮（初审）

### [维度14-1] runningExecutions.put 不去重，并发 execute 同 sessionId 互覆并 remove 错位

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java:661-673`（同样模式在 `resumeSession:774-784`、`restoreSession:888-898`）
- **证据片段**:
  ```java
  return CompletableFuture.supplyAsync(() -> {
      session.setStatus(AgentExecStatus.running);
      CancelHandle handle = new CancelHandle(ctx, Thread.currentThread());
      runningExecutions.put(sessionId, handle);          // 665: 无 putIfAbsent
      AgentExecutionResult result;
      try {
          result = executor.execute(ctx).toCompletableFuture().join();
      } finally {
          runningExecutions.remove(sessionId);            // 671: 直接 remove，可能误删
          session.setStatus(ctx.getStatus());
      }
      ...
  ```
- **严重程度**: P1
- **现状**: 三处入口都用 `runningExecutions.put(sessionId, handle)`（无条件 put）+ `finally { runningExecutions.remove(sessionId); }`（无条件 remove）。没有 putIfAbsent，没有"session 已在执行"的快速失败。
- **风险**: 若同一 sessionId 被并发触发两次（典型场景：父 agent 并发调度两个 call-agent 工具，二者都向同一 subSessionId 发起 engine.execute），第二次 put 覆盖第一次的 handle。第一个执行结束时 finally 中的 remove 把第二个仍在运行的 handle 也清除掉。此后 cancelSession 永远找不到该 session，CancelHandle 语义被破坏。
- **建议**: 改为 `putIfAbsent` + 值比较 remove：`CancelHandle prev = runningExecutions.putIfAbsent(sessionId, handle); if (prev != null) throw new NopAiAgentException("session already executing: " + sessionId);`，finally 改为 `runningExecutions.remove(sessionId, handle)`。doExecute 应补 containsKey 检查（resume/restore 已有）。
- **信心水平**: 高
- **误报排除**: 已确认 CallAgentExecutor.executeSubAgent 显式接受 subSessionId 并通过 engine.execute 调用，sessionId 由 LLM 控制可达。
- **复核状态**: 未复核

### [维度14-2] cancelSession 与 supplyAsync 启动之间的 cancel 丢失窗口

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java:516-544`（cancelSession）与 `626-665`（doExecute 入队）
- **证据片段**:
  ```java
  public CompletableFuture<Void> cancelSession(String sessionId, String reason, boolean forced) {
      CancelHandle handle = sessionId != null ? runningExecutions.get(sessionId) : null;
      if (handle != null) {
          AgentExecutionContext ctx = handle.context;
          ctx.setCancelRequested(true);
          ...
      } else {
          AgentSession session = sessionStore.get(sessionId);
          ...
          session.setStatus(AgentExecStatus.cancelled);   // 537
          ...
      }
      ...
  }
  ```
  ```java
  private CompletableFuture<AgentExecutionResult> doExecute(...) {
      ...
      return CompletableFuture.supplyAsync(() -> {
          session.setStatus(AgentExecStatus.running);      // 662: 覆盖 cancel
          CancelHandle handle = new CancelHandle(ctx, Thread.currentThread());
          runningExecutions.put(sessionId, handle);        // 665
          ...
  ```
- **严重程度**: P1
- **现状**: `execute(request)` 立刻返回 `CompletableFuture`（任务已提交 ForkJoinPool）。在 supplyAsync 实际运行前，`runningExecutions` 为空。此时若外部调用 `cancelSession`，走 else 分支将 session.status 设为 cancelled。随后 supplyAsync 启动，第 662 行把 status 改回 running，cancel 信息丢失，executor 正常执行。
- **风险**: 用户可见的 cancel 被吞掉；CANCEL_REQUESTED 标志未设置（只在 handle != null 分支设置 ctx.cancelRequested），executor 没有检查到 cancel。
- **建议**: 在 supplyAsync lambda 内进入时检查 `if (session.getStatus() == AgentExecStatus.cancelled) {...}`；或者把 runningExecutions.put 提前到 supplyAsync 之外（同步 put + 异步执行 + finally remove），让 cancel 在任务入队后即可见。
- **信心水平**: 中-高
- **误报排除**: 已确认 ctx 在 doExecute 中构造，`ctx.setCancelRequested` 仅在 cancelSession 的 handle != null 分支调用。
- **复核状态**: 未复核

### [维度14-3] CompletableFuture.allOf().join() 不取消其他工具，单工具异常导致整轮失败

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java:979-991`
- **证据片段**:
  ```java
  futures.add(toolManager.callTool(chatToolCall.getName(), aiToolCall, toolExecCtx)
          .thenApply(result -> new ToolCallOutput(chatToolCall, result)));
  }

  @SuppressWarnings("unchecked")
  CompletableFuture<ToolCallOutput>[] futuresArray = futures.toArray(new CompletableFuture[0]);
  CompletableFuture.allOf(futuresArray).join();           // 985: 任一异常 → CompletionException

  for (CompletableFuture<ToolCallOutput> f : futuresArray) {
      ToolCallOutput output = f.join();                    // 988: 此循环不会执行
      ...
  ```
- **严重程度**: P1
- **现状**: 并发工具调度用 `allOf().join()` 同步屏障。一旦任一工具的 future 异常完成（非 errorResult，而是真实异常），`allOf` 立即异常完成，`.join()` 抛 CompletionException 被外层 catch 捕获，整个 execute() 以 failed 状态结束。
- **风险**: (a) 其他工具的结果丢失；(b) 其他仍在运行的工具未被 cancel，会继续在后台执行（无超时、无中断），消耗 LLM 配额/副作用工具仍会落地；(c) ReAct 的设计意图是单个工具失败应作为 toolResponse 错误消息反馈给 LLM 重新规划，而不是终止会话。
- **建议**: 用 `exceptionally`/`handle` 在每个 future 链路上把异常转换为 errorResult（参考 `CallAgentExecutor.executeSubAgent:218-222` 的 `.exceptionally(e -> AiToolCallResult.errorResult(...))` 模式）。框架层不应假设所有工具都自包装异常。
- **信心水平**: 高
- **误报排除**: 已逐个检查三个 functional tool executor 对同步异常是安全的；但 doExecuteAsync 内部 future 链或第三方 IToolExecutor 实现返回未包装异常 future 时仍触发本问题。
- **复核状态**: 未复核

### [维度14-4] checkpoint 写入与 sessionStore.save 非原子（违反 messageCount ≤ session.messageCount 不变量）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java:1042-1074`（同样模式在 `LLM_TURN:673-700`、`COMPACTION:1352-1379`）
- **证据片段**:
  ```java
  checkpointManager.saveCheckpoint(Checkpoint.of(...));   // 1042-1055
  checkpointSeq[0]++;

  // Plan 183 Phase 1 intra-execution persistence
  if (sessionStore != null) {
      AgentSession persisted = sessionStore.get(sessionId);
      if (persisted != null) {
          persisted.replaceMessages(ctx.getMessages());
          sessionStore.save(persisted);                   // 1072: 独立的二次 I/O / JDBC 操作
      }
  }
  ```
- **严重程度**: P2
- **现状**: DB 路径下 `DBCheckpointManager.saveCheckpoint` 用一条独立 Connection 的 INSERT（auto-commit），`DBSessionStore.save` 用另一条独立 Connection 的 MERGE INTO（auto-commit）—— 两次独立事务。文件路径下两次独立的 `Files.write`。无协调。
- **风险**: 若 checkpoint 写入成功但 session 写入失败（IO 错误/DB 约束违反/进程在两次操作之间崩溃），持久化的 checkpoint.messageCount 会大于持久化的 session.messageCount，违反 restoreSession 中显式断言的不变量。
- **建议**: 在 DB 后端为 saveCheckpoint + session.save 引入统一事务接口；或显式接受"最终一致"语义并在文档标注。
- **信心水平**: 中-高
- **误报排除**: 已确认两表不同，不能用单条 SQL 原子写入。
- **复核状态**: 未复核

### [维度14-5] FileBacked* 写文件非原子（truncate+write，无 tmp+rename），崩溃留半写文件

- **文件**:
  - `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/session/SessionFileWriter.java:73-87`
  - `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/session/FileBackedSessionStore.java:251-258`
  - `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/CheckpointSnapshotWriter.java:64-78`
- **证据片段** (SessionFileWriter):
  ```java
  synchronized (ioLock) {
      try {
          Path parent = sessionFile.getParent();
          if (parent != null) {
              Files.createDirectories(parent);
          }
          Files.write(sessionFile, json.getBytes(StandardCharsets.UTF_8),
                  StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                  StandardOpenOption.TRUNCATE_EXISTING);   // 79-81: 先截断再写
      } catch (IOException e) {
          throw new NopAiAgentException(...);
      }
  }
  ```
- **严重程度**: P1
- **现状**: `Files.write(..., TRUNCATE_EXISTING)` 先把文件长度截断到 0，再写入新内容。整个过程不是原子的：JVM 在 TRUNCATE 之后、write 完成之前崩溃，磁盘上留下一个 0 字节或部分字节的文件。
- **风险**: 重启后 `FileBackedSessionStore.listAllSessions` 会 warn-skip 该 session，等于丢失会话历史。Javadoc 多处宣称 "crash-survival semantics"，但当前实现无法兑现。
- **建议**: 标准 crash-safe 写法：`Path tmp = ...resolveSibling(... + ".tmp"); Files.write(tmp, bytes, CREATE, WRITE, TRUNCATE); Files.move(tmp, sessionFile, ATOMIC_MOVE, REPLACE_EXISTING);`。
- **信心水平**: 高
- **误报排除**: grep 全模块无 `Files.move`/`ATOMIC_MOVE`，确认所有持久化路径都没用 atomic-rename 模式。
- **复核状态**: 未复核

### [维度14-6] cancelSession 的 thread.interrupt() 对 chatService.call 无立即效果，且 AgentSession.status 非 volatile

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java:528-530`（interrupt）与 `session/AgentSession.java:21,142-144`（status 字段）
- **证据片段**:
  ```java
  // DefaultAgentEngine
  if (forced) {
      handle.thread.interrupt();   // 529
  }
  ```
  ```java
  // AgentSession
  private AgentExecStatus status;        // 21: 非 volatile
  public AgentExecStatus getStatus() { return status; }
  public void setStatus(AgentExecStatus status) { this.status = status; }
  ```
- **严重程度**: P2
- **现状**: `cancelSession(forced=true)` 调用 `handle.thread.interrupt()`，但执行线程此时阻塞在 `chatService.call`（同步 HTTP 调用）。多数 HTTP 客户端不可中断。同时 cancelSession 的 else 分支 `session.setStatus(cancelled)` 由非拥有线程写入，status 非 volatile，写入可见性无保证。
- **风险**: 用户期望 forced=true 能立即中止，但实际仍要等当前 LLM 调用返回（可能 30-60 秒）。
- **建议**: (a) 文档化 forced 的实际语义；(b) 把 AgentSession.status 设为 volatile 或用 AtomicReference；(c) 考虑 cancel timeout。
- **信心水平**: 中
- **误报排除**: 已确认 AgentSession 所有可变字段都没有 volatile 修饰。
- **复核状态**: 未复核

### [维度14-7] DBMessageService.DbSubscription.cancel() 误删重订阅后的新消费者

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/message/DBMessageService.java:198-207, 399-403`
- **证据片段**:
  ```java
  public IMessageSubscription subscribe(String topic, IMessageConsumer listener, ...) {
      ...
      consumers.put(topic, listener);          // 198: 无条件覆盖
      DbSubscription subscription = new DbSubscription(topic);
      return subscription;
  }

  void unregisterConsumer(String topic) {
      consumers.remove(topic);                 // 205: 按 key 删，不看 value
      ...
  }

  @Override
  public void cancel() {
      cancelled = true;
      unregisterConsumer(topic);               // 402: cancel 时直接 remove 当前 topic 的注册
  }
  ```
- **严重程度**: P2
- **现状**: 同一 topic 二次 subscribe 会用新 listener 覆盖旧 listener，但旧 sub1 调用 cancel() 时 `consumers.remove(topic)` 会把 topic 整条删掉，连累新 sub2 的 listener 也被移除。
- **风险**: 订阅管理出现幻读——cancel 一个旧订阅让整个 topic 失活，后续消息不再被任何消费者接收。
- **建议**: 用 `consumers.remove(topic, listener)`（带值比较的 remove）。
- **信心水平**: 中
- **误报排除**: 已确认 `consumers` 是 ConcurrentHashMap，remove(topic) 只按 key 删。本模块未发现使用方真的二次订阅同一 topic，触发概率低。
- **复核状态**: 未复核

### [维度14-8] DBMessageService 异步消费者无超时，未完成 future 让消息永久卡在 CLAIMED

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/message/DBMessageService.java:343-359`
- **证据片段**:
  ```java
  @SuppressWarnings("unchecked")
  private void handleConsumerResult(String sid, Object result) {
      if (result instanceof CompletionStage) {
          ((CompletionStage<Object>) result).whenComplete((r, e) -> {
              if (e != null) {
                  LOG.error(...);
                  releaseClaim(sid);
              } else {
                  handleConsumerResult(sid, r);
              }
          });
          // 没有 orTimeout / 没有 fallback releaseClaim
      } else if (result instanceof ConsumeLater) {
          releaseClaim(sid);
      } else {
          markConsumed(sid);
      }
  }
  ```
- **严重程度**: P2
- **现状**: 消费者返回 `CompletionStage` 时，用 whenComplete 注册回调等待 future 完成。若 future 永不完成，消息停留在 CLAIMED 状态：既不会被 CONSUMED，也不会被 releaseClaim 回到 PENDING，poller 也不会再次选取它。
- **风险**: 消息黑洞。文档自称 at-least-once delivery，但此路径下消息既未交付成功也未重投。
- **建议**: 在 CompletionStage 分支附加 bounded timeout：`.orTimeout(processingTimeoutMs, MILLISECONDS)`，超时按异常路径 releaseClaim。
- **信心水平**: 中
- **误报排除**: 已确认 `LocalAgentMessenger.request` 走 `future.orTimeout` 自带超时，但消息处理器返回 CompletionStage 时没有强制 timeout。
- **复核状态**: 未复核

### [维度14-9] DBMessageService.sendAsync 不检查 closed 状态，关闭后仍可 INSERT

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/message/DBMessageService.java:134-147, 159-189`
- **证据片段**:
  ```java
  @Override
  public synchronized void close() {
      closed = true;                          // 135: volatile
      if (poller != null) {
          poller.shutdownNow();
          ...
      }
      started = false;
  }

  @Override
  public CompletionStage<Void> sendAsync(String topic, Object message, ...) {
      Objects.requireNonNull(topic, "topic must not be null");
      Objects.requireNonNull(message, "message must not be null");
      // 没有 closed 检查
      ...
      try (Connection conn = dataSource.getConnection();
           PreparedStatement ps = conn.prepareStatement(sql)) {
          ...
          ps.executeUpdate();                 // close 后仍执行
      }
      return CompletableFuture.completedFuture(null);
  }
  ```
- **严重程度**: P3
- **现状**: `close()` 设置 closed=true 并 shutdown poller；`sendAsync` 没有 closed 检查。close 之后调用 sendAsync 仍然走 INSERT，但 poller 已停永远不会被消费。
- **风险**: 单实例部署下消息黑洞。
- **建议**: `sendAsync` 开头加 `if (closed) throw new IllegalStateException("DBMessageService is closed");`。
- **信心水平**: 中
- **误报排除**: 已确认 sendAsync 没有任何 closed 检查。
- **复核状态**: 未复核

### [维度14-10] supplyAsync 默认用 ForkJoinPool.commonPool 跑长 LLM 调用

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java:661, 774, 888`
- **证据片段**:
  ```java
  return CompletableFuture.supplyAsync(() -> {           // 无第二个参数 executor
      session.setStatus(AgentExecStatus.running);
      CancelHandle handle = new CancelHandle(ctx, Thread.currentThread());
      runningExecutions.put(sessionId, handle);
      AgentExecutionResult result;
      try {
          result = executor.execute(ctx).toCompletableFuture().join();  // 阻塞数十秒
      } ...
  ```
- **严重程度**: P3
- **现状**: 三处 supplyAsync 都用默认 executor（ForkJoinPool.commonPool），其并行度 = CPU 核数 - 1。在 commonPool 上跑长任务。
- **风险**: 同 JVM 内的 parallel stream、其他 supplyAsync 链路被饿死；多 agent 并发执行时容易被 commonPool 容量限制阻塞。
- **建议**: 引入 dedicated ExecutorService，通过构造器或 setter 注入，所有 supplyAsync 显式传入。
- **信心水平**: 中
- **误报排除**: 已确认 ReActAgentExecutor.execute 内部主体是同步循环，调用方 join() 阻塞等待。
- **复核状态**: 未复核

## 零发现项（已检查未发现）

1. JDBC Connection/PreparedStatement/ResultSet 全部 try-with-resources，无泄漏。
2. ConcurrentHashMap 使用正确；bySession 配合 synchronized(list) 块访问。
3. 接口泛型精度准确，无 raw type 滥用。
4. JSON 反序列化类型安全（deserialize 均 instanceof Map + fail-fast）。
5. 无死锁风险（ioLock 不嵌套，replySubscriptions synchronized 不等待 future）。
6. CancelHandle 语义（除 14-1/14-2/14-6 外）在 ReAct 循环两处关键位置覆盖到位。
7. LocalAgentMessenger.request 的 correlationId 碰撞处理正确。

## 维度复核结论

| 发现 | 复核结论 | 理由 |
|------|---------|------|
| [维度14-1] put/remove 不去重 | **保留 P1** | put 无 putIfAbsent + remove 无值比较确证。并发 sessionId 由 LLM 可控。 |
| [维度14-2] cancel 丢失窗口 | **保留 P1** | supplyAsync 异步启动 + status 覆盖确证。 |
| [维度14-3] allOf.join 单工具异常终止整轮 | **保留 P1** | allOf 语义确证。ReAct 设计意图与实现不符。 |
| [维度14-4] checkpoint/session 非原子 | **保留 P2** | 两次独立 Connection/Files.write 确证。 |
| [维度14-5] 非原子文件写 | **保留 P1** | TRUNCATE_EXISTING 确证，无 ATOMIC_MOVE。Javadoc 宣称 crash-survival 但无法兑现。 |
| [维度14-6] interrupt 无效 + status 非 volatile | **保留 P2** | 字段修饰符确证。 |
| [维度14-7] DbSubscription.cancel 误删 | **保留 P2** | remove(topic) 按 key 删确证。 |
| [维度14-8] 消费者 future 无超时 | **保留 P2** | handleConsumerResult 无 orTimeout 确证。 |
| [维度14-9] sendAsync 不检查 closed | **保留 P3** | sendAsync 无 closed 检查确证。 |
| [维度14-10] commonPool 跑长任务 | **保留 P3** | supplyAsync 无 executor 确证。 |

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 14-1 | P1 | engine/DefaultAgentEngine.java:661-673 | runningExecutions put/remove 不去重，并发 sessionId 互覆 |
| 14-2 | P1 | engine/DefaultAgentEngine.java:516-544 | cancel 与 supplyAsync 启动间的 cancel 丢失窗口 |
| 14-3 | P1 | engine/ReActAgentExecutor.java:979-991 | allOf().join() 单工具异常终止整轮，其他工具不取消 |
| 14-4 | P2 | engine/ReActAgentExecutor.java:1042-1074 | checkpoint 写入与 sessionStore.save 非原子 |
| 14-5 | P1 | session/SessionFileWriter.java:73-87 | FileBacked 写文件非原子，崩溃留半写文件 |
| 14-6 | P2 | engine/DefaultAgentEngine.java:528 + session/AgentSession.java:21 | interrupt 无立即效果，status 非 volatile |
| 14-7 | P2 | message/DBMessageService.java:198-207 | DbSubscription.cancel() 误删重订阅后的新消费者 |
| 14-8 | P2 | message/DBMessageService.java:343-359 | 异步消费者无超时，未完成 future 让消息永久卡 CLAIMED |
| 14-9 | P3 | message/DBMessageService.java:159-189 | sendAsync 不检查 closed 状态 |
| 14-10 | P3 | engine/DefaultAgentEngine.java:661 | supplyAsync 默认用 ForkJoinPool.commonPool 跑长 LLM 调用 |
