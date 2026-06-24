# nop-stream 完善路线图

> Status: active
> Created: 2026-06-22
> Parent: `00-vision.md`（定位与约束）、`01-architecture-baseline.md`（架构基线）
> See also: `component-roadmap.md`（动态滚动视图）、`checkpoint-design.md` §13（容错契约）

---

## 一、定位与范围

本路线图的目标是**把 nop-stream 从「设计成熟、实现部分兑现」的状态推进到「完整分布式生产级」**，对齐 `00-vision.md` 定义的全部成功标准，并补齐深度设计分析中识别出的实现缺口。

### 与 `component-roadmap.md` 的分工

| 文档 | 视角 | 时间跨度 | 用途 |
|------|------|----------|------|
| `component-roadmap.md` | 动态滚动视图 | 当前一个可交付单元 | 审计→规划→执行循环，每次只规划最紧迫的工作 |
| **本文档** | 全局战略视图 | 完整补齐全路径 | 阶段划分、依赖关系、go/no-go 决策点，指导 component-roadmap 选择下一个工作单元 |

两份文档互补：本文档定义「要走到哪、按什么顺序」，component-roadmap 定义「当下做哪一块、怎么验收」。本文档的 Phase 划分不预设具体 plan 编号，由 component-roadmap 在每个 Phase 内动态选择。

### 不在本文档范围内

- 具体代码实现（看源码）
- 单个 plan 的执行步骤（看 `ai-dev/plans/`）
- 已完成工作的演进叙事（看 `ai-dev/logs/`）

---

## 二、核心策略

### 1. 定位确认

目标定位为 `00-vision.md` §一「可分布式执行引擎」的完整兑现——**完整分布式生产级**，但**坚持 vision §四的 Non-Goals**（不做双流 Join、SQL、大规模集群、异步算子）。

关键约束：**分布式能力通过复用 Nop 平台基础设施实现，不自建网络栈与 HA 算法**。这一约束已被 `01-architecture-baseline.md` §五「三面分离」和 §七「与 Nop 平台的集成」确立，本路线图严格执行。

### 2. 分阶段原则

| 原则 | 含义 |
|------|------|
| **正确性优先于能力扩展** | 与既定 exactly-once 目标直接冲突的缺口（Operator State、CEP 状态接入）先补，再扩展新能力 |
| **先 Build 后 Wire** | 自建流处理专有逻辑（状态后端、unaligned、operator state）先于平台基础设施接入，因为前者是后者的语义基础 |
| **先单进程后分布式** | 状态机、后端、checkpoint 在单进程内完全稳固后再上分布式，避免分布式问题与单进程 bug 混淆 |
| **每阶段可独立交付** | 每个 Phase 结束都有可验证的能力增量，不依赖未来 Phase 才能体现价值 |
| **WIRE 优先于 BUILD** | 平台已提供的能力（leader 选举、RPC、discovery、分布式锁）一律接入而非重造 |

### 3. 阶段划分总览

```
Phase 0  正确性缺口补齐与文档收敛
         (Operator State、CEP 接入、StreamModel 做实、容错契约补全)
    │
    ▼
Phase 1  状态后端生产化
         (RocksDB、增量 checkpoint、State TTL、状态迁移)
    │
    ▼
Phase 2  弹性与重分布
         (StateShard → Key-Group、savepoint rescale)
    │
    ▼
Phase 3  分布式接入平台基础设施
         (nop-cluster leader 选举、Nop RPC 跨 JVM、IMessageService 跨 JVM、
          discovery/lease、多 JVM 测试基建)
    │
    ▼
Phase 4  容错强化
         (channel 心跳、unaligned checkpoint、Coordinator HA 端到端、
          region failover、多并发 checkpoint)
    │
    ▼
Phase 5  生态与上层
         (Source split 体系、XDSL 声明式编排、Delta、Flink 后端、Kafka transport)
```

阶段顺序的依赖关系是硬约束：Phase 1 依赖 Phase 0 的状态后端接口稳固；Phase 2 依赖 Phase 1 的 RocksDB（key-group 与 SST 文件耦合）；Phase 3 依赖 Phase 0-2 的单进程语义完整；Phase 4 依赖 Phase 3 的跨 JVM 通道。

---

## 三、设计决策

### D1：为什么分布式一律复用平台，不自建

**选了什么**：网络传输（控制面 + 数据面）、leader 选举、lease/心跳、discovery、分布式锁全部通过接入 `nop-cluster` / `nop-rpc` / `nop-sys-dao` / `nop-commons` 的现有接口实现。

