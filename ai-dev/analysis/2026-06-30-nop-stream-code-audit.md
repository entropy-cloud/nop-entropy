# nop-stream 代码深度审计报告

> 日期：2026-06-30
> 范围：`nop-stream/` 全部 9 个子模块
> 方法：包结构 + 实现完整性 + 测试覆盖 + 端到端验证 + 代码质量

---

## 一、包结构审计

### 1.1 模块概览

| 模块 | 源文件 | 测试文件 | 状态 |
|------|--------|---------|------|
| `nop-stream-core` | ~220 | ~115 | ✅ 活跃开发 |
| `nop-stream-runtime` | ~47 | ~83 | ✅ 活跃开发 |
| `nop-stream-cep` | ~72 | ~46 | ✅ 活跃开发 |
| `nop-stream-connector` | 7 | 11 | ✅ 活跃开发 |
| `nop-stream-fraud-example` | 10 | 5 | ✅ 示例 |
| `nop-stream-api` | 0 | 0 | ❌ **空壳**（仅 pom.xml） |
| `nop-stream-checkpoint` | 0 | 0 | ❌ **空壳** |
| `nop-stream-flink` | 0 | 0 | ❌ **空壳** |
| `nop-stream-flow` | 0 | 0 | ❌ **空壳** |

**问题**：9 个模块中 4 个是空壳。它们占用了 reactor build 时间但产出为零。`nop-stream-api` 是规划中的接口抽取模块，但代码都在 core 中；`nop-stream-checkpoint` 的代码在 runtime 中。

### 1.2 包级重复与不合理

| 问题 | 说明 | 严重度 |
|------|------|--------|
| **`TimerService` 名称冲突** | `io.nop.stream.core.time.TimerService`（@Deprecated，空接口）vs `io.nop.stream.cep.time.TimerService`（活跃使用） | **中** |
| **`state.shard` / `state.backend` 碎片** | `ShardPrefixedKey` 同时出现在 `state.shard` 和 `state.backend.memory` 下（同一份代码两份） | **中** |
| **`execution.transport` vs `runtime.transport`** | `StreamElementCodec` / `StreamMessageEnvelope` / `TypeRegistry` 在 core 中，`RemoteResultPartition` / `RemoteInputChannel` 在 runtime 中。但 `RecordWriter`/`InputGate`/`ResultPartition` 也在 core 中。边界模糊 | **低** |
| **`checkpoint` 包散布在 core 和 runtime** | core 中有 `CheckpointBarrier` / `CheckpointPlan` / `TaskEpochSnapshot` 等模型类，runtime 中有 `CheckpointCoordinator` / `PendingCheckpoint` 等执行类。这个分离合理（模型 vs 执行），但 `CheckpointPlanBuilder` 在 runtime 而 `CheckpointPlan` 在 core | **低** |
| **`windowing` 也在 core 和 runtime 间分割** | core: 窗口四要素接口 + assigner/trigger/evictor。runtime: `WindowOperator` 实现 + `WindowOperatorBuilder` + `MergingWindowSet`。分离合理 | ✅ 合理 |
| **`common.state.backend.memory` 有 14 个文件** | 这是合理的（每个状态类型一个实现类） | ✅ 合理 |
| **`execution` 包含 26 个文件** | 承担了太重职责：数据交换（RecordWriter/ResultPartition/InputGate）+ Task 执行（Task/SubtaskTask/TaskExecutor）+ 分发（IStreamExecutionDispatcher）+ 图执行（GraphExecutionPlan）+ 配置（EdgeConfig/MemoryBudget） | **中**（建议拆分） |

### 1.3 依赖方向验证

核心的设计约束是 `runtime/connector/cep → core → api`。实际验证：

```
nop-stream-core:      无 nop-stream 内部依赖 ✅
nop-stream-runtime:   依赖 core ✅
nop-stream-cep:       依赖 core + nop-xlang ✅
nop-stream-connector: 依赖 core + nop-batch-core ✅
```

