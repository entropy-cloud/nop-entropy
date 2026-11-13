# C1 共享可变状态与可见性 — nop-host（WI 4.4）

- 审计单元：nop-host（宿主集成层，合并批 C1+C4+C6 三维度中的 C1 部分）
- 审计日期：2026-10-21（初审，盲审口径）
- 审读范围：11 源码根 53 文件 / 4,344 行，逐文件审读完成。

## 审计范围与口径

**合并批三维度覆盖声明**：本目录为 nop-host（Spring/Quarkus 宿主集成层）深度审计的合并批次，C1（共享可变状态）、C4（线程池与执行器治理）、C6（资源生命周期）三维度由同一初审员在同一轮逐文件审读中并行完成，发现按维度分文件归档，跨维度同根因的交叉引用已在各条目中显式标注。

**原始并发面极薄预校准声明**：基线 `_tmp/cra-baseline-nop-host.txt` 与逐文件审读一致确认——11 源码根内**零自建线程/线程池/定时器**（C4a/C4b 基线仅命中 `@EventListener`/`@PostConstruct`/`@PreDestroy` 生命周期回调），C1 面原语仅 6 文件 26 行（static 字段、1 个 ThreadLocal、2 处 synchronized 孪生方法、1 个 CHM、2 处 AtomicBoolean 复合态）。本审计严格按该预校准执行：**不凑数扩面**，对无共享可变状态的纯常量/无状态委托类（5 个 Constants 接口、NopSpringBeanContainer、NopQuarkusBeanContainer、MarkerInterfaceAnnotation、UnauthorizedException、SpringWebHelper/SpringMvcHelper 等静态工具）仅作清单级排除，不虚构发现。

**owner doc 覆盖薄声明**：nop-spring/nop-quarkus 宿主模块无专门 owner doc 覆盖并发契约；本审计的行为契约以源码 javadoc + Spring Boot（`@EventListener`/`OncePerRequestFilter`/MVC 异步语义）+ Quarkus/Vert.x（Filters/`@Observes`/RoutingContext 事件循环语义）+ Micrometer（Gauge.register 幂等语义）+ Jakarta WebSocket（basicRemote 非线程安全）框架语义为准。

### C1 共享可变状态清单表（载体 × 写者 × 读者 × 同步机制 × 判定）

| 载体 | 位置 | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|---|
| `webClients` CHM | SpringWebProxy.java:34 | computeIfAbsent(:160) | 每请求 | CHM 原子 | 安全发布，无缺陷（配置读锁内 I/O 见 CL-001） |
| `WebClientHolder.authorization/cookie` | WebClientHolder.java:8-9 | Reactor 线程 refreshAccessToken(:149,:154) | 请求线程 callRest/callWeb(:81-86,:107-112) | **无** | **C1-001** |
| `filters` List 缓存 | SpringHttpServerFilterConfiguration.java:39 / HttpServerFilterRegistrar.java:44 | synchronized getFilters 首次请求 | 每请求（锁内快照拷贝 :53-56） | synchronized + 防御性拷贝 | 快照语义正确；启动窗口残面见 C1-006 |
| `sysFilter/appFilter` | SpringHttpServerFilterConfiguration.java:59-60 | Spring 容器 @Bean 单次调用 | 无其他读者 | 容器单线程初始化 | 无缺陷 |
| `dialect` | NopSpringTransactionFactory.java:41 | 事务线程首次 getDialectForQuerySpace(:50-52) | 后续事务线程 | **无** | **C1-004** |
| `callbacks` 数组 | GraphQLToolCallbackProvider.java:25 | 首次 getToolCallbacks(:29-31) | MCP 工具列举请求 | **无** | **C1-004**（同族） |
| `g_serverContext` ThreadLocal | QuarkusExecutorHelper.java:28 | withRoutingContext :45 set / :65 remove（调用线程） | getHttpServerContext(:31)（任意线程） | set/remove 同线程配对；**不跨线程传播** | **C1-002** |
| `ServletHttpServerContext.context/characterEncoding` | ServletHttpServerContext.java:45,47 | 请求线程 setContext(:428) | executeBlocking/异步续延线程 getContext(:423) | **无**（plain field） | **C1-005**（理论风险） |
| `VertxHttpServerContext.responseSent` | VertxHttpServerContext.java:49 | 事件循环线程 send*(:177,:183,:190,:207,:273) | 跨线程读 isResponseSent(:346) | **无** | **C1-005**（同族） |
| `SpringTransaction.txn` | NopSpringTransactionFactory.java:136 | 事务绑定线程 doOpen/doClose | 同线程 | Spring 事务线程绑定契约 | 单线程契约内，见 C1-005 失效面备注 |
| `AtomicBoolean completed` | ServletHttpServerContext.java:239,315 / VertxHttpServerContext.java:219,285 | 流式回调线程 | 同上 | CAS | 复合态守卫正确（幂等 complete/fail），无缺陷 |
| `routingContext.put(KEY, ctx)` | QuarkusExecutorHelper.java:38 / HttpServerFilterRegistrar.java:81 | 事件循环线程 | 同请求事件循环 | Vertx 单请求单事件循环 | 无缺陷 |
| `CoreInitialization` 静态态 | （平台核心，本层仅调用） | onStart/onClose 主线程 | 健康检查线程读 volatile 标志 | synchronized + volatile | 调用面判定见 C6 文件 C6-003 |

