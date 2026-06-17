# 104 nop-job Round 4 Adversarial Review Remediation

> Plan Status: completed
> Last Reviewed: 2026-06-03
> Source: `ai-dev/audits/2026-06-03-adversarial-review-nop-job/01-open-findings.md` (Round 4: 6 new findings: AR-19~AR-24)
> Related: `ai-dev/plans/103-nop-job-adversarial-review-2026-06-03-remediation.md` (completed)

## Purpose

修复 2026-06-03 对抗性审查第 4 轮发现的 6 项新问题（2×P1 + 4×P2）。这些是 Plan 103 完成后的增量发现，聚焦于 AR-9 修复遗漏的对称路径（cancelFire、dispatch 状态转换）以及系统自愈能力不足。

## Current Baseline

- Plan 103 已完成，修复了 AR-1~AR-18 中的 13 项（5×P1 + 8×P2），5 项 P3 已 adjudicated 并 defer
- Round 4 验证确认 AR-1~AR-18 中 15/18 已修复，未修复项均为 P3（AR-6, AR-7, AR-14, AR-15, AR-16）和已知的 F9
- Round 4 新增 6 项发现（AR-19~AR-24）：
  - **AR-19 (P1)**: `cancelFire` 对 schedule 使用 `updateEntityDirectly` — AR-9 修复遗漏的对称路径
  - **AR-20 (P1)**: `insertTasksAndMarkFireDispatching` 使用 `updateEntityDirectly` 更新 fire 状态 — 可覆盖并发取消/超时的终态
  - **AR-21 (P2)**: `completeFireAndUpdateSchedule` 重试 3 次后降级为 `updateEntityDirectly` — 高并发计数器漂移
  - **AR-22 (P2)**: `NopJobScheduleBizModel.persistSchedule` 使用 `updateEntityDirectly` — 状态变更与引擎计数器竞态
  - **AR-23 (P2)**: `JobCompletionProcessorImpl` 加载 schedule 使用 `requireEntityById` — 删除后产生扫描毒丸
  - **AR-24 (P2)**: `NopRetryJobRetryBridge` 返回 `jobFireId` 而非 retry record ID — 已知 F9 仍存在
- `./mvnw clean test -pl nop-job -am` 当前基线为 BUILD SUCCESS

## Goals

- 修复 2 项 P1：AR-19（cancelFire schedule 版本检查）、AR-20（dispatch 状态机终态保护）
- 修复 4 项 P2：AR-21（重试降级策略）、AR-22（BizModel schedule 更新）、AR-23（schedule 删除防护）、AR-24（retryRecordId 返回值修正）
- 所有修复有对应测试覆盖

## Non-Goals

- 不修复已 adjudicated 的 P3 项（AR-6, AR-7, AR-14, AR-15, AR-16）— 仍在 Plan 103 的 Deferred But Adjudicated 中
- 不重新设计调度器架构或 ORM 模型
- 不处理 nop-retry 接口重新设计（F9 的根本解决方案）
- 不处理 deep-audit 中未在本次对抗性审查中重新出现的项

## Scope

### In Scope

- AR-19: `JobFireStoreImpl.cancelFire` 中 schedule 更新改用乐观锁 + 重试
- AR-20: `JobFireStoreImpl.insertTasksAndMarkFireDispatching` 中 fire 状态更新改用 `tryUpdateManyWithVersionCheck`
- AR-21: `JobFireStoreImpl.completeFireAndUpdateSchedule` 重试降级策略改进
- AR-22: `NopJobScheduleBizModel.persistSchedule` 改用选择性字段更新（SQL 级别 `updateByQuery` 只更新用户操作字段）
- AR-23: `JobCompletionProcessorImpl` 加载 schedule 改用 null-safe 加载，null 时强制完成 fire 为 FAILED
- AR-24: `NopRetryJobRetryBridge` 返回 `null` 而非 `jobFireId`（消除误导数据）
- 每项修复的测试覆盖

