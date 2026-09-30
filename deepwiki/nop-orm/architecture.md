# 架构与数据流

> 本页依据的源文件（相对路径自本页上行两级至仓库根，写入前已逐条核对文件行数与行段边界）：
>
> - ../../nop-persistence/nop-orm/pom.xml
> - ../../nop-persistence/nop-orm/src/main/resources/_vfs/nop/orm/beans/orm-defaults.beans.xml
> - ../../nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/OrmSessionFactoryBean.java
> - ../../nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java
> - ../../nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/DefaultOrmModelProvider.java
> - ../../nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java
> - ../../nop-persistence/nop-orm/src/main/java/io/nop/orm/impl/OrmTemplateImpl.java
> - ../../nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/IPersistEnv.java
> - ../../nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/EntityPersisterImpl.java
> - ../../nop-persistence/nop-orm/src/main/java/io/nop/orm/loader/JdbcQueryExecutor.java

本页是 nop-orm 的父级架构页：以 pom 依赖与 beans 装配证明四个外部边界，给出引擎内部分层职责表与符号级组件图，再沿一次加载、一次保存两条端到端路径串起全部接缝。机制细节由子页承接，本页只做结构总装与互链。

## 四个边界：引擎消费的四条支撑线

nop-orm 的非 test Maven 依赖共五个：四个边界模块 `nop-dao`、`nop-orm-eql`、`nop-orm-model`、`nop-core`（nop-persistence/nop-orm/pom.xml:16-34），外加一个纯 API 契约 `microprofile-reactive-streams-operators-api`（nop-persistence/nop-orm/pom.xml:95-98），后者不参与本页的四边界叙事。四条边界依赖不是偶然的打包结果，而是引擎刻意外移的四类能力：元模型由 nop-orm-model 供给，EQL 编译由 nop-orm-eql 完成，JDBC 执行由 nop-dao 承接，IoC 与反射由 nop-core 依赖族提供。引擎自身不做模型解析、不解析 EQL 文本、不持有 Connection、不做注解扫描——`SessionFactoryImpl` 的 import 区就是这张接缝清单的直接证据：`io.nop.dao.jdbc.IJdbcTemplate`、`io.nop.orm.model.IOrmModel`、`io.nop.orm.eql.ICompiledSql` 三组类型并列出现（nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java:19-45）。

| 边界 | 对方模块 | 本模块接缝类型 | 本模块接缝类/文件 |
|---|---|---|---|
| 元模型 | nop-orm-model | 模型装载调用 + `IEntityModel`/`IOrmModel` 只读消费 | `IOrmModelProvider`、`DefaultOrmModelProvider`、`LoadedOrmModel`、`PersistEnvBuilder` |
| EQL | nop-orm-eql | 调 `EqlCompiler.compile` 一次、长期持有编译产物 `ICompiledSql` | `SessionFactoryImpl.compileSql`、`LoadedOrmModel.compileSql`、`QueryPlanCacheKey` |
| JDBC | nop-dao | 语句执行、方言、事务、批执行全部委托 | `IPersistEnv.jdbc()/txn()`、`JdbcQueryExecutor`、`JdbcEntityPersistDriver`、`GenSqlTransformer` |
| IoC | nop-core（含 nop-api-core 的 `IBeanProvider` 契约） | 单文件 beans.xml 缺省装配 + `IBeanProvider` 按名取 bean | orm-defaults.beans.xml、`OrmSessionFactoryBean`、`SessionFactoryImpl.getBean` |

