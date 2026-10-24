package io.nop.message.core;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.message.core.local.LocalMessageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C3-009 barrier ③ regression (plan 2496 Phase 4): a deterministic Error thrown by the
 * local subscriber must not escape the delivery barrier. Pre-fix the Error escaped
 * {@code invokeSubscription}'s Exception-only catch to the synchronous send chain (the
 * sender thread got a raw LinkageError) and, on the redelivery scheduler thread, silently
 * killed the delayed task (the deferred message was lost without a WARN). Post-fix the
 * barrier catches Throwable, the sender is unaffected and the service keeps delivering.
 */
public class TestLocalMessageServiceErrorBarrier {

    private LocalMessageService service;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger serviceLogger;

    @BeforeEach
    void setUp() {
        service = new LocalMessageService();
        serviceLogger = (Logger) LoggerFactory.getLogger(LocalMessageService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        serviceLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        if (logAppender != null && serviceLogger != null) {
            serviceLogger.detachAppender(logAppender);
            logAppender.stop();
        }
    }

    @Test
    void handlerErrorDoesNotEscapeSendAndServiceKeepsDelivering() throws Exception {
        AtomicInteger deliveries = new AtomicInteger();
        CountDownLatch secondDelivered = new CountDownLatch(1);
        IMessageConsumer handler = (topic, message, context) -> {
            if (deliveries.incrementAndGet() == 1) {
                throw new LinkageError("injected handler Error");
            }
            secondDelivered.countDown();
            return null;
        };
        service.subscribe("t-barrier", handler, null);

        // First send: the handler Error must be contained by the delivery barrier —
        // pre-fix it escaped sendAsync to the caller thread.
        service.send("t-barrier", "boom-message", null);

        // The service must still deliver the next message (no dead subscription).
        service.send("t-barrier", "recovery-message", null);
        assertTrue(secondDelivered.await(5, TimeUnit.SECONDS),
                "local subscription must keep delivering after an injected Error");

        long errors = logAppender.list.stream()
                .filter(e -> e.getFormattedMessage().contains("nop.message.consumer-failed"))
                .count();
        assertTrue(errors >= 1, "the Error round must be observable via the delivery error log");
        assertEquals(2, deliveries.get(), "exactly two deliveries must have happened");
    }
}
