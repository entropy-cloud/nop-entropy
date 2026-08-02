/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.rocksdb;

import io.nop.stream.core.common.accumulators.SimpleAccumulator;
import io.nop.stream.core.common.state.ReducingState;
import io.nop.stream.core.common.state.ReducingStateDescriptor;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_ACCUMULATOR_CREATE_FAILED;
import io.nop.stream.core.exceptions.StreamException;

class RocksDBReducingState<T> implements ReducingState<T> {

    private final RocksDBKeyedStateBackend<?> backend;
    final ColumnFamilyHandle cfHandle;
    final ReducingStateDescriptor<T> descriptor;

    RocksDBReducingState(RocksDBKeyedStateBackend<?> backend, ColumnFamilyHandle cfHandle,
                         ReducingStateDescriptor<T> descriptor) {
        this.backend = backend;
        this.cfHandle = cfHandle;
        this.descriptor = descriptor;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T get() throws Exception {
        byte[] key = backend.buildStorageKeyForCurrent();
        byte[] bytes = backend.getDb().get(cfHandle, key);
        return RocksDBValueSerDe.deserialize(bytes, descriptor.getValueType());
    }

    @Override
    @SuppressWarnings("unchecked")
    public void add(T value) throws Exception {
        byte[] key = backend.buildStorageKeyForCurrent();
        SimpleAccumulator<T> acc;
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
    }

    @Override
    public void clear() {
        byte[] key = backend.buildStorageKeyForCurrent();
        try {
            backend.getDb().delete(cfHandle, key);
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
