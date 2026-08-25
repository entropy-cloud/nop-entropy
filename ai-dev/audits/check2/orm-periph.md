# orm-periph 实现代码检查报告（check2）

- 检查日期: 2026-08-23
- 模块路径: nop-persistence 下 orm-model/orm-drivers/orm-pdm/orm-rpc/orm-data/orm-geo
- 文件数: 93（src/main/java，剔除 `_gen/` 后；orm-model 64、orm-drivers(nop-orm-tdengine) 6、orm-pdm 9、orm-rpc 1、orm-data 1、orm-geo 12。任务书预估约 120，实际以仓库现状为准；nop-orm-drivers 下 nop-orm-es/nop-orm-mongo/nop-orm-redis 无 src/main/java）
- 覆盖范围声明: 全文深读 44 个文件（pdm 全部 9 个、tdengine 全部 6 个、geo 全部 12 个、rpc/data 全部 2 个、orm-model 中 init/ 全部 4 个 + OrmEntityModel/OrmModel/OrmReferenceModel/OrmColumnModel/OrmComputePropModel/OrmComponentModel/OrmCompositePKModel/OrmAliasModel/OrmJoinOnModel/OrmIndexColumnModel/OrmUniqueKeyModel/OrmToManyReferenceModel/OrmToOneReferenceModel/OrmRefSetModel/OrmDomainModel 等 15 个）；其余 orm-model 文件为纯常量类（OrmModelConstants/OrmModelErrors/OrmModelConfigs/TdEngine* 等）与生成基类的空包装（OrmView* 5 个、OrmIndexModel、OrmPackageModel、OrmDomainModel 等）及纯接口（I* 20 个，default 方法已逐一浏览），均通读确认无实质逻辑。模式扫描（catch/异常吞噬、@Inject/@Value、synchronized/volatile、RuntimeException、SimpleDateFormat、字符串拼接进 SQL）覆盖 100% 文件。每条发现均交叉验证了调用方/被调方（nop-orm 加载器、CrudBizModel、FutureHelper、IBlockingSource、MutableIntArray 等）。未覆盖区域: 动态实体加载链（IDynamicEntityModelProvider 在仓库内无实现类，无法验证其构造 relation 的方式，相关推测性风险不列入）。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 2 |
| P2 | 3 |
| P3 | 8 |

## 发现列表

### [P1] RpcEntityPersistDriver.loadAsync 用 `id` 参数调用 batchGet 操作，与同文件 batchLoadAsync 的 `ids` 及标准接收方契约不匹配

- **文件**: `nop-persistence/nop-orm-rpc/src/main/java/io/nop/orm/rpc/RpcEntityPersistDriver.java:261-267`
- **维度**: D8（API/契约一致性，兼 D1）
- **证据**:
```java
public CompletionStage<Void> loadAsync(ShardSelection shard, IOrmEntity entity, IntArray propIds,
                                       FieldSelectionBean subSelection, IOrmSessionImplementor session) {
    String operationName = newEntityAction("batchGet");
    FieldSelectionBean selection = newSelection(propIds, subSelection);

    Map<String, Object> data = new HashMap<>();
    data.put("id", entity.orm_idString());
```
而同文件 batchLoadAsync（第 353-354 行）为同一操作名传的是：
```java
    Map<String, Object> data = new HashMap<>();
    data.put("ids", ids);
```
- **现状**: 同一个远端操作 `{shortName}__batchGet`，单实体路径传 `id`（String），批量路径传 `ids`（List）。标准接收方 `ICrudBiz.batchGet` / `CrudBizModel.batchGet`（nop-persistence/nop-orm/src/main/java/io/nop/orm/biz/ICrudBiz.java:58）声明的参数是 `@Name("ids") Collection<String> ids`，且 `CollectionHelper.isEmpty(ids)` 时直接返回空列表。
- **风险**: 单实体懒加载路径（OrmSessionImpl._internalLoad → EntityPersisterImpl.loadAsync；以及 batchLoadAsync 在实体数为 1 时也降级到 loadAsync，见 EntityPersisterImpl.java:138-139）发出的 RPC 请求中 `ids` 参数绑定不上：接收方要么报缺少必填参数，要么按空 ids 返回空列表，随后本类 `result != null` 分支不成立，实体属性永远装配不上，且不报任何错误（静默失败）。
- **建议**: loadAsync 改为 `data.put("ids", List.of(entity.orm_idString()))`，或改用专门的 `get`/`findFirst` 操作；两者与接收方参数契约对齐。
- **误报排除**: 已读 (1) RpcEntityPersistDriver 全文确认两处参数名不一致；(2) ICrudBiz/CrudBizModel.batchGet 的参数声明与空 ids 行为；(3) EntityPersisterImpl.java:138-139、OrmSessionImpl.java:857 确认单实体 loadAsync 是现实调用路径；(4) batchModify/batchDelete/findList/findFirst 的参数名（data/ids/query）均与 ICrudBiz 对应方法一致，仅 loadAsync 此处错位。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。loadAsync 改为 `data.put("ids", List.of(entity.orm_idString()))`；同时发现仅改参数名会令 `(Map) response.getData()` 对 List 返回值抛 ClassCastException（batchGet 返回 `List<T>`），故响应改为按 List 解析并按主键匹配行（复用 buildEntityIdKey/buildMapIdKey，新增 findMatchedRow 私有方法）。测试：TestRpcEntityPersistDriver#testLoadAsyncSendsIdsParamAndBindsMatchedRow（验证 ids 参数、乱序多行按主键绑定）。红验证：旧代码上该测试以 ClassCastException（List12 cannot be cast to Map）失败。

