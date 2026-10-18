# C1 共享可变状态与可见性 — 第 2 轮深挖（盲审）

## 审计单元

`nop-service-framework-hotpath`（请求热路径五模块 / 8 源码根，src/main only）＋ C1-001 失效面按指令延伸的跨模块同族兄弟（nop-auth-service / nop-auth-sso，仅作失效面枚举与后果链推演，不改判其归属审计单元）。

**仓库**：`/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`（分支 `concurrency-audit-remediation`）

## 本轮输入

- 第 1 轮报告 `C1-shared-mutable-state.md`（C1-001~008 + CL-001~003，唯一准入的历史审计文件）。
- 基线 `_tmp/cra-baseline-nop-service-framework-hotpath.txt`（C1/C2 节站点清单）。
- live code 实测（下文所有行号均为当前分支实测）。

## 盲审纪律声明

本轮为盲审轮次：审计过程未读取 `ai-dev/audits/`（除第 1 轮 C1 文件外）、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件；未读取同目录其他维度（C2/C3/C4/C8/C9）文件。所有判定基于第 1 轮 C1 报告、基线与本轮 live code 独立实测。

## 本轮目标（逐条执行记录）

| # | 指令 | 执行方式 | 结论 |
|---|---|---|---|
| ① | C1-001 失效面扩展 | UserContextImpl 全部集合/标量字段变异点矩阵逐一枚举；DaoUserContextCache/OAuthLoginServiceImpl/MfaLoginPolicyServiceImpl 读写路径交叉矩阵；Dao 部署 dirty 丢更新后果链逐环推演（含 NopAuthSession ORM 模型 version 列实测） | 新增 C1-009、C1-010；F3 修正第 1 轮两处枚举误差 |
| ② | check-then-act 全扫 | 脚本化全扫 8 源码根：`containsKey` 33 处、`if(x==null)→字段赋值` 惰性初始化 120+ 处、`size()` 容量门 18 处，逐一判定共享性/原子性 | 无新增可立案站点（C1-003/C1-005 即全集）；逐站点判定见 §2 |
| ③ | 静态可变状态复核 | 两遍独立扫描（严格字段模式 + 宽松多行/INSTANCE 补扫），复核第 1 轮「仅 2 处且正确防护」结论 | 结论成立，计数修正 2→3（见 §3） |
| ④ | B 面引擎级缓存 | GraphQLEngine.documentCache（LocalCache→Caffeine 装配实测）、builtinSchema、GatewayHttpFilter.handlerCache、DataLoader 请求隔离（OrmBatchLoader/GraphQLExecutionContext 全读） | 条目纪律成立；DataLoader 无跨请求泄漏；builtinSchema 记 P3 卫生项不入案（见 §4） |
| ⑤ | 发布安全 | 异步边界每请求对象发布面（runOnContext / whenComplete / thenApply / cache put）逐边判定 | 提交边 HB 全部成立；弱点面与 C1-001/004/006 同源，扩展线索 CL-016/CL-017（见 §5） |

---

## 1. 目标①：C1-001 失效面扩展

### 1.1 UserContextImpl 变异点全矩阵（实测修正）

字段清单（`UserContextImpl.java:22-46`）：13 个标量字段 + `roles`(HashSet) + `attrs`(继承自 `ExtensibleBean` 的 LinkedHashMap) + `dirty`(boolean)。

| 字段 | 写者（同步性） | 读者（同步性） | 判定 |
|---|---|---|---|
| sessionId/tenantId/locale/timeZone/userId/deptName/loginType/userName/nickName/primaryRole/deptId/openId | 各自 setter，**全部非 synchronized**，写后置 `dirty=true`（:81-217） | getter 全部非同步 | C1-001 已立案；**计数修正：非同步 setter 实为 17 个**（第 1 轮 F3 记 16 个，漏计 W13 新增的 `setMfaRestricted` :314-319） |
| accessToken/refreshToken/lastAccessTime | setter 非同步（:237-264, :248-253） | getter 非同步 | C1-001 已立案 |
| `dirty` | `markDirty/clearDirty/dirty()` 全部非同步非 volatile（:300-352） | flush 路径 `AbstractLoginService:52` | C1-001 已立案 |
| `roles` | **写者实为 3 个**：`addRole`(:267 sync)、`removeRole`(:276 sync)、**`setRoles`(:224 非同步)** ——第 1 轮 C1-002 F3 称「写侧仅 addRole/removeRole 两处，已全覆盖」**有误** | `isUserInRole`(:322)、`isUserInAnyRole`(:334)、`getRoles()`→`AbstractLoginService.getRoleInfos`:158 / `LoginServiceImpl.getRoleInfos`:721（迭代）、`serializeToJson`:63、`DaoUserContextCache.saveUserContextAsync`:126（`map.put("roles", userContext.getRoles())`→`JsonTool.stringify` 迭代）——全部无锁 | C1-002 已立案；setRoles 归并为 C1-002 同域第三写者（live 生产调用点 `LoginServiceImpl:704`/`OAuthLoginServiceImpl:181`/`AuthHttpServerFilter:288` 均发生在实例发布前，当前无可达交错，故不另立案，修复须一并纳入同一同步域） |
| `attrs`（ExtensibleBean LinkedHashMap，非 volatile） | `setAttrs`(:285 sync)、`setAttr`(:293 sync)；**继承公开可变器 `addAttrs`/`removeAttr`（ExtensibleBean:38-56）非 synchronized、不置 dirty、绕过子类锁** | `getAttrs/getAttr`（ExtensibleBean:23-36）非同步；`serializeToJson`:72、`DaoUserContextCache`:135-136 读侧无锁 | **新缺陷 C1-009** |

