# C4 线程池与执行器治理 — nop-host（WI 4.4）

- 审计单元：nop-host（宿主集成层，合并批 C1+C4+C6 三维度中的 C4 部分）
- 审计日期：2026-10-21（初审，盲审口径）

## 审计范围与口径

**合并批三维度覆盖声明**：见 `C1-shared-mutable-state.md` 同名节。本文件承载 C4 维度发现；启动窗口/共享定时器的跨维度交叉引用已标注对应 C1/C6 条目。

**原始并发面极薄预校准声明（如实记录）**：基线 C4a 节（thread creation and pools）在全部 11 源码根**零命中**，C4b 节仅命中 3 处生命周期回调（Spring `@EventListener` onStart/onClose、Quarkus `@PostConstruct/@PreDestroy`）。本轮以补充 grep（`new Thread|Executors\.|newFixedThreadPool|newCachedThreadPool|IThreadPool|scheduleAtFixedRate|scheduleWithFixedDelay|new Timer\(|parallelStream|ForkJoinPool`）对 11 根全量复现，结果为空，与基线一致。**R1 实测结论：宿主集成层不自建任何线程/池/定时器——如实登记，不凑数扩面。** 因此本维度发现集中在三类"借用面"：(i) 对平台共享 executor（`GlobalExecutors.globalTimer()`、Quarkus `ExecutorRecorder` 执行器）的占用与生命周期配对；(ii) servlet 工作线程在异步链上的阻塞语义；(iii) 启动期单线程触达站点的降档裁定。

**降档口径声明（C4 专属）**：宿主以 main 线程启动为常态（`SpringApplication.run` / `Quarkus.run`），`@EventListener`/`@Observes` 回调、`NopApplication.run`、IoC bean 创建均运行在启动期单线程——这些站点的竞态/可见性按站点校准降档，不报"并发修改"；请求面（filter、controller、WebSocket 回调）不降档。

### C4 线程池清单表（池/线程来源 × 参数 × 队列 × 拒绝策略 × 关闭路径 × 判定）

| 执行载体 | 引用点（11 根内） | 参数/队列/拒绝 | 关闭路径 | 判定 |
|---|---|---|---|---|
| 平台 `GlobalExecutors.globalTimer()`（共享调度池，daemon，maxPoolSize=CFG_CONCURRENT_GLOBAL_TIMER_MAX_POOL_SIZE） | WS keepalive：两宿主端点 onOpen → `JsonRpcWebSocketHandler` 构造（JsonRpcWebSocketHandler.java:82-83，每连接 1 个 10s 周期任务） | 平台配置；非本层治理范围 | `onClose/onThrowable → cancel(false)` | **C4-002**（占用+泄漏窗口） |
| Quarkus `ExecutorRecorder.getCurrent()` 执行器 | `QuarkusExecutorHelper.executeBlocking` :77 | Quarkus 托管 | Quarkus 生命周期托管 | 合规（委托宿主池，任务隔离由调用方保证） |
| 平台 `FutureHelper.futureCall` | `SpringHttpServerFilterConfiguration` :100、`ServletHttpServerContext` :418-420 | **内联执行（task.call() 于当前线程，FutureHelper.java:122-129），非池提交** | 不适用 | 纠正常见误读：无隐藏池；阻塞面见 **C4-001** |
| servlet 容器工作线程（借用） | `NopHttpServerFilter.doFilterInternal` → `FutureHelper.syncGet` :105 | 容器池 | 容器托管 | **C4-001** |
| 启动 main 线程 | `NopSpringCoreAutoConfig.onStart` :28-34 / `QuarkusIntegration.start` :26 / 各 app main | 单线程 | — | 降档裁定见 C6 文件 |
| JVM ShutdownHook | `NopApplication.run`（nop-boot）每次 run 注册 1 个 destroy 任务 | JVM 退出串行执行 | — | C6 文件 C6-003 附注 |

## 发现

### [C4-001] Spring 过滤链把整个下游处理包进 CompletionStage 后用 syncGet 阻塞 servlet 工作线程，异步流式响应期间独占容器线程

