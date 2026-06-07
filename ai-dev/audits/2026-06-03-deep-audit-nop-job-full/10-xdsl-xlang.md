# 维度 10：XDSL 与 XLang 正确性 + 维度 11：XMeta 与 BizModel 对齐

## 第 1 轮（初审）

### [维度10-01] 遗留实体生成的 view.xml 引用不存在的 xmeta/bizObj

- **文件**: `nop-job/nop-job-web/.../pages/NopJobInstance/_gen/_NopJobInstance.view.xml` 等 4 个目录
- **证据片段**:
  ```xml
  <view ... bizObjName="NopJobAssignment" ...>
    <objMeta>/nop/job/model/NopJobAssignment/NopJobAssignment.xmeta</objMeta>
  </view>
  ```
- **严重程度**: P2
- **现状**: NopJobAssignment、NopJobDefinition、NopJobInstance、NopJobInstanceHis 四个实体的 view.xml 引用的 xmeta 在 nop-job-meta 中不存在（当前 ORM 模型仅定义了 3 个实体）。
- **风险**: 通过直接 URL 访问这些页面会因 xmeta 缺失而报错。增加维护者认知负担。
- **建议**: 删除 4 组残留文件（与维度05-01相同发现）。
- **信心水平**: 高
- **误报排除**: gen-page.xgen 只为当前存在的 xmeta 生成页面，这些是旧模型遗留产物。
- **复核状态**: 未复核

### [维度11-01] NopJobTask.xmeta 缺少对 5 个引擎管控字段的权限限制

- **文件**: `nop-job/nop-job-meta/src/main/resources/_vfs/nop/job/model/NopJobTask/NopJobTask.xmeta`
- **证据片段**: delta xmeta 已限制 9 个字段（taskStatus, workerInstanceId 等）为 `insertable="false" updatable="false"`，但遗漏了 progress, progressMessage, targetHost, shardingIndex, shardingTotal 五个同样由引擎/worker 自动写入的字段。
- **严重程度**: P2
- **现状**: 5 个引擎管控字段在 xmeta 中仍为 `insertable="true" updatable="true"`。通过 GraphQL API 可直接调用 `NopJobTask__update` 写入这些字段。对比 NopJobSchedule delta xmeta 对所有引擎字段做了完整限制。
- **风险**: 通过 API 可篡改应由引擎管控的字段。但 view 层已移除 Task 的 update/add 按钮，降低了直接风险。
- **建议**: 在 NopJobTask.xmeta 中为这 5 个字段添加 `insertable="false" updatable="false"`。
- **信心水平**: 高
- **误报排除**: 对比 NopJobSchedule.xmeta 对所有 11 个引擎字段都做了完整限制，Task 的遗漏是明显的不一致。
- **复核状态**: 未复核

### [维度11-02] NopJobFire.xmeta 缺少对程序性设置字段的权限限制

- **文件**: `nop-job/nop-job-meta/src/main/resources/_vfs/nop/job/model/NopJobFire/NopJobFire.xmeta`
- **严重程度**: P3
- **现状**: 已限制 8 个字段，但遗漏了 triggerSource, scheduledFireTime, triggeredBy, jobParamsSnapshot, retryRecordId 五个程序性设置字段。view 层已移除按钮，但 API 仍可调用。
- **建议**: 添加相应字段的 insertable/updatable 限制。
- **信心水平**: 中
- **误报排除**: 风险低于 D11-01 因为 Fire 的 view 层已移除所有 CRUD 按钮。
- **复核状态**: 未复核

### [维度11-03] NopJobFire/NopJobTask _gen view 保留 CRUD 页面定义

- **文件**: `nop-job/nop-job-web/.../pages/NopJobFire/_gen/_NopJobFire.view.xml` 等
- **严重程度**: P3
- **现状**: _gen view 包含 add/edit 表单和 save/update 页面定义。Delta view 移除了 UI 按钮但底层 API 端点仍存在。NopJobTaskBizModel 覆写了 delete 但 update/save 未被覆写。
- **建议**: 在 delta view 中移除 add/update 页面，或在 xmeta 中限制更多字段。
- **信心水平**: 高
- **误报排除**: _gen 文件是自动生成的，正确修复方式是在 delta 层处理。
- **复核状态**: 未复核

## 无问题确认

| 检查项 | 结论 |
|--------|------|
| x:schema 引用 | 所有 XDSL 文件 schema 引用正确 |
| x:extends 使用 | 保留层和 Delta 文件均正确 |
| x:override 语义 | view delta 中 remove 使用正确 |
| beans.xml class 引用 | 全部与 Java 类路径一致 |
| BizModel 方法与 xbiz 对齐 | 3 个 BizModel 全部正确映射 |
| BizModel 方法与 xmeta 对齐 | 8 个自定义 action 全部覆盖 |
| NopJobSchedule xmeta 引擎字段 | 11 个引擎字段全部限制（最完整） |
| BizProxyFactoryBean 注册 | 3 组注册模式完全正确 |
