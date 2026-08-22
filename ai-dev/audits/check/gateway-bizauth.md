# gateway-bizauth 实现代码检查报告

- 检查日期: 2026-08-19
- 模块路径: nop-service-framework/{nop-gateway,nop-biz-auth-api,nop-biz-auth-core,nop-biz-file-core}
- 文件数: 实际 161（nop-gateway 53、nop-biz-auth-api 28、nop-biz-auth-core 63、nop-biz-file-core 17，均为 src/main/java；任务描述中的 186 与实际不符）
- 覆盖范围声明:
  - **深读（逐行）**: biz-auth-core 的 jwt/filter/login/mfa/totp/password/model 全部手写类；gateway 的 http/impl/core(executor/interceptor/streaming/context)/conversion 全部手写类与 beans.xml；file-core 全部 17 个文件；biz-auth-api 的接口（LoginApi/SiteMapApi）、utils、mfa 契约。
  - **跳过**: 各模块 `model/_gen/` 与 `_*.java` 生成文件（仅读手写子类部分）；biz-auth-api 的 messages 数据 bean 仅抽查（纯 @DataBean 无逻辑）。
  - **行为链验证**: 为确认问题可触达，额外只读了外部类 `ApiResponse`/`JsonParser`/`NopException`（nop-kernel）、`VertxHttpServerContext`（nop-quarkus）、`HttpRequest`（nop-http-api）、`LoginServiceImpl` 的 SSO 分支与 `auth-service.beans.xml`（nop-auth），均未修改。
  - 测试代码（src/test）与 target/ 不在范围。除本报告外未修改任何文件。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 1 |
| P1 | 5 |
| P2 | 9 |
| P3 | 9 |

## 发现列表

### [P0] 网关认证拦截器默认装配硬编码公开测试 API Key

- **文件**: `nop-service-framework/nop-gateway/src/main/resources/_vfs/nop/gateway/beans/gateway-defaults.beans.xml:62-69`
- **维度**: D5（鉴权绕过）
- **证据**:
```xml
<bean id="nopAiAuthGatewayInterceptor"
      class="io.nop.gateway.core.interceptor.AiAuthGatewayInterceptor"
      ioc:default="true">
    <property name="validKeys">
        <list>
            <value>sk-test-key-1</value>
            <value>sk-test-key-2</value>
        </list>
    </property>
</bean>
```
- **现状**: `AiAuthGatewayInterceptor` 是网关 API Key 认证拦截器（`onRequest` 中 `validKeys.contains(token)` 不通过即拒绝）。其默认 bean 定义把两个写死在开源源码中的 key `sk-test-key-1`/`sk-test-key-2` 配置为有效凭证，且 `ioc:default="true"` 使未显式覆盖的应用直接继承该配置。
- **风险**: 任何应用引用该拦截器（gateway 模型 interceptor 的 bean 引用）而未覆盖 validKeys，等于对外发布公开已知的管理员级凭证——任何人持 `Authorization: Bearer sk-test-key-1` 即可通过网关认证。这是 CWE-798（硬编码凭证）落在**认证组件默认值**上的直接利用路径。
- **建议**: 默认装配移除硬编码 key，改为从加密配置（`@InjectValue("@cfg:..."）)读取，未配置时 fail-fast（启动报错）而非带弱默认值启动；至少应将 validKeys 默认置空并使空集合 = 拒绝所有请求。
- **误报排除**: 已确认该 bean 属 `_vfs` 正式装配文件且 `ioc:default="true"`；`AiAuthGatewayInterceptor.onRequest` 确实以该列表为唯一放行依据（见 `AiAuthGatewayInterceptor.java:48`）。

---

> **处置（fix-ai-check 分支，2026-08-22）**: 已修复（fail-closed 方案）。`gateway-defaults.beans.xml` 移除硬编码 key，`validKeys` 改为 `@cfg:nop.gateway.ai-auth.valid-keys|` 从应用配置读取（逗号分隔），未配置时注入空集合 = 拒绝所有请求；`AiAuthGatewayInterceptor.setValidKeys` 将 null 归一为空集合避免 NPE。全仓 grep `sk-test-key` 确认无测试/演示依赖默认 key（既有测试均显式 setValidKeys 自己的 key），无需依赖方改动。测试：`nop-gateway` `AiAuthGatewayInterceptorDefaultAssemblyTest#defaultAssembly_withoutConfig_rejectsFormerlyHardcodedKeys`（修复前：默认装配下带 `sk-test-key-1` 的请求通过认证放行；另 `#defaultAssembly_withConfiguredKeys_honorsConfig` 验证配置 key 生效，`AiAuthGatewayInterceptorTest#nullValidKeys_rejectsAllRequests`/`#emptyValidKeys_rejectsAllRequests` 验证空/null 拒绝所有）。