- **文件**: `nop-spring/nop-spring-web-starter/src/main/java/io/nop/spring/web/filter/SpringHttpServerFilterConfiguration.java:89-108`
- **证据片段**:
  ```java
  IHttpServerContext ctx = new ServletHttpServerContext(request, response);
  CompletionStage<?> future = HttpServerHelper.runWithFilters(serverFilters, ctx, () -> {
      return FutureHelper.futureCall(() -> {
          filterChain.doFilter(request, response);   // 内联：仍在当前 servlet 线程
          return null;
      });
  });
  FutureHelper.syncGet(future);                      // 阻塞直至下游（含异步流式）完成
  ```
- **严重程度**: P2
- **并发场景**: Tomcat 工作线程池（N 个请求线程）× servlet 异步链。同步 controller 路径上 `syncGet` 等待的是内联完成的结果（无额外代价）；但在下游走 servlet 异步（`ServletHttpServerContext.sendStreamingResponse` :237-239 调用 `request.startAsync()`，future 直到 `asyncCtx.complete()` 才完成）时，**工作线程在 `syncGet` 上阻塞至整个流式响应结束**。
- **触发时序**: T1（工作线程 W1）：请求进入 nop filter → `runWithFilters` → 下游 controller 发起 SSE/长流式响应 → `startAsync`；T2：`filterChain.doFilter` 返回但外层 future 未完成 → W1 停在 `syncGet`（:105）等待分钟级流结束；交叠后果：每条进行中的流式连接独占 1 个容器工作线程，SSE/代理流并发数 × 线程占用时长直接换算为容器线程池耗尽阈值，其余请求（含健康检查）排队。
- **现状**: `FutureHelper.futureCall` 是内联调用（FutureHelper.java:122-129 `task.call()`）——已核实**不存在**"下游被偷换到全局池"的问题；缺陷形态是"同步 filter API 桥接异步 servlet 语义"时的线程持有策略缺失：未使用容器异步退出路径（filter 内提前释放工作线程）。
- **风险**: 长流式（网关代理、订阅转发）负载下容器线程池按流并发数线性耗尽；与 C6-004（异步超时缺失）叠加时被阻塞线程的最长持有时间不受任何上界约束。
- **建议**: 对 `shouldNotFilterAsyncDispatch` 显式声明（`OncePerRequestFilter` 子类化）或改用 `WebAsyncManager` 注册 `Callable`/`DeferredResult`，把同步 filter 的等待转为容器管理的异步挂起；短期可在 `syncGet` 上加可配置超时并与 C6-004 的 `setTimeout` 对齐。
- **失效面枚举**: (a) `NopHttpServerFilter`（sys 实例）与（app 实例）：同一 `doFilterInternal` :89-108，两个注册实例同修；(b) Quarkus 孪生 `HttpServerFilterRegistrar.registerFilter` :84-88：**无 syncGet**（事件循环不阻塞，future 异常经 `rc.fail` 传递）——同族中唯一合规形态，反证 Spring 侧阻塞非必然；(c) `ZipContentEncodingFilter` :37-84：直通 filterChain 无等待，合规；(d) `ServletHttpServerContext.executeBlocking` :418-420：内联 futureCall 无跨线程，合规；(e) 11 根内其余 syncGet 调用：无（grep 复现仅此一处）。
- **信心水平**: 确定（阻塞结构由代码直接可证；触发规模取决于部署是否使用流式/异步下游）
- **误报排除**: 未按"同步 filter 等待下游"一概而论——同步路径等待内联结果是零成本合规形态；仅对"异步下游 + 工作线程持有"这一可量化耗尽路径立项，并给出量化口径（并发流数 × 流时长 vs 容器池容量）。低频路径粗阻塞不在此列（误报校准第 3 条）。
- **复核状态**: 已保留｜P2 维持。回码验证：SpringHttpServerFilterConfiguration.java:99-105（runWithFilters + futureCall 内联 + syncGet）逐行吻合；Quarkus 孪生 HttpServerFilterRegistrar.java:84-88 exceptionally→rc.fail 无 syncGet 对照属实；e) 项"11 根内其余 syncGet 无"经独立 grep 复现一致。台账查重：与 WI 4.3 MA-03（sidecar 校验和探测 get(30s) 阻塞 IO reactor 线程）为同族异站异机制（servlet 工作线程 vs HC5 reactor 回调线程），家族关联单列、零同站点重复。现状：新发现未修复。

