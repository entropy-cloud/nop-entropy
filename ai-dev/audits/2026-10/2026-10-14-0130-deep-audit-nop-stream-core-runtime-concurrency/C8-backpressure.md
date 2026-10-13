# C8 背压、流控与过载行为 — nop-stream-core-runtime（第 1 轮初审）

- **审计维度**: C8（背压、流控与过载行为）——目标：验证上游快于下游时系统有界降级，而非无界积压或静默丢弃
- **审计对象**: `nop-stream/nop-stream-core/src/main/java`（439 files / 57,135 lines）、`nop-stream/nop-stream-runtime/src/main/java`（132 files / 29,922 lines）；`_` 前缀生成文件不在发现对象内
- **审计方式**: 盲审（未读 ai-dev/audits、plans、bugs、lessons 下任何文件），只审当前 live code
- **前置阅读**: AGENTS.md、docs-for-ai/INDEX.md、docs-for-ai/03-modules/nop-stream.md（owner doc）、ai-dev/skills/concurrency-resource-audit-prompt.md 共享前缀 + C8 正文
- **定向对照**: 缺陷族 F2（过载拒绝路径「半个成功」）、F4（无界账本）、F7（OOM 同族）、F9（静默吞异常）；平台 `QueueOverflowPolicy`/`ISemaphore`/`IRateLimiter` 该用未用形态
- **方法**: 先逐文件核对数据面通道三元组（ResultPartition/BufferPool/InputChannel/RemoteInputChannel/RemoteResultPartition）、mailbox 与 timer 驱动、runtime 全部线程池边界与过载策略，再对照 F2 逐条核查过载拒绝路径的恢复语义

---

## 一、生产-消费边界清单

C8 正文要求逐条核对每条边界的队列有界性、满载行为与声明语义一致性。下表为完整盘点（✓=判定一致，✗=存在发现，○=已声明边界非缺陷）：

| # | 边界 | 队列/预算 | 满载行为 | 判定 |
|---|---|---|---|---|
| B1 | 算子链 tail → `RecordWriter` → 本地 `ResultPartition` | `LinkedBlockingQueue(capacity)`；capacity 来自 `EdgeConfig.queueCapacity`（默认 1024）+ 可选 per-job `BufferPool`（公平信号量，默认 `DEFAULT_GLOBAL_BUFFER_CAPACITY=65536`，`GraphExecutionPlan.java:96`） | 阻塞 `queue.put`（生产者背压），池耗尽时阻塞 `acquire`；interrupt 安全（permit 归还，`ResultPartition.java:356-364`） | ✓（发现 001 的 overflow-bypass 例外见下） |
| B2 | B1 的 materialization 变体（`JobEdge.setMaterializationEnabled(true)`，opt-in） | 队列不变，但满载时改走 overflow-bypass：元素**只**入 `InMemoryMaterializationPoint`（无界 ArrayList） | `queue.offer` 非阻塞 + permit 立即释放；活动运行期消费者不可见，仅 region 重启重放可见 | ✗ C8-001 |
| B3 | `ResultPartition` → `InputChannel` → `InputGate` → 任务线程 | 无界读侧（消费即释放）；50ms 有界 poll（`CHANNEL_POLL_TIMEOUT_MS`）+ 250ms idle-return | 阻塞消费，producer-death 由 coordinator liveness（taskTimeoutMs）兜底；通道心跳超时 opt-in（生产默认关闭，`InputGate.java:79-86` 自述） | ○ |
| B4 | `RemoteResultPartition` → `IMessageService` 后端（跨 JVM） | 无本地队列（`super(1)`，G53 有意排除 BufferPool——类注释声明）；DB 后端 WI6 门控：TTL 200ms 本地计数 + 达限锁外 100ms 分片等待 10s | 达限 10s → typed `ERR_STREAM_SEND_GATE_TIMEOUT` → task 失败重启（owner doc「DB 数据面生产者背压」节声明）；Kafka/Pulsar 自带背压 | ○（但 launch 路径后端 `PollingJdbcMessageService` 不实现 `ChannelBacklogProbe` → 门控恒为诚实 no-op，见 C8-003） |
| B5 | message 后端派发线程 → `RemoteInputChannel` 本地队列 | `LinkedBlockingQueue(1024)`（`DEFAULT_QUEUE_CAPACITY`）+ 有界入队等待 10s | 超时 → typed `ERR_STREAM_CHANNEL_OVERFLOW` + `finished=true` + EOS 哨兵 → 任务失败，恢复重订阅从后端重放（恢复语义闭环，非半个成功）；decode 失败同型 typed 失败 | ✓ |
| B6 | `PollingJdbcMessageService` poller（launch 模式 2 线程共享池）→ 各订阅 consumer | **无 LIMIT、无批上限**：每 poll 物化全部 `id > cursor` 行 | 积压全量进内存 | ✗ C8-002 |
| B7 | `PollingJdbcMessageService.send` → `nop_stream_msg_queue` 表 | 无界 INSERT-only 账本，无 TTL/归档/删除（`clearTopics` 为 test-only） | 表无限增长 | ✗ C8-003 |
| B8 | `ProcessingTimeServiceDriver`（100ms tick）→ `TaskMailbox` 控制队列 | `ConcurrentLinkedQueue` 无界，无 coalescing、无深度指标 | 任务线程被背压阻塞期间每 tick 一条 fire mail 无界积累 | ✗ C8-004 |
| B9 | `TaskMailbox` → 任务线程安全点（`processInputGate` loop top / SOURCE `collect()`） | drain-and-run 全量执行 | `emit` 阻塞在 B1 期间无法到达 loop top（与 B8 叠加成 C8-004）；解除后一次性 drain 全部陈旧 mail | ○（机制）/ ✗（与 C8-004 合并） |
| B10 | checkpoint 完成通知 → `TaskManager.commitExecutor`（2PC commit） | 单线程 + 有界队列（`commitQueueCapacity` 可配） | CallerRuns 兜底（不丢通知）+ `nop.stream.task.commitCallerRuns.total` 计数 + `commitQueueDepth` gauge（owner doc plan 369 R5-CC-10） | ✓ |
| B11 | task 终态上报 → `terminalReportExecutor` | 单线程 + 队列 64 | 拒绝即 inline 执行（含 shutdown 后，CC-17），终态报告不丢；stop() 先 drain 再放行 shutdown hook | ✓ |
| B12 | task 部署 → `taskExecutor` + `capacitySemaphore` | 池=capacity、队列 1000、AbortPolicy | 拒绝 → typed rejectDeploy（CC-17），信号量钉住在途任务 | ✓ |
| B13 | TM 心跳 → `heartbeatExecutor`（单线程 scheduleAtFixedRate） | — | 全迭代 try/catch 包裹（防 JDK 语义单异常杀循环，`TaskManager.java:442-447`）；任务线程 hang 由 liveness 时间戳（`lastActivityTime`）上报 | ✓ |
| B14 | 作业事件 → `StreamJobEventBus` → listener | 同步派发（CopyOnWriteArrayList） | listener 异常捕获记录不阻断控制路径（声明契约）；内建 listener 均快速返回 | ○ |
| B15 | 告警 → `WebhookAlertChannel` | `ArrayBlockingQueue(256)` 异步单线程投递 | 满则丢 + WARN（owner doc「慢/不可达 webhook 不阻塞控制路径，队列满丢弃并 WARN」）；重试有界（2 次 + 可配退避） | ✓（遗留：`delivered` 无界列表，C8-007） |
| B16 | REST/`/metrics` → `StreamOpsHttpServer` | `Executors.newFixedThreadPool(4)` | 有界池；低频运维面 | ✓ |
| B17 | enumerator → `ReaderChannel.pendingSplits`（`LocalSourceCoordinator.java:205`） | synchronizedList 无界 | 控制面、增速 = split 分配速率，受 split 总量约束；回调注册前后有 drain-pending 竞态补发 | ○ |
| B18 | source 限流：`RateLimitedSourceFunction` + `SourceRateLimiter`（WI5） | 双通道令牌桶，桶空起算，100ms 睡片 | 阻塞即背压（声明语义）；interrupt 即时传播，无 permit 泄漏（单线程持有，无共享计数器） | ✓ |
| B19 | 2PC sink `pendingCommits` 账本（内存 + checkpoint 持久化） | 每 epoch 一条，commit 成功即删 | commit 失败保留条目由下一 epoch subsuming commit / restore 重放覆盖（`TwoPhaseCommitSinkFunction.finishCommit/restoreFromEpoch`） | ✓（F2 核查通过） |
| B20 | unaligned checkpoint 通道状态捕获（`captureInFlightData`） | 捕获集=当时队列驻留（≤ 队列容量/通道） | 有界；恢复注入逐条校验 offer、失败 typed 溢出（`RemoteInputChannel.injectElements`） | ✓ |

**平台设施该用未用核对**：`QueueOverflowPolicy` 未被 nop-stream 使用——本地通道用 JDK `LinkedBlockingQueue.put`（阻塞=背压）与 `offer`（overflow-bypass），remote 通道用有界 offer+typed 溢出。两种满载行为都有显式声明与 typed 失败面，与平台策略枚举（阻塞/拒绝/降级）等价，不构成该用未用缺陷；C8-001 是 overflow-bypass 变体声明语义与实际后果的偏差，非策略缺失。`ISemaphore`/`HighWatermarkSemaphore`：TaskManager 用 JDK `Semaphore` 做 capacity 钉扎（等价且更简单），`BufferPool` 用公平 `Semaphore`（公平性有明确裁定注释），未发现许可泄漏（acquire 后全部异常路径均有 release 配对：`ResultPartition.java:360-364`、`BufferPool.acquire` 的 close 竞态归还 `BufferPool.java:66-72`）。`IRateLimiter`：source 侧自建 `SourceRateLimiter`（声明不引 nop-retry 同理的轻量选择，单线程使用、可序列化随 state 持久化，平台 `IRateLimiter` 不 Serializable——不构成该用未用）。

**过载可观测现状**（C8 步骤 4）：通道队列填充比/深度 gauge（WI16 D2b/D2c）、emit 时间背压代理、busy/backpressured/idle 占比、DB backlog gauge、commit 队列深度 + CallerRuns 计数均已接线且与 owner doc 名表一致；缺口见 C8-006。

---

## 二、发现条目

### [nop-stream-core-runtime-C8-001] materialization 边的 overflow-bypass 使队列背压失效：`InMemoryMaterializationPoint` 无界增长 + 活动运行期记录静默跳过

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/ResultPartition.java:346-376`、`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/materialization/InMemoryMaterializationPoint.java:24-42`、`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/GraphExecutionPlan.java:392-402`
- **证据片段**:
  ```java
  // ResultPartition.enqueueWithBackpressure — pool + materialization 象限
  if (bufferPool != null) {
      if (point != null) {
          bufferPool.acquire();
          if (!queue.offer(element)) {
              // Queue full — overflow to the materialization store only
              bufferPool.release();          // permit 立即归还，store 元素不占任何预算
          }
      }
  }
  ...
  } else {
      if (point != null) {
          // Overflow-bypass without a pool: non-blocking offer, no permit accounting.
          queue.offer(element);              // 返回值被忽略，且无任何日志/指标
      }
  }
  ```
  ```java
  // InMemoryMaterializationPoint 类注释（第 34-36 行）声称：
  // "The bypass write does not block on capacity (in-memory list
  //  grows unboundedly); global memory pressure is expected to be controlled
  //  upstream by the IBufferPool on the main queue."
  private final List<MaterializedElement> store = new ArrayList<>();  // 无界
  ```
- **严重程度**: P1
- **并发场景**: 单个上游任务线程（producer）写 materialization-enabled 边 + 下游任务线程（consumer）读同一 `ResultPartition` 队列 + region 重启时 `SupervisionLoop` 恢复线程读 store
- **触发时序**: T1（producer 任务线程）下游消费者变慢 → `queue` 达到 `partitionCapacity` → T1 执行 `write()`：`dualWriteToMaterialization` 先把记录写入 store（L296），`enqueueWithBackpressure` 的 `queue.offer` 返回 false → permit 释放，记录**只存在于 store**。T2（consumer 任务线程）只从 `queue.take()/poll()` 读 → 该记录在本次活动运行期永不可见（store 重放仅由 `SupervisionLoop.rebuildConsumerInputGateWithReplay`，`SupervisionLoop.java:1038-1113`，在 region 重启/恢复时触发）。T3：consumer 持续慢 → 队列恒满 → 后续每条记录都走 store-only 路径 → `store` 以生产速率无界增长；且 `BufferPool` 不构成上界——store 驻留元素不持有 permit（写时即释放），池只被队列驻留元素占用，队列满恰恰意味着 permit 全部空闲，producer 永不阻塞。T4（有界作业场景）：source EOS → 队列排空 → 任务正常 finish，作业「成功」结束，store 中溢出记录既未进入本次输出也无任何失败/告警。
- **现状**: `InMemoryMaterializationPoint` 的类注释断言「全局内存压力由主队列上的 IBufferPool 上游控制」，该不变式对 overflow-bypass 路径为假：池预算从不覆盖 store 驻留元素，队列满载时背压链路完全旁路；同时活动运行期溢出记录对下游不可见，语义依赖「未来某次恢复重放」补齐——若恢复不发生（作业正常完成/仅单次运行），这些记录的结果缺失无任何显式信号。
- **风险**: 一旦启用 materialization 边（见失效面枚举的可达性说明），过载场景从「有界降级」退化为「生产速率的堆增长」（OOM 同族 F7）+ 当前运行结果静默缺记录（F2「半个成功」邻型：已发射与被旁路的记录之间无中间态标记）。声明规模包络内（owner doc 未对该机制声明任何内存包络）过载即可触发。
- **建议**: 三选一：① store 驻留元素持有 BufferPool permit（溢出时保留 permit、重放/旁路回收时释放），使全局预算真实覆盖 bypass 路径——与 `pendingReplay`「零 permit」契约联动调整；② 保留 bypass 但在活动运行期安排 store 尾随消费（consumer 队列空闲时从 store 补读），消除「运行期不可见」；③ 在溢出发生时打点 + 达到阈值 typed 失败（复用 `ERR_STREAM_CHANNEL_OVERFLOW` 形态），把静默旁路变成显式过载信号。类注释的失真不变式必须同步更正。
- **失效面枚举**: 同族写入口共 4 个象限逐一核查：① pool+materialization（`L348-355`）= 本发现主体；② no-pool+materialization（`L367-370`）= 同型旁路且**连日志都没有**（`queue.offer` 返回值忽略），状态同病；③ pool 无 materialization（`L356-365`）= 阻塞 put + interrupt permit 归还，正确；④ no-pool 无 materialization（`L372-374`）= 传统阻塞 put，正确。Bypass 触发的其余入口：`attachPendingReplay`（有界由 capture 集决定）与 unaligned `captureInFlightData`/`injectElements`（Remote 侧逐条校验）均正确。可达性：`JobEdge.setMaterializationEnabled(boolean)`（`JobEdge.java:141`）是公开 API，全仓 grep 仅测试代码调用（`TestRegionDecomposer`、`TestMaterializationWiring`、`TestSupervisionLoop*E2E` 等），未发现 DSL/flow/生产 builder 接线——当前为休眠机制，缺陷随该 API 的任何生产启用而激活。
- **信心水平**: 确定（代码路径直接可证；类注释不变式与实现矛盾）
- **误报排除**: 不属于「有界声明存在的队列/缓存」误报——该路径的无界性恰好与类注释的声明相反，且满载行为未在 owner doc 声明为契约；也不是低频管理路径（它是数据面每记录热路径的过载分支）。
- **复核状态**: 已保留

---

### [nop-stream-core-runtime-C8-002] `PollingJdbcMessageService.poll()` 无 LIMIT 全量物化积压：消费滞后时单次 poll 把全部未消费行载入内存

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/launch/PollingJdbcMessageService.java:305-338`（查询 `310-314`、物化 `383-390`）；接线证据 `launch/TaskManagerMain.java:104`
- **证据片段**:
  ```java
  void poll() {
      ...
      long cursor = lastSeenId.get();
      List<Row> rows = jdbcTemplate.executeQuery(SQL.begin()
              .sql("SELECT id, payload FROM " + queueTable
                      + " WHERE topic = ? AND id > ? ORDER BY id ASC", topic, cursor)
              .end(), PollingJdbcMessageService::readRows);   // 无 LIMIT，readRows 全量物化
      ...
      for (Row row : rows) { ... consumer.onMessage(topic, message, ctx); ... }
  }
  private static List<Row> readRows(IDataSet dataSet) {
      List<Row> rows = new ArrayList<>();
      while (dataSet.hasNext()) { ... rows.add(new Row(r.getLong(0), r.getBytes(1))); }
      return rows;
  }
  ```
