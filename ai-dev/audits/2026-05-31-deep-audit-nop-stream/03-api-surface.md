# 维度 03：API 表面积与契约一致性

## 关键发现

### [维度03-03] 状态快照全部使用 Map<String, Object> — P1

- **文件**: StateSnapshot.java, OperatorSnapshotResult.java, TaskStateSnapshot.java
- 整条检查点序列化管线建立在 Map<String, Object> 之上，缺乏编译期类型约束。

### [维度03-05] TwoPhaseCommitSinkFunction 引入 invoke() 与 consume() 命名不一致 — P2

- **文件**: TwoPhaseCommitSinkFunction.java:40-44
- SinkFunction 定义 consume(T)，但 TwoPhaseCommitSinkFunction 委托给新的抽象方法 invoke(IN)。

### [维度03-07] RuntimeContext 和 IterationRuntimeContext 是空接口 — P1

- **文件**: RuntimeContext.java, IterationRuntimeContext.java
- 通过 RichFunction.getRuntimeContext() 拿到的对象无法在编译期调用任何方法。

### [维度03-01] AggregateFunction 和 WindowFunction 不继承 StreamFunction — P3

- 同包其他函数接口均继承 StreamFunction，但这两个直接继承 Serializable。

### 其他 P3 发现

- 03-02: 多个函数接口冗余声明 extends Serializable
- 03-04: StreamComponents 使用 7 个 Map<String, Object> 字段
- 03-06: DataStream 的 print/collect/sink 功能完全相同
- 03-08: DynamicSplitResponse 缺少 @Internal 注解
- 03-09: 4个 FLIP-27 预留接口无实现
- 03-10: CheckpointedSourceFunction Javadoc 不准确
- 03-11: CoMapFunction/CoFlatMapFunction 标记废弃但仍在公共包
- 03-12: nop-stream-api 和 nop-stream-flow 是空壳模块

## 最终保留项

| 编号 | 严重程度 | 摘要 |
|------|---------|------|
| 03-03 | P1 | 状态快照 Map<String, Object> |
| 03-07 | P1 | RuntimeContext 空接口 |
| 03-05 | P2 | consume vs invoke 命名不一致 |
| 03-01 | P3 | AggregateFunction 不继承 StreamFunction |
| 其余 | P3 | 冗余声明、空壳模块、FLIP-27 预留接口等 |
