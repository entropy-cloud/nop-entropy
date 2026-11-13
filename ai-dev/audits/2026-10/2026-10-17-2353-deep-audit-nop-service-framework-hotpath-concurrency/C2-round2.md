# C2 维度（锁与同步）第 2 轮深挖报告（盲审轮次）

## 基本信息

- **审计单元**: `nop-service-framework-hotpath`（请求热路径五模块，8 源码根，406 文件 / 42,748 行）
  - nop-graphql（core 145f / orm 11f / message 3f / grpc 37f）
  - nop-biz（89f）
  - nop-gateway（44f）
  - nop-biz-auth-core（60f）
  - nop-biz-file-core（17f）
- **仓库路径**: `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`，head `5994c18`）
- **本轮输入**: 第 1 轮报告 `C2-lock-sync.md`（C2-001~006、CL-004~006、25 锁站点矩阵、锁序图、正面样例表、盲区自评）+ live code + 基线 `_tmp/cra-baseline-nop-service-framework-hotpath.txt`。
- **方法论依据**: `ai-dev/skills/concurrency-resource-audit-prompt.md` C2 节 + `ai-dev/skills/deep-audit-prompts.md` 附录 A。

## 盲审纪律声明

本报告为**第 2 轮盲审轮次**产物。审计过程中未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件；**唯一例外**为本维度第 1 轮文件 `C2-lock-sync.md`（任务指定的必要输入）。未读取同目录其他维度文件（C1/C3~C8 轮产物均未接触）；对 C1 轮移交项 CL-016 仅按提示词描述独立取证，未引用 C1 报告原文。全部判定基于本轮亲验的 live code 证据。

## 本轮目标（逐条执行记录）

| # | 指令 | 执行方式 | 结果 |
|---|------|---------|------|
| ① | C2-001 失效面扩展，「锁内 I/O」族收口 | 两口径全量重扫（`synchronized|ReentrantLock|…\.lock()` 33 站点 / `computeIfAbsent|compute|merge` 18 站点），12 锁载体文件逐一精读临界区内容 | 终表见下；族=3 案封闭，无第 4 案 |
| ② | resultLock 失效面 + 引擎级全局锁同族全枚举 | grep `resultLock` 全仓 + `(Lock|lock)=new` + 类监视器 + `Collections.synchronized` + static 可变集合 | **C2-006 前提推翻**（每请求 executor）；同族=空 |
| ③ | Condition/wait/syncGet 面穷举终判 | grep `syncGet|join|await|CountDownLatch|wait|notify|Thread.sleep|\.get\(\)` 全五模块 + `ContextTaskQueue`/`FutureHelper`/`ContextProvider` 承载链精读 | 6+1 站点全判定；G1 自环维持理论注记；CL-018 |
| ④ | 锁契约缺失面（沿 C2-017 口径） | grep 线程相关契约注释 + 读写不对称锁面 + 跨线程字段分布核查 | 登记清单 8 项；无新立案 |
| ⑤ | CL-016 跨维度移交核对 | 调用链两跳亲验（AuthHttpServerFilter.runOnContext → ILoginService.refreshToken 实现 → syncGet(IdP HTTP)） | 事实确认；**维持线索，不升 C2 案** |

---

## ① 「锁内 I/O」站点终表（C2-001 族收口，修复 plan 直接输入）

**扫描口径**:（a）`\bsynchronized[ (]|ReentrantLock|\.lock\(\)|\.lockInterruptibly\(` → 12 文件 33 站点；（b）`computeIfAbsent|computeIfPresent|\.compute\(|\.merge\(` → 18 站点（其中并发容器承载 12，其余为领域模型 merge 方法）。两口径并集即五模块全部锁面。逐站点临界区内容亲验结果：

### A. 锁内含 I/O / 外部回调 / biz invoke 的站点（「锁内 I/O」族终成员）

