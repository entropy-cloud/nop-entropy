/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.debezium;

import io.nop.api.core.util.ICancellable;
import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.ChangeEventMetadata;
import io.nop.message.debezium.DebeziumConfig;
import io.nop.message.debezium.DebeziumMessageSource;
import io.nop.message.debezium.engine.DebeziumEngineWrapper;
import io.nop.message.debezium.engine.NopStreamOffsetBackingStore;
import io.nop.stream.core.common.functions.source.SourceFunction;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 368 Phase 4, audits R5-CON-02 and R5-CON-03: the Debezium path must fail the
 * task loudly instead of silently dropping CDC data.
 *
 * <ul>
 *   <li><strong>R5-CON-02</strong>: a collector exception (ctx.collect throwing inside
 *       the subscription consumer) is captured into {@code pendingError} and rethrown
 *       from {@code run()} — the task is recognized as FAILED and the offset does not
 *       advance past the failure (previously the upstream dispatcher swallowed the
 *       exception, the event was lost and its offset still advanced).</li>
 *   <li><strong>R5-CON-03</strong>: a terminal engine failure reported through the
 *       {@link DebeziumEngineWrapper} failure listener (engine run() threw or
 *       CompletionCallback success=false) lands on the same {@code pendingError} path —
 *       the task fails instead of RUNNING with a silently dead CDC stream.</li>
 * </ul>
 */
class TestDebeziumCdcSourceErrorPropagation {

    private static final ChangeEvent EVENT = new ChangeEvent(
            new ChangeEventMetadata("mysql", "test", "db", null, "t", "data"),
            "c", null, java.util.Collections.singletonMap("id", 1), null, 0L);

    /** Stub source: captures the subscribed consumer, records cleanup, no real engine. */
    static class CapturingMessageSource extends DebeziumMessageSource {
        final AtomicReference<Consumer<ChangeEvent>> subscribed = new AtomicReference<>();
        final CountDownLatch subscribedLatch = new CountDownLatch(1);
        final AtomicBoolean stopped = new AtomicBoolean(false);
        private final ICancellable subscription = new ICancellable() {
            @Override public boolean isCancelled() {
                return false;
            }

            @Override public String getCancelReason() {
                return null;
            }

            @Override public void cancel(String reason) {
            }

            @Override public void appendOnCancel(Consumer<String> task) {
            }

            @Override public void removeOnCancel(Consumer<String> task) {
            }
        };

        CapturingMessageSource(DebeziumConfig config) {
            super(config, null);
        }

        @Override
        public ICancellable subscribe(Consumer<ChangeEvent> action) {
            subscribed.set(action);
            subscribedLatch.countDown();
            return subscription;
        }

        @Override
        public void stop() {
            stopped.set(true);
        }
    }

    private static SourceFunction.SourceContext<ChangeEvent> ctx(AtomicReference<Throwable> collectError) {
        return new SourceFunction.SourceContext<>() {
            @Override public void collect(ChangeEvent element) {
                if (collectError != null && collectError.get() != null) {
                    throw (RuntimeException) collectError.get();
                }
            }

            @Override public void collectWithTimestamp(ChangeEvent element, long timestamp) {
            }

            @Override public void emitWatermark(long mark) {
            }

            @Override public void markAsTemporarilyIdle() {
            }

            @Override public long getProcessingTime() {
                return System.currentTimeMillis();
            }
        };
    }

    private static DebeziumCdcSourceFunction functionWith(String name, CapturingMessageSource stub) {
        DebeziumConfig config = new DebeziumConfig();
        config.setName(name);
        config.setConnectorType("mysql");
        config.setDatabaseHost("localhost");
        return new DebeziumCdcSourceFunction(config) {
            @Override
            protected DebeziumMessageSource createMessageSource(DebeziumConfig cfg,
                                                                NopStreamOffsetBackingStore store) {
                return stub;
            }
        };
    }

