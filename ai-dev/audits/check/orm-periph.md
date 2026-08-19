# orm-periph 实现代码检查报告

- 检查日期: 2026-08-19
- 模块路径: nop-persistence/{nop-orm-model,nop-orm-drivers,nop-orm-pdm,nop-orm-rpc,nop-orm-data,nop-orm-geo}
- 文件数: 约 121（src/main/java）
- 覆盖范围声明:
  - `nop-orm-model`（91 文件）: 全部 25 个 `_gen/` 生成文件未逐行审读（按规范不应手改，仅按疑点抽查 `_OrmModel/_OrmEntityModel/_OrmToOneReferenceModel/_OrmReferenceModel` 的关键方法）；其余 66 个手写文件中，含逻辑的类（OrmEntityModel、OrmModel、OrmReferenceModel、OrmColumnModel、OrmComponentModel、OrmCompositePKModel、OrmUniqueKeyModel、OrmComputePropModel、OrmAliasModel、OrmJoinOnModel、OrmToMany/ToOneReferenceModel、拦截器模型、init/ 三个初始化器、loader/LazyLoadOrmModel、SchemaToOrmModel、utils）逐行通读；纯接口文件（I*Model 约 20 个）与常量/枚举仅浏览。
  - `nop-orm-drivers`（6 文件，全部在 nop-orm-tdengine，redis/es/mongo 子模块无 Java 源码）: 6/6 通读。
  - `nop-orm-pdm`（9 文件）: 9/9 通读，PdmModelParser（1274 行）全文阅读。
  - `nop-orm-rpc`（1 文件）: 通读。
  - `nop-orm-data`（1 文件）: 通读。
  - `nop-orm-geo`（13 文件）: 13/13 通读。
  - 为验证命中点，交叉查阅了范围外代码: nop-dao 的 `JdbcTemplateImpl`/`IDialect`/`SqlDataTypeMapping`/`IEntityPersistDriver`、nop-core 的 `SQL`/`XNode`/`AbstractResourceParser`、nop-api-core/nop-commons 的 `FutureHelper`/`Guard`/`StringHelper`（仅用于确认行为，未审计）。
  - 测试代码、target/、资源文件不在范围。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 7 |
| P2 | 7 |
| P3 | 8 |

说明: 本批模块（tdengine/rpc/data 驱动类）在仓库内均无 beans.xml 注册或调用点，属于"启用对应驱动/数据源才会触发"的库代码，故未定级 P0；但其中 TDengine SQL 生成、DaoEntityBlockingSource.take()、Rpc 保存失败静默等属于"一旦启用必然出错"的确定性缺陷，请优先处理。

## 发现列表

### [P1] TDengine 批量插入 SQL 生成完全错误：TAGS 取列模型对象当值、VALUES 恒为空

- **文件**: `nop-persistence/nop-orm-drivers/nop-orm-tdengine/src/main/java/io/nop/orm/tdengine/model/TdSqlHelper.java:92-122`
- **维度**: D1
- **证据**:
```java
private static void appendColValues(SQL.SqlBuilder sb, List<IColumnModel> cols, IOrmEntity entity) {
    for (int i = 0, n = cols.size(); i < n; i++) {
        if (i != 0)
            sb.append(',');
        Object value = cols.get(i);          // BUG: 取的是 IColumnModel，不是实体的属性值
        if (value instanceof Number) {       // IColumnModel 永远不是 Number，恒走 appendString
            sb.append(value);
        } else {
            appendString(sb, value);         // 生成 'OrmColumnModel[name=..,code=..]@..' 这样的垃圾字面量
        }
    }
}

public static void appendValues(SQL.SqlBuilder sb, IOrmEntity entity) {
    sb.append(" \nVALUES(");
    sb.append(")");                          // BUG: 从未写入实体的列值，恒生成空 VALUES()
}
```
- **现状**: `genInsertSubTableSql` 在存在 tag 列时通过 `appendColValues(sb, tableMeta.getTagCols(), entity)` 生成 TAGS 子句；`genBatchInsertSubTableSql` 对每个实体调用 `appendValues`。`TdEntityPersistDriver.batchExecuteAsync`（保存路径）调用两者。
- **风险**: 任何带 tag 列的 TDengine 实体保存都会生成非法 SQL（`INSERT INTO tb USING st (tags) TAGS ('<列模型toString>') VALUES()VALUES()...`）：当前必然执行失败；若仅修复 VALUES 部分，TAGS 值是列模型对象的 toString，属静默数据污染。
- **建议**: `appendColValues` 改为 `entity.orm_propValue(cols.get(i).getPropId())`；`appendValues` 按 dataCols 逐列生成值（timestamp + 数据列），多实体批次用 TDengine 的 `VALUES(...)(...)` 语法时需补齐分隔与列值。
- **误报排除**: 已确认 `appendColValues`/`appendValues` 被保存路径真实调用（TdEntityPersistDriver.java:124-131），非死代码；已对照 `OrmColumnModel.toString()` 确认生成内容。

