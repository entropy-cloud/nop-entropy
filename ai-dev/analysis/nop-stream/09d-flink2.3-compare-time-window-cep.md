# nop-stream vs Flink 2.3.0：时间模型、窗口与 CEP 结构性对比

> Status: resolved
> Date: 2026-09-30
> Scope: `nop-stream-core`（watermark/timestamps/timer service）、`nop-stream-runtime/operators/windowing`（WindowOperator/MergingWindowSet）、`nop-stream-cep`（NFA/NFACompiler/SharedBuffer/CepOperator）
> Conclusion: 窗口与 CEP 的**语义内核与 Flink 2.3.0 高度同构**（同源移植 + 有记录的增强），主要差距集中在三处结构性载体：timer 服务的 key-group 分片缺失、trigger 状态不走 keyed namespace、CEP 自建 timer 台账；窗口侧另有一组 API 级完备性缺口（迟到 side output 无公共入口、ProcessWindowFunction.clear 未接线、缺 PT session 窗口）。质量评分 **3.5/5**。

---

## 0. 校准与方法

- 对比基线：nop-stream HEAD（R4 轮 30+ 缺陷修复后，plan 366 + R5 修复批次在位）vs `~/sources/flink` @ `c0f8d1a1e09`（**release-2.3.0**，2026-09-30 确认）。
- Non-Goals 校准（`ai-dev/design/nop-stream/00-vision.md` §四）：双流 join / 广播状态 / 异步算子 / SQL API / PB 级吞吐**不计为缺陷**；Flink 的两输入算子（`TwoInputStreamOperator`）族、SlotSharing、Netty 栈不在对比面内。已确认缺陷（R4 `01-cep-correctness.md` 8 项、R4 summary 125 项）不重复立案，仅引用编号。
- Flink 2.x 布局定位：窗口算子仍在 `flink-runtime/.../streaming/runtime/operators/windowing/`（`WindowOperator` 1016 行 + `EvictingWindowOperator`）；timer 服务族在 `flink-runtime/.../streaming/api/operators/`（`InternalTimerServiceImpl` + `InternalTimeServiceManager`，heap/RocksDB 统一经 `KeyGroupedInternalPriorityQueue`）；watermark 在 `flink-core/api/common/eventtime/`（`WatermarkOutput`）+ `flink-runtime/.../watermarkstatus/`（`StatusWatermarkValve`）；**CEP 未精简、未改名**，仍在 `flink-libraries/flink-cep`（NFA/SharedBuffer/aftermatch 类集合与 nop-stream 一一对应，且新增了专用 serializer）。
- 方法：逐文件精读两侧 live 代码（WindowOperator 全文、CepOperator 全文、HeapInternalTimerService 全文、MergingWindowSet 全文、TimestampsAndWatermarksOperator 全文；Flink 侧对应文件关键路径），对每个疑点做代码级推演；不改代码。
- 前序对比：`05-window-comparison.md` / `06-cep-comparison.md` 基于 Flink 1.20，本篇为 2.3.0 基线重做 + R4 修复后现状核对，不重复其逐行引用。

## 1. 窗口语义完备性矩阵

