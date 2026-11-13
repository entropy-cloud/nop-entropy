package io.nop.graphql.core.engine;

import io.nop.core.context.ServiceContextImpl;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.3-C3-005（plan 2550 裁定 C）钉：订阅流三条终止路径（onError/onComplete/cancel）
 * 均触发执行上下文 complete。取消路径此前从不触发（上下文清理回调永不执行 = 泄漏）。
 */
public class TestSubscriptionContextComplete extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }


    private static class CompletableContext extends GraphQLExecutionContext {
        final AtomicBoolean completed = new AtomicBoolean(false);

        CompletableContext() {
            super(new ServiceContextImpl());
        }

        @Override
        public void complete() {
            completed.set(true);
            super.complete();
        }
    }

    private GraphQLEngine newEngine() {
        GraphQLEngine engine = new GraphQLEngine();
        engine.setSchemaLoader(new MockGraphQLSchemaLoader());
        engine.init();
        return engine;
    }

    private static <T> Flow.Subscriber<T> ignoreSubscriber() {
        return new Flow.Subscriber<T>() {
            @Override
            public void onSubscribe(Flow.Subscription s) {
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(T item) {
            }

            @Override
            public void onError(Throwable t) {
            }

            @Override
            public void onComplete() {
            }
        };
    }

    @Test
    public void pin7_onCompleteTriggersContextComplete() throws Exception {
        CompletableContext ctx = new CompletableContext();
        GraphQLEngine engine = newEngine();
        SubmissionPublisher<Object> source = new SubmissionPublisher<>();
        GraphQLSubscriptionPublisher publisher =
                new GraphQLSubscriptionPublisher(source, ctx, engine, null);
        publisher.subscribe(ignoreSubscriber());
        source.close();
        // SubmissionPublisher 的 onComplete 信号可能经默认 executor 异步投递——有界等待
        for (int i = 0; i < 300 && !ctx.completed.get(); i++) {
            Thread.sleep(10);
        }
        assertTrue(ctx.completed.get(), "onComplete path must complete the context");
    }

    @Test
    public void pin7_onErrorTriggersContextComplete() throws Exception {
        CompletableContext ctx = new CompletableContext();
        GraphQLEngine engine = newEngine();
        SubmissionPublisher<Object> source = new SubmissionPublisher<>();
        // engine 带 mock schema：buildGraphQLResponse 走异常响应装配面
        GraphQLSubscriptionPublisher publisher =
                new GraphQLSubscriptionPublisher(source, ctx, engine, null);
        CompletableFuture<Void> terminated = new CompletableFuture<>();
        publisher.subscribe(new Flow.Subscriber<Object>() {
            @Override
            public void onSubscribe(Flow.Subscription s) {
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(Object item) {
                terminated.complete(null);
            }

            @Override
            public void onError(Throwable t) {
                terminated.complete(null);
            }

            @Override
            public void onComplete() {
                terminated.complete(null);
            }
        });
        source.closeExceptionally(new RuntimeException("upstream boom"));
        terminated.get(10, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(ctx.completed.get(), "onError path must complete the context (2.3-C3-005)");
    }

    @Test
    public void pin7_cancelTriggersContextComplete() throws Exception {
        CompletableContext ctx = new CompletableContext();
        GraphQLEngine engine = newEngine();
        SubmissionPublisher<Object> source = new SubmissionPublisher<>();
        GraphQLSubscriptionPublisher publisher =
                new GraphQLSubscriptionPublisher(source, ctx, engine, null);
        AtomicReference<Flow.Subscription> subRef = new AtomicReference<>();
        publisher.subscribe(new Flow.Subscriber<Object>() {
            @Override
            public void onSubscribe(Flow.Subscription s) {
                subRef.set(s);
            }

            @Override
            public void onNext(Object item) {
            }

            @Override
            public void onError(Throwable t) {
            }

            @Override
            public void onComplete() {
            }
        });
        assertFalse(ctx.completed.get(), "context must stay open while subscription active");
        // onSubscribe 可能经 SubmissionPublisher 缺省 executor 延迟投递——有界等待
        for (int i = 0; i < 300 && subRef.get() == null; i++) {
            Thread.sleep(10);
        }
        if (subRef.get() == null) {
            org.junit.jupiter.api.Assertions.fail("onSubscribe not delivered within bounded window");
        }
        subRef.get().cancel();
        // cancel 同步触发 context.complete
        for (int i = 0; i < 50 && !ctx.completed.get(); i++) {
            Thread.sleep(10);
        }
        assertTrue(ctx.completed.get(), "cancel path must complete the context (2.3-C3-005)");
    }
}
