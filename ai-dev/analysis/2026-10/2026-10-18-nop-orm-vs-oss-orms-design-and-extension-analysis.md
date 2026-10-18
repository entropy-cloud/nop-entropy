# nop-orm 与 40+ 开源 ORM 对比：设计理性裁定与扩展功能清单

> Status: resolved
> Date: 2026-10-18
> Scope: nop 仓库 HEAD 2168ae056e（2026-10-17）的 nop-persistence/{nop-orm,nop-orm-model,nop-orm-eql,nop-dao,nop-dbtool,nop-db-migration,nop-orm-migration,nop-orm-drivers,nop-orm-geo,nop-orm-rpc}；对照 ~/sources/orm 下 40 个仓库 + 本次补克隆的 jOOQ 3.22.0-SNAPSHOT 与 Jimmer（不限语言）
> Conclusion: **按「必要能力 + 设计理性」口径，nop-orm 的设计领先于主流开源 ORM；按「功能清单逐项最多」口径，这个问题不成立——清单中大量能力是 JPA 运行期模型/POJO 假定的产物或反模式。** 继承/List/数组/生成列/对象图抓取/异步/强类型 DSL/自然键/命名 filter 等均属主动不做或已有替代；Schema 迁移与方言是「已有 + roadmap 工程化」。真正剩余的工程缺口只有三类：事务隔离级别（known gap）、native SQL 的租户/权限/写保护/审计（安全向）、迁移/方言/可观测的工程化硬化。实体级 upsert/RETURNING 便利层已否决——EQL 单行 upsert、nop-dao 批量原语、sql-lib returning 三路分工已覆盖。

## Context

### 要回答的三个问题

1. nop-orm 有哪些能力，边界在哪里？
2. 还有哪些**必要且值得做**的扩展功能？
3. 它的设计是否超越了所有其他开源 ORM（不限语言）？

### 材料与方法

- **nop 侧**：直接读 live code（HEAD 2168ae056e），关键结论落到 path:line；不以 README/宣传为依据。
- **外部侧**：~/sources/orm 下 40 个仓库（java/jvm/go/rust/python/ts/cs/ruby/elixir/php/optimizer 十组）全部浅克隆；额外补了 jOOQ（~/sources/orm/java/jooq，3.22.0-SNAPSHOT）与 Jimmer（~/sources/jimmer）。
- **并行调研**：5 个独立子 agent 按语系做源码级能力矩阵（底稿：_tmp/orm-research/g1-java-jpa.md、g2-java-sql.md、g3-ts-js.md、g4-go-rust-cs.md、g5-dynamic-langs.md），关键「nop 缺失」断言由本人 grep 复核。
- **复用前作**：ai-dev/analysis/2026-10/2026-10-05-orm-query-optimizer-landscape-vs-nop-orm.md（优化器/方言/缓存裁定）与 2026-10-05-shardingsphere-vs-nop-orm-gap-analysis.md（分片/读写分离/加密/迁移）。
- **设计依据**：docs/theory/lowcode-orm-1.md、lowcode-orm-2.md；ai-dev/design/nop-orm-scope-and-non-goals.md；docs-for-ai/02-core-guides/orm-model-design.md。

### 评价口径（本文的唯一框架）

本文不按「主流 ORM 功能清单」打分。原因：JPA/Hibernate 的很多能力是为运行期代理、字节码增强、POJO 假定、可变对象图服务的，脱离该模型后并不构成必要条件；把「别人有、nop 没有」直接判为落后，是评价框架错误。

本文使用两条判据：

1. **必要能力**：正确性、可运维、可扩展、与平台方向一致。
2. **设计理性**：是否引入第二套心智模型？是否产生隐式行为？是否破坏模型可逆性/跨库一致性？是否与编译期实体/无代理/元模型驱动冲突？

判断一个「缺失」之前必须先查三件事：① 是否是 non-goal；② 是否已有替代路径；③ 是否是反模式。三者任一成立，就不记为缺口。

### 本次核实的勘误

| 既有说法 | 核实结果 | 证据 |
|---|---|---|
| 「Hibernate 无内置多租户」（2026-10-05 前作） | **错误**。Hibernate 有 DATABASE/SCHEMA/DISCRIMINATOR 三策略 + @TenantId + CurrentTenantIdentifierResolver | hibernate-core/src/main/java/org/hibernate/context/spi/CurrentTenantIdentifierResolver.java、annotations/TenantId.java |
| 「nop 无 JSON↔POJO 绑定」 | **错误**。有 JsonOrmComponent（stdDomain=json 自动生成）+ DefaultJsonTypeHandler + IDataTypeHandler + IDialect.getDataParameterBinder | nop-orm/.../component/JsonOrmComponent.java:26-46、nop-dao/.../json/DefaultJsonTypeHandler.java:18 |
| 「nop 无字段级 partial update」 | **错误**。UPDATE 按 orm_dirtyPropIds() 只写脏列并按 propIds 复用 SQL | JdbcEntityPersistDriver.java:282-291 |
| 「nop 无 upsert / RETURNING」 | **不准确**。EQL 已有单行 upsert 与 insert/update/delete returning；现状是三条分工路径（EQL 单行、nop-dao 批量原语、sql-lib returning），不是缺口 | DMLStatement.g4:26-46、EqlTransformVisitor.java:1757,1772-1790,1869-1899、JdbcQueryExecutor.java:181、ai-dev/design/eql-cross-db-compat/2026-10-10-eql-upsert-and-returning-design.md §3.8/§3.9 |
| 「nop 无逻辑删除恢复」 | **不准确**。ORM 层没有 restore，但 BizModel 层有 recoverDeleted/deleted_get/deleted_findPage | CrudBizModel.java:989,1376,1398 |
| 「Schema 迁移/方言落后」 | **不准确**。迁移链路（db-migration + differ + reverse + migration pipeline）与方言能力模型都已存在；只是有 roadmap 工程化硬化项 | 见 1.3、1.4 |
| 「jOOQ 有 migrations」 | **不准确**。OSS 树只有 jgit 3 个类 + xml schema，执行器不在开源范围 | jOOQ-migrations/src/main/java/org/jooq/migrations/jgit/ |

