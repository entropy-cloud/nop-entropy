/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.cep.operator;

import io.nop.stream.cep.CepTestUtils;
import io.nop.stream.cep.Event;
import io.nop.stream.cep.NopCepConfigs;
import io.nop.stream.cep.functions.PatternProcessFunction;
import io.nop.stream.cep.functions.TimedOutPartialMatchHandler;
import io.nop.stream.cep.nfa.compiler.NFACompiler;
import io.nop.stream.cep.pattern.Pattern;
import io.nop.stream.cep.pattern.conditions.SimpleCondition;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.operators.ProcessingTimeService;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.core.util.Collector;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.43 R4-03 (plan 2536) red ⑦ + red ⑩ — cep operator growth-envelope
 * pins.
 *
 * <p>red ⑦ — W-07 (MA-16=C5-001, C7-005 merged as same-defect duplicate
 * confirmed-and-merged): {@code reconciledTimerKeys} must shrink when a key's
 * NFA state is fully cleared ({@code resetNfaStateIfFullyTimedOut}) — pre-fix
 * the set grows monotonically per distinct key and never shrinks (every key
 * ever drained stays forever, holding its strong key reference). Post-fix the
 * marker is removed at the full-clear point; a later re-drain of the same key
 * re-reconciles (idempotent no-op — empty legacy delta).
 *
 * <p>red ⑩ — W-10 (C8-002): the per-subtask match buffer must be bounded —
 * {@code nop.cep.pipeline.match-buffer.max-events} (default 100000) typed
 * fail-fast on overflow, {@code <= 0} fails fast at open, bucket consumption
 * decrements the counter (no false-kill of normal load after a drain).
 *
 * <p>Size observation via the {@code reconciledTimerKeys} field reflection so
 * the red trial compiles and runs against the pre-fix product (the
 * package-visible {@code reconciledTimerKeysSizeForTesting()} hook is the
 * sanctioned S8 seam on the green side and mirrors the same state).
 */
class TestCepOperatorMatchEnvelopeR403 {

    private static final ProcessingTimeService MOCK_PTS = new ProcessingTimeService() {
        private long time = 1000;

        @Override
        public long getCurrentProcessingTime() {
            return time++;
        }

        @Override
        public ScheduledFuture<?> registerTimer(long timestamp, ProcessingTimeCallback target) {
            return null;
        }
    };

    private static class CombinedFunction
            extends PatternProcessFunction<Event, String>
            implements TimedOutPartialMatchHandler<Event> {

        final List<String> results = new ArrayList<>();

        @Override
        public void processMatch(Map<String, List<Event>> match, Context ctx, Collector<String> out) {
            results.add("match:" + match.get("start").get(0).getName());
        }

        @Override
        public void processTimedOutMatch(Map<String, List<Event>> match, Context ctx) {
            results.add("timeout:" + match.get("start").get(0).getName());
        }
    }

    @AfterEach
    void restoreCapOverride() {
        // Re-sample every used static ref from (cleared) system properties —
        // the platform test-framework channel for washing dynamic config
        // overrides (AbstractConfigProvider.reset contract).
        io.nop.api.core.config.AppConfig.getConfigProvider().reset();
    }

    /** Dynamic config override via the platform provider (varRefs snapshot at
     *  interface load, so System.setProperty alone is not visible). */
    private static void setCap(int value) {
        io.nop.api.core.config.AppConfig.getConfigProvider()
                .updateConfigValue(NopCepConfigs.MATCH_BUFFER_MAX_EVENTS, value);
    }

    private static Pattern<Event, ?> windowedPattern() {
        return Pattern.<Event>begin("start")
                .where(SimpleCondition.of(event -> event.getId() >= 42))
                .followedBy("end")
                .where(SimpleCondition.of(event -> event.getName().equals("end")))
                .within(Duration.ofMillis(10));
    }

