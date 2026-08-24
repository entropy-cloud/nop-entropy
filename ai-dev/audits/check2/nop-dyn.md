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
