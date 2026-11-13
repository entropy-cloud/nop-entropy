/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.fraud.scenario;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.nop.api.core.util.ICancellable;
import io.nop.message.debezium.ChangeEvent;
import io.nop.message.debezium.DebeziumConfig;
import io.nop.message.debezium.DebeziumMessageSource;
import io.nop.message.debezium.engine.NopStreamOffsetBackingStore;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.fraud.scenario.ReplayableCdcSourceFunction.CdcEventFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.nop.stream.fraud.scenario.ReplayableCdcSourceFunction.REPLAY_INDEX_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * CRA 8.45 R4-05 (plan 2538) W-02 / W-03 / W-10 — the S1 replay engine lifecycle
 * guards, plus the C10-008 rider halves (emitter bounded termination +
 * double-subscribe contract) and the C.3 combination matrix.
 *
 * <p>W-02 (1.3-MA-03, driver 红② / C10-005 acceptance gate, same diff): a replay
 * failure must end the run FAILED (typed rethrow through the subclass override), not
 * as a disguised EOS. Injection seam: a non-numeric replay index under
 * {@code REPLAY_INDEX_KEY} (package-visible) written through the public
 * {@code NopStreamOffsetBackingStore.set} — the emitter fails on its FIRST statement,
 * deterministically outside the declared S-1 late-failure window.
 *
 * <p>W-03 (1.3-MA-04, driver 红③): the cancel-callback registry delegates to the
 * platform Cancellable — cancel is idempotent (callbacks run exactly once across
 * racing cancel paths), a throwing callback does not block the rest, and stop() is
 * symmetric (it drives the registry instead of bypassing it).
 *
 * <p>W-10 (1.3-C6-005, driver 红⑩ / C10-008 double-subscribe half): a second
 * subscribe on the same engine typed-fails; the first subscriber survives with the
 * replay index written by a single writer.
 */
public class TestReplayLifecycleGuardsR405 {

    private static final String CONNECTOR = "test-replay-lifecycle-r405";

    @AfterEach
    public void clearConnectorRegistry() {
        NopStreamOffsetBackingStore.clearConnector(CONNECTOR);
    }

    // ------------------------------------------------------------------
    // harness (mirrors TestReplayableCdcSourceFunction — its assertions incl.
    // the :130 cancel-does-not-throw anchor stay untouched)
    // ------------------------------------------------------------------

