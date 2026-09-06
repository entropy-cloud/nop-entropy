# nop-dyn 实现代码检查报告（check2）

- 检查日期: 2026-08-23
- 模块路径: nop-dyn
- 文件数: 122（src/main/java：nop-dyn-api 45、nop-dyn-dao 52、nop-dyn-service 24、nop-dyn-app 1）
- 覆盖范围声明: 深读 45 个文件（nop-dyn-service 全部 24 个：DynCodeGen、InMemoryCodeCache、DynResourceStore、DynOrmModelProvider、GptCodeGen、IDynCodeGenCacheHook、NopDynConfigs/Constants/Errors、16 个 BizModel；nop-dyn-dao 手写部分：16 个实体类、DynEntityMetaToOrmModel、OrmModelToDynEntityMeta、NopDynDaoConstants/Errors；nop-dyn-app 的 NopDynApplication）。模式扫描（异常吞噬、bare RuntimeException、SimpleDateFormat/Random、@Inject private、@Value、流/资源管理、synchronized/线程、printStackTrace）覆盖 100% 文件（含 `_gen/` 基类、nop-dyn-api 45 个 XGEN 生成文件、16 个生成 biz 接口，均仅模式扫描，未逐行深读）。未覆盖区域: 无整块遗漏；`_gen/` 生成代码仅按调用契约交叉核对（如 OrmEntitySet、_NopDynEntityMeta）而未逐行审计。交叉引用验证过的外部代码: ResourceHelper、StringHelper.isValidNopModuleName、ICache/LocalCache、InMemoryResourceStore/ResourceTreeNode（TreeMap）、OrmEntitySet.add、CrudBizModel（afterEntityChange/saveEntity/updateEntity/deleteEntity 调用链）、ObjFunctionHandle/MethodInvoker/ClassHelper.invoke（XLang 方法调用无参数类型转换）、ResourceTenantManager/DeltaResourceStore（租户 store 回退逻辑）、beans.xml 注册。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 4 |
| P2 | 7 |
| P3 | 5 |

## 发现列表

### [P1] allProps 字典项连续两次 setValue，"id" 选项的 value 被覆盖为 "ID"，label 缺失

- **文件**: `nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/entity/NopDynEntityMetaBizModel.java:31-34`
- **维度**: D1（复制粘贴错误）
- **证据**:
```java
List<DictOptionBean> options = new ArrayList<>();
DictOptionBean option = new DictOptionBean();
option.setValue("id");
option.setValue("ID");          // 应为 option.setLabel("ID")
options.add(option);
```
- **现状**: `@BizLoader(autoCreateField = true)` 的 `allProps` 为 NopDynEntityMeta 提供属性字典。第一项本意是 value=id/label=ID 的内置主键选项，第二次 `setValue("ID")` 直接覆盖了 value，且从未设置 label。
- **风险**: 每次查询 `allProps` 都返回错误数据：主键选项 value 变为 `"ID"`（不存在该字段名）、label 为 null。前端按 value 过滤/提交时引用不存在的属性名。
- **建议**: 第二行改为 `option.setLabel("ID")`。
- **误报排除**: 已读 DictOptionBean 用法（同方法第 39 行 `prop.setValue(...); prop.setLabel(...)` 成对调用），确认 label 才是第二参数的正确 setter；该 loader 通过 GraphQL selection 直接可达。

> **处置（fix-ai-check 分支，2026-08-28）**: 复查非问题。已被 plan344 修复覆盖（commit 0cfa0dba，2026-08-24）：第二个 `setValue("ID")` 已改为 `setLabel("ID")`，当前代码 NopDynEntityMetaBizModel.java L32-33 为 `setValue("id")`/`setLabel("ID")` 成对调用。钉定测试 `TestNopDynEntityMetaBizModel#testAllPropsPrimaryKeyOption` 本次全量回归跑绿（断言主键选项 value=id/label=ID 及后续属性选项）。模块测试：nop-dyn-service 30 run / 0 fail / 1 skip（既有 @Disabled）。

### [P1] NopDynFunctionMetaBizModel.afterEntityChange 忽略 action 参数：删除路径把已删除函数加回集合并按旧集合重新生成代码

- **文件**: `nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/entity/NopDynFunctionMetaBizModel.java:30-39`
- **维度**: D1（边界条件/逻辑错误）
- **证据**:
```java
@BizAction
@Override
protected void afterEntityChange(@Name("entity") NopDynFunctionMeta entity, @Name("action") String action, IServiceContext context) {
    super.afterEntityChange(entity, action, context);

    entity.validateSource();

    entity.getEntityMeta().getFunctionMetas().add(entity);
    codeGen.generateBizModel(entity.getEntityMeta());
}
```
- **现状**: 基类 CrudBizModel 的 `saveEntity`/`updateEntity`/`deleteEntity`（nop-biz CrudBizModel.java:2012/2040/2072 附近，均调用 3 参 `afterEntityChange(entity, action, context)`）都会进入此重写，但实现完全忽略 `action`：
  - 删除路径（嵌套集合删除走 `deleteEntity`，action=delete）：`getFunctionMetas().add(entity)` 把刚删除的实体重新加入父集合（OrmEntitySet.add 会 `removedEntities.remove(e)` + `orm_markDirty()`，见 nop-orm OrmEntitySet.java:497-509），随后 `generateBizModel` 用仍包含已删除函数的集合重新生成 xbiz；`validateSource()` 还可能因源码非法而让删除操作直接失败。
  - 更新路径：无条件重复触发整套代码生成与 `reloadModel()`（VFS 层重建），放大开销。
