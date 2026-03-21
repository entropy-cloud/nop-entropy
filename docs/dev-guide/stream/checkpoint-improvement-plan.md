# nop-stream Checkpoint 机制改进计划

> 版本：1.0  
> 日期：2024-03-19  
> 作者：Nop Platform Team  
> 参考：Apache SeaTunnel Checkpoint 实现

## 1. 概述

### 1.1 背景

nop-stream 是 Nop 平台的流处理引擎，目标是实现类似 SeaTunnel 的数据同步能力。当前 checkpoint 机制存在明显不足，无法支持完整的故障恢复和 Exactly-Once 语义。

### 1.2 目标

1. 实现完整的 Checkpoint 协调机制
2. 支持持久化存储（本地文件/Redis/数据库）
3. 实现两阶段提交，确保 Exactly-Once 语义
4. 支持故障恢复和状态回滚
5. 保持与 Nop 平台设计理念的一致性

### 1.3 当前状态 vs 目标状态

| 能力 | 当前状态 | 目标状态 |
|------|---------|---------|
| CheckpointCoordinator | ❌ 不存在 | ✅ 完整实现 |
| CheckpointStorage | ⚠️ 仅内存 | ✅ 多种后端支持 |
| CheckpointBarrier | ❌ 不存在 | ✅ 完整实现 |
| PendingCheckpoint | ❌ 不存在 | ✅ 完整实现 |
| 两阶段提交 | ❌ 不存在 | ✅ 完整实现 |
| 故障恢复 | ❌ 不存在 | ✅ 完整实现 |
| StateBackend | ✅ 内存实现 | ✅ 扩展支持持久化 |

---

## 2. SeaTunnel Checkpoint 架构分析

### 2.1 核心组件

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        SeaTunnel Checkpoint 架构                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                     CheckpointCoordinator                           │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐  │   │
│  │  │ 定时触发器    │  │ 状态管理     │  │ 任务 ACK 跟踪            │  │   │
│  │  │ Scheduler    │  │ StateMachine │  │ PendingCheckpoint        │  │   │
│  │  └──────────────┘  └──────────────┘  └──────────────────────────┘  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    │                                        │
│                                    ▼                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                       CheckpointBarrier                             │   │
│  │  - id: long (checkpoint ID)                                         │   │
│  │  - timestamp: long (触发时间戳)                                      │   │
│  │  - checkpointType: CheckpointType (CHECKPOINT/SAVEPOINT/等)         │   │
│  │  - prepareCloseTasks: Set<TaskLocation>                             │   │
│  │  - closedTasks: Set<TaskLocation>                                   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    │                                        │
│                                    ▼                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                       CheckpointStorage                             │   │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐   │   │
│  │  │ LocalFile   │ │ HDFS        │ │ S3          │ │ Redis       │   │   │
│  │  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Checkpoint 生命周期

```
┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐
│  INIT   │───▶│TRIGGERED│───▶│ PENDING │───▶│COMPLETED│───▶│  DONE   │
└─────────┘    └─────────┘    └─────────┘    └─────────┘    └─────────┘
                    │                              │
                    │                              ▼
                    │                        ┌─────────┐
                    └───────────────────────▶│ ABORTED │
                                             └─────────┘
```

### 2.3 关键流程

#### 2.3.1 Checkpoint 触发流程

```java
// 1. 定时触发
scheduler.schedule(() -> tryTriggerPendingCheckpoint(CHECKPOINT_TYPE), 
    interval, TimeUnit.MILLISECONDS);

// 2. 创建 PendingCheckpoint
PendingCheckpoint pending = new PendingCheckpoint(
    jobId, pipelineId, checkpointId, timestamp, checkpointType,
    notYetAcknowledgedTasks, taskStatistics, actionStates);

// 3. 发送 Barrier 到所有 Source 任务
CheckpointBarrier barrier = new CheckpointBarrier(
    checkpointId, timestamp, checkpointType);
triggerCheckpoint(barrier);

// 4. 等待所有任务 ACK
// 5. 完成后持久化并通知
```

#### 2.3.2 任务 ACK 流程

