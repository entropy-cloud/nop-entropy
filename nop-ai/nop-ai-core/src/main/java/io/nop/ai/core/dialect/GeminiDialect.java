package io.nop.ai.core.dialect;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolDefinition;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.messages.ChatUsage;
import io.nop.ai.core.model.LlmModel;
import io.nop.ai.core.model.LlmModelModel;
import io.nop.ai.core.model.LlmResponseModel;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.api.core.json.JSON;
import io.nop.commons.util.StringHelper;
import io.nop.http.api.client.HttpRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Google Gemini 方言实现。
 * <p>
 * 处理 Gemini API 格式：
 * <pre>
 * Request:
 * {
 *   "systemInstruction": {"parts": [{"text": "..."}]},
 *   "contents": [
 *     {"role": "user", "parts": [{"text": "..."}]}
 *   ],
 *   "generationConfig": {
 *     "temperature": 0.7,
 *     "maxOutputTokens": 1000
 *   }
 * }
 *
 * Response:
 * {
 *   "candidates": [{
 *     "content": {
 *       "role": "model",
 *       "parts": [{"text": "..."}]
 *     },
 *     "finishReason": "STOP"
 *   }],
 *   "usageMetadata": {
 *     "promptTokenCount": 10,
 *     "candidatesTokenCount": 20
 *   }
 * }
 * </pre>
 */
public class GeminiDialect extends AbstractLlmDialect implements ILlmDialect {

    @Override
    public String getName() {
        return "gemini";
    }

    @Override
    public String buildUrl(String baseUrl, String chatUrl, String apiKey) {
        String url = StringHelper.appendPath(baseUrl, chatUrl);
        // Gemini 使用 URL 查询参数传递 API key
        if (!StringHelper.isEmpty(apiKey)) {
            url = url + (url.contains("?") ? "&" : "?") + "key=" + apiKey;
        }
        return url;
    }

    @Override
    public void setHeaders(HttpRequest httpRequest, String apiKey, String apiKeyHeader) {
        // Gemini 在 URL 中传递 key，不需要 header
        httpRequest.setHeader("Content-Type", "application/json");
    }

    @Override
    public Map<String, Object> buildBody(ChatRequest request, LlmModel config,
                                          LlmModelModel modelConfig, String model, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        ChatOptions options = request.getOptions();

        // 分离 system 消息
        String systemContent = null;
        List<Map<String, Object>> contents = new ArrayList<>();

        for (ChatMessage msg : request.getMessages()) {
            if (msg instanceof io.nop.ai.api.chat.messages.ChatSystemMessage) {
                systemContent = msg.getContent();
            } else {
                contents.add(convertMessage(msg, modelConfig, msg == request.getLastMessage(), options));
            }
        }

        if (systemContent != null) {
            Map<String, Object> systemInstruction = new LinkedHashMap<>();
            systemInstruction.put("parts", singletonTextList(systemContent));
            body.put("systemInstruction", systemInstruction);
        }

        if (!contents.isEmpty()) {
            body.put("contents", contents);
        }

        // generationConfig
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        if (options != null) {
            addOptionIfNotNull(generationConfig, "temperature", options.getTemperature());
            addOptionIfNotNull(generationConfig, "maxOutputTokens", resolveMaxTokens(options, modelConfig));
            addOptionIfNotNull(generationConfig, "topP", options.getTopP());
            addOptionIfNotNull(generationConfig, "topK", options.getTopK());
            addOptionIfNotNull(generationConfig, "stopSequences", options.getStop());

            // 工具定义（Gemini 使用 functionDeclarations）
            if (options.getTools() != null && !options.getTools().isEmpty()) {
                Map<String, Object> toolsConfig = new LinkedHashMap<>();
                toolsConfig.put("functionDeclarations", convertToolDefinitions(options.getTools()));
                body.put("tools", java.util.Collections.singletonList(toolsConfig));
            }
        }
        if (!generationConfig.isEmpty()) {
            body.put("generationConfig", generationConfig);
        }

        return body;
    }