排除项（本领域误报校准）：无状态单例 bean 被并发调用（QuarkusGraphQLWebService 等）；CHM/synchronized/AtomicBoolean 原语自身使用；启动期粗粒度 synchronized；不可变 `static final Set IGNORE_HEADERS`。

## 发现

### [C1-001] WebClientHolder 凭据对（authorization+cookie）跨线程无同步读写，存在陈旧可见性与撕裂写入

- **文件**: `nop-spring/nop-spring-proxy/src/main/java/io/nop/spring/proxy/WebClientHolder.java:8-9`；`nop-spring/nop-spring-proxy/src/main/java/io/nop/spring/proxy/SpringWebProxy.java:129-157,41-73`
- **证据片段**:
  ```java
  // WebClientHolder.java:6-11 —— 普通可变字段，无 volatile/锁
  public class WebClientHolder {
      private WebClient webClient;
      private WebClientConfig config;
      private String authorization;
      private String cookie;
  // SpringWebProxy.java:146-154 —— Reactor 回调线程写
  .doOnNext(response -> {
      String cookie = response.getHeaders().getFirst("set-cookie");
      cookie = HttpCookieHelper.updateCookie(webClient.getCookie(), cookie);
      webClient.setCookie(cookie);                    // 写 1
      ...
      webClient.setAuthorization("Bearer " + token);  // 写 2（与写 1 非原子）
  })
  ```
- **严重程度**: P1
- **并发场景**: WebFlux/MVC 请求工作线程（读 `getAuthorization`/`getCookie` 构造下游请求头，:81-86/:107-112）与 Reactor Netty 事件循环线程（`refreshAccessToken` 的 `doOnNext` 回调，写凭据）。WebClient 刷新响应可能落在与原请求不同的 event-loop 连接上，两线程间无任何 happens-before。
- **触发时序**: T1（请求 A，worker/event-loop 线程）：`restProxy` 收到 401 → `UnauthorizedException` → `retryWhen.doBeforeRetryAsync`（:66-68）发起 `refreshAccessToken`；T2（Reactor 线程）：刷新完成，`setCookie(cookie#2)` 已执行、`setAuthorization("Bearer "+token#2)` 尚未执行；T3（请求 B 或 A 的重订阅，另一线程）：进入 `callRest` :81-86 读 `getCookie()`=cookie#2、`getAuthorization()`=旧 token#1（无 volatile，读可无限期滞后）→ **撕裂凭据对**发出，下游再 401；重试链 `Retry.max(2)` 耗尽。交叠后果：刷新成功但重试仍携陈旧 token，认证循环失败；并发多请求同时 401 时多个 `refreshAccessToken` 交叉写，最后写者胜出且 cookie/token 代际错配可长期驻留。
- **现状**: 凭据状态是跨线程共享可变对，两个写与全部读均无同步/volatile，复合更新（cookie+token）无原子性。
- **风险**: 网关型部署（`/rest/{provider}`、`/sse/{provider}`）在 token 过期窗口内的所有并发请求可能持续 401；撕裂对一旦驻留需等到下一次成功刷新才被整体覆盖，期间用户请求全部失败。
- **建议**: 将 authorization/cookie 合并为单个不可变 `volatile Credentials` 引用（一次写整体替换），或改用 `AtomicReference<Credentials>` CAS 更新；刷新去重可复用平台 `AsyncJoinType`/按 provider 加 `IResourceLockManager` 单飞。持有者生命周期与 CHM 发布本身是安全的（computeIfAbsent 安全发布），无需改动。
- **失效面枚举**: 同族兄弟全量清扫——(a) `sseStream` :41-56 与 `restProxy` :58-73 两条入口共用同一 holder：均已含在触发链；(b) `refreshAccessToken` :129-157 是唯一写点：已含；(c) `webClients` CHM 本身：安全；(d) `WebClientConfig` 仅为启动期加载的只读数据（`newWebClient` :163-177 填充后经 CHM 发布）：安全；(e) Quarkus 侧无对应代理模块：无兄弟；(f) 平台内对照——nop-graphql `JsonRpcWebSocketHandler.authHeaders/boundUserId`（JsonRpcWebSocketHandler.java:60-62）声明为 volatile，证明同族状态在平台别处按 volatile 惯例处理，本站点偏离惯例。
- **信心水平**: 很可能
- **误报排除**: 非"看到共享字段就报线程不安全"——已写出具体 T1/T2/T3 交错与后果链（陈旧 token 重发 + 撕裂凭据对），且写读分属不同线程池/事件循环，无隐含 hb；非"有界声明内的合理粗锁"——此处完全没有同步。
- **复核状态**: 已保留｜P1 维持。回码验证：WebClientHolder.java:6-11 四字段无一 volatile/synchronized；SpringWebProxy 写点 doOnNext :146-154（cookie 先、authorization 后，非原子）与读点 :81-86/:107-112 分属 Reactor/请求线程——证据链逐行吻合。台账查重：cra-index P1 表 nop-spring 零登记，无同案。现状：新发现未修复。

### [C1-002] g_serverContext ThreadLocal 只在调用线程 set/remove，runOnContext 分支在池线程执行 task 时上下文丢失，getHeaders/getParams 静默降级为空

