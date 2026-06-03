# 维度 02：模块职责与文件边界

## 第 1 轮（初审）

### [维度02-01] dao store 生产代码中大量重复定义状态常量，已有权威常量源但未使用

- **文件**: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobScheduleStoreImpl.java:37-49`
- **证据片段**:
  ```java
  private static final int SCHEDULE_STATUS_ENABLED = 10;
  private static final int FIRE_STATUS_WAITING = 0;
  private static final int FIRE_STATUS_DISPATCHING = 10;
  private static final int FIRE_STATUS_RUNNING = 20;
  private static final int FIRE_STATUS_CANCELED = 60;
  private static final int FIRE_STATUS_FAILED = 40;
  private static final int FIRE_STATUS_TIMEOUT = 50;
  private static final int TASK_STATUS_WAITING = 0;
  private static final int TASK_STATUS_CLAIMED = 10;
  private static final int TASK_STATUS_RUNNING = 20;
  private static final int TASK_STATUS_FAILED = 40;
  private static final int TASK_STATUS_TIMEOUT = 50;
  private static final int TASK_STATUS_CANCELED = 60;
  ```
- **严重程度**: P2
- **现状**: 同一语义常量在 3 个生产文件（JobScheduleStoreImpl、JobFireStoreImpl、JobTaskStoreImpl）中独立定义了 25 次。`_NopJobCoreConstants` 中已定义完全相同的常量，coordinator 模块已正确引用。
- **风险**: 若常量值变更需同步修改多处，有数值漂移风险。
- **建议**: 在 dao store 实现中统一 `import static io.nop.job.core._NopJobCoreConstants.*` 替代本地常量定义。
- **信心水平**: 确定
- **误报排除**: coordinator 模块已正确引用 _NopJobCoreConstants，说明 dao 模块同样可以引用。
- **复核状态**: 未复核

### [维度02-02] calculateFixedDelayNextFireTime 及辅助方法跨模块复制

- **文件**: `JobCompletionProcessorImpl.java:383-393`, `JobFireStoreImpl.java:359-369`
- **证据片段**:
  ```java
  // 两处完全相同的逻辑
  private Timestamp calculateFixedDelayNextFireTime(NopJobSchedule schedule, Timestamp fireEndTime) {
      NopJobSchedule evalSchedule = schedule.cloneInstance();
      evalSchedule.setLastEndTime(fireEndTime);
      long next = JobTriggerCalculator.calculateNextFireTime(
              toTriggerSpec(evalSchedule),
              toEvalContext(evalSchedule),
              fireEndTime.getTime()
      );
      return next <= 0 ? null : new Timestamp(next);
  }
  ```
- **严重程度**: P3
- **现状**: `calculateFixedDelayNextFireTime`、`toTriggerSpec`、`toEvalContext` 等 ~20 个辅助方法跨 4 个文件复制。`TriggerSpecHelper.java` 已提取了部分方法，但 JobFireStoreImpl、JobCompletionProcessorImpl、JobScheduleStoreImpl 仍保留私有拷贝。
- **风险**: 纯粹的代码重复，增加维护负担。
- **建议**: 将公共辅助方法提取到 `TriggerSpecHelper` 或新建 `JobStoreHelper` 中。
- **信心水平**: 高
- **误报排除**: TriggerSpecHelper 的存在说明团队已意识到需要提取，但提取不完整。
- **复核状态**: 未复核

### [维度02-03] dao store 层包含业务决策逻辑（层边界渗透）

- **文件**: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobFireStoreImpl.java:176-236`
- **证据片段**:
  ```java
  @Override
  public boolean cancelFire(String jobFireId) {
      NopJobFire fire = fireDao().requireEntityById(jobFireId);
      List<NopJobTask> tasks = findTasksByFireId(jobFireId);
      if (!isCancelableFire(fire, tasks)) {   // 业务判断
          return false;
      }
      // 设置 fire 状态为 CANCELED，计算 fixedDelayNextFireTime ...
      if (shouldAdvanceFixedDelaySchedule(schedule, fire)) {  // 业务判断
          schedule.setNextFireTime(calculateFixedDelayNextFireTime(schedule, cancelTime));
      }
  }
  ```
- **严重程度**: P2
- **现状**: dao store 层混合了状态机判断（isCancelableFire、shouldAdvanceFixedDelaySchedule）、状态转换规则、错误码选择等业务决策逻辑。coordinator 层也有类似处理，形成分散的业务逻辑。
- **风险**: 修改状态转换规则时需要在 dao store 和 coordinator 两处同步。
- **建议**: 将 store 层中的业务决策逻辑上提到 coordinator 或独立的状态机组件中。
- **信心水平**: 高
- **误报排除**: service-layer.md 的"调度 store 层"边界场景指的是写入路径（乐观锁重试等），但 store 层中的业务决策（如 isCancelableFire、shouldAdvanceFixedDelaySchedule）超出了纯数据访问的职责。
- **复核状态**: 未复核

### [维度02-04] 测试文件中大量重复定义状态常量

- **文件**: 7 个测试文件（TestJobCoordinatorScanner、TestJobConcurrency、TestJobWorkerScanner 等）
- **证据片段**:
  ```java
  // TestJobCoordinatorScanner.java:27-40
  private static final int SCHEDULE_STATUS_ENABLED = 10;
  private static final int FIRE_STATUS_RUNNING = 20;
  // ... 13 个常量
  ```
- **严重程度**: P3
- **现状**: 7 个测试文件中合计约 51 处常量重复定义。这些测试文件的子模块都依赖 nop-job-core（含 _NopJobCoreConstants）。
- **风险**: 测试中的数值与权威源不一致时难以发现。
- **建议**: 测试文件统一 `import static io.nop.job.core._NopJobCoreConstants.*`。
- **信心水平**: 高
- **误报排除**: 测试代码的独立性可理解，但同步修改风险仍然存在。
- **复核状态**: 未复核

## 无问题确认

| 检查项 | 结论 |
|--------|------|
| 超大文件 | 最大手写文件 DailyCalendar.java(512行) 职责单一，无问题 |
| 生成代码混放 | 无混放，所有 _gen 文件含 Auto Gen Code 标记 |
| 生成文件手写修改 | 无手工编辑痕迹 |
| 子模块职责划分 | 整体合规（api纯接口、core零DAO依赖、service/coordinator/worker正确分层） |
