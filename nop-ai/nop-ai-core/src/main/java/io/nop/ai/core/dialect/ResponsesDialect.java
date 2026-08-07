package io.nop.ai.core.dialect;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.ResponseFormat;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatReasoningMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatToolCallMessage;
import io.nop.ai.api.chat.messages.ChatToolDefinition;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.messages.ChatUsage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.api.chat.stream.StreamItemPhase;
import io.nop.ai.api.chat.stream.StreamItemType;
import io.nop.ai.core.model.LlmModel;
import io.nop.ai.core.model.LlmModelModel;
import io.nop.api.core.json.JSON;
import io.nop.commons.util.StringHelper;
import io.nop.http.api.client.HttpRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Responses API 方言实现（plan 330）。
 * <p>
 * 消费上游 {@code /v1/responses} 端点（OpenAI Responses wire），将 nop-ai 的单一拆分消息模型
 * （plan 325-329）与 Responses 的 typed items 双向转换：
 * <ul>
 *   <li>请求方向：{@code messages} → 顶层 {@code instructions} + {@code input[]} typed items</li>
 *   <li>响应方向：{@code output[]} typed items → {@code response.messages}</li>
 *   <li>流式方向：Responses 具名事件 → item 增量 chunk（与 328 的 chunk 模型同构）</li>
 * </ul>
 * 无状态（design #10）：{@code store=false}，多轮回放靠 {@code messages} 序列 append。
 * <p>
 * 设计来源：{@code ai-dev/design/nop-ai-responses-migration-design.md} §3.4。
 */
public class ResponsesDialect extends AbstractLlmDialect implements ILlmDialect {

    private static final String EVENT_RESPONSE_CREATED = "response.created";
    private static final String EVENT_RESPONSE_IN_PROGRESS = "response.in_progress";
    private static final String EVENT_OUTPUT_ITEM_ADDED = "response.output_item.added";
    private static final String EVENT_OUTPUT_TEXT_DELTA = "response.output_text.delta";
    private static final String EVENT_REASONING_SUMMARY_DELTA = "response.reasoning_summary_text.delta";
    private static final String EVENT_FUNCTION_CALL_ARGS_DELTA = "response.function_call_arguments.delta";
    private static final String EVENT_RESPONSE_COMPLETED = "response.completed";
    private static final String EVENT_RESPONSE_FAILED = "response.failed";
    private static final String EVENT_RESPONSE_INCOMPLETE = "response.incomplete";

