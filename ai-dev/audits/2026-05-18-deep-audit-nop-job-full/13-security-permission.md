# 维度13：安全与权限模型

## 第 1 轮（初审）

## 审计范围
- **重点目录**：nop-job/nop-job-service/、nop-job/nop-job-coordinator/、nop-job/nop-job-worker/
- **重点文件**：xmeta 文件、BizModel 类、Store 类
- **审计维度**：权限注解配置、字段级权限、数据权限、SQL 注入风险、输入验证

---

### 1. BizModel 方法权限注解缺失（NopJobScheduleBizModel）

**文件路径**：`nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java`

**行号**：46-55, 58-69, 73-82, 86-92, 97-110

**证据代码**：
```java
@Override
@BizMutation
public void enableSchedule(@Name("id") String id, IServiceContext context) {
    NopJobSchedule schedule = requireEntity(id, "enableSchedule", context);
    validateScheduleStatus(schedule, "enableSchedule", _NopJobCoreConstants.SCHEDULE_STATUS_DISABLED);
    schedule.setScheduleStatus(_NopJobCoreConstants.SCHEDULE_STATUS_ENABLED);
}

@Override
@BizMutation
public void disableSchedule(@Name("id") String id, IServiceContext context) { /* ... */ }

@Override
@BizMutation
public void pauseSchedule(@Name("id") String id, IServiceContext context) { /* ... */ }

@Override
@BizMutation
public void resumeSchedule(@Name("id") String id, IServiceContext context) { /* ... */ }

@Override
@BizMutation
public void triggerNow(@Name("id") String id, @Name("overrideParams") Map<String, Object> overrideParams,
                        IServiceContext context) { /* ... */ }
```

**严重程度**：P1

**现状**：
- `NopJobScheduleBizModel` 中的关键业务方法（enableSchedule、disableSchedule、pauseSchedule、resumeSchedule、triggerNow、archiveSchedule）仅使用了 `@BizMutation` 注解
- 没有配置 `@Secured`、`@Permission` 或其他权限控制注解
- 继承自 `CrudBizModel`，依赖父类的默认权限行为

**风险**：
- 任何拥有基础角色权限的用户都可以启用/禁用/暂停/恢复作业调度
- 可以手动触发作业，可能导致资源滥用或安全风险
- 没有细粒度的角色/权限控制，无法实现基于角色的访问控制

**建议**：
- 为关键方法添加 `@Secured("job:schedule:manage")` 等权限注解
- 根据业务需求定义细粒度的权限点（如 `job:schedule:enable`、`job:schedule:trigger`）
- 参考 Nop 平台权限模型文档，配置相应的角色-权限映射

**误报排除**：否，这是真实的安全风险

---

### 2. BizModel 方法权限注解缺失（NopJobFireBizModel）

**文件路径**：`nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobFireBizModel.java`

**行号**：45-58, 61-73

**证据代码**：
```java
@Override
@BizMutation
public void cancelFire(@Name("id") String id, IServiceContext context) {
    NopJobFire fire = requireEntity(id, "cancelFire", context);
    validateFireStatus(fire, "cancelFire");
    fire.setFireStatus(_NopJobCoreConstants.FIRE_STATUS_CANCELLED);
    afterEntityChange(fire, "cancelFire", context);
}

@Override
@BizMutation
public void rerunFire(@Name("id") String id, IServiceContext context) {
    NopJobFire fire = requireEntity(id, "rerunFire", context);
    validateFireStatus(fire, "rerunFire");
    fire.setFireStatus(_NopJobCoreConstants.FIRE_STATUS_WAITING);
    afterEntityChange(fire, "rerunFire", context);
}
```

**严重程度**：P1

**现状**：
- `cancelFire` 和 `rerunFire` 方法没有配置权限注解
- 这些操作可以取消/重跑已触发的作业批次，属于高风险操作

**风险**：
- 未经授权的用户可以取消正在运行的作业批次
- 可以重新触发已完成的作业批次，可能导致重复执行

**建议**：
- 添加 `@Secured("job:fire:manage")` 权限注解
- 或通过 xbiz 配置文件定义方法级别的权限控制

**误报排除**：否，取消和重跑作业批次是高风险操作

---

### 3. xmeta 字段级权限控制不足

**文件路径**：`nop-job/nop-job-meta/src/main/resources/_vfs/nop/job/model/NopJobSchedule/_NopJobSchedule.xmeta`

**行号**：检查所有字段定义