| # | 站点 | 锁载体 | 锁内操作 | 临界区量级 | 判定 |
|---|------|--------|---------|-----------|------|
| A1 | `GraphQLExecutionContext.dispatchAll` :156-170 | context 监视器（每请求，G1） | `loader.dispatch()` 循环 → 同线程同步回调 `OrmBatchLoader.load` :32 `getBatchLoadQueue().flush()`（SQL 批量往返）+ `BeanMethodBatchFetcher` 批回调 `FutureHelper.futureCall`（api-core :122-129 同步执行）→ `realFetcher.apply` = 完整 biz action（拦截链+事务+DB） | 毫秒~百毫秒 | **C2-001 维持**（亲验：futureCall 同步语义 + flush 在 load 首行均有代码证据） |
| A2 | `BufferedStreamingPublisher.startFirstAttempt` :208-213 → `startAttempt` :217-233 | BufferedSubscription 监视器（每流，B1） | `lifecycle.onFetchStarted()`（外部回调）→ `httpClient.fetchServerEventFlow` → `mapper.apply` → `mapped.subscribe`（类注释 :217-218 自述订阅期同步发起 HTTP） | connect 超时上限（秒级） | **C2-002 维持** |
| A3 | `AttemptSubscriber.onError` :392-400 → `handleError` :303-331 | B1 | `retryCallback.retry()`（外部回调，可查路由/配置）+ `lifecycle.onStreamTerminated` + `startAttempt(newRequest)`（重连=再发起 HTTP） | 秒级 | **C2-002 维持（本案核心链）** |
| A4 | `BizObjHelper.loadByProp` :34-42 | MapCache→CHM bin 监视器（每请求 service-cache，M1） | `bizObj.invoke(METHOD_FIND_FIRST)` = 完整 biz action（权限+校验器+事务+SQL） | 毫秒 | **C2-003 维持**（亲验：`ServiceContextImpl.getCache` :137-142 `new MapCache<>("service-cache", true)` → `MapCache.computeIfAbsent` :86-88 直通底层 CHM） |
| A5 | `AttemptSubscriber.onNext` :383-390 / `request` :158-177 | B1 | `handleItem`→`flushBuffer`/`flushAll` → `subscriber.onNext`（下游响应流写出） | 微秒~毫秒/元素 | C2-002 失效面 (5)，随案整改 |

**族终判**: 「为微秒级原子性设计的锁，临界区被毫秒级 I/O 放大」族在五模块内成员 = **A1/A2+A3/A4 三案封闭**，无第 4 案。第 1 轮 25 站点矩阵中其余锁站点的临界区均为微秒级以下纯内存操作（见 B 表），本轮逐一亲验复核通过。

### B. 锁内纯内存/轻工作站点（豁免复核，第 1 轮判定维持）

| # | 站点 | 锁 | 锁内操作 | 亲验结论 |
|---|------|-----|---------|---------|
| B1 | `BeanMethodBatchFetcher.get` :64-81 | context（G1 同锁） | map 查找 + lambda 构造 + `newDataLoader` + 注册 + `loader.load()` 入队 | **豁免维持 + 表述澄清**：临界区内无 I/O；第 1 轮风险段「futureCall 使 realFetcher.apply 在 G1 锁内」指 **dispatch 期**（批回调执行时），与 F3(3)「get 临界区豁免」不矛盾——biz action 在锁内发生的位置是 A1 的 dispatchAll，不是 fetcher.get。修复 C2-001 后本站点的同锁纪律（P1-2.6-004 防丢 key 注释契约完整）应原样保留 |
| B2 | `OrmBatchLoader.makeDataLoader` :45-56 | context（G1 同锁） | 注册 | 豁免维持 |
| B3 | `JwtAuthTokenProvider.ensureKeys` :87-106 | this | HMAC 密钥派生 ×3~4 | **亲验：纯 CPU 本地运算，无远程 I/O**（encKey 为配置字符串，无 JWKS 远程加载）→ 第 1 轮「O(1) 豁免」判定正确 |
| B4 | `AiRateLimitGatewayInterceptor.buckets` DCL :117-131 | this | LocalCache 构造（一次性） | 豁免维持 |
| B5 | `TokenBucket.tryConsume` :161 | bucket 实例 | refill+consume 算术 | 纳秒级，豁免维持 |
| B6 | `LoginAttemptStores.sharedLocalDefault` :31 | class | store 构造（DCL+volatile） | 豁免维持 |
| B7 | `LocalLoginAttemptStore.incrementLoginFailCount` :69 | this | 计数 RMW | 豁免维持 |
| B8 | `UserContextImpl` ×4 synchronized :267-293 | this | 集合/attrs RMW | 非锁内 I/O 问题；锁面不闭合归 C2-004 |
| B9 | `GatewayContextImpl` ×8 :68-107 | this（每请求） | attribute CHM 读写 | 冗余无害，P3 注记维持 |
| B10 | `BizObjectImpl.asProxy` :283-290 | this | `makeProxy` 反射代理构造（每 biz 实例一次） | 十微秒级一次性，无 I/O，豁免维持 |
| B11 | `GraphQLExecutor` resultLock ×4 :391/:414/:474/:481 | 每请求 executor 实例 | `LinkedHashMap.put` | **见 ②：C2-006 前提推翻**；锁本身必要且正确 |
| B12 | `GraphQLSubscriptionManager.registerSubscription` :99 | CHM bin | `new CopyOnWriteArrayList` | 纳秒；该案问题是双 map 复合无锁（C2-005），非锁内 I/O |
| B13 | `AttemptSubscriber.onSubscribe` :371-378 | B1 | 归属检查 + `subscription.cancel` | 微秒，豁免 |
| B14 | `BufferedStreamingPublisher.cancel` :184-203 | B1 | 状态置换（快照式），upSub.cancel/lifecycle 已在锁外 | **正确范本**，C2-002 修复参照 |
| B15 | computeIfAbsent 族其余 9 站点 | CHM bin | `new TokenBucket`/`new ToolCallAccumulator`/auth store compute/启动期注册/非并发容器 | 第 1 轮 C2-003 F3 枚举逐条复核一致，豁免维持 |

