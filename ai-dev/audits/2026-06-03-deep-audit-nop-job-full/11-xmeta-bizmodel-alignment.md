# 维度 11：XMeta 与 BizModel 对齐

## 第 1 轮（初审）

### [维度11-01] NopJobSchedule scheduleStatus mandatory=true 但 insertable=false 且无 defaultValue

- **文件**: `nop-job/nop-job-meta/src/main/resources/_vfs/nop/job/model/NopJobSchedule/NopJobSchedule.xmeta:5` 和 `_NopJobSchedule.xmeta:50-53`
- **证据片段**:
  ```xml
  <!-- Delta xmeta -->
  <prop name="scheduleStatus" insertable="false" updatable="false"/>
  <!-- 生成 xmeta -->
  <prop name="scheduleStatus" ... mandatory="true" insertable="true" updatable="true"/>
  ```
- **严重程度**: P2
- **现状**: Delta xmeta 将 scheduleStatus 设为 insertable=false，但生成 xmeta 中 mandatory=true。创建 Schedule 时不传 scheduleStatus 可能导致校验失败。BizModel 未覆盖 initDefaultValues 设置默认状态。
- **风险**: 前端创建 Schedule 时可能因 mandatory 校验失败。
- **建议**: 在 xmeta 中添加 defaultValue="0"（DISABLED），或在 BizModel 的 initDefaults 中设置默认值。
- **信心水平**: 很可能
- **误报排除**: 已确认 NopJobScheduleBizModel 没有覆盖 initDefaultValues 或 beforeCreate 方法。
- **复核状态**: 未复核

### [维度11-02] NopJobFire 运行时字段在生成 xmeta 中权限过于宽松（已被 Delta 修正）

- **文件**: `nop-job/nop-job-meta/src/main/resources/_vfs/nop/job/model/NopJobFire/NopJobFire.xmeta:5-12`
- **严重程度**: P3
- **现状**: 生成 xmeta 中 fireStatus/plannerInstanceId 等标记为 insertable/updatable，但 Delta 正确覆盖为 false。
- **风险**: 已被 Delta 修正，无运行时风险。
- **建议**: 无需修改。
- **信心水平**: 确定
- **误报排除**: Delta 修正后实际安全。
- **复核状态**: 未复核

### [维度11-03] NopJobTask progress/progressMessage 缺少写入保护

- **文件**: `nop-job/nop-job-meta/src/main/resources/_vfs/nop/job/model/NopJobTask/NopJobTask.xmeta`
- **证据片段**:
  ```xml
  <!-- 生成 xmeta 中这两个字段 insertable/updatable 均为 true，Delta 未覆盖 -->
  <prop name="progress" ... insertable="true" updatable="true"/>
  <prop name="progressMessage" ... insertable="true" updatable="true"/>
  ```
- **严重程度**: P2
- **现状**: NopJobTask Delta xmeta 将运行时状态字段（taskStatus 等）设为不可写，但遗漏了 progress 和 progressMessage。客户端可通过标准 GraphQL mutation 直接修改这两个字段。
- **风险**: 进度信息可被恶意篡改（伪造完成进度）。
- **建议**: 在 Delta xmeta 中添加 `<prop name="progress" insertable="false" updatable="false"/>` 和 `<prop name="progressMessage" insertable="false" updatable="false"/>`。
- **信心水平**: 确定
- **误报排除**: progress/progressMessage 与 taskStatus 同属运行时状态，应同等保护。
- **复核状态**: 未复核

### [维度11-04] NopJobTask delete 被禁止但 xmeta 未标记不可删除

- **文件**: `nop-job/nop-job-meta/src/main/resources/_vfs/nop/job/model/NopJobTask/NopJobTask.xmeta`
- **证据片段**:
  ```java
  // NopJobTaskBizModel.java:24-27
  public boolean delete(String id, IServiceContext context) {
      throw new NopException(ERR_JOB_TASK_DELETE_NOT_ALLOWED);
  }
  ```
- **严重程度**: P2
- **现状**: BizModel 覆盖 delete 方法抛异常，但 xmeta 无 deletable=false 标记。前端仍显示删除按钮，用户点击后才收到错误。
- **风险**: 用户体验不佳，浪费网络请求。
- **建议**: 在 xmeta 中标记 deletable="false"。
- **信心水平**: 很可能
- **误报排除**: 已确认 xmeta 中无任何不可删除的元数据标记。
- **复核状态**: 未复核

### [维度11-05] 所有 BizModel mutation 无权限注解

- **文件**: 三个 BizModel 类的所有 @BizMutation 方法
- **严重程度**: P3
- **现状**: 8 个 @BizMutation 方法（Schedule 6个 + Fire 2个）均无权限注解，任何已认证用户可执行。
- **风险**: 敏感操作（triggerNow、cancelFire）无访问控制。
- **建议**: 视业务安全需求添加权限注解。
- **信心水平**: 确定
- **误报排除**: 开发阶段可接受，生产部署前需评估。
- **复核状态**: 未复核

### [维度11-06] NopJobTask 缺少 jobFire 展开字段

- **文件**: `nop-job/nop-job-meta/src/main/resources/_vfs/nop/job/model/NopJobTask/_NopJobTask.xmeta`
- **严重程度**: P3
- **现状**: NopJobTask xmeta 中有 jobFire 关联 prop，但没有 jobFire.fireStatus 等展开字段，前端列表无法直接显示关联 Fire 信息。
- **建议**: 如需在 Task 列表中显示 Fire 信息，添加展开字段。
- **信心水平**: 很可能
- **误报排除**: 当前设计可能有意为之。
- **复核状态**: 未复核

## 正面确认

| 检查项 | 结果 |
|--------|------|
| dict 与常量对齐 | 完全一致（7 个字典） |
| displayName 本地化 | 中英文完整覆盖 |
| xmeta 字段覆盖 ORM | 完全对齐（3 个实体） |
| 无死字段/未暴露字段 | 无 |
| 无 @BizLoader 误用 | 无自定义 loader |
