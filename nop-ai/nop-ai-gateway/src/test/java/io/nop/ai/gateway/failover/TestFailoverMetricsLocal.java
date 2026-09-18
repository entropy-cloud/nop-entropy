package io.nop.ai.gateway.failover;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.core.NopAiCoreErrors;
import io.nop.ai.core.reliability.CircuitState;
import io.nop.ai.core.reliability.ThresholdBreaker;
import io.nop.ai.core.routing.ConcurrencyRegistry;
import io.nop.ai.core.service.ChatServiceImpl;
import io.nop.ai.core.service.DefaultChatLogger;
import io.nop.ai.core.service.LlmConfigHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import static io.nop.ai.gateway.failover.FailoverTestSupport.CollectingSubscriber;
import static io.nop.ai.gateway.failover.FailoverTestSupport.FakeHttpClient;
import static io.nop.ai.gateway.failover.FailoverTestSupport.FakeHttpResponse;
import static io.nop.ai.gateway.failover.FailoverTestSupport.StreamScenario;
import static io.nop.ai.gateway.failover.FailoverTestSupport.quotaBody;
import static io.nop.ai.gateway.failover.FailoverTestSupport.streamChunkJson;
import static io.nop.ai.gateway.failover.FailoverTestSupport.streamError;
import static io.nop.ai.gateway.failover.FailoverTestSupport.successBody;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W8 OBS-01 本地形态指标 focused 测试（plan 2026-08-15-1615-1 Phase 3）：每个契约指标类别
 * ≥1 断言（切换 / 重订阅 / 熔断迁移 / 探活与冷却 / 成功失败与 Timer / 饱和 / 并发配对），
 * 经真实事件路径触发（复用 {@link FailoverTestSupport} 基建），维度标签断言
 * （provider/model/account），无 flaky 计时断言（Timer 只断言已记录，不断言耗时值）。
 *
 * <p>指标实例 = {@code FailoverMetricsImpl(SimpleMeterRegistry)}（每测试独立 registry，
 * 与进程级 {@code GlobalMeterRegistry} 隔离，断言确定性）。
 */
class TestFailoverMetricsLocal {

    private static final String SWITCH = "nop.ai.gateway.failover.switch.total";
    private static final String RESUBSCRIBE = "nop.ai.gateway.failover.resubscribe.total";
    private static final String TRANSITION = "nop.ai.gateway.failover.circuit-transition.total";
    private static final String COOLDOWN = "nop.ai.gateway.failover.cooldown.total";
    private static final String SUCCESS = "nop.ai.gateway.failover.request-success.total";
    private static final String FAILURE = "nop.ai.gateway.failover.request-failure.total";
    private static final String DURATION = "nop.ai.gateway.failover.request.duration";
    private static final String SATURATION = "nop.ai.gateway.failover.saturation.total";
    private static final String ACQUIRE = "nop.ai.gateway.failover.concurrency-acquire.total";
    private static final String RELEASE = "nop.ai.gateway.failover.concurrency-release.total";

    private FakeHttpClient fake;
    private ThresholdBreaker breaker;
    private ConcurrencyRegistry registry;
    private ChatServiceFailoverAdapter adapter;
    private SimpleMeterRegistry meterRegistry;
    private FailoverMetricsImpl metrics;

    @BeforeAll
    static void init() {
        CoreInitialization.initialize();
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
        meterRegistry = new SimpleMeterRegistry();
        metrics = new FailoverMetricsImpl(meterRegistry);
        adapter = new ChatServiceFailoverAdapter();
        adapter.setDelegate(impl);
        adapter.setBreaker(breaker);
        adapter.setRegistry(registry);
        adapter.setMetrics(metrics);
        io.nop.api.core.config.AppConfig.getConfigProvider().assignConfigValue(
                "nop.ai.llm.gw-test.api-key", "key-gw-main");
    }

    @AfterEach
    void tearDown() {
        io.nop.api.core.config.AppConfig.getConfigProvider().assignConfigValue(
                "nop.ai.llm.gw-test.api-key", null);
        LlmConfigHelper.reset();
    }

    // ======================= 切换 + 成功率/延迟（非流式） =======================