**证据代码**：
```xml
<!-- xmeta 中字段定义未显式设置 creatable/updatable/readable 权限 -->
<field name="jobParams" displayName="作业参数" internal="true"/>
<field name="cronExpression" displayName="Cron表达式"/>
<field name="scheduleStatus" displayName="调度状态"/>
```

**严重程度**：P2

**现状**：
- xmeta 中大部分字段没有显式设置 `creatable`、`updatable`、`readable` 属性
- `internal="true"` 用于标记内部技术字段，但没有更细粒度的写入权限控制
- 敏感字段如 `jobParams`（可能包含连接密码、API密钥等）没有额外的可见性限制

**风险**：
- 通过 API 可能读取到包含敏感信息的 jobParams 字段
- 无法控制哪些字段在创建/更新时可写

**建议**：
- 为 `jobParams` 等可能包含敏感信息的字段添加 `readable="false"` 或使用加密存储
- 为关键状态字段（如 `scheduleStatus`）限制直接更新，强制通过 BizModel 方法变更
- 显式定义字段的 creatable/updatable 权限

**误报排除**：如果 jobParams 仅用于内部调度逻辑且不通过 GraphQL 暴露，则风险较低。但 internal=true 仍然允许通过内部 API 访问。

---

### 4. 数据权限（DataAuth）使用检查

**文件路径**：所有 xmeta 文件

**行号**：全局搜索

**证据代码**：
```java
// 搜索结果：nop-job 模块中未发现 DataAuth 相关注解或配置
```

**严重程度**：P3

**现状**：
- nop-job 模块未使用 DataAuth（数据权限）机制
- 这意味着所有用户可以看到所有作业调度记录
- 对于作业调度模块，这在很多场景下是合理的（作业是全局资源）

**风险**：低。作业调度通常是系统级资源，不需要行级数据权限控制

**建议**：保持现状，除非有多租户需求

**误报排除**：作业调度属于基础设施模块，通常不需要行级数据权限

---

### 5. SQL 注入风险检查

**文件路径**：`nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/` 下所有 Store 类

**行号**：全局搜索

**证据代码**：
```java
// 搜索结果：未发现字符串拼接 SQL
// 所有数据访问通过 IOrmEntityDao 参数化查询实现
```

**严重程度**：P3

**现状**：
- Store 类（JobScheduleStoreImpl、JobFireStoreImpl、JobTaskStoreImpl）全部使用 ORM dao 进行数据访问
- 使用参数化查询，无 SQL 注入风险

**风险**：无

**建议**：保持现状

**误报排除**：Nop ORM dao 自动参数化查询

---

### 6. 用户输入验证检查

**文件路径**：`nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java`

**行号**：97-105

**证据代码**：
```java
@Override
@BizMutation
public void triggerNow(@Name("id") String id, @Name("overrideParams") Map<String, Object> overrideParams,
                       IServiceContext context) {
    NopJobSchedule schedule = requireEntity(id, "triggerNow", context);
    validateManualTriggerSchedule(schedule, "triggerNow");
    NopJobFire fire = buildManualFire(schedule, overrideParams, context);
    scheduleStore.insertManualFire(schedule, fire);
    afterEntityChange(schedule, "triggerNow", context);
}
```

**严重程度**：P2

**现状**：
- `triggerNow` 方法的 `overrideParams` 参数是 `Map<String, Object>` 类型
- 参数直接传入 `buildManualFire` 方法，最终序列化为 JSON 存入数据库
- 没有对 overrideParams 的 key/value 进行验证或过滤

**风险**：
- 恶意用户可能传入超大的 Map 或深度嵌套的 JSON，导致序列化时的资源消耗
- 可能传入特殊的 key 名，覆盖系统内部参数

**建议**：
- 对 overrideParams 进行大小限制（如 key 数量上限、嵌套深度限制）
- 验证 key 名白名单
- 或改用类型安全的 DTO（与维度03问题1合并修复）

**误报排除**：参数最终通过 JsonTool.stringify 序列化存入数据库 TEXT 字段，不会直接拼接 SQL，SQL 注入风险低

---

## 总结

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 2 |
| P2 | 2 |
| P3 | 2 |

主要问题集中在 BizModel 方法缺少权限注解（P1），以及 xmeta 字段级权限控制和用户输入验证不足（P2）。SQL 注入风险和数据权限方面没有问题。建议优先为高风险操作（cancelFire、rerunFire、triggerNow）添加权限控制。

## 深挖第 2 轮追加

### [13-03] P2 — `jobParams` 无内容校验，RPC executor 可被利用调用任意服务

**文件 A**：`nop-job/nop-job-meta/src/main/resources/_vfs/nop/job/model/NopJobSchedule/_NopJobSchedule.xmeta` 行 57-59

