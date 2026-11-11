package io.nop.spring.web.filter;

import io.nop.http.api.server.IStreamResponseWriter;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.api.core.util.FutureHelper.success;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 4.4-MA-03（plan 2562 裁定 A）+ C6-004/C1-005（裁定 H）：ServletHttpServerContext
 * complete 语义钉——
 * <ul>
 * <li>三处错误路径（publisher completeWithError / writerFn.fail / writerFn 装配外层 catch）
 * 不再调用 asyncCtx.complete()（complete 不洗白截断）；正常完成路径保持 complete。</li>
 * <li>两处 startAsync 站点 setTimeout（可配，缺省 60000）并注册 AsyncListener；
 * onTimeout → complete 收口（闭合裁定 A 移除错误路径 complete 后的超时-dispatch 循环；
 * CAS 串行化吸收 C6-004 二次 ISE 噪声）。</li>
 * <li>C1-005：context / characterEncoding 字段 volatile 结构钉。</li>
 * </ul>
 * 硬同 diff 依赖：裁定 A（本测试 error 断言）与裁定 H（超时收口）不可拆分——错误路径仅经
 * 裁定 H 的 async timeout 终止。
 */
public class TestServletHttpServerContextCompleteSemantics {

    /**
     * Recording AsyncContext stub（JDK 动态代理，依赖无关——同包
     * TestServletHttpServerContextAdapter 先例）：断言面为 complete()/setTimeout()/
     * addListener 调用计数 + fireTimeout()（AsyncListener.onTimeout 手动触发）。
     */
    static class RecordingAsyncContext implements InvocationHandler {
        final AtomicInteger completeCount = new AtomicInteger();
        final List<Long> setTimeoutValues = new ArrayList<>();
        final List<AsyncListener> listeners = new ArrayList<>();

        AsyncContext proxy() {
            return (AsyncContext) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class[]{AsyncContext.class}, this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "complete":
                    completeCount.incrementAndGet();
                    return null;
                case "setTimeout":
                    setTimeoutValues.add((Long) args[0]);
                    return null;
                case "addListener":
                    listeners.add((AsyncListener) args[0]);
                    return null;
                default:
                    if (method.getReturnType() == boolean.class)
                        return false;
                    if (method.getReturnType() == int.class)
                        return 0;
                    if (method.getReturnType() == long.class)
                        return 0L;
                    return null;
            }
        }

