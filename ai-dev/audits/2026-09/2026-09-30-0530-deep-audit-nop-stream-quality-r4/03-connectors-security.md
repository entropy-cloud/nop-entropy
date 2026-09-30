# 03 连接器与安全深度审计（connectors × 5 + SQL/凭证/反序列化面）

> 轮次：nop-stream 深度审计 R4（编号前缀 R5-CON，承接 R3 报告的 R4-XXX 序列）
> 审计对象 HEAD：`66eaa9ae91`（2026-09-30）
> 仓库根：`/Users/abc/app/nop-entropy-wt/nop-entropy-master`（下文路径均相对仓库根）
> 范围：`nop-stream-connector`（通用 message source/sink + file connector）、`nop-stream-connector-jdbc`、`nop-stream-connector-batch`、`nop-stream-connector-debezium`、连接器赖以运行的 `nop-message-kafka/-pulsar/-debezium` codec、`nop-stream-runtime` 的 `JdbcCheckpointStorage`/`CheckpointSerDe`（SQL 构造与 checkpoint 反序列化属标准检查项）。
> 方法：逐文件通读全部 main 源码（connector 五模块 main 集合计 30 文件 + nop-message 相关 12 文件 + CheckpointSerDe/JdbcClusterRegistry/TwoPhaseCommitSinkFunction/IMessageSender/FutureHelper/escapeSQLName 交叉验证），对照 2026-05 各轮、2026-09-26 R1（04-concurrency-resources-connectors.md）、2026-09-27 R2、2026-09-29 R3 的既有结论去重。
> 历史修复核验：**B6'（plan 366）已验证修复完整**（见"历史修复核验"节）；**R4-P2 文件源缓冲化已验证行为保持**（同节）；R1 的 6 项 P3 有 6 项仍开放（见"遗留登记"节）。

## 结论摘要

共 **11 项新发现：P0×0、P1×3、P2×2、P3×6**。

- **SQL 注入（标准检查项）：无成立点。** JDBC sink 标识符全部经 `IDialect.escapeSQLName`（quote-dup-escape，见正向确认 A1），值全部参数化；checkpoint storage / cluster registry 表名为编译期常量；upsert SQL 文本仅由常量列名数组拼出。给出了利用路径推演，结论为不可利用（A1）。
- **白名单双向断言（标准检查项）：未发现"未命中即透传"回退。** 4 处白名单/校验点全部 fail-fast 抛错（A2）。
- **SSRF/主机规范化（标准检查项）：不适用。** 连接器子系统没有任何 URL/主机白名单校验逻辑（kafka/pulsar/debezium 目标均为部署期可信配置），无绕过面可言。
- **P1 集中在两个主题**：① JDBC 2PC 幂等账本键缺作业命名空间（跨 sink/跨作业共库时**静默跳写=数据丢失**，R5-CON-01）；② Debezium 路径的双通道静默失败（collect 异常吞掉 R5-CON-02、引擎死亡不上抛 R5-CON-03）——后者与 2026-05 已修的 P1-9（MessageSourceFunction 吞错误伪装 EOS）同族，但修复未覆盖到 debezium 路径。

---

## 发现明细

### [R5-CON-01] JDBC 2PC 幂等账本键 (epoch_id, subtask_id) 缺作业/管道命名空间——共库多 sink 场景静默跳写（数据丢失）

- **文件**: `nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java:299-313`（guard 判定）、`:537-541`（guard SQL）、`:408-421`（DDL，仅 3 列）、`:78`（默认表名常量）
- **严重程度**: P1（共库部署条件下接近 P0：exactly-once 机制反向成为静默丢数据的执行者）
- **证据片段**:
  ```java
  // :299-313  commit()
  // Idempotent guard: check ledger first (keyed by epoch + subtask so parallel
  // subtask copies committing the same epoch never collide)
  if (ledgerExists(connection, checkpointId, subtaskIndex)) {
      LOG.info("Epoch {} already recorded in ledger — skipping data write (idempotent re-commit)",
              checkpointId);
      connection.commit();
      committed = true;
      getPendingCommits().remove(checkpointId);
      return;
  }
  ```
  ```java
  // :537-541  guard 键只有两个维度
  private String buildLedgerExistsSql(IDialect d) {
      return "SELECT 1 FROM " + d.escapeSQLName(ledgerTableName)
              + " WHERE " + d.escapeSQLName(LEDGER_EPOCH_COL) + " = ?"
              + " AND " + d.escapeSQLName(LEDGER_SUBTASK_COL) + " = ?";
  }
  ```
  ```java
  // :408-419  账本表 DDL 无 job/pipeline/table 命名空间列
  sb.append(d.escapeSQLName(LEDGER_EPOCH_COL)).append(" BIGINT NOT NULL, ");
  sb.append(d.escapeSQLName(LEDGER_SUBTASK_COL)).append(" INT NOT NULL, ");
  sb.append(d.escapeSQLName(LEDGER_TIMESTAMP_COL)).append(" TIMESTAMP, ");
  ```
  `:78` `private static final String DEFAULT_LEDGER_TABLE = "stream_epoch_ledger";`
