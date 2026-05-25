package io.nop.stream.runtime.execution;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.exceptions.StreamException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TestE2EStorageTypeRouting {

    @TempDir
    Path tempDir;

    @Test
    void testJdbcStorageThrowsIllegalState() {
        CheckpointConfig config = new CheckpointConfig();
        config.setStorageType("jdbc");

        StreamException ex = assertThrows(StreamException.class,
                () -> GraphModelCheckpointExecutor.createStorage(config));
        assertTrue(ex.getMessage().toLowerCase().contains("jdbc"),
                "Exception message should mention 'jdbc', got: " + ex.getMessage());
    }

    @Test
    void testJdbcStorageCaseInsensitive() {
        CheckpointConfig config = new CheckpointConfig();
        config.setStorageType("JDBC");

        assertThrows(StreamException.class,
                () -> GraphModelCheckpointExecutor.createStorage(config));
    }

    @Test
    void testLocalStorageReturnsStorage() {
        CheckpointConfig config = new CheckpointConfig();
        config.setStorageType("local");
        config.setStorageProperty("path", tempDir.toString());

        assertNotNull(GraphModelCheckpointExecutor.createStorage(config));
    }

    @Test
    void testDefaultStorageReturnsStorage() {
        CheckpointConfig config = new CheckpointConfig();

        assertNotNull(GraphModelCheckpointExecutor.createStorage(config));
    }
}