### 1.2 三服务读写路径交叉矩阵

| 服务 | 对 UserContext/session 的写 | 对同一状态的读 | 交叉竞态判定 |
|---|---|---|---|
| `AbstractUserContextCache`（8 源码根内基座） | `saveUserContextAsync`:128-132 **先 `clearDirty()` 再 put**（clearDirty 前置于持久化——持久化失败即丢更新窗）；`getUserContextAsync`:107-120 缓存实例原样外发 + 会话不匹配时 fire-and-forget `removeAsync`:115 | 同上 | C1-001 主案；新增细节：clearDirty 与持久化的顺序意味着**任何一次持久化异常都等价于静默丢更新**（dirty 已清） |
| `DaoUserContextCache`（nop-auth-service，条件装配 `nop.auth.login.use-dao-user-context-cache`） | `getUserContextAsync`:48-85 **每请求从 DB 新建 UserContextImpl**（共享实例面在该部署下消失）；`checkExpired`:100-103 **读路径副作用写**（session.setLogoutType/setLogoutTime）；`saveUserContextAsync`:111-146 read-modify-write NopAuthSession 行（无 version 列，实测 `_app.orm.xml` 实体无 optimistic-lock 声明） | 同行级 | **新缺陷 C1-010**（DB 层丢失更新，机制与 C1-001 的 JMM 丢 dirty 不同）；另：`LocalUserContextCache.init()` 构造的 userContextCache/userSessionCache 在 Dao 部署下全部覆写方法绕过、成为死重内存 |
| `OAuthLoginServiceImpl`（nop-auth-sso） | `refreshToken`:248-268 在 **HTTP 客户端回调线程**（thenApply）变异共享实例（setAccessToken/setRefreshToken→markDirty）；`buildUserContext`:163-185 登录期新建实例（发布前，安全） | `newLogoutRequest`:215-227 读 getAccessToken/getRefreshToken；flush 序列化 | 写者线程集合扩展（请求池线程 + runOnContext 队列线程 + HTTP 客户端 IO 线程三类），归并 C1-001 F3；syncGet 阻塞面 → CL-016 |
| `MfaLoginPolicyServiceImpl`（nop-auth-service） | `completeRestricted`:117-137 变异 impl（setMfaRestricted/setLastAccessTime）后 saveUserContextAsync；`ensureSessionRow`:140-163 幂等落行 | Dao flush 序列化 | 实例为 OAuth 登录期新建、发布前变异——共享面小，维持第 1 轮「③ 低危兄弟面」判定；ensureSessionRow 的 getEntityById→saveEntity check-then-act 在单会话签发流内串行，不立案 |
| `LoginMfaFlow`（nop-auth-service，宿主装配） | `:212-216` `ctx.setAttr(ATTR_TRUSTED_DEVICE_REGISTERED,...)`、`:335-337` `ctx.setAttr(ATTR_MFA_ACCESS_CODE,...)`——**发生在 `completeLogin` 的 `saveUserContextAsync`（先发布进缓存）之后的 thenApply 里** | 后续 Dao/Local 读取 | 「先发布后变异」实例：Local 部署下同客户端窗口内无并发读者（sessionId 尚未回传客户端），后果仅剩 dirty 标志在 save 之后被置位（下一请求多一次 flush）——作为 C1-009/C1-001 的模式佐证记录，不独立立案 |

### 1.3 Dao 部署下「dirty 丢更新」完整后果链（指令点名的「令牌回退→再登录风暴？」）

**逐环推演（每环均经 live code 实测）**：

1. Dao 部署下 `getUserContextAsync` 每请求新建实例（DaoUserContextCache:66-82），C1-001 的「同实例共享变异 + 非 volatile dirty 竞态」前提在该部署下**不成立**——dirty 只被本请求线程与 flush 回调触碰。
2. 但 flush 的持久化形态变为：`DaoUserContextCache.saveUserContextAsync:114-145` = `getEntityById(sessionId)`（读行）→ 内存变异实体（setAccessToken/setRefreshToken/setCacheData/setLastAccessTime）→ `userContext.clearDirty()`:144 → `@SingleSession` 提交时落库。**读-改-写跨越整个 futureRun 周期且无版本控制**（`NopAuthSession` ORM 模型实测无 version/optimistic-lock 列）→ 同 session 并发请求 T1/T2 的 flush 构成 DB 层丢失更新，last-committer-wins。
3. **令牌回退**：T1（请求 A）触发半衰期刷新（AuthHttpServerFilter:357-366 → `LoginServiceImpl.refreshToken:954-959` 生成 AT1/RT1）；T2（请求 B）的上下文副本持有刷新前读到的 AT0/RT0。T2 的 flush 晚于 T1 落库 → DB 回写 AT0/RT0，**T1 的旋转被静默回滚**。反向顺序（T2 先 flush）则 T1 覆盖——两次 flush 必有一次丢失，且丢失方向不可控。
4. **纯 JWT（JwtAuthTokenProvider）部署**：令牌校验无状态（parseAuthToken 验签即过，DB 副本不参与校验），回退后果退化为 DB 副本陈旧 + `lastAccessTime` 轻微回退（会话过期判定漂移，量级=请求间间隔）——**无害**。
5. **SSO（OAuthLoginServiceImpl）部署 → 再登录风暴链成立**：IdP refresh token 通常一次性旋转。T1、T2 的 `refreshToken`（:248-268）先后以**同一个 RT0** 调 IdP：T1 成功（IdP 作废 RT0、签发 RT1），T2 收到 error → 返回 null（不变异）。但 T2 的 flush（第 3 环）把 RT0 写回 DB。下一半衰期窗口，任一请求从 DB 载入 RT0 → IdP 拒绝已消费 token → `refreshToken` 返回 null → 响应无新 token → 客户端继续用旧 AT 直到过期 → 硬 401 → **全部 Tab 同时重新登录**（风暴放大：每用户每窗口一次，多端同源触发）。
6. 风暴的「静默性」三重保障：flush 返回值在 `AuthHttpServerFilter:375` 被丢弃（无人等待/记日志）；`clearDirty` 先于落库（重试语义已死）；saveUserContextAsync 对 `session==null || logoutType!=NONE` 静默 return（:116-117）。三者叠加使丢失更新无任何可观测痕迹。
7. 结论：指令问句「令牌回退→再登录风暴？」的答案是——**Dao + SSO 双条件部署下成立且为设计级缺陷（分布式 read-modify-write 无并发控制），Dao + 本地 JWT 部署下退化为无害陈旧副本**。修复归属 nop-auth-service（超本轮 8 源码根），但 `AbstractUserContextCache.saveUserContextAsync` 的 clearDirty-先于-持久化 契约（8 源码根内）是共因，修复须一并覆盖。

