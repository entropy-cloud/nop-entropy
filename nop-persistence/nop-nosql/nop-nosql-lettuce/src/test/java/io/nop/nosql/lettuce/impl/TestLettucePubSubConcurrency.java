/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.nosql.lettuce.impl;

import io.nop.api.core.message.ConsumeFailure;
import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSubscribeOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PubSub 订阅/连接生命周期并发护栏钉（plan 2518 C10-022/C10-023/C10-025；红①②③载体）。
 * fake {@link PubSubLink} 注入——非 docker 可跑（勘误 9 口径）。
 *
 * <ul>
 * <li>红①（C6-004/C10-022）：N 线程 subscribe×cancel/suspend/resume 压力交错——缺陷态竞态红
 *     （孤儿订阅/addListener 泄漏/复活双投递）→ 监视器串行 + 属主校验 + 停机对账绿。</li>
 * <li>红②（C8-001/C10-023）：投递线程名断言 + 慢消费者不阻塞 IO 线程分发 + CompletionStage
 *     返回值观测 + 队列满拒绝类型化回调——缺陷态 IO 线程内联阻塞红 → 外移绿。</li>
 * <li>红③（C7-014/C10-025）：连接激活事件 → 幂等确认 SUBSCRIBE（恢复留痕）；确认失败计数；
 *     已 cancel 订阅不复活——缺陷态可观测缺失红 → 恢复留痕/失败告警绿。</li>
 * </ul>
 */
public class TestLettucePubSubConcurrency {

    private RecordingPubSubLink fakeLink;
    private LettucePubSubService service;

    @BeforeEach
    void setUp() {
        fakeLink = new RecordingPubSubLink();
        service = new LettucePubSubService(null) {
            @Override
            protected PubSubLink createLink() {
                return fakeLink;
            }
        };
        service.start();
    }

    @AfterEach
    void tearDown() {
        service.stop();
    }

    private IMessageSubscription subscribe(String topic, IMessageConsumer consumer) {
        return service.subscribe(topic, consumer, new MessageSubscribeOptions());
    }

    // ------------------------------------------------------------------
    // 红①：并发订阅管理压力（C10-022）
    // ------------------------------------------------------------------

