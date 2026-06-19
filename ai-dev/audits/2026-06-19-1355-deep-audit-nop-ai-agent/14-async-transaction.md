# 维度 14：异步与事务模式 — nop-ai-agent

## 全局事实

- 全模块**无** `Thread.ofVirtual`/`newVirtualThreadPerTaskExecutor` 实际调用（模块使用平台线程）。
- 全模块**无** `setAutoCommit(false)`/`commit()`/`rollback()` —— 所有 JDBC 都是默认 auto-commit=true。
- 全模块**无** `txn()`/`afterCommit` 调用。
- 所有 raw JDBC 操作均使用 try-with-resources 关闭 Connection/PreparedStatement/ResultSet。**合规**。

## 第 1 轮（初审）

### [维度14-01] `engine.execute().orTimeout()` 在 call-agent 超时时不取消底层 ReAct 执行，导致子 Agent 在父 Agent 已"放弃"后继续消耗 LLM/DB 资源

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/tool/CallAgentExecutor.java:364-371` 和 `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java:763-784`
- **证据片段**:
  ```java
  // CallAgentExecutor.executeSubAgent (line 354-371)
  CompletableFuture<AgentExecutionResult> future;
  try {
      future = engine.execute(execRequest);          // 启动一个 supplyAsync(commonPool)
  } catch (Exception e) { /* ... */ }
  CompletableFuture<AgentExecutionResult> withTimeout = future.orTimeout(timeoutMs, TimeUnit.MILLISECONDS);
  return withTimeout
          .<AiToolCallResult>thenApply(result -> toToolCallResult(call, result, subSessionId))
          .exceptionally(e -> AiToolCallResult.errorResult(call.getId(),
                  "call-agent sub-agent execution failed or timed out: ..."));
  // DefaultAgentEngine.handleCallAgentRequest (line 763-765)
  CompletableFuture<AgentExecutionResult> future = this.execute(execRequest);
  AgentExecutionResult result = future.orTimeout(req.getTimeoutMs(), TimeUnit.MILLISECONDS).join();
  ```
- **严重程度**: P1
- **现状**: `CompletableFuture.orTimeout` 只会在 future 上 complete 一个 `TimeoutException`，**不会**取消底层 `supplyAsync` 任务。父 Agent 通过 call-agent 超时放弃等待后，子 Agent 的 supplyAsync worker 仍在 commonPool 上继续跑：继续发 LLM 请求、继续 `sessionStore.save` 持久化、继续写 checkpoint、继续累积 token usage。`cancelSession` API 存在但这两个超时路径都**没有**调用它。
- **风险**:
  1. **资源放大**：父 Agent 已超时返回失败，但子 Agent 还在持续消耗 LLM 配额、写 DB。一个被放弃的长任务可能跑数十分钟。
  2. **公共池饥饿**：超时累积的 zombie supplyAsync worker 占住 commonPool 线程。多个并发 call-agent 超时后，整个 JVM 的并发 agent 吞吐塌陷。
  3. **状态污染**：子 Agent 持续向 sessionStore.save，覆盖 `session.status` / `tokensUsed`；父 Agent 失败后下游若尝试恢复或读该子 session，看到的是被 zombie 污染的部分结果。
- **建议**: 在 `CallAgentExecutor.executeSubAgent` 和 `DefaultAgentEngine.handleCallAgentRequest` 的 `.exceptionally(e -> ...)` 分支中，识别 `TimeoutException`，调用 `engine.cancelSession(subSessionId, "call-agent timeout", true)` 触发两级取消。
- **信心水平**: 确定
- **误报排除**: `IAgentEngine.cancelSession` 是模块自己提供的 API（含两级取消语义、能 setCancelRequested 标志 + 中断 worker 线程），但 call-agent 的两个超时分支都没用它。
- **复核状态**: 未复核

---

### [维度14-02] `DBMessageService.markConsumed` / `releaseClaim` 失败时只记 ERROR log，消息卡在 CLAIMED 永不重投，构成静默消息丢失

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/message/DBMessageService.java:360-406`
- **证据片段**:
  ```java
  // releaseClaim (line 360-382)
  private void releaseClaim(String sid) {
      // ... build UPDATE ... SET STATUS = PENDING
      try (Connection conn = ...; PreparedStatement ps = conn.prepareStatement(sql)) {
          // ...
          ps.executeUpdate();
      } catch (SQLException e) {
          LOG.error("nop.ai.agent.message.db-release-claim-error:sid={}", sid, e);
          // ⚠ no throw, no retry — message stuck in CLAIMED forever
      }
  }

  // markConsumed (line 384-406)
  private void markConsumed(String sid) {
      // ... build UPDATE ... SET STATUS = CONSUMED
      try (Connection conn = ...; PreparedStatement ps = conn.prepareStatement(sql)) {
          // ...
          ps.executeUpdate();
      } catch (SQLException e) {
          LOG.error("nop.ai.agent.message.db-mark-consumed-error:sid={}", sid, e);
          // ⚠ no throw, no retry — message stuck in CLAIMED forever
      }
  }
  ```
  `findPending`（line 296-330）只查 `STATUS = PENDING`，因此 CLAIMED 状态的消息永远不会被任何后续 poll 拾取。
