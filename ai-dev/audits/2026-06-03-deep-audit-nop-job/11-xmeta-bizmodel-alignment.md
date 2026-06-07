# 维度 11：XMeta 与 BizModel 对齐

## 第 1 轮（初审）

### [维度11-01] NopJobFire.xmeta 未限制创建时字段的 updatable 权限

- **文件**: `nop-job-meta/src/main/resources/_vfs/nop/job/model/NopJobFire/NopJobFire.xmeta:1-14`
- **证据片段**:
```xml
<meta x:schema="/nop/schema/xmeta.xdef" xmlns:x="/nop/schema/xdsl.xdef" x:extends="_NopJobFire.xmeta">
    <props>
        <prop name="fireStatus" insertable="false" updatable="false"/>
        <prop name="plannerInstanceId" insertable="false" updatable="false"/>
        ...
    </props>
</meta>
```
缺少 updatable="false" 的字段：scheduledFireTime, triggerSource, triggeredBy, jobScheduleId, executorKind, retryPolicyId。
- **严重程度**: P3
- **现状**: 正确限制了 8 个运行时字段，但未限制创建时设定后不应修改的字段。
- **风险**: 通过 GraphQL CRUD API 理论上可修改 scheduledFireTime 或 jobScheduleId，破坏数据一致性。
- **建议**: 为这些字段添加 updatable="false"。
- **信心水平**: 高
- **误报排除**: CrudBizModel.update() 确实会根据 xmeta 的 updatable 属性过滤输入。
- **复核状态**: 未复核

### [维度11-02] NopJobTask.xmeta 未限制创建时字段的 updatable 权限

- **文件**: `nop-job-meta/src/main/resources/_vfs/nop/job/model/NopJobTask/NopJobTask.xmeta:1-15`
- **证据片段**: 正确限制 9 个运行时字段，但 jobFireId, taskNo, targetHost, shardingIndex, shardingTotal 仍可被 update。
- **严重程度**: P2
- **现状**: Task 的生命周期完全由引擎管理。修改 jobFireId 会破坏关联关系，修改 targetHost 可能导致任务路由错误。
- **风险**: 用户通过 GraphQL API 修改关键字段可能破坏引擎一致性。
- **建议**: 为所有引擎管理字段添加 updatable="false"，或考虑完全禁止 update。
- **信心水平**: 高
- **误报排除**: NopJobTaskBizModel 继承的 CrudBizModel.update() 没有被 override，xmeta 是唯一保护层。
- **复核状态**: 未复核

### 正向确认

- NopJobSchedule.xmeta 正确限制了所有引擎管理字段（11 个字段 insertable=false updatable=false）
- Dict 定义与 _NopJobCoreConstants 完全对齐（7 个 dict）
- displayName 本地化全覆盖（中英文）
- _service.beans.xml 中 BizProxyFactoryBean 配置与 BizModel 类完全匹配
