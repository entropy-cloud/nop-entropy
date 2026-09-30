# 深审计 R4 第 0 号报告：plan 366 修复提交回归审计

> Date: 2026-09-30
> Auditor: 独立回归审计子代理（R4 轮，plan 366 收口后）
> Objects: 3 个提交——`5e33afea72`（Phase 2 缺陷修复 9 项）、`65af207b07`（Phase 3 可读性/结构整改，行为保持）、`3051805cc6`（Phase 4 性能留舍：P2 文件源缓冲化 + P4 NFA 惰性分配保留）
> Method: `git show` 逐 hunk 审读 + live code 逐路径核对 + 零引用 grep 全仓复核 + 配套测试变异式静态推演。不修改任何代码。
> Baseline: HEAD = 66eaa9ae91（三个被审提交之后无 nop-stream 代码变更介入，live code 即提交后状态）

## Summary

| 严重程度 | 数量 | 编号 |
|---|---|---|
| P0 | 0 | — |
| P1 | 1 | R5-REG-01 |
| P2 | 1 | R5-REG-02 |
| P3 | 5 | R5-REG-03 ~ R5-REG-07 |

Phase 2 的 9 项修复（R4-N1/N2/N3/N4/N5/A2'/B6'/S1/S2 + 执行期新增 F1）**全部真实落地且语义正确**，无一项回退。Phase 3 行为保持声明**抽查全部通过**（删除符号全仓零引用、守卫字符串保持、LATE_ELEMENTS 接线为真实丢弃路径）。Phase 4 文件源缓冲化**逐字节语义保持**。已登记的"回放窗口竞态"先存缝隙**未被本轮扩大**（论证见 §3.1）。

唯一 P1（R5-REG-01）是四案例矩阵自身的语义缺口：F1 COMPLETED 跳过 × 无物化内部边 × checkpoint 状态回滚的组合，会把"检查点保证本应覆盖的记录窗口"静默截断——修复前该场景是可检测的悬挂（P0），修复后变为不可检测的错误输出。该组合是 plan 366 R2 复审 F1 专项引入并裁定通过的案例，属于**修复方案引入的新暴露面**（非实现笔误），建议 owner 裁定失败语义（响亮失败 or 扩展回放源）。

---

## 1. Phase 2 逐项修复验证（9 项 + F1）

### 1.1 R4-N1 attachPendingReplay 惰性回放 — PASS

`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/ResultPartition.java:57-118, 368-414, 514-536`

- 回放不再经 `injectFront` 阻塞 `queue.put`（该方法已整体删除，全仓 grep 0 命中）；`attachPendingReplay` O(1) 挂 `volatile ConcurrentLinkedQueue`。
- 顺序不变式 pending → queue → EOS 在**双 read 重载**（`read()` :368-381、`read(timeout)` :397-414）均先 poll pending；gate 实际消费路径 `InputGate.readSingleChannel:584` / `readMultiChannel:716` 均走 timeout 重载，无漏。
- `drainBufferedElements`:514-536 先排空 pending 段再排 queue，与 unaligned 捕获（`InputChannel.captureInFlightData:112-114`）的交互正确：pending 未排空期被捕获进 channel state，恢复时不丢（`TestResultPartitionPendingReplay#captureIncludesPendingReplayAndKeepsSentinel` 钉死）。
- permit 记账：pending 段零 acquire/release，queue 段维持 write-acquire/read-release（`replayPathHoldsNoPoolPermits` 断言守恒）。N5 许可泄漏随 `injectFront` 删除一并消除。
- 阻塞版 `read()`（`queue.take()`）在"分区已 finished 但 queue 被 drain 掏空"时会永久阻塞——live 代码中 gate 全部走 timeout 重载，`RecordReader.read()`（阻塞版唯一非测试调用方形态）在 main 代码零调用，不构成可达悬挂（见 §2.3 的注释漂移发现）。
- RemoteInputChannel 排除核实：`RemoteInputChannel.injectElements`（:450-486）保留自有 local-queue 注入路径（含哨兵重置），不路由经 pendingReplay。✓

### 1.2 R4-N2 内部边复用旧分区 + MIDDLE writer 保留 — PASS（附 R5-REG-01 语义缺口）

`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/SupervisionLoop.java:793-857`

