# nop-orm 分片执行引擎（fan-out 与归并）设计

**日期**：2026-10-05（同日按首轮评审修订：2 Blocker + 5 Major + 3 Minor 全部处理，见各节【评审 B1】等标注）
**范围**：`nop-persistence/nop-orm`（查询执行漏斗、结果归并）、`nop-persistence/nop-dao`（shard SPI 扩展与配置契约）、`nop-persistence/nop-orm-eql`（**允许最小增量**：`CompiledSql` 只读结构信息字段，裁定见 §3.1）
**状态**：草案（roadmap `ai-dev/backlog/nop-persistence-middleware-roadmap.md` WI7；Deliverable = 本设计过评审，实施归 WI8。首轮评审 REJECT（2 Blocker + 5 Major + 3 Minor）已按必改清单修订，待复审）
**灵感来源**：Apache ShardingSphere sharding 执行引擎面对比；对 `nop-orm-sharding-strategy-design.md` §四"跨分片 fan-out 拒绝"的重新立项

---

## 一、设计结论

1. **拆分点 = 多次单分片 transform，per-shard 强制 selector 骑既有 default-shard 分支**。接管点在执行层：`JdbcQueryExecutor` 三个入口（executeQuerySql/executeUpdateSql/executeStatementSql）中 transform+fetch+merge 整体接管，判定发生在 transform 之前（泄漏进 transform 的 FANOUT 策略会静默回落基表执行——plan 2320 消灭的陷阱，见 §3.2）；每分片经强制 selector（missing policy 转写为 DEFAULT_SHARD、`selectShardWhenMissing` 返回强制落点）驱动既有 transform 链产出 N 份物理 SQL，`GenSqlTransformer` 路由链零改动，仅新增 `policy==FANOUT` 兜底显式失败分支。v1 执行形态为**串行**。
2. **fanout 资格收口**：仅 SELECT；**单条 SQL 至多一个 fanout 实体，且不得与带分片值的 useShard 实体混用**（fanout 实体数 >1 或混用一律 `ERR_ORM_SHARD_FANOUT_NOT_SUPPORT`——跨分片配对静默缺行风险，§3.2）；跨分片读显式声明无统一快照语义；跨分片写不进 v1 引擎（非 SELECT 命中 fanout 策略归 `ERR_ORM_SHARD_FANOUT_NOT_SUPPORT`），未来开启时事务面复用既有 best-effort 事务组（缺省组自动入组；多主组部署需 `txn-group-map` 收敛到同一主组），不新建组提交机制——与 `nop-orm-scope-and-non-goals.md` §一.6 XA non-goal 一致。
3. **归并语义分级（依赖 §3.1 编译期结构信息）**：支持无序拼接 / ORDER BY 内存排序（排序键经 orderBy→投影序数映射从归并行取值，**排序键未投影的查询 v1 显式失败**）/ 裸 count(*) 求和 / 浅分页（每分片下推 offset+limit 后归并截断），全部受 `nop.orm.shard.fanout-max-rows` **逐分片增量计数**兜底（达限立即中断后续分片、无部分结果、异常传播）；GROUP BY / 非裸 count 聚合 / DISTINCT / HAVING / FOR UPDATE / UNION 由编译期构造标志判定并显式失败。
4. **跨分片 JOIN 边界**：绑定表（同策略同分片值）单分片 JOIN 保持原生；其余显式失败（含"fanout 实体数 >1"这类隐式跨片 JOIN 意图，§3.2 资格收口统一覆盖）。
5. **广播表维持拒绝**：v1 跨分片 JOIN 不支持使广播表失去唯一收益场景；跨分片非原子写造成副本间不一致且静默渗入所有读结果。CDC 搬迁管道（WI9）成熟后以"一主多从订阅"形态重审，仍不进 OLTP 写路径。
6. **组合面全部复用既有契约**：读写分离在分片 querySpace 的 DataSource 内正交叠加（引擎对每份物理 SQL **逐份显式设置**只读意图标记）；fan-out 结果组装进 ORM 一级缓存无身份冲突（分布式 ID 保证 `(entityName, id)` 跨分片唯一）；搬迁/回填走 nop-batch 逐分片作业，不经执行引擎。
7. **使用契约**：`MissingShardPolicy` **第四取值** `FANOUT`（既有 NONE/FAIL/DEFAULT_SHARD 之外）+ 全局开关 `nop.orm.shard.fanout-enabled`（缺省 false；关闭时 fanout 策略运行期退化为 fail，不静默降级）+ 行数上限 `nop.orm.shard.fanout-max-rows`；错误码新增 2 个（OrmErrors）、复用 2 个；可观测性 = 既有每分片 SQL 日志 + 引擎级 fanout 汇总日志与指标。

## 二、背景与动机

### 2.1 重新立项的条件已成熟

