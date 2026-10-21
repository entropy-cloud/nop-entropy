# C10 并发正确性的测试保护力 — nop-host（WI 4.4）

- 审计单元：nop-host（宿主集成层，11 源码根 53 文件 / 4,344 行）
- 审计日期：2026-10-21（C10 终批维度，单轮执行）
- 输入：同目录 C1/C4/C6 三维度「最终保留项」19 条复核保留缺陷（P1×4 / P2×13 / P3×2）+ CL×8
- 口径：`ai-dev/skills/concurrency-resource-audit-prompt.md` C10 正文——不是有没有测试，而是"补上正确实现后，现有哪个测试会红"；无测试会红的登记"缺保护力"（与缺陷本体分开计数）

## 一、既有测试盘点

### 1.1 单元测试树（11 源码根内，10 文件 / 约 46 个测试方法，全部为单线程功能契约测试）

| 测试文件 | 覆盖对象 | 并发/资源相关内容 | 数量 |
|---|---|---|---|
| `nop-spring/nop-spring-core-starter/src/test/.../txn/TestNopSpringTransactionFactory.java` | 事务桥 | 仅钉"构造惰性不触 txn manager"（testDialectLookupIsLazyPerFactory），无线程安全断言 | 4 |
| `nop-spring/nop-spring-core-starter/src/test/.../resource/TestSpringResource.java` | 资源委托 | 无 | — |
| `nop-spring/nop-spring-core-starter/src/test/.../resource/TestPathPattern.java` | 路径模式 | 无 | — |
| `nop-spring/nop-spring-core-starter/src/test/.../ioc/TestNopSpringBeanContainer.java` | IoC 桥 | 无（审计已判无可变共享态） | 4 |
| `nop-spring/nop-spring-web-starter/src/test/.../filter/TestServletHttpServerContextAdapter.java` | servlet 适配器 | 单线程字段/头/cookie 契约；**不覆盖** sendStreamingResponse/AsyncContext/completeWithError | 12 |
| `nop-spring/nop-spring-web-starter/src/test/.../filter/TestServletHttpServerContextRemoveCookie.java` | cookie 移除 | 无 | 3 |
| `nop-spring/nop-spring-web-starter/src/test/.../filter/TestZipContentEncodingFilter.java` | zip 过滤器 | 单线程转发契约（资源面 C6 已核无缺陷） | 5 |
| `nop-spring/nop-spring-web-starter/src/test/.../ws/TestJsonRpcWebSocketEndpointLifecycle.java` | WS 端点生命周期 | onOpen/onClose/onError 序列（JDK proxy stub）；**不断言** keepalive 调度/取消配对 | 3 |
| `nop-quarkus/nop-quarkus-web/src/test/.../filter/TestVertxHttpServerContextAdapter.java` | vertx 适配器 | 单线程契约；**字面钉死** `isResponseSent` 内部标志语义（见反向钉死 RP-1） | 12 |
| `nop-quarkus/nop-quarkus-web/src/test/.../ws/TestJsonRpcWebSocketEndpointLifecycle.java` | WS 端点生命周期 | 同 Spring 孪生；stub 的 `getBeanByType` **恒返回非 null extractor**（掩蔽 C4-002 不对称面） | 3 |

**测试树为零的模块（5/11）**：nop-spring-delta、nop-spring-file、nop-spring-proxy、nop-spring-mcp-server、nop-quarkus-core-starter、nop-quarkus-file（6 个模块无任何测试文件——其中 4 个压着 P1/P2 发现）。

单元测试树内 `Thread.sleep` / `CountDownLatch` / `ExecutorService` / `CompletableFuture` 命中数：**0**。零真并发测试、零资源泄漏断言（close 计数/注册表空/池空）、零异步等待。

### 1.2 邻近平台测试（不属本单元，但作用于本单元缺陷的平台根因载体）

| 测试 | 覆盖 | 与本单元缺陷的关系 |
|---|---|---|
| `nop-kernel/nop-api-core/src/test/.../context/TestContextTaskQueue.java` | flush 退出窗口 check-then-act 竞态（锁冻结 + 状态 parking 条件 + 5s deadline 护栏的确定性竞态测试——**仓库最佳先例**） | **不覆盖** task.run() 抛 Throwable 的吞异常通道 → C6-007 缺口面无保护 |
| `nop-kernel/nop-api-core/src/test/.../util/TestFutureHelper.java` | bindResult/exceptionally 孤立语义 | 覆盖工具原语，不覆盖 QuarkusExecutorHelper 的"task.get() 在 try 外求值"装配缺口 → C6-007 缺口面无保护 |
| `nop-kernel/nop-api-core/src/test/.../context/TestContextProviderThenOnContext.java` | thenOnContext 关闭上下文语义 | 平台传播原语有测，宿主 `g_serverContext` 显式 ThreadLocal 不在其覆盖内 → C1-002 无保护 |
| `nop-service-framework/nop-graphql/.../ws/TestJsonRpcWebSocketHandler.java` | handler 功能面（subscribe/pingpong/token），事件驱动 awaitUntil（10ms 轮询 + 30s 护栏） | 单线程投递，MockWebSocketSession 不检测并发写 → C1-003 无保护 |
| `nop-demo/nop-spring-demo/src/test/.../TestDeltaMyBatis.java`（@SpringBootTest） | spring-delta mapper 装载 + CRUD | **@BeforeAll 预初始化** `CoreInitialization.initializeTo(REGISTER_COMPONENT)` → 只走 C4-004 变体 B，标准路径（变体 A）在 CI 永不可达 → **假保护** |

宿主模块自身的 `@SpringBootTest`/`@QuarkusTest` 集成测试：**0**（仅 nop-demo 出圈模块存在）。

