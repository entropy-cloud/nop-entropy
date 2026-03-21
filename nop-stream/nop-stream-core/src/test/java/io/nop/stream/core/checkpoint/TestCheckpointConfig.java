/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestCheckpointConfig {

    private CheckpointConfig config;

    @BeforeEach
    void setUp() {
        config = new CheckpointConfig();
    }

    @Test
    void testDefaultValues() {
        assertTrue(config.isCheckpointEnabled());
        assertEquals(60000L, config.getCheckpointInterval());
        assertEquals(600000L, config.getCheckpointTimeout());
        assertEquals(500L, config.getMinPause());
        assertEquals(1, config.getMaxConcurrentCheckpoints());
        assertEquals(5, config.getMaxRetainedCheckpoints());
        assertEquals("local", config.getStorageType());
        assertTrue(config.getStorageConfig().isEmpty());
    }

    @Test
    void testSettersAndGetters() {
        config.setCheckpointEnabled(false);
        assertFalse(config.isCheckpointEnabled());

        config.setCheckpointInterval(30000L);
        assertEquals(30000L, config.getCheckpointInterval());

        config.setCheckpointTimeout(300000L);
        assertEquals(300000L, config.getCheckpointTimeout());

        config.setMinPause(1000L);
        assertEquals(1000L, config.getMinPause());

        config.setMaxConcurrentCheckpoints(3);
        assertEquals(3, config.getMaxConcurrentCheckpoints());

        config.setMaxRetainedCheckpoints(10);
        assertEquals(10, config.getMaxRetainedCheckpoints());

        config.setStorageType("jdbc");
        assertEquals("jdbc", config.getStorageType());
    }

    @Test
    void testStorageConfig() {
        Map<String, String> storageConfig = new HashMap<>();
        storageConfig.put("table-name", "checkpoint");
        storageConfig.put("data-source", "default");

        config.setStorageConfig(storageConfig);
        
        assertEquals("checkpoint", config.getStorageProperty("table-name"));
        assertEquals("default", config.getStorageProperty("data-source"));
        assertEquals(2, config.getStorageConfig().size());
    }

    @Test
    void testStorageProperty() {
        config.setStorageProperty("key1", "value1");
        config.setStorageProperty("key2", "value2");
        
        assertEquals("value1", config.getStorageProperty("key1"));
        assertEquals("value2", config.getStorageProperty("key2"));
        assertNull(config.getStorageProperty("nonexistent"));
    }

    @Test
    void testBuilder() {
        CheckpointConfig built = CheckpointConfig.builder()
                .checkpointEnabled(false)
                .checkpointInterval(10000L)
                .checkpointTimeout(120000L)
                .minPause(200L)
                .maxConcurrentCheckpoints(2)
                .maxRetainedCheckpoints(3)
                .storageType("redis")
                .storageProperty("host", "localhost")
                .build();

        assertFalse(built.isCheckpointEnabled());
        assertEquals(10000L, built.getCheckpointInterval());
        assertEquals(120000L, built.getCheckpointTimeout());
        assertEquals(200L, built.getMinPause());
        assertEquals(2, built.getMaxConcurrentCheckpoints());
        assertEquals(3, built.getMaxRetainedCheckpoints());
        assertEquals("redis", built.getStorageType());
        assertEquals("localhost", built.getStorageProperty("host"));
    }

    @Test
    void testBuilderWithStorageConfig() {
        Map<String, String> storageConfig = new HashMap<>();
        storageConfig.put("path", "/tmp/checkpoints");

        CheckpointConfig built = CheckpointConfig.builder()
                .storageType("local")
                .storageConfig(storageConfig)
                .build();

        assertEquals("local", built.getStorageType());
        assertEquals("/tmp/checkpoints", built.getStorageProperty("path"));
    }
}
