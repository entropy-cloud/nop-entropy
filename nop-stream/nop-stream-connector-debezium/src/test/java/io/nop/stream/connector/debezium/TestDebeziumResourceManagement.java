package io.nop.stream.connector.debezium;

import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.DebeziumConfig;
import io.nop.stream.core.common.functions.source.SourceFunction;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests resource-management behavior specific to DebeziumCdcSourceFunction.
 * Moved from the base connector module as part of AR-2 (connector split):
 * this test references {@code nop-message-debezium} types and thus belongs
 * in {@code nop-stream-connector-debezium}, not the optional-dep-free base.
 */
class TestDebeziumResourceManagement {

    @Test
    void testDebeziumRunReentrancyGuard() throws Exception {
        DebeziumConfig config = new DebeziumConfig();
        config.setName("test-reentrant");
        config.setConnectorType("mysql");
        config.setDatabaseHost("localhost");
        DebeziumCdcSourceFunction debeziumSource = new DebeziumCdcSourceFunction(config);

        SourceFunction.SourceContext<ChangeEvent> ctx =
                new SourceFunction.SourceContext<>() {
            @Override public void collect(ChangeEvent element) {}
            @Override public void collectWithTimestamp(ChangeEvent element, long timestamp) {}
            @Override public void emitWatermark(long mark) {}
            @Override public void markAsTemporarilyIdle() {}
            @Override public long getProcessingTime() { return System.currentTimeMillis(); }
        };

        CountDownLatch runStarted = new CountDownLatch(1);
        AtomicBoolean secondRunReturned = new AtomicBoolean(false);

        Thread runner1 = new Thread(() -> {
            try {
                runStarted.countDown();
                debeziumSource.run(ctx);
            } catch (Exception e) {
                // expected on cancel
            }
        });
        runner1.start();
        assertTrue(runStarted.await(2, TimeUnit.SECONDS));

        Thread runner2 = new Thread(() -> {
            try {
                debeziumSource.run(ctx);
                secondRunReturned.set(true);
            } catch (Exception e) {
                secondRunReturned.set(true);
            }
        });
        runner2.start();
        runner2.join(5000);
        assertTrue(secondRunReturned.get(), "Second run() should return immediately due to reentrancy guard");

        debeziumSource.cancel();
        runner1.join(5000);
    }
}