**违规检查**：runtime → cep 有一个**幽灵依赖**（pom.xml 中有声明但零代码引用）。这是已知的技术债（`component-roadmap.md` §5 已记录）。

---

## 二、实现完整性审计

### 2.1 `UnsupportedOperationException` 桩方法

共发现 **6 处**抛 UOE 的桩方法：

| 位置 | 方法 | 原因 | 严重度 |
|------|------|------|--------|
| `SingleOutputStreamOperatorImpl.forceNonParallel()` | 强制非并行 | 接口定义了但未实现 | **中** |
| `GroupPattern.where/or/subtype` | 组模式的条件方法 | 合法的"禁止调用"设计 | 低 |
| `WindowAggregationFunction.merge()` | 窗口聚合合并 | `@Deprecated` 接口 | 低 |
| `Trigger.onMerge()` | 触发器合并 | 基类空实现，可覆盖 | 低 |
| `ICheckpointExecutorFactory.executeWithCheckpoint(StreamModel)` | 基于模型的 API | 规划中 | **中** |
| `DemoKeyedStateStore.getReducingState/getAggregatingState` | Fraud demo 状态 | demo 简化 | 低 |

关键缺失：`forceNonParallel()` 和 `ICheckpointExecutorFactory` 的 StreamModel 路径——这两个是 API 级别定义的接口方法但无法使用。

### 2.2 危险代码模式

#### 🔴 Optional 返回 null（高严重度）

`InputGate.java` 中有 **6 处**从 `Optional<StreamElement>` 方法返回 raw `null`：

| 方法 | 行号 | 问题 |
|------|------|------|
| `handleBarrierNonRecursive()` | 320, 336 | `return null;` 应为 `return Optional.empty()` |
| `checkBarrierAlignmentComplete()` | 386 | `return null;` 应为 `return Optional.empty()` |
| `handleWatermarkNonRecursive()` | 342, 353 | `return null;` 应为 `return Optional.empty()` |

调用者在 `readMultiChannel()`（266, 272 行）通过 `if (result != null)` 检查规避了 NPE，但这**完全违背了 Optional 的设计契约**。调用者无法安全使用 `.orElse()` / `.isPresent()`。

#### 🟡 通配符导入（中严重度）

**78 个文件**使用 `import java.util.*;`。违反了 AGENTS.md 中"Imports: grouped"的规范。受影响的关键文件包括：

- `WindowAggregationOperator.java`
- `CheckpointCoordinator.java`
- `StreamModel.java` / `StreamComponents.java`
- `CheckpointPlan.java` / `DeploymentPlan.java` / `PartitionedPlan.java`
- `JdbcCheckpointStorage.java` / `LocalFileCheckpointStorage.java`
- 几乎所有 runtime 测试文件（~30+）

#### 🟡 大量 `return null`（中严重度）

源码中有 **100+ 处 `return null`**。大部分合理（流结束、超时等语义），但集中在 `JdbcCheckpointStorage`（15 处）、`JdbcClusterRegistry`（11 处）、`LocalFileCheckpointStorage`（8 处）——这些 JDBC 存储实现中的 null 返回在 catch 块中较多，调用者缺少 null 保护时可能 NPE。

#### 🟡 硬编码值

| 位置 | 值 | 说明 |
|------|-----|------|
| `InputGate` | `LockSupport.parkNanos(10_000_000)` = 10ms 轮询间隔 | 硬编码，不可配置 |
| `InputGate` | `DEFAULT_ALIGNMENT_TIMEOUT_MS = 30000` | 常量但可作为配置参数 |
| `TaskManager` | 心跳间隔 5s、lease 超时 15s、轮询间隔 3s | 硬编码 |
| `JdbcCheckpointStorage` | 表名 `stream_checkpoint` | 硬编码 |

### 2.3 已废弃 API 的使用

