# nop-job-dao 审计 — FSM / Helper 层详细 findings

**审计源**：独立子 agent 深度审计（state machine / helper layer），2026-08-12
**文件**：`JobFireStateMachine`/`JobTaskStateMachine`/`JobScheduleStateMachine`/`TriggerSpecHelper`/`JobQueryHelper` + `_NopJobCoreConstants`

## 状态常量清单（`_NopJobCoreConstants`）

| Domain | Value | Constant |
|--------|-------|----------|
| schedule | 0/10/20/30/40 | DISABLED/ENABLED/PAUSED/COMPLETED/ARCHIVED |
| fire | 0/10/20 | WAITING/DISPATCHING/RUNNING |
| fire | 30/40/50/60 | SUCCESS/FAILED/TIMEOUT/CANCELED |
| task | 0/10/15/20 | WAITING/CLAIMED/SUSPICIOUS/RUNNING |
| task | 30/40/50/60 | SUCCESS/FAILED/TIMEOUT/CANCELED |

**Finished-condition ordering 确认**：WAITING(0)<DISPATCHING(10)<RUNNING(20)<SUCCESS(30)<FAILED(40)<TIMEOUT(50)<CANCELED(60)。`isTerminal = status >= 30`（`JobFireStateMachine.java:54`）对闭域精确（所有终态≥30，所有 active<30，无 21-29 间隙）。

## 语义迁移漂移核对（迁移 commit `df8d93f5f`）— **零漂移，9 处全部一致**

| 迁移点 | 迁移前 | 迁移后 | 结论 |
|--------|--------|--------|------|
| `DefaultJobCancelHandler.java:117` | `status != null && == RUNNING` | `isRunning`（同表达式） | 一致 |
| `DefaultJobExecutionContextBuilder.java:101` | 同上 | `isRunning` | 一致 |
| `TriggerSpecHelper.java:72` | `status != null && == COMPLETED` | `isCompleted` | 一致 |
| `JobFireStoreImpl.java:106,264` | `status == null \|\| != DISPATCHING` | `!isDispatching` | 一致（双重否定） |
| `JobScheduleStoreImpl.java:207` | `null \|\| (!=FAILED && !=TIMEOUT)` | `!isRecoverable` | 一致 |
| `JobScheduleStoreImpl.java:272` | `!= null && == CANCELED` | `isCanceled` | 一致 |
| `JobScheduleStoreImpl.java:420` | 4 成员精确终态集（30/40/50/60） | `isTerminal`(≥30) | 闭域内一致（仅对未定义值≥30 扩大） |
| `JobTaskStoreImpl.java:99` | `in {RUNNING,CLAIMED,SUSPICIOUS}` | `RUNNING_LIKE_STATUSES`（同 3） | 一致 |
| `JobTaskStoreImpl.java:133` | `in {RUNNING,CLAIMED}` | `IN_FLIGHT_STATUSES`（同 2） | 一致 |

Integer `==` 语义：每个谓词比较 `Integer` 字段与 `int` 常量 → 自动拆箱、值比较，无 `Integer == Integer` identity 陷阱。null 处理每处均保留（谓词均 null-guard）。

## Verdict

| Helper | Verdict |
|--------|---------|
| `JobFireStateMachine` | CORRECT（仅 P3 观察） |
| `JobTaskStateMachine` | CORRECT（仅 P3 观察） |
| `JobScheduleStateMachine` | CORRECT |
| `TriggerSpecHelper` | ISSUES-FOUND（1×P2 即 plan P2-9，2×P3） |
| `JobQueryHelper` | CORRECT（薄封装 `QueryBean.addPartitionFilter`，null/empty no-op 已单测） |

## Findings

| ID | Sev | File:line | Description | Evidence | One-line fix |
|----|-----|-----------|-------------|----------|--------------|
| P2-9 | P2 | `TriggerSpecHelper.java:46-48` | `getLastScheduledTime()` = `toTime(lastFireTime)`，从未触发的 schedule 返回 **0**（非负）；`PeriodicTrigger:51-67` 仅在 `start<0` 时 fallback `minScheduleTime` → 该分支死代码；首次 fixed-rate fire 相位锚到 epoch 0，skip 后 `lastFireTime` stale 致网格漂移 | 预存在（`ae7609d77`/`54f708264` 一致） | `lastFireTime==null` 返回 `-1`，或显式 `minScheduleTime` 锚定（plan 340 §2.10 含消费方核对） |
| P3-f | P3 | `JobFireStateMachine.java:46,54` | 范围 `isActive`/`isTerminal` 把**未定义值**≥30 归为终态。今天安全（闭域），但未来插入 (20,30)/(30,60) 状态会静默误分类 | `JobScheduleStoreImpl.java:417-421` vs 迁移前 | javadoc 标注闭域不变式 |
| P3-g | P3 | `JobFireStateMachine.java:173-179` | `resolveFinalStatus` 静默忽略未知 task status（非 pending/SUSPICIOUS/TIMEOUT/FAILED/CANCELED）→ 损坏/陈旧值产出 fire SUCCESS | else-if 链无 final 分支 | 未知值视为 pending（`hasPendingTask=true`） |
| P3-h | P3 | `JobFireStateMachine.java:157,199` | `resolveFinalStatus(List.of())` → SUCCESS（空 list 无 task=成功）。生产由 `completeSingleFire:129-130` 早返回保护，但 API 危险 | 测试未覆盖空 list | 空 list 返回 null 或文档化 |
| P3-i | P3 | `JobFireStateMachine.java:121-123` | `canRerun` **零生产调用方**——`NopJobFireBizModel.rerunFire:99` 直接用 `isTerminal` | 仅测试调用 | BizModel 改用 canRerun 或删 |
| P3-j | P3 | `JobCompletionProcessorImpl.java:183-187,202-210` | CANCELED fire 落入 else → `onFireFailure` + `retryBridge.onFireFailed` → CANCELED 可触发重试策略；CANCELED 还自增 `schedule.failFireCount`。预存在（2026-07-31 verified） | `:207-209`；`findMatchingErrorTask` 二次遍历用 `!isSuccess` | CANCELED 显式路由（skip 重试/告警，单独计数） |
| P3-k | P3 | `JobScheduleStoreImpl.java:417-420` vs `JobFireStoreImpl.java:314-318` | 两 cancel 入口分歧：biz-cancel 用 `canCancel(RUNNING, hasUnfinishedTask)`（拒绝 cancel 任务全完成的 RUNNING fire）；overlay-cancel 仅 `isTerminal` 短路。overlay 可在 completion processor 终结前把刚完成的 RUNNING fire 翻成 CANCELED → fire=CANCELED 但 task 全 SUCCESS | `JobFireStoreImpl.cancelFire:149`(biz) vs `JobScheduleStoreImpl.cancelFire:420`(overlay) | overlay 路径复用 `canCancel(fireStatus, hasUnfinishedTask)` |
| P3-l | P3 | `TriggerSpecHelper.java:77-82` | `parsePauseCalendars` 裸调 `JsonTool.parseBeanFromText`——损坏的 `pauseCalendarSpec` 在 trigger-eval 路径运行时抛 | 仅 empty/null guard | try/catch → warn + emptyList() |
| P3-m | P3 | `TriggerSpecHelper.java:34` | `maxFailedCount=0` 硬编码；无 schedule 字段被读（与 `LocalJobScheduler:520`/`DefaultJobCancelHandler:137-139` 一致，0=disabled） | grep `getMaxFailedCount` 仅此 3 处 | 文档化 0=unlimited |

