package io.nop.gateway.impl;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.initialize.CoreInitialization;
import io.nop.gateway.GatewayRejectException;
import io.nop.gateway.core.context.GatewayContextImpl;
import io.nop.gateway.core.context.IGatewayContext;
import io.nop.gateway.core.interceptor.IGatewayInterceptor;
import io.nop.gateway.model.GatewayInterceptorModel;
import io.nop.gateway.model.GatewayMatchModel;
import io.nop.gateway.model.GatewayModel;
import io.nop.gateway.model.GatewayRouteModel;
import io.nop.gateway.model.GatewayInvokeModel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * GatewayRejectException 携带的拒绝响应（401/429 + Retry-After）必须原样送达客户端，
 * 不得被通用错误转换（ErrorMessageManager）改写为语义上的 5xx。
 */
class TestGatewayRejectResponse {

    @BeforeAll
    static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    /** 模拟 AiAuth/AiRateLimit 拦截器：onRequest 同步抛出携带拒绝响应的异常。 */
    private static IGatewayInterceptor rejectingInterceptor(int status, String retryAfter) {
        return new IGatewayInterceptor() {
            @Override
            public ApiRequest<?> onRequest(ApiRequest<?> request, IGatewayContext svcCtx) {
                ApiResponse<?> rejected = ApiResponse.buildSuccess(null);
                rejected.setHttpStatus(status);
                if (retryAfter != null) {
                    rejected.setHeader("Retry-After", retryAfter);
                }
                throw new GatewayRejectException(rejected);
            }
        };
    }

    private static GatewayHandler handlerFor(IGatewayInterceptor interceptor) {
        GatewayRouteModel route = new GatewayRouteModel();
        route.setId("reject-route");
        GatewayMatchModel match = new GatewayMatchModel();
        match.setPath("/v1/chat/completions");
        route.setMatch(match);
        GatewayInvokeModel invoke = new GatewayInvokeModel();
        invoke.setServiceName("noop-service");
        route.setInvoke(invoke);

        GatewayInterceptorModel interceptorModel = new GatewayInterceptorModel();
        // interceptors 为 KeyedList（按 id 索引），必须提供非空 id
        interceptorModel.setId("reject-interceptor");
        interceptorModel.setInterceptor(interceptor);

        GatewayModel model = new GatewayModel();
        model.setRoutes(List.of(route));
        model.setInterceptors(List.of(interceptorModel));
        model.init();

        return new GatewayHandler(model, null, null, null, null);
    }

    private static ApiResponse<?> handle(IGatewayInterceptor interceptor) {
        GatewayHandler handler = handlerFor(interceptor);
        ApiRequest<Object> request = new ApiRequest<>();
        request.setData(Map.of());

        GatewayContextImpl context = new GatewayContextImpl();
        context.setRequestPath("/v1/chat/completions");
        context.setRequest(request);

        CompletionStage<ApiResponse<?>> future = handler.handle(request, context);
        assertNotNull(future, "路由必须命中");
        return FutureHelper.syncGet(future);
    }

    @Test
    void authRejection_preserves401Status() {
        ApiResponse<?> response = handle(rejectingInterceptor(401, null));
        assertEquals(401, response.getHttpStatus(),
                "鉴权拒绝的 401 状态码必须原样送达客户端，不得退化为通用错误响应");
    }

    @Test
    void rateLimitRejection_preserves429AndRetryAfter() {
        ApiResponse<?> response = handle(rejectingInterceptor(429, "1"));
        assertEquals(429, response.getHttpStatus(),
                "限流拒绝的 429 状态码必须原样送达客户端");
        assertEquals("1", response.getHeaders().get("Retry-After"),
                "Retry-After 头必须保留（客户端按其实现退避）");
    }
}
