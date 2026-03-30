# nop-stream Checkpoint 架构设计

> 快速参考文档 - 配合 [checkpoint-improvement-plan.md](./checkpoint-improvement-plan.md) 使用
>
> 注：当前代码中 `CheckpointType` 枚举项为 `CHECKPOINT`、`SAVEPOINT`、
> `COMPLETED_POINT_TYPE`。

## 1. 整体架构

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                              nop-stream Checkpoint 架构                             │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                     │
│  ┌───────────────────────────────────────────────────────────────────────────────┐ │
│  │                            StreamExecutionEnvironment                         │ │
│  │  - 配置 CheckpointConfig                                                      │ │
│  │  - 创建 CheckpointCoordinator                                                 │ │
│  └───────────────────────────────────────────────────────────────────────────────┘ │
│                                          │                                          │
│                                          ▼                                          │
│  ┌───────────────────────────────────────────────────────────────────────────────┐ │
│  │                            CheckpointCoordinator                              │ │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────────┐   │ │
│  │  │ Scheduler       │  │ StateManager    │  │ PendingCheckpoints Map      │   │ │
│  │  │ - 定时触发      │  │ - 状态机管理    │  │ - checkpointId -> Pending   │   │ │
│  │  │ - 超时检测      │  │ - 恢复逻辑      │  │                             │   │ │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────────────────┘   │ │
│  │                                        │                                      │ │
│  │                                        ▼                                      │ │
│  │  ┌─────────────────────────────────────────────────────────────────────────┐ │ │
│  │  │                         CheckpointIDCounter                             │ │ │
│  │  │                         (AtomicLong / Redis)                            │ │ │
│  │  └─────────────────────────────────────────────────────────────────────────┘ │ │
│  └───────────────────────────────────────────────────────────────────────────────┘ │
│                                          │                                          │
│                                          ▼                                          │
│  ┌───────────────────────────────────────────────────────────────────────────────┐ │
│  │                              CheckpointBarrier                               │ │
│  │  ┌─────────────────────────────────────────────────────────────────────┐    │ │
│  │  │  id: long                                                          │    │ │
│  │  │  timestamp: long                                                    │    │ │
│  │  │  checkpointType: CheckpointType (CHECKPOINT/SAVEPOINT/COMPLETED_POINT_TYPE) │ │ │
│  │  └─────────────────────────────────────────────────────────────────────┘    │ │
│  └───────────────────────────────────────────────────────────────────────────────┘ │
│                                          │                                          │
│                        ┌─────────────────┼─────────────────┐                       │
│                        ▼                 ▼                 ▼                       │
│  ┌─────────────────────────┐ ┌─────────────────────────┐ ┌─────────────────────────┐│
│  │     SourceOperator      │ │     TransformOperator   │ │     SinkOperator        ││
│  │  ┌───────────────────┐  │ │  ┌───────────────────┐  │ │  ┌───────────────────┐  ││
│  │  │ SourceContext     │  │ │  │ StateBackend      │  │ │  │ TwoPhaseCommit    │  ││
│  │  │ - emit Barrier    │  │ │  │ - ValueState      │  │ │  │ - beginTx         │  ││
│  │  │ - snapshotState   │  │ │  │ - MapState        │  │ │  │ - preCommit       │  ││
│  │  │ - restoreState    │  │ │  │ - snapshotState   │  │ │  │ - commit          │  ││
│  │  └───────────────────┘  │ │  └───────────────────┘  │ │  └───────────────────┘  ││
│  └─────────────────────────┘ └─────────────────────────┘ └─────────────────────────┘│
│                        │                 │                 │                         │
│                        └─────────────────┼─────────────────┘                         │
│                                          ▼                                          │
│  ┌───────────────────────────────────────────────────────────────────────────────┐ │
│  │                            CheckpointStorage                                  │ │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────────┐   │ │
│  │  │ LocalFileStorage│  │ JdbcStorage     │  │ RedisStorage (Planned)       │   │ │
│  │  │ - 文件系统      │  │ - nop-orm       │  │ - 高性能分布式存储          │   │ │
│  │  │ - 简单可靠      │  │ - 事务支持      │  │ - 适合集群部署              │   │ │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────────────────┘   │ │
│  └───────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                     │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## 2. 核心类关系

