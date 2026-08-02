/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state;

import io.nop.stream.core.checkpoint.SerializerFingerprint;
import io.nop.stream.core.model.StreamComponents;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Stage 33 Phase 1: verifies the {@link StateMigrationFunction} contract and the
 * registration → lookup chain via {@link StreamComponents} (the registration
 * carrier designated by {@code checkpoint-design.md} §8.4.1).
 *
 * <p>Key contract: a registered function is selected iff its
 * {@code sourceFingerprint().schemaChecksum} equals the restored-state checksum
 * AND its {@code targetFingerprint().schemaChecksum} equals the current
 * descriptor checksum. No match → {@code null} (no silent default), so the
 * caller fails fast with {@code ERR_STREAM_STATE_SCHEMA_MISMATCH}.
 */
class TestStateMigrationFunctionRegistration {

    private static SerializerFingerprint fp(String checksum) {
        return new SerializerFingerprint("state-" + checksum,
                SerializerFingerprint.DEFAULT_SCHEMA_VERSION, checksum);
    }

    private static StateMigrationFunction<Object, Object> migration(String source, String target) {
        return new StateMigrationFunction<Object, Object>() {
            @Override
            public Object migrate(Object oldValue) {
                return oldValue;
            }

            @Override
            public SerializerFingerprint sourceFingerprint() {
                return fp(source);
            }

            @Override
            public SerializerFingerprint targetFingerprint() {
                return fp(target);
            }
        };
    }

    @Test
    void registerAndQueryBySourceTargetChecksumReturnsFunction() {
        StreamComponents components = new StreamComponents();
        StateMigrationFunction<Object, Object> fn = migration("checksum-int", "checksum-long");
        components.registerStateMigrationFunction("counter", fn);

        StateMigrationFunction<?, ?> resolved = StateSchemaResolver.findMigration(
                components, "counter", fp("checksum-int"), fp("checksum-long"));

        assertNotNull(resolved, "registered (stateName, source, target) triple must resolve");
        assertEquals(fn, resolved);
    }

    @Test
    void queryWithMismatchedSourceReturnsNull() {
        StreamComponents components = new StreamComponents();
        components.registerStateMigrationFunction("counter", migration("checksum-int", "checksum-long"));

        // source mismatch (different restored schema)
        StateMigrationFunction<?, ?> resolved = StateSchemaResolver.findMigration(
                components, "counter", fp("checksum-double"), fp("checksum-long"));
        assertNull(resolved, "source checksum mismatch must not resolve");
    }

    @Test
    void queryWithMismatchedTargetReturnsNull() {
        StreamComponents components = new StreamComponents();
        components.registerStateMigrationFunction("counter", migration("checksum-int", "checksum-long"));

        // target mismatch (different current schema)
        StateMigrationFunction<?, ?> resolved = StateSchemaResolver.findMigration(
                components, "counter", fp("checksum-int"), fp("checksum-string"));
        assertNull(resolved, "target checksum mismatch must not resolve");
    }

    @Test
    void queryWithUnknownStateNameReturnsNull() {
        StreamComponents components = new StreamComponents();
        components.registerStateMigrationFunction("counter", migration("checksum-int", "checksum-long"));

        StateMigrationFunction<?, ?> resolved = StateSchemaResolver.findMigration(
                components, "unknown-state", fp("checksum-int"), fp("checksum-long"));
        assertNull(resolved, "unknown state name must not resolve");
    }

    @Test
    void queryOnEmptyRegistryReturnsNull() {
        StreamComponents components = new StreamComponents();

        StateMigrationFunction<?, ?> resolved = StateSchemaResolver.findMigration(
                components, "counter", fp("checksum-int"), fp("checksum-long"));
        assertNull(resolved, "empty registry must return null (no silent default)");
    }

    @Test
    void queryWithNullRegistryReturnsNull() {
        StateMigrationFunction<?, ?> resolved = StateSchemaResolver.findMigration(
                null, "counter", fp("checksum-int"), fp("checksum-long"));
        assertNull(resolved, "null registry must return null (caller fails fast)");
    }

    @Test
    void multipleFunctionsPerStateResolvesBySourceTarget() {
        StreamComponents components = new StreamComponents();
        StateMigrationFunction<Object, Object> intToLong = migration("checksum-int", "checksum-long");
        StateMigrationFunction<Object, Object> longToDouble = migration("checksum-long", "checksum-double");
        components.registerStateMigrationFunction("counter", intToLong);
        components.registerStateMigrationFunction("counter", longToDouble);

        StateMigrationFunction<?, ?> resolved = StateSchemaResolver.findMigration(
                components, "counter", fp("checksum-long"), fp("checksum-double"));
        assertEquals(longToDouble, resolved, "second registered function must resolve for its source/target");
    }

    @Test
    void registerRejectsNullStateName() {
        StreamComponents components = new StreamComponents();
        assertThrows(Exception.class,
                () -> components.registerStateMigrationFunction(null, migration("a", "b")));
        assertThrows(Exception.class,
                () -> components.registerStateMigrationFunction("", migration("a", "b")));
    }

    @Test
    void registerRejectsNullFunction() {
        StreamComponents components = new StreamComponents();
        assertThrows(Exception.class,
                () -> components.registerStateMigrationFunction("counter", null));
    }

    @Test
    void integerToLongMigrationComputesFingerprintsFromDescriptors() {
        // End-to-end shape: register a migration bridging real Integer→Long descriptors,
        // then look it up via the same fingerprints the state backend will compute.
        ValueStateDescriptor<Integer> intDesc = new ValueStateDescriptor<>("counter", Integer.class);
        ValueStateDescriptor<Long> longDesc = new ValueStateDescriptor<>("counter", Long.class);

        SerializerFingerprint intFp = StateSchemaResolver.fromDescriptor(
                StateSchemaResolver.STATE_TYPE_VALUE, intDesc);
        SerializerFingerprint longFp = StateSchemaResolver.fromDescriptor(
                StateSchemaResolver.STATE_TYPE_VALUE, longDesc);

        StateMigrationFunction<Integer, Long> fn = new StateMigrationFunction<Integer, Long>() {
            @Override
            public Long migrate(Integer oldValue) {
                return oldValue == null ? null : oldValue.longValue();
            }

            @Override
            public SerializerFingerprint sourceFingerprint() {
                return intFp;
            }

            @Override
            public SerializerFingerprint targetFingerprint() {
                return longFp;
            }
        };

        StreamComponents components = new StreamComponents();
        components.registerStateMigrationFunction("counter", fn);

        StateMigrationFunction<?, ?> resolved = StateSchemaResolver.findMigration(
                components, "counter", intFp, longFp);
        assertNotNull(resolved, "real-descriptor fingerprints must resolve");
        // Anti-hollow: migrate actually transforms the value
        @SuppressWarnings("unchecked")
        StateMigrationFunction<Object, Object> raw = (StateMigrationFunction<Object, Object>) resolved;
        assertEquals(Long.valueOf(42L), raw.migrate(42));
    }
}