`nop-orm-sharding-strategy-design.md` §四以"执行引擎级能力，须连同连接管理、事务边界一起设计，独立立项"为由拒绝了 fan-out。该独立立项的前置——策略注册表、`ShardSelection` 二元组、绑定校验、TABLE marker 静态信息注入、DDL N 表展开——已按 plan `ai-dev/plans/2320-orm-sharding-strategy.md` 落地（设计文档 §六实现裁定记录）。roadmap 将本设计列为 WI7（M3 分片执行引擎），并要求拒绝项含"广播表是否维持不做"的再裁定。

落地后的行为缺口明确：useShard 实体缺分片键的查询按 `missing-shard-policy` fail-fast 或回落 default 分片，**没有"按分片全集扫描"的合法路径**——而"按租户分片、管理端全量报表"是分片体系的常规诉求。本设计为该缺口给出可判定信息面、执行模型、事务边界与归并语义的完整裁定。

### 2.2 执行漏斗现状（均已核实 live code）

- **单一改写漏斗**：所有 ORM 查询/更新经 `JdbcQueryExecutor.transformEQL` 产出唯一一条物理 SQL（`nop-persistence/nop-orm/src/main/java/io/nop/orm/loader/JdbcQueryExecutor.java:153-158`），每次执行新建 `GenSqlTransformer` 实例；三个执行入口 `executeUpdateSql`/`executeQuerySql`/`executeStatementSql`（`:63-69`/`:82-99`/`:112-122`）都以 transformEQL 为第一步，`executeQuerySql` 在 transform **之后**对 SELECT 单条 SQL 设置只读意图标记（`:91-93`）。
- **单 querySpace 不变式的两个锚点**：`GenSqlTransformer` 内部是单一 `querySpace`/`dialect` 字段，第二个不同 querySpace 抛 `ERR_ORM_NOT_SUPPORT_MULTIPLE_QUERY_SPACE_IN_ONE_SQL`（`nop-persistence/nop-orm/src/main/java/io/nop/orm/sql/GenSqlTransformer.java:222-230`，错误码定义 `nop-persistence/nop-orm/src/main/java/io/nop/orm/OrmErrors.java:321`）；`SQL` 对象本身只携带一个 querySpace，`JdbcTemplateImpl.runWithConnection` 以它定位唯一连接与事务（`nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/impl/JdbcTemplateImpl.java:160-178`、`:225-232`）。
- **transform 的缺分片值分支**：`collectShardSelections` 仅在 `policy==FAIL` 时抛 `ERR_ORM_SHARD_CONDITION_MISSING`（`GenSqlTransformer.java:139-144`）；`DEFAULT_SHARD` 分支经 `selectShardWhenMissing` 回落（`:145-152`）；`NONE` 与"无分支命中"均静默 fall-through 基表执行（`:159`）。`transform(SQL, List<Object>)` 是唯一入口，**不存在外部注入 `ShardSelection` 的通道**。
- **查询结构信息面缺口（评审 B1 核定）**：`ICompiledSql`（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/ICompiledSql.java:21-63`）只暴露 statementKind/querySpace/dataSetMeta/readEntityNames/writeEntityName/columnBinders/fieldMetas/sql 等，**没有** GROUP BY/DISTINCT/HAVING/聚合投影/FOR UPDATE/UNION 的结构标志，也没有 ORDER BY 信息——执行层今天无法可靠判定"不可归并构造"（SQL 文本正则不可靠），也无法为归并定位排序键（ORDER BY 列不必然在投影中，如 `select o.id from X o order by o.createTime`）。而 AST 侧结构在编译期完整可得：`_SqlQuerySelect` 携带 `distinct`/`groupBy`/`having`/`orderBy`/`forUpdate` 字段（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/ast/_gen/_SqlQuerySelect.java:19-33`；FOR UPDATE 文法 `nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4:298`），集合操作有独立节点 `SqlUnionSelect`；`EqlCompiler.compile` 在 `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlCompiler.java:101` 构造 `CompiledSql` 且 SELECT 分支已遍历投影构建 fieldMetas——填充点现成。
- **连接与事务**：一个事务一个连接一库——`JdbcTransaction` 懒取并缓存单连接（`nop-persistence/nop-dao/src/main/java/io/nop/dao/jdbc/txn/JdbcTransaction.java`）；事务注册表 `TransactionRegistry` 挂在 `IContext` 上，而 `IContext` 是 ThreadLocal（`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/context/BaseContextProvider.java:15`）——**跨线程看不到已注册事务**。
- **best-effort 事务组的真实承载**：`DefaultTransactionManager.getMainTxnGroup` 按 `nop.dao.config.txn-group-map`（装配于 `nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/beans/dao-defaults.beans.xml:22`）把 querySpace 映射到主事务组，**无映射时回落 `DEFAULT_TXN_GROUP`**（`nop-persistence/nop-dao/src/main/java/io/nop/dao/txn/impl/DefaultTransactionManager.java:141-151`）；`TransactionTemplateImpl.getTxnState` 发现主组事务已注册时自动为该 querySpace 创建子事务并挂入主组（`nop-persistence/nop-dao/src/main/java/io/nop/dao/txn/impl/TransactionTemplateImpl.java:129-144`）；提交时主组**顺序**提交各子事务（`nop-persistence/nop-dao/src/main/java/io/nop/dao/txn/impl/AbstractTransaction.java:365-372`），子事务回滚失败仅记日志与 rollbackOnly（`:194-210`）。注意：`GroupTransaction` 类是 doCommit/doRollback 全空的占位实现且 main 代码零实例化点，事务组语义完全由上述主/子事务组合承载——本设计不复活该空壳类。多分片写路径已在既有事务组语义内工作（`nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/EntityPersisterImpl.java:557-565` 按分片 querySpace 注册事务监听器）。
- **分页与执行抽象**：分页由方言 `IPaginationHandler.getPagedSql` 在单条 SQL 上完成（`JdbcTemplateImpl.java:387-392`）；仓库内**不存在**名为 ExecutorEngine 的执行框架，并行原语是 `GlobalExecutors` 线程池（`nop-kernel/nop-commons/src/main/java/io/nop/commons/concurrent/executor/GlobalExecutors.java`）与 `CompletionStage` 组合风格（`ITransactionTemplate.runInTransactionAsync`）。
- **SPI 现状**：`IShardSelector` = `selectShard` 值路由 + default 方法组，其中 `getShardNames`（分片名全集静态枚举，`nop-persistence/nop-dao/src/main/java/io/nop/dao/shard/IShardSelector.java:77`）现由 DDL N 表展开消费（仅返回分片名，不含 querySpace）；`MissingShardPolicy` 现有三值 `NONE/FAIL/DEFAULT_SHARD`（`nop-persistence/nop-dao/src/main/java/io/nop/dao/shard/MissingShardPolicy.java:25`）。

