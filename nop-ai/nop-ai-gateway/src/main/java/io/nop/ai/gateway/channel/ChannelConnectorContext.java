package io.nop.ai.gateway.channel;

import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.engine.IAgentEventPublisher;
import io.nop.ai.gateway.login.NopAiGatewayErrors;
import io.nop.api.core.exceptions.NopException;

/**
 * Context handed to a connector at {@link IChannelConnector#start(Context)}.
 * Carries the agent-engine dependencies the connector needs to forward
 * inbound user messages (via {@link IAgentEngine#sendMessage}) and to receive
 * agent responses (by subscribing to {@link IAgentEventPublisher}), together
 * with the channel's {@link ChannelConfig}.
 *
 * <p>The connector is expected to retain this context for the lifetime of its
 * started connection. {@code null} engine/publisher are rejected at
 * construction so a connector can rely on non-null accessors.
 */
public class ChannelConnectorContext {

    private final IAgentEngine agentEngine;
    private final IAgentEventPublisher eventPublisher;
    private final ChannelConfig config;

    public ChannelConnectorContext(IAgentEngine agentEngine,
                                   IAgentEventPublisher eventPublisher,
                                   ChannelConfig config) {
        if (agentEngine == null) {
            throw new NopException(NopAiGatewayErrors.ERR_CHANNEL_CONTEXT_NULL_ENGINE)
                    .param(NopAiGatewayErrors.ARG_FIELD, "agentEngine");
        }
        if (eventPublisher == null) {
            throw new NopException(NopAiGatewayErrors.ERR_CHANNEL_CONTEXT_NULL_PUBLISHER)
                    .param(NopAiGatewayErrors.ARG_FIELD, "eventPublisher");
        }
        this.agentEngine = agentEngine;
        this.eventPublisher = eventPublisher;
        this.config = config != null ? config : new ChannelConfig();
    }

    public IAgentEngine getAgentEngine() {
        return agentEngine;
    }

    public IAgentEventPublisher getEventPublisher() {
        return eventPublisher;
    }

    public ChannelConfig getConfig() {
        return config;
    }
}
