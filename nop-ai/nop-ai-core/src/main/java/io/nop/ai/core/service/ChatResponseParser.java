package io.nop.ai.core.service;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatUsage;
import io.nop.ai.core.model.ApiStyle;
import io.nop.ai.core.model.LlmModel;
import io.nop.ai.core.model.LlmModelModel;
import io.nop.ai.core.model.LlmResponseModel;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.JSON;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.bean.BeanTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 聊天响应解析器。
 * <p>
 * 负责根据不同的 API 风格解析 HTTP 响应。
 * 参考 solon-ai 的 dialect 模式，支持多种 AI 提供商的特定格式：
 * <ul>
 *   <li>OpenAI - choices[0].message.content</li>
 *   <li>Anthropic (Claude) - content[0].text</li>
 *   <li>Google (Gemini) - candidates[0].content.parts[0].text</li>
 *   <li>Ollama - message.content</li>
 * </ul>
 * <p>
 * 性能优化：
 * <ul>
 *   <li>复用 BeanTool 解析路径</li>
 *   <li>延迟创建对象</li>
 *   <li>避免重复字符串操作</li>
 * </ul>
 */
public class ChatResponseParser {
    private static final Logger LOG = LoggerFactory.getLogger(ChatResponseParser.class);

    private final LlmModel config;
    private final ChatRequest request;
    // 缓存 modelConfig 避免重复查找
    private final LlmModelModel modelConfig;

    public ChatResponseParser(LlmModel config, ChatRequest request) {
        this.config = config;
        this.request = request;
        this.modelConfig = request.getOptions() != null
                ? LlmConfigHelper.getModelConfig(config, request.getOptions().getModel())
                : null;
    }

    /**
     * 解析 HTTP 响应
     */
    public ChatResponse parse(String responseBody) {
        if (StringHelper.isEmpty(responseBody)) {
            return ChatResponse.error("NULL_RESPONSE", "Empty response body");
        }


        @SuppressWarnings("unchecked")
        Map<String, Object> responseMap = (Map<String, Object>) JSON.parse(responseBody);
        ApiStyle apiStyle = config.getApiStyle() != null ? config.getApiStyle() : ApiStyle.openai;

        ChatResponse response;
        switch (apiStyle) {
            case anthropic:
                response = parseAnthropicResponse(responseMap);
                break;
            case gemini:
                response = parseGeminiResponse(responseMap);
                break;
            case ollama:
                response = parseOllamaResponse(responseMap);
                break;
            case openai:
            default:
                response = parseOpenAiResponse(responseMap);
                break;
        }

        return response;
    }

    /**
     * 解析 OpenAI 风格响应
     * <pre>
     * {
     *   "id": "...",
     *   "choices": [{"message": {"content": "..."}, "finish_reason": "stop"}],
     *   "usage": {"prompt_tokens": 10, "completion_tokens": 20}
     * }
     * </pre>
     */
    private ChatResponse parseOpenAiResponse(Map<String, Object> responseMap) {
        ChatResponse response = new ChatResponse();
        LlmResponseModel responseConfig = config.getResponse();

        // 解析内容
        String contentPath = responseConfig != null && responseConfig.getContentPath() != null
                ? responseConfig.getContentPath()
                : "choices.0.message.content";
        String content = getStringByPath(responseMap, contentPath);

        // 处理思考内容
        String thinkContent = extractThinkContent(responseMap, content);
        if (thinkContent != null && content != null) {
            content = removeThinkMarkers(content);
        }

        ChatAssistantMessage message = new ChatAssistantMessage();
        message.setContent(content);
        message.setThink(thinkContent);
        response.setMessage(message);

        // 解析元数据
        response.setId(getStringByPath(responseMap, "id"));
        response.setModel(getStringByPath(responseMap, "model"));

        // 解析结束原因
        String statusPath = responseConfig != null && responseConfig.getStatusPath() != null
                ? responseConfig.getStatusPath()
                : "choices.0.finish_reason";
        response.setFinishReason(normalizeFinishReason(getStringByPath(responseMap, statusPath)));

        // 解析 Usage
        response.setUsage(parseUsage(responseMap, responseConfig));

        return response;
    }