### [C4-002] WS keepalive：每连接一个 10s 周期任务常驻共享 globalTimer，取消仅系于 onClose/onThrowable 两条回调路径

- **文件**: `nop-spring/nop-spring-web-starter/src/main/java/io/nop/spring/web/ws/JsonRpcWebSocketEndpoint.java:70-79`；`nop-quarkus/nop-quarkus-web/src/main/java/io/nop/quarkus/web/ws/JsonRpcWebSocketEndpoint.java:66-76`（调度点：`JsonRpcWebSocketHandler.java:82-83`，nop-graphql，本层为实例化源头）
- **证据片段**:
  ```java
  // 端点 onOpen（两宿主同构）：每会话构造 handler → 即注册共享定时任务
  JsonRpcWebSocketHandler handler = new JsonRpcWebSocketHandler(executionService, wsSession, initialHeaders);
  // JsonRpcWebSocketHandler.java:82-83
  this.keepAliveSender = GlobalExecutors.globalTimer().scheduleWithFixedDelay(this::sendKeepAlive, 10, 10, TimeUnit.SECONDS);
  ```
- **严重程度**: P2
- **并发场景**: 宿主 WS 端点（Spring `@ServerEndpoint`、Quarkus `@ServerEndpoint`）的容器回调线程 onOpen 实例化 handler；`GlobalExecutors.globalTimer()`（平台全局共享调度池，daemon，容量 = `CFG_CONCURRENT_GLOBAL_TIMER_MAX_POOL_SIZE`）承载 N 个连接的周期任务；取消路径为容器线程 onClose/onThrowable。
- **触发时序**: 常态：每连接常驻 1 任务，`onClose`（JsonRpcWebSocketHandler.java:126-128）与 `onThrowable`（:114-116）cancel——配对成立。异常时序：容器在异常断链（TCP RST、进程内错误）场景按规范仍会回调 onClose；但 handler 构造成功后、`session.getUserProperties().put("graphql.handler", handler)`（Spring :77 / Quarkus :74）**之前**若 onOpen 抛出（如 Quarkus :70 `getBeanByType(IUserContextExtractor.class)` 无 bean 时抛 NopException，Spring :74 用 tryGet 安全，Quarkus 不对称）→ handler 已创建、**定时任务已注册**、但 userProperties 无引用 → onClose 取不到 handler → **任务永久滞留共享 timer 并持有 session 引用**；交叠后果：连接数反复异常建立时共享调度池积压常驻任务（每任务持有 handler/session/logger 引用链，不可回收）。
- **现状**: 平台惯例上共享 executor 占用是允许的（globalTimer 本为公共设施），缺陷点是"注册与可取消性之间隔了两个后续语句"的不原子装配，以及 Quarkus 端点 extractor 获取策略与 Spring 端点不对称放大了装配中断概率。
- **风险**: 长时运行下异常建立路径累积的滞留任务只增不减（对照 F4 无界账本/F8 清理不对称）；量级取决于异常建立频率，单调增长源明确。
- **建议**: handler 构造与定时任务解耦——keepalive 首次调度延迟到 `onMessage` 首包或装配完成后（惰性启动）；Quarkus :70 改 `tryGetBeanByType` 与 Spring 对齐；关闭兜底在 handler 构造处 try/catch，异常时立即 `keepAliveSender.cancel`。
- **失效面枚举**: (a) Spring 端点 onOpen :70-79：装配序列 put→addMessageHandler（滞留窗口 :74-77）；(b) Quarkus 端点 onOpen :66-76：同构且 extractor 抛错面更大（:70 vs Spring :74）；(c) 取消双路径 onClose/onThrowable：均已配对，二次 cancel 幂等无害；(d) 11 根内其他 globalTimer/schedule 使用：**无**（grep 复现为空）——本条是宿主层唯一共享调度占用点。
- **信心水平**: 很可能（滞留窗口由装配顺序直接可证；触发依赖 onOpen 中途抛错）
- **误报排除**: 不报"共享 executor 被占用"本身（平台设施设计意图、误报校准第 4 条）；立项依据是**具体的滞留装配窗口**与单调增长链，非泛化的池占用担忧。
- **复核状态**: 已保留｜P2 维持。回码验证：JsonRpcWebSocketHandler.java:82-83（构造器内 globalTimer.scheduleWithFixedDelay）与取消双路径 :114-116（onThrowable）/:126-128（onClose）实测吻合；两宿主端点装配序列（Spring :74 tryGet / Quarkus :70 getBeanByType 不对称）属实。现状：新发现未修复。

