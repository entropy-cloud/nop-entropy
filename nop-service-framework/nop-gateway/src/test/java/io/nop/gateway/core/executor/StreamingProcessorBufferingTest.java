package io.nop.gateway.core.executor;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.ICancelToken;
import io.nop.gateway.core.context.GatewayContextImpl;
import io.nop.gateway.core.context.IGatewayContext;
import io.nop.gateway.core.interceptor.IGatewayInvocation;
import io.nop.gateway.core.streaming.GatewayStreamingConstants;
import io.nop.gateway.core.streaming.IStreamingLifecycleListener;
import io.nop.gateway.core.streaming.IStreamingRetryCallback;
import io.nop.gateway.core.streaming.StreamingResponse;
import io.nop.gateway.model.GatewayInvokeModel;
import io.nop.gateway.model.GatewayRouteModel;
import io.nop.gateway.model.GatewayStreamingModel;
import io.nop.http.api.client.DownloadOptions;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpInputFile;
import io.nop.http.api.client.IHttpOutputFile;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.client.IServerEventResponse;
import io.nop.http.api.client.UploadOptions;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * W7 Phase 2 测试（plan 2026-08-15-1116-3）：nop-gateway 缓冲/重订阅层单元测试。
 * 纯 JUnit 直构模式（GatewayFixTest 先例：手动 Publisher stub + GatewayContextImpl + 匿名
 * IGatewayInvocation，无 VFS 引导，不引用 nop-ai 类型）。
 *
 * <p>覆盖：窗口内失败触发重执行回调重订阅（B-15 不重放钩子）/ 窗口外失败原样转发 /
 * 未配置回调零回归直通 / 参数默认关闭 / 生命周期回调 onFetchStarted/onStreamTerminated
 * （B-2 断言 + F4 abort 双触发去重）/ wrapper 取消委托当前活跃 sub（B-11 断言）/
 * 回调经 context attribute 注入生效（B-12 断言）/ base-url 覆盖（B-14）。
 */
class StreamingProcessorBufferingTest {

    @BeforeAll
    static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ======================= harness =======================

    /** 脚本化流式 publisher：首次 request(1) 时同步发射全部元素 + 终态（确定性，单线程）。
     * {@code silent=true} 时只建立订阅不发射（模拟 in-flight 长流，取消/abort 测试用）。 */
    private static final class ScriptedPublisher implements Flow.Publisher<IServerEventResponse> {
        private final List<String> items;
        private final Throwable error;
        private final boolean silent;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private volatile boolean started;

        ScriptedPublisher(List<String> items, Throwable error, boolean silent) {
            this.items = items;
            this.error = error;
            this.silent = silent;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super IServerEventResponse> subscriber) {
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                    if (started) {
                        return;
                    }
                    started = true;
                    if (silent) {
                        return;
                    }
                    for (String item : items) {
                        if (cancelled.get()) {
                            return;
                        }
                        subscriber.onNext(new FakeServerEvent(item));
                    }
                    if (cancelled.get()) {
                        return;
                    }
                    if (error != null) {
                        subscriber.onError(error);
                    } else {
                        subscriber.onComplete();
                    }
                }

