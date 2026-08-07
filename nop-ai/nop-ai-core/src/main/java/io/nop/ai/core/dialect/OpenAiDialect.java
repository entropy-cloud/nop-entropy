package io.nop.ai.core.dialect;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatReasoningMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
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
    @SuppressWarnings("unchecked")
    public ChatRequest parseRequestBody(Map<String, Object> body) {
        ChatRequest request = new ChatRequest();
        // parse messages
        List<Map<String, Object>> rawMessages = (List<Map<String, Object>>) body.get("messages");
        if (rawMessages != null) {
            List<ChatMessage> messages = new ArrayList<>();
            for (Map<String, Object> raw : rawMessages) {
                String role = (String) raw.get("role");
                String content = raw.get("content") != null ? raw.get("content").toString() : "";
                if ("system".equals(role)) {
                    messages.add(new ChatSystemMessage(content));
                } else if ("assistant".equals(role)) {
                    ChatAssistantMessage msg = new ChatAssistantMessage(content);
                    List<Map<String, Object>> tcs = (List<Map<String, Object>>) raw.get("tool_calls");
                    if (tcs != null) {
                        List<ChatToolCall> toolCalls = new ArrayList<>();
                        for (Map<String, Object> tc : tcs) {
                            ChatToolCall c = new ChatToolCall();
                            c.setId((String) tc.get("id"));
                            Object funcObj = tc.get("function");
                            if (funcObj instanceof Map) {
                                Map<String, Object> func = (Map<String, Object>) funcObj;
                                c.setName((String) func.get("name"));
                            }
                            toolCalls.add(c);
                        }
                        msg.setToolCalls(toolCalls);
                    }
                    messages.add(msg);
                } else if ("tool".equals(role)) {
                    messages.add(new ChatToolResponseMessage(
                            (String) raw.get("tool_call_id"), (String) raw.get("name"), content));
                } else {
                    messages.add(new ChatUserMessage(content));
                }
            }
            request.setMessages(messages);
        }
        // parse options
        ChatOptions options = new ChatOptions();
        if (body.get("temperature") instanceof Number) {
            options.setTemperature(((Number) body.get("temperature")).floatValue());
        }
        if (body.get("max_tokens") instanceof Number) {
            options.setMaxTokens(((Number) body.get("max_tokens")).intValue());
        }
        if (body.get("top_p") instanceof Number) {
            options.setTopP(((Number) body.get("top_p")).floatValue());
        }
        request.setOptions(options);
        // parse tools
        List<Map<String, Object>> rawTools = (List<Map<String, Object>>) body.get("tools");
        if (rawTools != null) {
            List<ChatToolDefinition> tools = new ArrayList<>();
            for (Map<String, Object> raw : rawTools) {
                Map<String, Object> func = (Map<String, Object>) raw.get("function");
                ChatToolDefinition def = new ChatToolDefinition();
                def.setName((String) func.get("name"));
                def.setDescription((String) func.get("description"));
                def.setParameters((Map<String, Object>) func.get("parameters"));
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

        // Plan 326 双轨：旧 message 保留，同时按语义顺序产出 messages 序列（reasoning → assistant text）。
        // OpenAi parseResponse 当前不解析 tool_calls（保持现状），故 messages 仅含 reasoning + assistant。
        List<ChatMessage> messages = new ArrayList<>();
        if (thinking != null) {
            messages.add(new ChatReasoningMessage(thinking));
        }
        messages.add(message);
        response.setMessages(messages);

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

        // choices[0].delta
        Object deltaObj = getByPath(dataMap, "choices.0.delta");
        Map<String, Object> deltaMap = deltaObj instanceof Map ? (Map<String, Object>) deltaObj : null;

        if (deltaMap != null) {
            // 文本内容增量（空串不是有效增量，OpenAI 常与 reasoning_content 同发空 content）
            String content = (String) deltaMap.get("content");
            boolean hasContent = content != null && !content.isEmpty();

            if (hasContent) {
                chunk.setItemType(StreamItemType.text);
                chunk.setItemIndex(0);
                chunk.setPhase(StreamItemPhase.DELTA);
                chunk.setDelta(content);
            }

            // 思考/推理内容增量（支持多种字段名）
            if (!hasContent) {
                String thinking = (String) deltaMap.get("thinking");
                if (thinking == null || thinking.isEmpty()) thinking = (String) deltaMap.get("reasoning_content");
                if (thinking == null || thinking.isEmpty()) thinking = (String) deltaMap.get("reasoning");
                if (thinking != null && !thinking.isEmpty()) {
                    chunk.setItemType(StreamItemType.reasoning);
                    chunk.setItemIndex(0);
                    chunk.setPhase(StreamItemPhase.DELTA);
                    chunk.setDelta(thinking);
                }
            }

            // 流式 tool_calls 增量（补缺口：原实现永不填充）
            if (!hasContent) {
                Object toolCallsObj = deltaMap.get("tool_calls");
                if (toolCallsObj instanceof List && !((List<?>) toolCallsObj).isEmpty()) {
                    parseToolCallDelta(chunk, (List<?>) toolCallsObj);
                }
            }
        }

        // finish 信号 → DONE
        String finishReason = normalizeFinishReason(getString(dataMap, "choices.0.finish_reason"));
        if (finishReason != null) {
            chunk.setPhase(StreamItemPhase.DONE);
            chunk.setFinishReason(finishReason);
        }

        return chunk;
    }

    /**
     * 解析流式 {@code delta.tool_calls[]} 增量。OpenAI SSE 结构：
     * <ul>
     *   <li>首个 delta（per index）：{@code {index, id, type, function:{name, arguments:""}}}
     *       → chunk(ADDED, callId=id, delta=name)</li>
     *   <li>后续 delta（per index）：{@code {index, function:{arguments:fragment}}}
     *       → chunk(DELTA, delta=arguments 片段)</li>
     * </ul>
     * 多 tool_call 靠 {@code index} 区分。单次返回一个 chunk（与非流式单 toolCall 字段
     * 等价边界；同一 SSE 事件内多 index 增量取首个）。
     */
    @SuppressWarnings("unchecked")
    private void parseToolCallDelta(ChatStreamChunk chunk, List<?> toolCalls) {
        for (Object tc : toolCalls) {
            if (!(tc instanceof Map)) continue;
            Map<String, Object> tcMap = (Map<String, Object>) tc;

            Integer index = toInt(tcMap.get("index"));
            String id = (String) tcMap.get("id");

            Map<String, Object> func = (Map<String, Object>) tcMap.get("function");
            String name = func != null ? (String) func.get("name") : null;
            String args = func != null ? (String) func.get("arguments") : null;

            // id 或 name 存在 → 首见声明（ADDED）；否则为 arguments 增量（DELTA）
            boolean isAdded = id != null || name != null;
            if (isAdded) {
                chunk.setItemType(StreamItemType.tool_call);
                chunk.setItemIndex(index);
                chunk.setCallId(id);
                chunk.setPhase(StreamItemPhase.ADDED);
                chunk.setDelta(name);
            } else {
                chunk.setItemType(StreamItemType.tool_call);
                chunk.setItemIndex(index);
                chunk.setPhase(StreamItemPhase.DELTA);
                chunk.setDelta(args);
            }
            return; // 单 chunk 返回，取首个 entry
        }
    }

    private Object getByPath(Map<String, Object> map, String path) {
        return io.nop.core.reflect.bean.BeanTool.getComplexProperty(map, path);
    }

    private Integer toInt(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        return null;
    }

    @Override
    public Map<String, Object> convertMessage(ChatMessage message, LlmModelModel modelConfig,
                                               ChatOptions options) {
        Map<String, Object> msgMap = new LinkedHashMap<>();

        msgMap.put("role", getRole(message));
        msgMap.put("content", message.getContent());

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
            Map<String, Object> msgMap = convertMessage(msg, modelConfig, options);
            if (msg == lastMessage && modelConfig != null) {
                msgMap.put("content", applyThinkingPrompt((String) msgMap.get("content"), modelConfig, options));
            }
            messages.add(msgMap);
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
