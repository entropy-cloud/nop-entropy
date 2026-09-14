package io.nop.ai.gateway.channel;

import io.nop.ai.agent.engine.AgentEvent;
import io.nop.ai.agent.engine.AgentExecutionResult;
import io.nop.ai.agent.engine.IAgentEngine;
import io.nop.ai.agent.engine.IAgentEventPublisher;
import io.nop.ai.agent.engine.IAgentEventSubscriber;
import io.nop.ai.agent.engine.AgentMessageAck;
import io.nop.ai.agent.engine.AgentMessageRequest;
import io.nop.ai.gateway.login.NopAiGatewayErrors;
import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 1 wiring test: verifies the {@link IChannelConnector} abstraction is
 * usable end-to-end at the type level — a stub connector receives a non-null
 * {@link ChannelConnectorContext}, can read both engine dependencies from it,
 * and {@link IChannelConnector#sendOutbound} receives the exact arguments
 * passed by the caller. This guards against hollow abstractions (empty method
 * bodies / dropped arguments).
 */
class TestIChannelConnectorWiring {

    @Test
    void connectorReceivesContextAndCanAccessEngineDeps() {
        IAgentEngine engine = new StubAgentEngine();
        IAgentEventPublisher publisher = new RecordingEventPublisher();
        ChannelConfig config = new ChannelConfig("agent-1");
        ChannelConnectorContext ctx = new ChannelConnectorContext(engine, publisher, config);

        RecordingConnector connector = new RecordingConnector("stub");

        // start hands the context to the connector
        connector.start(ctx);
        assertSame(ctx, connector.receivedContext, "start() must pass the context to the connector");

        // the connector can reach both engine dependencies through the context
        assertSame(engine, connector.receivedContext.getAgentEngine(),
                "context must expose the IAgentEngine");
        assertSame(publisher, connector.receivedContext.getEventPublisher(),
                "context must expose the IAgentEventPublisher");
        assertEquals("agent-1", connector.receivedContext.getConfig().getAgentName(),
                "context must expose the channel config");
    }

    @Test
    void sendOutboundReceivesAddressAndMessage() {
        RecordingConnector connector = new RecordingConnector("stub");
        // connector not started yet; sendOutbound should still capture args
        ChannelOutboundMessage msg = new ChannelOutboundMessage();
        msg.setText("hello");
        msg.setBusinessRef("notif-1");

        connector.sendOutbound("chat-42", msg);

        assertEquals("chat-42", connector.lastOutboundAddress,
                "sendOutbound must receive the channel address");
        assertSame(msg, connector.lastOutboundMessage,
                "sendOutbound must receive the outbound message");
        assertEquals("notif-1", connector.lastOutboundMessage.getBusinessRef());
    }

    @Test
    void contextRejectsNullEngineDeps() {
        IAgentEventPublisher publisher = new RecordingEventPublisher();
        // null engine must be rejected explicitly, not silently tolerated
        NopException e1 = assertThrows(NopException.class,
                () -> new ChannelConnectorContext(null, publisher, new ChannelConfig()));
        assertEquals(NopAiGatewayErrors.ERR_CHANNEL_CONTEXT_NULL_ENGINE.getErrorCode(), e1.getErrorCode());
        assertEquals("agentEngine", e1.getParam(NopAiGatewayErrors.ARG_FIELD));
        NopException e2 = assertThrows(NopException.class,
                () -> new ChannelConnectorContext(new StubAgentEngine(), null, new ChannelConfig()));
        assertEquals(NopAiGatewayErrors.ERR_CHANNEL_CONTEXT_NULL_PUBLISHER.getErrorCode(), e2.getErrorCode());
        assertEquals("eventPublisher", e2.getParam(NopAiGatewayErrors.ARG_FIELD));
    }

    // ---- stubs -------------------------------------------------------------

    /** Minimal recording connector: captures lifecycle + outbound args. */
    static class RecordingConnector implements IChannelConnector {
        final String type;
        ChannelConnectorContext receivedContext;
        int startCount;
        int stopCount;
        String lastOutboundAddress;
        ChannelOutboundMessage lastOutboundMessage;

        RecordingConnector(String type) {
            this.type = type;
        }

        @Override
        public String getChannelType() {
            return type;
        }

        @Override
        public void start(ChannelConnectorContext context) {
            this.receivedContext = context;
            this.startCount++;
        }

        @Override
        public void stop() {
            this.stopCount++;
        }

        @Override
        public ChannelCapabilities getCapabilities() {
            ChannelCapabilities caps = new ChannelCapabilities();
            caps.setSupportsMarkdown(true);
            return caps;
        }

        @Override
        public void sendOutbound(String channelAddress, ChannelOutboundMessage outboundMessage) {
            this.lastOutboundAddress = channelAddress;
            this.lastOutboundMessage = outboundMessage;
        }
    }

    /** Engine stub: only the abstract surface is implemented, unused paths throw. */
    static class StubAgentEngine implements IAgentEngine {
        @Override
        public AgentMessageAck sendMessage(AgentMessageRequest request) {
            return new AgentMessageAck(null, "accepted");
        }

        @Override
        public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            throw new UnsupportedOperationException("StubAgentEngine.execute not used in wiring test");
        }
    }

    static class RecordingEventPublisher implements IAgentEventPublisher {
        final List<IAgentEventSubscriber> subscribers = new ArrayList<>();

        @Override
        public void publish(AgentEvent event) {
            // no-op for wiring test
        }

        @Override
        public void addSubscriber(IAgentEventSubscriber subscriber) {
            subscribers.add(subscriber);
        }

        @Override
        public void removeSubscriber(IAgentEventSubscriber subscriber) {
            subscribers.remove(subscriber);
        }
    }
}
