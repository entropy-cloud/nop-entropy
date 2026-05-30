# 维度 14：异步与事务模式

## 审计范围

nop-stream 全部子模块中与并发控制、线程安全、异步处理相关的所有源文件。

## 第 1 轮（初审）

### [维度14-01] TwoPhaseCommitSinkFunction.finishCommit() 对 synchronizedMap 的复合操作缺乏原子性

- **文件**: `nop-stream-core/.../functions/sink/TwoPhaseCommitSinkFunction.java:76-95`
- **证据片段**:
```java
for (Map.Entry<Long, Object> entry : pending.entrySet()) {
    if (entry.getKey() <= epochId) {
        toCommit.put(entry.getKey(), entry.getValue());
    }
}
for (Long eid : toCommit.keySet()) {
    commit(eid);
    pending.remove(eid);  // 修改操作，但整个方法内无统一锁
}
```
- **严重程度**: P2
- **现状**: pendingCommits 是 Collections.synchronizedMap(new TreeMap<>())。finishCommit() 中遍历 + 条件过滤 + 删除不是原子操作。如果另一个线程（如 restoreFromEpoch 中调用 pending.clear()）并发修改该 map，将抛出 ConcurrentModificationException。
- **风险**: 在 exactly-once 语义下，并发修改可能导致事务状态不一致或 CME 崩溃。
- **建议**: 将整个 finishCommit 方法体包裹在 synchronized(pending) { ... } 块中。
- **信心水平**: 很可能
- **误报排除**: 不是理论性问题——synchronizedMap 的文档明确说明复合操作需要外部同步。
- **复核状态**: 未复核

### [维度14-02] InputGate.readMultiChannel() 递归调用 + Thread.sleep 热路径阻塞

- **文件**: `nop-stream-core/.../execution/InputGate.java:204-311`
- **证据片段**:
```java
// 递归调用
if (!allWatermarksUpdated) {
    return readMultiChannel();  // 递归
}
// 热路径阻塞
if (allChannelsEmpty) {
    Thread.sleep(10);  // 热路径固定休眠
}
```
- **严重程度**: P2
- **现状**: handleBarrier() 和 handleWatermark() 在条件未满足时递归调用 readMultiChannel()，存在栈溢出风险。当所有通道为空时执行 Thread.sleep(10)，在低吞吐量场景下显著增加延迟。
- **风险**: 多输入通道场景下连续 barrier/watermark 导致递归深度增长→StackOverflowError。每10ms固定休眠增加流处理延迟。
- **建议**: 将递归改为 while 循环中 continue。移除 Thread.sleep(10)，改为依赖 channel.read(timeout) 的阻塞超时。
- **信心水平**: 确定
- **误报排除**: 不是理论性问题——递归深度和固定延迟是可量化的运行时风险。
- **复核状态**: 未复核

### [维度14-03] PendingCheckpoint.dispose() 存在 TOCTOU，但影响有限

- **文件**: `nop-stream-runtime/.../checkpoint/PendingCheckpoint.java:176-185`
- **严重程度**: P3
- **现状**: check-then-act 非原子（volatile isDisposed）。但 clear()、cancel() 都是幂等操作，重复执行无害。
- **建议**: 可用 AtomicBoolean.compareAndSet 提高防御性。当前可接受。
- **信心水平**: 确定
- **误报排除**: 影响有限——所有操作都是幂等的。
- **复核状态**: 未复核

### [维度14-04] MemoryKeyedStateBackend 使用非线程安全 HashMap——需文档化

- **文件**: `nop-stream-core/.../state/backend/memory/MemoryKeyedStateBackend.java:63`
- **严重程度**: P3
- **现状**: states 使用 HashMap（非线程安全），依赖单线程访问假设。设计正确但未在 Javadoc 中标注。
- **建议**: 在类 Javadoc 中明确标注"单线程访问假设"。
- **信心水平**: 确定
- **误报排除**: 不是设计问题——是文档缺失。
- **复核状态**: 未复核

### 正面发现

- BarrierAligner: ReentrantLock + Condition 使用正确
- LocalFileCheckpointStorage: ReentrantReadWriteLock 使用正确
- TaskExecutor: AtomicBoolean + ConcurrentHashMap + daemon threads，资源管理完善
- CheckpointBarrierTracker: synchronized 保护 checkpoint overlap
- PendingCheckpoint: AtomicReference<Status> + CAS 状态机
- 所有线程池使用 daemon 线程，关闭路径正确
- CompletableFuture 使用在 CAS 保护下
- 未发现资源泄漏
