# Nop Stream 设计文档

> Status: active
> Created: 2026-05-19
> Updated: 2026-06-06（按 AGE owner-doc 模式重组）

本目录按 AGE（Attractor-Guided Engineering）owner-doc 模式组织，从高层设计原则到分项设计逐层展开：

1. **愿景层** — 定位、成功标准、约束、non-goals、设计不变量
2. **架构基线层** — 模块划分、分层设计、执行模型、分布式控制面、数据流模型
3. **核心模型层** — StreamModel、DataStream API、算子模型、稳定身份
4. **图模型与执行层** — StreamGraph、JobGraph、算子链化、Task/TaskExecutor
5. **容错层** — Checkpoint、Epoch 协议、Exactly-Once、恢复
6. **状态与时间层** — 状态管理、窗口机制、时间模型
7. **集成层** — 连接器、CEP 引擎
8. **参考层** — 架构对比、组件路线

---

## 愿景层

- `00-vision.md`
  - 产品定位、成功标准、不可违反的约束、显式 non-goals、设计收敛路径、必须由人决策的决策点、核心取舍、设计不变量、拒绝了什么

## 架构基线层

- `01-architecture-baseline.md`
  - 模块划分与依赖方向、七层分层设计、五层执行管线、分布式控制面（三面架构）、数据流模型、与 Nop 平台的集成

## 核心模型层

- `core-design.md`
  - StreamModel、StreamComponents、StreamRequirement、StreamBackendCapability、StreamModelFingerprint
  - DataStream API（Builder）、Transformation DAG
  - 算子模型、算子生命周期、Output 机制、算子链化
  - 稳定身份体系（operatorId、taskId、StateShard、TaskLocation）
  - 函数接口

## 图模型与执行层

- `graph-model-design.md`
  - 三层转换管线（Transformation → StreamGraph → JobGraph → Task）
  - 算子链识别算法、链化条件
  - StreamGraph / JobGraph 数据结构
  - Task / SubtaskTask / TaskExecutor 运行时执行
  - 执行路径统一（图模型为唯一路径）
  - 与 Flink 的差异

## 容错层

- `checkpoint-design.md`
  - Epoch Checkpoint 协议（生命周期、Barrier 注入/对齐、Snapshot、Manifest）
  - CheckpointParticipant（泛化事务参与）
  - ProcessingGuarantee（四种保证级别）
  - Source / Sink Exactly-Once 协议
  - JobTerminationMode
  - 故障恢复模型（fencing、Coordinator HA、恢复兼容性）
  - Serializer Fingerprint 策略
  - 可观测性契约

## 状态与时间层

- `state-management-design.md`
  - 状态接口层次（ValueState/MapState/AppendingState/ListState）
  - StateShard（确定性分片路由）、StatePath（持久化路径）
  - 状态后端（IStateBackend → IKeyedStateBackend → IInternalStateBackend）
  - 序列化策略、State Segment、Timer State、内存预算

- `window-design.md`
  - 窗口四要素（WindowAssigner + Trigger + Evictor + WindowFunction）
  - WindowingStrategy（可序列化模型）
  - 统一算子架构（单一 WindowOperator）
  - InternalWindowFunction 适配层、WindowOperatorBuilder
  - PaneState、WindowCompatibilityCheck
  - 合并窗口处理流程

- `time-model-design.md`
  - WatermarkStrategy、TimestampAssigner、WatermarkGenerator
  - Watermark 生成策略（Ascending / BoundedOutOfOrderness）
  - Watermark 传播机制
  - TimestampsAndWatermarksOperator

## 集成层

- `connector-design.md`
  - nop-batch 桥接（BatchLoaderSourceFunction / BatchConsumerSinkFunction）
  - SourceWorkUnit 协议（RestrictionTracker、DynamicSplit、DrainTruncate、WatermarkEstimator）
  - Split Assignment Recovery 协议
  - 消息队列与 CDC 适配

- `cep-design.md`
  - Pattern DSL、NFA 编译与匹配
  - SharedBuffer（引用计数 + Dewey 编号）
  - CepOperator、匹配后策略
  - 声明式模型（XMeta）

## 参考层

- `comparison.md`
  - 与 Flink / SeaTunnel / NiFi / Node-RED / StreamSets 的架构对比

- `component-roadmap.md`
  - 组件分解（C1–C8 + D1–D9 + P1–P4）
  - 开发方法（审计-规划-执行循环）
  - 已知技术债

---

## 阅读顺序

**必读路径**（理解定位 → 架构 → 核心模型）：

1. `00-vision.md` — 设计原则、约束、non-goals
2. `01-architecture-baseline.md` — 架构基线、模块划分、执行管线
3. `core-design.md` — StreamModel、DataStream API、算子模型

**按需深入**：

4. `graph-model-design.md` — 图模型转换、算子链化、执行路径
5. `checkpoint-design.md` — Checkpoint 协议、Exactly-Once
6. `state-management-design.md` — 状态后端、StateShard、序列化
7. `window-design.md` — 窗口机制、Trigger、Evictor
8. `time-model-design.md` — Watermark、时间戳分配
9. `connector-design.md` — 连接器适配
10. `cep-design.md` — CEP 引擎

**扩展方向**：

11. `comparison.md` — 架构对比（Flink / SeaTunnel / NiFi）
12. `component-roadmap.md` — 组件路线和开发方法