### C. 本轮复核对已立案案件的修正

#### [C2-002-R2] 失效面修正（维持 P2，修复范围扩大）：terminate 的「锁外回调」因重入实际总在 B1 锁内执行

- **文件**: `nop-gateway/src/main/java/io/nop/gateway/core/executor/BufferedStreamingPublisher.java:340-357`
- **第 1 轮论断**: F3(3) 称 `terminate` :340-357 的 `lifecycle.onStreamTerminated`（:347-349）与 `subscriber.onError/onComplete`（:352-356）「已在锁外，正确范本」。
- **本轮推翻证据**: `terminate` 本体的 `synchronized(this)` 块 :341-345 在锁外部分**文本上**不含回调，但 terminate 的**全部三条可达调用路径都在调用方已持有 B1 监视器的上下文中**：
  1. `request` :158（synchronized）→ :172 `pendingComplete` 分支 → `terminate(null)`；
  2. `onError` :393（synchronized）→ `handleError` → :310/:322 `terminate(t)`；
  3. `onComplete` :403（synchronized）→ `handleComplete` → :331 `terminate(null)`。
  监视器是**可重入**的：terminate 内部 `synchronized(this)` 立即重入成功，回调代码在**调用方监视器持有期内**执行。故 `lifecycle.onStreamTerminated`（外部回调）与 `subscriber.onError/onComplete`（下游响应写出）在**所有路径上均于 B1 锁内运行**。
- **后果**: 第 1 轮认定的唯一「正确范本」实际不成立；B1 临界区有效范围比第 1 轮认定更大——下游终态信号投递也被锁内化。五处 `synchronized(this)` 站点（:158/:190/:208/:383-404）**没有一个**存在真正锁外的下游交互（唯一例外是 `cancel` :194-199，其回调在自身 synchronized 块结束后执行，为真锁外范本）。
- **修复 plan 输入**: C2-002 的整改不能只移动 `startAttempt`/`retryCallback`——必须把 **terminate 改造为「锁内快照判定 + 锁外执行回调」**：锁内仅做 `terminated` CAS 置换与 cause/cancelled 快照，锁外执行 lifecycle 与 subscriber 终态信号；并保证 request→pendingComplete→terminate、onError/onComplete→handleError/handleComplete→terminate 三链改造后 exactly-once 语义不回归（原 :341-342 的 terminated 幂等检查移入锁内快照段）。
- **F3 失效面（修正后）**: B1 监视器全部 8 个 synchronized 站点逐一核对——:158 request（含 flushBuffer/flushAll 下游写出 + terminate 链，锁内）、:190 cancel（真锁外范本，保留）、:208 startFirstAttempt（fetch 锁内，本案）、:340 terminate（本轮修正：全路径锁内）、:371 onSubscribe（轻，豁免）、:383 onNext（下游 onNext 锁内，随案）、:393 onError（retry/reconnect 锁内，本案核心）、:404 onComplete（flushAll+terminate 链，锁内，随案）。**C2-002 的 P2 判级维持**（触发场景与后果不变，失效面扩大不改变量级）。
- **信心水平**: 确定（三条调用路径的锁持有关系逐行可验；Java 监视器重入语义为语言规范行为）。
- **复核状态**: 未复核
- **复核裁定（独立复核 2026-10-18）**: **R2 失效面修正采纳为事实**——terminate 三链（request/onError/onComplete）监视器重入锁内化逐行亲验证实，`cancel` :190-199 为唯一真锁外范本确认；修复指引（terminate 改「锁内快照 + 锁外回调」）保留有效。**但主案 C2-002 判级经复核新取证降 P2→P3**：锁内 HTTP 发起被两个 shipping 客户端证实为非阻塞任务提交（JDK `client.sendAsync` / Apache HC5 异步 `client.execute`，`AbstractServerEventSubscription.request` 惰性 `startRequest`），初审「秒级 connect 阻塞、取消延迟=connect 超时上限」的 P2 承重量化不成立——详见 `C2-lock-sync.md` 文末维度复核结论。

