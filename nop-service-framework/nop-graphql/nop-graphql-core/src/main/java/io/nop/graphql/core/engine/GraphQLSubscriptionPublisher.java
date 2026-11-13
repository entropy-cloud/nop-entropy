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
                    // 2.3-C3-005（plan 2550 裁定 C）：取消是第三条终止路径——取消订阅若不
                    // complete 上下文，上下文级清理回调永不触发（泄漏）。三条终止路径对称。
                    context.complete();
                    upstreamSubscription.cancel();
                }
            });
        }

        @Override
        public void onNext(Object item) {
            try {
                // 订阅流的每条消息不触发执行上下文complete：buildGraphQLResponse是为一次性
                // 请求-响应设计的收尾函数，在首条消息上complete会把上下文标记为完成并触发
                // 上下文级完成回调（后续onBeforeComplete注册抛IllegalStateException）。
                // 完成动作移到流终止信号（onComplete/onError）
                GraphQLResponseBean response = engine.buildGraphQLResponse(item, null, null);
                downstream.onNext(response);
            } catch (Exception e) {
                downstream.onError(e);
                upstreamSubscription.cancel();
            }
        }

        @Override
        public void onError(Throwable throwable) {
            // 2.3-C3-005（plan 2550 裁定 C）：错误终止路径对称补上下文 complete（onComplete
            // 路径已有；取消路径见 cancel()）——错误后上下文清理回调同样必须触发。
            context.complete();
            GraphQLResponseBean response = engine.buildGraphQLResponse(null, throwable, context);
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
