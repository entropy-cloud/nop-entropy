package io.nop.gateway.conversion.ai;

import io.nop.gateway.conversion.IBackendMessageConverter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class OllamaMessageConverter implements IBackendMessageConverter {

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
        Map<String, Object> message = ConverterUtils.toMap(backendResponse.get("message"));
        String content = ConverterUtils.asString(message.get("content"));
        String requestId = ConverterUtils.resolveRequestId(request);
        String model = ConverterUtils.resolveModel(request);
        Map<String, Object> response = ConverterUtils.buildOpenAIResponse(requestId, model, content);
        ConverterUtils.copyIfPresent(response, backendResponse, "usage", "system_fingerprint");
    ConverterUtils.copyChoiceExtras(response, backendResponse, "logprobs");
    ConverterUtils.copyMessageExtras(response, message,
        "tool_calls", "tool_call_id", "name", "audio", "annotations", "refusal", "function_call");
        return response;
    }

    @Override
    public Map<String, Object> toFrontendStreamChunk(Map<String, Object> backendDelta, Map<String, Object> request) {
        if (backendDelta == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> message = ConverterUtils.toMap(backendDelta.get("message"));
        String deltaContent = ConverterUtils.asString(message.get("content"));
        String finishReason = Boolean.TRUE.equals(backendDelta.get("done")) ? "stop" : null;
        String requestId = ConverterUtils.resolveRequestId(request);
        String model = ConverterUtils.resolveModel(request);
        Map<String, Object> chunk = ConverterUtils.buildOpenAIChunk(requestId, model, deltaContent, finishReason);
        ConverterUtils.copyIfPresent(chunk, backendDelta, "usage", "system_fingerprint");
        ConverterUtils.copyChoiceExtras(chunk, backendDelta, "logprobs");
        ConverterUtils.mergeDeltaIfPresent(chunk, backendDelta.get("delta"));
        return chunk;
    }
}
