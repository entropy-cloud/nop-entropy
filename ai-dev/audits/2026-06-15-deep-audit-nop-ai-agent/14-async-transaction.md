# 维度 14：异步与事务模式

**目标模块**: nop-ai-agent
**深挖轮次**: 2（第 1 轮 9 条 + 第 2 轮 4 条新增）

## 第 1 轮（初审）

### [维度14-01] DefaultAgentEngine 允许同一 session 并发执行，runningExecutions.put 静默覆盖 CancelHandle

- **文件**: `engine/DefaultAgentEngine.java:607-674, 642-654`
- **证据片段**:
  ```java
  // doExecute 无任何"session 已运行"预检查（对比 restoreSession:790 有 containsKey 检查）
  return CompletableFuture.supplyAsync(() -> {
      session.setStatus(AgentExecStatus.running);
      CancelHandle handle = new CancelHandle(ctx, Thread.currentThread());
      runningExecutions.put(sessionId, handle);   // put 而非 putIfAbsent
      ...
  // AgentSession.messages 是裸 ArrayList（AgentSession.java:30），非线程安全
  ```
- **严重程度**: P1
- **现状**: API 无"同一 session 已在执行"拒绝路径；runningExecutions.put 覆盖前任 handle。AgentSession.messages 是 ArrayList，status/updatedAt 非同步。
- **风险**: 同 sessionId 两次 execute 并发 mutate 同一 AgentSession → ArrayList 扩容 ArrayIndexOutOfBounds、消息丢失、token 计数覆盖；第二次 put 覆盖 handleA → cancelSession 只能取消 B，A 不可取消；finally remove 误删后继 handle。
- **建议**: doExecute 入口 putIfAbsent 抢占，已存在则拒绝；finally 用 remove(sessionId, handle) 条件移除。
- **信心水平**: 高
- **误报排除**: 已核对 AgentSession.messages 为 ArrayList 非 CopyOnWrite，无内部锁。
- **复核状态**: 独立复核**维持 P1**（触发条件现实：sessionId 是 caller-supplied；后果含 cancel 句柄丢失致僵尸会话）

### [维度14-02] DBDenialLedger.recordDenial 跨两条独立 Connection INSERT→COUNT，TOCTOU 竞态

- **文件**: `security/DBDenialLedger.java:122-158, 194-211`
- **证据片段**: INSERT（连接 A，try-with-resources 关闭）后 countDenials（连接 B，新 Connection SELECT COUNT）。javadoc 声称"thread safety guaranteed by DB operations"但跨连接非事务。
- **严重程度**: P1（注：与维度13-04 同源，此处从并发事务角度记录）
- **现状**: 同 session 并发 recordDenial：T1/T2 各自 INSERT 后 COUNT 可能同时返回 N-1，无人触发 pause；多实例共享 DB 时实例 A、B 对同 session 并发 deny 触发。
- **风险**: pause 决策非幂等非原子，拒绝阈值可被静默突破。
- **建议**: INSERT+COUNT 放进单 Connection 显式事务；或 INSERT...RETURNING COUNT(*)；或 SERIALIZABLE+重试。
- **信心水平**: 高
- **复核状态**: 已保留

### [维度14-03] 4 个 DB* 类全部 JDBC 操作从不设置 autoCommit，autoCommit=false 时静默不提交

- **文件**: `message/DBMessageService.java`, `reliability/DBCheckpointManager.java`, `session/DBSessionStore.java`, `security/DBDenialLedger.java`
- **证据片段**: 全模块 grep `setAutoCommit|commit()|rollback()` 零匹配；全部 try-with-resources 无 commit。
- **严重程度**: P2（独立复核从 P1 降级：主流连接池 HikariCP/Tomcat JDBC/Nop SimpleDataSource 默认 autoCommit=true，开箱场景不表现；JTA 场景表现可观测；非默认存储路径）
- **现状**: 所有 DB* 类假设 autoCommit=true 但从不显式声明。JTA/Spring managed DataSource 默认 autoCommit=false 时 close 会 rollback 未提交事务。
- **建议**: getConnection 后立即 setAutoCommit(true)；或文档化"DataSource 必须 autoCommit=true"。
- **信心水平**: 高
- **复核状态**: 独立复核**降级 P1→P2**

### [维度14-04] SessionFileWriter/CheckpointSnapshotWriter 直接覆盖目标文件（非原子写），崩溃留截断 JSON

