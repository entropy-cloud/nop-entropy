package io.nop.integration.api.channel;

/**
 * Business-driven channel message service — a pure "usage layer" facade whose
 * methods are anchored <b>only</b> on the platform userId, never on
 * channel-protocol fields. This is the single entry point for business code
 * that needs to push a proactive notification to a user
 * ({@link #sendToUser}) or receive non-agent inbound channel messages
 * ({@link #subscribeInbound}).
 *
 * <p><b>Layering</b>: this interface lives in {@code nop-integration-api}
 * (depends only on {@code nop-api-core}) so any business module can send
 * channel messages without depending on the AI engine. The implementation
 * lives in {@code nop-ai-gateway}, which bridges to the transport-layer
 * {@code IChannelConnector} via {@code ChannelConnectorManager}.
 *
 * <p><b>Agent session vs. notification</b>: this service handles proactive
 * notifications that do <b>not</b> trigger agent reasoning. Agent-session
 * responses flow back through the transport layer's
 * {@code IAgentEventPublisher} subscription directly (design §3.2), not
 * through this interface.
 *
 * <p><b>Cross-channel degradation</b> (e.g. Feishu unbound &#8594; fall back
 * to SMS) is an explicit v1 non-goal. The caller inspects the returned
 * {@link SendResult} and decides its own fallback.
 */
public interface IChannelMessageService {

    /**
     * Push a proactive (non-agent) message to a user. The service resolves
     * the user's channel binding via {@link UserChannelResolver}, selects the
     * connector, and hands the message to the transport layer. No exception
     * is thrown for "no binding" or "unsupported channel" — those are
     * explicit {@link SendResult} values.
     *
     * @param userId  the platform user id; must not be null
     * @param message the outbound message; must not be null
     * @return {@link SendResult#SENT}, {@link SendResult#NO_BINDING}, or
     *         {@link SendResult#UNSUPPORTED}
     */
    SendResult sendToUser(String userId, OutboundChannelMessage message);

    /**
     * Subscribe to inbound channel messages. Listeners are invoked when a
     * transport-layer connector forwards a non-agent user message through
     * the implementation's {@code dispatchInbound}.
     *
     * @param listener the listener to register; must not be null
     */
    void subscribeInbound(IInboundMessageListener listener);
}