- **文件**: `nop-quarkus/nop-quarkus-web/src/main/java/io/nop/quarkus/web/utils/QuarkusExecutorHelper.java:43-67`；`nop-quarkus/nop-quarkus-web/src/main/java/io/nop/quarkus/web/service/QuarkusGraphQLWebService.java:108-127`
- **证据片段**:
  ```java
  // QuarkusExecutorHelper.java:44-66
  VertxHttpServerContext ctx = makeServerContext(routingContext);
  g_serverContext.set(ctx);                 // 仅设置在当前（事件循环）线程
  try {
      IContext context = ctx.getContext();
      if (context == null)
          return task.get();                // 分支 1：内联执行，TL 可见
      CompletableFuture<T> future = new CompletableFuture<>();
      context.runOnContext(() -> {          // 分支 2：TaskQueue+StandardThreadPoolExecutor 池线程执行
          ...
          FutureHelper.bindResult(task.get(), future);  // 此处 g_serverContext 未设置
      ...
  } finally {
      g_serverContext.remove();
  }
  // QuarkusGraphQLWebService.java:110-113 —— 消费侧静默降级
  IHttpServerContext sc = QuarkusExecutorHelper.getHttpServerContext();
  if (sc == null)
      return Collections.emptyMap();
  ```
- **严重程度**: P1
- **并发场景**: Vert.x 事件循环线程（`withRoutingContext` 调用方，RESTEasy Reactive 端点）与 nop IContext 任务队列的 drain 线程（`BaseContext.runOnContext` → `TaskQueue`/`StandardThreadPoolExecutor` 池线程，BaseContext.java:228-243）。
- **触发时序**: T1（事件循环线程）：认证后的请求进入 `withRoutingContext`，`ctx.getContext() != null`（认证 filter 已绑定 context）→ 走 `context.runOnContext` 分支；T2（池线程）：TaskQueue drain 执行回调 → `task.get()` → `runGraphQL` → `getHeaders()`/`getParams()` → `g_serverContext.get()` 在该线程从未被 set → null → **返回 emptyMap/emptyMap，无任何日志**；交叠后果：已认证请求的请求头（含 clientAddr 提取 `QuarkusGraphQLWebService.java:117-126`、自定义 header）与查询参数在引擎侧全部为空。对照：匿名请求（context==null）走内联分支，同一代码却能取到头/参数——**同一调用点的结果取决于 TaskQueue 当时是否已挂接 drain 线程，语义非确定**。
- **现状**: ThreadLocal 的 set/remove 严格配对（无池化线程泄漏），但**跨线程传播缺失**：runOnContext 分支既不重新 `set` 也不经参数传递 `ctx`，消费端用 null-check 吞掉（emptyMap 降级）而非 fail-fast。
- **风险**: Quarkus 宿主上所有认证请求的 header 依赖型功能（IP 审计、租户 header、下游 header 透传）静默失效；降级无声，排障只能靠业务症状反推。
- **建议**: 在 runOnContext 回调内以 `g_serverContext.set(ctx)` + try/finally `remove()` 包裹 `task.get()`（回调线程上重新配对）；或将 `ctx` 显式并入 task 闭包、消费端改走 `makeServerContext(routingContext)` 数据通道，删除 ThreadLocal 依赖；短期至少把 `sc == null` 分支改为 LOG.warn 一次性告警。
- **失效面枚举**: 全量调用点清扫——`getHttpServerContext()` 共 4 个消费点：(a) QuarkusGraphQLWebService.getParams:110 / getHeaders:118（本发现主体，null→emptyMap 静默降级）；(b) QuarkusJsonRpcWebService：经 `runJsonRpc(body, getHeaders())`（:28）在 task 内同步取头——同属失效面；(c) JsonRpcWebSocketEndpoint.java:114（WebSocket onOpen 路径，g_serverContext 从未为 WS 会话设置，恒 null，已由 :115 null-check 防御+外层 catch 吞掉——降级但非本条新增风险）；(d) QuarkusFileService 上传/下载（:56,:167）：不读该 TL，无影响。Spring 宿主同族对照：`SpringGraphQLWebService.getParams/getHeaders` 用 `RequestContextHolder` ThreadLocal，但 `runGraphQL`（GraphQLWebService.java:100-108）在**发起异步前于请求线程同步调用** getHeaders，Spring 侧无此缺陷——证明该消费时序约束在本仓库是已知设计前提，quarkus 分支破坏了它。
- **信心水平**: 很可能（`TaskQueue` 挂接 `StandardThreadPoolExecutor` 后 enqueue-success 路径确定在池线程执行；未实测 drain 线程是否恰为同一 Vert.x 事件循环线程——按平台实现为独立线程池）
- **误报排除**: 不是"ThreadLocal 在池化线程未 remove 泄漏"型误报——set/remove 配对正确；本条缺陷是**传播缺失 + 静默降级 + 分支间语义不一致**，有具体 T1/T2 与可观察后果（认证请求头为空），非纯理论。
- **复核状态**: 已降级（P1→P2）｜缺陷本体保留（TL 传播缺失 + 消费端 emptyMap 静默降级 + 匿名/认证请求分支语义不一致均回码属实：QuarkusExecutorHelper.java:44-66、QuarkusGraphQLWebService.java:108-127）。降档事由（复核订正）：R1 触发链的执行载体归因有误——BaseContext 用的是 api-core `ContextTaskQueue`（ConcurrentLinkedDeque + processingThread/syncing，**无 StandardThreadPoolExecutor 池**），runOnContext 仅在"同 context 已有 syncGet 进行中（syncing>0）"时才把任务移交他线程，否则调用线程自 drain（此时 TL 尚未 remove，缺陷不触发）；"多线程 drain 同一请求上下文"这一前提在 11 根内无已证调用链（syncGet 消费面在平台 graphql 引擎侧，未在本单元落地）。按"时序未具体化到在库可复现调用链"口径降 P2，保留传播缺失的修复义务。

