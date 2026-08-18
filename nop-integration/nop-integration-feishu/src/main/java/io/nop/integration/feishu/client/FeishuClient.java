package io.nop.integration.feishu.client;

import io.nop.credential.api.CredentialData;
import io.nop.credential.api.ICredentialProvider;
import io.nop.integration.api.credential.CredentialResolutionSupport;
import io.nop.integration.feishu.NopFeishuException;
import io.nop.integration.feishu.codec.FeishuFrameType;
import io.nop.integration.feishu.codec.FeishuPbCodec;
import io.nop.integration.feishu.codec.FeishuStreamFrame;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
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

    /** 本渠道的凭证类型（家族允许集单元素；W16-impl-ext 设计 §4.3 类型清单）。 */
    static final String CREDENTIAL_TYPE_FEISHU_APP = "feishu-app";

    /** consumerRef 引用计数键（设计 §6.5：部署级单渠道，每渠道类型一条稳定引用）。 */
    static final String CONSUMER_REF = "integration:feishu-app";

    private final IStreamTransport transport;
    private final IFeishuHttpApi httpApi;
    private final ScheduledExecutorService scheduler;
    private final boolean ownsScheduler;

    private long heartbeatIntervalSeconds = DEFAULT_HEARTBEAT_SECONDS;

    /**
     * 凭证消费 SPI（可选装配，{@code @Nullable} → NopIoC optional）：部署不含 nop-credential 时
     * 为 null——credentialId 非空时经共享解析支持 fail-closed（部署不一致），静态路径不受影响。
     */
    protected ICredentialProvider credentialProvider;

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

    @Inject
    public void setCredentialProvider(@Nullable ICredentialProvider credentialProvider) {
        this.credentialProvider = credentialProvider;
    }

    /**
     * start 期有效凭证（W16-impl-ext，设计 §4.1 结论 6/7）。
     *
     * <p>优先级链（共享解析支持单点语义）：credentials.credentialId 空/空白 → 原对象直用
     * （DataBean {@code @InjectValue} 装配形态不变，四键静态值现状路径）；非空 → 解析
     * {@code feishu-app} 四字段（appId/appSecret 必填、verificationToken/encryptKey 可空敏感）
     * 并构建<b>已解析副本</b>（整组覆盖，同名静态值忽略）供 client 持有——token 惰性刷新
     * （{@code ensureToken}）与重连（{@code attemptReconnect}）继续使用已捕获值（轮换可见性 =
     * connector 重启）。解析失败 fail-closed 抛 {@code NopException}（不回退静态值）。包可见以便
     * 单元测试断言。
     */
    FeishuCredentials resolveEffectiveCredentials(FeishuCredentials credentials) {
        String credentialId = credentials.getCredentialId();
        if (!CredentialResolutionSupport.isConfigured(credentialId)) {
            return credentials;
        }
        CredentialData data = CredentialResolutionSupport.resolveGroup(credentialProvider, credentialId,
                Set.of(CREDENTIAL_TYPE_FEISHU_APP));
        FeishuCredentials resolved = new FeishuCredentials();
        resolved.setCredentialId(credentialId);
        resolved.setAppId(CredentialResolutionSupport.requireString(data, credentialId, "appId"));
        resolved.setAppSecret(CredentialResolutionSupport.requireString(data, credentialId, "appSecret"));
        resolved.setVerificationToken(CredentialResolutionSupport.optionalString(data, "verificationToken"));
        resolved.setEncryptKey(CredentialResolutionSupport.optionalString(data, "encryptKey"));
        return resolved;
    }

    /**
     * start 期幂等登记引用计数（credentialId 非空且 provider 装配时）。<b>catch-all WARN 不阻断
     * 启动</b>——含 provider 前置校验抛错（credentialId 配错/凭证软删）：登记是治理辅助而非安全边界
     * （安全边界 = start 期解析 fail-closed）。W16-impl-ext 登记载体裁定：live 形态下
     * {@code FeishuClient} 仅在 {@code start()} 经参数获得 credentials（bean 初始化期不可达），
     * 故登记随 start 期执行（credentialId 随 credentials 到手，生产链路 FeishuConnector.start →
     * client.start 真实可达）——设计 §4.1 结论 8 "bean 初始化"措辞的家族偏差，回写见设计 03 号文。
     */
    void registerUsageQuietly() {
        String credentialId = this.credentials != null ? this.credentials.getCredentialId() : null;
        if (!CredentialResolutionSupport.isConfigured(credentialId) || credentialProvider == null) {
            return;
        }
        try {
            credentialProvider.registerUsage(credentialId, CONSUMER_REF);
        } catch (Exception e) {
            LOG.warn("nop.credential-register-usage-failed:credentialId={},consumerRef={} (non-blocking)",
                    credentialId, CONSUMER_REF, e);
        }
    }

    /**
     * Establish the Stream long-connection. After the transport handshake
     * completes, a CONTROL {@code connect} frame carrying the app id + ticket
     * is sent, then periodic heartbeats are scheduled.
     *
     * <p><b>Idempotent</b>: calling start while already started is a no-op
     * (no duplicate connection) — an explicit, deterministic behaviour.
     *
     * <p>W16-impl-ext: when {@code credentials.credentialId} is configured, the
     * four-field credential group is resolved through {@code ICredentialProvider}
     * here (fail-closed on any resolution failure — no silent fallback to the
     * static config values) and a resolved copy is captured for the client
     * lifetime (token refresh and reconnect keep using the captured values).
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
        this.credentials = resolveEffectiveCredentials(credentials);
        registerUsageQuietly();
        this.handler = handler;
        this.endpoint = httpApi.getStreamEndpoint(this.credentials.getAppId(), this.credentials.getAppSecret());
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