## 二、保护力矩阵（19 行，缺陷 × 测试 × 变异结论）

变异推演基准："按各条目【建议】栏补上正确实现后，现有哪个测试会红？"

| # | 缺陷 | 判级 | 缺陷落点 | 既有测试（最近邻） | 变异结论 | 登记 |
|---|---|---|---|---|---|---|
| 1 | C1-001 凭据对撕裂 | P1 | WebClientHolder/SpringWebProxy | 无（模块测试树为空） | **无测试** | C10-001（强制） |
| 2 | C1-002 TL 不传播+静默降级 | P2 | QuarkusExecutorHelper | 平台 thenOnContext 测试不覆盖该 TL | **无测试** | C10-002 |
| 3 | C1-003 basicRemote 并发写 | P2 | 两宿主 WS session 门面 | WS lifecycle 测试（单线程，不并发发送） | **无测试** | C10-003 |
| 4 | C1-004 懒初始化 check-then-put | P2 | 事务工厂 dialect / MCP callbacks | TestNopSpringTransactionFactory（仅钉构造惰性） | **部分保护**（并发安全无测） | C10-004 |
| 5 | C1-005 上下文字段无 volatile（理论） | P2 | Servlet/VertxHttpServerContext | adapter 契约测试（单线程） | **无测试**（可见性不可单线程检出） | C10-005 |
| 6 | C1-006 启动窗口空链固化 | P1 | getFilters 孪生（两宿主） | 无（getFilters 零测试） | **无测试** | C10-006（强制） |
| 7 | C4-001 syncGet 阻塞工作线程 | P2 | NopHttpServerFilter :89-108 | 无 | **无测试** | C10-007 |
| 8 | C4-002 keepalive 滞留窗口 | P2 | JsonRpcWebSocketHandler:82-83 + 端点 | WS lifecycle 测试 | **部分保护**（滞留窗口/取消配对零断言，且 quarkus stub 掩蔽抛错路径） | C10-008 |
| 9 | C4-003 启动窗口无就绪门禁 | P2 | 两宿主 filter 入口/装配 | 无（宿主零集成测试） | **无测试** | C10-009 |
| 10 | C4-004 注册回调静默空发现 | P2 | spring-delta 两回调 | TestDeltaMyBatis（预初始化，只走变体 B） | **假保护** | C10-010 |
| 11 | C6-001 上传流校验失败不关 | P1 | SpringFileService/QuarkusFileService | 无（两 file 模块测试树为空） | **无测试** | C10-011（强制） |
| 12 | C6-002 Quarkus 只 start 不 stop | P2 | QuarkusIntegration | 无 | **无测试** | C10-012 |
| 13 | C6-003 destroy 无归属守卫 | P2 | NopSpringCoreAutoConfig.onClose | 无（测试基建自身是暴露面） | **无测试** | C10-013 |
| 14 | C6-004 startAsync 无超时无监听 | P2 | ServletHttpServerContext 流式 | adapter 测试不覆盖 streaming | **无测试** | C10-014 |
| 15 | C6-005 Gauge 旧绑定 | P2 | AgroalDataSourceFactory | 无 | **无测试** | C10-015 |
| 16 | C6-006 write(int) 吞字节 | P3 | ResponseOutputStream | 无 | **无测试** | C10-016 |
| 17 | C6-007 同步异常被吞→悬挂 | P1 | QuarkusExecutorHelper withRoutingContext | 平台 TestContextTaskQueue/TestFutureHelper（均不覆盖异常→future 绑定缺口） | **无测试**（邻近测试不达缺口） | C10-017（强制） |
| 18 | C6-008 complete() 洗白截断 | P2 | ServletHttpServerContext.completeWithError | 无 | **无测试** | C10-018 |
| 19 | C6-009 sendResponse 流契约 | P3 | IHttpServerContext 两实现 | 无 | **无测试** | C10-019 |

**矩阵统计**：会红 0 / 假保护 1（C4-004）/ 部分保护 2（C1-004、C4-002——均属"功能路径被测、缺陷面零断言"的掩蔽型）/ 无测试 16。19/19 缺陷补上正确实现后没有任何既有测试会红。P1×4 全部无保护。

## 三、反向钉死检查（既有测试钉住缺陷行为 / 掩蔽缺陷路径）

### 3.1 钉死型（按建议形态修复将导致既有测试变红，修复 diff 须先改测试）

- **RP-1（命中，条件性）**：`TestVertxHttpServerContextAdapter.testSendResponseRedirectAndIsResponseSent` 断言 `assertTrue(ctx.isResponseSent(), "vertx tracks its own responseSent flag")`——**字面钉死内部手工标志设计**。CL-008 的修复形态一（改为查询 `routingContext.response().ended()/closed()`）下，stub 未映射 `ended()`，`defaultFor(boolean)` 返回 false → 该断言必红。修复形态二（失败分支清除标志）不触雷。**约束：CL-008 修复若走形态一，测试必须同 diff 更新（stub 补 ended() 映射）。**
- 其余 18 条缺陷的建议修复形态（volatile/CAS/门禁/try-finally/completeExceptionally/completeWithError 等）经逐条比对既有测试断言，均无钉死冲突。

### 3.2 掩蔽型（测试替身宽于真实契约，测试绿 ≠ 行为对——缺陷路径在 CI 中不可达）

