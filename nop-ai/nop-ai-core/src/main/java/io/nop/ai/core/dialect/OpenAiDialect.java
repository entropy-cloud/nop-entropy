package io.nop.ai.core.dialect;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
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
 * OpenAI 方言实现。
 * <p>
 * 处理标准的 OpenAI API 格式：
 * <pre>
 * Request:
 * {
 *   "model": "gpt-4",
 *   "messages": [{"role": "user", "content": "..."}],
 *   "temperature": 0.7,
 *   "max_tokens": 1000,
 *   "stream": true
 * }
 *
 * Response:
 * {
 *   "id": "...",
 *   "choices": [{"message": {"content": "..."}, "finish_reason": "stop"}],
 *   "usage": {"prompt_tokens": 10, "completion_tokens": 20}
 * }
 * </pre>
 */
public class OpenAiDialect extends AbstractLlmDialect implements ILlmDialect {

    @Override
    public String getName() {
        return "openai";
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
                httpRequest.setBearerToken(apiKey);
            }
        }
    }

    @Override
    public Map<String, Object> buildBody(ChatRequest request, LlmModel config,
                                          LlmModelModel modelConfig, String model, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", buildMessages(request, modelConfig));
        body.put("stream", stream);

        ChatOptions options = request.getOptions();
        if (options != null) {
            addOptionIfNotNull(body, "temperature", options.getTemperature());
            addOptionIfNotNull(body, "max_tokens", resolveMaxTokens(options, modelConfig));
            addOptionIfNotNull(body, "top_p", options.getTopP());
            addOptionIfNotNull(body, "top_k", options.getTopK());
            addOptionIfNotNull(body, "stop", options.getStop());

            if (options.getTools() != null && !options.getTools().isEmpty()) {
                body.put("tools", convertToolDefinitions(options.getTools()));
            }
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

        // 解析内容
        String contentPath = responseConfig != null && responseConfig.getContentPath() != null
                ? responseConfig.getContentPath()
                : "choices.0.message.content";
        String content = getStringByPath(responseMap, contentPath);

        // 解析推理内容（用于 DeepSeek R1 等推理模型）
        String thinking = parseReasoningContent(responseMap, responseConfig, "choices.0.message.reasoning_content");
        // 如果没有 reasoning_content，尝试 reasoning 字段
        if (thinking == null) {
            thinking = parseReasoningContent(responseMap, responseConfig, "choices.0.message.reasoning");
        }

        ChatAssistantMessage message = new ChatAssistantMessage();
        message.setContent(content);
        message.setThink(thinking);
        response.setMessage(message);

        // 解析元数据
        response.setId(getStringByPath(responseMap, "id"));
        response.setModel(getStringByPath(responseMap, "model"));

        // 解析结束原因
        String statusPath = responseConfig != null && responseConfig.getStatusPath() != null
                ? responseConfig.getStatusPath()
                : "choices.0.finish_reason";
        response.setFinishReason(normalizeFinishReason(getStringByPath(responseMap, statusPath)));

        // 解析 Usage（使用基类通用方法）
        response.setUsage(parseUsage(responseMap, responseConfig,
                "usage.prompt_tokens", "usage.completion_tokens", "usage.total_tokens"));

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

        chunk.setId(getString(dataMap, "id"));
        chunk.setRole(getString(dataMap, "choices.0.delta.role"));
        chunk.setContent(getString(dataMap, "choices.0.delta.content"));

        // 处理思考/推理内容（支持多种字段名）
        String thinking = getString(dataMap, "choices.0.delta.thinking");
        if (thinking == null) {
            thinking = getString(dataMap, "choices.0.delta.reasoning_content");
        }
        if (thinking == null) {
            thinking = getString(dataMap, "choices.0.delta.reasoning");
        }
        chunk.setThinking(thinking);

        chunk.setFinishReason(normalizeFinishReason(getString(dataMap, "choices.0.finish_reason")));

        return chunk;
    }

    @Override
    public Map<String, Object> convertMessage(ChatMessage message, LlmModelModel modelConfig,
                                               boolean isLast, ChatOptions options) {
        Map<String, Object> msgMap = new LinkedHashMap<>();

        msgMap.put("role", getRole(message));

        String content = message.getContent();
        if (isLast && modelConfig != null) {
            content = applyThinkingPrompt(content, modelConfig, options);
        }
        msgMap.put("content", content);

        if (message instanceof ChatAssistantMessage) {
            ChatAssistantMessage assistantMsg = (ChatAssistantMessage) message;
            if (assistantMsg.getThink() != null) {
                msgMap.put("thinking", assistantMsg.getThink());
            }
            if (assistantMsg.getToolCalls() != null && !assistantMsg.getToolCalls().isEmpty()) {
                msgMap.put("tool_calls", convertToolCalls(assistantMsg.getToolCalls()));
            }
        }

        if (message instanceof ChatToolResponseMessage) {
            ChatToolResponseMessage toolMsg = (ChatToolResponseMessage) message;
            msgMap.put("tool_call_id", toolMsg.getToolCallId());
            msgMap.put("name", toolMsg.getName());
        }

        return msgMap;
    }

    @Override
    public String getRole(ChatMessage message) {
        return getBaseRole(message);
    }

    // OpenAI 使用默认的 convertToolDefinitions 实现

    // ==================== 私有辅助方法 ====================

    private List<Map<String, Object>> buildMessages(ChatRequest request, LlmModelModel modelConfig) {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (request.getMessages() == null) {
            return messages;
        }

        ChatMessage lastMessage = request.getLastMessage();
        ChatOptions options = request.getOptions();

        for (ChatMessage msg : request.getMessages()) {
            // OpenAI 支持 system 消息作为 messages 数组的一部分
            messages.add(convertMessage(msg, modelConfig, msg == lastMessage, options));
        }

        return messages;
    }

    private List<Map<String, Object>> convertToolCalls(List<ChatToolCall> toolCalls) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ChatToolCall toolCall : toolCalls) {
            Map<String, Object> callJson = new LinkedHashMap<>();
            callJson.put("id", toolCall.getId());
            callJson.put("type", "function");

            Map<String, Object> functionJson = new LinkedHashMap<>();
            functionJson.put("name", toolCall.getName());
            functionJson.put("arguments", JSON.stringify(toolCall.getArguments()));

            callJson.put("function", functionJson);
            result.add(callJson);
        }
        return result;
    }

    private String getString(Map<String, Object> map, String path) {
        return getStringByPath(map, path);
    }
}
