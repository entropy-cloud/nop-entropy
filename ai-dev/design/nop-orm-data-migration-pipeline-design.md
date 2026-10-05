# nop-orm 数据搬迁管道编排设计

**日期**：2026-10-05（更新于 2026-10-06，评审修订 v2：B1 位点时序契约 / M1 多 sink 拓扑前置项 / M2 切换收敛门 / M3 写停止关 / M4 密钥一致约束 / m1-m6）
**范围**：搬迁编排层（新设计，随 roadmap WI9 实施）；数据面复用 `nop-batch`、`nop-stream/nop-stream-connector-debezium`、`nop-stream/nop-stream-connector-jdbc`；路由面依赖 `nop-dao`/`nop-orm` 分片配置（plan 2320 已落地）
**状态**：草案（roadmap `ai-dev/backlog/nop-persistence-middleware-roadmap.md` WI9；Deliverable = 本设计，作业实现由 WI9 承载 plan 定义）
**灵感来源**：Apache ShardingSphere kernel/data-pipeline 作业形态（InventoryTaskSplitter / IncrementalTask / DataMatch ConsistencyChecker / RateLimitAlgorithm——仅理念对标，不照抄其分布式作业协议）

---

## 一、设计结论

1. **作业模型 = 独立搬迁编排器（migration job 状态机）+ 阶段执行面委托**：全量委托 nop-batch chunk 作业，增量委托 nop-stream 流作业，切换与校验是编排器自带的门禁步骤。编排器只做阶段状态迁移与门禁判定，不碰数据面。
2. **位点时序契约（B1）**：PREPARE 阶段在启动全量之前钉定源位点 P0（mysql binlog file+pos / pg LSN），INCREMENTAL 从 P0 起全序重放——全量写入的快照值被重放事件按序覆盖，目标端最终态收敛到"binlog/WAL 中每 key 的最后事件"，因此全量期间源端持续写入既不丢失、也无需停写；源库 binlog/WAL 保留窗口覆盖全量时长是部署前置条件。
3. **切换 = 停写窗口内的单点配置翻转，不是数据面两阶段提交**：停写靠编排写停止关（DML 提交路径显式 fail-fast，平台新增原语），lag=0 判定以写停为前置；**恢复写入的门禁 = 全部应用实例已按新配置完成路由重建（收敛门）**，配置翻转本身不是不可回滚点——写门保持关闭期间数据面冻结，fix-forward 与回翻均无损。
4. **扩容并存期裁定：应用整体按旧策略读写，新 M 片只是搬迁目标**——路由层不引入双策略并存、双写、读旧写新语义；并存期在物理数据面（直读直写），不在路由面。
5. **增量捕获 = Debezium CDC（v1 白名单 mysql/postgres）**，复用既有 `debezium-cdc` source + `jdbc-2pc` sink（UPSERT_DELETE）底座；应用侧双写拒绝。白名单外数据库 v1 只支持"停写全量"形态。**M>1 目标分片的 fan-out 流图依赖 nop-stream 多 sink 管线能力，属未验证能力，列为 WI9 实施前置验证项**；验证不通过的回退形态 = 每目标分片一个单 sink 链作业。
6. **搬迁数据面完全在物理表层（JDBC 直读直写）**：读源不经过 ORM 路由（缺分片键 fail-fast 是业务查询契约，hash-mod 旧分片区间不可反推），写目标按新策略计算出的物理表直写 keyed UPSERT；分片键为 null 的存量行 fail-fast 并携带实体/主键/物理表定位信息。
7. **一致性校验三级（count → 抽样 checksum → 逐行 checksum）全部应用侧计算**，按物理列值规范化串做稳定 hash；**v1 显式约束搬迁前后加密密钥一致（PREPARE 门禁校验）**，加密列在同 key 前提下按密文原样比对，脱敏展示字段是 compute 派生属性、不进入比对面。校验门禁只在"写停 + lag=0"后执行。
8. **使用契约**：编排器落新子模块 nop-persistence/nop-orm-migration（依赖四面单向收束）；编排配置 = JSON 配置串 `nop.orm.migration`（含写停止关）；错误码族 `ERR_MIG_*` 登记编排模块、写门执行错误登记 OrmErrors；指标族 `nop.orm.migration.*`；v1 不建新控制台 UI，全量阶段任务天然出现在 nop-batch 既有任务管理面，编排 job 状态提供只读 GraphQL 查询。

## 二、背景与动机

### 2.1 roadmap 定位

WI9（`ai-dev/backlog/nop-persistence-middleware-roadmap.md` M4）承载两个场景：**分片扩容**（N→M 片，依赖 WI6/plan 2320 策略模型）与**同构机房迁移**（整库搬到另一组数据源，无策略依赖）。`ai-dev/design/nop-orm-sharding-strategy-design.md` §3.5 已裁定：扩容是数据搬迁问题而非 DDL 问题——auto-upgrade 只建新表（N 表展开见该设计 §3.5/§6.3 与 `ai-dev/plans/2320-orm-sharding-strategy.md`），存量数据迁移归本设计。

对标参考（仅理念）：ShardingSphere data-pipeline 将一次迁移组织为 inventory 拆分（按唯一键区间 split，`InventoryTaskSplitter`）+ 增量任务（`IncrementalTask`，在全量位点之后应用）+ 一致性检查（`PipelineDataConsistencyChecker`，CRC32/DataMatch 两族）+ 限流算法 SPI（`JobRateLimitAlgorithm`）的作业族。nop 的对应物不需要其分布式作业协议（注册中心/job item 调度），因为 nop 已有两个各自断点语义完备的执行面（nop-batch、nop-stream），缺的是把它们按四阶段串起来的编排层、阶段间位点时序契约与切换协议。

### 2.2 底座事实（断点/幂等语义经 live code 核实；多 sink 拓扑成熟度另行核实并降级，见 §4.3）

