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
import java.util.Collections;
import java.util.List;

import io.nop.stream.core.common.state.InternalListState;
import io.nop.stream.core.common.state.ListStateDescriptor;

import io.nop.stream.core.exceptions.StreamException;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;

import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_STATE_ERROR;

class RocksDBInternalListState<K, N, T> implements InternalListState<K, N, T> {

    private final RocksDBKeyedStateBackend<K> backend;
    final ColumnFamilyHandle cfHandle;
    final ListStateDescriptor<T> descriptor;

    private transient N currentNamespace;

    RocksDBInternalListState(RocksDBKeyedStateBackend<K> backend, ColumnFamilyHandle cfHandle,
                             ListStateDescriptor<T> descriptor) {
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

    @SuppressWarnings("unchecked")
    @Override
    public Iterable<T> get() throws IOException {
        try {
            byte[] bytes = backend.getDb().get(cfHandle, getStorageKey());
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
            List<T> list = readList(key);
            list.add(value);
            backend.getDb().put(cfHandle, key, RocksDBValueSerDe.serialize(list));
        } catch (RocksDBException e) {
            throw new IOException("Failed to add to InternalListState", e);
        }
    }

    @Override
    public void addAll(Iterable<T> values) throws IOException {
        try {
            byte[] key = getStorageKey();
            List<T> list = readList(key);
            for (T value : values) {
                list.add(value);
            }
            backend.getDb().put(cfHandle, key, RocksDBValueSerDe.serialize(list));
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
            backend.getDb().put(cfHandle, getStorageKey(), RocksDBValueSerDe.serialize(newList));
        } catch (RocksDBException e) {
            throw new IOException("Failed to update InternalListState", e);
        }
    }

    @Override
    public void clear() {
        try {
            backend.getDb().delete(cfHandle, getStorageKey());
        } catch (RocksDBException e) {
            throw new StreamException("Failed to clear InternalListState", e);
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
