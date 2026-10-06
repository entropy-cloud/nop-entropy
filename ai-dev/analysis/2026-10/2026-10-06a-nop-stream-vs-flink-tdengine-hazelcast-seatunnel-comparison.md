# nop-stream 与 Flink / TDengine / Hazelcast / SeaTunnel 五方流处理能力对比

> Status: resolved
> Date: 2026-10-06
> Scope: `nop-stream/`（11 子模块源码 + owner doc + 既有 roadmap/backlog）vs Flink 2.3.0、TDengine 3.3.7+、Hazelcast 5.7、SeaTunnel 3.0（均为外部源码/文档调研）
> Conclusion: nop-stream 在 XDSL 声明式建模 + CEP + 窗口触发/演进全套 + unaligned checkpoint + DB 租约 HA 上已形成对四家的差异化优势，但连接器生态与工程化、Kafka 原生 exactly-once、生产进程入口、触发-计算解耦/范围重算类语义、观测指标三件套构成明确缺口；后续开发序列由 `ai-dev/backlog/nop-stream-multi-engine-comparison-roadmap.md` 承载。

## Context / Background

- 决策点：nop-stream 对标外部系统已做过单线对比（Flink 源码级 `nop-stream-flink-comparison-roadmap.md`、SeaTunnel `2026-10-02-seatunnel-core-vs-nop-stream-gap-analysis.md`），但 Flink/TDengine/Hazelcast/SeaTunnel 四家的横向能力面从未在一份报告中统一裁剪，导致"补什么、不补什么"的裁定分散在多份文档里。
- 方法：5 轮**串行**子 agent 调研（每家独立 session，源码级核查优先于文档转述），本文为 5 份调研的合并裁剪版；详细证据以各轮调研会话记录为准，本文只保留影响裁定的事实。
- 约束：`ai-dev/design/nop-stream/00-vision.md` §四 Non-Goals 六行（非等值/范围/广播 join、完整 SQL 栈、PB 级、复制 Flink Runtime、在线/自动 reshard、异步算子）与 §七 #G36（BroadcastState 永久排除）、productization D-GAP 处置（K8s defer / YARN/HPA exclude）、既有 roadmap（productization/production/sql/cdc/r5-successors）的完成项与登记项全程有效，本文的建议清单不得与之冲突。

## 一、五系统定位速览

| 系统 | 定位 | 对 nop-stream 的参照价值 |
|---|---|---|
| **nop-stream**（基线） | 平台内嵌流处理引擎：XDSL 声明式 + Java API 双入口，DB 租约 HA，不依赖 ZooKeeper | 本体 |
| **Flink 2.3.0** | 通用流批一体计算引擎，事实标准 | 语义完备性上限：SQL/PL 全栈、async I/O、broadcast state、interval join |
| **TDengine 3.3.7+** | 时序数据库内嵌流（enterprise 流处理） | 独有的**触发-计算解耦**（窗口事件 + 状态机）、**范围重算**（RECALCULATE/FILL_HISTORY）、maxDelay 兜底出数 |
| **Hazelcast 5.7** | IMDG 内嵌 Jet 计算（内存优先、低延迟） | 内存双副本状态容错、事件时间窗口在内存引擎的取舍、连接器形态参考 |
| **SeaTunnel 3.0** | 数据集成引擎（不做窗口/CEP） | 连接器生态工程化（84 连接器、OptionRule、split enumerator）、双层 2PC、CDC 框架 |

## 二、nop-stream 基线（已具备，不进入后续缺口清单）

