# nop-network 模块深度缺陷审查

> Status: resolved
> Date: 2026-09-05
> Scope: `nop-network/` 全部 6 个子模块组（nop-codec、nop-http、nop-netty、nop-rpc、nop-socket、nop-vertx），以及被 nop-network 代码触发、根因在 `nop-kernel/nop-commons` 的 2 处缺陷
> Conclusion: 全量精读约 150 个源文件后确认 46 项真实缺陷（P0 级 14 项）；本次修复 44 项并补充回归测试，2 项（涉及协议语义/部署形态决策）仅记录待议。

## Context

- 对 nop-network 做逐文件深度审查（4 个并行审查通道 + 主通道逐一复核源码、依赖字节码与调用方），目标是找出会在真实场景产生错误行为的缺陷，而非风格问题。
- 审查方法：先读设计文档（`docs-for-ai/02-core-guides/rpc-and-distributed-rpc.md`、`module-groups.md`），再按"公共契约 → 状态机 → 资源管理 → 并发"四类线索逐文件核对；每项疑似缺陷均在源码中亲自复核确认，未采信未经复核的结论（复核推翻了 1 项疑似：`RpcChannelState.prepareSend` 的 closed 复检看似完成错 future，实际 whenComplete 包装层会正确清理，不构成缺陷）。

## 缺陷总览

| 级别 | 数量 | 说明 |
|------|------|------|
| P0（核心功能不可用/必然崩溃） | 14 | OAuth 取 token NPE、multipart 上传必败、OkHttp GET 必抛异常、SSE 流 future 永不完成等 |
| P1（特定场景错误行为/泄漏/挂起） | 22 | 超时语义颠倒、协议解析错误、资源泄漏、重连死循环等 |
| P2（边界/诊断性缺陷） | 10 | 异常类型错误、健壮性缺口等 |

修复状态：44 项已修复并附回归测试（见"修复与测试映射"）；2 项 P2 仅记录（MQTT-08、HTTP-F6，原因见"遗留问题"）。

---

## A. nop-http-api（公共契约层）

### HTTP-01 [P0] `DefaultHttpResponse.getBodyAsBytes()` 赋值给错误变量
`support/DefaultHttpResponse.java:75-76`：`if (bodyAsText == null && body != null) body = JSON.stringify(body);` 应赋给 `bodyAsText`。后果：仅 `setBody(Object)` 时（三个流式聚合器的 `getFinalResult()` 正是如此）`getBodyAsBytes()` 返回 null，且 `body` 被污染为 JSON 字符串，后续 `getBodyAsString()` 双重编码。

### HTTP-02 [P0] `DefaultHttpResponse` 默认字符集 US-ASCII 破坏非 ASCII 响应体
`support/DefaultHttpResponse.java:80,103`：charset 未设置时用 `US_ASCII`，无 charset 声明的 UTF-8 JSON 响应中的中文被替换为 '?'。默认应为 UTF-8（`JdkHttpClient.toHttpResponse` 已按 UTF-8 假设）。

### HTTP-03 [P0] `AbstractServerEventSubscription` 双重 `onComplete`、违反 Flow 契约
`client/AbstractServerEventSubscription.java:20,63-66`：`completed` 标志声明后从未置 true。标准 OpenAI 流 `data: [DONE]` 触发一次 `onComplete()`，连接关闭再触发一次，`subscriber.onComplete()` 被调用两次；`[DONE]` 之后的数据行仍会被派发。仅因常见 Subscriber（`CompletableFuture`/`SubmissionPublisher`）幂等才未显式崩溃。

### HTTP-04 [P1] SSE 字段累积语义错误：未知行被当作事件派发、`id:`/`event:` 提前触发派发
`client/AbstractServerEventSubscription.java:100-117`：按 SSE 规范字段应累积到空行才派发；现实现 (a) 把 `retry: 1000` 等未知行原文作为 data 事件派发（下游 `OpenAIStreamingEventAggregator` 对其 `JSON.parse` 直接抛错）；(b) `data: hello` 后跟 `id: 42` 时事件在 id 设置前就被派发，id 丢失。

### HTTP-05 [P1] `waitForDemand` 在取消后仍递减 demand 并派发事件
`client/AbstractServerEventSubscription.java:134-144`：cancel/interrupt 唤醒后循环退出但仍执行 `demand--`，随后 `processLine` 继续 `onNext`，事件在取消后送达。

### HTTP-06 [P0] OpenAI 聚合器丢失 `finish_reason`
`aggregator/OpenAIStreamingEventAggregator.java:73-90`：终止块 `{"delta":{},"finish_reason":"stop"}` 同时含两字段时走 `containsKey("delta")` 分支，`finish_reason` 永不读取；真实原因（`length`/`tool_calls`/`content_filter`）被丢弃，仅在恰有 usage 时伪造 `"stop"`。

