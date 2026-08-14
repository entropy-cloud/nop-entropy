> Audit Status: planned
> Audit Type: open-ended
> Mission: nop-stream-invariant-loop
> Processed: 2026-08-13 — AR-01 → plan `ai-dev/plans/2026-08-13-1930-2-nop-stream-liveness-stall-detection-fix.md`；AR-02~07（P2）→ roadmap `## Follow-up Backlog`（`ai-dev/backlog/nop-stream-invariant-loop-roadmap.md`）

# nop-stream 开放式对抗审查报告（2026-08-13 1930 批次）

## 基本信息

- **审核模块**: `nop-stream/` 全模块组（core / runtime / cep / flow / rocksdb / connector / connector-batch / connector-jdbc / connector-debezium / fraud-example）
- **审核日期**: 2026-08-13（live 基线；git log 截至 2b447bf1a，AR-22 修复已落地）
- **方法**: 主 agent 独立逐文件深读（JobCoordinator / TaskManager / CheckpointCoordinator / RpcDistributedExecutor / CepOperator / NFA / SharedBuffer 族 / WindowOperator 关键路径 / connector 族 / fraud pattern），全部发现均经实际代码文本验证；未重跑测试（只读审计）。
- **去重声明**: 已完整阅读 2026-08-12-1217、2026-08-13-0805 open-audit 与同日 1930 multi-audit 报告。0805 的 AR-01/02/03（P0/P1/P1）与 AR-22（P0）已由 plan 1243-1/3、1615-1 修复并复核；backlog 已登记的 P2（AR-04~21、P2-01~21、P1-21-01、P1-09-01、P1-18-02 等）**不重报**。本轮视野集中在历轮盲区自评点名的"执行层 RPC/协调面"（JobCoordinator/TaskManager liveness 与生命周期）与 CEP 恢复契约，以及 1930 multi-audit 未覆盖的区域。

## 发现清单（按优先级）

---

### [AR-01] [P1] G52 per-task liveness 停滞检测把"空闲但健康"的任务与"已正常完成"的任务当作停滞：默认配置下空闲 4 分钟即触发重启上限 → 作业被 FAILED

**P1 判定理由**: 新功能（G52）的行为缺陷：任何合法空闲（无数据流入 >60s）或上游任务已正常完成的流作业，会被停滞检测反复触发 globalRecovery，restartCount 到顶后 failJob——健康作业被误杀；且与两个生产 executor 显式关闭的 FAILED-report 恢复路径（autoRecoverOnFailedReport=false）语义不一致（停滞路径未受该开关门控）。

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:1006-1024,972-1036`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/StreamTaskInvokable.java:105,395-396`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:207-250`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/RpcDistributedExecutor.java:236`、`EmbeddedDistributedExecutor.java:193`
- **证据片段**:
  ```java
  // JobCoordinator.detectFailures —— 停滞检测不区分 idle/completed/stalled
  long cutoff = now - taskTimeoutMs;              // 默认 60s
  if (lastProgress != null && lastProgress < cutoff) { taskStallDetected = true; }
  // 无任何 "任务已 COMPLETED" 或 "任务空闲但存活" 的排除；subtaskLiveness 只在
  // reportNodeTaskLiveness/reportTaskStatus 时更新，COMPLETED 报告也不移除条目
  // StreamTaskInvokable —— liveness 仅在数据处理点更新
  private volatile long lastProgressTime = System.currentTimeMillis();
  // markProgress() 调用点：SOURCE/SELF_CONTAINED 启动、sourceOp.collect()、processInputGate 每元素
  ```
