# 2319 nop-dao 读写分离（DataSource 层路由装饰器）

> Plan Status: active（2026-10-05 两轮独立对抗审查：第 1 轮 REJECT——1 Blocker（分页重建丢 readOnly）+ 2 Major（sql-lib 生成链/测试基类类型）已修；第 2 轮（fresh session）APPROVE，残留 2 处 baseline 表述已修正，证据见 Draft Review Evidence。2026-10-06 全部 4 Phase 实施落地，待独立 closure audit）
> Last Reviewed: 2026-10-06
> Source: `ai-dev/design/nop-dao-readwrite-splitting-design.md`（目标设计，承载本 plan）；roadmap `ai-dev/backlog/nop-persistence-middleware-roadmap.md` WI1/WI4

> Draft Review Evidence（WI1，2026-10-05）：第 1 轮独立审查对 live code 全量锚点核实（withTxn 桥接链、FakeDataSource 桩、addQuerySpace 先例等全部命中），发现分页 handler 经 `SQL.begin(sql)` 重建会丢弃 readOnly 标志（findPage 静默回落主库）、sql-lib 属性属 `_SqlItemModel` 生成物须走 regen、测试基类 `createDataSource()` 为具体类无法协变覆写——均已修入计划；第 2 轮换 fresh session 复审判定 APPROVE 可执行，另提示 `SqlBuilder.copy()` 同类漏拷先例与基类 Hikari 具体 API 的 tearDown 依赖（编译器强制暴露，非静默），已同步入计划文本。
> Related: `ai-dev/design/nop-orm-scope-and-non-goals.md` §3.5（读写分离归 DataSource 层的既有裁定）

## Purpose

按 `nop-dao-readwrite-splitting-design.md` 落地读写分离：`ReadWriteRoutingDataSource` 装饰器 + 三级判定（显式 hint > 活动事务主库 > 只读意图从库）+ 负载均衡/故障冷却 + 配置装配 + sql-lib 只读标注，端到端可用。本 plan 是 nop-core `SQL` 对象与 `sql-lib.xdef` 变更的 plan-first 依据（Protected Area）。

## Current Baseline

以下事实均经 2026-10-05 live code 核实（探索代理 + 本人复核）：

1. **物理连接唯一收口**：`JdbcTransactionFactory.getConnection()`（`nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/txn/JdbcTransactionFactory.java:53-59`）= `dataSource.getConnection()` + 异常翻译；事务内（`JdbcTransaction.getConnection()` 懒取，:60-75）与裸连接（`openConnection` :71-74）都汇聚到它。`DefaultTransactionManager` 对 DataSource 实现类零感知（`setDataSourceMap` 只包 `JdbcTransactionFactory`，:83-92）——**路由 DataSource 透明替换成立**。
2. **SQL 执行主路径与事务判定**：`JdbcTemplateImpl.withTxn`（`jdbc/impl/JdbcTemplateImpl.java:179-190`）走 `runInTransaction(SUPPORTS)`：有已注册事务则复用（`isTransactionOpened()==true` 表示真事务），无则新建未 open 的事务对象（连接 autoCommit 保持 true、用完即还）。**callback 内 `txn` 与 `txn.getConnection()` 懒取之间就是只读意图的作用点**（同步同线程）。
3. **statementKind 执行期可用**：`JdbcQueryExecutor.executeQuerySql/executeUpdateSql`（`loader/JdbcQueryExecutor.java:55-116`）入口参数即 `ICompiledSql`（`getStatementKind()`，`ICompiledSql.java:31`）；EQL/QueryBean 查询全部经此路径。sql-lib 的 `eql`/`query` 项也经 `ormTemplate` 到这里（`SqlLibManager.getExecutor` :210-215）；原生 `<sql>` 项走 `jdbcTemplate` 直连（无 statementKind，`SqlItemModel.invoke` :98-145 只按 SqlMethod/文本启发式）。
4. **装配**：`dao-defaults.beans.xml:14-24` `nopTransactionManager` 以 `ioc:collect-beans name-prefix="nopDataSource_" as-map` 建 querySpace→factory map；default querySpace 走 `defaultFactory`（`getTransactionFactory` :185-193 特判 default），`DefaultTransactionManager.setDefaultFactory` setter 存在（:79-80）；`addQuerySpace/addDataSource`（:102-108/:114-122）支持运行期注册。
5. **ThreadLocal 原语先例**：`DynamicDataSource`（static ThreadLocal + `runWith` try/finally，:23-59）、`QuerySpaceEnv`（`runWithQuerySpace`）均在 nop-dao 内；`ContextProvider` 的 tenant 系列是 proxy 上下文模式（nop-api-core）。设计裁定：hint 采用 nop-dao 本地 ThreadLocal（对齐 `QuerySpaceEnv` 先例），不进 `IContext`。
6. **SQL 对象无只读语义**：`io.nop.core.lang.sql.SQL` 携带 querySpace/name/text/markers，无 readOnly 字段；`SQL.SqlBuilder` 是链式构建器。
7. **测试基建**：`JdbcTestCase`（nop-dao，H2 mem + Hikari，`getActiveConnections()/checkNoActive()` 断言连接归还）；`TestDefaultTransactionManager` 有双 H2 双 querySpace 先例（`addQuerySpace(QS, ds)`）；`TestDynamicDataSource` 有 `FakeDataSource.lastResolved` 路由断言桩模式（:37-104）；nop-orm `AbstractJdbcTestCase` 的 `createDataSource()`/`dataSource` 现为具体类型 `HikariDataSource`（:32/:80），覆写返回路由 DS 需先放宽为 `DataSource`（Phase 4 执行项）。
8. **配置**：`DaoConfigs` 现无 readwrite 键（`nop.dao.config.query-space-to-dialect` 是 String 配置事后解析为 Map 的先例）；数据源 bean 实际示例为直接 `HikariDataSource` bean（`nopDataSource_<qs>`）。
9. **无既有实现**：全仓 `readwrite|replica|读写分离` 主代码零命中。

