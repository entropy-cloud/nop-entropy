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

    /**
     * Stage 43 (unaligned checkpoint): default config has unaligned enabled with a
     * threshold below the alignment timeout — the valid production default.
     */
    @Test
    void testUnalignedDefaults() {
        assertTrue(config.isUnalignedCheckpointEnabled(),
                "Unaligned checkpoint is enabled by default");
        assertEquals(CheckpointConfig.DEFAULT_UNALIGNED_THRESHOLD, config.getUnalignedThreshold());
        assertTrue(config.getUnalignedThreshold() < config.getBarrierAlignmentTimeout(),
                "Default threshold must be below alignment timeout");
    }

    /**
     * Stage 43: validateUnalignedConfig passes when threshold < timeout (enabled),
     * and passes regardless when unaligned is disabled.
     */
    @Test
    void testValidateUnalignedConfigAcceptsValidConfig() {
        // Valid: threshold below timeout, enabled.
        config.setUnalignedCheckpointEnabled(true);
        config.setUnalignedThreshold(500L);
        config.setBarrierAlignmentTimeout(5000L);
        assertDoesNotThrow(config::validateUnalignedConfig);

        // Disabled: any threshold accepted (no invariant to enforce).
        config.setUnalignedCheckpointEnabled(false);
        config.setUnalignedThreshold(999999L);
        assertDoesNotThrow(config::validateUnalignedConfig);
    }

    /**
     * Stage 43: validateUnalignedConfig fails fast when threshold >= timeout while
     * unaligned is enabled — misconfiguration must be rejected at config load, not
     * cause confusing runtime behavior.
     */
    @Test
    void testValidateUnalignedConfigRejectsThresholdNotBelowTimeout() {
        config.setUnalignedCheckpointEnabled(true);
        // threshold == timeout → reject
        config.setUnalignedThreshold(5000L);
        config.setBarrierAlignmentTimeout(5000L);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                config::validateUnalignedConfig);
        assertTrue(ex.getMessage().contains("unalignedThreshold"),
                "Error must name the offending field: " + ex.getMessage());

        // threshold > timeout → reject
        config.setUnalignedThreshold(6000L);
        assertThrows(IllegalArgumentException.class, config::validateUnalignedConfig);
    }

    /**
     * Stage 43: builder wires the unaligned config fields.
     */
    @Test
    void testBuilderUnalignedFields() {
        CheckpointConfig built = CheckpointConfig.builder()
                .unalignedCheckpointEnabled(true)
                .unalignedThreshold(750L)
                .barrierAlignmentTimeout(10000L)
                .build();
        assertTrue(built.isUnalignedCheckpointEnabled());
        assertEquals(750L, built.getUnalignedThreshold());
        assertDoesNotThrow(built::validateUnalignedConfig);
    }

    /**
     * Stage 44 successor 5: default per-region restart budget is 3 (consistent
     * with {@code SupervisionLoop.DEFAULT_MAX_RESTARTS_PER_REGION}).
     */
    @Test
    void testMaxRestartsPerRegionDefault() {
        assertEquals(CheckpointConfig.DEFAULT_MAX_RESTARTS_PER_REGION, config.getMaxRestartsPerRegion());
        assertEquals(3, config.getMaxRestartsPerRegion());
    }

    /**
     * Stage 44 successor 5: setter stores custom values (1, 5, and 0 — 0 means
     * "disable scoped restart, surface first failure immediately").
     */
    @Test
    void testMaxRestartsPerRegionSetter() {
        config.setMaxRestartsPerRegion(1);
        assertEquals(1, config.getMaxRestartsPerRegion());

        config.setMaxRestartsPerRegion(5);
        assertEquals(5, config.getMaxRestartsPerRegion());

        // 0 is a valid configuration (disable scoped restart entirely).
        config.setMaxRestartsPerRegion(0);
        assertEquals(0, config.getMaxRestartsPerRegion());
    }

    /**
     * Stage 44 successor 5 (No-Silent-No-Op #24): negative values are rejected
     * at config load time (fail-fast), never silently treated as 0 or default.
     */
    @Test
    void testMaxRestartsPerRegionRejectsNegative() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> config.setMaxRestartsPerRegion(-1));
        assertTrue(ex.getMessage().contains("maxRestartsPerRegion"),
                "Error must name the offending field: " + ex.getMessage());
    }

    /**
     * Stage 44 successor 5: builder wires the per-region restart budget.
     */
    @Test
    void testBuilderMaxRestartsPerRegion() {
        CheckpointConfig built = CheckpointConfig.builder()
                .maxRestartsPerRegion(7)
                .build();
        assertEquals(7, built.getMaxRestartsPerRegion());

        // Builder must also fail-fast on negative values.
        assertThrows(IllegalArgumentException.class,
                () -> CheckpointConfig.builder().maxRestartsPerRegion(-2));
    }
}
