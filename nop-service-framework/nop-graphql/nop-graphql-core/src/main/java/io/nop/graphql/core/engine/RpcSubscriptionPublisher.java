/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.engine;

import io.nop.api.core.beans.ApiResponse;
import io.nop.graphql.core.IGraphQLExecutionContext;

import java.util.concurrent.Flow;

public class RpcSubscriptionPublisher implements Flow.Publisher<ApiResponse<?>> {
    private final Flow.Publisher<Object> sourcePublisher;
    private final IGraphQLExecutionContext context;
    private final GraphQLEngine engine;

    public RpcSubscriptionPublisher(Flow.Publisher<Object> sourcePublisher,
                                    IGraphQLExecutionContext context,
                                    GraphQLEngine engine) {
        this.sourcePublisher = sourcePublisher;
        this.context = context;
        this.engine = engine;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ApiResponse<?>> subscriber) {
        sourcePublisher.subscribe(new TransformingSubscriber(subscriber));
    }

    private class TransformingSubscriber implements Flow.Subscriber<Object> {
        private final Flow.Subscriber<? super ApiResponse<?>> downstream;
        private Flow.Subscription upstreamSubscription;

        TransformingSubscriber(Flow.Subscriber<? super ApiResponse<?>> downstream) {
            this.downstream = downstream;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.upstreamSubscription = subscription;
            downstream.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                    upstreamSubscription.request(n);
                }

                @Override
                public void cancel() {
                    upstreamSubscription.cancel();
                }
            });
        }

        @Override
        public void onNext(Object item) {
            try {
                // 订阅流的每条消息不触发执行上下文complete：buildRpcResponse内部会complete
                // 执行上下文（一次性请求-响应的收尾语义），在首条消息上complete会把上下文标记
                // 为完成并提前触发上下文级完成回调。完成动作移到流终止信号（onComplete/onError）
                ApiResponse<?> response = engine.buildRpcResponse(item, null, null);
                downstream.onNext(response);
            } catch (Exception e) {
                downstream.onError(e);
                upstreamSubscription.cancel();
            }
        }

        @Override
        public void onError(Throwable throwable) {
            ApiResponse<?> response = engine.buildRpcResponse(null, throwable, context);
            downstream.onNext(response);
            downstream.onComplete();
        }

        @Override
        public void onComplete() {
            context.complete();
            downstream.onComplete();
        }
    }
}