## Goals

- `ReadWriteRoutingDataSource`（write + reads，实现 DataSource）以配置驱动装配，对事务管理器/ORM 全透明。
- 三级判定按设计文档 §3.1：显式 hint（`ReadWriteHint.runWithPrimary/runWithReplica`）> 活动事务主库 > 只读意图（无事务 + 语句只读）从库；写语句与语义未知 fail-safe 主库。
- 只读意图数据流化：`SQL.readOnly` 标志（EQL SELECT 自动标记；sql-lib 原生项显式标注）+ `JdbcTemplateImpl` 连接获取点桥接 ThreadLocal（作用域精确到 `getConnection()` 调用）。
- 负载均衡（round-robin/random/weight）+ 从库故障换下一台 + 连续失败冷却 + 半开恢复；全部从库不可用显式报错（不静默回主库）。
- 端到端：EQL 查询无事务走从库、事务内走主库、显式 hint 覆盖、从库故障降级行为可测。

## Non-Goals

- 隐式 write-then-read sticky、事务内 FIXED/DYNAMIC 读策略（设计文档 §四已拒绝）。
- 注册中心动态发现/剔除、独立代理进程、连接池层 hack（设计文档 §四已拒绝）。
- ORM 层路由逻辑（本 plan 的 ORM 侧改动仅限"传播只读意图"，违反即回退设计）。
- GenSqlHelper 物理路径（按主键 load/findByExample/updateByExample 等）v1 不标记只读（fail-safe 主库）；后续按需扩展（记 Follow-up）。
- 裸 `openConnection(querySpace)` 借用路径的路由（保持主库，无语句语义可判）。

## Scope

### In Scope

- `nop-kernel/nop-core`：`SQL` 只读标志（Protected Area 变更，本 plan 为依据）。
- `nop-persistence/nop-dao`：`ReadWriteHint`、`ReadWriteRoutingDataSource`、负载均衡、故障冷却、配置（`DaoConfigs` + 装配）、`JdbcTemplateImpl` 桥接。
- `nop-persistence/nop-orm`：`JdbcQueryExecutor` 对 SELECT 的 `SQL.readOnly` 标记（仅此一处）。
- `nop-kernel/nop-xdefs`：`sql-lib.xdef` 的 `SqlItemModel` 可选 `readOnly` 属性 + `SqlItemModel.buildSql` 传播。
- 测试：nop-dao 单测（桩模式）+ 双 H2 集成 + nop-orm E2E。
- 文档：设计文档转 active、`docs-for-ai/` owner doc + INDEX。

