# C3 异步链路正确性审计报告（第 1 轮初审，盲审）

## 审计单元

- **维度**：C3——异步链路正确性（异步任务链的异常传播/超时/取消语义；future 转换链断裂；异步回调的上下文传播；订阅/流式链的生命周期）
- **审计对象**：请求热路径五模块（8 源码根）：nop-graphql（core/orm/message/grpc）、nop-biz、nop-gateway、nop-biz-auth-core、nop-biz-file-core
- **仓库**：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`）
- **基线**：`_tmp/cra-baseline-nop-service-framework-hotpath.txt`（C3 节 async 链站点 + C4 节定时任务站点）
- **全局 ID 前缀**：`nop-service-framework-hotpath-C3-*`；维度集外线索 `CL-007` 起（CL-001~006 已被前轮占用）

## 盲审纪律声明

本报告为盲审轮次产出。审计过程中未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件；仅阅读了允许的方法论文件 `ai-dev/skills/concurrency-resource-audit-prompt.md`、当前 live code、`nop-api-core`/`nop-commons`/`nop-core`/`nop-rpc-api`/`nop-orm`/`LocalMessageService` 中被审计代码直接依赖的语义对照点（FutureHelper、ContextProvider、BaseContextProvider、IContext、ExecutorHelper、TccContext、ContextBinder、OrmSessionRegistry/OrmTemplateImpl、java-dataloader 3.2.2 字节码签名）。所有发现以当前 live code 实测为准。预注的 C1 轮 8 条、C2 轮 6 条仅按任务说明中的编号对照（C2-001 dispatchAll 持锁、C2-002 BufferedStreamingPublisher 锁内 fetch、CL-001 keepAlive 滞留），未展开复核其内容。

## 检查范围说明

### 一、异步入口清单（C3 执行步骤 1 产出）

| # | 异步入口 | 文件 | 类型 |
|---|---------|------|------|
| A1 | GraphQL 请求执行链（executeOneAsync/executeAsync/fetchResult） | `GraphQLExecutor` | CompletableFuture 链 + DataLoader dispatch 泵 |
| A2 | DataLoader 批量加载链 | `GraphQLExecutionContext.dispatchAll` + `OrmBatchLoader` | 显式 dispatch + 递归泵 |
| A3 | GraphQL 订阅链（subscribeGraphQL/subscribeRpc） | `GraphQLEngine` + `GraphQL/RpcSubscriptionPublisher` | Flow.Publisher |
| A4 | WebSocket JSON-RPC 订阅链 | `JsonRpcWebSocketHandler` | Flow 订阅 + 定时 keepAlive |
| A5 | 消息桥订阅管理 | `GraphQLSubscriptionManager` | IMessageService 消费回调 |
| A6 | 消息→GraphQL 派发 | `GraphQLMessageSubscriptionRegistrar` | 消息消费回调 |
| A7 | 网关普通转发链 | `GatewayHandler`/`RouteExecutor`/`InvokeProcessor`/`ForwardProcessor` | CompletableFuture 链 + 重试 |
| A8 | 网关流式链 | `StreamingProcessor`/`BufferedStreamingPublisher`/`GatewayHttpFilter` | Flow.Publisher + 缓冲/重订阅 |
| A9 | AI failover 链 | `AiFailoverGatewayInterceptor` | CompletableFuture fallback 链 |
| A10 | 异步 biz action 执行 | `BizActionService.callActionAsync` + `BizActionInvocation` | executor.execute + future 桥 |
| A11 | 操作级 invoker 装饰链 | `TccContextInvoker`/`GraphQLTransactionOperationInvoker`/`GraphQLFlowControlInvoker`/`DefaultExecutionFunctionInvoker`/`CancelTokenManager.wrap` | IAsyncFunctionInvoker 链 |
| A12 | 认证过滤链 | `AuthHttpServerFilter` | executeBlocking + runOnContext + future 桥 |
| A13 | maker-checker / cache 装饰链 | `MakerCheckerTryServiceAction`/`CacheActionDecorator`/`CacheEvictActionDecorator` | thenCompose/thenApply/thenRun |
| A14 | gRPC 服务派发 | `GraphQLServerCallHandler` + `GrpcServer` | grpc 回调 → executeRpcAsync |
| A15 | 文件上传下载 | `AbstractGraphQLFileService`/`IFileStore` | executeRpcAsync 桥 |
| A16 | 临时文件延迟删除 | `DownloadHelper` | globalTimer 一次性 schedule |
| A17 | RPC 代理 | `RpcServiceOnGraphQL` | thenCompleteAsync 桥 |

### 二、异步上下文传播矩阵（全部「切线程执行用户代码」站点）

依据：`BaseContextProvider.getOrCreateContext()` 在无附着 context 的线程上会**创建新 context 并永久附着**（`newContext(true)` → `t_context.set`）；`ExecutorHelper.submit/schedule` 的 finally `detachContext()` 是平台对池线程的清理约定。判定口径：✓=显式传播；✗=缺失；△=条件成立/部分传播。

| # | 站点 | 切线程方式 | IContext 传播 | 判定 |
|---|------|-----------|--------------|------|
| M1 | `GraphQLExecutor.thenFetchNext`（:152） | thenOnContext 显式挂接 | `ContextProvider.thenOnContext(future, env.getContext())` | ✓ |
| M2 | `GraphQLExecutor.invokeOperation` 出口泵（:135-147） | thenCompose 于完成线程 | 无 thenOnContext | **✗ → C3-002** |
| M3 | `GraphQLExecutor.dispatchAll` 递归泵（:200-205） | thenAccept 于完成线程 | 无 | **✗ → C3-001/002** |
| M4 | `GraphQLEngine.executeRpcAsync/executeGraphQLAsync`（:572/:615） | 初始同步段 | `invokeWithBindingCtx` 绑定 ATTR_SERVICE_CONTEXT | ✓（初始段） |
| M5 | HTTP GraphQL 入口（`GraphQLWebService.runGraphQL/runRest`） | 请求线程 | `ContextBinder`（tenant/locale/expireTime 落线程上下文） | ✓ |
| M6 | gRPC 入口（`GraphQLServerCallHandler.onHalfClose`） | grpc 回调线程 | `newRpcContext` 无 binder | **✗ → C3-012** |
| M7 | WS 订阅入口（`JsonRpcWebSocketEndpoint.executionService`） | WS 接收线程 | `newRpcContext` 无 binder | **✗ → C3-012** |
| M8 | 消息派发（`GraphQLMessageSubscriptionRegistrar.onMessage`） | 消费线程 | `newRpcContext` 无 binder；LocalMessageService 以 `getResult` 同步等待（`LocalMessageService.java:216`） | △/✗ → C3-012 |
| M9 | 文件入口（`AbstractGraphQLFileService.uploadAsync/downloadAsync`） | 调用线程 | 无自有 binder；若上游 HTTP filter 已附着则继承 | △ |
| M10 | `BizActionService.callActionAsync`（:74-91） | `executor.execute(task)` 裸提交 | 无传播；worker 线程 `getOrCreateContext` 附着新 context 且**永不 detach** | **✗ → C3-007** |
| M11 | `ExecutorHelper.submit/schedule`（:128-199） | 池线程 | 任务 finally `detachContext()` | ✓（平台约定，未覆盖裸 `execute`） |
| M12 | `InvokeProcessor.invokeUrlWithRetry` 重试延续（:156/:164） | delayedExecutor（commonPool）+ 完成线程 | 无 | ✗（C3-010 关联） |
| M13 | `AiFailoverGatewayInterceptor` fallback 延续（:126-144） | 完成线程 | 无 | ✗（同上） |
| M14 | `AuthHttpServerFilter.handleUserContext`（:362-380） | `ctx.runOnContext` | 绑定请求 context 执行队列 | ✓ |
| M15 | `TccContextInvoker.invokeAsync`（:29-38） | thenOnContext | 依赖调用线程已附着请求 context | △ → C3-008 |
| M16 | `CacheEvictActionDecorator` thenRun 回调 | 完成线程 | 回调内使用调用方 `ctx.getEvalScope()` 求值 cacheKey | △ → CL-009 |
| M17 | `MakerCheckerTryServiceAction.sendForCheck`（:67） | thenCompose 完成线程 | context 经参数显式传递 | ✓ |
| M18 | `DownloadHelper` 定时删除（:39-41） | globalTimer 线程 | 无需 context | ✗ 无害 → CL-007 |
| M19 | `JsonRpcWebSocketHandler.sendKeepAlive`（:82） | globalTimer 线程 | 无需 context | ✗ 无害（活性问题见 C3-006） |
| M20 | `FlowControlFetcher`/`GraphQLFlowControlInvoker`（FlowControl 门控） | 同线程门控（`DefaultFlowControlRunner.runAsync` 仅 tryAcquire 后就地 `task.get()`） | 无切线程 | ✓ |
| M21 | `GraphQLEngine.subscribeGraphQL/subscribeRpc` 的 fetcher.get(env)（:751/:814） | 调用线程同步执行 | 调用线程上下文 | ✓（初始段） |
| M22 | `FutureHelper.runOnExecutor` / `bindCancelToken`（api-core 对照） | executor 线程 | 不传播（api-core 越界仅对照，不立案） | — |

**结论**：热路径上共有 4 个 ✗ 站点集群（M2/M3、M6/M7/M8、M10、M12/M13），其中 M10（biz 异步 action）是唯一「平台已有清理约定但被绕过」的站点；M2/M3 是唯一同时影响 DataLoader 与 ORM 会话亲和的站点。

### 三、判级口径说明

按任务要求：「请求热路径被高并发调用」为本轮默认语境；凡缺陷触发依赖「特定 resolver/biz 方法配置/特定注入扩展点」的，按条件化降档执行（P1→P2 或标注条件）。P0=默认用法+常见条件即触发且不可逆——本轮未发现满足 P0 的异步链缺陷（所有发现均可在请求结束/连接关闭后回收或自愈，无不可逆数据损坏链）。

---

## 发现条目

### [C3-001] DataLoader dispatch 递归泵在任一批次失败后永久断链且异常被吞，并发窗口内新注册的 loader 永不调度导致请求悬挂

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/engine/GraphQLExecutor.java:200-205`（配合 `GraphQLExecutionContext.java:155-170`）
- **证据片段**:
  ```java
  // GraphQLExecutor.java
  void dispatchAll(IGraphQLExecutionContext context) {
      CompletionStage<Void> promise = context.dispatchAll();
      if (promise != null) {
          promise.thenAccept(v -> this.dispatchAll(context));
      }
  }
  // GraphQLExecutionContext.java —— promise = waitAll(loader.dispatch() futures)
  List<CompletionStage<?>> futures = new ArrayList<>();
  for (Map.Entry<String, DataLoader> entry : loaders.entrySet()) {
      futures.add(loader.dispatch());
  }
  return FutureHelper.waitAll(futures);
  ```