---

## 2. 目标②：check-then-act 全扫（逐站点判定）

**扫描面**：8 源码根 `containsKey`（33 处）、`if(x==null)→同名字段赋值` 惰性初始化（120+ 处，脚本提取）、`size()` 容量门（18 处）、`get→null check→put` 共享 map 面（GatewayHttpFilter.handlerCache/AiRateLimitGatewayInterceptor/LoginAttemptStores 等）。

### 2.1 containsKey 站点判定

| 站点 | 载体共享性 | 判定 |
|---|---|---|
| GraphQLSubscriptionManager:92 | 引擎级 CHM | **C1-003(b) 已立案** |
| JsonRpcWebSocketHandler:330 | per-connection CHM | **C1-005 已立案** |
| LocalMfaChallengeStore:89 | 引擎级 CHM（failCounts 清扫） | `keySet().removeIf` 逐元素原子 + 谓词读另一 CHM——最坏多清/漏清一个孤儿计数，自愈；**可接受** |
| GraphQLWebService:463、GraphQLArgsHelper:115/189/220、GraphQLSelectionResolver:521、OrmEntityPropConnectionFetcher:188/210、ObjMetaBasedValidator/CrudBizModel 全部 size 判断 | per-request 局部结构 | 单线程触达，**安全** |
| GraphQLBizModels:48、`_gen/*` 模型类（BizModel/GatewayModel/DataAuthModel 等 9 处） | 启动构建后只读注册表 | 启动 HB，**安全** |
| BizObjectBuildHelper:40、BizProxyInvocationHandler:88、OrmEntityCopier:188 | 构建期/调用期局部 | **安全** |
| GeminiMessageConverter:139、ConverterUtils:90/114/138、OpenAiDeltaAccumulator:37/87/93 | per-request 转换/累积对象（LoggingInterceptor:32-33 每请求 new + svcCtx attribute 隔离，实测） | **安全（请求隔离成立）** |

### 2.2 惰性初始化站点判定（共享态子集）

| 站点 | 形态 | 判定 |
|---|---|---|
| AiRateLimitGatewayInterceptor:116-124（buckets）、LoginAttemptStores:30-34（sharedDefault）、GatewayInterceptorModel:27（cached）、GraphQLFieldDefinition:109（typeConverter）、CrudBizModel:1333（cascadeProps，纯派生同值覆盖） | volatile/DCL 或幂等 | 第 1 轮正面样例 + #18 校准延续，**可接受** |
| **LoginServiceImpl:606-612（mfaFlow）、:615-621（mfaCodeSender）、:250-256（defaultRateLimiter）** | singleton bean 字段非同步惰性初始化，字段非 volatile | 实测 `LoginMfaFlow`/`MfaCodeSender` 全 **final 字段**、构造器无 this 逃逸 → final 冻结语义保证发布安全；重复创建幂等（无状态包装组件）。与第 1 轮 #18 同校准：**良性，不立案**（建议收敛至 @PostConstruct，卫生项） |
| GrpcServer:81（executor）、BizObjectManager:122（typeRegistry）、LocalUserContextCache:31/35、AuthHttpServerFilter:114 | 启动期（newBuilder/start、@PostConstruct）单线程触达 | **安全（启动 HB）** |
| OrmBatchLoader:45-53、GraphQLExecutor:391-401（serialChain 局部+resultLock）、BeanMethodBatchFetcher:64 | synchronized(context) 域内 | **安全（同锁串行化，注释自证）** |
| GraphQLSelectionSet:56、GraphQLInterfaceDefinition:24、AST 惰性结构（WithDirectives:30/GraphQLDirective:50/GraphQLDocument:114/GraphQLObjectDefinition:62,112/GraphQLOperation:57/GraphQLFieldSelection:116） | 共享 AST 惰性 map | C1-007 已立案（P3 防御性）；其余为解析期触达（documentCache loader 串行化），**同 C1-007 防御面** |
| LazyGraphQLArgsNormalizer:25、GraphQLBizModel:359-362、其余 ~80 处 | per-request/局部变量 | **安全** |

### 2.3 容量门判定

`GraphQLSubscriptionManager:87`（C1-003(a) 已立案）、`JsonRpcWebSocketHandler:324`（C1-005 同族已立案）、`LocalMfaChallengeStore:72-91` / `LocalEmailCodeStore:77`：check 在 put 之外——并发 create 可瞬时超限（超限量=并发度），但「清扫过期+按插入序驱逐」使上界自愈（注释 ：67-71 自证该权衡）；属 C5 有界性 best-effort 设计而非 C1 竞态缺陷，**可接受**。