#### [C2-006-R2] 前提推翻，建议撤案并改判正面样例：resultLock 是每请求锁，不是引擎级全局锁

- **文件**: `nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/engine/GraphQLEngine.java:558,601,878,893-894`
- **第 1 轮论断**: C2-006 称 resultLock 为「引擎级全局监视器……全部并发请求共享……跨请求串行化点」，信心依据为「单例判定链：GraphQLEngine 为 IoC 单例 bean → newGraphQLExecutor() :894 每引擎一次」。
- **本轮推翻证据**: `newGraphQLExecutor()` 的实现是 `return new GraphQLExecutor(...)`（:893-894）——**每次调用创建新实例**，不存在「每引擎一次」。全部三个调用点均为**每请求/每调用**粒度：
  1. `executeRpcAsync` :558（每 RPC 请求）；
  2. `executeGraphQLAsync` :601（每 GraphQL 请求）；
  3. `fetchResultWithSelection` :878（每次嵌套选取，`CrudBizModel.fetchSelection` :2265 的底层）。
  辅证：全仓 grep `GraphQLExecutor` 无 IoC beans.xml 注册（单例注入路径不存在）；订阅路径 `subscribeGraphQL` :705-766 直接用 fetcher + `GraphQLSubscriptionPublisher`，不经过 GraphQLExecutor。→ **resultLock 生命周期 = 单次请求执行**，跨请求共享前提不成立，「O(N×F) 跨请求串行化」定量全部失效。
- **改判理由**: 四个 `synchronized(resultLock)` 站点守护的是**同一请求**的结果 LinkedHashMap（executeAsync 局部 response / _fetchSelections 局部 ret），写者是该请求各异步字段完成回调——这些回调可运行在不同线程（waitAll 完成扇出），LinkedHashMap 非线程安全，互斥**必要且正确**。锁粒度=请求级，恰与保护对象的共享范围一致。
- **残存注记（P3，矩阵注记，不立案）**: 同一请求内深查询并行字段的完成回调在该请求级监视器上争用——量级为「单请求并行度 × 每请求字段数」，比第 1 轮口径低 N（并发请求数）个数量级，不构成可观测吞吐风险。
- **F3 失效面（推翻后）**: 「引擎级全局锁守护每请求数据」同族全枚举 = **空**——(a) `xxxLock = new Object()` 形态：全五模块仅 resultLock 一处（:56）；(b) 类监视器锁：唯一 `LoginAttemptStores.class` :31（DCL 一次性构造，豁免）；(c) `Collections.synchronizedXxx`：零命中；(d) static 可变集合：零命中；(e) 单例 bean 的 synchronized 方法守护每请求状态：GatewayContextImpl 为每请求实例（非单例，亲验 :22 extends ServiceContextImpl 构造注入），GraphQLSubscriptionManager 为真单例但属 C2-005 复合无锁问题。无遗漏成员。
- **处置建议**: 合并裁定阶段将 C2-006 从 P2 撤案，移入正面样例表（附 P3 注记）。第 1 轮报告的「单例判定链」表述应视为对 `newGraphQLExecutor()` 方法的误读。
- **复核状态**: 未复核
- **复核裁定（独立复核 2026-10-18）**: **撤案终裁成立**——独立核实三入口 :558/:601/:878 均每请求/每调用新建 executor、`newGraphQLExecutor()` :893-894 `return new GraphQLExecutor(...)`、全仓无 beans.xml 单例注册、订阅路径旁路；resultLock 判定为每请求锁、使用正确。C2-006 撤案并移入正面样例表（附 P3 注记）采纳。

---

## ② resultLock 全部使用点核验（指令 ② 主记录）

