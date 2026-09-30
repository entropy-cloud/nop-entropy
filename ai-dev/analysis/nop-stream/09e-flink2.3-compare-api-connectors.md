# nop-stream vs Flink 2.3.0 —— 前端 API（DataStream/XDSL/模型层）与连接器对比

> Status: resolved
> Date: 2026-09-30
> Scope: `nop-stream-core`（DataStream API + connector registry）、`nop-stream-flow`（stream.xdef / StreamModelDslBuilder / AdvancedTransforms / Delta）、`nop-stream-connector{,-jdbc,-batch,-debezium}`；对照 Flink `release-2.3.0`（commit `c0f8d1a1e09`，本地 checkout `/Users/abc/sources/flink`）的 `flink-datastream-api`（V2）、`flink-runtime` V1 DataStream、`flink-core` sink2/source connector SPI、`flink-connectors/flink-connector-base`。
> Conclusion: 「三入口归一 StreamModel + Delta 升级兼容」的差异化价值**真实成立**（Flink 2.x 自身正陷入 V1→V2 双轨迁移，且没有任何模型差量机制）；语义声明门禁（ProcessingGuarantee + capability 矩阵）是 Flink 没有的真实加分项，但 **STRICT 默认值把加分收了上手税**（DC-01/02 已修文档、默认未动）；连接器 2PC 基础扎实（账本 v2 已修命名空间），结构性短板是 sink 无 Writer/Committer 分离与重试协议、source 两代并存无弃用策略、XDSL 声明面宽于运行时面（union/sideOutput fail-fast）。综合评分 **3.5/5**。

---

## 0. 校准基准与版本锚点

- 校准文档：`ai-dev/design/nop-stream/00-vision.md`——「图模型为核」「模型优先」是不可违反约束 #1/#2，XDSL/Delta **不是附加物而是定位本身**。本篇所有「缺陷/裁剪」判定均以该定位为坐标系，不拿 Flink 的完整度直接当标尺。
- 已确认缺陷不重复报告：R4 轮 `03-connectors-security.md`（R5-CON-01..11，plan 368 Phase 4/7 已修 CON-01..05/07/08/10）与 `08-doc-contract-consistency.md`（R5-DC-01..13，plan 368 Phase 6 已修）。本篇引用其现状，不重开案。
- Flink 侧对照基线（全部经本地代码核实，标注路径）：
  - V1 DataStream 仍为**主 API 且未整体废弃**：`flink-runtime/src/main/java/org/apache/flink/streaming/api/datastream/DataStream.java`（2.3 已把 V1 API 主体从 flink-streaming-java 挪入 flink-runtime；仅个别方法 `@Deprecated`）。
  - V2 DataStream API：`flink-datastream-api`，**51 个文件、全部 `@Experimental`**，自带文档节 `docs/content/docs/dev/datastream-v2/`。
  - 旧契约被打入 `legacy` 包冻结：`org.apache.flink.streaming.api.functions.source.legacy.SourceFunction`、`org.apache.flink.streaming.api.functions.sink.legacy.TwoPhaseCommitSinkFunction`（均在 flink-runtime）。
  - Sink V2（FLIP-346）公共契约位于 `flink-core`：`org/apache/flink/api/connector/sink2/`（Sink / SinkWriter / StatefulSinkWriter / CommittingSinkWriter / Committer / SupportsCommitter / SupportsWriterState / WriterInitContext / CommitterInitContext）。**2.3 的 sink2 公共接口中已无 GlobalCommitter**（GlobalCommitter 仅残留在 V1 runtime 管线 `GlobalCommitterTransform`）。
  - Source V2（FLIP-27）位于 `flink-core`：`org/apache/flink/api/connector/source/`（含 `SupportsBatchSnapshot`、`SourceEvent` 等 plugin 扩展点）。
  - 主仓 `flink-connectors/` 仅 5 个模块：`flink-connector-base`（AsyncSinkBase 限速写框架 / HybridSource / source-reader 基类）、`flink-connector-files`（FileSource + FileSink）、`flink-file-sink-common`、`flink-connector-datagen`、`flink-hadoop-compatibility`。**kafka/jdbc connector 在独立仓库**，本 checkout 内无（jdbc exactly-once 的 XA 模式属外部仓事实，见 §3.2 注记）。

---

## 1. 总对照表

