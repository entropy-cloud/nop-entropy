package io.nop.integration.api.bind;

/**
 * Outcome of an {@link IChannelBindProvider#onChannelScanCallback(
 * ChannelScanCallback)} call, reported back to the platform so the
 * platform can decide what to do next (write the binding, ask the user to
 * confirm, or short-circuit an already-bound identity).
 */
public enum ChannelBindResultStatus {
    /** Channel confirmed the scan and produced a channel-side user id; the
     * platform may now call {@code IChannelBindService.completeBinding}. */
    BINDING_COMPLETED,
    /** The channel-side identity is already bound to a platform user (not
     * necessarily the one who started this ticket). The provider has filled
     * {@link ChannelBindResult#getPlatformUserId()} with the existing owner
     * if known; the platform should not write a duplicate binding. */
    ALREADY_BOUND,
    /** Channel reported a scan but still awaits user confirmation (e.g. the
     * user has not tapped "allow" in the channel app). The platform should
     * keep the ticket alive and wait for a follow-up callback. */
    PENDING_CONFIRM
}
