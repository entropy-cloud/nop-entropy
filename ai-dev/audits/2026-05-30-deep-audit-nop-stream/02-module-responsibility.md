# 维度 02：模块职责与文件边界

## 第 1 轮（初审）

### [维度02-01] GraphModelCheckpointExecutor 803 行纯静态方法类承担 6 种职责

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:55-803`
- **证据片段**:
  ```java
  // 第一个入口：第 59-97 行
  public static StreamExecutionResult executeWithCheckpoint(
          JobGraph jobGraph, String jobName, CheckpointConfig checkpointConfig) throws Exception {
      long startTime = System.currentTimeMillis();
      boolean barrierAlignment = resolveBarrierAlignment(checkpointConfig);
      GraphExecutionPlan execPlan = buildExecutionPlan(jobGraph, barrierAlignment);
      String jobId = resolveJobId(checkpointConfig);
      String pipelineId = resolvePipelineId(checkpointConfig);
      CheckpointIDCounter idCounter = new CheckpointIDCounter();
      ICheckpointStorage storage = createStorage(checkpointConfig);
      CheckpointPlan checkpointPlan = CheckpointPlanBuilder.build(execPlan, jobId, pipelineId, null, checkpointConfig);
      CheckpointCoordinator coordinator = createCoordinator(jobId, pipelineId, idCounter, storage, checkpointConfig);
      List<StreamTaskInvokable> allInvokables = registerTasksAndTrackers(execPlan, checkpointPlan, coordinator);
      ScheduledExecutorService barrierScheduler = startBarrierScheduler(...);
      restoreFromCheckpoint(execPlan, coordinator, checkpointPlan, null);
      Map<String, SubtaskTask> tasks = buildTasks(execPlan);
      TaskExecutor executor = new TaskExecutor();
      try { submitAndRun(...); handleJobTermination(...); ... } finally { shutdown(...); }
  }
  ```
- **严重程度**: P2
- **现状**: 该文件是一个 803 行的全静态方法类，包含 4 个公共入口方法和约 20 个私有辅助方法。同时承担执行编排、存储创建、状态恢复、任务终止模式、Barrier 调度、指标日志等 6 种职责。
- **风险**: 新增执行路径时需要在多个入口方法中复制相同的编排步骤，容易遗漏导致不一致。违反单一职责原则。
- **建议**: 提取公共编排流程为模板方法，将 createStorage、restoreFromCheckpoint、handleJobTermination 等分别提取为独立类。
- **信心水平**: 90%
- **误报排除**: 若认为全静态工具类是有意设计模式，可降级为 P3。但同类重复（4 段几乎相同的编排流程）是结构性问题。
- **复核状态**: 未复核

### [维度02-02] core 模块 WindowAggregationOperator 与 runtime 模块 WindowOperator 功能重叠

- **文件**:
  - `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/WindowAggregationOperator.java`
  - `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java`
- **证据片段**:
  ```java
  // core 模块
  public class WindowAggregationOperator<IN, ACC, OUT, K, W extends Window>
          extends AbstractStreamOperator<OUT>
          implements OneInputStreamOperator<IN, OUT>, KeyContext { ... }

  // runtime 模块
  public class WindowOperator<K, IN, ACC, OUT, W extends Window>
          extends AbstractUdfStreamOperator<OUT, InternalWindowFunction<ACC, OUT, K, W>>
          implements OneInputStreamOperator<IN, OUT>, Triggerable<K, W> { ... }
  ```
- **严重程度**: P2
- **现状**: 两者都实现了完整的窗口处理逻辑（窗口分配、Trigger 回调、窗口合并、状态快照/恢复、定时器管理），但有不同的状态后端策略。
- **风险**: 相同领域逻辑需在两处同步维护。对使用者造成困惑。
- **建议**: 在两个文件头部添加注释说明各自的定位和使用场景。考虑将共享逻辑提取为共享工具类或抽象基类。
- **信心水平**: 85%
- **误报排除**: 两者确实有不同的状态后端策略（自管理 vs IKeyedStateBackend），有明确的差异化设计意图。问题在于缺少文档。
- **复核状态**: 未复核

### [维度02-03] core 模块包含大量运行时执行引擎代码，职责边界模糊

- **文件**:
  - `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/TaskExecutor.java` (439 行)
  - `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/GraphExecutionPlan.java` (442 行)
  - `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/StreamTaskInvokable.java` (432 行)
  - `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java` (344 行)
- **证据片段**:
  ```java
  // TaskExecutor 线程池管理 (core 模块)
  public class TaskExecutor {
      private final ExecutorService executorService;
      private final Map<String, Task> submittedTasks;
      private final Map<String, Future<?>> taskFutures;
      private final AtomicBoolean isShutdown;
      public TaskExecutor(int poolSize) {
          this.executorService = Executors.newFixedThreadPool(poolSize, r -> {
              Thread t = new Thread(r, "stream-task-executor-" + THREAD_COUNTER.getAndIncrement());
              t.setDaemon(true);
              return t;
          });
      }
  }
  ```
- **严重程度**: P2
- **现状**: core 模块的 execution 包包含 TaskExecutor、GraphExecutionPlan、StreamTaskInvokable、InputGate 等运行时执行引擎组件，同时 runtime 模块也有自己的执行组件。
- **风险**: core 模块膨胀至 48,808 行。修改执行引擎逻辑需触及 core 模块，影响面大。
- **建议**: 考虑将 execution 包拆分为 execution-api（接口和数据类）和 execution-impl（实现类），后者移入 runtime。或在 package-info.java 中明确记录设计决策。
- **信心水平**: 75%
- **误报排除**: core 的 execution 包可能被 runtime 和 fraud-example 两个下游模块共同依赖，可能是当前设计的合理折衷。
- **复核状态**: 未复核

### [维度02-04] GraphModelCheckpointExecutor 4 个入口方法中 25+ 行编排代码逐行重复 4 次

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:59-261`
- **证据片段**:
  ```java
  // 入口1 (第59-97行): executeWithCheckpoint(JobGraph, String, CheckpointConfig)
  boolean barrierAlignment = resolveBarrierAlignment(checkpointConfig);
  GraphExecutionPlan execPlan = buildExecutionPlan(jobGraph, barrierAlignment);
  // ... 25+ 行相同编排代码 ...

  // 入口2 (第104-156行): 完全相同的步骤序列，仅参数来源不同
  // 入口3 (第175-220行): 完全相同的步骤序列，仅 run 阶段不同
  // 入口4 (第222-261行): 完全相同的步骤序列，仅 restore 阶段不同
  ```
