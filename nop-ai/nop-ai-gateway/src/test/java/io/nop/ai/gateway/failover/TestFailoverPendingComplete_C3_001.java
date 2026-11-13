package io.nop.ai.gateway.failover;

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

import static io.nop.ai.gateway.failover.FailoverTestSupport.ManualDemandSubscriber;
import static io.nop.ai.gateway.failover.FailoverTestSupport.StreamScenario;
import static io.nop.ai.gateway.failover.FailoverTestSupport.request;
import static io.nop.ai.gateway.failover.FailoverTestSupport.streamChunkJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2509 R2-06 成员 3.3-C3-001（C10-002 面）回归钉：终态冲刷不得受 demand 约束截断。
 *
 * <p>红形态（修复前）：demand 耗尽时上游 onComplete → 缓冲剩余元素（尾部 chunk，含
 * finish_reason/usage 语义位）被静默丢弃后仍发 onComplete（半个成功）。
 * 绿形态（裁定④ pendingComplete 先例对齐）：终态挂起，下游 request() 冲空缓冲后
 * 补发 onComplete，元素 exactly-once 全量交付。
 */
class TestFailoverPendingComplete_C3_001 {

    private FailoverTestSupport.FakeHttpClient fake;
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
        adapter = new ChatServiceFailoverAdapter();
        adapter.setDelegate(impl);
        // bufferTimeMs = 0：首个元素即越窗（窗口期不转发语义退化为透传），元素全部落入
        // 越窗缓冲（demand==0 路径）——终态冲刷语义的确定性触发形态。
        adapter.setBufferSize(10);
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
    void pendingCompleteTailChunksDeliveredBeforeOnComplete() {
        fake.queueStream(StreamScenario.success(
                streamChunkJson("e1"), streamChunkJson("e2"), streamChunkJson("e3")));

        ManualDemandSubscriber sub = new ManualDemandSubscriber();
        adapter.callStream(request("gw-test", "gw-model-1", true), null).subscribe(sub);

        // 不授信等待上游终态处理完成（有界观察窗）：demand==0 时全部元素只能挂在缓冲。
        sub.awaitTerminal(500);

        // 绿形态断言①：缓冲非空时终态必须挂起——onComplete 不得在 request 之前到达
        //（红形态：终态已到且元素全丢）。
        assertFalse(sub.isTerminated(),
                "onComplete must be suspended while the buffer is non-empty (pendingComplete), but arrived early; "
                        + "delivered=" + sub.texts);
        assertFalse(sub.completed, "no onComplete before downstream request");

        // 绿形态断言②：request(3) 冲空缓冲 → 元素 exactly-once 全量交付，onComplete 最后。
        sub.request(3);
        sub.awaitTerminal(15000);
        assertNull(sub.error, "unexpected stream error: " + sub.error);
        assertTrue(sub.completed, "stream must complete after the buffer is drained");
        assertEquals(0, sub.nullElements, "no null element may be delivered");
        assertEquals(java.util.List.of("e1", "e2", "e3"), sub.texts,
                "tail chunks must be delivered exactly-once before onComplete (no truncation)");
    }

    @Test
    @Timeout(30)
    void inWindowCompleteSecondaryFaceAlsoDeliversAll() {
        // 次级面（裁定④）：窗口内完成（passed=false）——flushAll 不受 passed 门控。
        adapter.setBufferTimeMs(60000); // 窗口未满：元素全部缓冲在窗口内
        fake.queueStream(StreamScenario.success(
                streamChunkJson("w1"), streamChunkJson("w2")));

        ManualDemandSubscriber sub = new ManualDemandSubscriber();
        adapter.callStream(request("gw-test", "gw-model-1", true), null).subscribe(sub);
        sub.awaitTerminal(500);
        assertFalse(sub.isTerminated(), "in-window complete must also suspend the terminal (pendingComplete)");

        sub.request(5);
        sub.awaitTerminal(15000);
        assertNull(sub.error);
        assertTrue(sub.completed);
        assertEquals(java.util.List.of("w1", "w2"), sub.texts,
                "in-window buffered elements must all be delivered before onComplete");
    }
}
