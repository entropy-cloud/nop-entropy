package io.nop.stream.connector.debezium;

import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.DebeziumConfig;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        // C10-015 same-diff rewrite (plan 2537, W-04): the pre-fix catch-all + success
        // flag FOLDED "silent return" (the defect carrier — fake EOS) and "typed throw"
        // into one observation and the old assertion pinned the silent return as the
        // EXPECTED behavior. Inversion per merge-adjudication §4.4:226 — the concurrent
        // second run's terminal shape must be a typed failure; "real entry" is
        // structurally unreachable here (the in-flight run owns runEntered).
        AtomicReference<Throwable> secondRunFailure = new AtomicReference<>();

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
            } catch (Throwable t) {
                secondRunFailure.set(t);
            }
        });
        runner2.start();
        runner2.join(5000);
        assertFalse(runner2.isAlive(),
                "the second run() must still terminate promptly (guard rejects immediately)");
        Throwable secondFailure = secondRunFailure.get();
        assertTrue(secondFailure instanceof StreamException,
                "the second run() must fail typed, never silently return as fake EOS "
                        + "(C10-015 / CRA 8.44 R4-04 W-04) — got: " + secondFailure);
        assertEquals(NopStreamErrors.ERR_STREAM_STATE_ERROR.getErrorCode(),
                ((StreamException) secondFailure).getErrorCode(),
                "the typed rejection must carry the state-error code");
        assertTrue(String.valueOf(secondFailure).contains("re-entered"),
                "the typed rejection must name the re-entry condition, got: " + secondFailure);

        debeziumSource.cancel();
        runner1.join(5000);
    }
}
