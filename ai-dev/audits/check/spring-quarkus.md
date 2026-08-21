# spring-quarkus 实现代码检查报告

- 检查日期: 2026-08-21
- 模块路径: nop-spring + nop-quarkus
- 文件数: 约 55（src/main/java）
- 覆盖范围声明: 两模块全部 50 个 `src/main/java` 文件逐一深读完毕（nop-spring 29 个：core-starter 5 / delta 4 / file 2 / proxy 5 / web-starter 13；nop-quarkus 21 个：core-starter 6 / file 3 / web 10 / web-starter 1 / web-orm-starter 1）。任务所述 55 中另 5 个为 nop-quarkus-grpc 下的 test 文件，按约定不在范围。为验证命中点，额外追读了关联框架代码（`AbstractTransaction`、`FutureHelper`、`BaseContext`/`ContextTaskQueue`、`IocCoreInitializer`、`ContextHttpServerFilter`、`AuthHttpServerFilter`、`HttpServerHelper`、`IoHelper.copy`、`JsonRpcWebSocketHandler`），并反编译本地 `arc-3.22.3.jar` 验证 ArC `Qualifiers.verify` 行为、实测 `ClassLoader.loadClass(null)` 抛 NPE。`src/main/resources`（beans.xml、autoconfig imports）仅作接线验证浏览。未发现 nop-spring / nop-quarkus 下存在需手改的 `_` 前缀文件违规问题。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 1 |
| P2 | 7 |
| P3 | 8 |

## 发现列表

### [P1] ServletHttpServerContext.removeCookie 无 Cookie 请求触发 NPE，登出接口 500

- **文件**: `nop-spring/nop-spring-web-starter/src/main/java/io/nop/spring/web/filter/ServletHttpServerContext.java:147`
- **维度**: D1
- **证据**:
```java
@Override
public void removeCookie(String name) {
    for (Cookie cookie : request.getCookies()) {   // getCookies() 无 cookie 时返回 null
        if (cookie.getName().equals(name)) {
```
- **现状**: `getCookies()` 在请求不携带任何 Cookie 时按 Servlet 规范返回 `null`，此处的增强 for 直接 NPE。同类中 `getCookie(String)`（第 121-129 行）做了 null 判断，唯独 `removeCookie` 漏掉。对比 Quarkus 侧 `VertxHttpServerContext.removeCookie` 走 `routingContext.response().removeCookie(name)`，无此问题。
- **风险**: 真实调用方为 `AuthHttpServerFilter.checkLogoutUrl`（`nop-biz-auth-core` 第 301 行 `routeContext.removeCookie(authCookieName())`）：任何不带 Cookie 访问登出 URL 的请求（会话已过期的用户、安全扫描器、直接 curl）都会以 NPE/500 失败，登出逻辑中断。
- **建议**: 仿照 `getCookie` 增加 `if (cookies == null) return;`（或直接落到末尾的兜底"立即过期 Cookie"分支，与 `removeCookie(name,domain,path)` 行为一致）。
- **误报排除**: 已确认 `request.getCookies()` 的 null 语义（jakarta.servlet 契约）及 `AuthHttpServerFilter` 的真实调用链，非猜测。

### [P2] NopQuarkusBeanContainer.getBeansWithAnnotation 对非 qualifier 注解必然抛 IllegalArgumentException / 恒返回空

