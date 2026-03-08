package io.nop.gateway.conversion.ai;

import io.nop.gateway.conversion.IBackendMessageConverter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class OpenAIMessageConverter implements IBackendMessageConverter {

    @Override
    public Map<String, Object> toBackendRequest(Map<String, Object> request) {
        if (request == null) {
            return Collections.emptyMap();
        }
        return new HashMap<>(request);
    }

    @Override
    public Map<String, Object> toFrontendResponse(Map<String, Object> backendResponse, Map<String, Object> request) {
        if (backendResponse == null) {
            return Collections.emptyMap();
        }
        if (backendResponse.containsKey("choices")) {
            return backendResponse;
        }
        String content = extractContent(backendResponse.get("content"));
        String requestId = ConverterUtils.resolveRequestId(request);
        String model = ConverterUtils.resolveModel(request);
        Map<String, Object> response = ConverterUtils.buildOpenAIResponse(requestId, model, content);
        ConverterUtils.copyIfPresent(response, backendResponse, "usage", "system_fingerprint");
    ConverterUtils.copyChoiceExtras(response, backendResponse, "logprobs");
        Map<String, Object> message = ConverterUtils.toMap(backendResponse.get("message"));
        ConverterUtils.copyMessageExtras(response, message,
                "tool_calls", "tool_call_id", "name", "audio", "annotations", "refusal", "function_call");
        return response;
    }

    @Override
    public Map<String, Object> toFrontendStreamChunk(Map<String, Object> backendDelta, Map<String, Object> request) {
        if (backendDelta == null) {
            return Collections.emptyMap();
        }
        if (backendDelta.containsKey("choices")) {
            return backendDelta;
        }
        String deltaContent = extractContent(backendDelta.get("content"));
        Object finishReasonObj = backendDelta.get("finish_reason");
        String finishReason = finishReasonObj instanceof String ? (String) finishReasonObj : null;
        String requestId = ConverterUtils.resolveRequestId(request);
        String model = ConverterUtils.resolveModel(request);
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