        void fireTimeout() throws Exception {
            for (AsyncListener listener : listeners) {
                listener.onTimeout(new AsyncEvent(proxy()));
            }
        }
    }

    static class Stub {
        final StringWriter body = new StringWriter();
        final RecordingAsyncContext asyncCtx = new RecordingAsyncContext();
        RuntimeException getWriterFailure;

        HttpServletRequest newRequest() {
            RecordingAsyncContext recording = asyncCtx;
            return (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class[]{HttpServletRequest.class}, (proxy, method, args) -> {
                        if ("startAsync".equals(method.getName()))
                            return recording.proxy();
                        return defaultFor(method.getReturnType());
                    });
        }

        HttpServletResponse newResponse() {
            return (HttpServletResponse) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class[]{HttpServletResponse.class}, (proxy, method, args) -> {
                        if ("getWriter".equals(method.getName())) {
                            if (getWriterFailure != null) {
                                throw getWriterFailure;
                            }
                            return new PrintWriter(body);
                        }
                        return defaultFor(method.getReturnType());
                    });
        }

        private static Object defaultFor(Class<?> type) {
            if (type == boolean.class)
                return false;
            if (type == int.class)
                return 0;
            if (type == long.class)
                return 0L;
            return null;
        }

        ServletHttpServerContext newContext() {
            return new ServletHttpServerContext(newRequest(), newResponse());
        }

        ServletHttpServerContext newContext(long timeout) {
            return new ServletHttpServerContext(newRequest(), newResponse(), timeout);
        }
    }

    // publisher-variant helpers：Flow.Publisher 是函数式接口（subscribe 单方法）
    private static Flow.Publisher<String> completingPublisher() {
        return subscriber -> subscriber.onComplete();
    }

    private static Flow.Publisher<String> failingPublisher(Throwable t) {
        return subscriber -> subscriber.onError(t);
    }

    private static CompletableFuture<Void> done(java.util.concurrent.CompletionStage<Void> stage) throws Exception {
        CompletableFuture<Void> future = stage.toCompletableFuture();
        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            // 预期：裁定 A 错误路径 future.completeExceptionally
        }
        return future;
    }

    // ---- MA-03 裁定 A：正常完成路径保持 complete ----

    @Test
    public void testPublisherNormalCompletionCallsCompleteOnce() throws Exception {
        Stub stub = new Stub();
        ServletHttpServerContext ctx = stub.newContext();

        done(ctx.sendStreamingResponse(200, "text/event-stream", completingPublisher()));

        assertEquals(1, stub.asyncCtx.completeCount.get(),
                "normal completion must call asyncCtx.complete() exactly once");
    }

    @Test
    public void testWriterFnNormalCompletionCallsCompleteOnce() throws Exception {
        Stub stub = new Stub();
        ServletHttpServerContext ctx = stub.newContext();

        done(ctx.sendStreamingResponse(200, "text/event-stream", (IStreamResponseWriter w) -> w.complete()));

        assertEquals(1, stub.asyncCtx.completeCount.get(),
                "normal completion must call asyncCtx.complete() exactly once");
    }

    // ---- MA-03 裁定 A：三处错误路径不调 complete（绿断言 NOT called；mutation 还原调用 → 红）----

    @Test
    public void testPublisherErrorPathDoesNotCallComplete() throws Exception {
        Stub stub = new Stub();
        ServletHttpServerContext ctx = stub.newContext();

        CompletableFuture<Void> future = done(ctx.sendStreamingResponse(200, "text/event-stream",
                failingPublisher(new RuntimeException("boom"))));

        assertTrue(future.isCompletedExceptionally(), "C10-018: completeExceptionally must be kept");
        assertEquals(0, stub.asyncCtx.completeCount.get(),
                "4.4-MA-03（裁定 A）: completeWithError must NOT call asyncCtx.complete() — "
                        + "error termination goes through the async timeout listener only");
    }

    @Test
    public void testWriterFnFailDoesNotCallComplete() throws Exception {
        Stub stub = new Stub();
        ServletHttpServerContext ctx = stub.newContext();

        CompletableFuture<Void> future = done(ctx.sendStreamingResponse(200, "text/event-stream",
                (IStreamResponseWriter writer) -> writer.fail(new RuntimeException("boom"))));

        assertTrue(future.isCompletedExceptionally(), "C10-018: completeExceptionally must be kept");
        assertEquals(0, stub.asyncCtx.completeCount.get(),
                "4.4-MA-03（裁定 A）: writerFn.fail must NOT call asyncCtx.complete()");
    }

    @Test
    public void testWriterFnAssemblyOuterCatchDoesNotCallComplete() throws Exception {
        Stub stub = new Stub();
        stub.getWriterFailure = new RuntimeException("writer setup blew up");
        ServletHttpServerContext ctx = stub.newContext();

        CompletableFuture<Void> future = done(ctx.sendStreamingResponse(200, "text/event-stream",
                (IStreamResponseWriter writer) -> success(null)));

        assertTrue(future.isCompletedExceptionally(), "C10-018: completeExceptionally must be kept");
        assertEquals(0, stub.asyncCtx.completeCount.get(),
                "4.4-MA-03（裁定 A）: writerFn assembly outer catch must NOT call asyncCtx.complete()");
    }

    // ---- C6-004 裁定 H：setTimeout + AsyncListener ----

    @Test
    public void testStartAsyncSitesSetConfiguredTimeoutAndAddListener() throws Exception {
        Stub stub = new Stub();
        ServletHttpServerContext ctx = stub.newContext(123456L);

        done(ctx.sendStreamingResponse(200, "text/event-stream", (IStreamResponseWriter w) -> w.complete()));
        done(ctx.sendStreamingResponse(200, "text/event-stream", (IStreamResponseWriter writer) -> success(null)));

        assertEquals(List.of(123456L, 123456L), stub.asyncCtx.setTimeoutValues,
                "C6-004（裁定 H）: both startAsync sites must setTimeout with the configured value");
        assertEquals(2, stub.asyncCtx.listeners.size(),
                "C6-004（裁定 H）: both startAsync sites must register an AsyncListener");
    }

    @Test
    public void testDefaultTimeoutIs60000() throws Exception {
        Stub stub = new Stub();
        ServletHttpServerContext ctx = stub.newContext();

        done(ctx.sendStreamingResponse(200, "text/event-stream", (IStreamResponseWriter w) -> w.complete()));

        assertEquals(60000L, ServletHttpServerContext.DEFAULT_ASYNC_TIMEOUT_MILLIS);
        assertEquals(List.of(60000L), stub.asyncCtx.setTimeoutValues,
                "default timeout must be 60000 (configurable via "
                        + ServletHttpServerContext.CFG_ASYNC_TIMEOUT_MILLIS + ")");
    }

    @Test
    public void testNonPositiveTimeoutFallsBackToDefault() throws Exception {
        Stub stub = new Stub();
        ServletHttpServerContext ctx = stub.newContext(0L);

        done(ctx.sendStreamingResponse(200, "text/event-stream", (IStreamResponseWriter w) -> w.complete()));

        assertEquals(List.of(ServletHttpServerContext.DEFAULT_ASYNC_TIMEOUT_MILLIS), stub.asyncCtx.setTimeoutValues);
    }

    @Test
    public void testTimeoutListenerCompletesOnceAndAbsorbsDoubleInvocation() throws Exception {
        // 裁定 A 闭合循环：错误路径无 complete → 超时监听器是唯一收口方；二次 onTimeout（C6-004
        // 噪声形态）经 asyncClosed CAS 吸收，不再产生二次 ISE 面。
        Stub stub = new Stub();
        ServletHttpServerContext ctx = stub.newContext(123456L);

        done(ctx.sendStreamingResponse(200, "text/event-stream",
                (IStreamResponseWriter writer) -> writer.fail(new RuntimeException("boom"))));

        assertEquals(0, stub.asyncCtx.completeCount.get(),
                "error path must leave complete to the timeout listener");

        stub.asyncCtx.fireTimeout();
        assertEquals(1, stub.asyncCtx.completeCount.get(),
                "onTimeout must call asyncCtx.complete() exactly once");

        stub.asyncCtx.fireTimeout();
        assertEquals(1, stub.asyncCtx.completeCount.get(),
                "C6-004: second onTimeout must be absorbed (no double-complete ISE noise)");
    }

    @Test
    public void testTimeoutListenerIsNoOpAfterNormalCompletion() throws Exception {
        Stub stub = new Stub();
        ServletHttpServerContext ctx = stub.newContext(123456L);

        done(ctx.sendStreamingResponse(200, "text/event-stream", (IStreamResponseWriter w) -> w.complete()));
        assertEquals(1, stub.asyncCtx.completeCount.get());

        stub.asyncCtx.fireTimeout();
        assertEquals(1, stub.asyncCtx.completeCount.get(),
                "timeout after normal completion must not double-complete");
    }

    @Test
    public void testPublisherVariantTimeoutListenerClosesAfterError() throws Exception {
        Stub stub = new Stub();
        ServletHttpServerContext ctx = stub.newContext(123456L);

        done(ctx.sendStreamingResponse(200, "text/event-stream",
                failingPublisher(new RuntimeException("boom"))));

        stub.asyncCtx.fireTimeout();
        assertEquals(1, stub.asyncCtx.completeCount.get(),
                "publisher error path is also closed by the timeout listener (裁定 A/H 同 diff 依赖)");
    }

    // ---- C1-005 裁定 H 搭车：volatile 结构钉 ----

    @Test
    public void testContextFieldsAreVolatile() throws Exception {
        assertVolatile(ServletHttpServerContext.class.getDeclaredField("context"));
        assertVolatile(ServletHttpServerContext.class.getDeclaredField("characterEncoding"));
    }

    private static void assertVolatile(Field field) {
        assertTrue(Modifier.isVolatile(field.getModifiers()),
                "C1-005（裁定 H 搭车）: field " + field.getName() + " must be volatile");
    }
}
