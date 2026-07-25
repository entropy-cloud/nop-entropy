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
| `getInternalAppendingState(ReducingStateDescriptor<IN>)` | `ReducingStateDescriptor` | `InternalAppendingState<K,N,IN,ACC,ACC>` | OUT==ACC，ReduceFunction 归约 |
| `getInternalAppendingState(AggregatingStateDescriptor<IN,ACC,OUT>)` | `AggregatingStateDescriptor` | `InternalAppendingState<K,N,IN,ACC,OUT>` | AggregateFunction 累积，支持 ACC≠OUT |

### 2.2 StateDescriptor

| 属性 | 类型 | 含义 |
|---|---|---|
| `name` | String | 状态名称（唯一标识） |
| `valueType` | Class\<T\> | 值的类型（用于 JSON 反射，**非**二进制序列化） |
| `defaultValue` | T | 默认值 |

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
IStateBackend (getName, createKeyedStateBackend)
└── MemoryStateBackend           → new MemoryKeyedStateBackend<K>

IKeyedStateBackend<K> (setCurrentKey, getState, getMapState)
└── IInternalStateBackend<K>     (+ getInternalAppendingState×2, getInternalListState)
    └── MemoryKeyedStateBackend<K>

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

### 5.3 MemoryKeyedStateBackend 存储结构

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

### 5.4 SimpleKeyedStateStore

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

1. **无内存控制** — 状态只增长不收缩（除窗口触发清理），无 TTL/驱逐/spill。大状态场景可能 OOM
2. **JSON 序列化性能** — Checkpoint 持久化使用 JSON，体积和速度均不如二进制格式
3. **状态对象是引用** — MemoryValueState 直接存储用户对象引用，没有深拷贝。用户代码意外修改对象会影响状态一致性
4. **MemoryInternalAppendingState accumulator 复用** — 单个 accumulator 实例在 add() 时先重置再加入，多线程不安全
5. **SimpleKeyedStateStore 无 key 隔离** — 所有 key 共享状态，不可用于分布式 exactly-once 作业
6. **无状态恢复路径** — `AbstractUdfStreamOperator.snapshotState()` 被注释掉，当前运行时不实际执行状态快照
7. **无状态重分布** — 不支持并行度变更后重新分配状态
8. **仅 Memory 后端** — `IStateBackend` 接口注释中提到 `RedisStateBackend`，未实现
9. **无 Operator State 实现** — `OperatorStateStore` 接口未实现，source offset checkpoint 缺口。见 `ai-dev/backlog/completion-roadmap.md` Phase 0.3
