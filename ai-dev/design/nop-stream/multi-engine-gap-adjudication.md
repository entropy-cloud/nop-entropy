# 五引擎对比缺口裁定记录（WI0）

**日期**：2026-10-07
**范围**：`ai-dev/analysis/2026-10/2026-10-06a-nop-stream-vs-flink-tdengine-hazelcast-seatunnel-comparison.md` 第四节全部条目（P0×6 / P1×9 / P2×5）+ 不跟进三类清单 + 本 roadmap（`ai-dev/backlog/nop-stream-multi-engine-comparison-roadmap.md`）WI1..WI19 的范围裁定
**状态**：active（本记录是 roadmap WI0 门的裁定落档，WI1..WI19 范围引用的唯一依据）
**裁定通道**：roadmap WI0 门（owner 人审门）。授权链见 §五。

---

## 一、裁定总览（逐条三态）

### P0（连接器生态与生产化硬缺口）——全部 go

| # | 分析条目 | 裁定 | 归属 | 依据 |
|---|---|---|---|---|
| 1 | Kafka 原生 exactly-once | **go** | WI3 | P0 硬缺口（8 工厂无原生 Kafka）；与 nop-message-kafka 的边界按 §三 R1 划定 |
| 2 | 生产进程入口 main-scope 化 | **go** | WI1 | 入口在 test sources 是可部署性底线缺口（productization F-26 关注点随迁） |
| 3 | 连接器工程化（成熟度分级 + OptionRule 式参数声明 + dry-run sample 数据档） | **go** | WI4 | SeaTunnel 对标核心面；承载点为既有 SPI registry + StreamConfValidator（roadmap Framework 节），不新建注册面 |
| 4 | 连接器 API 两代归一迁移 | **go** | WI2 | 迁移范围按 §三 R3；debezium B 层例外处置按 §三 R2 |
| 5 | Source 限速 + 倾斜感知 split 分配 | **go** | WI5 | split 型 source（file 等）生效；单写入者 source typed 标注不支持（No-Silent-No-Op） |
| 6 | DB 数据面生产者背压 | **go** | WI6 | **确认为 DB 通道整形**（credit 式分片限流生产者），不属 vision §四「复制 Flink Runtime 结构 / credit 式全量跨 JVM 数据面」禁入面（roadmap Rules 7 明示二者不得混淆） |

### P1（语义与可观测补强）——8 go + 1 defer

| # | 分析条目 | 裁定 | 归属 | 依据 |
|---|---|---|---|---|
| 7 | 窗口事件化 + maxDelay 兜底出数 + sideOutput 图级路由 | **go** | WI8 | TDengine 对标核心语义；单一连贯面三子项 |
| 8 | 范围重算 + FILL_HISTORY 回补 + SUSPEND 停机语义显式化 | **go**（拆映射） | WI10（重算/回补）+ WI9（SUSPEND 语义） | 单条分析条目横跨两个 WI：重算/回补复用 savepoint/restore 与 upsert sink 幂等；SUSPEND 是语义显式化 |
| 9 | 维表 lookup 异步增量 | **go（范围收窄）** | WI11 | 范围与形态按 §三 R5——落在 vision「异步算子」Non-Goal 内，Non-Goal 本身**不解除** |
| 10 | CatalogTable/RowKind 元数据抽象 | **go** | WI7 | 落点按 §三 R4 |
| 11 | CDC schema evolution 协调 | **defer（维持）** | WI15 本周期延期 | cdc-design 既有 defer **维持**：触发条件（首个真实需求）未出现——roadmap 立项属缺口登记，不构成真实用户需求。WI15 行内「协调形态（checkpoint 阻塞 vs fail-fast）二选一」**不预裁**，随 defer 撤销时的 owner 裁定作出。WI19 按「其余 WI 完成或显式按需/延期裁定」容纳本延期 |
| 12 | 观测三件套（busy/backpressured/idle 占比 + watermark 级联 skew + 队列填充比曲线） | **go** | WI16 | 队列填充比复用既有 `nop.stream.io.channel.queue.size` gauge 推导，不重复建设直测面 |
| 13 | 性能组（水位 punctuation 节流 + 滑动窗口 frame/deduct） | **go（基准退出门）** | WI12 | JMH 基准先行：收益不足即按 Follow-up 登记「不做」裁定退出，不为勾选强上实现（roadmap Rules 6） |
| 14 | 可查询状态轻量版 | **go** | WI14 | 结果即状态模式 + ops REST keyed state 只读接口；不追通用 queryable state |
| 15 | checkpoint 对象存储/S3 后端 | **go** | WI13 | 对齐既有 `ICheckpointStorage` SPI 形态（LocalFile/Jdbc 双先例） |

### P2（裁量项）——3 go + 2 确认既有处置