**为什么**：
- `nop-cluster` 已提供 `ILeaderElector` + `LeaderEpoch`（epoch + lease，正是 fencing token 所需原语）、`IDiscoveryClient`、`INamingService`、`MasterServerChooser`（route-to-leader 模式）、`IPartitionAssigner`
- `nop-rpc` 已提供 `IRpcService` 统一抽象、`RpcServiceProxyFactoryBean`（为任意 Java 接口生成远程代理）、`MessageRpcServer`/`SimpleRpcServer`/`ClusterRpcClient` 多种传输
- `IMessageService` 有跨 JVM 实现（`SysDaoMessageService` DB-backed、`PulsarMessageService`），`RemoteResultPartition`/`RemoteInputChannel` 已正确抽象且每条消息带 fencing token + epochId
- `nop-sys-dao` 的 `SysDaoLeaderElector`（JDBC）、`SysDaoResourceLockManager` 已实现完整 lease/renew/takeover 逻辑
- 流处理专有的分布式逻辑（fencing、barrier 对齐、epoch 恢复）nop-stream 已自己实现，且已对正确抽象编程

**拒绝了什么**：
- 引入 Netty 自建网络栈——违反 `00-vision.md` 约束 7「最小控制面」，且 `IMessageService` 已是数据面的正确抽象层
- 自建 leader 选举算法（Raft/Zab）——平台已有 lease-based 选举，且 nop-stream 不需要强一致共识，只需 coordinator 单点 lease
- 为「性能」绕过平台 RPC 自建二进制协议——平台 RPC 已支持 `IMessageService` 多种后端，性能由后端选择决定（Pulsar > DB）

**已知风险**：nop-stream 将是首个把 `SysDaoLeaderElector` 接入生产的模块（当前仅自测使用）。集成需趟平工作坑，但无需发明分布式算法。此风险记入 §五风险表 R3。

### D2：为什么 Phase 0 先补 Operator State 而非 RocksDB

**选了什么**：Phase 0 第一优先级是 Operator State 体系（`CheckpointedFunction` + `OperatorStateStore` + SPLIT_DISTRIBUTE/UNION/BROADCAST 三种重分布模式）。

**为什么**：
- Operator State 是 source exactly-once 的必要前提——任何带 offset 的 source（Kafka/Pulsar/文件游标/CDC）必须持久化消费位置，且恢复时按 split 重新分配
- 这是「正确性缺口」而非「能力扩展」——`checkpoint-design.md` §5 定义的 Source Offset Cut、Source Split 协议依赖 operator state 才能落地
- 工程量远小于 RocksDB（约 250 行核心逻辑 + 重分布恢复），性价比最高
- 不补 operator state，Phase 3 的分布式 source 无法做 exactly-once，整个分布式目标落空

**拒绝了什么**：
- 把 source offset 塞进 keyed state（用 fake key）——`checkpoint-design.md` §5.2 的 split owner 在恢复时可变，keyed state 的 key→shard 映射无法表达「split 集合重新分配给不同 subtask」的语义
- 推迟到 Phase 3 分布式接入时再补——那时 source 协议已成型，补 operator state 会引发 source 接口的破坏性重设计

### D3：为什么 Phase 2 升级到 Key-Group 而非保留 StateShard

**选了什么**：把当前固定不可变的 `StateShard` 升级为 Flink 风格的 Key-Group 模型（`maxParallelism` 上界，key→group 映射只依赖 maxParallelism，改变 parallelism 时 group 范围重算，状态按交集局部恢复）。

**为什么**：
- `00-vision.md` §四把「动态并行度调整」列为 Non-Goal，但「savepoint 改并行度恢复」是生产级作业的基本运维需求（垂直扩缩容、A/B 部署），与「运行时动态调整」是两回事
- 当前 `StateShard` 固定不可变意味着任何并行度变化都要丢弃状态，使 savepoint 的价值大幅缩水
- Key-Group 是 Flink 经工业验证的核心贡献之一，实现成本可控（key→group 映射 + range 交集恢复）
- 与 Phase 1 的 RocksDB 天然耦合（RocksDB key 带 key-group 前缀，SST 文件按 key-group 感知，使增量 rescale-restore 成为可能）

**拒绝了什么**：
- 保留 StateShard 并显式声明「savepoint 不支持改并行度」——这把引擎锁定在固定拓扑，与「生产级」定位冲突
- 完整复制 Flink 的 rescale 全部优化（`overlapFractionThreshold`、`useIngestDbRestoreMode` 等）——首版只做基础 range 交集恢复，高级优化作为 Phase 4+ 的可选增强

### D4：为什么 unaligned checkpoint 排在 Phase 4 而非更早

**选了什么**：Unaligned checkpoint（背压逃生）放在 Phase 4，在分布式接入（Phase 3）完成之后。

