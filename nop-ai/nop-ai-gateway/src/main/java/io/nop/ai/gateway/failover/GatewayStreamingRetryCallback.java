package io.nop.ai.gateway.failover;

import io.nop.ai.api.chat.ErrorClassification;
import io.nop.ai.core.dialect.ILlmDialect;
import io.nop.ai.core.dialect.LlmDialectFactory;
import io.nop.ai.core.model.LlmModel;
import io.nop.ai.core.routing.ModelClassCandidate;
import io.nop.ai.core.routing.ModelClassRouter;
import io.nop.ai.core.service.LlmConfigHelper;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.gateway.core.context.IGatewayContext;
import io.nop.gateway.core.streaming.GatewayStreamingConstants;
import io.nop.gateway.core.streaming.IStreamingRetryCallback;
import io.nop.gateway.model.GatewayRouteModel;
import io.nop.http.api.client.HttpRequest;

import java.util.Map;

import static io.nop.ai.gateway.failover.FailoverConstants.ATTR_ROUTER;
import static io.nop.ai.gateway.failover.FailoverConstants.PROP_ACCOUNT_KEY;
import static io.nop.ai.gateway.failover.FailoverConstants.PROP_ATTEMPT;
import static io.nop.ai.gateway.failover.FailoverConstants.PROP_MODEL;
import static io.nop.ai.gateway.failover.FailoverConstants.PROP_PROVIDER;

/**
 * 网关形态流式重执行回调（W7，plan 2026-08-15-1116-3 Phase 4，M-3/B-12 窄接口）。
 *
 * <p>由 {@link AiGatewayFailoverInterceptor#onRequest} 经 {@code IGatewayContext} attribute
 * 注入 nop-gateway 缓冲层；窗口内失败时被调用：分类（复用 W6 流式路径分类）→ 动作表
 * （NON_TRANSIENT → null 不重试；CACHE_STATE_LOST → 同候选原地重发一次；切换类 → 熔断记账 +
 * 预算检查 + 被动重选（含 provider 链扩展 + 探活恢复））→ 返回新 {@link HttpRequest}
 * （null = 不重试，断流报错）。
 *
 * <p><b>base 替换语义（B-14）</b>：重试 URL = {@code dialect.buildUrl(accountBaseUrl,
 * config.getChatUrl(), apiKey)}（与首次尝试的 base 替换一致）；请求体 = converter
 * {@code toBackendRequest}（读更新后的 properties——新 apiStyle/model/config，B-10
 * per-attempt 状态传播链：sinkCandidate 同步更新 properties 供 onStreamElement 反向转换）。
 */
final class GatewayStreamingRetryCallback implements IStreamingRetryCallback {

    private final AiGatewayFailoverInterceptor interceptor;

