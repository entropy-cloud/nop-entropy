# nop-orm 实现代码检查报告（check2）

- 检查日期: 2026-08-23
- 模块路径: nop-persistence/nop-orm
- 文件数: 152（src/main/java，其中 8 个为 `_gen/` 生成文件，按规则跳过，手工代码 144 个）
- 覆盖范围声明: 深读 93 个手工编写的 Java 文件（列举关键类：OrmSessionImpl、OrmSessionEntityCache、TenantOrmSessionEntityCache、StatelessOrmSessionEntityCache、CascadeFlusher、OrmEntity、OrmEntitySet、DynamicOrmEntity、EntityPersisterImpl、CollectionPersisterImpl、JdbcEntityPersistDriver、JdbcCollectionPersistDriver、BatchActionQueueImpl、SessionBatchActionQueue、LogicalDeleteHelper、OrmRevisionHelper、OrmTimestampHelper、GenSqlHelper、GenSqlTransformer、LoadedOrmModel、SimpleCachedQueryPlan、TenantCachedQueryPlan、QueryPlanCacheKey、OrmBatchLoadQueueImpl、JdbcQueryExecutor、OrmTemplateImpl、OrmSessionRegistry、XplOrmInterceptor、XplOrmInterceptorLoader、SessionFactoryImpl、SessionFactoryConfig、OrmSessionFactoryBean、DefaultOrmModelProvider、TenantAwareOrmModelProvider、PersistEnvBuilder、OrmEntityDao、DaoQueryHelper、SqlLibManager、SqlLibInvoker、SqlLibDictLoader、SqlItemModel、QuerySqlItemModel、SqlFiledRowMapper、MdxQuerySplitter、MdxQueryExecutor、OrmEntityHelper、OrmAssembly、OrmCompositePk、OrmManyToManyHelper、OrmEntityIdGenerator、AbstractOrmKeyValueTable、Json/Xml/OrmFile/OrmFileList/FloatingScale 组件、DaoEntityResource、DataBaseSchemaInitializer、DataInitInitializer、AddTenantColInitializer 等）。其余未深读文件为纯接口（IOrmSession/IOrmTemplate 等）、常量/错误码类（OrmConfigs/OrmConstants/OrmErrors）与简单模型类，已通过模式扫描（grep）覆盖。模式扫描（@Inject private、Spring 依赖、SimpleDateFormat、空 catch、bare RuntimeException、SQL 字符串拼接、静态可变状态、资源未关闭）覆盖全部 144 个手工文件；未发现 D7（IoC 规范）违规、未发现 Spring 依赖。重点覆盖了 session/实体状态机、dirty 检测、collection 持久化与级联、批量处理、拦截器、游标分页、语句缓存。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 3 |
| P2 | 8 |
| P3 | 9 |

## 发现列表

### [P1] LoadedOrmModel.getIdText 复合主键分支生成非法 SQL 片段（错用 getColumns() 且逗号位置错误）

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/LoadedOrmModel.java:130-141`
- **维度**: D1
- **证据**:
```java
} else {
    StringBuilder sb = new StringBuilder();
    for (int i = 0, n = entityModel.getPkColumns().size(); i < n; i++) {
        String code = entityModel.getColumns().get(i).getCode();   // 错误1: 遍历的是全部列而非主键列
        sb.append(alias).append(".").append(dialect.normalizeColumnName(code));
        if (i != 0)
            sb.append(',');                                        // 错误2: 逗号追加在元素之后
    }
    return sb.toString();
}
```
- **现状**: 复合主键分支有两个叠加缺陷：(1) 取列时用 `entityModel.getColumns()`（实体全部列）按下标取前 N 个，而非 `getPkColumns()`，只要主键列不是列定义的前 N 个就会取到非主键列；(2) 逗号在元素之后追加，3 个主键列时输出形如 `o.A o.B,o.C,`（首两个列之间无分隔符、末尾多余逗号），是非法 SQL。
- **风险**: 唯一调用方 `FilterSqlHelper.buildFilterSQL`（FilterSqlHelper.java:39-43）在数据权限过滤 SQL 涉及多表时拼接 ` and <idText> in(select ...)`。对复合主键实体启用数据权限（DataAuthEntityFilterProvider → dataAuthChecker 返回多表过滤）时，生成的 SQL 语法错误，查询直接失败；即使侥幸可解析，过滤列也可能不是主键列，导致数据权限过滤条件错误（越权或误拦）。
- **建议**: 遍历 `entityModel.getPkColumns()`，并把逗号追加改为前置（`if (i != 0) sb.append(',');` 放在 append 列名之前），与同模块 `GenSqlHelper.genIdEq`/`appendEntityPk` 的正确写法对齐。
- **误报排除**: 已读取 getIdText 全部调用方（FilterSqlHelper.java:41、ISqlCompileTool 接口），确认多表分支会实际使用该返回值拼入 SQL；已对比 `getColumns()` 与 `getPkColumns()` 语义（IEntityModel），确认前者为全部列。单列主键走 `prop.isColumnModel()` 分支不受影响，故仅在复合主键时触发。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。`getIdText` 复合主键分支改为遍历 `getPkColumns()`，逗号改为前置追加。回归测试 `TestLoadedOrmModel#testGetIdTextCompositePk`（红验证：临时回退修复后断言失败，旧代码输出 `o.ENTITY_NAMEo.ENTITY_ID,o.FIELD_NAME,`，正确值为 `o.ENTITY_NAME,o.ENTITY_ID,o.FIELD_NAME`）；`#testGetIdTextSinglePk` 验证单列主键行为不变。