- **严重程度**: P1
- **现状**: 4 个公共入口方法共享完全相同的"初始化→构建→注册→调度→恢复→运行→终止"流程，差异仅在入口参数类型、恢复策略、运行阶段动作。
- **风险**: 高概率回归——修改编排流程必须同时修改 4 处，遗漏导致某个入口行为不一致。
- **建议**: 提取通用的 CheckpointExecutionContext 对象和模板方法。
- **信心水平**: 95%
- **误报排除**: 重复代码是可量化的结构性问题（4 段 25+ 行逐行相同代码），不属于"不优雅"范畴。
- **复核状态**: 未复核

### [维度02-05] WindowAggregationOperator 混合了算子逻辑与 140 行序列化/反序列化职责

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/WindowAggregationOperator.java:543-683`
- **证据片段**:
  ```java
  // 第 543-589 行：3 个序列化方法
  private Map<String, Object> serializeWindowState(Map<WindowKey<K, W>, ACC> state) { ... }
  private String serializeWindowKey(WindowKey<K, W> wk) {
      return JsonTool.stringify(wk.key) + "#" + JsonTool.stringify(wk.window);
  }
  private Map<String, Object> serializeTimers(TreeMap<Long, Set<WindowKey<K, W>>> timers) { ... }
  // 第 591-683 行：4 个反序列化方法 + rebuildTimerLookups
  private void deserializeWindowState(...) throws Exception { ... }
  private void deserializeTimers(...) throws Exception { ... }
  ```
- **严重程度**: P2
- **现状**: 825 行算子中混合了约 140 行序列化/反序列化逻辑，使用了自定义 JSON 格式。
- **风险**: 序列化格式变更需要在算子文件中修改，增加破坏算子逻辑的风险。
- **建议**: 提取 WindowAggregationStateSerializer 类。
- **信心水平**: 80%
- **误报排除**: 如果项目统一在算子内部管理状态序列化则可降级。但 140 行已达到独立类的合理阈值。
- **复核状态**: 未复核

### [维度02-06] 4 个空占位模块增加构建时间和认知负担

- **文件**: `nop-stream/nop-stream-api/pom.xml`, `nop-stream/nop-stream-checkpoint/pom.xml`, `nop-stream/nop-stream-flink/pom.xml`, `nop-stream/nop-stream-flow/pom.xml`
- **证据片段**:
  ```xml
  <!-- nop-stream-api pom.xml -->
  <artifactId>nop-stream-api</artifactId>
  <!-- placeholder, planned but not implemented -->
  ```
- **严重程度**: P3
- **现状**: 4 个子模块只有 pom.xml，没有 src 目录和任何代码。
- **风险**: 增加不必要的 Maven 反应器构建时间。新开发者困惑。
- **建议**: 如果近期不计划实现，考虑从 pom.xml 的 modules 中移除。
- **信心水平**: 95%
- **误报排除**: 常见的预占位做法，但 4 个空模块同时存在增加认知负担。
- **复核状态**: 未复核

## 生成代码检查

`_gen/` 目录下的 4 个生成文件均为标准 Nop 平台代码生成器输出，未发现手写修改痕迹。
