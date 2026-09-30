# nop-stream vs Flink 2.3.0 — 状态后端与序列化架构对比

> Status: open
> Date: 2026-09-30
> Scope: `nop-stream/nop-stream-core/.../common/state/`（memory 后端、shard/key-group、TTL、operator state）、`nop-stream/nop-stream-rocksdb/`（RocksDB 后端、增量快照）；对照 Flink `release-2.3.0`（`~/sources/flink`，commit `c0f8d1a1e09`）的 `flink-runtime` state 包与 `flink-state-backends/`
> Conclusion: key-group 模型与 nop-stream 语义等价且稳定 hash 契约更强（框架侧强制 vs Flink 用户侧约定）；JSON 中心序列化在定位内成立，但代价已具体化为「restore 路径修复层」（R4 发现集中于此，多数已修）；RocksDB 后端为最小可用面（缺共享内存管控/写批处理/调优预设，增量快照有 ST-18 fail-fast 限制）；**operator state 重分布后端能力已交付但生产 rescale 路径未接线（本次新发现）**。定位内质量评分 **4/5**。

## Context

- 本系列 04 号文档（`04-state-comparison.md`）对照的是 Flink `release-1.20`。本文按 Flink 2.3.0（2.x 布局）重做状态后端与序列化维度的结构性对比，并纳入 nop-stream R4 轮 30+ 缺陷修复后的 HEAD 现状。
- 校准基线：`ai-dev/design/nop-stream/00-vision.md`（Non-Goals：不复制二进制序列化体系、不做 PB 级吞吐、几十 GB 状态级、不做在线 reshard、BroadcastState 永久排除 G36）与 `ai-dev/design/nop-stream/state-management-design.md`（§3 key-group、§6 序列化策略、§10 operator state、§12 TTL）。
- 已确认缺陷不重复立项，只引现状：`ai-dev/audits/2026-09/2026-09-30-0530-deep-audit-nop-stream-quality-r4/02-state-backend-serialization.md`（R5-ST-01..18）、`.../00-plan366-regression.md`（R5-REG-01..07）。经 HEAD 核验，其中 ST-01（PendingCheckpoint abort CAS）、ST-02（key 重材料化）、ST-03（Enum identity hash）、ST-04（MapState 快照分组键歧义）已修复；ST-05/08/09/10/11/12/13/18 仍 open，归入差距清单。
- Flink 2.3.0 关键布局变化（相对 1.20）：`flink-state-backends/` 拆为 `flink-statebackend-{common,heap-spillable,rocksdb,forst,changelog}`；新增 ForSt 后端（2.x 主推，async state v2 API 集成，`ForStDBTtlCompactFiltersManager` 原生 TTL compaction filter）；新增 heap-spillable（`CopyOnWriteSkipListStateMap`，堆状态可溢出盘）；增量 checkpoint SST 共享新增 CLAIM/FORWARD `SharingFilesStrategy`。核心 key-group 公式与 1.x 完全一致（源码核验）。

## 0. 机制对照表

