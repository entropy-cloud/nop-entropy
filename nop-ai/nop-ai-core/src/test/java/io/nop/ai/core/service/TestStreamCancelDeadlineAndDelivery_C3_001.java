package io.nop.ai.core.service;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.stream.ChatStreamChunk;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * CRA 3.1-C3-001（MA-01 流式主链族五修复点）+ 3.1-C7-001（全程期限/inactivity）+
 * 3.1-C7-002（R11 错误以成功交付族纪律）——plan 2505 Phase 2 回归面。
 *
 * <p>红形态①（取消悬挂红）：cancel 后下游 Flow 订阅在有界观察窗内收不到任何终态信号
 * 且 publisher 未关闭；红形态③（期限红）：挂死传输下聚合 future 在配置期限内不完成且无
 * 失败可见性；红形态④（R11 红）：半关闭流以 future.complete 成功交付。
 *
 * <p>接线验证（rule 23）：真实 {@code ChatServiceImpl.callStream → aggregateStreamToResponse}
 * 链 + fake 挂死传输（gate/静默形态）上断言取消闭合/期限/inactivity 运行时生效。
 */
public class TestStreamCancelDeadlineAndDelivery_C3_001 extends JunitBaseTestCase {

    private static final String CFG_LOG_MESSAGE = "nop.ai.service.log-message";
    private static final String CFG_STREAM_TOTAL_TIMEOUT = "nop.ai.service.stream-total-timeout";
    private static final String CFG_STREAM_IDLE_TIMEOUT = "nop.ai.service.stream-idle-timeout";
    private static final String CFG_STREAM_MAX_CHARS = "nop.ai.service.stream-max-accumulated-chars";
    private static final String CFG_STREAM_MAX_TOOL_CALLS = "nop.ai.service.stream-max-tool-calls";

    private ChatServiceImpl impl;
    private FakeStreamHttpClient fake;

    @BeforeEach
    void setUp() {
        fake = new FakeStreamHttpClient();
        impl = new ChatServiceImpl();
        impl.setHttpClient(fake);
        impl.setChatLogger(new DefaultChatLogger());
        AppConfig.getConfigProvider().assignConfigValue("nop.ai.service.default-llm", "test-sat");
        AppConfig.getConfigProvider().assignConfigValue("nop.ai.llm.test-sat.base-url", "https://api.example.com");
        AppConfig.getConfigProvider().assignConfigValue("nop.ai.llm.test-sat.api-key", "sk-test");
    }

    @AfterEach
    void tearDown() {
        // 显式恢复生产缺省值（assign(null) 不能复原数值型缺省，会向同 JVM 后续用例泄漏）
        AppConfig.getConfigProvider().assignConfigValue("nop.ai.service.default-llm", null);
        AppConfig.getConfigProvider().assignConfigValue("nop.ai.llm.test-sat.base-url", null);
        AppConfig.getConfigProvider().assignConfigValue("nop.ai.llm.test-sat.api-key", null);
        AppConfig.getConfigProvider().assignConfigValue(CFG_LOG_MESSAGE, false);
        AppConfig.getConfigProvider().assignConfigValue(CFG_STREAM_TOTAL_TIMEOUT, 600_000L);
        AppConfig.getConfigProvider().assignConfigValue(CFG_STREAM_IDLE_TIMEOUT, 120_000L);
        AppConfig.getConfigProvider().assignConfigValue(CFG_STREAM_MAX_CHARS, 2_000_000);
        AppConfig.getConfigProvider().assignConfigValue(CFG_STREAM_MAX_TOOL_CALLS, 100);
        LlmConfigHelper.reset();
        fake.shutdown();
    }

    // ------------------------------------------------------------ harness

    static final class SettableCancelToken implements ICancelToken {
        private final List<Consumer<String>> tasks = new CopyOnWriteArrayList<>();
        private volatile boolean cancelled;
        private volatile String reason;

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public String getCancelReason() {
            return reason;
        }

        @Override
        public void appendOnCancel(Consumer<String> task) {
            tasks.add(task);
        }

        @Override
        public void removeOnCancel(Consumer<String> task) {
            tasks.remove(task);
        }

        void cancel(String reason) {
            this.reason = reason;
            this.cancelled = true;
            for (Consumer<String> task : tasks) {
                task.accept(reason);
            }
        }
    }

