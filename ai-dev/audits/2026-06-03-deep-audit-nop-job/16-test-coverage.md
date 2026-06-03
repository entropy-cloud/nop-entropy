# 维度 16：测试覆盖审查

## 发现

### [16-01] P2 — NopJobTaskBizModel.delete() 完全无测试覆盖

- **文件**: 无对应的 TestNopJobTaskBizModel 测试类
- **现状**: `NopJobTaskBizModel.delete()` 方法包含删除权限封堵逻辑，但完全没有测试覆盖。不存在 `TestNopJobTaskBizModel` 测试类。
- **风险**: 删除封堵逻辑的回归无法被自动检测。任何重构都可能导致封堵逻辑被意外移除。
- **建议**: 创建 `TestNopJobTaskBizModel` 测试类，覆盖 delete 方法的正常路径和拒绝路径。

### [16-02] P2 — RpcBroadcastTaskBuilder 零测试覆盖

- **文件**: RpcBroadcastTaskBuilder（96 行代码，4+ 分支路径）
- **现状**: `RpcBroadcastTaskBuilder` 有 96 行代码和 4 个以上的分支路径，但完全没有测试覆盖。
- **风险**: 广播任务构建逻辑的任何回归都无法被自动检测。
- **建议**: 为 `RpcBroadcastTaskBuilder` 编写单元测试，覆盖所有分支路径。

### [16-03] P3 — CronExpression 测试仅使用单一 cron 模式

- **文件**: CronExpression（470 行代码）
- **现状**: `CronExpression` 的测试仅通过单一 cron 模式 `"0 0 6,19 * * *"` 进行验证。缺少以下场景的测试：
  - `L`（最后一天）修饰符
  - `W`（最近工作日）修饰符
  - `#`（第 N 个星期几）修饰符
  - 闰年边界条件
- **建议**: 补充覆盖特殊修饰符和边界条件的测试用例。

### [16-04] P3 — Calendar 子系统零独立测试覆盖

- **文件**: DailyCalendar, BaseCalendar, MonthlyCalendar, CronCalendar 等 8 个类（共 1575 行代码）
- **现状**: calendar 子系统的 8 个类（1575 行代码）完全没有任何独立的测试覆盖。
- **建议**: 为 calendar 包添加独立的单元测试。
