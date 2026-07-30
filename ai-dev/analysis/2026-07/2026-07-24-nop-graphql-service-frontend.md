# nop-entropy GraphQL 引擎、服务层与前后端一体化渲染

> Status: resolved
> Date: 2026-07-24
> Scope: `nop-service-framework`（nop-biz 服务层、nop-graphql-core/nop-graphql-orm 自动暴露）、`nop-frontend-support`（nop-web 页面生成、nop-ui 控件匹配）、`nop-kernel/nop-xdefs`（`graphql:*` xmeta 属性 schema）；端到端链路：数据模型 → BizModel/xbiz → GraphQL operation → 统一分发 → xmeta 字段可见性 → 两阶段渲染 → AMIS/Flux 框架 JSON + 5 方向联网对标（Spring for GraphQL / Hasura / Apollo Federation / BFF / 低代码前端）
> Conclusion: nop-entropy 的「服务层 + 前后端一体化」以 **xmeta 作为单一事实源**驱动一条端到端链路——同一个 `@BizModel` 方法（`@BizQuery`/`@BizMutation`）通过反射自动注册为 GraphQL operation（operationName `{bizObj}__{method}`），五个 HTTP 入口（`/graphql`、`/r/`、`/p/`、`/px/`、`/jsonrpc`）统一由 `IGraphQLEngine` 分发到同一方法；同一份 xmeta 同时约束 GraphQL 字段可见性（selection 只能命中 published props）并作为 codegen 输入生成 view 基线，运行时 `GenPage` 再消费 view + xmeta 渲染出 AMIS/Flux 框架 JSON。其差异化定位是：(1) **meta-first 自动暴露**——无需手写 GraphQL schema 文件（区别于 Spring for GraphQL 的 schema-first），也不依赖数据库 introspection（区别于 Hasura）；(2) **单引擎统一分发**——一个 BizModel 同时服务 GraphQL/REST/JSON-RPC，无需 BFF 聚合层，也非多服务 federation 组合；(3) **三层 Delta 渲染**——xmeta(源) → `_gen/_*.view.xml`(生成基线) → `*.view.xml`(手写定制) → `main.page.yaml`(入口) → 框架 JSON，且同一 view 模型可同时输出 AMIS 与 Flux 两套 JSON。所有事实性论断经 ≥21 个 source-anchors 锚点源码交叉核对（全部 PASS）。
> Mission: nop-deep-analysis（Work Item A4）
> Superseded By: （本分析为 A7 capstone 提供「服务层 + 前后端一体化」参照；若 A7 重新组织该章节，则被替代）

## Context

- **要回答的问题**：从 ORM 数据模型到 GraphQL API 再到前端渲染的端到端链路是如何串成一体化的？服务层约定（`CrudBizModel` CRUD 默认行为、`@BizQuery`/`@BizMutation`/`@BizAction` 可见性、`I*Biz` 动态代理、Processor 拆分）如何与 GraphQL 自动暴露对齐？`graphql:*` XMeta 属性如何驱动 connection/filter/orderBy/dict-label 与字段可见性？两阶段生成（codegen + 运行时 `GenPage`）与三层 Delta 架构如何工作？与 Spring for GraphQL、Hasura、Apollo Federation、BFF 模式、低代码前端（Formily/Lowcode Engine）相比有何取舍？
- **涉及模块/子系统**：`nop-biz`（服务层）、`nop-graphql-core`/`nop-graphql-orm`（自动暴露）、`nop-web`（页面生成）、`nop-ui`（控件匹配）、`nop-xdefs`（`graphql:*` schema）。
- **约束**：A2 已覆盖分发引擎内部机制（`GraphQLWebService`→`IGraphQLEngine`→BizModel 调用链 + operationName 分隔符 `__`）。本分析**引用** A2 的分发结论，聚焦 A2 未覆盖的 **CRUD 约定、`graphql:*` 属性、xmeta 字段可见性、前端两阶段渲染串联**，不重述引擎分发机制。A3 覆盖 codegen 生成管线与 Maven phase 绑定，本分析仅引用其结论。
- **来源基线**：`docs-for-ai/02-core-guides/`（service-layer / api-and-graphql / frontend-rendering-pipeline / amis-rendering / flux-rendering）、`docs-for-ai/04-reference/source-anchors.md`（BIZ/GQL/DOC/UI/EXT/TXN 系列）。本分析通过 3 个并行 explore 子 agent 对 **21 个 source-anchors 锚点**做源码交叉核对（全部 PASS，含 3 处命名性瑕疵已记入 Open Questions）建立。

## Analysis

### 1. 服务层约定：BizModel / CrudBizModel / I\*Biz / Processor

服务层是「一体化」链路的后端起点。当前仓库的默认服务层就是 BizModel 层，所有面向前端/RPC 的能力都从 `@BizModel` 暴露。

#### 1.1 CRUD 默认行为（BIZ-001 ~ BIZ-007）

标准实体服务的基类是 `CrudBizModel<T>`（BIZ-002, `nop-biz/.../crud/CrudBizModel.java`），它**直接 implements `ICrudBiz<T>`**（BIZ-001, `nop-orm/.../biz/ICrudBiz.java`），不是"extends 某个实现 ICrudBiz 的类"。`ICrudBiz` 是契约接口，所有方法末参为 `IServiceContext context`，方法注解按可见性三级划分：

| 注解 | 可见性 | ICrudBiz 中的方法 |
|------|--------|-------------------|
| `@BizQuery` | 前端 GraphQL 可调，只读 | `findCount`/`findPage`/`findFirst`/`findList`/`get`/`batchGet`/`asDict`/`findRoots`/`findTree*`/`deleted_*`/`recoverDeleted` |
| `@BizMutation` | 前端 GraphQL 可调，写操作 | `save`/`saveOrUpdate`/`copyForNew`/`update`/`batchUpdate`/`updateByQuery`/`delete`/`batchDelete`/`batchModify`/`deleteByQuery`/多对多关系维护 |
| `@BizAction` | **仅后端内部可调**，不暴露为 GraphQL operation | `requireEntity`/`deleteEntity`/`saveEntity`/`updateEntity`/`assignToEntity`/`buildEntityForSave`/`checkAllowAccess`/`fetchSelection` |

`CrudBizModel` 关键方法（锚点均经源码核对）：

