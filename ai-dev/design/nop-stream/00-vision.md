# nop-stream 高层设计原则

**日期**：2026-05-19（更新于 2026-06-06）
**范围**：`nop-stream` 子系统
**状态**：active

---

## 一、产品定位

nop-stream 是 Nop 平台的流处理引擎，定位为**声明式图模型驱动的可分布式执行引擎**。

核心模型是 **StreamModel**——可序列化的算子图及其组件注册表。它可由三种入口构造：
- **XDSL 声明式定义**（已落地）：直接描述 StreamGraph 拓扑、算子配置、分区策略，经 `DslModelParser(.stream.xml) → flow.model.StreamModel → StreamModelDslBuilder → DataStream API` 构建可执行管线（见 `nop-stream-flow`）
- **Java DataStream API**（当前 Builder）：编程方式构造 Transformation DAG，编译为 StreamModel
- **Delta 定制**（已落地，Stage 51）：Nop 平台的可逆计算机制，在模型层叠加差量修改（`x:extends` 显式 base path + `_delta/<layer>/` 目录分层覆盖），合并后的模型经既有 `StreamModelDslBuilder` 构建+执行

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
| 双流 Join（非等值 / 范围 / 广播 join） | 复杂度极高。**收窄裁定（2026-10-02，D2）**：双流**等值** join（hash / window merge）与静态维表 lookup join 已纳入规划，见 `ai-dev/backlog/nop-stream-sql-roadmap.md`（WI13 / WI14）；非等值 / 范围 / 广播 join 仍排除，可通过 CEP 或外部 lookup 替代 |
| SQL API（Flink SQL / Table 栈复刻、代价优化器、方言全覆盖） | **收窄裁定（2026-10-02，D2）**：窄范围流 SQL 已纳入规划——复用 nop-orm-eql 的 EQL 作载体，覆盖单表查询、窗口聚合与等值 join，见 `ai-dev/backlog/nop-stream-sql-roadmap.md`；完整 Flink SQL / Table 栈复刻、代价优化器与方言全覆盖仍排除，GraphQL 继续承担主 API 职能 |
| 大规模并行（PB 级吞吐） | 分布式解决高可用和资源隔离。几十 GB 状态级别由远程 Redis/状态后端承载 |
| 复制 Flink Runtime 结构 | 不引入 SlotSharingGroup、Netty 网络栈、二进制序列化体系。学习 Flink 语义边界，但实现结构自主 |
| 在线/自动 reshard（运行时自动重分片） | `parallelism` 变化在 `maxParallelism` 上界内是 supported rescale（Stage 35，restore 时按 KeyGroupRange 区间路由）；`maxParallelism` 变化需显式离线 reshard migration action（Stage 37 已交付）。仅运行时自动重分片不在范围内 |
| 异步算子 | 增加算子线程模型复杂度，当前阶段聚焦同步算子链 |

## 五、设计收敛路径

设计按以下顺序收敛，不可逆序：

1. **先定义核心模型**（StreamModel、StreamComponents、Transformation DAG）
2. **再定义编译管线**（StreamGraph → JobGraph → PartitionedPlan → DeploymentPlan）
3. **再定义执行引擎**（算子链、数据交换、分布式控制面）
4. **再补 Checkpoint、状态管理、窗口、时间模型等支撑子系统**
5. **最后实现连接器、CEP、XDSL 编排等上层能力**（XDSL 编排已由 `StreamModelDslBuilder` 落地，Stage 50）
6. **在其上扩展窄范围 SQL 编译层与 join 算子**（EQL → 流模型的编译器、双流等值 join / 分析窗口算子，规划见 `ai-dev/backlog/nop-stream-sql-roadmap.md`；复用而非改动阶段 1-4 既有语义，收窄裁定 2026-10-02）

只要这条顺序不乱，设计就不会滑入"先写 runtime 再补模型"的陷阱。

## 六、必须由人决策的决策点

以下决策不可由 AI 自行发明，必须经过显式确认：

1. `StreamModel` 的核心结构变更（如新增 Transformation 类型、修改 StreamComponents 注册表结构）
2. 定位变更（从"流处理引擎"改为其他定位）
3. 状态后端的变更（Memory → Redis → RocksDB）
4. Checkpoint 协议的变更（如从单 in-flight 扩展为多 in-flight）
5. 分布式模式的通信模型变更（IMessageService → 其他传输）
6. `maxParallelism` 的变更（需显式 reshard migration action）

