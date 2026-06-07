# 维度 14：异步与事务模式

## 审计范围

Store 层 14 处 REQUIRES_NEW、4 个异步调用点、乐观锁使用、资源管理。

## 第 1 轮（初审）发现

### [维度14-01] completeFireAndUpdateSchedule 并发 delta 计算错误导致计数器永久不一致

- **文件**: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobFireStoreImpl.java:118-167`
- **证据片段**:
  ```java
  int activeDelta = schedule.getActiveFireCount() - origActiveFireCount;
  long fireCountDelta = schedule.getFireCount() - origFireCount;
  // ...
  for (int attempt = 0; attempt < 5; attempt++) {
      List<NopJobSchedule> updatedSchedules = scheduleDao().tryUpdateManyWithVersionCheck(
              Collections.singletonList(schedule));
      if (!updatedSchedules.isEmpty()) {
          return;
      }
      NopJobSchedule fresh = scheduleDao().requireEntityById(schedule.getJobScheduleId());
      schedule.setVersion(fresh.getVersion());
      schedule.setActiveFireCount(fresh.getActiveFireCount() + activeDelta);
  ```
- **严重程度**: P1
- **现状**: delta 计算公式 `schedule.getActiveFireCount() - baseline.getActiveFireCount()` 假设调用方加载的值与 baseline 相同。当并发提交使 baseline 偏移后，差值不再是绝对增量，导致 activeFireCount、totalFireCount、successFireCount、failFireCount 永久性不一致。
- **风险**: 并发完成场景下（PARALLEL 策略或广播分片），计数器偏差无法自愈。activeFireCount 偏高导致 block strategy 判断失准（漏触发或误跳过）。统计计数器累积偏差。
- **建议**: 将 delta 改为绝对增量常量（activeDelta=-1, totalDelta=+1），在重试时使用 `fresh + absoluteDelta`。或与 `insertFireAndAdvanceSchedule` 的正确模式对齐。
- **信心水平**: 高
- **误报排除**: 对比同模块 `insertFireAndAdvanceSchedule` 的正确模式（每次重试从 fresh 重算 +1），此处使用预计算的 relativeDelta 是结构性错误。
- **复核状态**: 未复核

### [维度14-02] persistSchedule 乐观锁耗尽后降级 updateEntityDirectly 可丢失引擎字段更新

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java:142-161`
- **证据片段**:
  ```java
  LOG.warn("nop.job.schedule.persist-optimistic-lock-exhausted:scheduleId={}", schedule.getJobScheduleId());
  ormDao.updateEntityDirectly(schedule);
  ```
- **严重程度**: P2
- **现状**: 5 次重试耗尽后降级到 `updateEntityDirectly`（不检查 version 直接覆盖整行）。`restoreEngineFields` 在最后一次重试时恢复了引擎字段，但在恢复后到 `updateEntityDirectly` 执行前的窗口期，Store 层事务的更新可能被覆盖。
- **风险**: 极端并发场景下引擎计数器值回退。日常运行中 LOG.warn 很少出现。
- **建议**: 降级改为仅更新非引擎字段，或增加重试次数，或再次读取最新引擎字段。
- **信心水平**: 高
- **误报排除**: 有明确的时间窗口风险分析，不是理论性猜测。
- **复核状态**: 未复核

### [维度14-03] RpcJobInvoker 异步链缺少 exceptionally 防御性处理

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/executor/RpcJobInvoker.java:32-64`
- **证据片段**:
  ```java
  return rpcServiceInvoker.invokeAsync(serviceName, serviceMethod, request, null)
          .thenApply(this::toJobFireResult);
  ```
- **严重程度**: P2
- **现状**: `invokeAsync` 和 `cancelAsync` 返回的 CompletionStage 缺少 `.exceptionally()` 处理。当前两个调用方（JobWorkerScannerImpl 和 DefaultJobCancelHandler）通过 `whenComplete` 正确处理了 err 分支。
- **风险**: IJobInvoker 是公开接口（nop-job-api），未来新增调用方时易遗漏异常处理。
- **建议**: 增加 `.exceptionally(err -> JobFireResult.ERROR(...))` 使返回值语义更明确。
- **信心水平**: 中
- **误报排除**: 当前调用方已正确处理异常，但公开接口缺乏防御性编程是结构性问题。
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 14-01 | P1 | JobFireStoreImpl.java:118-167 | 并发 delta 计算错误导致计数器永久不一致 |
| 14-02 | P2 | NopJobScheduleBizModel.java:142-161 | 乐观锁耗尽降级丢失引擎字段 |
| 14-03 | P2 | RpcJobInvoker.java:32-64 | 异步链缺少 exceptionally |