### [P1] RpcEntityPersistDriver.loadAsync 远端未返回数据时不 markMissing，与 batchLoadAsync 及 JDBC/TDengine 驱动行为不一致

- **文件**: `nop-persistence/nop-orm-rpc/src/main/java/io/nop/orm/rpc/RpcEntityPersistDriver.java:269-279`
- **维度**: D1（边界条件/错误处理）+ D8
- **证据**:
```java
return invokeRpc(operationName, data, selection).thenAccept(response -> {
    checkResponse(response);
    Map<String, Object> result = (Map<String, Object>) response.getData();
    if (result != null) {
        bindEntity(entity, result, propIds, session);
        ...
    }
});   // result == null 时什么都不做
```
- **现状**: 远端返回 data 为 null（实体不存在，或上一条 P1 的参数绑定失败导致空结果）时，future 正常完成，但既不装配也不 `session.markMissing(entity)`。对比同文件 batchLoadAsync 第 384-387 行：`for (IOrmEntity entity : entityMap.values()) { session.markMissing(entity); }`；JdbcEntityPersistDriver.loadAsync（nop-orm/.../JdbcEntityPersistDriver.java:133-136）和 TdEntityPersistDriver.loadAsync（第 90-92 行）在无行时也都 `session.markMissing(entity)`。
- **风险**: 实体缺失时状态机不落 MISSING：OrmSessionImpl._internalLoad 依据 `entity.orm_state() != OrmEntityState.MISSING` 判定成功并返回 true，调用方读取属性得到未初始化值；且实体仍处于 allowLoad 状态，后续属性访问会反复触发同一 RPC 加载（远端不存在的实体造成重复远程调用）。
- **建议**: 在 `result == null` 分支补 `session.markMissing(entity)`，与另外三个驱动实现对齐。
- **误报排除**: 已读 JdbcEntityPersistDriver.loadAsync、TdEntityPersistDriver.loadAsync、本类 batchLoadAsync 三处对照实现，以及 OrmSessionImpl._internalLoad（第 861-863 行）对 MISSING 状态的依赖。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。loadAsync 的 else 分支补 `session.markMissing(entity)`（远端未返回该实体数据时），与 batchLoadAsync 及 Jdbc/Td 驱动对齐；该修复与上一条（参数契约）在同一次 loadAsync 改造中完成。测试：TestRpcEntityPersistDriver#testLoadAsyncMarksMissingWhenNoData（验证 markMissing 被调用且不 internalAssemble）。红验证：旧代码上该测试失败于 `verify(session).markMissing(e1)`（Wanted but not invoked）。

### [P2] OrmEntityModel.hasLazyColumn() 恒返回 true：双重 toImmutable() 破坏了初始化器的别名优化

- **文件**: `nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/OrmEntityModel.java:137-140` 与 `OrmEntityModel.java:353-355`、`init/OrmEntityModelInitializer.java:516-517`
- **维度**: D1（逻辑错误）+ D6（性能）
- **证据**:
```java
// OrmEntityModel
public boolean hasLazyColumn() {
    return allPropIds != eagerLoadProps;   // 引用比较
}
// OrmEntityModel.init()
this.allPropIds = initializer.getAllPropIds().toImmutable();
this.eagerLoadProps = initializer.getEagerLoadProps().toImmutable();
// OrmEntityModelInitializer.initProps() 尾部的别名优化
if (this.eagerLoadProps.size() == this.allPropIds.size())
    this.eagerLoadProps = this.allPropIds;
```
- **现状**: 初始化器在无 lazy 列时把 `eagerLoadProps` 与 `allPropIds` 指向同一 `MutableIntArray`，使引用比较结果为 false。但 `MutableIntArray.toImmutable()`（nop-kernel/nop-commons/.../MutableIntArray.java:91-93）每次都 `new ImmutableIntArray(toArray())`，OrmEntityModel.init() 对同一对象调用了两次 toImmutable()，产生两个不同实例，`!=` 恒为 true。
- **风险**: `hasLazyColumn()` 对所有实体恒真。消费方（nop-orm/loader/OrmBatchLoadQueueImpl.java:373、567、651）中：第 373/567 行会做本可跳过的逐属性入队；第 651 行 `if (!hasLazyColumn())` 的实体裁剪优化永久失效，批量加载队列中残留已加载实体，造成多余的批量装载（方向是"多加载"，不产生错误数据，故定 P2 而非 P1）。
- **建议**: 在 init() 中先取 `MutableIntArray all = initializer.getAllPropIds();`，再统一 `ImmutableIntArray imm = all.toImmutable()` 并按 `initializer.getEagerLoadProps() == all` 决定是否复用同一 ImmutableIntArray；或把 hasLazyColumn() 改为基于内容的布尔标志。
- **误报排除**: 已读 MutableIntArray.toImmutable() 源码确认无缓存；确认 OrmEntityModel 两个私有字段仅在 init() 中赋值、`inited` 标志防重入；确认 ImmutableIntArray 无实例缓存；grep 全仓库 hasLazyColumn 仅上述三个消费点；grep 测试目录无对 hasLazyColumn 的断言（不存在"测试证明其为 false"的反证）。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。OrmEntityModel.init() 先取 `MutableIntArray allPropIds/eagerLoadProps` 局部引用，`eagerLoadProps == allPropIds` 时复用同一 `this.allPropIds` 不可变实例，保持初始化器的别名优化；无 lazy 列时 hasLazyColumn() 恢复返回 false，OrmBatchLoadQueueImpl 的实体裁剪优化重新生效。测试：TestOrmEntityModelHasLazyColumn#testNoLazyColumn、#testLazyColumnExists（含 eager/all 集合内容断言）。红验证：旧代码上 testNoLazyColumn 失败于 `expected: <false> but was: <true>`。

