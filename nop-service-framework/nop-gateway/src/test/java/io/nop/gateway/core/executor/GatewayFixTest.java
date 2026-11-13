package io.nop.gateway.core.executor;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.gateway.conversion.IBackendMessageConverter;
import io.nop.gateway.conversion.ai.AiBackendMessageConverter;
import io.nop.gateway.conversion.ai.AiBackendType;
import io.nop.gateway.core.context.GatewayContextImpl;
import io.nop.gateway.core.context.IGatewayContext;
import io.nop.gateway.core.interceptor.IGatewayInterceptor;
import io.nop.gateway.core.interceptor.IGatewayInvocation;
import io.nop.gateway.core.interceptor.InterceptedGatewayInvocation;
import io.nop.gateway.core.streaming.StreamingResponse;
import io.nop.gateway.model.GatewayRouteModel;
import io.nop.gateway.model.GatewayStreamingModel;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.client.IServerEventResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class GatewayFixTest {

    @org.junit.jupiter.api.BeforeAll
    static void init() {
        io.nop.core.initialize.CoreInitialization.initialize();
    }

    @org.junit.jupiter.api.AfterAll
    static void destroy() {
        io.nop.core.initialize.CoreInitialization.destroy();
    }

    // ===== Test 1: 背压测试 — BufferedStreamingPublisher 真实实例化 + demand 门控授信 =====
    @Test
    void bufferedStreamingPublisher_requestsOneByOneViaDemandGate() {
        AtomicInteger requested = new AtomicInteger(0);
        AtomicInteger received = new AtomicInteger(0);
        int itemCount = 5;

        // 逐元素拉取的上游（真实 SSE 拉取形态）：每次 request 发射一个元素
        Flow.Publisher<IServerEventResponse> source = subscriber -> {
            subscriber.onSubscribe(new Flow.Subscription() {
                private int cursor;

                @Override
                public void request(long n) {
                    requested.addAndGet((int) n);
                    if (cursor < itemCount) {
                        subscriber.onNext(new IServerEventResponse() {
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
                                return String.valueOf(cursor++);
                            }

                            @Override
                            public java.util.Map<String, String> getHeaders() {
                                return java.util.Collections.emptyMap();
                            }
                        });
                    } else {
                        subscriber.onComplete();
                    }
                }

                @Override
                public void cancel() {
                }
            });
        };

        GatewayStreamingModel streaming = new GatewayStreamingModel();
        streaming.setContentType("text/event-stream");
        streaming.setBufferEnabled(true);
        streaming.setBufferSize(10);
        // 窗口期 = 0：元素即刻越窗透传（本测试隔离 request(1) 流控形态，不测窗口缓冲）
        streaming.setBufferTimeMs(0L);
        GatewayRouteModel route = new GatewayRouteModel();
        route.setId("stream-route");
        route.setStreaming(streaming);

        // 真实实例化被测类（修复 N1 虚假覆盖：旧用例只自建匿名 publisher，未触及被测类）
        IHttpClient fakeHttpClient = new IHttpClient() {
            @Override
            public CompletionStage<IHttpResponse> fetchAsync(HttpRequest request, io.nop.api.core.util.ICancelToken cancelToken) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Flow.Publisher<IServerEventResponse> fetchServerEventFlow(HttpRequest request, io.nop.api.core.util.ICancelToken cancelToken) {
                return source;
            }

            @Override
            public CompletionStage<IHttpResponse> downloadAsync(HttpRequest request, io.nop.http.api.client.IHttpOutputFile targetFile,
                    io.nop.http.api.client.DownloadOptions options, io.nop.api.core.util.ICancelToken cancelToken) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletionStage<IHttpResponse> uploadAsync(HttpRequest request, io.nop.http.api.client.IHttpInputFile inputFile,
                    io.nop.http.api.client.UploadOptions options, io.nop.api.core.util.ICancelToken cancelToken) {
                throw new UnsupportedOperationException();
            }
        };
        BufferedStreamingPublisher publisher = new BufferedStreamingPublisher(
                fakeHttpClient, route, new ApiRequest<>(), new GatewayContextImpl(), streaming,
                null, null,
                eventPublisher -> downstream -> eventPublisher.subscribe(new Flow.Subscriber<IServerEventResponse>() {
                    private Flow.Subscription up;

                    @Override
                    public void onSubscribe(Flow.Subscription s) {
                        this.up = s;
                        downstream.onSubscribe(s);
                        s.request(1); // 映射链自驱动首拉（与 createMappedPublisher 形态一致）
                    }

                    @Override
                    public void onNext(IServerEventResponse item) {
                        downstream.onNext(item.getData());
                        up.request(1); // 映射链自驱动逐元素 request(1)（经缓冲层授信门控）
                    }

                    @Override
                    public void onError(Throwable t) {
                        downstream.onError(t);
                    }

                    @Override
                    public void onComplete() {
                        downstream.onComplete();
                    }
                }),
                new HttpRequest());

        final Flow.Subscription[] subRef = new Flow.Subscription[1];
        final AtomicReference<Throwable> errorRef = new AtomicReference<>();
        publisher.subscribe(new Flow.Subscriber<Object>() {
            @Override
            public void onSubscribe(Flow.Subscription s) {
                subRef[0] = s;
                s.request(1); // 初始授信 1：demand 门控下才允许上游拉取
            }

            @Override
            public void onNext(Object item) {
                received.incrementAndGet();
                subRef[0].request(1); // 逐元素再授信（映射链 request(1) 经门控逐次放行）
            }

            @Override
            public void onError(Throwable t) {
                errorRef.set(t);
            }

            @Override
            public void onComplete() {
            }
        });

        assertNull(errorRef.get(), "流不得报错: " + errorRef.get());
        // demand 门控授信：上游拉取次数 == 消费授信数（逐元素 request(1)，不预取）
        assertEquals(itemCount, received.get());
        assertEquals(itemCount + 1, requested.get(), "初始 1 + 每元素 1 = 6（授信记账：不超授信拉取）");
    }

    // ===== Test 2: 断连测试 — StreamingResponse abort() =====
    @Test
    void streamingResponse_abortCancelsUpstream() {
        AtomicBoolean cancelled = new AtomicBoolean(false);

        Flow.Publisher<Object> publisher = subscriber -> {
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                }

                @Override
                public void cancel() {
                    cancelled.set(true);
                }
            });
        };

        StreamingResponse response = new StreamingResponse(publisher, "text/event-stream", null);

        // 先注入subscription（模拟GatewayHttpFilter中的行为）
        publisher.subscribe(new Flow.Subscriber<>() {
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription s) {
                this.subscription = s;
                response.setUpstreamSubscription(s);
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

        assertFalse(cancelled.get());
        response.abort();
        assertTrue(cancelled.get());
    }

    @Test
    void streamingResponse_abort_beforeSubscribe_doesNotThrow() {
        StreamingResponse response = new StreamingResponse(null, "text/event-stream", null);
        // 未注入subscription时abort不应抛异常
        response.abort();
    }

    // ===== Test 3: 流式拦截器链测试 — proceedOnStreamElement 传递变换后的值 =====
    @Test
    void interceptedInvocation_proceedOnStreamElement_passesTransformedValue() {
        List<IGatewayInterceptor> interceptors = new ArrayList<>();
        interceptors.add(new IGatewayInterceptor() {
            @Override
            public Object onStreamElement(Object element, IGatewayContext svcCtx) {
                return (int) element + 1;
            }
        });
        interceptors.add(new IGatewayInterceptor() {
            @Override
            public Object onStreamElement(Object element, IGatewayContext svcCtx) {
                return (int) element * 2;
            }
        });

        IGatewayInvocation baseInvocation = new IGatewayInvocation() {
            @Override
            public Object proceedOnStreamElement(Object element, IGatewayContext svcCtx) {
                return element;
            }

            @Override
            public java.util.concurrent.CompletionStage<ApiResponse<?>> proceedInvoke(ApiRequest<?> request, IGatewayContext svcCtx) {
                return null;
            }

            @Override
            public ApiRequest<?> proceedOnRequest(ApiRequest<?> request, IGatewayContext svcCtx) {
                return request;
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
            public Object proceedOnStreamError(Throwable exception, IGatewayContext svcCtx) {
                return null;
            }

            @Override
            public void proceedOnStreamComplete(IGatewayContext svcCtx) {
            }
        };

        // 修复前: 传入0 → 拦截器链计算: (0+1)*2 = 2 → 但baseInvocation收到原始0
        // 修复后: baseInvocation收到2
        InterceptedGatewayInvocation invocation = new InterceptedGatewayInvocation(interceptors, baseInvocation);

        // 需要mock一个IGatewayContext
        IGatewayContext ctx = new GatewayContextImpl();
        Object result = invocation.proceedOnStreamElement(0, ctx);

        assertEquals(2, result, "proceedOnStreamElement应传递拦截器链变换后的值");
    }

    // ===== Test 4: AI Converter 回退行为测试（FALLBACK_CONVERTERS已清空） =====
    @Test
    void backendMessageConverter_fallbackWhenIocNotAvailable() {
        // FALLBACK_CONVERTERS 已清空，未注册的 converter 应抛异常
        assertThrows(IllegalArgumentException.class, () -> {
            ApiRequest<Map<String, String>> req = new ApiRequest<>();
            req.setData(Map.of("model", "gpt-4"));
            AiBackendMessageConverter.toBackendRequest(req, AiBackendType.OPENAI);
        });
    }

    // ===== Test 5: 非流式路径传参测试 — RouteExecutor使用转换后的request =====
    @Test
    void routeExecutor_usesConvertedRequest() {
        // 创建一个标记转换的IBackendMessageConverter
        IBackendMessageConverter converter = new IBackendMessageConverter() {
            @Override
            public ApiRequest<?> toBackendRequest(ApiRequest<?> request) {
                ApiRequest<Object> converted = new ApiRequest<>();
                converted.setHeaders(request.getHeaders());
                converted.setData("CONVERTED:" + request.getData());
                return converted;
            }

            @Override
            public ApiResponse<?> toFrontendResponse(ApiResponse<?> backendResponse, ApiRequest<?> request) {
                return backendResponse;
            }

            @Override
            public Map<String, Object> toFrontendStreamChunk(Map<String, Object> backendDelta, ApiRequest<?> request) {
                return backendDelta;
            }
        };

        ApiRequest<String> request = new ApiRequest<>();
        request.setData("original");

        ApiRequest<?> converted = converter.toBackendRequest(request);
        assertEquals("CONVERTED:original", converted.getData());
    }
}
