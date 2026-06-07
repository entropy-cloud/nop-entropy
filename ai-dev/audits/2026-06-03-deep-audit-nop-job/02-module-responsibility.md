# 维度 02：模块职责与文件边界

## 第 1 轮（初审）

### [维度02-01] 工具方法在 5-7 个文件中拷贝重复

- **文件**: `JobScheduleStoreImpl.java:379-522`, `JobFireStoreImpl.java:313-418`, `JobTaskStoreImpl.java:109-123`, `JobCompletionProcessorImpl.java:384-434`, `JobTimeoutCheckerImpl.java:442-448`, `JobPlannerScannerImpl.java:255-263`, `DefaultJobCancelHandler.java:166-174`
- **证据片段** (JobScheduleStoreImpl.java:508-521):
```java
private Long calculateDuration(Timestamp startTime, Timestamp endTime) {
    if (startTime == null || endTime == null) {
        return null;
    }
    return Math.max(endTime.getTime() - startTime.getTime(), 0L);
}
private long defaultLong(Long value) {
    return value == null ? 0L : value;
}
private int defaultInt(Integer value) {
    return value == null ? 0 : value;
}
```
- **严重程度**: P3
- **现状**: 5 个文件中存在至少 6 组重复工具方法（defaultLong/defaultInt/calculateDuration/addPartitionFilter/isTaskFinished），总计约 80 行冗余代码。
- **风险**: 语义变更时需同步修改多处，遗漏即引入不一致行为。
- **建议**: 提取到 `io.nop.job.dao.helper.JobStoreHelper` 或 `io.nop.job.core.utils.JobHelper` 静态工具类。
- **信心水平**: 95%
- **误报排除**: 确实存在可提取的重复。
- **复核状态**: 未复核

### [维度02-02] resolveTriggeredBy 在两个 BizModel 中完全相同

- **文件**: `NopJobScheduleBizModel.java:262-273` 和 `NopJobFireBizModel.java:155-166`
- **证据片段**: 12 行完全相同的代码。
- **严重程度**: P3
- **现状**: 代码重复。
- **建议**: 提取到共享工具类。
- **信心水平**: 98%
- **误报排除**: 明确的 DRY 违反。
- **复核状态**: 未复核

### [维度02-03] JobScheduleStoreImpl 乐观锁重试模式重复 7 次

- **文件**: `JobScheduleStoreImpl.java:97-110, 119-131, 137-155, 179-206, 237-254, 275-289, 318-344`
- **证据片段**:
```java
for (int attempt = 0; attempt < 5; attempt++) {
    schedule.setNextFireTime(nextFireTime);
    schedule.setUpdatedBy("system");
    schedule.setUpdateTime(updateTime);
    List<NopJobSchedule> updated = scheduleDao().tryUpdateManyWithVersionCheck(
            Collections.singletonList(schedule));
    if (!updated.isEmpty()) return;
    NopJobSchedule fresh = scheduleDao().requireEntityById(schedule.getJobScheduleId());
    schedule.setVersion(fresh.getVersion());
}
scheduleDao().updateEntityDirectly(schedule);
```
- **严重程度**: P2
- **现状**: 核心"5次乐观锁重试+直接更新兜底"模式在 7 个方法中重复，各处 reset 的字段组合略有不同。
- **风险**: 需调整重试策略或恢复字段时容易遗漏，导致静默数据不一致。
- **建议**: 提取通用的 `retryableScheduleUpdate(Function<NopJobSchedule, Boolean> modifier, Set<String> fieldsToRestore)` 方法。
- **信心水平**: 85%
- **误报排除**: 功能正确，只是维护成本高。
- **复核状态**: 未复核

### [维度02-04] web 模块包含 4 组无 ORM/xmeta 支撑的遗留页面

- **文件**: `nop-job-web/src/main/resources/_vfs/nop/job/pages/NopJobDefinition/`, `NopJobAssignment/`, `NopJobInstance/`, `NopJobInstanceHis/`
- **证据片段**: 这些页面引用不存在的 xmeta 文件（如 `NopJobDefinition.xmeta`）。
- **严重程度**: P3
- **现状**: 4 组遗留页面无对应 ORM 实体/xmeta。访问会报错。
- **风险**: 运行时访问报错，增加新开发者困惑。
- **建议**: 清理遗留页面或为它们创建对应的 ORM/xmeta。
- **信心水平**: 90%
- **误报排除**: 这些实体在 ORM 中确实不存在。
- **复核状态**: 未复核