    static ChatRequest streamRequest() {
        ChatRequest request = ChatRequest.userPrompt("hi");
        request.setOptions(ChatOptions.builder().provider("test-sat").stream(true).build());
        return request;
    }

    /**
     * 脚本化流式 fake：request() 时在独立线程按脚本发射事件。hangAfterEvents=true 时
     * 事件发完即永久停滞（无终态——挂死传输形态，C7-001/取消红形态的构造前提）。
     */
    static final class FakeStreamSubscription implements Flow.Subscription {
        private final Flow.Subscriber<? super IServerEventResponse> subscriber;
        private final List<String> events;
        private final boolean hangAfterEvents;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        final List<Long> requestedN = new CopyOnWriteArrayList<>();
        private final AtomicBoolean started = new AtomicBoolean();

        FakeStreamSubscription(Flow.Subscriber<? super IServerEventResponse> subscriber,
                               List<String> events, boolean hangAfterEvents) {
            this.subscriber = subscriber;
            this.events = events;
            this.hangAfterEvents = hangAfterEvents;
        }

        @Override
        public void request(long n) {
            requestedN.add(n);
            if (!started.compareAndSet(false, true))
                return;
            Thread emitter = new Thread(this::emit, "fake-sse-emitter");
            emitter.setDaemon(true);
            emitter.start();
        }

        private void emit() {
            try {
                // 启动延迟：callStream 内部桥接订阅即触发发射，给测试侧下游订阅留出注册窗口
                // （FailoverTestSupport 先例同型；无此延迟首 chunk 会在下游订阅前被丢弃）
                Thread.sleep(50);
                for (String data : events) {
                    if (cancelled.get())
                        return;
                    subscriber.onNext(new FakeServerEvent(data));
                }
                if (!hangAfterEvents && !cancelled.get())
                    subscriber.onComplete();
                // hangAfterEvents：既不 onComplete 也不 onError（挂死形态）
            } catch (Throwable t) {
                if (!cancelled.get())
                    subscriber.onError(t);
            }
        }

        @Override
        public void cancel() {
            cancelled.set(true);
        }
    }

    record FakeServerEvent(String data) implements IServerEventResponse {
        @Override
        public int getHttpStatus() {
            return 200;
        }

        @Override
        public Map<String, String> getHeaders() {
            return java.util.Collections.emptyMap();
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
    }

    static final class FakeStreamHttpClient implements IHttpClient {
        final List<FakeStreamSubscription> subscriptions = new CopyOnWriteArrayList<>();
        private volatile List<String> nextEvents = new ArrayList<>();
        private volatile boolean hangAfterEvents = true;

        void script(List<String> events, boolean hangAfterEvents) {
            this.nextEvents = events;
            this.hangAfterEvents = hangAfterEvents;
        }

        void shutdown() {
            for (FakeStreamSubscription sub : subscriptions)
                sub.cancel();
        }

        @Override
        public Flow.Publisher<IServerEventResponse> fetchServerEventFlow(HttpRequest request, ICancelToken cancelToken) {
            return subscriber -> {
                FakeStreamSubscription sub = new FakeStreamSubscription(
                        subscriber, nextEvents, hangAfterEvents);
                subscriptions.add(sub);
                subscriber.onSubscribe(sub);
            };
        }

