# Nop Stream 设计文档

> Status: active
> Created: 2026-05-19
> Updated: 2026-05-23

## 定位

nop-stream 是 Nop 平台的流处理引擎，定位为**可分布式执行的流处理内核**。基于声明式执行模型，提供确定性分区、可恢复状态、epoch checkpoint、source/sink 协议化 exactly-once，以及可由 Java API、XDSL 和 Delta 共同驱动的执行计划。

## 设计文档结构

| 文档 | 职责 |
|------|------|
| `architecture.md` | 整体架构：模块划分、五层执行管线、分布式控制面、数据流模型 |
| `core-design.md` | StreamModel、StreamComponents、StreamRequirement、DataStream API、算子模型、稳定身份 |
| `graph-model-design.md` | 图模型：StreamGraph、JobGraph、算子链优化、PartitionedPlan、DeploymentPlan |
| `checkpoint-design.md` | Epoch Checkpoint 协议、CheckpointParticipant、ProcessingGuarantee、Source/Sink Exactly-Once、JobTerminationMode、故障恢复 |
| `state-management-design.md` | StateShard、StatePath、状态后端、序列化、State Segment、Timer State、内存预算 |
| `connector-design.md` | SourceWorkUnit 协议（Restriction/DynamicSplit/DrainTruncate/WatermarkEstimator）、nop-batch 桥接、消息队列/CDC 适配 |
| `window-design.md` | WindowingStrategy、窗口四要素、AccumulationMode、PaneState、WindowCompatibilityCheck |
| `time-model-design.md` | WatermarkStrategy、TimestampAssigner、WatermarkGenerator、传播机制 |
| `cep-design.md` | NFA、Pattern DSL、SharedBuffer、事件匹配语义 |
| `component-roadmap.md` | 组件分解与开发路线 |
| `comparison.md` | 与 Flink / Beam / Hazelcast Jet 的架构对比 |
| `design-review.md` | 设计质量评估快照（非设计规范，仅供参考） |

## 阅读顺序

1. `architecture.md` — 整体架构、模块结构、执行管线、分布式控制面
2. `core-design.md` — StreamModel、StreamComponents、稳定身份、算子模型
3. `graph-model-design.md` — 图模型转换、算子链优化、PartitionedPlan
4. `checkpoint-design.md` — Epoch 协议、CheckpointParticipant、ProcessingGuarantee、终止模式
5. `state-management-design.md` — StateShard、状态后端、序列化、内存预算
6. `connector-design.md` — SourceWorkUnit、nop-batch 桥接、CDC
7. `window-design.md` — WindowingStrategy、窗口四要素、PaneState
8. `time-model-design.md` — 事件时间、Watermark 机制
9. `cep-design.md` — CEP 引擎设计
10. `component-roadmap.md` — 开发路线和优先级
11. `comparison.md` — 架构对比

## 快速心智模型

nop-stream 的运行方式：

1. **用户通过 DataStream API 构建 Transformation DAG**
2. **编译为 canonical StreamModel**（含 StreamComponents、StreamRequirement）
3. **五层执行管线**：StreamModel → StreamGraph → JobGraph → PartitionedPlan → DeploymentPlan → RuntimeTopology
4. **分布式 exactly-once**：epoch checkpoint + barrier 对齐 + CheckpointParticipant + fencing

## 设计原则

1. **模型优先**：所有语义由可序列化模型决定，运行时只执行模型
2. **可替换后端**：本地线程、远程进程、外部引擎都遵守同一语义契约
3. **可 Delta 定制**：Java API、XDSL、Delta 都落到同一套 canonical StreamModel
4. **Nop 平台集成**：使用 IJdbcTemplate、IBatchLoader、IMessageService、JsonTool
