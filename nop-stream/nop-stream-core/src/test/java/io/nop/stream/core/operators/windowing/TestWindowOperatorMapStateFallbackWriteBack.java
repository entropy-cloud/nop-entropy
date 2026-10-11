/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:   https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.operators.windowing;

import io.nop.stream.core.common.functions.KeySelector;
import io.nop.stream.core.common.functions.ProcessWindowFunction;
import io.nop.stream.core.common.state.MapState;
import io.nop.stream.core.common.state.MapStateDescriptor;
import io.nop.stream.core.common.state.backend.IKeyedStateBackend;
import io.nop.stream.core.common.state.backend.memory.MemoryKeyedStateBackend;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.test.TestOutput;
import io.nop.stream.core.util.Collector;
import io.nop.stream.core.windowing.AccumulationMode;
import io.nop.stream.core.windowing.assigners.TumblingEventTimeWindows;
import io.nop.stream.core.windowing.evictors.CountEvictor;
import io.nop.stream.core.windowing.triggers.EventTimeTrigger;
import io.nop.stream.core.windowing.windows.TimeWindow;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A12 regression (plan 01 quality-perf Phase 2): on the MapState fallback
 * layout (null windowStateDescriptor — the shape produced by the PUBLIC
 * WindowOperator constructors) with an evictor, the per-record List branch
 * mutated the pane list WITHOUT writing it back. Copy-semantics backends
 * therefore dropped every record after the first in each pane; the stock
 * memory backend returns live references, which masked the bug.
 *
 * <p>The test wires the memory backend wrapped so that MapState.get returns a
 * COPY of the stored collection — the same observable semantics RocksDB
 * (and any serialize-on-read backend) exposes — and asserts all three records
 * of a pane reach the window function.
 *
 * <p>Not run against RocksDB directly: on that backend the fallback layout has
 * an additional, independent gap (Object-typed values round-trip through the
 * JSON wrapper layer, so the pane List does not survive to the fire path at
 * all) — see the 2279 F3 adjudication marking this layout production-unreachable
 * via the DSL. The write-back fix itself remains strictly required for every
 * copy-semantics backend.
 */
class TestWindowOperatorMapStateFallbackWriteBack {

    /**
     * Memory backend whose MapState views copy on read — the minimal
     * copy-semantics carrier for the A12 regression.
     */
    static class CopyingMapStateBackend<K> extends MemoryKeyedStateBackend<K> {
        private static final long serialVersionUID = 1L;

        CopyingMapStateBackend(Class<K> keyType) {
            super(keyType);
        }

        @Override
        public <UK, UV> MapState<UK, UV> getMapState(MapStateDescriptor<UK, UV> stateProperties) {
            return new CopyingMapState<>(super.getMapState(stateProperties));
        }
    }

    /** Delegating MapState that hands out defensive copies from get(). */
    static class CopyingMapState<UK, UV> implements MapState<UK, UV> {
        private final MapState<UK, UV> inner;

        CopyingMapState(MapState<UK, UV> inner) {
            this.inner = inner;
        }

        @SuppressWarnings("unchecked")
        private UV copy(UV value) {
            if (value instanceof List) {
                return (UV) new ArrayList<>((List<?>) value);
            }
            if (value instanceof Map) {
                return (UV) new LinkedHashMap<>((Map<?, ?>) value);
            }
            return value;
        }

        @Override
        public UV get(UK key) {
            return copy(inner.get(key));
        }

        @Override
        public void put(UK key, UV value) {
            inner.put(key, value);
        }

        @Override
        public void putAll(Map<UK, UV> map) {
            inner.putAll(map);
        }

        @Override
        public void remove(UK key) {
            inner.remove(key);
        }

        @Override
        public boolean contains(UK key) {
            return inner.contains(key);
        }

        @Override
        public boolean isEmpty() {
            return inner.isEmpty();
        }

        @Override
        public Iterable<Map.Entry<UK, UV>> entries() {
            return inner.entries();
        }

        @Override
        public Iterable<UK> keys() {
            return inner.keys();
        }

        @Override
        public Iterable<UV> values() {
            return inner.values();
        }

        @Override
        public java.util.Iterator<Map.Entry<UK, UV>> iterator() {
            return inner.iterator();
        }

        @Override
        public void clear() {
            inner.clear();
        }
    }

    @Test
    void everyRecordSurvivesWithCopySemanticPaneState() throws Exception {
        WindowOperator<String, Integer, Object, String, TimeWindow> op =
                new WindowOperator<String, Integer, Object, String, TimeWindow>(
                        TumblingEventTimeWindows.of(200L),
                        new WindowingTestSupport.SimpleTimeWindowSerializer(),
                        (KeySelector<Integer, String>) v -> "key1",
                        new WindowingTestSupport.SimpleStringSerializer(),
                        String.class,
                        (io.nop.stream.runtime.operators.windowing.functions.InternalWindowFunction) windowFunction(),
                        EventTimeTrigger.create(),
                        0L,
                        null,
                        (Class<Object>) (Class<?>) Object.class,
                        null,
                        null,
                        (io.nop.stream.core.windowing.evictors.Evictor) CountEvictor.of(100),
                        AccumulationMode.ACCUMULATING);

        // Memory backend + copy-on-read MapState: isolates exactly the A12
        // defect (missing write-back under copy semantics).
        op.setStateBackend(new MemoryStateBackend() {
            private static final long serialVersionUID = 1L;

            @Override
            public <K> IKeyedStateBackend<K> createKeyedStateBackend(Class<K> keyType) {
                return new CopyingMapStateBackend<>(keyType);
            }
        });

        TestOutput<String> output = new TestOutput<>();
        op.setOutput((Output) output);
        op.open();
        try {
            // Window [0,200): three records — pre-fix only the FIRST survived
            // (the null branch stored the list, the List branch mutated the copy
            // handed out by get() and threw it away).
            op.processElement(new StreamRecord<>(1, 10));
            op.processElement(new StreamRecord<>(2, 20));
            op.processElement(new StreamRecord<>(3, 30));
            advanceWatermark(op, 199L);
        } finally {
            op.close();
        }

        assertEquals(1, output.size(), "window fires exactly once");
        assertEquals("1,2,3", output.getElements().get(0),
                "all three records must reach the window function (pre-fix: only '1' — "
                        + "records 2 and 3 were added to a detached copy and lost)");
    }

    @SuppressWarnings("unchecked")
    private static void advanceWatermark(WindowOperator<String, Integer, Object, String, TimeWindow> op,
                                         long timestamp) throws Exception {
        if (op.internalTimerService instanceof io.nop.stream.core.operators.HeapInternalTimerService) {
            ((io.nop.stream.core.operators.HeapInternalTimerService<String, TimeWindow>)
                    op.internalTimerService).advanceWatermark(timestamp);
        }
    }

    private static io.nop.stream.runtime.operators.windowing.functions.InternalIterableProcessWindowFunction<Integer, String, String, TimeWindow> windowFunction() {
        return new io.nop.stream.runtime.operators.windowing.functions.InternalIterableProcessWindowFunction<>(
                new ProcessWindowFunction<Integer, String, String, TimeWindow>() {
                    @Override
                    public void process(String key, TimeWindow window, Iterable<Integer> input,
                                        Context context, Collector<String> out) {
                        StringBuilder sb = new StringBuilder();
                        boolean first = true;
                        for (Integer v : input) {
                            if (!first) {
                                sb.append(",");
                            }
                            sb.append(v);
                            first = false;
                        }
                        out.collect(sb.toString());
                    }
                });
    }
}
