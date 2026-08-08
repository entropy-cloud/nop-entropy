package io.nop.integration.api.channel;

import io.nop.api.core.annotations.data.DataBean;

import java.util.ArrayList;
import java.util.List;

/**
 * Business-layer outbound channel message — the payload a business caller
 * hands to {@link IChannelMessageService#sendToUser}. This type is
 * <b>identity-anchored</b>: it carries only message content plus an optional
 * business correlation key, never any channel-protocol field (no chat_id /
 * open_id). The business message implementation is responsible for resolving
 * the recipient's channel binding and bridging this type into the
 * transport-layer outbound carrier.
 *
 * <p>Fields (design {@code nop-ai-channel-integration-design.md} §3.2):
 * <ul>
 *   <li>{@code text} — plain-text body, always safe to send.</li>
 *   <li>{@code markdown} — optional Markdown rendering; connectors that do
 *       not support Markdown fall back to {@code text}.</li>
 *   <li>{@code attachments} — optional file/image references.</li>
 *   <li>{@code businessRef} — optional correlation key for the business
 *       caller (e.g. a notification id), opaque to the channel layer.</li>
 * </ul>
 */
@DataBean
public class OutboundChannelMessage {

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
     * channel connector resolves {@code url} (or inline {@code content})
     * against its native upload/download API according to its capabilities.
     */
    @DataBean
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