## 1. nop-orm 能力地图

### 1.1 定位

nop-orm 是**元模型驱动**的编译型 ORM：实体结构由 orm.xml（XDSL）描述，实体 Java 类由 codegen 生成，运行期不做字节码增强、不建代理；模型可继承、可 Delta 覆盖。引擎只消费只读元模型（nop-orm-model）、只持有 EQL 编译产物（nop-orm-eql）、只通过 IJdbcTemplate/ITransactionTemplate 执行（nop-dao）。

一句话：**Hibernate 式会话语义 + MyBatis 式显式 SQL + jOOQ 式编译期跨库翻译 + 平台级模型/代码生成闭环**，但每一轴都做了取舍，目标是「模型可逆、跨库可移植、语义可预测」，而不是「功能项最多」。

### 1.2 十层能力表（live code 证据）

| 层 | 能力 | 关键证据 |
|---|---|---|
| 1 元模型 | orm.xml/entity.xdef：实体、列、propId（删除不重排）、domain/stdDomain/stdSqlType/stdDataType 四层类型、alias、component、compute（XPL 派生属性）、filters、view entity、kvTable、dict、索引/唯一键；支持 x:extends 多父继承与 Delta 覆盖 | entity.xdef:46-228、orm.xdef:14-40；OrmModelLoader |
| 2 关系 | to-one / to-many（ref-set、sort、keyProp、maxSize）、显式 join、cascadeDelete；无继承映射/二级表/隐式 m2m（non-goal） | entity.xdef:132-200；non-goals 文档 |
| 3 会话/UoW | Hibernate 式 session；7 状态机；L1 缓存三种（普通/租户/Stateless）；脏标记 + 旧值；按脏列 UPDATE；CascadeFlusher；两阶段拓扑批量 flush；stateless/readonly；attach/detach | OrmEntityState.java:10-57、IOrmEntity.java:101-313、BatchActionQueueImpl.java:153-200 |
| 4 查询 | EQL（CTE/窗口/LATERAL/VALUES/FULL+RIGHT JOIN/集合算子/子查询/分页/锁 NOWAIT+SKIP LOCKED/空序改写/UPSERT/RETURNING）；sql-lib.xml（native/eql/query + fragments + 权限 + 超时 + fetchSize）；@SqlLibMapper；QueryBean/FilterBeans；MDX 多源查询（主子拆 SQL + 内存对齐，可跨 querySpace） | EqlCompiler.java:40-46、EqlTransformVisitor.java:455-472,1467、SqlLibManager.java:188、MdxQuerySplitter.java:46-105 |
| 5 执行期优化 | 批量装载队列消 N+1、keyset 分页、按 propId 复用 SQL 模板、零反射属性访问、JDBC batch、Caffeine 计划缓存、L1/L2/查询结果/session 缓存、实体 _t memo、JOIN 引用计数去重 | OrmBatchLoadQueueImpl.java:577,679、JdbcEntityPersistDriver.java:62-97、OrmSessionFactoryBean.java:95-119、JdbcTemplateImpl.java:371-395 |
| 6 数据语义 | 多租户（写强制/读注入/L1 隔离）、逻辑删除（deleteFlag/deleteVersion + 自动过滤 + BizModel 恢复）、审计戳、修订 useRevision、行级数据权限（filter + IDataAuthChecker）、透明加密 enc + 可检索加密、脱敏展示字段、乐观锁 version | docs-for-ai/02-core-guides/{tenant-model,logical-deletion,orm-model-design}.md；GenSqlHelper.java:467,703-713 |
| 7 分布式/多数据源 | querySpace；DataSource 读写分离（hint > 事务内主库 > 只读意图从库 + 负载均衡/故障冷却）；分片（策略 + 5 算法 + 单分片路由 + fan-out/归并 + DDL N 表展开）；数据搬迁五状态机 + CDC + 校验 + 写停止关 | ReadWriteRoutingDataSource.java:43-72；shard/ShardFanoutEngine.java；nop-orm-migration/ |
| 8 Schema 生命周期 | 模型→DDL；模型-活库 diff 安全升级（只加不 drop）；版本化迁移（VERSIONED/REPEATABLE、checksum、predicate、16 executor）；反向工程；PDM 导入；建表/数据初始化 | DdlSqlCreator.java、DataBaseUpgrader.java:132-134、MigrationEngine.java:164-185、JdbcMetaDiscovery.java |
| 9 全栈闭环 | 模型→实体/xmeta/API/GraphQL/页面/i18n/codegen；Delta 多客户覆盖；DynamicOrmEntity 免 codegen；nop-dyn 运行时实体/页面；GraphQL 自动 API + DataLoader | model-first-development.md、delta-customization.md、OrmDaoProvider.java:50-58、nop-dyn/ |
| 10 AI/元数据 | LLM 输出 ORM 模型 → OrmModel（GptOrmModelParser）；nop-dyn GptCodeGen；nop-metadata 语义层/数据治理建在 ORM 上；MDX 多源查询 | GptOrmModelParser.java:30-90、GptCodeGen.java:16-20 |

### 1.3 主动设计取舍（不是缺口）

这些能力 nop 明确不做；每一项都有设计理由或替代路径，不应记为「落后」。