| # | 语义维度 | Flink 2.3.0 | nop-stream（HEAD） | 判定 |
|---|---------|-------------|--------------------|------|
| 1 | Tumbling ET/PT | `TumblingEventTimeWindows` / `TumblingProcessingTimeWindows` | 同名类，公式同构（`start = ts - (ts - offset % size)`） | 同构 |
| 2 | Sliding ET/PT | 同上，`assignWindows` 多窗口展开 + stagger | 同构（另有 `WindowStagger`，Beam 概念，加法） | 同构+ |
| 3 | Session（合并） | `EventTimeSessionWindows` + **`ProcessingTimeSessionWindows`** | 仅 `EventTimeSessionWindows`；**无 ProcessingTimeSessionWindows** | **缺口 G-1** |
| 4 | Global | `GlobalWindows`（`NeverTrigger` + `EndOfStreamTrigger`） | `GlobalWindows`（`NeverTrigger`）；`countWindow(size[,slide])` 便捷面在位 | 同构（EndOfStreamTrigger 形态差异极小） |
| 5 | Merging 管线 | `MergingWindowSet`（mapping (W,W) ListState、stateWindow 保留、retireWindow） | 逐行同源移植；merge 越界校验用 `ERR_STREAM_WINDOW_MERGE_INVALID_*`（Flink 为 `UnsupportedOperationException`） | 同构（错误分层更优） |
| 6 | allowedLateness | `cleanupTime = maxTimestamp + allowedLateness`；`isWindowLate`/`isElementLate` 判等公式 | 三者公式逐字符一致（WindowOperator.java:1263-1338 vs Flink:609-682） | 同构 |
| 7 | 迟到 side output | `WindowedStream.sideOutputLateData(OutputTag)` 公共 API | **公共 API 无入口**：`WindowedStream(Impl)` 无 late-data 方法；`WindowOperatorBuilder.lateDataOutputTag()` 为孤儿 setter（runtime 侧，core 无调用链）。默认路径 = 丢弃 + `numLateRecordsDropped` 计数 | **缺口 G-2**（R4 登记未复核 → 本轮确证） |
| 8 | cleanup 语义 | `clearAllState`：windowState.clear + **`processContext.clear()`（→ `ProcessWindowFunction.clear`）** + trigger.clear + retire + persist | `onTimer` cleanup：clearWindowContents + triggerContext.clear + removeTriggerAccumulators + retire + persist；**从不调用 `userFunction.clear`**（`WindowContext.clear()` 存在但全仓无调用点） | **缺口 G-3** |
| 9 | trigger 状态载体 | `getPartitionedState(descriptor)` → keyed state、window namespace，天然随 key-group 路由 | `triggerAccumulators`：operator 本地 `HashMap<String, SimpleAccumulator>`，键为 `"trigger_"+key+"\0"+window+"\0"+name` 复合串；checkpoint 走 operator state | **结构性分歧 S-1** |
| 10 | evictor 写回 | `EvictingWindowOperator`：evictBefore/evictAfter 后 `windowState.update(survivors)`（FLINK-4369） | 单算子 evictor 分支 + `writeBackEvictedWindow`（§17.2 AR-2），element-timestamps side store 索引对齐裁剪 | 同构（写回语义一致） |
| 11 | evictor 计数契约 | `evictAfter` 收到 `Iterables.size(惰性视图)` —— 惰性视图在 evictBefore 迭代删除后重算，**实为 post-evictBefore 计数** | `emitWindowContents` 固定向两个回调传 `preEvictionSize`，注释声称“Flink 语义” | **微分歧 D-1**（仅影响依赖 size 的自定义 evictAfter） |
| 12 | fire 时间语义 | 输出时间戳 = `window.maxTimestamp`（`setAbsoluteTimestamp`） | 一致 | 同构 |
| 13 | 累积模式 | PurgingTrigger 包装 = DISCARDING；非 purging = ACCUMULATING；无 retract | `AccumulationMode` 三值，`ACCUMULATING_AND_RETRACTING` spec-only 快速失败（open() 显式抛） | 等价 + 更显式 |
| 14 | per-window 元信息 | 无（Flink 无 PaneInfo 概念） | `PaneInfo`（EARLY/ON_TIME/LATE + paneIndex + isLast 恒 false），checkpoint 参与（TimeWindow-scoped） | 加法特性（Beam 风格），`isLast` 不可行性已裁定 |
| 15 | 兼容性检查 | 无（作业图即契约） | `WindowingStrategy` 可序列化模型 + savepoint 兼容检查（§14），参与 fingerprint | 加法特性 |
| 16 | DISCARDING×merging 清除键 | stateWindow namespace（Flink `windowState.setCurrentNamespace(stateWindow)` 天然对齐） | `clearWindowContents(key, actualWindow, stateWindow)` 双键显式化（§17.4/17.6 已修） | 同构（修复在位） |

