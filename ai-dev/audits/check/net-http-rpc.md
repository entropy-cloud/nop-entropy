# net-http-rpc 实现代码检查报告

- 检查日期: 2026-08-21
- 模块路径: nop-network/{nop-http,nop-rpc}
- 文件数: 约 154（src/main/java）
- 覆盖范围声明:
  - 逐行深读（含跨模块调用链与 beans.xml 装配验证）: 三个 HTTP 客户端实现（Apache/JDK/OkHttp）及其 Helper、SSE 全链路（ServerEventPublisher 两个实现、AbstractServerEventSubscription、LineAsyncDataConsumer、AbstractStreamResponseConsumer、DownloadResponseConsumer）、oauth enhancer 及其配置类、DefaultHttpClientFactory/DelegateHttpClient/DefaultHttpResponse、HttpClientConfig、HttpRequest；RPC 侧的 HttpRpcService、RpcInvocationHandler、ReflectiveRpcService/MultiRpcService、DefaultRpcMessageTransformer/HttpRpcMessageTransformer、RpcServiceProxyFactoryBean、PollingRpcClient/CancellableRpcClient、MessageRpcClient/MessageRpcServer/RpcChannelState/RpcMessageSubscriber/RpcMessageSender/DefaultRpcMessageAdapter、SimpleRpcClient/Server/Factory、AopRpcService(Invocation)/DefaultRpcServiceInvocation、ContextBinder/ApiContextInterceptor/ClientContextRpcServiceInterceptor、Stat/Log 拦截器、FlowControl、RpcTaskMonitor、RpcMethodReference、DefaultApiResponseNormalizer。
  - 通读或快速浏览: http-api 的常量/错误码/接口/ContentType（vendored 自 httpcomponents，未逐行审）、aggregator 三个 SSE 聚合器、GraphQLRequestBuilder、HttpCookieHelper、DefaultClientIpFetcher、CoreInitializationGuardFilter、CompositeX509TrustManager、DefaultHttpInput/OutputFile、FileDownloadSubscriber/HttpInputFileBodyPublisher/MultipartBodyPublisher/ConcatenatedBodyPublisher；rpc-model 的 Api*Model bean 与 proto 解析/生成工具（构建期工具，仅浏览）。
  - 关键行为做了实证/字节码验证: (a) 用本机 JDK21 程序验证 `ScheduledThreadPoolExecutor.schedule(…, -1ms)` 约 2ms 内立即触发（发现 3 的依据）；(b) 反汇编 okhttp 4.12 `Request.Builder.method` 确认 GET+非空 body 抛 `IllegalArgumentException`（发现 7 的依据）；(c) 查看 httpclient5 5.4 的 `SimpleBody` 确认网络响应体为 bytes 路径（发现 6 的依据）。
  - 不在范围: 测试代码、target/ 与 `_` 前缀生成物、nop-netty/nop-socket/nop-vertx 等兄弟模块（仅在需要验证调用契约时引用）。
  - 未发现问题维度的说明: 全模块 grep 未发现空 catch（唯一 `catch (IOException expected) {}` 在关流处，惯例可接受）、`printStackTrace`、bare `new RuntimeException`（错误处理符合 NopException/ErrorCode 两档规范，D7 总体良好）。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 4 |
| P2 | 11 |
| P3 | 5 |

## 发现列表

### [P1] JdkHttpClient 把一切 503 响应误判为连接失败，且丢弃响应体、丢失 cause

- **文件**: `nop-network/nop-http/nop-http-client-jdk/src/main/java/io/nop/http/client/jdk/JdkHttpClient.java:302`（及 `JdkHttpClientHelper.java:35-36`）
- **维度**: D1/D4
- **证据**:
```java
IHttpResponse toHttpResponse(HttpResponse<?> response, boolean ignoreBody) {
    // 某些MAC系统或者JDK版本中连接不上也不会抛出connect异常，而是会返回503错误码
    if (response.statusCode() == HttpStatus.SC_SERVICE_UNAVAILABLE)
        throw new NopConnectException(ERR_HTTP_CONNECT_FAIL);

    DefaultHttpResponse ret = new DefaultHttpResponse();
    ret.setHttpStatus(response.statusCode());
    ...
```
- **现状**: 任何远端服务真实返回 503（网关过载、滚动发布、限流页、维护页——生产环境高频场景）时，平台默认 HTTP 客户端（`nopRawHttpClient` 即 JdkHttpClient，见 `_vfs/nop/http/beans/http-client-jdk.beans.xml`）直接抛 `NopConnectException(ERR_HTTP_CONNECT_FAIL)`，响应体、响应头全部丢弃。`fetchAsync` 与 `downloadAsync`（`toHttpResponse(res, true)`）均受影响。同时 `JdkHttpClientHelper.wrapException` 中 `new NopConnectException(ERR_HTTP_CONNECT_FAIL)` 未携带 cause，真实异常信息也丢失。
- **风险**: (1) 错误分类错误：4xx/5xx 类 HTTP 错误被归类为连接异常，上层按错误类型做的重试/熔断/告警判断全部失真；(2) 503 响应体（往往是错误详情 JSON）被静默丢弃，`HttpRpcService.toApiResponse` 拿不到任何信息，排障困难；(3) JDK HttpClient 实际不返回 503 来表示连接失败（连接失败抛 `ConnectException`），该 workaround 覆盖面过宽。
- **建议**: 删除该分支或仅在其他特征（如无任何响应头）同时成立时才映射；至少保留原始 status/body 并把 cause 传入异常。
- **误报排除**: 已确认该行为无测试固定（TestJdkHttpClient 无 503 用例）；已确认 JdkHttpClient 是 beans.xml 注册的默认客户端，触发路径现实。

