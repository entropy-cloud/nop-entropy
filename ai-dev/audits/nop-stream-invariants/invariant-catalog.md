# nop-stream 不变式目录（Invariant Catalog）— Cycle 1 / I0

> Status: active baseline
> Created: 2026-08-12
> Source: `ai-dev/backlog/nop-stream-invariant-loop-roadmap.md` Work Item I0；21 轮审计证据（`ai-dev/audits/2026-05-31-adversarial-review-nop-stream-r8` ~ `r16` 等）
> Plan: `ai-dev/plans/2026-08-12-1217-1-nop-stream-invariants-cycle1-I0-inventory-baseline.md`
> Consumption: I1（`2026-08-12-1217-2-...`）首批门禁以本目录为目标集来源与不变式定稿

---

## 0. 基线声明（live 实测，2026-08-12）

**当前基线 = 零代码不变式门禁**（live 核实）：

- `ai-dev/tools/check-nop-stream-invariants.mjs` **不存在**（`ai-dev/tools/` 下唯一 stream 相关工具为 `check-nop-stream-audit-manifest.mjs`，1304 行，只校验审计证据 schema）。
- `ai-dev/audits/nop-stream-invariants/` 目录本文件创建前**不存在**（本文件为首次建立）。
- nop-stream 各模块无 `@ParameterizedTest` 用法（`rg "@ParameterizedTest" nop-stream` 无结果）。
- 结论：没有任何可执行门禁约束五族不变式；本目录定稿的不变式全部待 I1 落为门禁。

**审计证据覆盖边界（live 核实修正）**：

- R15 完整发现集**可得**：`2026-05-31-adversarial-review-nop-stream-r9/01-open-findings.md` 头部自标 "Round 15"（目录名 r9 与轮次标签不一致，以文件内轮次标签为准），含 R15-AR-1..AR-9 完整证据。
- R14 完整发现集**不可得**（无 r14 目录）：R14-AR-1..R14-AR-4 以 `2026-05-31-adversarial-review-nop-stream-r9/01-open-findings.md` 头部「前轮已知未修复问题」表与 `2026-05-31-adversarial-review-nop-stream-r16/01-open-findings.md` 头部表为证据上限（R16 表仅含 R14-AR-1）。
- R8 轮：`2026-05-31-adversarial-review-nop-stream-r8/{summary,01-open-findings}.md`（AR-55..AR-68）。
- R10/R11/R12/R13 轮：`2026-05-30-adversarial-review-nop-stream-r{10,11,12,13}/{01-open-findings,summary}.md`。
- R16 轮：`2026-05-31-adversarial-review-nop-stream-r16/{summary,01-open-findings}.md`（AR-1..AR-22）。
- Round 17：`2026-05-31-adversarial-review-nop-stream/{01-open-findings,summary}.md`（AR-1..AR-4）。
- 历史 finding 引用类名以审计快照为准；旧类已删除的（如 `WindowAggregationOperator`）在 §4 带「旧类名 → live 类名」映射注记。

---

## 1. finding-ID → 失败族 映射表（Phase 1）

> 覆盖范围：R8/R10/R11/R12/R13/R14/R15/R16/R17 中与五族相关的 finding 主集（含所有已核实入族项与边界同类项）。timer 服务类 finding（R10-AR-8、R16-AR-3）因直接参与 Window 算子/CEP 触发器语义归入 F1/F4；输入/输出通道、TaskManager 信号量、SourceEnumerator 等非五族 finding 不在此表，列于 §5「I2 关注」的非族候选。

### F1 — Operator 族（Window 粘合层 / Window 算子）

| finding-ID | 严重度 | 摘要 | 审计证据位置 |
|---|---|---|---|
| R8-AR-58 | P1 | WindowAggregationOperator.resolveKey isInstance 方向反转 | `r8/01-open-findings.md` |
| R8-AR-62 | P2 | WindowOperator.snapshotState triggerAccumulators 浅拷贝 | `r8/01-open-findings.md` |
| R10-AR-5 | P1 | WindowAggregationOperator allowedLateness + Long.MIN_VALUE 溢出 | `r10/01-open-findings.md` |
| R10-AR-6 | P1 | WindowAggregationOperator.restoreState 不重建 activeWindowsPerKey | `r10/01-open-findings.md` |
| R10-AR-7 | P1 | WindowAggregationOperator 非前进 watermark 转发 | `r10/01-open-findings.md` |
| R10-AR-8 | P1 | HeapInternalTimerService 同时间戳重注册 timer 被丢弃 | `r10/01-open-findings.md` |
| R11-AR-5 | P2 | WindowAggregationOperator merge 后源窗口 trigger state 未清理 | `r11/01-open-findings.md` |
| R12-AR-5 | P1 | CountTrigger.onMerge() no-op 但 canMerge()=true | `r12/01-open-findings.md` |
| R13-AR-5 | P1 | WindowAggregationOperator 反序列化 `#` 分隔符 | `r13/01-open-findings.md` |
| R13-AR-6 | P1 | WindowOperator.mergeWindowContents 非累加器覆写 | `r13/01-open-findings.md` |
| R13-AR-7 | P2 | WindowOperator.triggerAccumulators 复合 key `_` 分隔碰撞 | `r13/01-open-findings.md` |
| R13-AR-8 | P2 | WindowOperator.windowNamespace() 使用 toString() | `r13/01-open-findings.md` |
| R13-AR-16 | P2 | WindowAggregationOperator.triggerState O(n²) 全表扫描 | `r13/01-open-findings.md` |
| R14-AR-1 | P1 | WindowAggregationOperator serializeTriggerState `:` 分隔符 | `r9/01-open-findings.md` 头部表（R14 全集不可得） |
| R15-AR-3 | P1 | WindowAggregationOperator.processElementWithMerging 不调 trigger.onMerge | `r9/01-open-findings.md` |
| R15-AR-8 | P2 | WindowOperator.onEventTime 清理计时器路径不退休 merging window（MergingWindowSet 无限增长） | `r9/01-open-findings.md` |
| R16-AR-2 | P1 | WindowedStreamImpl.allowedLateness() 死 API | `r16/01-open-findings.md` |
| R16-AR-3 | P1 | HeapInternalTimerService.advanceWatermark 先移除再触发 | `r16/01-open-findings.md` |
| R16-AR-4 | P1 | WindowAggregationOperator 合并路径多目标重复计数 | `r16/01-open-findings.md` |
| R17-AR-2 | P2 | WindowOperator.windowNamespace() identityHashCode | `2026-05-31-adversarial-review-nop-stream/01-open-findings.md` |

