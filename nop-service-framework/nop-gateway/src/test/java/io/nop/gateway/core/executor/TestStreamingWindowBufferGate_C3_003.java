package io.nop.gateway.core.executor;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.gateway.core.context.GatewayContextImpl;
import io.nop.gateway.core.context.IGatewayContext;
import io.nop.gateway.core.interceptor.IGatewayInvocation;
import io.nop.gateway.core.streaming.GatewayStreamingConstants;
import io.nop.gateway.core.streaming.IStreamingRetryCallback;
import io.nop.gateway.core.streaming.StreamingResponse;
import io.nop.gateway.model.GatewayInvokeModel;
import io.nop.gateway.model.GatewayRouteModel;
import io.nop.gateway.model.GatewayStreamingModel;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.initialize.CoreInitialization;
import io.nop.http.api.client.HttpRequest;
import io.nop.api.core.util.ICancelToken;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.client.IServerEventResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * plan 2509 Phase 2 组 A：2.3-C3-003 + 3.3-C2-009（2.3 半边）回归测试。
 *
 * <p>覆盖（设计 §六 成员 1 / §七 红绿形态表第 1 行）：
 * ① 越窗缓冲硬上界 fail-loud（溢出类型化错误 + 零重订阅）；
 * ② demand 门控授信（下游 request(n) 前上游拉取受授信约束）；
 * ③ C2-009 重试决策出锁（monitor 持有窗内不执行 retryCallback——并发探针断言）；
 * ④ disabled 直通档；
 * ⑤ 错误终态 cancel 上游（:164-166 一处两态）。
 */
class TestStreamingWindowBufferGate_C3_003 {

    @BeforeAll
    static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ======================= harness =======================

    /**
     * 可控上游 publisher：STEP 模式 = 每次 request 发射一个元素（真实 SSE 拉取形态）；
     * BURST 模式 = 首次 request 同步发射全部元素（违约突发上游，第二道防线触发面）。
     * 记录上游 request 次数与 cancel 状态（授信记账断言 + cancel 两态断言载体）。
     */
    private static final class CountingPublisher implements Flow.Publisher<IServerEventResponse> {
        enum Mode {STEP, BURST}

        private final List<String> items;
        private final Throwable error;
        private final Mode mode;
        final AtomicInteger requestCount = new AtomicInteger();
        final AtomicBoolean cancelled = new AtomicBoolean();
        private volatile boolean started;
        private int cursor;

        CountingPublisher(List<String> items, Throwable error, Mode mode) {
            this.items = items;
            this.error = error;
            this.mode = mode;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super IServerEventResponse> subscriber) {
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                    requestCount.incrementAndGet();
                    if (mode == Mode.STEP) {
                        emitOne(subscriber);
                        return;
                    }
                    if (started) {
                        return;
                    }
                    started = true;
                    emitAll(subscriber);
                }