### Out Of Scope

- AR-6 (P3): parallel path setActiveFireCount(0) 死代码
- AR-7 (P3): maxFailedCount 硬编码为 0
- AR-14 (P3): copyMap 返回原始引用
- AR-15 (P3): findFirstErrorTask 错误优先级
- AR-16 (P3): RpcBroadcastTaskBuilder 不设置 taskPayload
- F9 根本解决方案（nop-retry 接口重新设计）
- F14 (P3): CONTINUE 命名冲突

## Execution Plan

### Phase 1 - P1 并发状态机安全修复（AR-19, AR-20）

Status: completed
Targets: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobFireStoreImpl.java`

- Item Types: `Fix`

- [x] AR-19: `cancelFire` 方法中 schedule 更新从 `scheduleDao().updateEntityDirectly(schedule)` 改为 `tryUpdateManyWithVersionCheck` + 重试循环。具体步骤：
  1. 在修改 schedule 计数器之前，先保存原始 `activeFireCount` 值（`int origActiveFireCount = schedule.getActiveFireCount()`）
  2. 修改 schedule 字段（`activeFireCount = max(activeFireCount - 1, 0)`、`lastEndTime`、`lastFireStatus`、条件性 `nextFireTime`、`updatedBy`、`updateTime`）
  3. 使用 `scheduleDao().tryUpdateManyWithVersionCheck` 提交更新
  4. 版本冲突时：从 DB 重新加载 fresh schedule，将 `activeFireCount` 设为 `fresh.getActiveFireCount() - 1`（从 fresh 值重新计算差值），保留 `lastEndTime`/`lastFireStatus`/`updatedBy`/`updateTime` 的当前设置值（这些是 cancel 语义，始终需要覆盖），条件性 `nextFireTime` 从 fresh schedule 重新判断 `shouldAdvanceFixedDelaySchedule` 后重新计算
  5. 重试最多 5 次
  - 差值合并字段：`activeFireCount`（需从 fresh 值重新计算 `-1`）
  - 直接覆盖字段：`lastEndTime`、`lastFireStatus`、`updatedBy`、`updateTime`（cancel 语义，始终覆盖）
  - 条件合并字段：`nextFireTime`（仅当 `shouldAdvanceFixedDelaySchedule` 从 fresh schedule 重新判断为 true 时才覆盖）
- [x] AR-20: `insertTasksAndMarkFireDispatching` 采用事务回滚策略：
  1. 在检查 fire 状态为 DISPATCHING 后，先尝试 `fireDao().tryUpdateManyWithVersionCheck(currentFire)`（将 `fireStatus` 设为 RUNNING）
  2. 版本冲突时直接抛出 `NopException`（错误码 `ERR_JOB_FIRE_STATUS_CONFLICT`），让 `REQUIRES_NEW` 事务回滚——已插入的 tasks 随事务一起回滚，不会产生孤儿
  3. 版本检查成功后再插入 tasks（`taskDao().saveEntityDirectly(task)`）——顺序调整为先更新 fire 状态再插入 tasks，确保 fire 更新成功后才写 tasks
  4. 调用方 `JobDispatcherScannerImpl` 已有 try-catch 处理异常，不影响扫描主循环
- [x] 为 AR-19 添加测试：模拟 cancel 与 completion 并发，验证 schedule 计数器不被覆盖
- [x] 为 AR-20 添加测试：模拟 dispatch 与 timeout 竞争，验证已超时 fire 不被复活为 RUNNING，且版本冲突时 tasks 未被插入

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `cancelFire` 对 schedule 使用乐观锁 + 重试（最多 5 次），`activeFireCount` 从 fresh 值重新计算差值，`lastEndTime`/`lastFireStatus`/`updatedBy`/`updateTime` 直接覆盖，`nextFireTime` 条件性覆盖
- [x] `insertTasksAndMarkFireDispatching` 对 fire 使用乐观锁，版本冲突时抛出异常（`REQUIRES_NEW` 事务回滚），终态 fire（TIMEOUT/CANCELED）不会被覆盖回 RUNNING，tasks 不会残留
- [x] `JobCoreErrors` 中新增 `ERR_JOB_FIRE_STATUS_CONFLICT` 错误码
- [x] 新增测试覆盖并发场景，测试通过
- [x] `./mvnw compile -pl nop-job -am` 通过
- [x] No owner-doc update required（内部并发安全修复，不改变公共 API 契约）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - P2 自愈能力与防护修复（AR-21, AR-23）

Status: completed
Targets: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobFireStoreImpl.java`, `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/IJobFireStore.java`, `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobScheduleStoreImpl.java`, `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/IJobScheduleStore.java`, `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobCompletionProcessorImpl.java`, `nop-job/nop-job-core/src/main/java/io/nop/job/core/JobCoreErrors.java`

