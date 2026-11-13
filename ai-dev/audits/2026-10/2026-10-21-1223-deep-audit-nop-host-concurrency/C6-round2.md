# C6 资源生命周期与泄漏 — nop-host 第 2 轮深挖（WI 4.4）

- 审计单元：nop-host（宿主集成层）C6 维度深挖轮
- 审计日期：2026-10-21（深挖 round 2，盲审口径）
- 前置：R1 发现 C6-001～C6-006 + CL-006/CL-007（见 `C6-resource-lifecycle.md`），本轮不重复立案。
- 深挖任务简报点名目标：CoreInitialization.destroy 幂等/双调用（NopSpring onClose + Quarkus 各自触发）、McpServerConfiguration 资源、servlet startAsync 之外的其他异步资源。
- 降档口径沿用 R1 C6 专属声明（泄漏/残留形态不适用启动期降档）。

## 深挖结论总览

**本轮 3 条新立案（C6-007 P1 / C6-008 P2 / C6-009 P3）+ 1 条 CL（CL-008）**。最强新发现 C6-007 位于简报点名的"startAsync 之外异步资源"面：Quarkus 认证请求链路上任务同步异常被任务队列吞掉，返回给 RESTEasy 的 future 永不完成，请求悬挂。

## 发现

### [C6-007] withRoutingContext 任务同步异常被 ContextTaskQueue 吞掉，返回 future 永不完成：Quarkus 认证请求链路可被外部触发的永久悬挂（异步资源无释放）

- **文件**: `nop-quarkus/nop-quarkus-web/src/main/java/io/nop/quarkus/web/utils/QuarkusExecutorHelper.java:43-67`；平台根因证据 `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/context/ContextTaskQueue.java:104-107,150-156`（catch Throwable → 仅 LOG.error）、`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/context/BaseContext.java:228-243`（runOnContext 两分支均经队列执行）
- **证据片段**:
  ```java
  // QuarkusExecutorHelper.java:55-63 —— bindResult 在 task.get() 之后求值，同步抛出即失联
  context.runOnContext(() -> {
      IUserContext userContext = IUserContext.get();
      if (userContext != null) { AuthMDCHelper.bindMDC(userContext); }
      try {
          FutureHelper.bindResult(task.get(), future);   // task.get() 同步抛出 → 异常逃出 lambda
      } finally { AuthMDCHelper.unbindMDC(); }
  });
  return future;                                         // 该 future 永无 complete 调用
  // ContextTaskQueue.java:154-156 —— 唯一接住异常的地方只记日志
  try { task.run(); } catch (Throwable e) { LOG.error("nop.core.context.run-task-fail", e); }
  ```