- **现状**: 幂等提交 guard 的唯一键是 `(epoch_id, subtask_id)`，不含 jobId、pipelineId 或目标表名。`epoch` 序号按作业从 0 递增、`subtaskIndex` 各 sink 支路独立从 0 编号，而默认账本表名是全局同一个 `stream_epoch_ledger`。
- **可利用/触发场景推演**（非 SQL 注入，为配置级触发）:
  1. **同作业双写库支路（最自然、几乎必现）**：一个 DAG 内配置两条 `jdbc-2pc` sink 支路（如 `orders → orders_a` 表与 `orders → orders_b` 表），同一 querySpace 数据库，均用默认账本表。两支路 subtaskIndex 均为 0，epoch 编号同步递增。支路 A 先 commit epoch 1 → 写入账本行 `(1,0)`；支路 B commit epoch 1 → `ledgerExists(1,0)=true` → 走 302-312 行分支，**整批数据不写、仅打一行 INFO 日志、pendingCommits 移除**。B 支路此后每个 epoch 都被 A 的账本行挡掉——目标表 `orders_b` 永久缺数，作业全程无任何 ERROR。
  2. **跨作业共库**：作业 A（写 `t_a`）与作业 B（写 `t_b`）共用同一数据库。epoch 计数从同一起点递增，`subtask 0` 的账本行 `(N,0)` 互相碰撞，后提交方静默跳写。
  3. **HA 双活/脑裂**：同一作业两个实例并发运行（fencing 失效窗口内），实例 2 的 commit 被实例 1 的账本行挡掉——该场景下 skip 方向恰好"正确"（数据已由实例 1 写入同一目标表），是唯一无害变体。
- **风险**: 静默数据丢失（P0 级后果）；观测面仅一条 INFO 日志。反向变体：账本行先于数据写入被外部清理（手工清表只清了目标表）会造成重复写入。
- **建议**: 账本键扩为 `(job_id, pipeline_id[, sink_name], epoch_id, subtask_id)`，或在 DDL/guard 中引入 per-sink 的派生账本表名（如 `stream_epoch_ledger_{jobId}_{sinkVertex}`）；至少把 guard 命中日志从 INFO 升级为带作业上下文的 WARN 并提供可配置的 strict 模式（命中即抛错）。
- **信心水平**: 高（代码路径单一、guard 分支行为确定；场景 1 为纯配置即可触发）。
- **误报排除**: ① 并行子任务隔离不受影响——`(epoch, subtask)` 在**单一 sink 支路内部**确实无碰撞（`copyForSubtask` + `TestJdbcTwoPhaseCommitSinkParallelIsolation` 覆盖的是这一场景，不与跨支路场景矛盾）；② 事务名冲突检查项（重点 6）在单支路内由 `(epoch, subtask)` 正确消解，本发现是**跨支路/跨作业**维度，非重复报告；③ R1/R2/R3 的 connector 发现清单（04-concurrency-resources-connectors.md、r3 summary）无此条。

### [R5-CON-02] DebeziumMessageSource.dispatchEvent 吞掉 collect 异常——CDC 事件静默丢失且 offset 照常推进

