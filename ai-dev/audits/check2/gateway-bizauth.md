# gateway-bizauth 实现代码检查报告（check2）

- 检查日期: 2026-08-23
- 模块路径: nop-service-framework 下 nop-gateway + biz-auth-api/core + biz-file-core
- 文件数: 161（src/main/java，含 `_gen` 生成文件 15 个）；扣除生成文件后实际审计 146 个
- 覆盖范围声明: 深读 52 个实现文件（关键类：GatewayHttpFilter/GatewayHandler/RouteExecutor/InvokeProcessor/StreamingProcessor/BufferedStreamingPublisher/ForwardProcessor/MappingProcessor/AiAuth|AiRateLimit|AiFailover 拦截器/JwtHelper/JwtAuthTokenProvider/AuthHttpServerFilter/StateCookieHelper/AuthFilterConfig/AbstractLoginService/AbstractUserContextCache/LocalEmailCodeStore/LocalMfaChallengeStore/TOTPAuthenticator/BCrypt|SHA256|CompositePasswordEncoder/NopFileStoreBizModel/MediaTypeHelper/UploadRequestBean/Gemini|DeepSeekMessageConverter/AuthHelper/beans.xml x3 等），另交叉验证 10 余个外部文件（VertxHttpServerContext、ServletHttpServerContext、ApiMessage、NopException、BaseContext.runOnContext、StringHelper、DaoResourceFileStore、WebContentBean 等）；模式扫描（@Inject private / SimpleDateFormat / bare RuntimeException / printStackTrace / System.exit / 空	catch / 凭证日志）覆盖 100% 非生成文件；未深读区域: biz-auth-api 的 28 个消息/接口类中的 22 个（纯数据类/接口声明，抽读 6 个未见逻辑）、gateway 与 auth-core 的 `_gen` 生成模型类 15 个、BCrypt.java（第三方移植代码，仅抽读）。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 2 |
| P1 | 4 |
| P2 | 5 |
| P3 | 7 |

## 发现列表

### [P0] AiAuthGatewayInterceptor 用混合大小写 key 读取 Authorization 头，生产入口永远取到 null，启用即全量 401

- **文件**: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/interceptor/AiAuthGatewayInterceptor.java:37-46`
- **维度**: D1（正确性）/ D8（契约一致性）
- **证据**:
```java
String authHeader = request.getHeaders() != null
        ? (String) request.getHeaders().get("Authorization")
        : null;