## 三、核心设计

### 3.1 接管点可判定信息面：编译链最小增量【评审 B1】

**问题**：§3.4 的归并分级要求接管点回答两个问题——"这条查询是否含不可归并构造"（分组聚合/FOR UPDATE/UNION）与"排序键在归并行里的哪一列"。§2.2 已核定：`ICompiledSql` 今天不携带任何结构信息，执行层无法回答；对 SQL 文本做正则/关键字扫描不可靠（别名、子查询、字符串字面量都会误判），fail-fast 无从谈起，更无法实现"真序"归并。

**裁定：从编译链暴露静态结构信息，载体为 `CompiledSql` 新增只读字段，由 `EqlCompiler.compile` 编译期填充**（AST 在编译期完整可得，且该处已在遍历投影构建 fieldMetas）：

| 暴露信息 | 内容 | 消费方 |
|---|---|---|
| 构造标志（construct flags） | `hasGroupBy` / `hasHaving` / `hasDistinct` / `hasSetOp`（UNION 等集合操作）/ `hasForUpdate` / `isBareCount`（投影为裸 count(*)） | fanout 资格判定（§3.4 显式失败集）与归并方式选择（count 求和） |
| orderBy 投影序数映射 | 每个顶层排序项 → `fieldMetas` 序数；**排序键未投影的查询无映射项** | ORDER BY 归并的取值列（§3.4）；映射缺失即"排序键未投影"，v1 显式失败 |

**理由**：(a) 构造判定与排序归并都只有编译期信息可用，"静态结构进编译期、值路由进执行期"与 `ShardMarkerInjector` 的 marker 静态信息是同一分野的既有先例；(b) 标志是编译产物静态属性，随 SQL 文本一起进 `QueryPlanCache`，缓存安全模型不变；(c) AST 字段现成（§2.2），填充是纯静态投影，无行为改动。
**排序键未投影即失败的边界**：归并行里没有排序键则比较器无从取值——"归并器做内存排序产出真序"的正确性前提就是排序键可从行内取得；此类查询 fanout 下显式失败（`ERR_ORM_SHARD_FANOUT_NOT_SUPPORT`，arg=排序键），业务出路是把排序键加入投影。
**范围联动**：范围行由"nop-orm-eql 预期零改动"修订为"允许最小增量"——这是被 B1 证伪的原始声明，两处已同步。
**被拒替代**：只收窄 ORDER BY 支持面（"仅支持排序键已投影的查询"）而不暴露构造标志——解决不了资格判定问题：GROUP BY/DISTINCT/FOR UPDATE 仍无法可靠识别，"显式失败集"全部落空，静默错误结果风险比支持面窄更糟；既然 flags 必须暴露，orderBy 序数映射是边际增量而非独立决策。

### 3.2 执行模型：接管点、资格收口与拆分机制【评审 B2/M3/M4/M5】

**接管点在执行层，且必须在 transform 之前**。引擎挂在 `JdbcQueryExecutor` 的三个入口（executeQuerySql/executeUpdateSql/executeStatementSql）上，对 transform+fetch+merge 整体接管，`IQueryExecutor` 契约不变。判定依据是编译产物静态信息（TABLE marker 的实体名与 `shardParamIndex`/`shardValue`，由 `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/ShardMarkerInjector.java` 写入）加绑定策略裁决。