    /**
     * 解析 Anthropic (Claude) 风格响应
     * <pre>
     * {
     *   "id": "msg_...",
     *   "content": [{"type": "text", "text": "..."}],
     *   "role": "assistant",
     *   "stop_reason": "end_turn",
     *   "usage": {"input_tokens": 10, "output_tokens": 20}
     * }
     * </pre>
     */
    private ChatResponse parseAnthropicResponse(Map<String, Object> responseMap) {
        ChatResponse response = new ChatResponse();
        LlmResponseModel responseConfig = config.getResponse();

        // 解析内容 - Claude 使用 content[0].text
        String contentPath = responseConfig != null && responseConfig.getContentPath() != null
                ? responseConfig.getContentPath()
                : "content.0.text";
        String content = getStringByPath(responseMap, contentPath);

        // 解析工具调用
        List<ChatToolCall> toolCalls = parseAnthropicToolCalls(responseMap);

        ChatAssistantMessage message = new ChatAssistantMessage();
        message.setContent(content);
        if (!toolCalls.isEmpty()) {
            message.setToolCalls(toolCalls);
        }
        response.setMessage(message);

        // 解析元数据
        response.setId(getStringByPath(responseMap, "id"));
        response.setModel(getStringByPath(responseMap, "model"));

        // 解析结束原因
        String statusPath = responseConfig != null && responseConfig.getStatusPath() != null
                ? responseConfig.getStatusPath()
                : "stop_reason";
        response.setFinishReason(normalizeFinishReason(getStringByPath(responseMap, statusPath)));

        // 解析 Usage - Claude 使用 input_tokens/output_tokens
        ChatUsage usage = new ChatUsage();
        String promptTokensPath = responseConfig != null && responseConfig.getPromptTokensPath() != null
                ? responseConfig.getPromptTokensPath()
                : "usage.input_tokens";
        String completionTokensPath = responseConfig != null && responseConfig.getCompletionTokensPath() != null
                ? responseConfig.getCompletionTokensPath()
                : "usage.output_tokens";

        usage.setPromptTokens(getIntByPath(responseMap, promptTokensPath));
        usage.setCompletionTokens(getIntByPath(responseMap, completionTokensPath));
        if (usage.getPromptTokens() != null && usage.getCompletionTokens() != null) {
            usage.setTotalTokens(usage.getPromptTokens() + usage.getCompletionTokens());
        }
        response.setUsage(usage);

        return response;
    }

    /**
     * 解析 Anthropic 工具调用
     * <pre>
     * "content": [
     *   {"type": "text", "text": "..."},
     *   {"type": "tool_use", "id": "...", "name": "...", "input": {...}}
     * ]
     * </pre>
     */
    @SuppressWarnings("unchecked")
    private List<ChatToolCall> parseAnthropicToolCalls(Map<String, Object> responseMap) {
        List<ChatToolCall> toolCalls = new ArrayList<>();

        Object contentObj = responseMap.get("content");
        if (!(contentObj instanceof List)) {
            return toolCalls;
        }

        List<Map<String, Object>> contentList = (List<Map<String, Object>>) contentObj;
        for (Map<String, Object> block : contentList) {
            if ("tool_use".equals(block.get("type"))) {
                ChatToolCall toolCall = new ChatToolCall();
                toolCall.setId((String) block.get("id"));
                toolCall.setName((String) block.get("name"));
                toolCall.setArguments((Map<String, Object>) block.get("input"));
                toolCalls.add(toolCall);
            }
        }

        return toolCalls;
    }

