package io.nop.gateway.conversion.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ConverterUtils {
    static final String OPENAI_CHAT_COMPLETION = "chat.completion";
    static final String OPENAI_CHAT_COMPLETION_CHUNK = "chat.completion.chunk";

    private ConverterUtils() {
    }

    static String asString(Object value) {
        return value instanceof String ? (String) value : "";
    }

    static String resolveRequestId(Map<String, Object> request) {
        if (request == null) {
            return "";
        }
        String id = asString(request.get("id"));
        if (!id.isEmpty()) {
            return id;
        }
        id = asString(request.get("request_id"));
        if (!id.isEmpty()) {
            return id;
        }
        return asString(request.get("requestId"));
    }

    static String resolveModel(Map<String, Object> request) {
        if (request == null) {
            return "";
        }
        return asString(request.get("model"));
    }

    static Map<String, Object> buildOpenAIResponse(String requestId, String model, String assistantContent) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", requestId == null ? "" : requestId);
        response.put("object", OPENAI_CHAT_COMPLETION);
        response.put("model", model == null ? "" : model);

        Map<String, Object> message = new HashMap<>();
        message.put("role", "assistant");
        message.put("content", assistantContent == null ? "" : assistantContent);

        Map<String, Object> choice = new HashMap<>();
        choice.put("index", 0);
        choice.put("message", message);
        choice.put("finish_reason", "stop");

        List<Map<String, Object>> choices = new ArrayList<>();
        choices.add(choice);
        response.put("choices", choices);
        return response;
    }

    static Map<String, Object> buildOpenAIChunk(String requestId, String model, String deltaContent, String finishReason) {
        Map<String, Object> delta = new HashMap<>();
        if (deltaContent != null && !deltaContent.isEmpty()) {
            delta.put("content", deltaContent);
        }

        Map<String, Object> choice = new HashMap<>();
        choice.put("index", 0);
        choice.put("delta", delta);
        choice.put("finish_reason", finishReason);

        List<Map<String, Object>> choices = new ArrayList<>();
        choices.add(choice);

        Map<String, Object> chunk = new HashMap<>();
        chunk.put("id", requestId == null ? "" : requestId);
        chunk.put("object", OPENAI_CHAT_COMPLETION_CHUNK);
        chunk.put("model", model == null ? "" : model);
        chunk.put("choices", choices);
        return chunk;
    }

    static void copyIfPresent(Map<String, Object> target, Map<String, Object> source, String... keys) {
        if (target == null || source == null || keys == null) {
            return;
        }
        for (String key : keys) {
            if (source.containsKey(key)) {
                target.put(key, source.get(key));
            }
        }
    }

    static void copyMessageExtras(Map<String, Object> response, Map<String, Object> sourceMessage, String... keys) {
        if (response == null || sourceMessage == null) {
            return;
        }
        Map<String, Object> message = getFirstChoiceMessage(response);
        if (message == null) {
            return;
        }
        if (keys == null || keys.length == 0) {
            for (Map.Entry<String, Object> entry : sourceMessage.entrySet()) {
                String key = entry.getKey();
                if (!"content".equals(key) && !"role".equals(key)) {
                    message.putIfAbsent(key, entry.getValue());
                }
            }
            return;
        }
        for (String key : keys) {
            if (sourceMessage.containsKey(key)) {
                message.putIfAbsent(key, sourceMessage.get(key));
            }
        }
    }

    static void copyChoiceExtras(Map<String, Object> response, Map<String, Object> source, String... keys) {
        if (response == null || source == null) {
            return;
        }
        Map<String, Object> choice = getFirstChoice(response);
        if (choice == null) {
            return;
        }
        if (keys == null || keys.length == 0) {
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                String key = entry.getKey();
                if (!"message".equals(key) && !"index".equals(key)) {
                    choice.putIfAbsent(key, entry.getValue());
                }
            }
            return;
        }
        for (String key : keys) {
            if (source.containsKey(key)) {
                choice.putIfAbsent(key, source.get(key));
            }
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> getFirstChoice(Map<String, Object> response) {
        Object choicesObj = response.get("choices");
        if (!(choicesObj instanceof List)) {
            return null;
        }
        List<?> choices = (List<?>) choicesObj;
        if (choices.isEmpty()) {
            return null;
        }
        Object first = choices.get(0);
        if (first instanceof Map) {
            return (Map<String, Object>) first;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> getFirstChoiceMessage(Map<String, Object> response) {
        Object choicesObj = response.get("choices");
        if (!(choicesObj instanceof List)) {
            return null;
        }
        List<?> choices = (List<?>) choicesObj;
        if (choices.isEmpty()) {
            return null;
        }
        Object first = choices.get(0);
        if (!(first instanceof Map)) {
            return null;
        }
        Map<String, Object> choice = (Map<String, Object>) first;
        Object messageObj = choice.get("message");
        if (messageObj instanceof Map) {
            return (Map<String, Object>) messageObj;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    static void mergeDeltaIfPresent(Map<String, Object> chunk, Object deltaObj) {
        if (chunk == null || deltaObj == null) {
            return;
        }
        Object choicesObj = chunk.get("choices");
        if (!(choicesObj instanceof List) || ((List<?>) choicesObj).isEmpty()) {
            return;
        }
        Object first = ((List<?>) choicesObj).get(0);
        if (!(first instanceof Map)) {
            return;
        }
        Map<String, Object> choice = (Map<String, Object>) first;
        Object deltaTargetObj = choice.get("delta");
        if (!(deltaTargetObj instanceof Map)) {
            return;
        }
        Map<String, Object> deltaTarget = (Map<String, Object>) deltaTargetObj;
        if (deltaObj instanceof Map) {
            Map<String, Object> deltaSource = (Map<String, Object>) deltaObj;
            for (Map.Entry<String, Object> entry : deltaSource.entrySet()) {
                deltaTarget.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
    }

    @SuppressWarnings("unchecked")
    static void mergeDeltaIfPresentExcept(Map<String, Object> chunk, Object deltaObj, String... excludedKeys) {
        if (chunk == null || deltaObj == null) {
            return;
        }
        Object choicesObj = chunk.get("choices");
        if (!(choicesObj instanceof List) || ((List<?>) choicesObj).isEmpty()) {
            return;
        }
        Object first = ((List<?>) choicesObj).get(0);
        if (!(first instanceof Map)) {
            return;
        }
        Map<String, Object> choice = (Map<String, Object>) first;
        Object deltaTargetObj = choice.get("delta");
        if (!(deltaTargetObj instanceof Map)) {
            return;
        }
        Map<String, Object> deltaTarget = (Map<String, Object>) deltaTargetObj;
        if (deltaObj instanceof Map) {
            Map<String, Object> deltaSource = (Map<String, Object>) deltaObj;
            for (Map.Entry<String, Object> entry : deltaSource.entrySet()) {
                if (!isExcluded(entry.getKey(), excludedKeys)) {
                    deltaTarget.putIfAbsent(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    static void ensureDeltaRole(Map<String, Object> chunk, String role) {
        if (chunk == null || role == null || role.isEmpty()) {
            return;
        }
        Object choicesObj = chunk.get("choices");
        if (!(choicesObj instanceof List) || ((List<?>) choicesObj).isEmpty()) {
            return;
        }
        Object first = ((List<?>) choicesObj).get(0);
        if (!(first instanceof Map)) {
            return;
        }
        Map<String, Object> choice = (Map<String, Object>) first;
        Object deltaTargetObj = choice.get("delta");
        if (!(deltaTargetObj instanceof Map)) {
            return;
        }
        Map<String, Object> deltaTarget = (Map<String, Object>) deltaTargetObj;
        deltaTarget.putIfAbsent("role", role);
    }

    private static boolean isExcluded(String key, String... excludedKeys) {
        if (key == null || excludedKeys == null) {
            return false;
        }
        for (String excluded : excludedKeys) {
            if (key.equals(excluded)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> toMap(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> toMapList(Object value) {
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            List<Map<String, Object>> mapped = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map) {
                    mapped.add((Map<String, Object>) item);
                }
            }
            return mapped;
        }
        return Collections.emptyList();
    }
}
