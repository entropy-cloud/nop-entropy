# 阅读指南

> 本页依据（wiki 页面为同目录相对链接；仓库源码路径自本页上行两级至仓库根，写入前已逐条验证存在）：
>
> - [./PLAN.md](./PLAN.md)（模块地图 fan-in 数据、页面契约与覆盖缺口）
> - [./overview.md](./overview.md)
> - [./architecture.md](./architecture.md)
> - [./quickstart.md](./quickstart.md)
> - [./glossary.md](./glossary.md)
> - [./flows/entity-lifecycle.md](./flows/entity-lifecycle.md)
> - [./flows/query-pipeline.md](./flows/query-pipeline.md)
> - [./modules/session-factory.md](./modules/session-factory.md)
> - [./modules/persister-sql.md](./modules/persister-sql.md)
> - [./topics/assembly-interceptors.md](./topics/assembly-interceptors.md)
> - fan-in 前三契约文件：../../nop-persistence/nop-orm/src/main/java/io/nop/orm/IOrmEntity.java、../../nop-persistence/nop-orm/src/main/java/io/nop/orm/OrmErrors.java、../../nop-persistence/nop-orm/src/main/java/io/nop/orm/OrmConstants.java

本页是 nop-orm 文档集的导航页：按依赖重要度给出核心阅读主线，给三类读者（框架使用者、引擎研究者、问题排查者）各一条标注"读完能回答什么问题"的路径，并汇总全部子页的一句话摘要与模块边界外延。fan-in 数据取自 [./PLAN.md](./PLAN.md) 模块地图，文件级口径。

## 核心阅读主线：按依赖重要度

PLAN.md 模块地图的 fan-in 前三名是文件级口径的实测值：`IOrmEntity` 被 55 个其他 main 源文件引用（实体契约，引擎每一层都在读写它），`OrmErrors` 35 个（90 个错误码散布在 session/persister/sql_lib 各层），`OrmConstants` 30 个（PROP_ID、REV_TYPE_*、状态动作名等共享常量）。三者共同指向一个结论：引擎的公共词汇是"实体 + 状态 + 错误码"，先掌握这套词汇，后续任何一页都不会被术语卡住。

核心主线四步，覆盖引擎两大机制（写路径状态机、读路径查询管线）：

1. [./overview.md](./overview.md) —— 建立定位与边界：nop-orm 是什么、刻意不做什么（模型解析/EQL 编译/连接事务各归哪个模块）、平台位置与关键数字。
2. [./architecture.md](./architecture.md) —— 建立结构图式：四个支撑边界围住的五层内部分层，一次加载与一次保存的端到端时序。
3. [./flows/entity-lifecycle.md](./flows/entity-lifecycle.md) —— 写路径机制：七个状态的每个迁移由谁触发，flush 如何经 CascadeFlusher 与两阶段批量队列落到 JDBC。
4. [./flows/query-pipeline.md](./flows/query-pipeline.md) —— 读路径机制：一条查询如何经 SqlLib→编译缓存→JDBC→行映射→批量装载→session 缓存。

modules 章两页（[./modules/session-factory.md](./modules/session-factory.md)、[./modules/persister-sql.md](./modules/persister-sql.md)）是主线的结构化深化：主线读完再看它们，等于把两大机制按"谁拥有谁"重新拆开一遍；跳过主线直接读也可以，但状态与错误码词汇需回头补 [./glossary.md](./glossary.md)。

## 三类读者路径

