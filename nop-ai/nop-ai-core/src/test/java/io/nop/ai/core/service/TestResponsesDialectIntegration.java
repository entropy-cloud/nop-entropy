package io.nop.ai.core.service;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatReasoningMessage;
import io.nop.ai.api.chat.messages.ChatToolCallMessage;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.api.chat.stream.StreamItemPhase;
import io.nop.ai.api.chat.stream.StreamItemType;
import io.nop.api.core.json.JSON;
import io.nop.api.core.util.ICancelToken;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.http.api.client.DownloadOptions;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpInputFile;
import io.nop.http.api.client.IHttpOutputFile;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.client.IServerEventResponse;
import io.nop.http.api.client.UploadOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ResponsesDialect 端到端集成测试（plan 330 Phase 3）。
 * <p>
 * 从 {@code ChatRequest}（含 system + user + tools）→ {@code ChatServiceImpl} → ResponsesDialect
 * → mock {@code /v1/responses} → {@code ChatResponse.messages} 完整路径验证（非流式 + 流式 + 工具循环）。
 * 证明 {@code ApiStyle.responses} 路由到 ResponsesDialect 的链路连通（Anti-Hollow Check）。
 */
public class TestResponsesDialectIntegration extends JunitBaseTestCase {

    private FakeHttpClient httpClient;
    private IChatService chatService;

    @BeforeEach
    void setUp() {
        ChatServiceImpl impl = new ChatServiceImpl();
        httpClient = new FakeHttpClient();
        impl.setHttpClient(httpClient);
        impl.setChatLogger(new DefaultChatLogger());
        chatService = impl;
    }

    @AfterEach
    void tearDown() {
        LlmConfigHelper.reset();
    }

    private ChatOptions responsesOptions() {
        return ChatOptions.builder()
                .provider("responses-test")
                .model("gpt-4o")
                .stream(false)
                .accountKey("sk-test")
                .accountBaseUrl("https://api.example.com")
                .build();
    }

    // ==================== 非流式端到端 ====================

    @Test
    void nonStreamEndToEndProducesSplitMessages() {
        // mock Responses wire：reasoning + assistant text
        String wire = "{\"id\":\"resp_1\",\"model\":\"gpt-4o\"," +
                "\"output\":[" +
                "  {\"type\":\"reasoning\",\"summary\":[{\"type\":\"summary_text\",\"text\":\"thinking\"}]}," +
                "  {\"type\":\"message\",\"role\":\"assistant\"," +
                "   \"content\":[{\"type\":\"output_text\",\"text\":\"Hello!\"}]}" +
                "]," +
                "\"status\":\"completed\"," +
                "\"usage\":{\"input_tokens\":5,\"output_tokens\":3,\"total_tokens\":8}}";
        httpClient.setResponse(fakeResponse(200, wire));

        ChatRequest req = new ChatRequest();
        req.addMessage(new ChatUserMessage("Hi"));
        req.setOptions(responsesOptions());

        ChatResponse response = chatService.call(req, null);

        assertTrue(response.isSuccess(), "200 must be a success ChatResponse");
        assertNotNull(response.getMessages(), "messages must be populated (wiring)");
        assertEquals(2, response.getMessages().size());
        assertTrue(response.getMessages().get(0) instanceof ChatReasoningMessage);
        assertEquals("thinking", response.getMessages().get(0).getContent());
        assertTrue(response.getMessages().get(1) instanceof ChatAssistantMessage);
        assertEquals("Hello!", response.getMessages().get(1).getContent());

        // 接线验证：请求 URL 确实指向 /v1/responses（ApiStyle.responses 路由生效）
        assertTrue(httpClient.lastRequestUrl.endsWith("/v1/responses"),
                "request URL must route to /v1/responses, got: " + httpClient.lastRequestUrl);

        // 接线验证：请求 body 含 Responses wire 结构（store=false + input[]）
        assertNotNull(httpClient.lastRequestBody);
        assertTrue((Boolean) httpClient.lastRequestBody.get("store") == Boolean.FALSE,
                "store must be false in request body");
        assertNotNull(httpClient.lastRequestBody.get("input"),
                "input[] must be present in request body");
    }

