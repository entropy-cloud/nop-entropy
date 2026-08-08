package io.nop.integration.api.bind;

/**
 * Channel-specific protocol abstraction for binding a platform user to a
 * channel-side identity via QR scan. Each channel (Feishu, DingTalk, WeCom,
 * ...) ships one implementation, registered under its
 * {@code channelType} string so the {@code IChannelBindService} can look it
 * up by the channel the user is trying to bind.
 *
 * <p><b>Layering</b>: this interface lives in {@code nop-integration-api}
 * (depends only on {@code nop-api-core}) so a vendor module
 * (e.g. {@code nop-integration-feishu}) can implement it without depending
 * on the AI engine or the auth service. The {@code IChannelBindService}
 * (in {@code nop-auth-api}) consumes these methods indirectly — its own
 * message types live in {@code nop-auth-api} and the impl in
 * {@code nop-auth-service} translates between the two type sets (design
 * §3.4 ②).
 *
 * <p><b>Naming</b>: {@code …Provider} rather than {@code …Sender} because
 * binding is a multi-step, stateful protocol (mint a ticket → user scans →
 * channel confirms) — not the one-shot fire-and-forget that the existing
 * {@code ISmsSender} / {@code IEmailSender} names denote. Mirrors the
 * transport-layer {@code IChannelConnector}, which is also a stateful
 * protocol abstraction.
 *
 * <p><b>Ticket ownership</b>: the provider owns ticket state (design §3.4
 * ②). There is no shared {@code BindTicket} table; the platform never
 * reads or writes tickets, it only echoes {@link BindTicket#getTicketId()}
 * back in {@link ChannelScanCallback#getTicketId()}.
 */
public interface IChannelBindProvider {

    /**
     * The channel this provider handles. Must match the
     * {@code channelType} string returned by the transport-layer
     * {@code IChannelConnector.getChannelType()} and the codes declared in
     * {@code ChannelTypeCodes} (e.g. {@code "feishu"}).
     *
     * @return non-null channel type identifier
     */
    String getChannelType();

    /**
     * Start a binding flow for a platform user. Mint a channel-native
     * ticket, render the QR payload the user will scan in the channel app,
     * and return the ticket. The provider retains ticket state internally
     * keyed by {@link BindTicket#getTicketId()}.
     *
     * @param channelType     the channel to bind; must equal
     *                        {@link #getChannelType()}
     * @param platformUserId  the platform user starting the binding; will be
     *                        remembered on the ticket so the eventual
     *                        callback can be linked back to this user
     * @return non-null ticket whose {@link BindTicket#getStatus()} is
     *         {@link BindTicketStatus#PENDING}
     */
    BindTicket createBindTicket(String channelType, String platformUserId);

    /**
     * Handle a channel scan callback: parse the vendor payload, recover the
     * ticket, and report the channel-side user identity plus the outcome
     * status. The platform uses the returned {@link ChannelBindResult} to
     * decide whether to call {@code IChannelBindService.completeBinding}
     * (on {@link ChannelBindResultStatus#BINDING_COMPLETED}) or to wait /
     * short-circuit.
     *
     * @param callback the channel-agnostic normalised callback; never null
     * @return non-null result; {@link ChannelBindResult#getStatus()} is
     *         never null
     */
    ChannelBindResult onChannelScanCallback(ChannelScanCallback callback);
}
