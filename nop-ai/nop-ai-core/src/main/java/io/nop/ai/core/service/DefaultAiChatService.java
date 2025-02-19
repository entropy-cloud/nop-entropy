package io.nop.ai.core.service;

import io.nop.ai.core.api.chat.AiChatOptions;
import io.nop.ai.core.api.chat.IAiChatService;
import io.nop.ai.core.api.chat.IAiChatSession;
import io.nop.ai.core.api.messages.AiResultMessage;
import io.nop.ai.core.api.messages.AiMessage;
import io.nop.ai.core.api.messages.MessageStatus;
import io.nop.ai.core.api.messages.Prompt;
import io.nop.ai.core.model.LlmModel;
import io.nop.ai.core.model.LlmRequestModel;
import io.nop.ai.core.model.LlmResponseModel;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.ratelimit.DefaultRateLimiter;
import io.nop.commons.concurrent.ratelimit.IRateLimiter;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.xlang.api.XLang;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

import static io.nop.ai.core.AiCoreConfigs.CFG_AI_SERVICE_DEFAULT_LLM;
import static io.nop.ai.core.AiCoreConfigs.CFG_AI_SERVICE_LOG_MESSAGE;
import static io.nop.ai.core.AiCoreConstants.CONFIG_VAR_LLM_API_KEY;
import static io.nop.ai.core.AiCoreConstants.CONFIG_VAR_LLM_BASE_URL;
import static io.nop.ai.core.AiCoreConstants.PLACE_HOLDER_LLM_NAME;
import static io.nop.ai.core.AiCoreErrors.ARG_HTTP_STATUS;
import static io.nop.ai.core.AiCoreErrors.ARG_LLM_NAME;
import static io.nop.ai.core.AiCoreErrors.ARG_OPTION_NAME;
import static io.nop.ai.core.AiCoreErrors.ARG_PROP_PATH;
import static io.nop.ai.core.AiCoreErrors.ERR_AI_SERVICE_HTTP_ERROR;
import static io.nop.ai.core.AiCoreErrors.ERR_AI_SERVICE_NO_BASE_URL;
import static io.nop.ai.core.AiCoreErrors.ERR_AI_SERVICE_NO_DEFAULT_LLMS;
import static io.nop.ai.core.AiCoreErrors.ERR_AI_SERVICE_OPTION_NOT_SET;

public class DefaultAiChatService implements IAiChatService {
    static final Logger LOG = LoggerFactory.getLogger(DefaultAiChatService.class);

    private IHttpClient httpClient;

    private Map<String, IRateLimiter> rateLimiters = new ConcurrentHashMap<>();

