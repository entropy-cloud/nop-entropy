# 状态管理设计

> Status: active
> Created: 2026-05-20
> Revised: 2026-06-01
> Parent: `01-architecture-baseline.md` §4（执行模型）、`checkpoint-design.md` §3.3（状态快照）

## 1. 定位

状态管理负责算子处理数据时维护的状态（窗口聚合累加器、CEP NFA 状态、Source 消费偏移量）如何存储、序列化、分段持久化和故障恢复。

nop-stream 采用纯内存 HashMap 存储 + JSON 序列化的极简策略，同时定义了分布式场景下的 `StateShard` 分片和 `StatePath` 持久化路径规则。

## 2. 状态类型体系

### 2.1 状态接口层次

```
State (clear)
├── ValueState<T>              (value, update)
├── MapState<UK, UV>           (get, put, remove, entries, keys, values)
├── ListState<T>               (add, addAll, update, get)          ← 仅 Internal
└── AppendingState<IN, OUT>    (add, get)
    └── InternalAppendingState<K,N,IN,ACC,OUT>  (+setCurrentNamespace, getAccumulator, setAccumulator)
```

`ListState` 不通过 `KeyedStateStore` 暴露给用户，只作为 `InternalListState<K,N,T>` 存在于 `IInternalStateBackend` 中，由 WindowOperator 用于合并窗口元数据存储。

`InternalAppendingState` 和 `InternalListState` 支持泛型 namespace（如 Window 对象），用于按 namespace 分区状态的场景。

`InternalAppendingState` 通过 `IInternalStateBackend` 的两个 `getInternalAppendingState` 重载创建，分别对应不同的累积模式（详见 §5.1）：

| 重载 | 描述符 | 返回类型 | 累积模式 |
|---|---|---|---|
| `getInternalAppendingState(ReducingStateDescriptor<IN>)` | `ReducingStateDescriptor` | `InternalAppendingState<K,N,IN,IN,IN>` | IN==ACC==OUT，ReduceFunction 归约（reduce 语义为 (IN,IN)→IN） |
| `getInternalAppendingState(AggregatingStateDescriptor<IN,ACC,OUT>)` | `AggregatingStateDescriptor` | `InternalAppendingState<K,N,IN,ACC,OUT>` | AggregateFunction 累积，支持 ACC≠OUT |

### 2.2 StateDescriptor

| 属性 | 类型 | 含义 |
|---|---|---|
| `name` | String | 状态名称（唯一标识） |
| `valueType` | Class\<T\> | 值的类型（用于 JSON 反射，**非**二进制序列化） |
| `defaultValue` | T | 默认值 |
| `serializer` | TypeSerializer\<T\> | 序列化器引用（默认 `JsonToolSerializer<T>`，类型与 descriptor 的 T 一致） |

### 2.3 Namespace

| 类型 | 用途 | 使用者 |
|---|---|---|
| `VoidNamespace` | 不需要 namespace 时的占位符 | SimpleKeyedStateStore |
| 泛型 N（通常为 Window） | 按 namespace 分区状态 | WindowOperator、合并窗口 |

### 2.4 MergingState 抽象：设计决策（G62）

> **决策**：当前**不**引入 `MergingState`（一个把「合并语义」从 `WindowOperator` 内联代码抽离出来的状态类型抽象层）。判定为 `optimization candidate`，延后至有真实消费方的 successor。

**背景**：Flink 用 `MergingState` 接口把 session-window 的状态合并语义建模为 state 层能力（`mergeNamespaces` + accumulator merge）。nop-stream 当前没有这层抽象——窗口合并是 `WindowOperator` 的内联逻辑：