if (authHeader == null || !authHeader.startsWith("Bearer ")) {
    LOG.warn("Missing or invalid Authorization header");
    ApiResponse<?> rejected = ApiResponse.buildSuccess(null);
    rejected.setHttpStatus(HttpStatus.SC_UNAUTHORIZED);
    throw new GatewayRejectException(rejected);
}
```
- **现状**: 网关唯一 HTTP 入口 `GatewayHttpFilter.buildRequest`（GatewayHttpFilter.java:303-310）执行 `request.setHeaders(context.getRequestHeaders())`。两个运行时实现均把 header key 统一小写化：`VertxHttpServerContext.getRequestHeaders`（nop-quarkus-web，VertxHttpServerContext.java:107-115）`String normalized = entry.getKey().toLowerCase(Locale.ENGLISH)`；`ServletHttpServerContext`（nop-spring-web-starter，ServletHttpServerContext.java:103-112）同样小写化。`ApiMessage.getHeaders`（nop-api-core，ApiMessage.java:30-40）返回普通 `TreeMap`（区分大小写），无任何规范化。因此 `get("Authorization")` 在生产链路上永远返回 null。平台自身常量 `IHttpServerContext.HEADER_AUTHORIZATION = "authorization"`（小写）佐证小写是平台约定。
- **风险**: 任何在 gateway model interceptors 中引用 `nopAiAuthGatewayInterceptor`（gateway-defaults.beans.xml:50-54 默认装配）的部署，所有非白名单请求直接 401，AI 网关鉴权功能整体不可用。单测用 `request.setHeaders(Map.of("Authorization", ...))`（AiAuthGatewayInterceptorTest.java:29）混合大小写 key 直接注入，掩盖了该缺陷。
- **建议**: 使用小写常量读取（与 `IHttpServerContext.HEADER_AUTHORIZATION` 一致），或用 `ApiHeaders.getHeader(headers, name)`（ApiHeaders.java:57 有统一的取值入口）并按平台约定小写化；同时修正单测构造方式与生产一致。
- **误报排除**: 已读 GatewayHttpFilter.buildRequest（唯一 `handler.handle` 调用点，全模块 grep 确认无其他入口）；已读 Vertx/Servlet 两个 IHttpServerContext 实现确认小写化；已读 ApiMessage/ApiRequest 确认 headers 为区分大小写的 TreeMap 且 setHeaders 不做 key 规范化；已读测试文件确认测试以混合大小写注入使测试失真。

### [P0] AiRateLimitGatewayInterceptor 用混合大小写 key 读取 X-Forwarded-For，所有限流 key 塌缩为 "default"，全局共享 1 QPS 令牌桶

- **文件**: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/interceptor/AiRateLimitGatewayInterceptor.java:54-62`
- **维度**: D1（正确性）/ D8（契约一致性）
- **证据**:
```java
protected String resolveKey(IGatewayContext svcCtx) {
    String clientIp = svcCtx.getRequest().getHeaders() != null
            ? (String) svcCtx.getRequest().getHeaders().get("X-Forwarded-For")
            : null;
    if (clientIp == null) {
        clientIp = "default";
    }
    return clientIp;
}
```
- **现状**: 与上一条同根因：生产链路 headers key 已被 HTTP 层小写化为 `x-forwarded-for`，`get("X-Forwarded-For")` 永远返回 null，所有请求的限流 key 均为 `"default"`。默认装配参数为 capacity=10 / refillRate=1 / refillIntervalMs=1000（gateway-defaults.beans.xml:56-62）。
- **风险**: 一旦该拦截器被路由引用，整个 AI 网关所有用户、所有路由共享一个每秒补充 1 个令牌、容量 10 的桶 —— 正常多用户流量会互相挤占，服务被错误限流到约 1 QPS，构成自我 DoS；限流功能本身完全失效（无法按客户端区分）。
- **建议**: 改用小写 key 读取（并与可信代理配置联动，见下一条 P1 的 XFF 伪造问题）；单测同样需要修正 header key 构造。
- **误报排除**: 同上一条的证据链（GatewayHttpFilter 唯一入口 + Vertx/Servlet 小写化 + TreeMap 精确匹配）；AiRateLimitGatewayInterceptorTest 存在同源的大小写失真问题（grep 确认其未设置 X-Forwarded-For 头，测的是 default 桶路径）。

### [P1] GatewayRejectException 携带的 429/401 拒绝响应在整条链路上无任何消费者，客户端收到的是通用错误（语义上 5xx）而非设计的 429/401 + Retry-After