### [C1-003] WebSocket 会话封装用 basicRemote 同步发送且无串行化，keepalive 定时线程与订阅线程并发写同一 socket

- **文件**: `nop-spring/nop-spring-web-starter/src/main/java/io/nop/spring/web/ws/SpringWebSocketSession.java:24-25`；`nop-quarkus/nop-quarkus-web/src/main/java/io/nop/quarkus/web/ws/JakartaWebSocketSession.java:24-25`
- **证据片段**:
  ```java
  // 两个宿主的 IWebSocketSession 实现完全同构
  @Override
  public void sendMessage(String message) throws IOException {
      session.getBasicRemote().sendText(message);   // Jakarta 规范：basicRemote 非线程安全
  }
  ```
- **严重程度**: P2
- **并发场景**: 三类写线程汇聚到同一 `Session`：(a) `GlobalExecutors.globalTimer()` 线程——`JsonRpcWebSocketHandler` 构造时按每连接 10s 周期调度 `sendKeepAlive`（JsonRpcWebSocketHandler.java:82-83，由两个宿主端点 onOpen 实例化：Spring :71、Quarkus :67）；(b) GraphQL 订阅数据线程——`SubscriptionSubscriber.onNext` → `session.sendMessage`（JsonRpcWebSocketHandler.java:464）；(c) WebSocket 容器消息线程——onMessage 的 pong/error 应答。
- **触发时序**: T1（globalTimer 线程）：10s 心跳到期，进入 `basicRemote.sendText(ping)`，帧写入中；T2（订阅线程）：同一时刻 `onNext` 到达，进入 `basicRemote.sendText(data)`；交叠后果：Jakarta 容器（Tomcat/Quarkus 内嵌 Undertow）对并发 basic 写抛 `IllegalStateException`（TEXT_FULL_WRITING）或产生帧交错——两个 catch 均仅 LOG.warn（JsonRpcWebSocketHandler.java:359-362,466-468），**数据帧被静默丢弃**，订阅继续 `request(1)`，客户端缺失结果分片且无重传。
- **现状**: 会话封装是最薄的跨线程共享可变资源门面，选择了非线程安全的同步发送 API 且未加任何串行化（无锁、无单写线程队列）。
- **风险**: 活跃订阅 + 心跳常态并发，繁忙订阅下丢帧概率随负载上升；表现为偶发"订阅数据缺块"，难复现。
- **建议**: 在封装内加单飞串行化：`synchronized` 简单互斥（发送均为短操作），或切换 `getAsyncRemote()` + 平台 `IBlockingSource` 式发送队列；keepalive 与数据帧可统一经同一发送队列（复用 `io.nop.commons.concurrent.IBlockingQueue`）。
- **失效面枚举**: (a) Spring 宿主 `SpringWebSocketSession.sendMessage`：本发现主体；(b) Quarkus 宿主 `JakartaWebSocketSession.sendMessage`：完全同构，**F3 孪生同修**；(c) 两个 `close()` 实现（:29-35）：同步 close 与并发 send 交叠由容器处理，风险低，纳入同族一并串行化；(d) 写线程清单：keepalive/onNext/onMessage/onComplete（:489）共 4 类调用点全部经同一无同步门面；(e) nop-graphql 侧 handler 无任何发送串行化逻辑（已核实 JsonRpcWebSocketHandler 全文），修复必须落在会话门面层。
- **信心水平**: 很可能（容器对并发 basicRemote 的失败模式为抛 ISE 或排队，二者皆非正确串行语义）
- **误报排除**: 非"原语使用本身"误报——这里的问题不是 synchronized/CHM 用法，而是**非线程安全对象跨 3+ 线程暴露且写出具体交错**；也不是理论风险：心跳每 10s 必然与任何活跃订阅构成竞争窗口。
- **复核状态**: 已保留｜P2 维持。回码验证：SpringWebSocketSession/JakartaWebSocketSession :24-25 basicRemote.sendText 属实；调度点 JsonRpcWebSocketHandler.java:82-83（构造器内 scheduleWithFixedDelay 10s）与取消点 :114-116/:126-128 实测吻合；写线程清单（keepalive/onNext/onMessage 应答/onComplete）成立。现状：新发现未修复。

### [C1-004] 单例懒初始化"检查-然后-放入"无同步族：NopSpringTransactionFactory.dialect 与 GraphQLToolCallbackProvider.callbacks

