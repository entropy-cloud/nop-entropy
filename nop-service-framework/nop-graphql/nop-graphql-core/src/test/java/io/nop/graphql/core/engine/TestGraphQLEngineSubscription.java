/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.engine;

import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_UNEXPECTED_OPERATION_TYPE;
import static org.junit.jupiter.api.Assertions.*;

public class TestGraphQLEngineSubscription extends BaseTestCase {
    GraphQLEngine engine;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @BeforeEach
    public void setUp() {
        engine = new GraphQLEngine();
        engine.setSchemaLoader(new SubscriptionMockSchemaLoader());
        engine.init();
    }

    @Test
    public void testSubscribeGraphQL_nullOperation_throwsException() {
        IGraphQLExecutionContext context = new GraphQLExecutionContext(new ServiceContextImpl());
        
        assertThrows(Exception.class, () -> {
            engine.subscribeGraphQL(context);
        });
    }

    @Test
    public void testErrorPublisher_sendsErrorToSubscriber() throws InterruptedException {
        RuntimeException testError = new RuntimeException("Test error");
        
        Flow.Publisher<GraphQLResponseBean> errorPublisher = subscriber -> {
            Flow.Subscription subscription = new Flow.Subscription() {
                @Override
                public void request(long n) {
                    subscriber.onError(testError);
                }

                @Override
                public void cancel() {
                }
            };
            subscriber.onSubscribe(subscription);
        };

        final boolean[] errorReceived = {false};
        final Throwable[] receivedError = {null};
        Object lock = new Object();

        errorPublisher.subscribe(new Flow.Subscriber<GraphQLResponseBean>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(1);
            }

            @Override
            public void onNext(GraphQLResponseBean item) {
            }

            @Override
            public void onError(Throwable throwable) {
                synchronized (lock) {
                    errorReceived[0] = true;
                    receivedError[0] = throwable;
                    lock.notifyAll();
                }
            }

            @Override
            public void onComplete() {
            }
        });

        synchronized (lock) {
            lock.wait(1000);
        }

        assertTrue(errorReceived[0], "Should receive error");
        assertEquals(testError, receivedError[0]);
    }

    @Test
    public void testSubscriptionPublisher_wrapsPublisher() {
        SubmissionPublisher<Object> source = new SubmissionPublisher<>();
        IGraphQLExecutionContext context = new GraphQLExecutionContext(new ServiceContextImpl());
        
        GraphQLSubscriptionPublisher publisher = new GraphQLSubscriptionPublisher(
                source, context, engine, new DataFetchingEnvironment());

        assertNotNull(publisher);
        assertTrue(publisher instanceof Flow.Publisher);
    }
}
