# nop-biz 实现代码检查报告

- 检查日期: 2026-08-19
- 模块路径: nop-service-framework/nop-biz
- 文件数: 101（src/main/java 实测，任务描述约 109；其中 13 个为 `_gen/` 生成文件，按平台规范跳过不审）
- 覆盖范围声明: 逐行深读 CrudBizModel.java（全部 2131 行）、全部 action 装饰器与收集器（Cache/CacheEvict/Transaction + Collector）、OrmEntityCopier、ObjMetaBasedValidator、ObjMetaBasedFilterValidator、EntityData、AutoExprRunner、DelayedRelationAction、ManyToManyTool、TreeEntityHelper、BizSchemaHelper、BizObjMetaHelper、BizQueryHelper、CrudToolProvider、CrudBizInitializer、BizObjHelper、BizObjectManager/BizObjectBuilder/BizObjectImpl/BizObjectBuildHelper、ObjectDefinitionExtProcessor、EvalServiceAction、EvalActionDataFetcher、BizObjectQueryProcessorAdapter、BizModelToGraphQLDefinition、BizProxyInvocationHandler/BizProxyFactoryBean、BizActionService/BizActionInvocation/DefaultBizHashFunction、makerchecker 全部 3 个文件、dict 全部 2 个文件、dev 全部 3 个 BizModel、DownloadHelper、两个 ArgsNormalizer、DynamicCrudBizModel/EmptyBizModel、BizConfigs；api/* 接口、model/* 非生成类、dev/beans/*、schema/* 与常量类（BizErrors/BizConstants）做通读扫视（低风险数据/接口定义）。`_gen/` 生成文件未审。测试代码不在范围。关键疑点均通过阅读 nop-orm（OrmEntityDao/OrmSessionImpl）、nop-core（FilterBeanToSQLTransformer/AuthHelper）、beans.xml 装配与 git 历史（如 8c14c0270）做了交叉验证以排除误报。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 2 |
| P2 | 6 |
| P3 | 7 |

## 发现列表

### [P1] CacheActionDecorator 两个分支完全相同，缓存结果被丢弃且从不写入缓存

- **文件**: `nop-service-framework/nop-biz/src/main/java/io/nop/biz/decorator/CacheActionDecorator.java:38-46`
- **维度**: D1（功能失效）/ D6（缓存无效导致重复执行）
- **证据**:
```java
@Override
public IServiceAction decorate(IServiceAction action) {
    return (request, selection, ctx) -> {
        Object value = getCachedValue(request, ctx);
        if (value == null)
            return action.invoke(request, selection, ctx);

        return action.invoke(request, selection, ctx);   // 命中缓存也与未命中完全相同
    };
}
```
- **现状**: `getCachedValue` 计算出的缓存命中值 `value` 从未被返回；整个类中也没有任何 `cache.put(...)` 调用，即 biz action 结果从不写入缓存。上游 entropy-cloud/nop-entropy master 分支同文件代码一致（已联网核对），属于长期存在的缺陷而非本地误改。
- **风险**: 一旦装配 `CacheActionDecoratorCollector`（xbiz `<cache>` 配置或 `@Cache` 注解），`@BizCache` 功能完全不生效：读缓存永远 miss、永远回源执行、也永远不填充缓存。配置方以为有缓存保护的高频查询实际每次全量执行，属于静默的性能契约违约；`CacheEvictActionDecorator` 的失效逻辑也因此失去意义。
- **建议**: 命中分支改为 `return value;`（或按缓存约定包装 CompletionStage），并在未命中执行后通过 `cache.put(key, result)` 写入；同时补一个"装饰后第二次调用不触达底层 action"的单测。
- **误报排除**: 已核对装配链路 `BizObjectBuildHelper.buildDecorators → CacheActionDecoratorCollector.collectDecorator` 确实会把该装饰器套到 action 上（收集器经 beans.xml `by-type: IActionDecoratorCollector` 注入）；也确认本仓库默认 beans.xml 未注册该收集器（默认装配下不触发），但该类是公开扩展点，应用注册后即命中缺陷，代码层面两个分支相同无可辩解。

> **处置（fix-ai-check 分支，2026-08-22）**: 已修复。命中分支改为直接返回缓存值；未命中回源执行后写入缓存（异步 CompletionStage 结果经 thenApply 在正常完成后写入；null 结果不写缓存——底层 Caffeine 缓存不支持 null 值，且 get 返回 null 即 miss，语义一致）；key 为 null 时跳过缓存直接回源。新增 `TestCacheActionDecorator`（4 用例：命中不触达底层 action / 回源写缓存 / null key 旁路 / 异步结果完成后入缓存）。红验证：修复前 3 处失败形态与审计一致（第二次调用仍回源 `expected: <result-1> but was: <result-2>`、缓存永不写入为 null、异步结果不入缓存）。

### [P1] copyForNew 对启用逻辑删除的实体必然抛 ERR_BIZ_ENTITY_ALREADY_EXISTS

- **文件**: `nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/CrudBizModel.java:1673-1686, 690-718`
- **维度**: D1（CRUD 逻辑错误）/ D8（公开行为与契约不符）
- **证据**:
```java
// doCopyForNew: data 中必然携带源记录 id（下一行 requireEntityById 依赖它）
Object id = data.get(OrmConstants.PROP_ID);
T entity = dao.requireEntityById(id);
...
EntityData<T> entityData = buildEntityDataForSave(data, inputSelection, context);  // 传入原始 data

// buildEntityDataForSave → recoverLogicalDeleted → findLogicalDeleted:
Object id = getId(data, dao);
if (id != null) { id = dao.castId(id); entity = dao.getEntityById(id); }
if (entity != null) {
    if (!entity.orm_logicalDeleted()) {
        throw new NopException(ERR_BIZ_ENTITY_ALREADY_EXISTS)...   // 源记录未删除 → 直接抛错
    }
}
```
- **现状**: `doCopyForNew` 把携带"存活的源记录 id"的原始 `data` 传给 `buildEntityDataForSave`；`findLogicalDeleted` 按该 id 取到存活实体后抛"记录已存在"。该检查对 `save()` 是正确语义（用已存在 id 新增应报错），但复制新建场景源 id 天然指向存活记录。已验证 `OrmEntityDao.getEntityById → orm().get` 按 PK 直取、不过滤逻辑删除（`OrmSessionImpl.get` 仅对 isGone 返回 null），因此存活源记录一定被取到并触发异常。
- **风险**: 所有配置了 `deleteFlag`（`isUseLogicalDelete()==true`）的实体（如 nop-file 的 NopFileRecord，nop-auth 多表）调用 `copyForNew` 100% 失败，复制新建功能整体不可用；错误码还会误导排障方向（提示记录已存在而非复制流程缺陷）。
- **建议**: `doCopyForNew` 在调用 `buildEntityDataForSave` 前从 `data` 中移除 id（或给 `buildEntityDataForSave` 增加 skipRecover 参数），与现有 `entityData.getValidatedData().remove(OrmConstants.PROP_ID)` 的意图对齐；补一条"逻辑删除实体 copyForNew 成功"的回归测试。
- **误报排除**: 已确认 `dao.requireEntityById(id)` 要求 id 必须存在且指向源记录，排除"data 不带 id"的正常路径；已验证 `OrmSessionImpl.get` 返回存活实体（非 null、非 missing），异常分支必然到达；`findLogicalDeleted` 仅在 `dao.isUseLogicalDelete()` 为 true 时调用，非逻辑删除实体不受影响（这与该功能平时"看起来能用"并不矛盾）。

> **处置（fix-ai-check 分支，2026-08-22）**: 已修复。`buildEntityDataForSave` 增加 4 参重载（新增 `recoverDeleted` 参数，原 3 参签名与 @BizAction 保持不变以兼容子类/xbiz 覆写），`doCopyForNew` 改传 `recoverDeleted=false`，复制新建不再按源 id 触发逻辑删除恢复检查；validated data 中移除 id 的既有逻辑保留。新增 `TestCrudBizModelCrudFlow.testCopyForNewOnLogicalDeleteEntity`（fake dao `isUseLogicalDelete=true` + 存活源实体）。红验证：修复前该测试以 `nop.err.biz.entity-already-exists, id=src-1` 失败，与审计推演完全一致。

### [P2] doCopyForNew 使用的 OrmEntityCopier 未设置 delayedActions/context，writeMode=BIZ 的关联被静默丢弃

- **文件**: `nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/CrudBizModel.java:1693, 1707`；`nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/OrmEntityCopier.java:434-439`
- **维度**: D1（数据丢失）/ D8（与 doSave 行为不一致）
- **证据**:
```java
// CrudBizModel.doCopyForNew（两处均用单参重载，无 context/delayedActions）:
crudToolProvider.newOrmEntityCopier(objMeta).copyToEntity(entity, newEntity, inputSelection, ...);

// OrmEntityCopier.collectRelationBizAction:
if (delayedActions == null || bizObjectManager == null || context == null) {
    return;   // BIZ 模式关联被静默跳过
}
```
- **现状**: `doSave/doUpdate/assignToEntity` 都通过 `newOrmEntityCopier(objMeta, context, entityData.getDelayedActions())` 传递延迟动作并调用 `executeDelayedRelationActions`；`doCopyForNew` 两处 copier 均用单参重例，且方法内无 `executeDelayedRelationActions` 调用。
- **风险**: 对配置了 `writeMode="biz"` 的关联属性（commit 3b2d3b513 新增能力），复制新建时该关联数据被静默丢弃，无报错无日志，产生缺数据的副本；与 save 路径行为不一致，属契约漂移。
- **建议**: doCopyForNew 改用带 context/delayedActions 的 copier 重载，并在 `doSaveEntity` 前执行 `executeDelayedRelationActions(entityData, context)`。
- **误报排除**: 已通读 OrmEntityCopier 全文确认 `copyRefEntity/copyRefEntitySet` 在 writeMode==BIZ 时唯一出口就是 `collectRelationBizAction`，其守卫条件在单参构造下恒成立；非 BIZ 模式关联不受影响。

> **处置（fix-ai-check 分支，2026-08-22）**: 已修复。`doCopyForNew` 两处 copier 均改用 `newOrmEntityCopier(objMeta, context, entityData.getDelayedActions())` 重载，并在 `doSaveEntity` 前调用 `executeDelayedRelationActions(entityData, context)`，与 doSave/doUpdate 路径对齐（autoExpr 的 action 名仍为 copyForNew，不变更 when 语义）。新增 `TestCrudBizModelCrudFlow.testCopyForNewExecutesBizRelationAction`（writeMode=biz 的 to-one 关联 payload，断言目标 BizObject 收到 update 调用且回填引用）。红验证（分阶段）：在仅打 P1-2 补丁（剥离源 id）但保留单参 copier 的中间态下，该测试以 `expected: <1> but was: <0>` 失败（关联被静默丢弃），证实本项独立缺陷。

### [P2] OrmEntityCopier.copyToEntity 在 objMeta==null 且目标实体多租户时 NPE

- **文件**: `nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/OrmEntityCopier.java:131-139`
- **维度**: D1（NPE）
- **证据**:
```java
IEntityModel entityModel = target.orm_entityModel();
Set<String> ignoreAutoExprProps = new HashSet<>();
if (entityModel.getTenantPropId() > 0) {
    String tenantProp = entityModel.getTenantColumn().getName();
    IObjPropMeta propMeta = objMeta.getProp(tenantProp);   // objMeta 为 null 时 NPE
```
- **现状**: 同方法内其他位置对 objMeta 均做 null 防护（如 `getProp(objMeta, name)` 与行 204 `if (objMeta != null) AutoExprRunner...`），唯独租户列处理未防护。objMeta 为 null 的来源：公开 4 参重载 `copyToEntity(src, target, selection, action)` 固定传 null（本仓库暂无调用方，属公开 API 契约）；以及 242/246 行递归时 `getPropSchema` 可返回 null（propMeta 为 null 或 schema 未解析出时，见 BizSchemaHelper.getPropSchema 63-83 行）。
- **风险**: 关联子实体为多租户表且 schema 未解析时，保存主对象在拷贝关联阶段抛裸 NPE（500），错误信息无法定位业务字段。
- **建议**: 行 135 前补 `objMeta == null` 判断（null 时将 tenantProp 直接加入 ignoreAutoExprProps 或跳过检查），与同方法其余防护保持一致。
- **误报排除**: 已核对 `BizSchemaHelper.getPropSchema` 存在多个返回 null 的分支；已确认递归调用点 (303/314/542/551) 直接把可空的 subSchema 作为 objMeta 传入； CrudBizModel 自身调用传 requireObjMeta() 非空，故标注为条件触发而非必现。

> **处置（fix-ai-check 分支，2026-08-22）**: 已修复。租户列处理块前置 `objMeta != null` 判断（objMeta 为 null 时跳过租户 autoExpr 抑制检查，与同方法 `getProp(objMeta, name)`、行 204 的 null 防护一致；objMeta 非空时行为不变）。新增 `TestOrmEntityCopierWriteMode.testCopyToEntityWithNullObjMetaOnTenantEntityDoesNotNpe`（tenantPropId=1 + objMeta=null 的目标实体拷贝）。红验证：修复前以 `NullPointerException: Cannot invoke "IObjSchema.getProp(String)" because "objMeta" is null` 失败，与审计证据一致。

### [P2] doFindTreeEntityList 内部使用 findTreePage 作为数据权限 action，findTreeList 规则不被应用

- **文件**: `nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/CrudBizModel.java:1824-1841`
- **维度**: D5（数据权限）/ D8（契约不一致）
- **证据**:
```java
public List<T> findTreeEntityList(...) {
    return doFindTreeEntityList(query, getAuthObjName(METHOD_FIND_TREE_LIST), null, selection, context);
}
public List<StdTreeEntity> doFindTreeEntityList(..., String authObjName, ...) {
    ...
    query = prepareFindPageQuery(query, authObjName, METHOD_FIND_TREE_PAGE, prepareQuery, context);  // 硬编码 TREE_PAGE
```
- **现状**: `prepareFindPageQuery` 会以 action 参数调用 `AuthHelper.appendFilter(checker, query, authObjName, action, context)`，而 `IDataAuthChecker.getFilter(bizObjName, action, context)` 按 (bizObj, action) 匹配数据权限规则（已在 nop-biz-auth-api/AuthHelper.java:27-41 核实）。list 类入口（findTreeEntityList / findListForTree / doFindListForTree）对外宣称 findTreeList，实际规则查的是 findTreePage。
- **风险**: 开启数据权限（`nop.auth.enable-data-auth=true`）的应用若为 `findTreeList` 配置了行级规则，将不生效，树列表查询可能返回越权数据；同时 `query.setName` 记录的 action 名也与真实入口不符，影响审计日志归因。
- **建议**: 行 1838 改为按入口传入 action（增加参数或使用 METHOD_FIND_TREE_LIST），与 `findList/findPage` 各自使用自身 action 的惯例对齐。
- **误报排除**: 已核实分页入口 `doFindTreeEntityPage`（1791 行）使用 TREE_PAGE 是正确的，仅 list 入口存在错位；已确认 BizConstants 中同时定义了 METHOD_FIND_TREE_LIST 与 METHOD_FIND_TREE_PAGE 两个常量（非笔误复用）。

> **处置（fix-ai-check 分支，2026-08-22）**: 已修复。`doFindTreeEntityList` 内部 `prepareFindPageQuery` 的 action 参数由 `METHOD_FIND_TREE_PAGE` 改为 `METHOD_FIND_TREE_LIST`，与 `findTreeEntityList/findListForTree` 入口声明的 action 一致；分页入口 `doFindTreeEntityPage/doFindPageForTree` 保持 TREE_PAGE 不变。新增 `TestCrudBizModelBatchAndTreeQuery.testFindTreeListUsesFindTreeListAuthAction`（fake IDataAuthChecker 捕获 getFilter 的 action + 捕获 query.name）。红验证：修复前以 `expected: <TestTreeObj.findTreeList> but was: <TestTreeObj.findTreePage>` 失败。注意：这是权限语义收敛的行为变更——此前按 findTreePage 配置的行级规则在 list 入口生效，修复后按契约归位到 findTreeList（与 findList/findPage 各自使用自身 action 的平台惯例对齐）。

### [P2] batchUpdate 未同步修复 batchGetEntitiesByIds 可能返回 null 元素的同族 NPE

- **文件**: `nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/CrudBizModel.java:1293-1299`
- **维度**: D1（NPE）
- **证据**:
```java
List<T> entityList = ignoreUnknown ?
        dao().tryBatchGetEntitiesByIds(ids) : dao().batchGetEntitiesByIds(ids);
for (T entity : entityList) {
    Map<String, Object> copy = new LinkedHashMap<>(data);
    copy.put(GraphQLConstants.PROP_ID, entity.orm_idString());   // entity 为 null 时 NPE
    update(copy, context);
}
```
- **现状**: commit 8c14c0270（2026-08-08）为 `batchDelete` 添加了 `if (entity == null) { ret.add(null); continue; }`，其提交说明明确"batchGetEntitiesByIds 对不存在的 id 可能返回 null 元素，遍历时触发 NPE"。`batchUpdate` 存在完全相同的遍历模式，但未同步修复。
- **风险**: 在会返回 null 元素的 dao 实现下（维护者已确认该行为存在），`batchUpdate` 携带不存在 id 时抛裸 NPE（500），而非 UnknownEntityException 的规范错误；`batchModify` 的预加载结果被丢弃不受影响（已核对）。
- **建议**: 与 batchDelete 一致地跳过 null（ignoreUnknown=true 时）或转换为 UnknownEntityException（false 时）；更彻底的做法是在 dao 层保证不返回 null 元素。
- **误报排除**: 已核对默认 `OrmEntityDao.batchGetEntitiesByIds` 单 id 分支经 makeProxy 理论上非 null，故降级为 P2 而非 P1；但维护者修复提交是"该返回可含 null"的权威证据，属同族未修复缺陷。

> **处置（fix-ai-check 分支，2026-08-22）**: 已修复。与 batchDelete 的防御对齐：`ignoreUnknown=true` 时跳过 null 元素；`ignoreUnknown=false` 时转换为 `UnknownEntityException`（默认 dao 实现不返回 null，此分支仅针对会返回 null 的自定义 dao 实现；null 元素无对应 id 可标注，entityName 随异常给出）。新增 `TestCrudBizModelBatchAndTreeQuery.testBatchUpdateSkipsNullEntityWhenIgnoreUnknown` / `testBatchUpdateThrowsUnknownEntityWhenRequire`。红验证：修复前两用例均以 `NullPointerException: Cannot invoke "IOrmEntity.orm_idString()" because "entity" is null` 失败，与审计推演一致。

### [P2] 多对多关联三个 mutation 仅做 METHOD_GET 数据权限检查，缺少变更类权限校验

- **文件**: `nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/CrudBizModel.java:1591-1631`
- **维度**: D5（数据权限）
- **证据**:
```java
@BizMutation
public void addManyToManyRelations(@Name("id") String id, @Name("propName") String propName, ...) {
    T entity = get(id, false, context);          // 仅 METHOD_GET 数据权限
    ...
    tool.addRelations(leftValue, relValues, filter);   // 直接写中间表
```
- **现状**: `addManyToManyRelations/removeManyToManyRelations/updateManyToManyRelations` 通过 `get()` 完成 METHOD_GET 行级检查后，直接用 ManyToManyTool 增删中间表记录，全程没有 METHOD_UPDATE（或专用 action）的 `checkDataAuth`。对比同类入口：`doUpdateEntity` 链路有 requireEntity(METHOD_UPDATE) + checkDataAuthAfterUpdate，`doDeleteEntity` 有 checkDataAuth(METHOD_DELETE)。传给 tool 的 `filter` 参数是客户端提交的选择过滤，不是权限过滤。
- **风险**: 开启数据权限后，对某实体仅有读权限（无 update 权限）的用户可以增删该实体的多对多关联（例如给自己挂上角色/资源关系），绕过行级写权限模型。nop-auth 侧 MfaSensitiveTableBizModel 的注释也确认这三个动作不走 CrudBizModel 常规 mutation 面，属基类权限覆盖的空白区。
- **建议**: 三个方法在执行 tool 操作前增加 `checkDataAuth(BizConstants.METHOD_UPDATE, entity, context)`（或引入独立 action 名便于规则配置）。
- **误报排除**: 已确认 GraphQL 层函数级权限（bizObj__action 的 auth 配置）与行级数据权限是两套机制，此处缺失的是后者；已核对 `checkDataAuth` 在本类的所有其他 mutation 路径均有调用，唯独这三个方法没有。

> **处置（fix-ai-check 分支，2026-08-22）**: 已修复。三个方法在 `get()` 之后、调用 ManyToManyTool 之前统一增加 `checkDataAuth(BizConstants.METHOD_UPDATE, entity, context)`，与 doUpdateEntity 链路（requireEntity(METHOD_UPDATE)+checkDataAuth）的行级写权限语义对齐。新增 `TestCrudBizModelCrudFlow.testManyToManyMutationsCheckUpdateDataAuth`（fake checker 计数 get/update 检查 + 记录 tool 调用）。红验证：修复前以 `expected: <3> but was: <0>`（updateChecks）失败。注意：这是权限收紧的行为变更——开启数据权限后，此前仅有读权限的用户对这三个 mutation 的调用将开始被拒绝（这正是缺陷要修复的语义）。

### [P2] updateByQuery/deleteByQuery/asDict 被 maxPageSize 静默截断，返回计数误导调用方

- **文件**: `nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/CrudBizModel.java:1433-1455, 1470-1493, 1505-1534`；截断逻辑在 391-408 行
- **维度**: D8（契约漂移）/ D6
- **证据**:
```java
// prepareFindPageQuery 内:
if (query.getLimit() <= 0) { query.setLimit(maxPageSize); }
if (query.getLimit() > maxPageSize) { query.setLimit(maxPageSize); }
// doUpdateByQuery:
List<T> list = doFindList0(query, authObjName, prepareQuery, null, context);  // 最多 maxPageSize（默认1000）行
doUpdateMulti(list, data, prepareUpdate, context);
return list.size();   // 返回"已更新数"，调用方无法感知被截断
```
- **现状**: `updateByQuery/deleteByQuery` 经 `doFindList0 → prepareFindPageQuery` 受 `nop.graphql.max-page-size`（默认 1000，见 nop-graphql-core GraphQLConfigs:45）封顶；`asDict` 也自行 `query.setLimit(getMaxPageSize())`。超过上限的剩余行不会被处理，也不产生任何告警。
- **风险**: 命中超过 1000 行的批量修改/删除只处理前 1000 行且返回 1000，调用方据此认为已完成（例如数据订正任务），造成静默的部分生效；字典在超过上限时缺项，前端选项不完整且无提示。
- **建议**: 对批量 mutation 要么循环分页处理直到取空，要么在返回值/日志中显式标记"结果被截断"（抛错或返回 `truncated` 标志）；asDict 至少记录 warn。
- **误报排除**: 已核实 `doFindListByQueryDirectly → dao().findPageByQuery(query)` 确实按 limit 取数；已核实 `getMaxPageSize` 只会放大不会缩小上限；offset 语义保留意味着调用方可手动分页，但单次调用契约仍具误导性。

> **处置（fix-ai-check 分支，2026-08-22）**: 部分修复 + 暂缓。已落地：`doUpdateByQuery/doDeleteByQuery` 在取满 limit（`list.size() >= query.getLimit()`）时输出 WARN 日志（nop.biz.update-by-query-result-truncated / delete-by-query-result-truncated，含 bizObjName/limit/returned）；`asDict` 在选项数达到分页上限时输出 WARN（nop.biz.as-dict-options-truncated），静默截断变为可观测。纯日志增强无行为语义变化，免新测试（日志断言不属于模块既有测试实践）。暂缓（需设计决策）：剩余语义选项——(a) 循环分页处理直到取空（delete 场景需处理行集漂移，adversarial 场景有死循环风险）；(b) 超限即抛错（破坏现存"部分处理也是处理"的调用方预期）；(c) 返回值附 truncated 标志（公共 GraphQL API 返回类型变更，需跨模块契约裁定）。决策点归属 CrudBizModel 公共 API 契约维护者；影响面：所有超过 maxPageSize 的批量订正调用与超大字典表。

### [P3] DevStat.clearStats 以 @BizQuery 暴露破坏性操作，且 DevStat 默认注册

- **文件**: `nop-service-framework/nop-biz/src/main/java/io/nop/biz/dev/DevStatBizModel.java:19-23`
- **维度**: D5/D8
- **证据**:
```java
@BizQuery
@Description("清空所有统计信息")
public void clearStats() {
    GlobalStatManager.instance().clear();
}
```
- **现状**: 清空全部运行统计是变更操作，却挂在 `@BizQuery`（语义只读，可经 GET 类查询触发）。且 `biz-defaults.beans.xml` 中 DevStat 以 `nop.biz.stat.enabled enableIfMissing="true"` 默认启用（对比 DevDoc/DevTool 仅 `nop.debug` 时注册）。
- **风险**: 只读通道即可触发统计清空；若应用未单独配置函数级权限，任意已登录用户可清空运维依赖的 SQL/RPC 统计。同模型 `jdbcSqlStats` 还会暴露 SQL 语句模板。
- **建议**: 改为 `@BizMutation`，并考虑与 DevDoc/DevTool 一样用开关控制注册或要求管理端权限。
- **误报排除**: 已核对 beans.xml 注册条件（biz-defaults.beans.xml:131-135）；已确认 nop 平台 @BizQuery/@BizMutation 分别映射 GraphQL query/mutation 通道。

> **处置（fix-ai-check 分支，2026-08-22）**: 已修复。`clearStats` 注解由 `@BizQuery` 改为 `@BizMutation`（破坏性操作回归 mutation 通道），三个统计查询保持 @BizQuery 不变。新增 `TestDevStatBizModel`（反射断言 clearStats 为 @BizMutation、统计查询仍为 @BizQuery）。红验证：修复前注解断言失败（expected: not <null>）。遗留产品决策（不在本次范围）：DevStat 默认注册（nop.biz.stat.enabled enableIfMissing=true）与 jdbcSqlStats 暴露 SQL 模板的治理（对齐 DevDoc/DevTool 的 nop.debug 门控或强制管理端权限）需产品层裁定，未改动装配条件。

### [P3] ObjMetaBasedValidator 对 simple schema 的自定义 validator 重复执行两次

- **文件**: `nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/ObjMetaBasedValidator.java:398-411`
- **维度**: D1/D6
- **证据**:
```java
if (schema.isSimpleSchema()) {
    SimpleSchemaValidator.INSTANCE.validate(schema, null, bizObjName, ...);
}

if (schema.getValidator() != null) {
    // SimpleSchemaValidator中会调用validator, 因此这里判断else if即可
    schema.getValidator().call1(null, value, scope);   // 注释说明应else if，代码却仍是独立if
}
```
- **现状**: 已核实 `SimpleSchemaValidator`（nop-xlang，78-80 行）内部会调用 `schema.getValidator()`；此处再调用一次，simple schema 且带 validator 的属性每次保存校验执行两遍。
- **风险**: 带副作用或耗时（查库、远程调用）的自定义 validator 双倍执行；注释与代码相悖，维护者意图未被实现。
- **建议**: 按注释改为 `else if`，并补一条"validator 只调用一次"的单测（可用计数器式 validator）。
- **误报排除**: 已读 SimpleSchemaValidator 源码确认内部确实调用 getValidator；非 simple schema 路径不受影响（仅执行一次）。

> **处置（fix-ai-check 分支，2026-08-22）**: 已修复。按注释意图改为 `else if`（simple schema 时 validator 已由 SimpleSchemaValidator 内部执行并经 ThrowValidationErrorCollector 抛错，非 simple schema 时直接执行一次）。null 值不受影响：`_validate` 对空值在调用 validateValue 前已短路（setIn(ret, ..., null) + continue），validateValue 只会收到非空值。新增 `TestObjMetaBasedValidator.testSimpleSchemaValidatorExecutedOnlyOnce`（计数器 validator）。红验证：修复前以 `expected: <1> but was: <2>` 失败。

### [P3] BizObjectImpl.method_invoke 两参调用抛出语义相反的错误（too-many-action-args）且用裸 IllegalArgumentException

- **文件**: `nop-service-framework/nop-biz/src/main/java/io/nop/biz/impl/BizObjectImpl.java:251-273`
- **维度**: D4/D7
- **证据**:
```java
} else if (args.length > 1) {
    ...
    if (args.length > 2) {
        context = (IServiceContext) args[2];
    } else {
        throw new IllegalArgumentException("nop.err.graphql.too-many-action-args:bizObjName=" + ...
```
- **现状**: 以 `(data, selection)` 两参调用代理方法时进入 `else` 抛"参数过多"，实际是缺少 context；消息采用 `nop.err.*` 字符串拼进 IllegalArgumentException，而非 NopException + ErrorCode（违背模块公共 API 错误规范，D7）。
- **风险**: 排障方向被误导；错误码字符串无法被 i18n/错误码体系管理。行 272 的 `no-svc-context` 同样是裸 IllegalArgumentException。
- **建议**: 定义对应 ErrorCode 并改用 NopException；两参场景报"缺少服务上下文"而非"参数过多"。
- **误报排除**: 已核对三参 `(data, selection, context)` 与单参 + scope context 两条正常路径均可走通，仅两参路径异常；确认这是 IMethodMissingHook 代理调用协议的一部分而非不可达代码。

> **处置（fix-ai-check 分支，2026-08-22）**: 已修复。两处裸 IllegalArgumentException 均改为 NopException + 新增错误码 `ERR_BIZ_ACTION_NO_SVC_CONTEXT`（nop.err.biz.action-no-svc-context，BizErrors；该 Errors 类未注册进 nop-cli-errors i18n 清单，无需 i18n 同步）；两参场景错误语义修正为"缺少 IServiceContext 参数"（原来误报 too-many-action-args），context 为 null 场景复用同一错误码。新增 `TestBizObjectImplMethodInvoke`（3 用例，含三参正常路径回归）。红验证：修复前两用例分别以 `IllegalArgumentException: nop.err.graphql.too-many-action-args...` / `nop.err.graphql.no-svc-context...` 失败，与审计证据逐字一致。

### [P3] isAllowGetDeleted 未对 getObjMeta() 判空，deleted_get/deleted_findPage/recoverDeleted 在无 xmeta 对象上 NPE

- **文件**: `nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/CrudBizModel.java:1008-1010`
- **维度**: D1（NPE 防护不一致）
- **证据**:
```java
protected boolean isAllowGetDeleted() {
    return ConvertHelper.toPrimitiveBoolean(getThisObj().getObjMeta().prop_get(BizConstants.BIZ_ALLOW_GET_DELETED));
}
```
- **现状**: 同类的 `getMaxPageSize`、`checkAllowQuery`、`getDefaultRefNamesToCheckExists` 均先判空 objMeta，唯独此处直接解引用。BizObjectBuilder 允许仅有 xbiz 无 xmeta 的业务对象（objMeta 可为 null）。
- **风险**: 无 xmeta 的对象调用 `deleted_get/deleted_findPage/recoverDeleted` 抛 NPE 而非业务错误。
- **建议**: 与相邻方法一致地判空（null 时按 false 处理）。
- **误报排除**: 已核对 BizObjectBuilder.loadBizObjFromModel 存在 objMeta==null 的合法装配路径（bizModel 非 null 且未配置 meta 时并不总是抛错）。

> **处置（fix-ai-check 分支，2026-08-22）**: 已修复。`isAllowGetDeleted` 对 `getObjMeta()` 判空，null 时返回 false（与 `getMaxPageSize`、`getDefaultRefNamesToCheckExists` 的防护一致），无 xmeta 对象调用 `deleted_get/deleted_findPage/recoverDeleted` 时抛 ERR_BIZ_NOT_ALLOW_GET_DELETED 业务错误而非 NPE。新增 `TestCrudBizModelCrudFlow.testDeletedGetWithoutObjMetaReturnsBizError`。红验证：修复前以 `NullPointerException: ... "IObjMeta.prop_get(String)" because "IBizObject.getObjMeta()" is null` 失败。

### [P3] batchDelete 修复后向返回集合写入 null 元素

- **文件**: `nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/CrudBizModel.java:1310-1314`
- **维度**: D8（返回契约）
- **证据**:
```java
for (T entity : entities) {
    if(entity == null) {
        ret.add(null);        // Set<String> 中放入 null
        continue;
    }
```
- **现状**: commit 8c14c0270 为防 NPE 将 null 实体映射为返回集中的 `null` 元素；返回值是"未能删除的 id 集合"，null 元素无法对应任何 id。
- **风险**: GraphQL/JSON 序列化产出 `[null]`，客户端与统计逻辑易再次 NPE 或误判；调用方无法区分"哪个 id 失败"。
- **建议**: null 实体应映射回原始 ids 中的对应值（或直接过滤并以日志记录），避免向契约中引入 null。
- **误报排除**: 已核对方法语义（返回未删除/缺失 id 集合）与 LinkedHashSet 允许 null 的行为；确认缺失 id 在输入 `ids` 中可得。

> **处置（fix-ai-check 分支，2026-08-22）**: 已修复。null 实体不再映射为返回集中的 null 元素：遍历时跳过 null 并记录成功删除的 id，最后按原始入参 ids 补记"未返回且未删除"的缺失 id（顺序无关，与 batchDelete 自身语义"返回未删除的 id 集合"精确对齐）。新增 `TestCrudBizModelBatchAndTreeQuery.testBatchDeleteMapsMissingIdsBackToInput`（[存在, null, missing] → 返回 {2,3} 无 null，"1" 被删除）。红验证：修复前以 `expected: <false> but was: <true>`（ret.contains(null)）失败。

### [P3] DownloadHelper 在 newZipOutput 抛错时底层 OutputStream 文件描述符泄漏

- **文件**: `nop-service-framework/nop-biz/src/main/java/io/nop/biz/download/DownloadHelper.java:24-45`
- **维度**: D2（资源泄漏）
- **证据**:
```java
OutputStream os = resource.getOutputStream();     // 已打开流
zipOutput = zipTool.newZipOutput(os, zipOptions); // 若此处抛错，os 未关闭
...
} catch (Exception e) {
    IoHelper.safeCloseObject(zipOutput);          // 此时 zipOutput 仍为 null
    resource.delete();
    throw NopException.adapt(e);
}
```
- **现状**: `getOutputStream()` 成功而 `newZipOutput(os, ...)` 抛错时，catch 只关闭 null 的 zipOutput 并删文件，`os` 保持打开直到 GC。
- **风险**: 异常路径下文件描述符泄漏；高频触发（如 zip 工具初始化失败）时可累积句柄。正常路径关闭顺序（flush → 构造 bean → safeClose）无误。
- **建议**: 将 `os` 提升为方法级变量并在 catch 中一并用 `IoHelper.safeCloseObject(os)` 关闭。
- **误报排除**: 已核对正常路径与 `getOutputStream()` 自身抛错路径均无泄漏，仅 `newZipOutput` 抛错这一窗口受影响，故定 P3。

> **处置（fix-ai-check 分支，2026-08-22）**: 已修复。`os` 提升为方法级变量，catch 中追加 `IoHelper.safeCloseObject(os)`；同时抽取包内可见重载 `downloadZip(resource, zipTool, ...)` 供单测注入 fake zipTool（公开 API 签名不变）。新增 `TestDownloadHelper.testNewZipOutputFailureClosesUnderlyingStream`（跟踪流关闭状态 + newZipOutput 抛错）。红验证：修复前以 `underlying output stream should be closed on failure ==> expected: <true> but was: <false>` 失败。

### [P3] CrudBizModel.resolveQuery 死代码：空 if 块且无调用方

- **文件**: `nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/CrudBizModel.java:363-368`
- **维度**: D1/D8（维护性）
- **证据**:
```java
protected QueryBean resolveQuery(QueryBean query) {
    if (query.getFilter() != null) {

    }
    return query;
}
```
- **现状**: 空 if 块无任何作用；模块内及全仓库均无调用方；query 为 null 时反而会 NPE。
- **风险**: 公共底座类中的迷惑性死代码，诱导子类覆写一个不生效的钩子。
- **建议**: 删除该方法，或实现其命名暗示的查询预处理逻辑。
- **误报排除**: 已全仓库 grep `resolveQuery(` 确认无调用方；确认非覆写父类方法（父接口无此签名）。

> **处置（fix-ai-check 分支，2026-08-22）**: 已修复。删除 `resolveQuery` 死方法（空 if 块、全仓库无调用方、不覆写任何父类型方法；删除后模块及相邻模块回归全绿）。纯死代码删除，无行为语义变化，免测试（无调用方即无可断言行为，全量回归即为验证）。

## 附注（已核查、判定为非问题的高风险点）

- `findRoots` 中 `FilterBeans.eq(parentProp, null)`：`FilterBeanToSQLTransformer`（nop-core:130-137）会将 eq+null 转换为 `is null`，不会产生恒假条件。
- `TreeEntityHelper` 手拼递归 CTE 未显式过滤 delFlag/租户：该 SQL 经 `orm().findPage` 走 EQL 翻译，`EqlTransformVisitor` 会按实体模型附加逻辑删除条件，不构成越权/脏数据问题。
- `BizActionService.newInvocation` 把 ServiceContextImpl 传给 `ICancelToken` 形参：`IExecutionContext extends ICancellable extends ICancelToken`，类型与语义均成立。
- D7 规范项：手写代码中无私有字段 `@Inject`（DevDocBizModel 的 package-private 字段注入合规）、无 Spring `@Value`/`org.springframework` 导入、无 bare `RuntimeException`、无 printStackTrace；空 catch 未发现。