- **风险**: 删除函数后重新生成的 biz 模型仍包含该函数（幽灵函数继续暴露为 GraphQL action）；脏集合可能随会话 flush 造成 ORM 状态异常；删除被源码校验阻塞。
- **建议**: 按 `action` 分支处理，delete 时不校验 source、不加回集合并应从集合中移除后再生成。
- **误报排除**: 已核对 CrudBizModel 中 `afterEntityChange(entity, action, context)` 的全部调用点（save 803、update 987、saveEntity/updateEntity/deleteEntity 2012-2072），确认 3 参版本在删除流可达；已核对 OrmEntitySet 内部为 `LinkedHashSet` + removedEntities 机制，确认 add 对已移除实体的复活效果。

> **处置（fix-ai-check 分支，2026-08-28）**: 复查非问题。已被 plan344 修复覆盖（commit 0cfa0dba）：当前代码按 action 分支——delete 时不 validateSource、执行 `getFunctionMetas().remove(entity)` 后重新生成（审计证据中的"加回集合/删除被校验阻塞"均不复存在）；save/update 保持 validate+add；函数级变更改走 `generateBizModel(entityMeta, true, false)` 轻量刷新（update 路径"无条件重复触发 ORM 全量重载"的开销问题一并解决）。钉定测试 `TestNopDynFunctionMetaAfterChange#testDeleteFunctionWithInvalidSource`（非法 source 的函数可删除且 xbiz 不再暴露）与 `#testUpdateFunctionStaysLiveAfterLightRefresh`（轻量刷新后函数热更新仍生效）本次跑绿。模块测试：nop-dyn-service 30/0/1。

### [P1] unpublish 动作向 removeDynModule(String) 传入实体对象，XLang 无类型转换，取消发布必然抛异常

- **文件**: `nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/codegen/DynCodeGen.java:328-330`（契约方）；调用方 `nop-dyn/nop-dyn-service/src/main/resources/_vfs/nop/dyn/model/NopDynModule/NopDynModule.xbiz:48`
- **维度**: D8（公开接口与实现/调用方不匹配）
- **证据**:
```java
// DynCodeGen.java
public synchronized void removeDynModule(String moduleId) {
    getCodeCache().removeModule(moduleId);
}
```
```xml
<!-- NopDynModule.xbiz unpublish 动作 -->
const entity = thisObj.invoke("get",{id},null, svcCtx);
codeGen.removeDynModule(entity);   <!-- entity 是 NopDynModule 实体，非 String -->
```
- **现状**: xbiz 中 `publish` 动作调用 `codeGen.generateForModule(entity)`（参数类型 NopDynModule，匹配），而 `unpublish` 把实体传入仅有的 `removeDynModule(String)`。XLang 运行期按方法名+参数个数选择方法（ObjFunctionHandle.getUniqueMethod），最终 `MethodInvoker` → `ClassHelper.invoke` → 原生 `Method.invoke`，链路上没有任何参数类型转换（已核对 nop-xlang ObjFunctionHandle/MethodInvoker 与 nop-commons ClassHelper）。
- **风险**: 用户点击"取消发布"时 `method.invoke` 抛 IllegalArgumentException（argument type mismatch），被包装为 NopException，unpublish 功能完全不可用；即使未来加入宽松转换，toString 结果也不是 moduleId，删除仍是空操作（模块代码残留在线）。
- **建议**: xbiz 改为 `codeGen.removeDynModule(entity.nopModuleId)`。
- **误报排除**: 已通读 XLang 成员调用编译与执行链（BuildExecutableProcessor.buildMemberCall → ObjFunctionExecutable → ObjFunctionHandle → MethodInvoker → ClassHelper.invoke 纯反射），确认无隐式转换；DynCodeGen 上无其他 1 参 removeDynModule 重载。

> **处置（fix-ai-check 分支，2026-08-28）**: 复查非问题。已被 plan344 修复覆盖（commit 0cfa0dba）：xbiz `unpublish` 动作现为 `codeGen.removeDynModule(entity.nopModuleId)`（NopDynModule.xbiz，参数类型与 `removeDynModule(String moduleId)` 契约匹配，实体属性访问在 XLang 中为字段取值非反射调用）。xbiz 侧为 XPL 单行改动，与第一轮处置口径一致不另设独立单测（需完整 GraphQL 服务栈）；Java 侧同源 key 语义由 `TestDynCodeGenModuleLifecycle#testGenerateForAppRemovesUnpublishedModule` 覆盖，本次跑绿。模块测试：nop-dyn-service 30/0/1。

### [P1] InMemoryCodeCache.genViewFile/genPageFile 未加锁却共享可变 IEvalScope、非 volatile 的 mergedStore 与 TreeMap 资源树，违反类自述的单线程生成约定

- **文件**: `nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/codegen/InMemoryCodeCache.java:55,225-238,261-274,290-293,352-377`
- **维度**: D3（共享可变状态/线程安全）
- **证据**:
```java
private final IEvalScope scope = XLang.newEvalScope();   // L55 全缓存共享

public void genViewFile(ModuleModel module, GraphQLBizModel bizModel, boolean formatGenCode) {  // L225 未 synchronized
    ...
    scope.setLocalValue(VAR_BIZ_OBJ_NAME, bizObjName);   // 共享 scope 写入
    gen.execute(subPath, scope);
    ...
    addToMergedStore(resource);                          // L371-377 读非 volatile 的 mergedStore
}
```
- **现状**: 类头注释（L43）明确"所有代码生成都使用synchronized保护，确保单线程生成"，`clear/addModule/removeModule/genModuleCoreFiles/genOrmModel/getObjMeta/genBizObjFiles/getResourceStore` 均为 synchronized，唯独 `genViewFile`/`genPageFile` 不是。二者由 `DynResourceStore.getResource`（仅 synchronized 于 DynResourceStore 实例）经 `DynCodeGen.prepareResource` 触发；而并发的业务操作（如函数保存）走 `generateBizModel`（锁 DynCodeGen）→ `genBizObjFiles`（锁 InMemoryCodeCache），两把锁互不排斥。竞争面包括：共享 `scope` 的局部变量交叉污染（生成结果串台）、`moduleStores` 内 InMemoryResourceStore 的 TreeMap 并发读写（ResourceTreeNode 内部为 TreeMap，非线程安全）、`mergedStore/dynResourceStore` 两个普通字段的可见性问题（getResourceStore 在锁内写、addToMergedStore 无锁读）。
- **风险**: 并发的页面/视图懒生成与 biz 文件重生成交错时，模板变量（bizObjName/moduleId）错乱导致生成错误文件，或 TreeMap 并发修改抛 CME，或写入已被替换的旧 merged store。
- **建议**: 将 genViewFile/genPageFile 声明为 synchronized（与类内其他生成方法一致），mergedStore/dynResourceStore 声明为 volatile。
- **误报排除**: 已列出全模块 synchronized 方法清单确认这两个方法缺锁；已核对 DynResourceStore 锁粒度（仅自身实例）；已核对 ResourceTreeNode/InMemoryResourceStore 内部为 TreeMap 无并发保护；已确认 generateBizModel→genBizObjFiles 与 prepareResource→genPageFile 分属不同锁体系且均真实可达（函数保存 biz mutation 与 VFS 资源懒加载）。

