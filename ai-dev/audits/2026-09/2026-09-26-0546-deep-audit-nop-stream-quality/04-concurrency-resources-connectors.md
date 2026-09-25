# 04 连接器质量与并发正确性（connectors×4 + runtime/core 并发面）

> 审计代理：独立只读子代理（连接器 24 文件全量精读；并发面逐类核实线程边界）；19 条经独立复核代理裁决：16 CONFIRMED / 3 PARTIAL / 0 REJECTED。路径相对 `nop-stream/`。

## 资源管理/泄漏

- [P1] runtime/.../transport/RemoteInputChannel.java:248,472-485 — 数据面订阅从不关闭：构造器 subscribe，close()（内部 cancel :474）全仓 main 零调用方（InputGate 无 close；StreamTaskInvokable:702/745/778/827 与 SubtaskTask:223 只关 operatorChain；GraphExecutionPlan:716 只关 bufferPool）→ 任务完成/失败/恢复重部署每 subtask 泄漏一个 IMessageService 订阅，共享派发线程被死订阅持续占用。修复：InputGate/channel 生命周期接入 close 链。
- [P1] runtime/.../execution/EmbeddedDistributedExecutor.java:142-152 — tm.start() 在 try 之外，registerNodesWithDiscovery(:147)/assembleCoordinator(:149-150) 抛出时已启动 TM 的心跳/任务线程无人回收；RpcDistributedExecutor.java:252-258+359-407 teardownOnStartupFailure 为先例（其注释自称 mirror embedded 的纪律，而 embedded 恰缺此段）。
- [P2] runtime/.../metrics/TaskNodeMetrics.java:35,42-43,85-90 + EngineMetrics.java:44,52-53,116-120 — BY_JOB/BY_NODE/GAUGE_STATE_REFS 无 remove 路径；同名 meter 重复注册返回旧实例、新 supplier 静默不生效（TM 重启后 gauge 指向旧对象，且死 TM 经 supplier 强引用存活到 JVM 退出）。
- [P3] runtime/.../taskmanager/TaskManager.java:221-245 — stop() 未 await heartbeatExecutor；completedTasks 不清空。
- [P3] connector/.../file/FileTwoPhaseCommitSink.java:97,331-332 — 静态 MANIFEST_JVM_LOCKS 按 outputDir 无界增长永不剔除。

## 并发正确性

- [P1] runtime/.../taskmanager/TaskManager.java:1131-1160（+JobCoordinator.java:1139-1181）— 2PC finishCommit（JDBC/文件事务提交）同步执行在 message-service 派发线程（RPC 模式经 StreamControlRpcServer→MessageRpcServer）/ coordinator checkpoint-persist-ACK 线程（embedded 模式），慢提交阻塞同线程心跳/assignment/ACK。修复：commit 转投独立 executor，保持失败可重试语义。
- [P2] connector-jdbc/.../JdbcTwoPhaseCommitSink.java:180-189,220-230,239-240 — initialized/dialect/insertDataSql 等 transient 普通字段；saveState（任务线程 barrier 路径）与 commit（提交通知线程）并发首次初始化，教科书式 unsafe lazy-init（NPE/重复初始化）。修复：同锁或 volatile+happens-before。
- [P2] runtime/.../taskmanager/TaskManager.java:269-312 — heartbeat() 后半段 runningTasks 循环（:288-302）无 try/catch（前半段 lease 已守卫），未捕获异常按 ScheduledExecutorService 语义永久杀死心跳 → liveness 全停 → 误判 stall 触发恢复风暴。
- [P2] runtime/.../transport/RemoteInputChannel.java:458-466,483,427 — injectElements 三处 queue.offer 不查返回值（恢复注入可静默丢数据）；close() EOS 哨兵 offer 未检查（满队列 reader 永远阻塞在 take()）；captureInFlightData EOS 复位 offer 同样未检查。对照数据路径 :590 有界等待+fail-typed。
- [P2] runtime/.../transport/DataPlaneMessageServiceAdapter.java:116-136 — 不可解码记录仅 LOG.warn 后丢弃（:129-131 异常、:133-136 null），绕过 RemoteInputChannel 的 decodeError fail-fast 机制。修复：毒丸/typed 失败替代丢弃。
- [P3] connector/.../file/FileSourceReader.java:119-144 — pollNext 在 synchronized(this) 内执行阻塞文件 I/O，checkpoint 线程 snapshotState 被慢盘阻塞。
- [P3] connector-batch/.../BatchLoaderSourceFunction.java:60 — currentOffset 非 volatile，依赖"仅 task 线程经 mailbox 读写"的隐式约定无编译期保障。

## 错误处理规范（两档）

- [P3] core/.../datastream/WindowedStreamImpl.java:215,230,248,263 与 runtime/.../checkpoint/PendingCheckpoint.java:61,138,141、EmbeddedDistributedExecutor.java:401 — 框架公共 API 层 `new StreamException(英文字符串)` 无 ErrorCode（违反公共 API 档 NopException+ErrorCode+.param()）。
- [P3] runtime/.../transport/RemoteInputChannel.java:591 — 模块内裸 IllegalStateException 作 error 载体（最终抛出处包了 ERR_STREAM_CHANNEL_OVERFLOW）。
- 连接器四模块本体干净：全部 StreamException+NopStreamErrors 错误码+.param()，消息全英文。

## 连接器契约（exactly-once/批处理边界）

- [P2] connector-jdbc/.../JdbcTwoPhaseCommitSink.java:431-455 — JDBC 批无上限：整 epoch 记录一次性进内存+单次 executeBatch，大 epoch OOM/驱动超限。修复：按 maxBatch 分段。
- [P2] connector/.../file/FileTwoPhaseCommitSink.java:238-250,536-550,463-490 — temp 写入与 manifest 原子替换均无 fsync/force：进程崩溃安全，OS 崩溃后可能"manifest 记已提交而数据丢失"。修复：rename 前 force(false)。
- [P3] connector/.../file/FileSourceReader.java:173 — `(FileInputStream) resource.getInputStream()` 盲强转。
- [P3] connector/.../file/FileSplitEnumerator.java:60,153,168 — nextSubtaskIndex 死状态字段（分配实际用 i % parallelism :122）。
- [P3] connector-batch/.../BatchConsumerSinkFunction.java:71-74 — 构造器内执行 provider.setup() I/O 且活 consumer 存 final 字段（分布式 Java 序列化路径将 NotSerializableException）。

## 线程卫生（结果良好）

所有 Executors.new* 均命名+daemon；SupervisionLoop 有界等待+fail-loud；recoveryPending CAS 去重+recoveryLock 串行化；InputGate CME 防护+add-first abort 序；TaskManager put 原子换槽+信号量守恒。未发现 P0 竞态。仅：[P3] Rpc/EmbeddedDistributedExecutor.java:278/196 TaskManager 容量硬编码 16 不可配置。

## 总体评价

连接器四模块质量高于平均（契约对称、异常路径保持 pendingCommits 一致、copyForSubtask 解决 parallelism>1 批次覆盖），短板在持久化原子性止步于 rename 未 fsync、JDBC 批无上限、2PC 占用控制面派发线程。并发面 Top 风险：①RemoteInputChannel 订阅泄漏 ②2PC 派发线程阻塞 ③embedded 启动失败泄漏 ④JDBC unsafe lazy init ⑤恢复/传输路径静默丢数据。
