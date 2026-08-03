# 状态管理 & 状态后端源码级对比分析

> **Plan**: `docs/plans/nop-stream-flink-comparison/2026-07-24-1000-2-state-management-comparison.md`
> **Generated**: 2026-07-24
> **Method**: Direct source reading (Plans 316/317 not yet closed; deliverables `01-flink-source-audit.md`/`02-nopstream-live-audit.md` absent. Supplemented from direct source inspection of Flink `release-1.20.0` at `~/sources/flink/` and nop-stream at `nop-stream/nop-stream-core/`)
> **Consumable by**: Item 8 (Gap Analysis)

---

## 1. Keyed State 接口层次对比

### 1.1 接口层次

| 层级 | Flink (v1, `o.a.f.api.common.state`) | nop-stream (`io.nop.stream.core.common.state`) |
|------|-------|-----------|
| 根接口 | `State` (clear()) | `State` (clear()) |
| 单值 | `ValueState<T>` extends State (value(), update()) | `ValueState<T>` extends State (value(), update()) |
| 追加基类 | `AppendingState<IN,OUT>` extends State (get(), add()) | `AppendingState<IN,OUT>` extends State (get(), add()) |
| 合并标记 | `MergingState<IN,OUT>` extends AppendingState (no new methods) | — (不存在; ReducingState/AggregatingState 直接 extends State) |
| 列表 | `ListState<T>` extends MergingState (get, add, update, addAll) | `ListState<T>` extends State (get, add, update, addAll) |
| 归约 | `ReducingState<T>` extends MergingState (get, add) | `ReducingState<T>` extends State (get, add) |
| 聚合 | `AggregatingState<IN,OUT>` extends MergingState (get, add) | `AggregatingState<IN,OUT>` extends State (get, add) |
| KV 映射 | `MapState<UK,UV>` extends State (11 methods incl. clear) | `MapState<UK,UV>` extends State (11 methods incl. clear — 方法签名一致) |
| 广播只读 | `ReadOnlyBroadcastState<K,V>` extends State | — |
| 广播可写 | `BroadcastState<K,V>` extends ReadOnlyBroadcastState | — |

### 1.2 差异分析

| 差异 | 分类 | 详情 |
|------|------|------|
| 缺少 `MergingState` 中间接口 | Gap | Flink 的 ListState/ReducingState/AggregatingState 共享 MergingState 基类; nop-stream 各自直接 extends State。不影响运行时行为但减少了类型层次的可扩展性 |
| 缺少 `BroadcastState` / `ReadOnlyBroadcastState` | Gap | Flink 为 broadcast pattern 提供专用状态类型; nop-stream 完全没有 broadcast state 概念。**裁定（2026-08-04，G36）：permanently excluded**——专用 BroadcastState 类型永久不引入，理由见 `00-vision.md` §七裁决记录 #G36（operator state `BROADCAST` 重分布已覆盖配置流分发用例） |
| MapState 方法签名 | Match | Flink 和 nop-stream 的 MapState 均包含 `get/put/putAll/remove/contains/entries/keys/values/iterator/isEmpty/clear` 共 11 个方法 |
| nop-stream 有 Internal 变体 | Improvement | `InternalAppendingState<K,N,IN,ACC,OUT>`, `InternalListState<K,N,T>` — 为 Window operator 提供 namespace 感知的底层 API。Flink 也有 internal 包但隐藏在 `o.a.f.runtime.state.internal` |
| Flink v2 异步 API | Out of scope | Flink 1.20 引入了 `state.v2` 异步 API (StateFuture, StateIterator)。nop-stream 目前只有同步 API |
| 方法签名 | Match | 所有共同方法签名一致 (ValueState.value/update, ListState.get/add/addAll/update, MapState.get/put/remove/contains/entries/keys/values/iterator) |
| 异常声明 | Drift (minor) | nop-stream 的 ValueState 声明 `throws IOException`，而 Flink v1 同样声明 `throws IOException`。但 nop-stream 的 ReducingState/AggregatingState 声明 `throws Exception` 而非 `throws IOException` |

### 1.3 结论

nop-stream 覆盖了 Flink 的 5 种标准 keyed state 类型 (Value/List/Map/Reducing/Aggregating)。主要缺口是缺少 `BroadcastState` 类型和 `MergingState` 中间接口。

---

## 2. Operator State 体系对比

### 2.1 结构对比

