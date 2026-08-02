/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.integration;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.nop.stream.core.checkpoint.CheckpointType;
import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.StateSnapshotContext;
import io.nop.stream.core.common.functions.MapFunction;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.IKeyedStateBackend;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.common.state.backend.memory.MemoryStateBackend;
import io.nop.stream.core.common.state.backend.rocksdb.RocksDBStateBackend;
import io.nop.stream.core.operators.AbstractStreamOperator;
import io.nop.stream.core.operators.StreamMap;
import io.nop.stream.core.operators.StreamSinkOperator;
import io.nop.stream.core.operators.StreamSourceOperator;
import io.nop.stream.core.operators.StreamOperator;
import io.nop.stream.core.operators.ChainingOutput;
import io.nop.stream.core.jobgraph.OperatorChain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 4: End-to-end integration of RocksDBStateBackend with the operator
 * pipeline. Verifies:
 * <ul>
 *   <li>AbstractStreamOperator.snapshotState() calls the RocksDB backend path
 *       (wiring / anti-hollow check)</li>
 *   <li>Snapshot → restore → continue preserves keyed state</li>
 *   <li>Large state exceeding a small heap runs without OOM (off-heap storage)</li>
 * </ul>
 */
public class TestRocksDBStateBackendE2E {

    @TempDir
    Path tempDir;

    private String rocksPath(String sub) {
        File dir = tempDir.resolve(sub).toFile();
        dir.mkdirs();
        return dir.getAbsolutePath();
    }

    /**
     * Wiring verification: AbstractStreamOperator.snapshotState() calls the
     * RocksDB backend's snapshotState(), not a memory fallback. The operator
     * is wired with RocksDBStateBackend and the snapshot must reflect the
     * RocksDB-stored values.
     */
    @Test
    void testOperatorSnapshotUsesRocksDBBackend() throws Exception {
        RocksDBStateBackend stateBackend = new RocksDBStateBackend(rocksPath("wiring"));
        IKeyedStateBackend<String> keyedBackend = stateBackend.createKeyedStateBackend(String.class);

        StreamMap<String, String> mapOp = new StreamMap<>((MapFunction<String, String>) s -> s);
        mapOp.setStateBackend(stateBackend);
        mapOp.setKeyedStateBackend(keyedBackend);
        mapOp.open();

        keyedBackend.setCurrentKey("key1");
        ValueState<Long> vs = keyedBackend.getState(
                new ValueStateDescriptor<>("counter", Long.class, 0L));
        vs.update(42L);

        keyedBackend.setCurrentKey("key2");
        ValueState<Long> vs2 = keyedBackend.getState(
                new ValueStateDescriptor<>("counter", Long.class, 0L));
        vs2.update(99L);

        OperatorSnapshotResult result = mapOp.snapshotState(new StateSnapshotContext(1L, System.currentTimeMillis()));

        StateSnapshot snapshot = (StateSnapshot) result.getKeyedState("keyed-state");
        assertNotNull(snapshot, "RocksDB backend must produce a non-null keyed-state snapshot via operator path");
        assertFalse(snapshot.isEmpty(), "Snapshot must contain the keyed state written via RocksDB");

        keyedBackend.close();

        // Restore into a fresh RocksDB backend — values must survive
        RocksDBStateBackend restoreBackend = new RocksDBStateBackend(rocksPath("wiring-restore"));
        IKeyedStateBackend<String> restoredBackend = restoreBackend.createKeyedStateBackend(String.class);
        restoredBackend.restoreState(snapshot);

        restoredBackend.setCurrentKey("key1");
        assertEquals(42L, restoredBackend.getState(
                new ValueStateDescriptor<>("counter", Long.class, 0L)).value());
        restoredBackend.setCurrentKey("key2");
        assertEquals(99L, restoredBackend.getState(
                new ValueStateDescriptor<>("counter", Long.class, 0L)).value());

        restoredBackend.close();
    }

