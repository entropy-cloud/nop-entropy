# 维度 02：模块职责与文件边界

## 审计范围

nop-stream 全部 9 个子模块，621 个 Java 文件，约 89,290 行代码。

## 子模块量化概览

| 子模块 | Java 文件数 | 代码行数 | 定位 |
|--------|-----------|---------|------|
| nop-stream-core | 398 | 48,158 | 核心抽象+图+算子+状态管理+内嵌执行引擎 |
| nop-stream-runtime | 87 | 21,255 | 运行时（checkpoint、集群、task 管理） |
| nop-stream-cep | 108 | 15,916 | CEP 引擎（NFA、模式匹配） |
| nop-stream-connector | 14 | 1,562 | 连接器（source/sink） |
| nop-stream-fraud-example | 14 | 2,399 | 欺诈检测示例 |
| nop-stream-api/checkpoint/flow/flink | 0 | 0 | 空占位符 |

## 第 1 轮（初审）

### [维度02-01] 两个独立 Window 算子分别在 core 和 runtime，职责重叠无继承关系

- **文件**: `nop-stream-core/.../operators/WindowAggregationOperator.java`（808行）和 `nop-stream-runtime/.../operators/windowing/WindowOperator.java`（1093行）
- **证据片段**:
```java
// WindowAggregationOperator.java - core
public class WindowAggregationOperator<KEY, IN, OUT> extends AbstractStreamOperator<OUT> {
    // 完整窗口处理生命周期，不依赖 IKeyedStateBackend
}

// WindowOperator.java - runtime  
public class WindowOperator<K, IN, ACC, OUT> extends AbstractUdfStreamOperator<OUT, InternalWindowFunction<ACC, OUT, K, W>> {
    // 完整窗口处理生命周期，使用 IKeyedStateBackend
}
```
- **严重程度**: P2
- **现状**: 两个独立窗口算子实现，无继承关系，runtime 模块完全不引用 core 的 WindowAggregationOperator。窗口触发逻辑、迟到数据处理、状态管理等存在重复实现。
- **风险**: 修改窗口行为时需同时维护两套代码，易引入不一致。core 模块包含完整可运行的窗口算子，超出"核心抽象"定位。
- **建议**: 明确两者定位关系。若 WindowAggregationOperator 是轻量级嵌入式简化版，应在 Javadoc 中标注。考虑将共用窗口管理逻辑提取到 core 抽象基类。
- **信心水平**: 确定
- **误报排除**: 不是"看起来不优雅"——两套独立的窗口算子实现缺乏任何代码复用或文档说明其差异化定位，是真实的维护风险。
- **复核状态**: 未复核

### [维度02-02] NFACompiler 内部类 NFAFactoryCompiler 约 920 行，体量过大

- **文件**: `nop-stream-cep/.../nfa/compiler/NFACompiler.java:140-1058`
- **证据片段**:
```java
public class NFACompiler {
    // 公共入口方法 (56-139)
    
    private static class NFAFactoryCompiler {
        // 约 920 行内部类，包含完整模式编译逻辑
        // checkPatternNameUniqueness, createEndingState, createMiddleStates,
        // createStartState, createSingletonState, createTimesState, 
        // createGroupPatternState, createLoopingGroupPatternState, ...
    }
}
```
- **严重程度**: P3
- **现状**: 内部类 NFAFactoryCompiler 占文件 83% 代码量（920行）。所有方法围绕"将 Pattern 编译为 NFA 状态图"这一单一职责，不存在职责混合。
- **风险**: 可读性和可维护性降低。
- **建议**: 将 NFAFactoryCompiler 提升为同包下的独立顶层类（package-private 可见性）。纯重构，不改变行为。
- **信心水平**: 确定
- **误报排除**: 不是"看起来不优雅"——920 行内部类在 Nop 仓库中属于异常，且 Flink 原始代码中 NFACompiler 是独立工厂类。
- **复核状态**: 未复核

### [维度02-03] core 模块包含具体运行时执行实现，定位偏宽

- **文件**: 
  - `nop-stream-core/.../execution/TaskExecutor.java`（425行）
  - `nop-stream-core/.../execution/StreamTaskInvokable.java`（432行）
  - `nop-stream-core/.../execution/Task.java`（319行）
  - `nop-stream-core/.../execution/GraphExecutionPlan.java`（442行）
- **证据片段**:
```java
// TaskExecutor.java - core 模块中的具体实现类
public class TaskExecutor {
    // 线程池管理、任务提交/取消/等待完成等完整执行逻辑
}

// StreamTaskInvokable.java - core 模块中的算子链执行入口
public class StreamTaskInvokable {
    // 算子连接、输入处理循环、checkpoint barrier 跟踪等运行时行为
}
```
- **严重程度**: P3
- **现状**: core 模块体量（48K行、288个main文件）远超其他子模块，包含了完整的本地执行引擎。runtime 模块依赖 core 的 TaskExecutor 和 GraphExecutionPlan。
- **风险**: core 的定位从"核心抽象+图定义+算子接口"扩展到了包含完整的本地执行引擎，降低了模块分拆的粒度价值。
- **建议**: 如果有意保持 core 的"自包含嵌入式引擎"定位，建议在模块 README 或 package-info 中明确说明。如果未来需要瘦身，可将 execution 包中的具体实现移至 runtime。
- **信心水平**: 确定
- **误报排除**: 设计有合理性（core 作为自包含嵌入式引擎），但缺乏文档说明这一定位，可能导致后续开发者误解。
- **复核状态**: 未复核

### [维度02-04] 4 个空占位符子模块无代码无 README

- **文件**: nop-stream-api/, nop-stream-checkpoint/, nop-stream-flink/, nop-stream-flow/
- **严重程度**: P3
- **现状**: 4 个子模块各有一个最小化 pom.xml，没有 src 目录或 Java 代码，没有 README 说明规划用途。
- **风险**: 开发者无法判断这些模块的规划意图。
- **建议**: 在每个空模块中添加简短的 README.md 说明规划意图和状态。
- **信心水平**: 确定
- **误报排除**: 不是审美问题——缺少规划文档增加了维护和沟通成本。
- **复核状态**: 未复核

### 通过项

- **生成文件管理规范**: _gen 目录中无手写代码混放，4 个生成文件正确继承。
- **模块间依赖方向正确**: 无反向依赖或循环依赖。
- **超大文件均在流处理引擎合理范围内**: 13 个 >500 行文件均为领域固有复杂度。
- **connector 模块职责清晰**: 仅 6 个 main Java 文件，职责集中。