**② 结论**：全扫完成。共享注册表 TOCTOU 的可立案全集仍为 C1-003 + C1-005 两处，**无新增站点**。第 1 轮该族发现已覆盖全部真实站点。

---

## 3. 目标③：静态可变状态独立复核

**方法**：不依赖第 1 轮 grep 口径，自行两遍扫描（严格单行字段声明模式；宽松补扫多行声明/INSTANCE/静态块），再逐个核对写点。

**实测结果**：

1. 严格模式命中 2 处：`LoginAttemptStores.sharedDefault`（:23，volatile + 正确 DCL，:29-39 实测复核通过）、`MediaTypeHelper.configLoaded`（:32，volatile；check-then-act 非原子但 loadConfig 幂等 + CHM 表，注释自证）——与第 1 轮一致。
2. 宽松补扫新增 1 处第 1 轮未单列的静态非 final 字段：`MediaTypeHelper.fileExtToMediaType`（:22，`private static Map` **非 final** 但声明后无再赋值、CHM 实现、`registerMediaType(s)` 公开写点为 CHM 原子 put）——防护正确，仅计数修正：**静态非 final 字段实为 3 处而非 2 处**，第 1 轮结论「均已正确防护」经独立复核**成立**。
3. `public static` 非 final 单例引用实测 **17 处**（graphql-core 4 + grpc marshaller 12 + DefaultBizHashFunction 1），全部内联 `= new X()` 类初始化期构建、类无状态字段（DictLabelFetcherProvider/ObjMetaToGraphQLDefinition/ReflectionGraphQLTypeFactory/GraphQLActionAuthChecker 逐一复核）→ 类初始化安全发布。与第 1 轮计数一致。
4. **static final 集合复核**（第 1 轮未逐个验证写点，本轮补全）：`GraphQLScalarType.stdMap/textMap/typeMap`（HashMap!）与 `GraphQLDirectiveLocation.s_map`——实测全部写点仅存在于 `static {}` 初始化器（GraphQLScalarType:50-68），8 源码根内无运行期 register 调用方 → 初始化后只读，安全。`MediaTypeHelper.fileExtToMediaType` 例外（有公开运行期写点），已由 CHM 覆盖。
5. `ServiceContextKey.CONTEXT_KEY`（public static 非 final，无再赋值）——维持第 1 轮 P3 卫生项判定。

**③ 结论**：第 1 轮核心结论（无未防护静态可变状态）**独立复核成立**；计数口径修正 2→3，另补 static-final-HashMap 写点证明。

---

## 4. 目标④：B 面引擎级缓存条目纪律

### 4.1 GraphQLEngine.documentCache（解析缓存）

- 装配实测：`GraphQLEngine:134-139` 构造 `LocalCache.newCache(..., this::parseDocumentWithLoader)`；`LocalCache.buildCache:118-126`（nop-commons）实测 loader 存在 → `builder.buildAsync(CacheLoader)` + `get()` 经 `loadingCache.get`（:207-211）。
- 条目纪律：Caffeine AsyncLoadingCache 对同 key 并发加载按 per-key future 原子去重（第二请求 join 同一 future），**既无重复解析也无 CHM bin 全局阻塞**——比 `computeIfAbsent` 锁内加载面更优；loader 本体 `parseDocumentWithLoader:283-294` 在调用线程执行、结果为 `ResourceCacheEntryWithLoader`（volatile object + synchronized loadObject，nop-core 基建）。加载结果 GraphQLDocument `freeze(true)` 冻结。**判定：可接受（条目并发纪律成立）**。
- `clearCache()`:145-147 存在运行期调用方（`DevToolBizModel:39`，开发工具 reload）——`documentCache.clear()` 为 Caffeine invalidateAll，并发安全；与在途请求仅构成「解析结果被作废后重解析」的良性语义。**无缺陷**。

### 4.2 builtinSchema（Schema 缓存）

- `private GraphQLSchema builtinSchema`（:107）**非 final 非 volatile**：`@PostConstruct init():249` 写 / `setBuiltinSchema`:149-151 公开 setter。请求线程读点 7 处（:293/:326/:332/:339/:345-346/:355/:498）。发布依赖容器启动 HB（实测 `setBuiltinSchema` 生产代码零调用方，仅测试可达）。当前安全，但与 C1-008 同属「init 后只读靠约定而非类型系统保证」——记 **P3 卫生项（建议 final 化或去公开 setter），不入案编号**（写不出具体触发时序，按纪律降档）。

### 4.3 DataLoader 请求隔离复核（第 1 轮提示过泄漏面）

- `GraphQLExecutionContext.loaders`（:42）为 **per-request CHM**；`OrmBatchLoader.makeDataLoader:40-55` 每上下文 `DataLoaderFactory.newDataLoader(new OrmBatchLoader(...))` 新实例 + `options.setCachingEnabled(false)`，注册与 `dispatchAll`:156-170 的快照-清空共用 `synchronized(context)`（注释自证先注册者胜出）。**引擎层不存在共享 DataLoader 实例 → 无跨请求泄漏面**。第 1 轮载体清单 #15 判定**独立复核成立**。
- 残余面（不入案）：`registerDataLoader` 本身非 synchronized（:142-148），当前仅 makeDataLoader（持锁）与 BeanMethodBatchFetcher（持锁）调用——纪律靠调用方约定维持，新增调用方须持同锁；归入 C1-004 的「锁体系靠约定」观察。

---

## 5. 目标⑤：发布安全（非 final 字段跨线程发布，仅可见性面）

