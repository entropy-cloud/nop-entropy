# Nop Stream 设计文档

> Status: active
> Created: 2026-05-19
> Updated: 2026-05-21

## 定位

nop-stream 是 Nop 平台的流处理引擎，定位为**可分布式执行的 Flink 简化版**。保留 Flink 的核心流处理语义（DataStream API、窗口、状态、Checkpoint），去除复杂 Join 等高复杂度特性。详见 `component-roadmap.md`。

本目录记录 nop-stream 的架构决策、模块边界、核心设计约束和使用契约。

## 设计文档结构

| 文档 | 职责 | 状态 |
|------|------|------|
| `architecture.md` | 整体架构：模块划分、执行模型、数据流、分层设计 | active |
| `component-roadmap.md` | 组件分解与开发路线：6 个核心组件 + 4 个规划组件，6 个开发阶段 | active |
| `core-design.md` | 核心引擎设计：DataStream API、算子模型、状态管理 | active |
| `window-design.md` | 窗口机制：WindowAssigner/Trigger/Evictor、WindowOperator 两条处理路径、聚合语义 | active |
| `time-model-design.md` | 时间模型：WatermarkStrategy、TimestampAssigner、WatermarkGenerator、传播机制 | active（已对接） |
| `state-management-design.md` | 状态管理：存储模型、序列化策略、内存控制、与 Flink 对比 | active |
| `graph-model-design.md` | 图模型与执行引擎：StreamGraph、JobGraph、算子链优化、TaskExecutor | active（单链 + 多链管线已对接） |
| `checkpoint-design.md` | Checkpoint 与 Exactly-Once：barrier 对齐、协调器生命周期、2PC Sink、恢复流程、多数据库存储设计 | active（已对接，含 Savepoint） |
| `cep-design.md` | CEP 引擎设计：NFA、Pattern DSL、SharedBuffer、事件匹配语义 | active |
| `comparison.md` | 简化分析：与 Flink / SeaTunnel 的架构对比，取舍决策 | active |
| `connector-design.md` | 连接器设计：基于 Nop 平台通用抽象（IRecordInput/Output、IMessageService、IEntityDao）的 Source/Sink 适配 | active（设计阶段） |
| `design-review.md` | 设计质量评估（2026-04-02，独立审查结论） | historical |

## 阅读顺序

1. `architecture.md` — 理解整体模块结构和执行模型
2. `component-roadmap.md` — 理解组件分解和开发路线
3. `core-design.md` — 理解 DataStream API、算子模型和状态管理
4. `window-design.md` — 理解窗口机制和 WindowOperator 的核心逻辑
5. `time-model-design.md` — 理解事件时间语义、Watermark 生成和传播机制
6. `state-management-design.md` — 理解状态存储、序列化策略和内存控制
7. `graph-model-design.md` — 理解图模型的两层转换和算子链优化机制
8. `checkpoint-design.md` — 理解 Checkpoint 机制和端到端 Exactly-Once 语义
9. `cep-design.md` — 理解 CEP 引擎的设计（当前最成熟的子模块）
10. `comparison.md` — 理解简化决策，以及与 Flink/SeaTunnel 的能力边界
11. `connector-design.md` — 理解如何利用 Nop 平台现有抽象构建 Source/Sink
12. `design-review.md` — 了解已知问题和改进方向（参考性质）

## 快速心智模型

nop-stream 的运行方式可以概括为：

1. **用户通过 DataStream API 构建 Transformation DAG**：`env.addSource(...)` → `.map(...)` → `.keyBy(...)` → `.timeWindow(...)` → `.aggregate(...)` → `.addSink(...)`
2. **`env.execute()` 快速路径**：将 DAG 折叠为线性算子链，单线程同步执行。适合简单场景。
3. **`env.executeWithGraphModel()` 图模型路径**：Transformation → StreamGraph → JobGraph → Task → TaskExecutor。支持算子链优化、多 Task 并行执行、checkpoint 集成。

## 设计原则

1. **保留核心语义**：保留 Flink 的 DataStream API 概念和流处理语义，去除分布式调度和复杂 Join 等高复杂度特性
2. **API 兼容**：DataStream API 命名和概念与 Flink 保持一致，降低学习成本，方便未来对接 Flink 后端
3. **可替换执行后端**：core 模块定义抽象接口，runtime 模块提供实现，未来可接入 Flink 或其他引擎
4. **CEP 独立可用**：CEP 模块可直接使用 NFA + SharedBuffer 进行模式匹配，不依赖完整的流处理管线
5. **Nop 平台集成**：使用 Nop 标准基础设施（IJdbcTemplate、IBatchLoader、IMessageService、JsonTool）

## 与其他 Nop 模块的关系

- nop-stream-core 不依赖 Spring/NopIoC，是纯 Java 库
- nop-stream-runtime 使用 Nop 的 IJdbcTemplate 做多数据库适配
- nop-stream-connector 通过 nop-batch 的 IBatchLoader/IBatchConsumer 桥接数据源
- CEP 模块可直接在业务代码中使用（如 FraudDetectionDemo）
- 未来规划：nop-stream-flow 通过 NopFlow 声明式编排流处理任务
- 未来规划：nop-stream-flink 将 core API 映射到 Flink DataStream API

## 已知限制

详见 `design-review.md` 的问题清单和 `component-roadmap.md` 的各组件待完善项。当前最关键的实现缺口：

- **WindowedStreamImpl 的 apply/aggregate/reduce 未实现**：需要 core 定义 WindowOperatorFactory 接口，runtime 提供实现
- **WindowOperator 聚合正确性**：累加器类型腐蚀、合并窗口映射丢失、getSimpleAccumulator 返回 null 等问题
- **JdbcCheckpointStorage 仅支持 MySQL**：需改为基于 IJdbcTemplate 的多数据库实现
- **Barrier 注入线程安全**：从外部线程注入 barrier 与 source 算子线程存在竞争
