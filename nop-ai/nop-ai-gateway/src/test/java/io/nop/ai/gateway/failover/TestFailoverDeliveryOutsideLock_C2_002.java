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

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.nop.ai.gateway.failover.FailoverTestSupport.ManualDemandSubscriber;
import static io.nop.ai.gateway.failover.FailoverTestSupport.StreamScenario;
import static io.nop.ai.gateway.failover.FailoverTestSupport.request;
import static io.nop.ai.gateway.failover.FailoverTestSupport.streamChunkJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2509 R2-06 成员 3.3-C2-002（R03 族，C10-004 面）回归钉：监视器内下游投递外移
 * （裁定⑥ drain-loop）。
 *
 * <p>红形态（修复前）：慢下游 onNext 阻塞桩在监视器内执行——{@code request()} 与投递互相
 * 卡位（授信线程被投递锁阻塞，计时断言红）。绿形态：锁内仅状态机变更，投递经 drain-loop
 * 出锁串行执行——request 不被下游 onNext 阻塞，且信号序不变式保持（元素序 = 缓冲序、
 * 终态最后）。
 */
class TestFailoverDeliveryOutsideLock_C2_002 {

    @BeforeAll
    static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    @Timeout(60)
    void requestIsNotBlockedBySlowDownstreamOnNext() throws Exception {
        FailoverTestSupport.FakeHttpClient fake = new FailoverTestSupport.FakeHttpClient();
        ChatServiceImpl impl = new ChatServiceImpl();
        impl.setHttpClient(fake);
        impl.setChatLogger(new DefaultChatLogger());
        ChatServiceFailoverAdapter adapter = new ChatServiceFailoverAdapter();
        adapter.setDelegate(impl);
        adapter.setBufferSize(8);
        adapter.setBufferTimeMs(0); // 首元素即越窗（透传形态）
        adapter.setRegistry(new io.nop.ai.gateway.routing.ConcurrencyRegistry());
        adapter.setBreaker(new io.nop.ai.core.reliability.ThresholdBreaker(3, 0));

        fake.queueStream(StreamScenario.success(
                streamChunkJson("e1"), streamChunkJson("e2"), streamChunkJson("e3")));

        CountDownLatch onNextEntered = new CountDownLatch(1);
        CountDownLatch releaseOnNext = new CountDownLatch(1);
        ManualDemandSubscriber sub = new ManualDemandSubscriber();
        sub.onNextHook = () -> {
            // 慢下游桩：首个 onNext 阻塞（模拟下游处理耗时持锁面）。
            if (onNextEntered.getCount() > 0) {
                onNextEntered.countDown();
                try {
                    releaseOnNext.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        adapter.callStream(request("gw-test", "gw-model-1", true), null).subscribe(sub);

        // 授信 1 → 首元素投递 → onNext 阻塞桩生效。
        sub.request(1);
        assertTrue(onNextEntered.await(20, TimeUnit.SECONDS), "first element must be delivered");

        // 红形态断言：request() 不得被下游 onNext 卡位（投递出锁后应立即返回）。
        Thread grantor = new Thread(() -> sub.request(2), "c2-002-grantor");
        grantor.start();
        grantor.join(3000);
        assertFalse(grantor.isAlive(),
                "request() must not be blocked by a slow downstream onNext (delivery must run outside the monitor)");

        // 信号序不变式：放行下游 → 剩余元素按缓冲序交付、终态最后。
        releaseOnNext.countDown();
        sub.awaitTerminal(20000);
        assertNull(sub.error);
        assertTrue(sub.completed, "stream must complete after the downstream is released");
        assertEquals(List.of("e1", "e2", "e3"), sub.texts, "element order must be preserved (no reordering)");
        assertEquals(0, sub.nullElements, "no null element may be delivered");
    }
}