**判定边清单**：

| 发布边 | 机制 | 判定 |
|---|---|---|
| AuthHttpServerFilter:357 `ctx.runOnContext(lambda)` 捕获 userContext/routeContext | Executor 提交语义（任务入队 happens-before 执行） | **HB 成立** |
| handleUserContext:374-376 `whenComplete(flush)` | CompletableFuture 完成语义（完成前写 → 依赖动作可见） | **HB 成立**（单请求内） |
| OAuthLoginServiceImpl:258 `thenApply` 内变异 | CF 完成语义 + `syncGet` 返回后同线程续行 | 链内 HB 成立；**链外读者（下一请求缓存读/序列化）无 HB → 归并 C1-001** |
| LocalCache.put / putAsync 发布 UserContextImpl 进缓存 | 实测 `putAsync`（LocalCache:314-317）为**同步 put + 已完成 future**——put 的 CHM 释放语义保证「put 时刻前写」对后续 get 可见 | 首次发布安全；**put 之后的原地变异不受保护 = C1-001 本体** |
| GatewayContextImpl（getCurrentRoute/setCurrentRoute 等 6 对） | 全 synchronized（基线 C2 节实测一致），无新增成员字段（:16-66 实测注释自证「不引入新成员变量」） | **安全** |
| GraphQLExecutionContext 其余字段（operation/fieldSelection/makerCheckerEnabled/operationMfaChecker） | 非 volatile；当前调用序为「请求线程写完 → 执行开始 → 异步续行读」，靠 CF 链 HB | 当前调用序下安全；**与 C1-004 同构的结构地雷**（任何「异步化后再写」重构即打开窗口）——作为 C1-004 的 F3 扩展登记，不另立案 |
| JsonRpcWebSocketHandler.authHeaders/boundUserId | volatile（第 1 轮 #19 已核） | 安全 |
| BufferedStreamingPublisher/BufferedSubscription | 全字段 synchronized(this) 域内（基线 C2 节） | 安全 |
| SubscriptionInfo | CHM 发布后字段无写点（`setFlowSubscription` 在 8 源码根内零调用，实测） | 安全（ws 集成模块若赋值 flowSubscription 须复核——超出本轮范围） |

**⑤ 结论**：异步边界**提交/完成边的发布语义全部成立**；本轮未发现独立于 C1-001/004/006 的新发布缺陷。弱点集中在「CHM/CF 首次发布之后的原地无同步变异」（C1-001/C1-006 本体）与「靠调用序而非类型系统维持的可见性约定」（C1-004 本体及其扩展）。

---

## 第 2 轮新增发现

### [nop-service-framework-hotpath-C1-009] UserContextImpl attrs 域锁旁路：继承公开可变器绕过 synchronized 且不置 dirty，无锁读与持锁写构成混合同步

- **文件**: `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/login/UserContextImpl.java:285-298`（sync 覆写）、`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/ExtensibleBean.java:20-56`（attrs 本体 + 旁路可变器）、`UserContextImpl.java:72`（serializeToJson 无锁读）、`nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/DaoUserContextCache.java:135-137`（flush 序列化无锁读）
- **证据片段**:
  ```java
  // ExtensibleBean —— 子类锁域之外的公开可变器（写后既无锁也不置 dirty）
  public void addAttrs(Map<String, Object> attrs) {
      if (attrs != null) {
          if (this.attrs == null) this.attrs = new LinkedHashMap<>();   // 普通写，非 volatile
          this.attrs.putAll(attrs);                                      // LinkedHashMap 批量写
      }
  }
  public void removeAttr(String name) { if (attrs != null) attrs.remove(name); }
  // UserContextImpl —— 仅有两个入口被 synchronized 覆写并置 dirty
  @Override public synchronized void setAttr(String name, Object value) { ... super.setAttr(name, value); dirty = true; }
  // 读侧（serializeToJson / Dao flush）完全无锁
  out.putNotNull("attrs", getAttrs());        // UserContextImpl:72
  if (userContext.getAttrs() != null) map.put("attrs", userContext.getAttrs());  // DaoUserContextCache:135
  ```
