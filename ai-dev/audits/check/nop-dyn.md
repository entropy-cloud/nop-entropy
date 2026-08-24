# nop-dyn 实现代码检查报告

- 检查日期: 2026-08-19
- 模块路径: nop-dyn
- 文件数: 131（src/main/java，任务口径；实测 122 个非 target/非 _gen 文件：nop-dyn-api 45、nop-dyn-dao 35、nop-dyn-service 24、nop-dyn-app 1，另 nop-dyn-web/meta/codegen 无主代码 Java 文件）
- 覆盖范围声明: 全量 grep 扫描（空 catch、bare RuntimeException、printStackTrace、DDL 拼接、并发关键字、注入规范）后逐点 Read 验证。深读：dao/model 两个双向转换器、service/codegen 全部 7 个类（DynCodeGen/DynOrmModelProvider/InMemoryCodeCache/DynResourceStore/GptCodeGen/IDynCodeGenCacheHook）、service/entity 全部 15 个 BizModel、dao/entity 全部 16 个手写实体类、Configs/Constants/Errors/NopDynApplication。抽查：nop-dyn-api 生成契约（beans/crud 各 1 组）、biz 接口、beans.xml、NopDynModule.xbiz、xmeta、action-auth.xml。交叉验证涉及 nop-core（ResourceTenantManager/DeltaResourceStore/InMemoryResourceStore/ResourceTreeNode）、nop-orm-model（OrmEntityModelInitializer）、nop-biz（CrudBizModel）、nop-db-migration（CreateTableExecutor）。api 模块 45 个生成 bean 文件未逐行阅读（生成产物，抽查无异常）。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 3 |
| P2 | 9 |
| P3 | 3 |

## 发现列表

### [P1] 模块下架（unpublish/generateForApp）传入错误 key，InMemoryCodeCache 中模块无法被移除

- **文件**: `nop-dyn/nop-dyn-service/src/main/resources/_vfs/nop/dyn/model/NopDynModule/NopDynModule.xbiz:41-53`、`nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/codegen/DynCodeGen.java:207-219`、`nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/codegen/InMemoryCodeCache.java:121-142`
- **维度**: D1（正确性）/ D8（契约不一致）/ D5（越权）
- **证据**:
```java
// NopDynModule.xbiz unpublish：removeDynModule 形参是 String moduleId，却传入实体对象
const entity = thisObj.invoke("get",{id},null, svcCtx);
codeGen.removeDynModule(entity);   // entity 是 NopDynModule 对象，不是 moduleId 字符串

// DynCodeGen.generateForApp：传入 moduleName 而缓存 key 是 nopModuleId
if (module.getStatus() != NopDynDaoConstants.MODULE_STATUS_PUBLISHED) {
    codeCache.removeModule(module.getModuleName());   // key 应为 module.getNopModuleId()
} else { ... }

// InMemoryCodeCache.addModule 以 moduleId 为 key
removeModule(module.getModuleId());
enabledModules.put(module.getModuleId(), module);
```
- **现状**: `removeDynModule(String moduleId)` 的两个调用方都传错 key。xbiz `unpublish` 传入 NopDynModule 实体（类型不匹配：转换失败则抛错，即便容错转成 toString 也非合法 moduleId）；`generateForApp` 传入 `moduleName`，而 `ResourceHelper.getModuleIdFromModuleName` 会把 `-` 替换为 `/`（moduleName `app-demo-test` 的缓存 key 是 `app/demo/test`），模块名不含 `-` 时恰好相等掩盖了问题。
- **风险**: 下架模块后 `enabledModules`/`bizModels`/`ormModels`/`moduleStores` 中的模块条目不被移除（`removeModule` 对未知 key 静默 no-op），已下架模块的 GraphQL 接口、页面、ORM 实体在缓存刷新前继续对外可用——下架操作形同虚设，存在已收回功能被继续访问的风险。
- **建议**: unpublish 改为 `codeGen.removeDynModule(entity.getNopModuleId())`；generateForApp 改为 `codeCache.removeModule(module.getNopModuleId())`。
- **误报排除**: 已核对 `buildModuleModel`（`ret.setModuleId(module.getNopModuleId())`）与 `ResourceHelper.getModuleIdFromModuleName`（`moduleName.replace('-','/')`），确认 key 体系确为 moduleId；`removeModule` 对不存在的 key 直接 return，不会抛错提示。

> **处置（fix-ai-check 分支，2026-08-24）**: 已修复。`DynCodeGen.generateForApp` 改为 `codeCache.removeModule(module.getNopModuleId())`；xbiz `unpublish` 改为 `codeGen.removeDynModule(entity.nopModuleId)`。测试：`nop-dyn-service` `TestDynCodeGenModuleLifecycle#testGenerateForAppRemovesUnpublishedModule`（moduleName=app-demo/nopModuleId=app/demo，发布入缓存后转未发布并走 generateForApp）。红验证：HEAD 上失败于 `未发布模块必须从缓存中移除 ==> expected: <false> but was: <true>`——removeModule("app-demo") 对以 "app/demo" 为 key 的条目静默 no-op，下架后模块条目仍完整保留在 enabledModules/bizModels/moduleStores 中；修复后条目被移除。xbiz 侧为 XPL 脚本一行改动，无独立单测（需完整 GraphQL 服务栈），key 语义与 Java 侧同源（`removeDynModule(String moduleId)` 形参即 nopModuleId）。

### [P1] InMemoryCodeCache 锁顺序反转：getOrmModel 的 computeIfAbsent 与 removeModule 可互相死锁