### [P1] TDengine 单实体加载 SQL 缺少 FROM 子句；复合主键条件用逗号连接

- **文件**: `nop-persistence/nop-orm-drivers/nop-orm-tdengine/src/main/java/io/nop/orm/tdengine/model/TdSqlHelper.java:31-59`
- **维度**: D1
- **证据**:
```java
public static SQL.SqlBuilder genLoadSql(TdTableMeta tableMeta, IOrmEntity entity, IntArray propIds) {
    ...
    sb.select();
    for (...) { sb.append(col.getCode()); }   // select 列
    sb.where();                               // 直接 where，没有 sb.from() + 表名！

    List<? extends IColumnModel> pkCols = entityModel.getPkColumns();
    for (int i = 0, n = pkCols.size(); i < n; i++) {
        if (i != 0) {
            sb.append(',');                   // BUG: 复合主键条件之间应为 AND，却拼接 ','
        }
        sb.append(pkCol.getCode()).append('=');
        ...
```
- **现状**: `TdEntityPersistDriver.loadAsync`（懒加载/单实体装配路径）使用该 SQL。对照同文件 `genLoadSqlPart`（152-163 行）可见正确写法是 `sb.from(); table(sb, dialect, entityModel, null);`。已核对 nop-core `SQL.SqlBuilder`：`select()/where()/from()` 均只追加文本，`from()` 不会隐式触发。
- **风险**: 生成的 SQL 形如 `select C1,C2 where TS=..`，缺 FROM 表名，任何单实体加载都会因 SQL 语法错误失败；复合主键时额外产生 `A=1,B=2` 而非 `A=1 AND B=2`。
- **建议**: 补 `sb.from(); table(sb, dialect, entityModel, null);`（或按子表名加载）；主键条件分隔符改为 ` AND `。
- **误报排除**: 已比对批量加载路径 `genLoadSqlPart`（有 from）确认差异非 DSL 约定；`SQL.SqlBuilder.from()` 实现已核实。

### [P1] TDengine deleteByExample / countByExample 未设置 querySpace，删除/计数打到默认数据源

- **文件**: `nop-persistence/nop-orm-drivers/nop-orm-tdengine/src/main/java/io/nop/orm/tdengine/driver/TdEntityPersistDriver.java:222-229,246-253`
- **维度**: D1
- **证据**:
```java
public long deleteByExample(ShardSelection shard, IOrmEntity example, IOrmSessionImplementor session) {
    IDialect dialect = getDialect(shard);
    SQL.SqlBuilder sb = TdSqlHelper.genDeleteByExample(dialect, entityModel, binders, null, example);
    SQL sql = sb.end();                    // 缺 .querySpace(getQuerySpace(shard))
    return jdbc().executeUpdate(sql);
}
```
```java
public long countByExample(...) {
    ...
    SQL sql = sb.end();                    // 同样缺失
    return jdbc().findLong(sql, 0L);
}
```
- **现状**: 同类方法 `findPageByExample/findAllByExample/findFirstByExample/loadAsync`（78、198、213、237 行）都执行了 `sb.querySpace(getQuerySpace(shard))`，仅 delete/count 漏掉。
- **风险**: TDengine 实体通常配置独立的非默认 querySpace；缺失时 `JdbcTemplateImpl` 按 `sql.getQuerySpace()==null` 回退 DEFAULT_QUERY_SPACE（已核实 getQuerySpace 实现），删除会打到默认库——轻则报表不存在，重则默认库存在同名表时发生跨库误删。
- **建议**: 两处补 `.querySpace(getQuerySpace(shard))`。
- **误报排除**: 已核实 `SQL.getQuerySpace()` 与 `JdbcTemplateImpl.getQuerySpace(SQL)` 的回退逻辑，以及同文件其他方法的一致写法。

### [P1] TDengine batchExecuteAsync 静默丢弃 updateActions；updateByExample 恒返回 0