- **文件**: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/DebeziumMessageSource.java:155-163`；受害链路 `nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceFunction.java:192`（`source.subscribe(ctx::collect)`）
- **严重程度**: P1
- **证据片段**:
  ```java
  // DebeziumMessageSource.java:155-163
  private void dispatchEvent(ChangeEvent event) {
      for (Consumer<ChangeEvent> consumer : subscriptions.keySet()) {
          try {
              consumer.accept(event);
          } catch (Exception e) {
              LOG.error("Error processing CDC event: {}", event, e);   // 吞掉，返回正常
          }
      }
  }
  ```
  ```java
  // DebeziumCdcSourceFunction.java:190-197  订阅者就是 ctx::collect
  source = createMessageSource(effectiveEngineConfig(), offsetStore);
  try {
      subscription = source.subscribe(ctx::collect);
  ```
- **现状**: 嵌入式 Debezium 引擎的 notifying handler 返回即视为事件处理成功并推进 offset（DebeziumEngine 标准语义）。本项目的 handler 链在最后一级把 `ctx.collect` 的任何异常（下游关闭、barrier 超时、反压超限等）catch 后仅打日志。结果：事件丢弃、offset 照常推进，下一次 checkpoint 把推进后的 offset 持久化——重启也不再重放该事件。
- **风险**: **永久静默数据丢失**，且作业表现为健康（不 FAILED、不重放）。这与 2026-05 已修复的 P1-9（MessageSourceFunction `onMessage` 吞 collect 失败伪装 EOS，见 MessageSourceFunction.java:159-171 现存修复注释 "do NOT swallow the failure"）完全同族——修复只落在了 message source 路径，未覆盖 debezium 路径。同时违反该 source 声明的 `REPLAYABLE` 语义。
- **建议**: 与 P1-9 同构：dispatchEvent 不吞异常，让异常沿 `handleDebeziumEvent` → `DebeziumEngine.run()` 传播（引擎会以 CompletionCallback(success=false) 终止），并由 R5-CON-03 的接线把失败转化为任务失败；或在 DebeziumCdcSourceFunction 层用 pendingError 捕获-重抛模式包装 `ctx::collect`。
- **信心水平**: 高（代码路径确定；offset 推进时机为 Debezium 引擎标准行为，non-goal 范围外无重试缓冲）。
- **误报排除**: ① 不算"日志即观测"——LOG.error 不触发任务失败/重放，语义仍是丢失；② `subscribeTable/subscribeOperation` 的过滤 lambda（:89-114）吞事件是按设计过滤（不调用 action 不算异常路径），与本条无关。

### [R5-CON-03] Debezium 引擎线程死亡不上抛——任务存活、CDC 静默断流、checkpoint 空转

- **文件**: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/DebeziumEngineWrapper.java:91-102`（run 异常仅日志）、`:152-162`（CompletionCallback 失败仅日志）；`nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceFunction.java:199-203`（等待循环只看自身 latch）
- **严重程度**: P1
- **证据片段**:
  ```java
  // DebeziumEngineWrapper.java:91-102
  GlobalExecutors.globalWorker().execute(() -> {
      try {
          running.set(true);
          engine.run();
      } catch (Exception e) {
          LOG.error("Debezium engine error: {}", config.getName(), e);   // 无上抛、无回调
      } finally {
          running.set(false);
          LOG.info("Debezium engine stopped: {}", config.getName());
      }
  });
  ```
  ```java
  // DebeziumEngineWrapper.java:152-162
  private class EngineCompletionCallback implements DebeziumEngine.CompletionCallback {
      @Override
      public void handle(boolean success, String message, Throwable error) {
          if (!success) {
              LOG.error("Debezium engine completed with error: {} - {}", config.getName(), message, error);
          } ...
  ```
  ```java
  // DebeziumCdcSourceFunction.java:199-203   completionLatch 仅被 cancel/truncateForDrain countDown
  while (running && !draining) {
      if (completionLatch.await(1, TimeUnit.SECONDS)) {
          break;
      }
  }
  ```
- **现状**: 引擎运行在全局 worker 线程；引擎抛异常或以失败完成（连接器 crash、schema 失配、handler 抛错等）只产生两条 ERROR 日志。`DebeziumCdcSourceFunction.run()` 的等待循环对引擎死活零感知：`completionLatch` 只在 `cancel()`/`truncateForDrain()` 被触发。引擎死亡后：任务保持 RUNNING、checkpoint 周期照常成功（offset 无变化）、无 watermark/数据流出——**静默永久断流**，监督/心跳体系（R4-N3 修复所依赖的 finished 心跳过滤）不会介入。
- **风险**: CDC 管道最典型的生产故障模式（数据库连接器异常终止）从"任务失败→自动重启→从 checkpoint 重放"退化为"作业假活"。与 R3 P0 级"静默悬挂"问题（R4-N1/N2）同类严重度，故列 P1。
- **建议**: `DebeziumMessageSource` 增加 failure listener（或让 `DebeziumEngineWrapper.start` 接受 `Consumer<Throwable>`）；`DebeziumCdcSourceFunction` 订阅之：回调中设置 `pendingError` 并 `completionLatch.countDown()`，`run()` 退出时重抛（同 P1-9 修复模式）。CompletionCallback(success=false) 走同一通路。
- **信心水平**: 高（三处代码逐一核实，无其他把引擎失败接回任务生命周期的通路——`stop()` 仅由 cancel 侧调用）。
- **误报排除**: ① Debezium 引擎内部对瞬时错误有自己的重试（连接重试等），本条针对的是引擎**终态**死亡（run() 抛出/完成回调 success=false），不与内部重试重复；② `running` 字段确有翻转，但没有任何读取方把它连到任务失败路径（`isRunning()` 在 connector 侧无调用）。

### [R5-CON-04] MessageSourceFunction 取消/订阅竞态 + failed 退出路径不退订——消息订阅泄漏

- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/MessageSourceFunction.java:123-141`（run 未检查 running 即 subscribe）、`:178-191`（循环退出后任何路径都不退订）、`:193-202`（cancel 读 subscription）
- **严重程度**: P2
- **证据片段**:
  ```java
  public void run(final SourceContext<T> ctx) throws Exception {
      this.pendingError = null;
      this.failed = false;
      this.running = true;                       // (1) 复位
      this.shutdownLatch = new CountDownLatch(1);
      String effectiveTopic = getEffectiveTopic();
      subscription = messageService.subscribe(effectiveTopic, ...);  // (2) 无条件订阅，未检查 running
      while (running && !failed) {               // (3) cancel 若发生在 (1)(2) 之间 → 立即退出
          shutdownLatch.await(1, TimeUnit.SECONDS);
      }
      if (pendingError != null) { throw ...; }   // (4) failed 退出：subscription 仍活跃，无人取消
  }
  public void cancel() {
      running = false;
      ...
      if (subscription != null) { subscription.cancel(); }   // (5) 读到的是 null/旧值则什么都不取消
  }
  ```
- **现状**: 两条泄漏通路。① **取消窗口竞态**：runtime 在 run() 进入 (1) 之后、(2) 赋值完成之前调用 cancel()——cancel 读到旧值/null，run 仍无条件创建订阅，随后 (3) 立即退出、run 正常返回，runtime 不会再次调用 cancel → 新订阅永久泄漏（Kafka 侧为 consumer 线程 + group membership，`KafkaConsumeTask` 持续 poll 并把消息投给已终结的 ctx）。② **failed 退出路径**：onMessage 类型不匹配/collect 失败置 `failed=true`（P1-9 修复使 run 抛异常），但订阅未被取消；若 runtime 的失败清理不调用 cancel（或 cancel 恰逢窗口 ①），旧订阅随 run() 再入被字段覆盖后彻底失联。
- **风险**: 每次"取消落在订阅窗口"或"类型不匹配风暴"泄漏一个消费线程 + 消费者组成员身份；泄漏订阅继续投递消息到死 ctx（`KafkaConsumeTask.pollAndConsume` 中 `consumerFn.onMessage` 再失败则 seek 重投 → 无限空转）。
- **建议**: run() 在 subscribe 前检查 `running`；把 `IMessageSubscription` 局部变量化，run() 的 finally 中若 `running==false || failed` 则取消**本次**创建的订阅（局部引用不受字段覆盖影响）。
- **信心水平**: 中高（窗口 ① 为经典 check-then-act 竞态，代码确定存在；实际触发取决于 runtime 取消时序——R1 审计已确认 cancel 路径存在性，未审计该窗口）。需要补充确认 StreamTaskInvokable 失败/取消是否保证恰好一次 cancel 且在 run 返回后不再补调。
- **误报排除**: ① 顶部 reset 注释所修的是"region 重启后 run 再入"场景（running 复位），与本条（cancel 与 subscribe 的交错）是不同缺陷；② KafkaMessageSubscription.cancel 自身有 cancelled 幂等保护，不构成重复取消风险，也不抵消"从未被调用"的问题。

### [R5-CON-05] batch-loader 声明 PARALLEL 但无分片语义——parallelism>1 时全量数据被每副本重复投递

- **文件**: `nop-stream/nop-stream-connector-batch/src/main/java/io/nop/stream/connector/batch/BatchLoaderSourceConnectorFactory.java:34-38`（`.parallelism(ConnectorParallelism.PARALLEL)`）、`nop-stream/nop-stream-connector-batch/src/main/java/io/nop/stream/connector/batch/BatchLoaderSourceFunction.java:96-124`（run 循环无任何分片过滤）
- **严重程度**: P2
- **证据片段**:
  ```java
  // BatchLoaderSourceConnectorFactory.java:34-38
  private static final ConnectorCapabilityDescriptor DESCRIPTOR = ConnectorCapabilityDescriptor
          .source(TYPE_NAME, BatchLoaderSourceFunction.class.getName())
          .sourceConsistency(SourceConsistencyCapability.AT_LEAST_ONCE)
          .parallelism(ConnectorParallelism.PARALLEL)          // ← 声明可并行
          .recoverySemantic(ConnectorRecoverySemantic.OFFSET_CHECKPOINT)
  ```
  ```java
  // BatchLoaderSourceFunction.java:96-123   每个副本从 offset 处遍历【同一份】loader 全量数据
  while (running) {
      List<S> batch = loader.load(batchSize, chunkContext);
      if (batch == null || batch.isEmpty()) { ...fail-fast... break; }
      for (S item : batch) {
          if (!running) { return; }
          if (skipRemaining > 0) { skipRemaining--; skipped++; continue; }
          ctx.collect(item);
          currentOffset++;
      }
  }
  ```
- **现状**: 工厂能力描述声明 `PARALLEL`，但函数没有任何按 subtaskIndex 分片/取模过滤的逻辑（对比 MessageSourceFunction 的 `topic + "-" + subtaskIndex` 分区约定，MessageSourceFunction.java:114-119）。parallelism>1 时每个 subtask 副本独立遍历**同一** loader 的全量数据：每条记录经不同上游子任务路由到不同下游分区 → 下游收到 N 份完整数据集（N=parallelism），且各自 offset 独立 checkpoint、恢复后各自续读——重复是**稳定的**而非瞬时的。
- **风险**: 用户按能力描述配置 parallelism>1（合法输入）即触发 N 倍重复数据（下游若做聚合/写库则 N 倍结果污染）。与 R5-CON-01 同属"能力描述与实现不符"类，但后果是重复而非丢失。
- **建议**: 二选一：① 描述改为 `NON_PARALLEL`（与实现一致，代价是吞吐受限）；② 在 run 中加 `itemIndex % totalParallelism == subtaskIndex` 分片过滤并把分片维度纳入 offset 语义（`offset = emittedIndex * parallelism + subtaskIndex` 或恢复期按取模 skip）。
- **信心水平**: 高（代码确定；"重复到达下游"依赖标准边路由语义——不同上游子任务的输出分发到下游不同子任务，属平台既定路由模型）。
- **误报排除**: ① 若下游以 keyBy/hash 聚合，N 份相同记录同 key 路由到同一子任务仍产生 N 次累加——不存在"hash 去重"豁免；② batch-consumer sink 的 PARALLEL 是 sink 侧（每副本消费自己的上游分片），不适用本条。

### [R5-CON-06] BatchConsumerSinkFunction.close() 把首条记录全文写入 ERROR 日志——潜在敏感数据落日志

- **文件**: `nop-stream/nop-stream-connector-batch/src/main/java/io/nop/stream/connector/batch/BatchConsumerSinkFunction.java:124-127`
- **严重程度**: P3
- **证据片段**:
  ```java
  } catch (Exception flushErr) {
      LOG.error("Flush failed in close() with buffer size={}, first record summary={}",
              buffer.size(),
              buffer.isEmpty() ? "<empty>" : String.valueOf(buffer.get(0)));
      flushError = flushErr;
  }
  ```
- **现状**: 日志字段名为 "first record **summary**"，但 `String.valueOf(buffer.get(0))` 输出的是记录的完整 `toString()`——对典型 DTO 是全字段（可能含手机号、身份证、账号等业务敏感值）。
- **风险**: 关闭路径的 flush 失败（常见于目标端故障收尾场景）把缓冲区首条业务记录明文写入日志体系，违反最小化日志原则。
- **建议**: 输出类型 + 长度摘要（`record.getClass().getName() + "#" + len`），或提供可插拔的 redaction 钩子。
- **信心水平**: 高。
- **误报排除**: 仅 close() 路径（fail-fast 异常路径 flush() 不打印内容，:102-105 无记录内容输出）；不是每次失败都打印，但 close-fail 恰是数据可能丢失的高敏场景。

### [R5-CON-07] FileSourceReader 单行读取缓冲无上限——无换行符大文件可触发 OOM

- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileSourceReader.java:236-269`
- **严重程度**: P3
- **证据片段**:
  ```java
  private String readNextLine() throws IOException {
      long cap = activeSplit.getEndOffset() - activeSplit.getStartOffset();
      ByteArrayOutputStream out = new ByteArrayOutputStream();     // 无上限
      while (activeBytesConsumed < cap) {
          int b = readBuffered();
          ...
          out.write(b);                                            // 逐字节累积直到 \n / cap
      }
      return out.size() == 0 ? null : out.toString(StandardCharsets.UTF_8);
  }
  ```
