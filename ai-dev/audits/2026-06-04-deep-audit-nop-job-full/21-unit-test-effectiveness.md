# 维度 21：单元测试有效性

## 审计范围

25 个测试类、约 155 个 @Test 方法。反模式参考：ai-dev/skills/unit-test-antipatterns.md。

## 第 1 轮（初审）发现

### [维度21-01] TestBlockStrategies 5/5 方法命中 P-4（测试与实现高度耦合）

- **文件**: `nop-job/nop-job-coordinator/src/test/java/io/nop/job/coordinator/engine/TestBlockStrategies.java`
- **证据片段**:
  ```java
  assertEquals(1, scheduleStore.skipCount);
  assertEquals(1, scheduleStore.overlayCount);
  ```
- **严重程度**: P2
- **现状**: 通过 CountingScheduleStore 的内部计数器断言策略路由，与 Planner 的内部实现高度耦合。如果 planner 重构为统一方法，测试全部要重写。
- **风险**: 重构时测试维护成本高，且无法验证功能等价的替代实现。
- **建议**: 通过验证 fire/schedule 的最终状态而非中间方法调用来测试。
- **信心水平**: 高
- **误报排除**: 5/5 方法全部依赖 CountingScheduleStore 的内部计数器，属于经典 P-4 反模式。
- **复核状态**: 未复核

### [维度21-02] TestDefaultJobCancelHandler 6/9 方法无任何断言

- **文件**: `nop-job/nop-job-coordinator/src/test/java/io/nop/job/coordinator/engine/TestDefaultJobCancelHandler.java`
- **证据片段**: 6 个防御性分支测试仅验证"不抛异常"，无 assert 语句。
- **严重程度**: P3
- **现状**: 6 个方法命中 P-5/P-8 混合（无效的负面测试 + 缺少断言）。如果 cancelRunningTask 改为空方法，这些测试全部通过。
- **风险**: 无法捕获"什么都不做"的回归。
- **建议**: 使用 mock invoker 断言 cancelAsync 未被调用。
- **信心水平**: 高
- **误报排除**: 实际 6 个方法无断言语句。
- **复核状态**: 未复核

## 有效测试比例

约 87%（134/155）的测试方法具有捕获真实 bug 的能力。

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 21-01 | P2 | TestBlockStrategies.java | 5/5 命中 P-4 耦合反模式 |
| 21-02 | P3 | TestDefaultJobCancelHandler.java | 6/9 无断言 |