- **BIZ-003 `requireEntity(id, action, context)`**（`CrudBizModel.java:888-891`）：获取实体的安全路径。委托 `getEntity(...)`（L894-916）：`dao().getEntityById` 加载，找不到抛 `UnknownEntityException`，随后强制 `checkDataAuth(action, entity, context)`（L913）+ `checkMetaFilter`（L914）。
- **BIZ-004 `doFindList` / `doFindPage`**（`:1542`/`:313`）：普通查询的安全 API，均为 `@BizAction`。它们都**原地修改传入的 `QueryBean`**（非防御性拷贝）——通过 `prepareFindPageQuery` 追加数据权限过滤、默认 filter/orderBy、limit 裁剪、主键排序补全。
- **BIZ-007 `prepareFindPageQuery`**（`:370-424`）+ `appendOrderByPk`（`:459-469`）：查询预处理管线，按序执行：(1) `checkAllowQuery`（校验 filter/orderBy/left-join）；(2) **数据权限** `AuthHelper.appendFilter(context.getDataAuthChecker(), query, authObjName, action, context)`（L381-382）；(3) 默认 filter `query.addFilter(objMeta.getFilter().cloneInstance())`（L393-395）；(4) 默认 orderBy（L397-399）；(5) limit 裁剪到 `[1, maxPageSize]`（L402-408）；(6) `appendOrderByPk`——当 orderBy 不含主键时补升序主键排序，保证分页确定性（L459-469）。

> **设计要点**：数据权限、默认过滤、主键确定性排序都内置在 `CrudBizModel` 的查询管线里，业务代码调 `doFindPage`/`doFindList` 即自动获得这些能力——绕过它们直调 `dao().findAllByQuery(query)` 会**丢失数据权限**，这是 `service-layer.md` 把直接 `dao()` 列为反模式的根因。

#### 1.2 跨 BizModel 协作：`I*Biz` 动态代理契约

`I*Biz`（如 `IOrderBiz`、`INopJobScheduleBiz`）是**服务实现层接口**，不是前端 API 接口。它由 BizModel 实现类 `implements`，同时由 `BizProxyFactoryBean` + `BizProxyInvocationHandler` 生成 JDK 动态代理供跨模块注入（验证锚点）：

- `BizProxyFactoryBean.build()`（`nop-biz/.../proxy/BizProxyFactoryBean.java:23-28`）：`bizObjectManager.getBizObject(bizObjName).asProxy()` 返回代理。
- `BizProxyInvocationHandler.buildActionMap`（`.../BizProxyInvocationHandler.java:76-100`）：对每个接口方法调 `ReflectionBizModelBuilder.INSTANCE.getServiceActionName(method)`（L84），**只有标注 `@BizQuery`/`@BizMutation`/`@BizSubscription`/`@BizAction` 的方法返回非 null**（`ReflectionBizModelBuilder.getServiceActionName`, `:299-314` 顺序扫描四种注解），被路由为 `IServiceAction`；未标注的方法回退为直接反射 bean 调用（L88-92）。

因此强约束：`I*Biz` 接口方法**必须**标注注解（否则代理无法路由，运行时抛 `unsupported-method`），且 BizModel 新增的每个 `public` 方法都必须同步到 `I*Biz`（代理只识别接口上的方法）。

**真实案例**（BIZ-005）：`INopJobScheduleBiz extends ICrudBiz<NopJobSchedule>`（`nop-job-dao/.../biz/INopJobScheduleBiz.java:13`）声明 6 个额外 `@BizMutation`（`enableSchedule`/`disableSchedule`/`pauseSchedule`/`resumeSchedule`/`triggerNow`/`archiveSchedule`）；`NopJobScheduleBizModel`（`nop-job-service/.../entity/NopJobScheduleBizModel.java:41`）`@BizModel("NopJobSchedule") extends CrudBizModel<NopJobSchedule> implements INopJobScheduleBiz`，内部用继承的 `requireEntity(id, "...", context)` 拉实体再 mutate/persist——演示了 BizModel 调用自身 ICrudBiz action surface 的跨 action 协作。

#### 1.3 扩展返回字段：`@BizLoader`（BIZ-006）

扩展 GraphQL 返回字段优先用 `@BizLoader`（`nop-api-core/.../annotations/biz/BizLoader.java`）。注解属性：`value()`（字段名）、**`autoCreateField()` 默认 false**、`forType()`/`forTypeName()`（按类型挂载到非自身 BizModel）。新增字段可配合 `@LazyLoad`（昂贵字段默认不计算）。

- 接线：`ReflectionBizModelBuilder.build`（`:170-198`）读 `@BizLoader`，`field.setAutoCreate(bizLoader.autoCreateField())`（L185）；`buildFetcherField` 检测 `@LazyLoad` 设 `field.setLazy(true)`（L421-422）。
- Delta 案例（BIZ-006）：`LoginApiBizModelDelta`（`nop-delta-demo/.../biz/LoginApiBizModelDelta.java:35-39`）用 `@BizLoader(autoCreateField = true, forType = LoginResult.class) @LazyLoad` 给 `LoginResult` 增补 `location` 字段，无需改原 BizModel。

#### 1.4 xbiz `<source>`：用 XPL 实现 action（不写 Java）

一个 BizModel action 可用 xbiz `<source>` XPL 脚本实现而非 Java 方法。**注意**：xbiz 加载与 `ReflectionBizModelBuilder`（Java 反射路径）是**两条独立路径**：

- Java 反射路径：`ReflectionBizModelBuilder.build`（`:110-199`）扫 `@BizModel` 类的 `@BizQuery`/`@BizMutation`/`@BizAction`/`@BizLoader` 方法。
- xbiz 路径：`GraphQLBizModels`（`:65,86-101`）扫 `_vfs/.../model/*.xbiz` → `BizObjectBuilder.loadBizModel`（`BizObjectBuilder.java:270-273`，用 `DslModelParser(XDEF_BIZ)`）→ `BizModelToGraphQLDefinition.buildAction`（`:126-139`）读 `actionModel.getSource()` 包成 `EvalServiceAction`（`EvalServiceAction.java:29-37`）。

xbiz `<source>` 内**内置变量 `svcCtx`**（`IServiceContext`，`CoreConstants.VAR_SVC_CTX = "svcCtx"`）：通过 `<arg name="svcCtx" kind="ServiceContext"/>` 绑定（`BizModelToGraphQLDefinition.getArgBuilder:187-188` → `ArgBuilders.getContext()`），codegen（`BizActionGenHelper.java:87-88,105-111`）在缺少时自动注入。真实案例 `nop-wf-core/.../approval-support.xbiz` 的 `submitForApproval` action 用 `<source><c:script>` 调 `thisObj.invoke("requireEntity", ...)` 与 `ApprovalFlowHelper.start(wf, args, svcCtx)`。