| 维度 | Flink 2.3.0 | nop-stream（HEAD，plan 368 收口后） |
|---|---|---|
| 主用户 API | V1 DataStream（完整面，未废弃） | Java DataStream API（V1 风格，定位为**非核心路径构造器**，00-vision §七） |
| 新一代 API | V2（`flink-datastream-api`，51 文件，全 Experimental，双轨迁移中） | 无对应物（不适用——XDSL 是它的「下一代入口」且已稳定） |
| 声明式入口 | Table API/SQL（另一族 API，无模型差量） | XDSL `.stream.xml`（stream.xdef，15 种 transform，`xdef:support-extends="true"`） |
| 差量/升级机制 | 无（savepoint 兼容靠 serializer snapshot + 迁移文档） | Delta（`x:extends` / `_delta/<layer>/` + keyed-list merge + fingerprint 敏感度规则 + 3 个钉定测试） |
| canonical 模型 | Transformation DAG（内部物，非用户可见契约） | StreamModel（可序列化、含 StreamComponents registry / requirement / fingerprint，三入口唯一归一） |
| Source 契约 | Source V2（FLIP-27，功能面最全）+ legacy SourceFunction（冻结） | SourceFunction（push，主力）+ FLIP-27 风格 Source（收窄版，§3.3）两代并存 |
| Sink 契约 | Sink V2 三段式（Writer/Committer mixin + CommitRequest 重试协议）；无 GlobalCommitter | SinkFunction + `TwoPhaseCommitSinkFunction`（算子内 2PC，CheckpointParticipant，barrier 驱动） |
| JDBC exactly-once | 外部仓 flink-connector-jdbc：XA 事务模型 | `JdbcTwoPhaseCommitSink`：内存缓冲 + epoch ledger v2（`sink_namespace` 命名空间，plan 368 修 CON-01） |
| 连接器发现 | ServiceLoader（SQL 端 factory identifier）/ 手工 jar 依赖，无能力声明 | SPI 工厂注册中心（NopIoC beans.xml 载体）+ 能力描述符 + Catalog 枚举/探测 + conf-validate/dry-run 连通性探测 |
| 语义声明 | `CheckpointingMode`（仅管 barrier 对齐），**不校验连接器能力** | `ProcessingGuarantee` 4 档 + `Source/SinkConsistencyCapability` + `validateConnectorConsistency` STRICT 门禁 |
| 连接器数量 | 主仓 5 模块 + 外部生态数十个 | 8 个注册端点（file×2 / message×2 / jdbc-2pc / debezium-cdc / batch-loader / batch-consumer） |
| 文档化 | 官方文档体系极全（V1+V2 分节） | docs-for-ai owner doc 5 篇（user-guide/connectors/cdc-cookbook/migration-guide/nop-stream.md），R4 审计判定「逐项抽查基本全对」 |

---

## 2. 维度一：API 设计

### 2.1 Flink 2.3 的双轨现状（重要对比背景）

Flink 2.x 处于 **V1→V2 迁移中间态**，这本身是最有信息量的对比点：

- V1 DataStream 完整保留（union/connect/side-output/join/broadcast/async/window 全量），类已迁至 flink-runtime，**未标废弃**——V2 尚未准备好接班。
- V2（`flink-datastream-api`）是一次真正面向类型安全与协调边界重划的再设计：
  - 流类型显式分档：`NonKeyedPartitionStream` / `KeyedPartitionStream` / `GlobalStream` / `BroadcastStream`（`api/stream/`），分区语义编码进类型而非隐式 OperatorState。
  - 函数面收敛为 process 族（`OneInputStreamProcessFunction` / `TwoInput*` / `TwoOutput*`），map/filter 由 `BuiltinFuncs` 糖化；协调边界用 `ProcessConfigurable` 承载。
  - window/join/event-time 被移到 **extension 包**（`api/extension/{window,join,eventtime}`，如 `TumblingTimeWindowStrategy`）——主 API 面比 V1 更小。
  - **没有 union**（全模块 grep 零命中），side-output 以 `TwoOutputStreamProcessFunction` 双输出形态呈现，`toSink(Sink)` 直接对接 sink2。
  - 全部接口 `@Experimental`；连接器生态（外部仓 kafka/jdbc）尚未提供 V2 端点。
- 含义：**「API 面稳定升级」恰是 Flink 自己的痛点**。V2 从提出到 2.3 仍是 Experimental，说明任何「把图模型当一等契约」的体系一旦 API 定型，改动成本都会指数化——这正是 nop-stream 把契约下沉到可序列化 StreamModel、把 API 降为构造路径的动机面。

### 2.2 nop-stream 的 V1 风格 API + 三入口归一

