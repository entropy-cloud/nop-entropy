# nop-stream 第二轮深度审计（质量与性能）— 总体发现摘要

> Audit Date: 2026-09-27
> Baseline: HEAD 56e35aeaff（plan 358/359/360 已落地，编译全绿）
> Scope: `nop-stream/` 全部子模块 `src/main`（排除 `_gen/`、`_` 前缀生成物；行为保持）
> Method: 3 个独立只读审计子代理（可读性/结构增量审计、性能未测路径审计、昨日三计划落地代码回归审查）+ 主 agent 亲自抽查关键声明（C1 finally 顺序、NodeId.hashCode、三个超长方法行数均实证）
> De-dup: 全部 358/359/360 已修复项与已登记 Deferred 项已从发现中排除；引用行号均为本次实读的当前 HEAD 行号

## 一句话结论

昨日三个计划落地质量良好（7 项缓存失效语义全部 PASS、抽查拆分逐分支等价），但发现：**1 项窄路径 live defect（EOS 发送失败跳过输入 gate 关闭，复活订阅泄漏）+ 1 项低危（NodeId.hashCode null 不对称）**；结构债在 359 之外仍有 3 个超长方法、5 组重复代码、17 个 ≥800 行类；性能面 360 的收敛只覆盖其 8 条基准路径，**传输/算子/checksum 等未测路径存在 3 个新 P1 候选 + 6 个已登记未实测候选**。

## 关键发现（须修/须测）

### 正确性（回归审查）

| # | 位置 | 问题 | 严重度 |
|---|------|------|--------|
| C1 | `StreamTaskInvokable.java:713-719(invokeSource)/758-763(invokeMiddle)` | finally 中 `closeOutputWriters()` 抛出（EOS 发送失败，plan 358 起 typed 抛出）会跳过其后的 `operatorChain.close()` 与 `closeInputGate()` → 窄路径复活 plan 358 修复的订阅泄漏。兜底不存在：`RunningTask.cancel()`/`TaskManager.stop()` 均不关 gate | **P1 live defect** |
| C2 | `NodeId.java:75-78` | 手写 hashCode（360 R1）对 null 字段 NPE，而 equals(:63-72) null-safe → 畸形/半反序列化 NodeId 下 equals/hashCode 不对称 | P2 低危 |
| C3 | `JobCoordinator` 周期触发路径 | 359 删除 CheckpointCoordinator 自带触发循环后，NO_TASKS_TO_ACK/异常不再喂养 `consecutiveTriggerFailures` 计数器（现仅 GraphModelCheckpointExecutor:809 供给）→ JobCoordinator 模式下持续触发失败的阈值告警变弱（可观测性回归，非正确性） | P3 可观测性 |
| C4 | `SharedBuffer.isEmpty():354-357` | 把全局缓存空性与当前 key 的 state 混判（scoped 缓存保留跨 key 条目）；唯一调用方为 test-only hook | P3 watch-only |
| C5 | RocksDB (key,ns) 前缀缓存 | 用户原地变异可变 key 对象时返回过期编码（违反 key 不可变约定即不成立） | P3 watch-only |

PASS 结论：StreamElementCodec 类缓存（白名单前置每次执行）、JEP290 filter 缓存（属性值键控）、RocksDB 前缀缓存、Memory TypedNamespaceAndKey、RocksDBAggregatingState 前向缓存（clear 无条件失效在位）、SharedBuffer key-scoped（跨 key 隔离/scoped close 免清不漏清/驱逐安全）、ScopedId hash（final 字段）——全部失效语义正确。359 抽查的 InputGate/NFA/StreamTaskInvokable 拆分逐分支等价（side-output hash 探测为经核实的等价优化）。358 的 InputGate.close 关闭链本体、2PC 移出派发线程、EOS fail-fast、JDBC 双检锁全部 PASS。

### 结构/可读性（增量）

- 超长方法（>150 行）：`SupervisionLoop.rebuildTask:592-805`(214 行)、`RemoteGraphExecutionPlanBuilder.buildRemoteOnly:126-334`(209 行)、`MaxParallelismReshardMigration.reshardCheckpoint:96-279`(173 行)、`WindowOperator.open:386-535`(恰 150 行零余量)
- 重复代码：GraphExecutionPlan ↔ RemoteGraphExecutionPlanBuilder 四 helper + fan-out 装配 ~100 行结构性重复；MemoryStateSerDe ↔ RocksDBSnapshotSerDe mapValue 校验循环 + inferAccumulatorType 整段双份；JavaStreamSerializer/JsonToolSerializer 6 样板方法双份（后者唯一缺版权头）；TaskAssignment/TaskStatusReport/TaskDeploymentDescriptor 五元组字段三份手写；WindowOperator merging/regular 骨架同构 ~30 行
- 巨型类：17 个 ≥800 行（359 已知 5 个之外新发现 CepOperator 1258、InputGate 1235、StreamTaskInvokable 1153、NFACompiler 1109、NFA 1104 等）；TaskManager 因 358 新代码逆势 +66 行至 1268
- 注释考古债**再生产**：昨日三计划新引入 37 处 "Plan 358/359/360" 编号注释（15 个文件）；359 top-5 之外的考古注释长尾（TaskManager 33、CheckpointSerDe 32、RocksDBKeyedStateBackend 26 等 top-10）
- 布尔陷阱：JobCoordinator.globalRecovery(boolean)/rotateFencingEpochCoreLocked(boolean)/terminateWithTerminalSavepoint(boolean)、executeWithCheckpointSkeleton(boolean)、restoreAggregatingState(boolean)、getOrCreateState(boolean)、InputGate 三个构造器 barrierAlignment boolean（类内已有 BarrierAlignment 抽象）
- 66 个文件残留中文注释/Javadoc（长尾，建议分批英化，本轮不强制）

