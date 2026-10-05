package io.nop.api.core.message;

/**
 * Declares what an {@link IMessageService} implementation actually honors (adjudication D1,
 * see {@code ai-dev/design/nop-message/message-contract-design.md}). Capabilities are the
 * basis of the {@link MessageOptionsValidator} gate invoked at every {@code subscribe} /
 * {@code sendAsync} entry: an option field that requires a capability the implementation
 * does not declare is typed-rejected (or warn-once where the implementation declared that
 * disposition). An empty capability set means the implementation rejects every option.
 */
public enum MessageCapability {
    /** Basic fire-and-forget send. */
    SEND,

    /**
     * MessageSendOptions.sendTimeout and cancelToken are wired into the send path
     * (timeout upper bound on the returned future; pre-send cancellation check). Bundles
     * both send-option fields — a backend declaring this capability must implement both.
     */
    SEND_TIMEOUT,

    /** Basic subscription; subscribeName / concurrency / subscriptionType fields. */
    SUBSCRIBE,

    /** onMessageBatch dispatch with batchReceiveCount / batchReceiveTimeout honored. */
    BATCH_CONSUME,

    /** MessageSendOptions.delay is honored (native delayed delivery). */
    DELAYED_SEND,

    /** Consumer-side transactions (transactional / transactionTimeout honored). */
    TRANSACTION,

    /** SeekMode / seekToTime resolution is honored; seekToMessage stays typed-rejected. */
    SEEK,

    /** Acknowledge-style replies dispatched to the ack topic. */
    ACK_REPLY,

    /** Envelope metadata contract (messageId / topic / eventTime / key / headers round-trip). */
    ENVELOPE_METADATA,

    /** Native dead-letter support (adjudication D6 layer two). */
    DLQ
}
