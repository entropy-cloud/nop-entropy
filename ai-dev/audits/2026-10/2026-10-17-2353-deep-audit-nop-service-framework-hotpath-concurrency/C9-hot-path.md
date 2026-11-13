# C9 维度审计报告：请求热路径（并发高性能）

## 基本信息

- **审计单元**: `nop-service-framework-hotpath`（请求热路径五模块 / 8 源码根）
- **仓库**: `/Users/abc/app/nop-entropy-wt/nop-entropy-concurrency-audit`，分支 `concurrency-audit-remediation`
- **审计日期**: 2026-10-18
- **维度**: C9——请求热路径（每请求常驻开销：锁竞争、锁内开销、缓存命中路径常数因子、每请求分配热点、每记录循环）
- **规模基线**: 406 文件 / 42,748 行（src/main only，excl target/，见 `_tmp/cra-baseline-nop-service-framework-hotpath.txt` 尾部统计）
- **轮次**: 第 1 轮初审（盲审）

## 维度定义

每请求常驻开销：锁竞争热点（竞争频率量化）、锁内开销、缓存命中路径常数因子、每请求分配热点、每记录循环。**判级校准（plan 2470 对齐口径）**：默认 P2/P3；升 P1 仅限（a）每请求必经链上的全平台串行化点，或（b）掩盖正确性问题。

## 盲审纪律声明

