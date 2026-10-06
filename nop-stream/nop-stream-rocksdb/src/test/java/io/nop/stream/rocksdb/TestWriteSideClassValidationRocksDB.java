/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.rocksdb;

import io.nop.stream.core.exceptions.StreamException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R6 ST-03 regression pin, RocksDB face (plan 2325, closure audit M2): the
 * RocksDB snapshot WRITE side enforces the same class allowlist as the restore
 * side (StateKeyRematerializer.loadKeyType). Before the gate, a backend whose
 * keyType sits outside the allowlist produced a durable checkpoint that
 * succeeded at snapshot time and then failed loudly on EVERY restore attempt.
 * Reverting the validation call in
 * {@code RocksDBSnapshotSerDe.snapshotState} turns this test red.
 */
class TestWriteSideClassValidationRocksDB {

    /** A real loadable class whose package is OUTSIDE the restore allowlist. */
    private static Class<?> disallowedClass() {
        try {
            return Class.forName("javax.management.MBeanServer");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("pin requires a loadable non-allowlisted class", e);
        }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void disallowedKeyTypeFailsFastAtRocksdbSnapshot(@TempDir Path dir) throws Exception {
        Class evil = disallowedClass();
        RocksDBKeyedStateBackend backend = new RocksDBKeyedStateBackend(
                dir.resolve("rdb").toString(), evil, 16, null);

        // register one state so snapshotState passes its empty early-return and
        // reaches the header write where the keyType gate lives
        backend.getMapState(new io.nop.stream.core.common.state.MapStateDescriptor<>(
                "probe", String.class, String.class));

        StreamException e = assertThrows(StreamException.class,
                () -> RocksDBSnapshotSerDe.snapshotState(backend),
                "a backend whose keyType is outside the allowlist must fail fast AT SNAPSHOT time "
                        + "instead of producing an unrestorable checkpoint");
        assertTrue(String.valueOf(e.getParam("className")).contains("javax.management"),
                () -> "the rejection must name the offending class: " + e.getParam("className"));
        assertNotNull(e);
    }
}
