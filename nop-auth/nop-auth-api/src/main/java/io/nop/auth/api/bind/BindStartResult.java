package io.nop.auth.api.bind;

import io.nop.api.core.annotations.data.DataBean;

import java.sql.Timestamp;

/**
 * Result of {@link IChannelBindService#startBinding(String, String)}: the
 * QR payload the front-end renders into an image, plus the ticket id and
 * expiry the front-end uses to poll for a channel scan callback.
 *
 * <p><b>Layering note</b>: this bean lives in {@code nop-auth-api} (which
 * depends only on {@code nop-api-core}) so it does <b>not</b> expose the
 * transport-layer {@code BindTicket} type from {@code nop-integration-api}.
 * The {@code ChannelBindServiceImpl} in {@code nop-auth-service} translates
 * between the two — the {@code nop-auth-api} surface stays clean of vendor
 * protocol types.
 */
@DataBean
public class BindStartResult {

    private String ticketId;
    private String qrPayload;
    private Timestamp expiresAt;

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
}