- **现状**: split 的 cap 上界是**整个文件大小**（v1 一文件一 split，FileSplitEnumerator.java:80-88），单行缓冲只受 cap 限制。一个数 GB 无换行的输入文件（或被截断/损坏的日志文件）会在单次 `readNextLine` 中把整个文件累积进堆内存。
- **风险**: 单条脏输入即可打爆 reader 堆内存（并行 subtask 各自累积，放大 N 倍）。
- **建议**: 增加 maxLineLength 配置（默认如 16MB），超限抛 typed 错误（fail-fast 而非截断）。
- **信心水平**: 高。
- **误报排除**: R4-P2 缓冲化（8KB readBuf）只优化**读**路径的字节搬运，与本条的**行聚合**上限无关，不属重复报告。

### [R5-CON-08] JdbcCheckpointStorage.isDuplicateKeyException 依赖异常消息/类名嗅探——误判方向为"把真错误降级为 UPDATE 重试"

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/JdbcCheckpointStorage.java:913-928`
- **严重程度**: P3
- **证据片段**:
  ```java
  private static boolean isDuplicateKeyException(Exception e) {
      Throwable cause = e;
      while (cause != null) {
          String className = cause.getClass().getName().toLowerCase();
          String message = cause.getMessage() != null ? cause.getMessage().toLowerCase() : "";
          if (className.contains("integrity") || className.contains("constraint")
                  || className.contains("duplicate") || className.contains("unique")
                  || className.contains("primarykey") || className.contains("primary_key")
                  || message.contains("duplicate") || message.contains("unique constraint")
                  || message.contains("primary key") || message.contains("23505")) {
              return true;
          }
          cause = cause.getCause();
      }
      return false;
  }
  ```
- **现状**: GENERIC 方言路径（非 PG/MySQL/H2）靠消息文本判定唯一键冲突。两个缺口：① **假阳性**——任何消息里含 "unique"/"primary key"/"duplicate"/类名含 "constraint" 的**非**唯一键错误（如驱动文档串、别的约束冲突）都会进入 UPDATE 重试分支，掩盖原始错误并多打一次库；② **假阴性**——本地化消息（中文 Oracle/DM 报错）不匹配任何关键词 → 唯一键冲突被当真错误抛出（该方向安全，响亮失败）。
- **风险**: 误判时 checkpoint 写入表现为"UPDATE 成功或二次失败"，排障信号被稀释；正确性影响有限（INSERT 与 UPDATE 值相同）。
- **建议**: 用 `SQLException.getSQLState()`（23505/23000 族）+ 各方言 error code 做主判据，消息嗅探降级为最后兜底。
- **信心水平**: 高（逻辑确定；发生频率取决于 GENERIC 方言的实际使用面）。
- **误报排除**: PG/MySQL/H2 走 `buildNativeUpsert` 原子 upsert（:866-886），不经过本函数——影响面仅 GENERIC 路径，不夸大。

### [R5-CON-09] JdbcCheckpointStorage 在 GENERIC（最不可移植）方言上使用 LIMIT 语法——Oracle/SQL Server/达梦等恢复路径直接失败

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/JdbcCheckpointStorage.java:127-131`（LIMIT 1）、`:187-190`（LIMIT ?）、`:555-559`、`:610-614`
- **严重程度**: P3
- **证据片段**:
  ```java
  // :127-131  getLatestCheckpoint（restore 主路径）
  SQL sql = SQL.begin().name("getLatestCheckpoint").querySpace(querySpace)
          .sql("SELECT state_data FROM " + TABLE_NAME +
                  " WHERE job_id = ? AND pipeline_id = ?" +
                  " ORDER BY checkpoint_id DESC LIMIT 1", jobId, pipelineId)
          .end();
  ```