| 合并调用点 | 位置 | 现有实现 |
|---|---|---|
| 窗口元数据合并 | `WindowOperator.mergeWindowContents()`（`:1294`） | 内联遍历 sourceWindows，逐个归并到 targetWindow |
| ACC 归并（Reduce/Aggregate） | `:1333` | `mergeFunction.apply(targetValue, sourceValue)` |
| ACC 归并（Aggregate ACC） | `:1417` | `accumulator.merge((SimpleAccumulator<ACC>) sourceValue)` |

**为何不在本批引入**（Anti-Hollow）：

1. **无消费方即空壳**：`MergingState` 若仅作为新接口存在而 `WindowOperator.mergeWindowContents()` 仍用现有内联逻辑，则接口无真实消费者，构成 Hollow Implementation（接口/单测通过但运行时路径未变）。
2. **引入须同时迁移调用点**：要让接口有真实消费方，必须把 `mergeWindowContents()` 的三个合并调用点（`:1333` / `:1417`）迁移到 `MergingState` API，这属于行为保持的重构优化（非纯清理），超出「代码清理」plan 的范围。
3. **收益有限**：当前唯一 merge 场景是 session window，调用点集中在一个方法内，抽象的复用收益尚未出现第二个合并语义消费者。

**Successor 路径**：未来 window/state 重构 plan 应同时完成（a）定义 `MergingState` 接口 + state backend 支持，与（b）迁移 `WindowOperator.mergeWindowContents()` 三个调用点为该接口的消费者，二者一并交付以避免空壳。

## 3. StateShard

分布式状态下，keyed state 需要稳定的逻辑分片以支持跨节点定位和恢复。nop-stream 引入 `StateShard`：

| 属性 | 说明 |
|---|---|
| `stateShardCount` | 一个 keyed vertex 的逻辑状态分片总数，作业生命周期内稳定 |
| `stateShardId` | `0 <= id < stateShardCount` 的逻辑分片编号 |
| `ownerSubtask` | 当前 plan 中拥有该 shard 的 subtask |
| `hashPolicy` | key 到 shard 的确定性 hash 规则 |

**路由规则**：`stateShardId = stableHash(normalizedKey) mod stateShardCount`

`StateShard` 不是 Flink key-group 的照搬，只承担稳定状态路由职责，不引入 Flink 的序列化器或 ExecutionGraph 结构。

`stateShardCount` 默认不可改变。改变等价于 keyed state 重分片，必须提供显式 migration action 和校验报告。

## 4. StatePath

状态持久化路径由模型确定，不含运行时临时身份。

**Keyed state**：

```
checkpoint/{checkpointNamespace}/{epochId}/{operatorId}/{subtaskIndex}/{stateShardId}/{stateName}
```

**Non-keyed operator state**：

```
checkpoint/{checkpointNamespace}/{epochId}/{operatorId}/{subtaskIndex}/operator/{stateName}
```

**Source split state**：

```
checkpoint/{checkpointNamespace}/{epochId}/{operatorId}/{subtaskIndex}/source/{splitId}
```

**Sink transaction state**：

```
checkpoint/{checkpointNamespace}/{epochId}/{operatorId}/{subtaskIndex}/sink/{transactionId}
```

**约束**：路径只由稳定身份（jobId、pipelineId、operatorId、subtaskIndex、stateShardId）、epoch 和 state name 构成，不能包含对象内存地址、临时 index 或本地文件路径。

## 5. 状态后端

### 5.1 接口层次

```
IStateBackend (getName, createKeyedStateBackend, createOperatorStateBackend)
├── MemoryStateBackend           → new MemoryKeyedStateBackend<K>
└── RocksDBStateBackend          → new RocksDBKeyedStateBackend<K>

IKeyedStateBackend<K> (setCurrentKey, getState, getMapState)
└── IInternalStateBackend<K>     (+ getInternalAppendingState×2, getInternalListState)
    ├── MemoryKeyedStateBackend<K>
    └── RocksDBKeyedStateBackend<K>

IInternalStateBackend.getInternalAppendingState 有两个重载：
  • getInternalAppendingState(ReducingStateDescriptor<IN>)
      → InternalAppendingState<K, N, IN, ACC, ACC>    // OUT==ACC 的 reducing 模式
  • getInternalAppendingState(AggregatingStateDescriptor<IN, ACC, OUT>)
      → InternalAppendingState<K, N, IN, ACC, OUT>    // AggregateFunction 累积模式，支持 ACC≠OUT
```