### F2 — SinkFunction 族

| finding-ID | 严重度 | 摘要 | 审计证据位置 |
|---|---|---|---|
| R10-AR-10 | P1 | BatchConsumerSinkFunction.flush() 失败后清空 buffer | `r10/01-open-findings.md` |
| R11-AR-3 | P2 | TwoPhaseCommitSinkFunction.finishCommit 迭代未同步 | `r11/01-open-findings.md` |
| R12-AR-1 | P0 | TwoPhaseCommitSinkFunction.saveState() 返回 null | `r12/01-open-findings.md` |
| R12-AR-8 | P2 | StreamSinkOperator.restoreState 不恢复 pendingCommits | `r12/01-open-findings.md` |
| R13-AR-2 | P1 | BatchConsumerSinkFunction.close() 无 try/finally | `r13/01-open-findings.md` |
| R16-AR-1 | P0 | TwoPhaseCommitSinkFunction.saveState() 无锁遍历 synchronizedMap | `r16/01-open-findings.md` |
| R16-AR-11 | P1 | TwoPhaseCommitSinkFunction.setPendingCommits() 接受任意 Map | `r16/01-open-findings.md` |
| R16-AR-19 | P2 | BatchConsumerSinkFunction.flush() 失败后 buffer 无限增长 | `r16/01-open-findings.md` |
| R16-AR-20 | P2 | MessageSinkFunction/MessageSourceFunction 非序列化字段 | `r16/01-open-findings.md` |

### F3 — Checkpoint 机制

| finding-ID | 严重度 | 摘要 | 审计证据位置 |
|---|---|---|---|
| R8-AR-55 | P0 | RemoteGraphExecutionPlanBuilder 只为 taskIndex==0 注册 | `r8/01-open-findings.md` |
| R8-AR-56 | P1 | CheckpointCoordinator.registerTask 非原子 read-then-replace | `r8/01-open-findings.md` |
| R8-AR-60 | P2 | GraphModelCheckpointExecutor 重复注册 listener/participant | `r8/01-open-findings.md` |
| R8-AR-65 | P2 | GraphModelCheckpointExecutor.shutdown 不等待 barrierScheduler | `r8/01-open-findings.md` |
| R10-AR-1 | P0 | CheckpointCoordinator.retryFailedCommits 硬编码 true | `r10/01-open-findings.md` |
| R10-AR-2 | P0 | CheckpointCoordinator.shutdown 不通知 participant abort | `r10/01-open-findings.md` |
| R10-AR-9 | P1 | InputGate AT_LEAST_ONCE 每 channel 都发 barrier | `r10/01-open-findings.md` |
| R11-AR-8 | P3 | CheckpointCoordinator.shutdown 不设 pending ABORTED | `r11/01-open-findings.md` |
| R12-AR-3 | P1 | RecordWriter.emitElement 只写 partitions[0] | `r12/01-open-findings.md` |
| R12-AR-4 | P2 | RecordWriter.selectChannel Math.abs(MIN_VALUE) | `r12/01-open-findings.md` |
| R12-AR-6 | P2 | GraphExecutionPlan.topologicalSort 不检测环 | `r12/01-open-findings.md` |
| R13-AR-3 | P1 | CheckpointCoordinator 失败重试用参与者索引 | `r13/01-open-findings.md` |
| R13-AR-4 | P1 | InputGate.handleBarrierNonRecursive 重叠 barrier 静默丢弃 | `r13/01-open-findings.md` |
| R13-AR-11 | P2 | CheckpointCoordinator.cleanupOldCheckpoints 固定 pipelineId | `r13/01-open-findings.md` |
| R13-AR-12 | P2 | GraphExecutionPlan.build taskIndex==0 共享 OperatorChain | `r13/01-open-findings.md` |
| R10-AR-18 | P3 | GraphExecutionPlan.build 只用第一条 incoming edge 的 EdgeConfig | `r10/01-open-findings.md` |
| R11-AR-7 | P3 | GraphExecutionPlan.build 只取第一条 OperatorChain（多链静默丢弃） | `r11/01-open-findings.md` |
| R15-AR-1 | P0 | CheckpointBarrierTracker.triggerCheckpoint 永久死锁 | `r9/01-open-findings.md` |
| R15-AR-2 | P1 | InputGate.checkBarrierAlignmentComplete 静默丢弃对齐 barrier | `r9/01-open-findings.md` |
| R15-AR-5 | P1 | registerTasksAndTrackers tracker 覆盖 + index 错位 | `r9/01-open-findings.md` |
| R15-AR-9 | P2 | InputGate.handleBarrierNonRecursive 跨 channel 重叠 barrier 静默合并 | `r9/01-open-findings.md` |
| R16-AR-5 | P1 | CheckpointIDCounter 恢复后不更新（ID 倒退） | `r16/01-open-findings.md` |
| R16-AR-10 | P1 | checkpointSuccessMap 无限增长（内存泄漏） | `r16/01-open-findings.md` |
| R16-AR-15 | P2 | LocalFileCheckpointStorage 按文件名 ID 排序 | `r16/01-open-findings.md` |
| R16-AR-16 | P2 | CANCEL 触发最终 checkpoint 不等完成 | `r16/01-open-findings.md` |

