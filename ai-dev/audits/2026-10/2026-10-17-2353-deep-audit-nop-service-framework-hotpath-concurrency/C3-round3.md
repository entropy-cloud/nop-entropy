# C3 异步链路正确性审计报告（第 3 轮收口，盲审）

## 审计单元

- **维度**：C3——异步链路正确性（异步任务链的异常传播/超时/取消语义；future 转换链断裂；异步回调的上下文传播；订阅/流式链的生命周期）
- **审计对象**：请求热路径五模块（8 源码根）：nop-graphql（core/orm/message/grpc）、nop-biz、nop-gateway、nop-biz-auth-core、nop-biz-file-core
- **仓库**：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`，HEAD 5994c18211）
- **本轮输入**：第 1 轮报告 `C3-async-chains.md`（C3-001~012 + CL-007~009）、第 2 轮报告 `C3-round2.md`（C3-013~016 + CL-019~020 + C3-001 降档建议）+ live code + java-dataloader 3.2.2 字节码（`~/.m2/repository/com/graphql-java/java-dataloader/3.2.2`，本轮以 `javap -p -c` 对 `_tmp/c3r2-dl/` 解包类独立反汇编）+ 部署装配面实证（biz-defaults.beans.xml / auth-service.beans.xml / subscription-defaults.beans.xml / sentinel-defaults.beans.xml / HttpServerFilterRegistrar / SpringHttpServerFilterConfiguration）。

## 盲审纪律声明

本报告为第 3 轮（收口轮）盲审产出。除任务指明的前序轮次文件 `C3-async-chains.md`、`C3-round2.md` 外，未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件（含同目录其他维度文件）。对任务说明中提及的 C1-003、C2-005、C4 轮 CL-011，仅按任务说明中的编号与「同设施」「已终判不成立」的字面口径做关系裁定，未读取、未复核其内容（问③的关系终裁在盲审约束下以本维度自证事实 + 设施归属给出，并标注裁定边界）。所有发现以当前 live code 实测、beans.xml 装配实测与 java-dataloader 3.2.2 字节码独立反汇编为准。越界原语（ContextBinder/BaseContext/ContextHttpServerFilter/SentinelFlowControlRunner 等）仅作语义对照，缺陷归属维持「本单元使用站点」口径。

## 本轮目标（收口四问）

1. **① C3-013 部署面确认**：复用分支可达条件精确化；「每线程首个请求之后的全部请求受影响」量化；AuthHttpServerFilter runOnContext 的认证路由覆盖面；ORM 租户过滤后果链。
2. **② C3-001 降档复核**：java-dataloader 3.2.2 exceptionally 返回空列表实测复核；残留面判级终裁。
3. **③ C3-014/016 条件触发面复核**：C3-014 与 C1-003/C2-005 同设施关系终裁；C3-016 与 C4 轮 CL-011 关系厘清。
4. **④ 快扫**：认证路由之外是否存在第三类 ContextBinder 使用形态（grep 全部 bindSource/bind 调用点）。

---

## 一、① C3-013 部署面确认——重大反转：默认部署下 ContextBinder 恒为 no-op，第 2 轮后果链不成立

### 1.1 ContextBinder 分支精确化（每条分支，live 实测 `ContextBinder.java:25-46`）

```
ctor：branch B1 —— ContextProvider.currentContext()==null
        → newContext()（newContext(true)，【附着】到当前线程）→ created=true
      branch B2 —— currentContext()!=null（BaseContextProvider.currentContext = 裸 t_context.get()，
        【无 isClosed 过滤】——open 与 closed 一律复用）→ created=false
init(request)：B1 → 写 locale/tenantId/timezone/propagateRpcHeaders + timeout>0 时 setCallExpireTime(now+timeout)；
             B2 → 直接 return this（★ 请求级绑定全部跳过）
close()：    B1 → context.close()（BaseContext.close:315-327 —— 先判
                contextHolder.get()==this 才 BaseContextProvider.clear()【条件性 detach】，
                否则仅置 closed + 清 attributes，附着残留）；B2 → no-op
