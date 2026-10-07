package io.nop.stream.runtime.execution;

import io.nop.stream.core.checkpoint.CheckpointConfig;
import io.nop.stream.core.exceptions.StreamException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    // WI13 (plan 2339): storageType=s3 routing — real instance from config, and the
    // auto-restore intent gate recognizes the bucket marker.

    @org.junit.jupiter.api.Test
    void testS3StorageRequiresBucket() {
        io.nop.stream.core.checkpoint.CheckpointConfig config = new io.nop.stream.core.checkpoint.CheckpointConfig();
        config.setStorageType("s3");
        config.setStorageProperty("s3.endpoint", "http://localhost:9000");
        org.junit.jupiter.api.Assertions.assertThrows(io.nop.stream.core.exceptions.StreamException.class,
                () -> GraphModelCheckpointExecutor.createStorage(config));
    }

    @org.junit.jupiter.api.Test
    void testS3StorageRoutesFromConfig() {
        io.nop.stream.core.checkpoint.CheckpointConfig config = new io.nop.stream.core.checkpoint.CheckpointConfig();
        config.setStorageType("s3");
        config.setStorageProperty("s3.endpoint", "http://localhost:9000");
        config.setStorageProperty("s3.bucket", "test-bucket");
        io.nop.stream.core.checkpoint.storage.ICheckpointStorage storage =
                GraphModelCheckpointExecutor.createStorage(config);
        org.junit.jupiter.api.Assertions.assertNotNull(storage);
        org.junit.jupiter.api.Assertions.assertEquals("S3CheckpointStorage", storage.getName());
    }

    @org.junit.jupiter.api.Test
    void testHasExplicitStoragePathRecognizesS3Bucket() {
        io.nop.stream.core.checkpoint.CheckpointConfig config = new io.nop.stream.core.checkpoint.CheckpointConfig();
        config.setStorageType("s3");
        config.setStorageProperty("s3.endpoint", "http://localhost:9000");
        // no bucket: no recovery intent
        org.junit.jupiter.api.Assertions.assertFalse(GraphModelCheckpointExecutor.hasExplicitStoragePath(config));
        config.setStorageProperty("s3.bucket", "b");
        org.junit.jupiter.api.Assertions.assertTrue(
                GraphModelCheckpointExecutor.hasExplicitStoragePath(config),
                "bucket configured = explicit recovery intent (mirrors local explicit path)");
    }

}
