# 宿主启动就绪门禁/future 异常终结/上传流配对关闭/凭据对并发契约（R2-21 4.4 host 批 4 成员+测试缝裁定）

> Status: draft（plan 2524 Phase 1 产出物，独立 draft review 通过后转 active——转 active 后为该批实施唯一设计裁决权威）
> Source: 批次成员表、PA 判定、勘误登记与红绿判据以执行计划 `ai-dev/plans/2524-cra-phase8.30-r2-21-host.md` 为准（本文档为最终状态文档，勘误不落此处）；成员发现详情 = WI 4.4 审计目录 `ai-dev/audits/2026-10/2026-10-21-1223-deep-audit-nop-host-concurrency/`（C1-shared-mutable-state[C1-001/C1-006+←C4-003]/C6-resource-lifecycle[C6-001]/C6-round2[C6-007]/merge-adjudication[MA-01 :18-23/MA-02 :26-27/§7.1-3 :74-78/C4-003 行 :92/C6-007 行 :100]/C10-test-protection/inventory-p2-c10 :584/:803/cra-index :280-:283）；行号 = live @ `d8b0ec4334` 全文亲读核实值（审计基线 `2a2d546fc0` 后仅平台面漂移：`GraphQLWebService` runProxy `requestBuilder.get()` 审计 :201→live :186、runGraphQL getBeanByType :93→live :74、runRest getBeanByType :241→live :235[try 仍 :241]、`QuarkusJsonRpcWebService` :26-33→live :26-35——六载体文件与窗口锚零漂移[plan 勘误 2 口径]，再漂移以 live 为准并登记）
> 命名裁定：本文落盘名 `host-startup-readiness-gate-future-exception-termination-upload-close-and-credential-pair-concurrency.md`——与 plan Goals/Scope 占位名一致零调整；四域命名 = 宿主启动就绪门禁（MA-01 域）+ future 异常终结（MA-02 域）+ 上传流配对关闭（C6-001 域）+ 凭据原子性（credential-pair，C1-001 域），与 concurrency 设计集命名风格一致；本行即命名登记载体
> 实现锚：nop-spring-web-starter `SpringHttpServerFilterConfiguration`（filters 缓存 :39 / getFilters :47-57[:48 判空 / :50 getBeansOfType / :51 sort / :53-56 锁内副本流] / registerSysFilter :62-67 / registerAppFilter :69-74 / `NopHttpServerFilter` :76-109[doFilterInternal :89-108 / :92 getFilters 调用 / :94-95 isEmpty 直通 filterChain / :99 runWithFilters / :105 syncGet] / createFilter :111-120[:116 order / :118 addUrlPatterns("/*")]）、nop-quarkus-web `HttpServerFilterRegistrar`（filters :44 / getFilters :46-57 / setupSysFilter :59-61 / setupAppFilter :63-65 / registerFilter :67-91[:68-69 enableFilter 守卫 / :71「此时Nop平台还没有初始化」注册期注释 / :72 filters.register / :73 getFilters / :75-76 isEmpty→rc.next() / :78-82 context / :84 runWithFilters / :85-88 exceptionally→rc.fail / :90 order]）、nop-quarkus-web `QuarkusExecutorHelper`（g_serverContext TL :28 / makeServerContext :34-41 / **withRoutingContext :43-67**[:47-49 context==null 直通 / :51 future / :52 runOnContext / :53-56 bindMDC / :57-61 try[:58 `FutureHelper.bindResult(task.get(), future)`]finally[:60 unbindMDC] / :63 return / :64-66 finally TL remove] / executeBlocking :69-82）、nop-api-core `ContextTaskQueue`（**只读证据零触碰**：flushTasks :150-162[:157 task.run / :158-159 catch Throwable→仅 LOG.error——平台吞异常语义在位]）、nop-ioc `IocCoreInitializer`（只读窗口锚：initialize :37-57[:42 registerInstance(appContainer) / :44-56 capped 块[:46 start / :51 awaitStartFinished / :52-55 catch 回落 parentContainer+adapt 重抛]] / destroy :60-67[:64]）、nop-core `CoreInitialization`（**判据锚**：initialized :44 private static volatile / **isInitialized :54-56 公共静态只读** / initializeTo :82-127[:118 initialized=true / :119-123 catch→destroy+adapt] / destroy :158-206[:199 initialized=false] / reinitialize :208-264[:260]）、nop-api-core `BeanContainer`（isInitialized :24-26 / instance :28-33[:31 ERR_IOC_BEAN_CONTAINER_NOT_INITIALIZED] / registerInstance :35-37 无守卫[null 即反注册，:19-23 javadoc]）、nop-spring-core-starter `NopSpringCoreAutoConfig`（onStart :28-34[:31 宿主桥注册 / :33 NopApplication.run] / onClose :37-39[:38 destroy]）、nop-quarkus-core-starter `QuarkusIntegration`（start :26-52[:47 registerInstance]）、nop-http-server-filters `CoreInitializationGuardFilter`（:13-34[:24-25 isSuspended/isInitializerRunning / :29 sendResponse(503,...) / :31 completedFuture——**运行于 getFilters 求值之后**的佐证锚 + 平台 503 先例]）、nop-graphql-core `GraphQLEngine`（**送达语义锚**：toRpcResponse :576-590[:578 task.get / :579-581 thenApply / :582-585 exceptionally→buildRpcResponse / :586-589 catch Exception→buildRpcResponse]）、nop-graphql-core `GraphQLWebService`（runGraphQL :72[:74 getBeanByType try 外，try :79] / runProxy :177[:183 getBeanByType、:186 requestBuilder.get() 均 try 外，try :201] / runRest :231[:235 getBeanByType try 外 / try :241 / :243 requestBuilder.get() try 内——(c) 保护面] / runJsonRpc :397-400[:398 getBeanByType 无守卫]）、nop-quarkus-web `QuarkusGraphQLWebService`（proxy :48-57[:52] / graphql :62-64[:63] / rest :69-76[:72] / restQuery :81-88[:84] / pageQueryGet :92-97[:95] / pageQuery :101-106[:104]）+ `QuarkusJsonRpcWebService`（jsonRpcSpring :26-35[:27]）、nop-spring-file `SpringFileService`（upload :41-61[:46 file.getInputStream() 开流 / :49-53 buildUploadRequestBean / :55 uploadAsync / :56-58 仅 catch IOException / :60 thenApply]）、nop-quarkus-file `QuarkusFileService`（uploadFileAsync :53-96[:56 withRoutingContext / :76 getBody(InputStream) 开流 / :78-82 buildUploadRequestBean / :83 uploadAsync / :87-89 res 判空 / :90 throw No Upload File / :91-94 catch Exception→错误响应] / doDownload :163-178[:167]）、nop-biz-file-core `AbstractGraphQLFileService`（getGraphQLEngine :38-40[BeanContainer.getBeanByType] / uploadAsync :42-51[:43 / :45-48 newRpcContext / :50 executeRpcAsync] / buildUploadRequestBean :84-95[:89 new UploadRequestBean(is,...)]）、`UploadRequestBean`（**归属核对锚**：inputStream 字段 :19 / 4 参构造 :33-40 / getInputStream @JsonIgnore :94-97——归属 nop-biz-file-core 非 api/core 平台叶）、`NopFileStoreBizModel`（校验链锚：upload :101-113[:102 checkMaxSize / :103 checkFileExt / :104 checkBizObjName / :105 checkUploadAuth / :107 saveFile] / checkUploadAuth :129-133）、nop-file-dao `DaoResourceFileStore`（唯一关闭点：saveFile finally :240-244[:241 IoHelper.safeCloseObject(is)]）、nop-spring-proxy `WebClientHolder`（authorization :8 / cookie :9 普通可变字段 / pair 存取器 :27-41）+ `SpringWebProxy`（webClients CHM :34 / sseStream :41-56 / restProxy :58-73 / callRest 读点 :81-86 / callWeb 读点 :107-112 / refreshAccessToken :129-157[:131-132 refreshAuthUrl null 早退 / :137-141 刷新请求头读点 / :148 updateCookie(getCookie) / :149 setCookie 先写 / :154 setAuthorization 后写——非原子双写] / getWebClient :159-161 computeIfAbsent / newWebClient :163-177[:174-175 初始化写点]）+ `WebClientConfig`（getHeader :15）；测试先例 = `TestVertxHttpServerContextAdapter`（RoutingContext JDK 代理 :168-192[:169-170 Proxy.newProxyInstance]）/`TestServletHttpServerContextAdapter`（JDK 代理 request/response 假体，零依赖）；惯例先例 = `JsonRpcWebSocketHandler` :96-97（authHeaders/boundUserId volatile）+ `IoHelper.safeCloseObject` :137-144（null 守卫+吞异常——幂等面）+ `IContext` :23/:130（runOnContext(Runnable)）+ `FutureHelper`（bindResult :298 / syncGet :158）
> Related: [mqtt-session-ownership-sidecar-offload-rpc-factory-and-connect-cleanup-concurrency.md](mqtt-session-ownership-sidecar-offload-rpc-factory-and-connect-cleanup-concurrency.md)（plan 2523——本文结构模板 + 挂死类红钉失败模式=超时预算先例 + 确定性 close 计数形态先例 + 其 B1-06 判级落账义务在本批的映射 = C6-008 判级注记随行[§四第 9 条]）；[cluster-election-lifecycle-instance-snapshot-and-rpc-termination-concurrency.md](cluster-election-lifecycle-instance-snapshot-and-rpc-termination-concurrency.md)（plan 2521——**R21 族纪律先例**：completeExceptionally(adapt) 范式引用不重开，裁定②同族异站禁照抄细节）；[feishu-channel-lifecycle-file-delivery-integrity-and-resource-envelope-concurrency.md](feishu-channel-lifecycle-file-delivery-integrity-and-resource-envelope-concurrency.md)（plan 2522——R21 族第 4 例 + TrackingInputFile fd 真实计数 GC 假绿教训[裁定⑤红③禁用形态] + 挂死类超时形态先例）；plan 2499（R1-04 bindCancel+上下文传播五入口——软依赖已解除，其平台钉零回退硬条款）；plan 2496（R1-01 周期任务绝缘层——ContextTaskQueue 吞异常面属 2.3-CL-018 承重原语轨，与裁定②宿主面正交零触碰）；plan 2507（concurrent-start join 落 IocCoreInitializer :44-56——裁定①窗口论证表 fail 路径行 :52-55 语义输入）