### HTTP-07 [P0] Ollama 聚合器对 `tool_calls` 强转 Map，实际是数组
`aggregator/OllamaStreamingEventAggregator.java:105-110`：Ollama 流式 `message.tool_calls` 为 JSON 数组，`(Map)` 强转抛 ClassCastException，整个聚合失败。

### HTTP-08 [P1] Claude 聚合器 thinking 增量字段名错误 + `input_tokens` 丢失
`aggregator/ClaudeStreamingEventAggregator.java:103-108,124-126`：匹配 `"reasoning_delta"/reasoning_text`，而 Anthropic 实际是 `"thinking_delta"/thinking`，推理内容永远丢弃；`message_start.usage.input_tokens` 未读取，输入 token 用量丢失。

### HTTP-09 [P1] `ContentType.parse` 参数解析残缺
`contenttype/ContentType.java:353-369`：charset 仅在"最后一个分号之后"查找（`charset=UTF-8;q=0.9` 取不到）；`charset=` 大小写敏感；值未 trim、未去引号（`"utf-8"` 抛 IllegalCharsetNameException）；mimeType 未 trim。该类是公开 API，`JdkHttpClient.toHttpResponse:316` 在用。

### HTTP-10 [P1] `NameValuePair.hashCode` 对 null value 抛 NPE
`contenttype/NameValuePair.java:93`：构造器允许 null value、equals 支持 null，hashCode 却直接 `value.hashCode()`。

### HTTP-11 [P0] `HttpCookieHelper.parseCookie` 丢弃值中含 `=` 的 cookie
`utils/HttpCookieHelper.java:39-44`：`split(cookie,'=')` 按全部 `=` 分割且仅保留 size==2，`token=abc==`（Base64/JWT 风格）被静默丢弃。应在首个 `=` 处分割。

### HTTP-12 [P0] `ContextHttpServerFilter` 把剩余时长当绝对过期时间
`server/ContextHttpServerFilter.java:64,77-78`：`nop-timeout` 头由生产方写入**剩余毫秒数**（`ClientContextRpcServiceInterceptor.java:129-132`：`expireTime - now`），而此处直接 `ctx.setCallExpireTime(expireTime)`。`IContext.isCallExpired()` 按绝对 epoch 比较（`expireTime - now <= 0`），带 `nop-timeout: 30000` 的请求立刻被判过期（等价 1970 年），`JdbcHelper` 等会立即抛超时。应 `now + timeout`。

### HTTP-13 [P1] `DelegateHttpClient` 未委托 `fetchServerEventFlow`
`support/DelegateHttpClient.java`：仅委托 fetch/download/upload；`IHttpClient.fetchServerEventFlow` 默认实现抛 UnsupportedOperationException。`DefaultHttpClientFactory` 无条件用每个 enhancer 包装客户端，注册 OAuth enhancer（其 `EnhancedClient` 即 Delegate）后所有 SSE/LLM 流式调用全部中断，即使底层 Apache/JDK 客户端支持。

### HTTP-14 [P2] `ContextHttpServerFilter.filterAsync` 同步异常路径泄漏 context
`server/ContextHttpServerFilter.java:47-53`：`next.get()` 同步抛出时 `ctx.close()` 不会执行（whenComplete 未挂上）。

## B. nop-http-client-oauth

### OAUTH-01 [P0] 取 token 首次必 NPE、刷新返回陈旧 token
`enhancer/AddAccessTokenHttpClientEnhancer.java:110-124`：局部变量 `token` 从未赋值为 `fetchToken` 的结果 `response`。首次请求 `token == null` → `token.getAccessToken()` NPE；过期刷新时返回旧 token。所有 client-credentials 流程不可用。

### OAUTH-02 [P1] token 请求的 Content-Type 被 Apache 客户端覆盖为 JSON
`fetchToken`（同文件 126-131）显式设置 `content-type: application/x-www-form-urlencoded`，但 `ApacheHttpClient.addBody` 无条件 `setBody(body, APPLICATION_JSON)`（`dataType==null` 时），实体 content-type 覆盖显式头（JDK 客户端正确地检查了显式头）。token 端点收到 JSON content-type 会拒绝。修复点在 `ApacheHttpClient.getContentType`。

## C. nop-http-client-apache

### APACHE-01 [P0] `LineAsyncDataConsumer` 丢弃 `resultCallback`，execute 返回的 future 永不完成
`LineAsyncDataConsumer.java:92-96`：`AsyncEntityConsumer` 契约要求在实体消费完成时完成 `streamStart` 传入的回调（httpcore5 的 `AbstractCharAsyncEntityConsumer` 正是为此设计）；本类直接忽略。经字节码核实（httpclient5 5.4 `InternalAbstractHttpAsyncClient`），该回调是 `execute()` 返回 future 的唯一成功完成路径 → 成功的 SSE 流导致 execute future 永远 pending、内部 in-flight 记录泄漏。