- **文件**: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/interceptor/AiAuthGatewayInterceptor.java:43-49`（抛出点）；`nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/GatewayRejectException.java`（定义）
- **维度**: D4（错误处理）/ D8（契约一致性）
- **证据**:
```java
ApiResponse<?> rejected = ApiResponse.buildSuccess(null);
rejected.setHttpStatus(429);
rejected.setHeader("Retry-After", String.valueOf(refillIntervalMs / 1000));
throw new io.nop.gateway.GatewayRejectException(rejected);
```
- **现状**: 全仓库 grep `GatewayRejectException`，除 nop-gateway 内 3 处抛出外无任何 catch/处理者；`getRejectionResponse()` 零调用方。异常传播路径：`InterceptedGatewayInvocation.proceedOnRequest` catch 后 `NopException.adapt(e)`（NopException.java:208-216 对 RuntimeException 原样返回）→ `RouteExecutor.execute` catch → `proceedOnError` 链 → 最终 `FutureHelper.reject` → `GatewayHandler.processRoute` 的 `promise.exceptionally(err -> ErrorMessageManager.instance().buildResponseForException(locale, err))`（GatewayHandler.java:124-128）。`ErrorMessageManager` 不认识 `GatewayRejectException`（其不携带 Nop 错误码），按未知 RuntimeException 生成通用错误响应。
- **风险**: 限流/鉴权拒绝精心构造的 401/429 状态码与 Retry-After 头全部丢失；客户端（尤其按 429/401 语义实现退避的 SDK）拿到的是语义上的服务器内部错误，破坏 API 契约；诊断时误导运维以为是网关内部故障。
- **建议**: 在 `GatewayHandler.processRoute` 的 exceptionally（或 RouteExecutor 错误出口）显式识别 `GatewayRejectException` 并直接返回 `getRejectionResponse()`；或让 GatewayRejectException 携带 Nop ErrorCode。
- **误报排除**: 已全仓库 grep（含 nop-entropy 全模块源码，排除 target）确认无外部处理者；已读 NopException.adapt 确认 RuntimeException 原样透传不被包装；已读 GatewayHandler/RouteExecutor/InterceptedGatewayInvocation/GatewayRouteExecution 的完整错误传播链；已读 ErrorMessageManager 调用点确认无特判逻辑。

### [P1] BufferedStreamingPublisher 在 demand=0 时收到 onComplete 会直接丢弃缓冲区残留元素，流尾部数据丢失

- **文件**: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/executor/BufferedStreamingPublisher.java:263-272`（flushAll）与 `:311-314`（handleComplete）
- **维度**: D1（边界条件）
- **证据**:
```java
private void flushAll(Attempt state) {
    while (!state.buffer.isEmpty() && demand > 0 && !cancelled.get()) {
        demand--;
        state.forwarded = true;
        subscriber.onNext(state.buffer.poll());
    }
}

private void handleComplete(Attempt state) {
    flushAll(state);
    terminate(null);   // demand==0 时 buffer 残留元素被永久丢弃
}
```
- **现状**: `terminate` 置 `attempt = null`、`terminated = true`；此后下游再 `request(n)` 时（`request()` line 144-161）`attempt` 已为 null，不会再触发冲刷。缓冲区中因 demand 不足而未交付的元素随 onComplete 永久丢失。下游驱动方式（GatewayHttpFilter.writeStreamingResponse: onSubscribe 时 request(1)，每个 onNext 交付后由 HTTP 层补 request(1)）意味着稳态 demand 在 0/1 间波动；上游映射链（createMappedPublisher）每元素 request(1) 自驱动、不感知下游 demand，上游推送速度可以超过下游 HTTP 写出速度。
- **风险**: 开启 bufferEnabled（或配置了 retry/lifecycle 监听器，见 StreamingProcessor.java:89-98 的 bufferEngaged 条件）的路由，当上游在下游 demand 尚未恢复时结束（典型：短 SSE 流在缓冲窗口内整体完成、或最后一个 chunk 与 [DONE]/onComplete 紧邻到达），尾部若干元素——通常恰是携带 finish_reason/usage 的最后 chunk——丢失，客户端收到不完整回答且无任何错误信号。违反 Flow 规范"onComplete 前必须交付所有已发出元素"的要求。
- **建议**: handleComplete 时若 buffer 非空且 demand==0，挂起 terminate（记录 pendingComplete 状态），在后续 request() 冲空 buffer 后再向下游发 onComplete。
- **误报排除**: 已通读 BufferedStreamingPublisher 全文（含 request/cancel/handleItem/flushBuffer/terminate/AttemptSubscriber 全部路径）确认 terminate 后无任何补救冲刷路径；已读 GatewayHttpFilter.writeStreamingResponse 与 StreamingProcessor.createMappedPublisher 确认上下游驱动模型如上所述（上游自驱动、下游逐条 request）。

### [P1] AiRateLimitGatewayInterceptor 的令牌桶 Map 无淘汰机制且 key 取自可伪造的 X-Forwarded-For：内存无界增长 + 限流绕过（修复 P0 大小写问题后立即暴露）

