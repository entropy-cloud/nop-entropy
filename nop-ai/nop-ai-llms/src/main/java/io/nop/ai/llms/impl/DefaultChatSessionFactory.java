package io.nop.ai.llms.impl;

import io.nop.ai.core.api.chat.ChatOptions;
import io.nop.ai.core.api.chat.IChatService;
import io.nop.ai.core.api.chat.IChatSession;
import io.nop.ai.core.api.chat.IChatSessionFactory;
import io.nop.ai.core.api.messages.AiResultMessage;
import io.nop.ai.core.api.messages.Message;
import io.nop.ai.core.api.messages.Prompt;
import io.nop.ai.llms.config.LlmConfig;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.concurrent.ratelimit.DefaultRateLimiter;
import io.nop.commons.concurrent.ratelimit.IRateLimiter;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.retry.IRetryPolicy;
import io.nop.commons.util.retry.RetryHelper;
import io.nop.commons.util.retry.RetryPolicy;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

public class DefaultChatSessionFactory implements IChatSessionFactory, IChatService {
    static final Logger LOG = LoggerFactory.getLogger(DefaultChatSessionFactory.class);

    private LlmConfig llmConfig;
    private IHttpClient httpClient;
    private IRateLimiter rateLimiter;
    private IRetryPolicy<ChatOptions> retryPolicy;

    public void setLlmConfig(LlmConfig llmConfig) {
        this.llmConfig = llmConfig;
    }

    public void setHttpClient(IHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public String getModel() {
        return llmConfig.getModel();
    }

    synchronized IRateLimiter getRateLimiter() {
        if (rateLimiter == null)
            rateLimiter = new DefaultRateLimiter(llmConfig.getRateLimit());
        return rateLimiter;
    }

    synchronized IRetryPolicy<ChatOptions> getRetryPolicy() {
        if (retryPolicy == null)
            retryPolicy = RetryPolicy.retryNTimes(llmConfig.getRetryTimes());
        return retryPolicy;
    }

    IScheduledExecutor getRetryExecutor() {
        return GlobalExecutors.globalTimer();
    }

    @Override
    public IChatSession newSession(ChatOptions options) {
        DefaultChatSession session = new DefaultChatSession(this);
        session.setSessionId(generateSessionId());
        return session;
    }

    protected String generateSessionId() {
        return StringHelper.generateUUID();
    }

    @Override
    public CompletionStage<AiResultMessage> sendChatAsync(Prompt prompt, ChatOptions options, ICancelToken cancelToken) {
        IRateLimiter rateLimiter = getRateLimiter();
        return RetryHelper.retryExecute(() -> {
            rateLimiter.tryAcquire();
            return doSendChat(prompt, options, cancelToken);
        }, getRetryPolicy(), getRetryExecutor(), options);
    }

    protected CompletionStage<AiResultMessage> doSendChat(Prompt prompt, ChatOptions options, ICancelToken cancelToken) {
        return httpClient.fetchAsync(buildHttpRequest(prompt, options), cancelToken).thenApply(this::parseResult);
    }

    protected HttpRequest buildHttpRequest(Prompt prompt, ChatOptions options) {
        String url = llmConfig.getBaseUrl();
        url = StringHelper.appendPath(url, llmConfig.getChatUrl());
        HttpRequest request = HttpRequest.post(url);
        if (!StringHelper.isEmpty(llmConfig.getApiKey()))
            request.setBearerToken(llmConfig.getApiKey());
        initHeaders(request, options);
        Map<String, Object> body = new HashMap<>();
        initBody(body, prompt, options);
        request.setBody(body);
        return request;
    }

    protected void initHeaders(HttpRequest request, ChatOptions options) {
        Long requestTimeout = llmConfig.getRequestTimeout();
        if (options != null) {
            if (options.getRequestTimeout() != null)
                requestTimeout = options.getRequestTimeout();
        }
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
    void initBody(Map<String, Object> body, Prompt prompt, ChatOptions options) {
        body.put("model", llmConfig.getModel());
        body.put("stream", false);
        body.put("response_format", Map.of("type", "text"));
        List<Map<String, Object>> messages = new ArrayList<>();
        body.put("messages", messages);

        setMaxTokens(body, options);
        setTemperature(body, options);

        List<Message> msgs = prompt.toMessages();
        for (Message msg : msgs) {
            logRequest(msg);
            messages.add(Map.of("content", msg.getMessageContent(), "role", getRole(msg)));
        }
    }

    void setMaxTokens(Map<String, Object> body, ChatOptions options) {
        Integer maxTokens = this.llmConfig.getMaxTokens();
        if (options != null) {
            if (options.getMaxTokens() != null)
                maxTokens = options.getMaxTokens();
        }
        if (maxTokens != null)
            body.put("max_tokens", maxTokens);
    }

    void setTemperature(Map<String, Object> body, ChatOptions options) {
        Float temperature = this.llmConfig.getTemperature();
        if (options != null) {
            if (options.getTemperature() != null)
                temperature = options.getTemperature();
        }
        if (temperature != null)
            body.put("temperature", temperature);
    }

    protected String getRole(Message message) {
        return message.getRole();
    }

    protected AiResultMessage parseResult(IHttpResponse response) {
        try {
            Map<String, Object> result = response.getBodyAsBean(Map.class);
            Map<String, Object> usage = (Map<String, Object>) result.get("usage");
            List<Map<String, Object>> choices = (List<Map<String, Object>>) result.get("choices");

            AiResultMessage ret = new AiResultMessage();
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> choice = choices.get(0);
                Map<String, Object> message = (Map<String, Object>) choice.get("message");
                ret.setContent((String) message.get("content"));
                logResponse(message);
            } else {
                Map<String, Object> message = (Map<String, Object>) result.get("message");
                ret.setContent((String) message.get("content"));
                logResponse(message);
            }

            if (usage != null) {
                ret.setCompletionTokens(ConvertHelper.toInt(usage.get("completion_tokens")));
                ret.setPromptTokens(ConvertHelper.toInt(usage.get("prompt_tokens")));
                ret.setTotalTokens(ConvertHelper.toInt(usage.get("total_tokens")));
            }

            checkThink(ret);
            return ret;
        } catch (Exception e) {
            LOG.info("nop.ai.parse-result-fail", e);
            throw NopException.adapt(e);
        }
    }

    void checkThink(AiResultMessage message) {
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

    protected void logRequest(Message message) {
        if (llmConfig.isLogMessage())
            LOG.info("request:role={},content=\n{}", getRole(message), message.getMessageContent());
    }

    protected void logResponse(Map<String, Object> message) {
        if (llmConfig.isLogMessage())
            LOG.info("response:role={},content=\n{}", message.get("role"), message.get("content"));
    }

    @Override
    public IChatSession getSession(String sessionId) {
        return null;
    }
}