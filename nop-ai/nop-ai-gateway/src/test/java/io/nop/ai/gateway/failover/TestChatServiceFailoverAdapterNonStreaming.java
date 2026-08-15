package io.nop.ai.gateway.failover;

import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.ErrorClassification;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.core.NopAiCoreErrors;
import io.nop.ai.core.reliability.CircuitState;
import io.nop.ai.core.reliability.LlmErrorClassifier;
import io.nop.ai.core.reliability.ThresholdBreaker;
import io.nop.ai.core.routing.ConcurrencyRegistry;
import io.nop.ai.core.routing.ISelectionStrategy;
import io.nop.ai.core.routing.ModelClassCandidate;
import io.nop.ai.core.service.ChatServiceImpl;
import io.nop.ai.core.service.DefaultChatLogger;
import io.nop.ai.core.service.LlmConfigHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import static io.nop.ai.gateway.failover.FailoverTestSupport.FakeHttpClient;
import static io.nop.ai.gateway.failover.FailoverTestSupport.FakeHttpResponse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W6 本地适配器非流式测试（plan 2026-08-15-1116-2 Phase 4，LOCAL-04 非流式部分）：
 * 切换链（QUOTA/AUTH/RATE_LIMITED/TRANSIENT → 切换；NON_TRANSIENT → 不切换）、预算耗尽
 * fail-loud、无路由组直通零回归、熔断记账/恢复（B-1）、探活候选池（Major-2）、并发计数
 * acquire/release 全终止路径配对（M-2）+ acquire 后复查（M-6a）、重订阅同步异常（M-4）、
 * 调用方已取消干净终止（Minor-1）、接线验证（Minimum Rules #23）。
 */
class TestChatServiceFailoverAdapterNonStreaming {

    private FakeHttpClient fake;
    private ThresholdBreaker breaker;
    private ConcurrencyRegistry registry;
    private ChatServiceFailoverAdapter adapter;