### F4 — CEP NFA 族

| finding-ID | 严重度 | 摘要 | 审计证据位置 |
|---|---|---|---|
| R8-AR-63 | P2 | StateTransition.equals() 不比较 condition | `r8/01-open-findings.md` |
| R8-AR-66 | P2 | CepOperator timestamp==currentWatermark 丢弃事件 | `r8/01-open-findings.md` |
| R8-AR-67 | P3 | NFAStateNameHandler `:` 分隔符截断 | `r8/01-open-findings.md` |
| R10-AR-12 | P2 | Pattern.times(from,to) 不验证 from<=to | `r10/01-open-findings.md` |
| R10-AR-13 | P2 | Pattern.timesOrMore() 不验证 times>0 | `r10/01-open-findings.md` |
| R10-AR-14 | P2 | Pattern.allowCombinations() 不验证量词类型 | `r10/01-open-findings.md` |
| R10-AR-15 | P2 | NFAState.STATE_COMPARATOR 用 hashCode 排序 | `r10/01-open-findings.md` |
| R11-AR-4 | P2 | NFA.advanceTime pending 超时跳过 releaseNode（泄漏） | `r11/01-open-findings.md` |
| R11-AR-6 | P3 | Lockable.release() TOCTOU 检查-递减非原子 | `r11/01-open-findings.md` |
| R13-AR-15 | P2 | SharedBuffer.advanceTime 不清理 eventsBuffer（EventId 复用） | `r13/01-open-findings.md` |
| R13-AR-17 | P2 | SimpleCondition.of() 非序列化匿名类 | `r13/01-open-findings.md` |
| R14-AR-4 | P2 | SharedBuffer.advanceTime() 不清理 eventsBuffer 持久化状态 | `r9/01-open-findings.md` 头部表 |
| R16-AR-6 | P1 | CepOperator.onEventTime 清空非 start state | `r16/01-open-findings.md` |
| R12-AR-7 | P2 | CepOperator.currentWatermark (transient) 反序列化后为 0L | `r12/01-open-findings.md` |
| R16-AR-7 | P1 | DeweyNumber.increase() int 溢出 | `r16/01-open-findings.md` |
| R16-AR-8 | P1 | Lockable.release() refCounter 0 时返回 true（静默双重释放） | `r16/01-open-findings.md` |
| R16-AR-12 | P1 | NFA 无上界计算状态（OOM） | `r16/01-open-findings.md` |
| R16-AR-21 | P2 | CepPatternBuilder NOT group pattern 运行时崩溃 | `r16/01-open-findings.md` |

### F5 — ClusterRegistry 族

| finding-ID | 严重度 | 摘要 | 审计证据位置 |
|---|---|---|---|
| R16-AR-9 | P1 | JdbcClusterRegistry.registerNode 写 lease_expire_at=0L | `r16/01-open-findings.md` |
| R16-AR-18 | P2 | InMemoryClusterRegistry.renewLease 忽略 leaseTimeoutMs | `r16/01-open-findings.md` |

---

## 2. 零门禁基线确认记录（Phase 1）

已实测（2026-08-12，命令与结果）：

| 检查项 | 命令 | 结果 |
|---|---|---|
| 专用不变式检查脚本 | `ls ai-dev/tools/ \| grep -i invariant` | 不存在 `check-nop-stream-invariants.mjs` |
| 不变式目录 | `ls ai-dev/audits/nop-stream-invariants/` | 不存在（本文件创建前） |
| 参数化穷举测试 | `rg "@ParameterizedTest" nop-stream` | 无结果 |

---

## 3. 五族 live 修复状态表（Phase 1，2026-08-12 实测）

> 状态分类：`fixed`（live 代码已确认修复）/ `residual`（缺陷点仍在 live 代码，附 `文件:行`）/ `partial`（核心路径修复但兄弟点残留）。**residual/partial 全部为 I2 red list 候选输入**。