- **文件**: `nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/codegen/InMemoryCodeCache.java:301-304`（对照 129-142、192-206）
- **维度**: D3（并发）
- **证据**:
```java
// 线程 A：查询触发的动态实体模型懒加载（无外层锁）
public OrmModel getOrmModel(ModuleModel module, boolean formatGenCode) {
    String moduleId = module.getModuleId();
    return ormModels.computeIfAbsent(moduleId, k -> genOrmModel(formatGenCode, module)); // bin 锁内调 synchronized 方法
}

protected synchronized OrmModel genOrmModel(boolean formatGenCode, ModuleModel module) { ... } // 长 DB + 代码生成

// 线程 B：模块发布/移除
public synchronized void removeModule(String moduleId) {
    ...
    ormModels.remove(moduleId);  // 持 this 等 bin 锁
}
```
- **现状**: `ormModels` 是 ConcurrentHashMap。线程 A 在 `computeIfAbsent` 的 bin 锁内进入 `genOrmModel`（等待 `this` 监视器）；线程 B 持有 `this`（removeModule）后调用 `ormModels.remove`（等待同一 bin 锁）。锁获取顺序相反，JDK 21 的 CHM `computeIfAbsent` 在 mapping function 执行期间确持 bin 锁。
- **风险**: 查询流量（LazyLoadOrmModel 触发 `getDynamicEntityModel`→`getOrmModel`）与模块发布/下架（`generateForApp`/`generateForModule`→`addModule`→`removeModule`）并发时死锁，且 `genOrmModel` 含 DB 访问与代码生成，持锁时间长、窗口大。死锁后相关请求全部挂起。
- **建议**: 先在 `this` 上同步（或先 `get` 探测）再调用 `computeIfAbsent`，保证全局锁顺序为 `this → CHM`；或 `ormModels` 改用普通 HashMap 仅在 synchronized 方法内访问。
- **误报排除**: 已确认 `getOrmModel` 非 synchronized 且调用方 `DynCodeGen.getDynamicEntityModel`（161-173 行）无外层同步；`removeModule`/`addModule` 为 synchronized 且内部操作同一 `ormModels` map，ABBA 场景成立。

> **处置（fix-ai-check 分支，2026-08-24）**: 已修复。`getOrmModel` 加 `synchronized`，全局锁顺序统一为 `this → ormModels(CHM bin)`，与 removeModule/addModule/genOrmModel 等既有 synchronized 方法一致。测试：`TestInMemoryCodeCacheDeadlock#testGetOrmModelAndRemoveModuleNoDeadlock`——线程B removeModule 持 this 后阻塞在可控的 prepareUnloadModule 钩子（latch 门控），线程A getOrmModel 等待 monitor（两实现下均 BLOCKED），释放B后断言两线程 5s 内完成。红验证：HEAD 上失败于 `removeModule未卡死 ==> expected: <false> but was: <true>`——A 在 computeIfAbsent 的 bin 锁内等待 this、B 持 this 执行到 ormModels.remove 等待同一 bin 锁，ABBA 死锁真实复现（join 5s 超时线程仍存活）；修复后两线程正常完成。

### [P1] 租户模式下 getTenantResourceStore 返回恒为 null 的 mergedStore，租户动态资源层失效

- **文件**: `nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/codegen/DynCodeGen.java:186-188`（对照 351-361）、`nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/codegen/InMemoryCodeCache.java:104-106、290-293`
- **维度**: D1（正确性）/ D8（契约）
- **证据**:
```java
// DynCodeGen
public IResourceStore getTenantResourceStore(String tenantId) {
    return getTenantCodeCache(tenantId).getMergedStore();   // 直接读字段
}

// InMemoryCodeCache
public IResourceStore getMergedStore() {
    return mergedStore;   // 仅在 getResourceStore() 中被赋值；clearMergedStore() 到处置 null
}
void clearMergedStore() { this.mergedStore = null; this.dynResourceStore = null; }
```
- **现状**: `mergedStore` 只由 `getResourceStore()` 构建，而全模块（含测试）只有非租户路径 `reloadModel()`（DynCodeGen:355）调用它；租户初始化链 `initTenantCache → addModule → genModuleCoreFiles → clearMergedStore()` 只会把字段置 null。因此启用租户模式（`useTenant=true`）后 `ResourceTenantManager → DeltaResourceStore.getTenantStore0` 拿到的租户动态资源层恒为 null。
- **风险**: `DeltaResourceStore.getTenantStore0` 对 null 回退共享基础 store，租户动态模块的页面（.page.yaml/.view.xml）、xmeta/xbiz 等生成的 VFS 资源对租户不可见，租户动态实体读写链路失效；字段还非 volatile，存在可见性问题。
- **建议**: `getTenantResourceStore` 应返回 `getTenantCodeCache(tenantId).getResourceStore()`（惰性构建含 prepare 回调的 DynResourceStore），与 `reloadModel` 对齐。
- **误报排除**: 已全仓 grep 确认租户路径无任何 `getResourceStore()` 调用；`ResourceTenantManager.getTenantResourceStore` 直接透传 provider 结果、`DeltaResourceStore.getTenantStore0` 对 null 回退，链路核实。git 历史显示该写法自 e7e9de5f4 引入后未变。

> **处置（fix-ai-check 分支，2026-08-24）**: 已修复。`getTenantResourceStore` 改为 `getTenantCodeCache(tenantId).getResourceStore()`（惰性构建含 prepare 回调的 DynResourceStore，与 reloadModel 的非租户路径对齐；clearMergedStore 置空后下次访问自动重建）；`mergedStore`/`dynResourceStore` 补 volatile（与 P2-10 一并解决可见性）。测试：`TestInMemoryCodeCacheContract#testTenantResourceStoreLazilyBuilt`（直构 DynCodeGen + daoProvider/ormTemplate 桩走真实 initTenantCache→addEnabledModulesToCache 链）与 `#testFreshCacheMergedStoreNullUntilResourceStoreBuilt`（契约：新初始化 cache 的 getMergedStore 恒 null、getResourceStore 惰性构建、miss 时回调 prepare、clear 后重建）。红验证：HEAD 上 testTenantResourceStoreLazilyBuilt 失败于 `租户资源store必须惰性构建 ==> expected: not <null>`——租户初始化链只调 addModule（内部 clearMergedStore），mergedStore 从未被构建，直接返回该字段即 null。