### APACHE-02 [P0] SSE 行拆分不处理 CR
`LineAsyncDataConsumer.java:61-82`：仅 `\n` 结束一行，尾部 `\r` 保留在行内。CRLF 流（规范允许，代理/Windows 服务常见）：`[DONE]\r` 匹配失败被并入数据；空行变 `"\r"` 非空 → 产生 data 为 `"\r"` 的伪事件。JDK 变体用 `BufferedReader.readLine` 无此问题。

### APACHE-03 [P0] 无实体响应（204/304/HEAD）NPE
`ApacheHttpClientHelper.java:196-204`：`SimpleHttpResponse.copy()` 不复制 body，`body == null` 时 `body.isText()` NPE（第 205 行又有 `body != null` 判断，明显是遗漏）。

### APACHE-04 [P0] null body 序列化为字符串 "null" 发送
`ApacheHttpClient.java:194-202`：`JsonTool.stringify(null)` 返回 `"null"`，所有无 body 的 GET 请求携带 4 字节 body `null` + `application/json`，严格的服务端/代理拒绝或误路由。JDK 客户端正确用 `noBody()`。

### APACHE-05 [P1] `downloadAsync` 无法取消传输中下载 + 错误响应体写入目标文件
`ApacheHttpClient.java:252-257` 丢弃 execute 返回的 future，cancel 只取消 promise，下载继续写文件；`DownloadResponseConsumer` 不检查状态码，非 2xx 的错误体被完整写入目标文件后才以该状态完成。

### APACHE-06 [P1] 多值 header 仅支持 `List`，其他 Collection 变成垃圾头
`ApacheHttpClient.toSimpleRequest:154-160` 只判 `instanceof List`；`Set` 走 `toString()` 产出 `[a, b]` 形式的头（JDK 客户端支持任意 Collection）。

### APACHE-07 [P2] `uploadAsync` 返回 null
`ApacheHttpClient.java:283-287`：未实现却返回 null 而非抛 UnsupportedOperationException，调用方（如 `DelegateHttpClient.uploadAsync` 直接解引用返回值）NPE。

## D. nop-http-client-jdk

### JDK-01 [P0] `ConcatenatedBodyPublisher` 对每个内部 publisher 重复调用 `onSubscribe`
`ConcatenatedBodyPublisher.java:50-55`：JDK HttpClient 内部订阅者收到多次 onSubscribe 抛 `IllegalStateException: already subscribed`（已核对 JDK 源码）。multipart 至少 4 个内部 publisher → **所有 multipart 上传必然失败**。另：空列表时直接 onComplete 违反 Flow 契约（须先 onSubscribe）。

### JDK-02 [P0] multipart Content-Type 缺 `boundary` 参数
`MultipartBodyPublisher.java:16` + `JdkHttpClient.java:200-201`：header 只有 `multipart/form-data`，`getBoundary()` 全仓库无调用（grep 核实）；服务端无法确定边界，multipart 不可解析。且 `BOUNDARY` 为 static，全 JVM 共享一个边界。

### JDK-03 [P1] SSE 流用平台默认字符集解码
`ServerEventPublisher.java:99`：`new InputStreamReader(in)` 未指定 UTF-8；SSE 规范要求 UTF-8。非 UTF-8 默认字符集的 JVM 上多字节内容乱码。

### JDK-04 [P1] SSE 流 InputStream 永不关闭 + 取消后事件继续派发
`ServerEventPublisher.java:94-134`：取消/错误路径不关 reader/in（连接泄漏到 GC）；`parseEvents` 循环无 `isCancelled()` 检查，cancel 后零需求仍持续 `onNext`。

### JDK-05 [P1] `HttpInputFileBodyPublisher` 正常完成不关闭 InputStream
`HttpInputFileBodyPublisher.java:42-45`：EOF 路径仅 `onComplete()`，只有 `cancel()` 关流 → 每次上传泄漏一个文件句柄。

### JDK-06 [P1] `FileDownloadSubscriber` 取消/写失败分支泄漏 OutputStream
`FileDownloadSubscriber.java:36-50`：cancel 分支和 catch 分支未 `close()` channel（onError/onComplete 才关）。

### JDK-07 [P1] `toMultipart` 静默丢弃非 String/Path 的表单字段
`JdkHttpClient.java:280-296`：Number、`IHttpInputFile` 等类型直接跳过，表单缺字段且无任何报错。

### JDK-08 [P2] `uploadAsync` 返回 null（同 APACHE-07）

### JDK-09 [P2] 所有 503 响应被改写为连接失败
`JdkHttpClient.java:302-305`：真实上游 503（含响应体）被 `NopConnectException` 替换。有注释说明是 MAC/JDK 特性 workaround，属有意行为，仅记录。

