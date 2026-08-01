package io.nop.ai.core.service;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.ErrorClassification;
import io.nop.ai.api.chat.IChatService;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan W2e-2 (Phase 2) end-to-end test for the {@link ChatServiceImpl} non-stream
 * error path: a non-200 provider response must be normalized via
 * {@code dialect.parseErrorResponse(...)} into a {@link ChatResponse} carrying
 * {@code errorClassification} — and <b>must not throw</b> (this is the documented
 * {@code IChatService.call} contract change: response-level errors now travel via
 * ChatResponse instead of an exception).
 */
public class TestChatServiceImplErrorResponse extends JunitBaseTestCase {

    private CapturingHttpClient httpClient;
    private IChatService chatService;

    @BeforeEach
    void setUp() {
        ChatServiceImpl impl = new ChatServiceImpl();
        httpClient = new CapturingHttpClient();
        impl.setHttpClient(httpClient);
        impl.setChatLogger(new DefaultChatLogger());
        chatService = impl;
    }

    @AfterEach
    void tearDown() {
        LlmConfigHelper.reset();
    }

    @Test
    void nonStream429InsufficientQuotaReturnsErrorChatResponse() {
        httpClient.queueResponse(fakeResponse(429,
                "{\"error\":{\"code\":\"insufficient_quota\","
                        + "\"type\":\"requests\",\"message\":\"quota exceeded\"}}",
                hdr("Retry-After", "5")));

        ChatResponse resp = chatService.call(request(false), null);

        assertFalse(resp.isSuccess(),
                "non-200 must return a non-success ChatResponse, not throw");
        assertEquals(ErrorClassification.QUOTA_EXCEEDED, resp.getErrorClassification(),
                "429 insufficient_quota → QUOTA_EXCEEDED (first-match-wins over generic 429)");
        assertEquals(429, resp.getHttpStatus());
        assertNotNull(resp.getRetryAfterMs(), "Retry-After header must be normalized");
        assertEquals(5000L, resp.getRetryAfterMs());
    }

    @Test
    void nonStream429RateLimitReturnsRateLimitedClassification() {
        httpClient.queueResponse(fakeResponse(429,
                "{\"error\":{\"code\":\"rate_limit_exceeded\","
                        + "\"type\":\"requests\",\"message\":\"slow down\"}}", null));

        ChatResponse resp = chatService.call(request(false), null);
        assertFalse(resp.isSuccess());
        assertEquals(ErrorClassification.RATE_LIMITED, resp.getErrorClassification());
    }

    @Test
    void nonStreamServerErrorReturnsTransientClassification() {
        httpClient.queueResponse(fakeResponse(503,
                "{\"error\":{\"type\":\"server_error\",\"message\":\"unavailable\"}}", null));

        ChatResponse resp = chatService.call(request(false), null);
        assertFalse(resp.isSuccess());
        assertEquals(ErrorClassification.TRANSIENT, resp.getErrorClassification());
        assertEquals(503, resp.getHttpStatus());
    }

    @Test
    void transportLevelErrorStillPropagatesAsException() {
        // 传输级错误（无 HTTP 响应）仍抛异常——经 LlmErrorClassifier 启发式处理。
        httpClient.queueFailure(new java.net.ConnectException("connection refused"));

        assertThrows(RuntimeException.class, () -> chatService.call(request(false), null),
                "transport-level error (no HTTP response) must still throw, not return ChatResponse");
    }

    private static ChatRequest request(boolean stream) {
        ChatOptions options = ChatOptions.builder()
                .provider("deepseek")
                .model("deepseek-chat")
                .stream(stream)
                .maxTokens(50)
                .build();
        ChatRequest req = ChatRequest.userPrompt("hi");
        req.setOptions(options);
        return req;
    }

    private static Map<String, String> hdr(String name, String value) {
        Map<String, String> m = new LinkedHashMap<>();
        if (name != null) {
            m.put(name, value);
        }
        return m;
    }

    private static FakeHttpResponse fakeResponse(int status, String body, Map<String, String> headers) {
        FakeHttpResponse r = new FakeHttpResponse();
        r.status = status;
        r.body = body;
        r.headers = headers != null ? headers : new LinkedHashMap<>();
        return r;
    }

    /**
     * Minimal {@link IHttpClient} that returns queued {@link FakeHttpResponse}s
     * (or queued failures) from {@link #fetchAsync}, throwing UOE for the unused
     * download/upload entry points.
     */
    private static class CapturingHttpClient implements IHttpClient {
        private final java.util.Queue<Object> queue = new java.util.ArrayDeque<>();

        void queueResponse(IHttpResponse resp) {
            queue.add(resp);
        }

        void queueFailure(Throwable t) {
            queue.add(t);
        }

        @Override
        public CompletionStage<IHttpResponse> fetchAsync(HttpRequest request, ICancelToken cancelToken) {
            Object next = queue.poll();
            if (next instanceof Throwable) {
                CompletableFuture<IHttpResponse> f = new CompletableFuture<>();
                f.completeExceptionally((Throwable) next);
                return f;
            }
            return CompletableFuture.completedFuture((IHttpResponse) next);
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
