# 维度 16：测试覆盖与质量

## 审计范围

25 个测试类、约 150 个 @Test 方法。

## 第 1 轮（初审）发现

### [维度16-01] TestAnnualCalendar 测试质量极低，核心逻辑零覆盖

- **文件**: `nop-job/nop-job-core/src/test/java/io/nop/job/core/calendar/TestAnnualCalendar.java`
- **证据片段**:
  ```java
  void testIsTimeIncludedWithoutExcludeDays() {
      AnnualCalendar cal = new AnnualCalendar(null);
      long now = System.currentTimeMillis();
      assertTrue(cal.isTimeIncluded(now));
  }
  ```
- **严重程度**: P2
- **现状**: 3 个测试方法全部测试空日历的默认行为，核心功能（排除日期、跨年处理）零覆盖。
- **风险**: AnnualCalendar 的排除日期逻辑 bug 无法被测试捕获。
- **建议**: 增加排除特定日期后验证该天被排除、getNextIncludedTime 跳到下一天等测试。
- **信心水平**: 高
- **误报排除**: 3/3 方法命中 P-2 反模式（测试元数据属性而非行为）。
- **复核状态**: 未复核

### [维度16-02] NopJobScheduleBizModel.triggerNow 的 MANUAL_TRIGGER_DISCARDED 路径未被测试

- **文件**: `nop-job/nop-job-service/src/test/java/io/nop/job/service/entity/TestNopJobScheduleBizModel.java`
- **证据片段**: 两个 triggerNow 测试都假设 `insertManualFire` 返回 true。
- **严重程度**: P3
- **现状**: `insertManualFire` 返回 false 时的错误抛出和错误参数未被验证。
- **风险**: 用户收到 MANUAL_TRIGGER_DISCARDED 错误时可能缺少正确的上下文参数。
- **建议**: 新增测试 mock scheduleStore 使 insertManualFire 返回 false。
- **信心水平**: 高
- **误报排除**: 错误路径确实是未覆盖的分支。
- **复核状态**: 未复核

## 测试有效性统计

约 87% 的测试方法具有捕获真实 bug 的能力。低价值测试集中在 TestAnnualCalendar（3/3 无效）、TestBlockStrategies（5/5 耦合于实现）、TestDefaultJobCancelHandler（6/9 无断言）。

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 16-01 | P2 | TestAnnualCalendar.java | 核心逻辑零覆盖 |
| 16-02 | P3 | TestNopJobScheduleBizModel.java | triggerNow 错误路径未测 |
