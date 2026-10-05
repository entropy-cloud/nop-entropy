# Apache ShardingSphere 功能与 nop-orm 覆盖度对比分析

> Status: resolved
> Date: 2026-10-05
> Scope: apache/shardingsphere master（5.5.4-SNAPSHOT，commit 0163d9d79，克隆于 `~/sources/shardingsphere`） vs nop 平台数据访问层（`nop-persistence/nop-dao`、`nop-orm`、`nop-orm-eql`、`nop-dbtool`、`nop-db-migration`、`nop-tcc`，外围 `nop-metadata`、`nop-stream`）
> Conclusion: **未被覆盖是主基调**。ShardingSphere 的核心中间件能力——分库分表执行引擎（跨分片路由/改写/归并）、读写分离、影子库、数据搬迁管道、集群治理——nop 平台缺失（分片配置面与读写分离已有目标设计，见缺口清单）。透明列加密 nop **有**（`enc` 列标签 + binder 层透明加解密），可检索加密（assisted/like 辅助列）已有执行计划；脱敏经 2026-10-05 裁定为 meta 层概念、不做存储层移植。反方向 nop 在 ORM 全功能与 Schema 管理（模型→DDL、diff 升级、版本化迁移）上是 ShardingSphere 完全不具备的能力。XA/2PC 缺口为既有设计裁定 non-goal（`ai-dev/design/nop-orm-scope-and-non-goals.md`），不构成待补缺口。

## Context

- 要回答的问题：ShardingSphere 的功能是否已被 nop-orm 覆盖？还剩哪些缺口？
- 方法：克隆 ShardingSphere master 到 `~/sources/shardingsphere`（版本 5.5.4-SNAPSHOT），双端各派探索代理做全模块盘点，关键结论由本人对 live code 抽查核实（nop 侧抽查了 `io/nop/dao/shard/` 包、`GenSqlTransformer.java:205` 的跨 querySpace 禁止、`TAG_MASKED` 的消费点、全仓读写分离检索零命中、`nop-tcc` 模块存在性）。
- 两点前置事实，避免用旧认知做对比：
  1. **ShardingSphere master 自身在大裁剪**：db-discovery、traffic 两个功能模块已整体删除；加密内置算法只剩 AES + MD5（无 RC4/SM3/SM4/模糊加密）；主键生成只剩 SNOWFLAKE + UUID（无 CosId/NaNoid）；读写分离只剩静态分组（动态发现随 db-discovery 删除）；Spring Boot Starter / Spring namespace 早在 5.3.0 移除；SQL formatting、logging rule 在 5.5.3 移除。
  2. **nop 侧已有边界裁定**：XA/2PC 是 explicit non-goal（跨数据源按 best-effort 顺序提交 + `nop-tcc` 补偿）；读写分离被声明归属 DataSource/连接池层（`nop-orm-scope-and-non-goals.md` §3.5），ORM 层不做——目前该层也确实没有实现。

## 定位差异：先澄清比较基准

两者不是同类软件，直接逐功能打勾会产生误导：

- **ShardingSphere** 是 JDBC 增强驱动（`ShardingSphereDriver`）+ 独立代理进程（MySQL/PostgreSQL/openGauss/Firebird 协议）：在**运行期解析裸 SQL**，做 route → rewrite → execute → merge 四段管线，把多台异构数据库虚拟成一个逻辑库。它不提供任何 ORM 能力——没有关系映射、脏检查、缓存、关联加载。
- **nop-orm** 是元模型驱动 ORM：实体形态编译期由 codegen 确定，EQL（SQL + 关联展开）在**编译期**翻译为方言 SQL，配会话/L1/L2 缓存/批量/N+1 预取。它不做分片中间件。
- 真正的重叠面只有一条窄带：**多数据源路由 + SQL 改写**。其余维度是互补关系，不是竞争关系。

## 逐能力对比

判定：✅ 已覆盖 / 🟡 部分覆盖或语义错位 / ❌ 未覆盖。

