# 维度 02：模块职责与文件边界

## 第 1 轮（初审）

### [维度02-01] WindowAggregationOperator (core) 与 WindowOperator (runtime) 职责重复

- **文件**: `nop-stream-core/.../operators/WindowAggregationOperator.java:1-834` 与 `nop-stream-runtime/.../operators/windowing/WindowOperator.java:1-1099`
- **证据片段**:
  ```java
  // core: WindowAggregationOperator - 834 lines
  public class WindowAggregationOperator<IN, ACC, OUT, K, W extends Window>
          extends AbstractStreamOperator<OUT>
          implements OneInputStreamOperator<IN, OUT>, KeyContext {
      private transient Map<WindowKey<K, W>, ACC> windowState;
      // + own timer management, JSON serialization, trigger context
  }
  // runtime: WindowOperator - 1099 lines
  public class WindowOperator<K, IN, ACC, OUT, W extends Window>
          extends AbstractUdfStreamOperator<...>
          implements OneInputStreamOperator<IN, OUT>, Triggerable<K, W> {
      // + state-backend-based, also manages window state, trigger state, timer service
  }
  ```
- **严重程度**: P1
- **现状**: 两个独立窗口算子实现：core 的 WindowAggregationOperator（自管理状态+内联 JSON 序列化）和 runtime 的 WindowOperator（基于 state backend）。两者都处理元素处理、触发器评估、watermark 推进、定时器管理和 checkpoint 快照/恢复。
- **风险**: 语义分歧；bug 需修两处；开发者须知道用哪个。core 版本重新实现了 runtime 版已通过 state backend 抽象处理的功能。
- **建议**: 明确生命周期：如果 core 版是轻量级本地算子而 runtime 版是完整分布式算子，需文档化。否则考虑迁移 core 版委托给 runtime 版。
- **信心水平**: 很可能
- **误报排除**: 两个类存在于不同模块，职责重叠，无继承关系。
- **复核状态**: 未复核

### [维度02-02] GraphModelCheckpointExecutor 包含重复的 execute() 方法结构

- **文件**: `nop-stream-runtime/.../execution/GraphModelCheckpointExecutor.java:59-156`
- **证据片段**:
  ```java
  // Method 1 (line 59): executeWithCheckpoint(JobGraph, String, CheckpointConfig)
  // Method 2 (line 104): executeWithCheckpoint(StreamModel, PartitionedPlan, DeploymentPlan)
  // Both repeat the same ~20-step orchestration pattern
  ```
- **严重程度**: P2
- **现状**: 807 行静态工具类，4 个几乎相同的 execute* 方法，每个重复相同的编排步骤。
- **风险**: 新执行路径需复制完整编排。路径间细微不一致（如 method 2 有 fingerprint validation 但 method 1 没有）。
- **建议**: 提取共享 ExecutionContext 和单一 execute(ExecutionContext) 方法。
- **信心水平**: 确定
- **误报排除**: Lines 59-97 和 104-156 结构相同，仅 3-5 行设置不同。
- **复核状态**: 未复核

### [维度02-03] Core 模块包含运行时执行类（TaskExecutor, GraphExecutionPlan 等）

- **文件**: `nop-stream-core/.../execution/TaskExecutor.java:1-439`, `GraphExecutionPlan.java:1-462`, `CheckpointBarrierTracker.java:1-162`
- **证据片段**:
  ```java
  // TaskExecutor.java in CORE:
  public class TaskExecutor {
      private final ExecutorService executorService;
      public void submitTask(SubtaskTask task) { ... }
  }
  ```
- **严重程度**: P2
- **现状**: core 模块（407 文件）包含执行基础设施（线程池、数据交换通道、checkpoint barrier 追踪）。这些存在是因为 StreamExecutionEnvironment.execute() 在 core 中运行本地作业。
- **风险**: core 模块过大（捆绑 API + 模型 + 算子 + 状态 + 图编译 + 执行）。本地执行路径绕过 runtime 的 checkpoint/容错。
- **建议**: 将执行类标记 @Internal 并文档化为 local-only。nop-stream-api 占位模块确认了提取意图。
- **信心水平**: 很可能
- **误报排除**: StreamExecutionEnvironment.execute() 确实直接实例化 TaskExecutor 运行本地作业。
- **复核状态**: 未复核

### [维度02-04] WindowAggregationOperator 内联序列化应提取到独立 SerDe 类

- **文件**: `nop-stream-core/.../operators/WindowAggregationOperator.java:551-691`
- **证据片段**:
  ```java
  private Map<String, Object> serializeWindowState(...) { ... }
  private void deserializeWindowState(Map<String, Object> data, ...) throws Exception { ... }
  // 6 个 serialize/deserialize 方法，约 140 行（17%）
  ```
- **严重程度**: P2
- **现状**: 834 行中 ~140 行用于序列化/反序列化，与算子逻辑混合。
- **建议**: 提取到 WindowAggregationStateSerDe 工具类（遵循 MemoryStateSerDe 模式）。
- **信心水平**: 很可能
- **误报排除**: MemoryStateSerDe 已从 MemoryKeyedStateBackend 提取，是同类模式。
- **复核状态**: 未复核

### [维度02-05] MemoryStateSerDe 有 14 个结构相似的序列化方法

- **文件**: `nop-stream-core/.../state/backend/memory/MemoryStateSerDe.java:83-385`
- **证据片段**:
  ```java
  void restoreState(Map<String, Object> states, StateSnapshot snapshot) {
      switch (stateType) {
          case "ValueState":    restoreValueState(...); break;
          case "MapState":      restoreMapState(...); break;
          // 7 种状态类型，每种 2 个方法（snapshot + restore）
      }
  }
  ```
- **严重程度**: P2
- **现状**: 14 个方法共享 ~70% 样板代码。字段命名不一致（valueTypeName vs valueType）。
- **建议**: 引入 StateTypeHandler 接口 + 注册表，消除 switch 和重复的迭代逻辑。
- **信心水平**: 确定
- **误报排除**: restoreValueState 与 restoreListState 结构相同，仅状态类型特定构造不同。
- **复核状态**: 未复核

### [维度02-06] JobCoordinator.globalRecovery 通过 instanceof TaskManager 绕过 RPC 接口

- **文件**: `nop-stream-runtime/.../coordinator/JobCoordinator.java:414-418`
- **证据片段**:
  ```java
  for (IStreamTaskRpcService rpc : taskRpcServices.values()) {
      if (rpc instanceof TaskManager) {
          ((TaskManager) rpc).updateFencingToken(newToken);
      }
  }
  ```
- **严重程度**: P1
- **现状**: globalRecovery() 用 instanceof TaskManager 调用 updateFencingToken()，绕过 IStreamTaskRpcService 接口。
- **风险**: 分布式部署中 RPC proxy（非本地 TaskManager 实例）的 instanceof 检查失败，fencing token 更新被静默跳过，导致脑裂执行。
- **建议**: 将 updateFencingToken(String) 添加到 IStreamTaskRpcService 接口。移除 instanceof 检查。
- **信心水平**: 确定
- **误报排除**: IStreamTaskRpcService 接口确实没有 updateFencingToken 方法。注释说 "Update fencing token on all registered TaskManagers" 但 RPC 抽象被违反。
- **复核状态**: 未复核
