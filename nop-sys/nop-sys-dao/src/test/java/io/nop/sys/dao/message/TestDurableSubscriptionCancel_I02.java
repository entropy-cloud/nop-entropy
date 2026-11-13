package io.nop.sys.dao.message;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * I-02（C1-002，plan 2525 裁定③）：DurableSubscription.cancel 的「get→remove(state)→判空摘键」
 * 复合序列与 subscribe 的 computeIfAbsent+add 交错会孤儿化订阅者（T2 新订阅被 T1 摘键整体带走，
 * 轮询集合残留 topic，事件被 warn 后置 PROCESSED 静默丢弃）。修复 = compute 单步（与 subscribe
 * 同 CHM bin 锁互斥），getConsumers 条件移除位于 compute 内摘键成功分支。
 *
 * <p>红③承载形态 = 结构钉 + 功能钉：交错窗位于 T1 的 remove 与判空两条语句之间（纳秒级、
 * 无内部挂钩点），行为级确定性红不可构造——沿 plan 2524 M2 裁定的结构钉方法论先例，
 * 并发交错正确性由 CHM compute 的平台互斥保证（R17 族对齐矩阵见设计文档 §3.3）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestDurableSubscriptionCancel_I02 extends JunitBaseTestCase {
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

    private static ApiRequest<Map<String, Object>> request(String id) {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(Map.of("id", id));
        return request;
    }

    /**
     * 结构钉：cancel 判空+摘键必须 compute 单步，且 getConsumers 条件移除位于 compute lambda 内。
     */
    @Test
    public void testCancelUsesSingleStepCompute() throws Exception {
        Path source = Path.of("src/main/java/io/nop/sys/dao/message/SysDaoMessageService.java");
        String code = Files.readString(source);

        int cancelIdx = code.indexOf("public void cancel()");
        assertTrue(cancelIdx > 0, "DurableSubscription.cancel not found");
        int cancelEnd = code.indexOf("isSuspended()", cancelIdx);
        assertTrue(cancelEnd > cancelIdx, "cancel method end not found");
        String cancelBody = code.substring(cancelIdx, cancelEnd);

        assertTrue(cancelBody.contains("durableSubscriptions.compute(state.topic"),
                "I-02: cancel must remove-and-maybe-unkey in a single CHM compute step"
                        + " (the old get/remove/check sequence interleaves with subscribe's"
                        + " computeIfAbsent+add and orphans the new subscriber)");
        int computeIdx = cancelBody.indexOf("durableSubscriptions.compute(state.topic");
        int computeEnd = cancelBody.indexOf("});", computeIdx);
        String lambda = cancelBody.substring(computeIdx, computeEnd);
        assertTrue(lambda.contains("getConsumers().remove"),
                "I-02: the polling-set conditional removal must execute inside the compute lambda"
                        + " (after compute returns it re-opens the interleave window with re-subscribe)");
    }

    /**
     * 功能钉：cancel→resubscribe 后新订阅者必须能收到事件（摘键语义正确，轮询集合健康）。
     */
    @Test
    public void testResubscribeAfterCancelReceivesEvents() {
        AtomicInteger received = new AtomicInteger();
        IMessageSubscription first = service.subscribe("i02-topic", countingConsumer(received), null);
        first.cancel();

        IMessageSubscription second = service.subscribe("i02-topic", countingConsumer(received), null);

        service.send("i02-topic", request("evt-1"), null);
        service.processNonBroadcastEvent();

        assertEquals(1, received.get(),
                "the re-subscribed subscriber must receive events — no orphaned subscription");
        assertFalse(second.isCancelled());
    }

    /**
     * 并发交错功能钉：cancel×subscribe 高频交错无异常；风暴后事件投递恰好一次
     * （非广播队列语义投递给第一个存活订阅者——其订阅永不取消，投递必达；
     * 孤儿化的「事件被丢弃」形态即该断言的反面）。
     */
    @Test
    public void testConcurrentCancelSubscribeKeepsSubscriptionAlive() throws Exception {
        int rounds = 200;
        CountDownLatch done = new CountDownLatch(3);
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        // 永不取消的锚订阅（先于风暴注册，恒为投递首位）：其计数是「存活订阅可达」的观测点
        AtomicInteger anchorReceived = new AtomicInteger();

        IMessageConsumer anchor = new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
                anchorReceived.incrementAndGet();
                return null;
            }
        };
        service.subscribe("i02-stress", anchor, null);

        Runnable cancellerLoop = () -> {
            try {
                for (int i = 0; i < rounds; i++) {
                    IMessageSubscription s = service.subscribe("i02-stress", recording(), null);
                    s.cancel();
                }
            } catch (Throwable e) {
                errors.add(e);
            } finally {
                done.countDown();
            }
        };
        Runnable subscriberLoop = () -> {
            try {
                for (int i = 0; i < rounds; i++) {
                    service.subscribe("i02-stress", recording(), null);
                }
            } catch (Throwable e) {
                errors.add(e);
            } finally {
                done.countDown();
            }
        };

        Thread t1 = new Thread(cancellerLoop, "i02-canceller-1");
        Thread t2 = new Thread(cancellerLoop, "i02-canceller-2");
        Thread t3 = new Thread(subscriberLoop, "i02-subscriber");
        t1.start();
        t2.start();
        t3.start();
        assertTrue(done.await(30, java.util.concurrent.TimeUnit.SECONDS), "stress threads must finish");

        assertTrue(errors.isEmpty(), "no exceptions during concurrent subscribe/cancel: " + errors);

        // 收尾：锚订阅（存活）必须收到事件——孤儿化/键残留即投递丢失
        service.send("i02-stress", request("evt-final"), null);
        service.processNonBroadcastEvent();
        assertEquals(1, anchorReceived.get(),
                "the never-cancelled anchor subscription must receive the event after the storm");
    }

    private IMessageConsumer countingConsumer(AtomicInteger counter) {
        return new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
                counter.incrementAndGet();
                return null;
            }
        };
    }

    private IMessageConsumer recording() {
        return new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
                return null;
            }
        };
    }
}
