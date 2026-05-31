# 对抗性审查总结 — Round 8

## 基本信息

- **审查模块**: nop-stream
- **审查日期**: 2026-05-31
- **审查类型**: 开放式对抗性审查（第 8 轮）
- **审查方法**: 4 个并行 explore agent，分别聚焦 core/operators/state、runtime/checkpoint/cluster/transport、CEP/NFA/SharedBuffer、connector/windowing
- **去重基线**: R5 (AR-1~16) + R6 (AR-17~35) + R7 (AR-36~54) + deep audit (59 findings) + R13 及更早所有报告

## 关键数字

| 指标 | 数值 |
|------|------|
| 新发现 | 13 |
| P0 | 1 |
| P1 | 4 |
| P2 | 6 |
| P3 | 2 |
| 已修复确认 | 6 |
| 仍存在（确认） | 22 |

## 最重要发现

### P0: AR-55 — 分布式模式 parallelism>1 时 checkpoint 完全失效

`RemoteGraphExecutionPlanBuilder` 只为 taskIndex==0 注册 executionVertices，checkpoint 计划只包含每个 vertex 的第一个 subtask。这是之前报告的"分布式 checkpoint 问题"的精确根因定位。

### P1 关键发现

1. **AR-56**: CheckpointCoordinator.registerTask 非原子 read-then-replace，并发注册丢失 task
2. **AR-57**: ProcessingTimeoutTrigger 强制将 CONTINUE 转为 FIRE（未使用但 API 已公开）
3. **AR-58**: WindowAggregationOperator.resolveKey isInstance 检查方向反转
4. **AR-59**: SimpleStreamOperatorFactory 不可序列化时静默返回共享模板

## 审查盲区

1. nop-stream-flow/flink 空壳模块未审查
2. 所有 parallelism>1 问题为代码分析，无运行验证
3. 近50次提交新增的 3643 行测试的有效性未审查
4. 序列化兼容性未验证
