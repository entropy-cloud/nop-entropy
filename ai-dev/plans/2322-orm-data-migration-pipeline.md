# 2322 nop-orm 数据搬迁管道编排器落地（WI9）

> Plan Status: active
> Last Reviewed: 2026-10-06
> Source: ai-dev/design/nop-orm-data-migration-pipeline-design.md（active，唯一语义规格）；ai-dev/backlog/nop-persistence-middleware-roadmap.md WI9
> Related: 2320-orm-sharding-strategy（策略模型已落地）、2319-dao-readwrite-splitting

## Purpose

按设计文档落地数据搬迁管道编排器：新子模块 `nop-persistence/nop-orm-migration`（依赖四面单向收束 nop-dao/nop-orm/nop-batch-*/nop-stream-*），承载五状态机 PREPARE→FULL→INCREMENTAL→VERIFY→SWITCH/ABORT、P0 位点钉定与全序重放契约、写停止关原语、三级一致性校验、收敛门切换协议；配套 owner doc 与回归。

## Current Baseline

- 设计文档 active（评审修订 v2 已合），裁定 1-7 + B1/M1-M5/m1-m6 全部已有语义裁定；实施归本 plan。
- CDC 底座：`DebeziumCdcSourceFunction` 实现 CheckpointedSourceFunction，首跑路径 `newFreshOffsetStore()` 清空 offset registry 后从空开始——**首跑 offset 种子化为 P0 的契约不满足，需底座最小增量**（设计 §4.2）。
- `JdbcTwoPhaseCommitSink`（jdbc-2pc）支持 dmlMode UPSERT_DELETE + epoch ledger 幂等，`tableName` final 单表，不按行动态路由；方言 upsert 事实源 `UpsertStatements`（mysql/mariadb/postgresql + H2 测试通道）。
- nop-stream 多 sink 管线为未验证能力（component-roadmap C2 未勾选）；`SideOutputDataStream.sink` 非部署顶点、不可承载 2PC epoch。
- nop-batch：BatchTask/BatchTaskBuilder（batchSize/concurrency/rateLimit/jitterRatio/startLimit/stateStore）、`JdbcPageBatchLoaderProvider`（offset 分页，查询成功后推进游标）、`DaoBatchStateStore`（依赖 nop-batch-dao ORM 实体面）。
- `EncAssistedBackfiller` 确立 JDBC 直连不经 ORM session 先例；2320 已落地 `ConfigShardSelector`/`ShardStrategy`/`ShardSelection`/`ShardDdlHelper`（`nop.orm.shard` JSON 配置串）；`ShardStrategy.parseStrategies` 目前为包私有 static。
- 平台无写停止原语：`DaoConfigs`/`OrmConfigs` 无 write-gate 类配置；`OrmErrors` 无 migration 写门错误码。
- 平台无 `nop.orm.migration` 配置串与 `ERR_MIG_*` 错误码族；无 nop-orm-migration 模块。

## Goals

- 编排器五状态机 + DB 状态行持久化 + 重启按状态行续跑（绝不隐式回退阶段）。
- PREPARE：P0 钉定（mysql SHOW MASTER STATUS / pg pg_current_wal_lsn()，经 JDBC；测试注入模拟位点）、密钥一致门禁、目标物理表就绪校验。
- FULL：委托 nop-batch 作业（offset 分页读源物理表 → keyed upsert 写目标物理表；throttle 旋钮透传）。
- INCREMENTAL：debezium-cdc 源（首跑 offset 种子化 P0）→ shard-router → jdbc-2pc sink（UPSERT_DELETE）；多 sink 前置验证不通过时落地回退形态（每目标分片单 sink 链作业）。
- VERIFY：写停止关关闭 + lag=0（静默期 offset 无前进 + ledger 无新增）后三级校验（count 必选 / 抽样 / 逐行），应用侧物理列规范化 hash，enc 列密文原样比对；null 分片键 fail-fast。
- SWITCH/ABORT：收敛门（v1 人工确认 = 编排 API 显式 confirm）→ `nop.orm.shard` 原子翻转 → DONE；ABORT 全程可达。
- 写停止关：`nop.orm.migration.write-gate`（OrmConfigs 平面配置）+ `OrmErrors.ERR_ORM_MIGRATION_WRITE_GATE_CLOSED`，检查点在 ORM DML 提交入口，读路径不受影响。
- 错误码族 `ERR_MIG_*` 登记编排模块；owner doc `docs-for-ai/02-core-guides/data-migration.md` + INDEX 路由。

