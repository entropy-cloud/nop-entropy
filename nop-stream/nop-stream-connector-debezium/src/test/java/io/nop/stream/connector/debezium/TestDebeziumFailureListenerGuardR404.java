/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.connector.debezium;

import io.nop.api.core.util.ICancellable;
import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.DebeziumConfig;
import io.nop.message.debezium.DebeziumMessageSource;
import io.nop.message.debezium.engine.DebeziumEngineWrapper;
import io.nop.message.debezium.engine.NopStreamOffsetBackingStore;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.exceptions.StreamException;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.44 R4-04 (plan 2537) red② acceptance pin — W-02 (finding 1.2-C3-006;
 * adjudication B.1 / seam S2): two same-named pipelines co-resident in one JVM
 * must not cross-route engine failure signals. Pre-fix the second
 * {@code registerFailureListener} silently overwrote the first (the failing
 * pipeline looked alive, the healthy one looked dead) and the first run's
 * finally unregistered the OTHER's registration.
 *
 * <p>Three legs (adjudication E): (a) the duplicate run is rejected typed with
 * the config-error face; (b) [M1 survival counter-leg] the FIRST instance's
 * listener survives the rejection — an explicit
 * {@code DebeziumEngineWrapper.notifyEngineFailure} (carrier API used read-only,
 * the production notification path, zero forking) lands in the first instance's
 * pendingError; (c) [MJ-1 retryability invariant] the REJECTED instance remains
 * reusable — its {@code runEntered} was reset on the pre-try throw, so a retry
 * after the winner exits enters normally.
 *
 * <p>Hermetic: both functions run a stub message source (the
 * {@code NoEngineMessageSource} form of {@code TestDebeziumCdcSourceFunction}) —
 * no engine, no broker. The carrier registry
 * ({@code DebeziumEngineWrapper.FAILURE_LISTENERS}) is exercised through its
 * public register/unregister/notify API only — zero carrier diff (R4-24 red line).
 */
class TestDebeziumFailureListenerGuardR404 {

    private static final String PIPELINE_NAME = "dup-guard-probe";

    /** Stub engine host: run() enters the wait loop and parks until cancel/failure. */
    private static DebeziumCdcSourceFunction guardProbeFunction(AtomicInteger engineCreates) {
        DebeziumConfig config = new DebeziumConfig();
        config.setName(PIPELINE_NAME);
        config.setConnectorType("mysql");
        config.setDatabaseHost("localhost");
        return new DebeziumCdcSourceFunction(config) {
            @Override
            protected DebeziumMessageSource createMessageSource(
                    DebeziumConfig cfg, NopStreamOffsetBackingStore store) {
                engineCreates.incrementAndGet();
                return new NoEngineMessageSource(cfg);
            }
        };
    }

    private static SourceFunction.SourceContext<ChangeEvent> sinkContext() {
        return new SourceFunction.SourceContext<>() {
            @Override
            public void collect(ChangeEvent element) {
            }

            @Override
            public void collectWithTimestamp(ChangeEvent element, long timestamp) {
            }

            @Override
            public void emitWatermark(long mark) {
            }

            @Override
            public void markAsTemporarilyIdle() {
            }

            @Override
            public long getProcessingTime() {
                return System.currentTimeMillis();
            }

            @Override
            public boolean isCancelled() {
                return false;
            }
        };
    }

    @Test
    void duplicateLiveNameTypedRejectedAndFirstInstanceListenerSurvives() throws Exception {
        AtomicInteger createsA = new AtomicInteger();
        AtomicInteger createsB = new AtomicInteger();
        DebeziumCdcSourceFunction first = guardProbeFunction(createsA);
        DebeziumCdcSourceFunction duplicate = guardProbeFunction(createsB);
        try {
        runDuplicateLegs(first, duplicate, createsA, createsB);
        } finally {
            // cleanup-safe: on ANY leg failure the parked run threads must still be
            // released, or the non-daemon runners would hang the surefire fork
            first.cancel();
            duplicate.cancel();
        }
    }