| 能力 | 处置 | 理由/替代路径 |
|---|---|---|
| 对象继承映射（JOINED/TPC/单表判别符） | 不做 | 与「实体形态编译期确定 + 无代理 + L1 身份键 (entityName,id)」冲突；破坏模型→Java 可逆性（lowcode-orm-2 §4.3；non-goals） |
| List/集合下标映射 | 不做 | 集合语义由 Set + 指针唯一性自然推出；List/index 需要额外假设与 HQL 特殊语法（lowcode-orm-2 §4.1） |
| 数组/STRUCT/元素集合 | 不做 | 关系模型应规范化；半结构化用 JsonOrmComponent/component/kvTable；数组列破坏跨库/分片一致性 |
| JPA 二级表 | 不做 | 字段到表映射必须编译期确定；跨表需求由 alias（关联投影/kvTable 纵表/组件路径）承担 |
| 隐式 m2m 连接表 | 不做 | 连接表就是普通实体；显式建模才能获得字典/租户/逻辑删除/权限/审批/GraphQL 能力 |
| 生成列/公式列 | 不做 | 不同数据库/分布式数据库的表达式语义与 DDL 支持不一致；用 compute（XPL 应用层派生）+ sqlText（视图只读列）保证跨库一致 |
| 对象图抓取/join fetch/entity graph | 不做 | lowcode-orm-2 §6：多表 join 返回大量冗余行；BatchLoadQueue 每关系一条 IN + 内存装配，天然支持跨源，且不绑定固定访问模式；FieldSelectionBean/batchLoadSelection 已提供抓取规格 |
| 强类型 Query DSL | 不做 | lowcode-orm-2 §7：QueryBuilder 与 ORM 无关，QueryDsl 构造 SQL 无本质优势；前台 QueryForm → QueryBean → 数据权限 filter → SQL 是单一模型；复杂 SQL 用 sql-lib |
| 异步/流式/响应式 | 不做 | lowcode-orm-2 §5.3：异步是为 NoSQL/GraphQL 预留的 OrmTemplate 形态；当前 JDBC 全同步，R2DBC 是另一套执行栈；大结果集由 nop-batch/nop-stream 承担 |
| 自然键独立缓存区 | 不做 | unique-key 已声明业务唯一键；按唯一键查询 + 索引即可；独立 natural-id 缓存会引入第二 identity map 与失效复杂度 |
| 运行期命名 filter（可开关带参） | 不做 | 模型 filters + 数据权限 + QueryBean.appendFilter 已覆盖；可开关 filter 会引入隐式行为与缓存 key 复杂度 |
| 乐观锁变体（DIRTY/ALL/CHANGED_COLUMNS） | 不做 | version 列覆盖绝大多数业务；额外变体引入 WHERE/索引负担，收益不确定 |
| BulkCopy 进 ORM | 不做（按需在 dao 层） | 导入/搬迁由 nop-batch/nop-stream 承担；ORM 层 COPY 会绕开拦截器/审计/多租户 |
| Savepoint / XA / ORM 层 PS 缓冲 / Criteria / ORM 层脱敏引擎 | 不做 | non-goals 文档逐项裁定 |

### 1.4 已有替代路径（不是缺口）

| 需求 | nop 的现有机制 | 证据 |
|---|---|---|
| 列级类型转换/自定义值对象 | domain/stdDomain/stdDataType 四层 + IDataTypeHandler + IDialect.getDataParameterBinder + DefaultOrmColumnBinderEnhancer + IOrmComponent（Json/File/Xml/FloatingScaleDecimal）+ enc binder | g1/g5 底稿；DefaultJsonTypeHandler；bean 装配 |
| JSON 列 | JsonOrmComponent（stdDomain=json 自动生成）+ DefaultJsonTypeHandler；文本列存取 + 对象绑定 | JsonOrmComponent.java:26-46 |
| 修改日志/审计 | orm_dirtyOldValues/NewValues + IOrmInterceptor（lowcode-orm-2 §5.2 #5）；useRevision 拉链历史 | IOrmEntity.java:196-216；lowcode-orm-2 §5.2 |
| 逻辑删除恢复 | BizModel 的 recoverDeleted/deleted_get/deleted_findPage | CrudBizModel.java:989,1376,1398 |
| 对象图抓取 | BatchLoadQueue + FieldSelectionBean/batchLoadSelection + assembleAllCollectionInMemory（专家后门） | lowcode-orm-2 §6；IOrmTemplate.java:237-253 |
| 批量 upsert | nop-dao 的 UpsertStatements.buildNativeUpsertSqlText（nop-stream 两阶段提交 sink 已使用） | UpsertStatements.java:107；JdbcTwoPhaseCommitSink.java:1098 |
| RETURNING 取回 | sql-lib 显式声明取值型 sqlMethod（findAll/findFirst/findPage/exists）；默认 execute 丢弃返回行 | 2026-10-10-eql-upsert-and-returning-design.md §3.8；SqlMethod.java |
| Schema 迁移 | nop-db-migration（版本化/checksum/16 executor）+ OrmDbDiffer/DataBaseUpgrader + JdbcMetaDiscovery + nop-orm-migration | 1.2 第 8 层 |
| 方言差异 | nop-dao/dialect 的 XML 能力位 + 多父 mixin + Delta + 编译期硬错误 | dialect.xdef:72-85；15 个可加载方言 |
| 多源查询 | MDX：QueryBean 拆主子 SQL + 内存对齐，可跨 querySpace | MdxQuerySplitter.java:46-105 |
| 领域事件/审计事件 | IOrmInterceptor 的 pre/postSave/Update/Delete + orm_dirtyOldValues/NewValues；IOrmEntityLifecycle；IOrmDaoListener（每次 DB 访问，含 EQL）；after-commit 用 ITransactionTemplate.afterCommit(txnGroup, Runnable)（nop-datav 已用）。outbox 就是在同一 session 保存普通事件实体，不需要 ORM 新 API | IOrmInterceptor.java；IOrmEntity.java:212-216；ITransactionTemplate.java:87；NopDatavExportTaskBizModel.java:264 |
| 库/模式级多租户（database-per-tenant / schema-per-tenant） | **ShardSelector 已可表达**：ShardSelection(querySpace, shardName)；策略的 query-space-pattern 按分片名切换 querySpace（库分片），schema-per-tenant 把每个租户映射到指向目标 schema 的 querySpace/DataSource；表分片用 table-pattern。无需新增多租户策略 | ShardStrategy.java（querySpacePattern/buildSelection）、ShardSelection.java、GenSqlTransformer.java:137,236、docs-for-ai/02-core-guides/orm-sharding.md「库分片（querySpace 切换）」 |