- **文件**: `nop-persistence/nop-orm-drivers/nop-orm-tdengine/src/main/java/io/nop/orm/tdengine/driver/TdEntityPersistDriver.java:117-143,256-258`
- **维度**: D1/D8
- **证据**:
```java
public CompletionStage<Void> batchExecuteAsync(boolean topoAsc, String querySpace,
        List<IBatchAction.EntitySaveAction> saveActions,
        List<IBatchAction.EntityUpdateAction> updateActions,   // 从未被使用
        List<IBatchAction.EntityDeleteAction> deleteActions, ...) {
    if (topoAsc) {
        if (saveActions != null) { ... }
    } else {
        if (deleteActions != null) { ... }
    }
    return null;
}
```
- **现状**: 接口 `IEntityPersistDriver.batchExecuteAsync` 契约（javadoc）声明 updateActions 可能为 null 且会被传入；本实现完全不处理更新动作。`updateByExample` 也直接 `return 0` 无任何日志。
- **风险**: 业务侧对 TDengine 实体执行 update（脏实体 flush 或按例更新）时无异常、无日志、无效果——静默数据丢失。
- **建议**: 若 TDengine 不支持 update（时间序列库常见约束），应抛 `UnsupportedOperationException`/带 ErrorCode 的 NopException 或至少 WARN 日志；支持则补齐实现。
- **误报排除**: 已通读整个实现类确认无 update 处理分支；已读接口 javadoc 确认 updateActions 是契约参数。

### [P1] DaoEntityBlockingSource.take()/takeMulti() 必然抛 IllegalArgumentException（maxWait=-1 未特判）

- **文件**: `nop-persistence/nop-orm-data/src/main/java/io/nop/orm/data/source/DaoEntityBlockingSource.java:119-122,144-152`
- **维度**: D1
- **证据**:
```java
public int takeMulti(Collection<? super T> items, int maxCount) throws InterruptedException {
    return drainTo(items, maxCount, -1L, -1L);      // maxWait = -1 表示无限等待
}
...
FutureHelper.waitUntil(() -> { ... }, maxWait, minWait <= 0 ? pollInterval : Math.min(pollInterval, minWait));
```
- **现状**: `FutureHelper.waitUntil(BooleanSupplier, long timeout, long)` 第一行 `Guard.positiveLong(timeout, "timeout")`（nop-api-core FutureHelper.java:502-504，Guard.java:100-104），对 `-1` 直接抛 `IllegalArgumentException("NonPositive:timeout,value=-1")`。因此 `take()`、`takeMulti()` 一次查询都不会执行就抛异常，"阻塞取一条"的核心 API 完全不可用。
- **风险**: 任何把该类用作 `IBlockingSource`（如队列消费 worker）的代码在调用 take 时立即崩溃；`poll(timeout>0)`、`drainTo` 不受影响，容易漏测。
- **建议**: 在 drainTo 中对 `maxWait < 0` 用循环等待实现无限等待语义（或先 `Guard.checkArgument` 后用 `Long.MAX_VALUE`），并在单测中覆盖 take()。
- **误报排除**: 已核实 FutureHelper.waitUntil 与 Guard.positiveLong 的确切实现及调用链 take→takeMulti→drainTo→waitUntil。

### [P1] RpcEntityPersistDriver.batchExecuteAsync 不检查响应，远端保存/删除失败被当成功

- **文件**: `nop-persistence/nop-orm-rpc/src/main/java/io/nop/orm/rpc/RpcEntityPersistDriver.java:282-309`
- **维度**: D4/D1
- **证据**:
```java
return invokeRpc(newEntityAction("batchModify"), args).thenApply(response -> null);   // response 未检查
...
return invokeRpc(newEntityAction("batchDelete"), args).thenApply(response -> null);   // 同上
```
- **现状**: 本类其他路径（loadAsync:263-264、findFirst:551、count:730 等）都先 `checkResponse(response)`（失败抛 `NopRebuildException.rebuild(response)`）；唯独批量写路径 batchModify/batchDelete 直接丢弃响应。仅当 RPC 传输层异常时才会失败。
- **风险**: 远端返回业务错误 ApiResponse（校验失败、乐观锁冲突等）时，本地 session 认为保存成功并提交，产生无任何信号的数据丢失。
- **建议**: 两处改为 `.thenAccept(response -> checkResponse(response))`（返回类型适配为 Void）。
- **误报排除**: 已通读全类，确认 checkResponse 存在且其余 8 个调用点均使用；仅这两处缺失。

### [P1] RpcEntityPersistDriver.batchLoadAsync 按位置绑定结果，不校验返回条数/顺序/ID