| 维度 | Flink | nop-stream |
|------|-------|-----------|
| 用户接口 | `CheckpointedFunction` (`o.a.f.streaming.api.checkpoint`) | `ICheckpointedFunction` (`io.nop.stream.core.common.functions`) |
| 方法签名 | `snapshotState(FunctionSnapshotContext)`, `initializeState(FunctionInitializationContext)` | 同左 |
| OperatorStateStore | `OperatorStateStore` 接口 (flink-core): `getListState`, `getUnionListState`, `getBroadcastState`, `getRegisteredStateNames`, `getRegisteredBroadcastStateNames` | **不存在** — 无 `OperatorStateStore` 接口 |
| OperatorStateBackend | `OperatorStateBackend` 接口 (extends OperatorStateStore + Snapshotable) | **不存在** — 无 `IOperatorStateBackend` |
| 默认实现 | `DefaultOperatorStateBackend` (359 行) — 使用 `PartitionableListState` + `HeapBroadcastState` | **不存在** — 无等价实现 |
| 状态描述符 | 复用 `ListStateDescriptor` (operator list) 和 `MapStateDescriptor` (broadcast)；Flink **没有** `OperatorStateDescriptor` 类 | nop-stream **没有** `OperatorStateDescriptor`（与 Flink 一致） |
| 状态访问 | 通过 `OperatorStateStore.getListState()` 返回 `PartitionableListState` (ArrayList 后端) | 通过 `TaskStateSnapshot.putOperatorState(key, value)` / `getOperatorState(key)` 直接操作 `Map<String, Object>` |
| 重分布模式 | `SPLIT_DISTRIBUTE` (round-robin), `UNION` (全量广播), `BROADCAST` | **完全不支持** — 没有重分布概念 |

### 2.2 nop-stream 的 "Operator State" 现状

nop-stream 的 operator state 实际上是通过 `TaskStateSnapshot` 和 `OperatorSnapshotResult` 上的 `Map<String, Object>` 实现的：

- `TaskStateSnapshot.java:152` — `operatorStates: ConcurrentHashMap<String, Object>`
- `OperatorSnapshotResult.java:161` — `operatorStates: Map<String, Object>`
- 没有类型化访问器，没有 serializer 绑定，没有重分布语义
- `ICheckpointedFunction` 的 `initializeState()` 回调中，`FunctionInitializationContext` 仅提供 `isRestored()`，**不提供** `OperatorStateStore` 或 `KeyedStateStore` 访问

### 2.3 差异分析

| 差异 | 分类 | 严重性 | 详情 |
|------|------|--------|------|
| 缺少 OperatorStateStore 接口 | Gap | P1 | 用户函数无法通过标准 API 注册 operator state。ICheckpointedFunction 用户需要自行管理 Map<String, Object> |
| 缺少重分布模式 | Gap | P1 | 并行度变化时 operator state 无法正确重新分配。当前 Map<String, Object> 是 subtask 本地映射，不参与重分布 |
| 缺少 OperatorStateBackend | Gap | P1 | 没有统一的 operator state 生命周期管理（snapshot/restore/close/dispose） |
| Broadcast state 缺失 | Gap | P2 | 没有 broadcast state 模式 |
| ICheckpointedFunction 接口存在 | OK | — | 接口定义完整（与 Flink 签名一致） |
| snapshot/restore 管线存在 | Hollow | P2 | snapshot 管线可以保存 Map<String, Object>，但没有兼容性检查或序列化版本控制 |

---

## 3. 状态后端架构对比

### 3.1 架构层次

```
Flink:
  StateBackend (factory interface)
    +-- createKeyedStateBackend() → CheckpointableKeyedStateBackend / AsyncKeyedStateBackend
    +-- createOperatorStateBackend() → OperatorStateBackend
    +-- createCheckpointStorage() → CheckpointStorage (checkpoint 持久化，非 state 层)
  KeyedStateBackend (extends KeyedStateFactory + PriorityQueueSetFactory + Disposable)
    +-- HeapKeyedStateBackend (heap/)
    +-- 其他: RocksDBKeyedStateBackend (rocksdb/)
  OperatorStateBackend (extends OperatorStateStore + Snapshotable + Closeable + Disposable)
    +-- DefaultOperatorStateBackend

nop-stream:
  IStateBackend (factory interface)
    +-- createKeyedStateBackend() → IKeyedStateBackend
    +-- 没有 createOperatorStateBackend()
  IKeyedStateBackend (extends KeyedStateStore + AutoCloseable)
    +-- MemoryKeyedStateBackend (backend/memory/)
  IInternalStateBackend (extends IKeyedStateBackend — adds namespace-aware internal ops)
    +-- MemoryKeyedStateBackend (也实现此接口)
  (ICheckpointStorage 是 checkpoint 持久化层，不在 state/backend 包中)
```