- Java API（`nop-stream-core/.../datastream/`）：`DataStream` / `SingleOutputStreamOperator` / `KeyedStream` / `WindowedStream`（+ `*Impl`），面为 map/flatMap/filter/process/keyBy/window/aggregate/reduce/sum/min/max/assignTimestampsAndWatermarks（含 per-node watermarkInterval 重载）/transform/setParallelism/sink(fn[, parallelism])/print。
- 与 Flink 的两处显式偏离：① `WindowedStream extends DataStream`（Flink 中 WindowedStream 不是 DataStream）——因为窗口是虚拟元素，`setParallelism` 在其上有明确 javadoc caveat（重定向到上游 keyed 顶点）；② `sink(fn, parallelism)` 重载（终端无流对象可回设）。
- XDSL（`nop-stream-flow`）：`stream.xdef`（29 个 model 类 + `_gen` 生成物，`StreamModelDslBuilder` 810 行 + `AdvancedTransforms` 632 行）覆盖全部 15 种 transform；两种函数指定形态（bean 引用 / 内联 xpl，`aggregate` 例外仅 bean）；组件注册表（windowingStrategies/coders/schemas）稳定 ID 引用；构建期错误带 `SourceLocation` 锚点；per-transform parallelism 解析链（transform > stream > 默认 1）全链生效。
- Delta（`xdef:support-extends="true"`，stream.xdef:24）：`x:extends` 显式 base path 与 `_delta/<layer>/` + `x:extends="super"` 双入口；keyed-list（transforms/edges 按 id）merge 语义（覆盖/追加/`x:override="remove"`）；fingerprint 只敏感 DAG 拓扑、by-design 不敏感 config-only delta；`TestStreamModelDeltaExtends` / `TestStreamModelDeltaFailFast` / `TestStreamModelDeltaFingerprint` 三测试钉定。
- 归一验证：三入口都产出 `core.model.StreamModel`（含 `computeFingerprint()`、StreamComponents registry），`execute()` 统一走 `buildStreamModel → validate → StreamGraph → JobGraph → PartitionedPlan → DeploymentPlan`（`StreamExecutionEnvironment.java:337-377`）。

### 2.3 API 面完整性对照表

| 能力 | Flink V1 | Flink V2（2.3） | nop-stream core | nop-stream XDSL | 备注 |
|---|---|---|---|---|---|
| map/flatMap/filter | 有 | process 族 + BuiltinFuncs | 有 | 有（bean + 内联 xpl） | — |
| process（状态+timer） | 有 | 有（主形态） | 有（ProcessFunction/KeyedProcessFunction） | 有 | — |
| keyBy | 有 | 有（KeyedPartitionStream） | 有 | 有 | — |
| window | 完整（assigner/trigger/evictor/allowedLateness/late-data side output） | extension 包（Experimental） | trigger/evictor/apply/aggregate/reduce/process；**无 allowedLateness 用户面**（XDSL 声明非默认值即 fail-fast，P1-XDSL-6） | 有（strategyRef 注册表） | 窗口语义详见 `05-window-comparison.md` |
| aggregate | 有 | — | 有 | 有（仅 bean，无内联 xpl） | 函数指定面不对称 |
| union | 有 | **无** | **无**（builder fail-fast） | **声明了但 fail-fast** | runtime-API-gap，见 §5.1-1 |
| side output | 有（OutputTag 检索） | 双输出 process | **检索 API 无**；但 `SideOutputElement` 线协议已在（ResultPartition/StreamElementCodec 消费），窗口 late-data OutputTag 算子级已有（`TestSideOutputChainingE2E`） | `<sideOutput>` 声明了但 fail-fast | 缺口在 API 层不在传输层 |
| connect / join | 有 | connectAndProcess + join extension | 无（Non-Goal 双流 join） | 无 | 定位内裁剪 |
| broadcast | 有（BroadcastStream/BroadcastState） | BroadcastStream 类型 | 无专用 BroadcastState（G36 永久排除）；operator state `BROADCAST` 重分布替代 | 无 | 定位内裁剪 |
| Async I/O | AsyncDataStream | — | 无（Non-Goal） | 无 | 定位内裁剪 |
| timestamps/watermarks | 完整 | eventtime extension | 有（含 per-node interval 重载） | 有 | — |
| 并行度声明 | setParallelism | 配置面 | 有（协变覆盖 + sink 重载 + 类型化守卫） | 有（三段解析顺序） | XDSL 面先于 Flink |
| 自定义算子 | transform + ProcessOperator | — | `transform(name, typeInfo, operator)` | `<custom customType>` | — |
| CEP | 库（独立） | — | 内置 NFA/SharedBuffer + `<cep>` 节点 | 有（内联 + patternRef） | nop 先于 V2 集成 |

**小结**：nop-stream 的 API 面约为 Flink V1 的单流子集（含 window 全链）+ CEP 集成；比 Flink V2 主 API 多 union 之外的单流全件与声明面。缺的 union/sideOutput/connect 中，前两者在 XDSL **声明面已存在**（xdef 有节点、builder 拒绝执行），后两者是显式 Non-Goal。

### 2.4 差异化价值判定：「声明式模型 + Delta 升级兼容是 Flink 没有的」

**成立，且比宣称的更有分量**：

