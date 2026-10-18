# C3 异步链路正确性审计报告（第 2 轮深挖，盲审）

## 审计单元

- **维度**：C3——异步链路正确性（异步任务链的异常传播/超时/取消语义；future 转换链断裂；异步回调的上下文传播；订阅/流式链的生命周期）
- **审计对象**：请求热路径五模块（8 源码根）：nop-graphql（core/orm/message/grpc）、nop-biz、nop-gateway、nop-biz-auth-core、nop-biz-file-core
- **仓库**：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`，HEAD 5994c18211）
- **本轮输入**：第 1 轮报告 `C3-async-chains.md`（C3-001~012，P1×5/P2×7 + CL-007~009 + 22 站点传播矩阵）+ 任务说明中的 CL-018 移交核对项（ContextTaskQueue.syncGet 无超时）+ 基线 `_tmp/cra-baseline-nop-service-framework-hotpath.txt` + live code；java-dataloader 3.2.2 字节码（`~/.m2/repository/com/graphql-java/java-dataloader/3.2.2`，解包至 `_tmp/c3r2-dl/`）；api-core/nop-core/nop-rpc-api/nop-rpc-core 中被审计代码直接依赖的语义对照点（FutureHelper、ContextProvider、BaseContextProvider、BaseContext、ContextTaskQueue、ContextBinder、ExecutionContextImpl、DefaultFlowControlRunner）。

## 盲审纪律声明

本报告为第 2 轮盲审产出。除任务指明的第 1 轮文件 `C3-async-chains.md` 外，未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件（含同目录其他维度文件）。所有发现以当前 live code 实测与 java-dataloader 3.2.2 字节码反编译为准。预注的 C1/C2 轮 16 条仅按任务说明中的编号对照引用，未展开复核其内容。

## 本轮目标（任务指令 ①~⑤）

1. **① P1×5 失效面扩展**：C3-001（3.2.2 dispatch 语义对照 + 全部 dispatch 调用点）、C3-002（穷举缺 thenOnContext 的异步出口）、C3-003（gateway 其他自驱动 request(1) 流式路径）、C3-005（complete 回调触发点全枚举 + 不对称面）、C3-007（其余裸 execute 站点）。
2. **② CL-018 核对**：本单元全部 syncGet 站点的嵌套链中间 future 泄漏面逐一判定。
3. **③ 正常完成路径的上下文恢复对称性**。
4. **④ 订阅 execute 链异步生命周期**（GraphQLSubscriptionManager + JsonRpcWebSocketHandler）。
5. **⑤ 22 站点传播矩阵抽验 5 行**（含已立案 3 行）。

---

## 一、① P1×5 失效面扩展结论

### 1.1 C3-001：java-dataloader 3.2.2 dispatch 语义对照——原触发机制被证伪，修正后残留面收窄（重要修正）

**字节码实测**（`org.dataloader.DataLoaderHelper`，3.2.2）：

1. `DataLoader.dispatch()` → `DataLoaderHelper.dispatch()`：在 `DataLoader` 监视器内对 `loaderQueue` **快照 + clear**（offset 16-122），批调用在监视器外执行（offset 133+）。**3.2.2 无 recursivelyDispatch**（该语义在后续版本引入）；本平台的 `GraphQLExecutor.dispatchAll` 递归泵就是 3.2.2 下的递归调度替代物。
2. **关键反转**：`dispatchQueueBatch` 对批 future 挂了 `.thenApply(lambda$dispatchQueueBatch$2).exceptionally(lambda$dispatchQueueBatch$3)`；反编译 `lambda$dispatchQueueBatch$3` 显示：批失败时对每个 key `future.completeExceptionally(throwable)` + `dataLoader.clear(key)` 后 **`return Collections.emptyList()`**——即异常被 exceptionally 处理器**吞掉**，`loader.dispatch()` 返回的 future **正常完成**（空列表），异常仅传播到个体 load future。
3. 因此第 1 轮 C3-001 的触发时序「T2：F1 异常完成 → waitAll 聚合 promise 异常完成 → thenAccept 递归延续被跳过 → 泵死亡」**在 3.2.2 下不成立**：`FutureHelper.waitAll`（`FutureHelper.java:389-413`，`CompletableFuture.allOf`）的输入（各 dispatch future）在批失败时全部正常完成，泵链**存活**，字段级错误经个体 load future 正常可见。
4. **两个已出厂 loader 的失败形态**：`OrmBatchLoader.load`（`OrmBatchLoader.java:31-38`）全同步且仅返回 `FutureHelper.success(ret)`——失败只能以**同步抛出**发生（`requireSession()` 的 ERR_ORM_NOT_IN_SESSION、`supplier.get()` 的实体解析失败），同步抛出会**穿透** `invokeListBatchLoader`→`dispatch()` 传播出 `GraphQLExecutionContext.dispatchAll` 的 for 循环（`invokeLoader` 在 arg 求值处抛出，exceptionally 来不及挂接）；`BeanMethodBatchFetcher` 的 batchLoader 经 `FutureHelper.futureCall`（`FutureHelper.java:122-129`，try/catch Throwable → `reject(t)`）——**永不同步抛出**，批失败全部走 exceptionally 吞没路径。
5. **修正后的真实残留面**：同步抛出只可能来自 OrmBatchLoader，其后果按泵位点分化——
   - 初始泵（`executeOneAsync:98`/`fetchResult:120`/`executeAsync:189`，同步段）：异常传播至 `toRpcResponse`/`toGraphQLResponse` 的 `catch(Exception)`（`GraphQLEngine.java:608-612`）→ 500，无悬挂；
   - 回合出口泵（`invokeOperation:145`/`thenFetchNext:156`，thenCompose 内）：异常使该回合组合 future 异常完成 → 响应链报错，无悬挂（已创建的 `ret` stage 成为孤儿，次要）；
   - **递归泵（`dispatchAll:203` 的 `thenAccept`）**：延续内同步抛出时，`thenAccept` 返回的 stage **被丢弃、异常静默吞没、无日志**——当递归延续运行在非内联线程（异步批存在时，聚合 promise 在 biz worker 线程完成）时，本批及后续注册的 loader **永不 dispatch** → load future 永不完成 → 响应悬挂无日志。可达组合：`BeanMethodBatchFetcher` 异步批（biz 方法返回 CompletionStage，完成于 worker 线程）+ 失败窗口内注册的 OrmBatchLoader 嵌套加载 + 该线程上 `requireSession` 失败（与 C3-002/C3-007 的错误线程/错会话面复合）。
6. **建议改判**：按判级口径（触发依赖特定组合即条件化降档），C3-001 建议 **P1 → P2**（原 P1 依据的无条件「批失败即断链」机制不成立；残留面需三重条件）。修复建议不变：`:203` 改 `whenComplete`（失败也继续泵 + 记日志），`GraphQLExecutionContext.dispatchAll` 对单个 loader 的 dispatch 异常做逐 loader 隔离（try/catch per entry），避免一个 loader 的同步抛出丢弃同批其余 loader。

**F3 dispatch 调用点全枚举**：全仓库 main 源码 `.dispatch()` 调用点**唯一**：`GraphQLExecutionContext.java:167`；`dispatchWithCounts`/`dispatchAndJoin`/`dispatchDepth`/`recursivelyDispatch` 零调用。DataLoader 触点文件全集：`GraphQLExecutionContext`、`GraphQLExecutor`、`OrmBatchLoader`、`BeanMethodBatchFetcher`（其余 Orm*Fetcher 不直接触 DataLoader）。失效面收敛于单一递归泵实现 + 唯一 dispatch 入口。

### 1.2 C3-002：缺 thenOnContext 的异步出口穷举（22 矩阵标注站点复核 + 补漏）

live 复核：本分支已带 P1-2.6-004 调度修复注释（`invokeOperation:141-145`、`thenFetchNext:154-156`），但 **`:135` 出口泵与 `:203` 递归泵仍无 thenOnContext 挂接**——第 1 轮立案的两处 live 不变。全单元延续站点穷举（thenCompose/thenApply/thenAccept/thenRun/whenComplete/exceptionally 中执行 context 敏感或用户代码者）：

| 站点 | 延续内容 | 判定 |
|------|---------|------|
| `GraphQLExecutor.invokeOperation:135-147` | fetchNext 全树游走 + dispatchAll | **✗ 已立案 C3-002** |
| `GraphQLExecutor.dispatchAll:203` | 递归泵（batchLoader 同步段） | **✗ 已立案 C3-001/002** |
| `GraphQLExecutor.invokeOperations:285-291` thenCompose | action.get() 仅构建链（thenOnContext 在内），无用户代码执行 | 豁免 |
| `GraphQLExecutor:618` waitAll.thenApply + syncGet | normalizeValue/result.put（synchronized resultLock），context 无关纯数据操作；syncGet 受同批 waitAll 前缀守护 | 豁免 |
| `TransformFetcher:32-35` / `EvalActionTransformFetcher:31-35` | transformer（XPL/用户代码，携带 env/EvalScope）于上游完成线程 | **✗ 新立案 C3-015** |
| `MakerCheckerTryServiceAction:42` | context 经参数显式传递 | ✓（M17 复核一致） |
| `RpcServiceOnGraphQL.invokeAsync0:73-75` | `thenCompleteAsync` handler（实为完成线程就地执行，`FutureHelper.java:334-348`）触碰 ctx 构建 response | △ 映射为主，随 C3-015 族登记 |
| `JsonRpcService:79/:116` thenApply | 纯 JSON-RPC 映射 | 豁免 |
| `GraphQLWebService:96/:256` thenApply | 日志 + responseBuilder（JaxrsHelper 纯映射） | 豁免 |
| `GatewayHandler.processRoute:169-181` exceptionally | `ContextProvider.currentLocale()` 于完成线程 | △ → CL-020 |
| `RouteExecutor.execute:74-81` thenApply/exceptionally | interceptor onResponse/onError 回调于完成线程 | △ → C3-015 族 |
| `ForwardProcessor.executeAndRestore:103-106` | whenComplete 恢复 route 状态（共享 IGatewayContext） | △ → CL-020 备注 |
| `InvokeProcessor:156/:164`、`AiFailoverGatewayInterceptor:126-144` | 已立案 C3-010 | 不重复 |
| `CacheEvictActionDecorator` thenRun | 已立案 CL-009 | 不重复 |
| `AuthHttpServerFilter:374-377` | 已立案 C3-011 | 不重复 |

结论：C3-002 失效面在 graphql 引擎内收敛于 2 处（原立案），**补漏 2 个独立新站点**（fetcher 层 TransformFetcher/EvalActionTransformFetcher，立案 C3-015）+ 2 个 gateway 侧同族站点（C3-015 族/CL-020）。

### 1.3 C3-003：gateway 其他流式路径排查

gateway 内触碰 Flow API 的文件全集 4 个（grep 实证）：`GatewayHttpFilter`、`StreamingResponse`、`BufferedStreamingPublisher`、`StreamingProcessor`。结论：

1. **`StreamingProcessor.createMappedPublisher:137-193` 是两条路径（缓冲/直通）共享的自驱动泵**：`:144` onSubscribe 与 `:162` onNext 处无条件 `subscription.request(1)`——第 1 轮将其标注为「直通态由服务器适配层缓冲吸收」的同族差异，实测其与 BufferedStreamingPublisher **共用同一段代码**，缓冲态的越窗缓冲（`BufferedStreamingPublisher.handleItem` demand==0 分支，live 复核 `:264-266` 不变）与直通态的自驱动拉取同源于此。失效面收敛正确但应升级表述：修复点在 createMappedPublisher（改为 demand 门控）即可同时覆盖两态。
2. **新发现（并入 C3-003 F3）**：`onNext` 的 catch 分支（`:164-166`）转 `subscriber.onError(e)` 后**不调用 `subscription.cancel()`**——后续不再 request(1)，按需求归零上游应停摆，但上游 HTTP/SSE 连接无人取消，滞留至上游自行完成或超时；错误终态路径缺上游回收。
3. `GatewayHttpFilter.stringPublisher:200-234` **不是** driver：成功转发后 `return` 不 request(1)（`:217-218`），仅 null 序列化时补 request(1)（`:221`）——该层正确交还 demand 控制权；断连 abort 挂接（`:239-241`）与 `StreamingResponse.abort`（AtomicReference + cancel）live 不变。`writeStreamingResponse` 的 `whenComplete → abort()` 在成功完成后也触发 cancel（Flow 规范下 complete 后 cancel 为 no-op，无害）。
4. 非 AI 上游：`executeStreaming` 适用于任何配置了 streaming 的路由，上游统一为 `httpClient.fetchServerEventFlow`（SSE）——无第二套流式实现，无其他自驱动形态。

### 1.4 C3-005：onBeforeComplete/onAfterComplete 触发点全枚举 + 不对称面

回调基建：`ExecutionContextImpl`（nop-core）——`complete()` = `fireBeforeComplete()` + 置 done + `fireAfterComplete(null)`（`:108-130`）；`completeExceptionally()` = 置 done/error + `fireAfterComplete(exception)`，**跳过 fireBeforeComplete**（`:132-139`）——结构性不对称：注册于 onBeforeComplete 的清理回调在异常完成路径**永不执行**（机制上因 fireBeforeComplete 会 re-throw context error 而无法在异常路径调用，但后果是 before/after 清理语义分裂）。本单元内无 onBeforeComplete 注册点（grep 实证，注册方在范围外模块），故该不对称的实际受害面在范围外，登记为对照事实。

触发点全枚举（本单元）：

| 触发点 | 路径 | 判定 |
|--------|------|------|
| `GraphQLEngine.buildGraphQLResponse:659` / `buildRpcResponse:685` | 一次性请求收尾（HTTP GraphQL/REST、gRPC（经 executeRpcAsync→toRpcResponse）、JSON-RPC、文件服务全部经此）成功+异常均触发 | ✓ 对称 |
| `GraphQLSubscriptionPublisher.TransformingSubscriber.onComplete:82` / `RpcSubscriptionPublisher.onComplete:80` | 订阅正常完成 | ✓ |
| publisher onError → `buildXxxResponse(null, throwable, context)` → completeExceptionally | 上游错误 | ✓ |
| **`TransformingSubscriber.cancel`（两 publisher `:52-54`/`:52-54`）** | 下游取消（WS unsubscribe/断连级联） | **✗ C3-005 本体，live 不变** |
| **`TransformingSubscriber.onNext` catch（GraphQL 版 `:67-70`、Rpc 版 `:65-68`）** | 逐条消息转换/发送异常 → `downstream.onError(e)` + `upstreamSubscription.cancel()`，**自身不 completeExceptionally** | **✗ 新增缺口**（补入 C3-005 F3：错误语义只覆盖「上游 onError」一种，转换侧异常的 context 永达终态） |
| `GraphQLSubscriptionManager.unregisterSubscription:109-124` / `destroy:264-280` | 仅 cancel Flow 订阅 | ✗（C3-005 ③面，live 不变） |
| `GraphQLServerCallHandler.onCancel` | gRPC 取消 | ✗（C3-009，live 不变） |
| `JsonRpcWebSocketHandler.cancelOperation:338-347` | WS 取消 | ✗（C3-005 同链，live 不变） |

结论：三条终态路径（complete/error/cancel）+ 一条新增的「transform 异常」路径中，context 收尾仅覆盖前两条；取消与转换异常两条缺口维持/扩大 C3-005 失效面。

### 1.5 C3-007：其余裸 executor.execute 站点

全单元 grep（`executor.execute(`/`executeForPartition` 等 8 种形态）：**唯一站点 `BizActionService`**。live 复核：`:87`（`executeForPartition(hash, task)`，bizSequential 分支）与 `:90`（`execute(task)`）**双分支均裸提交**，无 context 快照传播、无 finally detach——第 1 轮双站点表述 live 一致。`:92-94` 的 `catch(Throwable) → completeExceptionally` 覆盖提交失败 ✓。`newInvocation:100` 的 `new ServiceContextImpl()` 在 worker 线程触发 `getOrCreateContext()` 附着（残留/新建）机制 live 不变。对照面：`ExecutorHelper:143/:167/:192` 为**全仓库 main 源码唯一的 detachContext 调用点**（grep 实证），C3-007「平台约定被绕过」的定性成立且修复入口唯一（改用受管提交或补传播+detach）。

---

## 二、② CL-018 核对：热路径 syncGet 站点的中间 future 泄漏面

**原语语义**（api-core，仅对照）：`FutureHelper.syncGet:158-175` → 全部路径最终 `ContextProvider.getOrCreateContext().syncGet(future)`；`ContextTaskQueue.syncGet:46-105` 的 `queueReady.await()` 无超时，唤醒源仅两个：promise 完成（whenComplete 内 `signalAll`）或 enqueue 时 `syncing>0`。关键兜底：`BaseContext.runOnContext:228-246` 在 `enqueue` 返回 false（无等待线程）时**由投递线程附着 context 后就地 `flush()`** 并 save/restore 线程上下文——队列任务不存在「无人执行」的滞留态；`beginProcess:175-188` 对 `processingThread == current` 放行（可重入），嵌套 syncGet 不自锁。

**站点逐一判定**（本单元 7 处）：

| 站点 | 调用链 | 中间 future 泄漏→永久挂起可达性 |
|------|--------|------------------------------|
| `GraphQLExecutor:618` | fetchNext 列表字段异步值聚合 | **不可达**：syncGet 的每个 future 均在 `waitAll(promises)` 同批内（同一对象引用），waitAll 完成即全部完成，syncGet 就地返回 |
| `IGraphQLEngine:101/:110`（executeGraphQL/executeRpc 同步桥） | 调用方 `BizActionInvoker.invokeGraphQLSync:99`、`GraphQLCommandExecutor:53` | **条件可达**：挂起 ⇔ 内部 executeRpcAsync 链的某个中间 future 永不完成。本单元内的无条件泄漏源不存在；条件源 = C3-001 修正机制的递归泵吞没（需异步批+同步抛组合）、C3-015 异步 fetcher 的上游 future 无超时、外部 RPC 客户端超时未配置（范围外） |
| `CrudBizModel:2265/:2280`（fetchSelection/fetchSelectionForList） | `GraphQLEngine.fetchResultWithSelection:876-891`（内层新 GraphQLExecutionContext 包同一 IServiceContext，thenFetchNext 挂接内层 context） | **条件可达**：同上。注意 syncGet 走调用线程当前附着 context 的队列，`BaseContext.execute` 兜底保证内层 thenOnContext 任务由本线程或投递线程执行——队列滞留型挂起不存在 |
| `ILoginService:170` | 单元内 grep 无调用点（外部模块桥方法） | 本单元不可达 |
| `IFileStore:20` | 本地实现同步完成 | 条件依赖 store 实现 |

**判定结论**：「中间 future 泄漏即永久挂起 HTTP worker」在本单元**无条件不可达**；条件化可达路径全部经由已立案缺陷（C3-001 修正面）或范围外的客户端超时配置。api-core 本体（await 无超时）的缺陷归属维持不变，本单元使用站点**无新增立案**，登记 P3 备忘：对 `executeRpc` 同步桥调用点补充「依赖内部链全部 future 有界完成」的前置假设说明。

---

## 三、③ 正常完成路径的上下文恢复对称性

**立案新发现 C3-013（P1，见下）**。其余恢复面盘点：

1. `IServiceContext.invokeWithBindingCtx`（`IServiceContext.java:159-176`）：bind 于调用线程初始段、`restoreCtx` 挂 whenComplete 于完成线程——操作对象是 context **attribute**（跨线程可见），恢复对称 ✓。
2. `ForwardProcessor.executeAndRestore:86-108`：sync 抛出（catch→restore→rethrow）与异步完成（whenComplete→restore）双路恢复 ✓——本单元正面样本。
3. `BaseContext.runOnContext:231-245` / `executeWithContext:249-271`：附着前保存 oldCtx、finally 恢复 ✓（api-core 对照）。
4. **HTTP 入口 binder 恢复面**：`runGraphQL:109`/`runRest:270` 的 `whenComplete → binder.close()` 在**完成线程**执行，而 `binder.init` 的效果（经 ctor 的 `newContext(true)`）落在**入口线程** ThreadLocal——init/close 线程错位 + `BaseContext.close()` 不 detach + `BaseContextProvider.getOrCreateContext` 的 isClosed 换新语义，三者复合形成 C3-013（详见发现条目）。
5. `TccContextInvoker` 的恢复在完成线程执行 `TccContext.setCurrent(oldContext)`（M15，C3-008 已立案，live 不变）。

---

## 四、④ 订阅 execute 链异步生命周期

### 4.1 GraphQLSubscriptionManager（A5 消息桥）

- **新立案 C3-014**：`registerSubscription:84-104` 的 `size()>=max` 检查、`containsKey` 检查与 `put` 三步非原子（ConcurrentHashMap 不消除 check-then-act）：并发双注册同 operationId 时双方均通过 containsKey，后者 `put` **静默覆盖**前者——`subscriptionsByOperation` 中前者残留、`subscriptionsById` 中前者被顶替。孤儿订阅的全部取消通道（`unregisterSubscription:110` 按 id remove、`cancelSessionSubscriptions:130-133` 遍历 subscriptionsById、`pushToClient:206-208` 的 closed-session 清理）均经 `subscriptionsById` 寻址——**孤儿不可达任何终态**：继续被 `onMessage→findMatchingSubscriptions:196-198` 命中、继续向已死会话 push、`SubscriptionInfo.executionContext` 永不 complete（与 C3-005③同宿主）。operationId 为客户端 WS 消息 id（客户端可控），重连风暴同 id 双发可达。对比：`JsonRpcWebSocketHandler.validSubscription:330-334` 对重复 id 显式 4409 断连（fail-loud），管理器侧无对等守卫。
- execute 数据流错误传播：`onMessage:180-187` 逐订阅 try/catch（push 失败仅日志，订阅保留——半开会话依赖 keepAlive/对端关闭兜底，CL-001 族已覆盖滞留机制）；`pushToClient` 的 closed-session 自动注销是孤儿清理的唯一自愈通道（仅覆盖 session 关闭、不覆盖上述 id 顶替）。

### 4.2 JsonRpcWebSocketHandler（A4 WS 订阅链）

- **execute future 异常落点**：同步段异常（subscribeRpc 内 auth/validation/未知操作抛出）穿透 `onMessage:160` 的 `catch(IOException)` 杀死整条连接（C3-006a live 不变）；**异步段**异常落点分两层——上游错误经 `RpcSubscriptionPublisher.onError:72-76` → `buildRpcResponse(null, throwable, context)` → `completeExceptionally` + 降级为 error 帧 + complete 帧 ✓；**转换层异常**（`TransformingSubscriber.onNext:58-69` 的 build/发送异常）→ `downstream.onError` + cancel 上游，但 `SubscriptionSubscriber.onError:476-480` 只移除注册表条目**不发任何帧**（C3-006b live 不变）且该路径 context 不 complete（C3-005 F3 新增缺口，见 1.4）——客户端对错误订阅永久等待且上下文泄漏。
- **断连后的执行终止**：`onClose:120-129` → 逐 activeOperations `cancelOperation` → `SubscriptionSubscriber.cancel:497-502` → 包装层 `cancel:51-53` → 仅 `upstreamSubscription.cancel()`（C3-005 本体，context 不 complete）；`onNext` 的 session-closed 分支（`:470-472`）仍不 cancel 不 remove（C3-006c live 不变）。上游终止依赖 Flow cancel 语义传导至 `LocalMessageService`/消息订阅——链路本身成立，缺口集中在 context 终态与错误帧。
- keepAlive（`:82-84`/`:357-363`）catch IOException only（C3-006d live 不变）；`onThrowable:112-117` 与 `onClose` 双点取消定时器 ✓。

---

## 五、⑤ 22 站点传播矩阵抽验（5 行，含已立案 3 行）

| 行 | 矩阵判定 | live 复核结果 |
|----|---------|--------------|
| M2（invokeOperation 出口泵 ✗ → C3-002） | ✗ | **一致**：`:135-147` 无 thenOnContext（该分支新增的 P1-2.6-004 注释只补了调度、未补挂接） |
| M10（callActionAsync 裸提交 ✗ → C3-007） | ✗ | **一致**：`:87`/`:90` 双分支裸提交、无 detach |
| M15（TccContextInvoker △ → C3-008） | △ | **一致**：`invokeAsync:22-37` 结构 live 原样（thenOnContext 无参版 + 完成线程恢复） |
| M20（FlowControlFetcher/Runner「同线程门控 ✓」） | ✓ | **判定一致但豁免结论修正**：`DefaultFlowControlRunner.runAsync:36-46` 确无切线程，但 `rateLimiter.tryAcquire(1, maxWaitMillis)` 在调用线程（context 队列线程）**阻塞等待**——新立案 C3-016 |
| M14（AuthHttpServerFilter runOnContext ✓） | ✓ | **一致**：`:357` runOnContext 绑定请求 context 执行；附带复核 C3-011 flush fire-and-forget live 不变（`:374-377`） |

5/5 与 live 一致；1 行（M20）的「豁免」结论被本轮新证据修正。

---

## 发现条目（本轮新增）

### [C3-013] ContextBinder 复用分支在预附着 context 上下文中跳过请求级绑定，且 close 不 detach：认证路由请求级 tenant/timeout 绑定恒失效、非认证路由线程残留 closed context 致后续请求租户丢失

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/web/GraphQLWebService.java:75-109（runGraphQL）/:238-271（runRest）`；缺陷原语 `nop-network/nop-rpc/nop-rpc-api/src/main/java/io/nop/rpc/api/ContextBinder.java:24-42`（范围外原语、本单元使用站点立案）；复合条件 `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/filter/AuthHttpServerFilter.java:350-357,556-579`；对照 `nop-kernel/nop-api-core/.../BaseContextProvider.java:36-54`、`BaseContext.java:315-341`
- **证据片段**:
  ```java
  // ContextBinder —— 复用分支 init 直接跳过
  public ContextBinder() {
      IContext context = ContextProvider.currentContext();
      if (context == null) { this.context = ContextProvider.newContext(); created = true; }
      else { this.context = context; created = false; }        // 预附着（含 closed）→ created=false
  }
  public ContextBinder init(ApiRequest<?> request) {
      if (created) initContext(context, request);              // ★ 非.created 时 tenant/locale/timeout 全不写
      return this;
  }
  public void close() { if (created) context.close(); }        // 只 close 不 detach；close 挂在完成线程
  // AuthHttpServerFilter.initUserContext:566 —— 认证过滤器先行建立并附着每请求 context
  IContext ctx = context.getContext(); ctx.setTenantId(...); ...
  ctx.runOnContext(() -> { ... next.get() ... });              // 下游（GraphQL 端点）在该 context 上执行
  // BaseContextProvider.getOrCreateContext —— closed 残留触发换新
  if (context == null || context.isClosed()) { return newContext(true); }
  ```
- **严重程度**: P1
- **并发场景**: HTTP 线程池复用 ×（认证过滤器先行建立 context 的路由 / 无过滤路由的第二次及以后请求）；请求处理跨 entry 线程与完成线程。
- **触发时序**: 面 (a)——认证路由（默认部署的主路径）：T1 `AuthHttpServerFilter.handleUserContext` 经 `initUserContext` 取 `IHttpServerContext.getContext()` 的每请求 context 并 `runOnContext` 执行下游链（`BaseContext.runOnContext:231-235` 附着该 context 到执行线程）。T2 GraphQL 端点 `runGraphQL/runRest` 在该线程构造 `new ContextBinder()`：`currentContext() != null`（认证 context，open）→ `created=false` → **`binder.init(req)` 直接 return**——请求头 `nop-tenant`/`nop-locale`/`nop-timeout` 从不落入 context；线程 context 值来自认证过滤器写入的**会话级** userContext 值，且 `callExpireTime` 全链无人设置（单元内 grep `setCallExpireTime` 仅生成代码，无请求级写入）→ 请求级超时**永不武装**，请求头租户/locale 无法与会话值区分。面 (b)——无认证过滤的路由：T1 首请求线程 T 上 binder 创建并附着 CA → 完成线程 `binder.close()` 使 `CA.closed=true`（`BaseContext.close:315-325` 不 detach，CA 残留附着 T）。T2 线程 T 的第二个请求：binder ctor 见 CA（closed，`currentContext()` 不做 isClosed 过滤）→ `created=false` → init 跳过；`newGraphQLContext→ServiceContextImpl` 构造器 `getOrCreateContext()` 见 `CA.isClosed()` → **新建空 context CB**（仅 CFG_DEFAULT_TENANT_ID 默认租户）→ 本请求以空租户/无 locale/无超时执行——ORM 租户过滤（`TenantParamBuilder`、`TenantOrmSessionEntityCache`、`EntityPersisterImpl` 等经 `ContextProvider.currentTenantId()` 读线程上下文，grep 实证）读到默认/空租户；第三个请求起 binder 见 open CB → 继续跳过且**复用 CB**（跨请求 attribute 残留）。
- **现状**: 全仓库 main 源码 detachContext 调用点仅 `ExecutorHelper:143/:167/:192`（池任务约定），HTTP 路径无任何 detach/清理；ContextBinder 的 created 判定未做 `isClosed` 过滤、init 无「复用时合并请求头」的兜底分支。
- **风险**: 认证路由：请求级 timeout 头静默失效（F5 超时面）、请求头租户/locale 与会话值偏离时无法表达；非认证路由 + 线程复用：多租户部署下后续请求以默认租户执行租户过滤（数据可见性错乱），error message locale 退化，跨请求 context attribute 残留。每线程首个请求之后的**全部**请求受面 (a) 或 (b) 之一影响。
- **建议**: ContextBinder ctor 对复用分支增加 `isClosed` 判定（closed 即换新）；init 改为「复用时也将请求头 tenant/locale/timeout 写入当前 context（先 save 后 restore）」或 GraphQLWebService 侧改用 `executeWithContext` 显式建立请求级 context 并在响应完成回调中恢复；同时为 HTTP 路径确立 detach 约定（对照 ExecutorHelper）。修 ContextBinder 属跨模块 API（nop-rpc-api），按 Protected Areas 需 plan-first；本单元侧可先行在 runGraphQL/runRest 包一层请求级 context 生命周期。
- **失效面枚举**: ContextBinder 全部使用点：`GraphQLWebService.runGraphQL:76/:87-91/:109`、`runRest:239/:262-266/:270`（✗ 本条双站点）；同原语其他消费方（范围外模块 grep 命中 nop-rpc 客户端侧，仅对照）；复合条件面：`AuthHttpServerFilter.initUserContext:556-579`（认证 context 先行建立，使面 (a) 成为认证路由默认行为）、`BaseContextProvider.getOrCreateContext:41-46`（closed 换新语义）、`BaseContext.close:315-325`（不 detach）；豁免面：`invokeWithBindingCtx`（attribute 层，对称 ✓）、`ExecutorHelper` 三点（池任务已守约）。
- **信心水平**: 很可能（机制链全部代码直证：ctor 判定/init 跳过/close 不 detach/全仓无 HTTP detach/ORM 读点实证；实害的部署触发面——认证过滤器是否覆盖 GraphQL 路由——依 beans.xml 过滤器装配，标注条件）
- **误报排除**: 非「原语在范围外不立案」——按任务口径缺陷归属本单元使用站点（runGraphQL/runRest 的绑定责任）；非「复用 context 是设计意图」——设计意图（注释「如果当前有线程正在执行」类）至多解释 close 不关他人 context，无法解释**请求头绑定被跳过**与 closed context 复用两处行为；非 C3-012 的入口 binder 缺失（那是 RPC 风格入口无 binder，本条是 HTTP 入口**有 binder 而失效**）。
- **复核状态**: 已复核（独立复核 2026-10-18）——**采纳第 3 轮改判，P1→P3 终裁**。决定性反证三环节全部独立核实：(a) `biz-defaults.beans.xml:122-127` 无条件注册 `nopContextHttpServerFilter`（on-class 条件恒真，quarkus/spring 均加载 biz-defaults）；(b) `ContextBinder` ctor `:25-34` 以 `currentContext()==null` 二分（BaseContextProvider.currentContext `:22-24` 裸 `t_context.get()` 无 isClosed 过滤），B2 复用分支 init/close 全跳过；(c) `ContextHttpServerFilter.filterAsync`（order=HIGH_PRIORITY=1000 < NORMAL_PRIORITY=5000，IOrdered:11/13）`:41-45` `newContext()`=`newContext(true)` 附着 + `initContext:62-87` 从请求头绑定 tenant/locale/timezone/callExpireTime；装配面实测 quarkus `HttpServerFilterRegistrar:33`（enabled 默认 true，filters 注册于 vertx Filters 先于路由）与 spring `SpringHttpServerFilterConfiguration`（matchIfMissing=true）。**面覆盖反转成立**：默认部署下五模块全部 HTTP 入口到达 GraphQLWebService 前线程必已附着携带请求级绑定的每请求 context → binder 恒走 B2 → init/close no-op 死代码；第 2 轮面 (a)（绑定恒失效）与面 (b) 的 B1 可达前提均被推翻；面 (b) 仅在 `nop.web.http-server-filter.enabled=false` 等非默认配置下成立。P3 终裁（死代码契约隐患 + 非默认配置面）；**修复方向警示采纳**：若按本条第 2 轮建议改 init 复用分支，将在认证路由以 header tenant 覆写 session tenant（安全降档），修复应改为「声明 ContextHttpServerFilter 为唯一绑定 owner、GraphQLWebService 侧废除 binder 依赖」。runProxy 第三站点补正确认（`:188-225` 实测）。现状：新发现/未修复。

### [C3-014] GraphQLSubscriptionManager.registerSubscription check-then-act 竞态产生不可达终态的孤儿订阅：id 顶替后全部取消通道失效，孤儿持续接收推送且上下文永不 complete

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/subscription/GraphQLSubscriptionManager.java:84-104（register）/:109-124（unregister）/:129-136（cancelSession）/:196-198/:204-218（onMessage/pushToClient）`
- **证据片段**:
  ```java
  if (subscriptionsById.size() >= maxActiveSubscriptions) { throw ...; }   // 检查1
  if (subscriptionsById.containsKey(operationId)) { throw ...; }           // 检查2（非原子）
  subscriptionsById.put(operationId, subscription);                        // 覆盖式写入
  subscriptionsByOperation.computeIfAbsent(...).add(subscription);         // 永远追加
  // unregister —— 唯一按 id 寻址的取消通道
  SubscriptionInfo subscription = subscriptionsById.remove(operationId);
  ```
- **严重程度**: P2
- **并发场景**: 两个 WS 会话（或同会话重连窗口）以相同 operationId（客户端可控的 JSON-RPC id）并发订阅同一操作；`registerSubscription` 由 WS 接收线程并发调用。
- **触发时序**: T1 线程 A/B 同时为 id=X 执行 register：双双通过 `containsKey` 检查（CHM 弱一致性 moment 见空）。T2 A put(subA)、B put(subB)——subB **静默顶替** subA（无异常，ERR_GRAPHQL_SUBSCRIPTION_DUPLICATE_OPERATION 分支被绕过）；`subscriptionsByOperation["op"] = [subA, subB]` 两条并存，`subscriptionsById["X"] = subB`。T3 取消面：客户端 unsubscribe X 或断连 `cancelSessionSubscriptions` 或 pushToClient 的 closed-session 清理——全部经 `subscriptionsById` 定位到 **subB**；subA 仅存于 ByOperation 列表：继续被 `onMessage→findMatchingSubscriptions`（遍历 subscriptionsById.values？——**实测 findMatchingSubscriptions 遍历 subscriptionsById**（`:196`），subA 已不在其中，推送面收窄为「ByOperation 残留 + executionContext 泄漏」；但 `getSubscriptionsByOperation`（`:148-150`）对外返回含 subA，消费方按该列表操作时作用于死订阅。T4 subA 的 Flow 订阅与 `SubscriptionInfo.executionContext` 永达终态（唯一 channel=destroy() 全量清理），长时运行累积。
- **现状**: 同仓库 `JsonRpcWebSocketHandler.validSubscription:330-334` 对重复 id 采 4409 断连（fail-loud）；`BeanMethodBatchFetcher:61-63`/`OrmBatchLoader.makeDataLoader:43-53` 对同型 check-then-act 已用「与 dispatchAll 同锁串行化」修复（本分支在案先例）——管理器是同族站点中唯一未加原子守卫的。
- **风险**: 订阅计数（maxActiveSubscriptions）与实际活跃流不一致；ByOperation 视图含死订阅；`SubscriptionInfo.executionContext`（含消息级资源）永不清理。触发需同 id 并发（客户端 id 冲突/重连风暴），条件化。
- **建议**: register 改 `subscriptionsById.putIfAbsent(operationId, sub)` 判返回值非 null 即抛 DUPLICATE（消除检查窗口）；或按仓内先例与 WS handler 同锁。`computeIfAbsent(...).add` 与 put 的两 map 一致性在同一 synchronized 内完成。
- **失效面枚举**: 订阅注册/注销家族：`GraphQLSubscriptionManager.registerSubscription`（✗ 本条）、对照 `JsonRpcWebSocketHandler.validSubscription`+`sendStreamingMessage`（register-then-subscribe 注释已处理同步 publisher 时序，重复 id 走 4409 ✓）、对照 `GraphQLMessageSubscriptionRegistrar`（IMessageService 订阅，注册点在 start() 一次性，无并发注册面 ✓）；清理通道面：`unregisterSubscription`/`cancelSessionSubscriptions`/`pushToClient` 自动清理/`destroy`（前三者 id 寻址受本条影响，destroy 全量兜底 ✓）。
- **信心水平**: 确定（机制直证；孤儿推送面经实测 findMatchingSubscriptions 遍历 id 表而收窄，已如实降格为视图污染+资源泄漏）
- **误报排除**: 非「CHM 线程安全所以无竞态」——size/containsKey/put 三步复合非原子是经典 check-then-act；非 C1 维度重复——本条立足点是**订阅终态机缺口**（取消通道不可达），共享状态仅是机制载体；C1/C2 轮立案清单未含此站点的该形态（按任务说明仅对照编号，未复核内容，如与既有条目重合由裁定合并）。
- **复核状态**: 已复核（独立复核 2026-10-18）——**采纳改判，P2→P3 终裁（dormant）**。机制在码证实：size 门 `:87-89` / containsKey `:92-95` / put `:97` / computeIfAbsent+add `:98-100` 三步非原子；findMatchingSubscriptions 遍历 subscriptionsById（`:196-198` 实测）的孤儿推送面收窄成立。**零生产调用方独立 grep 证实**：`registerSubscription` 在全仓 main 源码唯一引用为 `subscription-defaults.beans.xml:10` bean 定义 + 测试类（ReflectionMessageSubscriptionRegistrar 的 registerSubscriptionBean 为无关同名词）——出厂接线注册表恒空，并发双注册触发前提不存在，与同设施 C1-003 的复核结论（零调用方）交叉一致。P3 dormant 终裁。**合并裁定**：机制本体（putIfAbsent + 双 map 同锁）归并 C1-003/C2-005 设施工单（同设施双维已立案）；本条差异化 payload——终态机缺口（孤儿对 unregister/cancelSession/pushToClient 三取消通道不可达 + SubscriptionInfo.executionContext 永不 complete）——合并时必须随迁。现状：新发现/未修复（dormant 设施）。

### [C3-015] fetcher 层异步出口缺 thenOnContext：TransformFetcher/EvalActionTransformFetcher 的 transformer 于上游完成线程执行用户表达式（携带 env/EvalScope），C3-002 同族独立站点

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/fetcher/TransformFetcher.java:30-40`；`EvalActionTransformFetcher.java:29-38`（同构）；族内对照 `RpcServiceOnGraphQL.java:73-75`、`RouteExecutor.java:74-81`
- **证据片段**:
  ```java
  // TransformFetcher.get —— transformer 运行于 value 的完成线程
  public Object get(IDataFetchingEnvironment env) {
      Object value = fetcher.get(env);
      if (value instanceof CompletionStage) {
          return ((CompletionStage<?>) value).thenCompose(v -> {
              Object ret = transformer.apply(v, env);   // ★ 无 thenOnContext 挂接
              return FutureHelper.success(ret);
          });
  ```
- **严重程度**: P2（条件化：实害依赖上游 fetcher 为异步——远程 RPC fetcher 或返回 CompletionStage 的 biz action；同步 fetcher 时 thenCompose 内联，豁免）
- **并发场景**: 带转换的字段（XPL transform 表达式/transformer 用户代码）其上游 fetcher 返回异步值；完成线程为 RPC 客户端 IO 线程或 biz worker（C3-007 场景下为残留 context 的池线程）。
- **触发时序**: T1 字段 fetcher 返回 CompletionStage（如异步 biz action 经 RpcServiceOnGraphQL/callActionAsync）。T2 上游 future 在 IO/worker 线程完成 → `transformer.apply(v, env)` 于该线程执行：transformer 是闭包携带的函数（`BeanMethodBatchFetcher` 同款 `function.invoke(bean, args, context.getEvalScope())` 形态或 XPL 表达式），执行中读取 `env`（selectionBean/opRequest）与 `env.getContext().getEvalScope()`——EvalScope/IServiceContext 的线程亲和语义未声明（与 CL-009 CacheEvictActionDecorator 同形态）；若 transformer 内经 `ContextProvider.currentXxx()` 读线程上下文则读到空/残留（与 C3-013 残留面复合）。T3 同族：`RpcServiceOnGraphQL.invokeAsync0` 的 `thenCompleteAsync`（实为完成线程就地执行，`FutureHelper.java:334-348`）于完成线程触碰 `ctx.getServiceContext()`；`RouteExecutor.execute:74-81` 的 interceptor onResponse/onError 回调于完成线程执行拦截器用户代码。
- **现状**: 引擎行走层（thenFetchNext）已确立 thenOnContext 治理，fetcher 装配层与 gateway 延续层未对齐；`GraphQLExecutor` 的 P1-2.6-004 修复注释只覆盖调度未覆盖挂接。
- **风险**: 异步 fetcher + transform 字段组合（生产常见）间歇性在错误线程求值：轻则 locale/租户读取失真，重则 EvalScope 并发访问未定义行为；gateway 拦截器 onResponse/onError 于 IO 线程执行时写共享状态（IGatewayContext attribute）无序列化保证。
- **建议**: TransformFetcher/EvalActionTransformFetcher 的 thenCompose 前包 `ContextProvider.thenOnContext(value, env.getContext())`（与 thenFetchNext 对齐）；RouteExecutor 的 interceptor 延续同挂接（gateway 侧 context 语义按 C3-012 结论补入口 binder 后自然收敛）。
- **失效面枚举**: fetcher 装配层全部异步出口：`TransformFetcher:33`（✗ 本条）、`EvalActionTransformFetcher:33`（✗ 本条）、`FetcherHelper.processResponse`（同步映射 ✓）、`DictLabelFetcherProvider.loadLabel:70`（同步字典加载 ✓）、`ConvertTypeFetcher`（同步 ✓）、`FlowControlFetcher:32`（门控后就地执行 ✓ 门控本体另立 C3-016）、`BeanPropertyFetcher`/`BeanMethodAction`（同步 invoke ✓）；gateway 延续族：`RouteExecutor:74-81`（✗ 同族登记）、`RpcServiceOnGraphQL:73-75`（△ 映射为主）；引擎层两处已立案（C3-002）不重复。
- **信心水平**: 很可能（缺挂接直证；transformer 具体读取面依模型装配的 transform 定义而定，已列可核查读取点）
- **误报排除**: 非「thenCompose 于完成线程是 CompletableFuture 常态」——对照同文件族 `thenFetchNext:152` 已确立挂接标准，此处为遗漏而非约定；非 CL-009 重复——CL-009 是 cache 装饰器特定场景，本条是 fetcher 层通用出口缺失（F3 全枚举归本条）。
- **复核状态**: 已复核（独立复核 2026-10-18）——**维持 P2（条件化）**。证据逐行证实：`TransformFetcher:32-36` / `EvalActionTransformFetcher:31-35` 的 thenCompose 内 transformer/EvalAction 于上游完成线程执行、无 thenOnContext 挂接；与 `thenFetchNext:152` 的既有挂接标准形成同文件族不一致。条件化（上游 fetcher 返回 CompletionStage）如实标注；全 rpc 层 grep 复核确认无其他 thenOnContext 兜底（nop-graphql 内仅 GraphQLExecutor:152/:511 与 TccContextInvoker 两类挂接点）。与 C3-002 修复统一挂接治理。现状：新发现/未修复。

### [C3-016] FlowControl 门控在调用线程同步阻塞 tryAcquire(maxWaitMillis)：GraphQL fetcher 于 context 队列线程限流等待，序列化执行模型将单请求限流等待放大为引擎级停顿窗口

- **文件**: `nop-network/nop-rpc/nop-rpc-core/src/main/java/io/nop/rpc/core/flowcontrol/DefaultFlowControlRunner.java:36-46`（范围外原语、本单元使用站点立案）；使用站点 `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/engine/FlowControlFetcher.java:31-34`、`GraphQLFlowControlInvoker.java:33-36`
- **证据片段**:
  ```java
  // DefaultFlowControlRunner.runAsync —— 签名异步、实现同步阻塞
  public <T> CompletionStage<T> runAsync(FlowControlEntry entry, Supplier<CompletionStage<T>> task) {
      boolean acquired = rateLimiter.tryAcquire(1, maxWaitMillis);   // ★ 调用线程阻塞至 maxWait
      if (acquired) { return task.get(); }
      return FutureHelper.reject(new NopException(ERR_RPC_FLOW_CONTROL_REJECT));
  }
  // FlowControlFetcher —— GraphQL 字段游走内调用（thenOnContext 序列化后的队列线程）
  return runner.runAsync(entry, () -> FutureHelper.futureCall(() -> fetcher.get(env)));
  ```
- **严重程度**: P2（条件化：需限流阈值饱和 + maxWaitMillis>0 配置）
- **并发场景**: 高并发下某资源限流饱和；多个并发请求的 fetcher 经 `GraphQLFlowControlInvoker`/`FlowControlFetcher` 在各自的 context 队列线程上 tryAcquire。
- **触发时序**: T1 请求 R1 的字段 fetcher 触发限流等待：context 队列线程（经 thenOnContext 序列化执行 fetcher 的线程）阻塞 `tryAcquire(1, maxWaitMillis)`——该线程同时是 R1 全部序列化任务的唯一执行者。T2 等待期间 R1 的其他字段延续、响应收尾（buildXxxResponse）、onBeforeComplete/onAfterComplete 清理回调全部排队。T3 并发 R2..Rn 命中同一限流器 → 各自队列线程同时阻塞 → 工作线程池被「等待许可证」占满而非「执行业务」——吞吐在限流饱和时断崖下降，等待上限 maxWaitMillis 内无任何让出。T4 `runAsync` 返回类型为 CompletionStage，调用方（GraphQLExecutor 链）无法感知阻塞形态，无超时/取消挂接（C3-010 取消缺失族：客户端断连不中断等待）。
- **现状**: `DefaultFlowControlRunner` 是 `IFlowControlRunner` 的默认实现（graphql 引擎构造 `newGraphQLExecutor:893-895` 直接注入）；runAsync 的「异步签名 + 同步阻塞实现」使阻塞不可见于调用链。
- **风险**: 限流饱和场景的可用性放大器：本意「快速拒绝」的限流器退化为「每请求阻塞 maxWait」的减速带；与 C3-010（取消不传播）复合后等待不可中断。
- **建议**: runAsync 改非阻塞获取（`tryAcquire(0)` 即刻失败或异步令牌等待）；至少在 GraphQL 侧将 maxWaitMillis 配置钳制为小值并文档化「fetcher 门控阻塞 context 队列线程」的代价；取消挂接（客户端断连 → 中断等待）随 C3-010 一并治理。
- **失效面枚举**: 门控使用家族：`FlowControlFetcher:32`（字段级，队列线程阻塞 ✗ 本条）、`GraphQLFlowControlInvoker:33`（操作级，初始同步段阻塞——阻塞发生在入口线程而非队列线程，爆炸半径小一档 △ 同族）、`DefaultFlowControlRunner.run:49-58`（同步签名同步阻塞，语义一致 ✓）、范围外 RPC 调用方（仅对照）。
- **信心水平**: 确定（阻塞语义直证；饱和场景为条件触发，按判级口径 P2）
- **误报排除**: 非「限流器等待是功能」——阻塞**发生在序列化执行的队列线程**上且不可取消，是异步链路对调用线程的错误假设（矩阵 M20 原判「同线程门控 ✓ 豁免」依据本轮证据修正）；非 C8 维度——无队列/背压结构，是阻塞等待形态。
- **复核状态**: 已复核（独立复核 2026-10-18）——**采纳改判，P2→P3 终裁（契约条款）**。三事实独立核实成立：(1) `DefaultFlowControlRunner:18` `maxWaitMillis = 0` 默认，`tryAcquire(1, 0)` 即时失败语义、默认零阻塞；(2) 零接线——全仓 main 无 `new DefaultFlowControlRunner`、无 beans.xml 注册，`GraphQLEngine:127/186` 注入面为 `@Nullable IFlowControlRunner`（第 2 轮「构造时直接注入」表述失准复核确认）；(3) 生产 gate bean = `nopFlowControlRunner` → `SentinelFlowControlRunner`（sentinel-defaults.beans.xml:14-15，无 on-class 守卫、模块在即装配），其 runAsync 用 `SphU.asyncEntry`（:63）非阻塞。**与 C4 轮 CL-011 关系终裁确认**：CL-011（「默认 maxWaitMillis=0 不阻塞，配置化阻塞」不成立终判）与本条实测同向收敛，C3-016 并入其卷宗互注编号；本条残值仅为「runAsync 异步签名 + 实现可配置为同步阻塞且无注释声明」的 API 契约条款（owner doc 修复），P3。现状：新发现/未修复（默认路径无缺陷）。

---

## C3-001 机制修正（不改编号，供裁定降档）

第 1 轮 C3-001 的触发时序（异步批失败 → dispatch future 异常完成 → waitAll 失败 → thenAccept 跳过 → 泵断链）经 3.2.2 字节码对照**被证伪**：`dispatchQueueBatch` 的 `.exceptionally` 处理器吞掉批异常（逐 key `completeExceptionally` + `clear` 后返回空列表），dispatch future 恒正常完成。修正后残留悬挂面：递归泵 `GraphQLExecutor.dispatchAll:203` 的 `thenAccept` 延续内**同步抛出**（唯一来源 `OrmBatchLoader.load` 的 `requireSession`/`supplier.get`）被丢弃 stage 静默吞没——仅当递归延续运行于非内联线程（存在异步批 loader 时聚合 promise 在外部线程完成）才形成「未 dispatch loader → 请求悬挂无日志」；内联级联（纯 OrmBatchLoader）时异常传播出泵、响应 500。修复建议维持（`whenComplete` 失败延续 + 逐 loader 异常隔离 + 日志），判级建议 **P1 → P2**（三重条件：异步批 loader + 同步抛失败 + 非内联递归）。详细字节码证据见本报告 §1.1。

---

## 维度集外线索（不立案展开）

### [nop-service-framework-hotpath-CL-019]（维度集外线索）BeanMethodBatchLoader 的 biz 方法在 context 监视器内同步执行：futureCall 内联运行长业务/DB 操作期间持有 GraphQLExecutionContext 锁，同请求并行分支的字段注册被串行阻塞

`BeanMethodBatchFetcher.get:64-81` 在 `synchronized (context)` 内返回 `loader.load(...)`；`GraphQLExecutionContext.dispatchAll:156-170` 亦为 synchronized——`loader.dispatch()` → `futureCall`（`FutureHelper.java:122-129` **同步 call**）→ `realFetcher.apply` 反射调用 biz 方法（含 ORM/DB 操作）在**持有 context 监视器**状态下执行。同一请求的并行分支 fetcher（`BeanMethodBatchFetcher.get` 同锁）与回合出口泵（dispatchAll 同锁）在长批方法执行期间全部阻塞。属 C2 锁内执行/C9 形态；亦使「上下文队列串行化」之外新增了请求内 context 锁串行点。注：dispatch 的批调用本身在 DataLoaderHelper 监视器**外**执行（字节码 offset 133+），锁载体是 Nop 侧 context 对象。

### [nop-service-framework-hotpath-CL-020]（维度集外线索）gateway 错误路径于完成线程读 currentLocale 且网关无请求级 IContext 绑定：错误消息 locale 取线程残留/默认值，与 C3-013 残留面复合

`GatewayHandler.processRoute:173-175` exceptionally、`GatewayHttpFilter:162/:170` exceptionally 均 `ContextProvider.currentLocale()`；`buildGatewayContext:353-365` 仅建 `GatewayContextImpl`（网关自有上下文，非 IContext），网关转发链无 ContextBinder/runOnContext 挂接——locale 读取落到 HTTP 线程残留 context（受 C3-013 面 (b) 的 closed 残留影响时读到**前一请求**的 locale）。属 C3-012 入口传播家族在 gateway 侧的变体 + 错误路径上下文读取面。

---

## 总结

| 类别 | 数量 | 明细 |
|------|------|------|
| 本轮新增立案 | 4 | C3-013（P1）、C3-014/C3-015/C3-016（P2×3） |
| 既有条目修正 | 1 | C3-001 触发机制被 3.2.2 字节码证伪，残留面收窄，建议降档 P1→P2（§1.1 + 修正专节） |
| 既有条目失效面扩展 | 4 | C3-002（补漏 fetcher/gateway 层出口→C3-015）、C3-003（createMappedPublisher 双路径共享 + onNext 错误分支不 cancel 上游）、C3-005（transform 异常路径不 complete + completeExceptionally 跳过 before 回调的结构性不对称）、C3-007（双分支确认 + 全仓 detach 点唯一性实证） |
| CL-018 核对结论 | — | 本单元 syncGet 站点**无条件不可达**「中间 future 泄漏→永久挂起」；条件化可达路径全部经由已立案缺陷或范围外客户端超时配置；api-core 本体归属不变，登记 P3 备忘 |
| 维度集外线索 | 2 | CL-019（context 锁内执行 biz 批方法）、CL-020（gateway 错误路径 locale 残留读取） |
| 矩阵抽验 | 5/5 一致 | M2/M10/M15/M20/M14；M20 的豁免结论被 C3-016 修正 |

两轮累计（C3 维度）：立案 12（第 1 轮）+ 4（本轮）= 16 条；其中 P1：第 1 轮 5 条中 C3-001 建议降档后 4 条 + 本轮 C3-013 = 5 条；P2 11 条。CL 线索累计 5 条（CL-007~009 + CL-019~020）。

## 终止信号判定

**新增 P1×1（C3-013）→ 按深挖继续规则建议第 3 轮**，建议第 3 轮范围收敛为三项：
1. C3-013 的部署面确认（认证过滤器对 GraphQL 路由的装配覆盖范围、多租户部署的 tenant 读取点清单）与修复方案选型（ContextBinder 原语修复 vs 单元侧请求级 context 生命周期）——涉及 nop-rpc-api 公共原语，需 plan-first 评估；
2. C3-001 修正后的泵行为实测复验（构造异步批 loader + 同步抛组合验证递归泵吞没路径）；
3. C3-014/C3-016 的条件触发面复核（operationId 冲突的实际客户端模式、maxWaitMillis 默认值与生产配置采样）。

除上述外，①~⑤ 各方向的失效面均已收敛至单点或已立案（dispatch 调用点唯一、gateway Flow 文件全集 4 个、complete 触发点全枚举、裸 execute 唯一、syncGet 站点全判定、矩阵 5 行抽验一致）——第 3 轮完成后本维度可终止。

（本报告为第 2 轮深挖盲审产出，全部新增条目复核状态=未复核，待独立复核轮裁定。）
