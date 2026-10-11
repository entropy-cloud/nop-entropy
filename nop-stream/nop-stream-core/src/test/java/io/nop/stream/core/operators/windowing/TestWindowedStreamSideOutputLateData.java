/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.operators.windowing;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.nop.stream.core.common.eventtime.WatermarkStrategy;
import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.typeinfo.TypeInformation;
import io.nop.stream.core.common.typeinfo.UnknownTypeInformation;
import io.nop.stream.core.datastream.DataStream;
import io.nop.stream.core.datastream.DataStreamImpl;
import io.nop.stream.core.datastream.KeyedStream;
import io.nop.stream.core.datastream.KeyedStreamImpl;
import io.nop.stream.core.datastream.SingleOutputStreamOperator;
import io.nop.stream.core.datastream.WindowedStream;
import io.nop.stream.core.datastream.WindowedStreamImpl;
import io.nop.stream.core.environment.StreamExecutionEnvironment;
import io.nop.stream.core.model.StreamComponents;
import io.nop.stream.core.operators.TimestampsAndWatermarksOperator;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.util.OutputTag;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.runtime.operators.windowing.WindowOperatorFactoryImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-2+09e① (plan 369 Phase 2): the late-data side output becomes a PUBLIC API —
 * {@code WindowedStream.sideOutputLateData(OutputTag)} (previously the
 * {@code WindowOperatorBuilder.lateDataOutputTag} setter was unreachable) and
 * {@code SingleOutputStreamOperator.getSideOutput(OutputTag)} retrieves the emitted
 * late records. End-to-end through the local embedded execution.
 */
public class TestWindowedStreamSideOutputLateData {

    static class KeyValue {
        final String key;
        final int value;
        final long timestamp;

        KeyValue(String key, int value, long timestamp) {
            this.key = key;
            this.value = value;
            this.timestamp = timestamp;
        }

        public String getKey() {
            return key;
        }
    }

    static class SumAggregateFunction
            implements io.nop.stream.core.common.functions.AggregateFunction<KeyValue, int[], Integer> {
        private static final long serialVersionUID = 1L;

        @Override
        public int[] createAccumulator() {
            return new int[1];
        }

        @Override
        public int[] add(KeyValue value, int[] accumulator) {
            accumulator[0] += value.value;
            return accumulator;
        }

        @Override
        public Integer getResult(int[] accumulator) {
            return accumulator[0];
        }

        @Override
        public int[] merge(int[] a, int[] b) {
            a[0] += b[0];
            return a;
        }
    }

    @SuppressWarnings("unchecked")
    private final OutputTag<KeyValue> lateTag =
            new OutputTag<>("late-data-g2",
                    (TypeInformation<KeyValue>) UnknownTypeInformation.INSTANCE);

    @AfterEach
    void cleanRegistry() {
        io.nop.stream.core.datastream.SideOutputRegistry.clear();
    }

    @Test
    void lateRecordsReachSideOutputAndAreRetrievable() throws Exception {
        List<KeyValue> lateRecords = Collections.synchronizedList(new ArrayList<>());
        List<Integer> windowResults = Collections.synchronizedList(new ArrayList<>());

        List<KeyValue> events = Arrays.asList(
                new KeyValue("key1", 1, 10),
                // Pushes the watermark to 110: window [0,100) fires and cleans up
                // (allowedLateness=0).
                new KeyValue("key1", 2, 120),
                // Late: its window [0,100) was already skipped and the record is past
                // the allowed-lateness bound — must land in the side output.
                new KeyValue("key1", 3, 50));

        StreamExecutionEnvironment env = StreamExecutionEnvironment.createTestEnvironment();

        WatermarkStrategy<KeyValue> strategy = WatermarkStrategy
                .<KeyValue>forBoundedOutOfOrderness(Duration.ofMillis(10))
                .withTimestampAssigner((event, ts) -> event.timestamp);

        SingleOutputStreamOperator<KeyValue> timestamped = env.fromCollection(events)
                .transform("TimestampsAndWatermarks",
                        (TypeInformation<KeyValue>) UnknownTypeInformation.INSTANCE,
                        new TimestampsAndWatermarksOperator<>(strategy, 0));

        DataStreamImpl<KeyValue> timestampedImpl = (DataStreamImpl<KeyValue>) timestamped;

        KeyedStream<KeyValue, String> keyed = new KeyedStreamImpl<>(
                timestampedImpl.getEnvironment(),
                timestampedImpl.getTransformation(),
                (KeySelector<KeyValue, String>) KeyValue::getKey);

        StreamComponents components = new StreamComponents();
        components.setWindowOperatorFactory(new WindowOperatorFactoryImpl());

        // The public API shape under test: WindowedStream.sideOutputLateData(...) —
        // called through the INTERFACE — then the window operation, then
        // getSideOutput(...) retrieval.
        WindowedStream<KeyValue, String, io.nop.stream.core.windowing.windows.TimeWindow> windowed =
                new WindowedStreamImpl<>(keyed, TumblingEventTimeWindows.of(100))
                        .withComponents(components);

        windowed = windowed.sideOutputLateData(lateTag);

        SingleOutputStreamOperator<Integer> aggregated = windowed.aggregate(new SumAggregateFunction());
        aggregated.sink((SinkFunction<Integer>) windowResults::add);

        DataStream<KeyValue> lateStream = aggregated.getSideOutput(lateTag);
        lateStream.collect((SinkFunction<KeyValue>) lateRecords::add);

        env.execute("g2-late-data-side-output");

        // Main output unaffected: event 1@10 fires window [0,100) when the watermark
        // advances to 110; event 2@120 fires window [100,200) at end-of-stream.
        assertEquals(Arrays.asList(1, 2), windowResults,
                "window results must be produced; got: " + windowResults);
        // The late record is retrievable through getSideOutput — not dropped.
        assertEquals(1, lateRecords.size(),
                "exactly one late record must reach the side output; got: " + lateRecords);
        assertEquals(50, lateRecords.get(0).timestamp,
                "the late record must be the raw (unaggregated) input record");
    }

