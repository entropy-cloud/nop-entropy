# 维度 07：BizModel 规范遵循

## 第 1 轮（初审）

### [维度07-01] NopJobScheduleBizModel 的 persistSchedule 方法绕过 CrudBizModel 直接使用 IOrmEntityDao

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java:140-162`
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
  ```
- **严重程度**: P3
- **现状**: `persistSchedule` 使用 `IOrmEntityDao.tryUpdateManyWithVersionCheck()` 进行乐观锁重试，绕过了 `CrudBizModel` 提供的标准 `update` / `save` API，并自定义了冲突恢复逻辑（恢复引擎字段后重试）。
- **风险**: 如果 `CrudBizModel` 未来增加了通过标准 `update` 路径的 hook（如审计日志扩展），`persistSchedule` 不会经过这些 hook。不过当前 `afterEntityChange` 已被正确调用。该模式合理（需要自定义乐观锁重试），风险较低。
- **建议**: 维持现状可接受。如果后续框架增加了 `CrudBizModel` 层面的通用拦截器，需要同步审查此路径。
- **信心水平**: 很可能
- **误报排除**: 这不是"用 DAO 代替 CrudBizModel"的反模式——它是因为 `CrudBizModel` 的标准 `update` 不支持自定义乐观锁冲突恢复，所以必须使用更底层的 API。
- **复核状态**: 未复核

---

### [维度07-02] NopJobFireBizModel.cancelFire 中 fireStore.loadFire 被无条件调用两次

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobFireBizModel.java:62-66`
- **证据片段**:
  ```java
  if (!fireStore.cancelFire(id)) {
      throwCancelNotAllowed(fireStore.loadFire(id), "cancelFire");
  }

  afterEntityChange(fireStore.loadFire(id), "cancelFire", context);
  ```
- **严重程度**: P2
- **现状**: `fireStore.loadFire(id)` 在成功路径（第66行）总是被调用一次以传给 `afterEntityChange`。由于 `afterEntityChange` 在当前实现中是空操作，这个数据库查询在正常路径上是被浪费的。
- **风险**: 每次成功取消都会多一次不必要的数据库查询。在高频取消场景下可能成为性能瓶颈。
- **建议**: 将 `fireStore.loadFire(id)` 的结果缓存到局部变量。
- **信心水平**: 很可能
- **误报排除**: 这不是 `@BizLoader` 问题，而是运行时的数据库查询冗余。
- **复核状态**: 未复核

---

### [维度07-03] triggerNow 的 overrideParams 参数使用 Map<String, Object> 而非类型安全结构

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java:107-108`
- **证据片段**:
  ```java
  public void triggerNow(@Name("id") String id, @Name("overrideParams") Map<String, Object> overrideParams,
                         IServiceContext context) {
  ```
- **严重程度**: P3
- **现状**: `triggerNow` 的 `overrideParams` 使用 `Map<String, Object>` 代替类型安全的结构。
- **风险**: 参数结构不可文档化、不可校验。然而 `overrideParams` 的语义是"任意 KV 覆盖"，用 Map 表达是合理的。
- **建议**: 维持现状可接受。
- **信心水平**: 确定
- **误报排除**: `overrideParams` 的语义确实是"透传的 KV 覆盖"，与 jobParams（也是自由 JSON）一致。
- **复核状态**: 未复核

---

