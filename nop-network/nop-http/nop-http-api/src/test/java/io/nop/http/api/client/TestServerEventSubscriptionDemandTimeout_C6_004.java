package io.nop.http.api.client;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 4.3-C6-004 面①（plan 2505 Phase 1 PA 裁定面）：共享基类
 * {@code AbstractServerEventSubscription.waitForDemand} 的 demand 等待必须有界——
 * 停滞订阅者（有界 demand 不 request 也不 cancel）不得无限期挂起承载线程，
 * 预算耗尽必须以显式错误终态（cleanup + onError）收口（R21 纪律：异常终态非静默）。
 *
 * <p>红形态⑤：demand 挂起 = 承载线程滞留 {@code waitForDemand} 超过预算窗。
 * 修复前该用例在观察窗内收不到任何终态信号（RED）；修复后 onError 在预算内到达（GREEN）。
 */
public class TestServerEventSubscriptionDemandTimeout_C6_004 {

    /** demand 等待预算观察窗：修复后预算（测试收紧值）内终态必须到达。 */
    private static final long OBSERVE_WINDOW_MILLIS = 5000;

    static class RecordingSubscriber implements Flow.Subscriber<IServerEventResponse> {
        final List<IServerEventResponse> events = new ArrayList<>();
        final AtomicInteger completeCount = new AtomicInteger();
        volatile Throwable error;
        final CompletableFuture<Void> terminal = new CompletableFuture<>();

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(1);
        }

        @Override
        public void onNext(IServerEventResponse item) {
            events.add(item);
        }

        @Override
        public void onError(Throwable throwable) {
            error = throwable;
            terminal.complete(null);
        }

        @Override
        public void onComplete() {
            completeCount.incrementAndGet();
            terminal.complete(null);
        }
    }

    static class StallTestSubscription extends AbstractServerEventSubscription {
        final CompletableFuture<?> future = new CompletableFuture<>();

        /**
         * 测试收紧停滞预算（4.3-C6-004 面① protected 钩子）：300ms 内终态必须到达，
         * 用例不再受基类缺省 120s 预算支配。
         */
        @Override
        protected long demandWaitTimeoutMillis() {
            return 300;
        }

        StallTestSubscription(Flow.Subscriber<? super IServerEventResponse> subscriber) {
            super(subscriber);
        }

        @Override
        protected Future<?> startRequest() {
            return future;
        }

        void feed(String line) {
            processLine(line);
        }

        Thread feedOnThread(String line) {
            Thread feeder = new Thread(() -> processLine(line), "test-sse-feeder");
            feeder.setDaemon(true);
            feeder.start();
            return feeder;
        }
    }

    @Test
    public void demandStallTerminatesWithErrorWithinBudget_C6_004() throws Exception {
        RecordingSubscriber subscriber = new RecordingSubscriber();
        StallTestSubscription subscription = new StallTestSubscription(subscriber);
        subscription.request(1);

        // 第一个事件：demand=1，正常派发
        subscription.feed("data: one");
        subscription.feed("");
        assertEquals(1, subscriber.events.size(), "first event dispatched with demand");

        // 第二个事件：demand=0 且订阅者停滞 —— 派发线程进入 waitForDemand。
        // 修复前：无限期 wait（RED）；修复后：预算耗尽 → cleanup + onError（GREEN）。
        subscription.feed("data: two");
        subscription.feedOnThread("");

        subscriber.terminal.get(OBSERVE_WINDOW_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS);

        assertNotNull(subscriber.error,
                "stalled subscriber demand wait must terminate with onError within budget (4.3-C6-004)");
        assertTrue(subscriber.completeCount.get() == 0,
                "stall must be an error terminal, not a normal completion");
        assertEquals(1, subscriber.events.size(), "no further events after stall terminal");
    }

    @Test
    public void cancelWakesDemandWaitWithoutStallError_C6_004() throws Exception {
        RecordingSubscriber subscriber = new RecordingSubscriber();
        StallTestSubscription subscription = new StallTestSubscription(subscriber);
        subscription.request(1);

        subscription.feed("data: one");
        subscription.feed("");
        assertEquals(1, subscriber.events.size());

        subscription.feed("data: two");
        Thread feeder = subscription.feedOnThread("");
        // 取消应解除 demand 等待（既有语义），且不产生停滞错误（取消非停滞）
        Thread.sleep(200);
        subscription.cancel();

        feeder.join(OBSERVE_WINDOW_MILLIS);
        assertTrue(!feeder.isAlive(), "cancel must wake the demand wait (feeder must not stay parked)");
        assertTrue(subscriber.error == null,
                "cancel must wake the demand wait without raising a stall error, got: " + subscriber.error);
        assertEquals(1, subscriber.events.size(), "no dispatch after cancel");
    }
}