### [P1] JDK SSE 订阅取消后：后台线程继续读取流、继续向已取消的订阅者投递 onNext、InputStream 全程不关闭

- **文件**: `nop-network/nop-http/nop-http-client-jdk/src/main/java/io/nop/http/client/jdk/ServerEventPublisher.java:69`、`nop-network/nop-http/nop-http-api/src/main/java/io/nop/http/api/client/AbstractServerEventSubscription.java:134`
- **维度**: D2/D3/D8
- **证据**:
```java
// ServerEventPublisher(jdk) startRequest 返回的是 sendAsync 的 future（响应到达时已完成）
executor().execute(() -> {
    if (isCancelled()) return;
    onStart(status, headers);
    parseEvents(reader);            // 循环 readLine，无关闭、无取消检查
    ...
});
// AbstractServerEventSubscription.cleanup: future.cancel(true) 对已完成的 future 是 no-op
```
`processLine → waitForDemand()` 在 `isCancelled` 后 while 条件立即退出并继续 `subscriber.onNext(...)`。
- **现状**: 用户取消 SSE 订阅（AI 流式生成的典型"停止生成"路径，`ChatServiceImpl`/gateway 均走此链路）时：`cleanup` 只 cancel 已完成的 sendAsync future（no-op）；executor 线程继续 `readLine()` 直到服务端结束，期间每个事件仍触发 `waitForDemand()`（取消后不再阻塞）→ `subscriber.onNext()`，违反 Flow 规范（cancel 后不得再投递）；`response.body()` 的 InputStream 在任何路径（正常完成/异常/取消/非 2xx）均未 close，连接只能靠服务端关闭或 GC 兜底回收。
- **风险**: 取消后仍持续下载全部 token（带宽/费用浪费）、后台连接与线程占用（并发取消多路流时放大）、订阅者在 cancel 后继续收到回调可能破坏上层状态机。
- **建议**: 用 `AtomicBoolean`/`volatile` 循环内检查取消并中断读取（关闭 InputStream 使 readLine 抛异常退出）；`waitForDemand` 返回取消标志并在 `processLine` 据此终止投递；finally 中 close reader。
- **误报排除**: 已通读 `cleanup`/`request`/`waitForDemand` 全部同步逻辑，确认没有任何代码路径会中止 executor 中的读取循环或关闭流；对照 Apache 实现（cancel 会真实 cancel 底层请求）确认这是 JDK 实现特有缺陷。

### [P1] MessageRpcClient 未设置 Timeout 头时以负延迟调度超时任务，请求立即"超时"失败

- **文件**: `nop-network/nop-rpc/nop-rpc-core/src/main/java/io/nop/rpc/core/message/MessageRpcClient.java:71`、`RpcChannelState.java:110`、`DefaultRpcMessageAdapter.java:36`
- **维度**: D1
- **证据**:
```java
long timeout = messageAdapter.getTimeout(request);          // ApiHeaders.getTimeout(msg, -1) → 无头时为 -1
...
CompletionStage<ApiResponse<?>> promise = (CompletionStage<ApiResponse<?>>) channelState.prepareSend(id, request, timeout);
// RpcChannelState.scheduleTimeout:
final Future<?> scheduledFuture = timer.schedule(() -> { ...future.completeExceptionally(new RpcTimeoutException(...)); },
        timeout, TimeUnit.MILLISECONDS);   // timeout = -1
```
- **现状**: 请求未携带 `nop-timeout` 头时，`-1` 被直接传给 `ScheduledThreadPoolExecutor.schedule`，负延迟任务立即到期，挂起 future 几乎立刻以 `RpcTimeoutException` 完成；随后到达的真实响应落入 `onReceiveUnmatched`。已用本机程序实证 `schedule(…, -1ms)` 2ms 内触发；`nop-stream` 的 `StreamControlRpcTransformer` 专门写了"调用方未显式给超时时补默认 30s"的防御逻辑，佐证该缺陷真实存在且下游已被迫绕行。
- **风险**: 任何直接使用 `MessageRpcClient`（RPC-over-MessageService，nop-stream 控制面即此模式）且未显式设置超时头的调用全部立即失败；`ClientContextRpcServiceInterceptor` 仅在 context 有 `callExpireTime` 时才写该头，默认无。
- **建议**: `RpcChannelState.prepareSend`/`scheduleTimeout` 对 `timeout <= 0` 做防御（不调度超时或使用配置的默认超时）。
- **误报排除**: 已核对 `DefaultScheduledExecutor`→`ExecutorHelper.schedule`→JDK executor 的完整链路无任何负值钳制；已实证 JDK 行为。