`KeyedStateStore`（`IKeyedStateBackend` 的父接口）只暴露 `getState()` 和 `getMapState()`。`ListState` 只能通过 `IInternalStateBackend.getInternalListState()` 访问。

### 5.2 MemoryStateBackend

唯一的状态后端实现。所有状态存储在 JVM 堆内存。

- 实现 `Serializable`（但重启后状态丢失）
- 无大小限制、无 TTL、无驱逐策略

### 5.3 RocksDBStateBackend

第二个状态后端实现（Stage 30 交付）。所有 keyed state 存储在 off-heap 的 RocksDB 列族中，突破 JVM 堆内存上限。

- 独立模块 `nop-stream-rocksdb`（`rocksdbjni` ~58MB native jar 隔离在此模块，不污染 `nop-stream-core`）
- 每个注册的 state 对应一个 RocksDB column family
- Key 编码：`[nsLen][nsJsonBytes][shardId][keyLen][keyJsonBytes]`，namespace 序列化与 `MemoryStateSerDe` 一致
- Value 编码：JSON via `JsonTool`（与 memory backend 序列化体系一致）
- `RocksDBKeyedStateBackend<K>` 实现 `IInternalStateBackend<K>`，所有 keyed state 类型（Value/Map/List/Reducing/Aggregating + Internal 变体）由列族承载
- 快照格式与 `MemoryStateSerDe` byte-compatible（8 种 stateType、per-type info keys、entry discriminators、raw-key 不变量），实现 checkpoint 跨后端互换
- Operator state 复用 `MemoryOperatorStateBackend`（operator state 量小，非 off-heap 目标）
- 配置：`RocksDBStateBackend(dbPath, shardCount, RocksDBOptionConfig)`，最小配置项为 db path、write buffer size、max background threads

**使用方式**：
```java
env.setStateBackend(new RocksDBStateBackend("/data/rocksdb", 1, new RocksDBOptionConfig()));
```

**限制**：Stage 30 使用全量扫描快照（非增量 checkpoint），单线程访问模型（mailbox 保证）。

### 5.4 MemoryKeyedStateBackend 存储结构

```
MemoryKeyedStateBackend<K>
└── states: Map<String, Object>
    ├── MemoryValueState<T>           → HashMap<TypedNamespaceAndKey, T>
    ├── MemoryMapState<UK, UV>        → HashMap<TypedNamespaceAndKey, Map<UK, UV>>
    ├── MemoryInternalAppendingState  → HashMap<TypedNamespaceAndKey, ACC>     // ReducingStateDescriptor
    ├── MemoryInternalAggregatingState → HashMap<TypedNamespaceAndKey, ACC>     // AggregatingStateDescriptor
    └── MemoryInternalListState       → HashMap<TypedNamespaceAndKey, List<T>>
```

组合键：`TypedNamespaceAndKey = (Object namespace, Object key)`。

### 5.5 SimpleKeyedStateStore

非键控的全局状态存储。`ValueState` 用单个字段，`MapState` 用单个 HashMap。不感知 key，所有 key 共享同一状态。用于 CepOperator 等不需要 key 隔离的场景。

**限制**：分布式 exactly-once 作业的 CEP operator 不可使用 `SimpleKeyedStateStore`，必须接入统一 state backend。

## 6. 序列化策略

### 6.1 核心原则：序列化是内部实现细节

nop-stream 的序列化设计遵循一个铁律：**Store 接口只存取对象，序列化是否发生完全是 Store 的内部实现细节，对算子和用户不可见。**

