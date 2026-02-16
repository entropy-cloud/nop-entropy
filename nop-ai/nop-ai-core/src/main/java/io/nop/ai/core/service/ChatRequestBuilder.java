package io.nop.ai.core.service;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.core.model.ApiStyle;
import io.nop.ai.core.model.LlmModel;
import io.nop.ai.core.model.LlmModelModel;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.http.api.client.HttpRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.ai.core.AiCoreConstants.CONFIG_VAR_LLM_BASE_URL;
import static io.nop.ai.core.AiCoreConstants.PLACE_HOLDER_LLM_NAME;
import static io.nop.ai.core.AiCoreErrors.ARG_LLM_NAME;
import static io.nop.ai.core.AiCoreErrors.ERR_AI_SERVICE_NO_BASE_URL;
import static io.nop.ai.core.service.ToolCallHelper.convertAnthropicToolDefinitions;

/**
 * 聊天请求构建器。
 * <p>
 * 负责根据不同的 API 风格构建 HTTP 请求。
 * 参考 solon-ai 的 dialect 模式，支持多种 AI 提供商的特定格式：
 * <ul>
 *   <li>OpenAI - 标准 messages 格式</li>
 *   <li>Anthropic (Claude) - system 消息分离，content 数组</li>
 *   <li>Google (Gemini) - contents 数组，generationConfig</li>
 *   <li>Ollama - 本地部署，options 嵌套</li>
 * </ul>
 * <p>
 * 性能优化：
 * <ul>
 *   <li>缓存 modelConfig 避免重复查找</li>
 *   <li>延迟创建集合对象</li>
 *   <li>避免重复字符串操作</li>
 * </ul>
 */
public class ChatRequestBuilder {

    private final LlmModel config;
    private final String provider;
    private final String model;
    private final ChatRequest request;
    private final boolean stream;

    // 缓存解析后的路径，避免重复查找
    private final LlmModelModel modelConfig;

    public ChatRequestBuilder(LlmModel config, String provider, String model, ChatRequest request, boolean stream) {
        this.config = config;
        this.provider = provider;
        this.model = model;
        this.request = request;
        this.stream = stream;
        this.modelConfig = LlmConfigHelper.getModelConfig(config, model);
    }

    /**
     * 构建 HTTP 请求
     */
    public HttpRequest build() {
        String baseUrl = resolveBaseUrl();
        String apiKey = resolveApiKey();

        HttpRequest httpRequest = new HttpRequest();
        httpRequest.setMethod("POST");
        httpRequest.setUrl(buildUrl(baseUrl, apiKey));
        setHeaders(httpRequest, apiKey);
        httpRequest.setBody(buildBody());

        return httpRequest;
    }

    /**
     * 构建请求 URL
     */
    private String buildUrl(String baseUrl, String apiKey) {
        String url = StringHelper.appendPath(baseUrl, config.getChatUrl());

        // Gemini 使用 URL 查询参数传递 API key
        if (config.getApiStyle() == ApiStyle.gemini && !StringHelper.isEmpty(apiKey)) {
            url = url + (url.contains("?") ? "&" : "?") + "key=" + apiKey;
        }

        return url;
    }

    /**
     * 设置请求头
     */
    private void setHeaders(HttpRequest httpRequest, String apiKey) {
        httpRequest.setHeader("Content-Type", "application/json");

        if (!StringHelper.isEmpty(apiKey)) {
            // Gemini 在 URL 中传递 key，不需要 header
            if (config.getApiStyle() == ApiStyle.gemini) {
                return;
            }

            if (config.getApiKeyHeader() != null) {
                httpRequest.setHeader(config.getApiKeyHeader(), apiKey);
            } else {
                httpRequest.setBearerToken(apiKey);
            }
        }
    }

    /**
     * 构建请求体 JSON
     */
    private Map<String, Object> buildBody() {
        ApiStyle apiStyle = config.getApiStyle() != null ? config.getApiStyle() : ApiStyle.openai;
        Map<String, Object> body = new LinkedHashMap<>();

        switch (apiStyle) {
            case anthropic:
                buildAnthropicBody(body);
                break;
            case gemini:
                buildGeminiBody(body);
                break;
            case ollama:
                buildOllamaBody(body);
                break;
            case openai:
            default:
                buildOpenAiBody(body);
                break;
        }

        return body;
    }

