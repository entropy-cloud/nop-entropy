# 状态管理设计

> Status: active（**核心接口和内存实现完成，无持久化后端，无内存控制**）
> Created: 2026-05-20
> Parent: `architecture.md` §3（执行模型）、`checkpoint-design.md` §3.3（状态快照）

## 1. 定位

状态管理是流处理引擎的核心子系统：算子在处理数据时维护的状态（如窗口聚合累加器、CEP 的 NFA 状态、Source 的消费偏移量）如何存储、如何序列化、如何在故障后恢复。nop-stream 采用了极简策略——纯内存 HashMap 存储 + JSON 序列化，去除了 Flink 的完整二进制序列化体系和多种状态后端。

### 1.1 设计决策

**选了什么**：纯内存 HashMap + Nop 平台通用 JSON 序列化（`JsonTool`）。

**为什么不用 Flink 的方案**：
- Flink 的 TypeSerializer 体系（30+ 序列化器）是为跨 JVM 传输和磁盘存储优化的二进制格式，nop-stream 当前运行时不需要跨进程序列化
- Flink 的 RocksDB 后端是为超大状态设计的（TB 级），nop-stream 定位于中等规模 ETL 场景
- JSON 序列化利用 Nop 平台已有的 `JsonTool`，无需维护独立的序列化框架

**代价**：
- 无内存控制——状态只增长不收缩（除窗口触发清理），大状态场景可能 OOM
- 序列化效率低于二进制格式——JSON 体积更大、解析更慢
- 无状态重分布——不支持并行度变更后重新分配状态

## 2. 状态类型体系

### 2.1 状态接口层次

```
State (clear)
├── ValueState<T>              (value, update)
├── MapState<UK, UV>           (get, put, remove, entries, keys, values)
├── ListState<T>               (add, addAll, update, get)          ← 仅 Internal 使用
└── AppendingState<IN, OUT>    (add, get)
    └── InternalAppendingState<K,N,IN,ACC,OUT>  (+setCurrentNamespace, getAccumulator)
```

> **注意**：`ListState` 不通过 `KeyedStateStore` 暴露给用户。它只作为 `InternalListState<K,N,T>` 存在于 `IInternalStateBackend` 中，由 WindowOperator 用于合并窗口元数据存储。

**Internal 前缀的含义**：`InternalAppendingState` 和 `InternalListState` 支持泛型 namespace（如 Window 对象），用于 WindowOperator 等需要按 namespace 分区状态的场景。对应的 `IInternalStateBackend` 提供这些 Internal 状态的创建方法。

### 2.2 StateDescriptor

| 属性 | 类型 | 含义 |
|---|---|---|
| `name` | String | 状态名称（唯一标识） |
| `valueType` | Class<T> | 值的类型（用于 JSON 序列化） |
| `defaultValue` | T | 默认值 |

**与 Flink 的差异**：Flink 的 `StateDescriptor` 还持有 `TypeSerializer` 和 `TypeSerializerSnapshot`，支持自定义序列化和 schema evolution。nop-stream 去除了这些，仅保留 `Class<T>` 用于 JSON 序列化时的类型推断。

### 2.3 Namespace

| 类型 | 用途 | 使用者 |
|---|---|---|
| `VoidNamespace` | 不需要 namespace 时的占位符 | SimpleKeyedStateStore、普通 key-value 操作 |
| 泛型 N（通常为 Window） | 按 namespace（窗口）分区状态 | WindowOperator、合并窗口 |

`VoidNamespace` 是单例，配合 `VoidNamespaceSerializer` 使用。

## 3. 状态后端

### 3.1 接口层次

```
IStateBackend (getName, createKeyedStateBackend)
└── MemoryStateBackend           → new MemoryKeyedStateBackend<K>
                                    ↑ 唯一实现

IKeyedStateBackend<K> (setCurrentKey, getState, getMapState)
└── IInternalStateBackend<K>     (+ getInternalAppendingState, getInternalListState)
    └── MemoryKeyedStateBackend<K>
```

