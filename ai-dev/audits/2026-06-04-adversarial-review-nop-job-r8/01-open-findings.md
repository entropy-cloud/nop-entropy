# Adversarial Review: nop-job Module (Round 8)

**Date**: 2026-06-04
**Scope**: nop-job module — independent re-examination after 7 prior rounds (AR-1~AR-53), focused on previously unaudited code paths
**Approach**: Discovery-oriented, starting from Calendar exclusion logic, BizModel API surface, trigger calculation correctness, and recovery path completeness. Four parallel deep explorations of coordinator, worker/dao/service, core/api/retry, and config/model layers.

**Heuristics used**: 模型攻击者 (Calendar spec → trigger interaction), 10x规模运维 (batch processing, scale limits), 异常路径侦探 (recovery, partial failure), GraphQL 契约考古 (BizModel CRUD exposure), 未来破坏者 (API contract stability)

**Dedup**: Prior audits AR-1~AR-53 covered: startTime semantics, recovery fire field population, insertManualFire overlay, retryRecordId dead write, CLAIMED task orphans, SUSPICIOUS grace period, cancelFire schedule version, dispatch timeout, concurrency races on fire/task, copyMap reference (AR-14), findFirstErrorTask priority (AR-15), RpcBroadcastTaskBuilder taskPayload (AR-16), maxFailedCount (AR-7), CronExpression GC/equals (AR-43/45), completeTaskWithFailure return (AR-53), handleExecutionResult version conflict (AR-53). This report focuses on Calendar/trigger logic, BizModel API protection, and recovery completeness — areas with minimal prior coverage.

---

## New Findings

### [AR-54] `DailyCalendarSpec` default end time `LocalTime.of(24, 0, 0)` throws DateTimeException — crash on any DailyCalendarSpec with start but no end

- **文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/calendar/CalendarBuilder.java:80`
- **证据片段**:
  ```java
  // CalendarBuilder.java:76-81
  if (start == null)
      start = LocalTime.of(0, 0);

  if (end == null) {
      end = LocalTime.of(24, 0, 0);  // CRASH: hour must be 0-23
  }
  ```
- **严重程度**: P0
- **现状**: `LocalTime.of(24, 0, 0)` 抛出 `java.time.DateTimeException: Invalid value for HourOfDay (valid values 0 - 23): 24`。当代码路径到达 `DailyCalendarSpec` 提供了 `start` 但 `end` 为 null 时触发。此路径在 `TriggerBuilder.buildTrigger` 中触发，而后者在 `JobTriggerCalculator.calculateNextFireTime` 和 `LocalJobScheduler.addJob` 中被调用。任何配置了每日排除时段只指定了开始时间、未指定结束时间的任务，在注册时直接崩溃。
- **风险**: 用户通过 API/GraphQL 配置 `pauseCalendars` 包含一个 `DailyCalendarSpec{start: "09:00"}` 时，系统在 trigger 构建阶段立即崩溃，任务无法注册。崩溃信息 `DateTimeException` 对用户不友好，难以定位到配置问题。
- **建议**: 改为 `LocalTime.MAX`（`23:59:59.999999999`）或直接计算到次日 00:00 的毫秒值，绕过 `LocalTime` 的范围限制。
- **信心水平**: 确定
- **发现来源视角**: 模型攻击者（构造极端 CalendarSpec 输入）

---

### [AR-55] `WeeklyCalendarSpec` ISO 8601 到 `java.util.Calendar` 映射不一致 — 静默排除错误的星期几

- **文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/calendar/CalendarBuilder.java:63-67`
- **证据片段**:
  ```java
  // WeeklyCalendarSpec.java:17-21 — API 文档
  /**
   * 从1到7表示星期一到星期天。
   * 按照国际标准 ISO 8601 ...
   */
  private int[] excludes;

  // CalendarBuilder.java:63-67 — 无转换
  WeeklyCalendar weekly = new WeeklyCalendar(cal);
  for (int day : spec.getExcludes()) {
      weekly.setDayExcluded(day, true);  // 直接传 ISO 值
  }

  // WeeklyCalendar.java:104-106 — 使用 java.util.Calendar 常量作为数组索引
  public void setDayExcluded(int wday, boolean exclude) {
      excludeDays[wday] = exclude;  // Calendar.SUNDAY=1, Calendar.MONDAY=2, ..., Calendar.SATURDAY=7
  }
  ```
- **严重程度**: P1
- **现状**: `WeeklyCalendarSpec.excludes` 按 ISO 8601 编码（1=周一, 7=周日），但 `WeeklyCalendar` 的内部数组使用 `java.util.Calendar` 常量（1=周日, 2=周一, ..., 7=周六）作为索引。`CalendarBuilder` 传入值时未做转换。结果是系统性地排除错误的星期几：

  | 用户意图 (ISO) | 传入值 | 实际排除的 Calendar 日 |
  |---|---|---|
  | 周一 (1) | 1 | 周日 (SUNDAY=1) |
  | 周二 (2) | 2 | 周一 (MONDAY=2) |
  | 周六 (6) | 6 | 周五 (FRIDAY=6) |
  | 周日 (7) | 7 | 周六 (SATURDAY=7) |

  每个值都偏移了一天，且方向错误。用户配置"排除周末 `[6,7]`"实际排除的是周五和周六。