    GatewayStreamingRetryCallback(AiGatewayFailoverInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public HttpRequest retry(Throwable error, GatewayRouteModel route, ApiRequest<?> request,
                             IGatewayContext context) {
        ModelClassCandidate failedCandidate = currentCandidate(request);
        ErrorClassification cls = ChatServiceFailoverAdapter.classifyStreamError(error, failedCandidate);
        IFailoverMetrics metrics = interceptor.getMetrics();
        int attempt = request.getIntProperty(PROP_ATTEMPT, 1);
        if (cls == ErrorClassification.CACHE_STATE_LOST) {
            // 原地重试语义：不触发账号切换，重发同一候选一次，预算同样计数（防御分支）。
            if (attempt <= interceptor.getRetryBudget()) {
                request.setProperty(PROP_ATTEMPT, attempt + 1);
                HttpRequest retryRequest = buildHttpRequest(request, failedCandidate);
                if (metrics != null) {
                    metrics.onResubscribe(failedCandidate.getProvider(), failedCandidate.getModel(),
                            failedCandidate.getAccountKey());
                }
                return retryRequest;
            }
            return null;
        }
        if (!ChatServiceFailoverAdapter.isSwitchable(cls)) {
            // NON_TRANSIENT：不重试（断流报错，不耗预算）。
            return null;
        }
        // 切换类：熔断记账（编排层对已尝试候选逐个 recordFailure——D6）+ 预算检查。
        CircuitObservation.recordFailure(metrics, interceptor.getBreaker(),
                failedCandidate.getProvider(), failedCandidate.getModel(), failedCandidate.getModelKey());
        if (attempt > interceptor.getRetryBudget()) {
            // 预算耗尽 → null = 断流报错（fail-loud，不静默）。
            return null;
        }
        ModelClassRouter router = (ModelClassRouter) context.getAttribute(ATTR_ROUTER);
        ModelClassCandidate next;
        try {
            next = FailoverProbeSupport.selectNextWithProbe(interceptor.getBreaker(), interceptor.getRegistry(),
                    router, false, interceptor.resolvePrimaryProvider(), metrics);
        } catch (NopException e) {
            // 全池饱和：不重试（原错误信号断流，fail-loud）。
            return null;
        }
        // B-10 per-attempt 状态传播：sinkCandidate 同步更新 properties（新 apiStyle/model）供
        // onStreamElement 反向转换 + 首次 base 覆盖读取。
        interceptor.sinkCandidate(request, next, attempt + 1);
        if (metrics != null) {
            // 切换计数（新候选）+ 重订阅计数（重执行回调返回非 null = 重订阅发生）。
            metrics.onSwitchAttempt(next.getProvider(), next.getModel(), next.getAccountKey());
            metrics.onResubscribe(next.getProvider(), next.getModel(), next.getAccountKey());
        }
        return buildHttpRequest(request, next);
    }

    /**
     * 按选中候选重建 HttpRequest（M-3：fetch + 映射链重跑留在 nop-gateway 缓冲层内部，
     * 本回调只产出请求）。
     */
    private HttpRequest buildHttpRequest(ApiRequest<?> request, ModelClassCandidate candidate) {
        // 请求体 = converter 转换产物（读当前 properties——新 dialect/model/真实 config）
        ApiRequest<?> converted = interceptor.getConverter().toBackendRequest(request);
        LlmModel config = LlmConfigHelper.loadConfig(candidate.getProvider());
        String apiKey = candidate.getAccountKey();
        if (StringHelper.isEmpty(apiKey)) {
            // 主账号：凭证链回退（W6 语义：备用账号 apiKey 直接下沉 accountKey）。
            apiKey = LlmConfigHelper.resolveApiKey(candidate.getProvider());
        }
        ILlmDialect dialect = LlmDialectFactory.getDialect(config.getApiStyle());
        String baseUrl = candidate.getAccountBaseUrl();
        if (StringHelper.isEmpty(baseUrl)) {
            baseUrl = config.getBaseUrl();
        }
        HttpRequest httpRequest = new HttpRequest();
        // base 替换语义（B-14）：dialect.buildUrl(base, chatUrl, apiKey) 与首次尝试一致。
        httpRequest.setUrl(dialect.buildUrl(baseUrl, config.getChatUrl(), apiKey));
        httpRequest.setMethod("POST");
        // headers：原请求 headers + 目标账号 apiKey（dialect.setHeaders 覆盖认证头）。
        if (request.getHeaders() != null) {
            for (Map.Entry<String, Object> entry : request.getHeaders().entrySet()) {
                httpRequest.header(entry.getKey(), entry.getValue());
            }
        }
        dialect.setHeaders(httpRequest, apiKey, config.getApiKeyHeader());
        httpRequest.setBody(converted.getData());
        return httpRequest;
    }

    static ModelClassCandidate currentCandidate(ApiRequest<?> request) {
        return new ModelClassCandidate(
                request != null ? request.getStringProperty(PROP_PROVIDER) : null,
                request != null ? request.getStringProperty(PROP_MODEL) : null,
                request != null ? request.getStringProperty(PROP_ACCOUNT_KEY) : null,
                request != null ? request.getStringProperty(GatewayStreamingConstants.PROP_BASE_URL) : null,
                null);
    }
}