- **严重程度**: P2（结构缺陷确定；当前生产调用点中 attrs 写多发生在实例发布前，跨线程交错需 SPI 扩展点触发，故未判 P1）
- **并发场景**: 缓存共享 UserContextImpl（LocalUserContextCache 部署）被同用户并发请求触碰时：(a) 请求 A 经 `setAttr`（持 this 锁）写 LinkedHashMap，请求 B 的 flush（`DaoUserContextCache.saveUserContextAsync:135`）或 `serializeToJson:72` **无锁**迭代同一 map → CME 或读到半态 attrs；(b) 任何代码经继承的 `addAttrs/removeAttr` 写入则**完全脱离锁域且不置 dirty**——既构成无同步写，又使变更对 flush 契约不可见（永不持久化）。(c) `IUserContextHook.onAccess/onUpdate`（AbstractLoginService:56-57,82-83）以共享实例为入参的 SPI 扩展点是现成的跨线程写者入口。
- **触发时序**: T1（请求 A，用户 U）执行 `userContextHook.onAccess(ctx)`，hook 实现调用 `ctx.addAttrs({"lastRoute":...})`（LinkedHashMap putAll 中）；T2（请求 B，同用户）请求结束进入 flush → `saveUserContextAsync` → `getAttrs()` 无锁迭代该 map → CME 抛出即该请求 500，或读到部分插入的 attrs 序列化入库。并行地，T1 的 addAttrs 变更未置 dirty → 即使无并发异常，该变更在下一次 flush 中被跳过（除非别的 setter 恰好置脏）——**脏标志契约对该写入口静默失效**。
- **现状**: `attrs` 字段非 volatile（ExtensibleBean:20）；四个写入口中两个持 this 锁、两个完全无锁；dirty 契约只覆盖持锁入口。锁、字段、dirty 三层的保护面互不一致。
- **风险**: 权限/会话扩展属性的并发读不一致（低概率 CME 请求失败 + 属性半态持久化）与「扩展点写入永不生效」的功能陷阱叠加；后者在 hooks/租户切换标记（测试语义 `tenant-switch` 已见用例）等场景会表现为难排查的偶发丢属性。
- **建议**: (1) UserContextImpl 覆写并 synchronized `addAttrs/removeAttr`（与 setAttr 同域）并补 `dirty=true`；(2) 或将 attrs 收敛为 `Collections.synchronizedMap`/CopyOnWrite 语义 + 读侧快照；(3) 最小修正：ExtensibleBean.addAttrs/removeAttr 标记 @Deprecated 或收窄可见性，迫使扩展点走 UserContextImpl 持锁入口。
- **失效面枚举**（F3 同族清扫）: ① `ExtensibleBean.addAttrs/removeAttr`（本类，旁路写者全集——UserContextImpl 未覆写其余可变器，实测无更多旁路）；② `getAttrs/getAttr`（ExtensibleBean:23-36，无锁读者）；③ `UserContextImpl.serializeToJson:72`（无锁迭代触发点）；④ `DaoUserContextCache.saveUserContextAsync:135-137`（flush 序列化触发点，跨模块同族）；⑤ 同域修正：`setRoles`（:224 非同步）是 roles 字段第三个写者，第 1 轮 C1-002 F3「写侧仅两处已全覆盖」不成立，修复须与 roles 同批纳入同一同步域；⑥ 写点矩阵补全：非同步 setter 实为 17 个（第 1 轮记 16，漏 `setMfaRestricted`:314），全部已在 C1-001 F3④ 覆盖语义内。
- **信心水平**: 确定（锁旁路与 dirty 契约破口是代码事实；跨线程触达依赖 hook/attrs 写入路径启用）
- **误报排除**: 不是「继承即可疑」泛化——已逐一核实 UserContextImpl 仅 attrs 一域存在「子类加锁覆写 + 父类公开可变器绕行」的组合；也不是把「写侧已同步」误当安全——恰是锁对无锁读者与旁路写者同时失效。
- **复核状态**: 已复核——**维持 P2**。结构缺陷证实：ExtensibleBean.addAttrs:50-56 / removeAttr:45-48 公开无锁且不置 dirty、attrs :20 非 volatile、UserContextImpl 仅 setAttrs:285-290/setAttr:293-298 持锁覆写、serializeToJson:72 与 DaoUserContextCache:135-136 无锁读。**可达性实测（复核 grep）**：addAttrs/removeAttr 平台内对 UserContext 零生产调用（grep 命中均为 XML 模型类同名方法）；IUserContextHook 平台内无实现（仅接口 + AbstractLoginService:56-57/82-83 调用管线）——SPI 入口管线属实（onAccess 每请求携共享实例过 hook），旁路写者当前为扩展点专属，与初审「跨线程交错需 SPI 扩展点触发」自评一致。LoginMfaFlow:212/336 setAttr（持锁+置脏）为平台内真实写者，佐证先发布后变异模式。维持 P2。现状：新发现/未修复。

### [nop-service-framework-hotpath-C1-010] DaoUserContextCache 并发 flush 对 NopAuthSession 行的 DB 层丢失更新：令牌旋转被回写回滚，SSO 部署下形成「刷新链断裂→再登录风暴」链