### [C4-003] 启动执行窗口：HTTP 端点先于平台初始化可服务请求，filter/controller 首访未初始化全局态（瞬态 500），无就绪门禁

- **文件**: `nop-spring/nop-spring-web-starter/src/main/java/io/nop/spring/web/filter/SpringHttpServerFilterConfiguration.java:92`；`nop-quarkus/nop-quarkus-web/src/main/java/io/nop/quarkus/web/filter/HttpServerFilterRegistrar.java:71-73`；宿主装配 `nop-spring/nop-spring-core-starter/.../NopSpringCoreAutoConfig.java:27-34`、`nop-quarkus/nop-quarkus-core-starter/.../QuarkusIntegration.java:26-52`
- **证据片段**:
  ```java
  // Quarkus：filter 注册早于平台初始化（代码内注释自证）
  void registerFilter(Filters filters, boolean sys) {
      if (!enableFilter) return;
      // 此时Nop平台还没有初始化
      filters.register((rc) -> {
          List<IHttpServerFilter> serverFilters = getFilters(sys);  // 请求期求值
  ```
- **严重程度**: P2
- **并发场景**：启动 main 线程（执行 `NopApplication.run`：模块扫描/模型装载/初始化器链，秒级耗时）与启动窗口内到达的请求线程（Spring：Tomcat 于 `onRefresh` 即监听端口，早于 `ApplicationStartedEvent`；Quarkus：HTTP server 先于 `StartupEvent` observer 完成 `QuarkusIntegration.start`+`NopApplication.run`）。
- **触发时序**: T1（main 线程）：`NopApplication.run` 尚在初始化中段；T2（请求线程）：就绪探测或未等待的客户端请求到达 → filter 链求值 `getFilters` → `BeanContainer.instance()` 抛 `ERR_IOC_BEAN_CONTAINER_NOT_INITIALIZED`（BeanContainer.java:28-32）或 controller 侧 `getBeanByType(IGraphQLEngine)` 抛同类异常 → 500；交叠后果：k8s 启动期探测/重试风暴下的错误日志与失败计数放大。**降档裁定**：启动期单线程为宿主常态，窗口本身按站点校准降档（不报"并发初始化竞态"）；立项点是无门禁导致的请求面错误行为与 C1-006 变体 B 的空链固化残面。
- **现状**: 两宿主都提供了就绪探针（`NopPlatformReadyIndicator`/`QuarkusReadyCheck` 读 `CoreInitialization.isInitialized()`），但**请求面 filter 链未消费该状态**——就绪语义止步于健康端点，未回灌到执行路径。
- **风险**: 主要为启动窗口内的失败请求与错误噪声；持久化风险在 C1-006 变体 B（空链固化）单独覆盖。
- **建议**: filter 入口复用就绪语义：`CoreInitialization.isInitialized()` 为假时对非健康路径返回 503（fail-fast + 明确语义），或宿主装配层在平台就绪前延迟端口绑定（Quarkus `quarkus.vertx.event-loops`/Spring `server.port` 就绪后移由部署层兜底）。
- **失效面枚举**: (a) Spring `NopHttpServerFilter.doFilterInternal`（sys/app 两实例）；(b) Quarkus `HttpServerFilterRegistrar` 注册的两个 vertx filter（sys/app）；(c) 两宿主 controller 首访 `BeanContainer` 的入口全集（GraphQL/JsonRPC/File/WebProxy/MCP）同受窗口影响（同一根因，随 filter 门禁一并消除）；(d) 健康端点自身 `NopPlatformReadyIndicator`/`QuarkusReadyCheck`：无状态只读 volatile，合规。
- **信心水平**: 确定（窗口存在性由 Spring Boot/Quarkus 标准启动序 + 代码注释自证）
- **误报排除**: 按降档口径未把"启动期单线程触达"本身立项（如 `QuarkusIntegration.start` 的全局配置写入、`NopBeansRegistrar` 的注册期资源扫描）；立项点是启动窗口的**请求面**行为契约缺失，属 C4"初始化先于首个请求"时序假设条款，且不降档（请求面）。
- **复核状态**: 已保留｜P2 维持 + 后果面补强。回码验证：QuarkusIntegration.java:79-82 注释自证、两宿主就绪探针只读不门禁属实。复核补强：该窗口内 Quarkus 认证请求的实际后果为**悬挂而非 500**（task supplier 同步抛出被 ContextTaskQueue 吞掉 → future 永不完成，见 C6-007 (b) 面）——C4-003 的"500 失败请求"描述仅覆盖匿名路径与 Spring 宿主。关联：同族注册回调面已另立 C4-004（R2）。现状：新发现未修复。