- **严重程度**: P1
- **现状**: `markConsumed` / `releaseClaim` 捕获 `SQLException` 后仅写 ERROR log 不抛出。结果：消息进入 CLAIMED 状态后，如果 `markConsumed`/`releaseClaim` 因瞬时 DB 问题失败，消息就**永久**卡在 CLAIMED —— `findPending` 永远不会再选中它，DB poller 永远不会重投，消费者以为它"已被处理"但实际处理结果丢了。
- **风险**:
  - **at-least-once 投递承诺被破坏**：类注释承诺"at-least-once delivery"，但实际在 markConsumed/releaseClaim 失败时降级为 at-most-once（甚至 at-none-once）。call-agent / send-message 这类跨 Agent 消息可能整个丢失。
  - **静默**：仅 ERROR log 没有 metric/告警/异常上抛，运维不知道有消息丢失。`ScheduledRecoveryManager` 也没扫描"长期 CLAIMED 但未 CONSUMED"的消息。
  - **不可恢复**：除非人工 SQL 修改状态，否则消息永远停留在 CLAIMED。
- **建议**:
  1. `markConsumed` 失败应抛出（让 `pollTopic` 的 catch 路径释放回 PENDING via `releaseClaim`，或重投）。
  2. 增加一个 sweep 扫描 `STATUS=CLAIMED AND CLAIMED_AT < now - reclaimTimeout` 的行，重置为 PENDING，作为最终兜底。
  3. 至少抛出异常让 `pollAllTopics` catch 能 log 并由下次 poll 重试。
- **信心水平**: 很可能
- **误报排除**: 这不是"日志吞异常"的同类误报。其它 log+swallow（如 `releaseLockQuietly`）都是幂等且有 lease/TTL 兜底；这里 CLAIMED 状态无任何兜底。
- **复核状态**: 未复核

---