```
                    ┌──────────────────┐
                    │  CheckpointType  │
                    │  - CHECKPOINT    │
                    │  - SAVEPOINT     │
                    │  - COMPLETED_POINT_TYPE │
                    └────────┬─────────┘
                             │
                             ▼
┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│CheckpointBarrier │──│PendingCheckpoint │──│CompletedCheckpoint│
│ - id             │  │ - jobId          │  │ - jobId          │
│ - timestamp      │  │ - pipelineId     │  │ - pipelineId     │
│ - type           │  │ - checkpointId   │  │ - checkpointId   │
└──────────────────┘  │ - triggerTime    │  │ - completedTime  │
                      │ - notAckTasks    │  │ - taskStates     │
                      │ - future         │  │ - restored       │
                      └────────┬─────────┘  └────────┬─────────┘
                               │                     │
                               ▼                     ▼
                    ┌──────────────────────────────────┐
                    │      CheckpointCoordinator       │
                    │  - pendingCheckpoints: Map       │
                    │  - latestCompletedCheckpoint     │
                    │  - scheduler: ScheduledExecutor  │
                    │  - checkpointStorage             │
                    └──────────────────────────────────┘
                                     │
                                     ▼
                    ┌──────────────────────────────────┐
                    │       ICheckpointStorage         │
                    │  + storeCheckPoint()             │
                    │  + getLatestCheckpoint()         │
                    │  + deleteCheckpoint()            │
                    └──────────────────────────────────┘
                                     │
                    ┌────────────────┼────────────────┐
                    ▼                ▼                ▼
          ┌────────────────┐ ┌────────────────┐ ┌────────────────┐
          │LocalFileStorage│ │ JdbcStorage    │ │ RedisStorage   │
          └────────────────┘ └────────────────┘ └────────────────┘
```

## 3. Checkpoint 流程时序

```
┌─────────┐          ┌─────────┐          ┌─────────┐          ┌─────────┐
│Scheduler│          │Coordina-│          │ Source  │          │  Sink   │
│         │          │  tor    │          │ Operator│          │Operator │
└────┬────┘          └────┬────┘          └────┬────┘          └────┬────┘
     │                    │                    │                    │
     │  timeout           │                    │                    │
     │───────────────────>│                    │                    │
     │                    │                    │                    │
     │                    │ createPending      │                    │
     │                    │ Checkpoint         │                    │
     │                    │                    │                    │
     │                    │  CheckpointBarrier │                    │
     │                    │───────────────────>│                    │
     │                    │                    │                    │
     │                    │                    │ snapshotState()    │
     │                    │                    │                    │
     │                    │                    │  Barrier + Data    │
     │                    │                    │───────────────────>│
     │                    │                    │                    │
     │                    │                    │                    │ preCommit()
     │                    │                    │                    │
     │                    │  ACK (state)       │                    │
     │                    │<───────────────────│                    │
     │                    │                    │                    │
     │                    │  ACK (state)       │                    │
     │                    │<────────────────────────────────────────│
     │                    │                    │                    │
     │                    │ all ACKed          │                    │
     │                    │                    │                    │
     │                    │ storeCheckPoint()  │                    │
     │                    │                    │                    │
     │                    │  notifyComplete    │                    │
     │                    │───────────────────>│                    │
     │                    │                    │                    │
     │                    │  notifyComplete    │                    │
     │                    │────────────────────────────────────────>│
     │                    │                    │                    │
     │                    │                    │                    │ commit()
     │                    │                    │                    │
     ▼                    ▼                    ▼                    ▼
```

## 4. 状态恢复流程