### 3.2 功能映射

| 功能 | Flink | nop-stream | 差距 |
|------|-------|-----------|------|
| KeyedStateBackend 创建 | `StateBackend.createKeyedStateBackend(params)` → `CheckpointableKeyedStateBackend` | `IStateBackend.createKeyedStateBackend(keyType)` → `IKeyedStateBackend` | Match (简化) |
| OperatorStateBackend 创建 | `StateBackend.createOperatorStateBackend(params)` → `OperatorStateBackend` | **不存在** | Gap-P1 |
| 状态快照 | `KeyedStateBackend.snapshot(...)` → `SnapshotResult<KeyedStateHandle>` | `IKeyedStateBackend.snapshotState()` → `StateSnapshot` | Match (简化) |
| 状态恢复 | `KeyedStateBackend.restore(...)` | `IKeyedStateBackend.restoreState(StateSnapshot)` | Match (简化) |
| CheckpointStorage | `StateBackend.createCheckpointStorage()` → `CheckpointStorage` (持久化层) | ICheckpointStorage 在 checkpoint 包中 | Match (设计正确) |
| 并行 checkpoint | async 两阶段 (AsyncKeyedStateBackend) 或 sync 同步 | 同步 snapshot — 无异步两阶段 | Gap-P2 |
| 增量 checkpoint | RocksDB 后端支持 | 不支持 | Gap-P2 |
| key-group 分区 | 内置 (所有后端) | 分层 shard 而非 key-group | Gap-P2 (见第 4 节) |

### 3.3 差异分析

| 差异 | 分类 | 严重性 |
|------|------|--------|
| 缺少 `IOperatorStateBackend` | Gap | P1 |
| 缺少 IStateBackend.createOperatorStateBackend() 工厂方法 | Gap | P1 |
| 缺少异步两阶段 snapshot | Gap | P2 |
| 缺少增量 checkpoint | Gap | P3 |
| `ICheckpointStorage` 分类正确 | OK | — |

---

## 4. Key-Group vs StateShard 对比

### 4.1 Flink Key-Group 设计

**核心类**: `KeyGroupRange`, `KeyGroupRangeAssignment`, `KeyGroupsList`, `KeyGroupPartitioner`

- **设计目标**: 为 keyed state 提供不依赖并行度变化的稳定分区标识
- **哈希**: `murmurHash(key.hashCode()) % maxParallelism`
- **范围**: `KeyGroupRange` 表示连续区间 `[startKeyGroup, endKeyGroup]`
- **映射**: `operatorIndex = keyGroupId * parallelism / maxParallelism` — 并行度变化时 **只有 range 边界移动，keyGroupId 不变**
- **maxParallelism**: 上限 `Short.MAX_VALUE + 1 = 32768`，下限 `128`
- **状态管理**: 每个 key-group 内的 state 独立序列化/存储/恢复

### 4.2 nop-stream StateShard 设计

**核心类**: `StateShard` (state/shard/), `ShardPrefixedKey` (state/shard/)

- **设计目标**: 提供分片标识以支持状态数据的分区存储
- **哈希**: `key.hashCode() % stateShardCount` (使用 `stableHash` 方法)
- **范围**: `stateShardCount` 配置，每个 shard 用整数 `stateShardId` 标识
- **映射**: `computeShardId(key)` 返回 shard ID，通过 `ShardPrefixedKey(shardId, key)` 包装
- **没有 maxParallelism 概念** — shard 与 operator 并行度直接相关
- **状态管理**: shard 作为 key 前缀，以支持不同 shard 存储在不同位置

### 4.3 对比分析

| 维度 | Flink Key-Group | nop-stream StateShard |
|------|-----------------|----------------------|
| 稳定性 | key-group 在并行度变化时不变（基于 maxParallelism） | shard ID 取决于 stateShardCount；并行度变化时 shardCount 可能变 |
| 重分配 | 算子并行度变化时仅 range 边界移动 | 无内置重分配支持 |
| 哈希算法 | `murmurHash` | `Object.hashCode()` （不稳定，受 JVM 实现影响） |
| 范围定义 | `KeyGroupRange` （连续 range） | `int stateShardId` （单个整数） |
| 可组合性 | key-group 可交集、分割、合并 | shard 不可组合 |
| maxParallelism | 核心设计参数 | 不存在 |

