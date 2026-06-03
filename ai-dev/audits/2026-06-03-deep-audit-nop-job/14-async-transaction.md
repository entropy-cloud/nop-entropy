# 维度 14：异步与事务模式

## 第 1 轮（初审）

### [维度14-01] 乐观锁重试耗尽后 fallback 到 updateEntityDirectly

- **文件**: `JobScheduleStoreImpl.java:155-156,205-206,253-254,289,343-344`, `NopJobScheduleBizModel.java:157-158`
- **证据片段** (JobScheduleStoreImpl.java:153-156):
```java
        NopJobSchedule fresh = scheduleDao().requireEntityById(schedule.getJobScheduleId());
        schedule.setVersion(fresh.getVersion());
    }
    scheduleDao().updateEntityDirectly(schedule);
```
- **严重程度**: P2
- **现状**: 5 次乐观锁重试全部失败后回退到 updateEntityDirectly（绕过版本检查），有 WARN 日志。
- **风险**: 极端高并发场景下可能导致计数器更新丢失（fireCount, activeFireCount 偏低）。
- **建议**: 考虑增加重试次数或在 fallback 中使用原子 SQL（SET fireCount = fireCount + 1）。
- **信心水平**: 92%
- **误报排除**: 有意设计，但确实存在数据精度风险。
- **复核状态**: 未复核

### [维度14-02] Store 层长事务风险

- **文件**: `JobScheduleStoreImpl.java:158-207` (overlayFireAndAdvanceSchedule)
- **证据片段**:
```java
@Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
public void overlayFireAndAdvanceSchedule(NopJobSchedule schedule, ...) {
    // 1. 查询活跃 fires
    // 2. 循环取消每个 fire + tasks
    // 3. 插入新 fire
    // 4. 乐观锁重试更新 schedule
}
```
- **严重程度**: P2
- **现状**: 单个 REQUIRES_NEW 事务内涉及多表多行读写。当活跃 fire 数量多或乐观锁冲突频繁时，事务持续时间可能较长。
- **风险**: 高并发调度场景下可能导致锁竞争和响应延迟。
- **建议**: 监控事务执行时间；考虑为 findActiveFires 查询添加 LIMIT 上限。
- **信心水平**: 80%
- **误报排除**: 如果实际部署中每个调度活跃 fire 极少（0-1 个），则事务很短。
- **复核状态**: 未复核

### [维度14-03] cancelFire fallback 中 schedule 计数器直接赋值

- **文件**: `JobFireStoreImpl.java:253-256`
- **证据片段**:
```java
schedule.setActiveFireCount(Math.max(defaultInt(schedule.getActiveFireCount()) - 1, 0));
schedule.setTotalFireCount(defaultLong(schedule.getTotalFireCount()) + 1);
schedule.setFailFireCount(defaultLong(schedule.getFailFireCount()) + 1);
scheduleDao().updateEntityDirectly(schedule);
```
- **严重程度**: P3
- **现状**: 与 [维度14-01] 同源问题。fallback 路径中计数器基于重试时的 fresh 值计算，存在极小窗口的并发覆盖风险。
- **建议**: 可考虑 fallback 路径中使用增量式 SQL。
- **信心水平**: 85%
- **复核状态**: 未复核

### [维度14-04] persistSchedule 未用 @Transactional

- **文件**: `NopJobScheduleBizModel.java:141-161`
- **严重程度**: P3
- **现状**: tryUpdateManyWithVersionCheck 在 ORM 层面是原子的（单条 UPDATE WHERE version=?）。但重试循环中 restoreEngineFields 未恢复 nextFireTime。
- **建议**: 可在重试循环中也恢复 nextFireTime。
- **信心水平**: 75%
- **复核状态**: 未复核