| 能力维度 | ShardingSphere 5.5.4-SNAPSHOT | nop 平台现状 | 判定 |
|---|---|---|---|
| 分库分表路由 | standard/complex/hint/broadcast/unicast 五类路由（`ShardingRouteEngineFactory`），分库+分表双策略（`ShardingTableRuleConfiguration.databaseShardingStrategy/tableShardingStrategy`） | `useShard`/`shardProp` 实体级声明 + `IShardSelector` SPI；`GenSqlTransformer` 按 shard 值切换 querySpace 并给表名加后缀。**单分片、单库内分表**，且一条 SQL 禁止跨 querySpace（`ERR_ORM_NOT_SUPPORT_MULTIPLE_QUERY_SPACE_IN_ONE_SQL`） | ❌ 仅 SPI 骨架 |
| 内置分片算法 | 10 个：MOD/HASH_MOD/VOLUME_RANGE/BOUNDARY_RANGE/AUTO_INTERVAL/INTERVAL/CLASS_BASED/INLINE/COMPLEX_INLINE/HINT_INLINE | 仅 `EmptyShardSelector`（不分片），无任何内置算法 | ❌ |
| 跨分片查询归并 | `infra/merge MergeEngine`（排序/分组/聚合归并）+ 计算节点并行执行 | 无。查询单分片出口，无 fan-out、无归并 | ❌ |
| 绑定表/广播表/单表 | `ShardingTableReferenceRule`（绑定表）、`features/broadcast`（写全节点广播）、`kernel/single`（单表自动路由+元数据装载） | 无对应机制 | ❌ |
| 读写分离 | 静态分组（write + read 列表）、事务内读策略 PRIMARY/FIXED/DYNAMIC、RANDOM/ROUND_ROBIN/WEIGHT 负载均衡、注册中心联动剔除故障副本 | 无。设计裁定归属 DataSource 层（§3.5），但该层未建任何读写分离实现（全仓检索零命中） | ❌（已声明归属他层，待建） |
| 透明列加密 | cipher + assistedQuery + likeQuery 三列模型，SQL 改写对查询/写入透明换列，内置 AES/MD5 | **有**（勘误后修正）。列打 `enc` 标签 → `DefaultOrmColumnBinderEnhancer`（`orm-defaults.beans.xml:36` 缺省装配）以 `EncodedDataParameterBinder` 包装绑定器：写时 `ITextCipher.encrypt` + `@enc:` 前缀落 VARCHAR，读时自动解密；缺省 `AESTextCipher` AES/GCM v1 版本化格式（随机 IV + PBKDF2 派生密钥，`nop.crypt.default-enc-key`/`nop.crypt.default-iv`）。缺 assisted/like 辅助列：v1 随机 IV 下密文不确定，加密列等值查询不可命中（legacy 静态 IV 模式下可命中，`GenSqlHelper` 全部过滤点已核实走增强绑定器）；无模糊查询列 | 🟡 存储透明加密已覆盖，可检索加密缺失 |
| 数据脱敏 | 8 个 mask 算法（MD5/KEEP_FIRST_N_LAST_M/KEEP_FROM_X_TO_Y/MASK_* /特殊字符遮盖/随机替换），查询结果级通用脱敏 | 不移植（2026-10-05 用户裁定：脱敏属 meta 层概念，非存储层职责）。既有机制：`TAG_MASKED` 用于 SQL 日志打印脱敏；字段不暴露靠 xmeta `not-pub`；实际模式为实体/xmeta 提供脱敏展示字段（日常使用），敏感字段默认不发布。残余缺口仅为惯例辅助与文档化 | 🟡 裁定不移植，模式已有基础 |
| 影子库（压测路由） | shadow 规则：SQL Hint / 列值匹配 / 正则匹配三类算法，按 DML 语句类型提取影子值 | 无 | ❌ |
| 跨库联邦查询 | `kernel/sql-federation`：Calcite provider，跨分片/跨库 JOIN、子查询、聚合下推执行（`ShardingSQLFederationDecider` 触发） | ORM 层禁跨 querySpace；`nop-metadata` 有应用层跨库 JOIN（同库原生 SQL、跨库**内存拼接** `ExternalExternalJoinAggregationProcessor`）——BI 分析场景可用，OLTP 随机查询场景不可用 | 🟡 BI 场景覆盖，OLTP 缺失 |
| 分布式事务 | LOCAL / XA（Atomikos、Narayana，另新增 jakarta 变体）/ BASE（Seata AT） | 本地事务（自研 `ITransactionManager`，非 JTA）+ 事务组 best-effort 顺序提交 + `nop-tcc`（TCC 补偿，DB 记录持久化） | 🟡 TCC 对应 BASE 补偿路线；**XA 为既有裁定 non-goal，不算缺口** |
| 分布式 ID | SNOWFLAKE、UUID | `SnowflakeSequenceGenerator`（美团 Leaf 系）+ `UuidSequenceGenerator` + 业务编码规则（`nop-sys` `NopSysCodeRule`）+ DB 原生 sequence | ✅ |
| SQL 解析器 | 11 方言全量语法（mysql/postgresql/oracle/sqlserver/opengauss/clickhouse/doris/hive/presto/firebird/sql92），DDL/DCL/DAL/TCL/LCL 全量 | EQL（ANTLR，SQL92 实体子集 + 关联展开 + CTE/窗口函数/LATERAL），按方言能力位编译期翻译（15 种 dialect.xml：含达梦/TDengine/DuckDB 等国产与新兴库） | ✅ 目的一致、路线不同；语法面互有长短（SS 面向裸 SQL 全量，EQL 面向实体模型） |
| SQL 方言翻译 | `kernel/sql-translator`（provider 仅 NATIVE，即"翻译失败回退原库"） | EQL 编译期按能力位翻译 + `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE` fail-fast | ✅ 反超（编译期确定性 vs 运行期兜底） |
| 数据搬迁管道 | `kernel/data-pipeline`：全量+增量迁移（MySQL binlog/PG/openGauss WAL）、CDC 服务端+客户端（自研 protobuf 协议）、一致性校验、限流 | `nop-db-migration` 是 **schema** 版本迁移（Liquibase 风格），不搬数据。CDC 底座有（`nop-stream-connector-debezium` 源 + `nop-stream-connector-jdbc` exactly-once sink），但无"分片扩容搬迁"类开箱作业 | 🟡 schema 迁移 nop 更强；数据搬迁管道缺失（底座存在） |
| 集群模式/配置治理 | standalone（MEMORY/JDBC）+ cluster（ZooKeeper/etcd）、规则热更新（`RuleItemConfigurationChangedProcessor`）、DistSQL（RAL/RDL/RQL/RUL）管理面 | 无对应运行时治理面。配置走 NopIoC + delta 静态装载；无注册中心驱动的规则热更新、无 DistSQL 类管理 DSL | ❌ |
| SQL 审计 | `SQLAuditEngine` + 分片条件审计算法（拦截无分片键全路由 DML） | 无（有行级数据权限 filter，但那是授权不是审计） | ❌ |
| 可观测性 | agent 插件：Prometheus metrics、OpenTelemetry tracing、SQL 日志 | `IDaoMetrics`/`IOrmMetrics`/`ITransactionMetrics` + SQL 日志（含 masked 参数） | 🟡 粒度不同，均可用 |
| ORM 能力（反向） | **无**（显式不提供，要求用户已有 ORM/JDBC） | 关系映射/级联/脏检查/L1+L2 缓存/批量/N+1 批量预取/GraphQL 自动 CRUD/多租户/逻辑删除/乐观锁/审计戳/拉链历史（`useRevision`）/行级数据权限/ORM 事件拦截器 | ✅ nop 反超 |
| Schema 管理 | **无**（假设目标库 schema 已存在） | `DdlSqlCreator`（模型→DDL）、`OrmDbDiffer`/`DataBaseUpgrader`（模型-库 diff + 安全升级，不 drop）、`nop-db-migration`（版本化迁移）、`JdbcMetaDiscovery`（逆向工程） | ✅ nop 独有 |

