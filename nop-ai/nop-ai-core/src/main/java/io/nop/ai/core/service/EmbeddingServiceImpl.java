package io.nop.ai.core.service;

import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.ErrorClassification;
import io.nop.ai.api.credential.IAiModelCredentialResolver;
import io.nop.ai.core.AiCoreConfigs;
import io.nop.ai.core.api.embedding.EmbeddingOptions;
import io.nop.ai.core.api.embedding.IEmbeddingModel;
import io.nop.ai.core.api.support.VectorData;
import io.nop.ai.core.dialect.ILlmDialect;
import io.nop.ai.core.dialect.LlmDialectFactory;
import io.nop.ai.core.model.ApiStyle;
import io.nop.ai.core.model.LlmAccountModel;
import io.nop.ai.core.model.LlmModel;
import io.nop.ai.core.reliability.AccountChain;
import io.nop.ai.core.reliability.IRetryPolicy;
import io.nop.ai.core.reliability.LlmErrorClassifier;
import io.nop.ai.core.reliability.NoRetryPolicy;
import io.nop.ai.core.reliability.RetryContext;
import io.nop.ai.core.reliability.RetryOutcome;
import io.nop.ai.core.reliability.StandardRetryPolicy;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.ratelimit.DefaultRateLimiter;
import io.nop.commons.concurrent.ratelimit.IRateLimiter;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static io.nop.ai.core.AiCoreConfigs.CFG_AI_EMBEDDING_DEFAULT_LLM;
import static io.nop.ai.core.AiCoreConfigs.CFG_AI_SERVICE_RATE_LIMIT_ACQUIRE_TIMEOUT;
import static io.nop.ai.core.AiCoreConfigs.CFG_AI_SERVICE_READ_TIMEOUT;
import static io.nop.ai.core.NopAiCoreErrors.ARG_API_STYLE;
import static io.nop.ai.core.NopAiCoreErrors.ARG_DETAIL;
import static io.nop.ai.core.NopAiCoreErrors.ARG_HTTP_STATUS;
import static io.nop.ai.core.NopAiCoreErrors.ARG_LLM_NAME;
import static io.nop.ai.core.NopAiCoreErrors.ARG_OPTION_NAME;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_CORE_INVALID_ARGUMENT;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_EMBEDDING_ACCOUNT_CHAIN_EXHAUSTED;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_EMBEDDING_EMPTY_RESPONSE;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_EMBEDDING_NO_EMBED_URL;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_EMBEDDING_NO_PROVIDER;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_EMBEDDING_UNSUPPORTED_API_STYLE;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_RATE_LIMITED;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_SERVICE_HTTP_ERROR;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_SERVICE_NO_BASE_URL;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_SERVICE_OPTION_NOT_SET;

/**
 * K1（plan knowledge-rag/01）：基于 llm.xml 配置的 OpenAI 兼容 embedding 客户端，
 * {@link IEmbeddingModel} 的首个平台生产实现。与 {@link ChatServiceImpl} 同构：
 * provider 路由（{@code EmbeddingOptions.provider} > {@code nop.ai.embedding.default-llm}
 * > fail-loud）、{@code LlmConfigHelper} 配置加载、限流（{@code rateLimit} +
 * {@code tryAcquire} 超时抛 {@code ERR_AI_RATE_LIMITED}）、baseUrl/apiKey 优先级链、
 * 可靠性双通道（响应级错误经 {@code ILlmDialect.parseErrorResponse} 应用
 * {@code <errorMappings>}；传输级异常经 {@code LlmErrorClassifier}）驱动
 * {@link IRetryPolicy} 重试与 {@link AccountChain} 账号链 failover。链耗尽 /
 * 分类 STOP 时 fail-loud，不静默降级。
 */
public class EmbeddingServiceImpl implements IEmbeddingModel {
    private static final Logger LOG = LoggerFactory.getLogger(EmbeddingServiceImpl.class);

    private static final int MAX_FALLBACK_STEPS = 16;

    private IHttpClient httpClient;

    private IAiModelCredentialResolver credentialResolver;

    private IRetryPolicy retryPolicy = new StandardRetryPolicy();

    private volatile ScheduledExecutorService retryScheduler;
    private final Object schedulerLock = new Object();

    private final Map<String, IRateLimiter> rateLimiters = new ConcurrentHashMap<>();