    private static List<Map<String, Object>> fixture(int count) {
        List<Map<String, Object>> events = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            events.add(CdcEventFixtures.spec("c", 1000L + i, "tx-" + i, "user-" + i,
                    "100", "NYC", "PURCHASE"));
        }
        return events;
    }

    private static DebeziumConfig config() {
        DebeziumConfig config = new DebeziumConfig();
        config.setName(CONNECTOR);
        config.setConnectorType("mysql");
        return config;
    }

    /** captures/exposes the replay engine (public supertype only — engine-level tests
     * drive it directly instead of through the parent run(), which would consume the
     * engine's single subscription slot itself). */
    static final class EngineCapture extends ReplayableCdcSourceFunction {
        volatile DebeziumMessageSource capturedEngine;

        EngineCapture(List<Map<String, Object>> specs, long emitDelayMs, long finishLingerMs) {
            super(config(), specs, emitDelayMs, finishLingerMs);
        }

        DebeziumMessageSource buildEngine() {
            if (capturedEngine == null) {
                capturedEngine = createMessageSource(config(), getOffsetStore());
            }
            return capturedEngine;
        }

        @Override
        protected DebeziumMessageSource createMessageSource(DebeziumConfig config,
                                                            NopStreamOffsetBackingStore offsetStore) {
            DebeziumMessageSource engine = super.createMessageSource(config, offsetStore);
            this.capturedEngine = engine;
            return engine;
        }
    }

    private static final class CollectingContext
            implements SourceFunction.SourceContext<ChangeEvent> {
        final List<ChangeEvent> collected = new ArrayList<>();

        @Override
        public void collect(ChangeEvent element) {
            collected.add(element);
        }

        @Override
        public void collectWithTimestamp(ChangeEvent element, long timestamp) {
            collected.add(element);
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
    }

    private static void injectNonNumericReplayIndex(ReplayableCdcSourceFunction source) {
        // C10-005 injection seam (zero product visibility change): the store's set()
        // is public and REPLAY_INDEX_KEY is package-visible to this test package
        source.getOffsetStore().set(Collections.singletonMap(REPLAY_INDEX_KEY,
                ByteBuffer.wrap("not-a-number".getBytes(StandardCharsets.UTF_8))), null);
    }

    /** C10-008 emitter bounded-termination pin (pure observation, no product tie). */
    private static void assertReplayEmitterTerminated() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (!replayEmitterAlive()) {
                return;
            }
            Thread.sleep(20);
        }
        fail("the replay emitter thread must terminate within the bounded window");
    }

    private static boolean replayEmitterAlive() {
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.isAlive() && "fraud-s1-replay-cdc".equals(t.getName())) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 红② (W-02 / C10-005 same diff): failure → FAILED, not disguised EOS
    // ------------------------------------------------------------------

    @Test
    public void replayFailureFailsTheTaskNotDisguisedEos() throws Exception {
        ReplayableCdcSourceFunction source = new ReplayableCdcSourceFunction(
                config(), fixture(3), 0L, 0L);
        source.initializeState(null);
        injectNonNumericReplayIndex(source);

        AtomicReference<Throwable> error = new AtomicReference<>();
        CollectingContext ctx = new CollectingContext();
        Thread runner = new Thread(() -> {
            try {
                source.run(ctx);
            } catch (Throwable t) {
                error.set(t);
            }
        });
        runner.start();
        runner.join(15000);
        assertTrue(!runner.isAlive(), "run() must terminate in bounded time");

        // (a) FAILED semantics: run() ends with a Throwable (the recorded replay
        // failure rethrown typed), never a clean return
        assertNotNull(error.get(),
                "a replay failure must fail the run (FAILED), not return like an EOS");
        assertTrue(error.get() instanceof NumberFormatException,
                () -> "the recorded replay failure is rethrown in the parent's "
                        + "throwPendingError form, got: " + error.get());

        // (b) zero events — the "partial events + normal return" face must not exist
        assertTrue(ctx.collected.isEmpty(),
                "the failure must not be masked by partial consumption");

        // C10-008 rider half: the emitter terminates in bounded time
        assertReplayEmitterTerminated();
    }

    /** C.3 row 1: a racing external cancel must not wash out the failure signal. */
    @Test
    public void racingExternalCancelDoesNotWashOutTheFailure() throws Exception {
        ReplayableCdcSourceFunction source = new ReplayableCdcSourceFunction(
                config(), fixture(3), 0L, 0L);
        source.initializeState(null);
        injectNonNumericReplayIndex(source);

        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread runner = new Thread(() -> {
            try {
                source.run(new CollectingContext());
            } catch (Throwable t) {
                error.set(t);
            }
        });
        runner.start();
        source.cancel(); // racing external teardown
        runner.join(15000);
        assertTrue(!runner.isAlive(), "run() must terminate in bounded time");
        assertNotNull(error.get(),
                "the replay failure must surface FAILED even under a racing cancel");
        assertTrue(error.get() instanceof NumberFormatException,
                () -> "unexpected failure: " + error.get());
        assertReplayEmitterTerminated();
    }

    /**
     * C.3 row 2: after a failed replay the subscription is cancelled (mechanism
     * reason) and the engine stays one-shot (second subscribe typed-fails).
     */
    @Test
    public void subscriptionAfterFailureIsCancelledAndEngineStaysOneShot() throws Exception {
        EngineCapture source = new EngineCapture(fixture(3), 0L, 0L);
        source.initializeState(null);
        DebeziumMessageSource engine = source.buildEngine();
        injectNonNumericReplayIndex(source);

        ICancellable sub = engine.subscribe(event -> { /* no-op */ });
        // the emitter fails on its first statement and winds down (bounded)
        assertReplayEmitterTerminated();
        assertTrue(!sub.isCancelled(),
                "without a teardown the subscription is not yet cancelled");

        // teardown (parent cancel → engine stop in production): cancels the
        // subscription through the delegate with the mechanism reason
        engine.stop();
        assertTrue(sub.isCancelled(),
                "after the teardown the subscription must be cancelled");
        assertEquals(ICancellable.CANCEL_REASON_STOP, sub.getCancelReason(),
                "the stop path carries the mechanism reason");
        assertThrows(StreamException.class, () -> engine.subscribe(event -> { }),
                "the engine stays one-shot after a failure (W-10 gate)");
    }

    // ------------------------------------------------------------------
    // 红③ (W-03): idempotent cancel + per-callback isolation + stop symmetry
    // ------------------------------------------------------------------

    @Test
    public void cancelIsIdempotentAndIsolatesCallbackFailures() throws Exception {
        EngineCapture source = new EngineCapture(fixture(6), 50L, 0L);
        source.initializeState(null);
        DebeziumMessageSource engine = source.buildEngine();

        AtomicInteger firstCount = new AtomicInteger();
        AtomicInteger thirdCount = new AtomicInteger();
        ICancellable sub = engine.subscribe(event -> { });

        sub.appendOnCancel(reason -> {
            firstCount.incrementAndGet();
            throw new IllegalStateException("probe callback failure (isolation leg)");
        });
        sub.appendOnCancel(reason -> thirdCount.incrementAndGet());

        // cancel DURING the paced replay — the emitter is sleeping between events,
        // so this external cancel deterministically runs the callbacks first
        sub.cancel("kill");
        assertEquals(1, firstCount.get(),
                "the throwing callback must run exactly once (check-and-set idempotency)");
        assertEquals(1, thirdCount.get(),
                "a throwing callback must not block the rest (per-callback isolation)");
        assertTrue(sub.isCancelled());
        assertEquals("kill", sub.getCancelReason());

        // the emitter winds down; its teardown path re-cancels — the delegate's
        // check-and-set keeps every callback at exactly one execution
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline && replayEmitterAlive()) {
            Thread.sleep(10);
        }
        assertEquals(1, firstCount.get(), "callbacks must not re-run on teardown cancel");
        assertEquals(1, thirdCount.get(), "callbacks must not re-run on teardown cancel");
        assertReplayEmitterTerminated();
    }

    @Test
    public void stopFiresTheRegisteredCallbacksSymmetrically() throws Exception {
        EngineCapture source = new EngineCapture(fixture(6), 200L, 0L);
        source.initializeState(null);
        DebeziumMessageSource engine = source.buildEngine();

        AtomicInteger count = new AtomicInteger();
        ICancellable sub = engine.subscribe(event -> { });
        sub.appendOnCancel(reason -> count.incrementAndGet());

        // stop() must drive the REGISTERED subscription (symmetry leg) — the emitter
        // is parked in its 200ms pacing, so nothing else has cancelled yet
        engine.stop();
        assertEquals(1, count.get(),
                "stop() must fire the registered callbacks (registry no longer bypassed)");
        assertEquals(ICancellable.CANCEL_REASON_STOP, sub.getCancelReason());

        // the emitter exits via the loop's cancelled flag; teardown cancel is
        // idempotent — the count stays exactly one
        long deadline = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < deadline && replayEmitterAlive()) {
            Thread.sleep(10);
        }
        assertEquals(1, count.get(),
                "stop() + teardown cancel must run the callbacks exactly once (idempotent)");
        assertReplayEmitterTerminated();
    }

    // ------------------------------------------------------------------
    // 红⑩ (W-10 / C10-008 double-subscribe half): one-shot subscription gate
    // ------------------------------------------------------------------

    @Test
    public void doubleSubscribeFailsTypedAndFirstSubscriberSurvives() throws Exception {
        EngineCapture source = new EngineCapture(fixture(5), 5L, 0L);
        source.initializeState(null);
        DebeziumMessageSource engine = source.buildEngine();

        List<ChangeEvent> first = Collections.synchronizedList(new ArrayList<>());
        ICancellable sub1 = engine.subscribe(first::add);

        StreamException ex = assertThrows(StreamException.class,
                () -> engine.subscribe(event -> { }),
                "a second subscription must typed-fail (one-shot replay engine)");
        assertTrue(ex.getMessage().contains("already called"),
                () -> "typed rejection must identify the gate: " + ex.getMessage());

        // first subscriber survives: the full fixture flows to IT alone, and the
        // replay index has a single writer ending at the fixture size
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline
                && first.size() < 5 && replayEmitterAlive()) {
            Thread.sleep(10);
        }
        assertEquals(5, first.size(), "the first subscriber must run to completion");
        ByteBuffer idx = source.getOffsetStore().getOffsets().get(REPLAY_INDEX_KEY);
        assertNotNull(idx, "the replay index must be checkpointed");
        assertEquals("5", StandardCharsets.UTF_8.decode(
                ByteBuffer.wrap(idx.array(), idx.position(), idx.remaining())).toString(),
                "the replay index must end at the fixture size (single writer)");
        // teardown (parent cancel → engine stop in production) cancels the
        // subscription through the delegate
        engine.stop();
        assertTrue(sub1.isCancelled(), "the teardown must cancel the subscription");
        assertReplayEmitterTerminated();
    }

    /** happy path control: a normal bounded run self-cancels without a failure (C.3 row 5). */
    @Test
    public void normalCompletionSelfCancelsWithoutFailure() throws Exception {
        ReplayableCdcSourceFunction source = new ReplayableCdcSourceFunction(
                config(), fixture(3), 0L, 0L);
        source.initializeState(null);
        CollectingContext ctx = new CollectingContext();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread runner = new Thread(() -> {
            try {
                source.run(ctx);
            } catch (Throwable t) {
                error.set(t);
            }
        });
        runner.start();
        runner.join(15000);
        assertTrue(!runner.isAlive(), "run() must terminate in bounded time");
        assertNull(error.get(), "a normal bounded run must not throw");
        assertEquals(3, ctx.collected.size(), "the full fixture must be emitted");
        assertReplayEmitterTerminated();
    }
}