```
┌─────────────────────────────────────────────────────────────────┐
│                        故障恢复流程                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. 检测故障                                                    │
│     └── Task 执行异常 / 超时 / 节点宕机                         │
│                                                                 │
│  2. 获取最近 Checkpoint                                         │
│     └── checkpointStorage.getLatestCheckpoint(jobId)           │
│                                                                 │
│  3. 反序列化状态                                                │
│     └── serializer.deserialize(checkpointData)                 │
│                                                                 │
│  4. 通知任务恢复                                                │
│     └── for each task: notifyTaskRestore(taskState)            │
│                                                                 │
│  5. 算子初始化状态                                              │
│     └── operator.initializeState(taskState)                    │
│                                                                 │
│  6. 重新启动任务                                                │
│     └── 从 checkpoint 之后的 offset 开始消费                    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## 5. 两阶段提交流程

```
┌─────────────────────────────────────────────────────────────────┐
│                    Two-Phase Commit Sink                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Phase 1: Pre-Commit                                            │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  1. 收到 CheckpointBarrier                               │  │
│  │  2. flush 缓冲数据到外部系统                             │  │
│  │  3. preCommit() - 标记事务准备提交                        │  │
│  │  4. snapshotState() - 保存事务 ID 到状态                 │  │
│  │  5. ACK 到 Coordinator                                   │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                 │
│  Phase 2: Commit / Rollback                                     │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  成功:                                                    │  │
│  │    1. notifyCheckpointComplete() 被调用                  │  │
│  │    2. commit() - 真正提交事务到外部系统                   │  │
│  │                                                           │  │
│  │  失败:                                                    │  │
│  │    1. notifyCheckpointAborted() 被调用                   │  │
│  │    2. rollback() - 回滚事务                              │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

示例代码：

```java
public class JdbcTwoPhaseCommitSink implements TwoPhaseCommitSinkFunction<Record> {
    private Connection connection;
    private String currentTxId;
    
    @Override
    public void beginTransaction() {
        connection = dataSource.getConnection();
        connection.setAutoCommit(false);
        currentTxId = UUID.randomUUID().toString();
    }
    
    @Override
    public void invoke(Record value) {
        // 写入数据到临时表或带事务ID的表
        insertWithTxId(value, currentTxId);
    }
    
    @Override
    public void preCommit(long checkpointId) {
        // flush 确保所有数据已写入
        connection.flush();
    }
    
    @Override
    public void commit(long checkpointId) {
        // 真正提交事务
        connection.commit();
        // 标记临时数据为已提交（可被清理）
        markDataCommitted(currentTxId);
    }
    
    @Override
    public void rollback() {
        connection.rollback();
        // 清理临时数据
        cleanupTempData(currentTxId);
    }
}
```

## 6. 配置示例

```yaml
# application.yaml
stream:
  checkpoint:
    enabled: true
    interval: 60000           # 60秒
    timeout: 600000           # 10分钟
    min-pause: 500            # 最小间隔 500ms
    max-concurrent: 1         # 最大并发 checkpoint
    max-retained: 5           # 保留最近 5 个
    
    storage:
      type: jdbc              # local / jdbc / redis
      config:
        table-name: stream_checkpoint
        data-source: default
```

## 7. 关键接口速查

| 接口 | 方法 | 说明 |
|------|------|------|
| `ICheckpointStorage` | `storeCheckPoint()` | 存储 checkpoint |
| | `getLatestCheckpoint()` | 获取最近的 checkpoint |
| | `deleteCheckpoint()` | 删除 checkpoint |
| `CheckpointListener` | `notifyCheckpointComplete()` | checkpoint 完成通知 |
| | `notifyCheckpointAborted()` | checkpoint 中止通知 |
| `StreamOperator` | `snapshotState()` | 执行状态快照 |
| | `initializeState()` | 从快照恢复 |
| `TwoPhaseCommitSink` | `beginTransaction()` | 开始事务 |
| | `preCommit()` | 预提交 |
| | `commit()` | 提交 |
| | `rollback()` | 回滚 |
| `IStateBackend` | `createKeyedStateBackend()` | 创建 Keyed 状态后端 |
| `IKeyedStateBackend` | `getState()` | 获取状态 |
| | `setCurrentKey()` | 设置当前 key |