> **处置（fix-ai-check 分支，2026-08-28）**: 复查非问题。已被 plan344 修复覆盖（commit 0cfa0dba）：`genViewFile`/`genPageFile` 均已声明 `synchronized`（InMemoryCodeCache.java L247/L283），`mergedStore`/`dynResourceStore` 均已声明 `volatile`（L78-79），与类内其他生成方法锁策略一致；同轮还补齐了 `getOrmModel` 的 synchronized 统一 this→CHM 锁序（消 ABBA 死锁，属第一轮报告条目）。并发交叉写 scope 无确定性单线程红可写，钉定结构断言测试 `TestInMemoryCodeCacheContract#testGenerationMethodsSynchronizedAndStoresVolatile`（反射断言三方法 synchronized、两字段 volatile）与 `TestInMemoryCodeCacheDeadlock#testGetOrmModelAndRemoveModuleNoDeadlock`（latch 门控死锁复现）本次跑绿。模块测试：nop-dyn-service 30/0/1。

### [P2] generateForApp 用 moduleName（含 '-'）作为缓存 key 调 removeModule，实际 key 是 nopModuleId（'/'分隔），未发布模块永远无法移除

- **文件**: `nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/codegen/DynCodeGen.java:211-214`
- **维度**: D1（键不一致的逻辑错误）
- **证据**:
```java
for (NopDynModule module : app.getRelatedModuleList()) {
    if (module.getStatus() != NopDynDaoConstants.MODULE_STATUS_PUBLISHED) {
        codeCache.removeModule(module.getModuleName());   // key 应为 module.getNopModuleId()
    } else {
        generateForModule(module);
    }
}
```
- **现状**: InMemoryCodeCache 的 `enabledModules/moduleStores/ormModels` 均以 `ModuleModel.getModuleId()` 为 key，而 `buildModuleModel` 设定的值是 `module.getNopModuleId()` = `moduleName.replace('-','/')`（NopDynModule.java:19-21 + ResourceHelper.java:174-178）。平台规范要求合法模块名必须含 '-'（StringHelper.isValidNopModuleName：无 '-' 直接返回 false），因此 `getModuleName()` 与 `getNopModuleId()` 恒不相等，removeModule 为静默空操作。
- **风险**: 该分支失效后，App 重新生成时未发布模块的代码/资源仍留在缓存并继续对外服务（应下线未下线）。当前仓库内 `generateForApp/generateForAllApps` 无调用方（已全仓 grep），属潜伏缺陷，一旦接入即触发。
- **建议**: 改为 `codeCache.removeModule(module.getNopModuleId())`（与 `removeDynModule` 语义一致）。
- **误报排除**: 已核对 InMemoryCodeCache.addModule/buildGenerator 的 key 来源、buildModuleModel 的 moduleId 赋值、ResourceHelper.getModuleIdFromModuleName 的替换语义及 isValidNopModuleName 对 '-' 的强制要求。

> **处置（fix-ai-check 分支，2026-08-28）**: 复查非问题。已被 plan344 修复覆盖（commit 0cfa0dba）：`generateForApp` 未发布分支现为 `codeCache.removeModule(module.getNopModuleId())`（DynCodeGen.java L233，含注释说明 key 语义），未发布模块不再残留缓存。钉定测试 `TestDynCodeGenModuleLifecycle#testGenerateForAppRemovesUnpublishedModule`（moduleName=app-demo/nopModuleId=app/demo，发布入缓存后转未发布走 generateForApp，断言条目被移除）本次跑绿。模块测试：nop-dyn-service 30/0/1。

### [P2] prepareResource 多处对 getBizModel 返回值不判空，未知 bizObj 路径触发 NPE 而非正常未命中

- **文件**: `nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/codegen/DynCodeGen.java:453-483`；`nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/codegen/InMemoryCodeCache.java:208-211`
- **维度**: D1（NPE 风险）
- **证据**:
```java
if (path.endsWith(".xmeta") || path.endsWith(".xbiz")) {
    String bizObjName = cache.getBizObjNameFromModelsPath(path);
    if (bizObjName == null) return;
    GraphQLBizModel bizModel = cache.getBizModel(bizObjName);   // 可能为 null，未判空
    ormTemplate.runInSession(() -> cache.genBizObjFiles(formatGenCode, bizModel));  // genBizObjFiles 内 bizModel.getModuleId() NPE
    return;
}
...
GraphQLBizModel bizModel = cache.getBizModel(bizObjName);       // L469 同样未判空
...
IObjMeta objMeta = cache.getObjMeta(bizObjName, formatGenCode); // getObjMeta L209-211 bizModel.getModuleId() NPE
```
- **现状**: prepareResource 由 DynResourceStore 在 merged store 未命中时回调。只要路径落在已启用 dyn 模块下且形如 /model/{name}/... 或 /pages/{name}/...，而 `{name}` 不在 bizModels（实体已删除但存在残留引用、或新建实体尚未做模块级重生成），`getBizModel` 返回 null 后各分支直接解引用。
- **风险**: VFS 资源查找以 NPE（被包装）失败，替代了应得的"资源不存在"语义；DynResourceStore.getResource 同步块内抛出会中断整个查找。
- **建议**: 三个分支统一加 `if (bizModel == null) return;`，让上层按未命中处理。
- **误报排除**: 已通读 prepareResource 全部分支与 genBizObjFiles/getObjMeta 的首行代码，确认 null 解引用点；已确认 bizModels 仅在模块加载/单对象生成时写入（prepareLoadModule/genBizObjFiles），删除实体不清理同名路径的可达性。