### [P2] prepareResource/getObjMeta 对未注册 bizObj 缺 null 检查，任意猜测路径触发 NPE 500

- **文件**: `nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/codegen/DynCodeGen.java:453-483`、`nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/codegen/InMemoryCodeCache.java:208-211、276-277`
- **维度**: D1（NPE）/ D4（错误处理）
- **证据**:
```java
// DynCodeGen.prepareResource
String bizObjName = cache.getBizObjNameFromPagesPath(path);
if (bizObjName == null) return;          // 只检查了 bizObjName
GraphQLBizModel bizModel = cache.getBizModel(bizObjName);   // 未检查 null
...
cache.genPageFile(module, bizModel, pageName, formatGenCode); // bizModel.getBizObjName() NPE

// InMemoryCodeCache.getObjMeta
GraphQLBizModel bizModel = getBizModel(bizObjName);
String metaPath = ...;
IResource metaResource = getModuleResource(bizModel.getModuleId(), metaPath);  // NPE
```
- **现状**: `DynResourceStore.getResource` 在资源 miss 时回调 `prepareResource`（VFS in-memory 层的惰性生成入口）。路径 `/{moduleId}/pages/{bizObjName}/xxx.page.yaml` 中 bizObjName 只做路径解析，不校验是否注册于 `bizModels`。
- **风险**: 请求任意未注册 bizObj 的路径（模块未启用、刚被移除、用户猜测路径）时，资源查找以 NPE（500）中断而非正常 miss 回退/404；`.xmeta` 分支（458-461 行）与 `getObjMeta`（view.xml 分支 478 行）同样触发。
- **建议**: `getBizModel` 结果判空后直接 return；`genBizObjFiles` 入口对 null bizModel 抛带 errorCode 的 NopException。
- **误报排除**: 已确认 `DynResourceStore.getResource`（21-28 行）在 miss 时无条件 `prepare.accept(path)`，且 `getBizModel` 是纯 map 查询可返回 null。

> **处置（fix-ai-check 分支，2026-08-24）**: 已修复。`DynCodeGen.prepareResource` 的 .xmeta/.xbiz 分支与 pages 分支均对 `cache.getBizModel(bizObjName)` 判空后 return（资源 miss 语义，由上层正常回退/404）；`InMemoryCodeCache.getObjMeta` 判空返回 null；公共生成入口 `genBizObjFiles` 对 null bizModel 抛新错误码 `ERR_DYN_BIZ_MODEL_NOT_EXISTS`（防御性兜底）。测试：`TestInMemoryCodeCacheContract#testGetObjMetaUnknownBizObjReturnsNull`、`#testGenBizObjFilesNullBizModelRejected`。红验证：HEAD 上前者报 `NullPointerException: Cannot invoke "...GraphQLBizModel.getModuleId()" because "bizModel" is null`、后者失败于 `Unexpected exception type thrown, expected NopException but was NullPointerException`——未注册 bizObj 的猜测路径以 NPE（500）中断而非正常 miss。

### [P2] allProps 下拉选项 setValue 连续调用笔误，value 被 "ID" 覆盖且 label 丢失

- **文件**: `nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/entity/NopDynEntityMetaBizModel.java:31-34`
- **维度**: D1（正确性）
- **证据**:
```java
DictOptionBean option = new DictOptionBean();
option.setValue("id");
option.setValue("ID");   // 覆盖 value，本意应为 setLabel("ID")
options.add(option);
```
- **现状**: 第二次 `setValue("ID")` 覆盖了主键属性名 `"id"`，且 label 未设置。
- **风险**: 前端实体属性选择下拉中 ID 选项的提交值为 `"ID"` 而非 ORM 模型中的主键别名 `"id"`，选中后按错误属性名过滤/映射会失配。
- **建议**: 改为 `option.setValue("id"); option.setLabel("ID");`。
- **误报排除**: 已核对 `OrmEntityModelInitializer`（276 行 `props.put(OrmModelConstants.PROP_ID, idProp)`）确认主键别名确为小写 `"id"`；`DictOptionBean` 的 value/label 是两个独立字段。

> **处置（fix-ai-check 分支，2026-08-24）**: 已修复。第二个 `setValue("ID")` 改为 `setLabel("ID")`。测试：`nop-dyn-service` `TestNopDynEntityMetaBizModel#testAllPropsPrimaryKeyOption`（纯单测，同时断言主键选项 value=id/label=ID 及后续属性选项的 value/label）。红验证：HEAD 上失败于 `expected: <id> but was: <ID>`（且 label 为 null）——主键选项提交值变成 ORM 主键别名 "id" 之外的 "ID"，选中后按错误属性名过滤/映射失配。

### [P2] afterEntityChange 在 delete 动作时仍将已删除函数加回集合并重新生成，且 validateSource 可阻断删除

