# R3 性能审计（2026-09-29）

> 方法：既有裁定抽查验证（8 项）+ 新候选代码级分析 + 基准覆盖缺口盘点。停止判据沿用 360/2279/plan-01："无 ≥2% 低风险可收割项"（JFR 归因 + 实测双证据）。

## 第一部分：既有裁定抽查（8/8 维持，未被后续改动破坏）✅

| 项 | HEAD 现状 | 裁定一致性 |
|---|---|---|
| D2 evictor 时间戳 O(n²) | `WindowOperator.java:1440-1453` 全量 get+add+put | 2279 F2 Deferred（需列表格式设计）维持 |
| D3 checksum 多轮序列化 | `CheckpointSerDe.java:329-364` canonical 双路共用 | 2279 F1 Deferred（格式 v2）维持 |
| C2 BufferPool 公平信号量 | `BufferPool.java:56` fair semaphore，javadoc 明示契约 | 2279 量化关闭维持 |
| F3 RocksDBListState O(n) 追加 | `RocksDBListState.java:117-128` read-modify-write | 360 Deferred 维持 |
| E3 NFA 记忆化 | `NFA.java:722-732` IdentityHashMap 贯穿三处 | plan-01 保留（-7.7%~-10.6%）维持 |
| F1 RocksDB internal 缓存 | `RocksDBKeyedStateBackend.java:448-459` 单槽缓存 | plan-01 保留（-2.5%~-5.7%）维持 |
| F2 Memory internal 缓存 | `MemoryKeyedStateBackend.java:448-459` 同形态 | plan-01 保留（-57.8%）维持 |
| C1/SharedBuffer guava/A12 写回 | `InputGate.java:105`、`SharedBuffer.java:201,209`、`WindowOperator.java:1401-1406` | 各先行裁定维持 |

## 第二部分：新候选

> 已排除为非候选：TaskManager 心跳（5s）、SupervisionLoop（100ms 小 map）、GraphModelCheckpointExecutor prologue（每 checkpoint 一次，G10 可读性）、MessageSource/Sink（checkpoint-lock 直传）、ChainingOutput（零分配直转发）、CloseSupport（teardown 路径，无热路径开销）、HeapInternalTimerService register/advance（已有基准 78/102ns）。

| # | 候选 | 位置 | 热度 | 预估收益 | 风险 |
|---|---|---|---|---|---|
| R4-P1 | 文件 sink invoke 每记录 `toString()` 物化 | `FileTwoPhaseCommitSink.java:228` | 每记录；Map/Bean toString ≈ 序列化级成本，文件 sink 任务内最重单点逐记录 CPU | 任务内 10-40%（大 payload 端到端可能 ≥2%） | **中偏高（aliasing 契约）**：当前逐记录快照 aliasing-safe；延迟物化会暴露用户 mutate 污染。需实测 + owner 语义裁定，可能落"实测后 Deferred" |
| R4-P2 | 文件源逐字节读 + 每行新 BAOS + 每记录 FileSplit 拷贝 | `FileSourceReader.readNextLine:142,224-254`（H 组既有项量化升级） | 每行；PushbackInputStream→BufferedInputStream 双 synchronized | 源任务内 5-30% | 低（行为保持重写，缓冲化） |
| R4-P3 | JDBC sink invoke 每记录防御性 Map 拷贝 | `JdbcTwoPhaseCommitSink.java:239` `new LinkedHashMap<>(row)` | 每记录；10 列 ≈ 100-300ns | 任务内 3-10%；端到端通常 <2% | 低-中（拷贝防 mapper 复用 Map 的 aliasing；移除需契约文档化/门控） |
| R4-P4 | E3 记忆化每次 computeNextStates 分配 IdentityHashMap（默认 32 槽 ≈264B） | `NFA.java:728-729` | 每（事件×部分匹配状态）；d20 ≈ 20 实例/事件 ≈ 5KB（占 d20 45KB/op 的 ~11%） | d20 1-2.5%，浅模式 <1%——恰在 2% 边界 | 无（惰性分配/小容量预置，语义不变）；**不能**跨状态共享单 map（verdict 依赖 ConditionContext） |
| R4-P5 | 单槽 storage-key 缓存被 evictor 布局双族 namespace 打穿 | `RocksDBKeyedStateBackend.java:448-459`/`MemoryKeyedStateBackend.java:448-459` | 每记录 3-4 次 miss ≈ ~1µs（仅 evictor+list 布局） | 独立 ~1%（ROCKSDB EVICTOR 被 D2 主导）；MEMORY EVICTOR 可能 2-4% | 低（双槽缓存失效契约不变）；登记为 D2 successor 伴生项 |

## 第三部分：基准覆盖缺口

**建议补建**：
1. **ConnectorInvokeBench**（最高优先）：file source 行读取 + file 2PC sink invoke + JDBC 2PC sink invoke（mock driver），参数化 payload 大小——R4-P1/P2/P3 全部依赖此口径。
2. TaskDispatchLoop 中间层（processInputGate + metrics + Optional + mailbox drain + dispatch）——C3/C4/C5 家族从无隔离口径（2279 对 C4 的 ~100-240ns 是解析估值）。本轮候选不依赖它，登记 follow-up。
3. 嵌入态端到端 checkpoint 循环（trigger→snapshot→persist→ACK→notify→finishCommit）——为 D4/D7/A11 Deferred successor 提供前置证据。
4. SharedBufferAccessor.extractPatterns（E6 无直接口径）、Memory shard 路由（现有基准跑 maxParallelism=1 直通）。

**判定不值得建基准**：CheckpointBarrierTracker（每 checkpoint 一次）、SupervisionLoop、TaskManager 心跳、CC prologue——控制面低频。

## 下一轮实测排序（≤5）

1. R4-P2 文件源缓冲化（行为保持，最可能 ≥2% 且低风险）
2. R4-P1 文件 sink toString（实测 + 语义裁定；大概率 Deferred 记录）
3. R4-P3 JDBC map 拷贝（实测；端到端 <2% 预期，记录证据）
4. R4-P4 E3 memo 惰性分配（现有 NfaProcessBench d20/billable 直接复测）
5. R4-P5 双槽缓存（现有 WindowOperatorProcessElementBench EVICTOR 档；大概率 watch-only）
