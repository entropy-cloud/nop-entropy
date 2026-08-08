package io.nop.integration.api.bind;

/**
 * Lifecycle state of a {@link BindTicket}, owned by the
 * {@link IChannelBindProvider} that minted it. The state transitions are
 * driven by channel-side scan events and never stored in a separate table —
 * each provider tracks its own tickets in memory or in its own backend.
 *
 * <p>States:
 * <ul>
 *   <li>{@link #PENDING} — ticket minted, QR rendered, waiting for the
 *       user to scan.</li>
 *   <li>{@link #SCANNED} — channel reported the user scanned the QR but
 *       has not yet confirmed (e.g. the user has not tapped "allow" in the
 *       channel app).</li>
 *   <li>{@link #CONFIRMED} — channel reported a confirmed scan; the
 *       callback carrying the channel-side user id has arrived.</li>
 *   <li>{@link #EXPIRED} — ticket passed its {@link BindTicket#getExpiresAt()}
 *       deadline without being confirmed. The provider refuses further
 *       state changes on it.</li>
 * </ul>
 */
public enum BindTicketStatus {
    PENDING,
    SCANNED,
    CONFIRMED,
    EXPIRED
}
