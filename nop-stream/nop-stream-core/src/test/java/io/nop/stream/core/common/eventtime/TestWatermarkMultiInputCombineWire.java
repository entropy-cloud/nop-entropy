package io.nop.stream.core.common.eventtime;

import io.nop.stream.core.operators.AbstractStreamOperator;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;
import io.nop.stream.core.test.TestOutput;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wire-test for the multi-input watermark combine valve that backs
 * {@code processWatermark1}/{@code processWatermark2} on
 * {@link AbstractStreamOperator}.
 *
 * <p>P1-12 fix: lifts the prior "Anti-Hollow exemption" by exercising the
 * runtime valve wiring end-to-end through the operator's two-input watermark
 * methods. Verifies that:
 * <ul>
 *   <li>The combined watermark is the min of input1 and input2 (no advance while
 *       one input is at MIN_VALUE).</li>
 *   <li>Once both inputs advance, the operator emits the combined watermark to
 *       its output (wiring verified, not just unit math).</li>
 *   <li>Idleness correctly excludes an input from the combine (valve status
 *       propagation wired).</li>
 *   <li>Barrier-style monotonicity: combined watermark never regresses.</li>
 * </ul>
 *
 * <p>Plan {@code 2026-07-26-0804-2-parallel-execution-cep-correctness.md} Phase 3.
 */
public class TestWatermarkMultiInputCombineWire {

    @Test
    void combinedWatermarkAdvancesOnlyWhenBothInputsAdvance() throws Exception {
        RecordingOperator<String> op = new RecordingOperator<>();
        TestOutput<String> output = new TestOutput<>();
        op.setOutput(output);
        op.open();

        // Drive input 1 to 100 — combined cannot advance (input 2 still MIN_VALUE).
        op.processWatermark1(new Watermark(100));
        assertTrue(op.emittedWatermarks.isEmpty(),
                "No watermark should be emitted while one input is at MIN_VALUE");

        // Drive input 2 to 200 — now combined = min(100, 200) = 100, emit.
        op.processWatermark2(new Watermark(200));
        assertEquals(1, op.emittedWatermarks.size(),
                "One watermark should be emitted once both inputs have advanced");
        assertEquals(100L, op.emittedWatermarks.get(0),
                "Combined watermark must be the min of the two inputs");

        // Advance the non-min input — combined stays at 100 (no emit).
        op.emittedWatermarks.clear();
        op.processWatermark2(new Watermark(250));
        assertTrue(op.emittedWatermarks.isEmpty(),
                "Advancing the non-min input must not advance the combined watermark");

        // Advance the min input past the other — combined advances.
        op.processWatermark1(new Watermark(180));
        assertEquals(1, op.emittedWatermarks.size(),
                "Advancing the min input must advance and emit the new combined watermark");
        assertEquals(180L, op.emittedWatermarks.get(0));
    }

    @Test
    void idleInputExcludedFromCombine() throws Exception {
        RecordingOperator<String> op = new RecordingOperator<>();
        TestOutput<String> output = new TestOutput<>();
        op.setOutput(output);
        op.open();

        op.processWatermark1(new Watermark(100));
        op.processWatermark2(new Watermark(50));
        // Combined = 50 (input2 is min).
        assertEquals(1, op.emittedWatermarks.size());
        assertEquals(50L, op.emittedWatermarks.get(0));

        op.emittedWatermarks.clear();
        // Mark input 2 idle — input 2 is excluded from min, combined jumps to 100.
        op.processWatermarkStatus2(WatermarkStatus.IDLE);
        assertEquals(1, op.emittedWatermarks.size(),
                "Marking the min input idle must advance the combined watermark");
        assertEquals(100L, op.emittedWatermarks.get(0));
    }

    @Test
    void combinedWatermarkNeverRegresses() throws Exception {
        RecordingOperator<String> op = new RecordingOperator<>();
        TestOutput<String> output = new TestOutput<>();
        op.setOutput(output);
        op.open();

        op.processWatermark1(new Watermark(100));
        op.processWatermark2(new Watermark(100));
        // Combined = 100.
        assertEquals(100L, op.emittedWatermarks.get(0));

        op.emittedWatermarks.clear();
        // A lower watermark on input2 must not regress the combined output.
        op.processWatermark2(new Watermark(10));
        assertTrue(op.emittedWatermarks.isEmpty(),
                "Combined watermark must never regress on a lower input watermark");
    }

    /**
     * Minimal concrete {@link AbstractStreamOperator} subclass that records
     * the watermarks actually emitted to its output. Used to verify the
     * multi-input combine wiring through observable output behavior.
     */
    private static class RecordingOperator<OUT> extends AbstractStreamOperator<OUT>
            implements io.nop.stream.core.operators.OneInputStreamOperator<OUT, OUT> {
        private static final long serialVersionUID = 1L;

        final List<Long> emittedWatermarks = new ArrayList<>();

        @Override
        public void processElement(StreamRecord<OUT> element) throws Exception {
        }

        @Override
        public void processWatermark(Watermark mark) throws Exception {
            // Record every watermark the base class emits via the combined valve.
            // (Base class forwards to output.emitWatermark — we observe by
            // overriding the entry point and capturing the timestamp.)
            if (mark.getTimestamp() > Long.MIN_VALUE) {
                emittedWatermarks.add(mark.getTimestamp());
            }
            super.processWatermark(mark);
        }
    }
}