- **文件**: `nop-persistence/nop-orm-rpc/src/main/java/io/nop/orm/rpc/RpcEntityPersistDriver.java:350-364`
- **维度**: D1
- **证据**:
```java
List<Map<String, Object>> list = (List<Map<String, Object>>) response.getData();
if (list != null) {
    int i = 0;
    for (IOrmEntity entity : entities) {
        Map<String, Object> map = list.get(i++);        // 按位置取，不比对 id
        bindEntity(entity, map, propIds, session);
```
- **现状**: 假定远端 batchGet 返回列表与请求 ids 严格同序同长。远端少返回（实体不存在被过滤）时 `list.get(i++)` 抛 IndexOutOfBoundsException；远端顺序不同（如按主键排序返回）时把 A 的数据装进 B 实体——静默数据错绑。同仓库正确范式见 `TdEntityPersistDriver.batchLoadAsync`：先 `OrmAssembly.toIdMap(entities)` 再按返回的 id 匹配。
- **风险**: 跨服务批量加载在边界条件下崩溃或数据错绑，且无任何告警。
- **建议**: 请求时携带 id，从每条返回 map 中读 id 与本地实体匹配（复用 OrmAssembly.toIdMap 模式），未匹配实体 `session.markMissing`。
- **误报排除**: 已确认 bindEntity 直接用 map 值经 internalAssemble 写入实体，无 id 比对；已对照 TDengine 驱动的安全实现。

### [P2] H2GisInitializer 把 querySpace 写进 SQL 文本而非 querySpace 属性，H2GIS 总是加载到默认数据源

- **文件**: `nop-persistence/nop-orm-geo/src/main/java/io/nop/orm/geo/dialect/h2gis/H2GisInitializer.java:41-42`
- **维度**: D1
- **证据**:
```java
String dialect = entry.getValue();
String querySpace = entry.getKey();
if ("h2gis".equals(dialect)) {
    SQL sql = SQL.begin().sql(querySpace).sql("init").end();   // sql() 拼文本，querySpace 属性仍为 null
    jdbcTemplate.runWithConnection(sql, conn -> { H2GISFunctions.load(conn); ... });
```
- **现状**: 已核实 nop-core `SQL.SqlBuilder.sql(String)` 只追加文本（SQL.java:347-349），`querySpace(String)` 才设置属性（411-412）；`JdbcTemplateImpl.runWithConnection` 经 `getQuerySpace(sql)` 取属性，null 时回退 DEFAULT_QUERY_SPACE（JdbcTemplateImpl.java:158,196-203）。
- **风险**: 配置了多个 querySpace 且 h2gis 库不在默认空间时，`H2GISFunctions.load` 在错误的数据库连接上执行，H2GIS 函数在目标库不可用。
- **建议**: 改为 `SQL.begin().querySpace(querySpace).sql("init").end()`。
- **误报排除**: 已沿调用链核实 SQL 文本不会被当作语句执行（回调直接用 connection），错误只体现在连接选择上。

### [P2] Db2GeometryTypeHandler.setValue 未处理 null，写空几何值时 NPE

- **文件**: `nop-persistence/nop-orm-geo/src/main/java/io/nop/orm/geo/dialect/db2/Db2GeometryTypeHandler.java:30-35`
- **维度**: D1
- **证据**:
```java
@Override
public void setValue(IDataParameters params, int index, Object value) {
    final Geometry<?> geometry = toGeometry(value);      // value==null 时 geometry==null
    final Db2ClobEncoder encoder = new Db2ClobEncoder();
    String encoded = encoder.encode(geometry);           // geolatte 编码器对 null 大概率 NPE
    params.setObject(index, encoded);
}
```
- **现状**: 基类 `GeometryTypeHandler.setValue`（106-113 行）以及其他所有方言子类（Postgis:34-41、SqlServer:47-55、Oracle:55-63）都有 `if (value == null) params.setNull(index);` 分支，仅 DB2 覆盖时丢失。
- **风险**: DB2 上插入/更新 NULL 几何列时抛 NPE（或编码器底层异常），INSERT 语句整体失败。
- **建议**: 补 null 分支与其他方言对齐。
- **误报排除**: 已比对 4 个子类与基类的 setValue，确认唯 DB2 缺失；null 为合法数据库列值（可空 geometry 列）。

### [P2] GeometryObjectHelper 空字符串 WKT 生成包裹 null 的 GeolatteGeometry，延后 NPE