- **文件**: `nop-quarkus/nop-quarkus-core-starter/src/main/java/io/nop/quarkus/core/ioc/NopQuarkusBeanContainer.java:125-135`、`nop-quarkus/nop-quarkus-core-starter/src/main/java/io/nop/quarkus/core/ioc/MarkerInterfaceAnnotation.java`
- **维度**: D1、D8
- **证据**:
```java
public Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annClass) {
    Map<String, Object> ret = new HashMap<>();
    InjectableInstance<Object> handles = container().select(Object.class, new MarkerInterfaceAnnotation(annClass));
    for (InstanceHandle<Object> handle : handles.handles()) { ... }
    return ret;
}
```
- **现状**: 把目标注解包装成 `MarkerInterfaceAnnotation` 作为 **qualifier** 传给 CDI `select()`。反编译 arc-3.22.3 `ArcContainerImpl.getResolvedBeans` → `Qualifiers.verify(...)` → `verifyQualifier(annotationType)`：若注解类不在注册 qualifier 集合中直接 `throw new IllegalArgumentException("... is not a qualifier")`。Nop 侧典型查询注解（如 `@BizModel`、`@Order`）都不是 CDI qualifier，因此该调用**抛异常**；即便传入恰好是注册 qualifier 的注解，`MarkerInterfaceAnnotation` 未实现 `equals/hashCode`（违反 Annotation 契约，恒为 identity 相等），ArC 按 qualifier 实例匹配也永远匹配不到任何 bean，返回**空 Map**。两种情况都不会返回"带该注解的 bean"，与 `IBeanContainer.getBeansWithAnnotation` 契约（参见 `BeanFinder` 中 `bean.getBeanClass().isAnnotationPresent(annType)` 的正确语义）不符。
- **风险**: 桥接 API 静默失效：在 Quarkus 宿主下任何经 `BeanContainer.instance().getBeansWithAnnotation(...)` 的按注解发现（如 biz model/事件监听器发现）要么异常要么静默拿空集。当前仓库内未找到生产调用点（仅 nop-ioc 内部父容器委托），故降为 P2 而非 P1。
- **建议**: 改为遍历 `container().beanHandles()`/所有 bean 后按 `bean.getBeanClass().isAnnotationPresent(annClass)` 过滤；若保留 marker 方案至少实现 equals/hashCode，但语义上仍不成立，建议废弃。
- **误报排除**: 已通过 arc-3.22.3 字节码确认 `verify`/`verifyQualifier` 的抛错路径与 `hasQualifiers` 的实例匹配路径；排除"ArC 可能按 annotationType 匹配"的疑点。

### [P2] QuarkusExecutorHelper.withRoutingContext 的 ThreadLocal 上下文在任务被排队路径下丢失，getHeaders()/getParams() 静默返回空（认证头丢失）

- **文件**: `nop-quarkus/nop-quarkus-web/src/main/java/io/nop/quarkus/web/utils/QuarkusExecutorHelper.java:43-67`
- **维度**: D1、D3
- **证据**:
```java
g_serverContext.set(ctx);
try {
    IContext context = ctx.getContext();
    if (context == null) return task.get();
    context.runOnContext(() -> { ... FutureHelper.bindResult(task.get(), future); ... });
    return future;
} finally {
    g_serverContext.remove();     // 任务若被排队到别的线程执行，ThreadLocal 不随之传播
}
```
- **现状**: `BaseContext.runOnContext`（api-core 第 228-243 行）→ `ContextTaskQueue.enqueue`：仅当 `syncing > 0`（同 context 上有其它线程阻塞在 `syncGet`）时返回 true 并把任务交给**等待线程**执行，此时任务运行线程上 `g_serverContext` 为 null。`QuarkusGraphQLWebService.getParams()/getHeaders()`（第 108-127 行）在 `sc == null` 时静默返回 `Collections.emptyMap()`，而 `GraphQLWebService` 基类用 `getHeaders()` 构造请求头（第 86、352 行）——**auth/tenant/locale 等全部请求头与 query 参数丢失**。已验证常规路径（无等待线程）任务内联执行、ThreadLocal 有效，故此问题为条件触发。
- **风险**: 一旦同 context 上存在阻塞的 `syncGet`（例如应用代码把请求 context 传给同步调用线程），后续 REST/GraphQL 请求头静默清空：表现为随机"未登录"/租户头丢失，难排查。
- **建议**: 不依赖静态 ThreadLocal：把 `VertxHttpServerContext` 直接作为参数传入（或挂到 `IContext`/`ApiRequest` 属性），`getParams()/getHeaders()` 从闭包引用取；至少在 `sc == null` 时打 error 日志而非静默空 Map。
- **误报排除**: 已逐行验证 `BaseContext.runOnContext`/`ContextTaskQueue.enqueue/syncGet` 的排队-执行机制；确认 `GraphQLWebService` 对 `getHeaders()` 的消费链。

### [P2] Quarkus WebSocket 端点取真实客户端 IP 的代码为死路径，HEADER_CLIENT_ADDR 永远缺失

