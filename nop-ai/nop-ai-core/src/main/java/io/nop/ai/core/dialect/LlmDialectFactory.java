package io.nop.ai.core.dialect;

import io.nop.ai.core.model.ApiStyle;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM 方言工厂。
 * <p>
 * 根据 API 风格创建对应的方言实现。
 *
 * @author canonical_entropy@163.com
 */
public class LlmDialectFactory {

    private static final Map<ApiStyle, ILlmDialect> DIALECTS = new ConcurrentHashMap<>();

    static {
        // 注册默认方言
        register(ApiStyle.openai, new OpenAiDialect());
        register(ApiStyle.anthropic, new AnthropicDialect());
        register(ApiStyle.gemini, new GeminiDialect());
        register(ApiStyle.ollama, new OllamaDialect());
    }

    /**
     * 获取方言实现
     *
     * @param apiStyle API 风格
     * @return 对应的方言实现，如果未找到返回 OpenAI 方言作为默认
     */
    public static ILlmDialect getDialect(ApiStyle apiStyle) {
        if (apiStyle == null) {
            apiStyle = ApiStyle.openai;
        }
        ILlmDialect dialect = DIALECTS.get(apiStyle);
        return dialect != null ? dialect : DIALECTS.get(ApiStyle.openai);
    }

    /**
     * 注册自定义方言（覆盖模式）
     *
     * @param apiStyle API 风格
     * @param dialect 方言实现
     */
    public static void register(ApiStyle apiStyle, ILlmDialect dialect) {
        DIALECTS.put(apiStyle, dialect);
    }

    /**
     * 注册自定义方言（可选是否覆盖已存在的方言）
     *
     * @param apiStyle API 风格
     * @param dialect 方言实现
     * @param overwrite 是否覆盖已存在的方言
     * @return 是否注册成功（如果 overwrite=false 且方言已存在，则返回 false）
     */
    public static boolean register(ApiStyle apiStyle, ILlmDialect dialect, boolean overwrite) {
        if (overwrite) {
            DIALECTS.put(apiStyle, dialect);
            return true;
        } else {
            return DIALECTS.putIfAbsent(apiStyle, dialect) == null;
        }
    }

    /**
     * 检查是否支持该 API 风格
     *
     * @param apiStyle API 风格
     * @return 是否支持
     */
    public static boolean isSupported(ApiStyle apiStyle) {
        return apiStyle != null && DIALECTS.containsKey(apiStyle);
    }
}