- **严重程度**: P1
- **现状**: 心跳每 5s 上报 `inv.getLastProgressTime()`，而进度只在**有数据**时更新。空闲源（AR-02 审计即点名的"Kafka 式源空闲数小时"）、或混合作业中已正常完成的上游任务（`RunningTask.run()` finally 已将其移出 runningTasks，心跳不再上报，coordinator 侧 assignment+liveness 条目永不清除）→ 60s 后 `taskStallDetected` → `requestRecovery()` → `globalRecovery()`（restartCount++，重新 deployTask 从 checkpoint 重启）→ 新 attempt 再次空闲/完成 → 每 ~60s 一次 recovery → 第 4 次 `newCount(4) > maxRestarts(3)` → `failJob`。**一个健康但空闲的分布式作业默认配置下约 4 分钟被 FAILED**。两个生产 executor（Rpc/Embedded）均设 `autoRecoverOnFailedReport=false`（FAILED 报告不触发恢复），但 `detectFailures` 的停滞路径**不受该开关门控**，且 `reportTaskStatus` 收到 COMPLETED 报告时也不做任何 assignment/liveness 清理。
- **风险**: 生产分布式执行路径（RpcDistributedExecutor 长活 coordinator）：空闲期作业被杀（无数据也报 FAILED）；混合有界/无界作业中已完成 source 每 60s 被重新部署重跑 → 重复发射（checkpoint 关闭时全量重放）。排查困难（日志只显示"Global restart cap exceeded"）。
- **建议**: ① liveness 语义改为"心跳时间戳"（TM 每跳上报 `System.currentTimeMillis()`，与数据进度解耦）或停滞判定排除 COMPLETED 任务（reportTaskStatus 收到 COMPLETED 时清除 assignment/liveness）；② 停滞路径也受 `autoRecoverOnFailedReport` 门控（与 FAILED 路径一致）；③ 补回归测试："空闲任务不触发 recovery"、"完成的上游任务不触发 recovery"（当前仅 `TestJobCoordinatorPerTaskFailure.staleLivenessTriggersRecoveryViaDetectFailures` 覆盖真停滞）。
- **信心水平**: 确定（机制全链源码验证：进度更新点 → 心跳上报 → 停滞判定 → recovery → 重启上限）
- **发现来源视角**: 10x 规模运维者 / 异常路径侦探（AR-02 同族的 coordinator 级变体）

---

### [AR-02] [P2] CEP processing-time 定时器不做 checkpoint 快照：恢复后 `within()` 超时与乱序缓冲队列失去触发源（与 WindowOperator 的 internal-timers 快照不对称）

**P2 判定理由**: 真实缺陷但条件性触发（PT 模式 + checkpoint 恢复）：机制确定、影响面为 PT 模式 CEP 作业，与 0132-3 修复的 event-time 注册表对称性同族的新变体（PT 侧从未被快照）。

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:298-312,433-441,514-520`；对照 `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:560-565`（`putOperatorState("internal-timers", internalTimerService.snapshotTimers())`）
- **证据片段**:
  ```java
  // CepOperator.snapshotState —— 只存 watermark + event-time timer 注册表
  result.putOperatorState(WATERMARK_STATE_NAME, currentWatermark);
  result.putOperatorState(EVENT_TIME_TIMERS_STATE_NAME, new ArrayList<>(registeredEventTimeTimers));
  // registerProcessingTimeTimer 直连 ProcessingTimeService，不在任何快照内
  getProcessingTimeService().registerTimer(time, t -> {...onProcessingTime(t)...});
  ```
- **严重程度**: P2
- **现状**: PT 模式 + 有界乱序 comparator 时，`bufferEvent` 建的队列桶定时器（`timestamp+1`）在恢复后全部丢失；恢复后若无新事件到达，已恢复的 `elementQueueState` 桶**永不 drain**（PT 无 watermark 驱动，只有定时器回调驱动 `onProcessingTime`）。PT 无 comparator 模式下，`processEvent` 注册的 `within()` 超时定时器同样丢失 → 恢复后 partial match 永不过期（状态滞留、无 timeout 告警），直到下一条数据到达才补触发。WindowOperator 快照全部 internal-timers（AR-22 修复对象），CEP 是唯一不快照 PT 定时器的算子——跨算子恢复契约不对称。
- **风险**: PT 模式 CEP 作业 checkpoint 恢复后：缓冲事件静默滞留（数据不再被匹配）、`within()` 超时静默失效；空闲期状态滞留可无界增长。
- **建议**: PT 定时器表纳入 `snapshotState`（按 fire time 存储，恢复时重注册，注意恢复后 fire time 已过期则立即触发一次 drain）；补"PT 模式 + comparator + 存储层 round-trip 恢复后仍能触发"的回归测试（先红后绿）。
- **信心水平**: 确定（机制）；影响条件 = PT 模式 + checkpoint 恢复
- **发现来源视角**: GraphQL 契约考古学家（跨算子恢复契约）/ 异常路径侦探

---

### [AR-03] [P2] `TaskManager.RunningTask.run()` finally 无条件 `runningTasks.remove(key)`：旧 attempt 晚退可把恢复后新部署的 attempt 槽位移出跟踪

**P2 判定理由**: 真实竞态（窄窗口）：书签簿损坏导致新 attempt 失去心跳 liveness、cancelTask 可达性与完成计数——叠加 AR-01 后每轮恢复都可能再制造停滞误判。

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:706-718`（finally 块）、`:426-435`（deployTask 槽位替换）
- **证据片段**:
  ```java
  } finally {
      String key = taskKey(jobId, vertexId, subtaskIndex);
      boolean success = error == null && !canceled;
      completedTasks.put(key, ...);
      runningTasks.remove(key);          // ← 移除的是当前 key 上的任意任务，包括新 attempt
      if (semaphoreReleased.compareAndSet(false, true)) { capacitySemaphore.release(); }
  }
  ```
