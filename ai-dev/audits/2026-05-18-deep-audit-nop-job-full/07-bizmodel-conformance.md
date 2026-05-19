# 维度07：BizModel规范遵循

## 第 1 轮（初审）

### 审计范围
- NopJobFireBizModel (161 行)
- NopJobScheduleBizModel (306 行)
- NopJobTaskBizModel (15 行)

### 符合规范的部分
1. ✅ 所有 BizModel 都正确继承了 CrudBizModel
2. ✅ 构造函数都调用了 setEntityName()
3. ✅ 都有对应的 xmeta 文件和 ORM 实体，无"伪 BizModel"
4. ✅ 使用了 @BizMutation 和 @Name 注解
5. ✅ 注入字段使用 protected 修饰符（符合 NopIoC 限制）
6. ✅ NopJobScheduleBizModel 正确使用了 requireEntity()
7. ✅ 没有使用 @BizLoader 的场景
8. ✅ 方法归属正确（都在对应的聚合根 BizModel 中）

---

## 发现 1: NopJobFireBizModel 直接使用 fireStore.loadFire() 而非 requireEntity()

**文件路径**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobFireBizModel.java`

**行号**: 48, 54, 63

**证据片段**:
```java
public void cancelFire(@Name("id") String id, IServiceContext context) {
    NopJobFire fire = fireStore.loadFire(id);  // 第 48 行
    if (!isCancelableStatus(fire.getFireStatus())) {
        throwCancelNotAllowed(fire, "cancelFire");
    }
    if (!fireStore.cancelFire(id)) {
        throwCancelNotAllowed(fireStore.loadFire(id), "cancelFire");  // 第 54 行
    }
    afterEntityChange(fireStore.loadFire(id), "cancelFire", context);  // 第 57 行
}

