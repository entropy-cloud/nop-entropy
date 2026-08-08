package io.nop.integration.api.bind;

import io.nop.api.core.annotations.data.DataBean;

import java.sql.Timestamp;

/**
 * A short-lived, channel-specific bind ticket minted by an
 * {@link IChannelBindProvider} when a platform user starts binding a channel
 * account. The ticket is the bridge between "the platform asked the user to
 * scan a QR" and "the channel eventually reported back which channel-side
 * identity scanned it".
 *
 * <p><b>Lifecycle ownership</b>: each {@code IChannelBindProvider} owns the
 * ticket state (design §3.4 ②). There is no shared ticket store — a ticket
 * is only meaningful to the provider that minted it, and the
 * {@code IChannelBindService} never reads or writes ticket rows directly.
 *
 * <p><b>Fields</b>:
 * <ul>
 *   <li>{@code ticketId} — provider-assigned opaque id; the platform echoes
 *       it back in {@link ChannelScanCallback#getTicketId()} so the provider
 *       can rehydrate the ticket when the channel callback arrives.</li>
 *   <li>{@code qrPayload} — channel-native string to render as a QR code
 *       (e.g. a Feishu scan-login URL). The platform renders this with
 *       {@code IQrcodeService}.</li>
 *   <li>{@code expiresAt} — absolute deadline after which the ticket is
 *       {@link BindTicketStatus#EXPIRED}. The platform may use it to stop
 *       polling the channel callback endpoint.</li>
 *   <li>{@code status} — explicit {@link BindTicketStatus}, never
 *       {@code null}. A newly minted ticket starts at
 *       {@link BindTicketStatus#PENDING}.</li>
 * </ul>
 */
@DataBean
public class BindTicket {

    private String ticketId;
    private String qrPayload;
    private Timestamp expiresAt;
    private BindTicketStatus status;

    public String getTicketId() {
        return ticketId;
    }

    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
    }

    public String getQrPayload() {
        return qrPayload;
    }

    public void setQrPayload(String qrPayload) {
        this.qrPayload = qrPayload;
    }

    public Timestamp getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Timestamp expiresAt) {
        this.expiresAt = expiresAt;
    }

    public BindTicketStatus getStatus() {
        return status;
    }

    public void setStatus(BindTicketStatus status) {
        this.status = status;
    }
}