元模型边界只有一个进入方向：`DefaultOrmModelProvider.clearCache` 调 `new OrmModelLoader().loadOrmModel(false)` 取回 `OrmModel`，随后立刻包成引擎自己的 `LoadedOrmModel`（nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/DefaultOrmModelProvider.java:44-49）。这条边界还有第二条供给线：每次 `getOrmModel` 都按租户缓存装载 `XplOrmInterceptor` 并注入模型（DefaultOrmModelProvider.java:36-42）。`OrmModelLoader` 与 `IEntityModel` 都住在 nop-orm-model（nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/loader/OrmModelLoader.java:30-48、nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/IEntityModel.java:25），本模块只经 `getLoadedOrmModel()` 间接访问（SessionFactoryImpl.java:143-146）。EQL 边界同理是单点调用：`LoadedOrmModel.compileSql` 以五元组 `QueryPlanCacheKey` 查缓存，未命中才 `new EqlCompiler().compile(name, sqlText, ctx)`（nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/LoadedOrmModel.java:88-123；编译器入口在 nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlCompiler.java:40-46；五元组键定义在 nop-persistence/nop-orm/src/main/java/io/nop/orm/QueryPlanCacheKey.java:16-36）。JDBC 边界藏在 `IPersistEnv` 的两个方法后面：`jdbc()` 与 `txn()` 直接转发给注入的 `IJdbcTemplate`（nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/IPersistEnv.java:69-71；SessionFactoryImpl.java:425-433），方言按 querySpace 从 `IDialectProvider` 解析，缺省 provider 就是 jdbcTemplate 本身（SessionFactoryImpl.java:351-354，init 中的回落装配见 nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/OrmSessionFactoryBean.java:98-100）。IoC 边界是唯一双向的一条：容器把 bean 注入引擎——`nopOrmSessionFactory` 的两个集合属性用 `ioc:collect-beans` 按类型收集 `IOrmDaoListener` 与 `IOrmInterceptor` 实现并 `ioc:ignore-depends`（orm-defaults.beans.xml:44-50）——引擎也反向按名取 bean：替换持久化驱动时用 `getBean("entityPersister_" + driverName, true)` 且强制 prototype scope，非 prototype 直接抛 `ERR_ORM_BEAN_NOT_PROTOTYPE_SCOPE`（SessionFactoryImpl.java:379-384、445-451）。

IoC 边界的装配核心是单个 bean 定义，工厂本体经 `ioc:bean-method="getObject"` 暴露（`getObject` 返回内部持有的 `SessionFactoryImpl`，OrmSessionFactoryBean.java:193-195）：

`nop-persistence/nop-orm/src/main/resources/_vfs/nop/orm/beans/orm-defaults.beans.xml:38-51`

```xml
    <bean id="nopOrmSessionFactory" class="io.nop.orm.factory.OrmSessionFactoryBean"
          ioc:bean-method="getObject" ioc:default="true">
        <property name="name" value="app-orm"/>
        <property name="registerGlobalCache" value="true"/>
        <property name="sequenceGenerator" ref="nopSequenceGenerator" ioc:ignore-depends="true"/>

        <property name="daoListeners">
            <ioc:collect-beans by-type="io.nop.orm.IOrmDaoListener" ioc:ignore-depends="true"/>
        </property>

        <property name="interceptors">
            <ioc:collect-beans by-type="io.nop.orm.IOrmInterceptor" ioc:ignore-depends="true"/>
        </property>
    </bean>
```