**CDC 源**：`DebeziumCdcSourceFunction`（`nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceFunction.java`）实现 `CheckpointedSourceFunction`（`:59-61`），CDC offset 经 `NopStreamOffsetBackingStore` 进 nop checkpoint operator state（键 `cdc-offsets`，`:70`、snapshot `:350-365`、restore `:369-399`），一致性语义 `REPLAYABLE`（`:321-323`）；**首跑路径清空 offset store 后由引擎按其 snapshot.mode 默认行为开始（`:369-385`、`:404-410`），不从外部指定位点开始——"首跑 offset 种子化为编排器钉定的 P0"是本设计对 offset 存储初始化提出的契约级新增要求（§4.2）**；并行 B 层按表名 hash 路由 subtask（`validateParallelism` `:438-467`），并行时 schema history 按实例名隔离（`:661-677`）。connectorType 白名单 mysql/postgres、offset 真相源契约见 `ai-dev/design/nop-stream/cdc-design.md` §3.2/§3.4；注册名 `debezium-cdc`（`nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceConnectorFactory.java:31`）。

**exactly-once sink**：`JdbcTwoPhaseCommitSink`（`nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java`）每个 checkpoint epoch 一个 JDBC 事务，数据批 + ledger 行（复合键 `sink_namespace+epoch_id+subtask_id`）同 commit（`:627-706`），ledger 查重即幂等提交守卫（`:649-665`）；`dmlMode = INSERT|UPSERT|UPSERT_DELETE`（`:107-133`），keyed 模式批内同 key last-write-wins 保序折叠（`:920-981`），方言 upsert 语句由 `UpsertStatements.buildNativeUpsertSqlText`（`nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/UpsertStatements.java:92`）生成，**仅 mysql/mariadb/postgresql（H2 供测试，`:1052-1064`）——目标端方言边界显式成立（m2）**。注册名 `jdbc-2pc`（`nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSinkConnectorFactory.java:34`）。**注意：sink 的 `tableName` 是 final 单表字段（`:165`），不支持按行动态路由目标表。**

**多 sink 拓扑成熟度（M1，经评审核定后复核）**：nop-stream 的多 sink 管线是**未验证能力**——编译管线检查单「多 sink 管线正确编译（多个 JobVertex）」「跨 Task 数据交换端到端正确」均未勾选（`ai-dev/design/nop-stream/component-roadmap.md:240`、`:242`），编译测试面（`nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/graph/TestStreamGraphGenerator.java`，36 用例）未覆盖多 sink 部署图；`SideOutputDataStream.sink`（`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/SideOutputDataStream.java:72-98`）把 SinkFunction 注册为**进程内侧输出消费者**（javadoc 明示"delivered where it is produced, not through a deployed sink vertex"），不作为部署顶点、不参与 checkpoint/2PC，其下游图变换显式抛 unsupported（`:111-116`）——**不能承载 `JdbcTwoPhaseCommitSink` 的 epoch 生命周期**。

**nop-batch**：作业模型是 XDef 模型（`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/batch.xdef:9-19`，根属性 batchSize/concurrency/rateLimit/jitterRatio/startLimit/asyncProcessor 等），非 XMind。读写面：`orm-reader`/`jdbc-reader`（`:125-136`，均支持 `partitionIndexField` 分区过滤）、`orm-writer`/`jdbc-writer`（`:186-203`）、loader `dispatcher` 分区拆分（`:80-85`）、retryPolicy/skipPolicy（`:41-50`）。执行器 `BatchTask`（`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/impl/BatchTask.java`）按 concurrency 起 chunk 循环（`:134-137`），任一 chunk 失败 fail-fast 取消其余线程（`:228-237`），每 chunk 后持久化状态（`:302-303`）。断点存储 `DaoBatchStateStore`（`nop-batch/nop-batch-dao/src/main/java/io/nop/batch/dao/store/DaoBatchStateStore.java`）：`NopBatchTask` 行按 `(taskName,taskKey)` 唯一，条件 UPDATE 启动闸门原子裁决并发启动（`:142`、`:187-194`），重启恢复 completedIndex 等计数并置 `recoverMode`（`:174-190`）；任务状态字典 created/running/paused/completed/failed/cancelled/killed（`nop-batch/nop-batch-meta/src/main/resources/_vfs/dict/batch/task-status.dict.yaml`）。orm 读分页是 keyset 游标：`OrmQueryBatchLoaderProvider.load` 以 `dao.findNext(lastEntity, ...)` 推进（`nop-batch/nop-batch-orm/src/main/java/io/nop/batch/orm/loader/OrmQueryBatchLoaderProvider.java:123-131`；`IEntityDao.findNext` `nop-persistence/nop-dao/src/main/java/io/nop/dao/api/IEntityDao.java:276`）。限流旋钮：`BatchTaskBuilder.rateLimit`（`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/BatchTaskBuilder.java:237-238`）落到 `RateLimitConsumer`（`:419-420`）。

**同族先例**：`EncAssistedBackfiller`（`nop-persistence/nop-orm/src/main/java/io/nop/orm/enc/EncAssistedBackfiller.java`）确立"分页读 + 批量写、直接走 JDBC 不经 ORM session、幂等重算覆盖"的存量数据工具先例（类契约 `:31-52`，主键序 offset 分页 `:100-110`）；并显式声明"建议离线执行""分片实体仅回填缺省 querySpace"（`:51`）——搬迁管道是它的多分片、带增量、带校验的一般化。

