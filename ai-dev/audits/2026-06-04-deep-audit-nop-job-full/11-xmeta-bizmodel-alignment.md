# 维度 11：XMeta 与 BizModel 对齐

## 审计范围

3 对 xmeta 文件和 3 个 BizModel 类。

## 第 1 轮（初审）发现

### [维度11-01] NopJobFire xmeta 中部分引擎内部字段未在保留文件中限制 insertable/updatable

- **文件**: `nop-job/nop-job-meta/src/main/resources/_vfs/nop/job/model/NopJobFire/NopJobFire.xmeta`
- **证据片段**:
  ```xml
  <!-- 保留文件已限制的字段 -->
  <prop name="fireStatus" insertable="false" updatable="false"/>
  <prop name="plannerInstanceId" insertable="false" updatable="false"/>
  <!-- 但以下字段未限制 -->
  <!-- triggerSource, retryRecordId, scheduledFireTime, jobParamsSnapshot, 
       partitionIndex, executorKind, retryPolicyId, triggeredBy -->
  ```
- **严重程度**: P3
- **现状**: `NopJobFire.xmeta` 保留文件限制了 fireStatus、plannerInstanceId 等 8 个运行时字段，但 triggerSource（触发来源）、retryRecordId（重试记录ID）、scheduledFireTime 等引擎内部字段未限制。理论上可通过 GraphQL mutation 设置这些字段。
- **风险**: 低。实际操作中 fire 记录由调度引擎自动创建，不太可能通过 API 手动创建。但字段级权限的"最小开放"原则未完全落实。
- **建议**: 在保留文件中增加这些字段的 insertable/updatable 限制。
- **信心水平**: 中
- **误报排除**: rerunFire 在 BizModel 中通过 Store 层操作，不走通用 CRUD，所以这些字段在正常使用中不会被直接修改。问题在于理论上可能。
- **复核状态**: 未复核

## 正面评价（确认合规项）

1. **dict 定义与常量完全对齐**: 7 个 dict 文件的 value 与 _NopJobCoreConstants 一一对应
2. **BizModel 方法权限完整对齐**: 所有 @BizMutation 方法均有 action-auth 权限
3. **引擎统计字段正确限制**: NopJobSchedule 的 9 个引擎运行时字段在保留文件中全部设为 insertable=false/updatable=false
4. **无死字段**: 所有 xmeta 字段均有对应的 ORM 实体属性和业务用途

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 11-01 | P3 | NopJobFire.xmeta | 部分引擎内部字段未限制 insertable/updatable |
