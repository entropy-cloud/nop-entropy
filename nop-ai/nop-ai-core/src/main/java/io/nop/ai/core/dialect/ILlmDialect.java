package io.nop.ai.core.dialect;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatToolDefinition;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.core.model.LlmModel;
import io.nop.ai.core.model.LlmModelModel;
import io.nop.http.api.client.HttpRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 方言接口。
 * <p>
 * 定义特定 API 风格需要实现的所有功能，包括：
 * <ul>
 *   <li>请求构建 - 构建 HTTP 请求</li>
 *   <li>响应解析 - 解析 HTTP 响应</li>
 *   <li>消息转换 - 转换消息格式</li>
 *   <li>工具定义转换 - 转换工具定义格式</li>
 *   <li>流式解析 - 解析流式响应块</li>
 * </ul>
 * <p>
 * 每种 API 风格（OpenAI、Anthropic、Gemini、Ollama）都有对应的实现类，
 * 将该风格的所有特定逻辑集中在一个类中。
 *
 * @author canonical_entropy@163.com
 */
public interface ILlmDialect {

    /**
     * 获取方言名称
     */
    String getName();

    /**
     * 构建 HTTP 请求 URL
     *
     * @param baseUrl 基础 URL
     * @param chatUrl 聊天 API 路径
     * @param apiKey API 密钥（某些方言如 Gemini 需要 URL 传参）
     * @return 完整的请求 URL
     */
    String buildUrl(String baseUrl, String chatUrl, String apiKey);

    /**
     * 设置 HTTP 请求头
     *
     * @param httpRequest HTTP 请求对象
     * @param apiKey API 密钥
     * @param apiKeyHeader 自定义 API Key Header（可选）
     */
    void setHeaders(HttpRequest httpRequest, String apiKey, String apiKeyHeader);

    /**
     * 构建请求体
     *
     * @param request 聊天请求
     * @param config LLM 配置
     * @param modelConfig 模型配置
     * @param model 模型名称
     * @param stream 是否流式
     * @return 请求体 Map
     */
    Map<String, Object> buildBody(ChatRequest request, LlmModel config,
                                   LlmModelModel modelConfig, String model, boolean stream);

    /**
     * 解析 HTTP 响应
     *
     * @param responseBody 响应体字符串
     * @param config LLM 配置
     * @return 解析后的 ChatResponse
     */
    ChatResponse parseResponse(String responseBody, LlmModel config);

    /**
     * 解析流式响应块
     *
     * @param data SSE 数据行内容
     * @return 解析后的 ChatStreamChunk，如果数据无效返回 null
     */
    ChatStreamChunk parseStreamChunk(String data);

    /**
     * 转换消息为方言特定格式（纯函数：相同输入永远产生相同输出）
     *
     * @param message 消息对象
     * @param modelConfig 模型配置
     * @param options 聊天选项
     * @return 转换后的 Map
     */
    Map<String, Object> convertMessage(ChatMessage message, LlmModelModel modelConfig,
                                        ChatOptions options);

    /**
     * 获取消息角色
     *
     * @param message 消息对象
     * @return 角色字符串
     */
    String getRole(ChatMessage message);

    /**
     * 转换工具定义列表为方言特定格式
     * <p>
     * 默认实现将 ChatToolDefinition 转换为 OpenAI 风格的 Map 格式：
     * <pre>
     * {
     *   "type": "function",
     *   "function": {
     *     "name": "...",
     *     "description": "...",
     *     "parameters": {...}
     *   }
     * }
     * </pre>
     *
     * @param tools 工具定义列表（ChatToolDefinition）
     * @return 转换后的 Map 列表，供各 API 使用
     */
    default List<Map<String, Object>> convertToolDefinitions(List<ChatToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (ChatToolDefinition tool : tools) {
            Map<String, Object> toolMap = new java.util.LinkedHashMap<>();
            toolMap.put("type", tool.getType() != null ? tool.getType() : "function");
            
            Map<String, Object> funcMap = new java.util.LinkedHashMap<>();
            funcMap.put("name", tool.getName());
            funcMap.put("description", tool.getDescription());
            if (tool.getParameters() != null) {
                funcMap.put("parameters", tool.getParameters());
            }
            
            toolMap.put("function", funcMap);
            result.add(toolMap);
        }
        return result;
    }

