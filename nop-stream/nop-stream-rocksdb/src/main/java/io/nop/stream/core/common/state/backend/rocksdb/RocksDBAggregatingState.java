/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.rocksdb;

import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.state.AggregatingState;
import io.nop.stream.core.common.state.AggregatingStateDescriptor;

import io.nop.stream.core.exceptions.StreamException;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;

class RocksDBAggregatingState<IN, ACC, OUT> implements AggregatingState<IN, OUT> {

    private final RocksDBKeyedStateBackend<?> backend;
    final ColumnFamilyHandle cfHandle;
    final AggregatingStateDescriptor<IN, ACC, OUT> descriptor;

    RocksDBAggregatingState(RocksDBKeyedStateBackend<?> backend, ColumnFamilyHandle cfHandle,
                            AggregatingStateDescriptor<IN, ACC, OUT> descriptor) {
        this.backend = backend;
        this.cfHandle = cfHandle;
        this.descriptor = descriptor;
    }

    @Override
    @SuppressWarnings("unchecked")
    public OUT get() throws Exception {
        byte[] key = backend.buildStorageKeyForCurrent();
        byte[] bytes = backend.getDb().get(cfHandle, key);
        if (bytes == null) {
            return null;
        }
        ACC accumulator = RocksDBValueSerDe.deserialize(bytes, descriptor.getValueType());
        return descriptor.getAggregateFunction().getResult(accumulator);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void add(IN value) throws Exception {
        byte[] key = backend.buildStorageKeyForCurrent();
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
    }

    @Override
    public void clear() {
        byte[] key = backend.buildStorageKeyForCurrent();
        try {
            backend.getDb().delete(cfHandle, key);
        } catch (RocksDBException e) {
            throw new StreamException("Failed to clear AggregatingState", e);
        }
    }
}