- **严重程度**: P2
- **现状**: 恢复路径时序：`updateFencingToken`（取消旧 attempt）→ `deployTask`（`runningTasks.put(key, runningTask2)` 替换槽位）→ 旧 attempt 的 `invoke()` 展开 → finally `runningTasks.remove(key)` **移除了 runningTask2**。此后该 subtask：心跳不再上报 liveness（`runningTasks` 遍历）、`cancelTask` 找不到、`getRunningTaskCount` 少计（RpcDistributedExecutor.waitForCompletion 可能提前返回）。窗口 = 旧 invoke 返回与新 deployTask RPC 到达之间；`future.cancel(true)` 中断后旧线程展开需要时间，竞态真实存在。`completedTasks.put(key, ...)` 同样会覆盖新 attempt 的结果记录。
- **风险**: 恢复场景下跟踪丢失 → 停滞误判循环（与 AR-01 叠加）、并发 count 不准、取消失效。
- **建议**: finally 中按对象身份移除（`runningTasks.remove(key, this)`）或先 `runningTasks.computeIfPresent(key, (k, t) -> t == this ? null : t)`；补"旧 attempt 晚退不驱逐新 attempt 槽位"的并发测试。
- **信心水平**: 确定（机制）；触发条件 = 恢复竞态时序
- **发现来源视角**: 组合爆炸测试者

---

### [AR-04] [P2] `JobCoordinator.reportTaskStatus` 日志占位符错位：`attempt={}` 实际打印的是 terminalState

**P2 判定理由**: 诊断日志字段映射错误（6 占位符 6 参数但顺序错位），排障时 attempt 号显示为状态枚举——纯日志缺陷，不涉及行为。

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:894-898`
- **证据片段**:
  ```java
  LOG.info("Task status report: {}/{}/{} attempt={} state={} cause={}",
          report.getVertexId(), report.getSubtaskIndex(), report.getAttemptNumber(),
          report.getTerminalState(),        // ← attempt= 槽位收到 TerminalState
          report.getTerminalState(),        // ← state= 槽位仍是 TerminalState（重复）
          report.getErrorCause());
  ```
- **严重程度**: P2
- **现状**: `report.getAttemptNumber()` 从未出现在日志参数中；`attempt={}` 显示为 `COMPLETED/FAILED`。任务失败排障时 attempt 号缺失、字段重复。
- **风险**: 多 attempt 恢复场景下日志误导（无法区分哪次 attempt 失败）。
- **建议**: 参数改 `report.getAttemptNumber(), report.getTerminalState(), report.getErrorCause()`。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探

---

### [AR-05] [P2] `TaskManager.stop()` javadoc 声称"从 ClusterRegistry 注销"，代码从不注销

**P2 判定理由**: 文档-行为契约漂移（small but real）：节点停机后仍留在 active 列表，直到 15s lease 过期才被检测——lease 机制兜底使影响有限，但 javadoc 承诺与实现不一致且会误导后续开发。

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/taskmanager/TaskManager.java:166-193`
- **证据片段**:
  ```java
  /**
   * Shuts down the thread pool, cancels heartbeats, and unregisters from the ClusterRegistry.
   */
  public void stop() {
      ... heartbeatExecutor.shutdownNow(); taskExecutor.shutdownNow(); ... // 无任何 unregister 调用
  }
  ```
- **严重程度**: P2
- **现状**: `stop()` 无 `clusterRegistry.unregisterNode`（全类 grep 0 命中）；节点停止后 coordinator 的 `getActiveNodes()` 仍含该节点，`detectFailures` 靠 lease 过期（15s）间接收敛——期间新作业/恢复可能把任务分派给已停 TM。
- **风险**: 已停节点短暂接收 assignment（receiveAssignment 因 `running=false` 拒绝）；行为与文档不符难排查。
- **建议**: `stop()` 补 `clusterRegistry.unregisterNode(nodeId)`（如接口存在），否则修正 javadoc。
- **信心水平**: 确定
- **发现来源视角**: 文档-代码一致性侦探

---

### [AR-06] [P2] `CepOperator.numLateRecordsDropped` 死计数器：递增但永不暴露为指标（同名常量在 WindowOperator 同为死引用）

