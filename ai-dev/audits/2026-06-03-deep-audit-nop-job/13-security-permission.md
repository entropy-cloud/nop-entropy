# 维度13：安全与权限模型

## 第 1 轮（初审）

### [维度13-01] triggerNow 的 overrideParams 无输入验证

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java:97-112`
- **证据片段**:
  ```java
  @Override
  @BizMutation
  public void triggerNow(@Name("id") String id, @Name("overrideParams") Map<String, Object> overrideParams,
                         IServiceContext context) {
      NopJobSchedule schedule = requireEntity(id, "triggerNow", context);
      validateManualTriggerSchedule(schedule, "triggerNow");
      NopJobFire fire = buildManualFire(schedule, overrideParams, context);
  ```
  resolveJobParams 中 (行 205-222):
  ```java
  private Map<String, Object> resolveJobParams(NopJobSchedule schedule, Map<String, Object> overrideParams) {
      if (overrideParams != null) {
          return overrideParams;   // ← 直接透传，无校验
      }
  ```
- **严重程度**: P2
- **现状**: overrideParams 是无类型约束的 Map<String, Object>，直接序列化存储到 jobParamsSnapshot。无大小限制、key 白名单、嵌套深度验证。
- **风险**: 业务层数据注入风险（非 SQL 注入）。恶意/错误的参数可注入任意 JSON 结构，下游 executor 可能受影响。
- **建议**: 在 xmeta 层定义更具体的 input 类型，或添加参数 schema 校验（最大 JSON 大小限制）。
- **信心水平**: 很可能
- **误报排除**: 不是 SQL 注入（ORM 参数化查询）。Map<String, Object> 是 Nop 平台 JSON 参数的标准传递方式，风险取决于下游消费者。
- **复核状态**: 未复核

### [维度13-02] 数据权限配置为空

- **文件**: `nop-job/nop-job-service/src/main/resources/_vfs/nop/job/auth/nop-job.data-auth.xml:1-5`
- **证据片段**:
  ```xml
  <data-auth x:schema="/nop/schema/data-auth.xdef" xmlns:x="/nop/schema/xdsl.xdef">
      <objs/>
  </data-auth>
  ```
- **严重程度**: P3
- **现状**: nop-job 模块数据权限为空，无行级过滤。所有有 query 权限的用户能看到所有数据。
- **风险**: 多租户/多部门场景中用户可查看所有调度定义。对 admin 管理工具型调度系统是可接受的默认策略。
- **建议**: 评估是否需要按 namespaceId 增加数据权限规则。
- **信心水平**: 确定
- **误报排除**: 调度系统通常由管理员操作，全局可见是常见设计。
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|----------|
| 13-01 | P2 | NopJobScheduleBizModel.java:97 | triggerNow的overrideParams无输入验证 |
| 13-02 | P3 | data-auth.xml | 数据权限配置为空 |