- **RP-2（命中）**：Quarkus `TestJsonRpcWebSocketEndpointLifecycle` 的 BeanContainer stub 对 `getBeanByType` **恒返回非 null extractor**，使 Quarkus onOpen 中途抛错族（C4-002 滞留窗口、CL-007 回调契约面）在测试中结构性不可达；Spring 孪生测试用 `tryGet` 安全语义同样不触发。修复 C4-002 须新增"extractor 缺失抛错"路径测试（stub 改为可抛错形态）。
- **RP-3（命中）**：`TestDeltaMyBatis` `@BeforeAll` 预初始化（initializeTo REGISTER_COMPONENT，initialized=true 先于 refresh）→ C4-004 变体 A（标准路径 `SpringDemoMain` 仅 `SpringApplication.run`）在全部 CI 中不可达。测试套件把"必须预初始化"这一**未声明契约**制度化为隐性约定——C4-004 的修复门禁（`isInitialized()`，经核 CoreInitialization.java:54-56/:118 在 initializeTo 成功路径即置 true）与该测试兼容，不构成钉死；但**变体 A 的回归保护只能靠新增"不预初始化"的测试上下文**，既有套件无法提供。
- **RP-4（弱命中，断言缺口）**：`testOnErrorDoesNotLeak`（两宿主同构）注释声称"onThrowable cancels the keep-alive timer"，但断言体仅 `userProperties.containsKey("graphql.handler")`——**测试名称承诺的保护语义超出断言内容**，keepalive 的 schedule/cancel 配对（C4-002 正常路径面）实际零断言。属 C10 步骤 3"测试自己放过竞争（superficially-pass）"形态的静态版。

反向钉死命中计数：钉死型 1（RP-1）+ 掩蔽型 3（RP-2/RP-3/RP-4）= **4 处**。

## 四、时序敏感测试审查（C10 步骤 3）

- 单元测试树：零 `Thread.sleep` 同步、零"未等异步即断言"（单元测试全为同步 stub 调用）——**无平台性脆弱测试**；代价是零并发覆盖（见系统性观察 S1）。
- 邻近平台测试的两处轮询等待均为"事件条件 + deadline 护栏"形态而非裸 sleep：`TestContextTaskQueue.awaitFlusherParked`（5ms 轮询 + Thread.State==WAITING + markerRun 双条件 + 5s 护栏）、`TestJsonRpcWebSocketHandler.awaitUntil`（10ms 轮询 + 30s 护栏）。属仓库认可形态，C10 登记的建议测试形态复用该先例。
- 平台 `TestContextTaskQueue` 的**锁冻结确定性竞态测试**（反射取内部 ReentrantLock，把纳秒窗口放大为确定性步骤）是本仓库并发测试的最佳先例，C10-001/C10-017 等建议形态直接沿用。
- Awaitility 在宿主相关测试树零使用。

## 五、资源泄漏检测钩子与大规模边界（C10 步骤 4/5）

- **泄漏检测钩子：零。** 单元测试树无 close 计数包装、无注册表空断言、无池空断言、无 fd/句柄计数（对照：WI 4.3 P0 修复引入的 `TestFileDownloadSinkClose` fd 计数形态为本 campaign 已有先例，C10-011 直接沿用）。C6-001（外部可稳定触发的 fd 泄漏）在 CI 中永远绿。
- **大规模边界：宿主模块无自建池/队列/缓存上限（C4 基线零命中），无 owner doc 声明规模包络**——本单元无"有界队列满/缓存上限淘汰/maxParallelism 边界"型测试对象。借用面边界（容器线程池耗尽 C4-001、globalTimer 任务积压 C4-002、连接表占用 C6-007）按 C10 口径以小规模参数化模拟登记（见 C10-007/008/017），不要求真实大规模压测。

## 六、缺保护登记（C10-001 起，与缺陷本体分开计数）

### [C10-001] WebClientHolder 凭据撕裂零测试保护（本体 C1-001，P1）

- **文件**: 缺陷本体 `nop-spring/nop-spring-proxy/src/main/java/io/nop/spring/proxy/WebClientHolder.java:8-9`
- **保护缺口**: nop-spring-proxy 模块测试树为空（零测试文件）；补上 `volatile Credentials`/`AtomicReference` 整体替换实现后，无任何测试会红。
- **并发场景关联**: 请求线程读凭据对（callRest/callWeb :81-86/:107-112）× Reactor 线程刷新写（doOnNext :146-154），撕裂写无原子性。
- **变异推演**: 撕裂写（cookie 先、authorization 后）改为单引用整体替换 → 现有测试集输出不变（本就没有）。
- **建议测试形态**: 按平台 `TestContextTaskQueue` 锁冻结先例做确定性交错：CountDownLatch 把写线程钉在两次 set 之间，读线程断言凭据对代际一致。
- **测试载体**: 新增 `nop-spring/nop-spring-proxy/src/test/java/io/nop/spring/proxy/TestWebClientHolderCredentials.java`（模块现无测试树，需新建）。
- **保护不变式**: 任一观察时刻 `authorization` 与 `cookie` 属同一刷新代际（修复后永不出现 cookie#2 + token#1 组合）。
- **失效面**: `sseStream` :41-56 与 `restProxy` :58-73 两条入口共用 holder，同一测试覆盖两读点。
- **严重程度**: P2（P1 本体的缺保护强制登记）
- **现状与工单映射**: 无测试；随 nop-host-C1-001 独立工单（吸收 CL-004 同 diff 回归）。

### [C10-002] g_serverContext 传播缺失零测试保护（本体 C1-002，P2）