- **风险**: 所有使用 `WeeklyCalendarSpec` 的任务静默排除错误的星期几。排障困难——配置看起来正确，行为看似合理（某两天确实被排除了），但排除的是错误的两天。
- **建议**: 在 `CalendarBuilder` 中添加 ISO-to-Calendar 转换：`calendarDay = (isoDay % 7) + 1`。或在 `WeeklyCalendarSpec` 中改用 Calendar 常量编码并更新文档。
- **信心水平**: 确定
- **发现来源视角**: 模型攻击者（追踪用户输入到内部数据结构的映射链）

---

### [AR-56] `DailyCalendar.isTimeIncluded` 在非反转模式下排除午夜 — 与 `PauseCalendarTrigger` 组合导致无限循环

- **文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/calendar/DailyCalendar.java:209-211`
- **证据片段**:
  ```java
  // DailyCalendar.java:209-211 — 非反转模式
  if (!invertTimeRange) {
      return ((timeInMillis > startOfDayInMillis && timeInMillis < timeRangeStartingTimeInMillis)
              || (timeInMillis > timeRangeEndingTimeInMillis && timeInMillis < endOfDayInMillis));
  }
  // timeInMillis > startOfDayInMillis 是严格不等式
  // 午夜 (00:00:00.000) 等于 startOfDayInMillis，因此被排除
  ```
  与 `PauseCalendarTrigger` 的交互：
  ```java
  // PauseCalendarTrigger.java:33-44
  long time = trigger.nextScheduleTime(afterTime, evalContext);  // 返回午夜
  while (time > 0 && !calendar.isTimeIncluded(time)) {            // 午夜被排除!
      time = calendar.getNextIncludedTime(time);                   // 返回午夜+1ms
      if (time > 0) {
          time = trigger.nextScheduleTime(time - 1, evalContext);  // time-1 又回到午夜
      }
      count++;
      if (count > MAX_TRY_COUNT)
          throw new NopException(...);  // 10000 次后抛异常
  }
  ```
- **严重程度**: P1
- **现状**: 非反转模式下，`isTimeIncluded` 使用 `timeInMillis > startOfDayInMillis`（严格不等式）。午夜（00:00:00.000）恰好等于 `startOfDayInMillis`，因此始终被排除，无论配置的排除时段是什么。对于 cron `"0 0 0 * * *"`（每天午夜）加上任何 DailyCalendar（即使是排除 8AM-5PM 的时段），PauseCalendarTrigger 的循环变为：trigger 返回午夜 → 午夜被排除 → getNextIncludedTime 返回午夜+1ms → trigger.nextScheduleTime(午夜) 返回下一天午夜 → 下一天午夜被排除 → 循环重复。每次迭代推进一天，10000 次后抛出 `ERR_JOB_TRIGGER_LOOP_COUNT_EXCEED_LIMIT`。**任务永远无法触发。**
- **风险**: 任何在午夜触发的 cron 任务配合任何 DailyCalendar 排除配置，任务永远不执行并最终报错。此问题不仅限于 `"0 0 0 * * *"`，任何在 startOfDay 边界对齐的触发时间都受影响。
- **建议**: 将 `timeInMillis > startOfDayInMillis` 改为 `timeInMillis >= startOfDayInMillis`（使用 `>=`），或在 `getNextIncludedTime` 中将恰好等于 startOfDay 的时间视为包含。
- **信心水平**: 确定
- **发现来源视角**: 组合爆炸测试（DailyCalendar + PauseCalendarTrigger + 午夜 cron）

---

### [AR-57] `shouldRecovery` 不检查 `activeFireCount > 0` — 无活跃 fire 时 RECOVERY 路径仍被触发

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobPlannerScannerImpl.java:157-159,244-247`
- **证据片段**:
  ```java
  // JobPlannerScannerImpl.java:232-247
  private boolean shouldDiscard(NopJobSchedule schedule) {
      return defaultInt(schedule.getActiveFireCount()) > 0        // ← 有 activeFireCount 守卫
              && schedule.getBlockStrategy() == BLOCK_STRATEGY_DISCARD;
  }
  private boolean shouldOverlay(NopJobSchedule schedule) {
      return defaultInt(schedule.getActiveFireCount()) > 0        // ← 有 activeFireCount 守卫
              && schedule.getBlockStrategy() == BLOCK_STRATEGY_OVERLAY;
  }
  private boolean shouldRecovery(NopJobSchedule schedule) {
      return schedule.getBlockStrategy() != null
              && schedule.getBlockStrategy() == BLOCK_STRATEGY_RECOVERY;
      // ← 无 activeFireCount 守卫!
  }
  ```
- **严重程度**: P1
- **现状**: `shouldDiscard` 和 `shouldOverlay` 都在 `activeFireCount > 0` 时才返回 true（没有活跃 fire 时没有必要丢弃或覆盖）。但 `shouldRecovery` 不检查 `activeFireCount`。当 `blockStrategy == RECOVERY` 且 `activeFireCount == 0`（首次触发或 fire 已完成），planner 调用 `recoveryFireAndAdvanceSchedule`（line 158）并 **跳过正常的 fire 创建路径**（`continue` 在 line 159）。

  `recoveryFireAndAdvanceSchedule` 的行为取决于是否有 failed fires。如果没有 failed fires，它创建一个新 fire（R2-12 修复路径）。如果有 failed fires，它重用旧 fire。两种情况下，它都绕过了 `insertFireAndAdvanceSchedule` 的 `hasWaitingFire` 去重检查。更关键的是：当 schedule 状态完全正常（无活跃 fire、无失败 fire），进入 recovery 路径本身就是语义错误——没有需要恢复的东西。