- **文件**: `nop-quarkus/nop-quarkus-web/src/main/java/io/nop/quarkus/web/ws/JsonRpcWebSocketEndpoint.java:113-126`（另第 70 行一并说明）
- **维度**: D1
- **证据**:
```java
try {
    IHttpServerContext httpCtx = QuarkusExecutorHelper.getHttpServerContext();
    if (httpCtx != null) { ... clientAddr = ipFetcher.getClientRealAddr(httpCtx); ... }
} catch (Exception expected) {}
```
- **现状**: `g_serverContext` 这个 ThreadLocal 只在 `withRoutingContext(...)` 执行期间被设置（HTTP 请求线程）。WebSocket `onOpen` 运行在 Vert.x WS 线程上，从不经过 `withRoutingContext`，因此 `getHttpServerContext()` **恒为 null**，经 `IClientIpFetcher`（X-Forwarded-For 处理）计算真实客户端 IP 的分支永远不执行。Spring 侧同名端点用 `RequestContextHolder` 在握手 HTTP 请求内取 header，功能正常——这是两侧行为漂移。
- **风险**: Quarkus 宿主下 WS/订阅请求的 `X-Nop-Client-Addr` 缺失（降级为握手 header 原样透传，可能拿到的是代理 IP），影响审计日志、IP 级访问控制。另注：第 70 行 `getBeanByType(IUserContextExtractor.class)` 在无该 bean 时抛异常，而 Spring 侧（第 74 行）用 `tryGetBeanByType`，两侧健壮性不一致。
- **建议**: 改用握手阶段的 `RoutingContext`（`session.getUserProperties()` 或 `config.getUserProperties()` 中 Quarkus/RESTEasy 已放入的 header Map，前面第 90-111 行已在用）构造 `VertxHttpServerContext` 供 `getClientRealAddr` 使用。
- **误报排除**: 已确认 `g_serverContext` 的全部写点仅在 `withRoutingContext` 内（grep 全模块），WS onOpen 无设置路径。

### [P2] Spring 侧 WebSocket 两个开关 key 不一致：默认配置下 /ws/jsonrpc 端点静默不部署

- **文件**: `nop-spring/nop-spring-web-starter/src/main/java/io/nop/spring/web/ws/SpringWebSocketConfig.java:16`、`nop-spring/nop-spring-web-starter/src/main/java/io/nop/spring/web/ws/JsonRpcWebSocketEndpoint.java:45`
- **维度**: D8、D1
- **证据**:
```java
// JsonRpcWebSocketEndpoint —— 默认启用
@ConditionalOnProperty(name = "nop.spring.jsonrpc-websocket.enabled", havingValue = "true", matchIfMissing = true)
// SpringWebSocketConfig —— 默认不启用，且是另一个 key
@ConditionalOnProperty(name = "nop.spring.graphql-websocket.enabled", havingValue = "true", matchIfMissing = false)
public ServerEndpointExporter serverEndpointExporter() { ... }
```
- **现状**: 内嵌容器中 `@ServerEndpoint` 必须经 `ServerEndpointExporter` 扫描注册（Spring Boot 官方约定）。默认配置下 endpoint bean 存在但 exporter 不存在，`/ws/jsonrpc` 实际未注册，连接直接 404；只有显式设置 `nop.spring.graphql-websocket.enabled=true` 才生效——而那个 key 从字面看是"graphql websocket"，与 jsonrpc 端点对不上。
- **风险**: 功能静默缺失（bean 在、端点不在），排障困难；两个 key 漂移易在升级/文档中进一步错位。
- **建议**: 统一为一个开关（endpoint 与 exporter 用同一个 `nop.spring.jsonrpc-websocket.enabled`），或在 exporter 的条件里兼容两个 key。
- **误报排除**: 已核对两文件注解原文与 Spring Boot 对 `ServerEndpointExporter` 的要求（内嵌容器必须显式注册）。

### [P2] NopSpringBeanContainer.getBeanClass 对 @Bean 工厂方法定义的 bean 必抛 NPE