### [维度14-03] ReAct 主循环的 LLM 调用与工具 fanout `.join()` 均无超时，单次 hang 可永久阻塞整个 agent session

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java:1268-1374`（LLM 调用）和 `1803-1815`（工具 fanout）
- **证据片段**:
  ```java
  // LLM call retry loop (line 1273-1374)
  while (true) {
      try {
          llmCallStart = System.currentTimeMillis();
          attemptResponse = chatService.call(request, null);   // ⚠ no timeout argument
          break;
      } catch (RuntimeException | Error ex) {
          // ... retry policy / circuit breaker ... no wall-clock cap
      }
  }

  // Tool fanout (line 1803-1815)
  List<CompletableFuture<ToolCallOutput>> futures = new ArrayList<>();
  for (ChatToolCall chatToolCall : allowedCalls) {
      // ... build aiToolCall
      futures.add(toolManager.callTool(...).thenApply(...));
  }
  @SuppressWarnings("unchecked")
  CompletableFuture<ToolCallOutput>[] futuresArray = futures.toArray(new CompletableFuture[0]);
  CompletableFuture.allOf(futuresArray).join();   // ⚠ no orTimeout, no per-tool cap
  ```
- **严重程度**: P1
- **现状**:
  1. `chatService.call(request, null)` 没有传超时（第二个参数传 null）。retry 循环靠 `retryPolicy.shouldRetry` 控制重试次数，但没有 wall-clock 上限 —— 如果某个 attempt 永不返回，整个 ReAct worker 永久阻塞。
  2. `CompletableFuture.allOf(futuresArray).join()` 等所有工具完成，没有 `orTimeout`。如果一个工具 hang，整个 ReAct 循环 hang 住 —— supplyAsync worker 永不释放、runningExecutions 永不 remove、session 永远停在 `running`、takeover lock 等 30 分钟 lease 过期才能被其他实例抢占。
- **风险**:
  - **资源永久占用**：单个工具或 LLM hang 占死一个 commonPool worker + 一个 session + 一个 takeover lock（直到 lease TTL）。
  - **取消无效**：`cancelSession(forced=true)` 通过 `Thread.interrupt()` 触发取消，但 interrupt 只对响应 interrupt 的阻塞调用有效。如果工具内部不响应 interrupt，forced cancel 也不奏效。
- **建议**:
  1. LLM 调用至少传一个超时（或在 retry loop 外包一个 wall-clock budget）。
  2. 工具 fanout 加 `.orTimeout(perToolTimeoutMs, ...)` 或 `allOf(...).orTimeout(roundBudgetMs, ...)`，且在 timeout 分支显式取消仍在跑的工具 future。
  3. 文档化 `chatService` 实现必须自带 socket/connect timeout。
- **信心水平**: 很可能
- **误报排除**: grep 全模块 `orTimeout|completeOnTimeout`：`DefaultAgentEngine.handleCallAgentRequest` 和 `CallAgentExecutor.executeSubAgent` 都用了 orTimeout，但 ReAct loop 的 LLM call 和工具 fanout 这两个最关键的位置却没用。设计文档 `02-execution-model.md` §六明确承诺"超时限制执行时长"。
- **复核状态**: 未复核

---

### [维度14-04] `DefaultAgentEngine` 三个入口的 `CompletableFuture.supplyAsync(一参)` 默认跑在 `ForkJoinPool.commonPool()`，无界提交+长任务+worker 阻塞在 `.join()` 可能使 commonPool 饥饿

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java:1889-1973`（doExecute）、`2175-2233`（resumeSession）、`2354-2408`（restoreSession）
- **证据片段**:
  ```java
  // doExecute (line 1889)
  return CompletableFuture.supplyAsync(() -> {
      ThreadLocalTenantResolver.set(tenantId);
      try {
          session.setStatus(AgentExecStatus.running);
          handle.thread = Thread.currentThread();
          // ...
          AgentExecutionResult result;
          try {
              result = executor.execute(ctx).toCompletableFuture().join();  // ⚠ BLOCKS worker
          } finally {
              // ...
          }
          // ...
      } finally {
          ThreadLocalTenantResolver.clear();
      }
  });
  // ⚠ No executor argument → ForkJoinPool.commonPool()
  ```
- **严重程度**: P2
- **现状**: 引擎把每个 agent 执行整段跑在 `ForkJoinPool.commonPool()`（默认 `Runtime.getRuntime().availableProcessors() - 1` 个线程，通常 3-7）。每个 worker 期间多次同步 `.join()` 阻塞。多 agent 并发会迅速耗尽 commonPool。
- **风险**:
  - **吞吐塌陷**：在 4 核机器上，第 4 个并发 agent.execute() 就开始排队。
  - **跨功能影响**：commonPool 也是 JVM 内 `parallelStream` 等的默认池。
  - **死锁面**：worker 在 `executor.execute(ctx).join()` 阻塞；ctx 内部工具 fanout 又提交更多 commonPool 任务（如 `call-agent` → 再 `engine.execute`）。
- **建议**:
  1. 给 `DefaultAgentEngine` 增加一个可注入的 `Executor`，三个 supplyAsync 改为两参版本 `supplyAsync(lambda, agentExecutor)`。
  2. 至少在文档/WARN：当前默认 commonPool 不适合生产高并发。
  3. 考虑直接用虚拟线程池（Java 21）—— `Executors.newVirtualThreadPerTaskExecutor()`。
