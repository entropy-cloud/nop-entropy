package io.nop.integration.feishu.client;

import io.nop.api.core.annotations.data.DataBean;

/**
 * Inbound Feishu message delivered to an {@link IMessageHandler} when a
 * {@code im.message.receive_v1} DATA frame arrives over the Stream
 * long-connection.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code receiveIdType} — Feishu receive-id scope: {@code chat_id},
 *       {@code open_id}, {@code user_id}, {@code union_id}.</li>
 *   <li>{@code receiveId} — the actual receive id (the address to reply to,
 *       e.g. a {@code chat_id}).</li>
 *   <li>{@code msgType} — Feishu message type: {@code text}, {@code post},
 *       {@code image}, ...</li>
 *   <li>{@code content} — message body (for {@code text} messages, a JSON
 *       string like {@code {"text":"..."}}).</li>
 *   <li>{@code senderId} — the sender's id (open_id).</li>
 *   <li>{@code chatType} — {@code p2p} (direct message) or {@code group}.</li>
 *   <li>{@code rawPayload} — the original DATA-frame payload bytes, preserved
 *       so downstream consumers (Plan 7 {@code FeishuConnector}) can perform
 *       full event-shape deserialization.</li>
 * </ul>
 */
@DataBean
public class FeishuInboundMessage {

    private String receiveIdType;
    private String receiveId;
    private String msgType;
    private String content;
    private String senderId;
    private String chatType;
    private byte[] rawPayload;

    public String getReceiveIdType() {
        return receiveIdType;
    }

    public void setReceiveIdType(String receiveIdType) {
        this.receiveIdType = receiveIdType;
    }

    public String getReceiveId() {
        return receiveId;
    }

    public void setReceiveId(String receiveId) {
        this.receiveId = receiveId;
    }

    public String getMsgType() {
        return msgType;
    }

    public void setMsgType(String msgType) {
        this.msgType = msgType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getChatType() {
        return chatType;
    }

    public void setChatType(String chatType) {
        this.chatType = chatType;
    }

    public byte[] getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(byte[] rawPayload) {
        this.rawPayload = rawPayload;
    }
}