| 读者 | 阅读顺序 | 读完能回答什么问题 |
|---|---|---|
| 框架使用者（只想在业务里用 ORM） | [./quickstart.md](./quickstart.md) → [./glossary.md](./glossary.md) → [./topics/assembly-interceptors.md](./topics/assembly-interceptors.md) | 如何装配 SessionFactory、注入 IOrmTemplate/IDaoProvider 跑通第一条 EQL 查询，实体状态与 PROXY 词汇指什么，以及如何用 IOrmInterceptor/IOrmDaoListener、schema 初始化配置和 OrmConfigs 开关把 ORM 接进自己的应用。 |
| 引擎研究者（读实现） | 主线全览（overview → architecture → flows/entity-lifecycle → flows/query-pipeline）→ [./modules/session-factory.md](./modules/session-factory.md) → [./modules/persister-sql.md](./modules/persister-sql.md) | 一次 save 如何经状态机、级联 flush、两阶段拓扑队列落到 JDBC，一条查询如何经 QueryPlanCacheKey 编译缓存与批量装载队列返回实体，SessionFactory 如何组装、供给模型并按租户分化一级缓存与编译计划。 |
| 问题排查者（跑不通/报错） | [./quickstart.md](./quickstart.md) → [./flows/query-pipeline.md](./flows/query-pipeline.md) → [./flows/entity-lifecycle.md](./flows/entity-lifecycle.md) → [./topics/assembly-interceptors.md](./topics/assembly-interceptors.md) | 查不到新数据、ERR_ORM_SESSION_CLOSED、ERR_ORM_UPDATE_ENTITY_NOT_FOUND 这类现象发生在管线哪一段、对应哪个状态迁移，该检查哪个配置项（事务挂接、批量大小、全局缓存开关）或扩展点（拦截器 VETO、daoListener）。 |

问题排查者路径的顺序有意与主线相反：先确认用法没错（quickstart），再沿运行时实际发生的调用链定位（先查询后落库），最后才查装配与扩展面——拦截器 VETO 与配置开关是"代码看起来对但行为不对"的高频原因。

## 全部页面一句话摘要

| 页面 | 一句话摘要 |
|---|---|
| [./overview.md](./overview.md) | 界定 nop-orm 是元模型驱动的 ORM 引擎：能力边界、"不提供什么"归属表、与 Hibernate/MyBatis 范式对比、关键数字（152 文件 / 7 状态 / 19 缺省 bean / 56 下游模块）。 |
| [./architecture.md](./architecture.md) | 以 pom 依赖与 beans 装配证明元模型/EQL/JDBC/IoC 四个边界，给出五层内部分层表、符号级组件图与一次加载、一次保存的端到端时序。 |
| [./quickstart.md](./quickstart.md) | 从引入依赖、orm-defaults.beans.xml 缺省装配（或六行程序化装配）、放置 app.orm.xml，到经 IOrmTemplate/IEntityDao 执行第一条 EQL 的五步最短路径，含事务挂接与关键配置项表。 |
| [./glossary.md](./glossary.md) | 划定引擎自造概念：七个实体状态与判定谓词、会话与一级缓存、PersistEnv 与批量动作队列、EQL/SqlItem/SqlLib 与批量装载、组件与拦截器，每条附代码出处。 |
| [./flows/entity-lifecycle.md](./flows/entity-lifecycle.md) | 追踪 save→flush→SQL 的确定路径：状态迁移触发点表、flush 四道闸与 CascadeFlusher 级联、循环终止三重机制、两阶段拓扑落库、逻辑删除/时间戳/修订三助手对照。 |
| [./flows/query-pipeline.md](./flows/query-pipeline.md) | 追踪一条查询的六阶段管线：SqlLib 分发与权限检查、QueryPlanCacheKey 编译缓存（Simple/Tenant 双计划）、GenSqlTransformer 重写、JDBC 惰性行映射、批量装载合并 1+N、装配进 session 缓存。 |
| [./modules/session-factory.md](./modules/session-factory.md) | session/factory 两包类型职责：IOrmSessionImplementor 三合一契约、三种一级缓存实现的分化与并发遍历协议、Default/TenantAware 双模型供给、实体增强回调契约与关键测试。 |
| [./modules/persister-sql.md](./modules/persister-sql.md) | 写路径三层分工：EntityPersisterImpl 编排（逻辑删除转 update、乐观锁与时间戳填充）、BatchActionQueueImpl 两阶段拓扑 flush 与按 id 去重、GenSqlHelper 预生成 SQL、JdbcBatcher 批语义与恰好一次的失败回调契约。 |
| [./topics/assembly-interceptors.md](./topics/assembly-interceptors.md) | 单文件装配全景（19 个 bean 清单与装配手法）、IOrmInterceptor/IOrmDaoListener/XplOrmInterceptor/@SingleSession 四类扩展点的收集与分发、数据权限过滤、schema 初始化链与事务挂接。 |
| [./PLAN.md](./PLAN.md) | 本文档集的规划文件：概念分析、模块地图（fan-in 数据出处）、页面契约（路径/标题/职责/源文件）与覆盖缺口记录。 |