| # | 分析条目 | 裁定 | 归属 | 依据 |
|---|---|---|---|---|
| 16 | SQL 窄补强（DISTINCT/HAVING + lookup 异步接线 + changelog 复审） | **go** | WI17 | 显式 supersede 既有 DISTINCT fail-fast 钉码与 sql roadmap WI16 不支持清单（supersession 声明由 WI17 交付）；不扩完整 SQL 栈（§二 A 类维持） |
| 17 | Schema Registry / Avro 格式层 | **go** | WI18 | format 层接入，不内置 registry 服务 |
| 18 | 维表本地物化 + lookup 缓存分层 | **go（折入）** | 并入 WI11 | 与 WI11 物化刷新策略同域（§三 R5） |
| 19 | broadcast state 再裁定 | **exclude（维持）** | 无新 WI | vision §七 #G36 永久排除现行有效；本裁定 = 确认既有处置，非重议。重启须按 #G36 先例 owner 裁决回写 vision 后从 WI20 晋级（roadmap Follow-up Backlog 既有登记） |
| 20 | CEP × RocksDB | **follow-up（维持）** | 无新 WI | roadmap Follow-up Backlog 既有登记（晋级条件 = 生产场景 CEP + 大状态诉求）；本裁定 = 确认既有处置 |

## 二、不跟进三类清单（写死）

> 与 roadmap Rules 7 同口径；本周期内 A/B/C 类均不得静默重立项，新证据出现时先回对比分析补录再议。

### A 类（vision §四 / #G36）——全部维持排除，本次零解除

| 项 | 状态 | 解禁通道 |
|---|---|---|
| 非等值 / 范围 / 广播双流 join | 维持排除 | owner 人审 + §四/#G36 先例回写 |
| 完整 SQL / Table / PL 栈 | 维持排除（窄子集已由 sql roadmap 交付，WI17 窄补强不越界） | 同上 |
| PB 级大规模并行 | 维持排除 | 同上 |
| 复制 Flink Runtime 结构（Netty / credit 式全量跨 JVM 数据面） | 维持排除（WI6 的 DB 通道整形不在本条范围内） | 同上 |
| 在线 / 自动 reshard | 维持排除 | 同上 |
| 通用异步算子（含 **per-record 异步 lookup 算子**） | 维持排除（WI11 物化 + 后台刷新形态在 Non-Goal 内，见 §三 R5） | 同上 |
| BroadcastState | 维持永久排除（#G36 现行有效） | 同上 |

### B 类（productization D-GAP）——维持，零状态变更

| 项 | 状态 |
|---|---|
| YARN 部署编排 | exclude（维持） |
| HPA | exclude（维持） |
| RuntimeTopology | exclude（维持；概念已退役，vision §九注记在案） |
| K8s 部署编排 | **defer 维持**（P-REQ-15 revisit 条件不变，本 roadmap 不改其状态） |
| Web 控制台（P-REQ-9） | defer 维持（ops Web 控制台等 G5 项按其既有触发条件走，roadmap Follow-up Backlog 已声明不重复立项） |

### C 类（analysis-scope 排除）——写死

| 项 | 状态 | 触发/兜底 |
|---|---|---|
| 双副本内存状态容错 | 排除（定位差异，checkpoint 已覆盖主场景） | — |
| SaveMode 自动建表 | 排除（平台已有 ORM/DDL 能力，按需组合） | — |
| 变更表 / 连续查询 | 排除（CDC + tumble 可近似，文档声明近似做法） | — |
| 滚动升级 | 不立项（epoch fencing + DB 租约下重启恢复成本已可接受） | roadmap Follow-up Backlog 已登记 |
| 框架级 CDC chunk 跨进程分发 | **cdc-design defer 维持**（现以 Debezium 3.7 A 层并行快照透传兜底） | 按 cdc-design 既有触发条件走 |

## 三、下游 WI 范围裁定（R1..R6）

### R1 — Kafka EO 与 nop-message-kafka 边界（→ WI3）

- **新增专用连接器模块**（`nop-stream-connector-kafka`）承载流处理 Kafka 连接器：source offset 入 checkpoint（REPLAYABLE）、事务/幂等 sink（复用 epoch 2PC 或 ledger 幂等），语义进 checkpoint 边界。
- **nop-message-kafka 平台消息通道维持现状**（at-least-once，`MessageSourceFunction`/`MessageSinkFunction` 后端承载语义）：面向平台消息总线场景，不做 EO 化改造。
- **边界判据**：是否需要流处理故障恢复语义（offset checkpoint / 事务提交）——需要 = 用 nop-stream-connector-kafka 连接器；仅平台消息收发 = 用 nop-message-kafka 通道。边界写入 owner doc 由 WI3 交付。
- 替代方案拒绝：在 nop-message-kafka 上叠加 EO —— 平台消息通道与流连接器语义边界混淆，双语义单实现面维护成本高于双模块清晰边界。

### R2 — debezium B 层例外处置（→ WI2）

- WI2 将 debezium-cdc source 迁移 FLIP-27 后，B 层表路由由 **split 机制自然承接**（表 = split，enumerator 分配即路由），legacy 代上的 `SubtaskShardedSourceFunction` 例外失去存在必要。
- **裁定：例外随 WI2 迁移完成撤销**；迁移完成前例外维持有效（冻结政策照旧）。撤销动作（connectors.md 冻结政策节同步）由 WI2 closure 承担。
- 迁移语义保全要求：REPLAYABLE offset checkpoint、PARALLEL 并行度、实例级 offset 隔离三项能力在新代等价成立，能力矩阵回归由 WI2 交付。