```java
// 任务收到 Barrier 后快照状态并返回 ACK
void acknowledgeTask(TaskLocation location, List<ActionSubtaskState> states) {
    notYetAcknowledgedTasks.remove(location.getTaskID());
    // 记录状态
    actionStates.reportState(states);
    
    if (isFullyAcknowledged()) {
        // 所有任务都已 ACK，完成 checkpoint
        completableFuture.complete(toCompletedCheckpoint());
    }
}
```

#### 2.3.3 故障恢复流程

```java
// 1. 从存储加载最近的 checkpoint
PipelineState state = checkpointStorage.getLatestCheckpoint(jobId, pipelineId);
CompletedCheckpoint checkpoint = serializer.deserialize(state.getStates());

// 2. 通知所有任务恢复状态
for (TaskLocation location : tasks) {
    sendOperation(new NotifyTaskRestoreOperation(location, states));
}

// 3. 等待所有任务准备就绪
// 4. 通知任务开始执行
notifyTaskStart();
```

### 2.4 CheckpointType 类型

```java
public enum CheckpointType {
    CHECKPOINT_TYPE(true),           // 自动触发的常规 checkpoint
    SAVEPOINT_TYPE(false),           // 用户手动触发的 savepoint
    SCHEMA_CHANGE_BEFORE_POINT_TYPE(true),  // Schema 变更前
    SCHEMA_CHANGE_AFTER_POINT_TYPE(true),   // Schema 变更后
    COMPLETED_POINT_TYPE(true);      // 任务完成时的最终 checkpoint
}
```

---

## 3. nop-stream 当前实现分析

### 3.1 已有组件

#### 3.1.1 IStateBackend（已实现）

```java
// 位置：nop-stream-core/.../state/backend/IStateBackend.java
public interface IStateBackend extends Serializable {
    String getName();
    <K> IKeyedStateBackend<K> createKeyedStateBackend(Class<K> keyType);
}
```

**当前实现**：`MemoryStateBackend`（仅内存）

#### 3.1.2 CheckpointListener（已实现）

```java
// 位置：nop-stream-core/.../state/CheckpointListener.java
public interface CheckpointListener {
    void notifyCheckpointComplete(long checkpointId) throws Exception;
    default void notifyCheckpointAborted(long checkpointId) throws Exception {}
}
```

**问题**：接口存在但无协调器调用

#### 3.1.3 StreamOperator（部分实现）

```java
// 位置：nop-stream-core/.../operators/StreamOperator.java
public interface StreamOperator<OUT> extends CheckpointListener, KeyContext, Serializable {
    void open() throws Exception;
    void finish() throws Exception;
    void close() throws Exception;
    void prepareSnapshotPreBarrier(long checkpointId) throws Exception;
    
    // ❌ 被注释掉的方法
    // OperatorSnapshotFutures snapshotState(long checkpointId, ...);
    // void initializeState(StreamTaskStateInitializer streamTaskStateManager);
}
```

**问题**：`snapshotState` 方法被注释掉，无法执行真正的状态快照

### 3.2 缺失组件

| 组件 | 说明 | 优先级 |
|------|------|--------|
| CheckpointCoordinator | 协调所有任务的 checkpoint | P0 |
| CheckpointBarrier | 在数据流中传播的屏障 | P0 |
| PendingCheckpoint | 跟踪正在进行的 checkpoint | P0 |
| CompletedCheckpoint | 已完成的 checkpoint 数据 | P0 |
| CheckpointStorage | 持久化存储接口 | P0 |
| CheckpointIDCounter | 递增的 checkpoint ID 生成器 | P1 |
| CheckpointConfig | checkpoint 配置项 | P1 |
| TaskAcknowledgeOperation | 任务 ACK 操作 | P1 |
| NotifyTaskRestoreOperation | 任务恢复通知 | P1 |

---

## 4. 改进计划

### 4.1 Phase 1：核心组件实现（2周）

#### 4.1.1 新增 CheckpointBarrier