> **处置（fix-ai-check 分支，2026-08-28）**: 复查非问题。已被 plan344 修复覆盖（commit 0cfa0dba）：prepareResource 的 .xmeta/.xbiz 分支与 pages 分支均对 `getBizModel` 判空后 return（DynCodeGen.java L492-494/L506-508，资源 miss 语义）；`getObjMeta` 判空返回 null；公共入口 `genBizObjFiles` 对 null 抛 `ERR_DYN_BIZ_MODEL_NOT_EXISTS` 兜底。钉定测试 `TestInMemoryCodeCacheContract#testGetObjMetaUnknownBizObjReturnsNull`/`#testGenBizObjFilesNullBizModelRejected` 本次跑绿。模块测试：nop-dyn-service 30/0/1。

### [P2] 懒生成路径调用 genBizObjFiles 后 clearMergedStore，DynResourceStore 仍持有旧 merged store，本次请求永远 miss 且每次重复生成

- **文件**: `nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/codegen/InMemoryCodeCache.java:276-293`；`nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/codegen/DynResourceStore.java:21-28`
- **维度**: D1/D6（错误失效策略 + 热路径重复生成）
- **证据**:
```java
public synchronized IResourceStore getResource(String path, boolean returnNullIfNotExists) {
    IResource resource = store.getResource(path, true);   // store 为构造时捕获的 merged store
    if (resource != null) return resource;
    prepare.accept(path);                                 // 内部 genBizObjFiles → clearMergedStore()
    return store.getResource(path, returnNullIfNotExists); // 仍是旧 store，新生成文件只写入 moduleStores
}
```
```java
// genBizObjFiles 尾部
this.clearMergedStore();   // L287 仅置 null，不更新已发布 store；addToMergedStore 在 mergedStore==null 时静默丢弃资源
```
- **现状**: `genBizObjFiles`/`genModuleCoreFiles`/`genOrmModel` 生成后只 `clearMergedStore()`（置 null），重建依赖后续 `reloadModel()→getResourceStore()`。经 prepareResource 触发的懒生成（.xmeta/.xbiz）没有后续 reload，DynResourceStore 捕获的旧 merged store 不含新文件，本次查找返回 null；下次请求再次 miss → 再次全量生成（模板执行 + DB 访问），形成"每次请求都重新生成却永远查不到"的循环，直到某处恰好调用 reloadModel。
- **风险**: 懒加载场景资源不可得 + 热路径重复代码生成（CPU/DB 放大）。
- **建议**: prepareResource 的生成路径改用 addToMergedStore 直接补入当前 merged store（genBizObjFiles 末尾的 clearMergedStore 对该场景改为按需合并），或 DynResourceStore 持有 supplier 每次取 `getResourceStore()` 最新实例。
- **误报排除**: 已核对 DynResourceStore 构造点（getResourceStore 内）与 clearMergedStore 的全部调用方，确认懒生成链路（DynResourceStore→prepareResource→genBizObjFiles→clearMergedStore）无 reloadModel 跟进；已核对 addToMergedStore 的 null 短路。

> **处置（fix-ai-check 分支，2026-08-28）**: 已修复（本次）。事实修正：审计前提"本次请求永远 miss 且每次重复生成"不完全成立——`ResourceTreeNode.merge` 以 `putIfAbsent` 直接别名子节点对象，已发布 merged store 与 moduleStore 共享moduleId 以下的节点链，懒生成文件经共享祖先节点在旧 store 中仍可见（红测试运行亦证实 resource 非空）。真实残留缺陷：(1) 懒生成后 `clearMergedStore` 把 mergedStore/dynResourceStore 字段置空，到下次 `getResourceStore()` 前 `getMergedStore()` 恒 null 的空窗；(2) 资源可见性依赖树节点别名这一实现细节而非显式契约。修复：`genBizObjFiles` 在生成前捕获已发布的 merged store/DynResourceStore 实例（嵌套 `genOrmModel` 会置空字段），生成后将本模块 store 显式 `merge` 进已发布 merged store 并恢复字段引用；mergedStore 为 null 时维持既有失效语义。红验证：`TestDynCodeGenModuleLifecycle#testLazyGenBizObjFileVisibleInPublishedStore` 修复前失败于 `懒生成路径不能把已发布的mergedStore置空 ==> expected: not <null>`（genBizObjFiles 尾部 clearMergedStore 置空字段）。模块测试：nop-dyn-service 30/0/1、nop-dyn-web 2/0/0（资源查找链回归）。

### [P2] 租户模式下 getTenantResourceStore 恒返回 null：租户 merged store 从未被构建，租户动态资源经 VFS 不可见