## 一、裁定总表

| 裁定 | 成员 | 唯一结论 |
|------|------|---------|
| ① | 4.4-C1-006（P1，MA-01 主；←C4-003 吸收成员；C10-006+C10-009 双保护面） | 门禁判据 = **`CoreInitialization.isInitialized()`**（公共静态只读 API :54-56 在位——零新增平台 API、零反射、零平台叶触碰）；门禁落点 = **filter 入口最前**（Spring `NopHttpServerFilter.doFilterInternal` :92 之前 / vertx `registerFilter` lambda :73 之前——`getFilters` 本体 :47-57 零改写[R1 Major-F1]）；拒绝语义 = **双宿主统一 503 短路**（Spring：warn + `response.setStatus(SC_SERVICE_UNAVAILABLE)` + return；vertx：warn + `rc.fail(503)`——预设拒直通形态；plan 预设句「类型化异常」的承载形态裁定为 HTTP 503 类型化拒绝，理由与被拒备选见 §3.1/§六）；空链固化由构造消除（缓存只可能被 :118 之后的求值填充，无需清缓存钩子）；双宿主同批同型落点（F3 孪生禁只修单宿主，sys/app 双注册共享一门禁） |
| ② | 4.4-C6-007（P1，MA-02 主；future 族第 5 例宿主侧；C10-017 面） | host 层求值序重排：`task.get()` 先求值入局部变量 → `bindResult` 移入 try → **catch Throwable → `future.completeExceptionally(NopException.adapt(e))`**（Throwable 对齐队列语义 :158；adapt 对齐 R21 族 2521/2522 范式）；MDC unbind finally :60 保持；`g_serverContext` TL set/remove :45/:65 零触碰（MA-05/C1-002 不并族）；平台面 `ContextTaskQueue`/`BaseContext` 零触碰（归 2.3-CL-018 轨）；失效面 (a)-(g) 七调用点核对闭合（(c) 已保护面零回退、(f) Spring 宿主零触碰）；§7.1-3 联动与裁定①同批交付兑现 |
| ③ | 4.4-C6-001（P1，独立；C10-011 面；C6-008 不并族） | **whenComplete 配对形态**（audit 建议形态维持[R1 复核]）：`UploadRequestBean` 既有引用侧持流（:19 字段零改）+ 提交成功即 `res.whenComplete((r, e) -> IoHelper.safeCloseObject(input.getInputStream()))` 注册关闭（成功/失败双路径均触发）；**前段同步抛出段（getGraphQLEngine/newRpcContext）close 兜底条款**成文（§3.3——Spring 局部 catch 重抛前关流、Quarkus 外层 catch 分支关已开流）；恰一次不变式 = 宿主侧 close 恰 1（whenComplete CF 语义恰一触发 + 前段兜底与 whenComplete 互斥），成功路径 saveFile finally :241 先关经 `safeCloseObject` :137-144 幂等面无害；**送达语义 = toRpcResponse :582/:586 将校验失败包装为正常完成 isOk=false**（whenComplete 对正常/异常完成均触发——配对不依赖送达形态）；IOException catch :56-58 行为零变更；红③钉注入点 = stub `IGraphQLEngine.executeRpcAsync`（BeanContainer.registerInstance 注入 stub 容器）；`UploadRequestBean` 归属核对结论 = nop-biz-file-core 宿主邻接叶、预设形态零触碰该类（PA 零申报触发）；双宿主同 diff |
| ④ | 4.4-C1-001（P1，独立；C10-001 面；CL-004 零触碰） | **volatile 不可变 `Credentials` 单引用整体替换**（`WebClientHolder` 新增 `private volatile Credentials credentials` 单字段 + `getCredentials()` 读点；**legacy pair 字段 :8-9 与存取器 :27-41 移除形态**——消费面枚举闭合为 `SpringWebProxy` 同文件：newWebClient :174-175 改单写、refreshAccessToken :149/:154 双写改 doOnNext 末单写、callRest :81-86/callWeb :107-112/refresh :137-141 三读点改快照单读）；AtomicReference CAS 被拒（最后写者胜出为登记合法残余竞态，无 CAS 消费语义）；对齐 `JsonRpcWebSocketHandler` :96-97 volatile 惯例；红④驱动器随移除形态同步收口义务成文（R1 Major-F2——缺陷态红钉打 pair API、修复态绿钉打单写 API，两形态分别登记防假红/假绿）；刷新去重/单飞不扩张（Non-Blocking Follow-up） |
| ⑤ | 测试缝与零回退钉（全批） | 红①–红④驱动器表（红①③当前即红 + 红④构造性交错红 + 红②悬挂钉当前即红[失败模式=超时预算]；红①经 filter 入口驱动[R1 Major-F1]+rule-23 伴生钉[未就绪期 getBeansOfType 零调用]；红③ stub 引擎泄漏钉 + 确定性 close 计数[禁真实 fd 计数——2522 TrackingInputFile 教训]）；**全局静态态翻转机制 = 反射写 `CoreInitialization.initialized`（:44）+ @AfterEach 强制恢复纪律**[R1 Major-F3]（initialized=false 回写 + `BeanContainer.registerInstance(null)` 清桥 :35-37 无守卫可执行）；RoutingContext/端点 JDK 代理先例[R1 Minor-F7，TestVertxHttpServerContextAdapter :168-192]；零回退钉全列 = nop-spring 37 + nop-quarkus 14 双 log（含 2499 landed 钉点名）；三 pom test dep 补齐（勘误 6）；C10 五条全条承载集落名（§五.5） |

