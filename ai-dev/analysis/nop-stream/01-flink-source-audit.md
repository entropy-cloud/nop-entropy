# Flink 1.20.0 Source-Level Structural Audit (6 Key Packages)

> Status: open
> Date: 2026-07-31
> Scope: Apache Flink release-1.20.0 streaming API, runtime execution, checkpoint, state system, window/time, CEP, and distributed execution layer
> Conclusion: TBD — this audit provides the structural reference for subsequent gap analysis against Nop Stream

## Context

This document is a comprehensive source-level structural audit of the 6 key Flink subsystems that Nop Stream aims to reimplement. The audit was performed against the `release-1.20.0` tag of Apache Flink. Each subsystem is analyzed for its class hierarchy, key method signatures, collaboration wiring on the critical path, and design patterns.

The findings serve as the baseline reference for the Nop Stream gap analysis (`08-gap-analysis.md`) and subsequent design decisions.

---

## 1. Streaming API (`flink-streaming-java`)

### 1.1 Class Hierarchy

| Class | Package | Role |
|---|---|---|
| `DataStream<T>` | `org.apache.flink.streaming.api.datastream` | Root fluent API, wraps `Transformation<T>` |
| `SingleOutputStreamOperator<T>` | `org.apache.flink.streaming.api.datastream` | Decorator adding metadata (uid, name, parallelism, side outputs) |
| `DataStreamSource<T>` | `org.apache.flink.streaming.api.datastream` | Source wrapper (legacy or FLIP-27) |
| `KeyedStream<T, KEY>` | `org.apache.flink.streaming.api.datastream` | Keyed variant, adds `KeySelector` + `KeyGroupStreamPartitioner` |
| `WindowedStream<T, K, W>` | `org.apache.flink.streaming.api.datastream` | Builder for window operations |
| `AllWindowedStream<T, W>` | `org.apache.flink.streaming.api.datastream` | Non-keyed window builder |
| `ConnectedStreams<IN1, IN2>` | `org.apache.flink.streaming.api.datastream` | Dual-input stream connector |
| `StreamExecutionEnvironment` | `org.apache.flink.streaming.api.environment` | Pipeline entry point, holds `List<Transformation<?>>` |
| `StreamGraphGenerator` | `org.apache.flink.streaming.api.graph` | Visitor/Strategy: translates transformations → `StreamGraph` |
| `StreamGraph` | `org.apache.flink.streaming.api.graph` | Intermediate graph with `StreamNode[]` + `StreamEdge[]` |
| `StreamNode` | `org.apache.flink.streaming.api.graph` | Vertex with `StreamOperatorFactory`, `StateBackend`, chaining config |
| `StreamEdge` | `org.apache.flink.streaming.api.graph` | Edge with `StreamPartitioner`, output tag, side-output flag |

### 1.2 Key Method Signatures (Entry Points)

```java
// DataStream / SingleOutputStreamOperator
public <R> SingleOutputStreamOperator<R> map(MapFunction<T, R> mapper)
public <R> SingleOutputStreamOperator<R> flatMap(FlatMapFunction<T, R> f)
public SingleOutputStreamOperator<T> filter(FilterFunction<T> f)
public KeyedStream<T, KEY> keyBy(KeySelector<T, KEY> keySelector)
public WindowedStream<T, KEY, W> window(WindowAssigner<? super T, W> assigner)
public AllWindowedStream<T, W> windowAll(WindowAssigner<? super T, W> assigner)
public <R> SingleOutputStreamOperator<R> process(ProcessFunction<T, R> f)
public DataStreamSink<T> sinkTo(Sink<T> sink)  // FLIP-27 sink
public DataStreamSink<T> addSink(SinkFunction<T> sink)  // legacy sink

// StreamExecutionEnvironment
public StreamGraph getStreamGraph()
public JobExecutionResult execute(String jobName)

// StreamGraphGenerator
public StreamGraph generate()
```

### 1.3 Wiring Description (Critical Path)

```
          DataStream          KeyedStream          WindowedStream
              |                    |                     |
         doTransform()         keyBy()              reduce/aggregate/
              |                    |                 apply/process()
              v                    v                     |
       Transformation       Partition              input.transform()
          subclass        Transformation               |
              |                 +                          v
              |          KeyGroupStream           OneInputTransformation
              |          Partitioner              + WindowOperator
              v                    |                     |
      env.addOperator()           v                     v
              |            StreamGraph             StreamGraph
              v                                   
       StreamGraphGenerator.generate()  →  StreamGraph  →  JobGraph
```

1. User calls `DataStream.map(f)` → `doTransform(MapTransformation)` → `env.addOperator(transform)`
2. `StreamExecutionEnvironment.execute()` → `getStreamGraph()` → `StreamGraphGenerator(transformations).generate()`
3. Generator dispatches each `Transformation` subclass via `Map<Class, TransformationTranslator>` (Strategy pattern)
4. Generator outputs `StreamGraph` (StreamNode/StreamEdge), then `PipelineExecutor` translates to `JobGraph`
5. For keyed windows: `KeyedStream.window()` returns `WindowedStream`; terminal methods call `input.transform()` which creates `OneInputTransformation` + `WindowOperator`

### 1.4 Operator Base Classes

| Class | Role |
|---|---|
| `StreamOperator<OUT>` | Interface: `open()`, `finish()`, `close()`, `snapshotState()`, `prepareSnapshotPreBarrier()` |
| `Input<IN>` | Interface: `processElement()`, `processWatermark()` |
| `OneInputStreamOperator<IN, OUT>` | Combines StreamOperator + Input |
| `AbstractStreamOperator<OUT>` | Template with chainingStrategy, container, config, output, stateHandler, keyedStateBackend, timerService |
| `AbstractUdfStreamOperator<OUT, F>` | Adds user function F + outputCollector |
| `StreamMap`, `StreamFlatMap`, `StreamFilter` | Concrete UDF wrappers |
| `ProcessOperator` | Wraps `ProcessFunction` |
| `KeyedProcessOperator` | Wraps `KeyedProcessFunction` with timer support |
| `StreamSource` | Legacy `SourceFunction` wrapper |
| `SourceOperator` | FLIP-27 source wrapper |
| `StreamGroupedReduceOperator` | Keyed reduce with `InternalTimerService` |

### 1.5 Transformation Hierarchy (`flink-core`)

```
Transformation<T>                     [base: id, name, outputType, parallelism, uid]
  └─ PhysicalTransformation<T>        [+ setChainingStrategy()]
       ├─ SourceTransformation         [FLIP-27, 0 inputs]
       ├─ LegacySourceTransformation   [old SourceFunction]
       ├─ SinkTransformation           [FLIP-27, 1 input]
       ├─ LegacySinkTransformation     [old SinkFunction]
       ├─ OneInputTransformation       [1 input + operatorFactory + optional stateKeySelector]
       ├─ TwoInputTransformation       [2 inputs]
       ├─ MultipleInputTransformation  [N inputs]
       ├─ PartitionTransformation      [logical-only, resolved to edge metadata]
       ├─ ReduceTransformation         [optimized keyed reduce]
       ├─ UnionTransformation          [N→1 union]
       ├─ SideOutputTransformation     [side output selector]
       ├─ BroadcastStateTransformation [broadcast state]
       ├─ TimestampsAndWatermarksTransformation
       ├─ CacheTransformation
       └─ FeedbackTransformation
```

