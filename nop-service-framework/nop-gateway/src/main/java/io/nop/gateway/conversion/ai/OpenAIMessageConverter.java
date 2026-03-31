package io.nop.gateway.conversion.ai;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.gateway.conversion.IBackendMessageConverter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class OpenAIMessageConverter implements IBackendMessageConverter {

    @Override
    @SuppressWarnings("unchecked")
    public ApiRequest<Map<String, Object>> toBackendRequest(ApiRequest<?> request) {
        if (request == null || request.getData() == null) {
            return ApiRequest.build(Collections.emptyMap());
        }
        Map<String, Object> data = (Map<String, Object>) request.getData();
        return ApiRequest.build(new HashMap<>(data));
    }

    @Override
    @SuppressWarnings("unchecked")
    public ApiResponse<Map<String, Object>> toFrontendResponse(ApiResponse<?> backendResponse, ApiRequest<?> request) {
        if (backendResponse == null || backendResponse.getData() == null) {
            return ApiResponse.success(Collections.emptyMap());
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> backendData = (Map<String, Object>) backendResponse.getData();
        if (backendData.containsKey("choices")) {
            return ApiResponse.success(backendData);
        }
        Map<String, Object> requestData = request != null && request.getData() instanceof Map
                ? (Map<String, Object>) request.getData() : Collections.emptyMap();
        String content = extractContent(backendData.get("content"));
        String requestId = ConverterUtils.resolveRequestId(requestData);
        String model = ConverterUtils.resolveModel(requestData);
        Map<String, Object> response = ConverterUtils.buildOpenAIResponse(requestId, model, content);
        ConverterUtils.copyIfPresent(response, backendData, "usage", "system_fingerprint");
        ConverterUtils.copyChoiceExtras(response, backendData, "logprobs");
        Map<String, Object> message = ConverterUtils.toMap(backendData.get("message"));
        ConverterUtils.copyMessageExtras(response, message,
                "tool_calls", "tool_call_id", "name", "audio", "annotations", "refusal", "function_call");
        return ApiResponse.success(response);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> toFrontendStreamChunk(Map<String, Object> backendDelta, ApiRequest<?> request) {
        if (backendDelta == null) {
            return Collections.emptyMap();
        }
        if (backendDelta.containsKey("choices")) {
            return backendDelta;
        }
        Map<String, Object> requestData = request != null && request.getData() instanceof Map
                ? (Map<String, Object>) request.getData() : Collections.emptyMap();
        String deltaContent = extractContent(backendDelta.get("content"));
        Object finishReasonObj = backendDelta.get("finish_reason");
        String finishReason = finishReasonObj instanceof String ? (String) finishReasonObj : null;
        String requestId = ConverterUtils.resolveRequestId(requestData);
        String model = ConverterUtils.resolveModel(requestData);
        Map<String, Object> chunk = ConverterUtils.buildOpenAIChunk(requestId, model, deltaContent, finishReason);
        ConverterUtils.copyIfPresent(chunk, backendDelta, "usage", "system_fingerprint");
        ConverterUtils.copyChoiceExtras(chunk, backendDelta, "logprobs");
        ConverterUtils.mergeDeltaIfPresent(chunk, backendDelta.get("delta"));
        return chunk;
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Object contentObj) {
        if (contentObj instanceof String) {
            return (String) contentObj;
        }
        if (contentObj instanceof java.util.List) {
            java.util.List<?> blocks = (java.util.List<?>) contentObj;
            for (Object blockObj : blocks) {
                if (blockObj instanceof java.util.Map) {
                    java.util.Map<String, Object> block = (java.util.Map<String, Object>) blockObj;
                    if ("text".equals(block.get("type"))) {
                        return ConverterUtils.asString(block.get("text"));
                    }
                }
            }
        }
        return "";
    }
}