### [P1] SqlLibDictLoader.existsDict 传入 dictName 而非去除前缀的 sqlName，sql/ 字典存在性判断恒为 false

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/sql_lib/dict/SqlLibDictLoader.java:91-101`
- **维度**: D1（兼 D8 契约不一致）
- **证据**:
```java
public boolean existsDict(String dictName) {
    String sqlName = StringHelper.removeHead(dictName, OrmConstants.SQL_DICT_PREFIX);
    checkDictSql(sqlName);
    try {
        return sqlLibManager.getSqlItemModel(dictName) != null;   // 应传 sqlName
    } catch (NopException e) {
        return false;
    }
}
```
- **现状**: `SQL_DICT_PREFIX = "sql/"`（OrmConstants.java:128）。`loadDict` 中同样的查找使用去除前缀后的 `sqlName`（`sqlLibManager.getSqlItemModel(sqlName)`，第 87 行），而 `existsDict` 误传原始 `dictName`。`getSqlItemModel` 按 `lastIndexOf('.')` 拆分类名，得到 `"sql/xxx"`，进而构造 `module:/sql/sql/xxx.sql-lib.xml`（双重 sql/ 前缀）的资源路径，必然找不到，抛 NopException 后被 catch 返回 false。
- **风险**: `DictProvider.existsDict` 按前缀分发到此方法；`nop.graphql.check-dict-when-init` 默认为 true（GraphQLConfigs.java:64-65），`DictLabelFetcherProvider`（nop-graphql-core）在模型装载时校验字典存在性，任何使用合法 `sql/` 前缀字典作为 label 字典的 GraphQL 模型都会在装载时抛 `ERR_GRAPHQL_UNKNOWN_DICT`，服务无法启动/ schema 编译失败。字典本身在运行期可以正常加载（loadDict 正确），属于"配置正确但被误判不存在"。
- **建议**: 第 100 行改为 `sqlLibManager.getSqlItemModel(sqlName)`；并补充针对 `sql/` 字典 existsDict 为 true 的回归测试。
- **误报排除**: 已对照同文件 loadDict（第 85-87 行）确认正确入参是 sqlName；已读取 SqlLibManager.getSqlItemModel(String)（第 169-181 行）确认路径构造方式 `buildSqlLibPathFromClassName = "module:/sql/" + className + ...`；已确认 DictProvider.existsDict 的前缀分发逻辑（DictProvider.java:72-82）与 GraphQLConfigs 默认值。仓库内暂无 `sql/` 字典的使用样例（下游应用特性），但代码级可证明该分支必然返回 false。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。`existsDict` 第 100 行改为传去除前缀后的 `sqlName`，与 `loadDict` 对齐。回归测试 `TestSqlLibDictLoader#testExistsDict`（红验证：stash 主代码后 `assertTrue(existsDict("sql/test.demo_dict"))` 失败返回 false；同时验证不存在字典仍返回 false）。

### [P1] 游标分页的游标条件只按主键比较，忽略 orderBy 排序字段，非主键排序时分页跳行/漏行

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/dao/DaoQueryHelper.java:335-338`（appendGtLastEntity，appendLtCursorEntity 同理 359-362）
- **维度**: D1
- **证据**:
```java
static void appendGtLastEntity(SQL.SqlBuilder sb, IEntityModel entityModel, IOrmEntity entity) {
    if (entityModel.getPkColumns().size() == 1) {
        IColumnModel col = entityModel.getPkColumns().get(0);
        sb.owner("o").gt(col.getName(), entity.orm_propValue(col.getPropId()));
    } else { ... "(a,b) > (?,?)" 行值比较 ... }
}
// queryToFindNextSql (276-302): orderByPk = 用户 orderBy + 补齐主键，
// 但 where 追加的游标条件 appendGtLastEntity 只比较主键列
```
- **现状**: `queryToFindNextSql/queryToFindPrevSql` 把用户 `orderBy` 原样放进 ORDER BY（并补齐主键保证确定性），但 "下一页" 的过滤条件只用主键（`o.id > lastEntity.id` 或复合主键行值比较）。当 orderBy 含非主键字段（如 createTime desc）时，第二页条件 `id > 上一页最后一条的id` 与排序键无关。
- **风险**: 标准业务链路可触发：`CrudBizModel.doFindPageByQueryDirectly`（CrudBizModel.java:349）在 `query.cursor` 非空时调用 `dao.findPageAndReturnCursor` → `findNext(lastEntity, filter, query.getOrderBy(), ...)`。前端按任意非主键字段排序并用游标翻页时，主键序与排序序不一致的记录会被永久跳过或跨页重复，静默丢数据。另外复合主键使用 `(a,b)>(?,?)` 行值比较，在 SQL Server 等不支持行值的方言上直接报语法错误。
- **建议**: 游标条件应使用与 ORDER BY 一致的排序键元组做 keyset 条件（`(sortCol1, sortCol2, pk) > (?,?,?)`），或当 orderBy 含非主键字段时显式抛出"不支持该排序下的游标分页"，避免静默错页。
- **误报排除**: 已读取 queryToFindNextSql/queryToFindPrevSql/appendGtLastEntity/appendLtCursorEntity 全文与调用链（OrmEntityDao.findNext/findPrev/findPageAndReturnCursor、CrudBizModel.doFindPageByQueryDirectly、IEntityDao.findNext 的 default 方法），确认没有任何地方强制把 orderBy 归一化为主键。IEntityDao.findNext 的 javadoc 示例虽以 `o.id > lastEntity.id` 说明（暗示按主键游标），但方法签名接受任意 orderBy 且上层（CrudBizModel）直接透传用户排序，缺陷在现实路径上成立，故定 P1 而非 P2。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。`DaoQueryHelper` 重写游标条件构造：新增 `appendCursorCondition` 按完整排序键（用户 orderBy + 补齐主键）生成 keyset 条件，多字段时按 `(f1 cmp v1 or (f1 = v1 and f2 cmp v2) ...)` 展开，替代仅部分方言支持的行值比较；`desc` 字段自动反转比较方向；等值前缀对 null 游标值使用 `is null`；排序字段带非 `o` owner、不是实体简单列属性或游标值为 null 无法比较时显式抛 `ERR_ORM_CURSOR_ORDER_BY_NOT_COLUMN`/`ERR_ORM_CURSOR_SORT_VALUE_NULL`（OrmErrors 新增常量，文案沿用本接口中文惯例），不再静默错页。同时修复复核中确认的独立缺陷：`queryToFindPrevSql` 在 filter 为空时漏生成 `where` 关键字（旧测试注释中已记录）。回归测试 `TestEntityDaoQuery#testFindNextOrderByNonPk`（红验证：旧代码第二页返回 `[204,205]` 而正确值为 `[205,203]`，跳行）与 `#testFindPrevOrderByWithoutFilter`（红验证：旧代码因缺 where 抛 SQL 解析错误）。

