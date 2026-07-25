# Mailbox 控制面设计

> Status: active
> Created: 2026-07-25
> Parent: `01-architecture-baseline.md` §4（执行模型）、`checkpoint-design.md` §2.2（Epoch 生命周期）、§8.7（Checkpoint 超时 Abort）
> See also: `graph-model-design.md`（StreamTaskInvokable / SubtaskTask / TaskExecutor）

## 1. 定位与目标

nop-stream 引入**最小化 mailbox 控制面**，作为每个 task 线程的**控制信号通道**。它把以下两类原本依赖脆弱跨线程交接（cap-1 队列 + `Thread.interrupt()`）的控制动作，收敛为 task 线程上的**协作式 mail/flag**：

1. **SOURCE 任务的 checkpoint 触发**：barrier-injector 线程向 source task 的 mailbox 投递 trigger-checkpoint mail，source task 在数据发射点（`SourceContext.collect()`）drain 该 mail 并在**本 task 线程**执行 `snapshotState`→`emitBarrier`。
2. **所有任务的 abort**：abort handler 投递 cancel mail/置 cancel flag，task 线程在主循环顶部观察到后优雅退出；`Thread.interrupt()` 仅保留为**解除阻塞数据读**的手段（无现成非阻塞 `InputGate.read()` API）。

### 1.1 显式 Non-Goals

- **不移植 Flink `MailboxProcessor` 全套语义**（不做 Deferrable/Suspension mail、不做 exact-order yield、不做 multi-input mailbox 算子抢占）。
- **middle/sink 的 `triggerCheckpoint` 保持 injector 线程同步**（不改 mail）。理由见 §3.2。
- **finished-source final checkpoint 保留 injector 线程执行**（task 线程已不存在，显式例外）。理由见 §3.3。
- **不引入非阻塞 `InputGate.read()`**：abort 用 interrupt 解除阻塞 read + 循环顶 cancel 检查。
- processing-time timer 生产接线（仅奠基，不接线）；异步 snapshot；跨 JVM；多输入算子；拆除线程池。

## 2. 原语契约

三类原语位于 `nop-stream-core/.../execution/`：

### 2.1 `Mail`

| 字段 | 类型 | 说明 |
|---|---|---|
| `action` | `Runnable` | 在 task 线程上执行的闭包 |
| `priority` | `Mail.Priority` | `CONTROL`（高）/ `NORMAL`（低） |
| `description` | `String` | 诊断用描述 |

工厂：`Mail.control(action, desc)` / `Mail.normal(action, desc)`。单消费者：只有 owning task 线程可调 `run()`；生产者（injector 线程、abort handler 线程）只调 `TaskMailbox.put(Mail)`。

### 2.2 `TaskMailbox`

单消费者、多生产者邮箱。每 subtask 一个。

| 方法 | 语义 |
|---|---|
| `put(Mail)` | 非阻塞投递；任意线程可调；closed 后静默丢弃（终态） |
| `poll()` | **非阻塞**取出；CONTROL 类先于 NORMAL 类；空时立即返回 `null`（不阻塞） |
| `take()` | 阻塞取；mail 到达或 mailbox 关闭后返回；close 后返回 `null` 使消费者退出 |
| `close()` | 关闭邮箱（幂等）；唤醒阻塞的 `take()` |
| `drainAndRun()` | 在调用（task）线程上取出并运行所有 pending mail（CONTROL 先） |

**排序契约**：
- 同一 priority 类内 FIFO（每类一个 `ConcurrentLinkedQueue`）。
- CONTROL 类 mail **总是**先于 NORMAL 类被取出（当两者同时 pending）。

**线程安全**：基于两个 `ConcurrentLinkedQueue` + 一个内部锁/条件（`take()` 阻塞用）。多生产者并发 `put()` 安全；单消费者 `poll()`/`take()`。

### 2.3 `MailboxExecutor`

持有 `TaskMailbox` + 协作式 cancel flag，是 task 线程控制面的锚点。

