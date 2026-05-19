# 维度11：XMeta与BizModel对齐

## 第 1 轮（初审）

### 检查范围

| BizModel 类 | xmeta 文件 | xbiz 文件 |
|---|---|---|
| `NopJobScheduleBizModel` | `_NopJobSchedule.xmeta` + `NopJobSchedule.xmeta`（空 delta） | `_NopJobSchedule.xbiz` + `NopJobSchedule.xbiz`（空 delta） |
| `NopJobFireBizModel` | `_NopJobFire.xmeta` + `NopJobFire.xmeta`（空 delta） | `_NopJobFire.xbiz` + `NopJobFire.xbiz`（空 delta） |
| `NopJobTaskBizModel` | `_NopJobTask.xmeta` + `NopJobTask.xmeta`（空 delta） | `_NopJobTask.xbiz` + `NopJobTask.xbiz`（空 delta） |

三个 BizModel 均继承 `CrudBizModel<T>`，提供标准 CRUD 操作。BizModel 的自定义方法全部为 `@BizMutation`（无 `@BizQuery`、无 `@BizLoader`）。

---

### 发现 1：状态字段及内部跟踪字段通过通用 save 可直接修改 — P1

**文件路径**：
- `nop-job/nop-job-meta/src/main/resources/_vfs/nop/job/model/NopJobSchedule/_NopJobSchedule.xmeta`
- `nop-job/nop-job-meta/src/main/resources/_vfs/nop/job/model/NopJobFire/_NopJobFire.xmeta`
- `nop-job/nop-job-meta/src/main/resources/_vfs/nop/job/model/NopJobTask/_NopJobTask.xmeta`

**证据代码（NopJobSchedule.xmeta，propId 7/23-28/35-38）**：
```xml
<prop name="scheduleStatus" ... insertable="true" updatable="true">
    <schema type="java.lang.Integer" dict="job/schedule-status"/>
</prop>
<prop name="fireCount" ... insertable="true" updatable="true">
    <schema type="java.lang.Long"/>
</prop>
<prop name="activeFireCount" ... insertable="true" updatable="true">
    <schema type="java.lang.Integer"/>
</prop>
<prop name="nextFireTime" ... insertable="true" updatable="true">
    <schema type="java.sql.Timestamp"/>
</prop>
<prop name="totalFireCount" ... insertable="true" updatable="true">
    <schema type="java.lang.Long"/>
</prop>
```

**证据代码（BizModel 中的状态验证逻辑，NopJobScheduleBizModel.java:149-170）**：
```java
private void validateScheduleStatus(NopJobSchedule schedule, String action, int... allowedStatuses) {
    if (schedule.getScheduleStatus() != null
            && schedule.getScheduleStatus() == _NopJobCoreConstants.SCHEDULE_STATUS_ARCHIVED) {
        throw new NopException(ERR_JOB_SCHEDULE_ALREADY_ARCHIVED)
                .param("jobScheduleId", schedule.getJobScheduleId())
                ...
    }
    for (int allowedStatus : allowedStatuses) {
        if (schedule.getScheduleStatus() != null && schedule.getScheduleStatus() == allowedStatus) {
            return;
        }
    }
    throw new NopException(ERR_JOB_SCHEDULE_INVALID_STATUS_TRANSITION) ...
}
```

**现状**：
- `scheduleStatus`（propId=7）在 xmeta 中 `updatable="true"`，客户端可通过 `NopJobSchedule__save` 通用 mutation 直接设置任意状态值，完全绕过 `enableSchedule`/`disableSchedule`/`pauseSchedule`/`resumeSchedule`/`archiveSchedule` 中的状态验证。
- `fireStatus`（propId=9）在 NopJobFire xmeta 中 `updatable="true"`，同理可绕过 `cancelFire`/`rerunFire` 的状态检查。
- 跟踪字段（`fireCount`, `activeFireCount`, `nextFireTime`, `lastFireTime`, `lastEndTime`, `lastFireStatus`, `lastDurationMs`, `totalFireCount`, `successFireCount`, `failFireCount`）全部 `updatable="true"`，客户端可直接篡改调度统计数据。
- NopJobFire 的执行跟踪字段（`startTime`, `endTime`, `durationMs`, `plannerInstanceId`, `dispatchInstanceId`）同理。
- NopJobTask 的执行字段（`taskStatus`, `workerInstanceId`, `workerAddress`, `startTime`, `endTime`, `durationMs`）同理。