`WindowedStreamImpl.java` 使用 `@Deprecated` 的 `WindowAggregationFunction` / `WindowAggregationOperator` 作为回退路径（当 `IWindowOperatorFactory` 返回 null 时）。这意味着 DataStream API 的正常窗口聚合路径走的是**已废弃的代码路径**，而非正式的 `WindowOperator` 路径。

### 2.4 `_gen` 生成代码

仅在 `nop-stream-cep` 中有 4 个 `_gen` 文件（`_CepPatternSingleModel` 等）。无其他模块有生成的代码。这是正确的——`_gen` 文件不手改。

---

## 三、测试覆盖审计

### 3.1 全量统计

| 模块 | 测试类 | @Test 方法 | 源文件 | 测试/源码比 |
|------|--------|-----------|--------|------------|
| nop-stream-cep | 46 | 272 | 72 | 0.64 |
| nop-stream-core | ~115 | 976 | ~220 | 0.52 |
| nop-stream-runtime | ~83 | 453 | ~47 | **1.77** |
| nop-stream-connector | 11 | 50 | 7 | **1.57** |
| nop-stream-fraud-example | 5 | 25 | 10 | 0.50 |
| **合计** | **~260** | **~1,776** | **~356** | **0.73** |

测试覆盖非常充足。runtime 和 connector 的测试/源码比超过 1.0（因为 runtime 的源码少但测试完备）。

### 3.2 核心路径覆盖评估

#### ✅ 覆盖充分的区域

| 核心功能 | 测试覆盖 | 关键测试 |
|----------|---------|---------|
| **CEP NFA 引擎** | 272 个 @Test，13+ 个测试类 | NFA 匹配、共享缓冲区、Dewey 编号、窗口超时、贪婪匹配、跳过策略 |
| **状态后端** | 9 个测试类 | Value/Map/Aggregating/List 状态、namespace 隔离、key 隔离、snapshot/restore round-trip |
| **检查点协调器** | 30+ 个测试类 | 触发/ACK/完成/abort/恢复/两阶段提交/保存点/并发安全 |
| **执行引擎** | 30+ 个测试类 | 图模型、Task 生命周期、数据交换、分区路由、barrier 对齐/转发/超时 |
| **窗口操作** | 16+ 个测试类 | Tumbling/Sliding/Session/Global 窗口、Evictor、Trigger、合并窗口 |
| **集成/E2E** | 9 个 core + 多个 runtime | 完整 pipeline（Source→算子→Sink）、事件时间窗口、检查点恢复 |

#### ⚠️ 覆盖不足的区域

| 包/功能 | 问题 | 严重度 |
|---------|------|--------|
| `common.functions.co`（CoMapFunction/CoFlatMapFunction）| **零测试** | **高**（但这是规划不实现的功能） |
| `common.functions.source`（SourceFunction 接口）| **零独立测试**（在 connector 中有间接测试） | 低 |
| `configuration`（Configuration 类）| **零测试** | 低 |
| `streamrecord` / `streamrecord.watermark` | **零独立测试**（通过集成测试间接覆盖） | 低 |
| `windowing.delta` / `windowing.utils` | **零测试** | 低 |
| `common.typeutils`（TypeSerializer）| **零测试**（在 CEP 中有间接测试） | 低 |
| `execution.flow` / `execution.plan` / `execution.transport` | 各 1 个测试 | 低 |
| **算子链多链管线** | `TestGraphModelExecution` 覆盖了 keyBy 管线，但缺少"分支（multiple outputs）+ 合并（multiple inputs）"场景 | **中** |
| **Operator State** | 未实现，无测试 | **高**（已知缺口） |
| **分布式真实跨 JVM** | `TestEmbeddedDistributedExecution` 使用同进程 `InProcessMessageService` 模拟分布式，并非真正多 JVM | **中** |

### 3.3 E2E 测试现状

