package io.nop.integration.api.channel;

import io.nop.api.core.annotations.data.DataBean;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Business-layer inbound channel message — a channel-agnostic representation
 * of a message received from an external channel. The transport-layer
 * connector parses the native protocol, resolves the sender's platform userId
 * (when a binding exists), and hands an {@code InboundChannelMessage} to the
 * business message layer via {@code ChannelMessageServiceImpl.dispatchInbound},
 * which fans it out to all registered {@link IInboundMessageListener}s.
 *
 * <p>This type is distinct from the agent-session inbound path: agent replies
 * flow back through the transport layer's {@code IAgentEventPublisher}
 * subscription directly (design §3.2), not through this type. This type serves
 * non-agent inbound (user messages that trigger workflows / audit rather than
 * agent reasoning).
 *
 * <p>Fields (design {@code nop-ai-channel-integration-design.md} §3.2):
 * <ul>
 *   <li>{@code userId} — the resolved platform userId if a binding exists;
 *       {@code null} when the sender is not bound.</li>
 *   <li>{@code channelType} — which channel the message came from
 *       (e.g. {@code "feishu"}).</li>
 *   <li>{@code channelAddress} — the channel-native sender address
 *       (e.g. a Feishu {@code open_id}).</li>
 *   <li>{@code text} — the message text body.</li>
 *   <li>{@code rawAttachments} — raw attachment references as received from
 *       the channel.</li>
 *   <li>{@code receivedAt} — when the connector received the message.</li>
 * </ul>
 */
@DataBean
public class InboundChannelMessage {

    private String userId;
    private String channelType;
    private String channelAddress;
    private String text;
    private List<OutboundChannelMessage.Attachment> rawAttachments = new ArrayList<>();
    private Timestamp receivedAt;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getChannelType() {
        return channelType;
    }

    public void setChannelType(String channelType) {
        this.channelType = channelType;
    }

    public String getChannelAddress() {
        return channelAddress;
    }

    public void setChannelAddress(String channelAddress) {
        this.channelAddress = channelAddress;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public List<OutboundChannelMessage.Attachment> getRawAttachments() {
        return rawAttachments;
    }

    public void setRawAttachments(List<OutboundChannelMessage.Attachment> rawAttachments) {
        this.rawAttachments = rawAttachments != null ? rawAttachments : new ArrayList<>();
    }

    public Timestamp getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Timestamp receivedAt) {
        this.receivedAt = receivedAt;
    }
}