1. Flink 没有任何等价物。SQL/Table 是「另一族入口」而非「同一 canonical 模型的第三入口」，且 Flink 的执行图（Transformation DAG）是内部物，不作为用户可见、可 diff、可叠加差量的交付工件。nop-stream 的 StreamModel 是序列化工件：有 fingerprint（拓扑身份）、有 keyed-list merge、有 `_delta` 分层目录。
2. Flink 2.x 的 V1→V2 双轨现状反向证明了该价值：API 定型后迁移成本极高（V2 数年仍 Experimental、生态未跟），而 nop-stream 把稳定性契约放在模型层，API 层（构造器）反而可以低代价演进——模型不变、构造器可换。
3. Delta 的真实边界必须诚实陈述（`connector-design.md` §8.8 D8 已裁定）：**Delta 只覆盖模型/配置面**（拓扑增删改、参数覆盖、checkpoint 配置），不能分发代码物（新函数类、连接器 jar）。Flink 的 udf jar 分发、maven 生态、跨集群提交（`flink-run`）在代码物分发面上完整强于 nop-stream（后者依赖平台 VFS/类路径同侧交付）。所以精确表述是：**「配置/拓扑面的升级兼容」nop-stream 胜，「代码物分发与生态」Flink 胜**——两者合起来才是完整图景，单引前半句会过度承诺。
4. 文档化程度：nop-stream 侧 owner doc（`docs-for-ai/03-modules/nop-stream-user-guide.md`、`nop-stream-connectors.md`）在 R4 审计（08 号）中判定「抽查准确度很高」；README 门面问题（DC-01/04/05）已在 plan 368 Phase 6 修复（本篇复核 README 已含语义组合规则与降档行）。Flink 文档体系（V1 + V2 分节）量级不可比，属生态规模差。

---

## 3. 维度二：Connector 框架

### 3.1 Sink 侧：算子内 2PC vs 三段式

| 方面 | Flink Sink V2（2.3，flink-core sink2） | nop-stream TwoPhaseCommitSinkFunction |
|---|---|---|
| 分离度 | Writer（写）/ Committer（提交）**两类对象、两段拓扑**（`StandardSinkTopologies` 装配 CommittableMessage 通道）；commit 不占用数据面 subtask 线程 | 单对象：sink 算子在 `processBarrier` 内联 saveState→preCommit，checkpoint durable 后 commit（同 subtask） |
| 2PC 入口 | `SupportsCommitter` mixin + `CommittingSinkWriter.prepareCommit()` 产 committable | `beginTransaction/invoke/preCommit/commit/rollback/abort(epoch)` + `CheckpointParticipant` 协议 |
| 重试协议 | `CommitRequest`：retries 计数 + `signalFailedWithKnownReason/UnknownReason` 分类 + 可重试判定 | 无显式协议：commit 失败保留 pendingCommits 由 `finishCommit`/`restoreFromEpoch` 子重试；无上限、无分类指标（R5-CON-11 记录了内存滞留面） |
| 恢复语义 | writer state + committable 恢复由 runtime 拓扑承载 | `restoreFromEpoch`：durable-未提交重提交 / 非 durable abort——语义正确且有钉定测试 |
| 并行隔离 | writer per-subtask 天然隔离 | `copyForSubtask(int/TaskLocation)`，默认 fail-loud（subtask>0 未实现即抛）——比 Flink 更防御 |
| 全局提交 | GlobalCommitter **已从 sink2 公共接口移除**（仅 V1 管线残留） | 无对应物（不需要：ledger/manifest 幂等守卫承担） |
| 契约冻结 | 旧 `TwoPhaseCommitSinkFunction<IN,TXN,CONTEXT>`（三类型参）打入 legacy 包 | 当前主力契约，无弃用计划 |

**评价**：Flink 的 Writer/Committer 分离解决的是「慢提交（目标端故障）拖死数据面」与「commit 并行化」问题，代价是一条 committable 传输拓扑 + 一套重试协议。nop-stream 在其定位（几十 GB 状态、非 PB 吞吐）下算子内 2PC 足够，且 `copyForSubtask` fail-loud、`restoreFromEpoch` 的 durable/非 durable 二分这类防御性细节比 Flink legacy 2PC 更清晰。**真正值得抄的是 CommitRequest 重试协议的最小面**（见 §5.3），而不是整套三段拓扑——GlobalCommitter 被 Flink 自己移除这个事实，恰好支持 nop-stream「不做独立 committer 拓扑」的裁剪。

### 3.2 JDBC exactly-once 机制对比

| 方面 | Flink jdbc connector（外部仓，3.x+，本 checkout 无源码——按公开机制陈述） | nop-stream JdbcTwoPhaseCommitSink |
|---|---|---|
| 事务载体 | XA 事务（XaFacade；prepare 于 checkpoint、commit 于 notifyComplete），连接死亡后事务可由 recovery manager 接管 | 标准 JDBC 连接 + **内存缓冲模型**（preCommit no-op；commit 时新开连接原子写数据+ledger 行）——D1 裁定明确拒绝「preCommit flush 到连接」因标准连接不跨死亡存活 |
| 幂等 | XA recovery + saga 补偿 | epoch ledger v2：`(sink_namespace, epoch_id, subtask_id)` 复合主键 + commit 前查 guard；命名空间 = 部署 `jobId|vertexId`（缺省回落目标表名），跨支路/跨作业共库不再互相挡（plan 368 修 R5-CON-01，表 `stream_epoch_ledger_v2`） |
| 限制 | 依赖 DB XA 支持（PG/MySQL 受限、需 max_prepared_txn 配置等） | 整 epoch 批驻留堆内存、无上限无背压（R5-CON-11 P3 已登记：高吞吐×长间隔×目标端故障组合可 OOM；file sink 的 saveState 落盘是现成 spill 先例） |
| 依赖面 | 自管 DataSource/连接池 | 平台 `IJdbcTemplate + IDialect`（多方言、标识符转义链经审计 A1 确认无注入） |