- **建模**：XDSL `stream.xdef`（234 行）/ Java DataStream API / Delta 三入口合成 canonical `StreamModel`（fingerprint）；建图期 fail-fast 校验。
- **窗口语义**：Tumbling/Sliding/Session/Global + 8 种 trigger + 3 种 evictor + allowedLateness + 迟到 side output（Event/Processing 双时间域）。
- **CEP**：完整 Flink 风格 NFA + shared buffer + DeweyNumber + aftermatch skip + `pattern.xdef` 声明式模式；限制：CEP × RocksDB 不可用（限 Memory 后端，`nop-stream/README.md:22` 自曝）。
- **Checkpoint**：真 barrier 协议（对齐 + unaligned + channel state）、savepoint、改并行度 reshard、增量快照（SharedStateRegistry/SST）、restore 端到端 wired。
- **Exactly-once**：`STRICT_EXACTLY_ONCE` build 期 fail-fast；2PC sink（JDBC ledger 表幂等 + file epoch/manifest）；Debezium offset 进 checkpoint。
- **HA**：JDBC 租约选主 + 持久 epoch fencing（单调计数行），零 ZooKeeper；RPC 控制面 + 跨 JVM 数据面。
- **状态/背压**：Memory 全套（含 TTL）+ RocksDB keyed（key-group、rescale 重材料化）；BufferPool 信号量 + 边级流控 + TaskMailbox。
- **平台整合**：delta 定制、xpl 生成器、nop-auth 权限、ops HTTP（健康状态机/告警）。

## 三、逐系统缺口对照

### 3.1 vs Flink 2.3.0（语义完备性与生态上限）

| 能力 | Flink 2.3.0 | nop-stream | 缺口裁定 |
|---|---|---|---|
| SQL / Table / PL 全栈 | DataStream+SQL+Table+PL 统一 | SQL 仅窄子集（select/agg/tumble/sliding/session/over/distinct=否/HAVING=否/join=弱，无 changelog/retract） | 不追完整栈（vision Non-Goal）；窄补强进 roadmap |
| async I/O | `AsyncDataStream` + AsyncLookupOperator 全套 | 无通用异步算子（vision §四 Non-Goal）；静态维表 lookup join **已交付**（sql roadmap WI14，`ITableLookup` + DSL 全链），但为同步形态 | **范围收窄**：只做 lookup 的有界并发异步增量，须经 vision 裁定门 |
| broadcast state | `BroadcastProcessFunction` + checkpoint 支持 | 无 | re-audit：vision 裁定门决定，暂不默认做 |
| interval join / stream-stream join | IO + relational join 全套 | 仅 union + sideInputs | 延后，按业务触发 |
| 连接器 | 官方 SPI 100+（Kafka/ES/S3/…含 exactly-once 语义分级） | 8 内建工厂 / 4 模块（source: file/message/debezium-cdc/batch-loader；sink: file/message/jdbc-2pc/batch-consumer），无原生 Kafka | **P0**：Kafka source offset 入 checkpoint + 2PC/幂等 sink |
| connector 工程化 | `Option`/`OptionRule` 声明式参数 + 质量分级（Source/Sink 官方认证） | registry 端点已有（productization item 19 done）、conf-validate 已有，但无 OptionRule 式声明、无成熟度分级、sample 仅 conf 而非数据采样 | **P0**：成熟度分级 + OptionRule + dry-run sample 档 |
| process function 家族 | KeyedProcessFunction/CoProcess/… timer 全套 | 有（negotiated 流控另有） | 已具备 |
| Savepoint/作业生命周期 | savepoint + stop-with-savepoint + 自动周期 | savepoint + restore + suspend/resume（语义未显式化） | **P1**：SUSPEND/IGNORE UNTREATED 类停机语义显式化 |
| 观测 | metrics 全家（busy/backpressured/idle 时间占比、watermark skew、autoWatermarkAnalysis）+ REST/SQL 查询 | ops 有 /metrics（吞吐/延迟/背压计数）+ 健康状态机；缺三件套时间占比、watermark 级联 skew、边级队列填充比曲线 | **P1**：指标三件套 + 实时曲线 |
| schema evolution | SQL DDL + pipeline 协调 | 仅事件开关（无 checkpoint 协调 + sink DDL 执行） | **P1**（=10-02 分析 G2b） |
| 异步快照/IO | state processor + snapshot 并行 | 有 | 已具备 |
| K8s/YARN、在线扩缩 | 完整 | 无 | **不跟进**（K8s = productization D-GAP defer、YARN/HPA exclude；在线扩缩 = vision §四） |