    @BeforeAll
    static void init() {
        CoreInitialization.initialize();
        // M-4/M-2 同步抛测试：本地限流许可获取超时 0 = 立即失败（fail-fast），确定性。
        System.setProperty("nop.ai.service.rate-limit-acquire-timeout", "0");
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    void setUp() {
        fake = new FakeHttpClient();
        ChatServiceImpl impl = new ChatServiceImpl();
        impl.setHttpClient(fake);
        impl.setChatLogger(new DefaultChatLogger());
        breaker = new ThresholdBreaker(3, 0);
        registry = new ConcurrencyRegistry();
        adapter = new ChatServiceFailoverAdapter();
        adapter.setDelegate(impl);
        adapter.setBreaker(breaker);
        adapter.setRegistry(registry);
        io.nop.api.core.config.AppConfig.getConfigProvider().assignConfigValue(
                "nop.ai.llm.gw-test.api-key", "key-gw-main");
        io.nop.api.core.config.AppConfig.getConfigProvider().assignConfigValue(
                "nop.ai.llm.gw-cache.api-key", "key-gw-main");
    }

    @AfterEach
    void tearDown() {
        io.nop.api.core.config.AppConfig.getConfigProvider().assignConfigValue(
                "nop.ai.llm.gw-test.api-key", null);
        io.nop.api.core.config.AppConfig.getConfigProvider().assignConfigValue(
                "nop.ai.llm.gw-cache.api-key", null);
        LlmConfigHelper.reset();
    }

    // ======================= 切换链 =======================

    @Test
    void quotaExceededSwitchesToNextAccount() throws Exception {
        fake.queueResponse(response(429, FailoverTestSupport.quotaBody()))
                .queueResponse(response(200, FailoverTestSupport.successBody()));

        ChatResponse resp = syncGet(adapter.callAsync(FailoverTestSupport.request("gw-test", "gw-model-1", false), null));

        assertTrue(resp.isSuccess(), "second attempt on backup account must succeed: " + resp.getError());
        assertEquals(2, fake.requests.size(), "one failure → one switch");
        assertBearer(fake.requests.get(1), "key-gw-a",
                "switch must sink backup-account apiKey into the second request");
        assertEquals(0, registry.currentCount("gw-test", null), "release must pair the acquire");
        assertEquals(0, registry.currentCount("gw-test", "key-gw-a"));
        // 接线验证（Minimum Rules #23）：失败被记账 → 熔断器观察到失败（1 次，未达阈值）。
        assertEquals(CircuitState.CLOSED, breaker.getState("gw-test:gw-model-1"));
    }

    @Test
    void authInvalidSwitchesToNextAccount() throws Exception {
        fake.queueResponse(response(401, FailoverTestSupport.authBody()))
                .queueResponse(response(200, FailoverTestSupport.successBody()));

        ChatResponse resp = syncGet(adapter.callAsync(FailoverTestSupport.request("gw-test", "gw-model-1", false), null));

        assertTrue(resp.isSuccess());
        assertEquals(2, fake.requests.size());
        assertBearer(fake.requests.get(1), "key-gw-a");
        assertEquals(0, registry.currentCount("gw-test", null));
    }

    @Test
    void rateLimitedSwitchesToNextAccount() throws Exception {
        fake.queueResponse(response(429, FailoverTestSupport.rateLimitBody()))
                .queueResponse(response(200, FailoverTestSupport.successBody()));

        ChatResponse resp = syncGet(adapter.callAsync(FailoverTestSupport.request("gw-test", "gw-model-1", false), null));

        assertTrue(resp.isSuccess());
        assertEquals(2, fake.requests.size());
        assertBearer(fake.requests.get(1), "key-gw-a");
    }

    @Test
    void transientResponseSwitchesToNextAccount() throws Exception {
        fake.queueResponse(response(503, FailoverTestSupport.serverErrorBody()))
                .queueResponse(response(200, FailoverTestSupport.successBody()));

        ChatResponse resp = syncGet(adapter.callAsync(FailoverTestSupport.request("gw-test", "gw-model-1", false), null));

        assertTrue(resp.isSuccess());
        assertEquals(2, fake.requests.size());
        assertBearer(fake.requests.get(1), "key-gw-a");
    }

    @Test
    void transportErrorSwitchesToNextAccount() throws Exception {
        // 传输异常级（无 HTTP 响应）：async 异常 → LlmErrorClassifier → TRANSIENT → 切换。
        fake.queueFailure(new java.net.ConnectException("connection refused"))
                .queueResponse(response(200, FailoverTestSupport.successBody()));

        ChatResponse resp = syncGet(adapter.callAsync(FailoverTestSupport.request("gw-test", "gw-model-1", false), null));

        assertTrue(resp.isSuccess());
        assertEquals(2, fake.requests.size());
        assertBearer(fake.requests.get(1), "key-gw-a");
        assertEquals(0, registry.currentCount("gw-test", null), "async exception path must release (M-2)");
    }

    @Test
    void nonTransientFailsDirectlyWithoutSwitch() throws Exception {
        fake.queueResponse(response(400, FailoverTestSupport.nonTransientBody()));

        ChatResponse resp = syncGet(adapter.callAsync(FailoverTestSupport.request("gw-test", "gw-model-1", false), null));

        assertFalseSuccess(resp);
        assertEquals(ErrorClassification.NON_TRANSIENT, resp.getErrorClassification());
        assertEquals(1, fake.requests.size(), "NON_TRANSIENT must not switch (no budget consumed)");
        assertEquals(CircuitState.CLOSED, breaker.getState("gw-test:gw-model-1"),
                "NON_TRANSIENT must not record breaker failure");
        assertEquals(0, registry.currentCount("gw-test", null), "NON_TRANSIENT path must release (M-2)");
    }

    // ======================= 预算耗尽 fail-loud + 熔断记账 =======================

    @Test
    void budgetExhaustedFailsLoudAndTripsBreaker() throws Exception {
        // retryBudget 默认 2：初始 + 2 次重试 = 3 次调用，全失败 → 返回末次错误响应（fail-loud）。
        fake.queueResponse(response(429, FailoverTestSupport.quotaBody()))
                .queueResponse(response(429, FailoverTestSupport.quotaBody()))
                .queueResponse(response(429, FailoverTestSupport.quotaBody()));

        ChatResponse resp = syncGet(adapter.callAsync(FailoverTestSupport.request("gw-test", "gw-model-1", false), null));

        assertFalseSuccess(resp);
        assertEquals(ErrorClassification.QUOTA_EXCEEDED, resp.getErrorClassification());
        assertEquals(3, fake.requests.size(), "budget 2 = 1 initial + 2 retries, then fail-loud");
        assertBearer(fake.requests.get(2), "key-gw-b", "third attempt = second backup account");
        // 记账归属（D6）：同一模型类内多账号连续失败跨账号累计 → 3 次失败达阈值 → OPEN。
        assertEquals(CircuitState.OPEN, breaker.getState("gw-test:gw-model-1"),
                "3 consecutive failures on provider:model key must trip the breaker");
        assertEquals(0, registry.currentCount("gw-test", "key-gw-b"), "every terminal path releases (M-2)");
    }

    // ======================= CACHE_STATE_LOST 原地重发（W8 OBS-02 防御分支） =======================

    @Test
    void cacheStateLostRetriesSameCandidateInPlace() throws Exception {
        // CACHE_STATE_LOST（响应级 errorClassification，经 gw-cache.llm.xml errorMappings 可达）：
        // 不触发账号切换，重发同一候选一次（预算计数）；成功 → 无熔断记账（CLOSED）。
        fake.queueResponse(response(409, FailoverTestSupport.cacheLostBody()))
                .queueResponse(response(200, FailoverTestSupport.successBody()));

        ChatResponse resp = syncGet(adapter.callAsync(
                FailoverTestSupport.request("gw-cache", "gw-cache-model", false), null));

        assertTrue(resp.isSuccess(), "in-place resend must succeed: " + resp.getError());
        assertEquals(2, fake.requests.size(), "CACHE_STATE_LOST must resend the same candidate once");
        assertBearer(fake.requests.get(1), "key-gw-main",
                "in-place resend must keep the same account (no account switch)");
        assertEquals(fake.requests.get(0).getUrl(), fake.requests.get(1).getUrl(),
                "in-place resend must hit the same provider URL");
        assertEquals(CircuitState.CLOSED, breaker.getState("gw-cache:gw-cache-model"),
                "CACHE_STATE_LOST must not record breaker failure");
        assertEquals(0, registry.currentCount("gw-cache", null), "every terminal path releases (M-2)");
    }

    @Test
    void cacheStateLostBudgetExhaustedFailsLoud() throws Exception {
        adapter.setRetryBudget(1);
        fake.queueResponse(response(409, FailoverTestSupport.cacheLostBody()))
                .queueResponse(response(409, FailoverTestSupport.cacheLostBody()));

        ChatResponse resp = syncGet(adapter.callAsync(
                FailoverTestSupport.request("gw-cache", "gw-cache-model", false), null));

        assertFalseSuccess(resp);
        assertEquals(ErrorClassification.CACHE_STATE_LOST, resp.getErrorClassification());
        assertEquals(2, fake.requests.size(), "budget 1 = 1 initial + 1 in-place resend, then fail-loud");
        assertBearer(fake.requests.get(1), "key-gw-main", "in-place resend must stay on the same account");
        assertEquals(CircuitState.CLOSED, breaker.getState("gw-cache:gw-cache-model"),
                "CACHE_STATE_LOST budget exhaustion must not trip the breaker");
        assertEquals(0, registry.currentCount("gw-cache", null));
    }

    // ======================= 无路由组直通零回归 =======================

    @Test
    void noRoutingGroupPassThroughZeroRegression() throws Exception {
        fake.queueResponse(response(200, FailoverTestSupport.successBody()));

        ChatResponse resp = syncGet(adapter.callAsync(
                FailoverTestSupport.request("gw-test", "no-such-model", false), null));

        assertTrue(resp.isSuccess());
        assertEquals(1, fake.requests.size(), "no routing group → single direct delegate call");
        assertBearer(fake.requests.get(0), "key-gw-main",
                "pass-through must keep original options (main account, no selection)");
        assertEquals(0, registry.currentCount("gw-test", null),
                "no-routing-group pass-through must not count (M-6b)");
        assertEquals(CircuitState.CLOSED, breaker.getState("gw-test:no-such-model"));
    }

    // ======================= 全池饱和 fail-loud =======================

    @Test
    void fullPoolConcurrencySaturationFailsLoud() {
        // tier-gw-sat：主账号 limit 2 ×2 + s1 limit 1 + s2 limit 1 → 全并发饱和。
        registry.acquire("gw-sat", null);
        registry.acquire("gw-sat", null);
        registry.acquire("gw-sat", "key-sat-s1");
        registry.acquire("gw-sat", "key-sat-s2");

        NopException e = assertThrows(NopException.class,
                () -> syncGet(adapter.callAsync(FailoverTestSupport.request("gw-sat", "gw-sat-model", false), null)));
        assertEquals(NopAiCoreErrors.ERR_AI_MODEL_CLASS_SATURATED.getErrorCode(), e.getErrorCode(),
                "full-pool saturation must fail loud with the saturation error code");

        assertEquals(0, fake.requests.size(), "no request must be sent when the pool is saturated");
        // 主动切换（并发饱和跳过）不记熔断、不耗预算。
        assertEquals(CircuitState.CLOSED, breaker.getState("gw-sat:gw-sat-model"));
    }

    @Test
    void fullPoolHealthSaturationFailsLoud() {
        // 健康度饱和（全候选熔断 OPEN）且冷却未期满 → 探活不放行 → 全池饱和 fail-loud。
        // 注：冷却期满（0）时 OPEN 候选会被探活放行（B-1 语义）——本测试用长冷却 breaker 隔离。
        ThresholdBreaker longCooldown = new ThresholdBreaker(3, 60_000L);
        adapter.setBreaker(longCooldown);
        for (int i = 0; i < 3; i++) {
            longCooldown.recordFailure("gw-sat:gw-sat-model");
        }

        NopException e = assertThrows(NopException.class,
                () -> syncGet(adapter.callAsync(FailoverTestSupport.request("gw-sat", "gw-sat-model", false), null)));
        assertEquals(NopAiCoreErrors.ERR_AI_MODEL_CLASS_SATURATED.getErrorCode(), e.getErrorCode());
        assertEquals(0, fake.requests.size());
    }

    // ======================= 熔断探活恢复（B-1）+ 探活池（Major-2） =======================

    @Test
    void breakerProbeRecoveryEndToEnd() throws Exception {
        // 端到端（B-1）：全池 OPEN（冷却 0）→ 探活遍历 allowCall → HALF_OPEN 放行 → 调用成功 →
        // recordSuccess → CLOSED 恢复 → 账号重新可用。
        tripOpen("gw-test:gw-model-1");
        tripOpen("gw-test2:gw-model-2");

        fake.queueResponse(response(200, FailoverTestSupport.successBody()));

        ChatResponse resp = syncGet(adapter.callAsync(FailoverTestSupport.request("gw-test", "gw-model-1", false), null));

        assertTrue(resp.isSuccess(), "probe-admitted account must be callable again: " + resp.getError());
        assertEquals(1, fake.requests.size());
        assertBearer(fake.requests.get(0), "key-gw-main", "probe must admit the primary candidate");
        assertEquals(CircuitState.CLOSED, breaker.getState("gw-test:gw-model-1"),
                "probe success (recordSuccess) must restore CLOSED");
        assertEquals(0, registry.currentCount("gw-test", null));
    }

    @Test
    void probePoolIncludesProviderChainCandidates() throws Exception {
        // Major-2：探活候选池 = 类内 + provider 链扩展候选（primary = 请求目标 provider）。
        // 链候选 gw-rl（chain(gw-test) = [gw-test2, gw-rl, gw-sat]）OPEN 且冷却期满 →
        // 探活遍历必须对其 allowCall（断言其状态被推进到 HALF_OPEN——探活池含链候选的证据）。
        tripOpen("gw-test:gw-model-1");
        tripOpen("gw-test2:gw-model-2");
        tripOpen("gw-rl:gw-rl-model");

        fake.queueResponse(response(200, FailoverTestSupport.successBody()));

        ChatResponse resp = syncGet(adapter.callAsync(FailoverTestSupport.request("gw-test", "gw-model-1", false), null));

        assertTrue(resp.isSuccess());
        assertEquals(1, fake.requests.size());
        assertEquals(CircuitState.CLOSED, breaker.getState("gw-test:gw-model-1"));
        assertEquals(CircuitState.HALF_OPEN, breaker.getState("gw-rl:gw-rl-model"),
                "provider-chain candidate must be probed (allowCall) by the probe traversal");
    }

    // ======================= 并发复查（M-6a） =======================

    @Test
    void concurrencyRecheckSkipsSaturatedCandidate() throws Exception {
        // 策略忽略健康视图恒选首个未尝试候选：主账号已饱和（2/2）时选择仍返回它 →
        // 适配器 acquire 后复查（3 > 2）→ release + 视为饱和跳过 → 重选 backup-s1。
        ISelectionStrategy blindStrategy = (request, candidates, health, attempted) -> {
            for (ModelClassCandidate c : candidates) {
                if (!attempted.contains(c)) {
                    return c;
                }
            }
            return null;
        };
        adapter.setStrategy(blindStrategy);
        registry.acquire("gw-sat", null);
        registry.acquire("gw-sat", null);
        fake.queueResponse(response(200, FailoverTestSupport.successBody()));

        ChatResponse resp = syncGet(adapter.callAsync(FailoverTestSupport.request("gw-sat", "gw-sat-model", false), null));

        assertTrue(resp.isSuccess());
        assertEquals(1, fake.requests.size());
        assertBearer(fake.requests.get(0), "key-sat-s1",
                "post-acquire over-limit candidate must be skipped and reselected");
        assertEquals(2, registry.currentCount("gw-sat", null), "skip must release the over-limit acquire");
        assertEquals(0, registry.currentCount("gw-sat", "key-sat-s1"));
    }

    // ======================= 重订阅同步异常（M-4 / Minor-2 本地限流边界） =======================

    @Test
    void syncRateLimitOnResubscribeSwitchesToChainCandidateAndRecovers() throws Exception {
        // Guava RateLimiter 实证语义：fresh limiter 首次 tryAcquire 恒成功，第二次立即失败（timeout 0）。
        // attempt1 = gw-rl 主账号（限流通过）→ 响应级 QUOTA 失败；attempt2 = gw-rl-b1（同 provider，
        // 同一限流器 → 同步抛 ERR_AI_RATE_LIMITED——Minor-2 落档边界："限流换账号"不保证生效）；
        // attempt3 = 类内耗尽 → provider 链扩展 → gw-sat 主账号 → 成功。
        fake.queueResponse(response(429, FailoverTestSupport.quotaBody()))
                .queueResponse(response(200, FailoverTestSupport.successBody()));

        ChatResponse resp = syncGet(adapter.callAsync(FailoverTestSupport.request("gw-rl", "gw-rl-model", false), null));

        assertTrue(resp.isSuccess(), "RATE_LIMITED sync-throw must switch and recover: " + resp.getError());
        assertEquals(2, fake.requests.size(),
                "attempt1 (gw-rl) fetch + attempt3 (gw-sat) fetch; attempt2 sync-throws before any fetch");
        assertTrue(fake.requests.get(1).getUrl().contains("gw-sat.example.com"),
                "final attempt must hit the provider-chain candidate gw-sat, got: " + fake.requests.get(1).getUrl());
        assertEquals(0, registry.currentCount("gw-rl", null), "sync-throw path must release (M-2)");
        assertEquals(0, registry.currentCount("gw-rl", "key-rl-b1"));
        assertEquals(0, registry.currentCount("gw-sat", null));
    }

    @Test
    void syncRateLimitBudgetExhaustedFailsLoud() {
        adapter.setRetryBudget(1);
        fake.queueResponse(response(429, FailoverTestSupport.quotaBody()));
        // attempt1 = gw-rl 主账号 → QUOTA 错误响应；attempt2 = gw-rl-b1 → 同步抛 RATE_LIMITED →
        // 2 > 1 预算耗尽 → fail-loud（表面末次同步异常，不静默）。
        Throwable t = assertThrows(Throwable.class,
                () -> syncGet(adapter.callAsync(FailoverTestSupport.request("gw-rl", "gw-rl-model", false), null)));
        assertEquals(ErrorClassification.RATE_LIMITED, LlmErrorClassifier.classify(t),
                "budget-exhausted fail-loud must surface the RATE_LIMITED sync-throw error");
        assertEquals(1, fake.requests.size(), "attempt1 fetch only; attempt2 sync-throws before any fetch");
        assertEquals(0, registry.currentCount("gw-rl", "key-rl-b1"), "sync-throw path must release (M-2)");
    }

    // ======================= 调用方取消（Minor-1） =======================

    @Test
    void callerCancelledBeforeAttemptTerminatesCleanly() {
        fake.queueResponse(response(200, FailoverTestSupport.successBody()));
        ICancelToken cancelled = new ICancelToken() {
            @Override
            public boolean isCancelled() {
                return true;
            }

            @Override
            public String getCancelReason() {
                return "test-cancel";
            }

            @Override
            public void appendOnCancel(java.util.function.Consumer<String> task) {
            }

            @Override
            public void removeOnCancel(java.util.function.Consumer<String> task) {
            }
        };

        CompletionStage<ChatResponse> stage = adapter.callAsync(
                FailoverTestSupport.request("gw-test", "gw-model-1", false), cancelled);
        Throwable t = assertThrows(Throwable.class, () -> syncGet(stage));
        assertTrue(t instanceof CancellationException || unwrap(t) instanceof CancellationException,
                "pre-cancelled token must terminate cleanly, got: " + t);
        assertEquals(0, fake.requests.size(), "cancelled → no attempt must be started");
        assertEquals(0, registry.currentCount("gw-test", null));
        assertEquals(CircuitState.CLOSED, breaker.getState("gw-test:gw-model-1"),
                "cancellation must not record breaker state (no accounting)");
    }

    // ======================= 接线验证（Minimum Rules #23） =======================

    @Test
    void adapterInvokesStrategyAndRouterAtRuntime() throws Exception {
        final int[] strategyCalls = {0};
        ISelectionStrategy recording = (request, candidates, health, attempted) -> {
            strategyCalls[0]++;
            for (ModelClassCandidate c : candidates) {
                if (attempted.contains(c) || !health.healthOf(c).isAvailable()) {
                    continue;
                }
                return c;
            }
            return null;
        };
        adapter.setStrategy(recording);
        fake.queueResponse(response(200, FailoverTestSupport.successBody()));

        syncGet(adapter.callAsync(FailoverTestSupport.request("gw-test", "gw-model-1", false), null));

        assertTrue(strategyCalls[0] >= 1, "adapter must invoke the selection strategy at runtime (wiring)");
        assertEquals(1, fake.requests.size());
    }

    // ======================= 辅助 =======================

    private void tripOpen(String modelKey) {
        for (int i = 0; i < 3; i++) {
            breaker.recordFailure(modelKey);
        }
    }

    private static FakeHttpResponse response(int status, String body) {
        FakeHttpResponse r = new FakeHttpResponse();
        r.status = status;
        r.body = body;
        return r;
    }

    private static void assertBearer(io.nop.http.api.client.HttpRequest request, String expected) {
        assertBearer(request, expected, "bearer token must contain " + expected);
    }

    private static void assertBearer(io.nop.http.api.client.HttpRequest request, String expected, String msg) {
        String token = request.getBearerToken();
        assertNotNull(token, msg);
        assertTrue(token.contains(expected), msg + " (token=" + token + ")");
    }

    private static ChatResponse syncGet(CompletionStage<ChatResponse> stage) throws Exception {
        try {
            return stage.toCompletableFuture().get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new NopException(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG)
                    .param(NopAiCoreErrors.ARG_MSG, "unexpected checked failure: " + cause);
        }
    }

    private static Throwable unwrap(Throwable t) {
        Throwable cause = t;
        while (cause != null && cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static void assertFalseSuccess(ChatResponse resp) {
        assertTrue(!resp.isSuccess(), "expected an error response, got success");
    }
}
