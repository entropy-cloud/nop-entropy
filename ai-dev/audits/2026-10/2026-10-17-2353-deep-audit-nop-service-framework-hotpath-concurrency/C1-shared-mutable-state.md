# C1 共享可变状态与可见性 — 第 1 轮初审（盲审）

## 审计单元

`nop-service-framework-hotpath`（请求热路径五模块 / 8 源码根，src/main only）：

| 源码根 | 规模（实测） |
|---|---|
| nop-graphql/nop-graphql-core/src/main/java | 188f / 23,858 行（含 xlang 基线口径外的少量文件） |
| nop-graphql/nop-graphql-orm/src/main/java | 11f / 859 行 |
| nop-graphql/nop-graphql-message/src/main/java | 3f / 162 行 |
| nop-graphql/nop-graphql-grpc/src/main/java | 37f / 3,174 行 |
| nop-biz/src/main/java | 102f / 12,951 行 |
| nop-gateway/src/main/java | 53f / 6,836 行 |
| nop-biz-auth-core/src/main/java | 66f / 6,721 行 |
| nop-biz-file-core/src/main/java | 17f / 968 行 |

**仓库路径**：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`）

## 维度定义（C1）

多线程共享的字段/集合/缓存/注册表的线程安全与可见性；check-then-act 竞态；非同步惰性初始化；static 可变状态；ThreadLocal 误用；发布安全。按 `ai-dev/skills/concurrency-resource-audit-prompt.md` C1 节五类载体盘点 + 逐项 happens-before 判定。

## 盲审纪律声明

本报告为第 1 轮初审产出。审计过程未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何历史记录；仅阅读方法论文件（`concurrency-resource-audit-prompt.md`、`deep-audit-prompts.md`）、owner docs（`docs-for-ai/02-core-guides/api-and-graphql.md` 按需）与当前 live code。现状标注（新发现/已知未修复）留待复核阶段。

## 检查范围说明

- **判级口径**：请求热路径「每请求必经」引擎级单例/缓存缺陷按最低门槛判级；条件化路径（特定 resolver/biz 方法/订阅/流式）正常降档；启动期单线程触达降档。
- **已实测锚点核实**：IUserContextCache/LocalUserContextCache/AuthHttpServerFilter/ChunkFileUploadHandler/GraphQLExecutionContext/BizInterceptorModel 均确认存在。其中 **ChunkFileUploadHandler 为纯 stub**（三个方法全部 `throw UnsupportedOperationException`，无任何分片状态），本分支无 C1 对象。
- **判级校准**：并发类发现均写出具体触发时序；写不出的标 P2「理论风险，未具体化」。

### 状态载体清单（C1 五类载体盘点）

| # | 载体 | 位置 | 写者 / 读者 | 同步机制 | 判定 |
|---|---|---|---|---|---|
| 1 | singleton bean 实例字段 | GraphQLEngine（documentCache/builtinSchema/cancelTokenManager/enableActionAuth 等） | IoC init 写 / 请求线程读 | 启动期写→请求期读（容器启动 HB）；documentCache 为 LocalCache | 可接受（builtinSchema/init 后只读） |
| 2 | singleton bean 实例字段 | GatewayHttpFilter.handlerCache、GatewayModel.router/interceptorRouter | init() 写 / 请求线程读 | 同上；ResourceCacheEntry 内部 volatile+synchronized（nop-core 基建） | 可接受 |
| 3 | **每用户共享可变对象** | LocalUserContextCache/AbstractUserContextCache 缓存中的 `UserContextImpl` 实例 | 同一用户的全部并发请求线程 + loginService.flush | **对象字段无同步**（仅 roles/attrs 子集 synchronized） | **缺陷 C1-001/C1-002** |
| 4 | AuthHttpServerFilter.stateCookieHelper/config/loginService/autoRefreshToken | init/setter 写 / 请求读 | 启动期写→请求期读 | 可接受 |
| 5 | JwtAuthTokenProvider.signKeys (HashMap) + legacyKey | ensureKeys() synchronized 写 | 全部读路径先 synchronized ensureKeys() 再读 | **正确**（monitor 门控读，HB 成立） | 可接受（正面样例） |
| 6 | LoginAttemptStores.sharedDefault (static volatile) | DCL | volatile + synchronized DCL | **正确** | 可接受 |
| 7 | static 可变状态全枚举 | DictLabelFetcherProvider.INSTANCE、ObjMetaToGraphQLDefinition.INSTANCE、ReflectionGraphQLTypeFactory.INSTANCE、GraphQLActionAuthChecker.INSTANCE、RandomSessionIdGenerator.INSTANCE、grpc marshaller 12 个 INSTANCE | static initializer 初始化后只读，类均无状态字段 | 类初始化安全发布 | 可接受 |
| 8 | static 可变状态 | MediaTypeHelper.fileExtToMediaType (static CHM) + configLoaded (static volatile) | getMediaTypeFromFileExt 惰性 loadConfig | CHM put/get + volatile，check-then-act 非原子但幂等（代码注释已声明） | 可接受（注释自证，非缺陷） |
| 9 | static 可变状态 | ServiceContextKey.CONTEXT_KEY（grpc `Context.Key`，public static 非 final） | 模块内无读写调用点（仅定义） | Context.key 不可变；字段未再赋值 | 可接受（建议改 final，P3 级卫生项，不立案） |
| 10 | 全局缓存/注册表 | GraphQLEngine.documentCache（parse cache，key=查询原文，有界） | 请求线程并发 get | LocalCache + ResourceCacheEntry（volatile object + synchronized loadObject） | 可接受 |
| 11 | 全局缓存/注册表 | GraphQLSubscriptionManager.subscriptionsById / subscriptionsByOperation（CHM / CHM→CopyOnWriteArrayList） | WS 订阅注册线程 + 消息消费线程 | CHM 原子单操作，**复合操作无原子性** | **缺陷 C1-003** |
| 12 | 全局缓存/注册表 | GatewayInterceptorModel.interceptor（volatile） | 请求线程首调 getOrCreateInterceptor | volatile + 幂等重复创建（注释声明） | 可接受（正面样例） |
| 13 | 全局缓存/注册表 | AiRateLimitGatewayInterceptor.buckets（volatile LocalCache + DCL）+ TokenBucket synchronized tryConsume | 请求线程 | volatile DCL + per-bucket monitor，有界（maxTrackedKeys） | 可接受（正面样例） |
| 14 | 全局注册表 | TypeRegistry types/sourceToTypes/bizObjClasses/enumClasses（CHM） | schema 构建期（启动）写 / 请求读 | computeIfAbsent 原子；启动期单线程为主 | 可接受 |
| 15 | 每请求上下文字段 | GraphQLExecutionContext.loaders（CHM）+ dispatchAll synchronized | GraphQLExecutor 调度点 | per-request 隔离 + synchronized(context)（OrmBatchLoader.makeDataLoader 同锁，注释声明） | 可接受（DataLoader 请求隔离成立，无跨请求泄漏面） |
| 16 | 每请求上下文字段 | GraphQLExecutionContext request（挂 ServiceContextImpl） | initRpcContext 写 / 异步续行读 | **getRequest synchronized / setRequest 非 synchronized——monitor 失配** | **缺陷 C1-004** |
| 17 | 共享 AST/元数据 | 缓存 GraphQLDocument（freeze(true)）及 builtinSchema 字段表 | 请求线程只读 | 冻结 + cache 安全发布；惰性 map（buildSelectionMap/fieldsMap）在共享对象上的请求期触达点未证实 | 可接受（C1-007 防御性提示） |
| 18 | 共享 AST/元数据 | GraphQLFieldDefinition.typeConverter 惰性初始化 | 请求线程并发 getTypeConverter | 非同步惰性初始化，但纯函数幂等（同值覆盖） | 可接受（良性竞态，与误报校准一致） |
| 19 | 共享会话对象 | JsonRpcWebSocketHandler.activeOperations（per-connection CHM）、authHeaders/boundUserId（volatile） | WS 消息线程 + keepAlive timer 线程 + abort 线程 | volatile + CHM；validSubscription→put 复合操作非原子 | **缺陷 C1-005** |
| 20 | 共享 MFA challenge 对象 | LocalMfaChallengeStore 内 MfaChallenge（peek 外发共享） | 验证线程 markVerified(compute 内 setVerifiedAt) / 并发 peek 读 | CHM compute 原子，**challenge 对象字段非 volatile** | **缺陷 C1-006** |
| 21 | 共享对象 | LocalEmailCodeStore/LocalSmsCodeStore Entry（code final + failCount AtomicInteger）+ compute 原子消费 | 请求线程 | 不可变 Entry + AtomicInteger + CHM compute | 可接受（正面样例） |
| 22 | biz 元数据/拦截链 | BizObjectImpl.operations/actions/bizModelBeans（构建期 set，请求期读）、BizInterceptorModel/TransactionActionDecorator(Collector)/CacheActionDecorator(Collector) | 启动期构建 / 请求期只读；装饰器全部 final 字段无状态 | 启动 HB | 可接受（BizInterceptorModel 为纯模型，无运行时共享可变状态） |
| 23 | 每请求缓存 | BizObjHelper.loadByProp → context.getCache()（ServiceContextImpl synchronized getCache → MapCache(CHM)） | 请求内并发 fetcher | CHM computeIfAbsent；**mapping 内嵌套 biz invoke（DB I/O + 可能递归同 map）** | 可接受（C1 无数据竞争；锁内 I/O 移交 C2） |
| 24 | ThreadLocal | 8 源码根 `grep ThreadLocal` = **0 命中**；`IUserContext.set()` 落在 per-request IContext attribute（ExecutionContextImpl.attributes 为 CHM），非裸 ThreadLocal | — | 无池化线程残留路径 | 可接受 |
| 25 | 发布安全 | 构造器启动线程/注册监听器扫描：GatewayHttpFilter.handlerLoader（field init，PostConstruct 前后无逃逸）、JsonRpcWebSocketHandler（构造器内 scheduleWithFixedDelay——**keepAlive timer 持 this 逃逸**） | — | C1 未见未安全发布；timer 生命周期 → CL-001 | CL-001 |

计数口径：静态可变字段枚举以 `grep -P "^\s+(private|protected|public)\s+static\s+(?!final)"` 排除方法/导入后全量人工核对，8 源码根共 2 处（MediaTypeHelper.configLoaded、LoginAttemptStores.sharedDefault），另加 public static 非 final INSTANCE 引用 17 处（均 static 初始化后只读）与 ServiceContextKey.CONTEXT_KEY 1 处。

---

## 第 1 轮（初审）发现

### [nop-service-framework-hotpath-C1-001] 缓存 UserContextImpl 被同用户并发请求原地共享变异，dirty 标志丢更新导致令牌刷新静默丢失

- **文件**: `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/login/AbstractUserContextCache.java:102-132`、`AbstractLoginService.java:50-61`、`AuthHttpServerFilter.java:342-384`、`UserContextImpl.java:22-46,300-352`
- **证据片段**:
  ```java
  // AbstractUserContextCache.getUserContextAsync —— 缓存实例原样外发（无拷贝）
  return userContextCache.getAsync(sessionId).thenApply(userContext -> {
      if (userContext != null && !allowMultipleSessionsSameUser) { ... }
      return userContext;                       // 共享同一 UserContextImpl 引用
  });
  // AbstractLoginService.flushUserContextAsync —— 每请求结束时 check-then-act
  public CompletionStage<Void> flushUserContextAsync(IUserContext userContext) {
      if (!userContext.dirty()) {               // 非原子读
          return FutureHelper.success(null);
      }
      ...
      return userContextCache.saveUserContextAsync(userContext).thenRun(userContext::clearDirty);
  }                                             // clearDirty 非 volatile，异步回调线程执行
  ```
  ```java
  // AuthHttpServerFilter.handleUserContext —— 每请求尾 flush 共享实例
  CompletionStage<Void> promise = next.get().whenComplete((v, e) -> {
      loginService.flushUserContextAsync(userContext);
  });
  // LoginServiceImpl.refreshToken（nop-auth-service，消费方佐证）—— 原地改共享实例
  userContext.setRefreshToken(authTokenProvider.generateRefreshToken(...));
  userContext.setAccessToken(accessToken);      // markDirty() 写非 volatile dirty
  ```
- **严重程度**: P1
- **并发场景**: 同一用户多 Tab/多端并发请求 → HTTP worker 线程池中多个线程各自经 `getUserContextAsync` 取到**同一个**缓存 `UserContextImpl` 实例；token 半衰期刷新在 `ctx.runOnContext` 上下文线程执行 `setAccessToken/setRefreshToken`；各请求结束在任意 pool 线程执行 `flushUserContextAsync`。
- **触发时序**: T1（请求 A，用户 U）进入 handleUserContext，`loginService.refreshToken(userContext, authToken)` 执行 `setAccessToken` → `dirty=true`（普通写）→ 请求 A 尚未结束。T2（请求 B，同用户 U）先完成 → `flushUserContextAsync` 读到 `dirty=true` → 启动异步 `saveUserContextAsync`（DaoUserContextCache 场景为异步序列化持久化）→ T2 的 save 完成 → `thenRun(userContext::clearDirty)` 置 `dirty=false`。T1 随后完成 → `flushUserContextAsync` 读到 `dirty=false`（T2 的 clearDirty 抹掉了 T1 的 markDirty）→ **跳过 flush** → T1 写入的新 accessToken/refreshToken 永不持久化。LocalUserContextCache 部署下对象同一性强掩盖该损失；DaoUserContextCache（nop-auth-service，同族兄弟）部署下下次从 DB 重载旧 token → 会话令牌回退、刷新链断裂（用户被动登出或刷新死循环）。
- **现状**: 缓存条目为共享活对象且全程无字段级同步；`dirty` 为非 volatile boolean，`markDirty/clearDirty` 分属不同请求线程与异步回调线程，无任何 happens-before 边。
- **风险**: 同用户并发请求为真实负载下的常见形态（多 Tab、前端并发聚合查询 + token 半衰期刷新叠加），「常见条件」门槛按热路径口径取最低。后果是鉴权令牌静默回退（不可逆丢更新，需用户重新登录）；对 LocalUserContextCache 部署，风险退化为 JMM 意义上的可见性竞态（实践中偶发读到旧 accessToken）。
- **建议**: 二选一：(a) 缓存命中返回 `UserContextImpl` 的不可变快照（只读视图接口），变更经 `saveUserContextAsync` 以新实例整体替换（copy-on-write，配合 `ICache.put` 原子发布）；(b) 保持共享实例但把 `dirty` 改 `AtomicBoolean`，`flushUserContextAsync` 用 `compareAndSet(true,false)` 原子认领 flush 责任，并将 accessToken/refreshToken/lastAccessTime 字段 volatile 化。优先复用平台 `NamedThreadLocal`/并发基础设施之外，本处属明确需手修的语义竞态。
- **失效面枚举**（F3 同族清扫）: ① `DaoUserContextCache`（nop-auth-service，**超本轮范围**，同 flush 契约的持久化兄弟——78-81 行同样调 `userContext.setAccessToken/setLastAccessTime` 变异共享实例，修复须覆盖）；② `OAuthLoginServiceImpl:264`（nop-auth-sso，登录流程内变异，实例尚为新建、共享面小）；③ `MfaLoginPolicyServiceImpl:128/159`（setLastAccessTime 于 session 对象，Dao session 兄弟面）；④ `UserContextImpl` 全部非 synchronized setter（sessionId/tenantId/locale/timeZone/userId/userName/nickName/primaryRole/deptId/deptName/loginType/openId/accessToken/refreshToken/lastAccessTime/mfaRestricted/dirty）——均为同族无同步变异点；⑤ `UserContextImpl.serializeToJson`（缓存读线程与变异线程并发序列化，同一失效面）。
- **信心水平**: 确定（共享同一实例由 LocalCache 语义与 getUserContextAsync 直接返回证实；丢更新交错无需 JMM 奇异性即可构造）
- **误报排除**: 不是「无状态单例被并发调用」（该缓存条目是带状态活对象）；不是「CHM 使用即安全」（竞态在 CHM 之外的值对象字段上）；亦非已声明边界——`IUserContextCache` javadoc 未声明"缓存对象可被并发变异"，flush 契约反而隐含单写者假设。
- **复核状态**: 已复核（独立复核 2026-10-18）——**保留，降级 P1→P2**。机制面全部证实：缓存活引用外发（getUserContextAsync:107-120 无拷贝）；dirty 丢更新交错可构造，且实际机制比初审描述更宽——clearDirty 同步发生于 saveUserContextAsync 入口（AbstractUserContextCache:129，先于持久化/put），AbstractLoginService:59 thenRun(clearDirty) 为第二次清脏。判级面降档理由：危害链跨部署拼接不成立——默认部署为 LocalUserContextCache（auth-core-defaults.beans.xml 装配；DaoUserContextCache 由 `nop.auth.login.use-dao-user-context-cache` 条件装配默认关），Local 下 LocalCache.newCache(...,null) 无 loader（LocalUserContextCache:21-23），丢失的 flush 等价于跳过一次同引用 put（数据无损），真实残余面为非 volatile 字段的跨线程可见性竞态（写者=半衰期刷新 LoginServiceImpl:952-959 / OAuthLoginServiceImpl:264-265 + MFA attrs，非每请求）；「DB 重载旧 token→令牌回退」后果仅在 Dao 部署存在，而该部署下共享实例前提被 DaoUserContextCache:48-85 每请求新建否定——机制与后果无单一部署同时成立，按条件化降档口径降 P2（初审「风险」节自认 Local 退化为可见性竞态，与此一致）。与 C2-004 关系终裁、Dao 风暴链归属终裁及合并立案建议详见文末「维度复核结论」。现状：新发现/未修复。

### [nop-service-framework-hotpath-C1-002] UserContextImpl 混合同步纪律：roles HashSet synchronized 写 / 非同步读

- **文件**: `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/login/UserContextImpl.java:266-282,319-331`、`AbstractLoginService.java:155-165`
- **证据片段**:
  ```java
  @Override
  public synchronized void addRole(String roleId) {      // 写侧持 this 锁
      if (roles == null)
          roles = new HashSet<>();
      if (roles.add(roleId)) { dirty = true; }
  }
  @Override
  public boolean isUserInRole(String roleId) {           // 读侧无锁
      ...
      return roles != null && roles.contains(roleId);    // HashSet.contains 与 add 并发
  }
  // AbstractLoginService.getRoleInfos —— 无锁迭代
  for (String roleId : userContext.getRoles()) { ... }
  ```
- **严重程度**: P2
- **并发场景**: 角色管理类写路径（运行中调用 `addRole/removeRole`，如角色热更新/授权变更 API 对在线用户上下文补写角色）与同一用户在读路径（鉴权 `isUserInRole`、GraphQL 返回用户信息 `getRoleInfos` 迭代、`serializeToJson` 缓存序列化）并发。
- **触发时序**: T1 执行 `addRole("admin")` → HashSet 内部桶扩容/树化中；T2 同刻执行 `isUserInRole("user")` 或 `getRoleInfos` for-迭代 → JMM 下无同步读：轻则读到中间态返回错误判定（权限放大或漏判），重则迭代期间结构性修改抛 `ConcurrentModificationException`，序列化路径抛出即该请求 500。T1 与 T2 之间无 happens-before（写持锁、读不持锁，锁对读侧无效）。
- **现状**: 写侧 synchronized、读侧裸读——`synchronized` 写不构成对非同步读的可见性/互斥保护；`roles` 字段亦非 volatile。
- **风险**: 权限判定瞬时错误（安全相关读不一致）+ 低概率 CME 请求失败。addRole/removeRole 运行期触达频率低（角色变更属低频管理操作），故降 P2；但一旦启用在线角色热更新路径即为常驻竞态。
- **建议**: 读侧统一加同一 monitor（`synchronized` 方法或读路径经 `Collections.unmodifiableSet` 快照 + `synchronized(this)` 拷贝），或 `roles` 改 `CopyOnWriteArraySet`（读多写少、集合小，拷贝成本可忽略）。
- **失效面枚举**: ① `isUserInRole`（UserContextImpl:322）——非同步读；② `isUserInAnyRole`（:334，循环调 isUserInRole）——非同步读；③ `AbstractLoginService.getRoleInfos`（:155-165，迭代）——非同步读；④ `UserContextImpl.serializeToJson:63`（`out.putNotNull("roles", roles)`，下游 JSON 序列化迭代）——非同步读；⑤ `getRoles()` 直接外发引用的全部消费方（含 DaoUserContextCache 持久化路径，跨模块同族）。写侧仅 addRole/removeRole 两处，已全覆盖。
- **信心水平**: 确定（竞态存在性确定；CME 实际触发概率低）
- **误报排除**: 不是「synchronized/FutureTask/ConcurrentHashMap 原语使用本身」误报——此处恰是复合纪律错误：锁只护写不护读，等于无锁；也不是纯理论——读点全部位于每请求必经的鉴权/序列化路径。
- **复核状态**: 已复核——**维持 P2**。证据全部证实（addRole/removeRole synchronized :267-282；isUserInRole :322-331 无锁读；getRoleInfos AbstractLoginService:155-165；serializeToJson:63）。第 2 轮 F3 修正（setRoles:224 第三写者）复核确认，其三个生产调用点均发布前。**可达性修正（复核 grep 实测）**：`.addRole(`/`.removeRole(` 在全仓库 src/main 内**零生产调用方**——当前交错在平台内不可达，触发依赖下游应用经 IUserContext 公共 API 运行期调用；因 IUserContext 属平台公共扩展面、修复必做且归并同设施工单，维持 P2 而非降 P3，此可达性标注入档。现状：新发现/未修复。

### [nop-service-framework-hotpath-C1-003] GraphQLSubscriptionManager 注册/注销复合操作 TOCTOU 三连：容量击穿、重复 id 幽灵条目、remove-if-empty 丢注册

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/subscription/GraphQLSubscriptionManager.java:84-124`
- **证据片段**:
  ```java
  public void registerSubscription(SubscriptionInfo subscription) {
      String operationId = subscription.getOperationId();
      if (subscriptionsById.size() >= maxActiveSubscriptions) {   // (a) 非原子容量检查
          throw new NopException(ERR_GRAPHQL_SUBSCRIPTION_TOO_MANY)...
      }
      if (subscriptionsById.containsKey(operationId)) {           // (b) check
          throw new NopException(ERR_GRAPHQL_SUBSCRIPTION_DUPLICATE_OPERATION)...
      }
      subscriptionsById.put(operationId, subscription);           // (b) act——非原子
      subscriptionsByOperation
              .computeIfAbsent(subscription.getOperationName(), k -> new CopyOnWriteArrayList<>())
              .add(subscription);
  }
  public void unregisterSubscription(String operationId) {
      SubscriptionInfo subscription = subscriptionsById.remove(operationId);
      if (subscription != null) {
          ...
          ops.remove(subscription);
          if (ops.isEmpty()) {                                    // (c) remove-if-empty 竞态
              subscriptionsByOperation.remove(subscription.getOperationName());
          }
      }
  }
  ```
- **严重程度**: P2
- **并发场景**: 引擎级单例；多个 WebSocket 连接的订阅注册/注销在各自 IO 线程并发执行；消息消费线程经 `pushToClient` 也会触发 `unregisterSubscription`（session 已 closed 时）。
- **触发时序**: (a) T1..T5 并发注册时 `size()=999`（max=1000）全部通过检查 → 5 个 put 全部成功 → 1004 条活跃订阅，容量上限失守。(b) T1、T2 以相同 operationId 并发注册：双双通过 containsKey（均未见）→ T1 put(subA)、T2 put(subB) 覆盖 → `subscriptionsById` 仅存 subB，而 `subscriptionsByOperation[opName]` 同时含 subA、subB；subA 永远无法经 unregisterSubscription 移除（id 已被 subB 占据）→ 幽灵订阅。(c) T1 注销 subA 使列表空、正要 remove map 条目；T2 register subC 同名 → computeIfAbsent 取到该空列表 add(subC)；T1 随后 remove 掉整个 list → subC 从 operationName 索引脱钩（subscriptionsById 仍在，推送经 id 扫描不受影响，但按名索引视图残缺）。
- **现状**: CHM 仅保证单键原子性；容量断言、重复断言、remove-if-empty 三个复合操作均未原子化。
- **风险**: 订阅容量防线可被并发绕过（资源面→CL-002 关联）；重复 id 场景泄漏不可注销的订阅对象（每个持有 session/publisher 引用链）；(c) 造成按名检索视图与真实订阅集不一致。该类经 `subscription-defaults.beans.xml` 装配但本轮 8 源码根内未见生产调用方（仅测试引用），故整体 P2 而非 P1；若订阅功能启用，(b) 的泄漏随重复 id 灌注单调增长。
- **建议**: register 用 `subscriptionsById.putIfAbsent` 返回值判重 + 容量检查改为 putIfAbsent 成功后 `size()` 复核回滚（或信号量预扣）；unregister 的列表清理改 `subscriptionsByOperation.compute(operationName, (k,v) -> { v.remove(sub); return v.isEmpty() ? null : v; })` 单键原子化。
- **失效面枚举**: 同族 check-then-act 注册点全量：① 本类 registerSubscription（capacity/containsKey 两处）；② 本类 unregisterSubscription（remove-if-empty）；③ JsonRpcWebSocketHandler.validSubscription→sendStreamingMessage（同形态，另立 C1-005，修复须同步覆盖）；④ `pushToClient:207` 在消息线程调用 unregisterSubscription —— 与注册线程构成 (c) 的另一半并发源，已在时序内。此外 `maxActiveSubscriptions`/`maxActiveOperations` 语义须成对复核。
- **信心水平**: 确定（交错均可构造；实际触达依赖订阅功能启用与并发重复 id，故 P2）
- **误报排除**: 不是「ConcurrentHashMap 本身使用」误报——三条竞态全部是跨多键/多步复合操作；不是冷路径——订阅注册随客户端重连风暴高频发生。
- **复核状态**: 已复核——**维持 P2**。三处 TOCTOU 逐行证实（容量门 :87 / containsKey :92 / put :97 / computeIfAbsent+add :98-100 / remove-if-empty :117-119 / pushToClient 消息线程 unregister :207）。复核 grep 确认 registerSubscription 在 8 源码根内无生产调用方（仅本类 unregister 内部互调与测试）——与初审自述一致，维持 P2 的可达性依据成立；组件经 beans 装配、实际触达在 ws 集成模块（超单元）。CL-002 归并维持。现状：新发现/未修复。

### [nop-service-framework-hotpath-C1-004] GraphQLExecutionContext 监视器失配：synchronized getRequest() 读 / 非同步 setRequest() 写

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/engine/GraphQLExecutionContext.java:126-134`
- **证据片段**:
  ```java
  @Override
  public synchronized ParsedGraphQLRequest getRequest() {     // 锁 this
      return (ParsedGraphQLRequest) serviceContext.getRequest();  // 真数据在 serviceContext
  }

  @Override
  public void setRequest(ParsedGraphQLRequest request) {      // 无锁
      serviceContext.setRequest(request);
  }
  ```
- **严重程度**: P2（理论风险，未具体化为必然触发——写点先于异步读点的当前调用序掩盖了窗口）
- **并发场景**: per-request 上下文；写方为请求线程（`GraphQLEngine.initRpcContext:506 context.setRequest(req)`、`initForReturnType:506`）；读方 `getRequest()` 加了 synchronized，暗示作者预期跨线程读（异步续行/fetcher 线程），但写侧未持同一监视器，且真值存于 `serviceContext`（ServiceContextImpl），`this` 锁根本不守护该字段。
- **触发时序**: T1（请求线程）执行 `context.setRequest(req)` —— 对 serviceContext.request 字段的普通写；后续异步续行在 pool 线程 T2 执行 `context.getRequest()`——虽 synchronized(this)，但与 T1 的写之间无 monitor 顺序、无 volatile、无 final 发布 → JMM 允许 T2 读到 null 或部分发布对象 → 后续取 `request.getSelection()`/`getOperationId()` NPE 或返回残缺 request。当前 live 代码写点均在请求发起同步段完成、未来续行读点经 CompletionStage 完成语义间接建立 HB，故窗口未必然打开；任何新增"先异步化再 setRequest"或"读点脱离 completion 链"的重构都会触发。
- **现状**: 同步装饰与真实存储位置脱节——`synchronized` 方法保护的字段不在本对象上，非同步方法反而改写真字段；同文件 `dispatchAll()` 与 `OrmBatchLoader.makeDataLoader` 均以 `synchronized(context)` 为正确锁，唯独 request 字段游离在锁体系外。
- **风险**: 结构性地雷——锁形同虚设给后续维护者虚假安全感；一旦 `setRequest` 在异步回调（如 RPC 请求头后续补充、TCC 场景重挂 request）被调用即成真实竞态。
- **建议**: 去掉 `getRequest()` 上误导性的 synchronized，或将 request 读写统一收敛到 `synchronized(this)`（与 dispatchAll/makeDataLoader 共用该 monitor），或将 request 改 volatile 存于 GraphQLExecutionContext 本体而非透传 serviceContext。
- **失效面枚举**: ① `GraphQLEngine.initRpcContext:466`（`context.setRequestHeaders(request.getHeaders())`——responseHeaders/请求头同存 serviceContext，同样无同步写）；② `GraphQLEngine.initForReturnType:506`（setRequest）；③ `GraphQLWebService.runRest/runProxy`（间接经 initRpcContext）；④ 同族正确实现对照组：`registerDataLoader`（非同步但调用方锁内）、`dispatchAll`（synchronized）——修复须把 ①② 一并纳入同一同步域，不能只改 getRequest。
- **信心水平**: 很可能（监视器失配是代码事实；实际可见性失败需要特定重构才打开窗口，故按纪律降 P2）
- **误报排除**: 不是「看到共享字段就报线程不安全」——已给出锁体系不一致的结构证据与打开窗口的具体重构条件；也不是纯风格问题——synchronized 修饰存在本身就是对跨线程读的显式声明，声明与保护错位属真实缺陷。
- **复核状态**: 已复核——**维持 P2**。监视器失配证实（synchronized getRequest :126-129 / 非同步 setRequest :131-134 / 真值存 serviceContext）；F3 锚点实测（initRpcContext:449、setRequestHeaders :466、initForReturnType setRequest :506）；dispatchAll:156 synchronized 与 registerDataLoader:142-148 非同步的「锁体系靠约定」观察成立。初审自评「当前调用序掩盖窗口」与 C2 轮 K1 注记一致，两维自洽，C1 侧维持 P2 结构缺陷立案。现状：新发现/未修复。

### [nop-service-framework-hotpath-C1-005] JsonRpcWebSocketHandler 订阅注册 check-then-act：validSubscription 检查与 put 之间隔着重执行链，重复 id 并发下泄漏首个订阅

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/ws/JsonRpcWebSocketHandler.java:323-336,349-355`
- **证据片段**:
  ```java
  private boolean validSubscription(String operationId, String method) throws IOException {
      if (activeOperations.size() >= maxActiveOperations) { ... return false; }
      if (activeOperations.containsKey(operationId)) {          // check
          sendError(...); session.close((short) 4409, ...);
          return false;
      }
      return true;
  }
  private void sendStreamingMessage(String operationId, Flow.Publisher<ApiResponse<?>> stream) {
      SubscriptionSubscriber subscriber = new SubscriptionSubscriber(session, operationId);
      // 必须先注册再subscribe：...（注释只处理了"同步 publisher 清理早于注册"的单线程时序）
      activeOperations.put(operationId, subscriber);            // act——与 check 之间隔着：
      stream.subscribe(subscriber);                             // executionService.apply 全套订阅构建
  }
  ```
- **严重程度**: P2（理论风险，未具体化——依赖容器对单连接消息回调的线程模型）
- **并发场景**: 同一 WebSocket 连接的 `onMessage` 若被容器（quarkus/spring 适配层）并发派发到不同 worker（或未来引入 per-message 异步处理），两个 subscribe 帧（同 operationId）可同时进入 handleSubscription。
- **触发时序**: T1、T2 同时处理同 id 订阅帧 → 双双通过 `validSubscription`（此刻 map 无该 id）→ 各自执行 `executionService.apply(...)`（含鉴权、订阅 fetcher 启动，耗时毫秒级）→ T1 put(sub1)、subscribe；T2 put(sub2) **覆盖 sub1**、subscribe → activeOperations 仅存 sub2：sub1 的上游 publisher 无人可 cancel（cancelOperation 只能 remove 到 sub2），持续消费上游并在每次 onNext 向 session 写帧（与 sub2 交错输出）；占用的 maxActiveOperations 名额在其自然完成前不释放。
- **现状**: 单线程消息派发下无害（check 与 put 串行化）；代码注释已意识到 subscribe 前后的单线程清理时序（put-before-subscribe 修复），但未覆盖并发派发假设。容量检查同为非原子（同 C1-003(a) 形态）。
- **风险**: 若适配层保证 per-connection 串行则永不触发（需在 quarkus/spring 适配模块核实并钉定该契约——契约未声明即守卫缺失）；触发时为连接内资源泄漏 + 响应流交错。
- **建议**: 用 `activeOperations.putIfAbsent(operationId, subscriber)` 的返回值判重（非 null 即回滚 cancel 新 subscriber 并回 4409），将判重与占位合并为单原子步，消除对容器线程模型的隐式依赖。
- **失效面枚举**: ① validSubscription containsKey（本处）；② sendStreamingMessage put（本处）；③ `onClose`/`cancelOperation` 的 remove 路径（双参 remove 使用正确，为正面对照）；④ 同族 C1-003（GraphQLSubscriptionManager，修复批次须合并覆盖两处同形注册点）；⑤ `maxActiveOperations` 容量检查（非原子，同族 (a)）。
- **信心水平**: 很可能（竞态结构确定；触达前提是容器并发派发单连接消息，当前主流容器通常串行）
- **误报排除**: 不是把「CHM 原语使用」当问题——竞态在 check/act 分离；也不是纯思辨——类内注释证明该注册点已发生过时序缺陷（put/subscribe 次序），同位点并发形态是现实演进方向。
- **复核状态**: 已复核——**维持 P2**。证据逐行证实（validSubscription:323-336、containsKey:330；sendStreamingMessage:349-355、put:353、subscribe:354、时序注释 :351-352；容量门 :324）。容器 per-connection 串行契约未钉定的可达性前提如实标注，维持理论风险档 P2；与 C1-003 同族修复批次合并。现状：新发现/未修复。

### [nop-service-framework-hotpath-C1-006] LocalMfaChallengeStore 外发共享 MfaChallenge 的 verifiedAt 无同步变异/读取

- **文件**: `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/mfa/store/LocalMfaChallengeStore.java:94-114,137-153`
- **证据片段**:
  ```java
  public MfaChallenge peek(String challengeToken) {
      Entry e = challenges.get(challengeToken);
      ...
      Long verifiedAt = e.challenge.getVerifiedAt();        // 无锁读共享对象字段
      if (verifiedAt != null && now >= verifiedAt + opTicketMs()) { ... }
      return e.challenge;                                   // 外发活引用
  }
  public boolean markVerified(String challengeToken) {
      ...
      challenges.compute(challengeToken, (k, e) -> {
          ...
          e.challenge.setVerifiedAt(now);                   // compute 锁内变异 entry 内对象，
          marked.set(true);                                 // 但 peek 读不持该锁、字段非 volatile
          return e;
      });
  ```
- **严重程度**: P2
- **并发场景**: 同一 challengeToken 的并发验证请求（攻击者重放/用户双击）——T1 在 `markVerified` 的 CHM compute 内执行 `setVerifiedAt`；T2 同时在 `peek`/`consume` 路径无锁读 `getVerifiedAt`。CHM compute 的锁只串行化 map 操作，不约束对 entry 值对象字段的裸读。
- **触发时序**: T1 compute 内 `setVerifiedAt(now)`（普通写）；T2 peek 无 HB 读到 null（旧值）→ 判定票窗口走「未验证」分支返回 challenge 继续验证流程；或 T2 读到部分窗口态误判过期提前 remove。后果集中在单 challenge 生命周期内：并发二次 markVerified 已被 compute 原子性正确挡住（`marked` 标志设计正确），裸读仅在验证进行中的瞬窗造成判定抖动，最坏是已验证票被误判过期提前失效（用户需重新发起 MFA）。
- **现状**: MfaChallenge 的 verifiedAt 非 volatile/final，peek/consume/incrFailCount 三处读均无同步；markVerified 写虽在 compute 内，锁与读侧无共同监视器。
- **风险**: 写不出跨请求/跨用户的污染——对象按 token 一次性使用、生命周期短，故 P2「理论风险」档：无安全违约（compute 原子性保住了"恰好首个未验证调用者迁移成功"的主属性），仅判定瞬窗抖动。
- **建议**: `MfaChallenge.verifiedAt` 改 volatile（单写多读、写频率极低，代价可忽略）；或 peek 返回不可变快照。
- **失效面枚举**: ① LocalMfaChallengeStore.peek/consume 的 getVerifiedAt 读（本处）；② markVerified compute 写（本处）；③ 同族对照：LocalEmailCodeStore/LocalSmsCodeStore 的 Entry（code 为 final、failCount 为 AtomicInteger，无同类裸字段——**正面样例，无同族缺陷**）；④ `MfaChallenge` 其余字段（scene/payload 等 create 时写定后只读，安全）。全族清扫后仅本类存在裸字段竞态。
- **信心水平**: 确定（竞态存在）；后果轻微（故 P2 非更高级别）
- **误报排除**: 不是把 compute/CHM 原语当问题——主竞态恰在原语保护之外的对象字段上；也未与设计注释冲突（注释声明的"原子 compute 恰好首个迁移成功"成立，本发现针对的是 peek 读侧可见性，属未声明的剩余面）。
- **复核状态**: 已复核——**维持 P2**。证实：peek :106 无锁读 getVerifiedAt、markVerified compute :148 内 setVerifiedAt、MfaChallenge.verifiedAt（:81 `private Long verifiedAt`）非 volatile、设计注释 :142 与发现不冲突。后果单 challenge 生命周期内 bounded，P2 恰当；修复（volatile 一行）随设施工单顺带。现状：新发现/未修复。

### [nop-service-framework-hotpath-C1-007] GraphQLSelectionSet.buildSelectionMap 非同步惰性 HashMap——当前共享触达点未证实（防御性立案）

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/ast/GraphQLSelectionSet.java:19,54-63,69-74`
- **证据片段**:
  ```java
  private Map<String, GraphQLSelection> selectionMap;          // 非 volatile

  private Map<String, GraphQLSelection> buildSelectionMap() {
      if (selectionMap == null) {                              // check-then-act
          selectionMap = new HashMap<>();                      // 先发布空 map 再填充
          for (GraphQLSelection selection : getSelections()) {
              selectionMap.put(selection.getAliasOrName(), selection);
          }
      }
      return selectionMap;
  }
  ```
- **严重程度**: P3（当前不可达于共享对象，防御性提示）
- **并发场景**: 若该惰性 map 在**共享缓存 GraphQLDocument** 的 selection set 上于请求期被触发，两线程并发首调将出现：T1 发布空 HashMap 后仍在填充，T2 见非 null 直接 `get` → 读到不完整 map → `getSelection` 返回 null → 调用方按「selection 不存在」跳过字段（如 resolver/鉴权剪枝），产生静默数据缺失；无界哈希表并发读处于中间态还有低概率结构性异常。
- **触发时序（潜在）**: T1 `getSelection("f1")` → selectionMap 赋值（字段写先于 put 完成）→ T2 同刻 `getSelection("f2")` → 见非 null → get("f2") 返回 null → f2 被当作未选择跳过。**当前 live code 逐点核实**：请求路径的 getSelection 调用仅发生在 `expandTreeChildren` 的 `deepClone()` 副本上（GraphQLSelectionResolver:290），共享文档的解析在 documentCache loader 内完成（ResourceCacheEntry synchronized loadObject 串行化）；`removeSelection` 亦仅在解析期副本上触达。故本轮无可达交错。
- **现状**: 非同步惰性初始化 + 非安全发布形态（字段写先于内容填充、无 volatile/final）真实存在；被 freeze(true) + 解析期串行化的现有时序所掩盖。
- **风险**: 该类是公共 AST 基类，任何新增的请求期 `getSelection(name)` 调用（如新的 fetcher/directive 处理器）都会在共享缓存文档上打开竞态窗口，且表现为难排查的静默字段缺失。
- **建议**: `init()`（与 `GraphQLObjectDefinition.init()` 同款预构建 fieldsMap）中预构建 selectionMap，或在 doc.freeze(true) 时全量物化各 lazy map；最低成本方案是字段加 volatile + 构建后填充完再发布（局部变量构建完再赋字段）。
- **失效面枚举**: ① `buildSelectionMap`/`getSelection`/`removeSelection`（本类三触点）；② 同族惰性 map：`GraphQLObjectDefinition.fieldsMap`（init() 预构建，启动期——安全）；`GraphQLInterfaceDefinition.fieldsMap`（:24,34,41 惰性——解析期触达，同 P3 防御面）；`GraphQLOperation.vars`（init 期构建——安全）；③ `GraphQLFieldDefinition.typeConverter` 惰性（幂等同值，良性，见状态载体清单 #18，不立案）。
- **信心水平**: 很可能（缺陷形态确定；当前不可达性经逐调用点核实）
- **误报排除**: 已按「写不出具体可达交错 → 降级」纪律执行（P3 + 防御性标注）；未把解析期副本上的安全调用误报为竞态。
- **复核状态**: 已复核——**维持 P3**。证据证实（字段 :19 非 volatile；buildSelectionMap :55-63 check-then-act——初审行号 54 微漂 1 行）；「当前不可达」主张复核成立：请求路径 getSelection 仅在 expandTreeChildren 的 deepClone 副本上触达（GraphQLSelectionResolver:283-295，copy.getSelection :290）。补充触点：addFieldSelection:89-90（selectionMap 非 null 时 put）同属副本/解析期面，不改判定。防御性 P3 口径正确。现状：新发现/未修复。

### [nop-service-framework-hotpath-C1-008] GraphQLEngine.actionAuthNoCheckerWarned 非 volatile 标志跨线程写（测试观测点）

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/engine/GraphQLEngine.java:158-159,166-172,380-394`
- **证据片段**:
  ```java
  /** F-API2-1 测试观测点：action-auth 启用但 checker 缺席的告警是否已触发。 */
  boolean actionAuthNoCheckerWarned;                 // 非 volatile，包私有

  // newGraphQLContextFromContext（每请求路径）：
  LOG.warn("nop.graphql.action-auth-enabled-but-no-checker:...");
  actionAuthNoCheckerWarned = true;                  // 请求线程普通写
  ```
- **严重程度**: P3
- **并发场景**: 请求线程（newGraphQLContextFromContext）与测试线程（断言该标志）之间无 happens-before；多请求线程并发写同值。
- **触发时序**: T1（请求线程）置 true；测试线程读——JMM 允许读到过期 false → 断言偶发失败或假绿。并发写同值无失update风险。
- **现状**: 注释明确其为「测试观测点」，语义为尽力而为标志；生产逻辑不读该字段（仅写）。
- **风险**: 仅测试断言稳定性问题；生产无行为影响。
- **建议**: 标 volatile（一行成本），或在测试用 AtomicBoolean 字段替换。
- **失效面枚举**: ① 本字段两处写点（setActionAuthChecker:171、newGraphQLContextFromContext:392）已全量；② 全引擎其余非 volatile 标志扫描：`registerCache/enableActionAuth/enableDataAuth`（启动期写定后只读，安全）、`JsonRpcWebSocketHandler.maxActiveOperations`（配置期——安全）。同族无遗漏。
- **信心水平**: 确定
- **误报排除**: 未按「看到共享字段就报」处理——已核实生产路径零读取，按观测点定位 P3，不虚报为告警丢失缺陷。
- **复核状态**: 已复核——**维持 P3**。字段（非 volatile 包私有）与两处写点（setActionAuthChecker 内、newGraphQLContextFromContext :392 附近）实测证实；生产零读取、仅测试观测的定性成立。P3 恰当。现状：新发现/未修复。

---

## 维度集外线索（不立案展开）

### [nop-service-framework-hotpath-CL-001]（维度集外线索，不立案展开）JsonRpcWebSocketHandler keepAlive 定时任务在容器缺失关闭通知时的滞留（C6/C7 形态）

`JsonRpcWebSocketHandler` 构造器（:82-83）向 `GlobalExecutors.globalTimer()` 注册每连接一个 10s `scheduleWithFixedDelay` keepAlive 任务，持 `this`+session 引用；取消仅挂接 `onClose`/`onThrowable`（:113-117,126-128）。若容器对异常断连既不回调 onClose 也不回调 onThrowable，任务与其引用链滞留直至进程退出——C6 资源生命周期/C7 长时运行单调增长形态。建议对账点：容器适配层（quarkus/spring ws 适配）是否保证两回调必达其一。

### [nop-service-framework-hotpath-CL-002]（维度集外线索，不立案展开）GraphQLSubscriptionManager.subscriptionsByOperation 幽灵条目无界累积（C5/C7 形态）

C1-003(b) 的重复 id 竞态在 `subscriptionsByOperation`（CHM→CopyOnWriteArrayList）留下不可注销的 SubscriptionInfo 条目；该索引无独立容量/清扫机制，`subscriptionsById` 的 maxActiveSubscriptions 上界覆盖不到它——随重复 id 灌注单调增长（C5 有界性 + C7 长时运行形态）。若 C1-003 修复采用 putIfAbsent 方案则本线索自动消除。

### [nop-service-framework-hotpath-CL-003]（维度集外线索，不立案展开）AiRateLimitGatewayInterceptor 令牌桶回填依赖墙钟，时钟回拨期间限流桶不回填（C7 形态）

`TokenBucket.refill()`（AiRateLimitGatewayInterceptor.java:170-178）以 `CoreMetrics.currentTimeMillis()` 差值驱动回填；系统时钟回拨使 `elapsed` 为负 → 不回填直至时钟追回，窗口内全部请求被 429 拒绝（拒绝面放大）。时钟单调性/回拨属 C7 时钟与期限节；建议改用 `System.nanoTime()` 基准。

### 跨维度移交提示（非 CL，不立案）

- **移交 C2**：`BufferedStreamingPublisher.startAttempt`（nop-gateway/core/executor/BufferedStreamingPublisher.java:220-237）在持有 `BufferedSubscription` monitor 内执行 HTTP fetch + mapper 构建 + subscribe，下游 `request()/cancel()` 线程在建立上游连接期间被阻塞（锁内 I/O）。
- **移交 C2**：`BizObjHelper.loadByProp`（nop-biz/crud/BizObjHelper.java:34-40）在 per-request CHM `computeIfAbsent` mapping 内执行嵌套 biz invoke（DB 查询，且 find_first 内部可再触发同 map 不同 key 的嵌套 computeIfAbsent——CHM 递归更新死锁/ISE 形态）。

---

## 发现清单（本轮总计）

| 编号 | 严重程度 | 一句话摘要 | 文件 |
|---|---|---|---|
| nop-service-framework-hotpath-C1-001 | P1 | 缓存 UserContextImpl 共享实例无同步变异，dirty 丢更新致令牌刷新静默丢失 | AbstractUserContextCache / AbstractLoginService / AuthHttpServerFilter / UserContextImpl |
| nop-service-framework-hotpath-C1-002 | P2 | roles HashSet synchronized 写 / 非同步读竞态（权限读不一致 + CME） | UserContextImpl / AbstractLoginService |
| nop-service-framework-hotpath-C1-003 | P2 | 订阅注册表三处 TOCTOU：容量击穿 / 重复 id 幽灵条目 / remove-if-empty 丢注册 | GraphQLSubscriptionManager |
| nop-service-framework-hotpath-C1-004 | P2 | getRequest synchronized / setRequest 非同步的监视器失配（锁不守护真字段） | GraphQLExecutionContext |
| nop-service-framework-hotpath-C1-005 | P2 | WS 订阅 check-then-act 隔着重执行链，重复 id 并发泄漏首个订阅（依赖容器线程模型） | JsonRpcWebSocketHandler |
| nop-service-framework-hotpath-C1-006 | P2 | MfaChallenge.verifiedAt 裸字段跨线程读写（compute 锁不覆盖 peek 读） | LocalMfaChallengeStore |
| nop-service-framework-hotpath-C1-007 | P3 | GraphQLSelectionSet 惰性 HashMap 非安全发布形态（当前共享触达点未证实，防御性） | GraphQLSelectionSet |
| nop-service-framework-hotpath-C1-008 | P3 | actionAuthNoCheckerWarned 非 volatile 测试观测标志 | GraphQLEngine |

**CL 线索**：3 条（CL-001 keepAlive 定时器滞留 C6/C7、CL-002 订阅索引幽灵条目无界 C5/C7、CL-003 令牌桶墙钟回拨 C7）；另 2 条跨维度移交提示（C2：BufferedStreamingPublisher 锁内 I/O、BizObjHelper computeIfAbsent 内嵌套 invoke）。

**总计**：8 条发现（P1×1、P2×5、P3×2）+ 3 条维度集外线索 + 2 条跨维度移交提示。状态载体清单 25 项全部逐项判定（正面样例 5 项：JwtAuthTokenProvider、LoginAttemptStores、GatewayInterceptorModel、AiRateLimitGatewayInterceptor、LocalEmailCodeStore/LocalSmsCodeStore——后续修复轮勿回归）。

**深挖建议**（供第 2 轮）：C1-001 的失效面向 nop-auth-service DaoUserContextCache/DaoLoginSessionStore 延伸（超本轮范围，建议独立审计单元）；JsonRpcWebSocketHandler 的容器线程模型契约需在 nop-quarkus/nop-spring 适配模块钉定。

---

## 维度复核结论（独立复核子代理，2026-10-18）

复核方式：与初审/深挖代理相互独立，以怀疑姿态对 10 条发现 + 5 条 CL 线索逐条亲开 live code 核对证据（file:line 全量重开）；本阶段解除盲审，对照 `ai-dev/audits/cra-index.md`（WI 1.1–2.2 台账，**不含本单元**——本审计发现均不在既有台账）、`ai-dev/plans/2460~2469-*.md`、`ai-dev/bugs/`、`ai-dev/lessons/`（无 UserContext flush/dirty 相关既有记录）及同目录 C2 轮（C2-004 关系终裁）。git log 实测：涉及文件最后提交为 2026-10-03（plan-2306）与 2026-10-07（allow-multiple-sessions），均早于本审计（2026-10-17），**无审计后修复提交**。

### 裁定汇总表

| 编号 | 初审判级 | 复核裁定 | 复核要点 |
|---|---|---|---|
| C1-001 | P1 | **保留，降级 P2** | 机制全部证实；危害链跨部署拼接不成立（Local 丢 flush 数据无损 / Dao 无共享实例）；与 C2-004 合并，主 ID 归 C1-001 |
| C1-002 | P2 | 维持 P2 | 证据证实；可达性修正：addRole/removeRole 平台内生产调用方为零（grep 实测），触发依赖下游公共 API 调用 |
| C1-003 | P2 | 维持 P2 | 三处 TOCTOU 逐行证实；8 根内无生产调用方（初审已自述，复核确认）；CL-002 归并维持 |
| C1-004 | P2 | 维持 P2 | 监视器失配证实；F3 锚点（:449/:466/:506）实测；与 C2 轮 K1 注记自洽 |
| C1-005 | P2 | 维持 P2 | 证据逐行证实（:323-336/:349-355/:351-352 注释）；容器串行契约前提如实标注 |
| C1-006 | P2 | 维持 P2 | peek:106 / compute:148 / MfaChallenge:81 非 volatile 全证实；后果 bounded |
| C1-007 | P3 | 维持 P3 | deepClone 副本可达性主张复核成立（GraphQLSelectionResolver:283-295）；行号微漂（55-63）；补 addFieldSelection:89-90 触点 |
| C1-008 | P3 | 维持 P3 | 字段与两写点证实；生产零读取定性成立 |
| C1-009 | P2 | 维持 P2 | 旁路 + dirty 破口证实；SPI 入口管线证实（hook 无平台内实现，旁路写者当前扩展点专属）；LoginMfaFlow setAttr 为真实写者佐证 |
| C1-010 | P2 | 维持 P2 | 全链亲验（RMW / clearDirty:144 / NopAuthSession 0 个 version 列 / flush 返回值丢弃）；风暴链唯一归属终裁确认 |
| CL-001 | 线索 | 确认 | keepAlive 构造器 :82-83 注册、仅 onClose/onThrowable 取消（:113-117/:126-128）证实 |
| CL-002 | 线索 | 确认（维持归并 C1-003(b)） | 投影关系成立；putIfAbsent 修复自动消除；验收不变式已写明 |
| CL-003 | 线索 | 确认 | TokenBucket.refill 以 CoreMetrics.currentTimeMillis() 差值回填（:171-179），时钟回拨不回填——C7 形态 |
| CL-016 | 线索 | 确认，移交 C4 | syncGet:258 于 runOnContext 队列线程阻塞 IdP 往返；thenApply :264-265 第三类写者线程证实 |
| CL-017 | 线索 | 确认，移交 C4/C5 | sys 上下文必然 dirty（:273-288 三 setter 判等必真）→ putAsync("sys", null)（AbstractUserContextCache:130）→ LocalCache.putAsync:314-317 同步 put → Caffeine Cache.put null NPE（LocalCache 字段 :40 为 Caffeine Cache 实测） |

**统计**：10 条发现——0 驳回、0 升级、1 降级（C1-001 P1→P2）、9 维持；复核后判级分布 **P1×0 / P2×8 / P3×2**。5 条 CL 全部确认（CL-016→C4、CL-017→C4/C5 按预设移交并注明）。新增复核补充线索 1 条（R-CL-1，见下）。

### C1-001 终裁（本轮核心裁定）

1. **机制面（指令 a/b）全部证实**：缓存外发活引用成立（AbstractUserContextCache.getUserContextAsync:107-120 无拷贝返回）；dirty 丢更新时序成立且**实际机制比初审描述更宽**——clearDirty 同步发生于 saveUserContextAsync 入口（:129），先于任何持久化/put，AbstractLoginService:59 的 thenRun(clearDirty) 是第二次清脏；交错每步可信、可构造。
2. **判级终裁（指令 e）：降 P1→P2**。默认部署为 LocalUserContextCache（auth-core-defaults.beans.xml :47 装配；DaoUserContextCache 由 `nop.auth.login.use-dao-user-context-cache` 条件装配，auth-service.beans.xml :203-207，默认关）。Local 下共享实例前提成立，但 LocalCache.newCache(..., null) 无 cache loader（LocalUserContextCache:21-23）——丢失的 flush 等价于跳过一次「同引用 put」（数据无损），真实残余危害是非 volatile 字段的跨线程可见性竞态（写者=半衰期刷新 + MFA attrs，低频非每请求）；「DB 重载旧 token → 令牌回退 → 被动登出」后果仅在 Dao 部署存在，而 Dao 部署下共享实例前提被每请求新建（DaoUserContextCache:48-85）否定。**P1 所需的「机制」与「后果」无单一部署同时成立**，按「条件化路径正常降档」降 P2。初审「风险」节自认 Local 退化为 JMM 可见性竞态，与本次降级内在一致。
3. **与 C2-004 关系终裁（指令 c）**：同设施双镜头成立（本条=可见性/dirty 丢更新镜头；C2-004=锁面不闭合/复合操作镜头，其 F3(6) 亦自认「编号待复核阶段对齐」）。**裁定合并为单一修复工单，主 ID 归 C1-001**（C1 共享可变状态为本体设施、编号在前、修复面更宽），C2-004 为并案镜头；合并修复面覆盖 C1-001 + C2-004 + C1-002 + C1-009 的全部 F3。**附注（改判级不改事实的边界外提示）**：C2-004 的 P1 危害链（"角色变更永久丢失……缓存与 Redis/Dao 持久层均不含新角色"）存在与本条同构的跨部署拼接缺陷——其交错需要「共享实例（Local）+ flush 期序列化持久层（Dao）」同时成立，且 addRole/removeRole 平台内生产调用方为零（复核 grep 实测）；建议 C2 复核对 C2-004 同步降档（C2 文件不在本轮改写范围）。
4. **Dao「令牌回退→再登录风暴」链归属终裁（指令 d）**：**独立于 C1-001，归属 C1-010**。机制不同（DB 行写写冲突 read-modify-write、无 version 列 vs JMM dirty 丢更新）、部署前提不同（每请求新建实例 vs 共享实例）、修复分治（条件 UPDATE/version 列 + 刷新单飞 vs 对象级同步/快照）。C1-001 不覆盖、也不应吸收该链。C1-010 全链证据复核亲验成立（含 NopAuthSession 实体块 version 列 grep=0）。
5. **现状**：新发现/未修复。

### F3 完备性补全（复核亲自 grep，补全不改判级）

- 第 2 轮两处 F3 修正**复核确认**：roles 第三写者 setRoles:224（三个生产调用点 LoginServiceImpl:704 / OAuthLoginServiceImpl:181 / AuthHttpServerFilter:288 均发布前）；非同步 setter 计数 17（逐一数过，含 setMfaRestricted:314）。
- **复核补充线索 R-CL-1（新增，不入案编号，建议 C4/安全维度复核）**：**logout/kick 与在途请求 flush 的会话复活竞态**——`AbstractUserContextCache.saveUserContextAsync:128-132`（Local 路径）无会话有效性守卫：`doLogout`（AbstractLoginService:117-128）经 removeUserContextAsync:135-138 双缓存移除后，同 session 在途请求（logout 前已 getUserContextAsync 取得引用）结束时若 dirty（半衰期刷新/biz setter）仍会 putAsync 把已注销会话的 userContext 与 userName→sessionId 映射**重新写回双缓存**，被注销会话复活（旧凭证继续可用；Local 部署下 loginSessionStore 可为 null，无 DB 层拒绝记录兜底）。Dao 兄弟实现有守卫（DaoUserContextCache:116-117 logoutType 检查后 return）——同一契约的 Local/Dao 不对称即为守卫缺失证据。触发条件常见（登出/改密踢出时刻恰有页面在途聚合请求）。修复归并设施工单：saveUserContextAsync 增加与 Dao 对称的会话有效性守卫。
- serializeToJson 生产调用面收窄标注：复核 grep 证实 UserContextImpl.serializeToJson 在平台 src/main 内无直接调用方（C1-002 F3④ / C1-001 F3⑤ 的序列化读面为 JSON 机制条件触达，非每请求直调）。

### 争议点与保留意见

1. **C1-001/C2-004 的 P1 判级**是本轮唯一实质争议：两轮初审均以「令牌/角色静默丢失→被动登出」承载 P1，但该后果链依赖 Dao 持久化语义，而共享实例机制仅存在于 Local 部署——复核以部署一致性为据降档/建议降档。若后续发现「Local 部署 + 带异步持久化的自定义 ICache 实现」第三种装配，判级应上调回 P1。
2. **C1-002 的独立判级**：若严格按「平台内可达性」口径应为 P3（生产写者零）；维持 P2 是考虑 IUserContext 为平台公共 API（下游运行期调用受支持）且修复必做。裁定时已知悉此双重口径并留档。
3. **C1-003/C1-005 的 P2**依赖订阅/WS 功能启用与 ws 集成模块（超 8 源码根）调用方存在——若集成模块核实无调用方可再降档。
4. C2 轮 K1 注记对 C1-004 取「P3 不立案」、C1 轮取「P2 立案」，两维口径差异各自留档，不构成冲突（立案归属 C1 维度）。

## 最终保留项

修复工单合并建议（同一同步域批次，避免碎片化修复）：

1. **设施工单（主 ID：C1-001，并案：C2-004、C1-002、C1-009，复核补充：R-CL-1）**——UserContextImpl / AbstractUserContextCache 同步纪律整体修复：
   - 全访问器统一同步域，或改不可变快照 + volatile 引用发布（C2-004 建议方案二选一）；
   - dirty 改 AtomicBoolean，flush 以 CAS 原子认领（C1-001 建议 (b)）；
   - 覆写 addAttrs/removeAttr 补锁 + 置脏，或收窄 ExtensibleBean 可变器可见性（C1-009 建议）；
   - clearDirty 移至持久化成功之后（AbstractUserContextCache:129 契约修正，C1-010 F3③ 共因）；
   - saveUserContextAsync 增加 Local 路径会话有效性守卫（R-CL-1，与 DaoUserContextCache:116-117 对称）；
   - 回归正面样例 7 项（JwtAuthTokenProvider、LoginAttemptStores、GatewayInterceptorModel、AiRateLimitGatewayInterceptor、LocalEmailCodeStore/SmsCodeStore、DbMfaChallengeStore 条件 UPDATE 先例、OrmBatchLoader 同锁注册）勿回归。
2. **订阅注册表工单（C1-003 + C1-005 + CL-002）**：putIfAbsent 判重 + 容量 put 后复核回滚（或信号量预扣）+ unregister 列表清理 compute 单键原子化；验收同时断言无幽灵条目与 `subscriptionsByOperation` 条目数上界不变式。
3. **Dao flush 工单（C1-010）**：条件 UPDATE（平台内同型先例 DbMfaChallengeStore.markVerified）+ 刷新单飞（per-session in-flight 去重）+ flush 失败日志（AuthHttpServerFilter:375 返回值丢弃面）；与设施工单的 clearDirty 契约修正同批。
4. **单行/小修批**：C1-006（verifiedAt volatile）、C1-008（标志 volatile 或 AtomicBoolean）、C1-004（去装饰锁或 request 读写统一 monitor，连同 setRequestHeaders:466 一并纳入同步域）、C1-007（init 预构建或局部变量构建完再发布）。
5. **移交项**：CL-016→C4（runOnContext 队列内 syncGet 阻塞；第三写者线程型须纳入 C1-001 修复验证面）；CL-017→C4/C5（sys flush NPE 面）；CL-001→C6/C7（容器适配层 onClose/onThrowable 必达契约对账）；R-CL-1→C4/安全复核；JsonRpcWebSocketHandler 容器线程模型契约钉定→ws 适配模块。

**现状标注汇总**：10 条发现 + 5 条 CL + R-CL-1 全部为**新发现/未修复**（git log 无审计后修复提交；cra-index 台账 WI 1.1–2.2 不含本单元）。
