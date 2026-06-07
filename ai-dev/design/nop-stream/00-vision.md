# nop-stream 高层设计原则

**日期**：2026-05-19（更新于 2026-06-06）
**范围**：`nop-stream` 子系统
**状态**：active

---

## 一、产品定位

nop-stream 是 Nop 平台的流处理引擎，定位为**声明式图模型驱动的可分布式执行引擎**。

核心模型是 **StreamModel**——可序列化的算子图及其组件注册表。它可由三种入口构造：
- **XDSL 声明式定义**（未来主路径）：直接描述 StreamGraph 拓扑、算子配置、分区策略
- **Java DataStream API**（当前 Builder）：编程方式构造 Transformation DAG，编译为 StreamModel
- **Delta 定制**：Nop 平台的可逆计算机制，在模型层叠加差量修改

三种入口最终生成同一套 canonical StreamModel，经过统一的五层执行管线编译执行。

## 二、成功标准

1. 用户可以仅通过 Java DataStream API 构建包含 source → window/aggregate → sink 的完整流处理管线，并正确执行
2. StreamModel 是唯一 canonical 入口——Java API、XDSL 和 Delta 三种路径生成同一类模型，运行时不区分入口来源
3. Checkpoint 可从任意 durable epoch 恢复，恢复后端到端 exactly-once 语义可独立验证
4. 分布式执行模式可通过 `DeploymentMode.DISTRIBUTED` + 多 TaskManager 实例运行，控制面/数据面/编排面三面分离
5. CEP 引擎（NFA + SharedBuffer）可独立使用，也可作为算子接入统一状态后端

## 三、不可违反的约束

| # | 约束 | 含义 |
|---|------|------|
| 1 | **图模型为核** | StreamModel（可序列化算子图）是系统核心。XDSL、DataStream API 和 Delta 都是它的构造路径，运行时只执行模型 |
| 2 | **模型优先** | 所有语义由可序列化模型决定。执行计划用可序列化、可对比、可 Delta 定制的 plan 表达分布式语义。状态恢复用稳定 ID 和 epoch manifest 路由 |
| 3 | **可替换后端** | 本地线程、远程进程、外部引擎都遵守同一语义契约。backend 通过 `StreamBackendCapability` 声明能力 |
| 4 | **语义不降级** | source 不可重放或 sink 不具备严格提交能力时，不允许声明 `STRICT_EXACTLY_ONCE`。语义等级必须在 runtime metrics 中暴露 |
| 5 | **稳定身份** | 所有持久状态必须有稳定 `operatorId`（用户显式 uid > 模型路径 > 结构 hash）。状态路径不能包含 `deploymentId`、`runId` 或 `attemptId` |
| 6 | **统一数据通道** | Record、Barrier、Watermark 三者通过同一数据管线传输。Barrier 不需要独立 RPC 通道 |
| 7 | **最小控制面** | 使用 Nop 的模型和租约抽象表达分布式控制，不引入 Flink 的 SlotSharingGroup、Netty 网络栈等结构 |
| 8 | **Nop 平台集成** | 使用 IJdbcTemplate、IBatchLoader、IMessageService、JsonTool 等平台基础设施，不绕过平台自建基础设施 |

## 四、显式 Non-Goals

本系统**不做**以下事情：

| Non-Goal | 理由 |
|----------|------|
| 双流 Join（interval join / window join / broadcast join） | 复杂度极高，用例有限。可通过 CEP 或外部 lookup 替代 |
| SQL API | Nop 平台已有 GraphQL，流式 SQL 需求不迫切 |
| 大规模并行（PB 级吞吐） | 分布式解决高可用和资源隔离。几十 GB 状态级别由远程 Redis/状态后端承载 |
| 复制 Flink Runtime 结构 | 不引入 SlotSharingGroup、Netty 网络栈、二进制序列化体系。学习 Flink 语义边界，但实现结构自主 |
| 动态并行度调整和状态重分布 | 当前阶段不需要。`stateShardCount` 改变需要显式 migration action |
| 异步算子 | 增加算子线程模型复杂度，当前阶段聚焦同步算子链 |

## 五、设计收敛路径

设计按以下顺序收敛，不可逆序：

1. **先定义核心模型**（StreamModel、StreamComponents、Transformation DAG）
2. **再定义编译管线**（StreamGraph → JobGraph → PartitionedPlan → DeploymentPlan）
3. **再定义执行引擎**（算子链、数据交换、分布式控制面）
4. **再补 Checkpoint、状态管理、窗口、时间模型等支撑子系统**
5. **最后实现连接器、CEP、XDSL 编排等上层能力**