### 六裁决记录

**#4 — Unaligned checkpoint 是否构成"Checkpoint 协议变更"（Stage 43，2026-08-03）**

裁定：**不构成需要单独人审批的协议变更，可推进。** 理由：决策点 #4 的判据是并发模型变更——示例即"从单 in-flight 扩展为多 in-flight"。Unaligned checkpoint（Stage 43）**保持 single-in-flight 约束不变**（一次仍只追踪一个 in-flight epoch，multi-concurrent 是 Stage 45 职责）。它只变更两件事：(a) barrier 处理模式（aligned 阻塞对齐 → 可选 unaligned 快照在途数据并立即完成），(b) snapshot 内容（`TaskEpochSnapshot` 新增 `ChannelState`）。这两者不改变 checkpoint 并发协议，而是在既有 single-in-flight 协议内的运行时优化与快照扩展。协议不变量（§八 4–6：barrier 由 source 线程注入并随数据 channel 传播；manifest durable 前不 commit；恢复从最新 durable epoch）全部保持。详细行为语义见 `checkpoint-design.md` §2.11。

**#1 — 窄范围 SQL 所需的 StreamModel 结构变更（2026-10-02）**

裁定：**窄范围 SQL roadmap 所需的结构变更已获授权，可推进。** 授权范围限定为两类：(a) 新增 Transformation 类型——union 多输入通路与双输入 join 形态所需；(b) `StreamComponents` 注册表新增条目——参数化窗口声明、聚合声明面（aggregators）、join 声明面（joins），编译落点裁定 D14=(a)（参数化算子面）。授权依据：`ai-dev/backlog/nop-stream-sql-roadmap.md`（2026-09-30 owner 立项，2026-10-02 owner 委托执行，门控判据见该文件 Purpose 条款表）。本裁定**不覆盖**决策点 #2-#6：定位、状态后端、checkpoint 协议、通信模型与 maxParallelism 均不变；§八 15 条设计不变量是被验证对象而非被修改对象。

> 执行注记（WI21，2026-10-03）：授权 (b) 已落地——`StreamComponents.declarativeRegistries` 以 flow-free 载体 `StreamComponentEntry` 收录 aggregators/joins 条目；**schemas 条目为授权原文之外的实施延伸**（schemas 声明面本身经 WI8b 授权，其注册条目是该授权的注册实现），在此显式补记。条目为描述性规范化载体，不含可执行语义，未改 §八 15 条不变量的被验证对象地位。

> 执行注记（WI0b，2026-10-08）：**joins 既有条目的字段级扩展授权补记**——`StreamJoinSpecModel` 条目内单一 `timeout` 字段参数化为 `leftTimeout`/`rightTimeout` 双字段声明（非对称窗口 join timeout，`ai-dev/backlog/nop-stream-sql-enhancement-roadmap.md` WI27 实施），属授权原文之外的实施延伸（授权 (b) 列举的三类注册表条目不含既有条目内字段参数化），在此显式补记，比照上条 WI21 先例格式。**本补记的实质理由（与 WI21 不同，不可平移其理由）**：条目集与条目类型不变（joins 仍是授权 (b) 已覆盖的条目），仅将既有 timeout 字段参数化并扩展构建期校验面，不触碰授权 (a) 的 Transformation 类型；leftTimeout/rightTimeout 为可执行语义参数（非 WI21 的描述性载体），其语义由 WI27 构建期校验与判别性测试钉死。**边界**：本补记不改变 2026-10-02 授权 (a)(b) 的既有范围——新增注册表条目类型仍限授权原文所列，范围之外的新条目类型或条目集变更仍属本决策点人审门。授权依据：`ai-dev/backlog/nop-stream-sql-enhancement-roadmap.md` WI0b（2026-10-08 执行指令「在 nop-entropy-stream-roadmap worktree 中执行 nop-stream-sql-enhancement-roadmap.md 直到彻底完成……」委托 ZCode 代理按 roadmap 建议裁定）。

**#WI0-XDSL — xdsl-full-coverage 五项触点裁定（WI0 门，2026-10-09）**

