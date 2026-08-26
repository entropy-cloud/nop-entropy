# nop-graphql 实现代码检查报告（check2）

- 检查日期: 2026-08-23
- 模块路径: nop-service-framework/nop-graphql
- 文件数: 205（src/main/java，已排除 `_gen/` 与 `_` 前缀生成文件；分布：nop-graphql-core 154 / nop-graphql-grpc 37 / nop-graphql-message 3 / nop-graphql-orm 11）
- 覆盖范围声明: 深读约 70 个文件，重点覆盖：引擎执行链（GraphQLEngine、GraphQLExecutor、GraphQLSelectionResolver、GraphQLActionAuthChecker、GraphQLArgumentValidator、DataFetchingEnvironment、GraphQLExecutionContext、SelectionBeanBuilder、RpcSelectionSetBuilder、CancelTokenManager、各 Invoker/Fetcher 包装器、订阅发布器）；解析层（GraphQLDocumentParser、GraphQLDocumentHelper、AST 关键类 Document/Operation/FieldSelection/SelectionSet/Directive/Variable/Literal/ObjectDefinition/FieldDefinition）；biz 反射装配（ReflectionBizModelBuilder、GraphQLBizModel(s)、ReflectionGraphQLTypeFactory、ArgBuilders、各 Normalizer）；schema/缓存（TypeRegistry、GraphQLSchema、BuiltinSchemaLoader、ObjMetaToGraphQLDefinition）；fetcher 全家族；web/ws/jsonrpc/subscription 层；orm 子模块全部 fetcher；grpc 子模块（GrpcServer、ServiceSchemaManager、GraphQLServerCallHandler、marshalling 抽样）。并对全部 4 个子模块执行了模式扫描（catch 吞噬、bare RuntimeException、SimpleDateFormat/Random、Spring 依赖、private @Inject、System.out、deepClone/toSource 热点）。未深读区域：AST 剩余简单节点类（GraphQLArgument 等 12 个）、grpc marshaller 叶子类（Int/Long/Boolean 等 13 个小文件）、DevDoc/DevModelGrpcBizModel、GraphQLToJsonSchema、ObjPropGraphQLMapperRegistry、GraphQLRpcProxyFactoryBean、utils 中 GraphQLNameHelper/GraphQLObjMetaHelper/GraphQLResponseHelper/JaxrsHelper/GraphQLSourcePrinter。结论均来自 live code，并对 nop-api-core（GraphQLRequestBean）、nop-orm-model（OrmEntityModel.getProp）、nop-biz（CrudBizModel.prepareFindPageQuery、BizObjectQueryProcessorAdapter、BizObjectManager）、nop-core（ExecutionContextImpl.complete）、quarkus/spring 的 JsonRpcWebSocketEndpoint 做了跨模块交叉验证。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 2 |
| P2 | 5 |
| P3 | 8 |

> 注：初版统计表 P3 误记为 7，实际发现列表 P3 条目为 8（2026-08-26 处置时按发现列表逐条对账修正，与 plan346 的 P1×2 P2×5 P3×8 口径一致）。

## 发现列表

