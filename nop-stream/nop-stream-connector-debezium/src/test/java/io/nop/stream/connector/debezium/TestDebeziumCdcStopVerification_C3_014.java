/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.connector.debezium;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.nop.api.core.util.ICancellable;
import io.nop.credential.api.ICredentialProvider;
import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.DebeziumConfig;
import io.nop.message.debezium.DebeziumMessageSource;
import io.nop.message.debezium.engine.NopStreamOffsetBackingStore;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.source.SourceReaderContext;
import io.nop.stream.core.source.SplitAssignmentProxy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 1.2-C3-014 residual ② (plan 2503 Phase 3): the four in-scope stop faces verify ENGINE
 * TERMINATION after the stop chain — the contract is「已终止」, not「已发出」. The runner-join
 * typed timeout (plan 2343) is untouched (zero-regression); the verification closes the
 * residual window「join 成功 ≠ 引擎终止」(a runner exiting via latch while a ghost engine
 * survives).
 *
 * <p>Failure shapes per face (rule 24 — visible either way, never silent):
 * {@code truncateForDrain} and reader {@code close} fail typed; {@code run}-finally and
 * {@code cancel} declare the ghost risk by explicit ERROR (cleanup/cancel must not mask or
 * throw into framework teardown).
 */
public class TestDebeziumCdcStopVerification_C3_014 {

    static DebeziumConfig config(String name) {
        DebeziumConfig cfg = new DebeziumConfig();
        cfg.setName(name);
        return cfg;
    }

    /**
     * Source stub whose engine-termination signal is test-controlled; subscribe/stop are
     * no-ops (no real engine).
     */
    static class GhostSource extends DebeziumMessageSource {
        volatile boolean terminated = true;
        final CountDownLatch subscribed = new CountDownLatch(1);

        GhostSource(DebeziumConfig config) {
            super(config, null);
        }

        @Override
        public ICancellable subscribe(Consumer<ChangeEvent> action) {
            subscribed.countDown();
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
                public void appendOnCancel(Consumer<String> task) {
                }

                @Override
                public void removeOnCancel(Consumer<String> task) {
                }
            };
        }

        @Override
        public synchronized void stop() {
            // no-op: the ghost engine ignores stop
        }