- **严重程度**: P1
- **并发场景**: `PollingJdbcMessageService` 的 2 线程 `ScheduledExecutorService` poller 线程（`newScheduledThreadPool(2)`，L96-100）执行 poll；同表的 `send()` 来自各 producer 任务线程（跨 JVM 写入）。消费速率（poller）低于生产速率（任务数据面）时积压累积。
- **触发时序**: T1（TM 任务线程）经 `RemoteResultPartition.sendUnderLock` 每记录 INSERT 一行；T2（poller 线程）每 `pollIntervalMs`（launch 默认 50ms）poll 一次。下游任务因背压变慢（如 sink 慢）→ `RemoteInputChannel` 队列满 → `onMessage` 的 10s 有界 offer 逐行使派发速率 ≥10s/条 → cursor 推进极慢 → 表内该 topic 未消费行数 N 持续增长 → 下一次 poll 的 `SELECT ... id > cursor` 返回全部 N 行，`readRows` 在单次调用内把 N × (行对象 + payload byte[]) 全部物化成 List。N 达百万级（每行 payload 数百字节～KB）→ 单次 poll 数 GB 堆分配 → OOM。
- **现状**: 消费侧批大小无上限：单 poll 一次载入全部积压，逐行同步派发（派发完才推进 cursor、才发下一次 poll）。没有 `LIMIT`/分批/prefetch 上限，与 B5 通道侧 1024 的有界入队形成断层——通道有界、派发物化无界。
- **风险**: owner doc 运维手册把 `TaskManagerMain/JobCoordinatorMain + jdbcUrl`（即本类）列为多 JVM 分布式模式的标准启动路径（`nop-stream-cluster.sh` 同路径），不属纯测试装置：持续下游滞后（恰是 C8 的过载场景）下，OOM 在声明使用方式内可达。WI6 生产者门控即使启用也不缓解本条——门控只限制「未消费深度」，而本类不实现 `ChannelBacklogProbe`（见 C8-003），门控在该后端恒关闭。
- **建议**: poll 查询加 `LIMIT <batch>`（如 500/1000，可配），cursor 按批推进，天然形成消费侧 prefetch 上限；积压深度可经 `countRows`/新 gauge 暴露。与 C8-003 一并处理时，可顺带把「派发满载」从行级 10s 等待改为批次级跳过+typed 失败。
- **失效面枚举**: 同族「无界查询物化」全模块 grep：本类 `readRows`（本发现）、`firstLongOrNull`（单值，安全）、`countRows`（单值，安全）；`JdbcCheckpointStorage`/`JdbcClusterRegistry` 读路径均为按 key/limit 的点查或小结果集（未发现同族全量物化）；`nop-stream-core` 侧无裸 SQL 查询。`PollingJdbcMessageService` 是本仓库唯一轮询型 IMessageService 实现；`SysDaoMessageService`（nop-sys）的扫描走平台消息服务自有批量语义，不在本模块范围。
- **信心水平**: 确定
- **误报排除**: 不是「测试代码按生产标准报 P1」的误报——类 javadoc 自称 test-infrastructure，但生产 launch 入口（`TaskManagerMain`/`JobCoordinatorMain`，owner doc 运维手册明确为 main-scope 生产入口）直接构造并使用它承载数据面；也不是「有界声明存在」误报——本路径无任何批上界声明。
- **复核状态**: 已保留

---

### [nop-stream-core-runtime-C8-003] `nop_stream_msg_queue` INSERT-only 无界账本：无 TTL/清理路径，且 launch 后端不实现 `ChannelBacklogProbe` 使 WI6 生产者门控恒不可用

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/launch/PollingJdbcMessageService.java:177-186`（INSERT-only send）、`151-156`（清理仅 test helper）；对照 `RemoteGraphExecutionPlanBuilder.java:224-237,668-674`（门控能力探测）
- **证据片段**:
  ```java
  // send：每条数据面记录一行，永删除
  public void send(String topic, Object message, MessageSendOptions options) {
      ...
      byte[] payload = javaSerialize(message);
      jdbcTemplate.executeUpdate(SQL.begin()
              .sql("INSERT INTO " + queueTable
                      + " (topic, payload, created_at_ms) VALUES(?,?,?)", topic, payload, System.currentTimeMillis())
              .end());
  }
  /** Drops all rows for topics starting with {@code prefix}. Test-cleanup helper. */
  public void clearTopics(String prefix) { ... }   // 全仓唯一删除路径，仅测试调用
  ```
  ```java
  // RemoteGraphExecutionPlanBuilder.resolveBacklogProbe()（L668-674）：
  // raw instanceof ChannelBacklogProbe 才启用门控；PollingJdbcMessageService 未实现该接口，
  // 配置了 limit 时仅启动日志声明一次后诚实禁用（RemoteGraphExecutionPlanBuilder.java:226-231）
  ```
- **严重程度**: P1
- **并发场景**: 多 JVM launch 模式：各 TM 的 `RemoteResultPartition` 生产线程持续 INSERT；JC/TM 的 2 线程 poller 只读不删；无任何后台清理任务（`StreamGovernanceConfig` 治理清扫只裁剪 checkpoint 观测历史与终态作业记录；`RetentionCleaner` 只管 durable checkpoint 保留）。
- **触发时序**: T0 集群按 owner doc 运维手册以 `JobCoordinatorMain jobId=... jdbcUrl=... checkpointBaseDir=...` + `TaskManagerMain nodeId=... jdbcUrl=...` 启动（共享 H2/JDBC，`PollingJdbcMessageService` 为跨 JVM 消息服务）→ 数据面每记录一行 INSERT，消费仅推进订阅方 cursor，行永驻 → 长时运行（天/周级）表以记录速率单调增长，连同 `idx_*_topic_id` 索引膨胀；`StreamStateResetTool`/作业 stop/恢复均不触及该表。T1 并行：运维按 owner doc「DB 数据面生产者背压」配置 `nop.stream.data.db.backlog.limit=<n>` 期望约束积压 → `resolveBacklogProbe()` 对裸后端做能力探测 → `PollingJdbcMessageService` 非 `ChannelBacklogProbe` → 门控以启动日志声明后禁用（诚实 no-op）→ 生产者侧零约束，账本增长速率 = 完整生产速率。
- **现状**: 该表是 append-only 持久化账本（F4 族）：只有写入路径和 test-only `clearTopics`，没有 TTL、归档、消费后删除或任何运行时清理；同时该后端使唯一的生产者侧流控设施（WI6 门控）结构性不可用——owner doc 声明的「配置了 limit 会在启动日志声明一次后禁用（诚实 no-op）」是可见降级，但与「生产 DB 通道」的组合后果（持续过载下既有表无界又有门控不可用）未在任何文档声明。
- **风险**: 长时运行必然触发的存储无界增长（F4）：磁盘耗尽 → INSERT 失败 → 数据面 send 失败 → 任务失败循环；叠加 C8-002 的全量物化，消费滞后窗口越大 OOM 越近。触发链无需任何罕见条件，仅「按文档启动 + 长跑」即可。
- **建议**: ① 为 `PollingJdbcMessageService` 增加消费确认后删除（或 TTL 清扫任务，复用引擎自建调度线程裁定 ST-6 的形态），按 topic 前缀批量 DELETE；② 或为该后端实现 `ChannelBacklogProbe`（COUNT 未消费行即可），使 WI6 门控在 launch 路径真实可用，同时补 C8-002 的 LIMIT 批读；③ owner doc 补充该后端的保留语义声明。
- **失效面枚举**: append-only 持久化全仓排查（F3 清扫）：`PollingJdbcMessageService` → `nop_stream_msg_queue`（本发现，无清理）；`SysDaoMessageService`（nop-sys）→ 平台消息表 WAITING/CLAIMED 行（owner doc WI6 节明示「无订阅者 topic 的事件永不被扫描」，nop-sys 范围另案）；`JdbcCheckpointStorage`（有 `maxRetainedCheckpoints` 保留策略 ✓）；`JdbcClusterRegistry`（租约过期即失活，行体由 lease 语义覆盖 ○）；`JdbcCheckpointStorage` 2PC 台账行（nop-stream-connector-jdbc，非本轮对象）；`StreamJobEvent`/checkpoint 观测历史（`StreamGovernanceConfig` 双约束清理 ✓）。状态：仅本发现对象与 nop-sys 侧无清理。
- **信心水平**: 确定
- **误报排除**: 不是「有界声明存在的缓存」误报——不存在任何上界声明或清理契约；不是测试专用代码——构造点在 main-scope 生产入口（owner doc 运维手册权威路径）。
- **复核状态**: 已保留

---

### [nop-stream-core-runtime-C8-004] 处理时间定时器 fire mail 无 coalescing：任务线程被背压阻塞期间 mailbox 以 10 条/秒无界积累，解除后陈旧 mail 风暴

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/ProcessingTimeServiceDriver.java:116-133`、`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/TaskMailbox.java:40-44,54-72`
- **证据片段**:
  ```java
  // ProcessingTimeServiceDriver.run() — 每 tick 检查 due，有 due 即投递，无「已有 pending fire mail」守卫
  while (!stopped) {
      long now = CoreMetrics.currentTimeMillis();
      boolean due = processingTimeService.isTimerDue(now)
              || (timeServiceManager != null && timeServiceManager.hasProcessingTimeTimersDue(now));
      if (due) {
          mailbox.put(Mail.control(this::fireDueTimersOnTaskThread, "processing-time-timer-fire"));
      }
      Thread.sleep(tickMs);   // DEFAULT_TICK_MS = 100
  }
  ```
  ```java
  // TaskMailbox — 无界队列，无深度上限/指标
  private final ConcurrentLinkedQueue<Mail> controlQueue = new ConcurrentLinkedQueue<>();
  ```
- **严重程度**: P2
- **并发场景**: `processing-time-driver` 守护线程（producer，每 100ms）vs 任务线程（consumer，仅在 `processInputGate` loop top / SOURCE `collect()` 安全点 drain）。过载（背压）恰是消费侧停摆的场景：MIDDLE/SINK 任务线程阻塞在 `RecordWriterOutput.collect → writer.emit → queue.put`（`StreamTaskInvokable.java:1282`）期间无法回到 loop top。
- **触发时序**: T1 任务注册了处理时间 timer（window 处理时间触发器等）→ `nextTimerTimestamp` 落入未来。T2 下游队列满 → 任务线程进入 `queue.put` 阻塞（B1 背压，正是设计行为）→ loop top 不再到达。T3 driver 每 100ms 检查：due timer 未被消费前 `isTimerDue`/`hasProcessingTimeTimersDue` 持续为 true（`TimerServiceManager.java:99-106` 逐 service 查未 fire 的 timer）→ 每 tick 投递一条新 control mail，无任何「已有一条 pending」的 coalescing 判断。T4 阻塞持续 D 秒 → 积累 ≈ 10×D 条 mail（每条 Mail 对象 + lambda 引用 + 描述串）。T5 任务线程解除阻塞（下游恢复或 interrupt 失败重启前）→ `drainAndRun()`（`TaskMailbox.java:149-155`）一次性同步执行全部陈旧 mail——每条执行一次 `fireDueTimers(now)`（大部分为空转的 TreeMap firstEntry 检查，`synchronized` 串行），积压 864k 条（一天）时 drain 阶段秒级停顿。
- **现状**: mailbox 设计文档定位为控制面低速率通道，但 timer-fire 是唯一潜在高速率生产者且无 coalescing；`TaskMailbox` 无容量界（对比：commit 通知有界队列 + CallerRuns、通道队列 1024 有界）也无深度 gauge（对比：`commitQueueDepth` 已接线）。
- **风险**: 背压/用户代码长阻塞期间每任务每天 ~40 MB 级慢增长（10/s × ~48B ≈ 864k 条/天）；解除阻塞后的陈旧 mail 风暴造成一次性处理停顿；任务最终失败/重启时 mailbox 随任务丢弃，故不构成跨重启泄漏——限制定级 P2。定量口径：10 条/s × 阻塞时长，阻塞时长受 coordinator taskTimeoutMs（60s 级）与恢复机制上界约束，实际稳态积累量级为数千条/分钟级阻塞。
- **建议**: driver 侧加 coalescing：投递前检查「已有一条未消费的 timer-fire mail」（mailbox 增加 `hasPending(descriptor)` 或 driver 持有 AtomicBoolean 在 mail action 内复位），使任意阻塞时长至多积压 1 条；`TaskMailbox.size()` 已存在，可顺带注册 per-task mailbox 深度 gauge 进 task 层指标名表。
- **失效面枚举**: mailbox 全部生产者逐一核查：① `ProcessingTimeServiceDriver.run`（本发现，100ms 周期高速率潜在源）；② checkpoint 触发 barrier mail（每次 checkpoint 一条，低速率，安全）；③ `MailboxExecutor.signalCancel` 的 cancel 标记 mail（每次取消一条，安全）；④ abort handler 取消 mail（同上）；⑤ `Mail.normal` 工厂（`Mail.java:64`，main 源无调用方）。消费安全点覆盖：MIDDLE/SINK loop top、SOURCE `collect()`/`collectWithTimestamp()`、`SourceReaderOperator` L389——`emit` 阻塞段不可达安全点是该发现成立的前提，四个消费点同构（F3 已覆盖：修复 driver 即全部受益）。
- **信心水平**: 确定（机制直接可证；量级为估算，已在风险栏给出口径）
- **误报排除**: 不是「synchronized/ConcurrentHashMap 原语使用」类误报——问题是复合行为（无界生产者 × 可能长期停摆的消费者）而非原语本身；不是低频路径——处理时间窗口是 owner doc 用户指南的常规特性，背压是流引擎常态。
- **复核状态**: 已保留

---

### [nop-stream-core-runtime-C8-005] `PollingJdbcMessageService` 消费侧行反序列化失败静默跳过：数据面记录丢失对任务完全不可见（F9/F2 邻型）

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/launch/PollingJdbcMessageService.java:319-334`
- **证据片段**:
  ```java
  for (Row row : rows) {
      Object message;
      try {
          message = javaDeserialize(row.payload);
      } catch (Exception e) {
          LOG.warn("Failed to deserialize payload for topic={} id={}: {}", topic, row.id, e.toString());
          lastSeenId.set(row.id);     // 推进 cursor，该行永久跳过
          continue;
      }
      try {
          consumer.onMessage(topic, message, ctx);
      } catch (Exception e) {
          LOG.error("Consumer onMessage failed for topic={} id={}", topic, row.id, e);
      }
      lastSeenId.set(row.id);
  }
  ```
- **严重程度**: P2
- **并发场景**: poller 线程（消费派发）vs 跨 JVM producer 任务线程（写入行）。损坏行触发条件：DB 层截断/手工干预/版本漂移导致的 payload 不可反序列化。
- **触发时序**: T1 producer JVM 写入一行 payload；T2 存储层/运维操作使该行 payload 损坏（H2 页损坏、误 UPDATE、跨版本类结构变更后旧运行残留行）；T3 poller 轮询到该行 → `javaDeserialize` 抛异常 → LOG.warn + `lastSeenId.set(row.id)` → 该记录对订阅方（数据面 = `RemoteInputChannel`，控制面 = RPC handler）永久消失；任务线程毫无感知，watermark/屏障/ACK 均照常，数据流出现静默缺口。
- **现状**: 与模块自身的「无静默丢记录」契约冲突：`RemoteInputChannel.onWireDecodeFailure` 对 wire 层解码失败明确声明「a data-plane consumer must never silently lose a record」并 typed 失败通道；本类在更外层用 warn+skip 丢弃了同一记录，两层语义矛盾。类 javadoc 的 Limitations 节只声明了「崩溃在派发与 cursor 推进之间会重投」（at-least-once），未声明「反序列化失败即永久跳过」。
- **风险**: 单行损坏 → 下游算子状态永久缺一条记录且无失败、无恢复重放入口（cursor 已越过）；对 2PC sink 场景，缺记录在 checkpoint 一致性切面内不可检测（各端一致地看不到它）。触发需要存储损坏这一非默认条件，故 P2。
- **建议**: 数据面 topic（`nop-stream.data.*`）的行反序列化失败应升级为 typed 失败传播——订阅回调抛出 → `RemoteInputChannel.onWireDecodeFailure` 同型通道失败（已有成熟通道失败面），或最低限度按 topic 前缀区分：数据面行失败 → 通道 typed 失败；控制面行维持 warn+skip（RPC 请求有调用方超时兜底）。
- **失效面枚举**: 本类消费失败面共 3 处：① `javaDeserialize` 失败（本发现，warn+skip）；② `consumer.onMessage` 抛异常（`L329-332`，error+skip——实际不可达：内建 consumer（`EnvelopeConsumer`/RPC handler）内部自捕获，但契约上仍是 skip 语义，与①同修）；③ poll 整体失败（`L335-337`，error 后下轮从原 cursor 重试——正确，无跳过）。对照兄弟实现：`RemoteInputChannel` 的 decode/overflow/interrupt 全部 typed 失败 + EOS 哨兵（✓ 声明语义）；`DataPlaneMessageServiceAdapter` wire 解码失败经 `WireDecodeFailureAware` typed 上抛（✓）。仅本类外层跳过语义与家族不一致。
- **信心水平**: 确定（行为可证；触发依赖存储损坏，概率低）
- **误报排除**: 不按「测试专用代码」豁免——同 C8-002/003，该类是生产 launch 路径的消息服务；不与「静默吞异常导致并发状态不一致」（F9 已在范围内）重复计数——本条是 C8 视角的「过载/损坏拒绝路径半个成功」（被拒行从未进入任何恢复语义）。
- **复核状态**: 已保留

---

### [nop-stream-core-runtime-C8-006] 过载可观测缺口：BufferPool 全局用量、mailbox 深度、告警丢弃计数、materialization store 大小均无指标接线；`EdgeConfig.queueCapacity≤0` 静默回落

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/buffer/IBufferPool.java:97-123`（`getGlobalUsage`/`isGlobalBackpressured` 全仓无调用方）、`TaskMailbox.java:138-140`（`size()` 无指标消费方）、`io/nop/stream/core/execution/GraphExecutionPlan.java:796-801`（`resolvePartitionCapacity` 静默回落）、`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/alert/WebhookAlertChannel.java:121-127`（丢弃仅 WARN 无计数）
- **证据片段**:
  ```java
  // GraphExecutionPlan.resolvePartitionCapacity — 配置错误静默吞掉
  private static int resolvePartitionCapacity(EdgeConfig edgeConfig) {
      if (edgeConfig != null && edgeConfig.getQueueCapacity() > 0) {
          return edgeConfig.getQueueCapacity();
      }
      return ResultPartition.DEFAULT_CAPACITY;   // 0/负值 → 无声变 1024
  }
  ```