> **注意**：`KeyedStateStore`（`IKeyedStateBackend` 的父接口）只暴露了 `getState()` 和 `getMapState()`。`getListState()`、`getReducingState()`、`getAggregatingState()` 均被注释掉。`ListState` 只能通过 `IInternalStateBackend.getInternalListState()` 访问，用于 WindowOperator 合并窗口元数据。

### 3.2 MemoryStateBackend

唯一的状态后端实现。`createKeyedStateBackend(keyType)` 直接创建 `MemoryKeyedStateBackend` 实例。

**特征**：
- 所有状态存储在 JVM 堆内存
- 实现 `Serializable`（但重启后状态丢失，因为没有从持久化恢复的路径）
- 无大小限制、无 TTL、无驱逐策略

### 3.3 MemoryKeyedStateBackend 的存储结构

```
MemoryKeyedStateBackend<K>
└── states: Map<String, Object>              // stateName → State 实例
    ├── MemoryValueState<T>
    │   └── storage: HashMap<TypedNamespaceAndKey, T>
    ├── MemoryMapState<UK, UV>
    │   └── storage: HashMap<TypedNamespaceAndKey, Map<UK, UV>>
    ├── MemoryInternalAppendingState<K,N,IN,ACC>
    │   └── storage: HashMap<TypedNamespaceAndKey, ACC>
    └── MemoryInternalListState<K,N,T>
        └── storage: HashMap<TypedNamespaceAndKey, List<T>>
```

**组合键**：`TypedNamespaceAndKey = (Object namespace, Object key)`。所有状态以 `(namespace, key)` 二元组为存储键，支持泛型 namespace。

**状态实例缓存**：同一个 `stateName` 只创建一次状态实例，后续 `getState()` 返回缓存实例。

**MemoryInternalAppendingState 的 accumulator 复用**：每个状态实例共享一个 `SimpleAccumulator<IN>`（构造时创建）。`add()` 方法在每次调用时：先从 storage 取出当前累加值 → 重置 accumulator → 加入旧累加值和新值 → 将 `getLocalValue()` 存回 storage。这意味着 accumulator 不是按 key 隔离的，而是每次 add 时临时复用（详见 §8.5 的已知限制）。

### 3.4 SimpleKeyedStateStore

**非键控**的全局状态存储。用于不需要 key 隔离的场景（如 CepOperator）。

**特征**：
- `ValueState` 用单个字段存储，无 key 分区
- `MapState` 用单个 `HashMap<UK, UV>` 存储
- ListState/ReducingState/AggregatingState 方法被注释掉（`UnsupportedOperationException`）
- 带有 `stateReads` / `stateWrites` 计数器（用于统计）

**与 MemoryKeyedStateBackend 的区别**：`MemoryKeyedStateBackend` 按 `(namespace, key)` 分区，`SimpleKeyedStateStore` 不感知 key，所有 key 共享同一状态。

## 4. 序列化策略

### 4.0 核心原则

1. **对象存取，屏蔽序列化**：状态存取接口是对象级别的。算子代码调用 `putState(name, object)` / `<T> getState(name, type)`，不需要关心内部如何序列化。序列化是实现细节，不是接口契约。
2. **所有内部结构必须支持 JSON 序列化**：Nop 平台要求所有内部数据结构（包括 Window 子类、状态 key/value、namespace 等）都能通过 `JsonTool` 进行 JSON round-trip。这是添加新类型的强制前置约束。
3. **默认 JSON，可替换**：内部默认使用 `JsonTool`（JSON 序列化），但序列化策略可替换（如未来引入二进制格式），不影响调用方。
4. **byte[] 不出现在任何接口上**：序列化产物（`byte[]`、JSON 字符串等）仅在持久化实现层内部使用。从 StateBackend 到 CheckpointStorage 的所有接口都是对象级别的，不传递 `byte[]`。

### 4.1 运行时：无序列化