## 二、背景与动机

R2-21 批 4 项（P1×4）覆盖宿主集成层三类资源面 + 一类可见性面：**启动窗口门禁域**（MA-01 = C1-006 主+←C4-003 吸收：宿主桥注册（Spring `onStart` :31 / Quarkus `start()` :47）到 `IocCoreInitializer` :42 重注册（并延伸至平台满级 :118）的秒级窗口内，`getFilters` 首访把「落在 Spring context/CDI 上的空 filter 链」synchronized 永久固化——全部 IHttpServerFilter 实现均为 nop-IoC bean（全仓零 CDI SyntheticBean、唯一 spring-*.beans.xml 仅 nopBaseMapper），任一启动窗口请求即令认证链持久静默旁路直至重启不可自愈[变体 B]；更早窗口 getBeansOfType 未及桥则 ERR_IOC_BEAN_CONTAINER_NOT_INITIALIZED 500[变体 A]；既有平台守卫 `CoreInitializationGuardFilter` 运行在 getFilters 求值之后无法防护[失效面 (e) 佐证]）、**future 终结域**（MA-02 = C6-007：`withRoutingContext` :57-61 把「取结果」与「异常转译」合并在 :58 单表达式，task supplier 同步异常逃出 lambda 落入平台 `ContextTaskQueue.flushTasks` :158-159 catch-Throwable-仅记日志，返回 RESTEasy 的 future 永不完成——`/px/` 畸形 JSON 用户可触发的认证请求永久悬挂，Quarkus 默认无请求级超时）、**资源配对域**（C6-001：controller 开流即交出所有权，`NopFileStoreBizModel.upload` :102-105 四道校验任何一道先行抛出即形成「半个获取」——唯一关闭点 `DaoResourceFileStore` finally :241 永不可达，每被拒上传泄漏 1 个 fd[Spring 主体]，Quarkus 孪生 :76 同根因泄漏堆内流）、**凭据可见性域**（C1-001：`WebClientHolder` authorization :8/cookie :9 由 Reactor 回调线程写（:149 先/:154 后非原子双写）、请求线程读（:81-86/:107-112），无 volatile 无锁无 happens-before——撕裂凭据对可驻留至下一次成功刷新，网关型部署 token 过期窗口内并发请求持续 401）。本文档一次裁定 4 成员 + 测试缝（裁定⑤），使实施只有一种合法形态；C10 五条全条承载（勘误 4 口径）、C6-008 判级注记随行落账、刷新去重不扩张与 CL-004 零触碰随批成文（条款见四、五）。

## 三、核心设计契约

### 3.1 裁定①：filter 入口就绪门禁 + 空链固化构造消除（MA-01，三登记一处闭合）

- **门禁判据（预设，读点核对结论）**：`CoreInitialization.isInitialized()`（:54-56，public static，读 :44 `private static volatile boolean initialized`）——**公共只读方法在位，产品侧零新增 API、零反射、nop-core 零修改**；任务书预设的「若无 isInitialized 公共方法则登记反射/新增分支」**不触发**（核对负结论登记）。判据语义 = 平台满级布尔：:118（initializeTo 全部 initializer 完成后置 true）/ :199（destroy 置 false）/ :260（reinitialize 完成置 true）三点翻转，语义自洽；`volatile` 保证请求线程读最新值。判据严格强于审计危险窗：审计窗 [宿主桥注册..IocCoreInitializer :42] ⊂ 门禁关闭窗 [-∞..:118]——危险窗内必被拒绝，固化由构造消除。
- **门禁落点与形态（预设，双宿主矩阵）**：门禁行插在两宿主 filter 入口 lambda/方法体最前，**先于 getFilters 快照求值、先于 ServletHttpServerContext/VertxHttpServerContext 构造、短路于 runOnContext 之外**（§7.1-3 联动的结构性保证——门禁分支所在的 doFilterInternal 在 servlet 线程、vertx lambda 在 event loop，均不进入 withRoutingContext task 通道）；`getFilters` 本体 :47-57（两文件孪生）零改写[R1 Major-F1 修复形态裁定]。

| 宿主 | 落点 | 门禁分支语义 | 双注册覆盖 |
|------|------|------------|-----------|
| Spring | `NopHttpServerFilter.doFilterInternal` :89-108 最前（:92 getFilters 之前） | warn（消息含 requestURI 与 not-initialized 语义）→ `response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE)` → return | sys/app 两 FilterRegistrationBean（:62-74）共享 NopHttpServerFilter 一体，一门禁覆盖双注册 |
| Quarkus | `registerFilter` lambda :72-90 体最前（:73 getFilters 之前） | warn（消息含 rc.normalizedPath() 与 not-initialized 语义）→ `rc.fail(503)` → return | setupSysFilter/setupAppFilter（:59-65）注册的两 lambda 同体，一门禁覆盖双注册 |

- **拒绝语义一次定夺（预设拒直通形态）**：双宿主统一 **503 短路 + 门禁分支 warn 观测**。承载形态裁定为 HTTP 503 类型化拒绝而非 Java 异常通道：①503 = 「未就绪可重试」，与平台守卫既有先例 `CoreInitializationGuardFilter` :29 `sendResponse(503,...)` 同语义；②Spring 侧抛异常经容器错误通道映射 500（= 复刻变体 A 的语义错误形态），且错误分派（ERROR dispatch）可再入 filter 链、错误页机制自身可能依赖已初始化容器；③`setStatus+return`/`rc.fail` 的短路路径零依赖（仅 response/rc 通道可用），最坏环境下确定可达。**空链直通+warn（audit 建议②）被拒**——直通即重构变体 B（认证旁路窗口仍在，仅少固化），预设拒直通（§六）。
- **窗口论证表（门禁读点 × 未就绪请求路径矩阵；含 plan 2507 fail 路径登记）**：

| 时间窗（两宿主同构） | initialized | BeanContainer 桥指向 | 修复前行为 | 门禁后 | 归属 |
|----|----|----|----|----|----|
| t0 宿主启动前（Spring onRefresh 已监听 / Quarkus HTTP 已就绪，onStart/start 未跑） | false | 无（provider null） | 变体 A：instance() :31 抛 ERR_IOC_BEAN_CONTAINER_NOT_INITIALIZED → 500 | 503 短路（变体 A 消除） | 本批修复 |
| t1 宿主桥注册（Spring :31 / Quarkus :47）.. :42 重注册 | false | 宿主桥（getBeansOfType(IHttpServerFilter)=空） | 变体 B：空链 synchronized 永久固化 → 认证旁路 | 503 短路（固化不可达——缓存只可能被 t5 后求值填充） | 本批修复 |
| t2 :42 .. :118（appContainer 装载完，含 start+awaitStartFinished，平台未满级） | false | appContainer | 请求可触达未满级平台（可解析真链但平台语义未就绪） | 503 短路（**安全侧收紧，行为面变化登记**——探针/健康检查在窗口内收 503 非 200/500） | 本批修复引入 |
| t3 初始化失败（:52-55 回落 parentContainer → :119-123 destroy → :199） | false（destroy 后） | parentContainer/宿主桥 | 请求可触达回落容器 | 503（**plan 2507 fail 路径窗口语义登记：initialized=false 为门禁安全侧**——不向半死容器分发请求） | 本批修复引入 |
| t4 destroy/shutdown（Spring onClose :38 → destroy :199） | false | parentContainer | drain 期请求仍可服务 | 503（安全侧收紧，登记；与 t3 同语义） | 本批修复引入 |
| t5 平台满级后（:118+） | true | appContainer | 真链服务 | 门禁直通（volatile 布尔读，零锁零开销）+ 平台守卫 suspended/initializerRunning 503 语义零触碰不冲突 | — |

