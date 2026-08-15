package io.nop.ai.gateway.failover;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.ErrorClassification;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.core.NopAiCoreErrors;
import io.nop.ai.core.NopAiCoreException;
import io.nop.ai.core.reliability.CircuitState;
import io.nop.ai.core.reliability.LlmErrorClassifier;
import io.nop.ai.core.reliability.ThresholdBreaker;
import io.nop.ai.core.routing.ConcurrencyRegistry;
import io.nop.ai.core.routing.ISelectionStrategy;
import io.nop.ai.core.routing.ModelClassCandidate;
import io.nop.ai.core.routing.ModelClassRouter;
import io.nop.ai.core.service.LlmConfigHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;
import io.nop.http.api.HttpApiErrors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/**
 * 本地形态透明账号 failover 适配器（plan 2026-08-15-1116-2，设计 §3.1/§3.2/§3.3/§4.4）。
 *
 * <p>实现 {@link IChatService}（nop-ai-api，非已废弃 {@code IAiChatService}），包装
 * {@code ChatServiceImpl}（bean {@code nopChatService}）：账号切换经
 * {@code ChatOptions.accountKey/accountBaseUrl/model/provider} 四字段下沉（W5
 * {@code ModelClassRouter.toChatOptions}），不重建管线。
 *
 * <p><b>非流式 {@link #callAsync}</b>（LOCAL-02）：选择 → 下沉 → 并发计数 acquire → 委托 →
 * 全终止路径 release → 失败分类（响应级 {@code ChatResponse.errorClassification} 优先 +
 * 传输异常级 {@code LlmErrorClassifier}）→ 动作表（QUOTA/AUTH/RATE_LIMITED/TRANSIENT →
 * 切换 + 熔断记账 + 预算；NON_TRANSIENT → 不切换直接失败；CACHE_STATE_LOST → 原地重发同一候选
 * 一次）→ 重选（含 provider 链扩展）→ 预算耗尽 fail-loud。
 *
 * <p><b>流式 {@link #callStream}</b>（LOCAL-03，经 {@link FailoverStreamFlow}）：首段缓冲窗口
 * （N 元素 / T 毫秒，先到者越窗）→ 窗口内（未转发任何数据）失败 → 分类（流式路径经
 * {@code NopException} ARG_HTTP_STATUS/ARG_BODY 恢复响应级分类）+ 熔断记账 + 重选 + 重订阅
 * （新 {@code ChatOptions} 重调 {@code callStream}，消耗重试预算）→ 窗口外失败断流报错；
 * 并发计数挂钩 {@code callStream} 调用时刻 +1 / attempt 终止或取消 -1；per-attempt 独立
 * {@code ICancelToken}（调用方取消传播到当前 attempt，绝不直接取消调用方 token）；
 * 取消顺序 = 先 cancel 内部订阅（唤醒阻塞 submit）→ 再 cancel attempt token（断 HTTP）。
 *
 * <p><b>裁定落档</b>（Phase 1）：缓冲窗口 N=10/T=1000ms、重试预算 2 次、独立 bean 不覆盖
 * {@code ioc:default}、熔断探活恢复（全池饱和时对 OPEN 候选 {@code allowCall} 探活）、
 * acquire/release 全终止路径配对 + acquire 后复查（并发上限竞态）、breaker/registry 进程
 * 共享单例（bean 注入，不按 router 缺省构造）、不消费 {@code IRetryPolicy}、无路由组直通
 * 零回归（不计数）。全部细则见 plan Phase 1 裁定落档 D1-D10。
 *
 * <p><b>无静默跳过</b>：重试预算耗尽 / NON_TRANSIENT / 全池饱和均显式失败（错误响应或抛错），
 * 无空 catch；release 下溢由 {@link ConcurrencyRegistry} fail-fast 暴露（不吞）。
 */
public class ChatServiceFailoverAdapter implements IChatService {

    public static final int DEFAULT_BUFFER_SIZE = 10;
    public static final long DEFAULT_BUFFER_TIME_MS = 1000L;
    public static final int DEFAULT_RETRY_BUDGET = 2;

