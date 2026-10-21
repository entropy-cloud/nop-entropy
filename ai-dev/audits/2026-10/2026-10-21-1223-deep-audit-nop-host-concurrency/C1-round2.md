# C1 共享可变状态与可见性 — nop-host 第 2 轮深挖（WI 4.4）

- 审计单元：nop-host（宿主集成层）C1 维度深挖轮
- 审计日期：2026-10-21（深挖 round 2，盲审口径）
- 前置：R1 发现 C1-001～C1-006 + CL-001～CL-004（见 `C1-shared-mutable-state.md`），本轮不重复立案。
- 深挖任务简报点名目标：McpServerConfiguration @Bean 状态、NopSpringBeanContainer 包装状态、ZipContentEncodingFilter 压缩状态、webClients CHM 迭代面。

## 深挖结论总览

**本轮 0 条新立案**（编号 C1-007 起备而未用）。四个点名目标经回码逐一验证均为负结果；同族扩展扫描确认 R1 已覆盖全部真实共享可变状态载体。原始并发面极薄的预校准在深挖粒度下依然成立。

## 点名目标验证记录（载体 × 写者 × 读者 × 同步机制 × 判定）

### 1. McpServerConfiguration @Bean 状态 — 负结果

- 站点：`nop-ai/nop-spring-mcp-server/src/main/java/io/nop/spring/mcp/server/McpServerConfiguration.java:7-14`（全文 14 行）
- 验证：`@Configuration` 仅声明 1 个 `@Bean toolCallbackProvider()` → `new GraphQLToolCallbackProvider()`。无实例字段、无 static 可变字段、无注册表。@Bean 方法由 Spring 容器在 singleton 锁内单线程调用一次，返回值经容器发布（安全发布）。provider 的懒初始化字段 `callbacks` 的检查-然后-放入竞态已由 **C1-004** 立案，不重复。
- 补充核对：`getToolCallbacks()` 返回内部数组本身（非副本）——属"C1 步骤 1.5 内部数组逃逸"形态，但数组构造后仅被整体替换、从不原地修改，元素（FunctionToolCallback）为不可变构造，逃逸无撕裂后果；Spring AI MCP 侧对数组的消费（tools/list 并发读）在不安全发布窗口之外等价于 C1-004 已登记面。**不立案。**
- 同模块对照：`SpringMCPServerMain`（:9-12）在 `SpringApplication.run` 前显式 `CoreInitialization.initialize()`，运行期 provider 的平台访问（buildCallbacks→BizObjectManager）发生在初始化完成后，无启动窗口暴露面。

### 2. NopSpringBeanContainer 包装状态 — 负结果

- 站点：`nop-spring/nop-spring-core-starter/src/main/java/io/nop/spring/core/ioc/NopSpringBeanContainer.java:20-34`
- 验证：可变字段仅 `id`（非 final，`setId` 存在）；grep 全仓确认 **setId 无任何调用点**（id 恒为 "spring"，R1 清单表"仅启动期 setter 注入"的表述按深挖实测收紧为"无调用点"）。包装对象 `context` 为 final，全部方法为对 Spring `ConfigurableApplicationContext` 的直接委托（getBean/getBeansOfType/getBeanNamesForType 等），线程安全性由 Spring 容器契约承担。
- 孪生对照：`NopQuarkusBeanContainer`（nop-quarkus-core-starter，115 行）无任何实例字段（getId 返回常量），委托 Arc 容器；`getBeansOfType` 内部新建 HashMap 承载 Arc handles 快照，无共享可变态。**两容器均无可变共享状态，不立案。**
- 注册侧（`BeanContainer.registerInstance` 的静态态归属平台 nop-kernel，按误报校准第 4 条不重审；使用面覆盖语义已并入 C6-003 触发时序 T2）。

### 3. ZipContentEncodingFilter 压缩状态 — 负结果

- 站点：`nop-spring/nop-spring-web-starter/src/main/java/io/nop/spring/web/filter/ZipContentEncodingFilter.java:33-123`；孪生 `nop-quarkus/nop-quarkus-web/src/main/java/io/nop/quarkus/web/filter/ZipContentEncodingFilterRegistrar.java:28-94`
- 验证：两宿主 filter 均**无实例字段**（仅 static final LOG），doFilterInternal 全部局部变量每请求新建（path/gzPath/Resource/wrapper）。"压缩状态"仅存在于 per-request 的 HTTP 响应头（content-encoding/content-type），Spring 侧经 `newResponseWrapper` 抑制 content-type 覆写、Quarkus 侧依赖 reroute 清头后重设——均为请求内单线程（servlet 工作线程 / vertx 事件循环）操作，无跨线程共享。`MimeMappings.DEFAULT`/`MimeMapping` 为只读静态映射。
- 存在性探测成本（`exists()` 每请求）已由 R1 CL-002 立案；IndexHtmlProvider 的缓存行为属平台 nop-web，不重审。**不立案。**

### 4. webClients CHM 迭代面 — 负结果（迭代点为零）