- **文件**: `session/SessionFileWriter.java:73-87`; `reliability/CheckpointSnapshotWriter.java:64-78`
- **证据片段**:
  ```java
  Files.write(sessionFile, json.getBytes(StandardCharsets.UTF_8),
      StandardOpenOption.CREATE, StandardOpenOption.WRITE,
      StandardOpenOption.TRUNCATE_EXISTING);   // 非 tmp+ATOMIC_MOVE
  ```
  全模块 grep `ATOMIC_MOVE|Files.move|\.tmp` 零匹配。
- **严重程度**: P1
- **现状**: TRUNCATE+WRITE 非原子，JVM/OS 崩溃、磁盘满、kill 时目标文件 0 字节或部分字节。FileBackedSessionStore.listAllSessions 对 corrupt JSON 是 LOG.warn+skip（session 永久不可恢复）。
- **风险**: 专为 crash-recovery 设计的子系统（plan 183/184）"crash 时丢失正在恢复的 session"是功能性失败。silent skip 让运维难发现。
- **建议**: write-to-tmp + ATOMIC_MOVE + REPLACE_EXISTING；listAllSessions corrupt JSON 应进 failed 桶而非仅 skip。
- **信心水平**: 高
- **误报排除**: listAllSessions 的 corrupt-skip 容错反证这是真实隐患。
- **复核状态**: 独立复核**维持 P1**

### [维度14-05] DBMessageService.close 用 shutdownNow 强制中断 poller，CLAIMED 消息永久卡死无回收

- **文件**: `message/DBMessageService.java:133-147, 288-307`
- **证据片段**: close 调 poller.shutdownNow()；claimMessage UPDATE STATUS=CLAIMED；无任何"CLAIMED 行超时回收"SQL（findPending 仅 SELECT STATUS=PENDING）。
- **严重程度**: P2
- **现状**: 中断发生在 claim 后 markConsumed 前，该消息 STATUS 已 CLAIMED 但永远不会被后续 poll 拾起。
- **风险**: 违反"at-least-once delivery"+"JVM crash does not lose pending messages"承诺；跨实例 competing consumers 被破坏。
- **建议**: 启动期回收本实例遗留 CLAIMED；或基于 claimed_at 时间戳全局回收；或事务内 SELECT FOR UPDATE。
- **信心水平**: 中-高
- **复核状态**: 已保留

### [维度14-06] DBCheckpointManager loadedSessions 负缓存永久驻留，跨进程新增 checkpoint 后本进程永远看不见

- **文件**: `reliability/DBCheckpointManager.java:80, 154-166, 209-213`
- **证据片段**: loadedSessions 只有 put 无 remove/clear；ensureSessionLoaded putIfAbsent 后 loadSessionFromDb，空则 bySession 不写但 loadedSessions 已置位。
- **严重程度**: P2
- **现状**: 实例 A 首次访问 session X（无 checkpoint）→ 实例 B 写入 X 的 checkpoint → 实例 A 再访问仍返回 null 直至重启。
- **风险**: 违反"cross-process recovery"承诺；restorePendingSessions 批处理场景错过补充写入。
- **建议**: loadedSessions 加 TTL；或 saveCheckpoint 时 remove 触发 reload。
- **信心水平**: 高
- **复核状态**: 已保留

### [维度14-07] CancelHandle 持有 ForkJoinPool.commonPool worker 引用，forced cancel 中断可能波及无关任务

- **文件**: `engine/DefaultAgentEngine.java:497-525, 642-654, 579-587`
- **证据片段**: supplyAsync 默认 commonPool；cancelSession(forced=true) 调 handle.thread.interrupt()。
- **严重程度**: P2
- **现状**: commonPool 是 JVM 范围共享池，被 Arrays.parallelSort/stream parallel 复用。forced cancel 直接 interrupt worker，若 worker 已被池回收跑下一个任务则误伤。
- **建议**: 用专用 ExecutorService；用协作式 cancelRequested 标志。
- **信心水平**: 中
- **复核状态**: 已保留

### [维度14-08] ReAct 循环每个工具调用都同步全量 sessionStore.save，DBSessionStore 每次全量 MERGE+JSON 序列化

