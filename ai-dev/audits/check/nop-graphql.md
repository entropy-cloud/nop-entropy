# nop-graphql 实现代码检查报告

- 检查日期: 2026-08-19
- 模块路径: nop-service-framework/nop-graphql
- 文件数: 239（src/main/java，分布于 4 个子模块：nop-graphql-core 188 / nop-graphql-grpc 37 / nop-graphql-message 3 / nop-graphql-orm 11；target/ 与测试代码未计入）
- 覆盖范围声明: 全模块 grep 扫描（空 catch、bare RuntimeException、printStackTrace、synchronized、可变 static 集合、@Inject/@Value 用法、beans.xml 装配）；精读执行引擎（GraphQLExecutor/GraphQLExecutionContext/GraphQLEngine）、查询解析器（GraphQLDocumentParser）、selection 解析与构建（GraphQLSelectionResolver/SelectionBeanBuilder/RpcSelectionSetBuilder）、DataLoader 批处理（BeanMethodBatchFetcher）、BizApi 反射装配（ReflectionBizModelBuilder/GraphQLBizModel）、鉴权校验（GraphQLActionAuthChecker/GraphQLArgumentValidator）、web/ws/subscription/jsonrpc 全部文件、introspection（BuiltinSchemaLoader）、fetcher 目录全部文件；抽查 grpc 子模块（GrpcServer/GraphQLServerCallHandler/beans 装配）、orm 子模块（OrmFetcherBuilder 及 fetcher 签名语义）、message 子模块、CancelTokenManager/FlowControlFetcher/MetricsGraphQLHook/TccContextInvoker 等。`ast/_gen/` 生成代码仅核对 freeze/checkAllowChange 机制，未逐行审计（平台约定生成物不手改）。错误响应泄漏面已对照 nop-core ErrorMessageManager 的 onlyPublic 映射逻辑核实。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 3 |
| P2 | 5 |
| P3 | 6 |

## 发现列表