### [P2] OrmEntitySet.orm_reset 在 flush 之后调用会把集合清空（initialEntities 与 entities 别名共享）

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/support/OrmEntitySet.java:354-366`（配合 648-655）
- **维度**: D1
- **证据**:
```java
public void orm_reset() {
    // proxy集合尚未从数据库加载，没有需要恢复的内容。...
    if (orm_proxy())
        return;
    this.entities.clear();                       // initialEntities == entities 时两者同时被清空
    if (this.initialEntities != null) {
        this.entities.addAll(initialEntities);   // 别名对象已空，恢复结果为空集合
    }
    this.orm_clearDirty();
}
// orm_clearDirty(): this.initialEntities = this.entities;  <-- 建立别名
```
- **现状**: `CascadeFlusher.cascadeCollection`（CascadeFlusher.java:340）处理完集合后调用 `coll.orm_clearDirty()`，使 `initialEntities` 与 `entities` 指向同一对象。此后若调用 `orm_reset()`（`session.reset()` → `resetEntity` → 每个ToMany集合 `pc.orm_reset()`，OrmSessionImpl.java:1301-1311），`entities.clear()` 会连带清空别名引用的 `initialEntities`，恢复结果为空集合。
- **风险**: `IOrmEntity.reset()` 是文档化的"在内存中回滚未保存修改"的公开 API（IOrmEntity.java:21）。修改集合并 flush 成功后再在同一 session 内调用 reset()（例如按记录回滚的场景），集合中的所有已加载元素从内存中丢失，后续读取 size()==0，表现为数据"消失"，直到集合被重新加载。
- **建议**: `orm_reset()` 开头先处理别名：`if (entities == initialEntities) return;`（无可恢复内容直接返回），或先 `initialEntities = new LinkedHashSet<>(initialEntities)` 再 clear；与 `beginModify()` 的别名打断逻辑保持对称。
- **误报排除**: 已通读 OrmEntitySet 全文（orm_clearDirty/orm_beginLoad/orm_unload/beginModify/orm_added 的别名语义）与 CascadeFlusher.cascadeCollection、OrmSessionImpl.reset/resetEntity，确认 flush 路径会建立别名且 orm_reset 无别名防护；已确认 test 中仅覆盖"未加载 proxy 集合 reset 保持 proxy"场景（TestOrmEntitySet），未覆盖"flush 后 reset"。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。`orm_reset()` 增加别名防护：`initialEntities == entities` 时当前集合即已保存基线，仅执行 `orm_clearDirty()` 直接返回，不再 clear+addAll（所有修改路径均先经 `beginModify()` 打断别名，别名态等价于无可恢复内容）。回归测试 `TestOrmEntitySetReset#testOrmResetAfterFlush`（红验证：stash 主代码后集合被清空，size 期望 2 实际 0）。

### [P2] flush 异常中断后实体的 orm_flushVisiting 标记残留，同 session 重试 flush 会静默跳过级联处理

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/session/CascadeFlusher.java:62-82`（配合 OrmSessionImpl.flush:203-221）
- **维度**: D1
- **证据**:
```java
public void execute() {
    sessionCache.forEachDirty(entity -> cascadeEntity(entity, false));  // 第一遍: 置 flushVisiting=true
    session.flushBatchLoadQueue();
    this.processWaitDeletes();
    this.flushing = true;
    sessionCache.forEachDirty(entity -> {
        entity.orm_flushVisiting(false);                                // 第二遍: 复位标记
        internalFlush(entity);
        ...
    });
    flushChanged();
    this.flushing = false;
}
```
- **现状**: `flushVisiting` 只在第二遍遍历或 `cascadeInternalFlush` 中复位。若 flush 在第一遍之后、第二遍完成之前抛出（最典型是 internalFlush → flushUpdate/flushDelete 触发 SQL 异常），第二遍中尚未访问到的实体其 `flushVisiting` 保持 true，且 flush() 的 catch 分支不做任何清理。
- **风险**: 在同一 session 上重试 flush（批量任务吞掉异常后继续）时，第一遍的 `cascadeEntity` 对这些实体直接 return（CascadeFlusher.java:207-208），级联逻辑被跳过：to-many 集合的孤儿删除（isOrphan → cascadeDeleteEntity）、级联删除、`flushCollectionChange` 都不再执行，但实体自身的 save/update/delete 仍会在第二遍执行。结果是"删除了子表关联但孤儿记录被留下"这类静默数据不一致。正常请求-响应流程中事务回滚后 session 会被丢弃，触发面有限，故定 P2。
- **建议**: 在 CascadeFlusher.execute() 用 try/finally 或在 OrmSessionImpl.flush() 的异常路径统一复位所有脏实体的 `orm_flushVisiting` 标记（例如 flush 失败时再执行一次 `cache.forEachCurrent(e -> e.orm_flushVisiting(false))`）。
- **误报排除**: 已 grep 全仓库 `flushVisiting` 的全部读写点（仅 OrmEntity 字段与 CascadeFlusher 4 处复位点），确认异常路径无任何复位；已读取 OrmSessionImpl.flush 的 catch/finally（只复位 flusher=null 和 sessionRevVersion）。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。`CascadeFlusher` 新增 `flushVisitingEntities` 跟踪本次 flush 标记过的实体，`execute()` 与 `execute(entity)` 均改为 try/finally，在 finally 中 `clearFlushVisiting()` 统一复位（顺带覆盖成功路径下第一遍标记、第二遍已非 dirty 实体的残留场景）。回归测试 `TestFlushFailure#testFlushFailureClearsFlushVisiting`：drop 表使装载队列在 flush 中途 SQL 失败，断言 `orm_flushVisiting()` 复位且同 session 重试 flush 成功（红验证：旧代码标记残留，断言 `expected false but was true`）；`#testFlushSuccess` 验证正常路径不变。

### [P2] DataBaseSchemaInitializer 用实例 @InjectValue setter 写入 static 字段，静态方法使用时可能 NPE 且多实例互相污染

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/initialize/DataBaseSchemaInitializer.java:39-44、79`
- **维度**: D1（兼 D7）
- **证据**:
```java
public static String[] specifyQuerySpaces;          // 静态可变字段

@InjectValue("@cfg:nop.orm.db-differ.auto-upgrade-database-specify-query-spaces|")
public void setSpecifyQuerySpaces(String[] specifyQuerySpaces) {
    this.specifyQuerySpaces = specifyQuerySpaces;   // 实例 setter 写静态字段
}