    @Test
    void nonStreamInstructionsFromSystemMessage() {
        String wire = "{\"id\":\"resp_2\",\"output\":[{\"type\":\"message\"," +
                "\"role\":\"assistant\",\"content\":[{\"type\":\"output_text\",\"text\":\"ok\"}]}]," +
                "\"status\":\"completed\"}";
        httpClient.setResponse(fakeResponse(200, wire));

        ChatRequest req = new ChatRequest();
        req.addMessage(new io.nop.ai.api.chat.messages.ChatSystemMessage("You are helpful."));
        req.addMessage(new ChatUserMessage("Hi"));
        req.setOptions(responsesOptions());

        ChatResponse response = chatService.call(req, null);

        assertTrue(response.isSuccess());
        assertEquals("ok", response.outputText());

        // 验证 system message → instructions（端到端经 ChatServiceImpl 透传）
        assertEquals("You are helpful.", httpClient.lastRequestBody.get("instructions"));
    }

    // ==================== 工具调用循环端到端 ====================

    @Test
    void toolCallLoopEndToEnd() {
        // 第一轮：mock 响应含 function_call
        String firstWire = "{\"id\":\"resp_3\",\"model\":\"gpt-4o\"," +
                "\"output\":[{\"type\":\"function_call\",\"call_id\":\"call_1\"," +
                "\"name\":\"get_weather\",\"arguments\":\"{\\\"city\\\":\\\"SF\\\"}\"}]," +
                "\"status\":\"completed\"}";
        httpClient.setResponse(fakeResponse(200, firstWire));

        ChatRequest req = new ChatRequest();
        req.addMessage(new ChatUserMessage("What's the weather in SF?"));
        req.setOptions(responsesOptions());

        ChatResponse firstResponse = chatService.call(req, null);

        // 验证首轮：function_call → ChatToolCallMessage
        assertTrue(firstResponse.isSuccess());
        assertEquals(1, firstResponse.getMessages().size());
        ChatToolCallMessage tcm = (ChatToolCallMessage) firstResponse.getMessages().get(0);
        assertEquals("call_1", tcm.getCallId());
        assertEquals("get_weather", tcm.getName());
        assertEquals("SF", tcm.getArguments().get("city"));

        // 模拟工具执行：将首轮 response.messages 回放到上下文，append 工具结果
        List<ChatMessage> context = new ArrayList<>(req.getMessages());
        context.addAll(firstResponse.getMessages());
        context.add(new ChatToolResponseMessage("call_1", "get_weather", "sunny, 72F"));

        // 第二轮：mock 最终响应（assistant 文本）
        String secondWire = "{\"id\":\"resp_4\",\"model\":\"gpt-4o\"," +
                "\"output\":[{\"type\":\"message\",\"role\":\"assistant\"," +
                "\"content\":[{\"type\":\"output_text\",\"text\":\"It's sunny in SF, 72F.\"}]}]," +
                "\"status\":\"completed\"}";
        httpClient.setResponse(fakeResponse(200, secondWire));

        ChatRequest secondReq = new ChatRequest();
        secondReq.setMessages(context);
        secondReq.setOptions(responsesOptions());

        ChatResponse finalResponse = chatService.call(secondReq, null);

        // 验证最终响应
        assertTrue(finalResponse.isSuccess());
        assertEquals("It's sunny in SF, 72F.", finalResponse.outputText());

        // 验证第二轮请求体含 function_call_output（工具结果回传）
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> input = (List<Map<String, Object>>) httpClient.lastRequestBody.get("input");
        assertNotNull(input);
        boolean hasToolOutput = false;
        for (Map<String, Object> item : input) {
            if ("function_call_output".equals(item.get("type"))) {
                assertEquals("call_1", item.get("call_id"));
                assertEquals("sunny, 72F", item.get("output"));
                hasToolOutput = true;
            }
        }
        assertTrue(hasToolOutput, "second round request must contain function_call_output item");
    }

