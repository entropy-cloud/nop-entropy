/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.rocksdb;

import java.nio.ByteBuffer;

import io.nop.stream.core.common.functions.AggregateFunction;
import io.nop.stream.core.common.state.AggregatingState;
import io.nop.stream.core.common.state.AggregatingStateDescriptor;
import io.nop.stream.core.common.state.TtlContext;

import io.nop.stream.core.exceptions.StreamException;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;

class RocksDBAggregatingState<IN, ACC, OUT> implements AggregatingState<IN, OUT>, RocksDbTtlAware {

    private final RocksDBKeyedStateBackend<?> backend;
    final ColumnFamilyHandle cfHandle;
    final AggregatingStateDescriptor<IN, ACC, OUT> descriptor;
    private TtlContext<ByteBuffer> ttl;

    RocksDBAggregatingState(RocksDBKeyedStateBackend<?> backend, ColumnFamilyHandle cfHandle,
                            AggregatingStateDescriptor<IN, ACC, OUT> descriptor) {
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
    public OUT get() throws Exception {
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
        ByteBuffer keyBuf = ByteBuffer.wrap(key);
        AggregateFunction<IN, ACC, OUT> aggFn = descriptor.getAggregateFunction();
        if (ttl != null && ttl.isExpired(keyBuf)) {
            backend.getDb().delete(cfHandle, key);
            ttl.removeTimestamp(keyBuf);
        }
        ACC accumulator;
        byte[] existing = backend.getDb().get(cfHandle, key);
        if (existing != null) {
            accumulator = RocksDBValueSerDe.deserialize(existing, descriptor.getValueType());
        } else {
            accumulator = aggFn.createAccumulator();
        }
        accumulator = aggFn.add(value, accumulator);
        backend.getDb().put(cfHandle, key, RocksDBValueSerDe.serialize(accumulator));
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
            throw new StreamException("Failed to clear AggregatingState", e);
        }
    }
}