### [P1] 批量 Loader 的非 source 参数被首次调用冻结，后续同名字段参数被静默丢弃

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/fetcher/BeanMethodBatchFetcher.java:47-64`（loaderName 构造见 `io/nop/graphql/core/reflection/ReflectionBizModelBuilder.java:529-530`）
- **维度**: D1（正确性）/ D8（契约一致性）
- **证据**:
```java
DataLoader<Object, Object> loader = context.getDataLoader(loaderName);
if (loader == null) {
    Object[] args = new Object[argBuilders.size()];
    // 这里假定了除了source之外，其他的参数都相同
    for (int i = 0, n = args.length; i < n; i++) {
        if (i != sourceIndex)
            args[i] = argBuilders.get(i).apply(env);   // 仅取首次创建 loader 的 env 参数
    }
    BatchLoader<Object, Object> batchLoader = keys -> {
        args[sourceIndex] = keys;
        return FutureHelper.futureCall(() -> realFetcher.apply(args, context));
    };
    loader = DataLoaderFactory.newDataLoader(batchLoader);
    context.registerDataLoader(loaderName, loader);
}
return loader.load(env.getSource());
```
- **现状**: loaderName = `bizObjName + "@" + name`，不包含参数值。`@BizLoader` 批量方法允许声明额外的 `@Name` 参数（ReflectionBizModelBuilder 第 507-511 行），但当同一查询在两个分支以不同参数选择同一字段时（loader 名相同），第二个分支的参数不会被应用——整个批次都使用首次调用捕获的 `args`。
- **风险**: 同一请求内同名 loader 字段带不同过滤/分页参数时，第二处返回按第一处参数计算的数据，**静默错误数据**，无任何告警。注释表明是已知假设，但缺少运行期检测（如参数不一致时抛错或按参数分 loader）。
- **建议**: 将非 source 参数纳入 loaderKey（如 `loaderName + "@" + argsHash`），或在第二次 `get()` 时校验参数一致、不一致即抛 NopException。
- **误报排除**: 已确认 loaderName 不含参数（ReflectionBizModelBuilder:529）；已确认 DataLoader 按请求级 context 注册、跨请求无影响；单处使用或无额外参数的 loader 不受影响，故不升 P0。

### [P1] WebSocket 订阅完成/出错后 activeOperations 不清理，id 复用会直接断开整个连接

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/ws/JsonRpcWebSocketHandler.java:349-353`（只在 sendStreamingMessage 写入）、`:479-489`（onComplete 不移除）、`:473-476`（onError 不移除）、`:323-336`（重复 id 关闭会话）
- **维度**: D1（正确性）/ D2（资源管理）
- **证据**:
```java
private void sendStreamingMessage(String operationId, Flow.Publisher<ApiResponse<?>> stream) {
    SubscriptionSubscriber subscriber = new SubscriptionSubscriber(session, operationId);
    stream.subscribe(subscriber);
    activeOperations.put(operationId, subscriber);   // 只此一处 put
}
// SubscriptionSubscriber:
@Override
public void onComplete() {
    ...
    session.sendMessage(createCompleteMessage(operationId));   // 未从 activeOperations 移除
}
@Override
public void onError(Throwable t) {
    LOG.error("nop.websocket.error", t);                        // 未从 activeOperations 移除
}
// validSubscription:
if (activeOperations.containsKey(operationId)) {
    sendError(operationId, JsonRpcWebSocketErrorCodes.SUBSCRIPTION_EXISTS, ...);
    session.close((short) 4409, "Subscriber for " + operationId + " already exists");  // 关闭整个连接
```
- **现状**: `activeOperations` 只在 unsubscribe / 会话 onClose 时清理；订阅正常完成（onComplete）或失败（onError）后条目永久滞留。
- **风险**: (1) 客户端在收到 complete 后复用同一 operationId 重新订阅（graphql-ws 协议允许 id 复用）会触发 4409 关闭**整个 WebSocket 连接**；(2) 长连接会话反复订阅-完成后，滞留条目耗尽 `maxActiveOperations=1000`，新订阅全部被 TOO_MANY_SUBSCRIPTIONS 拒绝，直到断开重连。
- **建议**: onComplete/onError 中调用 `activeOperations.remove(operationId, this)`（注意用双参 remove 防误删并发重订阅的新条目）。
- **误报排除**: 已全文检索该类，确认无其他移除路径；onClose 的 forEach 清理只在连接关闭时执行，不覆盖本问题。

### [P1] GraphQLSubscriptionManager 的 beans.xml 装配三重损坏：永不加载 + 属性不存在 + start() 不会被调用

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/resources/_vfs/nop/graphql/beans/subscription-defaults.beans.xml:10-15`；对照 `io/nop/graphql/core/subscription/GraphQLSubscriptionManager.java`（仅有 setMessageService/setMaxActiveSubscriptions 两个 setter，start() 无 @PostConstruct）
- **维度**: D1（正确性）/ D7（平台规范：bean 必须在 beans.xml 显式定义且装配正确）
- **证据**:
```xml
<bean id="nopGraphqlSubscriptionManager" class="io.nop.graphql.core.subscription.GraphQLSubscriptionManager"
      ioc:type="@bean:id" ioc:default="true">
    <property name="graphQLEngine" ref="nopGraphQLEngine"/>   <!-- 类上无 setGraphQLEngine -->
    <property name="messageService" ref="nopMessageService"/>
    <property name="maxActiveSubscriptions" value="@cfg:nop.graphql.subscription.max-active|1000"/>