```java
package io.nop.stream.core.checkpoint;

/**
 * 在数据流中传播的 checkpoint 屏障
 */
public class CheckpointBarrier implements Serializable {
    private final long id;                    // checkpoint ID
    private final long timestamp;             // 触发时间戳
    private final CheckpointType checkpointType;  // checkpoint 类型
    
    public boolean snapshot() {
        return true;
    }
    
    public boolean prepareClose() {
        return checkpointType.isFinalCheckpoint();
    }
}
```

#### 4.1.2 新增 CheckpointType

```java
package io.nop.stream.core.checkpoint;

public enum CheckpointType {
    CHECKPOINT_TYPE(true, "checkpoint"),       // 常规 checkpoint
    SAVEPOINT_TYPE(false, "savepoint"),        // 用户触发
    COMPLETED_POINT_TYPE(true, "completed");   // 任务完成
    
    private final boolean auto;
    private final String name;
    
    public boolean isFinalCheckpoint() {
        return this == COMPLETED_POINT_TYPE || this == SAVEPOINT_TYPE;
    }
}
```

#### 4.1.3 新增 PendingCheckpoint

```java
package io.nop.stream.runtime.checkpoint;

/**
 * 正在进行的 checkpoint，跟踪任务 ACK 状态
 */
public class PendingCheckpoint implements Checkpoint {
    private final long jobId;
    private final int pipelineId;
    private final long checkpointId;
    private final long triggerTimestamp;
    private final CheckpointType checkpointType;
    
    // 尚未 ACK 的任务集合
    private final Set<Long> notYetAcknowledgedTasks;
    
    // 任务状态统计
    private final Map<Long, TaskStatistics> taskStatistics;
    
    // 完成后的 Future
    private final CompletableFuture<CompletedCheckpoint> completableFuture;
    
    public void acknowledgeTask(long taskId, List<StateSnapshot> states) {
        notYetAcknowledgedTasks.remove(taskId);
        // 记录状态...
        
        if (isFullyAcknowledged()) {
            completableFuture.complete(toCompletedCheckpoint());
        }
    }
    
    public boolean isFullyAcknowledged() {
        return notYetAcknowledgedTasks.isEmpty();
    }
}
```

#### 4.1.4 新增 CompletedCheckpoint

```java
package io.nop.stream.runtime.checkpoint;

/**
 * 已完成的 checkpoint，包含所有任务的状态快照
 */
public class CompletedCheckpoint implements Serializable {
    private final long jobId;
    private final int pipelineId;
    private final long checkpointId;
    private final long triggerTimestamp;
    private final long completedTimestamp;
    private final CheckpointType checkpointType;
    
    // 所有任务的状态
    private final Map<Long, TaskState> taskStates;
    
    // 是否从存储恢复
    private boolean restored = false;
}
```

### 4.2 Phase 2：协调器实现（2周）

#### 4.2.1 新增 CheckpointCoordinator

