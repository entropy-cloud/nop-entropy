# nop-biz 实现代码检查报告（check2）

- 检查日期: 2026-08-23
- 模块路径: nop-service-framework/nop-biz
- 文件数: 101（src/main/java），其中 12 个为 `_gen/` 生成文件按规则跳过，实际审计 89 个
- 覆盖范围声明: 深读 89 个非生成文件中的约 60 个核心实现类（CrudBizModel、OrmEntityCopier、ObjMetaBasedValidator、BizObjectBuilder/BizObjectBuildHelper/BizObjectImpl/BizObjectManager、BizModelToGraphQLDefinition、4 个装饰器、MakerCheckerTryServiceAction、BizProxyInvocationHandler、BizActionService、DevDoc/DevStat/DevToolBizModel、TreeEntityHelper、ManyToManyTool、DownloadHelper、ObjDictLoader 等），其余接口/常量/模型类（api/、model/、dev/beans/、BizConstants、BizErrors）逐文件快速通读；模式扫描（@Inject private、@Value、SimpleDateFormat、catch 吞噬、RuntimeException、静态可变状态）覆盖 100%。未覆盖区域: 无（`_gen/` 12 个生成文件按纪律跳过）。结论均经跨模块源码交叉验证（nop-graphql-core 鉴权与执行器、nop-orm SQL 生成、nop-xdefs XDEF、模块内 beans.xml）。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 3 |
| P2 | 5 |
| P3 | 5 |

## 发现列表

### [P1] 树查询 SQL 未过滤逻辑删除记录，findListForTree/findPageForTree 在存在已删除节点时整体抛异常

- **文件**: `nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/TreeEntityHelper.java:78-110`、`nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/CrudBizModel.java:1907-1910`
- **维度**: D1
- **证据**:
```java
// TreeEntityHelper.buildTreeEntityBaseSql：手工拼 CTE，只处理用户 filter，无 deleteFlag 条件
sb.sql("with recursive tree_page as (\n");
appendTreeSql(sb, "b", entityName, pkProp, dispProp, parentProp, levelProp, sortProp, rightJoinProp);
sb.where();
if (filter != null) {
    DaoQueryHelper.appendFilter(sb, "b", filter);
}
...
// CrudBizModel.getEntityListByTreeEntity：随后用 batchGet 装载实体
protected List<T> getEntityListByTreeEntity(List<StdTreeEntity> list, IServiceContext context) {
    List<String> idList = list.stream().map(StdTreeEntity::getId).collect(Collectors.toList());
    return batchGet(idList, false, context);   // batchGet 中对 orm_logicalDeleted() 直接抛异常
}
```
- **现状**: 常规 QueryBean 查询路径会由 `GenSqlHelper.appendExampleFilter/addQueryConditions` 追加 `deleteFlag = 0`（见 `nop-persistence/nop-orm/.../sql/GenSqlHelper.java:467-477`），但 `TreeEntityHelper` 手工构建的递归 CTE SQL 完全没有加逻辑删除条件，`orm().findPage(sql, ...)` 是裸 SQL 也不会自动注入。随后 `getEntityListByTreeEntity` 调用 `batchGet(idList, false, context)`，而 `batchGet`（CrudBizModel.java:1040-1042）对 `entity.orm_logicalDeleted()` 抛 `UnknownEntityException`。
- **风险**: 启用逻辑删除的树形实体只要子树中存在任何一条已删除记录，`findListForTree`/`findPageForTree`/`doFindListForTree`（以及 `findTreeEntityList` 的 count/展示）就会整体失败或返回含已删除节点的错误结果（total 与 findPage 口径不一致），属于特定但常见数据状态下触发的功能级错误。
- **建议**: 在 `buildTreeEntityBaseSql` 中按 `entityModel.isUseLogicalDelete()` 追加 `deleteFlag` 过滤（锚点段 b 与递归段 o 都要加），或在 `getEntityListByTreeEntity` 中跳过（而非抛异常）已删除节点并同步过滤树列表。
- **误报排除**: 已核对 `GenSqlHelper`（正常查询路径确实加 deleteFlag，证明树 SQL 属遗漏而非全局约定）；核对 `DaoQueryHelper.appendFilter` 仅翻译 filter 节点不涉及 deleteFlag；核对 `batchGet` 抛异常逻辑与 `deleted_get`/`getEntityById` 会返回已删除记录的事实（CrudBizModel.java:1005-1014、914-919），确认"查得到、装不上"的矛盾真实存在。
> **处置（fix-ai-check 分支，2026-08-26）**: 已修复. TreeEntityHelper 三个方法增加 `IEntityModel` 参数，锚点段(b)与递归段(o)按 `isUseLogicalDelete()` 追加 `deleteFlag = ?`（绑定值与 GenSqlHelper.getBooleanLiteral 语义一致：BOOLEAN→false/VARCHAR→"0"/其余→0）；CrudBizModel 新增 `getTreeEntityModel()` 经 `orm().getOrmModel()` 解析传入，实体模型未注册时返回 null 保持原有行为。新增 TestTreeEntityHelper 5 用例（双段过滤/count SQL/未启用与 null 模型不过滤/filter 与根条件 and 链接/绑定值取型）；stash 红=编译级（旧签名无 IEntityModel 参数，能力不存在）。修复中新发现同族缺陷（未修，记 follow-up）：树查询 CTE 同样缺少租户（tenant）过滤条件，GenSqlHelper 常规路径会追加而手工 CTE 不会，与本条同源，影响面需独立裁定。

