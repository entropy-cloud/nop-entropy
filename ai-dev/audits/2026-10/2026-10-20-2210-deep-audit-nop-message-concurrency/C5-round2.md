# C5 深挖第 2 轮 — nop-message（WI 4.1）

- 审计维度：C5（内存有界性与大数据量）· 深挖第 2 轮（合并批 C1/C3/C5/C6/C8 之 C5 分卷）
- 日期：2026-10-20
- 盲审声明：同 C1-round2.md。验算口径沿用第 1 轮：「单消息有界 × 并发/订阅数 × key 基数 × 时间」；有界声明存在的客户端缺省值按误报校准不报"无界"，只审"配置面缺位/边界与使用方规模矛盾"的例外分支。

## 深挖范围与方法

按派发指令对第 1 轮 4 条之外的四个定向面复核：

| 定向面 | 复核对象 | 结论 |
|---|---|---|
| ChangeEvent 事件对象的 payload 持有（大行/大 blob） | `ChangeEvent.java:32-61`（final 字段持有 before/after/key Map）、`DebeziumEventConverter.convert:39-69`（String + Map 双活）、`DebeziumMessageSource.dispatchEvent:155-163`（无驻留、`LOG.error` 用 toString 不含 before/after） | 单事件瞬态有界（第 1 轮清单第 13 行结论维持），**无跨事件驻留面**；blob 行宽问题转为引擎队列验算输入 → 立案 C5-006 |
| Kafka 批量 poll 后的记录物化 | `KafkaConsumeTask.batchConsume:488-500`（records → list + recordList + responses 三重物化） | 计数上界 = maxPollRecords（可配），放大倍数 ~3-4x/批，瞬态；**并入「扩展清单第 7 行」**，不立案 |
| Pulsar receiverQueue 与 maxPendingMessages 发送端缓冲 | `PulsarProducerConfig.java:17-41`（仅 batching 三字段）、`PulsarMessageService.buildProducer:221-245`（builder 只装配 batching/sendTimeout）、`producers` CHM :87（每 topic 一 producer 永不淘汰） | 发送端 pending 缓冲为 C5-004（consumer 端 prefetch 配置面缺位）的 F3 对称兄弟 → **立案 C5-005** |
| MessageMetrics 标签基数 | `MessageMetrics.java:34-47`（4 个固定名计数器/Timer，backend 字符串为唯一变量段，无 topic/groupId 标签） | 基数 = 后端数（≤4），有界且无爆炸面，examined-empty |

## 新增发现

### [C5-005] Pulsar 发送端 pending/batching 缓冲无模块配置面：每 topic 一个常驻 producer × 客户端缺省双队列，堆峰值验算不可控（C5-004 的 F3 对称兄弟）

- **文件**: `nop-message/nop-message-pulsar/src/main/java/io/nop/message/pulsar/PulsarProducerConfig.java:17-41`（全类仅 batchingEnabled/batchMaxMessages/sendTimeout 三字段）；`nop-message/nop-message-pulsar/src/main/java/io/nop/message/pulsar/PulsarMessageService.java:231-238`（builder 装配全程未触 maxPendingMessages/blockIfQueueFull）、`:87`（`producers` CHM 按 topic 常驻、永不淘汰）
- **证据片段**:
  ```java
  // PulsarMessageService.buildProducer:233-237 —— 全部装配点
  if (defaultProducerConfig != null) {
      builder.enableBatching(defaultProducerConfig.isBatchingEnabled());
      builder.batchingMaxMessages(defaultProducerConfig.getBatchMaxMessages());
      builder.sendTimeout(defaultProducerConfig.getSendTimeout(), TimeUnit.MILLISECONDS);
  }
  return builder.create();     // maxPendingMessages / blockIfQueueFull 走客户端缺省
  ```