```java
package io.nop.stream.runtime.checkpoint;

/**
 * Checkpoint 协调器，负责触发、跟踪和完成 checkpoint
 */
public class CheckpointCoordinator {
    private final long jobId;
    private final CheckpointStorage checkpointStorage;
    private final CheckpointIDCounter checkpointIdCounter;
    private final CheckpointConfig config;
    
    // 正在进行的 checkpoint
    private final ConcurrentHashMap<Long, PendingCheckpoint> pendingCheckpoints;
    
    // 最近完成的 checkpoint
    private volatile CompletedCheckpoint latestCompletedCheckpoint;
    
    // 定时调度器
    private ScheduledExecutorService scheduler;
    
    /**
     * 启动定时 checkpoint 触发
     */
    public void startCheckpointScheduler() {
        scheduler.scheduleAtFixedRate(
            () -> tryTriggerPendingCheckpoint(CheckpointType.CHECKPOINT_TYPE),
            config.getCheckpointInterval(),
            config.getCheckpointInterval(),
            TimeUnit.MILLISECONDS
        );
    }
    
    /**
     * 尝试触发新的 checkpoint
     */
    protected void tryTriggerPendingCheckpoint(CheckpointType type) {
        // 1. 检查是否可以触发（无正在进行的 checkpoint 等）
        if (pendingCounter.get() > 0) {
            return;
        }
        
        // 2. 生成 checkpoint ID
        long checkpointId = checkpointIdCounter.getAndIncrement();
        
        // 3. 创建 PendingCheckpoint
        PendingCheckpoint pending = new PendingCheckpoint(
            jobId, pipelineId, checkpointId, 
            System.currentTimeMillis(), type,
            getNotYetAcknowledgedTasks()
        );
        
        pendingCheckpoints.put(checkpointId, pending);
        
        // 4. 创建并发送 Barrier
        CheckpointBarrier barrier = new CheckpointBarrier(
            checkpointId, pending.getCheckpointTimestamp(), type);
        triggerCheckpoint(barrier);
        
        // 5. 设置超时
        scheduleTimeout(pending);
    }
    
    /**
     * 处理任务 ACK
     */
    public void acknowledgeTask(long taskId, long checkpointId, List<StateSnapshot> states) {
        PendingCheckpoint pending = pendingCheckpoints.get(checkpointId);
        if (pending == null) return;
        
        pending.acknowledgeTask(taskId, states);
    }
    
    /**
     * 完成 checkpoint
     */
    public void completePendingCheckpoint(CompletedCheckpoint completed) {
        // 1. 持久化到存储
        checkpointStorage.storeCheckPoint(completed);
        
        // 2. 更新最新完成的 checkpoint
        latestCompletedCheckpoint = completed;
        
        // 3. 通知所有任务 checkpoint 完成
        notifyCheckpointCompleted(completed);
        
        // 4. 清理 pending
        pendingCheckpoints.remove(completed.getCheckpointId());
    }
    
    /**
     * 故障恢复
     */
    public void restoreFromCheckpoint() {
        CompletedCheckpoint checkpoint = checkpointStorage.getLatestCheckpoint(jobId);
        if (checkpoint != null) {
            latestCompletedCheckpoint = checkpoint;
            // 通知任务恢复状态
            notifyTaskRestore(checkpoint);
        }
    }
}
```

### 4.3 Phase 3：存储层实现（1周）

#### 4.3.1 CheckpointStorage 接口

```java
package io.nop.stream.core.checkpoint.storage;

/**
 * Checkpoint 持久化存储接口
 */
public interface ICheckpointStorage {
    /**
     * 存储 checkpoint
     */
    String storeCheckPoint(CompletedCheckpoint checkpoint) throws Exception;
    
    /**
     * 获取最近的 checkpoint
     */
    CompletedCheckpoint getLatestCheckpoint(long jobId, int pipelineId) throws Exception;
    
    /**
     * 获取所有 checkpoint
     */
    List<CompletedCheckpoint> getAllCheckpoints(long jobId) throws Exception;
    
    /**
     * 删除 checkpoint
     */
    void deleteCheckpoint(long jobId, int pipelineId, long checkpointId) throws Exception;
    
    /**
     * 获取存储名称
     */
    String getName();
}
```

#### 4.3.2 本地文件存储实现

```java
package io.nop.stream.runtime.checkpoint.storage;

/**
 * 本地文件系统存储实现
 */
public class LocalFileCheckpointStorage implements ICheckpointStorage {
    private final String baseDir;
    private final ISerializer serializer;
    
    @Override
    public String storeCheckPoint(CompletedCheckpoint checkpoint) throws Exception {
        String path = getCheckpointPath(checkpoint);
        byte[] data = serializer.serialize(checkpoint);
        FileHelper.writeBytes(new File(path), data);
        return path;
    }
    
    @Override
    public CompletedCheckpoint getLatestCheckpoint(long jobId, int pipelineId) throws Exception {
        File dir = new File(getJobDir(jobId, pipelineId));
        File[] files = dir.listFiles((d, name) -> name.endsWith(".checkpoint"));
        if (files == null || files.length == 0) return null;
        
        // 找最新的
        Arrays.sort(files, (a, b) -> Long.compare(
            extractCheckpointId(b.getName()), extractCheckpointId(a.getName())));
        
        return serializer.deserialize(FileHelper.readBytes(files[0]));
    }
}
```

#### 4.3.3 数据库存储实现（利用 nop-orm）

