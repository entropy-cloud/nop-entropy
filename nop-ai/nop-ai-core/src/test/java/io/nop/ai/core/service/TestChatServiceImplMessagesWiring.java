package io.nop.ai.core.service;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatReasoningMessage;
import io.nop.api.core.util.ICancelToken;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.http.api.client.DownloadOptions;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpInputFile;
import io.nop.http.api.client.IHttpOutputFile;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.client.UploadOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 326 Phase 2 接线验证（Rule #23）：{@code ChatServiceImpl.call} 非流式路径
 * （{@code dialect.parseResponse} @ ChatServiceImpl）端到端拿到的 {@link ChatResponse#getMessages()}
 * 非空——证明 dialect 双轨产出的 messages 序列经服务层透传到调用方，而非仅存在于 dialect 单测。
 */
public class TestChatServiceImplMessagesWiring extends JunitBaseTestCase {

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

    @Test
    void nonStreamResponseCarriesMessagesSequence() {
        // OpenAI 风格响应：带 reasoning_content + assistant 文本。
        httpClient.setResponse(fakeResponse(200,
                "{\"id\":\"chatcmpl-1\",\"model\":\"gpt-4\"," +
                        "\"choices\":[{\"message\":{\"content\":\"answer\",\"reasoning_content\":\"think\"}," +
                        "\"finish_reason\":\"stop\"}]}"));

        ChatOptions options = ChatOptions.builder()
                .provider("default")
                .model("gpt-4")
                .stream(false)
                .accountKey("sk-test")
                .accountBaseUrl("https://api.example.com")
                .maxTokens(50)
                .build();
        ChatRequest req = ChatRequest.userPrompt("hi");
        req.setOptions(options);

        ChatResponse response = chatService.call(req, null);

        assertTrue(response.isSuccess(), "200 response must be a success ChatResponse");
        assertNotNull(response.getMessages(),
                "non-stream path must surface dialect-produced messages (wiring Rule #23)");
        assertFalse(response.getMessages().isEmpty(), "messages must not be empty");
        assertEquals(2, response.getMessages().size(), "reasoning -> assistant text");

        assertTrue(response.getMessages().get(0) instanceof ChatReasoningMessage,
                "first message is reasoning");
        assertEquals("think", response.getMessages().get(0).getContent());
        assertTrue(response.getMessages().get(1) instanceof ChatAssistantMessage,
                "second message is assistant text");
        assertEquals("answer", response.getMessages().get(1).getContent());
    }

    private static FakeHttpResponse fakeResponse(int status, String body) {
        FakeHttpResponse r = new FakeHttpResponse();
        r.status = status;
        r.body = body;
        r.headers = new LinkedHashMap<>();
        return r;
    }

    private static class FakeHttpClient implements IHttpClient {
        private IHttpResponse response;

        void setResponse(IHttpResponse response) {
            this.response = response;
        }

        @Override
        public CompletionStage<IHttpResponse> fetchAsync(HttpRequest request, ICancelToken cancelToken) {
            return CompletableFuture.completedFuture(response);
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
}