### 1.5 真实缺口（只列三类）

1. **事务隔离级别**：known gap。ITransactionTemplate 无 isolation API，Connection#setTransactionIsolation 全仓零调用（non-goals 文档 G1 明示）。
2. **native SQL 的租户/权限/写保护/审计**：EQL/QueryBean 有注入；native SQL/sql-lib 是显式逃生口，需要写保护与审计，而不是隐式改写。这是安全/可运维问题，不是映射能力。
3. **工程化硬化（按项给出承载，不笼统说 roadmap）**：
   - **迁移**：`nop-db-migration-roadmap` 承载——WI2 并发锁、WI8 dry-run、WI9 baseline/漂移检测、WI10 rollback、WI12a/b 模型→迁移文件。2026-10-12 基线：6 done / 10 planned / 24 todo。**fake 与自动反演不在该 roadmap**：rollback 只覆盖执行语义，自动反演未立项。
   - **方言覆盖度**：**无专门 roadmap**。`eql-cross-db-compat-roadmap` 的 WI0/WI1/WI3 已 done，维护现有 14 个可加载方言的能力位/兼容性正确性；新增方言是工作量，按需求评估。
   - **可观测**：**无 ORM roadmap**。已有 `GlobalStatManager`/`JdbcSqlStat`（JVM 级 per-SQL 统计：耗时/并发/直方图/行数）、`IOrmMetrics`（session/load/save/update/delete 计数）、`IDaoMetrics`（query/update/batch 计时）、SQL 日志 `nop.jdbc.run:usedTime`；N+1 检测、慢查询捕获（`JdbcSqlStat.lastSlowParameters` 无生产 setter）、SQL 审计、OTel tracing 未实现，按需立项（已拟 draft plan `ai-dev/plans/41-orm-observability-completion.md`）。

按需项（不是缺口）：多驱动补全、分片 v2、GraphQL 订阅、AI-native NL→QueryBean。按真实场景启动。领域事件/outbox 见 1.4（已有机制，不需要新增 ORM API）。

库/模式级多租户不是缺口：现有 ShardSelector 的 `ShardSelection(querySpace, shardName)` 已能表达库分片（`query-space-pattern`）与表分片；schema-per-tenant 用「每个租户→指向目标 schema 的 querySpace/DataSource」表达。注意事项：`useShard`/`shardProp` 是实体级声明，租户值必须能进入 SQL 条件或走 missing-shard-policy；跨租户共用 session 时 L1 身份键 (entityName,id) 仍要求 id 全局唯一；DDL N 表展开只针对表分片，库/模式级租户按 querySpace 分别建库/建 schema。

### 1.6 其他 ORM 没有同类实现的能力（无对手轴）

1. 模型即数据 + Delta 可逆定制（方言层已用三父 mixin + mysql5.7 一个元素关断三个能力位证明）。
2. 全栈 codegen 闭环（实体/xmeta/API/GraphQL/页面/i18n）。
3. 31 个方言能力位 + 编译期响亮失败。
4. 模型级多租户/逻辑删除/数据权限/加密的强制注入。
5. 执行期 IO 削减组合（批量装载 + keyset + propId 模板 + 零反射 + 两阶段拓扑 flush + 四层缓存）。
6. MDX 多源查询（跨 querySpace 的应用层 join）。
7. AI 生成模型（LLM→OrmModel→动态实体/低代码）。

## 2. 行业版图：六类 ORM 与代表性设计

### 2.1 分类

| 范式 | 代表 | 核心主张 | 与 nop 的关系 |
|---|---|---|---|
| 会话式全功能 ORM | Hibernate、EF Core、SQLAlchemy、Doctrine、EclipseLink、Ebean、Jimmer | 对象图 + identity map + 脏检查 + 关联加载 | nop 是同族但做了不同取舍：保留会话/脏检查/批量装载，拒绝代理/继承/join fetch |
| SQL 映射器 | MyBatis、MyBatis-Plus、JDBI | SQL 先行、结果映射、插件链 | nop 的 sql-lib + @SqlLibMapper 覆盖同类需求 |
| 类型安全 SQL DSL | jOOQ、Kysely、Exposed、Diesel、sqlx、linq2db | 编译期校验 SQL/列/类型 | nop 主动不做；用 QueryBean/sql-lib/EQL 的单一模型替代 |
| Schema-first / codegen | Prisma、Drizzle、ent、GORM、SeaORM、ActiveRecord、Ecto | schema 即代码/契约，生成客户端/迁移 | nop 的 orm.xml 同类，但多了 Delta/全栈生成 |
| 轻量数据映射 | Peewee、Tortoise、Sequel、Illuminate、Phalcon | 简单查询 + 约定优于配置 | 对 nop 参考有限 |
| 查询引擎/优化器 | Calcite、Dolt | 规则 + 代价 + 统计 | 不是 ORM；nop 不在 ORM 层做 CBO（前作已裁定） |

### 2.2 能力面矩阵（只描述「有没有」，不代表「是否必要」）

> 矩阵用于回答「能力面上有什么」。nop 列的「主动不做」不是缺陷；判定依据见 1.3。