    /** Runs fn.run(ctx) on a separate thread and captures the outcome. */
    private static RunOutcome runOnThread(DebeziumCdcSourceFunction fn,
                                          SourceFunction.SourceContext<ChangeEvent> ctx) {
        RunOutcome outcome = new RunOutcome();
        Thread runner = new Thread(() -> {
            try {
                fn.run(ctx);
            } catch (Throwable t) {
                outcome.thrown.set(t);
            } finally {
                outcome.finished.countDown();
            }
        });
        runner.start();
        outcome.runner = runner;
        return outcome;
    }

    private static final class RunOutcome {
        final AtomicReference<Throwable> thrown = new AtomicReference<>();
        final CountDownLatch finished = new CountDownLatch(1);
        Thread runner;

        void awaitCompletion() throws InterruptedException {
            assertTrue(finished.await(10, TimeUnit.SECONDS), "run() must terminate");
            runner.join(5000);
        }
    }

    @Test
    void testCollectorExceptionIsRethrownFromRun() throws Exception {
        RuntimeException collectError = new RuntimeException("simulated downstream collect failure");
        AtomicReference<Throwable> collectErrorRef = new AtomicReference<>(collectError);

        CapturingMessageSource stub = new CapturingMessageSource(new DebeziumConfig());
        DebeziumCdcSourceFunction fn = functionWith("err-prop-collect", stub);
        CopyOnWriteArrayList<ChangeEvent> seen = new CopyOnWriteArrayList<>();

        RunOutcome outcome = runOnThread(fn, new SourceFunction.SourceContext<>() {
            @Override public void collect(ChangeEvent element) {
                seen.add(element);
                throw collectError;
            }

            @Override public void collectWithTimestamp(ChangeEvent element, long timestamp) {
            }

            @Override public void emitWatermark(long mark) {
            }

            @Override public void markAsTemporarilyIdle() {
            }

            @Override public long getProcessingTime() {
                return System.currentTimeMillis();
            }
        });

        assertTrue(stub.subscribedLatch.await(5, TimeUnit.SECONDS), "run must subscribe");
        // Dispatch one event through the captured consumer: collect throws.
        stub.subscribed.get().accept(EVENT);

        outcome.awaitCompletion();

        assertSame(collectError, outcome.thrown.get(),
                "run() must rethrow the collector exception (R5-CON-02), not swallow it");
        assertEquals(1, seen.size(), "exactly one event was delivered before the failure");
        assertTrue(stub.stopped.get(), "cleanup must stop the message source after the failure");
        // No checkpoint may persist offset progress past the failure: the offset store
        // binding is untouched (engine offsets are engine-internal; the invariant tested
        // is that the task FAILED — the snapshot path only ever reflects the last
        // checkpointed offsets).
        assertNull(fn.getOffsetStore());
    }

    @Test
    void testEngineFailureCallbackFailsRun() throws Exception {
        CapturingMessageSource stub = new CapturingMessageSource(new DebeziumConfig());
        DebeziumCdcSourceFunction fn = functionWith("err-prop-engine", stub);
        SourceFunction.SourceContext<ChangeEvent> sourceCtx = ctx(null);

        RunOutcome outcome = runOnThread(fn, sourceCtx);
        // Waiting for the subscription guarantees run() has registered the engine
        // failure listener (registration happens before createMessageSource).
        assertTrue(stub.subscribedLatch.await(5, TimeUnit.SECONDS), "run must subscribe");

        IllegalStateException engineError = new IllegalStateException("simulated engine death");
        DebeziumEngineWrapper.notifyEngineFailure("err-prop-engine", engineError);

        outcome.awaitCompletion();

        assertSame(engineError, outcome.thrown.get(),
                "the engine failure callback must fail the task (R5-CON-03)");
        assertTrue(stub.stopped.get(), "cleanup must stop the message source after the failure");
    }
}
