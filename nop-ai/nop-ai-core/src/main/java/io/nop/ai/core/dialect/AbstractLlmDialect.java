package io.nop.ai.core.dialect;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.ErrorClassification;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.messages.ChatUsage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.core.model.LlmErrorMappingModel;
import io.nop.ai.core.model.LlmErrorResponseModel;
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

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * LLM 方言抽象基类。
 * <p>
 * 提供通用的辅助方法，具体方言实现可以继承此类。
 *
 * @author canonical_entropy@163.com
 */
public abstract class AbstractLlmDialect {

    public static final int PER_MESSAGE_TOKEN_OVERHEAD = 4;

    public static final int CHARS_PER_TOKEN = 4;

    /**
     * Baseline token estimation for a list of chat messages.
     * <p>
     * Delegates to {@link ILlmDialect#estimateTokensDefault(List)}.
     *
     * @param messages the messages to estimate, may be null or empty
     * @return estimated token count (always {@code >= 0})
     */
    public static long estimateTokensBaseline(List<ChatMessage> messages) {
        return ILlmDialect.estimateTokensDefault(messages);
    }

    private static final Logger LOG = LoggerFactory.getLogger(AbstractLlmDialect.class);

    /**
     * 配置驱动的错误响应规范化（设计 §3.3 / §3.4 / §3.7）。所有 dialect 共享同一实现——
     * 错误规范化是数据驱动（{@code <errorMappings>}）而非 per-provider Java，与成功响应解析
     * 同构。返回携带 {@code errorClassification} 的错误 {@link ChatResponse}，不抛异常。
     *
     * <p>解析顺序（首条命中胜出）：</p>
     * <ol>
     *   <li>对 {@code config.getErrorMappings()} 按声明顺序逐条匹配（多条件合取）。</li>
     *   <li>未命中 → 默认启发式（按 HTTP 状态码，等价今日行为，零回归红线）。</li>
     * </ol>
     */
    public ChatResponse parseErrorResponse(String responseBody, int httpStatus,
                                           Map<String, String> headers, LlmModel config) {
        Map<String, Object> bodyMap = parseBodyMap(responseBody);
        LlmErrorResponseModel errorResponse = config != null ? config.getErrorResponse() : null;

        String errorType = errorResponse != null ? getStringByPath(bodyMap, errorResponse.getErrorTypePath()) : null;
        String errorCode = errorResponse != null ? getStringByPath(bodyMap, errorResponse.getErrorCodePath()) : null;
        String errorMessage = errorResponse != null ? getStringByPath(bodyMap, errorResponse.getErrorMessagePath()) : null;

        ErrorClassification classification = null;
        LlmErrorMappingModel matched = null;
        if (config != null && config.hasErrorMappings()) {
            for (LlmErrorMappingModel m : config.getErrorMappings()) {
                if (matchesMapping(m, httpStatus, errorType, errorCode, errorMessage)) {
                    classification = m.getClassification();
                    matched = m;
                    break;
                }
            }
        }
        if (classification == null) {
            classification = defaultHeuristic(httpStatus);
        }

        Long retryAfterMs = resolveRetryAfterMs(headers, bodyMap, errorResponse, matched);

        String code = errorCode != null ? errorCode : errorType;
        // 永远标记为错误响应（isSuccess()=error==null）。未抽到 message 时用 fallback 文案，
        // 保证非 2xx 一定 isSuccess()==false。
        String displayError = errorMessage != null ? errorMessage
                : (code != null ? code : "HTTP " + httpStatus + " error response");
        return ChatResponse.error(classification, httpStatus, code, displayError, retryAfterMs);
    }

    /**
     * 默认启发式（零回归红线）：与 {@code LlmErrorClassifier} 今日的 HTTP 状态码映射一致。
     * 注意 401/403 → {@link ErrorClassification#NON_TRANSIENT}（不是 AUTH_INVALID），
     * 故 AUTH_INVALID 只能经配置后的 {@code <errorMappings>} 到达。
     */
    private ErrorClassification defaultHeuristic(int httpStatus) {
        if (httpStatus == 429) {
            return ErrorClassification.RATE_LIMITED;
        }
        if (httpStatus >= 500 && httpStatus < 600) {
            return ErrorClassification.TRANSIENT;
        }
        if (httpStatus >= 400 && httpStatus < 500) {
            return ErrorClassification.NON_TRANSIENT;
        }
        return ErrorClassification.NON_TRANSIENT;
    }