```java
package io.nop.stream.runtime.checkpoint.storage;

/**
 * 基于 nop-orm 的数据库存储实现
 */
public class JdbcCheckpointStorage implements ICheckpointStorage {
    private final IOrmTemplate ormTemplate;
    
    @Override
    public String storeCheckPoint(CompletedCheckpoint checkpoint) throws Exception {
        StreamCheckpointPO po = new StreamCheckpointPO();
        po.setJobId(checkpoint.getJobId());
        po.setPipelineId(checkpoint.getPipelineId());
        po.setCheckpointId(checkpoint.getCheckpointId());
        po.setCheckpointType(checkpoint.getCheckpointType().getName());
        po.setTriggerTime(new Date(checkpoint.getTriggerTimestamp()));
        po.setCompletedTime(new Date(checkpoint.getCompletedTimestamp()));
        po.setStateData(serializeStates(checkpoint));
        
        ormTemplate.save(po);
        return String.valueOf(po.getSid());
    }
}
```

### 4.4 Phase 4：与算子集成（1周）

#### 4.4.1 恢复 StreamOperator.snapshotState

```java
// StreamOperator.java - 恢复被注释的方法
public interface StreamOperator<OUT> extends CheckpointListener, KeyContext, Serializable {
    
    /**
     * 执行状态快照
     * @param checkpointId checkpoint ID
     * @return 状态快照结果
     */
    default OperatorSnapshotResult snapshotState(long checkpointId) throws Exception {
        // 默认实现：返回空快照
        return OperatorSnapshotResult.empty();
    }
    
    /**
     * 从快照恢复状态
     * @param state 恢复的状态数据
     */
    default void initializeState(TaskState state) throws Exception {
        // 默认实现：无操作
    }
}
```

#### 4.4.2 新增 OperatorSnapshotResult

```java
package io.nop.stream.core.checkpoint;

/**
 * 算子快照结果
 */
public class OperatorSnapshotResult implements Serializable {
    private final Map<String, byte[]> operatorStates;      // 算子状态
    private final Map<String, byte[]> keyedStates;         // Keyed 状态
    private final Map<String, byte[]> rawKeyedStates;      // 原始 Keyed 状态
    
    public static OperatorSnapshotResult empty() {
        return new OperatorSnapshotResult(Collections.emptyMap(), 
            Collections.emptyMap(), Collections.emptyMap());
    }
}
```

#### 4.4.3 Source/Sink 与 Checkpoint 集成

```java
/**
 * 支持 Exactly-Once 的 Sink 接口
 */
public interface TwoPhaseCommitSinkFunction<IN> extends SinkFunction<IN> {
    
    /**
     * 开始事务
     */
    void beginTransaction() throws Exception;
    
    /**
     * 在事务中写入数据
     */
    void invoke(IN value) throws Exception;
    
    /**
     * 预提交事务（checkpoint 完成前）
     */
    void preCommit(long checkpointId) throws Exception;
    
    /**
     * 提交事务（checkpoint 完成后）
     */
    void commit(long checkpointId) throws Exception;
    
    /**
     * 回滚事务
     */
    void rollback() throws Exception;
}
```

### 4.5 Phase 5：配置与监控（1周）

#### 4.5.1 CheckpointConfig

```java
package io.nop.stream.core.checkpoint;

/**
 * Checkpoint 配置
 */
public class CheckpointConfig {
    // 是否启用 checkpoint
    private boolean checkpointEnabled = true;
    
    // checkpoint 间隔（毫秒）
    private long checkpointInterval = 60000;
    
    // checkpoint 超时（毫秒）
    private long checkpointTimeout = 600000;
    
    // 两次 checkpoint 之间的最小间隔
    private long checkpointMinPause = 500;
    
    // 最大并发 checkpoint 数
    private int maxConcurrentCheckpoints = 1;
    
    // 保留的 checkpoint 数量
    private int maxRetainedCheckpoints = 5;
    
    // 存储类型
    private String storageType = "local";
    
    // 存储配置
    private Map<String, String> storageConfig = new HashMap<>();
}
```

#### 4.5.2 监控指标