**为什么必须在 transform 之前（评审 M3 修正后的真实理由）**：现行 `collectShardSelections` 对缺分片值只在 `policy==FAIL` 分支抛错（`GenSqlTransformer.java:139-144`）。若 FANOUT 策略声明后不作接管而泄漏进 transform，它**不命中任何既有分支**——与 `NONE` 同款静默 fall-through 基表执行：查询"成功"但扫的是不存在的基表或错误数据，正是 plan 2320 消灭的陷阱借新策略值复活。**纵深防御**：`GenSqlTransformer` 为 `policy==FANOUT` 新增一个显式失败分支（抛 `ERR_ORM_SHARD_FANOUT_NOT_SUPPORT`，arg=实体名）——这是 transform 的唯一增量，语义为"fanout 策略命中但引擎未接管"；即使未来新增执行入口忘记接入引擎判定，泄漏也响亮失败而非静默。

**fanout 资格收口（评审 B2）**：v1 最安全形态——**单条 SQL 至多一个 fanout 实体，且该 SQL 不得同时包含路由到其他分片落点的 useShard 实体**。fanout 绑定实体数 >1，或 fanout 实体与带分片值实体混用，一律 `ERR_ORM_SHARD_FANOUT_NOT_SUPPORT`。理由：(a) fanout 场景没有分片值，JOIN 谓词若不相关分片键列，跨分片配对在每份物理 SQL 中都不出现（各物理表只有本分片行），合并结果**静默缺行**——直接违反"无静默错误"硬裁定；(b) 混合形态（一实体带值路由 shard_i + 一实体 fanout）在分库形态触发 querySpace 冲突显式抛错、在单库分表形态静默缺行——同一 SQL 两种部署形态行为不一致，宁可统一拒绝；(c) 保留"同策略多实体共置受限 JOIN"需要"谓词覆盖双方分片键"的编译期谓词校验，v1 无业务拉动（评审同判定不建议 v1 做）。SQL 内混入其他 querySpace 的非分片表仍由既有 `setQuerySpace` 校验显式拦截（每分片 transform 自然获得的行为，不新增语义）。本裁定统一约束 §3.5（JOIN 边界）与 §3.8（错误码 arg）。

**每分片物理 SQL 的产出机制（评审 M5）**：`transform(SQL, List<Object>)` 是唯一入口，无注入通道。裁定：引擎对每个分片包装一层**强制 selector**（装饰 `IShardSelector`）——`getMissingShardPolicy` 返回 `DEFAULT_SHARD`、`selectShardWhenMissing` 返回该分片的强制 `ShardSelection`——骑既有 default-shard 分支（`GenSqlTransformer.java:145-152`：addShardSelection + setQuerySpace），per-shard transform 自然产出该分片的表名后缀与 querySpace，方言由 `collectShardSelections` 尾部按该 querySpace 解析（`:166`）。**transform 路由链零改动**（唯一增量是上述 FANOUT 兜底失败分支）；B2 收口后 fanout 查询无分片值等值条件，`selectShard` 主路径不参与。伪代码（语义契约）：

```
executeFanoutQuery(compiled, params, range):
    # 前置：statementKind==SELECT；构造标志放行（§3.4）；资格收口通过（至多一个 fanout 实体）
    shards = selector.selectAllShards(entityName)          # SPI 新增（§3.8 m1），分片全集含 querySpace
    totalRows = 0
    for shard in shards:                                   # v1 串行：调用线程逐分片执行
        forced = forceSelector(selector, shard)            # 强制 selector：policy→DEFAULT_SHARD + 强制落点
        sql = new GenSqlTransformer(forced, ...).transform(compiled.getSql(), params).end()
        sql = sql.readOnly(true)                           # 逐份显式设置（见 §3.7）
        rows = fetchRows(sql, perShardRange(range))        # 连接经既有事务组/读写分离路径获取（§3.3）
        totalRows += rows.size()
        if totalRows > fanoutMaxRows:                      # 逐分片增量计数，达限立即中断
            throw ERR_ORM_SHARD_FANOUT_ROW_LIMIT_EXCEEDED  # 无部分结果，异常传播（M4）
        collected.addAll(rows)
    return callback(merge(collected))                      # 按支持集归并（§3.4），合并数据集走既有行组装
```

**行数上限的中断语义（评审 M4）**：计数是**逐分片增量**的——每拉回一个分片立即累计，达限立即中断（不再向剩余分片发起物理 SQL），已拉回数据直接丢弃，不返回截断集，异常向调用方传播。上限是内存兜底的正确性边界，不是截断语义；全分片拉回后才校验的形态起不到兜底作用，被拒。

**执行形态：v1 串行**。三个依据：(a) 事务注册表是 ThreadLocal 域（§2.2），并行分片线程拿不到调用方已注册事务，事务内的并行分片连接将退化成各自的非事务连接——同一语句各分片读到不同隔离级别的数据，正确性不可推理；(b) 并行使连接占用放大 N 倍，OLTP 请求路径上对连接池是隐式背压；(c) 常见分片数（4–64）下串行归并的延迟可接受。与既有执行风格的关系：仓库无 ExecutorEngine 框架，nop 的异步组合子是 `CompletionStage` + `GlobalExecutors`；若未来需要并行，正确形态是先解决事务上下文跨线程传播（`IContext` 快照复制），再以 `runInTransactionAsync` 同族风格实现，v1 不预埋。