- **文件**: `nop-persistence/nop-orm-geo/src/main/java/io/nop/orm/geo/util/GeometryObjectHelper.java:25-28`
- **维度**: D1
- **证据**:
```java
if (value instanceof String) {
    Geometry<?> geo = GeoLatteHelper.decodeWktString(value.toString());  // 空串时返回 null（GeoLatteHelper.java:61-64）
    return new GeolatteGeometry(geo);                                    // 包一层 null，非 null 返回值
}
```
- **现状**: `decodeWktString` 对 null/空串返回 null，但这里仍然包装成非 null 的 GeolatteGeometry。后续 `toString()`（Wkt.toWkt(null)）或 `setValue` 编码时才 NPE，错误位置远离根因。
- **风险**: 前端传空字符串表示空几何是常见输入；转换结果不是 null 导致空值判断失效，并在序列化/落库时以难定位的 NPE 爆发。
- **建议**: `geo == null 时 return null`；非法 WKT 建议走 errorFactory 报转换错误。
- **误报排除**: 已核实 decodeWktString 的空串分支与 GeolatteGeometry.toString 的直接解引用。

### [P2] OrmModelInitializer.checkRefPrimary: to-one 关联无主键实体的 "id" 时 NPE

- **文件**: `nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/init/OrmModelInitializer.java:452-455`
- **维度**: D1
- **证据**:
```java
OrmColumnModel col = refEntityModel.getColumn(join.getRightProp());
if (col == null && OrmModelConstants.PROP_ID.equals(join.getRightProp())) {
    col = refEntityModel.getIdProp().isColumnModel() ? (OrmColumnModel) refEntityModel.getIdProp() : null;
}
```
- **现状**: `noPrimaryKey=true` 的实体（OrmEntityModelInitializer.initIdProp:260-262）`idProp` 为 null 且 props 中无 "id"；此处直接 `.isColumnModel()` 解引用。
- **风险**: orm.xml 中一个指向无主键只读实体、join rightProp="id" 的 to-one 关联，会使整个 OrmModel 初始化以裸 NPE 失败，无任何模型位置/名称上下文，排查成本高。
- **建议**: 先判 `getIdProp() != null`，否则抛带 `ERR_ORM_MODEL_REF_ENTITY_NO_PROP` + 实体名/属性名参数的 NopException。
- **误报排除**: 已核实 initIdProp 对 noPrimaryKey 实体的提前 return 路径（idProp 保持 null）。

### [P2] OrmModelInitializer.findJoinByRefCol 抛 bare IllegalStateException，违反错误处理规范且可被畸形模型触发

- **文件**: `nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/init/OrmModelInitializer.java:472-483,510-516`
- **维度**: D4/D7
- **证据**:
```java
for (IColumnModel col : refEntityModel.getPkColumns()) {
    ordered.add(findJoinByRefCol(ref.getJoin(), col));     // 找不到时：
}
...
private OrmJoinOnModel findJoinByRefCol(List<OrmJoinOnModel> join, IColumnModel refCol) {
    for (OrmJoinOnModel on : join) { ... }
    throw new IllegalStateException("invalid join prop");  // 无模型上下文、无 ErrorCode
}
```
- **现状**: 组合主键场景下，校验只比对 join 数量与主键列数（465-467 行），不校验"每个主键列恰好被引用一次"。若两个 join 条件都引用同一主键列（如 `a=PK1 and b=PK1`，PK2 未引用），重排序时 `findJoinByRefCol(PK2)` 落入该分支。
- **风险**: 畸形但可通过现有校验的 orm.xml 触发无上下文的 IllegalStateException，直接违背框架核心"两档错误处理 + NopException/ErrorCode"约定（AGENTS.md D7 依据）。
- **建议**: 改抛 `NopException(ERR_ORM_MODEL_JOIN_COLUMN_COUNT_LESS_THAN_PK_COLUMN_COUNT).source(ref).param(...)` 类带上下文异常；或在 465 行前校验 join 的 rightPropModel 与主键列一一对应。
- **误报排除**: 已推演触发路径（重复引用同一主键列通过 rightPropCount==pkColumns.size() 检查但未覆盖全部主键列），确认非常驻防御分支。

### [P2] LazyLoadOrmModel.putEntityModel 与 checkTopoEntryReady 存在竞态，新增实体可能在拓扑表中永久缺失

