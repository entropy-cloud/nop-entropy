package io.nop.ai.gateway.failover;

import io.nop.ai.core.service.ChatServiceImpl;
import io.nop.ai.core.service.DefaultChatLogger;
import io.nop.ai.core.service.LlmConfigHelper;
import io.nop.api.core.config.AppConfig;
import io.nop.core.initialize.CoreInitialization;
import io.nop.gateway.core.executor.StreamingBufferOverflowException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static io.nop.ai.gateway.failover.FailoverTestSupport.CountingFailoverMetrics;
import static io.nop.ai.gateway.failover.FailoverTestSupport.ManualDemandSubscriber;
import static io.nop.ai.gateway.failover.FailoverTestSupport.StreamScenario;
import static io.nop.ai.gateway.failover.FailoverTestSupport.request;
import static io.nop.ai.gateway.failover.FailoverTestSupport.streamChunkJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2509 R2-06 成员 3.3-C8-001（C10-001 面）回归钉：越窗缓冲硬上界（与窗口期同界
 * = bufferSize）+ 溢出 fail-loud 不可重试终态（裁定⑤）。
 *
 * <p>红形态（修复前）：越窗后 demand==0 元素进无界 ArrayDeque——慢客户端长流无界积压
 * （本钉以 bufferSize=2 + 6 元素长流实证：修复前不产生任何错误终态，缓冲静默堆积）。
 * 绿形态：溢出 = 类型化 {@link StreamingBufferOverflowException} 错误终态 +
 * 溢出零重订阅零 recordFailure（不可重试终态，不进 handleStreamFailure 分类/记账面）。
 * 上游授信按裁定⑤(b) 3.3 半边保留 request(MAX)（阻塞 submit 语义），上界兜底承担防线。
 */
class TestFailoverOverflowBound_C8_001 {

    private FailoverTestSupport.FakeHttpClient fake;
    private ChatServiceFailoverAdapter adapter;
    private CountingFailoverMetrics metrics;

    @BeforeAll
    static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    void setUp() {
        fake = new FailoverTestSupport.FakeHttpClient();
        ChatServiceImpl impl = new ChatServiceImpl();
        impl.setHttpClient(fake);
        impl.setChatLogger(new DefaultChatLogger());
        metrics = new CountingFailoverMetrics();
        adapter = new ChatServiceFailoverAdapter();
        adapter.setDelegate(impl);
        adapter.setMetrics(metrics);
        adapter.setBufferSize(2);
        adapter.setBufferTimeMs(0);
        adapter.setRegistry(new io.nop.ai.gateway.routing.ConcurrencyRegistry());
        adapter.setBreaker(new io.nop.ai.core.reliability.ThresholdBreaker(3, 0));
        AppConfig.getConfigProvider().assignConfigValue("nop.ai.llm.gw-test.api-key", "key-gw-main");
    }

    @AfterEach
    void tearDown() {
        AppConfig.getConfigProvider().assignConfigValue("nop.ai.llm.gw-test.api-key", null);
        LlmConfigHelper.reset();
    }

    @Test
    @Timeout(30)
    void overflowBeyondBufferSizeIsTypedNonRetryableTerminal() {
        // 6 元素长流 + demand==0（慢客户端）：越窗缓冲上界 = bufferSize(2)。
        fake.queueStream(StreamScenario.success(
                streamChunkJson("e1"), streamChunkJson("e2"), streamChunkJson("e3"),
                streamChunkJson("e4"), streamChunkJson("e5"), streamChunkJson("e6")));

        ManualDemandSubscriber sub = new ManualDemandSubscriber();
        adapter.callStream(request("gw-test", "gw-model-1", true), null).subscribe(sub);
        sub.awaitTerminal(15000);

        // 绿形态断言①：溢出 = 类型化错误终态（fail-loud，非静默丢弃/非静默堆积）。
        assertNotNull(sub.error,
                "backlog beyond bufferSize must fail loud with a typed overflow error; "
                        + "delivered=" + sub.texts + " completed=" + sub.completed);
        assertTrue(sub.error instanceof StreamingBufferOverflowException,
                "overflow must surface as StreamingBufferOverflowException, got: " + sub.error);
        assertFalse(sub.completed, "overflow must not masquerade as a successful completion");
        assertEquals(0, sub.nullElements, "no null element may be delivered");
    }

    @Test
    @Timeout(30)
    void overflowIsNonRetryableNoResubscribeNoRecordFailure() {
        fake.queueStream(StreamScenario.success(
                streamChunkJson("e1"), streamChunkJson("e2"), streamChunkJson("e3"),
                streamChunkJson("e4")));

        ManualDemandSubscriber sub = new ManualDemandSubscriber();
        adapter.callStream(request("gw-test", "gw-model-1", true), null).subscribe(sub);
        sub.awaitTerminal(15000);
        assertNotNull(sub.error, "overflow must produce the typed error terminal");

        // 绿形态断言②：溢出为不可重试终态——零重订阅、零 recordFailure、零预算消耗。
        assertEquals(1, fake.streamCallCount(),
                "overflow must not trigger a resubscribe (single upstream call)");
        assertEquals(0, metrics.resubscribes.get(), "zero onResubscribe on overflow");
        assertEquals(0, metrics.requestFailures.get(), "zero onRequestFailure on overflow (non-retryable terminal)");
    }
}
