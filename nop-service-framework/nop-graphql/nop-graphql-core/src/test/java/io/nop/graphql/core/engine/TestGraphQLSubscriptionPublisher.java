/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.engine;

import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import io.nop.graphql.core.IGraphQLExecutionContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class TestGraphQLSubscriptionPublisher extends BaseTestCase {
    private GraphQLEngine engine;
    private IGraphQLExecutionContext context;

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
        engine.setSchemaLoader(new MockGraphQLSchemaLoader());
        engine.init();
        context = new GraphQLExecutionContext(new ServiceContextImpl());
    }

    @Test
    public void testValueTransformation() throws InterruptedException {
        SubmissionPublisher<Object> sourcePublisher = new SubmissionPublisher<>();
        GraphQLSubscriptionPublisher publisher = new GraphQLSubscriptionPublisher(
                sourcePublisher, context, engine, new DataFetchingEnvironment());

        List<GraphQLResponseBean> results = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);

        publisher.subscribe(new Flow.Subscriber<GraphQLResponseBean>() {
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                subscription.request(10);
            }

            @Override
            public void onNext(GraphQLResponseBean item) {
                results.add(item);
                latch.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
            }

            @Override
            public void onComplete() {
            }
        });

        sourcePublisher.submit("value1");
        sourcePublisher.submit(42);
        sourcePublisher.submit(List.of("a", "b", "c"));
        sourcePublisher.close();

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Should receive all values within timeout");
        assertEquals(3, results.size());
        assertEquals("value1", results.get(0).getData());
        assertEquals(42, results.get(1).getData());
        assertEquals(List.of("a", "b", "c"), results.get(2).getData());
    }

    @Test
    public void testCompletion() throws InterruptedException {
        SubmissionPublisher<Object> sourcePublisher = new SubmissionPublisher<>();
        GraphQLSubscriptionPublisher publisher = new GraphQLSubscriptionPublisher(
                sourcePublisher, context, engine, new DataFetchingEnvironment());

        AtomicBoolean completed = new AtomicBoolean(false);
        CountDownLatch completeLatch = new CountDownLatch(1);
        List<GraphQLResponseBean> results = new ArrayList<>();

        publisher.subscribe(new Flow.Subscriber<GraphQLResponseBean>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(10);
            }

            @Override
            public void onNext(GraphQLResponseBean item) {
                results.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
            }

            @Override
            public void onComplete() {
                completed.set(true);
                completeLatch.countDown();
            }
        });

        sourcePublisher.submit("value1");
        sourcePublisher.submit("value2");
        sourcePublisher.close();

        assertTrue(completeLatch.await(5, TimeUnit.SECONDS), "Should complete within timeout");
        assertTrue(completed.get(), "Should be completed");
        assertEquals(2, results.size());
    }

    @Test
    public void testCancellation() throws InterruptedException {
        SubmissionPublisher<Object> sourcePublisher = new SubmissionPublisher<>();
        GraphQLSubscriptionPublisher publisher = new GraphQLSubscriptionPublisher(
                sourcePublisher, context, engine, new DataFetchingEnvironment());

        AtomicInteger receivedCount = new AtomicInteger(0);
        AtomicReference<Flow.Subscription> subscriptionRef = new AtomicReference<>();
        CountDownLatch subscribeLatch = new CountDownLatch(1);

        publisher.subscribe(new Flow.Subscriber<GraphQLResponseBean>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscriptionRef.set(subscription);
                subscription.request(1);
                subscribeLatch.countDown();
            }

            @Override
            public void onNext(GraphQLResponseBean item) {
                receivedCount.incrementAndGet();
                subscriptionRef.get().cancel();
            }

            @Override
            public void onError(Throwable throwable) {
            }

            @Override
            public void onComplete() {
            }
        });

        assertTrue(subscribeLatch.await(5, TimeUnit.SECONDS));
        sourcePublisher.submit("first");
        Thread.sleep(100);
        sourcePublisher.submit("second");
        sourcePublisher.submit("third");
        sourcePublisher.close();

        Thread.sleep(200);
        assertEquals(1, receivedCount.get(), "Should receive only 1 item after cancellation");
    }

    @Test
    public void testBackpressure() throws InterruptedException {
        SubmissionPublisher<Object> sourcePublisher = new SubmissionPublisher<>();
        GraphQLSubscriptionPublisher publisher = new GraphQLSubscriptionPublisher(
                sourcePublisher, context, engine, new DataFetchingEnvironment());

        List<GraphQLResponseBean> results = new ArrayList<>();
        AtomicInteger requestCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(3);

        publisher.subscribe(new Flow.Subscriber<GraphQLResponseBean>() {
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                subscription.request(1);
            }

            @Override
            public void onNext(GraphQLResponseBean item) {
                results.add(item);
                latch.countDown();
                requestCount.incrementAndGet();
                if (requestCount.get() < 3) {
                    subscription.request(1);
                }
            }

            @Override
            public void onError(Throwable throwable) {
            }

            @Override
            public void onComplete() {
            }
        });

        sourcePublisher.submit("value1");
        sourcePublisher.submit("value2");
        sourcePublisher.submit("value3");
        sourcePublisher.close();

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Should receive all values within timeout");
        assertEquals(3, results.size());
    }

    @Test
    public void testEmptyPublisher() throws InterruptedException {
        SubmissionPublisher<Object> sourcePublisher = new SubmissionPublisher<>();
        GraphQLSubscriptionPublisher publisher = new GraphQLSubscriptionPublisher(
                sourcePublisher, context, engine, new DataFetchingEnvironment());

        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicInteger receivedCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        publisher.subscribe(new Flow.Subscriber<GraphQLResponseBean>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(10);
            }

            @Override
            public void onNext(GraphQLResponseBean item) {
                receivedCount.incrementAndGet();
            }

            @Override
            public void onError(Throwable throwable) {
            }

            @Override
            public void onComplete() {
                completed.set(true);
                latch.countDown();
            }
        });

        sourcePublisher.close();

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(completed.get());
        assertEquals(0, receivedCount.get());
    }
}
