/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.core.service;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatToolDefinition;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.messages.ChatUsage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.api.core.json.JSON;
import io.nop.api.core.util.ICancelToken;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IServerEventResponse;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

/**
 * 实现 nop-ai-api 中定义的 IChatService 接口。
 * <p>
 * 该实现类直接使用 IHttpClient 进行 HTTP 调用，不再依赖内部 AI core API。
 */
public class ChatServiceImpl implements IChatService {

    private IHttpClient httpClient;

    @Inject
    public void setHttpClient(IHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
        HttpRequest httpRequest = buildHttpRequest(request);
        return httpClient.fetchAsync(httpRequest, cancelToken)
                .thenApply(response -> parseChatResponse(response.getBodyAsString(), request.getOptions()));
    }

    @Override
    public Flow.Publisher<ChatStreamChunk> callStream(ChatRequest request, ICancelToken cancelToken) {
        SubmissionPublisher<ChatStreamChunk> publisher = new SubmissionPublisher<>();
        HttpRequest httpRequest = buildHttpRequest(request);
        httpRequest.setHeader("Accept", "text/event-stream");

        Flow.Publisher<IServerEventResponse> eventPublisher = httpClient.fetchServerEventFlow(httpRequest, cancelToken);
        eventPublisher.subscribe(new Flow.Subscriber<IServerEventResponse>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(IServerEventResponse item) {
                ChatStreamChunk chunk = parseStreamChunk(item);
                if (chunk != null) publisher.submit(chunk);
            }

            @Override
            public void onError(Throwable throwable) {
                publisher.closeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                ChatStreamChunk finalChunk = new ChatStreamChunk();
                finalChunk.setFinishReason("stop");
                publisher.submit(finalChunk);
                publisher.close();
            }
        });

        return publisher;
    }

    protected HttpRequest buildHttpRequest(ChatRequest request) {
        HttpRequest httpRequest = new HttpRequest();
        httpRequest.setMethod("POST");
        httpRequest.setHeader("Content-Type", "application/json");

        Map<String, Object> body = new HashMap<>();
        List<Map<String, Object>> messages = new ArrayList<>();
        if (request.getMessages() != null) {
            for (ChatMessage message : request.getMessages()) {
                messages.add(convertMessageToMap(message));
            }
        }
        body.put("messages", messages);

        ChatOptions options = request.getOptions();
        if (options != null) {
            if (options.getModel() != null) body.put("model", options.getModel());
            if (options.getTemperature() != null) body.put("temperature", options.getTemperature());
            if (options.getTopP() != null) body.put("top_p", options.getTopP());
            if (options.getMaxTokens() != null) body.put("max_tokens", options.getMaxTokens());
            if (options.getStream() != null) body.put("stream", options.getStream());
            if (options.getTools() != null && !options.getTools().isEmpty()) {
                List<Map<String, Object>> tools = new ArrayList<>();
                for (ChatToolDefinition toolDef : options.getTools()) {
                    tools.add(convertToolDefinitionToMap(toolDef));
                }
                body.put("tools", tools);
            }
        }

        httpRequest.setBody(JSON.stringify(body));
        return httpRequest;
    }

    protected Map<String, Object> convertMessageToMap(ChatMessage message) {
        Map<String, Object> map = new HashMap<>();
        if (message instanceof ChatUserMessage) {
            ChatUserMessage userMsg = (ChatUserMessage) message;
            map.put("role", "user");
            map.put("content", userMsg.getContent());
        } else if (message instanceof ChatAssistantMessage) {
            ChatAssistantMessage assistantMsg = (ChatAssistantMessage) message;
            map.put("role", "assistant");
            map.put("content", assistantMsg.getContent());
            if (assistantMsg.getThink() != null) map.put("thinking", assistantMsg.getThink());
        } else if (message instanceof ChatSystemMessage) {
            map.put("role", "system");
            map.put("content", ((ChatSystemMessage) message).getContent());
        } else if (message instanceof ChatToolResponseMessage) {
            ChatToolResponseMessage toolMsg = (ChatToolResponseMessage) message;
            map.put("role", "tool");
            map.put("content", toolMsg.getContent());
            map.put("tool_call_id", toolMsg.getToolCallId());
        }
        return map;
    }

    protected Map<String, Object> convertToolDefinitionToMap(ChatToolDefinition toolDef) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", "function");
        Map<String, Object> function = new HashMap<>();
        function.put("name", toolDef.getName());
        function.put("description", toolDef.getDescription());
        if (toolDef.getParameters() != null) function.put("parameters", toolDef.getParameters());
        map.put("function", function);
        return map;
    }

    @SuppressWarnings("unchecked")
    protected ChatResponse parseChatResponse(String body, ChatOptions options) {
        if (body == null || body.isEmpty()) {
            return ChatResponse.error("NULL_RESPONSE", "Empty response");
        }
        try {
            Map<String, Object> responseMap = JSON.parseObject(body, Map.class);
            ChatResponse response = new ChatResponse();
            response.setId((String) responseMap.get("id"));
            response.setModel((String) responseMap.get("model"));

            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> choice = choices.get(0);
                response.setFinishReason((String) choice.get("finish_reason"));
                Map<String, Object> messageMap = (Map<String, Object>) choice.get("message");
                if (messageMap != null) {
                    ChatAssistantMessage message = new ChatAssistantMessage();
                    message.setContent((String) messageMap.get("content"));
                    message.setThink((String) messageMap.get("thinking"));
                    response.setMessage(message);
                }
            }

            Map<String, Object> usageMap = (Map<String, Object>) responseMap.get("usage");
            if (usageMap != null) {
                ChatUsage usage = new ChatUsage();
                usage.setPromptTokens(getInt(usageMap.get("prompt_tokens")));
                usage.setCompletionTokens(getInt(usageMap.get("completion_tokens")));
                usage.setTotalTokens(getInt(usageMap.get("total_tokens")));
                response.setUsage(usage);
            }
            return response;
        } catch (Exception e) {
            return ChatResponse.error("PARSE_ERROR", e.getMessage());
        }
    }

    private int getInt(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    @SuppressWarnings("unchecked")
    protected ChatStreamChunk parseStreamChunk(IServerEventResponse event) {
        if (event == null || event.getData() == null) return null;
        String data = event.getData();
        if (data.isEmpty() || data.equals("[DONE]")) return null;
        try {
            Map<String, Object> dataMap = JSON.parseObject(data, Map.class);
            ChatStreamChunk chunk = new ChatStreamChunk();
            chunk.setId((String) dataMap.get("id"));
            chunk.setRole("assistant");
            List<Map<String, Object>> choices = (List<Map<String, Object>>) dataMap.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> choice = choices.get(0);
                Map<String, Object> delta = (Map<String, Object>) choice.get("delta");
                if (delta != null) {
                    chunk.setContent((String) delta.get("content"));
                    chunk.setThinking((String) delta.get("thinking"));
                }
                chunk.setFinishReason((String) choice.get("finish_reason"));
            }
            return chunk;
        } catch (Exception e) {
            return null;
        }
    }
}
