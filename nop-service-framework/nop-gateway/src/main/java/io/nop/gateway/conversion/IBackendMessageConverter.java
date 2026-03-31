package io.nop.gateway.conversion;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;

import java.util.Map;

public interface IBackendMessageConverter {
    ApiRequest<?> toBackendRequest(ApiRequest<?> request);

    ApiResponse<?> toFrontendResponse(ApiResponse<?> backendResponse, ApiRequest<?> request);

    Map<String, Object> toFrontendStreamChunk(Map<String, Object> backendDelta, ApiRequest<?> request);
}