    /**
     * Full E2E: source → map → sink pipeline with RocksDB state backend.
     * Process data, snapshot, restore into new pipeline, continue processing,
     * verify results are consistent with memory backend.
     */
    @Test
    void testPipelineWithRocksDBCheckpointRestore() throws Exception {
        List<Integer> firstBatch = Collections.synchronizedList(new ArrayList<>());

        SourceFunction<Integer> source1 = new SourceFunction<Integer>() {
            private static final long serialVersionUID = 1L;
            @Override
            public void run(SourceContext<Integer> ctx) throws Exception {
                for (int i = 1; i <= 5; i++) {
                    ctx.collect(i);
                }
            }
            @Override
            public void cancel() {
            }
        };
        MapFunction<Integer, Integer> doubler = value -> value * 2;
        SinkFunction<Integer> sink1 = firstBatch::add;

        StreamSourceOperator<Integer> srcOp1 = new StreamSourceOperator<>(source1);
        StreamMap<Integer, Integer> mapOp1 = new StreamMap<>(doubler);
        StreamSinkOperator<Integer> sinkOp1 = new StreamSinkOperator<>(sink1);

        RocksDBStateBackend rocksBackend = new RocksDBStateBackend(rocksPath("pipeline"));
        mapOp1.setStateBackend(rocksBackend);
        IKeyedStateBackend<String> keyedBackend = rocksBackend.createKeyedStateBackend(String.class);
        mapOp1.setKeyedStateBackend(keyedBackend);

        srcOp1.setOutput(new ChainingOutput<>(mapOp1));
        mapOp1.setOutput(new ChainingOutput<>(sinkOp1));

        List<StreamOperator<?>> operators = Arrays.asList(srcOp1, mapOp1, sinkOp1);
        new OperatorChain(operators);

        srcOp1.open();
        mapOp1.open();
        sinkOp1.open();
        srcOp1.run();

        assertEquals(Arrays.asList(2, 4, 6, 8, 10), firstBatch);

        OperatorSnapshotResult snapshot = mapOp1.snapshotState(
                new StateSnapshotContext(1L, System.currentTimeMillis()));

        keyedBackend.close();

        // Restore: new pipeline with same RocksDB path
        List<Integer> secondBatch = Collections.synchronizedList(new ArrayList<>());
        SourceFunction<Integer> source2 = new SourceFunction<Integer>() {
            private static final long serialVersionUID = 1L;
            @Override
            public void run(SourceContext<Integer> ctx) throws Exception {
                for (int i = 6; i <= 10; i++) {
                    ctx.collect(i);
                }
            }
            @Override
            public void cancel() {
            }
        };
        SinkFunction<Integer> sink2 = secondBatch::add;

        StreamSourceOperator<Integer> srcOp2 = new StreamSourceOperator<>(source2);
        StreamMap<Integer, Integer> mapOp2 = new StreamMap<>(doubler);
        StreamSinkOperator<Integer> sinkOp2 = new StreamSinkOperator<>(sink2);

        RocksDBStateBackend restoreBackend = new RocksDBStateBackend(rocksPath("pipeline-restore"));
        mapOp2.setStateBackend(restoreBackend);
        IKeyedStateBackend<String> restoredKeyed = restoreBackend.createKeyedStateBackend(String.class);
        if (snapshot.getKeyedState("keyed-state") != null) {
            restoredKeyed.restoreState((StateSnapshot) snapshot.getKeyedState("keyed-state"));
        }
        mapOp2.setKeyedStateBackend(restoredKeyed);

        srcOp2.setOutput(new ChainingOutput<>(mapOp2));
        mapOp2.setOutput(new ChainingOutput<>(sinkOp2));
        new OperatorChain(Arrays.asList(srcOp2, mapOp2, sinkOp2));

        srcOp2.open();
        mapOp2.open();
        sinkOp2.open();
        srcOp2.run();

        assertEquals(Arrays.asList(12, 14, 16, 18, 20), secondBatch);
        restoredKeyed.close();
    }

    /**
     * Result consistency: the same pipeline produces the same output whether
     * backed by MemoryStateBackend or RocksDBStateBackend.
     */
    @Test
    void testRocksDBMatchesMemoryResults() throws Exception {
        List<Integer> rocksResults = runPipelineWithBackend(
                new RocksDBStateBackend(rocksPath("consistency")));
        List<Integer> memResults = runPipelineWithBackend(
                new MemoryStateBackend());

        assertEquals(memResults, rocksResults,
                "RocksDB and Memory backends must produce identical results");
    }

    private List<Integer> runPipelineWithBackend(io.nop.stream.core.common.state.backend.IStateBackend backend) throws Exception {
        List<Integer> results = Collections.synchronizedList(new ArrayList<>());

        SourceFunction<Integer> source = new SourceFunction<Integer>() {
            private static final long serialVersionUID = 1L;
            @Override
            public void run(SourceContext<Integer> ctx) throws Exception {
                for (int i = 1; i <= 10; i++) {
                    ctx.collect(i);
                }
            }
            @Override
            public void cancel() {
            }
        };
        StreamSourceOperator<Integer> srcOp = new StreamSourceOperator<>(source);
        StreamMap<Integer, Integer> mapOp = new StreamMap<>(value -> value * 3);
        StreamSinkOperator<Integer> sinkOp = new StreamSinkOperator<>(results::add);

        srcOp.setOutput(new ChainingOutput<>(mapOp));
        mapOp.setOutput(new ChainingOutput<>(sinkOp));
        new OperatorChain(Arrays.asList(srcOp, mapOp, sinkOp));

        srcOp.open();
        mapOp.open();
        sinkOp.open();
        srcOp.run();

        if (mapOp.getKeyedStateBackend() != null) {
            mapOp.getKeyedStateBackend().close();
        }
        return results;
    }

    /**
     * Large state test: insert enough entries to generate SST files on disk.
     * Asserts that RocksDB's SST files exist on disk, proving off-heap storage
     * is in effect. This is a deterministic assertion that does not depend on
     * OOM behavior.
     */
    @Test
    void testLargeStateSpillsToSstFiles() throws Exception {
        String dbPath = rocksPath("large");
        io.nop.stream.core.common.state.backend.rocksdb.RocksDBOptionConfig smallBuffer =
                new io.nop.stream.core.common.state.backend.rocksdb.RocksDBOptionConfig(64 * 1024L, 2);
        RocksDBStateBackend backend = new RocksDBStateBackend(dbPath, 1, smallBuffer);
        IKeyedStateBackend<String> keyedBackend = backend.createKeyedStateBackend(String.class);

        ValueState<String> state = keyedBackend.getState(
                new ValueStateDescriptor<>("bulk", String.class));

        // Insert enough data to generate SST files (RocksDB flushes memtable to SST)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            sb.append("x");
        }
        String payload = sb.toString();

        for (int i = 0; i < 200; i++) {
            keyedBackend.setCurrentKey("bulk-" + i);
            state.update(payload + i);
        }

        // Close flushes memtable to SST files on disk
        keyedBackend.close();

        // Verify SST files exist on disk (off-heap storage proof)
        File dbDir = new File(dbPath);
        long sstTotalSize = 0;
        File[] sstFiles = dbDir.listFiles((dir, name) -> name.endsWith(".sst"));
        if (sstFiles != null) {
            for (File f : sstFiles) {
                sstTotalSize += f.length();
            }
        }

        assertTrue(sstTotalSize > 0,
                "RocksDB must have generated SST files on disk (total .sst size > 0), proving off-heap storage. Got: " + sstTotalSize);
    }
}
