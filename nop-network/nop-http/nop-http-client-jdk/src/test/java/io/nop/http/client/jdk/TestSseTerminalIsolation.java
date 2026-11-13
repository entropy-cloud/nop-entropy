package io.nop.http.client.jdk;

import com.sun.net.httpserver.HttpServer;
import io.nop.http.api.client.AbstractServerEventSubscription;
import io.nop.http.api.client.IServerEventResponse;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 4.3-C3-033（plan 2561 裁定 I）钉：SSE 终态隔离四路径——
 * ① onComplete 即 cleanup（底层请求取消）；② dispatchEvent completed 守卫（终态后不派发）；
 * ③ [DONE] 终态取消底层请求；④ jdk ServerEventPublisher 取消不再派发假 onError
 * （CancellationException 分流）。勿回退 2505 的 ERR_HTTP_SSE_DEMAND_TIMEOUT 行为
 * （该面由 nop-http-api 既有 TestServerEventSubscriptionDemandTimeout_C6_004 承载）。
 */
public class TestSseTerminalIsolation {

    /** 轻量订阅：底层请求以可观测 future 承载，行喂入受控。 */
    static class TestSubscription extends AbstractServerEventSubscription {
        final CompletableFuture<Void> underlying = new CompletableFuture<>();
        final List<String> events = new CopyOnWriteArrayList<>();
        volatile Throwable error;
        volatile boolean subscriberCompleted;

        TestSubscription(Flow.Subscriber<IServerEventResponse> subscriber) {
            super(subscriber);
        }

        @Override
        protected Future<?> startRequest() {
            return underlying;
        }

        void feed(String line) {
            processLine(line);
        }

        void finish() {
            onComplete();
        }
    }

    static class RecordingSubscriber implements Flow.Subscriber<IServerEventResponse> {
        final CountDownLatch completed = new CountDownLatch(1);
        final CountDownLatch errored = new CountDownLatch(1);
        volatile Throwable error;
        volatile Flow.Subscription subscription;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
        }

        @Override
        public void onNext(IServerEventResponse item) {
        }

        @Override
        public void onError(Throwable throwable) {
            this.error = throwable;
            errored.countDown();
        }

        @Override
        public void onComplete() {
            completed.countDown();
        }
    }

    /**
     * ① onComplete → cleanup：自然终态后底层请求必须被取消、订阅置为取消态。
     * 变异红形态：恢复原 onComplete（无 cleanup）→ underlying 未取消 → 红。
     */
    @Test
    public void onCompleteTriggersCleanup() throws Exception {
        RecordingSubscriber subscriber = new RecordingSubscriber();
        TestSubscription sub = new TestSubscription(subscriber);
        sub.request(10);
        assertFalse(sub.underlying.isCancelled(), "终态前底层请求不得已取消");

        sub.feed("data: hello");
        sub.feed("");
        sub.feed("event: tick");
        sub.feed("data: last");
        sub.feed("");
        sub.finish();

        assertTrue(subscriber.completed.await(5, TimeUnit.SECONDS), "订阅者必须收到 onComplete");
        assertTrue(sub.underlying.isCancelled(), "C3-033①: onComplete 必须触发 cleanup 取消底层请求");
        assertNull(subscriber.error, "正常终态不得派发错误");
    }

    /**
     * ② completed 守卫：终态后喂入的事件不得再派发。
     * 变异红形态：去掉守卫 → 终态后事件照常投递 → 红。
     */
    @Test
    public void noDispatchAfterCompleted() throws Exception {
        RecordingSubscriber subscriber = new RecordingSubscriber();
        List<String> events = new CopyOnWriteArrayList<>();
        TestSubscription sub = new TestSubscription(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription s) {
            }

            @Override
            public void onNext(IServerEventResponse item) {
                events.add(item.getData());
            }

            @Override
            public void onError(Throwable t) {
            }

            @Override
            public void onComplete() {
                subscriber.completed.countDown();
            }
        });
        sub.request(10);

        sub.feed("data: before-done");
        sub.feed("");
        sub.finish();
        assertTrue(subscriber.completed.await(5, TimeUnit.SECONDS));
        assertEquals(1, events.size());

        // 终态后再喂入完整事件：不得派发
        sub.feed("data: late");
        sub.feed("");
        assertEquals(1, events.size(), "C3-033②: completed 后不得继续派发事件");
    }

    /**
     * ③ [DONE] → 取消底层请求：[DONE] 行本身即流终态。
     * 变异红形态：恢复原 [DONE] 分支（只 onComplete 不 cleanup）→ underlying 未取消 → 红。
     */
    @Test
    public void doneMarkerCancelsUnderlyingRequest() throws Exception {
        RecordingSubscriber subscriber = new RecordingSubscriber();
        TestSubscription sub = new TestSubscription(subscriber);
        sub.request(10);

        sub.feed("data: x");
        sub.feed("data: [DONE]");

        assertTrue(subscriber.completed.await(5, TimeUnit.SECONDS), "[DONE] 必须终态化订阅");
        assertTrue(sub.underlying.isCancelled(), "C3-033③: [DONE] 必须经 cleanup 取消底层请求");
        assertNull(subscriber.error);
    }

    /**
     * ④ jdk ServerEventPublisher：headers 阶段取消 → sendAsync future 以 CancellationException
     * 完结，不得再向订阅者派发假 onError（取消是调用方主动终态而非传输错误）。
     * 注：必须在 headers 到达前取消——headers 后 sendAsync future 已完成，cancel 为 no-op，
     * 不经过本分流点（该窗口由 2505 的 bodyIn 显式关闭承载）。
     * 变异红形态：去掉 CancellationException 分流 → 取消后 onError 被触发 → 红。
     */
    @Test
    public void cancelDuringHeadersPhaseDoesNotFakeOnError() throws Exception {
        CountDownLatch releaseHeaders = new CountDownLatch(1);
        CountDownLatch requestArrived = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sse", exchange -> {
            requestArrived.countDown();
            try {
                // 扣住响应头：客户端 sendAsync future 保持未完成，取消才能命中它
                releaseHeaders.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            try {
                exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                exchange.sendResponseHeaders(200, 0);
                OutputStream os = exchange.getResponseBody();
                os.write("data: late\n\n".getBytes(StandardCharsets.UTF_8));
                os.close();
            } catch (Exception ignored) {
                // 客户端已取消断开
            }
        });
        server.start();
        HttpClient jdkClient = HttpClient.newHttpClient();
        try {
            ServerEventPublisher publisher = new ServerEventPublisher(jdkClient,
                    java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/sse"))
                            .build(),
                    null);
            RecordingSubscriber cancelTarget = new RecordingSubscriber();
            publisher.subscribe(cancelTarget);
            cancelTarget.subscription.request(1);
            assertTrue(requestArrived.await(10, TimeUnit.SECONDS), "请求必须已到达服务端（headers 未回）");

            cancelTarget.subscription.cancel();

            assertFalse(cancelTarget.errored.await(2, TimeUnit.SECONDS),
                    "C3-033④: 取消不得派发假 onError（CancellationException 分流），实际收到: "
                            + cancelTarget.error);
            assertEquals(1, cancelTarget.completed.getCount(), "取消终态也不得伪造 onComplete");
        } finally {
            releaseHeaders.countDown();
            server.stop(0);
        }
    }
}
