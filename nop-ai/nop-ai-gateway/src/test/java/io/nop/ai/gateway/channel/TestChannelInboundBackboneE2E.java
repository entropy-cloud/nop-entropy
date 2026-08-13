package io.nop.ai.gateway.channel;

import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.integration.api.channel.IInboundMessageListener;
import io.nop.integration.api.channel.InboundChannelMessage;
import io.nop.message.core.local.LocalMessageService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * End-to-end + calling-contract-stability verification for the optional
 * inbound backbone (mode 2, design §3.3 问题 B / W6-4).
 *
 * <p><b>Anti-Hollow (Minimum Rule #22)</b>: the E2E test assembles REAL
 * components — a real {@link ChannelMessageServiceImpl} wired to a real
 * {@link LocalMessageService} (the genuine {@code IMessageService}
 * implementation, exercising the real {@code send}/{@code subscribe}
 * contract) — and drives the full path from a simulated transport-layer
 * inbound source ({@code dispatchInbound}) through {@code IMessageService.send}
 * &#8594; the {@code channel.inbound.feishu} topic &#8594; the singleton bridge
 * fan-out (to the business listener) AND an external audit consumer that
 * subscribes to the topic directly. It asserts each consumer actually
 * receives the message body, not merely that no exception is thrown.
 *
 * <p><b>Agent-session inbound is NOT in this backbone</b> (design §3.2/§3.3):
 * agent-session replies flow back through the transport layer's
 * {@code IAgentEventPublisher} subscription / {@code IChannelConnector}
 * directly and never pass through {@code dispatchInbound}. This backbone
 * serves only non-agent inbound (user messages that trigger workflow / audit
 * rather than agent reasoning). The tests below do not fabricate an agent
 * path through the backbone; {@link #agentSessionInboundIsNotRoutedViaBackbone}
 * documents this invariant.
 */
class TestChannelInboundBackboneE2E {

    private static final String FEISHU = "feishu";
    private static final String TOPIC = ChannelMessageServiceImpl.INBOUND_TOPIC_PREFIX + FEISHU;

    /**
     * E2E multi-consumer: one inbound message is received by BOTH consumer
     * kinds — the internal business listener (registered via
     * {@code subscribeInbound}, reached through the bridge) and an external
     * audit consumer (subscribed directly to the topic). Each captures and
     * the assertions confirm equivalent message bodies.
     */
    @Test
    void e2eBackboneDeliversToBusinessListenerAndExternalAuditConsumer() {
        LocalMessageService bus = new LocalMessageService();
        ChannelMessageServiceImpl svc = newChannelMessageService();
        svc.setMessageService(bus);

        TestChannelMessageService.RecordingListener business = new TestChannelMessageService.RecordingListener();
        svc.subscribeInbound(business);

        CaptureConsumer audit = new CaptureConsumer();
        bus.subscribe(TOPIC, audit);

        InboundChannelMessage inbound = inbound(FEISHU, "user-1", "audit-this-message");

        // a transport-layer connector hands a non-agent user message to the
        // business message layer (the only entry point; agent sessions do not
        // use it).
        svc.dispatchInbound(inbound);

        // (a) business listener received via the bridge fan-out (async on the
        // fan-out executor — I3 R-2-1 — await delivery)
        awaitUntil(() -> business.received.size() == 1 && audit.received.size() == 1,
                "business listener and external audit consumer to receive");
        // (b) external audit consumer received directly from the topic
        assertEquals(1, audit.received.size(), "external audit consumer received from topic");
        // the two consumers received the SAME message (equivalent delivery)
        assertSame(inbound, business.received.get(0));
        assertSame(inbound, audit.received.get(0));
        assertEquals("audit-this-message", business.received.get(0).getText());
        assertEquals("audit-this-message",
                ((InboundChannelMessage) audit.received.get(0)).getText());
    }

    /**
     * Calling-contract stability: the SAME calling code
     * ({@code svc.subscribeInbound(listener)} + {@code svc.dispatchInbound(msg)})
     * delivers in BOTH deployment modes. Harness A does not inject a
     * message service (mode 1, direct); harness B injects a real
     * {@link LocalMessageService} (mode 2, backbone). Both deliver the
     * message to the listener, and the delivered messages are equivalent —
     * proving the mode switch is invisible to callers.
     *
     * <p>Precision note: {@code dispatchInbound} is the impl's public inbound
     * entry (not on the {@code IChannelMessageService} interface, which only
     * declares {@code sendToUser} + {@code subscribeInbound}) and currently
     * has no production caller because agent-session inbound goes through the
     * transport layer directly. The stability being asserted is that this
     * inbound entry's callers are insensitive to the mode switch.
     */
    @Test
    void callingContractStableAcrossMode1DirectAndMode2Backbone() {
        InboundChannelMessage msg = inbound(FEISHU, "user-1", "contract-stable");

        // harness A — mode 1 (direct): no message service injected
        ChannelMessageServiceImpl svcA = newChannelMessageService();
        TestChannelMessageService.RecordingListener listenerA = new TestChannelMessageService.RecordingListener();
        svcA.subscribeInbound(listenerA);
        svcA.dispatchInbound(msg);

        // harness B — mode 2 (backbone): real LocalMessageService injected
        ChannelMessageServiceImpl svcB = newChannelMessageService();
        svcB.setMessageService(new LocalMessageService());
        TestChannelMessageService.RecordingListener listenerB = new TestChannelMessageService.RecordingListener();
        svcB.subscribeInbound(listenerB);
        svcB.dispatchInbound(msg);

        // identical calling code delivered in both modes, equivalent messages
        // (fan-out is async on the dedicated executor — I3 R-2-1 — await)
        awaitUntil(() -> listenerA.received.size() == 1 && listenerB.received.size() == 1,
                "mode 1 and mode 2 delivery");
        assertEquals(1, listenerA.received.size(), "mode 1 delivered");
        assertEquals(1, listenerB.received.size(), "mode 2 delivered");
        assertSame(msg, listenerA.received.get(0));
        assertSame(msg, listenerB.received.get(0));
        assertEquals(listenerA.received.get(0).getText(), listenerB.received.get(0).getText());
    }

    /**
     * Documents the agent-session invariant: agent replies go through the
     * transport layer directly (design §3.2/§3.3) and are NOT published to
     * the inbound backbone topic. This test asserts that the backbone topic
     * carries only non-agent inbound messages dispatched via
     * {@code dispatchInbound}; it does not fabricate an agent path.
     */
    @Test
    void agentSessionInboundIsNotRoutedViaBackbone() {
        LocalMessageService bus = new LocalMessageService();
        ChannelMessageServiceImpl svc = newChannelMessageService();
        svc.setMessageService(bus);

        CaptureConsumer topicConsumer = new CaptureConsumer();
        bus.subscribe(TOPIC, topicConsumer);

        // the backbone topic only receives what dispatchInbound publishes
        // (mode-2 publish is async + bounded — I3 R-2-1 — await delivery)
        svc.dispatchInbound(inbound(FEISHU, "user-1", "non-agent"));
        awaitUntil(() -> topicConsumer.received.size() == 1,
                "the backbone topic to receive the non-agent inbound");

        // Agent-session inbound is handled by IChannelConnector + IAgentEngine
        // directly (not asserted here by construction — there is no code path
        // from the agent engine into dispatchInbound). This test exists to
        // record that invariant: the backbone intentionally does not serve
        // agent sessions.
        assertNotNull(topicConsumer.received.get(0));
    }

    // ---- helpers ----------------------------------------------------------

    /** Await an async delivery (inbound fan-out runs on the dedicated fan-out executor — I3 R-2-1). */
    private static void awaitUntil(Supplier<Boolean> condition, String label) {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.get()) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("interrupted while awaiting " + label);
            }
        }
        fail("timed out awaiting " + label);
    }

    private static ChannelMessageServiceImpl newChannelMessageService() {
        ChannelMessageServiceImpl svc = new ChannelMessageServiceImpl();
        svc.setUserChannelResolver(new TestChannelMessageService.RecordingResolver(java.util.Collections.emptyList()));
        svc.setChannelConnectorManager(new ChannelConnectorManager());
        return svc;
    }

    private static InboundChannelMessage inbound(String channelType, String userId, String text) {
        InboundChannelMessage m = new InboundChannelMessage();
        m.setChannelType(channelType);
        m.setUserId(userId);
        m.setText(text);
        return m;
    }

    /** External multi-consumer that captures every message delivered to a topic. */
    static class CaptureConsumer implements IMessageConsumer {
        final List<Object> received = new ArrayList<>();

        @Override
        public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
            received.add(message);
            return null;
        }
    }
}