| 层 | 接口 | 是否感知序列化 |
|---|---|---|
| 算子层 | `putState(name, object)` / `getState(name, type)` | **不感知** |
| Backend 层 | `snapshotState() → StateSnapshot` / `restoreState(snapshot)` | **不感知** |
| Storage 层 | `storeCheckpoint(CompletedCheckpoint)` / `getCheckpoint(id)` | **不感知** |
| Store 实现层（内部） | Memory store：直接对象引用，**零序列化**；持久化 store：内部 `JsonTool` | 内部 |

**关键含义：**

1. **内存 Store 零序列化**：`MemoryKeyedStateBackend` 直接存储对象引用（`HashMap`），checkpoint 快照也是对象引用传递。从算子到 store 到快照，**没有任何序列化/反序列化开销**。只有当 checkpoint 需要持久化到外部（文件、数据库）时，才在 storage 实现内部使用 `JsonTool`。

2. **不暴露序列化接口**：`StateDescriptor` **不携带** serializer 引用。`IStreamSerializer` / `TypeSerializer` 接口**不向上暴露**。算子代码只调用 `getState()` / `putState()`，不接触任何序列化 API。

3. **不采用 Flink Type-based 二进制序列化**：Flink 根据 `TypeInformation` 为每种类型生成专用 `TypeSerializer`，导致大量业务代码被序列化逻辑污染（每个 `StateDescriptor` 必须绑定 serializer、每种数据结构需要配套的 serializer 工厂、schema 演进需要 `TypeSerializerSnapshot` 全套机制）。nop-stream 明确拒绝此路径：
   - JSON 序列化通过类型反射工作，**对象上不需要任何特殊支持**（无 `@Serializable`、无 serializer 工厂、无 `TypeInfo` 注解）
   - 业务对象保持 POJO 纯净性，不被序列化代码污染
   - 唯一的序列化实现是 `JsonTool`，没有第二个实现，也不需要 SPI 扩展点

4. **Flink 序列化体系的反面教训**：Flink 的 `TypeSerializer` 体系为了性能引入了极高的复杂度——`TypeSerializerSnapshot`、`TypeSerializerConfigSnapshot`、schema 兼容性四态解析、每种容器类型的专用 serializer（`ListSerializer`、`MapSerializer`、`RowSerializer`...）。这些代码渗透到 Flink 几乎每个模块。nop-stream 用 JSON 反射 + `JsonTool` 一行代码替代这套体系，代价是序列化性能略低（适合几十 GB 级状态），换来的是**业务代码零污染**。

### 6.2 控制面元数据

- **metadata**：plan、manifest、state segment descriptor 必须可 JSON round-trip
- **payload**：默认 JSON（`JsonTool`）。descriptor 记录 schema version + checksum 用于兼容性检查
- **schema version**：每个 state name 必须记录 value schema version
- **checksum**：每个 segment 必须有 checksum
- **compatibility**：savepoint 恢复时通过 `SerializerFingerprint` 比对 schema version + checksum

### 6.3 Schema 兼容性（内部实现）

schema 演进兼容性检查是 **checkpoint storage 的内部实现细节**，不向上暴露给算子或用户。算子只调用 `getState()` / `putState()`，不知道也不需要知道 schema 指纹的存在。

**内部实现可选方案**：

- 平台已有的 `record-object.xdef` 对象描述机制可以用于生成结构化的 schema 描述（字段名、类型、编码规则）
- 但具体采用哪种 schema 描述技术是 **storage 实现的内部决策**，不在 store 接口上暴露，也不在设计文档中固定为实现耦合

**`SerializerFingerprint` 的定位**：

`SerializerFingerprint` 不是用户 API，而是 checkpoint manifest 内部记录的元数据。它的作用是：持久化 store 在恢复时**内部**比对 savepoint 的 schema 与当前代码的 schema 是否兼容。如果发现不兼容，store 内部决定拒绝恢复或触发已注册的 `StateMigrationFunction`。这一切对算子透明。

