/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.integration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.nop.stream.core.common.functions.ProcessFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.exceptions.StreamRuntimeException;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.IKeyedStateBackend;
import io.nop.stream.core.common.state.backend.IStateBackend;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.common.state.backend.rocksdb.RocksDBStateBackend;
import io.nop.stream.core.common.state.shard.KeyGroup;
import io.nop.stream.core.operators.ChainingOutput;
import io.nop.stream.core.operators.ProcessOperator;
import io.nop.stream.core.operators.StreamSinkOperator;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.core.jobgraph.OperatorChain;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.util.Collector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 34 (Phase 3) end-to-end: a keyed aggregation pipeline (source &#8594;
 * keyed sum operator &#8594; sink) exercising the new key&#8594;group routing
 * at runtime on both the Memory and RocksDB backends with the Stage-34 default
 * {@code maxParallelism}. Asserts that keyed aggregation results are correct
 * and identical across both backends (behaviour does not regress).
 *
 * <p>Anti-hollow: the {@link ProcessOperator} here holds the keyed state backend
 * and the process function reads/writes {@link ValueState} through the live
 * {@code keyGroupId} routing path, so the key-group model is exercised end to
 * end (source element &#8594; setCurrentKey &#8594; key-group routing &#8594;
 * stored value &#8594; sink output), not just at the unit level.
 */
public class TestKeyGroupRoutingE2E {

    @TempDir
    Path tempDir;

    /** A (key, value) record carried through the pipeline. */
    public static final class KeyValue {
        public final String key;
        public final long value;

        public KeyValue(String key, long value) {
            this.key = key;
            this.value = value;
        }
    }

    /**
     * Run source &#8594; keyed-sum &#8594; sink on the given backend and return
     * the final per-key accumulated sums (the last emitted sum per key).
     */
    private Map<String, Long> runKeyedSumPipeline(IStateBackend backend) throws Exception {
        IKeyedStateBackend<String> keyed = backend.createKeyedStateBackend(String.class);

        List<String> emitted = Collections.synchronizedList(new ArrayList<>());

        // Source: emit a fixed, key-interleaved sequence so that keys are
        // visited multiple times and in non-sorted order (exercises group
        // switching on both backends).
        List<KeyValue> records = Arrays.asList(
                new KeyValue("a", 1),
                new KeyValue("b", 10),
                new KeyValue("a", 2),
                new KeyValue("c", 100),
                new KeyValue("b", 20),
                new KeyValue("a", 3),
                new KeyValue("c", 200),
                new KeyValue("d", 1000),
                new KeyValue("b", 30),
                new KeyValue("c", 300));

        SourceFunction<KeyValue> source = new SourceFunction<>() {
            private static final long serialVersionUID = 1L;
            @Override
            public void run(SourceContext<KeyValue> ctx) throws Exception {
                for (KeyValue rec : records) {
                    ctx.collect(rec);
                }
            }
            @Override
            public void cancel() {
            }
        };

        // Keyed sum operator: accumulates per-key running sum in ValueState,
        // emits "key=sum" on every element. setCurrentKey is driven by a
        // KeyExtractingOutput-style adapter via the source operator.
        ProcessFunction<KeyValue, String> sumFn = new ProcessFunction<KeyValue, String>() {
            private ValueState<Long> sumState;

            @Override
            public void processElement(KeyValue rec, Context ctx, Collector<String> out) throws Exception {
                if (sumState == null) {
                    sumState = getRuntimeContext().getKeyedStateStore()
                            .getState(new ValueStateDescriptor<>("sum", Long.class, 0L));
                }
                long next = sumState.value() + rec.value;
                sumState.update(next);
                out.collect(rec.key + "=" + next);
            }
        };

        StreamSourceOperator<KeyValue> srcOp = new StreamSourceOperator<>(source);
        ProcessOperator<KeyValue, String> sumOp = new ProcessOperator<>(sumFn);
        StreamSinkOperator<String> sinkOp = new StreamSinkOperator<>(emitted::add);

        // Wire source -> sum -> sink, but route each source element through
        // setCurrentKey on the keyed backend before processing (the operator
        // chain does not do keyBy automatically in this minimal harness).
        srcOp.setOutput(new OutputAdapter<>(sumOp, keyed));
        sumOp.setOutput(new ChainingOutput<>(sinkOp));
        new OperatorChain(Arrays.asList(srcOp, sumOp, sinkOp));

        sumOp.setKeyedStateBackend(keyed);
        srcOp.open();
        sumOp.open();
        sinkOp.open();
        srcOp.run();

        // Extract final per-key sum from the last emission for each key.
        Map<String, Long> finalSums = new LinkedHashMap<>();
        for (String e : emitted) {
            int eq = e.indexOf('=');
            finalSums.put(e.substring(0, eq), Long.parseLong(e.substring(eq + 1)));
        }

        keyed.close();
        return finalSums;
    }

    /**
     * Adapter that, for each source record, sets the current key on the keyed
     * backend then forwards the record to the downstream operator's
     * processElement. This stands in for the runtime keyBy() partitioning in
     * the minimal single-subtask harness.
     */
    private static final class OutputAdapter<T> implements io.nop.stream.core.operators.Output<StreamRecord<T>> {
        private final ProcessOperator<T, ?> downstream;
        private final IKeyedStateBackend<String> keyed;

        OutputAdapter(ProcessOperator<T, ?> downstream, IKeyedStateBackend<String> keyed) {
            this.downstream = downstream;
            this.keyed = keyed;
        }

        @Override
        public void collect(StreamRecord<T> record) {
            T value = record.getValue();
            if (value instanceof KeyValue) {
                keyed.setCurrentKey(((KeyValue) value).key);
            }
            try {
                downstream.processElement(record);
            } catch (Exception e) {
                throw new StreamRuntimeException("Failed to process element in KeyGroupRoutingE2E harness", e);
            }
        }

        @Override
        public void emitWatermark(io.nop.stream.core.streamrecord.watermark.Watermark mark) {
            try {
                downstream.processWatermark(mark);
            } catch (Exception e) {
                throw new StreamRuntimeException("Failed to process watermark in KeyGroupRoutingE2E harness", e);
            }
        }

        @Override
        public void emitWatermarkStatus(io.nop.stream.core.streamrecord.watermark.WatermarkStatus status) {
            // no-op for this minimal harness
        }

        @Override
        public <X> void collect(io.nop.stream.core.util.OutputTag<X> outputTag, StreamRecord<X> record) {
            throw new UnsupportedOperationException("side output not used in KeyGroupRoutingE2E harness");
        }

        @Override
        public void emitLatencyMarker(io.nop.stream.core.streamrecord.LatencyMarker latencyMarker) {
            // no-op for this minimal harness
        }

        @Override
        public void emitBarrier(io.nop.stream.core.checkpoint.CheckpointBarrier barrier) {
            // no-op for this minimal harness (no checkpointing in E2E)
        }

        @Override
        public void close() {
        }
    }

    @Test
    void keyedSumPipelineProducesCorrectResultsOnMemoryBackend() throws Exception {
        IStateBackend backend = new MemoryStateBackend(KeyGroup.DEFAULT_MAX_PARALLELISM);
        Map<String, Long> sums = runKeyedSumPipeline(backend);
        // a: 1+2+3 = 6, b: 10+20+30 = 60, c: 100+200+300 = 600, d: 1000
        assertEquals(6L, sums.get("a"));
        assertEquals(60L, sums.get("b"));
        assertEquals(600L, sums.get("c"));
        assertEquals(1000L, sums.get("d"));
    }

    @Test
    void keyedSumPipelineProducesCorrectResultsOnRocksDBBackend() throws Exception {
        java.io.File dir = tempDir.resolve("rocks-keygroup-e2e").toFile();
        dir.mkdirs();
        IStateBackend backend = new RocksDBStateBackend(dir.getAbsolutePath(), KeyGroup.DEFAULT_MAX_PARALLELISM);
        Map<String, Long> sums = runKeyedSumPipeline(backend);
        assertEquals(6L, sums.get("a"));
        assertEquals(60L, sums.get("b"));
        assertEquals(600L, sums.get("c"));
        assertEquals(1000L, sums.get("d"));
    }

    @Test
    void memoryAndRocksDBBackendsProduceIdenticalKeyedResults() throws Exception {
        Map<String, Long> mem = runKeyedSumPipeline(new MemoryStateBackend(KeyGroup.DEFAULT_MAX_PARALLELISM));
        java.io.File dir = tempDir.resolve("rocks-keygroup-parity").toFile();
        dir.mkdirs();
        Map<String, Long> rocks = runKeyedSumPipeline(
                new RocksDBStateBackend(dir.getAbsolutePath(), KeyGroup.DEFAULT_MAX_PARALLELISM));
        assertEquals(mem, rocks, "keyed aggregation must be identical across backends");
        assertTrue(mem.size() == 4 && mem.containsKey("a") && mem.containsKey("d"));
    }
}
