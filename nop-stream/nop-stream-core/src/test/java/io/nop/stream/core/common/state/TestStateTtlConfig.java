/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state;

import java.time.Duration;

import io.nop.stream.core.checkpoint.SerializerFingerprint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestStateTtlConfig {

    @Test
    void disabledIsSentinelAndNotEnabled() {
        assertFalse(StateTtlConfig.DISABLED.isEnabled());
        assertEquals(StateTtlUpdateType.Disabled, StateTtlConfig.DISABLED.getUpdateType());
    }

    @Test
    void builderProducesEnabledConfig() {
        StateTtlConfig cfg = StateTtlConfig.newBuilder(Duration.ofSeconds(10))
                .setUpdateType(StateTtlUpdateType.OnReadAndWrite)
                .build();
        assertTrue(cfg.isEnabled());
        assertEquals(Duration.ofSeconds(10), cfg.getTtl());
        assertEquals(StateTtlUpdateType.OnReadAndWrite, cfg.getUpdateType());
        assertTrue(cfg.getCleanupStrategy().isLazyEviction());
    }

    @Test
    void javaSerializationRoundTrip() throws Exception {
        StateTtlConfig cfg = StateTtlConfig.newBuilder(Duration.ofMillis(500))
                .setUpdateType(StateTtlUpdateType.OnCreateAndWrite)
                .setCleanupStrategy(new TtlCleanupStrategy(true, false))
                .build();

        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(bos)) {
            oos.writeObject(cfg);
        }
        StateTtlConfig restored;
        try (java.io.ObjectInputStream ois = new java.io.ObjectInputStream(
                new java.io.ByteArrayInputStream(bos.toByteArray()))) {
            restored = (StateTtlConfig) ois.readObject();
        }
        assertEquals(cfg, restored);
        assertTrue(restored.isEnabled());
        assertFalse(restored.getCleanupStrategy().isBackgroundCleanup());
    }

    @Test
    void ttlConfigDoesNotAffectSchemaChecksum() {
        ValueStateDescriptor<Integer> noTtl = new ValueStateDescriptor<>("v", Integer.class);
        ValueStateDescriptor<Integer> withTtl = new ValueStateDescriptor<>("v", Integer.class);
        withTtl.setTtlConfig(StateTtlConfig.newBuilder(Duration.ofSeconds(5)).build());

        SerializerFingerprint fpNoTtl = StateSchemaResolver.fromDescriptor(StateSchemaResolver.STATE_TYPE_VALUE, noTtl);
        SerializerFingerprint fpWithTtl = StateSchemaResolver.fromDescriptor(StateSchemaResolver.STATE_TYPE_VALUE, withTtl);

        assertEquals(fpNoTtl.getSchemaChecksum(), fpWithTtl.getSchemaChecksum(),
                "TTL config must not affect schemaChecksum");
    }

    @Test
    void differentTtlDurationsAreDistinctConfigs() {
        StateTtlConfig a = StateTtlConfig.newBuilder(Duration.ofSeconds(5)).build();
        StateTtlConfig b = StateTtlConfig.newBuilder(Duration.ofSeconds(10)).build();
        assertNotEquals(a, b);
    }

    @Test
    void descriptorResolvesNullToDisabled() {
        // Simulate a previously-serialized descriptor whose ttlConfig field is null on the
        // stream by constructing then clearing via reflection-free path: getTtlConfig must
        // always return a non-null DISABLED when unset.
        ValueStateDescriptor<String> desc = new ValueStateDescriptor<>("v", String.class);
        assertEquals(StateTtlConfig.DISABLED, desc.getTtlConfig());
        assertFalse(desc.getTtlConfig().isEnabled());
    }
}
