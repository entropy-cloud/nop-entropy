# 维度 17：代码风格与规范

## 第 1 轮（初审）

### [维度17-01] 冗余的非静态 NopStreamErrors/NopCepErrors import（36 文件）

- **文件**: 34 文件 (core) + 1 文件 (runtime) + 1 文件 (cep)，共 36 文件
- **行号范围**: import 区域
- **证据片段**:
  ```java
  // OperatorChain.java 第 21-24 行
  import io.nop.stream.core.exceptions.StreamException;
  import io.nop.stream.core.exceptions.NopStreamErrors;          // ← 冗余
  import static io.nop.stream.core.exceptions.NopStreamErrors.*; // ← 实际使用
  ```
- **严重程度**: P2
- **现状**: 文件同时存在非静态和静态 import。代码中仅通过静态常量 ERR_STREAM_* 引用，从不使用 NopStreamErrors 类名。
- **风险**: import 区域噪音，IDE 标记 unused warning。
- **建议**: 删除冗余非静态 import，仅保留 static import。
- **信心水平**: 高 (100%)
- **误报排除**: 已逐一验证所有 36 文件中 NopStreamErrors 类名均未在非 import 行出现。
- **复核状态**: 未复核

### [维度17-02] 确认未使用的 import（至少 12 处）

- **文件**: 12 个文件，见下表
- **行号范围**: import 区域
- **证据片段**:
  ```java
  // ForwardPartitionRouter.java 第 10-13 行
  import io.nop.commons.partition.IPartitioner;       // ← 未使用
  import io.nop.stream.core.common.state.shard.StateShard;  // ← 未使用
  ```
- **严重程度**: P2
- **现状**: 多个文件包含从未在代码中引用的 import 语句。

  完整清单: ForwardPartitionRouter(IPartitioner,StateShard)、DataStreamImpl(TimestampsAndWatermarksOperator)、TwoPhaseCommitSinkFunction(OperatorSnapshotResult)、MemoryStateSerDe(IKeyedStateBackend)、SinkTransformation(Serializable)、PhysicalTransformation(List)、ListState(Iterator)、ReduceFunction(Serializable)、Subtask(OperatorChain)、StreamExecutionEnvironment(JobVertex)、HeapInternalTimerService(Iterator)、CepOperator(HashSet)
- **风险**: 增加编译依赖、降低可读性。
- **建议**: 删除未使用的 import。
- **信心水平**: 高 (95%)
- **误报排除**: 每个 import 均已验证类名在文件中零出现。
- **复核状态**: 未复核

### [维度17-03] 重复 import 行

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/windowing/assigners/TumblingProcessingTimeWindows.java:15-16`
- **证据片段**:
  ```java
  import io.nop.core.context.IServiceContext;
  import io.nop.core.context.IServiceContext; // ← 完全重复
  ```
- **严重程度**: P3
- **现状**: 同一 import 出现两次。
- **风险**: 极低。
- **建议**: 删除重复行。
- **信心水平**: 高 (100%)
- **误报排除**: 整个模块仅此一例。
- **复核状态**: 未复核

### [维度17-04] import 分组间缺少空行（7 文件）

- **文件**: JobGraph.java、JobVertex.java、StreamGraph.java、TypeRegistry.java、DeweyNumber.java、SkipToLastStrategy.java、SkipToFirstStrategy.java
- **证据片段**:
  ```java
  // JobGraph.java 第 14-16 行
  import java.util.List;
  import java.util.Map;
  import io.nop.stream.core.exceptions.StreamException;  // ← 缺少空行分隔
  ```
- **严重程度**: P3
- **现状**: java.* import 组直接紧跟 io.nop.* 组，中间无空行。违反 Nop 平台分组约定。
- **风险**: 降低 import 区域可读性。
- **建议**: 在 java.* 与 io.nop.* 之间插入空行。
- **信心水平**: 高 (95%)
- **误报排除**: 3 个 CEP 文件为 Flink 移植代码。
- **复核状态**: 未复核

### [维度17-05] import 与 class 声明之间缺少空行（4 文件）

- **文件**: SlidingEventTimeWindows.java、SlidingProcessingTimeWindows.java、TumblingProcessingTimeWindows.java、EventTimeSessionWindows.java
- **证据片段**:
  ```java
  // SlidingEventTimeWindows.java 第 22-23 行
  import static io.nop.stream.core.exceptions.NopStreamErrors.*;
  public class SlidingEventTimeWindows extends WindowAssigner<Object, TimeWindow> {  // ← 缺少空行
  ```
- **严重程度**: P3
- **现状**: import static 行直接紧跟 public class 声明。
- **风险**: 降低可读性。
- **建议**: 插入空行。
- **信心水平**: 高 (100%)
- **误报排除**: 4 个文件同一目录同一批次编写。
- **复核状态**: 未复核

### [维度17-06] 缺少文件版权头（14 个非生成文件）

- **文件**: 14 个文件（主要在 core/operators/ 和 core/common/state/ 下）
- **严重程度**: P3
- **现状**: 14 个手写文件直接以 package 声明开头，缺少标准 Nop 平台版权注释头。
- **风险**: 版权合规性不一致。
- **建议**: 添加标准 Nop 平台文件头。
- **信心水平**: 高 (90%)
- **误报排除**: 已排除 _gen/ 目录下的生成文件。
- **复核状态**: 未复核

### [维度17-07] 超长行影响可读性（195 处）

- **文件**: JdbcCheckpointStorage(21处)、LocalFileCheckpointStorage(16处)、NopStreamErrors(15处)、GraphModelCheckpointExecutor(14处) 等
- **严重程度**: P3
- **现状**: 195 行超过 120 字符，主要集中在 SQL DDL 拼接和 .param() 链式调用。
- **风险**: code review 需横向滚动，diff 时上下文截断。
- **建议**: 随日常修改逐步改善。
- **信心水平**: 中 (80%)
- **误报排除**: 部分 SQL/日志长行在实际中难以避免。
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 17-01 | P2 | 36 文件 | 冗余非静态 NopStreamErrors import |
| 17-02 | P2 | 12 文件 | 未使用的 import |
| 17-03 | P3 | TumblingProcessingTimeWindows.java | 重复 import 行 |
| 17-04 | P3 | 7 文件 | import 分组间缺少空行 |
| 17-05 | P3 | 4 文件 | import 与 class 间缺少空行 |
| 17-06 | P3 | 14 文件 | 缺少文件版权头 |
| 17-07 | P3 | 195 行 | 超长行影响可读性 |