    @Inject
    public void setHttpClient(IHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    protected synchronized IRateLimiter getRateLimiter(String llmName) {
        IRateLimiter rateLimiter = rateLimiters.get(llmName);
        if (rateLimiter != null)
            return rateLimiter;

        LlmModel llmModel = loadLlmModel(llmName);
        Double rateLimit = llmModel.getRateLimit();
        if (rateLimit == null)
            return null;

        rateLimiter = new DefaultRateLimiter(rateLimit);
        rateLimiters.put(llmName, rateLimiter);
        return rateLimiter;
    }

    protected String getLlmName(AiChatOptions options) {
        String llm = options.getLlm();
        if (llm == null)
            llm = CFG_AI_SERVICE_DEFAULT_LLM.get();
        if (StringHelper.isEmpty(llm))
            throw new NopException(ERR_AI_SERVICE_NO_DEFAULT_LLMS);
        return llm;
    }

    protected LlmModel loadLlmModel(String llmName) {
        String modelPath = buildLlmModelPath(llmName);
        return (LlmModel) ResourceComponentManager.instance().loadComponentModel(modelPath);
    }

    protected String buildLlmModelPath(String llmName) {
        Guard.checkArgument(!llmName.contains(".."), "llmName");
        return "/nop/ai/llm/" + llmName + ".llm.xml";
    }

    @Override
    public IAiChatSession newSession(AiChatOptions options) {
        DefaultAiChatSession session = new DefaultAiChatSession(this);
        session.setSessionId(generateSessionId());
        return session;
    }

    protected String generateSessionId() {
        return StringHelper.generateUUID();
    }

    @Override
    public CompletionStage<AiResultMessage> sendChatAsync(Prompt prompt, AiChatOptions options, ICancelToken cancelToken) {
        Guard.notNull(options, "chatOptions");

        String llmName = getLlmName(options);
        LlmModel llmModel = loadLlmModel(llmName);

        IRateLimiter rateLimiter = getRateLimiter(llmName);

        if (rateLimiter != null)
            rateLimiter.tryAcquire();
        return doSendChat(llmName, llmModel, prompt, options, cancelToken);
    }

    protected CompletionStage<AiResultMessage> doSendChat(String llmName,
                                                          LlmModel llmModel,
                                                          Prompt prompt, AiChatOptions options,
                                                          ICancelToken cancelToken) {
        boolean logMessage = CFG_AI_SERVICE_LOG_MESSAGE.get();
        if (logMessage) {
            for (AiMessage message : prompt.getMessages()) {
                logRequest(message);
            }
        }

        HttpRequest request = buildHttpRequest(llmName, llmModel, prompt, options);

        IEvalScope scope = XLang.newEvalScope();

        if (llmModel.getBuildRequest() != null) {
            llmModel.getBuildRequest().call3(null, request, prompt, options, scope);
        }

        return httpClient.fetchAsync(request, cancelToken).thenApply(
                res -> {
                    if (res.getHttpStatus() != 200)
                        throw new NopException(ERR_AI_SERVICE_HTTP_ERROR)
                                .param(ARG_LLM_NAME, llmName).param(ARG_HTTP_STATUS, res.getHttpStatus());

                    Map<String, Object> response = res.getBodyAsBean(Map.class);

                    AiResultMessage resultMessage = parseHttpResponse(llmName, llmModel, response, prompt, options);
                    if (llmModel.getParseResponse() != null) {
                        llmModel.getParseResponse().call3(null, response, resultMessage, options, scope);
                    }
                    return resultMessage;
                });
    }

    protected HttpRequest buildHttpRequest(String llmName, LlmModel llmModel,
                                           Prompt prompt, AiChatOptions options) {
        String url = getBaseUrl(llmName, llmModel);

        url = StringHelper.appendPath(url, llmModel.getChatUrl());

        String apiKey = getApiKey(llmName);

        HttpRequest request = HttpRequest.post(url);
        if (!StringHelper.isEmpty(apiKey))
            request.setBearerToken(apiKey);

        initHeaders(request, options);
        Map<String, Object> body = new HashMap<>();
        initBody(llmName, llmModel, body, prompt, options);
        request.setBody(body);
        return request;
    }

    protected String getBaseUrl(String llmName, LlmModel llmModel) {
        String baseUrlKey = StringHelper.replace(CONFIG_VAR_LLM_BASE_URL, PLACE_HOLDER_LLM_NAME, llmName);
        String baseUrl = (String) AppConfig.var(baseUrlKey);
        if (StringHelper.isEmpty(baseUrl))
            baseUrl = llmModel.getBaseUrl();
        if (StringHelper.isEmpty(baseUrl))
            throw new NopException(ERR_AI_SERVICE_NO_BASE_URL).param(ARG_LLM_NAME, llmName);
        return baseUrl;
    }

    protected String getApiKey(String llmName) {
        String apiKeyName = StringHelper.replace(CONFIG_VAR_LLM_API_KEY, PLACE_HOLDER_LLM_NAME, llmName);
        String apiKey = (String) AppConfig.var(apiKeyName);
        return apiKey;
    }

    protected void initHeaders(HttpRequest request, AiChatOptions options) {
        Long requestTimeout = options.getRequestTimeout();
        if (requestTimeout != null) {
            request.timeout(requestTimeout);
        }
    }

    /**
     * {
     * "messages": [
     * {
     * "content": "You are a helpful assistant",
     * "role": "system"
     * },
     * {
     * "content": "Hi",
     * "role": "user"
     * }
     * ],
     * "model": "deepseek-chat",
     * "frequency_penalty": 0,
     * "max_tokens": 2048,
     * "presence_penalty": 0,
     * "response_format": {
     * "type": "text"
     * },
     * "stop": null,
     * "stream": false,
     * "stream_options": null,
     * "temperature": 1,
     * "top_p": 1,
     * "tools": null,
     * "tool_choice": "none",
     * "logprobs": false,
     * "top_logprobs": null
     * }
     */
    protected void initBody(String llmName,
                            LlmModel llmModel, Map<String, Object> body,
                            Prompt prompt, AiChatOptions options) {
        String model = getModel(llmName, llmModel, options);
        body.put("model", model);
        body.put("stream", false);

        body.put("response_format", Map.of("type", "text"));
        List<Map<String, Object>> messages = new ArrayList<>();
        body.put("messages", messages);

        setOptions(llmModel, body, options);

        List<AiMessage> msgs = prompt.getMessages();
        for (AiMessage msg : msgs) {
            messages.add(Map.of("content", msg.getContent(), "role", getRole(msg)));
        }
    }

    protected String getModel(String llmName, LlmModel llmModel, AiChatOptions options) {
        String model = options.getModel();
        if (StringHelper.isEmpty(model))
            model = llmModel.getDefaultModel();
        if (StringHelper.isEmpty(model))
            throw new NopException(ERR_AI_SERVICE_OPTION_NOT_SET)
                    .param(ARG_LLM_NAME, llmName).param(ARG_OPTION_NAME, "model");
        return model;
    }

    protected void setOptions(LlmModel llmModel, Map<String, Object> body, AiChatOptions options) {
        LlmRequestModel requestModel = llmModel.getRequest();
        if (requestModel == null)
            return;

        setIfNotNull(body, requestModel.getSeedPath(), options.getSeed());
        setIfNotNull(body, requestModel.getMaxTokensPath(), options.getMaxTokens());
        setIfNotNull(body, requestModel.getTemperaturePath(), options.getTemperature());
        setIfNotNull(body, requestModel.getTopPPath(), options.getTopP());
        setIfNotNull(body, requestModel.getTopKPath(), options.getTopK());
        setIfNotNull(body, requestModel.getStopPath(), options.getStop());
        setIfNotNull(body, requestModel.getContextLengthPath(), options.getContextLength());
    }

    protected void setIfNotNull(Map<String, Object> body, String propPath, Object value) {
        if (propPath == null)
            return;

        if (value != null)
            BeanTool.setComplexProperty(body, propPath, value);
    }

    protected String getRole(AiMessage message) {
        return message.getRole();
    }

    protected AiResultMessage parseHttpResponse(String llmName, LlmModel llmModel,
                                                Map<String, Object> response, Prompt prompt,
                                                AiChatOptions options) {

        try {
            AiResultMessage ret = new AiResultMessage();
            parseToResult(ret, llmModel, response);
            checkThink(ret);
            return ret;
        } catch (Exception e) {
            LOG.info("nop.ai.parse-result-fail", e);
            throw NopException.adapt(e);
        }
    }

    protected void parseToResult(AiResultMessage ret, LlmModel llmModel,
                                 Map<String, Object> result) {
        LlmResponseModel responseModel = llmModel.getResponse();

        String content = getString(result, responseModel.getContentPath());
        ret.setContent(content);

        ret.setPromptTokens(getInteger(result, responseModel.getPromptTokensPath()));
        ret.setTotalTokens(getInteger(result, responseModel.getTotalTokensPath()));
        ret.setCompletionTokens(getInteger(result, responseModel.getCompletionTokensPath()));
        ret.setStatus(getMessageStatus(result, responseModel.getStatusPath()));

        if (CFG_AI_SERVICE_LOG_MESSAGE.get()) {
            logResponse(ret);
        }
    }

    protected MessageStatus getMessageStatus(Map<String, Object> body, String propPath) {
        String status = getString(body, propPath);
        if (status == null)
            return null;
        return MessageStatus.END;
    }

    protected String getString(Map<String, Object> result, String path) {
        if (path == null)
            return null;
        return StringHelper.toString(BeanTool.getComplexProperty(result, path), null);
    }

    protected Integer getInteger(Map<String, Object> result, String path) {
        if (path == null)
            return null;
        return ConvertHelper.toInteger(BeanTool.getComplexProperty(result, path),
                err -> new NopException(err).param(ARG_PROP_PATH, path));
    }

    protected void checkThink(AiResultMessage message) {
        String content = message.getContent();
        if (content != null) {
            boolean bThink = content.startsWith("<think>\n");
            if (bThink) {
                int pos2 = content.indexOf("\n</think>\n");
                if (pos2 > 0) {
                    String think = content.substring("<think>\n".length(), pos2);
                    message.setThink(think);
                    pos2 += "\n</think>\n".length();
                    if (pos2 < content.length() && content.charAt(pos2) == '\n')
                        pos2++;

                    message.setContent(content.substring(pos2));
                }
            }
        }
    }

    protected void logRequest(AiMessage message) {
        LOG.info("request:role={},content=\n{}", getRole(message), message.getContent());
    }

    protected void logResponse(AiResultMessage message) {
        LOG.info("response:promptTokens={},completionTokens={},content=\n{}",
                message.getPromptTokens(), message.getCompletionTokens(), message.getContent());
    }

    @Override
    public IAiChatSession getSession(String sessionId) {
        return null;
    }
}