    private CepOperator<Event, Integer, String> newEventTimeOperator(
            Pattern<Event, ?> pattern, PatternProcessFunction<Event, String> function,
            TestOutput<String> output) throws Exception {
        CepOperator<Event, Integer, String> operator = new CepOperator<>(
                new MinimalSerializer(),
                false,
                NFACompiler.compileFactory(pattern, function instanceof TimedOutPartialMatchHandler),
                null,
                null,
                function,
                null
        );
        operator.setStateBackend(new MemoryStateBackend());
        operator.setOutput(output);
        CepTestUtils.injectProcessingTimeService(operator, MOCK_PTS);
        operator.open();
        return operator;
    }

    /** Reflects the current reconciledTimerKeys size (works pre- and post-fix). */
    private static int reconciledTimerKeysSize(CepOperator<?, ?, ?> operator) throws Exception {
        Field field = CepOperator.class.getDeclaredField("reconciledTimerKeys");
        field.setAccessible(true);
        Set<?> keys = (Set<?>) field.get(operator);
        return keys == null ? 0 : keys.size();
    }

    // ------------------------------------------------------------------
    // red ⑦ — W-07 timerKeys cleanup
    // ------------------------------------------------------------------

    @Test
    void timerKeysMarkerRemovedWhenKeyFullyTimedOut() throws Exception {
        CombinedFunction function = new CombinedFunction();
        TestOutput<String> output = new TestOutput<>();
        CepOperator<Event, Integer, String> op =
                newEventTimeOperator(windowedPattern(), function, output);
        try {
            op.setCurrentKey(1);
            op.processElement(new StreamRecord<>(new Event(42, "a1"), 1));
            op.processWatermark(new Watermark(20));

            // The 10ms window has fully passed with no end event → the key's NFA
            // state is fully cleared. Its reconciled-ledger marker must go with it.
            assertEquals(0, reconciledTimerKeysSize(op),
                    "a fully-cleared key must not keep a reconciledTimerKeys entry "
                            + "(pre-fix the marker survives forever and the set grows "
                            + "monotonically per distinct key), got "
                            + reconciledTimerKeysSize(op));
        } finally {
            op.close();
        }
    }

    @Test
    void multiKeysEachConvergeOnFullClear() throws Exception {
        CombinedFunction function = new CombinedFunction();
        TestOutput<String> output = new TestOutput<>();
        CepOperator<Event, Integer, String> op =
                newEventTimeOperator(windowedPattern(), function, output);
        try {
            op.setCurrentKey(1);
            op.processElement(new StreamRecord<>(new Event(42, "a1"), 1));
            op.setCurrentKey(2);
            op.processElement(new StreamRecord<>(new Event(42, "a2"), 1));
            // The first drain of each key backfills its ledger marker; the
            // watermark then fully clears both keys' NFA state.
            op.processWatermark(new Watermark(20));

            assertEquals(0, reconciledTimerKeysSize(op),
                    "both drained keys fully cleared → the set must converge to empty "
                            + "(pre-fix both markers survive forever: the set never shrinks)");
        } finally {
            op.close();
        }
    }