### [P1] DevStatBizModel 默认启用且所有操作无 @Auth，SQL 统计信息（含慢查询参数）外泄、统计可被任意清空

- **文件**: `nop-service-framework/nop-biz/src/main/java/io/nop/biz/dev/DevStatBizModel.java:19-48`、`nop-service-framework/nop-biz/src/main/resources/_vfs/nop/biz/beans/biz-defaults.beans.xml`（nopDevStatBizModel 注册段）
- **维度**: D5
- **证据**:
```java
@Locale("zh-CN")
@BizModel("DevStat")
public class DevStatBizModel {
    @BizMutation
    @Description("清空所有统计信息")
    public void clearStats() { GlobalStatManager.instance().clear(); }

    @BizQuery
    @Description("jdbc调用的统计信息")
    public List<JdbcSqlStatValue> jdbcSqlStats(...) { ... }
```
```xml
<!-- beans.xml：属性缺失时条件为真 => 默认注册 -->
<bean id="nopDevStatBizModel" class="io.nop.biz.dev.DevStatBizModel">
    <ioc:condition>
        <if-property name="nop.biz.stat.enabled" enableIfMissing="true"/>
    </ioc:condition>
</bean>
```
- **现状**: 该 BizModel 的 4 个操作均无 `@Auth` 注解。平台鉴权语义（`nop-graphql-core/.../GraphQLActionAuthChecker.java:118-124`）为 `auth == null => isAllowAccess == true`，即无 auth 元数据的操作对任意调用者（含未登录会话）开放。与同模块 `DictProviderBizModel.getDict` 明确标注 `@Auth(roles = "user")` 的惯例形成对比。`JdbcSqlStatValue`（nop-core）暴露 `sql`（完整 SQL 文本）、`dataSource`、`file`、`lastSlowParameters`（慢 SQL 绑定参数，可能含 PII/凭据类数据）。
- **风险**: 生产默认配置下任意 GraphQL 调用者可读取全部 JDBC/RPC 统计（SQL 文本、数据源名、慢查询参数等信息泄露），并可调用 `clearStats` 清空全局统计（监控数据破坏）。
- **建议**: 为 DevStat 的 4 个操作添加 `@Auth(roles = "admin")`（或至少 `roles = "user"`）；或将 `nop.biz.stat.enabled` 的默认值改为 false / 与 `nop.debug` 绑定（与 DevDoc/DevTool 的注册条件一致）。
- **误报排除**: 已核对 `GraphQLActionAuthChecker.isAllowAccess` 的 auth==null 放行逻辑与 `GraphQLExecutor.isPublicAccess` 注释（"auth==null 视为公开"）；核对 beans.xml 中 `enableIfMissing="true"`（默认启用）与 DevDoc/DevTool 仅在 `nop.debug` 下注册的差异；核对 `JdbcSqlStatValue` 字段列表确认敏感字段存在；未发现任何为无 auth 操作补默认权限的 Initializer（仓库内 IGraphQLBizInitializer 实现仅 OrmBizInitializer 与 CrudBizInitializer）。
> **处置（fix-ai-check 分支，2026-08-26）**: 已修复. 4 个操作（clearStats/jdbcSqlStats/rpcServerStats/rpcClientStats）全部加 `@Auth(roles = "admin")`（与 nop-code NopCodeIndexBizModel 等既有惯例一致）；保留 `nop.biz.stat.enabled` 默认启用不变（改为默认关闭会改变既有部署的可观测性行为，超出最小修复）。新增 TestDevStatBizModel.testAllOperationsRequireAdminRole；stash 红=旧代码 clearStats 无 @Auth（assertNotNull 失败）。

### [P1] xbiz `<cache-evicts>` 配置被解析但从未被消费，缓存淘汰静默失效

