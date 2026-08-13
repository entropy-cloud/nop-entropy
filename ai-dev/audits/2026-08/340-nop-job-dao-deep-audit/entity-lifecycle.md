# nop-job-dao 审计 — Entity / 跨模块生命周期详细 findings

**审计源**：独立子 agent 深度审计（entity + cross-module lifecycle），2026-08-12
**文件**：`NopJobFire`/`NopJobSchedule`/`NopJobTask` + `_gen/_*` + ORM `nop-job.orm.xml` + 消费者（coordinator/worker/service）

## 生命周期迁移 trace（21 条路径）

| # | 迁移 | 代码路径 | 测试覆盖 |
|---|------|----------|----------|
| 1 | schedule ENABLED → planner 插 WAITING fire | `JobPlannerScannerImpl:152` → `JobScheduleStoreImpl:91` | ✅ TestJobConcurrency/ TestJobStoreImpl |
| 2 | ENABLED + DISCARD + activeFires>0 → skip | `JobPlannerScannerImpl:162` → `:85 advanceScheduleAfterSkip` | ✅ TestBlockStrategies |
| 3 | ENABLED + OVERLAY → cancel active + new fire | `JobPlannerScannerImpl:173` → `:117 overlayFireAndAdvanceSchedule` | ✅ TestBlockStrategies |
| 4 | ENABLED + RECOVERY → 复用 failed fire | `JobPlannerScannerImpl:167` → `:159 recoveryFireAndAdvanceSchedule` | ⚠️ 仅 no-failed 分支测过 |
| 5 | fire WAITING → DISPATCHING | `JobFireStoreImpl:86 tryLockFiresForDispatch` | ✅ TestJobStoreImpl |
| 6 | fire DISPATCHING → RUNNING + 插 tasks | `JobFireStoreImpl:104 insertTasksAndMarkFireDispatching` | ✅ TestJobStoreImpl |
| 7 | fire DISPATCHING → WAITING（AR-86 backoff） | `JobFireStoreImpl:262 revertDispatchingFireToWaiting` | ✅ TestJobDispatcherScannerRouting |
| 8 | fire DISPATCHING → TIMEOUT | `JobTimeoutCheckerImpl:281` ← `processDispatchTimeouts:163` | ✅ TestJobTimeoutChecker |
| 9 | task WAITING → CLAIMED | `JobTaskStoreImpl:81 tryLockTasksForExecute` ← `JobWorkerScannerImpl:210` | ✅ TestJobWorkerScanner |
| 10 | task CLAIMED → RUNNING（AR-85） | `JobWorkerScannerImpl:246` | ✅ TestJobWorkerScanner |
| 11 | task RUNNING → SUCCESS/FAILED/TIMEOUT | `JobWorkerScannerImpl:275 handleExecutionResult` | ✅ TestJobWorkerScanner |
| 12 | task CLAIMED/RUNNING → SUSPICIOUS（worker gone） | `JobTimeoutCheckerImpl:262 tryMarkSuspiciousIfWorkerGone` | ✅ TestJobTimeoutChecker |
| 13 | task SUSPICIOUS → TIMEOUT | `JobTimeoutCheckerImpl:385 markSuspiciousAsTimeout` | ✅ TestJobTimeoutChecker |
| 14 | task RUNNING → TIMEOUT（执行超时） | `JobTimeoutCheckerImpl:415 tryMarkTimeout` | ⚠️ **P1-1 此处误触发** |
| 15 | task WAITING(stale, dead worker) → WAITING+workerInstanceId=null | `JobTaskStoreImpl:156 resetStaleWaitingTasks`（AR-88） | ✅ TestJobTimeoutChecker/ TestJobStoreImpl |
| 16 | fire RUNNING → 终态（聚合） | `JobCompletionProcessorImpl:123 completeSingleFire` → `resolveFinalStatus` | ✅ TestJobE2E |
| 17 | fire → CANCELED（手动） | `JobFireStoreImpl:146 cancelFire` ← `NopJobFireBizModel:78` | ✅ TestNopJobFireBizModel |
| 18 | 终态 fire → WAITING（rerun） | `NopJobFireBizModel:97 rerunFire` → `JobScheduleStoreImpl:239 insertManualFire` | ✅ TestNopJobFireBizModel |
| 19 | schedule COMPLETED（task result completed:true） | `JobCompletionProcessorImpl:189-191` | ⚠️ 仅 decision 逻辑测过，无 full e2e |
| 20 | schedule ARCHIVED（手动） | `NopJobScheduleBizModel:130 archiveSchedule` | ✅ TestNopJobScheduleBizModel |
| 21 | fire DISPATCHING + schedule 删除 → FAILED | `JobTimeoutCheckerImpl:283-305` | ✅ TestJobTimeoutChecker |

## Findings（与 store/helper 报告去重后，本报告独有项）