## E. nop-http-client-okhttp

### OKHTTP-01 [P0] 所有 GET/HEAD 请求抛 IllegalArgumentException
`OkHttpClientImpl.java:98-102`：body 永远非 null（null 时是 `""`），OkHttp 4.12 `Request.Builder.method` 对 GET+body 抛 `"method GET must not have a request body."`（字节码核实）。

### OKHTTP-02 [P0] `downloadAsync` 是占位代码 `client.newCall(null).enqueue(null)`
`OkHttpClientImpl.java:119-123`：调用即 NPE。应抛 UnsupportedOperationException。

### OKHTTP-03 [P1] 查询参数被静默丢弃
`OkHttpClientImpl.java:96`：`url(request.getUrl())` 未用 `getUrlWithParams()`（Apache/JDK 客户端都带参数），`request.param(...)` 设置的参数全部丢失。

### OKHTTP-04 [P2] 超时拦截器仅作用于连接阶段
`OkHttpTimeoutInterceptor.java:21-34`：`nop-timeout` 是整请求剩余时长，却只 `min(timeout, connectTimeout)` 到连接阶段；read/write 超时不调整，且内部头被转发到服务端。仅记录。

## F. nop-rpc

### RPC-01 [P0] `RpcMethodReference.equals` 用 `||` 而非 `&&`
`nop-rpc-api/.../RpcMethodReference.java:47-57`：同服务不同方法被判相等，而 hashCode 混合两字段 → 违反 equals 契约；HashSet/Map 去重时不同 RPC 方法被静默合并。

### RPC-02 [P0] `BinaryScalarType.fromText` 永远返回 null（根因在 nop-commons）
`nop-kernel/nop-commons/.../BinaryScalarType.java` 静态块：`textMap.put(textMap.toString(), type)` —— key 是 map 自身的 toString！全仓库唯一调用方是 `ProtoFileParser:284`（grep 核实）。后果：`.proto` 中 `int32/int64/bool/bytes/fixed32` 等全部标量类型被解析为未解析命名类型，`fieldModel.setBinaryScalarType` 永不设置，mandatory→primitive/optional→wrapper 映射失效，仅 `string/float/double` 因 `PredefinedGenericTypes` 的无关别名侥幸存活。

### RPC-03 [P0] proto `map<...>` 字段解析失败
`nop-rpc-model/.../ProtoFileParser.java:273-282`：`parseDataType` 解析完 value 类型后未 `sc.match('>')`，后续 `nextWord()` 遇 `>` 抛扫描错误。`map<string,int32> tags = 1;` 使整个文件解析中止。

### RPC-04 [P0] `MessageRpcClient` 四个关联缺陷
`nop-rpc-core/.../MessageRpcClient.java` + `RpcChannelState.java`：
- (a) timeout<=0（未设超时的默认值 -1）仍 `timer.schedule(task, -1ms)` → 立即执行 → **所有未显式设超时的请求瞬间超时**。`IRpcMessageAdapter.getTimeout` javadoc 明确 "<=0 表示不超时"。
- (b) 未设消息 id 时（`ApiHeaders.getId` → null）：响应按 null id 永不匹配；并发调用经 `waitFutures.put(null, future)` 相互 cancel。
- (c) `messageService.sendAsync(...)` 返回值被忽略、无 try/catch：发送失败时等待方只能等超时，pending 表项滞留。
- (d) cancel 传播块创建的 `Cancellable` 无任何回调注册，是无效代码。
旁证：`nop-stream-runtime/StreamControlRpcTransformer` 的注释明确描述了本客户端"不合成请求 id/无默认超时"并自行补偿。

### RPC-05 [P0] `RpcTaskMonitor` 错误路径 NPE + 单异常杀死监控循环
`nop-rpc-core/.../RpcTaskMonitor.java:94-97`：`callAsync` 异常完成时 `ret==null`，`RpcHelper.toTaskStatusResponse(null)` NPE（被 whenComplete 吞掉，`handleTaskStatus` 永不执行、失败计数不增长）；`checkTaskStatus` 无 try/catch，`scheduleWithFixedDelay` 任务抛异常后**所有后续执行被永久取消**。

### RPC-06 [P0] `PollingRpcClient` 同步抛出使调用方 future 永久挂起
`nop-rpc-core/.../PollingRpcClient.java:98-129`：`call()` 内 `callAsync` 同步抛（底层 channel closed / in-flight 超限时 `prepareSend` 直接 throw）→ 异常逃逸到 timer 线程，`PollTask.future` 永不完成。

### RPC-07 [P1] 取消后轮询不停止
`PollingRpcClient.java:87-92,110,120`：cancel 后在途调用的 whenComplete 仍无条件 `schedule()` 下一轮，不检查 `future.isDone()`，远程状态调用空转到状态恰好完成为止。

