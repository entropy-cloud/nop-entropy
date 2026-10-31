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
import io.nop.core.reflect.bean.BeanTool;
import io.nop.http.api.client.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
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

    private static final Logger LOG = LoggerFactory.getLogger(OpenAiDialect.class);

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
                    messages.add(msg);
                    List<Map<String, Object>> tcs = (List<Map<String, Object>>) raw.get("tool_calls");
                    if (tcs != null) {
                        for (Map<String, Object> tc : tcs) {
                            ChatToolCall c = new ChatToolCall();
                            c.setId((String) tc.get("id"));
                            Object funcObj = tc.get("function");
                            if (funcObj instanceof Map) {
                                Map<String, Object> func = (Map<String, Object>) funcObj;
                                c.setName((String) func.get("name"));
                            }
                            messages.add(ChatToolCallMessage.fromChatToolCall(c));
                        }
                    }
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
        // Plan 2519 (裁定⑧/C3-030): stream_options.include_usage — without it
        // OpenAI-compatible providers never deliver the terminal usage frame
        // and the whole streaming usage chain stays dead. Tolerated as absent
        // when a provider rejects the option (usage is an enhancement signal,
        // null-tolerant per design §3.8).
        if (stream) {
            Map<String, Object> streamOptions = new LinkedHashMap<>();
            streamOptions.put("include_usage", Boolean.TRUE);
            body.put("stream_options", streamOptions);
        }

        ChatOptions options = request.getOptions();
        if (options != null) {
            // plan 2435/WI8 声明式选项绑定（原 22 处 addOptionIfNotNull 平铺调用收敛）
            applyOptionBindings(body, options, new LinkedHashMap<String, java.util.function.Supplier<Object>>() {{
                put("temperature", () -> options.getTemperature());
                put("max_tokens", () -> resolveMaxTokens(options, modelConfig));
                put("top_p", () -> options.getTopP());
                put("top_k", () -> options.getTopK());
                put("stop", () -> options.getStop());
            }});

            // Plan 2302 (WI20): per-provider cache-key placement — the
            // OpenAI field form. Config-gated and session-keyed; missing
            // session id skips the placement (optimization semantics,
            // llm-layer cache-key placement section).
            if (modelConfig != null && Boolean.TRUE.equals(modelConfig.getPromptCacheKey())
                    && options.getSessionId() != null && !options.getSessionId().isEmpty()) {
                body.put("prompt_cache_key", options.getSessionId());
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

        // Plan 329：单一拆分模型产出。reasoning → ChatReasoningMessage，assistant 文本 → ChatAssistantMessage。
        // 非流式 tool_calls 在此解析并回填为独立 ChatToolCallMessage（与流式路径 parseStreamChunk 的
        // tool_calls 增量等价覆盖；与 Anthropic/Gemini/Ollama/Responses 非流式解析行为一致）。
        List<ChatToolCall> toolCalls = parseNonStreamingToolCalls(responseMap);
        List<ChatMessage> messages = new ArrayList<>();
        if (thinking != null) {
            messages.add(new ChatReasoningMessage(thinking));
        }
        messages.add(message);
        for (ChatToolCall toolCall : toolCalls) {
            messages.add(ChatToolCallMessage.fromChatToolCall(toolCall));
        }
        response.setMessages(messages);

        // 解析元数据
        response.setId(getStringByPath(responseMap, "id"));
        response.setModel(getStringByPath(responseMap, "model"));

        // 解析结束原因
        String statusPath = responseConfig != null && responseConfig.getStatusPath() != null
                ? responseConfig.getStatusPath()
                : "choices.0.finish_reason";
        response.setFinishReason(normalizeFinishReason(getStringByPath(responseMap, statusPath)));

        // 解析 Usage（使用基类通用方法；plan 2303/WI21 缓存观测默认路径——
        // OpenAI 无 cache-write 计费概念，creation 无默认）
        response.setUsage(parseUsage(responseMap, responseConfig,
                "usage.prompt_tokens", "usage.completion_tokens", "usage.total_tokens",
                "usage.prompt_tokens_details.cached_tokens", null, "usage"));

        return response;
    }

    /**
     * Plan 2519 (裁定⑧): full enumeration — one SSE event may carry a text
     * delta, a reasoning delta, N tool_call entries AND (terminal) the usage
     * frame with empty choices. The single-chunk contract truncated to the
     * first tool_call entry (deterministic parallel-tool-call data loss).
     */
    @Override
    public List<ChatStreamChunk> parseStreamChunks(String data) {
        if (data == null || data.isEmpty() || "[DONE]".equals(data)) {
            return new ArrayList<>();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> dataMap = (Map<String, Object>) JSON.parse(data);
        List<ChatStreamChunk> chunks = new ArrayList<>();
        String requestId = getStringByPath(dataMap, "id");

        // usage frame: choices absent/empty + usage present → usage-only chunk
        ChatUsage usage = parseStreamUsage(dataMap);
        Object choicesObj = dataMap.get("choices");
        boolean noChoices = !(choicesObj instanceof List) || ((List<?>) choicesObj).isEmpty();

        Object deltaObj = BeanTool.getComplexProperty(dataMap, "choices.0.delta");
        Map<String, Object> deltaMap = deltaObj instanceof Map ? (Map<String, Object>) deltaObj : null;

        if (deltaMap != null) {
            String content = (String) deltaMap.get("content");
            if (content != null && !content.isEmpty()) {
                ChatStreamChunk text = new ChatStreamChunk();
                text.setItemType(StreamItemType.text);
                text.setItemIndex(0);
                text.setPhase(StreamItemPhase.DELTA);
                text.setDelta(content);
                chunks.add(text);
            }

            String thinking = (String) deltaMap.get("thinking");
            if (thinking == null || thinking.isEmpty()) thinking = (String) deltaMap.get("reasoning_content");
            if (thinking == null || thinking.isEmpty()) thinking = (String) deltaMap.get("reasoning");
            if (thinking != null && !thinking.isEmpty()) {
                ChatStreamChunk reasoning = new ChatStreamChunk();
                reasoning.setItemType(StreamItemType.reasoning);
                reasoning.setItemIndex(0);
                reasoning.setPhase(StreamItemPhase.DELTA);
                reasoning.setDelta(thinking);
                chunks.add(reasoning);
            }

            Object toolCallsObj = deltaMap.get("tool_calls");
            if (toolCallsObj instanceof List) {
                // 裁定⑧: enumerate EVERY tool_call entry in the event
                for (Object tc : (List<?>) toolCallsObj) {
                    ChatStreamChunk tcChunk = toToolCallChunk(tc);
                    if (tcChunk != null) {
                        chunks.add(tcChunk);
                    }
                }
            }
        }

        if (usage != null) {
            ChatStreamChunk usageChunk = new ChatStreamChunk();
            usageChunk.setUsage(usage);
            if (noChoices && chunks.isEmpty()) {
                // pure usage frame (include_usage terminal frame): only usage
                usageChunk.setPhase(StreamItemPhase.DONE);
            }
            chunks.add(usageChunk);
        }

        String finishReason = normalizeFinishReason(getStringByPath(dataMap, "choices.0.finish_reason"));
        if (finishReason != null) {
            // termination chunk: attach to the last enumerated chunk, or emit a
            // standalone DONE chunk when the event carries only the finish frame
            // (pre-fix single-chunk contract did the same — zero regression for
            // the plan 2505 termination-signal face)
            ChatStreamChunk last = chunks.isEmpty() ? null : chunks.get(chunks.size() - 1);
            if (last == null) {
                last = new ChatStreamChunk();
                chunks.add(last);
            }
            last.setPhase(StreamItemPhase.DONE);
            last.setFinishReason(finishReason);
        }
        if (chunks.isEmpty()) {
            return new ArrayList<>();
        }
        // propagate the response id onto every enumerated chunk (the single-chunk
        // contract set it on the one returned chunk; multi-chunk keeps per-chunk id)
        if (requestId != null) {
            for (ChatStreamChunk c : chunks) {
                if (c.getId() == null) {
                    c.setId(requestId);
                }
            }
        }
        return chunks;
    }

    private ChatStreamChunk toToolCallChunk(Object tc) {
        if (!(tc instanceof Map)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> tcMap = (Map<String, Object>) tc;
        Integer index = toInt(tcMap.get("index"));
        String id = (String) tcMap.get("id");
        @SuppressWarnings("unchecked")
        Map<String, Object> func = (Map<String, Object>) tcMap.get("function");
        String name = func != null ? (String) func.get("name") : null;
        String args = func != null ? (String) func.get("arguments") : null;

        ChatStreamChunk chunk = new ChatStreamChunk();
        chunk.setItemType(StreamItemType.tool_call);
        chunk.setItemIndex(index);
        boolean isAdded = id != null || name != null;
        if (isAdded) {
            chunk.setCallId(id);
            chunk.setPhase(StreamItemPhase.ADDED);
            chunk.setDelta(name);
        } else {
            chunk.setPhase(StreamItemPhase.DELTA);
            chunk.setDelta(args);
        }
        return chunk;
    }

    /**
     * 裁定⑧: the include_usage terminal frame carries {@code usage} with
     * empty choices — parse prompt/completion/total tokens, null-tolerant.
     */
    private ChatUsage parseStreamUsage(Map<String, Object> dataMap) {
        Object usageObj = dataMap.get("usage");
        if (!(usageObj instanceof Map)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> usageMap = (Map<String, Object>) usageObj;
        ChatUsage usage = new ChatUsage();
        usage.setPromptTokens(getIntByPath(usageMap, "prompt_tokens"));
        usage.setCompletionTokens(getIntByPath(usageMap, "completion_tokens"));
        usage.setTotalTokens(getIntByPath(usageMap, "total_tokens"));
        return usage;
    }

    /**
     * Plan 2519 (裁定⑧): single-chunk compatibility delegate — returns the
     * first enumerated chunk (ILlmDialect contract unchanged). Full consumers
     * must use {@link #parseStreamChunks}.
     */
    @Override
    public ChatStreamChunk parseStreamChunk(String data) {
        List<ChatStreamChunk> chunks = parseStreamChunks(data);
        return chunks.isEmpty() ? null : chunks.get(0);
    }

    /**
     * 解析非流式响应体中的 tool_calls（OpenAI 结构：{@code choices[0].message.tool_calls[]}，
     * 每项 {@code {id, type, function:{name, arguments}}}，arguments 为 JSON 字符串或结构化 Map）。
     * 与流式 {@code parseToolCallDelta} 是同一 OpenAI tool_calls 契约的两种形态；其余 4 个 dialect
     * （Anthropic/Gemini/Ollama/Responses）的非流式 parseResponse 均解析工具调用，本方法补齐 OpenAI
     * 非流式路径，消除"零工具调用零报错"的静默丢弃。
     * <p>
     * 畸形 arguments JSON 不静默吞：WARN 日志（含 tool call id/name）+ arguments 置 {@code null}
     * （与 {@code ChatServiceImpl.ToolCallAccumulator} 处置一致——下游可区分合法 {@code {}} 空参数
     * 与畸形输入，repair 层 {@code ArgumentStructureRepairStage} 会把 null 归一到空 Map）。
     */
    @SuppressWarnings("unchecked")
    private List<ChatToolCall> parseNonStreamingToolCalls(Map<String, Object> responseMap) {
        Object toolCallsObj = BeanTool.getComplexProperty(responseMap, "choices.0.message.tool_calls");
        if (!(toolCallsObj instanceof List)) {
            return Collections.emptyList();
        }
        List<ChatToolCall> toolCalls = new ArrayList<>();
        for (Object tc : (List<?>) toolCallsObj) {
            if (!(tc instanceof Map)) {
                continue;
            }
            Map<String, Object> tcMap = (Map<String, Object>) tc;
            ChatToolCall toolCall = new ChatToolCall();
            toolCall.setId((String) tcMap.get("id"));
            Object funcObj = tcMap.get("function");
            if (funcObj instanceof Map) {
                Map<String, Object> funcMap = (Map<String, Object>) funcObj;
                toolCall.setName((String) funcMap.get("name"));
                Object argsObj = funcMap.get("arguments");
                if (argsObj instanceof Map) {
                    toolCall.setArguments((Map<String, Object>) argsObj);
                } else if (argsObj instanceof String) {
                    toolCall.setArguments(parseArgumentsJson((String) argsObj, toolCall.getId(), toolCall.getName()));
                }
            }
            toolCalls.add(toolCall);
        }
        return toolCalls;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArgumentsJson(String argsStr, String callId, String name) {
        try {
            Object parsed = JSON.parse(argsStr);
            if (parsed instanceof Map) {
                return (Map<String, Object>) parsed;
            }
            LOG.warn("nop.ai.parse-tool-args-invalid: callId={}, name={}, tool arguments is not a JSON object",
                    callId, name);
            return null;
        } catch (Exception e) {
            LOG.warn("nop.ai.parse-tool-args-fail: callId={}, name={}, malformed tool arguments JSON",
                    callId, name);
            return null;
        }
    }



    @Override
    public Map<String, Object> convertMessage(ChatMessage message, LlmModelModel modelConfig,
                                               ChatOptions options) {
        Map<String, Object> msgMap = new LinkedHashMap<>();

        msgMap.put("role", getRole(message));

        if (message instanceof ChatToolCallMessage) {
            // P1-6.7-002（plan 2399）：tool_calls 字段承载调用参数——不把 arguments JSON
            // 重复置入 content（请求体污染）。
            ChatToolCallMessage toolCallMsg = (ChatToolCallMessage) message;
            ChatToolCall tc = new ChatToolCall();
            tc.setId(toolCallMsg.getCallId());
            tc.setName(toolCallMsg.getName());
            tc.setArguments(toolCallMsg.getArguments());
            msgMap.put("tool_calls", convertToolCalls(java.util.Collections.singletonList(tc)));
        } else if (message instanceof ChatReasoningMessage) {
            // P1-6.7-002（plan 2399）：reasoning 只发 thinking 载荷——不再双发裸文本 content。
            msgMap.put("thinking", message.getContent());
        } else {
            msgMap.put("content", message.getContent());
        }

        if (message instanceof ChatToolResponseMessage) {
            ChatToolResponseMessage toolMsg = (ChatToolResponseMessage) message;
            msgMap.put("tool_call_id", toolMsg.getCallId());
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
        return super.buildMessages(request, modelConfig, this::convertMessage, false);
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

}