#### 1.5 Processor 拆分时机与事务边界

- **Processor 拆分**：当方法已是多步骤编排、逻辑需被多 BizModel 复用、需拆开外部系统交互与业务编排、或单方法难以阅读测试时，拆 Processor 优于继续堆 BizModel（`service-layer.md` §何时拆 Processor）。
- **事务边界（TXN-001）**：`BizActionInvoker.invokeActionSync`（`nop-biz/.../service/BizActionInvoker.java:42-53`）：`opType == query` 时**跳过事务**直接 `bizObject.invoke`（L43-44）；其余 opType（`@BizMutation`/`@BizAction`/`@BizSubscription`）进 `txnTemplate.runInTransaction(null, TransactionPropagation.REQUIRED, ...)`（L48）。两条路径都强制 `ormTemplate.runInSession(...)`（L42）。这就是 `service-layer.md` 禁止 `@BizMutation @Transactional` 的原因——管道已自动包事务，叠 `@Transactional` 是重复包裹。

### 2. GraphQL 自动暴露与统一分发（引用 A2，补前端侧约定）

A2 已详述分发引擎内部机制（`GraphQLWebService:229`→`IGraphQLEngine`→`ReflectionBizModelBuilder`→`BizObjectManager`→`BizActionInvoker`→`IOrmTemplate`），以及五个 HTTP 入口（`/graphql`、`/r/{opName}`、`/p/{opName}`、`/px/{svc}/{method}`、`/jsonrpc`）统一由 `GraphQLWebService` 适配到同一 `IGraphQLEngine`，按 operationName `{bizObj}__{method}`（分隔符 `GraphQLConstants.OBJ_ACTION_SEPARATOR = "__"`，`GraphQLConstants.java:96`）路由到同一 BizModel 方法。**本节不重述，仅补充 A2 未覆盖的前端侧 operationName 消费约定。**

#### 2.1 前端侧 `@query:` URL 机制（前后端共用同一 operationName）

view.xml 中 `<api url="@query:BizObjName__actionName?param=$param"/>` 的处理分两端：

- **后端**（`WebPageHelper.fixPage`, `nop-web/.../page/WebPageHelper.java:100-139`）：对 `@query:`/`@mutation:`/`@subscription:` 前缀的字符串仅调 `escapeGraphQL`（`:141-161`），**只转义空白**（space/tab/`\r`→`%20`，`\n`→`%0A`），`@query:` 与 operationName 原样输出到框架 JSON。后端不做任何 operationName 解析。
- **前端**（`nop-chaos` 项目的 `graphql.ts`）：解析 URL 取最后一个 `_` 后的方法名后缀作为 `stdAction`；`stdAction` 命中 `operationRegistry` 标准动作（`get`/`findPage`/`findList`/`findFirst`/`save`/`update`/`saveOrUpdate`/`upsert`/`copyForNew`/`delete`/`batchGet`/`batchDelete`/`batchModify`）则用预定义参数签名（忽略 URL 自定义参数），否则 `guessDefinition(data)` 逐字段推断参数类型，组装成 GraphQL query POST 到 `/graphql`。

> **核对说明**：`nop-chaos` 前端仓库**不在本 Java 仓库内**（仓库根目录无 `nop-chaos/`，无 `graphql.ts`，无 `operationRegistry` 引用）。本仓库的职责止于"原样 emit `@query:` URL"，前端解析逻辑属于独立前端项目。源码核对确认所有 codegen 模板（如 `_gen/...view.xml.xgen` 多处）emit 的就是 `@query:${objName}__findPage` 这类原样字符串。

#### 2.2 标准动作命名约束

前端 `operationRegistry` 注册了标准 CRUD 动作名。**自定义 BizModel 方法名不得与标准动作重名**（如 `get`/`save`/`update`/`delete`/`findPage`）——`GraphQLBizModel` 注册时按方法名路由（不支持重载），同名会抛 `ERR_GRAPHQL_DUPLICATE_ACTION`。自定义方法用不同名字（`getById`、`saveOrder`）。不同优先级时高优先级覆盖低优先级（delta 机制依赖此行为）。

### 3. xmeta 字段可见性与 `graphql:*` 属性

xmeta 是「一体化」的枢纽：它既是 GraphQL object definition 的来源（约束字段可见性），又是 codegen 生成 view 基线的输入，还承载 `graphql:*` 扩展属性驱动 connection/filter/orderBy/dict-label。

#### 3.1 `graphql:*` 属性全集（GQL-001 ~ GQL-008）

所有 `graphql:*` 属性在 `obj-schema.xdef` 定义（GQL-002, `nop-xdefs/.../schema/schema/obj-schema.xdef`），常量名集中在 `GraphQLConstants.java`（GQL-008, `ATTR_GRAPHQL_*` + `TAG_GRAPHQL_*`）：

**XML 属性**（在 `<prop>` 上）：`graphql:queryMethod`/`graphql:connectionProp`/`graphql:authObjName`/`graphql:maxFetchSize`/`graphql:type`/`graphql:inputType`/`graphql:dictName`/`graphql:dictValueProp`/`graphql:datePattern`/`graphql:labelProp`/`graphql:jsonComponentProp`/`graphql:mapper`/`graphql:joinLeftProps`/`graphql:joinRightProps`/`graphql:disableLogicalDelete`/`graphql:selection`/`graphql:prop`。

**XML 子元素**（在 `<prop>` 内）：`<graphql:filter>`（filter-bean）、`<graphql:orderBy>`（order-by.xdef）、`<graphql:selection>`、`<graphql:inputType>`、`<graphql:transFilter>`（`xpl-fn:(filter,query,forEntity)=>any`）。

各消费方（验证锚点）：

