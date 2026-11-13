package io.nop.gateway.core.executor;

import io.nop.api.core.beans.ApiRequest;
import io.nop.gateway.core.context.GatewayContextImpl;
import io.nop.gateway.model.GatewayRouteModel;
import io.nop.gateway.model.GatewayStreamingModel;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.client.IServerEventResponse;
import io.nop.api.core.util.ICancelToken;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.3-C3-004（plan 2550 裁定 C）钉：窗口内失败重订阅链的次数上界 + 指数退避。
 * pin3-a：maxAttempts=2 时恒失败上游 → 恰 1 次首发 + 2 次重订阅后错误终态（旧形态无限重订阅红）。
 * pin3-b：退避间隔经注入缝确定断言（重订阅前按退避函数调度）。
 * pin3-c：maxAttempts<=0 保持无限重订阅语义档（此处以上界 0 时多次重订阅仍推进验证）。
 */
public class TestStreamResubscribeBound {

    /** 测试局部类型化异常（规避 no-raw-Exception lint：不实例化裸 RuntimeException） */
    static final class UpstreamFailure extends RuntimeException {
        UpstreamFailure(String message) {
            super(message);
        }
    }

    private GatewayRouteModel streamingRoute() {
        GatewayStreamingModel streaming = new GatewayStreamingModel();
        streaming.setContentType("text/event-stream");
        streaming.setBufferEnabled(true);
        streaming.setBufferSize(10);
        streaming.setBufferTimeMs(0L); // 元素即刻越窗（本钉不测窗口缓冲）
        GatewayRouteModel route = new GatewayRouteModel();
        route.setId("resub-route");
        route.setStreaming(streaming);
        return route;
    }

    private static class FailingUpstreamClient implements IHttpClient {
        final AtomicInteger fetches = new AtomicInteger();