- **文件**: 缺陷本体 `nop-quarkus/nop-quarkus-web/src/main/java/io/nop/quarkus/web/utils/QuarkusExecutorHelper.java:43-67`
- **保护缺口**: QuarkusExecutorHelper 零测试；平台 TestContextProviderThenOnContext 覆盖平台原语、不覆盖该显式 ThreadLocal。
- **并发场景关联**: 事件循环线程 set/remove × runOnContext 分支池线程执行 task 时 TL 从未设置，getHeaders/getParams 静默降级 emptyMap。
- **变异推演**: runOnContext 回调内补 `set/remove` 配对后，无测试变化。
- **建议测试形态**: stub IContext.runOnContext 在另一线程执行回调（Executor + latch），认证请求路径断言 task 内 `getHttpServerContext()` 非 null 且 headers 非空；消费端降级分支断言 warn 日志。
- **测试载体**: 新增 `nop-quarkus/nop-quarkus-web/src/test/java/io/nop/quarkus/web/utils/TestQuarkusExecutorHelperContext.java`。
- **保护不变式**: 经 withRoutingContext 进入的 task 在任意执行线程上都能取到本请求的 IHttpServerContext；分支间语义一致（匿名/认证请求同结果）。
- **失效面**: 消费点 QuarkusGraphQLWebService.getParams/getHeaders + QuarkusJsonRpcWebService（同测覆盖）。
- **严重程度**: P2（建议性）
- **现状与工单映射**: 无测试；随 MA-05（C1-002 主登记）工单。

### [C10-003] basicRemote 并发写无串行化零测试保护（本体 C1-003，P2）

- **文件**: 缺陷本体 `SpringWebSocketSession.java:24-25` / `JakartaWebSocketSession.java:24-25`
- **保护缺口**: WS lifecycle 测试单线程；nop-graphql TestJsonRpcWebSocketHandler 用 MockWebSocketSession 记录消息、不检测并发写。
- **并发场景关联**: keepalive globalTimer 线程 × 订阅 onNext 线程 × 容器 onMessage 应答线程三类写者汇聚同一 Session。
- **变异推演**: 门面加 synchronized/发送队列后，无测试变化。
- **建议测试形态**: stub Session 的 sendText 内用 AtomicBoolean 检测重入（in-flight flip），2 线程 × N 次 sendMessage 断言零重入；带 deadline 护栏避免修复前 flaky。
- **测试载体**: 新增两宿主孪生 `TestWebSocketSessionSendSerialization`（spring-web-starter + quarkus-web 测试树各一，F3 同修同测）。
- **保护不变式**: 同一 Session 的 sendText 任意时刻至多一个线程在写（串行化契约）。
- **失效面**: 两宿主 sendMessage + close（:29-35）一并纳入串行化面。
- **严重程度**: P2（建议性）
- **现状与工单映射**: 无测试；随 nop-host-C1-003 独立工单。

### [C10-004] 懒初始化并发安全无测试保护（本体 C1-004，P2）

- **文件**: 缺陷本体 `NopSpringTransactionFactory.java:49-54` / `GraphQLToolCallbackProvider.java:28-32`
- **保护缺口**: `testDialectLookupIsLazyPerFactory` 仅钉"构造期惰性"（并发首调零覆盖）；MCP provider 零测试。
- **并发场景关联**: 并发事务首解析方言 / MCP 并发工具列举，两线程同时读 null 重复构建 + 非 volatile 不安全发布。
- **变异推演**: 字段加 volatile 后无测试变化（该测试亦不约束 volatile）。
- **建议测试形态**: N 线程并发首调 `getDialectForQuerySpace`，stub DialectManager 计数构建次数，latch 对齐首调窗口，断言全部返回同一实例且构建计数收敛。
- **测试载体**: 扩展 `TestNopSpringTransactionFactory`（并发用例）+ 新增 `nop-ai/nop-spring-mcp-server` 测试树 `TestGraphQLToolCallbackProviderLazyInit`。
- **保护不变式**: 并发首调无异常、无撕裂读（读到的数组元素完全构造）；良性重复构建不升级为失败。
- **失效面**: 全族 (a)-(g) 已由 C1-004 穷举，测试只钉两处未守卫站点。
- **严重程度**: P2（建议性）
- **现状与工单映射**: 部分保护（并发安全无测）；随 nop-host-C1-004 独立工单。

### [C10-005] 服务器上下文字段可见性无测试保护（本体 C1-005，P2 理论）

- **文件**: 缺陷本体 `ServletHttpServerContext.java:45-47` / `VertxHttpServerContext.java:49`
- **保护缺口**: 可见性缺陷不可单线程检出；单元无 jcstress/多线程契约测试设施。
- **并发场景关联**: 请求/事件循环线程写 × executeBlocking 池线程读（当前 11 根无此调用实例——理论风险本体）。
- **变异推演**: 加 volatile 后无测试变化。
- **建议测试形态**: 不强制立测（修复成本一行 volatile + javadoc 契约化）；若立测则为多线程可见性探针（写线程翻转、读线程 deadline 内必见），成本高于收益。
- **测试载体**: 无（watch 级；随 C6-004 工单同文件搭车修复）。
- **保护不变式**: `setContext`/`responseSent=true` 对后续任意线程的读可见（JMM 语义，测试仅能统计性验证）。
- **失效面**: (a)-(f) 同族清单见 C1-005 本体；`SpringTransaction.txn` 契约豁免。
- **严重程度**: P3（弱建议，本体为理论风险）
- **现状与工单映射**: 无测试；随 C1-005 watch（搭车 nop-host-C6-004 工单）。

### [C10-006] getFilters 启动窗口空链固化零测试保护（本体 C1-006，P1）