    /**
     * 解析 Google Gemini 风格响应
     * <pre>
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
    private ChatResponse parseGeminiResponse(Map<String, Object> responseMap) {
        ChatResponse response = new ChatResponse();
        LlmResponseModel responseConfig = config.getResponse();

        // 解析内容 - Gemini 使用 candidates[0].content.parts[0].text
        String contentPath = responseConfig != null && responseConfig.getContentPath() != null
                ? responseConfig.getContentPath()
                : "candidates.0.content.parts.0.text";
        String content = getStringByPath(responseMap, contentPath);

        ChatAssistantMessage message = new ChatAssistantMessage();
        message.setContent(content);
        response.setMessage(message);

        // 解析元数据
        response.setModel(getStringByPath(responseMap, "model"));

        // 解析结束原因
        String statusPath = responseConfig != null && responseConfig.getStatusPath() != null
                ? responseConfig.getStatusPath()
                : "candidates.0.finishReason";
        response.setFinishReason(normalizeFinishReason(getStringByPath(responseMap, statusPath)));

        // 解析 Usage - Gemini 使用 usageMetadata
        ChatUsage usage = new ChatUsage();
        String promptTokensPath = responseConfig != null && responseConfig.getPromptTokensPath() != null
                ? responseConfig.getPromptTokensPath()
                : "usageMetadata.promptTokenCount";
        String completionTokensPath = responseConfig != null && responseConfig.getCompletionTokensPath() != null
                ? responseConfig.getCompletionTokensPath()
                : "usageMetadata.candidatesTokenCount";
        String totalTokensPath = responseConfig != null && responseConfig.getTotalTokensPath() != null
                ? responseConfig.getTotalTokensPath()
                : "usageMetadata.totalTokenCount";

        usage.setPromptTokens(getIntByPath(responseMap, promptTokensPath));
        usage.setCompletionTokens(getIntByPath(responseMap, completionTokensPath));
        usage.setTotalTokens(getIntByPath(responseMap, totalTokensPath));
        response.setUsage(usage);

        return response;
    }

    /**
     * 解析 Ollama 风格响应
     * <pre>
     * {
     *   "model": "llama2",
     *   "message": {"role": "assistant", "content": "..."},
     *   "done_reason": "stop",
     *   "prompt_eval_count": 10,
     *   "eval_count": 20
     * }
     * </pre>
     */
    private ChatResponse parseOllamaResponse(Map<String, Object> responseMap) {
        ChatResponse response = new ChatResponse();
        LlmResponseModel responseConfig = config.getResponse();

        // Ollama 使用 message.content 路径
        String contentPath = responseConfig != null && responseConfig.getContentPath() != null
                ? responseConfig.getContentPath()
                : "message.content";
        String content = getStringByPath(responseMap, contentPath);

        ChatAssistantMessage message = new ChatAssistantMessage();
        message.setContent(content);
        response.setMessage(message);

        // 模型名称
        response.setModel(getStringByPath(responseMap, "model"));

        // 结束原因
        String statusPath = responseConfig != null && responseConfig.getStatusPath() != null
                ? responseConfig.getStatusPath()
                : "done_reason";
        response.setFinishReason(normalizeFinishReason(getStringByPath(responseMap, statusPath)));

        // Usage - Ollama 使用 prompt_eval_count 和 eval_count
        ChatUsage usage = new ChatUsage();
        String promptTokensPath = responseConfig != null && responseConfig.getPromptTokensPath() != null
                ? responseConfig.getPromptTokensPath()
                : "prompt_eval_count";
        String completionTokensPath = responseConfig != null && responseConfig.getCompletionTokensPath() != null
                ? responseConfig.getCompletionTokensPath()
                : "eval_count";

        usage.setPromptTokens(getIntByPath(responseMap, promptTokensPath));
        usage.setCompletionTokens(getIntByPath(responseMap, completionTokensPath));
        if (usage.getPromptTokens() != null && usage.getCompletionTokens() != null) {
            usage.setTotalTokens(usage.getPromptTokens() + usage.getCompletionTokens());
        }
        response.setUsage(usage);

        return response;
    }

    /**
     * 解析 Usage 信息
     */
    private ChatUsage parseUsage(Map<String, Object> responseMap, LlmResponseModel responseConfig) {
        ChatUsage usage = new ChatUsage();

        String promptTokensPath = responseConfig != null && responseConfig.getPromptTokensPath() != null
                ? responseConfig.getPromptTokensPath()
                : "usage.prompt_tokens";
        String completionTokensPath = responseConfig != null && responseConfig.getCompletionTokensPath() != null
                ? responseConfig.getCompletionTokensPath()
                : "usage.completion_tokens";
        String totalTokensPath = responseConfig != null && responseConfig.getTotalTokensPath() != null
                ? responseConfig.getTotalTokensPath()
                : "usage.total_tokens";

        usage.setPromptTokens(getIntByPath(responseMap, promptTokensPath));
        usage.setCompletionTokens(getIntByPath(responseMap, completionTokensPath));
        usage.setTotalTokens(getIntByPath(responseMap, totalTokensPath));

        return usage;
    }