**P2 判定理由**: 死代码/死指标：命名与注释暗示是 Flink 式指标（`LATE_ELEMENTS_DROPPED_METRIC_NAME`），实际 0 消费者——迟到事件丢弃量不可观测，与项目"No-Silent-No-Op"精神不符（丢弃可观测）。

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:106,172,348,509`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:191`
- **证据片段**:
  ```java
  private static final String LATE_ELEMENTS_DROPPED_METRIC_NAME = "numLateRecordsDropped";
  private transient LongAdder numLateRecordsDropped;
  ...
  } else { numLateRecordsDropped.increment(); }   // 全仓唯一写点，无任何读/暴露点
  ```
- **严重程度**: P2
- **现状**: 事件时间 CEP 的迟到事件（无 lateDataOutputTag 时）被静默丢弃并计数，但计数无处可查；WindowOperator 侧同名常量同样无消费者（跨算子同族死引用）。
- **风险**: 迟到丢弃不可观测；后续接入指标系统时两处重复定义易分叉。
- **建议**: 接入指标注册（mirror Flink）或删除死字段；WindowOperator 常量一并清理。
- **信心水平**: 确定（0 消费者 grep 验证）
- **发现来源视角**: 死代码清道夫

---

### [AR-07] [P2] `SharedBufferAccessor` ASF 许可证头文字错误（"NOVICE file"/"Vhe ASF"）

**P2 判定理由**: 许可证头录入错误（copy 时改动原文），属于文档级 polish；不影响行为，但许可证文本不应被改动。

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/SharedBufferAccessor.java:3,6`
- **证据片段**:
  ```
  * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements.  See the NOVICE file
  * ... regarding copyright ownership.  Vhe ASF licenses this file
  ```
- **严重程度**: P2
- **现状**: 标准 ASF 头中 "NOTICE" 误作 "NOVICE"、"The ASF" 误作 "Vhe ASF"（同文件两处）。
- **风险**: 许可证文本被篡改的观感；其他移植文件可能同病（建议 grep 全仓 `NOVICE file`）。
- **建议**: 恢复标准 ASF 头文本。
- **信心水平**: 确定
- **发现来源视角**: 死代码清道夫

---

## 总评

本轮视野聚焦历轮盲区点名的**执行层协调面**与 **CEP 恢复契约**，收获集中在两处：

1. **G52 liveness 停滞检测的"空闲即死亡"缺陷（AR-01，P1）是当前最值得关注的方向**。这与 0805 的 AR-02（PT 定时器空闲期不触发）是同一类缺陷的第三层实例：数据面推进点被当作唯一存活信号，空闲=停滞。AR-02 修的是任务内 timer 触发，liveness 检测则把整个作业在空闲 ~4 分钟后送进 failJob。更糟的是它与 FAILED-report 恢复路径的开关（autoRecoverOnFailedReport=false）不一致——两个生产 executor 都关掉了报告驱动的恢复，却留下了停滞驱动这条没关的门。修复方向（心跳时间戳 vs 数据进度、COMPLETED 任务排除）应沉淀为不变式家族新成员或至少补两条回归测试（"空闲任务不触发 recovery"、"完成的上游任务不触发 recovery"）。
2. **恢复契约的"未同步族"继续出现**：AR-02（CEP PT 定时器从不快照，与 WindowOperator 的 internal-timers 快照不对称）是 0132-3"CEP timer 注册表对称"修复的 PT 侧残余；AR-03（RunningTask finally 驱逐新 attempt 槽位）是恢复路径书签簿的竞态漏洞。两者都不需要结构性重构，但属于"修复族的最新变体"，应纳入下一轮类别清扫。

## 本次审核盲区自评

1. 未重跑测试/门禁（只读审计）；AR-01 的 4 分钟误杀未做 E2E 实证（机制已源码全链闭环，建议留待修复计划先红测试）。
2. RocksDB 增量族（AR-17/18 等 backlog 项）本轮未复核（已登记）。
3. `TwoPhaseCommitSinkFunction`/JDBC sink 的事务提交失败重试路径本轮未深挖（0805 后未变，backlog 覆盖）。
4. 并发类发现（AR-03 竞态）依赖静态时序分析，未做压力实测。
5. `nop-stream-flow` XDSL 面本轮未复查（plan 1243-2 刚收口，1930 multi-audit 已覆盖）。

## 按严重程度分布

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 0 | — |
| P1 | 1 | G52 liveness 停滞检测误杀空闲/已完成任务 → 重启上限 failJob（执行层协调面） |
| P2 | 6 | CEP PT 定时器不快照（恢复契约不对称）、RunningTask 槽位竞态、日志占位符错位、stop() 未注销 javadoc 漂移、死计数器、许可证头文字错误 |

<AI_STEP_RESULT>issues</AI_STEP_RESULT>