                @Override
                public void cancel() {
                    cancelled.set(true);
                }
            });
        }

        boolean isCancelled() {
            return cancelled.get();
        }
    }

    private static final class FakeHttpClient implements IHttpClient {
        final List<HttpRequest> requests = new ArrayList<>();
        final List<ScriptedPublisher> publishers = new ArrayList<>();
        final java.util.ArrayDeque<Scenario> scenarios = new java.util.ArrayDeque<>();

        static final class Scenario {
            final List<String> items;
            final Throwable error;
            final boolean silent;

            Scenario(List<String> items, Throwable error) {
                this(items, error, false);
            }

            Scenario(List<String> items, Throwable error, boolean silent) {
                this.items = items;
                this.error = error;
                this.silent = silent;
            }
        }

        FakeHttpClient queue(List<String> items, Throwable error) {
            scenarios.add(new Scenario(items, error));
            return this;
        }

        FakeHttpClient queueSilent() {
            scenarios.add(new Scenario(List.of(), null, true));
            return this;
        }

        HttpRequest lastRequest() {
            return requests.get(requests.size() - 1);
        }

        @Override
        public CompletionStage<IHttpResponse> fetchAsync(HttpRequest request, ICancelToken cancelToken) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Flow.Publisher<IServerEventResponse> fetchServerEventFlow(HttpRequest request, ICancelToken cancelToken) {
            requests.add(request);
            Scenario scenario = scenarios.poll();
            ScriptedPublisher publisher = new ScriptedPublisher(
                    scenario != null ? scenario.items : List.of(),
                    scenario != null ? scenario.error : null,
                    scenario != null && scenario.silent);
            publishers.add(publisher);
            return publisher;
        }

        @Override
        public CompletionStage<IHttpResponse> downloadAsync(HttpRequest request, IHttpOutputFile targetFile,
                                                            DownloadOptions options, ICancelToken cancelToken) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletionStage<IHttpResponse> uploadAsync(HttpRequest request, IHttpInputFile inputFile,
                                                          UploadOptions options, ICancelToken cancelToken) {
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
            // 合法 JSON 字符串：映射链 JSON.parse 后还原为纯文本（CoreInitialization 提供 JSON provider）
            return "\"" + text + "\"";
        }

        @Override
        public Map<String, String> getHeaders() {
            return new LinkedHashMap<>();
        }
    }

    private static final class CollectingSubscriber implements Flow.Subscriber<Object> {
        final List<String> texts = new ArrayList<>();
        Throwable error;
        boolean completed;
        private final CompletableFuture<Void> done = new CompletableFuture<>();

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
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

    // ======================= tests =======================

    /** 端到端：窗口内失败 → 重执行回调 → 重订阅（新 fetch）→ 越窗转发；attempt1 缓冲元素丢弃。 */
    @Test
    void inWindowFailureTriggersRetryCallbackAndResubscribes() {
        GatewayStreamingModel streaming = streamingModel(true, 10);
        GatewayRouteModel route = route(streaming);
        GatewayContextImpl context = new GatewayContextImpl();
        ApiRequest<?> request = request();
        context.setRequest(request);
        context.setHttpMethod("POST");

        AtomicInteger callbackCalls = new AtomicInteger();
        AtomicInteger fetchStarts = new AtomicInteger();
        AtomicInteger streamTerminations = new AtomicInteger();
        context.setAttribute(GatewayStreamingConstants.ATTR_RETRY_CALLBACK, (IStreamingRetryCallback) (error, r, req, ctx) -> {
            callbackCalls.incrementAndGet();
            HttpRequest retry = new HttpRequest();
            retry.setUrl("https://gw-b.example.com/v1/chat/completions");
            retry.setBody(req.getData());
            return retry;
        });
        context.setAttribute(GatewayStreamingConstants.ATTR_LIFECYCLE_LISTENER, new IStreamingLifecycleListener() {
            @Override
            public void onFetchStarted() {
                fetchStarts.incrementAndGet();
            }

            @Override
            public void onStreamTerminated(Throwable cause) {
                streamTerminations.incrementAndGet();
            }
        });

        FakeHttpClient fake = new FakeHttpClient();
        // attempt 1: 发射 2 个元素后窗口内失败（bufferSize=10，未越窗）
        RuntimeException fail1 = new RuntimeException("upstream fail attempt 1");
        fake.queue(List.of("a1", "a2"), fail1);
        // attempt 2: 成功流
        fake.queue(List.of("b1", "b2"), null);

        StreamingProcessor processor = new StreamingProcessor(fake, new MappingProcessor(null));
        CompletionStage<ApiResponse<?>> stage = processor.executeStreaming(route, request, context, identityInvocation());
        ApiResponse<?> response = stage.toCompletableFuture().join();
        assertTrue(response.isOk());

        StreamingResponse streamingResponse = (StreamingResponse) context.getAttribute(StreamingResponse.class.getName());
        assertNotNull(streamingResponse);
        CollectingSubscriber collector = new CollectingSubscriber();
        streamingResponse.getPublisher().subscribe(collector);
        collector.await();

        assertEquals(1, callbackCalls.get(), "重执行回调必须被缓冲层调用（接线验证 Minimum Rules #23）");
        assertEquals(2, fetchStarts.get(), "每次 attempt fetch 发起各 +1（重订阅 +1 新 attempt）");
        assertEquals(2, streamTerminations.get(), "attempt1 失败 -1 + attempt2 完成 -1");
        assertTrue(collector.completed, "重订阅后必须正常完成");
        assertNull(collector.error, "重订阅后不得报错: " + collector.error);
        assertEquals(List.of("b1", "b2"), collector.texts, "attempt1 缓冲元素必须丢弃（Major-1），只输出 attempt2");
        assertEquals(2, fake.requests.size(), "两次 fetch（首次 + 重订阅）");
        assertEquals("https://gw-b.example.com/v1/chat/completions", fake.requests.get(1).getUrl(),
                "重订阅使用回调构造的新 HttpRequest");
    }

    /** 窗口内失败但回调返回 null = 不重试，原样转发错误（无静默跳过）。 */
    @Test
    void inWindowFailureCallbackDeclinesForwardsError() {
        GatewayStreamingModel streaming = streamingModel(true, 10);
        GatewayRouteModel route = route(streaming);
        GatewayContextImpl context = new GatewayContextImpl();
        context.setRequest(request());
        context.setHttpMethod("POST");
        context.setAttribute(GatewayStreamingConstants.ATTR_RETRY_CALLBACK,
                (IStreamingRetryCallback) (error, r, req, ctx) -> null);

        FakeHttpClient fake = new FakeHttpClient();
        RuntimeException fail = new RuntimeException("declined retry");
        fake.queue(List.of("a1", "a2"), fail);

        StreamingProcessor processor = new StreamingProcessor(fake, new MappingProcessor(null));
        processor.executeStreaming(route, request(), context, identityInvocation());

        StreamingResponse streamingResponse = (StreamingResponse) context.getAttribute(StreamingResponse.class.getName());
        CollectingSubscriber collector = new CollectingSubscriber();
        streamingResponse.getPublisher().subscribe(collector);
        collector.await();

        assertNotNull(collector.error, "回调拒绝重试必须断流报错");
        assertEquals(fail, collector.error);
        assertFalse(collector.completed);
        assertEquals(1, fake.requests.size(), "不得发起第二次 fetch");
    }

    /** 窗口外失败（已转发数据）→ 不 consult 回调，原样转发错误（需求 §3.2）。 */
    @Test
    void outOfWindowFailureForwardsErrorWithoutCallback() {
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
        RuntimeException fail = new RuntimeException("out of window fail");
        fake.queue(List.of("a1", "a2", "a3"), fail);

        StreamingProcessor processor = new StreamingProcessor(fake, new MappingProcessor(null));
        processor.executeStreaming(route, request(), context, identityInvocation());

        StreamingResponse streamingResponse = (StreamingResponse) context.getAttribute(StreamingResponse.class.getName());
        CollectingSubscriber collector = new CollectingSubscriber();
        streamingResponse.getPublisher().subscribe(collector);
        collector.await();

        assertEquals(0, callbackCalls.get(), "窗口外失败不得 consult 回调（窗口内外判定唯一归属缓冲层）");
        assertEquals(List.of("a1", "a2", "a3"), collector.texts, "已转发元素照常交付");
        assertNotNull(collector.error, "窗口外失败断流报错");
    }

    /** 未配置回调 + 缓冲关闭 = 既有零缓冲直通（零回归，非静默跳过）。 */
    @Test
    void noCallbackNoBufferPassthrough() {
        GatewayStreamingModel streaming = streamingModel(false, 10);
        GatewayRouteModel route = route(streaming);
        GatewayContextImpl context = new GatewayContextImpl();
        context.setRequest(request());
        context.setHttpMethod("POST");

        FakeHttpClient fake = new FakeHttpClient();
        fake.queue(List.of("a1", "a2"), null);

        StreamingProcessor processor = new StreamingProcessor(fake, new MappingProcessor(null));
        processor.executeStreaming(route, request(), context, identityInvocation());

        StreamingResponse streamingResponse = (StreamingResponse) context.getAttribute(StreamingResponse.class.getName());
        assertFalse(streamingResponse.getPublisher() instanceof BufferedStreamingPublisher,
                "无回调无缓冲配置时不得进入缓冲层（零回归直通）");
        CollectingSubscriber collector = new CollectingSubscriber();
        streamingResponse.getPublisher().subscribe(collector);
        collector.await();
        assertTrue(collector.completed);
        assertEquals(List.of("a1", "a2"), collector.texts, "直通：元素原样到达");
    }

    /** 参数默认关闭：bufferEnabled 缺省 false = 零缓冲直通。 */
    @Test
    void bufferDefaultsToDisabled() {
        GatewayStreamingModel streaming = streamingModel(null, null);
        GatewayRouteModel route = route(streaming);
        GatewayContextImpl context = new GatewayContextImpl();
        context.setRequest(request());
        context.setHttpMethod("POST");

        FakeHttpClient fake = new FakeHttpClient();
        fake.queue(List.of("a1"), null);

        StreamingProcessor processor = new StreamingProcessor(fake, new MappingProcessor(null));
        processor.executeStreaming(route, request(), context, identityInvocation());

        StreamingResponse streamingResponse = (StreamingResponse) context.getAttribute(StreamingResponse.class.getName());
        assertFalse(streamingResponse.getPublisher() instanceof BufferedStreamingPublisher,
                "缓冲参数缺省 = 关闭（零回归）");
        CollectingSubscriber collector = new CollectingSubscriber();
        streamingResponse.getPublisher().subscribe(collector);
        collector.await();
        assertTrue(collector.completed);
        assertEquals(List.of("a1"), collector.texts);
    }

    /** 生命周期回调：缓冲关闭时仍触发（计数与缓冲解耦）；abort 双触发 exactly-once（F4）。 */
    @Test
    void lifecycleFiresWithoutBufferAndAbortIsDeduplicated() {
        GatewayStreamingModel streaming = streamingModel(false, 10);
        GatewayRouteModel route = route(streaming);
        GatewayContextImpl context = new GatewayContextImpl();
        context.setRequest(request());
        context.setHttpMethod("POST");
        AtomicInteger fetchStarts = new AtomicInteger();
        AtomicInteger streamTerminations = new AtomicInteger();
        context.setAttribute(GatewayStreamingConstants.ATTR_LIFECYCLE_LISTENER, new IStreamingLifecycleListener() {
            @Override
            public void onFetchStarted() {
                fetchStarts.incrementAndGet();
            }

            @Override
            public void onStreamTerminated(Throwable cause) {
                streamTerminations.incrementAndGet();
            }
        });

        FakeHttpClient fake = new FakeHttpClient();
        fake.queue(List.of("a1"), null);

        StreamingProcessor processor = new StreamingProcessor(fake, new MappingProcessor(null));
        processor.executeStreaming(route, request(), context, identityInvocation());

        StreamingResponse streamingResponse = (StreamingResponse) context.getAttribute(StreamingResponse.class.getName());
        CollectingSubscriber collector = new CollectingSubscriber();
        streamingResponse.getPublisher().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                streamingResponse.setUpstreamSubscription(subscription);
                collector.onSubscribe(subscription);
            }

            @Override
            public void onNext(Object item) {
                collector.onNext(item);
            }

            @Override
            public void onError(Throwable throwable) {
                collector.onError(throwable);
            }

            @Override
            public void onComplete() {
                collector.onComplete();
            }
        });
        collector.await();
        assertTrue(collector.completed);
        // GatewayHttpFilter 在 send 完成时总是 abort()（含正常完成）：重复触发必须去重。
        streamingResponse.abort();
        streamingResponse.abort();

        assertEquals(1, fetchStarts.get());
        assertEquals(1, streamTerminations.get(), "onComplete + abort 双触发必须 exactly-once（F4）");
    }

    /** 静默取消（abort）经 wrapper 委托当前活跃 sub（B-11）+ 生命周期终止（B-2）。 */
    @Test
    void abortCancelsCurrentSubscriptionAndTerminatesLifecycle() {
        GatewayStreamingModel streaming = streamingModel(true, 10);
        GatewayRouteModel route = route(streaming);
        GatewayContextImpl context = new GatewayContextImpl();
        context.setRequest(request());
        context.setHttpMethod("POST");
        AtomicInteger terminations = new AtomicInteger();
        context.setAttribute(GatewayStreamingConstants.ATTR_LIFECYCLE_LISTENER, new IStreamingLifecycleListener() {
            @Override
            public void onFetchStarted() {
            }

            @Override
            public void onStreamTerminated(Throwable cause) {
                terminations.incrementAndGet();
            }
        });

        FakeHttpClient fake = new FakeHttpClient();
        fake.queueSilent();

        StreamingProcessor processor = new StreamingProcessor(fake, new MappingProcessor(null));
        processor.executeStreaming(route, request(), context, identityInvocation());

        StreamingResponse streamingResponse = (StreamingResponse) context.getAttribute(StreamingResponse.class.getName());
        // 模拟 GatewayHttpFilter：订阅触发 fetch（in-flight，silent）后客户端断连 abort()。
        streamingResponse.getPublisher().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                streamingResponse.setUpstreamSubscription(subscription);
                subscription.request(1);
            }

            @Override
            public void onNext(Object item) {
            }

            @Override
            public void onError(Throwable throwable) {
            }

            @Override
            public void onComplete() {
            }
        });
        assertEquals(1, fake.publishers.size(), "fetch 必须已发起");
        streamingResponse.abort();

        assertTrue(fake.publishers.get(0).isCancelled(), "abort 必须经 wrapper 取消当前活跃上游 sub（B-11）");
        assertEquals(1, terminations.get(), "静默取消必须触发 onStreamTerminated（B-2）");
    }

    /** 重订阅后 abort 委托<b>新</b>活跃 sub（B-11：不取消旧 sub）。 */
    @Test
    void abortAfterResubscribeCancelsNewSubscription() {
        GatewayStreamingModel streaming = streamingModel(true, 10);
        GatewayRouteModel route = route(streaming);
        GatewayContextImpl context = new GatewayContextImpl();
        context.setRequest(request());
        context.setHttpMethod("POST");
        context.setAttribute(GatewayStreamingConstants.ATTR_RETRY_CALLBACK,
                (IStreamingRetryCallback) (error, r, req, ctx) -> {
                    HttpRequest retry = new HttpRequest();
                    retry.setUrl("https://gw-b.example.com/v1/chat/completions");
                    return retry;
                });

        FakeHttpClient fake = new FakeHttpClient();
        RuntimeException fail1 = new RuntimeException("attempt 1 fail");
        fake.queue(List.of("a1"), fail1);
        fake.queueSilent();

        StreamingProcessor processor = new StreamingProcessor(fake, new MappingProcessor(null));
        processor.executeStreaming(route, request(), context, identityInvocation());

        StreamingResponse streamingResponse = (StreamingResponse) context.getAttribute(StreamingResponse.class.getName());
        streamingResponse.getPublisher().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                streamingResponse.setUpstreamSubscription(subscription);
                subscription.request(1);
            }

            @Override
            public void onNext(Object item) {
            }

            @Override
            public void onError(Throwable throwable) {
            }

            @Override
            public void onComplete() {
            }
        });
        // 重订阅已同步完成（attempt 2 = silent in-flight）；abort 应取消 attempt 2 的 sub。
        assertEquals(2, fake.publishers.size(), "重订阅必须已发起第二次 fetch");
        streamingResponse.abort();

        assertTrue(fake.publishers.get(1).isCancelled(), "abort 必须取消当前（新）活跃 sub（B-11）");
        assertTrue(fake.publishers.get(0).isCancelled(), "旧 attempt 的 sub 已在重订阅时被 cancel");
    }

    /** 回调经 context attribute 注入生效（B-12）：写入 attribute → 缓冲层 engage。 */
    @Test
    void retryCallbackInjectedViaContextAttribute() {
        GatewayStreamingModel streaming = streamingModel(false, 10);
        GatewayRouteModel route = route(streaming);
        GatewayContextImpl context = new GatewayContextImpl();
        context.setRequest(request());
        context.setHttpMethod("POST");
        context.setAttribute(GatewayStreamingConstants.ATTR_RETRY_CALLBACK,
                (IStreamingRetryCallback) (error, r, req, ctx) -> null);

        FakeHttpClient fake = new FakeHttpClient();
        fake.queue(List.of("a1"), new RuntimeException("fail"));

        StreamingProcessor processor = new StreamingProcessor(fake, new MappingProcessor(null));
        processor.executeStreaming(route, request(), context, identityInvocation());

        StreamingResponse streamingResponse = (StreamingResponse) context.getAttribute(StreamingResponse.class.getName());
        assertInstanceOf(BufferedStreamingPublisher.class, streamingResponse.getPublisher(),
                "注入回调后缓冲层必须 engage（B-12 运行时接线）");
    }

    /** base-url 覆盖（B-14）：request property 覆盖求值 URL 的 base，保留 path+query。 */
    @Test
    void baseUrlOverrideReplacesBase() {
        GatewayStreamingModel streaming = streamingModel(false, 10);
        GatewayRouteModel route = route(streaming);
        GatewayContextImpl context = new GatewayContextImpl();
        ApiRequest<?> request = request();
        request.setProperty(GatewayStreamingConstants.PROP_BASE_URL, "https://gw-b.example.com");
        context.setRequest(request);
        context.setHttpMethod("POST");

        FakeHttpClient fake = new FakeHttpClient();
        fake.queue(List.of("a1"), null);

        StreamingProcessor processor = new StreamingProcessor(fake, new MappingProcessor(null));
        processor.executeStreaming(route, request, context, identityInvocation());

        assertEquals("https://gw-b.example.com/v1/chat/completions",
                fake.lastRequest().getUrl(), "base 替换语义（B-14）：保留 path，替换 authority");
    }

    /** base-url 覆盖零回归：无 property 时保持既有表达式求值。 */
    @Test
    void noBaseUrlOverrideKeepsExpressionUrl() {
        GatewayStreamingModel streaming = streamingModel(false, 10);
        GatewayRouteModel route = route(streaming);
        GatewayContextImpl context = new GatewayContextImpl();
        context.setRequest(request());
        context.setHttpMethod("POST");

        FakeHttpClient fake = new FakeHttpClient();
        fake.queue(List.of("a1"), null);

        StreamingProcessor processor = new StreamingProcessor(fake, new MappingProcessor(null));
        processor.executeStreaming(route, request(), context, identityInvocation());

        assertEquals("https://gw-test.example.com/v1/chat/completions",
                fake.lastRequest().getUrl(), "无覆盖 = 既有 URL 表达式求值（零回归）");
    }
}