### 1.6 TypeInformation Hierarchy

```
TypeInformation<T>
  ├─ BasicTypeInfo<T>
  ├─ NumericTypeInfo<T>
  ├─ AtomicType<T>
  ├─ CompositeType<T>
  │    ├─ PojoTypeInfo<T>
  │    ├─ TupleTypeInfo<T>
  │    └─ RowTypeInfo
  ├─ GenericTypeInfo<T>          [Kryo fallback]
  ├─ ListTypeInfo<T>
  ├─ MapTypeInfo<K, V>
  └─ ...
```

Pattern: type-safe Visitor + Factory via `TypeExtractor`.

### 1.7 Design Patterns

| Pattern | Usage |
|---|---|
| Builder | `WindowedStream`, `StreamExecutionEnvironment.buildStreamGraph()` |
| Decorator | `SingleOutputStreamOperator` wraps `DataStream` + adds metadata |
| Strategy | `StreamGraphGenerator` dispatches via `Map<Class, TransformationTranslator>` |
| Composite | Transformation tree via `getInputs()` |
| Visitor | `StreamGraphGenerator` visits each Transformation in the DAG |
| Template Method | `AbstractStreamOperator` provides default lifecycle hooks |
| Factory | `SimpleOperatorFactory.wrap()`, `TypeExtractor` for TypeInformation |
| Bridge | `TypeInformation` ↔ `TypeSerializer` |

---

## 2. Runtime Execution Model (`flink-runtime` + `flink-streaming-java`)

### 2.1 Class Hierarchy

| Class | Package | Role |
|---|---|---|
| `Task` | `o.a.flink.runtime.taskmanager` | Runnable; each Task owns one thread. State machine: CREATED→DEPLOYING→INITIALIZING→RUNNING→FINISHED/CANCELED/FAILED |
| `StreamTask` | `o.a.flink.streaming.runtime.tasks` | Abstract TaskInvokable; manages operator chain, mailbox, checkpoint, timers |
| `OneInputStreamTask` | `o.a.flink.streaming.runtime.tasks` | Single-input concrete impl |
| `TwoInputStreamTask` | `o.a.flink.streaming.runtime.tasks` | Dual-input concrete impl |
| `SourceOperatorStreamTask` | `o.a.flink.streaming.runtime.tasks` | FLIP-27 source task |
| `MailboxProcessor` | `o.a.flink.streaming.runtime.tasks.mailbox` | Main loop: alternate `processMail()` and `defaultAction.runDefaultAction()` |
| `MailboxExecutorImpl` | `o.a.flink.streaming.runtime.tasks.mailbox` | `execute()`, `yield()`, `tryYield()` |
| `TaskMailbox` | `o.a.flink.streaming.runtime.tasks.mailbox` | Priority queue (MIN for records, MAX for control) |
| `StreamInputProcessor` | `o.a.flink.streaming.runtime.io` | Interface: `processInput()` → `DataInputStatus` |
| `StreamOneInputProcessor` | `o.a.flink.streaming.runtime.io` | Wraps `StreamTaskInput` + `DataOutput` |
| `StreamMultipleInputProcessor` | `o.a.flink.streaming.runtime.io` | Selects next input via `MultipleInputSelectionHandler` |
| `AbstractStreamTaskNetworkInput` | `o.a.flink.streaming.runtime.io` | Polls `CheckpointedInputGate`, deserializes, emits |
| `CheckpointedInputGate` | `o.a.flink.streaming.runtime.io` | Wraps `InputGate` + `CheckpointBarrierHandler` |
| `SingleCheckpointBarrierHandler` | `o.a.flink.streaming.runtime.io` | Exactly-once barrier handling (aligned/unaligned/alternating) |
| `CheckpointBarrierTracker` | `o.a.flink.streaming.runtime.io` | At-least-once barrier counting |
| `InputGate` | `o.a.flink.runtime.io.network.api` | Interface: `pollNext()`, `getNextBufferOrEvent()` |
| `SingleInputGate` | `o.a.flink.runtime.io.network` | Manages `InputChannel[]` (LocalInputChannel / RemoteInputChannel) |

### 2.2 Key Method Signatures

```java
// Task
public void run()                              // main entry
public void invoke()                           // via doRun() → restoreAndInvoke()
public void restoreAndInvoke()                  // invokable.restore() → invokable.invoke() → cleanUp()

// StreamTask
public final void invoke()                      // init() → initializeStateAndOpenOperators() → runMailboxLoop() → afterInvoke()
public void processInput()                      // default mailbox action → inputProcessor.processInput()
protected void init()                           // abstract, subclass-specific initialization

// MailboxProcessor
public void runMailboxLoop()                    // primary loop
public boolean processMail()                    // process prioritized mails
public void runDefaultAction()                  // delegates to mailboxDefaultAction

// StreamInputProcessor
DataInputStatus processInput()                  // MORE_AVAILABLE, NOTHING_AVAILABLE, END_OF_RECOVERY, STOPPED, END_OF_DATA, END_OF_INPUT

// CheckpointedInputGate
Optional<BufferOrEvent> pollNext()              // polls gate, intercepts barriers

// CheckpointBarrierHandler
void processBarrier(CheckpointBarrier, ...)
void processCancellationBarrier(...)
void processEndOfPartition(...)
```

### 2.3 Wiring Description (Critical Path)

```
Task.run()
  └─ doRun()
       └─ restoreAndInvoke()
            ├─ invokable.restore()        → initializeState()
            ├─ invokable.invoke()
            │    └─ StreamTask.invoke()
            │         ├─ init()           → creates inputGate, output, processors (subclass-specific)
            │         ├─ initializeStateAndOpenOperators()
            │         └─ runMailboxLoop()
            │              ├─ processMail()                [checkpoint barriers, timers]
            │              └─ mailboxDefaultAction.run()   [processInput()]
            │                   └─ StreamInputProcessor.processInput()
            │                        └─ CheckpointedInputGate.pollNext()
            │                             └─ InputGate.pollNext()
            │                                  └─ InputChannel.getNextBufferOrEvent()
            └─ cleanUp()
```

#### Init Specialization (OneInputStreamTask)

```
OneInputStreamTask.init()
  └─ CheckpointedInputGate(inputGate, barrierHandler)
  └─ DataOutput(recordWriters)
  └─ StreamTaskNetworkInput(checkpointedInputGate, deserializer, ...)
  └─ StreamOneInputProcessor(networkInput, output)
```

#### Mailbox Loop Detail

```
runMailboxLoop()
  while (isRunning) {
    if (processMail()) continue          // handle priority mails (checkpoint, timer)
    if (mailboxDefaultAction...) {
      defaultAction.runDefaultAction()   // processInput()
    }
  }
```

- Job scheduling: control mails (checkpoint barriers, timer events) at MAX_PRIORITY preempt record processing
- Mailbox provides thread confinement: all operator calls happen on the task thread
- No locks needed for operator state access

### 2.4 DataInputStatus Transitions

```
MORE_AVAILABLE  → continue loop
NOTHING_AVAILABLE → suspend (mailbox will suspend via suspendDefaultAction)
END_OF_RECOVERY → initial catch-up complete (partial)
STOPPED         → suspend (source done)
END_OF_DATA    → afterFinish
END_OF_INPUT   → exit loop
```

### 2.5 Design Patterns