**[R2 Minor-3 残余登记]reinitialize 窄窗**：`reinitialize()`（:208-264）全程不清 initialized（:260 才重置）——其 destroy 半程 IocCoreInitializer.destroy :64 回落 parentContainer 而门禁仍放行，若首次 getFilters 求值恰落该半程（此前零请求）空链固化且守卫不在链内。既有残余非本批引入、窗口极窄，诚实登记不追求此窗（守护须 reinitialize 前必有请求已填缓存）。

- **C4-003 controller 入口传递性消除论证表**：

| 入口面 | 是否门禁下游 | 论证 |
|----|----|----|
| Spring filter 入口（doFilterInternal :92） | 门禁本体 | 落点即门禁 |
| Quarkus filter 入口（lambda :73） | 门禁本体 | 落点即门禁 |
| Spring controller 全集（/graphql、/r/、/p/、file upload/download、/rest/、/sse/、/jsonrpc） | 是 | `createFilter` :118 `addUrlPatterns("/*")` + :116 order——全部 servlet 请求先经 NopHttpServerFilter |
| Quarkus controller 全集（/px/、/graphql、/r/、/p/、file、/jsonrpc） | 是 | filters.register :72 对全部路由按 order :90 前置生效 |
| 平台守卫 CoreInitializationGuardFilter | getFilters 求值之后 | **无法防护固化**（失效面 (e) 佐证成立——其自身是 nop-IoC bean，固化后根本不在链内）；本批零触碰，与门禁互补（其 isSuspended/isInitializerRunning 判据覆盖运行期 suspend/重初始化面） |
| 非 HTTP 入口（调度任务/出站 RPC 等） | 不适用 | 不经 filter 入口，不属 C4-003 登记面（HTTP 请求面全集）——批外零申报 |

- **C10-009 观测层级**：观测发生在**门禁分支层**（host filter 入口 warn 一条）——拒绝决策点自带观测，不依赖下游 filter/controller 日志反推；钉断言以 503 状态/rc.fail 记录为准（§5.1 红①门禁钉）。
- **备选拒绝**：getFilters 本体改[R1 Major-F1]——红①驱动器契约 = 经 filter 入口驱动，修复若落 getFilters 内则钉与修复错位（入口直呼 getFilters 的钉在修复后仍固化空链永不变绿），且把「请求可服务性」语义（503 需 response/rc 通道）混入查询函数属职责混淆；`BeanContainer.isInitialized()`（:24-26）作判据——t1 窗口内已为 true 而桥上 filter 为空，门禁恰在危险窗放行 = 门禁失效；isInitializerRunning/isSuspended 作判据——不覆盖 t0/t1 前段与 t3/t4 后段，且属平台守卫面；初始化完成后清空 filters 缓存重算（audit 建议③）——门禁构造消除后为冗余第二变更点；就绪钩子挂 NopApplication.run 后（audit 建议④）——触碰窗口锚（Non-Goals 零触碰预设）需平台面扩张（§六）。
- **C4-004 装配面零触碰声明**：spring-delta 注册回调门禁面（`NopBeansAutoConfiguration`/`NopMybatisSessionFactoryCustomizer`）与 MA-01 同门禁原语不同失效层（装配面 refresh 期 vs 请求面），归 R4-28，本批零触碰。

### 3.2 裁定②：withRoutingContext 同步异常 host 层终结（MA-02，R21 族第 5 例）

- **修复形态（预设，R21 族纪律引用不重开）**：`withRoutingContext` :57-61 的 runOnContext 回调体改为——`try { CompletionStage<T> result = task.get(); FutureHelper.bindResult(result, future); } catch (Throwable e) { future.completeExceptionally(NopException.adapt(e)); } finally { AuthMDCHelper.unbindMDC(); }`。三要素：`task.get()` 先求值入局部变量（异常不再逃出 lambda）；`bindResult` 移入 try（求值与转译同 try 界）；catch Throwable → `completeExceptionally(adapt(e))`。范式沿 2521（ClusterRpcClient 体+err 回调体双包裹）/2522（OkHttp onResponse 体 catch→completeExceptionally(adapt)）先例**引用不重开**——站点语义不同（TaskQueue drain lambda vs 工厂/HTTP 回调）禁照抄细节。syncGet 侧 :105（Spring）与本修复正交零触碰。
- **catch Throwable vs Exception 一次定夺（预设 Throwable）**：与队列吞异常语义对齐（`ContextTaskQueue.flushTasks` :158 catch Throwable——平台以 Throwable 为 drain 边界）；catch Exception 将漏 Error/包装通道造成悬挂残留 = 缺陷半修；per-request future 的失败终结优先于异常类型保真（adapt 保留 cause 链）。
- **MDC 与 TL 保持声明**：bindMDC :53-56 / unbindMDC finally :60 形态零改写；`g_serverContext` TL set :45/remove :65 配对语义零触碰（C1-002/MA-05 同函数不同根因归 R4-28——**本批修改禁顺手改 TL set/remove**）；**[R2 Minor-4 残余登记]bindMDC 前奏（lambda 内 try 之前）抛出仍逃 lambda 落平台吞异常**——future 悬挂残余未入 (a)-(g) 枚举（概率低：TL 读+MDC put），登记不追求。
- **失效面 (a)-(g) 七调用点核对表（行号 = live @ d8b0ec4334，审计行号漂移已登记 Source）**：

| 点 | 站点 | supplier 同步抛出面 | 修复后行为 |
|----|----|----|----|
| (a) | `QuarkusGraphQLWebService.proxy` :48-57[:52] → runProxy :177 的 getBeanByType :183、requestBuilder.get() :186（均 try 外，try :201） | 用户可触发主形态（/px/ 畸形 JSON→JSON.parse 抛 NopException） | completeExceptionally 覆盖 → RESTEasy 产 500 非悬挂 |
| (b) | graphql :62-64 / rest :69-76 → runGraphQL :74 / runRest :235 getBeanByType try 外 | 启动窗口/平台销毁后 engine 缺失即抛 | 同根因随 (a) 一并消除 |
| (c) | restQuery :81-88 / pageQueryGet :92-97 / pageQuery :101-106 → doPageQuery 将 buildRequest 包为 runRest 的 requestBuilder（get() :243 在 try :241 内） | **已保护面**（被 runRest 内 catch 转错误响应） | **零回退声明：禁回退改写其内部结构**；host 层 catch 兜底仅自然覆盖残余，不改变其既有保护形态 |
| (d) | `QuarkusJsonRpcWebService.jsonRpcSpring` :26-35[:27] → runJsonRpc :398 getBeanByType(JsonRpcService) 无守卫 | 启动/销毁窗口抛出 | 覆盖 |
| (e) | `QuarkusFileService.uploadFileAsync` :53-96 / doDownload :163-178 | upload supplier 自带 try/catch :91-94 转错误响应；download 同步抛出面低 | 自带保护面零回退；host 层 catch 兜底覆盖残余（引擎缺失窗口归 (b) 类） |
| (f) | Spring 宿主对照（SpringGraphQLWebService/SpringJsonRpcWebService 直调 runXxx） | controller 线程直调，异常经 Spring MVC 通道 → 500 | **无此缺陷，零触碰**（反证缺陷仅在 runOnContext 间接层） |
| (g) | 平台面 runGraphQL/runRest/runProxy 的 catch(Exception)（live :110/:220/:269） | 覆盖各自 try 内同步异常 | 本批抛出面全部位于各 catch 之外——枚举闭合 |