| 方法 | 语义 |
|---|---|
| `getMailbox()` | 返回所持 `TaskMailbox` |
| `cancel()` | 置 cancel flag（abort 路径的协作半） |
| `isCancelled()` | task 线程在主循环顶查询 |
| `processAvailableMails()` | drain 当前 pending mail（CONTROL 先）；返回 cancel flag |
| `signalCancel()` | 置 cancel flag + 投递 CONTROL marker mail（唤醒阻塞 drain） |
| `runLoop()` | 独立控制面循环（`while(!cancelled){ mail=poll(); ... }`）—— 为未来 processing-time timer 接线奠基，当前 task 主循环不委托给它 |

### 2.4 归属契约

| 归属 | 创建/持有者 | 生命周期 |
|---|---|---|
| `TaskMailbox` | **每 subtask 一个**，由 `StreamTaskInvokable` 创建并经 `MailboxExecutor` 持有 | = task 生命周期 |
| `MailboxExecutor` | `StreamTaskInvokable` 持有，经 `getMailboxExecutor()` 暴露 | = task 生命周期 |
| 消费者 | owning task 线程（`SubtaskTask.run()`）独占 `poll()`/`take()` | 单消费者 |
| 生产者 | barrier-injector 线程（trigger-checkpoint mail）、abort handler 线程（cancel mail） | 多生产者 |

引用链：`GraphModelCheckpointExecutor` 的 abort handler(659-683) 经 `tasks`→`SubtaskTask`→`Subtask`→`StreamTaskInvokable.getMailboxExecutor()` 取得 handle 投递 cancel mail。

## 3. 三种用法与同步决策

### 3.1 用法 A — SOURCE trigger-checkpoint 经 mail（发射点 drain）

```
barrier-injector thread:
    invokable.getMailboxExecutor().getMailbox().put(
        Mail.control(() -> sourceOp.injectBarrier(barrier), "trigger-checkpoint-" + id));

source task thread (in SourceContext.collect()):
    mailboxExecutor.processAvailableMails();   // drain → injectBarrier() on task thread
    output.collect(new StreamRecord<>(element));
```

- `StreamSourceOperator.pendingBarriers`（cap-1 `LinkedBlockingQueue`）**移除**。
- `SourceContext.collect()`/`collectWithTimestamp()` 发射点改为 drain mailbox。
- source `snapshotState`/`emitBarrier` 在 task 线程经 mail 触发（与改造前 on-task-thread 序一致）。

### 3.2 用法 B — middle/sink trigger **保持 injector 线程同步**（不改 mail）

**决策**：middle/sink 的 `CheckpointBarrierTracker.triggerCheckpoint()` 保持 `synchronized` + 在 injector 线程同步执行（仅 prime ack 计数 + 返回 `boolean accepted`）。

**为什么不能改 mail**：middle/sink 的 barrier 经数据流（`InputGate.read()`→`processBarrier`）in-band 到达。当前不变式是 **"下游 ack 计数在 barrier 经数据流到达前已 prime"**：injector 线程同步调 `triggerCheckpoint()` prime 了 `currentCheckpointId`/`operatorsToAck`，且该 prime **先于** barrier 经数据流到达下游。若改为 fire-and-forget mail，mail 可能晚于 in-band barrier 被处理 → ACK 被 `acknowledgeOperator` 因 `currentCheckpointId<0` 静默丢弃 → checkpoint **hang**。

**当前同步 trigger 已 `synchronized` 安全**：仅 prime 计数、不执行 operator 代码（不调 `snapshotState`）。middle/sink 的 in-line barrier 处理（`AbstractStreamOperator.processBarrier()`→`snapshotState`→`acknowledgeOperator`）在 task 线程，不变。

### 3.3 用法 C — finished-source final checkpoint 例外（保留 injector 线程）

源完成后 `invokeSource()` 返回、`SubtaskTask.run()` 跳出循环（line 77-80），**source task 线程不再存在**。此时 `offerBarrier()` 的 `finished` 分支直接在 injector 线程调 `injectBarrier()`→`snapshotState`+`emitBarrier`（final checkpoint）。**无 task 线程可投递 mail —— 本路径显式保留 injector 线程执行**。

### 3.4 用法 D — middle/sink 主循环顶检查 mailbox（cancel mail）