本报告为盲审轮次产出。审计过程中**未读取** `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件（包括同目录已有的 C1–C4/C8 维度文件）。仅阅读了方法论文件 `ai-dev/skills/concurrency-resource-audit-prompt.md`、`ai-dev/skills/deep-audit-prompts.md` 与基线 `_tmp/cra-baseline-nop-service-framework-hotpath.txt`。任务书中给出的已立案项（C2-001 dispatchAll、C2-006 resultLock、CL-010、CL-013）按"量化不重复立案"处理；CL 编号从 CL-015 起使用。

## 检查范围说明

8 源码根：`nop-graphql/nop-graphql-core`（145f/17,097 行）、`nop-graphql/nop-graphql-orm`（11f/859 行）、`nop-graphql/nop-graphql-message`（3f/162 行）、`nop-graphql/nop-graphql-grpc`（37f/3,174 行）、`nop-biz`（89f/9,985 行）、`nop-gateway`（44f/4,743 行）、`nop-biz-auth-core`（60f/5,760 行）、`nop-biz-file-core`（17f/968 行）。

重点核对链路（全部以 live code 通读取证）：

1. **GraphQL HTTP 执行链**：`GraphQLWebService.runGraphQL` → `IGraphQLEngine.newGraphQLContext`（parse 缓存命中路径 + `initGraphQLContext` 的选择树重建）→ `executeGraphQLAsync` → `GraphQLExecutor`（resultLock、dispatchAll、字段游走、`fetchList` 每记录循环）→ 响应序列化。
2. **RPC/REST 执行链**：`runRest` → `initRpcContext`/`initForReturnType`（每请求合成选择树）→ `executeRpcAsync`。
3. **auth 过滤链**：`AuthHttpServerFilter._filterAsync`（`executeBlocking` 跳线程、`parseAuthToken`、`getUserContextAsync` 缓存命中路径、`handleUserContext` flush 路径）→ `AbstractUserContextCache`/`AbstractLoginService`/`JwtAuthTokenProvider`。
4. **Biz 拦截链**：`BizObjectManager.getBizObject`（bizObjCache 命中路径 + 100ms 节流核对）→ `BizObjectImpl`（asProxy 调用时机取证）→ `BizActionInvocation` → decorator 链（`TransactionActionDecorator`/`CacheActionDecorator`——构建期组合，逐 invoke 仅虚调用）。
5. **Gateway 链**：`GatewayHttpFilter.filterAsync`（模型缓存每请求 checkChanged、请求体物化、流式写出）→ `GatewayHandler`（trie 匹配 ×2、`loadInterceptors`、`matchRoute`）→ `StreamingProcessor`/`BufferedStreamingPublisher`/`InvokeProcessor`。
6. **每记录循环抽验**：基线 C9 节 169 个循环站点中定向抽验了结果集序列化/字段遍历/字典翻译/gRPC 编解码站点（`GraphQLExecutor.fetchList`、`DataFetchingEnvironment.prepare`、`BeanPropertyFetcher`、`DictLabelFetcherProvider`、`ProtobufMarshallerHelper`、`JsonFieldMarshaller`、`LocalDateTimeFieldMarshaller`）；全审计域 grep `Pattern.compile|\.matcher(` 仅命中 `BCryptPasswordEncoder`（登录路径，Pattern 为字段级预编译）——**热路径无正则编译**。

### 串行化点清单表（每请求必经锁站点 × 竞争频率量化）

| 锁站点 | 作用域 | 每请求获取次数 | 临界区内容 | 竞争对象 | 判定 |
|---|---|---|---|---|---|
| `GraphQLExecutionContext` 监视器（`dispatchAll`/`getRequest`） | 每请求上下文 | O(异步游走回合数 + 批量 loader 字段数 + 1)，典型 3–30 次 | `isEmpty` 快检 / `HashMap` 拷贝 + 清空 / map get | 仅同请求内的并行分支（BatchFetcher 注册 vs dispatch 泵） | 低竞争，C9-007 量化记录 |
| `GraphQLExecutor.resultLock` | 每请求 executor（`newGraphQLExecutor()` 每请求新建） | O(结果集叶子字段数) | 单次 `LinkedHashMap.put`（O(1)） | 仅同请求内并行完成的分支 | 低竞争，C9-008 量化记录 |
| `JwtAuthTokenProvider.ensureKeys()`（synchronized 方法，单例 bean） | **全平台** | 每个携带凭证的认证请求 1 次 | `isEmpty` 快检（在锁内） | 所有认证请求 | 全平台串行化点，C9-003 立案 P2 |
| `BufferedSubscription` 监视器（流式） | 每条流 | 每 SSE 元素 1 次（`onNext`）+ 每次 `request(n)` | 含**下游 `subscriber.onNext`（JSON 序列化 + 投递）** | 同一条流的上游推送线程与下游 demand 线程 | C9-005 立案 P2（锁内含重操作） |
| `JwtAuthTokenProvider.signKeys` HashMap 读（`getKey`） | 全平台只读（init 后） | 每请求 1 次 | map get | 无写者（初始化后） | OK |
| `TokenBucket.tryConsume`（AiRateLimit） | 每客户端 IP 桶 | 每 AI 请求 1 次 | refill + 比较（O(1)） | 同 IP 并发请求 | OK（临界区常量级） |
| `GatewayContextImpl` synchronized 访问器 | 每请求上下文（线程封闭为主） | 每请求约 6–10 次 | attribute map get/set | 流式回程线程（isStreamingMode 等） | C9-009 立案 P3 |
| `LoginAttemptStores.class` / `LocalLoginAttemptStore` this 监视器 | 全局 / 每 store | 仅登录路径 | 计数器操作 | 登录并发 | 非每请求路径，不立案 |
| `BizObjectImpl.asProxy()`（synchronized） | 每 biz obj 单例 | **0 次/请求**（仅 IoC bean 构建期调用，`BizProxyFactoryBean.build`） | 代理创建 | 启动期 | OK（低频路径粗锁，误报排除） |

### 缓存命中路径核对结论（本维度要求覆盖）

| 缓存 | 命中路径成本 | 判定 |
|---|---|---|
| GraphQL parse 缓存（`documentCache`，key=全查询文本） | `LocalCache.get`（O(len) hash）→ `ResourceCacheEntry.getObject(true)` → `isChanged()`：text-parse 不记录文件依赖 → `deps.getDepends()==null` 快速返回 false；+1 次空 `HashSet` 分配 + 1 次 ThreadLocal get（`traceAllDepends` 栈空早退） | **OK**（`_tmp` 基线 C2-00x 的担心不成立于该路径；浪费仅一个空 HashSet/请求） |
| `BizObjectManager.bizObjCache` | `ResourceLoadingCache.get`：`isRefreshEnabled(refreshMinInterval=100ms 默认)` 节流 + `getObject(b)`，未到节流点为 volatile 读 | **OK**（100ms 节流设计正确；对照 C9-002 的无节流兄弟） |
| auth 用户上下文缓存（`LocalUserContextCache`） | `getAsync`（本地 Caffeine 即时完成）+ `userSessionCache.get`（CHM）+ 请求收尾 `flushUserContextAsync` 的 `dirty()` 检查（脏时 2 次 putAsync） | **OK**（常数因子为 2 次 map 操作；共享可变 `UserContextImpl` 的可见性面归 C1 已立案维度，本轮不重复） |
| Gateway handlerCache | `ResourceCacheEntry.getObject(true, loader)` **无节流** → 每请求依赖树走查 + 文件 stat | **C9-002 立案** |
| 配置引用 `CFG_*.get()`（鉴权逐字段、cacheNull 等） | `provider.get()`（StaticValue/volatile），单次 volatile 读 | OK（nop-api-core 平台设施不重审） |

---

## 发现清单

### [nop-service-framework-hotpath-C9-001] RPC/REST 热路径每请求重建全量合成 GraphQL 选择树（O(字段数×深度) 分配 + 三次全树遍历，可缓存未缓存）

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/engine/GraphQLEngine.java:486-516`（`initForReturnType`），协同证据 `RpcSelectionSetBuilder.java:59-241`、`GraphQLEngine.java:292-296`（`resolveSelections`）、`GraphQLEngine.java:337-341`（`buildSelectionBean`）
- **证据片段**:
  ```java
  // GraphQLEngine.initForReturnType —— 每个 REST/RPC 请求执行
  GraphQLDocument doc = new GraphQLDocument();
  GraphQLFieldSelection selection = doc.addOperation(operationType, operationName, request);
  ...
  GraphQLSelectionSet selectionSet = new RpcSelectionSetBuilder(this.builtinSchema, schemaLoader,
          CFG_GRAPHQL_QUERY_MAX_DEPTH.get()).buildForType(returnType, selectionBean);
  selection.setSelectionSet(selectionSet);
  resolveSelections(doc, CFG_GRAPHQL_QUERY_MAX_DEPTH.get());   // 第 2 次全树遍历
  ...
  FieldSelectionBean fieldSelection = buildSelectionBean(operationName, op.getSelectionSet(), Collections.emptyMap()); // 第 3 次全树遍历
  ```
- **严重程度**: P2
- **并发场景**: HTTP worker / blocking 池线程；每个走 `runRest`（含 `/r/`、`/f/` 页面查询）、`GraphQLMessageSubscriptionRegistrar.onMessage`（消息驱动 RPC）、`AbstractGraphQLFileService.uploadAsync/downloadAsync`（文件服务）、`makeRpcProxy` 的请求都经过此路径。
- **触发时序**（量化链，非竞态而是每请求常驻开销）: 每请求 1 次 → `buildForType`：客户端未给 selection 时经 `addDefaultFieldsForObjType`/FRAGMENT_DEFAULTS 为返回类型**全部非 lazy 字段**递归构建 `GraphQLFieldSelection`，直至 `maxObjLevel=7` 层 → 分配量 O(F×D)（F=返回类型字段数，ORM 实体典型 20–100；D≤7）≈ 每请求 100–700 个 AST 对象 → 随后 `resolveSelections`（new 一个 `GraphQLSelectionResolver` + 全树解析）与 `buildSelectionBean`（全树转 `FieldSelectionBean`）再走两遍 → 单请求纯 CPU 开销估算数十至数百 µs 量级（仅 AST 构建，不含业务执行）。
- **现状**: 合成选择树是 (返回类型, maxDepth) 的确定性函数（variables 为空 Map），但每请求全量重建，三次遍历无任何按类型缓存。
- **风险**: REST API 高 QPS 场景下，这是除业务执行外最大的单请求常驻 CPU/分配项；年轻代分配速率随 (QPS × F×D) 线性放大，加剧 GC 频率。
- **建议**: 按 `typeName → 预构建的 GraphQLSelectionSet/FieldSelectionBean 模板` 建只读缓存（复用平台 `LocalCache`，冻结 AST 后共享只读；`GraphQLDocument.freeze` 已支持），args/directives 的每请求差异部分浅拷贝注入；或至少把三次遍历合并为一遍。
- **失效面枚举（F3 同族清扫）**: ① `runRest`（GraphQLWebService.java:249 调 `initRpcContext`）——本案主面；② `GraphQLMessageSubscriptionRegistrar.onMessage:90`（`newRpcContext` → 同 `initRpcContext`）——同族，未修；③ `AbstractGraphQLFileService.uploadAsync:45`/`downloadAsync:55`（`newRpcContext`）——同族，未修；④ `GraphQLEngine.fetchResultWithSelection:888`（`SYS_OPERATION_FETCH_RESULTS` 批量结果拉取，`CrudBizModel.java:2265/2280` 经此）——同族，未修；⑤ `makeRpcProxy` 路径（无 selection 构建，仅代理）——不经过 `initForReturnType`，不受影响；⑥ HTTP GraphQL 路径（`newGraphQLContext` → `initGraphQLContext`）——选择树来自 parse 缓存的 AST（已缓存），仅 `buildSelectionBean` 一次遍历，且变量替换依赖每请求 vars，**不属于本缺陷同族**（误报排除）。
- **信心水平**: 确定（代码路径通读 + 分配量算术）
- **误报排除**: 不是"每请求必要工作"的误判——结果类型字段集是启动期确定的模型事实，与单次请求的请求体无关（无 selection 时），具备缓存条件；对比同仓库 `documentCache` 对查询文本的缓存先例。
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P2**。（a）三遍构建亲核：`initForReturnType`（GraphQLEngine.java:486-516）每请求 `new GraphQLDocument` + `RpcSelectionSetBuilder.buildForType` + `resolveSelections`（第二次全树遍历）+ `buildSelectionBean`（第三次）成立；`doc.addOperation` 将请求体挂顶层 field（opRequest），与 `setSelectionSet` 挂载的子树分离——"合成树是 (返回类型,maxDepth) 确定性函数、模板只读共享可行"前提成立。（b）documentCache 对照面亲核：:321 按查询文本缓存 + `initGraphQLContext` :414-417 `doc.isResolved()` 使 resolveSelections 仅首请求执行 + :436 每请求 `buildSelectionBean`（依赖 per-request vars）——round2 扩展复核的缓存可行性与 key 方案论证通过。（c）F3 同族站点存在性抽检（`newRpcContext`/`initRpcContext` 调用方）通过。判级校准合规：纯 CPU/分配常驻开销、无串行化点，不命中 (a)/(b)——P2 为正确上限。现状：新发现（本 campaign），已知未修复（HEAD 5994c18211）。

---

### [nop-service-framework-hotpath-C9-002] Gateway 每请求对模型缓存做无节流变更检查 → 每请求至少一次模型文件 stat 系统调用

- **文件**: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/http/GatewayHttpFilter.java:127`
- **证据片段**:
  ```java
  GatewayHandler handler;
  try {
      handler = handlerCache.getObject(true, handlerLoader);   // checkChanged 硬编码 true，无节流
      if (handler == null) {
          return next.get();
      }
  ```
- **严重程度**: P2
- **并发场景**: 网关的全部入站请求（每请求 1 次，IO/worker 线程）。
- **触发时序**（量化链）: 每请求 → `ResourceCacheEntry.getObject(true, loader)` → `isChanged()` → `ResourceDependsManager.isAnyDependsChange(deps.getDepends())` → 递归 `DefaultResourceChangeChecker.checkChanged(path, lastModified)` → `resource.lastModified()`。gateway.model 的依赖集合含真实模型文件（loadComponentModel 期间经 `collectDependsTo` 记录），而平台的 `ResourceTimestampCache` **整文件处于注释停用状态**（`nop-kernel/nop-core/src/main/java/io/nop/core/resource/cache/ResourceTimestampCache.java` 全文注释）——即每次检查是真实文件系统 stat（~1–5µs/次，虚拟化容器中更高）。同文件另一条缓存读取路径 `ResourceLoadingCache.get` 对同一检查做了 `refreshMinInterval`（默认 100ms）节流，本路径直接调用 `ResourceCacheEntry` **绕过节流**。
- **现状**: 每个网关请求固定支付 ≥1 次（依赖树有 N 个文件则 N 次）stat 系统调用 + 依赖树递归走查 + 每次一个 `HashSet` 分配。
- **风险**: 网关是全平台吞吐入口；QPS=10k 时即 10k+ stat/s 的纯开销，且把模型文件的 atime/mtime 元数据压力持续打在存储上；与 `biz-object-cache` 的节流行为不一致形成双标。
- **建议**: 对齐 `ResourceLoadingCache` 的做法——改用 `ResourceLoadingCache<GatewayHandler>`（自带 100ms refreshMinInterval 节流）或在 `getObject` 前先判 `entry.isRefreshEnabled(interval)`；热更新语义不变（100ms 内的模型修改延迟可见）。
- **失效面枚举（F3 同族清扫）**: ① `GatewayHttpFilter.java:127`——本案唯一硬编码 `getObject(true)` 站点（全审计域 grep `getObject(true` 仅此一处）；② `GraphQLEngine.java:321` `documentCache.get(query).getObject(CFG_GRAPHQL_PARSE_CACHE_CHECK_CHANGED.get())`（默认 true）——同形态，但 text-parse 无文件依赖、`deps.getDepends()==null` 快速返回，实际无 stat，**判定不立案**（误报排除见检查范围表）；③ `ResourceLoadingCache.get` 系全部读点（bizObjCache 等）——已有节流，判定 OK。
- **信心水平**: 确定
- **误报排除**: 不是"有界声明内的合理检查"——同一平台对同类检查给出了 100ms 节流的既定口径，本站点是绕过节流的孤立写法；stat 成本有实测口径（syscall 1–5µs），非臆测。
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P2；与 WI 2.1 C9-010 跨单元包含关系确认、盲审遗留开放点闭合**。（a）域内唯一硬站点亲核：全审计域 grep `\.getObject(true` 仅 GatewayHttpFilter.java:127；GraphQLEngine:288 为 `getObject(false)`、:321 为 config 门 + text-parse 条目 deps 内层 null 短路——枚举准确。（b）设施链亲核：`ResourceCacheEntry.getObject` :176-178 `value==null||checkChanged&&isChanged()`、`isRefreshEnabled` :103-106 public 无 API 障碍、`ResourceLoadingCache.get` :192 节流先行、`ResourceDependsManager.isAnyDependsChange` :214-215 入口 `new HashSet` + 递归 checkChanged→真实 stat、`ResourceTimestampCache` 全文注释死代码——量化链成立。（c）**开放点闭合**：第 2 轮"若 WI 2.1 清单不含 gateway 站点则补入"已解除——历史对照面（本阶段授权）核实 **WI 2.1 C9-010 直接使用方清单 E3 即 `GatewayHttpFilter:127`**（WI 2.1 复核轮已亲核含 GatewayConfigs 默认模型路径），gateway 站点已在设施层清单内，无需补入；C9-002 维持本单元热路径量化实例，修复归设施层条目统一下单，与 WI 2.1 C7-007（同链正确性面 P1）"同链三面绑定单点修复"口径一致。现状：新发现（本 campaign），已知未修复。

---

### [nop-service-framework-hotpath-C9-003] JwtAuthTokenProvider.ensureKeys() synchronized 使每个认证请求串行通过单例监视器（全平台串行化点，临界区虽小但可零成本消除）

- **文件**: `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/jwt/JwtAuthTokenProvider.java:87-106, 130-134`
- **证据片段**:
  ```java
  protected synchronized void ensureKeys() {
      if (!signKeys.isEmpty())     // 快检在锁内——每次都要先 acquire
          return;
      ... // 仅首调用实际填充
  }

  @Override
  public AuthToken parseAuthToken(String accessToken) {
      ensureKeys();                // 每个认证请求必经
      return JwtHelper.parseToken(accessToken, this::getKey, ...);
  }
  ```
- **严重程度**: P2
- **并发场景**: 所有携带凭证的认证请求（`AuthHttpServerFilter.parseAuthToken` → `loginService.parseAuthToken` → 本方法），在 blocking 池多线程并发下争用同一单例 bean 监视器。
- **触发时序**（量化链）: 每认证请求 1 次 `monitorenter/monitorexit`（临界区 = 1 次 `isEmpty` 判断，O(1)，~20–40ns）。竞争频率 = 全平台认证 QPS；C 线程并发时该点构成顺序化瓶颈点：无争用 ~30ns/请求，争用时出现锁队列排队与缓存行弹跳（CAS 失败重试），在 10k+ QPS 下可观测为尾延迟抖动。判级校准说明：属于条款 (a) 的"每请求必经全平台串行化点"，但临界区常量级、无锁内 I/O，按"默认 P2/P3"不升 P1（无可量化的吞吐悬崖，也未掩盖正确性问题）。
- **现状**: 密钥表初始化后终身只读，却让 100% 认证请求支付一次全局监视器获取；快检本可放在锁外。
- **风险**: 纯开销 + 一个无谓的全局顺序化点；若未来有人在 `ensureKeys` 中加入惰性密钥轮换等重逻辑，将直接放大为全平台热点。
- **建议**: 初始化完成语义改为不可变发布——`volatile ImmutableMap<String,Key> signKeys` + 锁外快检（`if (keys != null) return keys;`）的 double-checked locking（volatile 写保证安全发布），或 `@PostConstruct` 中急切初始化（encKey 注入后即可计算），运行路径零锁。
- **失效面枚举（F3 同族清扫）**: ① `parseAuthToken:130`、② `parseRefreshToken:137`、③ `parseAccessCode:144`、④ `generateAccessToken:109`、⑤ `generateAccessCode:116`、⑥ `generateRefreshToken:123`、⑦ `getKey:103`——7 个入口全部以 `ensureKeys()` 开头，全部同族未修；其中 ① 为每请求路径，②–⑦ 为登录/刷新路径（非每请求）。平台内其他 `synchronized` 惰性初始化（`AiRateLimitGatewayInterceptor.buckets():114-132`）已采用锁外 volatile 快检写法，证明本仓库存在正确先例，本案偏离该惯例。
- **信心水平**: 确定
- **误报排除**: 不是"低频路径粗锁"误报——`parseAuthToken` 是每认证请求必经；也不是"synchronized 原语使用"误报——问题在复合模式（快检放锁内 + 无 volatile 快检路径），非原语本身。
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P2**。（a）证据亲核：synchronized `ensureKeys` 快检在锁内（JwtAuthTokenProvider.java:87-90）；`parseAuthToken`/`parseRefreshToken`/`generateAccessToken`/`generateAccessCode`/`generateRefreshToken`/`getKey` 各入口 `ensureKeys()` 开头逐一亲核，每认证请求必经链成立。（b）判级复核：命中条款 (a) 形态（单例监视器 × 全平台认证 QPS）但临界区 1 次 `isEmpty`（~20–40ns），无可量化吞吐悬崖、未掩盖正确性问题——"候选但不升 P1、维持 P2"的校准裁定正确。（c）先例对照亲核：`AiRateLimitGatewayInterceptor.buckets`（:114-132）锁外 volatile 快检 + DCL 成立，"偏离仓库既有惯例"论证成立。现状：新发现（本 campaign），已知未修复。

---

### [nop-service-framework-hotpath-C9-004] gRPC MAP/ANY 字段每记录双次 JSON 序列化（computeSize 与 write 各序列化一次，第一次结果被丢弃）

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-grpc/src/main/java/io/nop/graphql/grpc/proto/marshaller/JsonFieldMarshaller.java:30-46`
- **证据片段**:
  ```java
  public void writeField(CodedOutputStream out, int propId, Object value) throws IOException {
      out.writeString(propId, JsonTool.stringify(value));        // 第 2 次序列化
  }
  ...
  public int computeSize(int propId, Object value) {
      return CodedOutputStream.computeStringSize(propId, JsonTool.stringify(value)); // 第 1 次：只为量长度
  }
  ```
- **严重程度**: P2
- **并发场景**: gRPC 响应写出线程；每条含 MAP/ANY 字段的记录 2 次。
- **触发时序**（量化链）: `GenericObjSchema.toByteArray`（:94-99）先 `ProtobufMarshallerHelper.computeSize`（对每个 MAP/ANY 字段完整 `JsonTool.stringify` 得 String→量长度→String 丢弃），分配等长 byte[] 后 `writeObject` 再次 `JsonTool.stringify` 同一对象。每记录 × k 个 JSON 字段 = 2k 次序列化 + k 个中间 String 直接变垃圾。单次 stringify 对 1KB 值约 2–5µs + 1KB String 分配；批量结果集（分页 100 行 × 3 个 JSON 字段）= 600 次序列化/请求，其中 300 次纯浪费（50% 双倍开销）。
- **现状**: protobuf 两阶段（先量长度后写）对标量字段是零成本模式（如 `LocalDateTimeFieldMarshaller` 用常量 `SIZE_NO_TAG` 免算），但 JSON 嵌入字段把"量长度"实现成了完整序列化。
- **风险**: gRPC 出口（nop-graphql-grpc 服务端 marshaller）在 JSON 字段密集的负载下 CPU 与分配速率双倍放大。
- **建议**: 序列化一次并复用——`byte[]/String payload = JsonTool.stringify(value)` 后 size 与 write 共用；或给 `IFieldMarshaller` 增加 `writeAndSize` 一趟式接口。
- **失效面枚举（F3 同族清扫）**: ① `writeField:35`、② `writeFieldNoTag:40`、③ `computeSize:43`、④ `computeSizeNoTag:45`——4 个方法各自独立 stringify，任一 compute/write 配对均为双倍；读方向 `readField:31` 单次 parse 无此问题。同构 marshaller 中 `LocalDateTimeFieldMarshaller`/`LocalDateFieldMarshaller` 用常量尺寸避免了双算（但其正确性缺陷另见 CL-015），证明仓库内已有正确模式先例。
- **信心水平**: 确定
- **误报排除**: 不是"protobuf 标准两阶段模式的固有成本"——两阶段对标量是零成本，本缺陷仅因 JSON 字段的 compute 实现选择了完整序列化；有同仓-marshaller 的零成本先例为对照。
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P2（行号订正）**。双序列化亲核：`writeField`/`writeFieldNoTag` 与 `computeSize`/`computeSizeNoTag` 各自独立 `JsonTool.stringify`（实际 :38-55；初审 :30-46 及 F3 :35/:40/:43/:45 存在 -3~-8 行漂移，方法一一对应）——任一 compute/write 配对即双倍序列化；两阶段契约（`GenericObjSchema.toByteArray` :97-101 按 computeSize 分配后 write）成立。P2 判级合规（gRPC 出口每记录常数因子加倍、50% 纯浪费量化算术成立）。同族对照（LocalDateTimeFieldMarshaller 常量尺寸免双算）成立——其正确性缺陷归 C9-012。现状：新发现（本 campaign），已知未修复。

---

### [nop-service-framework-hotpath-C9-005] 流式路径每 SSE 元素在事件循环线程做 parse→拦截器→serialize 往返，且 BufferedSubscription 监视器跨下游回调持有（CL-010 深化立案）

- **文件**: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/executor/StreamingProcessor.java:148-167`；`nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/http/GatewayHttpFilter.java:200-260`；`nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/executor/BufferedStreamingPublisher.java:260-267, 382-389`
- **证据片段**:
  ```java
  // StreamingProcessor.onNext —— 上游 HTTP 客户端事件循环线程执行
  Object element = item.getData();
  if (element instanceof String) {
      element = JSON.parse(element.toString());        // 每元素 JSON → Map
  }
  element = invocation.proceedOnStreamElement(element, context);  // 拦截器链逐元素遍历
  if (element != null) subscriber.onNext(element);
  subscription.request(1);

  // GatewayHttpFilter.serializeStreamElement —— 下游再 Map → JSON
  String json = JsonTool.serialize(item, false);
  return "data: " + json + "\n\n";                     // 再拼 2 次字符串

  // BufferedStreamingPublisher.handleItem —— 在 synchronized(this) 内调用下游 onNext
  if (demand > 0) {
      demand--;
      state.forwarded = true;
      subscriber.onNext(item);                          // 序列化发生在监视器内
  }
  ```
- **严重程度**: P2
- **并发场景**: AI 代理流式路由；上游推送线程 = HTTP 客户端（reactor/event-loop）线程，该线程同时服务多条流的多路复用；下游 `request(n)`（客户端背压）可能来自响应写出线程，与 `onNext` 共争 `BufferedSubscription` 监视器。
- **触发时序**（量化链）: 每元素（典型 AI chunk 100B–4KB）：`JSON.parse`（~2–5µs + 3–6× chunk 大小的 Map 树分配）→ 拦截器链遍历（无拦截器时为空遍历）→ `JsonTool.serialize` + `"data: "+json+"\n\n"` 两次字符串拼接（~2–5µs + 同量级分配）。**纯透传场景（无任何 streamElement 拦截器）两个方向都无语义增益**，每元素 4–10µs CPU 全部落在事件循环线程上——事件循环被 CPU 工作占据即阻塞同 loop 上所有其他流与连接的 I/O。叠加监视器问题：`AttemptSubscriber.onNext` 在 `synchronized (BufferedSubscription.this)` 内调 `handleItem` → `subscriber.onNext`（含上述全部序列化与向服务端容器投递），把 O(元素大小) 的工作拉进锁内；同流的 `request(n)`/`cancel` 线程在此期间被阻塞，极端时序下（下游写 socket 慢）锁内含 I/O 形态成立。
- **现状**: CL-010 登记的"流式每元素 JSON 序列化在事件循环线程"在本轮深挖确认为完整三段链（parse→transform→serialize + 拼接），并新增锁内回调面。
- **风险**: 高并发流式负载下事件循环 CPU 饱和 → 所有复用该 loop 的连接尾延迟劣化；锁内下游回调使单流内背压线程与推送线程互相拖延。
- **建议**: ① 透传快速路径：无 `onStreamElement` 拦截器且 contentType 语义为纯转发时直接转发原始 String（跳过 parse+serialize 往返，等价于字节管道）；② 每元素链维持原语可用；③ `BufferedSubscription` 的 `subscriber.onNext` 移出监视器——在锁内仅做缓冲摘除（snapshot 后锁外投递，Flow 规范允许单订阅者串行投递）。
- **失效面枚举（F3 同族清扫）**: ① 非缓冲路径 `StreamingProcessor.createMappedPublisher.onNext:148`（parse+拦截器链）——同族未修；② 缓冲路径经 `BufferedStreamingPublisher.AttemptSubscriber.onNext:382` 包裹后到达同一链——同族未修且叠加锁内回调；③ `serializeStreamElement` 的三个 contentType 分支（event-stream/ndjson/默认，GatewayHttpFilter.java:246-259）——三分支同构双倍拼接，同族未修；④ `OpenAiDeltaAccumulator`（每 chunk `computeIfAbsent`）——每流有界 map 操作，判定 OK 不立案。
- **信心水平**: 确定（线程归属：上游 onNext 由 HTTP 客户端 publisher 驱动，非计算池）
- **误报排除**: 不是"事件循环上做少量 CPU 工作属正常"——这是每元素双方向的全量 JSON 编解码，且是 AI 网关的主路径（每个流式响应的每个 chunk）；锁内回调不是"监视器持有期间调用监听器"的误报泛化——被调方是含序列化与潜在 socket 写的数据面回调，非轻量通知。
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P2**。（a）三段链亲核：`StreamingProcessor.onNext`（:148-167，String→`JSON.parse`→`proceedOnStreamElement`→`subscriber.onNext`→`request(1)`）与 `serializeStreamElement` 三 contentType 分支（GatewayHttpFilter.java:246-259，`"data: "+json+"\n\n"`）逐行亲核成立。（b）锁内下游回调亲核：`AttemptSubscriber.onNext` :382-389 `synchronized (BufferedSubscription.this)` 内 `handleItem` → :259-267 `demand>0` 分支 `subscriber.onNext(item)` 在监视器内执行——锁内含序列化与容器投递、同流 `request(n)`/`cancel` 跨线程阻塞面成立。（c）线程归属（上游 HTTP 客户端事件循环）论证合理；透传快速路径建议语义等价成立。现状：新发现（本 campaign），已知未修复。

---

### [nop-service-framework-hotpath-C9-006] GraphQL HTTP 入口每请求以 INFO 级输出全量 query 文本（O(query 长度) 的日志格式化与 I/O 常驻开销）

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/web/GraphQLWebService.java:96-99`
- **证据片段**:
  ```java
  return engine.executeGraphQLAsync(context).thenApply(res -> {
      LOG.info("nop.graphql.end-graphql-request:usedTime={},query={},errorCode={},msg={}",
              CoreMetrics.currentTimeMillis() - beginTime, request.getQuery(), res.getErrorCode(),
              res.getMsg());
  ```
- **严重程度**: P2
- **并发场景**: 所有 HTTP GraphQL 请求（`runGraphQL` 成功路径，响应回调线程）。
- **触发时序**（量化链）: 每请求 1 条 INFO 日志；`query` 上限为 `nop.graphql.query.parse-max-length`（默认 4096 字符）。INFO 开启时（生产常见配置）每请求支付：SLF4J 参数装箱 + 消息格式化（含 4KB 级字符串拷贝）+ appender I/O。1,000 QPS × 平均 2KB query ≈ 2MB/s 的纯日志吞吐与相应格式化 CPU；若接入同步 File appender 则叠加每请求一次磁盘写路径。
- **现状**: 请求审计日志以 INFO 级携带全量 query 文本，无采样/截断开关；同级 `runRest`/`runProxy` 的完成日志（`end-rpc-request`/`end-rpc-proxy`）只含 operationName，不含请求体——同族中唯独 GraphQL 路径携带全文本。
- **风险**: 高 QPS 下日志成为 CPU 与磁盘 I/O 的显著常驻项；日志洪峰反过来拖慢响应回调线程。
- **建议**: 对齐 runRest 同族口径：query 改为截断摘要（如前 256 字符）或仅在 `LOG.isDebugEnabled()`/错误分支携带全文；或引入采样率配置。
- **失效面枚举（F3 同族清扫）**: ① `runGraphQL`（本案，含全量 query）；② `runRest:256-258`、③ `runProxy:206-209`——同族完成日志，不含请求体，判定无此问题；④ `JaxrsHelper`/`buildJaxrsGraphQLResponse:161-165` 的 `LOG.debug` —— debug 级且有守护，判定 OK；⑤ JsonRpc 路径（`JsonRpcService`）无等价 INFO 全量日志，判定 OK。
- **信心水平**: 确定（成本存在性）；实际部署是否 INFO 开启属部署假设（已标注）
- **误报排除**: 不是"请求审计日志属必要功能"的一刀切——同模块 RPC 路径的同族日志已采用"不含请求体"的轻量口径，本站点是族内偏离；建议为口径对齐而非取消审计。
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P2**。亲核：`runGraphQL` 完成日志 INFO 携带全量 `request.getQuery()`（GraphQLWebService.java:96-99）；同族对照成立——`runRest` :257 仅 operationName、`runProxy` :207 仅 serviceName/serviceMethod；全量文本上限由 `CFG_GRAPHQL_QUERY_PARSE_MAX_LENGTH`（默认 4096）调节；JaxrsHelper debug 级守护、JsonRpc 无等价日志的误报排除面成立。部署条件标注（INFO 开启与否属部署假设）已由初审自带，P2 以成本存在性为据合规。现状：新发现（本 campaign），已知未修复。

---

### [nop-service-framework-hotpath-C9-007] dispatchAll/上下文监视器竞争频率量化记录（C2-001 已立案正确性面，本案仅补竞争频率与临界区成本，量化结论：不构成性能热点）

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/engine/GraphQLExecutionContext.java:156-170`；协同站点 `BeanMethodBatchFetcher.java:64-81`、`OrmBatchLoader.java:43-52`、`GraphQLExecutionContext.getRequest():127-129`
- **证据片段**:
  ```java
  public synchronized CompletionStage<Void> dispatchAll() {
      if (loaders.isEmpty())
          return null;                        // 无 loader 请求的常态快速路径（一次 monitor enter/exit + isEmpty）
      Map<String, DataLoader> loaders = new HashMap<>(this.loaders);
      this.loaders.clear();
      ...
  }
  ```
- **严重程度**: P3（量化记录，非新缺陷）
- **并发场景**: 同一请求内的并行字段分支（BatchFetcher 注册/`loader.load`）与 executor 的三个调度点（发起点名/游走回合出口/批次完成递归）争用同一请求级监视器；跨请求无竞争（上下文 per-request）。
- **触发时序**（量化链）: 频率 = 每请求 O(R + B + 1)，R=异步游走回合数（嵌套异步字段层数，典型 1–5），B=批量 loader 字段命中数；每次临界区为 O(1)（isEmpty 快检 / map get / `loader.load` 入队），~100ns 级；最坏情形（深嵌套 + 多分支同时注册）单请求监视器串行总量 <10µs，相对字段 I/O（ms 级）占比 <1%。无 loader 的普通查询走 `isEmpty` 快速路径，仅支付 monitor enter/exit。
- **现状**: 正确性面（注册/dispatch 丢 key 竞态）已由 C2 轮立案；本案确认该监视器在性能面不构成热点，无需为性能引入无锁结构。
- **风险**: 无（量化记录）。备注一条边界：若未来在 `dispatchAll` 临界区内追加重逻辑（如同步 batch 执行），将把请求内所有字段分支串行化，届时应重新评级。
- **建议**: 维持现状；在该监视器的全部使用点（已列 4 处）加注释声明"临界区必须保持 O(1)"的隐性契约，防止后续修复把批执行拖入锁内。
- **失效面枚举（F3 同族清扫）**: 同一监视器的全部获取点：`dispatchAll:156`、`getRequest:127`、`BeanMethodBatchFetcher.get:64`、`OrmBatchLoader.makeDataLoader:44`——4 处已逐一核对临界区内容（见上），全部 O(1)。
- **信心水平**: 确定
- **误报排除**: 不是"看到 synchronized 就报"——经频率×临界区成本量化后判为低竞争，仅作量化记录归档。
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P3，量化事实修正（复核发现）**。（a）**"临界区 O(1)、单请求监视器串行总量 <10µs"的量化不成立，予以修正**：`dispatchAll` 为 synchronized **方法**（GraphQLExecutionContext.java:156-172），方法体整体在监视器内，含 `loader.dispatch()`——本域两族 loader 的批量取数均在调用线程**同步**执行：`OrmBatchLoader.load`（:29-36）内联执行 `ormTemplate.requireSession().getBatchLoadQueue().flush()`（DB 批量 I/O）+ `supplier.get()`；`BeanMethodBatchFetcher` 的 BatchLoader lambda 经 `FutureHelper.futureCall`（FutureHelper.java:122-129，`task.call()` 同步执行）调用 realFetcher。即批量 loader 路径下监视器跨同步批量取数 I/O（ms 级）持有，同请求内并发 `load()` 注册（`BeanMethodBatchFetcher.get` 的 synchronized(context)）在此期间被阻塞。（b）判级不变的理由：监视器作用域仍为 per-request（跨请求零竞争，不构成条款 (a) 串行化点）；批量执行本身即由单一 dispatch 线程内联顺序执行（无锁亦顺序），监视器的增量代价为"dispatch 期间并发注册推迟到下一轮"——设计内行为而非额外热点；正确性面（丢 key 竞态）归 C2-001。故维持 P3 量化记录。（c）F3 修正："4 处已逐一核对临界区内容全部 O(1)"订正为：`getRequest:127`/`BeanMethodBatchFetcher.get:64`/`OrmBatchLoader.makeDataLoader:44` 三处 O(1)，`dispatchAll:156` 临界区 = O(1) 快照 + 同步批量执行。建议中的"临界区必须保持 O(1)"契约注释应按修正后事实改述为"dispatchAll 现状在锁内同步执行批量取数，未来加重逻辑前应先把批执行移出锁外"。现状：新发现（本 campaign），已知未修复。

---

### [nop-service-framework-hotpath-C9-008] resultLock 竞争频率量化记录（C2-006 已立案面，本案量化确认：per-request 锁、锁内仅 O(1) put，非平台串行化点）

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/engine/GraphQLExecutor.java:56, 391-393, 414-416, 474-477, 481-483`
- **证据片段**:
  ```java
  private final Object resultLock = new Object();   // per GraphQLExecutor —— GraphQLEngine.newGraphQLExecutor() 每请求新建
  ...
  v = normalizeValue(v, sel);
  synchronized (resultLock) {
      result.put(resultAlias, v);                    // 临界区 = 单次 LinkedHashMap.put
  }
  ```
- **严重程度**: P3（量化记录，非新缺陷）
- **并发场景**: 同一请求内并行完成顶层数据获取的字段分支（query 并行语义）；跨请求无竞争（executor 每请求新建）。
- **触发时序**（量化链）: 频率 = O(L)，L=结果集叶子字段写入总数（顶层次数 + 每记录每字段 1 次，分页 100 行 × 10 字段 = ~1,000 次/请求）；每次临界区 = 1 次 `LinkedHashMap.put`（~50ns）；单请求监视器串行总量 ~50µs（1000 字段大结果集），且与字段数据获取（ms 级）完全重叠，无可观测吞吐影响。争用窗口仅在多分支同一时刻完成时出现，临界区短到争用即消散。
- **现状**: "O(请求数×字段数)" 的获取总量属实，但获取次数≠竞争成本：作用域是单请求、临界区是单次 put。量化后判定不构成热点；若未来把 normalizeValue 或列表物化移入锁内才会成为问题。
- **风险**: 无（量化记录）。隐性契约：`synchronized (resultLock)` 块内只允许 map put。
- **建议**: 维持现状；在 4 个同步块处加同一行注释固化契约（与 C9-007 同款做法）。
- **失效面枚举（F3 同族清扫）**: 全部 4 个 `synchronized (resultLock)` 站点（:391、:414、:474、:481）——逐一核对，锁内均仅 `put`，无 I/O、无遍历、无用户回调。
- **信心水平**: 确定
- **误报排除**: 不是为已立案缺陷翻案——正确性归属 C2-006（其立案面若涉及结果覆盖语义，以 C2 轮为准）；本案仅证明其**性能面**可忽略，防止后续修复以性能为由重写锁结构引入新风险。
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P3**。亲核：`resultLock` per-executor 字段（GraphQLExecutor.java:56，`newGraphQLExecutor()` 每请求新建）；4 个 `synchronized (resultLock)` 块（:391-393/:414-416/:474-477/:481-483）锁内均仅 map put，`result`/`ret` 为 LinkedHashMap（:169/:265/:432）——与字段数据获取（ms 级）完全重叠，非热点。量化记录归档与"防以性能为由重写锁结构"的定位恰当。现状：新发现（本 campaign），已知未修复。

---

### [nop-service-framework-hotpath-C9-009] GatewayContextImpl 对线程封闭状态使用 synchronized 访问器（每请求 6–10 次无谓监视器操作）

- **文件**: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/context/GatewayContextImpl.java:67-107`
- **证据片段**:
  ```java
  @Override
  public synchronized String getRequestPath() {
      return (String) getAttribute(ATTR_REQUEST_PATH);     // attribute map 存储再加监视器
  }
  @Override
  public synchronized boolean isStreamingMode() {
      Boolean mode = (Boolean) getAttribute(ATTR_STREAMING_MODE);
      return mode != null && mode;
  }
  ```
- **严重程度**: P3（优化候选）
- **并发场景**: 请求处理线程为主；流式回程（`isStreamingMode`/`getPathVariables` 由 StreamingResponse 回程读取）构成唯二真实跨线程点。
- **触发时序**（量化链）: 每请求约 6–10 次同步访问器（`getRequestPath` 在 `hasRouteFor`/`handle`/`loadInterceptors`/错误路径被反复读取），每次 = monitor enter + attribute map hash get + monitor exit ≈ 40–60ns；单请求总计 <1µs——**无量化显著的吞吐影响**，按"写不出量化收益的一律 P3"归优化候选。
- **现状**: 状态本存于 attribute map（多数实现已是并发容器），其上再叠加方法级 synchronized；线程封闭字段（requestPath 等）的同步纯属冗余，仅流式回程两个字段有跨线程语义。
- **风险**: 无正确性风险；微小常数因子 + 语义噪音（读者误以为全部字段需同步保护）。
- **建议**: 仅对 `streamingMode`/`currentRoute`（流式回程确实跨线程的两项）保留 volatile/synchronized 语义，其余访问器去 synchronized。
- **失效面枚举（F3 同族清扫）**: 8 个 synchronized 访问器（getCurrentRoute/setCurrentRoute/getPathVariables/setPathVariables/isStreamingMode/setStreamingMode/getRequestPath/setRequestPath）全部同构；`getQueryParams`/`getHttpMethod` 已无 synchronized——同族内已存在无锁先例。
- **信心水平**: 确定（冗余性）；量化收益微小（故 P3）
- **误报排除**: 不是"方法级 synchronized 一律报 P2 热点"——量化后 <1µs/请求，主动降 P3。
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P3**。亲核：8 个 synchronized 访问器（GatewayContextImpl.java:67-107，getCurrentRoute/setCurrentRoute/getPathVariables/setPathVariables/isStreamingMode/setStreamingMode/getRequestPath/setRequestPath）与 `getQueryParams` 无 synchronized 的同族先例成立；流式回程跨线程两项的甄别合理；量化 <1µs/请求、P3 归优化候选合规。现状：新发现（本 campaign），已知未修复。

---

### [nop-service-framework-hotpath-C9-010] Gateway 每请求双重 trie 路由匹配 + 拦截器列表每请求重建（P3 优化候选，两项均量化）

- **文件**: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/http/GatewayHttpFilter.java:140`（`hasRouteFor`）与 `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/impl/GatewayHandler.java:100-113, 238-249`（`handle` 再次 `matchPath`；`loadInterceptors` 每请求 stream 重建）
- **证据片段**:
  ```java
  // GatewayHttpFilter.filterAsync —— 第 1 次匹配
  if (!handler.hasRouteFor(context.getRequestPath())) {
      return next.get();
  }
  ...
  // GatewayHandler.handle —— 第 2 次同一匹配
  MatchResult<List<RouteValue<GatewayRouteModel>>> result = model.getRouter().matchPath(svcCtx.getRequestPath());
  ...
  // loadInterceptors —— 每请求 stream filter + collect 重建 List
  return model.getInterceptors().stream()
          .filter(interceptorModel -> matches.contains(interceptorModel) && matchInterceptor(interceptorModel, svcCtx))
          .map(interceptorModel -> interceptorModel.getOrCreateInterceptor(svcCtx))
          .collect(Collectors.toList());
  ```
- **严重程度**: P3（优化候选）
- **并发场景**: 网关每请求主线程。
- **触发时序**（量化链）: 每请求 2 次 trie `matchPath`（P1-check-gw-49 的路径预匹配 + handle 内正式匹配；trie 遍历 ~百 ns 级/次）+ 1 次 `matchAllPathValues` + stream 管道分配（~3 个对象）+ O(拦截器数) 遍历 + 每 route 的 `when` 表达式求值（`matchRoute`/`matchInterceptor`，XPL call2，有 when 配置时为 µs 级解释执行）。合计单请求 ~1–2µs 常驻（无 when 条件时）——量化收益不足以升 P2。
- **现状**: 类注释宣称"拦截器缓存 - 提升拦截器获取性能"，实际拦截器 List 每请求经 stream 重建（`getOrCreateInterceptor` 缓存的是实例而非匹配结果）；路由预匹配（body 零消费优化）与 handle 匹配重复。
- **风险**: 无正确性风险；QPS 极高时为可测但次要的常数项。
- **建议**: `filterAsync` 预匹配后把 `MatchResult` 传入请求属性供 `handle` 复用；或按 path 前缀缓存拦截器 List（模型不可变，路由表构建期可预计算）。
- **失效面枚举（F3 同族清扫）**: ① `hasRouteFor`（本案面 1）；② `GatewayHandler.handle:102`（面 2）；③ `loadInterceptors:242`（面 3）；④ `matchRoute:213` 与 `matchInterceptor:259` 的 when 求值（面 4）——4 处同族均为每请求执行，逐一登记。
- **信心水平**: 确定（重复存在）；收益量化属估算（故 P3）
- **误报排除**: 预匹配是 P1-check-gw-49 的正确性/语义修复（避免无路由请求消费 body），本案不否定其必要性，仅指出可与 handle 匹配复用结果。
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P3**。亲核：`hasRouteFor` 预匹配（GatewayHttpFilter.java:140，P1-check-gw-49 语义修复）+ `handle` 内 `matchPath` 再匹配（GatewayHandler.java:100-113）+ `loadInterceptors` stream 每请求重建（:238-249）+ `matchRoute`/`matchInterceptor` 的 when `call2` 求值（:213-216/:259-262）成立；`getOrCreateInterceptor` 缓存实例而非匹配结果、与类注释宣称的"拦截器缓存"差距表述准确。P3（量化收益不足以升 P2）合规。现状：新发现（本 campaign），已知未修复。

---

### [nop-service-framework-hotpath-C9-011] Auth 过滤链每认证请求强制 executeBlocking 线程跳跃（命中缓存的快路径也付 2 次线程切换）

- **文件**: `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/filter/AuthHttpServerFilter.java:154-155`
- **证据片段**:
  ```java
  IContext ctx = routeContext.getContext();
  Guard.notNull(ctx, "context not inited");

  // 耗时的操作不能在IO线程上执行
  return routeContext.executeBlocking(ctx.wrapWithContext(() -> {
  ```
- **严重程度**: P3（优化候选）
- **并发场景**: 所有非 public 路径的认证请求（IO 线程 → blocking 池 → 回 IO 线程完成 next 链）。
- **触发时序**（量化链）: 每认证请求 1 次任务提交 + 2 次线程切换 + `wrapWithContext` 上下文包装对象分配 ≈ 2–10µs（取决于 blocking 池队列深度）。会话/缓存全命中时临界内容实际只有：JWT HMAC 验签（~1–2µs）+ 2 次 map get—— fast path 本可与 IO 线程兼容，仅 token 刷新/OAuth 等慢分支真正需要 blocking。
- **现状**: 无快慢路径分流，注释声明的"耗时操作"以最重情形统一处理。
- **风险**: 无正确性风险；高 QPS 下 blocking 池成为额外排队点（池参数配置不在本审计域）。
- **建议**: 保持现默认；可选优化为 parse+cache-hit 快路径留在 IO 线程、`isNeedRefresh`/OAuth 分支才 `executeBlocking`（需平台 `IHttpServerContext.executeBlocking` 语义配合）。
- **失效面枚举（F3 同族清扫）**: ① `_filterAsync:155`（唯一 executeBlocking 站点，全文件 grep 确认）；② `handleUserContext:357` 的 `ctx.runOnContext`——另一类线程跳跃（任务队列），非本案同族，判定为上下文传播所需（误报排除）。
- **信心水平**: 确定（成本存在）；收益量化属估算（故 P3）
- **误报排除**: 不是"线程切换无关紧要"或"线程切换必须消灭"——设计注释已声明防 IO 线程阻塞的意图，本案仅登记快路径分流这一优化候选，不否定现设计的安全性动机。
- **复核状态**: 未复核
- **复核状态**: 已复核——**保留 P3**。亲核：`_filterAsync` 唯一 executeBlocking 站点（AuthHttpServerFilter.java:154-155，全文件 grep 确认）+ `handleUserContext` 的 `ctx.runOnContext`（:357，上下文传播所需误报排除）成立；快路径分流建议的语义前提（parse+cache-hit 可留 IO 线程）标注合理、域外池参数待平台层审计的盲区声明保留。P3 可选优化定位合规。现状：新发现（本 campaign），已知未修复。

---

## 维度集外线索

### [nop-service-framework-hotpath-CL-013]（维度集外线索，不立案展开）Gateway 请求体全量物化——维持登记并补充量化

- **文件**: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/http/GatewayHttpFilter.java:143-151, 329-351`
- **本轮量化**（不改变登记形态）: `context.getRequestBody().getTextAsync()` 将请求体全量物化为 String（LATIN1 时 1 字节/字符），随后 `JSON.parse(requestText)` 生成全量 DOM（Map 树），放大倍数按字段名/对象头开销经验值 3–8×。峰值瞬态分配 ≈ body 字节数 ×(1 + 3~8)；并发叠加：100 并发 × 1MB body ≈ 400–900MB 堆峰值。过滤层无体积上限（`parseRequestData` 仅判 content-type），上限依赖底层服务器配置（本审计域外）。路由预匹配（P1-check-gw-49）已把"无路由可匹配"的请求排除在物化之外，残留面为"有路由匹配的全部请求"。
- **关联**: 上述量化供 CL-013 主条目合并；本维度不再单独立案。
- **复核状态**: 复核确认——跨维度引用口径正确：本单元 CL-013 主条目归 C8 维度段（CL-012~014 为 C8 编号段，见 plan 2470 执行记录），本节为 C9 侧量化补充（"供主条目合并、不单独立案"），非重复立案；请求体物化站点亲核成立（GatewayHttpFilter.filterAsync `resumeRequest()` 后 `getRequestBody().getTextAsync()`，路由预匹配排除面表述与 P1-check-gw-49 一致）。

### [nop-service-framework-hotpath-CL-015]（维度集外线索，不立案展开）LocalDateTimeFieldMarshaller.computeSize 固定 19 字节假设与可变长 toString 不一致——线格式正确性面（非并发/热路径性能问题，转交裁定）

- **文件**: `nop-service-framework/nop-graphql/nop-graphql-grpc/src/main/java/io/nop/graphql/grpc/proto/marshaller/LocalDateTimeFieldMarshaller.java:19, 25-30, 34-39`
- **证据片段**:
  ```java
  static final int SIZE_NO_TAG = CodedOutputStream.computeStringSizeNoTag("2002-01-02 11:02:03"); // 恒 19
  ...
  public void writeField(CodedOutputStream out, int propId, Object value) throws IOException {
      out.writeString(propId, value.toString());   // LocalDateTime.toString()：ISO-8601 可变长
  }
  public int computeSize(int propId, Object value) {
      return CodedOutputStream.computeTagSize(propId) + SIZE_NO_TAG;  // 固定 19 + tag
  }
  ```
- **说明**: `LocalDateTime.toString()` 秒与纳秒为零时省略秒段（如 `2026-01-01T10:00` = 16 字符），含纳秒时可达 29 字符。`GenericObjSchema.toByteArray:94-99` 按 `computeSize` 总量分配 `byte[]` 并原样返回整个数组：零秒值 → 实写 16 字节、声明 19 → **帧尾 3 个脏字节**（每记录每个 DATETIME 字段）；纳秒值 → 实写超长 → CodedOutputStream 越界（`OutOfSpaceException`）→ **含纳秒时间戳的记录 gRPC 序列化必然失败**。该缺陷属线格式正确性（C5/正确性形态之外的独立面），C9 维度仅在 per-record 抽验中顺带发现，按 CL 线索登记，建议裁定归属（疑似 P1 级正确性问题：任何纳秒精度 DATETIME 字段经 gRPC generic marshaller 写出即失败）。
- **失效面枚举**: `computeSize:34`、`computeSizeNoTag:38` 两处同源；`writeField:25`/`writeFieldNoTag:29` 的 `value.toString()` 为长度不一致源头。`LocalDateFieldMarshaller`（ISO 日期恒 10 字符）核对为安全，不属同族。
- **复核状态**: 复核确认——已升格为 **C9-012 终裁立案（P1 正确性，移交功能正确性维度）**，终裁见 `C9-round2.md` ① 节与本文件「维度复核结论」§一.1（probe 双失效模式经复核者本地复跑逐项复现）；本节作为首轮发现记录保留，其"帧尾脏字节/越界失效"结论与终裁一致。台账现状：升格事实已登记于 plan 2470 执行记录与 dispatch-log.md；cra-index.md 的 WI 2.3 CL 台账节待单元收口时补登。

---

## 基线对照与抽验记录（基线 C9 节 169 个 per-record 循环站点）

- 抽验通过（不立案）：`BeanPropertyFetcher`（BeanTool 反射元数据平台缓存）、`DictLabelFetcherProvider.loadLabel`（字典按 context cache + map lookup）、`DataFetchingEnvironment.prepare()`（同步路径复用 env，仅异步才 copy——分配设计正确）、`GraphQLExecutor.fetchList`（每记录 `env.prepare()` + list 槽位，必要分配）、`OrmBatchLoader.load`（每 key supplier.get + 一次 batch flush，同步聚合无每记录 I/O）、`ProtobufMarshallerHelper.parseObject/writeObject`（单遍走查 + schema map lookup，标准模式）、`OpenAiDeltaAccumulator`（每流有界）。
- 抽验发现：`JsonFieldMarshaller`（→C9-004）、`LocalDateTimeFieldMarshaller`（→CL-015）。
- 正则编译：审计域热路径 0 站点（仅 BCrypt 登录路径字段级预编译 Pattern）。
- 字符串拼接热点：`serializeStreamElement` 的 `"data: "+json+"\n\n"`（→C9-005 族内）；`buildLoaderKey` 的 `deepHashCode+deepToString`（每次批量字段调用，O(args)，量级小，不立案）。

## 总计

| 类别 | 数量 | 明细 |
|---|---|---|
| C9 发现（新立案） | 6 P2 + 5 P3 | C9-001…006（P2），C9-007…011（P3，其中 007/008 为已立案项的量化记录） |
| 升 P1 | 0 | C9-003 命中条款 (a) 形态但临界区常量级、无可量化吞吐损失，按判级校准维持 P2 |
| CL 线索 | 2 | CL-013（维持+量化）、CL-015（新，正确性面转交） |
| 误报排除记录 | 9 | asProxy 启动期、matchSimplePattern、auth 缓存命中路径、graphql parse 缓存命中路径、配置 get()、TokenBucket、decorator 构建期组合、ResourceLoadingCache 100ms 节流、runRest/runProxy 同族日志口径 |

## 本次审核盲区自评

1. **`IHttpServerContext.executeBlocking`/`sendStreamingResponse` 的实现池配置**在 nop-http-api（8 源码根之外），C9-011 的池排队风险只能标注待平台层审计确认。
2. **XPL 表达式（`match.when`/URL 表达式）的解释执行成本**未逐操作码量化，仅给出 µs 级区间；如需精确口径建议 JMH 基准（对照 nop-benchmark 惯例：`GatewayMatchBenchmark` 形态）。
3. **nop-auth-service 侧的 `IUserContextHook.onAccess` 实现**（每请求触发 dirty → flush 的源头）在审计域外，auth 命中路径的写回频率上限依赖其实现，本轮仅按接口语义量化。
4. GraphQL subscription/WS 长连接路径（`GraphQLSubscriptionManager`、`JsonRpcWebSocketHandler`）属常驻连接面而非每请求面，本轮仅覆盖注册/派发点，逐消息成本未全量展开。

---

## 维度复核结论

（独立复核子代理，2026-10-18；对第 1 轮全部 11 条发现 + 2 条维度集外线索、第 2 轮 2 条新发现逐条裁定，合计 13 条发现 + 2 条 CL。复核方式：亲自打开全部 file:line 核对 live code（行号漂移以实际代码订正）；本地复跑 `_tmp/c9r2-cl015/Cl015Probe.java`（protobuf-java 3.25.8）；按本阶段授权读取历史审计对照面（`ai-dev/audits/cra-index.md`、WI 2.1 nop-kernel-concurrency 审计目录、`ai-dev/plans/2468`/`2470`）。裁定可改严重度，不得改事实。）

### 一、特别复核指令裁定

**1. C9-012（CL-015 终裁，P1 移交功能正确性维度）——终裁：P1 成立，移交裁定成立**

- (a) **computeSize/toString 不一致亲核成立**：`SIZE_NO_TAG = CodedOutputStream.computeStringSizeNoTag("2002-01-02 11:02:03")`（LocalDateTimeFieldMarshaller.java:21）为与 value 无关的定值；`writeField`/`writeFieldNoTag` 用 `value.toString()`（:39-46）可变长。调用契约亲核：`GenericObjSchema.toByteArray`（:94-114）按 computeSize 总量分配 `byte[]` 后**原样返回整个数组**——声明长度与实写长度必须严格一致，无容差。
- (b) **实测复现独立复核通过（复核者本地复跑 probe，protobuf-java 3.25.8）**：长度谱系 (sec=0,ns=0)→16、(sec>0,ns=0)→19、millis→23、micros→26、nanos→29、midnight→16；**模式 A**（亚秒>0）→ `CodedOutputStream$OutOfSpaceException`（IOException 子类）；**模式 B**（秒=0 且纳秒=0）→ 尾部 3 个脏 0x00、首个字段解析成功后第二次 `readTag()` 抛 `InvalidProtocolBufferException: Protocol message contained an invalid tag (zero)`——与报告记载逐项一致。平台解析端 `ProtobufMarshallerHelper.parseObject` 走同一 `readTag()`，行为一致。
- (c) **豁免面亲核成立**：`CFG_GRAPHQL_IGNORE_MILLIS_IN_TIMESTAMP` 默认 true（GraphQLConfigs.java:76-77）→ Timestamp 字段走 `formatTimestampNoMillis`（DateHelper.java:262-268，`toString().substring(0,19)` 恒 19 字符）→ 长度安全；**LocalDateTime 字段无豁免**：`StdDataType.DATETIME` javaClass=LocalDateTime（StdDataType.java:93）、转换器 `ConvertHelper::toLocalDateTime`（SysConverterRegistry.java:76）、ORM 亲核 `_NopAuthUser.expireAt/pwdUpdateTime` 为 `stdDataType="datetime"`（nop-auth/model/nop-auth.orm.xml:83-86）而 createTime/updateTime 族走 timestamp 域（豁免）——触发面宽度论证准确。'T' 分支回环解析（ConvertHelper.java:992-993）亲核成立。亚秒精度来源（PostgreSQL timestamp(6)/Oracle/H2 默认精度、`LocalDateTime.now()` JDK9+ 微秒时钟）真实存在。
- (d) **移交裁定成立**：本缺陷为线格式正确性（marshaller 序列化契约违反），不属 C9 维度定义（并发/每请求常驻开销）的缺陷类；以 C9-012 归档终裁证据、移交功能正确性维度执行修复，符合 campaign 跨轨惯例（发现登记一处、修复归属唯一）。移交落地待办：单元收口登记 cra-index 时应显式落名接收轨。
- (e) **P1 判级成立**：两种失效模式均为硬失效且触发面真实（午夜/整点零秒值与亚秒精度值皆为常见数据形态）；模式 B 发送端静默（排障成本高）。未升 P0 正确：失败封闭（fail-closed）、无数据损坏、存在规避面（Timestamp 默认豁免 + DATETIME(0) 且秒非零）。
- (f) **精度订正（不改结论）**：`SIZE_NO_TAG` 实为 `1 字节 varint 长度前缀 + 19 字节载荷 = 20`（报告叙述"恒 19 / 固定 19+tag"差 1 字节）；对结论无影响——模式 B 脏字节数 3（声明 21=tag1+20，实写 18=tag1+len1+16）与实测一致，模式 A 越界结论不变。同族 F3 ⑤ `LocalDateFieldMarshaller`（恒 10 字符）"实际安全"判定同意。

**2. C9-002 与 WI 2.1 C9-010 关系——裁定确认，盲审遗留开放点闭合**

第 2 轮"同设施（ResourceCacheEntry/ResourceLoadingCache）同缺陷面跨单元包含关系、非重复立案、修复并入设施层条目"的裁定**确认**。开放点闭合：第 2 轮注明"若 WI 2.1 的 4 处清单不含 gateway 站点则补入（交协调者对齐）"——经历史对照面核实，**WI 2.1 C9-010 的直接使用方清单 E3 即 `GatewayHttpFilter:127`**（WI 2.1 复核轮已亲核该站点含 GatewayConfigs 默认模型路径与频率口径），gateway 站点已在设施层清单内，**无需补入**。台账口径核对：WI 2.1 C9-010 为 P2 保留条目（plan 2468 执行记录："C9-010 P2 维持且 F3 补至 6 使用方"），主面为"ResourceCacheEntry 直连使用方绕 refreshMinInterval 门"设施缺陷，本单元 C9-002 是其在请求热路径的量化实例；同链第三面为 WI 2.1 C7-007（P1，check-changed 正确性面）——与"同链三面绑定单点修复"既有口径一致，两单互引不重复计数。

**3. C9-003 维持 P2 复核——成立**

条款 (a) 形态命中（全平台单例监视器 × 每认证请求必经）但临界区常量级（1 次 `isEmpty`，~20–40ns），无可量化吞吐悬崖、未掩盖正确性问题，按维度校准"默认 P2/P3、不升 P1"正确；修复建议（volatile 快检 + DCL 或急切初始化）与仓库既有先例（`AiRateLimitGatewayInterceptor.buckets` :114-132）对齐。维持 P2。

**4. CL-015 台账登记核对**

升格事实已在单元内双处登记：plan 2470 执行记录 2026-10-18 行（"C9 r2（+2 含 C9-012 P1（CL-015 终裁升格，移交功能线），终止，13）"）与 dispatch-log.md（第 33/50/54 行，C9 合计 13、移交裁定入账）。campaign 级 `cra-index.md` **尚无 WI 2.3 节**（单元未收口——无 summary.md，发现表/CL 台账/P2 裁决台账均未含本单元行）；按 done 门控（目录完整 + summary 归档 + 索引更新 + closure audit PASS）在收口时补登，届时应同时登记 C9-012 的移交接收轨与 CL-013 主条目归属（C8 段）。CL 编号按单元隔离，本单元 CL-015 与 WI 1.7 CL-015（已驳回）/WI 2.1 CL-015（HighWatermarkSemaphore）无编号冲突。

### 二、逐条裁定汇总

| ID | 初审判级 | 复核裁定 | 关键依据 |
|---|---|---|---|
| C9-001 | P2 | 保留 | 三遍构建亲核；模板共享可行性前提（opRequest 与子树分离）亲核；documentCache 先例对照成立 |
| C9-002 | P2 | 保留 | 域内唯一硬站点；设施链（节流门/stat 死代码缓存/HashSet 分配）亲核；WI 2.1 C9-010 E3 即本站点（开放点闭合） |
| C9-003 | P2 | 保留 | 7 入口亲核；(a) 形态但临界区常量级；AiRateLimit DCL 先例对照成立 |
| C9-004 | P2 | 保留（行号订正） | 双 stringify 亲核；初审行号 -3~-8 漂移，方法一一对应 |
| C9-005 | P2 | 保留 | 三段链 + 锁内 `subscriber.onNext`（:382-389→:259-267）亲核 |
| C9-006 | P2 | 保留 | INFO 全量 query 亲核；runRest/runProxy 同族对照成立 |
| C9-007 | P3 | **保留（量化事实修正）** | dispatchAll synchronized 方法含同步批量取数 I/O（OrmBatchLoader flush / futureCall 同步调 realFetcher）——"O(1)/<10µs"量化作废；per-request 作用域不变，不升判级 |
| C9-008 | P3 | 保留 | 4 站点锁内仅 put 亲核；result/ret 为 LinkedHashMap |
| C9-009 | P3 | 保留 | 8 访问器 + getQueryParams 无锁先例亲核 |
| C9-010 | P3 | 保留 | 双 trie 匹配 + loadInterceptors stream 重建 + when call2 求值亲核 |
| C9-011 | P3 | 保留 | 唯一 executeBlocking 站点 + runOnContext 误报排除亲核 |
| C9-012 | P1 移交 | **终裁确认（P1 + 移交）** | probe 复跑双模式复现；豁免面 javaType 分布亲核；SIZE_NO_TAG=20 精度订正（不改结论） |
| C9-013 | P3 | 保留 | :137/:146 双扫亲核；isPublicPath/isServicePath 线性扫描与分支分析正确 |
| CL-013 | 引用登记 | 确认（跨维度引用口径正确） | 主条目归本单元 C8 段；不重复立案 |
| CL-015 | →C9-012 | 确认；台账待单元收口补登 | plan 2470/dispatch-log 已登记；cra-index 无 WI 2.3 节（现状合规） |

### 三、统计、复核新发现与争议点

- **裁定统计**：13/13 保留（驳回 0、降级 0、升级 0）；CL 线索 2 条确认（CL-013 引用口径、CL-015 升格）。第 2 轮收口判定（写终止、"无新 P0/P1"说明、终表排序）复核后**维持**。
- **复核新发现（事实面修正，非新立案）**：C9-007 的 `dispatchAll` 为 synchronized 方法，临界区在批量 loader 路径跨同步批量取数 I/O（`OrmBatchLoader.load` 内联 DB flush；`BeanMethodBatchFetcher` 经 `FutureHelper.futureCall` 同步调 realFetcher）——初审"临界区 O(1)、<10µs"量化作废；因作用域仍为 per-request 且批执行本就由单线程内联顺序进行，判级维持 P3，已在 C9-007 复核状态行与终表修正。
- **开放点闭合**：C9-002 与 WI 2.1 C9-010 的"gateway 站点是否在设施层清单"——已含（E3），无需补入，协调者对齐动作解除。
- **精度订正**：C9-012 的 `SIZE_NO_TAG` 叙述"恒 19"实为 20（含 1 字节 varint 长度前缀），脏字节数 3 与越界结论均不受影响；C9-004 行号漂移 -3~-8 行。
- **移交待办**：C9-012 收口登记时显式落名功能正确性接收轨。

---

## 最终保留项

| 优先级 | ID | 一句话 | 处置 |
|---|---|---|---|
| P1（正确性，移交） | C9-012 | gRPC LocalDateTime marshaller 声明定长 vs 实写 16–29 可变长：亚秒记录序列化必败、整点零秒记录接收端解析必败（实测双模式复现） | 移交功能正确性维度修复；C9 归档终裁证据 |
| P2 | C9-001 | RPC/REST 每请求三遍重建合成选择树（O(F×D) 分配 ×3 遍） | 按 key=返回类型名缓存（可行性已论证）；同族 4 面 |
| P2 | C9-002 | Gateway handlerCache 无节流变更检查（每请求 O(N) stat + HashSet） | 并入 WI 2.1 C9-010 设施层修复（E3 已在清单） |
| P2 | C9-003 | JwtAuthTokenProvider.ensureKeys 全平台认证串行点（临界区常量级） | volatile 快检 + DCL 或急切初始化 |
| P2 | C9-004 | gRPC MAP/ANY 每记录双次 JSON 序列化（50% 纯浪费） | 序列化一次共用（byte[]/String payload） |
| P2 | C9-005 | 流式每元素 parse→拦截器→serialize 往返 + 锁内下游回调 | 透传快速路径 + onNext 移出监视器 |
| P2 | C9-006 | GraphQL 入口 INFO 全量 query 日志（≤4KB 文本/请求） | 对齐 runRest 口径（截断/降级/采样） |
| P3 | C9-013 | auth 双次 isPublicPath 线性扫描（非 public+service 主形态恒 2 次） | :146 复用 publicPath 局部变量（1 行） |
| P3 | C9-009 | GatewayContextImpl 8 个冗余 synchronized 访问器 | 仅流式回程 2 字段保留同步 |
| P3 | C9-010 | Gateway 双重 trie 匹配 + 拦截器 List 每请求 stream 重建 | MatchResult 复用 / 路由表预计算 |
| P3 | C9-011 | auth 每认证请求强制 executeBlocking 双跳 | 快慢路径分流（可选） |
| 量化记录 | C9-007 | dispatchAll/上下文监视器（复核修正：临界区在批量路径含同步批取数 I/O，per-request 作用域、非跨请求串行化点） | 维持现状 + 契约注释（按修正后事实改述） |
| 量化记录 | C9-008 | resultLock（per-request、锁内仅 LinkedHashMap.put） | 维持现状 + 契约注释 |

（复核后合计：P1×1（移交）+ P2×6 + P3×4 + 量化记录×2 = 13 条全部保留，与第 2 轮终表一致；判级零变更。）