- **严重程度**: P1
- **并发场景**: HTTP worker/context 队列线程执行游走回合；数据库回调线程或 RPC 响应线程完成批次 future；并行 query 分支在其他线程注册新 loader。
- **触发时序**: T1（context 队列线程）字段 A 的 fetcher 调 `loader1.load(k1)` 注册 loader1，回合出口泵执行 `dispatchAll`：快照并清空注册表后 `loader1.dispatch()` 启动批处理，返回批次 future F1，`promise.thenAccept(递归)` 挂在 `waitAll([F1])` 上。T2（DB 回调线程）F1 以异常完成（批量加载失败）→ `waitAll` 聚合 promise 异常完成 → `thenAccept` 的递归延续**被跳过**，泵链死亡；该异常落入被丢弃的依赖 stage，无 exceptionally/handle，**无任何日志**。T3（并行分支线程，在 F1 批处理执行期间）字段 B 的 fetcher 注册 loader2 并 `load(k2)`——注册表非空但已无任何存活泵（初始泵已过、回合出口泵位于 `thenFetchNext` 的 thenCompose 内、而该链正等待永不完成的 k2 future）。后果：loader2 的 load future 永不完成 → 字段 B 的 `waitAll` 永不完成 → `executeAsync` 返回的响应 future 永不完成 → 请求悬挂至 HTTP 容器超时（若容器无超时则永久占用连接与 context 队列）。
- **现状**: 递归泵只处理了「成功完成」这一种延续；失败路径同时丢失「继续泵」与「异常可见性」两个语义。java-dataloader 3.2.2 的 `load()` 仅入队不自动 dispatch（显式 dispatch 模式），因此新注册 loader 的调度完全依赖本泵链。
- **风险**: 批量加载失败 + 失败窗口内新注册的组合一旦出现即产生无日志悬挂请求；高并发下悬挂请求堆积占用连接池与 context 执行队列，放大为服务不可用。失败批次本身的错误仍可通过字段级 load future 传播（字段 A 报错），故单个失败可见，但**其余字段的悬挂掩盖了根因**。
- **建议**: 递归泵改为 `promise.whenComplete((v, err) -> { if (err != null) LOG.error(...) ; this.dispatchAll(context); })`——失败时也继续尝试泵（`context.dispatchAll()` 对空注册表返回 null 自然终止）并记录批次失败；或以 `handle` 保持链存活。同时考虑给 loader dispatch 聚合失败增加挂接到响应 future 的错误通道。
- **失效面枚举**: dispatch 泵的全部调用点：`GraphQLExecutor.executeOneAsync:98`（初始，同步段后——同缺陷）、`fetchResult:120`（同步段后）、`executeAsync:189`（初始）、`invokeOperation:145`（回合出口）、`thenFetchNext:156`（回合出口）——五个调用点共用同一条递归泵实现，**全部同族**；`GraphQLSubscriptionManager` 不使用 DataLoader，无同族。修复必须覆盖上述 5 处共用的 `dispatchAll(IGraphQLExecutionContext)` 单点。
- **信心水平**: 确定
- **误报排除**: 不属于「原语使用不是问题」——这是对 `thenAccept`  exceptional-completion 语义与被丢弃 stage 的复合误用，且写出了具体交错序列（T1/T2/T3），非「看到共享字段报线程不安全」。
- **复核状态**: 已复核（独立复核 2026-10-18）——**采纳改判，降级 P1→P3**。二次字节码反证独立复验成立：本复核从 `~/.m2/.../java-dataloader-3.2.2.jar` 全新解包（`_tmp/c3-review-dl/`）以 `javap -p -c` 独立反汇编——(1) `invokeLoader(List,List)` 异常表实测 `from 0 to 58 target 61 type java/lang/Exception`，方法体全程捕获 → `CompletableFutureKit.failedFuture`（offset 61-68），第 2 轮「同步抛出穿透 invokeListBatchLoader→dispatch() 传播出 for 循环」被推翻；(2) `lambda$dispatchQueueBatch$3`（offset 0-98）逐 key `completeExceptionally`+`clear` 后 `Collections.emptyList()` 正常返回（offset 95-98），批失败时 dispatch future 恒正常完成；(3) `FutureHelper.waitAll:389-413` = `CompletableFuture.allOf`，输入恒正常完成 → 聚合 promise 恒正常完成 → `thenAccept` 递归泵存活。原 P1 触发时序（T2 批失败断链→T3 悬挂）在 3.2.2 下不存在。残余缺陷=观测性（批级聚合无日志，但字段级错误经逐 key completeExceptionally 正常可见 + `incrementBatchLoadExceptionCount` 计数递增）+ 泵正确性隐式依赖 3.2.2 内部语义无守卫声明——按 P3 诊断性加固处置（`:203` thenAccept 改 whenComplete+日志+语义依赖注释；逐 loader 异常隔离为顺手加固非缺陷修复）。现状：新发现/未修复。