- **文件**: `nop-spring/nop-spring-core-starter/src/main/java/io/nop/spring/core/ioc/NopSpringBeanContainer.java:73-80`
- **维度**: D1
- **证据**:
```java
public Class<?> getBeanClass(String name) {
    String className = context.getBeanFactory().getBeanDefinition(name).getBeanClassName();
    try {
        return ClassHelper.safeLoadClass(className);
    } catch (Exception e) { throw NopException.adapt(e); }
}
```
- **现状**: `@Configuration` 类中 `@Bean` 方法定义的 BeanDefinition 通常经 `setTargetType` 记录类型，`getBeanClassName()` 返回 **null**（Spring Boot 自动配置的绝大多数 bean 都属于此类）。实测 `ClassLoader.loadClass(null)` 抛 NPE（AppClassLoader 并行锁 `ConcurrentHashMap.computeIfAbsent(null)`），被 `NopException.adapt(e)` 包成无参数的 NopException。正确实现应为 `context.getType(name)`。
- **风险**: 该容器作为 nop `BeanContainerImpl` 的 parentContainer 时，`BeanContainerImpl.getBeanClass(name)` 对 Spring bean 的委托调用必然失败且报错信息不可读（NPE 无 message）。当前仓库内无生产直调点，属潜在契约地雷，定 P2。
- **建议**: 改用 `context.getType(name)`；同时对 `NoSuchBeanDefinitionException` 显式转为 `ERR_IOC_UNKNOWN_BEAN_FOR_NAME`。
- **误报排除**: 已用 JDK21 实测 `loadClass(null)` 行为，并确认 Spring `@Bean` 方式下 `getBeanClassName()` 为 null 的常见事实。

### [P2] NopQuarkusBeanContainer 各查找方法不关闭 InstanceHandle，@Dependent 作用域 bean 实例泄漏

- **文件**: `nop-quarkus/nop-quarkus-core-starter/src/main/java/io/nop/quarkus/core/ioc/NopQuarkusBeanContainer.java:59-135`
- **维度**: D2
- **证据**:
```java
public boolean containsBeanType(Class<?> clazz) { return container().instance(clazz).isAvailable(); }
public String findAutowireCandidate(Class<?> beanType) {
    InstanceHandle<?> bean = container().instance(beanType);   // 未 close
    return getQuarkusBeanId(bean);
}
public Object getBean(String name) {
    ... Object bean = quarkusBean == null ? null : Arc.container().instance(quarkusBean).get(); // 未 close
}
for (InstanceHandle<T> handle : Arc.container().select(clazz).handles()) { ret.put(beanId, handle.get()); } // 未 close
```
- **现状**: ArC 的 `InstanceHandle` 契约要求对 `@Dependent` 作用域 bean 用完调用 `close()`，否则依赖实例（及其 `@PreDestroy`/销毁回调）永不释放。桥接容器的 `getBean`/`tryGetBeanByType`/`getBeansOfType`/`findAutowireCandidate`/`containsBeanType`/`getBeansWithAnnotation` 全部创建 handle 后不关闭。对 ApplicationScoped/RequestScoped 无害（normal scoped proxy），仅 `@Dependent` 受影响。
- **风险**: Nop 框架侧每次按名/按类型查找 `@Dependent` bean 都产生一个永不销毁的实例（含其 `@PreDestroy` 资源），长期运行累积泄漏。
- **建议**: `@Dependent` 判定后 `handle.close()`（查找类方法拿到需要的元数据即可关闭），或至少对 `getBeansOfType` 的迭代使用 try-with-resources（`handles()` 返回 `Iterable<InstanceHandle>` 可逐个 close）。
- **误报排除**: 已核对 ArC `InstanceHandle` 的 close 契约（handle 持有 CreationalContext，dependent 实例由其追踪）。

### [P2] VertxHttpServerContext.sendResponse(InputStream)：事件循环上阻塞拷贝、忽略写 Future 无背压、流不关闭

- **文件**: `nop-quarkus/nop-quarkus-web/src/main/java/io/nop/quarkus/web/filter/VertxHttpServerContext.java:185-198`、`:428-443`
- **维度**: D2、D6
- **证据**:
```java
resp.setChunked(true);
try {
    IoHelper.copy(body, new ResponseOutputStream(resp));   // 同步阻塞 copy
    resp.end();
} catch (Exception e) { throw NopException.adapt(e); }

static class ResponseOutputStream extends OutputStream {
    public void write(int b) throws IOException {}          // 单字节写为空实现
    public void write(byte[] b, int off, int len) throws IOException {
        resp.write(Buffer.buffer().appendBytes(b, off, len)); // 忽略返回的 Future
    }
}
```
- **现状**: 三个问题叠加：(1) Vert.x HTTP filter/REST（event loop 线程）调用此方法时，`IoHelper.copy` 同步读 InputStream（可能是文件/网络流）阻塞 event loop，卡住该 loop 上全部请求；(2) `resp.write(buf)` 忽略 Future 且无等待，整个响应体被无背压地缓冲进 Vert.x 内部写队列（大文件 = 全量驻留内存）；(3) `body` 从不 close，调用方传 `FileInputStream` 时句柄泄漏。对比 Servlet 版（`ServletHttpServerContext:203-212`）在 worker 线程同步写、try-with-resources 关闭 `out`（body 同样不关，两侧一致地依赖调用方）。
- **风险**: 大文件下载/网关转发（`GatewayHttpFilter.sendResponse(status, body)` 是 InputStream 变体的真实消费者）在 Quarkus 下导致 event loop 停顿 + 内存峰值 + 句柄泄漏。
- **建议**: 用 `resp.write(buf, handler)` 或 `send(stream)` 做异步分帧并施加写等待（writeQueueFull 时 pause）；`finally` 中 `IoHelper.safeClose(body)`。
- **误报排除**: 已确认 Vert.x Filters 注册的处理器在 event loop 执行、`IoHelper.copy` 为同步循环写（`os.write(buf,0,n)`），以及 ArC/REST 层无自动关闭 body 的机制。

