package io.nop.integration.feishu.client;

import io.nop.integration.feishu.NopFeishuException;
import io.nop.integration.feishu.codec.FeishuFrameType;
import io.nop.integration.feishu.codec.FeishuPbCodec;
import io.nop.integration.feishu.codec.FeishuStreamFrame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lifecycle + message-delivery tests for {@link FeishuClient}. Uses a fake
 * transport (drives the {@link IStreamListener} synchronously) and a fake HTTP
 * API (records call counts) so the tests verify real state transitions and
 * token caching without a live Feishu server (real connectivity = W6 E2E).
 */
class TestFeishuClient {

    private FakeStreamTransport transport;
    private FakeFeishuHttpApi httpApi;
    private NoopScheduler scheduler;
    private FeishuClient client;
    private RecordingHandler handler;

    @BeforeEach
    void setUp() {
        transport = new FakeStreamTransport();
        httpApi = new FakeFeishuHttpApi();
        scheduler = new NoopScheduler();
        client = new FeishuClient(transport, httpApi, scheduler);
        handler = new RecordingHandler();
    }

    @AfterEach
    void tearDown() {
        client.stop();
    }

    private FeishuCredentials creds() {
        FeishuCredentials c = new FeishuCredentials();
        c.setAppId("cli_app");
        c.setAppSecret("secret");
        return c;
    }

    @Test
    void startTransitionsToConnected() {
        assertEquals(0, transport.connectCount);
        client.start(creds(), handler);

        assertTrue(client.isConnected());
        assertEquals(1, transport.connectCount, "start must establish exactly one connection");
        // onOpen sends a CONTROL connect handshake frame
        assertEquals(1, transport.sentFrames.size());
        FeishuStreamFrame handshake = FeishuPbCodec.decode(transport.sentFrames.get(0));
        assertEquals(FeishuFrameType.CONTROL.getMethod(), handshake.getMethod());
        assertEquals("connect", handshake.getHeaders().get("type"));
    }

    @Test
    void stopTransitionsToDisconnected() {
        client.start(creds(), handler);
        assertTrue(client.isConnected());

        client.stop();
        assertFalse(client.isConnected());
        assertEquals(1, transport.closeCount, "stop must close the transport");
    }

    @Test
    void inboundMessageDeliveredToHandler() {
        client.start(creds(), handler);

        // build a DATA frame carrying a representative event payload
        String payload = "{\"receive_id_type\":\"chat_id\","
                + "\"receive_id\":\"oc_123\","
                + "\"msg_type\":\"text\","
                + "\"content\":\"{\\\"text\\\":\\\"hello\\\"}\","
                + "\"sender_id\":\"ou_sender\","
                + "\"chat_type\":\"p2p\"}";
        byte[] frameBytes = FeishuPbCodec.encode(new FeishuStreamFrame(
                FeishuFrameType.DATA.getMethod(), null,
                payload.getBytes(StandardCharsets.UTF_8), null));

        transport.deliver(frameBytes);

        assertEquals(1, handler.messages.size(), "DATA frame must be delivered to handler");
        FeishuInboundMessage msg = handler.messages.get(0);
        assertEquals("chat_id", msg.getReceiveIdType());
        assertEquals("oc_123", msg.getReceiveId());
        assertEquals("text", msg.getMsgType());
        assertEquals("ou_sender", msg.getSenderId());
        assertEquals("p2p", msg.getChatType());
        assertNotNull(msg.getRawPayload());
    }

    @Test
    void inboundMalformedFrameReportsErrorNotSilent() {
        client.start(creds(), handler);

        transport.deliver(new byte[]{0x12, 0x10, 0x0A}); // truncated

        assertEquals(0, handler.messages.size());
        assertEquals(1, handler.errors.size());
        assertTrue(handler.errors.get(0) instanceof NopFeishuException);
    }

    @Test
    void sendMessageAcquiresAndUsesAccessToken() {
        client.start(creds(), handler);

        client.sendMessage("chat_id", "oc_1", "text", "{\"text\":\"hi\"}");
        client.sendMessage("chat_id", "oc_2", "text", "{\"text\":\"yo\"}");

        assertEquals(1, httpApi.getTokenCount, "token acquired once and cached");
        assertEquals(2, httpApi.sendMessageCount, "both messages sent");
    }

    @Test
    void sendMessageWhileNotStartedFailsExplicitly() {
        assertThrows(NopFeishuException.class,
                () -> client.sendMessage("chat_id", "oc_1", "text", "{}"));
        assertEquals(0, httpApi.getTokenCount, "no token acquisition before start");
    }

    @Test
    void startWithNullCredentialsFailsExplicitly() {
        assertThrows(NopFeishuException.class, () -> client.start(null, handler));
        assertEquals(0, transport.connectCount, "null credentials must not open a connection");
    }

    @Test
    void startWithNullHandlerFailsExplicitly() {
        assertThrows(NopFeishuException.class, () -> client.start(creds(), null));
        assertEquals(0, transport.connectCount);
    }