- **风险**: RECOVERY 策略的 schedule 在正常状态下总是进入 recovery 路径而非正常 fire 创建路径，可能产生微妙的差异（缺少去重检查、不同的字段初始化路径）。在某些边界条件下，可能导致 fire 丢失。
- **建议**: 在 `shouldRecovery` 中添加 `defaultInt(schedule.getActiveFireCount()) > 0` 守卫，与 `shouldDiscard` 和 `shouldOverlay` 保持一致。当 `activeFireCount == 0` 时，应走正常的 fire 创建路径（line 162-176 的默认路径）。
- **信心水平**: 确定
- **发现来源视角**: 10x规模运维（block strategy 一致性审计）

---

### [AR-58] `resolveCompletionDecision` 中 `JsonTool.parseMap(resultPayload)` 抛异常 — 单个畸形 task 结果中止整个完成扫描批次

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobCompletionProcessorImpl.java:283`
- **证据片段**:
  ```java
  // JobCompletionProcessorImpl.java:283 — resolveCompletionDecision 内部
  String resultPayload = task.getResultPayload();
  Map<String, Object> payload = JsonTool.parseMap(resultPayload);  // 可抛异常

  // 调用链: tryCompleteFireAndGetStatus (line 172) → scanOnce 的 for 循环 (line 137-144)
  for (NopJobFire fire : fires) {
      if (tryCompleteFireAndGetStatus(fire) != null) {
          completedCount++;
      }
  }
  // 异常被 scanOnce 的 catch (line 145-147) 捕获:
  } catch (Exception e) {
      LOG.error("nop.job.completion.scan-failed", e);
  }
  ```
- **严重程度**: P1
- **现状**: `resultPayload` 是 worker 执行结果的任意字符串（用户可控数据）。如果某个 task 的 `resultPayload` 包含畸形 JSON（如 invoker 返回非 JSON 字符串），`JsonTool.parseMap` 抛出异常，传播到 `scanOnce` 的最外层 catch，导致整个扫描批次中止。所有后续 fires 在当前周期不被处理。由于 `resultPayload` 来自 worker 执行输出，这是外部可触发的。
- **风险**: 一个畸形 task 结果可以阻塞整个 completion processor 的扫描进度。在 10x 规模下，batch 中的 fires 数量更大，影响范围更广。operator 看到大量 fires 卡在 RUNNING 状态直到下一个扫描周期。
- **建议**: 在 `resolveCompletionDecision` 中对 `JsonTool.parseMap` 包裹 try-catch，解析失败时将 payload 视为空 Map 并记录 WARN 日志。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探（追踪用户可控数据通过 completion 管线的错误传播）

---

### [AR-59] `persistSchedule` 乐观锁耗尽后 force-update — 可覆盖并发引擎计数器

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java:146-161`
- **证据片段**:
  ```java
  // NopJobScheduleBizModel.java:146-161
  for (int attempt = 0; attempt < 5; attempt++) {
      List<NopJobSchedule> updated = ormDao.tryUpdateManyWithVersionCheck(
              Collections.singletonList(schedule));
      if (!updated.isEmpty()) {
          afterEntityChange(schedule, action, context);
          return;
      }
      NopJobSchedule fresh = ormDao.requireEntityById(schedule.getJobScheduleId());
      schedule.setVersion(fresh.getVersion());
      restoreEngineFields(schedule, fresh);  // 从 fresh 复制引擎字段
  }
  // 5 次重试全部失败 — 绕过版本检查!
  LOG.warn("nop.job.schedule.persist-optimistic-lock-exhausted:scheduleId={}", schedule.getJobScheduleId());
  ormDao.updateEntityDirectly(schedule);  // 无条件覆盖所有字段
  ```
- **严重程度**: P1
- **现状**: `restoreEngineFields` 在每次重试时从最新 DB 状态复制引擎字段（`activeFireCount`, `fireCount`, `totalFireCount` 等）。但 5 次重试全部失败后，`updateEntityDirectly` 使用的是第 5 次 `requireEntityById` 读取的状态。在这次读取和实际写入之间，并发的 planner/completion/cancel 可能修改了引擎计数器。`updateEntityDirectly` 无条件覆盖，丢弃这些并发更新。

  这在高并发场景下尤其危险：planner 频繁 increment `activeFireCount`/`fireCount`，completion processor 频繁 increment `totalFireCount`/`successFireCount`，cancel 频繁 decrement `activeFireCount`。5 次 version conflict 说明并发程度极高，force-update 正好在这种场景下破坏数据。
- **风险**: 在高并发 schedule 操作下（频繁 enable/disable + 并发 planner/completion），引擎计数器被覆盖导致漂移。Dashboard 统计不准确，`activeFireCount` 可能错误归零，导致 BLOCK 策略失效。
- **建议**: (a) 不做 force-update，而是抛出异常让调用者重试。(b) 或在 force-update 前再次读取最新状态并只更新非引擎字段（用户可编辑字段如 `scheduleStatus`, `jobName`, `cronExpr` 等）。
- **信心水平**: 确定
- **发现来源视角**: 10x规模运维（高并发下乐观锁耗尽的降级行为）

---