```

任务说明中的「bindSource」在全部 main 源码 grep 为零命中（无该符号）；复用判断的唯一输入是 `currentContext()==null`，不存在第三个分支。第 2 轮「close 不 detach」表述修正为：**close 的 detach 是条件性的（仅当执行 close 的线程其附着 context 恰为该 context），且 close 挂在完成线程上**——同步完成（whenComplete 内联于入口线程）时 detach 成功，异步完成时入口线程残留 closed context。

### 1.2 决定性反证：ContextHttpServerFilter 先于一切在 filter 链内创建并绑定每请求 context

第 2 轮漏检的装配事实（本轮 beans.xml + registrar 实测）：

1. `nop-service-framework/nop-biz/src/main/resources/_vfs/nop/biz/beans/biz-defaults.beans.xml:122-127`：`nopContextHttpServerFilter`（`io.nop.http.api.server.ContextHttpServerFilter`）**无条件注册**（on-class 条件恒真）——quarkus 与 spring 部署均加载 biz-defaults。
2. quarkus：`HttpServerFilterRegistrar`（`nop.web.http-server-filter.enabled` 默认 true）把全部 `IHttpServerFilter` bean 按序注册为 vertx Filters，**先于路由（含 JAX-RS /graphql 端点）对每个请求执行**；spring：`SpringHttpServerFilterConfiguration.NopHttpServerFilter`（urlPatterns `/*`）同链。
3. `ContextHttpServerFilter.filterAsync`（order=HIGH_PRIORITY，**最先执行**）：`ContextProvider.newContext()`（**附着**到 filter 执行线程）→ `context.setContext(ctx)`（这就是 AuthHttpServerFilter 里 `routeContext.getContext()` 的来源）→ `initContext(ctx, request)` **从请求头绑定** tenantId/locale/timezone/**callExpireTime（nop-timeout 头 → now+timeout）**/traceId/propagateRpcHeaders → `next.get().whenComplete → ctx.close()`。

**推论（面覆盖反转）**：默认部署下，五模块全部 HTTP 入口请求在到达 `GraphQLWebService.runGraphQL/runRest/runProxy` 之前，线程上**必然**已附着携带请求级绑定值的每请求 context → `ContextBinder` ctor 恒走 B2 → created=false → init/close 全部 no-op。第 2 轮面 (a)「认证路由请求级 tenant/locale/callExpireTime 绑定恒失效」**不成立**（绑定由 ContextHttpServerFilter 完成，callExpireTime 亦被武装——第 2 轮「callExpireTime 全链无人设置」系单元内 grep 漏掉范围外 filter）；面 (b)「binder created=true 首请求 → close 残留 → 次请求空租户」的可达前提（binder ctor 见 null）在默认部署下**不存在**——B1 分支不可达。

### 1.3 认证路由覆盖面（全部请求都过该 filter？）

`AuthHttpServerFilter`（`nopAuthHttpServerFilter`，`nop.auth.http-server-filter.enabled` 默认 true，order=NORMAL_PRIORITY-100）随同一 filter 链对**每个** HTTP 请求执行，但分支二分：

- **认证分支**（authPaths ∩ 非 publicPaths，requireContext=true）：authPaths = `/graphql*`、`/r/*`、`/q/*`（非 dev-public 时）、`/p/*`、`/f/*`、`/jsonrpc`、`/px/*`（auth-service.beans.xml 实测）——**五模块全部服务面（GraphQL/REST/页面/文件/JSON-RPC/代理）都在此分支**。路径：`executeBlocking(ctx.wrapWithContext(task))`（CallableWithContext → executeWithContext 附着/恢复）→ `initUserContext:556-579` 在**同一每请求 context 上覆写** userId/userName/tenantId（**session 值，无条件覆盖**，含 session tenant 为 null 时把 header 值清为 null）/locale（**header 优先、session 兜底**——与 ContextHttpServerFilter 绑定值一致）/timezone（session 值覆盖 header 值）→ `handleUserContext:362-380` 的 `ctx.runOnContext(next)`（BaseContext.runOnContext 无等待线程时内联附着执行，任务 finally 恢复原附着）。
- **公开分支**（defaultPublic=true 的未列路径 + publicPaths `/r/LoginApi_*`、`/q/health*`、`/q/metrics*`、可选 `/q/*`、`/f/download/*`）：`handlePublicPath = next.get()` 裸透传——**无 userContext、无 context 覆写**，ContextHttpServerFilter 的 header 绑定原样生效。