- **严重程度**: P3
- **并发场景**: 每个曾发送过的 topic 一个常驻 Producer（CHM 缓存），各持客户端侧两道缓冲：batching 队列（batchMaxMessages=1000 条，可配）与 pending 未确认队列（maxPendingMessages 客户端缺省 1000 条，不可配）；发送线程并发写入，broker 慢时两道缓冲同趋满。
- **触发时序**: T1 应用向 N 个 topic 发送（含派生的 ack-topic 族——`PulsarHelper.deriveAckTopic` 使 reply 场景 topic 基数翻倍）→ 每个首次发送惰性建一个常驻 producer → T2 broker 退化期间消息积压：每 producer 堆驻留 ≈ 1000（batching）× 行宽 + 1000（pending）× 行宽；常规 64KB 行单 producer ≈ 128MB，10 个业务 topic + 10 个 ack topic ≈ 2.5GB 量级，且应用**无任何配置点**收窄（对照 Kafka：buffer.memory=32MB 单例总量封顶，`KafkaProducerConfig.bufferMemory` 可配）。
- **现状**: 上界存在（客户端缺省 1000/1000 per producer，非无界），缺的是**模块配置面与验算公式**——与第 1 轮 C5-004（consumer 端 receiverQueueSize 缺省 1000/消费者不可配）完全对称；round 1 边界清单第 14 行只登记了 batchingMaxMessages"有界且可配"，未盘 pending 队列与 per-topic 乘数。
- **风险**: 峰值叠加（单 producer 有界 × topic 基数 × ack-topic 翻倍）超出堆预算时无调优入口；与 Kafka 后端（32MB 总量）的验算口径不可比，容量规划按 Kafka 经验迁移到 Pulsar 后端即失效。
- **建议**: `PulsarProducerConfig` 增加 `maxPendingMessages`/`blockIfQueueFull` 字段并映射 builder（blockIfQueueFull=true 时改走 Pulsar 自带阻塞背压，同时把阻塞面移交 C8 语义评估）；owner doc 补记 per-producer 双队列验算公式与 ack-topic 基数翻倍提示。
- **失效面枚举**（F3：发送/接收缓冲配置面逐一）: ① Pulsar consumer receiverQueueSize —— C5-004 已立案 ✗；② **Pulsar producer maxPendingMessages/batch 队列 —— 本条** ✗；③ Kafka producer buffer.memory=32MB+max.block.ms —— 可配且有总量界 ✓；④ Kafka consumer maxPollRecords —— 可配 ✓；⑤ Debezium 引擎队列 —— 计数上界存在但字节面缺位，另案 C5-006 ✗。
- **信心水平**: 确定（配置面缺位与缺省值行为可证；峰值数值依赖行宽画像）
- **误报排除**: 按误报校准不报"无界"——客户端缺省构成事实上界；本条走校准的例外分支（有界但配置面缺位、与使用方规模验算不可控），与 C5-004 的定级逻辑逐字对齐，故 P3。
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜现状:新发现（PulsarProducerConfig 仅 batchingEnabled/batchMaxMessages/sendTimeout 三字段（:14-16）+buildProducer :233-237 装配面无 maxPendingMessages/blockIfQueueFull+producers CHM 常驻逐点回码确认；与 C5-004 的对称性、ack-topic 基数翻倍提示均成立）

### [C5-006] Debezium 引擎队列仅有事件数上界且可被 extraProperties 上调——宽行/blob 表快照期的字节峰值无装配面、无验算约束