- **平台面零触碰声明**：`ContextTaskQueue`/`BaseContext`（nop-api-core，plan-first Protected Area）零修改——平台吞异常语义 :158-159 为 2.3-CL-018 承重原语轨登记设计语义；host 层终结不改变平台 flushTasks 行为，只改变 host 自己的 future 通道（软依赖 R1-04/R1-01 已解除，本批对绝缘层零回退义务）。
- **§7.1-3 联动矩阵（与裁定①同批交付兑现）**：主保证 = 门禁短路位置在 runOnContext 之外（doFilterInternal servlet 线程 / vertx lambda event loop——t0-t4 窗口请求根本不进入 withRoutingContext）；兜底保证 = 即使异常进入 task 通道（如 (b) 类启动窗口 getBeanByType 抛出），裁定②的 completeExceptionally 使 future 必终结非悬挂。两修复同一提交序列落地，「门禁单独上线恶化悬挂」风险闭合；红②钉为该联动兑现的机械证明（§5.1）。
- **备选拒绝**：catch Exception——见上；平台面修 flushTasks（complete 传播/异常标记）——nop-api-core Protected Area + 归 2.3-CL-018 轨；asyncResource 超时兜底（给 future 加 timeout）——超时语义属 CL-005 登记面（filter future 无超时），本批不扩张（§六）。

### 3.3 裁定③：上传流获取侧配对 close（C6-001，双宿主 F3 同 diff）

- **配对形态（预设，两案一次定夺禁第三形态）**：**whenComplete 形态**（audit 建议形态，R1 复核维持）——`UploadRequestBean` 既有引用侧持流形态零改（inputStream :19 字段、4 参构造 :33-40、@JsonIgnore getInputStream :94-97 均既有），宿主 service 在 `uploadAsync` 提交成功后立即 `res.whenComplete((r, e) -> IoHelper.safeCloseObject(input.getInputStream()))` 注册关闭——whenComplete 对正常完成与异常完成均恰触发一次，成功/校验失败双路径均关。**校验前移被拒**：checkUploadAuth :129-133 依赖完整 record 上下文与 IServiceContext，元数据先行校验需无引擎构造 IServiceContext，确定性劣（R1 复核维持），且改变「四道校验在引擎执行线程内」的时序语义（§六）。
- **送达语义（R1 Minor-F6 精度）**：真引擎将校验失败包装为**正常完成的 isOk=false 响应**——`NopFileStoreBizModel.upload` :102-105 校验异常在引擎执行线程抛出 → `GraphQLEngine.toRpcResponse` :582-585 exceptionally → `buildRpcResponse` 错误响应 → future 正常完成 isOk=false；try 内同步抛出段 :586-589 catch Exception 同包装。**whenComplete 对两种送达形态均触发**（CF 语义）——配对正确性不依赖送达形态，钉的 stub 以两形态覆盖（§5.1 红③）。注入点 = stub `IGraphQLEngine.executeRpcAsync`（`BeanContainer.registerInstance` 注入 stub `IBeanContainer`，getGraphQLEngine :38-40 消费），非直接 stub 校验链（校验链在平台 biz 面，stub 引擎即可驱动宿主配对行为）。
- **前段同步抛出段 close 兜底条款（设计文档义务，双宿主各落点）**：`uploadAsync` :43-50 的 getGraphQLEngine/newRpcContext 同步抛出时 `res` 尚未赋值、whenComplete 未注册——若不兜底则开出的流泄漏（引擎缺失/容器异常窗口）。既定形态：**Spring** —— upload :41-61 内对提交段加局部守卫（Throwable 通道 catch → close → 原样重抛[R2 Minor-1 自洽化：与裁定② Throwable 标准一致]），**:56-58 既有 catch IOException 分支行为零变更**（getInputStream 的 checked 通道不动，error-response 语义保持）；**Quarkus** —— uploadFileAsync 外层 **catch Exception** 分支（既有错误响应 envelope 语义零回退——**[closure audit Major-3 闭环]catch Throwable 统一形态被否决**：buildResponseForException checked IOException 逃逸 + 失效面 (e) 保护面零回退违反，拆为 catch Exception [envelope 保持] + catch Throwable [close+rethrow] 两通道；**[R2 Major-1 三细节落位规格]**：①`inputStream` 声明于内层循环 :76，须提升为外层局部变量（`InputStream opened = null`，每 part 开流时赋值）方可在 :91 catch 作用域内可见；②catch 内按「本迭代已提交否」关流（`opened != null && res == null` 时 safeClose(opened) 并置回 null）——不依赖 res 判空（part₁ 成功 res₁ 非 null 而 part₂ 于 :76 抛出时，字面 res!=null 读法漏关 part₂ 流——用户可触每泄漏 1 流）；③catch 通道扩为 Throwable（Error 经裁定②终结名义下已开流同样关流），Spring 守卫通道同升 Throwable（原样重抛语义不变）[R2 Minor-1 自洽化]；:90 No Upload File 抛出点在流未开路径（:70-71 continue 先于 :76 getBody）零泄漏。前段兜底与 whenComplete **互斥**（前段抛出时未注册、注册后前段不可能再抛）——恰一次由构造保证。
- **恰一次不变式与防双关观测点（R1 Minor-F8）**：观测点 = **宿主侧 close 恰 1 次**（stub 引擎形态下 saveFile finally 不在钉内——平台侧零触碰零计数）；成功路径时序 = 引擎执行线程 saveFile finally :241 先关 → future 完成 → 宿主侧 whenComplete 后关——双关经 `IoHelper.safeCloseObject` :137-144（null 守卫+吞异常）幂等面无害（论证登记；钉**不对宿主+平台两处求和计数**）。
- **UploadRequestBean 归属核对结论（PA 前置核对项闭合）**：归属 `nop-service-framework/nop-biz-file-core`（io.nop.file.core）——非 nop-api-core/nop-kernel 平台叶、非 nop-http-api；预设 whenComplete 形态**零触碰该类**（无 AutoCloseable 改造、无签名变更，宿主侧仅读取既有引用注册回调）——PA 判定表第 3 行条件申报**不触发**，plan-first 申报义务解除。
- **Quarkus 孪生同 diff**：:76 getBody 开流（RESTEasy 缓冲区背书，泄漏面为堆内流非 fd）——同型 whenComplete + 外层 catch 兜底，与 Spring 同批同型落点禁只修 Spring；跨单元同族两构造点（NopDatavExportTaskBizModel/ReportDeliveryExecutor）零触碰（登记去向 5.3/5.4 批）；C6-008（servlet complete() 洗白）不并族零触碰，判级注记随行落账（§四第 9 条）。
- **备选拒绝**：AutoCloseable/try-with-resources——触碰共享 bean 契约，且 controller 方法返回 CompletionStage 即返回、流在引擎线程异步消费，资源于方法出口先关后消费**语义性必坏**（非次优，是错误）；依赖 GC 终结——rule 24 禁静默（§六）。

### 3.4 裁定④：WebClientHolder 凭据对 volatile 单引用（C1-001）

- **形态（预设，两案一次定夺禁第三形态）**：**volatile 不可变 `Credentials` 单引用整体替换**——`WebClientHolder` 新增不可变值对象 `Credentials(authorization, cookie)`（nop-spring-proxy 宿主模块内新类型，构造后零 setter）+ `private volatile Credentials credentials` 单字段 + `getCredentials()` 读点；**legacy pair 字段 :8-9 与 pair 存取器 :27-41 移除**（移除形态预设）。一次写整体替换对齐平台 volatile 惯例（`JsonRpcWebSocketHandler` :96-97 authHeaders/boundUserId 先例）；撕裂消除后「读旧值构造头被并发刷新覆盖」为最后写者胜出的合法残余竞态（plan Risks 登记语义，非缺陷）。
- **legacy 兼容矩阵（消费面枚举闭合 = SpringWebProxy 同文件，全仓 grep 亲证无其他消费方）**：