- Item Types: `Fix`

- [x] AR-21: `completeFireAndUpdateSchedule` 重试降级策略改进 — 将重试次数从 3 增加到 5；降级时不再 `updateEntityDirectly`，改为抛出异常让调用方感知失败。调用方 `JobCompletionProcessorImpl.tryCompleteFireAndGetStatus` 的外层 `scanOnce` 已有 `try-catch(Exception)` 会捕获并记录错误，fire 会留在 RUNNING 状态，下次扫描会重试完成
- [x] AR-23: 分为 3 步：
  1. 在 `IJobScheduleStore` 接口新增 `NopJobSchedule tryLoadSchedule(String jobScheduleId)` 方法（返回 null 而非抛异常）
  2. 在 `JobScheduleStoreImpl` 实现该方法，使用 `scheduleDao().getEntityById()` 替代 `requireEntityById()`
  3. 在 `JobCompletionProcessorImpl` 中将 `scheduleStore.loadSchedule(fire.getJobScheduleId())` 改为 `scheduleStore.tryLoadSchedule(fire.getJobScheduleId())`；返回 null 时通过 `IJobFireStore` 新增的 `failFireWithoutSchedule(String jobFireId, String errorCode, String errorMessage)` 方法将 fire 强制标记为 FAILED（错误码 `ERR_JOB_SCHEDULE_DELETED`），跳过 schedule 计数器更新，记录 warn 日志。此方法绕过 `completeFireAndUpdateSchedule`（该方法要求 schedule 参数非 null），直接更新 fire 状态和结束时间
- [x] 在 `JobCoreErrors` 中新增 `ERR_JOB_SCHEDULE_DELETED` 错误码定义（位于 `nop-job/nop-job-core/src/main/java/io/nop/job/core/JobCoreErrors.java`）
- [x] 为 AR-21 添加测试：模拟持续版本冲突，验证 5 次重试后抛出异常而非静默覆盖
- [x] 为 AR-23 添加测试：模拟 schedule 被删除后 fire 被强制标记为 FAILED，`retryRecordId` 列为 NULL

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `completeFireAndUpdateSchedule` 在持续版本冲突时不再静默覆盖，改为抛出异常。行为变化：fire completion 从"尽力完成"变为"可能失败并在下次扫描重试"
- [x] `IJobScheduleStore` 新增 `tryLoadSchedule` 方法，`JobScheduleStoreImpl` 使用 `getEntityById` 实现
- [x] `IJobFireStore` 新增 `failFireWithoutSchedule` 方法，用于 schedule 不存在时直接将 fire 标记为 FAILED（不经过 `completeFireAndUpdateSchedule`）
- [x] `JobCoreErrors` 新增 `ERR_JOB_SCHEDULE_DELETED` 错误码
- [x] schedule 不存在时 fire 被强制标记为 FAILED（非 RUNNING 永久停留）
- [x] 新增测试覆盖，测试通过
- [x] `./mvnw compile -pl nop-job -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - P2 API 层防护与数据语义修复（AR-22, AR-24）

Status: completed
Targets: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java`, `nop-job/nop-job-retry-adapter/src/main/java/io/nop/job/retry/adapter/NopRetryJobRetryBridge.java`