- **文件**: `nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/codegen/DynCodeGen.java:186-188,351-361`；`nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/codegen/InMemoryCodeCache.java:104-106,352-369`
- **维度**: D1/D8（初始化链路缺失）
- **证据**:
```java
@Override
public IResourceStore getTenantResourceStore(String tenantId) {
    return getTenantCodeCache(tenantId).getMergedStore();   // mergedStore 仅在 getResourceStore() 中赋值
}
...
public synchronized void reloadModel() {
    InMemoryCodeCache cache = getCodeCache();
    if (cache.getTenantId() == null) {        // 租户缓存被显式跳过，不会调用 getResourceStore()
        IResourceStore store = cache.getResourceStore();
        VirtualFileSystem.instance().updateInMemoryLayer(store);
        ...
    }
}
```
- **现状**: `initTenantCache`→`addEnabledModulesToCache`→`addModule`→`genModuleCoreFiles` 全链只 `clearMergedStore()`；全模块 grep 确认 `getResourceStore()` 仅被 `reloadModel()` 的非租户分支调用。因此租户缓存的生命周期内 `mergedStore` 恒为 null，`getTenantResourceStore` 恒返回 null，DeltaResourceStore.getTenantStore0 静默回退到全局基础 store（已核对 DeltaResourceStore.java:152-160 的 null 回退）。
- **风险**: 开启租户（NopDynModule 表 useTenant）时，租户专属的动态页面/模型资源无法通过 VFS 读取，功能整体缺失且无报错（静默降级）。
- **建议**: `getTenantResourceStore` 改为 `getTenantCodeCache(tenantId).getResourceStore()`（其内部带缓存与懒生成回调），或在租户模块加载后重建 merged store。
- **误报排除**: 已核对 getResourceStore/getMergedStore/clearMergedStore 的全部调用点（仅 reloadModel 非租户分支与 DeltaResourceStore 租户查找链），确认租户路径无 merged store 构建入口；已核对 DeltaResourceStore 对 null 的回退行为（不抛错，故为静默缺失）。

> **处置（fix-ai-check 分支，2026-08-28）**: 复查非问题。已被 plan344 修复覆盖（commit 0cfa0dba）：`getTenantResourceStore` 改为 `getTenantCodeCache(tenantId).getResourceStore()` 惰性构建（DynCodeGen.java L192-196，含 prepare 回调的 DynResourceStore，clearMergedStore 置空后下次访问自动重建），不再直接读取恒为 null 的 mergedStore 字段。钉定测试 `TestInMemoryCodeCacheContract#testTenantResourceStoreLazilyBuilt`（直构 DynCodeGen + daoProvider/ormTemplate 桩走真实 initTenantCache 链，断言非 null）与 `#testFreshCacheMergedStoreNullUntilResourceStoreBuilt`（契约：新缓存 getMergedStore 恒 null、getResourceStore 惰性构建、clear 后重建）本次跑绿。模块测试：nop-dyn-service 30/0/1。

### [P2] OrmModelToDynEntityMeta 对存量模块转换时实体名 simple/full 不匹配导致全删重建，关联元数据无条件 add 会重复

- **文件**: `nop-dyn/nop-dyn-dao/src/main/java/io/nop/dyn/dao/model/OrmModelToDynEntityMeta.java:66-68,106,121,125-137,147-157,201,214,277`
- **维度**: D1（键口径不一致/重复累积）
- **证据**:
```java
private NopDynEntityMeta makeEntityMeta(String entityName) {     // 传入 ORM 全名
    NopDynEntityMeta entityMeta = entityMetas.get(entityName);   // map 以存量 entityName（简名）为 key
    if (entityMeta == null) {
        entityMeta = new NopDynEntityMeta();
        entityMeta.setEntityName(StringHelper.removeHead(entityName, this.entityPackagePrefix)); // 存简名
        ...
}
private void removeNotExistingMetas(IOrmModel ormModel, NopDynModule dynModule) {
    ...
    if (ormModel.getEntityModel(entityMeta.getEntityName()) == null)  // 用简名查全名索引，恒 miss → 全部删除
        it.remove();
}
...
entityMeta1.getRelationMetasForEntity().add(relMeta1);           // 无去重，无条件 add
```
- **现状**: `entityMetas` 以简名为 key，`makeEntityMeta`/`removeNotExistingMetas` 却用 ORM 全名（DynEntityMetaToOrmModel 导出时 `setName(fullEntityName)`）做查找。`removeNotExisting` 参数与 `removeNotExistingMetas` 的存在表明该类设计支持对存量模块转换，但实际效果是：先因名字口径不一致把存量实体全部删除重建（entityMetaId 主键全部漂移，级联的 prop/relation 元数据随之重建），且 `transformEntityMeta`/m2m 分支的 relation meta 是无条件 add，同轮内也会叠加。
- **风险**: 当前调用方（importExcel/generateByAI）都传入新建模块所以未触发；一旦按设计意图对已有模块重导入（removeNotExisting=true），主键全量重建破坏外键引用，关系元数据翻倍。
- **建议**: 统一名字口径（makeEntityMeta 先做 removeHead 再查 map），relation meta 按 (relationName, refEntity) 去重后再 add。
- **误报排除**: 已核对 DynEntityMetaToOrmModel.transformEntityModel 导出的实体名为 fullEntityName、NopDynEntityMeta.getEntityName 存简名、OrmModel.getEntityModel 按全名索引；已确认当前两个调用方均传入 newEntity 故标注为潜伏（P2 而非 P1）。

> **处置（fix-ai-check 分支，2026-08-28）**: 已修复（本次）。修复内容（OrmModelToDynEntityMeta.java）：(1) `makeEntityMeta` 先 `removeHead` 统一到简名再查 entityMetas，并同步 put 新建 meta（修复重导入时新建重复同名 meta 导致主键漂移/级联重建）；(2) `removeNotExistingMetas` 改用 `entityExists`（简名或 entityPackagePrefix+简名二选一命中即存在），不再把仍存在的实体误删后重建；(3) 新增 `addRelationMeta` 按 relationName 去重（替换旧 meta）后 add，消除重复转换时的关联元数据翻倍。邻接缺陷 2 处一并修复（审计未直接报告但同链路阻断）：`makeProp` 新建 prop 未写回 propMetas 查找表（首轮转换时 to-one 关联因 `propMetas.get(join.leftProp)` 恒 miss 被静默丢弃）、`makeEntityMeta` 新建 meta 未写回 entityMetas（同轮内引用同一实体会新建重复 meta——红调试实测单轮即产生 2×AppEntity/2×OtherEntity）。红验证：`TestOrmModelToDynEntityMetaReuse#testTransformExistingModuleReusesEntityMeta` 修复前失败于 `存量实体必须被复用，不能新建同名meta ==> expected: <1> but was: <2>`；`#testRemoveNotExistingKeepsExistingEntity` 修复前失败于 assertSame（仍存在的实体被删除后重建）；`#testRelationMetaNotDuplicatedOnRetransform` 在名字口径修复后、去重实施前的中间态失败于 `expected: <1> but was: <2>`（临时禁用 removeIf 复核确认）。模块测试：nop-dyn-dao 14/0/0。