- `matPoint == null` 通道一律 `consumerPartition = oldPartition`，删除"全新空分区"分支——内部边不再被切。
- MIDDLE writer 保留：`oldInvokable.getFanOutWriters()` 非空走 3 参（chain, fanOutWriters, gate）构造器（`StreamTaskInvokable.java:220-233`，`wireOperators(fanOutWriters)` 把新 chain 尾接旧 writer），否则回退单 writer 构造器。纯 SINK（双 null）角色判定不受影响（`getRole()` :235-244）。
- **分区 seal 时机核实**（本项安全性的根基）：失败/取消路径**不** close 输出 partition——`StreamTaskInvokable.invokeSource` finally :698-706 与 `invokeMiddle` finally :769-773 的注释与代码一致（仅 `inputError == null && exitReason == END_OF_STREAM` 才 `closeOutputWriters()`），失败任务的旧 writer/旧分区保持可写。✓ 复用旧 writer 不会命中 `ERR_STREAM_INVALID_STATE`。
- 四案例矩阵逐案例核对：
  - mat + running：drain → attach replay → 消费 pending 后读 live queue ✓；
  - mat + finished：drain（残留 ⊆ replay 集合）→ attach → pending 耗尽后 gate 经 `isFinished()`+null 判 EOS ✓（`readSingleChannel:586` / `readMultiChannel:717-721, 756-758` 双路均以 `channel.isFinished()` 消歧 timeout-null，drain 移除哨兵不影响终止语义）；
  - no-mat + running：不 drain，重建 producer 重发 [N+1..now] + 残留 → 完整（at-least-once 重复）✓；
  - no-mat + finished：不 drain，residual + EOS as-is —— **在无 checkpoint 时正确**（e2e 测试注释明示 r1 丢失为"inherent in-flight loss without checkpoint"），**但有 checkpoint 时存在静默截断窗口** → 见 R5-REG-01。

### 1.3 F1 restartRegion 跳过 COMPLETED — PASS

`SupervisionLoop.java:498-510`

跳过分支在 `rebuildTask` 之前，COMPLETED 任务的 finished 分区保留 residual + sentinel 供重建下游消费；FAILED/CANCELED（`isFinished()` 为真但非 COMPLETED）照常重建。`TestRegionRestartInternalEdgeE2E#internalEdge_restartAfterProducerCompleted_skipsCompletedAndDrainsResidual` 用 `assertSame` 钉死实例同一性。删除该跳过 → 重投递首写 finished 分区抛错/超时 → 测试必失败。✓ 但该跳过正是 R5-REG-01 的触发前提。

### 1.4 R4-N3 心跳过滤 finished 任务 — PASS（附 R5-REG-02 残余竞态）

`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:318-328`

- `RunningTask.isFinished()`（volatile，`RunningTask.java:62,127,329-331`）在 finally 对全部终态置位；FAILED/CANCELED 条目在同一 finally 自移除（:161-170），心跳跳过对它们无额外影响；SUCCESS 保留条目（尾段 2PC 提交依赖），心跳停止回插——确定性路径修复正确。
- **尾段 2PC 提交窗口交互**：提交通知（`RunningTask.notifyCheckpointComplete` :293-322）与 barrier 注入（`triggerCheckpoint` :267-284）由 coordinator RPC 驱动，不经心跳循环，跳过不影响；`TaskManager.getRunningTaskCount`（:865-876）排除 finished 与完成检测器兼容。✓
- `TestTaskManagerLivenessAndReporting#heartbeatSkipsFinishedTaskWhoseRegistryEntryIsRetained`（:158-193）断言 `livenessBatches` 全批无 v-done——删除 skip 后必失败。✓

### 1.5 R4-N4 JDBC 目录查询响亮失败 — PASS

`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/JdbcCheckpointStorage.java:409-417, 577-585`

catch Exception → typed `CheckpointStorageException(ERR_STREAM_CHECKPOINT_ERROR)`（该类 extends `StreamException`），外层方法体的 `catch (NopException e) { throw e; }`（:142-143 等 16 个调用点同构）原样穿透——restore 路径（`getLatestCheckpoint:123`、`loadLatestEpochManifest:551/606`）与写路径全部响亮失败，无静默冷启动。`TestJdbcCheckpointStorage#testRestorePathFailsLoudWhenCatalogQueryFails`（:672-698）双路断言，回退为 debug+false 后 assertThrows 必失败。✓

### 1.6 R4-N5 injectFront 删除 — PASS

全仓（含测试）grep `injectFront` 0 命中；permit 守恒由 `replayPathHoldsNoPoolPermits` 断言（attach 前后 availablePermits 不变、交付不 release、queue 读如数归还）。✓

### 1.7 R4-A2' fail 路径 checkpointSuccessMap 清理 — PASS

`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:869-878`

与 abort 路径（:928-936）逐行同构；`if (!failedCommitParticipants.containsKey(checkpointId))` 的保留条件与 `retryFailedCommits`（:1225-1256，`getOrDefault(failedEpoch, true)` 依赖 marker）语义吻合——有失败参与者时保留 success=false 防止重试把失败 epoch 升格为 committed。`TestCheckpointAbortMarkerCleanup#persistFailureDropsTerminalMarkerWhenNoCommitFailed`（:145-198）经真实完成路径（sync 模式 + 恒败 storage + coordinator acknowledge）驱动 `onCompletePersistFailure`，删除清理行后 assertFalse 必失败。✓

