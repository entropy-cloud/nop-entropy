package io.nop.sys.dao.message;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MA-06 sys 侧（C1-003，plan 2525 裁定⑦）：ensureNonBroadcastProcessor/ensureBroadcastProcessor
 * 懒初始化检查-然后-行动无同步且字段非 volatile——timer 线程 × batch XLang 线程（claimNonBroadcastEvents
 * 经 AOP 进入）并发首访构造双实例 + JMM 可见性窗。修复 = volatile + DCL（范本 DynCodeGen.getTenantCodeCache）。
 *
 * <p>红⑦承载形态 = 结构钉 + 并发冒烟：双实例构造的观测点（构造计数）在现类结构中不存在
 * （ensure 内联 new，无注入点）——沿 plan 2524 M2/2523 C6-017 结构钉方法论先例；并发冒烟钉
 * 保证多线程首访后单实例存活且功能可用（双实例的反面观测）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestSysMessageServiceLazyInit_MA06 extends JunitBaseTestCase {
    @Inject
    IDaoProvider daoProvider;

    private SysDaoMessageService service;

    @BeforeEach
    public void setUp() {
        service = new SysDaoMessageService();
        service.setDaoProvider(daoProvider);
        service.setAssignedPartitions(IntRangeSet.parse("0,32767"));
        service.setFetchSize(20);
        service.setMinProcessDelay(1);
        service.setLeaseTimeout(1000);
    }

    /**
     * 结构钉：两字段 volatile + ensure 方法 DCL（synchronized 二次检查）。
     */
    @Test
    public void testLazyInitUsesVolatileAndDcl() throws Exception {
        Path source = Path.of("src/main/java/io/nop/sys/dao/message/SysDaoMessageService.java");
        String code = Files.readString(source);

        assertTrue(code.contains("private volatile BroadcastEventProcessor broadcastProcessor")
                        && code.contains("private volatile NonBroadcastEventProcessor nonBroadcastProcessor"),
                "C1-003: lazy processor fields must be volatile — without it the timer/XLang threads"
                        + " may each construct their own instance (double instance) or read a stale null");

        assertTrue(code.contains("private void ensureNonBroadcastProcessor() {\n"
                        + "        NonBroadcastEventProcessor processor = nonBroadcastProcessor;"),
                "C1-003: ensureNonBroadcastProcessor must use the check-volatile-read-synchronized-recheck DCL form");

        int ensureCount = code.split("private void ensureBroadcastProcessor\\(\\)", -1).length - 1;
        assertEquals(1, ensureCount);
        String ensureBroadcast = code.substring(code.indexOf("private void ensureBroadcastProcessor()"));
        assertTrue(ensureBroadcast.contains("synchronized (this)"),
                "C1-003: ensureBroadcastProcessor must guard construction with synchronized(this) recheck");
    }

    /**
     * 并发冒烟钉：16 线程并发触发 ensure 路径（processNonBroadcastEvent / claimNonBroadcastEvents），
     * 全部成功且此后轮询功能正常（单实例存活的功能面观测）。
     */
    @Test
    public void testConcurrentFirstAccessKeepsProcessorFunctional() throws Exception {
        int threads = 16;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        Thread[] workers = new Thread[threads];

        for (int i = 0; i < threads; i++) {
            final boolean claimer = i % 2 == 0;
            workers[i] = new Thread(() -> {
                ready.countDown();
                try {
                    start.await();
                    if (claimer)
                        service.claimNonBroadcastEvents(java.util.Collections.emptyList());
                    else
                        service.processNonBroadcastEvent();
                } catch (Exception e) {
                    throw io.nop.api.core.exceptions.NopException.adapt(e);
                } finally {
                    done.countDown();
                }
            }, "lazy-init-" + i);
            workers[i].start();
        }

        assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS));
        start.countDown();
        assertTrue(done.await(30, java.util.concurrent.TimeUnit.SECONDS),
                "all concurrent first-access threads must succeed");

        // 单实例存活的功能面观测：此后轮询投递正常
        AtomicInteger received = new AtomicInteger();
        service.subscribe("ma06-topic", new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
                received.incrementAndGet();
                return null;
            }
        }, null);
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(Map.of("id", "evt-ma06"));
        service.send("ma06-topic", request, null);
        service.processNonBroadcastEvent();
        assertEquals(1, received.get(),
                "the surviving processor instance must be fully functional after the concurrent first access");
    }
}