现有端到端测试：

| 测试 | 范围 | 覆盖了什么 |
|------|------|-----------|
| `TestE2ESimplePipeline` | core | Source→map→sink, Source→filter→sink, 多链, 空 Source |
| `TestEndToEndPipeline` | core | API→Transformation→StreamGraph→JobGraph→TaskExecutor 完整链路 |
| `TestEventTimeWindowE2E` | core | 事件时间窗口的端到端 |
| `TestSessionWindowE2E` | core | Session 窗口的端到端 |
| `TestWindowAggregationE2E` | core | 窗口聚合的端到端 |
| `TestE2ECheckpointAndRecovery` | runtime | 检查点触发→barrier→快照→持久化→恢复 |
| `TestE2EMultiVertexCheckpoint` | runtime | 多顶点检查点 |
| `TestE2EMultipleCheckpoints` | runtime | 多个连续检查点 |
| `TestE2ETwoPhaseCommitSink` | runtime | 两阶段提交 Sink E2E |
| `TestE2EWindowOperatorWithCheckpoint` | runtime | 窗口 + 检查点 |
| `TestDistributedE2EIntegration` | runtime | 分布式模式 E2E（同进程模拟） |
| `TestCepPublicApiE2E` | cep | CEP.pattern() 从 DataStream/KeyedStream 创建 |
| `TestCepOperatorStateRecovery` | cep | CEP 状态恢复 |

**缺口**：
1. **无跨 JVM 真实分布式 E2E**（所有 distributed 测试用同进程消息服务模拟）
2. **无 Operator State E2E**（未实现）
3. **无 Union/SideOutput E2E**（未实现）

---

## 四、关键问题深度分析

### 4.1 InputGate Optional 反模式

这是**最高严重度的代码问题**：

```
Optional<StreamElement> handleBarrierNonRecursive(...) {
    // ...
    return null;  // BUG: 应为 Optional.empty()
}

// 调用方被迫做 null 检查：
Optional<StreamElement> result = handleBarrierNonRecursive(...);
if (result != null) {  // 违背 Optional 设计意图
    // ...
}
```

影响：如果未来某个调用方使用 `result.orElse(...)` 或 `result.isPresent()`，会直接 NPE。当前代码侥幸正确只是因为调用方做了 null 检查。

**修复方案**：将 6 处 `return null` 改为 `return Optional.empty()`，移除调用方的 null 检查改为 `result.isPresent()` / `result.isEmpty()`。

### 4.2 废弃 API 回退路径

`WindowedStreamImpl` 的窗口聚合通过两个路径：
1. **主路径**（期望）：`IWindowOperatorFactory` → `WindowOperator`（runtime 实现）
2. **回退路径**（当前实际走的路径？）：`WindowAggregationFunction` + `WindowAggregationOperator`（已废弃）

需要验证在默认配置下是否真正走了 `IWindowOperatorFactory` 路径。如果 `StreamComponents` 中没有注册 `IWindowOperatorFactory`，所有 DataStream API 的窗口聚合都走到了已废弃的回退路径。

### 4.3 4 个空壳模块

| 模块 | 规划用途 | 代码实际位置 |
|------|---------|-------------|
| `nop-stream-api` | 公共接口抽取 | 全部在 nop-stream-core 中 |
| `nop-stream-checkpoint` | 独立 checkpoint 协调器 | 全部在 nop-stream-runtime 中 |
| `nop-stream-flink` | Flink 后端适配 | 未有实现 |
| `nop-stream-flow` | XDSL 声明式编排 | 未有实现 |

### 4.4 execution 包膨胀