**风险**：
- 授权用户可通过通用 save API 将 `scheduleStatus` 设为任意非法值（如从 DISABLED 直接跳到 COMPLETED），导致调度引擎行为异常。
- 统计字段被篡改后，监控数据失真。

**建议**：
在 delta xmeta 中将以下字段设为 `updatable="false"`（或 `updatable="false"` + `insertable="false"`）：
- NopJobSchedule: `scheduleStatus`, `fireCount`, `activeFireCount`, `lastFireTime`, `lastEndTime`, `nextFireTime`, `lastFireStatus`, `lastDurationMs`, `totalFireCount`, `successFireCount`, `failFireCount`
- NopJobFire: `fireStatus`, `plannerInstanceId`, `dispatchInstanceId`, `startTime`, `endTime`, `durationMs`
- NopJobTask: `taskStatus`, `workerInstanceId`, `workerAddress`, `startTime`, `endTime`, `durationMs`

状态变更应只通过专用 BizMutation 方法进行。

**误报排除**：如果项目有意允许管理员通过通用 API 直接修改这些字段（管理后台手动修复场景），则此发现可降级为 P2。但应至少在 delta xmeta 中添加注释说明设计意图。

---

### 发现 2：孤立 xbiz 文件引用不存在的实体类 — P2

**文件路径**：
- `nop-job/nop-job-service/src/main/resources/_vfs/nop/job/model/NopJobPlan/_NopJobPlan.xbiz`
- `nop-job/nop-job-service/src/main/resources/_vfs/nop/job/model/NopJobInstance/_NopJobInstance.xbiz`
- `nop-job/nop-job-service/src/main/resources/_vfs/nop/job/model/NopJobInstanceHis/_NopJobInstanceHis.xbiz`
- `nop-job/nop-job-service/src/main/resources/_vfs/nop/job/model/NopJobDefinition/_NopJobDefinition.xbiz`
- `nop-job/nop-job-service/src/main/resources/_vfs/nop/job/model/NopJobAssignment/_NopJobAssignment.xbiz`

**证据代码（_NopJobPlan.xbiz）**：
```xml
<biz-gen:DefaultBizGenExtends xpl:lib="/nop/core/xlib/biz-gen.xlib" forEntity="true"
                              entityName="io.nop.job.dao.entity.NopJobPlan"/>
```

**证据代码（grep 结果 — 仅 3 个实体存在）**：
```
nop-job/nop-job-dao/.../entity/NopJobSchedule.java
nop-job/nop-job-dao/.../entity/NopJobFire.java
nop-job/nop-job-dao/.../entity/NopJobTask.java
```

**现状**：
- ORM 模型（`model/nop-job.orm.xml`）只定义了 3 个实体（NopJobSchedule、NopJobFire、NopJobTask）。
- 但 xbiz 目录中有 5 个额外的 `_*.xbiz` + delta xbiz 文件引用了 `io.nop.job.dao.entity.NopJobPlan`、`NopJobInstance`、`NopJobInstanceHis`、`NopJobDefinition`、`NopJobAssignment`，这些实体类不存在。
- 这些实体也没有对应的 xmeta 文件或 BizModel Java 类。

**风险**：
- 如果这些 xbiz 文件在运行时被加载，gen-extends 会尝试查找不存在的实体类，可能导致启动或首次访问时异常。
- 增加维护困惑：开发者不清楚这些文件是否应该存在。

**建议**：删除这 5 个目录下的 xbiz 文件（共 10 个：5 个 `_*.xbiz` + 5 个 delta xbiz），或确认它们属于另一个模块并迁移。

**误报排除**：如果这些实体通过运行时动态注册（其他 jar 包提供实体类），则不会出错。但 ORM 模型文件中也没有对应定义，此可能性低。

---

### 发现 3：三个 delta xmeta 文件完全空白 — P2

**文件路径**：
- `nop-job/nop-job-meta/src/main/resources/_vfs/nop/job/model/NopJobSchedule/NopJobSchedule.xmeta`
- `nop-job/nop-job-meta/src/main/resources/_vfs/nop/job/model/NopJobFire/NopJobFire.xmeta`
- `nop-job/nop-job-meta/src/main/resources/_vfs/nop/job/model/NopJobTask/NopJobTask.xmeta`

**证据代码（NopJobSchedule.xmeta，完整文件）**：
```xml
<?xml version="1.0" encoding="UTF-8" ?>
<meta x:schema="/nop/schema/xmeta.xdef" xmlns:x="/nop/schema/xdsl.xdef" x:extends="_NopJobSchedule.xmeta">
    <props/>
</meta>
```