## Non-Goals

- 真实 mysql/pg 端到端 CDC 搬迁（docker 真源场景归既有 docker-gated 测试面，本 plan 用流图构造单测 + H2 回退形态覆盖）。
- Web 控制台 UI；nop-wf 编排；数据面 2PC；双写/读旧写新（设计已拒，全部不再裁定）。
- 密钥轮换/重加密、白名单外数据库增量搬迁、自动扩容触发（设计 Non-Goals 原样继承）。
- 编排 job 的 GraphQL 只读查询 Biz 面落 nop-biz 集成（v1 编排器仅程序化 Bean API + 最小只读 BizModel 查询类；若集成面超出四依赖面约束则显式留痕为 follow-up）。

## Scope

### In Scope

- 新模块 `nop-persistence/nop-orm-migration`（根 pom + nop-persistence/pom modules 注册）。
- 底座最小增量：`DebeziumCdcSourceFunction` 首跑 offset 种子化 API（AR-03 防陈旧语义保持）；`ShardStrategy.parseStrategies` 可见性放宽；`OrmConfigs`/`OrmErrors` 写门平面配置与错误码；`OrmSessionImpl` DML 提交入口门禁检查。
- 编排器、配置解析、错误码、状态行、P0、FULL 委托、增量流图（fan-out 探测 + 回退形态）、三级校验、切换/中止协议。
- H2/内嵌为主的全套测试；nop-batch/nop-stream 既有模块回归；文档。

### Out Of Scope

- nop-batch-dao（DaoBatchStateStore）接线到测试环境（编排器以 `IBatchStateStore` 注入口暴露，宿主有 nop-batch-dao 时注入；无注入时 FULL 重跑幂等安全由 keyed upsert 保证）。
- nop-job 调度入口、mission/console 集成。

## Execution Plan

### Phase 1 - 模块骨架 + 配置 + 错误码 + 写停止关原语

Status: completed（2026-10-06）
Targets: `nop-persistence/nop-orm-migration/`（pom、错误码、配置）、`nop-persistence/nop-orm`（OrmConfigs/OrmErrors/OrmSessionImpl 写门）、`nop-persistence/nop-dao`（ShardStrategy 可见性）、`pom.xml`/`nop-persistence/pom.xml`

- Item Types: `Feature`

- [x] 新模块 pom（依赖 nop-dao/nop-orm/nop-batch-core/nop-batch-jdbc/nop-stream-core/nop-stream-connector-debezium/nop-stream-connector-jdbc/nop-message-debezium；test: junit5/h2/HikariCP）+ 两级 pom modules 注册
- [x] `MigrationErrors`（ERR_MIG_* 族：配置解析/状态机/null 分片键/校验失配/目标表未就绪/密钥不一致/P0/方言边界/位点查询失败）
- [x] `MigrationConfigs.CFG_ORM_MIGRATION`（`nop.orm.migration` JSON 串）+ `MigrationJobConfig` 解析（scenario/entity/source-query-spaces/target-strategy/target-query-space-mapping/write-gate/incremental/verify/throttle/batch-task-name），配置错误显式失败
- [x] `OrmConfigs.CFG_ORM_MIGRATION_WRITE_GATE`（`nop.orm.migration.write-gate`，String open|closed，缺省 open）+ `OrmErrors.ERR_ORM_MIGRATION_WRITE_GATE_CLOSED`（携带实体与操作）
- [x] `OrmMigrationWriteGate` 门禁原语（闭态判定 + 类型化 fail-fast）接入 ORM DML 提交入口：`OrmSessionImpl.flush()`（dirty 时）、`flushSave/flushUpdate/flushDelete`、`flushImmediately`；读路径不受影响
- [x] `ShardStrategy.parseStrategies` 包私有 → public（可见性放宽，唯一 nop-dao 增量）

