/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.CheckpointBarrier;
import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.common.state.backend.IStateBackend;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.operators.Output;
import io.nop.stream.core.operators.ProcessOperator;
import io.nop.stream.core.streamrecord.LatencyMarker;
import io.nop.stream.core.streamrecord.StreamRecord;
import io.nop.stream.core.streamrecord.watermark.Watermark;
import io.nop.stream.core.streamrecord.watermark.WatermarkStatus;
import io.nop.stream.core.util.OutputTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * WI14 process-path evidence: the dimension-table lookup join on a keyed
 * {@code ProcessOperator} — load → cache → checkpoint snapshot → restore → the
 * DISCRIMINATING assertions. After restore the table stub moves to a new generation
 * (v1→v2): a cached key must still yield the v1 value (proving the cache came from
 * the restored keyed state, not a reload) while a fresh key yields v2 with the load
 * counter incremented (proving the miss-reload path is alive).
 */
public class TestE2EDimLookupWithCheckpoint {

    @SuppressWarnings("unchecked")
    private static Object lastDimValue(CollectingOutput out) {
        Map<String, Object> last = (Map<String, Object>) out.records.get(out.records.size() - 1).getValue();
        return last.get("dimValue");
    }

    /** minimal record-collecting output (core's TestOutput lives in its test jar). */
    static final class CollectingOutput implements Output<StreamRecord<Map<String, Object>>> {
        final List<StreamRecord<Map<String, Object>>> records = new ArrayList<>();

        @Override
        public void collect(StreamRecord<Map<String, Object>> record) {
            records.add(record);
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

    @Test
    void dimCacheSurvivesCheckpointRestoreAndMissStillReloads() throws Exception {
        IStateBackend stateBackend = new MemoryStateBackend();

        Map<String, String> rows = new HashMap<>();
        rows.put("k1", "merchant-a");
        rows.put("k2", "merchant-b");
        AtomicInteger loadCount = new AtomicInteger();

        // --- epoch 1: load k1 into the keyed cache, then snapshot ---
        DimLookupEnrichFunction fn1 = new DimLookupEnrichFunction();
        fn1.setTableLookup(new DimLookupEnrichFunction.VersionedTableLookup(rows, "v1", loadCount));
        ProcessOperator<Map<String, Object>, Map<String, Object>> op1 = new ProcessOperator<>(fn1);
        op1.setStateBackend(stateBackend);
        CollectingOutput out1 = new CollectingOutput();
        op1.setOutput(out1);
        op1.open();

        op1.setCurrentKey("k1");
        op1.processElement(DimLookupEnrichFunction.record("k1"));
        assertEquals("merchant-a@v1", lastDimValue(out1), "first lookup loads from the table");
        assertEquals(1, loadCount.get(), "one table load so far");

        // same key again → cache hit, no new load
        op1.processElement(DimLookupEnrichFunction.record("k1"));
        assertEquals(1, loadCount.get(), "cache hit must not reload");

        CheckpointBarrier barrier = new CheckpointBarrier(1L, System.currentTimeMillis(), CheckpointType.CHECKPOINT);
        op1.processBarrier(barrier);
        OperatorSnapshotResult snapshot = op1.getLastSnapshotResult();
        assertNotNull(snapshot);
        assertFalse(snapshot.isEmpty(), "keyed dim cache must participate in the checkpoint snapshot");

        // --- epoch 2: restore into a fresh operator, then DISCRIMINATE ---
        DimLookupEnrichFunction fn2 = new DimLookupEnrichFunction();
        fn2.setTableLookup(new DimLookupEnrichFunction.VersionedTableLookup(rows, "v2", loadCount));
        ProcessOperator<Map<String, Object>, Map<String, Object>> op2 = new ProcessOperator<>(fn2);
        op2.setStateBackend(stateBackend);
        CollectingOutput out2 = new CollectingOutput();
        op2.setOutput(out2);
        op2.open();
        op2.restoreState(snapshot);

        // cached key: still v1 — proof the value came from the RESTORED keyed state,
        // not from a post-restore reload (a reload would produce v2)
        op2.setCurrentKey("k1");
        op2.processElement(DimLookupEnrichFunction.record("k1"));
        assertEquals("merchant-a@v1", lastDimValue(out2),
                "restored cache must be used (v1), not a fresh v2 load");
        assertEquals(1, loadCount.get(), "restored hit must not reload");

        // fresh key: miss → reload path alive, now serving v2
        op2.setCurrentKey("k2");
        op2.processElement(DimLookupEnrichFunction.record("k2"));
        assertEquals("merchant-b@v2", lastDimValue(out2), "miss must reload from the table");
        assertEquals(2, loadCount.get(), "fresh key reloads exactly once");
    }
}
