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
        // 2.3-C8-003（plan 2551 裁定 G）：错误终态清理 accumulator 引用——不再持有聚合缓冲
        // （失控上游的堆驻留随流终止释放，不延续到请求对象生命周期）。
        if (svcCtx != null) {
            // removeAttribute（而非 set null——CHM 属性表不接受 null value）
            svcCtx.removeAttribute(OpenAiDeltaAccumulator.class.getName());
        }
        return null;
    }
}
