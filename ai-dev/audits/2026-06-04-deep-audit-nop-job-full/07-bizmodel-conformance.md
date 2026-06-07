# 维度 07：BizModel 规范遵循

## 第 1 轮（初审）

### [维度07-01] persistSchedule 绕过 CrudBizModel 标准 DAO 访问模式，直接通过 IOrmEntityDao 手动实现乐观锁重试

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
      LOG.warn("nop.job.schedule.persist-optimistic-lock-exhausted:scheduleId={}", schedule.getJobScheduleId());
      throw new NopException(ERR_JOB_SCHEDULE_INVALID_STATUS_TRANSITION)
              .param("jobScheduleId", schedule.getJobScheduleId())
              .param("action", action)
              .param("reason", "Optimistic lock exhausted after 5 retries");
  }
  ```

- **严重程度**: P2
- **现状**: persistSchedule 绕过 CrudBizModel 的标准 save/update 管线，直接获取底层 IOrmEntityDao 并手动实现乐观锁重试。未经检查的类型转换、绕过 action pipeline（beforeEntityChange 等钩子不调用）、魔法数字 5、错误码误用（INVALID_STATUS_TRANSITION 用于乐观锁耗尽）。
- **风险**: 绕过管线意味着 BizModel 的扩展点（prepare/validate/before/after）不会被触发；错误码误用可能误导运维排查。
- **建议**: 通过重写 doSaveEntity 或 doUpdate 实现自定义乐观锁重试，保持管线完整性。至少将重试次数配置化并使用专门错误码。
- **信心水平**: 确定
- **误报排除**: 不是"看起来不优雅"的主观判断——存在具体的功能性问题（绕过管线、错误码误用）。
- **复核状态**: 未复核

### [维度07-02] cancelFire 中 fireStore.loadFire(id) 在取消后再次加载实体，存在不必要的额外数据库查询

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobFireBizModel.java:56-67`
- **证据片段**:
  ```java
  @Override
  @BizMutation
  public void cancelFire(@Name("id") String id, IServiceContext context) {
      NopJobFire fire = requireEntity(id, "cancelFire", context);
      if (!isCancelableStatus(fire.getFireStatus())) {
          throwCancelNotAllowed(fire, "cancelFire");
      }
      if (!fireStore.cancelFire(id)) {
          throwCancelNotAllowed(fireStore.loadFire(id), "cancelFire");
      }
      afterEntityChange(fireStore.loadFire(id), "cancelFire", context);
  }
  ```

- **严重程度**: P3
- **现状**: cancelFire 方法在成功路径上产生最多 3 次数据库查询（requireEntity + cancelFire + loadFire）。第 66 行成功后再次 loadFire 是不必要的。
- **风险**: 性能浪费，每次取消操作多一次 DB 查询。
- **建议**: 让 fireStore.cancelFire 返回更新后的实体，或在 afterEntityChange 中使用已知 ID 的轻量引用。
- **信心水平**: 确定
- **误报排除**: 已排除"afterEntityChange 可能需要完整实体"——可让 Store 方法返回实体避免额外查询。
- **复核状态**: 未复核