- **文件**: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/DaoUserContextCache.java:109-146`（read-modify-write flush）、`:87-107`（checkExpired 读路径副作用写）、`nop-auth/nop-auth-dao/src/main/resources/_vfs/nop/auth/orm/_app.orm.xml`（NopAuthSession 实体实测无 version/optimistic-lock 列）、`nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/filter/AuthHttpServerFilter.java:374-376`（flush 返回值丢弃）、`AbstractUserContextCache.java:128-132`（clearDirty 先于持久化）
- **证据片段**:
  ```java
  // DaoUserContextCache.saveUserContextAsync —— getEntityById 与落库之间跨越整个异步周期，无版本控制
  return FutureHelper.futureRun(() -> {
      NopAuthSession session = dao().getEntityById(userContext.getSessionId());   // 读
      if (session == null || session.getLogoutType() != ...NONE) return;
      ...
      session.setAccessToken(userContext.getAccessToken());    // 改（本请求上下文副本，可能已是旧 token）
      session.setRefreshToken(userContext.getRefreshToken());
      session.setCacheData(JsonTool.stringify(map));
      session.setLastAccessTime(...);
      userContext.clearDirty();                                // 清脏先于真实落库（@SingleSession 提交在其后）
  });
  ```
- **严重程度**: P2（后果会话级且不可逆，但触发条件化为 Dao 部署 + SSO 旋转 IdP + 同 session 并发请求叠加，按「条件化路径正常降档」取 P2； Dao+本地 JWT 部署下无害——见后果链分档）
- **并发场景**: 同一 sessionId 的并发请求（多 Tab/前端聚合）各自经 `getUserContextAsync`（:48-85，每请求从 DB 新建上下文副本——此部署下 C1-001 的共享实例面消失，竞态转移到 DB 行）持有 RT/lastAccessTime 各自快照，请求尾 flush 并发 read-modify-write 同一行。
- **触发时序**: 完整链见 §1.3。核心交错：T1 触发半衰期刷新得 RT1（IdP 已作废 RT0）→ T1 flush 落库 RT1；T2（副本仍持 RT0）flush 晚到 → 回写 RT0 → 下一窗口任一请求以 RT0 刷新 → IdP 拒绝已消费 token → refreshToken 返回 null → 客户端旧 AT 到期硬 401 → 该用户全部终端同时重登（风暴）。每环均实测：flush 返回值在 AuthHttpServerFilter:375 被丢弃、clearDirty 先于落库、logoutType 静默 return——三重静默保障使丢失不可观测。
- **现状**: 实体无乐观锁版本列（ORM 模型实测）；flush 无「compare-and-swap 语义」（对比同文件兄弟 `LoginMfaFlow` 恢复码路径已用原子 EQL UPDATE + affected-row 的正确先例）；另有 `checkExpired:100-103` 在 getUserContextAsync 读路径内对 session 实体做 logoutType/logoutTime 副作用写（并发读同行为双重标记，仅冗余写、无正确性损害）。
- **风险**: SSO+Dao 部署为确定可复现的会话级事故（每半衰期窗口一次、多端放大）；Dao+本地 JWT 部署退化为 DB 副本陈旧 + lastAccessTime 微回退（无害）。与 C1-001 机制不同（JMM 丢 dirty vs DB 丢失更新），修复分治：本条需行级原子写（`UPDATE ... WHERE sessionId=? AND refresh_token=?` 乐观条件写或 version 列），C1-001 需对象级同步/快照。
- **建议**: (1) flush 改条件 UPDATE（以读时 refresh_token/lastAccessTime 为乐观条件，affected-row=0 即放弃并告警）——平台内 `DbMfaChallengeStore.markVerified` 已有同型先例；(2) `clearDirty` 移至落库成功回调之后（与 C1-001 建议同批，契约共因）；(3) 刷新单飞（per-session in-flight 去重）消除双请求同打 IdP；(4) flush 失败面补日志（当前 AuthHttpServerFilter:375 返回值丢弃）。
- **失效面枚举**（F3 同族清扫）: ① `DaoUserContextCache.saveUserContextAsync`（本处，读-改-写主体）；② `checkExpired`（:100-103，读路径副作用写，同实体）；③ `AbstractUserContextCache.saveUserContextAsync:129`（clearDirty-先于-持久化契约，8 源码根内共因，Local 部署下同一契约使持久化异常等价丢更新）；④ `MfaLoginPolicyServiceImpl.completeRestricted:117-137`（同写路径的新建实例变体，签发流单线程，共享面小——修复①时顺带覆盖）；⑤ `DaoUserContextCache.getUserContextAsync:66-82`（每请求新建实例 + :81 setLastAccessTime——使上下文副本间 lastAccessTime 天然发散，加剧②的回退幅度）；⑥ 死重面：LocalUserContextCache.init() 构造的 userContextCache/userSessionCache 在 Dao 部署下全部被覆写方法绕过（纯内存浪费，归 C5 观察不入案）。
- **信心水平**: 确定（丢失更新交错可构造且无需 JMM 奇异性；风暴链依赖 SSO IdP 一次性旋转语义，为本条唯一外部假设）
- **误报排除**: 不是 C1-001 的重复立案——前提（共享实例）在本部署下不成立，竞态载体换为 DB 行且机制为写写冲突；也不是「有 DB 就有丢失更新」的泛化——已给出平台内条件 UPDATE 的正确先例对照与双条件部署判定；未把 lastAccessTime 微回退夸大为会话失效（本地 JWT 下显式判无害）。
- **复核状态**: 已复核——**维持 P2**。全链证据亲验：DaoUserContextCache read-modify-write（getEntityById:115 → 变异 :139-142 → clearDirty:144 于 futureRun 内）；NopAuthSession 实体（_app.orm.xml）实体块内 grep 实测 **0 个 version 列**；checkExpired :100-103 读路径副作用写；flush 返回值 AuthHttpServerFilter:375 丢弃；Base 契约 clearDirty-先于-持久化（AbstractUserContextCache:129，Local 路径共因）。双条件部署降档口径正确（Dao+SSO 成立 / Dao+本地 JWT 无害）；与 C1-001 机制分治判定正确。**终裁确认本条为「令牌回退→再登录风暴」链的唯一归属**（独立于 C1-001，见 C1 文件维度复核结论）。现状：新发现/未修复。

---

## 维度集外线索（新增）

### [nop-service-framework-hotpath-CL-016]（维度集外线索，不立案展开）OAuthLoginServiceImpl.refreshToken 在 runOnContext 任务队列线程内 syncGet 阻塞 IdP HTTP 往返（C4 形态）

`OAuthLoginServiceImpl.refreshToken:258` 以 `FutureHelper.syncGet(httpClient.fetchAsync(...))` 在 `AuthHttpServerFilter.handleUserContext:357` 的 `ctx.runOnContext` 任务队列线程内同步阻塞等待 IdP HTTP 往返——该 IContext 任务队列在阻塞期间排空停滞（同上下文后续任务全部滞留一个 IdP RTT+超时窗口），C4「队列内/临界区内 I/O」形态。另：变异实际发生在 thenApply 的 HTTP 客户端 IO 回调线程（:264-265），将 C1-001 的写者线程集合从「请求池线程 + runOnContext 队列线程」扩展出第三类——修复 C1-001 的同步方案须覆盖该线程型。移交 C4 复核阻塞面与队列语义。

### [nop-service-framework-hotpath-CL-017]（维度集外线索，不立案展开）newSysUserContext 上下文必然 dirty，servicePublic 请求尾 flush 触发 userSessionCache.putAsync("sys", null)（Caffeine 禁 null value）同步 NPE（C4/功能形态）

`AuthHttpServerFilter.newSysUserContext:271-290` 经 setUserId/setUserName/setRoles 置 dirty=true（setRoles(emptySet) 对 null 判等必真）；请求尾 `handleUserContext:374-376` flush → `AbstractUserContextCache.saveUserContextAsync:130` `userSessionCache.putAsync(userName, sessionId=null)`。实测 `LocalCache.putAsync`（LocalCache:314-317）为**同步 put + 已完成 future**——Caffeine `Cache.put` 对 null value 抛 NPE，异常在 whenComplete 回调线程同步抛出 → 依 CompletableFuture.whenComplete 语义使外层 promise/future 异常完成 → 每个「servicePath 非 public 且无凭证」请求（servicePublic 开启时每请求）走一次无效 flush + NPE 面。返回 stage 被丢弃导致该失败近乎不可观测。需 C4/C5 复核实际部署表现与 Caffeine null 语义钉定（本线索为推演 + 代码事实，未运行验证）。

### 既有 CL 联动判定（指令要求）

- **CL-002（subscriptionsByOperation 幽灵条目无界累积）与 C1-003(b) 归并判定**：维持归并，不独立立案。CL-002 是 C1-003(b) 在 C5/C7 维度上的投影（同一竞态的资源后果），非独立根因；C1-003 建议的 `putIfAbsent` 修复方案自动消除 CL-002。修复验收须同时断言两点：无重复 id 幽灵条目（C1-003(b)）+ `subscriptionsByOperation` 条目数 ≤ `subscriptionsById` 条目数（CL-002 上界不变式）。

---

## 第 1 轮结论复核汇总（指令③之外的一并复核项）

| 第 1 轮论断 | 本轮复核结果 |
|---|---|
| 「静态非 final 字段全量枚举仅 2 处且均已正确防护」（载体清单计数口径） | **成立**，计数修正为 3（补 fileExtToMediaType，防护正确）；static-final-HashMap 写点补全证明仅 static 初始化器可达 |
| C1-002 F3「roles 写侧仅 addRole/removeRole 两处，已全覆盖」 | **不成立**：`setRoles`:224 为第三个非同步写者（生产调用点均发布前，无可达交错，维持 C1-002 域内修正不另立案） |
| C1-001 F3④「非同步 setter」计数 16 | 修正为 17（漏 W13 新增 setMfaRestricted:314-319） |
| 载体 #15「DataLoader 请求隔离成立，无跨请求泄漏面」 | **独立复核成立**（per-request loaders CHM + per-context DataLoader + cachingEnabled(false) + 同锁注册/调度） |
| 载体 #10「documentCache LocalCache + ResourceCacheEntry 可接受」 | **成立并强化**：Caffeine AsyncLoadingCache per-key 去重加载，无 bin 阻塞；clearCache 运行期调用方（DevToolBizModel）并发安全 |
| 载体 #1「builtinSchema/init 后只读」 | 成立但为约定保证：非 final + 公开 setter（生产零调用方），记 P3 卫生项不入案 |
| 正面样例 5 项（JwtAuthTokenProvider/LoginAttemptStores/GatewayInterceptorModel/AiRateLimitGatewayInterceptor/LocalEmailCodeStore+SmsCodeStore） | 逐一复核**全部维持**（本轮修复轮勿回归）；新增正面样例 2 项：DbMfaChallengeStore 同型条件 UPDATE 先例引用面、OrmBatchLoader makeDataLoader 同锁注册纪律 |

---

## 发现清单（本轮总计）

| 编号 | 严重程度 | 一句话摘要 | 文件 |
|---|---|---|---|
| nop-service-framework-hotpath-C1-009 | P2 | attrs 域锁旁路：继承 addAttrs/removeAttr 绕过 synchronized 且不置 dirty，无锁读混合同步 | UserContextImpl / ExtensibleBean / DaoUserContextCache |
| nop-service-framework-hotpath-C1-010 | P2 | Dao flush DB 层丢失更新：令牌旋转回写回滚，SSO 下刷新链断裂→再登录风暴 | DaoUserContextCache / AbstractUserContextCache / AuthHttpServerFilter |

**新增 CL 线索**：CL-016（syncGet 阻塞 runOnContext 队列线程，C4）、CL-017（sys 上下文必然 dirty→flush NPE 面，C4/功能）。既有 CL-002 与 C1-003(b) 维持归并（验收不变式已写明）。

**总计**：新增 2 条发现（P2×2，无 P0/P1）+ 2 条 CL 线索；第 1 轮 8 条发现 0 条被推翻（2 处 F3 枚举修正 + 1 处计数修正已列入复核汇总表）。

## 终止信号判定

- 新增发现全部为 P2，**无 P0/P1** → 按深挖继续规则不触发第 3 轮强制。
- ② check-then-act 全扫：候选站点穷尽（containsKey/惰性初始化/容量门三族），可立案集合与第 1 轮一致（C1-003/C1-005 即全集）——**同族收敛信号**。
- ③ 静态状态：结论经独立复核成立——**载体面收敛信号**。
- ④⑤ 引擎缓存与发布面：全部判定完毕，弱点与已立案发现同源——**维度面收敛信号**。
- 剩余开口均为跨模块/跨维度移交（DaoUserContextCache 归属 nop-auth-service 审计单元、CL-016 移交 C4、CL-017 移交 C4/C5、JsonRpcWebSocketHandler 容器线程模型契约钉定移交 ws 适配模块）。

**判定：C1 维度达到终止条件，建议以本报告收口（第 3 轮不启动）。**