## 缺口清单（按优先级）

> 本清单的执行组织由 `ai-dev/backlog/nop-persistence-middleware-roadmap.md` 承载（2026-10-05 立项，WI0–WI11 + M0–M5 分期与依赖图）；本 analysis 保留为缺口核定记录。

**P0 —— ShardingSphere 存在意义所在的核心能力**

1. **分库分表执行引擎**：跨分片 fan-out 执行 + 结果归并（排序/分组/聚合）、跨分片 JOIN/分页/聚合。**配置面已有目标设计**：`ai-dev/design/nop-orm-sharding-strategy-design.md`（2026-10-05，草案）——策略注册表（MOD/HASH_MOD/RANGE/INTERVAL/INLINE 内置算法，yaml 配置）+ `ShardSelection(querySpace, shardName)` 两级路由 + 缺分片键 fail-fast + DDL N 表展开；绑定表由"同策略名"隐式覆盖、SQL 审计（SS `DML_SHARDING_CONDITIONS` 的等价物）由缺分片键 fail-fast 覆盖；设计同时裁定删除零调用方的 `selectShards` 死 SPI 面，广播表明确拒绝（超出单 querySpace 事务模型）。**执行引擎（fan-out+归并）仍为缺口**，设计文档明确将其独立立项（与单 querySpace 连接模型冲突，须连同事务边界设计）。
2. **读写分离**（DataSource 层）：静态分组 + 负载均衡 + 事务内读策略（PRIMARY/FIXED/DYNAMIC 语义可直接对标）。这是 `nop-orm-scope-and-non-goals.md` §3.5 已显式划给 DataSource 层的职责，该层目前为空白。**已有目标设计**：`ai-dev/design/nop-dao-readwrite-splitting-design.md`（2026-10-05，草案）——`ReadWriteRoutingDataSource` 装饰器（`nopDataSource_<querySpace>` 装配无感知）+ 三级判定（显式 hint > 事务内主库 > 只读意图从库，复用 `statementKind` 元数据不做 SQL 解析）+ 进程内故障冷却；拒绝隐式 sticky、静默回退主库、注册中心发现。

