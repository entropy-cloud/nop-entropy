/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.rocksdb;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import io.nop.stream.core.checkpoint.incremental.IncrementalSnapshotResult;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Regression coverage for the incremental-restore key-layout fail-fast. Unlike
 * {@code TestRocksDBKeyGroupPrefixLayout}, which calls the
 * {@link RocksDBKeyEncoder#verifyKeyLayoutVersion} helper directly, these tests
 * drive the public {@link RocksDBKeyedStateBackend#restoreState} entry point so
 * the wiring (helper invoked before {@code restoreIncremental}) is verified:
 * a legacy/absent {@code keyLayoutVersion} must fail fast with
 * {@code ERR_STREAM_STATE_ERROR} instead of silently scanning SST files under
 * the wrong byte-range assumption.
 */
class TestRocksDBIncrementalRestoreFailFast {

    @TempDir
    java.io.File tempDir;

    private RocksDBKeyedStateBackend<String> newBackend() {
        java.io.File sub = new java.io.File(tempDir, "db-" + System.nanoTime());
        sub.mkdirs();
        // No segmentStore: fail-fast must trigger before restoreIncremental.
        return new RocksDBKeyedStateBackend<>(sub.getAbsolutePath(), String.class, 4, null);
    }

    private static IncrementalSnapshotResult emptyResult() {
        return new IncrementalSnapshotResult(1L, Collections.emptyList(),
                null, Collections.emptyList(), 0L);
    }

    private static StateSnapshot snapshotWithIncrementalMarker(Object marker, Object layoutVersion) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(IncrementalSnapshotResult.MARKER_KEY, marker);
        if (layoutVersion != null) {
            data.put(RocksDBKeyEncoder.KEY_LAYOUT_VERSION_FIELD, layoutVersion);
        }
        return new StateSnapshot(data);
    }

    @Test
    void typedMarkerRejectsLegacyLayoutVersionThroughRestoreState() {
        // Typed-marker incremental branch with legacy v1 layout must fail fast
        // at restoreState (before any SST scan).
        StateSnapshot snapshot = snapshotWithIncrementalMarker(
                emptyResult(), RocksDBKeyEncoder.LEGACY_KEY_LAYOUT_VERSION);

        RocksDBKeyedStateBackend<String> backend = newBackend();
        StreamException ex = assertThrows(StreamException.class, () -> backend.restoreState(snapshot));
        assertEquals(NopStreamErrors.ERR_STREAM_STATE_ERROR.getErrorCode(), ex.getErrorCode());
        backend.close();
    }

    @Test
    void typedMarkerRejectsAbsentLayoutVersionThroughRestoreState() {
        // No keyLayoutVersion at all (legacy Stage 31 SST) must fail fast.
        StateSnapshot snapshot = snapshotWithIncrementalMarker(emptyResult(), null);

        RocksDBKeyedStateBackend<String> backend = newBackend();
        StreamException ex = assertThrows(StreamException.class, () -> backend.restoreState(snapshot));
        assertEquals(NopStreamErrors.ERR_STREAM_STATE_ERROR.getErrorCode(), ex.getErrorCode());
        backend.close();
    }

    @Test
    void mapMarkerRejectsLegacyLayoutVersionThroughRestoreState() {
        // JSON-deserialized (Map) incremental branch: same strict fail-fast.
        // The marker is a Map so BeanTool rebuilds an IncrementalSnapshotResult,
        // but verifyKeyLayoutVersion must still run first.
        Map<String, Object> markerMap = new LinkedHashMap<>();
        markerMap.put("checkpointId", 1L);
        markerMap.put("totalSize", 0L);
        StateSnapshot snapshot = snapshotWithIncrementalMarker(
                markerMap, RocksDBKeyEncoder.LEGACY_KEY_LAYOUT_VERSION);

        RocksDBKeyedStateBackend<String> backend = newBackend();
        StreamException ex = assertThrows(StreamException.class, () -> backend.restoreState(snapshot));
        assertEquals(NopStreamErrors.ERR_STREAM_STATE_ERROR.getErrorCode(), ex.getErrorCode());
        backend.close();
    }
}