### 3.2 vs TDengine 3.3.7+（触发-计算解耦与重算语义）

| 能力 | TDengine | nop-stream | 缺口裁定 |
|---|---|---|---|
| 触发与计算解耦 | 窗口事件（OPEN/CLOSE/IDLE/RESUME）+ 状态机逐事件消费，触发逻辑可自定义 | trigger 在 WindowOperator 内部闭环，窗口事件不外发；无图级事件路由 | **P1**：窗口事件化 + XDSL sideOutput 打通 |
| 兜底出数 | maxDelay 超时强制关闭窗口出数（scopeless/scope-aware 两档延迟） | CUBE 口径的 maxDelay 是产品口径，引擎层无窗口级兜底 | 随窗口事件化一并落地（P1） |
| 范围重算 | RECALCULATE 指定时间范围重放 + FILL_HISTORY 回补，结果幂等回写 | 无官方重算路径；可手工 savepoint+改起点，结果依赖 upsert sink | **P1**：重算模式（时间范围重放 + 回补作业形态） |
| 滑动窗口高效 | 滑动对齐 + 增量维护 | frame/deduct 两阶段聚合未做（现为按窗口重复计算） | **P1 性能组**：JMH 基准先行，达标才落 |
| 水位传播节流 | 水位 punctuation 按节流间隔批量下发 | watermark 逐条随记录下发（AR-06 已知 xpl 生成器恒空） | **P1 性能组**：节流间隔配置 + AR-06 修复 |
| 变更表 / 连续查询 | change table、CQ 定时降采样 | 无（CDC + tumble 可近似） | 不跟进，文档声明近似做法 |
| 库内嵌定位 | 流在时序库内，单体运维 | 平台内嵌，DB 依赖为租约/checkpoint 存储 | 各自定位差异，无缺口 |

### 3.3 vs Hazelcast 5.7（内存引擎与数据面）

| 能力 | Hazelcast 5.7 | nop-stream | 缺口裁定 |
|---|---|---|---|
| 状态容错 | 分区副本 + 状态双副本（机故障级）+ 集群级 snapshot | checkpoint 语义（进程故障级），无双副本 | 不跟进（定位差异）；RocksDB/Memory checkpoint 已覆盖主场景 |
| 事件时间窗口 | 事件时间 + watermark 支持（默认 ingestion-time），session grace | 全套（Event/Processing 双域 + allowedLateness） | nop 反超 |
| 2PC/连接器 | JDBC 2PC join、Kafka/JDBC/HDFS/Mongo/ES 连接器 | JDBC 2PC ledger、file epoch；连接器更少 | 并入连接器生态 P0 |
| 统一批流 | 同一 pipeline 批流统一 + SQL + IMDG 查询 | 批仅 snapshot 数据回放（FILL 模式待 WI） | 批模式并入重算 WI（P1），不追统一批流平台 |
| 滚动升级/CP 子系统 | rolling upgrade + CP subsystem（Raft） | epoch fencing + DB 租约；无滚动升级 | 滚动升级暂不跟进，登记 follow-up |
| 网络数据面 | IMDG credit-based 分布式全量背压 | DB/Pulsar/Kafka 数据面，DB 通道无生产者背压 | **P0**：DB 数据面生产者背压（仅 DB 通道 credit 式分片限流）；不自建全量 Netty credit 数据面（vision §四「复制 Flink Runtime 结构」禁入） |

### 3.4 vs SeaTunnel 3.0（数据集成与连接器工程化）

