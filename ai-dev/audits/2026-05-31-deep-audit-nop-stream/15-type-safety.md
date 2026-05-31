# 维度 15：类型安全与泛型使用

## 第 1 轮（初审）

### [维度15-01] TwoPhaseCommitSinkFunction Map<Long, Object> 完全无类型约束

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/common/functions/sink/TwoPhaseCommitSinkFunction.java:31, 57-63`
- **证据片段**:
  ```java
  private Map<Long, Object> pendingCommits;
  public Map<Long, Object> getPendingCommits() { return pendingCommits; }
  ```
- **严重程度**: P1
- **现状**: pendingCommits 存储事务对象类型为 Object，子类 commit 无法获取类型安全引用。
- **风险**: 子类实现必须自行维护类型映射，运行时 ClassCastException 风险。
- **建议**: 泛型化为 TwoPhaseCommitSinkFunction<IN, TX>，pendingCommits 改为 Map<Long, TX>。
- **信心水平**: 确定
- **误报排除**: 作为公共 API，类型安全很重要。
- **复核状态**: 未复核

### [维度15-02] SourceSplit/SourceEnumerator cursor 使用 Object 类型

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/source/SourceSplit.java:23,43` + `SourceEnumerator.java:55,288-293`
- **严重程度**: P2
- **现状**: cursor 和 discoveryCursor 使用 Object，序列化后类型信息可能丢失。
- **建议**: 泛型化 SourceSplit<T> 或定义 Cursor 接口。
- **信心水平**: 很可能
- **误报排除**: 如果 cursor 仅透传可接受，但 discoveryCursor 是可变的。
- **复核状态**: 未复核

### [维度15-03] MemoryStateSerDe 大量 Map<String, Object> + 强制转换

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryStateSerDe.java`
- **严重程度**: P2
- **现状**: 整个序列化管道使用 Map<String, Object> 中间格式，11 个 @SuppressWarnings("unchecked")。
- **建议**: 定义强类型 StateSnapshotData DTO。
- **信心水平**: 很可能
- **误报排除**: 序列化框架常见模式，转为 DTO 是维护成本改善而非 bug。
- **复核状态**: 未复核

### [维度15-04] StreamRecord.replace() 原地变异泛型类型

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/streamrecord/StreamRecord.java:113-122`
- **严重程度**: P2
- **现状**: replace() 将 StreamRecord<T> 原地变异为 StreamRecord<X>，代码注释已标注 "rogue pattern"。
- **建议**: 创建新实例而非原地变异。
- **信心水平**: 很可能
- **误报排除**: 从 Flink 移植的模式，isInstance 运行时检查提供安全网。
- **复核状态**: 未复核

### [维度15-05] SharedBuffer 构造器 raw type 强制转换

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/SharedBuffer.java:87-105`
- **严重程度**: P3
- **现状**: MapStateDescriptor 第三个参数传入 (Class) Lockable.class 擦除泛型。当前 MemoryMapState 不依赖此参数。
- **建议**: 保持，添加更详细注释说明安全性。
- **信心水平**: 很可能
- **误报排除**: 当前实现不依赖 valueClass 做反序列化。
- **复核状态**: 未复核

### [维度15-06] NFACompiler 11 处 @SuppressWarnings("unchecked")

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/compiler/NFACompiler.java`
- **严重程度**: P3
- **现状**: CEP 模式类型系统本质需要运行时检查，@SuppressWarnings 不可避免。
- **建议**: 维持现状，每个 suppress 旁加注释说明安全。
- **信心水平**: 确定
- **误报排除**: CEP 引擎固有特性。
- **复核状态**: 未复核

### [维度15-07] KeyedStreamImpl sum/min/max 数值强制转换链

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/datastream/KeyedStreamImpl.java:178-191, 200-212, 221-230`
- **严重程度**: P2
- **现状**: sum 使用 instanceof + 强制转换实现数值聚合，(T)(Integer) 利用 autoboxing 和泛型擦除。
- **建议**: 在 instanceof Number 检查后再强制转换 v2。
- **信心水平**: 很可能
- **误报排除**: 流处理框架 API 常见模式。
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件 | 摘要 |
|------|---------|------|------|
| 15-01 | P1 | TwoPhaseCommitSinkFunction.java | Map<Long, Object> 无类型约束 |
| 15-02 | P2 | SourceSplit.java, SourceEnumerator.java | Object cursor 无类型 |
| 15-03 | P2 | MemoryStateSerDe.java | Map<String, Object> 泛滥 |
| 15-04 | P2 | StreamRecord.java | replace() 原地变异泛型 |
| 15-05 | P3 | SharedBuffer.java | raw type 转换 |
| 15-06 | P3 | NFACompiler.java | 11 处 suppress unchecked |
| 15-07 | P2 | KeyedStreamImpl.java | sum/min/max 数值转换链 |
