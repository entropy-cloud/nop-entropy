package io.nop.ai.gateway.channel;

import io.nop.integration.api.channel.ChannelBinding;
import io.nop.integration.api.channel.IInboundMessageListener;
import io.nop.integration.api.channel.InboundChannelMessage;
import io.nop.integration.api.channel.OutboundChannelMessage;
import io.nop.integration.api.channel.SendResult;
import io.nop.integration.api.channel.UserChannelResolver;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        // both listeners received the exact message (real fan-out, not a no-op)
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

    // ---- helpers / stubs ---------------------------------------------------

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
}