- **文件**: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/interceptor/AiRateLimitGatewayInterceptor.java:21,41-52`
- **维度**: D3/D5/D6
- **证据**:
```java
private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();
...
String key = resolveKey(svcCtx);
TokenBucket bucket = buckets.computeIfAbsent(key, k -> new TokenBucket(capacity, refillRate, refillIntervalMs));
if (!bucket.tryConsume()) { ... }
```
- **现状**: `buckets` 只增不减，没有任何过期淘汰（对比 auth-core 的 LocalUserContextCache 有 expireAfterAccess，LocalEmailCodeStore 在 verify 时惰性清理）。key 直接取自客户端可任意伪造的 `X-Forwarded-For` 头（值可以为任意字符串）。
- **风险**: (1) 内存耗尽：攻击者每个请求换一个随机 XFF 值（或正常场景下大量不同客户端 IP），ConcurrentHashMap 与 TokenBucket 永久累积，长期运行后 OOM；(2) 限流绕过：攻击者轮换 XFF 值即可获得无限个独立桶，限流完全失效——而限流正是防 DoS/防滥用的唯一默认防线（配合 AiAuth 拦截器使用时保护的是昂贵的 LLM 上游配额）。
- **建议**: 使用带 TTL 淘汰的缓存（如 LocalCache expireAfterAccess）；key 采用"可信代理解析后的真实 IP"（结合 `CFG_AUTH_TRUST_FORWARDED_TENANT` 类似的可信代理开关，仅信任配置的可信代理列表转发的 XFF），不可信时退化为统一 key；并考虑对 key 做长度/格式校验。
- **误报排除**: 已读该类全文确认无任何淘汰逻辑；已读 beans.xml 默认装配确认该拦截器默认容量参数；XFF 可伪造性为 HTTP 常识且 nop 平台自身在 AuthHttpServerFilter.newSysUserContext（AuthHttpServerFilter.java:265-270）对 X-Forwarded-Tenant 采取了"仅在可信代理开启时读取"的防伪造处理，佐证平台对转发头的威胁模型。

### [P1] AuthFilterConfig.isAllowedRedirectUri 用 startsWith 前缀匹配，可被 `https://trusted.com.attacker.com` 形式绕过（开放重定向）

- **文件**: `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/filter/AuthFilterConfig.java:205-214`
- **维度**: D5
- **证据**:
```java
public boolean isAllowedRedirectUri(String uri) {
    if (this.allowedRedirectPrefixes == null || this.allowedRedirectPrefixes.isEmpty())
        return false;

    for (String prefix : this.allowedRedirectPrefixes) {
        if (uri.startsWith(prefix))
            return true;
    }
    return false;
}
```
- **现状**: OAuth code 回调流程中（AuthHttpServerFilter.processOAuthCode:198-207），redirect_uri 为绝对 URL 时经此方法白名单校验后 `routeContext.sendRedirect(redirectUri)`。校验仅 `uri.startsWith(prefix)`，未做 host 边界校验。同文件的 `isRelativePath`（AuthHttpServerFilter.java:239-258）对协议相对/反斜杠/控制字符做了严格防御，说明该处是有意收紧过的安全边界，但前缀匹配这一环漏了 host 后缀攻击。
- **风险**: 配置了 `allowedRedirectPrefixes = ["https://app.example.com"]` 的部署，攻击者构造 `redirect_uri=https://app.example.com.attacker.com/phish` 即可通过校验，登录凭证（code 换取的 token，processOAuthCode 随后写入 cookie/头）被引导至攻击者站点——OAuth 开放重定向 + 凭证泄漏链。
- **建议**: 校验时解析 URI 的 host 与端口和允许列表精确匹配（或要求 prefix 以 `/` 结尾并匹配到 path 起始处），而非裸字符串前缀。
- **误报排除**: 已读 AuthHttpServerFilter.processOAuthCode/isAllowedRedirectUri/isRelativePath 完整调用链，确认绝对 URL 仅经此方法放行；已确认空配置时返回 false（fail-closed），只有显式配置前缀的部署受影响，故定 P1 而非 P0。

### [P2] AiAuth 拦截器将无效 API Key 明文完整写入 warn 日志