- **文件**: `nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/entity/NopDynFunctionMetaBizModel.java:30-39`
- **维度**: D1（正确性）
- **证据**:
```java
@BizAction
protected void afterEntityChange(@Name("entity") NopDynFunctionMeta entity, @Name("action") String action, IServiceContext context) {
    super.afterEntityChange(entity, action, context);
    entity.validateSource();                                       // delete 时也校验
    entity.getEntityMeta().getFunctionMetas().add(entity);         // delete 时也加回
    codeGen.generateBizModel(entity.getEntityMeta());              // 用被污染的集合立即生成
}
```
- **现状**: `CrudBizModel.deleteEntity` 在删除完成后同样回调 `afterEntityChange(action=METHOD_DELETE)`（CrudBizModel.java:1964）。本实现不区分 action：把已删除实体 add 回 `functionMetas` 集合并立即用它重新生成 xbiz；xbiz 生成模板直接遍历 `entityMeta.functionMetas`（dyn-gen 模板已核对）。同 session 下 `generateBizModel → prepareBizObject → requireEntityById` 返回的是 session 缓存中的同一 entityMeta 对象。
- **风险**: 删除函数后重新生成的 xbiz 可能仍包含该函数（下架不生效，直到下次全量重建）；更直接的：`validateSource` 解析非法 XPL 抛异常会导致删除操作本身失败——用户想删掉一个 source 非法的函数时被卡死。
- **建议**: 按 action 分支：delete 时不 validateSource、不 add（或显式 remove），生成前从 DB 重新加载干净的 entityMeta。
- **误报排除**: 已核对 `CrudBizModel.deleteEntity` 源码确认 delete 也回调；`getFunctionMetas` 是 `to-many keyProp="name"` 集合（nop-dyn.orm.xml:608），add 同 key 项是替换语义而非拒绝，已删除实体会被保留在集合中。

> **处置（fix-ai-check 分支，2026-08-24）**: 已修复。`afterEntityChange` 按 action 分支：delete 时不 validateSource、执行 `getFunctionMetas().remove(entity)`（OrmEntity 为指针相等，dao 删除不会同步父对象内存集合，故需显式移除）后重新生成；save/update 保持原 validate+add 语义。测试：`TestNopDynFunctionMetaAfterChange#testDeleteFunctionWithInvalidSource`（source 设为非法 XML 片段后 dao 删除+钩子回调，断言函数从集合移除、该 GraphQL operation 变为 unknown-operation）。红验证：HEAD 上失败于 `NopException(nop.err.antlr.common.parse-fail, source=[<]not-closed)`——delete 动作同样执行 validateSource，source 非法的函数永远删不掉；修复后删除不被阻断，重新生成的 xbiz 不再暴露该函数。

### [P2] generateByAI 硬编码模块名 "app-demo"、无数据权限检查，重复调用产生重复模块

- **文件**: `nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/entity/NopDynModuleBizModel.java:95-115`
- **维度**: D7（平台规范）/ D1
- **证据**:
```java
public void generateByAI(@Name("response") String response) {
    ...
    entity.setModuleName("app-demo");           // 硬编码
    ...
    entity.setStatus(NopDynDaoConstants.MODULE_STATUS_PUBLISHED);  // 直接发布
    dao.saveEntity(entity);
    dao.flushSession();
    dynCodeGen.generateForModule(entity);
    dynCodeGen.reloadModel();
}
```
- **现状**: 同文件 `importExcel` 在 saveEntity 前调用了 `checkDataAuth(BizConstants.METHOD_SAVE, entity, context)`，`generateByAI` 没有；moduleName 固定为 `app-demo` 且直接置为 PUBLISHED 并触发生成与 reloadModel。
- **风险**: 每次调用都会新建（并发布）一个同名 `app-demo` 模块（moduleName 无唯一约束），污染模块列表并触发全量模型重载；数据权限检查缺失与同类方法不一致。`response` 为外部输入（AI 输出被用户转发），解析结果直接落库为实体元数据。
- **建议**: 模块名参数化并做唯一性校验；补 checkDataAuth 与导入同级的校验。
- **误报排除**: 已核对 importExcel 的 checkDataAuth 调用与 orm.xml 中 MODULE_NAME 列无 unique 索引。

> **处置（fix-ai-check 分支，2026-08-24）**: 已修复。签名增加 `@Optional moduleName` 参数（空值回退 "app-demo"，对既有调用方向后兼容）与 `IServiceContext`；落库前 `dao.findFirstByExample` 查重，同名模块抛新错误码 `ERR_DYN_MODULE_NAME_EXISTS`；补 `checkDataAuth(BizConstants.METHOD_SAVE, entity, context)` 与 importExcel 对齐（context 为 null 时框架幂等跳过，服务端隐式注入 svcCtx 后生效）。测试：`TestNopDynModuleBizModel#testGenerateByAIDuplicateModuleNameRejected`（复用 test.orm.xml 附件连续调用两次，二次必须拒绝；既有 `testGenerateByAI` 适配新签名继续覆盖正常路径）。红验证：HEAD 上失败于 `Expected NopException to be thrown, but nothing was thrown`——重复调用静默创建并发布第二个同名 app-demo 模块并触发全量模型重载；修复后二次调用报同名模块错误。

### [P2] 动态列未填 precision 时默认设为 1，VARCHAR 列将生成 VARCHAR(1) 截断数据

- **文件**: `nop-dyn/nop-dyn-dao/src/main/java/io/nop/dyn/dao/model/DynEntityMetaToOrmModel.java:413-427`（domain 分支 434-448 同样逻辑）
- **维度**: D1（数据错误）
- **证据**:
```java
StdSqlType sqlType = toStdSqlType(propMeta.getStdSqlType());
ret.setStdSqlType(sqlType);

if (sqlType.isAllowPrecision()) {
    if (propMeta.getPrecision() != null) {
        ret.setPrecision(propMeta.getPrecision());
    } else {
        ret.setPrecision(1);      // VARCHAR 未填精度 → precision=1
    }
}
```
- **现状**: `_NopDynPropMeta.xmeta` 中 precision 字段无 mandatory/默认值约束；用户通过前端或 Excel 导入建列不填精度且未指定 domain 时，生成 ORM 列 precision=1。
- **风险**: `genOrmModel` 经 orm.xml round-trip（生成 app.orm.xml 再由 OrmModelLoader 解析）不会改变该值，动态建表 DDL 将得到 `VARCHAR(1)`，超长字符串写入被截断或报错——动态 DDL 破坏库结构、数据静默损坏。
- **建议**: 未填 precision 时按 StdSqlType 给出合理默认（如 VARCHAR 映射平台 domain 默认长度），或在元数据保存时强制校验 precision 必填。
- **误报排除**: 已确认 `toColumnModel` 两个分支（propMeta 与 domain）均无其他 precision 来源；xmeta schema 无默认；`toOrmDomain`（475-489 行）同样模式。