| 机制 | Flink 2.3.0 | nop-stream（HEAD） | 语义等价性 |
|---|---|---|---|
| key→group | `murmurHash(key.hashCode()) % maxParallelism`（`KeyGroupRangeAssignment.computeKeyGroupForKeyHash` :75-77） | `(stableHash(key) & 0x7FFFFFFF) % maxParallelism`；stableHash：Enum 按 `name().hashCode()`（ST-03 修复）、JDK 值类型直接 `hashCode()`、其余 Murmur3 over canonical JSON（`KeyGroupAssignment.stableHash` :66-92） | **等价**（同为稳定 hash→取模）；nop 的稳定性契约由框架强制，Flink 由用户类型契约保证 |
| hash 加扰 | 一律经 `MathUtils.murmurHash` 加扰后再取模（对弱 hashCode 兜底） | JDK 值类型直用 `hashCode()`，无加扰 | 近似；nop 对 String/Integer 等规范 hash 够用，弱分布模式风险略高 |
| maxParallelism 默认 | `max(roundUpToPowerOf2(P*1.5), 128)`，上界 32768（`computeDefaultMaxParallelism`） | job-global 固定默认 128（`KeyGroup.DEFAULT_MAX_PARALLELISM` :41），无上界校验 | 等价（默认值同为 128）；Flink 公式为大集群 scale-up 余量设计，nop 定位内不必要 |
| group→subtask | `keyGroupId * parallelism / maxParallelism`，O(1) | 连续均匀切分 + O(P) 逆查循环（`assignKeyGroupToSubtask` :201-207） | 等价（边界分组略有差异，各自内部自洽）；性能 O(1) vs O(P) |
| 路由/属主统一 | 单一公式天然统一（`assignKeyToParallelOperator`） | AR-01（plan 368）：keyBy 路由改走 `assignToSubtask`（`DataStreamImpl` :442），与属主判定同公式 | 等价；nop 曾有双公式缺陷，现已收敛 |
| 区间切分 | `start=ceil(i*M/P)`，`end=ceil(((i+1)*M-1)/P)` | `start=i*base+min(i,rem)`，首 rem 个 subtask 多一组 | 等价（同为均匀连续切分，边界差 ±1 group） |
| heap 后端 | `HeapKeyedStateBackend` + `CopyOnWriteStateMap`（写时深拷贝，快照一致） | `MemoryKeyedStateBackend`：对象引用直存，零序列化、零拷贝 | 行为差异：nop 用户改动状态对象会污染状态（已登记限制 §11.3）；本地模式下 nop live 路径更快 |
| RocksDB 后端 | `EmbeddedRocksDBStateBackend`（+ 2.x 主推 `ForSt`）；共享 memory controller、`RocksDBWriteBatchWrapper`、PredefinedOptions/OptionsFactory | `RocksDBKeyedStateBackend`：每 state 一 CF、key layout v2 `[group:i32BE][nsLen][ns][keyLen][key]` 可排序前缀；`RocksDBOptionConfig` 仅 2 个旋钮 | 形态等价（CF-per-state + 复合键）；调优面与写入路径优化差距大 |
| 快照（全量） | 异步 snapshot + stream factory 上传 | 全量扫描 → `MemoryStateSerDe`/`RocksDBSnapshotSerDe` JSON（跨后端 byte-compatible） | 等价（全量语义）；nop 为同步扫描 |
| 快照（增量） | native checkpoint 硬链 SST → 只上传上次完成 checkpoint 以来新增 SST → `SharedStateRegistry` 引用计数，2.x 加 CLAIM/FORWARD 共享策略 | native `Checkpoint` → SHA-256 内容寻址 `SharedStateHandle` → coordinator 从 task-local 路径拷入 `ISegmentStore` → `SharedStateRegistryImpl` 引用计数；restore 需 segment store（fail-fast，ST-18 限制） | 语义等价（引用计数 + 内容寻址）；nop 依赖 task-local 绝对路径，生命周期时序弱于 Flink（Flink 持久层即刻上传，本地目录仅为机会性缓存） |
| operator state 重分布 | `getListState`(SPLIT_DISTRIBUTE)/`getUnionListState`(UNION)/`getBroadcastState`(BROADCAST)；restore 时 `RoundRobinOperatorStateRepartitioner` **总是执行**，UNION/BROADCAST 并集发给所有实例（:277-286） | `RedistributionMode.{NONE,UNION,BROADCAST,SPLIT_DISTRIBUTE}`，由 restore 调用方注入模式；UNION 合并列表、SPLIT_DISTRIBUTE round-robin stride、BROADCAST 取**首个非空快照** | SPLIT_DISTRIBUTE/UNION 等价；BROADCAST 弱于 Flink（非并集）；**生产 rescale 路径（`RescaleStateAssembler.buildRescaledTaskState` :125-145）1:1 按索引取 operator state、扩容 subtask 置空——4-mode 后端能力无生产调用方（本次新发现）** |
| keyed 状态 schema 演进 | `TypeSerializerSnapshot` 全套（per-serializer 指纹 + 四态兼容解析） | `SerializerFingerprint`（type-signature SHA-256）+ `StateMigrationFunction`/`StateMigrationRegistry`，getState() 时比对、不兼容 fail-fast | 目标等价（不兼容可检测、可迁移）；粒度粗于 Flink（类签名级 vs 字段级） |
| TTL 配置 | `StateTtlConfig`：OnCreateAndWrite/OnReadAndWrite；清理策略 FULL_STATE_SCAN_SNAPSHOT（默认）/INCREMENTAL_CLEANUP/ROCKSDB_COMPACTION_FILTER；仅 processing time | `StateTtlConfig`：同两组 updateType；清理 = lazy eviction + snapshot 排除 + RocksDB sweep（仅 snapshot 时）；仅 processing time；`DISABLED` 哨兵 | 语义子集等价（默认档对齐 Flink 默认档）；缺 per-entry MapState 粒度、增量清理与原生 compaction filter |
| TTL 时间戳载体 | `TtlValue<T>` 包装值 | sidecar `TtlContext<K>` 时间戳 map，与值分离（不包装，accumulator 类型不破坏） | 实现相反、语义等价；nop 选择由 accumulator 复用与 JSON serde 分发约束驱动 |

