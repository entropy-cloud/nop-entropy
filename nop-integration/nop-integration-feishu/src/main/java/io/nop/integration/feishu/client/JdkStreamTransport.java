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
import java.util.concurrent.CompletionStage;

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
            ws.sendBinary(ByteBuffer.wrap(frameBytes), true).get();
        } catch (Exception e) {
            throw new NopFeishuException("JdkStreamTransport.send failed", e);
        }
    }

    @Override
    public void close() {
        WebSocket ws = this.webSocket;
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "client shutdown").join();
            } catch (Exception e) {
                LOG.debug("feishu stream close ignored error", e);
            }
            this.webSocket = null;
        }
        this.listener = null;
    }
}