    @Test
    void switchAndRequestOutcomeMetricsOnAccountSwitch() throws Exception {
        fake.queueResponse(response(429, quotaBody()))
                .queueResponse(response(200, successBody()));

        ChatResponse resp = syncGet(adapter.callAsync(
                FailoverTestSupport.request("gw-test", "gw-model-1", false), null));

        assertTrue(resp.isSuccess());
        // 切换计数：维度 = 新候选（provider/model/account）。
        assertEquals(1L, counter(SWITCH, "provider", "gw-test", "model", "gw-model-1", "account", "key-***(8)"));
        // 失败计数：主账号（accountKey null → 空串维度）。
        assertEquals(1L, counter(FAILURE, "provider", "gw-test", "model", "gw-model-1", "account", ""));
        // 成功计数：备份账号。
        assertEquals(1L, counter(SUCCESS, "provider", "gw-test", "model", "gw-model-1", "account", "key-***(8)"));
        // Timer 只断言已记录（flaky-free，不断言耗时值）。
        assertEquals(1L, timerCount(DURATION, "outcome", "success"));
        assertEquals(1L, timerCount(DURATION, "outcome", "failure"));
        // 并发配对路径观测：acquire == release 且均 ≥ 每次 attempt 的配对。
        assertEquals(counterTotal(ACQUIRE), counterTotal(RELEASE), "acquire/release 配对必须平衡");
        assertTrue(counterTotal(ACQUIRE) >= 2, "两个 attempt 各一次 acquire");
        // 未触发类别不得计数。
        assertEquals(0L, counter(RESUBSCRIBE));
        assertEquals(0L, counter(SATURATION));
    }

    // ======================= 熔断迁移 + 冷却启动（CLOSED→OPEN） =======================

    @Test
    void circuitTransitionAndCooldownStartedOnBreakerTrip() throws Exception {
        breaker = new ThresholdBreaker(1, 0);
        adapter.setBreaker(breaker);
        fake.queueResponse(response(429, quotaBody()))
                .queueResponse(response(200, successBody()));

        ChatResponse resp = syncGet(adapter.callAsync(
                FailoverTestSupport.request("gw-test", "gw-model-1", false), null));

        assertTrue(resp.isSuccess());
        // 熔断迁移（编排层调用点派生）：主账号 1 次失败 → CLOSED→OPEN。
        assertEquals(1L, counter(TRANSITION, "provider", "gw-test", "model", "gw-model-1", "to-state", "OPEN"));
        // 冷却启动 = 迁移至 OPEN。
        assertEquals(1L, counter(COOLDOWN, "provider", "gw-test", "model", "gw-model-1", "type", "started"));
        // 备份账号成功 recordSuccess 在 CLOSED 上 = 无迁移。
        assertEquals(0L, counter(TRANSITION, "to-state", "CLOSED"));
        assertEquals(CircuitState.OPEN, breaker.getState("gw-test:gw-model-1"),
                "breaker 状态机语义不受观测影响");
    }

    // ======================= 冷却拒绝 + 全池饱和 fail-loud =======================

    @Test
    void cooldownRejectedAndSaturationOnHealthSaturation() {
        // 健康度饱和（全候选 OPEN）且冷却未期满 → 探活不放行 → 全池饱和 fail-loud。
        ThresholdBreaker longCooldown = new ThresholdBreaker(1, 60_000L);
        adapter.setBreaker(longCooldown);
        longCooldown.recordFailure("gw-test:gw-model-1");
        longCooldown.recordFailure("gw-test2:gw-model-2");

        NopException e = assertThrows(NopException.class,
                () -> syncGet(adapter.callAsync(
                        FailoverTestSupport.request("gw-test", "gw-model-1", false), null)));
        assertEquals(NopAiCoreErrors.ERR_AI_MODEL_CLASS_SATURATED.getErrorCode(), e.getErrorCode());

        // 冷却期计数：OPEN 下 allowCall 未期满拒绝。探活遍历 = 类内（tier-gw：gw-test 主 +
        // gw-backup-a/b + gw-test2 主） + provider 链（gw-test2/gw-rl/gw-sat 及账号展开）；
        // 熔断键 = provider:model（不含 account）——gw-test 键 3 候选、gw-test2 键 2 候选各被
        // allowCall 拒绝一次。
        assertEquals(3L, counter(COOLDOWN, "provider", "gw-test", "model", "gw-model-1", "type", "rejected"));
        assertEquals(2L, counter(COOLDOWN, "provider", "gw-test2", "model", "gw-model-2", "type", "rejected"));
        // HALF_OPEN 探活占用拒绝不触发（无探活放行）。
        assertEquals(0L, counter(COOLDOWN, "type", "probe-rejected"));
        // 饱和指标（§3.3）：provider + model-class 维度。
        assertEquals(1L, counter(SATURATION, "provider", "gw-test", "model-class", "tier-gw"));
        // 探活未放行 → 无 OPEN→HALF_OPEN 迁移。
        assertEquals(0L, counter(TRANSITION, "to-state", "HALF_OPEN"));
    }

    // ======================= 探活放行（OPEN→HALF_OPEN→CLOSED 恢复） =======================