**评价**：两条路线的取舍都与各自平台绑定（Flink 要通吃外部任意 DB → XA；nop-stream 绑平台 JDBC 基础设施 → 内存缓冲 + DB 端幂等账本）。机制上 nop-stream 方案不弱于 Flink 的 at-least-once flush 模式、弱于 XA 的「大事务跨 epoch」，但换来了零 XA 运维成本与平台方言统一。剩余结构性风险是 CON-11（内存驻留无上限）——低成本修法见 §5.3-6。

### 3.3 Source 侧

| 方面 | Flink（Source V2 + connector-base） | nop-stream |
|---|---|---|
| 契约 | Source/SplitEnumerator/SourceReader + 富上下文（`SplitEnumeratorContext` 支持 callAsync/定时器/SourceEvent） | 同名四契约（`core/source/`）但**有意收窄**：pull 模型 `handleSplitRequest`、无 fraction-splitting（Beam-SDF D1 reject）、无 SourceEvent、无 WatermarkEstimator（defer）、enumerator 硬接 JobCoordinator（D7 bypass 通用 OperatorCoordinator） |
| 协调 | OperatorCoordinator 通用抽象（每算子可挂 coordinator） | 单点 enumerator + 控制面 RPC 下发 split（D3：不进 TaskDeploymentDescriptor） |
| 恢复 | enumerator state + reader split cursor 双通道，官方框架承载 | 相同双通道结构（`EpochManifest.sourceEnumeratorSnapshots` + `TaskEpochSnapshot`）+ §4.8 孤儿 split 归还协议——**与 Flink 同构** |
| 旧契约 | legacy SourceFunction 打包冻结、新连接器一律 Source | SourceFunction 仍是 message/debezium/batch 三族的主力契约；Source 接口仅 FileSource 一族；**无弃用策略与收敛路线** |
| 框架层 | connector-base：source-reader 基类族 + HybridSource + AsyncSinkBase 限速框架 | 无框架层（8 个端点直接实现契约）——规模匹配 |

**评价**：nop-stream 的 Source 契约是 FLIP-27 的**功能子集 + 相同状态模型**，这是收敛正确（拒绝项均有裁定与理由，无空壳抽象）。结构性问题只有一个：两代 source 并存且主力连接器还在旧契约上，而 Flink 已经用「legacy 包 + 冻结」给出了收敛姿态。这是 §5.3-5 的零成本采纳项。

### 3.4 SPI 注册中心 vs Flink 的连接器发现

- Flink：DataStream 端连接器就是普通 jar 依赖 + 手工构造；SQL 端经 factory identifier + ServiceLoader 发现；**没有任何跨连接器的能力声明/校验机制**（能力差异散落在各 connector 文档）。
- nop-stream：`(direction, typeName)` 命名空间注册中心 + `ConnectorCapabilityDescriptor`（方向/交付语义/并行度/恢复语义/参数规格）+ 单一事实源不变式（描述符声明 == 端点实例 `getSourceConsistency()`，2PC ⟹ PARALLEL，发现测试 + catalog 运行期探测双钉定）+ `StreamConnectivityProber` 提交前连通性探测（含副作用红线表）。
- 评价：这是 nop-stream 把「能力真相」机器可读化的独特资产，直接喂给 §4 的语义门禁。代价见 §5.4-2（8 个连接器配六件套的机制先行成本）。

---

## 4. 维度三：语义声明机制

### 4.1 机制对照

| 方面 | Flink 2.3 | nop-stream |
|---|---|---|
| 声明位 | `CheckpointingMode`（EXACTLY_ONCE/AT_LEAST_ONCE，`flink-core/.../core/execution/CheckpointingMode.java`）——**只约束 barrier 对齐**，不触碰 sink 语义；Exactly-once 与否实际由「用没用 2PC sink」隐式决定 | `ProcessingGuarantee` 四档（STRICT_EXACTLY_ONCE 默认 / AT_LEAST_ONCE / EFFECTIVELY_ONCE / BEST_EFFORT），barrierAlignment + requiresDurableCheckpoint 双标志 |
| 连接器能力 | 无声明机制 | `Source/SinkConsistencyCapability` 枚举（source：BEST_EFFORT→…→REPLAYABLE→TRANSACTIONAL_READ；sink：…→TWO_PHASE_COMMIT→STAGED_ATOMIC_COMMIT），连接器实现自声明 |
| 门禁 | **无**：非事务 sink 配 EXACTLY_ONCE 模式照样运行，语义静默降为 at-least-once | `validateConnectorConsistency`：STRICT 时 source 必须 ≥REPLAYABLE、sink 必须 ≥TWO_PHASE_COMMIT，违者 typed error（`StreamRequirementValidator.java:84-114`）；**非 STRICT 档零校验** |
| 违规后果 | 静默数据重复（用户通常后知后觉） | 拒绝启动（错误信息带逐 connector 的 capability 差距） |