- **文件**: 缺陷本体 `SpringHttpServerFilterConfiguration.java:47-57` / `HttpServerFilterRegistrar.java:46-57`
- **保护缺口**: getFilters 零测试；变体 B（空链永久固化→认证链旁路直至重启）无任何回归钉。
- **并发场景关联**: 宿主桥注册..IocCoreInitializer 重注册窗口内首请求触发 getBeansOfType 落在 Spring/CDI → 空列表被 synchronized 缓存永久固化。
- **变异推演**: 加初始化门禁 + 初始化完成后清缓存重算，无测试变化。
- **建议测试形态**: (a) 门禁用例：未注册 provider 时调 getFilters → 断言 fail-fast；(b) 固化解除用例：未初始化态污染缓存 → 初始化钩子后断言重算出真链。
- **测试载体**: 新增两宿主孪生 `TestHttpServerFilterSnapshotGate`（spring-web-starter + quarkus-web，F3 同修同测）。
- **保护不变式**: 未初始化期请求不产生持久化空快照；初始化完成后快照必含全部 nop-IoC filter bean。
- **失效面**: 两宿主 getFilters 孪生 + 每请求快照拷贝语义（:53-56）回归。
- **严重程度**: P2（P1 本体的缺保护强制登记）
- **现状与工单映射**: 无测试；随 MA-01 工单（C1-006 主 + C4-003）。

### [C10-007] syncGet 阻塞工作线程零测试保护（本体 C4-001，P2）

- **文件**: 缺陷本体 `nop-spring/nop-spring-web-starter/src/main/java/io/nop/spring/web/filter/SpringHttpServerFilterConfiguration.java:89-108`
- **保护缺口**: NopHttpServerFilter.doFilterInternal 零测试；异步流式下游的线程持有行为无测。
- **并发场景关联**: 每条进行中流式连接独占 1 个容器工作线程至流结束（分钟级），并发流数 × 时长线性换算池耗尽。
- **变异推演**: 改 WebAsyncManager/Callable 挂起或 syncGet 加超时后，无测试变化。
- **建议测试形态**: 小规模参数化：stub filterChain 返回后 future 由测试可控延迟 complete，断言修复后工作线程在流式期间被释放（或 syncGet 受可配置超时上界）。
- **测试载体**: 新增 `TestNopHttpServerFilterAsyncDispatch`（spring-web-starter）。
- **保护不变式**: 流式期间容器工作线程不被独占（或持有时长 ≤ 配置超时）；同步路径内联零成本语义不被误伤。
- **失效面**: sys/app 两注册实例同测；Quarkus 对照形态（无 syncGet）为合规参照不立测。
- **严重程度**: P2（建议性）
- **现状与工单映射**: 无测试；随 nop-host-C4-001 独立工单（§7.1-2 家族判级议程）。

### [C10-008] keepalive 滞留窗口与取消配对零断言（本体 C4-002，P2）

- **文件**: 缺陷本体 `JsonRpcWebSocketHandler.java:82-83` + 两端点 onOpen 装配序列
- **保护缺口**: lifecycle 测试不断言 schedule/cancel 配对；quarkus stub 恒返非 null extractor 掩蔽抛错路径（RP-2）；testOnErrorDoesNotLeak 断言缺口（RP-4）。
- **并发场景关联**: onOpen 中途抛错时 handler 已构造、定时任务已注册、userProperties 无引用 → onClose 取不到 → 任务永久滞留共享 globalTimer。
- **变异推演**: 惰性启动 keepalive / tryGetBeanByType / 构造处 try-catch-cancel 后，无测试变化。
- **建议测试形态**: (a) stub 容器令 extractor 获取抛错 → 断言无滞留任务（修复前红）；(b) 注入可观测 ScheduledFuture stub 断言 onError/onClose 后 cancel 被调用。
- **测试载体**: 扩展两宿主 `TestJsonRpcWebSocketEndpointLifecycle`（先改 stub 可抛错——RP-2 前置）。
- **保护不变式**: 每个成功注册的 keepalive 任务存在可达的 cancel 路径；装配中断不留共享 timer 常驻任务。
- **失效面**: 两宿主端点孪生 + handler 取消双路径（:114-116/:126-128）。
- **严重程度**: P2（建议性）
- **现状与工单映射**: 部分保护（掩蔽型）；随 nop-host-C4-002 独立工单（吸收 CL-007）。

### [C10-009] 启动窗口无就绪门禁零测试保护（本体 C4-003，P2）

- **文件**: 缺陷本体两宿主 filter 入口 + `NopSpringCoreAutoConfig.java:27-34` / `QuarkusIntegration.java:26-52`
- **保护缺口**: 宿主零 @SpringBootTest/@QuarkusTest；就绪探针存在但请求面门禁无测。
- **并发场景关联**: NopApplication.run 秒级窗口内到达的请求面（filter 链/controller 首访）触达未初始化全局态。
- **变异推演**: filter 入口加 isInitialized 门禁（503）后，无测试变化。
- **建议测试形态**: stub CoreInitialization 未初始化态调 filter → 断言 503/fail-fast 而非下游触达；初始化态回归断言直通。
- **测试载体**: 新增 `TestFilterReadinessGate`（两宿主孪生，随 MA-01 测试批）。
- **保护不变式**: 未就绪期非健康路径请求收到明确 503，绝不进入下游处理（含 controller 入口全集）。
- **失效面**: (a) Spring sys/app 两实例；(b) Quarkus 两个 vertx filter；(c) 五类 controller 入口随门禁一并消除。
- **严重程度**: P2（建议性）
- **现状与工单映射**: 无测试；随 MA-01 工单（C4-003 成员）。

### [C10-010] spring-delta 标准路径静默空发现被测试套件掩蔽（本体 C4-004，P2，假保护型）