委托依据：2026-10-09 owner 执行指令「/goal 执行 nop-stream-xdsl-full-coverage-roadmap.md直到彻底完成。每个工作项按照plan guide拟制计划执行。每个计划执行完毕自动提交一次。」——与 WI0b（2026-10-08）先例同构，委托 ZCode 代理按 roadmap 预设默认方向裁定（零解禁、零 Non-Goal 推翻）。裁定全文与逐场景回填见 `ai-dev/plans/2346-wi0-xdsl-touchpoint-adjudication.md`，矩阵回填落 `ai-dev/backlog/nop-stream-xdsl-full-coverage-roadmap.md` 12 场景矩阵「接受降级（WI0 裁定）」列。五项裁定：

- **(a) 通用异步算子：维持 §四 Non-Goal，不解除。** 2026-10-07 #WI0 五引擎对比裁定已维持 per-record 异步 lookup 排除（异步性收敛在物化刷新层）；通用异步算子属 §六决策点人审面，无新增 owner 诉求不改判。E6/E11 的异步 I/O 走同步替代声明形态（roadmap WI17 默认分支：process bean 承载外部调用 + 有界容量与超时行为契约 + 显式串行语义标注）。
- **(b) 非等值/区间 join：维持 §四排除项，不解除。** D2 收窄（2026-10-02）仅纳入等值 join；区间谓词声明面无 demonstrated user need。E11/E12 的 `BETWEEN` 区间语义走声明式变通形态（roadmap WI18 默认分支：`<union>` + 多次两路等值 join + `<process>` 聚合 + 事件时间过滤），变通形态进 owner doc。
- **(c) `<sideInputs broadcast>` 属性语义重定义：go。** 语义钉死为「有限维表注入方式」标记——构建期或低频更新的有限维表只读快照，声明该 sideInput 复制到所有并行实例；不引入流式广播状态、不引入 BroadcastState 类型，守 §七 #G36。roadmap WI11 按此语义落地。
- **(d) DISTINCT SQL 口径：精确口径归 `<sql>` 面**（已由 multi-engine roadmap WI17a 交付并显式 supersede sql roadmap WI16 不支持清单）；**roadmap WI9 声明面聚合目录交付 distinct 计数的近似口径**（dedup 集合语义 + 内存上界说明）并文档化两口径边界。
- **(e) transform 级 SQL：维持 `<sql>` 顶层唯一约束，不放宽。** `<sql>` 顶层生成器是唯一 SQL 编译入口的单一事实源；放宽将引入「模型内嵌 SQL 片段」的第二编译路径，与 §三 #1「图模型为核、运行时只执行模型」的收敛路径相悖。roadmap WI16 交付 Java-only 记录 + owner doc 指针；E8 的 transform 级 SQL 以「Java DataStream API 挂接 EQL/`<sql>` 编译产物」形态验收。重启触发条件 = 真实多段 SQL 管线声明诉求出现。

影响面：WI9（(d) 近似口径落地）、WI11（(c) 语义落地）、WI16（(e) Java-only 记录）、WI17（(a) 默认分支）、WI18（(b) 默认分支）、WI19（tail 源裁定不做 → bounded 重放 + DRAIN 变通落地）、WI20（E8 多表目标形态 = WI20 声明面，CatalogTable 前置已交付）。

**2026-10-10（deferred-unlock，owner 指令解除）**：owner 指令「（maxDelay 声明面）这个不是很容易实现吗？容易实现的部分都写计划自动完成」+ 对 transform 级 SQL 的 EQL 支持质疑（「stream sql 不是已经有内置支持了吗，nop orm eql 也做了很多增强，并且有执行器可以直接解析 AST 执行」），构成对 #WI0-XDSL (e) 延期项中 **transform 形态**的解除裁定：`<sqlTransform>` 顶点声明面交付（`ISqlStreamCompiler.compileTransform` 片段契约 + `expandSqlTransforms` 拼接，plan 2375）。**边界不变式维持**：`<sql>` 顶层唯一约束不放宽（本裁定不触碰 (e) 的「单一编译入口」内核——`<sqlTransform>` 与顶层共用同一 SPI provider/解析器，非第二编译设施）；其余四项裁定 (a)(b)(c)(d) 与 §四 Non-Goals 零变更。承载：`ai-dev/backlog/nop-stream-deferred-unlock-roadmap.md`（U1-U6；另含 WI25/WI22 两处登记勘误与 E3 误判反转）。