### [P2] PdmModelParser.addJoin 在循环内 return，丢弃全部已收集的 join 且不设置 columns

- **文件**: `nop-persistence/nop-orm-pdm/src/main/java/io/nop/orm/pdm/PdmModelParser.java:1251-1263`
- **维度**: D1（边界条件/逻辑错误）
- **证据**:
```java
for (XNode refJoinN : joinsN.elements(JOIN_KEY)) {
    XNode objectOne = refJoinN.element(OBJECT1_NAME);
    if (objectOne == null)
        objectOne = refJoinN.element(COLUMN1_NAME);
    if (objectOne == null) {
        return;                       // <-- 直接返回整个方法
    }
    XNode objectTwo = refJoinN.element(OBJECT2_NAME);
    ...
    if (objectTwo == null) {
        return;                       // <-- 同上
    }
```
- **现状**: `<c:Joins>` 下任一 `<o:ReferenceJoin>` 缺少 Object1/Column1 或 Object2/Column2 时，方法直接 return：(a) 已收集的 joins 局部列表被丢弃，`rel.setJoin(joins)`/`rel.setColumns(relCols)` 不会执行，即使其余 join 条件完全合法；(b) 若整个 Reference 的 Joins 存在但所有 join 均缺 Object 节点，`rel.getColumns()` 保持 null，随后 collectRefInfo 第 790 行 `isAllPrimaryCol(rel.getColumns())` 对 null 迭代直接 NPE。对比同方法第 1268-1269 行对"列解析失败"的处理是 `continue`（跳过单条），行为不一致。
- **风险**: 畸形/被裁剪的 PDM 引用定义导致关联条件整体丢失（生成的 ORM 模型该关联无 join 条件，后续 OrmEntityModelInitializer.initRelations 会报 ERR_ORM_MODEL_REF_JOIN_NO_CONDITION，错误位置与真实原因脱节），或直接 NPE。
- **建议**: 两处 `return` 改为 `continue`；并保证 addJoin 总是执行 `rel.setJoin(joins); rel.setColumns(relCols);`（空列表也应设置），同时在 collectRefInfo 对 columns 为空时给出带表名的明确校验错误。
- **误报排除**: 已读 addJoin 全文与 collectRefInfo 第 786-798 行（isAllPrimaryCol 迭代 getColumns()、`rel.getColumns().get(0)`），确认 null/空列时的两条崩溃路径；已读 OrmReferenceModel.setColumns（null 入参本身会 NPE，但此处是不调用而非传 null）。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。addJoin 循环内两处 `return` 改为 `continue`（附 WARN 日志 pdm.reference_join_missing_object），畸形 join 只跳过自身；`rel.setJoin/setColumns` 在 joinsN 存在时必然执行。collectRefInfo 在 addJoin 之后新增校验：columns 为 null/空时抛新增错误码 ERR_PDM_REFERENCE_NO_JOIN_COLUMN（PdmModelErrors，带 childTableName/parentTableName 参数），替代原 isAllPrimaryCol(null) 的裸 NPE。测试：TestPdmParser#testAddJoinMalformedJoinSkipped（畸形+合法混合 → 保留合法 join）、#testAllJoinsMalformedThrowsWithContext（全部畸形 → 带表名 NopException）。红验证：旧代码上分别以 `NullPointerException: cols is null` 与「expected NopException but was NullPointerException」失败。

### [P2] LazyLoadOrmModel.getCollectionModel 对不含 '@' 的名字抛 StringIndexOutOfBoundsException，偏离 IOrmModel 契约