### 4.2 DC-01 案例复盘（「默认 STRICT 使示例全挂」）

- 机制链：`CheckpointConfig.java` 默认 `STRICT_EXACTLY_ONCE` + `execute()` 无条件前置 `validateConnectorConsistency`（`StreamExecutionEnvironment.java:348-352`）+ 内建 `fromElements`/`print` 默认 BEST_EFFORT/AT_LEAST_ONCE ⇒ README/user-guide 第一条示例必抛异常（R4 08 号报告 DC-01/02，runtime 实测复现）。
- plan 368 的修法：**改文档不改默认**——README 现已含语义组合规则注释 + 显式 `setProcessingGuarantee(AT_LEAST_ONCE)` 降档行（本篇复核确认）；xdef 默认值仍为 STRICT（stream.xdef `processingGuarantee="!enum:...=STRICT_EXACTLY_ONCE"`）。
- 这个修法方向正确：门禁本身是对的（vision 不变量 #4/#7「语义不降级」），错的是**默认值选择了最严档**，把「声明语义」变成了「每个 pipeline 必须做一次降档仪式」。

### 4.3 加分项还是负担

**加分项（真实、Flink 没有）**：
1. 它把 Flink 里最经典的静默事故（「我以为是 exactly-once」）变成启动期 typed error，错误消息逐 connector 列差距——这在审计 03 号的 2PC 正确性复核里已被证明与账本幂等构成完整的 EOS 闭环。
2. 能力声明与描述符单一事实源 + catalog 探测，使「连接器声称的语义」可被机器验证，这是 §2.4 模型优先定位的自然延伸。

**负担（结构性、未消除）**：
1. **默认值税**：STRICT 默认 + 内建默认 capability 低档 ⇒ 每个最小 pipeline 都要一行降档。Flink 的 `CheckpointingMode` 默认 EXACTLY_ONCE 没有这个问题，因为它只管对齐不管连接器。可选出路：XDSL 面（有 SPI 描述符，可在模型层静态判定）保留 STRICT 默认；Java API 面默认降为 AT_LEAST_ONCE 或 warning-first（门禁保留、默认档放松）——这是一处一行的默认值改动 + 文档同步，属低成本决策项。
2. **门禁非对称**：仅 STRICT 触发校验，EFFECTIVELY_ONCE（声明 requiresDurableCheckpoint）不做任何连接器校验——且 `EFFECTIVELY_ONCE` 枚举值在 main 代码中**零消费**（仅 ProcessingGuarantee/StreamRequirement 两处声明，全仓 grep 证实），TRANSACTIONAL_READ/STAGED_ATOMIC_COMMIT 两个高档位**无任何内建连接器声明**。语义阶梯的上层是「声明了不存在的档位」。
3. 上手期文档已付的学费（DC-01/02/03 三处示例级误导）说明该机制的易错面在「文档示例与新用户第一小时」，门禁每严一档，文档维护义务就重一分——这是选择 STRICT 默认的持续性成本，不是一次性成本。

---

## 5. 维度四：最优化评估

### 5.1 定位内缺陷清单（对照 Flink 后仍成立的差距）

| # | 缺陷 | 证据锚点 |
|---|---|---|
| 1 | **XDSL 声明面 > 运行时面**：`<union>`/`<sideOutput>` 在 xdef 有节点、builder fail-fast（`AdvancedTransforms.java:470-479`）；side output 更尴尬——`SideOutputElement` 线协议已被 ResultPartition/StreamElementCodec 消费、窗口 late-data OutputTag 算子级已接（`TestSideOutputChainingE2E`），**只缺 DataStream.getSideOutput 检索 API** | `nop-stream-flow/.../AdvancedTransforms.java`；`nop-stream-core/.../streamrecord/SideOutputElement.java` |
| 2 | windowingStrategies 声明 `triggerId/allowedLateness/accumulationMode` 非默认即拒（P1-XDSL-6）——声明字段无运行时消费者；Flink V1 的 allowedLateness 是 window 标配 | `AdvancedTransforms.java:86-90` |
| 3 | `aggregate` 仅 bean 无内联 xpl——同一 xdef 内函数指定面不对称 | stream-dsl-design.md §5 |
| 4 | FL-1 字段（source `params/outputType/maxParallelism/consistencyCapability`）xdef 声明、builder 一律拒绝——schema 面早于消费面 | `StreamModelDslBuilder.java` FL-1 拒绝面 |
| 5 | message source/sink 无 offset checkpoint（恢复语义 NONE），exactly-once 消息路径（2PC 消息连接器）缺席——对照 Flink kafka source 的 partition-as-split + offset，这是 nop 与「真实消息队列 exactly-once」之间的最大能力差 | connector-design.md §7-10 |
| 6 | `ProcessingGuarantee.EFFECTIVELY_ONCE` 枚举值零消费（§4.3-2）；`TRANSACTIONAL_READ/STAGED_ATOMIC_COMMIT` 无实现方 | `ProcessingGuarantee.java`、`SourceConsistencyCapability.java` |
| 7 | 文件 sink v1：单文件/epoch、text-line、无滚动策略无 format SPI——对照 `FileSink`（rolling policy + bulk/row writer + bucket assigner）已在主仓证成的模式 | connector-design.md §7-9 |
| 8 | SourceFunction（主力，3 族连接器）与 Source（1 族）两代并存，无冻结/收敛策略——Flink 已示范 legacy 包 + 冻结 | `nop-stream-connector` 各工厂 |
| 9 | 2PC commit 重试无协议（无次数上限、无 known/unknown 分类、无指标）——CON-11 内存滞留缺的正是这层管理面 | `TwoPhaseCommitSinkFunction.finishCommit` |