| finding-ID | 旧类 → live 类 | 状态 | 证据（live 文件:行） |
|---|---|---|---|
| R16-AR-9 | JdbcClusterRegistry | **residual** | `JdbcClusterRegistry.java:112-115` INSERT 仍写 `lease_expire_at=0L`（:115）；`:172-174` getActiveNodes 仍按 `lease_expire_at > now` 过滤（:174）。**I2 red list 候选** |
| R16-AR-18 | InMemoryClusterRegistry | **residual** | `InMemoryClusterRegistry.java:68-81` renewLease 仍忽略 `leaseTimeoutMs` 参数（:74 只存时间戳）；`:90/:98/:114` 仍用固定 `leaseTtlMs`。**I2 red list 候选** |
| R16-AR-1 | TwoPhaseCommitSinkFunction | **residual** | `TwoPhaseCommitSinkFunction.java:83` saveState() 的 `new TreeMap<>(pendingCommits)` 无 synchronized 块。**I2 red list 候选** |
| R16-AR-11 | TwoPhaseCommitSinkFunction | **residual** | `TwoPhaseCommitSinkFunction.java:76-78` setPendingCommits() 仍接受任意 Map（未包装 synchronizedMap）。调用方 `StreamSinkOperator.java:157` restore 路径已主动包装 synchronizedMap —— 部分缓解但 setter 本身未防护。**I2 red list 候选** |
| R16-AR-2 | WindowedStreamImpl → WindowOperator | **fixed** | `WindowedStreamImpl.java:186/:201/:216/:231` 4 个 call-site 均传 `assigner, trigger, evictor, allowedLateness, function, elementType, keySelector, keyClass`；`WindowOperatorFactoryImpl.java:29-119` 4 个 create*Operator 全参数转发；`WindowOperatorBuilder.java:183-203` buildWindowOperator 14 参数全部传入 `WindowOperator` 构造器 |
| R15-AR-3 | WindowAggregationOperator → WindowOperator | **fixed** | `WindowOperator.java:644` 已调用 `triggerContext.onMerge(mergedWindows)`；`:652` 调 `mergeWindowContents` |
| R16-AR-8 | Lockable | **fixed** | `Lockable.java:56-66` release() CAS + refCounter<=0 抛 `StreamRuntimeException`；`:68-81` releaseOrDetach() 同样 CAS + 负数 fail-fast |
| R16-AR-5 | CheckpointIDCounter / GraphModelCheckpointExecutor → CheckpointCoordinator | **fixed** | `CheckpointCoordinator.java:896-900` restoreFromCheckpoint() 单调 ID 推进（`restoredId >= currentCounter` 才 `set(restoredId + 1)`）——**R16 AR-5 真实修复点**；`CheckpointIDCounter` AtomicLong 仅为并发机制 |
| R16-AR-10 | CheckpointCoordinator | **fixed** | `CheckpointCoordinator.java:203` checkpointSuccessMap 为 ConcurrentHashMap；`:805` 完成时 remove；`:1179` 清理 |
| R16-AR-7 | DeweyNumber | **fixed** | `DeweyNumber.java:110-118` increase(int) 溢出检查，越界抛 `StreamException` |
| R16-AR-6 | CepOperator | **fixed（带守卫）** | `CepOperator.java:540-568` 清空前验证 allTimedOut 且先 releaseNode（不再盲清空非 start state） |
| R13-AR-15 / R14-AR-4 | SharedBuffer | **fixed** | `SharedBuffer.java:224-234` advanceTime 同时清理 `eventsBufferCache`；`:242-249` registerEvent 复用 EventId 冲突检测 + 溢出 fail-fast |
| R16-AR-15 | LocalFileCheckpointStorage | **partial** | `LocalFileCheckpointStorage.java:116-138` getLatestCheckpoint 仍按文件名 extractCheckpointId 排序；因 AR-5 已修（ID 单调），级联风险解除——**watch-only residual** |
| R10-AR-1 | CheckpointCoordinator | **fixed** | `CheckpointCoordinator.java:1114` retryFailedCommits 用 `checkpointSuccessMap.getOrDefault(failedEpoch, true)` 记录原始 success（R11 修复确认） |
| R10-AR-2 | CheckpointCoordinator | **fixed** | `CheckpointCoordinator.java:1138` shutdown() 先 `:1170-1171` notifyParticipantsFinishCommit(false) + notifyCheckpointAborted 再清理 |
| R10-AR-10 | BatchConsumerSinkFunction | **fixed** | `BatchConsumerSinkFunction.java:91-105` flush() 中 `buffer.clear()` 仅在 consume 成功后（:99-100）；失败保留数据 + 抛错（"data retained for retry"） |
| R13-AR-2 | BatchConsumerSinkFunction | **fixed** | `BatchConsumerSinkFunction.java:116-143` close() try/catch/finally：flush 失败记录 flushError，finally 中 close consumer，最后统一抛出 |
| R12-AR-1 | TwoPhaseCommitSinkFunction | **fixed** | `TwoPhaseCommitSinkFunction.java:81-86` saveState() 返回真实 snapshot（含 PENDING_COMMITS_KEY） |
| R12-AR-8 | StreamSinkOperator | **fixed** | `StreamSinkOperator.java:146-158` restoreState() 从 snapshot 重建 pendingCommits 并 `setPendingCommits(synchronizedMap(...))` |
| R11-AR-3 | TwoPhaseCommitSinkFunction | **fixed** | `TwoPhaseCommitSinkFunction.java:101-118` finishCommit 迭代/删除均在 `synchronized(pending)` 内 |
| R16-AR-4 | WindowAggregationOperator → WindowOperator | **fixed** | 旧合并多目标路径已随 `WindowAggregationOperator` 删除（git `905d6411a`）；live 合并路径集中于 `WindowOperator.java:641-652`（单 merged window + onMerge） |
| R13-AR-5 / R14-AR-1 | WindowAggregationOperator → WindowOperator | **fixed（随类删除）** | 旧 `#`/`:` 分隔符序列化路径已删除；live `WindowOperator` 状态键用结构化描述符（stateDesc） |
| R13-AR-6 | WindowOperator | **fixed** | 非累加器回退路径改为显式 fail-fast：`WindowOperator.java:1462-1472` 非累加器冲突抛 `ERR_STREAM_WINDOW_NON_ACCUMULATOR_MERGE_CONFLICT`（`mergeWindowContents` 定义于 :1336） |
| R15-AR-8 | WindowOperator | **待 I2 动态验证** | `WindowOperator.java:776-783` onEventTime cleanup 分支未见 `retireWindow` 调用（MergingWindowSet 收敛性待 I2 验证）——watch-only residual |
| R15-AR-9 | InputGate | **fixed** | `InputGate.java:625-674` handleBarrierNonRecursive 已改为 per-id `inFlightAlignments` 对齐，跨 channel 重叠 barrier 按 checkpoint ID 分离 |