### [P1] maker-checker 开启时，多 operation 文档的 tryAction 决策永远取第一个 operation 的 fieldDef，且 HTTP GraphQL 路径 request 恒为 null

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/engine/GraphQLExecutor.java:274-287`
- **维度**: D1（附带 D5：审批机制可被绕过）
- **证据**:
```java
private CompletionStage<OperationResult> invokeOperationOrTry(DataFetchingEnvironment env) {
    ...
    CompletionStage<OperationResult> future = null;
    if (env.getGraphQLExecutionContext().isMakerCheckerEnabled()) {
        GraphQLFieldSelection operation = env.getGraphQLExecutionContext().getOperation().getFieldSelection();
        GraphQLFieldDefinition fieldDef = operation.getFieldDefinition();
        if (fieldDef.getTryAction() != null) {
            Object request = env.getOpRequest();
            FieldSelectionBean selection = env.getSelectionBean();
            future = FutureHelper.futureCall(() -> {
                return withFlowControl(v -> fieldDef.getTryAction().invoke(request, selection, ...)).get(env);
            }).thenApply(v -> new OperationResult(v, true));
```
- **现状**: `invokeOperationOrTry` 被 `_invokeOperations`（GraphQLExecutor.java:357、380）对文档中**每个**顶层 selection 调用，但 maker-checker 分支里 `getOperation().getFieldSelection()`（GraphQLOperation.java:41-43，取 `getSelectionSet().getSelections().get(0)`）返回的始终是**第一个**顶层字段的 fieldDefinition，与当前正在执行的 `env.getSelection()` 无关。同时 `env.getOpRequest()` 在 executeAsync 路径为 null（`DataFetchingEnvironment.copy()` 不拷贝 opRequest；opRequest 仅在 RPC 路径 `GraphQLOperation.addBizAction` 设置）。
- **风险**: `nop.graphql.maker-checker.enabled=true` 时（GraphQLWebService.java:87 从配置写入 context）：
  1. 文档 `mutation { A__x B__y }` 中 B 标注了 `@BizMakerChecker` 而 A 未标注：B 的 tryAction 永远不会被咨询，B 直接执行——审批拦截被静默绕过；
  2. A 标注而 B 未标注：B 会被错误地路由进 A 的 tryAction（request=null、selection 为 B 的）；
  3. 即使单 operation 的 HTTP GraphQL 请求，tryAction 也收到 null request（GraphQL 请求参数在 selectionBean 中而非 opRequest）。
- **建议**: maker-checker 分支改用 `env.getSelection().getFieldDefinition()` 判定与取 tryAction；HTTP GraphQL 路径需要从 selectionBean/args 构造 request，或明确该机制仅支持 RPC 路径并在 executeAsync 路径禁用/报错。
- **误报排除**: 已读 `_invokeOperations` 全文确认逐字段调用 `invokeOperationOrTry(env)`；已读 `GraphQLOperation.getFieldSelection`/`addBizAction` 与 `DataFetchingEnvironment.copy()` 确认首字段语义与 opRequest 仅 RPC 设置；已读 `GraphQLWebService.runGraphQL` 确认 HTTP 路径会按配置开启 makerCheckerEnabled；已确认 `GraphQLFieldDefinition.getTryAction` 的装配（setTryAction）不在本模块但读取路径属实。
> **处置（fix-ai-check 分支，2026-08-26）**: 已修复。maker-checker 分支改用 `env.getSelection().getFieldDefinition()`（当前正在执行的顶层字段）判定与取 tryAction；request 取值对齐 `GraphQLArgumentValidator.checkField` 惯例——`selection.getOpRequest()`（RPC 路径）为 null 时回落 `selectionBean.getArgs()`（HTTP GraphQL 路径，原先恒为 null）。新增 `TestMakerCheckerMultiOperation`（2 用例）。红验证：修复前 `mutation A/B` 场景 1（B 标注、A 未标注）`expected: <try_b> but was: <normal_b_1>`（B 的 tryAction 被静默绕过）；场景 2（A 标注、B 未标注）`expected: <normal_b_2> but was: <try_a>`（B 被错误路由进 A 的 tryAction），与报告两个风险场景逐条吻合；request 参数断言 `{x:1}` 非空。

### [P1] OrmEntityPropConnectionFetcher 偏移分页路径 hasNextPage 恒为 true（比较对象用错）

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-orm/src/main/java/io/nop/graphql/orm/fetcher/OrmEntityPropConnectionFetcher.java:218-227`
- **维度**: D1
- **证据**:
```java
} else {
    List<Object> data = fetcher.apply(query);
    if (data != null) {
        GraphQLPageInfo pageInfo = new GraphQLPageInfo();
        pageInfo.setHasPreviousPage(query.getOffset() > 0);
        if (data.size() < input.getLimit()) {      // <-- input.getLimit() 而非生效的 query.getLimit()
            pageInfo.setHasNextPage(false);
        } else {
            pageInfo.setHasNextPage(true);
        }
```
- **现状**: 参数经 `query:{offset,limit}` 对象传入时（line 72-77：`query = input.getQuery()` 非 null 时不再从 input 复制 offset/limit），`input.getLimit()` 保持 0，而实际生效的 limit 在 `query` 上。`data.size() < 0` 恒为 false → `hasNextPage` 恒为 true，即使返回行数远小于生效 limit（已是最后一页）。顶层 `limit` 参数路径（input.getLimit()>0）不受影响。
- **风险**: 客户端按 pageInfo 做偏移翻页时会无限追加空页请求：数据正确但分页契约错误，造成重复查询负载和客户端死循环风险。
- **建议**: 改为 `data.size() < query.getLimit()`（fetcher.apply 之后 query.getLimit() 即生效 limit）。
- **误报排除**: 已通读该 fetcher 全文确认 input/query 两套 limit 的赋值链（line 73-77、100-107、fetchItems 各分支）；已确认 `doFindPage0` 返回的 PageBean.items 行数以 query.limit 为界（CrudBizModel.doFindPageByQueryDirectly）；GraphQLConnectionInput 的 limit 与 query.limit 是两个独立来源。
> **处置（fix-ai-check 分支，2026-08-26）**: 已修复。`data.size() < input.getLimit()` 改为 `data.size() < query.getLimit()`（fetcher.apply 之后 query.limit 即生效 limit）。新增 `TestOrmEntityPropConnectionFetcher`（nop-graphql-orm 首个测试类，6 用例）。红验证：修复前 `3 rows returned with effective limit 5 must be the last page ==> expected: <false> but was: <true>`（query 对象路径 hasNextPage 恒 true），与报告描述一致；顶层 limit 路径与满页对照用例同时覆盖。

### [P2] JsonRpcWebSocketHandler 初始身份绑定从未执行，tokenRefresh 的用户一致性/有效性校验为死代码

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/ws/JsonRpcWebSocketHandler.java:81-85, 226, 290-309`
- **维度**: D1/D5
- **证据**:
```java
// 构造函数（line 81-85）:
this.keepAliveSender = GlobalExecutors.globalTimer().scheduleWithFixedDelay(this::sendKeepAlive, 10, 10, TimeUnit.SECONDS);
bindUserIdentityIfNeeded();      // 此时 userContextExtractor 必然为 null

// handleTokenRefresh（line 226）:
if (userContextExtractor != null && boundUserId != null) {   // boundUserId 永远为 null，分支不可达
    IUserContext userContext = userContextExtractor.extractFromHeaders(mergedHeaders);
    ...
// bindUserIdentityIfNeeded（line 290 起）:
private void bindUserIdentityIfNeeded() {
    if (boundUserId != null) return;
    if (userContextExtractor == null) return;   // 构造时恒成立，直接 return
```
- **现状**: `bindUserIdentityIfNeeded()` 仅在构造函数中调用，而 `userContextExtractor` 由端点在构造**之后**通过 `setUserContextExtractor` 注入（nop-quarkus-web 与 nop-spring-web-starter 的 JsonRpcWebSocketEndpoint 均如此，全仓库无其他调用点——已 grep 验证）。因此 `boundUserId`/`boundSessionId` 永远为 null：连接建立时"无认证信息则关闭连接（4401）"的设计不生效；tokenRefresh 中刷新令牌的用户一致性校验（4403）与有效性校验（4401）全部不可达，客户端可通过 `tokenRefresh` 消息把会话 authHeaders 任意替换（含切换到其他用户的令牌）后继续订阅。
- **风险**: 会话身份绑定与刷新令牌校验这套纵深防御在所有部署下失效。实际利用仍需持有目标用户的有效令牌（否则订阅在执行层鉴权失败），故不评 P0/P1；但"未认证连接关闭"与"刷新令牌校验"两个设计行为完全落空。
- **建议**: `setUserContextExtractor` 内调用 `bindUserIdentityIfNeeded()`，或端点改为先注入 extractor 再启用消息处理。
- **误报排除**: 已 grep 全仓库确认 `bindUserIdentityIfNeeded`/`bindUserFromContext` 仅有类内两处调用；已读 quarkus/spring 两个 Endpoint 的 `onOpen` 确认 extractor 在构造之后注入；已读 `handleTokenRefresh` 全文确认守卫条件。
> **处置（fix-ai-check 分支，2026-08-26）**: 已修复。`setUserContextExtractor` 注入时调用 `bindUserIdentityIfNeeded()`（spring 端点 `tryGetBeanByType` 可能返回 null，bind 内部已有 null 守卫），并删除构造函数中恒空跑的 bind 调用。`TestJsonRpcWebSocketHandler` 新增 4 用例（StubUserContext 手写桩）。红验证：修复前 `identity must be bound when extractor is injected ==> expected: <u1> but was: <null>`、`connection without auth info must be closed ==> expected: <true> but was: <false>`、tokenRefresh 场景在 bind 断言即红（boundUserId=null 使 4403 校验不可达），与报告'设计行为完全落空'一致；同用户令牌刷新正常生效的对照用例同时覆盖。

### [P2] GraphQL HTTP 请求缺省 variables 时，引用 `$var` 的查询触发 NPE 而非校验错误

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/ast/GraphQLVariable.java:26-30`（触发点）；链路 `IGraphQLEngine.java:80-88` → `GraphQLEngine.java:401-403` → `GraphQLFieldSelection.java:82-99`
- **维度**: D1/D4
- **证据**:
```java
// GraphQLVariable.buildValue:
public Object buildValue(Map<String, Object> vars) {
    String name = getName();
    Object value = vars.get(name);   // vars == null 时 NPE
    return value;
}

// IGraphQLEngine.newGraphQLContext（default 方法）:
parsed.setVariables(request.getVariables());   // GraphQLRequestBean.variables 无缺省值，可为 null

// GraphQLEngine.initGraphQLContext:
Map<String, Object> vars = request.getVariables();
FieldSelectionBean selectionBean = buildSelectionBean(op.getName(), op.getSelectionSet(), vars);
```
- **现状**: `GraphQLRequestBean.variables` 字段无初始化（nop-api-core/GraphQLRequestBean.java:24、57-58，已交叉验证），请求 JSON 省略 `variables` 键时为 null。查询文本里声明并引用了 `$var` 时，解析期变量存在性校验用的是文档自身的变量定义（`op.getVars()`，非 null），通过后到 `buildSelectionBean` 才在 `GraphQLVariable.buildValue(null)` 处 NPE。
- **风险**: 客户端收到 internal error（NOP 内部错误 + NPE 堆栈信息）而非明确的"变量未提供"校验错误；GraphQL 规范语义应为"未提供变量 = null"。已被 web 层 catch 收敛为错误响应，无崩溃。
- **建议**: `newGraphQLContext`/`initGraphQLContext` 对 null variables 归一化为空 Map；或 `GraphQLVariable.buildValue` 对 null vars 报 `ERR_GRAPHQL_UNKNOWN_VAR`。
- **误报排除**: 已读 GraphQLRequestBean 源码确认 variables 可为 null；已读 runGraphQL 的 try/catch 确认错误被收敛为响应而非崩溃；已确认 JsonRpc/RPC 路径不经过 buildSelectionBean(vars)（参数为字面量/ApiRequest）。
> **处置（fix-ai-check 分支，2026-08-26）**: 已修复（采用报告建议一：归一化而非报错）。`initGraphQLContext` 对 null variables 归一化为空 Map（GraphQL 规范语义：未提供变量 = 取值 null）。新增 `TestGraphQLVariables`。红验证：修复前 `java.lang.NullPointerException: Cannot invoke "java.util.Map.get(Object)" because "vars" is null`，与报告链路（buildSelectionBean → GraphQLVariable.buildValue(null)）一致；修复后缺省 variables 的 `$var` 查询得到明确的 field-null-arg 校验错误而非 internal error。

### [P2] 订阅发布器对每条消息调用 context.complete()，首条消息即终结执行上下文

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/engine/GraphQLSubscriptionPublisher.java:59-67`、`RpcSubscriptionPublisher.java:58-66`；`GraphQLEngine.java:620-627`
- **维度**: D1/D2（生命周期）
- **证据**:
```java
// GraphQLSubscriptionPublisher.TransformingSubscriber.onNext:
public void onNext(Object item) {
    try {
        GraphQLResponseBean response = engine.buildGraphQLResponse(item, null, context);  // 内部 complete
        downstream.onNext(response);

// GraphQLEngine.buildGraphQLResponse:
if (context != null) {
    if (err != null) {
        context.completeExceptionally(err);
    } else {
        context.complete();     // 标记 IServiceContext 完成，触发 before/afterComplete 回调
    }
}
```
- **现状**: `buildGraphQLResponse`/`buildRpcResponse` 是为一次性请求-响应设计的收尾函数，内部调用 `IGraphQLExecutionContext.complete()`（默认实现转到 `IServiceContext.complete()`，见 nop-core `ExecutionContextImpl.complete()`：置 done 并 fire before/afterComplete 回调）。订阅流中每条消息都经过该函数，第一条消息就会把整个执行上下文标记为完成并触发上下文级完成回调，而订阅仍继续在该上下文上派发后续消息（惰性字段加载、batch loader 等均依赖该上下文）。
- **风险**: 任何挂在上下文完成回调上的清理逻辑（会话释放、审计收尾等，取决于运行时装订）会在订阅存活期间被提前触发；后续消息在"已完成"上下文上执行。默认装订下未观察到直接崩溃，但违反生命周期契约，属于随装订变化的隐患。
- **建议**: 为订阅路径提供不触发 complete 的响应构建变体；完成动作应移到 publisher 的 onComplete/onError 或取消路径。
- **误报排除**: 已读两个 Publisher 全文与 `IGraphQLExecutionContext.complete` 默认实现、`ExecutionContextImpl.complete()`（nop-core）确认行为；已确认 quarkus WS 端点经 `subscribeRpc` → `RpcSubscriptionPublisher` 使用该路径。
> **处置（fix-ai-check 分支，2026-08-26）**: 已修复（含报告事实修正）。事实修正：GraphQL 路径的普通数据项经 `buildGraphQLResponse` 的非 GraphQLResponseBean 分支提前 return，原本并不触发 complete；实际触发面为 GraphQL 路径的 GraphQLResponseBean 形态消息（每条 complete）与 **RPC 路径全部消息**（`buildRpcResponse` 无提前返回分支，每条 complete）——RPC 路径（quarkus WS 端点实际使用）的'首条消息即终结上下文'成立。修复：两个 Publisher 的 onNext 以 null context 构建响应（不触发 complete；Rpc 路径同时不再逐消息复制 context 响应头——唯一消费者 JsonRpcWebSocketHandler.SubscriptionSubscriber 不消费订阅消息的 headers），完成动作移到流终止信号（onComplete→complete，onError→completeExceptionally 经原 buildXxx(null, err, context)）。`TestGraphQLSubscriptionPublisher` 扩展 4 用例。红验证：修复前 RPC 路径 `rpc subscription must not complete the context on the first message`、GraphQL 路径 GraphQLResponseBean 消息 `execution context must not be completed by individual subscription messages`、流终止后 `execution context must be completed when the stream terminates` 三种形态齐红；仓库内 onBeforeComplete/onAfterComplete 回调注册方仅 nop-batch（不在订阅链路），无下游依赖提前 complete 的行为。

### [P2] ServiceSchemaManager：operation 返回枚举类型时 gRPC 服务注册抛 ClassCastException

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-grpc/src/main/java/io/nop/graphql/grpc/server/ServiceSchemaManager.java:156-177, 188-204`
- **维度**: D1/D8
- **证据**:
```java
private GenericObjSchema buildResponseSchema(String responseName, GraphQLType type) {
    ...
    } else {
        return (GenericObjSchema) marshaller;    // marshaller 可能是 IntFieldMarshaller.INSTANCE
    }

private IFieldMarshaller buildItemTypeMarshaller(GraphQLType type, boolean allowRequired) {
    if (type.isScalarType()) { ... }
    else {
        GraphQLTypeDefinition objDef = graphQLEngine.getSchemaLoader().resolveTypeDefinition(type);
        if (objDef.isEnumDefinition())
            return IntFieldMarshaller.INSTANCE;   // 枚举返回 IntFieldMarshaller
```
- **现状**: 顶层 operation 返回类型为枚举（非 scalar、非 list 的命名枚举类型）时，`buildTypeMarshaller` → `buildItemTypeMarshaller` 返回 `IntFieldMarshaller.INSTANCE`，随后 `buildResponseSchema` 的 else 分支将其强转为 `GenericObjSchema`，启动期注册 gRPC 服务时抛 CCE。
- **风险**: 启用 gRPC server 且存在枚举返回的 operation 时服务无法启动，且 CCE 无类型/方法上下文，难以定位。ReflectionGraphQLTypeFactory 确认枚举会生成 named enum 类型（buildEnumDef），返回枚举是合法建模。
- **建议**: `buildResponseSchema` 对枚举类型按 scalar 风格包装单字段 `value` 的 GenericObjSchema（复用 int marshaller），或在强转前显式校验并抛带 operation 名的 NopException。
- **误报排除**: 已读 `buildResponseSchema`/`buildTypeMarshaller`/`buildItemTypeMarshaller` 全文及 `getResponseName`；已确认 `GraphQLScalarType` 内置名单不含业务枚举名（isScalarType 为 false）；已读 ReflectionGraphQLTypeFactory 确认枚举返回类型的建模路径。
> **处置（fix-ai-check 分支，2026-08-26）**: 已修复（采用报告建议一：scalar 风格包装）。`buildResponseSchema` else 分支先 `instanceof GenericObjSchema` 判别再使用，枚举等非对象命名类型按标量风格包装为单字段 `value`（LABEL_OPTIONAL + 复用 buildItemTypeMarshaller 对枚举返回的 IntFieldMarshaller，与枚举字段的既定编解码约定一致）。免红测试注明理由：nop-graphql-grpc 在父 pom 中被注释排除（非默认构建模块）、无测试基建（与 check 系列 P3-10 处置先例一致），以单独 `./mvnw compile -f nop-graphql-grpc/pom.xml -o` 验证通过；行为分支由 instanceof 守卫保证（非 GenericObjSchema 一律走包装，不限于枚举）。

### [P2] OrmEntityPropConnectionFetcher：first/last 分页路径重置 limit，绕过字段级 maxFetchSize 上限

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-orm/src/main/java/io/nop/graphql/orm/fetcher/OrmEntityPropConnectionFetcher.java:94-107, 176-177, 198-199`
- **维度**: D5（资源限制失效）/D1
- **证据**:
```java
// 主流程先钳制（line 94-107）:
if (queryMethod != GraphQLQueryMethod.findFirst) {
    int maxSize = maxFetchSize;
    if (maxSize <= 0) maxSize = CFG_GRAPHQL_MAX_PAGE_SIZE.get();
    if (query.getLimit() <= 0) query.setLimit(maxSize);
    if (query.getLimit() > maxSize) query.setLimit(maxSize);
}
// fetchItems 再重置（line 176-177 / 198-199）:
if (input.getLast() > 0) {
    query.setLimit(input.getLast() + 1);
...
} else if (input.getFirst() > 0) {
    query.setLimit(input.getFirst() + 1);
```
- **现状**: `first`/`last` 是客户端可控参数（`BeanTool.castBeanToType(env.getArgs(), GraphQLConnectionInput.class)`）。主流程钳制后，`fetchItems` 用 `first+1`/`last+1` 覆盖 limit，本 fetcher 层的 `graphql:maxFetchSize`（propMeta 配置，可小于全局）失效。已交叉验证下游 `CrudBizModel.prepareFindPageQuery` 会再次按 `getMaxPageSize()`（全局 CFG_GRAPHQL_MAX_PAGE_SIZE=1000 或 objMeta 上调值）钳制，因此不会无界拉取。
- **风险**: 开发者在特定关联属性上配置的小额 maxFetchSize（如敏感大集合限制为 10）可被 `first: 999` 绕过，实际拉取可达全局上限 1000 行；分页语义也因 `data.size() > input.getFirst()` 的比较被钳制值干扰而可能与配置意图不符。
- **建议**: `fetchItems` 内重置 limit 后再按 maxSize 钳制一次（`Math.min(first+1, maxSize)`）。
- **误报排除**: 已读该文件全部 limit 赋值链；已读 nop-biz 的 `CrudBizModel.prepareFindPageQuery`/`getMaxPageSize` 与 `BizObjectQueryProcessorAdapter`（确认处理器最终路由到该方法）确认全局兜底存在，故不评 P1。
> **处置（fix-ai-check 分支，2026-08-26）**: 已修复（采用报告建议：重置后再钳制）。`fetchItems` 开头计算 maxSize（maxFetchSize<=0 时取全局 CFG_GRAPHQL_MAX_PAGE_SIZE），first/last 重置 limit 改为 `Math.min(first+1/last+1, maxSize)`。`TestOrmEntityPropConnectionFetcher` 新增 3 用例。红验证：修复前 first:999/maxFetchSize:10 `first+1 must be clamped to the field-level maxFetchSize ==> expected: <10> but was: <1000>`（last 路径同形态），字段级上限被绕过与报告一致；first 低于上限时保持 first+1 探测语义的对照用例同时覆盖。

### [P3] GraphQLSchema.addType 重复类型时误用 directive 的错误码与参数名

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/schema/GraphQLSchema.java:73-78`
- **维度**: D4/D8
- **证据**:
```java
public void addType(GraphQLTypeDefinition type) {
    GraphQLDefinition old = types.putIfAbsent(type.getName(), type);
    if (old != null)
        throw new NopException(ERR_GRAPHQL_DUPLICATE_DIRECTIVE_DEF).source(type)
                .param(ARG_DIRECTIVE_NAME, type.getName()).param(ARG_OLD_LOC, old.getLocation());
}
```
- **现状**: 类型重复抛出的错误码是"重复指令定义"（ERR_GRAPHQL_DUPLICATE_DIRECTIVE_DEF），参数名也是 ARG_DIRECTIVE_NAME，复制粘贴自 addDirective。
- **风险**: 内置 schema 装载失败时诊断信息指向"指令"而非"类型"，误导排障。无运行时数据危害。
- **建议**: 新增/复用类型重复错误码并改用 ARG_TYPE_NAME。
- **误报排除**: 已读 GraphQLErrors 中同时存在 ERR_GRAPHQL_DUPLICATE_OBJ_DEF 等类型语义错误码，确认非笔误性唯一选择。
> **处置（fix-ai-check 分支，2026-08-26）**: 已修复。改用既有 `ERR_GRAPHQL_DUPLICATE_TYPE_DEF`（nop.err.graphql.duplicate-type-def）+ `ARG_TYPE_NAME`（两者早已定义且有 i18n，无需新增）。新增 `TestGraphQLSchemaAddType`（2 用例，对象类型与枚举类型各一）。红验证：修复前 `expected: <nop.err.graphql.duplicate-type-def> but was: <nop.err.graphql.duplicate-directive-def>`，与报告'复制粘贴自 addDirective'一致。

### [P3] GraphQLArgsHelper.normalizeSubArgs 对无二级点号的 `_subArgs.` 前缀参数抛 StringIndexOutOfBoundsException

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/utils/GraphQLArgsHelper.java:33-39`
- **维度**: D1/D4（边界输入）
- **证据**:
```java
if (name.startsWith(GraphQLConstants.SUB_PARAMS_PREFIX)) {   // 前缀 "_subArgs."（9字符，含点）
    Object value = entry.getValue();
    int pos = name.lastIndexOf('.');
    String subName = name.substring(GraphQLConstants.SUB_PARAMS_PREFIX.length(), pos);  // pos=8 < 9 → SIOOBE
```
- **现状**: 前缀本身以 `.` 结尾。形如 `?_subArgs.foo=1`（REST 查询参数，经 GraphQLWebService.buildRequest 合入 args）匹配前缀但无第二个点，`lastIndexOf('.')` 落在前缀内（8），`substring(9, 8)` 抛 SIOOBE。
- **风险**: 恶意/畸形查询参数得到 internal error（被 web 层 catch 收敛）而非 400 参数错误。无数据危害。
- **建议**: `pos <= SUB_PARAMS_PREFIX.length() - 1` 时跳过或报参数格式错误。
- **误报排除**: 已确认 SUB_PARAMS_PREFIX 常量值为 `"_subArgs."`（GraphQLConstants.java:146）；已读 buildRequest 确认外部参数可进入该路径。
> **处置（fix-ai-check 分支，2026-08-26）**: 已修复（采用报告建议的'跳过'分支）。`pos < SUB_PARAMS_PREFIX.length()` 时 continue 跳过（畸形条目保留在 args 中，不 relocate、不删除）。`TestGraphQLArgsHelper` 新增 2 用例。红验证：修复前 `_subArgs.foo` → `StringIndexOutOfBoundsException: Range [9, 8) out of bounds for length 12`、裸前缀 `_subArgs.` → `Range [9, 8) out of bounds for length 9`，与报告推导一致。

### [P3] GraphQLSubscriptionManager 消息推送双重 JSON 序列化，且无订阅者时仍序列化

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/subscription/GraphQLSubscriptionManager.java:169-188, 202-215`
- **维度**: D6
- **证据**:
```java
protected Object onMessage(String topic, Object message, IMessageConsumeContext context) {
    List<SubscriptionInfo> matchedSubscriptions = findMatchingSubscriptions(topic);
    String text = JsonTool.stringify(message);          // 第一次序列化（即使无匹配订阅）
    for (SubscriptionInfo subscription : matchedSubscriptions) {
        pushToClient(subscription, topic, text);
...
protected void pushToClient(SubscriptionInfo subscription, String topic, String message) {
    ...
    response.put("result", JsonTool.parse(message));    // 第二次：parse 回对象
    session.sendMessage(JsonTool.stringify(response));  // 第三次：再序列化
```
- **现状**: 每条消息先 stringify，推送时再 parse + stringify；无匹配订阅时白做一次全量序列化。
- **风险**: 消息热点路径上的 CPU/内存浪费，大消息放大 3 倍编解码成本。另注：`registerSubscription` 的 containsKey+put 非原子（并发同 id 注册可双双通过），但 WS 消息通常串行处理，现实触发概率低。
- **建议**: 传递原始 message 对象，仅在 sendMessage 处序列化一次；匹配为空时短路。
- **误报排除**: 已读该类全文确认调用链与空匹配路径；已确认 `GraphQLSubscriptionManager` 目前在生产代码中无 registerSubscription 调用方（仅注册为 bean），故只评 D6 性能项。
> **处置（fix-ai-check 分支，2026-08-26）**: 已修复（采用报告建议：传原始对象 + 空匹配短路）。`onMessage` 无匹配订阅时直接返回（不再无条件 stringify）；`pushToClient` 签名 String→Object，消息对象直接放入 result、仅在 sendMessage 处序列化一次（与原 stringify→parse→stringify 的输出逐字节等价）。`TestGraphQLSubscriptionManager` 新增 2 用例。红验证：修复前以不可序列化探针对象（new Object()）投递且无订阅者时 `NopException: nop.err.core.json.only-data-bean-is-serializable` 从 onMessage 传播（证明无订阅者也做了序列化），修复后静默返回；单次序列化的输出等价性由既有 4 个路由用例 + 新增内容断言覆盖。报告另注的 containsKey+put 非原子未修（保持现状，与 check 系列 P1-3 暂缓裁定一致：manager 属未接线原型）。

### [P3] GraphQLFieldDefinition.deepClone 不拷贝 makerCheckerMeta/tryAction/serviceAction 等运行时装配字段，与注释宣称的"对齐先例"矛盾

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/ast/GraphQLFieldDefinition.java:66-77, 87-100`
- **维度**: D8（契约漂移）
- **证据**:
```java
/** 操作级 MFA 元数据（@MfaRequired...）。传播链拷贝对齐 makerCheckerMeta 先例。 */
private MfaRequiredMeta mfaRequiredMeta;
...
public GraphQLFieldDefinition deepClone() {
    GraphQLFieldDefinition field = super.deepClone();
    field.setPropMeta(propMeta);
    ...
    field.setArgsNormalizer(argsNormalizer);
    field.setAuth(auth);
    field.setMfaRequiredMeta(mfaRequiredMeta);   // 拷贝了 mfaRequiredMeta
    return field;                                 // 但未拷贝 makerCheckerMeta / tryAction / operationName
}
```
- **现状**: deepClone 拷贝 propMeta/auth/mfaRequiredMeta 等，但遗漏 makerCheckerMeta、tryAction、serviceAction、operationName。注释声称 mfaRequiredMeta 的拷贝"对齐 makerCheckerMeta 先例"，实际 makerCheckerMeta 并未被拷贝。现有调用方（nop-biz BizObjectManager.getObjDef，用于构建 schema 导出文档）只用 SDL 可见信息，暂无运行时危害。
- **风险**: 任何后续把 deepClone 产物用于执行的代码会静默丢失 maker-checker/审批元数据（mfa 元数据却保留），形成选择性生效的不一致契约。
- **建议**: 补齐 makerCheckerMeta/operationName 拷贝（fetcher/serviceAction 视用途决定），并修正注释。
- **误报排除**: 已读 deepClone 全文与 `_GraphQLFieldDefinition.deepClone`（生成的基类只拷贝 AST 属性）；已 grep 全仓库确认生产调用方仅 BizObjectManager.getObjDef（SDL 导出）。
> **处置（fix-ai-check 分支，2026-08-26）**: 已修复（含注释勘误）。deepClone 补拷 `makerCheckerMeta` 与 `operationName`；fetcher/tryAction/serviceAction 刻意不拷贝（运行时装配的执行绑定，克隆体用于 schema 导出等非执行场景，混入会造成隐性执行入口）并在 deepClone 内注释显式声明排除清单与恢复条件；mfaRequiredMeta 字段注释同步勘误（原注释声称'对齐 makerCheckerMeta 先例'而该字段实际未被拷贝）。新增 `TestGraphQLFieldDefinitionDeepClone`。红验证：修复前 `maker-checker meta must survive deepClone ==> expected: <BizMakerCheckerMeta@...> but was: <null>`（operationName 同批断言覆盖），与报告'选择性生效的不一致契约'一致。

### [P3] JsonRpcService 批量上限错误的参数填充：ARG_MAX_COUNT 填的是实际数量而非上限

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/jsonrpc/JsonRpcService.java:68-73`
- **维度**: D4
- **证据**:
```java
if (GraphQLConfigs.CFG_GRAPHQL_QUERY_MAX_OPERATION_COUNT.get() < requests.size()) {
    NopException err = new NopException(ERR_JSONRPC_EXCEED_MAX_COMMAND_COUNT)
            .param(ARG_MAX_COUNT, requests.size());   // 应为配置上限
```
- **现状**: 错误参数 `maxCount` 报告的是请求中的实际命令数，客户端/运维无法从错误中得知允许上限。
- **风险**: 仅诊断误导，无运行时危害。
- **建议**: `.param(ARG_MAX_COUNT, GraphQLConfigs.CFG_GRAPHQL_QUERY_MAX_OPERATION_COUNT.get())`。
- **误报排除**: 已对照 GraphQLEngine.validateDocument（ARG_MAX_COUNT/ARG_COUNT 的正确用法）确认本处为笔误。
> **处置（fix-ai-check 分支，2026-08-26）**: 已修复。`.param(ARG_MAX_COUNT, requests.size())` 改为 `.param(ARG_MAX_COUNT, GraphQLConfigs.CFG_GRAPHQL_QUERY_MAX_OPERATION_COUNT.get())`。`TestJsonRpcService` 新增 `testExceedMaxCountReportsConfiguredLimit`（11 个命令的批量）。红验证：修复前错误消息为'最大个数为11'（实际数量），修复后'最大个数为10'（配置上限），与报告'客户端/运维无法得知允许上限'一致。

### [P3] BeanMethodBatchFetcher 用 Arrays.deepHashCode 构造 loader key：哈希碰撞会把不同参数静默合并为一批（错误数据）

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/fetcher/BeanMethodBatchFetcher.java:59-92`
- **维度**: D1（理论边界）/D6
- **证据**:
```java
String key = buildLoaderKey(args);
...
private String buildLoaderKey(Object[] args) {
    if (args.length == 1) return loaderName;
    Object[] keyArgs = new Object[args.length - 1];
    ...
    return loaderName + "@" + Arrays.deepHashCode(keyArgs);
}
```
- **现状**: 同名字段携带不同非 source 参数时按参数哈希区分 loader。deepHashCode 碰撞的两个不同参数集合会共享同一个 loader：batchLoader 闭包捕获的是**首次注册时**的 args 数组，第二批 key 的参数被静默丢弃，整批按首组参数计算——返回错误数据。代码注释只讨论了身份哈希导致"批次变细"的情形，未覆盖碰撞导致"批次错误合并"的情形。32 位哈希下需要同一请求内大量参数组合才现实可触发（生日界约 7 万组），概率低。
- **风险**: 极低概率下的静默错误数据，且无法从日志发现。
- **建议**: key 中加入确定性的参数序列化（如对基本类型/字符串拼接、复杂对象用稳定 JSON），或在注释中显式声明碰撞取舍。
- **误报排除**: 已读该类全文（synchronized 注册、args 闭包捕获、dispatch 语义）确认碰撞即错误合并；已确认 DataLoader 分发按 key 隔离的正确性依赖 key 不碰撞。
> **处置（fix-ai-check 分支，2026-08-26）**: 已修复（采用报告建议一：确定性序列化，碰撞静默合并属不可观测的错误数据故不选注释取舍）。buildLoaderKey 改为 `deepHashCode + ":" + deepToString` 双因子：基本类型/字符串/Map 等值对象由 deepToString 文本区分，两者须同时碰撞才错误合并；未覆写 toString 的对象（identity hash）只会使批次变细（安全方向，注释已声明）。`TestBeanMethodBatchFetcher` 新增 `testDeepHashCodeCollisionDoesNotMergeDifferentArgs`（经典碰撞对 "Aa"/"BB"，hashCode 均 2112，测试内前置断言碰撞成立）。红验证：修复前 `colliding-hash args must still be loaded in separate batches ==> expected: <2> but was: <1>`——第二组参数被静默丢弃、整批按首组参数计算，与报告推演一致。

### [P3] OrmFetcherBuilder：queryMethod 配在非关联属性且无 graphql:filter 时，初始化抛无上下文的 IllegalArgumentException("filter")

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-orm/src/main/java/io/nop/graphql/orm/OrmFetcherBuilder.java:135-155, 169-175`；`OrmEntityPropConnectionFetcher.java:59`
- **维度**: D4
- **证据**:
```java
} else {
    // 如果是关联集合属性，且设置了graphql:queryMethod...
    propModel = entityModel.getProp(propMeta.getName(), true);   // 未知属性返回 null
    if (propModel != null && !propModel.isRelationModel())
        propModel = null;
}
return buildConnectionFetcher(objType, queryMethod, (IEntityRelationModel) propModel, propMeta);
...
TreeBean relFilter = buildRelationFilter(propModel);              // null → 返回 null（OrmQueryHelper:88-89）
...
this.filter = Guard.notNull(filter, "filter");                   // IllegalArgumentException("filter")
```
- **现状**: `graphql:queryMethod` 配在实体上不存在/非关联的属性且未配置 `graphql:filter` 时，filter 为 null，构造器里 `Guard.notNull(filter, "filter")` 抛裸 IllegalArgumentException，只带参数名 "filter"，不含对象/属性名，启动失败难定位。`connectionProp` 指向未定义属性的路径（getProp(name,false)）则会抛带上下文的 ERR_ORM_UNKNOWN_PROP，两路错误质量不一致。
- **风险**: 仅配置错误路径的 fail-fast 质量（已交叉验证 OrmEntityModel.getProp 的 ignoreUnknown 语义与 buildRelationFilter 的 null 处理），无运行时数据危害。
- **建议**: buildConnectionFetcher 入口对 filter==null 抛带 objType/propName 的 NopException。
- **误报排除**: 已读 OrmEntityModel.getProp（false=抛错、true=返 null）与 OrmQueryHelper.buildRelationFilter 的 null 分支，确认链条成立。
> **处置（fix-ai-check 分支，2026-08-26）**: 已修复（采用报告建议）。`buildConnectionFetcher` 在 filter==null 时抛新错误码 `ERR_BIZ_CONNECTION_FILTER_REQUIRED`（nop.err.biz.connection-filter-required，GraphQLOrmErrors，param bizObjName/propName + source(propMeta)），构造器的 Guard.notNull 保留为最后防线。新增 `TestOrmFetcherBuilderConnectionFilter`（2 用例：未知属性与非关联属性两路，JDK proxy 桩 IEntityModel/IObjPropMeta）。红验证：修复前 `Unexpected exception type thrown, expected: <NopException> but was: <java.lang.IllegalArgumentException>`，cause `IllegalArgumentException: IsNull:filter`——裸 IAE 无对象/属性上下文，与报告一致。

### [P3] GraphQLExecutor.fetchNext：声明为 List 的字段返回数组/非 Collection 值时无提示 CCE

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/engine/GraphQLExecutor.java:534-536`
- **维度**: D1（边界）
- **证据**:
```java
GraphQLFieldDefinition fieldDef = env.getSelection().getFieldDefinition();
if (fieldDef.getType().isListType()) {
    return fetchList((Collection<?>) value, selectionSet, env);   // 数组等非 Collection → CCE
}
```
- **现状**: schema 声明为列表类型但 fetcher 实际返回 Object[]（或其他非 Collection）时直接 CCE，错误中无字段名信息。`isEmpty` 只识别 Collection，非集合非 null 值会走到强转。
- **风险**: 开发者错误 surfaced 为无上下文的 ClassCastException；平台内 fetcher 惯例返回 List，现实触发需要自定义 fetcher 违约。
- **建议**: 强转前 instanceof 检查，失败时抛带字段名的 NopException（或对数组做 Arrays.asList 适配）。
- **误报排除**: 已读 fetchNext/fetchList/isEmpty 全文；确认无其他 Collection 适配层（normalizeValue 只做类型转换不改集合形态）。
> **处置（fix-ai-check 分支，2026-08-26）**: 已修复（两条建议都采纳）。fetchNext 的 list 分支：Collection 直行；`Object[]` 经 `Arrays.asList` 适配继续装载；其余非集合形态抛新错误码 `ERR_GRAPHQL_FIELD_LIST_VALUE_NOT_COLLECTION`（nop.err.graphql.field-list-value-not-collection，i18n zh-CN/en 同步）携带 fieldName/actualType。新增 `TestGraphQLExecutorFetchNext`（2 用例）。红验证：修复前数组 `ClassCastException: class [Ljava.lang.String; cannot be cast to class java.util.Collection`、非集合 `expected: <NopException> but was: <ClassCastException>`，与报告'无字段名信息的 CCE'一致。
