/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.operators;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.common.eventtime.WatermarkOutput;
import io.nop.stream.core.common.eventtime.WatermarkStrategy;
import io.nop.stream.core.streamrecord.LatencyMarker;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;
import io.nop.stream.core.util.OutputTag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R6 KR-08 regression pin (plan 2324, operator half): the FIRST element after an
 * idle episode must reactivate the channel through
 * {@code OperatorWatermarkOutput.markActive()} so a
 * {@code WatermarkStatus.ACTIVE} travels downstream and the downstream InputGate
 * un-evicts the channel from watermark merging. The pre-fix code did a bare
 * {@code this.idle = false} — the flag flipped but no ACTIVE ever traveled, so a
 * channel that had been idle ONCE stayed event-time-frozen forever. Reverting
 * {@code processElement} to the bare flip turns this test red.
 */
class TestWatermarkIdleResumeAtOperator {

    static class TestEvent {
        final long timestamp;

        TestEvent(long timestamp) {
            this.timestamp = timestamp;
        }
    }

    /** Output capturing watermark STATUS transitions (the shared TestOutput swallows them). */
    static class StatusOutput implements Output<StreamRecord<TestEvent>> {
        final List<WatermarkStatus> statuses = new ArrayList<>();
        final List<Long> watermarks = new ArrayList<>();
        final List<Long> elementTimestamps = new ArrayList<>();

        @Override
        public void collect(StreamRecord<TestEvent> record) {
            elementTimestamps.add(record.getTimestamp());
        }

        @Override
        public <X> void collect(OutputTag<X> outputTag, StreamRecord<X> record) {
        }

        @Override
        public void emitWatermark(Watermark mark) {
            watermarks.add(mark.getTimestamp());
        }

        @Override
        public void emitWatermarkStatus(WatermarkStatus status) {
            statuses.add(status);
        }

        @Override
        public void emitLatencyMarker(LatencyMarker latencyMarker) {
        }

        @Override
        public void emitBarrier(CheckpointBarrier barrier) {
        }

        @Override
        public void close() {
        }
    }

    private static void forceIdle(TimestampsAndWatermarksOperator<TestEvent> op) throws Exception {
        Field idle = TimestampsAndWatermarksOperator.class.getDeclaredField("idle");
        idle.setAccessible(true);
        idle.setBoolean(op, true);
    }

    @Test
    void firstElementAfterIdleEpisodeEmitsActiveAndResumesWatermarks() throws Exception {
        WatermarkStrategy<TestEvent> strategy = WatermarkStrategy
                .<TestEvent>forBoundedOutOfOrderness(Duration.ofMillis(10))
                .withTimestampAssigner((event, ts) -> event.timestamp);
        TimestampsAndWatermarksOperator<TestEvent> op =
                new TimestampsAndWatermarksOperator<>(strategy, 0L);
        StatusOutput output = new StatusOutput();
        op.setOutput((Output) output);
        op.open();

        // a prior idle episode (the IDLE status itself is the AR-9 fix's contract)
        forceIdle(op);

        // the first element after the idle episode must reactivate the channel
        op.processElement(new StreamRecord<>(new TestEvent(100L)));

        assertTrue(output.statuses.contains(WatermarkStatus.ACTIVE),
                () -> "first element after idle must emit the ACTIVE transition, got: " + output.statuses
                        + " (a bare idle=false flip emits nothing and the downstream channel stays frozen)");
        assertTrue(output.elementTimestamps.contains(100L), "the element itself must flow");
        assertTrue(!output.watermarks.isEmpty(),
                "watermark emission must resume after reactivation (interval=0 emits per element)");
    }

    @Test
    void noActiveSpamWhenChannelAlreadyActive() throws Exception {
        WatermarkStrategy<TestEvent> strategy = WatermarkStrategy
                .<TestEvent>forBoundedOutOfOrderness(Duration.ofMillis(10))
                .withTimestampAssigner((event, ts) -> event.timestamp);
        TimestampsAndWatermarksOperator<TestEvent> op =
                new TimestampsAndWatermarksOperator<>(strategy, 0L);
        StatusOutput output = new StatusOutput();
        op.setOutput((Output) output);
        op.open();

        op.processElement(new StreamRecord<>(new TestEvent(100L)));
        assertEquals(List.of(), output.statuses,
                "an active channel must not emit spurious ACTIVE transitions");
    }
}