- **严重程度**: P3
- **并发场景**: 运维/监控视角的全局观测面：跨分区全局背压（`BufferPool` 耗尽）、控制面积压（mailbox）、告警通道丢弃率、边容量配置校验。
- **触发时序**: 理论风险，未具体化——无运行期交叠，属观测面缺口而非竞态。过载发生时：全局池是否为瓶颈只能靠 `emitTime` 代理间接推断（owner doc WI16 口径），`BufferPool.getGlobalUsage()`/`isGlobalBackpressured()` 已实现但 main 源零调用；mailbox 积压（C8-004 的伴随观测）不可见；webhook 丢弃只有日志无 counter（对比：CallerRuns 有 `nop.stream.task.commitCallerRuns.total` 的成熟先例）。
- **现状**: owner doc 指标名表已覆盖通道队列、emit 代理、DB backlog、commit 队列四类；上述四项是名表之外的盲区。`queueCapacity≤0` 静默回落 1024 而非 fail-fast，违背本模块「No-Silent-No-Op」惯例（对照 `commitQueueCapacity<=0` 在 `TaskManager.java:225-227` 是 typed 拒绝）。
- **风险**: 过载定位只能依赖间接代理；容量配置 typo 静默生效造成与声明不符的背压水位。
- **建议**: `nop.stream.io.bufferpool.usage` gauge（进程级，`StreamMetricsRegistries` 注册）；task 层 mailbox 深度 gauge；`WebhookAlertChannel` 丢弃计数进 alert 指标族；`resolvePartitionCapacity` 对 `queueCapacity<=0` 且非缺省值的输入 typed 拒绝（对齐 `TaskManager` 的先例）。
- **失效面枚举**: 同族配置静默回落点 grep：`resolvePartitionCapacity`（本条）；`EdgeConfig` 构造器对 null policy 回落 `BLOCKING_QUEUE`（缺省语义，合理）；`RecordWriter.validateFlowControlPolicy` 对非支持 policy 是 typed 拒绝（✓ 先例一致）；`RemoteInputChannel` 容量 ≤0 会直接在 `LinkedBlockingQueue` 构造抛 IllegalArgumentException（✓ 天然 fail-fast）。指标缺口同族：`InMemoryMaterializationPoint.size()`、`TaskMailbox.size()`、`IBufferPool.getGlobalUsage()`、`WebhookAlertChannel` drop——四者均已实现读取方法、均缺注册接线。
- **信心水平**: 确定（缺口为 grep 可证事实）
- **误报排除**: 不属于「无状态单例被并发调用」类误报；也非要求完美主义——owner doc P-REQ-1/P-REQ-8 已把过载可观测列为交付契约，本条是该契约内的补面清单。
- **复核状态**: 已保留

---

### [nop-stream-core-runtime-C8-007] `WebhookAlertChannel.delivered` 无界 `CopyOnWriteArrayList`：生产进程内随成功投递单调增长

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/alert/WebhookAlertChannel.java:67,180`
- **证据片段**:
  ```java
  private final List<AlertEvent> delivered = new CopyOnWriteArrayList<>();
  ...
  if (response.statusCode() >= 200 && response.statusCode() < 300) {
      delivered.add(event);   // 每次 successful 投递永久追加，无上限、无清理
      return;
  }
  /** Delivered alerts (test observability). */
  public List<AlertEvent> getDelivered() { return delivered; }
  ```
- **严重程度**: P3
- **并发场景**: webhook 投递线程（drainLoop 单线程，写）vs 测试/诊断读取方（读）。生产路径：`AlertService` 在每次 `JOB_FAILED`/`RECOVERY_STARTED`/`JOB_DEGRADED` 外发时调用 `send`，投递成功即追加。
- **触发时序**: 理论风险，未具体化为单次故障——长时运行累积：故障-恢复循环的集群（恢复风暴、健康状态反复迁移）持续产生告警事件，每个成功投递的 AlertEvent（含 payload/message 字符串）永久驻留于 coordinator 进程堆；增长速率 = 告警事件速率，常态低（每次恢复 2-3 条），恢复风暴/告警路由误配置时显著。
- **现状**: 字段注释自我定位为「test observability」，但类未做任何测试/生产区分——启用 webhook 渠道的生产 coordinator 同样累积；与 `pending` 队列的有界（256）形成不对称。
- **风险**: 慢速无界增长（F4 内存变体），数月级长跑 + 告警密集场景可观测为堆爬升；CopyOnWriteArrayList 的整表复制读语义在规模大后放大。
- **建议**: 改为有界环形缓冲（如 `ArrayDeque` + 上限 128，与 `pending` 对称）或仅在测试构造路径启用记录（构造参数开关）。
- **失效面枚举**: 告警/事件面驻留点全查：`WebhookAlertChannel.delivered`（本发现）；`LoggingAlertChannel`（纯日志输出，无驻留 ✓）；`StreamJobEventBus.listeners`（CopyOnWriteArrayList，有 `removeListener` 配对且规模=监听器数 ✓）；`CheckpointHistory`（`setCheckpointHistoryMaxEntries` 有界 + 治理清扫双约束 ✓）；`OpsJobManager` 终态作业记录（retention-minutes 清扫 ✓）。仅本条无界。
- **信心水平**: 确定（增长事实可证，速率为场景依赖估算）
- **误报排除**: 不属「低频管理路径粗锁」类豁免——它是随事件速率线性增长的驻留集合；不与 C8-006 重复：006 是「缺指标」，本条是「缺上界」。
- **复核状态**: 已保留

---

## 三、F2 定向核查记录（过载拒绝路径「半个成功」）

| 过载拒绝点 | 拒绝后果 | 恢复语义 | 判定 |
|---|---|---|---|
| 本地 `ResultPartition` 队列满 | 生产者阻塞（背压） | 无拒绝即无半个成功 | ✓ |
| `BufferPool` 耗尽 | 生产者阻塞于 `acquire` | 同上；interrupt 时 permit 归还（无许可泄漏） | ✓ |
| materialization 边队列满 | 记录旁路（活动运行期下游不可见） | 依赖 region 重启重放；恢复不发生则输出静默缺记录 | ✗ → C8-001 |
| `RemoteInputChannel` 入队 10s 超时 | 通道 typed 失败 → 任务失败 | 恢复重订阅从后端重放（cursor/epoch 兜底），非半个成功 | ✓ |
| `RemoteResultPartition` WI6 门控 10s 超时 | typed `ERR_STREAM_SEND_GATE_TIMEOUT` → 任务失败重启 | source 重放 + epoch fencing；owner doc 显式裁定「持续过载的显式语义，非静默丢弃」 | ✓（launch 后端门控不可用另记 C8-003） |
| barrier 对齐超时 | `ERR_STREAM_BARRIER_ALIGNMENT_TIMEOUT` 抛出 → 任务失败（或 unaligned 降级，savepoint 豁免） | 失败路径一致；降级路径捕获通道状态进 checkpoint | ✓ |
| 广播 emit 中途 interrupt | 首 N 个分区已写入、其余未写 → 任务失败 | checkpoint 对齐 + 恢复重放覆盖两侧，无持久化半个成功 | ✓ |
| 2PC `finishCommit` 失败 | 条目保留于 `pendingCommits` | subsuming commit / restore 重放（`restoreFromEpoch` durable-则-commit 不变量） | ✓ |
| webhook 队列满 | 丢弃 + WARN | best-effort 契约（owner doc 声明），logging 渠道兜底记录 | ○ |
| `PollingJdbcMessageService` 行反序列化失败 | 静默跳过，任务不可见 | 无 | ✗ → C8-005 |

## 四、覆盖范围说明

**重点文件（逐文件精读）**: `ResultPartition`、`RecordWriter`、`InputChannel`、`InputGate`、`BufferPool`/`IBufferPool`、`TaskMailbox`、`MailboxExecutor`、`ProcessingTimeServiceDriver`、`TaskProcessingTimeService`、`StreamTaskInvokable`（含三类 RecordWriterOutput）、`GraphExecutionPlan`（build/buildPartitionMatrix/resolvePartitionCapacity）、`InMemoryMaterializationPoint`/`IMaterializationPoint`、`MemoryBudget`/`EdgeConfig`/`FlowControlPolicy`、`PerKeyOrderedBuffer`（trim 路径核对）、`RateLimitedSourceFunction`/`SourceRateLimiter`、`LocalSourceCoordinator`（ReaderChannel）、`TwoPhaseCommitSinkFunction`、`RemoteResultPartition`、`RemoteInputChannel`、`DataPlaneMessageServiceAdapter`（头部）、`TaskManager`（executor 矩阵 + heartbeat + stop）、`RunningTask`、`CheckpointAckSender`、`PollingJdbcMessageService`、`WebhookAlertChannel`、`StreamJobEventBus`、`StreamOpsHttpServer`（executor 点查）、`SupervisionLoop`（replay 重放段 L1010-1130）。

**扫描核实（grep 定位后判定不需立项）**: `Collections.synchronized*` 全部迭代点在锁内（C2 范围不展开）；`scheduleAtFixedRate`/`newSingleThread*` 全部线程创建点的队列/关闭路径（B10-B16）；`CompletableFuture` 异步链（runtime 侧少量，sendAsync 均为已完成 future 或委托后端）；`QueueOverflowPolicy`/`ISemaphore`/`IRateLimiter` 该用未用核对（见边界清单末段）；checkpoint 触发节奏与 in-flight 上限（`maxConcurrentCheckpoints` + minPause 门控存在）。

**零发现区域**: `nop-stream-core` 的 windowing/triggers/state 序列化面（C5/C11 维度对象）、CEP（不在本轮对象）、connector 族（不在本轮对象）。

# C8 背压、流控与过载行为 — nop-stream-core-runtime（深挖第 2 轮追加）

- **审计对象**: 同第 1 轮（`nop-stream/nop-stream-core/src/main/java`、`nop-stream/nop-stream-runtime/src/main/java`；`_` 前缀生成文件不在发现对象内）
- **盲审边界**: 本轮仅读取本维度第 1 轮文件（`C8-backpressure.md`）与本提示词（`ai-dev/skills/concurrency-resource-audit-prompt.md` 共享前缀 + C8 正文）；未读其他审计维度文件、plans、bugs、lessons
- **方法**: 在第 1 轮生产-消费边界清单（B1–B20）基础上，按深挖指令对五个盲区做定向排查：① 信号量/限流器许可泄漏漏查点；② 批处理边界（单批超大输入绕过批上限）；③ 过载可观测补充；④ F2 过载/恢复拒绝路径「半个成功」漏查；⑤ `QueueOverflowPolicy`/死配置该用未用复核

---

## 一、第 2 轮盲区核查记录（无发现结论，供合并裁定去重）

| 排查点 | 结论 |
|---|---|
| `TaskManager.capacitySemaphore` 全部 8 处 release 点逐点核对（L568/587/768/775-777/812-815/963/1079 + `RunningTask.java:171-173`） | 正常路径许可会计平衡（duplicate/putIfAbsent 路径的 release 与本次调用自身 acquire 配对；终态/取消/轮换/位移均 CAS 单次释放）；缺口见 C8-009（两处 submit 拒绝点不清理） |
| `deployTask` 同 key 重部署位移（`selectDisplacedTask`，L741-745：older-or-equal 位移）对 submit 拒绝 zombie 的自愈 | remote 路径 zombie 靠下次同槽重部署位移自愈（有界）；in-process `receiveAssignment` 无位移逻辑、其自愈重部署恰被静默吞没——并入 C8-009 |
| `SourceRateLimiter`/`RateLimitedSourceFunction`（B18 复核） | 单线程持有、无共享许可计数器、interrupt 传播，无泄漏（维持第 1 轮 ✓） |
| `CheckpointAckSender` | 无队列；内联有界重试（3 次 + 200ms 线性退避），interrupt 安全 break，失败计数 `ackSendFailed`（✓） |
| `SupervisionLoop` 持续过载下的重启风暴 | per-region 重启预算（`DEFAULT_MAX_RESTARTS_PER_REGION=3`，L165/336-345），耗尽 typed `ERR_STREAM_SUPERVISION_RESTART_EXHAUSTED`——有界降级（✓）；`restartRegion` Phase 3 显式跳过 COMPLETED 任务（L556-568），排除「retained-success 条目 + 区域重启」静默拒绝触发链 |
| `DataPlaneMessageServiceAdapter` 单条超大消息 | 适配器无本地帧组装/长度前缀解码，尺寸守卫归属后端（Kafka/Pulsar 自限；launch DB 后端的 payload 无界已由第 1 轮 C8-002/003 覆盖）——本轮无新增 |
| `PerKeyOrderedBuffer` 陈旧 key trim 覆盖 | `OverWindowOperator.processWatermark`（L202-211）与 `EquiJoinOperator.completeAndTrimHash`（L272-286）均在 watermark 推进时遍历 `buffer.keys()` 全量 trim——陈旧 key 已覆盖（✓） |
| `JobCoordinator.barrierFanoutExecutors`（per-nodeId 单线程执行器，L2932-2940） | 仅 coordinator stop 统一关闭（L723/726）；nodeId 为运维配置值、跨重启稳定，条目基数 = 历史 distinct nodeId 数，空闲 daemon 线程——低于立项阈值，不立项 |
| `queue.offer` 忽略返回值全仓 grep | 仅 `ResultPartition.java:370` 一处（第 1 轮 C8-001 已立项），无同族漏网 |

---

## 二、发现条目（接续第 1 轮序号）

### [nop-stream-core-runtime-C8-008] 运维面 `/jobs` 提交端点无界读取请求体：`readAllBytes` 无上限，单次超大 POST 即可打出堆峰值

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/ops/StreamOpsHttpServer.java:389-396`（executor 配置 `127-129`；config 面 `ops/StreamOpsConfig.java:22-43`）
- **证据片段**:
  ```java
  private void handleSubmit(HttpExchange exchange) throws IOException {
      ...
      String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      JobSubmissionSpec spec;
      try {
          spec = io.nop.core.lang.json.JsonTool.parseBeanFromText(
                  requestBody, JobSubmissionSpec.class);
      } catch (Exception e) { ... 400 ... }
  ```
- **严重程度**: P2
- **并发场景**: `stream-ops-http` 4 线程固定池（`Executors.newFixedThreadPool(4)`，L127-128，`/metrics`、`/jobs`、catch-all 共享）vs coordinator 进程堆。每个在途请求把请求体全量读入 `byte[]` 再复制为 `String`（2× 足迹），随后 `parseBeanFromText` 再构建中间树（第 3 份）。
- **触发时序**: T1 客户端向 `/jobs` POST 一个 N GB body（无需合法 JSON——读取发生在解析之前）→ `readAllBytes` 在单次调用内把 N GB 全量载入堆并立即再复制一份 String → 堆峰值 ≥ 2N GB。T2 并发 2-4 个同类请求（池仅 4 线程）→ 直接 OOM；或单个请求超过堆余量 → OOM。T3 即使不 OOM：慢速上传客户端可长时间占住 4 个 worker 之一，多个慢上传使 `/metrics`、`/jobs` 查询、threaddump 等全部运维端点排队（运维面队头阻塞）。解析失败的 body 在读取完成后才被 400 拒绝——防护闸门装在闸门之后。
- **现状**: `StreamOpsConfig` 全部配置键（enabled/port/bind/metricsEnabled/authToken）中无任何 body 上限；读取点无 Content-Length 预检、无 `readNBytes` 截断、无 413 路径。已有的 F-09b 鉴权（非回环绑定强制 token）限定了攻击者面，但「合法持有 token 的客户端/脚本 bug」同样可触发——C8 的口径是超大输入下有界降级，与调用方善意无关。
- **风险**: 按文档启用 ops 端点（loopback 默认或 token 模式）后，任一可达方一次请求即可 OOM coordinator 进程（JobCoordinator/告警/2PC commit 转发随之全灭）；无任何 typed 错误与限流缓冲。
- **建议**: 读取前预检 `Content-Length`（超限直接 413 typed 错误）；读取改用 `InputStream.readNBytes(maxBodyBytes + 1)`，超限即断并返回 413；上限进 `StreamOpsConfig`（如 `nop.stream.ops.http.maxBodyBytes`，默认 1-4 MB 量级）。与第 1 轮 C8-006 的「配置静默回落」先例不同，此处是缺守卫本体。
- **失效面枚举**: 本服务器全部请求体读取点 grep：`handleSubmit`（本发现，唯一 `readAllBytes` 点）；其余端点（`/metrics`、`/jobs` 列表、stop、checkpoints、threaddump、subtask state）均为无 body 或仅读 query/路径参数（✓）。同族「运维面无界输入」：`StreamOpsHttpServer` 4 线程池本身有界（第 1 轮 B16 ✓）；`PollingJdbcMessageService.poll` 全量物化（第 1 轮 C8-002，数据面同族，不重复计数）。
- **信心水平**: 确定（代码路径直接可证；OOM 所需 body 尺寸取决于堆配置，但无界性本身确定）
- **误报排除**: 不属「低频管理路径豁免」——报告的不是锁粒度而是无界输入本体，且 C8 正文第 3 步明确把「单批超大输入」列为必查项；不属「有界声明存在」误报——不存在任何上限声明；F-09b 鉴权是访问控制不是输入边界，不能替代。
- **复核状态**: 已保留

