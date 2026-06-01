# 状态管理设计

> Status: active
> Created: 2026-05-20
> Revised: 2026-06-01
> Parent: `architecture.md` §3（执行模型）、`checkpoint-design.md` §3.3（状态快照）

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
    └── InternalAppendingState<K,N,IN,ACC,OUT>  (+setCurrentNamespace, getAccumulator)
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
| `valueType` | Class\<T\> | 值的类型（用于 JSON 序列化） |
| `defaultValue` | T | 默认值 |

### 2.3 Namespace

| 类型 | 用途 | 使用者 |
|---|---|---|
| `VoidNamespace` | 不需要 namespace 时的占位符 | SimpleKeyedStateStore |
| 泛型 N（通常为 Window） | 按 namespace 分区状态 | WindowOperator、合并窗口 |

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

### 6.1 分层原则

| 层 | 接口 | 序列化 |
|---|---|---|
| 算子层 | `putState(name, object)` / `getState(name, type)` | 不感知 |
| Backend 层 | `snapshotState() → StateSnapshot` / `restoreState(StateSnapshot)` | 不感知 |
| Storage 层 | `storeCheckPoint(CompletedCheckpoint)` | 不感知 |
| 实现层 | 内部 `JsonTool.serialize()` | JSON |

所有层都是对象存取。序列化仅在持久化实现层内部发生。

### 6.2 控制面元数据

- **metadata**：plan、manifest、state segment descriptor 必须可 JSON round-trip
- **payload pluggable**：默认 JSON（`JsonTool`），大状态可选择二进制 payload，但 descriptor 必须说明 codec
- **schema version**：每个 state name 必须记录 value schema version
- **checksum**：每个 segment 必须有 checksum
- **compatibility**：savepoint 恢复必须检查 codec 和 schema version

### 6.3 TypeSerializer 接口

nop-stream 保留了 Flink 的 `TypeSerializer<T>` 接口，但大幅简化。唯一实现是 `VoidNamespaceSerializer`。没有 `serialize/deserialize` 方法——纯结构接口。

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

## 10. 已知限制

1. **无内存控制** — 状态只增长不收缩（除窗口触发清理），无 TTL/驱逐/spill。大状态场景可能 OOM
2. **JSON 序列化性能** — Checkpoint 持久化使用 JSON，体积和速度均不如二进制格式
3. **状态对象是引用** — MemoryValueState 直接存储用户对象引用，没有深拷贝。用户代码意外修改对象会影响状态一致性
4. **MemoryInternalAppendingState accumulator 复用** — 单个 accumulator 实例在 add() 时先重置再加入，多线程不安全
5. **SimpleKeyedStateStore 无 key 隔离** — 所有 key 共享状态，不可用于分布式 exactly-once 作业
6. **无状态恢复路径** — `AbstractUdfStreamOperator.snapshotState()` 被注释掉，当前运行时不实际执行状态快照
7. **无状态重分布** — 不支持并行度变更后重新分配状态
8. **仅 Memory 后端** — `IStateBackend` 接口注释中提到 `RedisStateBackend`，未实现
