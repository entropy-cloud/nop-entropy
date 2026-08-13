# nop-job-dao 审计 — Store 层详细 findings

**审计源**：独立子 agent 深度审计（store layer），2026-08-12
**文件**：`IJobFireStore`/`IJobScheduleStore`/`IJobTaskStore` + `JobFireStoreImpl`/`JobScheduleStoreImpl`/`JobTaskStoreImpl` + `FireScheduleOutcome`/`WorkerReservedCost`/`NopJobTaskMapper`/`ReservedCostRow` + ORM `nop-job.orm.xml`

## Verdict

| File | Verdict |
|------|---------|
| `IJobFireStore.java` / `IJobScheduleStore.java` / `IJobTaskStore.java` | CORRECT |
| `JobFireStoreImpl.java` | ISSUES-FOUND（P2-1, P2-3, P2-4, P2-5, P2-6, P2-8） |
| `JobScheduleStoreImpl.java` | ISSUES-FOUND（P2-2, P2-4, P2-5, P2-7, P2-8） |
| `JobTaskStoreImpl.java` | ISSUES-FOUND（P2-1, P2-8） |
| `FireScheduleOutcome`/`WorkerReservedCost`/`NopJobTaskMapper`/`ReservedCostRow` | CORRECT |
| ORM `nop-job.orm.xml` | 索引 sane（minor 覆盖缺口 → P3 索引项） |

## Findings

| ID | Sev | File:line | Description | Evidence | One-line fix |
|----|-----|-----------|-------------|----------|--------------|
| P2-1a | P2 | `JobTaskStoreImpl.java:119-120`（修复前 110-111） | `fetchRunningTasks` orders ASC（`addOrderField(x,false)`），cursor 谓词用 `lt`，Javadoc 要求 DESC。ASC+lt 使批次 2 重复批次 1 → drainBatch 提前结束。 | mock `TestJobTimeoutChecker:946-948` 用 reverseOrder(DESC) | `false`→`true`（plan 340 §2.2 已修） |
| P2-1b | P2 | `JobTaskStoreImpl.java:182-183`（修复前 173-174） | `resetStaleWaitingTasks` 同款 ASC/DESC 反转（`createTime`/`jobTaskId`） | mock `TestJobTimeoutChecker:996-998` reverseOrder | `false`→`true`（已修） |
| P2-1c | P2 | `JobFireStoreImpl.java:255-256` | `fetchDispatchingFires` 同款反转（`startTime`/`jobFireId`） | Javadoc 要求 DESC | `false`→`true`（已修） |
| P2-2 | P2 | `JobScheduleStoreImpl.java:207-210` | `recoveryFireAndAdvanceSchedule` 在 fire 不可恢复时 return 不推进 `nextFireTime` → 5s 紧循环无退避 | `:207 isRecoverable false → :210 return` 跳过 `:229 updateScheduleWithRetry` | 不可恢复分支仍 call `updateScheduleWithRetry(...nextFireTime)` |
| P2-3 | P2 | `JobFireStoreImpl.java:291` | `failFireWithoutSchedule` 丢弃 `tryUpdateWithVersionCheck` 返回值，版本冲突时 fire 静默留 DISPATCHING，超时检查器无限重入 | `:291 fireDao().tryUpdateWithVersionCheck(fire);` 结果未检查（对比 `completeFireAndUpdateSchedule:128`） | 检查返回值，false 时 `LOG.warn` |
| P2-4 | P2 | `JobFireStoreImpl.java:139,205` | `activeFireCount` 计数漂移：fire 更新提交但 schedule 版本冲突→`fireOnly()`，计数不递减；`Math.max(0,…)` 防负不自愈，长期可阻塞 DISCARD/OVERLAY/RECOVERY。**@SingleSession 约束下路径内 reload 重试无意义**（见 `:132-136` 注释） | `:139`/`:205` fireOnly()；`JobPlannerScannerImpl:225-227` shouldDiscard 读 activeFireCount | 独立会话对账器周期重算（plan 340 §2.5） |
| P2-5 | P2 | `JobFireStoreImpl.cancelFire:146` vs `JobScheduleStoreImpl.cancelFire:417` | 两同名方法副作用分歧：public=fire+tasks+schedule 计数；private=仅 fire。overlay/manual 须手工组合 | `insertManualFire:255-263` 已手工组合证明 merge 更干净 | plan 340 §2.6 裁定 **rename**（`markFireCanceledOnly`）——拒绝 merge（REQUIRES_NEW 嵌套事务破坏原子性），详见 `store-contract-design.md` §2 |
| P2-6 | P2 | `JobCompletionProcessorImpl.java:110,122,199-200` | 完成路径 dirty-flush 冲突（OptimisticLockException）被外层 `catch(Exception):110` 吞为通用 warn，不可观测 | 超时路径 `:322` 显式分支处理 outcome，完成路径无 | 拆分 catch，冲突异常结构化 warn |
| P2-7 | P2 | `JobScheduleStoreImpl.java:196-204` | recovery 死代码：mutate `failedFire`(196-204) 后 reload `freshFire:205` 重设同字段，前者从不持久化 | `:202` failedFire.setJobParamsSnapshot → 从不 flush；`:218` freshFire 同字段 → flush | 删 196-204 块 |
| P2-8a | P2 | `JobScheduleStoreImpl.java:363-370` | `findActiveFires` 无 LIMIT，overlay/手动风暴无界，REQUIRES_NEW 事务内逐行 CAS | 对比 `findFailedFires:388` 有 `limit(1)` | `setLimit(500)` |
| P2-8b | P2 | `JobFireStoreImpl.java:306` / `JobTaskStoreImpl.java:116` | `findTasksByFireId` 无 LIMIT，广播 fire 任务数随健康实例数增长 | `findAllByQuery` 无 setLimit | `setLimit(1000)` |
| P3-a | P3 | `JobFireStoreImpl.java:86-99` | `tryLockFiresForDispatch` 参数 `lockTimeoutMs` 从不使用（status-based lock；startTime=now 非 now+lockTimeout）。对比 `tryLockSchedulesForPlan:75` 用了 | `:87` 参数声明；body 忽略 | 删参数（或文档化） |
| P3-b | P3 | ORM `nop-job.orm.xml:462-475` | `fetchRunningTasks` 按 startTime 排序但 task 索引仅 `(taskStatus,partitionIndex,createTime)`/`(taskStatus,workerInstanceId)` → filesort；`fetchDispatchingFires` 同（fire 索引第三键 scheduledFireTime 非 startTime） | 索引定义 `:333-337,463-467` | 加 `(taskStatus,partitionIndex,startTime,jobTaskId)` 索引（→ ORM plan） |
| P3-c | P3 | `JobFireStoreImpl.java:306-312` vs `JobTaskStoreImpl.java:116-122` | `findTasksByFireId` 重复实现（fire-store 私有 + task-store 公共），分歧风险 | 两者均 filter jobFireId + order taskNo,jobTaskId | fire-store 委托 task-store |
| P3-d | P3 | `JobFireStoreImpl.java:351-361` + ORM `:327-331` | `hasWaitingFire` 仅查 WAITING，但 UK `UK_NOP_JOB_FIRE_SCHEDULE_TIME_SOURCE` 跨全部状态；两 manual 同 ms 触发 bypass pre-check 后撞 UK 抛异常（非返回 false） | `:354` filter；UK `:328-330` | 捕获 UK 异常返回 false，或 widen pre-check |
| P3-e | P3 | `JobTaskStoreImpl.java:60-77` | `enforceAttribution=true && workerInstanceId==null` 静默跳过归因 → hostId 未设的 worker 看到所有 task | `:66 if (enforceAttribution && workerInstanceId != null)` | throw 或文档化（plan 340 裁定 throw） |