    @Test
    public void testConcurrentSubscribeCancelSuspendResume_NoOrphanNoLeak() throws Exception {
        String topic = "stress-topic";
        int threads = 8;
        int loops = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        AtomicInteger deliveries = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.execute(() -> {
                try {
                    go.await();
                    for (int j = 0; j < loops; j++) {
                        IMessageSubscription sub = subscribe(topic, counting(deliveries));
                        sub.suspend();
                        sub.resume();
                        sub.cancel();
                        // cancelled 重入：cancel/suspend/resume 对已 cancel 订阅必须为显式 no-op
                        sub.cancel();
                        sub.suspend();
                        sub.resume();
                    }
                } catch (Throwable e) {
                    errors.add(e);
                }
            });
        }
        go.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "stress run must finish");
        assertEquals(List.of(), errors, "no exceptions under concurrent subscription management");

        // 无孤儿订阅：全部 cancel 后 fake 链接上的 UNSUBSCRIBE 次数 == SUBSCRIBE 次数（末次状态一致）
        int subscribed = fakeLink.subscribeCount(topic);
        int unsubscribed = fakeLink.unsubscribeCount(topic);
        assertEquals(subscribed, unsubscribed,
                "final unsubscribe count must match subscribe count (no orphan redis-side subscription)");
        // 无 addListener 泄漏：全部订阅注销后连接消息适配器数为 0
        assertEquals(0, fakeLink.messageListenerCount(),
                "connection message adapter must be removed when the last owner cancels");
        // 无复活双投递：注销后投递不得再触达消费者
        int before = deliveries.get();
        fakeLink.simulateMessage(topic, "post-cancel");
        Thread.sleep(200);
        assertEquals(before, deliveries.get(), "no delivery after all subscriptions cancelled");
    }

    private static IMessageConsumer counting(AtomicInteger counter) {
        return (topic, msg, ctx) -> {
            counter.incrementAndGet();
            return null;
        };
    }

    /**
     * 属主校验钉：一个 subscription 实例只管理自己 subscribe 时注册的 listener——
     * 同 topic 多订阅者，cancel 其一不得注销其他订阅者（锁内空判复查）。
     */
    @Test
    public void testOwnerScope_CancelOnlyOwnListener() throws Exception {
        String topic = "owner-topic";
        AtomicInteger count1 = new AtomicInteger();
        AtomicInteger count2 = new AtomicInteger();
        IMessageSubscription sub1 = subscribe(topic, counting(count1));
        IMessageSubscription sub2 = subscribe(topic, counting(count2));
        assertEquals(1, fakeLink.messageListenerCount(), "one shared connection adapter for the topic");

        sub1.cancel();
        assertEquals(1, fakeLink.messageListenerCount(),
                "cancelling one owner must not remove the shared adapter while another owner remains");
        fakeLink.simulateMessage(topic, "m");
        Thread.sleep(200);
        assertEquals(0, count1.get(), "cancelled owner must not receive");
        assertTrue(count2.get() >= 1, "remaining owner keeps receiving");

        sub2.cancel();
        assertEquals(0, fakeLink.messageListenerCount(), "last owner cancel removes the adapter");
    }

    // ------------------------------------------------------------------
    // 红②：投递外移与返回值契约（C10-023）
    // ------------------------------------------------------------------

    @Test
    public void testDeliveryRunsOnDedicatedConsumerThread() throws Exception {
        AtomicReference<String> threadName = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        subscribe("t1", (topic, msg, ctx) -> {
            threadName.set(Thread.currentThread().getName());
            latch.countDown();
            return null;
        });
        fakeLink.simulateMessage("t1", "hello");
        assertTrue(latch.await(5, TimeUnit.SECONDS), "delivery must arrive");
        assertTrue(threadName.get().startsWith("nop-lettuce-pubsub-consumer"),
                "consumer callback must run on the dedicated delivery executor thread, got: " + threadName.get());
    }

    @Test
    public void testSlowConsumerDoesNotBlockIoThreadDispatch() throws Exception {
        CountDownLatch block = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(1);
        List<String> seen = new CopyOnWriteArrayList<>();
        subscribe("slow", (topic, msg, ctx) -> {
            if (entered.getCount() > 0) {
                entered.countDown();
                try {
                    block.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            seen.add(topic + ":" + msg);
            return null;
        });
        fakeLink.simulateMessage("slow", "m1");
        assertTrue(entered.await(5, TimeUnit.SECONDS), "slow consumer entered");

        // IO 线程模拟：慢消费者占住投递线程时，后续分发调用不得被阻塞（缺陷态：内联执行会卡死此处）
        long start = System.currentTimeMillis();
        fakeLink.simulateMessage("slow", "m2");
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed < 1000, "dispatch must only enqueue (off-IO-thread delivery), took " + elapsed + "ms");

        block.countDown();
        // 投递线程 FIFO：慢消费者之后的任务不被丢弃（不再断链丢消息）
        long deadline = System.currentTimeMillis() + 5000;
        while (seen.size() < 2 && System.currentTimeMillis() < deadline)
            Thread.sleep(20);
        assertEquals(List.of("slow:m1", "slow:m2"), seen, "queued message must still be delivered (FIFO)");
    }

    @Test
    public void testCompletionStageReturnObservedWithoutBlocking() throws Exception {
        AtomicReference<String> threadName = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        subscribe("async-ret", (topic, msg, ctx) -> {
            threadName.set(Thread.currentThread().getName());
            latch.countDown();
            java.util.concurrent.CompletableFuture<String> future = new java.util.concurrent.CompletableFuture<>();
            // 不完成该 future：有界观测只告警不等待——投递线程必须已经返回
            return future;
        });
        fakeLink.simulateMessage("async-ret", "m");
        assertTrue(latch.await(5, TimeUnit.SECONDS), "delivery must arrive");
        // 投递线程未被阻塞：任务已结束（未等待 CompletionStage 完成——有界观测只告警不等待）
        assertTrue(threadName.get().startsWith("nop-lettuce-pubsub-consumer"));
    }

    @Test
    public void testQueueFullRejection_InvokesTypedConsumeFailure() throws Exception {
        service.deliveryQueueCapacity = 1;
        CountDownLatch block = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(1);
        List<ConsumeFailure> failures = new CopyOnWriteArrayList<>();
        AtomicReference<CountDownLatch> failureSeen = new AtomicReference<>(new CountDownLatch(1));
        IMessageConsumer failingConsumer = new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object msg, IMessageConsumeContext ctx) {
                if (entered.getCount() > 0) {
                    entered.countDown();
                    try {
                        block.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return null;
            }

            @Override
            public void onConsumeFailure(ConsumeFailure failure) {
                failures.add(failure);
                failureSeen.get().countDown();
            }
        };
        subscribe("reject-topic", failingConsumer);
        fakeLink.simulateMessage("reject-topic", "occupy-thread"); // occupy the single delivery thread
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        fakeLink.simulateMessage("reject-topic", "fill-queue"); // 队列 1 槽被占
        fakeLink.simulateMessage("reject-topic", "queue-full");  // 队列满 → 拒绝
        assertTrue(failureSeen.get().await(5, TimeUnit.SECONDS),
                "queue-full rejection must invoke the typed onConsumeFailure callback (never silent)");
        assertEquals("reject-topic", failures.get(0).getTopic());
        assertTrue(String.valueOf(failures.get(0).getThrowable().getMessage()).contains("queue is full"),
                "rejection cause must be explicit");
        block.countDown();
    }

    // ------------------------------------------------------------------
    // 红③：重连恢复可观测（C10-025，勘误 10 收窄口径）
    // ------------------------------------------------------------------

    @Test
    public void testReconnectActivation_TriggersIdempotentConfirmSubscribe() throws Exception {
        String topic = "reconnect-topic";
        subscribe(topic, counting(new AtomicInteger()));
        int initialSubscribes = fakeLink.subscribeCount(topic);
        assertEquals(1, initialSubscribes);

        // 模拟断线重连后 lettuce activated() 自主 resubscribe——服务侧对注册表快照发幂等确认 SUBSCRIBE
        fakeLink.fireActivated();
        long deadline = System.currentTimeMillis() + 5000;
        while (fakeLink.subscribeCount(topic) < 2 && System.currentTimeMillis() < deadline)
            Thread.sleep(20);
        assertTrue(fakeLink.subscribeCount(topic) >= 2,
                "reconnect activation must leave a recovery trace (idempotent confirm SUBSCRIBE)");
        assertEquals(0, service.getResubscribeFailureCount(), "confirm success must not count failures");
    }

    @Test
    public void testReconnectConfirm_DoesNotResurrectCancelledSubscription() throws Exception {
        String topic = "cancel-no-resurrect";
        IMessageSubscription sub = subscribe(topic, counting(new AtomicInteger()));
        sub.cancel();
        int afterCancel = fakeLink.subscribeCount(topic);

        fakeLink.fireActivated();
        Thread.sleep(300);
        assertEquals(afterCancel, fakeLink.subscribeCount(topic),
                "confirm SUBSCRIBE must only target the registry snapshot: cancelled topics must not be resurrected");
    }

    @Test
    public void testReconnectConfirmFailure_CountedAndWarned() throws Exception {
        String topic = "confirm-fail-topic";
        subscribe(topic, counting(new AtomicInteger()));
        fakeLink.failSubscribeOn = true;
        fakeLink.fireActivated();
        long deadline = System.currentTimeMillis() + 5000;
        while (service.getResubscribeFailureCount() < 1 && System.currentTimeMillis() < deadline)
            Thread.sleep(20);
        assertTrue(service.getResubscribeFailureCount() >= 1,
                "confirm SUBSCRIBE failure must increment the observable failure counter (resubscribe-failed)");
        fakeLink.failSubscribeOn = false;
    }

    // ------------------------------------------------------------------
    // 停机对账（裁定① doStop，与裁定③共享注册表=SSOT 不变式）
    // ------------------------------------------------------------------

    @Test
    public void testDoStopReconcilesRegistryAndDrainsUndelivered() throws Exception {
        CountDownLatch block = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(1);
        List<ConsumeFailure> failures = new CopyOnWriteArrayList<>();
        IMessageConsumer consumer = new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object msg, IMessageConsumeContext ctx) {
                if (entered.getCount() > 0) {
                    entered.countDown();
                    try {
                        block.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return null;
            }

            @Override
            public void onConsumeFailure(ConsumeFailure failure) {
                failures.add(failure);
            }
        };
        subscribe("stop-topic", consumer);
        fakeLink.simulateMessage("stop-topic", "in-flight-1");
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        fakeLink.simulateMessage("stop-topic", "queued-2"); // 排在阻塞任务之后

        service.stop();

        assertTrue(fakeLink.unsubscribedTopics.contains("stop-topic"),
                "stop must reconcile: every registered topic unsubscribed from redis");
        assertEquals(0, fakeLink.messageListenerCount(), "stop must remove all message adapters");
        assertTrue(fakeLink.closed, "stop must close the connection link");
        block.countDown();
        long deadline = System.currentTimeMillis() + 5000;
        while (failures.isEmpty() && System.currentTimeMillis() < deadline)
            Thread.sleep(20);
        assertTrue(failures.size() >= 1,
                "undelivered queued messages must be drained via typed onConsumeFailure (never silently dropped)");
    }

    // ------------------------------------------------------------------
    // rule 22 端到端链二：PubSub 生命周期（裁定①②③合流场景）
    // ------------------------------------------------------------------

    @Test
    public void testPubSubLifecycleEndToEnd() throws Exception {
        // subscribe → 慢消费者并发（外移投递不断链）→ 断线重连恢复留痕与幂等确认 →
        // cancel 属主校验 → doStop 对账（含停机排空留痕）
        CountDownLatch block = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();
        AtomicInteger deliveries = new AtomicInteger();
        IMessageConsumer slow = (topic, msg, ctx) -> {
            threadName.set(Thread.currentThread().getName());
            deliveries.incrementAndGet();
            if (entered.getCount() > 0) {
                entered.countDown();
                try {
                    block.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return null;
        };
        IMessageSubscription slowSub = subscribe("life-slow", slow);
        fakeLink.simulateMessage("life-slow", "m1");
        assertTrue(entered.await(5, TimeUnit.SECONDS), "slow consumer entered");

        // 外移投递：IO 线程分发不阻塞，投递发生在专用消费线程
        long start = System.currentTimeMillis();
        fakeLink.simulateMessage("life-slow", "m2");
        assertTrue(System.currentTimeMillis() - start < 1000, "dispatch must only enqueue");
        assertTrue(threadName.get().startsWith("nop-lettuce-pubsub-consumer"),
                "delivery must be off the IO thread (裁定②)");

        // 断线重连：lettuce 自主 resubscribe 之上，服务侧幂等确认 SUBSCRIBE 留痕（裁定③）
        fakeLink.fireActivated();
        long deadline = System.currentTimeMillis() + 5000;
        while (fakeLink.subscribeCount("life-slow") < 2 && System.currentTimeMillis() < deadline)
            Thread.sleep(20);
        assertTrue(fakeLink.subscribeCount("life-slow") >= 2, "recovery trace must be left");

        // cancel 属主校验（裁定①）：慢订阅注销后其余订阅不受影响
        CountDownLatch other = new CountDownLatch(1);
        IMessageSubscription otherSub = subscribe("life-other", (topic, msg, ctx) -> {
            other.countDown();
            return null;
        });
        block.countDown(); // 先释放慢消费者（单投递线程 FIFO：后续任务不再被阻塞）
        slowSub.cancel();
        fakeLink.simulateMessage("life-other", "ping");
        assertTrue(other.await(5, TimeUnit.SECONDS), "other subscription keeps receiving after owner cancel");

        // doStop 对账 + 停机排空（裁定①）
        service.stop();
        assertTrue(fakeLink.unsubscribedTopics.contains("life-slow")
                && fakeLink.unsubscribedTopics.contains("life-other"),
                "stop must reconcile every registered topic");
        assertEquals(0, fakeLink.messageListenerCount());
        assertTrue(fakeLink.closed);
        otherSub.cancel(); // 幂等重入 no-op
    }

    // ------------------------------------------------------------------

    /** 记录型 fake 链接（非 docker 可跑；注入 subscribe 失败模拟确认失败）。 */
    static final class RecordingPubSubLink implements PubSubLink {
        final Map<String, Integer> subscribeCounts = new ConcurrentHashMap<>();
        final List<String> unsubscribedTopics = new CopyOnWriteArrayList<>();
        final List<io.lettuce.core.pubsub.RedisPubSubListener<String, Object>> messageListeners =
                new CopyOnWriteArrayList<>();
        final List<Runnable> stateListeners = new CopyOnWriteArrayList<>();
        volatile boolean failSubscribeOn;
        volatile boolean closed;

        @Override
        public void subscribe(String topic) {
            if (failSubscribeOn)
                throw new IllegalStateException("simulated redis subscribe failure");
            subscribeCounts.merge(topic, 1, Integer::sum);
        }

        @Override
        public void unsubscribe(String topic) {
            unsubscribedTopics.add(topic);
        }

        @Override
        public void addMessageListener(io.lettuce.core.pubsub.RedisPubSubListener<String, Object> entry) {
            messageListeners.add(entry);
        }

        @Override
        public void removeMessageListener(io.lettuce.core.pubsub.RedisPubSubListener<String, Object> entry) {
            messageListeners.remove(entry);
        }

        @Override
        public void addStateListener(Runnable onActivated) {
            stateListeners.add(onActivated);
        }

        @Override
        public void close() {
            closed = true;
        }

        int subscribeCount(String topic) {
            return subscribeCounts.getOrDefault(topic, 0);
        }

        int unsubscribeCount(String topic) {
            int count = 0;
            for (String t : unsubscribedTopics) {
                if (t.equals(topic))
                    count++;
            }
            return count;
        }

        int messageListenerCount() {
            return messageListeners.size();
        }

        /** 模拟 redis 消息到达：分发到已注册 adapter。 */
        void simulateMessage(String topic, Object message) {
            for (io.lettuce.core.pubsub.RedisPubSubListener<String, Object> entry : messageListeners) {
                entry.message(topic, message);
            }
        }

        /** 模拟 lettuce 连接激活事件（IO 线程语义：只投递事件）。 */
        void fireActivated() {
            for (Runnable listener : stateListeners) {
                listener.run();
            }
        }
    }
}
