# nop-orm 实现代码检查报告

- 检查日期: 2026-08-19
- 模块路径: nop-persistence/nop-orm
- 文件数: 152（src/main/java 实测；任务描述的 265 与实际不符，以实际为准，不含 target/ 与测试代码）
- 覆盖范围声明:
  - 全量通读（逐行）: session 包全部 5 个主类、persister 包全部主类（EntityPersisterImpl/CollectionPersisterImpl/BatchActionQueueImpl/OrmAssembly/LogicalDeleteHelper/OrmRevisionHelper/OrmTimestampHelper）、support 包核心（OrmEntity/OrmEntitySet/OrmEntityHelper）、driver/jdbc 两个 driver、loader 包（OrmBatchLoadQueueImpl/JdbcQueryExecutor）、OrmEntityDao、DaoQueryHelper、GenSqlHelper、GenSqlTransformer、OrmTemplateImpl、id/OrmEntityIdGenerator、component 包（Json/Xml/AbstractOrmComponent）、initialize 包、TenantOrmSessionEntityCache、StatelessOrmSessionEntityCache、PersistEnvBuilder、SqlLibInvoker、OrmEntityBuilder 等约 35 个文件（占模块总行数主体）。
  - 定向检查: 全模块 grep 扫描空 catch、bare RuntimeException、printStackTrace、synchronized、可变 static、private @Inject，逐一回读验证。
  - 跳过: `sql_lib/_gen/`（生成代码）、纯接口/常量/异常类、mdx/MdxQuerySplitter 仅抽查调用方。
  - 交叉验证: 对每个疑点追了调用链（含 nop-orm-eql、nop-orm-model、nop-dao、nop-biz 的调用方）与 git 历史、测试模型，未确认的一律未写入。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 1 |
| P1 | 5 |
| P2 | 7 |
| P3 | 7 |

## 发现列表

### [P0] 游标分页 findPageAndReturnCursor 无条件 remove：最后一页丢数据、空结果崩溃、hasPrev 恒 false

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/dao/OrmEntityDao.java:592-608`
- **维度**: D1
- **证据**:
```java
if (query.isFindPrev()) {
    list = findPrev(lastEntity, query.getFilter(), query.getOrderBy(), query.getLimit() + 1);
    list.remove(list.size() - 1);
    list = ListFunctions.reverse(list);
    hasPrev = list.size() > query.getLimit();
    hasNext = lastEntity != null;
} else {
    list = findNext(lastEntity, query.getFilter(), query.getOrderBy(), query.getLimit() + 1);
    hasNext = list.size() > query.getLimit();
    list.remove(list.size() - 1);
    hasPrev = lastEntity != null;
}
```
- **现状**: findNext/findPrev 以 `limit + 1` 取数用于探测是否有下一/上一页。两个分支都**无条件** `list.remove(list.size() - 1)`：
  1. 当实际结果不足 `limit+1` 条（最后一页，最常见场景）时，remove 会误删一条**有效数据**，该页少返回一条；
  2. 当 cursor 之后无任何记录时，`findPage` 返回空列表（已验证 `RowMapperAllExtractor.apply` 返回 `new ArrayList<>(0)`），`list.remove(-1)` 抛 `IndexOutOfBoundsException`；
  3. findPrev 分支中 `hasPrev` 在 remove 之后基于移除后的 size 判断，恒为 `false`（size 最多等于 limit），与 findNext 分支"先判断后 remove"的对称写法明显不一致。
- **风险**: 生产路径可达：`CrudBizModel.doFindPage`（nop-biz，GraphQL 分页查询带 cursor 时）直接调用本方法。每次游标分页翻到最后一页都会少返回一条记录；游标越过数据末尾时整个查询请求抛异常。
- **建议**: 仅当 `list.size() > query.getLimit()` 时才 remove 探测行；findPrev 分支将 hasPrev 判断移到 remove 之前；对空列表直接短路返回。
- **误报排除**: 已验证 `ISqlExecutor.findPage` → `RowMapperAllExtractor` 返回可变 ArrayList（空结果返回 `new ArrayList<>(0)` 而非 null），remove 语义如上所述成立；已确认 `CrudBizModel:349` 为生产调用方。

### [P1] to-one 关联 cascadeDelete 传入 owner 自身，级联删除完全失效

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/session/CascadeFlusher.java:265-273`
- **维度**: D1
- **证据**:
```java
} else if (entity.orm_refLoaded(propModel.getName())) {
    IOrmEntity refEntity = entity.orm_refEntity(propModel.getName());
    if (deleteProp) {
        cascadeDeleteEntity(entity, propModel.isAutoCascadeDelete());
    } else {
        if (refEntity.orm_state().isTransient()) {
            cascadeEntity(refEntity, false);
        }
    }
}
```
- **现状**: `deleteProp = deleting && propModel.isCascadeDelete()` 时应删除的是关联对象 `refEntity`（`cascadeDelete` 的模型语义为"删除主表时是否自动删除子表"，见 `_OrmReferenceModel` javadoc），但代码传入的是 owner 实体 `entity` 自身。此时 `entity` 的状态是 DELETING：`cascadeDeleteEntity` 内 `entity.orm_state().isGone()`（DELETING 属于 gone）跳过 `internalDelete`，随后 `cascadeEntity(entity, ...)` 因 `entity.orm_flushVisiting()` 已在入口置 true 而直接返回。整个调用是 no-op，`refEntity` 从未被删除。
- **风险**: 配置了 `cascadeDelete="true"` 的一对一（含 reverseDepends）关联，删除主实体时子表记录残留（孤儿数据）。测试模型 `base.orm.xml` 中存在该配置（`TestCompositeOneToOneMain.sub` 等），但 TestCascadeFlush 仅测试 1-1 保存，未覆盖删除，缺陷因此存活。
- **建议**: 改为 `cascadeDeleteEntity(refEntity, propModel.isAutoCascadeDelete())`，并补充 to-one 级联删除的回归测试。
- **误报排除**: 已核对 `IEntityRelationModel.isCascadeDelete` 的模型 javadoc、`OrmEntityState.isGone/isDeleting` 定义、`cascadeDeleteEntity` 全文及 `cascadeEntity` 的 flushVisiting 短路逻辑，确认 no-op 推导成立；to-many 分支（cascadeCollection）不受影响。