运行时状态以 Java 对象直接存储在 HashMap 中。`MemoryValueState` 存 `T`，`MemoryMapState` 存 `Map<UK, UV>`，没有序列化/反序列化开销。

**代价**：状态对象就是用户对象的直接引用——修改用户对象会直接修改状态（与 Flink 的深拷贝语义不同）。

### 4.2 Checkpoint 快照：JSON 序列化（内部实现）

Checkpoint 时状态通过 Nop 平台的 `JsonTool` 序列化为 JSON。这是内部实现细节，对外接口是对象级别的。

**整条链路的对象接口**：

```
算子层     putState(name, object) / getState(name, type)      ← 对象接口
  ↓
Backend层  snapshotState() → StateSnapshot / restoreState(StateSnapshot)  ← 对象接口
  ↓
Storage层  storeCheckPoint(CompletedCheckpoint)                ← 对象接口
  ↓        getLatestCheckpoint() → CompletedCheckpoint
  ↓
实现层     内部: JsonTool.serialize() → JSON → 持久化          ← 实现细节
```

每一层都是对象存取，序列化只在**持久化实现层**内部发生，不向上层暴露。

**调用方视角**：
```java
// 算子存状态 — 直接传对象
snapshot.putState("my-state", myStateObject);

// 算子取状态 — 指定类型，直接拿对象
MyState state = snapshot.getState("my-state", MyState.class);

// CheckpointStorage 存取 — 直接传对象
storage.storeCheckPoint(completedCheckpoint);
CompletedCheckpoint restored = storage.getLatestCheckpoint(jobId, pipelineId);
```

**快照内部格式要求**：
- 快照必须包含类型元信息（stateType、valueType、keyType），确保反序列化时能正确重建对象
- 用作 namespace 的类型必须支持 `JsonTool` round-trip（如 TimeWindow、GlobalWindow）
- 序列化策略可替换，只要满足对象存取接口契约

`JdbcCheckpointStorage` 和 `LocalFileCheckpointStorage` 内部使用 `JsonTool.serialize()` 将 `CompletedCheckpoint` 序列化后持久化，反序列化时用 `JsonTool.parseBeanFromText()` 还原。这些是内部实现，调用方不感知。

### 4.3 TypeSerializer 接口

nop-stream 保留了 Flink 的 `TypeSerializer<T>` 接口，但大幅简化：

| 方法 | 含义 | 实际用途 |
|---|---|---|
| `isImmutableType()` | 是否不可变类型 | 仅 VoidNamespaceSerializer 实现 |
| `duplicate()` | 深拷贝 serializer | 未使用 |
| `createInstance()` | 创建类型实例 | 未使用 |
| `copy(T)` / `copy(T, T)` | 深拷贝元素 | 未使用 |
| `getLength()` | 固定长度类型的字节长度 | 未使用 |

**关键差异**：Flink 的 `TypeSerializer` 有 `serialize(T, DataOutputView)` 和 `deserialize(DataInputView)` 方法。nop-stream 的版本**没有序列化方法**——这是一个纯结构接口，唯一的实现是 `VoidNamespaceSerializer`。

### 4.4 为什么不用 Flink 的二进制序列化

| 维度 | Flink 二进制序列化 | Nop JSON 序列化 |
|---|---|---|
| 性能 | 高（定长编码、零拷贝） | 低（文本解析、反射） |
| 体积 | 紧凑 | 2-5x 膨胀 |
| 开发成本 | 需维护 30+ TypeSerializer | 利用 Nop 平台已有能力 |
| 跨语言 | 不支持（Java 特定） | JSON 天然跨语言 |
| Schema Evolution | 支持（TypeSerializerSnapshot） | 有限（字段增删兼容） |
| 适用场景 | 大规模分布式流处理 | 单机、中等数据量 |

**设计判断**：nop-stream 定位于简化流处理，状态量通常在 GB 级别以下。JSON 序列化的性能损失在可接受范围内，换来了零序列化框架维护成本和与 Nop 平台的无缝集成。如果未来需要更高性能的序列化，可通过 `IStateBackend` 扩展点引入二进制序列化。