    /**
     * 提取思考内容
     */
    private String extractThinkContent(Map<String, Object> responseMap, String content) {
        LlmResponseModel responseConfig = config.getResponse();

        // 首先尝试从独立字段提取
        if (responseConfig != null && responseConfig.getReasoningContentPath() != null) {
            Object value = BeanTool.getComplexProperty(responseMap, responseConfig.getReasoningContentPath());
            if (value != null) {
                return value.toString();
            }
        }

        // 从内容中提取思考标记
        if (content != null) {
            return extractThinkMarkers(content);
        }

        return null;
    }

    /**
     * 从文本中提取思考标记内容
     */
    private String extractThinkMarkers(String content) {
        ChatOptions options = request.getOptions();
        String modelName = options != null ? options.getModel() : null;
        LlmModelModel modelConfig = LlmConfigHelper.getModelConfig(config, modelName);


        String startMarker = modelConfig != null ? modelConfig.getThinkStartMarker() : null;
        String endMarker = modelConfig != null ? modelConfig.getThinkEndMarker() : null;

        if (startMarker == null || endMarker == null)
            return null;

        if (!content.contains(startMarker)) {
            return null;
        }

        int start = content.indexOf(startMarker);
        int end = content.indexOf(endMarker, start + startMarker.length());

        if (end > start) {
            return content.substring(start + startMarker.length(), end).trim();
        }

        return null;
    }

    /**
     * 移除思考标记
     */
    private String removeThinkMarkers(String content) {
        ChatOptions options = request.getOptions();
        String modelName = options != null ? options.getModel() : null;
        LlmModelModel modelConfig = LlmConfigHelper.getModelConfig(config, modelName);

        String startMarker = modelConfig != null ? modelConfig.getThinkStartMarker() : null;
        String endMarker = modelConfig != null ? modelConfig.getThinkEndMarker() : null;

        if (startMarker == null || endMarker == null)
            return content;

        if (!content.contains(startMarker)) {
            return content;
        }

        int start = content.indexOf(startMarker);
        int end = content.indexOf(endMarker, start + startMarker.length());

        if (end > start) {
            end += endMarker.length();
            if (end < content.length() && content.charAt(end) == '\n') {
                end++;
            }
            return content.substring(0, start).trim() + " " + content.substring(end).trim();
        }

        return content;
    }

    /**
     * 标准化结束原因
     */
    private String normalizeFinishReason(String reason) {
        if (StringHelper.isEmpty(reason)) {
            return null;
        }

        switch (reason.toLowerCase()) {
            case "stop":
            case "end_turn":
            case "stop_sequence":
                return "stop";
            case "length":
            case "max_tokens":
                return "length";
            case "content_filter":
            case "safety":
            case "recitation":
                return "content_filter";
            case "tool_calls":
            case "function_call":
                return "tool_calls";
            default:
                return reason;
        }
    }

    /**
     * 通过路径获取字符串值
     */
    private String getStringByPath(Map<String, Object> map, String path) {
        if (path == null || map == null) {
            return null;
        }

        Object value;
        if (path.contains(".")) {
            value = BeanTool.getComplexProperty(map, path);
        } else {
            value = map.get(path);
        }
        return StringHelper.toString(value, null);
    }

    /**
     * 通过路径获取整数值
     */
    private Integer getIntByPath(Map<String, Object> map, String path) {
        if (path == null || map == null) {
            return null;
        }

        Object value;
        if (path.contains(".")) {
            value = BeanTool.getComplexProperty(map, path);
        } else {
            value = map.get(path);
        }
        return ConvertHelper.toInteger(value, NopException::new);
    }
}