### [P1] OrmAssembly.readId 单列主键分支忽略 fromIndex，EQL 非首位实体表达式 id 错读

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/OrmAssembly.java:139-151`
- **维度**: D1
- **证据**:
```java
public static Object readId(Object[] values, int fromIndex, IEntityModel entityModel) {
    IEntityPropModel prop = entityModel.getIdProp();
    if (prop == null)
        return null;

    if (prop.isSingleColumn()) {
        return values[0];            // 忽略 fromIndex
    }
    int n = entityModel.getPkColumns().size();
    Object[] ids = new Object[n];
    System.arraycopy(values, fromIndex, ids, 0, n);   // 复合主键正确使用 fromIndex
    ...
}
```
- **现状**: 单列主键分支返回 `values[0]` 而非 `values[fromIndex]`。调用链 `EntityExprMeta.buildValue(row, fromIndex)` → `OrmSessionImpl.internalReadId` → 本方法。EQL select 多字段时（`JdbcQueryExecutor.transformRow` 按 index 递增传 fromIndex），实体表达式若不在首位，读到的 id 是第一列的值。
- **风险**: `select o.xxx, o from Entity o` 这类实体表达式非首位的 EQL，会以错误 id 调用 `internalMakeEntity`，把整行属性装配到错误 id 的实体上并写入 session 一级缓存，造成实体张冠李戴（数据错误，甚至可能以错误主键更新数据库）。现有测试全部是 `select o, ...`（实体在首位）故未暴露。
- **建议**: 改为 `return values[fromIndex];`，并补充"实体表达式位于非首位"的 EQL 回归测试。
- **误报排除**: 已验证 `eagerLoadProps` 主键列排在最前（OrmEntityModelInitializer.initProps 明确保证），复合分支的 fromIndex 语义正确；同类的 `SingleColumnExprMeta`/`CompositePkExprMeta`/`EntityRefPropExprMeta.buildValue` 均正确使用 fromIndex，仅本方法遗漏；EQL 编译器不重排 select 项。

### [P1] EntityPersisterImpl.batchLoadAsync 丢弃真实 future 并返回 voidPromise，批量加载错误被吞

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/EntityPersisterImpl.java:159-183`
- **维度**: D4（兼 D1/D3）
- **证据**:
```java
CompletionStage<Void> future = FutureHelper.voidPromise();
if (entityModel.getShardPropId() <= 0) {
    future = this._batchLoad(null, toLoad, propIds, subSelection, session);
} else {
    for (...) { future = FutureHelper.bothSuccess(future, shardFuture); }
}

if (useGlobalCache) {
    future.thenRun(() -> { ... });   // 返回值亦被丢弃
}

return FutureHelper.voidPromise();   // 丢弃 future，恒返回已完成 promise
```
- **现状**: 方法末尾无条件返回新的已完成 promise，真正的加载 future（含异常）被丢弃。`FutureHelper.futureCall`（JDBC driver 使用）会同步执行并把异常捕获进 rejected future——该异常随之丢失。调用方 `OrmSessionImpl._internalLoad` 的 `syncGet` 与 `OrmBatchLoadQueueImpl.flush` 的 `waitAll` 都只会看到成功的 voidPromise。
- **风险**: 多实体批量加载（size>1）时数据库故障（连接失败、约束冲突等）被完全静默：实体保持 PROXY 状态但调用方认为加载成功，后续读取再次触发加载或产生 NPE，错误现场丢失极难排查；对真正异步的 driver（如 rpc）还会造成数据竞争。对比同文件 `loadAsync`（单实体）正确返回 future，此处明显是笔误。
- **建议**: `return future;`；全局缓存更新也应纳入返回的 future 链。
- **误报排除**: 已验证 `FutureHelper.futureCall` 同步执行并 `reject(t)` 捕获异常、`JdbcEntityPersistDriver.batchLoadAsync` 使用 futureCall；已核对 `OrmBatchLoadQueueImpl._flushEntity` 对返回 future 的 syncGet 无兜底异常处理路径。