### [P1] GatewayHttpFilter 在路由匹配前对所有请求体做严格 JSON 解析

- **文件**: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/http/GatewayHttpFilter.java:138-148`
- **维度**: D1（正确性）/ D6（性能）
- **证据**:
```java
return context.getRequestBody().getTextAsync().thenCompose(text -> {
    ApiRequest<?> request = buildRequest(context, text);   // 内部 JSON.parse(text)
    IGatewayContext gatewayCtx = buildGatewayContext(request, context);

    CompletionStage<ApiResponse<?>> future = handler.handle(request, gatewayCtx);
    if (future == null) {
        return next.get();   // 路由不匹配才放行给下游 filter
    }
```
`buildRequest`（同文件 303-310 行）调用 `JSON.parse(requestText)`，为 strictMode 解析（`JsonParser.parseJsonDoc` 对空文本/非 JSON 文本走 `parseLiteral` 抛 `ERR_JSON_UNEXPECTED_CHAR`，已读 nop-kernel JsonParser.java:160-176 验证）。
- **现状**: filter 是全局 `IHttpServerFilter`，只要 gateway 模型存在且配置了路由，**所有**经过的请求（包括不匹配任何网关路由、本应 `next.get()` 交给下游处理的请求）都会先把 body 读成文本并按严格 JSON 解析。GET/DELETE 无 body（Vert.x `body()` 返回空 Buffer → 空字符串）、multipart 文件上传、表单提交、二进制 body 全部解析失败。
- **风险**: 1) 网关 filter 与任何非 JSON 端点（文件上传、表单、REST GET 路由）无法共存，失败发生在路由判定之前，且异常在 thenCompose 异步段抛出、不进本方法 catch 的 `writeErrorResponse` 兜底；2) 全量 body 读入 String，大请求内存翻倍（getTextAsync 文本化也会破坏二进制转发）。作者注释 "No gateway route configured, skip body read to avoid consuming downstream payload"（132 行）表明意识到了 body 消费问题，但仅处理了"模型无路由"一种情况。
- **建议**: 先做路由匹配（仅用 path/method/query），匹配成功后再读取并解析 body；body 解析失败或路由不匹配时走 `next.get()`；对非 JSON 路由支持原始字节转发。
- **误报排除**: 已验证 `JSON.parse` 空串必抛异常（JsonParser default 分支）；已验证 VertxHttpServerContext.getTextAsync 对无 body 请求返回 ""（VertxHttpServerContext.java:400-408）；filter 挂载为全局 filter chain（beans.xml 无路径限定）。

### [P1] GatewayRejectException 携带的拒绝响应（401/429）无任何消费者，拒绝语义全部丢失

- **文件**: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/interceptor/AiAuthGatewayInterceptor.java:42-44`、`AiRateLimitGatewayInterceptor.java:46-49`、`GatewayRejectException.java`
- **维度**: D4（错误处理）/ D8（契约一致性）
- **证据**:
```java
ApiResponse<?> rejected = ApiResponse.buildSuccess(null);
rejected.setHttpStatus(HttpStatus.SC_UNAUTHORIZED);
throw new GatewayRejectException(rejected);
```
全仓 grep `GatewayRejectException`（排除 import 与定义处）仅 3 处，全部是 throw，无任何 catch。
- **现状**: 异常传播链为 `InterceptedGatewayInvocation.proceedOnRequest` → `NopException.adapt`（RuntimeException 原样上抛）→ `RouteExecutor.execute` catch → `proceedOnError` → 各拦截器默认 `onError`（rethrow）→ `GatewayHandler.processRoute` 的 `exceptionally` 用 `ErrorMessageManager.buildResponseForException` 生成通用错误响应。精心构造的 `rejectionResponse`（含 401/429 状态码与 `Retry-After` 头）被直接丢弃。
- **风险**: 认证失败与限流触发对客户端表现为"系统内部错误"（且经 P2-9 的 200 回退问题，实际 HTTP 状态可能是 200），客户端 SDK 无法凭 401 重新认证、无法凭 429/Retry-After 退避；`GatewayRejectException.rejectionResponse` 字段成为死代码。
- **建议**: 在 `GatewayHandler.processRoute` 的 `exceptionally`（或 `RouteExecutor` 错误路径）中优先识别 `GatewayRejectException`（含 cause 链解包），直接返回 `getRejectionResponse()`。
- **误报排除**: 已逐一读完 `InterceptedGatewayInvocation`、`GatewayRouteExecution`、`IGatewayInterceptor` 默认实现、`GatewayHandler`、`NopException.adapt`，确认链路上无一处还原 rejectionResponse。

### [P1] OAuth code 登录 + redirect 分支不下发 auth cookie、不初始化用户上下文

- **文件**: `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/filter/AuthHttpServerFilter.java:193-208`
- **维度**: D1（正确性）/ D5（认证流程）
- **证据**:
```java
return loginWithOAuthCode(routeContext).thenCompose(userContext -> {
    String accessToken = userContext.getAccessToken();
    AuthToken authToken = loginService.parseAuthToken(accessToken);   // redirect 分支未使用（死代码）
    String redirectUri = routeContext.getQueryParam(AuthCoreConstants.PARAM_REDIRECT_URI);
    if (redirectUri != null) {
        if (!isAllowedRedirectUri(redirectUri)) { throw new NopException(ERR_AUTH_INVALID_REDIRECT_URI); }
        routeContext.sendRedirect(redirectUri);
        return next.get();                                            // 未 addCookie / 未 initUserContext
    }
    return handleUserContext(userContext, routeContext, next, authToken);
});
```
- **现状**: cookie 的下发只存在于 `handleUserContext`（352-359 行：`addCookie(authCookieName(), ...)` 或刷新后写 cookie）。redirect 分支绕过了它：`loginAsync(LOGIN_TYPE_SSO)` 在服务端创建了会话，但响应既没有 `Set-Cookie`，也没有把 token 以任何形式传递给目标页，`initUserContext`/`IUserContext.set` 也未执行。
- **风险**: OAuth/SSO 回调带 `redirect_uri` 参数时，浏览器被重定向后没有任何凭证，下一请求仍未登录 → 再次跳登录页，形成登录循环或静默登录失败。前两行解析 `authToken` 在该分支完全未使用，佐证实现未完成。
- **建议**: redirect 分支在 `sendRedirect` 前补 `addCookie(authCookieName(), accessToken, routeContext)`（或按前端约定以 URL fragment 传递一次性 accessCode）。
- **误报排除**: 已确认 cookie 写入职责在 filter 而非 LoginService（`LoginServiceImpl` 无 addCookie/响应操作，grep 验证）；`handleUserContext` 的 cookie 逻辑不会在此路径执行。

### [P1] IP 维度登录失败计数读写 key 不一致，防爆破永远读到 0

- **文件**: `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/login/AbstractUserContextCache.java:105-117`
- **维度**: D1（正确性）/ D8（契约一致性）
- **证据**:
```java
public int getLoginFailCountForIp(String ip) {
    return ConvertHelper.toPrimitiveInt(loginFailCache.get(userKey(ip)), 0, NopException::new);  // 读 "un:"+ip
}
...
public void setLoginFailCountForIp(String ip, int count) {
    loginFailCache.put(ipKey(ip), count);   // 写 "ip:"+ip
}
public void resetLoginFailCountForIp(String ip) {
    loginFailCache.remove(ipKey(ip));       // 删 "ip:"+ip
}
```
- **现状**: 读用 `userKey(ip)`（前缀 `un:`），写与删除用 `ipKey(ip)`（前缀 `ip:`）。`IUserContextCache` 接口将三者作为配对的公共契约（`loginFailCache` 同时还存用户名计数，键空间 `un:{userName}`）。
- **风险**: 任何按接口契约使用 IP 失败计数的调用方（防爆破锁定、限流）读到的永远是 0——IP 级锁定完全失效；写出的 `ip:{ip}` 条目成为永不读取的死数据。仓库内当前主链路只用了 user 维度（`LoginServiceImpl:333`），但该方法是平台对外 API，按文档接入即中招。
- **建议**: `getLoginFailCountForIp` 改为 `loginFailCache.get(ipKey(ip))`；补一个三方法 round-trip 回归测试。
- **误报排除**: user 维度方法对（`getLoginFailCountForUser`/`setLoginFailCountForUser` 均 `userKey`）对照证明这是笔误而非命名约定。

### [P1] 限流拦截器以可伪造的 X-Forwarded-For 为键，且桶缓存无界

- **文件**: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/interceptor/AiRateLimitGatewayInterceptor.java:21-62`
- **维度**: D5（限流绕过）/ D3（资源耗尽）
- **证据**:
```java
private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

protected String resolveKey(IGatewayContext svcCtx) {
    String clientIp = svcCtx.getRequest().getHeaders() != null
            ? (String) svcCtx.getRequest().getHeaders().get("X-Forwarded-For")
            : null;
    if (clientIp == null) { clientIp = "default"; }
    return clientIp;
}
```
- **现状**: 限流键直接取客户端可任意设置值的 `X-Forwarded-For` 请求头（未取 remote address，也未取 XFF 首跳）；每个 key 在 `buckets` 中创建 `TokenBucket`，无 TTL、无容量上限、无淘汰。
- **风险**: 1) 每个请求随机伪造一个 XFF 值即可获得全新令牌桶，限流完全失效；2) 伪造海量不同 XFF 值使 `buckets` 无限增长，内存耗尽（每个 bucket 为小对象，攻击成本极低）；3) XFF 常为逗号列表（`client, proxy1`），整串作 key 导致同一客户端被拆到多个桶。该拦截器默认装配（beans.xml `ioc:default="true"`，capacity=10）。
- **建议**: 键改用服务端观测的 remote IP（仅在信任代理后解析 XFF 首跳）；`buckets` 换用带容量上限与过期淘汰的 `LocalCache`（参照 `LocalUserContextCache` 的做法）。
- **误报排除**: 已确认该类被 `gateway-defaults.beans.xml` 默认注册，且 `onRequest` 经 `InterceptedGatewayInvocation` 在每请求执行。

### [P2] MFA/短信/邮箱验证码的 Local 存储为无界 Map，过期条目仅被动清除

- **文件**: `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/mfa/store/LocalMfaChallengeStore.java:40-41`、`LocalSmsCodeStore.java:40`、`LocalEmailCodeStore.java:40`
- **维度**: D2（资源泄漏/耗尽）
- **证据**:
```java
private final Map<String, Entry> challenges = new ConcurrentHashMap<>();
private final Map<String, AtomicInteger> failCounts = new ConcurrentHashMap<>();
```
（`peek`/`verify`/`consume` 访问时才惰性删除过期条目；`markVerified` 对已过期条目原样保留。）
- **现状**: 三个 Local store 均为无界 `ConcurrentHashMap`，没有容量上限、没有后台清理任务，过期条目只有再次被同 key 访问才删除。对比 `LocalUserContextCache` 使用了带 maximum size 的 `LocalCache`。
- **风险**: `LocalSmsCodeStore`/`LocalEmailCodeStore` 的 `send(key)` 以手机号/邮箱为 key，`LoginApi.sendSmsCode` 是公开 mutation（接口 javadoc 自述"公开访问"），匿名流量对随机伪造号码循环调用即可让 map 线性增长（受上层短信发送成本与频控制约）；`LocalMfaChallengeStore.create` 每次生成新 UUID key，正常流量残留的过期 challenge 永不清理，长期运行缓慢泄漏。三者已在 `auth-service.beans.xml` 注册为 local 默认实现。
- **建议**: 统一改用 `LocalCache`（maximumSize + expireAfterWrite），或增加定期清扫；对 send 入口按 IP 维度频控。
- **误报排除**: 已确认三类的全部读写路径（本报告深读），确认无任何淘汰机制；已确认生产装配存在。

### [P2] 上游调用重试：Retry-After 无上限、非幂等请求盲目重试、重试次数硬编码

- **文件**: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/executor/InvokeProcessor.java:134-135、152-166、203-226`
- **维度**: D1（正确性）/ D2（资源占用）
- **证据**:
```java
if (httpStatus == 429 && retriesLeft > 0) {
    String retryAfter = httpResponse.getHeaders().get("Retry-After");
    long delayMs = parseRetryAfter(retryAfter, retriesLeft);   // Long.parseLong(trimmed) * 1000，无上限
    return delayedFuture(delayMs).thenCompose(v -> invokeUrlWithRetry(...));
}
if (httpStatus >= 500 && retriesLeft > 0) { ... 同样重试 ... }
```
- **现状**: 429/5xx 一律自动重试（`maxRetries=3` 硬编码）；`parseRetryAfter` 直接信任上游 `Retry-After` 秒数（可为任意大值，如 `86400` → 请求挂起 24 小时），`delayedFuture` 无总超时；重试对 POST 等非幂等请求同样执行，上游已产生副作用的失败会被重复提交。
- **风险**: 恶意/异常上游可使网关请求长时间挂起占用连接与内存；5xx 重试导致重复扣费、重复生成等副作用；响应 body 在重试分支未消费（连接池归还需依赖客户端实现）。
- **建议**: 对 Retry-After 设上限（如 min(值, 30s)）；整个重试链路加总 deadline；仅对幂等方法或显式配置 `retryable=true` 的路由重试；maxRetries 提升为配置。
- **误报排除**: 已通读 `invokeUrlWithRetry` 全部分支，确认无上限逻辑与幂等性判断。

### [P2] 上游请求全量复制客户端请求头（含 hop-by-hop 头）

- **文件**: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/executor/InvokeProcessor.java:231-245`；同模式见 `StreamingProcessor.java:224-229`、`AiFailoverGatewayInterceptor.java:89-93`
- **维度**: D1（正确性）/ D5（头注入）
- **证据**:
```java
private HttpRequest buildHttpRequest(String url, ApiRequest<?> request) {
    HttpRequest httpRequest = HttpRequest.post(url);
    if (request.getHeaders() != null) {
        for (Map.Entry<String, Object> entry : request.getHeaders().entrySet()) {
            httpRequest.header(entry.getKey(), entry.getValue());   // 无任何过滤
        }
    }
    httpRequest.setBody(request.getData());
```
- **现状**: 三处构造上游 HTTP 请求时原样复制客户端的全部请求头，包括 `Host`、`Connection`、`Content-Length`、`Transfer-Encoding` 等 hop-by-hop 头以及 `Cookie`（已验证 `HttpRequest.header` 是纯 put，无过滤）。
- **风险**: 复制的 `Host` 可能覆盖客户端实现自动设置的正确值，导致上游虚拟主机路由错乱；`Content-Length` 与重新序列化的 body 长度不一致会造成上游解析失败/挂起；客户端可向下游注入任意头（会话 fixation、内部头伪造如 `X-Internal-*`），违反反向代理最小转发原则。
- **建议**: 建立转发头黑名单（hop-by-hop + Host + Content-Length），或默认白名单转发并对内部头做前缀剥离。
- **误报排除**: 已核对 `HttpRequest.java:128-134`（header 无过滤）与三处调用点，确认均无头清洗。

### [P2] 流式缓冲层在下游零背压时无界累积

- **文件**: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/executor/BufferedStreamingPublisher.java:243-249`
- **维度**: D2（资源耗尽）/ D3
- **证据**:
```java
if (demand > 0) {
    demand--;
    state.forwarded = true;
    subscriber.onNext(item);
} else {
    state.buffer.add(item);    // passed 之后 demand==0 的兜底缓冲，无大小上限
}
```
- **现状**: 上游拉取由映射链"自驱动"（`createMappedPublisher` 每元素 `request(1)`，`AttemptSubscriber.onSubscribe` 注释明确"缓冲层不追加请求"），不受下游 demand 门控。窗口期缓冲有 `bufferSize` 上限，但越窗后若下游（HTTP 响应写端）停止 request（慢客户端/写缓冲满），元素仍持续进入无上限的 `state.buffer`。
- **风险**: 快速生成模型 + 慢消费客户端的场景下内存持续增长；客户端断连但 cancel 信号未达时同样累积。
- **建议**: 为兜底缓冲设上限并在超限时向上游 `cancel()` 或 `onError`（背压失败语义），使上游拉取受下游 demand 驱动。
- **误报排除**: 已通读该类全部同步/取消路径，确认 demand==0 分支无上限；自驱动注释（355 行）确证上游不受门控。

### [P2] AiFailover 拦截器抛 bare UnsupportedOperationException，maxRetries 配置无效

- **文件**: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/interceptor/AiFailoverGatewayInterceptor.java:42-47`
- **维度**: D4（错误处理）/ D7（平台规范）/ D8
- **证据**:
```java
if (svcCtx.isStreamingMode()) {
    return CompletableFuture.failedFuture(
            new UnsupportedOperationException("Streaming failover not yet implemented"));
}
```
- **现状**: 1) 直接抛 JDK 裸异常（无 ErrorCode、无 `.param`），违背平台"框架核心用 NopException + ErrorCode"的两档错误策略，最终对客户端表现为未分类系统错误并暴露内部实现细节；2) `maxRetries` 字段有 setter 且 beans.xml 配置了 3，但 `tryInvokeWithFallback` 全程未读取该字段（死配置）。
- **风险**: 流式路由 + failover 拦截器组合直接失败且错误语义混乱；运维按 maxRetries 调参无效。
- **建议**: 定义 `ERR_GATEWAY_STREAMING_FAILOVER_NOT_SUPPORTED` 错误码并使用 NopException；要么实现 maxRetries 逻辑要么删除该配置项。
- **误报排除**: 全文 grep 确认 `maxRetries` 仅出现在字段/setter/beans.xml，无读取点。

### [P2] 重定向白名单前缀匹配无主机边界

- **文件**: `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/filter/AuthFilterConfig.java:205-214`
- **维度**: D5（开放重定向）
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
- **现状**: 绝对 URL 仅做字符串前缀匹配，无 host 边界校验。`AuthHttpServerFilter.isRelativePath` 已严格处理相对路径（DR-1c，含协议相对/反斜杠/控制字符），但配置了 `allowedRedirectPrefixes` 后绝对 URL 走此处。
- **风险**: 配置 `"https://app.example.com"`（不带路径分隔符）时，`https://app.example.com.evil.io` 通过校验形成开放重定向（OAuth 回调后跳钓鱼站窃取后续凭证）；默认配置为空 = 全部拒绝，故仅在显式配置后可触发。
- **建议**: 校验 `URI` 的 scheme+host 与白名单条目精确相等（或前缀以 `/` 结束时校验 host 相等）。
- **误报排除**: 已确认调用点 `AuthHttpServerFilter.processOAuthCode:201`，redirect 前仅经此函数校验。

### [P2] 认证过滤器错误兜底未检查响应已发送，二次 sendResponse 崩溃

- **文件**: `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/filter/AuthHttpServerFilter.java:285-294`
- **维度**: D1（正确性）/ D4
- **证据**:
```java
private void handleError(IHttpServerContext routeContext, Throwable ex, boolean forceJson) {
    if (ex instanceof NopLoginException) {
        NopLoginException err = (NopLoginException) ex;
        if (!routeContext.isResponseSent()) {          // 仅登录异常检查了
            this.responseNotLogin(routeContext, forceJson, ...);
        }
    } else {
        routeContext.sendResponse(500, "Server Error");  // 未检查 isResponseSent
    }
}
```
- **现状**: `next.get()` 链中响应已写出后再抛出非登录异常时，else 分支无条件再次 `sendResponse(500,...)`。已验证 Vert.x 实现 `VertxHttpServerContext.sendResponse`（178-182 行）直接 `routingContext.response().send(body)`，对已结束的响应会抛 `IllegalStateException`。
- **风险**: 流式/部分写入后出错的请求，错误兜底本身崩溃，异常上抛顶替了本应返回给客户端的错误信息，并产生噪音日志。
- **建议**: else 分支同样先判 `isResponseSent()`，已发送时仅记日志。
- **误报排除**: 已读 Vertx 实现确认无内部防重入保护；`isResponseSent` 的存在证明框架层面支持该检查。

### [P2] 文件上传默认跳过对象级鉴权且默认放行全部扩展名

- **文件**: `nop-service-framework/nop-biz-file-core/src/main/java/io/nop/file/core/NopFileStoreBizModel.java:69-72、96-108、124-128`
- **维度**: D5（鉴权/内容安全）
- **证据**:
```java
@InjectValue("@cfg:nop.file.upload.allowed-file-exts|")     // 默认空 = 不限制扩展名
public void setAllowedFileExts(Set<String> allowedFileExts) { ... }

protected void checkUploadAuth(UploadRequestBean record, IServiceContext ctx) {
    if (!StringHelper.isEmpty(record.getBizObjId()) && bizAuthChecker != null) {   // bizObjId 空 = 不检查
        bizAuthChecker.forceCheckAuth(record.getBizObjName(), record.getBizObjId(), ...);
    }
}
```
- **现状**: 1) 上传鉴权以客户端提供的 `bizObjId` 是否非空为前提，客户端不传即完全跳过 `forceCheckAuth`（仅剩登录门槛）；2) `allowedFileExts` 默认空集合 = 任意扩展名（含 `.html`/`.svg`/`.exe`）；3) mimeType 取自客户端 `fileName` 扩展名（`buildUploadRequestBean` → `MediaTypeHelper.getMimeType`），无内容嗅探。
- **风险**: 任意登录用户可上传 HTML/SVG 等主动内容并通过 `download`（`record.getMimeType()` 原样返回）以 `text/html` 下发，若文件链接可公开访问则构成存储型 XSS；无对象级授权使上传面完全开放。
- **建议**: `allowedFileExt` 默认改为白名单（或默认拒绝可执行/主动内容扩展名）；bizObjId 为空时至少要求显式的"公共上传"声明；对文本类型做 content sniffing 或强制 `Content-Disposition: attachment`。
- **误报排除**: 已确认 `upload` 为 `@BizMutation` 公开操作、配置默认值表达式 `|` 后为空；download 的 `isPublic()` 由文件记录数据决定，与上传侧共同构成默认宽策略。