    @Test
    void doubleStartIsIdempotent() {
        client.start(creds(), handler);
        client.start(creds(), handler);

        assertEquals(1, transport.connectCount, "duplicate start must not open a second connection");
        assertTrue(client.isConnected());
    }

    @Test
    void heartbeatSendsControlFrameAndIsWired() {
        client.start(creds(), handler);
        int sentBefore = transport.sentFrames.size();

        client.sendHeartbeat();

        assertEquals(sentBefore + 1, transport.sentFrames.size());
        FeishuStreamFrame hb = FeishuPbCodec.decode(transport.sentFrames.get(transport.sentFrames.size() - 1));
        assertEquals(FeishuFrameType.CONTROL.getMethod(), hb.getMethod());
        assertEquals("heartbeat", hb.getHeaders().get("type"));
        assertTrue(scheduler.scheduleAtFixedRateCount >= 1, "heartbeat must be scheduled by start");
    }

    // ---- fakes ----

    static final class FakeStreamTransport implements IStreamTransport {
        int connectCount;
        int closeCount;
        final List<byte[]> sentFrames = new ArrayList<>();
        private IStreamListener listener;

        @Override
        public void connect(URI uri, IStreamListener listener) {
            connectCount++;
            this.listener = listener;
            // synchronous handshake so tests are deterministic
            listener.onOpen();
        }

        @Override
        public void send(byte[] frameBytes) {
            sentFrames.add(frameBytes);
        }

        @Override
        public void close() {
            closeCount++;
            listener = null;
        }

        void deliver(byte[] data) {
            if (listener != null) {
                listener.onBinary(data);
            }
        }

        // W16-impl-ext: 触发 onClose（重连路径测试入口）
        void fireClose() {
            if (listener != null) {
                listener.onClose();
            }
        }
    }

    static final class FakeFeishuHttpApi implements IFeishuHttpApi {
        int endpointCount;
        int getTokenCount;
        int sendMessageCount;
        // W16-impl-ext: 记录每次调用的凭证参数（appId/appSecret 消费点接线断言）
        final List<String> endpointAppIds = new ArrayList<>();
        final List<String> endpointAppSecrets = new ArrayList<>();
        final List<String> tokenAppIds = new ArrayList<>();
        final List<String> tokenAppSecrets = new ArrayList<>();

        @Override
        public StreamEndpoint getStreamEndpoint(String appId, String appSecret) {
            endpointCount++;
            endpointAppIds.add(appId);
            endpointAppSecrets.add(appSecret);
            return new StreamEndpoint("wss://fake.feishu.cn/stream", "ticket-" + endpointCount);
        }

        @Override
        public AccessTokenResult getTenantAccessToken(String appId, String appSecret) {
            getTokenCount++;
            tokenAppIds.add(appId);
            tokenAppSecrets.add(appSecret);
            return new AccessTokenResult("t-token-" + getTokenCount, 7200L);
        }

        @Override
        public int sendMessage(String accessToken, String receiveIdType, String receiveId,
                               String msgType, String content) {
            sendMessageCount++;
            assertEquals("t-token-1", accessToken, "second call must reuse cached token");
            return 200;
        }
    }

    /** A scheduler that captures schedule requests but never runs them. */
    static final class NoopScheduler implements java.util.concurrent.ScheduledExecutorService {
        int scheduleAtFixedRateCount;
        int scheduleCount;
        // W16-impl-ext: 记录一次性调度命令（重连路径测试可手动执行）
        final List<Runnable> scheduledCommands = new ArrayList<>();
        private boolean shutdown;

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            scheduleAtFixedRateCount++;
            return null;
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            scheduleCount++;
            scheduledCommands.add(command);
            return null;
        }

        @Override
        public <V> ScheduledFuture<V> schedule(java.util.concurrent.Callable<V> callable, long delay, TimeUnit unit) {
            scheduleCount++;
            return null;
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            scheduleAtFixedRateCount++;
            return null;
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return java.util.Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public <T> java.util.concurrent.Future<T> submit(java.util.concurrent.Callable<T> task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> java.util.concurrent.Future<T> submit(Runnable task, T result) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.concurrent.Future<?> submit(Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> List<java.util.concurrent.Future<T>> invokeAll(java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> List<java.util.concurrent.Future<T>> invokeAll(java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks, long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T invokeAny(java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T invokeAny(java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks, long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void execute(Runnable command) {
            throw new UnsupportedOperationException();
        }
    }

    static final class RecordingHandler implements IMessageHandler {
        final List<FeishuInboundMessage> messages = new ArrayList<>();
        final List<Throwable> errors = new ArrayList<>();

        @Override
        public void onMessage(FeishuInboundMessage message) {
            messages.add(message);
        }

        @Override
        public void onError(Throwable error) {
            errors.add(error);
        }
    }
}