| Pattern | Usage |
|---|---|
| Reactor / Event Loop | `MailboxProcessor` alternates processMail() + defaultAction |
| Priority Queue | `TaskMailbox` with MIN/MAX priority for scheduling |
| Thread Confinement | All operator state accessed on single mailbox thread |
| Strategy | `CheckpointBarrierHandler` variants (aligned/unaligned/tracking) |
| Template Method | `StreamTask.init()` + `StreamTask.invoke()` lifecycle |
| Chain of Responsibility | `CheckpointedInputGate` intercepts barriers before record processing |
| Bridge | `InputGate` ↔ `InputChannel` network abstraction |

---

## 3. Checkpoint Subsystem (`flink-runtime`)

### 3.1 Class Hierarchy

| Class | Package | Role |
|---|---|---|
| `CheckpointCoordinator` | `o.a.flink.runtime.checkpoint` | Singleton per job on JobManager; orchestrates all checkpoints |
| `PendingCheckpoint` | `o.a.flink.runtime.checkpoint` | In-flight checkpoint state; tracks ack/decline from tasks |
| `CompletedCheckpoint` | `o.a.flink.runtime.checkpoint` | Immutable checkpoint metadata + state handle locations |
| `CompletedCheckpointStore` | `o.a.flink.runtime.checkpoint` | Interface: bounded LIFO store for completed checkpoints |
| `DefaultCompletedCheckpointStore` | `o.a.flink.runtime.checkpoint` | ZK-backed implementation (HA) |
| `StandaloneCompletedCheckpointStore` | `o.a.flink.runtime.checkpoint` | In-memory (non-HA) |
| `EmbeddedCompletedCheckpointStore` | `o.a.flink.runtime.checkpoint` | Embedded (for testing/standalone) |
| `CheckpointIDCounter` | `o.a.flink.runtime.checkpoint` | Interface: `getAndIncrement()`, `setCount()` |
| `StandaloneCheckpointIDCounter` | `o.a.flink.runtime.checkpoint` | AtomicLong-based |
| `ZooKeeperCheckpointIDCounter` | `o.a.flink.runtime.checkpoint` | ZooKeeper-based (HA) |
| `CheckpointFailureManager` | `o.a.flink.runtime.checkpoint` | Tracks consecutive failures, triggers job failover |
| `CheckpointRequestDecider` | `o.a.flink.runtime.checkpoint` | Admission control: max concurrent, min pause between checkpoints |
| `CheckpointPlan` | `o.a.flink.runtime.checkpoint` | Snapshot of which vertices/coordinators participate |
| `DefaultCheckpointPlanCalculator` | `o.a.flink.runtime.checkpoint` | Determines plan from ExecutionGraph topology |
| `CheckpointStatsTracker` | `o.a.flink.runtime.checkpoint` | Metrics via callbacks, ring buffer history |
| `CheckpointMetrics` | `o.a.flink.runtime.checkpoint` | Alignment duration, sync/async duration, bytes persisted |
| `StateAssignmentOperation` | `o.a.flink.runtime.checkpoint` | Redistributes OperatorSubtaskState during restore |
| `OperatorSubtaskState` | `o.a.flink.runtime.checkpoint` | ManagedKeyedState + rawKeyedState + operatorState + stateHandles + ... |
| `CheckpointBarrier` | `o.a.flink.runtime.checkpoint` | Barrier event sent from source to downstream |
| `CheckpointOptions` | `o.a.flink.runtime.checkpoint` | CheckpointType (full/unaligned), alignment timeout, alignmentType |

### 3.2 Key Method Signatures

```java
// CheckpointCoordinator
public CompletableFuture<CompletedCheckpoint> triggerCheckpoint(long delay)
  private CompletableFuture<CompletedCheckpoint> startTriggeringCheckpoint(...)
    private CheckpointPlan calculateCheckpointPlan(ExecutionGraph)
    private PendingCheckpoint createPendingCheckpoint(...)
    private void snapshotMasterState(PendingCheckpoint)
    private void triggerTasks(PendingCheckpoint, CheckpointPlan)
public void receiveAcknowledgeMessage(AcknowledgeCheckpoint msg)
public void receiveDeclineMessage(DeclineCheckpoint msg)
  private void completePendingCheckpoint(PendingCheckpoint)
  private void addCompletedCheckpointToStoreAndSubsumeOldest(CompletedCheckpoint)
  private void abortPendingCheckpoint(PendingCheckpoint, CheckpointException)
  private void dropCheckpoint(CheckpointPlan)

// PendingCheckpoint
public boolean acknowledgeTask(ExecutionAttemptID, OperatorSubtaskState)
public void finalizeCheckpoint(CompletedCheckpoint completed)
public CompletedCheckpoint mockCompletedCheckpoint()  // for triggering side effects
```

### 3.3 Wiring Description (Critical Path)

```
CheckpointCoordinator.triggerCheckpoint()
  └─ startTriggeringCheckpoint()
       ├─ calculateCheckpointPlan()          → DefaultCheckpointPlanCalculator.determineCheckpoint()
       ├─ getAndIncrement()                  → CheckpointIDCounter.getAndIncrement()
       ├─ createPendingCheckpoint()          → new PendingCheckpoint(...), add to pendingCheckpoints (LinkedHashMap)
       ├─ snapshotMasterState()              → invoke master hooks (e.g., savepoint metadata)
       └─ triggerTasks()                     → send CheckpointTrigger to each source Execution via RPC
            │
            ▼  [SourceOperatorStreamTask receives barrier, snapshots state, emits barriers downstream]
            │
       Task.receiveCheckpointBarriers()      → barrier propagates through Streaming topology
            │
            ▼  [all tasks acknowledge]
            │
CheckpointCoordinator.receiveAcknowledgeMessage()
  └─ pendingCheckpoint.acknowledgeTask()     → removes from pending set, merges OperatorSubtaskState
  └─ if all tasks + coordinators acknowledged:
       ├─ completePendingCheckpoint()
       │    └─ finalizeCheckpoint()
       │         ├─ create CompletedCheckpoint
       │         └─ complete onCompletionPromise
       └─ addCompletedCheckpointToStoreAndSubsumeOldest()
            └─ CompletedCheckpointStore.addCheckpointAndSubsumeOldestOne()

On decline:
CheckpointCoordinator.receiveDeclineMessage()
  └─ abortPendingCheckpoint()
       └─ CheckpointFailureManager.handleCheckpointException()
            └─ may trigger job failover if threshold exceeded
```

### 3.4 PendingCheckpoint Internal State

```java
class PendingCheckpoint {
    Map<ExecutionAttemptID, ExecutionVertexAcknowledge> acknowledgedTasks;
    Map<ExecutionAttemptID, ExecutionVertexAcknowledge> acknowledgedCoordinators;
    Map<ExecutionAttemptID, ExecutionVertexAcknowledge> acknowledgedMaster;
    OperatorSubtaskState operatorStates;                      // accumulated
    CompletableFuture<CompletedCheckpoint> onCompletionPromise;
    CheckpointProperties props;
    CheckpointStorageLocation targetLocation;
    // ...
}
```

`acknowledgeTask()` removes from pending map, merges `OperatorSubtaskState` via `StateAssignmentOperation`.

### 3.5 Design Patterns

