package io.nop.integration.feishu.client;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

/**
 * Abstraction over the WebSocket long-connection transport. The production
 * implementation {@code JdkStreamTransport} wraps the JDK
 * {@link java.net.http.WebSocket} client (Java 11+ stdlib — no external
 * dependency, per the W5-0 SDK-selection decision). Tests supply a fake that
 * drives the {@link IStreamListener} synchronously.
 *
 * <p>Package-private: internal seam, not a public API contract.
 */
interface IStreamTransport {

    /**
     * Establish the WebSocket connection to {@code uri}. When the handshake
     * completes, invoke {@code listener.onOpen()}; subsequent binary frames
     * invoke {@code listener.onBinary(byte[])}; closure invokes
     * {@code listener.onClose()}; errors invoke {@code listener.onError}.
     *
     * @param uri      the stream gateway URI
     * @param listener the connection listener; never null
     */
    void connect(URI uri, IStreamListener listener);

    /**
     * Send raw frame bytes over the established connection.
     *
     * @param frameBytes the encoded Pbbp2 frame; never null
     */
    void send(byte[] frameBytes);

    /**
     * C3-015 (plan 2522)：异步建连——connect 阻塞段移出心跳/重连 scheduler。
     * default 委托同步 connect 并返回已完成 future（既有假体零改写兼容；生产
     * JdkStreamTransport override 真异步链）。包私有 seam 的 default 方法级扩展，
     * 不构成跨模块公共契约面演进。
     */
    default CompletableFuture<Void> connectAsync(URI uri, IStreamListener listener) {
        connect(uri, listener);
        return CompletableFuture.completedFuture(null);
    }

    /** Close the connection and release transport resources. */
    void close();
}