                @Override
                public void cancel() {
                    cancelled.set(true);
                }
            });
        }

        private void emitOne(Flow.Subscriber<? super IServerEventResponse> subscriber) {
            if (cursor < items.size()) {
                subscriber.onNext(new FakeServerEvent(items.get(cursor++)));
                return;
            }
            if (error != null) {
                subscriber.onError(error);
            } else {
                subscriber.onComplete();
            }
        }

        private void emitAll(Flow.Subscriber<? super IServerEventResponse> subscriber) {
            while (cursor < items.size()) {
                subscriber.onNext(new FakeServerEvent(items.get(cursor++)));
            }
            if (error != null) {
                subscriber.onError(error);
            } else {
                subscriber.onComplete();
            }
        }
    }

    private static final class FakeHttpClient implements IHttpClient {
        final List<HttpRequest> requests = new ArrayList<>();
        final List<CountingPublisher> publishers = new ArrayList<>();
        private final List<CountingPublisher> queued = new ArrayList<>();

        FakeHttpClient queue(CountingPublisher publisher) {
            queued.add(publisher);
            return this;
        }

        @Override
        public CompletionStage<IHttpResponse> fetchAsync(HttpRequest request, ICancelToken cancelToken) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Flow.Publisher<IServerEventResponse> fetchServerEventFlow(HttpRequest request, ICancelToken cancelToken) {
            requests.add(request);
            CountingPublisher publisher = queued.remove(0);
            publishers.add(publisher);
            return publisher;
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

    private static final class FakeServerEvent implements IServerEventResponse {
        private final String text;

        FakeServerEvent(String text) {
            this.text = text;
        }

        @Override
        public int getHttpStatus() {
            return 200;
        }

        @Override
        public String getId() {
            return null;
        }

        @Override
        public String getEvent() {
            return "message";
        }

        @Override
        public String getData() {
            return "\"" + text + "\"";
        }

        @Override
        public Map<String, String> getHeaders() {
            return new LinkedHashMap<>();
        }
    }

    /** 手动驱动 demand 的订阅者：onSubscribe 不预取（demand = 0），request(n) 显式授信。 */
    private static final class ManualDemandSubscriber implements Flow.Subscriber<Object> {
        final List<String> texts = new ArrayList<>();
        volatile boolean completed;
        volatile Throwable error;
        private Flow.Subscription subscription;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription; // 不 request：demand = 0
        }

        @Override
        @SuppressWarnings("unchecked")
        public void onNext(Object item) {
            Object text = item instanceof Map ? ((Map<String, Object>) item).get("text") : item;
            texts.add(text != null ? text.toString() : null);
        }

        @Override
        public void onError(Throwable throwable) {
            this.error = throwable;
        }

        @Override
        public void onComplete() {
            this.completed = true;
        }

        void request(long n) {
            subscription.request(n);
        }
    }

    private static class CollectingSubscriber implements Flow.Subscriber<Object> {
        final List<String> texts = new ArrayList<>();
        Throwable error;
        boolean completed;
        Flow.Subscription subscription;
        private final CompletableFuture<Void> done = new CompletableFuture<>();

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void onNext(Object item) {
            Object text = item instanceof Map ? ((Map<String, Object>) item).get("text") : item;
            texts.add(text != null ? text.toString() : null);
        }

        @Override
        public void onError(Throwable throwable) {
            this.error = throwable;
            done.complete(null);
        }

        @Override
        public void onComplete() {
            this.completed = true;
            done.complete(null);
        }

        void await() {
            try {
                done.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                fail("stream did not terminate: " + e);
            }
        }
    }

    private static GatewayStreamingModel streamingModel(Boolean bufferEnabled, Integer bufferSize) {
        GatewayStreamingModel model = new GatewayStreamingModel();
        model.setContentType("text/event-stream");
        model.setBufferEnabled(bufferEnabled);
        model.setBufferSize(bufferSize);
        return model;
    }

    private static GatewayRouteModel route(GatewayStreamingModel streaming) {
        GatewayInvokeModel invoke = new GatewayInvokeModel();
        invoke.setUrl((IEvalAction) ctx -> "https://gw-test.example.com/v1/chat/completions");
        GatewayRouteModel route = new GatewayRouteModel();
        route.setId("stream-route");
        route.setInvoke(invoke);
        route.setStreaming(streaming);
        return route;
    }

    private static IGatewayInvocation identityInvocation() {
        return new IGatewayInvocation() {
            @Override
            public ApiRequest<?> proceedOnRequest(ApiRequest<?> request, IGatewayContext svcCtx) {
                return request;
            }

            @Override
            public CompletionStage<ApiResponse<?>> proceedInvoke(ApiRequest<?> request, IGatewayContext svcCtx) {
                return null;
            }

            @Override
            public ApiResponse<?> proceedOnResponse(ApiResponse<?> response, IGatewayContext svcCtx) {
                return response;
            }

            @Override
            public ApiResponse<?> proceedOnError(Throwable exception, IGatewayContext svcCtx) {
                return null;
            }

            @Override
            public void proceedOnStreamStart(ApiRequest<?> request, IGatewayContext svcCtx) {
            }

            @Override
            public Object proceedOnStreamElement(Object element, IGatewayContext svcCtx) {
                return element;
            }

            @Override
            public Object proceedOnStreamError(Throwable exception, IGatewayContext svcCtx) {
                return null;
            }

            @Override
            public void proceedOnStreamComplete(IGatewayContext svcCtx) {
            }
        };
    }

    private static ApiRequest<?> request() {
        ApiRequest<Object> request = new ApiRequest<>();
        request.setData(Map.of("model", "test-model"));
        return request;
    }

    // ======================= ① 越窗上界 fail-loud（红绿形态表第 1 行主腿） =======================

    /**
     * demand 受限订阅者 + 突发上游：缓冲深度不得超 bufferSize——溢出 = 类型化错误终态
     * （不进 retryCallback、不重订阅），上游 cancel 回收。
     */
    @Test
    @Timeout(10)
    void overflowBurstBeyondBufferSizeFailsLoud_C3_003() {
        GatewayStreamingModel streaming = streamingModel(true, 2);
        GatewayRouteModel route = route(streaming);
        GatewayContextImpl context = new GatewayContextImpl();
        context.setRequest(request());
        context.setHttpMethod("POST");
        AtomicInteger callbackCalls = new AtomicInteger();
        context.setAttribute(GatewayStreamingConstants.ATTR_RETRY_CALLBACK,
                (IStreamingRetryCallback) (error, r, req, ctx) -> {
                    callbackCalls.incrementAndGet();
                    return null;
                });

        FakeHttpClient fake = new FakeHttpClient();
        // 突发上游：首次 request 同步发射 5 个元素（bufferSize=2 → 必然越窗 + 溢出）
        fake.queue(new CountingPublisher(List.of("a1", "a2", "a3", "a4", "a5"), null, CountingPublisher.Mode.BURST));

        StreamingProcessor processor = new StreamingProcessor(fake, new MappingProcessor(null));
        processor.executeStreaming(route, request(), context, identityInvocation());

        StreamingResponse streamingResponse = (StreamingResponse) context.getAttribute(StreamingResponse.class.getName());
        assertNotNull(streamingResponse);
        ManualDemandSubscriber subscriber = new ManualDemandSubscriber();
        streamingResponse.getPublisher().subscribe(subscriber);

        // 初始授信 2：触发突发发射 → 缓冲上界必须生效
        subscriber.request(2);

        assertNotNull(subscriber.error,
                "溢出必须 fail-loud 断流（修复前：静默无界缓冲、无错误终态；类型化校验见 BoundedWindowBufferTest）");
        assertTrue(subscriber.texts.size() <= 2,
                "交付元素不得超 bufferSize：修复前缓冲深度随元素数线性增长（" + subscriber.texts + "）");
        assertEquals(0, callbackCalls.get(), "溢出为不可重试终态，不得 consult retryCallback");
        assertEquals(1, fake.requests.size(), "溢出零重订阅");
        assertTrue(fake.publishers.get(0).cancelled.get(), "溢出错误终态必须 cancel 上游");
        assertFalse(subscriber.completed, "溢出不得伪装正常完成");
    }

    // ======================= ② demand 门控授信（裁定⑤(b) 2.3 半边） =======================

    /**
     * 下游 request(n) 之前，缓冲层不得驱动上游拉取（映射链 request(1) 前置 demand 检查）；
     * 授信后上游拉取次数受授信记账约束（逐元素拉取 = 上游 request 次数 == 已授信交付数）。
     */
    @Test
    @Timeout(10)
    void upstreamPullGatedByDownstreamDemand_C3_003() {
        GatewayStreamingModel streaming = streamingModel(true, 10);
        // 窗口期 = 0：首个元素即越窗（本测试隔离 demand 授信面，不测窗口缓冲）
        streaming.setBufferTimeMs(0L);
        GatewayRouteModel route = route(streaming);
        GatewayContextImpl context = new GatewayContextImpl();
        context.setRequest(request());
        context.setHttpMethod("POST");

        FakeHttpClient fake = new FakeHttpClient();
        fake.queue(new CountingPublisher(List.of("a1", "a2", "a3", "a4"), null, CountingPublisher.Mode.STEP));

        StreamingProcessor processor = new StreamingProcessor(fake, new MappingProcessor(null));
        processor.executeStreaming(route, request(), context, identityInvocation());

        StreamingResponse streamingResponse = (StreamingResponse) context.getAttribute(StreamingResponse.class.getName());
        ManualDemandSubscriber subscriber = new ManualDemandSubscriber();
        streamingResponse.getPublisher().subscribe(subscriber);

        CountingPublisher upstream = fake.publishers.get(0);
        assertEquals(0, upstream.requestCount.get(),
                "下游授信前缓冲层不得驱动上游拉取（修复前：映射链自驱动 request(1) 立即拉取）");

        subscriber.request(1);
        assertEquals(1, upstream.requestCount.get(), "授信 1 = 上游拉取 1");
        assertEquals(List.of("a1"), subscriber.texts);

        subscriber.request(3);
        assertEquals(4, upstream.requestCount.get(), "累计授信 4 = 上游拉取 4（授信记账：demand==0 不再 request(1)）");
        assertEquals(List.of("a1", "a2", "a3", "a4"), subscriber.texts);
        assertFalse(subscriber.completed, "上游未越出元素边界前不得补发终态");

        // 最后一格授信：拉取越出元素边界 → 上游 onComplete → 冲空后补发
        subscriber.request(1);
        assertEquals(5, upstream.requestCount.get());
        assertTrue(subscriber.completed, "上游完成后冲空缓冲补发 onComplete");
        assertEquals(1, fake.requests.size(), "零重订阅");
    }

    // ======================= ③ C2-009 重试决策出锁 =======================

    /**
     * retryCallback（重试决策）执行期间，BufferedSubscription monitor 不得被持有：
     * 探针线程并发争抢下游 subscription 监视器，决策窗内必须可获锁
     * （修复前：handleError 持 monitor 执行 retryCallback，探针在决策窗内不可获锁）。
     */
    @Test
    @Timeout(10)
    void retryDecisionRunsOutsideMonitor_C2_009() {
        GatewayStreamingModel streaming = streamingModel(true, 10);
        GatewayRouteModel route = route(streaming);
        GatewayContextImpl context = new GatewayContextImpl();
        context.setRequest(request());
        context.setHttpMethod("POST");

        AtomicReference<Flow.Subscription> downstreamSub = new AtomicReference<>();
        AtomicBoolean probeAcquired = new AtomicBoolean(false);
        AtomicBoolean decisionWindowClosed = new AtomicBoolean(false);
        CountDownLatch probeAcquiredGate = new CountDownLatch(1);
        context.setAttribute(GatewayStreamingConstants.ATTR_RETRY_CALLBACK,
                (IStreamingRetryCallback) (error, r, req, ctx) -> {
                    Thread probe = new Thread(() -> {
                        synchronized (downstreamSub.get()) { // BufferedSubscription monitor
                            // 仅当重试决策窗未关闭时获锁成功才算数（防决策结束后补获锁的假阳性）
                            if (!decisionWindowClosed.get()) {
                                probeAcquired.set(true);
                            }
                        }
                        probeAcquiredGate.countDown();
                    }, "c2-009-monitor-probe");
                    probe.setDaemon(true);
                    probe.start();
                    boolean acquiredInWindow = false;
                    try {
                        acquiredInWindow = probeAcquiredGate.await(2, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    decisionWindowClosed.set(true);
                    probeAcquired.set(acquiredInWindow && probeAcquired.get());
                    HttpRequest retry = new HttpRequest();
                    retry.setUrl("https://gw-b.example.com/v1/chat/completions");
                    return retry;
                });

        FakeHttpClient fake = new FakeHttpClient();
        RuntimeException fail1 = new RuntimeException("upstream fail attempt 1");
        fake.queue(new CountingPublisher(List.of("a1"), fail1, CountingPublisher.Mode.BURST));
        fake.queue(new CountingPublisher(List.of("b1", "b2"), null, CountingPublisher.Mode.BURST));

        StreamingProcessor processor = new StreamingProcessor(fake, new MappingProcessor(null));
        processor.executeStreaming(route, request(), context, identityInvocation());

        StreamingResponse streamingResponse = (StreamingResponse) context.getAttribute(StreamingResponse.class.getName());
        CollectingSubscriber collector = new CollectingSubscriber() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                downstreamSub.set(subscription);
                subscription.request(Long.MAX_VALUE);
            }
        };
        streamingResponse.getPublisher().subscribe(collector);
        collector.await();

        assertNotNull(downstreamSub.get(), "下游 subscription 必须已建立（探针载体）");
        assertTrue(probeAcquired.get(),
                "重试决策期间 monitor 必须已释放（修复前：retryCallback 在 monitor 内执行，探针不可获锁）");
        assertTrue(collector.completed);
        assertEquals(List.of("b1", "b2"), collector.texts, "重订阅后新 attempt 正常交付");
        assertEquals(2, fake.requests.size());
    }

    // ======================= ④ disabled 直通档 =======================

    /** bufferEnabled=false：零缓冲直传（元素按序直达 + 正常完成），缓冲层仍 engaged（回调注入）。 */
    @Test
    @Timeout(10)
    void disabledBufferPassthroughDirectDelivery_C3_003() {
        GatewayStreamingModel streaming = streamingModel(false, 10);
        GatewayRouteModel route = route(streaming);
        GatewayContextImpl context = new GatewayContextImpl();
        context.setRequest(request());
        context.setHttpMethod("POST");
        context.setAttribute(GatewayStreamingConstants.ATTR_RETRY_CALLBACK,
                (IStreamingRetryCallback) (error, r, req, ctx) -> null);

        FakeHttpClient fake = new FakeHttpClient();
        fake.queue(new CountingPublisher(List.of("a1", "a2", "a3"), null, CountingPublisher.Mode.BURST));

        StreamingProcessor processor = new StreamingProcessor(fake, new MappingProcessor(null));
        processor.executeStreaming(route, request(), context, identityInvocation());

        StreamingResponse streamingResponse = (StreamingResponse) context.getAttribute(StreamingResponse.class.getName());
        assertInstanceOf(BufferedStreamingPublisher.class, streamingResponse.getPublisher(),
                "回调注入后缓冲层 engaged（直通档属于组件六合一状态核之一档）");
        ManualDemandSubscriber subscriber = new ManualDemandSubscriber();
        streamingResponse.getPublisher().subscribe(subscriber);

        subscriber.request(3);
        assertEquals(List.of("a1", "a2", "a3"), subscriber.texts, "直通档：元素按序直达，不缓冲");
        assertTrue(subscriber.completed);
        assertNull(subscriber.error);
    }

    // ======================= ⑤ 错误终态 cancel 上游（:164-166 一处两态） =======================

    /** 窗口外失败：错误终态必须 cancel 上游（修复前：SSE 连接滞留至 EOF）。 */
    @Test
    @Timeout(10)
    void outOfWindowErrorTerminalCancelsUpstream_C3_003() {
        GatewayStreamingModel streaming = streamingModel(true, 2);
        GatewayRouteModel route = route(streaming);
        GatewayContextImpl context = new GatewayContextImpl();
        context.setRequest(request());
        context.setHttpMethod("POST");

        FakeHttpClient fake = new FakeHttpClient();
        RuntimeException fail = new RuntimeException("out of window fail");
        fake.queue(new CountingPublisher(List.of("a1", "a2", "a3"), fail, CountingPublisher.Mode.BURST));

        StreamingProcessor processor = new StreamingProcessor(fake, new MappingProcessor(null));
        processor.executeStreaming(route, request(), context, identityInvocation());

        StreamingResponse streamingResponse = (StreamingResponse) context.getAttribute(StreamingResponse.class.getName());
        CollectingSubscriber collector = new CollectingSubscriber();
        streamingResponse.getPublisher().subscribe(collector);
        collector.await();

        assertEquals(fail, collector.error, "窗口外失败原样转发错误");
        assertTrue(fake.publishers.get(0).cancelled.get(), "错误终态必须 cancel 上游（修复前：连接滞留）");
    }

    /** 映射链 onNext 错误分支（StreamingProcessor :164-166）：onError 同时 cancel 本层上游订阅。 */
    @Test
    @Timeout(10)
    void mappingErrorBranchCancelsUpstream_C3_003() {
        GatewayStreamingModel streaming = streamingModel(true, 10);
        GatewayRouteModel route = route(streaming);
        GatewayContextImpl context = new GatewayContextImpl();
        context.setRequest(request());
        context.setHttpMethod("POST");

        FakeHttpClient fake = new FakeHttpClient();
        fake.queue(new CountingPublisher(List.of("a1"), null, CountingPublisher.Mode.STEP));

        RuntimeException mapError = new RuntimeException("mapping failure");
        IGatewayInvocation throwingInvocation = new IGatewayInvocation() {
            @Override
            public ApiRequest<?> proceedOnRequest(ApiRequest<?> request, IGatewayContext svcCtx) {
                return request;
            }

            @Override
            public CompletionStage<ApiResponse<?>> proceedInvoke(ApiRequest<?> request, IGatewayContext svcCtx) {
                return null;
            }

            @Override
            public ApiResponse<?> proceedOnResponse(ApiResponse<?> response, IGatewayContext svcCtx) {
                return response;
            }

            @Override
            public ApiResponse<?> proceedOnError(Throwable exception, IGatewayContext svcCtx) {
                return null;
            }

            @Override
            public void proceedOnStreamStart(ApiRequest<?> request, IGatewayContext svcCtx) {
            }

            @Override
            public Object proceedOnStreamElement(Object element, IGatewayContext svcCtx) {
                throw mapError;
            }

            @Override
            public Object proceedOnStreamError(Throwable exception, IGatewayContext svcCtx) {
                return null;
            }

            @Override
            public void proceedOnStreamComplete(IGatewayContext svcCtx) {
            }
        };

        StreamingProcessor processor = new StreamingProcessor(fake, new MappingProcessor(null));
        processor.executeStreaming(route, request(), context, throwingInvocation);

        StreamingResponse streamingResponse = (StreamingResponse) context.getAttribute(StreamingResponse.class.getName());
        CollectingSubscriber collector = new CollectingSubscriber();
        streamingResponse.getPublisher().subscribe(collector);
        collector.await();

        assertEquals(mapError, collector.error, "映射链错误断流报错");
        assertTrue(fake.publishers.get(0).cancelled.get(),
                "映射 onNext 错误分支必须 cancel 上游（:164-166 一处两态，修复前滞留）");
    }

}