**核实方法**：所有 `fixed` 结论均以 2026-08-12 live 源码 + 上述 `文件:行` 为据；R16 AR-1/AR-11/AR-9/AR-18 的 residual 结论同样 live 实测。

---

## 4. 审计目标集清单（Phase 2 — live 枚举，I1 表完备性门禁的「表」来源）

### 4.1 「变更型方法」判定标准（定稿，I1 分类器语义）

- **入表**：`public`/`protected` 且会改变对象内部状态的方法——显式状态更新、集合/Map 写入、计数器递增、状态注册/清除、lease 续期/注册、timer 注册/删除。
- **不入表**：getter / 只读查询方法、`toString`/`equals`/`hashCode`、构造器、静态工厂、测试专用方法（`getXForTesting`/`setXForTesting`）。
- I1 表完备性门禁：新增变更型方法未入表 → 门禁红。

### 4.2 Operator 族

**粘合层（WindowedStreamImpl → IWindowOperatorFactory → WindowOperatorFactoryImpl → WindowOperatorBuilder → WindowOperator）**

| 类（live 路径） | 变更型方法 |
|---|---|
| `nop-stream-core/.../datastream/WindowedStreamImpl.java` | `apply`(:186), `aggregate`(:201), `reduce`(:216), `process`(:231)（4 个 call-site） |
| `nop-stream-core/.../operators/IWindowOperatorFactory.java` | `createAggregateOperator`(:22), `createReduceOperator`(:33), `createApplyOperator`(:44), `createProcessOperator`(:55) |
| `nop-stream-runtime/.../windowing/WindowOperatorFactoryImpl.java` | 4 个 `create*Operator`(:31/:54/:77/:100) |
| `nop-stream-runtime/.../windowing/WindowOperatorBuilder.java` | `windowAssigner`(:61), `trigger`(:66), `evictor`(:71), `allowedLateness`(:76), `keySelector`(:81), `keyClass`(:86), `keySerializer`(:91), `windowSerializer`(:96), `lateDataOutputTag`(:101), `accumulationMode`(:106), `reduce`(:135), `apply`(:163), `process`(:173), `aggregate`(:119) |
| `nop-stream-runtime/.../windowing/WindowOperator.java` | `open`(:386), `processElement`(:578), `processWatermark`(:494), `onEventTime`(:725), `onProcessingTime`(:791), `snapshotState`(:513), `restoreState`(:545), `close`(:500), `copyForSubtask`(:381) |
| `nop-stream-core/.../operators/StreamReduceOperator.java` | （live 类，待 I1 复核变更方法） |

### 4.3 SinkFunction 族

| 类（live 路径） | 变更型方法 |
|---|---|
| `nop-stream-core/.../functions/sink/TwoPhaseCommitSinkFunction.java` | `setPendingCommits`(:76), `saveState`(:81), `prepareCommit`(:89), `finishCommit`(:95), `restoreFromEpoch`(:153), `abort`(:63), `recover`(:67), `consume`(:40) |
| `nop-stream-core/.../operators/StreamSinkOperator.java` | `restoreState`(:141)（2PC pendingCommits 重建路径 :146-158） |
| `nop-stream-connector-batch/.../BatchConsumerSinkFunction.java` | `consume`(:77), `finish`(:108), `close`(:116)（`flush`(:91) 为 private 内部助手，不入表） |
| `nop-stream-connector/.../MessageSinkFunction.java` | （live 类，serialization 关注） |
| `nop-stream-connector/.../MessageSourceFunction.java` | （live 类，serialization 关注） |

### 4.4 Checkpoint 机制

