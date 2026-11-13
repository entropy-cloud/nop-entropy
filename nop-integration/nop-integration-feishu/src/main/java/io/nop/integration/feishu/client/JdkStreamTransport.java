package io.nop.integration.feishu.client;

import io.nop.integration.feishu.NopFeishuException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * Production {@link IStreamTransport} backed by the JDK
 * {@link java.net.http.WebSocket} client (Java 11+ stdlib — no external
 * dependency, per the W5-0 SDK-selection decision). Real network behaviour is
 * verified at the W6 E2E stage.
 *
 * <p>Package-private: this is an internal transport implementation, exercised
 * only via the {@link IStreamTransport} seam.
 */
class JdkStreamTransport implements IStreamTransport {

    static final Logger LOG = LoggerFactory.getLogger(JdkStreamTransport.class);

    private final HttpClient httpClient;
    private volatile WebSocket webSocket;
    private volatile IStreamListener listener;

    /**
     * C3-015 (plan 2522)：send/close 预算（毫秒）。缺省 15s 对齐 JdkFeishuHttpApi
     * 同模块先例；无界 get() 在半开连接上占死心跳+重连唯一 scheduler 线程（整体死锁面）。
     */
    private long sendTimeoutMillis = 15_000L;
    private static final long CLOSE_TIMEOUT_MS = 5_000L;

    void setSendTimeoutMillis(long sendTimeoutMillis) {
        this.sendTimeoutMillis = sendTimeoutMillis;
    }

    JdkStreamTransport() {
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public void connect(URI uri, IStreamListener listener) {
        if (uri == null) {
            throw new NopFeishuException("JdkStreamTransport.connect: uri must not be null");
        }
        this.listener = listener;
        WebSocket.Listener wsListener = new WebSocket.Listener() {
            private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

            @Override
            public void onOpen(WebSocket webSocket) {
                webSocket.request(1);
                LOG.info("feishu stream connected to {}", uri);
                if (JdkStreamTransport.this.listener != null) {
                    JdkStreamTransport.this.listener.onOpen();
                }
            }

            @Override
            public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
                byte[] chunk = new byte[data.remaining()];
                data.get(chunk);
                buffer.write(chunk, 0, chunk.length);
                webSocket.request(1);
                if (last) {
                    byte[] frame = buffer.toByteArray();
                    buffer.reset();
                    if (JdkStreamTransport.this.listener != null) {
                        JdkStreamTransport.this.listener.onBinary(frame);
                    }
                }
                return null;
            }

            @Override
            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                LOG.info("feishu stream closed: {} {}", statusCode, reason);
                if (JdkStreamTransport.this.listener != null) {
                    JdkStreamTransport.this.listener.onClose();
                }
                return null;
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                LOG.error("feishu stream error", error);
                if (JdkStreamTransport.this.listener != null) {
                    JdkStreamTransport.this.listener.onError(error);
                }
            }
        };
        try {
            this.webSocket = httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .buildAsync(uri, wsListener)
                    .join();
        } catch (Exception e) {
            throw new NopFeishuException("JdkStreamTransport.connect failed: " + uri, e);
        }
    }

    /**
     * C3-015 (plan 2522)：connect 阻塞段移出 scheduler——异步建连链，listener 武装保持
     * 入口即刻生效（早期 onClose/onError 不丢）。假体经接口 default 委托同步 connect。
     */
    @Override
    public CompletableFuture<Void> connectAsync(URI uri, IStreamListener listener) {
        if (uri == null) {
            throw new NopFeishuException("JdkStreamTransport.connectAsync: uri must not be null");
        }
        this.listener = listener;
        WebSocket.Listener wsListener = new WebSocket.Listener() {
            private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

            @Override
            public void onOpen(WebSocket webSocket) {
                webSocket.request(1);
                LOG.info("feishu stream connected to {}", uri);
                if (JdkStreamTransport.this.listener != null) {
                    JdkStreamTransport.this.listener.onOpen();
                }
            }

            @Override
            public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
                byte[] chunk = new byte[data.remaining()];
                data.get(chunk);
                buffer.write(chunk, 0, chunk.length);
                webSocket.request(1);
                if (last) {
                    byte[] frame = buffer.toByteArray();
                    buffer.reset();
                    if (JdkStreamTransport.this.listener != null) {
                        JdkStreamTransport.this.listener.onBinary(frame);
                    }
                }
                return null;
            }

            @Override
            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                LOG.info("feishu stream closed: {} {}", statusCode, reason);
                if (JdkStreamTransport.this.listener != null) {
                    JdkStreamTransport.this.listener.onClose();
                }
                return null;
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                LOG.error("feishu stream error", error);
                if (JdkStreamTransport.this.listener != null) {
                    JdkStreamTransport.this.listener.onError(error);
                }
            }
        };
        return httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .buildAsync(uri, wsListener)
                .whenComplete((ws, err) -> {
                    if (err == null) {
                        this.webSocket = ws;
                    }
                })
                .handle((ws, err) -> {
                    if (err != null)
                        throw new NopFeishuException("JdkStreamTransport.connectAsync failed: " + uri, err);
                    return null;
                });
    }

    @Override
    public void send(byte[] frameBytes) {
        if (frameBytes == null) {
            throw new NopFeishuException("JdkStreamTransport.send: frameBytes must not be null");
        }
        WebSocket ws = this.webSocket;
        if (ws == null) {
            throw new NopFeishuException("JdkStreamTransport.send: not connected");
        }
        try {
            // C3-015 (plan 2522)：有界 get——半开连接上无界 get 永不返回（scheduler 整体死锁）
            ws.sendBinary(ByteBuffer.wrap(frameBytes), true).get(sendTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new NopFeishuException("JdkStreamTransport.send failed", e);
        }
    }

    @Override
    public void close() {
        WebSocket ws = this.webSocket;
        if (ws != null) {
            try {
                // C3-015 (plan 2522)：close 预算 5s 与 send 解耦——stop 持锁执行 close，
                // 长预算直接拉长停机延迟；超时即放弃
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "client shutdown")
                        .get(CLOSE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                LOG.debug("feishu stream close ignored error", e);
            }
            this.webSocket = null;
        }
        // 4.3-MA-16+C6-025（plan 2560 裁定 I）：stop 收敛清单补 transport 自持 JDK
        // HttpClient 的 close——此前 close 只关 WS，HttpClient selector 线程滞留
        try {
            this.httpClient.close();
        } catch (Exception e) {
            LOG.debug("feishu stream transport http client close ignored error", e);
        }
        this.listener = null;
    }
}