**P1 —— 有真实生产诉求的中间件功能**

3. **通用脱敏——已改判为 meta 层概念，不做移植（2026-10-05 用户裁定）**：脱敏不是 ORM/存储层职责，是 xmeta（meta 层）的发布/展示概念；SS 的列级 mask 算法改写引擎不移植（masked 值写回即脏数据，持久化语义二义）。实际模式：实体/xmeta 上提供**脱敏展示字段**（计算属性，日常查询与页面使用它），真正敏感字段以 `not-pub` 等发布控制默认不暴露。`TAG_MASKED` 维持既有定位（SQL 日志打印脱敏）。**残余缺口**从"执行引擎"降级为"惯例支持"：脱敏计算属性的标准化辅助（常用 mask 算法工具函数）、该模式在 owner doc 的文档化。裁定已登记 `ai-dev/design/nop-orm-scope-and-non-goals.md` §四。
4. **数据搬迁管道**：分片扩缩容/机房迁移所需的全量+增量+一致性校验作业。CDC 底座（Debezium source + JDBC exactly-once sink）已具备，缺作业编排层。
5. **OLTP 跨库联邦**：Calcite 式下推联邦。BI 场景已由 `nop-metadata` 应用层聚合覆盖，此项仅在"OLTP 随机跨库查询"真实出现时才值得做。

**P2 —— 治理与外围**

6. **加密列的等值/模糊检索**：SS encrypt 三列模型中的 assistedQuery（确定性加密辅助列）与 likeQuery（分词辅助列）。nop 的 `enc` 标签透明加密已覆盖静态存储保护（见勘误记录），缺的是"加密后仍可检索"这半边。后续执行计划：`ai-dev/plans/2318-orm-enc-assisted-query-columns.md`（draft，经对抗性审查修订）；2026-10-05 用户裁定：`updateByExample` 等 bulk UPDATE SET 涉及 enc 列**不在框架层处理**（归业务层校验，owner doc 显式声明限制），已落入 plan 的 Non-Goals 与 Deferred But Adjudicated。
7. 影子库/压测流量路由。
8. 数据源/分片规则热更新与集群治理面（对标 SS mode/DistSQL；nop 的 delta + NopIoC 是否足够需按场景评估）。
9. SQL 审计（无分片键全表扫描拦截类）——**已由分片设计的缺分片键 fail-fast 覆盖**（`missing-shard-policy` 缺省 fail 即 SS `DML_SHARDING_CONDITIONS` 审计的等价物，见 `ai-dev/design/nop-orm-sharding-strategy-design.md` §3.4）。