**分片策略面（2320 已落地）**：策略配置为 JSON 配置串 `nop.orm.shard`（`DaoConfigs.CFG_ORM_SHARD`，`nop-persistence/nop-dao/src/main/java/io/nop/dao/DaoConfigs.java:83`），`ConfigShardSelector.init()` 启动期解析注册表（`nop-persistence/nop-dao/src/main/java/io/nop/dao/shard/ConfigShardSelector.java:66`），SPI 终态含 `getShardNames`/`getShardTableName`（`:171`、`:179`，供 DDL 展开消费）；DDL N 表展开入口 `ShardDdlHelper.shardTableNames`（`nop-persistence/nop-orm/src/main/java/io/nop/orm/ddl/ShardDdlHelper.java:45-56`），三建表调用点（`DataBaseSchemaInitializer` / `OrmDbDiffer`+`DataBaseUpgrader` / `AutoTestCaseDataBaseInitializer`，见 `ai-dev/plans/2320-orm-sharding-strategy.md` Current Baseline #5 与 Phase 3）。运行期路由 `GenSqlTransformer.collectShardSelections(sql, paramValues)`（`nop-persistence/nop-orm/src/main/java/io/nop/orm/sql/GenSqlTransformer.java:112`），缺分片键 fail-fast `ERR_ORM_SHARD_CONDITION_MISSING`（`:141-157`）。

**写停原语现状（M3 前提）**：平台无写停止原语——`DaoConfigs`/`OrmConfigs` 无任何 write-gate 类配置；plan 2319 的读写分离是读路由（从库故障 fail-fast），无写禁止开关。写停止关是本设计新增的平台原语（§4.3）。

**其他**：nop-job 调度入口 `IJobScheduler`（`nop-job/nop-job-api/src/main/java/io/nop/job/api/IJobScheduler.java:27`）；`NopBatchTask` 已有 `flowStepId`/`flowId` 挂钩字段（`nop-batch/nop-batch-dao/src/main/java/io/nop/batch/dao/entity/_gen/_NopBatchTask.java:65`、`:69`）——编排器与批次任务行的关联点现成。nop-batch 与 nop-stream 的互适配（BatchLoaderSourceFunction 等）见 `ai-dev/design/nop-stream/connector-design.md` §3。

## 三、范围与 Non-Goals

**In scope**：nop 平台自身 useShard 实体的分片扩容（N→M）与同构机房迁移（同一 ORM 模型、同方言数据库家族，换数据源落点）的作业编排：全量搬迁、增量追平、一致性校验、路由切换的契约与失败语义。**目标端方言边界（m2）**：keyed UPSERT/UPSERT_DELETE 依赖 native upsert，目标数据库限 mysql/mariadb/postgresql（H2 仅为测试通道）——"同构机房迁移"隐含源目标同方言，但目标侧仍受 native upsert 支持面约束，目标为其他方言时 v1 无增量+幂等搬迁路径（显式失败，不静默降级）。

**Non-Goals**：

1. **通用异构数据库迁移工具**：不做 ShardingSphere 式迁移矩阵（80+ 连接器生态、异构 schema 转换、schema evolution DDL 协调）；CDC 能力边界即 `ai-dev/design/nop-stream/cdc-design.md` §3.2 白名单（mysql/postgres）。
2. **双写 / 灰度切流 / 无窗口在线切换**：切换采用停写窗口协议（§4.3），不做应用双写改造与按比例读切流。
3. **路由层双策略并存语义**：新旧策略的交替是一次配置事件，不是路由引擎特性（§4.3）。
4. **白名单外数据库的增量搬迁**：无 binlog/WAL CDC 的数据库 v1 只支持停写全量形态，显式声明而非静默降级。
5. **自动扩容触发与容量监视**：扩容由人工发起，管道不监视数据量。
6. **Web 控制台 UI**：观测走既有面（§4.8）。
7. **搬迁中换 key / 重加密（M4）**：v1 约束搬迁前后加密密钥一致；密钥轮换/重加密是独立的密钥管理工具职责，与搬迁编排正交。不同 key 下密文比对会恒真放过数据错误（校验给绿灯），因此该约束必须前置校验而非留白（§4.6）。

## 四、核心设计

### 4.1 作业模型：四阶段状态机 + 执行面委托（裁定 1）

一次搬迁作业 = 编排器持有的显式状态机，五个阶段、一条正常出路与一条中止出路（m4：任何前置门禁失败或人工中止，均可从 PREPARE/FULL 早期直接进入 ABORT）：

```mermaid
stateDiagram-v2
    [*] --> PREPARE: 发起作业（钉定源位点 P0）
    PREPARE --> FULL: 目标表就绪 + 密钥一致 + P0 已钉定
    PREPARE --> ABORT: 前置校验失败 / 人工中止
    FULL --> INCREMENTAL: 全量完成
    FULL --> ABORT: 失败 / 人工中止
    INCREMENTAL --> VERIFY: 写停止关关闭 + lag=0 门禁
    INCREMENTAL --> ABORT: 失败 / 人工中止
    VERIFY --> SWITCH: 校验通过
    VERIFY --> ABORT: 校验失败
    SWITCH --> DONE: 配置翻转 + 全实例路由重建收敛门满足 + 恢复写
    ABORT --> [*]: 旧配置未动，恢复写即回滚
    DONE --> [*]
```

阶段与执行面的委托关系：

| 阶段 | 执行面 | 断点机制 | 幂等语义 |
|---|---|---|---|
| PREPARE 目标校验 + 钉定 P0 | 编排器（目标物理表存在性/结构比对复用 auto-upgrade 产物；密钥一致性校验；源位点 P0 钉定） | 编排 job 状态行 | 校验幂等；P0 钉定重复执行取新值 |
| FULL 全量 | nop-batch 作业：物理 JDBC 直读旧分片（keyset/offset 分页）→ keyed UPSERT 写目标 M 片 | `DaoBatchStateStore`/`NopBatchTask`（启动闸门 + 计数恢复） | keyed UPSERT 重放安全 |
| INCREMENTAL 增量 | nop-stream 作业：`debezium-cdc` source（**首跑 offset 种子化为 P0**）→ shard-router → `jdbc-2pc` sink × M（UPSERT_DELETE） | checkpoint operator state（offset）+ epoch ledger | 2PC + upsert/delete 幂等 |
| VERIFY 校验 | 编排器门禁步骤（§4.6） | 编排 job 状态行 | 只读，可重复 |
| SWITCH 切换 | 编排器 + 配置翻转 + 收敛门（§4.3） | 编排 job 状态行 | 翻转到同一目标配置幂等 |