| 消费点 | 现形态 | 收口后 |
|----|----|----|
| newWebClient :174-175（初始化写点） | setCookie+setAuthorization 两写 | `setCredentials(new Credentials(config.getHeader("cookie"), config.getHeader("authorization")))` 单写——初始化路径行为保持（computeIfAbsent :160 单线程构造面，CHM 安全发布注记维持） |
| refreshAccessToken :149/:154（doOnNext :146-156 双写） | setCookie 先 + setAuthorization 后 | doOnNext 末**单写** `setCredentials(new Credentials(newCookie, newAuth))`；:148 updateCookie 读点改读快照 |
| 刷新请求头读点 :137-141 | getAuthorization/getCookie 两读 | getCredentials() 快照单读拆双值 |
| callRest 读点 :81-86 / callWeb 读点 :107-112 | 两读 | 快照单读——authorization/cookie 必同一刷新代际（构造保证） |

- **红④驱动器随兼容形态同步收口义务（R1 Major-F2 成文）**：缺陷态红 = latch 交错钉打在 **pair API** 上（写线程 setCookie→latch 握手→setAuthorization，读线程在窗口内读对）→（新 cookie, 旧 auth）撕裂红——构造性交错非竞态碰运气；修复态绿 = 钉改打**单写 API**（写线程循环 setCredentials(gen_i)，读线程循环断言 authorization/cookie 同代际）——两写间状态由构造消除，绿为构造保证。移除形态下无委托双写通道 → 无假红/假绿混合面；**若实施期复活被拒的委托 setter 形态，钉必须改打委托链并预期重红**——该形态已拒，本义务条款登记防混（§六）。
- **不扩张与零触碰声明**：刷新去重/单飞（多请求并发 401 交叉写去重，AsyncJoinType/IResourceLockManager 候选）不在本批 exit criteria——Non-Blocking Follow-up 登记；**CL-004 零触碰**（refresh 无超时 :131-132 早退语义/refreshAuthUrl null 重试必败——C3 轨独立线索）；`webClient`/`config` 字段 :6-7 零触碰（CHM computeIfAbsent 安全发布已登记合规面）。

## 四、对齐纪律条款（逐条成文）

1. **MA-01 三登记一处闭合（单批双宿主）**：变体 A（provider 未注册窗口 500）与变体 B（宿主桥后空链固化认证旁路）由同一门禁消除；C4-003（controller 入口面）随门禁传递性消除（§3.1 论证表）——C1-006 主登记 + C4-003 吸收成员不拆分（cra-index :280 单行回填带吸收注记）；双宿主同批同型落点（F3 孪生禁只修单宿主），sys/app 双注册共享一门禁禁单侧加门禁。
2. **MA-01 × C4-004/MA-05 相邻不并族**：spring-delta 装配面注册回调门禁（C4-004，R4-28）零触碰；`QuarkusExecutorHelper` TL 传播面（C1-002/MA-05，R4-28）零触碰——裁定②对 withRoutingContext 的修改禁顺手改 g_serverContext TL set/remove 配对语义。
3. **MA-02 × R21 族纪律引用不重开 + 平台面零触碰**：completeExceptionally(adapt) 范式沿 2521/2522 先例引用，站点细节禁照抄；`ContextTaskQueue`/`BaseContext` 零修改（2.3-CL-018 轨）；(c) 已保护面零回退改写为硬门禁；(f) Spring 宿主零触碰。
4. **§7.1-3 联动同批交付**：门禁短路于 runOnContext 之外（结构性）+ completeExceptionally 兜底（红②钉机械证明）——MA-01 与 MA-02 同一提交序列落地，禁拆分上线。
5. **C6-001 双宿主同 diff + C6-008 不并族 + 判级注记随行**：whenComplete + 前段兜底双宿主同型落点禁只修 Spring；C6-008（servlet complete() 洗白）判级维持 P2 随族留 7.1（cra-index :282 行内注记口径落账）；4.3-C6-005 同族异站（plan 2522 已修）零回退；跨单元两构造点零触碰。
6. **C1-001 最小契约面**：volatile 单引用 vs CAS 两案已定夺禁第三形态；pair 移除形态消费面枚举闭合（SpringWebProxy 同文件）为移除合法性前置；刷新去重不扩张（Follow-up 去向 plan Deferred 节）；CL-004 零触碰。
7. **同变更集绑定（C10 随本体，E2）**：C10-006+C10-009[MA-01]/C10-017[MA-02]/C10-011[C6-001]/C10-001[C1-001] 与各自本体同 diff 落地，禁先修本体后补测拆分交付；五条全部全条承载零半边（勘误 4 口径），集落名 §五.5；反向钉死 RP-1~RP-4 全部归 R4-28 本批零承载。
8. **硬序与批内软序**：无跨批硬依赖（软依赖 R1-04/R1-01 已解除）；批内 = 每成员先红后绿或变异/回退论证（E2）+ 双宿主同 diff 两处（MA-01/C6-001）+ MA-01×MA-02 同序列。
9. **判级落账口径随行（2523 B1-06 类义务在本批的映射）**：本批无 B1-06 工单；对应落账义务 = C6-008 判级维持注记（cra-index :282 行内）+ C10 五条全条口径注记（勘误 4）+ cra-index :280-:283 四行回填（各带 finding 注记与 plan 2524/closure audit 结论）——Phase 4 落账，口径以 plan Current Baseline 节为 SSOT。
10. **禁触边界（设计登记语义）**：nop-api-core/nop-core/nop-ioc 零修改（CoreInitialization/BeanContainer/IocCoreInitializer 只读引用——判据消费公共只读 API，测试侧反射见 §5.2）；`IHttpServerFilter`/`IHttpServerContext`/`UploadRequestBean`/`ApiRequest` 契约签名零变更；`IocCoreInitializer`/`NopSpringCoreAutoConfig`/`QuarkusIntegration` 窗口锚零触碰（如执行期需要集成层就绪钩子须沿 R2-19 裁定⑥先例四件证据链 plan-first 申报禁静默扩权）；R4-28 批外成员本体（C1-002/C4-004/C6-008/CL-004）与跨单元同号站（4.3-C6-001/5.4-C6-001/5.6-C6-001、4.3-C1-001/2.1-C1-001/4.2-C1-001/3.2-C1-001、1.7-C4-003/2.1-C4-003）零触碰——cra-index 回填逐行带 4.4 前缀核对。

## 五、裁定⑤：测试缝与零回退钉

### 5.1 红①–红④驱动器（先红后绿，E2；缺陷态红判据 → 修复后绿口径）