- **文件**: `nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/lazy/LazyLoadOrmModel.java:54-76,162-188`
- **维度**: D3
- **证据**:
```java
protected void checkTopoEntryReady() {
    if (this.topoEntryInited) return;
    synchronized (this.entityModelMap) {
        if (topoEntryInited) return;
        ... // 重建 topoEntryMap / sortedEntityModels
        topoEntryInited = true;             // (A)
    }
}
...
void putEntityModel(IEntityModel entityModel) {     // addEntityModel 调用，无锁
    ...
    this.topoEntryInited = false;                   // (B)
}
```
- **现状**: 时序 B(置 false) 发生在 A(置 true) 之前完成时，最终状态为 `topoEntryInited=true` 但 topoEntryMap/sortedEntityModels 不含新实体，且没有任何机制再触发重建（下次调用直接 return）。另外 `addEntityModel` 不加 `synchronized(entityModelMap)`，与 `getEntityModel` 的双重检查互不互斥，短名冲突检查也会漏判。
- **风险**: 动态注册实体（IDynamicEntityModelProvider 场景）后，`getEntityNames()/getEntityModelInTopoOrder()` 可能永远看不到该实体，批量保存拓扑排序出现"unknown entity name"或顺序错误；且问题随机出现难以复现。
- **建议**: putEntityModel 也在 `synchronized(entityModelMap)` 内执行，并在 checkTopoEntryReady 末尾于锁内再次确认没有并发插入；或用版本号/队列代替布尔标志。
- **误报排除**: 已核对两个方法的锁使用与 volatile 语义，确认 B 与 A 无 happens-before 约束。

### [P2] TdTableMeta.getSubTableName 将实体属性值直接拼接为 SQL 表名标识符

- **文件**: `nop-persistence/nop-orm-drivers/nop-orm-tdengine/src/main/java/io/nop/orm/tdengine/model/TdTableMeta.java:59-64`
- **维度**: D5
- **证据**:
```java
public String getSubTableName(IOrmEntity entity) {
    Object v = entity.orm_propValue(nbrCol.getPropId());   // 设备编号，业务数据
    if (v instanceof Number)
        return "dev" + v;
    return v.toString();                                    // 无任何合法性校验/转义
}
```
- **现状**: 返回值在 `TdSqlHelper.genInsertSubTableSql` 中 `sb.append(tableMeta.getSubTableName(entity))` 直接拼进 INSERT 语句（TdSqlHelper.java:71）。nbr 列的值来自实体数据（可由外部输入写入），未做标识符合法性校验。
- **风险**: 含空格/引号/分号的设备编号可注入或破坏 SQL（TDengine 参数化不支持表名，必须白名单校验）；即使无恶意，脏数据也会导致 SQL 语法错误。
- **建议**: 校验子表名满足 `^[A-Za-z0-9_]+$`（或方言标识符规则），非法时抛带上下文异常；超长截断按 TDengine 限制处理。
- **误报排除**: 已确认 genInsertSubTableSql 对该值零处理直接 append；TDengine 无表名绑定参数可用，校验是唯一手段。

### [P3] PdmModelParser MULTIPLE_STATE_PROP / MULTIPLE_VERSION_PROP 错误参数误用 labelProp

- **文件**: `nop-persistence/nop-orm-pdm/src/main/java/io/nop/orm/pdm/PdmModelParser.java:617-632`
- **维度**: D4
- **证据**:
```java
if (col.containsTag(STEREOTYPE_STATE)) {
    if (table.getStateProp() != null) {
        throw new NopException(ERR_ORM_MODEL_MULTIPLE_STATE_PROP).source(col)
                ... .param(ARG_OTHER_PROP_NAME, table.getLabelProp());   // 应为 getStateProp()
```
- **现状**: stateProp、versionProp 两个重复校验的 `ARG_OTHER_PROP_NAME` 都传 `table.getLabelProp()`（versionProp 块 629 行同错）。
- **风险**: 仅影响报错信息内容，误导排查（报"与显示属性冲突"实为状态/版本属性冲突）。
- **建议**: 分别改为 `table.getStateProp()` / `table.getVersionProp()`。
- **误报排除**: 已逐行比对 label(608-615)/state(617-624)/version(625-632) 三块，确认仅第一块参数正确。

### [P3] OrmModelInitializer.initRef 异常参数传模型对象而非名称

- **文件**: `nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/init/OrmModelInitializer.java:281-283`
- **维度**: D4
- **证据**:
```java
throw new NopException(ERR_ORM_UNKNOWN_PROP).param(ARG_ENTITY_NAME, refEntityModel.getName())
        .param(ARG_PROP_NAME, refPropName)
        .param(ARG_REF_NAME, ref);          // OrmReferenceModel 对象，应为 ref.getName()
```
- **现状**: 错误消息中 ref 参数会打印模型对象 toString。
- **风险**: 仅影响报错可读性。
- **建议**: 改为 `ref.getName()`。
- **误报排除**: 已确认 ARG_REF_NAME 在同文件其他用法均为字符串。