因此「runOnContext 前置的认证路由覆盖面」精确答案：**filter 链对全部请求执行；runOnContext+session 绑定仅覆盖 authPaths 服务面（即五模块全部业务入口）；公开路径无 runOnContext 但仍有 ContextHttpServerFilter 绑定**。initUserContext 对 tenant/timezone 的无条件 session 覆写是认证路由上请求头不可表达的**真实残面**（设计上 tenant session-first 属安全取向；timezone 覆写无安全必要性，属轻微行为面，不单独立案）。

### 1.4 量化：「每线程首个请求之后的全部请求受影响」

- **默认部署**：受 binder 复用分支影响的用户可见后果 = **0%**（绑定责任由 ContextHttpServerFilter 履行；binder 恒 no-op 为死代码）。线程复用率本身：quarkus worker（ExecutorRecorder executor）与 vertx event loop 均为长生命周期池线程，预热后请求复用率 ≈100%——该量化只对下述非默认配置有意义。
- **非默认配置**（`nop.web.http-server-filter.enabled=false` 或 GraphQLWebService 被链外嵌入式调用——后者在本单元 grep 无实例）：B1 在每线程首个请求成立（init 正确、close 条件 detach）；一旦某请求的响应 future 于非入口线程完成，该线程残留 closed context，**其后该线程 100% 的请求**落入「B2 跳过 init + getOrCreateContext 见 closed 换新建空 context（仅 CFG_DEFAULT_TENANT_ID 默认租户）」——第 2 轮面 (b) 机制链在该配置下成立；第 3 请求起复用 open CB（attribute 跨请求残留）亦成立。

### 1.5 ORM 租户过滤后果链（读点全枚举，对照面）

| 读点 | API | isClosed 过滤 | 默认部署取值 |
|---|---|---|---|
| `TenantParamBuilder.buildParams:23`（nop-orm-eql，租户 SQL 参数） | `ContextProvider.currentTenantId()` → `currentContext()` 裸读 | **无** | 认证路由=session tenant（正确）；公开路径=header tenant（正确）；**越界线程（WS/完成线程，见 C3-017/C3-002/C3-007）=残留值或抛 ERR_CORE_NO_TENANT_ID** |
| `OrmSessionRegistry.instance:19-20`（会话注册表，按 context attribute 键控） | `getOrCreateContext()` | 有（closed 换新） | 每请求独立会话（正确） |
| `BizObjectBuilder.java:214`（本单元 nop-biz） | `currentTenantId()` | 无 | 同 TenantParamBuilder |
| 错误消息 locale：`GraphQLEngine.buildRpcResponse:670`（本单元） | `currentLocale()` | 无 | 请求线程=正确；**WS/完成线程=残留（C3-017）** |

结论：默认部署下 C3-013 的 ORM 租户后果链不存在；跨请求租户错乱风险全部收敛到「线程残留」家族（已由 C3-002/C3-007/C3-015 与本轮新立 C3-017 承载）。

### 1.6 C3-013 处置建议（供裁定）

机制描述按 §1.1/§1.2 修正；建议改判 **P1 → P3**：默认部署零用户可见后果，缺陷实质收窄为（i）ContextBinder「create-or-reuse + 仅 created 才 init/close」契约在一切预附着环境（默认 HTTP 路径）下退化为死代码，契约误导（修复轮若按第 2 轮建议改 init 复用分支，反而在认证路由用 header tenant 覆写 session tenant，构成安全降档——修复方向应改为「GraphQLWebService 侧废除 binder 依赖，声明 ContextHttpServerFilter 为唯一绑定 owner」）；（ii）非默认配置（关闭 filter 链）下面 (b) 完整成立。失效面补正：runProxy:188/198/218/225 为**第三使用站点**（与 runGraphQL/runRest 同构，第 2 轮 F3 漏列）。

---

## 二、② C3-001 降档复核——残留面二次坍缩，终裁建议 P3（非 P2）