**不列入缺口（既有裁定）**

- XA/2PC：`nop-orm-scope-and-non-goals.md` §四 explicit non-goal，跨数据源走 best-effort 顺序提交 + `nop-tcc`。
- 动态数据库发现（SS 已删除 db-discovery）、traffic 流量规则（SS 已删除）——上游已裁撤，无对齐对象。

## nop 已覆盖或反超的部分

- 分布式 ID（雪花/UUID/编码规则）、SQL→方言翻译（编译期路线）、事务内本地 + TCC 补偿。
- **透明列加密**（勘误后补入）：SS 的 cipher 列透明加解密 nop 同样有——`enc` 标签驱动、binder 层对实体读写完全透明、密文自描述（`@enc:`/`v1:` 前缀，legacy 密文可无歧义识别）、密钥支持 PBKDF2 派生 + 空密钥一次性告警；SS 多出的只有 assistedQuery/likeQuery 辅助列与 JDBC 层作用面。
- 整个 ORM 与 Schema 管理维度 nop 独有：ShardingSphere 的定位是"替你路由 SQL"，假设 schema 静态、由用户 ORM 消费；nop-orm 是"从模型生成一切"。两者在架构上恰好可以叠放（nop-orm 作为 SS-JDBC 之上的 ORM 使用方在技术上是成立的，尽管从未集成）。

## 勘误记录（2026-10-05 当日复核）

初版报告误判「nop 无列加密」，经用户指正复核**不成立**，透明列加密相关结论已全部修正。根因：探索代理以 `encrypt|decrypt` 关键词检索 nop-persistence 源码，而 nop 的机制类命名为 `Encoded*`/`ITextCipher`（`DefaultOrmColumnBinderEnhancer`、`EncodedDataParameterBinder`、`AESTextCipher`），关键词漏检；模型侧的入口是列标签 `enc`（`OrmConstants.TAG_ENC`），不含 encrypt 字样。教训：能力盘点不能只按命名关键词检索，必须沿执行链路核对（列标签 → binder enhancer → SessionFactory 装配）。

## 可借鉴点（若立项补缺口）

1. **编译期分片是 nop 的差异化机会**：SS 必须运行期解析任意裸 SQL 做路由/改写，nop 实体的分片键在模型里编译期已知，EQL 编译阶段就能完成路由决策与表名改写（现有 `GenSqlTransformer` 已是这个思路的雏形），归并引擎是真正要新建的部分。
2. **规则可插拔分层**：SS 的 feature = api(RuleConfiguration) + core(Router/Decider/RewriteDecorator/MergeEngine) + distsql 的分层清晰，nop 若建读写分离/分片引擎可对标该切面组织代码。
3. **脱敏算法集**：SS 的 8 个通用 mask 算法可直接作为 nop `TAG_MASKED` 执行引擎的算法清单（顺带说明手机号/身份证专用算法 SS 也没有，master 上均为通用字符串算法）。
4. **低成本对齐时机**：SS 自身在收缩（删 db-discovery/traffic/Bitronix/多 tracing 插件），对照 master 而非旧文档立项，避免对齐已被上游删除的能力。

## Open Questions