- **文件**: `nop-spring/nop-spring-core-starter/src/main/java/io/nop/spring/core/txn/NopSpringTransactionFactory.java:49-54`；`nop-ai/nop-spring-mcp-server/src/main/java/io/nop/spring/mcp/server/GraphQLToolCallbackProvider.java:28-32`
- **证据片段**:
  ```java
  // NopSpringTransactionFactory.java:48-54 —— singleton @Component 实例字段
  @Override
  public IDialect getDialectForQuerySpace(String querySpace) {
      if (dialect == null) {
          this.dialect = DialectManager.instance().getDialectForDataSource(dataSource);
      }
      return dialect;
  }
  // GraphQLToolCallbackProvider.java:28-32 —— singleton @Bean
  public ToolCallback[] getToolCallbacks() {
      if (callbacks == null)
          callbacks = buildCallbacks().toArray(new ToolCallback[0]);
      return callbacks;
  }
  ```
- **严重程度**: P2
- **并发场景**: 请求工作线程池并发首次调用（dialect：并发事务首次解析方言；callbacks：MCP server 并发工具列举/调用）。
- **触发时序**: T1：线程 A 读 `dialect == null` 进入赋值；T2：线程 B 同样读到 null，重复执行 `DialectManager.instance().getDialectForDataSource(dataSource)`；T3：两次赋值均发生，后写覆盖前写。后果分两层：(i) 重复计算（DialectManager 内部有缓存，代价小）；(ii) `dialect`/`callbacks` 均为非 volatile 普通 field，**不安全发布**——读者理论上可观察非 null 引用但对象字段未完全构造的状态（callbacks 数组为 `toArray` 产物，元素为外部构造的 ToolCallback）。
- **现状**: 两处均为经典 benign-racy 懒初始化；值层面幂等（DialectManager 对同数据源返回等价实例；callbacks 重建内容相同），无复合状态撕裂，但违反本仓库"检查-然后-放入缓存路径是高频竞态点"的审计口径。
- **风险**: 实际危害低：最坏为重复构建与理论可见性窗口；不会导致数据损坏或语义违约。
- **建议**: 按仓库惯例最小修复：字段加 `volatile`（成本可忽略）；或改 holder/双检；MCP 侧 callbacks 可改为构造期/buildCallbacks 急切初始化（bean 创建为单线程）。
- **失效面枚举**: 11 根内全部"if (x == null) x = ..."懒初始化已穷举——(a) `dialect`（本条）；(b) `callbacks`（本条）；(c) `SpringHttpServerFilterConfiguration.filters` / `HttpServerFilterRegistrar.filters`：**已 synchronized 守卫**，合规；(d) `sysFilter/appFilter`：@Bean 方法由容器单线程单次调用，无竞态；(e) `SpringWebProxy.webClients`：computeIfAbsent 原子，合规；(f) `SpringWebProxy.configDir`：@Value 容器注入单线程，合规；(g) `NopQuarkusBeanContainer`/`NopSpringBeanContainer`：无可变实例字段（id 为 spring 侧可设字段，仅启动期 setter 注入），合规。**除 (a)(b) 外全族合规**。
- **信心水平**: 确定（竞态存在性确定；实际后果轻微）
- **误报排除**: 不按 P0/P1 报——不满足"数据损坏/语义违约"判级；也不豁免不报——属 C1 步骤 4 明确的检查-然后-行动盘点项，且为 P2 判级标准形态（可写不出危害性时序，但重复计算/不安全发布客观存在）。
- **复核状态**: 已保留｜P2 维持。回码验证：NopSpringTransactionFactory.java:50-54 与 GraphQLToolCallbackProvider.java:29-31 检查-然后-放入形态属实，均无 volatile；失效面 (a)-(g) 穷举经 R2 独立 grep 复现一致（懒初始化全族仅此两处未守卫）。现状：新发现未修复。

### [C1-005] 服务器上下文实例字段跨线程读无 volatile 族：ServletHttpServerContext.context、VertxHttpServerContext.responseSent、characterEncoding

- **文件**: `nop-spring/nop-spring-web-starter/src/main/java/io/nop/spring/web/filter/ServletHttpServerContext.java:45-47,423-430`；`nop-quarkus/nop-quarkus-web/src/main/java/io/nop/quarkus/web/filter/VertxHttpServerContext.java:49-53,346-348,416-423`
- **证据片段**:
  ```java
  // ServletHttpServerContext.java:45-47
  private IContext context;          // 请求线程 set，异步线程 get
  private String characterEncoding;
  // VertxHttpServerContext.java:49 + :346-348
  private boolean responseSent;      // 事件循环写；executeBlocking 后的池线程可读
  ...
  public boolean isResponseSent() { return responseSent; }
  ```