## 维度集外线索（overlay C2/C3/C5/C7/C8/C9）

### [nop-host-CL-005] proceedAsync 双端处理器重复完成同一 future；quarkus filter future 无超时（C2/C3）
- 站点：`nop-quarkus/nop-quarkus-web/src/main/java/io/nop/quarkus/web/filter/VertxHttpServerContext.java:376-391`（`addBodyEndHandler` + `addEndHandler` 双注册，先到者完成 future、后者完成调用为 no-op——幂等但语义冗余）；`HttpServerFilterRegistrar.java:84-88`（runWithFilters future 仅挂 exceptionally，无超时——nop filter 挂死时请求悬挂至 vertx idle timeout）。建议：双处理器收敛为单 `addEndHandler`；filter future 接入平台超时配置。
- 现状标注：新线索，已确认（复核回码属实：VertxHttpServerContext.java:378-387 双注册幂等完成；HttpServerFilterRegistrar.java:84-88 exceptionally 无超时；与 WI 2.3 CL-022 Delayer 移交轮无同站点冲突）。

（C1 维度已登记 CL-001～CL-004；本维度续接 CL-005；C6 维度自 CL-006 起续接。）

## 维度复核结论（任务 B，独立复核员，2026-10-21）

**判定统计**：3 条初审 + 1 条 CL 全部回码验证；保留 3、降级 0、驳回 0。R2 新增 C4-004 经复核维持 P2（见 C4-round2.md 复核结论）。

**逐条裁定要点**：
- **C4-001（保留 P2）**：syncGet 阻塞结构、futureCall 内联纠偏（不存在隐藏池）、Quarkus 对照形态（exceptionally→rc.fail 无 syncGet）逐行吻合；"11 根内其余 syncGet 无"独立 grep 复现一致。家族关联：WI 4.3 MA-03（sidecar get(30s) 阻塞 IO reactor 线程）同族异站异机制，单列零重复。
- **C4-002（保留 P2）**：keepalive 构造即调度（JsonRpcWebSocketHandler.java:82-83）、取消双路径（:114-116/:126-128）、装配滞留窗口与 Quarkus extractor 不对称（:70 getBeanByType vs Spring :74 tryGet）全部实测吻合。
- **C4-003（保留 P2 + 后果面补强）**：窗口存在性由启动序+代码注释自证；复核补强——窗口内 Quarkus 认证请求实际后果为悬挂而非 500（C6-007 (b) 面同根延伸），修复该条时应与 C6-007 的 future 失联面同 diff 评估。

**台账查重**：cra-index 对宿主零先期登记，3+1 条零同站点重复；WI 4.3 MA-02（JDK 取消语义失效）与本维度无同案（宿主无 cancelToken 绑定面）；WI 2.1 CL-021（raw 线程点名）宿主零命中（漂移复查闭合，见 C1 文件同节）。

## 最终保留项（C4，合并 R1+R2）

| 编号 | 标题 | 判级 | 复核裁定 |
|---|---|---|---|
| C4-001 | 过滤链 syncGet 阻塞 servlet 工作线程 | P2 | 保留 |
| C4-002 | WS keepalive 共享 timer 滞留窗口 | P2 | 保留 |
| C4-003 | 启动窗口无就绪门禁 | P2 | 保留（后果面补强） |
| C4-004（R2） | spring-delta 注册回调平台初始化依赖（静默空发现） | P2 | 保留 |
| CL-005 | proceedAsync 双处理器 / filter future 无超时 | CL | 确认 |

R2 深挖（C4-round2.md）：1 条新发现（C4-004 P2）+ 阻塞面穷举/QuarkusIntegration.start 时长核算负结果；C4 终止，不派第 3 轮。
