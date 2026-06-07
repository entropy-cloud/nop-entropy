# 维度 07：BizModel 规范遵循

## 第 1 轮（初审）

### [维度07-01] NopJobFireBizModel 在用户侧 @BizMutation 方法中绕过 requireEntity()，跳过数据权限校验

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobFireBizModel.java:48-59, 62-79`
- **证据片段**:
  ```java
  // cancelFire — 第 48–59 行
  @Override
  @BizMutation
  public void cancelFire(@Name("id") String id, IServiceContext context) {
      NopJobFire fire = fireStore.loadFire(id);              // 直接走 store
      if (!isCancelableStatus(fire.getFireStatus())) {
          throwCancelNotAllowed(fire, "cancelFire");
      }
      if (!fireStore.cancelFire(id)) {
          throwCancelNotAllowed(fireStore.loadFire(id), "cancelFire");
      }
      afterEntityChange(fireStore.loadFire(id), "cancelFire", context);
  }

  // rerunFire — 第 62–79 行
  @Override
  @BizMutation
  public void rerunFire(@Name("id") String id, IServiceContext context) {
      NopJobFire sourceFire = fireStore.loadFire(id);        // 直接走 store
      ...
      NopJobSchedule schedule = scheduleStore.loadSchedule(sourceFire.getJobScheduleId());
      ...
  }
  ```
- **严重程度**: P2
- **现状**: `cancelFire` 和 `rerunFire` 通过 `fireStore.loadFire(id)` 加载实体，内部仅调用 `fireDao().requireEntityById()`，绕过了 `CrudBizModel.requireEntity()` 中的 `checkDataAuth()` 和 `checkMetaFilter()` 两个安全钩子。
- **风险**: 如果 xmeta 或 BizModel 层配置了数据权限规则（如按 namespaceId/groupId 行级过滤），这些规则在 cancelFire/rerunFire 上将不会生效。
- **建议**: 改为先通过 `requireEntity(id, "cancelFire", context)` 加载并鉴权，再将已加载的实体传给 store 层执行原子状态变更。
- **信心水平**: 高
- **误报排除**: service-layer.md 的"调度 store 层"边界场景指的是写入路径，不意味着加载时的权限校验也应被绕过。requireEntity() 是用户侧 @BizMutation 方法加载实体的标准模式。
- **复核状态**: 未复核

### [维度07-02] resolveTriggeredBy(IServiceContext) 在两个 BizModel 中完全重复

- **文件 A**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java:262-273`
- **文件 B**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobFireBizModel.java:155-166`
- **证据片段**:
  ```java
  private String resolveTriggeredBy(IServiceContext context) {
      String userName = null;
      if (context != null) {
          if (context.getUserContext() != null) {
              userName = context.getUserContext().getUserName();
          }
          if ((userName == null || userName.isEmpty()) && context.getContext() != null) {
              userName = context.getContext().getUserName();
          }
      }
      return userName == null || userName.isEmpty() ? "system" : userName;
  }
  ```
- **严重程度**: P3
- **现状**: 两个 BizModel 类中包含完全相同的 resolveTriggeredBy 方法实现（逐字一致）。
- **风险**: 维护成本——如果将来需要修改用户名解析逻辑，需要同步修改两个位置。
- **建议**: 提取到 `io.nop.job.service` 包下的共享工具类（如 `JobBizModelHelper`），两处调用改为静态方法引用。
- **信心水平**: 高
- **误报排除**: 两个方法位于同一模块的不同 BizModel 类中，职责完全相同。
- **复核状态**: 未复核

### [维度07-03] NopJobScheduleBizModel.persistSchedule 使用直接 DAO 写入（已记录的边界场景）

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java:141-161`
- **证据片段**:
  ```java
  @SuppressWarnings("unchecked")
  private void persistSchedule(NopJobSchedule schedule, String action, IServiceContext context) {
      IOrmEntityDao<NopJobSchedule> ormDao = (IOrmEntityDao<NopJobSchedule>) daoProvider().daoFor(NopJobSchedule.class);
      for (int attempt = 0; attempt < 5; attempt++) {
          java.util.List<NopJobSchedule> updated = ormDao.tryUpdateManyWithVersionCheck(
                  java.util.Collections.singletonList(schedule));
          if (!updated.isEmpty()) {
              afterEntityChange(schedule, action, context);
              return;
          }
          NopJobSchedule fresh = ormDao.requireEntityById(schedule.getJobScheduleId());
          schedule.setVersion(fresh.getVersion());
          restoreEngineFields(schedule, fresh);
      }
      LOG.warn("nop.job.schedule.persist-optimistic-lock-exhausted:scheduleId={}", schedule.getJobScheduleId());
      ormDao.updateEntityDirectly(schedule);
      afterEntityChange(schedule, action, context);
  }
  ```
- **严重程度**: P3（信息性）
- **现状**: persistSchedule 绕过 CrudBizModel.updateEntity()，直接使用 DAO 实现自定义乐观锁重试逻辑。这是为解决调度引擎并发更新 engine 字段的竞争条件。所有调用者已通过 requireEntity() 完成了权限校验。
- **风险**: 低。绕过的 CrudBizModel.updateEntity() 路径包含事件钩子，但这些 hook 在 nop-job 当前场景中未使用。
- **建议**: 无需立即改动。建议在方法上方添加注释标注此边界决策，防止被当作普通模板复制。
- **信心水平**: 高
- **误报排除**: service-layer.md 已明确将此类模式列为边界场景。
- **复核状态**: 未复核

## 合规确认

| 检查项 | Schedule | Fire | Task |
|--------|----------|------|------|
| @BizModel 名称与实体名一致 | OK | OK | OK |
| extends CrudBizModel<T> | OK | OK | OK |
| 构造函数调用 setEntityName() | OK | OK | OK |
| 实现 I*Biz 接口 | OK | OK | OK |
| 参数使用 @Name | OK | OK | OK |
| 无 Map<String,Object> 返回值 | OK | OK | OK |
| @Inject setter 注入 (protected 字段) | OK | OK | N/A |
| 无 @Transactional 重复声明 | OK | OK | OK |