| 能力 | SeaTunnel 3.0 | nop-stream | 缺口裁定 |
|---|---|---|---|
| 连接器规模 | ~84 连接器 / 2644 文件 | 8 工厂 / 4 模块 | 纯工程量（10-02 分析 G1）；legacy SourceFunction 已裁定冻结（`docs-for-ai/03-modules/nop-stream-connectors.md` 两代章节），但存量 3 个 legacy source 未迁 FLIP-27，**迁移波次**是扩生态前置 |
| split/枚举协议 | SourceSplitEnumerator（分片枚举 + restore） | FLIP-27 Source/SplitEnumerator/SourceReader 已就位 | 已具备新代；缺**倾斜感知分配与限速** |
| 连接器开发工具 | connector-dev toolkit + 模板 | 无 | **P0**：OptionRule + dry-run sample 档承接 |
| CDC | 框架级 chunk 分布式分发 + 低/高水位合并 + 断点续传 | Debezium 3.7 嵌入式（cdc-roadmap WI3/4 并行快照已落）；框架级 chunk 跨进程分发无 | 保留缺口登记；schema evolution 协调进 P1 |
| 类型/元数据 | RowKind + CatalogTable + 20+ 元数据类 | `StreamRecord<T>` 无 RowKind/tableId，CDC 语义靠消息体约定 | **结构性前置**（10-02 G3）：CatalogTable 级抽象 |
| 双层 2PC | writer 2PC + aggregated committer | checkpoint epoch 2PC（ledger 幂等） | nop 更强，无缺口 |
| 生产化 | Zeta 引擎 + K8s + UI | 入口在 test sources、无 K8s | 入口进 P0；K8s **defer**（productization D-GAP P-REQ-15，revisit 条件维持）、YARN/HPA exclude |
| 批量/SaveMode | 批 + 自动建表 | 无自动建表 | 不跟进（平台已有 ORM/DDL 能力，按需组合） |

## 四、改进建议清单（roadmap 输入）

### P0（连接器生态与生产化硬缺口）

1. **Kafka 原生 exactly-once**：source offset 进 checkpoint + 事务/幂等 sink。
2. **生产进程入口**：`JobCoordinatorMain`/`TaskManagerMain` 出 test sources，main-scope 打包启动。
3. **连接器工程化**：成熟度分级进 registry + OptionRule 式参数声明 + conf-validate dry-run sample 数据档。
4. **连接器 API 两代归一**：存量迁移波次（冻结裁定已在 connectors.md，3 个 legacy source 迁 FLIP-27 是扩生态前置债）。
5. **Source 限速 + 倾斜感知 split 分配**（rate limiting rows/bytes + skew-aware enumerator）。
6. **DB 数据面生产者背压**：DB 通道 credit 式分片限流生产者。

### P1（语义与可观测补强）

7. **窗口事件化 + maxDelay 兜底出数 + sideOutput 图级路由**（TDengine 窗口事件/状态机面）。
8. **范围重算 + FILL_HISTORY 回补**（复用 savepoint/upsert sink 幂等）+ SUSPEND 停机语义显式化。
9. **维表 lookup 异步增量**（同步静态 lookup 已由 sql roadmap WI14 交付）：有界并发异步模式 + 物化刷新策略——须过 vision 裁定门。
10. **CatalogTable/RowKind 元数据抽象**（生态扩张的结构性前置）。
11. **CDC schema evolution 协调**（checkpoint 阻塞协调 + sink DDL 或 fail-fast 裁定）。
12. **观测三件套**：busy/backpressured/idle 时间占比、watermark 级联 skew、边级队列填充比 + 实时曲线。
13. **性能组**：水位 punctuation 节流、滑动窗口 frame/deduct + 两阶段聚合（JMH 基准先行）。
14. **可查询状态轻量版**：结果即状态模式 + REST keyed state 只读接口（Hazelcast/Ignite 参照，不追通用 queryable state）。
15. **checkpoint 对象存储/S3 后端**（10-02 G4d）。

### P2（裁量项）

16. **SQL 窄补强**：DISTINCT/HAVING + lookup 异步接线；changelog/retract 维持不做并文档声明终值语义（supersede 既有 DISTINCT fail-fast 钉码须显式声明）。
17. **Schema Registry / Avro 格式层**对接（format 层，不内置 registry）。
18. **维表本地物化 + lookup 缓存**（Hazelcast 参照的分层）。
19. **broadcast state 再裁定**（vision §七 #G36 永久排除现行有效，经 vision 裁定门按先例重启）。
20. **CEP × RocksDB**（工程债，follow-up 晋级）。