    @Inject
    public void setHttpClient(IHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * 凭证解析器可选注入（与 ChatServiceImpl 同款 {@code @Nullable} 语义）：
     * 未装配时回退 {@code resolveApiKey}。
     */
    @Inject
    public void setCredentialResolver(@Nullable IAiModelCredentialResolver credentialResolver) {
        this.credentialResolver = credentialResolver;
    }

    /**
     * 普通 setter（非 IoC 注入——容器无 {@code IRetryPolicy} bean）：
     * 默认 {@link StandardRetryPolicy}，可覆盖为 {@link NoRetryPolicy}。
     */
    public void setRetryPolicy(IRetryPolicy retryPolicy) {
        if (retryPolicy == null)
            throw new NopException(ERR_AI_CORE_INVALID_ARGUMENT)
                    .param(ARG_DETAIL, "retryPolicy must not be null");
        this.retryPolicy = retryPolicy;
    }

    /**
     * 可注入重试调度器（普通 setter）：默认惰性创建 daemon 单线程调度器；
     * 测试可注入直接执行实现以消除延迟等待。传入 null 恢复默认。
     */
    public void setRetryScheduler(ScheduledExecutorService retryScheduler) {
        this.retryScheduler = retryScheduler;
    }

    private ScheduledExecutorService scheduler() {
        ScheduledExecutorService sched = this.retryScheduler;
        if (sched != null)
            return sched;
        synchronized (schedulerLock) {
            if (this.retryScheduler == null) {
                ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, r -> {
                    Thread t = new Thread(r, "nop-ai-embedding-retry");
                    t.setDaemon(true);
                    return t;
                });
                executor.setRemoveOnCancelPolicy(true);
                this.retryScheduler = executor;
            }
            return this.retryScheduler;
        }
    }

    @Override
    public CompletionStage<VectorData> embedAsync(io.nop.ai.core.api.document.AiDocument doc,
                                                  EmbeddingOptions options) {
        String text = doc == null ? null : doc.getContent();
        if (StringHelper.isBlank(text)) {
            throw new NopException(ERR_AI_CORE_INVALID_ARGUMENT)
                    .param(ARG_DETAIL, "embedding text must not be blank");
        }
        return embedAllAsync(List.of(doc), options).thenApply(list -> list.get(0));
    }