| 维度 | Hibernate | EF Core | SQLAlchemy | jOOQ | MyBatis-Plus | Jimmer | Prisma | MikroORM | Ecto/AR | GORM/ent | Diesel/sqlx | **nop-orm** |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 映射表达力（继承/embedded/collection/natural id/JSON/array/generated） | ✅✅ | ✅✅ | ✅✅ | ✅ | 🟡 | ✅ | ✅ | ✅ | ✅/✅ | 🟡/✅ | 🟡/❌ | ✅ 最小充分集（component/JSON 组件/kvTable/alias）；继承/List/数组/生成列主动不做 |
| 类型安全查询 | 🟡（Criteria） | ✅✅（LINQ） | ✅（2.0 Mapped） | ✅✅ | 🟡（Lambda 运行期反解） | ✅✅ | ✅ | ✅ | 🟡/❌ | 🟡/✅（ent） | ✅✅ | 主动不做（QueryBean/sql-lib/EQL 单一模型） |
| 动态查询/规格 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 🟡 | ✅ QueryBean/FilterBeans |
| 对象图抓取（join fetch/entity graph） | ✅✅ | ✅✅ | ✅✅ | 🟡（MULTISET） | 🟡（嵌套 ResultMap） | ✅✅（Fetcher） | ✅✅（单查询嵌套 JSON） | ✅✅（LoadStrategy） | ✅✅ | ✅✅/✅ | 🟡 | 主动不做 join fetch；BatchLoadQueue + FieldSelection |
| UoW/identity map/dirty | ✅✅ | ✅✅ | ✅✅ | ❌ | ❌ | 🟡 | 🟡 | ✅✅ | ✅✅ | ❌/❌ | ❌ | ✅ 7 态 + L1 + 脏列；change set/merge 按需 |
| 二级/查询缓存 | ✅✅ | 🟡（L1） | ✅ | 🟡 | ✅ | ✅✅ | 🟡 | ✅ | ✅ | 🟡 | ❌ | ✅ ICache/Caffeine/分布式 SPI；查询缓存失效按需 |
| Schema/迁移 | ✅（diff） | ✅✅（migrations） | ✅✅（Alembic） | ❌（OSS） | 🟡 | ✅ | ✅✅ | ✅✅ | ✅✅ | ✅（AutoMigrate）/✅（Atlas） | ✅（migrations） | ✅ 已有 + roadmap 工程化 |
| 多租户/软删除/审计历史/权限 | ✅✅/✅/✅（Envers）/❌ | ✅/✅/❌/✅ | 🟡/🟡/🟡/❌ | ✅/❌/❌/❌ | ✅/✅/🟡/✅ | 🟡/✅/❌/🟡 | 🟡/✅/❌/❌ | ✅/✅/❌/✅ | 🟡/✅/❌/❌ | ✅/✅/❌/❌ | ❌/🟡/❌/❌ | ✅✅ 行级租户模型级强制；库/模式级由 ShardSelector+querySpace 表达；修改日志经 interceptor，revision 为字段拉链 |
| 并发控制 | ✅✅（4 种乐观锁/悲观锁模式） | ✅（token） | ✅ | 🟡 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 🟡 | ✅ version + EQL 锁；isolation 为 known gap |
| 分片/读写分离/多数据源 | 🟡/❌/✅ | ❌/❌/✅ | 🟡/✅/✅ | ❌/❌/✅ | ❌/❌/✅ | ❌/❌/✅ | ❌/❌/✅ | ❌/❌/✅ | ✅/✅/✅ | ❌/❌/✅ | ❌/❌/✅ | ✅ DataSource 读写分离 + 分片 v1 + fan-out |
| 异步/流式/响应式 | ✅（Reactive 独立）/✅ | ✅/✅ | ✅/✅ | ✅/✅ | ❌/🟡 | ❌/❌ | 🟡/🟡 | 🟡/🟡 | 🟡/✅（AR load_async） | ❌/❌ | ✅（sqlx）/✅（Diesel） | 主动不做（JDBC 体系；nop-batch/nop-stream 承担大数据） |
| 方言/跨库失败模式 | ✅（20+ dialect 类） | ✅（~10 provider） | ✅ | ✅✅（30+ RDBMS） | ❌ | 🟡 | ✅ | ✅ | ✅ | ✅/🟡 | 🟡/✅ | ✅✅ 31 能力位 + 编译期 fail；15 方言；覆盖度按需 |
| 扩展机制 | ✅（事件/拦截器/类型） | ✅（interceptor/converter） | ✅✅（events/hybrid） | ✅（listener） | ✅（plugin） | ✅ | ✅（extensions） | ✅ | ✅（plugins） | ✅/✅ | ✅ | ✅ AST transformer/ORM interceptor/DAO listener/driver SPI；native SQL 横向切面见 4.2 |
| 全栈/元数据/定制 | ❌ | ❌ | ❌ | ❌ | ❌ | 🟡（GraphQL/DTO） | 🟡 | ❌ | ❌ | ❌ | ❌ | ✅✅ 模型→实体/xmeta/API/GraphQL/页面/i18n + Delta |
| AI/生态/成熟度 | ❌/✅✅ | ❌/✅✅ | ❌/✅✅ | ❌/✅✅ | ❌/✅ | ❌/🟡 | 🟡/✅ | ❌/🟡 | ❌/✅ | ❌/✅ | ❌/🟡 | 🟡 AI 生成模型先行；生态不参与竞争（平台内框架） |

### 2.3 各类「最强」的不可替代之处（不代表 nop 需要照做）

- Hibernate：映射表达力、JPA 标准、多租户三策略、Envers、两级缓存、生态/人才。
- EF Core：LINQ + 编译查询 + migrations + 拦截器 + 值转换器 + 全局查询过滤器 + 时序表。
- SQLAlchemy：Core/ORM 双层、2.0 类型化、loader 策略、events、Alembic、庞大方言层。
- jOOQ：schema 生成类型安全 DSL、MULTISET/ROW、SQL parser/translator、30+ RDBMS。
- Jimmer：Fetcher + SaveMode/图保存 + DTO + 缓存一致性 + SQL 优化。
- Prisma：PSL + 单查询嵌套 JSON + 迁移图 + 类型安全客户端。
- MikroORM：UoW/ChangeSet + LoadStrategy + Filter + migrations。
- Ecto：changeset + Multi。ent：schema-as-code + 图遍历 + Atlas + privacy。Diesel/sqlx：Rust 编译期校验。
- Calcite/Dolt：真正的查询优化器，不是 ORM。