    private IChatService delegate;
    private ISelectionStrategy strategy;
    private ThresholdBreaker breaker;
    private ConcurrencyRegistry registry;
    private IFailoverMetrics metrics;
    private int bufferSize = DEFAULT_BUFFER_SIZE;
    private long bufferTimeMs = DEFAULT_BUFFER_TIME_MS;
    private int retryBudget = DEFAULT_RETRY_BUDGET;

    public void setDelegate(IChatService delegate) {
        this.delegate = delegate;
    }

    /**
     * 可选：选择策略（null = 默认策略 {@code DefaultSelectionStrategy}）。
     */
    public void setStrategy(ISelectionStrategy strategy) {
        this.strategy = strategy;
    }

    /**
     * 进程共享熔断器（Phase 1 D8/M-5：必须注入单例，不得按 router 缺省构造）。
     */
    public void setBreaker(ThresholdBreaker breaker) {
        this.breaker = breaker;
    }

    /**
     * 进程共享并发注册表（Phase 1 D8/M-5）。
     */
    public void setRegistry(ConcurrencyRegistry registry) {
        this.registry = registry;
    }

    /**
     * 可观测性指标服务（W8 OBS-01；null = 观测 no-op——Phase 1 null-object/fail-fast
     * 裁定：标准部署经 beans.xml 非 optional ref fail-fast，null 仅测试/手工构造形态）。
     */
    public void setMetrics(IFailoverMetrics metrics) {
        this.metrics = metrics;
    }

    /**
     * 首段缓冲窗口元素数 N（Phase 1 D1；>= 1）。
     */
    public void setBufferSize(int bufferSize) {
        if (bufferSize < 1) {
            throw new NopAiCoreException(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG)
                    .param(NopAiCoreErrors.ARG_MSG, "ChatServiceFailoverAdapter bufferSize must be >= 1: " + bufferSize);
        }
        this.bufferSize = bufferSize;
    }

    /**
     * 首段缓冲窗口时长 T 毫秒（Phase 1 D1；>= 0）。
     */
    public void setBufferTimeMs(long bufferTimeMs) {
        if (bufferTimeMs < 0) {
            throw new NopAiCoreException(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG)
                    .param(NopAiCoreErrors.ARG_MSG, "ChatServiceFailoverAdapter bufferTimeMs must be >= 0: " + bufferTimeMs);
        }
        this.bufferTimeMs = bufferTimeMs;
    }

    /**
     * 重试预算（初始 attempt 之后最多重试次数；Phase 1 D3；>= 0 = 不重试）。
     */
    public void setRetryBudget(int retryBudget) {
        if (retryBudget < 0) {
            throw new NopAiCoreException(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG)
                    .param(NopAiCoreErrors.ARG_MSG, "ChatServiceFailoverAdapter retryBudget must be >= 0: " + retryBudget);
        }
        this.retryBudget = retryBudget;
    }

    public IChatService getDelegate() {
        return delegate;
    }

    public ThresholdBreaker getBreaker() {
        return breaker;
    }

    public ConcurrencyRegistry getRegistry() {
        return registry;
    }

    public IFailoverMetrics getMetrics() {
        return metrics;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public long getBufferTimeMs() {
        return bufferTimeMs;
    }

    public int getRetryBudget() {
        return retryBudget;
    }

    // ======================= 非流式（LOCAL-02） =======================

    @Override
    public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
        ModelClassRouter router = routerFor(request);
        if (!router.hasRoutingGroup()) {
            // 无路由组：直通零回归（不计数、不选择、不缓冲——Phase 1 D7/M-6b）。
            return delegate.callAsync(request, cancelToken);
        }
        return callAsyncAttempt(request, router, cancelToken, 0);
    }

