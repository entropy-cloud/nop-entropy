package io.nop.ai.agent.engine;

/**
 * Plan 2291 (WI10): policy for handling a subscriber whose {@code onEvent}
 * throws.
 *
 * <ul>
 *   <li>{@link #LOG_ONLY} — log every failure and keep the subscriber
 *       (shipped default; the prior behaviour plus failure counters).</li>
 *   <li>{@link #REMOVE_AFTER_THRESHOLD} — automatically remove the subscriber
 *       once its consecutive failure count reaches the configured threshold
 *       (a misbehaving subscriber cannot degrade every subsequent event
 *       fan-out).</li>
 * </ul>
 */
public enum SubscriberFailurePolicy {
    LOG_ONLY,
    REMOVE_AFTER_THRESHOLD
}