### 2.4 行业常见反模式（nop 主动避开的）

- JSqlParser 式「解析 SQL 字符串再改写」：丢失注释/hint/方言细节；nop 走 EQL AST。
- 运行期 Lambda/反射反解属性名：与零反射/静态可分析冲突。
- hbm2ddl.auto=update 式隐式改库：nop 只 diff 出 SQL、显式执行、默认不 drop。
- 无 identity map + 每次重建对象（Django 式）：nop 的 L1 + 脏标记更省。
- 隐式 default scope/懒加载代理：排查「为什么查不到/为什么多了查询」的经典来源。
- 默认递归删除的图 upsert：nop 的集合变更由子实体保存/删除显式表达。
- 继承映射 + 运行期代理：L1 身份键与懒加载触发点会二义化。

## 3. 裁定：nop-orm 是否「超越了所有开源 ORM」

### 3.1 先定义「超越」

- 定义 A（功能清单逐项最多）→ **不成立，且这个问题本身不成立**：清单中大量项是反模式或为 JPA 运行期模型服务，nop 不实现不是落后。
- 定义 B（任一维度绝对最强）→ **不成立**，也没有任何 ORM 做到。
- 定义 C（若干设计轴无对手/领先）→ **成立**，见 1.6。
- 定义 D（设计更理性、更少反模式、更适配模型驱动/AI 开发）→ **成立**，见 1.3/1.4/2.4。

### 3.2 设计理性对照（不是评分卡）

| 维度 | nop 的取舍 | 判定 |
|---|---|---|
| 映射表达力 | 以物理模型自动映射为基底，只增量表达 component/alias/kvTable；拒绝继承/List/数组/生成列 | 更克制：减少不可逆选择，保持模型可逆 |
| 查询模型 | 前台 QueryForm → QueryBean → 数据权限 filter → SQL；复杂 SQL 用 sql-lib；拒绝 typed DSL | 单一心智模型，AI/低代码友好 |
| 对象图 | BatchLoadQueue + FieldSelection；拒绝 join fetch/entity graph | 跨源、无冗余行、不绑定固定访问模式 |
| 会话/写路径 | 7 状态 + L1 + 脏列 + 两阶段拓扑 flush；拒绝 merge/change set 作为内核 | 状态显式、可预测；写意图由业务层表达 |
| 缓存 | ICache/Caffeine/分布式 SPI/租户失效；查询缓存失效按需 | 基础设施强；不引入隐式失效 |
| 迁移/方言 | 模型→DDL/diff/版本化/反读 + 方言 XML 能力位 + 编译期硬错误 | 能力模型领先；覆盖度/工程化按 roadmap |
| 多租户/权限/加密 | 行级租户模型级强制；库/模式级由 ShardSelector 的 querySpace 切换表达；权限/加密模型级注入 | 领先：不依赖业务代码或第三方插件 |
| 全栈/Delta/AI | 模型→实体/xmeta/API/GraphQL/页面/i18n；Delta；LLM→OrmModel | 无对手 |
| 生态/标准 | 平台内框架，不追求 JPA 兼容与外部生态 | 不参与该维度竞争 |

### 3.3 结论

1. **按设计理性/必要能力，nop-orm 的设计领先于主流开源 ORM**：它用元模型/编译期实体/批量装载/QueryBean/sql-lib/Delta 消除了 JPA 系多数偶然复杂度，并主动避开反模式。
2. **「超越所有」若指「功能清单最多」，不成立且无意义**；若指「设计更理性、更适配模型驱动/AI 开发」，成立。
3. **剩余工程缺口只有三类**：事务隔离级别、native SQL 安全/审计、迁移/方言/可观测工程化。它们不是「别人有而 nop 没有」的映射能力。
4. **不主张「综合最强 JVM ORM」**：Jimmer/Hibernate 在各自取舍下更强（图抓取、映射表达力、生态）；nop 的强项是另一条设计轴。

## 4. 扩展功能清单（已过必要性审查）

### 4.1 审查原则

1. 不与 non-goal 冲突（继承/二级表/m2m/savepoint/XA/PS 缓存/Criteria/脱敏引擎）。
2. 不引入第二套心智模型（typed DSL 等）。
3. 不产生隐式行为（join fetch/命名 filter/自动缓存失效等默认关闭或不做）。
4. 不破坏模型可逆性/跨库一致性（生成列/数组/继承等）。
5. 与既有 roadmap 去重。

### 4.2 保留项