- **文件**: `nop-service-framework/nop-biz/src/main/java/io/nop/biz/decorator/CacheActionDecoratorCollector.java:49-56`
- **维度**: D1/D8
- **证据**:
```java
@Override
public void collectDecorator(BizActionModel actionModel, List<IServiceActionDecorator> decorators) {
    BizCacheModel cacheModel = actionModel.getCache();
    if (cacheModel != null) {
        decorators.add(
                new CacheActionDecorator(cacheProvider, cacheModel.getCacheName(), cacheModel.getCacheKeyExpr()));
    }
    // 未处理 actionModel.getCacheEvicts()
}
```
- **现状**: xbiz XDEF（`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/biz/xbiz.xdef:52-54`）明确定义了 `<cache-evicts><cache-evict cacheName=... cacheKeyExpr=.../></cache-evicts>`，生成模型 `_BizActionModel` 也带有 `getCacheEvicts()`（List&lt;BizCacheEvictModel&gt;，含 cacheName/cacheKeyExpr 字段），但全仓库对 `getCacheEvicts()` 的调用为零（grep 全仓库仅生成类自身）。xbiz 侧只有 `<cache>`（读缓存）被装配，`<cache-evict>`（淘汰）被静默丢弃；Java 注解路径的 `@CacheEvicts` 反而在 `collectDecorator(IFunctionModel, ...)` 中有完整支持（第 39-46 行），形成明显不对称。
- **风险**: 用户按 XDEF 契约在 xbiz 中配置缓存淘汰后，淘汰逻辑完全不执行，缓存中持续返回过期数据（数据正确性问题），且无任何报错提示，极难排查。
- **建议**: 在 `collectDecorator(BizActionModel, ...)` 中补齐：遍历 `actionModel.getCacheEvicts()`，为每一项 `new CacheEvictActionDecorator(cacheProvider, evict.getCacheName(), evict.getCacheKeyExpr())`。
- **误报排除**: 已核对 XDEF 定义、`_BizActionModel.getCacheEvicts()` 生成代码、`BizCacheEvictModel` 字段（cacheKeyExpr 类型与 CacheEvictActionDecorator 构造参数完全匹配），并全仓库 grep 确认无任何其他消费者；对比注解路径 `@CacheEvicts` 的处理确认这是遗漏而非有意设计。
> **处置（fix-ai-check 分支，2026-08-26）**: 已修复. `collectDecorator(BizActionModel,...)` 遍历 `actionModel.getCacheEvicts()` 为每一项生成 `CacheEvictActionDecorator(cacheProvider, cacheName, cacheKeyExpr)`，与注解路径 @CacheEvicts 语义对齐。新增 TestCacheActionDecorator.testCollectorConsumesXbizCacheEvicts（含 action 成功后淘汰缓存的执行断言）；stash 红=decorators 0 vs 1（配置被静默丢弃）。注：默认装配下两个 collector 仍未注册（见下一条 P2 暂缓裁定），本条修复使注册后 xbiz 路径能力完整。

### [P2] CacheActionDecoratorCollector / TransactionActionDecoratorCollector 未在任何 beans.xml 注册，默认配置下 biz 装饰器链为空

- **文件**: `nop-service-framework/nop-biz/src/main/java/io/nop/biz/decorator/CacheActionDecoratorCollector.java:23`、`nop-service-framework/nop-biz/src/main/java/io/nop/biz/decorator/TransactionActionDecoratorCollector.java:21`、`nop-service-framework/nop-biz/src/main/resources/_vfs/nop/biz/beans/biz-defaults.beans.xml`（actionDecoratorCollectors 收集段）
- **维度**: D7
- **证据**:
```xml
<!-- biz-defaults.beans.xml：按类型收集 IActionDecoratorCollector -->
<property name="actionDecoratorCollectors">
    <ioc:collect-beans only-concrete-classes="true" ioc:ignore-depends="true"
                       by-type="io.nop.biz.decorator.IActionDecoratorCollector"/>
</property>
```
- **现状**: `BizObjectManager.actionDecoratorCollectors` 通过 by-type 收集，但 `CacheActionDecoratorCollector` 与 `TransactionActionDecoratorCollector` 两个实现类在整个仓库（含所有 `_vfs` beans 文件、测试代码）中没有任何 `<bean>` 定义或程序化实例化。平台默认配置下 `@Cache`/`@CacheEvicts`/`@Transactional`（biz 方法级）与 xbiz `<cache>`/`<txn>` 的装饰器均不会生效。
- **风险**: 平台自称支持的 action 装饰器机制（缓存/事务装饰）在默认部署中是死代码；应用层若不知情地依赖 `@Transactional` 注解在 biz 方法上的语义（mutation 已由 `nopGraphQLTransactionOperationInvoker` 在 operation 层兜底开启事务，但 query 类带 @Transactional、以及 `<txn>` 的 txnGroup/propagation 定制均失效）会得到与预期不一致的事务/缓存行为。
- **建议**: 在 `biz-defaults.beans.xml` 中注册两个 collector（CacheActionDecoratorCollector 需要 ICacheProvider 构造参数），或至少在文档/注释中明确"需应用层自行注册"。
- **误报排除**: 已全仓库 grep 两个类名（xml/beans/java 全部文件类型），仅命中源码自身与 ai-dev/docs 文档（未读取内容）；核对 beans.xml 的 by-type 收集机制与 `BizObjectBuildHelper.buildDecorators` 的空列表行为（collectors 为空时返回 emptyList，不报错），确认是"静默不生效"而非启动报错。
> **处置（fix-ai-check 分支，2026-08-26）**: 裁定暂缓. 注册 TransactionActionDecoratorCollector 会平台级改变默认事务行为（mutation 已由 nopGraphQLTransactionOperationInvoker 在 operation 层兜底开事务，装饰器叠加后 query 带 @Transactional、<txn> 的 txnGroup/propagation 定制全部激活，语义需设计核定）；CacheActionDecoratorCollector 注册依赖平台默认 ICacheProvider bean（仓库内不存在，引入全局默认缓存基础设施超出条目范围）；且全仓库无 @Cache/<cache> 使用点，激活属新特性开启而非缺陷修复。决策点：是否引入默认 ICacheProvider bean、事务装饰器与 operation 层事务的分层关系；影响面：所有依赖 nop-biz 默认装配的应用。缓解：下一条（P2 CrudBizInitializer 注入）修复后，应用层自行注册的 collector 已能正确传递到全部 biz 对象（含动态 CRUD）。