## 1. key-group 模型

**结论：语义等价，nop-stream 的稳定性契约更强，Flink 的哈希质量与查询效率略优；两者均已收口到「路由=属主」单一公式。**

- **稳定性契约对比（结构性差异）**。Flink 的 `assignToKeyGroup` 直接采信 `key.hashCode()`，跨 JVM 稳定性是**用户文档契约**（key 的 hashCode/equals 必须基于值；违反则路由漂移，框架不拦）。nop-stream 经 G38/ST-03 演进后是**框架侧强制**：白名单值类型直接用 spec-stable 的 `hashCode()`，Enum 强制走 `name()`（Enum.hashCode 是 identity hash，Flink 同样会踩，但把责任推给用户），@DataBean 走 canonical JSON + Murmur3，不可序列化 key 才回退 identity hash 并 WARN（`KeyGroupAssignment.stableHash` :78-91）。在「key 可 JSON 序列化」是平台前提的定位下，nop 的契约覆盖面**大于** Flink，代价是每个非原始类型 key 的路由都要付出一次 JSON 序列化 + murmur3（keyBy 逐记录路径上，热路径成本真实存在）。
- **公式核对**。`keyGroupId % maxParallelism` 与均匀区间切分两套公式在两侧各自内部自洽、语义等价；边界分布差 ±1 个 group（Flink ceil 式 vs nop 首附余式），不影响任何语义。Flink 2.x 未改变该公式（源码核验，任务描述中「Flink 2.x 按新策略分配」未体现在 2.3.0 的 `KeyGroupRangeAssignment` 中）。
- **AR-01 的教训价值**。Flink 从第一天起路由与属主就是同一公式；nop-stream 曾双公式并存（路由 `(hashCode&M)%P` vs 属主 stableHash），仅在 `maxParallelism==parallelism` 时重合，rescale 恢复后出现幻影状态。plan 368 用 `assignToSubtask` 收敛（`DataStreamImpl` :442）。这印证了 key-group 模型里**不变量必须单点实现**——两侧现在的形态一致：稳定 hash → group → 连续区间属主。
- **残余差距（低危）**：
  1. nop 无 murmur 加扰：Flink 对一切 hash 先 `MathUtils.murmurHash` 加扰，弱 hashCode（如 `Short`/`BigDecimal` 的稀疏 hash）也能均匀摊开；nop 对白名单类型直用原始 hash。一行可补（见低成本清单）。
  2. `assignKeyGroupToSubtask` 是 O(P) 逆查循环（:201-207），逐记录路由每次调用；Flink 是 O(1) 闭式 `keyGroupId * parallelism / maxParallelism`。P 小时可忽略，但存在闭式解。
  3. nop 的 maxParallelism 无上界校验（Flink 上界 32768，防区间公式舍入问题）；nop 区间公式用 int 乘法，`maxParallelism` 极大时 `subtaskIndex * base` 理论可溢出——当前默认 128、定位内不触发，属锐利边缘。

## 2. 序列化体系

**结论：JSON 中心方案在定位内成立（收益真实）；但「JSON 免费灵活性」的代价不是消失了，而是变形为 restore 路径的修复层代码——R4 全部 P1/P2 状态缺陷恰好都落在这条接缝上，这就是该取舍的实测价格。性能差距仅部分被定位豁免。**

