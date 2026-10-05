package io.nop.api.core.message;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Return-value signal (adjudication D5 b-prime): the consumer declares the message is a
 * poison message that must NOT be redelivered. The backend acknowledges the message and
 * sends no reply — the failure endpoint (dead-letter, retry bridge) is the caller's
 * responsibility via the failure callback (WI16).
 *
 * <p>Contract: a backend must never forward this signal to the ack topic as a reply.
 */
public final class TerminateConsume {
    private final String reason;

    public TerminateConsume(@JsonProperty("reason") String reason) {
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
