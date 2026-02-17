package io.nop.ai.core.dialect;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.messages.ChatUsage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.core.model.LlmModelModel;
import io.nop.ai.core.model.LlmResponseModel;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.bean.BeanTool;

import java.util.Map;

/**
 * LLM 方言抽象基类。
 * <p>
 * 提供通用的辅助方法，具体方言实现可以继承此类。
 *
 * @author canonical_entropy@163.com
 */
public abstract class AbstractLlmDialect {

    /**
     * 应用思考模式提示词
     */
    protected String applyThinkingPrompt(String content, LlmModelModel modelConfig, ChatOptions options) {
        if (content == null) {
            return null;
        }

        boolean enableThinking = options != null && Boolean.TRUE.equals(options.getEnableThinking());

        if (enableThinking && modelConfig != null && modelConfig.getEnableThinkingPrompt() != null) {
            return content + "\n" + modelConfig.getEnableThinkingPrompt();
        } else if (!enableThinking && modelConfig != null && modelConfig.getDisableThinkingPrompt() != null) {
            return content + "\n" + modelConfig.getDisableThinkingPrompt();
        }

        return content;
    }

    /**
     * 获取基础角色（OpenAI 风格）
     */
    protected String getBaseRole(ChatMessage message) {
        if (message instanceof ChatUserMessage) {
            return "user";
        } else if (message instanceof ChatAssistantMessage) {
            return "assistant";
        } else if (message instanceof ChatSystemMessage) {
            return "system";
        } else if (message instanceof ChatToolResponseMessage) {
            return "tool";
        }
        return "user";
    }

    /**
     * 标准化结束原因
     */
    protected String normalizeFinishReason(String reason) {
        if (StringHelper.isEmpty(reason)) {
            return null;
        }

        String lower = reason.toLowerCase();
        switch (lower) {
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
     * 解析最大 token 数
     */
    protected Integer resolveMaxTokens(ChatOptions options, LlmModelModel modelConfig) {
        if (options == null) {
            return null;
        }

        Integer maxTokens = options.getMaxTokens();

        if (maxTokens == null && modelConfig != null) {
            maxTokens = modelConfig.getDefaultMaxTokens();
        }

        if (maxTokens != null && modelConfig != null && modelConfig.getMaxTokensLimit() != null) {
            if (modelConfig.getMaxTokensLimit() < maxTokens) {
                maxTokens = modelConfig.getMaxTokensLimit();
            }
        }

        return maxTokens;
    }

    /**
     * 添加非空选项到 body
     */
    protected void addOptionIfNotNull(Map<String, Object> body, String key, Object value) {
        if (value != null) {
            body.put(key, value);
        }
    }

    /**
     * 从 Map 中按路径获取字符串值
     */
    protected String getStringByPath(Map<String, Object> map, String path) {
        if (path == null || map == null) {
            return null;
        }
        Object value = BeanTool.getComplexProperty(map, path);
        return value != null ? value.toString() : null;
    }

    /**
     * 从 Map 中按路径获取整数值
     */
    protected Integer getIntByPath(Map<String, Object> map, String path) {
        if(path == null)
            return null;
        Object value = BeanTool.getComplexProperty(map, path);
        return ConvertHelper.toInteger(value, NopException::new);
    }

    /**
     * 从 Map 中按路径获取长整数值
     */
    protected Long getLongByPath(Map<String, Object> map, String path) {
        if(path == null)
            return null;
        Object value = BeanTool.getComplexProperty(map, path);
        return ConvertHelper.toLong(value, NopException::new);
    }

    /**
     * 解析 Usage 信息
     * <p>
     * 使用 responseConfig 中的路径配置解析 token 使用信息，
     * 包括缓存相关的 token 统计。
     *
     * @param responseMap    响应 Map
     * @param responseConfig 响应配置，包含各种路径
     * @param defaultPromptPath 默认的 prompt tokens 路径
     * @param defaultCompletionPath 默认的 completion tokens 路径
     * @param defaultTotalPath 默认的 total tokens 路径
     * @return ChatUsage 对象
     */
    protected ChatUsage parseUsage(Map<String, Object> responseMap, LlmResponseModel responseConfig,
                                   String defaultPromptPath, String defaultCompletionPath, String defaultTotalPath) {
        ChatUsage usage = new ChatUsage();

        // 基础 token 统计
        String promptPath = responseConfig != null && responseConfig.getPromptTokensPath() != null
                ? responseConfig.getPromptTokensPath() : defaultPromptPath;
        String completionPath = responseConfig != null && responseConfig.getCompletionTokensPath() != null
                ? responseConfig.getCompletionTokensPath() : defaultCompletionPath;
        String totalPath = responseConfig != null && responseConfig.getTotalTokensPath() != null
                ? responseConfig.getTotalTokensPath() : defaultTotalPath;

        usage.setPromptTokens(getIntByPath(responseMap, promptPath));
        usage.setCompletionTokens(getIntByPath(responseMap, completionPath));
        usage.setTotalTokens(getIntByPath(responseMap, totalPath));

        // 缓存 token 统计（用于 Prompt Caching）
        if (responseConfig != null) {
            String cacheHitPath = responseConfig.getPromptCacheHitTokensPath();
            String cacheCreationPath = responseConfig.getPromptCacheCreationTokensPath();

            if (cacheHitPath != null) {
                usage.setCacheHitTokens(getIntByPath(responseMap, cacheHitPath));
            }
            if (cacheCreationPath != null) {
                usage.setCacheCreationTokens(getIntByPath(responseMap, cacheCreationPath));
            }
        }

        return usage;
    }

    /**
     * 解析推理/思考内容
     * <p>
     * 使用 responseConfig 中的 reasoningContentPath 解析推理内容，
     * 并设置到 ChatAssistantMessage 的 think 字段。
     *
     * @param responseMap    响应 Map
     * @param responseConfig 响应配置
     * @param defaultPath    默认的推理内容路径
     * @return 推理内容，如果没有则返回 null
     */
    protected String parseReasoningContent(Map<String, Object> responseMap, LlmResponseModel responseConfig,
                                           String defaultPath) {
        String reasoningPath = responseConfig != null && responseConfig.getReasoningContentPath() != null
                ? responseConfig.getReasoningContentPath() : defaultPath;

        if (reasoningPath == null) {
            return null;
        }

        return getStringByPath(responseMap, reasoningPath);
    }

    /**
     * 构建带有思考内容的完整消息
     * <p>
     * 使用 modelConfig 中的 thinkStartMarker 和 thinkEndMarker 来格式化思考内容。
     *
     * @param content    主要内容
     * @param thinking   思考内容
     * @param modelConfig 模型配置
     * @return 格式化后的完整内容
     */
    protected String buildFullContentWithThinking(String content, String thinking, LlmModelModel modelConfig) {
        if (StringHelper.isEmpty(thinking)) {
            return content;
        }

        String startMarker = modelConfig != null && modelConfig.getThinkStartMarker() != null
                ? modelConfig.getThinkStartMarker() : "ery\n";
        String endMarker = modelConfig != null && modelConfig.getThinkEndMarker() != null
                ? modelConfig.getThinkEndMarker() : "module-info>\n";

        StringBuilder sb = new StringBuilder();
        sb.append(startMarker);
        sb.append(thinking);
        sb.append(endMarker);

        if (!StringHelper.isEmpty(content)) {
            sb.append(content);
        }

        return sb.toString();
    }
}
