# 维度 03：API 表面积与契约一致性

## 第 1 轮（初审）

### P2 发现（3个）

| 编号 | 文件 | 摘要 |
|------|------|------|
| 03-02 | MemoryStateSerDe, WindowAggregationOperator 等 | 状态序列化路径大量 Map<String, Object> |
| 03-06 | StreamExecutionEnvironment.java (439行) | 混合 API 构建、配置管理和执行引擎逻辑 |
| 03-11 | ICheckpointedFunction vs CheckpointedSourceFunction | 两套并行的 checkpoint 接口语义重叠 |

### P3 发现（12个）

| 编号 | 文件 | 摘要 |
|------|------|------|
| 03-01 | nop-stream-api | 空壳，API 未分离 |
| 03-03 | ShardPrefixedKey | 两个同名不同包的版本 |
| 03-04 | CoMapFunction 等 | @Deprecated 未清理 |
| 03-05 | MapFunction 等 | Serializable 重复继承 |
| 03-07 | CheckpointedSourceFunction | @Internal 且未使用 |
| 03-08 | PatternStream | 构造器可见性不一致 |
| 03-09 | SinkFunction | consume vs invoke 命名不一致 |
| 03-10 | DebeziumCdcSourceFunction | 不必要的 public isDraining() |
| 03-12 | 空壳模块 | 增加构建开销 |
| 03-13 | 多个接口 | @Internal 注解使用不系统化 |
| 03-14 | KeyedStream | 字段名/索引聚合方法缺少类型安全 |
| 03-15 | StateSnapshot | 魔法字符串键 |

## 最终保留项

全部 15 个发现保留。
