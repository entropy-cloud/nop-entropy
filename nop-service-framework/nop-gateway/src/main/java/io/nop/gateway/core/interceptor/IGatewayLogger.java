package io.nop.gateway.core.interceptor;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.gateway.core.context.IGatewayContext;

public interface IGatewayLogger {

    void logRequest(ApiRequest<?> request, IGatewayContext ctx);

    void logResponse(ApiResponse<?> response, IGatewayContext ctx);

    void logStreamingResponse(Object aggregatedResponse, IGatewayContext ctx);

    void logError(Throwable exception, IGatewayContext ctx);
}