- Item Types: `Fix`

- [x] AR-22: `NopJobScheduleBizModel.persistSchedule` 使用 SQL 级别选择性更新（Nop ORM 的 `OrmEntityDao.updateByQuery(QueryBean query, Map<String, Object> props)` 已验证支持选择性字段更新），只更新用户操作相关字段，不触碰引擎维护的计数器字段。仅更新以下字段：
  - 用户操作字段（允许覆盖）：`scheduleStatus`, `nextFireTime`
  - 引擎维护字段（不触碰）：`activeFireCount`, `fireCount`, `totalFireCount`, `successFireCount`, `failFireCount`, `lastFireTime`, `lastEndTime`, `lastFireStatus`, `lastDurationMs`
  - 通用字段（始终更新）：`updatedBy`, `updateTime`
  - 如果 Nop ORM 的 `updateByQuery` 在此场景不可用，退化为 `tryUpdateManyWithVersionCheck` + 重试，重试时从 fresh schedule 恢复上述引擎维护字段（从 fresh 复制 `activeFireCount`/`fireCount`/`totalFireCount`/`successFireCount`/`failFireCount`/`lastFireTime`/`lastEndTime`/`lastFireStatus`/`lastDurationMs`）
- [x] AR-24: `NopRetryJobRetryBridge.onFireFailed` 将 `return event.getJobFireId()` 改为 `return null`。`JobCompletionProcessorImpl:226-229` 中 `if (retryRecordId != null)` 分支不再进入，`fire.retryRecordId` 不会被设置，`updateRetryRecordId` 不会被调用——修复后 `retryRecordId` 列将为 NULL 而非误导性的 `jobFireId`
- [x] 为 AR-22 添加测试：模拟用户 disable 与 completion 并发，验证 `activeFireCount` 不被覆盖
- [x] 为 AR-24 添加测试：验证 `onFireFailed` 返回 `null`（而非 `jobFireId`），验证 `retryRecordId` 列为 NULL

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `persistSchedule` 使用选择性字段更新（或乐观锁 + 引擎字段保护），并发场景下不覆盖引擎维护的计数器字段
- [x] `NopRetryJobRetryBridge.onFireFailed` 返回 `null`，`retryRecordId` 列不再写入误导性的 `jobFireId`（修复后为 NULL）
- [x] 新增测试覆盖，测试通过
- [x] `./mvnw compile -pl nop-job -am` 通过
- [x] No owner-doc update required（AR-22 内部安全修复；AR-24 消除误导数据，不影响 API 契约）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 全部 2 项 P1 已修复（AR-19, AR-20）
- [x] 全部 4 项 P2 已修复（AR-21, AR-22, AR-23, AR-24）
- [x] 每项修复有对应测试覆盖
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] Anti-Hollow Check：closure audit 已验证组件间调用链在运行时确实连通，无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-job -am` 通过
- [x] `./mvnw test -pl nop-job -am` 通过
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### AR-6: Planner parallel path setActiveFireCount(0) 死代码

- Classification: `watch-only residual`
- Why Not Blocking Closure: 仅误导后续开发者，不影响运行时行为。Plan 103 已 adjudicated。
- Successor Required: no

### AR-7: maxFailedCount 硬编码为 0

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 需要 ORM 模型变更，属于 feature enhancement。Plan 103 已 adjudicated。
- Successor Required: yes

### AR-14: copyMap 返回原始引用

- Classification: `optimization candidate`
- Why Not Blocking Closure: 实际影响极低。Plan 103 已 adjudicated。
- Successor Required: no

### AR-15: findFirstErrorTask 错误优先级

- Classification: `optimization candidate`
- Why Not Blocking Closure: 仅影响广播场景 errorCode 准确性。Plan 103 已 adjudicated。
- Successor Required: no

### AR-16: RpcBroadcastTaskBuilder 不设置 taskPayload

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 运行时从 fire 的 jobParamsSnapshot 读取，不依赖 taskPayload。Plan 103 已 adjudicated。
- Successor Required: no

### F14: CONTINUE 命名冲突

- Classification: `optimization candidate`
- Why Not Blocking Closure: 编译和运行时无歧义。Plan 103 已 adjudicated。
- Successor Required: no

## Non-Blocking Follow-ups

- 考虑为 `maxFailedCount` 添加 ORM 列和配置入口（AR-7）
- 考虑重新设计 nop-retry 回调接口以支持同步获取真实 retryRecordId（F9 根本解决方案）
- 清理 parallel path 死代码 `setActiveFireCount(0)`（AR-6）
- 考虑 `copyMap` 重命名或改为真正深拷贝（AR-14）
- 考虑 `findFirstErrorTask` 优先级与 `resolveFinalFireStatus` 对齐（AR-15）
- 考虑广播 task 设置 taskPayload（AR-16）

## Closure

Status Note: All 6 findings (AR-19~AR-24) have been fixed with tests. `./mvnw test -pl nop-job -am` passes.

Closure Audit Evidence:

- Reviewer / Agent: Independent sub-agent (ses_172802c4dffexoE593eARCEzw1)
- Audit Session: 2026-06-03 closure audit
- Evidence:
  - AR-19: PASS — `cancelFire` uses `tryUpdateManyWithVersionCheck` with 5 retries, `activeFireCount` from fresh value. Test `testCancelFireUsesOptimisticLockOnSchedule` verifies.
  - AR-20: PASS — `insertTasksAndMarkFireDispatching` version checks before inserting tasks, throws `NopException` on conflict. Tests `testDispatchTimeoutFireReturnsEarlyWithoutTasks` and `testDispatchCanceledFireReturnsEarlyWithoutTasks` verify.
  - AR-21: PASS — `completeFireAndUpdateSchedule` retries 5 times, throws on exhaustion. Test `testCompleteFireThrowsOnScheduleVersionConflict` verifies.
  - AR-22: PASS — `persistSchedule` uses `tryUpdateManyWithVersionCheck` with `restoreEngineFields`. Test `testDisableSchedulePreservesEngineCountersOnVersionConflict` verifies.
  - AR-23: PASS — `tryLoadSchedule` returns null safely, `failFireWithoutSchedule` marks fire FAILED. Test `testScheduleDeleted_fireMarkedFailed` verifies.
  - AR-24: PASS — `onFireFailed` returns `null`. Test `testOnFireFailed_submitsRetryTask` verifies return is null.
  - `node ai-dev/tools/check-plan-checklist.mjs` exit code: 0
  - Anti-Hollow check: All call chains connected. `cancelFire` → `scheduleDao().tryUpdateManyWithVersionCheck`. `insertTasksAndMarkFireDispatching` → `fireDao().tryUpdateManyWithVersionCheck` → `taskDao().saveEntityDirectly`. `completeFireAndUpdateSchedule` → `scheduleDao().tryUpdateManyWithVersionCheck` or throws. `tryCompleteFireAndGetStatus` → `scheduleStore.tryLoadSchedule()` → `fireStore.failFireWithoutSchedule()`. `persistSchedule` → `daoProvider().daoFor().tryUpdateManyWithVersionCheck()`. No empty method bodies or silent no-ops.
  - Deferred items check: All deferred items (AR-6, AR-7, AR-14, AR-15, AR-16, F14) are P3 watch-only/optimization/out-of-scope — no in-scope live defects downgraded.

Follow-up:

- no remaining plan-owned work