- **严重程度**: P1
- **并发场景**: Vert.x 事件循环线程（端点方法进入 withRoutingContext，认证请求 `ctx.getContext() != null` 走 runOnContext 分支）× ContextTaskQueue drain 线程（BaseContext 自 drain 或 StandardThreadPoolExecutor 池线程，BaseContext.java:228-243 经 TaskQueue/StandardThreadPoolExecutor）。两分支中 task lambda 的同步异常都落进 ContextTaskQueue 的 catch-Throwable-仅记日志路径，异常不再向 `future` 传播。
- **触发时序**: T1（事件循环线程）：认证 POST `/px/{serviceName}/{serviceMethod}` 携带畸形 JSON body 进入 `QuarkusGraphQLWebService.proxy`（:48-57）→ `withRoutingContext(rc, () -> runProxy(...))` → runOnContext 分支（认证上下文非空）；T2（drain 线程）：`task.get()` → `runProxy` 首行 `ApiRequest<?> request = requestBuilder.get()`（GraphQLWebService.java:201，**try 块之外**）→ `buildRequest` → `JSON.parse(body)`（:340）对畸形 JSON 抛 NopException → 逃出 lambda → ContextTaskQueue 仅记 `nop.core.context.run-task-fail`；T3：`FutureHelper.bindResult` 从未执行 → `future` 永不完成 → RESTEasy Reactive 等待的 `CompletionStage<Response>` 悬挂（Quarkus 默认无请求级超时）→ **HTTP 连接挂起直至客户端断开**，期间 RoutingContext/VertxHttpServerContext/请求缓冲以未完成 future 链保持可达。交叠后果：每个畸形请求稳定制造 1 个悬挂连接（客户端不断开则持续占用），无响应、无 5xx、无请求级关联日志。
- **现状**: host 层把"取结果"与"异常转译"合并在 `bindResult(task.get(), future)` 一个表达式里，task supplier 的同步异常没有落回 future 的通道；平台 ContextTaskQueue 的吞异常语义（F9）在这里的投影是"请求悬挂"而非"错误响应"。与 C1-002 同函数不同根因（该条是 TL 传播缺失，本条是异常→future 绑定缺口），分开计数。
- **风险**: 用户可控输入（畸形 JSON/selection）稳定复现；k8s 探测与慢客户端场景下悬挂连接累积挤占连接表；启动窗口（C4-003）内 `getBeanByType(IGraphQLEngine)` 抛出同样落此路径——Quarkus 认证请求的表现从预期的 500 恶化为悬挂（扩大 C4-003 的后果面）。
- **建议**: host 层最小修复（不动平台）：`try { CompletionStage<T> r = task.get(); FutureHelper.bindResult(r, future); } catch (Throwable e) { future.completeExceptionally(e); }`；同步异常落 future 后 RESTEasy 正常产出 500，悬挂消失。平台侧可同时评估 ContextTaskQueue 吞异常处补充 `LOG.error` 之外的上下文标记（本审计不改平台）。
- **失效面枚举**: withRoutingContext 全部 7 个调用点逐一核对 task supplier 的同步抛出面——(a) `QuarkusGraphQLWebService.proxy` :48-57 → runProxy `requestBuilder.get()`（GraphQLWebService.java:201，try 外）：**用户可触发**（畸形 JSON/selection），本条主形态；(b) `graphql` :62-64 / `rest` :69-73 → runGraphQL:93 / runRest:241 的 `getBeanByType(IGraphQLEngine)` 在 try 外：启动窗口/平台销毁后触达（engine 缺失即抛）——同根因，随 (a) 一并消除；(c) `restQuery` :81-86 / `pageQueryGet` :92-96 / `pageQuery` :101-105 → doPageQuery :377-393 将 buildRequest 包进 runRest 的 requestBuilder（try 内 :250）→ 已被 catch 转错误响应，**保护面**；(d) `QuarkusJsonRpcWebService` :26-33 → runJsonRpc（GraphQLWebService.java:397-400）`getBeanByType(JsonRpcService)` 无保护 + `executeAsync` 同步抛出面：同根因；(e) `QuarkusFileService.uploadFileAsync` :56-96 / `doDownload` :167-177：supplier 体内自带 try/catch 转 FutureHelper.success（upload :61-94），download 路径 buildApiRequest/downloadAsync 同步抛出面低（引擎缺失窗口除外，归 (b) 类）；(f) Spring 宿主对照：`SpringGraphQLWebService`/`SpringJsonRpcWebService` 直接在 controller 线程调用同名 runXxx，同步异常由 Spring MVC 异常通道转 500，**无此缺陷**（反证缺陷仅在 runOnContext 间接层）；(g) 平台面 `GraphQLWebService.runGraphQL` 的 catch(Exception)（:141-149）覆盖了 try 内同步异常——本条抛出面全部位于各 catch 之外，枚举闭合。
- **信心水平**: 确定（异常路径逐行可证；悬挂唯一不确定项是部署方是否配置了 Quarkus HTTP idle-timeout——默认无请求级超时）
- **误报排除**: 非"低频路径粗阻塞"误报——(a) 面由单条畸形请求即可触发且属主 API 面（/px/ 代理端点）；非"RESTEasy 自会超时"臆断——已核对 Quarkus 默认无 CompletionStage 级超时且代码侧无任何 timeout 补偿（对照 R1 CL-005 对 filter future 无超时的同族登记）；亦不与 C1-002/CL-005 重复计数（同函数不同根因、不同站点）。
- **复核状态**: 已保留（详见文末复核结论）

### [C6-008] servlet 流式响应错误路径调用 complete() 而非 completeWithError()：失败被伪装成正常完成，截断的 chunked 响应体对客户端表现为完整 200（错误中止语义缺失）

- **文件**: `nop-spring/nop-spring-web-starter/src/main/java/io/nop/spring/web/filter/ServletHttpServerContext.java:292-297,346-352`
- **证据片段**:
  ```java
  // :292-297（Publisher 变体；:346-352 writerFn 变体 fail 同构）
  private void completeWithError(Throwable error) {
      if (completed.compareAndSet(false, true)) {
          asyncCtx.complete();                 // 非 completeWithError(error)：容器按"正常完成"收口
          future.completeExceptionally(error); // 错误只进 future（调用方是阻塞的 syncGet，丢弃）
      }
  }
  ```