### 4.4 StateShard 不是 Key-Group 的替代

StateShard 的目标与 Key-Group 不同：
- StateShard 是存储分区标识（用于决定 "数据存到哪个分片"）
- Key-Group 是状态分配策略（用于决定 "并行度变化时状态如何重分配"）

**如果 nop-stream 需要支持并行度动态变化**，StateShard 需要迁移到类似 Key-Group 的设计，或者 StateShard 本身需要增加 "基于 maxShardCount 的稳定哈希" 和 "range 范围映射" 语义。

### 4.5 差异分析

| 差异 | 分类 | 严重性 |
|------|------|--------|
| 缺少 maxParallelism 概念 | Gap | P2 |
| 使用 Object.hashCode() 而非稳定哈希 | Gap (Hollow) | P2 — 序列化后会丢失一致性 |
| 缺少 range 交集/分割/合并操作 | Gap | P2 |
| StateShard 不参与状态恢复重分配 | Gap | P2 |
| 单层 shard 而非 key-group range | Design Difference | — |

---

## 5. 状态序列化/反序列化对比

### 5.1 Flink 方案

**核心类**: `TypeSerializer<T>` (abstract), `TypeSerializerSnapshot<T>` (interface), `TypeSerializerSchemaCompatibility`

- 每个数据类型有专属 serializer（`IntSerializer`, `StringSerializer`, `KryoSerializer` 等）
- `TypeSerializer.snapshotConfiguration()` 返回 `TypeSerializerSnapshot`
- `TypeSerializerSnapshot.resolveSchemaCompatibility()` 返回兼容性结果（Compatible / RequiresMigration / Incompatible）
- 支持 schema evolution：新增字段、删除字段、类型变更等场景
- `ArrayListSerializer`, `MapSerializer` 等复合 serializer

### 5.2 nop-stream 方案

**核心类**: `TypeSerializer<T>` (interface in `typeutils/`), `StreamModelFingerprint` (model/), `MemoryStateSerDe` (state/backend/memory/)

- `TypeSerializer` 接口存在但 **没有 `TypeSerializerSnapshot`** — `snapshotConfiguration()` 只存在于 Javadoc 注释中，实际代码不存在
- 状态序列化使用 `MemoryStateSerDe`：以 `LinkedHashMap<String, Object>` 为中间格式
- 值序列化使用 `JsonTool.serialize()` / `JsonTool.parseBeanFromText()` 做类型转换
- `StreamModelFingerprint` 用于编译期兼容性检查（DAG、requirements），**不是序列化级别的**
- 没有 schema evolution 支持

### 5.3 对比分析

| 维度 | Flink | nop-stream | 差距 |
|------|-------|-----------|------|
| 序列化接口 | `TypeSerializer<T>` 抽象类 | `TypeSerializer<T>` 接口 | Match (形式) |
| 快照接口 | `TypeSerializerSnapshot<T>` | **不存在** | Gap-P1 |
| Schema 兼容性 | `TypeSerializerSchemaCompatibility` | **不存在** | Gap-P1 |
| Serializer 注册 | TypeSerializer 作为 StateDescriptor 的一部分 | 无 serializer 绑定（StateDescriptor 仅保留 type Class） | Gap-P2 |
| 实际序列化 | 二进制序列化 (DataInputView/DataOutputView) | `JsonTool` JSON 序列化 | Design Difference |
| 复合类型 | ArrayListSerializer, MapSerializer | 无 | Gap-P2 |
| 版本兼容性 | snapshot + restoreSerializer | `StreamModelFingerprint` (DAG 级别) | Gap-P1 |
| 序列化后 | 二进制字节（紧凑、高性能） | JSON 字符串（人可读、灵活性高、体积大） | Trade-off |

### 5.4 结论

nop-stream 的序列化方案严重不足：
- `TypeSerializerSnapshot` 完全缺失 — 无法做 schema evolution
- `TypeSerializer` 接口没被实际使用（`MemoryStateSerDe` 用的是 `JsonTool`，不是 `TypeSerializer`）
- 没有 serializer 注册/管理机制
- `StreamModelFingerprint` 是编译期 DAG 校验，不是运行时序列化兼容性