### 2.1 字节码独立复验（3.2.2，`javap -p -c DataLoaderHelper`）

1. `dispatchQueueBatch`（offset 36-67）：`invokeLoader(...).thenApply(lambda$2).exceptionally(lambda$3)`——exceptionally 挂接于**返回链末端**。
2. `lambda$dispatchQueueBatch$3`（offset 0-98，逐条复核）：统计计数 → 解包 CompletionException → 遍历 keys 逐个 `future.completeExceptionally(t)` + `dataLoader.clear(key)` → **`Collections.emptyList()` 正常返回**——批失败时 dispatch future **恒正常完成**。第 2 轮 §1.1 结论 1~3 **复核确认**。
3. **本轮新增反证（推翻第 2 轮残留面的唯一来源）**：`invokeLoader(List,List)`（2 参版）异常表实测 `from 0 to 58 target 61 type java/lang/Exception`——**方法体全程被 catch(Exception) 包裹**，捕获后 `CompletableFutureKit.failedFuture(e)`（offset 63-68）。OrmBatchLoader.load 的同步抛出（requireSession 的 ERR_ORM_NOT_IN_SESSION、supplier.get 的解析失败——均 NopException/RuntimeException ⊂ Exception）**在 invokeLoader 内即被转为 failed future**，随后流入 lambda$3 → 逐 key completeExceptionally + clear + 空列表正常完成。成功映射器 lambda$2（含 assertResultSize）的异常同样被末端 exceptionally 接住。第 2 轮「同步抛出穿透 invokeListBatchLoader→dispatch() 传播出 for 循环」与其括注「invokeLoader 在 arg 求值处抛出，exceptionally 来不及挂接」**均不成立**（环境构建 offset 14-30 与批调用 offset 50-54 全在 try 域内）。
4. 附核：3.2.2 `DataLoader.dispatch()` 返回 `CompletableFuture<List<V>>`（`dispatchWithCounts` 才返回 DispatchResult）——`GraphQLExecutionContext.dispatchAll:167` 的 `futures.add(loader.dispatch())` 类型吻合；`dispatch()` 的监视器内快照+清空（异常表 16-122→125）与批调用在监视器外（offset 133+）结构确认；`FutureHelper.waitAll`（:388-410）对正常完成的输入恒正常完成。

### 2.2 残留面终裁

三条款项下的泵链状态：异步批失败→dispatch future 正常完成→递归泵**存活**；同步抛出→invokeLoader catch→同上→**存活**；`thenAccept` 延续内已无任何 Exception 级同步抛出源（`context.dispatchAll()` 仅剩 Error/OOM 级）。**第 2 轮修正后的「thenAccept 延续同步抛出被丢弃 stage 吞没 → 悬挂」残留面坍缩为零**——悬挂路径在 3.2.2 下不存在。

缺陷实质残余：(i) 批级异常在 dispatch 聚合层无日志（但**用户可见性无损**——逐 key completeExceptionally 使字段级错误正常进入 GraphQL response errors，且 stats 计数器 `incrementBatchLoadExceptionCount` 递增，可观测面完整）；(ii) 泵的正确性隐式依赖 3.2.2 的「invokeLoader 全捕获 + exceptionally 吞没」内部语义，升级 java-dataloader（后续版本的 recursivelyDispatch/语义变更）时该依赖无守卫声明。

**终裁建议：C3-001 P1 → P3**（推翻第 2 轮的 P1→P2 建议——P2 仍高估）。修复建议同步降格：`:203` 的 `thenAccept` 改 `whenComplete` + 失败日志（纯诊断性加固）+ 注释声明 3.2.2 语义依赖；「逐 loader 异常隔离」仍可作为顺手加固但非缺陷修复。

---

## 三、③ C3-014/016 条件触发面复核与关系终裁

### 3.1 C3-014（订阅注册三步非原子）——触发面坍缩为零生产调用者；与 C1-003/C2-005 同设施关系终裁