### Out Of Scope

- quarkus 侧 `quarkus-defaults.beans.xml` 的等价装配（跟随主线装配，验证记 Follow-up）。
- 影子库（roadmap WI10）。

## Execution Plan

### Phase 1 - 只读意图传播管道

Status: completed
Targets: `nop-kernel/nop-core/src/main/java/io/nop/core/lang/sql/SQL.java`、`nop-persistence/nop-dao/.../jdbc/datasource/ReadWriteHint.java`（新）、`nop-persistence/nop-dao/.../jdbc/impl/JdbcTemplateImpl.java`、`nop-persistence/nop-orm/.../loader/JdbcQueryExecutor.java`

- Item Types: `Feature`

- [x] `SQL` 增加只读标志（字段 + `SqlBuilder` 链式方法 + 缺省 false），不改变任何既有语义
- [x] **readOnly 传播完整性**：`SQL.begin(SQL)` 现状只拷 name/querySpace/fetchSize/timeout/cacheRef/disableLogicalDelete——分页 handler（`LimitOffsetPaginationHandler`/`OffsetFetchPaginationHandler`）正以 `SQL.begin(sql)` 重建 paged SQL 且 `JdbcTemplateImpl.executeQuery` 桥接读到的就是 pagedSql，不处理则 findPage 全量静默回落主库；标志必须经重建/复制路径保留（含 `SqlBuilder.copy()`——它现状已漏拷 enableFilter，是同类丢失的在案先例），并配 focused 断言
- [x] 新建 `ReadWriteHint`：ThreadLocal 三态（PRIMARY/REPLICA/UNSET），`runWithPrimary/runWithReplica/runWithReadOnly` try/finally 作用域原语（对齐 `QuerySpaceEnv`/`DynamicDataSource.runWith` 先例；静态字段，javadoc 警告线程池复用须用 runWith 形式）
- [x] `JdbcQueryExecutor`：`executeQuery/executeQuerySql`（`getStatementKind()==SELECT`）对 transform 后的 SQL 标记只读
- [x] `JdbcTemplateImpl` 桥接：`withTxn` 无 SQL 参数，包裹上移到 `runWithConnection` 的 `withTxn(...)` 调用处（SQL 在该作用域，语义等价）——`sql.readOnly && hint==UNSET && 非已 open 事务` 时以 REPLICA 作用域包裹连接获取；显式 hint 与已 open 事务（连接缓存复用，本就要求主库）不被覆盖
- [x] 单测（`FakeDataSource.lastResolved` 桩模式）：readOnly→REPLICA、无标志→主库、显式 hint 优先、事务 open 时→主库、hint 作用域退出后恢复 UNSET、分页（带 range）SQL 标志不丢失

Exit Criteria:

- [x] 上述行为组各有 focused 断言且通过，**含分页重建路径（range 查询 readOnly→REPLICA）**（Minimum Rules #25）
- [x] 既有 `TestDynamicDataSource`/`JdbcTestCase` 全量回归通过
- [x] 无静默跳过：桥接不吞异常、hint 未生效路径可观察（Minimum Rules #24）
- [x] owner-doc 更新延至 Phase 4 统一执行（本 Phase 无独立文档交付）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - ReadWriteRoutingDataSource 与负载均衡/故障冷却

Status: completed
Targets: `nop-persistence/nop-dao/.../jdbc/datasource/ReadWriteRoutingDataSource.java`（新）、负载均衡实现、`DaoConfigs`

- Item Types: `Feature`