- **严重程度**: P2
- **并发场景**: publisher onError/onNext 写失败线程（任意提供者线程）与容器异步完成管理。失败发生在流式写出中途（下游服务断流、序列化异常、客户端写超时）。
- **触发时序**: T1：流式响应已写出 N 个 chunk（chunked 传输、未发终止块）；T2：publisher onError → `completeWithError(e)` → `asyncCtx.complete()` —— 容器将 async 按**正常完成**处理，servlet 容器补齐 chunked 终止块并按已写状态提交响应；T3：客户端收到状态 200 + 结构完整但内容截断的 body，与正常成功的唯一区别是长度——消费方（SSE/代理流的下游解析器）无法区分"流结束"与"流故障"。交叠后果对照 F2"半个成功"：错误在传输层被洗白为成功。future 侧 `syncGet`（C4-001 链）对 exceptionally 的处理不影响已提交的响应。
- **现状**: CAS 守卫与双路径收口结构正确（R1 已核），缺失的是**错误路径的容器通报**：Servlet 规范的 `AsyncContext.completeWithError()` 会标记 async 超时/错误态并允许容器中断连接（chunked 未终止时触发截断可见的协议级错误），`complete()` 则尽力正常收尾。Quarkus 孪生 `VertxHttpServerContext.completeWithError`（:259-264）与 `fail`（:317-324）用 `resp.reset()` 中止连接——**两宿主在错误中止语义上不对称，Quarkus 形态正确**。
- **风险**: 网关/订阅转发（GatewayHttpFilter 经此路径）下，上游故障被静默转换为截断成功响应，客户端缓存/重试逻辑失去错误信号；数据完整性要求高的消费端（如导入任务）可能把半截数据当全量入库（F2 同族投影）。
- **建议**: `completeWithError` 内改调 `asyncCtx.completeWithError(error)`（外层 try 包裹防容器已完成时的 ISE，与 R1 C6-004 建议的幂等容错合并实现）；writerFn 变体 `fail` :346-352 同改；错误仅上报 future 的现状可保留（响应侧已由容器通报）。
- **失效面枚举**: (a) Publisher 变体 `completeWithError` :292-297（本条）；(b) writerFn 变体 `fail` :346-352（同构，F3 孪生同修）；(c) `onComplete`/`complete` 正常路径 :278-290/:332-343：complete() 语义正确，不涉及；(d) Quarkus 孪生 `completeWithError` :259-264 与 `fail` :317-324（resp.reset()）：合规参照形态；(e) 同站点 R1 C6-004（容器超时截断）：同函数不同根因（超时声明缺失 vs 错误中止语义），互补不重复计数。
- **信心水平**: 确定（API 语义差异由 Servlet/Vertx 规范直接可证；客户端可见后果为 chunked 终止块的有无，属协议可判事实）
- **误报排除**: 未把 CAS 守卫当缺陷（已判定正确）；不是 R1 C6-004 的重复——C6-004 立案的是"无 setTimeout/无 AsyncListener"的超时面，本条立案的是主动失败路径的错误通报面，修复动作不同（加超时/监听器 vs 换中止 API）；也不按"容器总会关连接"豁免——complete() 后容器不会中断已正常收尾的连接，截断不可见是规范后果。
- **复核状态**: 已保留（详见文末复核结论）

### [C6-009] IHttpServerContext.sendResponse(int, InputStream) 释放责任未声明：两宿主实现均不关闭调用方传入的流（成功与异常路径都不关），当前在库调用面为零的潜伏契约缺口

- **文件**: `nop-network/nop-http/nop-http-api/src/main/java/io/nop/http/api/server/IHttpServerContext.java:92`（接口，无 javadoc 契约）；两实现 `nop-spring/nop-spring-web-starter/src/main/java/io/nop/spring/web/filter/ServletHttpServerContext.java:216-225`、`nop-quarkus/nop-quarkus-web/src/main/java/io/nop/quarkus/web/filter/VertxHttpServerContext.java:189-202`
- **证据片段**:
  ```java
  // ServletHttpServerContext.java:219-221 —— try-with-resources 只包输出流，输入流 body 无人关闭
  try (OutputStream out = response.getOutputStream()) {
      IoHelper.copy(body, out);        // body（InputStream）在成功/异常路径均不被 close
      out.flush();
  // VertxHttpServerContext.java:197-198 —— 同样只管拷贝
  IoHelper.copy(body, new ResponseOutputStream(resp));
  resp.end();
  ```