### [P1] OkHttpClientProvider useSsl=true 默认关闭全部证书与主机名校验（与另两个客户端的安全默认相反）

- **文件**: `nop-network/nop-http/nop-http-client-okhttp/src/main/java/io/nop/http/client/okhttp/OkHttpClientProvider.java:150`
- **维度**: D5
- **证据**:
```java
protected void addSSLConfig(OkHttpClient.Builder builder) {
    if (config.isUseSsl()) {
        X509TrustManager trustManager = this.trustManager != null ? this.trustManager
                : new DisableValidationTrustManager(); //NOSONAR
        ...
        HostnameVerifier verifier = this.hostnameVerifier != null ? this.hostnameVerifier
                : new TrustAllHostnames(); //NOSONAR
```
- **现状**: 配置 `useSsl=true` 且未显式提供 trustManager/hostnameVerifier 时，直接使用空实现的 `DisableValidationTrustManager`（`checkServerTrusted` 为空）和 `TrustAllHostnames`（恒 true）。对比：Apache/JDK 客户端在同条件下使用 `CompositeX509TrustManager` + JDK 默认信任库 + `DefaultHostnameVerifier`（安全默认）。
- **风险**: 用户设置 `useSsl=true` 的本意是启用 TLS，结果得到零校验的 TLS——任意自签/中间人证书均可通过，出站请求（可能携带 oauth token、业务数据）可被拦截解密。三个客户端实现之间默认行为不一致也会诱导配置事故。
- **建议**: 默认使用系统信任库与严格主机名校验；将"信任所有"显式化为独立配置项（如 `ignoreSslCerts`，与 HttpClientConfig 现有字段对齐）。
- **误报排除**: 已读 `DisableValidationTrustManager`/`TrustAllHostnames` 全部实现确认为全放行；已确认触发条件仅为 `useSsl=true`（无其他前置）。

### [P2] Apache SSE 背压 `waitForDemand` 在 IO reactor 线程上无限期 `wait()`，可冻结整个客户端

- **文件**: `nop-network/nop-http/nop-http-client-apache/src/main/java/io/nop/http/apache/LineAsyncDataConsumer.java:61`（经 `ServerEventPublisher.ServerEventConsumer.onLine` → `AbstractServerEventSubscription.waitForDemand:134`）
- **维度**: D3
- **证据**:
```java
// LineAsyncDataConsumer.data() 由 Apache 异步客户端在 reactor 线程上回调
protected void data(CharBuffer src, boolean endOfStream) throws IOException {
    ...
    if (c == '\n') { onLine(buf.toString()); ... }   // → processLine → waitForDemand()
// AbstractServerEventSubscription:
protected synchronized void waitForDemand() {
    while (demand <= 0 && !isCancelled) { wait(); }   // 阻塞 reactor 线程
```
- **现状**: 订阅者若按需 request（而非平台默认的 `Long.MAX_VALUE`），`waitForDemand` 会在 Apache HttpAsyncClient 的 I/O reactor 分发线程上阻塞，直到订阅者补充 demand。reactor 线程默认数=CPU 核数，被阻塞期间同一客户端上所有其他连接（普通 fetch、其他 SSE）全部停摆。
- **风险**: 一个慢消费者（背压限速）可拖死共享客户端上的全部并发请求；属于线程模型层面的放大故障。
- **建议**: SSE 消费改为投递到独立 executor（如 JDK 实现的做法）再背压等待，或在 demand 不足时暂停向 capacityChannel 申请容量（利用 `updateCapacity` 机制做真正的非阻塞背压）。
- **误报排除**: 已确认 `AbstractStreamResponseConsumer.consume` → `dataConsumer.consume` 在 reactor 回调线程执行；已确认平台默认 `fetchStreamAsync` 用 `request(Long.MAX_VALUE)`（故默认路径不触发），评级 P2 而非 P1。

### [P2] DefaultHttpResponse 无 charset 时回退 US-ASCII：非 ASCII 响应体乱码，且与 JDK 客户端默认 UTF-8 不一致