### 不跟进（有明确裁定依据，三类归位）

- **vision §四/#G36 类（解禁须 owner 人审）**：非等值/范围/广播双流 join、完整 Flink SQL/Table/PL 栈、PB 级状态、复制 Flink Runtime 结构（Netty/credit 式全量数据面）、在线/自动 reshard、通用异步算子、BroadcastState。
- **productization D-GAP 类**：YARN/HPA/RuntimeTopology exclude；K8s defer（revisit 条件维持，非永久排除）。
- **analysis-scope 排除类**：双副本内存状态容错、SaveMode 自动建表、变更表/连续查询、框架级 CDC chunk 跨进程分发（cdc-design 既有 defer）、滚动升级。

## Conclusion

nop-stream 的差异化优势在**声明式建模（XDSL/Delta）+ CEP + 窗口触发演进全套 + DB 租约 HA**，对 Flink 是"语义子集但工程独立"，对 TDengine/Hazelcast/SeaTunnel 在窗口与 exactly-once 深度上反超；缺口集中在连接器生态与工程化、Kafka 原生 exactly-once、生产入口、触发-计算解耦与重算语义、观测指标三件套。上述 P0/P1/P2 清单经裁定后由 `ai-dev/backlog/nop-stream-multi-engine-comparison-roadmap.md` 按依赖组织为开发序列；裁定门（go/defer/exclude，含 async lookup 与 broadcast state 两条 vision Non-Goal 的再裁定）是 roadmap 的 WI0。

## Open Questions

- [ ] CatalogTable/RowKind 抽象落点：引擎级 `StreamRecord` 升级，还是仅 CDC 连接器层消息信封（10-02 分析遗留 OQ，影响 WI 实施深度）？
- [ ] 通用异步算子与 broadcast state 是否解除 vision Non-Goal（WI0 裁定门，须人审）？
- [ ] 连接器两代归一的存量迁移量：3 个 legacy SourceFunction 模块的工作量尚未逐文件核实。
- [ ] Kafka exactly-once 与 nop-message-kafka（平台消息通道，at-least-once）的边界如何划分，避免双实现。
- [ ] 滑动窗口 frame/deduct 与水位节流的收益未实测，JMH 基准可能判定"不值得做"（预设了退出路径）。

## References

- `ai-dev/analysis/2026-10/2026-10-02-seatunnel-core-vs-nop-stream-gap-analysis.md`（SeaTunnel 单线深挖，G1-G5 缺口分级）
- `ai-dev/backlog/nop-stream-flink-comparison-roadmap.md`（Flink 源码级单线对比，21 项多已收口）
- `ai-dev/backlog/nop-stream-productization-roadmap.md`（P-REQ-1..28，连接器 registry/文档/验证已 done）
- `ai-dev/backlog/nop-stream-cdc-roadmap.md`（Debezium 3.7 + 并行快照透传，WI1-8 全 done）
- `ai-dev/backlog/nop-stream-r5-successors.md`（ST-12/CON-11/CC-06 等已登记 successor，本文不重复）
- `ai-dev/design/nop-stream/00-vision.md`（§四 Non-Goals 六行、§六人审决策点、§七 #G36——roadmap 经 WI0 唯一通道按先例裁定）
- `docs-for-ai/03-modules/nop-stream-connectors.md`、`docs-for-ai/03-modules/nop-stream-user-guide.md`（基线能力矩阵）
- 5 轮串行调研会话：nop-stream 基线 / Flink 2.3.0 / TDengine 3.3.7+ / Hazelcast 5.7 / SeaTunnel 3.0（`ses_ef0d533…`、`ses_ef0c746…`、`ses_ef0bcfd…`、`ses_ef0af51…`、`ses_ef0a57b…`）
