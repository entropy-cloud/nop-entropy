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

import io.nop.stream.core.common.state.TtlContext;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;

import io.nop.stream.core.exceptions.StreamException;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;

class RocksDBValueState<T> implements ValueState<T>, RocksDbTtlAware {

    private final RocksDBKeyedStateBackend<?> backend;
    final ColumnFamilyHandle cfHandle;
    final ValueStateDescriptor<T> descriptor;
    private TtlContext<ByteBuffer> ttl;

    RocksDBValueState(RocksDBKeyedStateBackend<?> backend, ColumnFamilyHandle cfHandle,
                      ValueStateDescriptor<T> descriptor) {
        this.backend = backend;
        this.cfHandle = cfHandle;
        this.descriptor = descriptor;
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
    public T value() throws IOException {
        byte[] key = backend.buildStorageKeyForCurrent();
        ByteBuffer keyBuf = ByteBuffer.wrap(key);
        try {
            if (ttl != null && ttl.isExpired(keyBuf)) {
                backend.getDb().delete(cfHandle, key);
                ttl.removeTimestamp(keyBuf);
                return descriptor.getDefaultValue();
            }
            byte[] bytes = backend.getDb().get(cfHandle, key);
            if (ttl != null && bytes != null) {
                if (!ttl.hasTimestamp(keyBuf)) {
                    ttl.grantFreshWindow(keyBuf);
                } else {
                    ttl.recordRead(keyBuf);
                }
            }
            T result = RocksDBValueSerDe.deserialize(bytes, descriptor.getValueType());
            return result != null ? result : descriptor.getDefaultValue();
        } catch (RocksDBException e) {
            throw new IOException("Failed to read ValueState", e);
        }
    }

    @Override
    public void update(T value) throws IOException {
        byte[] key = backend.buildStorageKeyForCurrent();
        ByteBuffer keyBuf = ByteBuffer.wrap(key);
        try {
            if (value == null) {
                clear();
            } else {
                if (ttl != null && ttl.isExpired(keyBuf)) {
                    backend.getDb().delete(cfHandle, key);
                    ttl.removeTimestamp(keyBuf);
                }
                backend.getDb().put(cfHandle, key, RocksDBValueSerDe.serialize(value));
                if (ttl != null) {
                    ttl.recordWrite(keyBuf);
                }
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
            if (ttl != null) {
                ttl.removeTimestamp(ByteBuffer.wrap(key));
            }
        } catch (RocksDBException e) {
            throw new StreamException("Failed to clear ValueState", e);
        }
    }
}