### [P2] CrudBizInitializer.setDecoratorCollectors 缺少注入配置，动态 CRUD biz 对象的装饰器恒为 null

- **文件**: `nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/CrudBizInitializer.java:60-62, 76-85`
- **维度**: D7
- **证据**:
```java
public void setDecoratorCollectors(List<IActionDecoratorCollector> collectors) {
    this.collectors = collectors;      // 无 @Inject，beans.xml 也未配置该 property
}
...
@Override
public void initialize(IGraphQLBizObject bizObj, ...) {
    Set<String> base = ConvertHelper.toCsvSet(bizObj.getExtAttribute(BizConstants.GRAPHQL_BASE_NAME));
    if (base != null && base.contains(BizConstants.BASE_CRUD)) {
        DynamicCrudBizModel bean = newBizModelBean(bizObj);
        GraphQLBizModel bizModel = ReflectionBizModelBuilder.INSTANCE.build(bean, typeRegistry, bizModels);
        BizObjectBuildHelper.addDefaultAction(bizObj, bizModel, collectors);  // collectors 恒为 null
    }
}
```
- **现状**: beans.xml 中 `nopCrudBizInitializer` 的 bean 定义不带任何 property；该类其余依赖（daoProvider、bizObjectManager、transactionTemplate、crudToolProvider）都标注了 `@Inject` 可被注解注入，唯独 `setDecoratorCollectors` 既无 `@Inject` 也无 XML property，`collectors` 永远为 null。走 `base=crud` 动态装配的 biz 对象与 `BizObjectBuilder.buildOperations` 主路径（传入 manager 收集的 collectors）行为不一致。
- **风险**: 动态 CRUD biz 对象上的 biz action 完全不经过缓存/事务等装饰器链（即使应用注册了 decorator collector），造成同类对象行为分叉；与主路径的装配差异属于隐蔽的平台规范违背。
- **建议**: 为 `setDecoratorCollectors` 添加 `@Inject`（或在 beans.xml 中为 `nopCrudBizInitializer` 显式配置 `decoratorCollectors` 的 by-type 收集）。
- **误报排除**: 已核对 beans.xml 中 `nopCrudBizInitializer` 定义无 property；核对同类中其他 @Inject setter 依赖注入惯例（如 `nopCrudToolProvider`、`nopBizAuthChecker` 均为无 property 定义 + @Inject 注入，证明 NopIoC 注解注入生效且本 setter 确实被遗漏）；核对 `BizObjectBuildHelper.buildAction` 对 null collectors 的静默容忍行为。
> **处置（fix-ai-check 分支，2026-08-26）**: 已修复. biz-defaults.beans.xml 为 nopCrudBizInitializer 显式配置 `decoratorCollectors`（ioc:collect-beans by-type IActionDecoratorCollector，与 nopBizObjectManager 的收集段逐字一致；不采用 @Inject 是对齐 BizObjectManager.setActionDecoratorCollectors 仅 XML 收集的既有惯例）。新增 TestBizDefaultsBeansWiring（结构断言：bean 存在/property 存在/collect-beans by-type 正确）；stash 红=property 缺失 assertNotNull 失败；JunitBaseTestCase 容器级加载（既有 TestDynamicCrudBizModel）验证 XML 变更后 IoC 容器正常装配。

### [P2] CacheActionDecorator 缓存命中直接返回共享可变对象，存在缓存污染（跨请求数据串改）风险

- **文件**: `nop-service-framework/nop-biz/src/main/java/io/nop/biz/decorator/CacheActionDecorator.java:47-54`
- **维度**: D3
- **证据**:
```java
ICache<Object, Object> cache = cacheProvider.getCache(cacheName);
Object value = cache.get(key);
// 命中缓存直接返回，不触发底层action
if (value != null)
    return value;

Object result = action.invoke(request, selection, ctx);
return cacheResult(cache, key, result);
```
- **现状**: 命中缓存后按引用直接返回缓存对象，无任何防御性拷贝或只读包装；写入侧同样按引用 `cache.put(key, value)`（`putIfNotNull`，第 70-74 行）。biz action 的典型返回值（`PageBean`、`List`、`DictBean`、Map）都是可变容器，GraphQL fetcher / 业务代码 / 调用方后续修改（如 `pageBean.setItems(...)`、list.sort、map.put）会直接改写缓存内容。
- **风险**: 并发条件下一个请求的修改会污染缓存并影响所有后续命中请求（数据串改），且问题偶发难定位。当前仓库内虽无 @Cache 使用点，但该装饰器是平台公开扩展点，配置后即暴露此风险。
- **建议**: 至少在文档中约束"被 @Cache 标注的 action 必须返回不可变对象"；更稳妥的做法是对命中值做深拷贝（JsonTool round-trip 或 cloneInstance），或在写入时冻结（FreezeHelper）。
- **误报排除**: 已核对 `ICache`（nop-commons）是普通引用型 KV 缓存（get/getIfPresent 无拷贝语义）；核对 `cacheResult` 对 CompletionStage 的解包逻辑确认缓存值为最终结果对象本身；确认装饰器无任何只读防护代码。
> **处置（fix-ai-check 分支，2026-08-26）**: 已修复（文档约束，免红测试）. CacheActionDecorator 类 javadoc 明确契约："命中与写入均按共享引用返回/保存，不做防御性拷贝（与 Spring @Cacheable 语义一致），被缓存标注的 action 必须返回不可变对象"。理由：共享引用语义与 Spring @Cacheable 行业标准一致；深拷贝（JsonTool round-trip）对 ORM 实体/惰性对象不安全且每次命中都有性能开销，Freeze 冻结会破坏调用方对返回值的可变性预期，两者都可能改变既有调用行为，超出最小修复边界；仓库内无 @Cache 使用点，实际暴露面为零。纯 javadoc 文档化，无行为变更，故免红测试。