**为什么**：
- unaligned 的核心前置是 channel state 持久化与 priority event，二者都依赖跨 JVM 通道（Phase 3 提供）才能端到端验证
- aligned checkpoint 在「单进程 + 中低背压」场景下工作正常（Phase 0-2 的范围内），不是早期阻塞项
- `checkpoint-design.md` §13.3 已提供 `EFFECTIVELY_ONCE` 模式（barrier 不阻塞 + sink 两阶段提交）作为 unaligned 落地前的缓解选项
- unaligned 与 rescale 的交互（in-flight data 跨新并行度重映射）是 Phase 2 key-group 之上的复杂叠加，必须在 Phase 2 稳固后处理

**拒绝了什么**：
- 把 unaligned 提前到 Phase 1——那时跨 JVM 通道、channel state 抽象都未就绪，强行实现会产出无法验证的代码

---

## 四、阶段定义

每个 Phase 给出：目标、工作项（标注 **BUILD** 自建 / **WIRE** 接入平台 / **FIX** 修复 / **DOC** 文档）、前置依赖、验收标准（exit criteria）、go/no-go 决策点。

### Phase 0：正确性缺口补齐与文档收敛

**目标**：消除「设计宣称 vs 实现现实」的落差，补齐与既定 exactly-once 目标直接冲突的缺口。本 Phase 不引入新定位能力，只让现有设计真正落地。

**工作项**：

| 项 | 类型 | 内容 | 关联设计 |
|----|------|------|----------|
| 0.1 | DOC | 收敛五层执行管线文档：要么把 `PartitionedPlan`/`DeploymentPlan` 做实（承载并行展开、分区策略、状态路由的真实语义），要么承认 `GraphExecutionPlan` 为正式执行层并修订 `01-architecture-baseline.md` §四与 `graph-model-design.md`。消除「文档五层、实际干活的是第六层」的认知负担 | `01-architecture-baseline.md` §四 |
| 0.2 | FIX | `StreamModel` 做实：`buildStreamModel()` 必须真正注册 StreamComponents（transforms/streams/windowingStrategies、requirements、checkpointParticipants），使 fingerprint 与 requirement 校验在编译期真正生效；否则降级 README/vision 中「三入口同一 canonical 模型」的表述，使文档与现实一致 | `core-design.md` §1 |
| 0.3 | **BUILD** | **Operator State 体系**：`CheckpointedFunction` 接口、`OperatorStateStore`、`OperatorStateDescriptor`、三种重分布模式（SPLIT_DISTRIBUTE round-robin、UNION 全量广播、BROADCAST）、operator state 快照与恢复。这是 source exactly-once 的语义基础 | 新建 operator state 专题设计文档 |
| 0.4 | FIX | CEP 接入统一状态后端：移除 `CepOperator` 自建的 `SimpleKeyedStateStore`，改用标准 `IKeyedStateBackend`，使 CEP 状态参与 checkpoint/恢复。兑现 `01-architecture-baseline.md` §二「cep 通过标准 state/timer 接口接入」的模块边界 | `cep-design.md` |
| 0.5 | FIX | 启用 `BarrierAligner`（已实现未启用），替换 `InputGate` 内联对齐逻辑，统一多输入对齐实现。同步修复 `findCompletedCheckpointId` 的 O(输入数×待完成数) 复杂度 | `checkpoint-design.md` §13.2「多输入对齐统一」 |
| 0.6 | **BUILD** | abort 控制通道：独立于数据流的控制通道传播 cancel 信号。local 路径用直接方法调用，distributed 路径复用 `IStreamTaskRpcService.cancelTask`（Phase 3 提供跨 JVM）。解决「对齐等待时数据队列读不到 marker」问题 | `checkpoint-design.md` §13.2「abort 传播通道」 |
| 0.7 | FIX | 端到端并行度 > 1 通过 DataStream API 验证：`KeySelectorPartitioner` 多并行度 key hash 分区、多 sink/union 管线编译、`assignTimestampsAndWatermarks` 自动插入。当前 infra 存在但未端到端打通 | `graph-model-design.md` §10 |
| 0.8 | FIX | 移除 runtime → cep 幽灵依赖；清理 `GraphModelCheckpointExecutor` 代码重复；统一 `Task` 与 `Invokable` 的 OperatorChain 生命周期管理（`SubtaskTask.cancel` 状态机规范化） | `component-roadmap.md` §5 |

**前置依赖**：无（本 Phase 是后续所有 Phase 的语义基础）。

**验收标准**：
- `./mvnw clean install -pl nop-stream -am -T 1C` 全量通过
- 新增 Operator State E2E 测试：带 offset 的 source checkpoint 后 kill，恢复不丢不重
- CEP 状态 checkpoint/恢复 E2E 测试通过（`TestCepOperatorStateRecovery` 扩展为真恢复）
- 并行度 = 4 的 keyBy + window 管线端到端正确性测试通过
- `StreamModelFingerprint` 在编译期真正拒绝不兼容的 requirements 组合
- 文档审计：`01-architecture-baseline.md` §四描述的层与代码一致；README 与实现一致