    @Override
    public String getName() {
        return "responses";
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

    // ==================== buildBody（请求方向） ====================

    @Override
    public Map<String, Object> buildBody(ChatRequest request, LlmModel config,
                                          LlmModelModel modelConfig, String model, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("stream", stream);
        // 无状态（design #10）：不存储 response，多轮回放靠 messages 序列
        body.put("store", false);

        ChatOptions options = request.getOptions();

        // system messages → 顶层 instructions；其余消息 → input[] typed items
        List<Map<String, Object>> inputItems = new ArrayList<>();
        StringBuilder instructions = new StringBuilder();

        if (request.getMessages() != null) {
            ChatMessage lastMessage = request.getLastMessage();
            for (ChatMessage msg : request.getMessages()) {
                if (msg instanceof ChatSystemMessage) {
                    if (instructions.length() > 0) {
                        instructions.append("\n");
                    }
                    String c = msg.getContent();
                    if (c != null) {
                        instructions.append(c);
                    }
                } else {
                    Map<String, Object> item = convertMessage(msg, modelConfig, options);
                    if (msg == lastMessage && modelConfig != null) {
                        applyThinkingPromptToItem(item, modelConfig, options);
                    }
                    inputItems.add(item);
                }
            }
        }

        if (instructions.length() > 0) {
            body.put("instructions", instructions.toString());
        }
        body.put("input", inputItems);

        if (options != null) {
            addOptionIfNotNull(body, "temperature", options.getTemperature());
            addOptionIfNotNull(body, "top_p", options.getTopP());
            addOptionIfNotNull(body, "max_output_tokens", resolveMaxTokens(options, modelConfig));

            // responseFormat → text.format（design §3.4）
            ResponseFormat rf = options.getResponseFormatConfig();
            if (rf != null && rf.getType() != null) {
                Map<String, Object> format = new LinkedHashMap<>();
                format.put("type", rf.getType());
                if (rf.getSchema() != null) {
                    format.put("schema", rf.getSchema());
                }
                Map<String, Object> text = new LinkedHashMap<>();
                text.put("format", format);
                body.put("text", text);
            }

            // tools — 仅 function 类型，剥离 hosted tools（design §3 映射表 #2）
            if (options.getTools() != null && !options.getTools().isEmpty()) {
                List<Map<String, Object>> tools = convertToolDefinitions(options.getTools());
                if (tools != null && !tools.isEmpty()) {
                    body.put("tools", tools);
                }
            }

            if (options.getToolChoice() != null) {
                body.put("tool_choice", options.getToolChoice());
            }
        }

        return body;
    }

    /**
     * 对最后一条消息的文本 content part 应用思考模式提示词（与 OpenAiDialect 的
     * {@code applyThinkingPrompt} 语义一致，适配 Responses 的 content parts 结构）。
     */
    @SuppressWarnings("unchecked")
    private void applyThinkingPromptToItem(Map<String, Object> item, LlmModelModel modelConfig,
                                            ChatOptions options) {
        Object contentObj = item.get("content");
        if (contentObj instanceof List) {
            for (Object part : (List<?>) contentObj) {
                if (part instanceof Map) {
                    Map<String, Object> partMap = (Map<String, Object>) part;
                    Object text = partMap.get("text");
                    if (text instanceof String) {
                        partMap.put("text", applyThinkingPrompt((String) text, modelConfig, options));
                    }
                }
            }
        }
    }

    // ==================== convertMessage（消息 → input item） ====================

    @Override
    public Map<String, Object> convertMessage(ChatMessage message, LlmModelModel modelConfig,
                                               ChatOptions options) {
        if (message instanceof ChatUserMessage) {
            return buildMessageItem("user", "input_text", message.getContent());
        }
        if (message instanceof ChatAssistantMessage) {
            return buildMessageItem("assistant", "output_text", message.getContent());
        }
        if (message instanceof ChatReasoningMessage) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "reasoning");
            item.put("summary", buildContentParts("summary_text", message.getContent()));
            return item;
        }
        if (message instanceof ChatToolCallMessage) {
            ChatToolCallMessage tcm = (ChatToolCallMessage) message;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "function_call");
            if (tcm.getCallId() != null) {
                item.put("call_id", tcm.getCallId());
            }
            item.put("name", tcm.getName());
            item.put("arguments", tcm.getArgumentsText() != null ? tcm.getArgumentsText() : "");
            return item;
        }
        if (message instanceof ChatToolResponseMessage) {
            ChatToolResponseMessage trm = (ChatToolResponseMessage) message;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "function_call_output");
            if (trm.getCallId() != null) {
                item.put("call_id", trm.getCallId());
            }
            item.put("output", stringifyToolOutput(trm));
            return item;
        }
        // fallback：未知消息类型视为用户输入
        return buildMessageItem("user", "input_text", message.getContent());
    }

    private Map<String, Object> buildMessageItem(String role, String textType, String text) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "message");
        item.put("role", role);
        item.put("content", buildContentParts(textType, text));
        return item;
    }

    private List<Map<String, Object>> buildContentParts(String textType, String text) {
        List<Map<String, Object>> parts = new ArrayList<>();
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("type", textType);
        part.put("text", text != null ? text : "");
        parts.add(part);
        return parts;
    }

    /**
     * 工具结果字符串化规则（design §3.4）：{@code function_call_output.output} 是普通字符串。
     * 结构化 result 对象 → JSON 序列化；否则使用 content 文本。
     */
    private String stringifyToolOutput(ChatToolResponseMessage trm) {
        if (trm.getResult() != null) {
            return JSON.stringify(trm.getResult());
        }
        String c = trm.getContent();
        return c != null ? c : "";
    }

    @Override
    public String getRole(ChatMessage message) {
        return getBaseRole(message);
    }

    // ==================== convertToolDefinitions（工具定义 → Responses 扁平格式） ====================

    /**
     * Responses function tools 使用扁平格式（type/name/description/parameters 直接平铺），
     * 非 Chat Completions 的嵌套 {@code {type, function:{...}}} 结构。
     * 剥离 hosted tools（web_search/file_search/code_interpreter）——上游第三方不支持（design §3）。
     */
    @Override
    public List<Map<String, Object>> convertToolDefinitions(List<ChatToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (ChatToolDefinition tool : tools) {
            String type = tool.getType() != null ? tool.getType() : "function";
            if (!"function".equals(type)) {
                continue;
            }
            Map<String, Object> toolMap = new LinkedHashMap<>();
            toolMap.put("type", "function");
            toolMap.put("name", tool.getName());
            if (tool.getDescription() != null) {
                toolMap.put("description", tool.getDescription());
            }
            if (tool.getParameters() != null) {
                toolMap.put("parameters", tool.getParameters());
            }
            result.add(toolMap);
        }
        return result.isEmpty() ? null : result;
    }

    // ==================== parseResponse（响应方向） ====================

    @Override
    @SuppressWarnings("unchecked")
    public ChatResponse parseResponse(String responseBody, LlmModel config) {
        if (StringHelper.isEmpty(responseBody)) {
            return ChatResponse.error("NULL_RESPONSE", "Empty response body");
        }

        Map<String, Object> responseMap = (Map<String, Object>) JSON.parse(responseBody);
        ChatResponse response = new ChatResponse();

        response.setId(getStringByPath(responseMap, "id"));
        response.setModel(getStringByPath(responseMap, "model"));

        // output[] typed items → messages（按 type 分派，保留 output 顺序）
        List<ChatMessage> messages = new ArrayList<>();
        Object outputObj = responseMap.get("output");
        if (outputObj instanceof List) {
            for (Object item : (List<?>) outputObj) {
                if (!(item instanceof Map)) {
                    continue;
                }
                Map<String, Object> itemMap = (Map<String, Object>) item;
                String itemType = (String) itemMap.get("type");
                if ("message".equals(itemType)) {
                    ChatAssistantMessage am = new ChatAssistantMessage();
                    am.setContent(extractContentText(itemMap, "content"));
                    messages.add(am);
                } else if ("reasoning".equals(itemType)) {
                    ChatReasoningMessage rm = new ChatReasoningMessage();
                    rm.setSummary(extractContentText(itemMap, "summary"));
                    messages.add(rm);
                } else if ("function_call".equals(itemType)) {
                    String callId = (String) itemMap.get("call_id");
                    String name = (String) itemMap.get("name");
                    String argsStr = (String) itemMap.get("arguments");
                    messages.add(new ChatToolCallMessage(callId, name, parseArguments(argsStr)));
                }
            }
        }
        response.setMessages(messages);

        // 顶层 status → normalizeFinishReason（completed→stop, incomplete→length）
        String status = getStringByPath(responseMap, "status");
        response.setFinishReason(normalizeFinishReason(status));

        // usage: input_tokens/output_tokens → ChatUsage
        Object usageObj = responseMap.get("usage");
        if (usageObj instanceof Map) {
            response.setUsage(parseUsageFromResponses((Map<String, Object>) usageObj));
        }

        return response;
    }

    /**
     * 从 content/summary parts 数组中提取文本（拼接所有 part 的 text 字段）。
     */
    @SuppressWarnings("unchecked")
    private String extractContentText(Map<String, Object> itemMap, String fieldName) {
        Object partsObj = itemMap.get(fieldName);
        if (!(partsObj instanceof List)) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Object part : (List<?>) partsObj) {
            if (!(part instanceof Map)) {
                continue;
            }
            Map<String, Object> partMap = (Map<String, Object>) part;
            Object text = partMap.get("text");
            if (text != null) {
                sb.append(text);
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(String argsStr) {
        if (StringHelper.isEmpty(argsStr)) {
            return null;
        }
        try {
            Object parsed = JSON.parse(argsStr);
            if (parsed instanceof Map) {
                return (Map<String, Object>) parsed;
            }
        } catch (Exception ignored) {
            // 容忍模型返回的畸形 arguments JSON：留空由调用方处理
        }
        return null;
    }

    private ChatUsage parseUsageFromResponses(Map<String, Object> usageMap) {
        ChatUsage usage = new ChatUsage();
        usage.setPromptTokens(toInt(usageMap.get("input_tokens")));
        usage.setCompletionTokens(toInt(usageMap.get("output_tokens")));
        Integer total = toInt(usageMap.get("total_tokens"));
        if (total == null && usage.getPromptTokens() != null && usage.getCompletionTokens() != null) {
            total = usage.getPromptTokens() + usage.getCompletionTokens();
        }
        usage.setTotalTokens(total);
        return usage;
    }

    // ==================== parseStreamChunk（流式方向） ====================

    @Override
    @SuppressWarnings("unchecked")
    public ChatStreamChunk parseStreamChunk(String data) {
        if (data == null || data.isEmpty() || "[DONE]".equals(data)) {
            return null;
        }

        Map<String, Object> eventMap = (Map<String, Object>) JSON.parse(data);
        String type = (String) eventMap.get("type");
        if (type == null) {
            return null;
        }

        ChatStreamChunk chunk = new ChatStreamChunk();

        switch (type) {
            case EVENT_RESPONSE_CREATED:
            case EVENT_RESPONSE_IN_PROGRESS: {
                Object respObj = eventMap.get("response");
                if (respObj instanceof Map) {
                    Map<String, Object> respMap = (Map<String, Object>) respObj;
                    chunk.setId((String) respMap.get("id"));
                    chunk.setModel((String) respMap.get("model"));
                }
                return chunk;
            }
            case EVENT_OUTPUT_ITEM_ADDED: {
                Integer outputIndex = toInt(eventMap.get("output_index"));
                Object itemObj = eventMap.get("item");
                if (!(itemObj instanceof Map)) {
                    return null;
                }
                Map<String, Object> itemMap = (Map<String, Object>) itemObj;
                String itemType = (String) itemMap.get("type");
                if ("function_call".equals(itemType)) {
                    chunk.setItemType(StreamItemType.tool_call);
                    chunk.setItemIndex(outputIndex);
                    chunk.setCallId((String) itemMap.get("call_id"));
                    chunk.setPhase(StreamItemPhase.ADDED);
                    chunk.setDelta((String) itemMap.get("name"));
                } else if ("reasoning".equals(itemType)) {
                    chunk.setItemType(StreamItemType.reasoning);
                    chunk.setItemIndex(outputIndex);
                    chunk.setPhase(StreamItemPhase.ADDED);
                } else {
                    // message 或其他 → text
                    chunk.setItemType(StreamItemType.text);
                    chunk.setItemIndex(outputIndex);
                    chunk.setPhase(StreamItemPhase.ADDED);
                }
                return chunk;
            }
            case EVENT_OUTPUT_TEXT_DELTA: {
                chunk.setItemType(StreamItemType.text);
                chunk.setItemIndex(toInt(eventMap.get("output_index")));
                chunk.setPhase(StreamItemPhase.DELTA);
                chunk.setDelta((String) eventMap.get("delta"));
                return chunk;
            }
            case EVENT_REASONING_SUMMARY_DELTA: {
                chunk.setItemType(StreamItemType.reasoning);
                chunk.setItemIndex(toInt(eventMap.get("output_index")));
                chunk.setPhase(StreamItemPhase.DELTA);
                chunk.setDelta((String) eventMap.get("delta"));
                return chunk;
            }
            case EVENT_FUNCTION_CALL_ARGS_DELTA: {
                chunk.setItemType(StreamItemType.tool_call);
                chunk.setItemIndex(toInt(eventMap.get("output_index")));
                chunk.setPhase(StreamItemPhase.DELTA);
                chunk.setDelta((String) eventMap.get("delta"));
                return chunk;
            }
            case EVENT_RESPONSE_COMPLETED:
            case EVENT_RESPONSE_FAILED:
            case EVENT_RESPONSE_INCOMPLETE: {
                Object respObj = eventMap.get("response");
                if (respObj instanceof Map) {
                    Map<String, Object> respMap = (Map<String, Object>) respObj;
                    chunk.setId((String) respMap.get("id"));
                    chunk.setModel((String) respMap.get("model"));
                    chunk.setFinishReason(normalizeFinishReason((String) respMap.get("status")));
                    Object usageObj = respMap.get("usage");
                    if (usageObj instanceof Map) {
                        chunk.setUsage(parseUsageFromResponses((Map<String, Object>) usageObj));
                    }
                }
                chunk.setPhase(StreamItemPhase.DONE);
                return chunk;
            }
            default:
                return null;
        }
    }

    private Integer toInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }
}