- **严重程度**: P2（理论风险，未具体化到危害时序）
- **并发场景**: 请求线程/事件循环线程（写：`setContext`、各 `send*` 设 `responseSent=true`）与 `executeBlocking` 分派的池线程、异步续延线程（读：`getContext()`/`isResponseSent()`，例如 filter 链在 `HttpServerHelper.runWithFilters` 异步段中检查响应是否已发送）。
- **触发时序**: 无法写出确定交错——主执行流中读写均收敛于同一请求线程/事件循环（Vertx 单请求单事件循环、Servlet filter 链同步段），跨线程读仅发生在业务方于 executeBlocking 任务内主动访问 ctx 的场景，当前 11 根内无此调用实例。理论交错：T1 事件循环线程 `sendResponse` 置 `responseSent=true`；T2 池线程经 executeBlocking 读 `isResponseSent()` → 可能读到 false。后果：误判后重复发送响应（Vertx 二次 send 抛错）。
- **现状**: plain field 承载跨线程可见性契约；写点密集但全部在单线程归属内。
- **风险**: 当前无实际触发路径；作为公共 `IHttpServerContext` 实现被宿主外代码（如 nop-gateway 的 GatewayHttpFilter）异步访问时暴露面扩大。
- **建议**: `context`/`responseSent` 加 `volatile`（每请求单实例，写频低，成本可忽略）；或 javadoc 显式声明"仅限请求线程访问"契约。
- **失效面枚举**: (a) `ServletHttpServerContext.context`（本条）；(b) `ServletHttpServerContext.characterEncoding` :47,:392-402（仅请求线程写读，无跨线程点——列作同族伴随项）；(c) `VertxHttpServerContext.responseSent`（本条）；(d) `VertxHttpServerContext.context/characterEncoding` :51-53（同 a/b 形态）；(e) `SpringTransaction.txn` :136（Spring 事务线程绑定契约内单线程访问，豁免但登记在案）；(f) `WebClientHolder` 对：独立成 C1-001，不并入本族（有真实触发链）。
- **信心水平**: 有趣的猜测（风险真实存在但无在库触发路径）
- **误报排除**: 主动按口径降级标注"理论风险，未具体化"并压 P2，而非按字段即报；也未因"无触发路径"漏盘——保留为公共契约层风险登记。
- **复核状态**: 已保留｜P2 维持（理论风险标注成立）。回码验证：ServletHttpServerContext.java:45-47（context/characterEncoding plain field）与 VertxHttpServerContext.java:49（responseSent plain boolean）+ :346-348 getter 属实；servlet 侧 isResponseSent 实测为 `response.isCommitted()` 委托（:376-378），R1 清单表 grouping 准确。补充登记：两宿主 isResponseSent 语义不对称（手工标志 vs 容器委托）已立 CL-008。现状：新发现未修复。

### [C1-006] getFilters 同构孪生站点：synchronized 惰性缓存把"启动窗口快照"永久固化，首请求早于平台初始化时异常或空链固化

- **文件**: `nop-spring/nop-spring-web-starter/src/main/java/io/nop/spring/web/filter/SpringHttpServerFilterConfiguration.java:47-57`；`nop-quarkus/nop-quarkus-web/src/main/java/io/nop/quarkus/web/filter/HttpServerFilterRegistrar.java:46-57`
- **证据片段**:
  ```java
  public synchronized List<IHttpServerFilter> getFilters(boolean sys) {
      if (filters == null) {
          filters = new ArrayList<>(
              BeanContainer.instance().getBeansOfType(IHttpServerFilter.class).values());
          Collections.sort(filters, OrderedComparator.instance());
      }
      return filters.stream().filter(...)   // 锁内快照拷贝，返回副本
              .collect(Collectors.toList());
  }
  ```
