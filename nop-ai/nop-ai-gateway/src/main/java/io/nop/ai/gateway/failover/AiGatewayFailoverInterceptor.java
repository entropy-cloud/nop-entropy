package io.nop.ai.gateway.failover;

import io.nop.ai.api.chat.ErrorClassification;
import io.nop.ai.core.NopAiCoreErrors;
import io.nop.ai.core.NopAiCoreException;
import io.nop.ai.core.dialect.ILlmDialect;
import io.nop.ai.core.dialect.LlmDialectFactory;
import io.nop.ai.core.model.ApiStyle;
import io.nop.ai.core.model.LlmModel;
import io.nop.ai.core.reliability.ThresholdBreaker;
import io.nop.ai.core.routing.ConcurrencyRegistry;
import io.nop.ai.core.routing.ISelectionStrategy;
import io.nop.ai.core.routing.ModelClassCandidate;
import io.nop.ai.core.routing.ModelClassRouter;
import io.nop.ai.core.service.LlmConfigHelper;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.util.StringHelper;
import io.nop.gateway.conversion.IBackendMessageConverter;
import io.nop.gateway.core.context.IGatewayContext;
import io.nop.gateway.core.interceptor.IGatewayInterceptor;
import io.nop.gateway.core.interceptor.IGatewayInvocation;
import io.nop.gateway.core.streaming.GatewayStreamingConstants;
import io.nop.gateway.model.GatewayRouteModel;
import io.nop.gateway.model.GatewayStreamingModel;
import io.nop.http.api.client.HttpRequest;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static io.nop.ai.gateway.failover.FailoverConstants.ATTR_ROUTER;
import static io.nop.ai.gateway.failover.FailoverConstants.PROP_ACCOUNT_KEY;
import static io.nop.ai.gateway.failover.FailoverConstants.PROP_ACTIVE;
import static io.nop.ai.gateway.failover.FailoverConstants.PROP_API_STYLE;
import static io.nop.ai.gateway.failover.FailoverConstants.PROP_ATTEMPT;
import static io.nop.ai.gateway.failover.FailoverConstants.PROP_MODEL;
import static io.nop.ai.gateway.failover.FailoverConstants.PROP_PROVIDER;
import static io.nop.ai.gateway.failover.FailoverConstants.PROP_STREAM;

/**
 * 网关形态透明账号 failover 拦截器（W7，plan 2026-08-15-1116-3，GW-01）。
 *
 * <p>实现 {@link IGatewayInterceptor}，编排语义与 W6 本地适配器一致（选择/切换/重试/记账/预算/
 * fail-loud，复用 W5 游走原语 + W2 可靠性机制），消费 per-request properties 通道
 * （Phase 1 GW-A4/B-9，@JsonIgnore 不序列化/不可注入/不转发）。
 *
 * <p><b>接线分工（B-8，无双重转换）</b>：
 * <ul>
 *   <li><b>流式路径</b>：{@link #onRequest} 做首次候选选择 + converter 请求体转换（stream=true）
 *       + properties 下沉 + 重执行回调/生命周期监听器经 context attribute 注入（B-12）；
 *       判定条件与 {@code RouteExecutor:76} 同构（F2：{@code getStreaming() != null &&
 *       isStreamingEnabled}）。</li>
 *   <li><b>非流式路径</b>：{@link #invoke} 内包装选择/切换/重试（每次 attempt 下沉 properties，
 *       供 converter 动态 dialect 与 route URL 表达式 baseUrl 覆盖消费——F3/F5）；
 *       <b>不</b>在 invoke 内调 converter（保留 {@code executeRouteLogic} 既有调用，防双转）。</li>
 * </ul>
 *
 * <p><b>{@code onError} 契约（B-17/B-18）</b>：流式路径只做重试性分类——可重试（
 * QUOTA/AUTH/RATE_LIMITED/TRANSIENT）与 CACHE_STATE_LOST（回调原地重发）一律 rethrow
 * （纯观测，重试决策唯一归属缓冲层/重执行回调）；仅 NON_TRANSIENT 族返回降级终止响应；
 * 窗口内外判定唯一归属缓冲层。生效前提：链上无先返回非空响应的吞错者（B-18，测试路由遵守）。
 *
 * <p><b>并发计数</b>：流式经生命周期监听器（fetch 发起 +1/终止 -1，缓冲关闭仍计数）；
 * 非流式在 invoke 内 acquire 后复查（W6 D7/M-6a）；double-booking 竞态 = 预检语义（B-13）。
 *
 * <p><b>无静默跳过</b>：预算耗尽/全池饱和/不可切换分类显式失败（错误响应或抛错）；
 * 无路由组直通零回归（不转换、不计数、不挂载）。
 */
