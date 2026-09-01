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
    void testGetLatestCheckpointSelectsMaxIdUnderOutOfOrderWrites() throws Exception {
        // I2 WO-1 (AR-15) dynamic verification: getLatestCheckpoint must select the
        // max checkpoint id by filename-ID sort, not the last-written file. Mixed /
        // out-of-order writes simulate concurrent writers and restore races.
        storage.storeCheckPoint(createTestCheckpoint("1", "1", 300L));
        storage.storeCheckPoint(createTestCheckpoint("1", "1", 100L));
        storage.storeCheckPoint(createTestCheckpoint("1", "1", 250L));
        storage.storeCheckPoint(createTestCheckpoint("1", "1", 200L));

        CompletedCheckpoint latest = storage.getLatestCheckpoint("1", "1");
        assertNotNull(latest);
        assertEquals(300L, latest.getCheckpointId(),
                "getLatestCheckpoint must return the max-ID checkpoint (300), not the last-written (200)");

        List<CompletedCheckpoint> top2 = storage.getLatestCheckpoints("1", 2);
        assertEquals(2, top2.size());
        assertEquals(300L, top2.get(0).getCheckpointId());
        assertEquals(250L, top2.get(1).getCheckpointId(),
                "getLatestCheckpoints must return descending ID order (300, 250)");
    }

    @Test
    void testGetLatestCheckpointAfterDeletingMaxId() throws Exception {
        // I2 WO-1 (AR-15): after deleting the max-ID checkpoint, getLatestCheckpoint
        // must fall back to the next max by filename-ID sort (not file mtime).
        storage.storeCheckPoint(createTestCheckpoint("1", "1", 300L));
        storage.storeCheckPoint(createTestCheckpoint("1", "1", 100L));
        storage.storeCheckPoint(createTestCheckpoint("1", "1", 200L));

        storage.deleteCheckpoint("1", "1", 300L);

        CompletedCheckpoint latest = storage.getLatestCheckpoint("1", "1");
        assertNotNull(latest);
        assertEquals(200L, latest.getCheckpointId());
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

    // ==================== Torn-write fault injection (P-REQ-20, audit 2026-09-01) ====================

    /**
     * R-24(a): a leftover {@code .checkpoint.tmp} file (a torn write interrupted
     * before the atomic move) must be invisible to readers — scans filter by the
     * durable suffix, and the next store's finally block sweeps it.
     */
    @Test
    void testLeftoverTmpFileIsInvisibleToReaders() throws Exception {
        storage.storeCheckPoint(createTestCheckpoint("torn-job", "1", 100L));

        // Simulate a torn write: a .tmp file next to the durable artifact.
        java.nio.file.Path jobDir = tempDir.resolve("torn-job").resolve("1");
        java.nio.file.Path tmp = jobDir.resolve("200.checkpoint.tmp");
        java.nio.file.Files.write(tmp, java.util.Collections.singletonList("{half-written"));

        assertEquals(1, storage.getAllCheckpoints("torn-job").size(),
                "the .tmp artifact must not surface as a checkpoint");
        assertEquals(100L, storage.getLatestCheckpoint("torn-job", "1").getCheckpointId(),
                "the latest checkpoint must remain the durable one");

        // The whole-tree delete removes the residue along with everything else.
        storage.deleteAllCheckpoints("torn-job");
        assertFalse(java.nio.file.Files.exists(tmp),
                "deleteAllCheckpoints must remove the leftover .tmp file with the job tree");
    }

    /**
     * R-24(b): a truncated (torn) durable checkpoint file must fail fast with a
     * typed exception — the documented no-fallback semantics (no silent
     * restart-from-scratch, no silent fall-back to an older checkpoint). The
     * truncation surfaces as a typed NopException (either the storage's
     * CheckpointStorageException or the underlying JSON scan error rethrown by
     * the NopException branch — both fail-fast, neither null).
     */
    @Test
    void testTruncatedCheckpointFileFailsFast() throws Exception {
        storage.storeCheckPoint(createTestCheckpoint("torn-job", "1", 100L));

        java.nio.file.Path file = tempDir.resolve("torn-job").resolve("1").resolve("100.checkpoint");
        byte[] data = java.nio.file.Files.readAllBytes(file);
        assertTrue(data.length > 10, "test setup: the stored checkpoint must have payload");
        java.nio.file.Files.write(file, java.util.Arrays.copyOf(data, data.length / 2));

        io.nop.api.core.exceptions.NopException ex = assertThrows(io.nop.api.core.exceptions.NopException.class,
                () -> storage.getLatestCheckpoint("torn-job", "1"),
                "a truncated checkpoint must fail fast instead of returning null/falling back");
        assertNotNull(ex.getMessage());
    }

    /**
     * R-24(c): a savepoint directory with payload but no metadata file (crash
     * between the two atomic moves — the one non-transactional pair) yields a
     * null from loadSavepointMetadata: no torn FILE is ever visible, only an
     * incomplete artifact set.
     */
    @Test
    void testSavepointPayloadWithoutMetadataYieldsNullMetadata() throws Exception {
        CompletedCheckpoint checkpoint = createTestCheckpoint("torn-sp", "pipe-sp", 700L);
        String savepointPath = tempDir.resolve("savepoints-torn").toString();
        storage.storeSavepoint(checkpoint, savepointPath);

        // Simulate the crash window: remove only the metadata file.
        java.nio.file.Path metadata = tempDir.resolve("savepoints-torn")
                .resolve("savepoint-700").getParent().resolve("savepoint-700.metadata");
        if (!java.nio.file.Files.exists(metadata)) {
            // locate by suffix scan (layout detail may vary)
            try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(tempDir.resolve("savepoints-torn"))) {
                metadata = files.filter(p -> p.getFileName().toString().endsWith(".metadata"))
                        .findFirst().orElseThrow(() -> new IllegalStateException("metadata file not found"));
            }
        }
        java.nio.file.Files.delete(metadata);

        assertNull(storage.loadSavepointMetadata(savepointPath + "/savepoint-700"),
                "an incomplete savepoint artifact set must yield null metadata (no torn file is visible)");
    }
}