- **文件**: `nop-network/nop-http/nop-http-api/src/main/java/io/nop/http/api/support/DefaultHttpResponse.java:80`（`getBodyAsString`）、`:103`
- **维度**: D1/D8
- **证据**:
```java
bodyAsBytes = bodyAsText.getBytes(charset != null ? charset : StandardCharsets.US_ASCII.name());
...
bodyAsText = new String(bodyAsBytes, charset != null ? charset : StandardCharsets.US_ASCII.name());
```
- **现状**: `ApacheHttpClientHelper.fromSimpleResponse` 仅当响应 Content-Type 显式带 charset 时才设置 charset（网络响应体走 `SimpleBody` bytes 路径，已核实）；此后 `getBodyAsString()` 以 US-ASCII 解码，非 ASCII 字节（如 UTF-8 中文）全部替换为 `?`。而 `JdkHttpClient.toHttpResponse` 在无 charset 时默认 UTF-8（`ret.setCharset(ENCODING_UTF8)`），两个实现行为漂移。`HttpRpcService.toApiResponse` 经 `getBodyAsString` 解析 JSON，同一服务在两个客户端下行为不同。
- **风险**: 经 Apache 客户端访问"Content-Type: application/json"（无 charset，RFC 8259 规定 JSON 默认 UTF-8，这是最常见的生产形态）且含中文/多字节字符的接口时，数据静默损坏。
- **建议**: 统一默认 UTF-8（与 JDK 实现对齐）。
- **误报排除**: 已核实 httpclient5 `SimpleBody` 网络响应为 bytes 路径、`fromSimpleResponse` 不设默认 charset；已确认 JdkHttpClient 设了 UTF-8 默认（差异可复现推导）。

### [P2] OkHttpClientImpl：GET 请求必然抛 IllegalArgumentException，downloadAsync 必然 NPE

- **文件**: `nop-network/nop-http/nop-http-client-okhttp/src/main/java/io/nop/http/client/okhttp/OkHttpClientImpl.java:94`
- **维度**: D1/D8
- **证据**:
```java
String method = request.getMethod();
String json = request.getBody() == null ? "" : JSON.serialize(request.getBody(), config.isPrettyJson());
final RequestBody body = RequestBody.create(MEDIA_TYPE_JSON, json);   // 恒非空
requestBuilder.method(method, body);                                   // GET → okhttp 抛 IAE

public CompletionStage<IHttpResponse> downloadAsync(...) {
    client.newCall(null).enqueue(null);   // 直接 NPE
    return null;
}
```
- **现状**: okhttp 的 `Request.Builder.method` 对 body!=null 且方法不允许 body（GET/HEAD）抛 `IllegalArgumentException("method GET must not have a request body.")`（已反汇编 okhttp 4.12 字节码确认，3.x 同）。该实现为所有请求（含 GET）都构造非空 body，因此 GET 请求 100% 失败；`downloadAsync` 第一行即 `client.newCall(null)` NPE。仓内无任何装配使用该类（grep 全仓仅注释引用），属未完成实现。
- **风险**: 一旦被接入（该类是模块对外公开的 IHttpClient 实现），所有 GET 调用与全部下载调用立即失败。
- **建议**: body 仅在 method 允许或 body 非空时设置（GET 且无 body 传 null）；downloadAsync 未实现应抛 `UnsupportedOperationException` 而非留 NPE 桩。
- **误报排除**: 已反汇编确认 okhttp 校验行为；已确认仓内无使用（严重度因此限 P2）。

### [P2] `IHttpClient.uploadAsync` 三个实现全部静默返回 null；上传链路死代码且 EOF 不关流

- **文件**: `nop-network/nop-http/nop-http-client-apache/.../ApacheHttpClient.java:283`、`nop-http-client-jdk/.../JdkHttpClient.java:353`、`nop-http-client-okhttp/.../OkHttpClientImpl.java:125`、`nop-http-client-jdk/.../HttpInputFileBodyPublisher.java:31`
- **维度**: D8/D2
- **证据**:
```java
// ApacheHttpClient
public CompletionStage<IHttpResponse> uploadAsync(HttpRequest request, IHttpInputFile inputFile, ...) {
    return null;
}
// HttpInputFileBodyPublisher.request(): 读到 EOF → completed=true; subscriber.onComplete();
// —— 全程未调用 inputStream.close()（仅 cancel() 关闭）
```
- **现状**: `IHttpClient.uploadAsync` 是非 default 抽象方法（契约要求实现），三个实现均返回 null，调用方（如 `DelegateHttpClient.uploadAsync` → `onUploadBegin` 后转发）拿到 null 直接 NPE——既非"未实现"声明，也无错误码。配套的 `HttpInputFileBodyPublisher`（唯一按流上传的组件）在正常读完后不关闭 `IHttpInputFile.getInputStream()`（`DefaultHttpInputFile` 返回 FileInputStream），FD 泄漏；且全仓无使用（upload 功能实际未接线）。
- **风险**: 契约违约 + 一旦启用即泄漏/崩溃；`FileDownloadSubscriber` 的取消分支同样不 close `channel`（未使用，同类问题）。
- **建议**: 未实现处统一抛带 ErrorCode 的 `NopException` 或在接口 default 化为 `UnsupportedOperationException`；`HttpInputFileBodyPublisher` 在 EOF 时 close 流。
- **误报排除**: 已读三个实现的 uploadAsync 全文确认均 return null；已读 HttpInputFileBodyPublisher 全文确认 EOF 路径无 close。