**裁定：不用 nop-batch 单一作业表达四阶段**——batch 是单任务 chunk 读写循环模型（`batch.xdef` 无阶段概念），增量追平是长驻流式作业不是 chunk 终止作业；两者断点语义异构（`NopBatchTask` 行 vs checkpoint operator state），强行统一只会造出第三种残缺恢复语义。**也不用 nop-wf 表达编排**——工作流的持久化/恢复模型为人工审批语义设计，机器状态机的门禁判定（lag=0、校验通过）不是审批节点；编排器仅为全量阶段 batch 任务打 `flowId`（字段已存在，`nop-batch/nop-batch-dao/src/main/java/io/nop/batch/dao/entity/_gen/_NopBatchTask.java:69`）建立任务行关联，v1 不引入工作流定义。**切换不做数据面两阶段提交**——XA/2PC 是平台级 non-goal（roadmap Current Baseline 硬裁定；CDC sink 的 epoch 2PC 是流内幂等机制，不是跨库分布式事务）；切换的原子性语义由"单点配置翻转 + 收敛门"保证（§4.3）。

编排 job 状态本身持久化于独立的状态行（承载归 §4.8 模块归属裁定），语义契约：**状态迁移只能由编排器推进，且每一步迁移前检查前置门禁**；编排器重启后按状态行从最后完成的阶段继续，绝不隐式回退阶段。

### 4.2 位点时序契约：P0 钉定与全序重放（裁定 B1，选评审建议 (a)）

FULL 委托 nop-batch（CDC 流外）、INCREMENTAL 委托 debezium-cdc，两者的正确性由位点时序契约闭合：

```
1. PREPARE：启动 FULL 之前，编排器钉定源位点 P0
   （mysql: SHOW MASTER STATUS 的 binlog file+pos / GTID 集合；pg: pg_current_wal_lsn()）
2. FULL：nop-batch 全量复制（源端业务写不停）。
   写入值 = 各行在读取时刻的版本（可能已含 P0 后变更），无需与 P0 快照一致。
3. INCREMENTAL：CDC 源首跑 offset 种子化为 P0，从 P0 起按 binlog/WAL 序全序重放。
   每 key 的重放事件依次覆盖目标端该 key 的现值（keyed UPSERT/UPSERT_DELETE），
   目标端最终态 = binlog/WAL 中该 key 的最后一个事件 —— 与 FULL 写入的快照值新旧无关：
   FULL 写旧值会被后续重放覆盖，FULL 写新值会被同 key 后续事件再次覆盖到更新版本。
   收敛性由此结构性成立，FULL 与源端写入无需停写、无需 stop-the-world。
4. 部署前置条件：源库 binlog/WAL 保留窗口（mysql binlog_expire_logs_seconds /
   pg wal_keep_size 或 replication slot）必须覆盖 P0 → FULL 完成 → CDC 建立消费的时长
   加余量；不满足则位点被 purge，INCREMENTAL 无法启动。PREPARE 输出该前置条件确认
   （v1 为人工确认项）。
```

对底座的契约级要求：**CDC 源首跑 offset 必须可种子化为编排器钉定的 P0**。现状首跑路径清空 offset store、由引擎按 snapshot.mode 默认行为开始（`DebeziumCdcSourceFunction.java:369-385`、`:404-410`），不满足该契约；种子化机制（对 `NopStreamOffsetBackingStore` 首跑内容注入 P0 的 binlog 位点，配合 `snapshot.mode=never`）归实施 plan 裁定，属契约要求的实现面。

**拒绝方案 (b)（全量改由 Debezium initial snapshot 承载）**：与裁定 1 冲突且代价更高——全量的断点续传（`DaoBatchStateStore` 启动闸门/计数恢复）、限流（batch `rateLimit`/`concurrency`/`jitterRatio`）、分区并行（`partitionIndexField`/`dispatcher`）与 nop-batch 控制台可见性全部改绑 Debezium 引擎内部机制；且 relocate 场景在 CDC 白名单外数据库上无全量面可用（Debezium 承载全量 = 白名单外场景连停写全量都失去承载），故 (b) 被拒。

### 4.3 扩容场景（N→M）：配置翻转切换协议（裁定 2 + M2 + M3 + m6）

与 2320 策略配置的关系：`nop.orm.shard` 是部署环境属性（`ai-dev/design/nop-orm-sharding-strategy-design.md` §3.1），扩容前后是同一配置面的两个版本。**并存期（搬迁进行中）应用整体按旧策略读写，新 M 片只是搬迁管道的写入目标**——路由引擎不知道新策略的存在。切换协议（伪代码，语义契约）：

```
VERIFY → SWITCH 门禁（停写窗口内顺序执行）：
1. 写停止关闭（nop.orm.migration.write-gate = closed）：
   源端所有 DML 在 ORM 提交路径显式 fail-fast（ERR_ORM_MIGRATION_WRITE_GATE_CLOSED，
   携带实体与操作），读路径不受影响。写停止关是 lag=0 判定与无损回滚的前置契约——
   写不停则位点静止无意义、数据面也不存在可冻结的"无损回滚点"。
2. lag=0 判定（m6，两个观测量同时满足，观测窗口 = incremental.quiesce-seconds 静默期）：
   a. CDC offset 在静默期内无前进（复用 cdc-design §3.7 的 offset lag 指标族）；
   b. 目标端 epoch ledger（stream_epoch_ledger_v2）在静默期内无新增提交行。
3. 执行一致性校验（§4.6，count 必选 + 抽样/逐行按配置）。
4. 校验通过 → 将 nop.orm.shard 整体翻转为新策略配置（单一配置点，原子生效）。
5. 收敛门（M2）：等待全部应用实例按新配置完成路由重建。
   观测面：实例级 readiness ack——v1 最小形态 = 运维在编排器门禁处人工确认
   "全部实例已重建/重启完成"，后续以实例配置版本心跳自动化。
   v1 部署假设：单写点或可全量停写的部署（编排器与应用同进程的单体形态为主场景）。
6. 收敛门满足 → 开启写停止关 → 恢复写入（此后业务读写全部落新 M 片）。
   【不可回滚点 = 第 6 步】。
7. 校验失败或收敛门期间实例重建失败 → 写停止关保持 closed，数据面冻结
   （源无新写、目标无新写），两条无损出路：
   fix-forward（修复失败实例的重建后再开门）或 回翻（nop.orm.shard 翻回旧配置 +
   全实例重建回旧策略 + 开门——数据面未变，回翻可行）。
```