public class AiGatewayFailoverInterceptor implements IGatewayInterceptor {

    private ISelectionStrategy strategy;
    private ThresholdBreaker breaker;
    private ConcurrencyRegistry registry;
    private IBackendMessageConverter converter;
    private IFailoverMetrics metrics;
    private int retryBudget = ChatServiceFailoverAdapter.DEFAULT_RETRY_BUDGET;
    private String primaryProvider;

    /**
     * 可选：选择策略（null = 默认策略 {@code DefaultSelectionStrategy}，经 ModelClassRouter）。
     */
    public void setStrategy(ISelectionStrategy strategy) {
        this.strategy = strategy;
    }

    /**
     * 进程共享熔断器（Phase 1 D8/M-5 同 W6：必须注入单例，不得按 router 缺省构造）。
     */
    public void setBreaker(ThresholdBreaker breaker) {
        this.breaker = breaker;
    }

    /**
     * 进程共享并发注册表（Phase 1 D8/M-5 同 W6）。
     */
    public void setRegistry(ConcurrencyRegistry registry) {
        this.registry = registry;
    }

    /**
     * 前后端格式转换器（bean {@code nopBackendMessageConverter_AI_DIALECT} 同源——流式路径
     * 转换 + onStreamElement 反向转换；非流式路径由路由配置的 converter 经 executeRouteLogic
     * 调用，两处须为同一 bean，Phase 4 落档）。
     */
    public void setConverter(IBackendMessageConverter converter) {
        this.converter = converter;
    }

    /**
     * 可观测性指标服务（W8 OBS-01；null = 观测 no-op——Phase 1 null-object/fail-fast
     * 裁定：标准部署经 beans.xml 非 optional ref fail-fast，null 仅测试/手工构造形态）。
     */
    public void setMetrics(IFailoverMetrics metrics) {
        this.metrics = metrics;
    }

    /**
     * 重试预算（初始 attempt 之后最多重试次数；W6 D3 同值默认 2，@cfg 可配置）。
     */
    public void setRetryBudget(int retryBudget) {
        if (retryBudget < 0) {
            throw new NopAiCoreException(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG)
                    .param(NopAiCoreErrors.ARG_MSG,
                            "AiGatewayFailoverInterceptor retryBudget must be >= 0: " + retryBudget);
        }
        this.retryBudget = retryBudget;
    }

    /**
     * 主 provider（provider 链扩展 primary 键；缺省 = {@code nop.ai.service.default-llm}）。
     */
    public void setPrimaryProvider(String primaryProvider) {
        this.primaryProvider = primaryProvider;
    }

    public ISelectionStrategy getStrategy() {
        return strategy;
    }

    public ThresholdBreaker getBreaker() {
        return breaker;
    }

    public ConcurrencyRegistry getRegistry() {
        return registry;
    }

    public IBackendMessageConverter getConverter() {
        return converter;
    }

    public IFailoverMetrics getMetrics() {
        return metrics;
    }

    public int getRetryBudget() {
        return retryBudget;
    }

    public String getPrimaryProvider() {
        return primaryProvider;
    }

    // ======================= onRequest（流式路径，B-8/F2） =======================