- **信心水平**: 很可能
- **误报排除**: 模块自身在其他位置（spawn step）已识别这个问题并显式避开，且模块已用 Java 21 但没用虚拟线程。
- **复核状态**: 未复核

---

### [维度14-05] `DockerSandboxBackend.execute` 超时路径未对本地 `Process process` 调用 `destroyForcibly()`，依赖 docker CLI 自退出；docker daemon 不可达时本地进程可能泄漏

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/DockerSandboxBackend.java:173-189` 和 `321-338`
- **证据片段**:
  ```java
  // execute (line 173-189)
  boolean timedOut;
  try {
      timedOut = !process.waitFor(config.getWallSeconds(), TimeUnit.SECONDS);
  } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      timedOut = true;
  }

  if (timedOut) {
      killContainer(containerName);                       // ⚠ 只跑 docker kill/rm CLI
      awaitReader(reader, config.getWallSeconds());
      // ⚠ 没调 process.destroyForcibly() / process.waitFor(2s) 兜底
      long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
      throw new SandboxException(SandboxFailureReason.TIMEOUT, ...);
  }

  // killContainer (line 327-338) — 只跑 docker CLI，不碰本地 Process
  private static void killContainer(String containerName) {
      try { runShortCommand("docker", "kill", containerName); }
      catch (Exception ignored) { /* best-effort */ }
      try { runShortCommand("docker", "rm", "-f", containerName); }
      catch (Exception ignored) { /* best-effort */ }
  }
  ```
  对比 `NoOpSandboxBackend.killTree`（line 219-238）—— 那个会 `process.descendants().forEach(...destroyForcibly())` + `process.destroyForcibly()` + `process.waitFor(2s)`。
- **严重程度**: P2
- **现状**: 超时分支假设 `docker kill <name>` 会让容器退出，从而让本地 `docker run` Process 跟着退出。但如果 docker daemon 已经不可达，`docker kill` CLI 自身会失败或卡住，而本地 `docker run` Process 可能还活着。`process` 这个 Java Process 对象没有显式 destroy。
- **风险**:
  - **进程泄漏**：在 docker daemon flapping 的环境下，每次超时都可能泄漏一个本地 `docker run` Process。
  - **非对称清理**：`NoOpSandboxBackend` 已有正确的 `killTree` 实现，但 `DockerSandboxBackend` 没复用 —— 行为不对称。
  - **不可观测**：泄漏的 Process 没有任何 metric/log。
- **建议**:
  1. 超时分支在 `killContainer` 之后加 `process.destroyForcibly()` + `process.waitFor(2, TimeUnit.SECONDS)` 兜底（参考 NoOpSandboxBackend.killTree）。
  2. 或抽出一个共用 `killTree(process)` 静态方法在两个 backend 间共享。
- **信心水平**: 很可能
- **误报排除**: 具体对比了同模块 `NoOpSandboxBackend.killTree` 的正确实现 —— 它确实调 `destroyForcibly()`。`DockerSandboxBackend` 依赖外部 CLI 清理而忽略本地 ProcessHandle 是不对称行为。
- **复核状态**: 未复核

---

### [维度14-06] `DbSessionTakeoverLock` 默认 lease 30 分钟且无自动续约，长 Agent 执行超过 lease 后会被另一实例双执行

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/runtime/lock/DbSessionTakeoverLock.java:280-312`（tryRenew 未被自动调用）+ `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java:363-370`（lease 字段）
- **证据片段**:
  ```java
  // DefaultAgentEngine.java (line 363-370)
  // Plan 221 (L4-8-P4): lease duration in milliseconds for the takeover
  // lock. Default = 30 minutes (1_800_000 ms).
  // Passive TTL expiry: when the lock holder crashes, the lease
  // auto-expires and another instance can preempt — no background sweeper
  // thread is needed.
  private long lockLeaseMs = 1_800_000L;

  // tryRenew javadoc (DbSessionTakeoverLock line 280-281)
  /**
   * ... Reserved for manual / future use — the engine
   * does not auto-call it during ReAct iterations (auto heart-beat renew is
   * an explicit successor — see plan 221 Non-Goals).
   */
  @Override
  public boolean tryRenew(String sessionId, String ownerId, long leaseMs) { ... }
  ```
  ReAct 循环没有任何对 `tryRenew` 的调用 —— grep 全模块只有定义和测试，无生产调用。
