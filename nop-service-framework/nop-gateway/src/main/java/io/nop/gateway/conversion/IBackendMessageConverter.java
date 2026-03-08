package io.nop.gateway.conversion;

import java.util.Map;

public interface IBackendMessageConverter {
    Map<String, Object> toBackendRequest(Map<String, Object> request);

    Map<String, Object> toFrontendResponse(Map<String, Object> backendResponse, Map<String, Object> request);

    Map<String, Object> toFrontendStreamChunk(Map<String, Object> backendDelta, Map<String, Object> request);
}