    @Override
    public ApiRequest<?> onRequest(ApiRequest<?> request, IGatewayContext svcCtx) {
        GatewayRouteModel route = svcCtx.getCurrentRoute();
        if (route == null || route.getStreaming() == null || !isStreamingEnabled(route, svcCtx)) {
            // 非流式路径：不转换（B-8——executeRouteLogic 既有 converter 调用负责；invoke 内
            // 每次 attempt 下沉 properties 供其消费）。判定与 RouteExecutor:76 同构（F2）。
            return request;
        }
        ModelClassRouter router = routerFor(request);
        if (!router.hasRoutingGroup()) {
            // 无路由组：直通零回归（不转换、不计数、不挂载）。
            return request;
        }
        ModelClassCandidate candidate = FailoverProbeSupport.selectNextWithProbe(
                breaker, registry, router, true, resolvePrimaryProvider(), metrics);
        sinkCandidate(request, candidate, 1);
        if (metrics != null) {
            // 接管计数（流式首次候选下沉）。
            metrics.onTakeover(candidate.getProvider(), candidate.getModel(), candidate.getAccountKey());
        }
        request.setProperty(PROP_STREAM, true);
        // 请求体转换（仅流式路径——RouteExecutor:74 返回的 request 传入 executeStreaming）
        ApiRequest<?> converted = requireConverter().toBackendRequest(request);
        asObjectRequest(request).setData(converted.getData());
        // 注入重执行回调 + 生命周期监听器（B-12 context attribute 通道；StreamingProcessor
        // 缓冲层从 context 读取，缺省 null = 不重订阅/不计数，零回归）。
        svcCtx.setAttribute(GatewayStreamingConstants.ATTR_RETRY_CALLBACK,
                new GatewayStreamingRetryCallback(this));
        svcCtx.setAttribute(GatewayStreamingConstants.ATTR_LIFECYCLE_LISTENER,
                new GatewayStreamingLifecycleListener(registry, request, metrics));
        svcCtx.setAttribute(ATTR_ROUTER, router);
        // F1：原地修改并返回同一实例（流式 failover 路由不配置 requestMapping/onRequest xpl）。
        return request;
    }

    // ======================= invoke（非流式路径，选择/切换/重试编排） =======================

    @Override
    public CompletionStage<ApiResponse<?>> invoke(IGatewayInvocation invocation, ApiRequest<?> request,
                                                  IGatewayContext svcCtx) {
        GatewayRouteModel route = svcCtx.getCurrentRoute();
        if (route != null && route.getStreaming() != null && isStreamingEnabled(route, svcCtx)) {
            // 流式路径不走 invoke（RouteExecutor:76-77 直接 executeStreaming）；防御性直通。
            return invocation.proceedInvoke(request, svcCtx);
        }
        ModelClassRouter router = routerFor(request);
        if (!router.hasRoutingGroup()) {
            // 无路由组：直通零回归（不选择、不计数、不重试）。
            return invocation.proceedInvoke(request, svcCtx);
        }
        request.setProperty(PROP_ACTIVE, true);
        return invokeNonStreamingAttempt(invocation, request, svcCtx, router, 0, null);
    }