- **文件**: 缺陷本体 `NopBeansAutoConfiguration.java:35-53` / `NopMybatisSessionFactoryCustomizer.java:30-33`
- **保护缺口**: 唯一间接保护 TestDeltaMyBatis @BeforeAll 预初始化 → CI 只走变体 B；标准路径变体 A 零覆盖且被制度化掩蔽（RP-3）。
- **并发场景关联**: refresh 期（ConfigurationClassPostProcessor/singleton 预实例化）严格早于 ApplicationStartedEvent 的平台初始化，两阶段由启动线程串行分隔但无门禁。
- **变异推演**: 加 isInitialized 门禁 fail-fast 后无测试变化（demo 测试已初始化、门禁放行——经核 CoreInitialization.java:118 兼容）。
- **建议测试形态**: 新增"不预初始化"的测试上下文 → 断言 registrar fail-fast 报"先 initialize"而非静默空发现；mybatis customizer 孪生同测。
- **测试载体**: 新增 `nop-spring/nop-spring-delta/src/test/.../TestRegistrationGate`（模块现无测试树）。
- **保护不变式**: 平台未初始化时注册回调不静默空返回（要么 fail-fast 要么显式告警），标准 SpringDemoMain 装配路径可观测。
- **失效面**: (a) NopBeansRegistrar；(b) NopMybatisSessionFactoryCustomizer（F3 孪生同修同测）。
- **严重程度**: P2（建议性，假保护型登记）
- **现状与工单映射**: 假保护；随 nop-host-C4-004 独立工单。

### [C10-011] 上传流校验失败路径泄漏零测试保护（本体 C6-001，P1）

- **文件**: 缺陷本体 `SpringFileService.java:44-60` / `QuarkusFileService.java:76-90`
- **保护缺口**: 两 file 模块测试树为空；fd 泄漏（外部可稳定触发）无回归钉。
- **并发场景关联**: 校验失败（checkFileExt/checkUploadAuth 等 4 道）发生在引擎入口同步段，此时开流线程已返回、流句柄仅存于 UploadRequestBean。
- **变异推演**: 校验前移 / UploadRequestBean AutoCloseable + whenComplete 关闭后，无测试变化。
- **建议测试形态**: close 计数 InputStream 包装 + stub 校验链抛 NopException → 断言 close 被调用（修复前红）；成功路径断言 saveFile finally 兜底不双关。
- **测试载体**: 新增 `TestUploadStreamClose`（spring-file + quarkus-file 测试树各一，F3 同测）；fd 计数形态对照 WI 4.3 TestFileDownloadSinkClose 先例。
- **保护不变式**: upload 入口返回（无论成败）后，controller 打开的流必被关闭（恰一次）。
- **失效面**: (a) Spring upload；(b) Quarkus uploadFileAsync；(c) 下载路径合规不涉；(d) 跨单元两构造点待验提示随工单。
- **严重程度**: P2（P1 本体的缺保护强制登记）
- **现状与工单映射**: 无测试；随 nop-host-C6-001 独立工单。

### [C10-012] Quarkus 只 start 不 stop 零测试保护（本体 C6-002，P2）

- **文件**: 缺陷本体 `nop-quarkus/nop-quarkus-core-starter/src/main/java/io/nop/quarkus/core/QuarkusIntegration.java:26-52`
- **保护缺口**: 零测试；生命周期对称性（Spring 有 onClose 配对、Quarkus 无）无结构测试。
- **并发场景关联**: 启动/停机单线程生命周期 × 跨实例残留影响运行期请求线程（热重载场景）。
- **变异推演**: 增加 stop/ShutdownEvent 观察者后，无测试变化。
- **建议测试形态**: 结构对称性测试——反射断言 QuarkusIntegration 存在 stop 配对且注册 ShutdownEvent 观察者（Spring 侧 @EventListener onClose 对照）。
- **测试载体**: 新增 `nop-quarkus/nop-quarkus-core-starter/src/test/.../TestQuarkusIntegrationLifecyclePairing.java`（模块现无测试树）。
- **保护不变式**: 两宿主集成层生命周期配对对称（有 start 必有可达 stop/destroy 路径）。
- **失效面**: (a) QuarkusIntegration.start；(b) Spring 对照；(d) ShutdownHook 累积伴随项不立测。
- **严重程度**: P2（建议性）
- **现状与工单映射**: 无测试；随 MA-04 工单（C6-002 成员）。

### [C10-013] destroy 无归属守卫零测试保护（本体 C6-003，P2）

- **文件**: 缺陷本体 `nop-spring/nop-spring-core-starter/src/main/java/io/nop/spring/core/autoconfig/NopSpringCoreAutoConfig.java:36-39`
- **保护缺口**: 零测试；多上下文拆毁恰是 spring-test 套件常态而测试基建自身是暴露面（TestDeltaMyBatis @AfterAll destroy 即触发形态）。
- **并发场景关联**: 上下文 A 关闭触发 destroy 清全局缓存/facade 回写宿主桥时，在役上下文 B 的请求线程正读这些全局态。
- **变异推演**: onClose 加归属守卫（BeanContainer 引用相等才 destroy）后，无测试变化。
- **建议测试形态**: 两个 StaticApplicationContext 模拟多上下文：A 关闭 → 断言在役 B 的 facade 与全局缓存完好（修复前红）；本上下文关闭 → 断言 destroy 正常执行。
- **测试载体**: 新增 `TestSpringCoreAutoConfigDestroyGuard`（spring-core-starter）。
- **保护不变式**: destroy 仅由最后一个使用者触发；非归属上下文的 ContextClosedEvent 不拆全局态。
- **失效面**: (a) NopSpringCoreAutoConfig.onClose；(c) destroy 自身幂等合规；(d) Quarkus 面归 C6-002。
- **严重程度**: P2（建议性）
- **现状与工单映射**: 无测试；随 MA-04 工单（C6-003 主）。