| Pattern | Usage |
|---|---|
| Coordinator / Worker | `CheckpointCoordinator` orchestrates; tasks acknowledge/decline |
| State Machine | `PendingCheckpoint` ack tracking; `CheckpointFailureManager` failure policy |
| Eventually Consistent Log | `CompletedCheckpointStore` as bounded LIFO |
| Promise / Completion | `PendingCheckpoint.onCompletionPromise` drives async completion chain |
| Strategy | `CheckpointIDCounter` (standalone vs ZK); `CompletedCheckpointStore` variants |
| Flow Control | `CheckpointRequestDecider` admission control |
| Callback / Observer | `CheckpointStatsTracker` receives metrics via completion callbacks |
| Snapshot Isolation | Barriers create consistent cut (Chandy-Lamport) |

---

## 4. State System (`flink-runtime` + `flink-core-api`)

### 4.1 Class Hierarchy

| Class | Package | Role |
|---|---|---|
| `StateBackend` | `o.a.flink.api.common.state` | Factory interface: `createKeyedStateBackend()`, `createOperatorStateBackend()` |
| `HashMapStateBackend` | `o.a.flink.runtime.state.hashmap` | Heap-based: CopyOnWriteStateMap/StateTable, full checkpoints only |
| `EmbeddedRocksDBStateBackend` | `o.a.flink.state.rocksdb` | RocksDB-backed, incremental checkpoints, managed memory |
| `AbstractKeyedStateBackend<K>` | `o.a.flink.runtime.state` | Template: keySerializer, numberOfKeyGroups, keyGroupRange, kvStateRegistry, ttlTimeProvider |
| `HeapKeyedStateBackend<K>` | `o.a.flink.runtime.state.heap` | StateTable[] per key-group, HeapSnapshotStrategy |
| `OperatorStateBackend` | `o.a.flink.api.common.state` | Interface: `getListState()`, `getUnionListState()`, `getBroadcastState()` |
| `DefaultOperatorStateBackend` | `o.a.flink.runtime.state` | PartitionableListState, HeapBroadcastState |
| `InternalKvState<K, N, V>` | `o.a.flink.runtime.state.internal` | Internal state interface: getKeySerializer, getNamespaceSerializer, getValueSerializer, setCurrentNamespace |
| `InternalValueState<K, N, V>` | `o.a.flink.runtime.state.internal` | Extends InternalKvState + ValueState |
| `InternalListState<K, N, T>` | `o.a.flink.runtime.state.internal` | Extends InternalKvState + ListState |
| `InternalMapState<K, N, UK, UV>` | `o.a.flink.runtime.state.internal` | Extends InternalKvState + MapState |
| `HeapValueState<K, N, V>` | `o.a.flink.runtime.state.heap` | Heap impl, extends AbstractHeapState |
| `HeapListState<K, N, T>` | `o.a.flink.runtime.state.heap` | Heap impl, extends AbstractHeapState |
| `HeapMapState<K, N, UK, UV>` | `o.a.flink.runtime.state.heap` | Heap impl, extends AbstractHeapState |
| `CopyOnWriteStateMap<K, N, V>` | `o.a.flink.runtime.state.heap` | Copy-on-write map per key-group for heap state |
| `StateTable<K, N, V>` | `o.a.flink.runtime.state.heap` | Key-group-indexed state table |
| `KeyGroupRange` | `o.a.flink.runtime.state` | Immutable range [start, end] of key groups |
| `KeyGroupRangeAssignment` | `o.a.flink.runtime.state` | `assignToKeyGroup(key.hashCode % maxParallelism)` → operator index |
| `SnapshotStrategy` | `o.a.flink.runtime.state` | Interface: `syncPrepareResources()`, `asyncSnapshot()` |
| `HeapSnapshotStrategy` | `o.a.flink.runtime.state.heap` | Sync: freeze StateTable snapshots; Async: write key-groups |
| `SnapshotStrategyRunner` | `o.a.flink.runtime.state` | Wraps strategy execution |
| `KeyGroupPartitioner` | `o.a.flink.runtime.state` | Counting-sort partitioning for per-key-group writes |
| `TtlStateFactory` | `o.a.flink.runtime.state.ttl` | Decorator adding TTL to any InternalKvState |
| `LatencyTrackingStateFactory` | `o.a.flink.runtime.state` | Decorator adding latency metrics to any InternalKvState |

### 4.2 User-Facing State Types (`flink-core-api`)

```
State                            [clear()]
  ├─ ValueState<T>               [value(), update()]
  ├─ AppendingState<IN, OUT>     [get(), add()]
  │    └─ MergingState<IN, OUT>  [marker interface]
  │         ├─ ListState<T>      [add(), update(), get()]
  │         ├─ ReducingState<T>  [add() -> reduce in state]
  │         └─ AggregatingState<IN, OUT> [add() -> aggregate in state]
  └─ MapState<UK, UV>            [get(), put(), remove(), entries(), keys(), values(), iterator()]
```

### 4.3 Key Method Signatures

```java
// StateBackend
KeyedStateBackend<K> createKeyedStateBackend(...)
OperatorStateBackend createOperatorStateBackend(...)

// KeyedStateBackend
void setCurrentKey(K key)
K getCurrentKey()
<K, N, V> InternalKvState<K, N, V> getOrCreateKeyedState(TypeSerializer<N>, StateDescriptor<S, V>)

// InternalKvState
TypeSerializer<K> getKeySerializer()
TypeSerializer<N> getNamespaceSerializer()
TypeSerializer<V> getValueSerializer()
void setCurrentNamespace(N namespace)

// SnapshotStrategy
SnapshotResultSupplier asyncSnapshot(CheckpointSnapshotContext, CheckpointStreamFactory, KeyedStateHandle)

// DefaultOperatorStateBackend
<T> ListState<T> getListState(ListStateDescriptor<T>)
<T> ListState<T> getUnionListState(ListStateDescriptor<T>)
<K, V> BroadcastState<K, V> getBroadcastState(MapStateDescriptor<K, V>)
```

### 4.4 Key-Group Design

```
maxParallelism = total key groups (immutable for job lifetime)

KeyGroupRangeAssignment.assignToKeyGroup(key)
  → MathUtils.murmurHash(key.hashCode()) % maxParallelism

KeyGroupRangeAssignment.computeOperatorIndexForKeyGroup(maxParallelism, parallelism, keyGroup)
  → keyGroup * parallelism / maxParallelism

Default maxParallelism:
  min(max(roundUpToPowerOf2(p + p/2), 128), 32768)
```

- `HeapKeyedStateBackend`: one `StateTable[]` per key-group range owned by subtask
- `AbstractKeyedStateBackend.setCurrentKey()` computes keyGroupIndex on every record
- `HeapValueState.get()`: delegates to `StateTable.get(keyGroupIndex, key, namespace)`

### 4.5 Wiring Description (State Creation)

```
StateBackend.createKeyedStateBackend(...)
  └─ HeapKeyedStateBackendBuilder.build()
       ├─ HeapSnapshotStrategy
       └─ STATE_CREATE_FACTORIES map:
            VALUE  → HeapValueState.create()
            LIST   → HeapListState.create()
            MAP    → HeapMapState.create()
            REDUCING → HeapReducingState.create()
            AGGREGATING → HeapAggregatingState.create()

Operator gets state via:
  getRuntimeContext().getState(ValueStateDescriptor<T>)
    └─ KeyedStateBackend.getOrCreateKeyedState(namespaceSerializer, descriptor)
         └─ createInternalState(stateDescriptor)
              └─ STATE_CREATE_FACTORIES.get(stateType).apply(stateBackend, descriptor, ...)
                   └─ new HeapValueState(keySerializer, valueSerializer, namespaceSerializer, stateTable, ...)
                        └─ wrapped by TtlStateFactory / LatencyTrackingStateFactory as configured
```