public void rerunFire(@Name("id") String id, IServiceContext context) {
    NopJobFire sourceFire = fireStore.loadFire(id);  // 第 63 行
    if (!isRerunnableStatus(sourceFire.getFireStatus())) {
        throwRerunNotAllowed(sourceFire, "rerunFire");
    }
```

**严重程度**: P2

**风险**:
1. 绕过了 CrudBizModel 的权限检查机制
2. 绕过了标准审计日志和变更通知
3. 在同一方法中多次调用 loadFire()（cancelFire 中调用了 3 次），存在性能浪费和数据不一致风险
4. 违反了"优先使用 CrudBizModel 安全 API"的规范

**建议**:
1. 使用 requireEntity(id, action, context) 替代 fireStore.loadFire(id)
2. 在 cancelFire 方法中缓存首次加载的 fire 实体，避免重复加载
3. 确保所有实体获取都通过 CrudBizModel 的标准方法

**误报排除**: IJobFireStore 不是标准的 DAO 接口，而是专门为作业调度设计的 Store，包含特殊业务逻辑。但 loadFire() 是纯粹的实体加载操作，应该通过 CrudBizModel 的标准 API。

---

## 发现 2: NopJobFireBizModel 跨模块直接使用 scheduleStore 而非 Biz 接口

**文件路径**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobFireBizModel.java`

**行号**: 29, 41-42, 68, 72

**证据片段**:
```java
public class NopJobFireBizModel extends CrudBizModel<NopJobFire> implements INopJobFireBiz{
    protected IJobFireStore fireStore;
    protected IJobScheduleStore scheduleStore;  // 第 29 行：直接注入其他模块的 Store

    @Inject
    public void setScheduleStore(IJobScheduleStore scheduleStore) {  // 第 41-42 行
        this.scheduleStore = scheduleStore;
    }

    public void rerunFire(@Name("id") String id, IServiceContext context) {
        NopJobFire sourceFire = fireStore.loadFire(id);
        if (!isRerunnableStatus(sourceFire.getFireStatus())) {
            throwRerunNotAllowed(sourceFire, "rerunFire");
        }
        NopJobSchedule schedule = scheduleStore.loadSchedule(sourceFire.getJobScheduleId());  // 第 68 行
```

**严重程度**: P2

**风险**: 破坏模块边界和封装，绕过 NopJobScheduleBizModel 的业务逻辑和权限检查，验证逻辑分散在不同模块。

**建议**: 在 INopJobScheduleBiz 中暴露所需方法，注入 INopJobScheduleBiz 并通过其接口访问 Schedule 相关操作。

**误报排除**: scheduleStore.loadSchedule() 是纯粹的实体加载操作，但 BizModel 间应通过 Biz 接口通信。

---

## 发现 3: NopJobScheduleBizModel 使用 dao().updateEntityDirectly() 而非 updateEntity()

**文件路径**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java`

**行号**: 126

**证据片段**:
```java
private void persistSchedule(NopJobSchedule schedule, String action, IServiceContext context) {
    dao().updateEntityDirectly(schedule);  // 第 126 行
    afterEntityChange(schedule, action, context);
}
```

**严重程度**: P2

**风险**: 绕过权限检查和审计日志，updateEntityDirectly() 是 DAO 层低级 API，不应在 BizModel 直接使用。

**建议**: 使用 updateEntity(schedule, action, context) 替代，所有持久化通过 CrudBizModel 标准方法。

**误报排除**: persistSchedule() 是私有方法，但被多个 @BizMutation 调用，仍应遵循 BizModel 规范。

---

## 发现 4: NopJobScheduleBizModel 的 triggerNow 方法使用 Map<String, Object> 参数

**文件路径**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java`

**行号**: 97

**证据片段**:
```java
public void triggerNow(@Name("id") String id, 
                       @Name("overrideParams") Map<String, Object> overrideParams,  // 第 97 行
                       IServiceContext context) {
```

**严重程度**: P3

**风险**: 编译期无法类型检查，降低 API 自文档性，违反类型安全原则。

**建议**: 定义 ManualTriggerRequest DTO 类替代 Map，或使用 @RequestBean 注解配合 DTO。

**误报排除**: 作业参数是动态的，但可通过定义包含 Map 字段的 DTO 兼顾类型安全和灵活性。

---

## 发现 5: NopJobScheduleBizModel 直接使用 scheduleStore.insertManualFire()

**文件路径**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java`

**行号**: 103

**证据片段**:
```java
NopJobFire fire = buildManualFire(schedule, overrideParams, context);
scheduleStore.insertManualFire(schedule, fire);  // 第 103 行
```

**严重程度**: P2

**风险**: 破坏模块边界，绕过 NopJobFireBizModel 的业务逻辑和权限检查。

**建议**: 在 INopJobFireBiz 中暴露 insertManualFire() 方法，注入 INopJobFireBiz 并通过其接口创建 Fire。

**误报排除**: insertManualFire() 是特殊业务方法，但应通过 Biz 接口调用。

---

## 发现 6: NopJobFireBizModel 直接使用 fireStore.cancelFire() 进行状态更新

**文件路径**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobFireBizModel.java`

**行号**: 53

**证据片段**:
```java
if (!fireStore.cancelFire(id)) {  // 第 53 行
    throwCancelNotAllowed(fireStore.loadFire(id), "cancelFire");
}
```

**严重程度**: P2

**风险**: 绕过权限检查和审计日志，特殊业务逻辑应在 BizModel 层实现而非 Store 层。

**建议**: 在 BizModel 中实现 cancelFire 业务逻辑，使用 requireEntity() 和 updateEntity() 进行状态更新。

**误报排除**: cancelFire() 涉及复杂业务逻辑，但这个逻辑应在 BizModel 层实现。

---

## 发现 7: NopJobFireBizModel 在 buildRecoveryFire 中手动创建 NopJobFire 实体

**文件路径**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobFireBizModel.java`

**行号**: 110-127

**证据片段**:
```java
NopJobFire fire = new NopJobFire();
fire.setJobScheduleId(sourceFire.getJobScheduleId());
fire.setNamespaceId(sourceFire.getNamespaceId());
fire.setCreatedBy("system");
```

**严重程度**: P3

**风险**: 手动设置字段容易遗漏，绕过标准保存流程，与 buildManualFire 代码重复。

**建议**: 提取公共实体构建逻辑到工厂方法，或返回 DTO 在 BizModel 层转换为实体。

**误报排除**: 通过 scheduleStore.insertManualFire() 保存，但手动构建存在维护性问题。

---

## 发现 8: NopJobScheduleBizModel 在 buildManualFire 中手动创建 NopJobFire 实体

**文件路径**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java`

**行号**: 177-194

**证据片段**:
```java
NopJobFire fire = new NopJobFire();
fire.setJobScheduleId(schedule.getJobScheduleId());
fire.setNamespaceId(schedule.getNamespaceId());
fire.setCreatedBy("system");
```

**严重程度**: P3

**风险**: 手动设置字段容易遗漏，绕过标准保存流程，与 buildRecoveryFire 代码重复。

**建议**: 提取公共实体构建逻辑到工厂方法，或通过 INopJobFireBiz 接口创建 Fire。

**误报排除**: 通过 scheduleStore.insertManualFire() 保存，但手动构建存在维护性问题。

---

## 发现 9: NopJobScheduleBizModel 使用 scheduleStore.getCurrentTime() 获取当前时间

**文件路径**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java`

**行号**: 174

**证据片段**:
```java
private NopJobFire buildManualFire(...) {
    long now = scheduleStore.getCurrentTime();  // 第 174 行
```

**严重程度**: P3

**风险**: 将时间获取耦合到 Store 接口，降低可测试性，违反单一职责原则。

**建议**: 使用 System.currentTimeMillis() 或独立的时间服务接口，不在 Store 中放置工具方法。

**误报排除**: 如需支持分布式时间一致性，应有独立时钟服务而非混在 Store 中。

---

## 发现 10: NopJobFireBizModel 重复调用 fireStore.loadFire(id)

**文件路径**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobFireBizModel.java`

**行号**: 48, 54, 57

**证据片段**:
```java
NopJobFire fire = fireStore.loadFire(id);  // 第 48 行
if (!fireStore.cancelFire(id)) {
    throwCancelNotAllowed(fireStore.loadFire(id), "cancelFire");  // 第 54 行
}
afterEntityChange(fireStore.loadFire(id), "cancelFire", context);  // 第 57 行
```

**严重程度**: P3

**风险**: 同一 id 重复加载 3 次，浪费性能，可能导致数据不一致。

**建议**: 使用 requireEntity() 缓存实体，cancelFire() 后重新加载一次获取最新状态。

**误报排除**: 重复加载可能是为获取最新状态，但应通过一次重新加载而非多次实现。

---

## 发现 11: NopJobFireBizModel 和 NopJobScheduleBizModel 使用重复的 resolveTriggeredBy 方法

**文件**: `NopJobFireBizModel.java:149-160`, `NopJobScheduleBizModel.java:217-228`

**证据片段**:
```java
private String resolveTriggeredBy(IServiceContext context) {
    if (context != null && context.getUserContext() != null) {
        userName = context.getUserContext().getUserName();
    }
    return userName == null || userName.isEmpty() ? "system" : userName;
}
```

**严重程度**: P3

**风险**: 代码重复违反 DRY 原则，修改需同步多处，增加维护成本。

**建议**: 提取到父类或工具类，定义通用的上下文解析方法，确保用户信息获取逻辑统一。

**误报排除**: 虽然代码重复但不影响功能，但长期维护会产生同步问题。

---

## 发现 12: NopJobFireBizModel 跨模块验证 NopJobSchedule 状态

**文件**: `NopJobFireBizModel.java:91-103`

**证据片段**:
```java
private void validateRerunSchedule(NopJobSchedule schedule, String action) {
    if (schedule.getScheduleStatus() == _NopJobCoreConstants.SCHEDULE_STATUS_ARCHIVED
            || schedule.getScheduleStatus() == _NopJobCoreConstants.SCHEDULE_STATUS_COMPLETED) {
        throw new NopException(ERR_JOB_SCHEDULE_MANUAL_TRIGGER_NOT_ALLOWED)
                .param("jobScheduleId", schedule.getJobScheduleId());
    }
}
```

**严重程度**: P2

**风险**: 破坏模块边界和封装，绕过 NopJobScheduleBizModel 业务逻辑，验证逻辑分散。

**建议**: 在 INopJobScheduleBiz 提供 validateRerun() 方法，通过 Biz 接口调用验证逻辑。

**误报排除**: validateRerunSchedule 与 rerunFire 紧密相关，但它操作的是 Schedule 实体。

---

## 发现 13: NopJobFireBizModel 直接设置 createdBy/updatedBy 为 "system"

**文件**: `NopJobFireBizModel.java:122-124`

**证据片段**:
```java
fire.setCreatedBy("system");
fire.setCreateTime(fireTime);
fire.setUpdatedBy("system");
fire.setUpdateTime(fireTime);
```

**严重程度**: P3

**风险**: 审计信息不准确，无法追踪实际触发者，与 resolveTriggeredBy 逻辑不一致。

**建议**: 使用 resolveTriggeredBy(context) 作为 createdBy/updatedBy，或统一在 insertManualFire 中设置。

**误报排除**: "system" 表示系统自动生成，但从审计角度应记录实际触发者。

---

## 发现 14: NopJobScheduleBizModel 的 resolveJobParams 返回 Map<String, Object>

**文件**: `NopJobScheduleBizModel.java:198-215`

**证据片段**:
```java
private Map<String, Object> resolveJobParams(NopJobSchedule schedule, Map<String, Object> overrideParams) {
    if (overrideParams != null) {
        return overrideParams;
    }
    Map<String, Object> scheduleParams = schedule.getJobParamsComponent().get_jsonMap();
    return Collections.emptyMap();
}
```

**严重程度**: P3

**风险**: 内部代码缺乏类型安全，容易出现参数拼写错误，难以重构和代码导航。

**建议**: 定义 JobParams 专用类型或在 buildManualFire 中直接使用 Map，确保一定类型约束。

**误报排除**: 这是私有方法不对外暴露，但内部代码也应有类型约束。

## 维度复核结论

| 编号 | 标题 | 判断 | 理由 |
|------|------|------|------|
| 07-01 | NopJobFireBizModel 用 fireStore.loadFire() 而非 requireEntity() | 保留 P2 | `CrudBizModel.requireEntity()` L913 调用 `checkDataAuth`，`loadFire()` 绕过此检查。cancelFire 中重复加载 3 次。 |
| 07-02 | NopJobFireBizModel 跨模块使用 scheduleStore | 降级 P3 | `scheduleStore.loadSchedule()` 是只读查询，Fire 聚合根读取关联 Schedule 信息合理。通过 Biz 接口通信的规范主要适用于写操作。 |
| 07-03 | NopJobScheduleBizModel 用 updateEntityDirectly() | 降级 P3 | private `persistSchedule()` 被已通过 `requireEntity()` 权限检查的 `@BizMutation` 方法调用。Schedule 非标准状态变更用 DAO 直接更新可接受。 |
| 07-04 | triggerNow 使用 Map<String, Object> 参数 | 保留 P3 | 审计已评为 P3。缺乏类型安全但功能正确，API 设计改进建议。 |

### 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 07-01 | P2 | `NopJobFireBizModel.java` | loadFire() 绕过 CrudBizModel 的 checkDataAuth 检查 |
| 07-02 | P3 | `NopJobFireBizModel.java` | 跨模块使用 scheduleStore（只读查询，可接受） |
| 07-03 | P3 | `NopJobScheduleBizModel.java` | updateEntityDirectly() 绕过标准 ORM 更新路径 |
| 07-04 | P3 | `NopJobScheduleBizModel.java` | triggerNow 使用 Map<String, Object> 参数缺乏类型安全 |