## 裸状态比较 bypass helper 清单（消费者侧）

生产中剩余 `_NopJobCoreConstants` 裸用均为**查询 filter 或写**，非谓词逻辑：

1. **查询 filter（不可避免，非谓词可用）**：`JobFireStoreImpl:58,77`（eq WAITING/RUNNING）、`JobScheduleStoreImpl:57,354`（eq ENABLED/WAITING）、`JobTaskStoreImpl:65`（eq WAITING）、`:140,151` + `NopJobTaskMapper`（`RESERVED_TASK_STATUSES`——刻意独立于 FSM，差异在 `JobTaskStateMachine.java:34-35` 文档化）。
2. **状态写（非比较）**：worker `JobWorkerScannerImpl:246,375`；timeout `JobTimeoutCheckerImpl:273,297,309,317,348,402,475`；completion `JobCompletionProcessorImpl:144,190`；stores `JobFireStoreImpl:95,110,156,170,267,286`；`tryLockTasksForExecute:87`；BizModels。
3. **测试断言（允许）**：`TestJobTimeoutChecker:706,758,867,881`、`TestJobE2E:266`。
4. **非状态常量**：block-strategy switch、trigger-type checks、`TRIGGER_SOURCE_*`——FSM 范围外。

**无生产谓词 bypass 残留**。Coordinator/worker/dao 主源 100% 迁移。

## FSM 完备性

- Fire：所有迁移由 `canCancel`（WAITING/DISPATCHING 无条件，RUNNING 门控未完成 task）、`canRerun`=isTerminal、`isRecoverable`=FAILED/TIMEOUT、`mapFireToTaskStatus`（映射 3 个负终态）覆盖。消费者 guard 一致。
- Task：`isPending`/`isFinished` 对所有 8 定义值严格互补（SUSPICIOUS 刻意对 cancel 流"finished"、对 recovery"resettable"——均 `JobTaskStateMachine:113-117,133-140` 文档化）；`isInFlight` 窄于 `RUNNING_LIKE_STATUSES`——三集合刻意区分（`:29-52`）。
- 聚合：`resolveFinalStatus` 优先级 TIMEOUT>FAILED>CANCELED>SUCCESS，SUSPICIOUS→TIMEOUT 仅当无 pending——与文档一致；注意聚合排序**非数值序**（CANCELED=60 排最后）——`:146-149` 文档化。
- 未用谓词：`isWaiting/isRunning/isFailed`(fire)、`isWaiting/isFailed/isCanceled`(task)、`canRerun`——无生产调用方（closed predicate API；仅 `canRerun` 是 dead convenience alias）。

## 覆盖 gap

- **`TriggerSpecHelper` 零直接单测**（cron/once/fixed-rate/fixed-delay 映射、null 字段、pause-calendar parse、边界 `repeatInterval=0`、misfire threshold、P2-9 行为）。
- `resolveFinalStatus`：空 list（P3-h）、SUSPICIOUS+FAILED/SUSPICIOUS+CANCELED 组合未测；null 混入仅单 null list 测过。
- `fetchDispatchingFires` 游标分页无 focused 测试。
- 未定义值行为（status 25/999）两 FSM 均未测（关联 P3-f/P3-g）。
- `JobScheduleStateMachine`：`isDisabled(null)/isPaused(null)/isCompleted(null)` 未测（仅 isEnabled/isArchived null 测过）——trivial。
- 所有定义域迁移**已测**（每谓词 × 全部 8/7/5 状态含 task/fire 的 null；schedule `canXxx` 全覆盖）。`isFinished`↔`isPending` 互补性穷尽断言（`TestJobTaskStateMachine:123-138`）；`mapFireToTaskStatus` 正负覆盖。

**结论**：迁移语义干净（零漂移），FSM 内部自洽，null 输入统一安全，唯一实质（预存在 P2）问题是 `TriggerSpecHelper` 的 fixed-rate 相位锚定（P2-9）。