### 1.8 R4-B6' directoryPath 保留字符校验 — PASS

`nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileSource.java:178-186`

仅拒 `\n`/`\r`（indexOf 双查），`|`/`,` 合法通过——与 split 路径的 `"|\n\r"`、CSV 节的 `",\n\r"` 集合刻意差异已注释说明。deserialize 侧 `lines[0]` 解析与之闭环。✓

### 1.9 R4-S1 扁平 suppressed 树 — PASS

`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/CloseSupport.java:94-105`

`accumulate` 把 `error` 及其 suppressed 全部挂到 firstError；javadoc 钉死形状契约并如实声明双路径可见（原始嵌套不可移除）。`TestCloseSupport#accumulateFlattensCloseAllSuppressionChain` 断言 `[X, Y]` 顺序——去掉 hoist 循环后长度断言必失败。✓

### 1.10 R4-S2 recoveryPending 注释归真 — PASS

`JobCoordinator.java:1497-1511`（内层 finally 仅释放锁）、:1532-1543（外层 finally 才是 `recoveryPending.set(false)` 清除点）——注释与 live 控制流一致（grep 实测清除语句仅外层 finally 一处）。✓

---

## 2. 发现清单

### [R5-REG-01] F1 COMPLETED 跳过 × 无物化内部边 × checkpoint 回滚：静默丢失 (N, M] 记录窗口