| 字段 | 含义 | 暴露层级 |
|---|---|---|
| `stateName` | 状态名 | 算子已知（StateDescriptor.name） |
| `schemaVersion` | schema 版本号 | **内部**（store 自动管理） |
| `schemaChecksum` | schema 结构 checksum | **内部**（store 自动生成） |

用户唯一需要做的：如果 schema 发生了不兼容变更（如字段改名、类型变更），注册一个 `StateMigrationFunction`。其余全自动。

**Stage 29 落地的最终实现**（`SerializerFingerprint.java` + `StateSchemaResolver.java` in `nop-stream-core`）：

- **checksum 算法**：type-signature 级别 SHA-256 hex digest，不采用 deep POJO field-level introspection。canonical 字符串由 `stateType` + `valueType` class FQN + 可选的 `mapKeyType` / `accumulatorType` / `aggregateFunctionType` class FQN 按固定顺序拼接，字段缺失时省略（而非输出空值），保证同一逻辑类型签名跨 JVM 重启稳定。
- **checksum 嵌入位置**：checksum 嵌入 `MemoryStateSerDe` 写出的 per-state JSON info map（`schemaChecksum` + `schemaVersion=1` 两个 key），随 `StateSnapshot.stateData` → `TaskStateSnapshot.keyedStates` → `CheckpointSerDe` → JSON 自动传播。**不引入** `OperatorSnapshot` 外层 wrapper。
- **比对时机**：在 `MemoryKeyedStateBackend.getState()` 时比对 —— 拿当前算子传入的 descriptor 算出的 checksum 与恢复出来的 state 对象上保存的 descriptor 算出的 checksum 比较。两次比较来自两个独立来源（live code vs checkpoint），不是 tautology。design 原文描述的"恢复时由 storage 层比对"在实现上向下游移到 state backend 层 —— 因为恢复出的 descriptor 在 `MemoryStateSerDe.restoreState` 时才重建，而 `getState()` 是算子真正消费恢复 state 的入口点，在那里 fail-fast 最自然。
- **不兼容即 fail-fast**：checksum 不匹配时抛 `ERR_STREAM_STATE_SCHEMA_MISMATCH`。Stage 33 会在此路径之前查询已注册的 `StateMigrationFunction`，注册了则迁移、未注册才 fail-fast。
- **schemaVersion 当前恒为 1**：Stage 29 不激活 version-based branching（design `checkpoint-design.md` 中 lower→migrate / higher→reject 的逻辑）。`schemaVersion=1` 仅作为前向兼容元数据持久化。version-based 分支需要 Stage 33 的迁移基础设施才有意义。
- **向后兼容**：旧 checkpoint 不含 `schemaChecksum`/`schemaVersion` 字段也能恢复 —— 因为 `getState()` 检查比较的是恢复出的 descriptor 与当前 descriptor，两者都从代码侧的 type 信息重建，不会引用持久化的 checksum 字段。持久化字段仅用于人工 inspect 和 Stage 33 未来用途。

### 6.4 JSON 约束

所有通过 checkpoint 持久化的内部结构（包括 Window 子类、状态 key/value）必须满足 `JsonTool` round-trip 要求。新增 Window 子类或状态类型时，这是强制前置约束。

## 7. State Segment

分布式恢复需要 segment 化的状态快照，而非一整张大 Map。

| Segment 类型 | 粒度 |
|---|---|
| operator state | `operatorId + subtaskIndex + stateName` |
| keyed state | `operatorId + subtaskIndex + stateShardId + stateName` |
| timer | `operatorId + subtaskIndex + stateShardId + timer domain` |
| source | `operatorId + subtaskIndex + splitId` |
| sink | `operatorId + subtaskIndex + transactionId` |