| 类（live 路径） | 变更型方法 |
|---|---|
| `nop-stream-core/.../checkpoint/CheckpointIDCounter.java` | `getAndIncrement`(:35), `set`(:49), `incrementAndGet`(:56), `compareAndSet`(:63) |
| `nop-stream-runtime/.../checkpoint/CheckpointCoordinator.java` | `addListener`(:242), `removeListener`(:246), `addParticipant`(:250), `removeParticipant`(:254), `setAbortHandler`(:262), `startCheckpointScheduler`(:266), `stopCheckpointScheduler`(:334), `tryTriggerPendingCheckpoint`(:353), `tryTriggerCheckpointWithReason`(:369), `acknowledgeTask`(:423), `completePendingCheckpoint`(:453), `abortPendingCheckpoint`(:849), `restoreFromCheckpoint`(:885), `registerTask`(:983), `unregisterTask`(:987), `shutdown`(:1138), `registerSourceEnumeratorVertex`(:1259), `reportTaskCheckpointFailure`(:930) |
| `nop-stream-runtime/.../checkpoint/PendingCheckpoint.java` | `acknowledgeTask`(:135), `toCompletedCheckpoint`(:150), `abort`(:162/:188), `fail`(:175), `forceComplete`(:196), `dispose`(:207) |
| `nop-stream-runtime/.../checkpoint/storage/LocalFileCheckpointStorage.java` | `storeCheckPoint`(:84), `deleteCheckpoint`(:237), `deleteAllCheckpoints`(:255), `storeSavepoint`(:389), `storeEpochManifest`(:489) |
| `nop-stream-runtime/.../checkpoint/CheckpointPlanBuilder.java` | `build`(:44) |
| `nop-stream-core/.../execution/InputGate.java` | barrier 对齐状态机（I1 复核变更方法） |

### 4.5 CEP NFA 族

| 类（live 路径） | 变更型方法 |
|---|---|
| `nop-stream-cep/.../nfa/NFA.java` | `open`(:193), `close`(:206), `process`(:236), `advanceTime`(:266) |
| `nop-stream-cep/.../nfa/DeweyNumber.java` | `increase`(:99), `increase(int)`(:110), `addStage`(:127) |
| `nop-stream-cep/.../nfa/sharedbuffer/Lockable.java` | `lock`(:46), `release`(:56), `releaseOrDetach`(:68) |
| `nop-stream-cep/.../nfa/sharedbuffer/SharedBuffer.java` | `advanceTime`(:224), `registerEvent`(:236), `upsertEvent`(:311), `upsertEntry`(:327), `removeEvent`(:342), `removeEntry`(:352), `flushCache`(:420) |
| `nop-stream-cep/.../nfa/sharedbuffer/SharedBufferAccessor.java` | `advanceTime`(:64), `registerEvent`(:80), `lockNode`(:237), `releaseNode`(:258), `releaseEvent`(:329), `close`(:345), `materializeMatch`(:211) |
| `nop-stream-cep/.../operator/CepOperator.java` | `open`(:247), `processElement`(:462), `processWatermark`(:450), `onEventTime`(:509), `onProcessingTime`(:571), `snapshotState`(:419), `restoreState`(:429), `close`(:405) |
| `nop-stream-cep/.../nfa/NFAState.java` | `setStateChanged` 等（I1 复核） |

### 4.6 ClusterRegistry 族

| 类（live 路径） | 变更型方法 |
|---|---|
| `nop-stream-runtime/.../cluster/ClusterRegistry.java`（接口） | `registerCoordinator`(:25), `registerNode`(:42), `renewLease`(:51), `assignTask`(:85), `removeTaskAssignment`(:127) |
| `nop-stream-runtime/.../cluster/JdbcClusterRegistry.java` | `registerCoordinator`(:50), `registerNode`(:97), `renewLease`(:132), `assignTask`(:188), `removeTaskAssignment`(:260) |
| `nop-stream-runtime/.../cluster/InMemoryClusterRegistry.java` | `registerCoordinator`(:48), `registerNode`(:59), `renewLease`(:68), `assignTask`(:122), `removeTaskAssignment`(:169), `evictExpiredNodes`(:94) |
| `nop-stream-runtime/.../cluster/NodeDiscoveryConsistencyChecker.java` | `check`(:98), `assertConsistent`(:132) |

---

## 5. 首批 5 条不变式（Phase 3 定稿）

> 每条四要素：**不变式陈述 / 覆盖失败族 / 历史审计证据（finding-ID + 文件:行）/ 检测方法**。live 修复状态与 I2 关注点一并登记。检测方法本轮默认 JUnit `@ParameterizedTest`（方法表驱动穷举）或 mjs 静态扫描；ArchUnit 标注为可选待后续 Decision。

### 不变式 #1 — Window 粘合层构造参数完备性