### [AR-60] `enableSchedule` 保留过时的 `nextFireTime` — 导致重新启用时立即意外触发

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java:59-67`
- **证据片段**:
  ```java
  // NopJobScheduleBizModel.java:59-67
  public void enableSchedule(@Name("id") String id, IServiceContext context) {
      NopJobSchedule schedule = requireEntity(id, "enableSchedule", context);
      validateScheduleStatus(schedule, "enableSchedule", _NopJobCoreConstants.SCHEDULE_STATUS_DISABLED);
      schedule.setScheduleStatus(_NopJobCoreConstants.SCHEDULE_STATUS_ENABLED);
      if (schedule.getNextFireTime() == null) {       // 仅在 null 时重新计算
          schedule.setNextFireTime(recalculateNextFireTime(schedule));
      }
      persistSchedule(schedule, "enableSchedule", context);
  }
  ```
  对比 `resumeSchedule`（总是重新计算）：
  ```java
  // NopJobScheduleBizModel.java:100-103
  public void resumeSchedule(@Name("id") String id, IServiceContext context) {
      // ...
      schedule.setNextFireTime(recalculateNextFireTime(schedule));  // 总是重新计算
  }
  ```
- **严重程度**: P2
- **现状**: 重新启用一个先前被禁用的 schedule 时，`nextFireTime` 仅在 null 时才重新计算。如果 schedule 被禁用时的 `nextFireTime` 已经过去，重新启用会保留这个过时时间。下次 planner 扫描时，`fetchDueSchedules` 会立即匹配此 schedule（`nextFireTime <= now`），导致意外的立即触发。`resumeSchedule`（用于暂停恢复）总是重新计算，行为不一致。
- **风险**: 用户 disable 一个 schedule，几天后 re-enable，期望按正常调度时间执行，但实际立即触发一次。如果 disable 期间的 `nextFireTime` 是很久以前的时间，没有实际问题（立即触发后恢复正常），但用户行为预期不一致。
- **建议**: 在 `enableSchedule` 中总是重新计算 `nextFireTime`（与 `resumeSchedule` 一致），或至少检查 `nextFireTime` 是否在过去并重新计算。
- **信心水平**: 确定
- **发现来源视角**: 未来破坏者（enable/disable 生命周期完整性）

---

### [AR-61] `resetFailedTasks` 跳过 SUSPICIOUS 状态的任务 — recovery 无法恢复 worker 崩溃导致的 SUSPICIOUS 任务

- **文件**: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobScheduleStoreImpl.java:505-510`
- **证据片段**:
  ```java
  // JobScheduleStoreImpl.java:505-510
  private boolean isTaskFailed(Integer taskStatus) {
      return taskStatus != null
              && (taskStatus == _NopJobCoreConstants.TASK_STATUS_CANCELED
              || taskStatus == _NopJobCoreConstants.TASK_STATUS_FAILED
              || taskStatus == _NopJobCoreConstants.TASK_STATUS_TIMEOUT);
      // TASK_STATUS_SUSPICIOUS (15) 不包含在内!
  }
  ```
- **严重程度**: P2
- **现状**: RECOVERY block strategy 的 recovery 路径调用 `resetFailedTasks` 将失败任务重置为 WAITING。但 `isTaskFailed` 不包含 SUSPICIOUS (15) 状态。如果一个 schedule 使用 RECOVERY 策略且有 SUSPICIOUS 任务（例如 worker 崩溃后被 timeout checker 标记），recovery 不会重置这些任务。recovered fire 被设为 WAITING，但其 SUSPICIOUS 任务保持原状。当 completion processor 评估 fire 时，SUSPICIOUS 被当作 TIMEOUT 处理（R2-5 修复后），导致 fire 立即被标记为 TIMEOUT——recovery 无效。
- **风险**: RECOVERY 策略无法恢复 worker 崩溃场景（SUSPICIOUS 任务），与 RECOVERY 策略的设计意图矛盾。用户期望 recovery 重试失败任务，但 SUSPICIOUS 任务被跳过。
- **建议**: 在 `isTaskFailed` 中添加 `taskStatus == _NopJobCoreConstants.TASK_STATUS_SUSPICIOUS`。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探（SUSPICIOUS 状态在 recovery 路径中的处理）

---

### [AR-62] `PeriodicTrigger` fixed-rate 模式漂移 — `lastScheduledTime` 使用实际执行时间而非计划时间