### 5.2 定位外裁剪（正确的「没有」，非缺陷）

- 双流 join/connect/broadcast-stream（含 BroadcastState，G36 永久排除）、Async I/O、SQL/Table API——显式 Non-Goal，替代路径已指定（CEP / operator state BROADCAST 重分布 / IBatchLoader lookup）。
- Sink V2 的独立 Committer 拓扑与 GlobalCommitter——Flink 自己已把 GlobalCommitter 移出 sink2 公共接口；nop-stream 算子内 2PC 在其规模下成立。
- Beam-SDF fraction-splitting、WatermarkEstimator、SourceEvent、通用 OperatorCoordinator（D7 bypass）——均有裁定记录与 successor 触发条件，非空壳抽象。
- 海量连接器族 / K8s-YARN 编排 / 限速写框架（AsyncSinkBase）/ HybridSource——生态规模差，非设计差。

### 5.3 低成本可采纳清单（Flink 已证可低成本采纳、且在定位内）

| # | 采纳项 | 来源 | 成本估计 |
|---|---|---|---|
| 1 | **side output 检索 API 闭合**：`SingleOutputStreamOperator.getSideOutput(OutputTag)` + tag 注册表——线协议与算子级发射已在，只补 API 与 builder `<sideOutput>` 接线 | Flink V1/V2（双输出 process） | 低（core API + AdvancedTransforms 各一处，fail-fast 分支已有） |
| 2 | **CommitRequest 最小重试协议**：给 `commit(long)` 失败加可配置重试上限 + known/unknown 分类 + 指标——解决 CON-11 的「无限滞留」管理面，不必引入独立 Committer 拓扑 | sink2 `Committer.CommitRequest` | 低（基类 + 2 个内建 sink） |
| 3 | **文件 sink 滚动策略**（size/time `RollingPolicy` 形态）+ 后续 format SPI | `flink-file-sink-common` | 中低（successor 已列） |
| 4 | **legacy 冻结姿态**：SourceFunction 端点 javadoc/owner doc 标注「legacy，仅维护；新连接器一律 `Source` 接口」——零代码，只定政策 | Flink legacy 包 | 极低 |
| 5 | **SourceReaderContext 携带源配置**（如 directoryPath），补 split 路径 containment 校验（R5-CON-10 建议的防御纵深） | Flink `SourceReaderContext` 富上下文模式 | 低 |
| 6 | **2PC sink 可选 spill-to-temp**（file sink 的 saveState-落盘已是自家先例）作为 CON-11 的彻底解 | `FileSinkCommittable`/writer state 思路 | 中低 |
| 7 | Java API 默认 guarantee 放松（或 warning-first 双档门禁）——一行默认值 + 门禁分级，见 §4.3-1 | Flink CheckpointingMode 只管对齐不管连接器的解耦思路 | 极低（决策成本 > 代码成本） |

不建议采纳：union 的多输入算子运行时（Flink V1 有、但 nop 的 union 用例可用多条独立管线 + sink 侧合并表达；若 XDSL `<union>` 无真实需求，删除声明面比补运行时更便宜——需产品侧确认有没有消费者后再定方向）。

### 5.4 过度设计点

1. **STRICT 默认值**（§4.3-1/4.2）：门禁机制本身是加分项，默认选最严档使机制的第一个用户体验是报错——默认值选择是本次对比中唯一「机制正确、参数错误」的点。
2. **8 个连接器配六件套**（注册中心 + 描述符 + catalog + probe + conf-validate + 能力矩阵文档）：机制先行的成本已一次性付掉，当前消费者基本是维护工具自身（Anti-Hollow 论证成立但单薄）。建议冻结机制扩展直至出现第 9 个（第三方）连接器——按 connector-design §8.7 的同一需求门控纪律。
3. **语义阶梯空档**（§5.1-6）：EFFECTIVELY_ONCE / TRANSACTIONAL_READ / STAGED_ATOMIC_COMMIT 三个无实现、无消费的档位——要么给出运行时语义（EFFECTIVELY_ONCE 至少应触发 durable-checkpoint 校验），要么降档为两个有消费者的值。
4. 小项：`DataStream.print(SinkFunction)` 重载语义怪异（print 名下接受任意 sink）；`KeyedStream.sum/min/max(int|String field)` 是 Flink V1 早期位置的 field 聚合形态——非核心路径上的维护面，可在 API 冻结时评估删除。