public static Map<String, List<IEntityModel>> splitByQuerySpace(...) {
    ...
    if(specifyQuerySpaces.length > 0 && ...){       // 静态字段未初始化时 NPE
```
- **现状**: `splitByQuerySpace` 是被外部直接调用的公开静态方法（nop-dbtool 的 `DataBaseUpgrader.upgrade()`，DataBaseUpgrader.java:55）。若进程中没有实例化 DataBaseSchemaInitializer bean（例如以代码方式直接构造 DataBaseUpgrader 执行升级），`specifyQuerySpaces` 为 null，`specifyQuerySpaces.length` 抛 NPE。同时多个 initializer 实例（不同配置）共享同一静态字段，配置互相覆盖。
- **风险**: dbtool/升级工具路径 NPE（错误信息无业务上下文，难排查）；同 JVM 多应用/多数据源场景配置串扰导致 auto-upgrade 的 querySpace 范围错误。
- **建议**: 把 `specifyQuerySpaces` 改为实例字段并让 splitByQuerySpace 接收参数；或在静态方法中做 `specifyQuerySpaces == null ? new String[0] : specifyQuerySpaces` 防御。
- **误报排除**: 已确认 DataBaseUpgrader（nop-dbtool-core）是 public 类且可脱离 IoC 直接 new（两个 public 构造器），其 upgrade() 直接调用静态 splitByQuerySpace；已确认仓库内无其他地方初始化该静态字段。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复（防御式）。`splitByQuerySpace` 对 `specifyQuerySpaces` 判空（取局部快照，避免遍历期间被实例 setter 并发改写）。不改公有静态方法签名：外部调用方 `nop-dbtool-core` 的 `DataBaseUpgrader.upgrade()` 直接调用该方法（该模块不在本单元处置范围，改签名会破坏其编译）。回归测试 `TestDataBaseSchemaInitializer#testSplitByQuerySpaceWithoutBeanInit`（红验证：旧代码 NPE `Cannot read the array length because specifyQuerySpaces is null`）+ `#testSplitByQuerySpaceWithSpecifyQuerySpaces`。注：多实例共享静态字段的配置串扰依旧存在，彻底解决需把配置改为方法参数并同步修改 nop-dbtool 调用方，属跨模块变更，未在本单元实施。

### [P2] MdxQuerySplitter.getSubQuery 对未知的 owner 别名抛 NPE 而非有意义的错误

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/mdx/MdxQuerySplitter.java:239-242`
- **维度**: D1
- **证据**:
```java
private MdxQueryBean getSubQuery(String alias, List<QueryFieldBean> fields, IEntityModel entityModel) {
    IEntityRelationModel rel = entityModel.getRelation(alias, false);   // 未找到时返回 null
    MdxQueryBean query = new MdxQueryBean();
    query.setSourceName(rel.getRefEntityName());                        // NPE
```
- **现状**: `split()` 中 `subFieldsMap` 残留的 owner 别名（既不在 query.getJoins() 配置中、也不是实体的关联属性名）会进入此方法；`getRelation(alias, false)` 不抛错返回 null，下一行直接 NPE。
- **风险**: `OrmTemplateImpl.findListByQuery/findFirstByQuery/existsByQuery` 是公开查询入口，QueryBean 的 fields 可来自外部请求（GraphQL 聚合查询）。调用方写错 owner 名（拼错或引用非关联属性）时得到的是无上下文的 NullPointerException，而不是指出哪个 alias/实体有问题的 NopException，排错成本高；上层若按"系统错误"兜底还会掩盖配置问题。
- **建议**: rel == null 时抛 `NopException(ERR_ORM_UNKNOWN_PROP/INVALID_OWNER)`，参数带上 alias 与 entityModel.getName()。
- **误报排除**: 已读取 split() 全文确认 subFieldsMap 的 key 来自 `field.getOwner()`（仅做了 checkOwnerName 的标识符校验，不校验是否为关联属性）；已确认 getRelation(name, false) 的可空语义（IEntityModel 接口 bThrowError=false）。

> **处置（fix-ai-check 分支，2026-08-25）**: 复查非问题（误报）。live code 中 `OrmEntityModel.getRelation(name, ignoreUnknown)` 的实际语义是：`ignoreUnknown=true` 才返回 null；`ignoreUnknown=false`（本处调用）在未找到时直接抛 `NopException(ERR_ORM_UNKNOWN_PROP)` 并携带 entityName+propName 上下文（见 OrmEntityModel.getRelation 实现）。报告对参数语义判断有误（不存在 bThrowError 参数）。全仓库 `IEntityModel` 的唯一实现是 OrmEntityModel（grep implements IEntityModel 仅一处），不存在返回 null 的实现路径，所述 NPE 不会发生。未改代码。

### [P2] SqlFiledRowMapper 将 computeExpr 字段值放入 null 键（getAsString 时丢失计算列）

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/sql_lib/SqlFiledRowMapper.java:40-53`
- **维度**: D1
- **证据**:
```java
for (SqlFieldModel field : sqlItemModel.getFields()) {
    IEvalFunction expr = field.getComputeExpr();
    if (expr != null) {
        Object value = expr.call2(null, mapOfColValues, sqlItemModel, scope);
        String as = field.getAs();
        if (as == null)
            as = field.getName();          // 已兜底为 name
        if (as.indexOf('.') > 0) {
            BeanTool.setComplexProperty(mapOfColValues, as, value);
        } else {
            mapOfColValues.put(field.getAs(), value);   // BUG: 应使用 as
        }
    }
}
```
- **现状**: 简单字段名（不含 '.'）且未配置 `as` 时，`field.getAs()` 为 null，计算结果被放入 null 键，按字段名读取永远取不到该计算列的值。
- **风险**: sql-lib 中声明 `<field name="x" computeExpr="..."/>`（不写 as）时，查询结果 Map 中该字段缺失（值挂在 null 键下），业务拿到 null，属于静默数据错误。
- **建议**: 改为 `mapOfColValues.put(as, value)`，并补充"computeExpr 无 as"的用例。
- **误报排除**: 已读取 _SqlFieldModel 确认 getAs 可空（生成代码，仅用于核实语义）；已确认 getColumnKey 分支对 as 的兜底写法正确，仅 computeExpr 写回分支用错变量。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。写回分支改用已兜底为 `field.getName()` 的局部变量 `as`。回归测试 `TestSqlFiledRowMapper#testComputeFieldWithoutAs`（红验证：旧代码计算结果放入 null 键，`map.get("fullA")` 为 null）+ `#testComputeFieldWithAs`（配置 as 时按 as 写入）。

### [P2] OrmFileComponent 与 OrmFileListComponent 在逻辑删除时的附件 detach 行为相互矛盾

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/component/OrmFileComponent.java:84-104` 与 `OrmFileListComponent.java`（onEntityDelete）
- **维度**: D8（兼 D1）
- **证据**:
```java
// OrmFileComponent.onEntityDelete:
public void onEntityDelete(boolean logicalDelete) {
    // 逻辑删除也自动删除附件
    //if (logicalDelete)
    //    return;
    ... fileStore.detachFile(fileId, bizObjName, entity.orm_idString(), propName);
}
// OrmFileListComponent.onEntityDelete:
public void onEntityDelete(boolean logicalDelete) {
    if (logicalDelete)
        return;                 // 逻辑删除时直接返回，不 detach
```
- **现状**: EntityPersisterImpl.delete 逻辑删除路径调用 `syncComponentWhenDelete(entity, true)`，两个组件对同一个 `logicalDelete=true` 语义给出相反处理：单文件组件总是 detach，文件列表组件不 detach。
- **风险**: 同一系统里"单文件字段"与"多文件字段"在逻辑删除后文件引用计数（fileStore attach/detach 账目）不一致：单文件实体的附件被解绑（可能触发孤儿文件清理），多文件实体的附件仍保持绑定。行为不可预期且与其中一个组件的注释意图必有一处违背。
- **建议**: 统一两个组件的逻辑删除语义（依据 OrmFileComponent 的注释"逻辑删除也自动删除附件"，应删除 OrmFileListComponent 的 early-return，或两者都保留绑定），并补充对应用例。
- **误报排除**: 已读取两个组件的 onEntityFlush/onEntityDelete 全文与 EntityPersisterImpl.delete/syncComponentWhenDelete 的调用参数（logicalDelete=true 仅出现在逻辑删除分支）。

> **处置（fix-ai-check 分支，2026-08-25）**: 裁定暂缓。决策点：两种统一方向都改变用户可见行为且有数据后果。(A) 按 `OrmFileComponent` 注释意图（"逻辑删除也自动删除附件"）删除 `OrmFileListComponent` 的 early-return——但 `DaoResourceFileStore.detachFile` 在文件为唯一引用时会物理删除文件（removeResource），将使多文件字段的逻辑删除不可恢复；(B) 反向统一为"逻辑删除保留附件绑定"——与 OrmFileComponent 中被注释掉的 early-return 的显式意图相悖。需要产品层面确认"逻辑删除与附件生命周期"的预期语义后统一两个组件并补对应用例。未改代码。

### [P2] DaoQueryHelper 多个 queryTo*Sql 把实体名/属性名未经校验拼入 EQL 文本，update 的 props key 直接进入 SET 子句

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/dao/DaoQueryHelper.java:262-274`（queryToUpdateSql；另见 73-88/240-252/254-260）
- **维度**: D5
- **证据**:
```java
public static SQL queryToUpdateSql(String entityName, QueryBean query, Map<String, Object> props) {
    SQL.SqlBuilder sb = newSQL(query);
    sb.update(entityName);                 // entityName 未校验
    sb.br().set(null, props);              // props 的 key 未经校验
    ...
}
// SQL.SqlBuilder.set (nop-core SQL.java:707-722):
//     owner(owner).append(name).param(value);   // name 原样拼入 SQL 文本
```
- **现状**: 同类中 `appendField/appendGroupBy/appendOrderBy` 都专门加了 `checkFieldName/checkOwnerName` 防御（注释明确"避免非法的owner/字段名被拼接到SQL中"），但 `queryToUpdateSql` 的 props key、`queryToSelectObjectSql/queryToCountSql/queryToDeleteSql` 的 entityName 没有同等校验。`OrmEntityDao.updateByQuery` 是公开 API，props 的 key 完全取决于调用方。
- **风险**: 若业务代码把用户可控的字段名 Map 透传给 updateByQuery（如动态批量更新），恶意 key（如 `"a=1 --"`）会被拼入 EQL 文本；虽然 EQL 编译器仍需解析通过，但 `--` 注释可吞掉参数占位符，存在注入防御缺口。实体名路径风险较低（EQL 编译要求实体真实存在），主要缺口在 SET 子句。
- **建议**: 在 queryToUpdateSql 中对 props 的每个 key 执行 `checkFieldName`（并对 entityName 执行 `checkEntityName`），与同文件其他拼装点对齐。
- **误报排除**: 已读取 SQL.SqlBuilder.set 实现（nop-core SQL.java:707-722）确认 key 原样 append；已确认 CrudBizModel.updateByQuery 的默认实现走实体更新而非该 SQL 路径（风险依赖自定义调用方），故定 P2 而非 P1。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。`queryToUpdateSql` 对 entityName 执行 `checkEntityName`、对 props 的每个 key 执行 `checkFieldName`，与同文件 appendField/appendOrderBy 的防御对齐。回归测试 `TestEntityDaoQuery#testQueryToUpdateSqlRejectsInvalidFieldName`（红验证：旧代码对恶意 key `"collegeName=1 --"` 不抛 `ERR_ORM_INVALID_FIELD_NAME`，assertThrows 失败；同时覆盖非法实体名 `ERR_ORM_INVALID_ENTITY_NAME` 与正常路径生成 `set collegeName?` 文本）。

### [P2] JdbcEntityPersistDriver 单实体/批量装载对非 eager 属性每次重新生成 SQL，热路径无缓存

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/driver/jdbc/JdbcEntityPersistDriver.java:122-142、180-214`
- **维度**: D6
- **证据**:
```java
// loadAsync:
EntitySQL loadSql = this.loadSql;
if (dialect != this.dialect || !loadSql.propIds.equals(propIds)) {
    loadSql = GenSqlHelper.genLoadSql(dialect, entityModel, binders, propIds);   // 每次重新生成，不回写缓存
}
// 对比 buildUpdateSql (271-283):
if (sql == null || dialect != this.dialect || !sql.propIds.equals(propIds)) {
    sql = GenSqlHelper.genUpdateSql(dialect, entityModel, binders, propIds);
    this.lastUpdateSql = sql;                                                    // update 有单槽缓存
}
```
- **现状**: 延迟加载单个 lazy 属性（`internalLoadProperty` → loadAsync，propIds=[该属性]）时，每个实体每次访问都触发 `genLoadSql` 重新构建 SQL 文本与 marker；批量路径 `batchLoadSqlPart` 同样不缓存。驱动实例是 per-entityModel 全局共享的，具备缓存条件（update 语句已用 lastUpdateSql 单槽缓存）。
- **风险**: 逐条访问延迟属性的场景（未走 batchLoad 队列的循环，如 N+1 回退路径）在热路径上反复进行字符串拼接与 marker 构造，浪费 CPU 与内存分配。
- **建议**: 仿照 lastUpdateSql 为 load/lock/batchLoad 各维护按 propIds 的单槽（或小型 Map）缓存；注意并发下使用 volatile 或 benign-race 模式（与 lastUpdateSql 相同）。
- **误报排除**: 已通读该驱动全部 SQL 生成/使用点，确认仅 update 有缓存；已确认 loadAsync 的调用频率场景（OrmSessionImpl.internalLoadProperty/_internalLoad 每次懒加载都会进入）。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复（纯性能优化，无行为差异，免红测试）。`loadSql`/`lockSql`/`batchLoadSqlPart` 字段改为 volatile 单槽缓存，propIds 不匹配时重新生成并回写（仅驱动默认方言回写，避免 shard 其他方言的 SQL 污染缓存槽），模式与既有 `lastUpdateSql` 一致。外部可观测行为不变，断言缓存命中需侵入驱动内部状态，故未构造红测试。

### [P3] CascadeFlusher.addChangeDuringFlush 去重判断差一（size() > 1 应为 size() > 0）

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/session/CascadeFlusher.java:56-59`
- **维度**: D1
- **证据**:
```java
if (changedDuringFlush.size() > 1 && changedDuringFlush.get(changedDuringFlush.size() - 1) == entity)
    return;
changedDuringFlush.add(entity);
```
- **现状**: 列表只有 1 个元素（size()==1）时跳过去重检查，连续两次添加同一实体会产生重复项；`> 1` 应为 `> 0`（或 `!isEmpty()`）。
- **风险**: 仅造成 flushChanged 中同一实体被重复 internalFlush（幂等，状态机保证第二次无动作），无数据危害。
- **建议**: 改为 `!changedDuringFlush.isEmpty() && changedDuringFlush.get(changedDuringFlush.size() - 1) == entity`。
- **误报排除**: 已读取 flushChanged 的消费逻辑确认重复项无实质危害，定级 P3。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复（与 P2-5 同文件同批修改）。去重条件 `size() > 1` 改为 `!isEmpty()`。免红测试理由：报告自认重复项幂等（第二次 internalFlush 无动作）、无数据危害，属边界条件对齐；构造用例需 mock IOrmSessionImplementor 全链路，成本与收益不匹配。

### [P3] OrmSessionImpl.internalAssemble 对 nopRevType 强制拆箱，列值为 null 时 NPE

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:399-403`
- **维度**: D1
- **证据**:
```java
if (entityModel.isUseRevision()) {
    byte revision = (Byte) entity.orm_propValue(entityModel.getNopRevTypePropId());
    if (revision == OrmConstants.REV_TYPE_DELETE) {
```
- **现状**: NOP_REV_TYPE 列在 OrmEntityModelInitializer 中未设置 mandatory（nop-orm-model OrmEntityModelInitializer.java:394），手工插入/历史数据该列为 null 时 `(Byte) null` 拆箱抛 NopException 之外的裸 NPE。
- **风险**: 仅当 revision 实体的 revType 列被外部写为 null 时触发，正常 ORM 写入不会出现。
- **建议**: 判空处理（null 视为非删除版本），或抛带实体上下文的 OrmException。
- **误报排除**: 已核对模型初始化代码确认该列无 mandatory 约束；已确认读取路径（loadCollection/batchLoad/readEntity 都经 internalAssemble）。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。`internalAssemble` 改为 `Byte` 接收并判空，null 按非删除版本处理。回归测试 `TestLoadRevisionEntity#testLoadRevisionEntityWithNullRevType`：测试夹具 app.orm.xml（test resources，非产品模型结构）新增 `test.entity.SimsRevision`（useRevision=true、DynamicOrmEntity、显式 nopRevExtChange 列），原生 SQL 插入 NOP_REV_TYPE 为 null 的行后经 ORM 装载（红验证：旧代码抛 NPE `Cannot invoke java.lang.Byte.byteValue()`）。

### [P3] OrmMappingTableMeta.getMappingPropEnDisplayName2 误读 NAME1 常量（复制粘贴）

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/support/OrmMappingTableMeta.java:111-118`
- **维度**: D1
- **证据**:
```java
public String getMappingPropEnDisplayName2() {
    String displayName = (String) mappingTable.prop_get(OrmModelConstants.ORM_MAPPING_PROP_EN_DISPLAY_NAME1);  // 应为 NAME2
    return displayName;
}
```
- **现状**: 方法名是 DisplayName2，读取的却是 prop1 的英文名配置。
- **风险**: 多对多元数据生成时 prop2 的英文名总是取到 prop1 的配置，产生错误的展示元数据；无运行时数据危害。
- **建议**: 改为 ORM_MAPPING_PROP_EN_DISPLAY_NAME2。
- **误报排除**: 已对照相邻的 getMappingPropEnDisplayName1 与其他 NAME1/NAME2 成对方法（getMappingPropDisplayName1/2）确认正确模式。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。常量改为 `ORM_MAPPING_PROP_EN_DISPLAY_NAME2`。回归测试 `TestOrmMappingTableMeta#testMappingPropEnDisplayName2`（红验证：旧代码返回 prop1 的配置 en-name-1，期望 en-name-2）。

### [P3] JdbcCollectionPersistDriver 的 Logger 使用了错误的类（JdbcEntityPersistDriver.class）

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/driver/jdbc/JdbcCollectionPersistDriver.java:41`
- **维度**: D1（复制粘贴）
- **证据**:
```java
static final Logger LOG = LoggerFactory.getLogger(JdbcEntityPersistDriver.class);
```
- **现状**: 集合驱动的日志（如 `orm.err_batch_load_collection_missing` 告警，第 137 行）会归到 JdbcEntityPersistDriver 名下。
- **风险**: 仅影响日志归类与排查体验。
- **建议**: 改为本类 class 字面量。
- **误报排除**: 直接代码可见，两处日志调用均使用该 LOG。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。LOG 改为本类 `JdbcCollectionPersistDriver.class` 字面量。免红测试理由：纯日志归类常量修正，无行为断言意义。

### [P3] SessionFactoryConfig.addInterceptor 在默认不可变列表上调用会抛 UnsupportedOperationException

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/SessionFactoryConfig.java:64、263-269`
- **维度**: D1
- **证据**:
```java
private List<IOrmInterceptor> interceptors = Collections.emptyList();   // 不可变
...
public void addInterceptor(IOrmInterceptor interceptor) {
    interceptors.add(interceptor);                                       // UnsupportedOperationException
}
```
- **现状**: 未先 setInterceptors 就调用 addInterceptor/removeInterceptor 会在运行时抛 UnsupportedOperationException。
- **风险**: 仅影响以代码方式配置该 Bean 的使用方；IoC XML 方式（setter 注入 List）不受影响。
- **建议**: 默认改为 `new ArrayList<>()`，或 add 时复制构建新列表（与 SessionFactoryImpl.addInterceptor 的安全写法一致）。
- **误报排除**: 已确认 Collections.emptyList() 不可变及 SessionFactoryImpl.addInterceptor 的复制式实现对比。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。`interceptors` 默认值改为 `new ArrayList<>()`，addInterceptor/removeInterceptor 可直接使用。回归测试 `TestSessionFactoryConfig#testAddInterceptorOnDefaultList`（红验证：旧代码抛 UnsupportedOperationException）。

### [P3] OrmCompositePk.parse 的 "null" 分支为死代码，最终以 IllegalArgumentException 失败而非业务异常

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/support/OrmCompositePk.java:173-198`（配合构造器 44-51）
- **维度**: D1/D4
- **证据**:
```java
String part = parts.get(i);
if (part.equals("null")) {
    propValues[i] = null;                  // 允许 null
} else { ... }
...
return new OrmCompositePk(entityModel.getPkColumnNames(), propValues);
// 构造器: for (Object propValue : propValues) { Guard.notEmpty(propValue, "pk value"); }  // null 抛 IllegalArgumentException
```
- **现状**: parse 显式支持 "null" 片段，但构造器 Guard.notEmpty 拒绝 null，结果是以 `IllegalArgumentException: IsEmpty:pk value` 失败，而非预期的 NopException/返回 null。
- **风险**: 对非法 id 字符串（含 "null" 片段）报错类型错误、无实体上下文；主键本不应为 null，行为可接受但分支具有误导性。
- **建议**: parse 中直接对 null 片段抛 ERR_ORM_INVALID_COMPOSITE_PK_PART 的 NopException，删除死分支；或改用 buildNotNull 语义。
- **误报排除**: 已核对 Guard.notEmpty(Object) 实现（ApiStringHelper.isEmptyObject 判空即抛）。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。`parse` 遇 "null" 片段直接抛 `ERR_ORM_INVALID_COMPOSITE_PK_PART`（携带 entityName/entityId/propName/value 上下文），删除死分支。回归测试 `OrmCompositePkTest#testParseRejectsNullPart`（红验证：旧代码以 `IllegalArgumentException: IsEmpty:pk value` 失败而非 NopException）+ 正常解析回归；测试类补充 CoreInitialization + app.orm.xml 模型装载。

### [P3] QueryPlanCacheKey 未包含 enableFilter 与 astTransformer，语句缓存键存在潜在碰撞

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/QueryPlanCacheKey.java:16-30`（配合 LoadedOrmModel.compileSql:89-122）
- **维度**: D8/D1
- **证据**:
```java
public QueryPlanCacheKey(String name, String sqlText, boolean disableLogicalDelete,
                         boolean allowUnderscoreName) { ... }   // 无 enableFilter / astTransformer
// LoadedOrmModel.compileSql: key 仅含上述4项，但编译上下文还依赖 enableFilter 与 astTransformer
```
- **现状**: 同一 name+sqlText 以不同 enableFilter 或不同 astTransformer 调用时命中同一缓存项，先编译的变体被后续复用。当前全仓库无 `enableFilter()` 调用方（grep 确认），astTransformer 各调用点均传默认值，故为潜在缺陷。
- **风险**: 未来任何调用方开始使用这两个参数时会出现语义错误的计划复用（filter 标记缺失/多余）。
- **建议**: 将 enableFilter 纳入 key；astTransformer 若存在非全局实例也应纳入（或文档化约束）。
- **误报排除**: 已 grep 全仓库确认 `.enableFilter()` 无生产调用方、astTransformer 传参一致，当前无现实触发路径，故定 P3。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。`QueryPlanCacheKey` 增加 `enableFilter` 字段（构造器/hashCode/equals），`LoadedOrmModel.compileSql` 构造 key 时传入；`astTransformer` 不纳入 key（无可靠 equals 语义），改为在 `ILoadedOrmModel.compileSql` javadoc 文档化约束（调用方必须保证同一 session factory 内 transformer 语义一致）。回归测试 `TestQueryPlanCacheKey#testEnableFilterParticipatesInKey`。免红验证说明：旧构造器无 enableFilter 参数，回归测试针对新增 API，在旧代码上无法编译（API 扩展型修复，红形态为编译期不兼容）。

### [P3] BatchActionQueueImpl.flushAsync 失败路径丢弃未执行动作且不回调 onFailure，delayTasks 仍照常执行

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/BatchActionQueueImpl.java:150-200`
- **维度**: D4/D2
- **证据**:
```java
if (FutureHelper.isError(future)) {
    bError = true;
    break;                       // 后续实体的动作不再执行
}
...
flushDelayTasks();               // 无论 bError 与否都执行
...
} finally {
    actionMap.clear();           // 未执行动作被清空，callback.onFailure 不会被调用
```
- **现状**: 某个实体模型的批量执行出错后，剩余模型的动作被直接清空，其回调（checkUpdateResult/persisterPostUpdate/evictGlobalCache 等）既不走 onSuccess 也不走 onFailure；同时 flushDelayTasks 在错误路径仍执行。
- **风险**: 与"flush 抛异常→事务回滚→session 丢弃"的常规路径叠加时影响有限（dirty 状态保留与回滚语义一致），但依赖 onFailure 做清理的扩展实现会漏触发；delay 任务在失败时执行可能产生多余的缓存失效。条件触发，定 P3。
- **建议**: 清空前对未执行动作调用 onFailure（携带已发生的异常），并将 flushDelayTasks 移到 !bError 分支内。
- **误报排除**: 已通读 flushAsync 全文与 IBatchAction 回调协议（onSuccess/onFailure），确认清空路径无失败通知；已确认调用方 SessionBatchActionQueue.flush 最终会因 waitAll 异常而抛出，事务回滚路径存在。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。(1) 未提交动作补发 onFailure：`notifyUnsubmittedFailure` 按 phase1FailIndex（正序中断点之后的模型）与 phase2FailIndex（逆序中断点之前且 isDependByOtherEntity 的 delete 动作）定位从未提交的动作并以收集到的错误回调；错误对象经每个 future 的 `whenComplete` 收集（`errorRef`），因 `FutureHelper.isError` 探测不到部分 future 实现的失败。(2) delayTasks 捕获后仅在 `waitAll` 整体成功且提交期无错误时执行——实证发现单个 rejected ResolvedPromise 经 waitAll 的 thenRun 包装后 whenComplete 收到 err=null（异常不透传），故采用双源判定。(3) 集合动作在整体失败时补发 onFailure（原路径 onSuccess/onFailure 均不触发）。新增错误码 `ERR_ORM_BATCH_FLUSH_ABORTED` 作为错误未决时的兜底。回归测试 `TestFlushFailure#testFlushFailureSkipsDelayTasks`（红验证：旧代码失败路径延迟任务照常执行，断言失败；并验证成功路径延迟任务正常执行）。未提交动作 onFailure 通知为防御性增强，需注入自定义 persister 才能构造场景，未单独建用例。

### [P3] OrmBatchLoadQueueImpl.flush 在等待异步完成前捕获 loadQueue，异步驱动下新增装载项可能滞留

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/loader/OrmBatchLoadQueueImpl.java:577-605`
- **维度**: D1
- **证据**:
```java
do {
    List<CompletionStage<?>> futures = new ArrayList<>();
    _flushCollection(queue, futures);
    _flushEntity(queue, futures);
    _flushFile(queue, futures);

    queue = this.loadQueue;          // 在 syncGet 之前捕获
    this.loadQueue = null;

    FutureHelper.syncGet(FutureHelper.waitAll(futures));
} while (queue != null);
```
- **现状**: thenRun 回调（enqueueSelection 等）在 future 完成时才执行；若驱动真正异步完成（在捕获之后才回调），新入队项留在 this.loadQueue 而循环已退出。当前 JDBC 驱动的 futureCall 同步执行（FutureHelper.futureCall 直接调用 task），回调在捕获前完成，故现网不触发。
- **风险**: 仅在使用异步驱动（自定义 IEntityPersistDriver 返回异步 future）时出现装载队列滞留；数据正确性由后续访问时的懒加载兜底，表现为额外查询。
- **建议**: 将 `queue = this.loadQueue; this.loadQueue = null;` 移到 syncGet 之后。
- **误报排除**: 已核对 FutureHelper.futureCall 实现（同步执行）确认 JDBC 路径安全；已分析 thenRun 挂接时序。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。`queue = this.loadQueue; this.loadQueue = null;` 移到 `syncGet` 之后，异步驱动的 thenRun 回调在 syncGet 期间新入队的装载项可被下一轮循环捕获。免红测试理由：触发需要自定义异步 IEntityPersistDriver（现网 JDBC 驱动 futureCall 同步执行、回调在捕获前完成，行为不变），构造该夹具需引入完整驱动装配链路，超出最小回归测试范畴。

## 附注（核对后排除的疑点，供后续审计参考）

1. 逻辑删除实体按主键 load 不带 delFlag=0 过滤（GenSqlHelper.genLoadSql/genEntityFilter）——经 TestLogicalDelete 用例确认是有意行为（getEntityById 可见已逻辑删除记录，delFlag=1）。
2. OrmEntity.orm_extDirty(false) 在 CascadeFlusher.execute 中对 nopRevExtChange 属性再次置 0→1 制造"幽灵脏标记"——经全链路追踪（queueUpdate 的 SQL 在 batch 执行期才用 orm_dirtyPropIds 构建、动作回调 persisterPostUpdate 会 orm_clearDirty、session 级 dirty 在 flush() 尾部复位）确认最终无残留、无多余 UPDATE，属当前时序下的冗余但无害写法。
3. CascadeFlusher._cascadeEntity 中 `refEntity.orm_state()` 的空指针疑虑——经 DynamicOrmEntity.orm_refLoaded 实现（refProps 为 null 即 false）确认 refLoaded==true 蕴含 refEntity 非空。
4. TenantOrmSessionEntityCache.forEachDirty 的快照遍历遗漏新租户缓存——经 CascadeFlusher 两遍遍历 + 级联递归（cascadeEntity 递归处理 internalSave 实体的关联）分析确认覆盖完整。

## 处置过程中的超范围新发现（fix-ai-check 分支，2026-08-25）

1. **`queryToFindPrevSql` 在 filter 为空时漏生成 `where` 关键字**（DaoQueryHelper，TestEntityDaoQuery 测试注释中已有记录的"独立缺陷"）：生成 `select o from X as o (o.pk < ?) order by ...` 非法 EQL。已随 P1-3 修复一并处理，`testFindPrevOrderByWithoutFilter` 覆盖。
2. **`FutureHelper.waitAll` 对单个 rejected `ResolvedPromise` 的 thenRun 包装不透传异常**（nop-api-core）：BatchActionQueueImpl 原失败探测（`FutureHelper.isError` + waitAll 结果）在该路径下全部失效。已在 P3-19 修复中以 errorRef 双源判定缓解；根因在 nop-api-core，未在本单元修改。
3. **`JdbcEntityPersistDriver.buildUpdateSql` 的 `lastUpdateSql` 单槽缓存在跨方言（shard querySpace）场景会用非默认方言生成的 SQL 覆盖缓存槽**，后续默认方言调用若 propIds 恰好匹配会复用错误方言的 SQL。不在本次 20 条清单内，本次新增的 load/lock/batchLoad 缓存已做方言守卫；lastUpdateSql 本身为控制 diff 范围未改动，留待后续处置。