| ID | Sev | File:line | Description | Evidence | One-line fix |
|----|-----|-----------|-------------|----------|--------------|
| P1-1 | P1 | `JobTimeoutCheckerImpl.java:446-457` | RUNNING-task 执行超时在 `schedule.timeoutSeconds` 空 且 `executionTimeoutMs≤0` 时回退 `dispatchTimeoutMs`（300000ms）。合法长任务（>5min，无 per-schedule 超时）被"派发超时"误杀 | `if…else if…else effectiveTimeoutMs = dispatchTimeoutMs;`——dispatchTimeoutMs 语义是"fire 卡 DISPATCHING" | 默认禁用（-1，依赖 worker-liveness SUSPICIOUS→TIMEOUT）或独立 taskExecutionTimeoutMs 配置（plan 340 §2.1） |
| P3-n | P3 | `_NopJobFire.java:28,29` + ORM `:294-299` | **死列** NopJobFire `taskCostCpu`/`taskCostMemory`：dispatcher 从 schedule 读 cost 直写 task（`JobDispatcherScannerImpl:149,152`），fire 副本从不读写 | grep `fire.setTaskCostCpu\|fire.getTaskCostCpu` → 0 | 移除列或 buildFire 时填快照（→ ORM plan） |
| P3-o | P3 | `_NopJobTask.java:6,21,22` + ORM `:374-379,426-430` | **死列** NopJobTask `workerAddress`（builder 写 targetHost；`TestDefaultJobCancelHandler:313` 注释确认漂移）、`progress`/`progressMessage`（主代码从不读写，仅 generated+beans） | grep 仅 generated/beans/test | 移除列或实现 progress 上报（→ ORM plan） |
| P3-p | P3 | `_NopJobFire.java:18` + ORM `:281-283,342-344` + `JobFireStoreImpl:276` | **死列+死方法+死索引** `retryRecordId`：仅 `JobFireStoreImpl.updateRetryRecordId` 写，**无生产调用方**（retry bridge 从不回写）；索引 `IX_NOP_JOB_FIRE_RETRY` 从不被查询用 | grep `updateRetryRecordId` → 1 prod 定义 + 3 test stub，0 调用 | 接线 retry adapter 调用它，或删列+索引+方法（→ ORM plan） |
| P3-q | P3 | `JobTimeoutCheckerImpl.java:376-383 startTimeOrNow` | 误导命名+脆弱 fallback：返回 duration（`now-startTime`）非"start time"；startTime null 时 fallback `updateTime`，但 AR-86 revert 后 startTime 是 backoff-until 标记，产出无意义 duration | 方法名暗示 timestamp，body 返回 `Math.max(now-startTime,0)` | 改名 `computeDispatchElapsedMs`，去 updateTime fallback（plan 340 §2.11） |
| P3-r | P3 | `NopJobFire.java:8-10`/`NopJobSchedule.java:8-10` | **实体空壳**——所有行为在 helper/store/BizModel，FSM 是纯函数取 status int，实体无法强制迁移（任意处可 `fire.setFireStatus(SUCCESS)` bypass FSM） | NopJobFire.java 11 空行 | 加 `fire.transitionTo(s)` 委托 FSM，或文档化 helper 为唯一 sanctioned 路径 |

（P2-1..P2-9 见 store/helper 报告；此处不重复。）

## ORM 模型 gap

| Gap | Sev | Detail |
|-----|-----|--------|
| `fetchDispatchingFires` ORDER BY 索引缺失 | P3 | 需 `(fireStatus,partitionIndex,startTime,jobFireId)`——现 `IX_NOP_JOB_FIRE_DISPATCH_SCAN` 终于 `scheduledFireTime` |
| `IX_NOP_JOB_FIRE_DISPATCH_SCAN` 不覆盖 AR-86 startTime backoff | P3 | `fetchWaitingFires` filter `startTime IS NULL OR <=?`，该列不在索引 |
| `IX_NOP_JOB_TASK_RUN_SCAN` ORDER BY 不匹配 | P3 | 索引 `(taskStatus,partitionIndex,createTime)`，`fetchRunningTasks` 按 startTime 排序 |
| `IX_NOP_JOB_FIRE_RETRY` 未用 | P3 | 支撑死列 retryRecordId（P3-p） |
| `UK_NOP_JOB_FIRE_SCHEDULE_TIME_SOURCE` 跨全部状态 | P3 | pre-check `hasWaitingFire` 只查 WAITING——语义漂移 |
| VERSION 乐观锁 | ✅ | 3 实体均 `versionProp="version"`；store 一致用 `tryUpdateWithVersionCheck`/`tryUpdateManyWithVersionCheck` |
| Unique 约束 | ✅ | `UK_NOP_JOB_SCHEDULE_NS_GROUP_NAME`、`UK_NOP_JOB_TASK_FIRE_NO` 正确且被使用 |
| Relations 定义但运行时未用 | P3 | `NopJobFire.jobSchedule`/`NopJobTask.jobFire` 声明但消费者总用显式 `loadSchedule`/`loadFire`（刻意 session 控制）——非 bug，但元数据冗余 |
| `partitionRange` VARCHAR(400) vs 存储格式 | ✅ | `PartitionTaskBuilder:80` 写 `IntRangeBean.toString()`（~10 字符），400 充足 |
| `shardingIndex`/`shardingTotal` INTEGER | ✅ | 受 `healthyInstances.size()` 约束，无溢出；`PARTITION_HASH_RANGE=[0,32768]` 适配 int |