---

### [nop-stream-core-runtime-C8-009] `receiveAssignment` 同 key 拒绝静默吞没（无 FAILED 上报，违反单向 RPC 契约）；两处 submit 拒绝点许可/登记不清理，in-process 自愈链恰好被同一静默吞没打断（F2 邻型）

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:566-570,585-589`（静默拒绝）、`594-600`（in-process submit 拒绝不清理）、`780-790`（remote submit 拒绝不清理）、对照契约声明 `860-867`（`reportAssignmentFailure` javadoc）
- **证据片段**:
  ```java
  // receiveAssignment：同 key 已有条目 → WARN + release + return，无 reportAssignmentFailure
  if (runningTasks.containsKey(taskKey)) {
      LOG.warn("Task {} already running, ignoring duplicate assignment", taskKey);
      capacitySemaphore.release();
      return;
  }
  ...
  } catch (java.util.concurrent.RejectedExecutionException e) {
      // 只上报，不 release、不 remove —— zombie 条目 + 钉住的许可留在 runningTasks
      reportAssignmentFailure(assignment, assignment.getFencingEpoch(), ...);
      return;
  }
  ```
  ```java
  // 同类契约的自我声明（reportAssignmentFailure javadoc, L862-866）：
  // "The receiveAssignment RPC is one-way, so a warn-and-return (or even a thrown
  //  exception) never reaches the coordinator — the FAILED report makes the
  //  rejection observable and lets recovery act on it instead of waiting for
  //  supervision to detect the stalled slot."
  ```
- **严重程度**: P2
- **并发场景**: coordinator 分配 fan-out 线程（`AssignmentPlanner.java:251` 调 `receiveAssignment`）vs TM 侧并发同 key 部署/取消（`deployTask`/`cancelTask`/`updateFencingToken`）；控制面消息后端为 at-least-once（`PollingJdbcMessageService` javadoc 自声明「crash 在派发与 cursor 推进之间会重投」），assignment 消息可被重投/重复派发。
- **触发时序**: T1（正常路径）任务 N 次尝试 submit 被 `taskExecutor` 拒绝（shutdown 竞态窗口——CC-17 专用测试钩子 `forceTaskExecutorShutdownForTest` 即为构造该状态而设，L1117-1124）→ `reportAssignmentFailure` 发出 N 的 FAILED 报告，但**许可未释放、`runningTasks` 条目未移除**（run() 永不执行，`RunningTask.java:171-173` 的终态释放不可达）。T2 coordinator 收到 FAILED → 同 epoch 发起 N+1 次尝试重部署到同一 TM → `receiveAssignment` 走到 `containsKey` 命中 zombie → **WARN + return，无任何 FAILED 上报**。T3 coordinator 侧对 N+1 的视角：分配已投递、无终态、无 liveness——只能等 taskTimeoutMs/lease 超时才发现；恢复被拖满一个完整超时周期，期间零 ERROR、零指标、零报告。remote 路径（`deployTask` L780-790）的孪生 catch 同样不 release/remove，但其 zombie 靠下次同槽重部署的位移逻辑（`selectDisplacedTask`，older-or-equal 即位移，L741-745 + L772-777）自愈；in-process 路径**没有位移逻辑，其自愈重部署恰是 T2 被吞没的那条**——只能靠 epoch 轮换（`updateFencingToken`）/cancelTask/stop().clear() 兜底。
- **现状**: ① 同 key 拒绝的两个分支（L566-570 containsKey、L585-589 putIfAbsent 竞态）都是静默 warn-return——对「真正在跑的任务收到重投消息」是无害正确，对「zombie/不可用条目」则把拒绝变成黑洞；这与模块自身在 `rejectDeploy`/`reportAssignmentFailure` 两处 javadoc 里写明的单向 RPC 契约（拒绝必须携带 FAILED 上报）直接矛盾，CC-17 的修复意图覆盖了 capacity 与 submit 两个失败面，唯独漏掉 duplicate 分支。② 两处 submit 拒绝点 acquire 后不 release、put 后不 remove——构造器注释（L237-241）声称「rejection is converted to a typed rejectDeploy/reportAssignmentFailure at both submit sites」只对了一半（上报有了，清理没有）。③ 两个 submit 点此前均已执行 `nodeMetrics.taskDeployed()`（L591/777），从未运行的 zombie 计入已部署指标（观测失真）。
- **风险**: 恢复路径「半个成功」（F2 邻型）：N 的失败可见、N+1 的拒绝不可见，恢复延迟从秒级（typed 失败驱动）退化为 taskTimeoutMs/lease 级（60s/15s 兜底）；shutdown 竞态窗口内每个 zombie 钉住 1 个容量许可，使该 TM 在下次 epoch 轮换前的有效容量悄悄 -1。不丢数据、不无界（轮换/停机清理兜底），故 P2。
- **建议**: ① `receiveAssignment` 的两个 duplicate 分支按 incumbent 状态分流：incumbent 处于运行态（`!isFinished()` 且有 future）→ 维持静默（重投语义正确）；否则调用 `reportAssignmentFailure` 后 return（复用现成通道，与 CC-17 对齐）。② 两处 submit 拒绝 catch 补 `runningTasks.remove(taskKey, runningTask)` + `runningTask.semaphoreReleased.compareAndSet(false,true)` + `capacitySemaphore.release()`（照抄同方法内 build-failure 分支 L810-815 的现成清理形态）。③ `taskDeployed()` 移到 submit 成功之后。
- **失效面枚举**: 许可全部触碰点逐一核查：`receiveAssignment` tryAcquire L554 → release L568/587（与自身 acquire 配对 ✓，但无上报 ✗ 主发现）；submit catch L594-600（✗ 不清理）；`deployTask` tryAcquire L702 → staleRejected L768 ✓、位移 L772-777 ✓（CAS）、build-failure L810-815 ✓（CAS + 条件 remove）、submit catch L780-790（✗ 不清理，remote 有位移自愈）；`cancelTask` L961-963 ✓；`updateFencingToken` L1077-1079 ✓；`RunningTask.run` finally L171-173 ✓；`stop()` L381 `clear()` 不释放（进程消亡，○）；`submitTerminalReport` L1107-1113 inline 兜底 ✓。assignment 拒绝面：not-running ✓（有上报）、fencing ✓（有上报）、capacity ✓（有上报）、duplicate containsKey ✗、putIfAbsent 竞态 ✗（仅这两处静默）。
- **信心水平**: 确定（代码事实：静默分支、两处不清理、契约 javadoc 均直接可证）；「恢复被拖满超时周期」的完整触发链为很可能（依赖同 epoch 重试的 coordinator 行为，attemptNumber/attemptId 与 fencingEpoch 的字段分离强烈暗示 epoch 非按尝试轮换，未逐行追 JobCoordinator 重试路径）
- **误报排除**: 不属「duplicate 拒绝本就该静默」——对运行态 incumbent 静默正确，本条限定的正是 zombie/不可用 incumbent 场景与 submit 清理缺口；不属「shutdown 竞态进程即死、无后果」——CC-17 专用测试钩子证明该状态被视为真实运行态（running==true 而执行器已死），且 embedded 模式 TM 是长驻库对象，同 epoch 重试在进程存活期内发生；不与第 1 轮任何条目重复（B12 曾判 ✓，本轮修正该判定）。
- **复核状态**: 已保留

---

### [nop-stream-core-runtime-C8-010] `EdgeConfig.receiveWindow`/`packetSize` 为零消费者死配置：声明的流控参数静默不生效

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/flow/EdgeConfig.java:23-31`（字段与构造）、`flow/FlowControlPolicy.java:21-27`（G27 裁定注释）；消费面 `execution/GraphExecutionPlan.java:645-647,656-658`、`runtime/transport/RemoteGraphExecutionPlanBuilder.java:544-545`
- **证据片段**:
  ```java
  @DataBean
  public class EdgeConfig implements Serializable {
      private final FlowControlPolicy flowControlPolicy;
      private final int queueCapacity;
      private final int receiveWindow;   // 全仓唯一消费点：equals + toString
      private final int packetSize;      // 同上
  ```
- **严重程度**: P3
- **并发场景**: 无运行期竞态——配置面 vs 行为面的一致性缺口（C8 步骤 1「声明语义与实现一致」与步骤 4「过载行为声明」的配置侧延伸）。
- **触发时序**: 理论风险，未具体化（非竞态）。用户经公开 API `JobEdge.setEdgeConfig`（`JobEdge.java:125`）或 `EdgeConfig` 四参构造设置 `receiveWindow`/`packetSize` → 构建期被 equals/hashCode/toString 携带（`GraphExecutionPlan.java:645-647`、`RemoteGraphExecutionPlanBuilder.java:544-545`）→ 运行期零消费——背压行为完全由 `queueCapacity` + `BufferPool` 决定，两个参数对过载水位无任何影响。
- **现状**: G27 已永久移除 credit-based 流控策略（`FlowControlPolicy` 仅剩 `BLOCKING_QUEUE`），`receiveWindow`/`packetSize` 是该裁定的残留字段：生产构建侧只用 `defaultConfig()`（`DefaultDeploymentPlanProvider.java:48`、`DeploymentPlanGenerator.java:87`），无任何 DSL/builder 通路设置非默认值，也无任何代码读取它们参与流控。
- **风险**: 编程式使用者设置这两个参数后获得「已生效」的假象（进入 equals/日志，看似被尊重）；未来若有人「补实现」会在数据面引入未经裁定的第二套流控机制。无运行期正确性风险。
- **建议**: 三选一：① 删除两个字段（G27 清理收尾，需同步 equals/toString 与序列化兼容评估）；② javadoc 显式声明为保留/无效字段（标注 G27 出处）；③ 若 owner doc 已声明其为 inert，则仅在 `EdgeConfig` 构造器对非默认值打 WARN。以 ① 为优（本模块对「无静默 no-op」有明文惯例）。
- **失效面枚举**: `EdgeConfig` 四字段逐一核查：`flowControlPolicy`（被 `RecordWriter.validateFlowControlPolicy` typed 校验消费 ✓）；`queueCapacity`（`resolvePartitionCapacity` 消费 ✓，其 ≤0 静默回落已由第 1 轮 C8-006 立项，不重复）；`receiveWindow`/`packetSize`（本发现，零消费）。同族死配置 grep：两字段全仓仅 equals（2 处）+ toString（2 处）引用，无其他消费者。
- **信心水平**: 确定（零消费为 grep 可证事实）
- **误报排除**: 不属「有界声明存在的缓存/队列」误报——问题恰是声明了参数而无实现；不属纯命名/风格问题——它是用户可达的公开 API 面（`JobEdge` 公开 setter + `@DataBean` 可序列化），且 C8 正文把「过载时行为是否有文档声明且与实现一致」列为步骤 4 必查项。若 owner doc 已明文声明两参数为保留字段，本条应降级/驳回（本轮盲审边界内无法核对该声明，复核阶段请对照 owner doc 裁定）。
- **复核状态**: 已降级（P3→已裁定边界）

# C8 背压、流控与过载行为 — nop-stream-core-runtime（深挖第 3 轮追加）

- **审计对象**: 同第 1 轮（`nop-stream/nop-stream-core/src/main/java`、`nop-stream/nop-stream-runtime/src/main/java`；`_` 前缀生成文件不在发现对象内）
- **盲审边界**: 本轮仅读取本维度第 1/2 轮文件（`C8-backpressure.md`、`C8-round2.md`）与提示词（`ai-dev/skills/concurrency-resource-audit-prompt.md` 共享前缀 + C8 正文）；未读其他审计维度文件、plans、bugs、lessons
- **方法**: 在第 1 轮生产-消费边界清单（B1–B20）与第 2 轮盲区核查基础上，第 3 轮对三类盲区做定向排查：① 前两轮未盘点的生产-消费边界（CheckpointCoordinator trigger→ack→timeout 面、JobCoordinator 周期驱动/故障检测/fan-out、checkpoint RPC 控制面、集群选主/注册面、LOCAL 模式 barrier 注入面、指标注册面、治理清扫面）；② 信号量/限流许可泄漏漏查（全仓 grep 收口）；③ 过载行为与声明语义不一致漏查（远程 transport 的 EdgeConfig 消费、barrier fan-out 过滤一致性、对齐超时与背压的复合行为）

---

## 深挖第 3 轮追加

## 一、第 3 轮盲区核查记录（无发现结论，供合并裁定去重）

| 排查点 | 结论 |
|---|---|
| `CheckpointCoordinator` trigger→ack→timeout 账目（`pendingCheckpoints`/`numPendingCheckpoints`/`checkpointSegments`/`failedCommitParticipants`/`checkpointSuccessMap`） | 全部记账点核对：触发受 `maxConcurrentCheckpoints`+`minPause` 双门控（L459-489）；timeout CAS 兜底 abort 并递减（L1467-1481）；persist/commit-fanout/retention 三执行器均有界 + 终态 shutdown 纪律（L1090-1101, L1567-1638）；failed-commit 重试在位替换防 abort 门控误判（L1555-1563）——记账有界，无发现 |
| `PendingCheckpoint` ack 收集与 async/sync 持久化分叉 | async 路径 I/O 出监视器；sync 回退在 ACK 调用线程内联持监视器做存储 I/O 是 D1(d) 声明的钉死语义——但其与共享 poller 的复合后果并入 C8-011，不单独立项 |
| `JobCoordinator.startPeriodicCheckpoints`/`detectFailures`/`OpsJobManager.governanceSweep`/`StreamMetricsReporter.report`/`TaskCheckpointWiring` barrier 调度器 | 五个 `scheduleAtFixedRate/Delay` 周期任务全部有 per-run try/catch（JDK 单异常停调度语义已防）；`failureDetector` 线程上跑 `globalRecovery` 使检测延迟一个恢复时长（fixed-rate 补跑），恢复 CAS 去重 + 双预算（real/stall）+ 冷却——有界降级，无发现 |
| `JdbcLeaderElector.checkElection` 自续循环 | try/catch 包裹 + `scheduleCheck()` 自续 + stopping 守卫（防停机后自夺主）；epoch 来自持久计数器行（CC-15）——无发现 |
| `StreamNodeAutoRegistration`/`NodeDiscoveryConsistencyChecker` | 注册/注销成对（`@PostConstruct`/`@PreDestroy`），无循环无队列——无发现 |
| `StreamControlRpcServer`/`StreamControlRpcProxyFactory`/`CorrelatingRpcService` | 委托平台 `MessageRpcClient`/`RpcChannelState`（超时/关联属平台设施，按共享前缀口径不重审）；timer 单线程 + stop 销毁配对——使用方式无发现 |
| `TaskCheckpointWiring`（LOCAL 模式 barrier 注入 + epoch 精确 abort） | `allInvokables` 用 CopyOnWriteArrayList（防调度线程 CME，注释声明）；per-invokable 注入遏制；abort handler 跨线程直调 `InputGate.abortBarrierAlignment` 的并发安全论证完整（L336-345）——无发现 |
| `CheckpointBarrierTracker.inFlight` 泄漏与 ACK 计数一致性 | epoch 移除路径 4 条（末位 ACK/快照错误/notifyCheckpointAborted/source 拒 barrier）齐全；`operatorsToAck` 计数与 snapshotCallback 接线一致（`StreamSourceOperator`/`SourceReaderOperator` 均继承 `AbstractStreamOperator`，链非空则计数 ≥1，每个被计数者都有回调）——无发现 |
| `InputGate.inFlightAlignments` 陈旧/重复 barrier 条目 | 重复 id 走 get-or-create 可能新建永不完成的 alignment，但由对齐超时（30s typed 失败）或 abort 通道收敛——有界降级，维持第 1 轮 F2 表 ✓ 判定 |
| `JobCoordinator.subtaskLiveness`/`deployIssuedAtMs`/`deployTransportFailures`/`completedSubtaskKeys` 生命周期 | 恢复路径统一 clear（L2023-2030），COMPLETED 上报即 remove（L1124-1128）；键 = vertex/subtask，基数受分配规模约束——无发现 |
| 信号量/限流许可全仓 grep 收口（第 1/2 轮文件之外） | `Semaphore/acquire/release/tryAcquire` 全部命中点仅 `BufferPool`/`ResultPartition`/`TaskManager.capacitySemaphore`（前两轮已逐一核对）+ `ChannelQueueGauges.release`（gauge 绑定非许可）；`IRateLimiter` 类平台设施无其他使用点；`SourceRateLimiter` 第 2 轮已复核——无许可泄漏漏网点 |
| `ChannelQueueGauges`/`DbBacklogGauges` 静态 HOLDERS map | 可变持有者反陷阱设计，类注释显式裁定「条目不删除 + 增长受进程内 distinct 通道身份约束」（micrometer 静默丢弃陷阱）——已声明边界，不立项（同族对照：C8-007 未声明故立项） |
| `sendBarrierToAllTaskManagers`（终态 savepoint 路径 L2941-2954 全节点）vs 周期路径（L974-1000 仅 assigned 节点）过滤不一致 | 两处均为 fire-and-forget（接口契约：无响应、无内建超时、发送由后端限界），未分配节点收到 barrier 为无害 no-op，终态路径一次性触发——低于立项阈值 |
| barrier fan-out per-node 单线程执行器队列无界 + 死节点 | 已核实接口契约（`IStreamTaskRpcService` javadoc：one-way fire-and-forget，发送有界性归后端）：单任务耗时 = 一次 DB INSERT/后端 send，不因节点死亡而阻塞（无响应等待）——无队列积压链；死节点行积压已由第 1 轮 C8-003 账本条目覆盖 |
| `DataPlaneMessageServiceAdapter` 全文（第 2 轮仅查头部） | 解码失败 → `WireDecodeFailureAware` typed 上抛（fail-fast），legacy consumer 丢弃有 WARN——与第 1 轮 C8-005 对照的家族语义一致，无发现 |
| `RetentionCleaner` coalescing | CAS + recheck 标志 + 尾随重跑，双重平面保留——无发现 |