**维度一结论**：窗口语义面是 nop-stream 对 Flink 还原度最高的部分——四要素模型、merging 管线、迟到判定公式、cleanup 公式、evictor 写回（FLINK-4369 等价物）全部对齐，且在错误分层（ErrorCode 体系）、merge 校验、pane 跟踪上做了有记录的增强。剩余差距集中在**API 级完备性**（G-1/G-2/G-3），均为低成本可闭合项；S-1（trigger 状态载体）是唯一的结构性分歧，其代价见维度三。

## 2. 时间与水位

| 维度 | Flink 2.3.0 | nop-stream | 判定 |
|------|-------------|-----------|------|
| 策略抽象 | `WatermarkStrategy`（`flink-core/api/common/eventtime`） | 同名同构移植（forMonotonousTimestamps / forBoundedOutOfOrderness / withIdleness / withTimestampAssigner） | 同构 |
| 生成算子 | `TimestampsAndWatermarksOperator`：`processWatermark` **只发 MAX_WATERMARK**（finish 路径），`processWatermarkStatus` 显式 no-op；上游 watermark 完全忽略 | 同名算子：周期 timer + 逐元素限频发射双驱动；`processWatermark` **转发所有推进中的上游 watermark**（TWO 源混合）；`watermarkInterval=0` 退化为逐事件发射（Flink 无此档） | **分歧 D-2**（单调性有保证、无回退，但中游插入时生成水位可被上游水位抢跑） |
| 空闲传播 | `WatermarkStatus` 经 channel → `StatusWatermarkValve`（per-channel idle、active-min 合并、idle 通道水位不驱动） | plan 1326-2 Phase 4 四段接线在位：operator 侧状态转移发射 + `RecordWriter.emitWatermarkStatus` 广播 + `InputGate.channelIdle[]` 按 active 集 min 合并（InputGate.java:532-620/1179-1258） | 同构 |
| watermark 对齐 | `WatermarkAlignmentParams` + coordinator 强制暂停 source | 接口存在（`withWatermarkAlignment` / `WatermarksWithWatermarkAlignment`），无 Coordinator 运行时 | 已文档化的定位外裁剪（time-model-design §9.2） |
| 多 split 合并 | source reader 内 `WatermarkOutputMultiplexer` 常驻 | 类存在且 N-capable，**未接入执行路径**（仅单测） | 已文档化（§9.4，defer 至 source split） |
| 两输入合并 | `TwoInput` 算子族 + valve | `IndexedCombinedWatermarkStatus` 单测级 dormant（G47 裁定，Anti-Hollow 豁免） | 定位外（Non-Goals：无两输入算子） |
| processWatermarkStatus 死代码 | — | plan 366 回归确认：`processWatermarkStatus1/2` 全仓 0 残留（R4 00 号报告复核在位） | 现状干净 |
| timer 中断 | `tryAdvanceWatermark(watermark, ShouldStopAdvancingFn)` — 长计时器风暴可被打断 | `advanceWatermark` 无中断钩子（单线程跑完整个 due 队列） | 微分歧（规模大时 timer 风暴会占住 mailbox；与 mailbox 设计取舍相关） |
| 卫生 | — | `processElement` 内空 if/else-if 死分支（TimestampsAndWatermarksOperator.java:120-122）+ `elementsSinceLastEmit` 写而不读 | **卫生项 T-1** |

**维度二结论**：水位语义面（生成策略、单调性、idleness 跨任务传播、active-min 合并）与 Flink 等价；plan 366 死代码删除与 idleness 接线均已核实**在位**。剩余为 D-2（上游 watermark 混合转发）这一处可低成本对齐的行为分歧，以及对齐/多 split 两项已文档化的定位外裁剪。

## 3. Timer 服务

