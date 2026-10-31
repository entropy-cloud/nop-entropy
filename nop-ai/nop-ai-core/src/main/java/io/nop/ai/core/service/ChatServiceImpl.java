/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.core.service;

import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatLogger;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.api.chat.stream.StreamItemPhase;
import io.nop.ai.api.chat.stream.StreamItemType;
import io.nop.ai.api.credential.IAiModelCredentialResolver;
import io.nop.ai.core.dialect.AbstractLlmDialect;
import io.nop.ai.core.dialect.ILlmDialect;
import io.nop.ai.core.model.LlmModel;
import io.nop.ai.core.model.LlmModelModel;
import io.nop.ai.core.dialect.LlmDialectFactory;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.JSON;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.ICancelToken;
import jakarta.annotation.Nullable;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.concurrent.ratelimit.DefaultRateLimiter;
import io.nop.commons.concurrent.ratelimit.IRateLimiter;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IServerEventResponse;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static io.nop.ai.core.AiCoreConfigs.CFG_AI_SERVICE_LOG_MESSAGE;
import static io.nop.ai.core.AiCoreConfigs.CFG_AI_SERVICE_RATE_LIMIT_ACQUIRE_TIMEOUT;
import static io.nop.ai.core.AiCoreConfigs.CFG_AI_SERVICE_READ_TIMEOUT;
import static io.nop.ai.core.AiCoreConfigs.CFG_AI_SERVICE_STREAM_IDLE_TIMEOUT;
import static io.nop.ai.core.AiCoreConfigs.CFG_AI_SERVICE_STREAM_MAX_ACCUMULATED_CHARS;
import static io.nop.ai.core.AiCoreConfigs.CFG_AI_SERVICE_STREAM_MAX_TOOL_CALLS;
import static io.nop.ai.core.AiCoreConfigs.CFG_AI_SERVICE_STREAM_TOTAL_TIMEOUT;
import static io.nop.ai.core.NopAiCoreErrors.ARG_DETAIL;
import static io.nop.ai.core.NopAiCoreErrors.ARG_HTTP_STATUS;
import static io.nop.ai.core.NopAiCoreErrors.ARG_LLM_NAME;
import static io.nop.ai.core.NopAiCoreErrors.ARG_TIMEOUT;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_RATE_LIMITED;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_SERVICE_NO_BASE_URL;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_STREAM_ACCUMULATION_EXCEEDED;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_STREAM_BUFFER_OVERFLOW;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_STREAM_IDLE_TIMEOUT;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_STREAM_TERMINATION_SIGNAL_MISSING;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_STREAM_TOTAL_TIMEOUT;
import static io.nop.http.api.HttpApiErrors.ARG_BODY;
import static io.nop.http.api.HttpApiErrors.ARG_RESPONSE_HEADERS;

/**
 * 基于 llm.xml 配置的多模型 ChatService 实现。
 * <p>
 * 使用 LlmDialect 处理不同 API 风格的特定逻辑，将请求构建、响应解析、流式处理
 * 等功能委托给对应的方言实现。
 *
 * @author canonical_entropy@163.com
 */
public class ChatServiceImpl implements IChatService {
    private static final Logger LOG = LoggerFactory.getLogger(ChatServiceImpl.class);

    /**
     * 3.1-C3-001 ②（plan 2505）：桥接订阅对上游的 request 窗口（替代 request(MAX_VALUE)
     * 全速拉取）——传输层 demand 等待被 nop-http 基类有界化后，窗口耗尽时读取线程在
     * 有界预算内停滞，慢下游不再经 uninterruptible submit 阻塞共享承载线程。
     */
    static final long STREAM_REQUEST_WINDOW = 64;

    /**
     * 3.1-C3-001 ③（plan 2505）：桥接 publisher 的缓冲大小（有界缓冲语义的一部分；
     * 满载经 {@code offer} fail-loud 处理，不阻塞提交线程）。
     */
    static final int STREAM_PUBLISHER_BUFFER_SIZE = 256;

    private IHttpClient httpClient;
    private final Map<String, IRateLimiter> rateLimiters = new ConcurrentHashMap<>();
    private IChatLogger chatLogger;

    /**
     * 可选的凭证解析器（W7-successor，plan 2026-08-13-1118-3 Phase 1 D1）。当 nop-ai-service +
     * nop-credential 在 classpath 且 bean 装配时注入；否则为 null（{@code @Nullable} → NopIoC optional），
     * 此时跳过 credentialId 解析，回退既有 {@code resolveApiKey} 路径，**零回归**。
     *
     * <p>非 private：兼容 NopIoC 字段注入可见性（AGENTS.md）。这里用 setter 注入 + {@code @Nullable}。
     */
    private IAiModelCredentialResolver credentialResolver;

    @Inject
    public void setChatLogger(IChatLogger chatLogger) {
        this.chatLogger = chatLogger;
    }