全仓 main 源码 grep 实测：`GraphQLSubscriptionManager.registerSubscription` **零生产调用者**——唯一引用是其 bean 定义（`subscription-defaults.beans.xml:10`，messageService=nopMessageService）与测试类。管理器 start() 仅向 IMessageService 订阅 `graphql-subscription*` 话题做消息→已注册订阅的桥接（onMessage→findMatchingSubscriptions 遍历 subscriptionsById），而注册表在主代码中**恒空**（实际 WS 订阅由 `JsonRpcWebSocketHandler.activeOperations` 自管，不经管理器）。推论：

- **触发面终裁**：并发双注册的触发前提（WS 接收线程并发调用 register）在出厂接线中不存在——站点仅在测试或链外消费方接入时可达。建议 **P2 → P3**（潜伏缺陷：API 一旦被接线即按第 2 轮时序复现；修复 `putIfAbsent` 成本极低，建议随修复轮顺手处理）。
- **同设施关系终裁（盲审边界内的裁定）**：设施归属 = `subscriptionsById`/`subscriptionsByOperation` 双 CHM 的复合不变量维护。该「共享容器上的 check-then-act」机制本体是 C1（共享可变状态）/C2（锁与同步）维度的自然属地——若 C1-003/C2-005 确为同设施条目（任务说明口径），建议**机制修复合并到设施属主条目**（putIfAbsent + 双 map 同锁一致性，一处修复）；**C3-014 的差异化payload——终态机缺口（孤儿订阅对 unregister/cancelSession/pushToClient 三条取消通道全部不可达 + `SubscriptionInfo.executionContext` 永不 complete）与 findMatchingSubscriptions 遍历 id 表导致的孤儿推送面收窄实测——是 C3 维度独有内容，合并时必须随迁**，不得在合并中丢失。裁定轮按编号择一保留即可。
- **连带修正（C3-005 F3 ③面）**：`unregisterSubscription` 不 complete executionContext 的失效面同属该**零接线设施**——现网实际影响同步降为零，C3-005 的该子面标注「dormant」。

### 3.2 C3-016（DefaultFlowControlRunner 同步阻塞）——触发面双重坍缩；与 C4 轮 CL-011 关系厘清

实测三事实：

1. **默认 maxWaitMillis=0**（`DefaultFlowControlRunner.java:18`）：`tryAcquire(1, 0)` 经 DefaultRateLimiter 委托 Guava `RateLimiter.tryAcquire(permits, 0, MILLISECONDS)`——即时失败语义，**默认配置零阻塞**（快速拒绝即设计行为）。
2. **零接线**：全仓 main 无 `new DefaultFlowControlRunner`、无 beans.xml 注册；`newGraphQLExecutor:894` 注入的是 IoC bean `flowControlRunner`（`@Nullable`，GraphQLEngine:127/186）——第 2 轮「graphql 引擎构造时直接注入 DefaultFlowControlRunner」表述失准。生产 gate bean 是 `nopFlowControlRunner = SentinelFlowControlRunner`（nop-cluster-sentinel，sentinel-defaults.beans.xml，`nop.cluster.sentinel.enabled` 缺省启用），其 runAsync 用 `SphU.asyncEntry`（非阻塞，BlockException 即刻拒绝；阻塞仅可能来自 sentinel 核心在限流排队规则下的行为，属部署期规则选择，不在本类与本单元）。
3. 未部署 sentinel 时 flowControlRunner=null → 门控整体不装配。

**判级终裁建议：P2 → P3**（API 契约隐患：`runAsync` 异步签名 + 实现可配置为同步阻塞，且无注释声明——留文档/契约修复；默认路径无缺陷）。

**与 C4 轮 CL-011（已终判不成立）的关系厘清**：盲审纪律约束下未读取 C4 轮文件，以下为边界内裁定——(a) 若 CL-011 的对象是 DefaultFlowControlRunner 阻塞对线程池的占用面，则其「不成立」终判与本轮实测**同向收敛**（默认 0 等待 + 零接线，阻塞面不存在），C3-016 降 P3 后两案无冲突，建议裁定轮将 C3-016 并入 CL-011 的不成立卷宗并互注编号；(b) C3-016 在 C3 维度的残值仅为「异步签名不得静默阻塞调用线程」的契约条款（写回 owner doc 即可），不构成与终判对抗的独立证据。两种情形下 C3-016 均不应以 P2 存续。

---