| 消费方 | 读取的 `graphql:*` | 锚点（file:line） |
|--------|--------------------|-------------------|
| `GraphQLObjMetaHelper` | `queryMethod`/`type`/`authObjName` | GQL-003 `GraphQLObjMetaHelper.java:19,45,67` |
| `ObjMetaToGraphQLDefinition` | `inputType`（自定义输入类型） | GQL-004 `:99-102,123-129` |
| `OrmFetcherBuilder` → `OrmEntityPropConnectionFetcher` | `queryMethod`/`connectionProp`/`maxFetchSize`/`disableLogicalDelete`/`authObjName`/`filter`/`orderBy` | GQL-001 `OrmFetcherBuilder.java:136,137,158,160,161,165,169,177` |
| `DictLabelFetcherProvider` | `dictName`+`dictValueProp` → dict code→label | GQL-005 `DictLabelFetcherProvider.java:40,42,63,70-78` |
| `XuiViewAnalyzer` | `labelProp`/`jsonComponentProp`/`selection` → 补 selection | GQL-006 `XuiViewAnalyzer.java:397-409,267-277,248-265` |
| `ExtPropsGetter` | 通用 `getTreeBean`/`getOrderBy` 读 `filter`/`orderBy` | GQL-007 `ExtPropsGetter.java:25-33,35-51` |

`graphql:queryMethod` 是关联子表查询的核心——配置后该字段在 GraphQL 中变为带分页/排序/条件的可查询关联（返回类型 `findCount`→Long、`findFirst`→单对象、`findList`→`[对象]`、`findPage`→`PageBean`、`findConnection`→`Connection`）。

#### 3.2 字段可见性：selection 由 xmeta props 约束

GraphQL selection 并非自由，而是受 xmeta 定义的 published props 约束：

- `ObjMetaToGraphQLDefinition.toObjectDefinition`（`:61-63`）**只发射 published props**，跳过 composite `a.b` 名字（`:69-70`），生成的 `GraphQLObjectDefinition` 定义了合法 selection 宇宙。
- `GraphQLExecutor._fetchSelections`（`GraphQLExecutor.java:309-359`）：遍历已解析 selection，**对每个字段若不在客户端 `sourceSelection` 则 `continue` 跳过**（L325-328），否则调该字段 fetcher 的 `get(env)`（L332→:374）。即**字段只有在被 selection 请求时才计算**。
- `@LazyLoad`/`lazy=true`：`RpcSelectionSetBuilder.addDefaultFieldsForObjType`（`:145-164`）在构建默认/auto selection 时**显式跳过 lazy 字段**（L150-152 `if (isLazy(fieldDef)) continue`）。即 lazy 字段只在客户端显式请求时才取数。
- `autoCreateField`：`@BizLoader(autoCreateField=true)` 时即使 bean 无此属性也创建 GraphQL 字段（`ReflectionBizModelBuilder.java:185`），其 loader 仍只在 selection 命中时执行（同 `_fetchSelections` 路径）。

> **设计要点**：字段可见性由 xmeta 控制，不需要改返回类型来隐藏字段。直接返回实体即可——客户端只能看到 xmeta 中定义且在 selection 中请求的字段。这是 `service-layer.md`「实体能表达的优先用实体」的底层支撑。

#### 3.3 dict 字段自动 `_label`（GQL-005/GQL-006 扩展）

当字段配置了 dict，编译期会自动生成配套的 `_label` 显示字段（机制实为 xlib 标签，非 Java 类——见 Open Questions §1）：

- **`<GenDictLabelFields>` 标签**（`nop-xlang/.../core/xlib/meta-gen.xlib:32-61`，由 `<DefaultMetaPostExtends>`（`:22`）调用）：(a) 在原字段标记 `graphql:labelProp="{name}_label"`（`:49-51`）；(b) 生成 `{name}_label` 字段，设 `graphql:dictName="{dict}"` + `graphql:dictValueProp="{name}"`（`:53-56`）。
- **运行时解析**：`DictLabelFetcherProvider.provideFetcher`（`:40,42,63`）读 `dictValueProp`/`dictName`，包成 `TransformFetcher`，其 transform 调 `DictProvider.instance().getDict(...)` 后 `dict.getLabelByValue(value)`（`DictLabelFetcherProvider.java:76` → `DictBean.getLabelByValue`, `DictBean.java:195-198`）实时把 code 转成 label。
- **label 格式**：`nop.core.dict.return-normalized-label`（默认 true，`CoreConfigs.java:229-230`）控制——true 时 `DictProvider:115` 调 `dict.normalize()`，`DictBean.normalize()`（`:250-264`）把每个 option label 重写为 `"{value}-{label}"`（如 `"APPROVED-已审核"`）；false 则只返回显示文本。

> selection 写法：dict 字段是**扁平标量**，用 `@selection=status,status_label`，**不是** `{value,label}` 子对象语法。

### 4. 前后端一体化渲染

渲染管线是「一体化」的前端出口。同一份 xmeta + view.xml 模型，经两阶段生成、三层 Delta 架构、控件匹配链，产出 AMIS 或 Flux 框架 JSON。

#### 4.1 两阶段生成

| 阶段 | 时机 | 输入 | 输出 | 触发点 |
|------|------|------|------|--------|
| 构建时 codegen | `mvn install` 的 `precompile` | `*.xmeta` | `_gen/_Xxx.view.xml`、`Xxx.view.xml`、`main.page.yaml` | `*-web/precompile/gen-page.xgen`（GEN-005） |
| 运行时渲染 | 前端请求 `PageProvider__getPage` | `view.xml` + `xmeta` | 框架 JSON（AMIS 或 Flux） | `main.page.yaml` 的 `x:gen-extends` → `GenPage` |

**构建时**（GEN-005）：`gen-page.xgen`（如 `nop-auth-web/precompile/gen-page.xgen:4`）调 `codeGenerator.withTplDir('/nop/templates/orm-web').execute("/",{ moduleId: "nop/auth" },$scope)`。模板在 `nop-codegen/.../_vfs/nop/templates/orm-web/`，关键产物：

- `_gen/_{objName}.view.xml.xgen` → `_gen/_Xxx.view.xml`（view 基线，含 grid/form/crud/picker/simple 全套，下次 install 被覆盖）
- `{objName}.view.xml.xgen` → `Xxx.view.xml`（保留层，`x:extends` 继承 _gen，留给手写定制）
- `main.page.yaml.xgen` → `main.page.yaml`（入口 wrapper，核心就 1 行）

生成的 `main.page.yaml` 实证（`nop-auth-web/.../NopAuthResource/main.page.yaml:1-2`）：
```yaml
x:gen-extends: |
  <web:GenPage view="NopAuthResource.view.xml" page="main" xpl:lib="/nop/web/xlib/web.xlib" />
```
（模板 `main.page.yaml.xgen:7-10` 还有 flux 分支：`web:renderer=='flux'` 时 emit `flux-web:GenPage`。）