### [P2] BizActionService.callActionAsync 对未知 action 或无 xbiz 的 biz 对象直接 NPE

- **文件**: `nop-service-framework/nop-biz/src/main/java/io/nop/biz/service/BizActionService.java:51-57`、`nop-service-framework/nop-biz/src/main/java/io/nop/biz/api/IBizObject.java:57-59`
- **维度**: D1/D4
- **证据**:
```java
public CompletionStage<ApiResponse<?>> callActionAsync(String bizObjName, String bizAction, ApiRequest<?> request) {
    IBizObject actor = bizObjManager.getBizObject(bizObjName);
    IBizActionModel actionModel = actor.getActionModel(bizAction);   // 可能返回 null；bizModel 为 null 时 NPE
    String executorName = getWorkExecutorBean(actionModel);          // actionModel.getExecutor() NPE
    ...
    if (actionModel.isBizSequential()) {                             // 再次解引用
```
```java
// IBizObject 接口默认实现：
default IBizActionModel getActionModel(String action) {
    return getBizModel().getAction(action);   // getBizModel() 可为 null（无 xbiz 的 Java biz model）
}
```
- **现状**: `BizModel.getAction(name)`（_BizModel.java:115-117，`_actions.getByKey`）对未知名返回 null；纯 Java 注解注册的 biz model（无 xbiz 文件）`getBizModel()` 为 null。两种情况下 `callActionAsync` 都在 `getWorkExecutorBean`/`isBizSequential` 处抛裸 NPE，而不是"未知操作"语义的错误。另外 `BizActionService` 本身也未在仓库任何 beans.xml 中注册（`IBizActionService` 在仓库内无其他实现）。
- **风险**: 调用方传入拼错的 action 名或对 Java-only biz model 调用时得到无上下文的 NullPointerException（future 以 NPE 异常完成），丢失 bizObjName/actionName 排查信息，违背模块错误处理规范。
- **建议**: 在 `getBizObject` 之后判空并使用 `actor.requireAction(bizAction)` 风格的显式校验（抛 `ERR_BIZ_OBJECT_NOT_SUPPORT_ACTION` 携带 bizObjName/actionName）；无 xbiz 的场景可回退到反射构建的 action 元数据或明确报不支持。
- **误报排除**: 已核对 `_BizModel.getAction` 的 `getByKey` null 返回语义、`BizObjectImpl` 中 bizModel 可为 null（`loadBizObjFromModel` 允许 bizModel==null）、`IBizObject.getActionModel` 默认实现无判空；全仓库 grep 确认 `IBizActionService` 无其他实现且无 bean 注册。
> **处置（fix-ai-check 分支，2026-08-26）**: 已修复. `callActionAsync` 在 `getBizObject` 后对 actor/bizModel/actionModel 三级判空，null 时抛既有错误码 `ERR_BIZ_OBJECT_NOT_SUPPORT_ACTION`（中文文案已定义，无需新增），携带 bizObjName/actionName 参数（无 xbiz 的纯 Java biz model 也统一转换）。新增 TestBizActionService 2 用例（未知 action 名/无 bizModel）；stash 红=NPE "Cannot invoke IBizActionModel.getExecutor() because actionModel is null"，与报告描述形态一致。

### [P2] BizProxyInvocationHandler 将 IExecutionContext 形参强转 IServiceContext，接口声明父类型时 ClassCastException