```java
package io.nop.stream.runtime.checkpoint.metrics;

/**
 * Checkpoint 监控指标
 */
public class CheckpointMetrics {
    // 已完成的 checkpoint 数量
    private final AtomicLong numCompletedCheckpoints = new AtomicLong();
    
    // 失败的 checkpoint 数量
    private final AtomicLong numFailedCheckpoints = new AtomicLong();
    
    // 最新 checkpoint 的大小
    private final AtomicLong latestCheckpointSize = new AtomicLong();
    
    // 最新 checkpoint 耗时
    private final AtomicLong latestCheckpointDuration = new AtomicLong();
    
    // 状态总大小
    private final AtomicLong totalStateSize = new AtomicLong();
}
```

---

## 5. 实现路线图

```
Week 1-2: Phase 1 - 核心组件
├── CheckpointBarrier
├── CheckpointType
├── PendingCheckpoint
├── CompletedCheckpoint
└── 单元测试

Week 3-4: Phase 2 - 协调器
├── CheckpointCoordinator
├── CheckpointIDCounter
├── 定时触发机制
├── ACK 跟踪机制
└── 集成测试

Week 5: Phase 3 - 存储层
├── ICheckpointStorage 接口
├── LocalFileCheckpointStorage
├── JdbcCheckpointStorage
└── 存储测试

Week 6: Phase 4 - 算子集成
├── 恢复 snapshotState 方法
├── TwoPhaseCommitSinkFunction
├── Source 状态恢复
└── 端到端测试

Week 7: Phase 5 - 配置与监控
├── CheckpointConfig
├── CheckpointMetrics
├── Web UI 展示
└── 文档完善
```

---

## 6. 测试计划

### 6.1 单元测试

| 测试项 | 测试内容 |
|--------|---------|
| CheckpointBarrierTest | Barrier 创建、序列化、比较 |
| PendingCheckpointTest | ACK 跟踪、完成条件、超时处理 |
| CheckpointCoordinatorTest | 触发、ACK、完成、超时、恢复 |
| LocalFileCheckpointStorageTest | 存储、读取、删除、并发 |
| JdbcCheckpointStorageTest | CRUD、事务、并发 |

### 6.2 集成测试

| 测试项 | 测试内容 |
|--------|---------|
| CheckpointIntegrationTest | 完整 checkpoint 生命周期 |
| RecoveryIntegrationTest | 故障恢复流程 |
| TwoPhaseCommitTest | 两阶段提交语义 |
| StateMigrationTest | 状态版本兼容 |

### 6.3 性能测试

| 测试项 | 指标 |
|--------|------|
| Checkpoint 开销 | 对吞吐量的影响 < 5% |
| 状态大小 | 支持单算子 100MB+ 状态 |
| 恢复时间 | 10s 内完成恢复 |

---

## 7. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 状态序列化性能 | checkpoint 耗时过长 | 使用 Protobuf/Kryo 优化序列化 |
| 存储并发写入 | 数据丢失 | 使用原子写入 + 临时文件 |
| 状态版本不兼容 | 恢复失败 | 实现状态迁移机制 |
| 内存占用过大 | OOM | 实现增量 checkpoint |

---

## 8. 后续扩展

### 8.1 增量 Checkpoint

只保存变化的状态，减少 checkpoint 大小和时间。

### 8.2 对齐 Checkpoint

在多输入算子中，等待所有输入的 barrier 到达后再执行快照。

### 8.3 非对齐 Checkpoint

不等待 barrier 对齐，直接快照，适用于高吞吐场景。

### 8.4 分布式 Checkpoint

支持多节点协调，实现真正的分布式故障恢复。

---

## 9. 参考资料

1. Apache SeaTunnel Checkpoint 实现
   - `CheckpointCoordinator.java`
   - `PendingCheckpoint.java`
   - `CheckpointBarrier.java`
   - `CheckpointStorage.java`

2. Apache Flink Checkpoint 机制
   - Chandy-Lamport 算法
   - ABS (Aligned Barrier Snapshot)
   - UBS (Unaligned Barrier Snapshot)

3. Nop 平台设计理念
   - 可逆计算
   - 模型驱动
   - 差量定制