Exit Criteria:

- [x] 模块注册后 `./mvnw compile -pl nop-persistence/nop-orm-migration -am` 通过
- [x] 写门测试：gate=closed 时 flush/flushSave 抛 `ERR_ORM_MIGRATION_WRITE_GATE_CLOSED` 且携带实体与操作；gate=open 或纯读路径零行为变更（新增 JUnit5 覆盖，既有 ORM 测试回归绿）
- [x] 配置解析测试：缺实体/场景与字段矛盾/坏 JSON/未知 write-gate 值逐形态显式失败
- [x] **无静默跳过**：配置非法/门禁不满足均为类型化异常，无占位返回
- [x] nop-dao/nop-orm 既有测试回归绿（可见性放宽与门禁增量为行为保持改动）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 状态机 + 状态行 + PREPARE/FULL 委托

Status: completed（2026-10-06）
Targets: `nop-orm-migration`（MigrationPhase/状态行 store/编排器/PREPARE/FULL）

- Item Types: `Feature`

- [x] `MigrationPhase` 五状态机（PREPARE/FULL/INCREMENTAL/VERIFY/SWITCH + DONE/ABORT 终态），非法迁移显式失败；编排器重启按状态行续跑，不隐式回退
- [x] 状态行 JDBC 持久化（`nop_orm_migration_job` 表，CREATE TABLE IF NOT EXISTS 方言兼容，先例=sink ledger `initializeLedgerTable`）
- [x] PREPARE：P0 钉定（mysql SHOW MASTER STATUS / pg pg_current_wal_lsn() 经 JDBC；`PositionProvider` seam 供 H2 测试注入模拟位点）→ 回填状态行与配置；目标物理表存在性校验（按新策略 shardSelections × 表名 pattern，经 JDBC metadata）；密钥一致门禁（源/目标密码器配置一致性，M4）
- [x] FULL：委托 nop-batch——`BatchTaskBuilder`（batchSize/concurrency/rateLimit/jitterRatio 透传）+ offset 分页 loader（select * order by pk，查询成功后推进游标语义复用）+ `ShardedUpsertConsumer`（按新策略分表分组 keyed upsert，`UpsertStatements` 共用事实源；目标方言无 native upsert 显式失败 m2）；null 分片键 fail-fast ERR_MIG_*（携带实体/主键/物理表）；`IBatchStateStore` 注入口；幂等重放（同 epoch 重跑 keyed upsert 覆盖）
- [x] 编排器 API：start/resume（含 PREPARE→FULL 门禁：目标表就绪+密钥一致+P0 已钉定）、abort 全程可达

Exit Criteria:

- [x] 状态机迁移矩阵测试：合法序列推进 + 每条非法迁移显式失败 + 重启续跑（状态行恢复后从最后完成阶段继续）
- [x] P0 钉定测试（H2 + 模拟位点对象）：P0 回填状态行；不支持的方言显式失败
- [x] FULL 端到端（H2→H2）：源表 N 行经 batch 作业落目标分片表，行数/内容一致；断点续跑安全（重跑同任务 keyed upsert 结果不变）；null 分片键行 fail-fast 且错误携带定位三元组
- [x] **端到端验证**（Minimum Rules #22）：PREPARE→FULL 从编排 API 入口到目标表数据落库完整走通
- [x] **接线验证**（Minimum Rules #23）：FULL 阶段编排器确实构造并启动了 nop-batch BatchTask（测试断言任务行/完成计数经编排器状态行可见）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - INCREMENTAL（offset 种子化 + 流图）+ 多 sink 前置验证/回退

Status: completed（2026-10-06）
Targets: `nop-stream-connector-debezium`（种子化 API）、`nop-orm-migration`（流图构造/路由/seed）

- Item Types: `Feature`