| ID | 功能 | 类型 | 说明 |
|---|---|---|---|
| R1 | 事务隔离级别 + 只读事务 | 真实缺口 | ITransactionTemplate 增 TransactionOptions{isolation, readOnly, timeout}；JDBC 连接应用；@Transactional 增参数。non-goals G1 已明示 |
| R2 | native SQL 安全/审计（SQL firewall） | 真实缺口（安全向） | 对 sql-lib/native SQL 做写保护 + 审计；租户/权限条件只在可获取元数据的 sql-lib 声明式 SQL 上做显式注入（不做隐式改写）；禁止 JSqlParser 式二次解析 |
| R3 | 可观测：N+1/慢查询/SQL 审计/OTel | 按需（无 roadmap） | 现有 GlobalStatManager/JdbcSqlStat、IOrmMetrics/IDaoMetrics、SQL 日志；缺 N+1 检测、慢查询捕获、SQL 审计、OTel tracing；已拟 draft plan 41 |
| R4 | 文档/示例：领域事件与 outbox 的现有实现 | 文档（已有机制） | IOrmInterceptor + orm_dirtyOldValues/NewValues + ITransactionTemplate.afterCommit；outbox 用普通事件实体同 session 保存；不新增 ORM API |
| R5 | 查询缓存表级自动失效 | 按需 | EQL 编译期已知 table 集合；写后按表失效；默认关闭 |
| R6 | 迁移工程化 | roadmap（nop-db-migration-roadmap） | WI2 锁 / WI8 dry-run / WI9 baseline+漂移 / WI10 rollback / WI12a/b 模型→迁移；状态 6 done/10 planned/24 todo；fake 与自动反演不在 roadmap |
| R7 | 方言覆盖度 | 按需（无专门 roadmap） | eql-cross-db-compat-roadmap 维护现有 14 方言能力位；新增方言按需求评估 |
| R8 | 多驱动（Mongo/ES/Redis） | 按需 | nop-orm-drivers 目前只有 TDengine；按真实场景再建或删除占位 |
| R9 | 分片 v2（跨分片 JOIN/广播表/写 fan-out） | 按需 | nop-persistence-middleware-roadmap 已明确 v1 边界 |
| R10 | GraphQL 订阅/实时查询 | 按需 | 需 nop-message/CDC 联动 |
| R11 | AI-native：NL→QueryBean/EQL + AST 校验 + explain | 战略 | 与平台方向一致；LLM 输出限制在 QueryBean/EQL 白名单，不生成第二套 DSL |
| R12 | 文档/示例：upsert/returning 三路用法 | 文档 | EQL 单行 upsert、nop-dao 批量原语、sql-lib returning 的适用边界与 sqlMethod 选择 |
| R13 | 文档/示例：库/模式级多租户用 ShardSelector 配置 | 文档 | 说明 useShard/shardProp、query-space-pattern、table-pattern、missing-shard-policy、DDL/建库边界 |

### 4.3 已否决的扩展提议（附理由）

| 提议 | 否决理由 | 现有替代 |
|---|---|---|
| E1 实体级 upsert/RETURNING API | 与设计文档 §3.9/§4.4 的三路分工冲突；与 saveOrUpdate 语义冲突；需重定义 L1/缓存/审计/version/revision | EQL 单行 upsert + nop-dao 批量原语 + sql-lib returning |
| E2 ORM 级流式游标 | JDBC 体系下收益低；大结果集由 nop-batch/nop-stream 处理 | 低层 IDataSet + fetchSize；分页 |
| E7 命名 fetch plan/join fetch/raiseload | 反模式：冗余行、绑定固定访问模式 | BatchLoadQueue + FieldSelection + assembleAllCollectionInMemory |
| E8 类型安全 DSL | QueryBuilder 与 ORM 无关；第二套心智模型；AI 生成 QueryBean 更自然 | QueryBean/FilterBeans + sql-lib |
| E9 生成列/公式列/表达式索引/check/分区/物化视图 | 跨库/分布式语义不一致；生成列是历史遗留设计 | compute（XPL 派生）+ sqlText（视图只读列）；DDL 中必要项按需 |
| E10 自然键 API + 缓存区 | 第二 identity map；unique-key + 查询已覆盖 | unique-key + 索引 |
| E11 运行期命名 filter | 隐式行为 + 缓存 key 复杂化 | 模型 filters + 数据权限 + QueryBean.appendFilter |
| E12 乐观锁变体/悲观锁参数化 | version 已覆盖多数场景；额外变体收益不确定 | version + EQL for update/nowait/skip locked |
| E15 JSON 路径/向量进 ORM | JSON 路径应按需走 compute/sql-lib/元数据层；向量属 nop-ai | JsonOrmComponent + 元数据层 + nop-ai |
| E19 R2DBC | 另一套执行栈；当前 JDBC 全同步 | 阻塞 JDBC + nop-batch/nop-stream |
| E22 BulkCopy 进 ORM | 绕开拦截器/审计/多租户 | nop-batch/nop-stream + dao 层按需 |
| E23 写侧 mutation policy | 与数据权限/拦截器职责重叠；易变成隐式规则 | IOrmInterceptor + IDataAuthChecker + 业务校验 |
| E24 DTO partial 作为 ORM 内建 | 已有 xmeta/DTO/record-mapping；ORM 不必承担 | xmeta/DTO + record-mapping + selectFieldsByQuery |
| E25 编译查询句柄 | 编译计划缓存已覆盖；用户级重写 SPI 收益低 | Caffeine 计划缓存 + IEqlAstTransformer |

### 4.4 跨项目观察（只作趋势判断，不驱动 nop 照做）

1. 编译期安全与动态 schema 存在张力；nop 选择动态 schema + 模型驱动，代价是放弃 typed DSL，收益是模型可定制/全栈生成。
2. 关联加载在主流 ORM 中转向显式声明；nop 用批量装载 + FieldSelection 解决同一问题，避免 join 冗余。
3. 写路径的「字段级意图」在 EF/sea-orm/diesel 中有显式载体；nop 在会话内有脏列跟踪，脱离会话的场景按需用 DTO/业务层表达。
4. 迁移与 ORM 解耦是共识；nop 的 differ/migration/migration-pipeline 分层一致。
5. JSON 从「能存」走向「能查」；nop 保持「存储与查询分层」，路径查询按需放到 compute/元数据/专用引擎。
6. 主流 ORM 很少内置 L2 实体缓存/分片/数据权限/修订；这些是 nop 的差异化优势。

## 5. 落地建议

