/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.rocksdb;

import java.io.IOException;

import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.state.AggregatingStateDescriptor;
import io.nop.stream.core.common.state.InternalAppendingState;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;

import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR;

class RocksDBInternalAggregatingState<K, N, IN, ACC, OUT>
        implements InternalAppendingState<K, N, IN, ACC, OUT> {

    private final RocksDBKeyedStateBackend<K> backend;
    final ColumnFamilyHandle cfHandle;
    final AggregatingStateDescriptor<IN, ACC, OUT> descriptor;

    private transient N currentNamespace;

    RocksDBInternalAggregatingState(RocksDBKeyedStateBackend<K> backend, ColumnFamilyHandle cfHandle,
                                    AggregatingStateDescriptor<IN, ACC, OUT> descriptor) {
        this.backend = backend;
        this.cfHandle = cfHandle;
        this.descriptor = descriptor;
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
    public ACC getAccumulator() throws Exception {
        byte[] bytes = backend.getDb().get(cfHandle, getStorageKey());
        return RocksDBValueSerDe.deserialize(bytes, descriptor.getValueType());
    }

    @Override
    public void setAccumulator(ACC accumulator) throws Exception {
        backend.getDb().put(cfHandle, getStorageKey(), RocksDBValueSerDe.serialize(accumulator));
    }

    @Override
    @SuppressWarnings("unchecked")
    public OUT get() throws IOException {
        try {
            byte[] bytes = backend.getDb().get(cfHandle, getStorageKey());
            if (bytes == null) {
                return null;
            }
            ACC accumulator = RocksDBValueSerDe.deserialize(bytes, descriptor.getValueType());
            return descriptor.getAggregateFunction().getResult(accumulator);
        } catch (Exception e) {
            throw new IOException("Failed to get aggregated state", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void add(IN value) throws IOException {
        try {
            byte[] key = getStorageKey();
            AggregateFunction<IN, ACC, OUT> aggFn = descriptor.getAggregateFunction();
            ACC accumulator;
            byte[] existing = backend.getDb().get(cfHandle, key);
            if (existing != null) {
                accumulator = RocksDBValueSerDe.deserialize(existing, descriptor.getValueType());
            } else {
                accumulator = aggFn.createAccumulator();
            }
            accumulator = aggFn.add(value, accumulator);
            backend.getDb().put(cfHandle, key, RocksDBValueSerDe.serialize(accumulator));
        } catch (RocksDBException e) {
            throw new IOException("Failed to add to InternalAggregatingState", e);
        }
    }

    @Override
    public void clear() {
        try {
            backend.getDb().delete(cfHandle, getStorageKey());
        } catch (RocksDBException e) {
            throw new StreamException("Failed to clear InternalAggregatingState", e);
        }
    }
}