**运行时**：前端请求 `GET /p/PageProvider__getPage?path=.../main.page.yaml`。调用链（验证锚点）：

1. `PageProviderBizModel.getPage`（`nop-web/.../biz/PageProviderBizModel.java:37-44`，`@BizModel("PageProvider")`）→ `pageProvider.getPage(path, locale)`（`:42`）。
2. `PageProvider.getPage`（`.../page/PageProvider.java:175-192`）→ `ResourceComponentManager.instance().loadComponentModel(...)`（`:180`）。
3. `PageModelLoaderFactory.PageModelLoader.loadObjectFromPath`（`:23-36`）桥接到 `PageProvider.loadPage`（`PageProvider.java:223-246`），后者 `JsonTool.loadDeltaBean(resource, JObject.class, options)`（`:234`）——正是 Delta 加载器处理 `x:gen-extends` 指令，触发 `web:GenPage`。
4. `web.xlib:GenPage`（`web.xlib:11-20`）include `web/impl_GenPage.xpl`，后者加载 view + xmeta + controlLib，按 `pageModel.type` 分发（`impl_GenPage.xpl:18-34`）：`crud`→`page_crud.xpl`、`picker`→`page_picker.xpl`、`simple`→`page_simple.xpl`、`tabs`→`page_tabs.xpl`，未知类型抛 `nop.err.web.unknown-page-type`。
5. 子模板读 view 的 grid/form/page 配置 + objMeta 字段元数据组装 JSON；`PageProvider` 再做 i18n 解析、`xui:permissions`→`xui:roles` 权限转换、清理空值。

#### 4.2 三层 Delta 架构

```
xmeta (实体元数据,源)
  ↓ [构建时 codegen]
_gen/_Xxx.view.xml  (自动生成的 view 基线,会被覆盖)
  ↓ x:extends
Xxx.view.xml        (保留层,手写定制)
  ↓ 运行时被 GenPage 读取
main.page.yaml      (入口 wrapper)
  ↓ x:gen-extends 触发
框架 JSON           (运行时输出,可缓存,AMIS 或 Flux)
```

**手写约束**：优先改非下划线 `Xxx.view.xml`，**绝不**手改 `_gen/_*`（下次 `mvn install` 被覆盖）；`page.yaml` 通常不动（除非需 page 级 `x:gen-extends`、自定义 title/body 包装、`fixedProps` 子表关联）。

真实 view.xml 案例（UI-001 ~ UI-004，均经源码核对存在）：

- **UI-001** `NopAuthResource.view.xml`（186 行）：树形 CRUD，`<selection>children @TreeChildren(max:5)</selection>`，`<crud name="main">` + `<rowActions>`。
- **UI-002** `NopJobSchedule.view.xml`：`<crud name="main" x:inherit="true">` + 大量自定义 row actions（runtime-summary/view-fires/trigger-now/enable/disable/pause/resume/archive）。
- **UI-003** `NopRuleNode.view.xml`：rule node CRUD，`<pages><crud name="main"/>`。
- **UI-004** `nop-wf-web/.../designer/designer.page.yaml`（122 行）：工作流设计器页面，`x:gen-extends` 调 `dingflow-gen:GenFlowEditorPage`，body 是 `type: nop-flow-editor` 组件，定义 nodeMetas/edgeMetas——`x:gen-extends` 与大块手写 schema 混合的典型。

#### 4.3 控件匹配链

`XuiHelper.getControlTag`（`nop-ui/.../utils/XuiHelper.java:56-113` 公开入口，`:148-181` 内部 `_getControlTag`）按优先级匹配控件标签，从控件库（`control.xlib` / `flux-control.xlib`）查 `{mode}-{type}`（`tryGetControl`, `:183-188`）：

```
control → domain → stdDomain → [relKind] → stdDataType
```

即优先匹配显式 `control`，其次 `domain`（含 length-suffixed 如 `json-4k` 的 baseDomain 回退，`:158-167`），再 `stdDomain`，最后 `stdDataType`。**核对发现**：实际代码在 `stdDomain` 与 `stdDataType` 之间插入了一层 `relKind`（relation kind: to-one/to-many，`:173`），这是文档未列出的额外匹配层（见 Open Questions §3）。常见映射：`string`→`input-text`、`int/long`→`input-text`+`isInt`、`double/decimal`→`input-number`、`enum`→`select`。

#### 4.4 AMIS vs Flux 双渲染管线

同一 view.xml 模型可输出两套框架 JSON，靠切换 xlib 实现：

| 维度 | AMIS（`web.xlib` + `control.xlib`） | Flux（`flux-web.xlib` + `flux-control.xlib`） |
|------|--------------------------------------|-----------------------------------------------|
| 控件映射库 | `control.xlib`（75 标签，EXT-007） | `flux-control.xlib`（**75 标签**，EXT-008 核对一致） |
| 页面生成库 | `web.xlib`（37 标签） | `flux-web.xlib`（**37 标签**，EXT-009 核对一致） |
| 切换方式 | 默认 | page.yaml 改 `xpl:lib`；或 ORM `<entity ext:web-renderer="flux">` |
| URL 机制 | `@query:`/`@mutation:` 前缀 | 同（后端 emit 相同，前端运行时各自处理） |
| 动作系统 | `actionType` 扁平（`drawer`/`dialog`/`ajax`） | `onClick` ActionSchema DAG |
| 条件属性 | `visibleOn`/`disabledOn`/`staticOn` | `visible`/`disabled`（无 `staticOn`） |

**Flux NormalizeAction 的 onClick 优先规则**（`flux-web.xlib:711-794` 核对）：`action.onClick != null`（`:727-730`）→ 直接透传 Flux 原生 ActionSchema；否则从 `api`/`actionType`/`dialog`/`drawer` 自动转换（`:732+`，转成 `type` 字段的 `api`/`dialog`/`drawer`/`reload`/`close`/`toast`/`link`/`url`/`copy`），有 `confirmText` 套 `{type:'confirm',when,then}`（`:786-789`）。**Flux 不自动传递表单数据到 API**，需显式 `includeScope:"*"` 或 `data:{...}`（与 AMIS `api.withFormData` 隐式携带不同）。

### 5. 联网对标与差异定位

#### 5.1 Spring for GraphQL（schema-first 注解控制器）

