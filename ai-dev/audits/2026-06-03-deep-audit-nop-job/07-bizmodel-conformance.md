# 维度 07：BizModel 规范遵循

## 第 1 轮（初审）

### [维度07-01] NopJobScheduleBizModel.persistSchedule 绕过 CrudBizModel 标准 API，直接操作 DAO

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java:141-161`
- **证据片段**:
```java
IOrmEntityDao<NopJobSchedule> ormDao = (IOrmEntityDao<NopJobSchedule>) daoProvider().daoFor(NopJobSchedule.class);
...
ormDao.tryUpdateManyWithVersionCheck(...)
...
ormDao.updateEntityDirectly(schedule);
```
- **严重程度**: P3
- **现状**: `persistSchedule()` 方法绕过了 `CrudBizModel.updateEntity()` 的标准流程（数据权限检查、唯一性检查、metaFilter 检查等），直接使用底层 DAO 实现乐观锁重试。
- **风险**: 丢失数据权限检查和唯一性约束检查。但这是调度引擎内部的状态流转（仅修改 scheduleStatus、nextFireTime 等引擎字段），风险有限。
- **建议**: 在 `persistSchedule()` 方法上添加注释说明这是边界场景及原因。
- **信心水平**: 90%
- **误报排除**: 调度引擎场景，CrudBizModel 的标准 updateEntity 不支持乐观锁重试+引擎字段恢复的复合需求。
- **复核状态**: 未复核

### [维度07-02] triggerNow 使用 Map<String, Object> 代替类型安全结构

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java:108`
- **证据片段**:
```java
public void triggerNow(@Name("id") String id, @Name("overrideParams") Map<String, Object> overrideParams, IServiceContext context)
```
- **严重程度**: P3
- **现状**: `triggerNow` 方法的 `overrideParams` 参数使用 `Map<String, Object>`，缺乏类型安全。
- **风险**: API 消费者无法从签名获知结构约定。但 overrideParams 本质上是透传给 job executor 的自由格式参数。
- **建议**: 考虑定义 `@RequestBean` DTO，或保持现状作为合理折衷。
- **信心水平**: 70%
- **误报排除**: overrideParams 是透传的任意 JSON 参数，定义 DTO 反而过度约束灵活性。
- **复核状态**: 未复核

### [维度07-03] resolveTriggeredBy 方法在两个 BizModel 中重复实现

- **文件**: `NopJobScheduleBizModel.java:262-273` 和 `NopJobFireBizModel.java:155-166`
- **证据片段**: 两个文件中存在完全相同的 `private String resolveTriggeredBy(IServiceContext context)` 实现。
- **严重程度**: P3
- **现状**: 代码重复。
- **风险**: 维护成本增加。
- **建议**: 提取到共享工具类。
- **信心水平**: 95%
- **误报排除**: 明确的代码重复。
- **复核状态**: 未复核

### [维度07-04] NopJobFireBizModel.cancelFire 中 fireStore.loadFire 被重复调用

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobFireBizModel.java:54-58`
- **证据片段**:
```java
if (!fireStore.cancelFire(id)) {
    throwCancelNotAllowed(fireStore.loadFire(id), "cancelFire");
}
...
afterEntityChange(fireStore.loadFire(id), "cancelFire", context);
```
- **严重程度**: P3
- **现状**: `fireStore.loadFire(id)` 在方法中被调用了多次。
- **风险**: 多余的数据库查询，性能浪费。
- **建议**: 复用已加载的对象。
- **信心水平**: 85%
- **误报排除**: cancelFire 是 CAS 操作，之后重新加载获取最新状态是合理的，但异常分支可复用已有对象。
- **复核状态**: 未复核

### 正向确认（无问题）

- 三个 BizModel 均正确继承 CrudBizModel<T> 并实现 I*Biz 接口
- 构造函数均正确调用 setEntityName()
- @BizModel 注解与 ORM 实体 + xmeta 一一对应
- 所有变更方法均使用 @BizMutation
- 所有需要实体的方法均通过 requireEntity() 获取
- 所有参数均使用 @Name 注解
- 错误处理使用 NopException + ErrorCode + .param()
- NopJobTaskBizModel 覆写 delete 阻止直接删除（合理业务约束）
- 未直接注入其他 BizModel 实现类
