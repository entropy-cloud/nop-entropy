package io.nop.ai.gateway.channel;

import io.nop.integration.api.channel.IChannelMessageService;
import io.nop.integration.api.channel.IInboundMessageListener;
import io.nop.integration.api.channel.InboundChannelMessage;
import io.nop.integration.api.channel.OutboundChannelMessage;
import io.nop.integration.api.channel.SendResult;
import io.nop.integration.api.channel.UserChannelResolver;
import io.nop.integration.api.channel.ChannelBinding;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Business-driven channel message service implementation. This is the
 * "usage layer" bridge between the identity-anchored business API
 * ({@link IChannelMessageService}, {@link OutboundChannelMessage}) and the
 * transport layer ({@link IChannelConnector}, {@link ChannelOutboundMessage}).
 *
 * <p><b>Outbound path</b> ({@link #sendToUser}): resolve the recipient's
 * channel bindings via {@link UserChannelResolver} (anchored only on userId),
 * take the most-recently-active binding, look up its connector through
 * {@link ChannelConnectorManager}, convert the business message into the
 * transport carrier, and hand it to the connector. "No binding" and
 * "unsupported channel" are explicit {@link SendResult} values, never
 * exceptions and never silent drops (design §3.2).
 *
 * <p><b>Inbound path</b> ({@link #dispatchInbound}): a transport-layer
 * connector forwards a non-agent user message as an
 * {@link InboundChannelMessage}; the implementation fans it out to every
 * registered {@link IInboundMessageListener}. Agent-session inbound flows
 * back through the transport layer's event subscription directly and does
 * not pass through here (design §3.2).
 *
 * <p><b>Cross-channel degradation</b> (Feishu unbound &#8594; fall back to SMS)
 * is an explicit v1 non-goal. Only the most-recently-active binding is tried;
 * the caller inspects {@link SendResult} and decides its own fallback.
 */
public class ChannelMessageServiceImpl implements IChannelMessageService {

    private UserChannelResolver userChannelResolver;
    private ChannelConnectorManager channelConnectorManager;

    private final List<IInboundMessageListener> inboundListeners = new CopyOnWriteArrayList<>();

    @Inject
    public void setUserChannelResolver(UserChannelResolver userChannelResolver) {
        this.userChannelResolver = userChannelResolver;
    }

    @Inject
    public void setChannelConnectorManager(ChannelConnectorManager channelConnectorManager) {
        this.channelConnectorManager = channelConnectorManager;
    }

    @Override
    public SendResult sendToUser(String userId, OutboundChannelMessage message) {
        if (userId == null || userId.isEmpty() || message == null) {
            return SendResult.NO_BINDING;
        }

        // UserChannelResolver is injected as ioc:optional (the impl lives in
        // nop-auth-service, which a channel-less gateway deployment may not
        // include). A null resolver means no bindings can be resolved.
        if (userChannelResolver == null) {
            return SendResult.NO_BINDING;
        }

        List<ChannelBinding> bindings = userChannelResolver.resolve(userId);
        if (bindings == null || bindings.isEmpty()) {
            // no verified channel binding: explicit result, not an exception
            return SendResult.NO_BINDING;
        }

        // default outbound target = most-recently-active binding (resolver
        // returns bindings ordered by lastLoginTime DESC)
        ChannelBinding binding = bindings.get(0);

        IChannelConnector connector;
        try {
            connector = channelConnectorManager.lookup(binding.getChannelType());
        } catch (RuntimeException e) {
            // a binding exists but no connector is deployed for that channel
            // type — the channel form is not supported. ChannelConnectorManager
            // .lookup has a single throw path for the miss case, so any
            // RuntimeException from it means "no connector". Explicit result.
            return SendResult.UNSUPPORTED;
        }

        ChannelOutboundMessage carrier = toCarrier(message);
        connector.sendOutbound(binding.getChannelAddress(), carrier);
        return SendResult.SENT;
    }

    @Override
    public void subscribeInbound(IInboundMessageListener listener) {
        if (listener != null) {
            inboundListeners.add(listener);
        }
    }

    /**
     * Called by transport-layer connectors when a non-agent inbound message
     * is received. Fans the message out to every registered listener.
     */
    public void dispatchInbound(InboundChannelMessage message) {
        for (IInboundMessageListener listener : inboundListeners) {
            listener.onInbound(message);
        }
    }

    /**
     * Bridge the business-layer {@link OutboundChannelMessage} (identity-
     * anchored, lives in nop-integration-api) into the transport-layer
     * {@link ChannelOutboundMessage} carrier (protocol-adjacent, lives in
     * nop-ai-gateway). The two types are intentionally distinct (design §5 /
     * §3.2 adjudication) so nop-integration-api never depends on the AI
     * engine.
     */
    private ChannelOutboundMessage toCarrier(OutboundChannelMessage message) {
        ChannelOutboundMessage carrier = new ChannelOutboundMessage();
        carrier.setText(message.getText());
        carrier.setMarkdown(message.getMarkdown());
        carrier.setBusinessRef(message.getBusinessRef());

        List<ChannelOutboundMessage.Attachment> carrierAttachments =
                new ArrayList<>(message.getAttachments().size());
        for (OutboundChannelMessage.Attachment src : message.getAttachments()) {
            ChannelOutboundMessage.Attachment dst = new ChannelOutboundMessage.Attachment();
            dst.setName(src.getName());
            dst.setMimeType(src.getMimeType());
            dst.setUrl(src.getUrl());
            dst.setContent(src.getContent());
            carrierAttachments.add(dst);
        }
        carrier.setAttachments(carrierAttachments);
        return carrier;
    }
}
