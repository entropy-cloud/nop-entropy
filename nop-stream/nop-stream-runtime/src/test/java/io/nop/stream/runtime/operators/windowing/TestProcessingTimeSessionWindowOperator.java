/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.operators.windowing;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.functions.ProcessWindowFunction;
import io.nop.stream.core.common.typeutils.TypeSerializer;
import io.nop.stream.core.operators.HeapInternalTimerService;
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.streamrecord.LatencyMarker;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.util.OutputTag;
import io.nop.stream.core.windowing.assigners.ProcessingTimeSessionWindows;
import io.nop.stream.core.windowing.assigners.WindowAssigner;
import io.nop.stream.core.windowing.triggers.ProcessingTimeTrigger;
import io.nop.stream.core.windowing.triggers.Trigger;
import io.nop.stream.core.windowing.windows.TimeWindow;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-1 (plan 369 Phase 2): processing-time session windows drive the MergingWindowSet
 * pipeline end-to-end — records arriving within the gap are merged into ONE session and
 * fire once on processing time; a record arriving after the previous session's end starts
 * a separate session.
 *
 * <p>Time control: the timer service's processing-time clock is the wall clock (records
 * sent back-to-back are within the gap by construction); due timers are drained via
 * {@code fireProcessingTimeTimers(Long.MAX_VALUE)}.
 */
public class TestProcessingTimeSessionWindowOperator {

    /** Windows seen by the user function, with the element values per firing. */
    static final List<String> FIRED = new ArrayList<>();

    static class RecordingProcessWindowFunction
            implements ProcessWindowFunction<Integer, String, String, TimeWindow> {
        private static final long serialVersionUID = 1L;

        @Override
        public void process(String key, TimeWindow window, Iterable<Integer> input,
                            Context context, Collector<String> out) {
            StringBuilder values = new StringBuilder();
            for (Integer value : input) {
                if (values.length() > 0) {
                    values.append(',');
                }
                values.append(value);
            }
            FIRED.add(window + " -> [" + values + ']');
            out.collect(window + " -> [" + values + ']');
        }
    }

    static class RecordingOutput implements Output<StreamRecord<String>> {
        final List<String> elements = new ArrayList<>();

        @Override
        public void collect(StreamRecord<String> record) {
            elements.add(record.getValue());
        }

        @Override
        public <X> void collect(OutputTag<X> outputTag, StreamRecord<X> record) {
        }

        @Override
        public void emitWatermark(Watermark mark) {
        }

        @Override
        public void emitWatermarkStatus(WatermarkStatus watermarkStatus) {
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

    static class SimpleTimeWindowSerializer implements TypeSerializer<TimeWindow> {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isImmutableType() {
            return true;
        }

        @Override
        public TypeSerializer<TimeWindow> duplicate() {
            return this;
        }

        @Override
        public TimeWindow createInstance() {
            return new TimeWindow(0, 0);
        }

        @Override
        public TimeWindow copy(TimeWindow from) {
            return new TimeWindow(from.getStart(), from.getEnd());
        }

        @Override
        public TimeWindow copy(TimeWindow from, TimeWindow reuse) {
            return new TimeWindow(from.getStart(), from.getEnd());
        }

        @Override
        public int getLength() {
            return -1;
        }
    }

    static class SimpleStringSerializer implements TypeSerializer<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isImmutableType() {
            return true;
        }

        @Override
        public TypeSerializer<String> duplicate() {
            return this;
        }

        @Override
        public String createInstance() {
            return "";
        }

        @Override
        public String copy(String from) {
            return from;
        }

        @Override
        public String copy(String from, String reuse) {
            return from;
        }

        @Override
        public int getLength() {
            return -1;
        }
    }

    @SuppressWarnings("unchecked")
    private WindowOperator<String, Integer, Iterable<Integer>, String, TimeWindow> newOperator(long gap) {
        WindowOperatorBuilder<Integer, String, TimeWindow> builder = new WindowOperatorBuilder<>();
        return builder
                .windowAssigner((WindowAssigner<? super Integer, TimeWindow>)
                        ProcessingTimeSessionWindows.withGap(gap))
                .trigger((Trigger<? super Integer, ? super TimeWindow>) ProcessingTimeTrigger.create())
                .allowedLateness(0L)
                .keySelector((KeySelector<Integer, String>) v -> "key1")
                .keyClass(String.class)
                .keySerializer(new SimpleStringSerializer())
                .windowSerializer(new SimpleTimeWindowSerializer())
                .process(new RecordingProcessWindowFunction(), Integer.class);
    }

    @Test
    void processingTimeSessionMergesWithinGapAndFiresSeparately() throws Exception {
        FIRED.clear();
        long gap = 300L;

        WindowOperator<String, Integer, Iterable<Integer>, String, TimeWindow> operator =
                newOperator(gap);
        RecordingOutput output = new RecordingOutput();
        operator.setOutput((Output) output);
        operator.open();
        try {
            // Two records sent back-to-back: both processing-time session windows
            // overlap by construction and must merge into ONE session.
            operator.processElement(new StreamRecord<>(1, 0));
            operator.processElement(new StreamRecord<>(2, 0));
            assertEquals(0, output.elements.size(), "no fire before the processing time passes the session end");

            // Drain all processing-time timers: the merged session fires exactly once
            // with both records, then cleans up.
            ((HeapInternalTimerService<String, TimeWindow>) operator.internalTimerService)
                    .fireProcessingTimeTimers(Long.MAX_VALUE);

            assertEquals(1, output.elements.size(),
                    "records within the gap must merge into a single session firing; fired: " + FIRED);
            assertTrue(FIRED.get(0).contains("[1,2]"),
                    "the merged session must contain both records; fired: " + FIRED);

            // A record arriving well past the merged session's end starts a NEW session.
            Thread.sleep(gap + 200);
            operator.processElement(new StreamRecord<>(3, 0));
            ((HeapInternalTimerService<String, TimeWindow>) operator.internalTimerService)
                    .fireProcessingTimeTimers(Long.MAX_VALUE);

            assertEquals(2, output.elements.size(),
                    "a record after the session gap must form a separate session; fired: " + FIRED);
            assertTrue(FIRED.get(1).contains("[3]"),
                    "the second session must contain only the new record; fired: " + FIRED);
        } finally {
            operator.close();
        }
    }
}
