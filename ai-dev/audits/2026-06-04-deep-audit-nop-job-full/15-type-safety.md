# 维度 15：类型安全与泛型使用

## 审计范围

全模块手写 Java 文件的泛型使用、原始类型、强制转换。

## 第 1 轮（初审）发现

### [维度15-01] IJobInstanceState.getJobDefId() 与 ORM 实体 jobScheduleId 对同一标识符使用不同名称

- **文件**: `nop-job/nop-job-api/src/main/java/io/nop/job/api/IJobInstanceState.java:21`
- **证据片段**:
  ```java
  String getJobDefId();
  ```
  实现中：`setJobDefId(schedule.getJobScheduleId())`
- **严重程度**: P2
- **现状**: API 层称 `jobDefId`（Job Definition ID），ORM 层称 `jobScheduleId`，实际值相同。在跨层调试时造成混淆。
- **风险**: 调用者可能误以为返回的是与 Schedule ID 不同的另一个标识符。
- **建议**: 将 API 接口的方法名改为 `getJobScheduleId()` 或在 Javadoc 中明确说明等价关系。
- **信心水平**: 高
- **误报排除**: 所有实现都传入 `schedule.getJobScheduleId()`，命名不一致是可验证的事实。
- **复核状态**: 未复核

## 正面评价

- 零原始类型使用
- @SuppressWarnings("unchecked") 仅 5 处，全部用于 Map cast
- 接口泛型精度正确（ICrudBiz<T>、IJobInvoker 等）

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 15-01 | P2 | IJobInstanceState.java:21 | jobDefId vs jobScheduleId 命名不一致 |