    @Inject
    public void setHttpClient(IHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * 凭证解析器可选注入。{@code @Nullable} 使 NopIoC 在无匹配 bean 时注入 null（optional=true），
     * 部署不含 nop-credential 时仍可启动（mode: 回退 config 变量 apiKey）。
     */
    @Inject
    public void setCredentialResolver(@Nullable IAiModelCredentialResolver credentialResolver) {
        this.credentialResolver = credentialResolver;
    }

    @InjectValue("@cfg:nop.ai.secret-dir|/nop/ai/secret")
    public void setSecretDir(File secretDir) {
        LlmConfigHelper.setSecretDir(secretDir);
    }

    public void clearSecretCache() {
        LlmConfigHelper.clearSecretCache();
    }

    @Override
    public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
        // null options 是文档化契约（ChatRequest.getOptions 可 null；LlmConfigHelper.getProvider /
        // ModelClassRouter / RuleBasedSelectionStrategy 均按 null 处理）——按无 options 语义
        // null-safe 处理：stream 走默认值（true），等价于 options.stream == null 的既有行为。
        boolean stream = true;
        if (request.getOptions() != null && request.getOptions().getStream() != null)
            stream = request.getOptions().getStream();

        // 如果 stream=true，先调用流式接口，再汇聚结果
        if (stream) {
            return aggregateStreamToResponse(request, cancelToken);
        }

        String provider = LlmConfigHelper.getProvider(request.getOptions());
        LlmModel config = LlmConfigHelper.loadConfig(provider);
        ILlmDialect dialect = LlmDialectFactory.getDialect(config.getApiStyle());

        // 速率限制检查
        checkRateLimit(provider, config);

        long beginTime = CoreMetrics.currentTimeMillis();
        request.setRequestTime(beginTime);
        if (request.getRequestId() == null)
            request.setRequestId(StringHelper.generateUUID());

        boolean logMessage = shouldLogMessage(config);
        if (logMessage) {
            chatLogger.logRequest(request);
        }

        // 构建请求
        String model = LlmConfigHelper.resolveModel(config, request.getOptions());
        HttpRequest httpRequest = buildHttpRequest(config, provider, model, request, false, dialect);

        return httpClient.fetchAsync(httpRequest, cancelToken)
                .thenApply(response -> {
                    if (response.getHttpStatus() != 200) {
                        // 非 200 不再吞 body/头，也不抛异常——经 dialect 规范化为携带
                        // errorClassification 的错误 ChatResponse（设计 §3.4 契约变更：
                        // 响应级错误走 ChatResponse，传输级错误才抛异常）。
                        ChatResponse errResponse = dialect.parseErrorResponse(
                                response.getBodyAsString(),
                                response.getHttpStatus(),
                                response.getHeaders(),
                                config);
                        errResponse.setRequestId(request.getRequestId());
                        errResponse.setResponseTime(CoreMetrics.currentTimeMillis());

                        if (logMessage) {
                            chatLogger.logResponse(request, errResponse);
                        }
                        return errResponse;
                    }

                    ChatResponse chatResponse = dialect.parseResponse(response.getBodyAsString(), config);
                    chatResponse.setRequestId(request.getRequestId());
                    chatResponse.setResponseTime(CoreMetrics.currentTimeMillis());

                    if (logMessage) {
                        chatLogger.logResponse(request, chatResponse);
                    }
                    return chatResponse;
                });
    }

    @Override
    public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
        String provider = LlmConfigHelper.getProvider(request.getOptions());
        LlmModel config = LlmConfigHelper.loadConfig(provider);
        ILlmDialect dialect = LlmDialectFactory.getDialect(config.getApiStyle());

        // 速率限制检查
        checkRateLimit(provider, config);

        long beginTime = CoreMetrics.currentTimeMillis();
        request.setRequestTime(beginTime);
        if (request.getRequestId() == null)
            request.setRequestId(StringHelper.generateUUID());

        boolean logMessage = shouldLogMessage(config);
        if (logMessage) {
            chatLogger.logRequest(request);
        }

        // 构建流式请求
        String model = LlmConfigHelper.resolveModel(config, request.getOptions());
        HttpRequest httpRequest = buildHttpRequest(config, provider, model, request, true, dialect);
        httpRequest.setHeader("accept", "text/event-stream");

        // 3.1-C3-001 ③（plan 2505）：显式平台受管承载（GlobalExecutors 治理面，daemon cached 池）——
        // 消除缺省 ForkJoinPool.commonPool 承载（聚合/日志 I/O/AiCommand 延续链全部落共享池的缺陷形态）；
        // 阻塞日志 I/O 不指向 globalTimer（MA-16 收窄后约束）。有界缓冲 + 满载 fail-loud（②）。
        SubmissionPublisher<ChatStreamChunk> publisher =
                new SubmissionPublisher<>(GlobalExecutors.cachedThreadPool(), STREAM_PUBLISHER_BUFFER_SIZE);

