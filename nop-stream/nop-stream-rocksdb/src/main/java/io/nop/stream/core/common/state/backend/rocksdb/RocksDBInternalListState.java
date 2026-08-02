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
import java.util.Collections;
import java.util.List;

import io.nop.stream.core.common.state.InternalListState;
import io.nop.stream.core.common.state.ListStateDescriptor;
import io.nop.stream.core.common.state.TtlContext;

import io.nop.stream.core.exceptions.StreamException;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR;

class RocksDBInternalListState<K, N, T> implements InternalListState<K, N, T>, RocksDbTtlAware {

    private final RocksDBKeyedStateBackend<K> backend;
    final ColumnFamilyHandle cfHandle;
    final ListStateDescriptor<T> descriptor;
    private TtlContext<ByteBuffer> ttl;

    private transient N currentNamespace;

    RocksDBInternalListState(RocksDBKeyedStateBackend<K> backend, ColumnFamilyHandle cfHandle,
                             ListStateDescriptor<T> descriptor) {
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

    @SuppressWarnings("unchecked")
    @Override
    public Iterable<T> get() throws IOException {
        try {
            byte[] key = getStorageKey();
            ByteBuffer keyBuf = ByteBuffer.wrap(key);
            if (ttl != null && ttl.isExpired(keyBuf)) {
                backend.getDb().delete(cfHandle, key);
                ttl.removeTimestamp(keyBuf);
                return Collections.emptyList();
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
                return Collections.emptyList();
            }
            return (List<T>) RocksDBValueSerDe.deserializeList(bytes, descriptor.getValueType());
        } catch (RocksDBException e) {
            throw new IOException("Failed to read InternalListState", e);
        }
    }

    @Override
    public void add(T value) throws IOException {
        try {
            byte[] key = getStorageKey();
            evictIfExpired(key);
            List<T> list = readList(key);
            list.add(value);
            backend.getDb().put(cfHandle, key, RocksDBValueSerDe.serialize(list));
            recordWrite(key);
        } catch (RocksDBException e) {
            throw new IOException("Failed to add to InternalListState", e);
        }
    }

    @Override
    public void addAll(Iterable<T> values) throws IOException {
        try {
            byte[] key = getStorageKey();
            evictIfExpired(key);
            List<T> list = readList(key);
            for (T value : values) {
                list.add(value);
            }
            backend.getDb().put(cfHandle, key, RocksDBValueSerDe.serialize(list));
            recordWrite(key);
        } catch (RocksDBException e) {
            throw new IOException("Failed to addAll to InternalListState", e);
        }
    }

    @Override
    public void update(Iterable<T> values) throws IOException {
        List<T> newList = new ArrayList<>();
        for (T value : values) {
            newList.add(value);
        }
        try {
            byte[] key = getStorageKey();
            backend.getDb().put(cfHandle, key, RocksDBValueSerDe.serialize(newList));
            recordWrite(key);
        } catch (RocksDBException e) {
            throw new IOException("Failed to update InternalListState", e);
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
            throw new StreamException("Failed to clear InternalListState", e);
        }
    }

    private void evictIfExpired(byte[] key) throws RocksDBException {
        if (ttl != null && ttl.isExpired(ByteBuffer.wrap(key))) {
            backend.getDb().delete(cfHandle, key);
            ttl.removeTimestamp(ByteBuffer.wrap(key));
        }
    }

    private void recordWrite(byte[] key) {
        if (ttl != null) {
            ttl.recordWrite(ByteBuffer.wrap(key));
        }
    }

    @SuppressWarnings("unchecked")
    private List<T> readList(byte[] key) throws RocksDBException {
        byte[] bytes = backend.getDb().get(cfHandle, key);
        if (bytes == null) {
            return new ArrayList<>();
        }
        return (List<T>) RocksDBValueSerDe.deserializeList(bytes, descriptor.getValueType());
    }
}
