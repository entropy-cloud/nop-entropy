# 维度07：BizModel 规范遵循

## 第 1 轮（初审）

### [维度07-01] persistSchedule 使用 updateEntityDirectly 绕过 CrudBizModel 标准更新流程

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java:132-135`
- **证据片段**:
  ```java
  private void persistSchedule(NopJobSchedule schedule, String action, IServiceContext context) {
      dao().updateEntityDirectly(schedule);
      afterEntityChange(schedule, action, context);
  }
  ```
- **严重程度**: P3
- **现状**: 所有状态变更方法（enableSchedule/disableSchedule/pauseSchedule/resumeSchedule/archiveSchedule）统一通过 persistSchedule 持久化，跳过了 CrudBizModel 的 updateEntity 方法（封装了 xmeta autoExpr 计算、字段校验、前后置钩子）。
- **风险**: 当前仅做简单字段修改，尚无实际功能受损。但后续通过 xmeta 增加校验规则会被静默跳过。
- **建议**: 改为调用 updateEntity(schedule, action, context)。需验证 xmeta 中 nextFireTime 的 updatable 配置是否兼容。
- **信心水平**: 确定
- **误报排除**: 已排除 Store 层的 updateEntityDirectly（属于文档所说的"边界场景"）。
- **复核状态**: 未复核

### [维度07-02] cancelFire/rerunFire 未使用 requireEntity 加载实体

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobFireBizModel.java:47-48, 63`
- **证据片段**:
  ```java
  public void cancelFire(@Name("id") String id, IServiceContext context) {
      NopJobFire fire = fireStore.loadFire(id);  // 非 requireEntity
  ...
  public void rerunFire(@Name("id") String id, IServiceContext context) {
      NopJobFire sourceFire = fireStore.loadFire(id);  // 非 requireEntity
  ```
- **严重程度**: P3
- **现状**: 两个 mutation 方法使用 fireStore.loadFire(id) 而非 CrudBizModel 的 requireEntity。loadFire 内部调用 requireEntityById 会抛出实体不存在异常，但绕过了 CrudBizModel 的 beforeRequireEntity/afterRequireEntity 钩子和 action 级权限校验。
- **风险**: 当前无 action 级权限钩子，实际功能无损。但未来若添加字段级审计或 action 权限控制，此路径会静默绕过。
- **建议**: 考虑改用 requireEntity(id, action, context) 加载实体，或确认当前 Store 层校验已满足需求。
- **信心水平**: 确定
- **误报排除**: fireStore.loadFire 内部确实有 requireEntityById，实体不存在时会正确报错。
- **复核状态**: 未复核

## 合规确认项

- 三个 BizModel 全部继承 CrudBizModel<T>，构造函数均调用 setEntityName()。
- 每个 BizModel 有对应的 ORM 实体和 xmeta。
- 无 Map<String, Object> 代替类型安全结构的反模式。
- 无 @BizLoader 使用。
- 无 Processor 类（当前复杂度可接受）。
- @Inject 字段全部为 setter 注入，无 private 字段注入。

## 维度复核结论

待复核。

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|----------|
| 07-01 | P3 | NopJobScheduleBizModel.java:132 | persistSchedule使用updateEntityDirectly绕过标准流程 |
| 07-02 | P3 | NopJobFireBizModel.java:47 | cancelFire/rerunFire未使用requireEntity加载实体 |