### 4.6 Snapshot Flow

```
CheckpointCoordinator triggers
  └─ StreamTask.createCheckpointOutputStreams() → beginCheckpoint()
       └─ AbstractStreamOperator.snapshotState()
            └─ keyedStateBackend.snapshot(...)
                 └─ SnapshotStrategyRunner
                      ├─ syncPrepareResources()
                      │    └─ HeapSnapshotStrategy.syncPrepareResources()
                      │         └─ Freeze StateTable snapshots per key-group
                      └─ asyncSnapshot()
                           └─ HeapSnapshotStrategy.asyncSnapshot()
                                └─ KeyGroupPartitioner: counting-sort partitions key-groups
                                     └─ Write to CheckpointStreamFactory streams
```

### 4.7 Design Patterns

| Pattern | Usage |
|---|---|
| Factory Method | `StateBackend.createKeyedStateBackend()` |
| Builder | `HeapKeyedStateBackendBuilder`, `DefaultOperatorStateBackendBuilder` |
| Strategy | `SnapshotStrategy` (Heap vs RocksDB incremental) |
| Template Method | `AbstractKeyedStateBackend` provides common lifecycle |
| Decorator | `TtlStateFactory.wrap()`, `LatencyTrackingStateFactory.wrap()` |
| Copy-on-Write | `CopyOnWriteStateMap` (heap state) |
| Two-Phase Snapshot | syncPrepareResources (freeze) + asyncSnapshot (write) |
| Counting Sort | `KeyGroupPartitioner` for O(n) per-key-group file writes |
| State Locality | Key-group indexed `StateTable[]` partitions data per subtask assignment |

---

## 5. Window / Time Subsystem

### 5.1 Class Hierarchy

| Class | Package | Role |
|---|---|---|
| `WindowOperator<K, W, T, R>` | `o.a.flink.streaming.runtime.operators.windowing` | Core operator: extends AbstractUdfStreamOperator, implements OneInputStreamOperator + Triggerable |
| `WindowAssigner<T, W>` | `o.a.flink.streaming.api.windowing.assigners` | Abstract: `assignWindows()`, `getDefaultTrigger()`, `isEventTime()` |
| `TumblingEventTimeWindows` | `o.a.flink.streaming.api.windowing.assigners` | Single [start, start+size) per timestamp |
| `SlidingEventTimeWindows` | `o.a.flink.streaming.api.windowing.assigners` | Multiple overlapping windows, pan by slide |
| `EventTimeSessionWindows` | `o.a.flink.streaming.api.windowing.assigners` | Session gap, uses `MergingWindowAssigner` + `TimeWindow.mergeWindows()` |
| `GlobalWindows` | `o.a.flink.streaming.api.windowing.assigners` | Singleton global window; default NeverTrigger |
| `Trigger<T, W>` | `o.a.flink.streaming.api.windowing.triggers` | Abstract: `onElement()`, `onProcessingTime()`, `onEventTime()`, `clear()`. Returns `TriggerResult` |
| `EventTimeTrigger` | `o.a.flink.streaming.api.windowing.triggers` | FIRE when watermark >= window.maxTimestamp |
| `ProcessingTimeTrigger` | `o.a.flink.streaming.api.windowing.triggers` | FIRE when processing time >= window.maxTimestamp |
| `CountTrigger` | `o.a.flink.streaming.api.windowing.triggers` | FIRE when element count reaches maxCount (uses ReducingState) |
| `ContinuousEventTimeTrigger` | `o.a.flink.streaming.api.windowing.triggers` | FIRE periodically every interval |
| `PurgingTrigger` | `o.a.flink.streaming.api.windowing.triggers` | Decorator: converts FIRE → FIRE_AND_PURGE |
| `TimeWindow` | `o.a.flink.streaming.api.windowing.windows` | [start, end) pair with mergeWindows() |
| `InternalTimerService` | `o.a.flink.streaming.api.operators` | Interface: registerEventTimeTimer/registerProcessingTimeTimer, delete, currentProcessingTime/currentWatermark |
| `InternalTimerServiceImpl` | `o.a.flink.streaming.runtime.operators` | Priority queues (KeyedGroupedInternalPriorityQueue) per namespace |
| `InternalTimeServiceManagerImpl` | `o.a.flink.streaming.runtime.tasks` | Manages multiple named timer services per operator |
| `WatermarkStrategy` | `o.a.flink.api.common.eventtime` | Interface: `createTimestampAssigner()` + `createWatermarkGenerator()` |
| `WatermarkGenerator` | `o.a.flink.api.common.eventtime` | `onEvent()`, `onPeriodicEmit()` |
| `StatusWatermarkValve` | `o.a.flink.streaming.runtime.io` | Min-heap of subpartition status for watermark propagation across input channels |
| `ProcessWindowFunction` | `o.a.flink.streaming.api.functions.windowing` | User-facing: `process(key, context, elements, out)`, `clear(context)` |
| `InternalWindowFunction` | `o.a.flink.streaming.runtime.operators.windowing` | Internal bridge interface |

### 5.2 Key Method Signatures

```java
// WindowOperator
public void processElement(StreamRecord<T> element)           // main entry: assign windows, trigger, fire
public void onEventTime(InternalTimer<K, W> timer)             // event-time timer callback
public void onProcessingTime(InternalTimer<K, W> timer)        // processing-time timer callback
  private void emitWindowContents(W window, TimeWindow context, DataOutput<...> output)

// WindowAssigner
abstract Collection<W> assignWindows(T element, long timestamp, WindowAssignerContext context)
abstract Trigger<T, W> getDefaultTrigger()
abstract boolean isEventTime()

// Trigger
abstract TriggerResult onElement(T element, long timestamp, W window, TriggerContext ctx)
abstract TriggerResult onProcessingTime(long time, W window, TriggerContext ctx)
abstract TriggerResult onEventTime(long time, W window, TriggerContext ctx)
abstract void clear(W window, TriggerContext ctx)

// InternalTimerService
void registerProcessingTimeTimer(N namespace, long time)
void registerEventTimeTimer(N namespace, long time)
void deleteProcessingTimeTimer(N namespace, long time)
void deleteEventTimeTimer(N namespace, long time)
long currentProcessingTime()
long currentWatermark()

// WatermarkStrategy (static factories)
static WatermarkStrategy<T> forMonotonousTimestamps()
static WatermarkStrategy<T> forBoundedOutOfOrderness(Duration maxOutOfOrderness)
```

### 5.3 Wiring Description (Critical Path)

#### Event-Time Window Data Flow

```
WindowOperator.processElement(element)
  ├─ windowAssigner.assignWindows(element, timestamp)      → Collection<W>
  ├─ for each window:
  │    ├─ if element.timestamp < window.maxTimestamp - allowedLateness → drop (late)
  │    ├─ windowState.add(element)                          → accumulate in state
  │    ├─ trigger.onElement(element, timestamp, window)     → TriggerResult
  │    └─ if FIRE: emitWindowContents(window)
  └─ registerCleanupTimer(window.maxTimestamp + allowedLateness)
```