- **陈述**：`WindowedStreamImpl` 4 个聚合入口（apply/aggregate/reduce/process）的 8 参数元组 `(windowAssigner, trigger, evictor, allowedLateness, function, elementType, keySelector, keyClass)` 必须 round-trip 至 `WindowOperator` 构造器（经 `IWindowOperatorFactory.create*` → `WindowOperatorBuilder.buildWindowOperator` → `WindowOperator(...)`），任何参数不得在传输链上被遗漏、替换或置默认值。
- **覆盖失败族**：F1（Operator 族——"参数被类似地遗忘传递"打地鼠模式）
- **历史审计证据**：R16-AR-2（`2026-05-31-adversarial-review-nop-stream-r16/01-open-findings.md`，allowedLateness 死 API：`WindowedStreamImpl.java:127-130,141-168` 快照行号）；R8-AR-58（`r8/01-open-findings.md`，resolveKey 类型检查方向反转）；R10-AR-5/6/7（`r10/01-open-findings.md`，溢出/恢复/watermark 三个独立参数语义缺陷）；R15-AR-3（`r9/01-open-findings.md`，合并路径 trigger 生命周期不一致）；R16-AR-4（`r16/01-open-findings.md`，多目标重复计数）。历史引用类 `WindowAggregationOperator` 已删除 → live 对应类 `WindowOperator`（git `905d6411a`）。
- **检测方法**：JUnit `@ParameterizedTest`——对 4 个入口 × 8 参数做构造器参数断言（反射或 spy 验证 `WindowOperator` 构造器收到的实参与入口调用实参逐一相等）；可选 mjs 静态扫描调用链参数名比对。
- **live 修复状态**：fixed（2026-08-12 实测，见 §3 R16-AR-2 行）。**I2 关注**：作为回归门禁防未来重构遗漏参数。

### 不变式 #2 — synchronized 集合字段迭代点必须在 synchronized 块内

- **陈述**：所有 `Collections.synchronizedMap/List/Set` 类型字段的遍历（含 copy 构造、`entrySet()`/`iterator()` 迭代）必须持有该集合的 monitor（`synchronized(field)`）或使用非迭代替代（`computeIfAbsent` 等）；违反即潜在 CME。
- **覆盖失败族**：F2（SinkFunction 族并发守卫）
- **历史审计证据**：R16-AR-1（`r16/01-open-findings.md`，`TwoPhaseCommitSinkFunction.saveState()` 无锁遍历）；R16-AR-11（`r16/01-open-findings.md`，setPendingCommits 接受任意 Map 破坏同步保证）；R11-AR-3（`r11/01-open-findings.md`，finishCommit 迭代未同步）；R12-AR-1（`r12/01-open-findings.md`，saveState 返回 null 同文件 :66-71）。
- **检测方法**：JUnit `@ParameterizedTest` 或 mjs 静态扫描——枚举 live 代码中 `Collections.synchronized*` 字段，检查其所有使用点（引用/迭代/copy）是否在 `synchronized` 块内。
- **live 修复状态**：partial——`finishCommit` 已同步（`TwoPhaseCommitSinkFunction.java:101-118`），但 `saveState()`(:83) 仍无锁 copy、`setPendingCommits()`(:76-78) 仍接受任意 Map。**I2 red list 候选**（`TwoPhaseCommitSinkFunction.java:83`）。

### 不变式 #3 — CheckpointIDCounter 更新原子性 / 恢复后单调性

- **陈述**：checkpoint ID 生成与恢复必须满足：(a) 并发下 `getAndIncrement` 原子（AtomicLong）；(b) 恢复路径必须将计数器推进至 `restoredId + 1`（`restoredId >= current` 才 set），任何恢复后 ID 倒退 = 违约。
- **覆盖失败族**：F3（Checkpoint 机制）
- **历史审计证据**：R16-AR-5（`r16/01-open-findings.md`，恢复后 ID 从 0 开始 → 覆写旧 checkpoint）；R16-AR-15（`r16/01-open-findings.md`，storage 按文件名排序与 AR-5 级联）；R10-AR-1/2（`r10/01-open-findings.md`，2PC 提交/abort 通知）；R13-AR-3（`r13/01-open-findings.md`，失败重试索引）；R8-AR-56（`r8/01-open-findings.md`，registerTask 非原子）。
- **检测方法**：JUnit `@ParameterizedTest`——恢复不同 `restoredId`（含 <、=、> current）断言计数器推进正确且单调；并发 getAndIncrement 压力测试。
- **live 修复状态**：fixed（`CheckpointCoordinator.java:896-900` 单调推进；`CheckpointIDCounter` AtomicLong :22-25）。**I2 关注**：作为回归门禁防恢复路径回归（R16 summary 级联 AR-5+AR-15）。

### 不变式 #4 — CEP SharedBuffer/Lockable 释放对称性（over-release 必须 fail-fast）

