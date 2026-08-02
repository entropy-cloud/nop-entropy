/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.rocksdb;

import java.nio.ByteBuffer;

import io.nop.stream.core.common.accumulators.SimpleAccumulator;
import io.nop.stream.core.common.state.ReducingState;
import io.nop.stream.core.common.state.ReducingStateDescriptor;
import io.nop.stream.core.common.state.TtlContext;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_ACCUMULATOR_CREATE_FAILED;
import io.nop.stream.core.exceptions.StreamException;

class RocksDBReducingState<T> implements ReducingState<T>, RocksDbTtlAware {

    private final RocksDBKeyedStateBackend<?> backend;
    final ColumnFamilyHandle cfHandle;
    final ReducingStateDescriptor<T> descriptor;
    private TtlContext<ByteBuffer> ttl;

    RocksDBReducingState(RocksDBKeyedStateBackend<?> backend, ColumnFamilyHandle cfHandle,
                         ReducingStateDescriptor<T> descriptor) {
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
