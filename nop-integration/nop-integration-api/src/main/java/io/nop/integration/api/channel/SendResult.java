package io.nop.integration.api.channel;

/**
 * Outcome of a business-driven outbound channel send
 * ({@link IChannelMessageService#sendToUser}). The three values make the
 * "no binding" and "unsupported channel" cases explicit results rather than
 * exceptions, so the caller can decide its own fallback policy (e.g. degrade
 * to SMS) — see design {@code nop-ai-channel-integration-design.md} §3.2.
 *
 * <ul>
 *   <li>{@link #SENT} — the message was handed to the channel connector.</li>
 *   <li>{@link #NO_BINDING} — the user has no verified channel binding;
 *       nothing was sent.</li>
 *   <li>{@link #UNSUPPORTED} — the user has a binding but no connector is
 *       deployed for that channel type (the message form is not supported
 *       either); nothing was sent.</li>
 * </ul>
 */
public enum SendResult {
    SENT,
    NO_BINDING,
    UNSUPPORTED
}