关键裁定与理由：

- **恢复写入以收敛门为门禁（M2）**：`ConfigShardSelector.init()` 是启动期一次解析（`ConfigShardSelector.java:66`），v1 路由重建靠应用重启/重载——若在部分实例仍是旧配置时恢复写，旧配置实例会把写入落进旧 N 片，成为永远不被读取的孤儿写。因此"允许恢复写入"的门禁 = 全部实例重建完成（收敛门），不是配置翻转动作本身。
- **拒绝写停止关省略（纯 lag=0 + 校验）**：lag=0 与校验结果的有效性都以"源端无新写"为前提；无写停止原语时（现状核实：`DaoConfigs`/`OrmConfigs` 无任何写停配置）两者都是不可判定的软条件。写停止关与 2320 的 fail-fast 哲学同构：门禁不满足时显式失败，绝不静默放行。
- **拒绝双写并存**：路由层无双写原语（一条 SQL 单 querySpace 是硬约束，`ai-dev/design/nop-orm-sharding-strategy-design.md` §3.2），内置双写意味着新的写入通道与冲突消解语义，超出编排层边界。
- **拒绝"读旧写新"**：读按旧策略、写按新策略要求路由层按行存在性判定（新片未搬完的行必须回落旧片），该语义不存在且无法用分片算法表达（hash-mod 反推不可行）。
- **配置翻转必须原子**：不存在"部分实体旧策略、部分实体新策略"的中间态——`nop.orm.shard` 是单一 JSON 串（`DaoConfigs.CFG_ORM_SHARD`），整体替换天然满足；收敛门保证中间态不可见的另一面（实例间不一致）在恢复写之前消除。
- **目标表先行建立**：新 M 片物理表由 auto-upgrade/DDL N 表展开在 PREPARE 阶段建立并校验（2320 已覆盖，`ai-dev/plans/2320-orm-sharding-strategy.md` Phase 3），搬迁管道不建表、不改表。
- **机房迁移是本协议的退化形态**：表 1:1 映射（表名不变），"新策略配置"退化为 querySpace→数据源指向的翻转，增量阶段是无 router 的 1:1 CDC 镜像（cdc-design §六验收场景 1 即此形态），无 WI6 策略依赖——与 roadmap WI9 的依赖声明一致。

### 4.4 增量捕获选型：Debezium，不双写（裁定 3 + M1 前置项）

**裁定**：v1 增量 = Debezium CDC，复用 `debezium-cdc` source + `jdbc-2pc` sink 底座；应用侧双写被拒（侵入业务写路径、引入双写顺序/失败补偿问题，平台不可内置）。

**范围边界（诚实声明）**：CDC 白名单 mysql/postgres（cdc-design §3.2）。白名单外数据库的搬迁 v1 只有 FULL → VERIFY → SWITCH 三段（无 INCREMENTAL），停写窗口从"最终追平"提前为"覆盖全量搬迁全程"，窗口长度要求显著更高——这是显式支持面，不是可静默降级的能力。H2 仅存在于测试面（jdbc-2pc 的 H2 识别即为此留的测试通道，`nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java:1050-1064`）。

**N→M 扩容的增量流图与前置项（M1）**：源侧订阅旧 N 张物理表（`tableIncludeList`），事件经 shard-router 按新策略重算落点，fan-out 到 M 个 sink 分支：

```mermaid
flowchart LR
    S[debezium-cdc source<br/>旧 N 张物理表] --> R[shard-router<br/>从行内分片键值按新策略计算 ShardSelection]
    R --> T1[jdbc-2pc sink 分支 × M<br/>UPSERT_DELETE 到目标物理表<br/>epoch ledger 幂等]
```

- **M>1 fan-out 是 nop-stream 未验证能力，列为 WI9 实施前置验证项**：多 sink 管线编译（多 JobVertex）与跨 Task 数据交换端到端正确性在 `ai-dev/design/nop-stream/component-roadmap.md` C2 检查单（`:240`、`:242`）均未勾选，编译测试面未覆盖多 sink 部署图，且 sink 分支的 per-vertex checkpoint/2PC 对齐未经验证。**WI9 实施 plan 必须先补该验证项（或在该检查单勾选后启动）**；验证不通过的回退形态：**每目标分片一个独立单 sink 链作业**（source 全订阅源表 + filter 本分片行 + 单 `jdbc-2pc` sink）——正确性等价（每作业只写自己的目标表），代价是 M 倍源事件消费，v1 接受该代价作为回退面。
- **拒绝以 `SideOutputDataStream.sink` 承载多目标**：它把 SinkFunction 注册为进程内侧输出消费者（`SideOutputDataStream.java:72-98`，javadoc 明示不经部署顶点投递），不参与 checkpoint、无法承载 `JdbcTwoPhaseCommitSink` 的 epoch 生命周期；下游图变换显式 unsupported（`:111-116`）。
- router 的输入是 `ChangeEvent` 的 after/key image 中的分片键值，调用与 `ConfigShardSelector` 同族的策略算法计算目标 `(querySpace, shardName)`——但**只在搬迁管道内消费**，不触碰运行期路由（§4.5 边界）。
- sink 侧不改造 `JdbcTwoPhaseCommitSink` 为动态表名（`tableName` final 是其幂等/ledger 契约的一部分，`:165`），按目标分片 fan-out 出 M 个分支，每分支固定一张目标物理表；批内同 key 保序折叠（last-write-wins）与跨 epoch 位点序保证最终一致。
- 机房迁移退化为 source → 单 sink 分支（1:1 表映射），无 router，单 sink 链是已验证形态。

