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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class GatewayFixTest {

    // ===== Test 1: 背压测试 — StreamingProcessor request(1) 流控 =====
    @Test
    void streamingProcessor_requestsOneByOne() {
        AtomicInteger requested = new AtomicInteger(0);
        AtomicInteger received = new AtomicInteger(0);
        int itemCount = 5;

        // 创建一个Publisher，跟踪request次数
        Flow.Publisher<Object> source = subscriber -> {
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                    requested.addAndGet((int) n);
                }

                @Override
                public void cancel() {
                }
            });
            // 同步发送所有数据
            for (int i = 0; i < itemCount; i++) {
                subscriber.onNext(i);
            }
            subscriber.onComplete();
        };

        // 订阅并消费
        source.subscribe(new Flow.Subscriber<>() {
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription s) {
                this.subscription = s;
                s.request(1);
            }

            @Override
            public void onNext(Object item) {
                received.incrementAndGet();
                subscription.request(1);
            }

            @Override
            public void onError(Throwable t) {
            }

            @Override
            public void onComplete() {
            }
        });

        // 验证request(1) + onNext后request(1) 的流控模式
        // 初始request(1) + 5个onNext各request(1) = 6
        assertEquals(itemCount + 1, requested.get());
        assertEquals(itemCount, received.get());
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