### RPC-08 [P1] 零参数 REST 方法 URL 被丢弃
`nop-rpc-core/.../HttpRpcMessageTransformer.java:85-88`：`args.length==0` 时提前 return，不设 httpUrl；`DefaultRpcUrlBuilder` 回退到 `/r/{method}` 通用端点，声明的 REST 路径被静默忽略。

### RPC-09 [P1] `DefaultRpcMessageTransformer.toRequest` 对非首位的 ApiRequest 参数取 `args[0]`
`DefaultRpcMessageTransformer.java:61-66`：循环按位置 i 检测，却恒取 `args[0]`（含 `RequestBean` 分支），ApiRequest 参数不在首位时取错参数或 ClassCastException。

### RPC-10 [P1] 服务端 `fromRequest` 永不回填 `ICancelToken` 参数
`DefaultRpcMessageTransformer.java:148-193`：`cancelToken` 形参完全未使用；客户端 `toRequest` 有意跳过该类参数，Map 路径按名绑定得 null → 取消永远无法传导到业务方法。

### RPC-11 [P1] GraphQL 请求类型判断写反
`nop-rpc-http/.../HttpRpcService.java:91`：`request.getData() instanceof GraphQLResponseBean` —— 请求侧不可能出现 Response bean，分支死亡；GraphQL 响应落到 ApiResponse 解析，`errors` 被静默丢弃。应为 `GraphQLRequestBean`。

## G. nop-netty

### NETTY-01 [P0] `scheduleReconnect` 无视重试策略的停止信号（-1）→ 热循环
`tcp/NettyTcpClient.java:228-250`：`IRetryPolicy.getRetryDelay` 返回 -1 表示不再重试（javadoc 明确），`eventLoop.schedule(task, -1, ms)` 立即执行 → 配置了有限 `maxConnectRetryCount` 且服务端持续不可达时形成无间隔重连风暴（日志每 1000 次才打一条）。

### NETTY-02 [P0] `RetryPolicy.withRetryDelay` 设置错字段（根因在 nop-commons）
`nop-kernel/nop-commons/.../RetryPolicy.java:96-99`：`withRetryDelay` 调 `setMaxRetryDelay`；`NettyTcpClient.doStart:131-138` 是全仓库唯一调用链（grep 核实）：配置的 `connectRetryDelay=100` 失效（停留默认 1000ms）、`maxConnectRetryDelay=3000` 被覆盖为 100。另 `setMaxRetryDelay:105-108` 的 Guard 检查错了变量（检查字段而非参数）。

### NETTY-03 [P0] 内建 worker group 时流量整形对每个连接抛 IllegalArgumentException
`tcp/NettyTcpServer.java:136-140,187-189,195-203`：`doStart` 的局部 `workerGroup` 从未赋给字段，`getWorkerGroup()` 返回 null；Netty `GlobalChannelTrafficShapingHandler` 构造器 `checkNotNull(executor)`（4.1.124 源码核实）→ 开启 globalTrafficShaping 配置且未注入外部 worker group 时**每个接入连接在 initChannel 即死**。

### NETTY-04 [P1] `GlobalChannelTrafficShapingHandler` 每 channel 一个实例，"全局"语义失效
`NettyTcpServer.java:195-213`：该类 `@Sharable` 且 javadoc 要求全服务器一个实例共享计数器；现在每连接新建（各自独立的 global 队列/计数器），配置的全局限速实际是**每连接**限速；且与 `ChannelTrafficShapingHandler` 同管线共存违反 Netty 明确禁令。修复：doStart 创建单例共享（同时解决 NETTY-03）。

### NETTY-05 [P1] `sendOneway` 只 write 不 flush，消息可能永不出站
`NettyTcpClient.java:286-300`：`write(msg)` 后无 flush，消息滞留 ChannelOutboundBuffer；连接刚建立就 oneway 发送（主要用例）时永远不发出。

### NETTY-06 [P0] `RpcMessageHandler` 在连接断开后 send → NPE + 调用方永久挂起
`handlers/RpcMessageHandler.java:102-135,151`：`channelInactive` 置 `channel=null` 但 `executor` 保留；`send` 只检查 executor → `write` 里 `channel.writeAndFlush` NPE 于 executor 任务内，返回的 future 永不完成（timeout 定时器已随 inactive 取消，无人兜底）→ `FutureHelper.syncGet` 无超时变体永久阻塞。服务端 `NettyTcpServer.sendToChannel:278-279` 同样无 null 检查。

### NETTY-07 [P1] `NettySslEngineFactory` 完全忽略 trustStore 配置
`ssl/NettySslEngineFactory.java:35-78`：`SslConfig.trustStorePath/trustStorePassword` 全仓库无引用（grep 核实）；trust manager 恒用 keystore 初始化 —— 配置了 truststore 的部署静默失效，keystore 中任何 CA 都被信任。