### [P3] orm-geo 多处裸异常 + 伪错误码字符串，未走 NopException/ErrorCode 体系

- **文件**: `nop-persistence/nop-orm-geo/src/main/java/io/nop/orm/geo/type/GeometryTypeHandler.java:136-138`; `.../dialect/postgis/PostgisGeometryTypeHandler.java:81`; `.../dialect/db2/Db2GeometryTypeHandler.java:48`; `.../dialect/sqlserver/SqlServerGeometryTypeHandler.java:41`
- **维度**: D4/D7
- **证据**:
```java
} else {
    throw new IllegalArgumentException();   // 无消息、无上下文（GeometryTypeHandler）
}
...
throw new IllegalStateException("nop.err.orm.invalid-object:" + object.getClass().getCanonicalName());  // Postgis
throw new IllegalStateException("nop.err.orm.invalid-geometry-value:" + object.getClass());            // Db2
throw new IllegalArgumentException("nop.err.orm.invalid-geometry-type");                                // SqlServer
```
- **现状**: "nop.err.orm.xxx" 形如错误码但未在任何 ErrorCode 资源中定义，也不经 NopException 携带 param。
- **风险**: 数据库返回非预期类型（驱动版本差异、hex WKT 变体等）时诊断信息贫乏，违背模块错误处理约定。
- **建议**: 统一 `throw new NopException(带ErrorCode).param(ARG_TYPE, object.getClass().getName())`。
- **误报排除**: 已检索模块内无对应错误码定义；基类 parseDbValue 的 catch 会 adapt 但不补充信息。

### [P3] OrmModelInitializer.buildDependsMap 为死代码，与 OrmModelTopEntryBuilder 重复

- **文件**: `nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/init/OrmModelInitializer.java:522-555`
- **维度**: D6（维护性）
- **证据**:
```java
private IDirectedGraph<IEntityModel, DefaultEdge<IEntityModel>> buildDependsMap(Collection<? extends IEntityModel> entityModels) {
    ... // 与 OrmModelTopEntryBuilder.buildDependsMap 逻辑近乎相同
}
```
- **现状**: 全文件唯一出现处是定义本身（grep 验证）；实际拓扑构建走 `initTopoMap() → OrmModelTopEntryBuilder`。
- **风险**: 两份近似实现后续修改时易只改其一（两者 entityMap 键构造已有细微差异：是否包含 simpleClassName）。
- **建议**: 删除 OrmModelInitializer 中的私有 buildDependsMap。
- **误报排除**: 已 grep 全仓库确认无反射/其他调用。

### [P3] PdmModelParser.parseKeys 对缺失 `<c:Key>` 的主键定义直接 NPE

- **文件**: `nop-persistence/nop-orm-pdm/src/main/java/io/nop/orm/pdm/PdmModelParser.java:1138-1141`
- **维度**: D1（解析边界）
- **证据**:
```java
XNode primaryKey = node.element(KEY_PRIMARY_NAME);
if (primaryKey != null) {
    primaryKeyId = primaryKey.element(KEY_NAME).attrText("Ref");   // element(KEY_NAME) 可能为 null
}
```
- **现状**: 手工裁剪/异常导出的 PDM 若 `<c:PrimaryKey>` 下无 `<c:Key>` 子节点，此处 NPE，报错无文件位置上下文（解析器其他错误均为 NopException）。
- **风险**: 仅影响畸形输入的报错质量。
- **建议**: 判空后走 validateFail/NopException（带 node.getLocation()）。
- **误报排除**: 已确认 XNode.element 对缺失子节点返回 null（与文件中其他判空用法一致）。

### [P3] TdMessageService 为空 stub：sendAsync/subscribe 返回 null

- **文件**: `nop-persistence/nop-orm-drivers/nop-orm-tdengine/src/main/java/io/nop/orm/tdengine/message/TdMessageService.java:18-28`
- **维度**: D8
- **证据**:
```java
public class TdMessageService implements IMessageService {
    @Override
    public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
        return null;                 // 调用方按非 null future 使用时会 NPE
    }
    @Override
    public IMessageSubscription subscribe(String topic, IMessageConsumer listener, MessageSubscribeOptions options) {
        return null;
    }
}
```
- **现状**: 仓库内无任何 beans.xml/代码注册或引用该类（grep 验证），属未完成实现。
- **风险**: 一旦被注册使用，所有消息发送路径 NPE；空 stub 违反接口契约（IMessageService 语义）。
- **建议**: 要么实现，要么抛 UnsupportedOperationException，或删除该类避免误用。
- **误报排除**: 已全仓库检索确认无引用点，故降为 P3。