## 已验证为非问题（safe）

- `tryLockFiresForDispatch`/`tryLockTasksForExecute`/`tryLockSchedulesForPlan` version-column CAS 是正确的互斥（失败 CAS → entity 排除出返回列表），无需 `WHERE status=WAITING`。
- `DateHelper.durationMs(null,…)` 返回 null（非 NPE）——`cancelFire:158,172`/`failFireWithoutSchedule:288` 对 WAITING/DISPATCHING（startTime null）null-safe。
- `insertTasksAndMarkFireDispatching` 原子：fire CAS + task saves 共享一个 REQUIRES_NEW tx。
- `RESERVED_TASK_STATUSES`(WAITING+SUSPICIOUS+CLAIMED+RUNNING) vs `IN_FLIGHT_STATUSES`(CLAIMED+RUNNING) 差异是**有意且文档化**的。
- 时钟方向选择 deliberate：`fetchDueSchedules` 用 minCurrentTime（due 保守），`fetchWaitingFires` backoff 用 maxCurrentTime（skip 保守）。

## 未覆盖的 store 方法（测试 gap）

| 方法 | 风险 |
|------|------|
| `fetchDispatchingFires` 游标 | 高（P2-1c 无集成测试，且 mock 测试被 H2 遮蔽） |
| `fetchRunningTasks` 游标 | 高（`testFetchRunningTasksCursorPaginatesStrictly` 被 H2 遮蔽从未执行） |
| `tryLockTasksForExecute` 多 worker 竞争 | 高（version-CAS 保证仅推断未验证） |
| `revertDispatchingFireToWaiting` | 中（AR-86 路径仅间接覆盖） |
| `overlayFireAndAdvanceSchedule` | 中（cancel-loop + 计数 math 未测） |
| `insertManualFire` | 中（dedup + overlay-cancel 未测） |
| `failFireWithoutSchedule` 版本冲突路径 | 中（P2-3） |
