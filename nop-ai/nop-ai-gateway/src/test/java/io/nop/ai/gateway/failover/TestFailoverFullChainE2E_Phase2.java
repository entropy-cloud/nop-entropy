package io.nop.ai.gateway.failover;

import io.nop.ai.core.reliability.CircuitState;
import io.nop.ai.core.service.ChatServiceImpl;
import io.nop.ai.core.service.DefaultChatLogger;
import io.nop.ai.core.service.LlmConfigHelper;
import io.nop.api.core.config.AppConfig;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static io.nop.ai.gateway.failover.FailoverTestSupport.ManualDemandSubscriber;
import static io.nop.ai.gateway.failover.FailoverTestSupport.StreamScenario;
import static io.nop.ai.gateway.failover.FailoverTestSupport.request;
import static io.nop.ai.gateway.failover.FailoverTestSupport.streamChunkJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2509 R2-06 端到端链路①（rule 22）：3.3 流式 failover 全链。
 *
 * <p>入口 {@code callStream} → attempt-1 缓冲窗口内失败（429）→ 熔断记账 + 被动路径重选 →
 * 重订阅 attempt-2（新账号下沉）→ 越窗 → ManualDemand 受限消费（分段授信）→ 终态交付
 * exactly-once 零泄漏 → 取消路径 teardown 配对（并发计数归零 + 上游订阅取消）。
 */
class TestFailoverFullChainE2E_Phase2 {

