package io.nop.gateway.conversion.ai;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.gateway.conversion.IBackendMessageConverter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClaudeMessageConverter implements IBackendMessageConverter {

    @Override
    @SuppressWarnings("unchecked")
    public ApiRequest<Map<String, Object>> toBackendRequest(ApiRequest<?> request) {
        if (request == null || request.getData() == null) {
            return ApiRequest.build(Collections.emptyMap());
        }
        Map<String, Object> data = (Map<String, Object>) request.getData();
        Map<String, Object> result = new HashMap<>(data);
        List<Map<String, Object>> messages = ConverterUtils.toMapList(data.get("messages"));
        if (!messages.isEmpty()) {
            result.put("messages", convertMessages(messages));
        }
        return ApiRequest.build(result);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ApiResponse<Map<String, Object>> toFrontendResponse(ApiResponse<?> backendResponse, ApiRequest<?> request) {
        if (backendResponse == null || backendResponse.getData() == null) {
            return ApiResponse.success(Collections.emptyMap());
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> backendData = (Map<String, Object>) backendResponse.getData();
        Map<String, Object> requestData = request != null && request.getData() instanceof Map
                ? (Map<String, Object>) request.getData() : Collections.emptyMap();
        String content = extractAssistantContent(backendData);
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
        Map<String, Object> requestData = request != null && request.getData() instanceof Map
                ? (Map<String, Object>) request.getData() : Collections.emptyMap();
        String deltaContent = extractDeltaContent(backendDelta);
        String requestId = ConverterUtils.resolveRequestId(requestData);
        String model = ConverterUtils.resolveModel(requestData);
        Map<String, Object> chunk = ConverterUtils.buildOpenAIChunk(requestId, model, deltaContent, null);
        ConverterUtils.copyIfPresent(chunk, backendDelta, "usage", "system_fingerprint");
        ConverterUtils.copyChoiceExtras(chunk, backendDelta, "logprobs");
        ConverterUtils.mergeDeltaIfPresentExcept(chunk, backendDelta.get("delta"), "text");
        ConverterUtils.ensureDeltaRole(chunk, "assistant");
        return chunk;
    }

    private List<Map<String, Object>> convertMessages(List<Map<String, Object>> messages) {
        List<Map<String, Object>> converted = new ArrayList<>();
        for (Map<String, Object> message : messages) {
            if (message == null) {
                continue;
            }
            Map<String, Object> copy = new HashMap<>(message);
            Object contentObj = message.get("content");
            if (contentObj instanceof List) {
                copy.put("content", contentObj);
            } else if (contentObj instanceof String) {
                String content = (String) contentObj;
                List<Map<String, Object>> blocks = new ArrayList<>();
                Map<String, Object> block = new HashMap<>();
                block.put("type", "text");
                block.put("text", content);
                blocks.add(block);
                copy.put("content", blocks);
            }
            converted.add(copy);
        }
        return converted;
    }

    private String extractAssistantContent(Map<String, Object> backendResponse) {
        List<Map<String, Object>> blocks = ConverterUtils.toMapList(backendResponse.get("content"));
        for (Map<String, Object> block : blocks) {
            if (block == null) {
                continue;
            }
            if ("text".equals(block.get("type"))) {
                return ConverterUtils.asString(block.get("text"));
            }
        }
        return "";
    }

    private String extractDeltaContent(Map<String, Object> backendDelta) {
        if ("content_block_delta".equals(backendDelta.get("type"))) {
            Map<String, Object> delta = ConverterUtils.toMap(backendDelta.get("delta"));
            return ConverterUtils.asString(delta.get("text"));
        }
        return "";
    }
}