    private CompletionStage<ApiResponse<?>> invokeNonStreamingAttempt(IGatewayInvocation invocation,
                                                                      ApiRequest<?> request, IGatewayContext svcCtx,
                                                                      ModelClassRouter router, int attempt,
                                                                      ModelClassCandidate fixedCandidate) {
        ModelClassCandidate candidate;
        if (fixedCandidate != null) {
            candidate = fixedCandidate;
        } else {
            try {
                candidate = FailoverProbeSupport.selectNextWithProbe(
                        breaker, registry, router, attempt == 0, resolvePrimaryProvider(), metrics);
            } catch (NopException e) {
                // 全池饱和 fail-loud（主动路径不记熔断、不耗预算——W6 D5b/D6）。
                return FutureHelper.reject(e);
            }
        }
        // 并发计数 acquire（发起时）+ 复查（M-6a）：超限 → release + 视为饱和跳过 → 重选。
        int count = registry.acquire(candidate.getProvider(), candidate.getAccountKey());
        if (metrics != null) {
            metrics.onConcurrencyAcquire(candidate.getProvider(), candidate.getAccountKey());
        }
        if (FailoverProbeSupport.isConcurrencySaturated(registry, candidate)) {
            registry.release(candidate.getProvider(), candidate.getAccountKey());
            if (metrics != null) {
                metrics.onConcurrencyRelease(candidate.getProvider(), candidate.getAccountKey());
            }
            return invokeNonStreamingAttempt(invocation, request, svcCtx, router, attempt, null);
        }
        if (attempt > 0 && fixedCandidate == null && metrics != null) {
            // attempt > 0 且非 CACHE_STATE_LOST 原地重发（fixedCandidate 非 null）→ 已切换至新候选。
            metrics.onSwitchAttempt(candidate.getProvider(), candidate.getModel(), candidate.getAccountKey());
        }
        sinkCandidate(request, candidate, attempt + 1);
        long startNanos = System.nanoTime();
        CompletionStage<ApiResponse<?>> stage;
        try {
            stage = invocation.proceedInvoke(request, svcCtx);
        } catch (Throwable t) {
            registry.release(candidate.getProvider(), candidate.getAccountKey());
            if (metrics != null) {
                metrics.onConcurrencyRelease(candidate.getProvider(), candidate.getAccountKey());
            }
            CompletableFuture<ApiResponse<?>> future = new CompletableFuture<>();
            decideNonStreamFailure(future, invocation, request, svcCtx, router, attempt, candidate, null, t,
                    startNanos);
            return future;
        }
        CompletableFuture<ApiResponse<?>> future = new CompletableFuture<>();
        stage.whenComplete((resp, err) -> {
            // 全终止路径 release（M-2）：成功、失败切换、NON_TRANSIENT、async 异常。
            registry.release(candidate.getProvider(), candidate.getAccountKey());
            if (metrics != null) {
                metrics.onConcurrencyRelease(candidate.getProvider(), candidate.getAccountKey());
            }
            if (err != null) {
                decideNonStreamFailure(future, invocation, request, svcCtx, router, attempt, candidate, null, err,
                        startNanos);
            } else if (resp != null && resp.isOk()) {
                // 成功路径：recordSuccess（D6；HALF_OPEN 探活成功 → CLOSED 恢复）。
                CircuitObservation.recordSuccess(metrics, breaker, candidate.getProvider(), candidate.getModel(),
                        candidate.getModelKey());
                if (metrics != null) {
                    metrics.onRequestSuccess(candidate.getProvider(), candidate.getModel(), candidate.getAccountKey(),
                            (System.nanoTime() - startNanos) / 1_000_000L);
                }
                future.complete(resp);
            } else if (resp != null) {
                decideNonStreamFailure(future, invocation, request, svcCtx, router, attempt, candidate, resp, null,
                        startNanos);
            } else {
                future.completeExceptionally(new NopAiCoreException(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG)
                        .param(NopAiCoreErrors.ARG_MSG,
                                "proceedInvoke returned null response for route: " + routeId(svcCtx)));
            }
        });
        return future;
    }

    /**
     * 非流式失败决策（W6 动作表同款）：NON_TRANSIENT → 不切换直接失败（不耗预算）；
     * CACHE_STATE_LOST → 原地重发同一候选一次（预算计数）；切换类 → 熔断记账 + 预算检查 +
     * 重选（被动路径，含 provider 链扩展）。
     */
    private void decideNonStreamFailure(CompletableFuture<ApiResponse<?>> future, IGatewayInvocation invocation,
                                        ApiRequest<?> request, IGatewayContext svcCtx, ModelClassRouter router,
                                        int attempt, ModelClassCandidate candidate,
                                        ApiResponse<?> response, Throwable error, long startNanos) {
        if (metrics != null) {
            // 每次 attempt 失败均计数（切换类 / NON_TRANSIENT / CACHE_STATE_LOST / 预算耗尽）。
            metrics.onRequestFailure(candidate.getProvider(), candidate.getModel(), candidate.getAccountKey(),
                    (System.nanoTime() - startNanos) / 1_000_000L);
        }
        ErrorClassification cls = error != null
                ? ChatServiceFailoverAdapter.classifyStreamError(error, candidate)
                : classifyResponseStatus(response);
        if (cls == ErrorClassification.CACHE_STATE_LOST) {
            if (attempt + 1 <= retryBudget) {
                invokeNonStreamingAttempt(invocation, request, svcCtx, router, attempt + 1, candidate)
                        .whenComplete((r, e) -> completeRelay(future, r, e));
            } else {
                completeNonStreamFailure(future, response, error);
            }
            return;
        }
        if (!ChatServiceFailoverAdapter.isSwitchable(cls)) {
            completeNonStreamFailure(future, response, error);
            return;
        }
        CircuitObservation.recordFailure(metrics, breaker, candidate.getProvider(), candidate.getModel(),
                candidate.getModelKey());
        if (attempt + 1 > retryBudget) {
            completeNonStreamFailure(future, response, error);
            return;
        }
        invokeNonStreamingAttempt(invocation, request, svcCtx, router, attempt + 1, null)
                .whenComplete((r, e) -> completeRelay(future, r, e));
    }