- **严重程度**: P3
- **并发场景**: 无（生命周期契约缺陷）；未来调用方在异步链上传入流时，获取线程与释放责任解耦，泄漏面随调用方数量放大。
- **触发时序**: 潜伏（当前 11 根及全仓 grep 均无 `sendResponse(int, InputStream)` 调用点——最近邻 `GatewayHttpFilter.writeNormalResponse/writeErrorResponse`（GatewayHttpFilter.java:282,306）走的都是 String 重载）。任何未来调用方按"方法消费即释放"的直觉传入已打开的流：拷贝中途异常（客户端断开 → IOException）时流不被关闭，fd 直到 GC 终结才释放——与 C6-001 同形态的"获取-交接无兜底"。
- **现状**: 接口方法零契约文档；Servlet 实现只对 OutputStream 负责，Vertx 实现对流完全不负责——同一契约在两宿主的释放语义已经漂移，先于调用方存在。
- **风险**: 当前无触发路径（潜伏登记）；作为平台公共宿主接口，首个调用方即暴露，届时按 P1/P2 口径立案。
- **建议**: 接口 javadoc 显式声明释放责任（推荐"实现负责关闭 body"）；实现侧 `try (InputStream in = body) { IoHelper.copy(in, out); }`（Vertx 侧同改）；或统一经 `IoHelper.safeClose` finally 兜底。
- **失效面枚举**: (a) Servlet 实现 :216-225（本条）；(b) Vertx 实现 :189-202（同构，F3 孪生同修）；(c) `sendResponse(int, String)` 两实现：字符串无资源，豁免；(d) `IStreamResponseWriter`/`getRequestBody` 路径：流由调用方 lambda/容器管理，不涉本契约；(e) 在库调用面：全仓 grep 为空（含 nop-gateway），潜伏确认。
- **信心水平**: 确定（实现行为直接可证；"无调用方"由全仓 grep 复现）
- **误报排除**: 未按"Spring Resource 契约里调用方关闭"类比豁免——`SpringResource.getInputStream`（R1 配对表）有明确的调用方契约来源，而本接口方法无任何契约声明，两实现行为还不一致；也不升格 P1/P2——当前零调用面，按潜伏防御性缺陷 P3 登记（对照 C6-006 的 P3 判级先例）。
- **复核状态**: 已保留（详见文末复核结论）

### 核验无缺陷记录（简报点名站点）

1. **CoreInitialization.destroy 幂等/双调用**（`nop-kernel/nop-core/src/main/java/io/nop/core/initialize/CoreInitialization.java:158-206`）：destroy 为 `synchronized` + `initializers==null` 短路（:160-163），幂等成立。三触发源——Spring `NopSpringCoreAutoConfig.onClose`、`NopApplication.run` 注册的 JVM ShutdownHook disposable（NopApplication.java:41-44）、Quarkus app main 的 `@Observes ShutdownEvent → destroy`（如 NopTaskApplication.stop）——正常停机序下先后触发，第二次调用整体短路；`initializeTo` 失败路径的内部 destroy（:97-100）为同锁重入，部分初始化按 `initializationLevel < order()` 跳过逆序回收，语义闭合。**双调用无缺陷**（多上下文拆毁守卫缺失与 Quarkus 集成层无 stop 已分别由 C6-003/C6-002 立案，不重复）。`initializationLevel` 非 volatile 但全部写点在 synchronized 内，公开 getter（:130-132）理论陈旧读仅影响观测不影响资源回收。
2. **McpServerConfiguration 资源**（`McpServerConfiguration.java:7-14`）：仅 @Bean 工厂方法构造无状态 provider（唯一可变态 `callbacks` 数组的惰初始化归 C1-004），无流/连接/注册型资源，bean 生命周期由 Spring 容器托管。**无生命周期缺陷。**
3. **startAsync 之外的异步资源逐点清点**：(a) `withRoutingContext` future——立案 **C6-007**；(b) `VertxHttpServerContext.proceedAsync` :376-391 双 handler 完成 future——R1 CL-005 已登记（幂等冗余 + filter future 无超时）；(c) `VertxHttpServerContext.getTextAsync` :394-408 基于 `request().body()` vertx Future（多订阅安全）——合规；(d) `executeBlocking` 两实现（Servlet :418-420 内联 / Vertx :411-413 经 ExecutorRecorder）——future 绑定完整、无池占用，合规；(e) `sendStreamingResponse` 返回 future 的 vertx 变体 :205-343——complete/fail 双路径经 CAS 幂等绑定 `resp.end()/resp.reset()`，配对正确（错误中止语义见 C6-008 的 servlet 侧对照）；(f) WS keepalive 定时任务与 handler 装配窗口——R1 C4-002/CL-007 已立案。

