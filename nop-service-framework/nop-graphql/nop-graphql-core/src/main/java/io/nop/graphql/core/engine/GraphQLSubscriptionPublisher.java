/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.engine;

import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.graphql.core.IGraphQLExecutionContext;

import java.util.concurrent.Flow;

public class GraphQLSubscriptionPublisher implements Flow.Publisher<GraphQLResponseBean> {
    private final Flow.Publisher<Object> sourcePublisher;
    private final IGraphQLExecutionContext context;
    private final GraphQLEngine engine;

    public GraphQLSubscriptionPublisher(Flow.Publisher<Object> sourcePublisher,
                                        IGraphQLExecutionContext context,
                                        GraphQLEngine engine,
                                        DataFetchingEnvironment env) {
        this.sourcePublisher = sourcePublisher;
        this.context = context;
        this.engine = engine;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super GraphQLResponseBean> subscriber) {
        sourcePublisher.subscribe(new TransformingSubscriber(subscriber));
    }

    private class TransformingSubscriber implements Flow.Subscriber<Object> {
        private final Flow.Subscriber<? super GraphQLResponseBean> downstream;
        private Flow.Subscription upstreamSubscription;

        TransformingSubscriber(Flow.Subscriber<? super GraphQLResponseBean> downstream) {
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
                GraphQLResponseBean response = engine.buildGraphQLResponse(item, null, context);
                downstream.onNext(response);
            } catch (Exception e) {
                downstream.onError(e);
                upstreamSubscription.cancel();
            }
        }

        @Override
        public void onError(Throwable throwable) {
            GraphQLResponseBean response = engine.buildGraphQLResponse(null, throwable, context);
            downstream.onNext(response);
            downstream.onComplete();
        }

        @Override
        public void onComplete() {
            downstream.onComplete();
        }
    }
}