| 红 | 成员 | 载体形态 | 缺陷态红 → 修复后绿 | trial 上界 |
|----|------|---------|-------------------|-----------|
| ① | MA-01 | **经 filter 入口驱动**[R1 Major-F1]双宿主孪生：Spring = 同包测试构造 `SpringHttpServerFilterConfiguration`+内部 `NopHttpServerFilter` 直调 doFilterInternal（protected 同包可达）+ JDK 代理 request/response（TestServletHttpServerContextAdapter 先例）；Quarkus = `registerFilter` 传入捕获型 Filters stub（JDK 代理记录 lambda+order）→ lambda 应用于 RoutingContext JDK 代理（TestVertxHttpServerContextAdapter :168-192 先例）。钉组四件：**(i) 固化解除钉**（当前即红，C10-006 建议形态 (b) 单线程零竞态）：stub 容器（registerInstance :35-37 注入）返回真链 + 反射置 initialized=false → 入口驱动 → 翻转 initialized=true → 同入口再驱动 → 断言真链执行（缺陷态仍空链直通=红）；**(ii) 门禁钉**（C10-009 面）：未就绪入口驱动 → 断言 503（Spring response.status / vertx rc.fail(503) 记录）且下游零触达；**(iii) 初始化态回归钉**：initialized=true → 入口驱动直通真链（防过修——门禁禁拦截就绪流量）；**(iv) rule-23 伴生钉**：未就绪驱动期 stub 容器 getBeansOfType 调用计数 == 0（真拦截非仅分支存在） | 缺陷态红（空链固化+直通）→ 门禁 503 + 构造消除绿；当前即红 = 先行失败测试 | 单线程即红 |
| ② | MA-02 | stub IContext（runOnContext 内联执行回调，IContext :130 单方法 stub）+ `VertxHttpServerContext.setContext` 注入 + RoutingContext JDK 代理 → `QuarkusExecutorHelper.withRoutingContext(rc, 抛 NopException 的 supplier)` → `future.get(有限预算)` 断言 ExecutionException。**挂死类：失败模式 = 超时，给足有限 timeout 并在钉内注释失败模式语义**（2522 红④先例）；缺陷态 TimedOut 红（**当前即红=先行失败测试**，C10-017 建议形态零平台依赖）。辅钉：context==null 直通分支（:47-49）零回归——直通路径异常经调用方通道非本缺陷面 | 缺陷态红（TimedOut）→ completeExceptionally 绿（ExecutionException）；注入红（确定性构造非竞态） | 单线程 |
| ③ | C6-001 | **确定性 close 计数形态禁真实 fd 计数**（2522 TrackingInputFile GC 假绿教训）：close 计数 InputStream 包装（覆盖 `getInputStream()` 返回值）+ stub 容器→stub `IGraphQLEngine.executeRpcAsync`（BeanContainer.registerInstance 注入）。钉组：**(i) 泄漏钉**（当前即红，C10-011 面）：stub 引擎同步抛 NopException / exceptionally 完成两形态 → 断言宿主侧 close 恰 1（缺陷态 0=红）；**(ii) 送达语义等价钉**：stub 引擎返回正常完成 isOk=false（toRpcResponse :582/:586 等价形态）→ close 恰 1（配对不依赖送达形态）；**(iii) 防双关钉**：正常完成 isOk=true → 宿主侧 close 恰 1（saveFile finally 兜底不在钉内，safeCloseObject :137-144 幂等面论证在案）；**(iv) 前段兜底钉**：stub 容器使 getGraphQLEngine 抛出 → 断言流已关且异常原样上抛/错误响应语义不变。双宿主各自跑同型钉（spring-file/quarkus-file 测试树新建） | 缺陷态红（零关闭）→ whenComplete + 前段兜底绿；当前即红 = 先行失败测试 | 单线程 |
| ④ | C1-001 | **构造性交错红非竞态红**：CountDownLatch 钉写线程于 pair API 两写之间（setCookie→握手→setAuthorization）→ 读线程在窗口内读对 → 断言凭据对同一刷新代际（缺陷态撕裂红，C10-001 建议形态）；修复态绿 = 钉改打单写 API：写线程循环 setCredentials(gen_i)、读线程循环 getCredentials() 断言两字段同代际（N 轮零混合观测 + 两写间状态由构造消除论证）；**初始化路径防过修钉**：newWebClient 等价构造 → credentials 两字段 == 配置值（行为保持）；**驱动器随移除形态同步收口义务**（R1 Major-F2）：缺陷态红钉打 pair API（跑在修复前代码）、修复态绿钉打单写 API——两形态分别登记防假红/假绿（§3.4） | 缺陷态红（撕裂，构造性交错）→ volatile 单引用绿（构造消除）；先行失败测试 | 交错钉单线程编排 |

### 5.2 全局静态态翻转机制与恢复纪律（R1 Major-F3 点名机制）

- **翻转机制（预设）**：反射写 `CoreInitialization.initialized`（:44 `private static volatile`，无公共 setter——产品侧零反射零新增 API；**测试侧反射为已点名机制**）。capped `initializeTo` 被拒为钉机制：触发真 initializer 链 = 重依赖非 plain-JVM 隔离面（§六）。
- **恢复纪律（强制，@AfterEach）**：`initialized` 反射回写 false + `BeanContainer.registerInstance(null)` 清桥（registerInstance :35-37 无守卫可注入，null 即反注册——isInitialized :24-26 false 语义在案）。同 fork 内测试隔离论证：静态残留不跨测试类存活（JUnit 默认 per-method 生命周期 + 每类 AfterEach 收尾）；红④不触全局静态面（纯对象级），红②③不触 initialized（仅红①需要翻转）。
- **假体依赖面**：全部钉 plain-JVM 可跑（JDK 代理假体/stub IContext/close 计数包装/latch/反射字段读写），无 docker 门控、无 @SpringBootTest/@QuarkusTest 依赖（C10-009 缺口登记 live 复核维持——本批不引入集成测试基建）。

### 5.3 测试资产落位与三 pom 前置

- nop-spring-web-starter：红① Spring 钉新增（固化解除+门禁+回归+rule-23 伴生）——既有 5 类 24 用例零改写。
- nop-quarkus-web：红① Quarkus 钉 + 红②悬挂钉新增——既有 2 类 14 用例零改写。
- nop-spring-file / nop-quarkus-file：红③双宿主钉——**测试树新建 + pom junit-jupiter test dep 补齐**（勘误 6，Phase 2 前置项先落并单独跑通）。
- nop-spring-proxy：红④交错钉——**测试树新建 + pom test dep**。
- 红绿快照落 `_tmp/red-green-2524-summary.md` 总册 + 逐 trial 日志；端到端义务（rule 22）：门禁链 = 宿主桥注册→未就绪请求→门禁 503→初始化完成→同路径真链执行全链钉；future 链 = supplier 抛出→completeExceptionally→调用方得失败 future 全链钉；上传链 = 开流→校验拒绝→close 恰 1→错误响应正常返回全链钉。接线验证（rule 23）：①未就绪请求真不触达 getBeansOfType/下游（伴生钉计数）；②异常 future 真完成（ExecutionException 断言）；③失败路径真关流+成功路径恰一次；④交错点读侧必见完整代际。无静默跳过（rule 24）：①未就绪拒绝分支禁空链直通当正常路径；②异常必终结 future（禁 catch 后仅记日志——与平台吞异常形态划清边界）；③失败路径必关流（禁依赖 GC）；④撕裂消除禁残留双写通道。

### 5.4 既有钉兼容与零回退钉全列（钉零回改逐条 git diff 为空核对）

- 基线 **51 tests** 双 log 在案（plan 勘误 5：`_tmp/baseline-2524-host-r2.log` BUILD SUCCESS）：nop-spring 37（web-starter 5 类 24：TestZipContentEncodingFilter 10/TestServletHttpServerContextAdapter 3/TestServletHttpServerContextRemoveCookie 6/TestSpringWsEntryContextBinding_C3_012 3/TestJsonRpcWebSocketEndpointLifecycle 2[R2 Minor-2 勘误：live 实数 Zip 6/ServletAdapter 10/RemoveCookie 3/WsEntry 2/JsonRpc 3——multiset 相同纯跨类错位，plan 同步修] + core-starter 4 类 13）+ nop-quarkus 14（web：TestVertxHttpServerContextAdapter 11 + TestJsonRpcWebSocketEndpointLifecycle 3）；file/proxy 三模块零测试树（新建后基线零回退口径 = 既有 51 全绿）。
- 点名零触碰钉族：TestSpringWsEntryContextBinding_C3_012（plan 2499 landed）/两宿主 TestJsonRpcWebSocketEndpointLifecycle/TestServletHttpServerContextAdapter/TestVertxHttpServerContextAdapter/core-starter 4 类。
- 上游环境抖动按 plan 勘误 5 三联裁定（stash 基线同红 / solo 绿 / 恢复复跑全绿）处理留痕；nop-core `TestResourceCacheInvalidationChain` 负载 flake 在 `-am` 闭包内（2517–2523 八批先例）。

### 5.5 C10 五条全条承载集落名（随本体同 diff，E2；勘误 4 全条口径）