| 维度 | Flink 2.3.0 | nop-stream | 判定 |
|------|-------------|-----------|------|
| 数据结构 | `KeyGroupedInternalPriorityQueue<TimerHeapInternalTimer>`（heap 或 RocksDB 后端可选），per (key,namespace,timestamp) 去重 | 单 `TreeMap<Long, Set<TimerEntry<K,N>>>` ×2（ET/PT），Set 去重等价 | 去重同构；**无 key-group 分片 S-2** |
| key-group | `localKeyGroupRange` 分片；`snapshotTimersForKeyGroup`/`restoreTimersForKeyGroup`；恢复时校验 key-group 属主 | snapshot 为整表 `TimerSnapshot` DTO，operator state 承载；restore 整表插回 + AR-22 键重物化（JSON 漂移防御） | **S-2**：rescale 场景 timer 不随 key-group 重新路由（与 R4 P0 AR-01 路由家族同源，恢复正确性依赖并行度不变或离线 reshard） |
| checkpoint 载体 | raw keyed state（key-group 流），与 keyed state 同生命周期 | operator state（`internal-timers`，JSON-safe form）+ restore 延迟应用到 open() | 载体差异（可用，但恢复规模与 rescale 语义弱于 Flink） |
| PT 驱动 | `ProcessingTimeService`（任务线程 mailbox 安全点回调） | `TaskProcessingTimeService` + `ProcessingTimeServiceDriver`（daemon 线程只投递 mail、task 线程安全点执行）+ `TimerServiceManager`；volatile `nextProcessingTimeTimer` 跨线程只读契约 | 同构（跨线程协议与 Flink mailbox 思想一致） |
| 规模上限 | RocksDB timer：亿级 timer 可外溢磁盘 | 纯内存 TreeMap：数十万~百万级 timer 量级为实际上限（vision Non-Goal「PB 级吞吐」之下可接受） | 定位内接受，但应作为容量声明写入文档 |
| 窗口接线 | `InternalTimeServiceManager.getInternalTimerService(name, keySer, nsSer, triggerable)` 按名注册多服务 | `HeapInternalTimerService` 直接构造 + `setKeyType(keyClass)` + `timeServiceManager.registerTimerService`；restore-before-open 延迟应用模式 | 等价（少一层多服务注册名；单服务够用） |

**维度三结论**：功能面（ET/PT 定时器、去重、checkpoint/restore、watermark 驱动 fire、AR-22 键重物化）完整且恢复语义正确；差距是**规模与 rescale**——Flink 的 key-group 分片使 timer 状态可以随并行度重路由、可外溢 RocksDB；nop-stream 的整表 operator-state 载体把这两项能力锁死在“并行度不变恢复”的边界内。在 vision 声明的规模目标（几十 GB 状态、非 PB 吞吐）下，S-2 属**记录在案的定位内裁剪**而非缺陷，但它是 nop-stream 时间体系与 Flink 最大的单点结构性差距。

## 4. CEP

### 4.1 内核同构性

| 组件 | Flink 2.3.0（`flink-libraries/flink-cep`） | nop-stream-cep | 判定 |
|------|------------------------------------------|----------------|------|
| Pattern DSL / 量词 | `Pattern`/`Quantifier`/`GroupPattern`/`WithinType` | 同名同集合（745 行 vs Flink 同构） | 同构 |
| skip 策略 | aftermatch/ 8 类（NoSkip/SkipPastLast/SkipToFirst/SkipToLast/SkipToElement/SkipToNext/SkipRelativeToWholeMatch + 基类） | **逐类同名** 8 类 | 同构 |
| NFA | `NFA.process`/`advanceTime`（Tuple2 返回 pending+timedOut）、Dewey 版本、greedy/until 编译 | 同源移植；nop 增加 `getWindowTimes()` per-state 窗口表 + STEP5 空闲重置（见 4.3） | 同构+ |
| SharedBuffer | `SharedBuffer` + `SharedBufferAccessor` + Lockable 引用计数 + Guava cache（eventsBuffer/entryCache）；**专用 serializer**（`SharedBufferNodeSerializer`/`LockableTypeSerializerSnapshot`/`NFAStateSerializer`） | 同构结构；Guava cache + `ScopedId(scope=key)` 按 key 隔离 + `flushCache` write-back；序列化走 `JavaStreamSerializer`（byte[] 经 JSON 快照） | 内核同构；serde 面 Flink 更严谨（专用 serializer + snapshot 兼容层） |
| 释放对称性 | — | 不变式 #4 + `TestCepReleaseSymmetryInvariant` CI 门禁（R4 复核：desync 修复在位、over-release fail-fast 在位） | nop 有显式门禁（Flink 无对应测试形态） |
| keyed 隔离/恢复 | `computationStates`/`elementQueueState`/SharedBuffer 均 keyed state；timer 走 runtime key-group 服务 | 三态 keyed + 水位/timer 台账/键类三个 operator state；恢复链 `restoreState → open` 时序钉死（TestCepCheckpointRestoreE2E） | 同构（载体差异同 S-2 家族） |

