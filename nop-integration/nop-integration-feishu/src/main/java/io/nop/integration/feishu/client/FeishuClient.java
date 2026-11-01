package io.nop.integration.feishu.client;

import io.nop.api.core.time.CoreMetrics;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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

    // C1-009/C6-021 (plan 2522): single-flight reconnect + lifecycle generation guard
    private final AtomicBoolean reconnectPending = new AtomicBoolean();
    private final AtomicLong lifecycleGeneration = new AtomicLong();
    /** 世代快照：每次 connect 前记录，onOpen/完成回调比对（R2 Major-2 捕获侧机制）。 */
    private volatile long connectGeneration;
    /** C3-015 (plan 2522)：send 预算（毫秒），转发到 JdkStreamTransport（部署可调）。 */
    private long sendTimeoutMillis = 15_000L;

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
     * C3-015 (plan 2522)：send 预算部署可调面（缺省 15s，对齐 JdkFeishuHttpApi 同模块
     * 15s 先例）；转发到 JDK transport 的有界 get。半开连接上无界 {@code get()} 会占死
     * 心跳+重连唯一 scheduler 线程（整体死锁面）。
     */
    public void setSendTimeoutMillis(long sendTimeoutMillis) {
        this.sendTimeoutMillis = sendTimeoutMillis;
        if (transport instanceof JdkStreamTransport) {
            ((JdkStreamTransport) transport).setSendTimeoutMillis(sendTimeoutMillis);
        }
    }

    long getSendTimeoutMillis() {
        return sendTimeoutMillis;
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
        // C1-009 (plan 2522): 世代递增必须先于 connect —— 同步 connect 内 fake/生产都可能
        // 同步触发 onOpen，onOpen 比对的是 connect 前记录的快照
        this.connectGeneration = lifecycleGeneration.incrementAndGet();
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
        // C6-021 (plan 2522)：stop 递增世代——在途 connect/重连回调捕获的世代即刻过期
        lifecycleGeneration.incrementAndGet();
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
        long now = CoreMetrics.currentTimeMillis();
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
            // C3-015 (plan 2522)：心跳失败 debug→warn 升级，经失败处理单点驱动重连
            handleSendFailure("heartbeat", e);
        }
    }

    /**
     * C3-015 (plan 2522) 失败处理单点：:277/:324/:353 三 send 调用点统一收敛——
     * connected=false + close（尽力而为）+ warn（含站点维度）+ 重连调度（单飞由裁定②承载）。
     */
    private void handleSendFailure(String site, Exception e) {
        connected = false;
        LOG.warn("feishu-client send failed:site={}", site, e);
        try {
            transport.close();
        } catch (Exception closeError) {
            LOG.debug("feishu-client transport close after send failure ignored", closeError);
        }
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        if (!started) {
            return;
        }
        // C1-009 (plan 2522)：单飞 CAS——onClose/onError 双终态（无论互斥与否）去重为恰一在飞
        if (!reconnectPending.compareAndSet(false, true)) {
            return;
        }
        long attempt = reconnectAttempt.incrementAndGet();
        long delay = Math.min(RECONNECT_INITIAL_DELAY_MS << Math.min(attempt - 1, 14), RECONNECT_MAX_DELAY_MS);
        LOG.info("feishu-client scheduling reconnect attempt {} in {} ms", attempt, delay);
        long capturedGeneration = lifecycleGeneration.get();
        try {
            scheduler.schedule(() -> attemptReconnect(capturedGeneration, attempt), delay, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            reconnectPending.set(false);
            LOG.warn("feishu-client failed to schedule reconnect", e);
        }
    }

    /**
     * C1-009 世代守卫 + C6-021 在途建连撤销（plan 2522）。attempt 序号快照承载
     * 守卫清零（R2 Major-1）——仅当捕获序号==当前才清零单飞标志，防迟到回调击落
     * 更新的在飞声明。
     */
    private void attemptReconnect(long capturedGeneration, long capturedAttempt) {
        if (!started) {
            reconnectPending.set(false);
            return;
        }
        if (capturedGeneration != lifecycleGeneration.get()) {
            // 旧世代重连链终止：守卫清零，不重排
            if (capturedAttempt == reconnectAttempt.get())
                reconnectPending.set(false);
            return;
        }
        try {
            // C6-021：旧连接先 close（尽力而为），fd 不依赖对端断开
            try {
                transport.close();
            } catch (Exception closeError) {
                LOG.debug("feishu-client pre-reconnect close ignored", closeError);
            }
            StreamEndpoint ep = httpApi.getStreamEndpoint(credentials.getAppId(), credentials.getAppSecret());
            this.endpoint = ep;
            this.connectGeneration = lifecycleGeneration.get();
            long attemptGeneration = this.connectGeneration;
            transport.connectAsync(URI.create(ep.getUri()), streamListener).whenComplete((nothing, err) -> {
                if (err != null) {
                    LOG.warn("feishu-client reconnect failed, will retry", err);
                    if (attemptGeneration == lifecycleGeneration.get()) {
                        if (capturedAttempt == reconnectAttempt.get())
                            reconnectPending.set(false);
                        scheduleReconnect();
                    }
                    return;
                }
                // C6-021 消费点 (a)：stop 后完成的建连即刻关闭（孤儿消除）
                if (attemptGeneration != lifecycleGeneration.get()) {
                    LOG.info("feishu-client reconnect generation expired, closing stale connection");
                    try {
                        transport.close();
                    } catch (Exception closeError) {
                        LOG.debug("feishu-client stale-connect close ignored", closeError);
                    }
                    if (capturedAttempt == reconnectAttempt.get())
                        reconnectPending.set(false);
                    return;
                }
                // 消费点 (c)：尾段复校通过——守卫清零单飞标志（重连成功，退避由 stop 重置）
                if (capturedAttempt == reconnectAttempt.get())
                    reconnectPending.set(false);
            });
        } catch (Exception e) {
            LOG.warn("feishu-client reconnect failed, will retry", e);
            if (capturedAttempt == reconnectAttempt.get())
                reconnectPending.set(false);
            scheduleReconnect();
        }
    }

    private final IStreamListener streamListener = new IStreamListener() {
        @Override
        public void onOpen() {
            // C6-021 消费点 (b)（plan 2522）：世代不符（stop 后 onOpen 先达）不置 connected
            // 不发握手帧——封「建立与关闭之间 onOpen 先达」复活窗
            if (connectGeneration != lifecycleGeneration.get()) {
                LOG.info("feishu-client onOpen for expired generation, ignoring");
                return;
            }
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
                handleSendFailure("connect-handshake", e);
            }
        }

        @Override
        public void onBinary(byte[] data) {
            try {
                FeishuStreamFrame frame = FeishuPbCodec.decode(data);

                if (frame.getMethod() == FeishuFrameType.DATA.getMethod()) {

                    FeishuInboundMessage msg = toInbound(frame);

                    handler.onMessage(msg);

                    // P1-6.8-004（plan 2392）：DATA 帧处理后按 wire 协议回 ACK——headers 原样复制

                    //（携带 message_id/log_id 等回执定位信息）、requestId 沿用；缺失时服务端

                    // 视为未确认而重复投递/反复断连。

                    try {

                        FeishuStreamFrame ack = new FeishuStreamFrame(FeishuFrameType.ACK.getMethod(),

                                frame.getHeaders(), null, frame.getRequestId());

                        transport.send(FeishuPbCodec.encode(ack));

                    } catch (Exception e) {

                        handleSendFailure("ack", e);

                    }

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
        m.setHeaders(frame.getHeaders());
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