## 七、核心取舍

- **保留**：Barrier 快照、算子链化、多 Task 并行执行、窗口/CEP 语义、key-group 重分布（Stage 34 KeyGroup 模型 + Stage 35 `parallelism` rescale + Stage 37 `maxParallelism` 离线 reshard migration）
- **保留（非核心路径）**：DataStream API——作为 StreamModel 的编程构造器，但不是最终用户的主入口
- **去除**：复杂（非等值 / 范围）Join、广播流、异步算子（广播流 / BroadcastState 的永久排除裁定见 `### 七裁决记录` #G36；双流**等值** join 的 hash / window 两种已收窄纳入规划，收窄裁定 2026-10-02，见 `ai-dev/backlog/nop-stream-sql-roadmap.md`）
- **聚焦**：单流窗口聚合 + CEP 模式匹配 + Checkpoint 容错

### 七裁决记录

**#G36 — BroadcastState 是否纳入范围（Stage 36，2026-08-04）**

裁定：**永久排除（no-go），不引入专用 BroadcastState 类型。** 本裁定不改变 vision 边界（§四 Non-Goal「broadcast join」与 §七「去除：广播流」保持不变），仅正式记录排除理由并关闭 G36，故无需 §六「必须由人决策」审批。

依据：
1. §七/§四 既有的排除理由（复杂度极高、用例有限、可通过 CEP 或外部 lookup 替代）经核对仍然成立。
2. 无 demonstrated production user need——路线图自立项以来无任何用户/场景要求广播状态。
3. nop-stream 已落地 operator state 重分布（`RedistributionMode.UNION`/`BROADCAST`/`SPLIT_DISTRIBUTE`，见 `RedistributionMode.java` + `MemoryOperatorStateBackend` + `TestE2EOperatorStateRedistribution`）。其中 `BROADCAST` 模式（restore 时每个 subtask 收全量副本）已部分覆盖「配置流/规则流分发到所有并行实例」这一 broadcast state 的典型用例，专用 BroadcastState 类型非必需。
4. 复杂双流 broadcast join 语义属 §四 Non-Goal「双流 Join」，与本裁定一致排除。

替代路径：配置/规则分发 → operator state `BROADCAST` 重分布 + `ICheckpointedFunction`；复杂模式匹配 → CEP 引擎；外部维表 → `IBatchLoader` lookup。

如未来出现 demonstrated production user need（broadcast state 特有能力无法被上述替代覆盖），可重启 vision 决策流程（Stage 37 先例）。裁定证据见 plan `ai-dev/plans/nop-stream-production/2026-08-04-2200-2-final-gap-adjudication-g36-g66-g67.md`。

**#WI0 — 五引擎对比缺口裁定（WI0 门，2026-10-07）**

裁定：**§四 Non-Goals 六行与 #G36 边界维持不变、零解除。** 五引擎对比（Flink/TDengine/Hazelcast/SeaTunnel）核定的缺口经逐条 go/defer/exclude 裁定后，由 `ai-dev/backlog/nop-stream-multi-engine-comparison-roadmap.md` WI1..WI19 承载；其中两条涉及本 vision 边界的事项裁定如下：(a) per-record 异步 lookup 算子**维持排除**（§四「异步算子」不变）——维表 lookup 增量以「复用既有同步 lookup join 路径 + 本地物化 + 后台有界并发刷新」形态落地，异步性收敛在物化刷新层，不进入算子线程模型；(b) broadcast state **维持 #G36 永久排除**。cdc-design schema evolution defer 与 productization K8s defer 均维持。全部裁定方向与 roadmap 既有文本预设方向一致，零推翻、零解禁、零 defer 撤销。裁定全文见 `multi-engine-gap-adjudication.md`。

## 八、设计不变量

以下不变量不可违反：

1. 所有持久状态必须有稳定 `operatorId`
2. 所有 keyed state 必须有确定性 `KeyGroup` 路由（`key → keyGroupId` 仅依赖 job-global `maxParallelism`）
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
3. **五层执行管线**：StreamModel → StreamGraph → JobGraph → PartitionedPlan → DeploymentPlan（原第六阶段 RuntimeTopology 概念已退役——2026-09-01 D-GAP 裁定：0 Java 引用，运行时实例视图职能由 `ClusterRegistry`/`RuntimeNode`/liveness 承担）
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