    @Test
    void probeAdmissionTransitionsOnRecovery() throws Exception {
        tripOpen("gw-test:gw-model-1");
        tripOpen("gw-test2:gw-model-2");
        fake.queueResponse(response(200, successBody()));

        ChatResponse resp = syncGet(adapter.callAsync(
                FailoverTestSupport.request("gw-test", "gw-model-1", false), null));

        assertTrue(resp.isSuccess(), "probe-admitted account must be callable again");
        // 探活放行 = OPEN→HALF_OPEN 迁移（类内 2 候选均被探活）。
        assertEquals(2L, counter(TRANSITION, "to-state", "HALF_OPEN"));
        // 探活成功 recordSuccess → HALF_OPEN→CLOSED（仅被调用候选）。
        assertEquals(1L, counter(TRANSITION, "to-state", "CLOSED"));
        // 冷却 0：无拒绝事件。
        assertEquals(0L, counter(COOLDOWN));
        assertEquals(1L, counter(SUCCESS, "provider", "gw-test", "model", "gw-model-1", "account", ""));
    }

    // ======================= 流式重订阅 + 切换 =======================

    @Test
    void streamingResubscribeAndSwitchMetrics() {
        fake.queueStream(StreamScenario.success(streamChunkJson("a1"), streamChunkJson("a2"))
                        .error(streamError(429, quotaBody())))
                .queueStream(StreamScenario.success(
                        streamChunkJson("b1"), streamChunkJson("b2"), streamChunkJson("b3"),
                        streamChunkJson("b4"), streamChunkJson("b5"), streamChunkJson("b6"),
                        streamChunkJson("b7"), streamChunkJson("b8"), streamChunkJson("b9"),
                        streamChunkJson("b10"), streamChunkJson("b11"), streamChunkJson("b12")));

        CollectingSubscriber sub = new CollectingSubscriber();
        adapter.callStream(FailoverTestSupport.request("gw-test", "gw-model-1", true), null).subscribe(sub);
        sub.awaitAndAssertSuccess("b1", "b2", "b3", "b4", "b5", "b6", "b7", "b8", "b9", "b10", "b11", "b12");

        // 重订阅计数（attempt2，换候选）。
        assertEquals(1L, counter(RESUBSCRIBE, "provider", "gw-test", "model", "gw-model-1", "account", "key-***(8)"));
        // 流式换候选重订阅同时计入切换。
        assertEquals(1L, counter(SWITCH, "provider", "gw-test", "model", "gw-model-1", "account", "key-***(8)"));
        // 成功率/延迟（attempt 级：attempt1 失败 → attempt2 成功）。
        assertEquals(1L, counter(FAILURE, "provider", "gw-test", "model", "gw-model-1", "account", ""));
        assertEquals(1L, counter(SUCCESS, "provider", "gw-test", "model", "gw-model-1", "account", "key-***(8)"));
        assertEquals(1L, timerCount(DURATION, "outcome", "failure"));
        assertEquals(1L, timerCount(DURATION, "outcome", "success"));
        // 并发配对平衡（attempt1 +1/-1，attempt2 +1/-1）。
        assertEquals(counterTotal(ACQUIRE), counterTotal(RELEASE));
        assertTrue(counterTotal(ACQUIRE) >= 2);
    }

    // ======================= 辅助 =======================

    private long counter(String name, String... tags) {
        // 求和：同名称同标签子集可能命中多个 meter（如 to-state 维度多候选）。
        return meterRegistry.find(name).tags(tags).counters().stream()
                .mapToLong(c -> (long) c.count()).sum();
    }

    private long timerCount(String name, String... tags) {
        Timer timer = meterRegistry.find(name).tags(tags).timer();
        return timer != null ? timer.count() : 0L;
    }

    private long counterTotal(String name) {
        return meterRegistry.get(name).counters().stream()
                .mapToLong(c -> (long) c.count()).sum();
    }

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
    /** F-AI4-1 回归：account 标签必须是掩码值，绝不能是原始 API key。 */
    @Test
    public void testAccountTagMasked() {
        assertEquals("key-***(8)", FailoverMetricsImpl.maskAccount("key-gw-a"));
        assertEquals("sk-1***(" + 40 + ")", FailoverMetricsImpl.maskAccount("sk-1" + "a".repeat(36)));
        assertEquals("", FailoverMetricsImpl.maskAccount(null));
        assertEquals("***", FailoverMetricsImpl.maskAccount("ab"));
        String rawKey = "sk-PRODUCTION-KEY-1234567890abcdef";
        String masked = FailoverMetricsImpl.maskAccount(rawKey);
        assertNotEquals(rawKey, masked);
        assertFalse(masked.contains("PRODUCTION-KEY"));
    }

}
