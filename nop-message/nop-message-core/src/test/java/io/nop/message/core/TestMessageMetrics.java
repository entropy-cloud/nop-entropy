package io.nop.message.core;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class TestMessageMetrics {

    @Test
    void countersIncrement() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MessageMetrics metrics = MessageMetrics.create(registry, "test");
        metrics.incrementSent();
        metrics.incrementSent();
        metrics.incrementConsumed();
        metrics.incrementFailed();
        assertEquals(2, metrics.sentCount());
        assertEquals(1, metrics.consumedCount());
        assertEquals(1, metrics.failedCount());
    }

    @Test
    void noopIsSafe() {
        MessageMetrics metrics = MessageMetrics.noop();
        assertDoesNotThrow(() -> {
            metrics.incrementSent();
            metrics.incrementConsumed();
            metrics.incrementFailed();
            metrics.recordSendDuration(100);
        });
        assertEquals(0, metrics.sentCount());
    }
}