### [C3-002] invokeOperation 回合出口泵未经 thenOnContext 挂接，DataLoader dispatch 与 OrmBatchLoader 批量加载在「future 完成线程」上执行，脱离请求 IContext/ORM 会话

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/engine/GraphQLExecutor.java:135-147`（对照 `:150-159`）；`nop-service-framework/nop-graphql/nop-graphql-orm/src/main/java/io/nop/graphql/orm/fetcher/OrmBatchLoader.java:29-37`
- **证据片段**:
  ```java
  // GraphQLExecutor.invokeOperation —— 出口泵，未挂 context
  return future.thenCompose(opResult -> {
      ...
      CompletionStage<Object> ret = FutureHelper.toCompletionStage(fetchNext(opResult.getValue(), env));
      dispatchAll(env.getGraphQLExecutionContext());   // 在完成线程上执行
      return ret;
  });
  // 对照 thenFetchNext —— 有挂接
  return ContextProvider.thenOnContext(future, env.getContext()).thenCompose(v -> { ... });
  // OrmBatchLoader.load —— dispatch 同步执行的 DB 工作
  public CompletionStage<List<Object>> load(List<Supplier<Object>> keys) {
      ormTemplate.requireSession().getBatchLoadQueue().flush();
  ```
- **严重程度**: P1
- **并发场景**: 异步 fetcher（executor 配置的异步 biz action 经 `BizActionService` 在工作线程完成；或远程 RPC fetcher 在 HTTP 客户端 IO 线程完成）的完成线程执行 `thenCompose` 回调 → `dispatchAll` → `loader.dispatch()` **同步**执行 `OrmBatchLoader.load`（java-dataloader 3.2.2 dispatch 在调用线程跑 batchLoader）。
- **触发时序**: T1（请求线程）顶层字段为远程/异步 fetcher，返回 future；顶层操作完成后嵌套字段走 `thenFetchNext`（有挂接，回到请求 context），但 `invokeOperation` 的**顶层出口泵**（:135-147）直接 `thenCompose` 于完成线程。T2（biz worker 线程 W，经 callActionAsync 路径）操作 future 完成 → 泵回调在 W 上执行 `dispatchAll` → `OrmBatchLoader.load` 在 W 上执行 `ormTemplate.requireSession()` → `OrmSessionRegistry.instance()`（`OrmSessionRegistry.java:19-20`：`ContextProvider.getOrCreateContext()`）取到的是 **W 线程附着 context 的注册表**，而非请求 context 的注册表。两种后果（按 W 线程状态二选一）：(a) W 无会话 → `requireSession` 抛 `ERR_ORM_NOT_IN_SESSION`（`OrmTemplateImpl.java:181-186`）→ 批量加载整体失败，嵌套字段全部报错；(b) W 恰好残留上一个任务的会话/context（见 C3-007 的附着泄漏）→ 批量加载在**错误的 ORM 会话**中执行，脏状态分裂在两个会话间。
- **现状**: 同文件内 `thenFetchNext` 已用 thenOnContext 保证「所有 fetcher 都在同一个异步 context 上执行」（:151 注释），但 `invokeOperation` 顶层出口泵与递归泵遗漏同一处理，形成线程亲和漏洞。
- **风险**: 「异步 biz action + 嵌套 ORM 关系字段」组合（生产常见：@Async biz 方法 + GraphQL 关联查询）间歇性报 `not in session` 或加载到错误会话数据；配合 C3-007 的池线程 context 附着，失败模式不确定、难复现。
- **建议**: `invokeOperation` 出口泵与 `GraphQLExecutor.dispatchAll` 递归泵统一包 `ContextProvider.thenOnContext(..., env.getContext())`，与 `thenFetchNext` 对齐；或在 `OrmBatchLoader.makeDataLoader` 处将 batchLoader 包装为显式携带 `IGraphQLExecutionContext`、在 `load` 内用 `context.getServiceContext().executeWithContext(...)` 执行。
- **失效面枚举**: 同族未挂接点：`GraphQLExecutor.invokeOperation:135`（顶层出口泵）、`GraphQLExecutor.dispatchAll:203`（递归泵）——两处；已正确挂接的兄弟：`thenFetchNext:152`、`GraphQLExecutor.fetchResult:116-121`（同步段，无切线程，豁免）、`executeOneAsync:98`/`executeAsync:189`（初始泵，同步段后同线程，豁免）。`OrmBatchLoader` 是当前唯一 BatchLoader 实现（`nop-graphql-orm` 全目录核对），失效面收敛。
- **信心水平**: 很可能（对异步 fetcher + ORM 批量加载组合的触发链确定为真；对具体线程归宿依赖部署的 invoker 装配）
- **误报排除**: 非通用「异步回调上下文可能丢」的泛化担忧——给出了与同文件 `thenFetchNext` 的不一致证据和 ORM 侧具体故障点（requireSession 抛错/错会话），触发时序具体。
- **复核状态**: 已复核（独立复核 2026-10-18）——**维持 P1**。不一致直接在码：`:135-147` 出口泵与 `:203` 递归泵无挂接、`:151-156` thenFetchNext 有挂接（live 实测）。复核补强两点：(1) 默认 invoker 链实测 = `ChainedAsyncFunctionInvoker(GraphQLAuditOperationInvoker, GraphQLTransactionOperationInvoker)`（biz-defaults.beans.xml:27-35），两装饰器均无 thenOnContext 恢复（GraphQLTransactionOperationInvoker:27-34 直通/委托），TccContextInvoker 仅 TCC 操作条件装饰——初审「线程归宿依赖 invoker 装配」的保留意见已被消除，异步顶层操作 future 于 worker 线程完成后**无任何层拉回请求 context**；(2) 失效面比初审更宽——`invokeOperation.thenCompose` 内 `fetchNext(opResult.getValue(), env)` 是**同步调用**，首轮嵌套字段游走整体发生在完成线程（非仅 OrmBatchLoader），`OrmEntityRefFetcher:33` 的 `ormTemplate.requireSession()` 同样按线程上下文取会话（OrmTemplateImpl:181-186 ERR_ORM_NOT_IN_SESSION / C3-007 残留会话）。触发组合（异步 biz action + ORM 嵌套字段）为平台一等公民路径、无罕见条件合取。现状：新发现/未修复。

### [C3-003] BufferedStreamingPublisher 背压信号断裂：越窗后下游 demand==0 时元素进入无界缓冲，上游自驱动持续泵入，慢客户端长流累积致内存失控

- **文件**: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/executor/BufferedStreamingPublisher.java:260-266`（配合 `StreamingProcessor.java:137-193`、`GatewayHttpFilter.java:200-234`）
- **证据片段**:
  ```java
  // BufferedStreamingPublisher.handleItem
  if (demand > 0) {
      demand--;
      state.forwarded = true;
      subscriber.onNext(item);
  } else {
      state.buffer.add(item);      // 越窗后无界 ArrayDeque
  }
  // StreamingProcessor.createMappedPublisher —— 上游自驱动，与下游 demand 无关
  subscriber.onNext(element);
  subscription.request(1);         // 每元素无条件再请求
  ```
- **严重程度**: P1
- **并发场景**: HTTP 上游（LLM SSE 流）的 IO 线程持续回调 `onNext`；下游 `sendStreamingResponse` 订阅者按写盘/网络速度发 request(n)。两速度解耦时元素滞留于 `Attempt.buffer`。
- **触发时序**: T1 缓冲层 engage（`bufferEnabled=true` 或存在 lifecycle/retry 回调——`StreamingProcessor.java:89-93`）。首段 N 元素/T 毫秒窗口越过后进入透传态（`passed=true`）。T2 客户端读取变慢或暂停（移动端后台、慢代理），下游 demand 耗尽（`demand==0`）。T3 上游映射链不门控：`createMappedPublisher` 的 onNext 处理完一个元素后无条件 `subscription.request(1)` 继续拉取 HTTP 流 → `handleItem` 每个元素走 `state.buffer.add(item)` 分支。整条链上唯一的有界构造是首段窗口（bufferSize 个元素）；透传态缓冲无任何上界。T4 客户端完全断连才由 `GatewayHttpFilter.java:239-241` 的 `future.whenComplete → abort()` 终止；**慢而不断**的客户端不触发 abort，缓冲随流长度线性增长。30 分钟长流 + 空闲客户端 = 整流内容驻留堆内（每元素还经 `JSON.parse`/`JsonTool.serialize` 放大），并发流式请求数 × 流大小叠加可致 OOM。
- **现状**: `pendingComplete` 机制正确处理了「上游完成但尾部未交付」，但「上游未完成、下游无 demand」的持续态没有任何反压：demand 未连接到上游 request。
- **风险**: 声明规模包络内（正常流式转发）在负载上升/客户端变慢时必然触发的内存累积；OOM 或长 GC 停顿。类注释自述「下游 demand 门控」，实现仅对**转发**门控、未对**拉取**门控，声明与实现不一致。
- **建议**: 在 `createMappedPublisher` 内改为 demand 门控：仅当 `BufferedSubscription` 有 demand 时才 `subscription.request(1)`（例如映射层持有缓冲层的 `tryRequest()` 回调）；或给 `Attempt.buffer` 加容量上限，超限即向上游 `upSub.cancel()` 并断流（fail-fast 而非无限缓冲）。
- **失效面枚举**: 背压断裂面：`BufferedStreamingPublisher.handleItem:265`（透传态缓冲）、`flushBuffer:273-277`/`flushAll:284-288`（循环内 `subscriber.onNext` 在持 monitor 下执行——下游慢时 `cancel()`（:184-203）需等 monitor，取消延迟同根因，次要面）；同族 publisher：无缓冲直通路径（`StreamingProcessor.java:99-106` bufferEngaged=false）映射链同样自驱动，但直通态 `subscriber.onNext` 直达最终订阅者，由服务器适配层自身缓冲吸收（vertx 等有写缓冲上限），风险低于缓冲态——标注同族差异。`GatewayHttpFilter.stringPublisher.onNext:213-221` 串行化层无独立缓冲，不放大。
- **信心水平**: 确定（代码路径直接可证；量化放大链：元素到达速率 × 停留时长 × 序列化体积）
- **误报排除**: 非「有界声明存在的缓存」误报类——透传态缓冲无任何 CacheConfig/容量声明；也非 C5 维度的「声明包络外大数据量」——这是每请求热路径上的结构性背压缺失。
- **复核状态**: 已复核（独立复核 2026-10-18）——**维持 P1**。全部证据逐行证实：`handleItem:260-266` demand==0 → `state.buffer.add`（Attempt.buffer 为 ArrayDeque :119，无上界）；`createMappedPublisher` onSubscribe `:144` 与 onNext 尾部 `:162` 无条件 `subscription.request(1)` 自驱动；第 2 轮 F3 补充（onNext catch `:164-166` 转 onError 后不 `subscription.cancel()`，上游 HTTP/SSE 连接无人回收）复核确认。**复核补强（可达性上调）**：初审「缓冲层 engage 需 bufferEnabled 或回调注入」的条件在生产已被满足——nop-ai-gateway（审计 8 根之外、AI 网关生产链）`AiGatewayFailoverInterceptor:208` 向 `ATTR_RETRY_CALLBACK` 注入 `GatewayStreamingRetryCallback`，`bufferEngaged=true` 使缓冲路径成为 AI 流式路由的默认形态，慢客户端 × 长流的内存累积面为出厂可达。维持 P1。现状：新发现/未修复。

### [C3-004] BufferedStreamingPublisher 重订阅链无尝试上限、无退避，同步失败路径形成无界递归热循环（retryCallback 扩展点无框架守卫）

- **文件**: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/executor/BufferedStreamingPublisher.java:298-326, 220-237`
- **证据片段**:
  ```java
  private void handleError(Attempt state, Throwable throwable) {
      boolean inWindow = !state.forwarded;
      if (inWindow && retryCallback != null) {
          state.buffer.clear();
          ...
          if (newRequest != null) {
              Flow.Subscription oldSub = state.upSub;
              if (oldSub != null) { oldSub.cancel(); }
              attempt = null;
              ...
              startAttempt(newRequest);   // 无尝试计数、无退避、同步递归
              return;
          }
      }
      terminate(throwable);
  }
  ```
- **严重程度**: P2（条件化：当前仓库主代码内无生产 `IStreamingRetryCallback` 实现注入点——仅测试代码构造，拦截器注入属部署期扩展）
- **并发场景**: 上游错误回调线程执行 `AttemptSubscriber.onError` → `handleError` → `startAttempt`，全程持 `BufferedSubscription` monitor。
- **触发时序**: T1 拦截器注入「恒返回非 null 新 HttpRequest」的重试回调（平台未提供任何上限约束或示例守卫）。T2 上游 `fetchServerEventFlow` **同步**抛异常（连接拒绝、URL 非法等——`startAttempt` 的 catch 直接调 `handleError`）→ `handleError` → `startAttempt` 在**同一调用栈**上递归 → 每次迭代一次 HTTP fetch + 一次回调 consult，同步失败时栈深线性增长直至 `StackOverflowError`；异步失败时退化为无间隔热循环，对故障上游形成请求风暴（对照 C7 重试风暴族）。T3 同时每轮 `lifecycle.onStreamTerminated(throwable)` + 新 attempt 的 `onFetchStarted()`，生命周期计数被无限刷写。
- **现状**: `InvokeProcessor.invokeUrlWithRetry`（普通转发）有 `maxRetries=3` + 指数退避 + `MAX_RETRY_DELAY_MS` 钳制；同模块流式重订阅层完全没有对齐该治理口径，扩展点契约（类注释 §「窗口内失败 → 经回调重执行」）未声明任何界限。
- **风险**: 接入该扩展点的拦截器一旦决策逻辑有缺陷（恒重试），单请求即打满 CPU/连接并对上游发起无界请求风暴；同步抛错路径叠加 SOE 直接击穿工作线程。
- **建议**: `Attempt` 增加 `index` 已有——在 `handleError` 中加 `maxAttempts` 钳制（对照 `GatewayStreamingModel` 暴露配置，默认 1~3）与固定/指数退避（可复用 `CompletableFuture.delayedExecutor`，同 `InvokeProcessor.delayedFuture` 模式，但需注意退避期间释放 monitor——当前同步递归在锁内）。
- **失效面枚举**: 重试/重订阅治理全家族清单：`InvokeProcessor.invokeUrlWithRetry:139-196`（有界 3 次+退避 ✓）、`AiFailoverGatewayInterceptor.tryFallbackUrl:111-144`（有界 `maxFallbackAttempts` ✓ 但**无退避**，同族次缺陷随 C3-010 登记）、`BufferedStreamingPublisher.handleError:298`（**无界** ✗ 本条）。三处共用「上游失败 → 重发」语义，治理标准不一致。
- **信心水平**: 确定（机制确定；生产触发依赖扩展点接入，按判级口径条件化降档）
- **误报排除**: 非「测试专用代码简化并发模型」——被审对象是生产 publisher 的扩展点契约本身；也非低频路径——流式 AI 网关路由是模块主路径。
- **复核状态**: 已复核（独立复核 2026-10-18）——**维持 P2，附事实补正**。机制逐行证实：`handleError:298-326` 无尝试计数钳制、无退避；同步递归路径在码（`startAttempt:234-236` catch → `handleError` → `startAttempt` 同栈）；全程持 `BufferedSubscription` monitor（`AttemptSubscriber.onError:397-404` synchronized 内调 handleError，startAttempt 递归在锁内）。**事实补正**：初审「当前仓库主代码内无生产 IStreamingRetryCallback 实现注入点」在**仓库范围不成立**——nop-ai-gateway `AiGatewayFailoverInterceptor:208` 注入 `GatewayStreamingRetryCallback`（在审计 8 源码根之外，故初审表述在其单元范围内成立）；该生产实现自带 `retryBudget` 预算（attempt > budget → null）+ 熔断记账自限，「恒重试回调」场景需未来接入无界回调才成立。框架级无钳制 + 锁内同步递归 + 无退避的缺陷本体保留，扩展点已生产接线的事实提升修复优先级——按条件化口径维持 P2。现状：新发现/未修复。

### [C3-005] 订阅取消路径不 complete 执行上下文：onBeforeComplete/onAfterComplete 清理回调永不触发，取消的订阅泄漏执行上下文（JsonRpcWebSocketHandler 与 GraphQLSubscriptionManager 双入口同族）

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/engine/GraphQLSubscriptionPublisher.java:45-55`（`RpcSubscriptionPublisher.java:44-54` 同构）；`nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/ws/JsonRpcWebSocketHandler.java:338-347`；`nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/subscription/GraphQLSubscriptionManager.java:109-124`
- **证据片段**:
  ```java
  // GraphQLSubscriptionPublisher.TransformingSubscriber
  @Override
  public void cancel() {              // 下游取消 → 仅透传上游
      upstreamSubscription.cancel();  // context.complete() 缺失
  }
  @Override
  public void onComplete() {
      context.complete();             // 仅正常完成路径清理
      downstream.onComplete();
  }
  ```
- **严重程度**: P1
- **并发场景**: WS 容器线程处理 `unsubscribe` 消息或连接关闭事件（`JsonRpcWebSocketHandler.onClose:120-129` → `cancelOperation`）；订阅数据流在其他线程继续生产。
- **触发时序**: T1 客户端对订阅 id=1 发送 `unsubscribe`（或断连触发 `onClose` 级联取消）。T2 `cancelOperation` → `SubscriptionSubscriber.cancel()` → 包装 subscription.cancel → `TransformingSubscriber.cancel()` → 仅 `upstreamSubscription.cancel()`。**对照**：`onError` 路径经 `engine.buildRpcResponse(null, throwable, context)`（`RpcSubscriptionPublisher.java:73`）触发 `context.completeExceptionally`（`GraphQLEngine.java:683-687`），`onComplete` 路径显式 `context.complete()`——唯 cancel 路径两端（publisher 包装层与 handler 层）都无清理。T3 后果链：该订阅的 `IGraphQLExecutionContext`（`JsonRpcWebSocketEndpoint.executionService` 每次 `engine.newRpcContext` 新建）永达终态 → `ExecutionContextImpl.complete()` 才会执行的 `fireBeforeComplete()/fireAfterComplete()` 清理回调（`ExecutionContextImpl.java:108-181`）永不运行 → 注册过 onBeforeComplete/onAfterComplete 的资源（消息订阅、会话钩子等）悬挂至 GC；`SubscriptionInfo.executionContext` 同理（`GraphQLSubscriptionManager.unregisterSubscription:112` 仅 `subscription.cancel()`）。长连接客户端反复 subscribe/unsubscribe → 上下文对象单调积累。
- **现状**: 完成语义在三条终态路径（complete/error/cancel）中只覆盖了两条；取消是最常态的订阅退出方式（页面切换/组件卸载）。
- **风险**: 长时运行内存累积（C7 口径）+ 取消后清理回调不执行导致的下游资源悬挂；与「错误/取消路径的资源泄漏」P1 判级吻合。
- **建议**: 在 `TransformingSubscriber.cancel()`（两个 publisher 同修）中先行 `context.cancel()`/`context.complete()` 再 `upstreamSubscription.cancel()`；或 `JsonRpcWebSocketHandler.cancelOperation` 在移除条目时通过注册表回查执行上下文统一 complete。`GraphQLSubscriptionManager.unregisterSubscription` 对 `SubscriptionInfo.executionContext` 补同样处理。
- **失效面枚举**: 订阅上下文生命周期全家族：①`GraphQLSubscriptionPublisher`（GraphQL WS 订阅）✗ 本条；②`RpcSubscriptionPublisher`（RPC WS 订阅，quarkus/spring endpoint 实际使用路径）✗ 同构；③`GraphQLSubscriptionManager.unregisterSubscription/destroy` ✗ 仅 cancel Flow 订阅；④对照正确路径：`onComplete`（:81-84/:79-83）✓、`onError`（:74-78/:72-76，经 buildXxxResponse complete）✓；⑤`GraphQLServerCallHandler`（gRPC）取消路径为另一缺陷（C3-009），不重复计数。
- **信心水平**: 确定
- **误报排除**: context 对象并非纯值对象——`ExecutionContextImpl` 的 beforeCompletes/afterCompletes 是显式的清理回调契约，未触发即泄漏，属 F8「清理不对称」在取消路径的实例。
- **复核状态**: 已复核（独立复核 2026-10-18）——**保留，降级 P1→P2**。机制面全部证实：两 publisher 的 `cancel()`（GraphQLSubscriptionPublisher:51-54 / RpcSubscriptionPublisher:51-54）仅 `upstreamSubscription.cancel()`；`onComplete→context.complete`（:82/:80）、`onError→buildXxxResponse(null,t,context)→completeExceptionally`（GraphQLEngine:662-668/:683-687）两条已覆盖；transform 异常路径（onNext catch `:67-70`/`:65-68` → downstream.onError + cancel，自身不 complete）与 `ExecutionContextImpl` 不对称（`complete:109-130` 触发 before+after、`completeExceptionally:131-139` 跳过 before）复核确认；③面（GraphQLSubscriptionManager.unregisterSubscription:109-124）随 C3-014 确认为零接线 dormant。**降级依据（实害步骤证伪，机制不驳回）**：(1) context 无全局留存链——`newRpcContext→newGraphQLContextFromContext(null)+initRpcContext`（IGraphQLEngine:121-131）不附线程、subscribeRpc 无 cancelTokenManager 登记、`activeOperations` 于 cancelOperation 先 remove、上游 `LocalMessageService.cancel:74-90` 置位+摘除——取消后整个对象图（publisher+context）GC 可达，初审「上下文对象单调积累」不成立；(2) onBeforeComplete/onAfterComplete 在订阅路径的注册方为零（本单元 grep 无；平台注册方仅 nop-batch BatchTaskGlobals:30/35，不经订阅链）——现无确定性滞留资源；(3) ORM 会话生命周期独立于 context 终态（OrmTemplateImpl.runInNewSession finally `session.close:226-228`），无连接泄漏。残余=订阅终态机语义缺陷（live 于 RpcSubscriptionPublisher/WS 端点链的取消与 transform 异常路径）+ 未来回调注册方的潜伏面。P2。现状：新发现/未修复。

### [C3-006] WS 订阅链错误语义与活性：executionService 同步异常穿透 catch(IOException) 关闭整个连接；onError 不向客户端发终止信号；session-closed 分支不取消上游；keepAlive 定时任务遇 RuntimeException 静默终止调度

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/ws/JsonRpcWebSocketHandler.java:144-163, 173-189, 349-355, 457-480, 82-84, 112-117`
- **证据片段**:
  ```java
  protected void onMessage(JsonRpcRequest request) {
      try {
          switch (method) { ...
              default: handleSubscription(request);   // 可同步抛 NopException
          }
      } catch (IOException e) {                        // 只捕 IOException
          LOG.warn("nop.websocket.on-message-error", e);
      }
  }
  ...
  @Override
  public void onNext(ApiResponse<?> executionResult) {
      if (!session.isClosed()) { ...; subscription.get().request(1); }
      else { LOG.debug("nop.websocket.ignore-response"); }   // 不 cancel、不清理
  ```
- **严重程度**: P2
- **并发场景**: WS 接收线程处理订阅请求；数据流线程回调 onNext/onError；globalTimer 线程执行 keepAlive。
- **触发时序**: (a) 客户端发送 `{"id":"1","method":"Unknown__foo"}` → `handleSubscription:187` 的 `executionService.apply` → `subscribeRpc` 内 `fetcher == null` 抛 `NopException(ERR_GRAPHQL_UNKNOWN_OPERATION)`（`GraphQLEngine.java:807-810`）→ 穿透 `catch(IOException)` → 沿 `session.addMessageHandler` lambda 抛给容器 → 容器按规范终止连接：**一条坏消息杀死连接上全部活跃订阅**（错误语义降级为断连，客户端无 JSON-RPC 错误帧）。(b) 发送失败/转换异常进入 `TransformingSubscriber.onNext` 的 catch → `downstream.onError(e)` → `SubscriptionSubscriber.onError:476-480` 只记日志并移除注册表条目，**不发送任何错误/complete 帧** → 客户端对该 id 永久等待。(c) `onNext` 的 `session.isClosed()==true` 分支：不 `cancel()` 上游、不 `activeOperations.remove`——若容器未再回调 `onClose`（如 `parseMessage` 内 `session.close(4400)` 后的窗口），上游订阅与注册表条目依赖 onClose 兜底，存在滞留窗口。(d) `sendKeepAlive:357-363` 只捕 `IOException`；`IWebSocketSession` 实现抛任何 RuntimeException 即按 JDK `scheduleWithFixedDelay` 语义**永久终止**后续心跳且无重启/告警（CL-001 滞留问题的机制细化：死亡后连接空闲检测失效，半开连接滞留）。(e) `sendStreamingMessage:353` 先注册后订阅，若 `stream.subscribe` 同步抛（劣化 publisher），条目滞留至连接关闭。
- **现状**: 订阅链的五处错误/活性分支各自依赖不同兜底（容器关连接/onClose/无兜底），无统一的订阅终态机。
- **风险**: 单条畸形消息的爆炸半径为整条连接；客户端悬挂订阅无超时自知；keepAlive 死亡使代理/NAT 静默断连不可检测（F5）。
- **建议**: `onMessage(JsonRpcRequest)` 外层改捕 `Throwable` 并对可定位 id 回 `sendErrorFromGraphQL`/`INTERNAL_ERROR` 帧；`SubscriptionSubscriber.onError` 补发错误帧 + complete 帧；session-closed 分支补 `cancel()` + `activeOperations.remove(operationId, this)`；`sendKeepAlive` 改捕 `Throwable`。
- **失效面枚举**: WS 消息分支全家族：`handlePing/handleUnsubscribe/handleTokenRefresh`（内部 IOException 均就地处理 ✓）、`handleSubscription`（✗ 本条 a）；订阅终态回调：`onComplete`（发 complete 帧 ✓）、`onError`（✗ 本条 b）、`onNext` closed 分支（✗ 本条 c）；定时任务：`sendKeepAlive`（✗ d，与 CL-001 同对象，本条为机制细化，合并裁定时不重复计数）、`DownloadHelper` 定时删除（另行 CL-007）。
- **信心水平**: 确定（a/b/d 直接代码可证；c 依赖容器回调时序，标注窗口性）
- **误报排除**: 非「容器兜底所以无害」——(a) 的实害是错误协议语义丢失 + 连接级爆炸半径，(b)(c)(d) 均有明确滞留/悬挂后果，非优雅性报问题。
- **复核状态**: 已复核（独立复核 2026-10-18）——**维持 P2**。五个分支逐行证实：(a) `onMessage(JsonRpcRequest):144-163` 仅 catch(IOException)，`handleSubscription:187` 的 `executionService.apply` 内 `subscribeRpc` 对未知操作同步抛 NopException（GraphQLEngine:807-810）穿透至容器；(b) `SubscriptionSubscriber.onError:476-480` 仅日志+双参 remove、无任何错误/complete 帧；(c) `onNext` closed 分支 :468-472 不 cancel 不 remove（依赖 onClose 兜底，窗口性如实）；(d) `sendKeepAlive:357-363` 仅 catch(IOException)，RuntimeException 按 scheduleWithFixedDelay 语义永久终止心跳；(e) `sendStreamingMessage:349-355` 先注册后订阅（注释自认时序窗口）。与 CL-001 滞留机制的关系及 (d) 的合并不重复计数口径维持。现状：新发现/未修复。

### [C3-007] BizActionService.callActionAsync 裸提交工作线程：调用方 IContext 不传播，ServiceContextImpl 构造在池线程永久附着新建 context 且无 detach（平台 ExecutorHelper 约定被绕过），池线程上下文跨任务残留

- **文件**: `nop-service-framework/nop-biz/src/main/java/io/nop/biz/service/BizActionService.java:72-97`（配合 `BizActionInvocation.java:58-74`、`nop-kernel/nop-api-core/.../BaseContextProvider.java:36-54`、对照 `nop-kernel/nop-commons/.../ExecutorHelper.java:128-150`）
- **证据片段**:
  ```java
  // BizActionService.callActionAsync
  Runnable task = () -> {
      IRpcServiceInvocation inv = newInvocation(actor, bizAction, request);  // worker 线程上构造
      inv.proceedAsync().whenComplete((res, err) -> FutureHelper.complete(future, res, err));
  };
  Executor executor = (Executor) beanContainer.getBean(executorName);
  executor.execute(task);                       // 裸 execute，无 wrap、无 detach
  // BaseContextProvider.getOrCreateContext —— 池线程首次触达即永久附着
  IContext context = t_context.get();
  if (context == null || context.isClosed()) { return newContext(true); }  // attach=true
  // 对照平台约定 ExecutorHelper.submit —— finally { detachContext(); }
  ```
- **严重程度**: P1
- **并发场景**: HTTP/RPC 入口线程接收请求后经 `callActionAsync` 把 biz action 派发到工作线程池（默认 `DEFAULT_WORK_EXECUTOR_NAME` 或 bizSequential 分区执行器）；多请求任务在相同池线程上先后执行。
- **触发时序**: T1（入口线程）携带租户 tA/用户 uA/trace 的请求调用 `callActionAsync`——入口线程上的 tenant/locale/callExpireTime **均未随 task 传递**。T2（worker 线程 W）`newInvocation` → `new ServiceContextImpl()` → 构造器 `ContextProvider.getOrCreateContext()`：W 首次执行时创建空 context CA 并**附着到 W 的 ThreadLocal**（调用方身份、租户、trace 全部丢失）；若 W 曾执行过其他任务，`getOrCreateContext` 返回的是**上一个任务残留的附着 context**。T3 task 内任何写 context 的代码（`setAttribute`、`TccContext.setCurrent`、登录态写入、`IUserContext` 绑定）落在 CA 上；task 结束后**无人 detach**。T4 后续请求的任务在同一 W 上执行：`ContextProvider.currentTenantId()/currentUserId()` 与 `IUserContext.get()`（`ServiceContextImpl` 构造时捕获）读到前一请求残留值 → 租户过滤/鉴权判断基于错误身份。另：`BizActionInvocation.proceedAsync:60-61` 在 `currentContext()==null` 的线程上直接 `.getAttribute` 会 NPE（当前被 T2 的构造副作用掩盖，但任何在非池线程直接构造 `BizActionInvocation` 的调用路径都会踩中）。
- **现状**: 平台在 `ExecutorHelper.submit/schedule` 已确立「任务 finally detachContext」约定，`callActionAsync` 用裸 `executor.execute` 绕过了该约定；调用方上下文（tenant/locale/traceId/callExpireTime）既不快照传递、也不在 worker 上重建。
- **风险**: 异步 biz action（`@Async`/bizSequential 配置的方法）内依赖线程上下文的租户过滤、审计字段（createdBy 等）、日志 trace 全部失真；残留 context 使错乱**跨请求传播**，属「上下文丢失→鉴权失败/租户错乱」直接实例。
- **建议**: task 内以 `ContextBinder`/显式快照传播调用方 `IContext` 的 tenant/locale/trace（入口线程构造快照，worker 线程 `runWithContext` 执行），并在 finally `ContextProvider.instance().detachContext()`（或直接改用 `ExecutorHelper.submit`/`GlobalExecutors` 的受管提交入口）。`BizActionInvocation.proceedAsync` 对 `currentContext()==null` 增加 fail-fast 语义化错误。
- **失效面枚举**: 裸 `execute` 提交家族：`BizActionService.callActionAsync:87/:90`（`executeForPartition` 与 `execute` 两条分支**均裸提交** ✗ 本条双站点）；对照已守约站点：`ExecutorHelper.submit/schedule:143/:167/:192`（✓）；`GrpcServer` 的 executor 用于 gRPC 内部派发（grpc 自带线程模型，context 由 C3-012 入口面覆盖）；`JsonRpcWebSocketHandler`/`DownloadHelper` 的 globalTimer 任务不依赖 context（豁免）。`BizActionService` 是 `IBizActionService` 唯一实现（接口单实现核对）。
- **信心水平**: 确定（附着/残留机制由 BaseContextProvider 源码直接可证；T4 的实害取决于业务代码是否写 context——但调用方上下文**丢失**面（T1/T2）无任何条件，独立成立）
- **误报排除**: 非「无状态单例并发调用」误报类——这是线程绑定状态的跨任务残留与丢失，有具体写读链；非池化基础设施内部问题（基础设施在 api-core，仅对照），缺陷在调用方未守约。
- **复核状态**: 已复核（独立复核 2026-10-18）——**维持 P1**。双分支裸提交证实（`:87` executeForPartition / `:90` execute，`:92-94` 提交失败 completeExceptionally 覆盖 ✓）；唯一性独立 grep 复核成立——五模块全部 executor 提交形态中其余命中（CrudBizModel:916 `action.execute(context)`、GatewayHandler:184/187 routeExecutor.execute、InvokeProcessor:200 delayedExecutor.complete）均非裸线程池提交，`BizActionService` 为唯一站点；`ServiceContextImpl:35` 字段初始化 `ContextProvider.getOrCreateContext()` 在 worker 线程附着/复用残留 context 机制在码（BaseContextProvider:37-54）；`detachContext` 全仓 main 源码仅 ExecutorHelper:143/167/192 三点（grep 实证），「平台约定被绕过」定性成立。调用方上下文（tenant/locale/trace/callExpireTime）丢失面对任何 @Async biz action 无条件成立（平台一等公民路径），残留面（T4）条件性如实标注。维持 P1。现状：新发现/未修复。

### [C3-008] TccContextInvoker 的 set/restore 依赖「completion 与调用同线程」假设，在池化/无线附着线程上发起时经 getOrCreateContext 附着泄漏，TCC 分支上下文存在跨任务残留面

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/engine/TccContextInvoker.java:22-40`（配合 `nop-kernel/nop-core/.../TccContext.java:62-87`）
- **证据片段**:
  ```java
  TccContext oldContext = TccContext.getCurrent();
  TccContext.setCurrent(tccContext);                    // 调用线程的 currentContext 属性写入
  return thenOnContext(doInvokeAsync(task, request)).whenComplete((ret, err) -> {
      if (oldContext != null) {
          TccContext.setCurrent(oldContext);            // 完成线程执行；getOrCreateContext 语义
      } else {
          TccContext.removeCurrent(tccContext);
      }
  });
  ```
- **严重程度**: P2（条件化：正常 HTTP GraphQL 入口经 ContextBinder + thenOnContext 序列化后线程亲和成立；异常路径见触发时序）
- **并发场景**: GraphQL 操作执行链线程（HTTP 线程或 context 队列线程）发起 TCC 操作；异步 RPC 在 IO 线程完成后延续；C3-007 场景下 biz worker 线程再发起 GraphQL/RPC。
- **触发时序**: 主路径（HTTP 入口）中 `setCurrent` 与 `whenComplete` 经 `thenOnContext` 收敛到同一请求 context 对象，属性层（TccContext 存于 IContext attribute，非裸 ThreadLocal）恢复一致——此处**不误报**。条件化异常路径：T1 调用链运行在无附着 context 的线程（C3-007 的 biz worker 线程发起嵌套 GraphQL/RPC 调用，或消息消费线程路径 M8）→ `TccContext.setCurrent` 内 `getOrCreateContext()` 在该线程**创建并永久附着**新 context CX（`TccContext.java:69-73`）→ `thenOnContext(future)` 无参版用 `getOrCreateContext()` 拿到 CX，恢复在 CX 上执行；T2 任务完成后 CX 滞留于池线程（无 detach），`oldContext != null` 分支还把外层 TccContext 写进 CX；T3 同线程后续任务经 `TccContext.getCurrent()` 读到残留的 txnId/branchId → 若该任务恰好也是 TCC 分支操作，**join 到错误事务分支**（分布式事务下属数据损坏面前兆）。
- **现状**: 类内注释与 `ContextProvider.thenOnContext` 的 javadoc（「返回的promise需要立刻被使用，如果传递到其他线程上使用，则不确保这一点」）均自认线程假设，但无任何运行时守卫（如断言 `isRunningOnContext`）。
- **风险**: 条件触发但后果直指 TCC 分支错配；与 C3-007 的附着泄漏构成复合缺陷链（修复 C3-007 后本条风险面同步收敛）。
- **建议**: invokeAsync 入口断言/兜底：`currentContext()==null` 时 fail-fast（语义化错误）而非静默附着；恢复逻辑改为对捕获的 `IContext` 对象（而非完成线程的 currentContext）执行属性写回。
- **失效面枚举**: TCC 上下文传播家族：`TccContextInvoker`（graphql 操作层 ✗ 条件化，本条）；`TccContext.buildFromServiceContext` 供 RPC 客户端头传播（header 直读 ✓ 无线程依赖）；`ContextBinder.initContext`（HTTP 入口，不涉 TCC 属性 ✓）；api-core 内其他 `getOrCreateContext` 写点（`runWithTenant/runWithoutTenantId/disableExpireTime`，均 try/finally restore 且限同线程 ✗ 不涉案）。本维度内 TCC 相关仅此一处 invoker 装饰（`GraphQLTransactionOperationInvoker` 不触线程上下文，豁免）。
- **信心水平**: 有趣的猜测升级为很可能（主路径排除后剩余条件路径的代码链完整，但缺少线上触发实证；按口径不写出无条件下具体时序即不升级 P1）
- **误报排除**: 已显式排除主路径（HTTP binder + thenOnContext 线程亲和）误报，仅对条件路径立案并标注条件；非「原语使用」类误报。
- **复核状态**: 已复核（独立复核 2026-10-18）——**维持 P2（条件化）**。机制在码：`TccContextInvoker.invokeAsync:22-37` 结构原样（thenOnContext 无参版 + whenComplete 完成线程恢复）；`TccContext.setCurrent:69-70` 经 `ContextProvider.getOrCreateContext()` 在无线附着线程创建并永久附着新 context。主路径排除复核确认——默认 invoker 链（biz-defaults.beans.xml:27-35）中 TccContextInvoker 仅 TCC 操作条件装饰，HTTP 入口经 ContextHttpServerFilter 预附着 + thenOnContext 序列化后 setCurrent/恢复同 context 对象，属性层一致；条件路径（C3-007 biz worker 线程嵌套发起 / M8 消息线程）依赖已立案缺陷场景。判级 P2 恰当，与 C3-007 修复联动收敛。现状：新发现/未修复。

### [C3-009] gRPC 服务端取消传播断裂：onCancel 空实现、无 onCancelHandler 注册，客户端取消后 executeRpcAsync 幽灵执行到底

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-grpc/src/main/java/io/nop/graphql/grpc/server/GraphQLServerCallHandler.java:104-130`
- **证据片段**:
  ```java
  graphQLEngine.executeRpcAsync(ctx).whenComplete((res, err) -> {
      ...
      responseObserver.onNext((ApiResponse<R>) res);   // 写入已取消的 call
      ...
  });
  ...
  @Override
  public void onCancel() {
      super.onCancel();        // 空转：不取消执行、不置 cancelled、不 complete ctx
  }
  ```
- **严重程度**: P2
- **并发场景**: gRPC 客户端取消/断连（deadline 超时、client close）触发 transport onCancel；服务端 GraphQL 执行链（含 ORM 查询、下游 RPC）继续运行。
- **触发时序**: T1 客户端发起 unary 调用，服务端 `executeRpcAsync` 开始执行。T2 客户端 cancel/deadline 到期 → transport 回调 `onCancel()` → 仅 `super.onCancel()`（空实现），`ServerCallStreamObserverImpl.cancelled` 恒 false、`onCancelHandler` 未注册、`isCancelled()` 无人轮询。T3 执行链不被中断：数据库查询、下游 HTTP/RPC 全部执行完毕（幽灵任务）；`whenComplete` 在完成线程上对已死 call 执行 `sendMessage/close`（gRPC 静默忽略，无错误暴露）。对照引擎已有的取消基建：`CancelTokenManager` 登记的 `ctx`（IServiceContext 实现了 ICancellable）支持系统级 `cancel(reqId)`（`GraphQLEngine.cancel:699-701`），但 gRPC 路径未把 transport 取消接到该通道。
- **现状**: HTTP 路径有显式取消 API（SysBizModel → cancelTokenManager），网关流式有 abort 挂接；gRPC 路径是取消语义的盲区。
- **风险**: 大量客户端超时重试场景下服务端做双倍无用功（客户端已放弃），占用 ORM 连接池/下游配额；负载放大与 C7 重试风暴族同向。无数据正确性损害（结果写死 call 被忽略）。
- **建议**: `onCancel` 中调用 `responseObserver` 关联的取消通道：`graphQLEngine.getCancelTokenManager().cancel(reqId)` 或直接 `ctx.cancel()`；长执行 fetcher 侧在关键检查点读 `context.isCallExpired()`/取消标志。
- **失效面枚举**: gRPC 取消面家族：`GraphQLServerCallHandler`（unary，✗ 本条——`server/` 目录下唯一 ServerCallHandler 实现，失效面收敛）；同引擎其他入口对照：HTTP（SysBizModel cancel ✓）、WS（C3-005 取消但漏 complete，已另立案）、Gateway streaming（abort ✓ `GatewayHttpFilter.java:239-241`）、Gateway 普通转发（✗ 无取消挂接，见 C3-010，同族不重复计）。
- **信心水平**: 确定
- **误报排除**: 非「取消后结果被忽略即无害」——登记的失效面是幽灵执行本身；grpc generated stub 的取消语义属平台使用方式，缺陷在 handler 未接线。
- **复核状态**: 已复核（独立复核 2026-10-18）——**维持 P2**。证据逐行证实：`onCancel:128-130` 仅 `super.onCancel()` 空转；`ServerCallStreamObserverImpl.cancelled:148` 仅被 `onNext:186` 读取、全类无人写；`setOnCancelHandler:250-255` 存在但无注册调用；`executeRpcAsync:104` 链无 cancelTokenManager/`ctx.cancel()` 接线（对照 `getExecutionInvoker:688` 的 `cancelTokenManager.wrap` 仅服务系统级 cancel API，与 transport 取消无连接）。幽灵执行 + 客户端超时重试下的双倍无用功面成立，无数据正确性损害的定性维持。现状：新发现/未修复。

### [C3-010] Gateway 普通转发链无客户端取消传播：断连后重试延时器照常触发、上游请求照常发起（含非幂等 POST 重发），AiFailover 链同族且无退避

- **文件**: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/executor/InvokeProcessor.java:139-201`（配合 `GatewayHttpFilter.java:145-167`）；`nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/interceptor/AiFailoverGatewayInterceptor.java:73-144`
- **证据片段**:
  ```java
  // InvokeProcessor —— 429/5xx 延时重试
  return delayedFuture(delayMs).thenCompose(v ->
          invokeUrlWithRetry(url, request, invoke, svcCtx, retriesLeft - 1));
  private CompletionStage<Void> delayedFuture(long delayMs) {
      CompletableFuture<Void> future = new CompletableFuture<>();
      CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS)
              .execute(() -> future.complete(null));   // 到点即触发，无取消挂接
      return future;
  }
  ```
- **严重程度**: P2
- **并发场景**: 客户端断连/超时放弃后，服务端转发链仍在 `delayedFuture` 等待或执行重试；`CompletableFuture.delayedExecutor` 落在 commonPool 的静态调度线程。
- **触发时序**: T1 网关转发 POST 到上游，上游返回 502 → 进入退避（最长单次 30s，`MAX_RETRY_DELAY_MS`）。T2 客户端断连——`GatewayHttpFilter.filterAsync` 链上没有任何 cancel 挂接（`future.thenCompose(...).exceptionally(...)`，无 `whenComplete(cancel)`；仅流式路径有 abort）。T3 延时到点，`invokeUrlWithRetry` 重发 POST：若首次请求实际已被上游接受（502 发生于响应回程），重发造成业务副作用双执行（「半个成功」F2 面）；最坏 4 次上游调用（初发+3 重试）全部作用于已消失的客户端。T4 `AiFailoverGatewayInterceptor` 同族：fallback 链无退避（立即连打 `min(fallbackUrls, maxRetries)` 个备用 URL），同样无取消感知。
- **现状**: `CompletableFuture` 取消（`future.cancel`）本就只断链不打断任务，但本链**连 cancel 都无人调用**——响应 future 与客户端连接生命周期完全解耦。
- **风险**: 上游故障 + 客户端高超时重试的组合下，网关成为放大器（每客户端请求产生最多 4+N 次上游调用）；非幂等转发语义未声明也未守卫（无 idempotency-key 处理）。
- **建议**: `GatewayHttpFilter` 的响应 future 上挂接客户端断连回调（服务器适配层的 connection-close 事件 → `future.cancel(false)` 并传播到转发链首个 future）；`InvokeProcessor` 重试前检查链取消态；对非幂等方法（非 GET）默认不自动重试 5xx 或要求路由显式声明幂等。
- **失效面枚举**: 网关转发家族：`InvokeProcessor.invokeUrl`（url 直连 ✗ 本条）、`InvokeProcessor.invokeRpc`（RPC 调用链无重试，无放大面，豁免）、`InvokeProcessor.invokeSource`（XPL 本地执行，豁免）、`ForwardProcessor.executeAndRestore`（委托目标路由，继承目标路由处理器行为，间接涉案）、`AiFailoverGatewayInterceptor.tryFallbackUrl`（✗ 同族，附带无退避次缺陷——对照 C3-004 失效面清单的第 2 项）。流式路径 `BufferedStreamingPublisher` 取消已立案（C3-004/C2-002 相关），不重复。
- **信心水平**: 确定（机制）；重发双执行的实害取决于上游幂等性（标注条件）
- **误报排除**: 非「取消语义缺失是 CompletableFuture 固有限制」——对照同模块流式路径已实现 abort 挂接，证明该模块有取消意识，普通转发链是遗漏而非边界声明。
- **复核状态**: 已复核（独立复核 2026-10-18）——**维持 P2**。证据逐行证实：`GatewayHttpFilter.filterAsync:156-158` 普通转发链 `future.thenCompose(write).exceptionally(log+writeErrorResponse)` 无任何断连 cancel 挂接（对照 `writeStreamingResponse:238-241` 流式路径有 `whenComplete→abort()`，模块取消意识不对称）；`InvokeProcessor.delayedFuture:197-201` delayedExecutor 到点即触发无取消；429/5xx 重试上限 3 次有界；`AiFailoverGatewayInterceptor.tryFallbackUrl:111-144` 429/5xx 即刻 fallback 无退避复核确认。非幂等 POST 重发双执行面维持条件标注。现状：新发现/未修复。

### [C3-011] AuthHttpServerFilter 会话刷新回写 fire-and-forget：saveUserContextAsync 失败异常无人消费，会话状态静默丢失（F9）

- **文件**: `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/filter/AuthHttpServerFilter.java:374-377`（配合 `AbstractLoginService.java:51-62`、`AbstractUserContextCache.java:128-133`）
- **证据片段**:
  ```java
  CompletionStage<Void> promise = next.get().whenComplete((v, e) -> {
      loginService.flushUserContextAsync(userContext);   // 返回的 CompletionStage 被丢弃
  });
  // AbstractLoginService.flushUserContextAsync
  return userContextCache.saveUserContextAsync(userContext).thenRun(userContext::clearDirty);
  ```
- **严重程度**: P2
- **并发场景**: 请求执行完成时（context 队列线程）异步回写用户会话状态（dirty 标记的变更，如 token 刷新时间、权限变更）到底层 `userContextCache`。
- **触发时序**: T1 请求处理完毕，`whenComplete` 回调触发 `flushUserContextAsync`。T2 缓存写入失败（远程缓存抖动/序列化异常）→ `saveUserContextAsync` 异常完成 → 被丢弃的 stage 上无人 `exceptionally`/`whenComplete` → **异常无日志消失**；且 `thenRun(userContext::clearDirty)` 因上游失败被跳过，`dirty` 标记残留（此点自愈：下次请求会再 flush）。T3 实害：本次会话状态变更未持久化且不可见——如安全相关的会话字段更新（权限收缩、登录策略标记）在缓存故障窗口内静默丢失，鉴权继续按旧状态放行。
- **现状**: 同文件内对认证主链的异常有 `exceptionally` 处理（`_filterAsync:187-191`），唯独 flush 尾部裸丢。
- **风险**: 缓存故障窗口内会话一致性偏离且无任何可观测信号（排障无入口）；安全相关状态更新静默失效。
- **建议**: 补 `whenComplete((r, e) -> { if (e != null) LOG.error("nop.auth.flush-user-context-fail:sessionId={}", userContext.getSessionId(), e); })`；可选：失败时保留 dirty 并计数告警。
- **失效面枚举**: `flushUserContextAsync` 全部调用点 grep：`AuthHttpServerFilter.handleUserContext:375`（✗ fire-and-forget，本条）；`AbstractLoginService` 自身导出（由登录/登出链消费，`loginAsync/logoutAsync` 实现位于宿主模块，本审计根内调用点仅此一处）；`IUserContextCache.saveUserContextAsync` 的另一写点 `AbstractUserContextCache:130-132`（内部先 `clearDirty` 再 put，异常沿返回值传播，调用方负责——同款裸丢风险随本条同族登记）。
- **信心水平**: 确定
- **误报排除**: 非「吞异常但状态一致」豁免——吞掉后失败不可见且安全状态可能未持久化，F9 直接命中；非测试代码。
- **复核状态**: 已复核（独立复核 2026-10-18）——**维持 P2**。证据逐行证实：`AuthHttpServerFilter:374-377` 的 `whenComplete((v,e) -> loginService.flushUserContextAsync(userContext))` 返回 CompletionStage 被丢弃、无 exceptionally/whenComplete 消费；`AbstractLoginService.flushUserContextAsync:51-62` 的 `saveUserContextAsync(...).thenRun(userContext::clearDirty)` 失败时 clearDirty 跳过（dirty 残留下次自愈）复核确认；失败窗口内会话状态静默丢失且零日志的 F9 面成立。与 C1 轮同设施条目（UserContext flush 族）的修复批次合并建议见文末复核结论。现状：新发现/未修复。

### [C3-012] RPC 风格入口（gRPC/WS/消息/文件）统一缺失 ContextBinder：tenant/locale/callExpireTime 不从请求头落线程 IContext，与 HTTP GraphQL 入口传播能力不一致

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-grpc/src/main/java/io/nop/graphql/grpc/server/GraphQLServerCallHandler.java:100-102`、`nop-quarkus/nop-quarkus-web/src/main/java/io/nop/quarkus/web/ws/JsonRpcWebSocketEndpoint.java:50-64`（对照实现在 nop-graphql-core 的消费方）、`nop-service-framework/nop-graphql/nop-graphql-message/src/main/java/io/nop/graphql/message/GraphQLMessageSubscriptionRegistrar.java:103-112`、`nop-service-framework/nop-biz-file-core/src/main/java/io/nop/file/core/AbstractGraphQLFileService.java:42-60`
- **证据片段**:
  ```java
  // gRPC 入口 —— newRpcContext 仅把 headers 挂到 serviceContext
  IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(
          fieldDefinition.getOperationType(), fieldDefinition.getOperationName(), req);
  // 对照 HTTP 入口（GraphQLWebService.runRest）
  binder.init(request);   // ContextBinder: tenant/locale/timezone/callExpireTime → 线程 IContext
  ```
- **严重程度**: P2（条件化：实害取决于执行路径上是否有人读线程上下文而非 serviceContext；ORM 会话注册表按 context attribute 隔离，跨 context 无会话共享，加重隔离但 tenant 过滤若读 `ContextProvider.currentTenantId()` 则取到空/默认值）
- **并发场景**: gRPC 回调线程 / WS 接收线程 / 消息消费线程 / 文件服务调用线程上构建执行上下文。
- **触发时序**: T1 请求携带 `nop-tenant`/locale/timeout 头进入 gRPC（或 WS/消息）入口。T2 `newRpcContext` → `initRpcContext` 仅 `context.setRequestHeaders(request.getHeaders())`（`GraphQLEngine.java:466`）；无 `ContextBinder.init` → 线程附着 IContext 的 tenantId/locale/callExpireTime 保持为空/默认。T3 执行链内任何经 `ContextProvider.currentTenantId()/currentLocale()`（而非 `serviceContext.getRequestHeader`）的读取点——错误消息本地化（`buildRpcResponse` 的 `ContextProvider.currentLocale()`，`GraphQLEngine.java:670`）、超时判断（`isCallExpired`）、租户过滤——取不到请求级值。对照 HTTP 路径 binder 完整传播，形成同一引擎两套入口两套上下文语义。消息路径另有：`GraphQLMessageSubscriptionRegistrar.onMessage` 返回 `executeRpcAsync` 的 CompletionStage 依赖消费方解包（`LocalMessageService.java:216` 的 `FutureHelper.getResult` 已解包 ✓，但该契约未在 `IMessageSubscriber` 接口声明，其他 IMessageService 实现若不解包则消息提前 ACK，异步失败对 MQ 不可见——标注契约缺口）。
- **现状**: 传播责任分散在各入口，仅 HTTP GraphQL 入口做了完整 binder 初始化；`IGraphQLEngine.newRpcContext` 未提供带 binder 的统一入口。
- **风险**: 多租户部署下 gRPC/WS 通道请求的租户过滤、超时控制、locale 失效；同一 biz 逻辑经不同入口行为不一致（难排查的双语义）。
- **建议**: 在 `newRpcContext` 内统一接入 `ContextBinder` 等价逻辑（从 request headers 初始化线程 context 并在响应后关闭），或提供 `newRpcContextWithBinding` 供四个入口共用；`IMessageSubscriber` 接口显式声明「consumer 返回 CompletionStage 时消费方必须等待完成」契约。
- **失效面枚举**: 入口传播家族全景：HTTP GraphQL/REST（`GraphQLWebService` binder ✓）、gRPC（`GraphQLServerCallHandler` ✗）、WS（`JsonRpcWebSocketEndpoint`/`JsonRpcWebSocketEndpoint`（spring 同构）✗）、消息（`GraphQLMessageSubscriptionRegistrar` ✗）、文件（`AbstractGraphQLFileService` △ 依赖调用线程先行附着）、`RpcServiceOnGraphQL.callAsync`（进程内代理，调用线程通常已附着 △）。六入口中两 ✓ 一 △ 三 ✗。
- **信心水平**: 很可能（传播缺失确定；具体实害点依 ORM/错误消息路径的读取方式而定，已列可核查读取点）
- **误报排除**: 非跨模块公共 API 变更提议越权——按现状审计口径登记不一致事实；ORM 内部读取方式在 nop-orm（范围外）仅作语义对照，未审其内部。
- **复核状态**: 已复核（独立复核 2026-10-18）——**维持 P2（条件化），附对照面现状标注**。本体证据复核成立：`initRpcContext` 仅 `context.setRequestHeaders(request.getHeaders())`（GraphQLEngine:466 实测），gRPC/WS/消息/文件四入口无线程 IContext 请求级绑定。**对照面修正**：初审「对照 HTTP 入口 binder.init 完整传播」经第 3 轮部署面实证修正——默认部署下 HTTP 侧的请求级绑定实际由 `ContextHttpServerFilter`（HIGH_PRIORITY filter 链）履行、`ContextBinder` 为 no-op 死代码（见 C3-013 终裁）；本条的不一致本体（RPC 风格入口**无任何**每请求线程 context vs HTTP 入口**有**）在修正后对照下反而更清晰，且 RPC/WS 线程的读点落到残留/默认值的面与 C3-017 交汇。消息路径 `IMessageSubscriber` 解包契约缺口维持登记。现状：新发现/未修复。

---

## 维度集外线索（不立案展开）

### [nop-service-framework-hotpath-CL-007]（维度集外线索，不立案展开）DownloadHelper 定时删除任务异常静默吞没，临时 zip 文件滞留

`nop-biz/src/main/java/io/nop/biz/download/DownloadHelper.java:39-42`：`GlobalExecutors.globalTimer().schedule(() -> resource.delete(), waitMinutes, MINUTES)`——一次性调度任务的 RuntimeException 会被 ScheduledThreadPoolExecutor 吞入被丢弃的 Future（无任何日志），删除失败（文件被占用/权限）后临时文件永久滞留。属 C6 资源生命周期 + F9 形态，非异步链正确性本体。

### [nop-service-framework-hotpath-CL-008]（维度集外线索，不立案展开）GraphQLSubscriptionManager 在共享消息消费线程同步 pushToClient，慢客户端队头阻塞全局订阅分发

`nop-graphql-core/.../subscription/GraphQLSubscriptionManager.java:169-190`：`onMessage` 在 IMessageService 消费线程上顺序遍历命中订阅并同步 `session.sendMessage`（:217）——单个慢/阻塞 WS 会话使全部订阅的话题分发串行停摆。属 C8 背压/生产-消费边界形态（顺带记录：`findMatchingSubscriptions` 对全表 `matchesTopic` 线性扫描为 C9 形态）。

### [nop-service-framework-hotpath-CL-009]（维度集外线索，不立案展开）CacheActionDecorator 并发回源击穿无 singleflight；CacheEvictActionDecorator 回调线程复用调用方 EvalScope

`nop-biz/src/main/java/io/nop/biz/decorator/CacheActionDecorator.java:56-66`：miss 后并发请求同时回源（无 per-key in-flight 合并）；`CacheEvictActionDecorator.java:41-50`：evict 回调在**完成线程**上用调用方线程构造的 `ctx.getEvalScope().newChildScope()` 求值 cacheKey——跨线程使用 EvalScope 的线程安全语义未声明。均属 C2/C9 形态。

---

## 总结

| 类别 | 数量 | 明细 |
|------|------|------|
| C3 立案发现 | 12 | C3-001~C3-012；其中 P1×5（001/002/003/005/007），P2×7（004/006/008/009/010/011/012） |
| P0 | 0 | 无满足「默认用法+常见条件即触发且不可逆」的异步链缺陷 |
| 维度集外线索 | 3 | CL-007（DownloadHelper 吞异常滞留文件）、CL-008（订阅管理器同步 push 队头阻塞）、CL-009（cache 装饰器击穿/EvalScope 跨线程） |

合计：12 条立案（P1×5 + P2×7）+ 3 条 CL 线索。

**主线结论**：
1. **GraphQL 异步执行链**的三处结构性问题集中在 DataLoader 调度泵：失败断链+异常吞没（C3-001）、出口泵线程亲和缺失殃及 ORM 会话（C3-002）、以及与 `thenFetchNext` 已有 thenOnContext 治理的不一致——修复应统一泵的失败延续与 context 挂接。
2. **订阅链生命周期**的取消路径是系统性盲区：执行上下文不 complete（C3-005）、错误语义丢失（C3-006）、gRPC 幽灵执行（C3-009）同属「终态机缺一条腿」。
3. **Gateway 流式链**的背压声明与实现不符（C3-003），重订阅扩展点缺治理守卫（C3-004），普通转发链取消语义缺失（C3-010）。
4. **上下文传播矩阵**显示 4 个 ✗ 站点集群，其中 `BizActionService.callActionAsync` 绕过平台 `ExecutorHelper` 清理约定（C3-007）是最明确的「平台设施未用」实例；C3-008/C3-012 是其入口面延伸。

（本报告为第 1 轮初审盲审产出，全部条目复核状态=未复核，待独立复核轮裁定。）

---

## 维度复核结论（独立复核子代理，2026-10-18）

复核方式：与初审/深挖/收口代理相互独立，以怀疑姿态对 17 条发现 + 5 条 CL 线索逐条亲开 live code 核对证据（file:line 全量重开）；本阶段解除盲审，对照 `ai-dev/audits/cra-index.md`（WI 1.1–2.2 台账，不含本单元）、`ai-dev/plans/2470-cra-wi2.3-hotpath.md`（判级口径）、同目录 C1/C2/C4 轮（C3-014 与 C1-003/C2-005 同设施关系、C3-016 与 C4-CL-011 关系的合并终裁）。java-dataloader 3.2.2 字节码为全新解包独立反汇编（`_tmp/c3-review-dl/`，不依赖初审解包物）。git HEAD 5994c18211，涉及文件无审计后修复提交。

### 裁定汇总表

| 编号 | 原判级 | 复核裁定 | 复核要点 |
|---|---|---|---|
| C3-001 | P1 | **采纳改判，P3** | 二次字节码反证独立复验成立：invokeLoader 全方法体 catch(Exception)（异常表 from 0 to 58 target 61）+ lambda$3 逐 key completeExceptionally 后 emptyList 正常返回 + waitAll=allOf——批失败/同步抛出均不断链，悬挂不存在；残余=观测性+3.2.2 语义依赖 |
| C3-002 | P1 | 维持 P1 | 出口泵/递归泵无挂接 vs thenFetchNext 有挂接直接在码；复核消除「invoker 装配」保留意见（默认链无 context 恢复）；失效面比初审更宽（首轮嵌套游走整体在完成线程） |
| C3-003 | P1 | 维持 P1 | 无界缓冲 + 自驱动 request(1) + onError 不 cancel 上游全证实；复核补强：nop-ai-gateway 生产注入 retryCallback 使缓冲路径成为 AI 流式路由默认形态 |
| C3-004 | P2 | 维持 P2 + 事实补正 | 无钳制/锁内同步递归/无退避全证实；补正：nop-ai-gateway 存在生产 GatewayStreamingRetryCallback（初审「无生产实现」在仓库范围不成立），其自带 retryBudget+熔断自限 |
| C3-005 | P1 | **保留，降级 P2** | 三取消入口+transform 异常缺口机制全证；降级依据=实害步骤证伪：context 无全局留存链（GC 可达）、订阅路径回调注册方为零、ORM 会话独立于 context 终态——「单调积累/资源悬挂」不成立 |
| C3-006 | P2 | 维持 P2 | 五分支（a~e）逐行证实 |
| C3-007 | P1 | 维持 P1 | 双分支裸提交+全单元唯一站点+ServiceContextImpl:35 附着+detachContext 全仓仅 ExecutorHelper 三点全证实；调用方上下文丢失面无条件 |
| C3-008 | P2 | 维持 P2 | setCurrent→getOrCreateContext 附着语义在码；主路径排除复核确认；条件路径依赖 C3-007 场景 |
| C3-009 | P2 | 维持 P2 | onCancel 空转+cancelled 无人写+无 cancelToken 接线全证实 |
| C3-010 | P2 | 维持 P2 | 普通转发无断连挂接（流式有 abort，不对称）+delayedFuture 无取消+AiFailover 无退避全证实 |
| C3-011 | P2 | 维持 P2 | flush fire-and-forget+clearDirty 失败跳过全证实 |
| C3-012 | P2 | 维持 P2 + 现状标注 | 四入口无请求级线程 context 本体成立；对照面按第 3 轮修正（HTTP 侧实际为 ContextHttpServerFilter，非 binder）——修正后不一致本体更清晰 |
| C3-013 | P1 | **采纳改判，P3** | 决定性反证三环节独立核实：beans.xml 无条件注册+HIGH_PRIORITY filter 每请求建 context 绑定+binder ctor 二分——默认部署恒 no-op；面 (b) 仅非默认配置可达；修复方向警示采纳（改 init 复用分支=安全降档） |
| C3-014 | P2 | **采纳改判，P3（dormant）** | 机制在码+零生产调用方独立 grep 证实；机制归并 C1-003/C2-005 设施工单，终态机 payload 必须随迁 |
| C3-015 | P2 | 维持 P2 | 两 fetcher thenCompose 无挂接逐行证实；全 rpc 层无其他兜底 grep 确认 |
| C3-016 | P2 | **采纳改判，P3（契约条款）** | maxWaitMillis=0 默认+零接线+生产 bean 为 Sentinel 非阻塞三事实独立核实；并入 C4-CL-011 不成立卷宗互注 |
| C3-017 | P2 | 维持 P2 | 裸附着/条件 detach（holder==this）/无清理三环节+TenantParamBuilder:23 与 buildRpcResponse:670 读点全证实；条件化如实 |

**统计**：17 条发现——0 驳回、0 升级、5 降级（C3-001 P1→P3、C3-005 P1→P2、C3-013 P1→P3、C3-014 P2→P3、C3-016 P2→P3）、12 维持。复核后判级分布 **P1×3（C3-002/003/007）/ P2×10（C3-004/005/006/008/009/010/011/012/015/017）/ P3×4（C3-001/013/014/016）**，P0×0。5 条 CL 全部确认（CL-008 附 dormant 标注）。

### 四条改判终裁（本轮核心裁定）

1. **C3-013 P1→P3 终裁：采纳**。(a) `biz-defaults.beans.xml:122-127` 无条件注册实测；(b) `ContextBinder.java:25-34` ctor `currentContext()==null` 二分、B2 跳过 init/close 实测（`BaseContextProvider.currentContext:22-24` 裸读无 isClosed 过滤）；(c) `ContextHttpServerFilter` order=HIGH_PRIORITY（IOrdered: HIGH=1000<NORMAL=5000）+ `filterAsync:41-45` 裸附着每请求 context + `initContext:62-87` 头绑定（含 nop-timeout→callExpireTime，第 2 轮「callExpireTime 全链无人设置」确系漏检范围外 filter）+ quarkus/spring 装配默认启用实测。面 (b) 仅 `nop.web.http-server-filter.enabled=false` 非默认配置可达。**修复方向警示**：第 2 轮「init 复用时也写请求头」的建议若实施，将在认证路由以 header tenant 覆写 session tenant——安全降档；正确方向=声明 ContextHttpServerFilter 为唯一绑定 owner、GraphQLWebService 侧废除 binder 依赖（plan-first，涉 nop-rpc-api 原语）。
2. **C3-001 P1→P3 终裁：采纳**。第 3 轮对第 2 轮的二次反证（invokeLoader 异常表 + lambda$3 空列表）经本复核全新解包独立反汇编逐条证实；第 2 轮「同步抛出穿透 dispatch()」机制被推翻。残留缺陷为观测性与版本依赖守卫缺失，修复降格为诊断性加固（whenComplete+日志+注释），「逐 loader 异常隔离」为顺手加固非缺陷修复。
3. **C3-014 P2→P3 终裁：采纳（dormant）**。零生产调用方独立证实（与 C1-003 复核结论交叉一致）；机制归并 C1-003/C2-005 设施工单（同设施双维 P2 已立案），本条终态机 payload（孤儿三通道不可达 + executionContext 永不 complete + findMatchingSubscriptions 遍历 id 表的推送面收窄）合并时必须随迁，不得在合并中丢失。
4. **C3-016 P2→P3 终裁：采纳**。三事实独立核实；第 2 轮「newGraphQLExecutor:894 直接注入 DefaultFlowControlRunner」表述失准复核确认（实际为 @Nullable bean 注入）。与 C4-CL-011（不成立终判）同向收敛，并入其卷宗互注编号；残值=「异步签名不得静默阻塞调用线程」契约条款，写回 owner doc 即可。

### C3-005 终裁（复核主动降档，非初审/深挖建议）

机制面**全部保留**：两 publisher cancel() 仅透传上游、cancelOperation→SubscriptionSubscriber.cancel 链、transform 异常路径（onNext catch→downstream.onError+cancel 不 complete）、completeExceptionally 跳过 fireBeforeComplete 的结构性不对称、③面 dormant——逐行证实。**判级 P1→P2 依据为实害步骤证伪**（裁定可改严重度、不改事实的边界内）：
1. **「上下文对象单调积累」不成立**：`newRpcContext`（IGraphQLEngine:121-131）不附线程、subscribeRpc 无 cancelTokenManager 登记、`activeOperations` 于 cancelOperation 先 remove、上游 `LocalMessageService.cancel:74-90` 置位+摘除——取消后 publisher+context 对象图 GC 可达，无留存链；
2. **回调注册方为零**：订阅路径 onBeforeComplete/onAfterComplete 注册点在本单元 grep 为零（与第 2 轮自述一致），平台注册方（nop-batch BatchTaskGlobals）不经订阅链——「资源悬挂至 GC」的确定性滞留物现不存在；
3. **ORM 会话独立于 context 终态**：`OrmTemplateImpl.runInNewSession` finally `session.close`（:219-230），无连接随 context 滞留。

残余缺陷=订阅终态机语义不对称（live 于 RpcSubscriptionPublisher/WS 端点链的取消与 transform 异常路径）+ 未来回调注册方的潜伏面。**回升条件**：若订阅路径出现 onBeforeComplete/onAfterComplete 注册方，或发现新的 context 留存链，应回升 P1。修复建议不变（cancel 路径补 context.complete/cancel + unregister 对 executionContext 补收尾，随 dormant 设施工单一并处理）。

### C3-007 终裁

**维持 P1**。双分支（`:87` executeForPartition / `:90` execute）裸提交实测；全单元裸 executor 提交唯一性经 8 形态 grep 独立复核（其余命中均为非线程池提交或 future 完成）；`ServiceContextImpl:35` 字段初始化 `getOrCreateContext()` 的附着/残留机制在码；`detachContext` 全仓 main 仅 ExecutorHelper:143/167/192 三点。调用方上下文丢失面对任何 @Async biz action 无条件成立（平台一等公民路径：DEFAULT_WORK_EXECUTOR_NAME/bizSequential），满足「具体触发时序+每步可信」的 P1 口径；T4 残留实害的条件性标注维持。

### CL 线索处置

| 编号 | 处置 | 要点 |
|---|---|---|
| CL-007 | 确认 | `DownloadHelper:39-42` 一次性 schedule 吞异常滞留临时文件，C6/F9 形态 |
| CL-008 | 确认 + dormant 标注 | `onMessage:180-187` 串行同步 pushToClient 机制在码；但注册表出厂恒空（registerSubscription 零生产调用方，同 C3-014）——队头阻塞在出厂接线不可达，设施被接线即生效 |
| CL-009 | 确认 | CacheActionDecorator:56-66 miss 并发回源无 singleflight；CacheEvictActionDecorator:41-50 完成线程复用调用方 EvalScope 求值 cacheKey，C2/C9 形态 |
| CL-019 | 确认 | BeanMethodBatchFetcher:75-81 synchronized(context) + GraphQLExecutionContext.dispatchAll synchronized + futureCall 同步 call——biz 批方法（含 ORM/DB）持 context 监视器执行，C2/C9 形态 |
| CL-020 | 确认 | GatewayHandler.processRoute:150 / GatewayHttpFilter exceptionally 的 currentLocale 于完成线程读残留；网关无自身 IContext 绑定——与 C3-017/C3-012 同根，修复轮合并治理 |

### 争议点与保留意见

1. **C3-005 的降档是本轮唯一由复核主动发起的判级变更**：初审/深挖/收口三轮均维持 P1 未质疑其实害步骤；复核以「留存链不存在 + 回调注册方为零 + ORM 会话独立终态」三项实测证伪 P1 的后果链。机制不驳回、修复建议不变；若后续平台在订阅路径引入 cleanup 回调注册，判级应回升。
2. **C3-004 的事实补正**：初审「当前仓库主代码内无生产 IStreamingRetryCallback 实现」在仓库范围不成立（nop-ai-gateway `AiGatewayFailoverInterceptor:208`）。判级不受影响（该实现自带 retryBudget+熔断自限），但「扩展点已生产接线」的事实应纳入修复优先级评估——框架级 maxAttempts 钳制缺失意味着下一个接入者无需重蹈。
3. **C3-002 的失效面比初审更宽**：invokeOperation 出口泵 thenCompose 内的 `fetchNext` 是同步调用，异步顶层操作后的首轮嵌套字段游走整体发生在完成线程（非仅 OrmBatchLoader 的 dispatch 段）——补强性标注，判级不变（仍 P1）。
4. **C3-003 与 C3-004 的生产联动**：AiGatewayFailoverInterceptor 的回调注入同时使 C3-003 的缓冲路径（bufferEngaged）成为 AI 流式路由默认形态——两条 P1/P2 的修复应同批评估。
5. **C3-012 对照面表述**：初审以「HTTP 入口 binder.init 完整传播」为对照，经第 3 轮部署面实证该对照物为 no-op（C3-013）；本条按修正后对照（ContextHttpServerFilter vs 无）维持立案，修复轮须与 C3-013/C3-017 的入口 context 统一治理合并，避免按第 1 轮对照面误导修复。
6. **C3-001 残余的版本脆弱性**：泵正确性隐式依赖 java-dataloader 3.2.2「invokeLoader 全捕获 + exceptionally 吞没」内部语义，升级该依赖（后续版本 recursivelyDispatch 等）时无守卫声明——建议随 P3 加固补注释钉定。

## 最终保留项

修复工单合并建议（按同步域/设施归批，避免碎片化修复）：

1. **上下文传播统一工单（C3-002 + C3-007 + C3-015 + C3-008 + C3-012 + C3-017 + CL-020，涉 plan-first 面）**：
   - `GraphQLExecutor` 出口泵（invokeOperation:135-147）与递归泵（dispatchAll:203）补 `thenOnContext`（与 thenFetchNext:152 对齐）；TransformFetcher/EvalActionTransformFetcher（:33/:33）同批挂接（C3-002/015）；
   - `BizActionService.callActionAsync` 改受管提交（ExecutorHelper.submit 形态）或入口线程快照 + worker runWithContext + finally detachContext（C3-007，P1 主修复）；BizActionInvocation.proceedAsync 对 currentContext()==null 补 fail-fast；
   - TccContextInvoker 恢复改对捕获 IContext 对象写回 + 无附着线程 fail-fast（C3-008，随 C3-007 收敛）；
   - RPC 风格入口统一 context 建立（newRpcContext 统一 binder 等价逻辑或 newRpcContextWithBinding）+ gateway 错误路径 locale 改读入参 context（C3-012/017/CL-020）；ContextHttpServerFilter 对称 detach 交 plan-first（nop-http-api 原语）。
2. **DataLoader 泵加固工单（C3-001，P3）**：`:203` thenAccept→whenComplete + 失败日志 + 3.2.2 语义依赖注释；逐 loader try/catch 隔离顺手加固。
3. **Gateway 流式/转发工单（C3-003 + C3-004 + C3-010）**：createMappedPublisher 改 demand 门控（单点覆盖缓冲/直通双态）+ onNext catch 补上游 cancel（C3-003，P1）；BufferedStreamingPublisher.handleError 补 maxAttempts 钳制 + 退避（退避期间释放 monitor）+ 与 AiGatewayFailoverInterceptor 预算对账（C3-004，P2）；GatewayHttpFilter 普通转发挂接断连 cancel + InvokeProcessor 重试前查取消态 + 非幂等方法重试守卫（C3-010，P2）。
4. **订阅终态机工单（C3-005 + C3-006 + C3-009 + dormant 组）**：两 publisher cancel() 补 context 终态、unregister 对 executionContext 补收尾（C3-005，P2，与 dormant 设施工单同批）；JsonRpcWebSocketHandler onMessage 改捕 Throwable 回错误帧 + onError 补帧 + closed 分支补 cancel + sendKeepAlive 改捕 Throwable（C3-006，P2）；GraphQLServerCallHandler.onCancel 接 cancelTokenManager/ctx.cancel（C3-009，P2）；C3-014 putIfAbsent 随 C1-003/C2-005 设施工单（终态机 payload 随迁）。
5. **Auth 会话工单（C3-011）**：flushUserContextAsync 补 whenComplete 错误日志（sessionId 入参）；与 C1 轮 UserContext flush 族（C1-001/C1-010）修复批次合并对账 clearDirty 契约。
6. **契约/文档批（C3-013 + C3-016，P3）**：C3-013 按「ContextHttpServerFilter 唯一绑定 owner」方向处置（废除 GraphQLWebService binder 依赖或 ContextBinder ctor 补 isClosed 判定，plan-first）；C3-016 runAsync 契约注释 + maxWaitMillis 文档化，并入 C4-CL-011 卷宗。
7. **CL 移交批**：CL-007→C6（一次性 schedule 吞异常，日志包装或返回 future 消费）；CL-008→随 dormant 设施接线评估（接线时同步补 per-session 异步推送/有界队列）；CL-009→C2/C9（cache 装饰器 singleflight + EvalScope 线程亲和契约）；CL-019→C2/C9（context 锁内 biz 批方法的锁内 I/O 面）；CL-020→随第 1 项入口 context 统一治理。

**现状标注汇总**：17 条发现 + 5 条 CL 全部为**新发现/未修复**（git HEAD 5994c18211 无审计后修复提交；cra-index 台账 WI 1.1–2.2 不含本单元）。复核后 C3 维度合计：**P1×3 / P2×10 / P3×4 + CL×5（全部确认，CL-008 dormant）**。