- **收益（Flink 反面教材的验证）**。`state-management-design.md` §6.1 的论断在 2.3.0 源码上仍然成立：Flink 的 `TypeSerializer`/`TypeInformation`/`TypeSerializerSnapshot` 体系（`flink-core` typeutils 数十个 serializer 类 + 每容器专用实现 + snapshot 兼容四态解析）渗透到几乎所有模块，且要求每个 `StateDescriptor` 绑 serializer。nop-stream 用 `JsonTool` 反射一行替代，业务对象零污染，`StateDescriptor` 只持 `Class<T>`。对于「JSON 是平台通用语、模型优先」的 Nop 定位，拒绝该体系是正确决策——Flink 自己也在 2.x 付出代价（async state v2 API 又造了一套并行状态接口）。
- **代价的实测形态（结构观察，非重复立项）**。JSON 往返改变运行时类型（Long→Integer、Date→字段 map、bean→LinkedHashMap），nop 不得不逐点重建 Flink「写时就确定性」的保证：
  - `StateKeyRematerializer`（ST-02 修复）：restore 后先把 raw key 重材料化为声明 keyType 才能算 key-group——Flink 的 key 序列化是 injective 的，不存在这层；
  - `ContainerValueCodec`（P1-21-01）：容器值逐层元素类型包装——Flink 的 `ListSerializer`/`MapSerializer` 天然携带元素 serializer；
  - `__java_bytes__` marker（自定义 `IStreamSerializer` escape hatch 的 JSON 载体）与 `nopContainerType` 包装——Flink 的序列化格式自描述，无需 marker 判别（marker 判别歧义即 R5-ST-08，仍 open）；
  - `MapValuePairValidator`、TimeWindow namespace 守卫、`AccumulatorTypeInference`——Flink 在 descriptor 绑定阶段就拦截的错型，nop 推迟到 restore 数据路径逐条校验。
  这层的存在本身不是缺陷（每处都有 fail-fast 与测试），但它是**同族缺陷复发带**：R4 的 ST-02/ST-04/ST-05/ST-08 四项全部是该接缝上的新变体。结构性建议是把「keyType/元素类型/容器形态」的三元组收敛为快照格式的**一等字段**（而非逐修复点补丁），减少下一个变体的出现面。
- **live 路径的真实性能位置**。memory 后端零序列化、零拷贝（对象引用），单看 live 读写**快于** Flink heap 后端（`CopyOnWriteStateMap` 写时深拷贝以支撑并发快照一致）——nop 用 mailbox 单线程 + 「用户不得改引用」的文档约定换掉了这层拷贝（已登记限制 §11.3，定位内可接受）。真正弱于 Flink 的是 **RocksDB 路径**：每个 get/put 都过 JSON parse/serialize，访问成本由 JSON 而非 RocksDB 主导（Flink 是二进制 bytes 直比/直存）。对「几十 GB 状态级」的**容量**定位豁免成立，但对**访问吞吐**不豁免——选择 RocksDB 后端的作业恰恰是有大状态高访问频率的作业。
- **escape hatch 覆盖不均（定位内缺陷）**。自定义 `IStreamSerializer` 二进制通道只接在 memory serde（`MemoryStateSerDe.getSerializerIfAvailable` :680-687 + `__java_bytes__` marker :716）；**RocksDB 后端没有自定义 serializer 通道**（R5-ST-09 备注核实，`RocksDBValueState.update` 直 JSON）。即最需要绕开 JSON 的后端反而没有逃生门。低成本补齐见清单。
- **schema 演进**。`SerializerFingerprint`（类签名 SHA-256）+ `StateMigrationFunction`（getState() 时比对、迁移、幂等）达到「不兼容可检测 + 有迁移出口」的目标语义，且 checksum 不受 TTL 配置影响（运行时行为与 schema 契约分离，设计正确）。与 Flink 的差距在粒度：类签名级指纹对「同类型字段改名/字段类型变更」不敏感（都是同一个类），这类变更 nop 侧要么靠用户注册迁移函数、要么静默容忍 JSON 层宽容性——Flink 的字段级 snapshot 能精确感知。定位内（JSON 值语义本来就宽松）可接受，但 `schemaVersion` 恒为 1 意味着 version-based branching 尚未激活，未来跨大版本演进前需要补。

## 3. RocksDB 后端

**结论：列族模型与 key 编码与 Flink 同构且 layout 纪律严格（版本化 + fail-fast）；快照/恢复的正确性纪律（fail-fast、坏数据 typed 错误、引用计数）达到 Flink 同级；差距集中在资源管控面、写入路径优化与增量的生命周期时序。**