### [P1] CollectionPersisterImpl 集合全局缓存失效条件写反（err != null 才 evict），变更成功后缓存陈旧

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/CollectionPersisterImpl.java:171-181`
- **维度**: D1
- **证据**:
```java
IBatchAction.CollectionBatchAction action = new IBatchAction.CollectionBatchAction(collection, shard,
        (ret, err) -> {
            if (err != null) {
                evictGlobalCache(shard, collection);
            }
        });
```
- **现状**: 对比同文件实体路径 `EntityPersisterImpl.queueSave/queueUpdate/queueDelete` 的回调均为 `if (err == null) { evictGlobalCache(...); ... }`（成功后失效缓存），集合路径写成了 `err != null`。且 `onFailure` 在现有代码中无人调用（BatchActionQueueImpl 只对 collectionActions 调 `onSuccess`，JdbcCollectionPersistDriver.flushCollectionChange 为空实现），因此 evict 分支实际不可达。
- **风险**: 配置了集合 `useGlobalCache` 的 to-many 关联，元素增删提交成功后 owner 的 elementIds 缓存不失效，后续 loadCollection 命中旧缓存，返回增删之前的集合内容（读到已删除元素/看不到新增元素），缓存与数据库长期不一致。
- **建议**: 改为 `if (err == null) evictGlobalCache(shard, collection);` 与实体路径对齐。
- **误报排除**: 已核对 `IBatchAction.CollectionBatchAction.onSuccess/onFailure` 的回调约定（err 参数传递）、`BatchActionQueueImpl.flushAsync` 中 `action.onSuccess(null)` 的调用位置、以及 elementIds 缓存的读写路径（仅 loadFromGlobalCache/updateGlobalCache/evictGlobalCache 三处），确认成功路径无任何失效手段。

### [P1] JsonOrmComponent 读取 null 列后 flush 将字符串 "null" 写回，数据污染

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/component/JsonOrmComponent.java:55-62, 147-153`
- **维度**: D1
- **证据**:
```java
public Object get_jsonValue() {
    Object value = jsonValue;
    if (value == NOT_INITED) {
        String text = get_jsonText();
        value = jsonValue = JsonTool.parseBeanFromText(text, Object.class);  // DB 为 null 时缓存 null
    }
    return value;
}

public void flushToEntity() {
    if (jsonValue != NOT_INITED) {            // 读取过的 null 也满足条件
        String jsonText = JsonTool.stringify(jsonValue);   // stringify(null) == "null"
        internalSetPropValue(PROP_NAME__jsonText, jsonText);
    }
}
```
- **现状**: 懒加载把 `jsonValue` 缓存为 null 后，`jsonValue != NOT_INITED` 恒成立。实体参与 flush 时（`CascadeFlusher.flushComponent` → `orm_flushComponent` → `onEntityFlush` → `flushToEntity`，对 needFlush 组件**无条件**调用，不要求组件被修改过），`stringify(null)` 产生字符串 `"null"` 写回列；`internalSetPropValue` 走 `orm_propValue` → `markPropDirty`（null ≠ "null"）还会把实体标脏，生成多余的 UPDATE。
- **风险**: json 组件列原本为 null 的记录，只要业务读取过该组件且实体因其他原因参与 flush，列值就被污染为字符串 `"null"`，破坏下游 JSON 解析语义。对比同目录 `XmlOrmComponent.flushToEntity` 用 `node != null` 判断（null 不写回），本类处理明显不当。
- **建议**: 引入独立的 dirty 标志（仅 `set_jsonValue` 置位）或改为 `if (jsonValue != NOT_INITED && jsonValue != null)`。
- **误报排除**: 已验证 `IOrmComponent.onEntityFlush` 默认调用 `flushToEntity`、`OrmEntity.orm_flushComponent` 对 needFlush 组件无条件调用、`JsonTool.stringify` 委托 Jackson（writeValueAsString(null) 返回 "null"）、`markPropDirty` 对 null→"null" 判定为变更。

