# 维度 02：模块职责与文件边界

## 审计范围

nop-job 下 11 个子模块，共 145 个 Java 文件，22051 行代码。

## 第 1 轮（初审）发现

### [维度02-01] JobScheduleStoreImpl (581行) 承载了过重的多实体协调逻辑

- **文件**: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobScheduleStoreImpl.java:37-581`
- **证据片段**:
  ```java
  public class JobScheduleStoreImpl implements IJobScheduleStore {
      // ...
      @Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
      public void recoveryFireAndAdvanceSchedule(NopJobSchedule schedule, Timestamp nextFireTime) {
          List<NopJobFire> failedFires = findFailedFires(schedule.getJobScheduleId());
          // ... 107行复杂逻辑
      }
  ```
- **严重程度**: P3
- **现状**: 同时操作 Schedule、Fire、Task 三种实体。4 个 `@Transactional(REQUIRES_NEW)` 方法各自内嵌 5 次乐观锁重试循环，重复乐观锁模式在文件中出现 7 次。
- **风险**: 认知复杂度高。如果未来增加新的 fire-schedule 协调场景，文件将持续膨胀。
- **建议**: 将乐观锁重试模式抽取为通用模板方法，或将 fire 相关操作下沉到已有的 `JobFireStoreImpl`。
- **信心水平**: 高
- **误报排除**: 文件实际 581 行，超过 500 行基线，且职责偏重。但作为调度核心存储层，跨实体事务性操作在所难免。
- **复核状态**: 未复核

### [维度02-02] defaultLong/defaultInt/calculateDuration 工具方法在 5 个文件中重复

- **文件**: JobScheduleStoreImpl.java、JobFireStoreImpl.java、JobCompletionProcessorImpl.java、JobTimeoutCheckerImpl.java、JobPlannerScannerImpl.java
- **证据片段**:
  ```java
  // 5个文件中完全相同的方法
  private Long calculateDuration(Timestamp startTime, Timestamp endTime) { ... }
  private long defaultLong(Long value) { ... }
  private int defaultInt(Integer value) { ... }
  ```
- **严重程度**: P3
- **现状**: 3 个方法在 5 个文件中各复制一份，共约 15 处重复。
- **风险**: 纯代码卫生问题。修改需在 5+ 处同步。
- **建议**: 提取到 `nop-job-core` 或 `nop-job-dao` 中的共享工具类。
- **信心水平**: 高
- **误报排除**: 实际存在 5 文件重复。
- **复核状态**: 未复核

### [维度02-03] TestJobCoordinatorScanner 测试文件中硬编码与 _NopJobCoreConstants 完全重复的常量

- **文件**: `nop-job/nop-job-coordinator/src/test/java/io/nop/job/coordinator/engine/TestJobCoordinatorScanner.java:27-46`
- **证据片段**:
  ```java
  private static final int SCHEDULE_STATUS_ENABLED = 10;
  private static final int FIRE_STATUS_RUNNING = 20;
  // ... 20 个常量与 _NopJobCoreConstants 完全相同
  ```
- **严重程度**: P2
- **现状**: 测试文件自行定义了 20 个常量，与 `_NopJobCoreConstants` 中的定义完全相同。
- **风险**: 如果常量值发生变化，测试不会同步更新，可能导致测试通过但实际行为错误。
- **建议**: 直接引用 `_NopJobCoreConstants` 中的常量（`import static`）。
- **信心水平**: 高
- **误报排除**: `_NopJobCoreConstants` 是生成的常量类，可在测试中正常引用。
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 02-01 | P3 | JobScheduleStoreImpl.java | 581行，多实体协调逻辑偏重 |
| 02-02 | P3 | 5个文件 | 工具方法重复 |
| 02-03 | P2 | TestJobCoordinatorScanner.java | 测试硬编码常量与生产常量重复 |
