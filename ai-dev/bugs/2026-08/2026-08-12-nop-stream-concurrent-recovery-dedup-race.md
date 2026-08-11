# 2026-08-12 nop-stream 并发恢复去重竞态（TestJobCoordinatorRecoveryConcurrency flaky）

## Problem

`TestJobCoordinatorRecoveryConcurrency.concurrentGlobalRecovery_serializesToOneRotation`
偶现失败：

```
concurrent globalRecovery must bump restartCount exactly once, got 2
==> expected: <1> but was: <2>
```

测试用 `startLatch` 同时释放两个 driver 线程，每个线程调一次 `globalRecovery()`，
断言只产生一次恢复（`restartCount == 1`、`recoveryGen` 增量 1、`assignTask` 计数 2）。
偶发得到 `restartCount == 2`，即两个 driver 都跑完了完整恢复。

## Diagnostic Method

1. 读 `JobCoordinator.globalRecovery()` 的"短路守卫"：
   ```java
   long epochAtEntry = fencingEpoch.get();   // 锁外快照
   recoveryLock.lock();
   try {
       if (fencingEpoch.get() != epochAtEntry) return;  // 守卫
       restartCount.incrementAndGet();
       ...rotate epoch...
   }
   ```
2. 推演所有合法调度顺序，发现守卫**非确定性**：测试注释声称"loser
   deterministically observes the pre-rotation epoch"，但 `startLatch` 只能保证
   两线程"同时被唤醒"，**无法保证两个线程都在获胜者轮转 epoch 之前完成快照**。
3. 失败时序（合法调度）：
   - t1：Driver A 被调度，快照 `epochAtEntry=E0`，`lock()` 获取
   - t2：A `restartCount++`(=1)，轮转 `E0→E1`，`unlock()`
   - t3：Driver B 被调度（调度延迟），快照 `epochAtEntry=E1`（A 已做完），`lock()` 获取
   - t4：守卫 `E1 != E1` → false，**不短路**
   - t5：B `restartCount++`(=2)，轮转 `E1→E2`
   
   即"背靠背"场景：loser 在 winner 完成临界区后才快照，快照值与当前 epoch 一致，
   守卫失效。

## Root Cause

`globalRecovery()` 的 epoch 快照守卫只能在两个调用**真正重叠**（loser 在 winner 持锁
期间等待）时去重；无法去重"winner 刚释放锁、loser 紧接着进入"的背靠背调用。该限制是
**结构性的**——单一时间点快照无法表达"自上次恢复以来没有新故障"这一语义。

辅助验证：`-Dtest=TestJobCoordinatorRecoveryConcurrency` 单次运行几乎不失败；但加一个
50 次迭代的 stress 变种（每次新建 coordinator），在 nop-stream-runtime 全量测试中稳定
在第 ~38 次迭代失败——证实了非确定性窗口在生产测试负载下会被命中。

## Fix

**两层修复：生产代码做触发边界 CAS 去重，测试用阻塞 mock 消除调度依赖。**

### 生产代码（`JobCoordinator.java`）

1. 新增字段 `recoveryPending: AtomicBoolean`。
2. 新增公共方法 `requestRecovery()`：`compareAndSet(false, true)`——唯一赢家进入
   `globalRecovery()`，冗余调用方 CAS 失败后带 WARN 短路返回。
3. `globalRecovery()` 在 `finally` 块（持锁）末尾 `recoveryPending.set(false)`——
   关键设计点：**在末尾清，不是在开头清**。在开头清会打开一个竞态窗口（第二个调用方
   的 CAS 可在"globalRecovery 已清 pending"与"globalRecovery 未完成"之间成功，排队
   第二次恢复）；末尾清让 CAS 窗口在恢复**整个持续期间**保持关闭。
4. 移除原 epoch 快照守卫（`epochAtEntry`）——其语义已被 CAS 完全覆盖，且它是 flaky 源头。
5. 两个生产触发点（`detectFailures`、`reportTaskStatus` FAILED 路径）改调
   `requestRecovery()` 而不是 `globalRecovery()`。
6. `globalRecovery()` 保持公共、契约不变（总是执行完整恢复）——这保留了 7+ 个直接调用
   `globalRecovery()` 的测试（`TestJobCoordinatorRestartStrategy`、
   `TestFencingEpochUnification`、`TestJobCoordinatorAttemptTracking` 等）的现有契约；
   去重责任明确归于调用方（`requestRecovery`）。