        @Override
        public CompletionStage<IHttpResponse> fetchAsync(HttpRequest request, ICancelToken cancelToken) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Flow.Publisher<IServerEventResponse> fetchServerEventFlow(HttpRequest request, ICancelToken cancelToken) {
            return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
                boolean done;

                @Override
                public void request(long n) {
                    if (!done) {
                        done = true;
                        fetches.incrementAndGet();
                        subscriber.onError(new UpstreamFailure("upstream attempt-" + fetches.get() + " failed"));
                    }
                }

                @Override
                public void cancel() {
                }
            });
        }

        @Override
        public CompletionStage<IHttpResponse> downloadAsync(HttpRequest request,
                io.nop.http.api.client.IHttpOutputFile targetFile,
                io.nop.http.api.client.DownloadOptions options, ICancelToken cancelToken) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletionStage<IHttpResponse> uploadAsync(HttpRequest request,
                io.nop.http.api.client.IHttpInputFile inputFile,
                io.nop.http.api.client.UploadOptions options, ICancelToken cancelToken) {
            throw new UnsupportedOperationException();
        }
    }

    /** 映射链自驱动形态（与 createMappedPublisher 一致：onSubscribe request(1)，逐元素接力） */
    private static Function<Flow.Publisher<IServerEventResponse>, Flow.Publisher<Object>> selfDrivenMapper() {
        return eventPublisher -> downstream -> eventPublisher.subscribe(new Flow.Subscriber<IServerEventResponse>() {
            private Flow.Subscription up;

            @Override
            public void onSubscribe(Flow.Subscription s) {
                this.up = s;
                downstream.onSubscribe(s);
                s.request(1);
            }

            @Override
            public void onNext(IServerEventResponse item) {
                downstream.onNext(item.getData());
                up.request(1);
            }

            @Override
            public void onError(Throwable t) {
                downstream.onError(t);
            }

            @Override
            public void onComplete() {
                downstream.onComplete();
            }
        });
    }

    /** 记录重订阅调度的确定性执行器：同步执行任务并登记延迟值 */
    private static class RecordingScheduler implements BufferedStreamingPublisher.DelayedExecutor {
        final List<Long> delays = new CopyOnWriteArrayList<>();
        final List<Runnable> tasks = new CopyOnWriteArrayList<>();

        @Override
        public void schedule(long delayMs, Runnable task) {
            delays.add(delayMs);
            tasks.add(task);
            task.run(); // 同步推进（确定性，零真实等待）
        }
    }

    private BufferedStreamingPublisher newPublisher(IHttpClient client, int maxAttempts,
            java.util.function.LongUnaryOperator backoffFn, RecordingScheduler scheduler) {
        GatewayRouteModel route = streamingRoute();
        return new BufferedStreamingPublisher(client, route, new ApiRequest<>(), new GatewayContextImpl(),
                route.getStreaming(),
                (throwable, r, req, ctx) -> new HttpRequest(), // 恒重试回调
                null,
                selfDrivenMapper(),
                new HttpRequest(),
                maxAttempts,
                backoffFn,
                scheduler);
    }

    @Test
    public void pin3a_resubscribeStopsAtMaxAttempts() throws Exception {
        FailingUpstreamClient client = new FailingUpstreamClient();
        RecordingScheduler scheduler = new RecordingScheduler();
        // 退避 >0：重订阅经调度缝（确定性同步推进）；退避间隔取值在 pin3b 专项断言
        BufferedStreamingPublisher publisher = newPublisher(client, 2, n -> 5L, scheduler);

        CompletableFuture<Throwable> errorFuture = new CompletableFuture<>();
        publisher.subscribe(new Flow.Subscriber<Object>() {
            @Override
            public void onSubscribe(Flow.Subscription s) {
                s.request(1); // 首授信驱动 attempt
            }

            @Override
            public void onNext(Object item) {
            }

            @Override
            public void onError(Throwable t) {
                errorFuture.complete(t);
            }

            @Override
            public void onComplete() {
                errorFuture.complete(null);
            }
        });

        Throwable err = errorFuture.get(10, TimeUnit.SECONDS);
        assertNotNull(err, "exhausted resubscribes must deliver error terminal");
        // 恰 1 次首发 + 2 次重订阅（旧无限重订阅形态此计数持续增长，本断言红）
        assertEquals(3, client.fetches.get(), "attempts must stop at initial + maxAttempts");
        assertEquals(2, scheduler.tasks.size(), "each resubscribe must be scheduled");
    }

    @Test
    public void pin3b_backoffDelaysAppliedPerResubscribe() throws Exception {
        FailingUpstreamClient client = new FailingUpstreamClient();
        RecordingScheduler scheduler = new RecordingScheduler();
        // 退避函数：n -> n * 10（attemptCount 驱动）
        BufferedStreamingPublisher publisher = newPublisher(client, 3, n -> n * 10L, scheduler);

        CompletableFuture<Throwable> errorFuture = new CompletableFuture<>();
        publisher.subscribe(new Flow.Subscriber<Object>() {
            @Override
            public void onSubscribe(Flow.Subscription s) {
                s.request(1);
            }

            @Override
            public void onNext(Object item) {
            }

            @Override
            public void onError(Throwable t) {
                errorFuture.complete(t);
            }

            @Override
            public void onComplete() {
                errorFuture.complete(null);
            }
        });

        errorFuture.get(10, TimeUnit.SECONDS);
        assertEquals(4, client.fetches.get(), "initial + 3 resubscribes");
        // 退避延迟按注入函数取值：attemptCount=1,2,3 → 10,20,30
        assertEquals(List.of(10L, 20L, 30L), scheduler.delays,
                "backoff delays must be computed and applied per resubscribe (injection seam)");
    }

    @Test
    public void pin3c_unlimitedKeepsLegacySemantics() throws Exception {
        FailingUpstreamClient client = new FailingUpstreamClient();
        // 无上界档链条无限推进——异步调度（同步执行会无限递归）
        RecordingScheduler asyncScheduler = new RecordingScheduler() {
            @Override
            public void schedule(long delayMs, Runnable task) {
                CompletableFuture.runAsync(task);
            }
        };
        // maxAttempts=0 = 不设上界：多次重订阅持续推进（演示窗口内链条不被钳制）。
        // 退避函数须 >0——delay==0 走直呼分支会在主线程同步无限递归；>0 经调度缝异步推进。
        BufferedStreamingPublisher publisher = newPublisher(client, 0, n -> 1L, asyncScheduler);

        publisher.subscribe(new Flow.Subscriber<Object>() {
            @Override
            public void onSubscribe(Flow.Subscription s) {
                s.request(1);
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

        // 有界观察窗内链条持续推进（否则本测试因观察超时红）
        for (int i = 0; i < 200 && client.fetches.get() < 5; i++) {
            Thread.sleep(10);
        }
        assertTrue(client.fetches.get() >= 5, "unbounded mode must keep resubscribing");
    }
}
