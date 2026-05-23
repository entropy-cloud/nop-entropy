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

    private static final TaskLocation LOC_1 = new TaskLocation("1", "1", "v1", 1);
    private static final TaskLocation LOC_2 = new TaskLocation("1", "1", "v2", 2);

    @TempDir
    Path tempDir;

    private LocalFileCheckpointStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalFileCheckpointStorage(tempDir.toString());
    }

    @AfterEach
    void tearDown() throws Exception {
        storage.deleteAllCheckpoints("1");
    }

    @Test
    void testGetName() {
        assertEquals("LocalFileCheckpointStorage", storage.getName());
    }

    @Test
    void testStoreAndGetCheckpoint() throws Exception {
        CompletedCheckpoint checkpoint = createTestCheckpoint("1", "1", 100L);

        String path = storage.storeCheckPoint(checkpoint);
        assertNotNull(path);
        assertTrue(path.contains("100"));

        CompletedCheckpoint retrieved = storage.getLatestCheckpoint("1", "1");
        assertNotNull(retrieved);
        assertEquals(100L, retrieved.getCheckpointId());
        assertEquals("1", retrieved.getJobId());
    }

    @Test
    void testGetLatestCheckpoint() throws Exception {
        storage.storeCheckPoint(createTestCheckpoint("1", "1", 100L));
        storage.storeCheckPoint(createTestCheckpoint("1", "1", 200L));
        storage.storeCheckPoint(createTestCheckpoint("1", "1", 300L));

        CompletedCheckpoint latest = storage.getLatestCheckpoint("1", "1");
        assertNotNull(latest);
        assertEquals(300L, latest.getCheckpointId());
    }

    @Test
    void testGetAllCheckpoints() throws Exception {
        storage.storeCheckPoint(createTestCheckpoint("1", "1", 100L));
        storage.storeCheckPoint(createTestCheckpoint("1", "1", 200L));
        storage.storeCheckPoint(createTestCheckpoint("1", "2", 300L));

        List<CompletedCheckpoint> all = storage.getAllCheckpoints("1");
        assertEquals(3, all.size());
    }

    @Test
    void testDeleteCheckpoint() throws Exception {
        storage.storeCheckPoint(createTestCheckpoint("1", "1", 100L));
        assertEquals(1, storage.getCheckpointCount("1"));

        storage.deleteCheckpoint("1", "1", 100L);
        assertEquals(0, storage.getCheckpointCount("1"));

        CompletedCheckpoint retrieved = storage.getLatestCheckpoint("1", "1");
        assertNull(retrieved);
    }

    @Test
    void testDeleteAllCheckpoints() throws Exception {
        storage.storeCheckPoint(createTestCheckpoint("1", "1", 100L));
        storage.storeCheckPoint(createTestCheckpoint("1", "1", 200L));
        storage.storeCheckPoint(createTestCheckpoint("1", "2", 300L));

        storage.deleteAllCheckpoints("1");
        assertEquals(0, storage.getCheckpointCount("1"));
    }

    @Test
    void testGetCheckpointCount() throws Exception {
        assertEquals(0, storage.getCheckpointCount("1"));

        storage.storeCheckPoint(createTestCheckpoint("1", "1", 100L));
        assertEquals(1, storage.getCheckpointCount("1"));

        storage.storeCheckPoint(createTestCheckpoint("1", "1", 200L));
        assertEquals(2, storage.getCheckpointCount("1"));
    }

    @Test
    void testGetLatestCheckpoints() throws Exception {
        storage.storeCheckPoint(createTestCheckpoint("1", "1", 100L));
        storage.storeCheckPoint(createTestCheckpoint("1", "1", 200L));
        storage.storeCheckPoint(createTestCheckpoint("1", "1", 300L));
        storage.storeCheckPoint(createTestCheckpoint("1", "1", 400L));

        List<CompletedCheckpoint> latest = storage.getLatestCheckpoints("1", 2);
        assertEquals(2, latest.size());
    }

    @Test
    void testExistsByCheckpointIdAndPipeline() throws Exception {
        storage.storeCheckPoint(createTestCheckpoint("1", "1", 100L));
        storage.storeCheckPoint(createTestCheckpoint("1", "2", 200L));

        assertTrue(storage.exists("1", "1", 100L));
        assertFalse(storage.exists("1", "1", 200L));
        assertFalse(storage.exists("1", "2", 999L));
    }

    @Test
    void testSavepointStoreAndLoad() throws Exception {
        CompletedCheckpoint checkpoint = createTestCheckpoint("job-sp", "pipe-sp", 500L);
        String savepointPath = tempDir.resolve("savepoints").toString();

        String handle = storage.storeSavepoint(checkpoint, savepointPath);
        assertNotNull(handle);

        CompletedCheckpoint loaded = storage.loadSavepoint(handle);
        assertNotNull(loaded, "loadSavepoint should return the checkpoint stored at savepoint path");
        assertEquals(500L, loaded.getCheckpointId());
        assertEquals("job-sp", loaded.getJobId());
    }

    @Test
    void testSavepointMetadata() throws Exception {
        CompletedCheckpoint checkpoint = createTestCheckpoint("job-meta", "pipe-meta", 600L);
        String savepointPath = tempDir.resolve("savepoints-meta").toString();

        storage.storeSavepoint(checkpoint, savepointPath);

        SavepointMetadata metadata = storage.loadSavepointMetadata(
                savepointPath + "/savepoint-600");
        assertNotNull(metadata, "loadSavepointMetadata should return metadata");
    }

    private CompletedCheckpoint createTestCheckpoint(String jobId, String pipelineId, long checkpointId) {
        return CompletedCheckpoint.builder()
                .jobId(jobId)
                .pipelineId(pipelineId)
                .checkpointId(checkpointId)
                .triggerTimestamp(System.currentTimeMillis() - 1000)
                .completedTimestamp(System.currentTimeMillis())
                .checkpointType(CheckpointType.CHECKPOINT)
                .addTaskState(LOC_1, TaskStateSnapshot.empty(LOC_1))
                .addTaskState(LOC_2, TaskStateSnapshot.empty(LOC_2))
                .build();
    }
}