**go/no-go**：Phase 1 启动前必须确认 Operator State 已稳定，因为 Phase 1 的 RocksDB 要同时承载 keyed state 和 operator state。

### Phase 1：状态后端生产化

**目标**：突破「内存流处理器」上限，支持状态大小超过堆内存，checkpoint 耗时与状态大小解耦。

**工作项**：

| 项 | 类型 | 内容 | 关联设计 |
|----|------|------|----------|
| 1.1 | **BUILD** | `RocksDBStateBackend` + `RocksDBKeyedStateBackend`：嵌入 LSM 树，所有 keyed state（Value/Map/List/Reducing/Aggregating）落到 RocksDB 列族。State 后端接口已为此外预留扩展点（`state-management-design.md` §3） | `state-management-design.md` |
| 1.2 | **BUILD** | 增量 checkpoint：基于 RocksDB native checkpoint，SST 文件内容寻址 + `SharedStateRegistry` 引用计数，多个 checkpoint 共享同一 SST 文件，subsumption 时清理。这是 Flink 最关键的产能特性——checkpoint 耗时从 O(状态总大小) 降到 O(增量) | 新建 RocksDB 状态后端专题设计文档 |
| 1.3 | **BUILD** | State TTL：keyed state 的生存时间，过期自动清理（RocksDB compaction filter + 内存后端的惰性清理） | `state-management-design.md` |
| 1.4 | WIRE | 状态迁移接线：`SerializerFingerprint` 比对 + `StateMigrationFunction` 注册与触发。设计已在 `checkpoint-design.md` §8.4.1 定义，实现需接线 | `checkpoint-design.md` §8.4.1 |
| 1.5 | DOC | 大状态（GB+）性能基准与调优指南：write buffer、block cache、compaction 配置；状态大小与 checkpoint 耗时的关系曲线 | — |

**前置依赖**：Phase 0 完成（状态后端接口稳固，operator state 体系就位）。

**验收标准**：
- 10 GB keyed state 在 RocksDB 后端下稳定运行，单次增量 checkpoint < 5s
- 状态从 Memory 后端 savepoint，能在 RocksDB 后端 restore（后端可切换）
- State TTL 触发后状态大小符合预期回落
- 状态迁移：value 类型 Integer → Long 显式迁移 action 成功；未声明 migration 的不兼容变更被拒绝
- 与 Phase 0 operator state 集成：operator state 也在 RocksDB 持久化

**go/no-go**：Phase 2 启动前必须确认 RocksDB key 编码可扩展为 key-group 前缀（key-group 与 SST 文件耦合是 Phase 2 增量 rescale 的基础）。

### Phase 2：弹性与重分布

**目标**：支持 savepoint 改并行度恢复、rescale 操作，摆脱「固定拓扑」限制。

**工作项**：

| 项 | 类型 | 内容 | 关联设计 |
|----|------|------|----------|
| 2.1 | **BUILD** | Key-Group 模型：`maxParallelism`（默认 128，上界 32768），key→group 映射 `murmurHash(key) % maxParallelism`，group→operator 范围公式。替代当前 `StateShard` 固定路由 | `core-design.md` §4.4、`state-management-design.md` |
| 2.2 | **BUILD** | KeyGroupRange 恢复：rescale 时新 subtask 读取自己 range 与各旧 subtask range 的交集，并行局部恢复，无中心 reshuffle | — |
| 2.3 | **BUILD** | Operator State 重分布：SPLIT_DISTRIBUTE round-robin、UNION 全量、BROADCAST 全量三种模式的 rescale 恢复（Phase 0 已实现单并行度，本项扩展到改并行度） | Phase 0 operator-state-design |
| 2.4 | **BUILD** | RocksDB key-group 感知的增量 rescale restore：SST 文件按 key-group 范围读取（依赖 Phase 1.2 的 SST 内容寻址） | Phase 1 rocksdb-state-design |
| 2.5 | FIX | `00-vision.md` §四的 Non-Goal「动态并行度调整」边界澄清：明确区分「运行时动态 rescale」（仍 Non-Goal）与「savepoint 改并行度恢复」（本 Phase 支持，已升级为目标） | `00-vision.md` §四 |

**前置依赖**：Phase 1 完成（RocksDB + 增量 checkpoint）。