- [x] 底座最小增量：`DebeziumCdcSourceFunction` 首跑（state==null）offset 种子化 API——注入种子时清空 registry 后 setOffsets(种子)，未注入时 AR-03 行为逐字节不变；恢复路径（state!=null）种子不生效（checkpoint 为真相源）
- [x] `CdcOffsetSeedBuilder`：P0 → Debezium offset key/value（mysql: file/pos[/gtids]；pg: lsn）经 NopStreamOffsetBackingStore 序列化格式
- [x] INCREMENTAL 流图：debezium-cdc 源（snapshot.mode=never + 种子化 P0）→ shard-router（行内分片键值按新策略算 ShardSelection；null 键 fail-fast）→ jdbc-2pc sink（UPSERT_DELETE）
- [x] 多 sink 前置验证（M1）：`MultiSinkTopologyProbe` 编译级探测（M 个 sink 的图能否编译出部署图）；探测通过 + 显式 opt-in 才走 fan-out 形态，缺省落地回退形态（每目标分片：源全订阅 + filter 本分片 + 单 sink 链作业）并留痕
- [x] lag=0 判定（m6）：静默期（quiesce-seconds）offset map 无前进 + ledger 无新增提交行，两观测量同时满足
- [x] INCREMENTAL→VERIFY 门禁：写停止关 closed + lag=0 才允许进入 VERIFY

Exit Criteria:

- [x] 种子化测试：注入种子后首跑 offset store 内容=种子；不注入时清空行为与既有 AR-03 测试一致（debezium 模块既有测试回归绿）；恢复路径种子不覆盖 checkpoint
- [x] seed builder 单测：mysql/pg P0 → 序列化 offset map 往返（base64/JSON 契约）
- [x] 流图构造单测：回退形态 M 个单 sink 链作业图可编译；fan-out 探测结果可判定；路由器 null 分片键 fail-fast 携带定位
- [x] lag=0 测试：静默期内两观测量不变→满足；任一变化→门禁拒绝
- [x] **无静默跳过**：多 sink 探测不通过时落地回退形态而非空实现；白名单外 connectorType 显式失败
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - VERIFY 三级校验 + SWITCH/ABORT 协议

Status: completed（2026-10-06）
Targets: `nop-orm-migration`（校验器/切换门/收敛门）

- Item Types: `Feature`

- [x] 三级校验（应用侧，双游标按主键序配对，扩容场景源 N 片之和 vs 目标 M 片之和）：count 必选 → 抽样 checksum（sample-ratio）→ 逐行 checksum（full-checksum）；行值规范化串稳定 hash（null 标记/BigDecimal 规范化/时间统一精度/二进制固定编码）；enc 列密文原样比对；失配明细（实体/主键/段 hash）落报告，ERR_MIG_CHECKSUM_MISMATCH 携带明细参数
- [x] 校验门禁：只在写停 + lag=0 之后执行（编排器顺序保证）
- [x] SWITCH：VERIFY 通过 → 编排 API 显式 confirm（v1 人工收敛门）→ `nop.orm.shard` 原子更新（整体 JSON 翻转）+ 以新配置 init 新 `ConfigShardSelector` 重建路由（单 JVM 形态直接替换 selector；热重建不可行时的滚动重启语义在状态行标注待重启实例）→ 开启写门 → DONE
- [x] ABORT 全程可达：PREPARE/FULL/INCREMENTAL/VERIFY 各阶段中止，旧配置未动；写门 closed 期间 fix-forward 与回翻两出路语义可执行（回翻 = shard 翻回旧配置 + 开门）

Exit Criteria:

- [x] 三级校验测试（H2→H2）：count 失配拒绝；抽样/逐行一致通过；密文列同 key 原样比对；行值改写被逐行 checksum 抓住且报告携带定位
- [x] SWITCH 测试：VERIFY 未通过时 confirm 显式失败；confirm 后 shard 配置翻转 + selector 重建 + 写门开启 + 状态 DONE；恢复写后 ORM 路由落新分片（与 2320 路由语义一致）
- [x] ABORT 测试：各阶段中止后旧配置未动、写门恢复 open
- [x] **端到端验证**（Minimum Rules #22）：PREPARE→FULL→（增量门禁判定）→VERIFY→SWITCH→DONE 全链在 H2 + 模拟增量源下从编排 API 入口走通，目标端路由切新分片
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - 全量回归 + hollow 扫描

