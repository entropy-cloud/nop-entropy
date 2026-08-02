/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.rocksdb;

import java.io.IOException;

import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;

import io.nop.stream.core.exceptions.StreamException;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;

class RocksDBValueState<T> implements ValueState<T> {

    private final RocksDBKeyedStateBackend<?> backend;
    final ColumnFamilyHandle cfHandle;
    final ValueStateDescriptor<T> descriptor;

    RocksDBValueState(RocksDBKeyedStateBackend<?> backend, ColumnFamilyHandle cfHandle,
                      ValueStateDescriptor<T> descriptor) {
        this.backend = backend;
        this.cfHandle = cfHandle;
        this.descriptor = descriptor;
    }

    @Override
    public T value() throws IOException {
        byte[] key = backend.buildStorageKeyForCurrent();
        try {
            byte[] bytes = backend.getDb().get(cfHandle, key);
            T result = RocksDBValueSerDe.deserialize(bytes, descriptor.getValueType());
            return result != null ? result : descriptor.getDefaultValue();
        } catch (RocksDBException e) {
            throw new IOException("Failed to read ValueState", e);
        }
    }

    @Override
    public void update(T value) throws IOException {
        byte[] key = backend.buildStorageKeyForCurrent();
        try {
            if (value == null) {
                backend.getDb().delete(cfHandle, key);
            } else {
                backend.getDb().put(cfHandle, key, RocksDBValueSerDe.serialize(value));
            }
        } catch (RocksDBException e) {
            throw new IOException("Failed to write ValueState", e);
        }
    }

    @Override
    public void clear() {
        byte[] key = backend.buildStorageKeyForCurrent();
        try {
            backend.getDb().delete(cfHandle, key);
        } catch (RocksDBException e) {
            throw new StreamException("Failed to clear ValueState", e);
        }
    }
}
