# 维度 16+21：测试覆盖与有效性

## 第 1 轮（初审）

### [维度16-01] GraphModelCheckpointExecutor 测试只验证配置不验证实际执行行为

- **文件**: `nop-stream-runtime/.../execution/TestGraphModelCheckpointExecutor.java`
- **证据片段**: 测试方法如 `testHandleJobTerminationDrainModeConfig` 仅检查 `CheckpointConfig` getter，不验证 Executor 真实行为。
- **严重程度**: P2
- **现状**: 807 行执行器代码的核心逻辑（restoreFromSavepointPath、handleJobTermination、checkpoint 恢复流程）缺少行为级测试。
- **风险**: 核心执行路径变更时无测试保护。
- **建议**: 增加真正调用 executor 方法并验证副作用的测试。
- **信心水平**: 确定
- **误报排除**: 不是"测试少"的问题，而是已有测试验证了错误的东西（配置 vs 行为）。
- **复核状态**: 未复核

### [维度16-02] 74% 测试文件不含异常路径验证

- **文件**: 模块内 244 个测试文件
- **严重程度**: P2
- **现状**: 仅 63/239 (26%) 包含 assertThrows。对容错性要求极高的流处理引擎，异常路径测试率偏低。
- **风险**: checkpoint recovery、state backend 等关键路径的错误处理变更无测试保护。
- **建议**: 优先为 state backend、checkpoint recovery 增加异常测试。
- **信心水平**: 确定
- **误报排除**: 不是覆盖率数字游戏——流处理引擎对容错的要求确实高于普通应用。
- **复核状态**: 未复核

### [维度16-03] StreamConnectors 和 UserTransactionHistory 缺少测试

- **文件**: 
  - `nop-stream-connector/.../StreamConnectors.java` — 无测试
  - `nop-stream-fraud-example/.../UserTransactionHistory.java` — 无测试
- **严重程度**: P3
- **现状**: StreamConnectors 是工厂入口类，UserTransactionHistory 维护用户交易历史状态（欺诈检测核心数据结构）。
- **风险**: 工厂方法和有状态逻辑变更无测试保护。
- **建议**: 补充单元测试。
- **信心水平**: 确定
- **误报排除**: 不是所有无测试的类都值得报告，但这两个有明确逻辑。
- **复核状态**: 未复核

## 正面发现

- WindowAggregationOperator: 6 个测试文件（1482 行测试代码 / 871 行主代码），覆盖 snapshot/restore、late data、merge，**优秀**。
- WindowOperator: 16 个测试文件（4057 行 / 1668 行），**优秀**。
- SharedBuffer: 814 行测试 / 371 行主代码，包含引用计数和缓存一致性测试，**优良**。
- CheckpointCoordinator: 测试:代码 ≈ 1:1，**优良**。
- E2E 测试：checkpoint 模块有 6 个 E2E 测试验证 exactly-once 语义。
- 测试命名：绝大多数测试方法名表达预期行为。

## 维度复核结论

待复核。

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 16-01 | P2 | TestGraphModelCheckpointExecutor | 测试验证配置而非行为 |
| 16-02 | P2 | 模块内 74% 测试 | 异常路径测试率低 |
| 16-03 | P3 | StreamConnectors, UserTransactionHistory | 缺少测试 |