## 四、④ 快扫：ContextBinder 全部使用形态清单（无第三形态）

全仓 main 源码 grep（`new ContextBinder` / `binder.init` / `binder.close` / `bindSource`）：

| 形态 | 站点 | 构型 | 接线状态 |
|---|---|---|---|
| 形态 1：HTTP 入口（延后 init） | `GraphQLWebService` ×3：runGraphQL:77/93/108/114、**runProxy:188/198/218/225（第 2 轮漏列，本轮补正）**、runRest:239/253/267/273 | no-arg ctor → init(req) → whenComplete close（完成线程） | 在册（恒 no-op，见 §1.2） |
| 形态 2：RPC 服务端拦截器（ctor 即 init） | `ApiContextInterceptor`（nop-rpc-api）×2（interceptAsync/intercept） | ctor(request) → proceedAsync().whenComplete close | **零接线**（全仓无 bean 注册、无构造点——与 registerSubscription/DefaultFlowControlRunner 同属「出厂未接线」原语群） |
| 形态 3 | **不存在**：`bindSource` 零命中；无其他 bind/init 调用点 | — | — |

结论：认证路由之外不存在第三类使用形态；C3-013 失效面按形态 1 三站点收敛。

---

## 发现条目（本轮新增）

### [C3-017] HTTP filter 链每请求 context 裸附着无对称 detach：IO/worker 线程长期残留 closed context，无 filter 链入口（WS 消息）在其上执行的线程级上下文读取点读到跨连接残留 tenant/locale

- **文件**： 本单元后果站点 `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/engine/GraphQLEngine.java:670（buildRpcResponse 的 ContextProvider.currentLocale()）`、`GraphQLEngine.subscribeRpc/subscribeGraphQL 的 fetcher.get(env) 同步段（M21，WS 线程内联执行，tenant 读点经 TenantParamBuilder——nop-orm 对照）`；机制对照点（范围外）`nop-network/nop-http/nop-http-api/src/main/java/io/nop/http/api/server/ContextHttpServerFilter.java:44-64`、`nop-kernel/nop-api-core/.../BaseContext.java:315-327（close 的条件 detach）`、`nop-quarkus/nop-quarkus-web/.../ws/JsonRpcWebSocketEndpoint.java:44-64`
- **证据片段**:
  ```java
  // ContextHttpServerFilter.filterAsync —— 裸附着，无 save/restore、无 finally detach
  IContext ctx = ContextProvider.newContext();      // newContext(true) → t_context.set（filter 线程）
  context.setContext(ctx); initContext(ctx, context);
  CompletionStage<Void> ret = next.get();
  return ret.whenComplete((r, e) -> { ctx.close(); }); // close 在完成线程：holder!=this → 不 clear，仅置 closed
  // BaseContext.close —— 条件 detach
  IContext context = BaseContextProvider.contextHolder().get();
  if (context == this) { BaseContextProvider.clear(); }  // 异步完成时条件不成立 → filter 线程残留
  // GraphQLEngine.buildRpcResponse:670 —— WS 线程上的线程级读点
  String locale = ContextProvider.currentLocale();   // 读到 IO 线程残留的上一请求 context
  ```