## 5. 内存模型与消耗控制

### 5.1 当前状态：无控制

nop-stream **没有**任何内存控制机制：

| 机制 | 状态 |
|---|---|
| 状态大小限制 | ❌ 无 |
| TTL（自动过期） | ❌ 无 |
| LRU 驱逐 | ❌ 无 |
| Spill to disk | ❌ 无 |
| Off-heap 存储 | ❌ 无 |
| 引用计数 / GC 辅助 | ❌ 无 |

状态的生命周期完全由使用者控制：
- **WindowOperator**：窗口触发后调用 `state.clear()` 清理该窗口的状态
- **CEP SharedBuffer**：NFA 中不再需要的条目通过 `.prune` 操作清理
- **Source offset**：每次 checkpoint 时覆盖更新

### 5.2 主要内存消耗点

| 消耗点 | 数据结构 | 增长模式 | 回收方式 |
|---|---|---|---|
| 窗口聚合状态 | `HashMap<(namespace,key), ACC>` | 每条记录 add 一次 | 窗口触发后 clear |
| 窗口内容状态 | `HashMap<(namespace,key), List<T>>` | 每条记录 add 一次 | 窗口触发后 clear |
| CEP SharedBuffer | `Map<eventId, NodeMap>` | 每条记录可能创建多个条目 | prune 操作清理 |
| Source offset | 单个值 | 覆盖更新 | 无需回收 |
| Watermark 状态 | 不涉及 | — | — |

### 5.3 风险分析

**窗口状态**：
- Keyed Window：每个 `(key, window)` 组合一个 ACC。如果 key 基数大 + 窗口长，ACC 数量可能很大
- Session Window：合并窗口场景，`InternalListState` 存储合并窗口的元数据
- **最大风险**：滑动窗口（Slide）——同一记录出现在多个窗口中，ACC 数量 = key 数 × 窗口重叠数

**CEP 状态**：
- SharedBuffer 中的事件引用在模式匹配完成前不会清理
- 复杂模式 + 高吞吐 → 大量中间状态

**缓解因素**：
- nop-stream 定位单机 ETL，通常 key 基数有限
- 窗口触发后状态自动清理
- 事件时间模式下，watermark 推进会触发过期窗口

### 5.4 未来方向

`IStateBackend` 接口注释中提到 `RedisStateBackend`（未实现）。如果需要更大的状态容量，可能的演进路径：

1. **RedisStateBackend**：状态存储在 Redis 中，内存由 Redis 管理（淘汰策略 maxmemory-policy）
2. **TTL 支持**：在 StateDescriptor 中增加 `TTL` 配置，状态后端自动过期清理
3. **基于 Nop IGrid 的分布式状态**：利用 Nop 平台的分布式缓存能力
4. **状态快照增量优化**：当前 Full snapshot，大数据量下可考虑增量 snapshot

## 6. 与 Flink 的完整对比

| 维度 | Flink | nop-stream |
|---|---|---|
| **状态后端** | Memory / FileSystem / RocksDB / EmbeddedRocksDBStateBackend | 仅 Memory |
| **序列化框架** | 完整 TypeSerializer 体系（IntSerializer、StringSerializer、PojoSerializer、AvroSerializer 等 30+） | JSON（`JsonTool`），运行时不序列化 |
| **TypeSerializer 接口** | serialize/deserialize/copy/createInstance/getLength | 仅 isImmutableType/duplicate/copy/createInstance/getLength（无序列化方法） |
| **Key-Group** | 有（支持并行度变更后状态重分布） | 无 |
| **OperatorStateBackend** | 有（ListState + UnionListState + BroadcastState） | 无 |
| **状态 TTL** | `StateTtlConfig` 完整支持（清理策略：OnReadAndWrite / FullSnapshotCleanup） | 无 |
| **内存管理** | Managed Memory（RocksDB off-heap）+ State Backend 自管理 | JVM 堆 HashMap，无管理 |
| **Spill to disk** | RocksDB 后端自动 spill | 无 |
| **增量 Checkpoint** | 支持（RocksDB 基于 SST 文件） | 不支持（Full snapshot） |
| **状态重分布** | Key-Group range redistribution + OperatorState 重分布 | 不支持 |
| **Schema Evolution** | TypeSerializerSnapshot 支持序列化器升级 | 有限（JSON 字段兼容） |
| **异步快照** | AsyncSnapshot（RocksDB） | 同步 |
| **状态查询** | Queryable State（实验性） | 无 |