### [P2] 全局缓存 getToLoad 的缓存 key 错位：多租户实体批量加载缓存恒 miss

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/EntityPersisterImpl.java:591-619`；`nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/CollectionPersisterImpl.java:263-302`
- **维度**: D6（兼 D1）
- **证据**:
```java
// 写入/查询 keys 用 getCacheKey
List<String> keys = new ArrayList<>(entities.size());
for (IOrmEntity entity : ret) {
    keys.add(getCacheKey(entity));       // useTenantCache 时为 "tenantId:" + id
}
Map<String, Object> values = globalCache.getAll(keys);
...
// 取值却用 orm_idString()
Object value = values.get(entity.orm_idString());   // 纯 id 字符串，无 tenant 前缀
```
- **现状**: `getCacheKey` 在 `useTenantCache=true` 时返回 `tenantId + ":" + id`，而回查 map 时用 `orm_idString()`（已验证为 `StringHelper.toString(get_id())`，无租户前缀）。两个类（实体与集合的 getToLoad）存在同样的错位。
- **风险**: 多租户 + 全局缓存组合下，批量加载的缓存命中检查永远 miss，每个实体都回源数据库，全局缓存形同虚设（性能退化）；`getAll` 白白执行。非租户实体不受影响。
- **建议**: 回查统一使用 `values.get(getCacheKey(entity))`。
- **误报排除**: 已验证 `orm_idString` 的默认实现、`getCacheKey` 的两处实现、以及 `useTenantCache` 的判定条件（`isUseTenant() && !isGlobalUniqueId()`），确认错位仅在租户缓存场景发生。

### [P2] TenantOrmSessionEntityCache 遍历租户缓存时新增租户缓存导致 ConcurrentModificationException

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/session/TenantOrmSessionEntityCache.java:154-175`
- **维度**: D3
- **证据**:
```java
public void forEachDirty(Consumer<IOrmEntity> processor) {
    sharedCache.forEachDirty(processor);
    for (OrmSessionEntityCache cache : caches.values()) {   // 迭代 HashMap.values()
        cache.forEachDirty(processor);
    }
}
```
- **现状**: processor（如 CascadeFlusher 的 cascadeEntity → `session.internalSave` → `cache.add` → `makeTenantCache`）在遍历过程中保存了某尚未建缓存租户的新实体时，会 `caches.put(...)` 新建条目，外层 `for (caches.values())` 抛 CME。`forEachCurrent` 两个重载同样结构。
- **风险**: flush 期间（含 preSave/interceptor 回调）跨租户新建实体（后台任务遍历多租户数据并保存）触发 CME，事务中断。单租户场景不受影响。
- **建议**: 迭代前复制 `new ArrayList<>(caches.values())`，或新租户缓存延迟到遍历结束后注册。
- **误报排除**: 已验证 `makeTenantCache` 的 lazy put 逻辑与 `internalSave → cache.add` 调用链；HashMap.values() 迭代中 put 新 key 会触发 modCount 变化抛 CME。