### R3 — 连接器两代归一迁移范围（→ WI2）

- **三个 legacy source 全量迁移 FLIP-27**：`MessageSourceFunction`、`DebeziumCdcSourceFunction`、`BatchLoaderSourceFunction`。
- 语义保全基线：message 保持 AT_LEAST_ONCE 声明（语义由后端承载，无 offset checkpoint）；debezium 保持 REPLAYABLE + 并行；batch-loader 保持 AT_LEAST_ONCE seek 续读（确定性遍历前提 + typed skip shortfall 拒绝不变）。
- 迁移完成后 `SourceFunction` legacy 代在连接器面终结（core 内核 `SourceFunction` 函数接口本身属算子 API 面，不在本裁定范围）。

### R4 — WI7 元数据落点（→ WI7）

- **裁定：StreamRecord 引擎级可选变更元数据**（RowKind + tableId），非 CDC 层消息信封。
- 理由：WI15（schema evolution 协调）与生态 sink 需要**引擎级**表标识与变更语义；信封方案会把 CDC 语义锁死在单连接器层，生态扩张时每个 sink 各自解析信封，欠账加深。
- 兼容约束：元数据为**可选**字段，缺省 = INSERT-only append 语义，现有全链路零行为变化；非 CDC-aware sink 消费带非 INSERT 变更语义的记录流 = typed fail-fast（No-Silent-No-Op），不静默降级。
- 本落点不触犯 §六决策点 #1（StreamModel 核心结构）：StreamRecord 是运行时记录载体，非 StreamModel/Transformation/StreamComponents 结构。

### R5 — WI11 形态与范围（→ WI11）

- **裁定：复用既有 lookup join 路径，不新增异步算子**。vision §四「异步算子」Non-Goal **维持不变、零解除**。
- WI11 交付形态 = 既有同步 lookup join + **维表本地物化** + **后台有界并发刷新**（TTL/失效策略）：per-record 处理保持同步（读本地物化视图），异步性收敛在物化刷新层（函数内部后台资源，不改算子线程模型），有界并发约束施加于刷新池。
- roadmap WI11 行 Deliverable 中的「形态裁定记录」由 WI11 plan **引用本节**满足，WI11 不重开形态问题；「有界并发异步模式」按本节解释为后台刷新的有界并发。
- 未来若出现 per-record 异步 lookup 算子的真实需求：按 §四 解禁通道（owner 人审 + 先例回写）另议，不在本周期内。

### R6 — WI15 defer 处置（→ WI15 延期）

- **裁定：cdc-design schema evolution DDL 协调 defer 维持**。触发条件（首个真实需求）未满足——对比分析与 roadmap 立项是缺口登记，不是真实用户需求。
- WI15 **本周期显式延期**（非取消）：WI19 按「其余 WI 完成或显式按需/延期裁定」容纳；WI15 的前置闸门（WI0 撤销 defer）在本周期不触发。
- WI15 行内「协调形态（checkpoint 阻塞 vs fail-fast）二选一」与「sink 侧 DDL 应用或显式拒绝」**不预裁**，随 defer 撤销时的 owner 裁定作出（届时按 §三 R4 的引擎级表标识承接实施）。

## 四、与既有裁定文档的关系

- `00-vision.md`：§四 六行与 §七 #G36 零改动；§七裁决记录新增本裁定条目（确认性记录，边界无变更）。
- `cdc-design.md`：schema evolution defer 维持，零改动。
- `docs-for-ai/03-modules/nop-stream-connectors.md`：Source API 冻结政策节追加「B 层例外随 WI2 迁移撤销」注记；能力矩阵本周期内的进一步更新归属各实施 WI（connector 面单一事实源不变）。
- `ai-dev/backlog/nop-stream-productization-roadmap.md`：D-GAP 与 F 表零改动（F 表销号归属 WI1/WI19 closure）。
- roadmap 本体：头部 v3 版本注记指向本记录；WI 行内文零改动（Rule 3）。

## 五、授权链

- 本裁定由用户 2026-10-07 对 roadmap 的执行指令授权（「建立一个 worktree，执行 nop-stream-multi-engine-comparison-roadmap 直到彻底完成，每个工作项按照 plan guide 拟制计划完成，每完成一个计划自动提交一次」，ZCode session 载体；指令上下文记于 `ai-dev/logs/2026/10-07.md`）。
- 全部裁定方向与 roadmap 既有文本预设方向一致：**零推翻既有裁定、零解禁、零 defer 撤销、零 D-GAP 状态变更**。WI0 门的人审保护对象是「AI 不得单方面解除 vision/D-GAP/defer 边界」——本裁定未发生任何此类边界变更，故无需 owner 确认记录；未来任何边界变更（per-record 异步 lookup、broadcast state 重启、schema evolution defer 撤销、K8s defer 变更）均须按既有通道 owner 人审另议。
- owner 复核点：若 owner 对 R1..R6 任一范围裁定有异议，修订本记录并在 roadmap 留版本注记，受影响 WI 的承载 plan 须相应修订后方可执行。