- [x] `ReadWriteRoutingDataSource`：write + reads 物理 DataSource；`getConnection()` 判定序 = 显式 hint PRIMARY→write / REPLICA→read 池（空池或全冷却→模块错误码，英文文案） / UNSET→write
- [x] 负载均衡三实现（round-robin/random/weight，weight 缺省等权），选择前过滤冷却中从库
- [x] 故障状态机：获取连接异常→立即试下一台（按 LB 顺序）；连续失败达阈值→冷却；冷却期满→半开（放一台探测）；全部不可用→报错携带各从库失败摘要（不回退主库）
- [x] `DaoConfigs` 增加 `nop.dao.readwrite.*` 配置引用（querySpace→{write-ds, read-ds[], load-balancer, weights, replica-fail-threshold, replica-cooldown-seconds}，键名与设计文档 §3.2 一致；承载形态为字符串/JSON 配置引用——`query-space-to-dialect` 先例是 String 配置事后解析，非 Map 型引用）
- [x] 单测：路由判定矩阵；round-robin 轮转序列与 weight 配比断言；失败→冷却→半开恢复时序；全从库不可用→显式错误；连接归还（`checkNoActive`，在 Phase 4 双 H2 集成 `TestReadWriteSplittingIntegration.testConnectionsReturnedToPools` 落地）

Exit Criteria:

- [x] 路由矩阵/负载均衡/故障时序各有 focused 测试且通过
- [x] 无静默跳过：任何故障路径显式抛模块异常（NopException + 错误码 + 英文 message），无吞异常
- [x] owner-doc 更新延至 Phase 4 统一执行（本 Phase 无独立文档交付）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 装配接线与 sql-lib 只读标注

Status: completed
Targets: `nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/beans/dao-defaults.beans.xml`、`nop-kernel/nop-xdefs/.../orm/sql-lib.xdef`、`nop-persistence/nop-orm/.../sql_lib/SqlItemModel.java`

- Item Types: `Feature` + `Decision`

- [x] 装配初始化器（ioc:default bean，`@PostConstruct`）：读 `nop.dao.readwrite.*` 配置，解析 write/read 物理 DataSource（bean 名引用自 IoC 容器），构造 `ReadWriteRoutingDataSource`；非 default querySpace 经 `transactionManager.addQuerySpace` 注册，default querySpace 经 `setDefaultFactory(new JdbcTransactionFactory(routingDS, dialect))` 替换（setter 已核实存在）
- [x] **Decision**：default querySpace 的路由替换时机（初始化器 bean 生命周期 vs 显式配置开关）——实现时裁定并记录到设计文档；配置为空时零行为变更（不注册任何东西）；物理数据源 bean 命名前缀一并裁定（避免与 `nopDataSource_` collect-beans 前缀碰撞产生幻影 querySpace，建议 `nopReadWriteDs_`）（裁定：初始化器生命周期启用、`nopReadWriteDs_` 前缀、配置空即零行为变更——已落档设计文档 §〇 实施裁定表）
- [x] sql-lib `readOnly` 属性走生成链：编辑 `sql-lib.xdef`（非生成物，git 历史为人工编辑）→ 构建期 regen 更新 `_gen/_SqlItemModel`（**禁止手改 `_gen/` 任何文件**——AGENTS.md Generated Files Hard Stop）→ 手写 `SqlItemModel.buildSql` 将标志传入 `SQL`
- [x] 测试：初始化器装配（双 H2 + 配置）生效且未配置时行为不变；sql-lib 原生 `<sql readOnly="true">` 项走从库、未标注走主库（初始化器两态 `TestReadWriteDataSourceInitializer`；sql-lib 标注路由命中 `TestReadWriteSplittingOrmE2E.testSqlLibReadOnlyAnnotationRouting`）

Exit Criteria:

- [x] 装配/未装配两态 + sql-lib 标注各有 focused 测试且通过
- [x] **接线验证**（Minimum Rules #23）：初始化器构造的路由 DS 被 `DefaultTransactionManager` 真实使用（经事务模板执行的 SQL 路由可观察）
- [x] xdef 变更后构建期 regen 执行（`./mvnw compile -pl nop-persistence/nop-orm -am -DskipTests`），`git diff` 核对 `_gen/` 仅含预期字段漂移、无手改痕迹
- [x] owner-doc 更新延至 Phase 4 统一执行（本 Phase 无独立文档交付）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 端到端与文档收口

Status: completed
Targets: 测试（nop-dao 双 H2 集成、nop-orm E2E、测试基类类型放宽）、owner docs、设计文档状态

- Item Types: `Proof`