- **严重程度**: P2
- **现状**: lease 默认 30 分钟。引擎在 doExecute/resumeSession/restoreSession 开头调一次 `tryAcquire`（拿 lease），整个 ReAct 循环 + 可能的 sustainer 多轮接续期间**不续约**。如果实际执行超过 30 分钟，lease 会过期。此时另一 JVM 实例的 `tryAcquire` 会走"stale-lock preemption"分支，成功拿走锁并启动**第二份**并行执行同一 session。
- **风险**:
  - **双执行**：两实例同时跑同一 session 的 ReAct 循环，两套 LLM 调用并发进行，两套 `sessionStore.save` 互相覆盖，session 状态不一致。
  - **工具副作用放大**：双执行会触发两次相同工具调用，可能产生重复扣费、重复文件写入。
- **建议**:
  1. **简单方案**：把 `lockLeaseMs` 默认值大幅上调（如 24h）。
  2. **正确方案**：在 ReAct 循环每次迭代开始调用 `tryRenew(sessionId, instanceId, lockLeaseMs)` —— heart-beat 续约。续约失败说明被抢占，主动 abort。
  3. 至少在 lease 即将到期时 WARN。
- **信心水平**: 很可能
- **误报排除**: 设计/代码注释（`tryRenew` javadoc + `lockLeaseMs` 字段注释）明确说"auto heart-beat renew is an explicit successor"，即承认这是个已知缺口。但默认 lease 30min 与 maxIterations 默认行为 + sustainer 的"无限接续"能力组合，触发条件并不罕见。
- **复核状态**: 未复核

---

### [维度14-07] `InMemoryActorRuntime` 自身无 `shutdown()`/`destroyAll()` API，引擎替换 runtime 时残留 Actor 线程不会自动停

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/runtime/InMemoryActorRuntime.java:117`（executors map）+ 整文件
- **证据片段**:
  ```java
  // executors map (line 117)
  private final ConcurrentHashMap<String, ExecutorService> executors = new ConcurrentHashMap<>();

  // 引擎替换 runtime：
  // DefaultAgentEngine.setActorRuntime (line 1315-1317)
  public void setActorRuntime(IActorRuntime actorRuntime) {
      this.actorRuntime = actorRuntime != null ? actorRuntime : NoOpActorRuntime.noOp();
      // ⚠ 不调旧 runtime 的 destroyAll / shutdown
  }
  ```
- **严重程度**: P3
- **现状**: `InMemoryActorRuntime` 没有 `shutdown()` 或 `destroyAll()` API（虽然 `destroyActor` 单个销毁存在，`destroyAll` 也存在 —— 但 destroyAll 只 destroy 已注册 actor，且没有方法清理 executors map 中孤儿条目）。引擎 setter 替换 runtime 时，旧 runtime 持有的所有 Actor 的单线程 executor 不会自动 shutdown。Actor 线程是 daemon（line 280），不会阻止 JVM 退出，但在长跑应用里替换 runtime 会泄漏线程。
- **风险**:
  - **测试噪声**：单元测试反复 setActorRuntime 不销毁 → 测试套件累积大量 idle actor 线程。
  - **生产长跑**：极少触发，但若配错重配，CPU 会持续被 1Hz 的 idle poll 消耗。
  - **Mailbox 引用泄漏**：每个 actor 持有 mailbox 引用。
- **建议**:
  1. 在 `IActorRuntime` 接口加 `void shutdown()`，`shutdown()` = 遍历 executors map `shutdownNow()` + `awaitTermination()`。
  2. `DefaultAgentEngine.setActorRuntime` 在替换前调旧 runtime 的 `destroyAll` + `shutdown`。
  3. 或者文档化"runtime 一旦启用不能替换"。
- **信心水平**: 有趣的猜测
- **误报排除**: 模块自己有明确的 `destroyActor` API 表明"销毁是设计意图"；`destroyAll` 已存在但没纳入引擎替换路径 —— 不对称。
- **复核状态**: 未复核

---

### [维度14-08] `SessionFileWriter.write` 在 `synchronized(ioLock)` 内做文件 IO，未来若迁移到虚拟线程会 pin carrier

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/session/SessionFileWriter.java:64-107`
- **证据片段**:
  ```java
  public void write(Path sessionFile, AgentSession session) {
      // ...
      String json = serialize(session);   // 序列化在锁外 ✓

      synchronized (ioLock) {              // ⚠ 进入 monitor
          Path tmp = sessionFile.resolveSibling(sessionFile.getFileName() + ".tmp");
          try {
              Path parent = sessionFile.getParent();
              if (parent != null) {
                  Files.createDirectories(parent);     // IO inside synchronized
              }
              Files.write(tmp, json.getBytes(StandardCharsets.UTF_8),
                      StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                      StandardOpenOption.TRUNCATE_EXISTING);   // IO inside synchronized
              Files.move(tmp, sessionFile, StandardCopyOption.ATOMIC_MOVE,
                      StandardCopyOption.REPLACE_EXISTING);   // IO inside synchronized
          } catch (IOException e) { /* ... */ }
          finally { /* delete tmp */ }
      }
  }
  ```
  类似的"synchronized 包 IO"模式在 `CheckpointJournalWriter.write` (line 61)、`CheckpointSnapshotWriter.write` (line 65) 也存在。
