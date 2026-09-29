# DeepWiki Plan — nop-orm

> Status: approved
> Target: /Users/abc/app/nop-entropy-wt/nop-entropy-master @ 0e67dba845
> Depth: standard
> Language: zh
> Coverage: 从 nop-persistence/nop-orm 152 个相关源文件中的约 70 个构建（_gen 生成物跳过；证据可延伸至 nop-orm-model/nop-orm-eql/nop-dao 相邻模块）

## 1. 概念分析

nop-orm 是 Nop 平台内置的 ORM 引擎（152 个源文件 / 约 2.3 万行，无 _gen 生成物），提供 Hibernate 式的会话语义：实体状态机（TRANSIENT/SAVING/PROXY/MANAGED/MISSING/DELETING/DELETED，`OrmEntityState`）、一级缓存（`OrmSessionEntityCache`）、级联 flush（`CascadeFlusher`）、批量加载队列（`OrmBatchLoadQueueImpl`）、批量动作队列（`BatchActionQueueImpl`）。

四个支撑边界（本模块不实现，只消费）：
- **元模型**：nop-orm-model 的 `IEntityModel`/`IColumnModel`/`IRelationModel` 体系（orm.xdef 驱动），经 `IOrmModelProvider`/`DefaultOrmModelProvider` 供给引擎；
- **查询语言**：nop-orm-eql 把 EQL 编译为 SQL，本模块 `sql_lib` 包持有 `SqlLibManager` 与 dao.xlib/sql.xlib 的 `SqlItemModel` 执行面；
- **JDBC 层**：nop-dao 的 `IJdbcTemplate`/`JdbcBatcher` 承接语句执行，本模块 `driver/jdbc` 适配方言；
- **IoC 装配**：`orm-defaults.beans.xml` 单文件装配全部缺省 bean（SessionFactory、Template、拦截器、初始化器）。

形态判定：framework-repo（引擎实现仓库）——叙事讲引擎机制（会话/状态机/persister/缓存/查询管线），不讲"如何用 ORM 做业务"。

## 2. 模块地图（证据来源，非章节轴）

| 模块（包） | 职责 | 关键入口 | fan-in | 供证页面 |
|---|---|---|---|---|
| io.nop.orm（根） | 核心契约：IOrmTemplate/IOrmSession/IOrmEntity/状态/错误 | IOrmTemplate(14)、IOrmEntity(55)、OrmEntityState(8) | 高 | overview、architecture |
| .factory | SessionFactory 组装与模型供给、租户模型缓存 | OrmSessionFactoryBean、SessionFactoryImpl(458 行)、DefaultOrmModelProvider | 中 | modules/session-factory |
| .session | 会话实现：一级缓存、级联 flush、批量动作 | OrmSessionImpl(1466 行)、OrmSessionEntityCache、CascadeFlusher | 中 | modules/session-factory、flows/entity-lifecycle |
| .persister | 实体/集合持久化、SQL 写路径、批量队列 | EntityPersisterImpl(761 行)、CollectionPersisterImpl、BatchActionQueueImpl | 中 | modules/persister-sql、flows/entity-lifecycle |
| .loader | 实体装载与批量加载队列、查询执行器 | OrmBatchLoadQueueImpl、JdbcQueryExecutor | 中 | flows/query-pipeline |
| .sql_lib | dao.xlib SQL 库执行面（EQL/原生 SQL 统一入口） | SqlLibManager(264 行)、SqlItemModel(10)、EqlSqlItemModel | 高 | flows/query-pipeline |
| .support | 实体基类/集合实现/组件辅助 | OrmEntity、OrmEntitySet(17)、OrmEntityHelper(10) | 中 | flows/entity-lifecycle |
| .interceptor/.filter/.initialize/.txn | AOP 拦截、数据权限过滤、schema 初始化、事务挂接 | SingleSessionMethodInterceptor、DataAuthEntityFilterProvider、DataBaseSchemaInitializer、OrmTransactionListener | 低 | topics/assembly-interceptors |
| .impl/.dao/.component/.id/.kv/.mdx/.metrics/.ddl/.driver/.utils/.compile/.dataset/.resource/.exceptions | 门面实现、DAO 支撑、组件、KV 表、方言适配等 | OrmTemplateImpl(555 行)、OrmDaoProvider | 低-中 | 按需进各页 |

## 3. 页面契约（路径锁定——"Page paths are final once submitted"）

