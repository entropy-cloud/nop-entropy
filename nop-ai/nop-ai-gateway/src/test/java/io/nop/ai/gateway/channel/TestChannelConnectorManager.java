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

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 3 wiring test: proves {@link ChannelConnectorManager} REALLY drives
 * connector lifecycles at runtime (Anti-Hollow) — not just a type-level
 * registry. A stub connector records {@code start}/{@code stop} call counts;
 * the test asserts they advance after {@code startAll}/{@code stopAll} and
 * that the connector receives a non-null context carrying the engine deps.
 *
 * <p>Also covers the stub-integration chain (manager → connector → engine
 * deps): after {@code startAll}, the started connector reaches
 * {@link io.nop.ai.agent.engine.IAgentEngine} /
 * {@link io.nop.ai.agent.engine.IAgentEventPublisher} through the context it
 * received.
 */
class TestChannelConnectorManager {

    @Test
    void startAllInvokesEachConnectorStartWithNonNullContext() {
        ChannelConnectorManager manager = new ChannelConnectorManager();
        RecordingConnector a = new RecordingConnector("alpha", null);
        RecordingConnector b = new RecordingConnector("beta", null);
        manager.setConnectors(java.util.List.of(a, b));

        ChannelConnectorContext ctx = newContext();

        manager.startAll(ctx);

        assertEquals(1, a.startCount, "startAll must call start() on connector alpha");
        assertEquals(1, b.startCount, "startAll must call start() on connector beta");
        assertSame(ctx, a.receivedContext, "connector must receive the exact context passed to startAll");
        assertNotNull(a.receivedContext.getAgentEngine(),
                "started connector must reach IAgentEngine through the context");
        assertNotNull(a.receivedContext.getEventPublisher(),
                "started connector must reach IAgentEventPublisher through the context");
    }

    @Test
    void stopAllInvokesEachConnectorStopInReverseOrder() {
        ChannelConnectorManager manager = new ChannelConnectorManager();
        java.util.List<String> stopOrder = new java.util.ArrayList<>();
        RecordingConnector a = new RecordingConnector("alpha", stopOrder);
        RecordingConnector b = new RecordingConnector("beta", stopOrder);
        manager.setConnectors(java.util.List.of(a, b));

        manager.startAll(newContext());
        // no connector stopped yet
        assertEquals(0, a.stopCount);
        assertEquals(0, b.stopCount);

        manager.stopAll();

        assertEquals(1, a.stopCount, "stopAll must call stop() on connector alpha");
        assertEquals(1, b.stopCount, "stopAll must call stop() on connector beta");
        // reverse-order proof: b (started last) is stopped BEFORE a (started first)
        assertEquals(java.util.List.of("beta", "alpha"), stopOrder,
                "stopAll must stop in reverse start order (LIFO)");
    }

    @Test
    void lookupReturnsRegisteredConnectorAndThrowsOnUnknown() {
        ChannelConnectorManager manager = new ChannelConnectorManager();
        RecordingConnector a = new RecordingConnector("alpha", null);
        manager.register(a);

        assertSame(a, manager.lookup("alpha"));

        // unknown channelType must throw (no silent null return)
        assertThrows(RuntimeException.class, () -> manager.lookup("no-such-channel"));
    }

    @Test
    void emptyManagerStartAllIsLegitimateNoOp() {
        // no connectors registered: startAll/stopAll over an empty set is a
        // legitimate no-op (the set is genuinely empty — not work being skipped)
        ChannelConnectorManager manager = new ChannelConnectorManager();
        manager.startAll(newContext());
        manager.stopAll();
        assertEquals(0, manager.getConnectors().size());
    }

    @Test
    void startAllRejectsNullContext() {
        ChannelConnectorManager manager = new ChannelConnectorManager();
        manager.register(new RecordingConnector("alpha", null));
        // null context must surface, not silently propagate to connectors
        assertThrows(RuntimeException.class, () -> manager.startAll(null));
    }

    @Test
    void constructorRejectsNullAgentEngineWithErrorCodeAndParam() {
        NopException e = assertThrows(NopException.class,
                () -> new ChannelConnectorContext(null, new NopEventPublisher(), new ChannelConfig()),
                "null agentEngine must fail fast at construction");
        assertEquals(NopAiGatewayErrors.ERR_CHANNEL_CONTEXT_NULL_ENGINE.getErrorCode(), e.getErrorCode(),
                "null agentEngine must throw the module error code, not a bare IAE");
        assertEquals("agentEngine", e.getParam(NopAiGatewayErrors.ARG_FIELD));
    }

    @Test
    void constructorRejectsNullEventPublisherWithErrorCodeAndParam() {
        NopException e = assertThrows(NopException.class,
                () -> new ChannelConnectorContext(new StubAgentEngine(), null, new ChannelConfig()),
                "null eventPublisher must fail fast at construction");
        assertEquals(NopAiGatewayErrors.ERR_CHANNEL_CONTEXT_NULL_PUBLISHER.getErrorCode(), e.getErrorCode(),
                "null eventPublisher must throw the module error code, not a bare IAE");
        assertEquals("eventPublisher", e.getParam(NopAiGatewayErrors.ARG_FIELD));
    }

    // ---- helpers / stubs ---------------------------------------------------

    private static ChannelConnectorContext newContext() {
        return new ChannelConnectorContext(new StubAgentEngine(), new NopEventPublisher(), new ChannelConfig("agent-1"));
    }

    static class RecordingConnector implements IChannelConnector {
        final String type;
        final java.util.List<String> stopOrderLog;
        ChannelConnectorContext receivedContext;
        int startCount;
        int stopCount;

        RecordingConnector(String type, java.util.List<String> stopOrderLog) {
            this.type = type;
            this.stopOrderLog = stopOrderLog;
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
            if (stopOrderLog != null) {
                stopOrderLog.add(type);
            }
        }

        @Override
        public ChannelCapabilities getCapabilities() {
            return new ChannelCapabilities();
        }

        @Override
        public void sendOutbound(String channelAddress, ChannelOutboundMessage outboundMessage) {
            // unused by these lifecycle tests
        }
    }

    static class StubAgentEngine implements IAgentEngine {
        @Override
        public AgentMessageAck sendMessage(AgentMessageRequest request) {
            return new AgentMessageAck(null, "accepted");
        }

        @Override
        public CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request) {
            throw new UnsupportedOperationException("StubAgentEngine.execute not used in manager test");
        }
    }

    static class NopEventPublisher implements IAgentEventPublisher {
        @Override
        public void publish(AgentEvent event) {
            // no-op
        }

        @Override
        public void addSubscriber(IAgentEventSubscriber subscriber) {
            // no-op
        }

        @Override
        public void removeSubscriber(IAgentEventSubscriber subscriber) {
            // no-op
        }
    }
}