- **严重程度**: P3
- **现状**: 当前模块用平台线程，`synchronized + IO` 是合法的。但项目已是 Java 21，模块其他位置已提到 VT 迁移是 roadmap。如果未来把 `supplyAsync` worker 改成虚拟线程，`SessionFileWriter.write` 内的 synchronized 块会 pin carrier thread 直到 IO 完成。
- **风险**:
  - **未来 VT 迁移的隐性陷阱**：迁移到 VT 时这些 synchronized + IO 块会成为 pinning 点。
  - **当前并发限制**：`ioLock` 是 per-instance 锁。所有 session 的写串行化，影响吞吐。
- **建议**:
  1. 改用 `ReentrantLock` 替换 `synchronized`（VT-friendly）。
  2. 或者把 ioLock 改成 per-session-file（striped lock）。
  3. 至少在 javadoc 警告"contains blocking IO inside synchronized — pin carrier if migrated to virtual threads"。
- **信心水平**: 有趣的猜测
- **误报排除**: 审核维度 14 明确要求检查"synchronized 块内做 IO 的反模式（VT pinning 风险）"。当前代码**不是 bug**，但作为审计记录下来，未来 VT 迁移时是必修项。
- **复核状态**: 未复核

---

## 合规结论（无发现，已检查）

1. **`runningExecutions.putIfAbsent` 的正确性**：三个入口都用 `putIfAbsent` + 失败时 `releaseLockQuietly` + 抛异常；finally 用 `runningExecutions.remove(sessionId, handle)` 做值比较删除（消除 mutual-clobber 竞态）。**合规**。
2. **`DeferredAckMailbox` 的 3-phase reservation 并发正确性**：所有读写都用 `synchronized(lock)` 守护；deliveryId 用 AtomicLong；redelivery 分配新 deliveryId。**合规**。
3. **`InMemoryActorRuntime.runConsumptionLoop` 的并发正确性**：单线程 executor 保证 intra-actor 顺序；steering queue 是 `ConcurrentLinkedQueue`（无锁），跨线程安全。**合规**。
4. **`ScheduledRecoveryManager` / `TeamTaskSchedulerDaemon` / `DBMessageService` 的 stop/close 路径**：三者都实现 `synchronized start/stop`（或 `close`）+ `volatile Future` handle + `shutdownNow()` + `awaitTermination(timeout)`；都创建 daemon 线程。**合规**。
5. **跨 Connection 事务期望**：grep 确认无 `setAutoCommit(false)`、无 `commit()`、无 `rollback()`。所有 DAO 操作都是单 SQL 单事务。**合规**。
6. **`txn().afterCommit()` 使用**：grep 全模块零结果。本模块不依赖平台事务。**合规**。

## 维度复核结论

待独立复核子 agent 输出。

## 最终保留项

待复核完成后填写。
