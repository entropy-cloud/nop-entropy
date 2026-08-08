package io.nop.integration.feishu.client;

import java.net.URI;

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

    /** Close the connection and release transport resources. */
    void close();
}
