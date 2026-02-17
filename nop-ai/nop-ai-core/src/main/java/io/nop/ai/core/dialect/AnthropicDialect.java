package io.nop.ai.core.dialect;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.messages.ChatToolDefinition;
import io.nop.ai.api.chat.messages.ChatUsage;
import io.nop.ai.core.model.LlmModel;
import io.nop.ai.core.model.LlmModelModel;
import io.nop.ai.core.model.LlmResponseModel;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.api.chat.stream.ChatToolCallChunk;
import io.nop.api.core.json.JSON;
import io.nop.commons.util.StringHelper;
import io.nop.http.api.client.HttpRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anthropic (Claude) 方言实现。
 * <p>
 * 处理 Claude API 格式：
 * <pre>
 * Request:
 * {
 *   "model": "claude-3-5-sonnet",
 *   "system": "You are a helpful assistant",
 *   "messages": [{"role": "user", "content": [{"type": "text", "text": "..."}]}],
 *   "max_tokens": 4096,
 *   "temperature": 0.7,
 *   "stream": true
 * }
 *
 * Response:
 * {
 *   "id": "msg_...",
 *   "content": [{"type": "text", "text": "..."}],
 *   "role": "assistant",
 *   "stop_reason": "end_turn",
 *   "usage": {"input_tokens": 10, "output_tokens": 20}
 * }
 * </pre>
 */
public class AnthropicDialect extends AbstractLlmDialect implements ILlmDialect {

    @Override
    public String getName() {
        return "anthropic";
    }

    @Override
    public String buildUrl(String baseUrl, String chatUrl, String apiKey) {
        return StringHelper.appendPath(baseUrl, chatUrl);
    }

    @Override
    public void setHeaders(HttpRequest httpRequest, String apiKey, String apiKeyHeader) {
        httpRequest.setHeader("Content-Type", "application/json");
        if (!StringHelper.isEmpty(apiKey)) {
            if (apiKeyHeader != null) {
                httpRequest.setHeader(apiKeyHeader, apiKey);
            } else {
                httpRequest.setHeader("x-api-key", apiKey);  // Claude 使用 x-api-key
                httpRequest.setHeader("anthropic-version", "2023-06-01");
            }
        }
    }