## 维度集外线索

### [nop-host-CL-008] isResponseSent 语义两宿主不对称：servlet 委托 response.isCommitted()，vertx 为方法入口预置的手工标志（send 失败后仍为 true）（C3）
- 站点：`ServletHttpServerContext.java:376-378`（`return response.isCommitted()`）对照 `VertxHttpServerContext.java:49,177,183,190,207,273,346-348`（`responseSent=true` 在各 send 方法**入口**设置，实际 send 失败/reset 后不回滚）；消费面：filter 链中后置 filter 依据 `ctx.isResponseSent()` 决定是否继续处理（HttpServerHelper 链）。后果：vertx 侧 send 抛错/连接 reset 后标志仍为 true，后置 filter 对"响应已发送"的判定失真（如重复发送防护失效→由 vertx 二次写异常兜底）；servlet 侧则存在 C1-005 已登记的可见性面。建议：vertx 侧改为查询 `routingContext.response().ended()/closed()` 或在失败分支清除标志。
- 现状标注：新线索，已确认（复核回码属实：ServletHttpServerContext.java:376-378 vs VertxHttpServerContext.java:49/177/183/190/207/273）。

（R1 已登记 CL-001～CL-007；本维度续接 CL-008。）

## 继续判定

**C6 终止，不派第 3 轮。** 依据：(i) 三个点名目标全部闭环（destroy 双调用核验无缺陷、McpServerConfiguration 负结果、startAsync 外异步资源 6 点清点且产出本轮最强发现 C6-007）；(ii) 11 根的资源获取-释放配对面（上传/下载/WS/数据源/指标/流式响应/代理缓存/事务）经 R1 配对表 + R2 清点两轮覆盖，无剩余未核对获取点；(iii) 新发现集中在宿主间接层（withRoutingContext/AsyncContext/接口契约），平台侧根因（ContextTaskQueue 吞异常）已按边界标注，宿主内可修复面明确。

## 复核结论（任务 B，独立复核员）

- **C6-007 维持 P1。** 复核逐环重放：`FutureHelper.bindResult(task.get(), future)` 求值序（task.get() 先于 bindResult 进入）、ContextTaskQueue 双执行路径（enqueue-true 交给 syncing 线程 / enqueue-false 自 drain）的 catch-Throwable-仅记日志（flushTasks 与 syncGet 两处）、runProxy 首行 `requestBuilder.get()` 在 try 外（GraphQLWebService.java:201）、`JSON.parse` 对畸形输入抛出（:340）、runRest/doPageQuery 的 requestBuilder 在 try 内（对照保护面）、Spring 侧无间接层不受影响——全部回码吻合。**台账家族登记：future 永不完成族第 5 例**（先例 WI 4.2 C3-001 / 4.3 C3-001 / 3.2 C1-001 / 1.7 C6-001，机制与 3.2 C1-001 同构）；平台根因关联 WI 2.3 CL-018（ContextTaskQueue 承重原语）与 WI 2.1 C3-001（异常绝缘层）；修复义务按台账惯例建议与该族同批对齐。唯一判级敏感点（部署方可配 idle-timeout 兜底）已在条目信心水平中如实标注，不构成降档事由。
- **C6-008 维持 P2。** complete-vs-completeWithError 的规范语义差（chunked 终止块的有无 → 客户端可判截断）复核成立；vertx resp.reset() 正确对照属实。**台账家族登记：错误以成功交付族传输层成员**（先例 WI 3.4 C3-005 / 3.1 C7-002 / 4.3 C3-005）。与 C6-004 的根因切分（超时面 vs 错误中止面）经复核维持。
- **C6-009 维持 P3。** 两实现均不关调用方流、`sendResponse(int, InputStream)` 全仓调用面为零（含 GatewayHttpFilter 两处均走 String 重载，实测复核）——潜伏契约缺陷 P3 判级恰当。
- **CL-008 确认**：isResponseSent 两宿主语义不对称回码属实（servlet :376-378 isCommitted() 委托 vs vertx :49/:177/:183/:190/:207/:273 入口预置手工标志）。
- **核验无缺陷记录复核**：destroy 幂等/双调用、McpServerConfiguration 资源、startAsync 外异步资源清点全部维持；复核补充一处稳态机制说明——BeanContainer facade 稳态指向 nop-IoC 容器（IocCoreInitializer 注册，宿主桥退化为 parent），C6-007 (b) 启动窗口面的 `getBeanByType` 抛出窗口以该覆盖时点为界。**C6 终止判定维持。**