```
processInputGate() (MIDDLE/SINK):
    while (true) {
        if (mailboxExecutor.processAvailableMails()) break;  // cancel observed → exit
        Optional<StreamElement> elementOpt = inputGate.read();
        ...
    }
```

- cancel mail 与未来 mail（processing-time timer）在循环顶 `poll` 处理。
- **barrier/element 处理保持 in-line 不变**（不延迟、不改序）。

### 3.5 abort 协作式 cancel（替代仅靠 interrupt 上传）

```
abort handler (timeoutScheduler thread):
    for each task:
        invokable.getMailboxExecutor().signalCancel();   // 置 flag + CONTROL marker mail
        task.cancel();  // CAS RUNNING→CANCELING + Thread.interrupt() 解除阻塞 read

task main loop (middle/sink processInputGate / source run wrapper):
    top of loop: if (mailboxExecutor.isCancelled()) exit gracefully;
```

- `Thread.interrupt()` **保留**：作为解除阻塞 `InputGate.read()` / source 阻塞 I/O 的手段（无现成非阻塞 read）。
- **新增**：cancel flag/mail 在主循环顶检查，使退出为受控优雅退出，而非仅靠 `InterruptedException` 上传。两者协同：interrupt 解除阻塞 → 线程醒来 → 循环顶看到 cancel flag → 退出。

## 4. `synchronized` 处置裁定

`CheckpointBarrierTracker` 的三处 `synchronized`（`triggerCheckpoint`:60、`acknowledgeOperator`:102、`notifyCheckpointAborted`:183）的处置：

- **source 路程经 mail 串行化**：trigger-checkpoint mail 在 task 线程单线程执行，source 端不再有 injector 线程与 task 线程对 `currentCheckpointId`/`operatorsToAck` 的并发。
- **middle/sink trigger 仍为 injector 线程同步**：与 task 线程的 `acknowledgeOperator` 仍有并发。
- **裁定**：`synchronized` **保留**为跨线程不变量的保护。source 路程的串行化只是消除了 source 端的 injector/task 竞争，并未消除 middle/sink 端的 injector/task 竞争。移除 `synchronized` 会破坏 middle/sink 同步 trigger 的线程安全。未来若 full-mailbox 化（middle/sink trigger 也改 mail），需重构 priming，届时 `synchronized` 可裁剪。

## 5. ACK 越界缺陷处置

`TestCheckpointBarrierTrackerConcurrency.testExtraAckTriggersCallbackAgainKnownIssue`（119-142）记录的缺陷：多余 ACK 使 `operatorsToAck` 变负，再次触发回调。

**source 路程串行化 + middle/sink 同步不变**后，该越界应不再可能于正常 checkpoint 流程出现。`acknowledgeOperator` 已有 `operatorsToAck.get() <= 0` 的早退保护（110-113），多余 ACK 被安全忽略/拒绝（不计入、不触发回调）。该 known-issue 测试转为**正向断言**：多余 ACK 被安全忽略，回调不再被重复触发。

## 6. 反空壳约束

mailbox 原语与接线分支：
- 未实现的分支必须抛 `UnsupportedOperationException`，不得空方法体/静默跳过。
- `Mail.run()` 执行真实闭包；`TaskMailbox.put/poll/take` 有完整队列逻辑；`MailboxExecutor.processAvailableMails()/runLoop()` 有完整 drain 逻辑。
- cancel flag 是真实可观测的 boolean（`volatile`），不是空操作。

## 7. 奠基（未接线）

mailbox 原语为以下后续工作奠基，但本设计**不接线**：

- **processing-time timer 生产接线**：timer 触发需作为 mail 投递到 task 线程（当前生产不触发）。`MailboxExecutor.runLoop()` 与 `TaskMailbox` 已具备承载能力。
- **异步 snapshot（Stage 18）**：异步 snapshot 完成回调需作为 mail 回到 task 线程更新状态。
- **full-mailbox 化**（middle/sink trigger 也改 mail）：需先重构 `processBarrier` 使其能从 in-band barrier 自 prime，或引入显式 priming 同步——属更大范围改造，独立 successor plan。
