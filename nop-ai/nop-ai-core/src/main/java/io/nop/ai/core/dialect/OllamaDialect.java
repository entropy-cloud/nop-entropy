package io.nop.ai.core.dialect;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatReasoningMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolCallMessage;
import io.nop.ai.api.chat.messages.ChatToolDefinition;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
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
    @SuppressWarnings("unchecked")
    public ChatRequest parseRequestBody(Map<String, Object> body) {
        ChatRequest request = new ChatRequest();

        // messages：OpenAI 风格（role/content/thinking/tool_calls/tool_call_id）
        List<Map<String, Object>> rawMessages = (List<Map<String, Object>>) body.get("messages");
        if (rawMessages != null) {
            List<ChatMessage> messages = new ArrayList<>();
            for (Map<String, Object> raw : rawMessages) {
                String role = (String) raw.get("role");
                String content = raw.get("content") != null ? raw.get("content").toString() : "";
                String thinking = raw.get("thinking") != null ? raw.get("thinking").toString() : null;
                if ("assistant".equals(role)) {
                    // tool_calls 同发时 content 为 arguments 序列化伪影（convertMessage 副作用），
                    // 不再产出 assistant 文本消息（roundtrip 落档：样本不含文本+tool_calls 同发）
                    List<Map<String, Object>> tcs = (List<Map<String, Object>>) raw.get("tool_calls");
                    if (tcs != null && !tcs.isEmpty()) {
                        for (Map<String, Object> tc : tcs) {
                            ChatToolCall c = new ChatToolCall();
                            c.setId((String) tc.get("id"));
                            Object funcObj = tc.get("function");
                            if (funcObj instanceof Map) {
                                Map<String, Object> func = (Map<String, Object>) funcObj;
                                c.setName((String) func.get("name"));
                                Object argsObj = func.get("arguments");
                                if (argsObj instanceof Map) {
                                    c.setArguments((Map<String, Object>) argsObj);
                                } else if (argsObj instanceof String) {
                                    // arguments 可能是 JSON 字符串（与 parseResponse 同款降级）
                                    try {
                                        Object parsed = JSON.parse((String) argsObj);
                                        if (parsed instanceof Map) {
                                            c.setArguments((Map<String, Object>) parsed);
                                        }
                                    } catch (Exception ignored) {
                                        // 容忍模型返回的畸形 arguments JSON：留空由调用方处理
                                    }
                                }
                            }
                            messages.add(ChatToolCallMessage.fromChatToolCall(c));
                        }
                    } else {
                        if (thinking != null && !thinking.isEmpty()) {
                            messages.add(new ChatReasoningMessage(thinking));
                        }
                        messages.add(new ChatAssistantMessage(content));
                    }
                } else if ("tool".equals(role)) {
                    messages.add(new ChatToolResponseMessage(
                            (String) raw.get("tool_call_id"), (String) raw.get("name"), content));
                } else {
                    if (thinking != null && !thinking.isEmpty()) {
                        messages.add(new ChatReasoningMessage(thinking));
                    }
                    messages.add(new ChatUserMessage(content));
                }
            }
            request.setMessages(messages);
        }

        // options：temperature/num_predict/top_p/top_k/stop
        ChatOptions options = new ChatOptions();
        Object optionsObj = body.get("options");
        if (optionsObj instanceof Map) {
            Map<String, Object> ollamaOptions = (Map<String, Object>) optionsObj;
            if (ollamaOptions.get("temperature") instanceof Number) {
                options.setTemperature(((Number) ollamaOptions.get("temperature")).floatValue());
            }
            if (ollamaOptions.get("num_predict") instanceof Number) {
                options.setMaxTokens(((Number) ollamaOptions.get("num_predict")).intValue());
            }
            if (ollamaOptions.get("top_p") instanceof Number) {
                options.setTopP(((Number) ollamaOptions.get("top_p")).floatValue());
            }
            if (ollamaOptions.get("top_k") instanceof Number) {
                options.setTopK(((Number) ollamaOptions.get("top_k")).intValue());
            }
            if (ollamaOptions.get("stop") instanceof List) {
                options.setStop((List<String>) ollamaOptions.get("stop"));
            }
        }
        request.setOptions(options);

        // tools：OpenAI 嵌套格式（type/function.{name,description,parameters}）
        List<Map<String, Object>> rawTools = (List<Map<String, Object>>) body.get("tools");
        if (rawTools != null && !rawTools.isEmpty()) {
            List<ChatToolDefinition> tools = new ArrayList<>();
            for (Map<String, Object> raw : rawTools) {
                Map<String, Object> func = (Map<String, Object>) raw.get("function");
                if (func == null) {
                    continue;
                }
                ChatToolDefinition def = new ChatToolDefinition();
                def.setName((String) func.get("name"));
                def.setDescription((String) func.get("description"));
                if (func.get("parameters") instanceof Map) {
                    def.setParameters((Map<String, Object>) func.get("parameters"));
                }
                tools.add(def);
            }
            request.setTools(tools);
        }
        return request;
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
                    // Ollama 流式 tool_calls 单事件完整下发 name + args。args 可为结构化 Map
                    // 或 JSON 字符串（与 parseResponse/parseRequestBody 同款双形态），经 arguments
                    // 通道同载完整 JSON 文本，弥补单 chunk 无法再发 DELTA 片段的通道缺口。
                    if (func != null) {
                        Object argsObj = func.get("arguments");
                        if (argsObj instanceof Map && !((Map<?, ?>) argsObj).isEmpty()) {
                            chunk.setArguments(JSON.stringify(argsObj));
                        } else if (argsObj instanceof String && !((String) argsObj).isEmpty()) {
                            chunk.setArguments((String) argsObj);
                        }
                    }
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

    // ==================== 反向转换：ChatResponse → Provider 响应 Map ====================

    @Override
    public Map<String, Object> buildResponse(ChatResponse response) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (response.getModel() != null) {
            result.put("model", response.getModel());
        }

        // messages 序列 → message（content + thinking + tool_calls）
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        StringBuilder content = new StringBuilder();
        StringBuilder thinking = new StringBuilder();
        List<Map<String, Object>> toolCalls = new ArrayList<>();
        if (response.getMessages() != null) {
            for (ChatMessage msg : response.getMessages()) {
                if (msg instanceof ChatReasoningMessage) {
                    if (msg.getContent() != null) {
                        if (thinking.length() > 0) {
                            thinking.append("\n");
                        }
                        thinking.append(msg.getContent());
                    }
                } else if (msg instanceof ChatToolCallMessage) {
                    ChatToolCallMessage tcm = (ChatToolCallMessage) msg;
                    Map<String, Object> tc = new LinkedHashMap<>();
                    tc.put("id", tcm.getCallId());
                    tc.put("type", "function");
                    Map<String, Object> func = new LinkedHashMap<>();
                    func.put("name", tcm.getName());
                    if (tcm.getArguments() != null) {
                        func.put("arguments", tcm.getArguments());
                    }
                    tc.put("function", func);
                    toolCalls.add(tc);
                } else if (msg instanceof ChatAssistantMessage && msg.getContent() != null) {
                    if (content.length() > 0) {
                        content.append("\n");
                    }
                    content.append(msg.getContent());
                }
            }
        }
        if (content.length() > 0) {
            message.put("content", content.toString());
        }
        if (thinking.length() > 0) {
            message.put("thinking", thinking.toString());
        }
        if (!toolCalls.isEmpty()) {
            message.put("tool_calls", toolCalls);
        }
        result.put("message", message);

        // 归一化 finish_reason 与 Ollama done_reason 原生词汇重合（stop/length），直接透传
        if (response.getFinishReason() != null) {
            result.put("done_reason", response.getFinishReason());
        }

        // usage → prompt_eval_count/eval_count
        if (response.getUsage() != null) {
            if (response.getUsage().getPromptTokens() != null) {
                result.put("prompt_eval_count", response.getUsage().getPromptTokens());
            }
            if (response.getUsage().getCompletionTokens() != null) {
                result.put("eval_count", response.getUsage().getCompletionTokens());
            }
        }
        return result;
    }

    @Override
    public Map<String, Object> buildStreamChunk(ChatStreamChunk chunk) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (chunk.getModel() != null) {
            result.put("model", chunk.getModel());
        }

        if (chunk.getPhase() == StreamItemPhase.DONE) {
            // 终止信号 → done_reason
            if (chunk.getFinishReason() != null) {
                result.put("done_reason", chunk.getFinishReason());
            }
            return result;
        }

        StreamItemType type = chunk.getItemType();
        if (type != null) {
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("role", "assistant");
            if (type == StreamItemType.reasoning) {
                message.put("thinking", chunk.getDelta());
            } else if (type == StreamItemType.tool_call) {
                Map<String, Object> tc = new LinkedHashMap<>();
                tc.put("id", chunk.getCallId());
                tc.put("type", "function");
                Map<String, Object> func = new LinkedHashMap<>();
                func.put("name", chunk.getDelta());
                // 完整 arguments 通道回填（Ollama 原生形态为结构化 arguments）
                if (chunk.getArguments() != null) {
                    Object args = JSON.parse(chunk.getArguments());
                    if (args instanceof Map) {
                        func.put("arguments", args);
                    }
                }
                tc.put("function", func);
                message.put("tool_calls", List.of(tc));
            } else {
                message.put("content", chunk.getDelta());
            }
            result.put("message", message);
        }
        return result;
    }

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