**验收标准**：
- parallelism=4 的作业 savepoint，parallelism=16 restore，状态正确重分布
- parallelism=16 的作业 savepoint，parallelism=4 restore，状态正确合并
- operator state（如 source offset 列表）rescale 后正确重新分配给新 subtask 集合
- rescale restore 耗时与「全量重算」相比显著降低（局部交集恢复）
- `00-vision.md` 的 Non-Goal 表更新，反映 Key-Group 已纳入目标

**go/no-go**：Phase 3 启动前必须确认 Key-Group 路由在单进程多 subtask 下完全正确，因为分布式只是把 subtask 散到多 JVM，路由逻辑不变。

### Phase 3：分布式接入平台基础设施

**目标**：把「嵌入式分布式（同 JVM 模拟）」变为「真实分布式（跨 JVM）」，全部通过 WIRE 平台既有能力实现。

**工作项**：

| 项 | 类型 | 内容 | 平台依赖 |
|----|------|------|----------|
| 3.1 | WIRE | `JobCoordinator` 用 `ILeaderElector` 做 coordinator HA：`becomeLeader` 启动协调器，`becomeFollower` 转待命，`LeaderEpoch.epoch` 作为 fencing token 上界。**nop-stream 是首个生产集成者** | `nop-cluster` `ILeaderElector`、`nop-sys-dao` `SysDaoLeaderElector` |
| 3.2 | WIRE | 控制面 RPC 跨 JVM：`TaskManager` 通过 `MessageRpcServer`/`SimpleRpcServer` 暴露 `IStreamTaskRpcService`；`JobCoordinator` 通过 `RpcServiceProxyFactoryBean` + `ClusterRpcClient` 获取远程 `IStreamTaskRpcService` 代理。替换 `EmbeddedDistributedExecutor` 的直接引用 map | `nop-rpc` 全套 |
| 3.3 | WIRE | 数据面 `IMessageService` 跨 JVM：`RemoteResultPartition`/`RemoteInputChannel` 注入 `SysDaoMessageService`（DB-backed，零基建）或 `PulsarMessageService`（吞吐）。当前抽象已正确，每条消息带 fencing token + epochId | `nop-message` `SysDaoMessageService`/`PulsarMessageService` |
| 3.4 | WIRE | `ClusterRegistry` 收敛到平台：用 `IDiscoveryClient` + `INamingService` + `AutoRegistration` 替代当前自建的 `JdbcClusterRegistry`，或保留 JdbcClusterRegistry 但与 platform discovery 对接。决策点：是否完全替换 | `nop-cluster` discovery |
| 3.5 | FIX | fencing token 与 `LeaderEpoch.epoch` 统一：当前 stream 用 UUID fencing token，平台用单调递增 epoch。统一为单调 epoch（保留 UUID 仅作诊断），使 coordinator 切换与 task fencing 共用同一单调序 | — |
| 3.6 | **BUILD** | 多 JVM 集成测试基建：进程编排（启动 N 个 TaskManager JVM + 1 个 JobCoordinator JVM）、端口/消息主题分配、日志聚合、进程级 kill 与重启。这是验证真实分布式的必备基建，Flink 同等物是 `flink-dist` + `flink-runtime-web` | 新建测试基建 |
| 3.7 | FIX | abort 控制通道 distributed 部分：`IStreamTaskRpcService.cancelTask` RPC 接线（Phase 0.6 的 local 部分已完成），`JobCoordinator` 注册 abort listener 调用 cancelTask | Phase 0.6 |
| 3.8 | DOC | 分布式部署指南：进程启动参数、消息主题命名、leader elector 后端选择（JDBC vs Nacos vs Zookeeper，若平台后续提供）、生产配置模板 | — |

**前置依赖**：Phase 0、1、2 完成（单进程语义完整，状态后端生产化，弹性就位）。

**验收标准**：
- 2 个 TaskManager JVM + 1 个 JobCoordinator JVM，跨进程端到端 exactly-once 管线运行
- kill 任一 TaskManager 进程，coordinator 检测 lease 过期 → 触发 `globalRecovery` → 新 attempt 从 checkpoint 恢复 → 作业继续，不丢不重
- kill JobCoordinator 进程，`SysDaoLeaderElector` 选出新 leader → 新 coordinator 接管 → 旧 coordinator 被 fencing → 作业继续
- 数据面用 SysDaoMessageService 和 PulsarMessageService 两种后端各验证一次
- 集成测试基建可重复运行，CI 集成

**go/no-go**：Phase 4 启动前必须确认跨 JVM 通道稳定，因为 unaligned 的 channel state 持久化依赖此通道。同时评估 Phase 3 暴露的平台集成问题（作为首个 `SysDaoLeaderElector` 生产用户），必要时回传平台团队修复。

### Phase 4：容错强化

**目标**：填补 `checkpoint-design.md` §13.2 全部容错契约，使分布式 exactly-once 在生产故障场景下完全可用。

**工作项**：