</bean>
```
- **现状**: 三项事实均已在仓库内核实：(1) 该 beans.xml 不在任何 `_vfs/nop/autoconfig/*.beans` 清单中，也不符合 `app*.beans.xml` 命名（AppBeanContainerLoader 仅加载这两类），且 `docs/ref/merged-app.beans.xml` 中不存在 nopGraphqlSubscriptionManager —— 即当前是**永不加载的死配置**，订阅消息路由管理器从未注册为 bean；(2) 一旦按其它 `*-defaults.beans.xml` 的惯例接入加载，`graphQLEngine` 属性在类上无对应 setter，NopIoC `BeanDefinitionBuilder.getSetter()` 会抛 ERR_IOC_UNKNOWN_BEAN_PROP，启动失败；(3) 即使修好属性，bean 未配置 init-method 且 `start()` 无 @PostConstruct 注解，消息主题订阅不会启动。
- **风险**: GraphQL 订阅推送桥接功能（IMessageService → WebSocket 客户端路由）整体不可用或一修即崩；后续开发者按惯例补 autoconfig 注册时会直接踩中启动失败。
- **建议**: 删除不存在的 `graphQLEngine` property；为 `start()` 添加 @PostConstruct（对照 GraphQLEngine.init 的写法）；在 `_vfs/nop/autoconfig/` 增加 graphql-subscription.beans 清单引用该文件，并补一条装配后的启动验证。
- **误报排除**: 已确认类中无 graphQLEngine 字段/方法（grep 无命中）；已确认全仓库无对该 beans.xml 的 import/autoconfig 引用；已确认其它模块（如 nop-orm、nop-message-core）的 `*-defaults.beans.xml` 均通过 autoconfig 清单注册，本文件是例外。

### [P2] fragment 预解析使 maxDepth 深度防护失效，可用 fragment 链构造超深查询

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/engine/GraphQLSelectionResolver.java:99-118`（预解析于 level-1）、`:217-250`（使用处早退）
- **维度**: D5（DoS 防护绕过）
- **证据**:
```java
public void resolveSelection(GraphQLDocument doc) {
    for (GraphQLDefinition def : doc.getDefinitions()) {
        if (def instanceof GraphQLFragment) {
            resolveFragment(doc, (GraphQLFragment) def, 0);   // 先于 operation，level=0
        }
    }
    ...
}
private void resolveFragment(GraphQLDocument doc, GraphQLFragment fragment, int level) {
    if (fragment.isResolved())
        return;                                                // operation 侧再引用时直接早退
    fragment.setResolved(true);
    resolveSelections(doc, fragment.getOnType(), fragment.getSelectionSet(),
            new HashMap<>(), level - 1);                       // body 从 level=-1 起算
}
```
- **现状**: 所有 fragment 在 operation 解析前以 level=-1 基线独立校验；operation 中引用 fragment 时（`resolveFragmentSelection` → `resolveFragment`）因 `isResolved()==true` 直接返回，**使用点深度不叠加到 fragment body 的深度检查**。fragment 嵌套引用其它 fragment 同样贡献 0 层。
- **风险**: `nop.graphql.query.max-depth`（默认 7）是平台唯一的查询深度防护；用 K 个链式 fragment（每个 body ≤ 7 层）可构造总深度 ≈ K×7 的查询，在递归 schema（树形实体）上导致执行期 `fetchNext→fetchSelections→fetchSelection` 深递归，直至 StackOverflowError / 长时间占用请求线程。受 `parse-max-length`（默认 4096）限制，可达深度约数百层，仍显著超出配置意图。
- **建议**: fragment 校验基线与使用点深度合并（resolve 时记录 fragment 被引用处的最小 level，或将展开后的等价深度纳入检查）；或在执行前对已解析 AST 做一次整体 `isExceedDepth(maxDepth)` 校验（AST 上已有该方法，GraphQLFieldSelection:41）。
- **误报排除**: 已通读 resolveSelection/resolveFragment/resolveFragmentSelection/resolveSelections 全链路确认无其他深度补偿逻辑；测试目录无 maxDepth 相关用例佐证预期行为；GraphQLDocumentParser 本身无嵌套深度限制，解析期不会拦截。

### [P2] JSON-RPC batch 请求单项失败导致整批失败，违背 per-entry 错误语义

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/jsonrpc/JsonRpcService.java:108-129`
- **维度**: D4（错误处理）/ D8（契约一致性）
- **证据**:
```java
for (JsonRpcRequest request : requests) {
    if (request.getId() == null) {
        executeCommandAsync(request, context);          // 见下一条发现
    } else {
        promises.add(executeCommandAsync(request, context));
    }
}
return FutureHelper.waitAll(promises).thenApply(r -> FutureHelper.getResults(promises));
// executeCommandAsync 内部：
IGraphQLExecutionContext gqlCtx = graphQLEngine.newRpcContext(null, request.getMethod(), req, context);  // 同步抛 NopException，未捕获
```
- **现状**: 两个失败路径均整批失败：(1) `newRpcContext`（initRpcContext）对未知参数/selection 校验失败会**同步抛出** NopException，batch 循环无 try-catch，整个 batchExecuteCommandAsync 同步抛出；(2) 任一 promise 异常完成时，`FutureHelper.getResults` → `syncGet` 在 thenApply 内抛出，整批 future 失败。
- **风险**: JSON-RPC 2.0 规范要求 batch 中每个 entry 独立返回成功或错误响应；当前实现一个坏 entry 使整批 500，合法 entry 的结果全部丢失。攻击者可用一个畸形 entry 拒绝服务同批所有请求。
- **建议**: 循环内对每个 request 包 try-catch 转为错误 response；`getResults` 改为逐项 `whenComplete` 收集（失败项落为 JsonRpcResponse error）。
- **误报排除**: 已确认 `executeRpcAsync` 自身不抛（toRpcResponse 内部 catch），失败源是 newRpcContext 同步异常与 executeCommandAsync 前 95-105 行之外的路径；95-97 行的 METHOD_NOT_FOUND 已正确处理为单项错误。

### [P2] JSON-RPC notification（id==null）请求 fire-and-forget，异常被静默吞噬

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/jsonrpc/JsonRpcService.java:119-125`
- **维度**: D4（异常吞噬）
- **证据**:
```java
if (request.getId() == null) {
    executeCommandAsync(request, context);   // 返回的 CompletionStage 被丢弃
} else {
    promises.add(executeCommandAsync(request, context));
}
```
- **现状**: notification 按规范无需响应，但其 future 未注册任何 `exceptionally`/`whenComplete`，执行失败（业务异常、鉴权失败）时无日志、无度量、无任何痕迹。
- **风险**: 通过 notification 触发的写操作失败完全不可观测，问题无法排查；也可能被用作无回执的静默探测。
- **建议**: 对丢弃的 future 追加 `whenComplete((r, e) -> { if (e != null) LOG.warn(...); })`。
- **误报排除**: 已确认 executeCommandAsync 全链路无内部兜底日志（executeRpcAsync 的 LOG 级别为 error 的仅 buildGraphQLResponse 路径，Rpc 路径是 logIfNotTraced 的 debug 语义），失败确实无输出。

### [P2] 多个 mutation 在同一请求中不去等待前一个完成，违背 GraphQL 串行 mutation 语义

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/engine/GraphQLExecutor.java:315-358`（`_invokeOperations`）
- **维度**: D8（契约一致性）
- **证据**:
```java
for (GraphQLSelection selection : selectionSet.getSelections()) {
    ...
    CompletionStage<OperationResult> future = invokeOperationOrTry(opEnv);  // 立即触发下一个 operation
    actions.add(() -> thenFetchNext(future.thenApply(...), opEnv)...);      // 仅推迟后处理
    FutureHelper.collectWaiting(future, promises);
}
```
- **现状**: 循环依次**发起**每个顶层 operation 的 fetcher，但不等待前一个 future 完成再发起下一个。同步 fetcher（普通 biz 方法）时副作用恰好有序；异步 fetcher（返回 CompletionStage 的 biz 方法、经 FlowControlFetcher 提交线程池）时，mutation N+1 的副作用先于 mutation N 落地是可能的。
- **风险**: GraphQL 规范要求 mutation 顶层字段串行执行（保证副作用顺序）；异步 mutation 下客户端观察到的执行顺序不确定，依赖顺序语义的批量变更（如先创建后更新）可能失败或作用于未就绪数据。
- **建议**: operation 类型为 mutation 时改为逐个 `thenCompose` 串联（等待前一个完成后再发起下一个）。
- **误报排除**: 已确认 `GraphQLTransactionOperationInvoker` 只是把整批 mutation 包进一个事务，不做顺序保证；`operationInvoker` 亦无串行化逻辑。

### [P2] DataLoader 首次注册的 check-then-act 竞争：并发分支可触发 ERR_GRAPHQL_DUPLICATED_LOADER 使请求失败

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/fetcher/BeanMethodBatchFetcher.java:49-63` + `io/nop/graphql/core/engine/GraphQLExecutionContext.java:142-148`
- **维度**: D3（并发与线程安全）
- **证据**:
```java
DataLoader<Object, Object> loader = context.getDataLoader(loaderName);   // (1) 读
if (loader == null) {
    ... loader = DataLoaderFactory.newDataLoader(batchLoader);
    context.registerDataLoader(loaderName, loader);                       // (2) 写
}
// registerDataLoader:
DataLoader old = loaders.put(loaderName, loader);
if (old != null && old != loader)
    throw new NopException(ERR_GRAPHQL_DUPLICATED_LOADER).param(ARG_LOADER_NAME, loaderName);
```
- **现状**: (1)(2) 之间无原子性。同一请求中两个异步分支（兄弟异步字段在不同完成线程上各自展开子 selection）同时首次命中同一 loaderName 时，双方都读到 null、都创建并 put，后 put 方抛 ERR_GRAPHQL_DUPLICATED_LOADER。
- **风险**: 特定并发时序下整个请求以重复 loader 异常失败，且难以复现；`putIfAbsent` 语义本可完全避免。
- **建议**: `registerDataLoader` 改为 `putIfAbsent`，冲突时复用已注册 loader（返回旧值），或返回 boolean 让调用方回退。
- **误报排除**: 已确认 `loaders` 为 ConcurrentHashMap 但 put+检查不是原子的；已确认 `_fetchSelections` 的异步 promise 在完成线程上继续展开嵌套字段（GraphQLExecutor:396-407 + thenFetchNext），两个兄弟异步字段确实可能在不同线程并发调用同一 batch fetcher。

### [P3] GraphQL 错误响应为 all-or-nothing 语义，无字段级 null 传播/部分成功

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/engine/GraphQLEngine.java:600-627`（buildGraphQLResponse）；`GraphQLExecutor.java:372-422`（异常直接沿 future 传播）
- **维度**: D8
- **证据**:
```java
if (err != null) {
    LOG.error("nop.graphql.execute-fail", err);
    ...
    ret.addError(errorBean);           // data 保持 null
} else { ... ret.setData(result); }
```
- **现状**: 任一 fetcher 异常使整个请求 future 失败，响应为 `{data: null, errors: [...]}`；不按 GraphQL 规范将错误隔离到出错字段、以 null 传播到最近可空父级并返回其余部分数据。
- **风险**: 大查询中一个非关键子字段失败导致全部数据丢弃；批量 operation 中一个失败使其它成功结果不可见。属平台级设计取舍，但对熟悉标准 GraphQL 语义的客户端是契约漂移。
- **建议**: 至少在文档中明示该语义；中期可在 `_fetchSelections` 层为字段级异常增加可配置的 null 传播策略。
- **误报排除**: 已确认 executor 各层（fetchSelection/_fetchSelections/invokeOperations）均无 try-catch 包裹单个字段异常，整链路为全量失败语义，非个别路径遗漏。

### [P3] 多处 bare IllegalArgumentException/IllegalStateException，违背错误处理两档策略

- **文件**:
  - `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/web/GraphQLWebService.java:81`（`throw new IllegalArgumentException("null request")`）
  - `nop-graphql-core/.../reflection/ReflectionBizModelBuilder.java:111`、`:264`（装配期 bare IllegalArgumentException）
  - `nop-graphql-core/.../engine/SelectionBeanBuilder.java:69`（`new IllegalArgumentException("nop.graphql.fragment-not-resolved:...")`）
  - `nop-graphql-core/.../engine/GraphQLExecutor.java:293`（`new IllegalStateException("nop.graphql.null-operation-fetcher:...")`）
  - `nop-graphql-core/.../parse/GraphQLDocumentParser.java:97`（`new IllegalStateException("invalid extend syntax")`）
- **维度**: D4 / D7
- **证据**:
```java
if (request == null)
    throw new IllegalArgumentException("null request");          // GraphQLWebService:81
throw new IllegalArgumentException(                              // SelectionBeanBuilder:69
        "nop.graphql.fragment-not-resolved:" + fragmentSelection.getFragmentName());
```
- **现状**: 消息采用 `nop.graphql.*` 错误码字符串风格，但用 bare JDK 异常抛出，无 ErrorCode、无 .param 上下文。
- **风险**: 违背“框架核心用 NopException + ErrorCode + .param(...)”的平台约定；经 ErrorMessageManager onlyPublic 映射后对客户端呈现为通用系统错误，丢失可定位信息，且日志中无结构化参数。
- **建议**: 统一替换为 `GraphQLErrors` 中已定义或新增的 ErrorCode + NopException。
- **误报排除**: 逐处确认均为可达路径（web 入参为空、装配期 bizObjName 为空、fragment 未解析、fetcher 缺失、extend 语法非法），非死代码。

### [P3] JSON-RPC 批量上限判断 off-by-one：恰好等于上限的批量被拒绝

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/jsonrpc/JsonRpcService.java:62`
- **维度**: D1
- **证据**:
```java
if (GraphQLConfigs.CFG_GRAPHQL_QUERY_MAX_OPERATION_COUNT.get() <= requests.size()) {
    // 默认 max=10 时，10 个请求的 batch 即被拒，只允许 <=9 个
```
- **现状**: 用 `<=` 而非 `<`。
- **风险**: 边界行为与配置描述（“单次查询所允许的操作个数”）不符，客户端按上限构造的合法批量被拒。
- **建议**: 改为 `<`。
- **误报排除**: 无其它归一化逻辑补偿该边界。

### [P3] batchExecuteCommandAsync 空批返回值违反声明的 List 类型，潜伏 ClassCastException

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/jsonrpc/JsonRpcService.java:113-116`
- **维度**: D1
- **证据**:
```java
public CompletionStage<List<JsonRpcResponse<?>>> batchExecuteCommandAsync(...) {
    if (requests.isEmpty()) {
        return FutureHelper.success(JsonRpcResponse.INVALID_REQUEST(null));  // 单对象塞进 List 泛型
```
- **现状**: `FutureHelper.success(Object)` 的签名绕过了泛型检查，future 实际携带单个 `JsonRpcResponse`。当前唯一调用方经 `JsonTool.stringify` 序列化恰好不炸（且单对象响应恰符合 JSON-RPC 空批规范）。
- **风险**: 任何新调用方按声明类型消费（如 `list.size()`/迭代）即 ClassCastException；类型契约与运行时值不一致是潜伏陷阱。
- **建议**: 返回 `List.of(JsonRpcResponse.INVALID_REQUEST(null))` 或将方法签名拆分。
- **误报排除**: 已确认 `FutureHelper.success` 签名为 `success(Object o)`（FutureHelper.java:79），编译期不拦截；已确认当前唯一消费路径不触发 CCE。

### [P3] GrpcServer 无条件注册 ProtoReflectionService，与 introspection 默认关闭的收紧语义不对齐

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-grpc/src/main/java/io/nop/graphql/grpc/server/GrpcServer.java:57-59`
- **维度**: D5（暴露面）
- **证据**:
```java
this.server = builder
        .addService(ProtoReflectionService.newInstance()) // 添加Proto Reflection服务
        .build();
```
- **现状**: GraphQL 侧 introspection 由 `nop.graphql.schema-introspection.enabled`（默认 false）收紧，而 gRPC 端口无条件暴露反射服务，可枚举全部服务与方法 schema。
- **风险**: gRPC 端口若可达（默认 9000），攻击者可免费获得完整 API 面（对象、方法、参数类型），抵消 introspection 默认关闭的意图；且使用的是已废弃的 ProtoReflectionService API。
- **建议**: 增加配置开关（如 `nop.grpc.server.reflection-enabled`，默认与生产环境策略对齐），并迁移到 ProtoReflectionServiceV1/.newBuilder。
- **误报排除**: 已确认无任何条件包裹该 addService；无其它 filter/拦截限制反射服务。

### [P3] GraphQL 路径对所有失败请求以 ERROR 级记录全栈，与 RPC 路径降噪逻辑不一致

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/engine/GraphQLEngine.java:604`（对照 `:633` 的 RPC 路径）
- **维度**: D4 / D6
- **证据**:
```java
// buildGraphQLResponse（GraphQL 路径）：
LOG.error("nop.graphql.execute-fail", err);
// buildRpcResponse（RPC 路径）：
NopException.logIfNotTraced(LOG, "nop.graphql.rest-execute-fail", err);
```
- **现状**: 预期的业务校验/鉴权失败（NopException）在 GraphQL 路径也打 ERROR 级完整堆栈；RPC 路径则用 logIfNotTraced 避免重复与降噪。
- **风险**: 高频失败（如未授权扫描）会刷爆错误日志、放大 IO 开销，并淹没真正的系统级故障信号。
- **建议**: 对齐 RPC 路径使用 `NopException.logIfNotTraced`。
- **误报排除**: 已确认两路径相邻实现确实不一致；GraphQL 路径无其它去重机制。

### [P3] 解析器不支持 inline fragment（`... on Type { ... }`），报错信息误导

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/parse/GraphQLDocumentParser.java:716-733`
- **维度**: D8
- **证据**:
```java
private GraphQLSelection selection(TextScanner sc) {
    skipComments(sc);
    if (sc.tryMatch("...")) {
        return fragmentSelection(sc);       // fragmentSelection 直接 nextGraphQLVar 读作 fragment 名
    }
    ...
}
private GraphQLSelection fragmentSelection(TextScanner sc) {
    ...
    String fragmentName = sc.nextGraphQLVar();   // "... on X {...}" 中 "on" 被当作 fragment 名
```
- **现状**: 不支持 GraphQL 规范的 inline fragment 与类型条件；`... on Type` 会被误解析（"on" 当作 fragment 名，后续 `{` 触发扫描异常），产生与根因无关的解析错误信息。
- **风险**: 使用标准客户端生成 inline fragment（接口/联合类型查询的常规手段）的请求得到误导性错误；仅命名 fragment 可用是未声明的方言限制。
- **建议**: 短期在 fragmentSelection 中识别 `on` 关键字并抛出带明确错误码的“不支持 inline fragment”NopException；长期实现 inline fragment 支持。
- **误报排除**: 已通读解析器确认无 inline fragment 分支；顶层 fragment 选择在 resolveOperation 会被 ERR_GRAPHQL_UNSUPPORTED_AST 拒绝，但该拒绝不覆盖 inline fragment 误解析路径。

## 已排查并排除的疑似点（供复核）

- `GraphQLScalarType` 静态 HashMap（stdMap/textMap/typeMap）：仅在静态块写入，之后只读，线程安全，非问题。
- `GraphQLExecutor._invokeOperations` 对顶层 selection 的直接强转 `(GraphQLFieldSelection)`：受 `GraphQLSelectionResolver.resolveOperation` 的 ERR_GRAPHQL_UNSUPPORTED_AST 保护（所有执行路径先经 resolveSelections），不可达 CCE。
- `GraphQLExecutor.resultLock` 共享锁：executor 每请求新建（GraphQLEngine.newGraphQLExecutor），无跨请求争用。
- 缓存的冻结 GraphQLDocument 共享：freeze(true) 后生成 setter 均 checkAllowChange；GraphQL 查询路径无对缓存 AST 的写操作（opRequest 仅在 RPC 新建 doc 上设置）。
- `OrmFetcherBuilder.getConnectionFetcher` 中 `getProp(connectionProp, false)`：`ignoreUnknown=false` 为 requireProp 语义（不存在即抛 NopException），非 NPE。
- 错误响应信息泄漏：`buildErrorMessage(locale, err, false, true)` 的 onlyPublic=true 会将无映射/内部错误替换为通用系统错误（ErrorMessageManager.applyMapping），默认不泄漏内部细节。
- introspection 暴露：默认关闭（CFG_GRAPHQL_SCHEMA_INTROSPECTION_ENABLED=false），`__` 前缀操作在关闭时显式拒绝。
- 查询文本长度与 directive 数量均有上限（parse-max-length=4096、max-directive-per-request=20），与 maxDepth 共同构成入口限流。