### 3.3 连接与事务边界【评审 M1/m3】

**跨分片读：无事务语义，显式声明**。N 份物理 SQL 使用 N 个独立连接（各自经既有 `withTxn(SUPPORTS)` 路径，`JdbcTemplateImpl.java:180-191`），不存在跨分片统一快照。活动事务内的 fan-out 读：各分片连接经 `TransactionTemplateImpl.getSubTransaction` 自动加入事务组（既有机制，`:129-144`），满足"事务内一律主库"的读写分离一致性契约，但**各分片仍是各自库的独立快照**——跨分片读的一致性等级 = "每分片内一致，分片间无全局快照"。需要跨分片强一致的业务必须走单分片键路由或其他应用层手段；该声明写入 owner doc，不做隐式补偿。

**自动入组的成立条件（评审 m3）**：`getMainTxnGroup` 对未映射 querySpace 回落 `DEFAULT_TXN_GROUP`（`DefaultTransactionManager.java:141-151`），业务事务开在缺省组时各分片连接**自动**成为其子事务——这是缺省部署下的成立形态，无需配置。显式多主组部署（`txn-group-map` 配置了多个主组）：fanout 策略的全部分片 querySpace 必须映射到同一主组，否则各分片各随其主组、由各自事务所有者提交——该形态下"同一逻辑语句的分片连接分属多个事务组"写入 owner doc 契约，由 best-effort 语义覆盖，引擎不做额外收敛。

**跨分片写：v1 不进执行引擎**。无分片键的 UPDATE/DELETE 命中 fanout 策略时**统一归 `ERR_ORM_SHARD_FANOUT_NOT_SUPPORT`**（评审 M1：`ERR_ORM_SHARD_CONDITION_MISSING` 的语义是"缺分片键"，对"策略声明了 fanout 但语句类型越界"的失败不贴切；§3.8 错误码表同步）。理由：非原子的跨分片 DML 出现在 OLTP 请求路径上是事故源，而"全表修正"类低频运维操作有更安全的既有出路——nop-batch 逐分片作业（事务边界清晰、可断点续传、可校验）。逐实体写路由（`EntityPersisterImpl.getShardSelection`，`:265`）不受影响：它本来就是每实体单分片写，多实体多分片时自然落入既有事务组语义。

**与 best-effort 事务组的关系（未来若开写 fan-out）**：裁定为复用既有机制而非新建——把各分片 querySpace 映射到同一主事务组（`nop.dao.config.txn-group-map` 配置层完成），子事务由 `TransactionTemplateImpl` 自动创建、主组顺序提交。部分提交风险（第 k 片提交失败后前 k-1 片已提交）与既有跨 querySpace 业务写完全同构，是 XA non-goal（`nop-orm-scope-and-non-goals.md` §一.6"跨数据源按 best-effort 顺序提交"）的直接推论；引擎不引入第二套组提交，也不提供"反向补偿"。该裁定使 v2 写 fan-out 的增量收敛为"放开语句类型白名单 + 错误信息携带各分片执行明细"，事务面零新增。

### 3.4 归并语义分级【评审 B1 联动】

| 构造 | v1 语义 | 归并方式与裁定理由 |
|---|---|---|
| 无 ORDER BY 的 SELECT | 支持 | 各分片结果顺序拼接（concat），行组装走既有 `transformRow` 链路 |
| ORDER BY | 支持（**排序键须已投影**） | **排序由归并器做内存排序**：排序键经 §3.1 的 orderBy 投影序数映射从归并行取值，全量拉回后排序（每分片保留原 ORDER BY 下推仅为方言优化，非正确性依赖）。排序键未投影的查询显式失败（§3.1 边界）。这是"显式语义"而非整体 fail-fast：全量拉回 + 内存排序产出的是全量数据的真序，正确性无妥协；内存风险由 `fanout-max-rows` 逐分片增量计数兜底、超限立即中断（§3.2）。溢写磁盘不做（OLTP 跨分片查询超限即用法错误） |
| 无分组裸 count(*) | 支持 | 构造标志 `isBareCount` 判定，各分片 count 求和；非裸 count 聚合不适用本行 |
| 分页（findPage / range） | 支持（浅分页） | 每分片下推 offset+limit（取各分片前缀，全局第 k 名必在每分片前 offset+limit 名内，正确性成立），内存排序后截断 `[offset, offset+limit)`；总数 = 各分片 count 求和。深分页的行数放大（offset+limit 乘分片数）由同一 `fanout-max-rows` 上限显式拦截 |
| GROUP BY / HAVING / DISTINCT / 非 count 聚合（SUM/AVG 等） | **显式失败**（构造标志判定，`ERR_ORM_SHARD_FANOUT_NOT_SUPPORT`） | 正确归并需两阶段聚合（AVG 须分解为 sum/count 对、DISTINCT 须全量去重、HAVING 依赖分组结果），是执行引擎深水区且 v1 无业务拉动；判定依赖 §3.1 构造标志——对 SQL 文本正则不可靠，此即 §3.1 存在的理由。出路：带分片键单分片聚合（既有）、或 nop-metadata 应用层联邦（roadmap baseline 已有 BI 场景覆盖） |
| FOR UPDATE / UNION 等集合操作 / DDL / 存储过程 | **显式失败** | FOR UPDATE 跨分片锁无法协调（构造标志判定）；集合操作与 §3.2 资格收口叠加后无合法 fanout 形态 |