**建议**: 实现 `TypeSerializerSnapshot` 接口，将实际状态序列化从 `JsonTool` 迁移到 `TypeSerializer` + `DataInputView`/`DataOutputView` 模式，或至少为算子状态提供 serializer 绑定。

---

## 6. State TTL 实现对比

### 6.1 Flink TTL 实现

**核心包**: `o.a.f.runtime.state.ttl` (19 个文件)

- **配置**: `StateTtlConfig` (flink-core) — Builder 模式
  - `UpdateType`: Disabled, OnCreateAndWrite, OnReadAndWrite
  - `StateVisibility`: ReturnExpiredIfNotCleanedUp, NeverReturnExpired
  - `TtlTimeCharacteristic`: ProcessingTime
- **装饰器模式**: `AbstractTtlDecorator` → `AbstractTtlState` → 具体 TTL 状态
  - `TtlValueState` — 装饰 ValueState
  - `TtlListState` — 装饰 ListState
  - `TtlMapState` — 装饰 MapState
  - `TtlReducingState` — 装饰 ReducingState
  - `TtlAggregatingState` — 装饰 AggregatingState
- **工厂**: `TtlStateFactory` — 根据 descriptor 和配置自动包装
- **清理策略**:
  - `FULL_STATE_SCAN_SNAPSHOT` — 快照时扫描清除过期
  - `INCREMENTAL_CLEANUP` — 增量惰性清理
  - `ROCKSDB_COMPACTION_FILTER` — RocksDB compaction 过滤器
- **辅助类**: `TtlTimeProvider`, `TtlValue<T>`, `TtlUtils`, `TtlStateSnapshotTransformer`

### 6.2 nop-stream TTL 实现

**搜索结论**: TTL 相关代码 **不存在**

- `WatermarkStrategy.java:141` — 仅有 Idleness 注释提及，非 TTL
- `MapStateDescriptor.java:32` — 文档注释提到 "map state with TTL"，但无实现
- 没有任何 `Ttl` 类、接口、配置或装饰器

### 6.3 差异分析

| 差异 | 分类 | 严重性 |
|------|------|--------|
| StateTtlConfig 完全缺失 | Gap | P2 |
| TTL 装饰器完全缺失 | Gap | P2 |
| TTL 清理策略完全缺失 | Gap | P2 |
| 过期数据清除机制缺失 | Gap | P2 |

---

## 7. 差距汇总表

| # | 维度 | 发现 | 分类 | 严重性 | 说明 |
|---|------|------|------|--------|------|
| 1 | Keyed State | 缺少 BroadcastState 类型 | Gap | P2 | Broadcast state 完全缺失。**裁定（2026-08-04，G36）：permanently excluded** — 见 `00-vision.md` §七裁决记录 #G36 |
| 2 | Keyed State | 缺少 MergingState 中间接口 | Gap | P3 | 类型层次简化 |
| 3 | Keyed State | 五核心状态类型(Value/List/Map/Reducing/Aggregating)覆盖完整 | OK | — | 接口签名基本一致 |
| 4 | Operator State | 缺少 OperatorStateStore 接口 | Gap | P1 | 用户函数无法通过标准 API 注册 operator state |
| 5 | Operator State | 缺少重分布模式(SPLIT/UNION/BROADCAST) | Gap | P1 | 并行度变化时无法正确重分配 |
| 6 | Operator State | 缺少 IOperatorStateBackend | Gap | P1 | 没有统一 operator state 生命周期管理 |
| 7 | Operator State | ICheckpointedFunction 接口存在但 FunctionInitializationContext 不暴露 state store | Hollow | P2 | 接口签名对但连线断 |
| 8 | Backend | 缺少 IStateBackend.createOperatorStateBackend() | Gap | P1 | 工厂方法缺失 |
| 9 | Backend | 缺少异步两阶段 snapshot | Gap | P2 | 同步 snapshot 阻塞 barrier 处理 |
| 10 | Backend | MemoryKeyedStateBackend 实现完整(含 Internal 变体) | OK | — | 所有 5 种状态类型 + 3 种 Internal 类型 |
| 11 | Key-Group | 缺少 maxParallelism 概念 | Gap | P2 | StateShard 无法支持并行度变化 |
| 12 | Key-Group | StateShard 使用 Object.hashCode() 而非稳定哈希 | Gap (Hollow) | P2 | 序列化后哈希值可能不一致 |
| 13 | Key-Group | StateShard 无 range 交集/分割能力 | Gap | P2 | 无法做精确的状态分配控制 |
| 14 | Serialization | 缺少 TypeSerializerSnapshot | Gap | P1 | 无法做 schema evolution / 版本兼容性检查 |
| 15 | Serialization | TypeSerializer 接口未实际使用(MemoryStateSerDe 用 JsonTool) | Hollow | P2 | 接口存在但不在关键路径上 |
| 16 | Serialization | 缺少 serializer 注册/管理机制 | Gap | P2 | StateDescriptor 只保留 type Class |
| 17 | Serialization | StreamModelFingerprint 有效(编译期 DAG 校验) | OK | — | 补充性校验，非序列化级别 |
| 18 | TTL | StateTtlConfig 完全缺失 | Gap | P2 | 无 TTL 配置 |
| 19 | TTL | TTL 装饰器/清理策略完全缺失 | Gap | P2 | 无运行时 TTL 支持 |