    private CompletionStage<ChatResponse> callAsyncAttempt(ChatRequest request, ModelClassRouter router,
                                                           ICancelToken cancelToken, int attempt) {
        // 每次 attempt 前检查调用方取消（Minor-1）：干净终态终止，不记账不耗预算。
        if (cancelToken != null && cancelToken.isCancelled()) {
            return FutureHelper.reject(new CancellationException(
                    cancelToken.getCancelReason() != null ? cancelToken.getCancelReason() : "call cancelled"));
        }
        ModelClassCandidate candidate;
        try {
            candidate = selectNextWithProbe(router, attempt == 0, request);
        } catch (NopException e) {
            // 全池饱和 fail-loud（主动路径不记熔断、不耗预算——Phase 1 D5b/D6）。
            return FutureHelper.reject(e);
        }
        // 并发计数 acquire（发起时）+ 复查（M-6a）：超限 → release + 视为饱和跳过 → 重选。
        int count = registry.acquire(candidate.getProvider(), candidate.getAccountKey());
        if (metrics != null) {
            metrics.onConcurrencyAcquire(candidate.getProvider(), candidate.getAccountKey());
        }
        if (isOverConcurrencyLimit(candidate, count)) {
            registry.release(candidate.getProvider(), candidate.getAccountKey());
            if (metrics != null) {
                metrics.onConcurrencyRelease(candidate.getProvider(), candidate.getAccountKey());
            }
            return callAsyncAttempt(request, router, cancelToken, attempt);
        }
        if (attempt > 0 && metrics != null) {
            // attempt > 0 且非 CACHE_STATE_LOST 原地重发（该路径不经本方法）→ 已切换至新候选。
            // 超限重选递归保留原 attempt 值，此处仅对最终确认的候选计一次。
            metrics.onSwitchAttempt(candidate.getProvider(), candidate.getModel(), candidate.getAccountKey());
        }
        ChatOptions sunk = router.toChatOptions(request.getOptions(), candidate);
        return callAsyncWithOptions(request, router, cancelToken, attempt, candidate, sunk);
    }

    private CompletionStage<ChatResponse> callAsyncWithOptions(ChatRequest request, ModelClassRouter router,
                                                               ICancelToken cancelToken, int attempt,
                                                               ModelClassCandidate candidate, ChatOptions sunk) {
        long startNanos = System.nanoTime();
        ChatRequest attemptRequest = attemptRequest(request, sunk);
        CompletionStage<ChatResponse> stage;
        try {
            stage = delegate.callAsync(attemptRequest, cancelToken);
        } catch (Throwable t) {
            // 委托同步抛异常（checkRateLimit 等）：release 配对 + 按传输异常级决策。
            registry.release(candidate.getProvider(), candidate.getAccountKey());
            if (metrics != null) {
                metrics.onConcurrencyRelease(candidate.getProvider(), candidate.getAccountKey());
            }
            CompletableFuture<ChatResponse> future = new CompletableFuture<>();
            decideNonStreamFailure(future, request, router, cancelToken, attempt, candidate, sunk, null, t, startNanos);
            return future;
        }
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        stage.whenComplete((resp, err) -> {
            // 全终止路径 release（M-2）：成功、失败切换、NON_TRANSIENT、async 异常。
            registry.release(candidate.getProvider(), candidate.getAccountKey());
            if (metrics != null) {
                metrics.onConcurrencyRelease(candidate.getProvider(), candidate.getAccountKey());
            }
            if (err != null) {
                decideNonStreamFailure(future, request, router, cancelToken, attempt, candidate, sunk, null, err,
                        startNanos);
            } else if (resp != null && resp.isSuccess()) {
                // 成功路径：recordSuccess（D6；HALF_OPEN 探活成功 → CLOSED 恢复）。
                CircuitObservation.recordSuccess(metrics, breaker, candidate.getProvider(), candidate.getModel(),
                        candidate.getModelKey());
                if (metrics != null) {
                    metrics.onRequestSuccess(candidate.getProvider(), candidate.getModel(), candidate.getAccountKey(),
                            (System.nanoTime() - startNanos) / 1_000_000L);
                }
                future.complete(resp);
            } else if (resp != null) {
                // 响应级错误（errorClassification 优先；无分类时按 httpStatus 启发式，防御分支）。
                decideNonStreamFailure(future, request, router, cancelToken, attempt, candidate, sunk, resp, null,
                        startNanos);
            } else {
                decideNonStreamFailure(future, request, router, cancelToken, attempt, candidate, sunk, null,
                        new NopException(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG)
                                .param(NopAiCoreErrors.ARG_MSG, "delegate callAsync returned null response"),
                        startNanos);
            }
        });
        return future;
    }