| 项 | 类型 | 内容 | 关联设计 |
|----|------|------|----------|
| 4.1 | **BUILD** | `RemoteInputChannel` 心跳：channel 级心跳/超时检测，不止靠粗粒度 lease 兜底。网络分区时更快发现故障 channel | `checkpoint-design.md` §13.2「channel 心跳」 |
| 4.2 | **BUILD** | Unaligned checkpoint：channel state 持久化（in-flight input buffer + unflushed output buffer）、priority event（barrier 抢占式传播）、aligned→unaligned 超时回退。解决背压下 aligned checkpoint hang 问题 | `checkpoint-design.md` §13.2「背压逃生」、FLIP-76 |
| 4.3 | FIX | Coordinator HA 端到端：Phase 3 已接入 leader elector，本项补完整的 HA 测试矩阵（leader 切换、双 leader 脑裂、旧 leader fencing 验证、commit uncertainty 处理） | `checkpoint-design.md` §8.2、§8.3 |
| 4.4 | **BUILD** | Region-based failover：替代当前全局恢复，缩小故障爆炸半径。pipelined region 识别 + 区域级 task 重启。对大作业（>dozens vertex 或 GB+ 状态）必需 | `checkpoint-design.md` §8.1 |
| 4.5 | **BUILD** | 多并发 checkpoint：解开 `maxConcurrentCheckpoints=1` 硬上限。`CheckpointBarrierTracker` + `InputGate`（或 Phase 0.5 启用的 `BarrierAligner`）支持多 epoch 同时追踪。提升 checkpoint 吞吐 | `checkpoint-design.md` §2.8 |
| 4.6 | **BUILD** | Unaligned + rescale 交互：in-flight data 跨新并行度重映射（`InflightDataRescalingDescriptor` 等价物）。unaligned checkpoint 与 Phase 2 rescale 的复杂叠加 | Phase 2 + 4.2 |

**前置依赖**：Phase 3 完成（跨 JVM 通道、HA 接入就位）。

**验收标准**：
- 持续背压下 unaligned checkpoint 正常完成（aligned 模式下会 hang 的场景）
- 1000 vertex 作业中单 task 失败，region failover 只重启该 region（< 10 vertex），而非全局恢复
- 多并发 checkpoint（maxConcurrent=3）正常工作，互不干扰
- Coordinator HA 测试矩阵全部通过，包含脑裂场景
- `checkpoint-design.md` §13.2 容错契约表全部 ✅

**go/no-go**：Phase 5 是功能扩展而非正确性补齐，可在 Phase 4 部分完成后并行启动。

### Phase 5：生态与上层

**目标**：补齐连接器生态、声明式入口、跨后端适配，兑现 `00-vision.md` 全部「主入口」承诺。

**工作项**：

| 项 | 类型 | 内容 | 关联设计 |
|----|------|------|----------|
| 5.1 | **BUILD** | Kafka `IMessageService`：`nop-message-kafka` 当前是空模块。若 Kafka 是数据面硬需求，按 `PulsarMessageService` 形态实现 | `nop-message-kafka` |
| 5.2 | **BUILD** | Source split 体系（FLIP-27 风格）：`SourceEnumerator`（动态 split 发现与分配）+ `SourceReader`（split-based 消费）。当前是 Deferred，分布式 source exactly-once 的完整形态需要它 | `connector-design.md` §3、`checkpoint-design.md` §5 |
| 5.3 | **BUILD** | `nop-stream-flow`：XDSL 声明式 StreamModel 编排。真正兑现「三入口同一 canonical 模型」承诺（当前只有 DataStream API 一入口） | `00-vision.md` §一、`core-design.md` §1 |
| 5.4 | WIRE | Delta 定制 StreamModel：可逆计算机制接入，在模型层叠加差量修改。依赖 5.3 的 XDSL 入口 | `00-vision.md` §一、Nop 平台可逆计算 |
| 5.5 | **BUILD** | `nop-stream-flink`：可选外部后端适配，将 core 的 Transformation 映射到 Flink DataStream。给「已有 Flink 集群」的用户一个迁移路径 | `comparison.md` §3.1 |
| 5.6 | FIX | 模块独立化：`nop-stream-api`（公共接口提取）、`nop-stream-checkpoint`（从 runtime 分离协调器与存储）。当前是空壳占位 | `01-architecture-baseline.md` §二 |
| 5.7 | **BUILD** | 连接器生态扩展：事务型 JDBC sink（2PC）、Kafka source/sink、CDC（Debezium 桥接深化）、文件 sink（temp file + atomic rename + manifest commit） | `connector-design.md`、`checkpoint-design.md` §6 |

**前置依赖**：Phase 0-4 提供稳定的分布式运行时。Phase 5 各子项之间相对独立，可并行或按需选取。

