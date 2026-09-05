package io.nop.http.client.jdk;

import org.junit.jupiter.api.Test;

import java.net.http.HttpRequest.BodyPublishers;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestConcatenatedBodyPublisherFix {

    static class CollectingSubscriber implements Flow.Subscriber<ByteBuffer> {
        final AtomicInteger onSubscribeCount = new AtomicInteger();
        final AtomicInteger completeCount = new AtomicInteger();
        final StringBuilder text = new StringBuilder();
        volatile Flow.Subscription subscription;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            onSubscribeCount.incrementAndGet();
            this.subscription = subscription;
        }

        @Override
        public void onNext(ByteBuffer item) {
            byte[] bytes = new byte[item.remaining()];
            item.get(bytes);
            text.append(new String(bytes, StandardCharsets.UTF_8));
        }

        @Override
        public void onError(Throwable throwable) {
        }

        @Override
        public void onComplete() {
            completeCount.incrementAndGet();
        }
    }

    @Test
    public void testSingleOnSubscribe() throws InterruptedException {
        ConcatenatedBodyPublisher publisher = new ConcatenatedBodyPublisher(
                List.of(BodyPublishers.ofString("aa"), BodyPublishers.ofString("bb"), BodyPublishers.ofString("cc")));

        assertEquals(6, publisher.contentLength());

        CollectingSubscriber subscriber = new CollectingSubscriber();
        publisher.subscribe(subscriber);
        subscriber.subscription.request(Long.MAX_VALUE);

        for (int i = 0; i < 100 && subscriber.completeCount.get() == 0; i++) {
            Thread.sleep(10);
        }

        // Flow 契约：onSubscribe 只能被调用一次（JDK HttpClient 对重复 onSubscribe 抛 IllegalStateException）
        assertEquals(1, subscriber.onSubscribeCount.get());
        assertEquals(1, subscriber.completeCount.get());
        assertEquals("aabbcc", subscriber.text.toString());
    }
}