---

## 6. 质量评分

| 维度 | 评分 | 理由 |
|---|---|---|
| API 设计（DataStream/XDSL/Delta） | **3.5/5** | 三入口归一真实成立且经测试钉定，XDSL 面完整、错误锚点/并行度解析等工程质量高于同规模项目；扣分在声明面>运行时面（union/sideOutput/FL-1/窗口声明字段四处「xdef 有、运行时拒」）与 API 面缺口（getSideOutput）。Delta 边界（代码物不可 Delta）已在设计层诚实裁定。 |
| Connector 框架 | **3.5/5** | 2PC 基类协议（restoreFromEpoch 二分 + copyForSubtask fail-loud + 账本 v2 命名空间）经四轮审计打磨，防御性优于 Flink legacy 2PC；source 侧与 FLIP-27 同构且收窄有据。扣分：无 Writer/Committer 分离与重试协议（commit 慢拖死数据面）、无 commit 内存上限、两代 source 无收敛政策、连接器数量（8 个端点）与 Flink 生态量级差两个数量级。 |
| 语义声明机制 | **4/5** | capability 矩阵 + STRICT 门禁 + 单一事实源是 Flink 完全没有的真实资产，与 vision 不变量闭环。扣分：默认值选择（DC-01 学费）+ 门禁非对称（仅 STRICT）+ 语义阶梯空档（三个无消费者档位）。 |
| **综合** | **3.5/5** | 定位内完成度高、裁定纪律好（每个「没有」都有编号裁定），结构性欠账集中在「声明面收敛」与「commit 管理面」两处，均可低成本闭合；与 Flink 的绝对能力差距（生态、吞吐、connector 数量）属定位差而非质量差，不计入扣分。 |

---

## Conclusion

1. **差异化成立**：「XDSL + Delta + canonical StreamModel」是 Flink 没有的能力，且在 Flink 2.x 自身 V1→V2 双轨迁移的背景下，模型层契约相对 API 层契约的稳定性优势被反向验证。但必须连同边界一起表述：Delta 覆盖模型/配置面，代码物分发仍走传统 jar 路径（设计层已裁定，表述时不得过度承诺）。
2. **语义门禁是加分项，默认值是负担**：capability 矩阵 + STRICT 门禁应保留；建议把默认档决策（Java API 面 AT_LEAST_ONCE 或 warning-first）作为一个显式决策项提出，同时清理 EFFECTIVELY_ONCE 等三个无消费者档位。
3. **连接器框架的差距是管理面而非正确性面**：2PC 正确性经四轮审计已扎实（账本 v2 修复后），缺的是 Committer 重试协议、commit 内存上限、side-output 检索 API、legacy 冻结政策——全部在低成本可采纳清单（§5.3，7 项）内。
4. **最优先动作**（按性价比排序）：① getSideOutput API 闭合（声明面债最显性）；② commit 重试协议 + 内存上限（CON-11 管理面）；③ 默认 guarantee 档位决策；④ legacy source 冻结政策声明。
5. 被否决方向：引入 Flink Sink V2 的独立 Committer 拓扑（定位外，GlobalCommitter 已被 Flink 自身移除可佐证）；为 8 个连接器继续扩充注册机制（冻结至第三方连接器出现）。

## Open Questions

- [ ] `<union>` 声明面是否有真实需求方（fraud-example/quickstart 均未用）——决定「补运行时」还是「删声明」。
- [ ] Java API 面默认 guarantee 是否降档——涉及 vision 不变量 #4 的解释权，需 owner 显式决策（00-vision §六 决策点流程）。

## References

- `ai-dev/design/nop-stream/00-vision.md`（定位校准）
- `ai-dev/design/nop-stream/stream-dsl-design.md`、`graph-model-design.md`、`connector-design.md`
- `ai-dev/audits/2026-09/2026-09-30-0530-deep-audit-nop-stream-quality-r4/03-connectors-security.md`、`08-doc-contract-consistency.md`
- `docs-for-ai/03-modules/nop-stream-connectors.md`、`nop-stream-user-guide.md`
- 同族对比：`ai-dev/analysis/nop-stream/07-distributed-comparison.md`（执行面）、`03-checkpoint-comparison.md`、`08-gap-analysis.md`
- Flink 2.3.0（本地 `/Users/abc/sources/flink`，commit `c0f8d1a1e09`）：`flink-datastream-api`（51 文件全 Experimental）、`flink-runtime/.../streaming/api/datastream/DataStream.java`（V1 主 API）、`flink-core/.../api/connector/sink2/`（Sink V2）、`flink-core/.../api/connector/source/`（Source V2）、`flink-connectors/flink-connector-base`、`flink-streaming-java/.../functions/sink/legacy/TwoPhaseCommitSinkFunction.java`、`docs/content/docs/dev/datastream-v2/`
