package io.nop.ai.gateway.channel;

import java.util.ArrayList;
import java.util.List;

/**
 * Transport-layer outbound message carrier — the payload a connector's
 * {@link IChannelConnector#sendOutbound(String, ChannelOutboundMessage)}
 * receives and translates into the channel's native send API.
 *
 * <p><b>Layering</b>: this type lives in the transport layer
 * ({@code nop-ai-gateway}) and intentionally does <b>not</b> depend on
 * {@code nop-integration-api}. The business message layer's
 * {@code OutboundChannelMessage} (to be introduced in Plan 3 / W2, interface
 * in {@code nop-integration-api}) is a distinct, identity-anchored type; the
 * business {@code ChannelMessageServiceImpl} is responsible for bridging the
 * business type into this transport carrier. This adjudication closes the gap
 * between the transport design (§5 — the connector interface originally had
 * no send method) and the business design (§3.2 — which assumed connectors
 * can send).
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code text} — plain-text fallback, always safe to send</li>
 *   <li>{@code markdown} — optional Markdown rendering; connectors whose
 *       {@link ChannelCapabilities#isSupportsMarkdown()} is false should fall
 *       back to {@code text}</li>
 *   <li>{@code attachments} — optional file/image references (url + name +
 *       mime type); connectors whose capabilities do not support file upload
 *       should reject or downgrade</li>
 *   <li>{@code businessRef} — optional correlation key for the business
 *       caller (e.g. a notification id), opaque to the transport layer</li>
 * </ul>
 */
public class ChannelOutboundMessage {

    private String text;
    private String markdown;
    private List<Attachment> attachments = new ArrayList<>();
    private String businessRef;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getMarkdown() {
        return markdown;
    }

    public void setMarkdown(String markdown) {
        this.markdown = markdown;
    }

    public List<Attachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<Attachment> attachments) {
        this.attachments = attachments != null ? attachments : new ArrayList<>();
    }

    public String getBusinessRef() {
        return businessRef;
    }

    public void setBusinessRef(String businessRef) {
        this.businessRef = businessRef;
    }

    /**
     * A single attachment reference carried by an outbound message. The
     * connector resolves {@code url} (or inline {@code content}) against the
     * channel's upload/download API according to its capabilities.
     */
    public static class Attachment {
        private String name;
        private String mimeType;
        private String url;
        private byte[] content;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getMimeType() {
            return mimeType;
        }

        public void setMimeType(String mimeType) {
            this.mimeType = mimeType;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public byte[] getContent() {
            return content;
        }

        public void setContent(byte[] content) {
            this.content = content;
        }
    }
}
