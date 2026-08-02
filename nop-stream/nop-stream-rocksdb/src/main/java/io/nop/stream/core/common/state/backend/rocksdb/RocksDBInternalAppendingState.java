/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.rocksdb;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import io.nop.stream.core.common.accumulators.SimpleAccumulator;
import io.nop.stream.core.common.state.InternalAppendingState;
import io.nop.stream.core.common.state.ReducingStateDescriptor;
import io.nop.stream.core.common.state.StateDescriptor;
import io.nop.stream.core.common.state.StateMigrationFunction;
import io.nop.stream.core.common.state.TtlContext;
import io.nop.stream.core.common.state.backend.MigratableKeyedState;

import io.nop.stream.core.exceptions.StreamException;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ACTUAL_TYPE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_EXPECTED_TYPE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_ACCUMULATOR_CREATE_FAILED;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_TYPE_MISMATCH;

class RocksDBInternalAppendingState<K, N, IN>
        implements InternalAppendingState<K, N, IN, IN, IN>, RocksDbTtlAware, MigratableKeyedState {

    private final RocksDBKeyedStateBackend<K> backend;
    final ColumnFamilyHandle cfHandle;
    ReducingStateDescriptor<IN> descriptor;
    private transient SimpleAccumulator<IN> accumulator;
    private TtlContext<ByteBuffer> ttl;

    private transient N currentNamespace;

    @SuppressWarnings("unchecked")
    RocksDBInternalAppendingState(RocksDBKeyedStateBackend<K> backend, ColumnFamilyHandle cfHandle,
                                  ReducingStateDescriptor<IN> descriptor) {
        this.backend = backend;
        this.cfHandle = cfHandle;
        this.descriptor = descriptor;
        this.accumulator = createAccumulator();
    }

    private SimpleAccumulator<IN> createAccumulator() {
        try {
            return descriptor.getAccumulatorType().getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new StreamException(ERR_STREAM_ACCUMULATOR_CREATE_FAILED, e);
        }
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

    @Override
    public StateDescriptor<?> getMigrationDescriptor() {
        return descriptor;
    }

    /**
     * Stage 33 accumulator-state migration surface. Iterate every entry,
     * deserialize the stored value, pass through {@code migrate}, write back.
     * Correctness is the user's responsibility.
     */
    @Override
    @SuppressWarnings("unchecked")
    public void applyMigration(StateMigrationFunction<?, ?> migration) {
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
                Object old = RocksDBValueSerDe.deserialize(values.get(i), descriptor.getValueType());
                if (old == null) {
                    continue;
                }
                Object migrated = fn.migrate(old);
                backend.getDb().put(cfHandle, keys.get(i), RocksDBValueSerDe.serialize(migrated));
            }
        } catch (RocksDBException e) {
            throw new StreamException("Failed to migrate RocksDB InternalAppendingState", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void replaceDescriptor(StateDescriptor<?> newDescriptor) {
        this.descriptor = (ReducingStateDescriptor<IN>) newDescriptor;
    }

    @Override
    public void setCurrentNamespace(N namespace) {
        this.currentNamespace = namespace;
    }

    @Override
    public N getCurrentNamespace() {
        return currentNamespace;
    }

    private byte[] getStorageKey() {
        if (currentNamespace == null) {
            throw new StreamException(ERR_STREAM_STATE_ERROR)
                    .param(ARG_DETAIL, "currentNamespace is null. Call setCurrentNamespace() before accessing state.");
        }
        return backend.buildStorageKey(currentNamespace, backend.getCurrentKey());
    }

    @Override
    @SuppressWarnings("unchecked")
    public IN getAccumulator() throws Exception {
        byte[] key = getStorageKey();
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
    public void setAccumulator(IN accumulator) throws Exception {
        byte[] key = getStorageKey();
        backend.getDb().put(cfHandle, key, RocksDBValueSerDe.serialize(accumulator));
        if (ttl != null) {
            ttl.recordWrite(ByteBuffer.wrap(key));
        }
    }

    @Override
    public IN get() throws IOException {
        try {
            return getAccumulator();
        } catch (Exception e) {
            throw new IOException("Failed to get accumulator", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void add(IN value) throws IOException {
        try {
            byte[] key = getStorageKey();
            ByteBuffer keyBuf = ByteBuffer.wrap(key);
            if (ttl != null && ttl.isExpired(keyBuf)) {
                backend.getDb().delete(cfHandle, key);
                ttl.removeTimestamp(keyBuf);
            }
            byte[] existing = backend.getDb().get(cfHandle, key);
            IN current = existing != null
                    ? RocksDBValueSerDe.deserialize(existing, descriptor.getValueType()) : null;
            if (current != null && !descriptor.getValueType().isInstance(current)) {
                throw new StreamException(ERR_STREAM_TYPE_MISMATCH)
                        .param(ARG_EXPECTED_TYPE, descriptor.getValueType().getName())
                        .param(ARG_ACTUAL_TYPE, current.getClass().getName());
            }
            accumulator.resetLocal();
            if (current != null) {
                accumulator.add(current);
            }
            accumulator.add(value);
            Object localValue = accumulator.getLocalValue();
            if (localValue instanceof List) {
                localValue = new ArrayList<>((List<?>) localValue);
            }
            backend.getDb().put(cfHandle, key, RocksDBValueSerDe.serialize(localValue));
            if (ttl != null) {
                ttl.recordWrite(keyBuf);
            }
        } catch (RocksDBException e) {
            throw new IOException("Failed to add to InternalAppendingState", e);
        }
    }

    @Override
    public void clear() {
        try {
            byte[] key = getStorageKey();
            backend.getDb().delete(cfHandle, key);
            if (ttl != null) {
                ttl.removeTimestamp(ByteBuffer.wrap(key));
            }
        } catch (RocksDBException e) {
            throw new StreamException("Failed to clear InternalAppendingState", e);
        }
    }
}