| 路径 | 所属章 | 标题 | 职责（回答什么问题） | 源文件（≥5，含行号区段） | relatedPages | 计划图表 |
|------|--------|------|---------------------|--------------------------|--------------|----------|
| overview.md | 指南 | nop-orm 总览：元模型驱动的 ORM 引擎 | nop-orm 是什么、能力边界、平台位置、关键数字 | nop-persistence/nop-orm/pom.xml、src/main/resources/_vfs/nop/orm/beans/orm-defaults.beans.xml、IOrmTemplate.java、OrmEntityState.java、IOrmSessionFactory.java、OrmConstants.java | architecture | graph TD |
| quickstart.md | 指南 | 快速上手：装配 SessionFactory 到执行第一条查询 | 最短可运行路径：bean 装配→模型加载→session/模板操作→查询 | orm-defaults.beans.xml、factory/OrmSessionFactoryBean.java、IOrmTemplate.java、dao/OrmDaoProvider.java、factory/SessionFactoryConfig.java、src/test/java/io/nop/orm/AbstractOrmTestCase.java | overview | graph TD |
| glossary.md | 指南 | 术语表 | 引擎自造概念划界：实体状态/PROXY/PersistEnv/SqlItem/EQL/组件/批量队列 | OrmEntityState.java、OrmConstants.java、IOrmEntity.java、IOrmComponent.java、IOrmCompositePk.java、OrmErrors.java、persister/IPersistEnv.java | overview | （可免） |
| reading-guide.md | 指南 | 阅读指南 | 三类读者的阅读顺序与依赖重要度 | PLAN.md、IOrmTemplate.java、OrmSessionImpl.java、EntityPersisterImpl.java、SqlLibManager.java | overview | （可免） |
| architecture.md | 指南 | 架构与数据流 | 四个边界（元模型/EQL/JDBC/IoC）围住的引擎内部分层；一次加载与一次保存的端到端数据流；符号级图 | orm-defaults.beans.xml、factory/SessionFactoryImpl.java、session/OrmSessionImpl.java、impl/OrmTemplateImpl.java、persister/EntityPersisterImpl.java、loader/JdbcQueryExecutor.java、factory/DefaultOrmModelProvider.java | flows/*、modules/* | flowchart（符号级）+ sequenceDiagram |
| flows/entity-lifecycle.md | 机制 | 实体生命周期：从 TRANSIENT 到 DELETED 的状态机 | 实体每个状态迁移由谁触发、flush 时机、级联与批量落库、逻辑删除/时间戳/修订 | OrmEntityState.java、session/OrmSessionImpl.java、session/CascadeFlusher.java、persister/EntityPersisterImpl.java、support/OrmEntityHelper.java、persister/LogicalDeleteHelper.java、persister/OrmTimestampHelper.java | modules/persister-sql | stateDiagram-v2 + sequenceDiagram |
| flows/query-pipeline.md | 机制 | 查询管线：从 EQL/SQL 到实体装载与缓存 | 一条查询如何经 SqlLib→编译缓存→JDBC→行映射→批量装载→session 缓存 | sql_lib/SqlLibManager.java、sql_lib/SqlItemModel.java、sql_lib/EqlSqlItemModel.java、loader/JdbcQueryExecutor.java、loader/OrmBatchLoadQueueImpl.java、factory/SessionFactoryImpl.java、factory/SimpleCachedQueryPlan.java | modules/session-factory | flowchart + sequenceDiagram |
| modules/session-factory.md | 模块 | 会话与工厂：IOrmSession、SessionFactory 与模型供给 | session/factory 包的类型职责、一级缓存结构、租户模型缓存、实体增强 | session/IOrmSessionImplementor.java、session/OrmSessionImpl.java、session/OrmSessionEntityCache.java、factory/SessionFactoryImpl.java、factory/OrmSessionFactoryBean.java、factory/DefaultOrmModelProvider.java、factory/LoadedOrmModel.java | flows/query-pipeline | classDiagram + flowchart |
| modules/persister-sql.md | 模块 | 持久化与 SQL 生成：Persister、批量队列与驱动 | 写路径：persister 如何生成/执行 SQL、批量动作合并、方言适配 | persister/IEntityPersister.java、persister/EntityPersisterImpl.java、persister/CollectionPersisterImpl.java、persister/BatchActionQueueImpl.java、persister/IPersistEnv.java、persister/OrmAssembly.java、driver/jdbc 下类、../nop-dao/.../jdbc/JdbcBatcher.java | flows/entity-lifecycle | flowchart + classDiagram |
| topics/assembly-interceptors.md | 主题 | 装配与扩展：beans、拦截器、监听器与配置 | IoC 装配全景、扩展点（IOrmInterceptor/IOrmDaoListener）、数据权限过滤、schema 初始化、事务挂接、配置项 | orm-defaults.beans.xml、IOrmInterceptor.java、IOrmDaoListener.java、support/MultiOrmInterceptor.java、interceptor/SingleSessionMethodInterceptor.java、filter/DataAuthEntityFilterProvider.java、initialize/DataBaseSchemaInitializer.java、txn/OrmTransactionListener.java、OrmConfigs.java | overview | graph TD |

## 4. 生成顺序

叶子页（glossary、quickstart、topics/assembly-interceptors、modules/session-factory、modules/persister-sql）→ 机制章（flows/entity-lifecycle、flows/query-pipeline）→ architecture → quickstart 校订、overview、reading-guide。

## 5. 覆盖缺口与风险

- fan-in 为文件级口径（类名被多少其他文件提及），非符号级。
- `_gen` 包（sql_lib/_gen 8 文件）为生成物，跳过阅读，仅作为"模型由 xdef 生成"的证据。
- mdx/kv/metrics/ddl/dataset/resource 等小包（1-3 文件）不设独立页面，只在相关页面按需提及；未覆盖部分如实记录。
- 测试目录仅作 quickstart 证据来源，不进入机制叙事。