- **文件**: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/interceptor/AiAuthGatewayInterceptor.java:49-50`
- **维度**: D5（凭证泄漏）
- **证据**:
```java
String token = authHeader.substring(7).trim();
if (!validKeys.contains(token)) {
    LOG.warn("Invalid API key: {}", token);
```
- **现状**: 校验失败时把完整 key（Bearer token 原文）写入 warn 级日志。日志常被聚合到 ELK 等广泛可读系统。
- **风险**: 用户粘贴错 key、客户端截断 key、或前缀碰撞的有效 key（如带尾随空格场景 trim 前后差异）都会落日志；一旦泄漏即暴露（部分）凭证，可用于撞库猜测。同文件 `LOG.warn("Rate limit exceeded for key={}", key)`（AiRateLimitGatewayInterceptor.java:45）也打印原始 key（当前因大小写 bug 恒为 "default"，修复后会打印 XFF 原文，风险较低）。
- **建议**: 只记录 key 的前 4 位 + 长度或哈希（参照同模块 StateCookieHelper.maskState 的脱敏实践）。
- **误报排除**: 已读该类全文确认无脱敏处理；对照 StateCookieHelper.maskState（StateCookieHelper.java:146-151）确认平台其它敏感值日志已有脱敏惯例。

### [P2] AuthHttpServerFilter 将无效 auth token 明文完整写入 debug 日志

- **文件**: `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/filter/AuthHttpServerFilter.java:412-417`
- **维度**: D5（凭证泄漏）
- **证据**:
```java
AuthToken authToken = null;
try {
    authToken = loginService.parseAuthToken(token);
} catch (Exception e) {
    LOG.debug("nop.invalid-auth-token:token={}", token, e);
    return null;
}
```
- **现状**: token 解析失败（包括"曾经有效但已过期"的 token）时完整原文进日志。debug 级别虽默认不启用，但生产排障时常临时打开。
- **风险**: 过期 token 仍是凭证（若 enc-key 不变且后端只依赖 token 本身过期判断，旧 token 泄漏后无需再利用；且完整 token 模式便于离线爆破弱 enc-key）。
- **建议**: 记录 token 哈希或前后 4 位脱敏。
- **误报排除**: 已读 parseAuthToken 完整方法与调用链（getAuthToken → parseAuthToken → getUserContextAsync），确认无其它 token 日志路径、此为唯一泄漏点。

### [P2] LocalEmailCodeStore / LocalMfaChallengeStore 条目只在被再次访问时惰性清理，未被访问的过期条目永久驻留（无界内存增长）

- **文件**: `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/mfa/store/LocalEmailCodeStore.java:52-92`；`nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/mfa/store/LocalMfaChallengeStore.java:66-106`
- **维度**: D2/D6（资源泄漏）
- **证据**:
```java
// LocalEmailCodeStore：send 只 put，verify 才可能 remove
codes.put(key, new Entry(code, now + ttlMs));
...
// LocalMfaChallengeStore：create 只 put，peek/consume 才可能 remove
challenges.put(token, new Entry(c, now + ttlMs));
```
- **现状**: 两个 store 都没有后台清理或容量上限；过期条目只有在后续 verify/peek/consume 恰好命中同一 key 时才被删除。验证码/挑战场景下"发出去但从未回来验证"是常态（用户放弃输入、短信未达），这些条目永不回收。对照组：同模块 `LocalUserContextCache` 用 `LocalCache` 带 `expireAfterAccess` 自动淘汰，说明平台有现成机制。
- **风险**: sendCode/create 接口若面向公网（MFA 登录流程通常公开），攻击者可用大量随机邮箱/手机号刷接口，map 无界增长直至 OOM；正常运营下也是慢性泄漏。
- **建议**: 换用带 TTL 淘汰的 `LocalCache`（参照 LocalUserContextCache），或加定时清扫 + 最大容量限制（超出时拒绝新 send）。
- **误报排除**: 已读两个类全文及对应 Config 类，确认无任何清理机制；已读 LocalUserContextCache 确认平台惯例；已读 EmailCodeStore/MfaChallengeStore 接口确认无清理契约由外部承担。

### [P2] InvokeProcessor 重试的 Retry-After 解析无上限，上游返回极大值可让单个请求挂起数小时

- **文件**: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/executor/InvokeProcessor.java:152-157,203-207`
- **维度**: D1（边界条件）
- **证据**:
```java
if (httpStatus == 429 && retriesLeft > 0) {
    String retryAfter = httpResponse.getHeaders().get("Retry-After");
    long delayMs = parseRetryAfter(retryAfter, retriesLeft);
    return delayedFuture(delayMs).thenCompose(v ->
            invokeUrlWithRetry(url, request, invoke, svcCtx, retriesLeft - 1)
    );
}
...
return Long.parseLong(trimmed) * 1000;   // 无上限钳制
```
- **现状**: 上游（或被劫持/被滥用的上游响应）返回 `Retry-After: 86400` 时，网关将请求挂起 24 小时后再重试；HTTP-date 分支同样无上限。延迟期间占用执行资源与客户端连接；期间到达的客户端断连也不取消定时器（delayedFuture 与请求上下文无关联）。
- **风险**: 单个异常上游响应即可把请求线程槽/内存占用放大到小时级；放大后拖垮网关吞吐。
- **建议**: 对 delayMs 设上限（如 min(delayMs, 30s)），并让延迟链路响应客户端断连取消。
- **误报排除**: 已读 invokeUrlWithRetry/parseRetryAfter/delayedFuture 全文确认无钳制与取消机制；已确认 429 分支在 retriesLeft>0 时直接采用该值。

### [P2] AiFailoverGatewayInterceptor 将原始请求全部 header（含 Authorization/API Key）原样转发到 fallback URL，且 maxRetries 配置项完全无效

- **文件**: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/interceptor/AiFailoverGatewayInterceptor.java:24-38,87-95`
- **维度**: D5（凭证外发）/ D1（死配置）
- **证据**:
```java
private List<String> fallbackUrls = new ArrayList<>();
private int maxRetries = 3;          // 全文唯一一处引用，从未在逻辑中使用
...
private CompletionStage<ApiResponse<?>> tryFallbackUrl(String url, ...) {
    HttpRequest httpRequest = HttpRequest.post(url);
    if (request.getHeaders() != null) {
        request.getHeaders().forEach((k, v) -> httpRequest.header(k, v));   // 含 Authorization
    }
```
- **现状**: (1) fallback 请求复制原始请求所有 header——包括发给主上游的 Bearer API Key——到 fallbackUrls 指定的任意 URL。fallback 常是另一家提供商（DeepSeek→OpenAI 语义），主 key 会被发送给不相关第三方。(2) `maxRetries` 有 setter 且 beans.xml 默认装配设为 3（gateway-defaults.beans.xml:70），但 tryInvokeWithFallback/tryFallbackUrl 的重试深度实际由 fallbackUrls.size() 决定，maxRetries 从未参与任何判断——配置项对用户是无效契约。
- **风险**: 凭证外泄给第三方域；运维调整 maxRetries 期望控制重试深度时不生效，行为与配置声明不符。
- **建议**: fallback 转发时剥离/替换 Authorization 等凭证头（由 fallback 配置提供各自的 key）；要么实现 maxRetries 语义（限制总尝试次数），要么删除该配置项避免误导。
- **误报排除**: 已读该类全文确认 maxRetries 无引用；已读 beans.xml 确认默认装配暴露该配置；已读 tryFallbackUrl 确认无 header 过滤。另注意 fallback 递归（429/5xx 时 line 98-103）也绕过了 invoke 拦截链的剩余环节，属语义可议但影响小。

### [P3] ForwardProcessor.forward 对 forward==null 返回 null 的防御分支是 NPE 陷阱

- **文件**: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/executor/ForwardProcessor.java:54-55`
- **维度**: D1
- **证据**:
```java
public CompletionStage<ApiResponse<?>> forward(GatewayForwardModel forward, ...) {
    if(forward == null)
        return null;    // 调用方 RouteExecutor.executeLogic0 直接 .thenCompose 链式使用，null 将 NPE
```
- **现状**: 调用方 `RouteExecutor.executeLogic0`（RouteExecutor.java:152-155）在 `route.getForward() != null` 时才调用，当前为不可达分支；但任何新调用方直接使用 forward(null,...) 会在 thenCompose 处 NPE 且难以定位。
- **风险**: 低（当前死代码），未来重构时易踩坑。
- **建议**: 改为抛 IllegalArgumentException 或返回失败的 CompletionStage。
- **误报排除**: 已读 RouteExecutor.executeLogic0 调用点确认当前不会传 null（故 P3 而非 P1）。

### [P3] MappingProcessor header 过滤/复制的大小写敏感性与 HTTP 层小写约定存在配置漂移风险

- **文件**: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/executor/MappingProcessor.java:64-66,133-144`
- **维度**: D8
- **证据**:
```java
mappedRequest.setHeaders(new TreeMap<>(request.getHeaders()));   // key 已被 HTTP 层小写化
...
Set<String> allowHeaders = mapping.getAllowHeaders();
if (allowHeaders != null && !allowHeaders.isEmpty()) {
    headers.keySet().retainAll(allowHeaders);    // 与配置精确匹配，大小写敏感
```
- **现状**: 运行时 header key 恒为小写（HTTP 层已规范化），而 allowHeaders/disallowHeaders 来自 gateway model XML 配置原样字符串。配置写 `"Content-Type"` 时 retainAll 匹配不到小写的 `content-type`，导致白名单过滤把头全部清掉或黑名单过滤失效。
- **风险**: 模型配置大小写书写不一时静默行为错误（该透传的头被删/该删的头被透传，含 hop-by-hop 安全头）。
- **建议**: filterHeaders 双侧统一小写比较。
- **误报排除**: 已读 mapRequest/mapResponse/filterHeaders 全文与 GatewayHttpFilter.buildRequest 的 headers 来源；未发现模块内有对配置项的规范化（GatewayMessageMappingModel 为生成属性类）。

### [P3] file-core 扩展名白名单大小写不归一，且默认装配不限制扩展名

- **文件**: `nop-service-framework/nop-biz-file-core/src/main/java/io/nop/file/core/NopFileStoreBizModel.java:81-87`；`UploadRequestBean.java:103-106`
- **维度**: D1/D5（弱）
- **证据**:
```java
@JsonIgnore
public String getFileExt() {
    return StringHelper.fileExt(fileName);   // 不做 toLowerCase
}
...
if (allowedFileExts != null && !allowedFileExts.contains(fileExt)) {   // 大小写敏感 contains
```
- **现状**: `StringHelper.fileExt`（StringHelper.java:2855-2865）返回原始大小写；白名单 contains 区分大小写。默认 `@cfg:nop.file.upload.allowed-file-exts|` 为空 = 完全不限制文件类型。
- **风险**: (1) 配置 `jpg` 时用户上传 `a.JPG` 被误拒（可用性问题）；(2) 默认无限制时可直接上传 text/html/svg，配合 download 按上传 mimeType 返回（NopFileStoreBizModel.download:116-121 优先用 record.getMimeType()），若前端 inline 渲染存在存储型 XSS 面（取决于 HTTP 层 Content-Disposition 策略，本模块不可见，故只列弱风险）。
- **建议**: fileExt 统一 toLowerCase 后再比对；默认配置考虑至少排除 html/svg/htm 等可执行渲染类型。
- **误报排除**: 已读 StringHelper.fileExt 实现、beans 装配默认值、download 的 mimeType 选择逻辑；已查 WebContentBean 无 disposition 字段（由上层 http 决定），故 XSS 链路无法在本模块内完全证实，按弱风险表述。

### [P3] MediaTypeHelper.configLoaded 双检加载无同步/非 volatile

- **文件**: `nop-service-framework/nop-biz-file-core/src/main/java/io/nop/file/core/MediaTypeHelper.java:30-41`
- **维度**: D3
- **证据**:
```java
private static boolean configLoaded;      // 非 volatile
...
if (!configLoaded) {
    loadConfig();
    configLoaded = true;
}
```
- **现状**: 多线程首次并发调用 getMediaTypeFromFileExt 时可能重复 loadConfig（幂等，无害），且无 happens-before 的可见性发布；实际容器是 ConcurrentHashMap，读侧最坏拿到旧空表、下次再补。
- **风险**: 极低；最坏是偶发的重复 IO 与短暂查询不到扩展名映射。
- **建议**: 加 volatile 或静态 Holder 类初始化。
- **误报排除**: 已读该类全文确认 loadConfig 幂等（registerMediaTypes 仅 put）且 map 为 CHM，竞态无结构性破坏。

### [P3] ChunkFileUploadHandler 三个 API 全部返回 null 的空实现以正式 API 形态存在

- **文件**: `nop-service-framework/nop-biz-file-core/src/main/java/io/nop/file/core/chunk/ChunkFileUploadHandler.java:17-31`
- **维度**: D8（契约）/ D1
- **证据**:
```java
public StartChunkResponseBean startChunkApi(StartChunkRequestBean request, IServiceContext ctx) {
    return null;
}
public ChunkResponseBean chunkApi(ChunkRequestBean request, IServiceContext ctx) {
    return null;
}
public UploadResponseBean finishChunkApi(FinishChunkRequestBean request, IServiceContext ctx) {
    return null;
}
```
- **现状**: 类注释描述了完整的分片上传语义，但三个方法均为空返回 null。beans.xml 未装配该类（app-file-core.beans.xml 仅注册 NopFileStoreBizModel），当前无调用方。
- **风险**: 若未来被注册为 BizModel 或被误引用，调用得到静默 null（可能 NPE 于下游），而非显式"未实现"错误。
- **建议**: 抛 UnsupportedOperationException 或标注 @Deprecated/移除。
- **误报排除**: 已读全模块 beans.xml 与 grep 确认无装配无调用方（故 P3）。

### [P3] GatewayHttpFilter.writeErrorResponse 对缺失 httpStatus 的错误响应默认写 200

- **文件**: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/http/GatewayHttpFilter.java:269-272`
- **维度**: D4
- **证据**:
```java
protected void writeErrorResponse(IHttpServerContext context, ApiResponse<?> response) {
    int status = response.getHttpStatus();
    if (status == 0) {
        status = HttpStatus.SC_OK;    // 错误响应兜底 200
    }
```
- **现状**: 走 writeErrorResponse 分支的响应若 httpStatus 为 0（ApiResponse 默认值），将以 200 返回错误 body。
- **风险**: 上游构造错误响应但忘记设状态码时，客户端把错误当成功（缓存/重试逻辑误判）。当前主路径 buildResponseForException 均设置状态码，触发面窄。
- **建议**: 错误分支兜底应为 500。
- **误报排除**: 已读 writeErrorResponse 两个调用点（write() 的 else 分支、filterAsync catch），确认正常错误路径的状态码由 ErrorMessageManager 设置，故仅兜底值不当。

### [P3] GatewayInterceptorModel.getOrCreateInterceptor 的实例缓存无同步（幂等竞态，低危害）

- **文件**: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/model/GatewayInterceptorModel.java:23-33`
- **维度**: D3
- **证据**:
```java
public IGatewayInterceptor getOrCreateInterceptor(IGatewayContext svcCtx){
    if(interceptor == null){
        String bean = getBean();
        if(bean != null){
            interceptor = (IGatewayInterceptor) svcCtx.getEvalScope().getBeanProvider().getBean(bean);
        }else{
            interceptor = new ModelBasedGatewayInterceptor(this);
        }
    }
    return interceptor;
}
```
- **现状**: GatewayModel 经 ResourceCacheEntry 跨请求共享，多线程并发首调可能重复创建（bean 获取幂等 / ModelBased 构造幂等），字段无 volatile 存在理论可见性窗口（ModelBasedGatewayInterceptor.model 为 final，final 语义兜底发布安全）。
- **风险**: 极低；偶发多创建一个无状态对象。
- **建议**: volatile + 本地变量，或加载期预构建。
- **误报排除**: 已读 ModelBasedGatewayInterceptor（唯一字段 final）与 GatewayHandler.loadInterceptors 调用点（每请求调用）确认并发场景与幂等性。

## 附注（已排查、未列为发现的疑点）

- `AuthHttpServerFilter.handleUserContext` 的 bindMDC/runOnContext/unbindMDC 时序：经查 `BaseContext.runOnContext`（BaseContext.java:228-240）首次调用在当前线程内联 flush 执行任务，MDC 在业务链执行期间有效，非缺陷。
- `JwtAuthTokenProvider.ensureKeys` 的 HashMap 懒初始化：所有读路径先经过 synchronized ensureKeys()，锁语义建立 happens-before，安全发布成立；encKey 为空时随机 UUID 密钥为文档化的安全默认（重启失效）。
- `NopFileStoreBizModel.loadFileRecord` 潜在 NPE：唯一实现 `DaoResourceFileStore.getFile` 使用 `requireEntityById`（找不到抛异常不返回 null），不成立。
- `AiAuth/AiRateLimit` 拦截器 `(String)` 强转 header 值：Vertx/Servlet 实现均放入单值 String，不成立（真实缺陷是上面 P0 的大小写问题）。
- beans.xml 默认装配凭证检查：`nopAiAuthGatewayInterceptor` 已 fail-closed（`@cfg:nop.gateway.ai-auth.valid-keys|` 默认空 = 拒绝所有，测试 AiAuthGatewayInterceptorDefaultAssemblyTest 佐证曾有的硬编码 key 已移除）；`nop.auth.jwt.enc-key` 默认空 = 随机临时密钥，非硬编码凭证。
- auth-core 上传/下载与路径遍历：fileId 随机生成、bizObjName 经 isValidSimpleVarName 白名单校验、fileName 不参与存储路径（DaoResourceFileStore.newPath），无遍历风险。