- **文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/LocalJobScheduler.java:243`, `nop-job/nop-job-core/src/main/java/io/nop/job/core/trigger/PeriodicTrigger.java:51,66`
- **证据片段**:
  ```java
  // LocalJobScheduler.java:243 — executeJob
  job.state.lastScheduledTime = currentTime();  // 实际执行开始时间，不是计划时间

  // PeriodicTrigger.java:51,66 — fixed-rate 模式
  long start = evalContext.getLastScheduledTime();  // 使用实际执行时间作为基准
  long n = (afterTime - start) / period + 1;
  return start + n * period;  // 按实际执行时间对齐，不是原始计划时间
  ```
- **严重程度**: P2
- **现状**: `PeriodicTrigger` fixed-rate（非 fixedDelay）模式计算下次触发时间为 `start + n * period`，其中 `start` 是 `evalContext.getLastScheduledTime()`。此字段在 `LocalJobScheduler.executeJob` 中被设置为 `currentTime()`（实际执行时的时钟时间），而非原始计划时间。如果第一次触发计划在 T=1000 但实际在 T=1500 开始（因为 misfire、调度器负载或延迟），`lastScheduledTime` = 1500，所有后续触发按 1500 对齐而非 1000。这是永久性漂移。

  示例：period=5000ms，预期计划：T=1000, T=6000, T=11000...
  实际：首次延迟到 T=1500 → `lastScheduledTime=1500` → 后续：T=6500, T=11500...（永久偏移 500ms）

  PeriodicTrigger 的 Javadoc 说"从 minExecutionTime 按照固定周期进行计时"，但代码在首次触发后使用 `lastScheduledTime` 而非 `minScheduleTime`。
- **风险**: 对于需要精确固定间隔的调度场景（如每分钟执行一次的定时同步任务），任何初始延迟都会导致后续所有触发时间永久偏移。随着时间推移和多次延迟积累，偏移可能增大。
- **建议**: `LocalJobScheduler` 应将 `lastScheduledTime` 设置为计划时间（即 trigger 返回的 `time` 值），而非 `currentTime()`。或者在 `PeriodicTrigger` 中使用 `minScheduleTime` 而非 `lastScheduledTime` 作为对齐基准。
- **信心水平**: 确定
- **发现来源视角**: 10x规模运维（调度精度审计）

---

### [AR-63] `DailyCalendar.getNextIncludedTime` 无迭代次数限制 — 与 base calendar 组合可能导致无上限循环

- **文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/calendar/DailyCalendar.java:224-266`
- **证据片段**:
  ```java
  // DailyCalendar.java:224-266
  public long getNextIncludedTime(long timeInMillis) {
      long nextIncludedTime = timeInMillis + oneMillis;
      while (!isTimeIncluded(nextIncludedTime)) {  // 无迭代次数限制!
          if (!invertTimeRange) {
              if ((nextIncludedTime >= getTimeRangeStartingTimeInMillis(nextIncludedTime))
                      && (nextIncludedTime <= getTimeRangeEndingTimeInMillis(nextIncludedTime))) {
                  nextIncludedTime = getTimeRangeEndingTimeInMillis(nextIncludedTime) + oneMillis;
              } else if ((getBaseCalendar() != null) && (!getBaseCalendar().isTimeIncluded(nextIncludedTime))) {
                  nextIncludedTime = getBaseCalendar().getNextIncludedTime(nextIncludedTime);
              } else {
                  nextIncludedTime++;
              }
          } else { ... }
      }
      return nextIncludedTime;
  }
  ```
- **严重程度**: P2
- **现状**: AR-26 修复了 `HolidayCalendar`/`AnnualCalendar` 的无限循环（添加了 `MAX_DAY_SCAN = 366*5`）。AR-27 部分修复了 `CronCalendar` 的性能问题（添加了 `MAX_ITERATION = 10000`）。但 `DailyCalendar.getNextIncludedTime` 的 `while` 循环没有任何迭代次数保护。如果 DailyCalendar 链式连接一个 base calendar，且 base calendar 的 `getNextIncludedTime` 返回的时间反复落在 DailyCalendar 的排除范围内，循环无限制。虽然时间在每次迭代中推进（非真正无限循环），但无安全上限意味着可能消耗无上限的 CPU 时间——在极端配置下可能是分钟级别——阻塞调度器线程。
- **风险**: 病态的 Calendar 链配置可导致调度器线程长时间阻塞。AR-26 已修复同类问题于其他 Calendar 类型，DailyCalendar 是遗漏。
- **建议**: 添加 `MAX_ITERATION` 保护（与 CronCalendar 一致），超出时抛出异常而非无限循环。
- **信心水平**: 确定
- **发现来源视角**: 10x规模运维（Calendar 链安全性审计，AR-26/AR-27 修复的遗漏类型）

---

### [AR-64] `CronCalendar.getNextIncludedTime` 超过 MAX_ITERATION 时返回被排除的时间 — 违反 ICalendar 契约

- **文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/calendar/CronCalendar.java:110-133`
- **证据片段**:
  ```java
  // CronCalendar.java:110-133
  public long getNextIncludedTime(long timeInMillis) {
      long nextIncludedTime = timeInMillis + 1;
      int iterations = 0;
      while (!isTimeIncluded(nextIncludedTime)) {
          if (++iterations > MAX_ITERATION) {
              break;  // 返回 nextIncludedTime — 仍是被排除的时间!
          }
          // ...
      }
      return nextIncludedTime;  // 可能是被排除的时间
  }
  ```
- **严重程度**: P2
- **现状**: AR-27 添加了 `MAX_ITERATION` 保护，但当超过限制时，`break` 退出循环后返回当前 `nextIncludedTime`，该时间可能仍是被排除的。这违反了 `ICalendar.getNextIncludedTime` 的契约（返回值必须是包含的时间）。调用者 `PauseCalendarTrigger` 检测到此值被排除后会重新进入循环，再次触发 CronCalendar 的同一搜索（从同一被排除时间开始），导致同一 `MAX_ITERATION` 再次耗尽 → `PauseCalendarTrigger` 的 `MAX_TRY_COUNT` 也耗尽 → 最终抛出 `ERR_JOB_TRIGGER_LOOP_COUNT_EXCEED_LIMIT`。

  正确的做法是超过限制时抛出异常，而非返回一个可能错误的值。
- **风险**: 两个独立的 MAX_ITERATION 限制交互导致抛出误导性异常（`LOOP_COUNT_EXCEED_LIMIT` 而非 `Calendar iteration limit exceeded`），且错误信息不指示是哪个 Calendar 导致的问题。
- **建议**: 超过 `MAX_ITERATION` 时抛出 `NopException` 而非 `break` 返回错误值。或至少在 `PauseCalendarTrigger` 中检测返回值是否仍被排除并抛出更有意义的异常。
- **信心水平**: 确定
- **发现来源视角**: 10x规模运维（AR-27 修复的边界行为审计）

---

### [AR-65] `NopJobFireBizModel` 继承未限制的 CRUD — 允许直接修改/删除 fire 记录破坏引擎统计

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobFireBizModel.java:29`（整个类，155 行）
- **证据片段**:
  ```java
  // NopJobFireBizModel.java:29 — 仅添加了 cancelFire 和 rerunFire，未限制 CRUD
  public class NopJobFireBizModel extends CrudBizModel<NopJobFire> implements INopJobFireBiz {
      // 没有 override save__/update__/delete__
  }

  // 对比 NopJobTaskBizModel（限制了 delete）:
  @Override
  public boolean delete(String id, IServiceContext context) {
      throw new NopException(ERR_JOB_TASK_DELETE_NOT_ALLOWED)...;
  }
  ```