    @Override
    public Map<String, Object> buildBody(ChatRequest request, LlmModel config,
                                          LlmModelModel modelConfig, String model, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("stream", stream);

        // Claude 要求必须指定 max_tokens
        Integer maxTokens = resolveMaxTokens(request.getOptions(), modelConfig);
        if (maxTokens == null) {
            maxTokens = 4096; // Claude 默认值
        }
        body.put("max_tokens", maxTokens);

        ChatOptions options = request.getOptions();
        if (options != null) {
            addOptionIfNotNull(body, "temperature", options.getTemperature());
            addOptionIfNotNull(body, "top_p", options.getTopP());
            addOptionIfNotNull(body, "top_k", options.getTopK());
            addOptionIfNotNull(body, "stop_sequences", options.getStop());

            if (options.getTools() != null && !options.getTools().isEmpty()) {
                body.put("tools", convertToolDefinitions(options.getTools()));
            }
        }

        // 分离 system 消息到单独字段
        List<Map<String, Object>> messages = new ArrayList<>();
        for (ChatMessage msg : request.getMessages()) {
            if (msg instanceof io.nop.ai.api.chat.messages.ChatSystemMessage) {
                body.put("system", msg.getContent());
            } else {
                messages.add(convertMessage(msg, modelConfig, msg == request.getLastMessage(), options));
            }
        }

        if (!messages.isEmpty()) {
            body.put("messages", messages);
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

        // 解析内容块（可能包含 text, thinking, tool_use）
        StringBuilder contentBuilder = new StringBuilder();
        StringBuilder thinkingBuilder = new StringBuilder();
        List<ChatToolCall> toolCalls = new ArrayList<>();

        Object contentObj = responseMap.get("content");
        if (contentObj instanceof List) {
            for (Object block : (List<?>) contentObj) {
                if (block instanceof Map) {
                    Map<String, Object> blockMap = (Map<String, Object>) block;
                    String type = (String) blockMap.get("type");

                    if ("text".equals(type)) {
                        String text = (String) blockMap.get("text");
                        if (text != null) {
                            if (contentBuilder.length() > 0) {
                                contentBuilder.append("\n");
                            }
                            contentBuilder.append(text);
                        }
                    } else if ("thinking".equals(type)) {
                        // Extended Thinking 模式的思考内容
                        String thinking = (String) blockMap.get("thinking");
                        if (thinking != null) {
                            if (thinkingBuilder.length() > 0) {
                                thinkingBuilder.append("\n");
                            }
                            thinkingBuilder.append(thinking);
                        }
                    } else if ("tool_use".equals(type)) {
                        ChatToolCall toolCall = new ChatToolCall();
                        toolCall.setId((String) blockMap.get("id"));
                        toolCall.setName((String) blockMap.get("name"));
                        toolCall.setArguments((Map<String, Object>) blockMap.get("input"));
                        toolCalls.add(toolCall);
                    }
                }
            }
        }

        ChatAssistantMessage message = new ChatAssistantMessage();
        message.setContent(contentBuilder.length() > 0 ? contentBuilder.toString() : null);
        message.setThink(thinkingBuilder.length() > 0 ? thinkingBuilder.toString() : null);
        if (!toolCalls.isEmpty()) {
            message.setToolCalls(toolCalls);
        }
        response.setMessage(message);

        // 解析元数据
        response.setId(getStringByPath(responseMap, "id"));
        response.setModel(getStringByPath(responseMap, "model"));

        // 解析结束原因
        String statusPath = responseConfig != null && responseConfig.getStatusPath() != null
                ? responseConfig.getStatusPath() : "stop_reason";
        response.setFinishReason(normalizeFinishReason(getStringByPath(responseMap, statusPath)));

        // 解析 Usage（使用基类通用方法，支持缓存 token）
        ChatUsage usage = parseUsage(responseMap, responseConfig,
                "usage.input_tokens", "usage.output_tokens", null);

        // Anthropic Prompt Caching 专用字段
        // cache_creation_input_tokens: 创建缓存时消耗的 token
        // cache_read_input_tokens: 从缓存读取的 token（缓存命中）
        Integer cacheCreationTokens = getIntByPath(responseMap, "usage.cache_creation_input_tokens");
        Integer cacheReadTokens = getIntByPath(responseMap, "usage.cache_read_input_tokens");

        // 将 Anthropic 的字段映射到通用字段
        // cache_read_input_tokens -> cacheHitTokens (缓存命中)
        // cache_creation_input_tokens -> cacheCreationTokens (创建缓存时消耗)
        if (cacheReadTokens != null) {
            usage.setCacheHitTokens(cacheReadTokens);
        }
        if (cacheCreationTokens != null) {
            usage.setCacheCreationTokens(cacheCreationTokens);
        }

        response.setUsage(usage);

        return response;
    }

    @Override
    public ChatStreamChunk parseStreamChunk(String data) {
        if (data == null || data.isEmpty() || "[DONE]".equals(data)) {
            return null;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> dataMap = (Map<String, Object>) JSON.parse(data);
        String eventType = (String) dataMap.get("type");

        ChatStreamChunk chunk = new ChatStreamChunk();

        switch (eventType) {
            case "message_start":
                // 消息开始，可以获取模型信息
                Object message = dataMap.get("message");
                if (message instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> messageMap = (Map<String, Object>) message;
                    chunk.setModel((String) messageMap.get("model"));
                }
                break;

            case "content_block_start":
                // 内容块开始（thinking/text/tool_use）
                Object contentBlock = dataMap.get("content_block");
                if (contentBlock instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> blockMap = (Map<String, Object>) contentBlock;
                    String blockType = (String) blockMap.get("type");
                    if ("thinking".equals(blockType)) {
                        // 思考块开始
                        chunk.setThinking((String) blockMap.get("thinking"));
                    } else if ("text".equals(blockType)) {
                        chunk.setContent((String) blockMap.get("text"));
                    } else if ("tool_use".equals(blockType)) {
                        // 工具调用开始，创建 toolCall chunk
                        ChatToolCallChunk toolCallChunk = new ChatToolCallChunk();
                        toolCallChunk.setId((String) blockMap.get("id"));
                        toolCallChunk.setName((String) blockMap.get("name"));
                        toolCallChunk.setType("function");
                        chunk.setToolCall(toolCallChunk);
                    }
                }
                break;

            case "content_block_delta":
                // 内容增量
                Object delta = dataMap.get("delta");
                Integer index = (Integer) dataMap.get("index");
                if (delta instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> deltaMap = (Map<String, Object>) delta;
                    String deltaType = (String) deltaMap.get("type");
                    if ("text_delta".equals(deltaType)) {
                        chunk.setContent((String) deltaMap.get("text"));
                    } else if ("thinking_delta".equals(deltaType)) {
                        chunk.setThinking((String) deltaMap.get("thinking"));
                    } else if ("input_json_delta".equals(deltaType)) {
                        // 工具调用参数增量（partial_json）
                        String partialJson = (String) deltaMap.get("partial_json");
                        if (partialJson != null) {
                            ChatToolCallChunk toolCallChunk = new ChatToolCallChunk();
                            toolCallChunk.setIndex(index);
                            toolCallChunk.setArguments(partialJson);
                            chunk.setToolCall(toolCallChunk);
                        }
                    }
                }
                break;

            case "content_block_stop":
                // 内容块结束
                // 可以用于标记思考块或工具调用块的结束
                break;

            case "message_delta":
                // 消息增量，包含停止原因和 usage
                Object usage = dataMap.get("usage");
                if (usage instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> usageMap = (Map<String, Object>) usage;
                    ChatUsage chunkUsage = new ChatUsage();
                    chunkUsage.setPromptTokens(getIntByPath(usageMap, "input_tokens"));
                    chunkUsage.setCompletionTokens(getIntByPath(usageMap, "output_tokens"));
                    // Prompt Caching 统计
                    chunkUsage.setCacheHitTokens(getIntByPath(usageMap, "cache_read_input_tokens"));
                    chunkUsage.setCacheCreationTokens(getIntByPath(usageMap, "cache_creation_input_tokens"));
                    chunk.setUsage(chunkUsage);
                }
                Object deltaObj = dataMap.get("delta");
                if (deltaObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> deltaMap = (Map<String, Object>) deltaObj;
                    String stopReason = (String) deltaMap.get("stop_reason");
                    if (stopReason != null) {
                        chunk.setFinishReason(normalizeFinishReason(stopReason));
                    }
                }
                break;

            case "message_stop":
                // 消息结束
                chunk.setFinishReason("stop");
                break;

            case "ping":
                // 心跳消息，忽略
                return null;

            case "error":
                // 错误消息
                Object error = dataMap.get("error");
                if (error instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> errorMap = (Map<String, Object>) error;
                    String errorMsg = (String) errorMap.get("message");
                    if (errorMsg == null) {
                        errorMsg = errorMap.toString();
                    }
                    // 返回一个带有错误的 chunk
                    chunk.setContent("[ERROR] " + errorMsg);
                }
                break;
        }

        return chunk;
    }

    @Override
    public Map<String, Object> convertMessage(ChatMessage message, LlmModelModel modelConfig,
                                               boolean isLast, ChatOptions options) {
        Map<String, Object> msgMap = new LinkedHashMap<>();
        
        // Claude 使用 model 而不是 assistant
        msgMap.put("role", getRole(message));

        // 内容使用数组格式
        List<Map<String, Object>> contentBlocks = new ArrayList<>();

        String textContent = message.getContent();
        if (isLast && modelConfig != null) {
            textContent = applyThinkingPrompt(textContent, modelConfig, options);
        }
        
        if (textContent != null && !textContent.isEmpty()) {
            Map<String, Object> textBlock = new LinkedHashMap<>();
            textBlock.put("type", "text");
            textBlock.put("text", textContent);
            contentBlocks.add(textBlock);
        }

        if (message instanceof ChatAssistantMessage) {
            ChatAssistantMessage assistantMsg = (ChatAssistantMessage) message;
            
            // 思考内容作为单独的 thinking 块
            if (assistantMsg.getThink() != null) {
                Map<String, Object> thinkingBlock = new LinkedHashMap<>();
                thinkingBlock.put("type", "thinking");
                thinkingBlock.put("thinking", assistantMsg.getThink());
                contentBlocks.add(thinkingBlock);
            }
            
            // 工具调用块
            if (assistantMsg.getToolCalls() != null && !assistantMsg.getToolCalls().isEmpty()) {
                for (ChatToolCall toolCall : assistantMsg.getToolCalls()) {
                    Map<String, Object> toolBlock = new LinkedHashMap<>();
                    toolBlock.put("type", "tool_use");
                    toolBlock.put("id", toolCall.getId());
                    toolBlock.put("name", toolCall.getName());
                    toolBlock.put("input", toolCall.getArguments());
                    contentBlocks.add(toolBlock);
                }
            }
        }

        if (message instanceof ChatToolResponseMessage) {
            ChatToolResponseMessage toolMsg = (ChatToolResponseMessage) message;
            Map<String, Object> toolResultBlock = new LinkedHashMap<>();
            toolResultBlock.put("type", "tool_result");
            toolResultBlock.put("tool_use_id", toolMsg.getToolCallId());
            toolResultBlock.put("content", toolMsg.getContent());
            contentBlocks.add(toolResultBlock);
        }

        msgMap.put("content", contentBlocks);
        return msgMap;
    }

    @Override
    public String getRole(ChatMessage message) {
        String role = getBaseRole(message);
        // Claude 使用 model 而不是 assistant
        return "assistant".equals(role) ? "model" : role;
    }

    /**
     * 转换工具定义为 Anthropic 格式
     * <p>
     * Anthropic 使用 input_schema 而不是 parameters：
     * <pre>
     * {
     *   "name": "get_weather",
     *   "description": "获取天气",
     *   "input_schema": {
     *     "type": "object",
     *     "properties": {...}
     *   }
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
            Map<String, Object> toolMap = new LinkedHashMap<>();
            toolMap.put("name", tool.getName());
            toolMap.put("description", tool.getDescription());
            // Anthropic 使用 input_schema 而不是 parameters
            if (tool.getParameters() != null) {
                toolMap.put("input_schema", tool.getParameters());
            }
            result.add(toolMap);
        }
        return result;
    }
}
