# 维度 15 审计报告：nop-stream 类型安全与泛型使用

> 审计日期: 2026-05-27

## 发现

### [15-01] 状态后端 states 字段使用 Map<String, Object> 丢失全部泛型信息
- **文件**: `MemoryKeyedStateBackend.java:96`
- **严重程度**: P2
- **建议**: 在 getState 时增加 descriptor 类型一致性校验。

### [15-02] 状态快照/恢复路径全面基于 Map<String, Object> 弱类型
- **文件**: OperatorSnapshotResult.java, TaskStateSnapshot.java, StateSnapshot.java
- **严重程度**: P2
- **建议**: 引入 TypedStateValue sealed interface。

### [15-03] MemoryKeyedStateBackend 恢复将所有类型当作 Class<Object>
- **文件**: `MemoryKeyedStateBackend.java:370,395,433,466,497`
- **严重程度**: P2
- **建议**: 保留 Class<?> 并在 getState 时做 isAssignableFrom 校验。

### [15-04] WindowAggregationOperator 反序列化多重 unchecked cast
- **文件**: `WindowAggregationOperator.java:430-498`
- **严重程度**: P2
- **建议**: 增加 keyClass/windowClass 一致性校验。

### [15-05] CEP MapStateDescriptor 使用 (Class) List.class 丢失值类型
- **文件**: `CepOperator.java:199`, `SharedBuffer.java:99,106`
- **严重程度**: P2
- **建议**: 添加 @SuppressWarnings("rawtypes") 并标注限制。

### [15-06] StreamRecord.replace() rogue pattern 伪造泛型
- **文件**: `StreamRecord.java:109-131`
- **严重程度**: P3
- **建议**: Javadoc 中警告不得保留旧引用。

### [15-07] Output.collectElement() instanceof 分派 + unchecked cast
- **文件**: `Output.java:61-70`
- **严重程度**: P3
- **建议**: else 分支增加警告。

### [15-08] KeyedStreamImpl sum/min/max Comparable 假设不安全
- **文件**: `KeyedStreamImpl.java:204-227`
- **严重程度**: P3
- **建议**: 增加 T extends Comparable<? super T> 泛型约束。

### [15-09] LocalFileCheckpointStorage 反序列化 unchecked cast 无防御
- **文件**: `LocalFileCheckpointStorage.java:355,362`
- **严重程度**: P3
- **建议**: 增加 instanceof Map 检查。

### [15-10] LastValue.type() 返回 (Class) LastValue.class 违反类型安全
- **文件**: `LastValue.java:23`
- **严重程度**: P3
- **建议**: 添加 @SuppressWarnings 注解。

## 最终保留项

| 编号 | 严重程度 | 文件 | 摘要 |
|------|---------|------|------|
| 15-01 | P2 | MemoryKeyedStateBackend.java | Map<String,Object> 丢失泛型 |
| 15-02 | P2 | OperatorSnapshotResult等 | 快照路径弱类型 |
| 15-03 | P2 | MemoryKeyedStateBackend.java | 恢复用 Class<Object> |
| 15-04 | P2 | WindowAggregationOperator.java | 反序列化无校验 |
| 15-05 | P2 | CepOperator/SharedBuffer | raw cast 丢失类型 |
| 15-06 | P3 | StreamRecord.java | rogue pattern |
| 15-07 | P3 | Output.java | instanceof 分派 |
| 15-08 | P3 | KeyedStreamImpl.java | Comparable 假设 |
| 15-09 | P3 | LocalFileCheckpointStorage.java | 无防御 cast |
| 15-10 | P3 | LastValue.java | raw class return |
