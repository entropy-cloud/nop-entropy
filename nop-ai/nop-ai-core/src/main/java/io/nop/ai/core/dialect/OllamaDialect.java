package io.nop.ai.core.dialect;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatReasoningMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolCallMessage;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
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
 * Ollama 方言实现。
 * <p>
 * 处理 Ollama 本地部署 API 格式：
 * <pre>
 * Request:
 * {
 *   "model": "llama2",
 *   "messages": [{"role": "user", "content": "..."}],
 *   "options": {
 *     "temperature": 0.7,
 *     "num_predict": 1000
 *   },
 *   "stream": true
 * }
 *
 * Response:
 * {
 *   "model": "llama2",
 *   "message": {"role": "assistant", "content": "..."},
 *   "done_reason": "stop",
 *   "prompt_eval_count": 10,
 *   "eval_count": 20
 * }
 * </pre>
 */
public class OllamaDialect extends AbstractLlmDialect implements ILlmDialect {

    @Override
    public String getName() {
        return "ollama";
    }

    @Override
    public String buildUrl(String baseUrl, String chatUrl, String apiKey) {
        return StringHelper.appendPath(baseUrl, chatUrl);
    }

    @Override
    public void setHeaders(HttpRequest httpRequest, String apiKey, String apiKeyHeader) {
        httpRequest.setHeader("Content-Type", "application/json");
        // Ollama 通常不需要 API key
        if (!StringHelper.isEmpty(apiKey)) {
            if (apiKeyHeader != null) {
                httpRequest.setHeader(apiKeyHeader, apiKey);
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
            Map<String, Object> ollamaOptions = new LinkedHashMap<>();
            addOptionIfNotNull(ollamaOptions, "temperature", options.getTemperature());
            addOptionIfNotNull(ollamaOptions, "num_predict", resolveMaxTokens(options, modelConfig));
            addOptionIfNotNull(ollamaOptions, "top_p", options.getTopP());
            addOptionIfNotNull(ollamaOptions, "top_k", options.getTopK());
            addOptionIfNotNull(ollamaOptions, "stop", options.getStop());

            if (!ollamaOptions.isEmpty()) {
                body.put("options", ollamaOptions);
            }

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

        // Ollama 使用 message.content 路径
        String contentPath = responseConfig != null && responseConfig.getContentPath() != null
                ? responseConfig.getContentPath() : "message.content";
        String content = getStringByPath(responseMap, contentPath);

        // 解析思考内容（Ollama 某些模型支持 thinking 字段）
        String thinking = getStringByPath(responseMap, "message.thinking");

        ChatAssistantMessage message = new ChatAssistantMessage();
        message.setContent(content);

        // 解析工具调用（Ollama 使用 OpenAI 风格）。tool_calls 嵌套在 message 下，需按路径取值
        // （与 content 的 message.content 路径一致；历史 message.tool_calls 字面 key 取值取不到嵌套结构）。
        Object messageNode = responseMap.get("message");
        Object toolCallsObj = null;
        if (messageNode instanceof Map) {
            toolCallsObj = ((Map<?, ?>) messageNode).get("tool_calls");
        }
        List<ChatToolCall> toolCalls = null;
        if (toolCallsObj instanceof List) {
            toolCalls = new ArrayList<>();
            for (Object tc : (List<?>) toolCallsObj) {
                if (tc instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> tcMap = (Map<String, Object>) tc;
                    ChatToolCall toolCall = new ChatToolCall();
                    toolCall.setId((String) tcMap.get("id"));
                    Object funcObj = tcMap.get("function");
                    if (funcObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> funcMap = (Map<String, Object>) funcObj;
                        toolCall.setName((String) funcMap.get("name"));
                        Object argsObj = funcMap.get("arguments");
                        if (argsObj instanceof Map) {
                            toolCall.setArguments((Map<String, Object>) argsObj);
                        } else if (argsObj instanceof String) {
                            // arguments 可能是 JSON 字符串
                            try {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> argsMap = (Map<String, Object>) JSON.parse((String) argsObj);
                                toolCall.setArguments(argsMap);
                            } catch (Exception ignored) {
                                // Tolerate malformed arguments JSON from the model: leave arguments unset
                            }
                        }
                    }
                    toolCalls.add(toolCall);
                }
            }
        }


        // Plan 329：单一拆分模型产出。reasoning → ChatReasoningMessage、assistant 文本 → ChatAssistantMessage、
        // 每个 tool_call → 独立 ChatToolCallMessage。
        List<ChatMessage> messages = new ArrayList<>();
        if (thinking != null) {
            messages.add(new ChatReasoningMessage(thinking));
        }
        messages.add(message);
        if (toolCalls != null) {
            for (ChatToolCall toolCall : toolCalls) {
                messages.add(ChatToolCallMessage.fromChatToolCall(toolCall));
            }
        }
        response.setMessages(messages);

        // 模型名称
        response.setModel(getStringByPath(responseMap, "model"));

        // 结束原因
        String statusPath = responseConfig != null && responseConfig.getStatusPath() != null
                ? responseConfig.getStatusPath() : "done_reason";
        response.setFinishReason(normalizeFinishReason(getStringByPath(responseMap, statusPath)));

        // Usage（使用基类通用方法）
        response.setUsage(parseUsage(responseMap, responseConfig,
                "prompt_eval_count", "eval_count", null));

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

        chunk.setModel(getString(dataMap, "model"));

        // 文本内容增量
        String content = getString(dataMap, "message.content");
        // 思考内容增量（某些模型支持 thinking 字段，如 DeepSeek R1）
        String thinking = getString(dataMap, "message.thinking");

        if (content != null) {
            chunk.setItemType(StreamItemType.text);
            chunk.setItemIndex(0);
            chunk.setPhase(StreamItemPhase.DELTA);
            chunk.setDelta(content);
        } else if (thinking != null) {
            chunk.setItemType(StreamItemType.reasoning);
            chunk.setItemIndex(0);
            chunk.setPhase(StreamItemPhase.DELTA);
            chunk.setDelta(thinking);
        } else {
            // 工具调用（Ollama 使用 OpenAI 风格，流式下通常完整出现于终止帧）
            Object toolCallsObj = getByPath(dataMap, "message.tool_calls");
            if (toolCallsObj instanceof List && !((List<?>) toolCallsObj).isEmpty()) {
                Object first = ((List<?>) toolCallsObj).get(0);
                if (first instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> tcMap = (Map<String, Object>) first;
                    Map<String, Object> func = (Map<String, Object>) tcMap.get("function");
                    chunk.setItemType(StreamItemType.tool_call);
                    chunk.setItemIndex(0);
                    chunk.setCallId((String) tcMap.get("id"));
                    chunk.setPhase(StreamItemPhase.ADDED);
                    chunk.setDelta(func != null ? (String) func.get("name") : null);
                }
            }
        }

        // 结束信号
        String finishReason = normalizeFinishReason(getString(dataMap, "done_reason"));
        if (finishReason != null) {
            chunk.setPhase(StreamItemPhase.DONE);
            chunk.setFinishReason(finishReason);
        }

        return chunk;
    }

    private Object getByPath(Map<String, Object> map, String path) {
        return io.nop.core.reflect.bean.BeanTool.getComplexProperty(map, path);
    }

    @Override
    public Map<String, Object> convertMessage(ChatMessage message, LlmModelModel modelConfig,
                                               ChatOptions options) {
        Map<String, Object> msgMap = new LinkedHashMap<>();

        msgMap.put("role", getRole(message));
        msgMap.put("content", message.getContent());

        if (message instanceof ChatReasoningMessage) {
            msgMap.put("thinking", message.getContent());
        }

        if (message instanceof ChatToolCallMessage) {
            ChatToolCallMessage toolCallMsg = (ChatToolCallMessage) message;
            ChatToolCall tc = new ChatToolCall();
            tc.setId(toolCallMsg.getCallId());
            tc.setName(toolCallMsg.getName());
            tc.setArguments(toolCallMsg.getArguments());
            msgMap.put("tool_calls", convertToolCalls(java.util.Collections.singletonList(tc)));
        }

        if (message instanceof ChatToolResponseMessage) {
            ChatToolResponseMessage toolMsg = (ChatToolResponseMessage) message;
            msgMap.put("tool_call_id", toolMsg.getCallId());
            if (toolMsg.getName() != null) {
                msgMap.put("name", toolMsg.getName());
            }
        }

        return msgMap;
    }

    /**
     * 转换工具调用列表为 Ollama 格式
     */
    private List<Map<String, Object>> convertToolCalls(List<ChatToolCall> toolCalls) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ChatToolCall toolCall : toolCalls) {
            Map<String, Object> tcMap = new LinkedHashMap<>();
            tcMap.put("id", toolCall.getId());
            tcMap.put("type", "function");
            Map<String, Object> funcMap = new LinkedHashMap<>();
            funcMap.put("name", toolCall.getName());
            funcMap.put("arguments", toolCall.getArguments());
            tcMap.put("function", funcMap);
            result.add(tcMap);
        }
        return result;
    }

    @Override
    public String getRole(ChatMessage message) {
        return getBaseRole(message);
    }

    // Ollama 使用默认的 convertToolDefinitions 实现（与 OpenAI 相同）

    // ==================== 私有辅助方法 ====================

    private List<Map<String, Object>> buildMessages(ChatRequest request, LlmModelModel modelConfig) {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (request.getMessages() == null) {
            return messages;
        }

        ChatMessage lastMessage = request.getLastMessage();
        ChatOptions options = request.getOptions();

        for (ChatMessage msg : request.getMessages()) {
            if (msg instanceof io.nop.ai.api.chat.messages.ChatSystemMessage) {
                continue;
            }
            Map<String, Object> msgMap = convertMessage(msg, modelConfig, options);
            if (msg == lastMessage && modelConfig != null) {
                msgMap.put("content", applyThinkingPrompt((String) msgMap.get("content"), modelConfig, options));
            }
            messages.add(msgMap);
        }

        return messages;
    }

    private String getString(Map<String, Object> map, String path) {
        return getStringByPath(map, path);
    }
}