        @Override
        public boolean isEngineTerminated() {
            return terminated;
        }
    }

    /** Function wired to a GhostSource; run() blocks until cancelled (live-subscription shape). */
    static GhostFunction ghostFunction(String connectorName, GhostSource ghost) {
        DebeziumConfig config = config(connectorName);
        return new GhostFunction(config, ghost);
    }

    static class GhostFunction extends DebeziumCdcSourceFunction {
        final GhostSource ghost;
        final CountDownLatch runDone = new CountDownLatch(1);

        GhostFunction(DebeziumConfig config, GhostSource ghost) {
            super(config, (ICredentialProvider) null);
            this.ghost = ghost;
        }

        @Override
        protected DebeziumMessageSource createMessageSource(DebeziumConfig cfg,
                                                            NopStreamOffsetBackingStore store) {
            return ghost;
        }

        @Override
        public void run(SourceFunction.SourceContext<ChangeEvent> ctx) throws Exception {
            try {
                super.run(ctx);
            } finally {
                runDone.countDown();
            }
        }
    }

    /** Runs fn.run(ctx) on a separate thread (TestDebeziumCdcSourceErrorPropagation shape). */
    private static Thread runOnThread(DebeziumCdcSourceFunction fn) {
        Thread runner = new Thread(() -> {
            try {
                fn.run(new SourceFunction.SourceContext<>() {
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
                });
            } catch (Throwable ignored) {
                // run-finally verification declares ghosts; the outcome is asserted via logs
            }
        });
        runner.setDaemon(true);
        runner.start();
        return runner;
    }

    @AfterEach
    void tearDown() {
        // nothing persistent
    }

    @Test
    void truncateForDrainFailsTypedOnGhostEngine_C3_014() throws Exception {
        GhostSource ghost = new GhostSource(config("ghost-truncate"));
        GhostFunction fn = ghostFunction("ghost-truncate", ghost);
        Thread runner = runOnThread(fn);
        runner.setName("ghost-truncate-runner");
        assertTrue(ghost.subscribed.await(5, TimeUnit.SECONDS), "run must subscribe");
        // the engine "survives" the stop chain
        ghost.terminated = false;

        StreamException ex = assertThrows(StreamException.class, fn::truncateForDrain,
                "pre-fix truncateForDrain stopped the source and returned — a surviving ghost "
                        + "engine was invisible (C3-014 ②)");
        assertTrue(String.valueOf(ex).contains("ghost engine risk"));

        assertTrue(fn.runDone.await(15, TimeUnit.SECONDS), "run() must finish after the drain");
        runner.join(5000);
    }

    @Test
    void cancelDeclaresGhostWithoutThrowing_C3_014() throws Exception {
        GhostSource ghost = new GhostSource(config("ghost-cancel"));
        GhostFunction fn = ghostFunction("ghost-cancel", ghost);
        Thread runner = runOnThread(fn);
        assertTrue(ghost.subscribed.await(5, TimeUnit.SECONDS), "run must subscribe");
        ghost.terminated = false;

        Logger fnLogger = (Logger) LoggerFactory.getLogger(DebeziumCdcSourceFunction.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        fnLogger.addAppender(appender);
        try {
            // cancel() must not throw into framework teardown, but the ghost risk stays
            // visible as an explicit ERROR (rule 24)
            fn.cancel();
        } finally {
            fnLogger.detachAppender(appender);
        }

        assertTrue(appender.list.stream().anyMatch(e -> String.valueOf(e.getFormattedMessage())
                        .contains("engine-ghost-risk")),
                "pre-fix cancel produced no ghost-risk signal at all (C3-014 ②)");

        assertTrue(fn.runDone.await(15, TimeUnit.SECONDS), "run() must finish after cancel");
        runner.join(5000);
    }

    @Test
    void readerCloseFailsTypedOnGhostEngine_C3_014() throws Exception {
        GhostSource ghost = new GhostSource(config("reader-ghost"));
        AtomicBoolean stubCancelled = new AtomicBoolean(false);
        DebeziumCdcSourceReader reader = new DebeziumCdcSourceReader(config("reader-ghost"),
                null, null) {
            @Override
            protected DebeziumCdcSourceFunction createEmbeddedFunction(DebeziumConfig engineConfig) {
                return new DebeziumCdcSourceFunction(engineConfig, (ICredentialProvider) null) {
                    @Override
                    public void run(SourceFunction.SourceContext<ChangeEvent> ctx) {
                        // stub body: the engine lifecycle is stubbed; terminate on cancel
                        while (!stubCancelled.get()) {
                            try {
                                Thread.sleep(20);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }
                    }
                };
            }
        };
        reader.setContextForTest(readerCtx(0, 1));
        reader.start();
        reader.addSplits(List.of(new DebeziumTableSplit("db.a")));
        DebeziumCdcSourceFunction fn = reader.getEmbeddedFunctionForTest();
        // bind the ghost engine as the embedded function's message source
        Field sourceField = DebeziumCdcSourceFunction.class.getDeclaredField("source");
        sourceField.setAccessible(true);
        sourceField.set(fn, ghost);
        ghost.terminated = false;
        // release the stub runner first (its join is the plan-2343 face, kept verbatim);
        // the ghost verification below is the NEW contract face under test
        stubCancelled.set(true);
        Thread.sleep(200);

        StreamException ex = assertThrows(StreamException.class, () -> reader.close(),
                "reader close must verify engine TERMINATION, not merely issue stop (C3-014 ②; "
                        + "pre-fix a latch-exited runner with a surviving ghost closed clean)");
        assertTrue(String.valueOf(ex).contains("ghost engine risk"));
    }

    static SourceReaderContext readerCtx(int subtask, int parallelism) {
        return new SourceReaderContext(subtask, parallelism, new SplitAssignmentProxy() {
            @Override
            public void requestSplits(int subtaskIndex, Optional<Throwable> reason) {
            }

            @Override
            public void reportFinishedSplits(int subtaskIndex, List<String> finishedSplitIds) {
            }
        });
    }
}