- **文件**: `nop-service-framework/nop-biz/src/main/java/io/nop/biz/proxy/BizProxyInvocationHandler.java:126-131, 160-164`
- **维度**: D1
- **证据**:
```java
// 分类阶段（宽匹配）：
} else if (IExecutionContext.class.isAssignableFrom(arg.getRawClass())) {
    contextIndex = i;
}
// 使用阶段（窄强转）：
return args -> {
    FieldSelectionBean selection = ...;
    IServiceContext context = contextIndex >= 0 ? (IServiceContext) args[contextIndex] : IServiceContext.getCtx();
```
- **现状**: 参数分类用 `IExecutionContext`（父接口，含 IEvalContext 等）判断，调用时却无条件强转为 `IServiceContext`。业务接口（biz model bean 实现的、被收集进 proxy 的接口）方法参数若声明为 `IEvalContext`/`IExecutionContext` 而非 `IServiceContext`，代理调用即抛 ClassCastException。同文件 `buildBeanModelInvoker`（112-116 行）对同类形参正确按 `IEvalContext` 处理，进一步印证这是不一致的窄化。附带问题：`buildBeanModelInvoker` 中 `((IEvalContext) args[index])` 在调用方显式传 null 时会 NPE（index>=0 分支无判空）。
- **风险**: 通过 `bizObj.asProxy()` 进行的接口化调用在特定接口签名下运行期崩溃；该代理是跨模块调用 biz action 的公开机制（BizProxyFactoryBean.build）。
- **建议**: 分类与强转使用同一类型：要么只识别 `IServiceContext.class.isAssignableFrom(...)`，要么按 `IEvalContext` 接收后再适配（`IServiceContext.fromEvalContext`）；`buildBeanModelInvoker` 对 null 参数回退 `IServiceContext.getCtx()`。
- **误报排除**: 已通读 `buildActionFunction`/`buildFunction`/`buildBeanModelInvoker` 全文确认类型不对称；核对 `makeProxy` 收集接口的来源（biz model bean 的全部继承接口 + xbiz implements 声明），确认接口形参类型不受平台约束、可由业务自由声明。
> **处置（fix-ai-check 分支，2026-08-26）**: 已修复. buildFunction 调用处改用 `IServiceContext.fromEvalContext((IEvalContext) args[contextIndex])` 适配（IExecutionContext extends IEvalContext 保证强转类型安全；fromEvalContext 的 instanceof IServiceContext 分支保持原有行为，null 入参透传 null 不变）。新增 TestBizProxyInvocationHandler 3 用例（IEvalScope 实参不再 CCE/IServiceContext 实参透传不变/null 容忍）；stash 红=ClassCastException "EvalScopeImpl cannot be cast to IServiceContext"。报告附带疑点复核为非问题：buildBeanModelInvoker 第 114 行已有 `context == null ? DisabledEvalScope.INSTANCE` 判空兜底，显式传 null 不 NPE。

### [P3] SendForCheckRequest.bizObjName 字段从未被赋值，maker-checker 请求契约漂移

- **文件**: `nop-service-framework/nop-biz/src/main/java/io/nop/biz/makerchecker/MakerCheckerTryServiceAction.java:46-59`
- **维度**: D8
- **证据**:
```java
SendForCheckRequest req = new SendForCheckRequest();
if (userContext != null) {
    req.setMakerId(userContext.getUserId());
    req.setMakerName(userContext.getUserName());
}
req.setMakeTime(...);
req.setRequest(toApiRequest(request, selection, context));
req.setTryMethod(makerCheckerMeta.getTryMethod());
// 无 req.setBizObjName(...) —— SendForCheckRequest.bizObjName 恒为 null
```
- **现状**: `SendForCheckRequest`（@DataBean，含 bizObjName/bizMethod 字段）是发送给 `IMakerCheckerProvider.sendForCheckAsync` 的唯一请求对象；生产端只设置了 bizMethod 而从未设置 bizObjName。仓库内无该 provider 实现可验证消费端是否依赖该字段（接口仅 `isMakerCheckerEnabled(bizObjName, bizMethod)` 另行传参）。
- **风险**: 下游 provider 若按 bean 契约读取 `getBizObjName()` 将得到 null，审批记录无法关联业务对象；属于公开数据契约与实际生产行为的漂移。
- **建议**: 在 `sendForCheck` 中补充 `req.setBizObjName(...)`（BizObjectBuilder 调用 initMakerChecker 时可传入 bizObjName，或由 MakerCheckerTryServiceAction 构造参数携带）。
- **误报排除**: 已全仓库 grep `SendForCheckRequest` 的所有 set 调用点（唯一生产者即本类）；确认 `IMakerCheckerProvider` 在仓库内无实现（无法以消费端证伪，故降为 P3 契约问题而非确凿 bug）。
> **处置（fix-ai-check 分支，2026-08-26）**: 已修复. MakerCheckerTryServiceAction 构造函数增加 bizObjName 参数（仓库内唯一调用方 ObjectDefinitionExtProcessor.initMakerChecker 改传 `bizObj.getBizObjName()`），sendForCheck 中补充 `req.setBizObjName(bizObjName)`。新增 TestMakerCheckerTryServiceAction.testSendForCheckCarriesBizObjName（断言 bizObjName/bizMethod/tryMethod 全部就位）；stash 红=编译级（旧 4 参构造器无 bizObjName，能力不存在）。

### [P3] DevDocBizModel 参数描述回退错误：arg 级描述缺失时返回整个函数的 Description