### [P2] OrmSessionEntityCache.removeAll 在 visiting 时直接 clear 正在被遍历的主缓存

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionEntityCache.java:229-240`
- **维度**: D3
- **证据**:
```java
public void removeAll(String entityName) {
    if (this.visiting) {
        if (this.tempEntityCaches != null) {
            tempEntityCaches.remove(entityName);
        }
    }

    EntityCache cache = this.entityCaches.get(entityName);
    if (cache != null)
        cache.clear();      // 无条件直接清空主缓存（含正在被 forEach 遍历的 cache）
}
```
- **现状**: visiting 机制为 add/remove（单个实体）提供了 temp 缓冲，但 `removeAll`（`session.evictAll` 入口）仍然直接 `cache.clear()` 清空 `idToEntities`。若此时正处于 `forEachCurrent/forEachDirty` 对该 entityName 的 LinkedHashMap 迭代中，抛 ConcurrentModificationException；即使不抛（遍历其他实体类型），被 detach 的实体与遍历状态也不一致。
- **风险**: flush/load 回调（postLoad、interceptor）中调用 `session.evictAll(entityName)` 时崩溃。依赖业务回调写法，属特定条件触发。
- **建议**: visiting 时将 removeAll 记录到 temp 结构（如按 entityName 记录待清除集合），循环收敛后统一处理。
- **误报排除**: 已验证 `forEachCurrent` 直接迭代 `cache.entities()`（LinkedHashMap.values() 活视图）且 `EntityCache.clear()` 会 `idToEntities.clear()`，clear 与并发迭代组合必然 CME。

### [P2] OrmEntityIdGenerator.initTenantId 传错变量：租户主键列不会初始化为当前租户

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/id/OrmEntityIdGenerator.java:113-114`
- **维度**: D1
- **证据**:
```java
String tenantId = (String) OrmEntityHelper.getPropValue(col, entity);
if (StringHelper.isEmpty(tenantId)) {
    OrmEntityHelper.setPropValue(col, entity, tenantId);   // 应传 current（当前租户），却传入空的 tenantId 自身
}
```
- **现状**: 租户列为空时应初始化为 `current`（上下文租户），实际把空值原样写回（null 时 markPropDirty 判定值未变化，等于没有赋值）。本方法仅在"租户列是主键的一部分"（tenant-in-PK）时被 `generateId` 调用。
- **风险**: 使用 tenant 作为联合主键的实体，新建时主键的租户分量缺失 → `orm_hasId()` 为 false → `EntityCache.add` 的 `Guard.notNull(entity.get_id())` 抛出晦涩的 "entity._id" 异常。当前仓库的 orm 模型未使用 tenant-in-PK 配置（nopTenantId 均带 defaultValue 且非 primary），故未暴露；但平台明确支持该建模（`isGlobalUniqueId` 分支即为此设计）。
- **建议**: 改为 `OrmEntityHelper.setPropValue(col, entity, current);` 并补充 tenant-in-PK 实体的主键生成测试。
- **误报排除**: 已验证 `setPropValue` 对空值的实际效果、`generateId` 中 tenantPropId 分支无后续兜底检查、以及仓库现有 orm 模型中租户列的配置情况。

### [P2] OrmEntity.orm_requireEntity 用加载前的旧状态判断 isGone，违背接口契约

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/support/OrmEntity.java:738-747`
- **维度**: D8（兼 D1）
- **证据**:
```java
public <T extends IOrmEntity> T orm_requireEntity() {
    OrmEntityState state = orm_state();
    if (state.isProxy())
        orm_enhancer().internalLoad(this);

    if (state.isGone())        // 仍是加载前的 PROXY，恒非 gone
        throw new UnknownEntityException(get_entityName(), get_id());
    return (T) this;
}
```
- **现状**: `IOrmEntity.orm_requireEntity` 的契约是"如果是 proxy 状态，则强制加载实体。如果加载后发现实体不存在，则抛出异常"。实现用加载前捕获的局部变量 `state`（PROXY）判断，加载后即使实体被标记 MISSING 也不抛异常，返回未装配的实体。
- **风险**: 调用方（业务代码公共 API）在记录不存在时拿到 MISSING 状态实体而非异常，后续属性访问行为不可预期。当前模块内无调用方，属公共 API 契约违背。
- **建议**: 加载后重新读取 `orm_state()` 再判断（`internalLoad` 之后改用 `orm_state().isGone()`）。
- **误报排除**: 已验证 `internalLoad` 失败路径会调用 `markMissing` 将状态置为 MISSING、接口 javadoc 原文、以及全仓库无其他调用方。

### [P2] OrmEntitySet.orm_reset 将未加载的 proxy 集合固化为"空已加载"集合

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/support/OrmEntitySet.java:351-358`（配合 339-343、638-644）
- **维度**: D1
- **证据**:
```java
public void orm_reset() {
    this.entities.clear();
    if (this.initialEntities != null) {
        this.entities.addAll(initialEntities);
    }
    this.orm_clearDirty();      // 内部执行 this.initialEntities = this.entities;（非 null）
}

public boolean orm_proxy() {
    return proxy && initialEntities == null;   // reset 后 initialEntities 非 null → 恒 false
}
```
- **现状**: proxy 集合（从未加载，initialEntities == null）经 `orm_reset` 后 `initialEntities` 被赋为空 entities 引用，`orm_proxy()` 从此返回 false，集合被视为"已加载的空集合"。`OrmSessionImpl.reset`（契约："将所有新增记录删除，所有修改恢复到修改前，所有删除取消"）对每个实体的 to-many 集合调用 orm_reset，未加载集合的懒加载能力被静默破坏。
- **风险**: 调用 `session.reset()`（如事务回滚后的状态恢复）之后，访问原本未加载的集合得到空集而不是触发数据库加载，业务读不到数据。`reset()` 当前无框架内部调用方，属公共 API 上的数据正确性缺陷。
- **建议**: `orm_reset` 开头对 `orm_proxy()` 为 true 的集合直接 return（proxy 无需恢复）。
- **误报排除**: 已验证 `_makeProxy` 中 `refSet.orm_proxy(true)` 置 initialEntities=null、`orm_clearDirty` 的赋值语句、以及 `OrmSessionImpl.resetEntity` 的无条件 `pc.orm_reset()` 调用链。