### 4.2 timer/排水路径对比（plan 2279 / CEP-01 修复后现状）

- **Flink**：bucket 排水的登记簿**就是 runtime timer 本身**——`registerTimer` 注册 (key, VoidNamespace, ts) 定时器；`onEventTime(InternalTimer)` 被 runtime 唤醒时 key 上下文已恢复，排水候选来自 `getSortedTimestamps()`（直接迭代 `elementQueueState.keys()`，CepOperator.java:294-330）。
- **nop-stream**：`InternalTimerService` 为匿名实现，ET 定时器**不进** runtime 服务而是写入 per-key 台账（`registeredEventTimeTimersByKey: Map<Object, TreeSet<Long>>`）；`processWatermark` 活迭代台账收集 due keys → 逐 key `setCurrentKey → onEventTime`。**CEP-01 已修复**（本 HEAD 核实在位）：PT 分支 `registerTimer` 现在同步 `registerEventTimeTimerForKey` 写台账（CepOperator.java:881-898），`onProcessingTime` STEP6 补了对称清理（:1087-1096），“每桶必有台账项”超集不变式在 PT/ET 两模式均成立；回归钉子 `TestCepOperatorProcessingTimeBucketDrain`。
- **评价**：nop 的台账本质是“runtime timer 缺位下的替代登记簿 + 性能优化（免扫全桶）”，代价是**第二份必须与 `elementQueueState` 保持超集一致的簿记结构**——CEP-01 正是该一致性断链的产物。Flink 的“timer 即台账”设计少一份状态、天然恢复。这是**过度设计候选 O-1**（详见 §6），但因 nop 已投入台账 + 键类携带 + reconcile 三件套且有测试保护，**不建议现在回退**；建议以不变式断言（排水后 `ledger ∩ queue.keys() == ∅` 或超集检查）把一致性钉进 CI 而非增加新机制。

### 4.3 逐项生产级细节覆盖核对

| Flink CEP 生产级细节 | nop-stream 覆盖情况 |
|---------------------|--------------------|
| 超时语义（`within` 双时钟：startTimestamp + previousTimestamp 两级判定，`>=` 截止边界） | 同构（advanceTime 双 `isStateTimedOut` 分支逐行对应；R4 复核 STEP2/3/5 顺序与边界正确） |
| 超时部分匹配回调 | 同构（`TimedOutPartialMatchHandler` + `PatternTimeoutFunction` 适配器族） |
| until 打破 NOT 累积（`originalStateMap` 副本旁路） | 同构移植；同源缺陷 R5-CEP-02（greedy+optional+until null-target PROCEED → NPE）**已修复**——`originalStateMap` 读取 miss 回退 `proceedState`（NFACompiler.java:766-769，本轮核实在位）；同批 R5-CEP-08（times 非法参数抛 `MalformedPatternException`）亦已落地 |
| Dewey 引用计数 GC / release 对称性 | 同构 + CI 门禁（§4.1） |
| cache-only 驱逐安全（`advanceTime` 只驱逐 cache、引用计数不经 cache 生存期） | 同构（R4 §排查项 7 复核在位） |
| 比较器乱序缓冲（同 ts 稳定排序） | 同构（`StreamRecordComparator` + ledger 升序跨桶） |
| NFA 状态序列化 | Flink 走专用 serializer + snapshot 兼容层；nop 走 Java serialization + 状态名确定性（`NFAStateNameHandler`）+ 漂移 fail-fast（`ERR_CEP_NFA_*_STATE_CHECK_FAILED`）——等价可用，跨版本兼容策略不同 |
| start 态残留清理 | Flink 仅剩 start 态即 `computationStates.clear()`（无条件）；nop `resetNfaStateIfFullyTimedOut` 增加 windowTime 门控——**无 within() 的 pattern 永不清除** per-key start 态 NFAState（**微分歧 D-3**：语义无害——start 态无 SharedBuffer 条目；代价是每活跃 key 恒持一个空 NFAState，量级 = key 数） |
| 指标粒度 | Flink per-task metrics 天然隔离；nop R5-CEP-03 已修（TaskLocation tag 三通道），本轮核实 tags 在位（CepOperator.java:415-418）——已修复 |

