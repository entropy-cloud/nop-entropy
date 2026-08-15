package io.nop.ai.core.dialect;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatReasoningMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolCallMessage;
import io.nop.ai.api.chat.messages.ChatToolDefinition;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.api.chat.messages.ChatUsage;
import io.nop.ai.core.model.LlmModel;
import io.nop.ai.core.model.LlmModelModel;
import io.nop.ai.core.model.LlmResponseModel;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.api.chat.stream.StreamItemPhase;
import io.nop.ai.api.chat.stream.StreamItemType;
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
    @SuppressWarnings("unchecked")
    public ChatRequest parseRequestBody(Map<String, Object> body) {
        ChatRequest request = new ChatRequest();
        List<ChatMessage> messages = new ArrayList<>();

        // systemInstruction.parts[].text → 首位 ChatSystemMessage
        Object systemObj = body.get("systemInstruction");
        if (systemObj instanceof Map) {
            Map<String, Object> systemMap = (Map<String, Object>) systemObj;
            Object partsObj = systemMap.get("parts");
            if (partsObj instanceof List) {
                StringBuilder systemText = new StringBuilder();
                for (Object part : (List<?>) partsObj) {
                    if (part instanceof Map && ((Map<?, ?>) part).get("text") != null) {
                        if (systemText.length() > 0) {
                            systemText.append("\n");
                        }
                        systemText.append(((Map<?, ?>) part).get("text").toString());
                    }
                }
                if (systemText.length() > 0) {
                    messages.add(new ChatSystemMessage(systemText.toString()));
                }
            }
        }

        // contents[] → messages（parts 按语义产出 message/reasoning/tool_call/tool_output）
        List<Map<String, Object>> rawContents = (List<Map<String, Object>>) body.get("contents");
        if (rawContents != null) {
            for (Map<String, Object> raw : rawContents) {
                String role = (String) raw.get("role");
                Object partsObj = raw.get("parts");
                if (!(partsObj instanceof List)) {
                    continue;
                }
                for (Object part : (List<?>) partsObj) {
                    if (!(part instanceof Map)) {
                        continue;
                    }
                    Map<String, Object> partMap = (Map<String, Object>) part;
                    Object functionCall = partMap.get("functionCall");
                    Object functionResponse = partMap.get("functionResponse");
                    if (functionCall instanceof Map) {
                        Map<String, Object> fc = (Map<String, Object>) functionCall;
                        ChatToolCallMessage toolCallMsg = new ChatToolCallMessage();
                        toolCallMsg.setName((String) fc.get("name"));
                        if (fc.get("args") instanceof Map) {
                            toolCallMsg.setArguments((Map<String, Object>) fc.get("args"));
                        }
                        messages.add(toolCallMsg);
                    } else if (functionResponse instanceof Map) {
                        Map<String, Object> fr = (Map<String, Object>) functionResponse;
                        Object responseObj = fr.get("response");
                        String resultText = null;
                        if (responseObj instanceof Map) {
                            Object result = ((Map<?, ?>) responseObj).get("result");
                            if (result != null) {
                                resultText = result.toString();
                            }
                        }
                        messages.add(new ChatToolResponseMessage(null, (String) fr.get("name"), resultText));
                    } else {
                        Object textObj = partMap.get("text");
                        if (textObj == null) {
                            continue;
                        }
                        String text = textObj.toString();
                        // convertMessage 伪影检测：ChatToolCallMessage.getContent()=arguments JSON、
                        // ChatToolResponseMessage.content=result 文本，与 function part 同发的
                        // 文本 part 若等于对应 payload 的序列化则丢弃
                        if (isToolPartArtifact(partsObj, partMap, text)) {
                            continue;
                        }
                        // convertMessage 将推理消息包装为 <thinking>...</thinking> 文本 part（逆向剥离）
                        String reasoning = stripThinkingWrapper(text);
                        if (reasoning != null) {
                            messages.add(new ChatReasoningMessage(reasoning));
                        } else if ("model".equals(role)) {
                            messages.add(new ChatAssistantMessage(text));
                        } else {
                            messages.add(new ChatUserMessage(text));
                        }
                    }
                }
            }
        }
        if (!messages.isEmpty()) {
            request.setMessages(messages);
        }

        // generationConfig → options
        ChatOptions options = new ChatOptions();
        Object generationConfigObj = body.get("generationConfig");
        if (generationConfigObj instanceof Map) {
            Map<String, Object> generationConfig = (Map<String, Object>) generationConfigObj;
            if (generationConfig.get("temperature") instanceof Number) {
                options.setTemperature(((Number) generationConfig.get("temperature")).floatValue());
            }
            if (generationConfig.get("maxOutputTokens") instanceof Number) {
                options.setMaxTokens(((Number) generationConfig.get("maxOutputTokens")).intValue());
            }
            if (generationConfig.get("topP") instanceof Number) {
                options.setTopP(((Number) generationConfig.get("topP")).floatValue());
            }
            if (generationConfig.get("topK") instanceof Number) {
                options.setTopK(((Number) generationConfig.get("topK")).intValue());
            }
            if (generationConfig.get("stopSequences") instanceof List) {
                options.setStop((List<String>) generationConfig.get("stopSequences"));
            }
        }
        request.setOptions(options);

        // tools：Gemini 形态 [{functionDeclarations: [...]}]
        List<Map<String, Object>> rawTools = (List<Map<String, Object>>) body.get("tools");
        if (rawTools != null) {
            List<ChatToolDefinition> tools = new ArrayList<>();
            for (Map<String, Object> raw : rawTools) {
                Object declsObj = raw.get("functionDeclarations");
                if (!(declsObj instanceof List)) {
                    continue;
                }
                for (Object decl : (List<?>) declsObj) {
                    if (!(decl instanceof Map)) {
                        continue;
                    }
                    Map<String, Object> declMap = (Map<String, Object>) decl;
                    ChatToolDefinition def = new ChatToolDefinition();
                    def.setName((String) declMap.get("name"));
                    def.setDescription((String) declMap.get("description"));
                    if (declMap.get("parameters") instanceof Map) {
                        def.setParameters((Map<String, Object>) declMap.get("parameters"));
                    }
                    tools.add(def);
                }
            }
            if (!tools.isEmpty()) {
                request.setTools(tools);
            }
        }
        return request;
    }

    private static String stripThinkingWrapper(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("<thinking>") && trimmed.endsWith("</thinking>")
                && trimmed.length() > "<thinking></thinking>".length()) {
            return trimmed.substring("<thinking>".length(), trimmed.length() - "</thinking>".length());
        }
        return null;
    }

    /**
     * convertMessage 伪影判定：同一 contents 条目内若含 functionCall/functionResponse part，
     * 且本文本 part 等于对应 payload 的序列化（ChatToolCallMessage.getContent()=arguments JSON
     * / ChatToolResponseMessage.content=result 文本），则该文本 part 是 convertMessage 的副作用产物。
     */
    @SuppressWarnings("unchecked")
    private static boolean isToolPartArtifact(Object partsObj, Map<String, Object> textPart, String text) {
        for (Object part : (List<?>) partsObj) {
            if (!(part instanceof Map)) {
                continue;
            }
            Map<String, Object> partMap = (Map<String, Object>) part;
            if (partMap == textPart) {
                continue;
            }
            Object functionCall = partMap.get("functionCall");
            if (functionCall instanceof Map) {
                Object args = ((Map<String, Object>) functionCall).get("args");
                if (args instanceof Map && JSON.stringify(args).equals(text)) {
                    return true;
                }
                return args == null && (text == null || text.isEmpty());
            }
            Object functionResponse = partMap.get("functionResponse");
            if (functionResponse instanceof Map) {
                Object responseObj = ((Map<String, Object>) functionResponse).get("response");
                if (responseObj instanceof Map) {
                    Object result = ((Map<?, ?>) responseObj).get("result");
                    if (result != null && result.toString().equals(text)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public String buildUrl(String baseUrl, String chatUrl, String apiKey) {
        String url = StringHelper.appendPath(baseUrl, chatUrl);
        // API key is passed via x-api-key header instead of URL query parameter
        return url;
    }

    @Override
    public void setHeaders(HttpRequest httpRequest, String apiKey, String apiKeyHeader) {
        httpRequest.setHeader("Content-Type", "application/json");
        // Gemini supports API key via x-api-key header (preferred over URL query param)
        if (!StringHelper.isEmpty(apiKey)) {
            httpRequest.setHeader("x-api-key", apiKey);
        }
    }

    @Override
    public Map<String, Object> buildBody(ChatRequest request, LlmModel config,
                                          LlmModelModel modelConfig, String model, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        ChatOptions options = request.getOptions();

        // 分离 system 消息
        String systemContent = null;
        List<Map<String, Object>> contents = new ArrayList<>();
        ChatMessage lastMessage = request.getLastMessage();

        for (ChatMessage msg : request.getMessages()) {
            if (msg instanceof io.nop.ai.api.chat.messages.ChatSystemMessage) {
                systemContent = msg.getContent();
            } else {
                Map<String, Object> msgMap = convertMessage(msg, modelConfig, options);
                if (msg == lastMessage && modelConfig != null) {
                    applyThinkingToParts(msgMap, modelConfig, options);
                }
                contents.add(msgMap);
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

        // Plan 329：单一拆分模型产出。thought:true parts → ChatReasoningMessage，其余 text parts → assistant 文本。
        List<ChatMessage> messages = new ArrayList<>();
        if (thinkingBuilder.length() > 0) {
            messages.add(new ChatReasoningMessage(thinkingBuilder.toString()));
        }
        messages.add(message);
        response.setMessages(messages);

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
        String finishReason = normalizeFinishReason(getString(dataMap, "candidates.0.finishReason"));
        if (finishReason != null) {
            chunk.setPhase(StreamItemPhase.DONE);
            chunk.setFinishReason(finishReason);
        }

        // 解析内容 - 处理 thought 标记 / functionCall（itemIndex 按出现序）
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
                        int order = 0;
                        for (Object part : (List<?>) partsObj) {
                            if (part instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> partMap = (Map<String, Object>) part;
                                Boolean thought = (Boolean) partMap.get("thought");
                                String text = (String) partMap.get("text");
                                Object functionCall = partMap.get("functionCall");

                                if (text != null) {
                                    if (Boolean.TRUE.equals(thought)) {
                                        chunk.setItemType(StreamItemType.reasoning);
                                        chunk.setItemIndex(order);
                                        chunk.setPhase(StreamItemPhase.DELTA);
                                        chunk.setDelta(text);
                                    } else {
                                        chunk.setItemType(StreamItemType.text);
                                        chunk.setItemIndex(order);
                                        chunk.setPhase(StreamItemPhase.DELTA);
                                        chunk.setDelta(text);
                                    }
                                    return chunk; // 每个 chunk 通常只有一个内容
                                }
                                if (functionCall instanceof Map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> fc = (Map<String, Object>) functionCall;
                                    chunk.setItemType(StreamItemType.tool_call);
                                    chunk.setItemIndex(order);
                                    chunk.setPhase(StreamItemPhase.ADDED);
                                    chunk.setDelta((String) fc.get("name"));
                                    // Gemini 的 args 是结构化对象，作为 tool_call 增量。
                                    // 单 chunk 边界：args 完整时无法与 name 同载，按 name 优先
                                    // （与 OpenAI/Anthropic 流式 tool_call 先声明 name 的语义一致）。
                                    return chunk;
                                }
                                order++;
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
                                               ChatOptions options) {
        Map<String, Object> msgMap = new LinkedHashMap<>();
        
        msgMap.put("role", getRole(message));

        List<Map<String, Object>> parts = new ArrayList<>();

        String textContent = message.getContent();
        if (textContent != null && !textContent.isEmpty()) {
            Map<String, Object> textPart = new LinkedHashMap<>();
            textPart.put("text", textContent);
            parts.add(textPart);
        }

        if (message instanceof ChatReasoningMessage) {
            Map<String, Object> thinkingPart = new LinkedHashMap<>();
            thinkingPart.put("text", "<thinking>" + message.getContent() + "</thinking>");
            parts.add(thinkingPart);
        }

        if (message instanceof ChatToolCallMessage) {
            ChatToolCallMessage toolCallMsg = (ChatToolCallMessage) message;
            Map<String, Object> functionCall = new LinkedHashMap<>();
            functionCall.put("name", toolCallMsg.getName());
            functionCall.put("args", toolCallMsg.getArguments());

            Map<String, Object> toolPart = new LinkedHashMap<>();
            toolPart.put("functionCall", functionCall);
            parts.add(toolPart);
        }

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

    @SuppressWarnings("unchecked")
    private void applyThinkingToParts(Map<String, Object> msgMap,
                                       LlmModelModel modelConfig, ChatOptions options) {
        List<Map<String, Object>> parts = (List<Map<String, Object>>) msgMap.get("parts");
        if (parts != null) {
            for (Map<String, Object> part : parts) {
                if (part.containsKey("text")) {
                    String text = (String) part.get("text");
                    part.put("text", applyThinkingPrompt(text, modelConfig, options));
                    break;
                }
            }
        }
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

    // ==================== 反向转换：ChatResponse → Provider 响应 Map ====================

    @Override
    public Map<String, Object> buildResponse(ChatResponse response) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (response.getModel() != null) {
            result.put("model", response.getModel());
        }

        // messages 序列 → candidates[0].content.parts（reasoning→thought:true / text / functionCall）
        List<Map<String, Object>> parts = new ArrayList<>();
        if (response.getMessages() != null) {
            for (ChatMessage msg : response.getMessages()) {
                if (msg instanceof ChatReasoningMessage) {
                    Map<String, Object> part = new LinkedHashMap<>();
                    part.put("text", msg.getContent());
                    part.put("thought", true);
                    parts.add(part);
                } else if (msg instanceof ChatToolCallMessage) {
                    ChatToolCallMessage tcm = (ChatToolCallMessage) msg;
                    Map<String, Object> functionCall = new LinkedHashMap<>();
                    functionCall.put("name", tcm.getName());
                    if (tcm.getArguments() != null) {
                        functionCall.put("args", tcm.getArguments());
                    }
                    Map<String, Object> part = new LinkedHashMap<>();
                    part.put("functionCall", functionCall);
                    parts.add(part);
                } else if (msg instanceof ChatAssistantMessage && msg.getContent() != null) {
                    Map<String, Object> part = new LinkedHashMap<>();
                    part.put("text", msg.getContent());
                    parts.add(part);
                }
            }
        }
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("role", "model");
        content.put("parts", parts);

        Map<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("content", content);
        if (response.getFinishReason() != null) {
            candidate.put("finishReason", reverseFinishReason(response.getFinishReason()));
        }
        result.put("candidates", List.of(candidate));

        // usage → usageMetadata
        if (response.getUsage() != null) {
            Map<String, Object> usage = new LinkedHashMap<>();
            if (response.getUsage().getPromptTokens() != null) {
                usage.put("promptTokenCount", response.getUsage().getPromptTokens());
            }
            if (response.getUsage().getCompletionTokens() != null) {
                usage.put("candidatesTokenCount", response.getUsage().getCompletionTokens());
            }
            if (response.getUsage().getTotalTokens() != null) {
                usage.put("totalTokenCount", response.getUsage().getTotalTokens());
            }
            result.put("usageMetadata", usage);
        }
        return result;
    }

    /**
     * 归一化 finish_reason → Gemini 原生 finishReason（normalizeFinishReason 的逆映射）。
     * 归一化有损点 (a) 裁定：roundtrip 样本仅使用归一化目标值；未知值原样透传。
     */
    private String reverseFinishReason(String reason) {
        switch (reason) {
            case "stop":
            case "tool_calls":
                return "STOP";
            case "length":
                return "MAX_TOKENS";
            case "content_filter":
                return "SAFETY";
            default:
                return reason;
        }
    }

    @Override
    public Map<String, Object> buildStreamChunk(ChatStreamChunk chunk) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (chunk.getModel() != null) {
            result.put("model", chunk.getModel());
        }

        if (chunk.getPhase() == StreamItemPhase.DONE) {
            // 终止信号 → candidates[0].finishReason
            Map<String, Object> candidate = new LinkedHashMap<>();
            if (chunk.getFinishReason() != null) {
                candidate.put("finishReason", reverseFinishReason(chunk.getFinishReason()));
            }
            result.put("candidates", List.of(candidate));
            return result;
        }

        StreamItemType type = chunk.getItemType();
        if (type != null) {
            List<Map<String, Object>> parts = new ArrayList<>();
            if (type == StreamItemType.reasoning) {
                Map<String, Object> part = new LinkedHashMap<>();
                part.put("text", chunk.getDelta());
                part.put("thought", true);
                parts.add(part);
            } else if (type == StreamItemType.tool_call) {
                Map<String, Object> functionCall = new LinkedHashMap<>();
                functionCall.put("name", chunk.getDelta());
                Map<String, Object> part = new LinkedHashMap<>();
                part.put("functionCall", functionCall);
                parts.add(part);
            } else {
                Map<String, Object> part = new LinkedHashMap<>();
                if (chunk.getDelta() != null) {
                    part.put("text", chunk.getDelta());
                }
                parts.add(part);
            }
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("role", "model");
            content.put("parts", parts);
            Map<String, Object> candidate = new LinkedHashMap<>();
            candidate.put("content", content);
            result.put("candidates", List.of(candidate));
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