### [P2] MiddleEntityInfo 仅两个槽位，共享中间表名的第 3 个 m2m 关联被静默覆盖丢失

- **文件**: `nop-dyn/nop-dyn-dao/src/main/java/io/nop/dyn/dao/model/DynEntityMetaToOrmModel.java:75-111,287-292,499-572`
- **维度**: D1（边界条件）
- **证据**:
```java
static class MiddleEntityInfo {
    OrmEntityModel entityModelA;
    OrmEntityModel entityModelB;
    NopDynEntityRelationMeta relationA;
    NopDynEntityRelationMeta relationB;
    void addRelation(OrmEntityModel entityModel, NopDynEntityRelationMeta rel) {
        if (relationA == null) { this.entityModelA = entityModel; this.relationA = rel; }
        else { this.entityModelB = entityModel; this.relationB = rel; }   // 第 3 个起直接覆盖 B 槽
    }
}
...
middleInfos.computeIfAbsent(middleEntityName, k -> new MiddleEntityInfo()).addRelation(entityModel, rel);
```
- **现状**: 中间实体名默认由 `guessMiddleEntityName()` 生成 = 两个 bizObjName 排序拼接（NopDynEntityRelationMeta.java:40-55），同一对实体间定义第二个 m2m 关联（未显式指定 middleEntityName）时，4 次 addRelation 挤占 2 个槽位：第 3 次覆盖 relationB，第 4 次再覆盖，第一组关联的映射信息丢失，且 `addToManyRelation`/共享表 filters 基于被覆盖后的 relA/relB 构建，产出错误模型，无任何告警。
- **风险**: 同对实体多 m2m 关联（合法建模）时生成错误的中间表映射/过滤器，数据串表；静默失败难以排查。
- **建议**: addRelation 槽位已满时抛出明确异常，要求显式设置 middleEntityName；或按 (relationName) 维度支持多组。
- **误报排除**: 已核对 guessMiddleEntityName 的对称命名规则、handleRelationMeta 的 m2m 分支和 addMiddleTables 对 relA/relB 的唯一消费方式，确认两个槽位只够表达一对实体的双向关系。

> **处置（fix-ai-check 分支，2026-08-28）**: 复查非问题。已被 plan344 修复覆盖（commit 0cfa0dba）：`MiddleEntityInfo.addRelation` 第三个关系抛 `ERR_DYN_MIDDLE_ENTITY_CONFLICT`（带 middleEntityName/relationName 参数，DynEntityMetaToOrmModel.java L106-111），不再静默覆盖 relationB。钉定测试 `TestMiddleEntityInfo#testThirdRelationRejected`/`#testTwoRelationsAllowed`（nop-dyn-dao，plan344 新建测试目录并补 junit 依赖）本次跑绿。模块测试：nop-dyn-dao 14/0/0。

### [P2] 动态列 precision 未填时默认置 1，VARCHAR 属性会生成 VARCHAR(1) 截断风险

- **文件**: `nop-dyn/nop-dyn-dao/src/main/java/io/nop/dyn/dao/model/DynEntityMetaToOrmModel.java:413-427,434-448`（同型逻辑还有 toOrmDomain L475-489）
- **维度**: D1（错误的默认值处理）
- **证据**:
```java
if (sqlType.isAllowPrecision()) {
    if (propMeta.getPrecision() != null) {
        ret.setPrecision(propMeta.getPrecision());
    } else {
        ret.setPrecision(1);        // 兜底值 1
    }
}
```
- **现状**: NopDynPropMeta.precision 列可空（model/nop-dyn.orm.xml 中 PRECISION 无 mandatory）。真实表模式（ENTITY_STORE_TYPE_REAL）下，用户创建 VARCHAR/DECIMAL 属性而未填长度时，生成的 ORM 列 precision=1。
- **风险**: 由该模型驱动的建表 DDL/长度校验按 1 处理，字符串写入被截断或校验失败；错误值隐蔽（不抛错）。
- **建议**: 未指定 precision 时按 stdSqlType 给出合理默认（如 VARCHAR=50/255）或直接抛校验错误，强制显式配置。
- **误报排除**: 已核对 orm 模型中 precision 列无 mandatory 且无 defaultValue；已核对 NopDynPropMeta.getPrecision() 返回可空 Integer；导出模板（_app.orm.xml）里所有列均显式带 precision，说明 DB 存量数据通常有值，但新建属性路径无强制。

> **处置（fix-ai-check 分支，2026-08-28）**: 复查非问题。已被 plan344 修复覆盖（commit 0cfa0dba）：toColumnModel 的 propMeta/domain 两分支与 toOrmDomain 共三处 `setPrecision(1)` 统一改为 `defaultPrecision(sqlType)`（DynEntityMetaToOrmModel.java L441-443/462-464/537-539：字符串/二进制类取 100 对齐 NOP_NAME 惯例，DECIMAL 取 38，scale 缺省 0 不变）。钉定测试 `TestDynCodeGenModuleLifecycle#testRealTableColumnDefaultPrecision`（真实表实体全链路 round-trip，断言 VARCHAR 缺省 100/DECIMAL 缺省 38/显式 50 保留）本次跑绿。模块测试：nop-dyn-service 30/0/1。