- **文件**: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/DebeziumEngineConfig.java:158-175`（extraProperties 透传，钉定清单不含 max.queue.size 族）、`:103-141`（钉定清单仅 offset.storage/connector.class/schema.history.internal/record.processing.threads）
- **证据片段**:
  ```java
  // DebeziumEngineConfig.buildProperties:165-175 —— 前缀钉定族之外全量透传
  for (Map.Entry<String, String> entry : config.getExtraProperties().entrySet()) {
      String key = entry.getKey();
      if (key != null && (key.startsWith("schema.history.internal.")
              || key.startsWith("offset.storage."))) {
          throw ...;                     // 仅这两个前缀被钉
      }
      props.setProperty(key, entry.getValue());   // max.queue.size 可自由上调
  }
  ```
- **严重程度**: P3
- **并发场景**: 引擎快照/追平线程按 `max.queue.size`（第三方缺省 8192 条）向 `notifying` 回调投递 `byte[]` 事件（JsonByteArray 格式，单事件 = 整行 before/after 的完整 JSON）；`record.processing.threads=1`（:141 钉定）+ `dispatchEvent` 串行 fan-out 决定消费端速率——慢订阅者期间队列蓄满驻留。
- **触发时序**: T1 对含 BLOB/宽 TEXT 列的表执行 initial snapshot（快照模式缺省 "initial"，DebeziumConfig:100）→ 单事件字节数 = 行宽（数 MB 级现实）→ 队列蓄满时堆驻留 ≈ 8192 × 行宽：1MB 行 ≈ 8GB，且无 `max.queue.size.in.bytes` 类字节上界被模块缺省设置；T1' 部署侧经 extraProperties 把 max.queue.size 调到更大值亦无任何守卫（不在钉定族）。
- **现状**: 第 1 轮清单第 12 行以"有界（第三方默认声明）"结案，验算仅覆盖事件**计数**维；本轮补检发现字节维完全无装配（对比：本类对 offset.storage 等四键实施钉定，说明钉定机制在位而未覆盖该键），行宽自由变量未进入任何声明包络——符合校准例外分支"上限配置与使用方规模矛盾/不可控"。
- **风险**: 宽行表快照是 CDC 的一等场景而非边缘配置；OOM 点随行宽漂移且无配置入口收窄，运维无法按堆预算反推安全行宽。
- **建议**: buildProperties 缺省设置 `max.queue.size.in.bytes`（按堆预算派生，如 ≤256MB）并把 `max.queue.size`/`max.batch.size`/`max.queue.size.in.bytes` 纳入钉定或守卫族（仅允许下调）；owner doc 补记快照期峰值验算公式（队列条数 × 行宽）。
- **失效面枚举**（F3：本模块引擎侧缓冲逐一）: ① Debezium 事件队列 —— 本条 ✗（计数有界、字节面缺位、可上调）；② `NopStreamOffsetBackingStore` data map —— 每连接器分区数 × offset 字节，有界（C5-001/C1-002 已立案其生命周期面，内存面小）✓；③ snapshot.max.threads>1 的 chunk 并行（DebeziumConfig:103-106，可配）—— 并行仅放大速率不放大驻留上界 ✓；④ `FAILURE_LISTENERS`/`REGISTRY` 静态表 —— C5-001/C5-002 已立案，不重复。
- **信心水平**: 很可能（配置面与透传行为可证；具体 OOM 点依赖行宽与订阅者速率画像，未实测第三方队列对 byte[] 事件的精确驻留倍数）
- **误报排除**: 非"有界声明存在的缓存"误报——第 1 轮的"有界"结论只验算了计数维，本轮证据表明字节维无界且缺省验算与 blob 场景矛盾，属校准例外分支；亦非超出声明包络——owner doc 未对该引擎声明任何规模包络可援引。
- **复核状态**: 未复核
- 复核状态: 保留(P3)｜现状:新发现（:165-175 前缀钉定族仅 schema.history.internal.*/offset.storage.*、:141 record.processing.threads 钉 1、全类无 max.queue.size.in.bytes 缺省逐点回码确认；snapshot.mode 缺省 "initial"（DebeziumConfig.java:100）佐证宽行快照为一等场景；「很可能」信心自评恰当）

## 扩展已立案条目（第 1 轮）

### 扩展清单第 7 行（Kafka 批量物化，维持有界结论，补放大倍数证据）

`KafkaConsumeTask.batchConsume:488-500`：单批内同时存活 `records`（客户端缓冲）、`list`（TopicMessage+ApiMessage 包装）、`recordList`（ConsumerRecord 引用）及 handler 返回的 `responses`（用户侧），峰值 ≈ 3-4× 批字节量；上界 = `maxPollRecords`（可配，`:220/:229-231`）× 单消息大小，瞬态不跨批驻留——第 1 轮"有界（配置声明）"结论维持，放大倍数补记供堆验算。

### 扩展清单第 13 行（ChangeEvent 瞬态放大，维持有界结论）

`DebeziumEventConverter.convert:39-44`：`valueJson` String 与解析后 Map 树瞬态双活（单事件 ~2× String + 树开销），`dispatchEvent` 无驻留、错误日志 `toString()`（ChangeEvent.java:116-123）刻意排除 before/after——大 blob 行不产生跨事件驻留；其规模后果已转由 C5-006 的队列字节面承接。

## 维度集外线索（overlay）

（本轮 C5 分卷无新增集外线索；C8 分卷承接的 Debezium 消费速度耦合扩展见 C8-round2.md。）

## 继续判定

**终止信号。** 依据：本轮新增 C5-005/C5-006（P3×2，均为"有界但配置面缺位"的对称兄弟——C5-004 的 F3 双侧补全），无 P2 及以上；两个既有"有界"结论经深挖后维持并补强证据（第 7/13 行扩展）；四个指令面中 metrics 标签基数、payload 持有两面 examined-empty。第 1 轮 4 条 + 本轮 2 条覆盖了 16 行无界风险清单的全部复检点，且新发现与既有立案同构（配置面对称族），收敛证据充分，无需第 3 轮。

## 复核结论（WI 4.1 独立复核，2026-10-20）

- **C5-005 复核保留（P3）**、**C5-006 复核保留（P3）**：状态行已写于各条目末尾。本轮终止信号判定成立——新增仅 2 条 P3 配置面对称族、两面 examined-empty，C5 维度两轮覆盖可收口的裁定成立。
- 扩展清单第 7/13 行（Kafka 批量物化 3-4x 瞬态放大、ChangeEvent 单事件瞬态双活）的有界结论维持，核验通过：`batchConsume:487-507` 三重物化计数上界 = maxPollRecords（KafkaConsumerConfig.java:33 可配）、`convert` 无跨事件驻留，均与回码一致。
- 本维度复核全量结论（判定统计/跨单元对照/最终保留项）见 `C5-memory-bounds.md`「维度复核结论」节：6 条 = 保留 6（零降级零驳回），最终 P2×2/P3×4。
