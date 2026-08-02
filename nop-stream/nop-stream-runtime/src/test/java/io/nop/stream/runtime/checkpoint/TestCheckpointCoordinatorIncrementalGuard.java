/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.checkpoint.CheckpointIDCounter;
import io.nop.stream.core.checkpoint.storage.ISegmentStore;
import io.nop.stream.core.checkpoint.storage.LocalFileSegmentStore;
import io.nop.stream.runtime.checkpoint.storage.LocalFileCheckpointStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 31 Phase 3: the coordinator's incremental-checkpoint config guard fails fast
 * (No-Silent-No-Op rule) instead of silently degrading.
 */
class TestCheckpointCoordinatorIncrementalGuard {

    @TempDir
    Path tmp;

    private CheckpointCoordinator newCoordinator(boolean async) {
        CheckpointConfig config = new CheckpointConfig();
        config.setCheckpointEnabled(true);
        config.setAsyncSnapshotEnabled(async);
        return new CheckpointCoordinator(
                "job-inc", "pipe-inc", new CheckpointIDCounter(),
                new LocalFileCheckpointStorage(tmp.resolve("cp").toString()), config);
    }

    @BeforeEach
    void disableSchedulerSideEffect() {
        // The guard is invoked by startCheckpointScheduler(); tests below call
        // validateIncrementalConfig() directly to avoid spawning scheduler threads.
    }

    @Test
    void incrementalEnabledWithoutSegmentStoreThrowsUnsupported() {
        CheckpointCoordinator cc = newCoordinator(true);
        cc.setIncrementalCheckpointEnabled(true);
        // segmentStore NOT set -> must throw UnsupportedOperationException, not silent fallback
        assertThrows(UnsupportedOperationException.class, cc::validateIncrementalConfig);
    }

    @Test
    void incrementalEnabledWithSegmentStoreIsValid() {
        CheckpointCoordinator cc = newCoordinator(true);
        cc.setIncrementalCheckpointEnabled(true);
        cc.setSegmentStore(new LocalFileSegmentStore(tmp.resolve("ss")));
        assertDoesNotThrow(cc::validateIncrementalConfig);
    }

    @Test
    void incrementalEnabledWithoutAsyncThrowsIllegalState() {
        CheckpointCoordinator cc = newCoordinator(false);
        cc.setIncrementalCheckpointEnabled(true);
        cc.setSegmentStore(new LocalFileSegmentStore(tmp.resolve("ss")));
        // sync/incremental mutex: incremental requires async snapshot
        assertThrows(IllegalStateException.class, cc::validateIncrementalConfig);
    }

    @Test
    void incrementalDisabledIsValidRegardlessOfSegmentStore() {
        CheckpointCoordinator cc = newCoordinator(false);
        cc.setIncrementalCheckpointEnabled(false);
        // segmentStore null is fine when incremental is off (non-incremental path)
        assertDoesNotThrow(cc::validateIncrementalConfig);
        assertFalse(cc.isIncrementalCheckpointEnabled());
    }

    @Test
    void settersAndGetters() {
        CheckpointCoordinator cc = newCoordinator(true);
        assertFalse(cc.isIncrementalCheckpointEnabled());
        assertTrue(cc.getSegmentStore() == null);

        cc.setIncrementalCheckpointEnabled(true);
        ISegmentStore store = new LocalFileSegmentStore(tmp.resolve("ss"));
        cc.setSegmentStore(store);
        assertTrue(cc.isIncrementalCheckpointEnabled());
        assertTrue(cc.getSegmentStore() == store);
    }
}
