package io.nop.integration.feishu.client;

/**
 * Listener for {@link IStreamTransport} connection events. The transport
 * invokes these callbacks; {@link FeishuClient} supplies the implementation
 * that drives frame decode/dispatch and connection state.
 *
 * <p>Package-private: this is an internal seam for testability, not a public
 * API contract.
 */
interface IStreamListener {

    /** Called once when the WebSocket handshake completes. */
    void onOpen();

    /**
     * Called when a binary frame arrives over the connection.
     *
     * @param data the raw Pbbp2 frame bytes; never null
     */
    void onBinary(byte[] data);

    /** Called when the connection has been closed. */
    void onClose();

    /** Called when a transport-level error occurs. */
    void onError(Throwable error);
}
