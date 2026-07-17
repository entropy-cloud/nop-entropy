package io.nop.gateway.conversion.ai;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.gateway.conversion.IBackendMessageConverter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Google Gemini 后端消息转换器。
 * <p>
 * 在 OpenAI 消息结构（前端统一入口）和 Gemini API 格式之间进行映射：
 * <ul>
 *   <li>请求：messages → contents，system 消息 → systemInstruction，采样参数 → generationConfig</li>
 *   <li>响应：candidates[0].content.parts → choices[0].message</li>
 *   <li>流式 chunk：candidates[0].content.parts → choices[0].delta</li>
 * </ul>
 * 角色映射：OpenAI assistant ↔ Gemini model。
 */
public class GeminiMessageConverter implements IBackendMessageConverter {

    @Override
    public ApiRequest<?> toBackendRequest(ApiRequest<?> request) {
        Map<String, Object> data = ConverterUtils.toMap(request.getData());
        Map<String, Object> body = new LinkedHashMap<>();

        // 1. 分离 system 消息并构建 contents
        StringBuilder systemContent = null;
        List<Map<String, Object>> contents = new ArrayList<>();

        for (Map<String, Object> msg : ConverterUtils.toMapList(data.get("messages"))) {
            String role = ConverterUtils.asString(msg.get("role"));
            String content = ConverterUtils.asString(msg.get("content"));
            if ("system".equals(role)) {
                if (systemContent == null) {
                    systemContent = new StringBuilder();
                }
                if (systemContent.length() > 0) {
                    systemContent.append("\n");
                }
                systemContent.append(content);
            } else {
                Map<String, Object> geminiMsg = new LinkedHashMap<>();
                geminiMsg.put("role", toGeminiRole(role));
                geminiMsg.put("parts", singletonTextParts(content));
                contents.add(geminiMsg);
            }
        }

        if (systemContent != null && systemContent.length() > 0) {
            Map<String, Object> systemInstruction = new LinkedHashMap<>();
            systemInstruction.put("parts", singletonTextParts(systemContent.toString()));
            body.put("systemInstruction", systemInstruction);
        }
        if (!contents.isEmpty()) {
            body.put("contents", contents);
        }

        // 2. generationConfig
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        putIfPresent(generationConfig, data, "temperature", "temperature");
        putIfPresent(generationConfig, data, "max_tokens", "maxOutputTokens");
        putIfPresent(generationConfig, data, "top_p", "topP");
        putIfPresent(generationConfig, data, "top_k", "topK");
        if (!generationConfig.isEmpty()) {
            body.put("generationConfig", generationConfig);
        }

        // 透传 model（Gemini 路由可能用到）
        ConverterUtils.copyIfPresent(body, data, "model");

        ApiRequest<Object> backend = new ApiRequest<>();
        backend.setHeaders(request.getHeaders());
        backend.setData(body);
        return backend;
    }

    @Override
    public ApiResponse<?> toFrontendResponse(ApiResponse<?> backendResponse, ApiRequest<?> request) {
        Map<String, Object> backendData = ConverterUtils.toMap(backendResponse.getData());
        Map<String, Object> requestMap = ConverterUtils.toMap(request.getData());

        String content = extractCandidateText(backendData);
        Map<String, Object> openai = ConverterUtils.buildOpenAIResponse(
                ConverterUtils.resolveRequestId(requestMap),
                ConverterUtils.resolveModel(requestMap),
                content);

        // finish_reason：Gemini STOP → OpenAI stop。buildOpenAIResponse 默认设置 stop，
        // 这里覆盖为从后端响应解析出的真实值。
        String finishReason = normalizeFinishReason(extractFinishReason(backendData));
        Map<String, Object> firstChoice = ConverterUtils.getFirstChoice(openai);
        if (firstChoice != null && finishReason != null) {
            firstChoice.put("finish_reason", finishReason);
        }

        ApiResponse<Object> frontend = new ApiResponse<>();
        frontend.setHttpStatus(backendResponse.getHttpStatus());
        frontend.setData(openai);
        return frontend;
    }

    @Override
    public Map<String, Object> toFrontendStreamChunk(Map<String, Object> backendDelta, ApiRequest<?> request) {
        if (backendDelta == null) {
            return null;
        }
        Map<String, Object> requestMap = ConverterUtils.toMap(request.getData());
        String deltaContent = extractCandidateText(backendDelta);
        String finishReason = normalizeFinishReason(extractFinishReason(backendDelta));

        Map<String, Object> chunk = ConverterUtils.buildOpenAIChunk(
                ConverterUtils.resolveRequestId(requestMap),
                resolveChunkModel(backendDelta, requestMap),
                deltaContent,
                finishReason);
        return chunk;
    }

    // ==================== 辅助方法 ====================

    private static String toGeminiRole(String openaiRole) {
        return "assistant".equals(openaiRole) ? "model" : openaiRole;
    }

    private static List<Map<String, Object>> singletonTextParts(String text) {
        List<Map<String, Object>> parts = new ArrayList<>(1);
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("text", text == null ? "" : text);
        parts.add(part);
        return parts;
    }

    private static void putIfPresent(Map<String, Object> target, Map<String, Object> source,
                                      String sourceKey, String targetKey) {
        if (source.containsKey(sourceKey) && source.get(sourceKey) != null) {
            target.put(targetKey, source.get(sourceKey));
        }
    }

    /**
     * 从 Gemini candidates[0].content.parts[].text 中提取并拼接文本内容。
     */
    @SuppressWarnings("unchecked")
    private static String extractCandidateText(Map<String, Object> response) {
        Object candidatesObj = response.get("candidates");
        if (!(candidatesObj instanceof List)) {
            return "";
        }
        List<?> candidates = (List<?>) candidatesObj;
        if (candidates.isEmpty() || !(candidates.get(0) instanceof Map)) {
            return "";
        }
        Map<String, Object> candidate = (Map<String, Object>) candidates.get(0);
        Object contentObj = candidate.get("content");
        if (!(contentObj instanceof Map)) {
            return "";
        }
        Map<String, Object> contentMap = (Map<String, Object>) contentObj;
        Object partsObj = contentMap.get("parts");
        if (!(partsObj instanceof List)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Object part : (List<?>) partsObj) {
            if (part instanceof Map) {
                Object text = ((Map<String, Object>) part).get("text");
                if (text != null) {
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append(text);
                }
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static String extractFinishReason(Map<String, Object> response) {
        Object candidatesObj = response.get("candidates");
        if (candidatesObj instanceof List && !((List<?>) candidatesObj).isEmpty()) {
            Object first = ((List<?>) candidatesObj).get(0);
            if (first instanceof Map) {
                Object reason = ((Map<String, Object>) first).get("finishReason");
                return reason == null ? "" : reason.toString();
            }
        }
        return "";
    }

    private static String normalizeFinishReason(String geminiReason) {
        if (geminiReason == null || geminiReason.isEmpty()) {
            return null;
        }
        switch (geminiReason.toUpperCase()) {
            case "STOP":
                return "stop";
            case "MAX_TOKENS":
                return "length";
            case "SAFETY":
            case "RECITATION":
                return "content_filter";
            default:
                return geminiReason.toLowerCase();
        }
    }

    private static String resolveChunkModel(Map<String, Object> delta, Map<String, Object> requestMap) {
        String model = ConverterUtils.asString(delta.get("model"));
        if (model == null || model.isEmpty()) {
            model = ConverterUtils.asString(delta.get("modelVersion"));
        }
        if (model == null || model.isEmpty()) {
            model = ConverterUtils.resolveModel(requestMap);
        }
        return model;
    }
}