```
grep -rn 'resultLock' 五模块:
  GraphQLExecutor.java:56   private final Object resultLock = new Object();   ← 声明
  GraphQLExecutor.java:391  synchronized (resultLock)                          ← mutation 串行链结果写入
  GraphQLExecutor.java:414  synchronized (resultLock)                          ← 并行 operation 结果写入
  GraphQLExecutor.java:474  synchronized (resultLock)                          ← 异步字段结果写入
  GraphQLExecutor.java:481  synchronized (resultLock)                          ← 同步字段结果写入
```
全仓确认无第五处使用点（与第 1 轮 F3 一致）；但**锁实例的作用域判定被推翻**（见 C2-006-R2）。同族（引擎级全局锁守护每请求数据）枚举为空。

---

## ③ Condition/wait/syncGet 面终表（指令 ③）

**扫描口径**: `syncGet|\.join\(\)|\.get\(\)\s*;|\.get\(\d|await\(|CountDownLatch|Semaphore|futureBlock|toCompletableFuture|\.wait\(|\.notify|Thread\.sleep` 全五模块 src/main。误报过滤（Atomic*.get / Supplier.get / Config.get / Flow.Subscription.get / filter-chain next.get）后真实同步等待站点 6 处 + 承载原语 1 处：

| # | 站点 | 等待目标 | 锁内等待？ | 无超时？ | 判定 |
|---|------|---------|-----------|---------|------|
| W1 | `IGraphQLEngine.executeGraphQL` :101 / `executeRpc` :110（default 桥） | 请求完整执行 future | 否（HTTP worker，无监视器持有） | 是（无超时参数） | **豁免**：完成保证完备（toGraphQLResponse/toRpcResponse 包裹 exceptionally，future 必然完成）；Web 实际入口走异步（GraphQLWebService :96/:256、JsonRpcService :116 均 `executeXxxAsync(...).thenApply`），sync 桥仅为便利 API |
| W2 | `ILoginService.getUserContext` :170（default 桥） | 用户上下文缓存装载 future | 否 | 是 | **豁免（带边界注记）**：审计范围内唯一调用为接口内部 :166；AuthHttpServerFilter 主路径用异步 `getUserContextAsync`（:167/:437）。范围外宿主若在持锁环境调用，则进入 C2-001 同族「锁内同步等待远程装载」面——随 C2-001 修复口径覆盖 |
| W3 | `IFileStore.saveFile` :20（default 桥） | `saveFileAsync` = `futureCall(() -> saveFile(...))` | 否 | 形式上是 | **豁免**：futureCall 同线程执行，ResolvedPromise 立即返回；五模块内无实现类覆盖 saveFileAsync 为真异步（`implements IFileStore` 零命中） |
| W4 | `CrudBizModel.fetchSelection` :2265 / `fetchSelectionForList` :2280 | `graphQLEngine.fetchResultWithSelection` 嵌套 GraphQL 完整执行 | 常规调用否；**理论入口**：经 C2-001 放大临界区被批装载嵌套调用时为 G1 锁内 | 是 | **不独立立案，登记为 C2-001 联动注记**（终判理由见下） |
| W5 | `GraphQLExecutor` :618（fetchList 的 waitAll().thenApply 内） | 已完成 promises | 链上可能（完成链可在 G1 内执行） | — | **豁免（亲验维持）**：运行时 waitAll 已完成 → ResolvedPromise/isDone 快速路径立即返回，非阻塞等待点 |
| W6 | `ContextTaskQueue.syncGet` :46-104（nop-api-core，越界承载文件） | promise 完成 + 自泵任务队列 | 该原语自身以 ReentrantLock 短临界区 + `queueReady.await()` 组织 | **await 无超时** | **CL-018**（维度集外线索） |
| W7 | `wait()/notify()/join()/CountDownLatch/Semaphore/Thread.sleep` | — | — | — | 五模块 src/main **零命中**（亲验），第 1 轮判定维持 |

**G1 syncGet 自环终判（C2-001 附注的收口）**:

机制层面本轮已完整具体化——`ContextTaskQueue.syncGet`（W6）的等待策略是「等待线程自行泵任务队列」：`beginProcess()` 在本线程已是 processingThread 或无人处理时成功，**等待线程亲自执行队列中的完成回调**。两种形态分别判定：