### [P3] NopDynPageBizModel.getPage 硬编码 "/pages/" 与 ".page.json"，与 pageGroup 字段、getPagePath 的 ".page.yaml" 契约漂移

- **文件**: `nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/entity/NopDynPageBizModel.java:80-105`；`nop-dyn/nop-dyn-dao/src/main/java/io/nop/dyn/dao/entity/NopDynPage.java:16-19`
- **维度**: D8
- **证据**:
```java
// getPage: 只接受 /{moduleId}/pages/{pageName}.page.json，且仅按 pageName+module 查询
if (!StringHelper.startsWithAt(path, "/pages/", pos)) throw ...
String pageName = StringHelper.removeTail(path.substring(pos + "/pages/".length()), POSTFIX_PAGE_JSON);
// NopDynPage.getPagePath: 使用 pageGroup 字段与 .page.yaml
return "/" + nopModuleId + "/" + getPageGroup() + "/" + getPageName() + ".page.yaml";
```
- **现状**: pageGroup 列 defaultValue="pages" 且可改；getPage 忽略 pageGroup 固定匹配 "pages" 段，查询也不含 pageGroup 条件。默认配置下可用，pageGroup 非 "pages" 时按 getPagePath 生成的路径无法通过 getPage 取回。
- **风险**: 非默认分组的页面无法经该 API 访问；两种后缀并存易误导维护者。
- **建议**: getPage 解析并过滤 pageGroup；统一页面后缀约定。
- **误报排除**: 已核对 orm 模型 pageGroup 列定义与 getPagePath/getPage 两处路径构造逻辑。

> **处置（fix-ai-check 分支，2026-08-28）**: 已修复（本次）。NopDynPageBizModel.getPage 重写路径解析：按 `/{moduleId}/{pageGroup}/{pageName}` 结构从路径解析分组段（不再硬编码匹配 "/pages/"），查询增加 `pageGroup` 等值过滤（列 mandatory 且 defaultValue="pages"，存量行必有值），后缀兼容 `.page.json`（历史契约）与 `.page.yaml`（getPagePath/genPageFile 的实际契约）——getPagePath 生成的路径现可经该 API 取回。附加缺陷一并修复（报告未提及）：原实现的 `module.moduleName` 过滤字段在 NopDynPage.xmeta 中未暴露（仅 module 关联与 module.displayName 可查询），原查询即便路径合法也必抛 `unknown-query-prop`；现改为按路径 nopModuleId 解析 NopDynModule 主键后以 `moduleId` 外键过滤。红验证：`TestNopDynPageBizModel#testGetPageRespectsPageGroupAndYamlPostfix` 修复前失败于 `NopException(nop.err.dyn.invalid-page-path, path=/app/demo/custom/main.page.json)`——custom 分组路径被硬编码 "/pages/" 校验直接拒绝，与审计证据"非默认分组的页面无法经该 API 访问"吻合。模块测试：nop-dyn-service 30/0/1。

### [P3] getRefPropNameFromColCode 的 endsWith("id") 条件过宽，普通列名（如 valid/paid）误判为外键列

- **文件**: `nop-dyn/nop-dyn-dao/src/main/java/io/nop/dyn/dao/model/OrmModelToDynEntityMeta.java:280-288`
- **维度**: D1
- **证据**:
```java
private String getRefPropNameFromColCode(String colCode, String refEntityName) {
    if (colCode.equalsIgnoreCase("_id") || colCode.endsWith("id"))
        return refEntityName;
    if (StringHelper.endsWithIgnoreCase(colCode, "_id")) { ... }
```
- **现状**: 首个条件 `endsWith("id")` 覆盖了几乎所有以 id 结尾的单词，`_id` 子句被短路；仅当列设置了 AI 专用扩展属性 orm:ref-table 时才进入（L257-260），影响面小。
- **风险**: 名字以 id 结尾但非外键的列（valid、paid）生成多余的 relation meta。
- **建议**: 收紧为 `endsWithIgnoreCase(colCode, "_id") || colCode.equals("id")`。
- **误报排除**: 已确认调用入口要求 EXT_ORM_REF_TABLE 扩展属性存在，故降为 P3。

> **处置（fix-ai-check 分支，2026-08-28）**: 复查非问题。已被 plan344 修复覆盖（commit 0cfa0dba）：条件重排为 `equalsIgnoreCase("_id")` → `endsWithIgnoreCase("_id")`（取前缀 camelCase）→ `endsWith("id")` → `+Obj` 兜底（OrmModelToDynEntityMeta.java L280-291），小写 `user_id` 不再被 `endsWith("id")` 短路，与大写 `USER_ID` 行为一致。钉定测试 `TestOrmModelToDynEntityMetaColCode`（5 用例：小写/大写 _id 后缀、精确 _id 列、userId 对照、非 id 列兜底）本次跑绿。模块测试：nop-dyn-dao 14/0/0。

### [P3] DynCodeGen.destroy 仅清 tenantCache 容器，不调用各租户 InMemoryCodeCache.clear()，on-unload 钩子不执行

- **文件**: `nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/codegen/DynCodeGen.java:97-107`
- **维度**: D4/D2（资源清理不完整）
- **证据**:
```java
@PreDestroy
public void destroy() {
    ...
    codeCache.clear();       // 全局缓存正常走 runOnUnloadModule
    tenantCache.clear();     // 仅清 key→AtomicReference 容器，租户 InMemoryCodeCache 的模块未收到 unload 事件
    ...
}
```
- **现状**: 全局 codeCache.clear() 会为每个模块执行 on-unload.xpl；租户缓存只是从 map 移除引用，模块级卸载钩子（可能含清理逻辑）不执行。
- **风险**: 停机阶段租户模块的清理副作用缺失；多为演示/演示性影响，正常 JVM 退出影响有限。
- **建议**: destroy 时遍历 `tenantCache.getAllKeys()` 逐个 `getTenantCodeCache(id).clear()` 后再 clear 容器。
- **误报排除**: 已核对 InMemoryCodeCache.clear() 的行为（runOnUnloadModule + 清空 store）与 clearForTenant 仅 remove 容器项的实现。

