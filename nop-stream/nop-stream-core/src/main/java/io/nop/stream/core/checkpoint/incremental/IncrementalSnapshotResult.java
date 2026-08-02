/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint.incremental;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.stream.core.checkpoint.incremental.SharedStateHandle;

/**
 * Result of an incremental keyed-state snapshot. A task-side state backend (e.g.
 * the RocksDB incremental strategy) produces this: the shared state files are
 * content-addressed (SHA-256) into {@link SharedStateHandle}s, and the
 * non-shareable companion files (e.g. WAL / MANIFEST / OPTIONS / CURRENT /
 * IDENTITY) are referenced by path so the DB can be fully restored.
 *
 * <p>The result carries <em>references</em>, not inlined state data. The
 * coordinator resolves the shared handles against {@link SharedStateRegistry}
 * for cross-checkpoint de-duplication (Stage 31 Phase 4). It is serializable so
 * it can travel from task to coordinator through the ACK channel inside a
 * {@code StateSnapshot} data map.
 *
 * <p>This class lives in {@code nop-stream-core} (not the rocksdb module) so the
 * coordinator can extract it without taking a rocksdb compile dependency; the
 * RocksDB incremental strategy is just one producer.
 */
@DataBean
public final class IncrementalSnapshotResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Marker key under which this result is embedded in a StateSnapshot's data map. */
    public static final String MARKER_KEY = "__incremental_checkpoint__";

    private final long checkpointId;
    private final List<SharedStateHandle> sstHandles;
    private final String nonSstDir;
    private final List<String> nonSstFileNames;
    private final long totalSize;

    public IncrementalSnapshotResult(long checkpointId,
                                     List<SharedStateHandle> sstHandles,
                                     String nonSstDir,
                                     List<String> nonSstFileNames,
                                     long totalSize) {
        this.checkpointId = checkpointId;
        this.sstHandles = sstHandles != null
                ? Collections.unmodifiableList(new ArrayList<>(sstHandles))
                : Collections.emptyList();
        this.nonSstDir = nonSstDir;
        this.nonSstFileNames = nonSstFileNames != null
                ? Collections.unmodifiableList(new ArrayList<>(nonSstFileNames))
                : Collections.emptyList();
        this.totalSize = totalSize;
    }

    public long getCheckpointId() {
        return checkpointId;
    }

    public List<SharedStateHandle> getSstHandles() {
        return sstHandles;
    }

    /** Directory holding the per-checkpoint copies of WAL / MANIFEST / OPTIONS / etc. */
    public String getNonSstDir() {
        return nonSstDir;
    }

    public List<String> getNonSstFileNames() {
        return nonSstFileNames;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public int getSstFileCount() {
        return sstHandles.size();
    }

    @Override
    public String toString() {
        return "IncrementalSnapshotResult{checkpointId=" + checkpointId
                + ", sstCount=" + sstHandles.size()
                + ", totalSize=" + totalSize + "}";
    }
}