## Reserved-capacity 流分析

| 方面 | 状态 | 证据 |
|------|------|------|
| 写路径 | ✅ | dispatcher 从 schedule 写 `task.costCpu/Memory`（`JobDispatcherScannerImpl:149-152`、`AdaptiveJobTaskBuilder:113-114`） |
| 读路径（worker） | ✅ | `JobWorkerScannerImpl:150 sumReservedCost(hostId)`；double-count guard `:186-194`（AR-83） |
| 读路径（dispatcher bestFit） | ✅ | `sumReservedCostByWorker()` 经 `IWorkerLoadProvider`（`JobDispatcherScannerImpl:72`） |
| 单位一致 | ✅ | CPU 全 millicore、内存全 MB（`NopJobCoreConstants:10-15`）；`ResourceVector` 统一 |
| 状态集一致 | ⚠️ | `RESERVED_TASK_STATUSES`(WAITING+CLAIMED+SUSPICIOUS+RUNNING) 宽于 `IN_FLIGHT_STATUSES`(CLAIMED+RUNNING)，刻意文档化；**但** 死 worker 的 SUSPICIOUS task 在 timeout checker 解析前持续计入 `sumReservedCostByWorker`——对 bestFit 瞬态误报（self-healing，受 `scanInterval×2` 约束） |
| worker 消失清理 | ⚠️ | 无显式"worker down 释放 reserved"；依赖 (a) SUSPICIOUS→TIMEOUT、(b) `resetStaleWaitingTasks`(AR-88)。无泄漏，但至多 `taskDispatchWaitTimeoutMs`(10min) stale WAITING load + 一个 scan 周期 stale SUSPICIOUS load 会报给 bestFit |

## 未测生命周期路径

| 路径 | gap 证据 |
|------|----------|
| 手动 fire 全 e2e（triggerNow → … → SUCCESS） | TestNopJobScheduleBizModel 测创建；无 dispatch→worker→completion 全链 |
| rerun fire 全 e2e | `testRerunFireCreatesRecoveryFireFromSourceSnapshots` 止于 WAITING 创建 |
| **重启恢复**：DISPATCHING fire + 无 task（coordinator 在 `tryLockFiresForDispatch` 与 `insertTasksAndMarkFireDispatching` 间崩溃） | timeout 测覆盖 DISPATCHING→TIMEOUT，但不测"coordinator 重启后 fire 恢复到 RUNNING" |
| `activeFireCount` 漂移收敛（P2-4） | 无测断言 cancelFire/complete 的 `fireOnly()` 后计数最终收敛 |
| reserved-capacity 生命周期清理（SUSPICIOUS→TIMEOUT 退出 sumReservedCost） | `testSumReservedCostExcludesTerminalStatuses` 静态（直插终态行）；无驱动 CLAIMED→SUSPICIOUS→TIMEOUT 后断言 sumReservedCost 归零 |
| BLOCK_STRATEGY_RECOVERY 复用 failed fire（`failedFires.get(0)` 分支） | `testRecoveryFireNoFailedFiresContainsAllFields` 仅测 no-failed 分支；reuse 分支（含 P2-7 死代码）无 store 级测 |
| schedule ARCHIVED 带活跃 fire | 无测断言 `activeFireCount>0` 时 archive 行为 |
| `allowResultCompletion` schedule-COMPLETED 并发活跃 fire（PARALLEL） | `JobCompletionProcessorImpl:189-191` 中途翻 COMPLETED；无测覆盖后续 sibling fire 对已 COMPLETED schedule 的完成 |

## 总结

**严重度分布**：1×P1（P1-1 长任务误杀）、3×P2（P2-5 cancelFire 契约分歧、P2-6 完成路径不对称、P2-7 recovery 死代码——另 6×P2 在 store/helper 报告）、10×P3。

**首要建议**：先修 **P1-1**（唯一能损坏合法生产执行的发现——长批任务无 per-schedule 超时时 5min 被静默杀）。其余为可维护性/可观测性/效率问题，得益于稳健的乐观锁+FSM 设计，happy path 无即时正确性影响。