    private FailoverTestSupport.FakeHttpClient fake;
    private io.nop.ai.core.reliability.ThresholdBreaker breaker;
    private io.nop.ai.gateway.routing.ConcurrencyRegistry registry;
    private ChatServiceFailoverAdapter adapter;

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
        breaker = new io.nop.ai.core.reliability.ThresholdBreaker(3, 0);
        registry = new io.nop.ai.gateway.routing.ConcurrencyRegistry();
        adapter = new ChatServiceFailoverAdapter();
        adapter.setDelegate(impl);
        adapter.setBreaker(breaker);
        adapter.setRegistry(registry);
        adapter.setBufferSize(16); // 越窗上界宽裕（默认 bufferSize=10 亦可，显式声明避免耦合）
        AppConfig.getConfigProvider().assignConfigValue("nop.ai.llm.gw-test.api-key", "key-gw-main");
        AppConfig.getConfigProvider().assignConfigValue("nop.ai.llm.gw-cache.api-key", "key-gw-main");
    }

    @AfterEach
    void tearDown() {
        AppConfig.getConfigProvider().assignConfigValue("nop.ai.llm.gw-test.api-key", null);
        AppConfig.getConfigProvider().assignConfigValue("nop.ai.llm.gw-cache.api-key", null);
        LlmConfigHelper.reset();
    }

    @Test
    @Timeout(60)
    void fullChainFailoverResubscribeWindowDemandTerminalexactlyOnce() throws Exception {
        // attempt-1：2 个前缀元素后窗口内失败（429 → RATE_LIMITED 可切换，错误落在窗口期 1000ms 内）；
        // attempt-2：12 个元素慢速长流（150ms 间隔 → T=1000ms 越窗）+ 正常完成。
        fake.queueStream(StreamScenario.success(
                        streamChunkJson("a1"), streamChunkJson("a2"))
                .error(FailoverTestSupport.streamError(429, FailoverTestSupport.quotaBody())));
        StreamScenario second = StreamScenario.success(
                streamChunkJson("b1"), streamChunkJson("b2"), streamChunkJson("b3"), streamChunkJson("b4"),
                streamChunkJson("b5"), streamChunkJson("b6"), streamChunkJson("b7"), streamChunkJson("b8"),
                streamChunkJson("b9"), streamChunkJson("b10"), streamChunkJson("b11"), streamChunkJson("b12"));
        second.interEventDelayMs = 150;
        fake.queueStream(second);

        // bufferSize=16：越窗缓冲上界宽裕（本钉验证链路语义而非溢出面）。
        adapter.setBufferSize(16);

        ManualDemandSubscriber sub = new ManualDemandSubscriber();
        adapter.callStream(request("gw-test", "gw-model-1", true), null).subscribe(sub);

        // 受限消费：仅授信 5（< 元素总数）——越窗后超出 demand 的元素滞留缓冲，终态挂起。
        sub.request(5);

        // 等 attempt-2 长流自然完成（12 × 150ms + 失败/重订阅开销，有界观察窗）。
        Thread.sleep(2500);
        // 终态挂起断言：demand 耗尽 + 缓冲非空 → onComplete 不得提前到达（C3-001 语义在链路上）。
        assertTrue(!sub.isTerminated(),
                "onComplete must stay suspended while buffered elements exceed demand; delivered=" + sub.texts);

        // 补足授信 → 冲空缓冲 → 补发终态。
        sub.request(7);
        sub.awaitTerminal(20000);
        assertNull(sub.error, "unexpected error: " + sub.error);
        assertTrue(sub.completed, "stream must complete normally after failover");
        assertEquals(0, sub.nullElements, "no null element may be delivered");

        // 出口点 exactly-once 零泄漏：attempt-1 前缀零泄漏 + attempt-2 全量按序交付一次。
        assertEquals(List.of("b1", "b2", "b3", "b4", "b5", "b6", "b7", "b8", "b9", "b10", "b11", "b12"),
                sub.texts, "final element sequence must be exactly-once, ordered, zero leakage");

        // 全链终态：单次重订阅、并发计数归零、末次 attempt recordSuccess（CLOSED）。
        assertEquals(2, fake.streamCallCount(), "one in-window failure → exactly one resubscribe");
        assertEquals(0, registry.currentCount("gw-test", null), "attempt-1 concurrency count must be released");
        assertEquals(0, registry.currentCount("gw-test", "key-gw-a"),
                "attempt-2 concurrency count must be released");
        assertEquals(CircuitState.CLOSED, breaker.getState("gw-test:gw-model-1"),
                "final success must recordSuccess on the last attempt candidate");
    }



    @Test
    @Timeout(60)
    void cancelLegTeardownPairsUpstreamCancelAndConcurrencyRelease() throws Exception {
        // 取消路径（链路①的 teardown 配对腿）：窗口内流 + 输出侧取消 → 内部订阅取消 +
        // attempt token 取消 + 并发计数释放。
        StreamScenario longStream = StreamScenario.success(
                streamChunkJson("c1"), streamChunkJson("c2"), streamChunkJson("c3"), streamChunkJson("c4"),
                streamChunkJson("c5"), streamChunkJson("c6"));
        longStream.interEventDelayMs = 150; // T=1000ms 越窗后元素进入透传段（demand 门控可交付）
        fake.queueStream(longStream);

        ManualDemandSubscriber sub = new ManualDemandSubscriber();
        var flow = adapter.callStream(request("gw-test", "gw-model-1", true), null);
        flow.subscribe(sub);
        sub.request(1);

        // 等首个元素送达（有界观察窗）→ 输出侧取消。
        long deadline = System.currentTimeMillis() + 10000;
        while (sub.texts.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertTrue(!sub.texts.isEmpty(), "at least one element must be delivered before cancel");
        sub.subscription().cancel();

        // teardown 配对：上游订阅被取消 + 并发计数归零（无幽灵计数）。
        deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline && !fake.streamSubscriptions.isEmpty()
                && !fake.streamSubscriptions.get(0).isCancelled()) {
            Thread.sleep(20);
        }
        assertTrue(fake.streamSubscriptions.get(0).isCancelled(),
                "output cancel must propagate to the internal upstream subscription");
        assertEquals(0, registry.currentCount("gw-test", null),
                "cancel must release the attempt concurrency count (no ghost)");
    }
}