    @Override
    public CompletionStage<List<VectorData>> embedAllAsync(
            List<io.nop.ai.core.api.document.AiDocument> docs, EmbeddingOptions options) {
        if (docs == null || docs.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        List<String> texts = new ArrayList<>(docs.size());
        for (io.nop.ai.core.api.document.AiDocument doc : docs) {
            texts.add(doc == null ? null : doc.getContent());
        }

        String provider = resolveProvider(options);
        LlmModel config = LlmConfigHelper.loadConfig(provider);
        validateConfig(provider, config);

        String model = resolveModel(options, config, provider);
        checkRateLimit(provider, config);

        EmbedCall call = new EmbedCall(provider, config, model, texts);
        CompletableFuture<List<VectorData>> future = new CompletableFuture<>();
        call.start(future);
        return future;
    }

    private String resolveProvider(EmbeddingOptions options) {
        if (options != null && !StringHelper.isBlank(options.getProvider())) {
            return options.getProvider();
        }
        String def = CFG_AI_EMBEDDING_DEFAULT_LLM.get();
        if (!StringHelper.isBlank(def)) {
            return def;
        }
        throw new NopException(ERR_AI_EMBEDDING_NO_PROVIDER);
    }

    private void validateConfig(String provider, LlmModel config) {
        if (config.getApiStyle() != ApiStyle.openai) {
            throw new NopException(ERR_AI_EMBEDDING_UNSUPPORTED_API_STYLE)
                    .param(ARG_LLM_NAME, provider)
                    .param(ARG_API_STYLE, String.valueOf(config.getApiStyle()));
        }
        if (StringHelper.isBlank(config.getEmbedUrl())) {
            throw new NopException(ERR_AI_EMBEDDING_NO_EMBED_URL).param(ARG_LLM_NAME, provider);
        }
    }

    private String resolveModel(EmbeddingOptions options, LlmModel config, String provider) {
        if (options != null && !StringHelper.isBlank(options.getModel())) {
            return options.getModel();
        }
        if (!StringHelper.isBlank(config.getDefaultModel())) {
            return config.getDefaultModel();
        }
        throw new NopException(ERR_AI_SERVICE_OPTION_NOT_SET)
                .param(ARG_LLM_NAME, provider)
                .param(ARG_OPTION_NAME, "model");
    }

    void checkRateLimit(String provider, LlmModel config) {
        if (config.getRateLimit() == null) {
            return;
        }
        IRateLimiter rateLimiter = rateLimiters.computeIfAbsent(provider, k -> {
            LOG.debug("nop.ai.embedding.create-rate-limiter: provider={}, rate={}", provider,
                    config.getRateLimit());
            return new DefaultRateLimiter(config.getRateLimit());
        });
        long timeoutMs = CFG_AI_SERVICE_RATE_LIMIT_ACQUIRE_TIMEOUT.get();
        if (!rateLimiter.tryAcquire(1, timeoutMs)) {
            throw new NopException(ERR_AI_RATE_LIMITED)
                    .param(ARG_LLM_NAME, provider)
                    .param(ARG_HTTP_STATUS, 429);
        }
    }

    /**
     * 单次 embedding 调用的可重试状态（per-call 有状态，线程封闭在完成回调链内）。
     */
    private final class EmbedCall {
        final String provider;
        final LlmModel config;
        final String model;
        final List<String> texts;
        final ILlmDialect dialect;
        final AccountChain accountChain;
        int attempt = 0;
        int fallbackSteps = 0;
        LlmAccountModel currentAccount;

        EmbedCall(String provider, LlmModel config, String model, List<String> texts) {
            this.provider = provider;
            this.config = config;
            this.model = model;
            this.texts = texts;
            this.dialect = LlmDialectFactory.getDialect(config.getApiStyle());
            this.accountChain = new AccountChain(LlmConfigHelper.resolveAccountChain(provider));
        }

        void start(CompletableFuture<List<VectorData>> future) {
            dispatch(future);
        }

        void dispatch(CompletableFuture<List<VectorData>> future) {
            attempt++;
            try {
                String apiKey = resolveEffectiveApiKey();
                String baseUrl = resolveBaseUrl();
                HttpRequest request = buildRequest(baseUrl, apiKey);
                httpClient.fetchAsync(request, null).whenComplete((resp, err) -> {
                    try {
                        if (err != null) {
                            onTransportError(err, future);
                        } else if (resp.getHttpStatus() != 200) {
                            onResponseError(resp, future);
                        } else {
                            future.complete(parseSuccess(resp));
                        }
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                });
            } catch (Exception e) {
                // 配置缺失（如备用账号链耗尽后 baseUrl 无法解析）不得静默丢失：
                // dispatch 可能运行在 whenComplete 回调链内，异常必须落到 future。
                future.completeExceptionally(e);
            }
        }

        HttpRequest buildRequest(String baseUrl, String apiKey) {
            HttpRequest request = new HttpRequest();
            request.setMethod("POST");
            request.setUrl(baseUrl + config.getEmbedUrl());
            dialect.setHeaders(request, apiKey, config.getApiKeyHeader());
            request.setTimeout(CFG_AI_SERVICE_READ_TIMEOUT.get());

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("input", texts);
            request.setBody(JsonTool.serialize(body, false));
            return request;
        }

        String resolveEffectiveApiKey() {
            String accountKey = currentAccount != null ? currentAccount.getApiKey() : null;
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

        String resolveBaseUrl() {
            String accountBaseUrl = currentAccount != null ? currentAccount.getBaseUrl() : null;
            if (!StringHelper.isEmpty(accountBaseUrl)) {
                return accountBaseUrl;
            }
            String baseUrlKey = StringHelper.replace(
                    io.nop.ai.core.AiCoreConstants.CONFIG_VAR_LLM_BASE_URL,
                    io.nop.ai.core.AiCoreConstants.PLACE_HOLDER_LLM_NAME,
                    provider);
            String baseUrl = (String) io.nop.api.core.config.AppConfig.var(baseUrlKey);
            if (StringHelper.isEmpty(baseUrl)) {
                baseUrl = config.getBaseUrl();
            }
            if (StringHelper.isEmpty(baseUrl)) {
                throw new NopException(ERR_AI_SERVICE_NO_BASE_URL).param(ARG_LLM_NAME, provider);
            }
            return baseUrl;
        }

        void onTransportError(Throwable err, CompletableFuture<List<VectorData>> future) {
            handleFailure(err, LlmErrorClassifier.classify(err), null, future);
        }

        void onResponseError(io.nop.http.api.client.IHttpResponse resp,
                             CompletableFuture<List<VectorData>> future) {
            ChatResponse errResponse = dialect.parseErrorResponse(resp.getBodyAsString(),
                    resp.getHttpStatus(), resp.getHeaders(), config);
            NopException error = new NopException(ERR_AI_SERVICE_HTTP_ERROR)
                    .param(ARG_LLM_NAME, provider)
                    .param(ARG_HTTP_STATUS, resp.getHttpStatus());
            handleFailure(error, errResponse.getErrorClassification(),
                    errResponse.getRetryAfterMs(), future);
        }

        void handleFailure(Throwable error, ErrorClassification classification, Long retryAfterMs,
                           CompletableFuture<List<VectorData>> future) {
            if (classification == null) {
                classification = ErrorClassification.NON_TRANSIENT;
            }
            RetryContext ctx = new RetryContext(attempt, error, classification, false, retryAfterMs);
            RetryOutcome outcome = retryPolicy.shouldRetry(ctx);
            if (outcome.isRetry()) {
                long delayMs = Math.max(0, outcome.getDelayMs());
                scheduleRetry(delayMs, () -> dispatch(future));
                return;
            }
            if (outcome.isFallback()) {
                if (fallbackSteps >= MAX_FALLBACK_STEPS) {
                    future.completeExceptionally(new NopException(
                            ERR_AI_EMBEDDING_ACCOUNT_CHAIN_EXHAUSTED).param(ARG_LLM_NAME, provider));
                    return;
                }
                LlmAccountModel next = accountChain.next();
                if (next == null) {
                    future.completeExceptionally(new NopException(
                            ERR_AI_EMBEDDING_ACCOUNT_CHAIN_EXHAUSTED).param(ARG_LLM_NAME, provider)
                            .param(ARG_HTTP_STATUS, lastHttpStatus(error)));
                    return;
                }
                fallbackSteps++;
                currentAccount = next;
                LOG.info("nop.ai.embedding.account-fallback: provider={}, attempt={}, accountId={}",
                        provider, attempt, next.getId());
                dispatch(future);
                return;
            }
            future.completeExceptionally(error);
        }

        int lastHttpStatus(Throwable error) {
            if (error instanceof NopException) {
                Object status = ((NopException) error).getParam(ARG_HTTP_STATUS);
                if (status instanceof Number) {
                    return ((Number) status).intValue();
                }
            }
            return 0;
        }

        @SuppressWarnings("unchecked")
        List<VectorData> parseSuccess(io.nop.http.api.client.IHttpResponse resp) {
            Map<String, Object> body = JsonTool.parseMap(resp.getBodyAsString());
            if (body == null) {
                throw new NopException(ERR_AI_EMBEDDING_EMPTY_RESPONSE).param(ARG_LLM_NAME, provider);
            }
            Object dataObj = body.get("data");
            if (!(dataObj instanceof List) || ((List<Object>) dataObj).isEmpty()) {
                throw new NopException(ERR_AI_EMBEDDING_EMPTY_RESPONSE).param(ARG_LLM_NAME, provider);
            }
            List<Map<String, Object>> data = (List<Map<String, Object>>) dataObj;
            List<Map<String, Object>> sorted = new ArrayList<>(data);
            sorted.sort(Comparator.comparingInt(item -> {
                Object idx = item.get("index");
                return idx instanceof Number ? ((Number) idx).intValue() : Integer.MAX_VALUE;
            }));
            if (sorted.size() < texts.size()) {
                throw new NopException(ERR_AI_EMBEDDING_EMPTY_RESPONSE).param(ARG_LLM_NAME, provider);
            }

            List<VectorData> result = new ArrayList<>(texts.size());
            for (int i = 0; i < texts.size(); i++) {
                Map<String, Object> item = sorted.get(i);
                Object embObj = item == null ? null : item.get("embedding");
                if (!(embObj instanceof List)) {
                    throw new NopException(ERR_AI_EMBEDDING_EMPTY_RESPONSE).param(ARG_LLM_NAME, provider);
                }
                List<Object> embedding = (List<Object>) embObj;
                double[] vector = new double[embedding.size()];
                for (int j = 0; j < embedding.size(); j++) {
                    Object v = embedding.get(j);
                    vector[j] = v instanceof Number ? ((Number) v).doubleValue() : 0d;
                }
                VectorData vd = new VectorData();
                vd.setVector(vector);
                Object respModel = body.get("model");
                if (respModel != null) {
                    vd.addMetadata("model", respModel);
                }
                vd.addMetadata("dimension", vector.length);
                Object usage = body.get("usage");
                if (usage instanceof Map) {
                    vd.addMetadata("usage", usage);
                }
                result.add(vd);
            }
            return result;
        }
    }

    /**
     * 重试延迟调度点（protected 供测试以同线程立即执行方式覆盖）。
     */
    protected void scheduleRetry(long delayMs, Runnable task) {
        scheduler().schedule(task, delayMs, TimeUnit.MILLISECONDS);
    }
}
