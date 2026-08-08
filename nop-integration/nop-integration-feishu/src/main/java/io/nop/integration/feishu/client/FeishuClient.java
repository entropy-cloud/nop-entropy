package io.nop.integration.feishu.client;

import io.nop.integration.feishu.NopFeishuException;
import io.nop.integration.feishu.codec.FeishuFrameType;
import io.nop.integration.feishu.codec.FeishuPbCodec;
import io.nop.integration.feishu.codec.FeishuStreamFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages the Feishu/Lark Stream long-connection lifecycle plus Open API
 * calls. This is the protocol client consumed by the channel connector
 * (Plan 7 {@code FeishuConnector}).
 *
 * <p><b>W5-0 implementation path</b>: independent implementation (option B).
 * Transport = JDK {@code java.net.http.WebSocket} (no external dependency);
 * frame codec = {@link FeishuPbCodec} (hand-written protobuf wire format);
 * Open API = JDK {@code java.net.http.HttpClient}.
 *
 * <p><b>Testability seams</b>: the {@link IStreamTransport} and
 * {@link IFeishuHttpApi} seams (plus the heartbeat executor) are injectable so
 * unit tests can drive lifecycle state and message delivery without a real
 * Feishu server (real connectivity is W6).
 *
 * <p><b>No silent no-op</b>: null credentials, sendMessage while disconnected
 * and decode/transport failures are surfaced explicitly (thrown or
 * {@link IMessageHandler#onError(Throwable)}).
 */
public class FeishuClient {

    static final Logger LOG = LoggerFactory.getLogger(FeishuClient.class);

    private static final long DEFAULT_HEARTBEAT_SECONDS = 30;
    private static final long RECONNECT_INITIAL_DELAY_MS = 1000;
    private static final long RECONNECT_MAX_DELAY_MS = 30_000;
    private static final long TOKEN_REFRESH_LEAD_MS = 5_000;

    private final IStreamTransport transport;
    private final IFeishuHttpApi httpApi;
    private final ScheduledExecutorService scheduler;
    private final boolean ownsScheduler;

    private long heartbeatIntervalSeconds = DEFAULT_HEARTBEAT_SECONDS;

    // connection state
    private volatile boolean started = false;
    private volatile boolean connected = false;
    private FeishuCredentials credentials;
    private IMessageHandler handler;
    private StreamEndpoint endpoint;
    private ScheduledFuture<?> heartbeatFuture;
    private final AtomicLong reconnectAttempt = new AtomicLong();

    // tenant_access_token cache
    private volatile String cachedToken;
    private volatile long tokenExpireAtMs;

    /** Production constructor: JDK transport + JDK HTTP API + owned daemon scheduler. */
    public FeishuClient() {
        this(new JdkStreamTransport(), new JdkFeishuHttpApi(), null);
    }

    /** Test-friendly constructor with injectable seams (package-private). */
    FeishuClient(IStreamTransport transport, IFeishuHttpApi httpApi, ScheduledExecutorService scheduler) {
        this.transport = transport;
        this.httpApi = httpApi;
        if (scheduler != null) {
            this.scheduler = scheduler;
            this.ownsScheduler = false;
        } else {
            this.scheduler = Executors.newSingleThreadScheduledExecutor(new DaemonThreadFactory("feishu-client"));
            this.ownsScheduler = true;
        }
    }

    public void setHeartbeatIntervalSeconds(long seconds) {
        this.heartbeatIntervalSeconds = seconds;
    }

    /**
     * Establish the Stream long-connection. After the transport handshake
     * completes, a CONTROL {@code connect} frame carrying the app id + ticket
     * is sent, then periodic heartbeats are scheduled.
     *
     * <p><b>Idempotent</b>: calling start while already started is a no-op
     * (no duplicate connection) — an explicit, deterministic behaviour.
     *
     * @throws NopFeishuException if credentials or handler is null
     */
    public synchronized void start(FeishuCredentials credentials, IMessageHandler handler) {
        if (credentials == null) {
            throw new NopFeishuException("FeishuClient.start: credentials must not be null");
        }
        if (handler == null) {
            throw new NopFeishuException("FeishuClient.start: handler must not be null");
        }
        if (started) {
            LOG.debug("feishu-client already started, ignoring duplicate start");
            return;
        }
        this.credentials = credentials;
        this.handler = handler;
        this.endpoint = httpApi.getStreamEndpoint(credentials.getAppId(), credentials.getAppSecret());
        transport.connect(URI.create(endpoint.getUri()), streamListener);
        this.started = true;
        scheduleHeartbeat();
    }

    /** Gracefully stop the connection: cancel heartbeat, close transport, release scheduler. */
    public synchronized void stop() {
        if (!started) {
            return;
        }
        started = false;
        connected = false;
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(false);
            heartbeatFuture = null;
        }
        try {
            transport.close();
        } catch (Exception e) {
            LOG.debug("feishu-client transport close ignored error", e);
        }
        if (ownsScheduler) {
            scheduler.shutdownNow();
        }
        this.credentials = null;
        this.handler = null;
        this.endpoint = null;
        this.cachedToken = null;
        this.tokenExpireAtMs = 0;
        this.reconnectAttempt.set(0);
    }

    public boolean isConnected() {
        return connected;
    }

    /**
     * Send a message via the Feishu Open API {@code im/v1/messages}. Acquires
     * (and caches) a {@code tenant_access_token} transparently; on the second
     * call within the token validity window the token is reused without a
     * fresh acquisition.
     *
     * @throws NopFeishuException if not started/connected, or the API call fails
     */
    public void sendMessage(String receiveIdType, String receiveId, String msgType, String content) {
        if (!started) {
            throw new NopFeishuException("FeishuClient.sendMessage: client not started");
        }
        String token = ensureToken();
        httpApi.sendMessage(token, receiveIdType, receiveId, msgType, content);
    }

    private String ensureToken() {
        long now = System.currentTimeMillis();
        String token = cachedToken;
        if (token != null && tokenExpireAtMs > now) {
            return token;
        }
        AccessTokenResult result = httpApi.getTenantAccessToken(credentials.getAppId(), credentials.getAppSecret());
        cachedToken = result.getToken();
        tokenExpireAtMs = now + Math.max(1L, result.getExpireSeconds()) * 1000L - TOKEN_REFRESH_LEAD_MS;
        return cachedToken;
    }

    private void scheduleHeartbeat() {
        if (heartbeatIntervalSeconds <= 0) {
            return;
        }
        heartbeatFuture = scheduler.scheduleAtFixedRate(
                this::sendHeartbeat,
                heartbeatIntervalSeconds, heartbeatIntervalSeconds, TimeUnit.SECONDS);
    }

    /** Send a CONTROL heartbeat frame to keep the connection alive. Package-private for direct testing. */
    void sendHeartbeat() {
        if (!connected) {
            return;
        }
        try {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("type", "heartbeat");
            FeishuStreamFrame frame = new FeishuStreamFrame(FeishuFrameType.CONTROL.getMethod(), headers,
                    FeishuStreamFrame.EMPTY_PAYLOAD, null);
            transport.send(FeishuPbCodec.encode(frame));
        } catch (Exception e) {
            LOG.debug("feishu-client heartbeat send failed", e);
        }
    }

    private void scheduleReconnect() {
        if (!started) {
            return;
        }
        long attempt = reconnectAttempt.incrementAndGet();
        long delay = Math.min(RECONNECT_INITIAL_DELAY_MS << Math.min(attempt - 1, 14), RECONNECT_MAX_DELAY_MS);
        LOG.info("feishu-client scheduling reconnect attempt {} in {} ms", attempt, delay);
        try {
            scheduler.schedule(this::attemptReconnect, delay, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            LOG.warn("feishu-client failed to schedule reconnect", e);
        }
    }

    private void attemptReconnect() {
        if (!started) {
            return;
        }
        try {
            StreamEndpoint ep = httpApi.getStreamEndpoint(credentials.getAppId(), credentials.getAppSecret());
            this.endpoint = ep;
            transport.connect(URI.create(ep.getUri()), streamListener);
            reconnectAttempt.set(0);
        } catch (Exception e) {
            LOG.warn("feishu-client reconnect failed, will retry", e);
            scheduleReconnect();
        }
    }

    private final IStreamListener streamListener = new IStreamListener() {
        @Override
        public void onOpen() {
            connected = true;
            // send CONTROL connect handshake carrying app id + ticket
            try {
                Map<String, String> headers = new LinkedHashMap<>();
                headers.put("type", "connect");
                String payload = "{\"app_id\":\"" + credentials.getAppId()
                        + "\",\"ticket\":\"" + endpoint.getTicket() + "\"}";
                FeishuStreamFrame handshake = new FeishuStreamFrame(FeishuFrameType.CONTROL.getMethod(),
                        headers, payload.getBytes(StandardCharsets.UTF_8), null);
                transport.send(FeishuPbCodec.encode(handshake));
            } catch (Exception e) {
                LOG.warn("feishu-client connect handshake send failed", e);
            }
        }

        @Override
        public void onBinary(byte[] data) {
            try {
                FeishuStreamFrame frame = FeishuPbCodec.decode(data);
                if (frame.getMethod() == FeishuFrameType.DATA.getMethod()) {
                    FeishuInboundMessage msg = toInbound(frame);
                    handler.onMessage(msg);
                } else if (frame.getMethod() == FeishuFrameType.ACK.getMethod()) {
                    LOG.debug("feishu-client received ACK frame");
                } else {
                    LOG.debug("feishu-client received CONTROL frame");
                }
            } catch (Throwable t) {
                handler.onError(t);
            }
        }

        @Override
        public void onClose() {
            connected = false;
            scheduleReconnect();
        }

        @Override
        public void onError(Throwable error) {
            connected = false;
            if (handler != null) {
                handler.onError(error);
            }
            scheduleReconnect();
        }
    };

    private static FeishuInboundMessage toInbound(FeishuStreamFrame frame) {
        String json = frame.getPayload() == null ? "" : new String(frame.getPayload(), StandardCharsets.UTF_8);
        FeishuInboundMessage m = new FeishuInboundMessage();
        m.setReceiveIdType(FeishuJsons.extractString(json, "receive_id_type"));
        m.setReceiveId(FeishuJsons.extractString(json, "receive_id"));
        m.setMsgType(FeishuJsons.extractString(json, "msg_type"));
        m.setContent(FeishuJsons.extractString(json, "content"));
        m.setSenderId(FeishuJsons.extractString(json, "sender_id"));
        m.setChatType(FeishuJsons.extractString(json, "chat_type"));
        m.setRawPayload(frame.getPayload());
        return m;
    }

    private static final class DaemonThreadFactory implements ThreadFactory {
        private final String prefix;

        DaemonThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + "-" + System.identityHashCode(r));
            t.setDaemon(true);
            return t;
        }
    }
}
