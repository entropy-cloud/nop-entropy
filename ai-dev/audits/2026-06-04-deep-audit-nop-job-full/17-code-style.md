# 维度 17：代码风格与规范

## 审计范围

全模块手写 Java 文件的命名、import、格式。

## 第 1 轮（初审）发现

### [维度17-01] NopJobScheduleBizModel 中使用 System.currentTimeMillis() 违反 DDD-006 规则

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java:268`
- **证据片段**:
  ```java
  long next = JobTriggerCalculator.calculateNextFireTime(
      toTriggerSpec(schedule),
      toEvalContext(schedule),
      System.currentTimeMillis()
  );
  ```
- **严重程度**: P2
- **现状**: source-anchors.md DDD-006 规则明确要求使用 CoreMetrics 获取时间戳，禁止 System.currentTimeMillis()。同模块 Store 层正确使用了 dao.getDbEstimatedClock()。
- **风险**: 测试时无法注入可控时间，降低可测试性。
- **建议**: 替换为 `CoreMetrics.currentTimeMillis()`。
- **信心水平**: 高
- **误报排除**: DDD-006 是明确的项目规则，且同模块其他位置使用了正确方式。
- **复核状态**: 未复核

### [维度17-02] NopJobConstants 和 NopJobConfigs 为空接口（死代码）

- **文件**: 
  - `nop-job/nop-job-service/src/main/java/io/nop/job/service/NopJobConstants.java`
  - `nop-job/nop-job-service/src/main/java/io/nop/job/service/NopJobConfigs.java`
- **证据片段**:
  ```java
  public interface NopJobConstants {
  }
  ```
- **严重程度**: P3
- **现状**: 两个空接口，无任何引用。可能是预留占位符。
- **风险**: 无功能风险，但增加维护负担。
- **建议**: 移除或添加注释说明预期用途。
- **信心水平**: 高
- **误报排除**: 实际无引用可验证。
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 17-01 | P2 | NopJobScheduleBizModel.java:268 | System.currentTimeMillis() 违反 DDD-006 |
| 17-02 | P3 | NopJobConstants.java, NopJobConfigs.java | 空接口死代码 |