> **处置（fix-ai-check 分支，2026-08-28）**: 复查非问题。已被 plan344 修复覆盖（commit 0cfa0dba）：`destroy` 对 `tenantCache.getAllKeys()` 逐个 `removeTenantCache(tenantId)` 后执行该租户 cache 的 `clear()`（触发 on-unload 钩子）再整体清空容器（DynCodeGen.java L103-110），`clearForTenant` 同语义；新增的 `removeTenantCache` 辅助方法 getIfPresent 后 remove、不触发 cache loader 初始化。钉定测试 `TestInMemoryCodeCacheContract#testClearForTenantRunsCacheClear`（反射预置计数 clear() 的 cache 子类，断言 clear 被执行、条目移除、幂等）本次跑绿。模块测试：nop-dyn-service 30/0/1。

### [P3] importExcel 使用 APP_STATUS_* 常量设置模块状态、generateByAI 硬编码模块名 "app-demo"

- **文件**: `nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/entity/NopDynModuleBizModel.java:65,101-102`
- **维度**: D1（语义误用/演示残留）
- **证据**:
```java
entity.setStatus(NopDynDaoConstants.APP_STATUS_UNPUBLISHED);   // 模块应使用 MODULE_STATUS_*（当前值恰好同为 0）
...
entity.setModuleName("app-demo");   // AI 生成固定模块名，重复调用将撞唯一约束
```
- **现状**: `_NopDynDaoConstants` 中 APP_STATUS_UNPUBLISHED/MODULE_STATUS_UNPUBLISHED 值均为 0，暂无行为差异；generateByAI 二次调用会因模块名固定而失败。
- **风险**: 常量重构（如调整状态枚举值）时引入真实缺陷；AI 生成流程不可重复执行。
- **建议**: 改用 MODULE_STATUS_* 常量；generateByAI 的模块名参数化或查重。
- **误报排除**: 已核对 _NopDynDaoConstants 两组常量当前取值相同；已确认 generateByAI 无去重逻辑。

> **处置（fix-ai-check 分支，2026-08-28）**: 已修复（两段处置）。generateByAI 固定模块名 "app-demo" 部分：已被 plan344 修复覆盖（commit 0cfa0dba）——签名增加 `@Optional moduleName` 参数（空值回退 "app-demo"）+ `findFirstByExample` 同名查重抛 `ERR_DYN_MODULE_NAME_EXISTS` + 补 checkDataAuth。常量误用部分：本次将 importExcel/generateByAI 中设置 NopDynModule status 的两处 `APP_STATUS_UNPUBLISHED` 改为 `MODULE_STATUS_UNPUBLISHED`（NopDynModuleBizModel.java L69/L117；模块状态字典为 dyn/module-status）。两组常量当前取值相同（0/10），无行为差异，属编译级语义修正，免行为红测试；两处改动行由既有 `TestNopDynModuleBizModel#testGenerateByAI`/`#testGenerateByAIDuplicateModuleNameRejected` 覆盖执行路径（本次跑绿）。模块测试：nop-dyn-service 30/0/1。follow-up：NopDynModule.xbiz 的 publish/unpublish 同样以 APP_STATUS_* 设置模块状态（同族误用、值相同无行为差异），xbiz XPL 脚本修改超出本条目范围未动，建议后续统一。

### [P3] getTenantCodeCache 并发首次访问可能重复执行 initTenantCache（重复 DB 全量读取与代码生成）

- **文件**: `nop-dyn/nop-dyn-service/src/main/java/io/nop/dyn/service/codegen/DynCodeGen.java:143-151`
- **维度**: D6（并发重复初始化开销）
- **证据**:
```java
private InMemoryCodeCache getTenantCodeCache(String tenantId) {
    return tenantCache.get(tenantId).updateAndGet(k -> {
        if (k == null) {
            return initTenantCache(tenantId);   // 多线程同时见到 null 时都执行（DB 查询+逐模块代码生成）
        } else {
            return k;
        }
    });
}
```
- **现状**: Caffeine loadingCache 只保证容器项存在（loader 返回空 AtomicReference），初始化由 updateAndGet 承担；两个线程同时读到 null 会各自完整执行 initTenantCache，仅结果其一被保留。
- **风险**: 租户首次访问时的重复重活（全模块查询+模板生成），浪费但结果一致。
- **建议**: 改用 `tenantCache.computeIfAbsent` 语义（如 AtomicReference 内再放 done 标记）或对 init 加锁。
- **误报排除**: 已核对 LocalCache 带 loader 的 get() 语义（loadingCache.get 返回 loader 结果，不执行 init 逻辑）确认 null 判定确实依赖 updateAndGet；已确认 updateAndGet 对竞争只保证最终值原子性，不保证函数单次执行。

> **处置（fix-ai-check 分支，2026-08-28）**: 已修复（本次）。`getTenantCodeCache` 改为在容器 AtomicReference 上 double-check 加锁：快读非空直接返回，否则 `synchronized(ref)` 内复查并执行 initTenantCache 后 `ref.set`（DynCodeGen.java L150-169）；LocalCache loadingCache 保证同 key 返回同一 AtomicReference 实例，竞争线程在锁上等待后直接复用，initTenantCache（全模块 DB 查询+逐模块代码生成）保证单次执行。方法与 `initTenantCache` 由 private 放宽为包级可见供同包单测覆盖（无行为变化）。红验证：`TestDynCodeGenTenantCacheInit#testConcurrentTenantInitExecutedOnce`（latch 门控：A 阻塞在 init 内时 B 发起同租户首次访问）修复前失败于 `并发首次访问时initTenantCache必须只执行一次，修复前两个线程都读到null会各自执行一遍 ==> expected: <1> but was: <2>`；修复后两线程返回同一实例且 init 计数为 1。模块测试：nop-dyn-service 30/0/1。