### 4.5 搬迁数据面：物理直读直写（裁定 5 + m3）

**全量与增量的数据面完全在物理表层**：读源不经过 ORM 路由——路由语义（缺分片键 fail-fast、按当前配置改写表名）是业务查询契约，搬迁是基础设施操作；旧策略为 hash-mod 时分片区间不可反推，逐分片按实体查询不成立；`EncAssistedBackfiller` 已确立"直接走 JDBC、不经 ORM session"的同族先例（`:46` javadoc）。写目标直接落到按新策略计算出的物理表（全量 batch 的 jdbc-writer 形态 / 增量 `jdbc-2pc` sink），不使用 ORM 运行期路由。

- **null 分片键存量行 fail-fast（m3）**：全量映射或 router 遇到分片键为 null 的行 = 类型化错误（携带实体名、主键值、源物理表名，错误族 `ERR_MIG_*`），不静默跳过也不猜测落片；处置 = 修复源数据后从断点续跑。这是 roadmap"无静默错误结果"裁定在搬迁域的直接实例。
- **一切目标端写入 keyed UPSERT/UPSERT_DELETE，不用裸 INSERT**：使"任意点重跑"结构性幂等（重放的行覆盖而非重复）——全量 chunk 重放、增量 2PC 重放、`EncAssistedBackfiller` 幂等重算三种断点语义由此统一。全量写原语与增量 sink 共用同一方言 upsert 事实源 `UpsertStatements`（`nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/UpsertStatements.java:65,92`）；**其方言支持面（mysql/mariadb/postgresql + H2 测试）即搬迁目标端的方言边界（m2，§三）**。
- **失败处置 fail-fast，无静默跳过**：全量 chunk 失败即停（`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/impl/BatchTask.java:228-237`）；增量引擎终死即任务失败（offset 不前推，`nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceFunction.java:96-105`）；校验失配即门禁拒绝。失配明细（实体、主键、段 hash）必须落到校验报告，不允许"总体一致"掩盖行级差异。
- **阶段内断点归各自执行面，编排器不管数据断点**：全量 = `DaoBatchStateStore` 的既有语义（`(taskName,taskKey)` 唯一、条件 UPDATE 启动闸门防并发重入、completedIndex 计数恢复、`startLimit`/`allowStartIfComplete` 门控）；增量 = nop-stream checkpoint（offset operator state）+ `jdbc-2pc` epoch ledger（恢复后未提交 epoch 重放，ledger 查重跳过）。
- **SWITCH 之后的回滚语义（修正原 v1 断言）**：不可回滚点不是配置翻转，而是**恢复写收敛门满足（写停止关重新开启）之后**（§4.3 第 6 步）。写门保持 closed 期间数据面冻结，fix-forward 与回翻均无损；写门开启后即正常业务运行，无回滚需求。

### 4.6 一致性校验语义（裁定 4 + M4）

**三级校验，时序门禁**：count 对比（必选）→ 抽样 checksum（比例可配）→ 逐行 checksum（可选全量）。前两级可在增量追平中作为进度观测，**但作为切换门禁的校验结果只在"写停止关闭 + lag=0"之后产出**——期间源目标都在变，中途校验结果不具备门禁资格。

**比对计算在应用侧**：逐行按主键序双游标配对（扩容场景按逻辑表聚合：源 N 片之和 vs 目标 M 片之和；逐行配对按主键跨分片定位），行值规范化串（null 专用标记、数值 BigDecimal 规范化、时间统一精度、二进制固定编码）后计算稳定 hash；按主键区间分段聚合，段 hash 汇总，失配段二分定位。拒绝 DB 侧 CRC32/checksum 函数（SS DataMatch/CRC32 两族的路径）：方言绑定、且加密列语义与源库耦合。

**加密与脱敏边界**：

| 对象 | 裁定 | 理由 |
|---|---|---|
| 加密密钥（M4） | **v1 约束搬迁前后密钥一致，PREPARE 门禁校验源/目标密码器配置一致**（同应用实例天然一致；relocate 跨环境需显式确认）；换 key 属显式 non-goal（§三.7） | 密文比对只在同 key 下有判定力；不同 key 下密文比对恒真，会把"数据搬对了但全被旧 key 加密"这类错误放行为绿灯 |
| enc 主列 | 按密文原样比对，不解密比明文（同 key 前提下） | 搬迁是字节级复制，密文一致即正确；解密比对扩大密钥面且无判定增益 |
| assisted/like 辅助列 | 作为普通物理列参与比对 | 辅助列由管道原样搬运（`EncAssistedBackfiller` 回填契约同样只落物理列） |
| 脱敏展示字段 | 不进入比对面 | 脱敏是展示层 compute 派生属性（`ai-dev/design/nop-orm-scope-and-non-goals.md` ORM 层脱敏裁定；工具面 `nop-kernel/nop-commons/src/main/java/io/nop/commons/util/MaskHelper.java`），不是物理列，没有可比对对象 |

### 4.7 限流与在线影响（裁定 6）