### [C10-014] startAsync 无超时无监听零测试保护（本体 C6-004，P2）

- **文件**: 缺陷本体 `ServletHttpServerContext.java:228-301,303-373`
- **保护缺口**: adapter 测试不覆盖 sendStreamingResponse 两变体（Publisher/writerFn）。
- **并发场景关联**: 容器异步超时管理线程 30s 到期直接完成响应 × 流式写线程后续 write 抛 ISE 二次噪声。
- **变异推演**: setTimeout(0) + AsyncListener.onTimeout 收口后，无测试变化。
- **建议测试形态**: stub AsyncContext 捕获 setTimeout/addListener/complete 调用 → 断言流式路径注册超时策略与 onTimeout 监听（修复前红）；幂等容错断言（容器已完成后 complete 抛 ISE 被吞）。
- **测试载体**: 扩展 `TestServletHttpServerContextAdapter`（新增 streaming 组用例）或独立 `TestServletStreamingLifecycle`。
- **保护不变式**: 长流式响应显式声明超时意图（setTimeout(0)）且容器超时事件有监听收口。
- **失效面**: (a) Publisher 变体；(b) writerFn 变体（F3 同测）；(d) Quarkus 孪生无 AsyncContext 机制豁免。
- **严重程度**: P2（建议性）
- **现状与工单映射**: 无测试；随 nop-host-C6-004 独立工单（吸收 CL-005b、搭车 C1-005）。

### [C10-015] 数据源 Gauge 旧绑定零测试保护（本体 C6-005，P2）

- **文件**: 缺陷本体 `nop-quarkus/nop-quarkus-core-starter/src/main/java/io/nop/quarkus/core/dao/AgroalDataSourceFactory.java:34-36,108-183`
- **保护缺口**: nop-quarkus-core-starter 零测试；重建场景（reinitialize/热更新）零覆盖。
- **并发场景关联**: 重初始化线程重建数据源 × micrometer 轮询线程读 Gauge lambda（闭包绑旧池）。
- **变异推演**: 判存先 remove / AtomicReference 重绑后，无测试变化。
- **建议测试形态**: stub metricsFactory 记录 register：同 name+tag 二次注册前断言 find+remove 被调用（修复前红）；或断言 gauge 闭包持可替换引用。
- **测试载体**: 新增 `nop-quarkus/nop-quarkus-core-starter/src/test/.../TestAgroalMetricsRebinding.java`。
- **保护不变式**: 数据源重建后同名 Gauge 必绑定新池 metrics（观测值随新池活跃度变化）。
- **失效面**: exportMetrics 17 个 Gauge 全同构（:112-182），一修全修、一测全盖。
- **严重程度**: P2（建议性）
- **现状与工单映射**: 无测试；随 nop-host-C6-005 独立工单。

### [C10-016] write(int) 吞字节零测试保护（本体 C6-006，P3）

- **文件**: 缺陷本体 `VertxHttpServerContext.java:425-440`
- **保护缺口**: ResponseOutputStream 零测试。
- **并发场景关联**: 无（正确性/契约缺陷，单线程即可钉死）。
- **变异推演**: write(int) 实现 appendByte 后，无测试变化（该缺陷本身即单字节路径，测试即修复镜像）。
- **建议测试形态**: 一行往返：write(0x41) → stub resp 收到 1 字节 'A'；数组重载回归断言。
- **测试载体**: 扩展 `TestVertxHttpServerContextAdapter`（新增 ResponseOutputStream 组用例）。
- **保护不变式**: OutputStream 契约完整——两 write 重载都不丢字节。
- **失效面**: (a) 本类两重载；(c) Spring 宿主原生流无同族。
- **严重程度**: P3（建议性）
- **现状与工单映射**: 无测试；随 nop-host-C6-006 独立工单。

### [C10-017] withRoutingContext 同步异常→future 悬挂零测试保护（本体 C6-007，P1）

- **文件**: 缺陷本体 `QuarkusExecutorHelper.java:55-63`；平台根因 `ContextTaskQueue.java:104-107,150-156`
- **保护缺口**: 宿主零测试；平台 TestContextTaskQueue 覆盖 flush 退出竞态但**不覆盖 task.run() 抛 Throwable 的吞异常通道**；TestFutureHelper 覆盖 bindResult 孤立语义不覆盖装配缺口——缺口面（task.get() 同步抛出→bindResult 失联）无任何测试到达。
- **并发场景关联**: 事件循环线程提交 × drain 线程执行 task，同步异常落 ContextTaskQueue catch-Throwable-仅记日志，返回 RESTEasy 的 future 永不完成。
- **变异推演**: host 层 try/catch → completeExceptionally 后，无测试变化。
- **建议测试形态**: stub IContext.runOnContext 内联回调（或另一线程 + latch）：supplier 抛出 → `assertThrows(ExecutionException.class, () -> future.get(1, SECONDS))`（修复前 TimedOut 红）。
- **测试载体**: 新增 `TestQuarkusExecutorHelperExceptionBinding`（quarkus-web）。
- **保护不变式**: withRoutingContext 返回的 future 在 task 任何同步异常下必完成（exceptionally），不产生永久悬挂。
- **失效面**: withRoutingContext 全部 7 调用点清单见 C6-007 (a)-(g)；平台吞异常通道用例归 2.3 CL-018 面不在本单元。
- **严重程度**: P2（P1 本体的缺保护强制登记）
- **现状与工单映射**: 无测试（邻近不达）；随 MA-02 工单（C6-007）。

### [C10-018] complete() 洗白截断响应零测试保护（本体 C6-008，P2）