> **处置（fix-ai-check 分支，2026-08-24）**: 已修复。`toColumnModel` 的 propMeta/domain 两分支与 `toOrmDomain` 三处 `setPrecision(1)` 统一改为 `defaultPrecision(sqlType)`：字符串/二进制类（CHAR/VARCHAR/BINARY/VARBINARY）取 100（对齐 nop-dyn 自身通用字符串列 NOP_NAME/NOP_OBJ_TYPE 的精度=100 惯例）；DECIMAL 取 38（主流数据库均支持的最大精度，避免 DECIMAL(1) 只能存个位数）；scale 缺省 0 维持不变。平台侧核实：dialect 的 `stdToNativeSqlType` 对未指定精度无兜底默认，静态 ORM 模型的 VARCHAR 列均显式带精度，因此必须在元数据转换层给缺省值。测试：`TestDynCodeGenModuleLifecycle#testRealTableColumnDefaultPrecision`（真实表实体经 generateForAllModules→genOrmModel→orm.xml round-trip 全链路，断言 VARCHAR 缺省 100、DECIMAL 缺省 38、显式 50 保留）。红验证：HEAD 上失败于 `expected: <100> but was: <1>`（title 列 precision=1，即动态建表将得到 VARCHAR(1)）；修复后三断言全过。

### [P2] 动态表名/列名/默认值无格式校验直通 DDL 生成链，defaultValue 下游拼接未转义

- **文件**: `nop-dyn/nop-dyn-dao/src/main/java/io/nop/dyn/dao/entity/NopDynEntityMeta.java:50-55`、`nop-dyn/nop-dyn-dao/src/main/java/io/nop/dyn/dao/model/DynEntityMetaToOrmModel.java:394-407`、`nop-dyn/nop-dyn-meta/src/main/resources/_vfs/nop/dyn/model/NopDynEntityMeta/_NopDynEntityMeta.xmeta:41-44`
- **维度**: D5（安全）
- **证据**:
```java
// NopDynEntityMeta：表名直接透传
public String forceGetTableName() {
    if (!StringHelper.isEmpty(getTableName())) return getTableName();
    ...
}
// toColumnModel：列 code 由 propName 直接转换，特殊字符保留
ret.setCode(StringHelper.camelCaseToUnderscore(propMeta.getPropName(), false));
ret.setDefaultValue(propMeta.getDefaultValue());   // 无校验
// xmeta：tableName/propName 仅有 String precision 约束，无 pattern
```
- **现状**: `tableName`、`propName`、`defaultValue` 均无格式/pattern 校验即进入 OrmEntityModel/OrmColumnModel，经生成的 app.orm.xml 交给 db-migration 生成 DDL。下游 `CreateTableExecutor`（nop-db-migration，不在本模块）对表名/列名调用 `dialect.escapeSQLName()`，但 `DEFAULT ` 值与 `COMMENT` 是裸拼接（CreateTableExecutor.java:81、89）。
- **风险**: 表名/列名注入被下游 escaping 缓解（纵深防御仍缺一层）；`defaultValue` 链路（NopDynPropMeta.defaultValue → OrmColumnModel → CreateTableExecutor 裸拼 ` DEFAULT `）在 db-migration 侧无转义，持有动态模型维护权限的账号可借默认值注入 SQL 片段到建表语句。
- **建议**: nop-dyn 侧对 tableName/propName/defaultValue 增加 stdDomain/pattern 校验（参照 entityName 已用 `stdDomain="class-name"` 的做法）；同步修复 db-migration 的 DEFAULT/COMMENT 转义。
- **误报排除**: 已核对 xmeta（无 pattern）、`forceGetTableName`（无校验）、`CreateTableExecutor`（表名有 escapeSQLName、defaultValue 无转义）三方源码，链路连通。

> **处置（fix-ai-check 分支，2026-08-24）**: 已修复（nop-dyn 侧纵深防御）。`transformEntityModel` 对 `forceGetTableName()` 校验 `[A-Za-z][A-Za-z0-9_]*`（不合法抛 `ERR_DYN_INVALID_TABLE_NAME`）；`toColumnModel` 对 propName 同 pattern 校验（`ERR_DYN_INVALID_PROP_NAME`，列 code 由 propName 直接推导）、对 defaultValue 拒绝包含换行/分号/`--`/`/*` 的值（`ERR_DYN_INVALID_DEFAULT_VALUE`，语句分隔与注释类注入片段；正常带引号字符串缺省 `'active'` 放行）。db-migration 侧 DEFAULT/COMMENT 转义属另一模块（nop-db-migration）超出本单元范围未动，建议在 nop-db-migration 单元跟进。测试：`nop-dyn-dao` `TestDynEntityMetaToOrmModelColumn#testInvalidTableNameRejected/#testInvalidPropNameRejected/#testInjectedDefaultValueRejected/#testQuotedStringDefaultValueAllowed`。红验证：HEAD 上表名用例失败于 `expected NopException but was NullPointerException`（非法表名直通至未初始化的 dynEntityModel 访问）；propName/defaultValue 用例的错误码断言依赖新增错误码（HEAD 为编译级红），且纯单测下 HEAD 因 OrmException 是 NopException 子类而不可行为区分（生产环境下非法值静默进入 DDL 链），修复后四个用例全绿。