### 性能（未测路径 + 新发现）

已登记未实测候选的代码级分析（详见 03-performance-r2.md）：

| 候选 | 关键事实 | 拟建基准 |
|------|---------|---------|
| RemoteResultPartition.write 锁内同步 send | monitor 内 2×JSON 编码 + `FutureHelper.syncGet`（SysDao 后端 ms 级同步写全在锁内）；心跳线程同锁互卡；broadcast 对 T 分区重复 encode T 次 | `RemoteTransportWriteBench`（桩 IMessageService 可参数化延迟，fanout 1/4/16，writer+heartbeat 双线程组） |
| InputGate 50ms 轮询 | poll 信号驱动，饱和流零成本；真实成本=空闲 CPU 与空闲→首记录延迟（多通道 10ms park 不被唤醒） | `InputGateReadLoopBench`（吞吐预期无差异，SampleTime 量化延迟） |
| CepOperator timer 批全扫 | `getSortedTimestamps:1033-1039` 每 timer 批 new PriorityQueue + elementQueueState.keys() 全扫；`snapshotTimersByKey:847-856` 每 watermark 全台账深拷贝；`forEachEventTimeTimer:463-469` 循环内再拷贝；已有 per-key timer 台账（:170）→ 台账驱动索引化**无需状态格式变更**；:910 对 null 桶不设防 | `CepOperatorBench`（memory/rocksdb × keys 1/64 × bucketsPerKey 8/64，processElement+processWatermark 节拍） |
| CheckpointSerDe base64 | 落盘契约是 JSON 文本，byte[] 必须文本编码 → 免 base64 属格式变更，归并 Deferred；**真热点是 checksum**（见下） | 扩展 `CheckpointSerDeBench`（checksum on/off 参数） |
| BufferPool 批量许可 | 公平信号量逐许可 AQS park/unpark 握手 ~1-5µs，仅背压挤压的池边受益；畅通边预期 <2% | `BufferPoolPermitBench`（生产/消费 @Group，批量 1/8/32） |
| ProcessingTimeServiceDriver 固定 100ms tick | timer 触发延迟 0-100ms（均值 50ms）；`nextTimerTimestamp` volatile 已存在 → sleep-to-deadline 基础设施就绪；HeapInternalTimerService 需 shadow-volatile（线程封闭不可跨线程 firstKey） | `ProcessingTimeDriverLatencyBench`（SampleTime 量 fire 时刻−到期时刻） |

新发现（不在 360 已完成/禁止清单内）：

| # | 位置 | 问题 | 严重度 |
|---|------|------|--------|
| F1 | `CheckpointSerDe.java:351-365`（写侧 116/229-230，读侧 157/554-556） | `computeCanonicalChecksumHex`：全文 serialize→parseMap→深拷贝 normalize→再 serialize + SHA-256，随后 serializeCheckpoint:117 又做全文 #3 → 写一次 checkpoint = 全文 3 次序列化 + 1 次解析 + 2 次深拷贝；7.6ms serialize 基线的主体候选。校验值内容寻址，算法变更需版本门控 | **P1** |
| F2 | `WindowOperator.java:1441-1454`（调用点 1363-1428） | evictor 路径 `storeElementTimestamp` 每元素整 List get+add+put；RocksDB 后端每窗口 O(n²)；现有 EVICTOR 基准档仅 Memory 后端测不到 | **P1** |
| F3 | `WindowOperator.java:1371 + 1539-1567` | 兜底 MapState 布局（windowStateDescriptor==null 或后端非 IInternalStateBackend）每元素 get+put 整体 RMW；360 的 RocksDBAggregatingState 缓存不覆盖此路径 | **P1** |
| F4 | `WindowOperator.java:1716-1725 + 2164` | windowNamespace 字符串拼接每状态操作 1-5 次；getSimpleAccumulator stateKey 每 trigger 重建 | P2 |
| F5 | `StreamTaskInvokable.java:876/914/931/935` | 每记录 4 次 CoreMetrics 时钟读（~100-240ns/记录）+ InputGate 每记录 Optional 分配；降频需先复核 G52 liveness 语义 | P2 |
| F6 | `RecordWriter.java:155-167` | broadcast 重复 encode（与候选 1 同根） | P2 |
| F7 | `CepOperator.java:847-856/711/463-469` | 每 watermark 全台账深拷贝 ×2 处（与候选 3 同基准） | P2 |

## 建议归属

- **Plan 2277（Fix）**：C1 关链顺序 + focused 测试；C2 NodeId.hashCode null 防御；C3/C4/C5 裁定（C3 建议恢复计数喂养或显式裁定 watch-only）
- **Plan 2278（可读性/结构，行为保持）**：3 超长方法拆分、5 组去重、注释卫生 R2（37 处新编号注释 + top-10 考古长尾）、布尔陷阱、零风险巨型类抽离切面（static 内部类外移/纯函数协作者/RunningTask 顶层化）；中高风险切面（WindowContentStore/IncrementalCheckpointSupport/LeaderLifecycle/TerminationFlow/InputGate BarrierAligner 等）登记 Deferred + successor 条件
- **Plan 2279（性能 R2，JMH+JFR 迭代至无 ≥2% 收益）**：先建 5 个缺失基准（transport/CepOperator/InputGate/BufferPool/DriverLatency）+ 扩展 2 个既有基准（WindowOperatorProcessElement 加 rocksdb/evictor/fallback 参数、CheckpointSerDe 加 checksum 参数）→ 基线 → 逐轮"归因→优化→实测→留舍"（≥2% 保留、回退 revert）→ JFR 收敛裁定