- **文件**: `engine/ReActAgentExecutor.java:667-673, 1031-1037, 1336-1341`; `session/DBSessionStore.java:202-230`
- **证据片段**: 每工具调用后 sessionStore.save(persisted)；DBSessionStore.save 全量 serialize + MERGE INTO CLOB，同步阻塞 ReAct worker。
- **严重程度**: P2
- **现状**: 长对话每工具一次全量序列化+CLOB 写入，I/O 开销 O(N) 累积。
- **建议**: 异步/防抖批量持久化；或仅写增量 delta；或配置开关。
- **信心水平**: 中-高
- **复核状态**: 已保留

### [维度14-09] DBCheckpointManager loadSessionFromDb 与 saveCheckpoint 之间 bySession 列表覆盖竞态

- **文件**: `reliability/DBCheckpointManager.java:105-151, 209-267`
- **证据片段**: loadSessionFromDb 行 265 `bySession.put(sessionId, list_B)` 整表替换；saveCheckpoint 行 148 `computeIfAbsent(...).add(cp1)`。
- **严重程度**: P2
- **现状**: 首次访问窗口内并发 saveCheckpoint + loadSessionFromDb，后者 put 覆盖前者创建的 list，cp1 从 bySession 视图丢失（DB 和 byWatermark 仍在）。
- **风险**: getLatestCheckpoint 返回陈旧 watermark，restoreSession 从更早点重放，可能重复执行副作用工具。
- **建议**: loadSessionFromDb 改 computeIfAbsent + addAll + 同步块。
- **信心水平**: 中
- **复核状态**: 已保留

## 深挖第 2 轮追加

### [维度14-10] DefaultAgentEngine 三处 supplyAsync 默认 commonPool，ReAct .join() 阻塞 worker，嵌套 call-agent 可致 commonPool 死锁

- **文件**: `engine/DefaultAgentEngine.java:642-650, 755-761, 869-875`; `engine/ReActAgentExecutor.java:948`; `tool/CallAgentExecutor.java:188-196`
- **证据片段**:
  ```java
  // DefaultAgentEngine.java:642 — 无显式 Executor
  return CompletableFuture.supplyAsync(() -> {
      ...
      result = executor.execute(ctx).toCompletableFuture().join(); // 阻塞 commonPool worker
  // ReActAgentExecutor.java:948 — 在 commonPool worker 内再 .join()
  CompletableFuture.allOf(futuresArray).join();
  // CallAgentExecutor.java:188 — 子 agent 又 engine.execute → supplyAsync(commonPool)
  future = engine.execute(execRequest);
  ```
- **严重程度**: P1
- **现状**: 三处 supplyAsync 无 Executor（默认 commonPool）；循环内 .join() 阻塞 worker；call-agent 嵌套时子 agent 又需 commonPool worker。CompletableFuture.join() 不走 ForkJoinPool.managedBlock，commonPool 不会补偿扩容。
- **风险**: 当顶层 call-agent 占满 commonPool 全部 worker（每个阻塞在 .join() 等子 agent），子 agent supplyAsync 永远等不到 worker → 整个 JVM commonPool 死锁。commonPool parallelism = availableProcessors-1，2 核机器单次 call-agent 嵌套即死锁。
- **建议**: DefaultAgentEngine 注入可配置 Executor（固定线程池/虚拟线程），所有 supplyAsync 显式传入。
- **信心水平**: 高
- **误报排除**: 非 14-07（cancel 中断波及），是 pool 饱和死锁，机制不同。
- **复核状态**: 独立复核**维持 P1**（机制成立：join 不走 managedBlock + 三处 supplyAsync 无 Executor + call-agent 嵌套；CallAgentExecutor orTimeout 最终会 timeout 失败但耗尽 60s 且 supplyAsync 堆积）

### [维度14-11] FileBackedSessionStore.remove 与并发 save 留下 cache/backend 不一致；DBSessionStore 同型

- **文件**: `session/FileBackedSessionStore.java:129-146, 240-247`; `session/DBSessionStore.java:119-133, 201-230`
- **证据片段**: remove 先 sessions.remove（清缓存）后 deleteIfExists（删文件）；save 先 write（写文件）后 sessions.put（更新缓存）。无锁/事务关联。
- **严重程度**: P2
- **现状**: 并发 save+remove 交错（remove.clearCache→save.writeBackend→save.updateCache→remove.deleteBackend）导致缓存有 session、后端没有。
- **风险**: 进程重启后 session 消失；更糟：remove 后内存仍持有 session 对象直到进程退出，违反"删除即不可访问"。
- **建议**: remove 改为先删后端再清缓存；或 per-sessionId 写锁；或 compute 原子化。
- **信心水平**: 高
- **复核状态**: 已保留