- **现状**: `resolveUpsertDialect`（:810-826）对 PostgreSQL/MySQL/H2 之外的**所有**库返回 GENERIC——恰是 Oracle/SQL Server/达梦等**不支持 `LIMIT`** 的数据库族。restore 主路径（getLatestCheckpoint / loadLatestEpochManifest / loadRetainedEpochManifests）在这些库上直接 SQLException（响亮失败，非静默）。`pruneEpochManifests` 注释（:649-653）已自知 "GENERIC dialect has no LIMIT"，但读路径仍普遍使用。
- **风险**: 声称可用的 GENERIC 兼容面实际在常见商业库上无法完成恢复；失败方向安全（typed 异常），故 P3。
- **建议**: GENERIC 路径改用窗口函数或 `MAX(checkpoint_id)` 子查询等可移植写法；或在 resolveUpsertDialect 无法识别时显式 fail-fast 声明不支持的方言清单。
- **信心水平**: 高（SQL 语法事实；未逐一验证各商业库驱动行为，但 LIMIT 非标准语法确定）。
- **误报排除**: H2 2.x 的 LIMIT 支持已被 :649-653 注释确认为受限——本条不含 H2；JdbcClusterRegistry 无 LIMIT 使用，不涉。

### [R5-CON-10] FileSourceReader.openSplit 不校验 split 路径归属于源目录——恢复路径缺防御纵深

- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileSourceReader.java:173-183`；对照序列化入口 `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileSource.java:138-157`
- **严重程度**: P3
- **证据片段**:
  ```java
  // FileSourceReader.java:173-183   filePath 原样打开，无 containment 校验
  private void openSplit(FileSplit split) throws IOException {
      Path path = Paths.get(split.getFilePath());
      if (!Files.exists(path)) {
          throw new IOException("File not found for split: " + split);
      }
      FileResource resource = new FileResource(path.toString(), path.toFile());
      FileInputStream fis = (FileInputStream) resource.getInputStream();
  ```
  ```java
  // FileSource.java:138-147   反序列化仅校验段数/数字，路径任意
  public FileSplit deserialize(int version, byte[] bytes) throws IOException {
      ...
      String[] parts = s.split("\\|", -1);
      if (parts.length != 4) { throw new IOException("Malformed FileSplit payload: " + s); }
  ```
- **现状**: split.filePath 全程来自 checkpoint payload（SPLIT_CURSOR_CHECKPOINT 恢复语义）。新格式 payload 有 canonical checksum（CheckpointSerDe，篡改即拒），但 **v1 legacy 无 checksum 字节被显式容忍**（CheckpointSerDe.java:161-164 "pre-checksum legacy bytes — skipping integrity verification"），被篡改/损坏的 legacy checkpoint 可让 reader 以作业进程权限读取任意可读文件并把内容当作数据流入管道（数据外泄面）。同时路径相对/绝对、是否在 directoryPath 之下均不校验。
- **风险**: 防御纵深缺失；触发前提是对 checkpoint 存储的写权限（本地目录/DB），故 P3。
- **建议**: openSplit 前校验 `path.normalize().startsWith(Paths.get(directoryPath).normalize())`（FileSource/Enumerator 需要把 directoryPath 传递给 reader——SourceReaderContext 可承载）。
- **信心水平**: 高（代码确定；威胁模型依赖 checkpoint 可写前提）。
- **误报排除**: 与 2026-05 维度13-06（GraphModelCheckpointExecutor 的 savepointPath 未验证）不同点：那是对**用户输入路径**的校验缺失，本条是**恢复数据内的派生路径** containment，二者互补不重复。

### [R5-CON-11] JDBC 2PC sink 整 epoch 记录驻留内存且无上限——慢提交/故障滞留期间内存随 checkpoint 间隔线性增长

- **文件**: `nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java:227-241`（invoke 缓冲）、`:253-263`（saveState 移入 pendingCommits）、`:272-354`（commit 成功才释放）
- **严重程度**: P3（设计权衡注记）
- **证据片段**:
  ```java
  public void invoke(IN value) throws Exception {
      ...
      synchronized (currentBuffer) {
          currentBuffer.add(new LinkedHashMap<>(row));    // 无 any 上限
      }
  }
  ```
- **现状**: 数据路径全程内存态：当前 epoch 的 `currentBuffer` + 已 saveState 待 commit 的 `pendingCommits`（List<Map>）。R1 的批次分段修复（maxBatchSize，:469-504）只限制了**单次 executeBatch** 的驱动侧缓冲，不限制堆内驻留。commit 失败（DB 抖动）时条目按设计保留重试，多个 epoch 的批可同时滞留。
- **风险**: 高吞吐 × 长 checkpoint 间隔 × 目标库故障的组合下 OOM。属 exactly-once 内存缓冲语义的固有代价，但没有任何背压/上限/告警。
- **建议**: 提供可选 spill-to-temp-file（file sink 已是先例：saveState 即落盘）或 at least 记录 buffer 字节数指标 + 可配置上限（超限抛错让上游重放）。
- **信心水平**: 高（结构确定；触发需组合条件）。
- **误报排除**: 不与 R1 "整 epoch 单次 executeBatch"（已修）重复——那修的是 JDBC 驱动批量缓冲，本条是 Java 堆内驻留。

---

## 遗留登记（R1 已报告、plan 366 未收录、live code 确认仍开放）

| R1 编号位置 | 现状核实 |
|---|---|
| `FileTwoPhaseCommitSink.java:97,342-344` MANIFEST_JVM_LOCKS 按 outputDir 无界增长 | **仍开放**（静态 ConcurrentHashMap 无剔除） |
| `BatchLoaderSourceFunction.java:60` currentOffset 非 volatile | **仍开放**（仅 task 线程约定的隐式保障；快照读到滞后值只产生重复，与 AT_LEAST_ONCE 声明相容，故维持 P3） |
| `BatchConsumerSinkFunction.java:71-74` 构造器内 provider.setup() I/O + final 字段序列化风险 | **仍开放** |
| `FileSourceReader.java:127-171` pollNext 在 synchronized(this) 内执行阻塞文件 I/O | **仍开放**（R4-P2 缓冲化后单次 read 系统调用减少，监视器驻留时间已缩短但结构未变） |
| `FileSourceReader.java:183` `(FileInputStream) resource.getInputStream()` 盲强转 | **仍开放**（当前 FileResource 实现契约下安全，属脆弱耦合） |
| `FileSplitEnumeratorState.java`（经 FileSplitEnumerator snapshotState :146-157, :171-172）nextSubtaskIndex 死字段 | **仍开放**（有意保持 wire 兼容，0 常量写入，已有注释说明——建议下个 serializer 版本升级时移除） |

---

## 标准检查项结论

### 1. SQL 注入 — 不成立（推演）

**JDBC sink（唯一动态拼标识符的点）**：表名/列名/账本表名全部经 `IDialect.escapeSQLName`。核验 `nop-persistence/nop-dao/src/main/java/io/nop/dao/dialect/impl/DialectImpl.java:421-445`：
```java
public String escapeSQLName(String name) {
    if (StringHelper.isEmpty(name))
        throw new NopException(ERR_DIALECT_INVALID_SQL_NAME).param(ARG_NAME, name);
    ...
    // 用双引号来表示已经转义
    if(name.charAt(0) == '"'){
        return StringHelper.quoteDupEscapeString(StringHelper.unquoteDupEscapeString(name), getKeywordQuote());
    }
    if (isReservedKeyword(name) || !StringHelper.isValidSimpleVarName(name)) {
        return StringHelper.quoteDupEscapeString(name, getKeywordQuote());
    }
    return name;
}
```
含引号/空格/特殊字符的名字进入 `quoteDupEscapeString`（双引号重复转义）成为字面标识符；`isValidSimpleVarName` 为真的名字不含引号。**利用推演**：配置 `tableName = 'x"); DROP TABLE t; --'` → 含 `"`/空格 → 走 quoting 分支 → 生成 `"x""); DROP TABLE t; --"` 的合法转义标识符，作为整体被 JDBC 当作一个表名发送 → 语法错误（表不存在），无注入。值路径全部 `PreparedStatement` 参数（JdbcTwoPhaseCommitSink.java:481-502、:450-467）。`querySpace` 仅作 dialect 查找键（:216），不入 SQL。
**JdbcCheckpointStorage / JdbcClusterRegistry**：表名编译期常量（:50-51、:30-32），值参数化，upsert 文本仅由常量列名数组拼出（`buildNativeUpsertSqlText` :859-886，`EXCLUDED.`/`VALUES()` 前缀拼接的 updateColumns 来自 :86/:313 常量）。`pruneEpochManifests` 的 IN 占位符由 `Collections.nCopies(staleIds.size(), "?")` 生成（:683-695），值参数化。
**结论**：连接器子系统无可利用 SQL 注入点。残余风险不在注入而在 R5-CON-01 的键设计。

### 2. 白名单双向断言 — 无透传回退

全部 4 类校验点 fail-fast，无"未命中原样返回"分支：
- `ClassNameValidator.validateClassName/validateAccumulatorClass`（nop-stream-core/.../util/ClassNameValidator.java:44-63）：未命中**抛** `ERR_STREAM_CLASS_NOT_ALLOWED`；全部 6 个调用点（ContainerValueCodec:276、MemoryStateSerDe:349/355/558、SimpleTypeSerializer:47、StreamElementCodec:60、RocksDBSnapshotSerDe:628/634/656、CheckpointSerDe:919）无 catch-继续路径。
- FileSource 序列化保留字符校验（FileSource.java:109-117/131/182-185/201/219）：命中即抛 IOException。
- MessageSourceFunction typeClass 不匹配（:139-153）：capture-and-rethrow（P1-9 修复）。
- Debezium connectorType switch（DebeziumEngineConfig.java:72-86, :150-162）：default 抛 `ERR_DEBEZIUM_UNSUPPORTED_CONNECTOR_TYPE`。

### 3. SSRF/主机规范化 — 不适用

连接器子系统无任何 URL/主机白名单校验代码（kafka bootstrapServers、pulsar serviceUrl、debezium hostname 均为部署期配置直达客户端库），不存在可被绕过的校验逻辑，也无 userinfo/IPv6 规范化需求面。

### 4. 凭证处理 — 设计正确，一处低危日志面

- **Debezium（正面样本）**：`credential:{id}#{field}` 引用字符串持久化于 config 与所有序列化路径，明文仅在 `effectiveEngineConfig()` 的瞬态解密副本内（DebeziumCdcSourceFunction.java:372-438）；provider 为 transient 字段（:83），跨 JVM 不携带；解密 fail-closed（provider 缺失即 typed 错误）。日志面只打 `config.getName()`（DebeziumMessageSource.java:146-174），`DebeziumEngineConfig.buildProperties` 构建的含密码 Properties 无日志输出点。
- **Kafka/Pulsar**：SASL/SSL 凭证经 `extraProps` 传入 client properties（KafkaMessageService.java:140-142,166-169），无 toString/日志输出（KafkaClientConfig.java:25-53 无 toString）；init 日志仅 bootstrapServers（:92）。
- **JDBC sink**：凭证由 `IJdbcTemplate` 持有，connector 代码不触碰凭证；异常消息只含表名/epoch。
- 唯一低危：R5-CON-06（batch sink close 日志记录内容——业务数据而非凭证）。

### 5. 反序列化安全 — 无不受信反序列化

- Kafka/Pulsar payload 保持 String（`StringSerializer/StringDeserializer`，KafkaMessageService.java:90,273-274），消费侧按需由业务解析，codec 层无多态类型信标。
- Debezium JSON → `JsonTool.parseNonStrict` → 纯 Map（DebeziumEventConverter.java:39-44,98-110），无 `@type`/`class` 信标实例化。
- Checkpoint JSON：`JsonTool.parseMap`；唯一的类实例化点是 accumulator 恢复（CheckpointSerDe.java:915-931），且先过 `validateAccumulatorClass`（仅放行 `io.nop.stream.` 前缀）再 `Class.forName().newInstance()`——实例化面收敛到平台自身包内（平台外用户类不在该包即拒绝）。
- payload 大小限制：Kafka 受 `max.poll.records=500` + broker `fetch.max.bytes` 约束；checkpoint `state_data` BLOB 无应用层上限（受 DB 限制）；文件源单行无上限（R5-CON-07）。
- 2026-05 维度13-02（javax.crypto/javax.net 白名单前缀过宽）维持原状未修——沿用原登记，不重复展开。

### 6. 2PC sink 正确性 — 骨架正确，一个键设计缺陷

- 顺序：saveState（先于 super.saveState 快照）→ preCommit（no-op）→ commit（数据+账本同事务）→ rollback/abort（纯内存/临时文件清理），与 `StreamSinkOperator.processBarrier` 时序注释一致（JdbcTwoPhaseCommitSink.java:44-58）。
- 异常路径：`commit` 的 finally 保证 rollback（未提交时）/恢复 autoCommit/关闭连接（:333-351），失败保留 pendingCommits 由 `finishCommit`/`restoreFromEpoch` 重试——无事务泄漏；file sink 的 abort 删临时文件（:422-429）。
- 恢复：`TwoPhaseCommitSinkFunction.restoreFromEpoch`（:216-260）durable→re-commit（失败保留）、non-durable→abort，方向正确；`setPendingCommits` 键归一化（:79-101）与 file sink 值归一化（:382-420）覆盖 JSON 往返退化。
- 并行隔离：`copyForSubtask` 缺省 fail-loud（:128-136），JDBC/file sink 均实现。
- **缺陷**：R5-CON-01（账本键缺命名空间）。恢复时未决事务处理（重点 6 之问）在单 sink 支路内正确；并行实例间事务名冲突在单支路内由 (epoch, subtask) 消解，跨支路即 CON-01。

### 7. FileSource/FileSink — B6' 修复完整、R4-P2 行为保持

见下节"历史修复核验"。路径遍历：输出侧全部常量文件名（`epoch-N[.sK].txt`、`.epoch-N[.sK].tmp`、manifest.properties，FileTwoPhaseCommitSink.java:603-613）；manifest 值经 `escapeManifestValue`（:460-486）转义反斜杠/换行/CR/tab/前导空格——含 Windows 路径往返安全。轮转原子性：ATOMIC_MOVE + fsync（数据文件 :309、manifest :536-542、目录 :331,563-569）+ AR-14 双层锁（:342-357）——EOS 下 flush/duplicate 语义：manifest 幂等 + rename 前置 fsync，崩溃后 re-commit 无重复。**未发现 B6' 所指"第三处无校验路径写出"残留**（三处序列化点 + file-sink manifest 值共 4 处全部有校验/转义）。输入侧残留：R5-CON-07（行缓冲无上限）、R5-CON-10（恢复路径无 containment）。

### 8. batch source — 分片与恢复

恢复语义（AR-13 next-index 约定 + skip shortfall fail-fast，BatchLoaderSourceFunction.java:90-124）正确；缺陷为 R5-CON-05（声明 PARALLEL 无分片）与遗留的 offset 可见性 P3。

### 9. 资源管理

- JDBC sink commit：连接三层 finally（rollback/setAutoCommit/close 各自捕获），`openConnection` 抛出时无连接可漏；`initializeLedgerTable` 同样 close-in-finally（:427-446）。
- Kafka 订阅：cancel → task.stop + consumer.close + executor 两段关闭（KafkaMessageService.java:330-351）；destroy 收敛全部订阅（:100-122）。**MessageSourceFunction 侧的取消竞态见 R5-CON-04**。
- Debezium：run() finally 取消订阅 + stop source（DebeziumCdcSourceFunction.java:204-224）；但引擎死亡无感知（R5-CON-03）。
- FileSourceReader：close/restoreState 均 closeActiveReader（:288-297,336-360）；pollNext 抛出时依赖 runtime 的 task close 链调用 close()。

### 10. 反压与吞吐语义 — 正确

- MessageSinkFunction.consume → `IMessageSender.send` → `FutureHelper.syncGet(sendAsync(...))`（nop-kernel/nop-api-core/.../IMessageSender.java:26-28）**同步等待 broker ack**，失败抛出触发上游重放——AT_LEAST_ONCE 声明成立，背压传播正确（有 Kafka delivery.timeout 兜底，无无限阻塞）。
- Kafka 消费：`enableAutoCommit` 默认 **false**（KafkaConsumerConfig.java:29-30）+ 批后手动 `commitSync`（KafkaConsumeTask.java:252-258）——at-least-once 语义与 source 声明一致；失败 seek-back 重投（:242-248）方向正确。
- 2PC sink 的 invoke 均为内存/磁盘缓冲 + 同步 flush，背压正确；file sink saveState 持锁写盘会暂停 invoke（吞吐特性非缺陷）。

---

## 历史修复核验（按要求专项复核）

### B6'（plan 366）：directoryPath 保留字符校验 — **修复完整**

三处写出点全部有校验（FileSource.java）：
1. `FileSplitSerializer.serialize` :131 — `validateReservedChars(filePath, "|\n\r", ...)`；
2. `FileSplitEnumeratorStateSerializer.serialize` directoryPath :182-185 — newline 校验（该段为换行分隔，`|`/`,` 合法，拒绝范围正确收紧）；
3. splitById 段 :200-201 — `validateReservedChars(path, "|\n\r", ...)`；CSV 段 :219 — `",\n\r"`。
反序列化侧 :253-259 段数不符 fail-fast。**第四处潜在路径写出**（file sink manifest 值）由 `escapeManifestValue` 覆盖（FileTwoPhaseCommitSink.java:460-486）。未发现第五处无校验路径写出。

### R4-P2（plan 366 Phase 4）：文件源缓冲化 — **行为保持成立**

FileSourceReader.java:63-71（8KB readBuf）+ `readBuffered/unreadBuffered`（:271-286）。逐行核对 cursor 语义：字节数在离开缓冲时计增（:244）、CR 的 lookahead 字节回退时同步回退（:254-257），`activeBytesConsumed` 与流位置严格同步；恢复播种 `currentOffset - startOffset`（:221）保持旧语义；AR-15-③ 的 endOffset cap（:237-239）与 AR-15-② 的监视器内游标推进（:142-152）均保留。-33% 性能声明的实测不在本轮范围（R3 已实测裁定）。

---

## 正向确认（无发现，记录防回归）

- **A1** JDBC 标识符转义链完整（escapeSQLName quote-dup-escape + 参数化值，见标准检查项 1）。
- **A2** 白名单零透传回退（见标准检查项 2）。
- **A3** Debezium 凭证引用（D4）设计：引用持久化、明文瞬态、fail-closed、provider 不随序列化迁移。
- **A4** FileTwoPhaseCommitSink 提交链：数据 fsync → ATOMIC_MOVE → manifest 原子替换（fsync+move）→ 目录 fsync，且全程 AR-14 锁内——OS 崩溃后不存在"manifest 已记提交而数据块未落盘"窗口（R1 P2 已修验证）。
- **A5** Kafka 后端 at-least-once 链路自洽：auto-commit 默认关 + 手动 commitSync + 失败 seek-back（对照 MessageSourceFunction 声明）。
- **A6** `copyForSubtask` 缺省 fail-loud + JDBC/file sink 均实现独立副本——R1 的"parallelism>1 共享 pendingCommits"P0 族无回归。

## 发现分布

| 严重程度 | 数量 | 编号 |
|---|---|---|
| P0 | 0 | — |
| P1 | 3 | R5-CON-01（JDBC 账本键缺命名空间→静默跳写）、R5-CON-02（Debezium dispatchEvent 吞 collect 异常→数据丢失）、R5-CON-03（Debezium 引擎死亡不上抛→静默断流） |
| P2 | 2 | R5-CON-04（MessageSourceFunction 取消/订阅竞态→订阅泄漏）、R5-CON-05（batch-loader PARALLEL 无分片→N 倍重复） |
| P3 | 6 | R5-CON-06 ~ R5-CON-11 |

**P0：无。P1 详述见上文对应条目**——三条 P1 中两条（CON-02/03）集中在 nop-message-debezium 包装层（connector 对引擎生命周期的错误处理契约缺失），一条（CON-01）为 jdbc-2pc 幂等 guard 的键设计，均具备明确的修复模式（P1-9 捕获-重抛移植 / 失败监听接线 / 账本键扩维）。