### 4.4 Flink 2.x CEP 自身维护状态

flink-cep 在 2.3.0 **未被裁剪或改名**，仍在 `flink-libraries/`，且 2.x 周期为它补齐了专用状态序列化（`NFAStateSerializer`、`SharedBufferNodeSerializerSnapshotV2`）——说明 Flink 官方仍视 CEP 为活组件而非弃子。nop-stream 的移植基线与 2.3.0 结构一致，不存在“Flink 已简化、nop 背了历史包袱”的错位；nop 侧独有的扩展（per-state 窗口表、空闲重置、键类恢复通道、台账 reconcile）均有设计文档记录。

**维度四结论**：CEP 内核（NFA 编译/匹配/Dewey/SharedBuffer 引用计数/skip 家族/超时语义）与 Flink 2.3.0 **逐类同构**，且在恢复正确性（键类携带 AR-10/AR-11）与释放对称性门禁上强于 Flink 的裸实现。R4 CEP 审计 8 项中 4 项（CEP-01/02/03/08，含全部 P1/P2）经本轮代码核实已修复在位（与 plan 368 Phase 5/6/7 日志互证）；仍开放的为 4 项 P3（CEP-04 cache timer 关闭竞态、CEP-05 nfaFactory 跨子任务共享、CEP-06 DeweyNumber 字符串往返比较器、CEP-07 Lockable 相等性含引用计数）。时间/窗口/CEP 主题本轮未发现新的 P1/P2 正确性缺陷。

## 5. 差距清单

### 5.1 定位内缺陷（本轮新发现，均窗口/水位面；CEP 面无新增）

| 编号 | 位置 | 问题 | 严重度 |
|------|------|------|--------|
| G-3 | `WindowOperator.onTimer` cleanup 分支（:894-914）+ purge 分支（:788-797） | 从不调用 `processContext.clear()` → `InternalWindowFunction.clear` / `ProcessWindowFunction.clear` 死接口：用户经 `ctx.windowState()`（`PerWindowKeyedStateStore`，namespace=windowNamespace(window)）写入的 per-window 状态在窗口 cleanup/merge 时**永不清理**，随 checkpoint 永久泄漏。Flink `clearAllState`（EvictingWindowOperator.java:417-426 / WindowOperator 同名）显式调用 `processContext.clear()` | P2（需用户使用 windowState() 才触发，但接口是公开扩展面） |
| G-2 | `WindowedStream`/`WindowedStreamImpl`（core）无任何 late-data 方法；`WindowOperatorBuilder.lateDataOutputTag()`（runtime:101-102）无公共调用链 | 迟到 side output（Flink `sideOutputLateData`）**不可达**：所有迟到记录走“丢弃 + 计数”。R4 登记的“withLateDataOutputTag 孤儿（未复核）”本轮确证为孤儿 setter | P2（语义完备性缺口：迟到数据抢救/审计能力缺失） |
| G-1 | `nop-stream-core/windowing/assigners/` | 缺 `ProcessingTimeSessionWindows`（session 合并仅事件时间）；Flink 同名类在位 | P3（能力缺口，PT session 有真实用例：无事件时间戳源的会话切分） |
| W-M1 | `WindowOperator.open():412-413` | `numLateRecordsDropped` 仍注册为 JVM 级**无 tag** counter——R5-CEP-03 同族缺陷的窗口侧（plan 368 Phase 7 已登记为移交项"WindowOperator:412 同型无 tag 指标注册，后续收口"；本轮复核仍在位）。修复方案照抄 CepOperator.java:415-418 即可 | P3（已登记移交项，非新发现） |
| T-1 | `TimestampsAndWatermarksOperator.processElement:120-122` | 空 if/else-if 死分支（两支均空语句）+ `elementsSinceLastEmit` 写而不读（:41/75/109/125） | P3（卫生） |