    /**
     * 非流式失败决策（动作表，Phase 1 D5）。terminal 时完成 future；retry 时递归发起新 attempt。
     */
    private void decideNonStreamFailure(CompletableFuture<ChatResponse> future, ChatRequest request,
                                        ModelClassRouter router, ICancelToken cancelToken, int attempt,
                                        ModelClassCandidate candidate, ChatOptions sunk,
                                        ChatResponse response, Throwable error, long startNanos) {
        if (metrics != null) {
            // 每次 attempt 失败均计数（切换类 / NON_TRANSIENT / CACHE_STATE_LOST / 预算耗尽），
            // 取消路径不经过本方法。
            metrics.onRequestFailure(candidate.getProvider(), candidate.getModel(), candidate.getAccountKey(),
                    (System.nanoTime() - startNanos) / 1_000_000L);
        }
        ErrorClassification cls = error != null
                ? LlmErrorClassifier.classify(error)
                : classifyResponse(response);
        if (cls == ErrorClassification.CACHE_STATE_LOST) {
            // 原地重试语义：不触发账号切换，重发同一候选一次，预算同样计数（防御分支）。
            if (attempt + 1 <= retryBudget) {
                // 原地重发必须重新 acquire（callAsyncWithOptions 的 whenComplete 恒 release
                // 配对——W8 OBS-02 实测：缺失 acquire 导致 release 下溢异常被 whenComplete 吞掉、
                // future 永不完成）。并发复查（M-6a）语义与 invokeNonStreamingAttempt 一致。
                int count = registry.acquire(candidate.getProvider(), candidate.getAccountKey());
                if (metrics != null) {
                    metrics.onConcurrencyAcquire(candidate.getProvider(), candidate.getAccountKey());
                }
                if (isOverConcurrencyLimit(candidate, count)) {
                    registry.release(candidate.getProvider(), candidate.getAccountKey());
                    if (metrics != null) {
                        metrics.onConcurrencyRelease(candidate.getProvider(), candidate.getAccountKey());
                    }
                    completeNonStreamFailure(future, response, error);
                    return;
                }
                callAsyncWithOptions(request, router, cancelToken, attempt + 1, candidate, sunk)
                        .whenComplete((r, e) -> completeRelay(future, r, e));
            } else {
                completeNonStreamFailure(future, response, error);
            }
            return;
        }
        if (!isSwitchable(cls)) {
            // NON_TRANSIENT：不切换直接失败（不消耗预算）。
            completeNonStreamFailure(future, response, error);
            return;
        }
        // 切换类：熔断记账（编排层对已尝试候选逐个 recordFailure——D6）+ 预算计数。
        CircuitObservation.recordFailure(metrics, breaker, candidate.getProvider(), candidate.getModel(),
                candidate.getModelKey());
        if (attempt + 1 > retryBudget) {
            // 预算耗尽 → fail-loud（返回末次错误响应 / 传播末次异常）。
            completeNonStreamFailure(future, response, error);
            return;
        }
        callAsyncAttempt(request, router, cancelToken, attempt + 1)
                .whenComplete((r, e) -> completeRelay(future, r, e));
    }

    private static void completeRelay(CompletableFuture<ChatResponse> future,
                                      ChatResponse response, Throwable error) {
        if (error != null) {
            future.completeExceptionally(error);
        } else {
            future.complete(response);
        }
    }

