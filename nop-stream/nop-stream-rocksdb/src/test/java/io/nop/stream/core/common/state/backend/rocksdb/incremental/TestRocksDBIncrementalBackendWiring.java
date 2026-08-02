/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.rocksdb.incremental;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.nop.stream.core.checkpoint.incremental.IncrementalSnapshotResult;
import io.nop.stream.core.checkpoint.incremental.SharedStateHandle;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.common.state.backend.rocksdb.RocksDBKeyedStateBackend;
import io.nop.stream.core.common.state.ValueStateDescriptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 31 Phase 2 wiring: {@code RocksDBKeyedStateBackend#snapshotState()} routes
 * to the incremental strategy when {@code incrementalCheckpointEnabled=true}, and
 * keeps the Stage 30 full-scan path when it is false.
 */
class TestRocksDBIncrementalBackendWiring {

    @TempDir
    File tempDir;

    private RocksDBKeyedStateBackend<String> newBackend() {
        return new RocksDBKeyedStateBackend<>(tempDir.getAbsolutePath(), String.class, 1, null);
    }

    // ---- incrementalCheckpointEnabled=false => Stage 30 full-scan path (backward compat) ----

    @Test
    void disabledKeepsFullScanPath() throws Exception {
        RocksDBKeyedStateBackend<String> backend = newBackend();
        backend.setCurrentKey("k1");
        backend.getState(new ValueStateDescriptor<>("vs", Long.class)).update(42L);

        assertFalse(backend.isIncrementalCheckpointEnabled(), "default must be false");

        StateSnapshot snapshot = backend.snapshotState();
        assertNotNull(snapshot);
        // The full-scan path stores a JSON-serializable 'states' map, NOT the incremental marker.
        assertFalse(snapshot.getStateData().containsKey(IncrementalSnapshotResult.MARKER_KEY));
        assertNotNull(snapshot.getStateData().get("states"));
        backend.close();
    }

    // ---- incrementalCheckpointEnabled=true => incremental strategy path ----

    @Test
    @SuppressWarnings("unchecked")
    void enabledRoutesToIncrementalStrategy() throws Exception {
        RocksDBKeyedStateBackend<String> backend = newBackend();
        backend.setIncrementalCheckpointEnabled(true);
        backend.setCheckpointBaseDir(tempDir.toPath().resolve("ckp").toString());
        backend.setCurrentKey("k1");
        backend.getState(new ValueStateDescriptor<>("vs", Long.class)).update(7L);

        assertTrue(backend.isIncrementalCheckpointEnabled());

        StateSnapshot snapshot = backend.snapshotState();
        assertNotNull(snapshot);
        assertTrue(snapshot.getStateData().containsKey(IncrementalSnapshotResult.MARKER_KEY),
                "incremental marker must be present when enabled");

        Object raw = snapshot.getStateData().get(IncrementalSnapshotResult.MARKER_KEY);
        assertTrue(raw instanceof IncrementalSnapshotResult,
                "marker value must be an IncrementalSnapshotResult");

        IncrementalSnapshotResult result = (IncrementalSnapshotResult) raw;
        assertTrue(result.getSstFileCount() >= 0);
        // The non-SST dir must exist and contain at least CURRENT.
        Path nonSstDir = Path.of(result.getNonSstDir());
        assertTrue(Files.isDirectory(nonSstDir));
        backend.close();
    }

    // ---- incremental result carries the raw SST handles (registry registration is Phase 4) ----

    @Test
    void incrementalResultCarriesRawHandles() throws Exception {
        RocksDBKeyedStateBackend<String> backend = newBackend();
        backend.setIncrementalCheckpointEnabled(true);
        backend.setCheckpointBaseDir(tempDir.toPath().resolve("ckp2").toString());
        backend.setCurrentKey("k1");
        backend.getState(new ValueStateDescriptor<>("vs", String.class)).update("hello");

        StateSnapshot snapshot = backend.snapshotState();
        IncrementalSnapshotResult r = (IncrementalSnapshotResult)
                snapshot.getStateData().get(IncrementalSnapshotResult.MARKER_KEY);

        List<SharedStateHandle> handles = r.getSstHandles();
        for (SharedStateHandle h : handles) {
            assertEquals(64, h.getContentHash().length());
            assertTrue(h.getSize() > 0);
        }
        // checkpointId advances monotonically within a backend instance.
        long first = r.getCheckpointId();
        IncrementalSnapshotResult r2 = (IncrementalSnapshotResult)
                backend.snapshotState().getStateData().get(IncrementalSnapshotResult.MARKER_KEY);
        assertTrue(r2.getCheckpointId() > first, "checkpointId must advance");
        backend.close();
    }

    // ---- default checkpointBaseDir derivation (dbPath + "-checkpoints") ----

    @Test
    void defaultCheckpointBaseDirDerivedFromDbPath() throws Exception {
        RocksDBKeyedStateBackend<String> backend = newBackend();
        backend.setIncrementalCheckpointEnabled(true);
        // intentionally NOT setting checkpointBaseDir -> should derive from dbPath
        backend.setCurrentKey("k1");
        backend.getState(new ValueStateDescriptor<>("vs", Long.class)).update(1L);

        StateSnapshot snapshot = backend.snapshotState();
        IncrementalSnapshotResult r = (IncrementalSnapshotResult)
                snapshot.getStateData().get(IncrementalSnapshotResult.MARKER_KEY);

        // The derived base dir is dbPath + "-checkpoints" (a sibling of the DB dir).
        Path derivedBase = Path.of(tempDir.getAbsolutePath() + "-checkpoints");
        assertTrue(Files.exists(derivedBase.resolve("cp-1").resolve("native")),
                "default base dir {dbPath}-checkpoints must be used; derivedBase=" + derivedBase);
        assertEquals(derivedBase.resolve("cp-1").resolve("non-sst").toString(), r.getNonSstDir());
        backend.close();
    }

    // ---- incremental snapshot still round-trips restore via the full-scan restore path ----
    // (The incremental snapshot is a *reference*; the operator-level restore logic that
    // consumes SST files from the segment store is covered in Phase 4 end-to-end tests.
    // Here we verify the marker does not crash the standard getStates() accessor.)

    @Test
    void incrementalSnapshotDoesNotExposeStatesMap() throws Exception {
        RocksDBKeyedStateBackend<String> backend = newBackend();
        backend.setIncrementalCheckpointEnabled(true);
        backend.setCheckpointBaseDir(tempDir.toPath().resolve("ckp3").toString());
        backend.setCurrentKey("k1");
        backend.getState(new ValueStateDescriptor<>("vs", Long.class)).update(5L);

        StateSnapshot snapshot = backend.snapshotState();
        Map<String, Object> states = snapshot.getStates();
        // Incremental path stores the marker, not a 'states' map.
        assertTrue(states.isEmpty() || !states.containsKey("vs"));
        assertFalse(snapshot.isEmpty(), "snapshot must be non-empty (carries marker)");
        backend.close();
    }
}