Segment 由 LocalFile、JDBC、对象存储或其他 backend 持久化。Epoch manifest 只记录 segment 引用和校验信息。

## 8. Timer State

窗口和 CEP 的 timer 必须进入 checkpoint。

| 字段 | 说明 |
|---|---|
| timer domain | event-time 或 processing-time |
| timestamp | 触发时间 |
| key / stateShard | keyed timer 的归属 |
| namespace | window 或 CEP namespace |
| callback owner | operatorId 和 timer service 身份 |

**Processing-time timer**：不提供确定性重放语义（依赖 wall clock）。恢复时已过期的 processing-time timer 可立即触发或按策略延迟触发，但 operator state 和 sink epoch commit 不能产生重复外部副作用。需要确定性结果的逻辑应优先使用 event-time timer。

不 checkpoint timer 的窗口实现不能声明支持 exactly-once 恢复。

## 9. 内存预算

### 9.1 MemoryBudget

`DeploymentPlan` 包含内存预算配置：

```java
class MemoryBudget {
    long totalBytes;
    Map<String, Long> componentAllocations;  // component → bytes
    // component: state backend、edge queues、network buffers
}
```

**初始分配**：

```
stateBackendBudget    = totalBudget * 0.5
edgeQueueBudget       = sum(edge.queueCapacity * parallelism) * estimatedItemSize
networkBufferBudget   = sum(edge.receiveWindowBytes)
```

### 9.2 运行时监控

```java
class MemoryBudgetMonitor {
    long actualEdgeQueueMemory;
    long actualNetworkBufferMemory;

    void checkBudget() {
        if (actualEdgeQueueMemory > allocatedEdgeQueueBudget)
            triggerBackpressure();
        if (actualEdgeQueueMemory > allocatedEdgeQueueBudget * 1.5)
            log.warn("Edge queue memory exceeds 150% of budget");
    }
}
```

**超预算策略**：

| 策略 | 行为 |
|------|------|
| `TRIGGER_BACKPRESSURE` | 通知 sender 降低发送速率（默认） |
| `REJECT_NEW_DATA` | 拒绝接收新数据，直到内存恢复 |
| `SPILL_TO_DISK` | 将队列数据写入磁盘（暂不实现） |

## 10. Operator State（非键控状态）

Operator State 是**非键控**的算子级状态，与 key 无关，按 subtask 实例存储。与 `core-design.md` §7 对应。

### 10.1 设计接口

```java
interface CheckpointedFunction {
    void snapshotState(FunctionSnapshotContext context) throws Exception;
    void initializeState(FunctionInitializationContext context) throws Exception;
}

interface OperatorStateStore {
    <T> ListState<T> getListState(ListStateDescriptor<T> descriptor) throws Exception;
    <T> ListState<T> getUnionListState(ListStateDescriptor<T> descriptor) throws Exception;
    <T> ListState<T> getBroadcastState(ListStateDescriptor<T> descriptor) throws Exception;
}
```

### 10.2 重分布模式

| 模式 | 方法 | 并行度变化时的语义 |
|------|------|-------------------|
| `SPLIT_DISTRIBUTE` | `getListState()` | round-robin 分配给新旧 subtask 列表 |
| `UNION` | `getUnionListState()` | 所有 subtask 获取完整状态列表，自行过滤 |
| `BROADCAST` | `getBroadcastState()` | 所有 subtask 获得完全相同的一份状态 |

### 10.3 典型用途

| 用途 | 状态内容 | 模式 |
|------|---------|------|
| Source offset checkpoint | 每个 split 的消费位置 | SPLIT_DISTRIBUTE |
| Kafka partition 注册表 | 已知 partition 列表 | BROADCAST |
| 发现进度 | CDC 快照阶段进度 | SPLIT_DISTRIBUTE |
| 全局计数器 | 跨 key 的计数器 | UNION |

### 10.4 与 Keyed State 的关系