1. **单线程自环**：T1 持 G1（dispatchAll 批装载内）→ biz action → syncGet(F)，F 的完成回调经 thenOnContext 排入同一 context 队列 → T1 的 syncGet beginProcess 成功（自身即处理线程）→ 自泵执行回调 → 回调中的 dispatchAll 对 T1 为 **synchronized 重入**（同一线程）→ 无死锁。重入语义 + 自泵机制共同破解单线程形态。
2. **跨线程环**：需 T2 为 context 的 processingThread 且阻塞在 G1 上（T2 的当前任务执行到 dispatchAll），同时 T1 持 G1 在 syncGet 等待一个只能由 T2 泵出的任务完成的 future——T1 的 beginProcess 因 T2 持角色而失败，T1 挂 queueReady；T2 挂 G1；queue 中任务无人执行。**机制成立**，但生产调用链（批装载 biz action 内 syncGet 同请求 future 的实例）在第 1、2 轮穷举中均未寻得（W4 的 fetchSelection 嵌套是最接近的入口，其常规调用不持 G1）。

**终判**: 维持第 1 轮「理论链未完全具体化」口径，**不升级、不独立立案**；作为 C2-001 的伴生风险保留——C2-001 的修复（收窄临界区、dispatch 移锁外）同时关闭 W4 的锁内入口与本环的角色卷入条件。

**CL-018（维度集外线索）**: `ContextTaskQueue.syncGet` 的 `queueReady.await()`（nop-api-core ContextTaskQueue.java:89）为**无超时等待**，且是热路径全部 syncGet（W1/W2/W4）的承重原语：任何 awaited future 永不完成（fetcher 泄漏的中间链）即永久挂起调用线程，无兜底超时。顶层入口有 exceptionally 完成保证，嵌套 syncGet 的中间 future 无此保证。属 C3/C5 域（超时纪律），建议 C3 维度核对；C2 面仅注记其 await 期间 processing 角色已释放（endProcess :163-168 先于 await），不构成互斥锁持有。

---

## ④ 锁契约缺失面登记（指令 ④，沿 C2-017「契约靠注释」口径）

热路径上「被多线程调用但同步/契约声明缺失或不完备」的站点逐一登记：

| # | 站点 | 形态 | 判定 |
|---|------|------|------|
| K1 | `GraphQLExecutionContext` 读写不对称 | `getRequest` :127 `synchronized` vs `setRequest` :131 无同步——**装饰性锁**（写方不持锁，读方的 synchronized 互斥面对写方失效，与 C2-004 同病）；`operation`/`fieldSelection`/`makerCheckerEnabled`/`operationMfaChecker` 全字段无同步无注释，HB 依赖 future 链隐式保证（init 期写先于异步 fan-out） | **P3 注记**：未寻得写读跨线程交错的具象时序（setRequest/init 均在 fan-out 前的单线程相位），不立案；修复 C2-004 时建议同口径审视本类的对称性 |
| K2 | `AuthHttpServerFilter.handleUserContext` :356 注释 | 「runOnContext 会创建任务队列。如果异步调用过程中使用了同步等待，则内部实现会利用任务队列来避免出现死锁」——把锁纪律契约委托给范围外 ContextTaskQueue 实现；接口侧 `ILoginService.refreshToken` :123 为**同步签名**（SPI 实现被迫在某线程阻塞），接口 Javadoc 无任何线程约束说明 | **登记**：契约靠注释 + 范围外机制的站点；与 ⑤ CL-016 关联 |
| K3 | `GatewayInterceptorModel` :9-11 | 注释声明跨请求共享 + volatile + 幂等重复创建 | **正面样例**（契约声明完备的范本，维持第 1 轮 #20） |
| K4 | `MediaTypeHelper` :30 | 注释 + volatile + CHM 发布 | 正面样例（维持 #21） |
| K5 | `AuthHttpServerFilter` :211 | 「attribute，跨线程可见，无需 runOnContext」——底层 attribute store 为 CHM（ExecutionContextImpl :43） | 正面（注释契约有实现背书） |
| K6 | `JsonRpcWebSocketHandler` | 单连接消息串行投递的容器假设（activeOperations 的 check-then-act :324/:330/:353） | CL-005 维持（容器假设型契约缺失） |
| K7 | `GatewayContextImpl` | get/setQueryParams/get/setHttpMethod :110-135 未同步（兄弟访问器 synchronized） | #16 P3 注记维持（store 线程安全背书） |
| K8 | `DataFetchingEnvironment.async` :30 | 只写字段（setAsync :468/:606 写，全仓无读者） | 矩阵注记：死字段，非并发缺陷，建议清理 |

**小结**: 第 1 轮已把「多线程共享 + 无同步 + 无契约 + 存在具象交错」的站点全部立案（C2-004/005）；本轮新增登记项（K1/K2/K8）均无具象交错或已有背书，不构成新立案。热路径「契约靠注释」面收口完成。

---