    /**
     * 构建 OpenAI 风格请求体
     * <pre>
     * {
     *   "model": "gpt-4",
     *   "messages": [{"role": "user", "content": "..."}],
     *   "temperature": 0.7,
     *   "max_tokens": 1000,
     *   "stream": true
     * }
     * </pre>
     */
    private void buildOpenAiBody(Map<String, Object> body) {
        body.put("model", model);
        body.put("messages", buildMessages());
        body.put("stream", stream);

        ChatOptions options = request.getOptions();
        if (options != null) {
            addOptionIfNotNull(body, "temperature", options.getTemperature());
            addOptionIfNotNull(body, "max_tokens", resolveMaxTokens());
            addOptionIfNotNull(body, "top_p", options.getTopP());
            addOptionIfNotNull(body, "top_k", options.getTopK());
            addOptionIfNotNull(body, "stop", options.getStop());

            if (options.getTools() != null && !options.getTools().isEmpty()) {
                body.put("tools", ToolCallHelper.convertOpenAiToolDefinitions(options.getTools()));
            }
        }
    }

    /**
     * 构建 Anthropic (Claude) 风格请求体
     * <pre>
     * {
     *   "model": "claude-3-5-sonnet",
     *   "system": "You are a helpful assistant",
     *   "messages": [{"role": "user", "content": "..."}],
     *   "max_tokens": 4096,
     *   "temperature": 0.7,
     *   "stream": true
     * }
     * </pre>
     */
    private void buildAnthropicBody(Map<String, Object> body) {
        body.put("model", model);
        body.put("stream", stream);

        // Claude 要求必须指定 max_tokens
        Integer maxTokens = resolveMaxTokens();
        if (maxTokens == null) {
            maxTokens = 4096; // Claude 默认值
        }
        body.put("max_tokens", maxTokens);

        ChatOptions options = request.getOptions();
        if (options != null) {
            addOptionIfNotNull(body, "temperature", options.getTemperature());
            addOptionIfNotNull(body, "top_p", options.getTopP());
            addOptionIfNotNull(body, "top_k", options.getTopK());
            addOptionIfNotNull(body, "stop_sequences", options.getStop());

            if (options.getTools() != null && !options.getTools().isEmpty()) {
                body.put("tools", convertAnthropicToolDefinitions(options.getTools()));
            }
        }

        // 分离 system 消息到单独字段
        List<Map<String, Object>> messages = new ArrayList<>();
        for (ChatMessage msg : request.getMessages()) {
            if (msg instanceof ChatSystemMessage) {
                body.put("system", msg.getContent());
            } else {
                messages.add(MessageConverter.convert(msg, ApiStyle.anthropic, modelConfig, msg == request.getLastMessage(), options));
            }
        }

        if (!messages.isEmpty()) {
            body.put("messages", messages);
        }
    }

    /**
     * 构建 Google Gemini 风格请求体
     * <pre>
     * {
     *   "systemInstruction": {"parts": [{"text": "..."}]},
     *   "contents": [
     *     {"role": "user", "parts": [{"text": "..."}]}
     *   ],
     *   "generationConfig": {
     *     "temperature": 0.7,
     *     "maxOutputTokens": 1000
     *   }
     * }
     * </pre>
     */
    private void buildGeminiBody(Map<String, Object> body) {
        ChatOptions options = request.getOptions();

        // 分离 system 消息
        String systemContent = null;
        List<Map<String, Object>> contents = new ArrayList<>();

        for (ChatMessage msg : request.getMessages()) {
            if (msg instanceof ChatSystemMessage) {
                systemContent = msg.getContent();
            } else {
                contents.add(convertGeminiContent(msg));
            }
        }

        if (systemContent != null) {
            Map<String, Object> systemInstruction = new LinkedHashMap<>();
            systemInstruction.put("parts", singletonTextList(systemContent));
            body.put("systemInstruction", systemInstruction);
        }

        if (!contents.isEmpty()) {
            body.put("contents", contents);
        }

        // generationConfig
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        if (options != null) {
            addOptionIfNotNull(generationConfig, "temperature", options.getTemperature());
            addOptionIfNotNull(generationConfig, "maxOutputTokens", resolveMaxTokens());
            addOptionIfNotNull(generationConfig, "topP", options.getTopP());
            addOptionIfNotNull(generationConfig, "topK", options.getTopK());
            addOptionIfNotNull(generationConfig, "stopSequences", options.getStop());
        }
        if (!generationConfig.isEmpty()) {
            body.put("generationConfig", generationConfig);
        }
    }

