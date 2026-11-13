package io.nop.gateway.core.executor;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.concurrent.semaphore.DefaultSemaphore;
import io.nop.commons.concurrent.semaphore.ISemaphore;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.gateway.GatewayConfigs;
import io.nop.gateway.GatewayErrors;
import io.nop.gateway.core.context.GatewayContextImpl;
import io.nop.gateway.core.context.IGatewayContext;
import io.nop.gateway.core.interceptor.AiFailoverGatewayInterceptor;
import io.nop.gateway.core.interceptor.IGatewayInvocation;
import io.nop.gateway.model.GatewayInvokeModel;
import io.nop.gateway.model.GatewayRouteModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2508 2.3-C8-002 回归（C10-006 成员面）：网关 url 路由腿上游在途并发上界 + 429 重试时间整形。
 *
 * <p>红形态（修复前/变异：准入禁用）：permits 耗尽后第 N+1 并发腿照常发起并挂起（在途计数无界）；
 * 429 重试挂起窗不占许可；fallback 腿拒绝无传播路径。绿形态：第 N+1 腿零上游调用 + 类型化拒绝
 * （{@code ERR_GATEWAY_UPSTREAM_INFLIGHT_LIMIT}，不可重试）；在途全程 ≤ permits（含重试挂起窗）；
 * 终态/异常路径许可归还；fallback 腿拒绝经 failedFuture 传播至外层 future 终态（无挂死）；
 * equal jitter = D/2 + U(0, D/2)。
 */
@Timeout(60)
public class TestGatewayUpstreamAdmission {

    private static GatewayRouteModel route() {
        GatewayInvokeModel invoke = new GatewayInvokeModel();
        invoke.setUrl((IEvalAction) ctx -> "http://test-upstream/v1/chat/completions");
        GatewayRouteModel r = new GatewayRouteModel();
        r.setInvoke(invoke);
        return r;
    }

    private static GatewayContextImpl ctx() {
        GatewayContextImpl ctx = new GatewayContextImpl();
        ctx.setHttpMethod("POST");
        return ctx;
    }

    private static NopException admissionError(Throwable t) {
        Throwable c = t;
        while (c != null) {
            if (c instanceof NopException
                    && GatewayErrors.ERR_GATEWAY_UPSTREAM_INFLIGHT_LIMIT.getErrorCode().equals(((NopException) c).getErrorCode()))
                return (NopException) c;
            c = c.getCause();
        }
        return null;
    }

    /** 红①：permits=2，三并发在途 → 第 3 腿零上游调用 + 类型化拒绝；在途 ≤ 2；终态释放后可再准入。 */
    @Test
    public void testInflightBoundedAndExcessRejectedThenReleased() throws Exception {
        ControlledUpstreamHttpClient client = new ControlledUpstreamHttpClient();
        ISemaphore permits = new DefaultSemaphore(2);
        InvokeProcessor processor = new InvokeProcessor(null, client, permits);

        CompletionStage<ApiResponse<?>> s1 = processor.invoke(route(), ApiRequest.build(Map.of()), ctx());
        CompletionStage<ApiResponse<?>> s2 = processor.invoke(route(), ApiRequest.build(Map.of()), ctx());
        assertTrue(client.awaitPending(2, 5000), "two legs must be in flight (pending upstream)");

        NopException rejected = assertThrows(NopException.class,
                () -> processor.invoke(route(), ApiRequest.build(Map.of()), ctx()),
                "third leg must be admission-rejected synchronously on the main leg");
        assertEquals(GatewayErrors.ERR_GATEWAY_UPSTREAM_INFLIGHT_LIMIT.getErrorCode(), rejected.getErrorCode());
        assertEquals(2, client.totalCalls.get(), "rejected leg must not issue any upstream call");
        assertTrue(client.maxInflight.get() <= 2, "inflight must never exceed permits, was " + client.maxInflight.get());

        client.completeAllPending();
        s1.toCompletableFuture().get(5, TimeUnit.SECONDS);
        s2.toCompletableFuture().get(5, TimeUnit.SECONDS);

        // 终态释放 → 第 4 腿重新可准入
        CompletionStage<ApiResponse<?>> s4 = processor.invoke(route(), ApiRequest.build(Map.of()), ctx());
        assertTrue(client.awaitPending(1, 5000));
        client.completeAllPending();
        assertNotNull(s4.toCompletableFuture().get(5, TimeUnit.SECONDS));
        assertEquals(3, client.totalCalls.get());
    }

