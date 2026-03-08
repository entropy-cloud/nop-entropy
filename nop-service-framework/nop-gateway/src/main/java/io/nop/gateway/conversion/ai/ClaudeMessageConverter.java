package io.nop.gateway.conversion.ai;

import io.nop.gateway.conversion.IBackendMessageConverter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClaudeMessageConverter implements IBackendMessageConverter {

    @Override
    public Map<String, Object> toBackendRequest(Map<String, Object> request) {
        if (request == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> result = new HashMap<>(request);
        List<Map<String, Object>> messages = ConverterUtils.toMapList(request.get("messages"));
        if (!messages.isEmpty()) {
            result.put("messages", convertMessages(messages));
        }
        return result;
    }

    @Override
    public Map<String, Object> toFrontendResponse(Map<String, Object> backendResponse, Map<String, Object> request) {
        if (backendResponse == null) {
            return Collections.emptyMap();
        }
        String content = extractAssistantContent(backendResponse);
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
        String deltaContent = extractDeltaContent(backendDelta);
        String requestId = ConverterUtils.resolveRequestId(request);
        String model = ConverterUtils.resolveModel(request);
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