- [x] 测试基类类型放宽：`JdbcTestCase`（nop-dao）与 `AbstractJdbcTestCase`（nop-orm）的 `createDataSource()` 返回类型与 `dataSource` 字段由 `HikariDataSource` 放宽为 `DataSource`（协变覆写前提；`IJdbcTemplateFactory.newTransactionTemplate(DataSource)` 已就绪，既有子类不受影响）
- [x] 双 H2 集成（`TestDefaultTransactionManager.addQuerySpace` 先例）：primary 建表插数、replica 独立空库——无事务 EQL 查询命中从库（观察到从库数据）、事务内查询命中主库（观察到主库数据）、`runWithPrimary` 强制主库
- [x] ORM 级 E2E：`AbstractJdbcTestCase` 覆写 `createDataSource()` 返回路由 DS——`findPage`（QueryBean→EQL，分页路径）无事务走从库、写操作与事务内读走主库
- [x] 设计文档 `nop-dao-readwrite-splitting-design.md` 状态 草案→active（含 Phase 3 Decision 结论落档）
- [x] owner doc：`docs-for-ai/02-core-guides/` 新增"数据源与读写分离"章节（配置契约、判定矩阵、一致性契约、fail-safe 边界）+ `docs-for-ai/INDEX.md` 路由
- [x] 全量回归：`./mvnw test -pl nop-persistence/nop-dao,nop-persistence/nop-orm -am`

Exit Criteria:

- [x] **端到端验证**（Minimum Rules #22）：从配置装配到主从分流的完整路径双方向（从库命中/主库命中）均有断言
- [x] owner docs 与 INDEX 更新完成；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 三级判定矩阵（hint/事务/readOnly）+ 负载均衡 + 故障冷却 + 装配两态 + sql-lib 标注全部有 focused/E2E 测试
- [x] 全从库不可用显式报错、hint 作用域恢复、无吞异常——无静默跳过
- [x] 未配置读写分离时全部既有行为零变更（回归证明：`TestReadWriteDataSourceInitializer.testUnconfiguredMeansZeroBehaviorChange` + 全量回归绿）
- [x] owner docs 与设计文档（转 active）与 live baseline 一致
- [x] 独立子 agent closure audit 已完成并记录证据（含 Anti-Hollow：路由 DS 在事务模板真实调用链上）（fresh-closure-audit-2026-10-06，证据见 Closure Audit Evidence）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2319-dao-readwrite-splitting.md --strict` 退出码 0（留主会话执行）
- [x] `./mvnw test -pl nop-persistence/nop-dao,nop-persistence/nop-orm -am` 通过
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

（执行中如出现延期项按 guide 补 Classification 与 Why Not Blocking Closure）

## Non-Blocking Follow-ups

- GenSqlHelper 物理路径（按主键 load 等）的只读标记扩展（v1 fail-safe 主库，属优化项）。
- quarkus 侧等价装配验证。
- 从库权重的运行期动态调整（配合配置中心场景）。

## Closure

Status Note: （最终关闭由主会话执行：本轮独立 closure audit 结论为 **APPROVE closure**——4 Phase Exit Criteria 全 PASS、Anti-Hollow 五链全通、3 项工具 gate 退出码 0、契约一致、无必改项；主会话完成 checklist 复核后将 Plan Status 改为 completed 即可）

Completed: （主会话 closure 时填写）

Closure Audit Evidence:

- Reviewer / Agent: fresh-closure-audit-2026-10-06（独立 fresh session 子 agent，与实现会话无关；回看 live repo，不信任实现记录）
- Evidence:
  - **Phase 1 Exit Criteria（5/5 PASS）**：① `SQL.readOnly` 标志（`nop-core/.../sql/SQL.java:71,96,306-311`，缺省 false）＋传播完整性：`SQL.begin(sql)` 重建保留（SQL.java:226）、`SqlBuilder.copy()` 补拷 readOnly+enableFilter（SQL.java:270-273，在案先例修复）→ `TestSqlReadOnlyFlag` 5/5；② `ReadWriteHint` 三态 ThreadLocal runWith try/finally（`jdbc/datasource/ReadWriteHint.java`）→ `TestReadWriteHint` 6/6；③ SELECT 自动标记 `JdbcQueryExecutor.java:91-93`；④ 桥接 `JdbcTemplateImpl.withTxnForSql:204-213` → `TestJdbcReadOnlyBridge` 7/7（readOnly→REPLICA、无标志→UNSET、显式 PRIMARY 不覆盖、已 open 事务 UNSET、hint 退出恢复 UNSET）；⑤ 分页重建路径：`testPagedSqlKeepsReadOnlyFlag`（LimitOffset/OffsetFetch 重建 + 带 range 执行端到端 REPLICA 可观测）。
  - **Phase 2 Exit Criteria（3/3 PASS）**：路由矩阵/负载均衡/故障时序 focused 测试 `TestReadWriteRoutingDataSource` 12/12——`testUnsetHintGoesToWrite`/`testPrimaryHintGoesToWrite`/`testReplicaHintGoesToRead`/`testReplicaPoolEmptyFailsExplicitlyWithoutWriteFallback`/`testRoundRobinRotationSequence`/`testWeightDistribution`/`testFailureSwitchesToNextReplica`/`testConsecutiveFailuresTriggerCooldown`/`testAllCoolingFailsExplicitlyWithSummary`（ERR_DAO_NO_REPLICA_AVAILABLE + summary + 不回退主库 write.calls==0）/`testHalfOpenProbeAndRecovery`（半开探测失败重新冷却、成功完全恢复）；无静默跳过：错误码英文文案（`DaoErrors.java:73-77` "No replica available for querySpace..."、"Invalid readwrite-splitting config..."）。
  - **Phase 3 Exit Criteria（5/5 PASS）**：装配两态 `TestReadWriteDataSourceInitializer` 7/7（含 `testUnconfiguredMeansZeroBehaviorChange`：无 querySpace 注册、defaultFactory 不动；4 个 fail-fast 用例均 ERR_DAO_READWRITE_CONFIG_INVALID）；**接线验证（Rules #23）**`testWiredThroughTransactionTemplateObservable`——初始化器构造路由 DS（default 经 `setDefaultFactory`、qs2 经 `addQuerySpace`）注册进 `DefaultTransactionManager`，经 `TransactionTemplateImpl`+`JdbcTemplateImpl` 执行只读 SQL 命中从库 bean r3、无标志命中 w2；sql-lib 生成链：`sql-lib.xdef:22,29` readOnly 属性 → `_gen/_SqlItemModel` 纯 regen 字段 → `SqlItemModel.buildSql:86-88` 传播 → `TestSqlLibReadOnlyFlag` 1/1；`git show 35e40f53ba` 该提交 `_gen/` 仅 `_SqlItemModel.java` 一个文件，diff 形状为字段+getter/setter+putNotNull+clone 纯 regen 产物，无手改痕迹。
  - **Phase 4 Exit Criteria（3/3 PASS）**：端到端双方向 `TestReadWriteSplittingIntegration` 5/5（primary 1 行/replica 空表判别：无事务只读→replica 0 行、对照无标志→primary 1 行、事务内→primary、runWithPrimary→primary、写→primary 且 replica 不受影响、`testConnectionsReturnedToPools` 写库 checkNoActive+replica MXBean==0）＋ `TestReadWriteSplittingOrmE2E` 7/7（`testOrmFindPagePaginationPathHitsReplica` 分页重建路径回归钉、事务内/写/runWithPrimary 走主库、`testSqlLibReadOnlyAnnotationRouting` 双向路由）；owner doc `docs-for-ai/02-core-guides/datasource-and-readwrite-splitting.md` 与 INDEX.md:84 路由存在；测试基类放宽（`JdbcTestCase.java:35-36,90` 与 `AbstractJdbcTestCase.java:35-36,90` 均 `DataSource`）。全量回归（nop-dao 296/nop-orm 508）为提交 35e40f53ba 记录 + 本轮定向重跑全绿（见下）。
  - **Anti-Hollow 五链（全 PASS）**：a) 只读意图：JdbcQueryExecutor SELECT 标记 → withTxnForSql REPLICA 作用域 → 路由 DS 从库通道——断言链 `TestJdbcReadOnlyBridge.testReadOnlySqlAcquiresInReplicaScope`（HintRecordingDataSource 在 getConnection() 点观测）→ `TestReadWriteDataSourceInitializer.testWiredThroughTransactionTemplateObservable` → `TestReadWriteSplittingIntegration.testNoTxnReadOnlyQueryHitsReplica`；b) 三级判定：显式 hint 覆盖（`testExplicitPrimaryHintNotOverridden`/`testRunWithPrimaryForcesPrimary`×2）、事务内主库（`testOpenTransactionStaysPrimary`/`testQueryInOpenTransactionHitsPrimary`/`testOrmQueryInOpenTransactionHitsPrimary`）、只读从库（`testNoTxnReadOnlyQueryHitsReplica`）；c) 故障冷却：换台（testFailureSwitchesToNextReplica）→冷却（testConsecutiveFailuresTriggerCooldown）→半开→恢复（testHalfOpenProbeAndRecovery）/全挂报错带摘要不回退主库（testAllCoolingFailsExplicitlyWithSummary）；d) 装配接线：testWiredThroughTransactionTemplateObservable（setDefaultFactory 替换 + addQuerySpace 两路）＋分页不断裂（`TestSqlReadOnlyFlag.testBeginFromSqlKeepsReadOnly`+`testPagedSqlKeepsReadOnlyFlag`+`testOrmFindPagePaginationPathHitsReplica`）；e) sql-lib readOnly 双向：`TestSqlLibReadOnlyFlag`（标志级 true/false）+`testSqlLibReadOnlyAnnotationRouting`（路由级 true→replica 0 行、未标注→primary 1 行）。
  - **工具 gate（本轮实测）**：`check-plan-checklist.mjs --strict` 退出码 0（非 completed 计划仅警告：2 条未勾——本条 audit gate 已由本轮补勾、checklist 复核条留主会话执行；Evidence 短于 50 字符的提示随本段写入消除）；`scan-hollow-implementations.mjs --module nop-persistence/nop-dao --severity high` 退出码 0（117 文件 0 发现）；`check-doc-links.mjs --strict` 退出码 0（0 errors，5 条 warning 均位于 `2320-orm-sharding-strategy.md` 与 `nop-bytecode/06-resource-leak-v1.md`，与本 plan 无关）。
  - **定向测试（本轮独立实跑，surefire 时间戳 2026-10-06 01:31-01:32，全部 0 失败）**：nop-dao `TestReadWrite*` = TestReadWriteHint 6 + TestReadWriteRoutingDataSource 12 + TestReadWriteDataSourceInitializer 7 + TestReadWriteSplittingIntegration 5；补跑 TestJdbcReadOnlyBridge 7；nop-orm TestReadWriteSplittingOrmE2E 7 + TestSqlLibReadOnlyFlag 1；回归抽查 TestDynamicDataSource 3、nop-core TestSqlReadOnlyFlag 5。合计 53 例全绿。
  - **契约一致性**：设计文档 `ai-dev/design/nop-dao-readwrite-splitting-design.md` 状态 active（2026-10-06）＋ §〇 实施裁定表（初始化器生命周期 / `nopReadWriteDs_` 前缀 / 配置空零行为变更 / 桥接点 / ORM 标记范围 / String 配置形态）与 live 代码逐项吻合；owner doc 判定矩阵、配置契约（键名/缺省值）、装配行为、bean 前缀与 `ReadWriteRoutingDataSource`/`ReadWriteDataSourceInitializer`/`DaoConfigs.CFG_DAO_READWRITE` 实际行为一致；INDEX.md:84 路由存在。
  - **_gen/ 手改检查**：提交 35e40f53ba 全部 `_gen/` 变更仅 `_SqlItemModel.java`，diff 为 xdef 属性驱动的标准 regen 形状（字段注释逐字来自 xdef `@readOnly` 文档），PASS。
  - **Deferred/Follow-up 分类检查**：GenSqlHelper 物理路径只读标记（v1 fail-safe 主库，设计文档 Non-Goals 明确）、quarkus 侧装配验证（Out of Scope）、权重运行期调整——均为 optimization/out-of-scope 性质，无 in-scope live defect 或 contract drift 被降级，PASS。
  - 备注：工作区未提交的 `ai-dev/design/nop-orm-scope-and-non-goals.md` 修改与 `nop-orm-sharding-strategy-design.md` 新文件属 plan 2320（脱敏/sharding）工作面，与本 plan closure 无关。