    /**
     * Estimate the token count for a list of chat messages (pre-call approximation).
     * <p>
     * The default implementation delegates to {@link AbstractLlmDialect#estimateTokensBaseline(List)},
     * which sums content length / 4 plus a small per-message overhead. Concrete dialects
     * may override for Provider-specific accuracy. The result is intended as a rough
     * estimate for compaction accounting; runtime calibration refines it further.
     *
     * @param messages the messages to estimate, may be null or empty
     * @return estimated token count (always {@code >= 0})
     */
    default long estimateTokens(List<ChatMessage> messages) {
        return AbstractLlmDialect.estimateTokensBaseline(messages);
    }

    // ==================== 反向转换：Map ↔ 标准模型 ====================

    /**
     * Parse provider-specific request body into standard ChatRequest (reverse of buildBody).
     */
    default ChatRequest parseRequestBody(Map<String, Object> body) {
        throw new UnsupportedOperationException("parseRequestBody");
    }

    /**
     * Build provider-specific response Map from standard ChatResponse (reverse of parseResponse).
     * Default implementation produces OpenAI format (the universal gateway format).
     */
    default Map<String, Object> buildResponse(ChatResponse response) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", response.getRequestId());
        result.put("object", "chat.completion");
        // choices[0].message.content
        List<Map<String, Object>> choices = new ArrayList<>();
        Map<String, Object> message = new HashMap<>();
        message.put("role", "assistant");
        message.put("content", response.getMessage() != null ? response.getMessage().getContent() : "");
        Map<String, Object> choice = new HashMap<>();
        choice.put("index", 0);
        choice.put("message", message);
        choice.put("finish_reason", response.getFinishReason());
        choices.add(choice);
        result.put("choices", choices);
        if (response.getUsage() != null) {
            Map<String, Object> usage = new HashMap<>();
            usage.put("prompt_tokens", response.getUsage().getPromptTokens());
            usage.put("completion_tokens", response.getUsage().getCompletionTokens());
            usage.put("total_tokens", response.getUsage().getTotalTokens());
            result.put("usage", usage);
        }
        return result;
    }

    /**
     * Build provider-specific stream chunk Map from standard ChatStreamChunk (reverse of parseStreamChunk).
     * Default implementation produces OpenAI delta format.
     */
    default Map<String, Object> buildStreamChunk(ChatStreamChunk chunk) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", chunk.getId() != null ? chunk.getId() : "");
        result.put("object", "chat.completion.chunk");
        Map<String, Object> delta = new HashMap<>();
        delta.put("role", "assistant");
        if (chunk.getContent() != null) delta.put("content", chunk.getContent());
        if (chunk.getToolCall() != null) {
            Map<String, Object> tc = new HashMap<>();
            if (chunk.getToolCall().getId() != null) tc.put("id", chunk.getToolCall().getId());
            Map<String, Object> func = new HashMap<>();
            if (chunk.getToolCall().getName() != null) func.put("name", chunk.getToolCall().getName());
            if (chunk.getToolCall().getArguments() != null) func.put("arguments", chunk.getToolCall().getArguments());
            tc.put("function", func);
            delta.put("tool_calls", List.of(tc));
        }
        Map<String, Object> choice = new HashMap<>();
        choice.put("index", chunk.getIndex() != null ? chunk.getIndex() : 0);
        choice.put("delta", delta);
        choice.put("finish_reason", chunk.getFinishReason());
        result.put("choices", List.of(choice));
        return result;
    }
}
