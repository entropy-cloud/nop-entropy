package io.nop.ai.gateway.channel;

import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.integration.api.channel.ChannelBinding;
import io.nop.integration.api.channel.IInboundMessageListener;
import io.nop.integration.api.channel.InboundChannelMessage;
import io.nop.integration.api.channel.OutboundChannelMessage;
import io.nop.integration.api.channel.SendResult;
import io.nop.integration.api.channel.UserChannelResolver;
import io.nop.message.core.local.LocalMessageService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Phase 3 tests for {@link ChannelMessageServiceImpl}. Proves the full
 * outbound routing chain is connected at runtime
 * (resolver &#8594; manager &#8594; connector) and that the explicit
 * {@link SendResult} semantics hold — not just that the types compile.
 *
 * <p><b>Anti-Hollow / wiring verification (Minimum Rules #22, #23)</b>:
 * <ul>
 *   <li>{@code sendToUser(bound)} asserts the stub connector's
 *       {@code sendOutbound} call count advances and that it received the
 *       converted transport carrier + the binding's channel address — proving
 *       the manager lookup and the business&#8594;transport bridge are real.</li>
 *   <li>{@code sendToUser(unbound)} asserts the connector's
 *       {@code sendOutbound} is NEVER invoked and the result is
 *       {@link SendResult#NO_BINDING} — proving no silent drop.</li>
 *   <li>{@code dispatchInbound} asserts a registered listener is actually
 *       called with the inbound message.</li>
 * </ul>
 */
public class TestChannelMessageService {

    private static final String USER_ID = "user-1";
    private static final String FEISHU_ADDR = "feishu-open-id";

    /**
     * Await an async condition (inbound fan-out now runs on the dedicated
     * fan-out executor — I3 R-2-1) with a bounded deadline; fails the test
     * when the condition never holds.
     */
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

    /** Give any would-be (wrong) delivery a chance to land before asserting absence. */
    private static void settle() {
        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void sendToUserBoundRoutesToConnectorAndReturnsSent() {
        RecordingResolver resolver = new RecordingResolver(
                Collections.singletonList(new ChannelBinding(USER_ID, "feishu", FEISHU_ADDR)));
        RecordingConnector connector = new RecordingConnector("feishu");
        ChannelConnectorManager manager = managerWith(connector);
        ChannelMessageServiceImpl svc = newService(resolver, manager);

        OutboundChannelMessage msg = new OutboundChannelMessage();
        msg.setText("hello");
        msg.setMarkdown("# hi");
        msg.setBusinessRef("notif-1");

        SendResult result = svc.sendToUser(USER_ID, msg);

        assertEquals(SendResult.SENT, result);
        // the connector was REALLY invoked (call count > 0)
        assertEquals(1, connector.sendOutboundCount, "connector.sendOutbound must be called");
        // it received the binding's channel address
        assertEquals(FEISHU_ADDR, connector.lastAddress,
                "sendOutbound must receive the resolved channel address");
        // it received the converted transport carrier with bridged content
        ChannelOutboundMessage carrier = connector.lastMessage;
        assertEquals("hello", carrier.getText());
        assertEquals("# hi", carrier.getMarkdown());
        assertEquals("notif-1", carrier.getBusinessRef());
    }

    @Test
    void sendToUserUnboundReturnsNoBindingAndNeverCallsConnector() {
        RecordingResolver resolver = new RecordingResolver(Collections.emptyList());
        RecordingConnector connector = new RecordingConnector("feishu");
        ChannelConnectorManager manager = managerWith(connector);
        ChannelMessageServiceImpl svc = newService(resolver, manager);

        SendResult result = svc.sendToUser(USER_ID, msg("hi"));

        assertEquals(SendResult.NO_BINDING, result);
        assertEquals(0, connector.sendOutboundCount,
                "connector.sendOutbound must NEVER be called when there is no binding");
    }

    @Test
    void sendToUserWithBindingButNoConnectorReturnsUnsupported() {
        // the user has a feishu binding, but no feishu connector is deployed
        RecordingResolver resolver = new RecordingResolver(
                Collections.singletonList(new ChannelBinding(USER_ID, "feishu", FEISHU_ADDR)));
        RecordingConnector connector = new RecordingConnector("feishu");
        // manager has a DINGTALK connector, not feishu
        ChannelConnectorManager manager = managerWith(new RecordingConnector("dingtalk"));
        ChannelMessageServiceImpl svc = newService(resolver, manager);

        SendResult result = svc.sendToUser(USER_ID, msg("hi"));

        assertEquals(SendResult.UNSUPPORTED, result);
        assertEquals(0, connector.sendOutboundCount,
                "connector.sendOutbound must not be called for an unsupported channel");
    }

    @Test
    void sendToUserUsesMostRecentlyActiveBindingWhenMultiple() {
        // resolver returns bindings ordered most-recent-first (feishu, dingtalk)
        RecordingResolver resolver = new RecordingResolver(Arrays.asList(
                new ChannelBinding(USER_ID, "feishu", FEISHU_ADDR),
                new ChannelBinding(USER_ID, "dingtalk", "ding-1")));
        RecordingConnector feishu = new RecordingConnector("feishu");
        RecordingConnector ding = new RecordingConnector("dingtalk");
        ChannelConnectorManager manager = managerWith(feishu, ding);
        ChannelMessageServiceImpl svc = newService(resolver, manager);

        SendResult result = svc.sendToUser(USER_ID, msg("hi"));

        assertEquals(SendResult.SENT, result);
        // the FIRST (most-recently-active) binding wins; no cross-channel fallback
        assertEquals(1, feishu.sendOutboundCount);
        assertEquals(0, ding.sendOutboundCount, "must not try the secondary binding (no fallback)");
    }

    @Test
    void sendToUserNullResolverReturnsNoBinding() {
        // a channel-less deployment where UserChannelResolver is optional-injected null
        ChannelConnectorManager manager = managerWith(new RecordingConnector("feishu"));
        ChannelMessageServiceImpl svc = newService(null, manager);

        assertEquals(SendResult.NO_BINDING, svc.sendToUser(USER_ID, msg("hi")));
    }

    @Test
    void dispatchInboundFansOutToAllRegisteredListeners() {
        ChannelMessageServiceImpl svc = newService(
                new RecordingResolver(Collections.emptyList()), new ChannelConnectorManager());

        RecordingListener a = new RecordingListener();
        RecordingListener b = new RecordingListener();
        svc.subscribeInbound(a);
        svc.subscribeInbound(b);

        InboundChannelMessage inbound = new InboundChannelMessage();
        inbound.setUserId(USER_ID);
        inbound.setText("from channel");

        svc.dispatchInbound(inbound);

        // both listeners received the exact message (real fan-out, not a no-op).
        // Fan-out runs on the dedicated executor (I3 R-2-1) — await delivery.
        awaitUntil(() -> a.received.size() == 1 && b.received.size() == 1,
                "both listeners to receive the inbound message");
        assertEquals(1, a.received.size());
        assertEquals(1, b.received.size());
        assertSame(inbound, a.received.get(0));
        assertSame(inbound, b.received.get(0));
    }

    @Test
    void attachmentBridgeCarriesAllFields() {
        RecordingResolver resolver = new RecordingResolver(
                Collections.singletonList(new ChannelBinding(USER_ID, "feishu", FEISHU_ADDR)));
        RecordingConnector connector = new RecordingConnector("feishu");
        ChannelMessageServiceImpl svc = newService(resolver, managerWith(connector));

        OutboundChannelMessage.Attachment att = new OutboundChannelMessage.Attachment();
        att.setName("report.pdf");
        att.setMimeType("application/pdf");
        att.setUrl("https://example.com/report.pdf");

        OutboundChannelMessage msg = new OutboundChannelMessage();
        msg.setText("see attachment");
        msg.setAttachments(Collections.singletonList(att));

        svc.sendToUser(USER_ID, msg);

        assertEquals(1, connector.lastMessage.getAttachments().size());
        ChannelOutboundMessage.Attachment bridged = connector.lastMessage.getAttachments().get(0);
        assertEquals("report.pdf", bridged.getName());
        assertEquals("application/pdf", bridged.getMimeType());
        assertEquals("https://example.com/report.pdf", bridged.getUrl());
    }

    // ---- inbound backbone (mode 2) tests ----------------------------------

    /**
     * Mode 2: dispatchInbound publishes to channel.inbound.{channelType}.
     * An external consumer subscribed to the feishu topic receives; a
     * consumer on a different channelType topic does not. Proves the real
     * IMessageService.send path and correct topic routing.
     */
    @Test
    void backboneDispatchPublishesToPerChannelTypeTopic() {
        LocalMessageService bus = new LocalMessageService();
        ChannelMessageServiceImpl svc = newBackboneService(bus);

        RecordingMessageConsumer feishuAudit = new RecordingMessageConsumer();
        RecordingMessageConsumer dingtalkAudit = new RecordingMessageConsumer();
        bus.subscribe("channel.inbound.feishu", feishuAudit);
        bus.subscribe("channel.inbound.dingtalk", dingtalkAudit);

        InboundChannelMessage inbound = inbound("feishu", "user-1", "hi");
        svc.dispatchInbound(inbound);

        // the feishu topic consumer really received the published message
        // (mode-2 publish is async + bounded — I3 R-2-1 — await delivery)
        awaitUntil(() -> feishuAudit.received.size() == 1,
                "send to reach channel.inbound.feishu");
        assertSame(inbound, feishuAudit.received.get(0));
        // routing is per-channelType: the dingtalk topic must not receive it
        settle();
        assertTrue(dingtalkAudit.received.isEmpty(),
                "a feishu message must NOT leak to channel.inbound.dingtalk");
    }

    /**
     * Mode 2 multi-consumer: both the internal business listener (registered
     * via subscribeInbound, reached through the bridge) AND an external audit
     * consumer (subscribed directly to the topic) receive the same message.
     * This is the multi-consumer capability that mode 1 cannot provide.
     */
    @Test
    void backboneFansOutToInternalListenerAndExternalConsumer() {
        LocalMessageService bus = new LocalMessageService();
        ChannelMessageServiceImpl svc = newBackboneService(bus);

        RecordingListener business = new RecordingListener();
        svc.subscribeInbound(business);

        RecordingMessageConsumer audit = new RecordingMessageConsumer();
        bus.subscribe("channel.inbound.feishu", audit);

        InboundChannelMessage inbound = inbound("feishu", "user-1", "audit-me");
        svc.dispatchInbound(inbound);

        // internal listener reached via the bridge fan-out (async, I3 R-2-1)
        awaitUntil(() -> business.received.size() == 1 && audit.received.size() == 1,
                "business listener and external audit consumer to receive");
        assertSame(inbound, business.received.get(0));
        // external audit consumer reached directly from the topic
        assertEquals(1, audit.received.size(), "external audit consumer must receive from topic");
        assertSame(inbound, audit.received.get(0));
    }

    /**
     * Mode 2 ack-loop guard: the bridge onMessage returns null, so
     * LocalMessageService.handleMessageResult takes its no-reply branch and
     * never forwards to ack-channel.inbound.feishu.
     */
    @Test
    void backboneBridgeReturnsNullSoNoAckLoop() {
        LocalMessageService bus = new LocalMessageService();
        ChannelMessageServiceImpl svc = newBackboneService(bus);

        RecordingListener business = new RecordingListener();
        svc.subscribeInbound(business);

        // a consumer on the ack topic — it must stay empty
        RecordingMessageConsumer ackSink = new RecordingMessageConsumer();
        bus.subscribe("ack-channel.inbound.feishu", ackSink);

        svc.dispatchInbound(inbound("feishu", "user-1", "no-ack"));

        awaitUntil(() -> business.received.size() == 1, "business listener received (bridge worked)");
        settle();
        assertTrue(ackSink.received.isEmpty(),
                "bridge must return null so nothing is forwarded to the ack topic (no loop)");
    }

    /**
     * Mode 2 concurrency: the bridge is a single instance, so under concurrent
     * first-dispatch of the same channelType only ONE subscription is ever
     * registered (LocalMessageService dedups by consumer identity) and each
     * message is fanned out exactly once (no duplicate delivery).
     */
    @Test
    void backboneConcurrentFirstDispatchDedupsBridgeNoDuplicateFanOut() throws Exception {
        LocalMessageService bus = new LocalMessageService();
        ChannelMessageServiceImpl svc = newBackboneService(bus);
        CountingListener listener = new CountingListener();
        svc.subscribeInbound(listener);

        int n = 64;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch fire = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            final int idx = i;
            futures.add(pool.submit(() -> {
                fire.await();
                svc.dispatchInbound(inbound("feishu", "user-1", "m" + idx));
                return null;
            }));
        }
        fire.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        // exactly one bridge subscription for the topic (singleton + dedup)
        List<?> subscriptions = bus.getConsumers().get("channel.inbound.feishu");
        assertNotNull(subscriptions, "bridge subscription must exist for the topic");
        assertEquals(1, subscriptions.size(),
                "concurrent first-dispatch must yield a single bridge subscription (deduped)");

        // each of the n messages delivered exactly once (no duplicate fan-out).
        // The bridge fan-out is async (I3 R-2-1) — await the final count.
        awaitUntil(() -> listener.count.get() == n,
                "exactly n deliveries under concurrent first-dispatch");
    }

    /**
     * Mode 2 no-silent-skip: a missing channelType cannot silently route to a
     * bogus "channel.inbound.null" topic — it must fail loudly.
     */
    @Test
    void backboneRejectsNullChannelType() {
        ChannelMessageServiceImpl svc = newBackboneService(new LocalMessageService());
        InboundChannelMessage m = new InboundChannelMessage();
        m.setUserId("u");
        // channelType left null
        assertThrows(RuntimeException.class, () -> svc.dispatchInbound(m),
                "backbone dispatch with null channelType must fail explicitly");
    }

    @Test
    void backboneRejectsEmptyChannelType() {
        ChannelMessageServiceImpl svc = newBackboneService(new LocalMessageService());
        InboundChannelMessage m = inbound("", "u", "x");
        assertThrows(RuntimeException.class, () -> svc.dispatchInbound(m),
                "backbone dispatch with empty channelType must fail explicitly");
    }

    /**
     * Mode 1 non-regression: when no IMessageService is injected, dispatchInbound
     * takes the direct branch — synchronous fan-out, no topic publish. The
     * existing dispatchInboundFansOutToAllRegisteredListeners test also covers
     * this; here the direct branch is asserted explicitly against a live bus
     * that would otherwise receive the publish.
     */
    @Test
    void dispatchInboundUsesDirectBranchWhenNoMessageService() {
        LocalMessageService bus = new LocalMessageService();
        // observe the bus: if mode 2 were (wrongly) active it would publish here
        RecordingMessageConsumer audit = new RecordingMessageConsumer();
        bus.subscribe("channel.inbound.feishu", audit);

        // no setMessageService(...) call -> mode 1 (messageService == null)
        ChannelMessageServiceImpl svc = newService(
                new RecordingResolver(Collections.emptyList()), new ChannelConnectorManager());
        RecordingListener a = new RecordingListener();
        RecordingListener b = new RecordingListener();
        svc.subscribeInbound(a);
        svc.subscribeInbound(b);

        InboundChannelMessage inbound = inbound("feishu", "user-1", "direct");
        svc.dispatchInbound(inbound);

        // direct bounded fan-out happened (async on the fan-out executor —
        // I3 R-2-1 — await delivery)
        awaitUntil(() -> a.received.size() == 1 && b.received.size() == 1,
                "mode-1 direct fan-out delivery");
        assertSame(inbound, a.received.get(0));
        // the bus was never used — no publish occurred
        settle();
        assertTrue(audit.received.isEmpty(),
                "mode 1 must NOT publish to the backbone topic");
    }

    // ---- helpers / stubs ---------------------------------------------------

    private static InboundChannelMessage inbound(String channelType, String userId, String text) {
        InboundChannelMessage m = new InboundChannelMessage();
        m.setChannelType(channelType);
        m.setUserId(userId);
        m.setText(text);
        return m;
    }

    private static ChannelMessageServiceImpl newBackboneService(LocalMessageService bus) {
        ChannelMessageServiceImpl svc = new ChannelMessageServiceImpl();
        svc.setUserChannelResolver(new RecordingResolver(Collections.emptyList()));
        svc.setChannelConnectorManager(new ChannelConnectorManager());
        svc.setMessageService(bus);
        return svc;
    }

    private static OutboundChannelMessage msg(String text) {
        OutboundChannelMessage m = new OutboundChannelMessage();
        m.setText(text);
        return m;
    }

    private static ChannelConnectorManager managerWith(IChannelConnector... connectors) {
        ChannelConnectorManager manager = new ChannelConnectorManager();
        for (IChannelConnector c : connectors) {
            manager.register(c);
        }
        return manager;
    }

    private static ChannelMessageServiceImpl newService(UserChannelResolver resolver,
                                                        ChannelConnectorManager manager) {
        ChannelMessageServiceImpl svc = new ChannelMessageServiceImpl();
        svc.setUserChannelResolver(resolver);
        svc.setChannelConnectorManager(manager);
        return svc;
    }

    /** Recording resolver: returns a pre-configured binding list. */
    static class RecordingResolver implements UserChannelResolver {
        final List<ChannelBinding> bindings;

        RecordingResolver(List<ChannelBinding> bindings) {
            this.bindings = bindings;
        }

        @Override
        public List<ChannelBinding> resolve(String userId) {
            return bindings;
        }

        @Override
        public ChannelBinding resolve(String userId, String channelType) {
            for (ChannelBinding b : bindings) {
                if (channelType.equals(b.getChannelType())) {
                    return b;
                }
            }
            return null;
        }
    }

    /** Recording connector: captures sendOutbound call count + last args. */
    static class RecordingConnector implements IChannelConnector {
        final String type;
        int sendOutboundCount;
        String lastAddress;
        ChannelOutboundMessage lastMessage;

        RecordingConnector(String type) {
            this.type = type;
        }

        @Override
        public String getChannelType() {
            return type;
        }

        @Override
        public void start(ChannelConnectorContext context) {
            // unused
        }

        @Override
        public void stop() {
            // unused
        }

        @Override
        public ChannelCapabilities getCapabilities() {
            return new ChannelCapabilities();
        }

        @Override
        public void sendOutbound(String channelAddress, ChannelOutboundMessage outboundMessage) {
            this.sendOutboundCount++;
            this.lastAddress = channelAddress;
            this.lastMessage = outboundMessage;
        }
    }

    static class RecordingListener implements IInboundMessageListener {
        final List<InboundChannelMessage> received = new ArrayList<>();

        @Override
        public void onInbound(InboundChannelMessage message) {
            received.add(message);
        }
    }

    /**
     * Thread-safe counting listener for the concurrency test. Records total
     * deliveries across all threads; does not retain messages (avoiding
     * contention on a shared list).
     */
    static class CountingListener implements IInboundMessageListener {
        final AtomicInteger count = new AtomicInteger();

        @Override
        public void onInbound(InboundChannelMessage message) {
            count.incrementAndGet();
        }
    }

    /**
     * Recording IMessageConsumer for the backbone tests. Captures every
     * delivered message object and returns {@code null} so it never triggers
     * an ack-topic forward itself (keeping ack-loop assertions clean).
     */
    static class RecordingMessageConsumer implements IMessageConsumer {
        final List<Object> received = new ArrayList<>();

        @Override
        public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
            received.add(message);
            return null;
        }
    }
}