- **列族模型**：每 state 一 CF（`getOrCreateColumnFamily`），key layout v2 `[group:i32BE][nsLen][ns][keyLen][key]` 可排序前缀使字典序=group 数值序，支撑 range restore（`copyColumnFamilyRange` 半开区间字节扫描）。与 Flink 的 `(keyGroup, namespace, key)` 前缀布局语义相同。layout version 2/legacy 1 的 fail-fast 握手（全量路径容忍缺 version、增量路径严格匹配）比 Flink 更保守，符合平台 no-silent 原则。
- **全量快照**：全量扫描 → JSON，与 memory 后端 byte-compatible（跨后端互换是 Flink 没有的能力，Flink heap/RocksDB 快照格式不互通）。同步扫描会阻塞 mailbox（Flink 异步）；定位内（checkpoint 期间短暂停顿）可接受，与 Flink 的差距量级取决于状态量。
- **增量快照**：机制同构（native Checkpoint 硬链 → 内容寻址 SST → 共享注册表引用计数），但三处弱于 Flink：
  1. **持久化时序**（R5-ST-18，仍 open）：nop 的 SST 持久副本靠 coordinator 在异步 persist 阶段从 **task-local 绝对路径**拷入 segment store，task 侧 K=2 本地保留 + 重启后 `incrementalSnapshotIdCounter` 复位可删仍被引用的目录。Flink 在快照完成时即把新增 SST 流上传到 durable 存储，task-local 目录只是机会性缓存——生命周期不依赖本地文件。这是两者增量快照最本质的工程差距。
  2. **共享策略**：Flink 2.x 有 CLAIM/FORWARD `SharingFilesStrategy`（恢复后首个 checkpoint 也能增量），nop 无对应（首个 checkpoint 必全量语义，影响小）。
  3. **rescale 下的增量恢复**：Flink `RocksDBIncrementalCheckpointUtils` 支持增量 handle 的 bin-packing 重分配与 maxParallelism 变化时的字节级 group 前缀重映射（restore 时原地改 key 字节）；nop 的增量恢复按目标 `KeyGroupRange` 字节区间过滤（等价于 Flink 的 range 分配），`maxParallelism` 变化则整体走显式离线 reshard migration（`KeyGroupReshard` + `MaxParallelismReshardMigration`）——这是 vision §四的显式裁剪（在线 reshard 出范围），**不算缺陷**。
- **资源管控面（低成本差距）**。Flink 通过共享 memory controller（write-buffer/block-cache manager 跨 CF 统一配额，`RocksDBMemoryControllerUtils`）、`RocksDBWriteBatchWrapper`（按大小/条数攒批，抑制写放大）、`PredefinedOptions`/`RocksDBOptionsFactory`（调优预设）管控大状态内存。nop 的 `RocksDBOptionConfig` 只有 writeBufferSize + 后台线程数两个旋钮，无 block cache / write-buffer manager / bloom filter——几十 GB 状态下没有全局内存上限意味着可能 OOM native 侧。此项低成本可采纳且定位内必要（见清单）。
- **TTL 清理**：无 native compaction filter（rocksdbjni JNI 纯 Java 不可子类化，设计裁定 §12.8，与事实相符）；纯 Java sweep 替代。Flink 2.3 的原生 compaction filter 仍在（ForSt 模块 `ForStDBTtlCompactFiltersManager`；rocksdb 模块 2.3 未再见 TTL filter manager，治理路径向 ForSt 收敛）。nop 的裁定在纯 Java 模块约束下正确，compaction filter 保持 optimization candidate。
- **timer 存储**：Flink 把 timer priority queue 也放 RocksDB（`RocksDBPriorityQueueSetFactory`，off-heap timer），nop 的 timer 是状态条目走同一 CF 通道——语义等价，nop 无需单独机制，非缺陷。

## 4. operator state 重分布

**结论：后端级 4-mode 能力与 Flink 语义基本对齐，但 (a) 生产 rescale 路径未接线（重分布能力在生产代码零调用），(b) BROADCAST 语义弱于 Flink。前者是本次对比新发现的结构性缺口。**

- **新发现 A（重分布未接线）**：`MemoryOperatorStateBackend.restoreState(oldSnapshots, oldParallelism, mode, taskIndex, newParallelism)`（4 参重分布重载）全仓生产调用方为**零**——唯一调用在测试 `TestE2EOperatorStateRedistribution`。生产 restore 路径 `RescaleStateAssembler.restoreOperatorsFromState` 走单快照 `restoreState(opResult)`；rescale 组装 `buildRescaledTaskState`（:125-145）对 operator state **1:1 按索引复制、扩容 subtask 置空**，注释明示 "operator state rescale redistribution is out of scope"。后果：按 §10.3 的典型用途，**source offset（SPLIT_DISTRIBUTE 场景）在 rescale 时缩容丢被删 subtask 的 split 偏移、扩容新 subtask 拿空状态**——除非 source 走 `CheckpointParticipant.restoreFromEpoch` 自管。这与设计文档 §10.4「operator State 已落地」的口径存在文档-实现落差（后端能力落地、执行器接线未落地）。建议：要么在 rescale 组装处接线 4-mode 重分布，要么在 §10.4 明示「rescale 时不重分布」的限制，二者必居其一。
- **新发现 B（BROADCAST 弱化）**：`restoreBroadcast`（:101-111）取**首个非空快照**整份恢复；Flink 的 broadcast state 重分布是**并集发给所有实例**（`RoundRobinOperatorStateRepartitioner` :277-286，UNION/BROADCAST 同路）。对「各 subtask 状态一致」的正确 broadcast 用法两者等价；但 subtask 状态分叉（写 bug、或刻意分片写）时 Flink 并集保数据，nop 静默丢其余快照。一行可改为并集（见低成本清单）。
- **对齐良好的部分**：SPLIT_DISTRIBUTE 的 round-robin stride 与 Flink `RoundRobinOperatorStateRepartitioner` 的逐元素轮转等价（含 S-9 的 taskIndex 越界 fail-fast）；UNION 列表合并等价；模式选择不进用户 API、由执行图注入的设计是合理的定位裁剪（Flink 暴露 `getUnionListState` 是用户选择，nop 收敛为部署决策，两者都是自洽契约）。`NONE` 模式取首个快照、不校验并行度是否变化，比 Flink「并行度变化必须走 repartitioner」宽松，接线时需注意别让 NONE 成为 rescale 默认。