- **严重程度**: P2
- **现状**: `NopJobFireBizModel` 继承了 `CrudBizModel<NopJobFire>` 的全部 CRUD 操作，没有重写 `save__`、`update__` 或 `delete__`。通过 GraphQL API，任何已认证用户可以直接创建、修改或删除 fire 记录。直接删除或修改 fire 会破坏 schedule 统计（`activeFireCount`, `fireCount`, `totalFireCount` 等），因为这些计数器由 Store 层的事务方法维护。`NopJobTaskBizModel` 已限制了 delete（F10 修复），但 Fire 的保护缺失。
- **风险**: 直接 CRUD 操作绕过引擎生命周期，导致 schedule 统计漂移。删除一个 RUNNING fire 不会触发 completion processor，`activeFireCount` 永远不减。
- **建议**: 与 `NopJobTaskBizModel` 一致，限制 delete 和 update（或至少限制 delete）。Fire 应通过引擎操作（cancel, rerun）而非直接 CRUD 管理。
- **信心水平**: 确定
- **发现来源视角**: GraphQL 契约考古（BizModel API 表面积审计）

---

### [AR-66] `JobFireResult.ERROR()` 设置 `completed=true` — 当 `allowResultCompletion=true` 时导致 schedule 被永久标记 COMPLETED

- **文件**: `nop-job/nop-job-api/src/main/java/io/nop/job/api/execution/JobFireResult.java:65-67`
- **证据片段**:
  ```java
  // JobFireResult.java:65-67
  public static final JobFireResult ERROR(ErrorBean error) {
      return new JobFireResult(true, error, -1);  // completed=true!
  }

  // 注释说: "如果返回ERROR，则表示job进入ERROR状态，不再执行后续的调度"

  // resolveCompletionDecision 中 (JobCompletionProcessorImpl):
  if (allowResultCompletion && Boolean.TRUE.equals(payload.get("completed"))) {
      return new FireCompletionDecision(true, null);  // 标记 schedule 为 COMPLETED!
  }
  ```
- **严重程度**: P2
- **现状**: `ERROR(error)` 的设计意图是"执行失败，任务需终止，不再继续调度"。`completed=true` 在 ERROR 语义下表示"这个 schedule 应该停止"。但在 `resolveCompletionDecision` 中，`completed=true` 触发的是 `FireCompletionDecision(true)`，将 schedule 标记为 **COMPLETED**（而非 ERROR）。`COMPLETED` 和 `ERROR` 是不同的状态：COMPLETED 表示"正常完成"，ERROR 表示"异常终止"。

  当 `allowResultCompletion=true` 在 `jobParams` 中设置时，一个 `ERROR` 结果会将 schedule 永久标记为 COMPLETED 而非 ERROR。operator 在 Dashboard 上看到的是"已完成"而非"执行失败"，排障时被误导。
- **风险**: ERROR 和 COMPLETED 语义混淆。invoker 返回 `ERROR` 时期望 schedule 进入 ERROR 状态，实际进入 COMPLETED 状态。对依赖 schedule 状态的下游系统造成误导。
- **建议**: (a) 在 `resolveCompletionDecision` 中区分 ERROR 的 completed 和 COMPLETED 的 completed，ERROR 时设置 `scheduleStatus = ERROR` 而非 `COMPLETED`。(b) 或将 `ERROR` 的 `completed` 设为 `false`，让 schedule 按 normal error 流程处理（重试或失败）。
- **信心水平**: 很可能
- **发现来源视角**: 未来破坏者（API 语义契约审计）

---

### [AR-67] `NopJobFire.xmeta` 缺少 6 个引擎管理字段的只读覆盖 — 允许通过 API 篡改触发来源和重试策略

- **文件**: `nop-job/nop-job-meta/src/main/resources/_vfs/nop/job/model/NopJobFire/NopJobFire.xmeta`
- **证据片段**:
  ```xml
  <!-- NopJobFire.xmeta — 当前覆盖了 8 个字段为 insertable="false" updatable="false" -->
  <!-- 但以下引擎管理字段未被覆盖： -->
  <!-- triggerSource (dict="job/trigger-source") -->
  <!-- scheduledFireTime (由 planner/manual 设置) -->
  <!-- triggeredBy (从用户上下文设置) -->
  <!-- retryPolicyId (从 schedule 复制) -->
  <!-- retryRecordId (由 retry bridge 回填) -->
  <!-- partitionIndex (从 schedule 复制) -->
  ```