- **严重程度**: P2
- **并发场景**: 宿主启动期到达的首批请求线程（Spring：Tomcat 在 `onRefresh` 阶段即开始监听端口，早于 `ApplicationStartedEvent`→`NopSpringCoreAutoConfig.onStart` 注册 BeanContainer；Quarkus：HTTP server 先于 `StartupEvent`→`QuarkusIntegration.start`/`NopApplication.run` 可接受连接）与后续正常请求线程。
- **触发时序**: 变体 A（两宿主，确定）：T1 启动窗口请求进入 `doFilterInternal`/vertx filter lambda :92/:73 → `getFilters` → `BeanContainer.instance()`（BeanContainer.java:28-32）在 provider 未注册时**抛 ERR_IOC_BEAN_CONTAINER_NOT_INITIALIZED** → `filters` 字段保持 null（未固化）→ 该请求 500，启动完成后自愈。变体 B（Quarkus，条件化）：T1 若首请求落在 `QuarkusIntegration.start()` 注册容器**之后**、`NopApplication.run()` 装载 nop filter bean **之前**，且 IHttpServerFilter 实现为运行期注册（4 个实现类 AuthHttpServerFilter/GatewayHttpFilter/ContextHttpServerFilter/CoreInitializationGuardFilter 均无 CDI/Spring 注解，依赖 nop IoC 装载）→ `getBeansOfType` 返回空 → **空列表永久固化**，此后每个请求 `serverFilters.isEmpty()` 直通，认证 filter 整链旁路直至重启。
- **现状**: synchronized + 锁内快照拷贝的并发语义本身正确（判定：**过滤链快照无修改竞态**——`filters` 初始化后从不变更，返回副本可无锁迭代）；缺陷在**缓存生命周期与平台初始化序无对齐**，首次求值结果被永久固化。
- **风险**: 变体 A 为启动窗口 500（瞬态，可接受）；变体 B 为持久安全旁路（认证/限流 filter 失效），一旦发生无自愈路径。
- **建议**: getFilters 增加初始化门禁：`if (!CoreInitialization.isInitialized()) throw ...`（或返回 null 让调用方走 `rc.next()` 直通+warn），并在初始化完成后清空 `filters` 缓存重算一次；或把快照求值移到 `NopApplication.run` 完成后的就绪钩子。
- **失效面枚举**: (a) Spring `SpringHttpServerFilterConfiguration.getFilters`（本条，变体 A 主体）；(b) Quarkus `HttpServerFilterRegistrar.getFilters`（本条，A+B 双变体，F3 孪生同修）；(c) 两个注册点 `registerSysFilter/registerAppFilter`（:63-74）与 `setupSysFilter/setupAppFilter`（:59-65）：仅注册 lambda/Filter 实例，不求值快照，无此缺陷；(d) 每请求快照拷贝语义（:53-56/:53-56）：合规；(e) 平台侧已有 `CoreInitializationGuardFilter`（nop-http-server-filters）专门防初始化窗口，但它运行在 getFilters 求值**之后**，无法防护本缺陷——佐证该窗口是平台已知问题类。
- **信心水平**: 变体 A 确定 / 变体 B 很可能（取决于 quarkus 扩展是否在构建期为 nop bean 生成 CDI 合成 bean，未逐证）
- **误报排除**: 未把 synchronized 孪生结构本身当缺陷（明确判定合规）；报告的是初始化时序交叠 + 永久缓存的具体后果，两条变体均有明确触发链与证据行。
- **复核状态**: 已保留 + 判级修正（P2→P1）｜复核补全变体 B 机制并双宿主坐实：平台侧 `IocCoreInitializer.initialize`（nop-ioc/initialize/IocCoreInitializer.java:41-49）在 NopApplication.run 内以 `BeanContainer.registerInstance(appContainer)` 覆盖宿主桥（parentContainer 兜底）——危险窗口为 [宿主桥注册（Spring onStart / QuarkusIntegration.start）.. IocCoreInitializer 重注册]，横跨 NopApplication.run 全程（秒级）。窗口内 `getBeansOfType(IHttpServerFilter)` 落在 Spring context/CDI 上，而**全部 IHttpServerFilter 实现均为 nop-IoC bean**（AuthHttpServerFilter=auth-service.beans.xml:242、ContextHttpServerFilter/CoreInitializationGuardFilter=biz-defaults.beans.xml:120-130、GatewayHttpFilter 同类；全仓唯一 spring-*.beans.xml 为 nopBaseMapper，无 CDI 形态；nop-quarkus 无 deployment/extension 模块、无 SyntheticBeanBuild——R1"未逐证"点已逐证）→ **变体 B 双宿主成立**：任一启动窗口请求使空链永久固化，认证链（含 AuthHttpServerFilter）静默旁路直至重启，不可自愈。R1 变体 A 订正：抛 ERR_IOC_BEAN_CONTAINER_NOT_INITIALIZED 的 500 自愈仅适用于 onStart 注册桥之前的更早窗口。按"主请求面安全属性持久失效 + 确定触发链"升 P1。现状：新发现未修复。

## 维度集外线索（overlay C2/C3/C5/C7/C8/C9）

### [nop-host-CL-001] computeIfAbsent 内执行文件 I/O + YAML 解析；未知 provider 无负缓存反复落盘（C2 锁内 I/O / C9 热路径浪费）
- 站点：`nop-spring/nop-spring-proxy/src/main/java/io/nop/spring/proxy/SpringWebProxy.java:159-183`。`getWebClient` 的 `computeIfAbsent(provider, this::newWebClient)` 在 CHM bin 锁内执行 `loadConfig`（磁盘读 + JsonTool YAML 反序列化）；对不存在的 provider（URL 路径变量，键空间用户可控）每次请求重复读盘+解析，无负缓存。C2 关注点：持锁做重 I/O；C9 关注点：每请求可量化的重复 I/O。建议：配置加载移出 computeIfAbsent，负结果缓存 + 平台 ICache（TTL）。
- 现状标注：新线索，已确认（复核回码属实；台账查重无同案——cra-index 对 SpringWebProxy/Zip 过滤器零登记）。

### [nop-host-CL-002] 静态资源每请求 ClassPathResource.exists() 类加载器探测（C9）
- 站点：`nop-spring/nop-spring-web-starter/.../ZipContentEncodingFilter.java:53-76,86-88`；`nop-quarkus/nop-quarkus-web/.../ZipContentEncodingFilterRegistrar.java:44-63`。对每个 .js/.html/.css/.json 及 index 请求做 classpath 资源存在性探测（磁盘/JAR 打开），无结果缓存。量化口径：每静态请求 1 次 `getResource(...).exists()`（部分路径 2 次）。建议：启动期构建"存在 gz 变体的路径集"缓存。
- 现状标注：新线索，已确认（复核回码属实；无同案）。

### [nop-host-CL-003] getRequestBody 全量物化请求体为 String，无长度上界（C5）
- 站点：`nop-spring/nop-spring-web-starter/.../ServletHttpServerContext.java:405-415`（`IoHelper.readText(request.getInputStream(), UTF8)` 无界读入）；对照 `VertxHttpServerContext.java:394-408`（`body().toString(UTF8)`，受 vertx BodyLimit 配置约束与否取决于宿主配置）。建议：读入前检查 Content-Length 并施加平台上限配置。超出声明包络（网关代理大请求体）时为 OOM 候选。
- 现状标注：新线索，已确认（复核回码属实；无同案）。