---

## 二、发现条目（接续第 2 轮序号）

### [nop-stream-core-runtime-C8-011] launch 模式共享 2 线程 poller：慢进度数据面积压以「每行 ≤10s × 无界批量」垄断全部派发线程，数据面过载使控制面（barrier/deploy/cancel/状态上报）无界期饥饿——bounded-offer 裁定只覆盖零进度情形

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/launch/PollingJdbcMessageService.java:96-100`（2 线程池）、`293`（每订阅 `scheduleWithFixedDelay` 共享池）、`305-338`（单 poll 内联串行派发全部物化行）；对照裁定注释 `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteInputChannel.java:665-687`；放大器 `CheckpointCoordinator.java:652-656,678-699`（sync 持久化内联于 ACK 调用线程）
- **证据片段**:
  ```java
  // PollingJdbcMessageService — 全部订阅共享这 2 个线程（scheduled 池 worker 数 = core = 2，永不增长）
  this.poller = Executors.newScheduledThreadPool(2, r -> { Thread t = new Thread(r, "polling-jdbc-msg"); ... });
  ...
  this.task = poller.scheduleWithFixedDelay(this::poll, pollIntervalMs, pollIntervalMs, ...);  // 每订阅一个周期任务
  ...
  void poll() {
      List<Row> rows = jdbcTemplate.executeQuery(... "WHERE topic = ? AND id > ? ORDER BY id ASC" ...);  // 无 LIMIT：一次物化全部积压（第 1 轮 C8-002）
      for (Row row : rows) {
          ...
          consumer.onMessage(topic, message, ctx);   // 内联串行：数据面 channel 的有界 offer 在此线程内等待
          lastSeenId.set(row.id);
      }
  }
  ```
  ```java
  // RemoteInputChannel.EnvelopeConsumer.onMessage（既有裁定，L665-675）：
  // "under a shared dispatch loop (e.g. the polling JDBC backend's 2-thread pool
  //  serving data AND control topics) two dead channels stalled every delivery on
  //  the TaskManager. ... A full queue with ZERO consumer progress for the whole
  //  window is a stalled downstream: fail the channel typed ... Healthy slow
  //  consumers never trip this — ANY freed slot within the window lets the offer
  //  succeed (dispatch-level backpressure is preserved)."
  if (!queue.offer(element, enqueueOfferTimeoutMs, TimeUnit.MILLISECONDS)) {   // 单行至多占线程 10s
  ```
- **严重程度**: P1
- **并发场景**: TM JVM 内单个 `PollingJdbcMessageService` 实例同时承载数据面（`DataPlaneMessageServiceAdapter` 包装后的各 channel 订阅，`TaskManagerMain.start()` L87 起同一 `messageService` 三路接线：数据面 + 本节点 `StreamControlRpcServer` + coordinator 上行 proxy）与控制面（`nop-stream.rpc.task.{nodeId}`：deployTask/triggerCheckpoint barrier/cancelTask/updateFencingToken/notifyCheckpointComplete）；2 个 poller 线程是全部订阅的唯一派发点。coordinator JVM 同构（uplink topic 的 TaskStatusReport/checkpoint ACK + RPC reply topics 共享另一组 2 线程）。
- **触发时序**: T1 下游消费变慢（如 sink I/O 延迟抬升），channel A 队列（1024）打满但消费以 ~8s/条的速率继续推进——零进度不会发生，`RemoteInputChannel` 的 typed overflow 裁定**不触发**（注释原文：any freed slot within the window lets the offer succeed）。T2 poller 线程 P1 执行 channel A 的 `poll()`：SELECT 一次物化全部积压 N 行（第 1 轮 C8-002 的无 LIMIT 批读），随后逐行内联派发，每行 `offer(10s)` 等待 8-10s 后成功——P1 被连续占用 ≈ N×10s（N=1000 时 ≈2.8 小时）。T3 相关过载下第二个数据 topic（同一慢 sink 的另一条边）同理占住 P2；即使只有一个慢 topic，其余几十个订阅的 poll 任务也在 2 线程的无界延迟队列中排队，实际派发间隔被拉长到 P1 的批量时长。T4 期间本节点控制 topic 的行持续写入但无人 poll：每个 checkpoint 周期的 barrier RPC、（若恰逢恢复）deployTask/cancelTask/updateFencingToken、commit 通知全部滞留。T5 后果链：coordinator 侧本 epoch 等不到该 TM 的 ACK → `checkpointTimeout` 到点 `abortPendingCheckpoint("Timeout")`（`CheckpointCoordinator.java:1467-1477`）→ 过载持续期间**每个周期性 checkpoint 都超时中止**（数据不丢——source 重放兜底——但 checkpoint 机制对该作业功能性失效）；恢复场景下 deploy 行同样滞留 → 新任务不启动 → R5-CC-07 部署宽限期到期再触发恢复 → 正反馈放大，直至 stall 预算（`maxStallRestarts`）typed 失败兜底。T6 coordinator JVM 侧同族链：uplink 批量 ACK 经 poller 内联派发，`asyncSnapshotEnabled=false` 时每个 ACK 在 poller 线程上内联执行 `completePersistSynchronously` 的存储 I/O（JDBC/S3 写，持 coordinator 监视器，`CheckpointCoordinator.java:678-699`）——慢存储时 2 线程串行化为 ≤2 并发持久化，状态上报/ACK 派发同窗饥饿。
- **现状**: 代码注释（`RemoteInputChannel.java:665-675`）证明「2 线程池服务 data+control、派发线程被 stall」这一危害族是**已知且已裁定**的——但既有缓解只覆盖「队列满且整个窗口零消费进度」的单行情形（10s 后 typed 失败，快速返回：`onMessage` 首行 `if (finished) return null`）。两个未定价的残留构成新缺陷：① 「慢进度」消费（每窗口至少释放 1 槽）使每行都等满 ~10s 且永不触发 typed 失败，行级上界乘以 C8-002 的无界批量 = **单次 poll 的线程占用无上界**；② 数据-控制面隔离仅在 topic 层声明（`DataPlaneMessageServiceAdapter` javadoc：「topic-addressed, disjoint topics」），派发线程层无任何隔离、预算或优先级。类 javadoc 的 Limitations 节也未声明「数据面过载会延迟控制面派发」。
- **风险**: 恰在 C8 的目标场景（上游快于下游的过载期）内必然触发：checkpoint 全周期中止、恢复动作按 poller 占用时长成比例延迟、恢复宽限正反馈。owner doc 的过载声明语义（WI6 门控/typed 溢出/有界降级）都建立在「通道失败可被及时观测与恢复」之上，而本缺陷使失败信号本身（FAILED 状态上报经上行 topic）与恢复动作（deploy 经任务 topic）共用被饥饿的派发面——过载时系统的控制闭环退化为「轮询超时兜底的全量恢复」，且每轮恢复都重新经过同一条被饥饿的路径。
- **建议**: 三层递进：① 派发隔离——poller 池按面拆分（控制 topic 专用 ≥1 线程 + 数据 topic 池），或每订阅独立单线程执行器（订阅数即线程数，launch 模式节点内订阅数十量级，可配上限 + 共享回落）；② 批量上界——poll 查询加 `LIMIT <batch>`（与第 1 轮 C8-002 的修复同一处改动，一并消除 OOM 与占用两个后果）；③ 占用预算——单次 `poll()` 加墙钟预算（超时即保存 cursor 断点返回，剩余行下轮续派），把「慢进度×无界批」改造为有界占用。③ 与平台 `QueueOverflowPolicy`/`IBlockingSource` 语义对齐。sync 持久化内联（T6）应改为强制 async（`validateIncrementalConfig` 已有 sync/incremental 互斥先例，可加 launch 路径 sync+共享 poller 的组合校验）。
- **失效面枚举**: 共享派发面的全部消费者逐一核查：① TM 数据面 channel 订阅（`RemoteInputChannel.EnvelopeConsumer`，本发现主体——10s 有界 offer 是已裁定的单行上界，批量占用未裁定）；② TM 控制 topic（`MessageRpcServer`→`TaskManager` 各 RPC handler，派发饥饿直接延迟 deploy/barrier/cancel/fencing/commit——本发现后果面）；③ coordinator uplink（TaskStatusReport/checkpoint ACK——本发现后果面 + sync 持久化内联放大器）；④ coordinator reply topics（`MessageRpcClient` 响应关联，非 void RPC 的响应延迟——同面）；⑤ `EnvelopeConsumer` 解码（每行微秒级，安全）；⑥ `PollingJdbcMessageService.javaDeserialize`（每行有界于 payload 尺寸，安全）。同族兄弟实现：`SysDaoMessageService`（nop-sys，平台消息服务自有派发模型，不在本模块范围）；`LocalMessageService`（进程内直发，无 poller）。启动回放路径（`Subscription.start()` cursor=0 全量重放，Item 14 声明语义）与本发现的稳态饥饿不同窗，不重复计数。
- **信心水平**: 确定（2 线程共享、内联串行派发、每行 ≤10s 有界等待、慢进度不触发 typed 失败均为代码直证；「2 线程池 serving data AND control topics」的危害族被仓库自己的裁定注释点名）；「checkpoint 全中止 + 恢复正反馈」的完整链为很可能（依赖过载持续时长与 checkpoint 超时的相对大小，各参数可配但默认值下即成立）
- **误报排除**: 不属「测试专用代码豁免」——第 1 轮 C8-002/003/005 已裁定该类是生产 launch 路径（owner doc 运维手册权威启动方式）组件，本轮沿用同一口径；不与前两轮重复——C8-002 是「单 poll 物化的内存无界」（OOM 面），C8-003 是「表无清理 + 门控不可用」（存储面），本条是「共享派发线程的调度面饥饿」，根因（2 线程池 + 行级占用上界）与它们独立，修复 C8-002 的 LIMIT 只是本条三层修复之一而非全部；不属「已声明边界」误报——`RemoteInputChannel` 注文声明的边界恰好只覆盖零进度单行情形，慢进度批量占用与控制面饥饿均无任何声明。
- **复核状态**: 已保留

---

### [nop-stream-core-runtime-C8-012] `EdgeConfig.queueCapacity` 在远程/distributed transport 被静默忽略：消费侧通道容量硬编码 1024，声明的流控参数跨 transport 语义不一致

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteGraphExecutionPlanBuilder.java:69,249-252`（硬编码容量与唯一构造点）、`543`（`getQueueCapacity()` 仅出现于 equals 等价比较）；对照本地路径消费点 `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/GraphExecutionPlan.java:796-801`（`resolvePartitionCapacity`）；声明面 `flow/EdgeConfig.java:23-31`、公开 API `JobEdge.setEdgeConfig`（`JobEdge.java:125`）
- **证据片段**:
  ```java
  // RemoteGraphExecutionPlanBuilder — 远程边的消费侧通道容量与 EdgeConfig 无任何关联
  private static final int DEFAULT_CHANNEL_QUEUE_CAPACITY = 1024;
  ...
  RemoteInputChannel remoteChannel = new RemoteInputChannel(
          messageService, topic, epochId,
          DEFAULT_CHANNEL_QUEUE_CAPACITY, 0L,                       // 硬编码，不读 edgeConfig
          RemoteInputChannel.DEFAULT_ENQUEUE_OFFER_TIMEOUT_MS, subscribe);
  ```
  ```java
  // 对照：本地路径 queueCapacity 是唯一生效的流控旋钮（第 1 轮 B1/边界清单）
  // GraphExecutionPlan.resolvePartitionCapacity: edgeConfig.getQueueCapacity() > 0 → capacity
  // 远程路径：RemoteResultPartition super(1) 无本地队列（G53 有意排除），
  //           消费侧 1024 固定，getQueueCapacity() 全文件仅 equals(543)/toString 引用
  ```
- **严重程度**: P3
- **并发场景**: 无运行期竞态——同一声明参数在两种 transport 下的行为面一致性缺口（C8 步骤 1「满载行为与声明语义一致」与步骤 4「过载行为声明」的配置侧延伸，C8-010 的同族跨 transport 变体）。
- **触发时序**: 理论风险，未具体化（非竞态）。用户经 `JobEdge.setEdgeConfig(new EdgeConfig(policy, 64, ...))` 把某边容量设为 64（预期：更小的缓冲水位/更早的背压）→ 单机 LOCAL 形态该值生效（`resolvePartitionCapacity`）→ 同一作业以 owner doc 运维手册的分布式形态启动（`TaskManagerMain`/`JobCoordinatorMain`，`RemoteGraphExecutionPlanBuilder` 重建通道）→ 消费侧队列实为 1024 固定，生产侧无本地队列——容量旋钮对该边完全失效，且无日志、无校验、无 typed 拒绝（对比：`RecordWriter.validateFlowControlPolicy` 对不支持 policy 是 typed 拒绝的先例）。
- **现状**: `queueCapacity` 的消费面 = 仅 LOCAL `ResultPartition` 构建路径；远程路径上 `EdgeConfig` 的该字段与 `receiveWindow`/`packetSize`（第 1 轮第 2 轮 C8-010 已立项的零消费字段）一样不参与任何流控决策，但 C8-010 的失效面枚举当时依据 `resolvePartitionCapacity` 判定 `queueCapacity`「消费 ✓」——本轮补查远程路径后修正该判定：**该字段是「半消费」状态，跨 transport 语义分裂**，比全死字段更具误导性（同一作业形态间迁移时背压水位无变化，与用户声明差最高 16 倍）。
- **风险**: 编程式使用者调参获得「已生效」假象：调小容量以期降内存/降延迟无效果（分布式形态仍驻留 1024×通道矩阵），调大以期增缓冲抗抖动同样无效果；容量语义不一致还使「按 LOCAL 压测推导的过载水位」无法外推到分布式形态。无正确性风险（两侧各自的界都成立）。
- **建议**: 三选一：① 远程构建路径消费 `edgeConfig.getQueueCapacity()`（≤0 回落 `DEFAULT_CHANNEL_QUEUE_CAPACITY`，对齐 `resolvePartitionCapacity` 语义，并把第 1 轮 C8-006 的「≤0 typed 拒绝」建议同步应用于该路径）；② 若远程容量差异化属未裁定范围，则在 `RemoteGraphExecutionPlanBuilder` 对非默认 `queueCapacity` 打一次性 WARN（诚实 no-op，对齐 WI6 门控禁用的「announce once」先例）；③ owner doc 显式声明「queueCapacity 仅 LOCAL 形态生效」并在此轮修复中与 C8-010 的 receiveWindow/packetSize 处置合并裁决。以 ① 为优。
- **失效面枚举**: `EdgeConfig` 四字段跨 transport 消费矩阵（合并 C8-010 的枚举并修正）：`flowControlPolicy`（LOCAL `validateFlowControlPolicy` typed 校验 ✓；远程 `RemoteGraphExecutionPlanBuilder` 构建路径同型校验 ✓——两形态一致）；`queueCapacity`（LOCAL ✓ 消费；**远程 ✗ 零消费**——本发现，修正第 2 轮 C8-010 失效面枚举中「消费 ✓」的口径）；`receiveWindow`/`packetSize`（两形态均零消费，C8-010 已立项，不重复计数）。`new RemoteInputChannel` 全仓生产构造点唯一（`RemoteGraphExecutionPlanBuilder.java:249`），无同族漏网；`DEFAULT_QUEUE_CAPACITY=1024` 的其余引用（`RemoteInputChannel.java:98,171`）为类内默认构造链，同源于硬编码。
- **信心水平**: 确定（零消费为 grep 可证事实：`getQueueCapacity` 在远程构建器仅 equals/toString 引用）
- **误报排除**: 不属「有界声明存在的队列」误报——1024 有界本身无问题，问题是用户声明的另一个界被静默忽略；不与 C8-010 重复——那是「全死字段」，本条是「LOCAL 消费/远程不消费」的跨形态语义分裂，且修正了 C8-010 失效面枚举的一处判定口径（合并裁定时应将两条按同族相邻处理，根因同为 G27/流控参数的声明-实现契约缺口，失效层不同：字段级 vs transport 装配级）；不属纯命名/风格——`JobEdge.setEdgeConfig` 是公开 API，owner doc 若已声明「远程形态忽略容量」则本条应降级/驳回（本轮盲审边界内无法核对该声明，复核阶段请对照 owner doc 裁定）。
- **复核状态**: 已保留

---

## 三、覆盖范围说明

**本轮新精读文件**: `PollingJdbcMessageService`（全文）、`RemoteInputChannel`（onMessage/溢出/超时/EOS 全路径）、`CheckpointCoordinator`（trigger/ack/complete/abort/timeout/persist/retention/shutdown 全路径）、`JobCoordinator`（start/failureDetector/triggerCheckpoint/terminateWithTerminalSavepoint/barrier fan-out/liveness 生命周期）、`CheckpointBarrierTracker`（全文）、`InputGate`（对齐超时/切换/abort 清理段）、`TaskCheckpointWiring`（全文）、`GraphModelCheckpointExecutor`（骨架 + barrier 调度段）、`AssignmentPlanner.executeAssignmentFanOut`、`JdbcLeaderElector`（选举/续租循环）、`StreamNodeAutoRegistration`、`RetentionCleaner`（调度/清理段）、`StreamMetricsReporter`（全文）、`OpsJobManager`（governance 段）、`AlertService`/`WebhookAlertChannel`（复查）、`DataPlaneMessageServiceAdapter`（全文）、`ChannelQueueGauges`/`DbBacklogGauges`（全文）、`StreamControlRpcServer`/`StreamControlRpcProxyFactory`（全文）、`TaskManagerMain`/`JobCoordinatorMain`（接线段）、`IStreamTaskRpcService`（契约 javadoc 全文）。

**判「无发现」的关键复核结论**: 周期任务五处全部有异常遏制（无 JDK 停调度暴露）；信号量/限流许可在前两轮文件之外无遗漏点；checkpoint 记账五容器（pending/segments/failedCommits/successMap/history）均有界有清理；两个 gauge 静态注册表的「不删除」为注释声明的已裁定边界；barrier fan-out 队列无积压链（one-way 契约 + 后端 send 有界）；对齐超时与背压的复合行为（过载 >30s 触发 typed 失败→重启→预算兜底）为已声明有界降级，不重复立项。

# C8 背压、流控与过载行为 — nop-stream-core-runtime（深挖第 4 轮追加）

- **审计对象**: 同第 1 轮（`nop-stream/nop-stream-core/src/main/java`、`nop-stream/nop-stream-runtime/src/main/java`；`_` 前缀生成文件不在发现对象内）
- **盲审边界**: 本轮仅读取本维度第 1/2/3 轮文件（`C8-backpressure.md`、`C8-round2.md`、`C8-round3.md`）与提示词（`ai-dev/skills/concurrency-resource-audit-prompt.md` 共享前缀 + C8 正文）；未读其他审计维度文件、plans、bugs、lessons
- **方法**: 在前 3 轮生产-消费边界清单（B1–B20）与盲区核查基础上，第 4 轮按指令做两类定向排查：① C8-011（共享 poller 饥饿）的同族兄弟——对两模块 main 源全部 18 个执行器/线程创建点做全量清点（`Executors.new*` + 裸 `new Thread` + 未指定 executor 的 CompletableFuture 异步点三路 grep），逐一核对「共享执行器面 × 慢进度/积压垄断」形态；② 前三轮未盘点的边界——精读清点中浮出的两个未盘点面（core 侧 `io.nop.stream.core.execution.task.TaskExecutor`、`connector/lookup/CachingTableLookup`），并核对 commitExecutor CallerRuns 兜底的调用方身份裁定

---

## 深挖第 4 轮追加

## 一、第 4 轮盲区核查记录（无发现结论，供合并裁定去重）

| 排查点 | 结论 |
|---|---|
| `TaskManager.commitExecutor` CallerRuns 兜底（L291-318）对 C8-011 的同族叠加（兜底把 2PC commit 内联到控制面派发线程） | 裁定记录（plan 369 F-16，L296-308）**显式核对过调用方身份**：「callers … are the TM-side control-topic dispatch thread / coordinator commit forwarder — NOT the checkpoint monitor; the fallback delays at most ONE commit per saturated notify call」，且 `nop.stream.task.commitCallerRuns.total` 已计数——已声明边界（C8-011 的后果面不因它扩大），不立项 |
| `JdbcLeaderElector` 调度面 | `scheduleCheck()` 委托平台 `AbstractLeaderElector`（平台基础设施，按共享前缀口径不重审）；选举循环语义第 3 轮已核——无发现 |
| 未指定 executor 的 CompletableFuture 异步点（commonPool 隐式共享面，C8-011 同族候选） | main 源 grep：`supplyAsync/runAsync` 零命中、`thenXxxAsync`/`orTimeout` 零命中——无隐藏 commonPool 面，无发现 |
| 执行器创建点全量清点（18 处） | 除本轮两个新发现对象（core `TaskExecutor`、`CachingTableLookup`）外，其余 16 处均已被第 1–3 轮盘点（B10–B16、C8-004、C8-011、第 3 轮五处周期任务/三执行器、barrier fan-out、RPC timer、webhook drain）——无第三处漏网 |
| `CachingTableLookup` 每记录同步 lookup→delegate | WI11 裁定 R5 显式声明「per-record path fully synchronous（no async operator）」——声明边界，不立项 |
| `CachingTableLookup.close()` 与 scan 线程的 REE 竞态（`closed` 检查后、`execute` 前 close） | 窗口仅存在于停机期（`closed=true` 先于 `shutdownNow`，最坏杀死垂死实例的 scan 任务），无运行期后果——不立项 |
| `CachingTableLookup.evictOldestIfNeeded` 满载时每 miss O(n) 全表扫描（任务线程热路径） | 有界（≤ maxEntries）且为 CPU 成本而非背压/积压形态——属 C9 热路径口径，本维不计（交叉维度合并时按 C9 候选登记） |
| `SubtaskTask` 状态机（CREATED→SCHEDULED→…） | `CREATED→SCHEDULED` 转移唯一调用点在 `run()` 内（L86）——排队任务永驻 CREATED，被 C8-013 吸收，不单独计数 |

---

## 二、发现条目（接续第 3 轮序号）

### [nop-stream-core-runtime-C8-013] LOCAL 模式 core `TaskExecutor` 无界队列 + 无容量守卫：Σ subtask 超过池大小（CPU 核数）时下游任务永久排队不启动，上游背压阻塞互锁——整作业静默永久挂死，三层检测全部不可见（B12 的 LOCAL 同族兄弟）

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/task/TaskExecutor.java:116-117`（`newFixedThreadPool` = 无界内部队列 + 无拒绝处理器）、`environment/StreamExecutionEnvironment.java:74`（`DEFAULT_POOL_SIZE = availableProcessors`）、`environment/StreamExecutionEnvironment.java:543-574`（裸构造 + 提交全部 subtask + 无超时 `awaitCompletion()`）、`runtime/execution/GraphModelCheckpointExecutor.java:275,391`（checkpointed/savepoint LOCAL 路径同型裸构造）、`runtime/execution/SupervisionLoop.java:381-387`（轮询只认 FAILED/COMPLETED/CANCELED，CREATED 不可见）；对照声明面 `task/TaskExecutor.java:56-61`（javadoc「Tasks are queued if all threads are busy」）
- **证据片段**:
  ```java
  // TaskExecutor — 无界队列、无 RejectedExecutionHandler、无「提交数 vs 池大小」守卫
  this.executorService = Executors.newFixedThreadPool(poolSize,
          NopStreamThreadFactory.named("stream-task-executor-"));
  ```
  ```java
  // StreamExecutionEnvironment.runLocal — 池大小 = CPU 核数，subtask 数 = Σ(vertex parallelism)，无校验
  TaskExecutor executor = new TaskExecutor();
  for (String vertexId : plan.getSortedVertexIds()) {          // 拓扑序：source/middle 先提交
      for (Subtask subtask : vertexSubtasks) {
          executor.submitTask(subtaskTask);                     // 超出池大小的任务进无界队列，零告警
      }
  }
  executor.awaitCompletion();                                   // 无超时版本，永久阻塞
  ```
  ```java
  // SubtaskTask.run — SCHEDULED 转移只在 run() 内：排队任务永驻 CREATED
  if (!state.compareAndSet(State.CREATED, State.SCHEDULED)) { ...
  ```
  ```java
  // SupervisionLoop 轮询 — CREATED 既非 FAILED（不触发重启）也非 COMPLETED/CANCELED（不退出）
  if (allTasksCompletedOrCanceled(tasks)) { break; }
  Thread.sleep(pollIntervalMs);                                 // 永久轮询，零进度零检测
  ```
- **严重程度**: P1
- **并发场景**: LOCAL 部署模式（owner doc 权威口径中与 REMOTE 并列的两条执行路径之一）主提交线程（runLocal 调用方 / SupervisionLoop 所在线程）vs `stream-task-executor-*` 固定池工作线程。流式 SubtaskTask 是常驻线程模型（运行至作业终态才归还线程），池必须 ≥ Σ subtask 才可能运行全部任务。
- **触发时序**: T1 用户经公开 API 设并行度（`StreamExecutionEnvironment.parallelism(int)`，L163）或逐 vertex 设并行度，Σ subtask = 12，宿主机 8 核。T2 runLocal 提交 12 个 SubtaskTask：前 8 个（拓扑序靠前的 source/middle）占满全部 8 线程，后 4 个（sink/下游 middle）进入执行器内部无界队列——提交方与 javadoc 均视队列为正常行为，零 WARN。T3 运行中的上游任务持续写出 → 各边 `ResultPartition` 队列（默认 1024）填满 → 上游任务进入 `queue.put` 永久阻塞（B1 背压，恰是设计行为）→ **已占线程永不释放**。T4 队列中下游任务永无线程可用、`run()` 永不执行、状态永驻 CREATED——「上游等队列空间 ⟷ 下游等线程」形成资源互锁（线程-背压死锁环），确定性成立、无任何竞态前提。T5 检测面三层全空：① runLocal 的 `awaitCompletion()` 无超时永久阻塞；② checkpointed 路径 SupervisionLoop 永久 sleep 轮询（CREATED 不进任何分支）；③ restartRegion 的新任务（L572）提交进同一个饱和池同样排队——重启也无济于事。零 ERROR、零 WARN、零指标、零 typed 失败，作业表现为「正常运行中」。
- **现状**: runtime 侧同族面（B12）已被 CC-17 裁定为必须守卫：`TaskManager.taskExecutor` = 池数=capacity、队列 1000 有界、AbortPolicy、拒绝转 typed `rejectDeploy`（TaskManager.java:242-247 + 构造注释 L237-241）——项目对「任务提交超过执行容量」这一失效形态已有明确的工程裁定。core 侧 LOCAL 执行器是同族兄弟却无任何对应守卫；类 javadoc「Tasks are queued if all threads are busy」对流式常驻任务是把失效形态声明成正常行为（常驻任务下排队 = 永不启动）。
- **风险**: 条件满足时（默认并行度 1 不触发；任何 `parallelism(N>1)` × 多 vertex 的常规拓扑在小核数宿主机上即满足，dev/小规格宿主常见）LOCAL 作业确定性静默永久挂死：既不结束也不报错，无任何可观测信号；提交线程（用户 main / 检查点执行调用方）被 `awaitCompletion()` 连带挂死。对照 P1 判级口径「负载上升必然触发的死锁」+ F5「挂死对健康检测不可见」。
- **建议**: ① 提交侧守卫（最小修复）：runLocal/submitAndRun 提交前比较 Σ subtask 与 `executorService.getMaximumPoolSize()`，超限即 typed 拒绝（`ERR_STREAM_INVALID_STATE` 形态，声明「LOCAL 池须 ≥ Σ subtask」），对齐 CC-17 的 fail-fast 先例；② `TaskExecutor.submitTask` 内做同型守卫（三个提交入口统一，见失效面枚举）；③ `runLocal` 改用已有超时版 `awaitCompletion(timeout, unit)` 并在超时时 typed 失败，消除无超时挂死点；④ 可选：池大小默认改为 max(cores, Σ subtask)（提交发生在同一方法内，Σ 已知）。③④ 与 ① 取一即可消除静默性，①+③ 组合最小。
- **失效面枚举**: `TaskExecutor` 全部提交入口逐一核查：`submitJobVertex`（L138-170，main 源零调用方，仅测试使用——同病但当前不可达）、`submitTask(Task)`（L180-197，main 源零调用方）、`submitTask(SubtaskTask)`（L206-221，本发现主体，三个 main 调用点：`StreamExecutionEnvironment:570`、`SupervisionLoop:275` 初次提交、`SupervisionLoop:572` 区域重启提交——三处同病）。main 构造点 3 处全查：`StreamExecutionEnvironment:543`（runLocal）、`GraphModelCheckpointExecutor:275`（checkpointed LOCAL）、`:391`（savepoint runtime）——均裸 `new TaskExecutor()` 默认池，同病。`awaitCompletion` 两重载：无超时版（L305-317，runLocal 使用，挂死点）、有超时版（L334-360，main 源零调用方——守卫设施已存在未接线）。同族对照：runtime `TaskManager.taskExecutor`（B12/CC-17 ✓ 有界 + typed 拒绝）；`taskExecutor` 池与 C8-011 的共享 poller 无关联（独立面）。
- **信心水平**: 确定（无界队列、裸构造、无守卫、CREATED 永驻、轮询分支覆盖、无超时等待均为代码直证；死锁互锁环由常驻线程模型 + B1 阻塞语义直接推出，无竞态前提）
- **误报排除**: 不属「有界声明存在的队列」误报——执行器内部 LinkedBlockingQueue 无界且无任何上界声明，javadoc 声明的恰是失效形态本身；不属「低频管理路径」——runLocal/checkpointed LOCAL 是 owner doc 口径的一等执行路径，触发条件是常规公开 API 配置组合而非罕见状态；不与 B12 重复——B12 盘点的是 runtime `TaskManager` 内联执行器（有守卫 ✓），本条是 core `TaskExecutor`（LOCAL 模式唯一执行面，无守卫 ✗），第 1 轮边界清单 B12 未覆盖此面，属盘点缺口而非已裁定边界；不与 C8-011 重复——那是共享派发线程的调度饥饿（REMOTE launch 面），本条是固定池线程被常驻任务 + 背压联合垄断（LOCAL 面），根因、模块、执行器均不同。
- **复核状态**: 已保留

---

### [nop-stream-core-runtime-C8-014] `CachingTableLookup` 背景刷新边界（scan→refreshPool）无 per-key 在途去重、无队列上界：delegate 持续变慢时刷新作业无界积压 + 同 key 重复刷新 I/O 放大（正反馈）

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/connector/lookup/CachingTableLookup.java:211-227`（scanAndDispatch 逐条目无条件下发）、`193-198`（`newFixedThreadPool` = 无界内部队列）
- **证据片段**:
  ```java
  // scanAndDispatch — 每轮对每个过期条目无条件下发，无「该 key 已有在途/排队刷新」判断
  for (Map.Entry<Object, Entry> e : map.entrySet()) {
      if (now - e.getValue().loadNanos >= refreshAge) {
          // the worker pool's size is the concurrency bound (review F-2)
          refreshPool.execute(() -> refreshOne(e.getKey()));   // newFixedThreadPool：内部队列无界、永不拒绝
      }
  }
  ```
- **严重程度**: P2
- **并发场景**: `nop-lookup-refresh-scan-*` 单线程调度器（生产者，每 `refreshIntervalMs` 一轮）→ `nop-lookup-refresh-*` 固定池（消费者，`refreshMaxConcurrency` 线程）→ delegate（DB/远程表查询）。delegate 单次延迟 L 超过扫描间隔 R 时形成积压。
- **触发时序**: T1 池中 N 个条目全部老化（≥ refreshIntervalMs），delegate 因 DB 抖动/慢查询延迟 L > R。T2 第 k 轮扫描把全部 N 个老化条目下入队（无在途去重），池 P 个 worker 以每刷 L 的速率消化——单轮只能完成 P×R/L 个，其余滞留队列。T3 第 k+1 轮扫描：滞留条目仍未被刷新（`loadNanos` 未更新）→ **再次入队**——同一 key 的重复刷新作业在队列中堆积，队列每轮净增 ≈N−P×R/L。T4 重复刷新 = 对已降级 DB 的后台 I/O 放大（同一 key 被连续重复查询），进一步抬升 L → 正反馈；积压队列（每作业一个 lambda + key 引用）随轮次线性增长，无上界、无指标、无 WARN。
- **现状**: 注释「the worker pool's size is the concurrency bound」声明的界只覆盖**并发度**（同时在途的 delegate 调用数），不覆盖**排队深度**——池大小不是积压上界。scan→pool 边界既无 per-key coalescing（对比：C8-004 修复建议的 mailbox coalescing 同型机制）也无积压预算/丢弃策略（对比：平台 `QueueOverflowPolicy` 语义空间）。读路径新鲜度由 TTL 兜底（条目超 TTL 后下一次访问同步重查，声明语义），故无读正确性违约——定 P2 而非 P1。
- **风险**: 持续慢 delegate 场景（恰是 C8 的过载邻域）下：内存随积压慢增长（F7 邻型）+ 后台刷新 I/O 对已降级依赖的正反馈放大（重试风暴邻型，C7 口径）；被放大的查询负载还会挤占同一 DB 上数据面/其他作业的容量。触发无竞态前提，纯速率条件（N/R > P/L 持续成立）。
- **建议**: ① per-key 在途去重：scan 侧维护 `Set<Object> inFlight`（任务完成后移除），在途 key 跳过本轮下发——任意慢度下队列深度 ≤ N 且同 key 至多一个在途作业；② 或改用有界队列 + 丢弃旧作业策略（`refreshPool` 换 `ThreadPoolExecutor(P, P, ..., LinkedBlockingQueue(N))` + Abort→scan 侧吞掉并记一次性 WARN），复用平台 `QueueOverflowPolicy` 语义；③ 补积压深度 gauge（`materializedSize` 已有先例形态）。①为优（改动最小、语义最准）。
- **失效面枚举**: 类内全部生产-消费边界逐一核查：① scan→refreshPool（本发现）；② 每记录同步 lookup→delegate（WI11 裁定 R5 声明的同步路径，阻塞即背压，✓ 声明边界）；③ 每记录 put→evict（有界于 `maxEntries` ✓，满载时 O(n)/miss 的热路径 CPU 成本属 C9 口径，本维不计）；④ close→在途刷新（`closed` 双检 + `shutdownNow` 中断 ✓）；⑤ close 竞态 REE（停机期窗口，无运行期后果，本轮盲区表已裁）。同族 grep：`nop-stream-core` main 源无其他「周期扫描 + 无界池下发」形态；平台 `IAsyncCache`/`CacheConfig`（容量/淘汰配置）未用于刷新排队——无现成设施直接覆盖该边界，①的自研去重为最小方案。可达性：main 源生产构造点为零（全仓构造点仅测试：`TestCachingTableLookup`、test 侧 `DimLookupEnrichFunction`），类为 main 源公开 API（WI11 能力类，javadoc 明示生产接线由 host function 的 WI17 wiring / bean destroy-method 承担）——与第 1 轮 C8-001 同属「休眠能力」口径，缺陷随任何生产接线激活。
- **信心水平**: 确定（无去重、无界队列、重复下发、loadNanos 仅成功刷新时更新均为代码直证；积压增长速率为速率条件下的确定性推演）
- **误报排除**: 不属「有界声明存在的缓存/队列」误报——`maxEntries` 界的是条目数不是刷新作业队列，并发度界不等于积压界（注释声明的界与实际失效维度错位，恰是缺陷本体）；不属「平台设施该用未用」误报——已核对平台无直接对应的 coalescing 设施；不与 C8-004 重复——那是 mailbox 控制面 timer-fire 的 coalescing 缺失（nop-stream-core 任务线程面），本条是 lookup 刷新池（connector 面），生产者/消费者/修复位点均不同；不属纯测试代码豁免——发现对象是 main 源公开能力类，口径沿用 C8-001 先例。
- **复核状态**: 已保留

---

## 三、覆盖范围说明

**本轮新精读文件**: `TaskExecutor`（全文）、`SubtaskTask`（状态机段）、`StreamExecutionEnvironment`（runLocal/部署计划段）、`DefaultDeploymentPlanProvider`（全文）、`GraphModelCheckpointExecutor`（executeWithCheckpoint/submitAndRun 构造段）、`SupervisionLoop`（轮询/失败检测/重启提交段）、`CachingTableLookup`（全文）、`TaskManager`（执行器矩阵 + saturatedCommitQueuePolicy 裁定注释段，L240-330）、`JdbcLeaderElector`（checkElection/restartElection 段）、`JobVertex`/`StreamGraphGenerator`/`StreamExecutionEnvironment`（默认并行度确认）。

**判「无发现」的关键复核结论**: commitExecutor CallerRuns 兜底的调用方身份已被 plan 369 F-16 显式裁定并计数（C8-011 同族候选排除）；commonPool 隐式异步面在 main 源为零；全部 18 个执行器/线程创建点中 16 处已被前 3 轮覆盖，剩余 2 处即本轮两个发现对象；JdbcLeaderElector 调度面属平台设施（按共享前缀口径不重审）；`CachingTableLookup` 的同步读路径为已声明边界、close 竞态为停机期窗口、O(n) eviction 属 C9 口径——均不立项。

# C8 背压、流控与过载行为 — nop-stream-core-runtime（深挖第 5 轮追加）

- **审计对象**: 同第 1 轮（`nop-stream/nop-stream-core/src/main/java`、`nop-stream/nop-stream-runtime/src/main/java`；`_` 前缀生成文件不在发现对象内）
- **盲审边界**: 本轮仅读取本维度第 1–4 轮合并文件（`C8-backpressure.md`）与提示词（`ai-dev/skills/concurrency-resource-audit-prompt.md` 共享前缀 + C8 正文）；未读其他审计维度文件、plans、bugs、lessons
- **方法**: 在前 4 轮边界清单（B1–B20）与盲区核查基础上，第 5 轮按指令做三类定向排查：① 失败信号可达性盲区——`RemoteInputChannel` 溢出路径的 EOS 哨兵在队列满时的命运与任务线程的感知链；② 消费侧盲区——`processInputGate` 每迭代批量与 mailbox drain 的关系（C8-004 的消费者侧对偶）；③ C8-013 同族收口——`submitAndRun`/`awaitCompletion` 全部调用点复核 + 两模块 main 源队列容器全量清点（`LinkedBlockingQueue`/`ArrayBlockingQueue`/`ConcurrentLinkedQueue`/`Deque`/`PriorityBlockingQueue`/`DelayQueue` 六路 grep）；④ 清点中浮出的未盘点配置面——`MemoryBudget`（部署计划声明的内存包络）运行时接线核查

---

## 深挖第 5 轮追加

## 一、第 5 轮盲区核查记录（无发现结论，供合并裁定去重）

| 排查点 | 结论 |
|---|---|
| `RemoteInputChannel` 溢出路径失败信号可达性（EOS 哨兵在队列满时被非阻塞 offer 丢弃？） | 信号链三层兜底：① 溢出时 `overflowError` 先置位、`finished=true`，哨兵 offer 失败仅触发 `flagOverflow`（此时 `overflowError` 已非 null，幂等）；② 任务线程 `read()`/`read(timeout)` 在 `take/poll` 前后各有一次 `checkChannelError()`（`RemoteInputChannel.java:284-294,311-318`），队列满意味着消费者活跃，下一次 read 入口必抛 typed；③ `close()`/decode 失败路径同型（`enqueueTerminalSentinel` + flag）。`captureInFlightData`/`injectElements` 对哨兵重放置逐次校验 offer（L431-439,466-494）——无静默丢失，无发现 |
| `processInputGate` 消费侧 mailbox 饥饿（C8-004 的消费者对偶：持续有数据时 timer/barrier mail 是否被饿） | 主循环每迭代恰好处理一个元素（`classifyAndDispatch` 单元素）后在 loop top `processAvailableMails()`（`StreamTaskInvokable.java:1101-1153`）——drain 频率 = 每记录一次，无批内无界段；SOURCE 侧 `collect()` 安全点同理。空闲迭代路径注释显式声明「idle task must still reach the mailbox drain」（L1126-1130）。无饥饿，无发现 |
| `TaskMailbox` 第二队列 `normalQueue` 的生产者盲区 | `Mail.Priority.NORMAL` 生产者仅 `Mail.normal` 工厂（第 1 轮 C8-004 失效面枚举第 ⑤ 项：main 源零调用方）；本轮全文复读 TaskMailbox 确认无其他 NORMAL 入口——`normalQueue` 为休眠结构，无新生产者，无发现 |
| 恢复重订阅/任务终态的旧订阅关闭配对（poller 任务泄漏候选） | 四条 invoke 路径（SOURCE/MIDDLE/SINK/SELF_CONTAINED）finally 均经 `closeChainAndGate`（`StreamTaskInvokable.java:778-783,904,962,1006,1065`）→ `inputGate.close()` → 逐 channel `close()` → `subscription.cancel()`；javadoc 显式命名泄漏危害（L730-736,770-777）。`PollingJdbcMessageService.Subscription.cancel()` 取消自身调度任务（L341-349）。配对完整，无发现 |
| C8-013 同族收口：`submitAndRun`/`awaitCompletion` 全部调用点 | `awaitCompletion()` 两重载定义于 `TaskExecutor.java:305/334`；无超时版唯一 main 调用点 `StreamExecutionEnvironment.java:574`（C8-013 已立项）；`submitAndRun` 三处调用（`GraphModelCheckpointExecutor.java:279,421,459`）+ 定义 L843，全部汇入 SupervisionLoop 路径（其 TaskExecutor 裸构造三点已在 C8-013 失效面枚举内）——无第三处漏网，无发现 |
| 两模块 main 源队列容器全量清点（六路 grep） | 命中 8 处：`TaskMailbox` ×2（C8-004）、`ResultPartition.queue`（B1/B2）、`TaskManager.commitQueue`（B10）、`RemoteInputChannel.queue`（B5）、`WebhookAlertChannel.pending`（B15）——全部已盘点；另有 `MessageSourceReader`/`DebeziumCdcSourceReader` 两个 `ArrayBlockingQueue` 属 nop-stream-connector 族（第 1 轮已声明不在本轮对象）。无未盘点队列，无发现 |
| 恢复重订阅 cursor=0 全量重放 × C8-003 无界账本的复合 | Item 14 声明语义（C8-011 失效面枚举已裁定「不同窗不重复计数」）；重放行的 epoch fencing 丢弃由 `RemoteInputChannel` 承担（stale 消息 debug 丢弃，类注释声明）；内存面已被 C8-002 覆盖——无新增 |

---

## 二、发现条目（接续第 4 轮序号）

### [nop-stream-core-runtime-C8-015] `MemoryBudget` 声明的内存包络零运行时消费：部署计划携带 64MB 预算（edgeQueues=19.2MB）但无任何执行者，实际边队列内存从不对照预算（C8-010/012 同族，plan 级变体）

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/flow/MemoryBudget.java:36-46`（预算定义与分配查询）、`execution/plan/DeploymentPlan.java:32,62,77`（字段携带与 getter）、`execution/DefaultDeploymentPlanProvider.java:59`、`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/DeploymentPlanGenerator.java:45,79`（三处硬编码接线）；对照声明面 `execution/buffer/IBufferPool.java:27-29`（显式不交互声明）
- **证据片段**:
  ```java
  // MemoryBudget.defaultLocalBudget — 每个部署计划都携带的 64MB 声明包络
  public static MemoryBudget defaultLocalBudget(long totalBytes) {
      Map<String, Long> allocations = new LinkedHashMap<>();
      allocations.put("stateBackend", (long) (totalBytes * 0.5));
      allocations.put("edgeQueues", (long) (totalBytes * 0.3));      // 19.2MB
      allocations.put("networkBuffers", (long) (totalBytes * 0.2));
      return new MemoryBudget(totalBytes, allocations);
  }
  public long getAllocation(String component) { ... }   // 全仓零调用方
  ```
  ```java
  // DeploymentPlan — 随计划持久化/传播，getter 无任何消费者（grep 可证）
  public MemoryBudget getMemoryBudget() { return memoryBudget; }
  // IBufferPool javadoc（L27-29）：
  // "This SPI deliberately does not interact with MemoryBudget.networkBuffers
  //  (byte-based); the two units are incomparable and no conversion is performed."
  ```
- **严重程度**: P3
- **并发场景**: 无运行期竞态——配置面 vs 行为面的一致性缺口（C8 步骤 4「过载时行为是否有文档声明且与实现一致」的包络声明侧延伸；C8-010 `receiveWindow`/`packetSize`、C8-012 远程 `queueCapacity` 的同族第三变体，层级从字段/transport 装配上升到部署计划）。
- **触发时序**: 理论风险，未具体化（非竞态）。`DefaultDeploymentPlanProvider.generateLocal`（LOCAL 路径）与 `DeploymentPlanGenerator.generateLocal/generateDistributed`（distributed 路径，`L45/L79`）在每个 DeploymentPlan 中硬编码 `defaultLocalBudget(64MB)` → 计划随作业提交/分发传播（`@DataBean` 可序列化）→ `getMemoryBudget()`/`getAllocation()` 全仓 grep 仅 getter 自身、零调用方 → 实际边队列内存 = Σ(各 partition `queueCapacity` × 元素实际尺寸)，元素尺寸无任何上界校验，从不与 `edgeQueues=19.2MB` 分配对照；`networkBuffers` 组件被 `IBufferPool` javadoc 显式裁定「单位不可比、不做转换」——即唯一被提及的组件也是声明不消费的。
- **现状**: 部署计划对每个作业声明了三组件内存包络（32MB state / 19.2MB 边队列 / 12.8MB 网络缓冲），其中「edgeQueues」在语义上正是本维度 B1/B2 盘点的背压缓冲内存——但该声明是纯行李：无构建期校验（partition 数 × queueCapacity 不对照预算）、无运行期执行（元素尺寸不计量）、无观测接线（C8-006 的指标缺口清单亦未覆盖它）。三处硬编码的 64MB 无任何配置通路可调。
- **风险**: 编程式使用者/运维以部署计划中的 budget 为过载水位依据（64MB 边队列内存）做出容量决策，而实际驻留可按元素尺寸任意放大（`LinkedBlockingQueue` 只界元素个数不界字节）——「声明包络」与「真实上界」之间无任何机制保证一致；与 C8-001（materialization store 完全在预算之外）、C8-002/003（后端物化无包络）叠加后，整个「内存有界降级」的声明面全部悬空。无直接正确性风险，故 P3。
- **建议**: 三选一：① 实现预算执行（构建期：Σ partition 容量 × 声明平均元素尺寸 ≤ `edgeQueues` 分配，超限 typed 拒绝；运行期需元素尺寸计量，工程量大，建议仅做构建期近似校验）；② 对齐 G27 处置先例（C8-010 建议②）：javadoc 显式声明 `MemoryBudget` 为保留/无效字段并标注出处，同时在 `DeploymentPlan` 构建路径对 budget 打一次性 WARN；③ 删除字段（需序列化兼容评估）。合并裁定时建议与 C8-010/C8-012 按「流控参数声明-实现契约缺口」同族相邻处理（同根因：G27 后流控参数族的无执行者状态；失效层递进：字段级 → transport 装配级 → 部署计划级），一次裁决统一处置。
- **失效面枚举**: `MemoryBudget` 三组件逐一核查：`networkBuffers`（`IBufferPool.java:27-29` 显式声明不交互——已声明边界）；`edgeQueues`（零消费，本发现主体）；`stateBackend`（零消费，同病——但 state 后端内存核算属 C5 口径，此处只登记其零消费事实）。`defaultLocalBudget` 全部调用点 3 处：`DefaultDeploymentPlanProvider.java:59`、`DeploymentPlanGenerator.java:45,79`——同一硬编码值重复三处且无单一权威常量（口径漂移隐患，随本发现一并处置）。`getMemoryBudget`/`getAllocation` 零调用方为 grep 可证（仅 getter 定义与 IBufferPool 注释命中）。C8-010/012 的失效面枚举当时只覆盖 `EdgeConfig` 四字段，未含 `MemoryBudget`——本条为该家族的补面，非重复计数。
- **信心水平**: 确定（零消费为 grep 可证事实；「声明包络与真实上界无机制关联」由 IBufferPool 显式不交互声明 + 队列只界个数不界字节直接可证）
- **误报排除**: 不属「有界声明存在的缓存/队列」误报——问题恰是声明了预算而无任何执行者，与 C8-010 的「声明了参数而无实现」同构；不属纯命名/风格问题——它是随每个作业的 DeploymentPlan 传播的显式包络声明，使用者可合理依赖；不与 C8-006 重复——006 是已实现读取方法缺指标接线，本条是整个预算无任何读取方。若 owner doc 已明文声明 `MemoryBudget` 为保留/无效（同 C8-010/012 的裁定出口），本条应降级/驳回（本轮盲审边界内无法核对该声明，复核阶段请对照 owner doc 裁定）。
- **复核状态**: 已保留

---

## 三、覆盖范围说明

**本轮新精读文件**: `RemoteInputChannel`（溢出/EOS/哨兵/capture/inject 全路径复读）、`TaskMailbox`（全文复读）、`StreamTaskInvokable`（processInputGate/classifyAndDispatch/closeChainAndGate/invoke 四路径 teardown 段）、`PollingJdbcMessageService`（Subscription 生命周期段 L190-349）、`SupervisionLoop`（rebuildConsumerInputGateWithReplay 段 L960-1099 + oldInvokable 生命周期段）、`MemoryBudget`（全文）、`DeploymentPlan`（全文）、`DefaultDeploymentPlanProvider`（全文）、`DeploymentPlanGenerator`（generateLocal/generateDistributed 段）、`IBufferPool`（javadoc 头）、`EdgeConfig`（全文复读）、`InputGate`（close 段）、`RunningTask`（close 触点 grep）。

**判「无发现」的关键复核结论**: `RemoteInputChannel` 溢出失败信号经「先置错 + 双检查点 read + 哨兵重放置校验」三层兜底，队列满时信号仍可达任务线程；`processInputGate` 每迭代单元素 + loop-top drain，消费侧无 mailbox 饥饿（C8-004 仅生产侧成立）；`TaskMailbox.normalQueue` 无 main 源生产者；订阅关闭在四条 invoke 路径 finally 闭环；C8-013 家族的 `submitAndRun`/`awaitCompletion` 调用点全部在其既失效面枚举内；main 源队列容器六路 grep 无未盘点边界（connector 族两个 reader 在审计对象外）；cursor=0 重放 × 无界账本复合已被 C8-002/003 + Item 14 声明覆盖。本轮唯一新发现为清点中浮出的 `MemoryBudget` 零消费（C8-015，C8-010/012 家族补面）。深挖收敛。

# C8 背压、流控与过载行为 — 维度复核结论（独立复核子代理，2026-10-14）

- **复核方式**: 盲审解除——完整重读本维度第 1–5 轮全部发现；逐条重读 live code（HEAD = `4d522b00ae`，2026-10-14 01:29，审计开始后无修复提交）；对照历史审计（`ai-dev/audits/2026-09/2026-09-26/27/29/30`、`2026-10-05/2026-10-06` 两轮 quality 审计及 adversarial review）、`ai-dev/plans/366/368/369/2338/2328`、`docs-for-ai/03-modules/nop-stream.md` 与 `nop-stream-user-guide.md`、`git log --follow`（各发现对象文件）。
- **关键时序事实**: `JobCoordinatorMain`/`TaskManagerMain`/`PollingJdbcMessageService` 于 `feb189859b`（WI1/plan 2328，2026-10-07）从 test scope 移入 main scope——晚于 r5/r6 审计（10-05/10-06），故历史审计「生产零接线」结论已失效，本轮各发现以 owner doc 运维手册（main-scope 生产入口）为准；runtime `TaskManager.taskExecutor` 有界化（CC-17，`547be576f8`，plan 2435，2026-10-11）只覆盖 runtime 侧，core `TaskExecutor` 未动。

## 维度复核结论
> 注：原「逐条判定」节，2026-10-14 closure audit 要求标准化为本标题；内容未改动。

| 编号 | 判定 | 复核理由（含现状归属） |
|---|---|---|
| C8-001 | 保留（P1） | live 复核成立：`enqueueWithBackpressure` 四象限（`ResultPartition.java:346-376`）与类注释失真不变式逐字核实；可达性=全仓仅测试调用 `setMaterializationEnabled`（休眠机制，发现内已如实登记）。现状归属：**部分已知**——store 无界增长已在先记录（r3 `01-correctness-findings.md:11` 引「A5 已记录 store 无界」），overflow-bypass 上游永不阻塞性质已见于 r4 `04-concurrency-resource.md:301`（不同发现语境）；「背压链路完全旁路 + BufferPool 不变式为假 + 活动运行期记录静默缺失」的完整定性为本轮新增。触发时序具体，维持 P1；若合并裁定以「生产接线为零」为判级前提，可与 C8-014 同口径降 P2（二选一，由合并裁定统一）。 |
| C8-002 | 保留（P1） | live 复核成立：`poll()` SELECT 无 LIMIT、`readRows` 全量物化（`PollingJdbcMessageService.java` Subscription.poll）；该类自 `feb189859b` 起 main-scope 且为 owner doc 运维手册权威启动路径组件。历史审计（r1–r6、adversarial）对 `PollingJdbcMessageService` 零命中——**新发现**。 |
| C8-003 | 保留（P1） | live 复核成立：send INSERT-only（L177-186），全仓唯一删除路径 `clearTopics` 为 test helper 且**零调用方**（比初审更强）；owner doc L298 仅声明「无 ChannelBacklogProbe 能力的通道诚实禁用门控」，未声明「launch DB 后端结构性不可用门控 + 表无清理」的组合后果。历史审计零命中——**新发现**。 |
| C8-004 | 保留（P2） | live 复核成立：driver `run()`（`ProcessingTimeServiceDriver.java:116-133`）每 tick 无 coalescing 判断投递 fire mail；`TaskMailbox` 双 `ConcurrentLinkedQueue` 无界无指标。定量口径已在发现内给出（受 taskTimeout/恢复上界约束）——**新发现**。 |
| C8-005 | 保留（P2) | live 复核成立：反序列化失败 warn + cursor 推进 + 永久跳过（poll L319-326），与 `RemoteInputChannel` 家族「never silently lose a record」契约矛盾核实。触发依赖存储损坏/版本漂移（非默认条件），P2 恰当——**新发现**。 |
| C8-006 | 保留（P3） | live 复核成立：`getGlobalUsage` 全仓仅测试调用、`resolvePartitionCapacity` ≤0 静默回落 1024、`commitQueueCapacity<=0` typed 拒绝先例核实（`TaskManager.java:225-228`）、mailbox 深度/webhook 丢弃计数/materialization size 均无接线。观测面缺口非竞态（发现自身已标「理论风险，未具体化」），P3 恰当——**新发现**。 |
| C8-007 | 保留（P3） | live 复核成立：`delivered` 无界 `CopyOnWriteArrayList`（L67,180），注释自定位 test observability 无生产/测试区分。邻接面澄清：r5 [CC-06]（drainLoop 单异常死亡）已由 `e600de4058`（plan 2314）修复，与本条（缺上界）不同面；`pending` 队列容量已从 r5 时的 100 调至 256。历史审计未记录 delivered 无界——**新发现**。 |
| C8-008 | 保留（P2） | live 复核成立：`handleSubmit` `readAllBytes`（`StreamOpsHttpServer.java:395`）无 Content-Length 预检/截断/413；`StreamOpsConfig` 全部配置键（enabled/port/bindAddress/metricsEnabled/authToken）无 body 上限。可达性修正登记：ops 端点默认关闭（`opsHttpPort` 显式启用）、F-09b 鉴权限定攻击者面——发现内已如实陈述，P2 维持——**新发现**。 |
| C8-009 | 保留（P2） | live 复核成立：两个 duplicate 分支静默（containsKey → WARN+release+return；putIfAbsent 竞态 → release+return，均无 `reportAssignmentFailure`）；两处 submit catch（`receiveAssignment` L594-600 / `deployTask` L780-790）上报但不 release/remove；契约 javadoc（L860-867）逐字核实。CC-17（`547be576f8`）只覆盖上报通道 typed 化，未覆盖 duplicate 分支与清理——**新发现**（CC-17 修复后的残留面）。 |
| C8-010 | 降级（P3 → 已裁定边界，不独立立项） | 复核对照 owner 家族文档推翻立项前提：`nop-stream-user-guide.md:224/:330`（WI14/plan 2360 裁定）已显式记录「边级流控四属性维持 typed 拒绝 + **`receiveWindow`/`packetSize` 全仓零行为消费点（接线即空壳）** + 登记引擎级 successor」——属发现自身预置的「owner 已声明则降级/驳回」出口。live 核实残余缺口仅 Java API 编程式路径（`JobEdge.setEdgeConfig` ← `DeploymentPlan.edgeConfigs`，仓库内生产构建只放 `defaultConfig()`）未单独披露。并入 C8-012/015 流控参数声明-实现契约家族统一处置。 |
| C8-011 | 保留（P1） | live 复核成立：2 线程共享池 + 每订阅 `scheduleWithFixedDelay` + 单 poll 内联串行派发（慢进度每行等满 10s 且不触发 typed 失败）；`RemoteInputChannel.java:665-673` 裁定注释逐字核实——其声明的界只覆盖「零进度单行」情形；`DataPlaneMessageServiceAdapter` javadoc「topic-addressed, disjoint topics」（L30）证实隔离仅在 topic 层。历史审计对该面零覆盖——**新发现**（bounded-offer 裁定注释证明危害族已知，慢进度批量占用与控制面饥饿未被裁定）。 |
| C8-012 | 保留（P3） | live 复核成立：`DEFAULT_CHANNEL_QUEUE_CAPACITY=1024` 硬编码（`RemoteGraphExecutionPlanBuilder.java:69,251`），`getQueueCapacity` 全文件仅 equals（L543）；owner doc/user-guide 未声明「远程形态忽略容量」。WI14 successor（消费端补齐）覆盖方向但不覆盖本条跨 transport 分裂本体——**新发现**（家族内新变体，修正 C8-010 失效面枚举口径成立）。 |
| C8-013 | 保留（P1） | live 复核成立：core `TaskExecutor` `newFixedThreadPool` 无界队列（L116-117）、三个 main 构造点裸 `new TaskExecutor()`、`runLocal` 提交后无超时 `awaitCompletion()`（L574→L305）、`SubtaskTask` CREATED→SCHEDULED 仅在 `run()` 内（L86）、`allTasksCompletedOrCanceled`（`SupervisionLoop.java:425-433`）对 CREATED 永假——检测面三层全空核实。**现状归属：已知未修复**——r5 审计（`2026-10-05.../04-concurrency-resource.md` [CC-05]，P2）已完整记录同一死锁机制与同族文件，live 无修复提交（`547be576f8` 只修 runtime 侧 taskExecutor）。严重程度分歧：r5 判 P2、本轮判 P1（确定性静默永久挂死 + 零可观测），复核倾向 P1，由合并裁定统一；本轮补充的 CREATED 不可见/无超时等待调用点为 r5 未覆盖的检测面证据。 |
| C8-014 | 保留（P2） | live 复核成立：`scanAndDispatch` 无 per-key 在途去重、`refreshPool` = `newFixedThreadPool`（内部队列无界）；代码注释「the worker pool's size is the concurrency bound (review F-2)」所指裁定经查为 plan 2338（wi11-dim-lookup-materialization）审查 F-2 的执行模型钉定——**该裁定只钉定并发度界（扫描/执行分离 + 池大小即 delegate 并发硬上界），未裁定排队深度/在途去重**，故本条非已声明边界。可达性=休眠能力（main 源零构造点，WI17 M-4 产品化宿主 out-of-scope），P2 维持——**新发现**。 |
| C8-015 | 保留（P3） | live 复核成立：`getAllocation`/`getMemoryBudget` main 源零调用方、`defaultLocalBudget(64MB)` 三处硬编码（`DefaultDeploymentPlanProvider.java:59`、`DeploymentPlanGenerator.java:45,79`）、`IBufferPool` 显式不交互声明核实。**现状归属：部分已知**——r6 审计（`2026-10-06.../07-readability-structure.md` RD-03 死访问器清单 #12）已登记 `DeploymentPlan.getMemoryBudget` 为死 getter（P3 死代码口径）；「声明包络无任何执行者」的契约缺口定性与三处硬编码无配置通路为本轮新增。与 C8-010/012 同族合并裁决。 |

## 最终保留项

| 编号 | 最终严重程度 | 文件路径 | 一句话摘要 | 现状标注 |
|---|---|---|---|---|
| C8-001 | P1 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/ResultPartition.java | materialization 边 overflow-bypass 使队列背压失效：store 无界增长 + 活动运行期记录静默不可见，类注释不变式为假 | 部分已知未修复（store 无界见 r3 引 A5、bypass 不阻塞见 r4:301；完整定性本轮新增） |
| C8-002 | P1 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/launch/PollingJdbcMessageService.java | poll 无 LIMIT 全量物化积压，消费滞后时单次 poll 数 GB 堆分配可达 OOM | 新发现 |
| C8-003 | P1 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/launch/PollingJdbcMessageService.java | `nop_stream_msg_queue` INSERT-only 无界账本（唯一删除路径零调用方）+ launch 后端不实现 ChannelBacklogProbe 使 WI6 门控恒不可用 | 新发现 |
| C8-004 | P2 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/ProcessingTimeServiceDriver.java | timer fire mail 无 coalescing：背压阻塞期 mailbox ~10 条/s 无界积累，解除后陈旧 mail 风暴 | 新发现 |
| C8-005 | P2 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/launch/PollingJdbcMessageService.java | 消费侧行反序列化失败 warn+skip 永久跳过，任务对数据面静默缺记录零感知 | 新发现 |
| C8-006 | P3 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/GraphExecutionPlan.java | 过载可观测四缺口（BufferPool 用量/mailbox 深度/告警丢弃/mat store 大小）+ queueCapacity≤0 静默回落 | 新发现 |
| C8-007 | P3 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/alert/WebhookAlertChannel.java | `delivered` 无界 CopyOnWriteArrayList 随成功投递单调增长（test observability 字段无生产区分） | 新发现 |
| C8-008 | P2 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/ops/StreamOpsHttpServer.java | `/jobs` 提交端点 readAllBytes 无上限，单次超大 POST 即可打出堆峰值/占满 4 线程池 | 新发现 |
| C8-009 | P2 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java | duplicate 分配静默吞没（无 FAILED 上报，违反单向 RPC 契约）+ 两处 submit 拒绝不清理许可/登记（zombie） | 新发现（CC-17 修复后残留面） |
| C8-010 | P3（降级：已裁定边界） | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/flow/EdgeConfig.java | receiveWindow/packetSize 零消费死配置 | 已裁定（WI14/plan 2360，user-guide:224/:330 显式记录零消费点 + successor 登记）；残余 Java API 披露缺口并入 C8-012/015 家族 |
| C8-011 | P1 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/launch/PollingJdbcMessageService.java | launch 共享 2 线程 poller：慢进度数据面积压（每行≤10s×无界批量）垄断派发线程，控制面（barrier/deploy/cancel）无界期饥饿 | 新发现（既有 bounded-offer 裁定仅覆盖零进度单行情形） |
| C8-012 | P3 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteGraphExecutionPlanBuilder.java | queueCapacity 在远程 transport 被硬编码 1024 静默忽略，跨 transport 流控语义分裂 | 新发现（WI14 successor 覆盖方向，分裂本体未记录） |
| C8-013 | P1 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/task/TaskExecutor.java | LOCAL 模式 core TaskExecutor 无界队列：Σsubtask>核数时下游永排队+上游背压互锁，整作业静默永久挂死且三层检测全盲 | 已知未修复（r5 [CC-05] 2026-10-05 同机制 P2；本轮补充 CREATED 不可见/无超时等待检测面证据；severity 分歧 P1/P2 待合并裁定统一） |
| C8-014 | P2 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/connector/lookup/CachingTableLookup.java | 后台刷新边界无 per-key 在途去重、无队列上界：慢 delegate 下重复刷新 I/O 放大正反馈 + 积压无界（休眠能力类） | 新发现（plan 2338 审查 F-2 只钉定并发度界，排队深度缺口未被裁定） |
| C8-015 | P3 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/flow/MemoryBudget.java | MemoryBudget 声明的 64MB 内存包络零运行时消费（getAllocation/getMemoryBudget 零调用方，三处硬编码无配置通路） | 部分已知（r6 RD-03 #12 已登记 getMemoryBudget 死访问器；包络无执行者契约定性本轮新增） |

**复核统计**: 保留 14 / 降级 1（C8-010，已裁定边界）/ 驳回 0。保留项中 P1×5（C8-001/002/003/011/013）、P2×5（C8-004/005/008/009/014）、P3×4（C8-006/007/012/015，含降级项 C8-010）。现状标注：新发现 11、已知未修复 1（C8-013 → r5 [CC-05]）、部分已知 2（C8-001、C8-015）、已修复 0。另注：历史审计中与本维度相邻的两项已修复事实——r5 [CC-06] webhook drainLoop 包容（`e600de4058`）、runtime taskExecutor 有界化 CC-17（`547be576f8`）——均不影响本轮任一保留项的成立性。
