/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.rocksdb;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import io.nop.stream.core.common.accumulators.SimpleAccumulator;
import io.nop.stream.core.common.state.ReducingState;
import io.nop.stream.core.common.state.ReducingStateDescriptor;
import io.nop.stream.core.common.state.StateDescriptor;
import io.nop.stream.core.common.state.StateMigrationFunction;
import io.nop.stream.core.common.state.TtlContext;
import io.nop.stream.core.common.state.backend.MigratableKeyedState;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_ACCUMULATOR_CREATE_FAILED;
import io.nop.stream.core.exceptions.StreamException;

class RocksDBReducingState<T> extends AbstractRocksDBState implements ReducingState<T> {

    ReducingStateDescriptor<T> descriptor;

    RocksDBReducingState(RocksDBKeyedStateBackend<?> backend, ColumnFamilyHandle cfHandle,
                         ReducingStateDescriptor<T> descriptor) {
        super(backend, cfHandle);
        this.descriptor = descriptor;
    }

    @Override
    public StateDescriptor<?> getMigrationDescriptor() {
        return descriptor;
    }

    /**
     * Stage 33 accumulator-state migration surface. The RocksDB reducing state
     * stores the reduced value (accumulator's local value), so each entry holds
     * a single value of type T. This method iterates every entry, deserializes
     * the value, passes it through {@code migrate}, and writes it back.
     * Correctness of the migrated value is the user's responsibility; the
     * platform does not validate accumulator-migration semantics.
     */
    @Override
    public void applyMigration(StateMigrationFunction<?, ?> migration) {
        applyValueMigration(backend, cfHandle, migration, descriptor.getValueType(), "RocksDB ReducingState");
    }

    @Override
    @SuppressWarnings("unchecked")
    public void replaceDescriptor(StateDescriptor<?> newDescriptor) {
        this.descriptor = (ReducingStateDescriptor<T>) newDescriptor;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T get() throws Exception {
        byte[] key = backend.buildStorageKeyForCurrent();
        ByteBuffer keyBuf = ByteBuffer.wrap(key);
        if (ttl != null && ttl.isExpired(keyBuf)) {
            backend.getDb().delete(cfHandle, key);
            ttl.removeTimestamp(keyBuf);
            return null;
        }
        byte[] bytes = backend.getDb().get(cfHandle, key);
        if (ttl != null && bytes != null) {
            if (!ttl.hasTimestamp(keyBuf)) {
                ttl.grantFreshWindow(keyBuf);
            } else {
                ttl.recordRead(keyBuf);
            }
        }
        return RocksDBValueSerDe.deserialize(bytes, descriptor.getValueType());
    }

    @Override
    @SuppressWarnings("unchecked")
    public void add(T value) throws Exception {
        byte[] key = backend.buildStorageKeyForCurrent();
        ByteBuffer keyBuf = ByteBuffer.wrap(key);
        SimpleAccumulator<T> acc;
        // Evict stale accumulator before read-modify-write so an expired entry does not
        // seed the new accumulation.
        if (ttl != null && ttl.isExpired(keyBuf)) {
            backend.getDb().delete(cfHandle, key);
            ttl.removeTimestamp(keyBuf);
        }
        byte[] existing = backend.getDb().get(cfHandle, key);
        if (existing != null) {
            T current = RocksDBValueSerDe.deserialize(existing, descriptor.getValueType());
            acc = createAccumulator();
            if (current != null) {
                acc.add(current);
            }
        } else {
            acc = createAccumulator();
        }
        acc.add(value);
        T reduced = acc.getLocalValue();
        backend.getDb().put(cfHandle, key, RocksDBValueSerDe.serialize(reduced));
        if (ttl != null) {
            ttl.recordWrite(keyBuf);
        }
    }

    @Override
    public void clear() {
        byte[] key = backend.buildStorageKeyForCurrent();
        try {
            backend.getDb().delete(cfHandle, key);
            if (ttl != null) {
                ttl.removeTimestamp(ByteBuffer.wrap(key));
            }
        } catch (RocksDBException e) {
            throw new StreamException("Failed to clear ReducingState", e);
        }
    }

    private SimpleAccumulator<T> createAccumulator() {
        try {
            return descriptor.getAccumulatorType().getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new StreamException(ERR_STREAM_ACCUMULATOR_CREATE_FAILED, e)
                    .param(ARG_DETAIL, "ReducingState");
        }
    }
}