### [P2] genPageFile/genViewFile 未同步使用共享 IEvalScope，违背类自身"所有代码生成都 synchronized"的约定

- **文件**: `nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/codegen/InMemoryCodeCache.java:225-238、261-274`（对照 39-44 类注释、55 行 scope 字段、290-293 clearMergedStore）
- **维度**: D3（并发）
- **证据**:
```java
/** 注意：所有代码生成都使用synchronized保护，确保单线程生成 */
public class InMemoryCodeCache {
    private final IEvalScope scope = XLang.newEvalScope();   // 共享 scope

    // 无 synchronized（对比 genBizObjFiles/genOrmModel 均为 synchronized）
    public void genViewFile(ModuleModel module, GraphQLBizModel bizModel, boolean formatGenCode) {
        ...
        scope.setLocalValue(VAR_BIZ_OBJ_NAME, bizObjName);   // 与并发生成互相踩踏
        gen.execute(subPath, scope);
        ...
        addToMergedStore(resource);   // 读非 volatile 的 mergedStore
    }
```
- **现状**: `genPageFile`/`genViewFile` 是仅有的两个非 synchronized 生成方法，与 synchronized 方法共享同一 `scope`（VAR_BIZ_OBJ_NAME/VAR_MODULE_MODEL 等局部变量槽）。串行化目前仅靠 `DynResourceStore.getResource` 的实例级 synchronized；但 `clearMergedStore` 后 `getResourceStore()` 会创建新的 DynResourceStore 实例，新旧实例（及不同 VFS 线程持有的旧引用）之间无互斥。
- **风险**: 并发窗口内两个生成任务交叉写 scope，生成的页面/视图文件可能取到另一 bizObj 的上下文（生成内容污染）；`mergedStore` 非 volatile 无同步读，存在可见性问题与发布层不一致。
- **建议**: 为 genPageFile/genViewFile 补 synchronized；mergedStore/dynResourceStore 声明为 volatile。
- **误报排除**: 已核对方法签名与调用链（prepareResource → genPageFile/genViewFile 无锁），以及 `getResourceStore()` 每次重建新 DynResourceStore 实例的逻辑，多实例并存窗口真实存在。

> **处置（fix-ai-check 分支，2026-08-24）**: 已修复。`genPageFile`/`genViewFile` 补 `synchronized`（与 genBizObjFiles/genOrmModel/genModuleCoreFiles 一致，共享 IEvalScope 的 VAR_BIZ_OBJ_NAME 等局部变量槽不再有交叉写入窗口）；`mergedStore`/`dynResourceStore` 声明为 volatile（getResourceStore 的同步写与 addToMergedStore 的无锁读之间可见性）。并发交叉写 scope 需真实模板生成栈且时序不可控，无确定性单线程红可写，采用结构断言测试：`TestInMemoryCodeCacheContract#testGenerationMethodsSynchronizedAndStoresVolatile`（反射断言 genPageFile/genViewFile/getOrmModel 三方法 synchronized、两字段 volatile）。红验证：HEAD 上失败于 `genPageFile必须synchronized ==> expected: <true> but was: <false>`；修复后全过。

### [P2] 同名中间表实体被第三个 m2m 关系静默覆盖，关系定义丢失

- **文件**: `nop-dyn/nop-dyn-dao/src/main/java/io/nop/dyn/dao/model/DynEntityMetaToOrmModel.java:91-99、287-291`
- **维度**: D1（元数据一致性）
- **证据**:
```java
void addRelation(OrmEntityModel entityModel, NopDynEntityRelationMeta rel) {
    if (relationA == null) {
        this.entityModelA = entityModel; this.relationA = rel;
    } else {
        this.entityModelB = entityModel; this.relationB = rel;   // 第三个关系直接覆盖第二个
    }
}
...
middleInfos.computeIfAbsent(middleEntityName, k -> new MiddleEntityInfo()).addRelation(entityModel, rel);
```
- **现状**: MiddleEntityInfo 只有两个槽位；用户显式配置的 `middleEntityName`（或推导名）被 3 个及以上 m2m 关系共用时，第三个及之后的 addRelation 静默覆盖 relationB，被覆盖的关系在生成的 ORM 模型中消失，无任何告警或校验。
- **风险**: 动态 ORM 元数据与数据库/页面配置不一致：某个多对多关联在生成模型中缺失，读写该关联属性时报"未知属性"或数据不可达。
- **建议**: addRelation 遇到已满槽位时抛 NopException（带 middleEntityName/重复关系名），或在校验层拒绝同名中间表的第三关系。
- **误报排除**: 已核对全类无对该场景的校验/日志；`guessMiddleEntityName` 允许用户显式指定任意 middleEntityName，冲突可构造。

> **处置（fix-ai-check 分支，2026-08-24）**: 已修复。`MiddleEntityInfo.addRelation` 第三个关系抛新错误码 `ERR_DYN_MIDDLE_ENTITY_CONFLICT`（带 middleEntityName 与冲突 relationName），不再静默覆盖 relationB。测试：`nop-dyn-dao` `TestMiddleEntityInfo#testTwoRelationsAllowed`（两关系正常占据 A/B 槽位，语义回归）/`#testThirdRelationRejected`（第三个关系必须报错）。红验证：HEAD 上失败于 `Expected NopException to be thrown, but nothing was thrown`——第三个关系直接覆盖 relationB，被覆盖关系在生成的 ORM 模型中消失；修复后抛冲突错误。注：nop-dyn-dao 原无测试目录，pom 补 junit-jupiter test 依赖。

### [P2] 每次保存动态函数都触发 ormTemplate.reloadModel 全量重载与 VFS/BizModel 全局刷新

