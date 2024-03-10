/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.function.Consumer;

public class FlowHelper {
    public static <T> Flow.Publisher<T> toPublisher(CompletionStage<T> promise, Runnable canceller) {
        Flow.Publisher<T> publisher = new Flow.Publisher<T>() {
            @Override
            public void subscribe(Flow.Subscriber<? super T> subscriber) {
                Flow.Subscription subscription = new Flow.Subscription() {
                    @Override
                    public void request(long n) {
                    }

                    @Override
                    public void cancel() {
                        if (canceller != null) {
                            canceller.run();
                        }
                    }
                };
                subscriber.onSubscribe(subscription);

                promise.whenComplete((v, err) -> {
                    if (err != null) {
                        subscriber.onError(err);
                    } else {
                        try {
                            subscriber.onNext(v);
                        } catch (Exception e) {
                            subscriber.onError(e);
                        }
                        subscriber.onComplete();
                    }
                });
            }
        };
        return publisher;
    }

    public static <T> CompletableFuture<Void> toPromise(Flow.Publisher<T> publisher, Consumer<T> action) {
        CompletableFuture<Void> promise = new CompletableFuture<>();
        publisher.subscribe(new Flow.Subscriber<T>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(T item) {
                if (action != null) {
                    action.accept(item);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                promise.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                promise.complete(null);
            }
        });
        return promise;
    }
}