**验收标准**：
- XDSL 定义的 StreamModel 与等价的 DataStream API 程序生成相同 fingerprint，运行结果一致
- Delta 修改 StreamModel（如调整 window size）后，fingerprint 正确反映变更
- `nop-stream-flink` 后端运行 nop-stream 程序，结果与本地后端一致
- Kafka source + window + Kafka sink 端到端 exactly-once（kill 恢复不丢不重）

---

## 五、风险与缓解

| # | 风险 | 概率 | 影响 | 缓解 |
|---|------|------|------|------|
| R1 | Operator State 设计与现有 source 接口冲突，引发破坏性重构 | 中 | Phase 0 延期 | 0.3 启动前先审计现有 `BatchLoaderSourceFunction`/消息队列 source 的 offset 模型，设计 operator state 时兼容增量演进 |
| R2 | RocksDB 原生库跨平台加载问题（macOS/Linux/Windows） | 中 | Phase 1 阻塞 | 复用 Flink 的 RocksDB 加载模式；首版只支持 Linux + macOS，Windows 列为后续 |
| R3 | **nop-stream 作为 `SysDaoLeaderElector` 首个生产用户，集成遇未预见问题** | 高 | Phase 3 延期 | Phase 3 启动前先做平台 leader elector 的集成预研（独立 spike）；遇平台 bug 回传 nop-sys-dao 修复，不绕过 |
| R4 | Unaligned checkpoint 与 rescale 交互（4.6）复杂度超预期 | 高 | Phase 4 部分延期 | 4.6 列为 Phase 4 末尾可选交付；首版 unaligned（4.2）可限制为「不支持 rescale 的 unaligned」，rescale 时强制 aligned |
| R5 | 多 JVM 集成测试基建（3.6）投入超预期 | 中 | Phase 3 验收受限 | 评估是否复用 Flink `flink-dist` 测试模式或 Nop 现有 e2e 基建（`nop-entropy-e2e`） |
| R6 | Key-Group 升级（Phase 2）导致存量 savepoint 不兼容 | 中 | 状态迁移成本 | 提供 StateShard→KeyGroup 一次性迁移 action；升级前明确声明不兼容，要求用户显式迁移 |
| R7 | `00-vision.md` Non-Goal「动态并行度调整」与 Phase 2「savepoint rescale」边界模糊引发争议 | 低 | 文档一致性 | 2.5 显式澄清：运行时动态 rescale 仍 Non-Goal，savepoint 改并行度恢复纳入目标 |
| R8 | CEP 接入统一后端（0.4）改变 CEP 状态 schema，破坏存量 CEP savepoint | 低 | CEP 用户状态丢失 | 0.4 上线前提供 CEP 状态迁移 action；CEP 当前生产用户少，窗口期可控 |

---

## 六、工程量与复用判据

基于对 Nop 平台基础设施的审计，每个分布式能力按 **BUILD**（自建流处理专有逻辑）/ **WIRE**（接入平台既有接口）/ **DONE**（已实现可直接用）分类：

| 能力 | 判据 | 归属 Phase |
|------|------|-----------|
| Operator State 体系 | **BUILD** | Phase 0 |
| StreamModel 做实 / 文档收敛 | **FIX** | Phase 0 |
| CEP 接入统一后端 | **FIX** | Phase 0 |
| abort 控制通道 | **BUILD**（local）+ **WIRE**（distributed 用 cancelTask RPC） | Phase 0 + 3 |
| RocksDB 状态后端 | **BUILD** | Phase 1 |
| 增量 checkpoint（SST 共享） | **BUILD** | Phase 1 |
| State TTL | **BUILD** | Phase 1 |
| 状态迁移接线 | **WIRE**（设计已定义） | Phase 1 |
| Key-Group 模型 | **BUILD** | Phase 2 |
| KeyGroupRange rescale 恢复 | **BUILD** | Phase 2 |
| 跨 JVM 控制面 RPC | **WIRE**（`nop-rpc` 全套就绪） | Phase 3 |
| 跨 JVM 数据面（IMessageService） | **WIRE**（`SysDaoMessageService`/`PulsarMessageService` 就绪） | Phase 3 |
| Coordinator leader 选举 / HA | **WIRE**（`ILeaderElector` + `SysDaoLeaderElector` 就绪，**首个生产用户**） | Phase 3 |
| Discovery / 心跳 / lease | **WIRE**（`IDiscoveryClient`/`INamingService` 就绪） | Phase 3 |
| 分布式锁 | **DONE**（`IResourceLockManager` + `SysDaoResourceLockManager`） | 按需 |
| 分区分配 | **DONE**（`IPartitionAssigner` + `WeightedPartitionAssigner`） | 按需 |
| Fencing（消息拒绝错配 epoch） | **DONE**（`RemoteInputChannel` 已实现） | 已完成 |
| channel 心跳 | **BUILD** | Phase 4 |
| Unaligned checkpoint | **BUILD** | Phase 4 |
| Region-based failover | **BUILD** | Phase 4 |
| 多并发 checkpoint | **BUILD** | Phase 4 |
| Source split 体系 | **BUILD** | Phase 5 |
| XDSL 声明式编排 | **BUILD** | Phase 5 |
| Delta 定制 | **WIRE**（Nop 可逆计算机制） | Phase 5 |
| Flink 后端适配 | **BUILD** | Phase 5 |
| Kafka IMessageService | **BUILD**（`nop-message-kafka` 空模块） | Phase 5 |