## 补充阅读：模块边界外延

nop-orm 的非 test 依赖只有四个（nop-persistence/nop-orm/pom.xml:16-34），本文档集只写引擎侧；三个被消费模块的内部不在覆盖范围，一句定位如下：

- **nop-orm-model（元模型）**：IEntityModel/IColumnModel/IRelationModel 体系与 OrmModelLoader，由 orm.xdef 约束的 orm.xml 驱动；引擎经 IOrmModelProvider 只读消费，模型的解析、合并与 Delta 定制在该模块。
- **nop-orm-eql（查询语言）**：EqlCompiler 把 EQL 文本编译为 ICompiledSql；nop-orm 只持有编译产物并做计划缓存，文法与编译算法在 nop-orm-eql。
- **nop-dao（JDBC 层）**：IJdbcTemplate/JdbcBatcher 承接语句执行、批合并与事务回调，IDialect 决定方言；连接管理与事务实现在 nop-dao。

未覆盖部分同样记录在 [./PLAN.md](./PLAN.md) §5：mdx/kv/metrics/ddl/dataset/resource 等小包（1-3 文件）不设独立页面，只在相关页面按需提及；`_gen` 生成物不进入叙事。

## Sources

- [nop-persistence/nop-orm/pom.xml:16-34](https://gitee.com/canonical-entropy/nop-entropy/blob/f2ecee739b/nop-persistence/nop-orm/pom.xml#L16-L34)
- [nop-persistence/nop-orm/src/main/java/io/nop/orm/IOrmEntity.java](https://gitee.com/canonical-entropy/nop-entropy/blob/f2ecee739b/nop-persistence/nop-orm/src/main/java/io/nop/orm/IOrmEntity.java)
- [nop-persistence/nop-orm/src/main/java/io/nop/orm/OrmErrors.java:142-420](https://gitee.com/canonical-entropy/nop-entropy/blob/f2ecee739b/nop-persistence/nop-orm/src/main/java/io/nop/orm/OrmErrors.java#L142-L420)
- [nop-persistence/nop-orm/src/main/java/io/nop/orm/OrmConstants.java](https://gitee.com/canonical-entropy/nop-entropy/blob/f2ecee739b/nop-persistence/nop-orm/src/main/java/io/nop/orm/OrmConstants.java)
- [nop-persistence/nop-orm/src/main/java/io/nop/orm/OrmEntityState.java:10-57](https://gitee.com/canonical-entropy/nop-entropy/blob/f2ecee739b/nop-persistence/nop-orm/src/main/java/io/nop/orm/OrmEntityState.java#L10-L57)
- [nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/IEntityModel.java:25](https://gitee.com/canonical-entropy/nop-entropy/blob/f2ecee739b/nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/IEntityModel.java#L25)
- [nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlCompiler.java:40-46](https://gitee.com/canonical-entropy/nop-entropy/blob/f2ecee739b/nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlCompiler.java#L40-L46)
- [nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/IJdbcTemplate.java:24](https://gitee.com/canonical-entropy/nop-entropy/blob/f2ecee739b/nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/IJdbcTemplate.java#L24)

---

## On this page

- 核心阅读主线：按依赖重要度
- 三类读者路径
- 全部页面一句话摘要
- 补充阅读：模块边界外延
