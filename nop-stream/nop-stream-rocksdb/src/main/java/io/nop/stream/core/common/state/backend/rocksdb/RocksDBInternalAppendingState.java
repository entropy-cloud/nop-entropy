/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.rocksdb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.nop.stream.core.common.accumulators.SimpleAccumulator;
import io.nop.stream.core.common.state.InternalAppendingState;
import io.nop.stream.core.common.state.ReducingStateDescriptor;

import io.nop.stream.core.exceptions.StreamException;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;

import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ACTUAL_TYPE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_EXPECTED_TYPE;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_ACCUMULATOR_CREATE_FAILED;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_TYPE_MISMATCH;

class RocksDBInternalAppendingState<K, N, IN>
        implements InternalAppendingState<K, N, IN, IN, IN> {

    private final RocksDBKeyedStateBackend<K> backend;
    final ColumnFamilyHandle cfHandle;
    final ReducingStateDescriptor<IN> descriptor;
    private transient SimpleAccumulator<IN> accumulator;

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
        byte[] bytes = backend.getDb().get(cfHandle, getStorageKey());
        return RocksDBValueSerDe.deserialize(bytes, descriptor.getValueType());
    }

    @Override
    public void setAccumulator(IN accumulator) throws Exception {
        backend.getDb().put(cfHandle, getStorageKey(), RocksDBValueSerDe.serialize(accumulator));
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
        } catch (RocksDBException e) {
            throw new IOException("Failed to add to InternalAppendingState", e);
        }
    }

    @Override
    public void clear() {
        try {
            backend.getDb().delete(cfHandle, getStorageKey());
        } catch (RocksDBException e) {
            throw new StreamException("Failed to clear InternalAppendingState", e);
        }
    }
}
