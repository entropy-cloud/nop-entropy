package io.nop.integration.api.bind;

import io.nop.api.core.annotations.data.DataBean;

import java.util.Map;

/**
 * Channel-agnostic carrier for an inbound channel scan event, handed by the
 * platform's scan-callback endpoint to
 * {@link IChannelBindProvider#onChannelScanCallback(ChannelScanCallback)}.
 *
 * <p>The platform normalises every vendor callback (Feishu push, DingTalk
 * redirect, etc.) into this shape before forwarding it to the provider. Each
 * {@code IChannelBindProvider} parses {@code rawPayload} according to its
 * own channel protocol — the platform never interprets vendor fields.
 *
 * <p><b>Fields</b>:
 * <ul>
 *   <li>{@code channelType} — the channel that produced this callback; must
 *       match the {@link IChannelBindProvider#getChannelType()} of the
 *       provider being invoked.</li>
 *   <li>{@code ticketId} — the id minted by
 *       {@link IChannelBindProvider#createBindTicket(String, String)} when
 *       the binding was started. Lets the provider rehydrate ticket state.
 *       May be {@code null} for channels that do not echo the ticket
 *       (provider then looks up the binding flow by other means).</li>
 *   <li>{@code rawPayload} — opaque vendor-specific body. Typed as a
 *       {@code Map<String,Object>} so the platform can pass through parsed
 *       JSON without binding to a vendor DTO; providers extract the fields
 *       they understand (e.g. Feishu {@code open_id}). A provider that
 *       prefers the raw string can find its serialised form under a
 *       well-known key negotiated with the platform callback endpoint.</li>
 * </ul>
 */
@DataBean
public class ChannelScanCallback {

    private String channelType;
    private String ticketId;
    private Map<String, Object> rawPayload;

    public String getChannelType() {
        return channelType;
    }

    public void setChannelType(String channelType) {
        this.channelType = channelType;
    }

    public String getTicketId() {
        return ticketId;
    }

    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
    }

    public Map<String, Object> getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(Map<String, Object> rawPayload) {
        this.rawPayload = rawPayload;
    }
}