### 5.2 定位外裁剪（有裁定记录，不计缺陷）

- watermark 对齐 Coordinator（time-model-design §9.2）、`WatermarkOutputMultiplexer` source 侧接线（§9.4）、两输入 watermark 合并运行时生效（§5.4 G47 Anti-Hollow 豁免）。
- timer 服务 RocksDB 外溢与 key-group 分片（S-2）——vision 规模目标内可接受；建议在 docs-for-ai 模块文档补“timer 数量级上限”容量声明。
- `ACCUMULATING_AND_RETRACTING` spec-only 快速失败（window-design §6，无 retract 下游消费者）。

### 5.3 低成本可采纳（Flink 机制，收益/成本比高）

| # | 机制 | 动作 | 预估成本 |
|---|------|------|---------|
| A-1 | Flink `clearAllState` 的 `processContext.clear()` | WindowOperator 在 cleanup 分支与 merge-clear 循环中调用 `processContext.clear()`（`WindowContext.clear()` 已存在，一行接线 ×2~3 处）+ 回归测试（PerWindowKeyedStateStore 条目随清理消失） | 小（<0.5 天） |
| A-2 | Flink `WindowedStream.sideOutputLateData` | `WindowedStream(Impl)` 补一个方法，直通既有 `WindowOperatorBuilder.lateDataOutputTag`（孤儿 setter 转正）；round-trip 门禁 `TestWindowRoundTripInvariant` 加参数位 | 小（1 天内） |
| A-3 | CepOperator 同款 metric tag 方案 | `WindowOperator` 的 counter 改为带 `operator/subtask` tag 注册（照抄 CepOperator.java:415-418） | 极小 |
| A-4 | Flink `TimestampsAndWatermarksOperator` 的“忽略上游 watermark”语义 | `processWatermark` 改回仅透传 `MAX_WATERMARK`（或加开关 + 文档化现行为），消除 D-2 双源混合；顺带清 T-1 死分支 | 极小 |
| A-5 | Flink `tryAdvanceWatermark` 的中断钩子 | `advanceWatermark` 增加可选 `ShouldStopAdvancing` 谓词（mailbox 取消场景），非必须 | 中（可 defer） |
| A-6 | `ProcessingTimeSessionWindows` | 照 `EventTimeSessionWindows` 移植（merge 逻辑同构，仅时钟源不同） | 中（含测试 1-2 天） |

### 5.4 过度设计（结构与收益不匹配的项）

| # | 项 | 说明 | 建议 |
|---|----|------|-----|
| O-1 | CEP per-key timer 台账（`registeredEventTimeTimersByKey` + reconcile + 键类携带三件套） | 对 Flink“timer 即台账”的单结构设计而言，nop 为免扫 `elementQueueState.keys()` 引入了第二份簿记——其一致性断链正是已修的 CEP-01；台账键的重物化（AR-11 JSON 往返）又引入 legacy-form 回退分支。收益是 watermark 排水免全桶扫描（plan 2279 实测）与 PT 排水有界；成本是一致性维护面 | **不回退**（已投入+测试保护）；改以 CI 不变式断言钉死“台账 ⊇ 桶集合、排水后账实一致”，防止第三次断链 |
| O-2 | `WindowingStrategy`（Beam 风格模型 + fingerprint + savepoint 兼容检查） | Flink 无对应物；为“策略可序列化对比”支付了模型/注册表/检查器三层成本，而 `allowedLateness` 之外多数字段（closingBehavior/onTimeBehavior/outputTime）在 live WindowOperator 无消费者 | 冻结扩展面；若 fingerprint 之外无新消费场景，考虑在文档标注 spec-only 字段清单（与 AccumulationMode 同待遇） |
| O-3 | `PaneInfo.isLast` | 恒 false 的占位字段（§13.3 已裁定不可行） | 保留裁定，javadoc 已声明；无需动作 |

