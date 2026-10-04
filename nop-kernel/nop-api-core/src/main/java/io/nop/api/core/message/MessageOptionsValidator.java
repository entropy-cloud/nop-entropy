package io.nop.api.core.message;

import io.nop.api.core.exceptions.NopException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The D1 gate (see {@code ai-dev/design/nop-message/message-contract-design.md}): invoked at
 * EVERY {@code subscribe} and {@code sendAsync} entry of an {@link IMessageService}
 * implementation. An option field that requires a capability the implementation does not
 * declare is typed-rejected by default, or warn-once when the implementation declared that
 * capability in its {@code warnOnce} set (one WARN per JVM per backend+capability — the
 * third disposition state of the silent-violation rule, never per-call).
 *
 * <p>Only non-default option values trigger validation, so passing a fresh default options
 * object (or null) never rejects or warns. The deprecated transaction options
 * (D11/WI14) are never capability-gated: any backend warns once and ignores them.
 */
public final class MessageOptionsValidator {
    private static final Logger LOG = LoggerFactory.getLogger(MessageOptionsValidator.class);

    private static final Map<String, Boolean> WARNED = new ConcurrentHashMap<>();

    private MessageOptionsValidator() {
    }

    public static void validateSend(Set<MessageCapability> capabilities,
                                    MessageSendOptions options, String backend) {
        validateSend(capabilities, options, backend, null);
    }

    public static void validateSend(Set<MessageCapability> capabilities,
                                    MessageSendOptions options, String backend,
                                    Set<MessageCapability> warnOnce) {
        if (options == null)
            return;
        if (options.getDelay() > 0)
            check(capabilities, MessageCapability.DELAYED_SEND, "delay", backend, warnOnce);
        // sendTimeout / cancelToken: mapped to SEND; their wiring is adjudicated to WI15.
    }

    public static void validateSubscribe(Set<MessageCapability> capabilities,
                                         MessageSubscribeOptions options, String backend) {
        validateSubscribe(capabilities, options, backend, null);
    }

    public static void validateSubscribe(Set<MessageCapability> capabilities,
                                         MessageSubscribeOptions options, String backend,
                                         Set<MessageCapability> warnOnce) {
        if (options == null)
            return;
        if (options.isTransactional())
            warnOnce("message-spi", "transactional",
                    "deprecated and ignored by D11 (at-least-once consumption baseline)");
        if (options.getTransactionTimeout() > 0)
            warnOnce("message-spi", "transactionTimeout",
                    "deprecated and ignored by D11 (at-least-once consumption baseline)");
        if (options.getBatchReceiveCount() > 0) {
            check(capabilities, MessageCapability.BATCH_CONSUME, "batchReceiveCount", backend, warnOnce);
            if (options.getBatchReceiveTimeout() > 0)
                check(capabilities, MessageCapability.BATCH_CONSUME, "batchReceiveTimeout", backend, warnOnce);
        }
        if (options.getSeekMode() != null && options.getSeekMode() != SeekMode.DEFAULT)
            check(capabilities, MessageCapability.SEEK, "seekMode", backend, warnOnce);
        if (options.getSeekToMessage() != null)
            check(capabilities, MessageCapability.SEEK, "seekToMessage", backend, warnOnce);
        if (options.getSeekToTime() > 0)
            check(capabilities, MessageCapability.SEEK, "seekToTime", backend, warnOnce);
        // subscribeName / concurrency / subscriptionType require SUBSCRIBE, which every
        // subscribing backend declares by construction; subscriptionType value-level
        // disposition (e.g. Kafka warn-once) is handled in-place by the backend.
    }

    private static void check(Set<MessageCapability> capabilities, MessageCapability required,
                              String field, String backend, Set<MessageCapability> warnOnce) {
        if (capabilities.contains(required))
            return;
        if (warnOnce != null && warnOnce.contains(required)) {
            warnOnce(backend, field, "capability " + required
                    + " not declared; option field is ignored per the backend's declared disposition");
            return;
        }
        throw new NopException(MessageErrors.ERR_OPTION_NOT_SUPPORTED)
                .param("field", field)
                .param("capability", required)
                .param("backend", backend);
    }

    /**
     * Warn-once variant: instead of rejecting, logs ONE warning per backend+field pair per
     * JVM and returns (the implementation then executes its declared fallback behavior).
     */
    public static void warnOnce(String backend, String field, String message) {
        if (WARNED.putIfAbsent(backend + '|' + field, Boolean.TRUE) == null) {
            LOG.warn("nop.err.message.option-warn-once:backend={},field={},detail={}",
                    backend, field, message);
        }
    }
}
