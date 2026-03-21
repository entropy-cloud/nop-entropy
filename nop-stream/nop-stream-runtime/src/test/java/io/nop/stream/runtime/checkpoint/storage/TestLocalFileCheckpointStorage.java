/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.checkpoint.storage;

import io.nop.stream.core.checkpoint.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestLocalFileCheckpointStorage {

    @TempDir
    Path tempDir;

    private LocalFileCheckpointStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalFileCheckpointStorage(tempDir.toString());
    }

    @AfterEach
    void tearDown() throws Exception {
        storage.deleteAllCheckpoints(1L);
    }

    @Test
    void testGetName() {
        assertEquals("LocalFileCheckpointStorage", storage.getName());
    }

    @Test
    void testStoreAndGetCheckpoint() throws Exception {
        CompletedCheckpoint checkpoint = createTestCheckpoint(1L, 1, 100L);

        String path = storage.storeCheckPoint(checkpoint);
        assertNotNull(path);
        assertTrue(path.contains("100"));

        CompletedCheckpoint retrieved = storage.getLatestCheckpoint(1L, 1);
        assertNotNull(retrieved);
        assertEquals(100L, retrieved.getCheckpointId());
        assertEquals(1L, retrieved.getJobId());
    }

    @Test
    void testGetLatestCheckpoint() throws Exception {
        storage.storeCheckPoint(createTestCheckpoint(1L, 1, 100L));
        storage.storeCheckPoint(createTestCheckpoint(1L, 1, 200L));
        storage.storeCheckPoint(createTestCheckpoint(1L, 1, 300L));

        CompletedCheckpoint latest = storage.getLatestCheckpoint(1L, 1);
        assertNotNull(latest);
        assertEquals(300L, latest.getCheckpointId());
    }

    @Test
    void testGetAllCheckpoints() throws Exception {
        storage.storeCheckPoint(createTestCheckpoint(1L, 1, 100L));
        storage.storeCheckPoint(createTestCheckpoint(1L, 1, 200L));
        storage.storeCheckPoint(createTestCheckpoint(1L, 2, 300L));

        List<CompletedCheckpoint> all = storage.getAllCheckpoints(1L);
        assertEquals(3, all.size());
    }

    @Test
    void testDeleteCheckpoint() throws Exception {
        storage.storeCheckPoint(createTestCheckpoint(1L, 1, 100L));
        assertEquals(1, storage.getCheckpointCount(1L));

        storage.deleteCheckpoint(1L, 1, 100L);
        assertEquals(0, storage.getCheckpointCount(1L));

        CompletedCheckpoint retrieved = storage.getLatestCheckpoint(1L, 1);
        assertNull(retrieved);
    }

    @Test
    void testDeleteAllCheckpoints() throws Exception {
        storage.storeCheckPoint(createTestCheckpoint(1L, 1, 100L));
        storage.storeCheckPoint(createTestCheckpoint(1L, 1, 200L));
        storage.storeCheckPoint(createTestCheckpoint(1L, 2, 300L));

        storage.deleteAllCheckpoints(1L);
        assertEquals(0, storage.getCheckpointCount(1L));
    }

    @Test
    void testGetCheckpointCount() throws Exception {
        assertEquals(0, storage.getCheckpointCount(1L));

        storage.storeCheckPoint(createTestCheckpoint(1L, 1, 100L));
        assertEquals(1, storage.getCheckpointCount(1L));

        storage.storeCheckPoint(createTestCheckpoint(1L, 1, 200L));
        assertEquals(2, storage.getCheckpointCount(1L));
    }

    @Test
    void testGetLatestCheckpoints() throws Exception {
        storage.storeCheckPoint(createTestCheckpoint(1L, 1, 100L));
        storage.storeCheckPoint(createTestCheckpoint(1L, 1, 200L));
        storage.storeCheckPoint(createTestCheckpoint(1L, 1, 300L));
        storage.storeCheckPoint(createTestCheckpoint(1L, 1, 400L));

        List<CompletedCheckpoint> latest = storage.getLatestCheckpoints(1L, 2);
        assertEquals(2, latest.size());
    }

    private CompletedCheckpoint createTestCheckpoint(long jobId, int pipelineId, long checkpointId) {
        return CompletedCheckpoint.builder()
                .jobId(jobId)
                .pipelineId(pipelineId)
                .checkpointId(checkpointId)
                .triggerTimestamp(System.currentTimeMillis() - 1000)
                .completedTimestamp(System.currentTimeMillis())
                .checkpointType(CheckpointType.CHECKPOINT)
                .addTaskState(1L, TaskStateSnapshot.empty(1L))
                .addTaskState(2L, TaskStateSnapshot.empty(2L))
                .build();
    }
}