        @Override
        public CompletionStage<IHttpResponse> fetchAsync(HttpRequest request, ICancelToken cancelToken) {
            throw new UnsupportedOperationException();
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

    static String textChunk(String content) {
        return "{\"id\":\"chunk\",\"choices\":[{\"delta\":{\"content\":\"" + content + "\"}}]}";
    }

    static String finishChunk(String reason) {
        return "{\"id\":\"chunk\",\"choices\":[{\"delta\":{},\"finish_reason\":\"" + reason + "\"}]}";
    }

    static String toolAddChunk(int index, String callId, String name) {
        return "{\"id\":\"chunk\",\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":" + index
                + ",\"id\":\"" + callId + "\",\"type\":\"function\",\"function\":{\"name\":\"" + name
                + "\",\"arguments\":\"\"}}]}}]}";
    }

    /** 等待 future 终态；返回异常终态（正常完成返回 null，观察窗内无终态返回悬挂标记）。 */
    private static Throwable awaitTerminal(CompletableFuture<ChatResponse> future, long timeoutMillis)
            throws InterruptedException {
        try {
            future.get(timeoutMillis, TimeUnit.MILLISECONDS);
            return null;
        } catch (java.util.concurrent.ExecutionException e) {
            return e.getCause();
        } catch (java.util.concurrent.CancellationException e) {
            return e;
        } catch (java.util.concurrent.TimeoutException e) {
            return HANG_MARKER;
        }
    }

    private static final Throwable HANG_MARKER = new Throwable("hang");

    // ------------------------------------------------- 取消闭合（C3-001 ①）

    @Test
    void cancelClosesDownstreamSubscriber_C3_001() throws Exception {
        fake.script(List.of(textChunk("one"), textChunk("two")), true); // 发完即挂死

        SettableCancelToken token = new SettableCancelToken();
        CountDownLatch firstChunk = new CountDownLatch(1);
        CountDownLatch terminal = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        impl.callStream(streamRequest(), token).subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ChatStreamChunk item) {
                firstChunk.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                errorRef.set(throwable);
                terminal.countDown();
            }

            @Override
            public void onComplete() {
                terminal.countDown();
            }
        });

        assertTrue(firstChunk.await(5_000, TimeUnit.MILLISECONDS), "first chunk must arrive");
        // 直接订阅者场景的取消入口 = cancelToken（FailoverStreamFlow 形态）：
        // 取消必须闭合下游 publisher（C3-001 ①/C4-002），下游有界窗内收到可区分的取消终态；
        // 修复前 cancel 只向上游传播，下游永久悬挂（红形态①）
        token.cancel("test-cancel");

        assertTrue(terminal.await(3_000, TimeUnit.MILLISECONDS),
                "cancel must close the downstream publisher: subscriber must receive a terminal "
                        + "within the bounded window");
        assertNotNull(errorRef.get(), "cancel terminal must be distinguishable (onError/CancellationException)");
        assertTrue(errorRef.get() instanceof CancellationException,
                "cancel must surface as CancellationException, got: " + errorRef.get());
    }

    @Test
    void cancelTerminatesAggregatedFuture_C3_001() throws Exception {
        fake.script(List.of(textChunk("one")), true); // 首块后挂死

        SettableCancelToken token = new SettableCancelToken();
        CompletableFuture<ChatResponse> future = impl.aggregateStreamToResponse(streamRequest(), token)
                .toCompletableFuture();
        long deadline = System.currentTimeMillis() + 5_000;
        while (fake.subscriptions.isEmpty() && System.currentTimeMillis() < deadline)
            Thread.sleep(20);
        assertFalse(fake.subscriptions.isEmpty(), "stream must have started");

        token.cancel("test-cancel");

        Throwable terminal = awaitTerminal(future, 3_000);
        assertNotNull(terminal, "cancelled stream must terminate the aggregated future within the bounded "
                + "window (3.1-C3-001 hang signature: no terminal at all)");
        assertTrue(terminal != HANG_MARKER, "aggregated future must not hang after cancel");
    }

    // ------------------------------------------------- 背压窗口（C3-001 ②）

    @Test
    void upstreamRequestWindowIsBounded_C3_001() throws Exception {
        fake.script(List.of(textChunk("one")), false);

        CountDownLatch done = new CountDownLatch(1);
        impl.callStream(streamRequest(), null).subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ChatStreamChunk item) {
            }

            @Override
            public void onError(Throwable throwable) {
                done.countDown();
            }