### [P2] DelegateHttpClient 未委托 `fetchServerEventFlow`：经 enhancer 包装后客户端静默丢失 SSE 能力

- **文件**: `nop-network/nop-http/nop-http-api/src/main/java/io/nop/http/api/support/DelegateHttpClient.java:14`（对照 `IHttpClient.java:27`、`_vfs/nop/http/beans/http-api.beans.xml`）
- **维度**: D8
- **证据**:
```java
public class DelegateHttpClient implements IHttpClient {
    // 只委托了 fetchAsync / downloadAsync / uploadAsync
    // fetchServerEventFlow 落到接口 default 实现：
    default Flow.Publisher<IServerEventResponse> fetchServerEventFlow(...) {
        throw new UnsupportedOperationException();
    }
```
http-api.beans.xml: `<property name="httpClientEnhancers"><ioc:collect-beans name-prefix="nopHttpClientEnhancer_"/></property>`，oauth 模块无条件注册 `nopHttpClientEnhancer_oauth`。
- **现状**: 只要 classpath 上存在任意 `nopHttpClientEnhancer_` enhancer（如 nop-http-client-oauth，被 nop-rpc-client-demo 引用），默认 `nopHttpClient` bean 就变成 `DelegateHttpClient` 子类包装，`fetchServerEventFlow`/`fetchStream*` 全部抛 `UnsupportedOperationException`——装饰器静默阉割了被包装对象的能力。`ChatServiceImpl`（nop-ai-core:201）与 gateway `StreamingProcessor`（:102）均调用该方法。
- **风险**: 引入 oauth 模块后，凡注入默认 `nopHttpClient` 的流式调用全部报错；且异常是运行时才暴露。
- **建议**: `DelegateHttpClient` 增加 `fetchServerEventFlow` 委托（并在 `onFetchBegin` 处复用 token 注入逻辑）。
- **误报排除**: 已核实接口 default 抛 UOE、DelegateHttpClient 未覆写、beans 装配链（enhancers 非空时才包装）以及下游调用点。

### [P2] JDK 客户端 multipart：Content-Type 不含 boundary 参数（服务端无法解析），且 boundary 为 JVM 级静态值

- **文件**: `nop-network/nop-http/nop-http-client-jdk/src/main/java/io/nop/http/client/jdk/MultipartBodyPublisher.java:16`、`JdkHttpClient.java:200`、`HttpApiConstants.java:65`
- **维度**: D1/D5
- **证据**:
```java
public class MultipartBodyPublisher implements BodyPublisher {
    private static final String BOUNDARY = UUID.randomUUID().toString();   // 全 JVM 唯一一份
// JdkHttpClient.toJdkHttpRequest:
builder.setHeader(HttpApiConstants.HEADER_CONTENT_TYPE, HttpApiConstants.CONTENT_TYPE_FORM_MULTIPART);
// HttpApiConstants: String CONTENT_TYPE_FORM_MULTIPART = "multipart/form-data";   // 无 boundary=...
```
- **现状**: (1) 发出的 Content-Type 是不带 `boundary` 参数的裸常量，RFC 2046/7578 要求 multipart 必须在 Content-Type 中声明 boundary，任何合规服务端（Tomcat/Spring/nginx）都无法解析该请求体——该功能对真实服务器不可用；(2) boundary 是 JVM 级静态随机值，同一进程所有请求共享，且任何接收过一次该客户端 multipart 请求的服务端都可获知该值，用户可控的文件内容可拼接 boundary 伪造额外 part（multipart 走私/解析混淆）。仓内无任何测试或调用使用 multipart。
- **风险**: 功能级不可用 + 边界可预测带来的注入面。
- **建议**: boundary 改为每实例/每请求随机，并在 Content-Type 中输出 `multipart/form-data; boundary=...`。
- **误报排除**: 已读 `MultipartBodyPublisher`、`JdkHttpClient.toJdkHttpRequest/toMultipart`、常量定义与全仓使用情况（无使用、无测试）。

### [P2] MessageRpcClient 丢弃 `sendAsync` 返回的 future：发送失败被吞，调用方空等至超时