- **文件**: `nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/lazy/LazyLoadOrmModel.java:226-239`
- **维度**: D8（契约一致性）+ D1
- **证据**:
```java
public IEntityRelationModel getCollectionModel(String collectionName) {
    if (baseModel != null) {
        IEntityRelationModel relModel = baseModel.getCollectionModel(collectionName);
        if (relModel != null)
            return relModel;
    }

    int pos = collectionName.indexOf('@');
    String entityName = collectionName.substring(0, pos);   // pos == -1 时越界
```
- **现状**: 静态 OrmModel.getCollectionModel 是 map 查找，名字非法时返回 null；LazyLoadOrmModel 版本在名字不含 '@' 时 `substring(0, -1)` 抛未包装的 StringIndexOutOfBoundsException。
- **风险**: 任何把非法集合名传入该公共接口实现的调用方得到裸数组越界异常而非 null/业务异常，掩盖真实问题。仓库内现有调用（OrmSessionImpl/CascadeFlusher/OrmBatchLoadQueueImpl）传入的名字均来自 `OrmModelHelper.buildCollectionName`（恒含 '@'）或先经 requireCollectionPersister 报业务错，故当前无现实触发路径，属契约漂移式隐患。
- **建议**: `if (pos <= 0) return null;`（或抛带参数名的 NopException）。
- **误报排除**: 已读 OrmModel.getCollectionModel（map get）、OrmModelHelper.buildCollectionName（`entityName + '@' + propName`）、OrmSessionImpl.getCollectionModel → requireCollectionPersister 的前置校验链，确认 in-repo 调用不触发，故降为 P2 而非 P1。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。`pos <= 0`（不含 '@' 或以 '@' 开头）时返回 null，与静态 OrmModel 的 map 查找语义对齐，非法名字统一由 requireCollectionModel 抛 ERR_ORM_UNKNOWN_COLLECTION_NAME；实体名合法但实体不存在时仍走 requireEntityModel 抛业务异常。测试：TestLazyLoadOrmModel#testGetCollectionModelIllegalNameReturnsNull。红验证：旧代码上以 `StringIndexOutOfBoundsException: Range [0, -1)` 失败。

### [P3] PdmModelParser 对缺失 Name/Code 的 PDM 元素直接 NPE

- **文件**: `nop-persistence/nop-orm-pdm/src/main/java/io/nop/orm/pdm/PdmModelParser.java:237-241`、`479`、`295`
- **维度**: D1（边界条件）+ D4
- **证据**:
```java
String name = node.elementText(NAME_NAME);
// 兼容以前的配置格式
if (name.startsWith("*"))          // elementText 返回 null 时 NPE
    name = name.substring(1);
...
table.setTableName(elm.getCode().toLowerCase());   // code 为 null 时 NPE（第479行）
```
- **现状**: `XNode.elementText`（nop-kernel/nop-core/.../XNode.java:2326-2331）在元素不存在时返回 null。parseElement/parseTableName/parseDomains 均未判空。PowerDesigner 正常导出总带 Name/Code，但手工编辑或第三方工具生成的 pdm 可能缺失。
- **风险**: 缺失必填元素时抛出无 source/location、无元素路径的裸 NPE，排障困难（对比：parseDataType 对未知类型经 getNativeType 抛出带 ARG_DATA_TYPE 的 NopException）。
- **建议**: 在 parseElement 入口对 Name/Code 缺失抛 `NopException` 并 `.source(node.getLocation())` 附带节点标签。
- **误报排除**: 已读 XNode.elementText 实现确认 null 语义；已读 parseDomains/parseTableName/parseColumnTag 全部使用点确认无前置判空。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。parseElement 入口对 Name 或 Code 缺失抛新增错误码 ERR_PDM_ELEMENT_MISSING_NAME_OR_CODE（PdmModelErrors，`.source(node)` + tagName 参数），覆盖 name.startsWith NPE、parseTableName getCode().toLowerCase NPE、parseDomains startsWith NPE 三条崩溃路径（parseTable/parseView/parseColumn/parseDomains/parseIndex/parseKey 等全部经 parseElement）。复核确认所有经 parseElement 的元素类型（Model/Package/Table/View/Column/PhysicalDomain/Reference/Index/Key/Shortcut）在合法 PDM 中均必有 Name+Code，入口校验不会误伤。测试：TestPdmParser#testParseTableMissingNameThrows、#testParseTableMissingCodeThrows。红验证：旧代码上分别以 `NPE: name is null`（startsWith）与 `NPE: key is null`（CaseInsensitiveMap put null code）失败。

### [P3] DaoEntityBlockingSource.drainTo 未实现 IBlockingSource 的攒批契约，且丢失 maxElements<=0 防御