- **文件**: 缺陷本体 `ServletHttpServerContext.java:292-297,346-352`
- **保护缺口**: 流式错误路径零测试。
- **并发场景关联**: publisher onError/写失败线程 × 容器异步完成管理——错误在传输层被洗白为正常完成。
- **变异推演**: 改调 asyncCtx.completeWithError(error) 后，无测试变化。
- **建议测试形态**: stub AsyncContext 区分记录 complete/completeWithError：onError → 断言 completeWithError(error) 被调用且 complete() 未被调用（修复前红）。
- **测试载体**: 随 C10-014 载体同文件新增错误路径组用例。
- **保护不变式**: 流式失败必经容器错误通报通道（completeWithError），错误信号对客户端协议可见。
- **失效面**: (a) completeWithError Publisher 变体；(b) fail writerFn 变体（F3 同测）；(d) Quarkus resp.reset() 合规参照。
- **严重程度**: P2（建议性）
- **现状与工单映射**: 无测试；随 MA-03 工单（C6-008）。

### [C10-019] sendResponse(InputStream) 释放契约零测试保护（本体 C6-009，P3）

- **文件**: 缺陷本体 `IHttpServerContext.java:92` + 两实现（Servlet :216-225 / Vertx :189-202）
- **保护缺口**: 契约零测试（当前在库调用面为零，潜伏缺陷）。
- **并发场景关联**: 无（契约缺陷）；未来异步链调用方获取-释放解耦时泄漏面放大。
- **变异推演**: 实现侧 try-with-resources 关闭 body 后，无测试变化。
- **建议测试形态**: close 计数 InputStream 传入 → 断言拷贝完成后流被关闭（两实现同测）；拷贝中途 IOException 路径断言仍关闭。
- **测试载体**: 扩展两宿主 adapter 测试各一契约用例。
- **保护不变式**: sendResponse(int, InputStream) 返回后 body 流必被实现侧关闭（契约 javadoc 化 + 行为钉死）。
- **失效面**: (a) Servlet 实现；(b) Vertx 实现（F3 孪生同测）；(e) 在库调用面为零。
- **严重程度**: P3（建议性）
- **现状与工单映射**: 无测试；随 nop-host-C6-009 独立工单（Protected Area plan-first 修复批）。

**登记统计**：19 条（C10-001～019）；强制登记（P1 本体）4 条：C10-001/006/011/017；建议性 15 条（P2 本体 13 + P3 本体 2，其中 C10-005/016/019 为 P3 弱建议）。判级口径：缺保护登记本体 P2（提示词口径），随 P3 本体的 3 条按 P3 弱建议登记。

## 七、系统性观察

- **S1 测试面与发现面倒挂（会红=0 的结构性成因）**：宿主 11 根有 10 个测试文件约 46 个测试方法，但全部是单线程功能契约测试（adapter 字段映射/cookie/zip 转发/lifecycle 序列）；并发、异步、资源三个失效轴在宿主测试树**不可表达**（零 latch/executor/future 断言原语）。这与 WI 4.2「全模块 17 测试文件零真并发测试」、WI 4.3「测试栈结构性单线程化」构成本 campaign 宿主侧的第三次同型确认——缺陷不是漏测，而是测试基建写不出会红的测试。修复 C10 登记时应优先沉淀平台 `TestContextTaskQueue` 的锁冻结确定性竞态先例为可复用测试设施，而非逐条手写一次性线程测试。
- **S2 唯一集成级保护制度化了缺陷前提（假保护的结构性成因）**：宿主模块自身零 @SpringBootTest/@QuarkusTest，唯一触及宿主装配行为的集成测试（nop-demo TestDeltaMyBatis）以 `@BeforeAll` 预初始化为前提——恰好踩在 C4-004 变体 B 上，使"必须预初始化"这一未声明契约在 CI 中恒真。同一测试的 `@AfterAll CoreInitialization.destroy()` 又是 C6-003 多上下文拆毁的标准触发形态：**测试基建同时是缺陷的掩蔽者和暴露者**。宿主集成测试的缺位使 C1-006/C4-003/C6-002/C6-003 这组"启动窗口/生命周期"发现整体失去回归面。
- **S3 测试替身宽于真实契约（断言承诺与断言内容分离）**：JDK proxy stub 的 `defaultFor(boolean)=false`/null 缺省使"真实环境会抛错的路径"（extractor 缺失、容器未初始化）在测试中静默不可达（RP-2），`testOnErrorDoesNotLeak` 的命名承诺（keepalive cancel）没有对应断言（RP-4），`testSendResponseRedirectAndIsResponseSent` 的断言消息字面钉死内部实现设计（RP-1）。宿主测试的三个 stub 形态——恒非 null 容器、恒成功发送会话、手工翻转的 committed 标志——都宽于 Jakarta/Vertx 容器真实契约；C10 登记的测试形态要求 stub 具备**抛错与并发检测能力**，替身收敛到真实契约是本单元测试修复的前置。

## 八、单轮终止声明

**C10 单轮执行，终止，不派第 2 轮。** 依据：(i) C10 为附录 B 规定的终批维度，输入为三维度 19 条复核保留缺陷的完备清单（R1+R2 已终止，无待补缺陷）；(ii) 变异推演 19/19 逐条落到具体测试文件/方法级证据（存在性经 find 全量复现，结论经读文件核实），矩阵闭合；(iii) 反向钉死 4 处、时序敏感审查、泄漏钩子与规模边界两专项均已完成并落登记；(iv) 本维度产出（登记 + 测试形态建议）的后续动作属修复立项域（随本体工单），继续挖掘无新缺陷面。缺保护登记 19 条随本体映射见 merge-adjudication.md §九。
