package io.nop.api.core.message;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Return-value signal (adjudication D5 b-prime) for batch consumption: the consumer
 * declares the commit boundary. When returned as the response for record {@code k}
 * (0-based) of an {@link IMessageConsumer#onMessageBatch} batch, the backend commits
 * records {@code [0..k-1]} only; records from {@code k} onward are not committed and will
 * be redelivered, and processing of the remaining responses stops.
 *
 * <p>In single-message dispatch a backend without a commit concept treats this signal as
 * completion (documented per backend).
 */
public final class PartialCommit {
    private final int committedCount;

    public PartialCommit(@JsonProperty("committedCount") int committedCount) {
        this.committedCount = committedCount;
    }

    /**
     * Number of leading batch records that are committed ({@code k}); the record this
     * signal is returned for is NOT included.
     */
    public int getCommittedCount() {
        return committedCount;
    }
}