### [P2] 网关错误响应 httpStatus=0 时回退为 HTTP 200

- **文件**: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/http/GatewayHttpFilter.java:269-272`
- **维度**: D4 / D8（契约）
- **证据**:
```java
protected void writeErrorResponse(IHttpServerContext context, ApiResponse<?> response) {
    int status = response.getHttpStatus();
    if (status == 0) {
        status = HttpStatus.SC_OK;   // 错误响应落到 200
    }
```
- **现状**: `ApiResponse.httpStatus` 缺省 0（业务 status 与 httpStatus 是两个字段）；`ErrorMessageManager.buildResponseForException` 对无 ErrorCodeMapping 的异常（裸 RuntimeException、GatewayRejectException 等）产出的响应 httpStatus=0（已读 `_buildResponse`：`mapping == null ? 0 : ...`），经此回退以 HTTP 200 下发错误体。
- **风险**: 网关内部错误与拦截器拒绝（叠加 P1-3）对 LB/监控/客户端重试逻辑表现为成功响应，按状态码工作的中间件全部失效；与 `AuthHttpServerFilter.writeJsonResponse` 明确 `sendResponse(401)` 的行为不一致。
- **建议**: `writeErrorResponse` 的 0 回退改为 500（正常路径 `writeNormalResponse` 的 0→200 保留）。
- **误报排除**: 已核对 `ApiResponse.getHttpStatus` 直读字段（缺省 0）与 `ErrorMessageManager._buildResponse` 的 0 分支。

### [P3] AiAuthGatewayInterceptor：非常量时间比较、日志泄露完整 token、skip 前缀过宽

- **文件**: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/interceptor/AiAuthGatewayInterceptor.java:47-67`
- **维度**: D5
- **证据**:
```java
String token = authHeader.substring(7).trim();
if (!validKeys.contains(token)) {
    LOG.warn("Invalid API key: {}", token);   // 完整 key 明文进日志
    ...
private boolean isSkipPath(String path) {
    for (String pattern : skipPathPatterns) {
        if (path.startsWith(pattern) || path.equals(pattern)) {  // /api/open 误跳过 /api/open-anything
```
- **现状**: `ArrayList.contains`（equals 逐字节短路）比较密钥；无效 key 全文打日志；skip 路径用 startsWith 前缀。
- **风险**: 理论上的时序侧信道、日志中的敏感凭证（含被误传的真实 key）、跳过范围意外扩大。
- **建议**: 常量时间比较（`MessageDigest.isEqual`）；日志只输出 key 摘要/前后缀；skip 匹配用段边界或通配符语义。
- **误报排除**: 直接读源码确认，无其他调用方缓解。

### [P3] OAuth state cookie 比较非常量时间

- **文件**: `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/filter/StateCookieHelper.java:88-94`
- **维度**: D5（CSRF）
- **证据**:
```java
// 安全的state比较
if (!receivedState.equals(storedState)) {
```
- **现状**: 注释自称"安全的state比较"，实为普通 `equals`（短路逐字节）。
- **风险**: 理论时序攻击逐字节猜测 state；实际利用难度高（需同进程测量 + cookie 一次性），故 P3。
- **建议**: 改用 `MessageDigest.isEqual(receivedState.getBytes(...), storedState.getBytes(...))`，使注释成立。
- **误报排除**: 直接确认无其他比较路径。

### [P3] SHA256PasswordEncoder：单轮快速哈希 + 非常量时间比较

- **文件**: `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/password/SHA256PasswordEncoder.java:20-27`
- **维度**: D5
- **证据**:
```java
public String encodePassword(String type, String salt, String password) {
    return StringHelper.sha256Hash(password, salt);
}
public boolean passwordMatches(String type, String salt, String password, String encodedPassword) {
    return StringHelper.sha256Hash(password, salt).equals(encodedPassword);
}
```
- **现状**: 无迭代的 SHA-256 作为可选密码编码器；比较用 equals。
- **风险**: 若被选为主编码器，GPU 暴破成本远低于 BCrypt（平台已提供 `BCryptPasswordEncoder`）。作为遗留兼容编码器存在可接受。
- **建议**: 文档/javadoc 标注"仅用于遗留系统迁移，新口令应使用 BCrypt"。
- **误报排除**: 已确认 BCrypt 编码器同包存在且实现正确。

### [P3] isUserInAnyRole(null) 语义为"放行"且接口未文档化

- **文件**: `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/login/UserContextImpl.java:334-342`
- **维度**: D5 / D8
- **证据**:
```java
public boolean isUserInAnyRole(Collection<String> roleIds) {
    if (roleIds == null)
        return true;    // null = 无限制
```
- **现状**: 角色集合为 null 直接返回 true（fail-open）。`IUserContext.isUserInAnyRole` 接口无该语义说明。
- **风险**: 平台主鉴权路径（`GraphQLActionAuthChecker.isAllowAccess` 等）调用前已判空（已核实），但第三方/后续调用方从可空来源取角色列表传入 null 时会静默放行。
- **建议**: 接口 javadoc 明确"null/空集合语义"；考虑 null 时抛异常或返回 false 并在主路径显式处理。
- **误报排除**: 已 grep 全部调用方，主路径判空，风险限于契约层面。

### [P3] ChunkFileUploadHandler 三个 API 全部返回 null 的空壳实现

- **文件**: `nop-service-framework/nop-biz-file-core/src/main/java/io/nop/file/core/chunk/ChunkFileUploadHandler.java:17-31`
- **维度**: D8（契约与实现不匹配）
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
- **现状**: 分块上传处理器是纯 stub，配套的 7 个请求/响应 bean 已齐备但无实现；全仓无引用（未装配）。
- **风险**: 当前不可触达（未注册 bean）；若被误当完成品装配，客户端拿到 null 响应。
- **建议**: 删除或补实现；至少标注 `@Deprecated`/TODO 防止误用。
- **误报排除**: grep 全仓确认无引用、beans.xml 无注册。

### [P3] logout 路径 startsWith 前缀匹配且仅删 cookie、不撤销服务端会话

- **文件**: `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/filter/AuthHttpServerFilter.java:296-307`
- **维度**: D5 / D1
- **证据**:
```java
if (routeContext.getRequestPath().startsWith(config.getLogoutUrl())) {
    if (config.getAuthCookie() != null) {
        routeContext.removeCookie(authCookieName());
    }
    return true;
}
```
- **现状**: `/logout-anything` 也会命中 `/logout`（前缀误伤）；登出动作只清客户端 cookie，不调用 `loginService` 的 `doLogout`（会话缓存/JWT 仍有效），请求继续放行给下游。
- **风险**: 登出后 token 泄露仍可用至自然过期；共享机器上"退出"是弱保证。
- **建议**: 路径精确匹配或段边界匹配；logout 时同步 `removeUserContextAsync`（可从 cookie token 解析 sessionId）。
- **误报排除**: 已通读 `_filterAsync`/`handlePublicPath`，确认无会话撤销调用。注：正式登出另有 `LoginApi.logout`（服务端），本条仅针对 URL 触发的这条快捷路径。

### [P3] isAjaxRequest 对 Accept 头做全等匹配

- **文件**: `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/filter/AuthHttpServerFilter.java:533-539`
- **维度**: D1
- **证据**:
```java
header = (String) context.getRequestHeader(IHttpServerContext.HEADER_ACCEPT);
return HttpApiConstants.CONTENT_TYPE_JSON.equals(header);
```
- **现状**: 浏览器典型 Accept 值为 `application/json, text/plain, */*`，全等比较不命中，未登录请求被当作页面请求跳转登录页而非返回 401 JSON。
- **风险**: 前端 SPA 拿到 302/HTML 而非 401 JSON，未登录处理逻辑失效（取决于前端是否总带 X-Requested-With）。
- **建议**: 改为 `contains` 或前缀+参数剥离匹配。
- **误报排除**: 直接读源码确认。

### [P3] 验证码缓存盐 verifyKey 硬编码于源码

- **文件**: `nop-service-framework/nop-biz-auth-core/src/main/java/io/nop/auth/core/login/UserContextConfig.java`
- **维度**: D5
- **证据**:
```java
private String verifyKey = "s**j)KimerhNFH#_>DFLL#(4894M388H4FMNDee--39";
...
String buildVerifyCacheKey(String key) {
    return "vc:" + StringHelper.md5Hash(key + config.getVerifyKey());
}
```
- **现状**: 验证码缓存键的盐为开源仓库中可见的固定字符串。
- **风险**: 盐的作用（防缓存键枚举）弱化；直接危害有限（还需访问进程内缓存），故 P3。
- **建议**: 默认随机生成（进程级）并允许配置覆盖。
- **误报排除**: 已确认该默认值随 `UserContextConfig` 无条件生效。

### [P3] LoggingInterceptor 全量记录 LLM 请求与完整流式输出

- **文件**: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/interceptor/OpenAiDeltaAccumulator.java:66-69` 及 `LoggingInterceptor.java:30-55`
- **维度**: D5（敏感信息）/ D6（日志膨胀）
- **证据**:
```java
Object contentObj = delta.get("content");
if (contentObj instanceof String) {
    contentBuilder.append((String) contentObj);   // 累积完整回答
}
...
logger.logStreamingResponse(accumulator.toMap(), svcCtx);  // 完整 prompt/回答进日志
```
- **现状**: 开启 `nop.gateway.logging.enabled` 后，用户 prompt 与模型完整输出全部落日志（`onStreamStart` 还记录完整请求）。
- **风险**: 敏感业务数据/PII 长期留存在日志系统；长回答造成日志膨胀。默认关闭，故 P3。
- **建议**: 提供截断/脱敏开关（如仅记录长度与摘要）。
- **误报排除**: beans.xml 确认默认注册但受 `nop.gateway.logging.enabled` 条件控制。

## 补充说明（未单列的核查项）

- JWT 实现（`JwtHelper`/`JwtAuthTokenProvider`）整体质量较好：按用途分 KID 派生密钥、签名/过期/issuer/audience/typ 五重校验、legacy 迁移窗口显式受控（默认 0=关闭）、无算法混淆路径（`newVerifier` 按密钥类型固定 MAC/RSA，KID→key 由服务端 map 决定）。`toAuthToken` 对缺失 exp/iat 会 NPE，但被外层 catch 统一转为 `ERR_JWT_INVALID_TOKEN` 拒绝，行为正确，未单列。
- Nop 平台约定核查（D7）：未发现 `@Inject private` 字段、`@Value`、`@Autowired`；除 P2-10 的 `UnsupportedOperationException` 与接口 default 方法的 `UnsupportedOperationException`（平台惯例）外无 bare RuntimeException；无空 catch 块；`_gen` 文件未发现手改证据（未逐一比对）。
- `GatewayContextImpl`/`UserContextImpl` 的 synchronized 访问器与 `InterceptedGatewayInvocation` 游标均为每请求新建对象，无跨请求共享竞态。