- **文件**: `nop-network/nop-rpc/nop-rpc-core/src/main/java/io/nop/rpc/core/message/MessageRpcClient.java:108`
- **维度**: D4
- **证据**:
```java
CompletionStage<ApiResponse<?>> promise = (CompletionStage<ApiResponse<?>>) channelState.prepareSend(id, request, timeout);
...
messageService.sendAsync(topic, request, options);   // 返回值被丢弃
return promise;
```
- **现状**: 若消息发送失败（broker 不可用、topic 不存在、序列化失败），异常只存在于被丢弃的 future 里，无人消费；`promise` 只能等 `RpcChannelState` 超时（或永远挂起，若 timeout<=0 被 P1-3 修复后不调度）。
- **风险**: 发送侧错误被完全掩盖，表现为"莫名超时"，排障困难；发送失败本可立即失败返回。
- **建议**: `sendAsync(...).whenComplete((r, err) -> { if (err != null) channelState.onFailure(id, err); })`（`onFailure` 已存在且语义匹配）。
- **误报排除**: 已读完整 callAsync 确认无任何 err 分支；已确认 `RpcChannelState.onFailure` 可直接复用。

### [P2] RpcTaskMonitor 状态检查失败分支必然 NPE，错误统计与落库成为死代码

- **文件**: `nop-network/nop-rpc/nop-rpc-core/src/main/java/io/nop/rpc/core/monitor/RpcTaskMonitor.java:94`
- **维度**: D1/D4
- **证据**:
```java
service.callAsync(statusMethod, task.getRequest(), cancellable).whenComplete((ret, err) -> {
    ApiResponse<TaskStatusBean> res = RpcHelper.toTaskStatusResponse(ret);   // ret==null 时 NPE
    handleTaskStatus(task, res, err);
});
// RpcHelper.toTaskStatusResponse: Object data = response.getData();  → 对 null 直接 NPE
```
- **现状**: `callAsync` 异常完成时 `ret == null`，`toTaskStatusResponse(null)` 在 `whenComplete` 回调内抛 NPE，`handleTaskStatus(task, res, err)` 永不执行（err 参数形同虚设）：`checkFailCount` 不增长、`statusStorage.saveTaskStatus` 不落库、任务不会被 STOP 清理。回调内异常被 whenComplete 语义吞掉。
- **风险**: 长时任务状态检查失败完全不可见，监控数据缺失、失败任务无法回收。
- **建议**: `if (err != null) { handleTaskStatus(task, null, err); return; }` 再走正常分支。
- **误报排除**: 已核对 `RpcHelper.toTaskStatusResponse` 对 null 的行为（`response.getData()` NPE）；`handleTaskStatus` 自身已能处理 `res==null`，说明错误分支本是设计意图。

### [P2] DefaultRpcMessageTransformer.toRequest 对 ApiRequest 参数硬编码取 `args[0]`

- **文件**: `nop-network/nop-rpc/nop-rpc-core/src/main/java/io/nop/rpc/core/reflect/DefaultRpcMessageTransformer.java:61`
- **维度**: D1
- **证据**:
```java
for (int i = 0, n = method.getArgCount(); i < n; i++) {
    IFunctionArgument argModel = method.getArgs().get(i);
    if (argModel.getRawClass() == ApiRequest.class) {
        req = (ApiRequest<Object>) args[0];    // 应为 args[i]
    } else if ...
```
- **现状**: 循环用 `i` 定位到 ApiRequest 参数位置，取值却硬编码 `args[0]`。当服务接口签名为 `m(ICancelToken, ApiRequest<T>)` 或 ApiRequest 不在首位时，要么 ClassCastException，要么拿到错误参数对象构造请求。
- **风险**: 非 `ApiRequest` 首位签名的方法经 RPC 代理调用时请求内容错误/抛类型异常；服务端 `fromRequest` 也按 `argModels.size()==1` 特判，参数模型本身即脆弱。
- **建议**: 改为 `args[i]`。
- **误报排除**: 纯代码级缺陷（索引不一致），语义明确；当前平台内接口约定 ApiRequest 在首位，故为潜在触发，评 P2。

### [P2] RpcMethodReference.equals 使用 `||` 而非 `&&`，违反 equals/hashCode 契约

- **文件**: `nop-network/nop-rpc/nop-rpc-api/src/main/java/io/nop/rpc/api/RpcMethodReference.java:54`
- **维度**: D1
- **证据**:
```java
public boolean equals(Object o) {
    ...
    return serviceName.equals(other.getServiceName())
            || serviceMethod.equals(other.getServiceMethod());   // 应为 &&
}
```
- **现状**: 同一 service 的任意两个不同 method（`S/a` 与 `S/b`）判定相等，但二者 hashCode 不同——作为 HashMap/HashSet 键时行为紊乱（contains/remove 失真）。
- **风险**: 该类是 `Serializable` 的公开 RPC 方法引用值对象（注释明确定位为可序列化的服务方法标识），一旦被用作集合键或跨进程比较即产生数据错误。仓内当前无消费者（潜在触发）。
- **建议**: 改 `&&`。
- **误报排除**: 确凿的运算符错误；已确认仓内暂无使用方，评 P2。

### [P2] DefaultClientIpFetcher 取 X-Forwarded-For 最左侧值，客户端 IP 可伪造