        Flow.Publisher<IServerEventResponse> eventPublisher = httpClient.fetchServerEventFlow(httpRequest, cancelToken);
        eventPublisher.subscribe(new Flow.Subscriber<>() {
            private Flow.Subscription upstream;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.upstream = subscription;
                // ② 有界 request 窗口（替代 Long.MAX_VALUE 全速拉取）
                subscription.request(STREAM_REQUEST_WINDOW);
                if (cancelToken != null) {
                    // ① 取消闭合下游（C4-002 同点）：上游 cancel 的同时以可区分的取消终态
                    // 关闭桥接 publisher——下游订阅有界终止，聚合 future 不再悬挂
                    cancelToken.appendOnCancelTask(() -> {
                        subscription.cancel();
                        publisher.closeExceptionally(new CancellationException("chat stream cancelled"));
                    });
                }
            }

            @Override
            public void onNext(IServerEventResponse item) {
                try {
                    // Plan 2519 (裁定⑧/C3-028): consume the multi-chunk channel
                    // — one SSE event enumerates ALL increments (multi
                    // tool_call entries / usage frames / typed error chunks).
                    // Falls back to the single-chunk contract for external
                    // dialect implementations that do not extend
                    // AbstractLlmDialect (ILlmDialect signature unchanged).
                    java.util.List<ChatStreamChunk> chunks;
                    if (dialect instanceof AbstractLlmDialect abstractDialect) {
                        chunks = abstractDialect.parseStreamChunks(item.getData());
                    } else {
                        ChatStreamChunk single = dialect.parseStreamChunk(item.getData());
                        chunks = single == null ? java.util.List.of() : java.util.List.of(single);
                    }
                    for (ChatStreamChunk chunk : chunks) {
                        if (chunk instanceof io.nop.ai.core.dialect.DialectErrorChunk errorChunk) {
                            // 裁定⑧: typed in-band error — explicit termination
                            // signal + traceable log record (never merged into
                            // assistant text)
                            LOG.warn("nop.ai.stream-error-chunk: provider in-band error event "
                                            + "(typed error chunk; stream terminated): provider={}, errorType={}, message={}",
                                    provider, errorChunk.getErrorType(), errorChunk.getErrorMessage());
                            upstream.cancel();
                            publisher.closeExceptionally(new NopException(ERR_AI_STREAM_TERMINATION_SIGNAL_MISSING)
                                    .param(ARG_LLM_NAME, provider));
                            return;
                        }
                    }
                    for (ChatStreamChunk chunk : chunks) {
                        if (chunk != null) {
                            // ② 有界缓冲满载 fail-loud（替代 submit 的不可中断阻塞）：溢出即显式终态
                            AtomicBoolean overflow = new AtomicBoolean();
                            publisher.offer(chunk, (sub, dropped) -> {
                                overflow.set(true);
                                return true; // 溢出项由 closeExceptionally 的错误终态承载，不静默降级
                            });
                            if (overflow.get()) {
                                upstream.cancel();
                                publisher.closeExceptionally(new NopException(ERR_AI_STREAM_BUFFER_OVERFLOW)
                                        .param(ARG_LLM_NAME, provider));
                                return;
                            }
                        }
                    }
                } catch (Throwable e) {
                    // ① onNext 容错 + closeExceptionally 兜底：解析/提交异常不得使 publisher
                    // 永不关闭（悬挂流形态）
                    upstream.cancel();
                    publisher.closeExceptionally(NopException.adapt(e));
                    return;
                }
                // ② 滑动窗口补给：维持传输侧在途上界
                upstream.request(1);
            }

            @Override
            public void onError(Throwable throwable) {
                publisher.closeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                publisher.close();
            }
        });

        return publisher;
    }

    /**
     * 构建 HTTP 请求
     * <p>
     * [账号回退链 plan 2026-08-01-1505-1，设计 §3.6]：当 {@code request.getOptions().getAccountKey()}
     * 非空时（agent 层重试循环在 QUOTA/AUTH FALLBACK 时从备用账号链取下一个账号后经 ChatOptions
     * 下沉），用该账号身份（apiKey + 可选 accountBaseUrl 覆盖）构造请求——而非按 provider 解析单 key。
     * 为空时退回 {@code LlmConfigHelper.resolveApiKey(provider)}（今日行为，零回归）。
     *
     * <p><b>apiKey 优先级链（W7-successor，plan 2026-08-13-1118-3 Phase 1 D2）</b>：
     * {@code accountKey > credentialId > resolveApiKey(config-var/secret-file)}。
     * <ol>
     *   <li>{@code accountKey} 非空（coordinator FALLBACK 下沉的显式纠正账号）→ 用之。</li>
     *   <li>否则若 {@code credentialResolver} 已装配且按 provider+model 解析出非空 apiKey
     *       （模型配了 credentialId 且凭证库解析成功）→ 用之。</li>
     *   <li>否则回退 {@code LlmConfigHelper.resolveApiKey(provider)}（零回归）。</li>
     * </ol>
     * credentialResolver 为 null（无 nop-credential 装配）或返回 null（模型未配 credentialId，
     * 显式回退+审计，Phase 1 D4）时直接走第 3 步。credentialId 非空但凭证失效时 resolver 抛
     * {@link NopException}（强 fail-closed，Phase 1 D5）——本方法不吞，异常向上传播中止调用。
     */
    private HttpRequest buildHttpRequest(LlmModel config, String provider, String model,
                                           ChatRequest request, boolean stream, ILlmDialect dialect) {
        io.nop.ai.api.chat.ChatOptions opts = request.getOptions();
        String accountKey = opts != null ? opts.getAccountKey() : null;
        String accountBaseUrl = opts != null ? opts.getAccountBaseUrl() : null;

        String baseUrl = resolveBaseUrl(config, provider, accountBaseUrl);
        // apiKey 优先级链：accountKey > credentialId > resolveApiKey
        String apiKey = resolveApiKeyForRequest(provider, model, accountKey);
        LlmModelModel modelConfig = LlmConfigHelper.getModelConfig(config, model);

        HttpRequest httpRequest = new HttpRequest();
        httpRequest.setMethod("POST");
        httpRequest.setUrl(dialect.buildUrl(baseUrl, config.getChatUrl(), apiKey));
        dialect.setHeaders(httpRequest, apiKey, config.getApiKeyHeader());

        httpRequest.setTimeout(CFG_AI_SERVICE_READ_TIMEOUT.get());

        // Plan 2302 (WI20): per-provider cache-key placement — session-
        // affinity header form (after auth headers; config-gated no-op when
        // LlmModelModel.cacheKeyHeader is unset)
        dialect.applyCacheHeaders(httpRequest, request, modelConfig);

        Map<String, Object> body = dialect.buildBody(request, config, modelConfig, model, stream);
        httpRequest.setBody(JsonTool.serialize(body, false));

        return httpRequest;
    }

    /**
     * 解析本次请求的 apiKey（W7-successor，plan 2026-08-13-1118-3 Phase 1 D2 优先级链）。
     * <p>
     * accountKey 非空（coordinator FALLBACK 显式纠正账号）→ 直接用之。否则 consult 凭证解析器：
     * 装配且返回非空（模型配了 credentialId 且解析成功）→ 用之；返回 null（未配 credentialId 或
     * resolver 未装配）→ 回退 {@link LlmConfigHelper#resolveApiKey(String)}（零回归）。
     * <p>
     * credentialId 非空但凭证失效时 resolver 抛 {@link NopException}（强 fail-closed），本方法不吞。
     */
    private String resolveApiKeyForRequest(String provider, String model, String accountKey) {
        // D6-04（A1-audit successor，2026-08-17）：isBlank 判空——纯空白 accountKey/credApiKey
        // 不是有效 key，不得当"非空"使用（空白 accountKey 应回退解析链而非注入空白凭证头）
        if (!StringHelper.isBlank(accountKey)) {
            return accountKey;
        }
        if (credentialResolver != null) {
            String credApiKey = credentialResolver.resolveApiKeyByCredential(provider, model);
            if (!StringHelper.isBlank(credApiKey)) {
                return credApiKey;
            }
        }
        return LlmConfigHelper.resolveApiKey(provider);
    }

    /**
     * 解析 Base URL
     * <p>
     * [账号回退链] 支持可选 per-account baseUrl 覆盖（{@code accountBaseUrl}）。非空时优先于
     * config 变量 / {@code config.getBaseUrl()}（不同账号可对应不同代理/区域 endpoint）。
     */
    private String resolveBaseUrl(LlmModel config, String provider, String accountBaseUrl) {
        // 账号链下沉：优先用 per-account baseUrl 覆盖。
        if (!StringHelper.isEmpty(accountBaseUrl)) {
            return accountBaseUrl;
        }

        String baseUrlKey = StringHelper.replace(
            io.nop.ai.core.AiCoreConstants.CONFIG_VAR_LLM_BASE_URL,
            io.nop.ai.core.AiCoreConstants.PLACE_HOLDER_LLM_NAME,
            provider
        );
        String baseUrl = (String) io.nop.api.core.config.AppConfig.var(baseUrlKey);

        if (StringHelper.isEmpty(baseUrl)) {
            baseUrl = config.getBaseUrl();
        }

        if (StringHelper.isEmpty(baseUrl)) {
            throw new NopException(ERR_AI_SERVICE_NO_BASE_URL).param(ARG_LLM_NAME, provider);
        }

        return baseUrl;
    }

    /**
     * 检查并应用速率限制（MA6.3-AR-6）：限时 {@code tryAcquire} 替代旧的无限阻塞
     * {@code acquire()}——配额耗尽时在 {@code nop.ai.service.rate-limit-acquire-timeout}
     * 内等待许可，超时抛 {@code ERR_AI_RATE_LIMITED}（携带 httpStatus=429，经
     * {@code LlmErrorClassifier} 判为 RATE_LIMITED 可重试，与上层 ReAct 重试循环联动）。
     * 失败面不静默：错误可被上层观察/重试。
     *
     * <p>per-tenant 配额：当前实现无 tenant 身份来源（无 ITenantResolver、无请求头解析），
     * 按 MA6.3-AR-6 裁定为<b>文档化扩展点</b>——子类可覆盖
     * {@link #createRateLimiter(double)}（按需 per-tenant key 建 limiter）或本方法
     * 引入自己的租户维度；跨 JVM 分布式限流同样为文档化扩展点（替换
     * {@code IRateLimiter} 实现即可，接口已抽象 tryAcquire 语义）。
     */
    void checkRateLimit(String provider, LlmModel config) {
        if (config.getRateLimit() == null) {
            return;
        }

        IRateLimiter rateLimiter = rateLimiters.computeIfAbsent(provider, k -> {
            LOG.debug("nop.ai.create-rate-limiter: provider={}, rate={}", provider, config.getRateLimit());
            return createRateLimiter(config.getRateLimit());
        });

        long timeoutMs = CFG_AI_SERVICE_RATE_LIMIT_ACQUIRE_TIMEOUT.get();
        if (!rateLimiter.tryAcquire(1, timeoutMs)) {
            throw new NopException(ERR_AI_RATE_LIMITED)
                    .param(ARG_LLM_NAME, provider)
                    .param(ARG_HTTP_STATUS, 429);
        }
    }

    private boolean shouldLogMessage(LlmModel config) {
        return CFG_AI_SERVICE_LOG_MESSAGE.get() && config.isLogMessage();
    }

    /**
     * 创建速率限制器（可由子类覆盖）
     */
    protected IRateLimiter createRateLimiter(double rate) {
        return new DefaultRateLimiter(rate);
    }

    /**
     * 将流式响应汇聚为 ChatResponse
     *
     * <p>错误路径（设计 §3.4）：流式 {@code onError} 收到的异常携带 Phase 1 挂上的
     * {@code ARG_BODY} + {@code ARG_HTTP_STATUS} + {@code ARG_RESPONSE_HEADERS}（含
     * Retry-After）。本方法从异常取出这些信息，经 {@code dialect.parseErrorResponse(...)}
     * 规范化为携带 {@code errorClassification} 的错误 ChatResponse，并 {@code complete}
     * （不 exceptionally）——与 {@code callAsync} 非流式错误路径一致：响应级错误走 ChatResponse。
     * 已流出内容后的错误仍走异常（流式保护不变，由上层 {@code hasStreamedContent} 守卫）。</p>
     */
    protected CompletionStage<ChatResponse> aggregateStreamToResponse(ChatRequest request, ICancelToken cancelToken) {
        StreamAggregator aggregator = new StreamAggregator();
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        AtomicReference<Flow.Subscription> upstreamRef = new AtomicReference<>();
        // C7-001：inactivity 进度基准（订阅时刻起算，每个 chunk 推进）
        AtomicLong lastActivity = new AtomicLong(CoreMetrics.currentTimeMillis());

        String provider = LlmConfigHelper.getProvider(request.getOptions());
        LlmModel config = LlmConfigHelper.loadConfig(provider);
        ILlmDialect dialect = LlmDialectFactory.getDialect(config.getApiStyle());
        boolean logMessage = shouldLogMessage(config);

        callStream(request, cancelToken).subscribe(new Flow.Subscriber<ChatStreamChunk>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                upstreamRef.set(subscription);
                // ② 有界 request 窗口（替代 Integer.MAX_VALUE 全速聚合）
                subscription.request(STREAM_REQUEST_WINDOW);
            }

            @Override
            public void onNext(ChatStreamChunk item) {
                try {
                    aggregator.addChunk(item);
                } catch (Exception e) {
                    // ⑤ 聚合上界超界（或其他累积异常）：显式终态（R21 纪律），不悬挂
                    Flow.Subscription upstream = upstreamRef.get();
                    if (upstream != null)
                        upstream.cancel();
                    future.completeExceptionally(e);
                    return;
                }
                Flow.Subscription upstream = upstreamRef.get();
                if (upstream != null)
                    upstream.request(1);
                lastActivity.set(CoreMetrics.currentTimeMillis());
            }

            @Override
            public void onError(Throwable throwable) {
                ChatResponse errResponse = parseStreamError(throwable, dialect, config);
                if (errResponse != null) {
                    errResponse.setRequestId(request.getRequestId());
                    errResponse.setResponseTime(CoreMetrics.currentTimeMillis());
                    future.complete(errResponse);
                    // ④ 日志盘写异步化：移出终态回调线程，失败不阻断 future 完成（显式可见）
                    logResponseAsync(request, errResponse, logMessage);
                } else {
                    future.completeExceptionally(throwable);
                }
            }

            @Override
            public void onComplete() {
                // C7-002（R11 错误以成功交付族纪律）：收到过内容 chunk 但无终止信号
                // （finishReason/DONE）= 干净半关闭的截断流——按传输级错误显式失败，
                // 不以成功响应交付（带截断字符数诊断；零 chunk 空流保持既有成功契约）
                if (aggregator.hasReceivedChunks() && !aggregator.hasTerminationSignal()) {
                    future.completeExceptionally(new NopException(ERR_AI_STREAM_TERMINATION_SIGNAL_MISSING)
                            .param(ARG_LLM_NAME, provider)
                            .param(ARG_DETAIL, String.valueOf(aggregator.totalAccumulatedChars())));
                    return;
                }
                ChatResponse response = aggregator.toResponse();
                response.setRequestId(request.getRequestId());
                response.setResponseTime(CoreMetrics.currentTimeMillis());

                future.complete(response);
                // ④ 日志盘写异步化（先 complete 后记日志）：序列化/正则/写盘移出终态回调线程，
                // 日志失败不阻断 future 完成（失败 WARN 显式可见，rule 24 不吞）
                logResponseAsync(request, response, logMessage);
            }
        });

        // C7-001：全程总期限 + chunk 间 inactivity 上界（独立于 read-timeout 的 headers 语义）。
        // watchdog 复用平台调度设施（globalTimer，任务体为短动作：检查/取消/异常终态——
        // 非 MA-16 禁止的长阻塞体直连形态）；超时经 subscription.cancel + completeExceptionally
        // 走既有错误路径（R21 纪律），失败可见非静默。
        scheduleStreamWatchdogs(future, upstreamRef, lastActivity, provider);
        return future;
    }

    /**
     * ④（3.1-C3-003 悬挂面）：响应日志盘写异步化——logResponse（序列化/正则脱敏/写盘）
     * 移出终态回调线程，日志失败仅 WARN 可见，不阻断 future 完成。承载为平台受管
     * cached 池（daemon；不指向 globalTimer——阻塞盘写体不违反 MA-16 收窄后约束）。
     * <p>落点注记：异步化在 ChatServiceImpl 终态回调侧实现而非 DefaultChatLogger 内部
     * ——IChatLogger 同步契约保持不变（既有脱敏/落盘测试零回退），异步调度属回调方职责。
     */
    private void logResponseAsync(ChatRequest request, ChatResponse response, boolean logMessage) {
        if (!logMessage || chatLogger == null)
            return;
        CompletableFuture.runAsync(() -> {
            try {
                chatLogger.logResponse(request, response);
            } catch (Exception e) {
                LOG.warn("nop.ai.chat-log-write-fail: log write failed (ignored, response already delivered)", e);
            }
        }, GlobalExecutors.cachedThreadPool());
    }

    /**
     * C7-001 watchdog 装配：future 级总期限（{@code CoreMetrics.timeoutToExpireTime} 口径）
     * + chunk 间 inactivity 巡检（固定延迟轮询，非自旋）。终态时双 watchdog 取消，无残留。
     */
    private void scheduleStreamWatchdogs(CompletableFuture<ChatResponse> future,
                                         AtomicReference<Flow.Subscription> upstreamRef,
                                         AtomicLong lastActivity, String provider) {
        long totalTimeoutMs = CFG_AI_SERVICE_STREAM_TOTAL_TIMEOUT.get();
        long idleTimeoutMs = CFG_AI_SERVICE_STREAM_IDLE_TIMEOUT.get();
        IScheduledExecutor scheduler = GlobalExecutors.globalTimer();

        Future<?> totalTask = null;
        if (totalTimeoutMs > 0) {
            totalTask = scheduler.schedule(() -> {
                fireStreamTimeout(future, upstreamRef, provider, totalTimeoutMs, ERR_AI_STREAM_TOTAL_TIMEOUT);
                return null;
            }, totalTimeoutMs, TimeUnit.MILLISECONDS);
        }

        Future<?> idleTask = null;
        if (idleTimeoutMs > 0) {
            long tick = Math.max(100L, idleTimeoutMs / 10);
            idleTask = scheduler.scheduleWithFixedDelay(() -> {
                if (future.isDone())
                    return;
                long idle = CoreMetrics.currentTimeMillis() - lastActivity.get();
                if (idle > idleTimeoutMs)
                    fireStreamTimeout(future, upstreamRef, provider, idleTimeoutMs, ERR_AI_STREAM_IDLE_TIMEOUT);
            }, tick, tick, TimeUnit.MILLISECONDS);
        }

        Future<?> totalTaskRef = totalTask;
        if (totalTaskRef != null || idleTask != null) {
            Future<?> idleTaskRef = idleTask;
            future.whenComplete((v, e) -> {
                if (totalTaskRef != null)
                    totalTaskRef.cancel(false);
                if (idleTaskRef != null)
                    idleTaskRef.cancel(false);
            });
        }
    }

    private void fireStreamTimeout(CompletableFuture<ChatResponse> future,
                                   AtomicReference<Flow.Subscription> upstreamRef,
                                   String provider, long timeoutMs, ErrorCode code) {
        if (future.isDone())
            return;
        Flow.Subscription upstream = upstreamRef.get();
        if (upstream != null)
            upstream.cancel();
        future.completeExceptionally(new NopException(code)
                .param(ARG_LLM_NAME, provider)
                .param(ARG_TIMEOUT, timeoutMs));
    }

    /**
     * 从流式 {@code onError} 的异常中解析错误 ChatResponse。仅当异常是携带 HTTP 状态码的
     * {@link NopException}（即 {@code ServerEventPublisher} 非 2xx 抛出的响应级错误）时规范化；
     * 否则返回 null（传输级错误——无 HTTP 响应，无法构造 ChatResponse，仍 exceptionally）。
     */
    @SuppressWarnings("unchecked")
    private ChatResponse parseStreamError(Throwable throwable, ILlmDialect dialect, LlmModel config) {
        Throwable t = throwable;
        while (t != null) {
            if (t instanceof NopException) {
                NopException ex = (NopException) t;
                Object statusObj = ex.getParam(ARG_HTTP_STATUS);
                Integer httpStatus = statusObj instanceof Number ? ((Number) statusObj).intValue() : null;
                if (httpStatus == null) {
                    break;
                }
                Object bodyObj = ex.getParam(ARG_BODY);
                String body = bodyObj != null ? bodyObj.toString() : "";
                Object headersObj = ex.getParam(ARG_RESPONSE_HEADERS);
                Map<String, String> headers = headersObj instanceof Map
                        ? (Map<String, String>) headersObj : null;
                return dialect.parseErrorResponse(body, httpStatus, headers, config);
            }
            t = t.getCause();
        }
        return null;
    }

    /**
     * 流式响应汇聚器
     * <p>
     * Plan 328 Phase 3：按 item 类型维度汇聚 item 增量 chunk，产出与非流式 dialect
     * （plan 326）{@code response.messages} 同构的消息序列：
     * <ul>
     *   <li>{@link StreamItemType#text} 增量 → 累积为 {@code ChatAssistantMessage}（content）</li>
     *   <li>{@link StreamItemType#reasoning} 增量 → 累积为 {@code ChatReasoningMessage}</li>
     *   <li>{@link StreamItemType#tool_call} 增量（按 itemIndex 区分多调用）
     *       → 每个 item 收敛为 {@code ChatToolCallMessage}</li>
     * </ul>
     * 旧 {@code message} 字段保留填充（双轨过渡，content/think/toolCalls 寄居）。
     */
    static class StreamAggregator {
        private final StringBuilder textBuilder = new StringBuilder();
        private final StringBuilder reasoningBuilder = new StringBuilder();
        private final Map<Integer, ToolCallAccumulator> toolCallAccumulators = new LinkedHashMap<>();
        private String id;
        private String model;
        private String finishReason;
        private io.nop.ai.api.chat.messages.ChatUsage usage;

        // ⑤（3.1-C5-001 聚合无界面，plan 2505）：累积上界状态
        private final int maxAccumulatedChars = CFG_AI_SERVICE_STREAM_MAX_ACCUMULATED_CHARS.get();
        private final int maxToolCalls = CFG_AI_SERVICE_STREAM_MAX_TOOL_CALLS.get();
        private long totalAccumulatedChars;
        private boolean receivedChunks;
        private boolean terminationSignal;

        boolean hasReceivedChunks() {
            return receivedChunks;
        }

        boolean hasTerminationSignal() {
            return terminationSignal;
        }

        long totalAccumulatedChars() {
            return totalAccumulatedChars;
        }

        private void ensureCharBudget(int additional) {
            if (maxAccumulatedChars <= 0)
                return;
            if (totalAccumulatedChars + additional > maxAccumulatedChars) {
                throw new NopException(ERR_AI_STREAM_ACCUMULATION_EXCEEDED)
                        .param(ARG_DETAIL, "accumulatedChars=" + (totalAccumulatedChars + additional)
                                + ", maxChars=" + maxAccumulatedChars);
            }
        }

        private void ensureToolCallBudget(Integer index) {
            if (maxToolCalls <= 0)
                return;
            if (!toolCallAccumulators.containsKey(index) && toolCallAccumulators.size() >= maxToolCalls) {
                throw new NopException(ERR_AI_STREAM_ACCUMULATION_EXCEEDED)
                        .param(ARG_DETAIL, "toolCallEntries=" + (toolCallAccumulators.size() + 1)
                                + ", maxToolCalls=" + maxToolCalls);
            }
        }

        void addChunk(ChatStreamChunk chunk) {
            receivedChunks = true;
            if (chunk.getId() != null) {
                this.id = chunk.getId();
            }
            if (chunk.getModel() != null) {
                this.model = chunk.getModel();
            }
            if (chunk.getFinishReason() != null) {
                this.finishReason = chunk.getFinishReason();
            }
            // C7-002：终止信号消费（finishReason 或 DONE 相位任一即视为收到终止信号）
            if (chunk.getFinishReason() != null || chunk.getPhase() == StreamItemPhase.DONE) {
                this.terminationSignal = true;
            }
            if (chunk.getUsage() != null) {
                this.usage = chunk.getUsage();
            }

            StreamItemType type = chunk.getItemType();
            if (type == null) {
                return;
            }
            switch (type) {
                case text:
                    if (chunk.getDelta() != null) {
                        ensureCharBudget(chunk.getDelta().length());
                        textBuilder.append(chunk.getDelta());
                        totalAccumulatedChars += chunk.getDelta().length();
                    }
                    break;
                case reasoning:
                    if (chunk.getDelta() != null) {
                        ensureCharBudget(chunk.getDelta().length());
                        reasoningBuilder.append(chunk.getDelta());
                        totalAccumulatedChars += chunk.getDelta().length();
                    }
                    break;
                case tool_call:
                    Integer index = chunk.getItemIndex() != null ? chunk.getItemIndex() : 0;
                    ensureToolCallBudget(index);
                    ToolCallAccumulator acc = toolCallAccumulators.computeIfAbsent(index, k -> new ToolCallAccumulator());
                    int argumentLength = (chunk.getDelta() != null ? chunk.getDelta().length() : 0)
                            + (chunk.getArguments() != null ? chunk.getArguments().length() : 0);
                    ensureCharBudget(argumentLength);
                    acc.apply(chunk);
                    totalAccumulatedChars += argumentLength;
                    break;
                default:
                    break;
            }
        }

        ChatResponse toResponse() {
            ChatResponse response = new ChatResponse();
            response.setId(id);
            response.setModel(model);
            response.setFinishReason(finishReason);
            response.setUsage(usage);

            String text = textBuilder.toString();
            String reasoning = reasoningBuilder.toString();

            List<io.nop.ai.api.chat.messages.ChatToolCall> toolCalls = new ArrayList<>();
            for (ToolCallAccumulator acc : toolCallAccumulators.values()) {
                io.nop.ai.api.chat.messages.ChatToolCall toolCall = acc.toToolCall();
                if (toolCall != null) {
                    toolCalls.add(toolCall);
                }
            }

            io.nop.ai.api.chat.messages.ChatAssistantMessage message =
                    new io.nop.ai.api.chat.messages.ChatAssistantMessage();
            message.setContent(text.isEmpty() ? null : text);

            // Plan 329：单一拆分模型产出。messages 按语义顺序 reasoning → assistant text → tool_calls，
            // 与非流式 dialect 产出的 messages 序列同构。
            List<io.nop.ai.api.chat.messages.ChatMessage> messages = new ArrayList<>();
            if (!reasoning.isEmpty()) {
                messages.add(new io.nop.ai.api.chat.messages.ChatReasoningMessage(reasoning));
            }
            messages.add(message);
            for (io.nop.ai.api.chat.messages.ChatToolCall toolCall : toolCalls) {
                messages.add(io.nop.ai.api.chat.messages.ChatToolCallMessage.fromChatToolCall(toolCall));
            }
            response.setMessages(messages);

            return response;
        }
    }

    /**
     * 工具调用增量累积器（按 itemIndex 维护，支持多 tool_call 并行）。
     * <p>
     * ADDED 阶段的 {@code delta} 承载函数名，{@code callId} 为调用 id；
     * DELTA 阶段的 {@code delta} 承载 arguments JSON 片段，逐步拼接。
     */
    private static class ToolCallAccumulator {
        String callId;
        String name;
        final StringBuilder argumentsBuilder = new StringBuilder();

        void apply(ChatStreamChunk chunk) {
            if (chunk.getCallId() != null) {
                this.callId = chunk.getCallId();
            }
            if (chunk.getDelta() != null) {
                if (chunk.getPhase() == StreamItemPhase.ADDED) {
                    this.name = chunk.getDelta();
                } else {
                    argumentsBuilder.append(chunk.getDelta());
                }
            }
            // 完整 arguments 通道（Gemini/Ollama 型单事件完整下发）：与 DELTA 片段同一拼装语义
            if (chunk.getArguments() != null) {
                argumentsBuilder.append(chunk.getArguments());
            }
        }

        io.nop.ai.api.chat.messages.ChatToolCall toToolCall() {
            if (callId == null && name == null && argumentsBuilder.length() == 0) {
                return null;
            }
            io.nop.ai.api.chat.messages.ChatToolCall toolCall =
                    new io.nop.ai.api.chat.messages.ChatToolCall();
            toolCall.setId(callId);
            toolCall.setName(name);

            String argsStr = argumentsBuilder.toString();
            if (!argsStr.isEmpty()) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> args = (Map<String, Object>) JSON.parse(argsStr);
                    toolCall.setArguments(args);
                } catch (Exception e) {
                    // 畸形 arguments JSON 不静默吞（P2-ROUND4-CALL-PATH Phase 3）：WARN 日志
                    // （含 tool call id/name）+ arguments 置 null，与合法 {}（空非 null Map）可区分。
                    LOG.warn("nop.ai.tool-call-args-parse-fail: callId={}, name={}, malformed tool arguments JSON",
                            callId, name);
                    toolCall.setArguments(null);
                }
            } else {
                toolCall.setArguments(new LinkedHashMap<>());
            }
            return toolCall;
        }
    }
}