- [x] 平台当前/近期用户是否存在真实多库分片诉求？——**部分收敛（2026-10-05）**：分片策略配置的目标设计已按用户要求核定（`ai-dev/design/nop-orm-sharding-strategy-design.md`，草案）；剩余未决：是否立项实施（设计转 active 需要 plan + 执行引擎边界的后续设计）。
- [x] 通用脱敏引擎放在哪一层？——**已裁定（2026-10-05 用户）**：脱敏是 meta 层（xmeta）概念，不做 ORM 层脱敏引擎，SS mask 算法引擎不移植；采用"脱敏展示字段 + 敏感字段不发布"模式，残余工作为惯例辅助与文档化（见缺口清单 P1 #3 与 `ai-dev/design/nop-orm-scope-and-non-goals.md` §四）。加密列检索已立项：`ai-dev/plans/2318-orm-enc-assisted-query-columns.md`（扩展属性声明辅助列方案，Phase 0 将裁定 legacy 确定性模式是否作为补充契约）。
- [ ] `nop-metadata` 应用层联邦（跨库内存拼接）是否已覆盖全部分析场景，从而推迟 Calcite 式下推联邦？

## References

- 本地克隆：`~/sources/shardingsphere`（master 5.5.4-SNAPSHOT，commit 0163d9d79）
- ShardingSphere 侧证据：`features/sharding/core/src/main/resources/META-INF/services/...ShardingAlgorithm`（10 算法 SPI）、`features/sharding/core/.../route/engine/ShardingRouteEngineFactory.java`、`features/readwrite-splitting/api/.../TransactionalReadQueryStrategy.java`、`features/encrypt/api/.../EncryptColumnRuleConfiguration.java`、`features/mask/core/src/main/resources/META-INF/services/...MaskAlgorithm`（8 算法）、`features/shadow/core/.../spi`、`kernel/sql-federation/.../CalciteSQLFederationProvider.java`、`kernel/transaction/core/.../ShardingSphereTransactionManagerEngine.java`、`kernel/data-pipeline/core/job/type/PipelineJobType.java`、`mode/type/cluster/repository/provider/`（zk/etcd）、`infra/executor/audit/SQLAuditEngine.java`
- nop 侧证据：`nop-persistence/nop-dao/src/main/java/io/nop/dao/shard/`（IShardSelector/EmptyShardSelector/ShardSelection）、`nop-persistence/nop-orm/src/main/java/io/nop/orm/sql/GenSqlTransformer.java:205`（跨 querySpace 禁止）、`nop-persistence/nop-orm/src/main/java/io/nop/orm/sql/GenSqlHelper.java`（TAG_MASKED 仅日志脱敏消费）、`nop-persistence/nop-orm/src/test/java/test/entity/TestOrmShardTable.java`、`nop-tcc/`（api/core/dao/service 全套）
- nop 透明加密证据（勘误补入）：`nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/DefaultOrmColumnBinderEnhancer.java`（TAG_ENC → EncodedDataParameterBinder）、`nop-persistence/nop-orm/src/main/resources/_vfs/nop/orm/beans/orm-defaults.beans.xml:36`（缺省装配）、`nop-kernel/nop-dataset/src/main/java/io/nop/dataset/binder/EncodedDataParameterBinder.java`（写加密/读解密 + `@enc:` 前缀）、`nop-kernel/nop-commons/src/main/java/io/nop/commons/crypto/impl/AESTextCipher.java`（AES/GCM v1 随机 IV + PBKDF2）、`nop-kernel/nop-commons/src/main/java/io/nop/commons/CommonConfigs.java`（`nop.crypt.default-enc-key`/`nop.crypt.default-iv`）、`nop-persistence/nop-orm/src/test/resources/_vfs/nop/test/orm/app.orm.xml`（enc 标签用法）
- `ai-dev/design/nop-orm-scope-and-non-goals.md` —— XA non-goal、读写分离归属 DataSource 层的既有裁定
- `ai-dev/design/nop-orm-sharding-strategy-design.md` —— 分片策略配置目标设计（2026-10-05 新增，缺口 P0#1 的配置面收敛）
- `ai-dev/plans/2318-orm-enc-assisted-query-columns.md` —— 可检索加密执行计划（缺口 P2#6 的承接）
- `docs-for-ai/02-core-guides/eql-and-database-compatibility.md` —— EQL 方言能力位与语法边界
- `docs-for-ai/01-repo-map/module-groups.md` —— nop 数据访问层模块归属
