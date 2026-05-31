# 维度 02：模块职责与文件边界

## 第 1 轮（初审）

### [维度02-01] nop-stream-core 包含大量具体运行时执行逻辑，core/runtime 层职责边界模糊

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/`（目录级别）
- **证据片段**:
  ```java
  // TaskExecutor.java (439行)
  public class TaskExecutor {
      private final ExecutorService executorService;
      public Future<?> submitTask(SubtaskTask task) { ... }
      public void awaitCompletion() throws InterruptedException { ... }
      public void shutdownNow() { ... }
  }
  
  // StreamTaskInvokable.java (432行)
  public class StreamTaskInvokable implements Invokable<Void> {
      private final OperatorChain operatorChain;
      public void invoke() throws Exception { ... }
  }
  
  // GraphExecutionPlan.java (462行)
  public class GraphExecutionPlan {
      public static GraphExecutionPlan build(JobGraph jobGraph, ...) { ... }
  }
  ```
- **严重程度**: P2
- **现状**: nop-stream-core 的 execution/ 包下至少 6 个文件（~2500+ 行）是具体运行时执行引擎实现。runtime 模块同时有自身的执行逻辑（GraphModelCheckpointExecutor, JobCoordinator, TaskManager），形成双层结构。
- **风险**: 开发者不清楚新功能应放在 core 还是 runtime；core 体积膨胀（289 个文件），被所有子模块传递依赖；StreamExecutionEnvironment.execute() 在 core 层直接创建 TaskExecutor 执行任务，runtime 层走完全不同路径。
- **建议**: 将 core 中 execution/ 包下的具体运行时实现类移至 nop-stream-runtime。core 只保留接口和抽象类。StreamExecutionEnvironment 的 execute() 改为 SPI/策略模式。
- **信心水平**: 确定
- **误报排除**: Nop 平台标准分层中 -core 应为抽象层和公共类型定义，-runtime 为具体执行引擎。
- **复核状态**: 未复核

### [维度02-02] WindowAggregationOperator (core) 与 WindowOperator (runtime) 存在功能重叠

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/operators/WindowAggregationOperator.java` (834行) + `nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java` (1099行)
- **证据片段**:
  ```java
  // core: WindowAggregationOperator.java L29-55
  public class WindowAggregationOperator<IN, ACC, OUT, K, W extends Window>
          extends AbstractStreamOperator<OUT>
          implements OneInputStreamOperator<IN, OUT>, KeyContext {
      private transient Map<WindowKey<K, W>, ACC> windowState;
      public void processElement(StreamRecord<IN> element) throws Exception { ... }
  }
  
  // runtime: WindowOperator.java L91-93
  public class WindowOperator<K, IN, ACC, OUT, W extends Window>
          extends AbstractUdfStreamOperator<OUT, InternalWindowFunction<ACC, OUT, K, W>>
          implements OneInputStreamOperator<IN, OUT>, Triggerable<K, W> {
      public void processElement(StreamRecord<IN> element) throws Exception { ... }
  }
  ```
- **严重程度**: P2
- **现状**: 两者都实现了窗口分配、触发器回调、合并窗口、水印处理、迟到数据处理、状态快照/恢复等几乎相同的职责，但内部实现完全不同。没有共享的抽象基类。
- **风险**: 修复一个窗口 bug 需要检查两个文件，容易遗漏。API 使用者困惑。
- **建议**: 提取公共抽象基类到 core 层；或标注旧实现为 @Deprecated 并在 Javadoc 中指向新实现。
- **信心水平**: 确定
- **误报排除**: 两者确实都处理完整的窗口生命周期，且都在 main source 中活跃使用。
- **复核状态**: 未复核

### [维度02-03] GraphModelCheckpointExecutor 混合多个执行入口和关注点

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java` (807行)
- **证据片段**:
  ```java
  // L59-97: executeWithCheckpoint(JobGraph) 入口
  // L104-156: executeWithCheckpoint(StreamModel, PartitionedPlan, DeploymentPlan) 入口
  // L175-220: triggerSavepoint 入口
  // L222-261: executeWithSavepoint 入口
  // L271-296: handleJobTermination (CANCEL/DRAIN/SUSPEND)
  ```
- **严重程度**: P3
- **现状**: 807 行全静态方法工具类，5 个公共入口和 ~15 个私有辅助方法。两个 executeWithCheckpoint 方法约 70% 逻辑相同。混合了执行计划构建、检查点协调、任务注册、Barrier 调度、状态恢复、Fingerprint 校验、作业终止处理。
- **风险**: 修改检查点逻辑需同步多个入口，全静态设计不利于测试。
- **建议**: 提取 CheckpointExecutionSession 非静态类消除重复；将恢复逻辑提取为独立的 CheckpointRestoreService。
- **信心水平**: 很可能
- **误报排除**: 文件大但确实是 checkpoint 核心编排器，主要问题是代码重复和静态设计。
- **复核状态**: 未复核

### [维度02-04] nop-stream-api 为空壳，公共 API 接口散落在 core 层

- **文件**: `nop-stream/nop-stream-api/pom.xml:12-14`
- **严重程度**: P3
- **现状**: nop-stream-api 模块已创建但为空。所有公共 API 接口（Function 系列、DataStream 系列、StreamExecutionEnvironment）都在 nop-stream-core 中。
- **风险**: 使用者必须依赖整个 core 模块才能获取接口。
- **建议**: 当前可保持现状（已文档化），中期执行 API 抽取计划。
- **信心水平**: 确定
- **误报排除**: 不影响功能正确性，Nop 平台其他模块也有类似模式。
- **复核状态**: 未复核

### [维度02-05] 三个空占位子模块产生无用 jar 产物

- **文件**: `nop-stream/nop-stream-flink/pom.xml` + `nop-stream-flow/pom.xml` + `nop-stream-checkpoint/pom.xml`
- **严重程度**: P3
- **现状**: 三个空占位模块每次构建都生成空 jar。
- **风险**: 构建产物噪音，增加认知负担。
- **建议**: 近期不实现则注释掉 modules 列表中的空模块。
- **信心水平**: 确定
- **误报排除**: 常见模块预留策略，不影响功能。
- **复核状态**: 未复核

### [维度02-06] nop-stream-checkpoint 空壳但实现在 runtime 层

- **文件**: `nop-stream/nop-stream-checkpoint/` (空) vs `runtime/src/main/java/.../checkpoint/storage/` (实际实现)
- **严重程度**: P3
- **现状**: checkpoint 存储接口定义在 core 层，实现在 runtime 层，而名为 nop-stream-checkpoint 的子模块为空壳。
- **风险**: 模块名与实际代码位置不一致。
- **建议**: 在路线图中标注 checkpoint 子模块的实现计划，或移除空壳。
- **信心水平**: 很可能
- **误报排除**: 空壳状态已在 pom.xml 中注释说明，但与 runtime 实际实现之间存在命名不一致。
- **复核状态**: 未复核

## 正面发现

- 生成文件 `_gen/` 完全合规：4 个文件正确以 `_` 前缀命名，非 _gen 目录无生成代码，无手写修改痕迹
- 非生成代码正确继承生成基类
- 无循环依赖
- JDBC 依赖作用域正确（provided scope）
- CEP/Connector/Fraud-Example 子模块职责清晰