### [P2] OrmTimestampHelper.onUpdate 的 creater 分支条件反转，审计字段记成系统用户

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/OrmTimestampHelper.java:81-94`
- **维度**: D1
- **证据**:
```java
if (entityModel.getCreaterPropId() > 0) {
    if (entity.orm_propValue(entityModel.getCreaterPropId()) == null) {
        String user = getCurrentUser();
        if (user != null) {
            user = CFG_ORM_SYS_USER_NAME.get();    // 有当前用户时反而覆盖为系统用户
        }
        entity.orm_propValue(entityModel.getCreaterPropId(), user);
    }
}

if (entityModel.getCreaterPropId() > 0) {          // 误用 creater 条件，应为 createTimePropId
    if (entity.orm_propValue(entityModel.getCreateTimePropId()) == null)
        entity.orm_propValue(entityModel.getCreateTimePropId(), current);
}
```
- **现状**: 与同函数 updater 分支（98-104 行：有用户用用户、无用户用 sys 兜底）及 `onCreate`（37-56 行）的对称逻辑相反，creater 分支在**有**当前用户时把它覆盖为系统用户名；`user == null` 时反而把 null 写回（无效操作）。另外第二个 if 用 `getCreaterPropId() > 0` 判断却操作 createTime 字段，条件误用。
- **风险**: creater 为 null 的历史记录被更新时，createdBy 补填为系统用户而非实际操作者，审计字段失真；只配置 createTime 未配置 creater 的模型，update 时 createTime 不会被补填。
- **建议**: 改为 `if (user == null) user = CFG_ORM_SYS_USER_NAME.get();`；第二个 if 改用 `getCreateTimePropId() > 0`。
- **误报排除**: 已对照 onCreate 与 updater 分支的正确模式，三处逻辑不对称明确指向笔误。

### [P2] OrmRevisionHelper.newError 忽略传入的 errorCode，恒报"非当前版本"

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/persister/OrmRevisionHelper.java:80-86`
- **维度**: D4
- **证据**:
```java
static NopException newError(IEntityModel entityModel, ErrorCode errorCode, IOrmEntity entity) {
    Object beginVer = -1L;
    if (entityModel.getNopRevBeginVerPropId() > 0) {
        beginVer = entity.orm_propValue(entityModel.getNopRevBeginVerPropId());
    }
    return OrmException.newError(ERR_ORM_ENTITY_NOT_CURRENT_REVISION, entity).param(ARG_REV_BEGIN_VER, beginVer);
}
```
- **现状**: 方法签名接收 `errorCode` 参数但从未使用，恒使用 `ERR_ORM_ENTITY_NOT_CURRENT_REVISION`。三个调用点分别传入 `ERR_ORM_ENTITY_ALREADY_EXISTS`、`ERR_ORM_ENTITY_REV_VER_IS_LESS_THAN_HIS_VER` 等，全部被吞。
- **风险**: revision 机制下的保存冲突（实体已删除仍保存）与版本冲突被统一报成"非当前版本"，误导排障与上游错误分类处理。
- **建议**: 改为 `OrmException.newError(errorCode, entity)`。
- **误报排除**: 已核对全部调用点传入的 errorCode 与 OrmErrors 中的常量定义。

### [P3] OrmBatchLoadQueueImpl.isEmpty() 返回值语义颠倒

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/loader/OrmBatchLoadQueueImpl.java:628-630`
- **维度**: D8
- **证据**:
```java
@Override
public boolean isEmpty() {
    return loadQueue != null;    // 队列存在（非空）返回 true，与方法名相反
}
```
- **现状**: `IOrmBatchLoadQueue.isEmpty()` 应在无待加载对象时返回 true，实现却返回"队列已创建"。当前全仓库无调用方（含 `afterFlush` 用 `loadQueue == null` 自行判断），一旦被使用即得反向结果。
- **风险**: 潜在的 API 契约陷阱。
- **建议**: 改为 `return loadQueue == null;`。
- **误报排除**: 已搜索全仓库确认无调用方，属潜伏缺陷。

### [P3] OrmEntityHelper.getEntityChange 双重笔误且恒返回空（死代码）

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/support/OrmEntityHelper.java:274-286`
- **维度**: D1
- **证据**:
```java
public static List<List<Object>> getEntityChange(IOrmEntity entity) {
    if (entity.orm_dirty())
        return Collections.emptyList();     // (a) 条件反：脏实体直接返回空
    List<List<Object>> ret = new ArrayList<>();
    entity.orm_forEachDirtyProp((oldValue, propId) -> {
        List<Object> change = new ArrayList<>(3);
        change.add(propId);
        change.add(oldValue);
        change.add(entity.orm_propValue(propId));
        // (b) 缺少 ret.add(change)
    });
    return ret;
}
```
- **现状**: javadoc 声称返回 `[[propId,oldValue,newValue]]`，实际因 (a) 条件写反 + (b) 忘记收集，恒返回空列表。全仓库无调用方。
- **风险**: 死代码中的陷阱，未来接入变更审计时直接失效。
- **建议**: 修正为 `if (!entity.orm_dirty()) return emptyList();` 并补 `ret.add(change)`；或删除死代码。
- **误报排除**: 已搜索确认无调用方。