## ⑤ 跨维度移交核对：CL-016（指令 ⑤）

**主张**: refreshToken 在 runOnContext 任务队列线程 syncGet 阻塞 IdP HTTP 往返。

**两跳事实核验（均亲验确认）**:

1. **syncGet(IdP HTTP)**: `OAuthLoginServiceImpl.refreshToken`（nop-auth/nop-auth-sso/.../login/OAuthLoginServiceImpl.java:248-266，`ILoginService.refreshToken` :123 的生产实现之一）:
   ```java
   HttpRequest req = newRefreshTokenRequest(refreshToken);      // POST {tokenUrl} grant_type=refresh_token
   return FutureHelper.syncGet(httpClient.fetchAsync(req, null).thenApply(res -> { ... }));   // ← :257 完整 IdP 往返同步等待
   ```
   对照实现 `LoginServiceImpl.refreshToken`（nop-auth-service :954-960）为纯本地 JWT 重签（无 I/O）——阻塞面仅在 SSO/OAuth 部署形态。
2. **runOnContext 任务内执行**: `AuthHttpServerFilter.handleUserContext`（nop-biz-auth-core :348-382）:
   ```java
   ctx.runOnContext(() -> {
       if (needRefresh) {
           String accessToken = loginService.refreshToken(userContext, authToken);   // ← :359 同步 SPI 调用
   ```
3. **触发面**: `autoRefreshToken` 默认 `true`（AuthHttpServerFilter :70/:72），`isNeedRefresh` :320-328 TTL<半衰期即触发——持有令牌的正常使用随时间推移必然命中，常见条件。

**锁面判定（C2 维度口径）**: syncGet **不在任何 Java 监视器内**——runOnContext 任务体无 synchronized；`ContextTaskQueue` 的 processing 角色在 await 期间经 `endProcess` 释放（:163-168），不构成互斥锁持有；`beginProcess` 角色让渡 + `queueReady` 唤醒机制保证无死锁环。→ **不构成 C2 立案要素**。

**终判: 维持线索（CL-016 不升级为 C2 发现）**。残存风险属 **C4**（阻塞的 IdP 往返占用 runOnContext 任务的执行线程——HTTP worker 或 blocking 线程池线程；IdP 慢时逐请求占满，与 :154 注释「耗时的操作不能在IO线程上执行」的治理意图相悖）与 **C5**（同步等待无平台级超时，仅依赖 HTTP client 自身配置）域，**建议移交 C4 维度立案**。

**C2 关联注记（并入 C2-004 修复面）**: OAuthLoginServiceImpl.refreshToken :261-262 的 `userContext.setAccessToken/setRefreshToken` 是 C2-004 无锁写者的**生产触发器**（runOnContext 线程写，与其他请求的读/写并发），C2-004 的修复工单失效面应列入此路径。

---

## 正面样例（本轮新增复核确认）

| 站点 | 结论 |
|------|------|
| `JwtAuthTokenProvider.ensureKeys` :87-106 | 亲验确认纯 CPU 密钥派生，无远程 I/O；monitor-entry HB 论证维持 |
| `BeanMethodBatchFetcher.get` :64-81 | 临界区内仅注册+入队，无 I/O；同锁纪律注释契约完整（P1-2.6-004），修复 C2-001 时保留 |
| `BufferedStreamingPublisher.cancel` :184-203 | 全 publisher 唯一真「锁外回调」范本（CAS 前置短路 + 状态快照式临界区） |
| `GatewayInterceptorModel` :9-11 | 契约靠注释的**正面**范本（注释+volatile+幂等三要素齐备） |
| `GraphQLExecutor` resultLock ×4 | 改判正面（C2-006-R2）：每请求锁，互斥必要且正确 |
| `AuthHttpServerFilter` 异步主路径 :167/:437 | 认证主路径全异步（getUserContextAsync + thenCompose），sync 桥未在生产入口使用 |

## 维度集外线索（本轮新增）