### 优先级定义

| 等级 | 含义 | 数量 |
|------|------|------|
| P0 | Correctness blocking (数据错误、丢失) | 0 |
| P1 | Design contract violation (关键功能缺失) | 6 |
| P2 | Missing capability (重要功能缺失) | 9 |
| P3 | Optimization/minor (小改进) | 1 |

---

## 8. 修复建议

### P1 关键修复

| 建议 | 对应差距 | 所属 Item |
|------|---------|-----------|
| 实现 `OperatorStateStore` 接口 (`getListState`, `getUnionListState`, `getBroadcastState`) | #5 | Item 12a |
| 实现三种重分布模式 (SPLIT, UNION, BROADCAST) | #6 | Item 12b |
| 实现 `IOperatorStateBackend` 接口 | #7 | Item 12a |
| 在 `IStateBackend` 中添加 `createOperatorStateBackend()` | #9 | Item 12a |
| 实现 `TypeSerializerSnapshot<T>` 接口体系 | #15 | Item 13 (或独立 serialization plan) |
| 将 `FunctionInitializationContext` 暴露 OperatorStateStore 给 ICheckpointedFunction 用户 | #8 | Item 12a |

### P2 重要修复

| 建议 | 对应差距 | 所属 Item |
|------|---------|-----------|
| 增加 BroadcastState 类型 | #1 | Item 12b (broadcast 是 operator state 的一部分) |
| 引入 maxParallelism + 稳定哈希算法 (murmurHash) + KeyGroupRange | #12, #13, #14 | 独立 plan 或整合进 state backend 重构 |
| 实现异步两阶段 snapshot | #10 | Item 9 (checkpoint) |
| 实现 `StateTtlConfig` + TTL 装饰器 | #19, #20 | 独立 plan (高投入) |
| 将 `TypeSerializer` 接入实际序列化路径 | #16, #17 | 跟随 Item 15 的 serialization plan |

### P3 小改进

| 建议 | 对应差距 |
|------|---------|
| 考虑补充 MergingState 中间接口 (可选) | #2 (renumbered) |

---

## 9. 与 Plans 316/317 的协调说明

本分析原计划依赖 `01-flink-source-audit.md` 和 `02-nopstream-live-audit.md` 的 subsection 结构进行成对对比。由于两个 plan 仍为 `active` 状态且交付物不存在，本分析进行了直接的源码级对比。发现与已知缺口一致，并新发现了以下细节：