**文件**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/SupervisionLoop.java:498-510`（F1 跳过）、`:807-815, 833-837`（no-mat 通道复用不 drain、无回放源）、`:711-760`（operator state 回滚到 N，且 region 重启路径**不**恢复 channel state）

**证据片段**（:807-815）：

```java
            // Always reuse the old partition. The rebuilt producer-side task
            // reuses the OLD output writer (writes to the OLD partition), so a
            // freshly created consumer partition would sever a same-region
            // internal edge forever ...
            ResultPartition consumerPartition = oldPartition;
            if (matPoint != null) {
```

```java
            // matPoint == null (internal same-region edge, or a legacy
            // non-materialization channel): reuse the partition as-is. Residual
            // in-flight data and a queued EOS sentinel (finished producer) are
            // delivered as-is — at-least-once duplicates are acceptable.
            newChannels.add(new InputChannel(consumerPartition));
```

及测试自述（`TestRegionRestartInternalEdgeE2E.java:73-76`）："Record r1 is consumed by the failed map attempt ... the inherent in-flight loss of a non-materialized edge **without a checkpoint**"——丢失声明显式限定在无 checkpoint 场景。

**严重程度**：P1（有 checkpoint 时的静默数据丢失/错误输出；触发面窄但后果不可检测）

**现状**：四案例矩阵的 no-mat + finished 象限在 plan 366（R2 复审 F1 折入）被裁定为"复用分区不 drain，consumer 读残留 + EOS"并通过 e2e 钉死。但矩阵未分析 **checkpoint 状态回滚与该象限的交互**：

1. 作业有已完成 checkpoint N；consumer（如 MIDDLE/SINK）在 N 之后消费到 M（N < M）后发生瞬态失败；
2. region 重启把 consumer 的 operator state 回滚到 N（`resolveConsistentCutEpochAndRestoreOperators` :719-753），region 重启路径不恢复 channel state（`rebuildTask` 只调 `restoreOperatorsFromState`，无 `restoreChannelStateIfPresent`）；
3. 同 region 的 producer 若仍在运行/FAILED，会被重建并从 N 重发 [N+1..now]，覆盖丢失窗口（含残留在内的重复，at-least-once 成立）；
4. **但 producer 已 COMPLETED 时（F1 跳过，:498-510），无任何重发源**：no-mat 内部边无物化 store，checkpoint N 的 channel state 又被忽略 → consumer 只读到残留 [M+1..end] + EOS → 记录 (N, M] 永久缺失，作业正常 COMPLETED，**输出静默截断**。

修复前同一场景的形态是 N2 的 P0 悬挂（fresh 空分区永不产数据）——可检测；修复后变为不可检测的错误结果，属于修复方案对该象限的**语义替换引入的新暴露面**。

**风险**：有界作业 + poison/瞬态失败 + 失败点位于最后 checkpoint 与 producer 完成之间 + 内部边拓扑 → 下游 2PC sink 提交缺失窗口的数据且无任何告警。unbounded 作业不受影响（producer 永不 COMPLETED）；mat 边不受影响（store ≥ N 全覆盖，已验证 replay ≥ N ⊇ residual，含 unaligned 捕获被排空段）。

**建议**：(a) owner 裁定该象限失败语义——最低成本方案是 F1 跳过时若同 region 存在"依赖其重发的 no-mat 下游消费者且存在已完成 checkpoint"，改为响亮失败（`ERR_STREAM_REGION_RESTART_UNSUPPORTED`）而非静默截断；或 (b) region 重启时对 no-mat 内部边补 channel-state 恢复/二次对账（与已登记的回放语义专项合并）。同时把 `TestRegionRestartInternalEdgeE2E` 的丢失声明从"无 checkpoint 固有"改写为区分两种情形。

**信心水平**：高（控制流逐跳核实：状态回滚无 channel-state 恢复、F1 无重发、no-mat 无 store；e2e 测试仅覆盖无 checkpoint 变体）。

**误报排除**：mat 边由 store 覆盖（drain ⊆ store ≥ N 已论证，含 unaligned）；producer 被重建时由重发覆盖；无 checkpoint 时测试已显式接受丢失；`isFinished()`-null EOS 消歧不受影响。

### [R5-REG-02] N3 残余 TOCTOU：在途心跳可在 COMPLETED 清除后回插冻结 liveness → 假 TASK_STALL 仍可达

**文件**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:924-933`（COMPLETED 分支 `subtaskLiveness.remove`）、`:1000-1007`（`reportNodeTaskLiveness` 无条件 `merge(key, ..., Math::max)`——键不存在时**插入**）；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:308-334`（心跳线程扫描 runningTasks → 组批 → RPC，批构建与投递之间存在窗口）

**证据片段**（JobCoordinator.java:1000-1006）：

```java
        for (TaskProgress p : progress) {
            String livenessKey = p.getVertexId() + "/" + p.getSubtaskIndex();
            // Monotonic max via atomic merge: ...
            subtaskLiveness.merge(livenessKey, p.getLastProgressTime(), Math::max);
        }
```

**严重程度**：P2（低概率 × 一次性 × 后果为修复前确定性发生的同一假 TASK_STALL 全局恢复）

**现状**：N3 修复消除了确定性回插（finished 任务不再进入心跳批），但时序 `心跳批构建（任务未 finished，值新鲜）→ 任务完成 → COMPLETED 报告先到并 remove → 心跳 RPC 后到 merge 插入` 仍会把该键复活。coordinator 侧 `reportNodeTaskLiveness` 不查任何"已完成任务"集合，`detectFailures`（:1234-1248）按 `taskAssignmentMap` 全量比对 liveness → 复活的时间戳在 taskTimeoutMs（默认 60s）后触发 TASK_STALL 全局恢复，恰好打断该任务所在尾段 2PC 提交窗口——即 N3 要消除的故障形态的窄窗残余。窗口为亚秒级且每任务完成至多一次。

**风险**：接近自然完成的有界作业在完成瞬间被假 stall 恢复取消；概率低但后果与原 N3 缺陷相同。

**建议**：coordinator 侧补一个 completed-tasks 集合（`reportTaskStatus(COMPLETED)` 记账、global recovery 清空），`reportNodeTaskLiveness` 对该集合的键直接忽略（或 merge 前检查）。一行级防御即可闭合。

**信心水平**：高（merge 语义与扫描-组批-投递三段式时序均为实测代码）。

**误报排除**：FAILED 分支 `put(now)`（:941）是有意的"新鲜基线"契约，非缺陷；benefit-of-the-doubt（无记录不告警）分支本身正确。

### [R5-REG-03] 三处新注释/文档声称"EOS 哨兵留在队列"，实际 drain 永久移除哨兵

**文件**：
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/SupervisionLoop.java:822-824`
- `nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/execution/TestResultPartitionPendingReplay.java:101-103`
- `ai-dev/design/nop-stream/failover-design.md:173`（"finished partition 的 EOS sentinel 在 pending 耗尽后自然到达"）

**证据片段**（SupervisionLoop.java:820-824）：

```java
                // would double-deliver replayed records. The EOS sentinel (if
                // the producer finished) stays in the queue, so the consumer
                // observes replay first, then end-of-stream.
```

对照 `ResultPartition.drainBufferedElements`（:523-529）：

```java
        while ((e = queue.poll()) != null) {
            if (e == END_OF_STREAM) {
                // Do not hand out the sentinel; it is a terminal signal, not data.
                // Stop draining once the sentinel is observed.
                break;
            }
```

**严重程度**：P3

**现状**：drain 对哨兵是 `poll → break`，**不重放回队列**（旧 `injectFront` 会重放，删除后机制改变）。drain 后的 EOS 终止实际由 gate 的 `isFinished()`+null 同形消歧承担（`InputGate.isChannelEndOfStream:660-662`），行为正确、测试通过（该测试末行 `assertNull(partition.read(10ms))` 恰好验证的是 isFinished 路径而非哨兵）。但三处新文本都把机制描述为"哨兵留在队列/自然到达"。

**风险**：误导后续维护——若有人按注释"依赖哨兵存在"写新读路径（例如对已 drain 的 finished 分区用阻塞 `read()`/`queue.take()`，将永久阻塞），或反向"修复"哨兵丢失，都会引入真实缺陷。本轮修复自身不受影响。

**建议**：三处文本改为如实表述："drain 会移除哨兵；EOS 由分区 `finished` 标志 + read-null 在 gate 层消歧交付"。

**信心水平**：高。**误报排除**：`ResultPartition` pendingReplay javadoc 中 "The queue sentinel is never placed in the deque"（:66-69）一句指 pending 队列本身，表述无误，不在本发现内。

### [R5-REG-04] rebuildTask javadoc 仍描述已被 N2 删除的 fresh-partition + seal 机制

**文件**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/SupervisionLoop.java:586-599`

**证据片段**（:586-592）：

```java
     *   <li>For consumer tasks (with an InputGate): a fresh {@link ResultPartition}
     *       sharing the materialization point of the old partition, with
     *       materialization replay activated at the checkpoint-aligned epoch
     *       (post-checkpoint records only). When the producer partition is
     *       finished, the fresh partition is sealed (EOS); otherwise it stays
     *       open for reconnect-to-live-queue.</li>
```

**严重程度**：P3

**现状**：N2 落地后 consumer 一律复用旧分区（`buildConsumerInvokableWithReplay` 的 javadoc :762-792 已正确改写），但同一提交内 `rebuildTask` 的行为清单 bullet 仍逐字保留旧设计（"a fresh ResultPartition ... fresh partition is sealed (EOS)"、"A fresh InputGate/InputChannel pointing to the fresh partition"）。同提交引入的注释漂移，与 plan 366 自身"注释归真/说谎 javadoc 改写"的验收标准相悖。

**风险**：读者按 rebuildTask javadoc 理解重启语义会得出与实现相反的结论（fresh vs 复用），排查 failover 问题时被误导。

**建议**：将该 bullet 改写为"一律复用旧分区（见 buildConsumerInvokableWithReplay）"，与 failover-design.md:173 的最终状态对齐。

**信心水平**：高。**误报排除**：该 bullet 之上描述 operator state 恢复、producer 复用旧 writer 的段落与实现一致，不涉及。

### [R5-REG-05] FileSourceReader.readBuf transient 字段带 inline 初始化器：Java 反序列化路径为 null

**文件**：`nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileSourceReader.java:59-72`；对照 `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/source/SourceReader.java:32`（`interface SourceReader<...> extends Serializable`）

**证据片段**（FileSourceReader.java:70-72）：

```java
    private transient byte[] readBuf = new byte[8192];
    private transient int readBufPos;
    private transient int readBufLimit;
```

**严重程度**：P3（当前 live 路径不可达；契约层面潜伏）

**现状**：`readBuf` 是 Phase 4 新增的 transient 字段且用字段初始化器赋值。类携带 `serialVersionUID` 并实现 `Serializable`（接口契约）；若实例经 Java 反序列化重建（字段初始化器不执行），`readBuf == null` → `readBuffered()`（:272-281）首读 NPE。当前 live 实例仅由 `FileSource.createReader`（FileSource.java:81）本地构造、从不序列化，故不可达；旧实现的 transient 字段（activeSplit/activeReader/activeBytesConsumed）均无初始化器依赖，不存在该差别。

**风险**：任何未来把 reader 对象放进序列化载体的改动（如跨进程 reader 迁移、深拷贝工具）会得到一个首读即 NPE 的实例，且报错点（readBuffered）远离根因。

**建议**：把初始化移入 `openSplit`（与 readBufPos/readBufLimit 的重置同点，:213-214），或在 `readBuffered` 做 `readBuf == null` 惰性初始化。

**信心水平**：高（Java 反序列化语义确定；live 不可达已核实调用点全集）。

**误报排除**：`pollNext`/`snapshotState` 均在构造后的对象上运行；测试直接 new，无反序列化路径。

### [R5-REG-06] plan 366 文本与最终实现漂移：injectElements 迁移 pendingReplay vs plan"显式排除"

**文件**：`ai-dev/plans/366-nop-stream-audit-r4-quality-perf.md:107`（"restore 路径 `injectElements` 维持既有 `injectFront`（有界 capture ≤ 容量，安全），**不迁移到 pendingReplay——显式排除**"）；对照 `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputChannel.java:127-137`

**证据片段**（InputChannel.java:131-136）：

```java
        // Attach as pending replay instead of queue injection: the restore path
        // must never block on the bounded queue (no consumer is running during
        // restore), and captured in-flight sets can exceed the queue capacity.
        // Delivery order (replay ahead of queue content) is preserved by the
        // partition's read paths.
        partition.attachPendingReplay(elements);
```

**严重程度**：P3（文本一致性；实现本身正确且更安全）

**现状**：plan 的 R1 审查约束写明 restore 路径不迁移且断言"有界 capture ≤ 容量"；实际提交（N5 删除 `injectFront` 使迁移成为必然）迁移了该路径，实现注释还明确反驳了"capture ≤ 容量"的前提。plan 收口时未回写该决策变化（closure audit 核对了代码与测试，但 plan 文本行 107 保持原表述）。

**风险**：后续读者以 plan 约束为准绳审查代码会得出"实现违反计划"的误判；或反向以 plan 为据"恢复" injectFront 路径（该 API 已删除，必然编译失败，误伤有限）。

**建议**：在 plan 366 的 Phase 2 执行披露或 Non-Blocking Follow-ups 补一行登记："injectElements 已随 N5 迁移至 pendingReplay（plan 行 107 的'显式排除'被 N5 连带推翻）"。

**信心水平**：高。**误报排除**：plan 行 107 其余约束（全部 read 路径挂 pending、四案例矩阵、RemoteInputChannel 排除）均已满足。

### [R5-REG-07] attachPendingReplay 整体替换语义无防御：二次 attach 会孤儿化旧 pending 未消费段

**文件**：`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/ResultPartition.java:85-96`

**证据片段**（:91-96）：

```java
    public void attachPendingReplay(List<StreamElement> elements) {
        if (elements == null || elements.isEmpty()) {
            return;
        }
        this.pendingReplay = new java.util.concurrent.ConcurrentLinkedQueue<>(elements);
    }
```

**严重程度**：P3（当前调用点不可达；契约锐利边缘）

**现状**：attach 是**替换**而非追加。若同一分区在旧 pending 未排空时被二次 attach，旧队列残余元素被直接丢弃（数据丢失）。当前全仓仅两个调用点：`InputChannel.injectElements`（部署期 restore，fresh 分区）与 `InputChannel.activateMaterializationReplay`（region 重建，且 mat 路径先 `drainBufferedElements`——drain 含 pending 段，旧 pending 已清空；二次重启场景中旧 pending ⊆ store，新 replay 集合超集覆盖）——均不可达孤儿化。`pollPendingReplay` 的 javadoc（:98-105）只论证了"不 null-out 字段"防 swap 丢新队列，未提及替换丢旧队列这一面。

**风险**：未来第三个调用点（如部分重放、单通道重连）若不满足"先 drain 或 fresh 分区"前提，会静默丢回放段，且无断言拦截。

**建议**：javadoc 补一句替换契约（"attach 前调用方必须保证旧 pending 已排空，否则其残余被丢弃"），或在非空替换时 `LOG.warn` 留痕。

**信心水平**：高（两调用点生命周期不相交已逐点核实）。

**误报排除**：同分区两个 mat/非 mat 通道各自持独立 partition，无跨通道 attach；`drainBufferedElements` 与并发 attach 的交错最坏情形是捕获列表混入新 attach 段，属 at-least-once 容忍面。

---

## 3. 专项评估

### 3.1 已登记 follow-up"回放窗口竞态"是否被本轮扩大 — 否

旧实现窗口：`drainBufferedElements`（物化+running 分支）→ `activateMaterializationReplay` 内 `point.replay(epoch)` 快照 → `injectFront`。新实现窗口：同一 drain → 同一 `point.replay` 快照 → 非阻塞 attach。**drain 与快照之间、以及快照之后的投递分界完全相同**，窗口宽度不变；差异仅在注入机制（阻塞 put → O(1) attach），不改变哪些记录落在窗口内。

新增面核查：新代码把 drain 扩展到 mat+finished 象限（旧实现该象限用 fresh 分区不 drain）——该象限 producer 已 finished，无并发写者，窗口内无写入，不产生新的重复/丢失面。no-mat 内部边从 fresh 分区改为残留复用，引入的是"残留 + 重发"的 at-least-once 重复（文档明示接受），不属于该 follow-up 定义的 drain↔snapshot 缝隙。结论：**未扩大**，`TestRegionRestartInternalEdgeE2E` 大回放用例的 at-least-once 断言口径与之自洽。

### 3.2 MIDDLE writer 保留 × 分区 seal 时机 — 验证通过

seal（`close()` → EOS 哨兵）仅发生在成功完成路径（`StreamTaskInvokable.java:707-726, 774-784`，`exitReason == END_OF_STREAM` 守卫），失败/取消/中断路径输出分区保持 open——这是 N2 复用旧 writer 安全性的根基，注释、代码、e2e 三方一致。F1 跳过补齐了"COMPLETED 任务不可重写 finished 分区"的对偶约束。唯一遗留即 R5-REG-01。

### 3.3 心跳过滤 isFinished × bounded-run 尾段 2PC 提交窗口 — 验证通过（附 P2 残余）

保留的 registry 条目继续接收 `triggerCheckpoint`/`notifyCheckpointComplete`（RPC 驱动，与心跳循环无关）；`getRunningTaskCount` 排除 finished 与完成检测器兼容；COMPLETED 报告丢失时行为变化（冻结 liveness 60s 后 stall→恢复，向 FAILED 分支契约对齐）已在 plan 登记。残余窄窗竞态见 R5-REG-02。

---

## 4. Phase 3 行为保持抽查 — PASS

| 项 | 结论 | 证据 |
|---|---|---|
| 删除 10 个零引用顶层类 + CheckpointBarrierSignal + StreamConnectors | 全仓（含 nop-stream 外）grep 均 0 命中 | SourceEnumeratorState/VoidNamespaceSerializer/SourceWorkUnit/DynamicSplitRequest/Response/RestrictionTracker/TaskAssignmentMessage/NopCepConstants/RichPatternFlatSelectFunction/RichPatternSelectFunction/CheckpointBarrierSignal/StreamConnectors 全部 0 |
| 单方法死 API 删除 | 0 命中（同名残留均为其他类的无关成员，如 HeapInternalTimerService 私有字段 nextProcessingTimeTimer、其他类的 markFailed/getFailedCount） | 逐符号 grep |
| RemoteResultPartition 心跳删除 | startHeartbeat/sendHeartbeatIfIdle/stopHeartbeat/getHeartbeatIntervalMs 0 命中；CONTROL_HEARTBEAT 常量与 RemoteInputChannel:642 容忍分支按裁定保留 | grep + live |
| TtlContext.sweepExpired / TtlCleanupStrategy.backgroundCleanup | 0 命中；DEFAULT 改单参构造；expiredKeys 保留（RocksDBKeyedStateBackend 在用，与裁定一致） | grep + TtlCleanupStrategy.java:30-33 |
| processWatermarkStatus1/2 删除 | 0 命中；单参 `processWatermarkStatus`（AbstractStreamOperator:446-449）与 processWatermark1/2 活路径保留，IndexedCombinedWatermarkStatus 保留 | grep + live |
| LATE_ELEMENTS_DROPPED 接线 | 两算子均为**真实丢弃路径**：CepOperator.java:798-804（事件时间戳 ≤ watermark 且无侧输出 → 丢弃+计数）；WindowOperator.java:672-680（跳过窗口且超 allowedLateness 且无侧输出 → 丢弃+计数）。进程级聚合语义已在 `docs-for-ai/03-modules/nop-stream.md:54` 显式标注（"进程级聚合"），命名例外已注释 | live + docs |
| deployTask 守卫收敛 | `rejectDeploy`（TaskManager.java:629-633）= 原 `reportDeployFailure + throw` 的逐字合并；错误码/ARG 参数逐条保持（diff 实测 8 个守卫点）；`dJobId/aJobId` 双胞胎合并无语义差 | git diff 对照 |
| TaskCheckpointWiring 缩进 | `git diff -w` 55 行 vs 全量 519 行——与 plan 声明的 53 行（计数口径差 2）吻合，纯空白重构 | git diff -w |
| withLateDataOutputTag 孤儿 | `PatternStreamBuilder.java:107` 保留（零外部调用方）；`lateDataOutputTag` 字段仍经 build() 流入 CepOperator（:166,182 → CepOperator.java:193,290,800-801）——孤儿仅为未用 setter，与 plan 登记一致，非行为缺口 | grep + live |

---

## 5. Phase 4 行为保持验证 — PASS

### 5.1 FileSourceReader 缓冲化（R4-P2）

`git show 3051805cc6` 逐行对照新旧 `readNextLine`：

- 主循环：旧 `while (consumed < cap && (b = read()) != -1)` ≡ 新 `while (consumed < cap) { b = readBuffered(); if (b == -1) break; ... }`——EOF 与 cap 边界处理逐位一致；
- CRLF：`\r` 后 lookahead，`next == '\n'` 双双计数；`next != -1` 且非 `\n` → `unreadBuffered()+consumed--`（lone CR 回退），与旧 PushbackInputStream 单槽回退契约等价；`\r` 恰在 cap 边界时不越过（新旧同形，包括"下一 split 首字节为 \n 产生空行"的既有 quirk）；
- `unreadBuffered` 跨 chunk 补给正确：lookahead 字节必在缓冲内（readBuffered 刚返回它），`readBufPos--` 与 refill 无关；
- `openSplit` 重置 readBufPos/readBufLimit（:213-214），等价旧实现每次 open 重建 Pushback 流——无跨 split 缓冲泄漏；
- 字符集 UTF-8（`out.toString(UTF_8)`）与 EOF 空行返回 null（`out.size() == 0`）逐字保持；close 链等价（旧关 wrapper 连带关 fis，新直关 fis）。
- 潜伏项 R5-REG-05（transient 初始化器）不触 live 路径。

### 5.2 NFA LazyVerdictCache（R4-P4）

`NFA.java:727, 854, 939, 977, 1017-1029, 1033-1050`：全仓仅 get/put 两个触点（`checkFilterCondition`），`condition == null` 短路在触缓存之前；put 发生在 filter 成功返回后（异常不缓存，与 closure audit 结论一致）；覆盖语义与旧 IdentityHashMap 逐点等价（同 (event × partial-match) 生命周期内 Identity 键缓存 Boolean）。P5 双槽缓存确认无残留（该提交 diff 仅 P2/P4 两文件）。

---

## 6. 配套测试回归捕获能力（变异式静态推演）

| 测试 | 钉死的回归 | 变异推演（把核心逻辑改错后是否失败） |
|---|---|---|
| TestResultPartitionPendingReplay（5 测试） | N1 顺序/容量/EOS/permit + injectElements 接线 | pollPendingReplay 恒返 null → assertNotNull 失败；pending/queue 次序颠倒 → 顺序断言失败；replay 路径 acquire/release → permit 守恒断言失败。**能捕获** |
| TestRegionRestartInternalEdgeE2E（3 测试） | N2 内部边复用/F1 跳过/MIDDLE writer 保留/超容量回放 | 删 F1 跳过 → COMPLETED source 重提交写 finished 分区 → 异常/超时失败；回退 fresh 分支 → map 永久阻塞 → @Timeout(60) 失败；writer 不保留 → sink 断言 2 条失败。**能捕获**（但仅覆盖无 checkpoint 变体，见 R5-REG-01） |
| TestTaskManagerLivenessAndReporting（N3 用例） | finished 任务心跳跳过 | 删 isFinished skip → livenessBatches 含 v-done → noneMatch 失败。**能捕获**（TOCTOU 窗口无测试覆盖，属固有限制） |
| TestJdbcCheckpointStorage（N4 用例） | 目录查询失败响亮 | 回退 debug+false → getLatestCheckpoint 返 null → assertThrows 失败。**能捕获** |
| TestCheckpointAbortMarkerCleanup（A2'/abort 双用例） | fail/abort 路径 marker 清理 + 重试期保留 | 删 A2' 清理 → persistFailure 用例 assertFalse 失败；改成无条件清理 → abortKeepsMarker 用例 assertTrue 失败。**能捕获** |
| TestCloseSupport（3 测试） | 扁平树形状 | 删 hoist 循环 → suppressed 长度/顺序断言失败。**能捕获** |
| TestFileSourceAuditFixes | B6' 序列化校验 + cursor 语义 | 拒绝集合误加 `|` → 合法目录用例失败。**能捕获** |
| TestCepOperatorLateRecordsDroppedMetric / TestWindowOperatorLateRecordsDroppedMetric | delta=1 接线 | 断开 increment 或改注册名 → find(...) 缺失/无 delta 失败。**能捕获** |

总体：Phase 2/3/4 新增配套测试均为真断言、且对各自修复点的"改错"敏感；覆盖缺口仅在 R5-REG-01（checkpoint × finished-producer 组合无测试）与 R5-REG-02（时序窗口不可静态构造，可接受）。

---

## 7. 结论

plan 366 三个提交的修复与整改**真实、正确、可验证**，R3 审计的 2 P0 悬挂确实消除且回归测试到位；行为保持与性能改动均通过逐字节/逐语义核对。回归暴露面集中于一处（R5-REG-01）：四案例矩阵在"有 checkpoint + no-mat 内部边 + producer 已 COMPLETED"组合下把可检测的悬挂换成了不可检测的输出截断，建议随 replay 语义专项一并由 owner 裁定失败语义；其余为注释/文本归真残留与两处低概率防御缺口。

**Follow-up 建议（非阻塞）**：
1. R5-REG-01 → 并入 replay 语义专项（新增 owner 裁定点：内部边 + COMPLETED producer + 有 checkpoint 的响亮失败或对账）；
2. R5-REG-02 → coordinator 侧 completed-set 防御（一行级）；
3. R5-REG-03/04/06 → 下次触碰对应文件时随手归真（文本三处 + plan 登记一行）；
4. R5-REG-05/07 → FileSourceReader/NFA 或 ResultPartition 下次触碰时顺手加固。