### [nop-host-CL-004] SpringWebProxy 重试链语义：refreshAccessToken 无超时、refreshAuthUrl 为空时重试必然失败（C3）
- 站点：`nop-spring/nop-spring-proxy/src/main/java/io/nop/spring/proxy/SpringWebProxy.java:47-52,64-69,129-157`。`retryWhen` 的 `doBeforeRetryAsync` 返回 `refreshAccessToken(...)`：未设 timeout（下游挂起则重试链挂起）；`refreshAuthUrl == null` 时返回 `Mono.empty()`（:131-132）→ 重试仍携旧凭据 → 2 次注定失败的重试放大下游 401 风暴。建议：refresh 调用加 `.timeout(...)` 并在无法刷新时改 `Mono.error` 终止重试。
- 现状标注：新线索，已确认（复核回码属实；无同案）。

## 维度复核结论（任务 B，独立复核员，2026-10-21）

**判定统计**：6 条初审 + 4 条 CL 全部逐条回码验证；保留 5、降级 1（C1-002 P1→P2）、驳回 0；判级修正 1（C1-006 P2→P1，见下）。CL×4 全部确认（回码属实、cra-index 台账查重无同案）。

**逐条裁定要点**：
- **C1-001（保留 P1）**：写读线程归属、非原子双写、无任何同步三要素逐行吻合；cra-index P1 表对 nop-spring 零登记，无跨单元同案。
- **C1-002（降级 P1→P2）**：缺陷本体成立，但 R1 触发链的执行载体归因有误——任务执行走 api-core `ContextTaskQueue`（无 StandardThreadPoolExecutor 池），仅在"同 context 已有 syncGet（syncing>0）"时才移交他线程，调用线程自 drain 路径 TL 尚未 remove、缺陷不触发；多线程 drain 前提在 11 根内无已证调用链，按"未具体化"口径降 P2，传播缺失修复义务保留。
- **C1-003/C1-004/C1-005（保留原判级）**：回码全部吻合（含 keepalive 调度/取消点 :82-83/:114-116/:126-128、懒初始化全族 grep 复现、servlet isResponseSent 实为 isCommitted() 委托）。
- **C1-006（保留 + P2→P1）**：复核补全了 R1"未逐证"的变体 B 机制链——`IocCoreInitializer.initialize`（nop-ioc IocCoreInitializer.java:41-49）在 NopApplication.run 中以 `BeanContainer.registerInstance(appContainer)` 覆盖宿主桥；窗口 [宿主桥注册 .. IocCoreInitializer 重注册]（横跨秒级平台初始化）内 `getBeansOfType(IHttpServerFilter)` 落在 Spring context/CDI 上，而全部 filter 实现均为 nop-IoC bean（auth-service.beans.xml:242、biz-defaults.beans.xml:120-130；全仓唯一 spring-*.beans.xml 仅含 nopBaseMapper；nop-quarkus 无 deployment/extension、无 SyntheticBeanBuild）→ **空链固化变体双宿主成立**：任一启动窗口请求即令认证链（AuthHttpServerFilter 等）持久静默旁路直至重启。R1 变体 A 订正：ERR_IOC_BEAN_CONTAINER_NOT_INITIALIZED 500 自愈仅适用 onStart 注册桥之前的窗口。安全属性持久失效 + 确定触发链 → P1。

**台账查重（cra-index WI 1.1–4.3）**：nop-host 单元在本 campaign 无先期登记（dispatch-log：宿主并发主题直接审计 0 命中），本维度 6+4 条零同站点重复。家族关联单列：C1-002 ↔ 上下文传播族（WI 2.3 C3-007 BizActionService IContext 不传播、WI 2.3 C3-002 出口泵缺 thenOnContext）——同族异站。
**WI 2.1 CL-021 漂移复查**：CL-021 点名载体为 raw `new Thread`（FeishuClient 面，归 WI 4.3 MA-04）；nop-host 基线 C4a 零线程创建 + R1 补充 grep 空 + R2 生命周期回调全表无 raw 线程——宿主无 CL-021 成员，无漂移，预注"不含宿主"成立。

## 最终保留项（C1，合并 R1+R2）

| 编号 | 标题 | 判级 | 复核裁定 |
|---|---|---|---|
| C1-001 | WebClientHolder 凭据对跨线程撕裂 | P1 | 保留 |
| C1-002 | g_serverContext ThreadLocal 不传播 + 静默降级 | **P2**（降自 P1） | 降级保留 |
| C1-003 | WS basicRemote 并发写无串行化 | P2 | 保留 |
| C1-004 | 懒初始化 check-then-put 无同步族 | P2 | 保留 |
| C1-005 | 服务器上下文字段无 volatile（理论） | P2 | 保留 |
| C1-006 | getFilters 启动窗口空链固化（认证旁路） | **P1**（升自 P2） | 保留 + 补强 |
| CL-001～004 | computeIfAbsent 锁内 I/O / 静态资源探测 / 请求体无界 / 重试链语义 | CL | 4 条全确认 |

R2 深挖（C1-round2.md）：0 条新发现，四个点名目标全部负结果（验证记录见该文件）；C1 终止，不派第 3 轮。
