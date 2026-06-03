# 维度02：模块职责与文件边界

## 第 1 轮（初审）

### [维度02-01] BizModel 间 resolveTriggeredBy 工具方法完全重复

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/entity/NopJobScheduleBizModel.java:224-235` 及 `NopJobFireBizModel.java:149-160`
- **证据片段**:
  ```java
  private String resolveTriggeredBy(IServiceContext context) {
      String userName = null;
      if (context != null) {
          if (context.getUserContext() != null) {
              userName = context.getUserContext().getUserName();
          }
          if ((userName == null || userName.isEmpty()) && context.getContext() != null) {
              userName = context.getContext().getUserName();
          }
      }
      return userName == null || userName.isEmpty() ? "system" : userName;
  }
  ```
- **严重程度**: P3
- **现状**: 两个 BizModel 各自持有完全相同的 12 行 private 方法。
- **风险**: 修改时需同步两处。
- **建议**: 提取到共享工具类，或待第三次出现时再抽取。
- **信心水平**: 确定
- **误报排除**: 方法体逐字符相同，确认是 copy-paste 产物。
- **复核状态**: 未复核

### [维度02-02] BizModel 间 Fire 对象构建逻辑高度相似

- **文件**: `NopJobScheduleBizModel.java:179-203`（buildManualFire）及 `NopJobFireBizModel.java:106-129`（buildRecoveryFire）
- **证据片段**:
  ```java
  // NopJobScheduleBizModel.java:179-203
  private NopJobFire buildManualFire(NopJobSchedule schedule, Map<String, Object> overrideParams,
                                     IServiceContext context) {
      // ... 70% 字段赋值模式与 buildRecoveryFire 相同
      fire.setCreatedBy("system");
      fire.setUpdatedBy("system");
  }
  ```
- **严重程度**: P3
- **现状**: 两个方法各自构建 NopJobFire 对象，约 70% 的字段赋值逻辑重复。
- **风险**: 新增 Fire 字段时容易遗漏其中一处。
- **建议**: 可提取 FireBuilder 工厂方法，或保持现状。
- **信心水平**: 很可能
- **误报排除**: 两方法数据源不同（schedule vs sourceFire），抽象有设计成本。
- **复核状态**: 未复核

## 合规确认项

- 所有生产手写文件低于 500 行阈值（最大 DailyCalendar 512 行）。
- _gen/ 目录无手写代码。
- _ 前缀生成文件无手写修改痕迹。
- 各子模块职责单一，无越权。

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|----------|
| 02-01 | P3 | 两个BizModel | resolveTriggeredBy方法完全重复 |
| 02-02 | P3 | 两个BizModel | Fire构建逻辑70%重复 |
