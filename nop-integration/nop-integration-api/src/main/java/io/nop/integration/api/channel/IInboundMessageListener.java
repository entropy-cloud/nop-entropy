package io.nop.integration.api.channel;

/**
 * Listener for inbound channel messages. Registered via
 * {@link IChannelMessageService#subscribeInbound}; invoked by the business
 * message layer's {@code dispatchInbound} when a transport-layer connector
 * forwards a non-agent user message. Multiple listeners may be registered
 * (fan-out for workflow triggers, audit, business handlers, ...).
 */
public interface IInboundMessageListener {

    /**
     * Called for each inbound channel message. Implementations should return
     * quickly; long work should be dispatched asynchronously.
     *
     * @param message the channel-agnostic inbound message; never null
     */
    void onInbound(InboundChannelMessage message);
}