**现状**：三个 delta xmeta 文件均为空壳，只继承生成文件，未做任何自定义。所有字段权限、类型、displayName 完全依赖生成文件的默认值。

**风险**：
- 发现 1 中描述的权限问题无法在 delta 层修复，因为当前 delta 没有覆盖任何字段。
- 生成文件（`_*.xmeta`）在 `mvn install` 时会被重新生成，手动修改会被覆盖。
- 当需要自定义字段权限或添加计算字段时，开发者需要意识到必须修改 delta 文件。

**建议**：作为发现 1 的修复手段，在 delta xmeta 中添加需要权限覆盖的字段定义。这是 Nop 平台 Delta 定制机制的正确使用方式。

**误报排除**：空 delta 文件本身不是错误——如果生成文件的默认值完全正确，空 delta 是正常状态。此发现的重要性在于它是发现 1 修复的前提。

---

### 确认合规的检查项

| 检查项 | 结果 | 说明 |
|--------|------|------|
| `@BizLoader` 方法对应 xmeta prop | ✅ 合规 | 三个 BizModel 中无任何 `@BizLoader` 方法，无需检查 |
| `@BizQuery`/`@BizMutation` 方法签名 | ✅ 合规 | 所有自定义方法都是 `@BizMutation`，返回 void，参数与 Biz 接口一致 |
| Biz 接口与 BizModel 实现一致性 | ✅ 合规 | `INopJobScheduleBiz`、`INopJobFireBiz`、`INopJobTaskBiz` 接口方法与 BizModel 实现完全匹配 |
| 字段类型与 Java 类型兼容 | ✅ 合规 | 所有 xmeta schema type 与 ORM 实体字段类型一致（Integer→int, String→String, Timestamp→Timestamp, Long→Long, Short→Short） |
| dict 值与 Java 常量对齐 | ✅ 合规 | 7 个 dict 文件与 `_NopJobCoreConstants` 完全一致（schedule-status 5 值、fire-status 7 值、task-status 8 值、trigger-source 3 值、trigger-type 4 值、block-strategy 4 值、executor-kind 3 值） |
| displayName 本地化 | ✅ 合规 | 所有 prop 均有中文 `displayName` 和 `i18n-en:displayName`，meta 根元素也有 |
| 死字段（xmeta 定义但无数据路径） | ✅ 合规 | 所有 xmeta prop 对应 ORM 实体字段，通过 CrudBizModel 的 get/findPage 可访问 |
| 未受控暴露（BizModel 无 xmeta） | ✅ 合规 | 所有 3 个 BizModel 均有对应的 xmeta 和 xbiz 文件 |
| 主键保护 | ✅ 合规 | `jobScheduleId`/`jobFireId`/`jobTaskId` 均 `updatable="false"` |
| 版本字段保护 | ✅ 合规 | `version` 字段均 `insertable="false" updatable="false" internal="true"` |
| 审计字段保护 | ✅ 合规 | `createdBy`/`createTime`/`updatedBy`/`updateTime` 均 `insertable="false" updatable="false"` |
| 组件字段标记 | ✅ 合规 | `jobParamsComponent`、`pauseCalendarSpecComponent`、`jobParamsSnapshotComponent`、`taskPayloadComponent`、`resultPayloadComponent` 均标记为 `internal="true" ext:kind="component"` |
| 关联字段配置 | ✅ 合规 | NopJobFire 的 `jobSchedule` 关联配置了 `ext:joinLeftProp`/`ext:joinRightProp`/`ext:joinRightDisplayProp`，并伴随 `jobSchedule.displayName` 展示字段 |
| 唯一键声明 | ✅ 合规 | NopJobSchedule: UK on (namespaceId, groupId, jobName)；NopJobFire: UK on (jobScheduleId, scheduledFireTime, triggerSource)；NopJobTask: UK on (jobFireId, taskNo) |

---

### 发现汇总

| # | 严重程度 | 概述 | 文件 |
|---|---------|------|------|
| 1 | **P1** | 状态字段和内部跟踪字段通过通用 save 可直接修改，绕过专用 mutation 的验证逻辑 | 三个 `_*.xmeta` |
| 2 | **P2** | 5 个 xbiz 目录引用不存在的实体类（NopJobPlan/Instance/InstanceHis/Definition/Assignment） | 10 个 xbiz 文件 |
| 3 | **P2** | 三个 delta xmeta 完全空白，无字段权限覆盖 | 3 个 `*.xmeta` delta 文件 |

## 深挖第 2 轮追加