    private void runDuplicateLegs(DebeziumCdcSourceFunction first, DebeziumCdcSourceFunction duplicate,
                                  AtomicInteger createsA, AtomicInteger createsB) throws Exception {
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        AtomicReference<Throwable> duplicateFailure = new AtomicReference<>();
        Thread firstRun = new Thread(() -> {
            try {
                first.run(sinkContext());
            } catch (Throwable t) {
                firstFailure.set(t);
            }
        });
        firstRun.start();
        assertTrue(awaitTrue(createsA, 1, 5_000L),
                "the first pipeline must reach its engine creation (claim held)");

        Thread duplicateRun = new Thread(() -> {
            try {
                duplicate.run(sinkContext());
            } catch (Throwable t) {
                duplicateFailure.set(t);
            }
        });
        duplicateRun.start();
        duplicateRun.join(5_000L);

        // ---- leg (a): the duplicate is rejected typed before any registration ----
        assertFalse(duplicateRun.isAlive(), "the duplicate run must terminate promptly");
        assertTrue(duplicateFailure.get() instanceof StreamException,
                "the duplicate run must fail typed (CRA 8.44 R4-04 W-02 red②), got: "
                        + duplicateFailure.get());
        StreamException dupEx = (StreamException) duplicateFailure.get();
        assertTrue(String.valueOf(dupEx).contains("duplicate live connector name"),
                "the rejection must name the duplicate-live-name condition, got: " + dupEx);
        assertEquals(0, createsB.get(),
                "the rejected duplicate must not create an engine (rejected before the try body)");

        // ---- leg (b): [M1 survival] the FIRST instance's listener survives — the
        // production notification path routes to the first instance's pendingError ----
        RuntimeException engineFailure = new RuntimeException("engine-terminal-boom");
        DebeziumEngineWrapper.notifyEngineFailure(PIPELINE_NAME, engineFailure);
        long deadline = System.currentTimeMillis() + 5_000L;
        while (firstFailure.get() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertNotNull(firstFailure.get(),
                "the failure notification must reach the FIRST (surviving) instance — "
                        + "pre-fix it landed in the second instance's listener (cross-route) "
                        + "and the first pipeline stayed silent (red② leg b)");
        assertSame(engineFailure, firstFailure.get(),
                "the first instance's failure must be the exact notified error");
        firstRun.join(5_000L);
        assertFalse(firstRun.isAlive(),
                "the first pipeline must fail out (its finally releases the claim)");

        // ---- leg (c): [MJ-1 retryability] the rejected instance is reusable — its
        // runEntered was reset on the pre-try throw, so a retry now enters normally ----
        assertTrue(awaitTrue(createsB, 0, 1_000L), "bookkeeping before retry");
        Thread retryRun = new Thread(() -> {
            try {
                duplicate.run(sinkContext());
            } catch (Throwable t) {
                duplicateFailure.set(t);
            }
        });
        retryRun.start();
        assertTrue(awaitTrue(createsB, 1, 5_000L),
                "the retried duplicate must enter normally (claim free + runEntered reset) — "
                        + "a stuck runEntered would fake-reject it via the W-04 guard");
        duplicate.cancel();
        retryRun.join(5_000L);
        assertFalse(retryRun.isAlive());
    }

    private static boolean awaitTrue(AtomicInteger counter, int target, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (counter.get() >= target) {
                return true;
            }
            Thread.sleep(20);
        }
        return counter.get() >= target;
    }

    /** Stub engine host source (TestDebeziumCdcSourceFunction's NoEngine form). */
    private static class NoEngineMessageSource extends DebeziumMessageSource {
        NoEngineMessageSource(DebeziumConfig config) {
            super(config, null);
        }

        @Override
        public ICancellable subscribe(java.util.function.Consumer<ChangeEvent> action) {
            return new ICancellable() {
                @Override
                public boolean isCancelled() {
                    return false;
                }

                @Override
                public String getCancelReason() {
                    return null;
                }

                @Override
                public void cancel(String reason) {
                }

                @Override
                public void appendOnCancel(java.util.function.Consumer<String> task) {
                }

                @Override
                public void removeOnCancel(java.util.function.Consumer<String> task) {
                }
            };
        }
    }
}