## 5. TTL

**结论：语义为 Flink 默认档的真子集，实现路径（sidecar 而非包装值）在其自身约束下正确；清理强度与粒度低于 Flink，memory 侧过期条目永生是定位内小缺陷。**

- **语义对齐**：updateType 两档（OnCreateAndWrite/OnReadAndWrite）与 Flink 一致；processing-time only 与 Flink 一致（Flink 至今未实现 event-time TTL）；默认惰性清理 + 快照排除与 Flink 默认档（FULL_STATE_SCAN_SNAPSHOT）一致。TTL 不进 schema checksum（增删 TTL 不破坏 checkpoint 兼容）、restore 后 sidecar 为空按 OnCreateAndWrite 给新窗口、重复 getState 不重置累计窗口——这些细节语义均正确且有测试钉住。
- **粒度差距**：Flink MapState TTL 是 **per-UK-entry**（每条目独立时间戳，get/put 逐条刷新）；nop 是整 map 一个 TTL 单元（§12.3，per-entry 登记为 Deferred）。对「map 中冷热条目混合」场景 nop 会整体过期。定位内可接受但值得在文档标注。
- **清理强度差距**：Flink 另有 INCREMENTAL_CLEANUP（访问期采样清理）与原生 compaction filter 两档后台清理；nop 只有 lazy + snapshot 排除 + RocksDB sweep（且 sweep 仅在 snapshotState 时执行）。由此产生 R5-ST-12（仍 open）：memory 后端不再被访问的过期条目在 JVM 内永生；RocksDB sidecar 随键基数线性增长且 `expiredKeys()` 每次全表拷贝。TTL 正确性不受影响，是内存/停顿问题。
- **实现对照**：Flink 用 `TtlValue<T>` 包装值；nop 用 sidecar `TtlContext` 时间戳 map 与存储值分离。nop 的选择由两个真实约束驱动（accumulator 类型不能被包装破坏；serde 的 instanceof 分发会被 wrapper 破坏），是有依据的本地化设计而非能力缺失。

## 6. 差距清单

### 6.1 定位内缺陷（已知 open 项 + 本次新发现）

| 编号 | 内容 | 级别（沿用 R4 口径） |
|---|---|---|
| R5-ST-05 | ValueState<List/Map> 容器元素类型在 P1-21-01 链路仍丢失（RocksDB 侧静默退化）——序列化修复层未覆盖 ValueState 家族 | P2 |
| R5-ST-08 | `__java_bytes__`/`nopContainerType` marker 与用户 Map 值判别歧义——marker 方案固有税 | P3 |
| R5-ST-12 | TTL 清理仅 checkpoint 时执行；memory 过期条目永生；`expiredKeys()` 全表拷贝 | P3 |
| R5-ST-13 | `targetKeyGroupRange`/`restoreAggregateFunctions` restore 后不复位（防御性缺失） | P3 |
| R5-ST-18 | 增量快照依赖 task-local 绝对路径 + cp-N 本地保留，重启计数器复位有删除竞态窗口（fail-fast 不静默） | P3 |
| R5-ST-09/10/11 | RocksDB 侧 17 处裸字符串 StreamException（错误码监控不可用）；全量 restore 先清空 DB 非原子；`restoreRangeInto` native handle 关闭顺序与后端纪律相反 | P3 |
| **NEW-A**（本次） | **operator state 重分布后端能力未接入生产 rescale 路径**：4 参 `restoreState(mode,...)` 生产零调用；`buildRescaledTaskState` operator state 1:1 按索引、扩容置空（"out of scope" 注释）。source offset 等 §10.3 用例在 rescale 下的语义需 owner 裁定（接线 or 文档明示限制） | 建议 P2（结构缺口，非运行时错误路径） |
| **NEW-B**（本次） | `restoreBroadcast` 取首个非空快照而非并集（Flink 为并集发所有实例）；分叉状态被静默丢弃 | 建议 P3 |