分级原则：**正确性能完整保住的显式做（内存上限兜底），正确性需要深水区机制的显式拒**。上限语义统一为"逐分片增量累计行数超过 `nop.orm.shard.fanout-max-rows` 即抛 `ERR_ORM_SHARD_FANOUT_ROW_LIMIT_EXCEEDED` 并中断"，缺省值（建议 10000）由 WI8 承载 plan 裁定。

### 3.5 跨分片 JOIN 边界【评审 B2】

- **绑定表 JOIN（同策略同分片值）**：路由结果同一 querySpace 同名后缀，天然单分片，保持原生 JOIN——既有行为，引擎零参与（策略设计 §3.2 已裁定绑定表用"同策略名"隐式表达）。
- **其余跨分片 JOIN：v1 不支持，显式失败**。判定由 §3.2 资格收口统一承载：fanout 实体数 >1（隐式跨片 JOIN 意图）或 fanout 与带值实体混用 → `ERR_ORM_SHARD_FANOUT_NOT_SUPPORT`。理由：(a) fanout 场景无分片值，跨分片配对在每份物理 SQL 中不出现，合并静默缺行（§3.2 (a)）；(b) 归并 JOIN 需要两侧全量拉回 + 应用层 hash/nested-loop join，内存与正确性风险双高；(c) nop 的 OLTP JOIN 几乎总沿外键，绑定表声明覆盖"同分片关联"的合法场景，"分片键不同的实体互相关联"本身是分片设计反模式——fail-fast 把设计错误提前到开发期暴露；(d) 替代路径存在：应用层两步查询（先查 A、再按关联键 in 查 B，in 查询可走 fan-out 或单分片），分析型跨库关联由 nop-metadata 联邦承担。

### 3.6 广播表重审

前置裁定（策略设计 §四）以"一条 SQL 写多个 querySpace，超出单 querySpace 事务模型"拒绝广播表。本设计重新立项后**维持拒绝**，且拒绝理由升级：

1. **收益面消失**：广播表的唯一价值是"小维度表各分片本地副本、JOIN 免跨分片"，而 v1 跨分片 JOIN 本就不支持——为不存在的收益引入跨分片非原子写，风险收益不成比例。
2. **不一致的代价不对称**：best-effort 事务组下广播写部分失败（第 3 片提交失败）造成各分片副本不一致，且**无自动修复路径**；业务写的不一致窗口是业务可补偿的，广播副本不一致会静默渗入此后所有读结果，违背"无静默错误"硬裁定。
3. **重审通道**：WI9 搬迁管道落地后，"广播"可表达为一主多从的 CDC 订阅（nop-stream-connector-debezium 源 + jdbc exactly-once sink，exactly-once 由 sink 保证），同步正确性由管道断点续传与校验作业保障，仍不进 OLTP 写路径。届时按需另立设计。

### 3.7 组合面【评审 M5 修正】

- **读写分离**（`nop-dao-readwrite-splitting-design.md` §五已声明正交叠加）：分片引擎只产出 querySpace，主从分流发生在各 querySpace 的 DataSource 内部。只读意图的传播方式（评审 M5 修正）：`JdbcQueryExecutor.executeQuerySql:91-93` 的 `readOnly(true)` 是在 transform 之后对**单条** SQL 设置的；引擎产出的 N 份物理 SQL **必须逐份显式设置**同一规则（`statementKind==SELECT` → `readOnly(true)`，见 §3.2 伪代码），不存在"标记自然传播到多份 SQL"的既有机制。无事务的 fan-out 读经此标记落到各分片的从库。
- **动态 querySpace**（`CFG_ORM_ENABLE_DYNAMIC_QUERY_SPACE`）：物理 SQL 走既有 `withTxn` 路径，`useDynamicQuerySpace` 映射对每份物理 SQL 自动生效，无特殊处理。
- **ORM 一级缓存**：fan-out 结果按既有行组装进 session，L1 身份键 `(entityName, id)` 依赖 id 跨分片唯一——分布式 ID（snowflake/uuid/编码规则，roadmap baseline 已有）是分片体系的前置约束，写入 owner doc；违反该前提（多分片自增主键）会在 L1 缓存产生身份冲突，属分片接入方的模型责任。
- **回填（可检索加密）/ 搬迁（WI9）**：均走 nop-batch 按 `ShardDdlHelper.shardTableNames`（`nop-persistence/nop-orm/src/main/java/io/nop/orm/ddl/ShardDdlHelper.java:45-53`）的分片名全集逐片读写，不经执行引擎——批量域与 OLTP 执行域分离，互不依赖。
- **sql-lib / 原生 SQL**：不解析、不 fan-out；原生 SQL 的分片语义由 SQL 作者显式负责（与读写分离"不解析 SQL"的架构原则同构）。

