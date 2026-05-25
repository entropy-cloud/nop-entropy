# Nop Stream 设计文档

> Status: active
> Created: 2026-05-19
> Updated: 2026-05-25（更新定位、阅读顺序、心智模型、设计原则）

## 定位

nop-stream 是 Nop 平台的流处理引擎，定位为**声明式图模型驱动的可分布式执行引擎**。

核心模型是 **StreamModel**（可序列化的算子图及其组件注册表），可由 XDSL 声明式定义、Java DataStream API 编程构造、或 Delta 定制合成。三种入口最终生成同一套 canonical StreamModel，经统一的五层执行管线（StreamModel → StreamGraph → JobGraph → PartitionedPlan → DeploymentPlan → RuntimeTopology）编译执行。

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

## 阅读顺序

1. `architecture.md` — 整体架构、模块结构、定位与设计目标、五层执行管线、数据流模型
2. `core-design.md` — **StreamModel/StreamComponents（核心模型入口）** → DataStream API（Builder）→ 算子模型 → 稳定身份
3. `graph-model-design.md` — 图模型转换、算子链优化、PartitionedPlan、DeploymentPlan
4. `checkpoint-design.md` — Epoch 协议、CheckpointParticipant、ProcessingGuarantee、故障恢复
5. `state-management-design.md` — StateShard、状态后端（对象接口层次）、序列化、内存预算
6. `window-design.md` — WindowingStrategy、窗口四要素、PaneState
7. `time-model-design.md` — 事件时间、Watermark 机制
8. `connector-design.md` — SourceWorkUnit、nop-batch 桥接、CDC
9. `cep-design.md` — CEP 引擎设计
10. `comparison.md` — 架构对比（Flink / NiFi / Node-RED / SeaTunnel）
11. `component-roadmap.md` — 开发路线和优先级

## 快速心智模型

nop-stream 的运行方式：

1. **入口**：StreamModel 可由 XDSL、DataStream API 或 Delta 合成三种路径构造
2. **编译为 canonical StreamModel**（含 StreamComponents、StreamRequirement、fingerprint）
3. **五层执行管线**：StreamModel → StreamGraph → JobGraph → PartitionedPlan → DeploymentPlan → RuntimeTopology
4. **部署模式选择**：`DeploymentMode.LOCAL`（线程池）或 `DeploymentMode.DISTRIBUTED`（多 TaskManager 实例），通过 `IStreamExecutionDispatcher` SPI 分发。分布式解决高可用和资源隔离，吞吐量由具体部署决定
5. **数据通道**：StreamRecord、CheckpointBarrier、Watermark 三者通过统一的 `RecordWriter → ResultPartition → InputChannel → RecordReader` 管线传输，对算子层透明
6. **分布式 exactly-once**：epoch checkpoint + barrier 对齐（走数据通道）+ CheckpointParticipant + fencing

### 分布式执行架构

分布式模式采用三面分离：

| 面 | 职责 | 传输 |
|---|---|---|
| 控制面 | 作业调度、task 分配、cancel | `IStreamTaskRpcService` / `IStreamCoordinatorRpcService` |
| 数据面 | 记录传输、barrier/watermark 传播 | `IMessageService` + RemoteResultPartition / RemoteInputChannel |
| 编排面 | Invokable 安装、算子链配置 | 直接 Java 调用 |

核心角色：`JobCoordinator`（持有 canonical plan，分发 subtask）、`TaskManager`（管理本节点 TaskExecutor）、`EmbeddedDistributedExecutor`（嵌入式模式下的编排器）。

## 设计原则

1. **图模型为核**：StreamModel（可序列化算子图）是系统核心，XDSL、DataStream API 和 Delta 都是它的构造路径
2. **模型优先**：所有语义由可序列化模型决定，运行时只执行模型，不重新发明拓扑语义
3. **可替换后端**：本地线程、远程进程、外部引擎都遵守同一语义契约
4. **可 Delta 定制**：三种入口最终落到同一套 canonical StreamModel，Delta 只作用于模型层
5. **对象级接口**：状态接口（IStateBackend → IKeyedStateBackend → IInternalStateBackend）操作 Java 对象而非二进制字节，序列化仅在持久化层内部发生
6. **统一数据通道**：Record、Barrier、Watermark 三者在同一数据管线传输，Barrier 不需要独立 RPC
7. **Nop 平台集成**：使用 IJdbcTemplate、IBatchLoader、IMessageService、JsonTool