            @Override
            public void onComplete() {
                done.countDown();
            }
        });
        assertTrue(done.await(5_000, TimeUnit.MILLISECONDS));

        // 桥接订阅对上游的 request 必须有界（修复前 request(Long.MAX_VALUE) 全速拉取）
        assertFalse(fake.subscriptions.isEmpty());
        List<Long> requests = fake.subscriptions.get(0).requestedN;
        assertFalse(requests.isEmpty(), "upstream subscription must have received request(n)");
        for (Long n : requests) {
            assertTrue(n > 0 && n < 1_000_000L,
                    "bridge request window must be bounded (3.1-C3-002 backpressure), got: " + n);
        }
    }

    @Test
    void slowDownstreamFailsFastInsteadOfBlocking_C3_001() throws Exception {
        // 大量 chunk + 从不 request 的下游：缓冲满载必须 fail-loud 终态化，
        // 不得 uninterruptibly 阻塞发射线程（3.1-C3-002 submit 阻塞面）
        List<String> events = new ArrayList<>();
        for (int i = 0; i < 400; i++)
            events.add(textChunk("chunk-" + i));
        fake.script(events, false);

        Flow.Publisher<ChatStreamChunk> publisher = impl.callStream(streamRequest(), null);
        CountDownLatch terminal = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                // 从不 request：下游停滞形态
            }

            @Override
            public void onNext(ChatStreamChunk item) {
            }

            @Override
            public void onError(Throwable throwable) {
                errorRef.set(throwable);
                terminal.countDown();
            }

            @Override
            public void onComplete() {
                terminal.countDown();
            }
        });

        assertTrue(terminal.await(10_000, TimeUnit.MILLISECONDS),
                "stalled downstream must reach an explicit terminal instead of blocking the reader thread");
        assertNotNull(errorRef.get(), "buffer-overflow stall must surface as onError (fail-loud, no silent drop)");
    }

    // ------------------------------------------------- 受管承载（C3-001 ③）

    @Test
    void streamCarrierIsManagedPoolNotCommonPool_C3_001() throws Exception {
        fake.script(List.of(textChunk("one")), false);

        CountDownLatch firstChunk = new CountDownLatch(1);
        AtomicReference<String> carrierThread = new AtomicReference<>();
        impl.callStream(streamRequest(), null).subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ChatStreamChunk item) {
                if (carrierThread.compareAndSet(null, Thread.currentThread().getName()))
                    firstChunk.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                firstChunk.countDown();
            }

            @Override
            public void onComplete() {
                firstChunk.countDown();
            }
        });

        assertTrue(firstChunk.await(5_000, TimeUnit.MILLISECONDS));
        String threadName = carrierThread.get();
        assertNotNull(threadName, "chunk delivery must run on a carrier thread");
        assertFalse(threadName.startsWith("ForkJoinPool"),
                "stream carrier must not be ForkJoinPool.commonPool (3.1-C3-009/C4-001), got: " + threadName);
        assertTrue(threadName.startsWith("nop-"),
                "stream carrier must be a platform-managed pool thread, got: " + threadName);
    }

    // ------------------------------------------------- 盘写异步化（C3-001 ④）

    @Test
    void logFailureDoesNotHangFuture_C3_001() throws Exception {
        AppConfig.getConfigProvider().assignConfigValue(CFG_LOG_MESSAGE, true);
        AppConfig.getConfigProvider().assignConfigValue("nop.ai.llm.test-sat.log-message", "true");

        impl.setChatLogger(new DefaultChatLogger() {
            @Override
            public void logResponse(ChatRequest request, ChatResponse response) {
                throw new IllegalStateException("simulated disk full");
            }
        });

        fake.script(List.of(textChunk("ok"), finishChunk("stop")), false);
        CompletableFuture<ChatResponse> future = impl.aggregateStreamToResponse(streamRequest(), null)
                .toCompletableFuture();

        Throwable terminal = awaitTerminal(future, 5_000);
        assertTrue(terminal != HANG_MARKER,
                "log write failure must not leave the future hanging (3.1-C3-003 suspension face)");
        assertNotNull(future.join(), "successful stream must still deliver its response");
    }

    // ------------------------------------------------- 聚合上界（C3-001 ⑤）

    @Test
    void accumulatedCharsBoundFailsFast_C3_001() throws Exception {
        AppConfig.getConfigProvider().assignConfigValue(CFG_STREAM_MAX_CHARS, 100);

        List<String> events = new ArrayList<>();
        for (int i = 0; i < 10; i++)
            events.add(textChunk("0123456789012345678901234567890123456789")); // 40 chars × 10
        fake.script(events, false);

        CompletableFuture<ChatResponse> future = impl.aggregateStreamToResponse(streamRequest(), null)
                .toCompletableFuture();
        Throwable terminal = awaitTerminal(future, 5_000);
        assertTrue(terminal != HANG_MARKER, "bound must terminate the stream explicitly");
        assertNotNull(terminal, "accumulation beyond the configured bound must fail fast (3.1-C5-001)");
        assertTrue(terminal instanceof NopException, "bound must fail with a typed error, got: " + terminal);
    }

    @Test
    void toolCallEntryBoundFailsFast_C3_001() throws Exception {
        AppConfig.getConfigProvider().assignConfigValue(CFG_STREAM_MAX_TOOL_CALLS, 2);

        List<String> events = new ArrayList<>();
        for (int i = 0; i < 5; i++)
            events.add(toolAddChunk(i, "call-" + i, "fn" + i));
        fake.script(events, false);

        CompletableFuture<ChatResponse> future = impl.aggregateStreamToResponse(streamRequest(), null)
                .toCompletableFuture();
        Throwable terminal = awaitTerminal(future, 5_000);
        assertTrue(terminal != HANG_MARKER, "bound must terminate the stream explicitly");
        assertNotNull(terminal, "tool-call entries beyond the configured bound must fail fast (3.1-C5-001)");
        assertTrue(terminal instanceof NopException, "bound must fail with a typed error, got: " + terminal);
    }

    // ------------------------------------------------- 全程期限（C7-001）

    @Test
    void totalDeadlineTerminatesHungStream_C7_001() throws Exception {
        AppConfig.getConfigProvider().assignConfigValue(CFG_STREAM_TOTAL_TIMEOUT, 800L);
        fake.script(List.of(textChunk("one")), true); // 首块后永久挂死

        CompletableFuture<ChatResponse> future = impl.aggregateStreamToResponse(streamRequest(), null)
                .toCompletableFuture();
        Throwable terminal = awaitTerminal(future, 3_000);
        assertNotNull(terminal, "hung stream must terminate within the total deadline (3.1-C7-001) "
                + "with visible failure (no silent hang)");
        assertTrue(terminal != HANG_MARKER, "aggregated future must not outlive the configured deadline");
    }

    @Test
    void idleTimeoutTerminatesSilentStream_C7_001() throws Exception {
        AppConfig.getConfigProvider().assignConfigValue(CFG_STREAM_IDLE_TIMEOUT, 500L);
        fake.script(List.of(textChunk("one")), true); // 首块后静默（无终态）

        CompletableFuture<ChatResponse> future = impl.aggregateStreamToResponse(streamRequest(), null)
                .toCompletableFuture();
        Throwable terminal = awaitTerminal(future, 3_000);
        assertNotNull(terminal, "silent stream must terminate within the inactivity budget (3.1-C7-001)");
        assertTrue(terminal != HANG_MARKER, "inactivity must surface as explicit failure");
    }

    // ------------------------------------------------- R11 纪律（C7-002）

    @Test
    void halfCloseWithoutTerminationSignalFailsLoud_C7_002() throws Exception {
        // 半关闭形态：有内容 chunk、无 finish_reason/[DONE]、连接干净关闭
        fake.script(List.of(textChunk("partial content")), false);

        CompletableFuture<ChatResponse> future = impl.aggregateStreamToResponse(streamRequest(), null)
                .toCompletableFuture();
        Throwable terminal = awaitTerminal(future, 5_000);
        assertTrue(terminal != HANG_MARKER, "half-close must reach a terminal");
        assertNotNull(terminal, "truncated stream (no termination signal) must NOT be delivered as success "
                + "(3.1-C7-002 R11 discipline)");
        assertTrue(terminal instanceof NopException, "truncation must fail with a typed error, got: " + terminal);
    }

    @Test
    void emptyStreamStillCompletesSuccessfully_C7_002() throws Exception {
        fake.script(List.of(), false); // 零 chunk 干净关闭：保持既有空流成功契约
        CompletableFuture<ChatResponse> future = impl.aggregateStreamToResponse(streamRequest(), null)
                .toCompletableFuture();
        Throwable terminal = awaitTerminal(future, 5_000);
        assertNull(terminal, "zero-chunk clean close keeps the existing empty-success contract");
        assertTrue(future.join().isSuccess());
    }

    @Test
    void terminatedStreamStillCompletesSuccessfully_C7_002() throws Exception {
        fake.script(List.of(textChunk("hello"), finishChunk("stop")), false);
        CompletableFuture<ChatResponse> future = impl.aggregateStreamToResponse(streamRequest(), null)
                .toCompletableFuture();
        Throwable terminal = awaitTerminal(future, 5_000);
        assertNull(terminal, "terminated stream keeps the success contract (zero regression)");
        ChatResponse response = future.join();
        assertEquals("stop", response.getFinishReason());
        assertEquals("hello", response.outputText());
    }
}