### [11-04] P2 — `fireStatus` / `taskStatus` 的 `updatable="true"` 允许通过通用 save 绕过状态机

**文件 A**：`nop-job/nop-job-meta/src/main/resources/_vfs/nop/job/model/NopJobFire/_NopJobFire.xmeta` 行 55-57

**文件 B**：`nop-job/nop-job-meta/src/main/resources/_vfs/nop/job/model/NopJobTask/_NopJobTask.xmeta` 行 35-37

**证据**：
```xml
<!-- _NopJobFire.xmeta:55-57 -->
<prop name="fireStatus" displayName="批次状态" propId="9" i18n-en:displayName="Fire Status" mandatory="true"
      queryable="true" sortable="true" insertable="true" updatable="true">
    <schema type="java.lang.Integer" dict="job/fire-status"/>
</prop>

<!-- _NopJobTask.xmeta:35-37 -->
<prop name="taskStatus" displayName="任务状态" propId="4" i18n-en:displayName="Task Status" mandatory="true"
      queryable="true" sortable="true" insertable="true" updatable="true">
    <schema type="java.lang.Integer" dict="job/task-status"/>
</prop>
```

**严重程度**：P2

**现状**：`fireStatus`（NopJobFire）和 `taskStatus`（NopJobTask）在生成的 xmeta 中均为 `updatable="true"`。通过通用 CRUD `save` mutation，拥有对应实体保存权限的调用者可以将状态设为任意值，完全绕过 `NopJobFireBizModel.cancelFire()` 里的 `isCancelableStatus` 校验以及 coordinator 内部 Store 层的 CAS 状态转换。

**风险**：与第一轮发现 1（`scheduleStatus` 同源问题）同构但影响面更广——`fireStatus`/`taskStatus` 的合法变更路径涉及 planner、dispatcher、worker 三个角色的协调操作；通用 save 可以直接把 WAITING 改成 SUCCESS，跳过实际执行。

**建议**：在手写 `NopJobFire.xmeta` / `NopJobTask.xmeta` delta 中将 `fireStatus` / `taskStatus` 的 `updatable` 设为 `false`（与发现 1 对 `scheduleStatus` 的修复方式一致）。

**误报排除**：不是误报。Store 层内部通过 `scheduleDao().updateEntityDirectly()` 直接写 ORM 对象，不走 xmeta 校验——xmeta 的 `updatable` 只影响 GraphQL API 层。将 xmeta 中的 `updatable` 改为 `false` 不影响 coordinator 内部流程，只封锁了外部 API 的直接修改路径。

## 维度复核结论

| 编号 | 标题 | 判断 | 理由 |
|------|------|------|------|
| 发现1 | 状态字段及跟踪字段通过通用 save 可直接修改 | 保留 P1 | 证据充分且经 OrmEntityCopier/ObjMetaBasedValidator 源码验证。`CrudBizModel.save()` 调用 `validateForSave()`，其过滤条件是 `propMeta.isInsertable()`（非 `isUpdatable()`）。`scheduleStatus` 在 xmeta 中 `insertable="true" updatable="true"`（行49-51），可通过 `__save` mutation 设置任意值，完全绕过 `enableSchedule`/`disableSchedule` 等专用方法中的状态验证逻辑（`validateScheduleStatus` 行149-170）。 |
| 发现2 | 孤立 xbiz 文件引用不存在的实体类 | 降级为 P2 | 与维度10发现1完全重叠（同一问题两个角度）。维度10已保留为 P1，此处降级避免重复计数。 |
| 发现3 | 三个 delta xmeta 文件完全空白 | 保留 P2 | 证据准确。`NopJobSchedule.xmeta` 确实是空壳 `<props/>`。这是发现1修复的前提条件——delta 层是覆盖字段权限的正确位置。本身不是缺陷，但与 P1 发现直接关联。 |

### 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 发现1 | P1 | `nop-job-meta/.../NopJobSchedule/_NopJobSchedule.xmeta` | scheduleStatus 等状态/跟踪字段 insertable=true updatable=true，可通过通用 save 绕过专用方法的状态验证 |
| 发现2 | P2 | `nop-job-service/.../_vfs/nop/job/model/{NopJobPlan,...}/*.xbiz` | 孤立 xbiz 引用不存在实体（与维度10-发现1重复，保留维度10的P1） |
| 发现3 | P2 | `nop-job-meta/.../NopJobSchedule/NopJobFire/NopJobTask.xmeta` | delta xmeta 为空壳，无法覆盖字段权限（P1发现1修复前提） |
