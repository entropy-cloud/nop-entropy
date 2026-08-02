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

import io.nop.stream.core.common.state.ListState;
import io.nop.stream.core.common.state.ListStateDescriptor;
import io.nop.stream.core.common.state.TtlContext;

import io.nop.stream.core.exceptions.StreamException;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;

class RocksDBListState<T> implements ListState<T>, RocksDbTtlAware {

    private final RocksDBKeyedStateBackend<?> backend;
    final ColumnFamilyHandle cfHandle;
    final ListStateDescriptor<T> descriptor;
    private TtlContext<ByteBuffer> ttl;

    RocksDBListState(RocksDBKeyedStateBackend<?> backend, ColumnFamilyHandle cfHandle,
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

    @SuppressWarnings("unchecked")
    @Override
    public Iterable<T> get() throws IOException {
        byte[] key = backend.buildStorageKeyForCurrent();
        ByteBuffer keyBuf = ByteBuffer.wrap(key);
        try {
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
        } catch (Exception e) {
            throw new IOException("Failed to read ListState", e);
        }
    }

    @Override
    public void add(T value) throws IOException {
        byte[] key = backend.buildStorageKeyForCurrent();
        try {
            evictIfExpired(key);
            List<T> list = readList(key);
            list.add(value);
            writeList(key, list);
            recordWrite(key);
        } catch (RocksDBException e) {
            throw new IOException("Failed to add to ListState", e);
        }
    }

    @Override
    public void addAll(Iterable<T> values) throws IOException {
        byte[] key = backend.buildStorageKeyForCurrent();
        try {
            evictIfExpired(key);
            List<T> list = readList(key);
            for (T value : values) {
                list.add(value);
            }
            writeList(key, list);
            recordWrite(key);
        } catch (RocksDBException e) {
            throw new IOException("Failed to addAll to ListState", e);
        }
    }

    @Override
    public void update(Iterable<T> values) throws IOException {
        byte[] key = backend.buildStorageKeyForCurrent();
        List<T> newList = new ArrayList<>();
        for (T value : values) {
            newList.add(value);
        }
        try {
            writeList(key, newList);
            recordWrite(key);
        } catch (RocksDBException e) {
            throw new IOException("Failed to update ListState", e);
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
            throw new StreamException("Failed to clear ListState", e);
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

    private void writeList(byte[] key, List<T> list) throws RocksDBException {
        backend.getDb().put(cfHandle, key, RocksDBValueSerDe.serialize(list));
    }
}