#### Timer Firing

```
Watermark advances:
  └─ InternalTimerServiceImpl.advanceWatermark()
       └─ poll event-time timer priority queue
            └─ for each timer with timestamp <= watermark:
                 └─ triggerTarget.onEventTime(timer)
                      └─ WindowOperator.onEventTime(timer)
                           ├─ trigger.onEventTime(timestamp, window, triggerContext)
                           │    = FIRE if timestamp >= window.maxTimestamp (EventTimeTrigger)
                           └─ emitWindowContents(window)
                                └─ windowState.get(window) → Iterable
                                     └─ to ProcessWindowFunction or aggregate/apply
                           
                           └─ OR: CLEAR → windowState.clear(); trigger.clear()
```

#### Cleanup Timer

- Registered at `window.maxTimestamp + allowedLateness`
- Fires to clear window state + trigger state
- Prevents unbounded state growth

### 5.4 Timer Priority Queue Architecture

```
InternalTimerServiceImpl
  ├─ processingTimeTimersQueue: KeyGroupedInternalPriorityQueue<InternalTimer<K, W>>
  │    [grouped by key-group, ordered by timestamp]
  └─ eventTimeTimersQueue: KeyGroupedInternalPriorityQueue<InternalTimer<K, W>>
       [grouped by key-group, ordered by timestamp]

InternalTimer<K, W> = (timestamp, key, namespace)

InternalTimeServiceManagerImpl manages Map<String, InternalTimerServiceImpl> per operator
```

### 5.5 Watermark Propagation

```
StatusWatermarkValve (per input channel)
  ├─ SubpartitionStatus per input
  │    ├─ watermark: long
  │    └─ idle: boolean
  └─ findAndPropagateWatermark():
       └─ min-heap of active (non-idle) subpartition watermarks = global watermark
       └─ if all idle → emit max watermark (signals no barriers)
```

### 5.6 Design Patterns

| Pattern | Usage |
|---|---|
| Builder | `WindowedStream` → creates `WindowOperator` with assembled components |
| Visitor | `WindowAssigner.assignWindows()` produces multiple windows per element |
| Strategy | `Trigger` decision per window; `WindowAssigner` window assignment strategy |
| Decorator | `PurgingTrigger` wraps any Trigger, converting FIRE → FIRE_AND_PURGE |
| Callback | `Triggerable.onEventTime/onProcessingTime` |
| Priority Queue | `InternalTimerServiceImpl` uses `KeyedGroupedInternalPriorityQueue` |
| Memento | `WindowOperator` state mementos (windowState, triggerState) persisted across restarts |
| Factory | `InternalTimerService` created via `InternalTimeServiceManager` |

---

## 6. CEP (Complex Event Processing) (`flink-cep`)

### 6.1 Class Hierarchy

| Class | Package | Role |
|---|---|---|
| `NFA` | `o.a.flink.cep.nfa` | Core automaton engine: one per key. Based on SASE+ paper |
| `NFAState` | `o.a.flink.cep.nfa` | Mutable state: partialMatches + completedMatches priority queues + stateChanged flag |
| `ComputationState` | `o.a.flink.cep.nfa` | Immutable: currentStateName, version (DeweyNumber), startTimestamp, previousBufferEntry (NodeId) |
| `SharedBuffer` | `o.a.flink.cep.nfa.sharedbuffer` | Memory-optimized event store: each event stored once, shared across matching branches |
| `SharedBufferAccessor` | `o.a.flink.cep.nfa.sharedbuffer` | Transactional access: registerEvent(), put(), extractPatterns(), releaseNode(), materializeMatch() |
| `Lockable<T>` | `o.a.flink.cep.nfa.sharedbuffer` | Reference-counted wrapper: lock() / release() |
| `DeweyNumber` | `o.a.flink.cep.nfa` | Version numbering for shared buffer branches (Dewey decimal notation) |
| `Pattern<T, F>` | `o.a.flink.cep.pattern` | Linked-list-based pattern DSL: `begin()`, `next()`, `followedBy()`, `where()`, `times()`, `within()` |
| `Quantifier` | `o.a.flink.cep.pattern.conditions` | ConsumingStrategy + QuantifierProperty (SINGLE/LOOPING/TIMES/OPTIONAL/GREEDY) |
| `CepOperator` | `o.a.flink.cep.operator` | Operator extending AbstractUdfStreamOperator + Triggerable |
| `PatternStream` | `o.a.flink.cep` | User-facing: `process()`, `select()`, `flatSelect()` |
| `PatternStreamBuilder` | `o.a.flink.cep` | Builds CepOperator from Pattern → NFACompiler |
| `NFACompiler` | `o.a.flink.cep.nfa.compiler` | Compiles Pattern into NFA state graph (NFAState → State, transitions) |
| `AfterMatchSkipStrategy` | `o.a.flink.cep.pattern` | Controls match pruning: NoSkip, SkipPastLast, SkipToFirst, SkipToLast, SkipToNext |

### 6.2 Key Method Signatures

```java
// NFA
Collection<Map<String, List<T>>> process(SharedBufferAccessor<T>, NFAState, T event, long timestamp, ...)
Collection<Map<String, List<T>>> advanceTime(SharedBufferAccessor<T>, NFAState, long timestamp)
  private Collection<ComputationState> computeNextStates(...)       // core transition logic
  private Collection<Map<String, List<T>>> extractPatterns(...)    // DFS backtracking from completed match
  private Collection<Map<String, List<T>>> processMatchesAccordingToSkipStrategy(...)

// SharedBuffer
EventId registerEvent(T event, long timestamp)
void put(SharedBufferNode node, EventId eventId, DeweyNumber version, NodeId previousNodeId)
Collection<Map<String, List<T>>> extractPatterns(...)              // DFS through node graph

// Pattern
static <T, F extends T> Pattern<T, F> begin(String name)
Pattern<T, F> next(String name)              // STRICT contiguity
Pattern<T, F> followedBy(String name)        // SKIP_TILL_NEXT (relaxed)
Pattern<T, F> followedByAny(String name)     // SKIP_TILL_ANY
Pattern<T, F> notNext(String name)
Pattern<T, F> notFollowedBy(String name)
Pattern<T, F> where(IterativeCondition<T>)
Pattern<T, F> subtype(Class<F> subtypeClass)
Pattern<T, F> oneOrMore()
Pattern<T, F> times(int times)
Pattern<T, F> optional()
Pattern<T, F> greedy()
Pattern<T, F> consecutive()
Pattern<T, F> within(Time time)

// CepOperator
public void processElement(StreamRecord<T> element)
public void onEventTime(InternalTimer<K, W> timer)
public void onProcessingTime(InternalTimer<K, W> timer)
```

### 6.3 Wiring Description (Critical Path)

#### Event-Time Processing Flow

```
CepOperator.processElement(element)
  ├─ elementQueueState.put(timestamp, element)           // buffer into MapState<timestamp, List<IN>>
  └─ registerEventTimeTimer(timestamp)                   // for later processing

CepOperator.onEventTime(timer)
  ├─ drain elementQueueState from lastProcessedTimestamp to timer.timestamp (sorted)
  ├─ for each event:
  │    └─ processEvent(event)
  │         └─ sharedBufferAccessor.registerEvent(event) → EventId
  │         └─ NFA.process(sharedBufferAccessor, nfaState, event, timestamp)
  │              └─ computeNextStates()                  // TAKE / IGNORE / PROCEED transitions
  │              └─ extractPatterns()                    // DFS backtracking for completed matches
  │              └─ processMatchesAccordingToSkipStrategy()
  │                   └─ sharedBufferAccessor.releaseNode() [pruning]
  └─ NFA.advanceTime(sharedBufferAccessor, nfaState, timer.timestamp)
       └─ prune timed-out partial matches
```