- **文件**: `nop-persistence/nop-orm-data/src/main/java/io/nop/orm/data/source/DaoEntityBlockingSource.java:136-170`
- **维度**: D8
- **证据**:
```java
public int drainTo(Collection<? super T> c, int maxElements, long minWait, long maxWait) throws InterruptedException {
    IEntityDao<T> dao = daoProvider.dao(entityName);
    if (maxWait == 0) {
        List<T> items = loadItems(dao, maxElements);   // 无 maxElements<=0 防御
        ...
    boolean found = FutureHelper.waitUntil(() -> {
        List<T> items = loadItems(dao, maxElements);
        if (items.isEmpty()) {
            return false;
        }
        ...
        c.addAll(items);
        return true;                                    // 拿到任意条即返回
    }, timeout, interval);
```
- **现状**: 接口 `IBlockingSource.drainTo(c, maxElements, minWait, maxWait)` 的 javadoc（nop-kernel/nop-commons/.../IBlockingSource.java:69）约定"返回时要么超时时间已到，要么获取到的数据条目数为 maxElements"，default 实现按 minWait 攒批；本覆写只要查到任意条数（< maxElements）就立即返回，minWait 仅用于钳制轮询间隔。另外接口 default 实现入口有 `if (maxElements <= 0) return 0;` 防御，覆写后丢失，maxElements=0 时 `query.setLimit(0)` 的语义未定义。
- **风险**: 批量消费方退化为小批量高频拉取（纯吞吐损失，无数据错误）；被调用方传 0 时行为未定义。
- **建议**: 入口补 `if (maxElements <= 0) return 0;`；waitUntil 内改为累计到 maxElements 或 minWait 用尽才返回 true（或修订自身 javadoc 明示"查到即返回"语义）。
- **误报排除**: 已读 IBlockingSource 接口 default 实现与本类覆写全文对照；已读 FutureHelper.waitUntil（第 500-516 行）确认其只做布尔终止判定、不支持攒批回调，问题确在覆写层。另注：曾怀疑 `take()/poll()` 经 this 调用绕过 @Transactional(REQUIRES_NEW)，经查 Nop 采用生成子类的 AopProxy（见 docs/ref/AuditServiceImpl__aop.java 样例，虚分派会命中覆写方法），内部调用仍会被拦截，该疑点已排除、不列为发现。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。(1) 入口补 `if (maxElements <= 0) return 0;` 恢复接口防御；(2) waitUntil 谓词改为攒批：按剩余量 loadItems 累计到 c，拿满 maxElements 或「已有部分数据且 minWait 窗口（自方法起点算，与接口 default 实现一致）用尽」时返回 true，实现接口声明的攒批契约；中断语义微调为「已取到数据时优先返回数据」（javadoc 同步更新）。takeMulti（minWait=-1）保持首批即返回的原行为。测试：TestDaoEntityBlockingSource#testDrainToAccumulatesUntilMaxElements、#testDrainToReturnsPartialWhenMinWaitExpired、#testDrainToMaxElementsNotPositiveSkipsQuery。红验证：旧代码上分别失败于 `expected: <3> but was: <1>`、`elapsed=128 <minWait`（提前返回）、`verify never findAllByQuery`（limit=0 查库）。

### [P3] OrmReferenceModel 的 ormModel 分支为死代码，且与活跃缓存路径逻辑分歧

- **文件**: `nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/OrmReferenceModel.java:136-185`
- **维度**: D8（可维护性）
- **证据**:
```java
public int[] getRefPropIds() {
    if (ormModel != null) {
        if (this.getJoin().size() == 1) {
            OrmJoinOnModel joinOn = this.getJoin().get(0);
            if (joinOn.getRightProp() != null) {
                IColumnModel propModel = getRefEntityModel().getColumn(joinOn.getRightProp(), false);
                return new int[]{propModel.getPropId()};
            }
        } ...
    }
    return refPropIds;   // 实际总是走这里
```
- **现状**: `setOrmModel` 在全仓库（含测试外的全部源码）无任何调用方，`ormModel` 恒为 null，第 136-185、165-184 行的重算分支为死代码。活跃路径是 OrmModelInitializer.buildRefPropIds（经 `join.getRightPropModel().getColumns()` 计算后 setRefPropIds 缓存）。死分支用 `getColumn(rightProp, false)` 重算，对别名/复合主键 join 会抛 ERR_ORM_UNKNOWN_COLUMN，与缓存路径行为不一致，一旦被激活会引入差异。
- **风险**: 当前无运行时影响；维护时误以为该分支生效，或未来激活时行为突变。
- **建议**: 删除 ormModel 字段及相关分支，或补注释声明保留原因。
- **误报排除**: grep 全仓库 `setOrmModel(` 无调用方（OrmReferenceModel 自身除外）；已读 OrmModelInitializer.buildRefPropIds 与 checkJoin 确认缓存路径的构造方式。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复（纯死代码删除）。复核时另发现报告未列的第三处死分支：getRefEntityModel() 中的 `ormModel.getEntityModel(...)` 分支（同样恒不生效）。一并删除 ormModel 字段、setOrmModel 方法及 isJoinOnNonPkColumn/getRefPropIds/getRefEntityModel 三处死分支，getRefPropIds/getRefEntityModel 处补注释指明缓存由 OrmModelInitializer 设置；移除不再使用的 OrmModelHelper 导入。免红测试理由：死代码删除，无任何行为变化（ormModel 全仓库无赋值点），既有 8 个 orm-model 测试全绿作回归。

