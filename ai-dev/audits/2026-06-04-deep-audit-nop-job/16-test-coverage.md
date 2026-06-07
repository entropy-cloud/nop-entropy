# 维度 16：测试覆盖与质量

## 第 1 轮（初审）

### [维度16-01] NopJobFireBizModel.rerunFire 的 PAUSED 状态调度缺少测试

- **文件**: `nop-job/nop-job-service/src/test/java/io/nop/job/service/entity/TestNopJobFireBizModel.java`
- **证据片段**: `testRerunFireRejectedForArchivedAndCompletedSchedule` 只覆盖了 ARCHIVED 和 COMPLETED，未覆盖 PAUSED 状态。
- **严重程度**: P2
- **现状**: `rerunFire` 在 `validateRerunSchedule` 中允许 PAUSED 状态的调度进行 rerun，但没有测试验证。
- **风险**: 如果 PAUSED 状态下调度的 rerun 逻辑存在边界问题，不会被测试捕获。
- **建议**: 添加 `testRerunFireAllowedForPausedSchedule` 测试用例。
- **信心水平**: 很可能
- **误报排除**: PAUSED 是第三个有意义的允许状态，属于遗漏的边界条件。
- **复核状态**: 未复核

---

### [维度16-02] NopJobScheduleBizModel.triggerNow 在 PAUSED 状态下缺少显式测试

- **文件**: `nop-job/nop-job-service/src/test/java/io/nop/job/service/entity/TestNopJobScheduleBizModel.java`
- **严重程度**: P3
- **现状**: PAUSED 状态的 triggerNow（合法路径）无显式测试。
- **建议**: 补充一个 PAUSED 状态下 triggerNow 的显式测试。
- **信心水平**: 很可能
- **误报排除**: ENABLED 状态已测核心路径，PAUSED 是边界补充。
- **复核状态**: 未复核
