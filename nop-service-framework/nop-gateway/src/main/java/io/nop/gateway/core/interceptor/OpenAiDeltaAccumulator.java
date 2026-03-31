package io.nop.gateway.core.interceptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class OpenAiDeltaAccumulator {

    private String id;
    private String model;
    private String object;
    private final StringBuilder contentBuilder = new StringBuilder();
    private final Map<Integer, ToolCallAccumulator> toolCallAccumulators = new LinkedHashMap<>();
    private String finishReason;
    private Map<String, Object> usage;

    OpenAiDeltaAccumulator() {
    }

    @SuppressWarnings("unchecked")
    void accumulate(Map<String, Object> chunk) {
        if (chunk == null) {
            return;
        }
        if (id == null) {
            id = (String) chunk.get("id");
        }
        if (model == null) {
            model = (String) chunk.get("model");
        }
        if (object == null) {
            object = (String) chunk.get("object");
        }
        accumulateFromChoices(chunk);
        if (chunk.containsKey("usage")) {
            usage = (Map<String, Object>) chunk.get("usage");
        }
    }

    @SuppressWarnings("unchecked")
    private void accumulateFromChoices(Map<String, Object> chunk) {
        Object choicesObj = chunk.get("choices");
        if (!(choicesObj instanceof List)) {
            return;
        }
        List<?> choices = (List<?>) choicesObj;
        if (choices.isEmpty()) {
            return;
        }
        Object first = choices.get(0);
        if (!(first instanceof Map)) {
            return;
        }
        Map<String, Object> choice = (Map<String, Object>) first;

        Object finishReasonObj = choice.get("finish_reason");
        if (finishReasonObj instanceof String) {
            this.finishReason = (String) finishReasonObj;
        }

        Object deltaObj = choice.get("delta");
        if (deltaObj instanceof Map) {
            Map<String, Object> delta = (Map<String, Object>) deltaObj;
            Object contentObj = delta.get("content");
            if (contentObj instanceof String) {
                contentBuilder.append((String) contentObj);
            }
            accumulateToolCalls(delta.get("tool_calls"));
        }
    }

    @SuppressWarnings("unchecked")
    private void accumulateToolCalls(Object toolCallsObj) {
        if (!(toolCallsObj instanceof List)) {
            return;
        }
        List<?> toolCalls = (List<?>) toolCallsObj;
        for (Object tcObj : toolCalls) {
            if (!(tcObj instanceof Map)) {
                continue;
            }
            Map<String, Object> tc = (Map<String, Object>) tcObj;
            Integer index = tc.get("index") instanceof Number ? ((Number) tc.get("index")).intValue() : 0;
            ToolCallAccumulator acc = toolCallAccumulators.computeIfAbsent(index, k -> new ToolCallAccumulator());
            if (tc.containsKey("id")) {
                acc.id = (String) tc.get("id");
            }
            Object funcObj = tc.get("function");
            if (funcObj instanceof Map) {
                Map<String, Object> func = (Map<String, Object>) funcObj;
                if (func.containsKey("name")) {
                    acc.name = (String) func.get("name");
                }
                Object argsObj = func.get("arguments");
                if (argsObj instanceof String) {
                    acc.argumentsBuilder.append((String) argsObj);
                }
            }
        }
    }

    Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (id != null) {
            result.put("id", id);
        }
        if (object != null) {
            result.put("object", object);
        }
        if (model != null) {
            result.put("model", model);
        }

        Map<String, Object> delta = new LinkedHashMap<>();
        String content = contentBuilder.toString();
        if (!content.isEmpty()) {
            delta.put("content", content);
        }
        if (!toolCallAccumulators.isEmpty()) {
            List<Map<String, Object>> toolCalls = new ArrayList<>();
            List<Integer> sortedIndexes = new ArrayList<>(toolCallAccumulators.keySet());
            Collections.sort(sortedIndexes);
            for (Integer idx : sortedIndexes) {
                toolCalls.add(toolCallAccumulators.get(idx).toMap(idx));
            }
            delta.put("tool_calls", toolCalls);
        }

        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("delta", delta);
        if (finishReason != null) {
            choice.put("finish_reason", finishReason);
        }

        List<Map<String, Object>> choices = new ArrayList<>();
        choices.add(choice);
        result.put("choices", choices);

        if (usage != null) {
            result.put("usage", usage);
        }
        return result;
    }

    private static class ToolCallAccumulator {
        String id;
        String name;
        final StringBuilder argumentsBuilder = new StringBuilder();

        Map<String, Object> toMap(int index) {
            Map<String, Object> tc = new LinkedHashMap<>();
            tc.put("index", index);
            if (id != null) {
                tc.put("id", id);
            }
            tc.put("type", "function");
            Map<String, Object> func = new LinkedHashMap<>();
            if (name != null) {
                func.put("name", name);
            }
            String args = argumentsBuilder.toString();
            if (!args.isEmpty()) {
                func.put("arguments", args);
            }
            tc.put("function", func);
            return tc;
        }
    }
}