只要这条顺序不乱，设计就不会滑入"先写 runtime 再补模型"的陷阱。

## 六、必须由人决策的决策点

以下决策不可由 AI 自行发明，必须经过显式确认：

1. `StreamModel` 的核心结构变更（如新增 Transformation 类型、修改 StreamComponents 注册表结构）
2. 定位变更（从"流处理引擎"改为其他定位）
3. 状态后端的变更（Memory → Redis → RocksDB）
4. Checkpoint 协议的变更（如从单 in-flight 扩展为多 in-flight）
5. 分布式模式的通信模型变更（IMessageService → 其他传输）
6. `stateShardCount` 默认值的变更

## 七、核心取舍

- **保留**：Barrier 快照、算子链化、多 Task 并行执行、窗口/CEP 语义
- **保留（非核心路径）**：DataStream API——作为 StreamModel 的编程构造器，但不是最终用户的主入口
- **去除**：复杂 Join、广播流、异步算子、完整 key-group 重分布
- **聚焦**：单流窗口聚合 + CEP 模式匹配 + Checkpoint 容错

## 八、设计不变量

以下不变量不可违反：

1. 所有持久状态必须有稳定 `operatorId`
2. 所有 keyed state 必须有确定性 `StateShard` 路由
3. `PartitionedPlan` 是 parallelism、edge partition、state route、checkpoint route 的唯一语义来源
4. Barrier 只能由 source 读取线程注入，并随数据 channel 传播
5. Epoch manifest durable 之前，sink transaction 不得 commit
6. 恢复必须从最新 durable epoch manifest 开始
7. Source 不可重放或 sink 不具备严格提交能力时，不允许声明 `STRICT_EXACTLY_ONCE`
8. 旧 attempt 和旧 coordinator 必须被 fencing
9. Timer state 是窗口和 CEP exactly-once 的必要状态
10. Delta 只能修改模型，不能 patch runtime object 来改变语义
11. 所有 `StreamModel` 必须包含 `StreamComponents` registry
12. 所有 `StreamRequirement` 必须在编译时和运行时校验
13. 所有 transactional operator 必须实现 `CheckpointParticipant`
14. 所有分布式 edge 必须配置 `EdgeConfig`
15. 所有作业终止必须明确 `JobTerminationMode`

## 九、核心隐喻

nop-stream 的运行方式：

1. **入口**：StreamModel 可由 XDSL、DataStream API 或 Delta 合成三种路径构造
2. **编译为 canonical StreamModel**（含 StreamComponents、StreamRequirement、fingerprint）
3. **五层执行管线**：StreamModel → StreamGraph → JobGraph → PartitionedPlan → DeploymentPlan → RuntimeTopology
4. **部署模式选择**：`DeploymentMode.LOCAL`（线程池）或 `DeploymentMode.DISTRIBUTED`（多 TaskManager 实例）
5. **数据通道**：StreamRecord、CheckpointBarrier、Watermark 三者通过统一的 `RecordWriter → ResultPartition → InputChannel → RecordReader` 管线传输
6. **分布式 exactly-once**：epoch checkpoint + barrier 对齐 + CheckpointParticipant + fencing

### 分布式执行架构

分布式模式采用三面分离：

| 面 | 职责 | 传输 |
|---|---|---|
| 控制面 | 作业调度、task 分配、cancel | `IStreamTaskRpcService` / `IStreamCoordinatorRpcService` |
| 数据面 | 记录传输、barrier/watermark 传播 | `IMessageService` + RemoteResultPartition / RemoteInputChannel |
| 编排面 | Invokable 安装、算子链配置 | 直接 Java 调用 |

---

## 十、拒绝了什么

| 方案 | 拒绝理由 |
|------|---------|
| 复制 Flink Runtime 三层调度（ExecutionGraph） | nop-stream 用 `IStreamExecutionDispatcher` SPI 替代，LOCAL 模式 JobGraph 直接生成 Task，DISTRIBUTED 模式由 SPI 分发 |
| 直接 `DataSource.getConnection()` 做 checkpoint 存储 | 违反 Nop 平台规范，不支持多数据库方言。必须通过 `IJdbcTemplate` + `IDialect` |
| 独立线程注入 Barrier | 与 source 读取线程竞争，破坏切点语义。Barrier 必须作为数据流元素在 source 读取线程中注入 |
| 自管 HashMap 做窗口状态 | 不参与平台 keyed state 生命周期管理（checkpoint、恢复、分片路由）。必须使用 namespace-based state |
| 为每个部分匹配独立复制 CEP 事件列表 | 内存开销随匹配路径数线性增长。使用 SharedBuffer + 引用计数 |
