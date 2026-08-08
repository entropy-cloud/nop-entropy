package io.nop.integration.feishu.client;

/**
 * Callback interface for receiving parsed inbound Feishu messages and
 * connection errors from {@link FeishuClient}. The channel connector
 * (Plan 7 {@code FeishuConnector}) implements this interface to forward
 * inbound user messages to the agent engine.
 *
 * <p><b>No silent no-op</b>: connection/decoding failures are reported via
 * {@link #onError(Throwable)} rather than swallowed.
 */
public interface IMessageHandler {

    /**
     * Called when a DATA frame carrying a Feishu event is received and parsed.
     *
     * @param message the parsed inbound message; never null
     */
    void onMessage(FeishuInboundMessage message);

    /**
     * Called when the connection or frame decoding fails. Implementations
     * should not rethrow — the client handles reconnect internally.
     *
     * @param error the error; never null
     */
    void onError(Throwable error);
}