### [维度14-12] DBMessageService.subscribe 同 topic 第二次订阅静默覆盖前一个 consumer

- **文件**: `message/DBMessageService.java:191-207, 390-403`
- **证据片段**: consumers.put(topic, listener) 覆盖前一个；cancel 调 unregisterConsumer(topic) 是 topic 级 remove。对比 LocalMessageService 用 CopyOnWriteArrayList 按引用精确移除。
- **严重程度**: P2
- **现状**: 同 topic 重新订阅时 listener1 静默丢失且 sub1.cancel() 会删掉 listener2（当前 holder）。
- **风险**: 被踩踏的 listener 不再收消息（静默丢消息），cancel 语义错乱。多组件共享单例 DBMessageService 订阅同名 topic 时互相踩踏。
- **建议**: 改 ConcurrentHashMap<String, CopyOnWriteArrayList<Subscription>>；或同 topic 重复订阅 fail-fast。
- **信心水平**: 中-高
- **复核状态**: 已保留

### [维度14-13] FileBackedCheckpointManager loadSessionFromDisk 读 journal 与并发 saveCheckpoint append 无锁互斥，reader 见半截 section 并丢弃

- **文件**: `reliability/FileBackedCheckpointManager.java:233-280, 130-149`; `reliability/CheckpointJournalWriter.java:61-72`; `reliability/CheckpointJournalReader.java:104-114, 116-142`
- **证据片段**: CheckpointJournalWriter 用 ioLock 串行化 append，但 CheckpointJournalReader.readTextIfExists 完全不获取该锁；parseAll 对残缺 section catch 后 LOG.warning+continue（永久跳过）。
- **严重程度**: P2
- **现状**: loadSessionFromDisk 读 journal 时若并发 saveCheckpoint 正 append，Files.readString 读到末尾半截 section，被 parseAll skip+warn，loadedSessions 已置位不再 reload。
- **风险**: 该 checkpoint 在本进程生命周期内从 bySession 列表消失，getLatestCheckpoint 返回残缺。
- **建议**: reader 走 writer 同一把 ioLock；或 loadSessionFromDisk 重试机制。
- **信心水平**: 中
- **复核状态**: 已保留

## 维度复核结论

独立复核 4 条关键发现（E/F/G/H 对应 14-01/14-03/14-04/14-10）：
- 14-01（同 session 并发执行）：**维持 P1**（doExecute 无预检 + put 非 putIfAbsent + ArrayList 非同步）
- 14-03（autoCommit）：**降级 P1→P2**（主流连接池默认 autoCommit=true，开箱不表现）
- 14-04（非原子写）：**维持 P1**（TRUNCATE_EXISTING 实锤；listAllSessions silent skip 让 crash 丢 session）
- 14-10（commonPool 死锁）：**维持 P1**（机制成立：join 不走 managedBlock）

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 14-01 | P1 | DefaultAgentEngine.java:607-674 | 同 session 并发执行，put 覆盖 CancelHandle |
| 14-02 | P1 | DBDenialLedger.java:122-158 | INSERT+COUNT 跨连接 TOCTOU 竞态 |
| 14-04 | P1 | SessionFileWriter/CheckpointSnapshotWriter | 非原子写，崩溃留截断 JSON |
| 14-10 | P1 | DefaultAgentEngine/ReAct/CallAgentExecutor | commonPool 死锁（supplyAsync+.join+嵌套） |
| 14-03 | P2 | 4 个 DB* 类 | 不设置 autoCommit（降级） |
| 14-05 | P2 | DBMessageService.java:133-147 | shutdownNow 致 CLAIMED 消息卡死 |
| 14-06 | P2 | DBCheckpointManager.java | loadedSessions 负缓存永久驻留 |
| 14-07 | P2 | DefaultAgentEngine.java | forced cancel interrupt 波及无关任务 |
| 14-08 | P2 | ReActAgentExecutor.java | 每工具全量 sessionStore.save |
| 14-09 | P2 | DBCheckpointManager.java | loadSessionFromDb 与 saveCheckpoint 覆盖竞态 |
| 14-11 | P2 | FileBackedSessionStore/DBSessionStore | remove 与并发 save cache/backend 不一致 |
| 14-12 | P2 | DBMessageService.java:191-207 | 同 topic 重复订阅静默覆盖 consumer |
| 14-13 | P2 | FileBackedCheckpointManager | 读 journal 与 append 无锁互斥丢 section |