    /** 红②：429 重试挂起窗全程持有许可——挂起期间第 2 腿被拒绝；重试终态后释放。 */
    @Test
    public void testRetrySuspendWindowHoldsPermit() throws Exception {
        ControlledUpstreamHttpClient client = new ControlledUpstreamHttpClient();
        ISemaphore permits = new DefaultSemaphore(1);
        // jitter 源固定 1.0 → delay = D/2 + D/2 = D（Retry-After: 2 → 2000ms 挂起窗，确定性）
        InvokeProcessor processor = new InvokeProcessor(null, client, permits, () -> 0.999);

        CompletionStage<ApiResponse<?>> s1 = processor.invoke(route(), ApiRequest.build(Map.of()), ctx());
        assertTrue(client.awaitPending(1, 5000));
        // 首发 429 Retry-After: 2 → 进入 ~2s 挂起窗
        client.completeOnePending(429, Map.of("Retry-After", "2"), "slow down");
        Thread.sleep(200);
        assertFalse(s1.toCompletableFuture().isDone(), "leg must be suspended in retry window");

        NopException rejected = assertThrows(NopException.class,
                () -> processor.invoke(route(), ApiRequest.build(Map.of()), ctx()),
                "permit must be held across the retry suspend window");
        assertEquals(GatewayErrors.ERR_GATEWAY_UPSTREAM_INFLIGHT_LIMIT.getErrorCode(), rejected.getErrorCode());

        // 挂起结束 → 重试发起 → 200 终态 → 释放
        assertTrue(client.awaitPending(1, 5000), "retry must be issued after the suspend window");
        client.completeOnePending(200, Map.of(), "{}");
        s1.toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertTrue(permits.tryAcquire(1, 0), "permit must be released at terminal state");
        permits.release(1);
    }

    /** 异常路径许可归还：终态失败（5xx 零重试）后在途计数回零、许可可再获取。 */
    @Test
    public void testPermitReleasedOnTerminalFailure() throws Exception {
        Object old = AppConfig.getConfigProvider().getConfigValue(
                GatewayConfigs.CFG_GATEWAY_UPSTREAM_MAX_RETRIES.getName(), null);
        AppConfig.getConfigProvider().updateConfigValue(GatewayConfigs.CFG_GATEWAY_UPSTREAM_MAX_RETRIES, 0);
        try {
            ControlledUpstreamHttpClient client = new ControlledUpstreamHttpClient();
            ISemaphore permits = new DefaultSemaphore(1);
            InvokeProcessor processor = new InvokeProcessor(null, client, permits);

            CompletionStage<ApiResponse<?>> s1 = processor.invoke(route(), ApiRequest.build(Map.of()), ctx());
            assertTrue(client.awaitPending(1, 5000));
            client.completeOnePending(503, Map.of(), "down");
            CompletionException ex = assertThrows(CompletionException.class, () -> s1.toCompletableFuture().join());
            assertTrue(ex.getCause() instanceof NopException);
            assertEquals(GatewayErrors.ERR_GATEWAY_UPSTREAM_FAILED.getErrorCode(),
                    ((NopException) ex.getCause()).getErrorCode());
            assertEquals(1, client.totalCalls.get(), "max-retries=0 must yield zero retries");
            assertTrue(permits.tryAcquire(1, 0), "permit must be returned on terminal failure");
            permits.release(1);
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(GatewayConfigs.CFG_GATEWAY_UPSTREAM_MAX_RETRIES,
                    old == null ? null : (Integer) old);
        }
    }

    /** fallback 腿拒绝必须经 failedFuture 传播至外层 future 终态（不挂死、不同步 throw）。 */
    @Test
    public void testFallbackLegRejectionPropagatesAsFailedFuture() throws Exception {
        ControlledUpstreamHttpClient client = new ControlledUpstreamHttpClient();
        AiFailoverGatewayInterceptor interceptor = new AiFailoverGatewayInterceptor();
        interceptor.setHttpClient(client);
        interceptor.setFallbackUrls(List.of("http://fallback-1/v1", "http://fallback-2/v1"));
        interceptor.setAdmissionPermits(new DefaultSemaphore(0)); // 许可耗尽：每条 fallback 腿均被拒

        IGatewayInvocation upstream429 = failingInvocation(GatewayErrors.ERR_GATEWAY_UPSTREAM_429, 429);
        CompletionStage<ApiResponse<?>> f = interceptor.invoke(upstream429, ApiRequest.build(Map.of()), ctx());

        // 有界观察窗：拒绝必须到达外层 future 终态（挂死形态下 5s 内不完成即红）
        assertTrue(waitDone(f.toCompletableFuture(), 5000), "rejection must reach the outer future (no hang)");
        CompletionException ex = assertThrows(CompletionException.class, () -> f.toCompletableFuture().join(),
                "rejection must reach the outer future as a terminal failure");
        assertNotNull(admissionError(ex), "outer failure must carry the admission error code");
        assertEquals(0, client.totalCalls.get(), "rejected fallback leg must not call upstream");
    }