- **陈述**：(a) 每个 `lock()` 必须有对称的 `release()`/`releaseOrDetach()`；(b) `release()` 时 refCounter<=0（over-release）必须 fail-fast（抛异常），不得静默返回 true；(c) `advanceTime`/`releaseNode`/`CepOperator` 清理路径不得遗留未释放的 SharedBuffer 条目（EventId 不得复用冲突）。
- **覆盖失败族**：F4（CEP NFA 族）
- **历史审计证据**：R16-AR-8（`r16/01-open-findings.md`，Lockable 双重释放静默 true）；R16-AR-7（`r16/01-open-findings.md`，DeweyNumber 溢出破坏版本比较）；R16-AR-6（`r16/01-open-findings.md`，CepOperator 清空非 start state）；R11-AR-4（`r11/01-open-findings.md`，pending 超时跳过 releaseNode）；R13-AR-15/R14-AR-4（`r13/01-open-findings.md` / `r9` 头部表，advanceTime 不清理 eventsBuffer）；R11-AR-6（`r11/01-open-findings.md`，TOCTOU）。
- **检测方法**：JUnit `@ParameterizedTest`——方法表驱动：对每个清理/释放路径（advanceTime、releaseNode、CepOperator 超时清理、NFA 超时）验证 SharedBuffer 条目计数收敛（锁对称）；Lockable over-release 断言抛 `StreamRuntimeException`；DeweyNumber 溢出断言抛错。
- **live 修复状态**：fixed（`Lockable.java:56-81` CAS + fail-fast；`DeweyNumber.java:110-118` 溢出抛错；`SharedBuffer.java:224-234/:242-249` advanceTime 清理 + EventId 冲突检测；`CepOperator.java:540-568` 清空前验证）。**I2 关注**：释放对称性逐路径计数验证（catalog 未做动态验证，I1 门禁 + I2 跑全路径）。

### 不变式 #5 — ClusterRegistry 多实现语义一致性（registerNode 可见性 + renewLease per-renewal timeout）

- **陈述**：(a) `registerNode` 成功后节点必须对 `getActiveNodes` 立即可见（JDBC 实现不得写 `lease_expire_at=0L` 导致注册窗口内不可见）；(b) `renewLease(nodeId, leaseTimeoutMs)` 必须按 per-renewal 参数计算过期时间，不得忽略参数使用固定 TTL；(c) 两实现（Jdbc / InMemory）对同一接口语义一致（注册可见性、lease 计算、过期判定）。
- **覆盖失败族**：F5（ClusterRegistry 族）
- **历史审计证据**：R16-AR-9（`r16/01-open-findings.md`，Jdbc registerNode 写 0L + getActiveNodes 过滤 `> now`）；R16-AR-18（`r16/01-open-findings.md`，InMemory 忽略 leaseTimeoutMs 硬编码 15s）。
- **检测方法**：JUnit `@ParameterizedTest`——以 `ClusterRegistry` 接口为表，对两个实现（Jdbc + InMemory）跑同一语义断言集：注册即可见、renewLease 用自定义 timeout 后节点在超时前活跃/超时后不活跃、过期判定一致。
- **live 修复状态**：**residual**——`JdbcClusterRegistry.java:112-115` INSERT 仍写 0L、`:172-174` 仍过滤 `lease_expire_at > now`（AR-9 未修复）；`InMemoryClusterRegistry.java:68-81` renewLease 仍忽略 `leaseTimeoutMs`（AR-18 未修复）。**I2 red list 候选**（两条）。

---

## 6. I2 关注点汇总（red list 候选 + 非族候选）

### I2 red list 候选（residual 不变式命中点）

| # | 位置 | 关联不变式 | 对应 finding |
|---|---|---|---|
| 1 | `JdbcClusterRegistry.java:112-115`（INSERT lease_expire_at=0L） | #5 | R16-AR-9 |
| 2 | `JdbcClusterRegistry.java:172-174`（getActiveNodes 按 > now 过滤） | #5 | R16-AR-9 |
| 3 | `InMemoryClusterRegistry.java:68-81`（renewLease 忽略 leaseTimeoutMs） | #5 | R16-AR-18 |
| 4 | `TwoPhaseCommitSinkFunction.java:83`（saveState 无锁 copy） | #2 | R16-AR-1 |
| 5 | `TwoPhaseCommitSinkFunction.java:76-78`（setPendingCommits 接受任意 Map） | #2 | R16-AR-11 |

### I2 watch-only residual（不阻塞门禁，但需验证）

- `LocalFileCheckpointStorage.java:116-138` 仍按文件名 ID 排序（R16-AR-15）；因 AR-5 已修而级联解除。
- `WindowOperator.java:776-783` onEventTime cleanup 路径未显式 `retireWindow`（R15-AR-8 相关）——live 代码在 cleanup 分支未见 retireWindow 调用，需 I2 动态验证 MergingWindowSet 是否随 cleanup 收敛（潜在 watch-only residual）。
- `InputGate.java:625-674` handleBarrierNonRecursive 已改为 per-id `inFlightAlignments` 对齐（R15-AR-9 跨 channel 重叠 barrier 路径已按 checkpoint ID 分离），需 I2 验证 `maxConcurrentCheckpoints>1` 场景语义成立。

### 非五族但同族复发的候选（I2 对抗探查评估升格）

- R13-AR-9 ResultPartition.close() 死锁类（并发资源管理）
- R16-AR-19/AR-20 BatchConsumerSinkFunction buffer 增长 / 序列化（SinkFunction 族边界）
- R8-AR-59 SimpleStreamOperatorFactory 共享模板（Operator 族边界）
- R16-AR-13 WatermarkOutputMultiplexer 停滞（watermark 生命周期）

---

## 7. 审计目标集复核方法（供 I1 表完备性门禁使用）

- 目标集清单（§4）每条含 live `类路径:方法`，可在仓库中 `rg "方法名" 类路径` 逐条复核（2026-08-12 已全部实测通过）。
- I1 门禁的「表」= §4 变更型方法集；「新方法未入表 → 红」的完备性检查以 §4.1 判定标准为分类器。