#### Processing-Time Flow

```
CepOperator.processElement(element)
  ├─ processEvent(element)                               // immediate (no buffering)
  └─ registerProcessingTimeTimer(windowTime)              // for within() timeouts
```

### 6.4 NFA Transition Logic

`computeNextStates()` implements three transition types:

```
TAKE    → consume event, advance state machine
IGNORE  → event is ignored, stay in same state (allows skipping irrelevant events)
PROCEED → advance without consuming event (for optional patterns)

States:
┌──────────────────┐
│  begin("start")  │────────┐
└──────────────────┘        │  TAKE("a") → IGNORE
     │ TAKE("a")            ▼
     ▼              ┌──────────────────┐
┌──────────────────┐│  start (continued)│
│  middle          ││                   │  Competing partial matches
│  (looking for b) ││  (still looking)  │  share events via SharedBuffer
└──────────────────┘└──────────────────┘
     │ TAKE("b")    
     ▼
┌──────────────────┐
│  end (match)     │──→ extractPatterns()
└──────────────────┘
```

### 6.5 SharedBuffer Architecture

```
SharedBuffer (backed by MapState)
  ├─ eventsBuffer:    MapState<EventId, Lockable<V>>       // event payload, one copy
  ├─ eventsCount:     MapState<Long, Long>                 // timestamp→count (for cleanup)
  └─ entries:         MapState<NodeId, Lockable<SharedBufferNode>>  // edge graph

SharedBufferNode (and SharedBufferEdge):
  SharedBufferNode = List<SharedBufferEdge>
  SharedBufferEdge = (target: NodeId, DeweyNumber version)

SharedBufferAccessor methods:
  ├─ registerEvent(event)       → EventId
  ├─ put(EventId, version, previousNodeId)   → NodeId (add edge)
  ├─ extractPatterns(NodeId)    → DFS through edges → Map<String, List<EventId>>
  ├─ releaseNode(NodeId)        → cascading release if refcount → 0
  └─ materializeMatch(patterns) → Map<String, List<T>>
```

### 6.6 AfterMatchSkipStrategy Pruning

```
AfterMatchSkipStrategy.shouldPrune(pruningId, matchIds) → boolean

NoSkipStrategy         → prune nothing
SkipPastLastStrategy   → prune up to last event of current match
SkipToFirstStrategy    → prune up to first event of pattern group
SkipToLastStrategy     → prune up to last event of pattern group
SkipToNextStrategy     → prune only current events
```

- Pruning iterates `matchesToPrune`, calls `sharedBufferAccessor.releaseNode()` for each
- Cascading release: when `Lockable.refCount` reaches 0, edges to children are released recursively

### 6.7 Design Patterns

| Pattern | Usage |
|---|---|
| Nondeterministic Finite Automaton | `NFA` core engine (SASE+ paper) |
| Shared Data Structure | `SharedBuffer` stores each event once, shares across branches |
| Reference Counting | `Lockable<T>` manages shared event/node lifetime |
| Dewey Decimal Versioning | `DeweyNumber` tracks branch version in shared buffer |
| Builder | `Pattern` linked-list DSL |
| Compiler | `NFACompiler` → Pattern → NFA state graph |
| Strategy | `AfterMatchSkipStrategy` variants for match pruning |
| State Machine | `NFAState` partialMatches + completedMatches |
| Transactional Access | `SharedBufferAccessor` scoped to single NFA process() call |
| Priority Queue | `NFAState` partialMatches/completedMatches ordered by startTimestamp |

---

## 7. Distributed Execution Layer

### 7.1 Class Hierarchy

| Class | Package | Role |
|---|---|---|
| `ExecutionGraph` | `o.a.flink.runtime.executiongraph` | Job graph with ExecutionJobVertex[], intermediateResults, state |
| `DefaultExecutionGraph` | `o.a.flink.runtime.executiongraph` | Default impl, ExecutionJobVertex → ExecutionVertex[] → Execution (state machine) |
| `Execution` | `o.a.flink.runtime.executiongraph` | Single attempt: state CREATED→SCHEDULED→DEPLOYING→INITIALIZING→RUNNING→FINISHED |
| `ExecutionVertex` | `o.a.flink.runtime.executiongraph` | Groups parallel Execution attempts for a subtask |
| `ExecutionJobVertex` | `o.a.flink.runtime.executiongraph` | Groups all ExecutionVertex for one job vertex |
| `SchedulerBase` | `o.a.flink.runtime.scheduler` | Template: holds ExecutionGraph, lifecycle |
| `DefaultScheduler` | `o.a.flink.runtime.scheduler` | Pluggable schedulingStrategy, executionSlotAllocator, failureHandler, failoverStrategy |
| `PipelinedRegionSchedulingStrategy` | `o.a.flink.runtime.scheduler` | Schedules pipelined regions, source regions first |
| `VertexwiseSchedulingStrategy` | `o.a.flink.runtime.scheduler` | Vertex-by-vertex for batch |
| `SlotPool` | `o.a.flink.runtime.jobmaster` | JobMaster side: declares requirements → RM allocates → TM offers |
| `SlotManager` | `o.a.flink.runtime.resourcemanager` | ResourceManager side: `FineGrainedSlotManager` |
| `ResourceManager` | `o.a.flink.runtime.resourcemanager` | Abstract FencedRpcEndpoint: TM registrations, JM registrations, slot allocation |
| `ActiveResourceManager` | `o.a.flink.runtime.resourcemanager` | ResourceManagerDriver (YARN/K8s) |
| `StandaloneResourceManager` | `o.a.flink.runtime.resourcemanager` | No dynamic allocation |
| `RpcEndpoint` | `o.a.flink.runtime.rpc` | Base class (single-threaded actor-like) |
| `FencedRpcEndpoint<F>` | `o.a.flink.runtime.rpc` | Adds fencing token (used by JobMaster, ResourceManager) |
| `PekkoRpcService` | `o.a.flink.runtime.rpc.pekko` | Pekko ActorSystem-backed RPC |
| `JobMaster` | `o.a.flink.runtime.jobmaster` | FencedRpcEndpoint: JobGraph, slotPoolService, schedulerNG, registeredTaskManagers |
| `TaskExecutor` | `o.a.flink.runtime.taskexecutor` | RpcEndpoint: taskSlotTable, jobTable, jobLeaderService, shuffleEnvironment |
| `TaskDeploymentDescriptor` | `o.a.flink.runtime.jobmaster` | Serialization: SerializedJobInformation, taskInfo, partitionWriters, inputGates |

### 7.2 Key Method Signatures