    @Override
    public ChatResponse parseResponse(String responseBody, LlmModel config) {
        if (StringHelper.isEmpty(responseBody)) {
            return ChatResponse.error("NULL_RESPONSE", "Empty response body");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> responseMap = (Map<String, Object>) JSON.parse(responseBody);
        LlmResponseModel responseConfig = config.getResponse();

        ChatResponse response = new ChatResponse();

        // 解析内容 - Gemini 使用 candidates[0].content.parts 数组
        // 需要处理 thought 标记的部分
        StringBuilder contentBuilder = new StringBuilder();
        StringBuilder thinkingBuilder = new StringBuilder();

        Object candidatesObj = responseMap.get("candidates");
        if (candidatesObj instanceof List) {
            List<?> candidates = (List<?>) candidatesObj;
            if (!candidates.isEmpty() && candidates.get(0) instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> candidate = (Map<String, Object>) candidates.get(0);
                Object contentObj = candidate.get("content");
                if (contentObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> contentMap = (Map<String, Object>) contentObj;
                    Object partsObj = contentMap.get("parts");
                    if (partsObj instanceof List) {
                        for (Object part : (List<?>) partsObj) {
                            if (part instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> partMap = (Map<String, Object>) part;
                                Boolean thought = (Boolean) partMap.get("thought");
                                String text = (String) partMap.get("text");

                                if (text != null) {
                                    if (Boolean.TRUE.equals(thought)) {
                                        // 思考内容
                                        if (thinkingBuilder.length() > 0) {
                                            thinkingBuilder.append("\n");
                                        }
                                        thinkingBuilder.append(text);
                                    } else {
                                        // 普通内容
                                        if (contentBuilder.length() > 0) {
                                            contentBuilder.append("\n");
                                        }
                                        contentBuilder.append(text);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        ChatAssistantMessage message = new ChatAssistantMessage();
        message.setContent(contentBuilder.length() > 0 ? contentBuilder.toString() : null);
        message.setThink(thinkingBuilder.length() > 0 ? thinkingBuilder.toString() : null);
        response.setMessage(message);

        // 解析元数据
        response.setModel(getStringByPath(responseMap, "model"));

        // 解析结束原因
        String statusPath = responseConfig != null && responseConfig.getStatusPath() != null
                ? responseConfig.getStatusPath() : "candidates.0.finishReason";
        response.setFinishReason(normalizeFinishReason(getStringByPath(responseMap, statusPath)));

        // 解析 Usage（使用基类通用方法）
        response.setUsage(parseUsage(responseMap, responseConfig,
                "usageMetadata.promptTokenCount", "usageMetadata.candidatesTokenCount", "usageMetadata.totalTokenCount"));

        return response;
    }

    @Override
    public ChatStreamChunk parseStreamChunk(String data) {
        if (data == null || data.isEmpty() || "[DONE]".equals(data)) {
            return null;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> dataMap = (Map<String, Object>) JSON.parse(data);
        ChatStreamChunk chunk = new ChatStreamChunk();

        // 解析模型信息
        chunk.setModel(getString(dataMap, "model"));
        if (chunk.getModel() == null) {
            chunk.setModel(getString(dataMap, "modelVersion"));
        }

        // 解析结束原因
        chunk.setFinishReason(normalizeFinishReason(getString(dataMap, "candidates.0.finishReason")));

        // 解析内容 - 处理 thought 标记
        Object candidatesObj = dataMap.get("candidates");
        if (candidatesObj instanceof List) {
            List<?> candidates = (List<?>) candidatesObj;
            if (!candidates.isEmpty() && candidates.get(0) instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> candidate = (Map<String, Object>) candidates.get(0);
                Object contentObj = candidate.get("content");
                if (contentObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> contentMap = (Map<String, Object>) contentObj;
                    Object partsObj = contentMap.get("parts");
                    if (partsObj instanceof List) {
                        for (Object part : (List<?>) partsObj) {
                            if (part instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> partMap = (Map<String, Object>) part;
                                Boolean thought = (Boolean) partMap.get("thought");
                                String text = (String) partMap.get("text");

                                if (text != null) {
                                    if (Boolean.TRUE.equals(thought)) {
                                        chunk.setThinking(text);
                                    } else {
                                        chunk.setContent(text);
                                    }
                                    break; // 每个chunk通常只有一个内容
                                }
                            }
                        }
                    }
                }
            }
        }

        return chunk;
    }

    @Override
    public Map<String, Object> convertMessage(ChatMessage message, LlmModelModel modelConfig,
                                               boolean isLast, ChatOptions options) {
        Map<String, Object> msgMap = new LinkedHashMap<>();
        
        // Gemini 使用 model 而不是 assistant
        msgMap.put("role", getRole(message));

        // 内容放在 parts 数组中
        List<Map<String, Object>> parts = new ArrayList<>();

        String textContent = message.getContent();
        if (isLast && modelConfig != null) {
            textContent = applyThinkingPrompt(textContent, modelConfig, options);
        }
        
        if (textContent != null && !textContent.isEmpty()) {
            Map<String, Object> textPart = new LinkedHashMap<>();
            textPart.put("text", textContent);
            parts.add(textPart);
        }

        if (message instanceof ChatAssistantMessage) {
            ChatAssistantMessage assistantMsg = (ChatAssistantMessage) message;
            
            // 思考内容作为单独的 text part（Gemini 没有专门的 thinking 类型）
            if (assistantMsg.getThink() != null) {
                Map<String, Object> thinkingPart = new LinkedHashMap<>();
                thinkingPart.put("text", "<thinking>" + assistantMsg.getThink() + "</thinking>");
                parts.add(thinkingPart);
            }
            
            // 工具调用（Gemini 使用 functionCall）
            if (assistantMsg.getToolCalls() != null && !assistantMsg.getToolCalls().isEmpty()) {
                for (ChatToolCall toolCall : assistantMsg.getToolCalls()) {
                    Map<String, Object> functionCall = new LinkedHashMap<>();
                    functionCall.put("name", toolCall.getName());
                    functionCall.put("args", toolCall.getArguments());
                    
                    Map<String, Object> toolPart = new LinkedHashMap<>();
                    toolPart.put("functionCall", functionCall);
                    parts.add(toolPart);
                }
            }
        }

        // 工具响应（Gemini 使用 functionResponse）
        if (message instanceof ChatToolResponseMessage) {
            ChatToolResponseMessage toolMsg = (ChatToolResponseMessage) message;
            Map<String, Object> functionResponse = new LinkedHashMap<>();
            functionResponse.put("name", toolMsg.getName());
            
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("result", toolMsg.getContent());
            functionResponse.put("response", response);
            
            Map<String, Object> toolPart = new LinkedHashMap<>();
            toolPart.put("functionResponse", functionResponse);
            parts.add(toolPart);
        }

        msgMap.put("parts", parts);
        return msgMap;
    }

    @Override
    public String getRole(ChatMessage message) {
        String role = getBaseRole(message);
        // Gemini 使用 model 而不是 assistant
        return "assistant".equals(role) ? "model" : role;
    }

    /**
     * 转换工具定义为 Gemini 格式
     * <p>
     * Gemini 使用 functionDeclarations 而不是 tools：
     * <pre>
     * {
     *   "functionDeclarations": [{
     *     "name": "get_weather",
     *     "description": "获取天气",
     *     "parameters": {
     *       "type": "object",
     *       "properties": {...}
     *     }
     *   }]
     * }
     * </pre>
     */
    @Override
    public List<Map<String, Object>> convertToolDefinitions(List<ChatToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (ChatToolDefinition tool : tools) {
            Map<String, Object> funcDecl = new LinkedHashMap<>();
            funcDecl.put("name", tool.getName());
            funcDecl.put("description", tool.getDescription());
            if (tool.getParameters() != null) {
                funcDecl.put("parameters", tool.getParameters());
            }
            result.add(funcDecl);
        }
        return result;
    }

    // ==================== 私有辅助方法 ====================

    private List<Map<String, Object>> singletonTextList(String text) {
        List<Map<String, Object>> list = new ArrayList<>(1);
        Map<String, Object> part = new LinkedHashMap<>(1);
        part.put("text", text);
        list.add(part);
        return list;
    }

    private String getString(Map<String, Object> map, String path) {
        return getStringByPath(map, path);
    }
}
