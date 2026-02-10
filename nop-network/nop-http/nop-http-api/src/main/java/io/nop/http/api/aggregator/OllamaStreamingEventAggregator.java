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
 * Ollama 流式响应聚合器，用于聚合 JSON 格式的流式响应。
 * Ollama API 使用 JSON 对象流，每个对象包含 message、done、model 等字段。
 * 只做汇总，不做复杂解析，确保所有原始信息都被保留。
 */
public class OllamaStreamingEventAggregator implements IServerEventAggregator {
    private final DefaultHttpResponse response = new DefaultHttpResponse();
    private final Map<String, Object> aggregatedBody = new LinkedHashMap<>();

    private final StringBuilder contentBuffer = new StringBuilder();
    private final StringBuilder thinkingContentBuffer = new StringBuilder();

    private String model;
    private String finishReason;

    private Integer promptEvalCount;
    private Integer evalCount;
    private Long totalDuration;
    private Long loadDuration;
    private Long promptEvalDuration;
    private Long evalDuration;

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

        if (json.containsKey("model") && model == null) {
            model = (String) json.get("model");
        }

        if (json.containsKey("message")) {
            Map<String, Object> message = (Map<String, Object>) json.get("message");
            if (message != null) {
                processMessage(message);
            }
        }

        if (json.containsKey("done") && Boolean.TRUE.equals(json.get("done"))) {
            if (json.containsKey("done_reason")) {
                finishReason = (String) json.get("done_reason");
            } else {
                finishReason = "stop";
            }

            if (json.containsKey("prompt_eval_count")) {
                promptEvalCount = ((Number) json.get("prompt_eval_count")).intValue();
            }
            if (json.containsKey("eval_count")) {
                evalCount = ((Number) json.get("eval_count")).intValue();
            }
            if (json.containsKey("total_duration")) {
                totalDuration = ((Number) json.get("total_duration")).longValue();
            }
            if (json.containsKey("load_duration")) {
                loadDuration = ((Number) json.get("load_duration")).longValue();
            }
            if (json.containsKey("prompt_eval_duration")) {
                promptEvalDuration = ((Number) json.get("prompt_eval_duration")).longValue();
            }
            if (json.containsKey("eval_duration")) {
                evalDuration = ((Number) json.get("eval_duration")).longValue();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void processMessage(Map<String, Object> message) {
        if (message.containsKey("content")) {
            String content = (String) message.get("content");
            if (content != null) {
                contentBuffer.append(content);
            }
        }

        if (message.containsKey("thinking")) {
            String thinking = (String) message.get("thinking");
            if (thinking != null) {
                thinkingContentBuffer.append(thinking);
            }
        }

        if (message.containsKey("tool_calls")) {
            Map<String, Object> toolCalls = (Map<String, Object>) message.get("tool_calls");
            if (toolCalls != null && !toolCalls.isEmpty()) {
                aggregatedBody.put("tool_calls", toolCalls);
            }
        }
    }

    @Override
    public IHttpResponse getFinalResult() {
        Map<String, Object> finalBody = new LinkedHashMap<>();

        if (model != null) {
            finalBody.put("model", model);
        }

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");

        String content = contentBuffer.toString();
        if (!content.isEmpty()) {
            message.put("content", content);
        }

        String thinking = thinkingContentBuffer.toString();
        if (!thinking.isEmpty()) {
            message.put("thinking", thinking);
        }

        if (aggregatedBody.containsKey("tool_calls")) {
            message.put("tool_calls", aggregatedBody.get("tool_calls"));
        }

        if (finishReason != null) {
            message.put("finish_reason", finishReason);
        }

        finalBody.put("message", message);

        if (promptEvalCount != null || evalCount != null) {
            Map<String, Object> usage = new LinkedHashMap<>();
            if (promptEvalCount != null) {
                usage.put("prompt_eval_count", promptEvalCount);
            }
            if (evalCount != null) {
                usage.put("eval_count", evalCount);
            }
            finalBody.put("usage", usage);
        }

        if (totalDuration != null || loadDuration != null ||
            promptEvalDuration != null || evalDuration != null) {
            Map<String, Object> stats = new LinkedHashMap<>();
            if (totalDuration != null) {
                stats.put("total_duration", totalDuration);
            }
            if (loadDuration != null) {
                stats.put("load_duration", loadDuration);
            }
            if (promptEvalDuration != null) {
                stats.put("prompt_eval_duration", promptEvalDuration);
            }
            if (evalDuration != null) {
                stats.put("eval_duration", evalDuration);
            }
            finalBody.put("stats", stats);
        }

        response.setBody(finalBody);
        return response;
    }
}