1. **P0：R1 事务隔离级别**。known gap，范围小、收益明确；先补 API 与连接应用，再考虑与读写分离「只读事务」联动。
2. **P0：R2 native SQL 安全/审计**。先定义边界（只覆盖 sql-lib 还是也覆盖 executeQuery），再做无 where 写阻断、租户/权限注入、审计；不引入 SQL 字符串二次解析。
3. **P1：R4/R12/R13 文档/示例**。写清领域事件与 outbox 的现有实现路径（IOrmInterceptor + dirty old/new + afterCommit）、upsert/returning 三路分工、库/模式级租户的 ShardSelector 配置。
4. **按需：R3 可观测、R5 查询缓存失效**。先有真实场景再立项；R3 无 roadmap 承载。
5. **roadmap/按需：R6 走 nop-db-migration-roadmap；R7 无专门 roadmap（新增方言按需）；R9 走 nop-persistence-middleware-roadmap。**
6. **流程**：涉及 ORM 模型语义/公共 API 的按 AGENTS.md Protected Area 走 plan-first；先 design 再 plan。

## 6. 设计依据

- docs/theory/lowcode-orm-2.md：§4.1（List 反模式）、§4.3（无继承、无 eager fetch、BatchLoadQueue）、§5.1（无数据库自增）、§5.2（审计/修改日志/组件）、§5.3（异步定位）、§5.4（dialect 差量化）、§5.5（模型→DDL/diff/迁移）、§6（EntityGraph 冗余 vs BatchLoadQueue）、§7（QueryBuilder 与 ORM 无关）
- docs/theory/lowcode-orm-1.md：动态 ORM、行代替列、文档型存储
- ai-dev/design/nop-orm-scope-and-non-goals.md：继承/二级表/m2m/savepoint/XA/PS 缓存/Criteria/脱敏；G1 事务隔离级别
- docs-for-ai/02-core-guides/orm-model-design.md：明确不支持的映射特性；compute/component/alias/kvTable 替代路径
- docs-for-ai/02-core-guides/model-first-development.md:571：实体写 → EQL → QueryBean → raw SQL 的访问顺序
- ai-dev/design/eql-cross-db-compat/2026-10-10-eql-upsert-and-returning-design.md：§3.8 returning 消费、§3.9 EQL upsert 与 DAO 批量原语分工、§4.4 拒绝合并

## Open Questions

- [ ] 事务隔离级别 API 放在 ITransactionTemplate 还是 nop-dao 的 TransactionOptions？是否需要与读写分离「只读事务」语义联动？
- [ ] native SQL 的统一注入边界：只覆盖 sql-lib 声明式 SQL，还是也覆盖 executeQuery(SQL)？「无 where 写阻断」默认开启还是显式开启？
- [ ] 领域事件是否需要在平台层提供统一事件总线（而不是每个 interceptor 自建），还是保持应用层组合？
- [ ] nop-orm-drivers 的 Mongo/ES/Redis 占位模块：删除还是按真实需求再建？

## References

### nop 内部（live code）

- nop-persistence/nop-orm/src/main/java/io/nop/orm/：IOrmTemplate、IOrmSession、IOrmEntity、IOrmSessionFactory、OrmEntityState，以及 session/persister/loader/dao/sql/mdx/enc/filter/id 子包
- nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4、compile/EqlCompiler.java、compile/EqlTransformVisitor.java
- nop-persistence/nop-dao/src/main/java/io/nop/dao/：jdbc/shard/dialect 子包
- nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/：init/loader 子包
- nop-persistence/nop-dbtool/nop-dbtool-core/.../：OrmDbDiffer、DataBaseUpgrader、JdbcMetaDiscovery
- nop-persistence/nop-db-migration/、nop-persistence/nop-orm-migration/
- nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/：orm.xdef、entity.xdef、dialect.xdef、sql-lib.xdef
- ai-dev/design/nop-orm-scope-and-non-goals.md、nop-orm-sharding-strategy-design.md、nop-orm-sharding-execution-engine-design.md、nop-dao-readwrite-splitting-design.md、nop-orm-enc-assisted-columns.md、nop-orm-data-migration-pipeline-design.md
- ai-dev/backlog/nop-persistence-middleware-roadmap.md、nop-db-migration-roadmap.md、eql-cross-db-compat-roadmap.md
- docs-for-ai/02-core-guides/：orm-model-design、tenant-model、logical-deletion、datasource-and-readwrite-splitting、orm-sharding、data-migration、eql-and-database-compatibility、concurrency-and-transactions
- deepwiki/nop-orm/（overview/architecture/flows/modules/topics）
- docs/theory/lowcode-orm-1.md、docs/theory/lowcode-orm-2.md

### 前作分析

- ai-dev/analysis/2026-10/2026-10-05-orm-query-optimizer-landscape-vs-nop-orm.md
- ai-dev/analysis/2026-10/2026-10-05-shardingsphere-vs-nop-orm-gap-analysis.md
- ai-dev/analysis/2026-10/2026-10-12-flyway-liquibase-vs-nop-db-migration-source-analysis.md
- ai-dev/analysis/2026-10/2026-10-02-eql-cross-database-compatibility-syntax-research.md

### 外部源码（~/sources）

- Java/JVM：orm/java/{jooq,hibernate-orm,eclipselink,openjpa,ebean,micronaut-data,mybatis-3,mybatis-plus,jdbi}、orm/jvm/{kotlin-exposed,vertx-sql-client}、jimmer
- TS/JS：orm/ts/{prisma,drizzle-orm,typeorm,mikro-orm,sequelize,kysely,knex,objection}
- Go/Rust/C#：orm/go/{gorm,ent,bun}、orm/rust/{diesel,sea-orm,sqlx}、orm/cs/{efcore,linq2db}
- Python/Ruby/Elixir/PHP：orm/python/{sqlalchemy,django,peewee,tortoise-orm,alembic}、orm/ruby/{activerecord,sequel}、orm/elixir/ecto、orm/php/{doctrine-orm,illuminate-database,phalcon}
- 查询引擎：orm/optimizer/{calcite,dolt}

### 子代理调研底稿（临时，供复核）

- _tmp/orm-research/g1-java-jpa.md、g2-java-sql.md、g3-ts-js.md、g4-go-rust-cs.md、g5-dynamic-langs.md