### NETTY-08 [P1] `HeartbeatHandler` 吞掉非 WRITER_IDLE 的所有用户事件
`handlers/HeartbeatHandler.java:26-34`：未处理的事件不 `fireUserEventTriggered` 转发，位于其后的所有 handler 收不到 READER_IDLE 等事件。（READER_IDLE 无"断开死连接"策略属设计缺口，另行记录。）

### NETTY-09 [P1] `NettyTcpServer.doStop` 不关闭已接受连接
`tcp/NettyTcpServer.java:306-315`：外部注入 worker group 时该 group 不 shutdown（正确），但也无任何代码关闭 child channel —— `DefaultChannelGroup` 建了却不用，stop 后存量连接继续服务。

### NETTY-10 [P2] 随机端口模式不回写实际绑定端口
`NettyTcpServer.java:119-124`：`port<=0` 时随机选端口绑定，但选中值不存回，`getPort()` 恒返回 0，测试无法发现监听端口。

### NETTY-11 [P2] `doSend` 在无 `IRpcMessageHandler` 的管线上 NPE → 调用方挂起
`NettyTcpClient.java:321-324`：`pipeline().get(IRpcMessageHandler.class)` 可能为 null（纯推送型管线），直接解引用；同类挂起缺陷。

## H. nop-socket

### SOCKET-01 [P0] `SocketServer` stop 后无法重启
`SocketServer.java:121-149`：`doStop` 关闭 socket 但不置 null、不重置 `stopped`；`LifeCycleSupport` 默认 `allowRestart=true` 且 stop 后状态回 CREATED —— 再次 start 时 `doStart` 因 `socket != null` 直接返回，`run()` 因 `stopped` 立即退出，服务器处于"ACTIVE 但不接受任何连接"的假活状态。

### SOCKET-02 [P0] accept 循环因任何单次异常永久终止
`SocketServer.java:197-230`：accept 的瞬时 IOException（如 fd 耗尽 EMFILE）、超额连接时 `client.close()` 的 IOException、executor 拒绝（`execute` 失败被刻意 rethrow）都会逃逸出 run() 循环 —— 服务器保持 ACTIVE 但从此不接受任何连接，仅一行日志。

### SOCKET-03 [P1] 并发写同一 socket 无同步 → 帧流交错损坏
`SocketServer.java:161-187,284-288`：`broadcast`/`sendTo`（任意线程）与 `processCommand`（每连接线程）对同一 OutputStream 并发 write+flush，无锁（对照 `SocketClient.send:250-260` 正确地 `synchronized(os)`）。并发时包字节交错，接收端 masks/长度校验失败断连。

### SOCKET-04 [P1] 恶意长度字段触发 RuntimeException 而非设计的 IOException
`BinaryCommand.java:146-162`：`minLen` 默认 0（ClientConfig/ServerConfig 均是），结构性非法长度（0-3、5-7；协议只允许 4=心跳或 >=8）绕过校验 → `BufferOverflowException`/`BufferUnderflowException` 逃逸，连接虽同样断开但异常类型错误、诊断信息误导。

### SOCKET-05 [P1] `SocketClient.connect` 失败泄漏 Socket 且无法重试
`SocketClient.java:110-123`：`socket` 字段在 connect 尝试前赋值，失败时不清理 —— fd 泄漏一次/尝试；重试时 `Guard.checkState(socket == null)` 抛 IllegalStateException 掩盖原始错误。仅 `reconnect()`（内部先 cleanup）可恢复，API 无任何提示。

### SOCKET-06 [P2] `setReuseAddress(true)` 在 bind 之后调用，无效
`SocketServer.java:126-128`：Java 语义要求 bind 前设置才生效，快速重启避免端口冲突的意图落空。

## I. nop-codec

### CODEC-01 [P1] `encodeToBytes`/`encodeBytes`/`decodeBytes` 泄漏引用计数 ByteBuf
`IPacketCodec.java:41-45`、`support/AbstractByteBufCodec.java:19-29`：`UnpooledByteBufAllocator.DEFAULT.buffer()`（直连内存、带引用计数）取出 byte[] 后从不 release；`ByteBufUtil.getBytes` 只拷贝不减计数。每消息压缩调用泄漏一块堆外内存直到 GC 触发 Cleaner；Netty ResourceLeakDetector（默认 SAMPLED）会持续报 LEAK。

### CODEC-02 [P1] `ByteBufHelper.writeBuf(os, buf, start, length)` 对直连缓冲忽略 start/length
`util/ByteBufHelper.java:42-54`：无 backing array 时 `getBytes(buf)` 只取可读区域整段写出，形参 start/length 被静默丢弃（前一行 Guard 还承诺校验它们）。当前仓库内调用恰好传 `(readerIndex, readableBytes)` 才未暴露；公开工具方法契约已破坏。

