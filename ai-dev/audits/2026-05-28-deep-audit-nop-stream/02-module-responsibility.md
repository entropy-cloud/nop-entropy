# 维度 02：模块职责与文件边界

## 第 1 轮（初审）

### [维度02-01] 4 个空壳占位子模块缺少明确的实现优先级标注

- **文件**: `nop-stream/nop-stream-{api,checkpoint,flow,flink}/pom.xml`
- **证据片段**:
```xml
<!-- nop-stream-api/pom.xml -->
<!-- placeholder, planned but not implemented -->
<!-- interfaces are in nop-stream-core; this module is reserved for future API extraction -->
```
- **严重程度**: P3
- **现状**: 四个子模块只有 `pom.xml`（均标注 `placeholder, planned but not implemented`），无任何 `src/` 目录。它们参与 Maven 反应器构建但产出空壳 jar。pom.xml 中缺少预期职责和优先级标注。
- **风险**: 对新开发者造成困惑——不知道这些模块是"即将建设"还是"废弃残留"。
- **建议**: 在各 placeholder pom.xml 中添加预期职责、优先级和预计实现版本号。如果不计划实现，应移除或移到 `inactive/` 目录。
- **误报排除**: placeholder 模块在框架项目中是常见的预占位模式，问题是缺少清晰的优先级和计划信息。
- **复核状态**: 未复核

### [维度02-02] MemoryKeyedStateBackend.java (1254行) 职责过重

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryKeyedStateBackend.java`
- **证据片段**:
```java
// 文件结构：
// 行 1-248: 后端管理（类定义、构造器、状态工厂方法）
// 行 249-711: snapshot/restore 序列化逻辑（~460行）
// 行 721-995: 7 个内部状态实现类（MemoryListState, MemoryValueState, MemoryMapState, ...）
// 行 1001-1254: 更多内部状态类 + 辅助类
```
- **严重程度**: P2
- **现状**: 单文件同时承担三种职责：(1) 状态后端的生命周期管理 (~250行) (2) 7 种状态的内存实现 (~400行) (3) 完整的序列化/反序列化逻辑 (~460行)。其中 snapshot/restore 代码有大量模式重复（每种状态类型都有独立的 snapshot* 和 restore* 方法）。
- **风险**: 后续若新增状态类型（如 BroadcastState），本文件会继续膨胀。1254 行已超过单文件合理维护上限。
- **建议**: 将 7 个内部状态类提取到 `io.nop.stream.core.common.state.backend.memory` 包下独立文件；将 snapshot/restore 提取到 `MemoryStateBackendSerializer` 工具类。保留 `MemoryKeyedStateBackend` 本身约 250-300 行。
- **误报排除**: 这不是"文件大就拆"的泛化问题——1254 行中有 3 种明显不同的职责，且 snapshot/restore 有 ~460 行的模式重复代码。
- **复核状态**: 未复核

### [维度02-03] NFACompiler.java (1090行) 结构合理但内含超大内部类

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/compiler/NFACompiler.java`
- **证据片段**:
```java
// NFAFactoryCompiler 内部类约占 900 行，是 Pattern -> NFA 图编译的单一算法
// 方法按职责分组：状态创建、条件处理、辅助逻辑、Group Pattern 支持
```
- **严重程度**: P3
- **现状**: NFAFactoryCompiler 虽有约 900 行，但它是一个单一的编译算法，内部方法围绕 Pattern->State 图的构建紧密耦合，具备强内聚性。移植自 Apache Flink（保留 ASF 头部），算法已被社区验证。
- **风险**: 如果未来需要扩展（如新增 Pattern 类型），可能需要拆分。
- **建议**: 暂不拆分。如果未来需要扩展 Pattern 类型，届时可将 Group Pattern 相关方法抽到 `GroupPatternCompiler`。
- **误报排除**: 算法紧密耦合性导致拆分收益低，不是"大就拆"的泛化问题。
- **复核状态**: 未复核

### [维度02-04] WindowOperator.java (1088行) processElement 方法过长

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:326-480`
- **证据片段**:
```java
// processElement 方法约 155 行，包含 MergingWindow 和非 MergingWindow 双分支逻辑
// 双分支有 ~120 行重复结构
```
- **严重程度**: P3
- **现状**: processElement 方法包含 MergingWindow 和非 MergingWindow 双分支，有代码对称性（~120 行重复结构）。1088 行中大部分是不可避免的窗口算子复杂度。
- **风险**: processElement 过长（~155行）增加了理解难度。
- **建议**: 提取 `processElementForMergingWindow` 和 `processElementForRegularWindow` 子方法。
- **误报排除**: 窗口算子双分支逻辑是领域复杂度导致的，不是设计错误。但 155 行的方法确实过长。
- **复核状态**: 未复核

### [维度02-05] GraphModelCheckpointExecutor.java (805行) restore 逻辑有重复

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java`
- **证据片段**:
```java
// restoreFromCheckpoint 和 restoreFromSavepointPath 有 ~100 行结构相同的遍历+恢复逻辑
```
- **严重程度**: P3
- **现状**: 全部为 static 方法的编排工具类，围绕"带 checkpoint 的流作业执行"单一职责。`restoreFromCheckpoint` 和 `restoreFromSavepointPath` 有大量重复的遍历+恢复逻辑（约 100 行结构相同）。
- **风险**: 代码重复增加了维护成本。
- **建议**: 提取通用的 `restoreFromCompletedCheckpoint(execPlan, checkpointPlan, checkpoint)` 方法。
- **误报排除**: 805 行的工具类中方法围绕同一编排流程，结构合理。问题仅在于 restore 方法的重复。
- **复核状态**: 未复核

### [维度02-06] 模块依赖方向正确，无越权导入

- **验证结果**:
  - core 不依赖 runtime/cep/connector ✓
  - cep 不依赖 runtime/connector ✓
  - connector 不依赖 runtime/cep ✓

### [维度02-07] 生成代码管理规范

- **验证结果**: 4 个 `_gen` 文件均为纯生成代码，手写扩展类正确继承 `_gen` 基类。无混放问题。

### [维度02-08] WindowAggregationOperator 定位模糊

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/WindowAggregationOperator.java`
- **严重程度**: P3
- **现状**: core 模块中存在一个功能完整的窗口聚合算子实现（658行），与 runtime 模块中的 `WindowOperator`（1088行）功能相似但定位不同。缺少定位说明注释。
- **风险**: 后续开发者困惑于为什么 core 模块有完整算子实现。
- **建议**: 在文件头注释中说明其定位（如"Simplified window aggregation operator for core-level use, without checkpoint support"）。
- **误报排除**: core 确实可以包含自带实现的算子。问题是缺少定位说明。
- **复核状态**: 未复核
