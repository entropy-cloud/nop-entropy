package io.nop.ai.gateway.channel;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.integration.api.channel.ChannelBinding;
import io.nop.integration.api.channel.IChannelMessageService;
import io.nop.integration.api.channel.IInboundMessageListener;
import io.nop.integration.api.channel.InboundChannelMessage;
import io.nop.integration.api.channel.OutboundChannelMessage;
import io.nop.integration.api.channel.SendResult;
import io.nop.integration.api.channel.UserChannelResolver;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.nop.api.core.ApiErrors.ERR_CHECK_INVALID_ARGUMENT;

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
 * <p><b>Inbound deployment modes</b> (design §3.3 问题 B — W6-4):
 * <ul>
 *   <li><b>Mode 1 (direct, default)</b>: when no {@link IMessageService} is
 *       injected, {@link #dispatchInbound} fans the message out synchronously
 *       to all registered listeners. This is the single-JVM deployment form
 *       and its behavior is byte-for-byte identical to the pre-mode-2
 *       implementation.</li>
 *   <li><b>Mode 2 (optional backbone)</b>: when an {@link IMessageService} is
 *       injected (e.g. the platform {@code nopLocalMessageService}, or a
 *       Kafka/Pulsar implementation for horizontal scale),
 *       {@link #dispatchInbound} publishes the message to the
 *       {@code channel.inbound.{channelType}} topic. A singleton internal
 *       <b>bridge</b> consumer — subscribed once per topic — fans the message
 *       out to all registered listeners (reusing the mode-1 fan-out logic),
 *       and external multi-consumers (audit / workflow) may subscribe to the
 *       same topic directly. {@link #subscribeInbound} is the same in both
 *       modes: it only adds the listener to the internal list and is unaware
 *       of topics. The mode switch is a pure deployment-assembly choice and
 *       is invisible to callers.</li>
 * </ul>
 *
 * <p><b>Cross-channel degradation</b> (Feishu unbound &#8594; fall back to SMS)
 * is an explicit v1 non-goal. Only the most-recently-active binding is tried;
 * the caller inspects {@link SendResult} and decides its own fallback.
 */
public class ChannelMessageServiceImpl implements IChannelMessageService {

    /**
     * Topic prefix for the optional inbound backbone (design §3.3 问题 B,
     * W6-4). The full topic is {@link #INBOUND_TOPIC_PREFIX} + channelType
     * (e.g. {@code channel.inbound.feishu}), so each channel type gets its
     * own topic and can have an independent consumer group / rate.
     */
    static final String INBOUND_TOPIC_PREFIX = "channel.inbound.";

    private UserChannelResolver userChannelResolver;
    private ChannelConnectorManager channelConnectorManager;

    /**
     * Optional platform message backbone (mode 2). When null the inbound path
     * uses direct synchronous fan-out (mode 1, the default); when non-null it
     * publishes to {@link #INBOUND_TOPIC_PREFIX}{channelType}. Injected
     * optionally ({@code ioc:optional="true"} in beans.xml) so a deployment
     * that does not include an {@link IMessageService} implementation still
     * starts in mode 1.
     */
    private IMessageService messageService;

    /**
     * Topics for which the {@link #bridgeConsumer} has been registered.
     * Subscribe-before-record ordering (see {@link #ensureBridgeSubscribed})
     * keeps this consistent with the live subscriptions.
     */
    private final Set<String> subscribedTopics = ConcurrentHashMap.newKeySet();

    /**
     * Singleton bridge consumer: subscribes to a per-channelType inbound topic
     * and fans each message out to the registered listeners. It MUST be a
     * single instance (not a per-call lambda) so that
     * {@code LocalMessageService#subscribe} deduplicates by consumer identity
     * and concurrent first-dispatch of the same channelType never creates a
     * duplicate subscription / duplicate fan-out.
     *
     * <p>{@link #onMessage} returns {@code null} deliberately:
     * {@code LocalMessageService#handleMessageResult} forwards any non-null
     * return value to {@code ack-{topic}}, which would form an ack loop.
     * Returning null triggers its {@code ignore-message-when-no-reply} branch.
     */
    private final IMessageConsumer bridgeConsumer = new IMessageConsumer() {
        @Override
        public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
            if (message instanceof InboundChannelMessage) {
                fanOutToListeners((InboundChannelMessage) message);
            }
            return null;
        }
    };

    @Inject
    public void setUserChannelResolver(@Nullable UserChannelResolver userChannelResolver) {
        this.userChannelResolver = userChannelResolver;
    }

    @Inject
    public void setChannelConnectorManager(ChannelConnectorManager channelConnectorManager) {
        this.channelConnectorManager = channelConnectorManager;
    }

    /**
     * Optional injection of the platform message backbone. The container
     * resolves this to null when no {@link IMessageService} bean is deployed
     * (mode 1) or to the real implementation (e.g.
     * {@code nopLocalMessageService}) when one is present (mode 2). The
     * explicit {@code <ref bean="nopLocalMessageService" ioc:optional="true">}
     * in beans.xml selects the exact platform bean (avoiding by-type ambiguity
     * when multiple IMessageService implementations coexist), while
     * {@link Nullable} makes the by-type fallback null-tolerant so a
     * deployment without any IMessageService implementation still starts.
     */
    @Inject
    public void setMessageService(@Nullable IMessageService messageService) {
        this.messageService = messageService;
    }

    private final List<IInboundMessageListener> inboundListeners = new CopyOnWriteArrayList<>();

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
     * is received. In mode 1 (no {@link IMessageService}) the message is
     * fanned out synchronously to every registered listener; in mode 2
     * (backbone assembled) it is published to
     * {@code channel.inbound.{channelType}}, from which the singleton bridge
     * consumer fans it out to listeners and external multi-consumers also
     * receive it. The two modes are a pure deployment-assembly choice; this
     * entry point is called identically in both.
     */
    public void dispatchInbound(InboundChannelMessage message) {
        if (messageService == null) {
            // Mode 1 (direct): byte-for-byte identical to the pre-mode-2
            // behavior — including the no-null-check contract (a null message
            // is forwarded as-is to listeners, unchanged).
            fanOutToListeners(message);
            return;
        }

        // Mode 2 (backbone): validate the routing key explicitly. A null/empty
        // channelType must fail loudly rather than silently publishing to a
        // bogus topic like "channel.inbound.null".
        String topic = inboundTopicFor(message.getChannelType());
        ensureBridgeSubscribed(topic);
        messageService.send(topic, message);
    }

    /**
     * Shared fan-out used by both mode-1 {@link #dispatchInbound} and the
     * mode-2 {@link #bridgeConsumer}. Keeping it in one place ensures the
     * two modes cannot drift apart.
     */
    private void fanOutToListeners(InboundChannelMessage message) {
        for (IInboundMessageListener listener : inboundListeners) {
            listener.onInbound(message);
        }
    }

    private static String inboundTopicFor(String channelType) {
        if (channelType == null || channelType.isEmpty()) {
            throw new NopException(ERR_CHECK_INVALID_ARGUMENT).param("msg",
                    "backbone inbound dispatch requires a non-empty channelType for topic routing");
        }
        return INBOUND_TOPIC_PREFIX + channelType;
    }

    /**
     * Register the singleton {@link #bridgeConsumer} for a topic on first use.
     * Subscribe is performed BEFORE the topic is recorded so that any caller
     * which later observes the topic in {@link #subscribedTopics} also
     * observes the live subscription (subscribe-before-publish, no message
     * loss). Repeated subscribe calls are harmless:
     * {@code LocalMessageService#subscribe} deduplicates by consumer identity,
     * so concurrent first-dispatch of the same channelType yields exactly one
     * subscription and never a duplicate fan-out.
     */
    private void ensureBridgeSubscribed(String topic) {
        if (subscribedTopics.contains(topic)) {
            return;
        }
        messageService.subscribe(topic, bridgeConsumer);
        subscribedTopics.add(topic);
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