### [P3] OrmSessionImpl.internalDelete 使用加载前状态，proxy 加载后发现 MISSING 仍标记 DELETING

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/session/OrmSessionImpl.java:967-993`
- **维度**: D1
- **证据**:
```java
OrmEntityState state = entity.orm_state();
if (state.isDeleting())
    return;

if (state.isProxy()) {
    internalLoad(entity);
}

if (state.isTransient()) { ... }
else if (state.isMissing()) { /* do nothing */ }
else {
    entity.orm_state(OrmEntityState.DELETING);   // state 是加载前的 PROXY，MISSING 分支永不命中
    this.markDirty();
    cache.markDirty(entity.orm_entityName());
}
```
- **现状**: proxy 实体 internalLoad 后若记录不存在（状态已变 MISSING），后续分支仍基于旧 state（PROXY）落入 else，实体被标记 DELETING 并触发多余的 delete SQL 与 onDelete 拦截器回调。
- **风险**: 删除不存在的 id 时执行无意义的 DELETE（影响 0 行）并触发本不应发生的 preDelete/postDelete 业务回调；不影响已存数据。
- **建议**: internalLoad 后重新读取 `entity.orm_state()` 再走状态分支。
- **误报排除**: 已验证 `_internalLoad` 失败时 `markMissing` 的路径与 OrmEntityState 各状态判定。

### [P3] GenSqlHelper 排序字段名未校验，未知字段触发无信息 NPE

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/sql/GenSqlHelper.java:554-562, 811-828`
- **维度**: D4
- **证据**:
```java
OrderFieldBean orderField = orderBy.get(i);
IColumnModel col = entityModel.getColumn(orderField.getName(), false);  // 未知字段返回 null
appendCol(sb, dialect, owner, col);      // appendCol 内 col.getSqlText() 直接 NPE
```
- **现状**: `genOrderBy`（sort 来自集合模型配置，风险低）与 `appendOrderBy`（`findAllByExample(example, orderBy)` 的 orderBy 由外部传入）都未校验字段名是否存在，未知字段在 `EqlHelper.appendCol` 的 `col.getSqlText()` 处抛裸 NPE，无任何"未知排序字段"信息。
- **风险**: 传错排序字段名时得到难以定位的 NPE 而非明确错误消息。无注入风险（拼接的是模型列名，未知值根本进不了 SQL）。
- **建议**: col 为 null 时抛带字段名的 NopException。
- **误报排除**: 已验证 `getColumn(name, false)` 的宽容语义与 `EqlHelper.appendCol` 的首行代码。

### [P3] DaoQueryHelper.appendField 缺少字段名/owner 校验，与同文件其他方法防御不一致

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/dao/DaoQueryHelper.java:132-142, 162-170`
- **维度**: D5
- **证据**:
```java
for (QueryFieldBean field : query.getFields()) {
    if (StringHelper.isEmpty(field.getAggFunc())) {
        appendField(sb, field.getOwner(), field.getName());   // 无 checkFieldName/checkOwnerName
    } else {
        checkFuncName(field.getAggFunc());                     // aggFunc 有校验
        ...
    }
}

