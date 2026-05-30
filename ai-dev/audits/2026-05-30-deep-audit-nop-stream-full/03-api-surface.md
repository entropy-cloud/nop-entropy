# 维度 03：API 表面积与契约一致性

## 审计范围

nop-stream-core 中所有公共接口和抽象类：函数接口层、算子接口层、DataStream API 层、连接器预留接口、状态接口层。

## 第 1 轮（初审）

### [维度03-01] Javadoc 引用了不存在的类 TwoInputStreamOperator

- **文件**: `nop-stream-core/.../operators/StreamOperator.java:29`
- **证据片段**:
```java
// 第 29 行
// "Implementers would implement one of OneInputStreamOperator or 
//  io.nop.stream.core.operators.TwoInputStreamOperator to create operators"
// 但 TwoInputStreamOperator 在整个代码库中不存在
```
- **严重程度**: P2
- **现状**: Flink 原始接口被移植时保留了双输入算子的 Javadoc 引用，但 nop-stream 当前未实现 TwoInputStreamOperator。
- **风险**: 外部开发者阅读 API 文档后可能去寻找不存在的类，浪费时间。构成契约文档失真。
- **建议**: 将 Javadoc 改为仅提及 OneInputStreamOperator，或如果双输入算子已规划则添加接口定义。
- **信心水平**: 确定
- **误报排除**: 不是审美问题——Javadoc 承诺了不存在的契约扩展点。
- **复核状态**: 未复核

### [维度03-02] Javadoc 引用了不存在的类 AbstractStreamOperatorV2、AbstractInput、MultipleInputStreamOperator

- **文件**: `nop-stream-core/.../operators/Input.java:28-31`、`OneInputStreamOperator.java:25`
- **严重程度**: P2
- **现状**: Input 和 OneInputStreamOperator 接口 Javadoc 引用了不存在的三个类。从 Flink 移植时未适配。
- **风险**: API 使用者无法根据 Javadoc 找到推荐的基类，削弱 API 可学性。
- **建议**: 清理 Javadoc 引用。
- **信心水平**: 确定
- **误报排除**: 不是审美问题——公共接口文档引用不存在的类是真实的契约失真。
- **复核状态**: 未复核

### [维度03-03] ProcessingTimeCallback.onProcessingTime 异常声明冗余

- **文件**: `nop-stream-core/.../operators/ProcessingTimeService.java:53`
- **证据片段**:
```java
void onProcessingTime(long time) throws IOException, InterruptedException, Exception;
```
- **严重程度**: P3
- **现状**: IOException 和 InterruptedException 是 Exception 的子类，被后者完全覆盖。
- **建议**: 简化为 `throws Exception`。
- **信心水平**: 确定
- **误报排除**: 不影响编译或运行，但降低 API 设计审慎度印象。
- **复核状态**: 未复核

### [维度03-04] TwoPhaseCommitSinkFunction 缺少事务状态类型参数 TX

- **文件**: `nop-stream-core/.../functions/sink/TwoPhaseCommitSinkFunction.java:30`
- **证据片段**:
```java
public abstract class TwoPhaseCommitSinkFunction<IN> extends AbstractRichFunction ... {
    // pendingCommits 使用 Map<Long, Object>，事务状态丢失类型信息
}
```
- **严重程度**: P2
- **现状**: 抽象类只有 IN 一个类型参数，pendingCommits 为 Map<Long, Object>。Flink 原版有 2-4 个类型参数。子类 commit()/rollback() 无法获得类型安全的传入对象。
- **风险**: 所有继承此类的 connector 实现者都需要在 commit()/rollback() 中做显式类型转换，增加出错概率。
- **建议**: 添加泛型参数 <IN, TX>，将 pendingCommits 改为 Map<Long, TX>。
- **信心水平**: 确定
- **误报排除**: 不是审美问题——泛型参数缺失导致所有子类必须做 unchecked cast。
- **复核状态**: 未复核

### [维度03-05] StreamOperator.setKeyContextElement2 是无用的 API 负担

- **文件**: `nop-stream-core/.../operators/StreamOperator.java:146`
- **严重程度**: P3
- **现状**: 接口定义了 setKeyContextElement2 但没有 TwoInputStreamOperator，AbstractStreamOperator 的实现是空方法体，每个实现者都被迫提供空实现。
- **建议**: 将 setKeyContextElement2 改为 default 空实现。
- **信心水平**: 确定
- **误报排除**: 不是审美问题——13 个测试被迫提供空实现是代码噪声。
- **复核状态**: 未复核

### [维度03-06] DataStream 的 print/collect/sink 三方法语义等价

- **文件**: `nop-stream-core/.../datastream/DataStream.java:100-128`
- **严重程度**: P3
- **现状**: print(SinkFunction)、collect(SinkFunction)、sink(SinkFunction) 三个方法在 DataStreamImpl 中全部委托给同一个 sink() 方法，实现完全相同。
- **建议**: 移除重载版本的 print 和 collect，或至少在 Javadoc 中说明它们是 sink() 的别名。
- **信心水平**: 确定
- **误报排除**: API 冗余是真实认知负担。
- **复核状态**: 未复核

### 正面发现

- 函数接口泛型使用正确完整
- DataStream API 的 keyBy→window→aggregate 类型链路通畅
- 状态接口层次清晰
- 所有函数接口正确继承 Serializable