    /**
     * rule 22 end-to-end chain (W-07 × W-10 combined face): processElement entry
     * → buffer-fill to the cap → typed overflow at the boundary → watermark
     * drain consumes the buckets (counter decremented) → the key's NFA fully
     * clears → timerKeys converges → the re-drained key still drains normally
     * — one continuous drive from the overload exit to the cleanup point.
     */
    @Test
    void combinedEnvelopeChainBufferOverflowDrainAndCleanupR403() throws Exception {
        setCap(4);
        CombinedFunction function = new CombinedFunction();
        TestOutput<String> output = new TestOutput<>();
        CepOperator<Event, Integer, String> op =
                newEventTimeOperator(windowedPattern(), function, output);
        try {
            op.setCurrentKey(1);
            // Entry: fill to the cap (cap-1 boundary passes).
            for (int i = 0; i < 4; i++) {
                op.processElement(new StreamRecord<>(new Event(42, "e" + i), 1 + i));
            }
            assertEquals(4, op.getPQSize(1));
            // Overload exit: the (cap+1)-th event typed-rejects.
            assertThrows(StreamException.class,
                    () -> op.processElement(new StreamRecord<>(new Event(42, "over"), 50)));
            // Watermark consumes the buckets → counter decremented (refill works).
            op.processWatermark(new Watermark(50));
            assertEquals(0, op.getPQSize(1), "the drain consumed all buckets");
            // Cleanup point: the drained key's NFA fully cleared → timerKeys converged.
            assertEquals(0, op.reconciledTimerKeysSizeForTesting(),
                    "key 1 converged after full clear (W-07 cleanup at the W-10 drained state)");
            // The chain restarts cleanly: refill + drain + convergence again.
            op.processElement(new StreamRecord<>(new Event(42, "e2"), 100));
            op.processWatermark(new Watermark(150));
            assertTrue(function.results.stream().anyMatch(r -> r.contains("e2")),
                    "the post-cleanup key must still drain normally, got: " + function.results);
            assertEquals(0, op.reconciledTimerKeysSizeForTesting(),
                    "the second lifecycle converges again");
        } finally {
            op.close();
        }
    }

    @Test
    void sizeHookMirrorsTheFieldState() throws Exception {
        // S8 seam leg: the package-visible watermark hook
        // (reconciledTimerKeysSizeForTesting) mirrors the raw field state.
        CombinedFunction function = new CombinedFunction();
        TestOutput<String> output = new TestOutput<>();
        CepOperator<Event, Integer, String> op =
                newEventTimeOperator(windowedPattern(), function, output);
        try {
            op.setCurrentKey(1);
            op.processElement(new StreamRecord<>(new Event(42, "a1"), 1));
            op.processWatermark(new Watermark(20));
            assertEquals(0, op.reconciledTimerKeysSizeForTesting(),
                    "the hook must report the converged (empty) marker set");
            op.processElement(new StreamRecord<>(new Event(42, "a2"), 100));
            op.processWatermark(new Watermark(130));
            assertEquals(0, op.reconciledTimerKeysSizeForTesting(),
                    "the re-drained key converges again (hook view)");
        } finally {
            op.close();
        }
    }

    @Test
    void reconcileAfterCleanupIsIdempotentNoOp() throws Exception {
        CombinedFunction function = new CombinedFunction();
        TestOutput<String> output = new TestOutput<>();
        CepOperator<Event, Integer, String> op =
                newEventTimeOperator(windowedPattern(), function, output);
        try {
            // Lifecycle 1: drain → full clear → marker removed.
            op.setCurrentKey(1);
            op.processElement(new StreamRecord<>(new Event(42, "a1"), 1));
            op.processWatermark(new Watermark(20));
            assertEquals(0, reconciledTimerKeysSize(op));

            // Lifecycle 2: the SAME key comes back (new event) — the re-reconcile
            // must be a no-op with zero side effects and the drain must still work.
            op.processElement(new StreamRecord<>(new Event(42, "b1"), 100));
            op.processWatermark(new Watermark(130));
            assertTrue(function.results.stream().anyMatch(r -> r.contains("b1")),
                    "a re-cleared key must still drain normally after cleanup, got: "
                            + function.results);
            assertEquals(0, reconciledTimerKeysSize(op),
                    "the re-drained key converges again after its second full clear");
        } finally {
            op.close();
        }
    }

    // ------------------------------------------------------------------
    // red ⑩ — W-10 match buffer bound
    // ------------------------------------------------------------------

