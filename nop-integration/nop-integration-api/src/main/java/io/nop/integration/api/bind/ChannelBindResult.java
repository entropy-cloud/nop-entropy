package io.nop.integration.api.bind;

import io.nop.api.core.annotations.data.DataBean;

/**
 * Result of {@link IChannelBindProvider#onChannelScanCallback(
 * ChannelScanCallback)}: the channel-side user identity (e.g. a Feishu
 * {@code open_id}) plus the linkage the provider was able to rehydrate
 * from the ticket.
 *
 * <p>The platform reads {@link #getExtId()} and writes it as
 * {@code NopAuthExtLogin.extId} via {@code IChannelBindService.completeBinding}.
 *
 * <p><b>Fields</b>:
 * <ul>
 *   <li>{@code extId} — channel-side user identity, the long-term stable id
 *       the channel reports for the user who scanned the QR (Feishu
 *       {@code open_id}, DingTalk {@code unionId}, etc.). Never
 *       {@code null} when {@link #getStatus()} is
 *       {@link ChannelBindResultStatus#BINDING_COMPLETED} or
 *       {@link ChannelBindResultStatus#ALREADY_BOUND}.</li>
 *   <li>{@code platformUserId} — the platform user who started the ticket,
 *       rehydrated by the provider from the ticket. May be {@code null} if
 *       the provider could not recover the ticket (e.g. ticket expired
 *       before the channel callback arrived).</li>
 *   <li>{@code ticketId} — echoed back so the platform can correlate the
 *       result with the started binding flow.</li>
 *   <li>{@code status} — explicit {@link ChannelBindResultStatus}; never
 *       {@code null}.</li>
 * </ul>
 */
@DataBean
public class ChannelBindResult {

    private String extId;
    private String platformUserId;
    private String ticketId;
    private ChannelBindResultStatus status;

    public String getExtId() {
        return extId;
    }

    public void setExtId(String extId) {
        this.extId = extId;
    }

    public String getPlatformUserId() {
        return platformUserId;
    }

    public void setPlatformUserId(String platformUserId) {
        this.platformUserId = platformUserId;
    }

    public String getTicketId() {
        return ticketId;
    }

    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
    }

    public ChannelBindResultStatus getStatus() {
        return status;
    }

    public void setStatus(ChannelBindResultStatus status) {
        this.status = status;
    }
}