### 3.8 使用契约【评审 M1/M2/m1/m2】

**配置**（对齐策略配置的 JSON 形态）：

```json
{
  "nop.orm.shard": {
    "strategies": {
      "order-mod": {
        "type": "hash-mod",
        "shard-count": 16,
        "missing-shard-policy": "fanout",
        "entities": ["app.entity.NopOrder"]
      }
    }
  },
  "nop.orm.shard.fanout-enabled": true,
  "nop.orm.shard.fanout-max-rows": 10000
}
```

三件套的语义与门控关系：

| 配置 | 级别 | 语义 |
|---|---|---|
| `missing-shard-policy: fanout` | 策略级 | 声明该策略绑定实体的缺分片值查询按分片全集展开；`MissingShardPolicy` 增加**第四取值** `FANOUT`（既有 NONE/FAIL/DEFAULT_SHARD，评审 M2 勘误：此前误写"第三取值"）。资格仅限 SELECT 且满足 §3.2 收口，越界一律 `ERR_ORM_SHARD_FANOUT_NOT_SUPPORT` |
| `nop.orm.shard.fanout-enabled` | 全局，缺省 false | 总开关。关闭时 fanout 策略**运行期退化为 fail**（不静默降级单分片），启动期 WARN——保留"一键关闭"的应急刹车属性且不产生配置矛盾 |
| `nop.orm.shard.fanout-max-rows` | 全局 | 归并行数上限（逐分片增量计数，§3.2），超限抛 `ERR_ORM_SHARD_FANOUT_ROW_LIMIT_EXCEEDED`；缺省值由 WI8 plan 裁定 |

**SPI 扩展（评审 m1）**：`IShardSelector` 新增 `selectAllShards(entityName)` 返回 `List<ShardSelection>`（分片名 + querySpace 二元组全集）。与既有 `getShardNames`（`IShardSelector.java:77`，仅分片名、消费方是 DDL 展开）的差异：fanout 执行需要每个分片的完整落点，querySpace 由 `query-space-pattern` 推导而该知识在策略内部，调用方无法从分片名自行组装。default 实现按 `getShardNames` + 策略 pattern 推导，`ConfigShardSelector` 原生实现；二者必须同源（同一分片全集），WI8 以断言固化。

**错误码清单**：

| 错误码 | 归属 | 语义 |
|---|---|---|
| `ERR_ORM_SHARD_FANOUT_NOT_SUPPORT` | OrmErrors（新增） | 统一收口（评审 M1）：不支持的跨分片构造（分组聚合/FOR UPDATE/UNION，arg=构造名）、排序键未投影（arg=排序键）、fanout 资格越界（多实体/混用/非 SELECT，arg=实体名与原因）、fanout 策略泄漏进 transform 的兜底失败（§3.2 纵深防御） |
| `ERR_ORM_SHARD_FANOUT_ROW_LIMIT_EXCEEDED` | OrmErrors（新增） | 归并行数超 `fanout-max-rows`（逐分片增量计数触发）；arg 携带实际行数与上限 |
| `ERR_ORM_SHARD_CONDITION_MISSING` | OrmErrors（复用，`:329`） | 仅用于全局开关关闭时 fanout 策略的运行期退化。**WI8 注记（评审 m2）**：该错误码现行文案硬编码"missing-shard-policy=fail"语义，用于开关关闭退化场景文案不贴切——实施时需为该错误补 arg（如 policy 实际值/关闭原因）或调文案，避免误导排障 |
| `ERR_DAO_SHARD_CONFIG_INVALID` | DaoErrors（复用，`:83`） | 配置解析错误（如 fanout 声明在未绑定实体上） |

**可观测性**：每分片物理 SQL 的执行明细由既有日志与指标覆盖——`nop.jdbc.run` 日志逐条打印 querySpace 与耗时（`JdbcTemplateImpl.java:175`），`IDaoMetrics` 逐物理 SQL 计量。引擎层新增汇总契约：日志 `nop.orm.shard.fanout`（逻辑 SQL 名、分片数、归并行数、归并耗时、是否达限中断）；指标 `nop.orm.shard.fanout.query`（计数/耗时）与 `nop.orm.shard.fanout.rows`（归并行数分布），失败按错误码维度计数。命名对齐平台小数点分层惯例。

## 四、拒绝了什么