- **文件**: `nop-service-framework/nop-biz/src/main/java/io/nop/biz/dev/DevDocBizModel.java:190-202`
- **维度**: D1
- **证据**:
```java
String getDescription(String locale, IFunctionModel fn, IFunctionArgument arg) {
    String key = getFunctionDocKey(fn) + "." + arg.getName();
    String text = I18nMessageManager.instance().getMessage(locale, key, null);
    if (text != null)
        return text;
    Description desc = fn.getAnnotation(Description.class);   // 取的是函数级注解
    if (desc != null) {
        return I18nMessageManager.instance().resolveI18nVar(locale, desc.value());
    }
    return null;
}
```
- **现状**: 该方法为参数（arg）生成描述，i18n key 未命中时回退读取 `fn`（函数）的 `@Description`，导致每个参数都显示函数的整体描述。`IFunctionArgument extends IAnnotatedElement`，本应回退到参数自身的注解。
- **风险**: 仅影响开发期文档查询（DevDoc 为 nop.debug 条件注册），返回误导性的参数文档，无运行时危害。
- **建议**: 回退逻辑改为优先 `arg.getAnnotation(Description.class)`，无则返回 null。
- **误报排除**: 已核对 `IFunctionArgument` 继承 `IAnnotatedElement`（具备 getAnnotation 能力）、对比同类 `getDescription(locale, fn)` 的函数级实现确认此处为复制粘贴残留；确认调用方 `toArgBean` 期望的是参数级描述。
> **处置（fix-ai-check 分支，2026-08-26）**: 已修复. 回退逻辑改为 `arg.getAnnotation(Description.class)`，参数自身无 @Description 时返回 null（不再错误显示函数整体描述）。新增 TestDevDocBizModel 2 用例（无参数注解返回 null/有参数注解返回参数描述）；stash 红=两用例在旧代码均返回"函数整体描述"，与报告"每个参数都显示函数的整体描述"形态一致。

### [P3] CrudBizModel.findRefEntity 在 objMeta 为 null 时 NPE（相邻代码使用 requireObjMeta）

- **文件**: `nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/CrudBizModel.java:1097-1098`
- **维度**: D1
- **证据**:
```java
protected IOrmEntity findRefEntity(IEntityModel entityModel, T entity, String refName, IServiceContext context) {
    IObjPropMeta propMeta = getThisObj().getObjMeta().getProp(refName);   // objMeta 为 null 时 NPE
    if (propMeta != null) {
    ...
        IEntityRelationModel relModel = entityModel.getRelation(refName, true);
        if (relModel == null) {
            propMeta = getThisObj().requireObjMeta().requireProp(refName);  // 同函数后文用了 requireObjMeta
```
- **现状**: 无 xmeta 的 biz 对象（本类多处判空：`getMaxPageSize`、`isAllowGetDeleted`、`getDefaultRefNamesToCheckExists` 均按 objMeta 可为 null 处理）在调用 `doDelete`/`doDeleteEntity` 且外部显式传入非空 `refNamesToCheck` 时，`checkEntityRefsNotExists -> findRefEntity` 第 1098 行直接 NPE。
- **风险**: 特定配置（无 xmeta + 自定义 refNamesToCheck）下删除操作以 NPE 而非语义化异常失败；常规路径（refNames 来自 objMeta 扩展属性）不触发。
- **建议**: 第 1098 行改用 `getThisObj().requireObjMeta().getProp(refName)`，与第 1121 行保持一致。
- **误报排除**: 已核对 `doDelete` 为 public @BizAction 可显式传 refNamesToCheck；核对 `getDefaultRefNamesToCheckExists` 在 objMeta==null 时返回 null 使默认路径不进入本函数（故仅显式传参触发，降为 P3）；核对本文件中 objMeta 判空的普遍惯例证明 null 是合法状态。
> **处置（fix-ai-check 分支，2026-08-26）**: 已修复. 第 1098 行改用 `getThisObj().requireObjMeta().getProp(refName)`（objMeta 为 null 时抛 ERR_BIZ_NO_OBJ_META 语义化异常），与本函数第 1121 行的 requireObjMeta 惯例保持一致。新增 TestCrudBizModelCrudFlow.testCheckEntityRefsNotExistsWithoutObjMetaThrowsBizError；stash 红=NPE "Cannot invoke IObjMeta.getProp(String) because the return value of IBizObject.getObjMeta() is null"。

### [P3] DefaultBizAuthChecker.checkAuth 在 objectDefinition 为 null（not-pub / 无字段 biz 对象）时 NPE