    private static void completeNonStreamFailure(CompletableFuture<ChatResponse> future,
                                                 ChatResponse response, Throwable error) {
        if (error != null) {
            future.completeExceptionally(error);
        } else if (response != null) {
            future.complete(response);
        } else {
            future.completeExceptionally(new NopException(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG)
                    .param(NopAiCoreErrors.ARG_MSG, "unclassified failure without response or error"));
        }
    }

    // ======================= 流式（LOCAL-03） =======================

    @Override
    public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
        ModelClassRouter router = routerFor(request);
        if (!router.hasRoutingGroup()) {
            // 无路由组：直通零回归。
            return delegate.callStream(request, cancelToken);
        }
        return new FailoverStreamFlow(this, request, router, cancelToken);
    }

    // ======================= 共享辅助 =======================

    private ModelClassRouter routerFor(ChatRequest request) {
        ChatOptions options = request != null ? request.getOptions() : null;
        return ModelClassRouter.forRequest(request, options, strategy, breaker, registry);
    }

    /**
     * 构建 per-attempt 请求：新 {@link ChatRequest}（下沉后的 options + 原 messages），
     * 保留 requestId（同源重试 requestId 复用语义，W1 spike 输入）。
     */
    private static ChatRequest attemptRequest(ChatRequest original, ChatOptions sunk) {
        ChatRequest req = new ChatRequest(original.getMessages(), sunk);
        if (original.getRequestId() != null) {
            req.setRequestId(original.getRequestId());
        }
        return req;
    }

    boolean isOverConcurrencyLimit(ModelClassCandidate candidate, int count) {
        Integer limit = candidate.getConcurrencyLimit();
        return limit != null && limit > 0 && count > limit;
    }

    private boolean isConcurrencySaturated(ModelClassCandidate candidate) {
        return isOverConcurrencyLimit(candidate,
                registry.currentCount(candidate.getProvider(), candidate.getAccountKey()));
    }

    /**
     * 选择 + 熔断探活恢复（Phase 1 D5b/D5c/D5d）：router 全池饱和（ERR_AI_MODEL_CLASS_SATURATED）
     * 时对健康视图 OPEN 的候选显式 {@code allowCall} 探活（冷却期满 → HALF_OPEN 放行，探活成功经
     * recordSuccess → CLOSED 恢复），随后重试选择；探活未放行任何候选 → 原样抛饱和。
     */
    ModelClassCandidate selectNextWithProbe(ModelClassRouter router, boolean active, ChatRequest request) {
        try {
            return active ? router.selectNext() : router.selectNextAfterFailure();
        } catch (NopException e) {
            if (!NopAiCoreErrors.ERR_AI_MODEL_CLASS_SATURATED.getErrorCode().equals(e.getErrorCode())) {
                throw e;
            }
            if (probeBrokenCandidates(router, request)) {
                return active ? router.selectNext() : router.selectNextAfterFailure();
            }
            // 全池饱和 fail-loud（§3.3 饱和指标；探活未放行任何候选）。
            if (metrics != null) {
                metrics.onSaturation(request != null ? LlmConfigHelper.getProvider(request.getOptions()) : null,
                        router.getModelClassId());
            }
            throw e;
        }
    }

    /**
     * 探活遍历（D5b/D5c/D5d）：候选池 = 类内候选 + provider 链扩展候选（primary = 请求目标
     * provider）；只探未尝试、非并发饱和、熔断 OPEN 的候选（并发复查先于探活——Minor-1，
     * 防探活槽位被跳过烧掉导致 HALF_OPEN+probeInFlight 状态悬置）。
     *
     * @return true 当至少一个候选被放行探活（HALF_OPEN）
     */
    private boolean probeBrokenCandidates(ModelClassRouter router, ChatRequest request) {
        boolean probed = false;
        List<ModelClassCandidate> pool = new ArrayList<>(router.getInClassCandidates());
        pool.addAll(LlmConfigHelper.resolveProviderChainCandidates(
                LlmConfigHelper.getProvider(request.getOptions())));
        for (ModelClassCandidate candidate : pool) {
            if (router.getAttempted().contains(candidate)) {
                continue;
            }
            if (breaker.getState(candidate.getModelKey()) != CircuitState.OPEN) {
                continue;
            }
            if (isConcurrencySaturated(candidate)) {
                continue;
            }
            if (CircuitObservation.allowCall(metrics, breaker, candidate.getProvider(), candidate.getModel(),
                    candidate.getModelKey())) {
                probed = true;
            }
        }
        return probed;
    }

    /**
     * 动作表分类判断（Phase 1 D5）：QUOTA_EXCEEDED/AUTH_INVALID/RATE_LIMITED/TRANSIENT → 切换。
     */
    static boolean isSwitchable(ErrorClassification cls) {
        return cls == ErrorClassification.QUOTA_EXCEEDED
                || cls == ErrorClassification.AUTH_INVALID
                || cls == ErrorClassification.RATE_LIMITED
                || cls == ErrorClassification.TRANSIENT;
    }

    /**
     * 响应级分类（防御分支）：{@code errorClassification} 非 null 优先；无分类的错误响应按
     * {@code httpStatus} 启发式（与 {@link LlmErrorClassifier} 一致）；均无 → NON_TRANSIENT。
     */
    private static ErrorClassification classifyResponse(ChatResponse response) {
        if (response == null) {
            return ErrorClassification.NON_TRANSIENT;
        }
        if (response.getErrorClassification() != null) {
            return response.getErrorClassification();
        }
        Integer status = response.getHttpStatus();
        if (status != null) {
            if (status == 429) {
                return ErrorClassification.RATE_LIMITED;
            }
            if (status >= 500 && status < 600) {
                return ErrorClassification.TRANSIENT;
            }
            if (status >= 400 && status < 500) {
                return ErrorClassification.NON_TRANSIENT;
            }
        }
        return ErrorClassification.NON_TRANSIENT;
    }

    /**
     * 流式路径错误分类（Phase 1 D5a/M-3/Minor-8）：unwrap cause 链找 {@code NopException} +
     * {@code ARG_HTTP_STATUS} → 经 {@code dialect.parseErrorResponse} 恢复响应级分类
     * （AUTH_INVALID/QUOTA_EXCEEDED 可达）；parseErrorResponse 自身抛异常（body 畸形）→ 回退
     * {@link LlmErrorClassifier}；无 HTTP 参数 → {@link LlmErrorClassifier}。
     */
    static ErrorClassification classifyStreamError(Throwable throwable, ModelClassCandidate candidate) {
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof NopException) {
                NopException ex = (NopException) cause;
                Object statusObj = ex.getParam(NopAiCoreErrors.ARG_HTTP_STATUS);
                Integer httpStatus = statusObj instanceof Number ? ((Number) statusObj).intValue() : null;
                if (httpStatus != null) {
                    Object bodyObj = ex.getParam(HttpApiErrors.ARG_BODY);
                    String body = bodyObj != null ? bodyObj.toString() : "";
                    Object headersObj = ex.getParam(HttpApiErrors.ARG_RESPONSE_HEADERS);
                    @SuppressWarnings("unchecked")
                    Map<String, String> headers = headersObj instanceof Map
                            ? (Map<String, String>) headersObj : null;
                    try {
                        io.nop.ai.core.model.LlmModel config =
                                LlmConfigHelper.loadConfig(candidate.getProvider());
                        io.nop.ai.core.dialect.ILlmDialect dialect =
                                io.nop.ai.core.dialect.LlmDialectFactory.getDialect(config.getApiStyle());
                        ChatResponse err = dialect.parseErrorResponse(body, httpStatus, headers, config);
                        if (err != null && err.getErrorClassification() != null) {
                            return err.getErrorClassification();
                        }
                    } catch (Exception ignored) {
                        // Minor-8: parseErrorResponse 抛异常（body 畸形）→ 回退 LlmErrorClassifier。
                    }
                }
            }
            cause = cause.getCause();
        }
        return LlmErrorClassifier.classify(throwable);
    }
}