    /**
     * 非流式响应路径分类（网关形态启发式，Phase 4 落档）：InvokeProcessor 对非 2xx 仅对
     * 429/5xx 抛异常（异常路径经 classifyStreamError 恢复响应级分类）；其余状态码（401/403/400
     * 等）作为响应返回且原始 body 已被解析丢失——按 httpStatus 启发式：429 → RATE_LIMITED、
     * 401/403 → AUTH_INVALID（需求 §3.1 动作表）、5xx → TRANSIENT、其他 4xx → NON_TRANSIENT。
     */
    private static ErrorClassification classifyResponseStatus(ApiResponse<?> response) {
        if (response == null) {
            return ErrorClassification.NON_TRANSIENT;
        }
        int status = response.getHttpStatus();
        if (status == 429) {
            return ErrorClassification.RATE_LIMITED;
        }
        if (status == 401 || status == 403) {
            return ErrorClassification.AUTH_INVALID;
        }
        if (status >= 500 && status < 600) {
            return ErrorClassification.TRANSIENT;
        }
        if (status >= 400 && status < 500) {
            return ErrorClassification.NON_TRANSIENT;
        }
        return ErrorClassification.NON_TRANSIENT;
    }

    private static void completeRelay(CompletableFuture<ApiResponse<?>> future,
                                      ApiResponse<?> response, Throwable error) {
        if (error != null) {
            future.completeExceptionally(error);
        } else {
            future.complete(response);
        }
    }

    private static void completeNonStreamFailure(CompletableFuture<ApiResponse<?>> future,
                                                 ApiResponse<?> response, Throwable error) {
        if (error != null) {
            future.completeExceptionally(error);
        } else if (response != null) {
            future.complete(response);
        } else {
            future.completeExceptionally(new NopAiCoreException(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG)
                    .param(NopAiCoreErrors.ARG_MSG, "unclassified non-stream failure without response or error"));
        }
    }

    // ======================= onError / onStreamElement（B-17） =======================