| 维度 | Keyed State | Operator State |
|------|------------|---------------|
| 范围 | 按 key 隔离 | 按 subtask 实例 |
| 分片 | StateShard（确定性 hash） | subtask（不确定，重分配改变归属） |
| 重分布 | Key-Group range 交集 | SPLIT_DISTRIBUTE / UNION / BROADCAST |
| 状态大小 | 大（O(key count × state per key)） | 小（O(splits × offset size)） |
| 实现 | `IKeyedStateBackend` → `IInternalStateBackend` | `OperatorStateStore` → `MemoryOperatorStateBackend` |

**当前缺口**：Operator State 尚未实现。实现计划见 `ai-dev/backlog/completion-roadmap.md` Phase 0.3。

## 11. 已知限制

1. **无内存控制（Memory 后端）** — MemoryStateBackend 的状态只增长不收缩（除窗口触发清理），无驱逐/spill。大状态场景可能 OOM。**RocksDBStateBackend（Stage 30）通过 off-heap 列族存储突破此限制；Stage 32 为 keyed state 引入了可选 TTL（见 §12），TTL 启用的 state 会按配置过期并被清理，但默认（无 TTL）状态仍只增长不收缩**
2. **JSON 序列化性能** — Checkpoint 持久化使用 JSON，体积和速度均不如二进制格式
3. **状态对象是引用** — MemoryValueState 直接存储用户对象引用，没有深拷贝。用户代码意外修改对象会影响状态一致性
4. **MemoryInternalAppendingState accumulator 复用** — 单个 accumulator 实例在 add() 时先重置再加入，多线程不安全
5. **SimpleKeyedStateStore 无 key 隔离** — 所有 key 共享状态，不可用于分布式 exactly-once 作业
6. ~~**无状态恢复路径**~~ — `AbstractStreamOperator.snapshotState()` 是活跃路径，在 `processBarrier` 触发时调用 `keyedStateBackend.snapshotState()` 产出 `StateSnapshot`（参见 `AbstractStreamOperator.java:261-295`）。此路径对 Memory 和 RocksDB 后端均生效
7. **无状态重分布** — 不支持并行度变更后重新分配状态
8. ~~**仅 Memory 后端**~~ — `IStateBackend` 接口已有两个实现：`MemoryStateBackend`（堆内存）和 `RocksDBStateBackend`（off-heap，Stage 30）
9. **无 Operator State 实现** — `OperatorStateStore` 接口未实现，source offset checkpoint 缺口。见 `ai-dev/backlog/completion-roadmap.md` Phase 0.3

## 12. State TTL（Stage 32）

为 keyed state 引入可选的生存时间（TTL）与过期清理。状态条目在指定 TTL 后自动失效。

### 12.1 配置

- `StateTtlConfig`：`ttl`（`Duration`）、`updateType`（`StateTtlUpdateType`：`Disabled`/`OnCreateAndWrite`/`OnReadAndWrite`）、`cleanupStrategy`（`TtlCleanupStrategy`：lazy eviction 默认启用；background cleanup 默认启用）。`StateTtlConfig.DISABLED` 是哨兵默认值。
- `StateDescriptor.setTtlConfig(StateTtlConfig)` 可选附加配置。TTL 是 **运行时行为，非 schema 契约**：`StateSchemaResolver` 不读取 TTL，故 `schemaChecksum` 不受 TTL 配置影响——在已有 state 上增删 TTL 不破坏 checkpoint 兼容性。`StateTtlConfig` 不进入 checkpoint 持久化；restore 后由 live descriptor（用户代码）在 `getState()` 时重新提供并重新绑定 TTL 上下文。

### 12.2 时间戳追踪：per-state sidecar（存储/值分离）

TTL 时间戳由每个 state 持有的 sidecar `TtlContext` 维护，**与存储 value 分离**：