**关键结论**：分布式核心能力（网络、HA、lease、discovery）大部分是 **WIRE**，真正需要 **BUILD** 的是流处理专有逻辑（operator state、RocksDB、unaligned、region failover、key-group、source split、XDSL）。这把 C 档「完整分布式生产级」的工程量从「自造一个 Flink」降到「接入平台 + 补齐流处理内核」。

---

## 七、决策点（必须由人确认）

以下决策不可由 AI 自行发明，必须在对应 Phase 启动前显式确认：

1. **Phase 0.1 的方向**：PartitionedPlan/DeploymentPlan 做实 vs 承认 GraphExecutionPlan 为正式执行层——影响 `01-architecture-baseline.md` 的修订方向
2. **Phase 0.2 的方向**：StreamModel 做实 vs 降级文档表述——影响「模型中心论」是否仍是核心卖点
3. **Phase 1 RocksDB 原生库的打包与分发策略**：随 nop-stream 发布 vs 依赖系统安装
4. **Phase 2 Key-Group 默认 maxParallelism**：128（Flink 默认下界）vs 其他值；以及 StateShard→KeyGroup 是否提供自动迁移
5. **Phase 3.4 ClusterRegistry 取舍**：完全替换为平台 discovery vs 保留 JdbcClusterRegistry 与平台对接
6. **Phase 3 leader elector 后端选择**：JDBC（零基建，已有 `SysDaoLeaderElector`）vs Nacos/Zookeeper（若平台后续提供对应 elector）
7. **Phase 3.3 数据面 IMessageService 默认后端**：SysDaoMessageService（DB，零基建）vs Pulsar（需 MQ 基建）
8. **Phase 4.4 region failover 是否首版必交付**：还是 Phase 4 首版只做 unaligned + HA，region 延后
9. **Phase 5.5 nop-stream-flink 的优先级**：是否值得投入，还是聚焦自有后端

---

## 八、与已有设计文档的关系

| 文档 | 本路线图的关系 |
|------|---------------|
| `00-vision.md` | 定位与 Non-Goals 的权威来源。Phase 2.5 会更新 Non-Goal 边界（savepoint rescale 纳入目标） |
| `01-architecture-baseline.md` | 架构基线。Phase 0.1 可能修订 §四执行管线层级的表述 |
| `core-design.md` | StreamModel 定义。Phase 0.2 做实或降级 |
| `graph-model-design.md` | 图模型。Phase 0.1、0.7 涉及 |
| `checkpoint-design.md` | Checkpoint 协议权威。Phase 0.5/0.6、Phase 4 全部指向 §13 容错契约表 |
| `state-management-design.md` | 状态后端。Phase 1、Phase 2 扩展 |
| `connector-design.md` | 连接器。Phase 5.2、5.7 扩展 |
| `cep-design.md` | CEP。Phase 0.4 接入统一后端 |
| `comparison.md` | 架构对比。本路线图完成后部分「未实现」标记需更新 |
| `component-roadmap.md` | 动态滚动视图。本文档定义阶段，component-roadmap 在每阶段内选工作单元 |

本路线图不取代任何已有设计文档的规范性定义，只定义「实现这些设计的顺序与判据」。设计的具体内容由对应专题文档权威。

---

## 九、不变量

本路线图的执行不可违反以下不变量：

1. 每个 Phase 必须有可验证的 exit criteria，未达验收标准不进入下一 Phase
2. WIRE 优先于 BUILD——平台已提供的能力一律接入，禁止重造（约束 1：复用平台）
3. 流处理专有逻辑（operator state、RocksDB、unaligned、key-group）才允许 BUILD
4. 设计文档与实现的落差在发现时立即修复（Phase 0.1、0.2 的精神适用于全流程）
5. Phase 启动前必须确认 §七的对应决策点
6. 任何阶段都不引入 `00-vision.md` §四明确排除的能力（双流 Join、SQL、大规模集群、异步算子）——若发现需要，先修订 vision 再纳入