    @Override
    public ApiResponse<?> onError(Throwable exception, IGatewayContext svcCtx) {
        if (!isActive(svcCtx)) {
            // 未接管（无路由组直通/onRequest 期失败）：纯观测 rethrow（零回归）。
            throw NopException.adapt(exception);
        }
        if (!svcCtx.isStreamingMode()) {
            // 非流式：invoke 已做终止/重试决策；纯观测 rethrow（错误经 RouteExecutor
            // exceptionally → ErrorMessageManager 通道，与 W6 一致）。
            throw NopException.adapt(exception);
        }
        // 流式路径（B-17 契约）：可重试（QUOTA/AUTH/RATE_LIMITED/TRANSIENT）与 CACHE_STATE_LOST
        // （回调原地重发）一律 rethrow（重试决策唯一归属缓冲层/重执行回调）；
        // 仅 NON_TRANSIENT 族返回降级终止响应。
        ModelClassCandidate candidate = GatewayStreamingRetryCallback.currentCandidate(requestOf(svcCtx));
        ErrorClassification cls = ChatServiceFailoverAdapter.classifyStreamError(exception, candidate);
        if (ChatServiceFailoverAdapter.isSwitchable(cls) || cls == ErrorClassification.CACHE_STATE_LOST) {
            throw NopException.adapt(exception);
        }
        if (metrics != null) {
            // 降级终止计数（流式 NON_TRANSIENT 族 → 降级响应，不重试不切换）。
            metrics.onDegradedTermination(candidate.getProvider(), candidate.getModel(), candidate.getAccountKey());
        }
        return degradedErrorResponse(exception);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object onStreamElement(Object element, IGatewayContext svcCtx) {
        ApiRequest<?> request = svcCtx.getRequest();
        if (request == null || !isActive(request)) {
            // 未接管：原样透传（零回归）。
            return element;
        }
        if (!(element instanceof Map)) {
            return element;
        }
        // per-attempt 反向转换（B-10）：每次从 svcCtx.getRequest() 读当前 properties——
        // attempt 2 用 attempt 2 的 backend dialect（重执行回调已同步更新）。
        if (metrics != null) {
            metrics.onStreamElementConverted(request.getStringProperty(PROP_PROVIDER),
                    request.getStringProperty(PROP_MODEL), request.getStringProperty(PROP_ACCOUNT_KEY));
        }
        return requireConverter().toFrontendStreamChunk((Map<String, Object>) element, request);
    }

    /**
     * 流式成功路径熔断恢复（W8 OBS-02 补缺——probe 恢复契约 §3.3 在网关流式路径的缺口）：
     * 缓冲层正常终止（onComplete）时对当前 attempt 候选 {@code recordSuccess}——HALF_OPEN
     * 探活放行的流成功 → CLOSED 恢复（与本地形态 {@code FailoverStreamFlow.handleStreamComplete}
     * 对齐）。仅接管请求生效（未接管零回归）；不记录 request-success 指标——OBS-01 契约表
     * 明确网关流式成功路径不在指标触发事件内（与既有指标测试断言一致）。
     */
    @Override
    public void onStreamComplete(IGatewayContext svcCtx) {
        ApiRequest<?> request = svcCtx != null ? svcCtx.getRequest() : null;
        if (request == null || !isActive(request)) {
            return;
        }
        ModelClassCandidate candidate = GatewayStreamingRetryCallback.currentCandidate(request);
        CircuitObservation.recordSuccess(metrics, breaker, candidate.getProvider(), candidate.getModel(),
                candidate.getModelKey());
    }

    // ======================= 共享辅助 =======================

    /**
     * 目标候选信息下沉（properties 通道，B-9）：provider/model/accountKey/apiStyle/attempt +
     * baseUrl 覆盖（nop-gateway 键，首次尝试 base 替换消费——B-14）+ 接管标记。
     */
    void sinkCandidate(ApiRequest<?> request, ModelClassCandidate candidate, int attempt) {
        request.setProperty(PROP_PROVIDER, candidate.getProvider());
        request.setProperty(PROP_MODEL, candidate.getModel());
        request.setProperty(PROP_ACCOUNT_KEY, candidate.getAccountKey());
        request.setProperty(PROP_ATTEMPT, attempt);
        request.setProperty(PROP_ACTIVE, true);
        if (candidate.getAccountBaseUrl() != null) {
            request.setProperty(GatewayStreamingConstants.PROP_BASE_URL, candidate.getAccountBaseUrl());
        } else {
            request.removeProperty(GatewayStreamingConstants.PROP_BASE_URL);
        }
        LlmModel config = LlmConfigHelper.loadConfig(candidate.getProvider());
        ApiStyle apiStyle = config != null ? config.getApiStyle() : null;
        if (apiStyle != null) {
            request.setProperty(PROP_API_STYLE, apiStyle.name());
        } else {
            request.removeProperty(PROP_API_STYLE);
        }
        sinkAuthHeader(request, candidate);
    }

    /**
     * 目标账号认证头下沉（Phase 4 落档）：备用账号 apiKey 直沉 accountKey（W6 语义），
     * 主账号经 {@code resolveApiKey}；经 {@code dialect.setHeaders} 写入 request headers
     * （InvokeProcessor/buildStreamingHttpRequest 复制转发）。无可用 key = 保持客户端头
     * （零回归——客户端自持认证基线）。
     */
    private void sinkAuthHeader(ApiRequest<?> request, ModelClassCandidate candidate) {
        LlmModel config = LlmConfigHelper.loadConfig(candidate.getProvider());
        String apiKey = candidate.getAccountKey();
        if (StringHelper.isEmpty(apiKey)) {
            apiKey = LlmConfigHelper.resolveApiKey(candidate.getProvider());
        }
        if (StringHelper.isEmpty(apiKey)) {
            return;
        }
        ILlmDialect dialect = LlmDialectFactory.getDialect(config.getApiStyle());
        HttpRequest tmp = new HttpRequest();
        dialect.setHeaders(tmp, apiKey, config.getApiKeyHeader());
        if (tmp.getHeaders() != null) {
            for (Map.Entry<String, Object> entry : tmp.getHeaders().entrySet()) {
                request.setHeader(entry.getKey(), entry.getValue());
            }
        }
    }

    ModelClassRouter routerFor(ApiRequest<?> request) {
        if (breaker == null || registry == null) {
            // D8 契约：必须注入进程共享单例（缺省构造会静默退化为 per-call 局部状态）。
            throw new NopAiCoreException(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG)
                    .param(NopAiCoreErrors.ARG_MSG,
                            "AiGatewayFailoverInterceptor requires injected breaker and registry (process-shared singletons)");
        }
        String model = bodyModel(request);
        ChatOptionsBuilder options = new ChatOptionsBuilder();
        options.provider = resolvePrimaryProvider();
        options.model = model;
        return ModelClassRouter.forRequest(null, options.build(), strategy, breaker, registry);
    }

    String resolvePrimaryProvider() {
        if (primaryProvider != null && !primaryProvider.isEmpty()) {
            return primaryProvider;
        }
        String def = io.nop.ai.core.AiCoreConfigs.CFG_AI_SERVICE_DEFAULT_LLM.get();
        return def != null ? def : null;
    }

    private static String bodyModel(ApiRequest<?> request) {
        Object data = request != null ? request.getData() : null;
        if (data instanceof Map) {
            Object model = ((Map<?, ?>) data).get("model");
            return model != null ? model.toString() : null;
        }
        return null;
    }

    private IBackendMessageConverter requireConverter() {
        if (converter == null) {
            throw new NopAiCoreException(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG)
                    .param(NopAiCoreErrors.ARG_MSG,
                            "AiGatewayFailoverInterceptor requires an injected IBackendMessageConverter (nopBackendMessageConverter_AI_DIALECT)");
        }
        return converter;
    }

    static boolean isStreamingEnabled(GatewayRouteModel route, IGatewayContext svcCtx) {
        GatewayStreamingModel streaming = route.getStreaming();
        if (streaming == null) {
            return false;
        }
        if (streaming.getEnabled() != null) {
            Object result = streaming.getEnabled().invoke(svcCtx.getEvalScope());
            return ConvertHelper.toBoolean(result);
        }
        return true;
    }

    private static boolean isActive(IGatewayContext svcCtx) {
        ApiRequest<?> request = svcCtx.getRequest();
        return request != null && isActive(request);
    }

    /**
     * getBooleanProperty 对缺失属性返回 null（ConvertHelper.toBoolean(null) = null）——
     * 统一 Boolean.TRUE.equals 判空（防 NPE）。
     */
    private static boolean isActive(ApiRequest<?> request) {
        return Boolean.TRUE.equals(request.getBooleanProperty(PROP_ACTIVE));
    }

    private static ApiRequest<?> requestOf(IGatewayContext svcCtx) {
        return svcCtx.getRequest();
    }

    @SuppressWarnings("unchecked")
    private static ApiRequest<Object> asObjectRequest(ApiRequest<?> request) {
        return (ApiRequest<Object>) request;
    }

    private static String routeId(IGatewayContext svcCtx) {
        GatewayRouteModel route = svcCtx.getCurrentRoute();
        return route != null ? route.getId() : null;
    }

    private static ApiResponse<?> degradedErrorResponse(Throwable exception) {
        ApiResponse<Object> response = new ApiResponse<>();
        response.setWrapper(true);
        Integer status = extractHttpStatus(exception);
        if (status != null) {
            response.setHttpStatus(status);
        }
        response.setMsg(exception != null && exception.getMessage() != null
                ? exception.getMessage() : "stream failed");
        return response;
    }

    private static Integer extractHttpStatus(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof NopException) {
                Object status = ((NopException) cause).getParam(NopAiCoreErrors.ARG_HTTP_STATUS);
                if (status instanceof Number) {
                    return ((Number) status).intValue();
                }
            }
            cause = cause.getCause();
        }
        return null;
    }

    /**
     * 轻量 ChatOptions 构造（ModelClassRouter 仅消费 provider/model，无需完整 builder 链）。
     */
    private static final class ChatOptionsBuilder {
        String provider;
        String model;

        io.nop.ai.api.chat.ChatOptions build() {
            io.nop.ai.api.chat.ChatOptions options = new io.nop.ai.api.chat.ChatOptions();
            options.setProvider(provider);
            options.setModel(model);
            return options;
        }
    }
}