private static void appendField(SQL.SqlBuilder sb, String ownerName, String name) {
    if (ownerName == null) ownerName = "o";
    ...
    sb.append(ownerName).append('.').append(name);             // 直接拼接
}
```
- **现状**: `appendGroupBy`/`appendOrderBy` 均调用 `checkOwnerName`/`checkFieldName`，select 字段的 name 与 owner 却直接拼进 EQL 文本。当前生成的 SQL 是 EQL，需经 EQL 编译器（实体属性映射）才能执行，未知字段会在编译期报错，因此**当前不可利用**。
- **风险**: 防御纵深缺失：一旦该 helper 的输出被直接交给原生 SQL 执行路径（如外部 JdbcBatchLoaderProvider 场景演进），字段名即成注入点。
- **建议**: appendField 内补 `checkFieldName(name)` 与 `checkOwnerName(ownerName)`，与同文件风格一致。
- **误报排除**: 已确认调用方（MdxQueryExecutor、OrmEntityDao、JdbcBatchLoaderProvider）中 queryToSelectFieldsSql 生成的 SQL 均经 OrmTemplate 的 EQL 编译路径；aggFunc 分支有校验说明作者已有防注入意识，此处属遗漏。

### [P3] OrmEntitySet.checkLoaded 使用 bare IllegalStateException

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/support/OrmEntitySet.java:379-381`
- **维度**: D7/D4
- **证据**:
```java
if (!tenantId.equals(ContextProvider.currentTenantId())) {
    if (dirty)
        throw new IllegalStateException("nop.orm.dirty-entity-set-not-allow-change-tenant:" + this);
```
- **现状**: 租户切换时集合带脏数据抛 bare `IllegalStateException` 并手工拼接消息。该类为框架公共路径（集合访问入口），按平台规范应使用 `NopException` + ErrorCode + `.param(...)`。同模块其余错误均遵循该规范。
- **风险**: 错误码体系外异常，无法被统一的错误分类/国际化处理。
- **建议**: 改用 OrmException + 新增 ErrorCode。
- **误报排除**: 已核对 OrmErrors 中无对应错误码，属规范违背而非既有约定。

### [P3] AddTenantColInitializer 初始化失败仅以 TRACE 级别记录

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/initialize/AddTenantColInitializer.java:69-74`
- **维度**: D4
- **证据**:
```java
try {
    jdbcTemplate.executeMultiSql(SQL.begin().sql(addSql).querySpace(querySpace).name("add_tenant").end());
    LOG.info("nop.orm.add-tenant-col:table={},col={}", entityModel.getTableName(), col.getCode());
} catch (Exception e) {
    LOG.trace("nop.orm.add-tenant-col-fail", e);
}
```
- **现状**: 新增租户列失败（列已存在属预期，但连接失败、权限不足等也走同一分支）仅 TRACE 记录，默认日志配置下完全不可见，启动流程无感知继续。
- **风险**: 真正的升级失败被"列已存在"的正常噪音掩盖，后续租户过滤异常难以回溯根因。
- **建议**: 至少提升为 WARN，或区分"列已存在"（忽略）与其他异常（快速失败）。
- **误报排除**: 已确认 catch 范围为所有 Exception 且无重新抛出路径。

### [P3] OrmEntitySet.orm_clearDirty 注释与代码矛盾

- **文件**: `nop-persistence/nop-orm/src/main/java/io/nop/orm/support/OrmEntitySet.java:637-644`
- **维度**: D2（维护性）
- **证据**:
```java
public void orm_clearDirty() {
    this.dirty = false;
    // 这里没有清空removeEntities,
    // 因为它可能已经传递到外部使用。例如BatchActions.CollectionBatchAction。
    this.removedEntities = null;
}
```
- **现状**: 注释声称"没有清空 removeEntities"，下一行却直接置 null。实际行为是安全的（`orm_removed()` 返回拷贝、CollectionBatchAction 构造时已暂存），但注释与代码相反会误导后续维护者做出错误修改。
- **风险**: 维护性风险。
- **建议**: 更新注释说明"字段置 null 不影响已通过 orm_removed() 传出的拷贝"。
- **误报排除**: 已核对 `orm_removed()` 返回防御性拷贝与 `CollectionBatchAction` 构造函数中的暂存逻辑。

## 其他已排查未列入的项（供参考）

- `JdbcEntityPersistDriver.lastUpdateSql`（volatile 缓存 SQL 模板）：读取-校验-使用局部变量模式自洽，竞态下仅重复生成，无正确性问题。
- `OrmSessionImpl.flush` 对 readOnly session 静默返回不 flush：符合只读 session 的常见设计（有 debug 日志）。
- 资源管理（D2）：driver 层连接/Statement/ResultSet 全部委托 `IJdbcTemplate`/`runWithConnection`/`JdbcBatcher` 管理，模块内无手写 JDBC 资源；session 关闭顺序（cache.clear → onClose 回调）未见泄漏路径。
- `BatchActionQueueImpl.flushDelayTasks` 吞异常但有 LOG.error 且 delayTask 为辅助任务，可接受。
- `SqlLibInvoker`/`SingleSessionMethodInterceptor` 的 catch 均正确解包/适配异常，无吞噬。
- `_gen/` 生成代码与 `@Inject` 用法（setter 注入、无 private 字段注入）符合平台规范。