| 被拒方案 | 拒绝理由 |
|---|---|
| 执行层对 SQL 文本正则/关键字扫描判定构造（评审 B1 替代项） | 别名、子查询、字符串字面量均致误判；fail-fast 落空即静默错误结果，评审核定不可靠 |
| 只收窄 ORDER BY 支持面而不暴露编译期结构信息（评审 B1 替代项） | 解决不了资格判定问题：GROUP BY/DISTINCT/FOR UPDATE 无法可靠识别，"显式失败集"全部落空；既然构造标志必须暴露，orderBy 序数映射是边际增量 |
| `GenSqlTransformer` 一次产出 N 份物理 SQL | 需 transformer 内部感知分组渲染：单一 querySpace/dialect 字段与 marker→ShardSelection 1:1 映射、setQuerySpace fail-fast 全部要改，错误面侵入编译产物消费链；强制 selector + 多次 transform 零路由链改动达成同一效果 |
| EQL 编译期 fan-out 改写（UNION ALL 展开 N 分支） | 编译产物按 SQL 文本进 `QueryPlanCache`，N 是随扩容变化的部署属性；执行期值路由原则（策略设计 §3.6）已裁定静态编译/动态路由分野，编译期展开违背之 |
| 并行 fan-out（GlobalExecutors 线程池分发） | 事务注册表是 ThreadLocal 域（`BaseContextProvider.java:15`），并行分片线程拿不到调用方事务，事务内各分片退化为独立非事务连接——同语句跨分片隔离级别不一致；且连接占用放大 N 倍。先决条件（事务上下文跨线程传播）未解决前不预埋 |
| 流式 K 路归并 + 每分片游标（SS 形态，排序/分页的标准解） | 需要 N 个并行持有游标的连接，与"executeQuery 回调消费完即关闭"的 `IDataSet` 生命周期模型冲突；v1 内存归并在行数上限内语义等价且实现面小。列为 v2 演进（配合并行化一起做） |
| 归并行数超限时溢写磁盘 / 全分片拉回后才校验行数 | OLTP 请求路径上超限即用法错误，响亮失败优于把执行引擎变成批处理引擎，大批量场景归 nop-batch；全量拉回后校验起不到内存兜底作用，裁定为逐分片增量计数 + 立即中断（评审 M4） |
| 跨分片写 fan-out（v1） | 非原子跨分片 DML 进 OLTP 请求路径是事故源；全表修正是低频运维操作，nop-batch 逐分片作业覆盖且事务边界清晰。v2 放开时事务面复用既有事务组（§3.3） |
| 独立的执行引擎组提交/补偿机制 | 与既有事务组语义重复且更弱化；XA non-goal 下 best-effort 顺序提交已是平台统一答案，第二套组提交必然产生语义分叉 |
| 广播表 | 维持拒绝且理由升级（§3.6）：收益场景（免跨分片 JOIN 的本地维度表）在 v1 不存在，部分失败造成无修复路径的副本不一致 |
| 归并器支持聚合下推（两阶段聚合） | 正确性陷阱密集（AVG 分解、DISTINCT 去重、HAVING 交互）且 v1 无业务拉动；分析场景已由 nop-metadata 联邦覆盖 |
| 同策略多 fanout 实体"共置受限 JOIN"（谓词须覆盖双方分片键） | 需要编译期谓词覆盖性校验（分片键等值条件的静态证明），复杂度高且 v1 无业务拉动；多实体 fanout 的静默缺行风险（评审 B2）优先收口为显式失败 |
| 裸 filename 风格的独立开关（如 per-request hint 强制单分片/fanout） | 与策略设计 §四拒绝 Hint 路由同因：隐式 ThreadLocal 语义可追溯性差；策略声明 + 全局开关两级已覆盖"按需启用/应急关闭" |

## 五、与已有设计的关系

- **`nop-orm-sharding-strategy-design.md`（前置契约）**：本设计是对其 §四 fan-out 拒绝的重新立项，落地后其 §3.4 路由判定伪代码的 missing-shard-policy 分支增加 fanout 取值、§3.6 执行期路由原则被本设计沿用并扩展为"静态结构进编译期（§3.1）/ 值路由进执行期"；`ShardSelection` 二元组、策略注册表、绑定校验、DDL N 表展开全部原样复用，SPI 仅增 `selectAllShards`（与 `getShardNames` 同源，§3.8 m1）。两篇文档在本文转 active 时同步修订。
- **`nop-orm-scope-and-non-goals.md`**：不触碰任何既有裁定；§一.6 XA non-goal 与 best-effort 顺序提交是 §3.3 事务裁定的上位依据；§3.5"连接级资源归 DataSource 层"与归并器不碰连接池的边界一致。
- **`nop-dao-readwrite-splitting-design.md`**：正交叠加（§3.7）；"事务内一律主库"契约被 fan-out 读继承（各分片事务内连接均为该分片主库）。
- **`docs-for-ai/02-core-guides/concurrency-and-transactions.md`**：事务组顺序提交语义、ORM 一级缓存规则（fan-out 组装不覆盖已初始化字段）在跨分片场景下继续成立；实施后该 guide 需补"跨分片读无统一快照"与"多主组部署的分片入组契约"（§3.3，WI8 owner doc 项）。
- **roadmap 定位**：本设计承载 WI7（Decision），实施归 WI8（Feature：归并正确性测试矩阵 + E2E；含 §3.8 的两处 WI8 注记）；WI9 搬迁管道是广播表重审的再评估前提（§3.6）。