> Sources: [../../nop-persistence/nop-orm/pom.xml:16-34](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/pom.xml#L16-L34)、[../../nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java:19-45](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java#L19-L45)、[../../nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/IPersistEnv.java:69-71](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/IPersistEnv.java#L69-L71)、[../../nop-persistence/nop-orm/src/main/resources/_vfs/nop/orm/beans/orm-defaults.beans.xml:38-51](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/resources/_vfs/nop/orm/beans/orm-defaults.beans.xml#L38-L51)、[../../nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/DefaultOrmModelProvider.java:44-49](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/DefaultOrmModelProvider.java#L44-L49)

## 引擎内部分层与符号级组件图

四个边界围出来的内部空间分五层。分层判据是"谁拥有谁"：入口层绑定会话，会话层拥有缓存与 flusher，编排层拥有驱动与全局缓存，驱动层拥有预生成 SQL，模型供给层在工厂构建期一次性生成前三层的所有单实例对象。

| 层 | 代表类型 | 拥有的状态 | 对下层的调用 |
|---|---|---|---|
| 入口层 | `OrmTemplateImpl`、`SqlLibManager`、`SqlItemModel` | 线程绑定的 session 注册表（`OrmSessionRegistry`） | `runInSession` 打开/复用 `OrmSessionImpl`（OrmTemplateImpl.java:204-231） |
| 会话层 | `OrmSessionImpl`、`OrmSessionEntityCache` 三实现、`CascadeFlusher`、`SessionBatchActionQueue`、`OrmBatchLoadQueueImpl` | 一级缓存、dirty 位、批量动作队列、批量装载队列 | flush 时消费脏实体并调 persister（OrmSessionImpl.java:176-224） |
| 持久化编排层 | `EntityPersisterImpl`、`CollectionPersisterImpl`、`BatchActionQueueImpl` | 每实体一个 persister、全局缓存开关、shard 选择 | 逻辑删除/时间戳/修订分流后生成动作入队，flush 时派发驱动（EntityPersisterImpl.java:288-337） |
| 驱动/SQL 层 | `JdbcEntityPersistDriver`、`GenSqlHelper`、`JdbcQueryExecutor`、`GenSqlTransformer` | 预生成 `EntitySQL`（insert/delete/load/lock/batchLoadSqlPart） | 经 `IPersistEnv.jdbc()` 交给 nop-dao 的 `IJdbcTemplate`/`JdbcBatcher`（JdbcEntityPersistDriver.java:80-97） |
| 模型供给层 | `DefaultOrmModelProvider`、`LoadedOrmModel`、`PersistEnvBuilder`、`SqlExprMetaCache` | persister 表、collectionPersister 表、queryPlanCache | 构建期调 `OrmModelLoader`（元模型边界）与 `EqlCompiler`（EQL 边界）（LoadedOrmModel.java:38-53） |

```mermaid
graph TD
    subgraph IOC["IoC 边界（nop-core / beans.xml）"]
        BEANS["orm-defaults.beans.xml<br/>nopOrmSessionFactory + collect-beans"]
    end
    subgraph META["元模型边界（nop-orm-model）"]
        LOADER["OrmModelLoader.loadOrmModel"]
    end
    subgraph EQL["EQL 边界（nop-orm-eql）"]
        CMP["EqlCompiler.compile"]
    end
    subgraph JDBC["JDBC 边界（nop-dao）"]
        JT["IJdbcTemplate"]
        JB["JdbcBatcher"]
    end
    BEANS --> SFB["OrmSessionFactoryBean.init()"]
    SFB -->|"缺省 new"| DMP["DefaultOrmModelProvider"]
    LOADER --> DMP
    DMP --> LOM["LoadedOrmModel<br/>entityPersisters / queryPlanCache"]
    CMP --> LOM
    LOM --> PEB["PersistEnvBuilder.buildEntityPersisters"]
    PEB --> SFI["SessionFactoryImpl<br/>implements IPersistEnv"]
    SFI --> TPL["OrmTemplateImpl.runInSession"]
    SFI --> SQLM["SqlLibManager / SqlItemModel"]
    TPL --> SESS["OrmSessionImpl<br/>一级缓存三实现 + flush 四道闸"]
    SQLM --> SESS
    SESS --> FLU["CascadeFlusher.execute"]
    FLU --> PER["EntityPersisterImpl<br/>逻辑删除/时间戳/修订分流"]
    PER --> QUE["BatchActionQueueImpl<br/>两阶段拓扑 flush"]
    QUE --> DRVT["JdbcEntityPersistDriver<br/>预生成 EntitySQL + GenSqlHelper"]
    SESS --> BQI["OrmBatchLoadQueueImpl<br/>三池合并 1+N"]
    BQI --> PER
    SESS --> JQE["JdbcQueryExecutor<br/>compileSql + GenSqlTransformer + transformRow"]
    JQE --> JT
    DRVT --> JB
```

`SessionFactoryImpl` 是全部接缝的物理汇聚点：一个类实现 `IPersistEnv`——该接口单独继承 `IOrmSessionFactory`（nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/IPersistEnv.java:31），会话与 persister 拿到的 env 契约就是工厂本身——并同时暴露四组能力。会话创建直接 `new OrmSessionImpl(stateless, this, getInterceptors())`：

`nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java:322-330`

```java
    @Override
    public IOrmSession openSession(boolean stateless) {
        return new OrmSessionImpl(stateless, this, getInterceptors());
    }

    @Override
    public IOrmModel getOrmModel() {
        return getLoadedOrmModel().getOrmModel();
    }
```

查询执行器路由与驱动创建同在此类，二者是 querySpace 维度与持久化驱动维度的两个分发点：

`nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java:370-384`

```java
    @Override
    public IQueryExecutor getQueryExecutor(String querySpace) {
        querySpace = DaoHelper.normalizeQuerySpace(querySpace);
        IQueryExecutor executor = getQueryExecutors().get(querySpace);
        if (executor == null)
            executor = getDefaultQueryExecutor();
        return executor;
    }

    @Override
    public IEntityPersistDriver createEntityPersistDriver(String driverName) {
        if (driverName == null || driverName.equals("jdbc"))
            return new JdbcEntityPersistDriver();
        return (IEntityPersistDriver) getBean("entityPersister_" + driverName, true);
    }
```

编译委托是第三个接缝：三个 `compileSql` 重载全部转发 `LoadedOrmModel`（SessionFactoryImpl.java:332-349），实体类加载则区分动态实体与普通实体两条路径（SessionFactoryImpl.java:411-423）。会话构造函数完成"引擎与供给层"的对接：`env.getLoadedOrmModel()` 引用计数加一（`incRef`），再按 stateless 标志与模型是否含租户实体在 `StatelessOrmSessionEntityCache`/`TenantOrmSessionEntityCache`/`OrmSessionEntityCache` 三种一级缓存实现里三选一（OrmSessionImpl.java:125-135）。供给层一侧，`LoadedOrmModel` 构造期用 `PersistEnvBuilder` 一次性生成 entityPersisters、collectionPersisters 与 `SqlExprMetaCache` 三张表（nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/LoadedOrmModel.java:38-52），此后五层共享这批单实例对象。

> Sources: [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java:322-349](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java#L322-L349)、[../../nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java:370-392](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java#L370-L392)、[../../nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/IPersistEnv.java:31](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/IPersistEnv.java#L31)、[../../nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:125-135](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java#L125-L135)、[../../nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/LoadedOrmModel.java:38-53](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/LoadedOrmModel.java#L38-L53)

## 一次加载：按 id 取实体的端到端时序

按 id 装载是引擎内最长的一条只读路径，它串起入口层、会话层、编排层与 JDBC 边界，且中途有两次"延迟"设计：先返回 PROXY 代理，再靠批量装载队列把 1+N 合并成 IN 查询。

```mermaid
sequenceDiagram
    participant App as 调用方
    participant Tpl as OrmTemplateImpl
    participant Sess as OrmSessionImpl
    participant Cache as OrmSessionEntityCache
    participant Queue as OrmBatchLoadQueueImpl
    participant Pers as EntityPersisterImpl
    participant Drv as JdbcEntityPersistDriver
    participant Jdbc as IJdbcTemplate(nop-dao)

    App->>Tpl: get(entityName, id)
    Tpl->>Sess: runInSession → runInNewSession（注册表绑定+收尾 flush）
    Sess->>Cache: makeProxy 查一级缓存
    alt 缓存命中
        Cache-->>App: 直接返回既有实体
    else 未命中
        Sess->>Cache: 新建 PROXY 实体入缓存
        Note over Sess: 首次属性访问 internalLoadProperty
        Sess->>Queue: 入批量装载队列（eager 池/延迟池/集合池）
        Sess->>Queue: flush()（同步等待）
        Queue->>Pers: batchLoadAsync(entities, propIds)
        Pers->>Drv: batchExecuteAsync（按 shard 分组、maxBatchLoadSize 分块）
        Drv->>Jdbc: IN 查询（缓存的 batchLoadSqlPart）
        Jdbc-->>Drv: IDataSet 逐行
        Drv->>Sess: readId → internalAssemble 填值置 MANAGED
        Drv->>Sess: 无行命中 → markMissing 置 MISSING
    end
    Sess-->>App: 实体（回调收尾 session.flush）
```

`runInSession` 先查 `OrmSessionRegistry` 里线程绑定的 session，stateless 标志一致则复用，否则 `runInNewSession` 打开新 session 并在 finally 中恢复旧 session、关闭新 session（OrmTemplateImpl.java:204-231）。代理创建与缓存登记集中在 `makeProxy`：主键经 `OrmEntityHelper.castId` 归一后查缓存，未命中才建 PROXY 入缓存，to-many 关联属性同步初始化为 proxy 集合（OrmSessionImpl.java:781-822）；无主键实体是例外——直接建 PROXY 返回、不入缓存（OrmSessionImpl.java:792-796）。懒属性触发在 `internalLoadProperty`：实体未初始化时取 `eagerLoadProps` 全集并附加请求的 propId，随后 `loadAsync` + `syncGet` 同步等待装载完成（OrmSessionImpl.java:840-873）。批量合并队列惰性创建于 `getBatchLoadQueue`（OrmSessionImpl.java:269-274），批量执行委托 `persister.batchLoadAsync`（OrmSessionImpl.java:880-887）。EQL 查询走另一条入口：`OrmSessionImpl.executeQuery` 按 querySpace 选 `IQueryExecutor` 后进入 `JdbcQueryExecutor`，在那里完成编译缓存查询（executeQuery 内先 `compileSql`，JdbcQueryExecutor.java:75-77）、daoListener 回调（按读写实体名分发 onRead/onSave/onUpdate/onDelete，JdbcQueryExecutor.java:118-145）、`GenSqlTransformer` 重写与 `TransformedDataSet` 惰性行映射（JdbcQueryExecutor.java:80-93、147-171）。这条管线的六阶段拆解见 [flows/query-pipeline.md](flows/query-pipeline.md)；PROXY→MANAGED 的状态语义见 [flows/entity-lifecycle.md](flows/entity-lifecycle.md)。

> Sources: [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/impl/OrmTemplateImpl.java:204-231](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/impl/OrmTemplateImpl.java#L204-L231)、[../../nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:781-887](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java#L781-L887)、[../../nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:1425-1430](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java#L1425-L1430)、[../../nop-persistence/nop-orm/src/main/java/io/nop/orm/loader/JdbcQueryExecutor.java:75-93](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/loader/JdbcQueryExecutor.java#L75-L93)、[../../nop-persistence/nop-orm/src/main/java/io/nop/orm/loader/JdbcQueryExecutor.java:147-171](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/loader/JdbcQueryExecutor.java#L147-L171)

## 一次保存：从 internalSave 到 JDBC 批执行

保存路径与装载共享入口层，分叉点在会话层。`save()` 调 `internalSave`：SAVING 状态幂等早退，非 TRANSIENT 抛 `ERR_ORM_SAVE_ENTITY_NOT_TRANSIENT`，随后置 SAVING、`initEntityId` 生成主键、`cache.add` 入一级缓存（OrmSessionImpl.java:906-948）；缓存中已有同 id 实体时做替换校验（非 GONE 状态抛 `ERR_ORM_SAVE_ENTITY_REPLACE_EXISTING_ENTITY`），"删除后又新建"则直接置 MANAGED 并继承旧值（OrmSessionImpl.java:927-940）；flush 进行中登记的新变更会追加到 `flusher.addChangeDuringFlush`（OrmSessionImpl.java:945-947）。stateless 会话在 `save()` 里立即 `flushImmediately`，有状态会话把落库推迟到 `flush()`（OrmSessionImpl.java:481-491）。`flush` 过四道闸——重入、非 dirty、只读，以及前置的 context/valid 校验——然后 `interceptPreFlush`、执行 `CascadeFlusher`、清 dirty、`batchActionQueue.flush()`、`interceptPostFlush`（OrmSessionImpl.java:176-224）；`flushImmediately` 复用同一 flusher，且收尾时以 `oldDirty || dirty` 保护 flush 过程中新增的脏标记不被回滚（OrmSessionImpl.java:1001-1017）。`CascadeFlusher` 对每个脏实体按状态分流到 `flushSave/flushUpdate/flushDelete`，三者先过拦截器 VETO 检查再调 persister（OrmSessionImpl.java:1019-1059）。

persister 层把状态翻译成动作：`EntityPersisterImpl.save/update/delete` 完成 `LogicalDeleteHelper`、`OrmTimestampHelper`、`OrmRevisionHelper` 三条特殊写路径分流与必填校验，然后 `enqueueSave/Update/Delete` 进 session 的 `SessionBatchActionQueue`（EntityPersisterImpl.java:288-337）。真正发 SQL 在 `BatchActionQueueImpl.flushAsync`：对实体模型拓扑排序后两阶段执行，正序跑 INSERT/UPDATE 和不被依赖实体的 DELETE，逆序补被依赖实体的 DELETE，任一阶段失败即中断并补发 onFailure 回调（BatchActionQueueImpl.java:145-257）。驱动层 `JdbcEntityPersistDriver` 按 `orm_dirtyPropIds` 现生成 UPDATE、复用预生成 INSERT，经 nop-dao 的 `JdbcBatcher` 按同文本合批执行（JdbcEntityPersistDriver.java:233-319、nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/JdbcBatcher.java:136-146）。关键点：SAVING→MANAGED、DELETING→DELETED 的状态迁移不发生在生成动作时，而在 SQL 成功后的回调 `persisterPostSave/PostUpdate/PostDelete` 里（OrmSessionImpl.java:1159-1175）。全链路的级联终止条件、失败恢复与三条特殊写路径对照见 [flows/entity-lifecycle.md](flows/entity-lifecycle.md) 与 [modules/persister-sql.md](modules/persister-sql.md)。

> Sources: [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:481-491](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java#L481-L491)、[../../nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:906-948](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java#L906-L948)、[../../nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:1001-1059](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java#L1001-L1059)、[../../nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/EntityPersisterImpl.java:288-337](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/EntityPersisterImpl.java#L288-L337)、[../../nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/BatchActionQueueImpl.java:145-257](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/BatchActionQueueImpl.java#L145-L257)

## 装配视图与阅读路径

运行时图里每个节点的出生地集中在两个文件。orm-defaults.beans.xml 一侧：`nopSqlLibManager` 以 `lazy-init` + `ioc:delay-method` 延迟启动（orm-defaults.beans.xml:13-15），`nopOrmSessionFactory` 用 `ioc:bean-method="getObject"` 暴露工厂、`ioc:collect-beans` 收集两个扩展点（38-51），`nopOrmTemplate`/`nopDaoProvider` 紧随其后（53-58），`SingleSessionMethodInterceptor` 以 `ioc:pointcut` 挂注解拦截（67-72），三个 `if-property` 条件初始化器（`nop.orm.init-database-schema`/`nop.orm.auto-add-tenant-col`/`nop.orm.init-database-data`）以 `ioc:after` 串在工厂之后（78-97）。`OrmSessionFactoryBean.init()` 一侧先做四个 `Guard.notNull` 前置校验再逐项组装（OrmSessionFactoryBean.java:68-93），缺省件补齐集中在同一段代码：

`nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/OrmSessionFactoryBean.java:95-113`

```java
        impl.setQueryPlanCache(LocalCache.newCache(buildFullName("orm-query-plan-cache"),
                newConfig(CFG_QUERY_PLAN_CACHE_SIZE.get()).useMetrics()));

        if (this.getDialectProvider() == null) {
            impl.setDialectProvider(getJdbcTemplate());
        }

        if (this.getGlobalCache() == null) {
            CacheConfig config = new CacheConfig();
            config.setMaximumSize(CFG_ENTITY_GLOBAL_CACHE_SIZE.get());
            config.setExpireAfterWrite(CFG_ENTITY_GLOBAL_CACHE_TIMEOUT.get());
            impl.setGlobalCache(new LocalCacheProvider(buildFullName("orm-global-cache"), config));
        }

        if (this.getEntityClassLoader() == null)
            this.setEntityClassLoader(clazz -> ClassHelper.getDefaultClassLoader().loadClass(clazz));

        if (this.getDefaultQueryExecutor() == null)
            impl.setDefaultQueryExecutor(new JdbcQueryExecutor(impl));
```

查询计划缓存容量、实体全局缓存容量与过期时间都接 `OrmConfigs` 配置项（OrmSessionFactoryBean.java:95-107），metrics 按 `useMetrics` 开关在 `OrmMetricsImpl` 与 `EmptyOrmMetricsImpl` 间二选一（115-119）。模型供给的缺省装配在私有方法 `setOrmModelHolder`：容器未注入 `IOrmModelProvider` 时 `new DefaultOrmModelProvider(sessionFactory)`（OrmSessionFactoryBean.java:170-177），该 provider 构造即调 `clearCache` 完成首次模型装载并注册租户拦截器缓存（DefaultOrmModelProvider.java:22-27）。这份装配的逐 bean 清单、两个扩展点的分发机制、schema 初始化与事务挂接见 [topics/assembly-interceptors.md](topics/assembly-interceptors.md)；可运行的最短上手路径见 [quickstart.md](quickstart.md)；会话与一级缓存的内部结构见 [modules/session-factory.md](modules/session-factory.md)；术语对照见 [glossary.md](glossary.md)；模块定位与平台坐标见 [overview.md](overview.md)；推荐阅读顺序见 [reading-guide.md](reading-guide.md)。

> Sources: [../../nop-persistence/nop-orm/src/main/resources/_vfs/nop/orm/beans/orm-defaults.beans.xml:38-97](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/resources/_vfs/nop/orm/beans/orm-defaults.beans.xml#L38-L97)、[../../nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/OrmSessionFactoryBean.java:72-113](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/OrmSessionFactoryBean.java#L72-L113)、[../../nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/OrmSessionFactoryBean.java:170-177](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/OrmSessionFactoryBean.java#L170-L177)、[../../nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/DefaultOrmModelProvider.java:22-27](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/DefaultOrmModelProvider.java#L22-L27)、[../../nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java:322-325](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java#L322-L325)

## Sources

- [../../nop-persistence/nop-orm/pom.xml:16-34](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/pom.xml#L16-L34)
- [../../nop-persistence/nop-orm/pom.xml:95-98](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/pom.xml#L95-L98)
- [../../nop-persistence/nop-orm/src/main/resources/_vfs/nop/orm/beans/orm-defaults.beans.xml:13-15](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/resources/_vfs/nop/orm/beans/orm-defaults.beans.xml#L13-L15)
- [../../nop-persistence/nop-orm/src/main/resources/_vfs/nop/orm/beans/orm-defaults.beans.xml:38-51](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/resources/_vfs/nop/orm/beans/orm-defaults.beans.xml#L38-L51)
- [../../nop-persistence/nop-orm/src/main/resources/_vfs/nop/orm/beans/orm-defaults.beans.xml:38-97](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/resources/_vfs/nop/orm/beans/orm-defaults.beans.xml#L38-L97)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java:19-45](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java#L19-L45)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java:143-146](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java#L143-L146)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java:322-330](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java#L322-L330)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java:322-349](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java#L322-L349)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java:351-354](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java#L351-L354)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java:370-384](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java#L370-L384)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java:370-392](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java#L370-L392)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java:411-423](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java#L411-L423)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java:425-433](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java#L425-L433)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java:445-451](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryImpl.java#L445-L451)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/OrmSessionFactoryBean.java:72-113](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/OrmSessionFactoryBean.java#L72-L113)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/OrmSessionFactoryBean.java:95-113](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/OrmSessionFactoryBean.java#L95-L113)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/OrmSessionFactoryBean.java:170-177](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/OrmSessionFactoryBean.java#L170-L177)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/OrmSessionFactoryBean.java:193-195](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/OrmSessionFactoryBean.java#L193-L195)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/DefaultOrmModelProvider.java:22-27](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/DefaultOrmModelProvider.java#L22-L27)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/DefaultOrmModelProvider.java:36-42](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/DefaultOrmModelProvider.java#L36-L42)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/DefaultOrmModelProvider.java:44-49](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/DefaultOrmModelProvider.java#L44-L49)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/LoadedOrmModel.java:38-52](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/LoadedOrmModel.java#L38-L52)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/LoadedOrmModel.java:38-53](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/LoadedOrmModel.java#L38-L53)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/LoadedOrmModel.java:88-123](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/LoadedOrmModel.java#L88-L123)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/QueryPlanCacheKey.java:16-36](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/QueryPlanCacheKey.java#L16-L36)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:125-135](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java#L125-L135)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:176-224](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java#L176-L224)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:269-274](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java#L269-L274)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:481-491](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java#L481-L491)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:781-822](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java#L781-L822)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:792-796](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java#L792-L796)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:840-873](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java#L840-L873)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:880-887](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java#L880-L887)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:906-948](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java#L906-L948)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:927-940](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java#L927-L940)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:945-947](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java#L945-L947)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:1001-1017](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java#L1001-L1017)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:1001-1059](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java#L1001-L1059)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:1159-1175](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java#L1159-L1175)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:1425-1430](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java#L1425-L1430)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/impl/OrmTemplateImpl.java:204-231](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/impl/OrmTemplateImpl.java#L204-L231)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/IPersistEnv.java:31](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/IPersistEnv.java#L31)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/IPersistEnv.java:69-71](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/IPersistEnv.java#L69-L71)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/EntityPersisterImpl.java:288-337](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/EntityPersisterImpl.java#L288-L337)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/BatchActionQueueImpl.java:145-257](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/BatchActionQueueImpl.java#L145-L257)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/driver/jdbc/JdbcEntityPersistDriver.java:80-97](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/driver/jdbc/JdbcEntityPersistDriver.java#L80-L97)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/driver/jdbc/JdbcEntityPersistDriver.java:233-319](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/driver/jdbc/JdbcEntityPersistDriver.java#L233-L319)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/loader/JdbcQueryExecutor.java:75-93](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/loader/JdbcQueryExecutor.java#L75-L93)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/loader/JdbcQueryExecutor.java:118-145](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/loader/JdbcQueryExecutor.java#L118-L145)
- [../../nop-persistence/nop-orm/src/main/java/io/nop/orm/loader/JdbcQueryExecutor.java:147-171](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm/src/main/java/io/nop/orm/loader/JdbcQueryExecutor.java#L147-L171)
- [../../nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/loader/OrmModelLoader.java:30-48](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/loader/OrmModelLoader.java#L30-L48)
- [../../nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/IEntityModel.java:25](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/IEntityModel.java#L25)
- [../../nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlCompiler.java:40-46](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlCompiler.java#L40-L46)
- [../../nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/IJdbcTemplate.java:24](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/IJdbcTemplate.java#L24)
- [../../nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/JdbcBatcher.java:136-146](https://gitee.com/canonical-entropy/nop-entropy/blob/555f7a9731/nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/JdbcBatcher.java#L136-L146)

---

## On this page

- 四个边界：引擎消费的四条支撑线
- 引擎内部分层与符号级组件图
- 一次加载：按 id 取实体的端到端时序
- 一次保存：从 internalSave 到 JDBC 批执行
- 装配视图与阅读路径