- **严重程度**: P2
- **现状**: Delta xmeta 覆盖了 `fireStatus`, `plannerInstanceId`, `dispatchInstanceId` 等 8 个字段为只读。但 `triggerSource`, `scheduledFireTime`, `triggeredBy`, `retryPolicyId`, `retryRecordId`, `partitionIndex` 仍然是 `insertable="true" updatable="true"`。通过 GraphQL `save__NopJobFire` 或 `update__NopJobFire`，用户可以设置 `triggerSource=MANUAL` 和任意的 `scheduledFireTime`，绕过引擎的 fire 创建逻辑。
- **风险**: 直接通过 API 创建带有虚假 `triggerSource` 的 fire，影响审计追踪。修改 `retryPolicyId` 影响重试行为。修改 `scheduledFireTime` 可能违反 unique key 约束的预期语义。
- **建议**: 在 `NopJobFire.xmeta` 中将这 6 个字段也标记为 `insertable="false" updatable="false"`。
- **信心水平**: 确定
- **发现来源视角**: GraphQL 契约考古（xmeta 字段保护完整性）

---

### [AR-68] 指标系统对失败/超时丢弃 `durationMs` — 运维无法观察失败任务的执行时长分布

- **文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/metrics/JobCompletionMetricsImpl.java:38-44`, `nop-job/nop-job-worker/src/main/java/io/nop/job/worker/metrics/JobWorkerMetricsImpl.java:39-46`
- **证据片段**:
  ```java
  // JobCompletionMetricsImpl.java:33-44
  public void onFireSuccess(long durationMs) {
      fireSuccessTimer.record(Duration.ofMillis(durationMs));  // Timer 记录分布
  }
  public void onFireFailure(long durationMs) {
      fireFailureCounter.increment();  // Counter 只计数，durationMs 被丢弃
  }
  public void onFireTimeout(long durationMs) {
      fireTimeoutCounter.increment();  // durationMs 被丢弃
  }

  // JobWorkerMetricsImpl.java:39-46 — 同样模式
  public void onTaskFailure(long durationMs) {
      taskFailureCounter.increment();  // durationMs 被丢弃
  }
  ```
- **严重程度**: P3
- **现状**: 成功路径使用 Micrometer `Timer` 记录完整的持续时长分布（P50/P95/P99），但失败和超时路径只使用 `Counter` 计数，丢弃 `durationMs` 参数。接口 `IJobCompletionMetrics` 和 `IJobWorkerMetrics` 都传递 `durationMs` 给所有方法，但实现对非成功路径静默忽略。
- **风险**: 运维无法区分"快速失败"（配置错误，几毫秒）和"慢失败"（运行时错误，数小时后超时）。对于超时场景，知道一个任务在超时前执行了多长时间对诊断至关重要。
- **建议**: 将 `fireFailureCounter` 改为 `fireFailureTimer`（或同时保留 counter 和 timer），对失败和超时路径也记录 duration 分布。
- **信心水平**: 确定
- **发现来源视角**: 10x规模运维（可观测性审计）

---

### [AR-69] `CronCalendar` 声明 `throws ParseException` 但实际抛出 `NopException` — 误导 API 消费者

- **文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/calendar/CronCalendar.java:42,68-71,171-174`
- **证据片段**:
  ```java
  // CronCalendar.java:42
  public CronCalendar(String expression) throws ParseException {
      this(null, expression, null);
  }

  // CronCalendar.java:68-71 — 实际构造器
  public CronCalendar(ICalendar baseCalendar, String expression, TimeZone timeZone) {
      super(baseCalendar);
      this.cronExpression = new CronExpression(expression, timeZone);
      // CronExpression 构造器抛出 NopException，不是 ParseException
  }
  ```
- **严重程度**: P3
- **现状**: 两个公共方法声明 `throws ParseException`（继承自 Quartz API），但实际抛出的是 `NopException`（Nop 平台的异常类）。调用者的 `catch (ParseException e)` 块永远不会捕获实际异常。异常会传播到更上层的通用 `catch (Exception e)`。
- **风险**: 扩展开发者依赖 `throws` 声明编写 catch 块，实际异常类型不匹配导致 catch 逻辑失效。低实际影响但 API 契约不准确。
- **建议**: 移除 `throws ParseException` 声明，或改为 `throws NopException`。
- **信心水平**: 确定
- **发现来源视角**: 未来破坏者（API 契约准确性）

---

## Overall Assessment

### Top 3 Actionable Directions

**1. Calendar 构建和边界问题 (AR-54, AR-55, AR-56) — P0 + 2×P1**

三个 Calendar 相关的高严重级别发现集中在 `CalendarBuilder` 和 `DailyCalendar`。AR-54 是直接崩溃（P0），AR-55 是静默排除错误的星期几（P1），AR-56 是午夜边界条件与 `PauseCalendarTrigger` 的组合爆炸（P1）。前 7 轮审查几乎没有覆盖 Calendar 排除逻辑（仅在 AR-26/AR-27 覆盖了 HolidayCalendar 和 CronCalendar 的循环问题）。修复简单：P0 只需一行代码修改（`LocalTime.MAX` 替代 `LocalTime.of(24,0,0)`），P1 需要添加 ISO-to-Calendar 转换和修改严格不等式。