- **C10-006 + C10-009 → MA-01（全条）**：红①四钉组双宿主孪生——载体命名 `Test*StartupReadinessGate_MA01`（spring/quarkus 各一，`Test*_<工单>` 后缀惯例）。
- **C10-017 → MA-02（全条）**：红②悬挂钉——`TestQuarkusFutureTermination_MA02`；平台吞异常通道用例归 2.3-CL-018 面零代做。
- **C10-011 → C6-001（全条）**：红③四钉组双宿主——`TestUploadStreamPairClose_C6_001`（spring-file/quarkus-file 各一）；fd 真实计数形态禁用（4.3 TestFileDownloadSinkClose 先例的确定性 close 计数形态复用）。
- **C10-001 → C1-001（全条）**：红④交错钉+防过修钉——`TestCredentialPairAtomicity_C1_001`（spring-proxy 测试树新建）。
- 反向钉死 RP-1/RP-2/RP-3/RP-4 全部归 R4-28 本批零承载（inventory-p2-c10 :803 明文）。

## 六、拒绝了什么

| 被拒方案 | 拒绝理由 |
|----------|---------|
| ① 门禁落 getFilters 本体内（:47-57 加判据） | R1 Major-F1：红①驱动器契约 = 经 filter 入口驱动，修复落 getFilters 内则钉与修复错位（直呼 getFilters 的钉修复后仍固化空链永不变绿）；且 503 拒绝需 response/rc 通道（入口层可见），查询函数职责混淆 |
| ① `BeanContainer.isInitialized()`（:24-26）作判据 | t1 危险窗（宿主桥注册..:42）内已为 true 而桥上 filter 为空——门禁恰在危险窗放行 = 门禁失效；判据翻转点必须严格晚于全部危险子窗（:118 满足） |
| ① isInitializerRunning/isSuspended 作判据（照抄平台守卫） | 不覆盖 t0/t1 前段与 t3/t4 后段（窗口表）；两判据属平台守卫面（CoreInitializationGuardFilter 零触碰） |
| ① 门禁拒绝 = 空链直通+warn（audit 建议②形态） | 直通即重构变体 B 语义（下游认证旁路窗口仍在，仅少固化）——预设拒直通（plan 明文） |
| ① 门禁拒绝 = 抛类型化异常（Spring 500 通道） | 500 语义错误（未就绪应可重试=503，变体 A 500 已是缺陷形态）；错误分派可再入 filter 链、错误页机制自身可能依赖已初始化容器；setStatus+return/rc.fail(503) 零依赖确定可达 |
| ① 初始化完成后清空 filters 缓存重算（audit 建议③） | 门禁构造消除固化后为冗余第二变更点（缓存只能被 t5 后求值填充）；重算钩子需额外触发时机管理 |
| ① 就绪钩子（快照求值移到 NopApplication.run 完成后，audit 建议④） | 触碰窗口锚 NopSpringCoreAutoConfig/QuarkusIntegration（Non-Goals 零触碰预设）+ 需新生命周期挂点属平台面扩张；如复活须沿裁定⑥先例 plan-first 申报 |
| ② catch Exception（窄化） | 平台 drain 边界为 Throwable（flushTasks :158）；Exception 漏 Error 通道造成悬挂残留 = 缺陷半修 |
| ② 平台面修 ContextTaskQueue（flushTasks 传播/标记） | nop-api-core plan-first Protected Area；吞异常语义属 2.3-CL-018 承重原语轨——本批零触碰声明 |
| ② future 超时兜底（timeout 补偿） | 超时语义属 CL-005 登记面（filter future 无超时同族）；本批缺陷本体 = 永不完成，终结修复已闭合，超时不扩张 |
| ③ 校验前移（元数据先行校验再开流，audit 备选） | checkUploadAuth :129-133 依赖完整 record 上下文与 IServiceContext——需无引擎构造 IServiceContext 确定性劣（R1 复核维持）；改变四道校验在引擎执行线程内的时序语义 |
| ③ UploadRequestBean AutoCloseable/try-with-resources | 触碰共享 bean 契约（平台 upload 服务与序列化面消费）；controller 返回 CompletionStage 即返回、流在引擎线程异步消费——方法出口先关后消费语义性必坏（非次优） |
| ④ AtomicReference<Credentials> CAS | 无 CAS 消费语义（最后写者胜出为登记合法残余竞态）；CAS 循环复杂度零收益；volatile 惯例先例在案（JsonRpcWebSocketHandler :96-97） |
| ④ legacy pair 委托 setter 形态（setCookie → RMW Credentials） | 委托两调用交错 = read-modify-write lost update，与原缺陷同类（残留双写通道可再造撕裂）；R1 Major-F2 假红/假绿耦合面；移除形态消费面枚举闭合使移除零破坏 |
| 红③ 真实 fd 计数（ulimit/fd 表观测） | 2522 TrackingInputFile 教训：fd 计数缺陷态 GC 假绿实证在案——确定性 close 计数包装为已裁定等价形态 |
| ⑤ capped initializeTo 作静态翻转机制 | 触发真 initializer 链 = 重依赖非 plain-JVM 隔离面；反射写 :44 单字段为最小翻转面（R1 Major-F3 点名） |
| ⑤ @SpringBootTest/@QuarkusTest 集成基建 | C10-009 缺口登记 live 复核维持（宿主零集成测试基建）；红钉全 plain-JVM 可跑，引入基建超范围 |

## 七、与已有设计的关系

- [mqtt-session-ownership-sidecar-offload-rpc-factory-and-connect-cleanup-concurrency.md](mqtt-session-ownership-sidecar-offload-rpc-factory-and-connect-cleanup-concurrency.md)（plan 2523）：本文结构模板；其挂死类红钉失败模式=超时预算（红②）与确定性 close 计数形态（红③）先例引用；差异登记：本批红③以行为钉可达（close 计数包装可注入）无需 2522/2523 的源码级伴随钉形态；其 B1-06 判级落账义务在本批的映射见 §四第 9 条。
- [cluster-election-lifecycle-instance-snapshot-and-rpc-termination-concurrency.md](cluster-election-lifecycle-instance-snapshot-and-rpc-termination-concurrency.md)（plan 2521）：R21 族 completeExceptionally(adapt) 纪律先例——裁定②引用不重开（同族异站禁照抄细节）；2521 钉族零回退为硬条款。
- [feishu-channel-lifecycle-file-delivery-integrity-and-resource-envelope-concurrency.md](feishu-channel-lifecycle-file-delivery-integrity-and-resource-envelope-concurrency.md)（plan 2522）：R21 族第 4 例先例 + TrackingInputFile fd 计数 GC 假绿教训（红③禁用形态）+ 4.3-C6-005 同族异站已修零回退 + 4.3-C6-001（plan 2479）同号防混。
- plan 2499（R1-04）/plan 2496（R1-01）：软依赖均已解除；2499 平台钉（TestContextProviderThenOnContext 等）与 bindCancel 五入口零回退；ContextTaskQueue 吞异常面属 2.3-CL-018 承重原语轨——本批宿主面与其正交零触碰零回退。
- **下游 R4-28（G70 双宿主/反向钉束 10 项，软依赖本批）**：MA-03[C6-008]/MA-05[C1-002]/C4-004 等承接面在本批修复中已登记禁顺手引入接缝（§四第 2/3/5/10 条）；刷新去重/单飞与 CL-004 候选留 plan Deferred/Follow-up 节（watch-only/优化候选，successor 口径以 plan 为准）。
- **Owner doc 缺口声明**：nop-spring/nop-quarkus 无 03-modules owner doc——缺口登记落 `docs-for-ai/01-repo-map/module-groups.md`「集成与运行时外围」行（CLUSTER-CC-001 行先例句式）+ `docs-for-ai/04-reference/source-anchors.md` 新增 `HOST-CC-001` 锚点行（四成员契约 + 回归钉族 + 本文为设计权威指针）——归 Phase 3 统一落账，本 Phase 零 owner doc 变更。
