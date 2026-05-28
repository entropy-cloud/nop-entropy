package io.nop.stream.runtime.checkpoint.storage;

import io.nop.stream.core.exceptions.StreamException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TestLocalFileCheckpointStoragePathTraversal {

    @TempDir
    Path tempDir;

    private LocalFileCheckpointStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalFileCheckpointStorage(tempDir.toString());
    }

    @Test
    void testPathTraversalViaJobId_rejected() {
        StreamException ex = assertThrows(StreamException.class,
                () -> storage.getLatestCheckpoint("../etc", "1"));
        assertTrue(ex.getMessage().contains("Path traversal") || ex.getMessage().contains("must match"));
    }

    @Test
    void testPathTraversalViaJobId_dotDot_rejected() {
        assertThrows(StreamException.class,
                () -> storage.getAllCheckpoints("../../tmp"));
    }

    @Test
    void testInvalidJobId_specialChars_rejected() {
        assertThrows(StreamException.class,
                () -> storage.getCheckpointCount("job;rm -rf /"));
    }

    @Test
    void testInvalidJobId_null_rejected() {
        assertThrows(StreamException.class,
                () -> storage.getAllCheckpoints(null));
    }

    @Test
    void testInvalidPipelineId_rejected() {
        assertThrows(StreamException.class,
                () -> storage.getLatestCheckpoint("valid-job", "../etc/passwd"));
    }

    @Test
    void testValidAlphanumericJobId_accepted() throws Exception {
        assertEquals(0, storage.getCheckpointCount("job-123_test"));
    }

    @Test
    void testLoadSavepoint_pathTraversal_rejected() {
        assertThrows(StreamException.class,
                () -> storage.loadSavepoint("../../etc/passwd"));
    }

    @Test
    void testLoadSavepointMetadata_pathTraversal_rejected() {
        assertThrows(StreamException.class,
                () -> storage.loadSavepointMetadata("../../../tmp/evil"));
    }

    @Test
    void testDeleteAllCheckpoints_invalidJobId_rejected() {
        assertThrows(StreamException.class,
                () -> storage.deleteAllCheckpoints("../outside"));
    }
}