### [P3] TdSqlHelper.appendEq 的 binder 参数从不使用，TDengine SQL 值全部字面量内联

- **文件**: `nop-persistence/nop-orm-drivers/nop-orm-tdengine/src/main/java/io/nop/orm/tdengine/model/TdSqlHelper.java:159-164`
- **维度**: D6（+ D5 弱相关）
- **证据**:
```java
public static void appendEq(SQL.SqlBuilder sb, IDialect dialect, String owner, IColumnModel col,
                            IDataParameterBinder binder, Object value) {
    appendCol(sb, dialect, owner, col);
    sb.append("=");
    appendValue(sb, value);       // binder 未使用；值经 appendValue 内联
}
```
- **现状**: 所有谓词值（含 genLoadSql/appendEntityEk 的主键值、appendExampleFilter 的租户/过滤值）以字面量拼进 SQL 文本；字符串经 `StringHelper.escapeSql(str, true)` 转义（`'`→`''`，已核对 StringHelper.java:648-666），Number 直接 append，注入风险已缓解。但 (a) binder 参数（含 `binders[col.getPropId()]` 的索引计算）是纯死代码；(b) 生成的 SQL 文本携带全部业务数据，进入 SQL 日志即明文泄露；(c) TdEntityPersistDriver.batchExecuteAsync topoDesc 分支（第 146 行）使用字段 `dialect` 而非按传入 querySpace 解析（JdbcEntityPersistDriver 第 227 行为 `getDialect(querySpace)`），多 querySpace 场景方言选择可能错。
- **风险**: 无正确性错误；热路径上多余计算、日志敏感信息暴露面扩大、多方言配置下的潜在不一致。
- **建议**: 删除 appendEq 的 binder 参数；TDengine 如支持参数绑定改用绑定；batchExecuteAsync 删除分支改用按 querySpace 解析 dialect。
- **误报排除**: 已读 appendValue/appendString/escapeSql 链路确认转义完备（不构成注入发现）；已读 appendExampleFilter 与 GenSqlHelper.appendExampleFilter 对照确认租户过滤行为与核心一致（`ContextProvider.currentTenantId()` 同样用法，非本模块偏差）。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。(a) 删除 appendEq 的 binder 参数及全部 5 处调用点的 `binders[col.getPropId()]` 索引计算，并级联删除 appendExampleFilter/appendBatchLoadEq/genCountByExample/genDeleteByExample/genBatchDelete/genFindByExample 的 binders 形参与 TdEntityPersistDriver 调用点对应实参（driver 的 binders 字段仍供 OrmAssembly 使用），appendEq 处补注释声明「TDengine 不支持参数绑定、值以转义字面量内联」；(b) 字面量内联维持现状（TDengine 驱动无参数绑定通道，转义链路已由报告确认完备）；(c) batchExecuteAsync 删除分支改为新增的 getDialect(String) 按传入 querySpace 解析方言（镜像 JdbcEntityPersistDriver 第 330-335 行实现）。测试：TestTdEntityPersistDriver#testBatchExecuteAsyncDeleteResolvesDialectByQuerySpace（红验证：旧代码失败于 `Wanted but not invoked: env.getDialectForQuerySpace("other")`，即从不按 querySpace 解析）；binder 参数删除为编译级死代码，行为不变，免红（既有 13 个 tdengine 测试全绿）。

### [P3] GeometryTypeHandler.fromLiteral 返回 null 的存根实现

- **文件**: `nop-persistence/nop-orm-geo/src/main/java/io/nop/orm/geo/type/GeometryTypeHandler.java:75-78`
- **维度**: D8
- **证据**:
```java
@Override
public Object fromLiteral(String text, IDialect dialect) {
    return null;
}
```
- **现状**: `toLiteral` 正常生成 `ST_GeomFromText('...')` 字面量，而 `fromLiteral` 恒返回 null。grep 全仓库 `fromLiteral` 仅剩接口声明（IDataTypeHandler.java:15）与本实现，无任何调用方——接口方法本身是死契约。
- **风险**: 当前无运行时影响；若未来在 SQL 文本回解析场景（如条件反编译、审计）启用该方法，几何字面量会被静默解析为 null。
- **建议**: 删除接口方法，或实现基于 parseWkt 的反解析；至少补注释声明"未实现"。
- **误报排除**: grep 全仓库 fromLiteral 调用点确认无消费方；已读 DialectImpl 第 563 行仅消费 toLiteral。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。接口方法在 nop-dao（本单元范围外）且全仓库无调用方，不做接口删除；实现从「恒返回 null」改为显式 `throw new UnsupportedOperationException`（附注释），与同接口 DefaultJsonTypeHandler.fromLiteral 的惯例一致，消除「字面量被静默解析为 null」的隐患（check2 附注已认定显式不支持存根语义清晰）。测试：TestGeometryTypeHandlerErrors#testFromLiteralThrowsExplicitUnsupported。红验证：旧代码上失败于 `Expected UnsupportedOperationException to be thrown, but nothing was thrown`。

