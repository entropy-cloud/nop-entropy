package io.nop.http.client.jdk;

import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Flow;

public class ConcatenatedBodyPublisher implements BodyPublisher {
    private final List<BodyPublisher> bodyPublishers;

    public ConcatenatedBodyPublisher(List<BodyPublisher> bodyPublishers) {
        this.bodyPublishers = bodyPublishers;
    }

    @Override
    public long contentLength() {
        return bodyPublishers.stream()
                .mapToLong(BodyPublisher::contentLength)
                .sum();
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        new Subscription(subscriber, bodyPublishers).start();
    }

    private static class Subscription implements Flow.Subscription {
        private final Flow.Subscriber<? super ByteBuffer> subscriber;
        private final List<BodyPublisher> bodyPublishers;
        private int currentPublisherIndex = 0;
        private Flow.Subscription currentSubscription;
        // 精确记账未满足的需求：每个内部 publisher 都要按剩余需求转发 request
        private long demand;

        Subscription(Flow.Subscriber<? super ByteBuffer> subscriber, List<BodyPublisher> bodyPublishers) {
            this.subscriber = subscriber;
            this.bodyPublishers = bodyPublishers;
        }

        void start() {
            // Flow 契约：下游订阅者只能收到一次 onSubscribe
            subscriber.onSubscribe(this);
            if (bodyPublishers.isEmpty()) {
                subscriber.onComplete();
            } else {
                subscribeToNextPublisher();
            }
        }

        private void subscribeToNextPublisher() {
            if (currentPublisherIndex < bodyPublishers.size()) {
                BodyPublisher nextPublisher = bodyPublishers.get(currentPublisherIndex++);
                nextPublisher.subscribe(new Flow.Subscriber<>() {
                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        currentSubscription = subscription;
                        long toRequest;
                        synchronized (Subscription.this) {
                            toRequest = demand;
                        }
                        if (toRequest > 0) {
                            subscription.request(toRequest);
                        }
                    }

                    @Override
                    public void onNext(ByteBuffer item) {
                        synchronized (Subscription.this) {
                            demand--;
                        }
                        subscriber.onNext(item);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        subscriber.onError(throwable);
                    }

                    @Override
                    public void onComplete() {
                        subscribeToNextPublisher();
                    }
                });
            } else {
                subscriber.onComplete();
            }
        }

        @Override
        public void request(long n) {
            synchronized (this) {
                demand += n;
            }
            Flow.Subscription current = currentSubscription;
            if (current != null) {
                current.request(n);
            }
        }

        @Override
        public void cancel() {
            Flow.Subscription current = currentSubscription;
            if (current != null) {
                current.cancel();
            }
        }
    }
}