### [维度07-04] NopJobFireBizModel.rerunFire 中 buildRecoveryFire 与 NopJobScheduleBizModel.buildManualFire 存在大量重复代码

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobFireBizModel.java:120-143` 与 `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java:218-242`
- **证据片段**:

  NopJobFireBizModel (120-143):
  ```java
  private NopJobFire buildRecoveryFire(NopJobFire sourceFire, NopJobSchedule schedule, IServiceContext context) {
      long now = scheduleStore.getCurrentTime();
      Timestamp fireTime = new Timestamp(now);

      NopJobFire fire = new NopJobFire();
      fire.setJobScheduleId(schedule.getJobScheduleId());
      fire.setNamespaceId(schedule.getNamespaceId());
      fire.setGroupId(schedule.getGroupId());
      // ... 14 个字段的重复设置
      fire.setJobParamsSnapshot(schedule.getJobParams());
      fire.setExecutorKind(schedule.getExecutorKind());
      return fire;
  }
  ```

  NopJobScheduleBizModel (218-242):
  ```java
  private NopJobFire buildManualFire(NopJobSchedule schedule, Map<String, Object> overrideParams,
                                     IServiceContext context) {
      long now = scheduleStore.getCurrentTime();
      Timestamp fireTime = new Timestamp(now);

      NopJobFire fire = new NopJobFire();
      fire.setJobScheduleId(schedule.getJobScheduleId());
      fire.setNamespaceId(schedule.getNamespaceId());
      // ... 几乎相同的字段设置
  ```
- **严重程度**: P2
- **现状**: `buildRecoveryFire`（在 NopJobFireBizModel）和 `buildManualFire`（在 NopJobScheduleBizModel）有约 80% 的重复代码。
- **风险**: 新增 NopJobFire 字段时需要同时修改两处，容易遗漏导致不一致。
- **建议**: 将公共的 NopJobFire 构建逻辑提取到共享的 helper/factory 方法中。
- **信心水平**: 很可能
- **误报排除**: 这不是"跨聚合根操作"问题，而是纯粹的代码重复维护风险。
- **复核状态**: 未复核

---

### [维度07-05] NopJobFireBizModel 访问了 NopJobSchedule 聚合根的 Store

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobFireBizModel.java:31-32,49-52,77-78`
- **证据片段**:
  ```java
  protected IJobScheduleStore scheduleStore;   // line 32

  @Inject
  public void setScheduleStore(IJobScheduleStore scheduleStore) {  // line 50-51
      this.scheduleStore = scheduleStore;
  }

  // rerunFire 中：
  NopJobSchedule schedule = scheduleStore.loadSchedule(sourceFire.getJobScheduleId());  // line 77
  validateRerunSchedule(schedule, "rerunFire");
  NopJobFire rerunFire = buildRecoveryFire(sourceFire, schedule, context);
  if (!scheduleStore.insertManualFire(schedule, rerunFire)) {  // line 81
  ```
- **严重程度**: P3
- **现状**: `NopJobFireBizModel`（聚合根为 `NopJobFire`）注入了 `IJobScheduleStore`，用于加载 schedule 并通过 `scheduleStore.insertManualFire` 创建新的 fire。
- **风险**: 两个聚合根之间的耦合。但 `insertManualFire` 是一个原子操作，无法通过简单调用 `NopJobScheduleBizModel` 的方法来实现。
- **建议**: 维持现状可接受。如果未来需要解耦，可将 `rerunFire` 迁移到 `NopJobScheduleBizModel`。
- **信心水平**: 很可能
- **误报排除**: `insertManualFire` 是跨表原子操作，不能简单地通过调用另一个 BizModel 方法来替代。
- **复核状态**: 未复核

---

### [维度07-06] NopJobTaskBizModel delete 重写使用全限定 IServiceContext

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobTaskBizModel.java:24-28`
- **证据片段**:
  ```java
  @Override
  public boolean delete(String id, io.nop.core.context.IServiceContext context) {
      throw new NopException(ERR_JOB_TASK_DELETE_NOT_ALLOWED)
              .param("jobTaskId", id);
  }
  ```
- **严重程度**: P3
- **现状**: 方法签名使用全限定名 `io.nop.core.context.IServiceContext` 而非 import，与同文件其他类风格不一致。
- **风险**: 风险极低。功能正确。
- **建议**: 可以添加 `import io.nop.core.context.IServiceContext` 使风格统一，优先级极低。
- **信心水平**: 确定
- **误报排除**: 重写 delete 抛异常是正确的禁止删除模式，已在测试中验证。
- **复核状态**: 未复核
