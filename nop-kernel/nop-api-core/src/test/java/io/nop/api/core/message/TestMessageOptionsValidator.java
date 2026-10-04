package io.nop.api.core.message;

import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestMessageOptionsValidator {
    private static final String BACKEND = "test-backend";

    private Set<MessageCapability> caps(MessageCapability... cs) {
        Set<MessageCapability> set = EnumSet.of(MessageCapability.SEND,
                MessageCapability.SUBSCRIBE, MessageCapability.ACK_REPLY);
        for (MessageCapability c : cs) {
            set.add(c);
        }
        return set;
    }

    @Test
    void nullAndDefaultOptionsPassWithoutValidation() {
        Set<MessageCapability> caps = caps(); // minimal set
        assertDoesNotThrow(() -> MessageOptionsValidator.validateSend(caps, null, BACKEND));
        assertDoesNotThrow(() -> MessageOptionsValidator.validateSubscribe(caps, null, BACKEND));
        // Default-value objects are no-ops: only non-default values trigger checks.
        assertDoesNotThrow(() -> MessageOptionsValidator.validateSend(caps,
                new MessageSendOptions(), BACKEND));
        assertDoesNotThrow(() -> MessageOptionsValidator.validateSubscribe(caps,
                new MessageSubscribeOptions(), BACKEND));
    }

    @Test
    void missingCapabilityIsTypedRejected() {
        NopException e = assertThrows(NopException.class,
                () -> MessageOptionsValidator.validateSend(caps(),
                        sendOptions(100L), BACKEND));
        assertEquals(MessageErrors.ERR_OPTION_NOT_SUPPORTED.getErrorCode(), e.getErrorCode());
        assertNotNull(e.getErrorCode());
    }

    @Test
    void declaredCapabilityPasses() {
        Set<MessageCapability> caps = caps(MessageCapability.DELAYED_SEND);
        assertDoesNotThrow(() -> MessageOptionsValidator.validateSend(caps,
                sendOptions(100L), BACKEND));
    }

    @Test
    void subscribeFieldsMapToCapabilities() {
        // D11 (WI14): transactional is deprecated — warn-once + ignore, never a rejection.
        MessageSubscribeOptions txOptions = new MessageSubscribeOptions();
        txOptions.setTransactional(true);
        assertDoesNotThrow(() -> MessageOptionsValidator.validateSubscribe(caps(), txOptions, BACKEND));
        MessageSubscribeOptions timeoutOptions = new MessageSubscribeOptions();
        timeoutOptions.setTransactionTimeout(1000L);
        assertDoesNotThrow(() -> MessageOptionsValidator.validateSubscribe(caps(), timeoutOptions, BACKEND));

        MessageSubscribeOptions seekOptions = new MessageSubscribeOptions();
        seekOptions.setSeekMode(SeekMode.DEFAULT); // DEFAULT is treated as unset
        assertDoesNotThrow(() -> MessageOptionsValidator.validateSubscribe(caps(), seekOptions, BACKEND));

        seekOptions.setSeekMode(SeekMode.INIT_SEEK_TO_BEGIN);
        assertThrows(NopException.class,
                () -> MessageOptionsValidator.validateSubscribe(caps(), seekOptions, BACKEND));
    }

    @Test
    void batchReceiveTimeoutOnlyCheckedWhenCountPositive() {
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setBatchReceiveTimeout(100); // count=0: timeout alone is a no-op field
        assertDoesNotThrow(() -> MessageOptionsValidator.validateSubscribe(caps(), options, BACKEND));

        options.setBatchReceiveCount(10);
        assertThrows(NopException.class,
                () -> MessageOptionsValidator.validateSubscribe(caps(), options, BACKEND));
    }

    @Test
    void warnOnceSetDowngradesRejectToWarn() {
        Set<MessageCapability> caps = caps();
        Set<MessageCapability> warnOnce = EnumSet.of(MessageCapability.DELAYED_SEND);
        assertDoesNotThrow(() -> MessageOptionsValidator.validateSend(caps,
                sendOptions(100L), BACKEND, warnOnce));
        // D11 (WI14): transactional is warn-once everywhere (not capability-gated).
        MessageSubscribeOptions options = new MessageSubscribeOptions();
        options.setTransactional(true);
        assertDoesNotThrow(() -> MessageOptionsValidator.validateSubscribe(caps,
                options, BACKEND, warnOnce));
        // Declared-but-missing fields outside warnOnce still reject (seek here).
        MessageSubscribeOptions seekOptions = new MessageSubscribeOptions();
        seekOptions.setSeekMode(SeekMode.INIT_SEEK_TO_END);
        assertThrows(NopException.class, () -> MessageOptionsValidator.validateSubscribe(caps,
                seekOptions, BACKEND, warnOnce));
    }

    @Test
    void sendTimeoutAndCancelTokenRequireSendTimeoutCapability() {
        // WI15: unmanaged backends (no SEND_TIMEOUT) warn-once; Kafka/Pulsar (declared) pass.
        MessageSendOptions timeoutOptions = new MessageSendOptions();
        timeoutOptions.setSendTimeout(100L);
        assertThrows(NopException.class,
                () -> MessageOptionsValidator.validateSend(caps(), timeoutOptions, BACKEND));
        MessageSendOptions cancelOptions = new MessageSendOptions();
        cancelOptions.setCancelToken(new io.nop.api.core.util.ICancelToken() {
            @Override
            public boolean isCancelled() {
                return true;
            }

            @Override
            public void appendOnCancel(java.util.function.Consumer<String> task) {
            }

            @Override
            public void removeOnCancel(java.util.function.Consumer<String> task) {
            }

            @Override
            public String getCancelReason() {
                return "test-cancelled";
            }
        });
        assertThrows(NopException.class,
                () -> MessageOptionsValidator.validateSend(caps(), cancelOptions, BACKEND));

        Set<MessageCapability> declared = caps(MessageCapability.SEND_TIMEOUT);
        assertDoesNotThrow(() -> MessageOptionsValidator.validateSend(declared, timeoutOptions, BACKEND));
        assertDoesNotThrow(() -> MessageOptionsValidator.validateSend(declared, cancelOptions, BACKEND));

        // warn-once path for a backend that declared the disposition
        Set<MessageCapability> warnOnce = EnumSet.of(MessageCapability.SEND_TIMEOUT);
        assertDoesNotThrow(() -> MessageOptionsValidator.validateSend(caps(), timeoutOptions, BACKEND, warnOnce));
    }

    @Test
    void transactionOptionsAreDeprecated() throws Exception {
        // D11 form assertion: fields kept (YAML compatible) but flagged deprecated.
        org.junit.jupiter.api.Assertions.assertTrue(
                MessageSubscribeOptions.class.getMethod("setTransactional", boolean.class)
                        .isAnnotationPresent(Deprecated.class));
        org.junit.jupiter.api.Assertions.assertTrue(
                MessageSubscribeOptions.class.getMethod("getTransactionTimeout")
                        .isAnnotationPresent(Deprecated.class));
    }

    private MessageSendOptions sendOptions(long delay) {
        MessageSendOptions options = new MessageSendOptions();
        options.setDelay(delay);
        return options;
    }
}