### [P3] Spring/Quarkus 容器 getBeanScope 桥接语义失真（"" / Spring 异常 / Dependent→singleton）

- **文件**: `nop-spring/nop-spring-core-starter/src/main/java/io/nop/spring/core/ioc/NopSpringBeanContainer.java:68-70`、`nop-quarkus/nop-quarkus-core-starter/src/main/java/io/nop/quarkus/core/ioc/NopQuarkusBeanContainer.java:138-151`
- **维度**: D8
- **证据**:
```java
// Spring: 默认 scope 的 bean 返回 ""(SCOPE_DEFAULT)，非 "singleton"；bean 不存在时抛 Spring 的 NoSuchBeanDefinitionException
return context.getBeanFactory().getBeanDefinition(name).getScope();

// Quarkus: @Dependent 映射为 singleton
if (scope == Dependent.class || scope == ApplicationScoped.class) return ApiConstants.BEAN_SCOPE_SINGLETON;
```
- **现状**: Spring 侧对普通单例返回空串而非 `ApiConstants.BEAN_SCOPE_SINGLETON`，Nop 侧按 `"singleton"` 字符串比较的逻辑（如 `BeanDefinition:347`）全部失配；bean 缺失时裸抛 Spring 异常（Quarkus 侧抛规范化的 `ERR_IOC_UNKNOWN_BEAN_FOR_NAME`）。Quarkus 侧把 `@Dependent`（每次 `get()` 新实例）报告为 singleton，Nop 若按 singleton 缓存语义使用会产生"声明单例、实际多实例"的漂移。
- **风险**: 低频路径的契约漂移，当前无生产调用点直接消费。
- **建议**: Spring 侧 `String scope = def.getScope(); return StringHelper.isEmpty(scope) ? BEAN_SCOPE_SINGLETON : scope;` 并规范化异常；Quarkus 侧 `@Dependent` 应返回独立标识或在使用处禁止缓存。
- **误报排除**: 已核对 Spring `AbstractBeanDefinition.SCOPE_DEFAULT = ""` 默认值与两侧源码。

### [P3] SpringWebProxy 共享 WebClientHolder 的 token/cookie 并发读写无同步

- **文件**: `nop-spring/nop-spring-proxy/src/main/java/io/nop/spring/proxy/SpringWebProxy.java:146-157`、`nop-spring/nop-spring-proxy/src/main/java/io/nop/spring/proxy/WebClientHolder.java`
- **维度**: D3
- **证据**:
```java
.doOnNext(response -> {
    String cookie = response.getHeaders().getFirst("set-cookie");
    cookie = HttpCookieHelper.updateCookie(webClient.getCookie(), cookie);  // 读-改-写非原子
    webClient.setCookie(cookie);
    ...
    webClient.setAuthorization("Bearer " + token);                          // 非 volatile 字段
})
```
- **现状**: 同一 provider 的 holder 被多个并发 SSE/REST 请求共享；401 触发 `refreshAccessToken` 时对非 volatile 的 `authorization`/`cookie` 做读改写。并发刷新会把"新 cookie + 旧 token"或交错结果写回，且无 happens-before 保证（Reactor 线程可能读到旧值）。
- **风险**: 并发场景下鉴权头错配、重复刷新风暴；独立小应用、影响面有限。
- **建议**: 字段改 volatile + 对 refresh 做 `compute`/单飞（single-flight）合并。
- **误报排除**: 已确认 holder 无任何同步措施且 `webClients` 为共享 ConcurrentHashMap 缓存（按 provider 复用）。

