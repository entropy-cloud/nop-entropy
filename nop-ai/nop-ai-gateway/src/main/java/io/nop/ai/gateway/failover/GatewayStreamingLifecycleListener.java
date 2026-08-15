package io.nop.ai.gateway.failover;

import io.nop.ai.core.routing.ConcurrencyRegistry;
import io.nop.api.core.beans.ApiRequest;
import io.nop.gateway.core.streaming.IStreamingLifecycleListener;

/**
 * 网关形态流式并发计数生命周期监听器（W7，plan 2026-08-15-1116-3 Phase 4，GW-A6/B-2）。
 *
 * <p>由 {@code AiGatewayFailoverInterceptor.onRequest} 经 {@code IGatewayContext} attribute
 * 注入 nop-gateway 缓冲层：{@code onFetchStarted} = fetch 发起 +1（键 = request properties
 * 当前候选的 (provider, accountKey)），{@code onStreamTerminated} = attempt 终止 -1；
 * 重订阅 = -1 旧 attempt（释放上次 acquire 时记录的键）+1 新 attempt（读更新后的 properties）。
 *
 * <p><b>预检语义（GW-A6/B-13）</b>：选择期饱和检查为尽力而为预检，本监听器为记账语义——
 * 并发窗口下可能短暂超限，不承诺硬上限（W8 指标审计可追溯）。
 *
 * <p>缓冲关闭（bufferEnabled=false）时仍触发（计数与缓冲解耦）；无路由组直通路径不注册
 * 本监听器（不计数，零回归）。
 */
final class GatewayStreamingLifecycleListener implements IStreamingLifecycleListener {

    private final ConcurrencyRegistry registry;
    private final ApiRequest<?> request;
    private final IFailoverMetrics metrics;
    private String acquiredProvider;    // guarded by this
    private String acquiredAccountKey;  // guarded by this
    private boolean released;           // guarded by this

    GatewayStreamingLifecycleListener(ConcurrencyRegistry registry, ApiRequest<?> request,
                                      IFailoverMetrics metrics) {
        this.registry = registry;
        this.request = request;
        this.metrics = metrics;
    }

    @Override
    public synchronized void onFetchStarted() {
        // 重订阅 -1 旧 attempt +1 新 attempt：先释放上一 attempt（若未释放——防御，正常路径
        // 由 onStreamTerminated 释放）。
        releaseAcquired();
        String provider = request.getStringProperty(FailoverConstants.PROP_PROVIDER);
        String accountKey = request.getStringProperty(FailoverConstants.PROP_ACCOUNT_KEY);
        registry.acquire(provider, accountKey);
        acquiredProvider = provider;
        acquiredAccountKey = accountKey;
        released = false;
        if (metrics != null) {
            // 流建立 = 并发计数 acquire（配对观测）。
            metrics.onConcurrencyAcquire(provider, accountKey);
        }
    }

    @Override
    public synchronized void onStreamTerminated(Throwable cause) {
        releaseAcquired();
    }

    private void releaseAcquired() {
        String provider = acquiredProvider;
        String accountKey = acquiredAccountKey;
        if (provider != null && !released) {
            registry.release(provider, accountKey);
            released = true;
            acquiredProvider = null;
            acquiredAccountKey = null;
            if (metrics != null) {
                // 流终止 = 并发计数 release（配对观测）。
                metrics.onConcurrencyRelease(provider, accountKey);
            }
        }
    }
}
