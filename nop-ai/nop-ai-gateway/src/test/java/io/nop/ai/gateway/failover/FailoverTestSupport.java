package io.nop.ai.gateway.failover;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.ai.core.NopAiCoreErrors;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancelToken;
import io.nop.http.api.HttpApiErrors;
import io.nop.http.api.client.DownloadOptions;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpInputFile;
import io.nop.http.api.client.IHttpOutputFile;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.client.IServerEventResponse;
import io.nop.http.api.client.UploadOptions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * W6 本地适配器测试共享 harness（plan 2026-08-15-1116-2 Phase 4，审查 Mn-3 修复裁定）：
 * <b>真实 {@code ChatServiceImpl} + fake {@link IHttpClient}</b>（nop-ai-core 既有先例
 * {@code TestChatServiceImplErrorResponse} 的 CapturingHttpClient 模式）——"四字段下沉真实生效"
 * 在 HTTP 请求体/URL/header 层面断言；fake {@code fetchServerEventFlow} 注入受控失败/成功流。
 * 不用 mock IChatService 替代 delegate（会绕过下沉面验证）。
 */
final class FailoverTestSupport {

    private FailoverTestSupport() {
    }

    static ChatRequest request(String provider, String model, boolean stream) {
        ChatOptions options = ChatOptions.builder()
                .provider(provider)
                .model(model)
                .stream(stream)
                .maxTokens(50)
                .build();
        ChatRequest req = ChatRequest.userPrompt("hi");
        req.setOptions(options);
        return req;
    }

    /** OpenAI 流式 chunk 的 SSE data 行（parseStreamChunk 可解析）。 */
    static String streamChunkJson(String text) {
        return "{\"id\":\"chunk\",\"choices\":[{\"delta\":{\"content\":\"" + text + "\"}}]}";
    }

    static NopException streamError(int httpStatus, String body) {
        return new NopException(NopAiCoreErrors.ERR_AI_SERVICE_HTTP_ERROR)
                .param(NopAiCoreErrors.ARG_HTTP_STATUS, httpStatus)
                .param(HttpApiErrors.ARG_BODY, body);
    }

    static String quotaBody() {
        return "{\"error\":{\"code\":\"insufficient_quota\",\"type\":\"requests\",\"message\":\"quota exceeded\"}}";
    }

    static String authBody() {
        return "{\"error\":{\"code\":\"invalid_api_key\",\"type\":\"authentication_error\",\"message\":\"bad key\"}}";
    }

    static String rateLimitBody() {
        return "{\"error\":{\"code\":\"rate_limit_exceeded\",\"type\":\"requests\",\"message\":\"slow down\"}}";
    }

    static String nonTransientBody() {
        return "{\"error\":{\"code\":\"invalid_request_error\",\"type\":\"invalid_request_error\",\"message\":\"bad request\"}}";
    }

    /** W8 OBS-02：命中 gw-cache.llm.xml errorMappings → CACHE_STATE_LOST 分类（防御分支可达）。 */
    static String cacheLostBody() {
        return "{\"error\":{\"code\":\"cache_state_lost\",\"type\":\"api_error\",\"message\":\"cache state lost\"}}";
    }

    static String serverErrorBody() {
        return "{\"error\":{\"type\":\"server_error\",\"message\":\"unavailable\"}}";
    }