### [P3] PdmModelParser.removeViewsNoPk 保留 tables 映射，引用被移除视图的关系在 init 时报脱节的错误

- **文件**: `nop-persistence/nop-orm-pdm/src/main/java/io/nop/orm/pdm/PdmModelParser.java:190-197` 与 `873-879`
- **维度**: D1（边角）+ D4（错误信息质量）
- **证据**:
```java
void removeViewsNoPk() {
    List<OrmEntityModel> views = tables.values().stream()
            .filter(tbl -> tbl.isReadonly() && tbl.getPkColumns().isEmpty()).collect(Collectors.toList());
    for (OrmEntityModel view : views) {
        tablesByCode.remove(view.getTableName());   // 仅从输出集合移除
    }
}
```
- **现状**: 无主键视图从 `tablesByCode`（最终 entities 来源）移除，但 `tables` 仍保留，故 parseAllReferences 阶段以 `tables.get(tableId)` 解析出的、指向这类视图的引用会生成 `rel.setRefEntityName(viewName)`，而视图已不在 OrmModel.entities 中，OrmModelInitializer.checkRefPrimary（OrmModelInitializer.java:433-437）抛 ERR_ORM_MODEL_REF_UNKNOWN_ENTITY，报错呈现为"未知实体"而非"视图无主键被剔除"。
- **风险**: 仅错误可诊断性问题（PDM 工具路径），无运行时数据风险。
- **建议**: removeViewsNoPk 同时从 tables 移除（引用会按 parentTableInfo==null 被静默跳过），或在移除时记录被剔除视图与受影响引用的 WARN。
- **误报排除**: 已读 parseAllReferences/getReferenceTable/addRelation 的表解析链，及 OrmModelInitializer.checkRefPrimary 的查找逻辑，确认错误链路如上所述。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。removeViewsNoPk 改为单趟 `tables.values().removeIf(...)`：从 tables 与 tablesByCode 同时移除，指向被剔除视图的引用在 collectRefInfo 按 parentTableInfo==null 被跳过，并输出 WARN 日志 pdm.remove_view_no_pk（tableName/entityName）便于诊断。复核时确认机制成立的前提是视图经 `<c:Packages><o:Package>` 路径解析（parseViews 仅由 parsePackage 调用，顶层 c:Views 不解析——见文末超范围备注）。测试：TestPdmParser#testReferenceToViewNoPkSkipped（包内表+无主键视图+指向该视图的引用 → 解析成功、0 关联、WARN）。红验证：旧代码上以 `NopException ref-unknown-entity: entityName=pkg1.TParent, refEntityName=pkg1.VView1` 失败，即报告所述脱节错误。

### [P3] OrmComponentModel.getColumnPropIds 顺序不确定（HashMap values 迭代序）

- **文件**: `nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/OrmComponentModel.java:106-119`
- **维度**: D6/D8
- **证据**:
```java
public int[] getColumnPropIds() {
    if (propIds == null) {
        Map<String, Integer> colPropIds = getColumnPropIdMap();   // HashMap
        int[] propIds = new int[colPropIds.size()];
        int index = 0;
        for (Integer propId : colPropIds.values()) {
            propIds[index] = propId;
            ...
public int getColumnPropId() {
    return getColumnPropIds()[0];      // 取哈希序首元素
```
- **现状**: propIds 顺序由 HashMap 哈希序决定，与 getProps() 声明顺序无关；getColumnPropId() 对多列组件返回"哈希序第一个"，语义不确定。
- **风险**: 现有消费方（OrmBatchLoadQueueImpl 的 `propIds.merge(...)`、OrmEntityHelper/DynamicOrmEntity 的全量遍历）均顺序不敏感，当前无错误；未来若有按序消费（值数组对位装配）会引入隐蔽 bug。
- **建议**: 改为按 getProps() 顺序构造；多列组件的 getColumnPropId 明确抛错或返回 0。
- **误报排除**: grep 全部 getColumnPropIds() 消费点（EntityTableMeta/OrmEntityHelper/DynamicOrmEntity/OrmBatchLoadQueueImpl）确认均为顺序不敏感用法。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。getColumnPropIds() 改为按 getProps() 声明顺序构造（附注释说明 HashMap.values() 哈希序问题）；getColumnPropId() 保持 `[0]` 语义但结果由「哈希序第一个」变为「声明顺序第一个」，确定化而非抛错（避免破坏现有多处单列取值用法，多列组件的 [0] 语义现在至少可预测）。测试：TestOrmComponentModel#testGetColumnPropIdsFollowsDeclarationOrder（选取 "B"/"a" 两键使 HashMap 桶序与声明序相反）。红验证：旧代码上失败于 `array contents differ at index [0], expected: <2> but was: <1>`。

### [P3] PdmModelParser 列 code 大写化使用默认 Locale