- **严重程度**: P2（条件化：多租户部署 + WS 订阅使用；locale 面无条件但影响轻，tenant 面可达即租户隔离后果）
- **并发场景**: vertx event loop（IO 线程）多路复用多连接：HTTP 请求 A 的 filter 链在该线程附着 ctxA → A 异步完成（completion 落在其他线程）→ ctxA.close() 的条件 detach 不成立 → 该 IO 线程残留 closed ctxA；其后同一线程处理 WS 连接 B 的消息（`@ServerEndpoint("/ws/jsonrpc")` 的消息回调在 IO 线程执行）或 HTTP 请求 C 的早期阶段（下一次 `newContext()` 覆写前的窗口），读到 ctxA 的 tenant/locale/attribute。
- **触发时序**: T1（IO 线程 E）请求 A 过 ContextHttpServerFilter：ctxA 裸附着 E（租户 tA、locale lA）。T2 认证过滤器 executeBlocking 派发 worker → A 的响应在 worker/其他 IO 线程完成 → whenComplete 的 ctxA.close() 在完成线程执行，E 的 holder==ctxA 条件不成立 → **不 detach**，closed ctxA 滞留 E。T3（同线程 E）WS 会话 B 的订阅消息到达：`JsonRpcWebSocketEndpoint.executionService` → `engine.newRpcContext`（不 attach 线程 context，C3-012）→ `subscribeRpc` 的 fetcher 同步段（M21）在 E 上执行——若触达 ORM，`TenantParamBuilder.buildParams` 的 `currentTenantId()` 裸读 E 的 holder → **tA（前一 HTTP 请求的会话租户）**：订阅初始数据按错误租户过滤（数据隔离后果）；若同步抛出路径走 `buildRpcResponse:670`，错误消息按 lA 本地化。T4 残留窗口的另一形态：WS upgrade 请求自身的 ctx 滞留 E，跨消息期间被其他连接的 HTTP 请求覆写为任意第三方 context——同一 WS 会话不同消息可能读到不同残留租户。
- **现状**: ContextHttpServerFilter 的附着无 try/finally 对称恢复；BaseContext.close 的 detach 条件（holder==this）在「附着线程 ≠ 完成线程」的常态异步路径下恒不成立；平台无任何对 IO 线程的周期性/请求边界清理（ExecutorHelper 的 detach 约定仅覆盖其自管池任务）。残留的存在性由代码直证；具体哪个线程在哪个时刻读到哪个残留取决于 vertx event loop 分配，属部署时序。
- **风险**: 多租户 + WS 订阅（或任何在 IO 线程执行且读 `ContextProvider.currentXxx()` 的链外入口）部署下的跨请求/跨连接租户信息泄漏（TenantParamBuilder 静默取残留租户）或 ERR_CORE_NO_TENANT_ID 误报（残留 context tenant 为空时——loud 失败，可探测）；错误消息 locale 随机化。与 CL-020（gateway 错误路径 locale 残留读取）同根不同站点。
- **建议**: 机制修复在范围外原语（ContextHttpServerFilter 补 save/finally-restore，或 runOnContext 化），按使用站点归属口径本单元动作：(a) `buildRpcResponse:670` 的 locale 读取改为优先 `context.getLocale()`（入参 context 已具备请求 locale），线程读取降级为兜底；(b) WS/订阅链的同步段若触达 ORM，显式以消息头构造的 context 执行（与 C3-012 的入口 binder 缺失同修，修复轮合并处理）；(c) 范围外建议随 C3-013 修复一并交 plan-first 评估 ContextHttpServerFilter 的对称 detach。
- **失效面枚举**: 线程级上下文读点 × 残留线程矩阵：`GraphQLEngine.buildRpcResponse:670`（WS 错误路径，本条 ✗）、`subscribeRpc/subscribeGraphQL` 同步段 M21（tenant 面可达性取决于订阅 fetcher 同步段是否触达 ORM，条件化）、`GraphQLMessageSubscriptionRegistrar.onMessage`（消息消费线程，M8，若消费线程与 HTTP 复用同池同险）、对照豁免：gRPC（独立 executor，无 HTTP filter 附着史）、`ExecutorHelper` 管内任务（finally detach ✓）、HTTP 请求自身链内读点（每请求新附着覆写 ✓）。同根机制备注：worker 线程的附着均经 executeWithContext/runOnContext 的 save/finally-restore 恢复，**残留集中于裸附着的 filter 线程（IO）**——worker 面无此缺陷。
- **信心水平**: 很可能（裸附着/条件 detach/无清理三环节代码直证，残留存在性确定；tenant 后果的触发依赖 WS 订阅同步段触达 ORM + IO 线程复用时序，locale 后果无条件）
- **误报排除**: 非「close 不 detach」的重复立案——本轮已证明 close 有条件 detach，缺陷在「条件在异步完成常态下不成立 + 附着无对称恢复」；非 C3-013 的复用分支问题（C3-013 在默认部署为 no-op 死代码，本条是 filter 链自身的生命周期缺陷，与 binder 无关）；非 C3-012 重复——C3-012 是入口不建 context，本条是入口读到**别人的** context。
- **复核状态**: 已复核（独立复核 2026-10-18）——**维持 P2（条件化）**。三环节逐行证实：(1) `ContextHttpServerFilter:41` `newContext()`=`newContext(true)`→`t_context.set`（BaseContextProvider:45-58）裸附着无 save/restore；(2) `:50-52` `whenComplete→ctx.close()` 于完成线程，`BaseContext.close:316-327` 的 `contextHolder().get()==this` 条件 detach 在异步完成常态下不成立；(3) 无任何周期/请求边界清理（ExecutorHelper 约定仅覆盖自管池）。读点实证：`buildRpcResponse:670` `currentLocale()`；`TenantParamBuilder:23` `currentTenantId()` 裸读（无 isClosed 过滤，nop-orm-eql 对照面实点）。条件化（多租户 + WS + 订阅同步段触 ORM + IO 线程复用时序）如实标注；locale 面无条件但影响轻。与 C3-012 的入口统一治理合并修复、ContextHttpServerFilter 对称 detach 交 plan-first 的处置维持。现状：新发现/未修复。

