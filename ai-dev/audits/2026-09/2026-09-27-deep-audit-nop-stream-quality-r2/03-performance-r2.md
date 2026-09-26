# nop-stream 性能未测路径审计（R2）

> 基准：HEAD 56e35aeaff。全部行号本次实读。排除 plan 360 已优化项。
> 基准风格对标 `nop-benchmark/nop-benchmark-stream`（@Fork(1)、@Warmup(3×2s)、@Measurement(5×2s)、AverageTime、-prof gc 记 alloc.rate.norm）。

## 一、已登记候选的代码级分析

### 1. RemoteResultPartition.write 锁内同步 send（360 登记，未实测）
`runtime/.../transport/RemoteResultPartition.java:162-180`：方法级 synchronized；锁内 encode（JSON #1，payload）→ `messageService.send`（IMessageSender 默认实现 = `FutureHelper.syncGet(sendAsync)` 阻塞）→ DataPlaneMessageServiceAdapter.sendAsync:74-77 → KafkaStringWireCodec.toWire:48（JSON #2，envelope 再序列化）→ 后端发送。SysDao/DB 后端一次同步 JDBC 写（ms 级）全在锁内。`sendHeartbeatIfIdle:271` 同锁，心跳线程与数据面互卡。`RecordWriter.emit` broadcast 分支（:154-167）对 T 分区逐个 `partition.write(record)` → 同一记录 encode T 次。
- 拟建基准：`RemoteTransportWriteBench`（桩 IMessageService，@Param 发送延迟 0/100µs/5ms、fanout 1/4/16，writer+heartbeat @Group）
- 收益机制：锁内不等网络；broadcast encode-once；心跳解耦
- 风险：close() 先停心跳再发 EOS 顺序契约（:189-197 注释）与 eosSendError 可见性需重新验证

### 2. InputGate 50ms 轮询（360 登记，未实测）
`core/.../execution/InputGate.java`：CHANNEL_POLL_TIMEOUT_MS=50(:105)、IDLE_PARK_NANOS=10ms(:113)、IDLE_RETURN_THRESHOLD_MS=250(:87)。readSingleChannel:513-594 `channel.read(50,MS)`(:530→RemoteInputChannel.read:304-320 queue.poll(50ms))；空轮询 idleThresholdReached(:541→:626) 每次读 CoreMetrics.currentTimeMillis()。readMultiChannel:629-713 逐通道 poll(50ms)(:667)，整轮空扫后 parkNanos(10ms)(:711)；空闲 250ms 返回 empty 让 StreamTaskInvokable.processInputGate(:883-910) 排空 mailbox。
- **关键事实**：poll 信号驱动，饱和流零成本；50ms 真实成本=空闲 CPU；10ms park 不被队列插入唤醒 → 空闲→首记录最坏 +10ms；250ms 阈值与 150ms producer-death 超时次序契约（:79-86）不可破坏
- 拟建基准：`InputGateReadLoopBench`（@Param gap 0/100µs/10ms、channels 1/4；饱和档吞吐 + SampleTime 延迟档）
- 预期：吞吐≈0 收益；延迟/CPU 小项 → 大概率维持不实施（量化后裁定）

### 3. CepOperator timer 批全扫 + RMW（360 登记，未实测）
`cep/.../operator/CepOperator.java`：
- `getSortedTimestamps:1033-1039` 每 timer 批（onEventTime:902 / onProcessingTime:956）new PriorityQueue + elementQueueState.keys() 全扫
- `bufferEvent:881-890` 每事件 MapState get+add+put（RocksDB 下整桶双 JSON）
- 已有 per-key timer 台账 `registeredEventTimeTimersByKey: Map<Object,TreeSet<Long>>`(:170)，bufferEvent→registerTimer(:885)→registerEventTimeTimerForKey(:822-827) 登记，onEventTime STEP 5(:942-951) 过期删除 → **台账驱动 drain 可行且无需状态格式变更**（台账 ⊇ 当前 key 队列桶；window timer 无桶需 :910 null 守卫）
- 相邻冗余：`processWatermark:699-722` 每 watermark `snapshotTimersByKey()`:847-856 全台账深拷贝（每 key new TreeSet）；`forEachEventTimeTimer:463-469` 循环内再 `new TreeSet<>`(:465)
- 拟建基准：`CepOperatorBench`（memory/rocksdb @Param、keys 1/64、bucketsPerKey 8/64 乱序散布、processElement+周期 processWatermark）
- 风险：legacy checkpoint 恢复后台账与桶短期不一致 → 保留全扫兜底分支；null 桶守卫必须补

### 4. CheckpointSerDe base64（360 登记，未实测）
层级实查：主层级 `core/.../memory/MemoryStateSerDe.serializeWithSerializer:748-771`（byte[] 包 `{"__java_bytes__": base64}`，decode :799-807；调用点 :123/:161/:180/:187/:205，最大 payload 为 CEP NFAState）；次层级 `CheckpointSerDe.java:295/:661`。**落盘契约是 JSON 文本**（serializeCheckpoint:117 `.getBytes(UTF_8)`），免 base64 属格式变更 → 归并二进制格式 Deferred。**真热点见 F1 checksum。**

### 5. BufferPool 批量许可（360 登记，未实测）
`core/.../execution/buffer/BufferPool.java:36-137`：公平 Semaphore（:39/:56，javadoc :22-31 明示 FIFO 有意）；acquire:60-73（closed 双检 :66-71）/release:92-94 单许可。生产侧 ResultPartition.enqueueWithBackpressure:265-282（acquire 267/274 在 queue.put 前）；消费侧逐元素 release（:308-310/:331-333；旁路 :444-446/:487-489）。公平信号量无竞争 ~15-25ns；ping-pong 下 park/unpark 握手 ~1-5µs/许可。
- 拟建基准：`BufferPoolPermitBench`（@Group 生产/消费环形，@Param 批量 1/8/32、并发 writer 1/4）
- 预期：仅背压挤压池边受益，畅通边 <2%（诚实预期，可能不达线）
- 风险：背压粒度松弛、公平 FIFO 语义、close/abort 与 unaligned captureInFlightData 逐元素 release（:427 注释契约）对齐