```java
// Execution
public void deploy()                      // creates TDD, sends submitTask() to TM via gateway
public void scheduleOrUpdateConsumers()

// DefaultScheduler
public void startScheduling()
  private void scheduleExecutionJobVertex(ExecutionJobVertex vertex)

// JobMaster
protected void onStart()                  // lifecycle: startJobExecution()
  private void startJobExecution()
    ├─ slotPoolService.start()
    ├─ schedulerNG.startScheduling()
    └─ connectToResourceManager()

// TaskExecutor
public void submitTask(TaskDeploymentDescriptor tdd, JobMasterId jobMasterId, Time timeout)
  ├─ verifyJobConnection(jobMasterId)
  ├─ taskSlotTable.addTask(task)
  └─ task.startTaskExecution()

// ResourceManager
public void registerJobManager(JobMasterId, ResourceID, ...)
public void registerTaskExecutor(TaskExecutorRegistration, ...)
  └─ slotManager.registerTaskManager(..., slotReport)

// SlotPool
public void declareRequiredSlots(Collection<SlotRequest>)
public void offerSlot(SlotOffer)
```

### 7.3 Wiring Description (Critical Path)

```
Job Submission Flow:

JobClient.submitJob(jobGraph)
  └─ Dispatcher → JobMaster

JobMaster.onStart()
  └─ startJobExecution()
       ├─ slotPoolService.start()
       ├─ schedulerNG.startScheduling()
       │    └─ DefaultScheduler.scheduleExecutionJobVertex()
       │         └─ schedulingStrategy.scheduleVertices()
       │              └─ PipelinedRegionSchedulingStrategy: sources first
       │                   → Execution.scheduleOrUpdateConsumers()
       │                        → SlotPool declares requirements → RM
       │
       └─ connectToResourceManager()       [JobMaster → RM registration]

ResourceManager:
  ├─ receives JM requirements
  ├─ allocates worker via ResourceManagerDriver (YARN/K8s)
  ├─ TaskExecutor registers → slotManager processes slotReport
  └─ TM offers slots → JM SlotPool accepts

Slot matched:
  └─ Execution.deploy()
       └─ create TaskDeploymentDescriptor
       └─ send submitTask() via TaskExecutorGateway

TaskExecutor.submitTask()
  ├─ verifyJobConnection(jobMasterId)
  ├─ taskSlotTable.addTask(task)
  └─ task.startTaskExecution()
       └─ new Thread(task).start()         [creates Task thread]
            └─ Task.run() → StreamTask lifecycle
```

### 7.4 Execution State Machine

```
Execution:
  CREATED → SCHEDULED → DEPLOYING → INITIALIZING → RUNNING → FINISHED
                            ↓                                       ↓
                          CANCELED                               FAILED

JobStatus:
  CREATED → RUNNING → FINISHED / CANCELED / FAILED
              ↓
         SUSPENDED (leadership loss)
```

### 7.5 RPC Layer

```
RpcEndpoint (base)
  ├─ single-threaded dispatcher (actor-like)
  ├─ runAsync(), callRpcAsync(), callRpc()
  └─ PekkoRpcService → Pekko ActorSystem

FencedRpcEndpoint<F> extends RpcEndpoint
  ├─ adds fencing token for HA (leader epoch)
  ├─ JobMasterGateway, ResourceManagerGateway, TaskExecutorGateway
  └─ all inter-component communication is via these gateways

Gateway method example:
  TaskExecutorGateway.submitTask(TaskDeploymentDescriptor, JobMasterId, Time)
```

### 7.6 Design Patterns

| Pattern | Usage |
|---|---|
| State Machine | `Execution` states (CREATED→SCHEDULED→DEPLOYING→INITIALIZING→RUNNING→FINISHED) |
| Template Method | `SchedulerBase` provides lifecycle; `DefaultScheduler` plugs in strategy |
| Strategy | `SchedulingStrategy` (pipelined region vs vertexwise) |
| Actor | `RpcEndpoint` single-threaded message processing per component |
| Proxy / Gateway | `Gateway` interfaces hide RPC transport details |
| Two-Level Scheduling | Physical (TM slot) + logical (Execution sharing via slot groups) |
| Factory | `SchedulerNG`, `SlotPoolService`, various RPC gateways |
| Leader Election | Fencing token in `FencedRpcEndpoint` for HA safety |

---

## Conclusion

### Coverage Summary

| Subsystem | Packages Scanned | Key Classes Identified | Design Patterns |
|---|---|---|---|
| Streaming API | `flink-streaming-java`, `flink-core` | ~25 | Builder, Decorator, Strategy, Composite, Visitor, Template Method, Factory, Bridge |
| Runtime Execution | `flink-runtime`, `flink-streaming-java` | ~15 | Reactor/Event-Loop, Priority Queue, Thread Confinement, Strategy, Template Method |
| Checkpoint | `flink-runtime` | ~15 | Coordinator/Worker, State Machine, Promise/Completion, Strategy, Flow Control |
| State System | `flink-runtime`, `flink-core-api` | ~30 | Factory Method, Builder, Strategy, Decorator, Copy-on-Write, Two-Phase Snapshot |
| Window/Time | `flink-streaming-java` | ~20 | Builder, Visitor, Strategy, Decorator, Priority Queue, Callback |
| CEP | `flink-cep` | ~15 | NFA, Shared Data Structure, Reference Counting, Dewey Versioning, Compiler, Strategy |
| Distributed Execution | `flink-runtime` | ~15+ | State Machine, Template Method, Strategy, Actor, Proxy/Gateway, Two-Level Scheduling |

### Key Architectural Insights

1. **Transformation Graph as Universal Intermediate Representation**: All user API calls reduce to `Transformation` subclass instances in a DAG. `StreamGraphGenerator` visits and translates them to `StreamGraph`. This Strategy+Visitor pattern cleanly separates user API from execution graph.

2. **Mailbox Threading Model**: All operator code runs on a single thread per Task. The `MailboxProcessor` alternates between control (checkpoint/timer) and data processing. This avoids multi-threading complexity in operator state access.

3. **Barrier-Based Snapshot Isolation**: Checkpoint barriers flow with data to create consistent state snapshots (Chandy-Lamport). The `CheckpointedInputGate` intercepts barriers transparently, making checkpoint logic orthogonal to operator logic.

4. **Key-Group Partitioning**: Central to state scalability. `maxParallelism` partitions keys into fixed groups, enabling state repartitioning across subtask rescaling. Every `setCurrentKey()` computes `keyGroupIndex` via `KeyGroupRangeAssignment`.

5. **SharedBuffer in CEP**: Memory optimization through reference-counted shared event storage across competing NFA branches, with Dewey decimal versioning for branch tracking.

6. **Two-Level Scheduling**: Slot allocation separated from execution deployment. `SlotPool` (JobMaster) declares requirements, `SlotManager` (RM) allocates resources, TaskManagers offer slots asynchronously.

### Recommended Focus Areas for Nop Stream Reimplementation

1. **Transformation Graph** — most reusable abstraction; Nop Stream should adopt similar DAG-based representation
2. **Mailbox Threading** — strong candidate for adoption; eliminates lock contention in operator development
3. **Key-Group State Partitioning** — essential for horizontal scalability; must replicate
4. **Checkpoint Coordinator** — Chandy-Lamport algorithm is the standard; Nop Stream checkpoint design should mirror the barrier flow
5. **WindowOperator** — the most complex single operator; needs careful reimplementation
6. **CEP NFA Engine** — significant complexity; consider if required for MVP

---

## References

- Audit performed against Flink source at `~/sources/flink/` tag `release-1.20.0`
- Package root: `org.apache.flink` (streaming, runtime, cep, core-api)
- Related: `nop-stream` comparison docs in this directory