    /**
     * 多条件合取匹配（设计 §3.3）：httpStatus / errorTypes / errorCodes / messagePattern
     * 全部命中（未设的维度视为通配）才算命中。
     */
    private boolean matchesMapping(LlmErrorMappingModel m, int httpStatus,
                                   String errorType, String errorCode, String errorMessage) {
        if (m.getHttpStatus() != null && !m.getHttpStatus().isEmpty()
                && !m.getHttpStatus().contains(String.valueOf(httpStatus))) {
            return false;
        }
        if (m.getErrorTypes() != null && !m.getErrorTypes().isEmpty()
                && (errorType == null || !m.getErrorTypes().contains(errorType))) {
            return false;
        }
        if (m.getErrorCodes() != null && !m.getErrorCodes().isEmpty()
                && (errorCode == null || !m.getErrorCodes().contains(errorCode))) {
            return false;
        }
        if (m.getMessagePattern() != null && !m.getMessagePattern().isEmpty()) {
            String msg = errorMessage != null ? errorMessage : "";
            // 复刻 dialect.xdef 消息正则规则：下划线替空白、. 跨行、大小写无关、全匹配。
            Pattern regex = Pattern.compile(m.getMessagePattern().replace('_', ' '),
                    Pattern.MULTILINE | Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
            if (!regex.matcher(msg).matches()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Retry-After 多源归一（设计 §3.7）。优先级：
     * <ol>
     *   <li>HTTP 头 retry-after-ms（毫秒）</li>
     *   <li>HTTP 头 retry-after（秒或 HTTP-date）</li>
     *   <li>body 字段（命中 mapping 的 retryAfterPath 优先，否则 errorResponse.retryAfterPath）</li>
     * </ol>
     * 全部缺失返回 null（策略层退回纯 full-jitter 退避）。解析失败不抛异常（容错）。
     */
    private Long resolveRetryAfterMs(Map<String, String> headers, Map<String, Object> bodyMap,
                                     LlmErrorResponseModel errorResponse, LlmErrorMappingModel matched) {
        if (headers != null) {
            Long fromHeaderMs = findHeaderMs(headers, "retry-after-ms");
            if (fromHeaderMs != null) {
                return fromHeaderMs;
            }
            Long fromHeaderSec = parseRetryAfterHeader(findHeaderIgnoreCase(headers, "retry-after"));
            if (fromHeaderSec != null) {
                return fromHeaderSec;
            }
        }
        String bodyPath = matched != null ? matched.getRetryAfterPath() : null;
        if (bodyPath == null && errorResponse != null) {
            bodyPath = errorResponse.getRetryAfterPath();
        }
        if (bodyPath != null) {
            Long fromBody = getLongByPath(bodyMap, bodyPath);
            if (fromBody != null) {
                // body 字段单位按毫秒处理（与 retry-after-ms 同），最常见为 provider 明确毫秒值
                return fromBody;
            }
        }
        return null;
    }

    private static String findHeaderIgnoreCase(Map<String, String> headers, String name) {
        if (headers == null) {
            return null;
        }
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static Long findHeaderMs(Map<String, String> headers, String name) {
        String v = findHeaderIgnoreCase(headers, name);
        if (StringHelper.isEmpty(v)) {
            return null;
        }
        Long ms = ConvertHelper.toLong(v, null);
        return ms;
    }

    /**
     * 解析 Retry-After 头：数字按秒，否则尝试 RFC 1123 HTTP-date。失败返回 null。
     */
    private static Long parseRetryAfterHeader(String value) {
        if (StringHelper.isEmpty(value)) {
            return null;
        }
        Long seconds = ConvertHelper.toLong(value, null);
        if (seconds != null) {
            return seconds * 1000L;
        }
        try {
            long target = ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant().toEpochMilli();
            long now = System.currentTimeMillis();
            long delta = target - now;
            return delta > 0 ? delta : 0L;
        } catch (DateTimeParseException e) {
            LOG.debug("nop.ai.parse-retry-after-fail: value={}, using null (fallback jitter)", value);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseBodyMap(String responseBody) {
        if (StringHelper.isEmpty(responseBody)) {
            return java.util.Collections.emptyMap();
        }
        try {
            Object parsed = JSON.parse(responseBody);
            if (parsed instanceof Map) {
                return (Map<String, Object>) parsed;
            }
        } catch (Exception e) {
            LOG.debug("nop.ai.parse-error-body-fail: not JSON, field extraction skipped");
        }
        return java.util.Collections.emptyMap();
    }

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
            case "completed":
                return "stop";
            case "length":
            case "max_tokens":
            case "incomplete":
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