    // ==================== 流式端到端 ====================

    @Test
    void streamEndToEndProducesItemChunks() throws Exception {
        // mock Responses SSE 事件序列
        List<String> events = new ArrayList<>();
        events.add(eventJson("response.created",
                mapOf("response", mapOf("id", "resp_s1", "model", "gpt-4o"))));
        events.add(eventJson("response.output_item.added",
                mapOf("output_index", 0,
                        "item", mapOf("type", "message", "role", "assistant", "content", List.of()))));
        events.add(eventJson("response.output_text.delta",
                mapOf("output_index", 0, "delta", "Hello")));
        events.add(eventJson("response.output_text.delta",
                mapOf("output_index", 0, "delta", " world")));
        events.add(eventJson("response.completed",
                mapOf("response", mapOf("id", "resp_s1", "model", "gpt-4o", "status", "completed"))));

        httpClient.setStreamEvents(events);

        ChatOptions options = ChatOptions.builder()
                .provider("responses-test")
                .model("gpt-4o")
                .stream(true)
                .accountKey("sk-test")
                .accountBaseUrl("https://api.example.com")
                .build();

        ChatRequest req = new ChatRequest();
        req.addMessage(new ChatUserMessage("Hi"));
        req.setOptions(options);

        List<ChatStreamChunk> chunks = new ArrayList<>();
        chatService.callStream(req, null).subscribe(new Flow.Subscriber<>() {
            Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ChatStreamChunk item) {
                chunks.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
            }

            @Override
            public void onComplete() {
            }
        });

        // 等待异步流完成
        TimeUnit.MILLISECONDS.sleep(1000);

        assertFalse(chunks.isEmpty(), "stream must produce chunks");

        // 验证至少含 text DELTA 增量
        boolean hasTextDelta = chunks.stream().anyMatch(c ->
                c.getItemType() == StreamItemType.text && c.getPhase() == StreamItemPhase.DELTA);
        assertTrue(hasTextDelta, "must contain text DELTA chunks");