### CODEC-03 [P2] 压缩编解码器异常路径不关闭 zip 流（native 内存滞留）
`compress/GZipCompressCodec.java`、`DeflateCompressCodec.java`：`IoHelper.copy` 抛出时 `zip.close()`（Inflater.end()）被跳过，zlib native 内存等 finalizer 兜底。

## J. nop-vertx

### VERTX-01 [P0] `MqttSessionManager.removeConnection` 从未被调用 → 会话永久泄漏
`impl/MqttSessionManager.java` + `impl/VertxMqttServer.java:64-66`：`addConnection` 后无人调用 remove（grep 核实零调用方），每个曾连接的客户端的 `MqttConnection` 永久滞留 ConcurrentHashMap，面向公网的服务器内存无限增长。

### VERTX-02 [P0] 无会话接管：重复 clientId 不断开旧连接
MQTT-3.1.1 要求新 CONNECT 到达时 MUST 断开既有连接；现在 `sessions.put` 直接覆盖映射，两条 TCP 连接同时存活且旧连接仍向业务 handler 投递消息。

### VERTX-03 [P0] 注入的 `IMqttAuthChecker` 从未被调用 → 零认证
`impl/VertxMqttServer.java:33-44,64-66`：`checkAuthAsync` 全仓库零调用；`MqttConnection.init:117` 无条件 `endpoint.accept()`。配置了凭据校验的使用者得到的是完全无认证的服务。

### VERTX-04 [P0] `SimpleMqttAuthChecker` 匿名（null/null）凭据通过校验
`auth/SimpleMqttAuthChecker.java:26-31`：`users.get(null)` → null，`Objects.equals(null,null)` → true，MQTT 允许无凭据 CONNECT → 匿名客户端通过空用户表校验（当前因 VERTX-03 未触发，属双重缺陷）。

### VERTX-05 [P0] `keepAlive=0` 的客户端 `isAlive()` 恒 false
`impl/MqttConnection.java:55,167-169`：KeepAlive=0 按规范表示无需保活、连接永不过期；实现里 `keepAliveTimeoutMs=0` 使 `(now-lastPing) < 0` 恒假，刚连上的健康连接即判死。`< 0` 分支是死代码（秒数不会为负）。

### VERTX-06 [P1] `MqttConnection.sendAsync` 返回 null 违反契约
`impl/MqttConnection.java:191-194`：`IMessageConsumeContext.sendAsync(...)` 的消费方直接 `whenComplete` 链式调用 → NPE。应抛 UnsupportedOperationException。

### VERTX-07 [P1] `MqttServerMessageService.send/subscribe` 返回 null
`bus/MqttServerMessageService.java:28-36`：接入消息总线后 send 即 NPE。同为未实现占位，应抛 UnsupportedOperationException。

### VERTX-08 [P1] `MqttMessageBuilder.forPublish` 不拷贝 payload ByteBuf
`message/MqttMessageBuilder.java:20`：`msg.payload().getByteBuf()` 返回底层缓冲（不动引用计数），vertx-mqtt 管线在 handler 返回后释放 → bean 异步/存储场景 use-after-free（IllegalReferenceCountException）。应 copy。

### VERTX-09 [P2] `doStart` 不等待 listen 结果
`impl/VertxMqttServer.java:53-62`：端口占用时 doStart 正常返回、生命周期报 ACTIVE。需阻塞生命周期决策，仅记录（见遗留问题）。

## 复核否决的疑似项（避免后人重复怀疑）

- `RpcChannelState.prepareSend` 的 closed 二次检查看似完成"包装层 future"导致表项滞留 —— 实际 whenComplete 回调会移除原始表项并取消定时器，无泄漏，非缺陷。
- `PacketCodecHandler` 的 readSlice 引用计数（不 retain 转发切片）与其声明的契约一致。
- `SocketClient.send` 的 `synchronized(os)` 与 `BinaryCommand.readFully` 的部分读记账均正确。
- `TestFragmentChannelHandler` 的 voidPromise 首片转发对测试用途可接受。

## 修复与测试映射

回归测试位置（模块内 `src/test/java`，均验证"修复前失败（红灯）、修复后通过（绿灯）"）：