`io.nop.stream.core.execution` 包含 26 个文件，承载了**数据交换**（RecordWriter/ResultPartition/InputChannel/InputGate/RecordReader）、**Task 执行**（Task/SubtaskTask/TaskExecutor/StreamTaskInvokable）、**图执行**（GraphExecutionPlan/CheckpointBarrierTracker）、**配置**（EdgeConfig/MemoryBudget）、**分发 SPI**（IStreamExecutionDispatcher）——至少 5 个不同职责。建议拆分为：
- `execution.transport`（已有，但 RecordWriter 等还在 execution 根包）
- `execution.runtime`（Task, SubtaskTask, TaskExecutor）
- `execution.plan`（已存在：GraphExecutionPlan、CheckpointBarrierTracker）

---

## 五、与 Flink / SeaTunnel 对比

### 5.1 实现完整度对比

| 维度 | Flink | SeaTunnel | nop-stream |
|------|-------|-----------|------------|
| 空壳模块 | 0/40+ | 0/30+ | **4/9** |
| 测试/源码比 | ~0.5-0.6 | ~0.3-0.4 | **~0.73** ✅ |
| E2E 测试 | 丰富（flink-tests, flink-end-to-end-tests） | 丰富（seatunnel-e2e） | 有限（9 个 E2E，无多 JVM 测试） |
| Operator State | ✅ 完整 | ✅ Source 状态 | ❌ 缺口 |
| 通配符导入 | 禁止（checkstyle） | 禁止（checkstyle） | 78 个文件 |
| 空实现的桩方法 | 极少 | 极少 | 6 处 UOE |

nop-stream 在**测试密度**上超过 Flink/SeaTunnel，但在**模块成熟度**和**代码规范**上仍然落后。

### 5.2 SeaTunnel 的教训

SeaTunnel 的包结构清晰度值得借鉴：
```
seatunnel-api/         → 纯接口
seatunnel-core/        → 入口
seatunnel-engine/      → 引擎实现
seatunnel-connectors/  → 连接器
```

nop-stream 的问题在于 `nop-stream-api` 是空壳，接口都在 `nop-stream-core` 中，而 `nop-stream-core` 又包含了实现（operators、execution）。这使得"面向接口编程"的模块边界形同虚设。

---

## 六、总结

### 6.1 必须修复的问题

| # | 问题 | 位置 | 严重度 | 修复建议 |
|---|------|------|--------|---------|
| 1 | Optional 返回 null | `InputGate.java:320,336,342,353,386` | **🔴 高** | 改为 `Optional.empty()` |
| 2 | 废弃 API 作为默认路径 | `WindowedStreamImpl.java:168` | **🟡 中** | 确保 `IWindowOperatorFactory` 注册，废弃回退路径 |
| 3 | 验证 DataStream API 窗口聚合实际走哪个路径 | DataStream API 行为 | **🟡 中** | 审计 `StreamComponents` 是否有 `WindowOperatorFactory` 注册 |

### 6.2 建议改进

| # | 改进 | 原因 |
|---|------|------|
| 1 | 修复 78 个通配符导入 | 代码规范 |
| 2 | 拆分 execution 包（26 文件→3 个包） | 降低认知负荷 |
| 3 | 消除 `state.backend.memory` 中重复的 `ShardPrefixedKey` | 消除冗余 |
| 4 | 为 `execution.flow/plan/transport` 补充测试 | 提升边缘路径覆盖 |
| 5 | 移除 runtime → cep 幽灵依赖 | 清理技术债 |
| 6 | 考虑放弃空壳模块或改为内部聚合 | 减少 build 噪音 |

### 6.3 测试缺口优先级

| 优先级 | 缺口 | 影响 |
|--------|------|------|
| **P0** | Operator State 端到端测试 | 阻碍 source exactly-once |
| **P1** | 真实跨 JVM 分布式 E2E 测试 | 验证分布式正确性 |
| **P1** | 分支/合并多链管线测试 | 验证复杂 DAG 正确性 |
| **P2** | `execution.flow/plan/transport` 单元测试 | 提升健壮性 |
| **P3** | `configuration` / `streamrecord` / `time` 单元测试 | 补齐基础覆盖 |
