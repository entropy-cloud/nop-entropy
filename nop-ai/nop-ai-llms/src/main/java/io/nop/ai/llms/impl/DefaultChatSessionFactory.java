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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

public class DefaultChatSessionFactory implements IChatSessionFactory, IChatService {
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
        Map<String, Object> body = new HashMap<>();
        initBody(body, prompt);
        request.setBody(body);
        return request;
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
    void initBody(Map<String, Object> body, Prompt prompt) {
        body.put("model", llmConfig.getModel());
        body.put("stream", false);
        body.put("response_format", Map.of("type", "text"));
        List<Map<String, Object>> messages = new ArrayList<>();
        body.put("messages", messages);

        List<Message> msgs = prompt.toMessages();
        for (Message msg : msgs) {
            messages.add(Map.of("content", msg.getMessageContent(), "role", getRole(msg)));
        }
    }

    protected String getRole(Message message) {
        return message.getRole();
    }

    protected AiResultMessage parseResult(IHttpResponse response) {
        Map<String, Object> result = response.getBodyAsBean(Map.class);
        Map<String, Object> usage = (Map<String, Object>) result.get("usage");
        List<Map<String, Object>> choices = (List<Map<String, Object>>) result.get("choices");

        AiResultMessage ret = new AiResultMessage();
        if (choices != null && !choices.isEmpty()) {
            Map<String, Object> choice = choices.get(0);
            Map<String, Object> message = (Map<String, Object>) choice.get("message");
            ret.setFullContent((String) message.get("content"));
        }

        if (usage != null) {
            ret.setCompletionTokens(ConvertHelper.toInt(usage.get("completion_tokens")));
            ret.setPromptTokens(ConvertHelper.toInt(usage.get("prompt_tokens")));
            ret.setTotalTokens(ConvertHelper.toInt(usage.get("total_tokens")));
        }
        return ret;
    }

    @Override
    public IChatSession getSession(String sessionId) {
        return null;
    }
}