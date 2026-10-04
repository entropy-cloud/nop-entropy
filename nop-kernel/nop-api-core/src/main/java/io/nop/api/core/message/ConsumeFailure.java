package io.nop.api.core.message;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Full context of a consumer failure (D6 layer one, WI16): dispatched via
 * {@link IMessageConsumer#onConsumeFailure(ConsumeFailure)}. The default implementation
 * logs a WARN with the full context (error-handling evidence rule) and bridges to
 * {@link IMessageConsumer#onException(Exception)} for legacy overrides.
 */
public final class ConsumeFailure {
    private final String topic;
    private final String location;
    private final Object message;
    private final int attempt;
    private final Exception throwable;

    public ConsumeFailure(@JsonProperty("topic") String topic,
                          @JsonProperty("location") String location,
                          @JsonProperty("message") Object message,
                          @JsonProperty("attempt") int attempt,
                          @JsonProperty("throwable") Exception throwable) {
        this.topic = topic;
        this.location = location;
        this.message = message;
        this.attempt = attempt;
        this.throwable = throwable;
    }

    /** Source topic; null for framework-level failures (poll/commit). */
    public String getTopic() {
        return topic;
    }

    /**
     * Broker-agnostic location string: {@code topic-partition@offset} for Kafka, the
     * Pulsar messageId string for Pulsar, null when unavailable.
     */
    public String getLocation() {
        return location;
    }

    /**
     * The message reference as delivered to the consumer. A reference, not a copy — do not
     * retain beyond the callback (WI21 dead-letter republish needs the full payload).
     */
    public Object getMessage() {
        return message;
    }

    /** Consecutive failure count for this location; 0 for framework-level failures. */
    public int getAttempt() {
        return attempt;
    }

    public Exception getThrowable() {
        return throwable;
    }
}
