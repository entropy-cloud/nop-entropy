package io.nop.gateway.core.interceptor;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.gateway.core.context.IGatewayContext;

import java.util.Map;

public class LoggingInterceptor implements IGatewayInterceptor {

    private final IGatewayLogger logger;

    public LoggingInterceptor(IGatewayLogger logger) {
        this.logger = logger;
    }

    @Override
    public ApiRequest<?> onRequest(ApiRequest<?> request, IGatewayContext svcCtx) {
        logger.logRequest(request, svcCtx);
        return request;
    }

    @Override
    public ApiResponse<?> onResponse(ApiResponse<?> response, IGatewayContext svcCtx) {
        logger.logResponse(response, svcCtx);
        return response;
    }

    @Override
    public void onStreamStart(ApiRequest<?> request, IGatewayContext svcCtx) {
        logger.logRequest(request, svcCtx);
        OpenAiDeltaAccumulator accumulator = new OpenAiDeltaAccumulator();
        svcCtx.setAttribute(OpenAiDeltaAccumulator.class.getName(), accumulator);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object onStreamElement(Object element, IGatewayContext svcCtx) {
        if (element instanceof Map) {
            OpenAiDeltaAccumulator accumulator = (OpenAiDeltaAccumulator) svcCtx.getAttribute(OpenAiDeltaAccumulator.class.getName());
            if (accumulator != null) {
                accumulator.accumulate((Map<String, Object>) element);
            }
        }
        return element;
    }

    @Override
    public void onStreamComplete(IGatewayContext svcCtx) {
        OpenAiDeltaAccumulator accumulator = (OpenAiDeltaAccumulator) svcCtx.getAttribute(OpenAiDeltaAccumulator.class.getName());
        if (accumulator != null) {
            logger.logStreamingResponse(accumulator.toMap(), svcCtx);
            svcCtx.setAttribute(OpenAiDeltaAccumulator.class.getName(), "");
        }
    }

    @Override
    public ApiResponse<?> onError(Throwable exception, IGatewayContext svcCtx) {
        logger.logError(exception, svcCtx);
        return null;
    }

    @Override
    public Object onStreamError(Throwable exception, IGatewayContext svcCtx) {
        logger.logError(exception, svcCtx);
        return null;
    }
}