    static String successBody() {
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}],"
                + "\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}";
    }

    /**
     * 一个流式场景（每次 {@code fetchServerEventFlow} 调用消费一个）：
     * {@code events} 依次发射；{@code error} 非 null = 发射完 events 后 onError；
     * 否则 onComplete。{@code gate} 非 null = 发射前 await（取消/时序确定性测试用）。
     */
    static final class StreamScenario {
        final List<String> events = new ArrayList<>();
        Throwable error;
        CountDownLatch gate;

        StreamScenario events(String... lines) {
            for (String line : lines) {
                events.add(line);
            }
            return this;
        }

        StreamScenario error(Throwable t) {
            this.error = t;
            return this;
        }

        StreamScenario gate(CountDownLatch latch) {
            this.gate = latch;
            return this;
        }

        static StreamScenario success(String... lines) {
            return new StreamScenario().events(lines);
        }

        static StreamScenario failing(Throwable t) {
            return new StreamScenario().error(t);
        }
    }

    /**
     * fake {@link IHttpClient}：记录全部请求（下沉面断言）；非流式按队列返回
     * {@link IHttpResponse} 或异常；流式按队列消费 {@link StreamScenario}。
     * 每次流式调用会收到 per-attempt {@code ICancelToken}——注册记录器，token 取消时
     * 记录（M-1 per-attempt token 可观测面）。
     */
    static final class FakeHttpClient implements IHttpClient {
        final List<HttpRequest> requests = new ArrayList<>();
        final List<String> attemptTokenCancels = new ArrayList<>();
        final java.util.ArrayDeque<Object> responses = new java.util.ArrayDeque<>();
        final java.util.ArrayDeque<StreamScenario> streamScenarios = new java.util.ArrayDeque<>();
        final List<FakeStreamSubscription> streamSubscriptions = new ArrayList<>();

        FakeHttpClient queueResponse(IHttpResponse resp) {
            responses.add(resp);
            return this;
        }

        FakeHttpClient queueFailure(Throwable t) {
            responses.add(t);
            return this;
        }

        FakeHttpClient queueStream(StreamScenario scenario) {
            streamScenarios.add(scenario);
            return this;
        }

        HttpRequest lastRequest() {
            return requests.isEmpty() ? null : requests.get(requests.size() - 1);
        }

        int streamCallCount() {
            return streamSubscriptions.size();
        }

        @Override
        public CompletionStage<IHttpResponse> fetchAsync(HttpRequest request, ICancelToken cancelToken) {
            requests.add(request);
            Object next = responses.poll();
            if (next instanceof Throwable) {
                CompletableFuture<IHttpResponse> f = new CompletableFuture<>();
                f.completeExceptionally((Throwable) next);
                return f;
            }
            return CompletableFuture.completedFuture((IHttpResponse) next);
        }

        @Override
        public Flow.Publisher<IServerEventResponse> fetchServerEventFlow(HttpRequest request, ICancelToken cancelToken) {
            requests.add(request);
            if (cancelToken != null) {
                cancelToken.appendOnCancelTask(() -> attemptTokenCancels.add(request.getUrl()));
            }
            StreamScenario scenario = streamScenarios.poll();
            StreamScenario sc = scenario != null ? scenario : StreamScenario.success();
            return subscriber -> subscriber.onSubscribe(new FakeStreamSubscription(subscriber, sc));
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

        final class FakeStreamSubscription implements Flow.Subscription {
            private final Flow.Subscriber<? super IServerEventResponse> subscriber;
            private final StreamScenario scenario;
            private final AtomicBoolean started = new AtomicBoolean();
            private final AtomicBoolean cancelled = new AtomicBoolean();

            FakeStreamSubscription(Flow.Subscriber<? super IServerEventResponse> subscriber,
                                   StreamScenario scenario) {
                this.subscriber = subscriber;
                this.scenario = scenario;
                streamSubscriptions.add(this);
            }

            @Override
            public void request(long n) {
                if (!started.compareAndSet(false, true)) {
                    return;
                }
                CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(20);
                        if (scenario.gate != null) {
                            scenario.gate.await(10, TimeUnit.SECONDS);
                        }
                        if (cancelled.get()) {
                            return;
                        }
                        for (String data : scenario.events) {
                            if (cancelled.get()) {
                                return;
                            }
                            subscriber.onNext(new FakeServerEvent(data));
                        }
                        if (cancelled.get()) {
                            return;
                        }
                        if (scenario.error != null) {
                            subscriber.onError(scenario.error);
                        } else {
                            subscriber.onComplete();
                        }
                    } catch (Throwable t) {
                        if (!cancelled.get()) {
                            subscriber.onError(t);
                        }
                    }
                });
            }

            @Override
            public void cancel() {
                cancelled.set(true);
            }

            boolean isCancelled() {
                return cancelled.get();
            }
        }
    }

    static final class FakeHttpResponse implements IHttpResponse {
        int status;
        String body;

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
            return new LinkedHashMap<>();
        }
    }

    static final class FakeServerEvent implements IServerEventResponse {
        private final String data;

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
            return "message";
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

    /**
     * 收集型订阅者：记录 onNext 载荷 / onError / onComplete，{@link #await()} 阻塞至终态。
     */
    static class CollectingSubscriber implements Flow.Subscriber<ChatStreamChunk> {
        final List<String> texts = new ArrayList<>();
        Throwable error;
        boolean completed;
        private final CompletableFuture<Void> done = new CompletableFuture<>();

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ChatStreamChunk item) {
            texts.add(item.getDelta());
        }

        @Override
        public void onError(Throwable throwable) {
            this.error = throwable;
            done.complete(null);
        }

        @Override
        public void onComplete() {
            this.completed = true;
            done.complete(null);
        }

        void await() {
            try {
                done.get(15, TimeUnit.SECONDS);
            } catch (Exception e) {
                fail("stream did not terminate: " + e);
            }
        }

        void awaitAndAssertSuccess(String... expectedTexts) {
            await();
            assertNull(error, "unexpected stream error: " + error);
            assertTrue(completed, "stream must complete");
            assertTrue(texts.size() == expectedTexts.length,
                    "expected " + expectedTexts.length + " chunks, got " + texts);
            for (int i = 0; i < expectedTexts.length; i++) {
                if (!Objects.equals(expectedTexts[i], texts.get(i))) {
                    fail("chunk " + i + " mismatch: expected " + expectedTexts[i] + ", got " + texts.get(i));
                }
            }
        }
    }
}