### 6. ProcessingTimeServiceDriver sleep-to-deadline（360 登记，未实测）
`core/.../execution/ProcessingTimeServiceDriver.java`：run:116-132 固定 tick（DEFAULT_TICK_MS=100 :43）：currentTimeMillis(:118)→双 due 检查(:119-120)→mailbox.put(:122)→sleep(tickMs)(:126)。问题：timer 触发延迟 0-100ms；空闲任务每秒 10 次唤醒；task 忙时重复 fire mail。`TaskProcessingTimeService.nextTimerTimestamp` volatile long(:60) 已存在 → `sleep(min(tick, max(1, nextDue-now)))` 可行；HeapInternalTimerService 的 TreeMap task 线程封闭，需 shadow-volatile（先例 javadoc :54-60）。
- 拟建基准：`ProcessingTimeDriverLatencyBench`（SampleTime 量 fire−due 分布；@Param tickMs 100/20）
- 收益口径：延迟/能耗非吞吐；风险低

## 二、新发现

| # | 位置 | 问题 | 严重度 |
|---|------|------|--------|
| F1 | `runtime/.../checkpoint/storage/CheckpointSerDe.java:351-365`（写侧调用 :116/:229-230；读侧 :157/:554-556） | computeCanonicalChecksumHex：全文 serialize→parseMap→normalizeNumbersDeep 深拷贝重建→再 serialize→SHA-256；随后 serializeCheckpoint:117/serializeEpochManifest:231 再全文 serialize → 写一次 checkpoint 全文 3× 序列化+1× 解析+2× 深拷贝；restore 侧同重算一轮。7.6ms serialize 基线的主体候选（360 未触碰）。校验值内容寻址，正形/算法变更需 CHECKSUM_ALGO 版本门控 | **P1** |
| F2 | `WindowOperator.java:1441-1454`（调用点 :1363/:1392/:1400/:1408/:1417/:1428） | evictor 路径 storeElementTimestamp 每元素整 List get+add+put；RocksDB 后端每窗口 O(n²)；现有 WindowOperatorProcessElementBench EVICTOR 档仅 Memory 后端测不到。AR-3 索引对齐契约 :1027-1030 约束增量设计 | **P1** |
| F3 | `WindowOperator.java:1371 + 1539-1567` | 兜底 MapState 布局（windowStateDescriptor==null 或后端非 IInternalStateBackend，open():434-457 分支）每元素 `windowContentsState.get("__window_value__")`+setWindowContents→put 整体 RMW；360 的 RocksDBAggregatingState 缓存只覆盖 InternalAppendingState 描述符路径 | **P1** |
| F4 | `WindowOperator.java:1716-1725 + :2164` | windowNamespace `"TW:"+start+","+end` 每状态操作 1-5 次拼接（:1369/:1447/:1496/:1532/:1565），抬高 (key,ns) 前缀缓存 equals 成本；getSimpleAccumulator stateKey 每 trigger 重建（计数/处理时间触发器=每元素每窗口一次） | P2 |
| F5 | `StreamTaskInvokable.java:876/:914/:931/:935` + `InputGate.java:369-374/:763` | 每记录 4 次 CoreMetrics 时钟读（~100-240ns/记录）+ 每记录 Optional 分配；activity/progress 降频需先复核 G52 liveness 语义 | P2 |
| F6 | `RecordWriter.java:155-167` | broadcast 每目标重复 encode（与候选 1 同根，encode-once 或 partition 内 memo） | P2 |
| F7 | `CepOperator.java:847-856/:711/:463-469` | 每 watermark 全台账深拷贝 ×2（与候选 3 同基准覆盖） | P2 |
| F8 | `InputGate.java:1129-1137 + :478-499` | 每 watermark new Watermark + 两轮 O(channels) min 扫描（低频，watch-only） | P3 |
| F9 | `StreamControlRpcTransformer.java:57-79` + `StreamControlRpcServer.java:105-134` | 控制面每消息反射参数映射+ApiRequest JSON 往返（速率低，watch-only） | P3 |
| — | `TaskManager.java:285-339` + `JobCoordinator.java:1028-1057` | 心跳/周期检查点调度健康，无每消息冗余（已扫描排除） | — |

## 三、建议裁定预判

| 候选 | 预判 | 理由 |
|------|------|------|
| 候选 3 + F7（CepOperator 台账驱动 drain + 免拷贝） | 建基准后大概率 ≥2%（RocksDB/多桶显著） | 无格式变更、有台账基础设施 |
| F1（checksum） | 量化后大概率 Deferred（格式耦合）或版本门控优化 | 内容寻址契约 |
| 候选 1 + F6（锁收窄/encode-once） | SysDao 后端数量级、Kafka 中等；须重验 close 顺序契约 | 360 明示"先建基准再实施" |
| F2/F3/F4（WindowOperator RocksDB 路径） | 建基准后量化；F2 需增量追加设计（AR-3 契约） | 现有基准盲区 |
| 候选 6（sleep-to-deadline） | 低风险可做；收益口径=延迟 | 非吞吐项，单独留舍 |
| 候选 2（InputGate 轮询）/候选 5（BufferPool） | 量化后大概率不实施/<2% | 诚实预期 |
| F5（时钟读降频） | 需 G52 语义复核，预判 watch-only 或小改 | liveness 语义风险 |