        // 验证含 DONE 终止 chunk
        boolean hasDone = chunks.stream().anyMatch(c -> c.getPhase() == StreamItemPhase.DONE);
        assertTrue(hasDone, "must contain DONE chunk");
    }

    @Test
    void streamAggregatedEndToEndProducesMessages() throws Exception {
        // 流式汇聚为 ChatResponse（stream=true 时 callAsync → aggregateStreamToResponse）
        List<String> events = new ArrayList<>();
        events.add(eventJson("response.created",
                mapOf("response", mapOf("id", "resp_s2", "model", "gpt-4o"))));
        events.add(eventJson("response.output_item.added",
                mapOf("output_index", 0,
                        "item", mapOf("type", "message", "role", "assistant", "content", List.of()))));
        events.add(eventJson("response.output_text.delta",
                mapOf("output_index", 0, "delta", "Hello")));
        events.add(eventJson("response.output_text.delta",
                mapOf("output_index", 0, "delta", " world")));
        events.add(eventJson("response.completed",
                mapOf("response", mapOf("id", "resp_s2", "model", "gpt-4o", "status", "completed"))));

        httpClient.setStreamEvents(events);

        ChatOptions options = ChatOptions.builder()
                .provider("responses-test")
                .model("gpt-4o")
                .stream(true)
                .accountKey("sk-test")
                .accountBaseUrl("https://api.example.com")
                .build();

        ChatRequest req = new ChatRequest();
        req.addMessage(new ChatUserMessage("Hi"));
        req.setOptions(options);

        // callAsync with stream=true → aggregateStreamToResponse → 汇聚 item 增量为 messages
        ChatResponse response = chatService.call(req, null);

        assertTrue(response.isSuccess());
        assertNotNull(response.getMessages());
        assertEquals("Hello world", response.outputText(),
                "streamed text deltas must aggregate to full text");
    }

    // ==================== helpers ====================

    private static FakeHttpResponse fakeResponse(int status, String body) {
        FakeHttpResponse r = new FakeHttpResponse();
        r.status = status;
        r.body = body;
        r.headers = new LinkedHashMap<>();
        return r;
    }

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static String eventJson(String type, Map<String, Object> body) {
        Map<String, Object> event = new LinkedHashMap<>(body);
        event.put("type", type);
        return JSON.stringify(event);
    }

    // ==================== Fake HTTP infra ====================

    private static class FakeHttpClient implements IHttpClient {
        private IHttpResponse response;
        private List<String> streamEvents;
        String lastRequestUrl;
        Map<String, Object> lastRequestBody;

        void setResponse(IHttpResponse response) {
            this.response = response;
        }

        void setStreamEvents(List<String> events) {
            this.streamEvents = events;
        }

        @Override
        public CompletionStage<IHttpResponse> fetchAsync(HttpRequest request, ICancelToken cancelToken) {
            captureRequest(request);
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public Flow.Publisher<IServerEventResponse> fetchServerEventFlow(HttpRequest request, ICancelToken cancelToken) {
            captureRequest(request);
            List<String> events = this.streamEvents;
            // 异步发射事件，确保 callStream 返回后订阅链已建立再触发事件流
            return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
                private final java.util.concurrent.atomic.AtomicBoolean fired = new java.util.concurrent.atomic.AtomicBoolean();

                @Override
                public void request(long n) {
                    if (!fired.compareAndSet(false, true)) {
                        return;
                    }
                    CompletableFuture.runAsync(() -> {
                        try {
                            Thread.sleep(100);
                            if (events != null) {
                                for (String data : events) {
                                    subscriber.onNext(new FakeServerEvent(data));
                                }
                            }
                            subscriber.onComplete();
                        } catch (Throwable t) {
                            subscriber.onError(t);
                        }
                    });
                }

                @Override
                public void cancel() {
                }
            });
        }

        @SuppressWarnings("unchecked")
        private void captureRequest(HttpRequest request) {
            this.lastRequestUrl = request.getUrl();
            if (request.getBody() != null) {
                try {
                    Object parsed = JSON.parse(request.getBody().toString());
                    if (parsed instanceof Map) {
                        this.lastRequestBody = (Map<String, Object>) parsed;
                    }
                } catch (Exception e) {
                    // 非合法 JSON body，忽略解析（仅捕获请求用于断言）
                    this.lastRequestBody = null;
                }
            }
        }

        @Override
        public CompletionStage<IHttpResponse> downloadAsync(HttpRequest request, IHttpOutputFile targetFile,
                                                             DownloadOptions options, ICancelToken cancelToken) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletionStage<IHttpResponse> uploadAsync(HttpRequest request, IHttpInputFile inputFile,
                                                           UploadOptions options, ICancelToken cancelToken) {
            throw new UnsupportedOperationException();
        }
    }

    private static class FakeHttpResponse implements IHttpResponse {
        int status;
        String body;
        Map<String, String> headers;

        @Override
        public int getHttpStatus() {
            return status;
        }

        @Override
        public String getContentType() {
            return "application/json";
        }

        @Override
        public String getCharset() {
            return "UTF-8";
        }

        @Override
        public byte[] getBodyAsBytes() {
            return body != null ? body.getBytes() : new byte[0];
        }

        @Override
        public String getBodyAsString() {
            return body;
        }

        @Override
        public <T> T getBodyAsBean(Class<T> beanClass) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object getBody() {
            return body;
        }

        @Override
        public Map<String, String> getHeaders() {
            return headers;
        }
    }

    private static class FakeServerEvent implements IServerEventResponse {
        final String data;

        FakeServerEvent(String data) {
            this.data = data;
        }

        @Override
        public int getHttpStatus() {
            return 200;
        }

        @Override
        public String getId() {
            return null;
        }

        @Override
        public String getEvent() {
            return null;
        }

        @Override
        public String getData() {
            return data;
        }

        @Override
        public Map<String, String> getHeaders() {
            return new LinkedHashMap<>();
        }
    }
}