    /** 准入拒绝码不可重试：主腿以该码失败时不触发任何 fallback 级联。 */
    @Test
    public void testAdmissionRejectionIsNotRetryable() throws Exception {
        ControlledUpstreamHttpClient client = new ControlledUpstreamHttpClient();
        AiFailoverGatewayInterceptor interceptor = new AiFailoverGatewayInterceptor();
        interceptor.setHttpClient(client);
        interceptor.setFallbackUrls(List.of("http://fallback-1/v1"));
        interceptor.setAdmissionPermits(new DefaultSemaphore(10));

        IGatewayInvocation rejectedMain = failingInvocation(GatewayErrors.ERR_GATEWAY_UPSTREAM_INFLIGHT_LIMIT, 0);
        CompletionStage<ApiResponse<?>> f = interceptor.invoke(rejectedMain, ApiRequest.build(Map.of()), ctx());
        CompletionException ex = assertThrows(CompletionException.class, () -> f.toCompletableFuture().join());
        assertNotNull(admissionError(ex));
        assertEquals(0, client.totalCalls.get(), "non-retryable admission rejection must not cascade to fallback");
    }

    /** equal jitter：D/2 + U(0, D/2)；D=0 → 0；源可注入。 */
    @Test
    public void testEqualJitterShape() {
        assertEquals(500L, new InvokeProcessor(null, null, null, () -> 0.0).applyEqualJitter(1000));
        assertEquals(999L, new InvokeProcessor(null, null, null, () -> 0.999).applyEqualJitter(1000));
        assertEquals(0L, new InvokeProcessor(null, null, null, () -> 0.5).applyEqualJitter(0));
        long v = new InvokeProcessor(null, null).applyEqualJitter(30_000);
        assertTrue(v >= 15_000 && v < 30_000, "jitter must stay within [D/2, D), was " + v);
    }

    /** 兼容构造（旧两参）= 不设上界档：三并发全部准入。 */
    @Test
    public void testLegacyConstructorIsUnbounded() throws Exception {
        ControlledUpstreamHttpClient client = new ControlledUpstreamHttpClient();
        InvokeProcessor processor = new InvokeProcessor(null, client);
        for (int i = 0; i < 3; i++) {
            processor.invoke(route(), ApiRequest.build(Map.of()), ctx());
        }
        assertTrue(client.awaitPending(3, 5000), "legacy constructor must not gate admission");
        client.completeAllPending();
    }

    private static boolean waitDone(CompletableFuture<?> f, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (f.isDone())
                return true;
            Thread.sleep(10);
        }
        return f.isDone();
    }

    private static IGatewayInvocation failingInvocation(io.nop.api.core.exceptions.ErrorCode code, int status) {
        return new IGatewayInvocation() {
            @Override
            public CompletionStage<ApiResponse<?>> proceedInvoke(ApiRequest<?> req, IGatewayContext svcCtx) {
                return CompletableFuture.failedFuture(new NopException(code).param("httpStatus", status));
            }

            @Override public ApiRequest<?> proceedOnRequest(ApiRequest<?> r, IGatewayContext c) { return r; }
            @Override public ApiResponse<?> proceedOnResponse(ApiResponse<?> r, IGatewayContext c) { return r; }
            @Override public ApiResponse<?> proceedOnError(Throwable e, IGatewayContext c) { return null; }
            @Override public void proceedOnStreamStart(ApiRequest<?> r, IGatewayContext c) {}
            @Override public Object proceedOnStreamElement(Object e, IGatewayContext c) { return e; }
            @Override public Object proceedOnStreamError(Throwable e, IGatewayContext c) { return null; }
            @Override public void proceedOnStreamComplete(IGatewayContext c) {}
        };
    }
}