    // Sanity guard for the operator-level emission path: the same tag wired into a
    // directly-driven WindowOperator routes late records through output.collect(tag, ...).
    @Test
    @SuppressWarnings("unchecked")
    void sideOutputLateDataTagReachesTheOperator() throws Exception {
        List<Integer> lateRecords = new ArrayList<>();

        WindowOperatorBuilder<Integer, String, io.nop.stream.core.windowing.windows.TimeWindow> builder =
                new WindowOperatorBuilder<>();
        WindowOperator<String, Integer, Integer, Integer, io.nop.stream.core.windowing.windows.TimeWindow> operator =
                builder
                        .windowAssigner(TumblingEventTimeWindows.of(100L))
                        .trigger(io.nop.stream.core.windowing.triggers.EventTimeTrigger.create())
                        .allowedLateness(0L)
                        .keySelector((KeySelector<Integer, String>) v -> "key1")
                        .keyClass(String.class)
                        .keySerializer(new SimpleStringSerializer())
                        .windowSerializer(new SimpleTimeWindowSerializer())
                        .lateDataOutputTag(new OutputTag<>("late-op",
                                (io.nop.stream.core.common.typeinfo.TypeInformation<Integer>)
                                        UnknownTypeInformation.INSTANCE))
                        .reduce((a, b) -> a, Integer.class);

        io.nop.stream.core.test.TestOutput<Integer> output = new io.nop.stream.core.test.TestOutput<>();
        operator.setOutput((io.nop.stream.core.operators.Output) output);
        operator.open();
        try {
            operator.processElement(new StreamRecord<>(10, 10));
            operator.processWatermark(new io.nop.stream.core.streamrecord.watermark.Watermark(100));
            operator.processElement(new StreamRecord<>(5, 5));

            assertEquals(1, output.getSideOutputs().size(),
                    "the late record must be emitted to the configured tag");
        } finally {
            operator.close();
        }
    }

    static class SimpleTimeWindowSerializer
            implements io.nop.stream.core.common.typeutils.TypeSerializer<io.nop.stream.core.windowing.windows.TimeWindow> {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isImmutableType() {
            return true;
        }

        @Override
        public io.nop.stream.core.common.typeutils.TypeSerializer<io.nop.stream.core.windowing.windows.TimeWindow> duplicate() {
            return this;
        }

        @Override
        public io.nop.stream.core.windowing.windows.TimeWindow createInstance() {
            return new io.nop.stream.core.windowing.windows.TimeWindow(0, 0);
        }

        @Override
        public io.nop.stream.core.windowing.windows.TimeWindow copy(io.nop.stream.core.windowing.windows.TimeWindow from) {
            return new io.nop.stream.core.windowing.windows.TimeWindow(from.getStart(), from.getEnd());
        }

        @Override
        public io.nop.stream.core.windowing.windows.TimeWindow copy(io.nop.stream.core.windowing.windows.TimeWindow from,
                                                                    io.nop.stream.core.windowing.windows.TimeWindow reuse) {
            return new io.nop.stream.core.windowing.windows.TimeWindow(from.getStart(), from.getEnd());
        }

        @Override
        public int getLength() {
            return -1;
        }
    }

    static class SimpleStringSerializer
            implements io.nop.stream.core.common.typeutils.TypeSerializer<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isImmutableType() {
            return true;
        }

        @Override
        public io.nop.stream.core.common.typeutils.TypeSerializer<String> duplicate() {
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
}