### [P3] SpringWebProxy 以 INFO 级全量记录代理请求响应内容

- **文件**: `nop-spring/nop-spring-proxy/src/main/java/io/nop/spring/proxy/SpringWebProxy.java:99`、`:120-123`
- **维度**: D5
- **证据**:
```java
.doOnSuccess(text -> LOG.info("nop.rest.recv: {}", text))
...
.doOnEach(signal -> { String text = signal.get(); LOG.info("nop.recv:{}", text); })
```
- **现状**: 代理转发（含 SSE 逐 chunk）的完整响应体以 INFO 级写日志。若上游是 LLM/内部 API，响应含对话内容、token 字段等敏感数据，随日志系统扩散。
- **风险**: 日志层面的数据泄漏与体积膨胀。
- **建议**: 降为 debug 级并截断长度，或提供开关。
- **误报排除**: 两处调用均为无条件 INFO，无配置门控。

### [P3] Vertx ResponseOutputStream.write(int) 空实现：违反 OutputStream 契约的单字节静默丢弃

- **文件**: `nop-quarkus/nop-quarkus-web/src/main/java/io/nop/quarkus/web/filter/VertxHttpServerContext.java:436`
- **维度**: D1
- **证据**:
```java
@Override
public void write(int b) throws IOException {}   // 丢弃
```
- **现状**: 单字节写静默丢数据。当前唯一调用方 `IoHelper.copy` 只用 `write(byte[],int,int)`，暂未触发；但这是私有工具类契约破坏，任何通用流工具（如 `write(b)` 结尾补字节、`PrintStream` 包装）接入即静默截断响应。
- **风险**: 潜伏的数据正确性地雷。
- **建议**: 实现 `write(int b)` 为单字节 buffer 写（或 `throw new UnsupportedOperationException` 显式失败优于静默丢弃）。
- **误报排除**: 已核对 `IoHelper.copy` 现行实现仅批量写，确认当前不可达、定级 P3。

### [P3] JsonRpcWebSocketEndpoint.extractInitialHeaders 空 catch 吞掉全部异常

- **文件**: `nop-spring/nop-spring-web-starter/src/main/java/io/nop/spring/web/ws/JsonRpcWebSocketEndpoint.java:124-125`、`nop-quarkus/nop-quarkus-web/src/main/java/io/nop/quarkus/web/ws/JsonRpcWebSocketEndpoint.java:125-126`
- **维度**: D4
- **证据**:
```java
        } catch (Exception expected) {
        }
```
- **现状**: 握手 header 提取 + 客户端 IP 解析整块被空 catch 吞掉。预期吞的是"无请求上下文"，但真实 bug（类型转换错误、IP 取值 NPE）也被静默吞掉，仅表现为 header 缺失。
- **风险**: 可观测性差；客户端 IP/租户 header 缺失时无任何线索。
- **建议**: 捕获后 `LOG.debug(...)` 记录异常；把"无上下文"的预期分支与异常分支分开。
- **误报排除**: 两侧 catch (Exception) 范围覆盖整个提取块，非单语句防护。

### [P3] QuarkusFileService.getFileName 解析脆弱 + 多文件上传时 break 只退出内层循环

- **文件**: `nop-quarkus/nop-quarkus-file/src/main/java/io/nop/file/quarkus/web/QuarkusFileService.java:62-90`、`:132-143`
- **维度**: D1
- **证据**:
```java
String[] name = filename.split("=");
return name[1].trim().replaceAll("\"", "");   // filename*=UTF-8''… / 文件名含 = 时解析错误

res = uploadAsync(buildApiRequest(request, fileInput));
break; // NOPMD - intentional early exit on first valid file   // 只跳出内层 for，外层 uploadForm.values() 继续扫描并覆盖 res
```
- **现状**: (1) Content-Disposition 解析用简单 split：`filename*=UTF-8''xxx`（RFC 5987，浏览器对非 ASCII 文件名常用）会被解析成乱码名；文件名含 `=` 时被截断（split 无 limit）。(2) 注释声称"第一个有效文件后提前退出"，但 `break` 只退出内层循环，外层继续遍历其余 part，`res` 被后续文件覆盖——多文件表单时前序 `uploadAsync` 已发起（副作用已发生）但响应只回传最后一个文件的结果。
- **风险**: 非 ASCII 文件名/多文件场景的响应不一致与命名错误；异常均有外层 catch 兜底（无 500）。
- **建议**: 解析改用正则捕获 `filename="..."` 与 `filename\*=...` 双分支；找到首个文件后用带标签 break 或 return。
- **误报排除**: 已核对循环结构与注释意图的矛盾；split 无 limit 的截断行为为 JDK 语义。