### [维度07-03] triggerNow 的 overrideParams 使用 Map<String,Object> 而非类型安全的 @RequestBean

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java:105-120`
- **证据片段**:
  ```java
  @Override
  @BizMutation
  public void triggerNow(@Name("id") String id, @Name("overrideParams") Map<String, Object> overrideParams,
                         IServiceContext context) {
  ```

- **严重程度**: P3
- **现状**: overrideParams 参数类型为 Map<String, Object>，在 GraphQL 中映射为 Map 标量，客户端传参完全没有类型约束。
- **风险**: 低。作业参数本身是自由格式 JSON，使用 Map 有业务合理性。但缺少前端校验和 API 文档。
- **建议**: 低优先级。如作业参数有已知固定字段可定义 @DataBean。
- **信心水平**: 很可能
- **误报排除**: 已考虑"作业参数是自由格式 JSON"的业务合理性。仍作为可改进项报告。
- **复核状态**: 未复核

### [维度07-04] NopJobFireBizModel 和 NopJobTaskBizModel 的 delete() override 丢掉父类 @BizMutation 注解

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobFireBizModel.java:38-42`
- **证据片段**:
  ```java
  // NopJobFireBizModel.java:38-42
  @Override
  public boolean delete(String id, IServiceContext context) {
      throw new NopException(ERR_JOB_FIRE_DELETE_NOT_ALLOWED)
              .param("jobFireId", id);
  }
  ```
  父类 CrudBizModel.java:1041-1044:
  ```java
  @Description("@i18n:biz.delete|根据主键删除指定对象")
  @BizMutation
  @BizMakerChecker(tryMethod = METHOD_TRY_DELETE)
  public boolean delete(@Name("id") @Description("@i18n:biz.id|对象的主键标识") String id, IServiceContext context) {
  ```

- **严重程度**: P2
- **现状**: 子类 override 的 delete() 不携带 @BizMutation（Java 注解不继承），可能导致 delete mutation 不在 GraphQL schema 中注册。虽有可能是期望行为（禁止删除就不应暴露），但框架提供了 @BizModel(disabledActions={"delete"}) 的正式机制。
- **风险**: 依赖注解丢失的副作用来禁用操作不够可靠——如果 ReflectionBizModelBuilder 同时返回父类方法，delete 可能路由到父类实现绕过禁用。
- **建议**: 使用 @BizModel(value="NopJobFire", disabledActions={"delete"}) 显式禁用 delete，同时保留 override 方法作为防御层。
- **信心水平**: 很可能
- **误报排除**: 不是对 Nop 平台标准模式的误报——这涉及自定义 delete override 的注解继承问题。
- **复核状态**: 未复核

### [维度07-05] rerunFire 跨聚合直接加载 NopJobSchedule 实体

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobFireBizModel.java:70-88`
- **证据片段**:
  ```java
  @Override
  @BizMutation
  public void rerunFire(@Name("id") String id, IServiceContext context) {
      NopJobFire sourceFire = requireEntity(id, "rerunFire", context);
      // ...
      NopJobSchedule schedule = scheduleStore.loadSchedule(sourceFire.getJobScheduleId());
      // ...
  }
  ```

- **严重程度**: P3
- **现状**: NopJobFireBizModel 是 NopJobFire 的聚合根，但 rerunFire 通过 scheduleStore 直接加载 NopJobSchedule 聚合的实体。
- **风险**: 在 Nop 平台中可接受——BizModel 兼具 Service 和聚合根双重职责，scheduleStore 是封装接口。
- **建议**: 设计观察记录，不需立即修改。
- **信心水平**: 很可能
- **误报排除**: 已考虑 Nop 平台中 BizModel 的 Service 性质和 Store 的封装性。
- **复核状态**: 未复核

### [维度07-06] buildManualFire/buildRecoveryFire 的代码重复

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java:218-242` 和 `NopJobFireBizModel.java:120-143`
- **证据片段**:
  ```java
  // NopJobScheduleBizModel.buildManualFire
  private NopJobFire buildManualFire(NopJobSchedule schedule, Map<String, Object> overrideParams,
                                     IServiceContext context) {
      long now = scheduleStore.getCurrentTime();
      NopJobFire fire = new NopJobFire();
      fire.setJobScheduleId(schedule.getJobScheduleId());
      fire.setNamespaceId(schedule.getNamespaceId());
      fire.setGroupId(schedule.getGroupId());
      fire.setJobName(schedule.getJobName());
      fire.setTriggerSource(_NopJobCoreConstants.TRIGGER_SOURCE_MANUAL);
      // ...
  }
  
  // NopJobFireBizModel.buildRecoveryFire (几乎相同的代码)
  private NopJobFire buildRecoveryFire(NopJobFire sourceFire, NopJobSchedule schedule, IServiceContext context) {
      long now = scheduleStore.getCurrentTime();
      NopJobFire fire = new NopJobFire();
      fire.setJobScheduleId(schedule.getJobScheduleId());
      fire.setNamespaceId(schedule.getNamespaceId());
      // ...
      fire.setTriggerSource(_NopJobCoreConstants.TRIGGER_SOURCE_RECOVERY);
      // ...
  }
  ```

- **严重程度**: P3
- **现状**: 两个方法约 80% 代码重复，仅 triggerSource 和 jobParamsSnapshot 来源不同。
- **风险**: 若 NopJobFire 新增字段，两处需同步修改。
- **建议**: 抽取共享的 buildBaseFire(schedule, triggerSource, context) 模板方法。
- **信心水平**: 确定
- **误报排除**: 已确认两个方法位于不同类中，确实存在代码重复。
- **复核状态**: 未复核

### [维度07-07] RpcJobInvoker 的 rpcServiceInvoker 缺少 null 安全检查

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/executor/RpcJobInvoker.java:22-29,63`
- **证据片段**:
  ```java
  public class RpcJobInvoker implements IJobInvoker {
      private IRpcServiceInvoker rpcServiceInvoker;
      @Inject
      public void setRpcServiceInvoker(IRpcServiceInvoker rpcServiceInvoker) {
          this.rpcServiceInvoker = rpcServiceInvoker;
      }
      // ...
      return rpcServiceInvoker.invokeAsync(serviceName, serviceMethod, request, null)
  }
  ```

- **严重程度**: P3
- **现状**: rpcServiceInvoker 字段通过 setter 注入但无 null 检查。若 beans 配置未正确注入，invokeAsync 会在第 63 行产生 NPE。
- **风险**: 低。正常部署不会出现注入失败。
- **建议**: 在 invokeAsync/cancelAsync 入口添加非空断言。
- **信心水平**: 很可能
- **误报排除**: 已考虑 NopIoC setter 注入是标准模式，但缺少 fail-fast 机制是真实改进点。
- **复核状态**: 未复核