### 6.1 保留的能力

尽管大幅简化，nop-stream 保留了以下 Flink 状态管理的关键抽象：

- **StateDescriptor** — 状态声明的统一入口
- **ValueState / MapState / ListState** — 核心状态类型接口
- **KeyedStateBackend** — 按 key 分区的状态存储抽象
- **Namespace 隔离** — 支持 WindowOperator 的多窗口状态隔离
- **AppendingState / InternalAppendingState** — 窗口聚合的基础

### 6.2 去除的能力

| 能力 | Flink 理由 | nop-stream 简化理由 |
|---|---|---|
| 二进制序列化体系 | 跨 JVM 传输、磁盘存储效率 | 单 JVM 运行，JSON 足够 |
| Key-Group | 分布式状态重分布 | 单 JVM，不需要重分布 |
| OperatorStateBackend | Source/ Sink 的非键控状态 | 简化为 SimpleKeyedStateStore |
| RocksDB 后端 | TB 级状态、off-heap | 单机 ETL，GB 级状态 |
| TTL | 长期运行的状态自动过期 | 当前无此需求，可后续扩展 |
| 异步快照 | 大状态场景不阻塞处理 | 同步快照足够（状态量小） |

## 7. 状态在各算子中的使用

| 算子 | 状态类型 | State Name | 用途 |
|---|---|---|---|
| WindowOperator | MapState (windowContents) | 用户定义 | 存储窗口内元素（非聚合） |
| WindowOperator | InternalAppendingState (windowAgg) | 用户定义 | 存储窗口聚合累加器 |
| WindowOperator | InternalListState (mergingWindowSet) | `_merging_window_set` | 合并窗口的元数据 |
| CepOperator | SimpleKeyedStateStore | NFA 状态 | SharedBuffer、NFA 状态 |
| Source | 无（通过 CheckpointedSourceFunction 管理） | — | offset 存在 TaskStateSnapshot 中 |
| Map/FlatMap | 可选（用户函数通过 RuntimeContext 访问） | 用户定义 | 用户自定义状态 |

## 8. 已知限制

1. **无内存控制** — 状态只增长不收缩（除窗口触发清理），无 TTL/驱逐/spill。大状态场景可能 OOM
2. **JSON 序列化性能** — Checkpoint 持久化使用 JSON，体积和速度均不如二进制格式
3. **SimpleKeyedStateStore 无 key 隔离** — CepOperator 使用 SimpleKeyedStateStore，所有 key 共享状态。如果 CEP 需要按 key 隔离，需要改用 MemoryKeyedStateBackend
4. **状态对象是引用** — MemoryValueState 直接存储用户对象引用，没有深拷贝。用户代码意外修改对象会影响状态一致性
5. **MemoryInternalAppendingState 的 accumulator 复用问题** — 单个 accumulator 实例在 add() 时先重置再加入，多线程不安全，且 `getLocalValue()` 的累加器存在初始值问题（详见 `window-design.md` §5.3）
6. **无状态恢复路径** — 虽然 Checkpoint 序列化代码存在，但 `AbstractUdfStreamOperator.snapshotState()` 被注释掉，当前运行时不实际执行状态快照
7. **JSON 序列化类型约束** — 所有通过 checkpoint 持久化的内部结构（包括 Window 子类、状态 key/value）必须满足 `JsonTool` round-trip 要求。新增 Window 子类或状态类型时，这是强制前置约束
