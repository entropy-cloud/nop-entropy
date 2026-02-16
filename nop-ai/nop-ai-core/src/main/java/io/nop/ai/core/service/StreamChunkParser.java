package io.nop.ai.core.service;

import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.core.model.ApiStyle;
import io.nop.ai.core.model.LlmModel;
import io.nop.api.core.json.JSON;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.bean.BeanTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 流式响应块解析器。
 * <p>
 * 负责根据不同 API 提供商的流式响应格式解析数据块。
 * 各提供商的流式响应格式差异很大：
 * <ul>
 *   <li>OpenAI - choices[0].delta.content</li>
 *   <li>Anthropic - delta.text (event-based)</li>
 *   <li>Gemini - candidates[0].content.parts[0].text</li>
 * </ul>
 * <p>
 * 性能优化：
 * <ul>
 *   <li>预定义各提供商的路径，避免重复字符串拼接</li>
 *   <li>延迟创建对象</li>
 * </ul>
 */
public class StreamChunkParser {
    private static final Logger LOG = LoggerFactory.getLogger(StreamChunkParser.class);

    // 预定义各 API 风格的流式响应路径，避免运行时拼接
    private static final StreamPaths OPENAI_PATHS = new StreamPaths(
            "id",
            "choices.0.delta.role",
            "choices.0.delta.content",
            "choices.0.delta.thinking",
            "choices.0.finish_reason"
    );

    private static final StreamPaths ANTHROPIC_PATHS = new StreamPaths(
            null,
            null,
            "delta.text",
            "delta.thinking",
            "stop_reason"
    );

    private static final StreamPaths GEMINI_PATHS = new StreamPaths(
            null,
            null,
            "candidates.0.content.parts.0.text",
            null,
            "candidates.0.finishReason"
    );

    private final LlmModel config;
    private final ApiStyle apiStyle;
    private final StreamPaths paths;

    public StreamChunkParser(LlmModel config) {
        this.config = config;
        this.apiStyle = config.getApiStyle() != null ? config.getApiStyle() : ApiStyle.openai;
        this.paths = resolvePaths(apiStyle);
    }

    /**
     * 解析流式数据块
     *
     * @param data SSE 数据行内容（不含 "data: " 前缀）
     * @return 解析后的 ChatStreamChunk，如果数据无效返回 null
     */
    @SuppressWarnings("unchecked")
    public ChatStreamChunk parse(String data) {
        if (data == null || data.isEmpty() || "[DONE]".equals(data)) {
            return null;
        }

        try {
            Map<String, Object> dataMap = (Map<String, Object>) JSON.parse(data);
            ChatStreamChunk chunk = new ChatStreamChunk();

            // 根据 API 风格解析
            switch (apiStyle) {
                case anthropic:
                    parseAnthropicChunk(dataMap, chunk);
                    break;
                case gemini:
                    parseGeminiChunk(dataMap, chunk);
                    break;
                case ollama:
                    parseOllamaChunk(dataMap, chunk);
                    break;
                case openai:
                default:
                    parseOpenAiChunk(dataMap, chunk);
                    break;
            }

            return chunk;

        } catch (Exception e) {
            LOG.warn("nop.ai.parse-stream-chunk-fail: data={}", data, e);
            return null;
        }
    }

    /**
     * 解析 OpenAI 风格流式块
     */
    private void parseOpenAiChunk(Map<String, Object> dataMap, ChatStreamChunk chunk) {
        chunk.setId(getString(dataMap, paths.idPath));
        chunk.setRole(getString(dataMap, paths.rolePath));
        chunk.setContent(getString(dataMap, paths.contentPath));
        chunk.setThinking(getString(dataMap, paths.thinkingPath));
        chunk.setFinishReason(normalizeFinishReason(getString(dataMap, paths.finishReasonPath)));
    }

    /**
     * 解析 Anthropic 风格流式块
     * <p>
     * Claude 使用事件驱动格式：
     * <pre>
     * {"type": "content_block_delta", "delta": {"type": "text_delta", "text": "..."}}
     * </pre>
     */
    private void parseAnthropicChunk(Map<String, Object> dataMap, ChatStreamChunk chunk) {
        String eventType = (String) dataMap.get("type");

        if ("content_block_delta".equals(eventType)) {
            Object delta = dataMap.get("delta");
            if (delta instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> deltaMap = (Map<String, Object>) delta;
                if ("text_delta".equals(deltaMap.get("type"))) {
                    chunk.setContent((String) deltaMap.get("text"));
                } else if ("thinking_delta".equals(deltaMap.get("type"))) {
                    chunk.setThinking((String) deltaMap.get("thinking"));
                }
            }
        } else if ("message_stop".equals(eventType)) {
            chunk.setFinishReason("stop");
        }
    }

    /**
     * 解析 Gemini 风格流式块
     */
    private void parseGeminiChunk(Map<String, Object> dataMap, ChatStreamChunk chunk) {
        chunk.setContent(getString(dataMap, paths.contentPath));
        chunk.setFinishReason(normalizeFinishReason(getString(dataMap, paths.finishReasonPath)));
    }

    /**
     * 解析 Ollama 风格流式块
     */
    private void parseOllamaChunk(Map<String, Object> dataMap, ChatStreamChunk chunk) {
        chunk.setModel(getString(dataMap, "model"));
        chunk.setContent(getString(dataMap, "message.content"));
        chunk.setFinishReason(normalizeFinishReason(getString(dataMap, "done_reason")));
    }

    /**
     * 根据 API 风格解析路径配置
     */
    private StreamPaths resolvePaths(ApiStyle apiStyle) {
        switch (apiStyle) {
            case anthropic:
                return ANTHROPIC_PATHS;
            case gemini:
                return GEMINI_PATHS;
            case ollama:
                // Ollama 使用硬编码路径
                return new StreamPaths(null, null, null, null, null);
            case openai:
            default:
                return OPENAI_PATHS;
        }
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
    private String getString(Map<String, Object> map, String path) {
        if (path == null || map == null) {
            return null;
        }

        try {
            Object value;
            if (path.contains(".")) {
                value = BeanTool.getComplexProperty(map, path);
            } else {
                value = map.get(path);
            }
            return StringHelper.toString(value, null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 流式响应路径配置（不可变）
     */
    private static class StreamPaths {
        final String idPath;
        final String rolePath;
        final String contentPath;
        final String thinkingPath;
        final String finishReasonPath;

        StreamPaths(String idPath, String rolePath, String contentPath,
                    String thinkingPath, String finishReasonPath) {
            this.idPath = idPath;
            this.rolePath = rolePath;
            this.contentPath = contentPath;
            this.thinkingPath = thinkingPath;
            this.finishReasonPath = finishReasonPath;
        }
    }
}
