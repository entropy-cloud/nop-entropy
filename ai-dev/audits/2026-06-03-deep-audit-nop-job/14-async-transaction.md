# 维度14：异步与事务模式

## 第 1 轮（初审）

### [维度14-01] completeFireAndUpdateSchedule 对 schedule 使用 updateEntityDirectly 绕过乐观锁

- **文件**: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobFireStoreImpl.java:117-128`
- **证据片段**:
  ```java
  @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
  @Override
  public void completeFireAndUpdateSchedule(NopJobFire fire, NopJobSchedule schedule) {
      NopJobFire currentFire = fireDao().requireEntityById(fire.getJobFireId());
      if (TERMINAL_FIRE_STATUSES.contains(currentFire.getFireStatus())) {
          return;
      }
      List<NopJobFire> updated = fireDao().tryUpdateManyWithVersionCheck(Collections.singletonList(fire));
      if (updated.isEmpty()) {
          return;
      }
      scheduleDao().updateEntityDirectly(schedule);    // ← 无版本检查
  }
  ```
- **严重程度**: P2
- **现状**: fire 使用 `tryUpdateManyWithVersionCheck` 做乐观锁保护，但同事务内 schedule 使用 `updateEntityDirectly` 直接写入。调用方（CompletionProcessor/TimeoutChecker）在 `@SingleSession` 定时扫描中先 loadSchedule 再修改字段，如果期间 schedule 被其他事务修改（如 BizModel 的 enableSchedule），修改会被直接覆盖。
- **风险**: 并发场景下可能丢失 schedule 的更新。schedule 有 version 字段，`updateEntityDirectly` 绕过了乐观锁。
- **建议**: 改为 `scheduleDao().tryUpdateManyWithVersionCheck(Collections.singletonList(schedule))`，版本冲突时重试。
- **信心水平**: 确定
- **误报排除**: `updateEntityDirectly` 确实不检查 version，`tryUpdateManyWithVersionCheck` 确实检查。两者在同一事务中使用是设计不一致。
- **复核状态**: 未复核

### [维度14-02] NopJobFireBizModel.cancelFire TOCTOU 多次加载 fire 实体

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobFireBizModel.java:47-58`
- **证据片段**:
  ```java
  public void cancelFire(@Name("id") String id, IServiceContext context) {
      NopJobFire fire = fireStore.loadFire(id);                     // 第1次加载
      if (!isCancelableStatus(fire.getFireStatus())) {
          throwCancelNotAllowed(fire, "cancelFire");
      }
      if (!fireStore.cancelFire(id)) {                              // 内部第2次加载+version check
          throwCancelNotAllowed(fireStore.loadFire(id), "cancelFire");  // 第3次加载
      }
      afterEntityChange(fireStore.loadFire(id), "cancelFire", context); // 第4次加载
  }
  ```
- **严重程度**: P2
- **现状**: BizModel 层先 loadFire 检查状态（第1次），然后调用 fireStore.cancelFire（内部第2次+version check），失败时第3次加载，最后第4次加载。BizModel 层的第一次检查是多余的——Store 层已有完整状态校验和乐观锁保护。
- **风险**: 多余的数据库查询增加 I/O 开销。BizModel 层的状态检查使用旧快照，可能已过时。功能上不会导致数据错误。
- **建议**: 移除 BizModel 层的 isCancelableStatus 预检查，直接调用 fireStore.cancelFire(id) 并根据返回值决定。
- **信心水平**: 确定
- **误报排除**: Store 层的 version check 保护了数据完整性，不会导致脏写。
- **复核状态**: 未复核

### [维度14-03] overlayFireAndAdvanceSchedule 吞掉异常后仍按全量计数

- **文件**: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobScheduleStoreImpl.java:125-139`
- **证据片段**:
  ```java
  for (NopJobFire activeFire : activeFires) {
      try {
          cancelFire(activeFire, cancelTime);
          cancelTasks(activeFire.getJobFireId(), cancelTime);
      } catch (Exception e) {
          LOG.warn("nop.job.schedule.cancel-fire-failed:fireId={}", activeFire.getJobFireId(), e);
      }
  }
  // Count overlay-cancelled fires as completed failures
  int cancelledCount = activeFires.size();  // ← 包括取消失败的
  schedule.setTotalFireCount(defaultLong(schedule.getTotalFireCount()) + cancelledCount);
  schedule.setFailFireCount(defaultLong(schedule.getFailFireCount()) + cancelledCount);
  ```
- **严重程度**: P2
- **现状**: overlay 策略取消 active fires 时，异常被 catch 后仅 log warn，然后 `cancelledCount = activeFires.size()` 把所有 active fires 都计入失败数，包括可能取消失败的 fire。
- **风险**: 统计数字与实际状态不一致。failFireCount 可能高于实际失败数。
- **建议**: 跟踪实际成功取消的数量来更新计数，或将取消失败作为不可恢复错误传播出去。
- **信心水平**: 确定
- **误报排除**: 已确认异常被吞掉后计数仍按全量计算。
- **复核状态**: 未复核

## 合规确认项

- txn() 未被使用；事务管理统一使用 @Transactional(REQUIRES_NEW)——模式清晰合理。
- @SingleSession 的定时扫描方法只做只读查询+委托调用，不存在长事务风险。
- txn().afterCommit() 未使用（nop-job 不需要异步回调场景）。

## 维度复核结论

待复核。

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|----------|
| 14-01 | P2 | JobFireStoreImpl.java:117 | schedule使用updateEntityDirectly绕过乐观锁 |
| 14-02 | P2 | NopJobFireBizModel.java:47 | cancelFire TOCTOU多次加载fire实体 |
| 14-03 | P2 | JobScheduleStoreImpl.java:125 | overlay操作吞掉异常后仍按全量计数 |