- **文件**: `nop-network/nop-http/nop-http-api/src/main/java/io/nop/http/api/server/DefaultClientIpFetcher.java:37`
- **维度**: D5
- **证据**:
```java
// X-Forwarded-For: client, proxy1, proxy2 → 取第一个 IP
String[] ips = headerValue.split(",");
for (String ip : ips) {
    String trimmedIp = ip.trim();
    if (isValidIp(trimmedIp)) return trimmedIp;   // 最左（客户端可控段）
```
- **现状**: XFF 最左侧条目来自客户端请求原样透传：攻击者直连或经代理时发送 `X-Forwarded-For: 1.2.3.4`，可信代理追加真实 IP 后链为 `1.2.3.4, real`，本实现返回伪造的 `1.2.3.4`。`isValidIp` 仅排除空/"unknown"，无任何格式校验（非 IP 字符串也放行）。该 bean 是 `ioc:default` 的默认 `nopClientIpFetcher`，quarkus/spring web 层（GraphQL 服务、WebSocket 端点）用其填充客户端地址。
- **风险**: 基于客户端 IP 的限流、审计、访问控制可被任意伪造绕过。
- **建议**: 从右往左取第一个"非可信代理"IP，或提供可信代理跳数配置；`isValidIp` 至少校验 IP 格式。
- **误报排除**: 已确认取值方向（最左）与消费方（web 层注入 clientAddr）；未假设必须带代理部署——直连时该头完全客户端可控，问题依旧。

### [P3] HTTP/RPC 层 INFO 级全量日志：每请求打 URL、全量序列化请求对象，且与客户端的敏感头脱敏约定不一致

- **文件**: `nop-network/nop-rpc/nop-rpc-http/src/main/java/io/nop/rpc/http/HttpRpcService.java:46`、`nop-rpc-core/.../interceptors/LogRpcServiceInterceptor.java:27`
- **维度**: D5/D6
- **证据**:
```java
LOG.info("nop.http.request:url={}", url);                       // 每次 RPC 调用
LOG.info("nop.rpc.invoke:request={}", JSON.serialize(inv.getRequest(), true));  // logBody 开启时全量请求
```
- **现状**: `HttpRpcService` 对每次调用无开关地 INFO 打 URL（可含 query 敏感参数）；`LogRpcServiceInterceptor`（`logBody=true` 时启用）序列化完整 ApiRequest（含 headers，未见 Authorization 脱敏），而 HTTP 客户端侧 `ApacheHttpClient/JdkHttpClient` 均有 `isSecretHeader` 脱敏逻辑——同平台两套标准。
- **风险**: 生产日志量与敏感信息（token/query 参数）泄漏风险。
- **建议**: 降为 DEBUG 或加配置开关；拦截器序列化前对敏感头脱敏（复用 `HttpHelper.isSecretHeader`）。
- **误报排除**: 已确认两处均无日志级别/开关判断（LogRpcServiceInterceptor 仅判 INFO enabled）。

### [P3] AddAccessTokenHttpClientEnhancer：全局锁内同步取 token、不校验 token 响应状态、缓存坏 token、无 expires_in 时每请求刷新、下载路径不加 token

- **文件**: `nop-network/nop-http/nop-http-client-oauth/src/main/java/io/nop/http/client/oauth/enhancer/AddAccessTokenHttpClientEnhancer.java:110`
- **维度**: D3/D4/D6
- **证据**:
```java
synchronized (authTokens) {                       // 所有 provider 共用一把锁
    token = authTokens.get(providerName);
    ...
    Oauth2TokenResponseBean response = fetchToken(client, providerConfig);   // 锁内同步 HTTP
    authTokens.put(providerName, response);
}
// fetchToken: return client.fetch(request, null).getBodyAsBean(Oauth2TokenResponseBean.class);
//   —— 不检查 httpStatus；token 接口非 2xx 时可能解析出空 token 或抛出，坏响应也会被缓存
```
- **现状**: (1) token 刷新在全局 `synchronized (authTokens)` 内做阻塞 HTTP，token 端点慢时所有 oauth 出站请求（全部 provider）排队；(2) `fetchToken` 不检查响应状态，非 2xx 响应被当作 token 缓存，`accessToken=null` 时静默不带鉴权头发送；(3) 响应缺 `expires_in` 时 `isExpired` 恒真（`now-createTime > 0*1000-gap`），退化为每次请求都刷新 token；(4) `EnhancedClient` 只覆写 `onFetchBegin`，`downloadAsync`（走 `onDownloadBegin→onRequestBegin`）不会注入 token。
- **风险**: 高并发/异常 token 端点场景下的吞吐退化与静默鉴权失败。
- **建议**: 校验响应状态并拒绝缓存失败响应；对无 `expires_in` 的响应使用保守默认有效期；token 注入移到统一的 `onRequestBegin`。
- **误报排除**: 已核对 `Oauth2TokenResponseBean.isExpired` 公式、`DelegateHttpClient` 的 begin 钩子分发逻辑（download 不经过 onFetchBegin）。