| 测试类 | 覆盖缺陷 |
|--------|----------|
| `io.nop.http.api.support.TestDefaultHttpResponseFix` | HTTP-01/02 |
| `io.nop.http.api.client.TestServerEventSubscriptionFix` | HTTP-03/04/05 |
| `io.nop.http.api.aggregator.TestStreamingAggregatorsFix` | HTTP-06/07/08 |
| `io.nop.http.api.contenttype.TestContentTypeParseParams` | HTTP-09/10 |
| `io.nop.http.api.utils.TestHttpCookieHelperFix` | HTTP-11 |
| `io.nop.http.api.server.TestContextHttpServerFilterFix` | HTTP-12 |
| `io.nop.http.api.support.TestDelegateHttpClientFix` | HTTP-13 |
| `io.nop.http.apache.TestLineAsyncDataConsumerFix` | APACHE-01/02 |
| `io.nop.http.apache.TestApacheHttpClientHelperFix` | APACHE-03 |
| `io.nop.http.apache.TestApacheHttpClientFix` | APACHE-04/05/06/07、OAUTH-02 |
| `io.nop.http.client.jdk.TestConcatenatedBodyPublisherFix` | JDK-01 |
| `io.nop.http.client.jdk.TestMultipartFix` | JDK-02/07 |
| `io.nop.http.client.jdk.TestServerEventPublisherStreamFix` | JDK-03/04 |
| `io.nop.http.client.jdk.TestBodyPublisherResourceFix` | JDK-05/06/08 |
| `io.nop.http.client.okhttp.TestOkHttpClientImplFix` | OKHTTP-01/02/03 |
| `io.nop.http.client.oauth.enhancer.TestAddAccessTokenEnhancerFix` | OAUTH-01 |
| `io.nop.rpc.api.TestRpcMethodReferenceFix` | RPC-01 |
| `io.nop.rpc.core.message.TestMessageRpcClientFix` | RPC-04 |
| `io.nop.rpc.core.monitor.TestRpcTaskMonitorFix` | RPC-05 |
| `io.nop.rpc.core.composite.TestPollingRpcClientFix` | RPC-06/07 |
| `io.nop.rpc.core.reflect.TestHttpRpcMessageTransformerFix` | RPC-08/09/10 |
| `io.nop.rpc.http.TestHttpRpcServiceFix` | RPC-11 |
| `io.nop.rpc.model.proto.TestProtoParserMapAndScalars` | RPC-02/03 |
| `io.nop.codec.TestCodecResourceFix` | CODEC-01/02/03 |
| `io.nop.socket.TestSocketProtocolFix` | SOCKET-04/05 |
| `io.nop.socket.TestSocketServerRestart` | SOCKET-01/02/03 |
| `io.nop.netty.tcp.TestNettyTcpClientReconnect` | NETTY-01/02/05/11 |
| `io.nop.netty.handlers.TestRpcMessageHandlerFix` | NETTY-06 |
| `io.nop.netty.tcp.TestNettyTcpServerFix` | NETTY-03/04/09/10 |
| `io.nop.vertx.mqtt.server.impl.TestMqttServerFix`（`TestMqttConnection` 保持既有断言通过） | VERTX-01~07 |

验证结果：
- 红灯阶段：全部新测试按预期失败（约 70 个失败断言），逐项对应上表缺陷。
- 绿灯阶段：nop-commons（268 个测试）+ nop-network 全部 17 个子模块 + 下游 nop-stream-runtime（1059）/nop-ai-core（379）/nop-biz（48）/nop-record-netty 全部通过，BUILD SUCCESS。
- `RetryPolicy`/`BinaryScalarType` 修复位于 `nop-kernel/nop-commons`（各仅 1 个仓库内调用方，grep 核实），回归测试分别由 `TestNettyTcpClientReconnect` 与 `TestProtoParserMapAndScalars` 覆盖。

## 遗留问题（本次不修，需决策）

- **VERTX-09**：`doStart` 是否阻塞等待 listen 需要生命周期语义决策（vertx 线程上 await 会抛异常）。
- **HTTP-F6**（DefaultClientIpFetcher）：`Forwarded` 多条目不按逗号分割、`isValidIp` 过松、XFF 取最左可被伪造 —— 涉及部署信任模型（最左 vs 最右），改动是安全语义变更，应单独立项。
- 其他记录项：MQTT QoS 上限钳制/autoAck 死配置/exceptionHandler 不关连接、OkHttp 超时拦截器语义（OKHTTP-04）、`NettyHelper.writeFully` 忙等（无调用方）、`ApiContextInterceptor` 异步完成后 context 关闭线程语义、`ProtoHelper` 对 Map 类型字段生成非法 proto 输出、`HttpRpcService` 每请求 INFO 日志、`SimpleRpcServer.stop` 后不可重启、`JdkHttpClient` 503→连接错误（有意 workaround）。

## References

- `docs-for-ai/02-core-guides/rpc-and-distributed-rpc.md`
- `docs-for-ai/01-repo-map/module-groups.md`
- 依赖版本核对：httpcore5 5.3.6 / httpclient5 5.4（javap 字节码）、OkHttp 4.12、Netty 4.1.124、JDK HttpClient 源码