    @Test
    void bufferOverflowTypedFailFast() throws Exception {
        setCap(4);
        CombinedFunction function = new CombinedFunction();
        TestOutput<String> output = new TestOutput<>();
        CepOperator<Event, Integer, String> op =
                newEventTimeOperator(windowedPattern(), function, output);
        try {
            op.setCurrentKey(1);
            // cap-1 boundary leg: 4 events pass.
            for (int i = 0; i < 4; i++) {
                op.processElement(new StreamRecord<>(new Event(42, "e" + i), 1 + i));
            }
            assertEquals(4, op.getPQSize(1), "cap-1 events must buffer normally");

            // The (cap+1)-th event must be typed-rejected, not silently buffered.
            StreamException e = assertThrows(StreamException.class,
                    () -> op.processElement(new StreamRecord<>(new Event(42, "overflow"), 50)),
                    "buffer overflow must fail typed (pre-fix the event silently buffers "
                            + "and the watermark-stalled backlog grows without bound)");
            assertEquals(NopStreamErrors.ERR_STREAM_STATE_ERROR.getErrorCode(), e.getErrorCode());
            String detail = String.valueOf(e.getMessage()) + e;
            assertTrue(detail.contains("100000") || detail.contains("max-events")
                            || detail.contains("buffer"),
                    "the failure must name the match-buffer envelope: " + detail);
            assertEquals(4, op.getPQSize(1),
                    "the rejected event must not enter the queue (no queue pollution)");
        } finally {
            op.close();
        }
    }

    @Test
    void bucketConsumptionDecrementsTheCounter() throws Exception {
        setCap(4);
        CombinedFunction function = new CombinedFunction();
        TestOutput<String> output = new TestOutput<>();
        CepOperator<Event, Integer, String> op =
                newEventTimeOperator(windowedPattern(), function, output);
        try {
            op.setCurrentKey(1);
            for (int i = 0; i < 4; i++) {
                op.processElement(new StreamRecord<>(new Event(42, "e" + i), 1 + i));
            }
            // Drain everything (bucket consumption decrements the in-flight count).
            op.processWatermark(new Watermark(50));
            assertEquals(0, op.getPQSize(1), "drain consumes all buckets");

            // Without a decrement a stale counter would reject this refill —
            // the refill must fully succeed (any throw fails the pin).
            for (int i = 0; i < 4; i++) {
                op.processElement(new StreamRecord<>(new Event(42, "x" + i), 100 + i));
            }
            assertEquals(4, op.getPQSize(1),
                    "after a drain the full cap must be available again "
                            + "(bucket consumption decrements the counter)");
        } finally {
            op.close();
        }
    }

    @Test
    void nonPositiveCapFailsFastAtOpen() {
        setCap(0);
        CombinedFunction function = new CombinedFunction();
        TestOutput<String> output = new TestOutput<>();
        CepOperator<Event, Integer, String> op = new CepOperator<>(
                new MinimalSerializer(),
                false,
                NFACompiler.compileFactory(windowedPattern(),
                        function instanceof TimedOutPartialMatchHandler),
                null,
                null,
                function,
                null
        );
        op.setStateBackend(new MemoryStateBackend());
        op.setOutput(output);
        CepTestUtils.injectProcessingTimeService(op, MOCK_PTS);
        StreamException e = assertThrows(StreamException.class, op::open,
                "a non-positive match-buffer cap is a nonsensical envelope — fail fast at open");
        assertEquals(NopStreamErrors.ERR_STREAM_INVALID_ARG.getErrorCode(), e.getErrorCode());
    }

    /** Minimal event serializer (same shape as TestCepOperatorLedgerDrain). */
    private static class MinimalSerializer
            implements io.nop.stream.core.common.typeutils.TypeSerializer<Event> {
        @Override
        public boolean isImmutableType() {
            return false;
        }

        @Override
        public io.nop.stream.core.common.typeutils.TypeSerializer<Event> duplicate() {
            return this;
        }

        @Override
        public Event createInstance() {
            return new Event();
        }

        @Override
        public Event copy(Event from) {
            return new Event(from.getId(), from.getName());
        }

        @Override
        public Event copy(Event from, Event reuse) {
            return new Event(from.getId(), from.getName());
        }

        @Override
        public int getLength() {
            return -1;
        }
    }
}