- 站点：`nop-spring/nop-spring-proxy/src/main/java/io/nop/spring/proxy/SpringWebProxy.java:34,160`
- 验证：grep 全模块 `webClients` 仅 2 处命中——字段声明 :34 与 `computeIfAbsent` :160。**size()/keySet()/values()/entrySet()/forEach/遍历点为零**（"synchronized 集合迭代必须在锁内"门禁的 CHM 对应检查：无迭代即无风险面）。唯一写入口 `computeIfAbsent` 原子；条目构造于 bin 锁内（含 loadConfig 磁盘 I/O，属 CL-001 已立案的锁内 I/O 问题，非可见性问题）；holder 内容器凭据对的撕裂写已由 C1-001 立案。
- 键空间上界复核：条目仅对存在有效 `{provider}.cfg.yaml` 的 provider 创建（loadConfig 失败即抛、不入缓存），键空间受 config-dir 文件数约束有界（与 R1 C6 文件配对表判定一致）。**不立案。**

## 同族扩展扫描（F3 口径，R1 立案之外的残留载体排查）

| 载体 | 站点 | 判定 |
|---|---|---|
| `WebClientConfig.headers` Map | WebClientConfig.java:12 | 启动期 `loadConfig` 构建后经 computeIfAbsent 安全发布，运行期只读（`getHeader` 只读）——无写者，合规 |
| `GraphQLToolCallbackProvider.buildCallbacks` 迭代 `bizObj.getOperations()` | GraphQLToolCallbackProvider.java:38 | 惰性触发（C1-004 时序内）；底层 map 为平台 BizObjectManager 快照（整体替换语义），非本单元可变载体 |
| `QuarkusExecutorHelper.makeServerContext` 的 `routingContext.get/put(KEY)` | QuarkusExecutorHelper.java:34-40 | check-then-put 形态，但全部调用方（withRoutingContext / HttpServerFilterRegistrar :79-80）在同一请求的同一事件循环线程上执行（RESTEasy Reactive CompletionStage 端点不上 worker 线程；WS onOpen 不调用 makeServerContext——实测 `extractInitialHeaders` 仅读 TL），单线程内无竞态，合规 |
| `withRoutingContext` runOnContext 回调内的 `IUserContext.get()` + MDC bind/unbind | QuarkusExecutorHelper.java:56-65 | 深挖订正：IUserContext.get 读 `ContextProvider.getContextAttr("userContext")`（IUserContext.java:21-23），runOnContext 期间 BaseContext 绑定 contextHolder，**属性随上下文传播，drain 线程可取到**；MDC unbind 在 finally 内，task.get() 同步抛出路径也执行清理（bindMDC 本体仅 MDC.put 无抛出面）——**无池化线程 MDC 泄漏链，不立案**（与 C1-002 的 g_serverContext 传播缺失区分：该 TL 是显式 ThreadLocal 属性，上下文属性机制不覆盖） |
| Spring `getParams/getHeaders` 的 RequestContextHolder | SpringGraphQLWebService.java:60-95 | attrs==null 时抛 IllegalStateException（fail-fast），与 Quarkus 侧 emptyMap 静默降级（C1-002）形成对照；请求线程访问，无跨线程点，合规 |
| 两 WS 端点 onOpen 装配序列 | Spring/Quarkus JsonRpcWebSocketEndpoint onOpen | userProperties put 与 addMessageHandler 之间的滞留窗口已由 C4-002/CL-007 覆盖（跨维度引用），无新增 C1 载体 |

## 继续判定

**C1 终止，不派第 3 轮。** 依据：(i) 四个点名目标全部负结果且验证到行级；(ii) 同族扩展扫描未发现 R1 清单表（13 载体）之外的新载体；(iii) 11 根的 static 字段/ThreadLocal/CHM/synchronized/AtomicBoolean 五类载体面在 R1+R2 两轮均已穷举（基线 C1a/C1b/C1c 与补充 grep 双重复现）。剩余价值集中在 R1 已立案项的复核与修复，不在新发现。

## 复核结论（任务 B，独立复核员）

R2 深挖 0 条新立案经复核维持——四个点名目标（McpServerConfiguration @Bean / NopSpringBeanContainer 包装状态 / ZipContentEncodingFilter 压缩状态 / webClients CHM 迭代面）的负结果验证全部回码成立：McpServerConfiguration 全文 14 行无状态、`setId` 全仓零调用点（复核实测，较 R1"仅启动期 setter 注入"表述进一步收紧）、两 Zip filter 无实例字段、`webClients` 全模块仅 2 处命中（声明+computeIfAbsent，迭代点为零）。同族扩展扫描中"`IUserContext.get()` 经 ContextProvider 上下文属性随 runOnContext 传播、MDC 无泄漏链"的订正性排除经回码（IUserContext.java:21-23、AuthMDCHelper）确认成立——该排除避免了一次误报。**C1 终止判定维持。**

**C1-006 关联补强（落位说明）**：复核在验证"宿主桥 vs nop-IoC bean"边界时补全了 R1 C1-006 变体 B 的双宿主机制（IocCoreInitializer 覆盖 BeanContainer facade + filter 均为 nop-IoC bean），已记入 `C1-shared-mutable-state.md` 复核节并升 P1——本文件第 1/3 节的"不立案"结论不受影响（两处为无状态/无共享载体验证），但 McpServerConfiguration 相关的 steady-state 解析路径说明以复核节为准（BeanContainer facade 在 IocCoreInitializer 后指向 nop-IoC 容器）。
