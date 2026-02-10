package io.nop.http.api.aggregator;

import io.nop.api.core.json.JSON;
import io.nop.api.core.util.ApiStringHelper;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.client.IServerEventAggregator;
import io.nop.http.api.client.IServerEventResponse;
import io.nop.http.api.support.DefaultHttpResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 流式响应聚合器，用于聚合 SSE (Server-Sent Events) 格式的流式响应。
 * 只做汇总，不做复杂解析，确保所有原始信息都被保留。
 */
public class OpenAIStreamingEventAggregator implements IServerEventAggregator {
    private final DefaultHttpResponse response = new DefaultHttpResponse();
    private final Map<String, Object> aggregatedBody = new LinkedHashMap<>();

    private final StringBuilder contentBuffer = new StringBuilder();
    private final StringBuilder reasoningContentBuffer = new StringBuilder();

    private String responseId;
    private String model;
    private String finishReason;

    private final List<Map<String, Object>> mergedToolCalls = new java.util.ArrayList<>();

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

        data = data.trim();
        if (data.equals("DONE") || data.equals("[DONE]"))
            return;

        Map<String, Object> json = (Map<String, Object>) JSON.parse(data);

        if (json.containsKey("id") && responseId == null) {
            responseId = (String) json.get("id");
        }

        if (json.containsKey("model") && model == null) {
            model = (String) json.get("model");
        }

        if (json.containsKey("choices")) {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) json.get("choices");
            if (choices != null && !choices.isEmpty()) {
                processDelta(choices.get(0));
            }
        } else if (json.containsKey("delta")) {
            processDelta(json);
        }

        if (json.containsKey("usage")) {
            aggregatedBody.put("usage", json.get("usage"));
        }
    }

    @SuppressWarnings("unchecked")
    private void processDelta(Map<String, Object> data) {
        Map<String, Object> delta = null;
        Map<String, Object> choice = null;

        if (data.containsKey("delta")) {
            delta = (Map<String, Object>) data.get("delta");
        } else if (data.containsKey("finish_reason")) {
            finishReason = (String) data.get("finish_reason");
            return;
        } else {
            choice = data;
            if (choice.containsKey("delta")) {
                delta = (Map<String, Object>) choice.get("delta");
            }
            if (choice.containsKey("finish_reason")) {
                finishReason = (String) choice.get("finish_reason");
            }
        }

        if (delta == null) {
            return;
        }

        if (delta.containsKey("content")) {
            String content = (String) delta.get("content");
            if (content != null) {
                contentBuffer.append(content);
            }
        }

        if (delta.containsKey("reasoning_content")) {
            String reasoning = (String) delta.get("reasoning_content");
            if (reasoning != null) {
                reasoningContentBuffer.append(reasoning);
            }
        }

        if (delta.containsKey("tool_calls")) {
            mergeToolCalls((List<Map<String, Object>>) delta.get("tool_calls"));
        }
    }

    @SuppressWarnings("unchecked")
    private void mergeToolCalls(List<Map<String, Object>> deltaCalls) {
        for (Map<String, Object> deltaCall : deltaCalls) {
            Integer index = (Integer) deltaCall.get("index");
            if (index == null) {
                continue;
            }

            while (mergedToolCalls.size() <= index) {
                mergedToolCalls.add(new LinkedHashMap<>());
            }

            Map<String, Object> merged = mergedToolCalls.get(index);

            if (deltaCall.containsKey("id")) {
                merged.put("id", deltaCall.get("id"));
            }
            if (deltaCall.containsKey("type")) {
                merged.put("type", deltaCall.get("type"));
            }

            if (deltaCall.containsKey("function")) {
                Map<String, Object> deltaFunc = (Map<String, Object>) deltaCall.get("function");
                Map<String, Object> mergedFunc = (Map<String, Object>) merged.computeIfAbsent("function", k -> new LinkedHashMap<>());

                if (deltaFunc.containsKey("name")) {
                    mergedFunc.put("name", deltaFunc.get("name"));
                }

                if (deltaFunc.containsKey("arguments")) {
                    String existingArgs = (String) mergedFunc.get("arguments");
                    String deltaArgs = (String) deltaFunc.get("arguments");
                    mergedFunc.put("arguments", existingArgs == null ? deltaArgs : existingArgs + deltaArgs);
                }
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

        Map<String, Object> choice = new LinkedHashMap<>();
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");

        String content = contentBuffer.toString();
        if (!content.isEmpty()) {
            message.put("content", content);
        }

        String reasoning = reasoningContentBuffer.toString();
        if (!reasoning.isEmpty()) {
            message.put("reasoning_content", reasoning);
        }

        if (!mergedToolCalls.isEmpty()) {
            message.put("tool_calls", mergedToolCalls);
        }

        choice.put("index", 0);
        choice.put("message", message);

        if (finishReason != null) {
            choice.put("finish_reason", finishReason);
        } else if (aggregatedBody.containsKey("usage")) {
            choice.put("finish_reason", "stop");
        }

        finalBody.put("choices", List.of(choice));

        if (aggregatedBody.containsKey("usage")) {
            finalBody.put("usage", aggregatedBody.get("usage"));
        }

        response.setBody(finalBody);
        return response;
    }
}