Status: completed（2026-10-06）
Targets: 构建与测试面

- Item Types: `Proof`

- [x] `./mvnw test -pl nop-persistence/nop-orm-migration -am` 全绿
- [x] nop-batch/nop-stream 受影响模块回归（nop-stream-connector-debezium 既有测试、nop-batch core 测试）绿
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-persistence/nop-orm-migration --severity high` 退出码 0
- [x] 编译门禁 `./mvnw clean install -DskipTests -pl nop-persistence/nop-orm-migration,nop-persistence/nop-orm,nop-stream/nop-stream-connector-debezium -am` 通过（跨模块增量无断编译）

Exit Criteria:

- [x] 上述四条命令全部退出码 0，数字记录进 daily log
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 6 - owner doc + INDEX 路由

Status: planned
Targets: `docs-for-ai/02-core-guides/data-migration.md`、`docs-for-ai/INDEX.md`、`docs-for-ai/01-repo-map/module-groups.md`

- Item Types: `Feature`（文档）

- [x] owner doc `data-migration.md`：作业模型/配置契约（nop.orm.migration JSON + write-gate 平面键）/五阶段协议/前置条件（binlog 保留窗口、密钥一致）/故障语义（fail-fast、断点、回滚边界）/代码锚点
- [x] INDEX.md 路由行 + module-groups.md 持久化行补 nop-orm-migration
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

Exit Criteria:

- [x] 三文件落地且 link checker 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 五状态机/门禁/校验/切换语义与设计文档裁定逐条对应（裁定 1-7、B1、M1-M4、m1-m6 无静默偏离；偏离处已在 plan/log 显式留痕）
- [x] 新增功能测试覆盖齐全（状态机矩阵/P0/门禁三态/三级校验/幂等重放/FULL E2E/种子化/回退形态/lag=0）
- [x] Anti-Hollow：编排 API→batch/stream 执行面→校验→切换的调用链运行时连通（端到端测试证明），无空方法体/静默跳过
- [x] 受影响 owner docs 已同步（data-migration.md 新增 + INDEX + module-groups）；设计文档无需改（已 active，若实施裁定偏离设计则同步修订并留痕）
- [x] `./mvnw test -pl nop-persistence/nop-orm-migration -am` 通过
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-persistence/nop-orm-migration --severity high` 退出码 0
- [x] checkstyle/代码规范：imports 分组、错误文案英文、无生成文件手改
- [ ] 独立子 agent closure audit 已完成并记录证据（待主会话派 fresh audit；本 plan 执行会话不做 self-audit closure）

## Deferred But Adjudicated

### 编排 job 只读 GraphQL Biz 面的宿主集成验证

- Classification: `watch-only residual`
- Why Not Blocking Closure: v1 交付最小只读 BizModel 查询类（@BizModel 注解位于 nop-api-core，不破四依赖面）；其在完整 nop-biz 宿主的 GraphQL 注册验证需要集成 app 环境，超出本 plan 模块级验证面，不影响编排器契约成立。
- Successor Required: `no`
- Successor Path: 归 WI11 组合 E2E 的集成验证面

## Non-Blocking Follow-ups

- 真实 mysql/pg（docker-gated）下的 P0 种子化真源验证（复用 TestDebeziumCrossDbSyncPipeline 形态补一例 rescale 场景）
- 实例配置版本心跳自动化收敛门（设计 §4.3 已声明 v1 人工确认）

## Closure

Status Note: （待主会话独立 closure audit 后填写）
Completed: （未完成）

Closure Audit Evidence:

- Reviewer / Agent: （留主会话）
- Evidence: （留主会话）

Follow-up:

- 见 Non-Blocking Follow-ups