---

## 维度集外线索

无新增（CL-021 未占用——本轮机制类发现均已能归属本单元使用站点；「出厂未接线原语群」观察属审计过程备注，不立案）。

---

## 总结

| 类别 | 数量 | 明细 |
|------|------|------|
| 本轮新增立案 | 1 | C3-017（P2，IO 线程 context 残留 × WS 线程级读点） |
| 既有条目修正（本轮建议终裁） | 4 | **C3-013：P1→P3**（默认部署 ContextBinder 恒 no-op、ContextHttpServerFilter 先行绑定——第 2 轮后果链被部署面实证推翻；残面=死代码契约隐患 + 非默认配置面 (b)；补 runProxy 第三站点）；**C3-001：P1→（第2轮建议P2）→P3**（invokeLoader catch(Exception) 使残留面二次坍缩为 observability）；**C3-014：P2→P3**（registerSubscription 零生产调用者，dormant；与 C1-003/C2-005 合并时终态机 payload 必须随迁）；**C3-016：P2→P3**（默认 maxWaitMillis=0 + 零接线 + 生产 bean 为 Sentinel 非阻塞；与 C4-CL-011 不成立终判同向收敛，建议并入其卷宗） |
| C3-005 连带修正 | 1 | F3 ③面（unregister 不 complete）属零接线设施，现网影响为 0，标注 dormant |
| 问①~④闭合状态 | 4/4 | ① 分支精确化+部署面反转+量化+覆盖面+ORM 读点全枚举；② 字节码独立复验+终裁；③ 两案触发面坍缩+关系终裁；④ 两形态无第三 |

**三轮累计（C3 维度，含本轮改判建议，待裁定轮确认）**：立案 17 条 = P1×4（C3-002/003/005/007）+ P2×9（C3-004/006/008/009/010/011/012/015/017）+ P3×4（C3-001/013/014/016）；CL 线索 5 条（CL-007~009/019/020）不变。

## 终止信号判定

**终止信号成立。** 依据：收口四问全部闭合（① 部署面以 beans.xml/registrar 实测闭环并产生改判级修正；② 字节码独立复验闭环且无残留 P1 面；③ 两案触发面坍缩至 dormant + 关系终裁给出；④ 全仓形态快扫无第三形态）；本轮唯一新增发现 C3-017 判级 P2，无新 P0/P1。C3 维度全部方向（dispatch 泵、订阅终态机、gateway 流式/转发、上下文传播矩阵 22 站点、裸 execute、syncGet、订阅注册、流控门控、ContextBinder 全站点）的失效面均已收敛至单点、dormant 设施或已立案条目——第 4 轮无增量空间。后续动作移交：改判建议（C3-001/013/014/016 降格 + C3-017 新案 + C3-005 dormant 标注）由裁定轮确认；ContextBinder/ContextHttpServerFilter 原语修复与 C3-012/017 的入口 context 统一治理涉 nop-rpc-api/nop-http-api，按 Protected Areas 在修复轮以 plan-first 处理。

（本报告为第 3 轮收口盲审产出，全部条目复核状态=未复核，待独立复核轮裁定。）