### [P3] CancellableRpcClient / MessageRpcClient 的 onCancel 回调完成后不从 cancelToken 移除

- **文件**: `nop-network/nop-rpc/nop-rpc-core/src/main/java/io/nop/rpc/core/composite/CancellableRpcClient.java:53`、`message/MessageRpcClient.java:97`
- **维度**: D2/D3
- **证据**:
```java
// CancellableRpcClient：future 完成后没有对应的 removeOnCancel
cancelToken.appendOnCancel(reason -> { if (!done.get()) { ...rpcService.callAsync(cancelMethod, ...); } });
return future;
// 对照 FutureHelper.bindCancelToken / PollingRpcClient 均有 whenComplete→removeOnCancel
```
- **现状**: 每次调用向 cancelToken 追加一个闭包（持有 request/reqId），调用完成后不移除。短生命周期 token 影响小，但长生命周期/复用型 token（任务级、会话级）上闭包持续累积。
- **风险**: 轻度内存增长 + cancel 触发时遍历大量已失效回调。
- **建议**: 仿照 `FutureHelper.bindCancelToken`，在 future whenComplete 里 `removeOnCancel`。
- **误报排除**: 已对照平台内正确写法（同文件族中 PollingRpcClient/FutureHelper 均做移除），确属遗漏。

### [P3] HttpRpcService 的 graphql 响应分支用 `GraphQLResponseBean` 判断请求数据，分支永假

- **文件**: `nop-network/nop-rpc/nop-rpc-http/src/main/java/io/nop/rpc/http/HttpRpcService.java:91`
- **维度**: D8
- **证据**:
```java
boolean graphql = request.getData() instanceof GraphQLResponseBean;   // 请求数据应为 GraphQLRequestBean
if (graphql) { GraphQLResponseBean gql = (GraphQLResponseBean) JSON.parseToBean(null, text, GraphQLResponseBean.class, ...); ... }
```
- **现状**: 请求侧 payload 由 `GraphQLRequestBuilder.build()` 构造为 `GraphQLRequestBean`，`instanceof GraphQLResponseBean` 恒为 false，该解析分支不可达；疑似应为 `GraphQLRequestBean`（笔误级契约漂移）。
- **风险**: 经此路径发送的 graphql 请求会落入普通 ApiResponse 解析并可能被误报"响应格式不符"。
- **建议**: 修正类型或删除死分支。
- **误报排除**: 已全仓搜索确认无任何代码以 `GraphQLResponseBean` 作为请求 data 传入 HttpRpcService。

### [P3] ApacheHttpClient.downloadAsync 的取消只取消 promise，不中止底层传输（与 fetchAsync 不对称）

- **文件**: `nop-network/nop-http/nop-http-client-apache/src/main/java/io/nop/http/apache/ApacheHttpClient.java:252`
- **维度**: D2
- **证据**:
```java
client.execute(SimpleRequestProducer.create(toSimpleRequest(request)),
        new DownloadResponseConsumer(targetFile), newHttpClientContext(request), callback);  // Future 被丢弃
FutureHelper.bindCancelToken(cancelToken, promise);   // 两参版本仅 cancel promise
// 对照 fetchAsync: FutureHelper.bindCancelToken(cancelToken, reason -> future.cancel(false), promise);
```
- **现状**: fetchAsync 保留了 execute 返回的 Future 并在取消时 `future.cancel(false)` 真正中止请求；downloadAsync 丢弃 Future、只取消 CompletableFuture，取消后下载仍在后台继续并持续写目标文件。
- **风险**: 大文件下载取消后带宽与磁盘写入浪费；调用方可能误以为文件已停止写入。
- **建议**: 与 fetchAsync 一致，保留 Future 并用三参 `bindCancelToken`。
- **误报排除**: 已对照 `FutureHelper.bindCancelToken` 两个重载的实现语义（两参版只 cancel future 本身）。

## 附注（检查过程中确认无问题的高风险点）

- 反序列化安全（D5）: RPC 消息统一走 Jackson（`JSON.parseToBean`/`JsonTool`），无 Java 原生反序列化调用点；SimpleRpc 使用 JSON 文本协议。
- bare RuntimeException / printStackTrace / 空 catch（D4/D7）: 全模块 grep 未命中；错误处理基本符合 Nop 两档规范。
- Nop IoC 规范（D7）: 未发现 private 字段注入或注解扫描式 bean 注册；beans.xml 装配（http-api/http-client-jdk/http-client-oauth）符合 `_vfs` 显式定义约定。
- 连接复用（D6）: 三个客户端均为单例长生命周期实例（`@PostConstruct` 启动、内建连接池/复用），未发现每请求新建客户端模式。
- SSRF（D5）: 本两模块不直接消费用户可控 URL（出站 URL 来自代码/配置）；`HttpClientConfig.dnsResolver` 已为上层（nop-ai-toolkit SsrfGuardDnsResolver）预留防护挂点。
