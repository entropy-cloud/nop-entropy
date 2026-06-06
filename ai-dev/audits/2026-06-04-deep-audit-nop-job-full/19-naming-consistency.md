# 维度 19：命名与术语一致性

## 审计范围

ORM 实体名、BizModel、接口、bean 名称的命名一致性。

## 第 1 轮（初审）发现

### [维度19-01] IJobScheduler 中 suspendJob 与 pauseJobs 对同一概念使用不同术语

- **文件**: `nop-job/nop-job-api/src/main/java/io/nop/job/api/IJobScheduler.java:117,119`
- **证据片段**:
  ```java
  boolean suspendJob(@Name("jobName") String jobName);
  default boolean pauseJobs(@Name("jobNames") Collection<String> jobNames) {
  ```
- **严重程度**: P2
- **现状**: 单数方法用 `suspend`，复数方法用 `pause`。ORM dict 中状态为 `PAUSED`，常量为 `SCHEDULE_STATUS_PAUSED`。
- **风险**: API 使用者混淆两个术语的等价关系。
- **建议**: 将 `suspendJob` 重命名为 `pauseJob` 或在 Javadoc 中说明等价性。
- **信心水平**: 高
- **误报排除**: 同一接口内两个术语表达相同语义，且与数据模型的 PAUSED 状态命名不一致。
- **复核状态**: 未复核

## 正面评价

- 实体名在 ORM/BizModel/xmeta/xbiz/view/action-auth 中全部一致
- 字段名在数据库列名/Java属性名/GraphQL字段名之间一致
- bean 名称与类名有合理的对应关系

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 19-01 | P2 | IJobScheduler.java:117,119 | suspendJob vs pauseJobs 术语不一致 |
