package io.nop.http.api.aggregator;

import io.nop.api.core.json.JSON;
import io.nop.api.core.util.ApiStringHelper;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.client.IServerEventAggregator;
import io.nop.http.api.client.IServerEventResponse;
import io.nop.http.api.support.DefaultHttpResponse;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Claude 流式响应聚合器，用于聚合 SSE (Server-Sent Events) 格式的流式响应。
 * Claude API 使用事件类型：message_start, content_block_start, content_block_delta, content_block_stop, message_delta, message_stop
 * 只做汇总，不做复杂解析，确保所有原始信息都被保留。
 */
public class ClaudeStreamingEventAggregator implements IServerEventAggregator {
    private final DefaultHttpResponse response = new DefaultHttpResponse();
    private final Map<String, Object> aggregatedBody = new LinkedHashMap<>();

    private final StringBuilder contentBuffer = new StringBuilder();
    private final StringBuilder reasoningContentBuffer = new StringBuilder();

    private String responseId;
    private String model;
    private String stopReason;

    private Integer inputTokens;
    private Integer outputTokens;

    @SuppressWarnings("unchecked")
    @Override
    public void onNext(IServerEventResponse event) {
        if (aggregatedBody.isEmpty()) {
            response.setHttpStatus(event.getHttpStatus());
            response.setHeaders(event.getHeaders());
        }

        String data = event.getData();
        if (ApiStringHelper.isEmpty(data)) {
            return;
        }

        Map<String, Object> json = (Map<String, Object>) JSON.parse(data);
        String eventType = (String) json.get("type");

        if (eventType == null) {
            return;
        }

        switch (eventType) {
            case "message_start":
                handleMessageStart(json);
                break;
            case "content_block_delta":
                handleContentBlockDelta(json);
                break;
            case "message_delta":
                handleMessageDelta(json);
                break;
            case "message_stop":
                break;
            default:
                break;
        }
    }

    @SuppressWarnings("unchecked")
    private void handleMessageStart(Map<String, Object> json) {
        Map<String, Object> message = (Map<String, Object>) json.get("message");
        if (message == null) {
            return;
        }

        if (message.containsKey("id")) {
            responseId = (String) message.get("id");
        }
        if (message.containsKey("model")) {
            model = (String) message.get("model");
        }
    }

    @SuppressWarnings("unchecked")
    private void handleContentBlockDelta(Map<String, Object> json) {
        Integer index = (Integer) json.get("index");
        if (index == null) {
            return;
        }

        Map<String, Object> delta = (Map<String, Object>) json.get("delta");
        if (delta == null) {
            return;
        }

        String deltaType = (String) delta.get("type");

        if ("text_delta".equals(deltaType)) {
            String text = (String) delta.get("text");
            if (text != null) {
                contentBuffer.append(text);
            }
        } else if ("reasoning_delta".equals(deltaType)) {
            String reasoning = (String) delta.get("reasoning_text");
            if (reasoning != null) {
                reasoningContentBuffer.append(reasoning);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void handleMessageDelta(Map<String, Object> json) {
        Map<String, Object> delta = (Map<String, Object>) json.get("delta");
        if (delta == null) {
            return;
        }

        if (delta.containsKey("stop_reason")) {
            stopReason = (String) delta.get("stop_reason");
        }

        Map<String, Object> usage = (Map<String, Object>) delta.get("usage");
        if (usage != null) {
            if (usage.containsKey("input_tokens")) {
                inputTokens = (Integer) usage.get("input_tokens");
            }
            if (usage.containsKey("output_tokens")) {
                outputTokens = (Integer) usage.get("output_tokens");
            }
        }
    }

    @Override
    public IHttpResponse getFinalResult() {
        Map<String, Object> finalBody = new LinkedHashMap<>();

        if (responseId != null) {
            finalBody.put("id", responseId);
        }
        if (model != null) {
            finalBody.put("model", model);
        }

        finalBody.put("type", "message");
        finalBody.put("role", "assistant");

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("type", "text");

        String text = contentBuffer.toString();
        if (!text.isEmpty()) {
            content.put("text", text);
        }

        String reasoning = reasoningContentBuffer.toString();
        if (!reasoning.isEmpty()) {
            content.put("reasoning_content", reasoning);
        }

        finalBody.put("content", content);

        if (stopReason != null) {
            finalBody.put("stop_reason", stopReason);
        }

        if (inputTokens != null || outputTokens != null) {
            Map<String, Object> usage = new LinkedHashMap<>();
            if (inputTokens != null) {
                usage.put("input_tokens", inputTokens);
            }
            if (outputTokens != null) {
                usage.put("output_tokens", outputTokens);
            }
            finalBody.put("usage", usage);
        }

        response.setBody(finalBody);
        return response;
    }
}