- **文件**: `nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/codegen/DynCodeGen.java:255-261、351-361`、调用链 `NopDynFunctionMetaBizModel.afterEntityChange`
- **维度**: D6（性能）/ D3
- **证据**:
```java
public synchronized void generateBizModel(NopDynEntityMeta entityMeta, boolean syncFile) {
    InMemoryCodeCache codeCache = getCodeCache();
    GraphQLBizModel bizModel = buildGraphQLBizModel(entityMeta);
    codeCache.genBizObjFiles(formatGenCode, bizModel);
    if (syncFile) this.reloadModel();     // 全量
}

public synchronized void reloadModel() {
    ... VirtualFileSystem.instance().updateInMemoryLayer(store);
    ModuleManager.instance().updateDynamicModules(...);
    bizObjectManager.setDynamicBizModels(...);
    ormTemplate.reloadModel();            // ORM session factory 级重载
}
```
- **现状**: CRUD 保存/更新/删除一个 NopDynFunctionMeta 即触发 `generateBizModel(entityMeta, true)` → `reloadModel`，无条件重建 VFS in-memory 层、动态模块集合、BizModel 集合并 `ormTemplate.reloadModel()`（整个 ORM 模型/session 工厂级刷新），即便改动只涉及单个 bizObj 的单个函数。
- **风险**: 管理端连续编辑函数定义时反复全量重载，拖慢管理操作并与正在执行的查询产生模型切换竞争（依赖 ormTemplate 内部的 volatile 替换才不致报错），是热更新路径上不必要的全量刷新。
- **建议**: 仅当实体结构（列/关系）变化时才 reloadModel；函数级变更只刷新对应 bizObj 的生成文件与 bizModel 缓存。
- **误报排除**: 已确认 afterEntityChange 对 save/update/delete 三种 action 都走 `generateBizModel(entityMeta)`（默认 syncFile=true）。

> **处置（fix-ai-check 分支，2026-08-24）**: 已修复（性能优化，外部可观察行为不变）。`reloadModel` 拆出 `reloadModel(boolean refreshOrmModel)`：VFS in-memory 层、动态模块集合、BizModel 集合的刷新保持每次执行，仅 `ormTemplate.reloadModel()`（session factory 级全量重建）按需执行；`generateBizModel` 增加 3 参重载（1/2 参重载保留，全量语义不变）。`NopDynFunctionMetaBizModel.afterEntityChange`（函数级变更不涉及实体结构/ORM 模型）改走 `generateBizModel(entityMeta, true, false)`。纯性能优化不存在可区分对错的单线程红测试（免测理由），但新增 `TestNopDynFunctionMetaAfterChange#testUpdateFunctionStaysLiveAfterLightRefresh` 作为新路径回归防线：经 afterEntityChange 轻量刷新后 GraphQL 热取到修改后的函数返回值 555（修复前后均绿；若轻量路径破坏函数热更新会红）。3 参重载在 HEAD 上为编译级红。

### [P3] getRefPropNameFromColCode 条件顺序错误，`endsWith("id")` 短路掉 `_id` 分支，命名行为不一致

- **文件**: `nop-dyn/nop-dyn-dao/src/main/java/io/nop/dyn/dao/model/OrmModelToDynEntityMeta.java:280-288`
- **维度**: D1
- **证据**:
```java
private String getRefPropNameFromColCode(String colCode, String refEntityName) {
    if (colCode.equalsIgnoreCase("_id") || colCode.endsWith("id"))
        return refEntityName;
    if (StringHelper.endsWithIgnoreCase(colCode, "_id")) {   // 永远只对大写 _ID 生效
        return StringHelper.camelCase(colCode.substring(0, colCode.length() - "_id".length()), false);
    }
    return StringHelper.camelCase(colCode, false) + "Obj";
}
```
- **现状**: 小写 `user_id` 命中第一分支返回 `refEntityName`，大写 `USER_ID` 命中第二分支返回 `user`——意图明显是先判 `_id` 后缀。nop 平台列 code 惯例为大写（如 code="DEPT_ID"），当前大写路径行为碰巧符合意图，小写 code 行为分叉。
- **风险**: AI 生成模型导入（addRefTable）时同一语义的列 code 大小写不同会生成不同的 relation 命名，元数据不稳定。
- **建议**: 交换两个条件的顺序。
- **误报排除**: 纯逻辑审查，两分支可达性已人工推演。

> **处置（fix-ai-check 分支，2026-08-24）**: 已修复。条件重排为：`equalsIgnoreCase("_id")` → `endsWithIgnoreCase("_id")`（取前缀 camelCase）→ `endsWith("id")` → 兜底 `+Obj`；`equalsIgnoreCase("_id")` 保持最前避免空串 camelCase。方法由 private 放宽为包级可见供同包单测。保留 `endsWith("id")` 兜底分支使 "userId" 等大小写混合列 code 行为与修复前完全一致（endsWith 区分大小写，"userId" 结尾是 "Id" 不命中，修复前后均返回 useridObj）。测试：`nop-dyn-dao` `TestOrmModelToDynEntityMetaColCode`（5 用例：小写/大写 _id 后缀、精确 _id 列、userId 对照、非 id 列兜底）。红验证：HEAD 上方法为 private（编译级红），临时反射改写调用后行为红为 `testLowercaseIdSuffix expected: <user> but was: <User>`——小写 user_id 被 `endsWith("id")` 短路返回 refEntityName，与大写 USER_ID→user 的行为分叉；修复后 user_id/USER_ID 一致返回 user。

### [P3] CFG_DYN_MAX_BIZ_OBJECTS 配置定义后无任何使用点（死配置）