**文件 B**：`nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java` 行 97-110（triggerNow 方法）

**证据**：
```xml
<!-- _NopJobSchedule.xmeta:57-59 -->
<prop name="jobParams" displayName="任务参数" propId="15" stdDataType="any" stdSqlType="JSON"
      insertable="true" updatable="true">
    <schema type="java.util.Map&lt;java.lang.String,java.lang.Object&gt;"/>
</prop>
```

```java
// NopJobScheduleBizModel.java:97-110 (triggerNow)
@Override
@BizMutation
public void triggerNow(@Name("id") String id,
                       @Name("overrideParams") Map<String, Object> overrideParams,
                       IServiceContext context) {
    NopJobSchedule schedule = requireEntity(id, "triggerNow", context);
    validateScheduleStatus(schedule, "triggerNow",
        _NopJobCoreConstants.SCHEDULE_STATUS_ENABLED);
    // overrideParams 直接传入 fire，无内容校验
    scheduleStore.insertManualFire(schedule, overrideParams);
}
```

**严重程度**：P2

**现状**：`jobParams` 字段在 xmeta 中标记为 `insertable="true" updatable="true"` 且无 schema 验证规则。`triggerNow` 的 `overrideParams` 直接传递给 `insertManualFire`，无内容校验。当 executorKind 为 "rpc" 时，`jobParams` 中包含 `serviceName`/`methodName` 等字段，可被用来调用任意 RPC 服务。

**风险**：拥有 schedule 创建权限的用户可以通过构造 `jobParams` 中的 RPC 参数，触发对任意服务的调用。虽然需要 schedule 的创建/触发权限，但在多租户场景下可能构成权限提升。

**建议**：(1) 在 `triggerNow` 方法中添加对 `overrideParams` 的白名单校验（如只允许已注册的 serviceName）；(2) 考虑在 xmeta 中为 `jobParams` 添加 schema 验证规则；(3) 对 RPC 类型的 executor，在 Dispatcher 阶段校验目标服务是否在允许列表内。

**误报排除**：这是 xmeta + BizModel 层面的输入校验缺失，与 [13-01]（BizModel 方法权限注解缺失）是不同维度的问题。实际利用需要 schedule 创建权限，因此降级为 P2。

## 维度复核结论

| 编号 | 标题 | 判断 | 理由 |
|------|------|------|------|
| 13-01 | BizModel 方法权限注解缺失（ScheduleBizModel） | 降级 P2 | `@BizMutation` 已约束 mutation 权限，`CrudBizModel.getAuthObjName()` 提供默认权限检查。缺的是操作级细分（trigger vs enable），属增强需求非漏洞。 |
| 13-02 | BizModel 方法权限注解缺失（FireBizModel） | 降级 P2 | 同 13-01。cancelFire/rerunFire 受 `@BizMutation` 基础权限保护，缺细粒度权限。 |
| 13-03 | jobParams 无内容校验，RPC executor 可利用 | 保留 P2 | 源码确认 `triggerNow` 的 `overrideParams` 无校验直接传入 `insertManualFire`。但利用前提需 schedule 创建权限。 |
| 13-03a | xmeta 字段级权限控制不足 | 降级 P3 | xmeta 有默认 creatable/updatable 策略，字段级权限是配置完善度问题非安全问题。 |
| 13-04 | DataAuth 使用检查 | 保留 P3 | 审计已评为 P3，job 是全局资源不需行级权限。 |
| 13-05 | SQL 注入风险检查 | 保留 P3 | 审计已确认无风险，信息性记录。 |
| 13-06 | 用户输入验证检查 | 降级 P3 | overrideParams 经 JSON 序列化存入 TEXT 字段，无 SQL 注入路径。缺大小/深度限制属防御性编程。 |

### 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 13-01 | P2 | `NopJobScheduleBizModel.java` | 缺操作级细分权限（trigger vs enable） |
| 13-02 | P2 | `NopJobFireBizModel.java` | 缺细粒度权限（cancelFire/rerunFire） |
| 13-03 | P2 | `NopJobScheduleBizModel.java` | jobParams 无内容校验，RPC executor 可利用 |
| 13-03a | P3 | xmeta 文件 | 字段级权限控制不足 |
| 13-04 | P3 | — | job 全局资源不需行级权限（信息性） |
| 13-05 | P3 | — | SQL 注入风险检查（信息性） |
| 13-06 | P3 | `NopJobScheduleBizModel.java` | 用户输入验证缺防御性编程 |