### [P3] NopSpringTransactionFactory：getDialectForQuerySpace 懒初始化无同步、SpringTransaction.txn 非 volatile

- **文件**: `nop-spring/nop-spring-core-starter/src/main/java/io/nop/spring/core/txn/NopSpringTransactionFactory.java:48-54`、`:135-141`
- **维度**: D3
- **证据**:
```java
public IDialect getDialectForQuerySpace(String querySpace) {
    if (dialect == null) {
        this.dialect = DialectManager.instance().getDialectForDataSource(dataSource);  // 非线程安全懒初始化
    }
    return dialect;
}
class SpringTransaction extends AbstractTransaction implements IJdbcTransaction {
    private TransactionStatus txn;   // 非 volatile；doOpen 在打开线程写，doClose/doCommitAsync 可能在其它线程读
```
- **现状**: `dialect` 懒初始化竞态为幂等重复探测（无害）；`txn` 字段在异步事务路径（`commitAsync/rollbackAsync` 经 `doCommitAsync` 在其它线程执行 `doCommit/doClose`）下无 happens-before 保证。
- **风险**: 异步事务模板下的可见性问题（理论上可能对已开启事务误判 null），触发条件窄。
- **建议**: `dialect` 用局部变量+赋值或 synchronized；`txn` 改 volatile。
- **误报排除**: 已核对 `AbstractTransaction.doCommitAsync` 确在 `FutureHelper.futureRun`（executor 线程）中调用 `doCommit`。

### [P3] ZipContentEncodingFilter（两侧）对每个静态资源请求做 classpath exists() 探测

- **文件**: `nop-spring/nop-spring-web-starter/src/main/java/io/nop/spring/web/filter/ZipContentEncodingFilter.java:69-77`、`nop-quarkus/nop-quarkus-web/src/main/java/io/nop/quarkus/web/filter/ZipContentEncodingFilterRegistrar.java:60-68`
- **维度**: D6
- **证据**:
```java
if (path.endsWith(".js") || ...) {
    String gzPath = path + ".gz";
    Resource resource = getResource("META-INF/resources" + gzPath);   // 每请求 ClassLoader 资源查找
    if (resource.exists()) { ... }
}
```
- **现状**: 对每个 `.js/.css/.html/.json` 请求都构造 `ClassPathResource` 并 `exists()`（一次 classloader 查找，JDK 不缓存），探测结果也不缓存。静态资源高频路径上增加无谓开销。
- **风险**: 高 QPS 静态资源场景的 CPU/锁开销（classpath 查找在并行 classloader 下有同步）。
- **建议**: 探测结果按 path 缓存（含 exists=false），启动时预热。
- **误报排除**: 两处实现均为每请求 new + exists()，无缓存字段。

## 汇总说明

- P0 无：未发现可直接导致数据错乱、安全突破或必然崩溃的缺陷。
- 最重的 P1（removeCookie NPE）有真实调用链（登出 URL）与现实触发方式（无 Cookie 请求）。
- P2 中 4 条集中于 Quarkus 桥（注解查找失效、ThreadLocal 上下文丢失、WS 客户端 IP 死路径、InstanceHandle 泄漏）与 2 条 Spring 桥（WS 开关错配、getBeanClass NPE），均已完成跨文件/字节码级验证。
- 未写入报告的存疑点（已排查后放弃）：`NopSpringTransactionFactory.getSynchronization` 的 `(SpringTransaction)` 强转在 registry 残留异源事务时可能 CCE（触发条件未证实）；`NopSpringCoreAutoConfig` 在 `ContextClosedEvent` 中销毁 CoreInitialization 的时序（早于 Spring bean destroy，但 destroy 幂等、实际影响未证实）；`JsonRpcWebSocketHandler` 多线程并发 `getBasicRemote().sendText`（属 nop-graphql-core，非本模块范围）。