## 6. 质量评分与理由

**评分：3.5 / 5**（定位内窗口/时间/CEP 子系统综合）

理由：

1. **语义内核还原度高（+）**：窗口四要素、merging 管线、迟到/清理公式、evictor 写回（FLINK-4369 等价）、CEP NFA/SharedBuffer/skip 全家族与 Flink 2.3.0 逐类同构；R4 修复（CEP-01 台账、R5-CEP-03 指标 tag、plan 366 水位接线）经本轮代码级核实全部真实在位，未发现修复回退。
2. **多处强于 Flink 裸实现（+）**：错误分层（ErrorCode 体系 vs `UnsupportedOperationException`）、释放对称性 CI 门禁、键类恢复通道、merge 越界显式错误码、pane 跟踪与 savepoint 兼容检查。
3. **结构性载体差距（−）**：timer 无 key-group 分片（S-2）与 trigger 状态不走 keyed namespace（S-1）共同决定了 rescale 场景的时间类状态弱于 Flink（后者由 AR-01 家族统一承载）；CEP 台账双簿记（O-1）持续支付一致性维护成本。
4. **API 级完备性缺口未闭合（−）**：G-2（迟到 side output 无公共入口）使 Flink 的一等公民能力在 nop 不可达；G-3（`ProcessWindowFunction.clear` 死接口）让 per-window 用户状态泄漏——两者都是窗口语义“看起来支持、实际半接线”的形态，与 No-Silent 原则相悖。
5. **未发现新的 P0/P1**：本主题（时间/窗口/CEP）在 R4 修复后的 HEAD 上，语义正确性层面无新增高危缺陷；本轮新发现为 G-3（P2）、G-2（P2）、G-1 与 T-1（P3）；W-M1 为 plan 368 已登记移交项的本轮复核确认。

## References

- nop-stream：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java`、`MergingWindowSet.java`、`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/HeapInternalTimerService.java`、`TimestampsAndWatermarksOperator.java`、`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java`、`nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java`
- Flink 2.3.0：`flink-runtime/src/main/java/org/apache/flink/streaming/runtime/operators/windowing/{WindowOperator,EvictingWindowOperator,MergingWindowSet}.java`、`flink-runtime/src/main/java/org/apache/flink/streaming/api/operators/{InternalTimerServiceImpl,InternalTimeServiceManager}.java`、`flink-runtime/src/main/java/org/apache/flink/streaming/runtime/watermarkstatus/StatusWatermarkValve.java`、`flink-runtime/src/main/java/org/apache/flink/streaming/runtime/operators/TimestampsAndWatermarksOperator.java`、`flink-core/src/main/java/org/apache/flink/api/common/eventtime/`、`flink-libraries/flink-cep/src/main/java/org/apache/flink/cep/`
- 设计文档：`ai-dev/design/nop-stream/00-vision.md`（Non-Goals 校准）、`window-design.md`、`time-model-design.md`、`cep-design.md`
- 审计基线：`ai-dev/audits/2026-09/2026-09-30-0530-deep-audit-nop-stream-quality-r4/01-cep-correctness.md`（已确认缺陷，勿重复）、`summary.md`
- 前序对比（Flink 1.20 基线）：`ai-dev/analysis/nop-stream/05-window-comparison.md`、`06-cep-comparison.md`