- **文件**: `nop-persistence/nop-orm-pdm/src/main/java/io/nop/orm/pdm/PdmModelParser.java:533`
- **维度**: D1（边角）
- **证据**:
```java
col.setCode(elm.getCode().toUpperCase());   // 无 Locale 参数
```
- **现状**: 代码库其他处一致使用 `Locale.ROOT`（如 OrmEntityModelInitializer 第 141 行 `toUpperCase(Locale.ROOT)`）。在 tr 等特殊 Locale 的 JVM 上，`'i'` 会大写为 `'İ'`，生成的列 code 异常。
- **风险**: 仅影响特殊 Locale 环境下的 PDM 导入；后续 colsByCode 为 CaseInsensitiveMap 可部分兜底，但生成到 orm.xml 的 code 文本会被污染。
- **建议**: 改为 `toUpperCase(Locale.ROOT)`。
- **误报排除**: 已读上下文确认 elm.getCode() 类型为 String 且此处是唯一的大写化点；已对照 OrmEntityModelInitializer 第 139-141 行的 ROOT 用法确认项目惯例。

> **处置（fix-ai-check 分支，2026-08-25）**: 已修复。列 code 大写化改为 `toUpperCase(Locale.ROOT)`；同文件 parseTableName 第 479 行的 `elm.getCode().toLowerCase()` 属同类问题，一并改为 `toLowerCase(Locale.ROOT)`（与 OrmEntityModelInitializer 惯例一致）。测试：TestPdmParser#testColumnCodeUppercaseUsesLocaleRoot（临时 Locale.setDefault(tr-TR)，finally 恢复；列 code "input" 断言为 "INPUT"）。红验证：旧代码上失败于 `expected: <INPUT> but was: <İNPUT>`。

## 附注（已核实为非问题的高风险疑点）

- **DaoEntityBlockingSource 内部调用绕过事务**：Nop 的 AOP 为生成子类（`*__aop` 覆写被注解方法，虚分派生效），`take()→takeMulti()→drainTo()` 链仍会命中 `@Transactional(REQUIRES_NEW)` 拦截，不构成发现（对照 docs/ref/AuditServiceImpl__aop.java 样例）。
- **RpcEntityPersistDriver.batchExecuteAsync 返回 null**：唯一消费方 BatchActionQueueImpl.flushAsync 经 `FutureHelper.collectWaiting`（null 安全）与 `isError`（null 返回 false）处理，安全。
- **TdMessageService 两个方法抛 UnsupportedOperationException**：作为显式"不支持"存根，语义清晰，非异常吞噬。
- **PdmModelParser.parseDataType 的 getNativeType**：`IDialect.getNativeType(name)` 默认 ignoreUnknown=false，未知类型抛带参数的 NopException，不存在 null 解引用。
- **TdTableMeta.getSubTableName**：子表名有 `^[A-Za-z0-9_]+$` 白名单校验，注入防护完备。
- **GeometryTypeHandler.toByteArray**：Blob 读取在 finally 中 safeClose，无流泄漏。
- **D7（Nop IoC 规范）**：rpc/data/geo 模块的 `@Inject` 均为 setter 或包私有字段注入（H2GisInitializer 的 `@Inject IJdbcTemplate jdbcTemplate` 为包私有，合规），无 `@Value`、无 private 字段注入；H2GisInitializer 已在 nop-orm-geo.beans.xml 按 on-class 条件注册；RpcEntityPersistDriver/TdMessageService 为供应用侧装配的库类（仓库内无预期注入点），不计违规。

## 处置补充（fix-ai-check 分支，2026-08-25）

### 超范围新发现（未修复，仅记录）

- **PdmModelParser 不解析顶层 `<c:Views>`**：`doParseResource` 对顶层表调用 `parseTables(modelNode, null)`，但 `parseViews` 仅由 `parsePackage` 调用，位于 Model 根下（不在 `c:Packages` 内）的视图被静默忽略——既不进入实体，也不参与 removeViewsNoPk，指向它们的引用因 tables 查不到而被静默跳过。P3-6 复核期间实测确认（包内视图正常解析、顶层视图消失）。未修复原因：改变解析流程超出本单元 13 条发现的最小修复范围，建议单独立项裁定（是否应补 `parseViews(modelNode, null)` 调用，或该格式本就不受支持）。
- **OrmReferenceModel 死分支实际有三处**：除报告所列 getRefPropIds/isJoinOnNonPkColumn 两处外，getRefEntityModel() 也含 `ormModel != null` 死分支，已在 P3-3 处置中一并删除。

### 处置统计

| 终态 | 数量 |
|------|------|
| 已修复 | 13/13 |
| 其中含红验证回归测试 | 12 条（P1×2、P2×3、P3 中除 P3-3 外全部） |
| 免红注明 | P3-3（纯死代码删除，无行为变化）；P3-4 的 binder 参数项（编译级死代码，其方言修复部分有红验证） |

新增 ErrorCode（均为模块 errors 接口常量，描述中文内联，与 PdmModelErrors 既有惯例一致，无 i18n bundle——核对 nop-orm-pdm 模块无 i18n 资源目录，确认无需补充）：`ERR_PDM_ELEMENT_MISSING_NAME_OR_CODE`、`ERR_PDM_REFERENCE_NO_JOIN_COLUMN`。