**为什么 CAS 比快照守卫好**：快照守卫的正确性依赖"loser 在 winner 轮转 epoch 之前快照"
这一时序假设，JVM 调度器不保证；CAS 在**触发边界**原子决定赢家，与后续调度无关。

**残留语义（已记录在字段/方法文档）**：CAS 去重对**重叠并发**完全确定性；对"背靠背"
（B 在 A 完全结束后触发）不去重——但 `globalRecovery` 是全局幂等的，冗余触发只是浪费
不致破坏（`restartCount` 多增一次、epoch 多转一次），且故障检测器的周期性保证了真实
故障最终被处理。

### 测试代码（`TestJobCoordinatorRecoveryConcurrency.java`）

仅靠 CAS 不足以让 `startLatch` 式测试确定性——mock 都是 no-op，`globalRecovery` 在
微秒内跑完，调度器仍可能把两个 driver 串行化，使第二个 CAS 观察到已清的 flag。

解决方案：`RecordingClusterRegistry` 增加**阻塞门**（两个 `CountDownLatch`）：

- `assignEnteredGate`：`assignTask` 首次进入时 `countDown`（信号"我已进入恢复中段"）。
- `releaseAssignGate`：`assignTask` 在返回前 `await`（阻塞直到测试放行）。

关键事实：`clusterRegistry.assignTask` 在 `prepareAssignmentsLocked()` 内被调用，后者在
`recoveryLock` 下执行。因此让 `assignTask` 阻塞 ⇔ driver A 持锁停在恢复中段 ⇔
`recoveryPending` 仍为 true。

重写后的 `concurrentGlobalRecovery_serializesToOneRotation` 流程：
1. 装 gate（在初始 `assignTasks()` 之后，只门控恢复阶段的 `assignTask`）。
2. 提交 driver A；`await(assignEntered)` 直到 A 确定停在恢复中段。
3. 提交 driver B；`await(bDone, 5s)`——B 必须 CAS 失败立即短路返回。若去重回归（B 进了
   `globalRecovery`），B 会阻塞在 `recoveryLock`（被 A 持有），5 秒超时 → 确定性失败。
4. `releaseAssign.countDown()` 放行 A，等 A 完成。
5. 断言 `restartCount==1`、`recoveryGen` 增量 1、`assignTask` 计数 2、无重复 subtask key。

`concurrentRecovery_leavesConsistentWorkingSet` 保留 `startLatch` 式（其断言对去重结果
不敏感：无论一次还是两次恢复，最终 working set 都一致），文档注明它是"接线一致性"测试
而非"去重"测试。

删除 50 次迭代的 stress 变种——确定性测试无需重复。

## Tests

- `concurrentGlobalRecovery_serializesToOneRotation`（重写为阻塞门确定性版本）——连续 5
  次单跑 + 1 次模块全量均通过，不再 flaky。
- `concurrentRecovery_leavesConsistentWorkingSet`——`startLatch` 版本，断言对去重不敏感。
- 全模块回归：`./mvnw test -pl nop-stream/nop-stream-runtime` →
  `Tests run: 765, Failures: 0, Errors: 0, Skipped: 8` → BUILD SUCCESS。
- 直接调用 `globalRecovery()` 的 7+ 个现有测试（restart-strategy / fencing-epoch /
  attempt-tracking / failover-restore / standby-state-machine / remote-deploy）全部通过，
  证明 `globalRecovery` 契约未改。

## Affected Files

- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java`
  - 新增 `recoveryPending` 字段、`requestRecovery()` 方法
  - `globalRecovery()` 移除 epoch 快照守卫，`finally` 末尾清 `recoveryPending`
  - `detectFailures` / `reportTaskStatus` FAILED 路径改调 `requestRecovery()`
- `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/coordinator/TestJobCoordinatorRecoveryConcurrency.java`
  - 重写为阻塞门确定性测试；`RecordingClusterRegistry` 增加 `installBlockGate`

## Notes For Future Refactors

- **并发测试不要依赖 `startLatch` + "希望两个线程重叠"**：no-op mock 下被测方法常在
  微秒级完成，调度器极易串行化两个 driver。要么用阻塞门/CountDownLatch 在被测代码内
  钉住一个线程的执行点，要么用 `@RepeatedTest` 配合真实工作负载——前者更可靠。
- **去重责任分层**：`globalRecovery()` 是"总是执行"的原语（保留给测试/管理路径直接调），
  `requestRecovery()` 是"带去重的生产触发入口"。新增触发点应调 `requestRecovery()`，
  不要直接调 `globalRecovery()`。
- **CAS flag 的清理位置**：在临界区**末尾**（finally 内）清，不要在开头清——开头清会
  重新打开让第二个 CAS 成功的窗口。这条规则对一切"CAS flag + 锁内工作"的去重模式通用。
- 当前去重对"背靠背"（B 在 A 完全结束后触发）不去重。若未来需要更强语义（per-failure-event
  恰好一次恢复），需要引入"故障观测序号"或在观测点（而非触发点）置位 flag——这是更大的
  设计变更，超出本次修复范围。

---

## Addendum: TestAsyncSnapshotPipeline flaky（构建验证时发现，独立 bug）

### Problem

上述去重修复完成后跑全模块构建（`./mvnw test -pl nop-stream/nop-stream-runtime`），
`TestAsyncSnapshotPipeline.testTimeoutAbortDuringInFlightPersistIsNoOpThenCompletes:306`
偶现 `expected: <0> but was: <1>`，阻塞 reactor。隔离单跑 5/5 通过——典型的负载相关
flaky test。该测试完全不引用 `JobCoordinator`，与本次去重修复无关，是独立的异步时序 bug。

### Root Cause

`CheckpointCoordinator.onCompletePersistSuccess`（持 `synchronized(this)`）的顺序：
1. `pendingCheckpoints.remove`（line 775）
2. `pending.forceComplete()`（line 782）—— **完成 CompletableFuture**
3. `latestCompletedCheckpoint` / `lastCompletedTimestamp`（line 784/788）
4. `decrementPendingCheckpointCount()`（line 789）—— **递减 AtomicInteger 计数**

而 `getNumberOfPendingCheckpoints()`（line 944）是**非 synchronized** 的
`AtomicInteger.get()`。

测试线程：`future.get()` 在 forceComplete（步骤 2）时返回 → 立即读计数。但 decrement
（步骤 4）在 forceComplete **之后**，且是**不同的 volatile 字段**——JMM 不保证测试线程
（未经 monitor 同步）立刻看到步骤 4 的写。全模块负载下这个窗口可观测，计数读到 1。

失败路径 `onCompletePersistFailure` 同理：`status=FAILED`（line 826）先于
`decrementPendingCheckpointCount`（line 828），测试 busy-wait 到 FAILED 即读计数也有
竞态（`TestAsyncSnapshotPipeline:390`，尚未被命中但理论 racy）。

### Fix

**只改测试，不改生产顺序。** 生产顺序是有意的——计数不能在 checkpoint 标记 durable/failed
**之前**归零，否则一个 racing trigger 可能在 checkpoint 尚未真正完成时看到 `numPending==0`
而过早触发新 checkpoint（参见 `onCompletePersistSuccess` line 786-788 的注释：
"Set [lastCompletedTimestamp] before decrement so a racing trigger sees the new anchor
when numPending drops to 0"）。

`TestAsyncSnapshotPipeline` 新增 `awaitPendingCount(coord, expected, timeoutMs)` 忙等辅助
（10ms 轮询、5s 超时），替换三处即时 `assertEquals(0, getNumberOfPendingCheckpoints())`
（原 line 306/358/390）。忙等让 volatile decrement 写最终对测试线程可见，无需改生产代码。
这与 `ai-dev/bugs/2026-07/2026-07-18-test-race-condition-fix.md` 的修法一致（忙等替代
即时断言 / 固定 sleep）。

### Tests

- `TestAsyncSnapshotPipeline`（12 tests）隔离 3/3 通过。
- 全模块连续 3 次 `Tests run: 765, Failures: 0, Errors: 0, Skipped: 8` → BUILD SUCCESS，
  flaky 消除。

### Affected Files (Addendum)

- `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/checkpoint/TestAsyncSnapshotPipeline.java`
  - 新增 `awaitPendingCount` 忙等辅助
  - 三处 `assertEquals(0, getNumberOfPendingCheckpoints())` 改用忙等

### Notes For Future Refactors (Addendum)

- **异步完成回调里"完成 future"与"递减计数"之间的可见性窗口**是这一类 flaky 的通用模式：
  future 的 `complete()` 只保证 complete **之前**的写对 `get()` 返回后的读可见，complete
  **之后**的写（即使同一线程、同一 synchronized 块）对未同步的读者不保证可见。若测试
  依赖"future 完成后立即读副作用计数"，必须用忙等或额外同步点，不能即时断言。
- `getNumberOfPendingCheckpoints()` 是否应改成 `synchronized` 是一个开放问题——当前选择
  保持非同步（读路径无锁、性能优先），由调用方/测试负责可见性。