### 6.2 定位外裁剪（vision 排除，不算缺陷）

| 项 | 裁剪依据 |
|---|---|
| TypeSerializer/TypeInformation/TypeSerializerSnapshot 二进制序列化体系 | vision §四「不引入二进制序列化体系」+ §6.1 铁律 |
| async state v2 API（Flink 2.x 异步状态接口族） | vision §四「异步算子」Non-Goal 的状态层对应物 |
| ForSt / heap-spillable 等第三、第四后端家族 | §六决策点 #3：后端变更须人决策；两后端已覆盖堆内/off-heap 定位 |
| 增量恢复下的在线 rescale（bin-packing + 字节前缀重映射）与在线 reshard | vision §四「在线/自动 reshard」Non-Goal；离线 migration action 已交付 |
| BroadcastState 专用类型 | §七裁决 G36 永久排除（BROADCAST 重分布覆盖用例） |
| MergingState 抽象层 | state-management-design §2.4 G62（optimization candidate，待第二消费者） |
| native compaction filter TTL 清理 | §12.8 JNI 约束裁定，纯 Java sweep 替代 |

### 6.3 低成本可采纳（Flink 已证可行、且不破定位）

1. **stableHash 加 murmur 扰动**：对白名单类型的 `hashCode()` 先过 `MathUtils.murmurHash` 再取模（对齐 Flink `computeKeyGroupForKeyHash`），一行改动 + 兼容性豁免需评估（会改变现有 key→group 映射——需随 maxParallelism reshard 或作为新 layout version 引入，见附注）。
2. **`assignKeyGroupToSubtask` 闭式化**：`start(i)=i*base+min(i,rem)` 有 O(1) 逆解，逐记录路由去掉 O(P) 循环。
3. **RocksDB 内存管控**：`RocksDBOptionConfig` 增加 block cache 大小、`WriteBufferManager`（跨 CF 总写缓冲上限）、bloom filter 预设——`RocksDBMemoryControllerUtils` 模式可直接借用思路，几十 GB 状态下这是定位内必要的 OOM 防线。
4. **RocksDB 写批处理**：`RocksDBWriteBatchWrapper` 式按大小/条数攒批，窗口触发期的 burst 写放大收益直接。
5. **RocksDB 侧接入自定义 `IStreamSerializer` 通道**：与 memory serde 对称，给高吞吐 RocksDB 作业一条绕开 JSON 的路（顺带消 ST-05 的 RocksDB 静默面）。
6. **BROADCAST 改并集语义**（NEW-B 修复，一行级）。
7. **TTL sweep 小改**：memory 后端提供 snapshot 之外的定期 sweep 入口；`expiredKeys()` 免拷贝迭代（单线程模型下安全）——即 R5-ST-12 的建议项。
8. **增量快照拷贝时机**：coordinator register 时同步拷贝，或删除条件改为「hash 已在 segment store」（R5-ST-18 建议项）。
9. ST-05/10/11 的修复建议本身（ValueState 容器 codec 对称接入、restore 失败二次清空、handle 关闭顺序）均为低成本。

> 附注（第 1 项的兼容性）：murmur 加扰会改变全部 key 的 group 映射，等效于一次 maxParallelism reshard。若采纳，应与快照头 `hashPolicy` 版本字段绑定（restore 时旧快照用旧公式），或明确声明「仅随离线 reshard action 一起执行」。不加版本控制地改公式会重演 AR-01 的幻影状态。

### 6.4 过度设计点（定位内可再精简）

1. **`TtlCleanupStrategy` 死配置面**：plan 366 删除 `backgroundCleanup` 标志后，`cleanupStrategy` 只剩一个文档明示的 no-op 保留槽位（`StateTtlConfig.Builder.setCleanupStrategy` 仍校验/存储/参与 equals——javadoc 自述 "documented no-op reserved slot"）。保留一个无行为旋钮是 API 面债务：用户设了它没有任何效果，不如整体移除或换成真正的 sweep 周期参数。
2. **兼容字段冗余度**：快照 state-info 同时携带 `shardCount`（>1 时）、`keyType` 头、`keyLayoutVersion`、`schemaChecksum/schemaVersion`、`valueType` + legacy `valueTypeName` 双拼（`resolveTypeName` 兼容）、`accumulatorTypeName` 双拼。每项各有出处，但兼容矩阵已接近「第六种拼法再加一个」的临界点——建议下次格式演进时收敢单拼 + 版本号，停止双拼累积。
3. **`RedistributionMode.NONE`**：Flink 无对应模式（并行度变化必须走 repartitioner）；NONE+并行度变化=静默取首快照，是接线时的语义陷阱（见 §4）。若在接线前删掉 NONE、强制显式模式，可少一个错误用法面。