- **该方案做什么**：Spring 官方的 GraphQL 集成。**schema-first**——需先手写 GraphQL schema 文件（`.graphqls`），再用 `@Controller` + `@QueryMapping`/`@MutationMapping`/`@SchemaMapping` 把 Java 方法绑定为 schema 中某字段的 `DataFetcher`。字段名默认取方法名，类型名默认取源对象类名。支持 `@Argument`/`@Arguments`/`@BatchMapping`（N+1 批量加载）、`@ProjectedPayload`、Bean Validation、`@GraphQlExceptionHandler`。来源：[Spring for GraphQL — Annotated Controllers](https://docs.spring.io/spring-graphql/reference/controllers.html)（访问 2026-07-24）。
- **nop 做什么**：**meta-first 自动暴露**——BizModel 的 `@BizQuery`/`@BizMutation` 方法经反射自动注册为 GraphQL operation，operationName `{bizObj}__{method}`，**无需手写 schema 文件**；GraphQL object definition 从 xmeta 自动派生（published props 即字段宇宙）。
- **差异点**：Spring 要求 schema 与 controller **双向对齐**（schema 定义字段，controller 实现 DataFetcher，二者必须一致），是"声明 + 实现"两份事实；nop 以 xmeta 为**单一事实源**，schema 与 service 同源派生。Spring 的 `@BatchMapping` 对应 nop 的 `graphql:queryMethod`+`connectionProp`（声明式关联查询，无需手写 DataFetcher）。

#### 5.2 Hasura（database-introspection 自动生成）

- **该方案做什么**：Hasura GraphQL Engine introspect 数据库，**基于 PostgreSQL 表/视图/SQL 函数自动生成 GraphQL schema 和 resolver**，无需写 schema 或代码。支持 tracking 表、权限规则、relationship、remote schema 拼接。来源：[Hasura — GraphQL Schema Overview](https://hasura.io/docs/2.0/schema/overview/)、[Hasura — PostgreSQL](https://hasura.io/graphql/database/postgresql)（访问 2026-07-24）。
- **nop 做什么**：**model-first 自动暴露**——从 ORM 模型（`*.orm.xml`）派生 xmeta，再从 xmeta 派生 GraphQL，关系/字段/权限由模型声明驱动，不依赖运行时 DB introspection。
- **差异点**：Hasura 是"DB 已存在 → 反向生成 API"（database-first，schema 跟随 DB 变）；nop 是"模型先行 → 正向生成 schema + DDL + 代码"（model-first，DB 跟随模型变）。Hasura 强绑 PostgreSQL（其他 DB 支持有限），nop 通过 Dialect 抽象多 DB。Hasura 的权限是 query-time row-level filter，nop 的数据权限在 `prepareFindPageQuery` 阶段 appendFilter（同样 query-time，但集成在 service 层管线）。

#### 5.3 Apollo Federation / Supergraph（多服务 schema 组合）

- **该方案做什么**：多个 subgraph 各自发布 schema，由 Apollo registry **compose** 成单一 supergraph schema，router 按跨 subgraph 的实体引用（`@key`/`@external`/`resolveReference`）在运行时跨服务解析字段。适合微服务团队各自独立演进。来源：[Apollo — Introduction to Federation](https://www.apollographql.com/docs/federation/v1)、[Apollo — Schema Composition](https://www.apollographql.com/docs/graphos/schema-design/federated-schemas/composition)（访问 2026-07-24）。
- **nop 做什么**：**单引擎统一分发**——一个 `IGraphQLEngine` 进程内路由所有 BizModel operation，跨模块协作走进程内 `I*Biz` 动态代理（同 JVM）或 `/px/` 分布式代理（跨 JVM）。分布式场景由 `RpcServiceOnGraphQL`（RPC-008）把 BizModel 包装为 `IRpcService`，不经 federation compose。
- **差异点**：Federation 是"多服务 → 合一 schema"的去中心化组合（适合组织规模大的微服务）；nop 是"单引擎 → 统一分发"的中心化模型（适合单体或模块化单体）。Federation 的复杂度在 compose 校验与跨服务实体解析；nop 的复杂度预算放在加载期 Delta 合并与反射注册。分布式扩展时 nop 用类型化 RPC（`*.api.xml` codegen 透明 HTTP 代理）而非 federation。

#### 5.4 BFF / Backend for Frontend（前端专用聚合层）

- **该方案做什么**：为**每种前端**（web/mobile/TV）建一个专用后端服务，聚合多个下游微服务 API、做数据裁剪/格式转换，避免前端直连多个服务。由 Sam Newman / Phil Calçado 提出。来源：[Sam Newman — Backends For Frontends](https://samnewman.io/patterns/architectural/bff/)、[Azure — Backends for Frontends Pattern](https://learn.microsoft.com/en-us/azure/architecture/patterns/backends-for-frontends)（访问 2026-07-24）。
- **nop 做什么**：**同一 BizModel 同时服务多入口**——`@BizQuery`/`@BizMutation` 方法经同一 operationName 同时服务 `/graphql`（AMIS/Flux 前端）、`/r/`（REST）、`/p/`（内容感知）、`/jsonrpc`、`/px/`（RPC）。前端字段裁剪由 GraphQL selection + xmeta 字段可见性完成，无需为每种前端写独立聚合服务。
- **差异点**：BFF 是"每前端一个后端"（解决多前端异构需求 + 下游聚合）；nop 是"一个后端多入口"（字段可见性由 selection 动态裁剪，无需多份后端代码）。BFF 的价值在异构前端定制与下游聚合；nop 的 `@BizLoader` + xmeta 已提供字段级按需裁剪，聚合需求由跨 BizModel 协作（`I*Biz`）在进程内解决。当真的需要 per-frontend 定制时，nop 用 Delta（`*.view.xml` / Delta BizModel）在同一模型上叠加差异，而非起独立服务。

#### 5.5 低代码前端：Formily / Lowcode Engine（协议驱动页面搭建）

- **该方案做什么**：
  - **Formily**（阿里）：深度集成 **JSON Schema 协议**的表单引擎，schema 驱动表单渲染，支持 reactive 联动，跨端。聚焦"表单"这一场景。来源：[alibaba/formily](https://github.com/alibaba/formily)（访问 2026-07-24）。
  - **Lowcode Engine**（阿里）：企业级低代码搭建引擎，实现"搭建协议规范"+"物料协议规范"，四大模块：入料（物料接入）、编排（可视化拖拽）、渲染、出码（schema→源码）。聚焦"整页可视化搭建"。来源：[alibaba/lowcode-engine](https://github.com/alibaba/lowcode-engine)（访问 2026-07-24）。
- **nop 做什么**：xmeta 驱动 view 生成 + `GenPage` 渲染框架 JSON——同样是"schema/协议驱动"，但 schema 源是**后端实体元数据**（xmeta），而非前端可视化拖拽产物；渲染目标可选 AMIS 或 Flux；codegen 已在构建期从 xmeta 生成 view 基线，运行时再叠加 Delta。
- **差异点**：Formily/Lowcode Engine 的 schema 是**前端产物**（人工搭建或可视化生成），渲染单一前端框架；nop 的 xmeta 是**后端模型派生产物**（与 ORM 模型同源），一次模型驱动 schema + service + 双前端渲染。Formily 重表单、Lowcode Engine 重搭建可视化；nop 重"模型驱动 + 自动生成 + Delta 定制"，无可视化拖拽设计器（设计器是 `nop-wf` 等业务模块自带，非平台级低代码搭建能力）。三者都属"协议/模型驱动渲染"谱系，差异在 schema 来源（前端搭建 vs 后端模型）与可定制层级（nop 的三层 Delta 提供生成后覆盖能力）。

#### 5.6 差异定位小结

nop 在前后端一体化上的核心差异化：**(1) meta-first 自动暴露**——xmeta 单一事实源同时驱动 GraphQL schema、字段可见性、view 基线、控件选择；**(2) 单引擎统一分发**——一个 BizModel 服务五个 HTTP 入口，无需 BFF 聚合层、非多服务 federation；**(3) 三层 Delta 渲染**——生成物可被手写层 Delta 覆盖，且同一 view 可输出 AMIS/Flux 双 JSON；**(4) 字段可见性内建**——selection 只能命中 xmeta published props，dict 字段编译期自动产 `_label`。代价是：强绑 Java/JVM 生态（Hasura 跨语言更中性）、无平台级可视化搭建设计器（Lowcode Engine 强项）、分布式扩展用 RPC 而非 federation（组织规模极大时 federation 更解耦）。

## Conclusion

- **结论**：nop-entropy 的服务层与前后端一体化以 xmeta 为单一事实源，把「BizModel 自动暴露为 GraphQL operation → 单引擎统一分发 → xmeta 约束字段可见性 → 两阶段生成 view → `GenPage` 渲染 AMIS/Flux JSON」串成一条端到端可追溯链路。经 21 个 source-anchors 锚点源码交叉核对（全部 PASS），核心机制在代码中成立，非纯文档空谈：
  - BizModel 确实自动成为 GraphQL operation（`ReflectionBizModelBuilder` 扫注解注册，operationName `{bizObj}__{method}`，分隔符 `__` 在 `GraphQLConstants.java:96`）。
  - `GenPage` 确实从 view.xml + xmeta 生成框架 JSON（`impl_GenPage.xpl:8-34` 加载二者并按 pageModel.type 分发）。
  - dict 字段确实编译期自动产 `_label`（`meta-gen.xlib:32-61` 的 `<GenDictLabelFields>` 标签）。
- **被否决/不采纳的方案**：本分析为研究型产出，不涉及方案采纳。记录的差异定位供 A7 capstone 综合评估。
- **后续工作**：A4 已完成，为 A7（综合评估与演进建议）提供「服务层 + 前后端一体化」层面参照。是否将本分析迁移到 `docs-for-ai/` 由 A7 决策。

## Open Questions

- [ ] **文档命名瑕疵（行为正确，类名/载体描述有误）**：`docs-for-ai/02-core-guides/api-and-graphql.md` 与 source-anchors GQL-005 描述的 `DictLabelFetcher` 实际**不存在该 Java 类**——运行时 fetcher 是 `TransformFetcher` 包 `DictLabelFetcherProvider.loadLabel`（`DictLabelFetcherProvider.java:63,70-78`）；`GenDictLabelFields` 在源码中是 **xlib 标签**（`meta-gen.xlib:32-61`）而非 Java 类。行为与文档一致，仅命名载体描述需修正。属独立文档维护任务，不在本 plan 修复。
- [ ] **`graphql:labelProp` 未在 `obj-schema.xdef` 声明**：`XuiViewAnalyzer`（`XuiConstants.GRAPHQL_LABEL_PROP`）与 `meta-gen.xlib` 都读写 `graphql:labelProp`，但 `obj-schema.xdef` 只声明了 `ui:labelProp`（obj-schema.xdef:90），`graphql:labelProp` 是 `xdef:check-ns` 宽松命名空间下的隐式属性。建议确认这是有意（编译期由 meta-gen 生成）还是 schema 遗漏。
- [ ] **控件匹配链 `relKind` 层未在文档记录**：`XuiHelper.getControlTag`（`XuiHelper.java:148-181`）实际在 `stdDomain` 与 `stdDataType` 之间插入 `relKind`（to-one/to-many）匹配层，而 `frontend-rendering-pipeline.md` 的优先级链 `control → domain → stdDomain → stdDataType` 未列出该层。建议文档补全。
- [ ] **`ICrudBiz.recoverDeleted` 注解不一致**：接口声明 `@BizQuery`（`ICrudBiz.java:158`），实现 `CrudBizModel#recoverDeleted` 为 `@BizMutation`（`CrudBizModel.java:1403`）。属良性注解不一致（不影响契约），建议对齐。
- [ ] **`nop-chaos` 前端仓库不在本 Java 仓库**：`@query:` 的前端解析逻辑（`graphql.ts`、`operationRegistry`）属于独立前端项目，本仓库仅原样 emit。若需完整核对前端动作签名表，需引入 `nop-chaos` 仓库（超出本分析范围）。

## References

### 平台内部（file:line 锚点）

- 服务层：`nop-persistence/nop-orm/src/main/java/io/nop/orm/biz/ICrudBiz.java`（BIZ-001）、`nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/CrudBizModel.java`（BIZ-002/003/004/007）、`nop-service-framework/nop-biz/src/main/java/io/nop/biz/proxy/BizProxyInvocationHandler.java`、`nop-service-framework/nop-biz/src/main/java/io/nop/biz/service/BizActionInvoker.java:42-53`（TXN-001）、`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/annotations/biz/BizLoader.java`（BIZ-006）、`nop-demo/nop-delta-demo/src/main/java/io/nop/demo/biz/LoginApiBizModelDelta.java`（BIZ-006）
- xbiz：`nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/.../BizObjectBuilder.java:270-273`、`.../BizModelToGraphQLDefinition.java:126-139,187-188`、`.../EvalServiceAction.java:29-37`、`nop-wf/nop-wf-core/src/main/resources/_vfs/.../approval-support.xbiz`
- GraphQL 自动暴露：`nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/GraphQLConstants.java:23-45,96`（GQL-008）、`.../utils/GraphQLObjMetaHelper.java:19,45,67`（GQL-003）、`.../schema/meta/ObjMetaToGraphQLDefinition.java:61-63,69-70,92,99-102`（GQL-004）、`.../engine/GraphQLExecutor.java:309-359`、`.../reflection/ReflectionBizModelBuilder.java:110-198,299-314,421-422`、`.../engine/RpcSelectionSetBuilder.java:145-164`
- graphql-orm：`nop-service-framework/nop-graphql/nop-graphql-orm/src/main/java/io/nop/graphql/orm/OrmFetcherBuilder.java:136-181`（GQL-001）、`.../fetcher/OrmEntityPropConnectionFetcher.java:52-172`
- dict：`nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/fetcher/DictLabelFetcherProvider.java:40-78`（GQL-005）、`nop-kernel/nop-xlang/src/main/resources/_vfs/nop/core/xlib/meta-gen.xlib:22,32-61`、`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/DictBean.java:195-264`、`nop-kernel/nop-core/src/main/java/io/nop/core/dict/DictProvider.java:115`、`nop-kernel/nop-core/src/main/java/io/nop/core/CoreConfigs.java:229-230`
- xmeta/ui：`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/schema/obj-schema.xdef:61-130`（GQL-002）、`nop-frontend-support/nop-ui/src/main/java/io/nop/xui/utils/XuiViewAnalyzer.java:248-409`（GQL-006）、`nop-frontend-support/nop-ui/src/main/java/io/nop/xui/utils/XuiHelper.java:56-188`、`nop-kernel/nop-xlang/src/main/java/io/nop/xlang/xdsl/ExtPropsGetter.java:25-51`（GQL-007）
- 前端渲染：`nop-auth/nop-auth-web/precompile/gen-page.xgen:4`（GEN-005）、`nop-kernel/nop-codegen/src/main/resources/_vfs/nop/templates/orm-web/`、`nop-frontend-support/nop-web/src/main/java/io/nop/web/biz/PageProviderBizModel.java:37-44`、`nop-frontend-support/nop-web/src/main/java/io/nop/web/page/PageProvider.java:175-246`、`nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/web.xlib:11-20`、`nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/web/impl_GenPage.xpl:8-34`、`nop-frontend-support/nop-web/src/main/java/io/nop/web/page/WebPageHelper.java:100-161`、`nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-web.xlib:711-794`、`nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-control.xlib`（EXT-008，75 标签）、`nop-frontend-support/nop-web/src/main/resources/_vfs/nop/web/xlib/flux-web.xlib`（EXT-009，37 标签）
- 真实 view.xml：`nop-auth/nop-auth-web/.../NopAuthResource/NopAuthResource.view.xml`（UI-001）、`nop-job/nop-job-web/.../NopJobSchedule/NopJobSchedule.view.xml`（UI-002）、`nop-rule/nop-rule-web/.../NopRuleNode/NopRuleNode.view.xml`（UI-003）、`nop-wf/nop-wf-web/.../designer/designer.page.yaml`（UI-004）

### 平台文档

- `docs-for-ai/02-core-guides/service-layer.md`（BIZ/服务层约定）
- `docs-for-ai/02-core-guides/api-and-graphql.md`（统一分发、`graphql:*` 全集、`@query:` 机制）
- `docs-for-ai/02-core-guides/frontend-rendering-pipeline.md`（两阶段生成、三层 Delta、控件匹配）
- `docs-for-ai/02-core-guides/amis-rendering.md`（AMIS 渲染管线）
- `docs-for-ai/02-core-guides/flux-rendering.md`（Flux 渲染管线、NormalizeAction）
- `docs-for-ai/04-reference/source-anchors.md`（BIZ-001~007 / GQL-001~008 / DOC-001~003 / UI-001~004 / EXT-007~009 / TXN-001）
- `ai-dev/analysis/2026-07/2026-07-24-nop-core-engine-deep-dive.md`（A2 引擎机制，本分析引用其分发结论）
- `ai-dev/analysis/2026-07/2026-07-24-nop-model-driven-and-codegen.md`（A3 codegen 管线）
- 复用的既有专题分析：`ai-dev/analysis/2026-07/2026-07-15-amis-dollar-shorthand-vs-expression-syntax.md`、`ai-dev/analysis/2026-07/2026-07-22-amis-dom-selector-reference.md`、`ai-dev/analysis/2026-06-19-amis-expression-syntax-unification.md`、`ai-dev/analysis/2026-06-28-amis-component-schema.md`、`ai-dev/analysis/2026-06-28-amis-vs-flux-schema-comparison.md`、`ai-dev/analysis/2026-07-11-flux-web-xlib-design-analysis.md`、`ai-dev/analysis/2026-06-24-compact-ext-field-analysis.md`、`docs/theory/nop-graphql-design-innovation.md`

### 外部链接（访问日期 2026-07-24）

- Spring for GraphQL — Annotated Controllers: <https://docs.spring.io/spring-graphql/reference/controllers.html>
- Hasura — GraphQL Schema Overview: <https://hasura.io/docs/2.0/schema/overview/>
- Hasura — PostgreSQL instant GraphQL: <https://hasura.io/graphql/database/postgresql>
- Apollo — Introduction to Federation: <https://www.apollographql.com/docs/federation/v1>
- Apollo — Schema Composition: <https://www.apollographql.com/docs/graphos/schema-design/federated-schemas/composition>
- Sam Newman — Backends For Frontends pattern: <https://samnewman.io/patterns/architectural/bff/>
- Azure Architecture Center — Backends for Frontends Pattern: <https://learn.microsoft.com/en-us/azure/architecture/patterns/backends-for-frontends>
- alibaba/formily (JSON Schema driven form engine): <https://github.com/alibaba/formily>
- alibaba/lowcode-engine (protocol-based lowcode page builder): <https://github.com/alibaba/lowcode-engine>