- 新发现: FunctionInitializationContext 不暴露任何 state store (#8)
- 新发现: StateShard 使用 `Object.hashCode()` 而非稳定哈希 (#13)
- 新发现: TypeSerializer 接口存在但完全未使用 (#16)
- 新发现: StreamModelFingerprint 只在 DAG 级别校验，不涉及序列化兼容性 (#18)

---

## 10. 引用索引

### Flink 类/方法引用

| 类 | 路径 |
|----|------|
| `State` | `flink-core/.../api/common/state/State.java` |
| `ValueState` | `flink-core/.../api/common/state/ValueState.java` |
| `ListState` | `flink-core/.../api/common/state/ListState.java` |
| `MapState` | `flink-core/.../api/common/state/MapState.java` |
| `ReducingState` | `flink-core/.../api/common/state/ReducingState.java` |
| `AggregatingState` | `flink-core/.../api/common/state/AggregatingState.java` |
| `AppendingState` | `flink-core-api/.../api/common/state/AppendingState.java` |
| `MergingState` | `flink-core-api/.../api/common/state/MergingState.java` |
| `BroadcastState` | `flink-core-api/.../api/common/state/BroadcastState.java` |
| `KeyedStateStore` | `flink-core/.../api/common/state/KeyedStateStore.java` |
| `OperatorStateStore` | `flink-core/.../api/common/state/OperatorStateStore.java` |
| `StateTtlConfig` | `flink-core/.../api/common/state/StateTtlConfig.java` |
| `CheckpointedFunction` | `flink-runtime/.../streaming/api/checkpoint/CheckpointedFunction.java` |
| `StateBackend` | `flink-runtime/.../runtime/state/StateBackend.java` |
| `KeyedStateBackend` | `flink-runtime/.../runtime/state/KeyedStateBackend.java` |
| `OperatorStateBackend` | `flink-runtime/.../runtime/state/OperatorStateBackend.java` |
| `DefaultOperatorStateBackend` | `flink-runtime/.../runtime/state/DefaultOperatorStateBackend.java` |
| `PartitionableListState` | `flink-runtime/.../runtime/state/PartitionableListState.java` |
| `KeyGroupRange` | `flink-runtime/.../runtime/state/KeyGroupRange.java` |
| `KeyGroupRangeAssignment` | `flink-runtime/.../runtime/state/KeyGroupRangeAssignment.java` |
| `TypeSerializer` | `flink-core/.../api/common/typeutils/TypeSerializer.java` |
| `TypeSerializerSnapshot` | `flink-core/.../api/common/typeutils/TypeSerializerSnapshot.java` |
| `TtlStateFactory` | `flink-runtime/.../runtime/state/ttl/TtlStateFactory.java` |

### nop-stream 类/方法引用

| 类 | 路径 |
|----|------|
| `State` | `nop-stream-core/.../common/state/State.java` |
| `ValueState` | `nop-stream-core/.../common/state/ValueState.java` |
| `ListState` | `nop-stream-core/.../common/state/ListState.java` |
| `MapState` | `nop-stream-core/.../common/state/MapState.java` |
| `ReducingState` | `nop-stream-core/.../common/state/ReducingState.java` |
| `AggregatingState` | `nop-stream-core/.../common/state/AggregatingState.java` |
| `AppendingState` | `nop-stream-core/.../common/state/AppendingState.java` |
| `InternalAppendingState` | `nop-stream-core/.../common/state/InternalAppendingState.java` |
| `InternalListState` | `nop-stream-core/.../common/state/InternalListState.java` |
| `KeyedStateStore` | `nop-stream-core/.../common/state/KeyedStateStore.java` |
| `ICheckpointedFunction` | `nop-stream-core/.../common/functions/ICheckpointedFunction.java` |
| `IStateBackend` | `nop-stream-core/.../common/state/backend/IStateBackend.java` |
| `IKeyedStateBackend` | `nop-stream-core/.../common/state/backend/IKeyedStateBackend.java` |
| `IInternalStateBackend` | `nop-stream-core/.../common/state/backend/IInternalStateBackend.java` |
| `MemoryStateBackend` | `nop-stream-core/.../common/state/backend/memory/MemoryStateBackend.java` |
| `MemoryKeyedStateBackend` | `nop-stream-core/.../common/state/backend/memory/MemoryKeyedStateBackend.java` |
| `MemoryStateSerDe` | `nop-stream-core/.../common/state/backend/memory/MemoryStateSerDe.java` |
| `StateShard.computeShardId` | `nop-stream-core/.../common/state/shard/StateShard.java:63` |
| `ShardPrefixedKey` | `nop-stream-core/.../common/state/shard/ShardPrefixedKey.java` |
| `SimpleKeyedStateStore` | `nop-stream-core/.../common/state/simple/SimpleKeyedStateStore.java` |
| `TaskStateSnapshot.putOperatorState` | `nop-stream-core/.../checkpoint/TaskStateSnapshot.java` |
| `OperatorSnapshotResult` | `nop-stream-core/.../checkpoint/OperatorSnapshotResult.java` |
| `StreamModelFingerprint` | `nop-stream-core/.../model/StreamModelFingerprint.java` |
| `TypeSerializer` | `nop-stream-core/.../common/typeutils/TypeSerializer.java` |
| `FunctionInitializationContext` | `nop-stream-core/.../checkpoint/FunctionInitializationContext.java` |
