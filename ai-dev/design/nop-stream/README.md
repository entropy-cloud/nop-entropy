# Nop Stream 设计文档

> Status: active
> Created: 2026-05-19

## 定位

nop-stream 是 Nop 平台的流处理引擎，定位为 Flink 的简化实现。目标场景是单流窗口操作、基于主记录 ID 的 hash join 查询关联记录等 ETL 处理，覆盖 SeaTunnel 等常用 ETL 软件的核心功能。

本目录记录 nop-stream 的架构决策、模块边界、核心设计约束和使用契约。

## 设计文档结构

| 文档 | 职责 | 状态 |
|------|------|------|
| `architecture.md` | 整体架构：模块划分、执行模型、数据流、分层设计 | active |
| `core-design.md` | 核心引擎设计：DataStream API、算子模型、状态管理 | active |
| `window-design.md` | 窗口机制：WindowAssigner/Trigger/Evictor、WindowOperator 两条处理路径、聚合语义 | active |
| `graph-model-design.md` | 图模型与执行引擎：StreamGraph、JobGraph、算子链优化、TaskExecutor | active（未对接） |
| `checkpoint-design.md` | Checkpoint 与 Exactly-Once：barrier 对齐、协调器生命周期、2PC Sink、恢复流程 | active |
| `cep-design.md` | CEP 引擎设计：NFA、Pattern DSL、SharedBuffer、事件匹配语义 | active |
| `comparison.md` | 简化分析：与 Flink / SeaTunnel 的架构对比，取舍决策 | active |
| `design-review.md` | 设计质量评估（2026-04-02，独立审查结论） | historical |

## 阅读顺序

1. `architecture.md` — 理解整体模块结构和执行模型
2. `core-design.md` — 理解 DataStream API、算子模型和状态管理
3. `window-design.md` — 理解窗口机制和 WindowOperator 的核心逻辑
4. `graph-model-design.md` — 理解图模型的两层转换和算子链优化机制
5. `checkpoint-design.md` — 理解 Checkpoint 机制和端到端 Exactly-Once 语义
6. `cep-design.md` — 理解 CEP 引擎的设计（当前最成熟的子模块）
7. `comparison.md` — 理解简化决策，以及与 Flink/SeaTunnel 的能力边界
8. `design-review.md` — 了解已知问题和改进方向（参考性质）

## 快速心智模型

nop-stream 的运行方式可以概括为 3 步：

1. **用户通过 DataStream API 构建 Transformation DAG**：`env.addSource(...)` → `.map(...)` → `.keyBy(...)` → `.timeWindow(...)` → `.aggregate(...)` → `.addSink(...)`
2. **`env.execute()` 将 DAG 折叠为线性算子链**：从 Sink 回溯到 Source，实例化算子，用 `ChainingOutput` 串联。数据通过推模型从 Source 流向 Sink。
3. **Source.run() 推送每条记录穿过链**：`processElement()` → `ChainingOutput.collect()` → 下一个算子的 `processElement()`。Source 结束后发送 `MAX_WATERMARK` 触发最终窗口计算。

**与 Flink 的核心区别**：没有 JobManager/TaskManager 分布式调度，没有 Netty RPC，没有 key-group 重分布。所有算子在同一线程中同步执行，每条记录立即被处理完毕。

## 设计原则

1. **简化优先**：去除 Flink 的分布式调度、RPC 通信、key-group 分区等分布式复杂性，保留"记录从 Source 流过算子链到 Sink"这一核心流处理语义
2. **API 兼容**：DataStream API 命名和概念与 Flink 保持一致，降低学习成本，方便未来对接 Flink 后端
3. **可替换执行后端**：core 模块定义抽象接口，runtime 模块提供实现，未来可接入 Flink 或其他引擎
4. **CEP 独立可用**：CEP 模块可直接使用 NFA + SharedBuffer 进行模式匹配，不依赖完整的流处理管线

## 与其他 Nop 模块的关系

- nop-stream-core 不依赖 Spring/NopIoC，是纯 Java 库
- CEP 模块可直接在业务代码中使用（如 FraudDetectionDemo）
- 未来规划：nop-stream-flow 通过 NopFlow 声明式编排流处理任务
- 未来规划：nop-stream-flink 将 core API 映射到 Flink DataStream API

## 已知限制

详见 `design-review.md` 的问题清单。当前最关键的架构限制及其行为影响：

- **单线程同步执行**：TaskExecutor/JobGraph 路径已实现但未对接到 `execute()`，所有算子在同一线程顺序处理，无法利用多核
- **keyBy 在当前执行路径中被跳过**：`PartitionTransformation` 在构建算子链时被移除，虽然 KeySelector 被提取并传给下游算子，但数据实际未按 key 分区。WindowOperator 内部通过 `keyedStateBackend.setCurrentKey()` 实现按 key 隔离的状态访问
- **状态管理无 key 隔离（CEP 路径）**：`CepOperator` 使用 `SimpleKeyedStateStore`（全局共享），多 key 场景数据混杂。`WindowOperator` 使用 `MemoryKeyedStateBackend`（按 key+namespace 隔离），不受此影响
- **事件时间语义不完整**：watermark 传播仅在 Source 结束后发送 MAX_WATERMARK，不支持持续的 watermark 推进。CEP 的 `currentWatermark()` 硬返回 `Long.MIN_VALUE`，`within(Duration)` 超时不会触发