- **全量阶段限流 = nop-batch 既有旋钮，不新造限流器**：`rateLimit`（records/s 标量）+ `batchSize` + `concurrency` + `jitterRatio`（batch.xdef `:9-19`；限流落点 `nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/BatchTaskBuilder.java:419-420`）。拒绝 SS 式限流算法 SPI（`JobRateLimitAlgorithm`）：单消费者、无扩展需求，YAGNI；需要更细控时扩为 SPI 是加法不是破格。
- **增量阶段速率跟随源写入**，无独立限流面；sink 批分段 `maxBatchSize`（默认 1000，`nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java:87`）是写入侧既有保护。
- **源读直连主库**：CDC 本身要求主库 binlog/WAL；全量读若走从库会引入复制延迟与位点一致性的额外复杂度，v1 明确不做。在线影响的诚实边界：全量搬迁对源主库有可感知的读负载，控制手段 = rateLimit 上限 + 低峰窗口 + 分区并行摊平扫描（`partitionIndexField`/`dispatcher`，batch.xdef `:80-85`、`:125-136`），没有免费午餐。
- **大表切片语义**：按物理表逐表切片；单表内由 batch keyset/offset 分页推进。不引入 SS 式"按唯一键区间静态拆 split 再分发"的协议（那是为多节点分发设计的，nop 单机执行面直接由 chunk 并发覆盖）。

### 4.8 使用契约（裁定 7 + m5）

**模块归属（m5）**：编排器（job 状态行实体、流图构造、只读 Biz 面、编排错误码族）落**新子模块 nop-persistence/nop-orm-migration**。理由：编排器依赖 nop-dao、nop-orm、nop-batch-*、nop-stream-* 四面，落任何既有模块都会给该模块引入原本不需要的 nop-stream/nop-batch 依赖；新模块使依赖单向收束（nop-orm-migration → {nop-dao, nop-orm, nop-batch-*, nop-stream-*}），不污染既有依赖面。写停止关的执行检查点位于 ORM DML 提交入口，其错误码登记 OrmErrors（跟检查点所在层走，2320"路由与校验错误归 OrmErrors"分层先例）。

**配置形态**：编排配置 = JSON 配置串 `nop.orm.migration`，与 `nop.orm.shard`（`ai-dev/design/nop-orm-sharding-strategy-design.md` §6.1）、`nop.dao.readwrite`（plan 2319）同构的部署属性层。语义字段（示意，不锁定 JSON 细节）：

```json
{
  "scenario": "rescale | relocate",
  "entity": "app.entity.NopOrder",
  "source-query-spaces": ["ds_0", "ds_1"],
  "target-strategy": { "…新 nop.orm.shard 策略片段（rescale 场景必填）…" },
  "target-query-space-mapping": { "…relocate 场景的新数据源指向…" },
  "write-gate": "open",
  "incremental": { "enabled": true, "connector-name": "mig-order",
                    "position-p0": "…PREPARE 钉定后回填…", "quiesce-seconds": 30 },
  "verify": { "count": true, "sample-ratio": 0.05, "full-checksum": false },
  "throttle": { "rate-limit": 5000, "batch-size": 1000, "concurrency": 4 },
  "batch-task-name": "orm-migration-order"
}
```

`write-gate` 是写停止关（§4.3 第 1 步）：`closed` 时 ORM DML 提交路径显式 fail-fast（读不受影响）。阶段执行体由平台内置模板参数化生成：全量阶段是标准 batch.xdef 作业模型（用户可 delta 定制 processor/consumer 行为），流作业图由编排器按 §4.4 构造——**用户不手写流图**，只声明场景参数。

**错误码**：

- `ERR_MIG_*` 族登记编排模块（`nop-orm-migration`）错误码类：配置解析类（缺实体/缺目标策略/场景与字段矛盾/P0 缺失）、状态机类（非法阶段迁移、门禁未满足即切换）、搬迁映射类（null 分片键，携带实体/主键/物理表）、校验类（count 失配、checksum 失配带明细参数）。
- `ERR_ORM_MIGRATION_WRITE_GATE_CLOSED` 登记 OrmErrors（写门执行检查随检查点层）。
- 数据面错误复用既有码：路由策略解析 `ERR_DAO_SHARD_*`、sink 写入 `ERR_STREAM_*`、batch 任务门控 `ERR_BATCH_TASK_*`。

**可观测性**：指标族 `nop.orm.migration.*`（阶段进入/退出、全量行计数与速率、校验进度与结果、切换耗时），命名分层沿用 `ai-dev/design/nop-stream/observability-design.md` 约定；全量阶段复用 `IBatchTaskMetrics`，增量阶段复用 cdc 指标族（cdc-design §3.7，其中 offset lag 即 lag=0 判定的观测量之一，m6）。结构化日志沿用 `nop.batch.*` 既有键风格（`nop-batch/nop-batch-core/src/main/java/io/nop/batch/core/impl/BatchTask.java:281-283`）。

**控制台集成面**：v1 不建新 UI。全量阶段任务天然出现在 nop-batch 既有任务管理面（`NopBatchTask` 的 GraphQL CRUD，`nop-batch/nop-batch-service`；状态字典 `nop-batch/nop-batch-meta/src/main/resources/_vfs/dict/batch/task-status.dict.yaml`）；编排 job 状态提供只读 GraphQL 查询（最小 Biz 面，落编排模块）；编排入口 = 编排服务 Bean（编程触发，亦可由 nop-job 调度/人工运维触发），为全量阶段 batch 任务打 `flowId` 实现编排状态 ↔ 批次任务行的双向追溯。

## 五、拒绝了什么

