/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.rocksdb;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import io.nop.stream.core.common.state.StateMigrationFunction;
import io.nop.stream.core.common.state.TtlContext;
import io.nop.stream.core.common.state.backend.MigratableKeyedState;

import io.nop.stream.core.exceptions.StreamException;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

/**
 * item 30 convergence: shared base of every RocksDB keyed-state class. Holds
 * the backend reference, the column-family handle and the TTL sidecar slot,
 * and carries the single whole-value migration implementation
 * {@link #applyValueMigration} (previously verbatim in five classes). The
 * list-element migration lives in {@link RocksDBListState}; the map migration
 * stays in {@link RocksDBMapState} (inner-map shape).
 */
abstract class AbstractRocksDBState implements RocksDbTtlAware, MigratableKeyedState {

    final RocksDBKeyedStateBackend<?> backend;
    final ColumnFamilyHandle cfHandle;

    TtlContext<ByteBuffer> ttl;

    AbstractRocksDBState(RocksDBKeyedStateBackend<?> backend, ColumnFamilyHandle cfHandle) {
        this.backend = backend;
        this.cfHandle = cfHandle;
    }

    @Override
    public void bindTtl(TtlContext<ByteBuffer> ctx) {
        this.ttl = ctx;
    }

    @Override
    public TtlContext<ByteBuffer> ttlContext() {
        return ttl;
    }

    @Override
    public ColumnFamilyHandle cfHandle() {
        return cfHandle;
    }

    /**
     * Single point for the whole-value migration shape: iterate the state's
     * column family, deserialize each stored value as {@code valueType}, pass
     * it through {@code migrate}, and write the migrated value back under the
     * same key. The column-family key encoding is schema-agnostic, so keys are
     * preserved. Correctness of the migrated object is the user's
     * responsibility (Stage 33 accumulator-migration caveat).
     */
    @SuppressWarnings("unchecked")
    static void applyValueMigration(RocksDBKeyedStateBackend<?> backend, ColumnFamilyHandle cfHandle,
                                    StateMigrationFunction<?, ?> migration, Class<?> valueType, String stateLabel) {
        StateMigrationFunction<Object, Object> fn = (StateMigrationFunction<Object, Object>) migration;
        List<byte[]> keys = new ArrayList<>();
        List<byte[]> values = new ArrayList<>();
        try (RocksIterator it = backend.getDb().newIterator(cfHandle)) {
            for (it.seekToFirst(); it.isValid(); it.next()) {
                keys.add(it.key());
                values.add(it.value());
            }
        }
        try {
            for (int i = 0; i < keys.size(); i++) {
                Object old = RocksDBValueSerDe.deserialize(values.get(i), valueType);
                if (old == null) {
                    continue;
                }
                Object migrated = fn.migrate(old);
                backend.getDb().put(cfHandle, keys.get(i), RocksDBValueSerDe.serialize(migrated));
            }
        } catch (RocksDBException e) {
            throw new StreamException("Failed to migrate " + stateLabel, e);
        }
    }
}