### [nop-service-framework-hotpath-CL-018]（维度集外线索，不立案展开）ContextTaskQueue.syncGet 的 queueReady.await 无超时，为热路径全部同步等待的承重原语

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/context/ContextTaskQueue.java:46-104`（承载证据），热路径调用面 W1/W2/W4
- **形态**: C3/C5（超时纪律）。`queueReady.await()` :89 无超时参数；`ContextProvider.getOrCreateContext().syncGet` 是 `FutureHelper.syncGet` 对非完成 future 的默认落点。顶层请求 future 有 exceptionally 完成保证，但嵌套 syncGet（如 CrudBizModel.fetchSelection 等待的嵌套执行链）依赖链上每个中间 fetcher future 必然完成——任一泄漏即永久挂起调用线程（HTTP worker），无平台级兜底。建议 C3 维度核对等待超时纪律；评估为 syncGet 增加可配置上限 + 超时诊断日志。
- **复核裁定（独立复核 2026-10-18）**: **确认（线索保留），与 C3 轮终判一致性核验通过**。`ContextTaskQueue.syncGet` :46-105 的 `queueReady.await()` :89 无超时、唤醒源（promise 完成 signalAll / enqueue）与「await 期间 processing 角色已释放、不构成互斥锁持有」亲验证实。C3-round2 终判「本单元 syncGet 站点**无条件不可达**中间 future 泄漏→永久挂起；条件化可达路径全部经由已立案缺陷（C3-001 修正面）或范围外客户端超时配置；api-core 本体归属不变，登记 P3 备忘」——本条「任一泄漏即永久挂起 HTTP worker」的一般性表述应按该终判收窄读取：本单元使用站点无新增立案，api-core 原语缺陷归属 nop-kernel 单元（沿「平台基础设施只审使用方式」校准）。C2 侧处置：维持 CL，P3 备忘（`executeRpc` 同步桥调用点补「依赖内部链全部 future 有界完成」前置假设说明）沿 C3 轮。

---

## 统计与总结

| 类别 | 数量 | 编号/处置 |
|------|------|-----------|
| 新增立案（C2-007+） | **0** | — |
| 复核维持 | 4 | C2-001（族收口终表）、C2-002（失效面扩大，见 C2-002-R2）、C2-003（证据链亲验闭合）、C2-004（共享机制亲验闭合，维持 P1）、C2-005（交错序列复核一致） |
| 复核推翻/撤案建议 | 1 | **C2-006**（前提不成立：resultLock 为每请求锁；建议改判正面样例） |
| 失效面修正 | 1 | C2-002-R2（terminate 回调全路径实际锁内，修复范围扩大至 terminate 三链） |
| CL 线索核实 | 1 | CL-016：事实确认、维持线索、建议移交 C4 |
| 新增 CL | 1 | CL-018（ContextTaskQueue 无超时 await） |
| P0 | 0 | 死锁环：两轮排查均未发现静态可证环 |

**本轮总评**: 第 2 轮的核心产出是**一次撤案与一次失效面扩大**，而非新立案——这符合深挖轮的预期形态：(1) 第 1 轮 C2-006 的「引擎级全局锁」判定源于对 `newGraphQLExecutor()` 的误读，resultLock 实为每请求锁且使用正确，本轮以三入口 + 无单例注册 + 订阅路径旁路的完整证据链推翻；(2) 第 1 轮 C2-002 认定的唯一「锁外回调范本」（terminate）因监视器重入而失效，B1 临界区的真实范围比第 1 轮认定更大，修复 plan 必须覆盖 terminate 的全部三条调用链；(3) 「锁内 I/O」族经两口径 51 站点重扫确认三案封闭；(4) 同步等待面 7 项全判定，G1 自环在机制层面具体化（重入+自泵破解单线程形态）但维持理论注记；(5) CL-016 事实成立但无锁面要素，移交 C4。

**终止信号判定**: 本轮**无新增 P0/P1**（唯一 P1 即第 1 轮 C2-004，维持）；五项深挖指令全部执行完毕；四个同族枚举（锁内 I/O、全局锁、同步等待、契约缺失）均收口为封闭清单。**建议 C2 维度在第 2 轮后终止**，残面以 P3 注记（K1/K7/B11）与 CL 线索（CL-018）形态移交对应维度，不再开第 3 轮。

**盲区自评**: (1) `IHttpContext`/`routeContext.executeBlocking`（AuthHttpServerFilter :155）的线程池归属实现（quarkus/vertx 容器层）不在五模块内，CL-016 的「占用哪个池」判定止于接口语义；(2) nop-graphql-grpc 37 文件本轮 grep 复确认零锁站点，Protobuf marshaller 线程安全性仍未逐字段深挖（沿第 1 轮盲区）；(3) `LocalCache`（Caffeine 包装）的 per-key loading 行为以 nop-kernel 接口语义为准，未验证其内部实现与 Caffeine 版本绑定细节；(4) Flow.Publisher 下游（HTTP server 响应 sink）的写出阻塞性依赖容器实现，A5 量级按「微秒~毫秒」区间从宽认定。