| 被拒方案 | 拒绝理由 |
|---|---|
| 四阶段塞进单一 nop-batch 作业 | batch 无阶段状态机；增量是长驻流不是 chunk 终止作业；两套断点语义（NopBatchTask 行 vs checkpoint state）无法统一为一种而不残缺（§4.1） |
| nop-wf 表达搬迁编排 | 审批流持久化/恢复语义 ≠ 机器门禁状态机；flowId 字段已提供任务行关联，无需工作流定义（§4.1） |
| 全量改由 Debezium initial snapshot 承载（B1 备选 (b)） | 与裁定 1 冲突：断点/限流/分区并行/控制台面全改绑引擎内部机制；且 CDC 白名单外场景（relocate 主场景之一）将失去全量承载面（§4.2） |
| CDC 从"FULL 完成时刻"起播（无 P0 全序重放） | FULL 窗口内（可达小时级）源端写入既不在全量副本、binlog 也未消费——永久丢失；位点契约必须锚定 FULL 之前的 P0（§4.2） |
| 数据面两阶段提交切换（XA/分布式 2PC） | 平台级 non-goal；切换原子性由"单点配置翻转 + 收敛门"保证；CDC sink 的 epoch 2PC 是流内幂等机制，不外推为跨库事务（§4.1/§4.3） |
| 配置翻转后直接恢复写（无收敛门） | `ConfigShardSelector` 启动期一次解析，滚动重启期间新旧配置实例并存，旧配置实例写落旧 N 片成为永久孤儿写；恢复写门禁 = 全实例重建收敛（§4.3） |
| 无写停止原语的"软停写"（仅靠 lag 观测） | lag=0 与校验资格都以源端无新写为前提，无写门则两者不可判定；与 2320 fail-fast 哲学同构，门禁不满足显式失败（§4.3） |
| `SideOutputDataStream.sink` 承载多目标 sink | 进程内消费者非部署顶点，不参与 checkpoint、无法承载 `jdbc-2pc` epoch 生命周期；下游图变换显式 unsupported（§4.4） |
| 双写并存（写旧+写新，读旧） | 路由层无双写原语（单 SQL 单 querySpace 硬约束）；应用侵入 + 冲突消解复杂度超出编排层边界（§4.3） |
| 读旧写新（按行存在性路由） | 该路由语义不存在且无法用分片算法表达（hash-mod 区间不可反推）；新片数据不全期间无法判定落点（§4.3） |
| 增量捕获改应用双写 | 侵入业务写路径、双写顺序/补偿问题平台不可内置；Debezium 底座已 active 且 mysql/pg 白名单覆盖 v1 场景（§4.4） |
| DB 侧 CRC32/checksum 校验 | 方言绑定；加密列语义与源库耦合；应用侧规范化 hash 一个实现覆盖同构双端（§4.6） |
| 解密后比明文 | 扩大密钥面，无判定增益——同 key 下密文一致即明文一致（§4.6） |
| 密钥不一致下搬迁 + 密文校验 | 密文比对恒真，重加密错误被放行为绿灯；v1 以 PREPARE 密钥一致门禁显式拒绝，换 key 归重加密工具（§三.7/§4.6） |
| 全量/增量经 ORM session 读写（orm-reader/orm-writer 承载搬迁数据面） | 业务路由契约（缺分片键 fail-fast、按当前配置改写表名）与搬迁的物理面操作冲突：旧策略期写新片会被路由改写回旧片；`EncAssistedBackfiller` 已确立 JDBC 直连先例（§4.5） |
| null 分片键行静默跳过或猜测落片 | 无静默错误结果裁定；落片不可猜测（null 无路由值），类型化失败携带实体/主键/物理表定位（§4.5） |
| SS 式限流算法 SPI | 单消费者无扩展需求，YAGNI；batch rateLimit 标量已覆盖（§4.7） |
| SS 式唯一键区间 split 分发协议 | 为多节点分发设计；nop 单机执行面 chunk 并发 + 按表切片已覆盖（§4.7） |
| 从库读搬迁源 | 复制延迟与位点一致性复杂度无对应用户；CDC 本身要求主库（§4.7） |

## 六、与已有设计的关系

- **`ai-dev/design/nop-orm-sharding-strategy-design.md`**：本设计消费其策略配置面（`nop.orm.shard`、`ShardSelection`、分片名全集）；切换协议是其 §3.5"扩容是数据搬迁问题"裁定的兑现。本设计不改变该文档任何裁定；切换后的路由行为与单分片路由设计完全一致。
- **`ai-dev/design/nop-orm-sharding-execution-engine-design.md`（WI7/WI8，fan-out 执行引擎）**：正交——执行引擎服务业务跨分片查询，本设计只做单分片粒度的物理数据面操作；搬迁后的跨分片查询能力是否完备由该设计回答，本设计不依赖其落地。
- **`ai-dev/design/nop-stream/cdc-design.md`、`connector-design.md`**：增量阶段是其 DB→DB CDC 能力的编排级消费者（白名单、offset 真相源、2PC/ledger 契约全部沿用，无协议变更）；新增的契约级要求仅一条——首跑 offset 可种子化为 P0（§4.2）。**多 sink 拓扑是未验证能力**，M>1 fan-out 流图以 `ai-dev/design/nop-stream/component-roadmap.md` C2 检查单勾选为前置（§4.4），回退形态不依赖它。
- **`ai-dev/design/nop-dao-readwrite-splitting-design.md`（plan 2319）**：读写分离在切换后继续生效于新分片（querySpace 指向路由 DS 的既有组合语义）；本设计不改动其裁定。写停止关与读写分离正交（一个禁写、一个路由读）。
- **`ai-dev/design/nop-orm-scope-and-non-goals.md`**：不触碰其任何既有裁定（XA/脱敏/读写分离归属等）。
- **实施时的挂点（live code 锚定见 §2.2）**：`ConfigShardSelector`（策略重算与配置翻转消费）、`ShardDdlHelper`（PREPARE 目标表全集校验）、`NopStreamOffsetBackingStore`（P0 种子化，契约级新增要求）、`JdbcTwoPhaseCommitSinkBuilder`（增量 sink 分支构造）、`DaoBatchStateStore`/`BatchTaskRunner`（全量阶段承载与触发）、`UpsertStatements`（全量/增量共用写原语）、ORM DML 提交入口（写停止关检查点）。