**2. RECOVERY 策略完整性 (AR-57, AR-61) — P1 + P2**

`shouldRecovery` 缺少 `activeFireCount` 守卫导致 RECOVERY 策略总是进入 recovery 路径而非正常 fire 创建（P1）。`resetFailedTasks` 不包含 SUSPICIOUS 状态导致 recovery 无法恢复 worker 崩溃场景（P2）。两个问题组合：RECOVERY 策略在正常情况下走了错误的代码路径，在异常情况下又无法完全恢复。

**3. BizModel API 保护缺失 (AR-65, AR-67) — 2×P2**

`NopJobFireBizModel` 没有限制 CRUD 操作（与 Task 的保护不一致），且 `NopJobFire.xmeta` 缺少 6 个引擎管理字段的只读覆盖。两个问题组合意味着通过 GraphQL API 可以绕过引擎生命周期直接操作 fire 记录。

### Blind Spot Assessment

本轮审查可能遗漏了：

1. **Multi-coordinator HA 分区重平衡**: Coordinator 实例增减时，partition 重新分配对正在执行的 fire/task 的影响。`JobPartitionResolver` 的 `isUnstable()` 方法在 partition 变更时的行为链未深入分析。
2. **`HandleMisfireTrigger` 与所有 Calendar 类型的组合测试**: 仅检查了 DailyCalendar 的午夜边界。其他 Calendar（AnnualCalendar, MonthlyCalendar, CronCalendar）与 `HandleMisfireTrigger` 的组合可能有类似边界问题。
3. **`LocalJobScheduler`（`nop-job-core` 内的本地调度器）与 coordinator 引擎的关系**: `LocalJobScheduler` 有自己的 trigger/calendar 执行路径，与 coordinator 的分布式执行路径有大量代码共享但生命周期不同。两者的交互和一致性未深入检查。
4. **ORM codegen 正确性**: `_` 前缀的生成代码仅做了抽查，未系统性验证 codegen 模板是否正确生成了所有必要的实体字段映射和 DAO 方法。
5. **nop-job-web 模块**: 前端页面和 Web 层的配置未审查。

---

## Finding Summary Table (New)

| # | Severity | Short Description | Confidence |
|---|----------|-------------------|------------|
| AR-54 | **P0** | DailyCalendarSpec `LocalTime.of(24,0,0)` 崩溃 | Certain |
| AR-55 | **P1** | WeeklyCalendarSpec ISO→Calendar 映射不一致 — 排除错误的星期几 | Certain |
| AR-56 | **P1** | DailyCalendar 排除午夜 — 与 PauseCalendarTrigger 组合导致无限循环 | Certain |
| AR-57 | **P1** | shouldRecovery 缺少 activeFireCount 守卫 — RECOVERY 路径语义错误 | Certain |
| AR-58 | **P1** | resolveCompletionDecision 畸形 JSON 中止整个扫描批次 | Certain |
| AR-59 | **P1** | persistSchedule 乐观锁耗尽后 force-update 覆盖并发引擎计数器 | Certain |
| AR-60 | P2 | enableSchedule 保留过时 nextFireTime 导致意外立即触发 | Certain |
| AR-61 | P2 | resetFailedTasks 跳过 SUSPICIOUS 任务 — recovery 不完整 | Certain |
| AR-62 | P2 | PeriodicTrigger fixed-rate 模式漂移 | Certain |
| AR-63 | P2 | DailyCalendar.getNextIncludedTime 无迭代次数限制 | Certain |
| AR-64 | P2 | CronCalendar MAX_ITERATION 退出返回被排除时间 — 契约违反 | Certain |
| AR-65 | P2 | NopJobFireBizModel 未限制 CRUD — 允许绕过引擎操作 fire | Certain |
| AR-66 | P2 | JobFireResult.ERROR 设置 completed=true — 语义混淆 | Likely |
| AR-67 | P2 | NopJobFire.xmeta 缺少 6 个引擎字段只读覆盖 | Certain |
| AR-68 | P3 | 指标系统丢弃失败/超时 durationMs | Certain |
| AR-69 | P3 | CronCalendar 声明 throws ParseException 但抛出 NopException | Certain |

---

## 严重程度分布 (Round 8 New)

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 1    | Calendar 构建 — LocalTime 越界崩溃 |
| P1      | 5    | Calendar 映射/边界错误, block strategy 守卫缺失, JSON 解析错误传播, 乐观锁降级 |
| P2      | 8    | Schedule 生命周期, recovery 完整性, 触发器漂移, Calendar 安全上限, API 保护, 语义混淆 |
| P3      | 2    | 指标可观测性, API 契约误导 |

## 累计未修复项汇总 (AR-1~AR-69)

| 严重程度 | 数量 | 来源 |
|---------|------|------|
| P0      | 1    | AR-54 (R8 新增) |
| P1      | 5    | AR-55,56,57,58,59 (R8 新增); AR-1~AR-47 中 P1 项已全部修复 |
| P2      | 17   | AR-40,42,43,44,46 (R6 未修复) + AR-48,49,50,51,52 (R7 未修复) + AR-60~AR-67 (R8 新增) |
| P3      | 10   | AR-6,7,14,15,16,45,47 (R1-R7 未修复) + AR-53 (R7 未修复) + AR-68,69 (R8 新增) |

**总未修复**: 33 项 (1×P0, 5×P1, 17×P2, 10×P3)
