package io.nop.http.api.client;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestServerEventSubscriptionFix {

    static class RecordingSubscriber implements Flow.Subscriber<IServerEventResponse> {
        final List<IServerEventResponse> events = new ArrayList<>();
        final AtomicInteger completeCount = new AtomicInteger();
        volatile Throwable error;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(IServerEventResponse item) {
            events.add(item);
        }

        @Override
        public void onError(Throwable throwable) {
            error = throwable;
        }

        @Override
        public void onComplete() {
            completeCount.incrementAndGet();
        }
    }

    static class TestSubscription extends AbstractServerEventSubscription {
        final CompletableFuture<?> future = new CompletableFuture<>();

        TestSubscription(Flow.Subscriber<? super IServerEventResponse> subscriber) {
            super(subscriber);
        }

        @Override
        protected java.util.concurrent.Future<?> startRequest() {
            return future;
        }

        void feed(String line) {
            processLine(line);
        }
    }

    @Test
    public void testIdAndEventAccumulateBeforeDispatch() {
        RecordingSubscriber subscriber = new RecordingSubscriber();
        TestSubscription subscription = new TestSubscription(subscriber);
        subscription.request(1);

        subscription.feed("id: 42");
        subscription.feed("event: add");
        subscription.feed("data: hello");

        // id/event 字段之后、空行之前的 data 行不应提前派发
        assertEquals(0, subscriber.events.size());

        subscription.feed("");
        assertEquals(1, subscriber.events.size());
        assertEquals("42", subscriber.events.get(0).getId());
        assertEquals("add", subscriber.events.get(0).getEvent());
        assertEquals("hello", subscriber.events.get(0).getData());
    }

    @Test
    public void testUnknownFieldIgnored() {
        RecordingSubscriber subscriber = new RecordingSubscriber();
        TestSubscription subscription = new TestSubscription(subscriber);
        subscription.request(1);

        subscription.feed("retry: 1000");
        subscription.feed("");
        assertEquals(0, subscriber.events.size());
    }

    @Test
    public void testDoneDispatchesPendingAndCompletesOnce() {
        RecordingSubscriber subscriber = new RecordingSubscriber();
        TestSubscription subscription = new TestSubscription(subscriber);
        subscription.request(1);

        subscription.feed("data: part1");
        subscription.feed("data: [DONE]");

        assertEquals(1, subscriber.events.size());
        assertEquals("part1", subscriber.events.get(0).getData());
        assertEquals(1, subscriber.completeCount.get());

        // 连接关闭再次触发 onComplete 不应重复通知
        subscription.feed("data: [DONE]");
        assertEquals(1, subscriber.completeCount.get());
    }

    @Test
    public void testPendingDataDispatchedOnComplete() {
        RecordingSubscriber subscriber = new RecordingSubscriber();
        TestSubscription subscription = new TestSubscription(subscriber);
        subscription.request(1);

        subscription.feed("data: tail-without-blank-line");
        assertEquals(0, subscriber.events.size());

        subscription.future.complete(null);
        subscription.onComplete();
        assertEquals(1, subscriber.events.size());
        assertEquals("tail-without-blank-line", subscriber.events.get(0).getData());
        assertEquals(1, subscriber.completeCount.get());
    }

    @Test
    public void testNoEventsAfterCancel() {
        RecordingSubscriber subscriber = new RecordingSubscriber();
        TestSubscription subscription = new TestSubscription(subscriber);
        subscription.request(1);

        subscription.cancel();
        subscription.feed("data: after-cancel");
        subscription.feed("");

        assertEquals(0, subscriber.events.size());
        assertTrue(subscriber.completeCount.get() == 0 || subscriber.error == null);
    }
}