    /**
     * 转换 Gemini 内容格式
     */
    private Map<String, Object> convertGeminiContent(ChatMessage message) {
        Map<String, Object> content = new LinkedHashMap<>();

        // Gemini 使用 "user" 和 "model" 角色
        String role = MessageConverter.getRole(message);
        if ("assistant".equals(role)) {
            role = "model";
        }
        content.put("role", role);

        // 内容放在 parts 数组中
        content.put("parts", singletonTextList(message.getContent()));

        return content;
    }

    /**
     * 创建文本 parts 列表
     */
    private List<Map<String, String>> singletonTextList(String text) {
        List<Map<String, String>> list = new ArrayList<>(1);
        Map<String, String> part = new LinkedHashMap<>(1);
        part.put("text", text);
        list.add(part);
        return list;
    }



    /**
     * 构建 Ollama 风格请求体
     * <pre>
     * {
     *   "model": "llama2",
     *   "messages": [...],
     *   "options": {
     *     "temperature": 0.7,
     *     "num_predict": 1000
     *   },
     *   "stream": true
     * }
     * </pre>
     */
    private void buildOllamaBody(Map<String, Object> body) {
        body.put("model", model);
        body.put("messages", buildMessages());
        body.put("stream", stream);

        ChatOptions options = request.getOptions();
        if (options != null) {
            Map<String, Object> ollamaOptions = new LinkedHashMap<>();
            addOptionIfNotNull(ollamaOptions, "temperature", options.getTemperature());
            addOptionIfNotNull(ollamaOptions, "num_predict", resolveMaxTokens());
            addOptionIfNotNull(ollamaOptions, "top_p", options.getTopP());
            addOptionIfNotNull(ollamaOptions, "top_k", options.getTopK());
            addOptionIfNotNull(ollamaOptions, "stop", options.getStop());

            if (!ollamaOptions.isEmpty()) {
                body.put("options", ollamaOptions);
            }

            if (options.getTools() != null && !options.getTools().isEmpty()) {
                body.put("tools", ToolCallHelper.convertOpenAiToolDefinitions(options.getTools()));
            }
        }
    }

    /**
     * 构建消息列表（OpenAI/Ollama 格式）
     */
    private List<Map<String, Object>> buildMessages() {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (request.getMessages() == null) {
            return messages;
        }

        ChatMessage lastMessage = request.getLastMessage();
        ChatOptions options = request.getOptions();
        ApiStyle apiStyle = config.getApiStyle() != null ? config.getApiStyle() : ApiStyle.openai;

        for (ChatMessage msg : request.getMessages()) {
            // Anthropic 和 Gemini 单独处理 system 消息
            if (msg instanceof ChatSystemMessage) {
                continue;
            }
            Map<String, Object> msgMap = MessageConverter.convert(
                    msg, apiStyle, modelConfig, msg == lastMessage, options
            );
            messages.add(msgMap);
        }

        return messages;
    }

    /**
     * 解析最大 token 数
     */
    private Integer resolveMaxTokens() {
        ChatOptions options = request.getOptions();
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
     * 解析Base URL
     */
    private String resolveBaseUrl() {
        String baseUrlKey = StringHelper.replace(CONFIG_VAR_LLM_BASE_URL, PLACE_HOLDER_LLM_NAME, provider);
        String baseUrl = (String) AppConfig.var(baseUrlKey);

        if (StringHelper.isEmpty(baseUrl)) {
            baseUrl = config.getBaseUrl();
        }

        if (StringHelper.isEmpty(baseUrl)) {
            throw new NopException(ERR_AI_SERVICE_NO_BASE_URL).param(ARG_LLM_NAME, provider);
        }

        // 模板变量替换
        return StringHelper.renderTemplate(baseUrl, varName -> {
            if ("model".equals(varName)) {
                return model;
            }
            String value = AppConfig.var(varName, "");
            return StringHelper.isEmpty(value) ? "{" + varName + "}" : value;
        });
    }

    /**
     * 解析API Key
     */
    private String resolveApiKey() {
        // 从配置中读取，具体实现在LlmConfigHelper中
        return LlmConfigHelper.resolveApiKey(provider);
    }

    /**
     * 添加非空选项
     */
    private void addOptionIfNotNull(Map<String, Object> body, String key, Object value) {
        if (value != null) {
            body.put(key, value);
        }
    }
}