## 7. 质量评分

**4 / 5（定位内）**

理由：

- **加分**：key-group 模型与 Flink 语义等价且稳定性契约更强（框架强制 vs 用户约定）；AR-01/ST-02/ST-03/ST-04 等 P1 级路由与恢复缺陷在 R4+plan 368 已全部闭环且有专项回归测试钉死；RocksDB 增量快照的引用计数/内容寻址/fail-fast 纪律达到 Flink 同级正确性；快照格式跨后端互通是 Flink 没有的能力；no-silent-no-op 原则在 restore 路径执行得比 Flink 更严格（Flink 多处 Kryo/unknown-tag 走宽容路径）。
- **扣分一**（-0.5）：JSON 中心取舍的结构性代价已被 R4 实证——4 个状态类 P1/P2 全部落在「JSON 类型退化 × restore 路径」接缝上，修复方式是逐点补丁（重材料化/包装/守卫），尚未收敛为格式级一等字段；同类变体（ST-05/08）仍 open。
- **扣分二**（-0.5）：operator state 重分布停留在后端能力层，生产 rescale 未接线（NEW-A）；BROADCAST 并集语义弱化（NEW-B）；RocksDB 资源管控面（共享内存上限/写批处理）缺失，使「几十 GB 状态」定位的关键场景（off-heap 大状态高吞吐）缺少 Flink 已证低成本的防线。

不评 5 的原因即扣分二：对照 Flink 的成熟度，nop 在「能力已写好但未长进执行路径」这一点上仍有一个可指名的结构缺口。不评 3 的原因：所有已知缺陷均有 fail-fast 兜底或明确登记，无静默数据损坏路径存活于 open 清单（ST-04/ST-02 类静默项均已修）。

## References

- `ai-dev/design/nop-stream/00-vision.md`（§四 Non-Goals、§七 G36、§八不变量）
- `ai-dev/design/nop-stream/state-management-design.md`（§3 key-group、§6 序列化、§10 operator state、§12 TTL）
- `ai-dev/audits/2026-09/2026-09-30-0530-deep-audit-nop-stream-quality-r4/02-state-backend-serialization.md`（R5-ST-01..18 现状）
- `ai-dev/audits/2026-09/2026-09-30-0530-deep-audit-nop-stream-quality-r4/00-plan366-regression.md`（R5-REG 系列）
- `ai-dev/analysis/nop-stream/04-state-comparison.md`（Flink 1.20 对照，本文为其 2.3.0 更新）
- nop-stream：`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/shard/KeyGroupAssignment.java`、`.../shard/StateKeyRematerializer.java`、`.../shard/KeyGroupReshard.java`、`.../backend/memory/MemoryStateSerDe.java`、`.../backend/memory/MemoryOperatorStateBackend.java`、`nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBKeyEncoder.java`、`.../incremental/RocksDBIncrementalSnapshotStrategy.java`、`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/RescaleStateAssembler.java`
- Flink 2.3.0：`flink-runtime/src/main/java/org/apache/flink/runtime/state/KeyGroupRangeAssignment.java`、`.../checkpoint/RoundRobinOperatorStateRepartitioner.java`、`.../state/DefaultOperatorStateBackend.java`、`flink-core/src/main/java/org/apache/flink/api/common/state/StateTtlConfig.java`、`flink-state-backends/flink-statebackend-rocksdb/src/main/java/org/apache/flink/state/rocksdb/snapshot/RocksIncrementalSnapshotStrategy.java`、`flink-state-backends/flink-statebackend-forst/src/main/java/org/apache/flink/state/forst/ForStDBTtlCompactFiltersManager.java`

## Open Questions

- [ ] NEW-A 的 owner 裁定：operator state rescale 是接线 4-mode 重分布，还是在 `state-management-design.md` §10.4 明示「rescale 不重分布」限制？
- [ ] murmur 加扰（低成本项 1）是否值得为哈希质量支付一次全量 key-group 迁移（需 hashPolicy 版本字段）？在小集群定位下可能答案是「不值得」，留待真实分布问题出现再议。
