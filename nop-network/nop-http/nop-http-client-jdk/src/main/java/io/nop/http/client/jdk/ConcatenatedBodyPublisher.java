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

        Subscription(Flow.Subscriber<? super ByteBuffer> subscriber, List<BodyPublisher> bodyPublishers) {
            this.subscriber = subscriber;
            this.bodyPublishers = bodyPublishers;
        }

        void start() {
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
                        subscriber.onSubscribe(Subscription.this);
                    }

                    @Override
                    public void onNext(ByteBuffer item) {
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
            if (currentSubscription != null) {
                currentSubscription.request(n);
            }
        }

        @Override
        public void cancel() {
            if (currentSubscription != null) {
                currentSubscription.cancel();
            }
        }
    }
}