- **文件**: `nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/NopDynConfigs.java:22-25`
- **维度**: D8（契约漂移）
- **证据**:
```java
@Description("最多允许多少动态对象")
IConfigReference<Integer> CFG_DYN_MAX_BIZ_OBJECTS =
        varRef(s_loc, "nop.dyn.max-biz-objects", Integer.class, 1000);
```
- **现状**: 全模块（含资源文件）grep 无引用。动态对象数量上限从未生效，`getTenantBizObjNames`/`addBizModel` 等入口无数量限制。
- **风险**: 配置项对外暴露（文档化语义"限制动态对象数"）但实际无效，误导运维；无上限时动态对象可无限增长挤占内存缓存。
- **建议**: 在 addModule/genBizObjFiles 入口实现该上限检查，或删除该配置项。
- **误报排除**: 全仓 nop-dyn 范围 grep 确认无第二处引用。

> **处置（fix-ai-check 分支，2026-08-24）**: 已修复（实现上限检查而非删除配置项，保留对外文档化语义）。`genModuleCoreFiles` 在执行任何生成动作前调用新增 `checkMaxBizObjects(newCount)`（`bizModels.size()+newCount > CFG_DYN_MAX_BIZ_OBJECTS` 抛新错误码 `ERR_DYN_MAX_BIZ_OBJECTS_EXCEED`，带 currentCount/maxCount 参数）；`genBizObjFiles` 单对象增长路径同样受检（已注册 bizObj 的重新生成不占新额度）。测试：`TestInMemoryCodeCacheContract#testMaxBizObjectsEnforced`（1001 个 bizObj 的模块发布被拒、1 个正常放行且不误报上限）/`#testMaxBizObjectsEnforcedOnSingleAdd`（预置 1000 后新增 1 个被拒）。红验证：HEAD 上无该检查，addModule 一路执行到模板生成（纯单测环境以 virtual-file-system-not-initialized 失败），上限错误码断言依赖新增错误码（HEAD 为编译级红）；修复后两用例精确抛 `nop.err.dyn.max-biz-objects-exceed`。

### [P3] 租户缓存清理不执行 on-unload 钩子，与共享缓存清理路径不一致

- **文件**: `nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/codegen/DynCodeGen.java:97-107、191-193`
- **维度**: D2/D7（资源清理一致性）
- **证据**:
```java
public void destroy() {
    ...
    codeCache.clear();        // InMemoryCodeCache.clear() 会 runOnUnloadModule
    tenantCache.clear();      // 仅清除 Map 条目，租户 InMemoryCodeCache 的 on-unload.xpl 不执行
    ...
}
public void clearForTenant(String tenantId) {
    tenantCache.remove(tenantId);   // 同上
}
```
- **现状**: 共享 `codeCache` 走 `InMemoryCodeCache.clear()`（对每个模块执行 runOnUnloadModule）；租户缓存只从 `tenantCache` map 移除条目，租户 InMemoryCodeCache 生命周期结束时无 unload 回调。当前 on-load/on-unload 模板仅打日志，暂无实际影响。
- **风险**: 一旦动态模块的 on-unload.xpl 承担真实清理职责（当前模板机制支持），租户路径将漏执行，产生租户资源泄漏。
- **建议**: clearForTenant/destroy 中先获取各租户 cache 调用其 `clear()` 再从 map 移除。
- **误报排除**: 已核对 `InMemoryCodeCache.clear()`（85-94 行）与 `LocalCache.remove/clear` 语义差异。

> **处置（fix-ai-check 分支，2026-08-24）**: 已修复。新增 `removeTenantCache(tenantId)`（getIfPresent 后 remove，不触发 cache loader 的租户初始化）；`clearForTenant` 移除条目前先对租户 cache 执行 `clear()`（内部对每个模块 runOnUnloadModule）；`destroy` 对 `tenantCache.getAllKeys()` 逐个同样处理后整体 clear，与共享 codeCache 的清理语义对齐。测试：`TestInMemoryCodeCacheContract#testClearForTenantRunsCacheClear`（反射预置租户缓存与计数 clear() 的 cache 子类，断言 clear 被执行、条目被移除、重复清理幂等）。红验证：HEAD 上失败于 `clearForTenant必须执行租户cache的clear()以触发on-unload钩子 ==> expected: <1> but was: <0>`——只做 map 移除、on-unload 钩子被跳过；修复后 clear 执行且幂等。destroy() 全路径依赖 VFS 单例未单独实测，与 clearForTenant 共用同一 removeTenantCache 辅助方法。

## 补充说明（已排查、未列为发现）

- **PROP_ID join 悬念排除**：`DynEntityMetaToOrmModel` 中间表 join 用 `OrmModelConstants.PROP_ID`（"id"）而动态实体主键列名为 "sid"，初看疑为失配；经核对 `OrmEntityModelInitializer:276`（`props.put(PROP_ID, idProp)`），"id" 是 init 时注册的主键别名，非 bug。
- **D7 平台规范整体良好**：@Inject 字段全部为 package-private（DynCodeGen/NopDynModuleBizModel/NopDynFunctionMetaBizModel 均合规）；配置注入使用 @InjectValue；无 bare RuntimeException/printStackTrace/吞异常（唯一 catch 在 runOnUnloadModule，有 LOG.error）；beans.xml 显式定义 nopDynCodeGen/nopDynOrmModelHolder；错误码统一 NopException + ErrorCode + .param。
- **NopDynEntityMeta.getMainPagePath / NopDynPage.getPagePath 的 getModule() NPE 悬念排除**：MODULE_ID 列 mandatory=true，关联缺失仅可能来自脏数据。
- **DynResourceStore 为 IResourceStore 的薄包装**，无流操作；模块内无直接 DDL 执行（动态建表经生成 app.orm.xml 交由平台 db-migration），D2 流泄漏无发现。