- Memory 后端：`TtlContext<TypedNamespaceAndKey>`，sidecar 为 `Map<TypedNamespaceAndKey, Long>`
- RocksDB 后端：`TtlContext<ByteBuffer>`，sidecar key 为 base 复合存储键字节（namespace+shard+rawKey，不含 map-key 后缀）

存储的始终是 raw user value（JSON / 对象引用），不包装成 `TtlValue<T>`。这是必须的：accumulator-based state（Reducing/Aggregating/Appending）存储 `SimpleAccumulator` 或 raw ACC 值，包装会破坏 `(ACC) current` 类型转换与 fusion 逻辑。

### 12.3 TTL 粒度

per-key+namespace（一个复合键一个时间戳）。`MapState` 的整 map 作为一个 TTL 单元过期（per-UK-entry 为后续优化，见 plan Deferred）。

### 12.4 拦截机制：intrusive modification

每个 state 类增加可选 `TtlContext` 字段，read 方法先检查过期（过期则返回默认值/空并**同时删除存储 entry**，双重清理），write 方法刷新时间戳。不使用 wrapper/decorator——`MemoryStateSerDe.snapshotState()` 与 `RocksDBSnapshotSerDe.snapshotState()` 使用 `instanceof` 分发，wrapper 会破坏分发逻辑。`OnCreateAndWrite`：仅写刷新；`OnReadAndWrite`：读也刷新。

### 12.5 清理策略

- **Lazy eviction**（所有后端，默认启用）：访问时检查过期并删除。
- **Snapshot 过期排除**：snapshot 遍历 entries 时跳过已过期项（非可选）。restore 后被排除的项不恢复。
- **Background cleanup**：
  - Memory 后端：`TtlContext.sweepExpired` 主动遍历删除。
  - RocksDB 后端：`RocksDBKeyedStateBackend.cleanupExpiredEntries()` 扫描 sidecar、按 base key 删除过期 entry（scalar/list/accumulator 单键删除；MapState 前缀删除）。该 sweep 在 `snapshotState()` 起始处执行，使每次 checkpoint 同时回收空间。

### 12.6 Restore 后 TTL 存活

`StateTtlConfig` 不持久化。restore 后 sidecar 为空；restored entry 在首次访问时被赋予 "now" 时间戳（按 `OnCreateAndWrite` 语义给予新 TTL 窗口），不会仅因 sidecar 未持久化而立即过期。`getState(liveDescriptorWithTtl)` 在 restored state 上重新绑定 `TtlContext`，TTL 保持活跃。

### 12.7 Processing-time only

首版仅实现 processing-time TTL（`TtlTimeProvider`，默认 `SystemTtlTimeProvider`）。Event-time/watermark-based TTL 需 watermark 集成与乱序处理，为后续增强（见 plan Non-Goals）。

### 12.8 RocksDB 后端：不实现 native compaction filter（设计裁定）

plan 原计划用 RocksDB `AbstractCompactionFilter`（JNI）做后台批量清理。**裁定不实现**：`rocksdbjni` Java 绑定（9.11.2，亦见于 7.9.2）未暴露纯 Java 的 compaction-filter 回调——`AbstractCompactionFilter` 仅有 protected native 构造器，纯 Java 子类无法覆盖 `filter()` 决策逻辑（仅 `RemoveEmptyValueCompactionFilter`/`CassandraCompactionFilter` 等原生实现可用）。实现自定义 compaction filter 需 C++/JNI native 扩展，超出 `nop-stream-rocksdb` 纯 Java 模块范围。

**替代方案**：采用纯 Java 的 `cleanupExpiredEntries()` 后台 sweep（§12.5）作为后台批量清理机制——语义等价（过期 entry 被批量删除），且可观测、可测试、确定性。compaction filter 作为 `optimization candidate` 延后至引入 native 扩展时（见 plan Deferred But Adjudicated）。TTL 正确性不依赖 compaction filter：lazy eviction + snapshot 排除 + sweep 已保证过期语义。