### [P3] DaoEntityBlockingSource.drainTo 返回值/中断语义与 IBlockingSource 契约有偏差

- **文件**: `nop-persistence/nop-orm-data/src/main/java/io/nop/orm/data/source/DaoEntityBlockingSource.java:144-153`
- **维度**: D8
- **证据**:
```java
FutureHelper.waitUntil(() -> { ... }, maxWait, ...);   // 中断时 waitUntil 返回 false 且吞掉中断标志（仅复位）
return c.size();                                        // 含调用前集合中已有元素
```
- **现状**: 1) 返回值语义应为"本次转移的元素数"，`c.size()` 把调用者预置元素计入；2) 线程被中断时 waitUntil 静默返回 false，drainTo 正常返回，方法声明的 InterruptedException 实际永远不会抛出，中断信号丢失。
- **风险**: 上层依赖精确转移数或中断退出的逻辑行为异常（如限流计数偏大、无法优雅停机）。
- **建议**: 记录调用前 size 取差值；waitUntil 返回 false 后检查 `Thread.currentThread().isInterrupted()` 并主动抛 InterruptedException。
- **误报排除**: 已核实 FutureHelper.waitUntil 对 InterruptedException 的处理（复位并 return false，FutureHelper.java:506-517）。

### [P3] LazyLoadOrmModel 的表名索引大小写敏感，与静态 OrmModel 的 CaseInsensitiveMap 行为不一致

- **文件**: `nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/lazy/LazyLoadOrmModel.java:35,167` 对比 `.../init/OrmModelInitializer.java:79`
- **维度**: D8
- **证据**:
```java
// OrmModelInitializer
Map<String, IEntityModel> entityModelByTableMap = new CaseInsensitiveMap<>();
// LazyLoadOrmModel
private final Map<String, IEntityModel> entityModelByTableMap = new ConcurrentHashMap<>();  // 大小写敏感
```
- **现状**: 同一个 `getEntityModelByTableName` 查询，在静态合并模型与懒加载动态模型下的大小写匹配行为不同。
- **风险**: schema 对比、按表名路由等逻辑在两种模型包装下结果不一致，产生难以复现的偶发差异。
- **建议**: LazyLoadOrmModel 侧统一按 `tableName.toUpperCase(Locale.ROOT)` 归一化存取（ConcurrentHashMap 自行归一 key）。
- **误报排除**: 已比对两处 Map 的构造与读写点。

## 已排查未立项（代表性误报排除记录）

- **PdmModelParser.parseDataType 中 scale 取 `a:Precision`、precision 取 `a:Length`**（334-335 行）: 命名反直觉，但符合 PowerDesigner 对数值列 "Length=总位数、Precision=小数位数" 的语义，与 DECIMAL(p,s) 内联解析（341-347 行）结果一致，不判为 bug。
- **orm-model `propsByUnderscoreName` 大写键 put 覆盖无告警**（OrmEntityModelInitializer.java:141-145）: 冲突时已有 INFO 日志（原 case 键），纯大写重复在 camelCase 归一后同键，影响仅限查名兜底，不立项。
- **`OrmModelLoader.merge` 同名实体 notGenCode 覆盖 / `entityMap` 静默 put**: KeyedList 按 name 去重属合并 DSL 预期语义，同名表冲突未见消费方受损证据，不立项。
- **XXE**: 六模块均不直接构造 SAXParser/DocumentBuilder，PDM/orm 解析统一走平台 `XNodeParser`，未发现外部实体处理问题（解析器实现本身不在本审计范围）。
- **空 catch / printStackTrace / bare RuntimeException / 可变 static 集合 / `@Inject private` 字段 / Spring `@Value`**: grep 全量扫描均无命中（`@Inject` 均为 public setter 或 package-private 字段，符合 Nop IoC 约定）。
- **`OrmToManyReferenceModel.setIgnoreDepends/setRefSet` 空实现**: 拓扑构建只对 to-one 关系调用 ignoreDepends（TopEntryBuilder 在 isToOneRelation 分支内且强转 OrmToOneReferenceModel，其生成基类有真实字段），to-many 空实现当前不可达，不立项。