- **文件**: `nop-service-framework/nop-biz/src/main/java/io/nop/biz/impl/DefaultBizAuthChecker.java:49-56`
- **维度**: D1
- **证据**:
```java
bizObject.invoke(BizConstants.METHOD_GET, input, FieldSelectionBean.fromProp(OrmConstants.PROP_ID), ctx);
GraphQLObjectDefinition objDef = bizObject.getObjectDefinition();   // 可为 null

if (!StringHelper.isEmpty(fieldName)) {
    GraphQLFieldDefinition field = objDef.getField(fieldName);      // NPE
```
- **现状**: `BizObjectBuilder.buildBizObject`（181-182 行）在字段定义为空时执行 `bizObj.setObjectDefinition(null)`；not-pub 标记的 biz 对象字段被清空。此时 `checkAuth(bizObjName, objId, fieldName非空, context)` 在 `objDef.getField` 处 NPE。
- **风险**: 程序化字段级鉴权（IBizAuthChecker 是 nop-api-core 公开扩展点）对这类对象崩溃；触发面窄（fieldName 非空 + 无字段定义对象）。
- **建议**: `objDef == null` 时按无字段定义处理（直接返回 true 或抛语义化异常）。
- **误报排除**: 已核对 `BizObjectBuilder` 第 152-153、180-182 行确认 setObjectDefinition(null) 的真实路径；核对本方法前一步 `invoke(METHOD_GET)` 成功与 objDef 为 null 并不互斥（get 动作来自 bizModel 而非 objDef）。
> **处置（fix-ai-check 分支，2026-08-26）**: 已修复. objDef != null 才执行字段级鉴权查找；objDef == null（not-pub 等字段定义被清空的对象）按无字段级权限限制直接放行，与 GraphQLActionAuthChecker 对 auth==null 的放行语义一致（对象级 get 数据权限仍由前一步 invoke(METHOD_GET) 校验）。新增 TestDefaultBizAuthChecker 2 用例（objDef null + fieldName 非空放行/fieldName 空串与 null 原行为不变）；stash 红=NPE "Cannot invoke GraphQLObjectDefinition.getField(String) because objDef is null"。

### [P3] copyForNew 在未定义 copy-for-new selection 时仅清除序列主键，非序列主键实体会因主键冲突保存失败

- **文件**: `nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/CrudBizModel.java:1740-1747`
- **维度**: D1
- **证据**:
```java
} else {
    newEntity = (T) entity.cloneInstance();
    // 序列号主键被设置为空
    for (IColumnModel col : entity.orm_entityModel().getPkColumns()) {
        if (col.containsTag(OrmConstants.TAG_SEQ) || col.containsTag(OrmConstants.TAG_SEQ_DEFAULT))
            newEntity.orm_propValue(col.getPropId(), null);
    }
}
```
- **现状**: `objMeta.getFieldSelection(SELECTION_COPY_FOR_NEW)` 返回 null 时走整实体克隆分支，只对带 `seq`/`seq-default` 标签的主键清空。若实体主键不是序列生成（如业务AssignedId/UUID 由前台或 defaultExpr 之外机制提供），克隆体保留源主键，且随后 `entityData.getValidatedData()` 已 remove 掉 id（1730 行），`doSaveEntity -> dao.saveEntity` 将触发主键重复错误。
- **风险**: 非序列主键实体 + 未配置 copy-for-new selection 时复制新建必然失败；属于边界条件下的功能缺陷，主流 seq 主键场景不受影响。
- **建议**: 克隆分支对所有主键列统一清空（或对非 seq 主键显式报"copyForNew 需要提供新主键/配置 copySelection"）。
- **误报排除**: 已核对 `buildEntityDataForSave` 的 validated 数据流（id 被 `_validate` 保留后在 1730 行移除，确认无其他主键来源）；核对 `OrmConstants.TAG_SEQ/TAG_SEQ_DEFAULT` 判定逻辑为本分支唯一的主键处理；确认 `cloneInstance` 语义为含主键的深拷贝（同文件 1741 行注释）。
> **处置（fix-ai-check 分支，2026-08-26）**: 已修复. 克隆分支对所有主键列统一清空（去掉 seq/seq-default 标签限定）。seq 主键行为不变（清空后由 ORM 生成器重新生成）；非序列主键清空后若 ORM 无法生成，由 OrmEntityIdGenerator.generateId 抛既有语义化错误 ERR_ORM_ENTITY_ID_NOT_SET，替代原先保留源主键导致保存时必然触发的主键重复错误。新增 TestCrudBizModelCopyForNewPk.testCloneBranchClearsNonSeqPrimaryKey（非 seq 主键实体 + 无 copy-for-new selection 的克隆路径）；stash 红=克隆体保留源主键（expected null but was "src-1"）。

## 附注（核实后排除的候选）

- `checkUniqueForSave`/`checkUniqueForUpdate` 中 key prop 提交 null 值的假阳性疑点：经追踪 `OrmEntity.orm_propValueByName(name, null)` 会将该 prop 标记为 inited（OrmEntity.onInitProp），`GenSqlHelper.appendExampleFilter` 生成 `col = null` 条件（SQL 下永不匹配），findFirstByExample 返回 null，不会误报"记录已存在"，与多数数据库唯一索引对 NULL 的语义一致。非问题。
- `batchUpdate`/`doUpdateMulti` 循环内逐条 update 的 N+1 疑点：实体已通过 `tryBatchGetEntitiesByIds`/`batchGetEntitiesByIds` 预载入 ORM 会话，`requireEntity -> getEntityById` 命中会话缓存，不产生逐条 SQL。非问题。
- `CacheEvictActionDecorator` 在 action 失败时不淘汰：与"成功后淘汰"语义一致，属设计选择。
- D2（资源管理）维度：全模块仅 `DownloadHelper` 涉及流操作，成功/异常路径均正确关闭（异常路径同时关 zipOutput 与底层 os），未发现泄漏。D3 的 `SimpleDateFormat`/共享可变静态状态扫描无命中（`CrudToolProvider.newOrmEntityCopier` 每次新建实例，无共享可变态）。
